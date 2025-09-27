# 🍎 SIGN IN WITH APPLE & FIREBASE - Processus Complet

## 🎯 Vue d'Ensemble

Processus d'authentification Apple intégré avec Firebase dans l'onboarding, sauvegarde des données utilisateur, et adaptation pour Google Sign In sur Android.

---

## 🏗️ Architecture Authentification iOS

### 1. Services Impliqués

```swift
// AuthenticationService - Gestion Apple Sign In
@StateObject private var authService = AuthenticationService.shared

// FirebaseService - Sauvegarde données
@StateObject private var firebaseService = FirebaseService.shared

// AppState - Gestion états globaux
@EnvironmentObject var appState: AppState
```

---

## 🔐 Processus Sign In with Apple

### 1. Interface Onboarding (AuthenticationStepView)

```swift
struct AuthenticationStepView: View {
    var body: some View {
        VStack {
            // Titre
            Text("create_secure_account".localized)
                .font(.system(size: 36, weight: .bold))
                .foregroundColor(.black)

            // Bouton Apple Sign In
            Button(action: {
                print("🔐 Authentification Apple démarrée")
                authService.signInWithApple()
            }) {
                HStack {
                    Image(systemName: "applelogo")
                        .font(.system(size: 20, weight: .medium))
                    Text("sign_in_with_apple".localized)
                        .font(.system(size: 18, weight: .semibold))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(Color.black)
                .cornerRadius(28)
            }
        }
        .onReceive(firebaseService.$isAuthenticated) { isAuthenticated in
            if isAuthenticated {
                if let user = firebaseService.currentUser {
                    // Utilisateur existant - terminer onboarding
                    appState.authenticate(with: user)
                    appState.completeOnboarding()
                } else {
                    // Nouvel utilisateur - continuer onboarding
                    appState.isAuthenticated = true
                    appState.startUserOnboarding()
                }
            }
        }
    }
}
```

### 2. AuthenticationService - Traitement Apple

```swift
class AuthenticationService: ObservableObject {
    func signInWithApple() {
        // Protection contre appels multiples
        guard !isSignInInProgress else { return }
        guard Date().timeIntervalSince(lastProcessedCredentialTime) > 2.0 else { return }

        isSignInInProgress = true

        // Génération nonce sécurisé
        let nonce = randomNonceString()
        currentNonce = nonce

        // Configuration requête Apple
        let appleIDProvider = ASAuthorizationAppleIDProvider()
        let request = appleIDProvider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)

        // Présentation interface Apple
        let authorizationController = ASAuthorizationController(authorizationRequests: [request])
        authorizationController.delegate = self
        authorizationController.presentationContextProvider = self
        authorizationController.performRequests()

        isLoading = true
    }

    // Délégué Apple - Succès authentification
    func authorizationController(controller: ASAuthorizationController,
                                didCompleteWithAuthorization authorization: ASAuthorization) {

        guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential else { return }

        // Récupération nom utilisateur (si fourni)
        if let fullName = appleIDCredential.fullName {
            let displayName = PersonNameComponentsFormatter.localizedString(from: fullName, style: .long)
            if !displayName.isEmpty {
                // Sauvegarde nom dans UserDefaults pour futures connexions
                let userID = appleIDCredential.user
                UserDefaults.standard.set(displayName, forKey: "AppleDisplayName_\\(userID)")
                self.appleUserDisplayName = displayName
            }
        }

        // Traitement credentials Firebase
        guard let nonce = currentNonce,
              let appleIDToken = appleIDCredential.identityToken,
              let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
            self.errorMessage = "Erreur traitement credentials"
            return
        }

        // Création credential Firebase
        let credential = OAuthProvider.credential(providerID: AuthProviderID.apple,
                                                  idToken: idTokenString,
                                                  rawNonce: nonce)

        // Authentification Firebase
        Auth.auth().signIn(with: credential) { result, error in
            DispatchQueue.main.async {
                self.isLoading = false
                self.isSignInInProgress = false

                if let error = error {
                    self.errorMessage = "Erreur connexion: \\(error.localizedDescription)"
                    return
                }

                // Notification succès authentification
                NotificationCenter.default.post(name: NSNotification.Name("UserAuthenticated"), object: nil)
                print("✅ Authentification Apple réussie")
            }
        }
    }
}
```

---

## 🔥 Intégration Firebase

### 1. FirebaseService - Traitement Authentification

```swift
func signInWithApple(authorization: ASAuthorization) {
    guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
          let nonce = currentNonce,
          let appleIDToken = appleIDCredential.identityToken,
          let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
        self.errorMessage = "Erreur authentification Apple"
        return
    }

    // Création credential Firebase
    let credential = OAuthProvider.credential(providerID: AuthProviderID.apple,
                                              idToken: idTokenString,
                                              rawNonce: nonce)

    isLoading = true

    Auth.auth().signIn(with: credential) { [weak self] result, error in
        DispatchQueue.main.async {
            self?.isLoading = false

            guard let firebaseUser = result?.user else {
                self?.errorMessage = "Erreur de connexion"
                return
            }

            // Vérifier si nouvel utilisateur
            if result?.additionalUserInfo?.isNewUser == true {
                // Créer profil utilisateur vide
                self?.createEmptyUserProfile(
                    uid: firebaseUser.uid,
                    email: firebaseUser.email,
                    name: appleIDCredential.fullName?.givenName
                )
            } else {
                // Charger données existantes
                self?.loadUserData(uid: firebaseUser.uid)
            }
        }
    }
}
```

### 2. Création Profil Utilisateur Vide

```swift
private func createEmptyUserProfile(uid: String, email: String?, name: String?) {
    print("🔥 Création profil utilisateur vide")

    // Données minimales pour nouvel utilisateur
    let userData: [String: Any] = [
        "id": UUID().uuidString,
        "appleUserID": uid,
        "email": email ?? "",
        "name": name ?? "",
        "createdAt": Timestamp(date: Date()),
        "lastLoginDate": Timestamp(date: Date()),
        "onboardingInProgress": true,
        "isSubscribed": false,
        "partnerCode": "",
        "partnerId": "",
        "relationshipGoals": [],
        "relationshipDuration": "notInRelationship"
    ]

    // Sauvegarde Firestore
    db.collection("users").document(uid).setData(userData, merge: true) { [weak self] error in
        DispatchQueue.main.async {
            if let error = error {
                print("❌ Erreur création profil: \\(error.localizedDescription)")
                self?.errorMessage = "Erreur création profil"
            } else {
                print("✅ Profil utilisateur vide créé")
                self?.isAuthenticated = true
                self?.currentUser = nil // Pas de données complètes encore
            }
        }
    }
}
```

---

## 💾 Sauvegarde Données Onboarding

### 1. Sauvegarde Partielle (Pendant Onboarding)

```swift
func savePartialUserData(_ user: AppUser) {
    guard let firebaseUser = Auth.auth().currentUser else {
        self.errorMessage = "Utilisateur non connecté"
        return
    }

    // Vérification authentification Apple
    guard firebaseUser.providerData.contains(where: { $0.providerID == "apple.com" }) else {
        self.errorMessage = "Authentification Apple requise"
        return
    }

    // Construction données partielles
    var userData: [String: Any] = [
        "id": user.id,
        "name": user.name,
        "birthDate": Timestamp(date: user.birthDate),
        "relationshipGoals": user.relationshipGoals,
        "relationshipDuration": user.relationshipDuration.rawValue,
        "partnerCode": user.partnerCode ?? "",
        "partnerId": user.partnerId ?? "",
        "isSubscribed": user.isSubscribed,
        "appleUserID": firebaseUser.uid,
        "lastLoginDate": Timestamp(date: Date()),
        "updatedAt": Timestamp(date: Date()),
        "onboardingInProgress": true  // Marquer onboarding en cours
    ]

    // Sauvegarde Firestore avec merge
    db.collection("users").document(firebaseUser.uid).setData(userData, merge: true) { [weak self] error in
        DispatchQueue.main.async {
            if let error = error {
                print("❌ Erreur sauvegarde partielle: \\(error.localizedDescription)")
                self?.errorMessage = "Erreur de sauvegarde"
            } else {
                print("✅ Données partielles sauvegardées")
                self?.currentUser = user
            }
        }
    }
}
```

### 2. Sauvegarde Finale (Fin Onboarding)

```swift
func saveUserData(_ user: AppUser) {
    guard let firebaseUser = Auth.auth().currentUser else {
        self.errorMessage = "Utilisateur non connecté"
        return
    }

    // Vérification authentification Apple
    guard firebaseUser.providerData.contains(where: { $0.providerID == "apple.com" }) else {
        self.errorMessage = "Authentification Apple requise"
        return
    }

    isLoading = true

    // Construction données complètes
    var userData: [String: Any] = [
        "id": user.id,
        "name": user.name,
        "birthDate": Timestamp(date: user.birthDate),
        "relationshipGoals": user.relationshipGoals,
        "relationshipDuration": user.relationshipDuration.rawValue,
        "relationshipImprovement": user.relationshipImprovement ?? "",
        "questionMode": user.questionMode ?? "",
        "partnerCode": user.partnerCode ?? "",
        "partnerId": user.partnerId ?? "",
        "connectedPartnerCode": user.connectedPartnerCode ?? "",
        "isSubscribed": user.isSubscribed,
        "appleUserID": firebaseUser.uid,
        "email": firebaseUser.email ?? "",
        "profileImageURL": user.profileImageURL ?? "",
        "relationshipStartDate": user.relationshipStartDate != nil ?
            Timestamp(date: user.relationshipStartDate!) : NSNull(),
        "currentLocation": user.currentLocation != nil ? [
            "latitude": user.currentLocation!.coordinate.latitude,
            "longitude": user.currentLocation!.coordinate.longitude,
            "address": user.currentLocation!.address ?? "",
            "city": user.currentLocation!.city ?? "",
            "country": user.currentLocation!.country ?? ""
        ] : NSNull(),
        "languageCode": user.languageCode ?? Locale.current.languageCode ?? "en",
        "createdAt": Timestamp(date: Date()),
        "lastLoginDate": Timestamp(date: Date()),
        "updatedAt": Timestamp(date: Date()),
        "onboardingInProgress": false  // Onboarding terminé
    ]

    // Sauvegarde finale Firestore
    db.collection("users").document(firebaseUser.uid).setData(userData, merge: true) { [weak self] error in
        DispatchQueue.main.async {
            self?.isLoading = false

            if let error = error {
                print("❌ Erreur sauvegarde finale: \\(error.localizedDescription)")
                self?.errorMessage = "Erreur de sauvegarde: \\(error.localizedDescription)"
            } else {
                // Mise à jour cache local
                UserCacheManager.shared.cacheUser(user)
                print("✅ Données utilisateur sauvegardées avec succès")

                self?.currentUser = user
                self?.isAuthenticated = true
            }
        }
    }
}
```

---

## 📱 Gestion États Onboarding

### 1. AppState - Coordination Authentification

```swift
class AppState: ObservableObject {
    @Published var isAuthenticated: Bool = false
    @Published var isOnboardingCompleted: Bool = false
    @Published var isOnboardingInProgress: Bool = false
    @Published var currentUser: AppUser?

    func authenticate(with user: AppUser) {
        print("🔥 AppState: Authentification utilisateur existant")
        self.currentUser = user
        self.isAuthenticated = true
        self.isOnboardingCompleted = true
        self.isOnboardingInProgress = false
    }

    func startUserOnboarding() {
        print("🔥 AppState: Démarrage onboarding nouvel utilisateur")
        self.isAuthenticated = true
        self.isOnboardingInProgress = true
        self.isOnboardingCompleted = false
    }

    func completeOnboarding() {
        print("🔥 AppState: Finalisation onboarding")
        self.isOnboardingCompleted = true
        self.isOnboardingInProgress = false
    }
}
```

### 2. Chargement Données Existantes

```swift
func loadUserData(uid: String) {
    db.collection("users").document(uid).getDocument { [weak self] document, error in
        DispatchQueue.main.async {
            guard let document = document, document.exists,
                  let data = document.data() else {
                print("🔥 Aucune donnée - onboarding requis")
                self?.isAuthenticated = true
                self?.currentUser = nil
                return
            }

            // Conversion données Firestore vers AppUser
            let user = AppUser(
                id: data["id"] as? String ?? UUID().uuidString,
                name: data["name"] as? String ?? "",
                birthDate: (data["birthDate"] as? Timestamp)?.dateValue() ?? Date(),
                relationshipGoals: data["relationshipGoals"] as? [String] ?? [],
                relationshipDuration: AppUser.RelationshipDuration(
                    rawValue: data["relationshipDuration"] as? String ?? "notInRelationship"
                ) ?? .notInRelationship,
                relationshipImprovement: data["relationshipImprovement"] as? String,
                questionMode: data["questionMode"] as? String,
                partnerCode: data["partnerCode"] as? String,
                partnerId: data["partnerId"] as? String,
                isSubscribed: data["isSubscribed"] as? Bool ?? false,
                onboardingInProgress: data["onboardingInProgress"] as? Bool ?? false,
                relationshipStartDate: (data["relationshipStartDate"] as? Timestamp)?.dateValue(),
                profileImageURL: data["profileImageURL"] as? String,
                currentLocation: self?.parseUserLocation(from: data["currentLocation"] as? [String: Any]),
                languageCode: data["languageCode"] as? String
            )

            print("✅ Utilisateur chargé: \\(user.name)")

            // Mise à jour cache local
            UserCacheManager.shared.cacheUser(user)

            self?.currentUser = user
            self?.isAuthenticated = true
        }
    }
}
```

---

## 🤖 Adaptation Android - Google Sign In

### 1. Configuration Google Sign In

```kotlin
// build.gradle (Module: app)
implementation 'com.google.android.gms:play-services-auth:20.7.0'
implementation 'com.google.firebase:firebase-auth-ktx'

// GoogleSignInOptions configuration
class GoogleSignInManager(private val context: Context) {

    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .requestProfile()
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun signOut(): Task<Void> {
        return googleSignInClient.signOut()
    }
}
```

### 2. Interface Authentification Android

```kotlin
@Composable
fun AuthenticationScreen(
    onAuthenticationSuccess: () -> Unit,
    viewModel: AuthenticationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Launcher pour Google Sign In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            viewModel.handleGoogleSignInResult(task)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        // Titre
        Text(
            text = stringResource(R.string.create_secure_account),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // Bouton Google Sign In
        Button(
            onClick = {
                Log.d("Auth", "🔐 Authentification Google démarrée")
                val signInIntent = viewModel.getGoogleSignInIntent()
                googleSignInLauncher.launch(signInIntent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(28.dp),
            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_google),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.sign_in_with_google),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Indicateur de chargement
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Observer changements authentification
    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            if (uiState.user != null) {
                // Utilisateur existant
                onAuthenticationSuccess()
            } else {
                // Nouvel utilisateur - continuer onboarding
                // Navigation gérée par le ViewModel
            }
        }
    }
}
```

### 3. ViewModel Authentification Android

```kotlin
@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseFirestore: FirebaseFirestore,
    private val googleSignInManager: GoogleSignInManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthenticationUiState())
    val uiState = _uiState.asStateFlow()

    fun getGoogleSignInIntent(): Intent {
        return googleSignInManager.getSignInIntent()
    }

    fun handleGoogleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d("Auth", "✅ Google Sign In réussi: ${account.email}")

            // Récupération token Google
            val idToken = account.idToken
            if (idToken != null) {
                firebaseAuthWithGoogle(idToken, account)
            } else {
                updateUiState { it.copy(
                    isLoading = false,
                    error = "Erreur récupération token Google"
                )}
            }

        } catch (e: ApiException) {
            Log.e("Auth", "❌ Erreur Google Sign In: ${e.statusCode} - ${e.message}")
            updateUiState { it.copy(
                isLoading = false,
                error = "Erreur authentification Google: ${e.message}"
            )}
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String, googleAccount: GoogleSignInAccount) {
        updateUiState { it.copy(isLoading = true) }

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Auth", "✅ Authentification Firebase réussie")
                    val firebaseUser = firebaseAuth.currentUser

                    if (firebaseUser != null) {
                        val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

                        if (isNewUser) {
                            // Créer profil utilisateur vide
                            createEmptyUserProfile(firebaseUser, googleAccount)
                        } else {
                            // Charger données existantes
                            loadUserData(firebaseUser.uid)
                        }
                    }
                } else {
                    Log.e("Auth", "❌ Erreur authentification Firebase: ${task.exception?.message}")
                    updateUiState { it.copy(
                        isLoading = false,
                        error = "Erreur connexion Firebase: ${task.exception?.message}"
                    )}
                }
            }
    }

    private fun createEmptyUserProfile(firebaseUser: FirebaseUser, googleAccount: GoogleSignInAccount) {
        Log.d("Auth", "🔥 Création profil utilisateur vide")

        val userData = mapOf(
            "id" to UUID.randomUUID().toString(),
            "googleUserID" to firebaseUser.uid,
            "email" to (googleAccount.email ?: ""),
            "name" to (googleAccount.displayName ?: ""),
            "profileImageURL" to (googleAccount.photoUrl?.toString() ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "lastLoginDate" to FieldValue.serverTimestamp(),
            "onboardingInProgress" to true,
            "isSubscribed" to false,
            "partnerCode" to "",
            "partnerId" to "",
            "relationshipGoals" to emptyList<String>(),
            "relationshipDuration" to "notInRelationship"
        )

        firebaseFirestore.collection("users").document(firebaseUser.uid)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Auth", "✅ Profil utilisateur vide créé")
                updateUiState { it.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = null // Pas de données complètes encore
                )}
            }
            .addOnFailureListener { error ->
                Log.e("Auth", "❌ Erreur création profil: ${error.message}")
                updateUiState { it.copy(
                    isLoading = false,
                    error = "Erreur création profil: ${error.message}"
                )}
            }
    }

    private fun loadUserData(uid: String) {
        firebaseFirestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val data = document.data ?: return@addOnSuccessListener

                    // Conversion données Firestore vers User
                    val user = User(
                        id = data["id"] as? String ?: UUID.randomUUID().toString(),
                        name = data["name"] as? String ?: "",
                        email = data["email"] as? String ?: "",
                        birthDate = (data["birthDate"] as? Timestamp)?.toDate() ?: Date(),
                        relationshipGoals = (data["relationshipGoals"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                        relationshipDuration = data["relationshipDuration"] as? String ?: "notInRelationship",
                        partnerCode = data["partnerCode"] as? String,
                        partnerId = data["partnerId"] as? String,
                        isSubscribed = data["isSubscribed"] as? Boolean ?: false,
                        onboardingInProgress = data["onboardingInProgress"] as? Boolean ?: false,
                        profileImageURL = data["profileImageURL"] as? String,
                        relationshipStartDate = (data["relationshipStartDate"] as? Timestamp)?.toDate()
                    )

                    Log.d("Auth", "✅ Utilisateur chargé: ${user.name}")

                    // Mise à jour cache local
                    userRepository.cacheUser(user)

                    updateUiState { it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        user = user
                    )}
                } else {
                    Log.d("Auth", "🔥 Aucune donnée - onboarding requis")
                    updateUiState { it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        user = null
                    )}
                }
            }
            .addOnFailureListener { error ->
                Log.e("Auth", "❌ Erreur chargement utilisateur: ${error.message}")
                updateUiState { it.copy(
                    isLoading = false,
                    error = "Erreur chargement: ${error.message}"
                )}
            }
    }

    private fun updateUiState(update: (AuthenticationUiState) -> AuthenticationUiState) {
        _uiState.value = update(_uiState.value)
    }
}

data class AuthenticationUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: User? = null,
    val error: String? = null
)
```

### 4. Sauvegarde Données Android

```kotlin
class UserRepository @Inject constructor(
    private val firebaseFirestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val userCacheManager: UserCacheManager
) {

    suspend fun savePartialUserData(user: User): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val firebaseUser = firebaseAuth.currentUser
                ?: return@withContext Result.failure(Exception("Utilisateur non connecté"))

            // Vérification authentification Google
            val hasGoogleProvider = firebaseUser.providerData.any { it.providerId == "google.com" }
            if (!hasGoogleProvider) {
                return@withContext Result.failure(Exception("Authentification Google requise"))
            }

            // Construction données partielles
            val userData = mapOf(
                "id" to user.id,
                "name" to user.name,
                "birthDate" to Timestamp(user.birthDate),
                "relationshipGoals" to user.relationshipGoals,
                "relationshipDuration" to user.relationshipDuration,
                "partnerCode" to (user.partnerCode ?: ""),
                "partnerId" to (user.partnerId ?: ""),
                "isSubscribed" to user.isSubscribed,
                "googleUserID" to firebaseUser.uid,
                "lastLoginDate" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "onboardingInProgress" to true
            )

            // Sauvegarde Firestore
            firebaseFirestore.collection("users").document(firebaseUser.uid)
                .set(userData, SetOptions.merge())
                .await()

            Log.d("Repository", "✅ Données partielles sauvegardées")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("Repository", "❌ Erreur sauvegarde partielle: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveCompleteUserData(user: User): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val firebaseUser = firebaseAuth.currentUser
                ?: return@withContext Result.failure(Exception("Utilisateur non connecté"))

            // Construction données complètes
            val userData = mutableMapOf<String, Any>(
                "id" to user.id,
                "name" to user.name,
                "email" to user.email,
                "birthDate" to Timestamp(user.birthDate),
                "relationshipGoals" to user.relationshipGoals,
                "relationshipDuration" to user.relationshipDuration,
                "relationshipImprovement" to (user.relationshipImprovement ?: ""),
                "partnerCode" to (user.partnerCode ?: ""),
                "partnerId" to (user.partnerId ?: ""),
                "isSubscribed" to user.isSubscribed,
                "googleUserID" to firebaseUser.uid,
                "profileImageURL" to (user.profileImageURL ?: ""),
                "languageCode" to (user.languageCode ?: Locale.getDefault().language),
                "createdAt" to FieldValue.serverTimestamp(),
                "lastLoginDate" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "onboardingInProgress" to false
            )

            // Ajouter date début relation si définie
            user.relationshipStartDate?.let {
                userData["relationshipStartDate"] = Timestamp(it)
            }

            // Ajouter localisation si définie
            user.currentLocation?.let { location ->
                userData["currentLocation"] = mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "address" to location.address,
                    "city" to location.city,
                    "country" to location.country
                )
            }

            // Sauvegarde finale Firestore
            firebaseFirestore.collection("users").document(firebaseUser.uid)
                .set(userData, SetOptions.merge())
                .await()

            // Mise à jour cache local
            userCacheManager.cacheUser(user)

            Log.d("Repository", "✅ Données complètes sauvegardées")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("Repository", "❌ Erreur sauvegarde complète: ${e.message}")
            Result.failure(e)
        }
    }

    fun cacheUser(user: User) {
        userCacheManager.cacheUser(user)
    }
}
```

### 5. Cache Utilisateur Android

```kotlin
class UserCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferences = context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun cacheUser(user: User) {
        val json = gson.toJson(user)
        val timestamp = System.currentTimeMillis()

        preferences.edit()
            .putString(CACHE_KEY, json)
            .putLong(TIMESTAMP_KEY, timestamp)
            .apply()

        Log.d("Cache", "💾 Utilisateur mis en cache")
    }

    fun getCachedUser(): User? {
        val json = preferences.getString(CACHE_KEY, null) ?: return null
        val timestamp = preferences.getLong(TIMESTAMP_KEY, 0)

        // Vérifier âge du cache (7 jours max)
        val age = System.currentTimeMillis() - timestamp
        if (age > 7 * 24 * 60 * 60 * 1000) {
            Log.d("Cache", "⏰ Cache expiré, nettoyage")
            clearCache()
            return null
        }

        return try {
            gson.fromJson(json, User::class.java)
        } catch (e: Exception) {
            Log.e("Cache", "❌ Erreur décodage cache: ${e.message}")
            clearCache()
            null
        }
    }

    fun clearCache() {
        preferences.edit()
            .remove(CACHE_KEY)
            .remove(TIMESTAMP_KEY)
            .apply()
        Log.d("Cache", "🗑️ Cache utilisateur nettoyé")
    }

    companion object {
        private const val CACHE_KEY = "cached_user"
        private const val TIMESTAMP_KEY = "cache_timestamp"
    }
}
```

---

## 🔄 Processus Complet

### iOS (Apple Sign In)

1. **Interface** → Bouton "Sign In with Apple"
2. **AuthenticationService** → Génération nonce + Présentation interface Apple
3. **Délégué Apple** → Récupération credentials + Nom utilisateur
4. **Firebase Auth** → Authentification avec credentials Apple
5. **FirebaseService** → Vérification nouvel/ancien utilisateur
6. **Nouveau** → Création profil vide + Onboarding
7. **Existant** → Chargement données + Navigation principale
8. **Onboarding** → Sauvegarde partielle puis finale
9. **Cache** → Mise à jour cache local pour performance

### Android (Google Sign In)

1. **Interface** → Bouton "Sign In with Google"
2. **GoogleSignInManager** → Configuration + Lancement intent Google
3. **Activity Result** → Récupération compte Google
4. **ViewModel** → Traitement résultat + Récupération token
5. **Firebase Auth** → Authentification avec credentials Google
6. **Repository** → Vérification nouvel/ancien utilisateur
7. **Nouveau** → Création profil vide + Onboarding
8. **Existant** → Chargement données + Navigation principale
9. **Onboarding** → Sauvegarde partielle puis finale
10. **Cache** → Mise à jour cache local pour performance

---

## 📋 Résumé Technique

### ✅ Authentification iOS (Apple)

- **Nonce sécurisé** pour sécurité
- **Nom utilisateur** sauvegardé localement
- **Firebase Auth** avec credentials Apple
- **Cache UserDefaults** pour performance
- **Gestion états** via Combine

### ✅ Authentification Android (Google)

- **Web Client ID** pour configuration
- **Token Google** pour Firebase
- **Firebase Auth** avec credentials Google
- **SharedPreferences** pour cache
- **StateFlow** pour gestion états

### 🔥 Firebase Commun

- **Collection users** pour données
- **Merge mode** pour sauvegardes partielles
- **Timestamps** pour tracking
- **onboardingInProgress** pour état
- **Authentication providers** vérifiés
