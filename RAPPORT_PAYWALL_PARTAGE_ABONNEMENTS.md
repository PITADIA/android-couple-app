# Rapport : Système Paywall et Partage d'Abonnements - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille l'architecture complète du système paywall in-app et de partage d'abonnements dans l'application iOS CoupleApp, incluant la logique freemium, la détection automatique des partages entre partenaires, l'intégration Firebase avec webhooks Apple, et les recommandations pour l'adaptation Android.

---

## 🏗️ Architecture Générale du Système

```
┌─────────────────────────────────────────────────────────────────┐
│                    SYSTÈME PAYWALL & ABONNEMENTS               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  COUCHE CLIENT iOS                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │FreemiumManager│  │SubscriptionView│  │   StoreKit   │          │
│  │- handleTaps   │  │  - Paywall   │  │- validateReceipt│         │
│  │- canAccess*   │  │  - UI/UX     │  │- transactions │         │
│  │- showPaywall  │  │  - Analytics │  │- products     │         │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  COUCHE PARTAGE PARTENAIRES                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │PartnerSubSync│  │PartnerSubNotif│  │  FirebaseService │      │
│  │- syncSubs    │  │- listenChanges│  │- listenUser   │         │
│  │- detectShare │  │- notifications│  │- updateState  │         │
│  │- inheritance │  │- inheritance  │  │- realtime     │         │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  COUCHE FIREBASE BACKEND                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Cloud Functions │  │Apple Webhooks │  │  Firestore   │         │
│  │- validateReceipt│  │- INITIAL_BUY │  │- users       │         │
│  │- syncPartners │  │- DID_RENEW   │  │- subscriptions│         │
│  │- shareLogic   │  │- EXPIRED     │  │- sharing_logs │         │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

FLUX COMPLET:
1. Utilisateur tape contenu premium → FreemiumManager.handleCategoryTap()
2. Si pas abonné → showingSubscription = true → SubscriptionView
3. Achat StoreKit → validateAppleReceipt() → Firebase
4. Si partenaire connecté → syncPartnerSubscriptions() → Partage auto
5. Webhooks Apple → Synchronisation temps réel états abonnements
```

---

## 💰 1. Système Paywall In-App - FreemiumManager

### 1.1 Architecture FreemiumManager

**Localisation :** `ViewModels/FreemiumManager.swift`

```swift
class FreemiumManager: ObservableObject {
    @Published var showingSubscription = false
    @Published var blockedCategoryAttempt: QuestionCategory?

    // Configuration freemium
    private let questionsPerPack = 32
    private let freePacksLimit = 2              // 64 questions gratuites
    private let freeJournalEntriesLimit = 5     // 5 entrées journal

    private weak var appState: AppState?
    private var cancellables = Set<AnyCancellable>()
}
```

### 1.2 Logique de Vérification d'Accès

#### handleCategoryTap() - Gestion Catégories Premium

```swift
func handleCategoryTap(_ category: QuestionCategory, onSuccess: @escaping () -> Void) {
    print("🔥 FreemiumManager: Tap sur catégorie: \(category.title)")

    // Vérifier si l'utilisateur est abonné
    let isSubscribed = appState?.currentUser?.isSubscribed ?? false

    // 1. Utilisateur abonné → Accès illimité
    if isSubscribed {
        print("🔥 Freemium TAP: UTILISATEUR ABONNE - ACCES ILLIMITE")
        onSuccess()
        return
    }

    // 2. Catégorie Premium + Non abonné → Paywall
    if category.isPremium {
        print("🔥 Freemium TAP: CATEGORIE PREMIUM - ACCES BLOQUE")

        blockedCategoryAttempt = category
        showingSubscription = true

        // Analytics tracking
        Analytics.logEvent("paywall_affiche", parameters: [
            "source": "freemium_limite"
        ])

        // Notification UI
        NotificationCenter.default.post(name: .freemiumManagerChanged, object: nil)
        return
    }

    // 3. Catégorie gratuite → Accès autorisé (limitation au niveau questions)
    onSuccess()
}
```

#### handleQuestionAccess() - Gestion Questions Individuelles

```swift
func handleQuestionAccess(at index: Int, in category: QuestionCategory, onSuccess: @escaping () -> Void) {
    print("🔥 Freemium QUESTION: Tentative accès question \(index + 1) dans \(category.title)")

    if canAccessQuestion(at: index, in: category) {
        print("🔥 Freemium QUESTION: Accès autorisé")
        onSuccess()
    } else {
        print("🔥 Freemium QUESTION: Accès bloqué - Affichage paywall")

        blockedCategoryAttempt = category
        showingSubscription = true

        // Analytics: Paywall affiché pour question
        Analytics.logEvent("paywall_affiche", parameters: [
            "source": "freemium_limite"
        ])

        trackQuestionBlocked(at: index, in: category)
    }
}

/// NOUVEAU: Vérifie si l'utilisateur peut accéder à une question spécifique
func canAccessQuestion(at index: Int, in category: QuestionCategory) -> Bool {
    // Si l'utilisateur est abonné, accès illimité
    if appState?.currentUser?.isSubscribed ?? false {
        return true
    }

    // Si c'est une catégorie premium, aucun accès
    if category.isPremium {
        return false
    }

    // Pour la catégorie "En couple" gratuite, limiter à 2 packs (64 questions)
    if category.id == "en-couple" {
        let maxFreeQuestions = freePacksLimit * questionsPerPack // 2 * 32 = 64
        return index < maxFreeQuestions
    }

    // Autres catégories gratuites
    return true
}
```

### 1.3 Limites Freemium

| Contenu                    | Version Gratuite         | Version Premium        |
| -------------------------- | ------------------------ | ---------------------- |
| **Catégories**             | 1 gratuite ("En couple") | 8 catégories complètes |
| **Questions "En couple"**  | 64 questions (2 packs)   | Illimitées (~300+)     |
| **Catégories premium**     | ❌ Bloquées              | ✅ Accès complet       |
| **Questions quotidiennes** | 3 premiers jours         | Illimitées             |
| **Défis quotidiens**       | 3 premiers jours         | Illimités              |
| **Entrées journal**        | 5 entrées maximum        | Illimitées             |
| **Widgets iOS**            | ✅ Gratuits              | ✅ Gratuits            |

### 1.4 Questions du Jour - Logique Freemium

```swift
extension FreemiumManager {
    private var freeDailyQuestionDays: Int { 3 } // 3 premiers jours gratuits

    /// Vérifie si l'utilisateur peut accéder à la question du jour actuel
    func canAccessDailyQuestion(for questionDay: Int) -> Bool {
        // Si l'utilisateur est abonné, accès illimité
        if appState?.currentUser?.isSubscribed ?? false {
            return true
        }

        // ✅ LOGIQUE FREEMIUM : Bloquer après le jour 3
        return questionDay <= freeDailyQuestionDays
    }

    /// Gère l'accès aux questions du jour
    func handleDailyQuestionAccess(currentQuestionDay: Int, onSuccess: @escaping () -> Void) {
        let isSubscribed = appState?.currentUser?.isSubscribed ?? false

        if isSubscribed {
            markDailyQuestionUsage(day: currentQuestionDay)
            onSuccess()
            return
        }

        // VÉRIFICATION FREEMIUM
        if currentQuestionDay <= freeDailyQuestionDays {
            print("📅 FreemiumManager: Jour \(currentQuestionDay)/\(freeDailyQuestionDays) - Accès gratuit autorisé")
            markDailyQuestionUsage(day: currentQuestionDay)
            onSuccess()
        } else {
            print("📅 FreemiumManager: Jour \(currentQuestionDay) > limite - Affichage paywall")
            showDailyQuestionPaywall()
        }
    }

    private func showDailyQuestionPaywall() {
        showingSubscription = true

        // Analytics
        Analytics.logEvent("paywall_affiche", parameters: [
            "source": "daily_question_freemium"
        ])

        NotificationCenter.default.post(name: .freemiumManagerChanged, object: nil)
    }
}
```

---

## 🎨 2. Interface Paywall - SubscriptionView

### 2.1 Architecture SubscriptionView

**Localisation :** `Views/Subscription/SubscriptionView.swift`

```swift
struct SubscriptionView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var receiptService = AppleReceiptService.shared
    @StateObject private var pricingService = StoreKitPricingService.shared
    @Environment(\.dismiss) private var dismiss

    @State private var showingAppleSignIn = false
    @State private var showingSuccessMessage = false
    @State private var purchaseCompleted = false

    var body: some View {
        ZStack {
            // Fond gris clair identique aux autres pages
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Header avec croix de fermeture
                HStack {
                    Button(action: {
                        print("🔥 SubscriptionView: Fermeture via croix")
                        appState.freemiumManager?.dismissSubscription()
                        dismiss()
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.black)
                    }
                    .padding(.leading, 20)

                    Spacer()
                }
                .padding(.top, 10)

                // Contenu paywall...
            }
        }
        .onReceive(receiptService.$isSubscribed) { isSubscribed in
            if isSubscribed {
                print("🎉 SubscriptionView: Abonnement confirmé - Fermeture automatique")
                appState.freemiumManager?.dismissSubscription()
                dismiss()
            }
        }
    }
}
```

### 2.2 Paywalls Spécialisés

#### DailyQuestionPaywallView - Questions Quotidiennes

```swift
struct DailyQuestionPaywallView: View {
    @EnvironmentObject var appState: AppState
    let questionDay: Int
    @State private var showSubscriptionSheet = false

    var body: some View {
        // Interface paywall spécialisée avec carte floutée
        VStack(spacing: 30) {
            // Titre spécialisé
            Text("paywall_page_title_questions".localized(tableName: "DailyQuestions"))
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.black)

            // Carte de question floutée (effet preview)
            BlurredQuestionCard(questionDay: questionDay)

            // Bouton d'upgrade
            UpgradeButton {
                showSubscriptionSheet = true
            }
        }
        .sheet(isPresented: $showSubscriptionSheet) {
            SubscriptionView()
                .environmentObject(appState)
        }
        .onAppear {
            // Analytics: Paywall vu
            Analytics.logEvent("paywall_viewed", parameters: [
                "source": "daily_question_freemium",
                "question_day": questionDay
            ])
        }
    }
}
```

#### FreemiumPaywallCardView - Carte Inline

```swift
struct FreemiumPaywallCardView: View {
    let category: QuestionCategory
    let questionsUnlocked: Int
    let totalQuestions: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 30) {
                VStack(spacing: 20) {
                    // Émoji de la catégorie
                    Text(category.emoji)
                        .font(.system(size: 60))
                        .padding(.bottom, 10)

                    Text("congratulations".localized)
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)

                    Text("keep_going_unlock_all".localized)
                        .font(.system(size: 18))
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)

                    // Bouton d'action
                    HStack(spacing: 8) {
                        Text("continue".localized)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)

                        Image(systemName: "arrow.right.circle.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                    }
                }
            }
            // Dégradé de la catégorie
            .background(
                LinearGradient(
                    gradient: Gradient(colors: category.gradientColors.map { Color(hex: $0) }),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}
```

---

## 🤝 3. Système de Partage d'Abonnements

### 3.1 Vue d'Ensemble du Partage

Le système permet à un partenaire abonné de **partager automatiquement** son abonnement avec l'autre partenaire connecté, créant un **accès premium pour les deux** avec un seul paiement.

#### Types d'Abonnements

| Type                    | Description                     | Champ Firebase                            | Behavior            |
| ----------------------- | ------------------------------- | ----------------------------------------- | ------------------- |
| `"direct"`              | Abonnement acheté directement   | `subscriptionType: "direct"`              | Source de partage   |
| `"shared_from_partner"` | Abonnement hérité du partenaire | `subscriptionType: "shared_from_partner"` | Bénéficiaire        |
| `null/undefined`        | Pas d'abonnement                | `isSubscribed: false`                     | Utilisateur gratuit |

### 3.2 PartnerSubscriptionSyncService - Synchronisation

**Localisation :** `Services/PartnerSubscriptionSyncService.swift`

```swift
class PartnerSubscriptionSyncService: ObservableObject {
    static let shared = PartnerSubscriptionSyncService()

    private let functions = Functions.functions()
    private var userListener: ListenerRegistration?
    private var partnerListener: ListenerRegistration?

    func startListeningForUser() {
        guard let currentUser = Auth.auth().currentUser else { return }

        // Écouter les changements de l'utilisateur actuel
        userListener = Firestore.firestore()
            .collection("users")
            .document(currentUser.uid)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    print("❌ PartnerSubscriptionSyncService: Erreur listener utilisateur: \(error)")
                    return
                }

                guard let data = snapshot?.data(),
                      let partnerId = data["partnerId"] as? String,
                      !partnerId.isEmpty else {
                    return
                }

                // Synchroniser avec le partenaire
                Task {
                    await self?.syncSubscriptionsWithPartner(userId: currentUser.uid, partnerId: partnerId)
                }
            }
    }

    private func syncSubscriptionsWithPartner(userId: String, partnerId: String) async {
        do {
            let data = ["partnerId": partnerId]
            let result = try await functions.httpsCallable("syncPartnerSubscriptions").call(data)

            if let resultData = result.data as? [String: Any],
               let success = resultData["success"] as? Bool,
               success {
                print("✅ PartnerSubscriptionSyncService: Synchronisation réussie")

                // Notifier les changements si héritage détecté
                if let inherited = resultData["subscriptionInherited"] as? Bool,
                   inherited,
                   let fromPartnerName = resultData["fromPartnerName"] as? String {

                    // Analytics: Abonnement partagé
                    Analytics.logEvent("abonnement_partage_partenaire", parameters: [:])

                    await MainActor.run {
                        NotificationCenter.default.post(
                            name: .partnerSubscriptionShared,
                            object: nil,
                            userInfo: [
                                "partnerId": userId,
                                "fromPartnerName": fromPartnerName
                            ]
                        )
                    }
                }
            }
        } catch {
            print("❌ PartnerSubscriptionSyncService: Erreur synchronisation: \(error)")
        }
    }
}
```

### 3.3 PartnerSubscriptionNotificationService - Notifications

**Localisation :** `Services/PartnerSubscriptionNotificationService.swift`

```swift
class PartnerSubscriptionNotificationService: ObservableObject {
    static let shared = PartnerSubscriptionNotificationService()

    private var partnerListener: ListenerRegistration?
    private var cancellables = Set<AnyCancellable>()

    private func startListeningForPartnerSubscription() {
        guard let currentUser = Auth.auth().currentUser else { return }

        partnerListener = Firestore.firestore()
            .collection("users")
            .document(currentUser.uid)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let data = snapshot?.data() else { return }

                // Vérifier si l'utilisateur a hérité d'un abonnement
                if let inheritedFrom = data["subscriptionSharedFrom"] as? String,
                   !inheritedFrom.isEmpty,
                   let isSubscribed = data["isSubscribed"] as? Bool,
                   isSubscribed {
                    Task {
                        await self?.handleSubscriptionInherited(from: inheritedFrom)
                    }
                }

                // Vérifier si l'utilisateur a perdu son abonnement hérité
                if let wasSubscribed = data["isSubscribed"] as? Bool,
                   !wasSubscribed,
                   let subscriptionType = data["subscriptionType"] as? String,
                   subscriptionType == "shared_from_partner" {
                    Task {
                        await self?.handleSubscriptionLost()
                    }
                }
            }
    }

    private func handleSubscriptionInherited(from partnerId: String) async {
        // Récupérer le nom du partenaire
        do {
            let partnerDoc = try await Firestore.firestore()
                .collection("users")
                .document(partnerId)
                .getDocument()

            let partnerName = partnerDoc.data()?["name"] as? String ?? "Votre partenaire"

            await MainActor.run {
                // Afficher notification de partage
                showSubscriptionInheritedNotification(from: partnerName)

                // Notifier les services
                NotificationCenter.default.post(
                    name: .partnerSubscriptionInherited,
                    object: nil,
                    userInfo: ["partnerName": partnerName]
                )
            }
        } catch {
            print("❌ Erreur récupération nom partenaire: \(error)")
        }
    }

    private func showSubscriptionInheritedNotification(from partnerName: String) {
        // Notification iOS native
        let notification = UNMutableNotificationContent()
        notification.title = "🎉 Abonnement Premium Activé !"
        notification.body = "\(partnerName) a partagé son abonnement avec vous. Vous avez maintenant accès à toutes les fonctionnalités premium !"
        notification.sound = .default

        let request = UNNotificationRequest(
            identifier: "subscription_inherited",
            content: notification,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        )

        UNUserNotificationCenter.current().add(request)
    }
}
```

---

## 🔥 4. Firebase Backend - Cloud Functions

### 4.1 syncPartnerSubscriptions() - Logique de Partage

**Localisation :** `firebase/functions/index.js:2171-2366`

```javascript
exports.syncPartnerSubscriptions = functions.https.onCall(
  async (data, context) => {
    console.log("🔄 syncPartnerSubscriptions: Début synchronisation");

    // Authentification
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const currentUserId = context.auth.uid;
    const { partnerId } = data;

    // Validation robuste des IDs
    if (
      !currentUserId ||
      !partnerId ||
      typeof currentUserId !== "string" ||
      typeof partnerId !== "string" ||
      currentUserId.trim() === "" ||
      partnerId.trim() === ""
    ) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "IDs utilisateur invalides"
      );
    }

    try {
      // Récupérer les données des deux utilisateurs
      const [currentUserDoc, partnerUserDoc] = await Promise.all([
        admin.firestore().collection("users").doc(currentUserId).get(),
        admin.firestore().collection("users").doc(partnerId).get(),
      ]);

      if (!currentUserDoc.exists || !partnerUserDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Utilisateur ou partenaire non trouvé"
        );
      }

      const currentUserData = currentUserDoc.data();
      const partnerUserData = partnerUserDoc.data();

      // Vérifier que les utilisateurs sont bien connectés
      if (
        currentUserData.partnerId !== partnerId ||
        partnerUserData.partnerId !== currentUserId
      ) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Les utilisateurs ne sont pas connectés en tant que partenaires"
        );
      }

      const currentIsSubscribed = currentUserData.isSubscribed || false;
      const currentSubscriptionType = currentUserData.subscriptionType;

      const partnerIsSubscribed = partnerUserData.isSubscribed || false;
      const partnerSubscriptionType = partnerUserData.subscriptionType;

      let subscriptionInherited = false;
      let fromPartnerName = "";

      // ✨ LOGIQUE DE SYNCHRONISATION

      if (currentIsSubscribed && currentSubscriptionType === "direct") {
        // 1. L'utilisateur actuel a un abonnement direct → Partager avec le partenaire
        if (
          !partnerIsSubscribed ||
          partnerSubscriptionType !== "shared_from_partner"
        ) {
          await admin.firestore().collection("users").doc(partnerId).update({
            isSubscribed: true,
            subscriptionType: "shared_from_partner",
            subscriptionSharedFrom: currentUserId,
            subscriptionSharedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
          console.log(
            "✅ syncPartnerSubscriptions: Abonnement partagé vers le partenaire"
          );
        }
      } else if (partnerIsSubscribed && partnerSubscriptionType === "direct") {
        // 2. Le partenaire a un abonnement direct → Partager avec l'utilisateur actuel
        if (
          !currentIsSubscribed ||
          currentSubscriptionType !== "shared_from_partner"
        ) {
          await admin
            .firestore()
            .collection("users")
            .doc(currentUserId)
            .update({
              isSubscribed: true,
              subscriptionType: "shared_from_partner",
              subscriptionSharedFrom: partnerId,
              subscriptionSharedAt:
                admin.firestore.FieldValue.serverTimestamp(),
            });
          subscriptionInherited = true;
          fromPartnerName = partnerUserData.name || "Partenaire";
          console.log(
            "✅ syncPartnerSubscriptions: Abonnement hérité du partenaire"
          );
        }
      } else if (!currentIsSubscribed && !partnerIsSubscribed) {
        // 3. Aucun des deux n'a d'abonnement direct → Nettoyer les abonnements partagés
        const batch = admin.firestore().batch();

        const currentUserRef = admin
          .firestore()
          .collection("users")
          .doc(currentUserId);
        const partnerUserRef = admin
          .firestore()
          .collection("users")
          .doc(partnerId);

        batch.update(currentUserRef, {
          isSubscribed: false,
          subscriptionType: admin.firestore.FieldValue.delete(),
          subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
          subscriptionSharedAt: admin.firestore.FieldValue.delete(),
        });

        batch.update(partnerUserRef, {
          isSubscribed: false,
          subscriptionType: admin.firestore.FieldValue.delete(),
          subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
          subscriptionSharedAt: admin.firestore.FieldValue.delete(),
        });

        await batch.commit();
        console.log(
          "✅ syncPartnerSubscriptions: Abonnements nettoyés - mode gratuit"
        );
      }

      return {
        success: true,
        subscriptionInherited: subscriptionInherited,
        fromPartnerName: fromPartnerName,
      };
    } catch (error) {
      console.error("❌ syncPartnerSubscriptions: Erreur:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);
```

### 4.2 Webhooks Apple - Synchronisation Temps Réel

#### appleWebhook() - Écoute des Notifications Apple

```javascript
exports.appleWebhook = functions.https.onRequest(async (req, res) => {
  try {
    console.log("🔥 appleWebhook: Notification reçue d'Apple");

    if (req.method !== "POST") {
      return res.status(405).send("Method Not Allowed");
    }

    const notification = req.body;
    const notificationType = notification.notification_type;
    const receiptData = notification.unified_receipt;

    switch (notificationType) {
      case "INITIAL_BUY":
      case "DID_RENEW":
        console.log("🔥 appleWebhook: Nouvel achat ou renouvellement");
        await handleSubscriptionActivation(receiptData);
        break;

      case "DID_FAIL_TO_RENEW":
      case "EXPIRED":
        console.log("🔥 appleWebhook: Échec de renouvellement ou expiration");
        await handleSubscriptionExpiration(receiptData);
        break;

      case "DID_CANCEL":
        console.log("🔥 appleWebhook: Annulation d'abonnement");
        await handleSubscriptionCancellation(receiptData);
        break;

      default:
        console.log("🔥 appleWebhook: Type non géré:", notificationType);
    }

    res.status(200).send("OK");
  } catch (error) {
    console.error("🔥 appleWebhook: Erreur:", error);
    res.status(500).send("Internal Server Error");
  }
});
```

#### handleSubscriptionActivation() - Activation

```javascript
async function handleSubscriptionActivation(receiptData) {
  try {
    const latestReceiptInfo = receiptData.latest_receipt_info || [];

    for (const purchase of latestReceiptInfo) {
      const originalTransactionId = purchase.original_transaction_id;

      // Chercher l'utilisateur avec cet ID de transaction
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
        const subscriptionData = {
          isSubscribed: true,
          subscriptionType: "direct", // 🔑 MARQUAGE DIRECT
          purchaseDate: new Date(parseInt(purchase.purchase_date_ms)),
          expiresDate: purchase.expires_date_ms
            ? new Date(parseInt(purchase.expires_date_ms))
            : null,
          transactionId: purchase.transaction_id,
          originalTransactionId: purchase.original_transaction_id,
          lastValidated: admin.firestore.FieldValue.serverTimestamp(),
        };

        // Mise à jour utilisateur
        await userDoc.ref.update({
          isSubscribed: true,
          subscriptionType: "direct",
          subscriptionDetails: subscriptionData,
        });

        console.log(
          "🔥 handleSubscriptionActivation: Abonnement activé pour:",
          userDoc.id
        );

        // 🚀 DÉCLENCHEMENT AUTOMATIQUE DU PARTAGE
        const userData = await userDoc.ref.get();
        const partnerId = userData.data()?.partnerId;

        if (partnerId) {
          console.log(
            "🔄 handleSubscriptionActivation: Déclenchement partage automatique"
          );

          // Déclencher la synchronisation avec le partenaire
          try {
            await admin.firestore().collection("users").doc(partnerId).update({
              isSubscribed: true,
              subscriptionType: "shared_from_partner",
              subscriptionSharedFrom: userDoc.id,
              subscriptionSharedAt:
                admin.firestore.FieldValue.serverTimestamp(),
            });

            // Logger pour conformité Apple
            await admin
              .firestore()
              .collection("subscription_sharing_logs")
              .add({
                fromUserId: userDoc.id,
                toUserId: partnerId,
                sharedAt: admin.firestore.FieldValue.serverTimestamp(),
                subscriptionType: "inherited",
                triggerSource: "apple_webhook_activation",
                deviceInfo: "iOS App",
                appVersion: "1.0",
              });

            console.log(
              "✅ handleSubscriptionActivation: Partage automatique réussi"
            );
          } catch (shareError) {
            console.error(
              "❌ handleSubscriptionActivation: Erreur partage:",
              shareError
            );
          }
        }
      }
    }
  } catch (error) {
    console.error("🔥 handleSubscriptionActivation: Erreur:", error);
  }
}
```

#### handleSubscriptionExpiration() - Expiration

```javascript
async function handleSubscriptionExpiration(receiptData) {
  try {
    const latestReceiptInfo = receiptData.latest_receipt_info || [];

    for (const purchase of latestReceiptInfo) {
      const originalTransactionId = purchase.original_transaction_id;

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
        const userData = await userDoc.ref.get();
        const partnerId = userData.data()?.partnerId;

        // Désactiver l'abonnement de l'utilisateur principal
        await userDoc.ref.update({
          isSubscribed: false,
          subscriptionType: admin.firestore.FieldValue.delete(),
          "subscriptionDetails.lastValidated":
            admin.firestore.FieldValue.serverTimestamp(),
        });

        console.log(
          "🔥 handleSubscriptionExpiration: Abonnement expiré pour:",
          userDoc.id
        );

        // 🚀 SUPPRESSION AUTOMATIQUE DU PARTAGE
        if (partnerId) {
          console.log(
            "🔄 handleSubscriptionExpiration: Suppression partage automatique"
          );

          try {
            await admin.firestore().collection("users").doc(partnerId).update({
              isSubscribed: false,
              subscriptionType: admin.firestore.FieldValue.delete(),
              subscriptionSharedFrom: admin.firestore.FieldValue.delete(),
              subscriptionSharedAt: admin.firestore.FieldValue.delete(),
            });

            // Logger la suppression de partage
            await admin
              .firestore()
              .collection("subscription_sharing_logs")
              .add({
                fromUserId: userDoc.id,
                toUserId: partnerId,
                sharedAt: admin.firestore.FieldValue.serverTimestamp(),
                subscriptionType: "revoked",
                triggerSource: "apple_webhook_expiration",
                reason: "subscription_expired",
              });

            console.log(
              "✅ handleSubscriptionExpiration: Suppression partage réussie"
            );
          } catch (shareError) {
            console.error(
              "❌ handleSubscriptionExpiration: Erreur suppression:",
              shareError
            );
          }
        }
      }
    }
  } catch (error) {
    console.error("🔥 handleSubscriptionExpiration: Erreur:", error);
  }
}
```

---

## 📊 5. État d'Abonnement - Calcul et Synchronisation

### 5.1 FirebaseService - Listener Temps Réel

**Localisation :** `Services/FirebaseService.swift:745-783`

```swift
class FirebaseService: ObservableObject {
    private var subscriptionListener: ListenerRegistration?

    func startListeningForSubscriptionChanges() {
        guard let user = Auth.auth().currentUser else { return }

        subscriptionListener = db.collection("users").document(user.uid)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    print("❌ FirebaseService: Erreur listener abonnement: \(error)")
                    return
                }

                guard let data = snapshot?.data() else { return }

                let isSubscribed = data["isSubscribed"] as? Bool ?? false
                let subscriptionType = data["subscriptionType"] as? String

                // Mettre à jour l'état local si l'abonnement a changé
                if let currentUser = self?.currentUser, currentUser.isSubscribed != isSubscribed {
                    var updatedUser = currentUser
                    updatedUser.isSubscribed = isSubscribed

                    // Mettre à jour les champs d'héritage si nécessaire
                    if subscriptionType == "shared_from_partner" {
                        updatedUser.subscriptionInheritedFrom = data["subscriptionSharedFrom"] as? String
                        updatedUser.subscriptionInheritedAt = (data["subscriptionSharedAt"] as? Timestamp)?.dateValue()
                    }

                    DispatchQueue.main.async {
                        self?.currentUser = updatedUser
                        print("🔥 FirebaseService: Abonnement mis à jour localement: \(isSubscribed)")

                        // Notifier le changement d'abonnement
                        NotificationCenter.default.post(name: .subscriptionUpdated, object: nil)
                    }
                }
            }
    }
}
```

### 5.2 AppState - Gestion Centralisée

**Localisation :** `ViewModels/AppState.swift:194-224`

```swift
class AppState: ObservableObject {
    @Published var currentUser: AppUser?
    @Published var freemiumManager: FreemiumManager?

    private func setupObservers() {
        firebaseService.$currentUser
            .receive(on: DispatchQueue.main)
            .sink { [weak self] (user: AppUser?) in
                // NOUVEAU: Détecter les changements d'abonnement
                if let oldUser = self?.currentUser, let newUser = user {
                    if oldUser.isSubscribed != newUser.isSubscribed {
                        print("🔒 AppState: Changement d'abonnement détecté: \(oldUser.isSubscribed) -> \(newUser.isSubscribed)")

                        // Mettre à jour les widgets avec le nouveau statut
                        self?.widgetService?.refreshData()

                        // Notifier FreemiumManager du changement
                        self?.freemiumManager?.handleSubscriptionChange(isSubscribed: newUser.isSubscribed)
                    }
                }

                self?.currentUser = user
            }
            .store(in: &cancellables)
    }
}
```

---

## 🏢 6. Structure Firebase

### 6.1 Collection "users" - Schéma d'Abonnement

```javascript
// Document utilisateur dans Firestore
{
  "userId": "abc123...",
  "name": "Marie",
  "email": "marie@example.com",
  "partnerId": "def456...",

  // 🔑 CHAMPS ABONNEMENT PRINCIPAL
  "isSubscribed": true,                    // Boolean - État actuel
  "subscriptionType": "direct",            // "direct" | "shared_from_partner" | null

  // 🔑 CHAMPS PARTAGE (si subscriptionType === "shared_from_partner")
  "subscriptionSharedFrom": "def456...",   // ID du partenaire qui partage
  "subscriptionSharedAt": Timestamp,       // Moment du partage

  // 🔑 DÉTAILS ABONNEMENT (si subscriptionType === "direct")
  "subscriptionDetails": {
    "purchaseDate": Timestamp,
    "expiresDate": Timestamp,
    "transactionId": "1000000123456789",
    "originalTransactionId": "1000000123456789",
    "productId": "com.lyes.love2love.subscription.monthly",
    "lastValidated": Timestamp
  },

  // Autres champs...
  "profileImageURL": "https://...",
  "currentLocation": { ... },
  "createdAt": Timestamp
}
```

### 6.2 Collection "subscription_sharing_logs" - Audit

```javascript
// Log des partages d'abonnement pour conformité Apple
{
  "fromUserId": "abc123...",              // Qui partage
  "toUserId": "def456...",                // Qui reçoit
  "sharedAt": Timestamp,                  // Moment du partage
  "subscriptionType": "inherited",        // "inherited" | "revoked"
  "triggerSource": "apple_webhook_activation", // Source du déclenchement
  "deviceInfo": "iOS App",                // Informations device
  "appVersion": "1.0",                    // Version app
  "reason": null                          // Raison si révocation
}
```

---

## 🔄 7. Flux Complet - Scénarios d'Usage

### 7.1 Scénario 1: Utilisateur Gratuit → Achat → Partage Automatique

```
1. Marie (gratuite) + Paul (gratuit) sont connectés
   - Marie.isSubscribed: false
   - Paul.isSubscribed: false

2. Marie clique sur catégorie premium "Désirs Inavoués"
   - FreemiumManager.handleCategoryTap()
   - category.isPremium: true + !isSubscribed
   - → showingSubscription = true
   - → SubscriptionView s'affiche

3. Marie achète abonnement mensuel
   - StoreKit purchase → validateAppleReceipt()
   - Firebase: Marie.isSubscribed = true, subscriptionType = "direct"
   - → PartnerSubscriptionSyncService détecte le changement

4. Partage automatique déclenché
   - syncPartnerSubscriptions(Marie.id, Paul.id)
   - Paul.isSubscribed = true, subscriptionType = "shared_from_partner"
   - Paul.subscriptionSharedFrom = Marie.id

5. Paul reçoit notification
   - PartnerSubscriptionNotificationService
   - Notification iOS: "Marie a partagé son abonnement avec vous"
   - Paul a maintenant accès à toutes les fonctionnalités premium

RÉSULTAT FINAL:
- Marie: Abonnement direct payant
- Paul: Abonnement partagé gratuit
- Les deux ont accès premium complet
```

### 7.2 Scénario 2: Expiration Abonnement → Suppression Partage

```
1. État initial: Marie (direct) + Paul (shared_from_partner)
   - Les deux ont accès premium

2. Abonnement de Marie expire
   - Apple webhook → handleSubscriptionExpiration()
   - Marie.isSubscribed = false, subscriptionType supprimé

3. Suppression automatique partage
   - Paul.isSubscribed = false
   - Champs de partage supprimés de Paul
   - Log audit créé

4. Les deux reviennent en mode gratuit
   - FreemiumManager limite l'accès
   - Paywalls réapparaissent pour contenu premium

RÉSULTAT FINAL:
- Marie: Mode gratuit (64 questions max)
- Paul: Mode gratuit (64 questions max)
- Incitation à renouveler l'abonnement
```

### 7.3 Scénario 3: Connexion Partenaire avec Abonnement Existant

```
1. Marie (premium) génère code partenaire
   - PartnerCodeService.generateCode()

2. Paul (gratuit) saisit le code
   - PartnerCodeService.connectWithCode()
   - connectPartners() Cloud Function

3. Connexion + Héritage immédiat
   - Paul.partnerId = Marie.id
   - Marie.partnerId = Paul.id
   - Paul hérite automatiquement: isSubscribed = true, shared_from_partner

4. Onboarding Skip Subscription
   - PartnerCodeStepView détecte partnerInfo.isSubscribed = true
   - viewModel.skipSubscriptionDueToInheritance()
   - viewModel.finalizeOnboarding(withSubscription: true)

5. Paul accède directement à l'app premium
   - Pas de page de paiement
   - Accès immédiat à toutes les fonctionnalités

RÉSULTAT FINAL:
- Marie: Continue avec son abonnement direct
- Paul: Accès premium immédiat via partage
- Onboarding optimisé (pas de friction paiement)
```

---

## 🤖 8. Adaptation Android - Implémentation Complète

### 8.1 Architecture Android Équivalente

#### FreemiumManager Android

```kotlin
class FreemiumManager @Inject constructor(
    private val appStateRepository: AppStateRepository,
    private val analyticsService: AnalyticsService
) {
    companion object {
        private const val QUESTIONS_PER_PACK = 32
        private const val FREE_PACKS_LIMIT = 2 // 64 questions gratuites
        private const val FREE_JOURNAL_ENTRIES_LIMIT = 5
        private const val FREE_DAILY_QUESTION_DAYS = 3
    }

    private val _showingSubscription = MutableStateFlow(false)
    val showingSubscription: StateFlow<Boolean> = _showingSubscription

    private val _blockedCategoryAttempt = MutableStateFlow<QuestionCategory?>(null)
    val blockedCategoryAttempt: StateFlow<QuestionCategory?> = _blockedCategoryAttempt

    fun handleCategoryTap(
        category: QuestionCategory,
        currentUser: AppUser?,
        onSuccess: () -> Unit
    ) {
        Log.d("FreemiumManager", "Tap sur catégorie: ${category.title}")

        val isSubscribed = currentUser?.isSubscribed ?: false

        // 1. Utilisateur abonné → Accès illimité
        if (isSubscribed) {
            Log.d("FreemiumManager", "Utilisateur abonné - Accès illimité")
            onSuccess()
            return
        }

        // 2. Catégorie Premium + Non abonné → Paywall
        if (category.isPremium) {
            Log.d("FreemiumManager", "Catégorie premium - Accès bloqué")

            _blockedCategoryAttempt.value = category
            _showingSubscription.value = true

            // Analytics
            analyticsService.logEvent("paywall_affiche") {
                param("source", "freemium_limite")
            }

            return
        }

        // 3. Catégorie gratuite → Accès autorisé
        Log.d("FreemiumManager", "Catégorie gratuite - Accès autorisé")
        onSuccess()
    }

    fun handleQuestionAccess(
        index: Int,
        category: QuestionCategory,
        currentUser: AppUser?,
        onSuccess: () -> Unit
    ) {
        Log.d("FreemiumManager", "Tentative accès question ${index + 1} dans ${category.title}")

        if (canAccessQuestion(index, category, currentUser)) {
            Log.d("FreemiumManager", "Accès question autorisé")
            onSuccess()
        } else {
            Log.d("FreemiumManager", "Accès question bloqué - Paywall")

            _blockedCategoryAttempt.value = category
            _showingSubscription.value = true

            analyticsService.logEvent("paywall_affiche") {
                param("source", "freemium_limite")
            }

            trackQuestionBlocked(index, category)
        }
    }

    fun canAccessQuestion(index: Int, category: QuestionCategory, currentUser: AppUser?): Boolean {
        // Si l'utilisateur est abonné, accès illimité
        if (currentUser?.isSubscribed == true) {
            return true
        }

        // Si c'est une catégorie premium, aucun accès
        if (category.isPremium) {
            return false
        }

        // Pour la catégorie "En couple" gratuite, limiter à 2 packs (64 questions)
        if (category.id == "en-couple") {
            val maxFreeQuestions = FREE_PACKS_LIMIT * QUESTIONS_PER_PACK
            return index < maxFreeQuestions
        }

        return true
    }

    fun canAccessDailyQuestion(questionDay: Int, currentUser: AppUser?): Boolean {
        if (currentUser?.isSubscribed == true) {
            return true
        }

        return questionDay <= FREE_DAILY_QUESTION_DAYS
    }

    fun dismissSubscription() {
        _showingSubscription.value = false
        _blockedCategoryAttempt.value = null
    }

    private fun trackQuestionBlocked(index: Int, category: QuestionCategory) {
        analyticsService.logEvent("question_blocked") {
            param("category_id", category.id)
            param("question_index", index.toLong())
            param("user_type", "free")
        }
    }
}
```

### 8.2 SubscriptionActivity Android

```kotlin
@AndroidEntryPoint
class SubscriptionActivity : ComponentActivity() {

    private val viewModel: SubscriptionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CoupleAppTheme {
                SubscriptionScreen(
                    viewModel = viewModel,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
    ) {
        Column {
            // Header avec croix de fermeture
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        viewModel.dismissSubscription()
                        onDismiss()
                    }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Color.Black
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // Titre principal
            Text(
                text = stringResource(R.string.subscription_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Contenu paywall
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Features premium
                items(getPremiumFeatures()) { feature ->
                    PremiumFeatureCard(feature = feature)
                }

                // Plans d'abonnement
                item {
                    SubscriptionPlansSection(
                        plans = uiState.subscriptionPlans,
                        selectedPlan = uiState.selectedPlan,
                        onPlanSelected = viewModel::selectPlan,
                        isLoading = uiState.isLoading
                    )
                }

                // Bouton d'achat
                item {
                    PurchaseButton(
                        selectedPlan = uiState.selectedPlan,
                        isLoading = uiState.isLoading,
                        onPurchase = viewModel::purchase
                    )
                }

                // Liens légaux
                item {
                    LegalLinksSection()
                }
            }
        }

        // Messages d'erreur
        uiState.errorMessage?.let { error ->
            ErrorSnackbar(
                message = error,
                onDismiss = viewModel::dismissError,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Message de succès
        if (uiState.purchaseCompleted) {
            LaunchedEffect(Unit) {
                delay(2000)
                onDismiss()
            }
        }
    }
}

@Composable
fun PremiumFeatureCard(feature: PremiumFeature) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = feature.emoji,
                fontSize = 32.sp,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(feature.titleRes),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )

                Text(
                    text = stringResource(feature.descriptionRes),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
```

### 8.3 SubscriptionViewModel Android

```kotlin
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val freemiumManager: FreemiumManager,
    private val analyticsService: AnalyticsService,
    private val appStateRepository: AppStateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState

    data class SubscriptionUiState(
        val subscriptionPlans: List<SubscriptionPlan> = emptyList(),
        val selectedPlan: SubscriptionPlan? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val purchaseCompleted: Boolean = false
    )

    init {
        loadSubscriptionPlans()
        trackUpgradePromptShown()
    }

    private fun loadSubscriptionPlans() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val plans = billingManager.getAvailableSubscriptions()
                _uiState.value = _uiState.value.copy(
                    subscriptionPlans = plans,
                    selectedPlan = plans.firstOrNull { it.isPopular },
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Impossible de charger les offres d'abonnement",
                    isLoading = false
                )
            }
        }
    }

    fun selectPlan(plan: SubscriptionPlan) {
        _uiState.value = _uiState.value.copy(selectedPlan = plan)

        analyticsService.logEvent("subscription_plan_selected") {
            param("plan_id", plan.productId)
            param("price", plan.priceAmountMicros.toDouble())
            param("period", plan.subscriptionPeriod)
        }
    }

    fun purchase() {
        val selectedPlan = _uiState.value.selectedPlan ?: return

        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            try {
                val result = billingManager.purchaseSubscription(selectedPlan)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        purchaseCompleted = true,
                        isLoading = false
                    )

                    analyticsService.logEvent("subscription_purchase_success") {
                        param("plan_id", selectedPlan.productId)
                        param("price", selectedPlan.priceAmountMicros.toDouble())
                    }

                    // Mettre à jour l'état d'abonnement local
                    appStateRepository.updateSubscriptionStatus(true)

                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.errorMessage ?: "Erreur lors de l'achat",
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Erreur lors de l'achat: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun dismissSubscription() {
        freemiumManager.dismissSubscription()

        analyticsService.logEvent("paywall_dismissed") {
            param("source", "user_action")
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun trackUpgradePromptShown() {
        analyticsService.logEvent("upgrade_prompt_shown") {
            param("source", "paywall")
            param("timestamp", System.currentTimeMillis())
        }
    }
}
```

### 8.4 Partage d'Abonnements Android

#### PartnerSubscriptionSyncService Android

```kotlin
@Singleton
class PartnerSubscriptionSyncService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val analyticsService: AnalyticsService
) {
    companion object {
        private const val TAG = "PartnerSubscriptionSync"
    }

    private var userListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null

    fun startListeningForUser(userId: String) {
        stopAllListeners()

        userListener = firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erreur listener utilisateur: ${error.message}")
                    return@addSnapshotListener
                }

                val data = snapshot?.data ?: return@addSnapshotListener
                val partnerId = data["partnerId"] as? String

                if (!partnerId.isNullOrEmpty()) {
                    // Synchroniser avec le partenaire
                    syncSubscriptionsWithPartner(userId, partnerId)
                }
            }
    }

    private fun syncSubscriptionsWithPartner(userId: String, partnerId: String) {
        val data = hashMapOf("partnerId" to partnerId)

        functions.getHttpsCallable("syncPartnerSubscriptions")
            .call(data)
            .addOnSuccessListener { result ->
                val resultData = result.data as? Map<String, Any>
                val success = resultData?.get("success") as? Boolean ?: false

                if (success) {
                    Log.d(TAG, "Synchronisation abonnements réussie")

                    // Vérifier si abonnement hérité
                    val inherited = resultData?.get("subscriptionInherited") as? Boolean ?: false
                    val fromPartnerName = resultData?.get("fromPartnerName") as? String

                    if (inherited && !fromPartnerName.isNullOrEmpty()) {
                        analyticsService.logEvent("abonnement_partage_partenaire")

                        // Notification de partage
                        showSubscriptionInheritedNotification(fromPartnerName)
                    }
                } else {
                    Log.w(TAG, "Synchronisation abonnements échouée")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Erreur synchronisation Cloud Function: ${exception.message}")
            }
    }

    private fun showSubscriptionInheritedNotification(partnerName: String) {
        // Créer notification Android
        val notificationManager = NotificationManagerCompat.from(context)

        val notification = NotificationCompat.Builder(context, "subscription_channel")
            .setSmallIcon(R.drawable.ic_subscription)
            .setContentTitle("🎉 Abonnement Premium Activé !")
            .setContentText("$partnerName a partagé son abonnement avec vous. Vous avez maintenant accès à toutes les fonctionnalités premium !")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    fun stopAllListeners() {
        userListener?.remove()
        partnerListener?.remove()
        userListener = null
        partnerListener = null
    }
}
```

### 8.5 BillingManager Android (Google Play)

```kotlin
@Singleton
class BillingManager @Inject constructor(
    private val context: Context,
    private val appStateRepository: AppStateRepository,
    private val functions: FirebaseFunctions
) : PurchasesUpdatedListener, BillingClientStateListener {

    private lateinit var billingClient: BillingClient
    private val subscriptionPlans = mutableListOf<SubscriptionPlan>()

    companion object {
        private const val WEEKLY_SUBSCRIPTION_ID = "com.lyes.love2love.subscription.weekly"
        private const val MONTHLY_SUBSCRIPTION_ID = "com.lyes.love2love.subscription.monthly"
    }

    init {
        initializeBillingClient()
    }

    private fun initializeBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d("BillingManager", "Connexion Google Play réussie")
            loadAvailableProducts()
        } else {
            Log.e("BillingManager", "Erreur connexion Google Play: ${billingResult.debugMessage}")
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w("BillingManager", "Service Google Play déconnecté")
    }

    private fun loadAvailableProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(WEEKLY_SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(MONTHLY_SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                subscriptionPlans.clear()
                productDetailsList.forEach { productDetails ->
                    val plan = SubscriptionPlan.fromProductDetails(productDetails)
                    subscriptionPlans.add(plan)
                }
                Log.d("BillingManager", "Produits chargés: ${subscriptionPlans.size}")
            } else {
                Log.e("BillingManager", "Erreur chargement produits: ${billingResult.debugMessage}")
            }
        }
    }

    suspend fun getAvailableSubscriptions(): List<SubscriptionPlan> {
        return subscriptionPlans
    }

    suspend fun purchaseSubscription(plan: SubscriptionPlan): PurchaseResult {
        return withContext(Dispatchers.IO) {
            try {
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(plan.productDetails)
                        .build()
                )

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                val billingResult = billingClient.launchBillingFlow(
                    context as Activity,
                    billingFlowParams
                )

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    PurchaseResult.success()
                } else {
                    PurchaseResult.failure("Erreur lors de l'achat: ${billingResult.debugMessage}")
                }

            } catch (e: Exception) {
                PurchaseResult.failure("Erreur achat: ${e.message}")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else {
            Log.e("BillingManager", "Erreur achat: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        // Acknowledger l'achat
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "Achat acknowledé avec succès")
                } else {
                    Log.e("BillingManager", "Erreur acknowledge: ${billingResult.debugMessage}")
                }
            }
        }

        // Valider l'achat côté serveur
        validatePurchaseWithServer(purchase)
    }

    private fun validatePurchaseWithServer(purchase: Purchase) {
        val data = hashMapOf(
            "purchaseToken" to purchase.purchaseToken,
            "productId" to purchase.products.firstOrNull(),
            "purchaseTime" to purchase.purchaseTime,
            "orderId" to purchase.orderId
        )

        functions.getHttpsCallable("validateGooglePlayPurchase")
            .call(data)
            .addOnSuccessListener { result ->
                val resultData = result.data as? Map<String, Any>
                val success = resultData?.get("success") as? Boolean ?: false

                if (success) {
                    Log.d("BillingManager", "Achat validé côté serveur")
                    // Mettre à jour l'état local
                    appStateRepository.updateSubscriptionStatus(true)
                } else {
                    Log.e("BillingManager", "Validation serveur échouée")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("BillingManager", "Erreur validation serveur: ${exception.message}")
            }
    }
}

data class SubscriptionPlan(
    val productId: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val subscriptionPeriod: String,
    val isPopular: Boolean,
    val productDetails: ProductDetails
) {
    companion object {
        fun fromProductDetails(productDetails: ProductDetails): SubscriptionPlan {
            val pricingPhase = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()

            return SubscriptionPlan(
                productId = productDetails.productId,
                priceAmountMicros = pricingPhase?.priceAmountMicros ?: 0L,
                priceCurrencyCode = pricingPhase?.priceCurrencyCode ?: "EUR",
                subscriptionPeriod = pricingPhase?.billingPeriod ?: "P1M",
                isPopular = productDetails.productId.contains("monthly"),
                productDetails = productDetails
            )
        }
    }
}

sealed class PurchaseResult {
    object Success : PurchaseResult()
    data class Failure(val errorMessage: String) : PurchaseResult()

    val isSuccess: Boolean get() = this is Success
    val errorMessage: String? get() = (this as? Failure)?.errorMessage

    companion object {
        fun success() = Success
        fun failure(message: String) = Failure(message)
    }
}
```

### 8.6 Firebase Cloud Functions Android Support

#### validateGooglePlayPurchase() - Validation Google Play

```javascript
exports.validateGooglePlayPurchase = functions.https.onCall(
  async (data, context) => {
    console.log("🔥 validateGooglePlayPurchase: Début validation Google Play");

    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const currentUserId = context.auth.uid;
    const { purchaseToken, productId, purchaseTime, orderId } = data;

    if (!purchaseToken || !productId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Token d'achat et ID produit requis"
      );
    }

    try {
      // Valider avec Google Play Developer API
      const { google } = require("googleapis");
      const androidPublisher = google.androidpublisher("v3");

      // Authentification avec service account
      const auth = new google.auth.GoogleAuth({
        keyFile: "path/to/google-play-service-account.json",
        scopes: ["https://www.googleapis.com/auth/androidpublisher"],
      });

      const authClient = await auth.getClient();
      google.options({ auth: authClient });

      // Validation de l'abonnement Google Play
      const result = await androidPublisher.purchases.subscriptions.get({
        packageName: "com.lyes.love2loveapp",
        subscriptionId: productId,
        token: purchaseToken,
      });

      const subscription = result.data;
      const now = Date.now();
      const expiryTime = parseInt(subscription.expiryTimeMillis);

      // Vérifier si l'abonnement est actif
      if (expiryTime > now && subscription.paymentState === 1) {
        // Abonnement valide - Mettre à jour Firestore
        const subscriptionData = {
          isSubscribed: true,
          subscriptionType: "direct",
          subscriptionPlatform: "google_play",
          purchaseDate: new Date(parseInt(subscription.startTimeMillis)),
          expiresDate: new Date(expiryTime),
          orderId: orderId,
          purchaseToken: purchaseToken,
          productId: productId,
          lastValidated: admin.firestore.FieldValue.serverTimestamp(),
        };

        await admin.firestore().collection("users").doc(currentUserId).update({
          isSubscribed: true,
          subscriptionType: "direct",
          subscriptionDetails: subscriptionData,
        });

        console.log(
          "✅ validateGooglePlayPurchase: Abonnement Google Play validé"
        );

        // Déclencher partage automatique si partenaire connecté
        const userDoc = await admin
          .firestore()
          .collection("users")
          .doc(currentUserId)
          .get();
        const partnerId = userDoc.data()?.partnerId;

        if (partnerId) {
          await triggerPartnerSubscriptionSharing(currentUserId, partnerId);
        }

        return { success: true, subscriptionActive: true };
      } else {
        console.log(
          "❌ validateGooglePlayPurchase: Abonnement Google Play expiré ou invalide"
        );
        return { success: false, reason: "subscription_expired" };
      }
    } catch (error) {
      console.error("❌ validateGooglePlayPurchase: Erreur validation:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);

// Helper pour déclencher partage automatique
async function triggerPartnerSubscriptionSharing(fromUserId, toUserId) {
  try {
    await admin.firestore().collection("users").doc(toUserId).update({
      isSubscribed: true,
      subscriptionType: "shared_from_partner",
      subscriptionSharedFrom: fromUserId,
      subscriptionSharedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Logger pour audit
    await admin.firestore().collection("subscription_sharing_logs").add({
      fromUserId: fromUserId,
      toUserId: toUserId,
      sharedAt: admin.firestore.FieldValue.serverTimestamp(),
      subscriptionType: "inherited",
      triggerSource: "google_play_validation",
      platform: "android",
    });

    console.log("✅ Partage automatique Google Play → Partenaire réussi");
  } catch (error) {
    console.error("❌ Erreur partage automatique:", error);
  }
}
```

---

## 📋 Conclusion

Le système de paywall et partage d'abonnements de CoupleApp iOS présente une architecture sophistiquée et une logique métier avancée :

### 🎯 **Points Forts du Système :**

- **Paywall intelligent** : FreemiumManager avec logique contextuelle (catégories, questions, jour)
- **Partage automatique** : Détection et synchronisation temps réel entre partenaires
- **Webhooks Apple intégrés** : Synchronisation automatique des états d'abonnement
- **Interface premium** : SubscriptionView + paywalls spécialisés
- **Analytics complets** : Tracking de chaque interaction freemium

### 🔧 **Composants Techniques iOS :**

- `FreemiumManager` - Logique centrale freemium/premium
- `SubscriptionView` - Interface paywall principale
- `PartnerSubscriptionSyncService` - Synchronisation abonnements
- `PartnerSubscriptionNotificationService` - Notifications partage
- Cloud Functions Firebase - Backend sécurisé et automatisé

### 🤝 **Système de Partage Sophistiqué :**

- **Détection automatique** : Un partenaire achète → L'autre hérite automatiquement
- **Types d'abonnements** : `"direct"` (payant) vs `"shared_from_partner"` (hérité)
- **Synchronisation temps réel** : Listeners Firestore + Cloud Functions
- **Conformité Apple** : Logs d'audit + respect des guidelines de partage familial

### 🔥 **Intégration Firebase Avancée :**

- Webhooks Apple temps réel (INITIAL_BUY, EXPIRED, CANCELLED)
- Cloud Function `syncPartnerSubscriptions()` avec logique complexe
- Validation côté serveur des reçus Apple
- Persistance sécurisée des états d'abonnement

### 🤖 **Adaptation Android Complète :**

- **FreemiumManager Kotlin** avec logique équivalente
- **BillingManager** pour Google Play Billing API
- **PartnerSubscriptionSyncService** Android avec Firestore listeners
- **SubscriptionActivity** Jetpack Compose moderne
- **Cloud Functions support** pour validation Google Play

### ⚡ **Fonctionnalités Avancées :**

- **Héritage d'onboarding** : Connexion partenaire premium → Skip paywall
- **Paywalls contextuels** : Questions quotidiennes, journal, catégories
- **Notifications intelligentes** : "Votre partenaire a partagé son abonnement"
- **Analytics granulaires** : Tracking de chaque tentative d'accès bloquée

### 📊 **Logique Freemium Détaillée :**

| Contenu                | Gratuit         | Premium     | Partage |
| ---------------------- | --------------- | ----------- | ------- |
| Catégories             | 1 ("En couple") | 8 complètes | ✅ Auto |
| Questions              | 64 max          | Illimitées  | ✅ Auto |
| Questions quotidiennes | 3 jours         | Illimitées  | ✅ Auto |
| Journal                | 5 entrées       | Illimité    | ✅ Auto |

### ⏱️ **Estimation Développement Android :**

- **Phase 1** : FreemiumManager + BillingManager (3-4 semaines)
- **Phase 2** : SubscriptionActivity + Paywall UI (2-3 semaines)
- **Phase 3** : Services partage + notifications (2-3 semaines)
- **Phase 4** : Cloud Functions Google Play + tests (2-3 semaines)

**Total estimé : 9-13 semaines** pour une réplication complète du système iOS vers Android.

Ce système représente un **avantage concurrentiel majeur** avec son partage d'abonnements automatique entre partenaires, créant un **taux de conversion élevé** et une **rétention optimale** grâce à l'effet de réseau du couple connecté.

L'architecture est **prête pour le scale** avec une logique backend robuste, des analytics détaillées, et une conformité complète aux stores Apple/Google.
