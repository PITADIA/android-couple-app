# 🎬 PAGE DE PRÉSENTATION - Premier Écran & Logique

## 🎯 Vue d'Ensemble

Page d'accueil qui s'affiche au premier lancement de l'app ou après suppression du compte, avec le slogan "L'application qui vous rapproche" et introduction à l'app.

---

## 📱 Architecture des Écrans

### 1. Écran de Chargement Initial (`LaunchScreenView`)

```swift
struct LaunchScreenView: View {
    var body: some View {
        ZStack {
            // Fond dégradé rose
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(hex: "#FD267A"),  // Rose principal
                    Color(hex: "#FF655B")   // Orange-rouge
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            // Logo centré
            Image("leetchi2")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 200, height: 200)
        }
    }
}
```

### 2. Page de Présentation (`AuthenticationView`)

```swift
struct AuthenticationView: View {
    var body: some View {
        ZStack {
            // Fond gris clair identique onboarding
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // 1. Header avec logo + nom app
                VStack(spacing: 20) {
                    HStack(spacing: 15) {
                        Image("Leetchi")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 40, height: 40)

                        Text("app_name".localized)
                            .font(.system(size: 50, weight: .bold))
                            .foregroundColor(.black)
                    }
                    .padding(.top, 60)
                }

                Spacer()

                // 2. Contenu principal centré
                VStack(spacing: 20) {
                    Text("app_tagline".localized)
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .padding(.horizontal, 30)

                    Text("app_description".localized)
                        .font(.system(size: 18))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .padding(.horizontal, 30)
                }

                Spacer()

                // 3. Zone boutons (fixe en bas)
                VStack(spacing: 15) {
                    Button(action: { startOnboarding() }) {
                        Text("start_free".localized)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(Color(hex: "#FD267A"))
                            .cornerRadius(28)
                    }
                    .padding(.horizontal, 30)

                    Button(action: { performAppleSignIn() }) {
                        Text("already_have_account".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.6))
                            .underline()
                    }
                }
                .padding(.vertical, 20)
                .background(Color.white)
            }
        }
    }
}
```

---

## 🎨 Design Détaillé

### 1. LaunchScreenView (Écran Chargement)

**Fond :**

- **Type:** LinearGradient diagonal
- **Couleur 1:** #FD267A (rose principal)
- **Couleur 2:** #FF655B (orange-rouge)
- **Direction:** Top-leading → Bottom-trailing

**Logo :**

- **Image:** "leetchi2"
- **Taille:** 200x200pt
- **Position:** Centré
- **Mode:** AspectRatio fit

### 2. AuthenticationView (Page Présentation)

**Fond :**

- **Couleur:** RGB(0.97, 0.97, 0.98) → #F7F7F8
- **Style:** Uni, ignore safe area

**Section Header (Haut) :**

```swift
// Padding top: 60pt
HStack(spacing: 15) {
    Image("Leetchi")           // Logo 40x40pt
    Text("Love2Love")          // 50pt, bold, noir
}
// Spacing VStack: 20pt
```

**Section Contenu Principal (Centre) :**

```swift
VStack(spacing: 20) {
    Text("L'application qui vous rapproche")  // 36pt, bold, noir
    Text("Description longue...")             // 18pt, regular, noir 70%
}
// Padding horizontal: 30pt
// Multiline + center alignment
```

**Section Boutons (Bas) :**

```swift
VStack(spacing: 15) {
    // Bouton principal
    Button {
        Text("Commencer Gratuitement")
            .font(.system(size: 18, weight: .semibold))
            .foregroundColor(.white)
            .frame(height: 56)
            .background(Color(hex: "#FD267A"))
            .cornerRadius(28)
    }

    // Bouton secondaire
    Button {
        Text("J'ai déjà un compte")
            .font(.system(size: 16))
            .foregroundColor(.black.opacity(0.6))
            .underline()
    }
}
.padding(.vertical, 20)
.background(Color.white)
// Padding horizontal bouton principal: 30pt
```

---

## 🖼️ Images Utilisées

### Logo Principal

- **Nom:** "Leetchi"
- **Utilisation:** Header page présentation
- **Taille:** 40x40pt
- **Format:** PNG (probable)
- **Style:** Logo petit pour interface

### Logo Splash

- **Nom:** "leetchi2"
- **Utilisation:** Écran de chargement
- **Taille:** 200x200pt
- **Format:** PNG (probable)
- **Style:** Logo grand pour splash screen

### Assets Structure

```
Assets.xcassets/
├── Leetchi.imageset/
│   ├── Contents.json
│   └── Leetchi.png
└── leetchi2.imageset/
    ├── Contents.json
    └── leetchi2.png
```

---

## 🔑 Clés XCStrings Utilisées

### Textes Principaux

```xml
<!-- Nom de l'application -->
<string name="app_name">Love2Love</string>

<!-- Slogan principal -->
<string name="app_tagline">L'application qui vous rapproche</string>

<!-- Description longue -->
<string name="app_description">Redécouvrez-vous à travers des questions qui raviveront votre amour et sauvegardez les moments passés ensemble.</string>
```

### Boutons d'Action

```xml
<!-- Bouton principal -->
<string name="start_free">Commencer Gratuitement</string>

<!-- Bouton secondaire -->
<string name="already_have_account">J'ai déjà un compte</string>
```

### Traductions Complètes

#### `app_tagline` (Slogan)

- 🇫🇷 **FR:** "L'application qui vous rapproche"
- 🇬🇧 **EN:** "The app that brings you closer"
- 🇩🇪 **DE:** "Die App, die euch näher zusammenbringt"
- 🇪🇸 **ES:** "La app que os acerca"

#### `app_description` (Description)

- 🇫🇷 **FR:** "Redécouvrez-vous à travers des questions qui raviveront votre amour et sauvegardez les moments passés ensemble."
- 🇬🇧 **EN:** "Deepen your bond through fun and intimate questions."
- 🇩🇪 **DE:** "Vertieft eure Bindung mit spaßigen und intimen Fragen."
- 🇪🇸 **ES:** "Profundiza vuestro vínculo a través de preguntas divertidas e íntimas."

#### `start_free` (Bouton Principal)

- 🇫🇷 **FR:** "Commencer Gratuitement"
- 🇬🇧 **EN:** "Try For Free"
- 🇩🇪 **DE:** "Kostenlos ausprobieren"
- 🇪🇸 **ES:** "Probar gratis"

#### `already_have_account` (Bouton Secondaire)

- 🇫🇷 **FR:** "J'ai déjà un compte"
- 🇬🇧 **EN:** "I already have an account"
- 🇩🇪 **DE:** "Ich habe schon einen Account"
- 🇪🇸 **ES:** "Ya tengo una cuenta"

---

## ⚙️ Logique d'Affichage (`ContentView`)

### Conditions d'Affichage

```swift
struct ContentView: View {
    var body: some View {
        ZStack {
            Group {
                if appState.isLoading {
                    // 🚀 ÉTAPE 1: Chargement initial
                    LaunchScreenView()

                } else if !appState.isAuthenticated {
                    // 📱 ÉTAPE 2: Utilisateur jamais connecté
                    AuthenticationView()

                } else if appState.isAuthenticated &&
                          !appState.hasUserStartedOnboarding &&
                          !appState.isOnboardingCompleted {
                    // 📱 ÉTAPE 2bis: Authentifié auto mais pas d'onboarding manuel
                    AuthenticationView()

                } else if appState.isAuthenticated &&
                          (appState.hasUserStartedOnboarding || appState.forceOnboarding) &&
                          !appState.isOnboardingCompleted {
                    // 🔄 ÉTAPE 3: Processus onboarding
                    OnboardingView()

                } else {
                    // ✅ ÉTAPE 4: Application principale
                    TabContainerView()
                }
            }
        }
    }
}
```

### États AppState Impliqués

```swift
// Dans AppState
@Published var isLoading: Bool = true                    // Chargement Firebase
@Published var isAuthenticated: Bool = false             // Connexion Firebase Auth
@Published var hasUserStartedOnboarding: Bool = false    // Onboarding déclenché manuellement
@Published var isOnboardingCompleted: Bool = false       // Onboarding terminé
@Published var forceOnboarding: Bool = false            // Forcer onboarding après erreur
```

### Scénarios d'Affichage

#### 🆕 Premier Lancement

1. **LaunchScreenView** (isLoading = true)
2. **AuthenticationView** (isAuthenticated = false)
3. Utilisateur clique "Commencer Gratuitement"
4. **OnboardingView** (hasUserStartedOnboarding = true)

#### 🔄 Suppression de Compte

1. **LaunchScreenView** (isLoading = true)
2. **AuthenticationView** (compte supprimé → isAuthenticated = false)
3. Recommence le processus comme nouveau utilisateur

#### 🔐 "J'ai déjà un compte"

1. **AuthenticationView**
2. Apple Sign In → Firebase Auth
3. Si utilisateur existant → **TabContainerView** directement
4. Si nouveau → **OnboardingView**

#### ⚡ Reconnexion Automatique

1. **LaunchScreenView** (isLoading = true)
2. Firebase reconnecte automatiquement
3. Si onboarding terminé → **TabContainerView**
4. Sinon → **AuthenticationView** (pour démarrage manuel)

---

## 🤖 Adaptation Android (Kotlin/Jetpack Compose)

### 1. Architecture Principale

```kotlin
@Composable
fun MainActivity(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    when {
        appState.isLoading -> {
            SplashScreen()
        }

        !appState.isAuthenticated ||
        (appState.isAuthenticated && !appState.hasUserStartedOnboarding && !appState.isOnboardingCompleted) -> {
            WelcomeScreen(
                onStartOnboarding = { appState.startUserOnboarding() },
                onSignInWithGoogle = { appState.signInWithGoogle() }
            )
        }

        appState.isAuthenticated &&
        (appState.hasUserStartedOnboarding || appState.forceOnboarding) &&
        !appState.isOnboardingCompleted -> {
            OnboardingScreen()
        }

        else -> {
            MainTabScreen()
        }
    }
}
```

### 2. Écran Splash Android

```kotlin
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFD267A), // Rose principal
                        Color(0xFFFF655B)  // Orange-rouge
                    ),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.leetchi2),
            contentDescription = null,
            modifier = Modifier.size(200.dp),
            contentScale = ContentScale.Fit
        )
    }
}
```

### 3. Écran Welcome Android

```kotlin
@Composable
fun WelcomeScreen(
    onStartOnboarding: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
    ) {
        // Header avec logo + nom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.leetchi),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = stringResource(R.string.app_name),
                    style = TextStyle(
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Contenu principal centré
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.app_tagline),
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            )

            Text(
                text = stringResource(R.string.app_description),
                style = TextStyle(
                    fontSize = 18.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Zone boutons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Button(
                onClick = onStartOnboarding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 30.dp)
            ) {
                Text(
                    text = stringResource(R.string.start_free),
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }

            TextButton(
                onClick = onSignInWithGoogle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.already_have_account),
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.6f),
                        textDecoration = TextDecoration.Underline
                    )
                )
            }
        }
    }
}
```

### 4. AppState Android

```kotlin
class AppStateRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val userPreferences: UserPreferences
) {
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated = _isAuthenticated.asStateFlow()

    private val _hasUserStartedOnboarding = MutableStateFlow(false)
    val hasUserStartedOnboarding = _hasUserStartedOnboarding.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted = _isOnboardingCompleted.asStateFlow()

    private val _forceOnboarding = MutableStateFlow(false)
    val forceOnboarding = _forceOnboarding.asStateFlow()

    init {
        checkAuthenticationState()
    }

    private fun checkAuthenticationState() {
        firebaseAuth.addAuthStateListener { auth ->
            val user = auth.currentUser
            _isAuthenticated.value = user != null

            if (user != null) {
                loadUserData(user.uid)
            } else {
                _isLoading.value = false
            }
        }
    }

    fun startUserOnboarding() {
        _hasUserStartedOnboarding.value = true
    }

    fun completeOnboarding() {
        _isOnboardingCompleted.value = true
        _hasUserStartedOnboarding.value = false
    }

    suspend fun signInWithGoogle() {
        // Implémentation Google Sign In
    }
}
```

### 5. Ressources Android

```xml
<!-- strings.xml -->
<string name="app_name">Love2Love</string>
<string name="app_tagline">L\'application qui vous rapproche</string>
<string name="app_description">Redécouvrez-vous à travers des questions qui raviveront votre amour et sauvegardez les moments passés ensemble.</string>
<string name="start_free">Commencer Gratuitement</string>
<string name="already_have_account">J\'ai déjà un compte</string>

<!-- drawable resources -->
res/drawable/
├── leetchi.png          (40x40dp logo)
├── leetchi2.png         (200x200dp splash logo)
```

### 6. Gestion États avec Hilt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppStateModule {

    @Provides
    @Singleton
    fun provideAppStateRepository(
        firebaseAuth: FirebaseAuth,
        userPreferences: UserPreferences
    ): AppStateRepository {
        return AppStateRepository(firebaseAuth, userPreferences)
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appStateRepository: AppStateRepository
) : ViewModel() {

    val appState = combine(
        appStateRepository.isLoading,
        appStateRepository.isAuthenticated,
        appStateRepository.hasUserStartedOnboarding,
        appStateRepository.isOnboardingCompleted,
        appStateRepository.forceOnboarding
    ) { isLoading, isAuthenticated, hasStarted, isCompleted, forceOnboarding ->
        AppUiState(
            isLoading = isLoading,
            isAuthenticated = isAuthenticated,
            hasUserStartedOnboarding = hasStarted,
            isOnboardingCompleted = isCompleted,
            forceOnboarding = forceOnboarding
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppUiState()
    )
}

data class AppUiState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val hasUserStartedOnboarding: Boolean = false,
    val isOnboardingCompleted: Boolean = false,
    val forceOnboarding: Boolean = false
)
```

---

## 📋 Résumé Technique

### ✅ Architecture iOS

- **LaunchScreenView** : Splash avec logo sur dégradé rose
- **AuthenticationView** : Page présentation avec slogan + 2 boutons
- **ContentView** : Logique navigation basée sur 5 états AppState
- **Images** : "Leetchi" (40x40) + "leetchi2" (200x200)

### ✅ Clés de Traduction

- **5 clés principales** : app_name, app_tagline, app_description, start_free, already_have_account
- **Multilingue** : FR, EN, DE, ES avec traductions complètes
- **Contexte** : Slogan accrocheur + description engageante

### ✅ Logique d'Affichage

- **Premier lancement** : Splash → Présentation → Onboarding
- **Suppression compte** : Reset états → Présentation (comme nouveau)
- **Reconnexion** : Auto-auth → App principale ou Présentation si onboarding incomplet

### ✅ Android Équivalent

- **Jetpack Compose** moderne avec StateFlow
- **Material Design 3** : Brush gradient, RoundedCornerShape
- **Hilt DI** pour gestion états centralisée
- **Google Sign In** au lieu d'Apple
- **strings.xml** avec échappement caractères

Cette page de présentation constitue la **première impression** cruciale avec un design soigné et un message clair ! 🎯✨
