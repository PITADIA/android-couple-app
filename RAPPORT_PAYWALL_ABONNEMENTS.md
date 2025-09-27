# Rapport : Système de Paywall et Abonnements - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille l'architecture complète du système de paywall et d'abonnements dans l'application iOS CoupleApp, incluant l'intégration StoreKit, la validation Firebase, la sécurité, et les recommandations pour l'adaptation Android.

---

## 🏗️ Architecture Générale

Le système d'abonnement utilise une architecture robuste en quatre couches :

1. **Interface Utilisateur** (`SubscriptionStepView`) - Paywall et sélection de plans
2. **Services iOS** (`AppleReceiptService`, `SubscriptionService`, `StoreKitPricingService`) - Logique métier
3. **StoreKit Framework** - Achats et transactions Apple
4. **Backend Firebase** - Validation sécurisée et persistance

```
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   Paywall UI        │    │   iOS Services      │    │   StoreKit Apple    │    │   Firebase Backend  │
│  SubscriptionStep   │◄──►│  AppleReceiptService│◄──►│   SKPaymentQueue    │◄──►│   Cloud Functions   │
│  StoreKitPricing    │    │  SubscriptionService│    │   SKProduct         │    │   Receipt Validation│
└─────────────────────┘    └─────────────────────┘    └─────────────────────┘    └─────────────────────┘
```

---

## 📱 1. Interface Utilisateur - SubscriptionStepView

### Structure du Paywall

```swift
// Localisation : Views/Onboarding/SubscriptionStepView.swift
struct SubscriptionStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @StateObject private var receiptService = AppleReceiptService.shared
    @StateObject private var pricingService = StoreKitPricingService.shared

    @State private var isProcessingPurchase = false
}
```

### Fonctionnalités de l'Interface

#### 1. **Section Présentation des Fonctionnalités**

```swift
// Mise en avant des nouvelles fonctionnalités premium
VStack(spacing: 25) {
    NewFeatureRow(
        title: "feature_love_stronger".localized,
        subtitle: "feature_love_stronger_description".localized
    )
    NewFeatureRow(
        title: "feature_memory_chest".localized,
        subtitle: "feature_memory_chest_description".localized
    )
    NewFeatureRow(
        title: "feature_love_map".localized,
        subtitle: "feature_love_map_description".localized
    )
}
```

#### 2. **Section Sélection de Plans**

```swift
// Plans affichés avec ordre stratégique (hebdomadaire en premier)
VStack(spacing: 8) {
    // Plan Hebdomadaire - Premium
    PlanSelectionCard(
        planType: .weekly,
        isSelected: receiptService.selectedPlan == .weekly,
        onTap: { receiptService.selectedPlan = .weekly }
    )

    // Plan Mensuel - Avec essai gratuit
    PlanSelectionCard(
        planType: .monthly,
        isSelected: receiptService.selectedPlan == .monthly,
        onTap: { receiptService.selectedPlan = .monthly }
    )
}
```

#### 3. **Bouton d'Achat Intelligent**

```swift
Button(action: {
    isProcessingPurchase = true
    purchaseSubscription()
}) {
    HStack {
        if isProcessingPurchase {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
            Text("loading_caps".localized)
        } else {
            Text(receiptService.selectedPlan == .weekly ?
                 "continue".localized.uppercased() :
                 "start_trial".localized)
        }
    }
    .frame(height: 56)
    .background(Color(hex: "#FD267A"))
    .cornerRadius(28)
}
.disabled(isProcessingPurchase)
```

### Gestion des États

#### États d'Interface

- **Initial** : Affichage du paywall avec prix dynamiques
- **Processing** : Animation de chargement pendant l'achat
- **Success** : Validation réussie → finalisation onboarding
- **Error** : Affichage des erreurs avec possibilité de retry

#### Navigation Intelligente

```swift
.onReceive(receiptService.$isSubscribed) { isSubscribed in
    if isSubscribed {
        // Abonnement validé → finaliser l'onboarding
        viewModel.completeSubscription()
    }
}
```

### Options de Sortie

```swift
// Croix pour continuer sans premium
Button(action: {
    viewModel.skipSubscription()
}) {
    Image(systemName: "xmark")
}

// Liens légaux obligatoires
HStack(spacing: 15) {
    Button("terms".localized) { /* Ouvrir CGU */ }
    Button("privacy_policy".localized) { /* Politique de confidentialité */ }
    Button("restore".localized) { receiptService.restorePurchases() }
}
```

---

## ⚙️ 2. Services iOS

### AppleReceiptService - Gestionnaire Principal

```swift
// Localisation : Services/AppleReceiptService.swift
class AppleReceiptService: NSObject, ObservableObject {
    static let shared = AppleReceiptService()

    @Published var isSubscribed: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var selectedPlan: SubscriptionPlanType = .monthly

    private let functions = Functions.functions()
    private let productIdentifiers = [
        "com.lyes.love2love.subscription.weekly",
        "com.lyes.love2love.subscription.monthly"
    ]
}
```

#### Processus d'Achat

```swift
func purchaseSubscription() {
    // 1. Vérifications préliminaires
    guard SKPaymentQueue.canMakePayments() else {
        errorMessage = "Achats non autorisés"
        return
    }

    // 2. Requête des produits StoreKit
    let productRequest = SKProductsRequest(productIdentifiers: Set(productIdentifiers))
    productRequest.delegate = self
    productRequest.start()

    isLoading = true
}

// 3. Traitement des produits reçus
func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
    if let product = response.products.first(where: {
        $0.productIdentifier == selectedPlan.rawValue
    }) {
        let payment = SKPayment(product: product)
        SKPaymentQueue.default().add(payment)
    }
}
```

#### Gestion des Transactions

```swift
func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
    for transaction in transactions {
        switch transaction.transactionState {
        case .purchased:
            handlePurchased(transaction)
        case .restored:
            handleRestored(transaction)
        case .failed:
            handleFailed(transaction)
        case .purchasing:
            isLoading = true // Maintenir l'état de chargement
        case .deferred:
            // Attendre l'approbation parentale
            break
        }
    }
}
```

#### Validation Firebase

```swift
private func validateReceiptWithFirebase() {
    guard let receiptURL = Bundle.main.appStoreReceiptURL,
          let receiptData = try? Data(contentsOf: receiptURL) else {
        return
    }

    let receiptString = receiptData.base64EncodedString()
    let validateReceipt = functions.httpsCallable("validateAppleReceipt")

    validateReceipt.call([
        "receiptData": receiptString,
        "productId": selectedPlan.rawValue
    ]) { result, error in
        if let data = result?.data as? [String: Any],
           let success = data["success"] as? Bool, success {
            self.isSubscribed = true
        }
    }
}
```

### SubscriptionService - Service Secondaire

```swift
// Localisation : Services/SubscriptionService.swift
class SubscriptionService: NSObject, ObservableObject {
    static let shared = SubscriptionService()

    @Published var products: [SKProduct] = []
    @Published var isSubscribed: Bool = false
    @Published var isLoading: Bool = false

    private let productIdentifiers: Set<String> = [
        "com.lyes.love2love.subscription.weekly",
        "com.lyes.love2love.subscription.monthly"
    ]
}
```

#### Fonctionnalités Additionnelles

```swift
// Nettoyage des transactions en attente
private func clearPendingTransactions() {
    let pendingTransactions = SKPaymentQueue.default().transactions

    for transaction in pendingTransactions {
        switch transaction.transactionState {
        case .purchased, .restored, .failed:
            SKPaymentQueue.default().finishTransaction(transaction)
        default:
            break
        }
    }
}

// Synchronisation avec le partenaire
private func markSubscriptionAsDirect() async {
    try await Firestore.firestore()
        .collection("users")
        .document(currentUser.uid)
        .updateData([
            "subscriptionType": "direct",
            "subscriptionPurchasedAt": Timestamp(date: Date())
        ])
}
```

### StoreKitPricingService - Gestion des Prix

```swift
// Localisation : Services/StoreKitPricingService.swift
class StoreKitPricingService: ObservableObject {
    static let shared = StoreKitPricingService()

    @Published var localizedPrices: [String: LocalizedPrice] = [:]
    @Published var isLoading: Bool = false
    @Published var lastError: Error?
}
```

#### Prix Dynamiques vs Fallback

```swift
func getLocalizedPrice(for productId: String) -> String {
    // 1. Vérifier prix dynamiques StoreKit
    if let localizedPrice = localizedPrices[productId] {
        return localizedPrice.formattedPrice
    }

    // 2. Fallback vers prix hardcodés localisés
    return getFallbackPrice(for: productId)
}

private func getFallbackPrice(for productId: String) -> String {
    switch productId {
    case "com.lyes.love2love.subscription.weekly":
        return LocalizationService.localizedCurrencySymbol(for: "plan_weekly_price".localized)
    case "com.lyes.love2love.subscription.monthly":
        return LocalizationService.localizedCurrencySymbol(for: "plan_monthly_price".localized)
    default:
        return "Prix non disponible"
    }
}
```

#### Structure Prix Localisés

```swift
struct LocalizedPrice {
    let productId: String
    let price: NSDecimalNumber
    let locale: Locale
    let currencyCode: String
    let formattedPrice: String
    let formattedPricePerUser: String // Prix / 2 pour couples

    init(product: SKProduct) {
        // Gestion intelligente des devises
        let systemLocale = Locale.current
        let systemCurrency = systemLocale.currency?.identifier ?? "USD"
        let storeKitCurrency = product.priceLocale.currency?.identifier ?? "USD"

        // Correction automatique si devises différentes
        let shouldUseSystemLocale = systemCurrency != storeKitCurrency
        let formatterLocale = shouldUseSystemLocale ? systemLocale : product.priceLocale

        let priceFormatter = NumberFormatter()
        priceFormatter.numberStyle = .currency
        priceFormatter.locale = formatterLocale

        self.formattedPrice = priceFormatter.string(from: product.price) ?? "\(product.price)"

        // Prix par utilisateur (pour couples)
        let pricePerUser = product.price.dividing(by: NSDecimalNumber(value: 2))
        self.formattedPricePerUser = priceFormatter.string(from: pricePerUser) ?? "\(pricePerUser)"
    }
}
```

---

## 🍎 3. Configuration StoreKit

### Produits d'Abonnement

```json
// Configuration : CoupleApp.storekit
{
  "subscriptionGroups": [
    {
      "id": "21482467",
      "name": "Premium",
      "subscriptions": [
        {
          "productID": "com.lyes.love2love.subscription.weekly",
          "displayPrice": "4.99",
          "recurringSubscriptionPeriod": "P1W",
          "introductoryOffer": {
            "paymentMode": "free",
            "subscriptionPeriod": "P3D" // 3 jours gratuits
          },
          "groupNumber": 1
        },
        {
          "productID": "com.lyes.love2love.subscription.monthly",
          "displayPrice": "14.99",
          "recurringSubscriptionPeriod": "P1M",
          "introductoryOffer": {
            "paymentMode": "free",
            "subscriptionPeriod": "P3D" // 3 jours gratuits
          },
          "groupNumber": 2
        }
      ]
    }
  ]
}
```

### Configuration AppDelegate

```swift
// Localisation : App/AppDelegate.swift
private func configureStoreKit() {
    // Ajout des observateurs de transactions
    SKPaymentQueue.default().add(SubscriptionService.shared)
    SKPaymentQueue.default().add(AppleReceiptService.shared)
}

private func configureRevenueCat() {
    // Configuration RevenueCat pour tracking analytics uniquement
    Purchases.logLevel = .info
    Purchases.configure(withAPIKey: "appl_pMKnixURdQHqWmKnsoicGCXeiJL")
}
```

---

## 🔥 4. Backend Firebase

### Structure Firestore

#### Collection `users` (mise à jour abonnement)

```javascript
{
  isSubscribed: true,
  subscriptionDetails: {
    subscriptionType: "com.lyes.love2love.subscription.monthly",
    purchaseDate: Timestamp,
    expiresDate: Timestamp,
    transactionId: "MASKED_FOR_SECURITY",
    originalTransactionId: "MASKED_FOR_SECURITY",
    lastValidated: Timestamp
  },
  subscriptionType: "direct", // "direct" | "inherited"
  subscriptionPurchasedAt: Timestamp
}
```

### Cloud Functions

#### 1. **Validation des Reçus Apple**

```javascript
// Localisation : firebase/functions/index.js
exports.validateAppleReceipt = functions.https.onCall(async (data, context) => {
  // 1. Rate limiting et authentification
  await checkRateLimit(context.auth.uid, "validateAppleReceipt", context);

  const { receiptData, productId } = data;

  // 2. Vérifications de sécurité
  if (!receiptData) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Données de reçu manquantes"
    );
  }

  // 3. Validation produit supporté
  const supportedProducts = [
    "com.lyes.love2love.subscription.weekly",
    "com.lyes.love2love.subscription.monthly",
  ];

  if (!supportedProducts.includes(productId)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Produit non supporté"
    );
  }

  // 4. Configuration validation Apple
  const sharedSecret = functions.config().apple?.shared_secret;
  appleReceiptVerify.config({
    secret: sharedSecret,
    environment: ["sandbox", "production"], // Support des deux
    verbose: true,
    ignoreExpired: false,
    extended: true,
  });

  // 5. Validation avec Apple
  const result = await appleReceiptVerify.validate({
    receipt: receiptData,
  });

  if (result && result.length > 0) {
    const purchase = result.find((item) => item.productId === productId);

    if (purchase) {
      // 6. Création des données d'abonnement
      const subscriptionData = {
        isSubscribed: true,
        subscriptionType: productId,
        purchaseDate: new Date(purchase.purchaseDate),
        expiresDate: purchase.expirationDate
          ? new Date(purchase.expirationDate)
          : null,
        transactionId: purchase.transactionId,
        originalTransactionId: purchase.originalTransactionId,
        lastValidated: admin.firestore.FieldValue.serverTimestamp(),
      };

      // 7. Mise à jour Firestore
      const userId = context.auth.uid;
      await admin.firestore().collection("users").doc(userId).update({
        isSubscribed: true,
        subscriptionDetails: subscriptionData,
      });

      return { success: true, subscription: subscriptionData };
    }
  }

  throw new functions.https.HttpsError("invalid-argument", "Reçu invalide");
});
```

#### 2. **Vérification Statut d'Abonnement**

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
      return { isSubscribed: false };
    }

    const userData = userDoc.data();
    const subscriptionDetails = userData.subscriptionDetails;

    // Vérifier si l'abonnement est encore valide
    let isActive = userData.isSubscribed === true;

    if (subscriptionDetails?.expiresDate) {
      const expirationDate = subscriptionDetails.expiresDate.toDate();
      isActive = isActive && expirationDate > new Date();
    }

    return {
      isSubscribed: isActive,
      subscription: subscriptionDetails,
    };
  }
);
```

#### 3. **Webhooks Apple Server-to-Server**

```javascript
exports.appleWebhook = functions.https.onRequest(async (req, res) => {
  const notification = req.body;
  const notificationType = notification.notification_type;
  const receiptData = notification.unified_receipt;

  switch (notificationType) {
    case "INITIAL_BUY":
    case "DID_RENEW":
      await handleSubscriptionActivation(receiptData);
      break;

    case "DID_FAIL_TO_RENEW":
    case "EXPIRED":
      await handleSubscriptionExpiration(receiptData);
      break;

    case "DID_CANCEL":
      await handleSubscriptionCancellation(receiptData);
      break;
  }

  res.status(200).send("OK");
});

async function handleSubscriptionExpiration(receiptData) {
  const latestReceiptInfo = receiptData.latest_receipt_info || [];

  for (const purchase of latestReceiptInfo) {
    const originalTransactionId = purchase.original_transaction_id;

    // Trouver l'utilisateur par transaction ID
    const usersSnapshot = await admin
      .firestore()
      .collection("users")
      .where(
        "subscriptionDetails.originalTransactionId",
        "==",
        originalTransactionId
      )
      .get();

    if (!usersSnapshot.empty) {
      const userDoc = usersSnapshot.docs[0];
      await userDoc.ref.update({
        isSubscribed: false,
        "subscriptionDetails.lastValidated":
          admin.firestore.FieldValue.serverTimestamp(),
      });
    }
  }
}
```

---

## 🛡️ 5. Sécurité

### Mesures de Sécurité Implémentées

#### 1. **Rate Limiting**

```javascript
// Protection contre les abus
const RATE_LIMITS = {
  validateAppleReceipt: { calls: 5, window: 1 }, // 5 appels par minute
  checkSubscriptionStatus: { calls: 10, window: 1 },
};

async function checkRateLimit(userId, functionName, context) {
  const rateLimitRef = admin
    .firestore()
    .collection("rateLimits")
    .doc(`${userId}_${functionName}`);

  const now = Date.now();
  const windowMs = RATE_LIMITS[functionName].window * 60 * 1000;

  await admin.firestore().runTransaction(async (transaction) => {
    const doc = await transaction.get(rateLimitRef);

    if (doc.exists) {
      const data = doc.data();
      if (
        now - data.windowStart < windowMs &&
        data.calls >= RATE_LIMITS[functionName].calls
      ) {
        throw new functions.https.HttpsError(
          "resource-exhausted",
          "Trop de tentatives"
        );
      }
    }
  });
}
```

#### 2. **Validation Environnement**

```javascript
// Support sandbox et production
appleReceiptVerify.config({
  environment: ["sandbox", "production"], // Essayer les deux automatiquement
  verbose: true,
  ignoreExpired: false,
});
```

#### 3. **Logs Sécurisés**

```swift
// iOS - Masquage des données sensibles
print("🔥 Transaction \(transaction.transactionIdentifier != nil ? "[ID_MASQUÉ]" : "unknown")")
```

```javascript
// Firebase - Logs sans exposition d'IDs
logger.info("🔥 validateAppleReceipt: Achat trouvé", {
  productId: purchase.productId,
  hasTransactionId: !!purchase.transactionId,
  hasOriginalTransactionId: !!purchase.originalTransactionId,
});
```

#### 4. **Gestion d'Erreurs Spécialisée**

```javascript
// Erreurs spécifiques selon le contexte
if (error.message?.includes("21007")) {
  throw new functions.https.HttpsError(
    "failed-precondition",
    "Environnement Apple incorrect - vérifiez sandbox/production"
  );
} else if (error.message?.includes("receipt")) {
  throw new functions.https.HttpsError(
    "invalid-argument",
    "Reçu Apple invalide ou corrompu"
  );
}
```

#### 5. **Authentification Requise**

```javascript
// Validation côté serveur obligatoire
if (!context.auth) {
  throw new functions.https.HttpsError(
    "unauthenticated",
    "Utilisateur non authentifié - authentification requise avant paiement"
  );
}
```

### Configuration App Store Connect

```javascript
// Configuration sécurisée des API Apple
const APP_STORE_CONNECT_CONFIG = {
  keyId: functions.config().apple?.key_id,
  issuerId: functions.config().apple?.issuer_id,
  bundleId: "com.lyes.love2love",
  privateKey: functions.config().apple?.private_key,
  environment: "production",
};
```

---

## 📊 6. Analytics et Monitoring

### Événements Trackés

```swift
// iOS - Événements Firebase Analytics
Analytics.logEvent("abonnement_reussi", parameters: [
    "type": planType,
    "source": "storekit_success"
])

Analytics.logEvent("achat_restaure", parameters: [:])
```

### RevenueCat Integration

```swift
// Configuration RevenueCat pour tracking uniquement
Purchases.configure(withAPIKey: "appl_pMKnixURdQHqWmKnsoicGCXeiJL")

// Les transactions StoreKit sont automatiquement trackées
// Sans interférer avec la logique d'achat native
```

---

## 🔄 7. Flux de Traitement Complet

### Workflow d'Achat Standard

```
1. Utilisateur sur SubscriptionStepView
   ├── Affichage prix dynamiques StoreKitPricingService
   ├── Sélection plan (weekly/monthly)
   └── Clic "Commencer l'essai"

2. AppleReceiptService.purchaseSubscription()
   ├── Vérification SKPaymentQueue.canMakePayments()
   ├── Requête produits StoreKit
   ├── Création SKPayment
   └── Ajout à la queue → Sheet Apple apparaît

3. Gestion Transaction StoreKit
   ├── .purchasing → Maintien loading UI
   ├── .purchased → Validation Firebase
   ├── .failed → Affichage erreur
   └── .restored → Gestion restauration

4. Validation Firebase (validateAppleReceipt)
   ├── Rate limiting + authentification
   ├── Validation Apple Servers
   ├── Mise à jour Firestore
   └── Retour succès/erreur

5. Post-Achat
   ├── isSubscribed = true
   ├── Finalisation onboarding
   ├── Synchronisation partenaire
   └── Analytics tracking
```

### Workflow de Restauration

```
1. Clic "Restaurer"
   └── SKPaymentQueue.default().restoreCompletedTransactions()

2. Gestion Restauration
   ├── paymentQueue:updatedTransactions (restored)
   ├── paymentQueueRestoreCompletedTransactionsFinished
   └── Validation immédiate sans Firebase (onboarding)

3. Post-Restauration
   ├── isSubscribed = true
   ├── Navigation onboarding
   └── Validation complète post-onboarding
```

---

## 📱 8. Adaptation pour Android

### Architecture Équivalente Android

#### 1. **Interface Utilisateur - Jetpack Compose**

```kotlin
@Composable
fun SubscriptionStepScreen(
    viewModel: OnboardingViewModel,
    billingService: BillingService = hiltViewModel()
) {
    var isProcessingPurchase by remember { mutableStateOf(false) }
    var selectedPlan by remember { mutableStateOf(SubscriptionPlan.MONTHLY) }

    val billingState by billingService.billingState.collectAsState()
    val productDetails by billingService.productDetails.collectAsState()

    LaunchedEffect(Unit) {
        billingService.initializeBilling()
        billingService.queryProductDetails()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
            .padding(16.dp)
    ) {
        // Header avec croix
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { viewModel.skipSubscription() }) {
                Icon(Icons.Default.Close, contentDescription = "Fermer")
            }
        }

        // Titre
        Text(
            text = stringResource(R.string.choose_plan),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Fonctionnalités premium
        FeaturesList()

        Spacer(modifier = Modifier.weight(1f))

        // Sélection des plans
        PlanSelectionSection(
            selectedPlan = selectedPlan,
            onPlanSelected = { selectedPlan = it },
            productDetails = productDetails
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Bouton d'achat
        PurchaseButton(
            isLoading = isProcessingPurchase,
            selectedPlan = selectedPlan,
            onClick = {
                isProcessingPurchase = true
                billingService.launchBillingFlow(selectedPlan)
            }
        )

        // Liens légaux
        LegalLinksSection()
    }

    // Gestion des états de paiement
    LaunchedEffect(billingState.purchaseState) {
        when (billingState.purchaseState) {
            PurchaseState.PURCHASED -> {
                isProcessingPurchase = false
                viewModel.completeSubscription()
            }
            PurchaseState.FAILED -> {
                isProcessingPurchase = false
                // Afficher erreur
            }
            PurchaseState.CANCELED -> {
                isProcessingPurchase = false
            }
            else -> { /* États intermédiaires */ }
        }
    }
}

@Composable
fun PlanSelectionCard(
    plan: SubscriptionPlan,
    isSelected: Boolean,
    productDetails: ProductDetails?,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFD267A).copy(alpha = 0.1f)
                           else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFFD267A) else Color.Gray.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFFD267A)
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (plan) {
                        SubscriptionPlan.WEEKLY -> stringResource(R.string.plan_weekly_title)
                        SubscriptionPlan.MONTHLY -> stringResource(R.string.plan_monthly_title)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Text(
                    text = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "Prix en cours de chargement...",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            if (plan == SubscriptionPlan.MONTHLY) {
                Badge(
                    text = stringResource(R.string.free_trial),
                    backgroundColor = Color(0xFFD267A)
                )
            }
        }
    }
}
```

#### 2. **Service de Facturation Android**

```kotlin
@Singleton
class BillingService @Inject constructor(
    private val application: Application,
    private val firebaseFunctions: FirebaseFunctions
) {

    private val _billingState = MutableStateFlow(BillingState())
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private var billingClient: BillingClient? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        handlePurchasesUpdated(billingResult, purchases)
    }

    data class BillingState(
        val isConnected: Boolean = false,
        val purchaseState: PurchaseState = PurchaseState.IDLE,
        val errorMessage: String? = null
    )

    enum class PurchaseState {
        IDLE, PENDING, PURCHASED, FAILED, CANCELED
    }

    private val productIds = listOf(
        "com.lyes.love2love.subscription.weekly",
        "com.lyes.love2love.subscription.monthly"
    )

    suspend fun initializeBilling() = withContext(Dispatchers.IO) {
        billingClient = BillingClient.newBuilder(application)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingState.update { it.copy(isConnected = true) }
                    CoroutineScope(Dispatchers.IO).launch {
                        queryProductDetails()
                        queryExistingPurchases()
                    }
                } else {
                    _billingState.update {
                        it.copy(
                            isConnected = false,
                            errorMessage = "Erreur de connexion au Play Store: ${billingResult.debugMessage}"
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingState.update { it.copy(isConnected = false) }
            }
        })
    }

    private suspend fun queryProductDetails() = withContext(Dispatchers.IO) {
        val productList = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val detailsMap = productDetailsList.associateBy { it.productId }
                _productDetails.update { detailsMap }
            } else {
                Log.e("BillingService", "Erreur query produits: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchBillingFlow(plan: SubscriptionPlan, activity: Activity) {
        val productId = when (plan) {
            SubscriptionPlan.WEEKLY -> "com.lyes.love2love.subscription.weekly"
            SubscriptionPlan.MONTHLY -> "com.lyes.love2love.subscription.monthly"
        }

        val productDetails = _productDetails.value[productId]
        if (productDetails == null) {
            _billingState.update {
                it.copy(
                    purchaseState = PurchaseState.FAILED,
                    errorMessage = "Produit non disponible"
                )
            }
            return
        }

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            _billingState.update {
                it.copy(
                    purchaseState = PurchaseState.FAILED,
                    errorMessage = "Offre d'abonnement non disponible"
                )
            }
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        _billingState.update { it.copy(purchaseState = PurchaseState.PENDING) }

        val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)
        if (billingResult?.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingState.update {
                it.copy(
                    purchaseState = PurchaseState.FAILED,
                    errorMessage = "Erreur lancement achat: ${billingResult?.debugMessage}"
                )
            }
        }
    }

    private fun handlePurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingState.update { it.copy(purchaseState = PurchaseState.CANCELED) }
            }
            else -> {
                _billingState.update {
                    it.copy(
                        purchaseState = PurchaseState.FAILED,
                        errorMessage = "Erreur d'achat: ${billingResult.debugMessage}"
                    )
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Vérifier et consommer l'achat
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        // Valider avec Firebase avant d'acquitter
                        val validationResult = validatePurchaseWithFirebase(purchase)

                        if (validationResult) {
                            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()

                            billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                    _billingState.update { it.copy(purchaseState = PurchaseState.PURCHASED) }
                                } else {
                                    Log.e("BillingService", "Erreur acknowledgement: ${billingResult.debugMessage}")
                                }
                            }
                        } else {
                            _billingState.update {
                                it.copy(
                                    purchaseState = PurchaseState.FAILED,
                                    errorMessage = "Échec de la validation"
                                )
                            }
                        }
                    } else {
                        _billingState.update { it.copy(purchaseState = PurchaseState.PURCHASED) }
                    }
                }
            } catch (e: Exception) {
                Log.e("BillingService", "Erreur traitement achat", e)
                _billingState.update {
                    it.copy(
                        purchaseState = PurchaseState.FAILED,
                        errorMessage = "Erreur: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun validatePurchaseWithFirebase(purchase: Purchase): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = firebaseFunctions
                .getHttpsCallable("validateGooglePlayPurchase")
                .call(hashMapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to purchase.products.firstOrNull(),
                    "orderId" to purchase.orderId
                ))
                .await()

            val data = result.data as? Map<String, Any>
            return@withContext data?.get("success") as? Boolean ?: false

        } catch (e: Exception) {
            Log.e("BillingService", "Erreur validation Firebase", e)
            return@withContext false
        }
    }

    private suspend fun queryExistingPurchases() = withContext(Dispatchers.IO) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activePurchases = purchases.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }

                if (activePurchases.isNotEmpty()) {
                    _billingState.update { it.copy(purchaseState = PurchaseState.PURCHASED) }
                }
            }
        }
    }

    fun restorePurchases() {
        CoroutineScope(Dispatchers.IO).launch {
            queryExistingPurchases()
        }
    }

    fun disconnect() {
        billingClient?.endConnection()
    }
}
```

#### 3. **Cloud Functions Android (Google Play)**

```kotlin
// Côté Firebase Functions - Validation Google Play
exports.validateGooglePlayPurchase = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Non authentifié');
    }

    const { purchaseToken, productId, orderId } = data;

    try {
        // Configuration Google Play Developer API
        const auth = new GoogleAuth({
            scopes: ['https://www.googleapis.com/auth/androidpublisher']
        });

        const androidpublisher = google.androidpublisher({ version: 'v3', auth });

        // Vérifier l'achat avec Google Play
        const result = await androidpublisher.purchases.subscriptions.get({
            packageName: 'com.lyes.love2love',
            subscriptionId: productId,
            token: purchaseToken
        });

        const purchaseData = result.data;

        if (purchaseData.paymentState === 1 && // Payé
            (purchaseData.cancelReason === undefined || purchaseData.cancelReason === 0)) { // Pas annulé

            // Mise à jour Firestore
            const userId = context.auth.uid;
            await admin.firestore().collection('users').doc(userId).update({
                isSubscribed: true,
                subscriptionDetails: {
                    subscriptionType: productId,
                    purchaseToken: purchaseToken,
                    orderId: orderId,
                    purchaseTime: new Date(parseInt(purchaseData.startTimeMillis)),
                    expiryTime: new Date(parseInt(purchaseData.expiryTimeMillis)),
                    autoRenewing: purchaseData.autoRenewing === true,
                    lastValidated: admin.firestore.FieldValue.serverTimestamp()
                }
            });

            return { success: true };
        } else {
            throw new functions.https.HttpsError('invalid-argument', 'Achat invalide');
        }

    } catch (error) {
        console.error('Erreur validation Google Play:', error);
        throw new functions.https.HttpsError('internal', 'Erreur de validation');
    }
});
```

#### 4. **Configuration Android - Manifest**

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="com.android.vending.BILLING" />

<application>
    <!-- ... -->
</application>
```

#### 5. **Dependencies Android**

```gradle
// build.gradle (Module: app)
dependencies {
    // Google Play Billing
    implementation 'com.android.billingclient:billing:6.1.0'
    implementation 'com.android.billingclient:billing-ktx:6.1.0'

    // Firebase
    implementation 'com.google.firebase:firebase-functions-ktx:20.4.0'
    implementation 'com.google.firebase:firebase-analytics-ktx:21.5.0'

    // Architecture Components
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
    implementation 'androidx.compose.runtime:runtime-livedata:1.5.6'

    // Dependency Injection
    implementation 'com.google.dagger:hilt-android:2.48'
    kapt 'com.google.dagger:hilt-compiler:2.48'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### Défis Spécifiques Android

#### 1. **Google Play Billing vs App Store**

| Aspect           | iOS StoreKit                   | Android Google Play Billing |
| ---------------- | ------------------------------ | --------------------------- |
| **API**          | SKPaymentQueue                 | BillingClient               |
| **Produits**     | SKProduct                      | ProductDetails              |
| **Transactions** | SKPaymentTransaction           | Purchase                    |
| **États**        | .purchased, .failed, .restored | PurchaseState.PURCHASED     |
| **Validation**   | Apple Receipt Validation       | Google Play Developer API   |

#### 2. **Gestion des États Android**

```kotlin
// Android nécessite une gestion d'états plus complexe
sealed class BillingUiState {
    object Loading : BillingUiState()
    object Connected : BillingUiState()
    data class ProductsLoaded(val products: List<ProductDetails>) : BillingUiState()
    object PurchaseInProgress : BillingUiState()
    object PurchaseCompleted : BillingUiState()
    data class Error(val message: String) : BillingUiState()
}
```

#### 3. **Fragmentation Android**

```kotlin
// Gestion des différentes versions Android
class BillingCompatHelper {
    fun createBillingClient(context: Context): BillingClient {
        val builder = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)

        // Android 5.0+ requis pour les abonnements
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.enablePendingPurchases()
        }

        return builder.build()
    }
}
```

#### 4. **Testing Strategy Android**

```kotlin
// Tests pour Google Play Billing
@RunWith(AndroidJUnit4::class)
class BillingServiceTest {

    @Mock
    private lateinit var billingClient: BillingClient

    @Mock
    private lateinit var firebaseFunctions: FirebaseFunctions

    @Test
    fun `should handle successful purchase`() = runTest {
        // Given
        val purchase = createMockPurchase()
        val billingResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()

        // When
        billingService.handlePurchasesUpdated(billingResult, listOf(purchase))

        // Then
        assertEquals(PurchaseState.PURCHASED, billingService.billingState.value.purchaseState)
    }
}
```

---

## 🎯 Conclusion

### Points Forts du Système iOS

1. **Architecture Robuste** : Séparation claire entre UI, services, et backend
2. **Sécurité Avancée** : Validation serveur, rate limiting, logs sécurisés
3. **UX Optimisée** : Prix dynamiques, états de chargement, gestion d'erreurs
4. **Intégration Complète** : StoreKit natif + Firebase + RevenueCat analytics
5. **Monitoring** : Webhooks Apple, validation temps réel, analytics détaillées

### Complexité d'Adaptation Android

**Niveau : Moyen/Élevé** - Le système nécessite :

- Maîtrise Google Play Billing API
- Architecture MVVM avec Compose
- Gestion d'états complexes (BillingClient)
- Validation Google Play Developer API
- Testing strategy pour fragmentation Android

### Estimation de Développement Android

1. **Phase 1** : Setup Google Play Billing + UI de base (2 semaines)
2. **Phase 2** : Interface Compose + sélection plans (1 semaine)
3. **Phase 3** : Intégration Firebase validation (1 semaine)
4. **Phase 4** : Gestion erreurs + restauration (1 semaine)
5. **Phase 5** : Testing + optimisations multi-devices (1-2 semaines)

**Total estimé : 6-8 semaines** pour une implémentation Android complète équivalente au système iOS.

### Recommandations d'Implémentation

#### 1. **Priorités de Développement**

1. **Core Billing** : BillingClient + validation de base
2. **Interface Utilisateur** : Paywall Compose responsive
3. **Intégration Backend** : Cloud Functions Google Play
4. **Sécurité** : Rate limiting + validation serveur
5. **Monitoring** : Analytics + error tracking

#### 2. **Risques et Considérations**

- **Fragmentation** : Tests sur multiples versions Android
- **Google Play Policies** : Respect strict des règles de facturation
- **Testing** : Environnement test Google Play Console complexe
- **Performance** : Optimisation pour appareils bas de gamme
- **Maintenance** : Synchronisation évolutions iOS futures

Le système de paywall iOS est très sophistiqué mais parfaitement adaptable à Android avec la même robustesse et sécurité !
