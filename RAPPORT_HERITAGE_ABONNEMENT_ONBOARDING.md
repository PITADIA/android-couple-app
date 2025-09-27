# 🎯 HÉRITAGE D'ABONNEMENT - Saut d'Onboarding

## 🔄 Logique iOS Actuelle

### Détection du Partenaire Premium (`PartnerCodeStepView.swift`)

```swift
// OBSERVER 1: Notification d'héritage
.onReceive(NotificationCenter.default.publisher(for: .subscriptionInherited)) { _ in
    print("🔗 PartnerCodeStepView: Abonnement hérité détecté")
    if partnerCodeService.partnerInfo?.isSubscribed == true {
        print("🔥🔥🔥 INHERITANCE: PARTENAIRE PREMIUM DETECTE - SKIP VERS FINALISATION DIRECTE")
        viewModel.skipSubscriptionDueToInheritance()  // 1️⃣ Marquer skip
        viewModel.finalizeOnboarding(withSubscription: true)  // 2️⃣ Finaliser direct
    } else {
        viewModel.nextStep()  // ➡️ Continuer normalement
    }
}

// OBSERVER 2: Connexion partenaire établie
.onChange(of: partnerCodeService.isConnected) { _, isConnected in
    if isConnected {
        if partnerCodeService.partnerInfo?.isSubscribed == true {
            print("🔥🔥🔥 INHERITANCE: CONNEXION ETABLIE AVEC PARTENAIRE PREMIUM - FINALISATION DIRECTE")
            viewModel.skipSubscriptionDueToInheritance()  // 1️⃣ Marquer skip
            viewModel.finalizeOnboarding(withSubscription: true)  // 2️⃣ Finaliser direct
        } else {
            viewModel.nextStep()  // ➡️ Vers page subscription
        }
    }
}
```

### Méthodes de Contrôle (`OnboardingViewModel.swift`)

```swift
// Marquer le flag de saut
func skipSubscriptionDueToInheritance() {
    print("🔥 OnboardingViewModel: Abonnement hérité du partenaire premium - skip subscription")
    shouldSkipSubscription = true
}

// Finaliser directement l'onboarding
func finalizeOnboarding(withSubscription isSubscribed: Bool = false) {
    print("🔥 OnboardingViewModel: Finalisation complète de l'onboarding")

    // Création utilisateur avec statut abonnement hérité
    FirebaseService.shared.finalizeOnboardingWithPartnerData(
        name: userName,
        // ... autres paramètres
        isSubscribed: isSubscribed  // ✅ true pour partenaire premium
    ) { [weak self] success, user in
        if success {
            appState.updateUser(user)
            appState.completeOnboarding()  // 🎯 SAUT VERS APP PRINCIPALE
        }
    }
}
```

---

## 🔥 Backend Firebase (`index.js`)

### Cloud Function: `connectPartners`

```javascript
// 5. Effectuer la connexion dans une transaction
await admin.firestore().runTransaction(async (transaction) => {
  const currentUserUpdate = {
    partnerId: partnerUserId,
    partnerConnectedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  // 🎁 HÉRITAGE AUTOMATIQUE si partenaire premium
  if (partnerIsSubscribed) {
    console.log("🔗 connectPartners: Héritage de l'abonnement...");
    currentUserUpdate.isSubscribed = true;
    currentUserUpdate.subscriptionInheritedFrom = partnerUserId;
    currentUserUpdate.subscriptionInheritedAt =
      admin.firestore.FieldValue.serverTimestamp();
    currentUserUpdate.subscriptionType = "shared_from_partner";

    // 📊 Logger pour conformité Apple
    const logData = {
      fromUserId: partnerUserId,
      toUserId: currentUserId,
      sharedAt: admin.firestore.FieldValue.serverTimestamp(),
      subscriptionType: "inherited",
      deviceInfo: "iOS App",
      appVersion: "1.0",
    };

    transaction.create(
      admin.firestore().collection("subscription_sharing_logs").doc(),
      logData
    );
  }

  transaction.update(currentUserDoc.ref, currentUserUpdate);
  // ...
});
```

### Structure Firebase Résultante

```json
// Collection: users/[userId]
{
  "isSubscribed": true,
  "subscriptionType": "shared_from_partner",
  "subscriptionInheritedFrom": "partnerUserId123",
  "subscriptionInheritedAt": "2024-01-15T10:30:00Z",
  "partnerId": "partnerUserId123",
  "partnerConnectedAt": "2024-01-15T10:30:00Z"
}

// Collection: subscription_sharing_logs/[logId]
{
  "fromUserId": "partnerUserId123",
  "toUserId": "currentUserId456",
  "sharedAt": "2024-01-15T10:30:00Z",
  "subscriptionType": "inherited",
  "deviceInfo": "iOS App",
  "appVersion": "1.0"
}
```

---

## 🔔 Système de Notifications

### Envoi Notification (`PartnerCodeService.swift`)

```swift
private func notifyConnectionSuccess(
    partnerName: String,
    subscriptionInherited: Bool
) {
    // 🎁 Notifier l'héritage si applicable
    if subscriptionInherited {
        print("✅ PartnerCodeService: Notification héritage abonnement envoyée")
        NotificationCenter.default.post(name: .subscriptionInherited, object: nil)
    }

    // Notifier connexion réussie
    NotificationCenter.default.post(
        name: .partnerConnected,
        object: nil,
        userInfo: [
            "partnerName": partnerName,
            "isSubscribed": subscriptionInherited
        ]
    )
}
```

### Déclaration Notifications

```swift
extension Notification.Name {
    static let subscriptionInherited = Notification.Name("subscriptionInherited")
    static let partnerConnected = Notification.Name("partnerConnected")
    static let partnerConnectionSuccess = Notification.Name("partnerConnectionSuccess")
}
```

---

## 🚀 Flux Complet iOS

### Scénario 1: Partenaire Premium ✅

1. **Utilisateur entre code** partenaire premium
2. **Firebase connectPartners()** → héritage automatique
3. **PartnerCodeService** reçoit `subscriptionInherited: true`
4. **NotificationCenter** envoie `.subscriptionInherited`
5. **PartnerCodeStepView** détecte via observer
6. **skipSubscriptionDueToInheritance()** + **finalizeOnboarding(true)**
7. **🎯 SAUT DIRECT** vers app principale

### Scénario 2: Partenaire Gratuit ❌

1. **Utilisateur entre code** partenaire gratuit
2. **Firebase connectPartners()** → connexion sans héritage
3. **PartnerCodeService** reçoit `subscriptionInherited: false`
4. **PartnerCodeStepView** appelle **viewModel.nextStep()**
5. **➡️ SUITE NORMALE** vers page subscription

---

## 🤖 Adaptation Android (Kotlin)

### 1. Observer Pattern avec StateFlow

```kotlin
// OnboardingViewModel.kt
class OnboardingViewModel : ViewModel() {
    private val _shouldSkipSubscription = MutableStateFlow(false)
    val shouldSkipSubscription = _shouldSkipSubscription.asStateFlow()

    private val _subscriptionInherited = MutableStateFlow(false)
    val subscriptionInherited = _subscriptionInherited.asStateFlow()

    fun skipSubscriptionDueToInheritance() {
        Log.d(TAG, "🔥 Abonnement hérité du partenaire premium - skip subscription")
        _shouldSkipSubscription.value = true
    }

    fun finalizeOnboarding(withSubscription: Boolean = false) {
        Log.d(TAG, "🔥 Finalisation complète de l'onboarding")

        viewModelScope.launch {
            val success = FirebaseService.finalizeOnboardingWithPartnerData(
                // ... paramètres
                isSubscribed = withSubscription
            )

            if (success) {
                // 🎯 NAVIGATION VERS MAIN ACTIVITY
                _navigationEvent.emit(NavigationEvent.NavigateToMain)
            }
        }
    }
}
```

### 2. PartnerCodeScreen Compose

```kotlin
@Composable
fun PartnerCodeScreen(
    onboardingViewModel: OnboardingViewModel,
    partnerCodeViewModel: PartnerCodeViewModel
) {
    // OBSERVER 1: Héritage d'abonnement
    LaunchedEffect(partnerCodeViewModel.subscriptionInherited) {
        partnerCodeViewModel.subscriptionInherited.collectLatest { inherited ->
            if (inherited && partnerCodeViewModel.partnerInfo?.isSubscribed == true) {
                Log.d(TAG, "🔥🔥🔥 INHERITANCE: PARTENAIRE PREMIUM DETECTE - SKIP VERS FINALISATION")
                onboardingViewModel.skipSubscriptionDueToInheritance()
                onboardingViewModel.finalizeOnboarding(withSubscription = true)
            } else {
                onboardingViewModel.nextStep()
            }
        }
    }

    // OBSERVER 2: Connexion établie
    LaunchedEffect(partnerCodeViewModel.isConnected) {
        partnerCodeViewModel.isConnected.collectLatest { connected ->
            if (connected) {
                if (partnerCodeViewModel.partnerInfo?.isSubscribed == true) {
                    Log.d(TAG, "🔥🔥🔥 INHERITANCE: CONNEXION PREMIUM - FINALISATION DIRECTE")
                    onboardingViewModel.skipSubscriptionDueToInheritance()
                    onboardingViewModel.finalizeOnboarding(withSubscription = true)
                } else {
                    Log.d(TAG, "🔗 Partenaire sans abonnement - vers page subscription")
                    onboardingViewModel.nextStep()
                }
            }
        }
    }

    // UI Content...
}
```

### 3. PartnerCodeService Android

```kotlin
class PartnerCodeService {
    private val _subscriptionInherited = MutableStateFlow(false)
    val subscriptionInherited = _subscriptionInherited.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    suspend fun connectToPartner(code: String): Boolean {
        return try {
            val functions = Firebase.functions
            val data = hashMapOf("partnerCode" to code)

            val result = functions
                .getHttpsCallable("connectToPartner")
                .call(data)
                .await()

            val response = result.data as HashMap<String, Any>
            val success = response["success"] as? Boolean ?: false

            if (success) {
                val subscriptionInherited = response["subscriptionInherited"] as? Boolean ?: false
                val partnerName = response["partnerName"] as? String ?: "Partenaire"

                // Mettre à jour l'état
                _isConnected.value = true
                _subscriptionInherited.value = subscriptionInherited

                Log.d(TAG, "✅ Connexion réussie - Abonnement hérité: $subscriptionInherited")

                // Analytics
                Firebase.analytics.logEvent("partenaire_connecte", null)
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur connexion partenaire: ${e.message}")
            false
        }
    }
}
```

### 4. Navigation Conditionnelle

```kotlin
// OnboardingActivity.kt
class OnboardingActivity : ComponentActivity() {

    private fun observeOnboardingFlow() {
        lifecycleScope.launch {
            onboardingViewModel.navigationEvent.collectLatest { event ->
                when (event) {
                    is NavigationEvent.NavigateToMain -> {
                        // 🎯 SAUT VERS MAIN - Abonnement hérité
                        startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                        finish()
                    }
                    is NavigationEvent.NavigateToSubscription -> {
                        // ➡️ Vers page subscription normale
                        // Navigation normale...
                    }
                }
            }
        }
    }
}
```

### 5. Firebase Cloud Functions (identique)

Le code Firebase en `index.js` reste **exactement le même** car il est partagé entre iOS et Android.

---

## 📊 Analytics & Logs

### iOS Tracking

```swift
// PartnerCodeService.swift
Analytics.logEvent("partenaire_connecte", parameters: [
    "subscription_inherited": subscriptionInherited
])

// AnalyticsService.swift
func track(_ event: AnalyticsEvent) {
    case .connectSuccess(let inheritedSub, let context):
        Analytics.logEvent("connect_success", parameters: [
            "inherited_subscription": inheritedSub,
            "context": context
        ])
}
```

### Android Tracking

```kotlin
// PartnerCodeService.kt
Firebase.analytics.logEvent("partenaire_connecte") {
    param("subscription_inherited", subscriptionInherited)
}

Firebase.analytics.logEvent("connect_success") {
    param("inherited_subscription", inheritedSub)
    param("context", context)
}
```

---

## 🔒 Sécurité & Conformité

### Logs Apple (Collection: `subscription_sharing_logs`)

```json
{
  "fromUserId": "premium_partner_id",
  "toUserId": "new_user_id",
  "sharedAt": "2024-01-15T10:30:00Z",
  "subscriptionType": "inherited",
  "deviceInfo": "iOS App / Android App",
  "appVersion": "1.0"
}
```

### Validation Côté Serveur

- ✅ **Code existe** et **non expiré**
- ✅ **Partenaire différent** (pas soi-même)
- ✅ **Statut subscription** vérifié
- ✅ **Transaction atomique** Firebase
- ✅ **Logs conformité** Apple/Google

---

## 🎯 Points Clés Android

1. **StateFlow** remplace **NotificationCenter**
2. **LaunchedEffect** remplace **onReceive**
3. **collectLatest** pour observing continu
4. **Navigation Events** via **ViewModel**
5. **Firebase Cloud Functions** identiques
6. **Analytics** avec Firebase Android SDK
7. **Logs sécurité** partagés

---

## 🚀 Résumé du Flux

**Partenaire Premium:** Code → Firebase → Héritage → Skip → Main App  
**Partenaire Gratuit:** Code → Firebase → Connexion → Subscription → Suite normale

✅ **iOS:** Implémenté avec NotificationCenter + Observers  
🤖 **Android:** À implémenter avec StateFlow + LaunchedEffect
