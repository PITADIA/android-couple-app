# Rapport : Page Principale de CoupleApp iOS - Architecture Complète

## Vue d'ensemble

Ce rapport détaille l'architecture complète de la page principale de l'application iOS CoupleApp, incluant tous les composants, fonctionnalités, intégrations Firebase, gestion d'état, et les recommandations pour l'adaptation Android.

---

## 🏗️ Architecture Générale

L'application utilise une architecture MVVM sophistiquée avec gestion d'état centralisée :

```
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   ContentView       │    │   TabContainerView  │    │   HomeContentView   │
│  (Point d'entrée)   │───►│  (Navigation tabs)  │───►│  (Page principale)  │
│  - Auth routing     │    │  - 6 onglets        │    │  - Composants UI    │
│  - Onboarding flow  │    │  - Menu fixe        │    │  - Interactions     │
└─────────────────────┘    └─────────────────────┘    └─────────────────────┘
            │                          │                          │
            ▼                          ▼                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            AppState (Gestion d'état globale)                │
│  • currentUser, isAuthenticated, onboarding                                 │
│  • 12+ services spécialisés (FirebaseService, LocationService, etc.)       │
└─────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Firebase Backend + Cloud Functions                 │
│  • Firestore : Données utilisateurs, partenaires, journal, favoris         │
│  • Storage : Images de profil, chiffrement URLs                             │
│  • Functions : 15+ fonctions (connexion partenaire, synchro, sécurité)     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📱 1. Navigation et Structure d'Écrans

### ContentView.swift - Routeur Principal

```swift
// Logique de routage selon l'état utilisateur
if appState.isLoading {
    LaunchScreenView()
} else if !appState.isAuthenticated {
    AuthenticationView()  // Apple Sign-In uniquement
} else if appState.isAuthenticated && !appState.isOnboardingCompleted {
    OnboardingView()      // Processus d'onboarding 7 étapes
} else {
    TabContainerView()    // Application principale
}
```

### TabContainerView.swift - Navigation Principale

**6 Onglets disponibles :**

1. **Accueil** (`HomeContentView`) - Page principale
2. **Questions du jour** (`DailyQuestionFlowView`) - Questions quotidiennes
3. **Défis du jour** (`DailyChallengeFlowView`) - Défis quotidiens
4. **Favoris** (`FavoritesView`) - Questions sauvegardées
5. **Journal** (`JournalPageView`) - Journal de couple géolocalisé
6. **Menu/Profil** (`MenuContentView`) - Paramètres et profil

**Analytics intégré :**

```swift
// Tracking Firebase Analytics pour chaque navigation
Analytics.logEvent("onglet_visite", parameters: ["onglet": "accueil"])
```

---

## 🏠 2. Page Principale - HomeContentView

### Structure de la Page

```swift
ScrollView {
    VStack(spacing: 30) {
        // 1. Section photos de profil + distance partenaire
        PartnerDistanceView(...)

        // 2. Invitation partenaire (si non connecté)
        if !hasConnectedPartner {
            PartnerInviteView(...)
        }

        // 3. Collections de catégories de questions
        VStack(spacing: 20) {
            ForEach(QuestionCategory.categories) { category in
                CategoryListCardView(category: category)
            }
        }

        // 4. Section Widgets
        WidgetPreviewSection(...)

        // 5. Statistiques du couple
        CoupleStatisticsView(...)
    }
}
```

### Design et UX

- **Fond dégradé rose** : `Color(hex: "#FD267A")` avec opacité décroissante
- **Fond gris clair** : `Color(red: 0.97, green: 0.97, blue: 0.98)`
- **Padding intelligent** : Adaptation automatique selon l'état du clavier
- **Scrolling fluide** : `ScrollView` avec gestion des safe areas

---

## 👥 3. Section Photos de Profil et Distance - PartnerDistanceView

### Fonctionnalités Clés

#### 3.1 Affichage Photos de Profil

**Architecture Cache Multi-Niveaux :**

```swift
// 1. Cache UserCacheManager (priorité maximale)
if let cachedImage = UserCacheManager.shared.getCachedProfileImage() {
    Image(uiImage: cachedImage)
}
// 2. Cache AsyncImageView + ImageCacheService
else if let imageURL = imageURL {
    AsyncImageView(imageURL: imageURL)
}
// 3. Initiales utilisateur avec fond coloré
else if !userName.isEmpty {
    UserInitialsView(name: userName, size: 80)
}
// 4. Fallback icône grise
else {
    Circle().fill(Color.gray.opacity(0.3))
    Image(systemName: "person.fill")
}
```

#### 3.2 Calcul Distance en Temps Réel

**Logique de Calcul :**

```swift
private var partnerDistance: String {
    // Cache ultra-rapide (2 secondes)
    let now = Date()
    if now.timeIntervalSince(lastCalculationTime) < 2 && cachedDistance != "km ?" {
        return cachedDistance
    }

    // Synchronisation LocationService ↔ currentUser
    if let locationServiceLocation = appState.locationService?.currentLocation,
       currentUser.currentLocation != locationServiceLocation {
        var updatedUser = currentUser
        updatedUser.currentLocation = locationServiceLocation
        appState.currentUser = updatedUser
    }

    // Calcul distance avec formatage intelligent
    let distance = currentLocation.distance(to: partnerLocation)

    if distance < 1 {
        return "widget_together_text".localized.capitalized  // "Ensemble"
    } else if distance < 10 {
        return String(format: "%.1f km", distance)
    } else {
        return "\(Int(distance)) km"
    }
}
```

**Cache Persistant :**

- Sauvegarde dans `UserDefaults` (24h)
- Chargement instantané au démarrage
- Mise à jour toutes les 5 secondes via Timer

#### 3.3 Interface Utilisateur Distance

**Design Courbe Sophistiqué :**

```swift
struct CurvedDashedLine: Shape {
    func path(in rect: CGRect) -> Path {
        // Ligne courbe quadratique avec contrôle dynamique
        let curveHeight = min(screenWidth * 0.03, 15)
        let controlPoint = CGPoint(x: rect.width / 2, y: rect.height / 2 - curveHeight)

        path.move(to: startPoint)
        path.addQuadCurve(to: endPoint, control: controlPoint)
    }
}
```

**Interaction Intelligente :**

- **Cliquable** si localisation manquante → Ouvre flow de permissions
- **Non-cliquable** si distance calculée avec succès
- **Messages contextuels** selon l'état de connexion partenaire

---

## 📚 4. Collections de Questions - CategoryListCardView

### 4.1 Système de Catégories

**8 Catégories Disponibles :**

| ID                    | Nom                 | Emoji | Status      | Description               |
| --------------------- | ------------------- | ----- | ----------- | ------------------------- |
| `en-couple`           | En Couple           | 💞    | **Gratuit** | Questions sur la relation |
| `les-plus-hots`       | Désirs Inavoués     | 🌶️    | **Premium** | Questions intimes         |
| `a-distance`          | À Distance          | ✈️    | **Premium** | Relation longue distance  |
| `questions-profondes` | Questions Profondes | ✨    | **Premium** | Réflexions profondes      |
| `pour-rire-a-deux`    | Pour Rire           | 😂    | **Premium** | Questions amusantes       |
| `tu-preferes`         | Tu Préfères         | 🤍    | **Premium** | Dilemmes et choix         |
| `mieux-ensemble`      | Mieux Ensemble      | 💌    | **Premium** | Améliorer la relation     |
| `pour-un-date`        | Pour un Date        | 🍸    | **Premium** | Questions de rendez-vous  |

### 4.2 Système Freemium

**FreemiumManager - Gestion des Accès :**

```swift
func handleCategoryTap(_ category: QuestionCategory, onSuccess: @escaping () -> Void) {
    if category.isPremium && !(appState.currentUser?.isSubscribed ?? false) {
        // Afficher paywall pour catégories premium
        showingSubscription = true
        print("🔒 Catégorie premium bloquée: \(category.title)")
    } else {
        // Accès autorisé
        onSuccess()
    }
}
```

### 4.3 Intégration Firebase

**Questions stockées localement :**

- Fichiers JSON par catégorie : `EnCouple.xcstrings`, `LesPlus Hots.xcstrings`, etc.
- Chargement via `QuestionDataManager.shared.loadQuestions(for: categoryId)`
- Support multilingue intégré

---

## 📊 5. Statistiques du Couple - CoupleStatisticsView

### 5.1 Métriques Calculées

**4 Statistiques Affichées :**

#### Jours Ensemble

```swift
private var daysTogetherCount: Int {
    guard let relationshipStartDate = appState.currentUser?.relationshipStartDate else {
        return 0
    }
    let dayComponents = Calendar.current.dateComponents([.day],
        from: relationshipStartDate, to: Date())
    return max(dayComponents.day ?? 0, 0)
}
```

#### Progression Questions

```swift
private var questionsProgressPercentage: Double {
    var totalQuestions = 0
    var totalProgress = 0

    for category in categories {
        let questions = getQuestionsForCategory(category.id)
        let currentIndex = categoryProgressService.getCurrentIndex(for: category.id)

        totalQuestions += questions.count
        totalProgress += min(currentIndex + 1, questions.count)
    }

    return (Double(totalProgress) / Double(totalQuestions)) * 100.0
}
```

#### Villes Visitées

```swift
private var citiesVisitedCount: Int {
    let uniqueCities = Set(journalService.entries.compactMap { entry in
        entry.location?.city?.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty })
    return uniqueCities.count
}
```

#### Pays Visités

```swift
private var countriesVisitedCount: Int {
    let uniqueCountries = Set(journalService.entries.compactMap { entry in
        entry.location?.country?.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty })
    return uniqueCountries.count
}
```

### 5.2 Design des Cartes

**Layout 2x2 Grid :**

```swift
LazyVGrid(columns: [
    GridItem(.flexible(), spacing: 16),
    GridItem(.flexible(), spacing: 16)
], spacing: 16) {
    // 4 cartes statistiques avec couleurs thématiques
}
```

**Couleurs Thématiques :**

- **Jours ensemble** : Rose (`#feb5c8`, `#fedce3`, `#db3556`)
- **Questions** : Orange (`#fed397`, `#fde9cf`, `#ffa229`)
- **Villes** : Bleu (`#b0d6fe`, `#dbecfd`, `#0a85ff`)
- **Pays** : Violet (`#d1b3ff`, `#e8dcff`, `#7c3aed`)

---

## 🔧 6. Section Widgets - WidgetPreviewSection

### 6.1 Fonctionnalité

**Widget Preview :**

```swift
struct WidgetPreviewSection: View {
    let onWidgetTap: () -> Void

    var body: some View {
        Button(action: onWidgetTap) {
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("add_widgets".localized)
                    Text("feel_closer_partner".localized)
                }
                Spacer()
                Image(systemName: "chevron.right")
            }
        }
    }
}
```

### 6.2 Types de Widgets Disponibles

**3 Widgets iOS :**

1. **Jours Ensemble** (Gratuit) - Widget petit/moyen/grand
2. **Distance Partenaire** (Premium) - Widget écran verrouillé
3. **Widget Complet** (Premium) - Combinaison données

**WidgetService - Synchronisation Temps Réel :**

```swift
class WidgetService: ObservableObject {
    @Published var currentUser: AppUser?
    @Published var partnerUser: AppUser?
    @Published var distanceInfo: DistanceInfo?

    func refreshData() {
        // Mise à jour données widget
        // Sauvegarde pour extension widget
        saveWidgetData()
    }
}
```

---

## 🔥 7. Intégration Firebase - Architecture Backend

### 7.1 Services Firebase Utilisés

**Firestore Collections :**

- `users` - Données utilisateurs principales
- `partnerCodes` - Codes de connexion temporaires
- `journalEntries` - Entrées de journal géolocalisées
- `favorites` - Questions favorites par utilisateur
- `sharedPartnerData` - Données partagées pour widgets

**Storage :**

- `profile_images/{uid}/profile.jpg` - Photos de profil chiffrées
- Génération URLs signées via Cloud Functions

**15+ Cloud Functions :**

```javascript
// Exemples de fonctions principales
exports.generatePartnerCode = functions.https.onCall(async (data, context) => {
  // Génération codes sécurisés 8 chiffres
});

exports.connectToPartner = functions.https.onCall(async (data, context) => {
  // Connexion partenaires avec héritage abonnements
});

exports.getSignedImageURL = functions.https.onCall(async (data, context) => {
  // URLs signées sécurisées pour photos
});

exports.syncPartnerSubscriptions = functions.https.onCall(
  async (data, context) => {
    // Synchronisation abonnements entre partenaires
  }
);
```

### 7.2 Sécurité Firebase

**Authentication :**

- Apple Sign-In uniquement (`"apple.com"` provider)
- Vérification stricte des tokens
- Pas d'authentification alternative

**Security Rules Firestore :**

```javascript
// Exemple règle sécurité utilisateurs
match /users/{userId} {
    allow read, write: if request.auth != null && request.auth.uid == userId;
    // Accès partenaire via Cloud Functions uniquement
}

match /partnerCodes/{codeId} {
    allow read: if request.auth != null;
    allow write: if request.auth != null && request.auth.uid == resource.data.userId;
}
```

### 7.3 Synchronisation Temps Réel

**Listeners Firestore Actifs :**

```swift
// AppState - Écoute changements utilisateur
firebaseService.$currentUser
    .receive(on: DispatchQueue.main)
    .sink { [weak self] user in
        // Mise à jour état global
        self?.currentUser = user
        self?.widgetService?.refreshData()
    }

// PartnerLocationService - Écoute localisation partenaire
partnerListener = db.collection("sharedPartnerData").document(partnerId)
    .addSnapshotListener { [weak self] snapshot, error in
        // Mise à jour localisation temps réel
    }
```

---

## 📊 8. Gestion d'État - AppState

### 8.1 Architecture de l'AppState

```swift
class AppState: ObservableObject {
    // État principal
    @Published var isAuthenticated: Bool = false
    @Published var currentUser: AppUser?
    @Published var isOnboardingCompleted: Bool = false

    // 12+ Services spécialisés
    @Published var freemiumManager: FreemiumManager?
    @Published var favoritesService: FavoritesService?
    @Published var categoryProgressService: CategoryProgressService?
    @Published var partnerLocationService: PartnerLocationService?
    @Published var journalService: JournalService?
    @Published var widgetService: WidgetService?
    @Published var locationService: LocationService?
    // ... 6+ autres services
}
```

### 8.2 Cache Intelligent Multi-Niveaux

**UserCacheManager - Cache Principal :**

```swift
class UserCacheManager {
    static let shared = UserCacheManager()

    // Cache utilisateur complet
    func cacheUser(_ user: AppUser)
    func getCachedUser() -> AppUser?

    // Cache images de profil
    func cacheProfileImage(_ image: UIImage)
    func getCachedProfileImage() -> UIImage?

    // Cache image partenaire
    func cachePartnerImage(_ image: UIImage, url: String)
    func getCachedPartnerImage() -> UIImage?
}
```

**ImageCacheService - Cache Images URL :**

```swift
class ImageCacheService {
    static let shared = ImageCacheService()

    func cacheImage(_ image: UIImage, for url: String)
    func getCachedImage(for url: String) -> UIImage?
    func clearCachedImage(for url: String)
}
```

### 8.3 Synchronisation Services

**Exemple - Synchronisation Localisation :**

```swift
// LocationService → AppState → PartnerDistanceView
.onChange(of: appState.locationService?.currentLocation) { oldValue, newValue in
    if let newLocation = newValue, var currentUser = appState.currentUser {
        currentUser.currentLocation = newLocation
        appState.currentUser = currentUser
    }
    forceUpdateDistance()
}
```

---

## 🔗 9. Pages Liées et Navigation

### 9.1 Sheets et Navigation

**SheetType Enum - Types de Navigation :**

```swift
enum SheetType: Identifiable {
    case questions(QuestionCategory)      // Liste questions catégorie
    case subscription                     // Paywall abonnement
    case widgets                         // Configuration widgets
    case partnerManagement              // Gestion partenaire
    case locationPermission             // Permissions localisation
    case partnerLocationMessage         // Message localisation partenaire
    case eventsMap                      // Carte journal
    case dailyQuestionPermission        // Permissions questions quotidiennes
}
```

### 9.2 Pages Accessibles depuis la Principale

**Navigation Directe :**

- `QuestionListView` - Via tap sur catégorie
- `SubscriptionView` - Via FreemiumManager ou cadenas
- `WidgetsView` - Via section widgets
- `PartnerManagementView` - Via photo partenaire
- `LocationPermissionFlow` - Via distance "km ?"

**Navigation Onglets :**

- `DailyQuestionFlowView` - Questions quotidiennes
- `DailyChallengeFlowView` - Défis quotidiens
- `FavoritesView` - Questions favorites
- `JournalPageView` - Journal géolocalisé
- `MenuContentView` - Profil et paramètres

---

## 🤖 10. Adaptation Android - Recommandations Techniques

### 10.1 Architecture Android Equivalente

**Structure Recommandée :**

```kotlin
// Architecture MVVM avec Jetpack Compose
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoupleAppTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val appState = rememberAppState()

    when {
        appState.isLoading -> LaunchScreen()
        !appState.isAuthenticated -> AuthenticationScreen()
        !appState.isOnboardingCompleted -> OnboardingFlow()
        else -> MainTabScreen()
    }
}
```

### 10.2 Gestion d'État - Android

**AppState avec StateFlow :**

```kotlin
class AppState {
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser

    // Services équivalents
    val firebaseService = FirebaseService()
    val locationService = LocationService()
    val widgetService = WidgetService()
    // ... autres services
}

@Composable
fun rememberAppState(): AppState {
    return remember { AppState() }
}
```

### 10.3 Navigation Android - Jetpack Compose

**Navigation avec BottomNavigation :**

```kotlin
@Composable
fun MainTabScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigation {
                BottomNavigationItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Accueil") },
                    label = { Text("Accueil") },
                    selected = currentRoute == "home",
                    onClick = { navController.navigate("home") }
                )
                // ... 5 autres onglets
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") { HomeScreen() }
            composable("daily_questions") { DailyQuestionsScreen() }
            composable("daily_challenges") { DailyChallengesScreen() }
            composable("favorites") { FavoritesScreen() }
            composable("journal") { JournalScreen() }
            composable("profile") { ProfileScreen() }
        }
    }
}
```

### 10.4 Page Principale Android - HomeScreen

**Structure Jetpack Compose :**

```kotlin
@Composable
fun HomeScreen(
    appState: AppState = LocalAppState.current
) {
    val currentUser by appState.currentUser.collectAsState()
    val scrollState = rememberScrollState()

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFfd267a).copy(alpha = 0.3f),
                        Color(0xFFfd267a).copy(alpha = 0.1f),
                        Color.Transparent
                    )
                )
            )
    ) {
        item {
            // Section photos + distance partenaire
            PartnerDistanceSection()
        }

        item {
            // Invitation partenaire si non connecté
            if (currentUser?.partnerId.isNullOrEmpty()) {
                PartnerInviteSection()
            }
        }

        items(QuestionCategory.categories) { category ->
            CategoryCard(
                category = category,
                onTap = { /* Navigation vers questions */ }
            )
        }

        item {
            // Section widgets
            WidgetPreviewCard()
        }

        item {
            // Statistiques couple
            CoupleStatisticsGrid()
        }
    }
}
```

### 10.5 Composants Android Équivalents

#### PartnerDistanceSection

```kotlin
@Composable
fun PartnerDistanceSection() {
    val partnerLocationService = LocalPartnerLocationService.current
    val distance by partnerLocationService.distance.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Photo profil utilisateur
        UserProfileImage(size = 80.dp)

        // Ligne distance courbe
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            CurvedDashedLine()

            DistanceChip(
                distance = distance,
                onClick = { /* Gestion permissions localisation */ }
            )
        }

        // Photo profil partenaire
        PartnerProfileImage(size = 80.dp)
    }
}
```

#### CategoryCard

```kotlin
@Composable
fun CategoryCard(
    category: QuestionCategory,
    onTap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clickable { onTap() },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    if (category.isPremium && !isSubscribed) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = "Premium",
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = category.emoji,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
```

### 10.6 Services Android - Équivalences

#### Firebase Service Android

```kotlin
class FirebaseService {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    suspend fun signInWithGoogle(): Result<AppUser> {
        // Google Sign-In pour Android
        // (équivalent Apple Sign-In iOS)
        return withContext(Dispatchers.IO) {
            try {
                // Authentification Google
                val result = googleSignInClient.silentSignIn().await()
                val credential = GoogleAuthProvider.getCredential(result.idToken, null)
                val authResult = auth.signInWithCredential(credential).await()

                // Charger ou créer utilisateur
                val user = loadOrCreateUser(authResult.user!!.uid)
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateUserLocation(location: UserLocation): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("No user"))

                firestore.collection("users").document(userId)
                    .update(
                        mapOf(
                            "currentLocation" to mapOf(
                                "latitude" to location.latitude,
                                "longitude" to location.longitude,
                                "city" to location.city,
                                "country" to location.country,
                                "lastUpdated" to FieldValue.serverTimestamp()
                            ),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()

                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

#### Location Service Android

```kotlin
class LocationService(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val geocoder = Geocoder(context, Locale.getDefault())

    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation

    @SuppressLint("MissingPermission")
    suspend fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 30_000 // 30 secondes
            fastestInterval = 15_000
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    updateLocation(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private suspend fun updateLocation(location: Location) {
        val addresses = withContext(Dispatchers.IO) {
            try {
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
            } catch (e: Exception) {
                null
            }
        }

        val userLocation = UserLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            city = addresses?.firstOrNull()?.locality,
            country = addresses?.firstOrNull()?.countryName,
            lastUpdated = Date()
        )

        _currentLocation.value = userLocation
    }
}
```

### 10.7 Cache Android - Room Database

```kotlin
@Entity(tableName = "cached_users")
data class CachedUser(
    @PrimaryKey val id: String,
    val name: String,
    val isAuthenticated: Boolean,
    val profileImagePath: String?,
    val partnerImagePath: String?,
    val lastCached: Long
)

@Dao
interface UserCacheDao {
    @Query("SELECT * FROM cached_users WHERE id = :userId")
    suspend fun getCachedUser(userId: String): CachedUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheUser(user: CachedUser)

    @Query("DELETE FROM cached_users WHERE id = :userId")
    suspend fun clearUser(userId: String)
}

@Database(
    entities = [CachedUser::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userCacheDao(): UserCacheDao
}

class UserCacheManager(private val dao: UserCacheDao, private val context: Context) {
    suspend fun cacheUser(user: AppUser) {
        val cachedUser = CachedUser(
            id = user.id,
            name = user.name,
            isAuthenticated = true,
            profileImagePath = user.profileImageURL,
            partnerImagePath = null, // À implémenter
            lastCached = System.currentTimeMillis()
        )
        dao.cacheUser(cachedUser)
    }

    suspend fun getCachedUser(userId: String): AppUser? {
        val cached = dao.getCachedUser(userId) ?: return null

        // Vérifier l'âge du cache (24h)
        if (System.currentTimeMillis() - cached.lastCached > 24 * 60 * 60 * 1000) {
            dao.clearUser(userId)
            return null
        }

        return AppUser(
            id = cached.id,
            name = cached.name,
            profileImageURL = cached.profileImagePath
            // ... autres propriétés
        )
    }
}
```

### 10.8 Widgets Android - App Widgets

```kotlin
class CoupleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_couple_days)

        // Récupérer données depuis SharedPreferences ou Room
        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        val daysCount = prefs.getInt("days_together", 0)
        val userName = prefs.getString("user_name", "")
        val partnerName = prefs.getString("partner_name", "")

        views.setTextViewText(R.id.days_count, daysCount.toString())
        views.setTextViewText(R.id.user_name, userName)
        views.setTextViewText(R.id.partner_name, partnerName)

        // Intent pour ouvrir l'app
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
```

### 10.9 Pages Android à Réutiliser/Adapter

**Pages iOS → Android Mapping :**

| Page iOS               | Équivalent Android       | Complexité  | Notes                                    |
| ---------------------- | ------------------------ | ----------- | ---------------------------------------- |
| `HomeContentView`      | `HomeScreen`             | **Moyenne** | Adaptation UI Compose, logique similaire |
| `PartnerDistanceView`  | `PartnerDistanceSection` | **Haute**   | Calculs géographiques, cache complexe    |
| `CategoryListCardView` | `CategoryCard`           | **Faible**  | Simple adaptation UI                     |
| `CoupleStatisticsView` | `CoupleStatisticsGrid`   | **Moyenne** | Calculs dates, intégration Room          |
| `WidgetPreviewSection` | `WidgetPreviewCard`      | **Faible**  | UI simple                                |
| `TabContainerView`     | `MainTabScreen`          | **Moyenne** | Navigation Compose                       |
| `AppState`             | `AppState` + ViewModels  | **Haute**   | Architecture MVVM Android                |
| Services iOS           | Services Android         | **Haute**   | Adaptation Firebase, location, cache     |

### 10.10 Recommandations Finales Android

**Priorités de Développement :**

1. **Phase 1 - Core** (4-6 semaines)

   - Authentication Google
   - Firebase integration
   - Navigation structure
   - Cache system (Room)

2. **Phase 2 - UI Principal** (3-4 semaines)

   - HomeScreen adaptation
   - Partner distance calculation
   - Categories display
   - Basic statistics

3. **Phase 3 - Fonctionnalités Avancées** (4-5 semaines)

   - Widget Android
   - Location services
   - Real-time sync
   - Image caching

4. **Phase 4 - Polish** (2-3 semaines)
   - Performance optimization
   - UI animations
   - Error handling
   - Testing

**Technologies Recommandées :**

- **UI** : Jetpack Compose
- **Architecture** : MVVM + Repository pattern
- **Navigation** : Navigation Compose
- **État** : StateFlow + ViewModel
- **Cache** : Room Database
- **Images** : Coil
- **Location** : Fused Location Provider
- **Firebase** : SDK Android standard

---

## 📋 Conclusion

La page principale de CoupleApp iOS présente une architecture sophistiquée avec :

- **Interface utilisateur** riche et interactive (photos, distance, statistiques)
- **Architecture MVVM** avec gestion d'état centralisée
- **Cache multi-niveaux** pour performances optimales
- **Intégration Firebase** complète avec 15+ Cloud Functions
- **Synchronisation temps réel** entre partenaires
- **Système freemium** intégré avec 8 catégories de questions

L'adaptation Android est **techniquement faisable** avec Jetpack Compose et les équivalents Android des services iOS. La complexité principale réside dans la réplication de l'architecture de cache et la synchronisation temps réel, mais la logique métier peut être largement réutilisée.

**Estimation totale Android** : 13-18 semaines de développement pour une équivalence fonctionnelle complète.
