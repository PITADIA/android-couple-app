# Rapport Technique : Système Code Partenaire et Déconnexion - CoupleApp

## 📋 Vue d'Ensemble

Ce document détaille le système complet de gestion des codes partenaires dans l'application CoupleApp, incluant la détection d'état de connexion, l'interface adaptative selon l'état, le processus de déconnexion, l'intégration Firebase et les guidelines pour l'implémentation Android.

## 🎯 Architecture du Système

### 1. Navigation depuis le Menu Principal

**Fichier:** `Views/Main/MenuView.swift`

#### 1.1 Point d'Entrée Menu

```swift
// Dans la section "À propos de moi"
ProfileRowView(
    title: "partner_code".localized,     // "Code partenaire"
    value: "",
    showChevron: true,
    action: {
        showingPartnerCode = true        // ✅ Déclencheur navigation
    }
)

// Sheet modal plein écran
.sheet(isPresented: $showingPartnerCode) {
    PartnerManagementView()
        .environmentObject(appState)
}
```

#### 1.2 Autres Points d'Accès

**Depuis HomeContentView :**

```swift
// Section invitation partenaire (si pas connecté)
if !hasConnectedPartner {
    PartnerInviteView {
        activeSheet = .partnerManagement  // ✅ Navigation vers gestion partenaire
    }
}

// Clic sur avatar partenaire
PartnerDistanceView(
    onPartnerAvatarTap: {
        activeSheet = .partnerManagement  // ✅ Navigation vers gestion partenaire
    }
)
```

### 2. Service Central - PartnerCodeService

**Fichier:** `Services/PartnerCodeService.swift`

#### 2.1 Structure du Service

```swift
class PartnerCodeService: ObservableObject {
    static let shared = PartnerCodeService()

    // 📊 États observables
    @Published var generatedCode: String?          // Code généré par l'utilisateur
    @Published var isLoading = false              // État de chargement
    @Published var errorMessage: String?          // Message d'erreur
    @Published var isConnected = false            // ✅ État clé : connecté ou non
    @Published var partnerInfo: PartnerInfo?      // Informations du partenaire

    struct PartnerInfo {
        let id: String
        let name: String
        let connectedAt: Date
        let isSubscribed: Bool              // État abonnement partenaire
    }
}
```

#### 2.2 Détection d'État de Connexion

```swift
func checkExistingConnection() async {
    print("🔍 PartnerCodeService: checkExistingConnection - Début vérification")
    guard let currentUser = Auth.auth().currentUser else {
        print("❌ PartnerCodeService: Utilisateur non connecté")
        return
    }

    do {
        // 1️⃣ Récupération données utilisateur depuis Firestore
        let doc = try await db.collection("users").document(currentUser.uid).getDocument()

        if let data = doc.data(),
           let partnerId = data["partnerId"] as? String,
           !partnerId.isEmpty {

            print("🔍 PartnerCodeService: Partenaire trouvé")

            // 2️⃣ Récupération infos partenaire via Cloud Function sécurisée
            let functions = Functions.functions()
            let result = try await functions.httpsCallable("getPartnerInfo").call([
                "partnerId": partnerId
            ])

            if let resultData = result.data as? [String: Any],
               let success = resultData["success"] as? Bool,
               success,
               let partnerData = resultData["partnerInfo"] as? [String: Any],
               let connectedAt = data["partnerConnectedAt"] as? Timestamp {

                let partnerName = partnerData["name"] as? String ?? "Partenaire"
                let partnerIsSubscribed = partnerData["isSubscribed"] as? Bool ?? false

                // 3️⃣ Mise à jour état connecté
                await MainActor.run {
                    self.isConnected = true          // ✅ ÉTAT CLÉ
                    self.partnerInfo = PartnerInfo(
                        id: partnerId,
                        name: partnerName,
                        connectedAt: connectedAt.dateValue(),
                        isSubscribed: partnerIsSubscribed
                    )
                }
            }
        } else {
            // 4️⃣ Pas de partenaire connecté
            print("🔍 PartnerCodeService: Aucun partenaire connecté")
            await MainActor.run {
                self.isConnected = false         // ✅ ÉTAT CLÉ
                self.partnerInfo = nil
            }
        }

        // 5️⃣ Vérification code généré par l'utilisateur
        let codeSnapshot = try await db.collection("partnerCodes")
            .whereField("userId", isEqualTo: currentUser.uid)
            .getDocuments()

        if let codeDoc = codeSnapshot.documents.first {
            await MainActor.run {
                self.generatedCode = codeDoc.documentID
            }
        }

    } catch {
        print("❌ PartnerCodeService: Erreur vérification connexion: \(error)")
    }
}
```

## 🎨 Interface Adaptative - PartnerManagementView

**Fichier:** `Views/Settings/PartnerManagementView.swift`

### 3. Logique d'Affichage Conditionnel

#### 3.1 Structure Principale

```swift
struct PartnerManagementView: View {
    @StateObject private var partnerCodeService = PartnerCodeService.shared
    @State private var showingDisconnectAlert = false

    var body: some View {
        NavigationView {
            ZStack {
                // 🎨 Fond dégradé rose cohérent
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FFE5F1"),    // Rose très clair
                        Color(hex: "#FFF0F8")     // Rose plus clair
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )

                ScrollView {
                    VStack(spacing: 40) {
                        // 🎯 TITRE CONDITIONNEL selon l'état
                        if partnerCodeService.isConnected {
                            Text("connected_with_partner".localized)
                                .font(.system(size: 28, weight: .bold))
                        } else {
                            VStack(spacing: 20) {
                                Text("connect_with_partner".localized)
                                    .font(.system(size: 28, weight: .bold))

                                Text("connect_partner_description".localized)
                                    .font(.system(size: 16))
                                    .foregroundColor(.black.opacity(0.7))
                            }
                        }

                        // 🔄 CONTENU CONDITIONNEL selon l'état
                        if partnerCodeService.isConnected {
                            connectedSection          // ✅ Vue déconnexion
                        } else {
                            disconnectedSection       // ✅ Vue connexion
                        }
                    }
                }
            }
        }
        .onAppear {
            Task {
                await partnerCodeService.checkExistingConnection()  // 🔍 Vérification état
            }
        }
    }
}
```

### 4. Vue État Connecté - Section Déconnexion

#### 4.1 Interface Partenaire Connecté

```swift
private var connectedSection: some View {
    VStack(spacing: 25) {
        // 👤 INFORMATIONS DU PARTENAIRE
        if let partnerInfo = partnerCodeService.partnerInfo {
            VStack(spacing: 15) {
                // Nom du partenaire
                Text("partner_name".localized + " \(partnerInfo.name)")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.black)

                // Date de connexion
                Text("connected_on".localized + " \(formatDate(partnerInfo.connectedAt))")
                    .font(.system(size: 14))
                    .foregroundColor(.black.opacity(0.7))

                // 👑 Badge Premium si partenaire abonné
                if partnerInfo.isSubscribed {
                    HStack {
                        Image(systemName: "crown.fill")
                            .foregroundColor(.yellow)
                        Text("shared_premium".localized)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.black)
                    }
                }
            }
            .padding(.vertical, 20)
            .padding(.horizontal, 25)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.95))
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
            )
            .padding(.horizontal, 30)
        }

        // ❌ BOUTON DE DÉCONNEXION
        Button("disconnect".localized) {
            showingDisconnectAlert = true               // ✅ Alerte confirmation
        }
        .font(.system(size: 16, weight: .medium))
        .foregroundColor(.red)                          // Rouge destructif
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.95))
                .shadow(color: Color.black.opacity(0.1), radius: 4)
        )
        .padding(.horizontal, 30)
    }
}
```

#### 4.2 Alerte de Confirmation Déconnexion

```swift
.alert("Déconnecter le partenaire", isPresented: $showingDisconnectAlert) {
    Button("Annuler", role: .cancel) { }
    Button("Déconnecter", role: .destructive) {
        Task {
            await partnerCodeService.disconnectPartner()    // ✅ Action déconnexion
        }
    }
} message: {
    Text("disconnect_confirmation".localized)
}
```

### 5. Vue État Non Connecté - Section Connexion

#### 5.1 Interface de Connexion Partenaire

```swift
private var disconnectedSection: some View {
    VStack(spacing: 40) {
        // 📱 SECTION CODE GÉNÉRÉ (affichage du code de l'utilisateur)
        if partnerCodeService.isLoading {
            loadingCodeSection
        } else if let code = partnerCodeService.generatedCode {
            generatedCodeSection(code: code)
        } else {
            // Aucun code généré
            Button("generate_code".localized) {
                Task {
                    _ = await partnerCodeService.generatePartnerCode()
                }
            }
        }

        // ✏️ SECTION SAISIE CODE (pour se connecter à un partenaire)
        enterCodeSection
    }
}
```

#### 5.2 Section Saisie Code Partenaire

```swift
private var enterCodeSection: some View {
    VStack(spacing: 20) {
        Text("enter_partner_code".localized)
            .font(.system(size: 16))
            .foregroundColor(.black)

        // 🔢 Champ de saisie code (8 chiffres)
        TextField("enter_code".localized, text: $enteredCode)
            .font(.system(size: 18, weight: .medium))
            .multilineTextAlignment(.center)
            .padding(.vertical, 16)
            .padding(.horizontal, 20)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.95))
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
            )
            .keyboardType(.numberPad)                    // Clavier numérique
            .onChange(of: enteredCode) { _, newValue in
                // Validation : seulement 8 chiffres
                if newValue.count > 8 {
                    enteredCode = String(newValue.prefix(8))
                }
                enteredCode = newValue.filter { $0.isNumber }
            }

        // 🔗 Bouton "Connecter"
        Button("connect".localized) {
            Task {
                await partnerCodeService.connectWithPartnerCode(enteredCode, context: .menu)
            }
        }
        .font(.system(size: 18, weight: .bold))
        .foregroundColor(Color(hex: "#FD267A"))
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 28)
                .stroke(Color(hex: "#FD267A"), lineWidth: 2)
                .opacity(enteredCode.count == 8 ? 1.0 : 0.5)    // Opacité selon validité
        )
        .disabled(enteredCode.count != 8 || partnerCodeService.isLoading)
    }
}
```

## 🔥 Processus de Déconnexion Firebase

### 6. Méthode de Déconnexion Sécurisée

**Service:** `PartnerCodeService.disconnectPartner()`

```swift
func disconnectPartner() async -> Bool {
    print("🔗 PartnerCodeService: disconnectPartner - Début déconnexion")

    guard Auth.auth().currentUser != nil else {
        print("❌ PartnerCodeService: Utilisateur non connecté")
        return false
    }

    await MainActor.run {
        self.isLoading = true               // ✅ État de chargement
        self.errorMessage = nil
    }

    do {
        // 📡 CLOUD FUNCTION SÉCURISÉE pour déconnexion
        print("🔗 PartnerCodeService: Appel Cloud Function disconnectPartners")
        let functions = Functions.functions()
        let result = try await functions.httpsCallable("disconnectPartners").call([:])

        guard let data = result.data as? [String: Any],
              let success = data["success"] as? Bool,
              success else {
            print("❌ PartnerCodeService: Échec Cloud Function")
            await MainActor.run {
                self.errorMessage = "Erreur lors de la déconnexion"
                self.isLoading = false
            }
            return false
        }

        // ✅ MISE À JOUR ÉTAT LOCAL
        await MainActor.run {
            self.isConnected = false        // ✅ État principal
            self.partnerInfo = nil          // ✅ Effacement infos partenaire
            self.isLoading = false
        }

        // 🗑️ NETTOYAGE CACHE LOCAL
        UserDefaults.standard.removeObject(forKey: "lastCoupleId")
        print("✅ PartnerCodeService: lastCoupleId vidé à la déconnexion")

        // 📢 NOTIFICATION SYSTÈME
        await MainActor.run {
            NotificationCenter.default.post(
                name: .partnerDisconnected,
                object: nil
            )
        }

        print("✅ PartnerCodeService: Déconnexion réussie via Cloud Function")
        return true

    } catch {
        print("❌ PartnerCodeService: Erreur déconnexion: \(error)")
        await MainActor.run {
            self.errorMessage = "Erreur lors de la déconnexion"
            self.isLoading = false
        }
        return false
    }
}
```

### 7. Cloud Function Firebase - disconnectPartners

**Fichier:** `firebase/functions/index.js`

```javascript
exports.disconnectPartners = functions.https.onCall(async (data, context) => {
  // Vérifier l'authentification
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Utilisateur non authentifié"
    );
  }

  const currentUserId = context.auth.uid;
  console.log(`🔗 disconnectPartners: Début déconnexion pour utilisateur`);

  try {
    // 1️⃣ Récupérer les données utilisateur
    const userDoc = await admin
      .firestore()
      .collection("users")
      .doc(currentUserId)
      .get();

    if (!userDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Utilisateur non trouvé"
      );
    }

    const userData = userDoc.data();
    const partnerId = userData.partnerId;

    if (!partnerId) {
      console.log("🔗 disconnectPartners: Aucun partenaire connecté");
      return { success: true, message: "Aucun partenaire à déconnecter" };
    }

    // 2️⃣ Batch de déconnexion bidirectionnelle
    const batch = admin.firestore().batch();

    // Déconnecter l'utilisateur actuel
    batch.update(admin.firestore().collection("users").doc(currentUserId), {
      partnerId: admin.firestore.FieldValue.delete(),
      partnerConnectedAt: admin.firestore.FieldValue.delete(),
      connectedPartnerCode: admin.firestore.FieldValue.delete(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Déconnecter le partenaire
    batch.update(admin.firestore().collection("users").doc(partnerId), {
      partnerId: admin.firestore.FieldValue.delete(),
      partnerConnectedAt: admin.firestore.FieldValue.delete(),
      connectedPartnerCode: admin.firestore.FieldValue.delete(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // 3️⃣ Supprimer les codes partenaires temporaires
    const codesSnapshot = await admin
      .firestore()
      .collection("partnerCodes")
      .where("userId", "in", [currentUserId, partnerId])
      .get();

    codesSnapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });

    // 4️⃣ Nettoyer les données partagées
    batch.delete(
      admin.firestore().collection("sharedPartnerData").doc(currentUserId)
    );
    batch.delete(
      admin.firestore().collection("sharedPartnerData").doc(partnerId)
    );

    // 5️⃣ Exécuter toutes les opérations
    await batch.commit();

    console.log("✅ disconnectPartners: Déconnexion réussie");

    return {
      success: true,
      message: "Déconnexion réussie",
    };
  } catch (error) {
    console.error("❌ disconnectPartners: Erreur:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});
```

## 🔍 Détection d'État - Logique Complète

### 8. Flux de Détection d'État

```mermaid
graph TD
    A[Ouverture PartnerManagementView] --> B[onAppear]
    B --> C[checkExistingConnection()]
    C --> D[Auth.auth().currentUser]
    D --> E{Utilisateur connecté?}
    E -->|Non| F[Affichage vue connexion]
    E -->|Oui| G[Récupération document Firestore]
    G --> H{partnerId existe?}
    H -->|Non| I[isConnected = false]
    H -->|Oui| J[Cloud Function getPartnerInfo]
    J --> K{Succès récupération?}
    K -->|Non| L[Affichage erreur]
    K -->|Oui| M[isConnected = true]
    M --> N[partnerInfo = données]
    I --> O[Affichage vue connexion]
    N --> P[Affichage vue déconnexion]
```

### 9. États de l'Interface

| État             | isConnected | partnerInfo        | Vue Affichée                                         |
| ---------------- | ----------- | ------------------ | ---------------------------------------------------- |
| **Non Connecté** | `false`     | `nil`              | Section connexion avec génération code + saisie code |
| **Connecté**     | `true`      | `PartnerInfo(...)` | Section partenaire avec infos + bouton déconnexion   |
| **Chargement**   | Variable    | Variable           | ProgressView avec états transitoires                 |
| **Erreur**       | Variable    | Variable           | Message d'erreur + retry                             |

## 🌍 Système Multilingue

### 10. Clés de Localisation

**Fichier:** `Localizable.xcstrings`

```json
{
  "partner_code": {
    "en": { "value": "Partner Code" },
    "fr": { "value": "Code Partenaire" },
    "es": { "value": "Código de Pareja" }
  },
  "connected_with_partner": {
    "en": { "value": "Connected with Partner" },
    "fr": { "value": "Connecté avec Partenaire" },
    "es": { "value": "Conectado con Pareja" }
  },
  "connect_with_partner": {
    "en": { "value": "Connect with Partner" },
    "fr": { "value": "Se connecter avec Partenaire" },
    "es": { "value": "Conectar con Pareja" }
  },
  "disconnect": {
    "en": { "value": "Disconnect" },
    "fr": { "value": "Déconnecter" },
    "es": { "value": "Desconectar" }
  },
  "disconnect_confirmation": {
    "en": {
      "value": "Are you sure you want to disconnect from your partner? This action cannot be undone."
    },
    "fr": {
      "value": "Êtes-vous sûr de vouloir vous déconnecter de votre partenaire ? Cette action est irréversible."
    },
    "es": {
      "value": "¿Estás seguro de que quieres desconectarte de tu pareja? Esta acción no se puede deshacer."
    }
  },
  "partner_name": {
    "en": { "value": "Partner:" },
    "fr": { "value": "Partenaire :" },
    "es": { "value": "Pareja:" }
  },
  "connected_on": {
    "en": { "value": "Connected on " },
    "fr": { "value": "Connecté le " },
    "es": { "value": "Conectado el " }
  },
  "shared_premium": {
    "en": { "value": "Shared Premium" },
    "fr": { "value": "Premium Partagé" },
    "es": { "value": "Premium Compartido" }
  }
}
```

## 🤖 Guide d'Implémentation Android

### 11. Architecture Android Recommandée

#### 11.1 Structure des Composants

```kotlin
// PartnerManagementActivity
class PartnerManagementActivity : ComponentActivity() {
    private lateinit var viewModel: PartnerManagementViewModel
    private lateinit var partnerCodeService: PartnerCodeService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PartnerManagementScreen(
                viewModel = viewModel,
                onBackPressed = { finish() }
            )
        }
    }
}

// ViewModel pour gérer l'état
class PartnerManagementViewModel(
    private val partnerCodeService: PartnerCodeService
) : ViewModel() {

    private val _uiState = MutableLiveData<PartnerUiState>()
    val uiState: LiveData<PartnerUiState> = _uiState

    data class PartnerUiState(
        val isConnected: Boolean = false,
        val partnerInfo: PartnerInfo? = null,
        val generatedCode: String? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val showDisconnectDialog: Boolean = false
    )

    init {
        checkExistingConnection()
    }

    fun checkExistingConnection() {
        viewModelScope.launch {
            partnerCodeService.checkExistingConnection()
        }
    }

    fun disconnectPartner() {
        viewModelScope.launch {
            val success = partnerCodeService.disconnectPartner()
            if (success) {
                _uiState.value = _uiState.value?.copy(
                    isConnected = false,
                    partnerInfo = null,
                    showDisconnectDialog = false
                )
            }
        }
    }
}
```

#### 11.2 Interface Compose Conditionnelle

```kotlin
@Composable
fun PartnerManagementScreen(
    viewModel: PartnerManagementViewModel,
    onBackPressed: () -> Unit
) {
    val uiState by viewModel.uiState.observeAsState()

    LaunchedEffect(Unit) {
        viewModel.checkExistingConnection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.partner_code)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                }
            )
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFE5F1),  // Rose clair
                            Color(0xFFFFF0F8)   // Rose plus clair
                        )
                    )
                )
                .padding(paddingValues)
        ) {

            // 🎯 AFFICHAGE CONDITIONNEL selon l'état
            when (uiState?.isConnected) {
                true -> {
                    // ✅ VUE ÉTAT CONNECTÉ
                    ConnectedPartnerSection(
                        partnerInfo = uiState.partnerInfo,
                        isLoading = uiState.isLoading ?: false,
                        onDisconnectClick = {
                            viewModel.showDisconnectDialog()
                        }
                    )
                }
                false -> {
                    // ✅ VUE ÉTAT NON CONNECTÉ
                    DisconnectedPartnerSection(
                        generatedCode = uiState.generatedCode,
                        isLoading = uiState.isLoading ?: false,
                        onGenerateCode = { viewModel.generateCode() },
                        onConnectWithCode = { code -> viewModel.connectWithCode(code) }
                    )
                }
                null -> {
                    // ⏳ ÉTAT DE CHARGEMENT INITIAL
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 🚨 DIALOG DE CONFIRMATION DÉCONNEXION
            if (uiState?.showDisconnectDialog == true) {
                DisconnectConfirmationDialog(
                    onConfirm = { viewModel.disconnectPartner() },
                    onDismiss = { viewModel.dismissDisconnectDialog() }
                )
            }
        }
    }
}
```

### 12. Vue État Connecté Android

```kotlin
@Composable
fun ConnectedPartnerSection(
    partnerInfo: PartnerInfo?,
    isLoading: Boolean,
    onDisconnectClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {

        // 📄 TITRE
        Text(
            text = stringResource(R.string.connected_with_partner),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // 👤 INFORMATIONS PARTENAIRE
        partnerInfo?.let { info ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Nom partenaire
                    Text(
                        text = "${stringResource(R.string.partner_name)} ${info.name}",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )

                    // Date connexion
                    Text(
                        text = "${stringResource(R.string.connected_on)} ${formatDate(info.connectedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    // 👑 Badge Premium
                    if (info.isSubscribed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),  // Or
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.shared_premium),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ❌ BOUTON DÉCONNEXION
        Button(
            onClick = onDisconnectClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = stringResource(R.string.disconnect),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
```

### 13. Vue État Non Connecté Android

```kotlin
@Composable
fun DisconnectedPartnerSection(
    generatedCode: String?,
    isLoading: Boolean,
    onGenerateCode: () -> Unit,
    onConnectWithCode: (String) -> Unit
) {
    var enteredCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {

        // 📄 TITRE ET DESCRIPTION
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.connect_with_partner),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.connect_partner_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 📱 SECTION CODE GÉNÉRÉ
        if (generatedCode != null) {
            GeneratedCodeSection(
                code = generatedCode,
                onShare = { /* Logique partage */ }
            )
        } else {
            Button(
                onClick = onGenerateCode,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.generate_code))
                }
            }
        }

        // ✏️ SECTION SAISIE CODE
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.enter_partner_code),
                style = MaterialTheme.typography.bodyLarge
            )

            // 🔢 CHAMP SAISIE CODE
            OutlinedTextField(
                value = enteredCode,
                onValueChange = { value ->
                    // Limitation à 8 chiffres uniquement
                    if (value.length <= 8 && value.all { it.isDigit() }) {
                        enteredCode = value
                    }
                },
                label = { Text(stringResource(R.string.enter_code)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 🔗 BOUTON CONNECTER
            Button(
                onClick = { onConnectWithCode(enteredCode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = enteredCode.length == 8 && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF267A)  // Rose principal
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                } else {
                    Text(
                        text = stringResource(R.string.connect),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
```

### 14. Service Android - PartnerCodeService

```kotlin
class PartnerCodeService(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions
) {

    private val _isConnected = MutableLiveData<Boolean>()
    val isConnected: LiveData<Boolean> = _isConnected

    private val _partnerInfo = MutableLiveData<PartnerInfo?>()
    val partnerInfo: LiveData<PartnerInfo?> = _partnerInfo

    private val _generatedCode = MutableLiveData<String?>()
    val generatedCode: LiveData<String?> = _generatedCode

    data class PartnerInfo(
        val id: String,
        val name: String,
        val connectedAt: Date,
        val isSubscribed: Boolean
    )

    suspend fun checkExistingConnection(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser
                    ?: return@withContext Result.failure(Exception("Utilisateur non connecté"))

                // 1️⃣ Récupération document utilisateur
                val userDoc = firestore.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()

                val userData = userDoc.data
                val partnerId = userData?.get("partnerId") as? String

                if (!partnerId.isNullOrEmpty()) {
                    // 2️⃣ Récupération infos partenaire via Cloud Function
                    val result = functions
                        .getHttpsCallable("getPartnerInfo")
                        .call(hashMapOf("partnerId" to partnerId))
                        .await()

                    val resultData = result.data as? Map<String, Any>
                    val success = resultData?.get("success") as? Boolean ?: false

                    if (success) {
                        val partnerData = resultData?.get("partnerInfo") as? Map<String, Any>
                        val connectedAtTimestamp = userData["partnerConnectedAt"] as? Timestamp

                        if (partnerData != null && connectedAtTimestamp != null) {
                            val partnerInfo = PartnerInfo(
                                id = partnerId,
                                name = partnerData["name"] as? String ?: "Partenaire",
                                connectedAt = connectedAtTimestamp.toDate(),
                                isSubscribed = partnerData["isSubscribed"] as? Boolean ?: false
                            )

                            // 3️⃣ Mise à jour état connecté
                            withContext(Dispatchers.Main) {
                                _isConnected.value = true
                                _partnerInfo.value = partnerInfo
                            }
                        }
                    }
                } else {
                    // 4️⃣ Pas de partenaire connecté
                    withContext(Dispatchers.Main) {
                        _isConnected.value = false
                        _partnerInfo.value = null
                    }
                }

                // 5️⃣ Vérification code généré
                val codeQuery = firestore.collection("partnerCodes")
                    .whereEqualTo("userId", currentUser.uid)
                    .get()
                    .await()

                if (!codeQuery.documents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _generatedCode.value = codeQuery.documents.first().id
                    }
                }

                Result.success(Unit)

            } catch (e: Exception) {
                Log.e("PartnerCodeService", "Erreur vérification connexion", e)
                Result.failure(e)
            }
        }
    }

    suspend fun disconnectPartner(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // 📡 Appel Cloud Function déconnexion
                val result = functions
                    .getHttpsCallable("disconnectPartners")
                    .call(emptyMap<String, Any>())
                    .await()

                val resultData = result.data as? Map<String, Any>
                val success = resultData?.get("success") as? Boolean ?: false

                if (success) {
                    // ✅ Mise à jour état local
                    withContext(Dispatchers.Main) {
                        _isConnected.value = false
                        _partnerInfo.value = null
                    }

                    // 🗑️ Nettoyage cache local
                    val prefs = context.getSharedPreferences("couple_cache", Context.MODE_PRIVATE)
                    prefs.edit()
                        .remove("lastCoupleId")
                        .apply()

                    Result.success(true)
                } else {
                    Result.failure(Exception("Échec déconnexion"))
                }

            } catch (e: Exception) {
                Log.e("PartnerCodeService", "Erreur déconnexion", e)
                Result.failure(e)
            }
        }
    }
}
```

### 15. Dialog de Confirmation Android

```kotlin
@Composable
fun DisconnectConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.disconnect_partner_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(R.string.disconnect_confirmation),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.disconnect),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
```

## ✅ Checklist d'Implémentation Android

### 16. Étapes de Développement

#### 🎯 **Architecture et Navigation**

- [ ] Créer `PartnerManagementActivity` avec navigation depuis menu
- [ ] Implémenter `PartnerManagementViewModel` avec états
- [ ] Configurer navigation conditionnelle selon état connexion
- [ ] Ajouter gestion back button et fermeture modale
- [ ] Tester navigation depuis différents points d'entrée

#### 📱 **Interface Utilisateur**

- [ ] Créer composables `ConnectedPartnerSection` et `DisconnectedPartnerSection`
- [ ] Implémenter interface conditionnelle avec états loading/error/success
- [ ] Ajouter dégradé rose cohérent avec l'app iOS
- [ ] Configurer champs de saisie avec validation (8 chiffres)
- [ ] Implémenter animations et transitions fluides

#### 🔐 **Logique de Connexion/Déconnexion**

- [ ] Créer `PartnerCodeService` Android avec LiveData
- [ ] Implémenter `checkExistingConnection()` avec Firestore
- [ ] Ajouter appels Cloud Functions sécurisés
- [ ] Configurer `disconnectPartner()` avec gestion d'erreurs
- [ ] Tester processus complet connexion/déconnexion

#### 🌍 **Localisation**

- [ ] Créer fichiers `strings.xml` pour toutes les langues
- [ ] Traduire toutes les clés (partner_code, disconnect, etc.)
- [ ] Implémenter formatage dates localisé
- [ ] Tester changement de langue dynamique
- [ ] Vérifier cohérence terminologie avec iOS

#### 🔥 **Firebase Integration**

- [ ] Configurer Firebase Functions SDK Android
- [ ] Implémenter appels `getPartnerInfo` et `disconnectPartners`
- [ ] Ajouter gestion offline/online avec cache
- [ ] Configurer retry automatique en cas d'échec réseau
- [ ] Tester sécurité et authentification

#### 📊 **État et Performance**

- [ ] Implémenter cache local avec SharedPreferences
- [ ] Ajouter gestion états complexes (loading, error, retry)
- [ ] Configurer nettoyage mémoire et ressources
- [ ] Optimiser performances interface (lazy loading)
- [ ] Tester rotation écran et lifecycle

#### 🧪 **Tests et Validation**

- [ ] Tests unitaires ViewModel et Service
- [ ] Tests UI automatisés avec Espresso
- [ ] Tests de régression états de connexion
- [ ] Tests de performance sur appareils low-end
- [ ] Tests d'intégration bout en bout avec backend

## 📊 Diagramme de Flux Android

```mermaid
graph TD
    A[Menu Android] --> B[Click Partner Code]
    B --> C[Intent PartnerManagementActivity]
    C --> D[PartnerManagementViewModel.init]
    D --> E[checkExistingConnection()]
    E --> F[FirebaseAuth.currentUser]
    F --> G{User authenticated?}
    G -->|No| H[Show login required]
    G -->|Yes| I[Firestore.collection("users")]
    I --> J{partnerId exists?}
    J -->|No| K[_isConnected.value = false]
    J -->|Yes| L[Cloud Function getPartnerInfo]
    L --> M{Success?}
    M -->|No| N[Show error state]
    M -->|Yes| O[_isConnected.value = true]
    O --> P[_partnerInfo.value = data]
    K --> Q[Compose DisconnectedPartnerSection]
    P --> R[Compose ConnectedPartnerSection]
    R --> S[Click Disconnect Button]
    S --> T[Show AlertDialog]
    T --> U[Confirm Disconnect]
    U --> V[disconnectPartner()]
    V --> W[Cloud Function disconnectPartners]
    W --> X[Update UI State]
    X --> Y[Navigate back to menu]
```

---

**Date de génération :** September 26, 2025  
**Version CoupleApp :** iOS Current  
**Destiné pour :** Implémentation Android  
**Langues supportées :** EN, FR, ES, IT, NL, PT, PT-BR  
**Complexité :** Avancée - État conditionnel + Firebase + Sécurité
