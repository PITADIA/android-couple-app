# 💎 PAYWALL - Collection Premium Bloquée

## 🔍 Détection d'Accès Premium

### Vérification Abonnement (`FreemiumManager.swift`)

```swift
func handleCategoryTap(_ category: QuestionCategory, onSuccess: @escaping () -> Void) {
    print("🔥 FreemiumManager: Tap sur catégorie: \(category.title)")

    // 1️⃣ Vérifier si l'utilisateur est abonné (direct OU hérité)
    let isSubscribed = appState?.currentUser?.isSubscribed ?? false

    // 2️⃣ Si abonné → Accès illimité
    if isSubscribed {
        print("🔥 UTILISATEUR ABONNE - ACCES ILLIMITE")
        onSuccess()
        return
    }

    // 3️⃣ Si catégorie premium + non abonné → PAYWALL
    if category.isPremium {
        print("🔥 CATEGORIE PREMIUM - ACCES BLOQUE")

        blockedCategoryAttempt = category  // Stocker catégorie bloquée
        showingSubscription = true         // Déclencher modal paywall

        // 📊 Analytics
        Analytics.logEvent("paywall_affiche", parameters: [
            "source": "freemium_limite"
        ])

        // 🔔 Notification pour UI
        NotificationCenter.default.post(name: .freemiumManagerChanged, object: nil)

        return
    }

    // 4️⃣ Catégorie gratuite → Accès autorisé (limité au niveau questions)
    onSuccess()
}
```

### Conditions de Blocage

```swift
// Dans AppUser model
struct AppUser {
    var isSubscribed: Bool              // ✅ Direct OU hérité
    var subscriptionInheritedFrom: String?  // ID du partenaire premium
    var subscriptionInheritedAt: Date?      // Date d'héritage
}

// Dans QuestionCategory model
struct QuestionCategory {
    var isPremium: Bool  // 🔒 Collection payante
}
```

---

## 🚀 Trigger sur Collection Card

### CategoryListCardView.swift

```swift
Button(action: {
    print("🔥 CategoryListCardView: Tap détecté sur \(category.title)")

    // Utiliser FreemiumManager pour gérer le tap
    if let freemiumManager = appState.freemiumManager {
        freemiumManager.handleCategoryTap(category) {
            action()  // ✅ Accès autorisé → Navigation
        }
    } else {
        action()  // Fallback sans FreemiumManager
    }
}) {
    // UI de la carte collection...
}
```

### Flow de Détection

1. **Utilisateur clique** sur collection premium (ex: "Les Plus Hots")
2. **CategoryListCardView** appelle `freemiumManager.handleCategoryTap()`
3. **FreemiumManager** vérifie `isSubscribed`
4. **Si non abonné** → `showingSubscription = true`
5. **Notification** `.freemiumManagerChanged` envoyée
6. **TabContainerView** détecte et affiche modal

---

## 🔔 Affichage Modal Paywall

### TabContainerView.swift - Observer

```swift
.onReceive(NotificationCenter.default.publisher(for: .freemiumManagerChanged)) { _ in
    if let freemiumManager = appState.freemiumManager {
        if freemiumManager.showingSubscription && activeSheet != .subscription {
            print("🔥 TabContainer: AFFICHAGE SUBSCRIPTION DEMANDE")
            activeSheet = .subscription  // 🎯 DÉCLENCHE MODAL
        }
    }
}
```

### Sheet Modal

```swift
.sheet(item: $activeSheet) { sheetType in
    switch sheetType {
    case .subscription:
        SubscriptionView()
            .environmentObject(appState)
            .onAppear {
                print("🔥 SubscriptionView apparue dans la sheet")
            }
            .onDisappear {
                print("🔥 SubscriptionView disparue de la sheet")
                appState.freemiumManager?.dismissSubscription()
            }
    // ... autres cases
    }
}
```

---

## 🔥 Interface Paywall (`SubscriptionView.swift`)

### Structure UI

```swift
struct SubscriptionView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var receiptService = AppleReceiptService.shared
    @StateObject private var pricingService = StoreKitPricingService.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            // Fond gris clair
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Header avec croix de fermeture
                HStack {
                    Button(action: {
                        appState.freemiumManager?.dismissSubscription()
                        dismiss()
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.black)
                    }
                    Spacer()
                }
                .padding(.top, 10)

                // Titre principal
                VStack(spacing: 8) {
                    Text("subscription_title".localized)  // "Débloque toutes les fonctionnalités"
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)

                    Text("subscription_subtitle".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)
                }

                // Boutons d'abonnement avec prix dynamiques
                VStack(spacing: 12) {
                    ForEach(SubscriptionPlanType.allCases, id: \.rawValue) { plan in
                        Button(action: {
                            purchasePlan(plan)
                        }) {
                            // UI bouton prix...
                        }
                    }
                }
            }
        }
    }
}
```

---

## 🔐 Vérification Firebase Abonnement

### Structure Document Utilisateur

```json
// Collection: users/[userId]
{
  "isSubscribed": false,                    // ❌ Pas abonné
  "subscriptionType": null,                 // Pas de type
  "subscriptionInheritedFrom": null,        // Pas d'héritage partenaire
  "subscriptionInheritedAt": null,
  "partnerId": "partner123",                // ID partenaire connecté
  "subscriptionDetails": {
    "expiresDate": null,
    "platform": "ios",
    "productId": null
  }
}

// SI PARTENAIRE PREMIUM:
{
  "isSubscribed": true,                     // ✅ Abonné via héritage
  "subscriptionType": "shared_from_partner",
  "subscriptionInheritedFrom": "partner123",
  "subscriptionInheritedAt": "2024-01-15T10:30:00Z",
  "partnerId": "partner123"
}
```

### Cloud Function: `checkSubscriptionStatus`

```javascript
exports.checkSubscriptionStatus = functions.https.onCall(
  async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const userId = context.auth.uid;
    const userDoc = await admin
      .firestore()
      .collection("users")
      .doc(userId)
      .get();

    if (!userDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Utilisateur non trouvé"
      );
    }

    const userData = userDoc.data();
    const subscriptionDetails = userData.subscriptionDetails || {};

    // Vérifier si l'abonnement est encore valide
    const now = new Date();
    const expiresDate = subscriptionDetails.expiresDate?.toDate();

    const isActive =
      userData.isSubscribed && (!expiresDate || expiresDate > now);

    return {
      isSubscribed: isActive,
      subscription: subscriptionDetails,
    };
  }
);
```

### Listener Temps Réel (`FirebaseService.swift`)

```swift
func startListeningForSubscriptionChanges() {
    guard let user = Auth.auth().currentUser else { return }

    subscriptionListener = db.collection("users").document(user.uid)
        .addSnapshotListener { [weak self] snapshot, error in
            guard let data = snapshot?.data() else { return }

            let isSubscribed = data["isSubscribed"] as? Bool ?? false
            let subscriptionType = data["subscriptionType"] as? String

            // Mettre à jour l'état local
            if let currentUser = self?.currentUser, currentUser.isSubscribed != isSubscribed {
                var updatedUser = currentUser
                updatedUser.isSubscribed = isSubscribed

                // Champs d'héritage partenaire
                if subscriptionType == "shared_from_partner" {
                    updatedUser.subscriptionInheritedFrom = data["subscriptionSharedFrom"] as? String
                    updatedUser.subscriptionInheritedAt = (data["subscriptionSharedAt"] as? Timestamp)?.dateValue()
                }

                DispatchQueue.main.async {
                    self?.currentUser = updatedUser

                    // Notifier changement abonnement
                    NotificationCenter.default.post(name: .subscriptionUpdated, object: nil)
                }
            }
        }
}
```

---

## 📊 Analytics & Suivi

### Événements Trackés

```swift
// FreemiumManager.swift
Analytics.logEvent("paywall_affiche", parameters: [
    "source": "freemium_limite",
    "category_id": category.id,
    "category_title": category.title
])

Analytics.logEvent("category_blocked", parameters: [
    "category_id": category.id,
    "category_title": category.title,
    "user_subscribed": false
])

// SubscriptionView.swift
Analytics.logEvent("subscription_started", parameters: [
    "plan": plan.rawValue,
    "source": "premium_category_block"
])
```

---

## 🤖 Adaptation Android (Kotlin)

### 1. FreemiumManager Android

```kotlin
class FreemiumManager(
    private val context: Context,
    private val appState: AppState
) : ViewModel() {

    private val _showingSubscription = MutableStateFlow(false)
    val showingSubscription = _showingSubscription.asStateFlow()

    private val _blockedCategoryAttempt = MutableStateFlow<QuestionCategory?>(null)
    val blockedCategoryAttempt = _blockedCategoryAttempt.asStateFlow()

    fun handleCategoryTap(
        category: QuestionCategory,
        onSuccess: () -> Unit
    ) {
        Log.d(TAG, "🔥 FreemiumManager: Tap sur catégorie: ${category.title}")

        // 1️⃣ Vérifier abonnement (utilisateur + héritage partenaire)
        val isSubscribed = appState.currentUser.value?.isSubscribed ?: false

        // 2️⃣ Si abonné → Accès illimité
        if (isSubscribed) {
            Log.d(TAG, "🔥 UTILISATEUR ABONNE - ACCES ILLIMITE")
            onSuccess()
            return
        }

        // 3️⃣ Si catégorie premium → PAYWALL
        if (category.isPremium) {
            Log.d(TAG, "🔥 CATEGORIE PREMIUM - ACCES BLOQUE")

            _blockedCategoryAttempt.value = category
            _showingSubscription.value = true

            // 📊 Analytics
            Firebase.analytics.logEvent("paywall_affiche") {
                param("source", "freemium_limite")
                param("category_id", category.id)
                param("category_title", category.title)
            }

            return
        }

        // 4️⃣ Catégorie gratuite → Accès autorisé
        onSuccess()
    }

    fun dismissSubscription() {
        _showingSubscription.value = false
        _blockedCategoryAttempt.value = null
    }
}
```

### 2. CategoryCard Compose

```kotlin
@Composable
fun CategoryCard(
    category: QuestionCategory,
    onClick: () -> Unit,
    freemiumManager: FreemiumManager
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                Log.d(TAG, "🔥 CategoryCard: Tap détecté sur ${category.title}")

                freemiumManager.handleCategoryTap(category) {
                    onClick()  // ✅ Accès autorisé → Navigation
                }
            }
    ) {
        // UI de la carte...
    }
}
```

### 3. MainActivity - Observer Paywall

```kotlin
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val freemiumManager = hiltViewModel<FreemiumManager>()
            val showingSubscription by freemiumManager.showingSubscription.collectAsState()

            // UI principale
            MainScreen()

            // MODAL PAYWALL
            if (showingSubscription) {
                Dialog(
                    onDismissRequest = {
                        freemiumManager.dismissSubscription()
                    }
                ) {
                    SubscriptionScreen(
                        onDismiss = {
                            freemiumManager.dismissSubscription()
                        }
                    )
                }
            }
        }
    }
}
```

### 4. SubscriptionScreen Compose

```kotlin
@Composable
fun SubscriptionScreen(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))  // Fond gris clair
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header avec croix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Color.Black
                    )
                }
            }

            // Titre
            Text(
                text = stringResource(R.string.subscription_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
            )

            // Sous-titre
            Text(
                text = stringResource(R.string.subscription_subtitle),
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Boutons d'abonnement
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(SubscriptionPlan.values()) { plan ->
                    SubscriptionButton(
                        plan = plan,
                        onClick = {
                            purchasePlan(plan)
                        }
                    )
                }
            }
        }
    }
}
```

### 5. Firebase Intégration Android

```kotlin
class FirebaseService {

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    private var subscriptionListener: ListenerRegistration? = null

    fun startListeningForSubscriptionChanges() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        subscriptionListener = FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener abonnement: ${error.message}")
                    return@addSnapshotListener
                }

                val data = snapshot?.data ?: return@addSnapshotListener

                val isSubscribed = data["isSubscribed"] as? Boolean ?: false
                val subscriptionType = data["subscriptionType"] as? String

                // Mettre à jour l'état local
                val currentUserData = _currentUser.value
                if (currentUserData != null && currentUserData.isSubscribed != isSubscribed) {
                    val updatedUser = currentUserData.copy(
                        isSubscribed = isSubscribed,
                        subscriptionInheritedFrom = if (subscriptionType == "shared_from_partner") {
                            data["subscriptionSharedFrom"] as? String
                        } else null,
                        subscriptionInheritedAt = if (subscriptionType == "shared_from_partner") {
                            (data["subscriptionSharedAt"] as? Timestamp)?.toDate()
                        } else null
                    )

                    _currentUser.value = updatedUser

                    Log.d(TAG, "🔥 Abonnement mis à jour localement: $isSubscribed")
                }
            }
    }

    suspend fun checkSubscriptionStatus(): SubscriptionStatus {
        return try {
            val result = Firebase.functions
                .getHttpsCallable("checkSubscriptionStatus")
                .call()
                .await()

            val data = result.data as HashMap<String, Any>
            SubscriptionStatus(
                isSubscribed = data["isSubscribed"] as? Boolean ?: false,
                subscription = data["subscription"] as? Map<String, Any>
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification statut: ${e.message}")
            SubscriptionStatus(false, null)
        }
    }
}
```

---

## 🎯 Points Clés

### iOS Logic Flow

1. **Tap Collection** → `CategoryListCardView`
2. **FreemiumManager** vérifie `isSubscribed`
3. **Si bloqué** → `showingSubscription = true`
4. **NotificationCenter** → `.freemiumManagerChanged`
5. **TabContainerView** → `activeSheet = .subscription`
6. **SubscriptionView** modal affiché

### Android Logic Flow

1. **Tap Collection** → `CategoryCard`
2. **FreemiumManager** vérifie `isSubscribed`
3. **Si bloqué** → `_showingSubscription.value = true`
4. **StateFlow** observer trigger
5. **MainActivity** → Dialog avec `SubscriptionScreen`

### Vérification Abonnement

- **Direct :** `isSubscribed = true` via achat
- **Hérité :** `isSubscribed = true` + `subscriptionInheritedFrom != null`
- **Firebase** listeners temps réel pour sync
- **Cloud Functions** pour validation sécurisée

---

## 🚀 Résumé

✅ **Détection automatique** collections premium  
✅ **Vérification abonnement** (utilisateur + partenaire)  
✅ **Modal paywall** responsive  
✅ **Analytics** complet  
✅ **Firebase sync** temps réel  
✅ **Android adaptation** complète avec Compose + StateFlow
