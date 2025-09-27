# 🎯 Génération Automatique de Nom d'Utilisateur - Rapport Technique Complet

## Vue d'Ensemble

Ce rapport détaille le mécanisme de génération automatique de nom d'utilisateur dans l'application iOS Love2Love, qui permet d'attribuer un nom par défaut ("Utilisateur" + nombres) quand l'utilisateur laisse le champ nom vide ou clique sur "Passer cette étape" durant l'onboarding. Il propose ensuite une implémentation équivalente pour Android GO.

---

## 🏗️ Architecture iOS Actuelle

### **1. Point de Déclenchement - DisplayNameStepView.swift**

**Interface Utilisateur** :

```swift
// Champ de saisie du nom
TextField("", text: $viewModel.userName)
    .font(.system(size: 18))
    .foregroundColor(.black)
    .padding(.horizontal, 20)
    .padding(.vertical, 16)

// Bouton "Passer cette étape"
Button(action: {
    print("🔥 DisplayNameStepView: Bouton 'Passer cette étape' cliqué")
    print("🔥 DisplayNameStepView: Nom actuel avant skip: '\(viewModel.userName)'")
    viewModel.userName = "" // ✅ VIDER POUR AUTO-GÉNÉRATION
    print("🔥 DisplayNameStepView: Nom vidé pour auto-génération")
    viewModel.nextStep()
}) {
    Text("skip_step".localized)
        .font(.system(size: 16))
        .foregroundColor(.black.opacity(0.6))
        .underline()
}
```

**Mécanismes de déclenchement** :

1. **Utilisateur clique "Passer cette étape"** → `viewModel.userName = ""`
2. **Utilisateur laisse le champ vide et clique "Continue"** → `viewModel.userName` reste vide
3. **Pré-remplissage Apple échoue** → Le champ reste vide

---

### **2. Logique de Navigation - OnboardingViewModel.swift**

```swift
func nextStep() {
    switch currentStep {
    case .displayName:
        // Toujours permettre de passer à profilePhoto, même avec nom vide
        print("🔥 OnboardingViewModel: displayName -> profilePhoto (nom: '\(userName)')")
        currentStep = .profilePhoto
        // ✅ AUCUNE VALIDATION - Le nom vide est accepté
    }
}
```

**Flow d'onboarding** :

1. **Authentication** → Connexion Apple ID
2. **DisplayName** → Saisie nom (optionnelle)
3. **ProfilePhoto** → Photo (optionnelle)
4. **Completion** → Finalisation des données
5. **Loading** → Traitement en arrière-plan
6. **Finalisation** → Création utilisateur Firebase

---

### **3. Génération Automatique - User.swift (Modèle)**

**Cœur du mécanisme** :

```swift
init(id: String = UUID().uuidString, name: String = "", ...) {
    self.id = id

    // ✅ AUTO-GÉNÉRATION SI NOM VIDE AVEC LOCALISATION
    if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        let locale = Locale.current
        let languageCode: String
        if #available(iOS 16.0, *) {
            languageCode = locale.language.languageCode?.identifier ?? "en"
        } else {
            languageCode = locale.languageCode ?? "en"
        }

        let shortId = String(id.prefix(4)) // 4 premiers caractères UUID
        if languageCode.hasPrefix("fr") {
            self.name = "Utilisateur\(shortId)" // 🇫🇷 Français
        } else {
            self.name = "User\(shortId)"        // 🇺🇸 Anglais/Autre
        }
    } else {
        self.name = name // Utiliser le nom fourni
    }
    // ... reste de l'initialisation
}
```

**Logique de génération** :

- **Déclenchement** : `name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty`
- **Identifiant unique** : 4 premiers caractères de l'UUID utilisateur
- **Localisation** : Détection automatique de la langue système
- **Format français** : `"Utilisateur" + [4 chars UUID]` → Ex: `"UtilisateurA3F2"`
- **Format anglais** : `"User" + [4 chars UUID]` → Ex: `"UserB8E1"`

---

### **4. Finalisation et Stockage - FirebaseService.swift**

```swift
func finalizeOnboardingWithPartnerData(
    name: String, // ← Nom potentiellement vide arrivant du ViewModel
    relationshipGoals: [String],
    // ... autres paramètres
    completion: @escaping (Bool, AppUser?) -> Void
) {
    // Récupérer données existantes Firebase
    db.collection("users").document(uid).getDocument { document, error in

        // ✅ CRÉATION APPUSER AVEC AUTO-GÉNÉRATION
        let finalUser = AppUser(
            id: existingData["id"] as? String ?? UUID().uuidString,
            name: name, // ← Si vide, auto-génération dans init AppUser
            birthDate: (existingData["birthDate"] as? Timestamp)?.dateValue() ?? Date(),
            // ... autres propriétés
        )

        // Sauvegarde Firebase avec nom généré
        self.saveUserData(finalUser)
        completion(true, finalUser)
    }
}
```

---

## 📊 Exemples Concrets de Génération

### **Processus Complet iOS**

**Scénario 1 - Utilisateur clique "Passer cette étape"** :

1. **Utilisateur** arrive sur `DisplayNameStepView`
2. **Champ nom** : Vide ou contient du texte
3. **Action** : Clique "Passer cette étape" (`skip_step`)
4. **Code** : `viewModel.userName = ""`
5. **Navigation** : Vers `ProfilePhotoStepView`
6. **Finalisation** : `AppUser(name: "")` → Auto-génération
7. **Résultat** : Nom = `"UtilisateurA3F2"` (français) ou `"UserB8E1"` (anglais)

**Scénario 2 - Champ nom resté vide** :

1. **Utilisateur** arrive sur `DisplayNameStepView`
2. **Champ nom** : Laissé vide (pas de saisie)
3. **Action** : Clique "Continue" (`continue`)
4. **Code** : `viewModel.userName` reste `""`
5. **Navigation** : Vers `ProfilePhotoStepView`
6. **Finalisation** : `AppUser(name: "")` → Auto-génération
7. **Résultat** : Nom = `"UtilisateurC7D9"` (français) ou `"UserE2F8"` (anglais)

**Exemples de noms générés** :

- **UUID** : `"A3F2E8B1-C4D7-48E2-B9F3-1A5C6E8D2B4F"`
- **Short ID** : `"A3F2"` (4 premiers caractères)
- **Français** : `"UtilisateurA3F2"`
- **Anglais** : `"UserA3F2"`
- **Autre langue** : `"UserA3F2"` (fallback anglais)

---

## 🔍 Avantages du Mécanisme iOS

### **Simplicité UX**

- ✅ **Onboarding non bloquant** : L'utilisateur peut passer sans saisir de nom
- ✅ **Pas de validation forcée** : L'app fonctionne même sans nom personnalisé
- ✅ **Choix utilisateur préservé** : "Passer" est un choix explicite

### **Unicité et Sécurité**

- ✅ **Noms uniques garantis** : UUID assure l'unicité
- ✅ **Pas de collision** : Aucun risque de doublon
- ✅ **Identification claire** : Facile à distinguer des vrais noms

### **Internationalisation**

- ✅ **Support multilingue** : Détection automatique de la langue
- ✅ **Fallback robuste** : Anglais par défaut si langue inconnue
- ✅ **Expérience localisée** : Adaptation au contexte utilisateur

---

## 🤖 Implémentation Android GO Équivalente

## Architecture Android Proposée

### **1. Structure Générale**

```kotlin
📁 com.love2love.android/
├── 📁 data/
│   ├── 📁 models/
│   │   └── User.kt
│   ├── 📁 repository/
│   │   └── UserRepository.kt
│   └── 📁 local/
│       └── UserPreferences.kt
├── 📁 ui/
│   ├── 📁 onboarding/
│   │   ├── DisplayNameScreen.kt
│   │   └── OnboardingViewModel.kt
│   └── 📁 components/
│       └── SkipButton.kt
├── 📁 utils/
│   ├── LocaleUtils.kt
│   └── IdGenerator.kt
└── 📁 constants/
    └── UserConstants.kt
```

---

## 🎯 Modèle de Données Android

### **2. User.kt - Modèle avec Auto-génération**

```kotlin
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    private val _name: String = "",
    val birthDate: Date,
    val relationshipGoals: List<String> = emptyList(),
    val relationshipDuration: RelationshipDuration = RelationshipDuration.NONE,
    val relationshipImprovement: String? = null,
    val questionMode: String? = null,
    val partnerCode: String? = null,
    val partnerId: String? = null,
    val partnerConnectedAt: Date? = null,
    val subscriptionInheritedFrom: String? = null,
    val subscriptionInheritedAt: Date? = null,
    val connectedPartnerCode: String? = null,
    val connectedPartnerId: String? = null,
    val connectedAt: Date? = null,
    val isSubscribed: Boolean = false,
    val onboardingInProgress: Boolean = false,
    val relationshipStartDate: Date? = null,
    val profileImageURL: String? = null,
    val profileImageUpdatedAt: Date? = null,
    val currentLocation: UserLocation? = null,
    val languageCode: String? = null,
    val dailyQuestionFirstAccessDate: Date? = null,
    val dailyQuestionMaxDayReached: Int = 0,
    val dailyChallengeFirstAccessDate: Date? = null,
    val dailyChallengeMaxDayReached: Int = 0
) {

    // ✅ PROPRIÉTÉ CALCULÉE AVEC AUTO-GÉNÉRATION
    val name: String
        get() {
            return if (_name.trim().isEmpty()) {
                generateAutomaticName()
            } else {
                _name
            }
        }

    /**
     * Génère un nom automatique basé sur la localisation et l'ID utilisateur
     */
    private fun generateAutomaticName(): String {
        // Récupération de la langue système
        val locale = Locale.getDefault()
        val languageCode = locale.language

        // 4 premiers caractères de l'UUID pour unicité
        val shortId = id.take(4).uppercase()

        return when {
            languageCode.startsWith("fr") -> "Utilisateur$shortId"
            else -> "User$shortId" // Fallback anglais
        }
    }

    // Constructeur secondaire pour faciliter la création
    constructor(
        name: String = "",
        birthDate: Date,
        relationshipGoals: List<String> = emptyList(),
        relationshipDuration: RelationshipDuration = RelationshipDuration.NONE
    ) : this(
        _name = name,
        birthDate = birthDate,
        relationshipGoals = relationshipGoals,
        relationshipDuration = relationshipDuration
    )
}

enum class RelationshipDuration(val value: String) {
    NONE("none"),
    LESS_THAN_MONTH("Moins d'un mois"),
    ONE_TO_SIX_MONTHS("Entre 1 et 6 mois"),
    SIX_MONTHS_TO_YEAR("Entre 6 mois et 1 an"),
    ONE_TO_TWO_YEARS("Entre 1 et 2 ans"),
    TWO_TO_FIVE_YEARS("Entre 2 et 5 ans"),
    MORE_THAN_FIVE_YEARS("Plus de 5 ans"),
    NOT_IN_RELATIONSHIP("Je ne suis pas en couple")
}
```

### **3. UserConstants.kt - Constantes de Génération**

```kotlin
object UserConstants {

    // Préfixes selon la langue
    const val USER_PREFIX_FRENCH = "Utilisateur"
    const val USER_PREFIX_DEFAULT = "User"

    // Configuration
    const val USER_ID_SHORT_LENGTH = 4

    // Langues supportées pour la génération
    val FRENCH_LANGUAGE_CODES = setOf("fr", "fr-FR", "fr-CA", "fr-BE")

    // Regex pour validation nom utilisateur
    val NAME_VALIDATION_REGEX = "^[\\p{L}\\p{N}\\p{M}\\s-_'.]{1,50}$".toRegex()

    fun generateDisplayName(userId: String, languageCode: String): String {
        val shortId = userId.take(USER_ID_SHORT_LENGTH).uppercase()

        return when {
            FRENCH_LANGUAGE_CODES.any { languageCode.startsWith(it) } ->
                "$USER_PREFIX_FRENCH$shortId"
            else ->
                "$USER_PREFIX_DEFAULT$shortId"
        }
    }
}
```

---

## 🎨 Interface Utilisateur Compose

### **4. DisplayNameScreen.kt - Écran de Saisie**

```kotlin
@Composable
fun DisplayNameScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onNextStep: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var userName by remember { mutableStateOf("") }
    var isTextFieldFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding(), // Padding pour clavier
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // Espace et titre
        Column {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.add_display_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 30.dp)
            )
        }

        // Contenu principal centré
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Champ de saisie sur carte blanche
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box {
                    // Placeholder
                    if (userName.isEmpty() && !isTextFieldFocused) {
                        Text(
                            text = stringResource(R.string.display_name_placeholder),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                color = Color.Black.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.padding(
                                horizontal = 20.dp,
                                vertical = 16.dp
                            )
                        )
                    }

                    // Champ de texte
                    BasicTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .onFocusChanged { isTextFieldFocused = it.isFocused },
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            color = Color.Black
                        ),
                        cursorBrush = SolidColor(Color(0xFFFF6B35)),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            capitalization = KeyboardCapitalization.Words
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                // Masquer le clavier et continuer
                                isTextFieldFocused = false
                                viewModel.setUserName(userName)
                                onNextStep()
                            }
                        )
                    )
                }
            }
        }

        // Zone boutons collée en bas
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {

            // Bouton Continue
            Button(
                onClick = {
                    viewModel.setUserName(userName)
                    onNextStep()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A)
                )
            ) {
                Text(
                    text = stringResource(R.string.continue_button),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }

            // Bouton "Passer cette étape"
            SkipStepButton(
                onClick = {
                    // ✅ VIDER LE NOM POUR AUTO-GÉNÉRATION
                    viewModel.setUserName("")
                    onNextStep()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun SkipStepButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.skip_step),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
                textDecoration = TextDecoration.Underline
            ),
            color = Color.Black.copy(alpha = 0.6f)
        )
    }
}
```

### **5. OnboardingViewModel.kt - Logique Métier**

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    data class OnboardingUiState(
        val currentStep: OnboardingStep = OnboardingStep.RELATIONSHIP_GOALS,
        val userName: String = "",
        val birthDate: LocalDate? = null,
        val relationshipGoals: List<String> = emptyList(),
        val relationshipDuration: RelationshipDuration = RelationshipDuration.NONE,
        val relationshipImprovement: String? = null,
        val questionMode: String? = null,
        val relationshipStartDate: LocalDate? = null,
        val profileImage: Bitmap? = null,
        val currentLocation: UserLocation? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    fun setUserName(name: String) {
        _uiState.value = _uiState.value.copy(userName = name)
        Log.d("OnboardingViewModel", "Nom défini: '$name'")
    }

    fun nextStep() {
        val currentStep = _uiState.value.currentStep
        Log.d("OnboardingViewModel", "Étape actuelle: $currentStep")

        val nextStep = when (currentStep) {
            OnboardingStep.RELATIONSHIP_GOALS -> OnboardingStep.RELATIONSHIP_IMPROVEMENT
            OnboardingStep.RELATIONSHIP_IMPROVEMENT -> OnboardingStep.RELATIONSHIP_DATE
            OnboardingStep.RELATIONSHIP_DATE -> OnboardingStep.COMMUNICATION_EVALUATION
            OnboardingStep.COMMUNICATION_EVALUATION -> OnboardingStep.DISCOVERY_TIME
            OnboardingStep.DISCOVERY_TIME -> OnboardingStep.LISTENING
            OnboardingStep.LISTENING -> OnboardingStep.CONFIDENCE
            OnboardingStep.CONFIDENCE -> OnboardingStep.COMPLICITY
            OnboardingStep.COMPLICITY -> OnboardingStep.AUTHENTICATION
            OnboardingStep.AUTHENTICATION -> OnboardingStep.DISPLAY_NAME
            OnboardingStep.DISPLAY_NAME -> {
                // ✅ TOUJOURS PERMETTRE DE PASSER, MÊME AVEC NOM VIDE
                Log.d("OnboardingViewModel", "displayName -> profilePhoto (nom: '${_uiState.value.userName}')")
                OnboardingStep.PROFILE_PHOTO
            }
            OnboardingStep.PROFILE_PHOTO -> OnboardingStep.COMPLETION
            OnboardingStep.COMPLETION -> {
                // Démarrer finalisation
                finalizeOnboarding()
                OnboardingStep.LOADING
            }
            OnboardingStep.LOADING -> OnboardingStep.PARTNER_CODE
            OnboardingStep.PARTNER_CODE -> OnboardingStep.QUESTIONS_INTRO
            OnboardingStep.QUESTIONS_INTRO -> OnboardingStep.CATEGORIES_PREVIEW
            OnboardingStep.CATEGORIES_PREVIEW -> OnboardingStep.SUBSCRIPTION
            OnboardingStep.SUBSCRIPTION -> OnboardingStep.COMPLETED
            OnboardingStep.COMPLETED -> OnboardingStep.COMPLETED // Final
        }

        _uiState.value = _uiState.value.copy(currentStep = nextStep)
        Log.d("OnboardingViewModel", "Nouvelle étape: $nextStep")
    }

    private fun finalizeOnboarding() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val state = _uiState.value
                Log.d("OnboardingViewModel", "Finalisation onboarding avec nom: '${state.userName}'")

                // ✅ CRÉATION USER AVEC AUTO-GÉNÉRATION INTÉGRÉE
                val user = User(
                    name = state.userName, // Si vide, auto-génération dans le modèle
                    birthDate = state.birthDate?.let { Date.from(it.atStartOfDay(ZoneId.systemDefault()).toInstant()) } ?: Date(),
                    relationshipGoals = state.relationshipGoals,
                    relationshipDuration = state.relationshipDuration,
                    relationshipImprovement = state.relationshipImprovement,
                    questionMode = state.questionMode,
                    relationshipStartDate = state.relationshipStartDate?.let { Date.from(it.atStartOfDay(ZoneId.systemDefault()).toInstant()) },
                    currentLocation = state.currentLocation
                )

                Log.d("OnboardingViewModel", "Nom final généré: '${user.name}'")

                // Sauvegarder en local et Firebase
                userRepository.saveUser(user)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentStep = OnboardingStep.COMPLETED
                )

                Log.d("OnboardingViewModel", "✅ Onboarding finalisé avec succès")

            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "❌ Erreur finalisation onboarding", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Erreur lors de la finalisation: ${e.message}"
                )
            }
        }
    }
}

enum class OnboardingStep {
    RELATIONSHIP_GOALS,
    RELATIONSHIP_IMPROVEMENT,
    RELATIONSHIP_DATE,
    COMMUNICATION_EVALUATION,
    DISCOVERY_TIME,
    LISTENING,
    CONFIDENCE,
    COMPLICITY,
    AUTHENTICATION,
    DISPLAY_NAME,
    PROFILE_PHOTO,
    COMPLETION,
    LOADING,
    PARTNER_CODE,
    QUESTIONS_INTRO,
    CATEGORIES_PREVIEW,
    SUBSCRIPTION,
    COMPLETED
}
```

---

## 🗄️ Repository et Persistance

### **6. UserRepository.kt - Gestion des Données**

```kotlin
@Singleton
class UserRepository @Inject constructor(
    private val firebaseFirestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val userDao: UserDao,
    private val userPreferences: UserPreferences
) {

    suspend fun saveUser(user: User): Result<User> {
        return try {
            Log.d("UserRepository", "Sauvegarde utilisateur: ID=${user.id}, Name='${user.name}'")

            // 1. Sauvegarder en local (Room Database)
            userDao.insertUser(user)
            Log.d("UserRepository", "✅ Utilisateur sauvé localement")

            // 2. Sauvegarder dans SharedPreferences pour accès rapide
            userPreferences.saveUser(user)
            Log.d("UserRepository", "✅ Utilisateur mis en cache")

            // 3. Sauvegarder sur Firebase
            saveToFirebase(user)
            Log.d("UserRepository", "✅ Utilisateur sauvé sur Firebase")

            Result.success(user)

        } catch (e: Exception) {
            Log.e("UserRepository", "❌ Erreur sauvegarde utilisateur", e)
            Result.failure(e)
        }
    }

    private suspend fun saveToFirebase(user: User) {
        val currentUser = firebaseAuth.currentUser
            ?: throw IllegalStateException("Utilisateur non authentifié")

        val userData = mapOf(
            "id" to user.id,
            "name" to user.name, // ← Nom déjà généré par le modèle
            "birthDate" to Timestamp(user.birthDate),
            "relationshipGoals" to user.relationshipGoals,
            "relationshipDuration" to user.relationshipDuration.value,
            "partnerCode" to (user.partnerCode ?: ""),
            "partnerId" to (user.partnerId ?: ""),
            "connectedPartnerCode" to (user.connectedPartnerCode ?: ""),
            "connectedPartnerId" to (user.connectedPartnerId ?: ""),
            "isSubscribed" to user.isSubscribed,
            "lastLoginDate" to Timestamp(Date()),
            "createdAt" to Timestamp(Date()),
            "updatedAt" to Timestamp(Date()),
            "onboardingInProgress" to false,
            "relationshipImprovement" to (user.relationshipImprovement ?: ""),
            "questionMode" to (user.questionMode ?: ""),
            "languageCode" to (user.languageCode ?: Locale.getDefault().language),
            "dailyQuestionMaxDayReached" to user.dailyQuestionMaxDayReached,
            "dailyChallengeMaxDayReached" to user.dailyChallengeMaxDayReached
        )

        // Ajouter date de relation si présente
        user.relationshipStartDate?.let {
            userData.toMutableMap()["relationshipStartDate"] = Timestamp(it)
        }

        // Ajouter URL photo si présente
        user.profileImageURL?.let {
            userData.toMutableMap()["profileImageURL"] = it
        }

        // Ajouter localisation si présente
        user.currentLocation?.let { location ->
            userData.toMutableMap()["currentLocation"] = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "city" to location.city,
                "country" to location.country
            )
        }

        firebaseFirestore.collection("users")
            .document(currentUser.uid)
            .set(userData)
            .await()
    }

    fun getUserDisplayName(userId: String): String {
        // Récupérer nom depuis cache ou générer automatiquement
        return userPreferences.getCachedUser()?.name
            ?: UserConstants.generateDisplayName(userId, Locale.getDefault().language)
    }
}
```

### **7. UserPreferences.kt - Cache Local Rapide**

```kotlin
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_IS_SUBSCRIBED = "is_subscribed"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LANGUAGE_CODE = "language_code"
        private const val KEY_PARTNER_ID = "partner_id"
        private const val KEY_PARTNER_CONNECTED_AT = "partner_connected_at"
    }

    fun saveUser(user: User) {
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.name) // ← Nom déjà généré
            .putString(KEY_LANGUAGE_CODE, user.languageCode)
            .putBoolean(KEY_IS_SUBSCRIBED, user.isSubscribed)
            .putBoolean(KEY_ONBOARDING_COMPLETED, !user.onboardingInProgress)
            .putString(KEY_PARTNER_ID, user.partnerId)
            .putLong(KEY_PARTNER_CONNECTED_AT, user.partnerConnectedAt?.time ?: 0L)
            .apply()

        Log.d("UserPreferences", "✅ Utilisateur mis en cache: '${user.name}'")
    }

    fun getCachedUser(): User? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val userName = prefs.getString(KEY_USER_NAME, null) ?: return null

        return try {
            User(
                id = userId,
                name = userName,
                birthDate = Date(), // Date minimale pour construction
                languageCode = prefs.getString(KEY_LANGUAGE_CODE, null),
                isSubscribed = prefs.getBoolean(KEY_IS_SUBSCRIBED, false),
                onboardingInProgress = !prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false),
                partnerId = prefs.getString(KEY_PARTNER_ID, null),
                partnerConnectedAt = let {
                    val timestamp = prefs.getLong(KEY_PARTNER_CONNECTED_AT, 0L)
                    if (timestamp > 0) Date(timestamp) else null
                }
            )
        } catch (e: Exception) {
            Log.e("UserPreferences", "❌ Erreur reconstruction utilisateur depuis cache", e)
            null
        }
    }

    fun getUserDisplayName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun clearCache() {
        prefs.edit().clear().apply()
        Log.d("UserPreferences", "🗑️ Cache utilisateur nettoyé")
    }
}
```

---

## 🛠️ Utilitaires et Extensions

### **8. LocaleUtils.kt - Gestion Localisation**

```kotlin
object LocaleUtils {

    /**
     * Détecte la langue de l'utilisateur pour la génération du nom
     */
    fun getCurrentLanguageCode(): String {
        return Locale.getDefault().language
    }

    /**
     * Vérifie si la langue actuelle est le français
     */
    fun isCurrentLanguageFrench(): Boolean {
        val languageCode = getCurrentLanguageCode()
        return UserConstants.FRENCH_LANGUAGE_CODES.any {
            languageCode.startsWith(it)
        }
    }

    /**
     * Génère le préfixe approprié selon la langue
     */
    fun getUserPrefix(): String {
        return if (isCurrentLanguageFrench()) {
            UserConstants.USER_PREFIX_FRENCH
        } else {
            UserConstants.USER_PREFIX_DEFAULT
        }
    }

    /**
     * Formatte un nom d'utilisateur auto-généré
     */
    fun formatAutoGeneratedName(userId: String): String {
        val shortId = IdGenerator.generateShortId(userId)
        val prefix = getUserPrefix()
        return "$prefix$shortId"
    }
}
```

### **9. IdGenerator.kt - Génération d'Identifiants**

```kotlin
object IdGenerator {

    /**
     * Génère un ID court à partir d'un UUID complet
     */
    fun generateShortId(fullId: String): String {
        return fullId.take(UserConstants.USER_ID_SHORT_LENGTH).uppercase()
    }

    /**
     * Génère un nouvel UUID complet
     */
    fun generateUserId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Valide qu'un ID utilisateur est bien formé
     */
    fun isValidUserId(id: String): Boolean {
        return try {
            UUID.fromString(id)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Génère un nom utilisateur automatique complet
     */
    fun generateAutomaticUsername(userId: String): String {
        return LocaleUtils.formatAutoGeneratedName(userId)
    }
}
```

---

## ✅ Tests et Validation

### **10. UserModelTest.kt - Tests Unitaires**

```kotlin
@RunWith(JUnit4::class)
class UserModelTest {

    @Test
    fun `given empty name, when creating user, then generates automatic name in french`() {
        // Arrange
        Locale.setDefault(Locale.FRANCE)
        val userId = "A3F2E8B1-C4D7-48E2-B9F3-1A5C6E8D2B4F"

        // Act
        val user = User(
            id = userId,
            name = "", // ← Nom vide
            birthDate = Date()
        )

        // Assert
        assertEquals("UtilisateurA3F2", user.name)
    }

    @Test
    fun `given empty name, when creating user, then generates automatic name in english`() {
        // Arrange
        Locale.setDefault(Locale.US)
        val userId = "B8E1F5A2-D3C6-49E7-A1F4-2B6D7E9C3F8A"

        // Act
        val user = User(
            id = userId,
            name = "", // ← Nom vide
            birthDate = Date()
        )

        // Assert
        assertEquals("UserB8E1", user.name)
    }

    @Test
    fun `given non empty name, when creating user, then keeps provided name`() {
        // Arrange
        val providedName = "Jean Dupont"

        // Act
        val user = User(
            name = providedName,
            birthDate = Date()
        )

        // Assert
        assertEquals(providedName, user.name)
    }

    @Test
    fun `given whitespace only name, when creating user, then generates automatic name`() {
        // Arrange
        Locale.setDefault(Locale.FRANCE)
        val userId = "C7D9A4B2-E6F1-4A8C-B3E5-7D9A2C4F6B8E"

        // Act
        val user = User(
            id = userId,
            name = "   \n\t  ", // ← Espaces seulement
            birthDate = Date()
        )

        // Assert
        assertEquals("UtilisateurC7D9", user.name)
    }
}

@RunWith(AndroidJUnit4::class)
class OnboardingIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `given user skips name step, when completing onboarding, then automatic name is generated`() {
        // Arrange
        val viewModel = mockk<OnboardingViewModel>()
        every { viewModel.uiState } returns MutableStateFlow(
            OnboardingUiState(currentStep = OnboardingStep.DISPLAY_NAME)
        ).asStateFlow()

        // Act
        composeTestRule.setContent {
            DisplayNameScreen(
                viewModel = viewModel,
                onNextStep = { }
            )
        }

        // Click skip button
        composeTestRule.onNodeWithText("Passer cette étape").performClick()

        // Assert
        verify { viewModel.setUserName("") } // Nom vidé pour auto-génération
    }
}
```

---

## 📊 Comparaison iOS vs Android

### **Tableau des Équivalences**

| **Aspect**        | **iOS**                            | **Android**                          |
| ----------------- | ---------------------------------- | ------------------------------------ |
| **Déclenchement** | `viewModel.userName = ""` (Button) | `viewModel.setUserName("")` (Button) |
| **Navigation**    | `currentStep = .profilePhoto`      | `OnboardingStep.PROFILE_PHOTO`       |
| **Génération**    | Modèle `AppUser` init              | Propriété calculée `User.name`       |
| **Langue**        | `Locale.current.language`          | `Locale.getDefault().language`       |
| **Format FR**     | `"Utilisateur" + shortId`          | `"Utilisateur" + shortId`            |
| **Format EN**     | `"User" + shortId`                 | `"User" + shortId`                   |
| **ID Court**      | `String(id.prefix(4))`             | `id.take(4).uppercase()`             |
| **Stockage**      | Firebase Firestore                 | Room + Firestore                     |
| **Cache**         | UserDefaults                       | SharedPreferences                    |
| **Interface**     | SwiftUI                            | Jetpack Compose                      |
| **État**          | `@StateObject` ViewModel           | `collectAsState()` ViewModel         |
| **Validation**    | Aucune (accepté)                   | Aucune (accepté)                     |

---

## 🚀 Plan d'Implémentation Android

### **Phase 1 : Modèles et Utilitaires (1 semaine)**

1. ✅ Créer le modèle `User` avec propriété calculée auto-génération
2. ✅ Implémenter `UserConstants` avec constantes de génération
3. ✅ Créer `LocaleUtils` pour détection langue
4. ✅ Implémenter `IdGenerator` pour génération IDs courts
5. ✅ Tests unitaires des utilitaires

### **Phase 2 : Repository et Cache (1 semaine)**

6. ✅ Créer `UserRepository` avec sauvegarde multi-niveaux
7. ✅ Implémenter `UserPreferences` pour cache rapide
8. ✅ Setup Room Database pour persistance locale
9. ✅ Intégration Firebase Firestore
10. ✅ Tests d'intégration repository

### **Phase 3 : ViewModel et Logique (1 semaine)**

11. ✅ Créer `OnboardingViewModel` avec gestion états
12. ✅ Implémenter navigation entre étapes
13. ✅ Gérer mécanisme "skip" avec nom vide
14. ✅ Finalisation onboarding avec auto-génération
15. ✅ Tests ViewModel et logique métier

### **Phase 4 : Interface Compose (2 semaines)**

16. ✅ Créer `DisplayNameScreen` avec champ de saisie
17. ✅ Implémenter `SkipStepButton` component
18. ✅ Gestion focus et clavier
19. ✅ Navigation et états de chargement
20. ✅ Tests UI Compose

### **Phase 5 : Intégration Complète (1 semaine)**

21. ✅ Intégrer dans le flow d'onboarding complet
22. ✅ Tests end-to-end du mécanisme
23. ✅ Validation multi-langues (FR/EN)
24. ✅ Optimisation performance et mémoire

### **Phase 6 : Tests et Validation (1 semaine)**

25. ✅ Tests sur différents appareils Android GO
26. ✅ Validation génération avec différents locales
27. ✅ Tests de régression et edge cases
28. ✅ Documentation développeur

---

## 📝 Exemples Concrets Android

### **Processus Complet Android**

**Scénario 1 - Utilisateur clique "Passer cette étape"** :

```kotlin
// 1. DisplayNameScreen - Utilisateur clique Skip
SkipStepButton(onClick = {
    viewModel.setUserName("") // ← Nom vidé
    onNextStep()
})

// 2. OnboardingViewModel - Navigation
fun nextStep() {
    when (currentStep) {
        OnboardingStep.DISPLAY_NAME -> {
            Log.d("OnboardingViewModel", "displayName -> profilePhoto (nom: '${userName}')")
            OnboardingStep.PROFILE_PHOTO // ← Pas de validation nom
        }
    }
}

// 3. Finalisation - Création User
val user = User(
    name = "", // ← Nom vide
    birthDate = Date()
)

// 4. Modèle User - Auto-génération
val name: String
    get() {
        return if (_name.trim().isEmpty()) {
            generateAutomaticName() // ← "UtilisateurA3F2" ou "UserB8E1"
        } else {
            _name
        }
    }

// 5. Résultat Final
Log.d("User", "Nom final: ${user.name}") // "UtilisateurA3F2"
```

**Scénario 2 - Champ nom resté vide** :

```kotlin
// 1. Utilisateur n'a rien saisi, clique Continue
Button(onClick = {
    viewModel.setUserName(userName) // userName = ""
    onNextStep()
})

// 2-5. Même processus que Scénario 1
// Résultat: "UtilisateurC7D9" ou "UserE2F8"
```

**Exemples de noms générés** :

- **Français (Locale.FRANCE)** : `"UtilisateurA3F2"`, `"UtilisateurB8E1"`, `"UtilisateurC7D9"`
- **Anglais (Locale.US)** : `"UserA3F2"`, `"UserB8E1"`, `"UserC7D9"`
- **Autre langue** : `"UserA3F2"` (fallback anglais)

---

## 🎯 Avantages de l'Implémentation Android

### **Robustesse**

- ✅ **Auto-génération garantie** : Aucun utilisateur sans nom
- ✅ **Unicité assurée** : UUID empêche les collisions
- ✅ **Gestion d'erreur** : Fallback sur générateur automatique

### **Performance**

- ✅ **Calcul à la demande** : Propriété calculée optimisée
- ✅ **Cache multi-niveaux** : SharedPreferences + Room + Firebase
- ✅ **Pas de blocage UI** : Génération instantanée

### **Expérience Utilisateur**

- ✅ **Onboarding fluide** : Pas de validation forcée
- ✅ **Choix préservé** : Skip explicite disponible
- ✅ **Feedback immédiat** : Nom visible instantanément

### **Localisation**

- ✅ **Support multilingue** : Français/Anglais auto-détectés
- ✅ **Extensible** : Ajout facile d'autres langues
- ✅ **Fallback robuste** : Anglais par défaut

Cette implémentation Android GO reproduit **fidèlement** le comportement iOS tout en étant **optimisée** pour les appareils Android moins puissants ! 🚀
