# Rapport : Système Questions du Jour - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille l'architecture complète du système "Questions du Jour" dans l'application iOS CoupleApp, incluant la connexion partenaire obligatoire, le système freemium (3 jours gratuits), la messagerie instantanée, les notifications push, l'intégration Firebase, et les recommandations pour l'adaptation Android.

---

## 🏗️ Architecture Générale du Système

```
┌─────────────────────────────────────────────────────────────────┐
│                    SYSTÈME QUESTIONS DU JOUR                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  FLUX D'ONBOARDING ET CONNEXION                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │OnboardingView│  │PartnerCodeStep│  │QuestionsIntro│          │
│  │- 8+ étapes   │  │- Code gen/saisie│  │- Présentation│        │
│  │- Freemium    │  │- Connexion req│  │- Permissions │        │
│  │- Premium skip│  │- Subscription │  │- Notifications│        │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  COUCHE CLIENT iOS                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │DailyQuestionService│  │DQFlowView │  │DQMainView    │         │
│  │- Generate Q  │  │- Route Logic │  │- Chat UI     │         │
│  │- Firebase Sync│  │- Freemium   │  │- MessageKit  │         │
│  │- Real-time   │  │- Partner Req │  │- Notifications│        │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  COUCHE FIREBASE BACKEND                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Cloud Functions │  │   Firestore  │  │ Notifications │         │
│  │- generateDaily │  │- dailyQuestions│  │- FCM Push    │         │
│  │- partnerSync  │  │- responses   │  │- Badge Mgmt  │         │
│  │- timezone mgmt│  │- settings    │  │- Real-time   │         │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

FLUX COMPLET:
1. Utilisateur ouvre Questions → DailyQuestionFlowView
2. Route Logic: Partner connecté ? Intro vue ? Premium ? → Route
3. Si OK → Generate today's question → Firebase sync
4. Chat interface → MessageKit → Real-time responses
5. Partner notifications → Push → Badge management
```

---

## 🚀 1. Flux d'Onboarding - Connexion Partenaire Obligatoire

### 1.1 Architecture OnboardingViewModel

**Localisation :** `ViewModels/OnboardingViewModel.swift:6-25`

```swift
class OnboardingViewModel: ObservableObject {
    enum OnboardingStep: CaseIterable {
        case relationshipGoals
        case relationshipImprovement
        case relationshipDate
        case communicationEvaluation
        case discoveryTime
        case listening
        case confidence
        case complicity
        case authentication
        case displayName
        case profilePhoto
        case completion
        case loading
        // 🔑 ÉTAPES QUESTIONS DU JOUR
        case partnerCode           // Connexion partenaire OBLIGATOIRE
        case questionsIntro        // Présentation Questions du Jour
        case categoriesPreview     // Aperçu des catégories
        case subscription          // Paywall (sauf si partenaire premium)
    }
}
```

### 1.2 Logique de Route Conditionnelle

**Localisation :** `Models/DailyContentRoute.swift:55-99`

```swift
struct DailyContentRouteCalculator {
    static func calculateRoute(
        for contentType: ContentType,
        hasConnectedPartner: Bool,
        hasSeenIntro: Bool,
        shouldShowPaywall: Bool,
        paywallDay: Int,
        serviceHasError: Bool,
        serviceErrorMessage: String?,
        serviceIsLoading: Bool
    ) -> DailyContentRoute {

        // 🔑 1. CONNEXION PARTENAIRE D'ABORD (OBLIGATOIRE)
        if !hasConnectedPartner {
            return .intro(showConnect: true)
        }

        // 🔑 2. INTRO AVANT TOUT LOADING/CONTENU
        if !hasSeenIntro {
            return .intro(showConnect: false)
        }

        // 🔑 3. ÉTATS TECHNIQUES (erreurs avant loading)
        if serviceHasError {
            let errorMessage = serviceErrorMessage ?? "Une erreur est survenue"
            return .error(errorMessage)
        }

        // 🔑 4. VÉRIFIER PAYWALL FREEMIUM (3 JOURS GRATUITS)
        if shouldShowPaywall {
            return .paywall(day: paywallDay)
        }

        // 🔑 5. ÉTAT PAR DÉFAUT - VUE PRINCIPALE
        return .main
    }
}
```

### 1.3 DailyQuestionFlowView - Router Principal

**Localisation :** `Views/DailyQuestion/DailyQuestionFlowView.swift:7-43`

```swift
struct DailyQuestionFlowView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyQuestionService = DailyQuestionService.shared

    var body: some View {
        Group {
            switch currentRoute {
            case .intro(let showConnect):
                DailyQuestionIntroView(showConnectButton: showConnect)
                    .environmentObject(appState)

            case .paywall(let day):
                DailyQuestionPaywallView(questionDay: day)
                    .environmentObject(appState)

            case .main:
                DailyQuestionMainView()
                    .environmentObject(appState)

            case .error(let message):
                DailyQuestionErrorView(message: message, onRetry: { configureServiceIfNeeded() })
                    .environmentObject(appState)

            case .loading:
                DailyQuestionMainView()  // Gestion loading intégrée
                    .environmentObject(appState)
            }
        }
        .onAppear {
            configureServiceIfNeeded()
        }
    }
}
```

---

## 💰 2. Système Freemium - 3 Jours Gratuits

### 2.1 Logique FreemiumManager pour Questions Quotidiennes

**Localisation :** `ViewModels/FreemiumManager.swift:357-408`

```swift
extension FreemiumManager {
    // Configuration freemium questions du jour
    private var freeDailyQuestionDays: Int { 3 } // 🔑 3 PREMIERS JOURS GRATUITS

    /// Vérifie si l'utilisateur peut accéder à la question du jour actuel
    func canAccessDailyQuestion(for questionDay: Int) -> Bool {
        // Si l'utilisateur est abonné, accès illimité
        if appState?.currentUser?.isSubscribed ?? false {
            return true
        }

        print("📅 FreemiumManager: Vérification accès jour \(questionDay)")

        // 🔑 LOGIQUE FREEMIUM : Bloquer après le jour 3
        return questionDay <= freeDailyQuestionDays
    }

    /// Gère l'accès aux questions du jour avec vérification du jour actuel
    func handleDailyQuestionAccess(currentQuestionDay: Int, onSuccess: @escaping () -> Void) {
        print("📅 FreemiumManager: Vérification accès jour \(currentQuestionDay)")

        let isSubscribed = appState?.currentUser?.isSubscribed ?? false

        if isSubscribed {
            print("📅 FreemiumManager: Utilisateur premium - Accès autorisé")
            markDailyQuestionUsage(day: currentQuestionDay)
            onSuccess()
            return
        }

        // 🔑 VÉRIFICATION FREEMIUM
        if currentQuestionDay <= freeDailyQuestionDays {
            print("📅 FreemiumManager: Jour \(currentQuestionDay)/\(freeDailyQuestionDays) - Accès gratuit autorisé")
            markDailyQuestionUsage(day: currentQuestionDay)
            onSuccess()
        } else {
            print("📅 FreemiumManager: Jour \(currentQuestionDay) > limite (\(freeDailyQuestionDays)) - Affichage paywall")
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

### 2.2 Calcul du Jour Actuel de Question

**Localisation :** `Views/DailyQuestion/DailyQuestionFlowView.swift:72-90`

```swift
private var currentQuestionDay: Int {
    if let settings = dailyQuestionService.currentSettings {
        return calculateExpectedDay(from: settings)
    }
    return 1 // Défaut
}

private var shouldShowPaywall: Bool {
    let isSubscribed = appState.currentUser?.isSubscribed ?? false
    if isSubscribed {
        return false // Premium = pas de paywall
    }

    // Utiliser FreemiumManager pour vérifier l'accès
    guard let freemiumManager = appState.freemiumManager else { return false }
    return !freemiumManager.canAccessDailyQuestion(for: currentQuestionDay)
}

private func calculateExpectedDay(from settings: DailyQuestionSettings) -> Int {
    // Utiliser UTC pour éviter les problèmes de timezone
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!

    let startOfDay = calendar.startOfDay(for: settings.startDate)
    let startOfToday = calendar.startOfDay(for: Date())

    let daysSinceStart = calendar.dateComponents([.day], from: startOfDay, to: startOfToday).day ?? 0
    return daysSinceStart + 1
}
```

---

## 🔥 3. Firebase Backend - Génération et Synchronisation

### 3.1 Cloud Function generateDailyQuestion()

**Localisation :** `firebase/functions/index.js:3821-4000`

```javascript
exports.generateDailyQuestion = functions.https.onCall(
  async (data, context) => {
    try {
      // 🔑 VÉRIFICATION AUTHENTIFICATION
      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "Utilisateur non authentifié"
        );
      }

      const { coupleId, userId, questionDay, timezone } = data;

      if (!coupleId || !userId || !questionDay) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "coupleId, userId et questionDay requis"
        );
      }

      console.log(
        `⚙️ generateDailyQuestion: coupleId=${coupleId}, questionDay=${questionDay}`
      );

      const today = new Date();
      const todayString = today.toISOString().split("T")[0];

      // 🔑 NETTOYAGE AUTOMATIQUE - Supprimer question d'hier
      const yesterday = new Date(today);
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayString = yesterday.toISOString().split("T")[0];

      try {
        const yesterdayQuestionQuery = await admin
          .firestore()
          .collection("dailyQuestions")
          .where("coupleId", "==", coupleId)
          .where("scheduledDate", "==", yesterdayString)
          .get();

        if (!yesterdayQuestionQuery.empty) {
          const yesterdayDoc = yesterdayQuestionQuery.docs[0];
          await yesterdayDoc.ref.delete();
          console.log(`🗑️ Question d'hier supprimée: ${yesterdayString}`);
        }
      } catch (cleanupError) {
        console.warn("⚠️ Erreur nettoyage question d'hier:", cleanupError);
      }

      // 🔑 VÉRIFIER SI QUESTION EXISTE DÉJÀ (IDEMPOTENCE)
      const existingQuestionQuery = await admin
        .firestore()
        .collection("dailyQuestions")
        .where("coupleId", "==", coupleId)
        .where("scheduledDate", "==", todayString)
        .get();

      if (!existingQuestionQuery.empty) {
        const existingDoc = existingQuestionQuery.docs[0];
        const existingData = existingDoc.data();

        return {
          success: true,
          message: "Question déjà existante pour aujourd'hui",
          existingQuestion: {
            id: existingDoc.id,
            questionKey: existingData.questionKey,
            questionDay: existingData.questionDay,
          },
        };
      }

      // 🔑 GÉNÉRER NOUVELLE QUESTION
      const questionKey = generateQuestionKey(questionDay);

      const newQuestion = {
        id: `${coupleId}_${todayString}`,
        coupleId: coupleId,
        questionKey: questionKey,
        questionDay: questionDay,
        scheduledDate: todayString,
        scheduledDateTime: admin.firestore.Timestamp.fromDate(today),
        responses: {},
        status: "pending",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        timezone: timezone || "Europe/Paris",
      };

      // 🔑 SAUVEGARDER DANS FIRESTORE
      await admin
        .firestore()
        .collection("dailyQuestions")
        .doc(newQuestion.id)
        .set(newQuestion);

      // 🔑 METTRE À JOUR SETTINGS COUPLE
      await admin
        .firestore()
        .collection("dailyQuestionSettings")
        .doc(coupleId)
        .update({
          currentDay: questionDay,
          lastGeneratedDate: todayString,
          lastGeneratedDateTime: admin.firestore.Timestamp.fromDate(today),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

      console.log(
        `✅ Question générée: ${questionKey} pour couple ${coupleId} jour ${questionDay}`
      );

      return {
        success: true,
        question: newQuestion,
        message: `Question du jour ${questionDay} générée avec succès`,
      };
    } catch (error) {
      console.error("❌ generateDailyQuestion: Erreur:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);
```

### 3.2 Génération Clé de Question

**Localisation :** `firebase/functions/index.js:3542-3549`

```javascript
function generateQuestionKey(questionDay) {
  // 🔑 FORMAT: daily_question_1, daily_question_2, etc.
  return `daily_question_${questionDay}`;
}

function getTotalQuestionsCount() {
  // 🔑 NOMBRE TOTAL DE QUESTIONS DISPONIBLES
  return 300; // Ajustable selon le contenu
}
```

### 3.3 Gestion des Settings de Couple

**Localisation :** `firebase/functions/index.js:3640-3743`

```javascript
async function getOrCreateDailyQuestionSettings(
  coupleId,
  timezone = "Europe/Paris"
) {
  console.log(`🔧 getOrCreateDailyQuestionSettings: coupleId=${coupleId}`);

  try {
    const settingsRef = admin
      .firestore()
      .collection("dailyQuestionSettings")
      .doc(coupleId);
    const settingsDoc = await settingsRef.get();

    if (settingsDoc.exists) {
      console.log("📋 Settings existants trouvés");
      return settingsDoc.data();
    }

    // 🔑 CRÉER NOUVEAUX SETTINGS
    const now = new Date();
    const startDate = new Date(now);
    startDate.setUTCHours(0, 0, 0, 0); // Début de journée UTC

    const newSettings = {
      coupleId,
      startDate: admin.firestore.Timestamp.fromDate(startDate),
      currentDay: 1,
      timezone: timezone,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      isActive: true,
    };

    await settingsRef.set(newSettings);
    console.log(`✅ Nouveaux settings créés pour couple ${coupleId}`);

    return {
      ...newSettings,
      startDate: startDate, // Retourner Date object pour usage
      createdAt: now,
      updatedAt: now,
    };
  } catch (error) {
    console.error(`❌ Erreur getOrCreateDailyQuestionSettings:`, error);
    throw error;
  }
}
```

---

## 💬 4. Messagerie Instantanée - Chat Interface

### 4.1 DailyQuestionMainView - Interface Chat Intégrée

**Localisation :** `Views/DailyQuestion/DailyQuestionMainView.swift:443-647`

```swift
private func chatSection(for question: DailyQuestion, proxy: ScrollViewProxy) -> some View {
    VStack(spacing: 0) {
        if stableMessages.isEmpty {
            // Message d'encouragement
            VStack(spacing: 8) {
                Text(NSLocalizedString("daily_question_start_conversation", tableName: "DailyQuestions", comment: ""))
                    .font(.system(size: 16))
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
            }
            .padding(.vertical, 20)
        } else {
            // 🔑 MESSAGES EXISTANTS AVEC ÉTAT STABLE
            ForEach(Array(stableMessages.enumerated()), id: \.element.id) { index, response in
                let isPreviousSameSender = index > 0 && stableMessages[index - 1].userId == response.userId

                ChatMessageView(
                    response: response,
                    isCurrentUser: response.userId == currentUserId,
                    partnerName: response.userName,
                    isLastMessage: response.id == stableMessages.last?.id,
                    isPreviousSameSender: isPreviousSameSender
                )
                .id("\(response.id)-stable")
            }

            // Spacer invisible pour scroll automatique
            Color.clear.frame(height: 1)
                .id("bottom")
                .onAppear {
                    // Auto-scroll vers le bas lors de nouveaux messages
                    withAnimation(.easeOut(duration: 0.3)) {
                        proxy.scrollTo("bottom", anchor: .bottom)
                    }
                }
        }
    }
}

// 🔑 SOUMISSION RÉPONSE AVEC UX OPTIMISÉE
private func submitResponse(question: DailyQuestion) {
    guard !responseText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

    isSubmittingResponse = true

    // 🔑 CRÉER RÉPONSE TEMPORAIRE POUR AFFICHAGE IMMÉDIAT
    let tempResponse = QuestionResponse(
        userId: currentUserId ?? "",
        userName: appState.currentUser?.name ?? "Vous",
        text: responseText.trimmingCharacters(in: .whitespacesAndNewlines),
        status: .answered
    )

    // 🔑 AJOUTER IMMÉDIATEMENT À L'INTERFACE
    withAnimation(.easeInOut(duration: 0.2)) {
        stableMessages.append(tempResponse)
    }

    // Nettoyer le champ et fermer le clavier
    let textToSubmit = responseText.trimmingCharacters(in: .whitespacesAndNewlines)
    responseText = ""
    isTextFieldFocused = false

    // 📊 Analytics: Message envoyé
    Analytics.logEvent("message_envoye", parameters: [
        "type": "texte",
        "source": "daily_question_main"
    ])

    Task {
        let success = await dailyQuestionService.submitResponse(textToSubmit)

        await MainActor.run {
            isSubmittingResponse = false

            if !success {
                // En cas d'erreur, supprimer le message temporaire
                stableMessages.removeAll { $0.id == tempResponse.id }
                responseText = textToSubmit // Remettre le texte
            }
        }
    }
}
```

### 4.2 MessageKit Integration (Alternative)

**Localisation :** `Views/DailyQuestion/DailyQuestionMessageKitView.swift:1-355`

```swift
struct DailyQuestionMessageKitView: UIViewControllerRepresentable {
    let question: DailyQuestion
    @ObservedObject private var dailyQuestionService = DailyQuestionService.shared
    @EnvironmentObject var appState: AppState

    func makeUIViewController(context: Context) -> DailyQuestionChatViewController {
        let chatVC = DailyQuestionChatViewController()
        chatVC.question = question
        chatVC.dailyQuestionService = dailyQuestionService
        chatVC.appState = appState
        return chatVC
    }
}

class DailyQuestionChatViewController: MessagesViewController {
    var question: DailyQuestion?
    var dailyQuestionService: DailyQuestionService?
    var appState: AppState?

    private var messages: [DailyQuestionMessage] = []
    private var currentUserSender: MessageSender?

    override func viewDidLoad() {
        super.viewDidLoad()
        setupCurrentUser()
        configureMessageKit()
        setupInputBar()
        updateMessages()
    }

    // 🔑 CONFIGURATION MESSAGEKIT
    private func configureMessageKit() {
        messagesCollectionView.messagesDataSource = self
        messagesCollectionView.messagesLayoutDelegate = self
        messagesCollectionView.messagesDisplayDelegate = self
        messageInputBar.delegate = self

        // Style Twitter-like sans avatars
        messagesCollectionView.messageCellDelegate = self

        // Configuration couleurs
        if let layout = messagesCollectionView.collectionViewLayout as? MessagesCollectionViewFlowLayout {
            layout.setMessageIncomingAvatarSize(.zero)
            layout.setMessageOutgoingAvatarSize(.zero)
        }
    }
}

// MARK: - InputBarAccessoryViewDelegate

extension DailyQuestionChatViewController: InputBarAccessoryViewDelegate {
    func inputBar(_ inputBar: InputBarAccessoryView, didPressSendButtonWith text: String) {
        guard let currentUserSender = currentUserSender,
              let _ = question,
              let dailyQuestionService = dailyQuestionService else { return }

        // 🔑 CRÉER MESSAGE TEMPORAIRE POUR AFFICHAGE IMMÉDIAT
        let tempMessage = DailyQuestionMessage(
            tempId: UUID().uuidString,
            text: text,
            sender: currentUserSender
        )

        // Ajouter immédiatement à l'interface
        insertMessage(tempMessage)

        // Vider la barre de saisie
        inputBar.inputTextView.text = ""
        inputBar.invalidatePlugins()

        // 📊 Analytics: Message envoyé
        Analytics.logEvent("message_envoye", parameters: [
            "type": "texte",
            "source": "daily_question_messagekit"
        ])

        // 🔑 ENVOYER À FIREBASE (ASYNCHRONE)
        Task {
            let success = await dailyQuestionService.submitResponse(text)

            await MainActor.run {
                if !success {
                    // Supprimer le message temporaire en cas d'erreur
                    self.removeMessage(tempMessage)
                }
            }
        }
    }
}
```

---

## 🔔 5. Notifications Push - Système Complet

### 5.1 Demande de Permissions Notifications

**Localisation :** `Views/DailyQuestion/DailyQuestionMainView.swift:702-735`

```swift
// MARK: - 🔔 NOTIFICATIONS

/// Vérifie et demande immédiatement les permissions de notifications (une seule fois)
private func requestNotificationIfNeeded() {
    guard let currentUser = appState.currentUser,
          !hasRequestedNotificationsThisSession else {
        return
    }

    let userKey = "notifications_requested_\(currentUser.id)"
    let hasAlreadyRequested = UserDefaults.standard.bool(forKey: userKey)

    if hasAlreadyRequested {
        return
    }

    Task { @MainActor in
        await self.requestNotificationPermissions()
    }
}

/// Demande les permissions de notifications avec la popup native iOS
@MainActor
private func requestNotificationPermissions() async {
    guard let currentUser = appState.currentUser else { return }

    hasRequestedNotificationsThisSession = true

    do {
        let granted = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])

        if granted {
            // 🔑 ENREGISTRER TOKEN FCM
            FCMService.shared.requestTokenAndSave()
        }

        let userKey = "notifications_requested_\(currentUser.id)"
        UserDefaults.standard.set(true, forKey: userKey)

    } catch {
        print("❌ Notifications: Erreur lors de la demande - \(error)")
    }
}
```

### 5.2 Notifications Nouveaux Messages

**Localisation :** `Services/DailyQuestionService.swift:739-785`

```swift
// MARK: - Notifications pour nouveaux messages

private func scheduleNewMessageNotification(for question: DailyQuestion, newResponse: QuestionResponse) async {
    // 🔑 NE NOTIFIER QUE SI LE MESSAGE VIENT DU PARTENAIRE
    guard let currentUserId = Auth.auth().currentUser?.uid,
          newResponse.userId != currentUserId else {
        print("🔔 DailyQuestionService: Message de l'utilisateur actuel – pas de notification")
        return
    }

    let center = UNUserNotificationCenter.current()
    let identifier = "new_message_\(question.id)_\(newResponse.id)"

    // 🔑 SUPPRIMER ANCIENNES NOTIFICATIONS POUR ÉVITER L'ACCUMULATION
    let questionNotificationPrefix = "new_message_\(question.id)_"
    let pendingRequests = await center.pendingNotificationRequests()
    let oldNotificationIds = pendingRequests
        .filter { $0.identifier.hasPrefix(questionNotificationPrefix) && $0.identifier != identifier }
        .map { $0.identifier }

    if !oldNotificationIds.isEmpty {
        center.removePendingNotificationRequests(withIdentifiers: oldNotificationIds)
        print("🗑️ DailyQuestionService: \(oldNotificationIds.count) anciennes notifications supprimées")
    }

    let content = UNMutableNotificationContent()
    // 🔑 FORMAT: Nom partenaire en titre, message complet en body
    content.title = newResponse.userName
    content.body = newResponse.text
    content.sound = .default
    content.badge = 1

    // 🔑 NOTIFICATION IMMÉDIATE POUR NOUVEAU MESSAGE
    let request = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)

    print("🔔 DailyQuestionService: Programmation notification nouveau message:")
    print("   - ID: \(identifier)")
    print("   - De: \(newResponse.userName)")
    print("   - Preview: \(String(newResponse.text.prefix(30)))...")

    do {
        try await center.add(request)
        print("✅ DailyQuestionService: Notification nouveau message programmée")
    } catch {
        print("❌ DailyQuestionService: Erreur notification nouveau message - \(error)")
    }
}

/// Nettoie toutes les notifications en attente et remet le badge à 0
func clearAllNotificationsAndBadge() {
    print("🧹 DailyQuestionService: Nettoyage notifications et badge")
    BadgeManager.clearAllNotificationsAndBadge()
}

/// Nettoie les notifications spécifiques à une question
func clearNotificationsForQuestion(_ questionId: String) {
    let questionNotificationPrefix = "new_message_\(questionId)_"

    Task {
        let center = UNUserNotificationCenter.current()
        let pendingRequests = await center.pendingNotificationRequests()
        let notificationIds = pendingRequests
            .filter { $0.identifier.hasPrefix(questionNotificationPrefix) }
            .map { $0.identifier }

        if !notificationIds.isEmpty {
            center.removePendingNotificationRequests(withIdentifiers: notificationIds)
            print("🗑️ DailyQuestionService: \(notificationIds.count) notifications supprimées pour question \(questionId)")
        }
    }
}
```

---

## 🌍 6. Localisation - Clés XCStrings

### 6.1 Structure DailyQuestions.xcstrings

**Localisation :** `DailyQuestions.xcstrings`

```json
{
  "sourceLanguage": "fr",
  "strings": {
    // 🔑 CLÉS PRINCIPALES INTERFACE
    "daily_question_title": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": { "state": "translated", "value": "Question du jour" }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "Question of the Day"
          }
        },
        "de": {
          "stringUnit": { "state": "translated", "value": "Frage des Tages" }
        },
        "es": {
          "stringUnit": { "state": "translated", "value": "Pregunta del día" }
        },
        "it": {
          "stringUnit": { "state": "translated", "value": "Domanda del giorno" }
        },
        "pt-BR": {
          "stringUnit": { "state": "translated", "value": "Pergunta do Dia" }
        },
        "nl": {
          "stringUnit": { "state": "translated", "value": "Vraag van de dag" }
        }
      }
    },

    // 🔑 CLÉS NOTIFICATIONS
    "daily_question_notification_title": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "💕 Question du jour"
          }
        },
        "en": {
          "stringUnit": { "state": "translated", "value": "💕 Daily Question" }
        },
        "de": {
          "stringUnit": { "state": "translated", "value": "💕 Tagesfrage" }
        },
        "es": {
          "stringUnit": { "state": "translated", "value": "💕 Pregunta diaria" }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "💕 Domanda del giorno"
          }
        },
        "pt-BR": {
          "stringUnit": { "state": "translated", "value": "💕 Pergunta do Dia" }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "💕 Dagelijkse vraag"
          }
        }
      }
    },

    // 🔑 CLÉS INTERFACE CHAT
    "daily_question_start_conversation": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "Commencez la conversation en répondant à la question ci-dessus"
          }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "Start the conversation by answering the question above"
          }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Beginnen Sie das Gespräch, indem Sie die obige Frage beantworten"
          }
        },
        "es": {
          "stringUnit": {
            "state": "translated",
            "value": "Comienza la conversación respondiendo a la pregunta de arriba"
          }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "Inizia la conversazione rispondendo alla domanda qui sopra"
          }
        },
        "pt-BR": {
          "stringUnit": {
            "state": "translated",
            "value": "Comece a conversa respondendo à pergunta acima"
          }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "Begin het gesprek door de vraag hierboven te beantwoorden"
          }
        }
      }
    },

    "daily_question_type_response": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "Tapez votre réponse..."
          }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "Type your response..."
          }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Geben Sie Ihre Antwort ein..."
          }
        },
        "es": {
          "stringUnit": {
            "state": "translated",
            "value": "Escribe tu respuesta..."
          }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "Digita la tua risposta..."
          }
        },
        "pt-BR": {
          "stringUnit": {
            "state": "translated",
            "value": "Digite sua resposta..."
          }
        },
        "nl": {
          "stringUnit": { "state": "translated", "value": "Typ uw antwoord..." }
        }
      }
    },

    "daily_question_send_button": {
      "extractionState": "manual",
      "localizations": {
        "fr": { "stringUnit": { "state": "translated", "value": "Envoyer" } },
        "en": { "stringUnit": { "state": "translated", "value": "Send" } },
        "de": { "stringUnit": { "state": "translated", "value": "Senden" } },
        "es": { "stringUnit": { "state": "translated", "value": "Enviar" } },
        "it": { "stringUnit": { "state": "translated", "value": "Invia" } },
        "pt-BR": { "stringUnit": { "state": "translated", "value": "Enviar" } },
        "nl": { "stringUnit": { "state": "translated", "value": "Verzenden" } }
      }
    },

    // 🔑 CLÉS ONBOARDING
    "activate_notifications_button": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "Activer les notifications"
          }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "Activate notifications"
          }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Benachrichtigungen aktivieren"
          }
        },
        "es": {
          "stringUnit": {
            "state": "translated",
            "value": "Activar las notificaciones"
          }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "Attiva le notifiche"
          }
        },
        "pt-BR": {
          "stringUnit": {
            "state": "translated",
            "value": "Ativar notificações"
          }
        },
        "nl": {
          "stringUnit": { "state": "translated", "value": "Activeer meldingen" }
        }
      }
    },

    // 🔑 CLÉS QUESTIONS DYNAMIQUES (daily_question_1, daily_question_2, etc.)
    "daily_question_1": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "Qu'est-ce qui t'a fait sourire aujourd'hui ?"
          }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "What made you smile today?"
          }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Was hat dich heute zum Lächeln gebracht?"
          }
        },
        "es": {
          "stringUnit": {
            "state": "translated",
            "value": "¿Qué te hizo sonreír hoy?"
          }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "Cosa ti ha fatto sorridere oggi?"
          }
        },
        "pt-BR": {
          "stringUnit": {
            "state": "translated",
            "value": "O que te fez sorrir hoje?"
          }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "Wat bracht je vandaag aan het glimlachen?"
          }
        }
      }
    },

    "daily_question_2": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "Raconte-moi ton plus beau souvenir avec moi"
          }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "Tell me about your most beautiful memory with me"
          }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Erzähl mir von deiner schönsten Erinnerung mit mir"
          }
        },
        "es": {
          "stringUnit": {
            "state": "translated",
            "value": "Cuéntame tu recuerdo más hermoso conmigo"
          }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "Raccontami il tuo ricordo più bello con me"
          }
        },
        "pt-BR": {
          "stringUnit": {
            "state": "translated",
            "value": "Me conte sua memória mais bonita comigo"
          }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "Vertel me over je mooiste herinnering met mij"
          }
        }
      }
    },

    "daily_question_3": {
      "extractionState": "manual",
      "localizations": {
        "fr": {
          "stringUnit": {
            "state": "translated",
            "value": "Quelle est ta plus grande fierté dans notre relation ?"
          }
        },
        "en": {
          "stringUnit": {
            "state": "translated",
            "value": "What is your greatest pride in our relationship?"
          }
        },
        "de": {
          "stringUnit": {
            "state": "translated",
            "value": "Was ist dein größter Stolz in unserer Beziehung?"
          }
        },
        "es": {
          "stringUnit": {
            "state": "translated",
            "value": "¿Cuál es tu mayor orgullo en nuestra relación?"
          }
        },
        "it": {
          "stringUnit": {
            "state": "translated",
            "value": "Qual è il tuo più grande orgoglio nella nostra relazione?"
          }
        },
        "pt-BR": {
          "stringUnit": {
            "state": "translated",
            "value": "Qual é seu maior orgulho em nosso relacionamento?"
          }
        },
        "nl": {
          "stringUnit": {
            "state": "translated",
            "value": "Wat is je grootste trots in onze relatie?"
          }
        }
      }
    }

    // ... jusqu'à daily_question_300+ selon le contenu disponible
  }
}
```

### 6.2 Usage Dynamique des Clés

**Localisation :** `Models/DailyQuestion.swift:197-199`

```swift
// MARK: - Computed Properties pour compatibilité
var localizedText: String {
    return NSLocalizedString(questionKey, tableName: "DailyQuestions", comment: "")
}
```

**Avec :**

- `questionKey` = `"daily_question_1"`, `"daily_question_2"`, etc.
- `tableName` = `"DailyQuestions"` (référence au fichier DailyQuestions.xcstrings)

---

## 🤖 7. Adaptation Android - Architecture Kotlin/Compose

### 7.1 Structure Android Strings.xml

```xml
<!-- res/values/daily_questions_strings.xml -->
<resources>
    <!-- Interface principale -->
    <string name="daily_question_title">Question du jour</string>
    <string name="daily_question_start_conversation">Commencez la conversation en répondant à la question ci-dessus</string>
    <string name="daily_question_type_response">Tapez votre réponse...</string>
    <string name="daily_question_send_button">Envoyer</string>

    <!-- Notifications -->
    <string name="daily_question_notification_title">💕 Question du jour</string>
    <string name="notification_daily_reminder_title">💕 Question du jour</string>

    <!-- Onboarding -->
    <string name="activate_notifications_button">Activer les notifications</string>

    <!-- Questions dynamiques -->
    <string name="daily_question_1">Qu\'est-ce qui t\'a fait sourire aujourd\'hui ?</string>
    <string name="daily_question_2">Raconte-moi ton plus beau souvenir avec moi</string>
    <string name="daily_question_3">Quelle est ta plus grande fierté dans notre relation ?</string>

    <!-- Localizations multilingues dans res/values-en/, res/values-de/, etc. -->
</resources>

<!-- res/values-en/daily_questions_strings.xml -->
<resources>
    <string name="daily_question_title">Question of the Day</string>
    <string name="daily_question_start_conversation">Start the conversation by answering the question above</string>
    <string name="daily_question_type_response">Type your response...</string>
    <string name="daily_question_send_button">Send</string>
    <string name="daily_question_notification_title">💕 Daily Question</string>
    <string name="activate_notifications_button">Activate notifications</string>
    <string name="daily_question_1">What made you smile today?</string>
    <string name="daily_question_2">Tell me about your most beautiful memory with me</string>
    <string name="daily_question_3">What is your greatest pride in our relationship?</string>
</resources>

<!-- Et ainsi de suite pour res/values-de/, res/values-es/, res/values-it/, etc. -->
```

### 7.2 DailyQuestionRepository Android

```kotlin
@Singleton
class DailyQuestionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService
) {

    companion object {
        private const val TAG = "DailyQuestionRepository"
        private const val COLLECTION_DAILY_QUESTIONS = "dailyQuestions"
        private const val COLLECTION_SETTINGS = "dailyQuestionSettings"
    }

    private val _currentQuestion = MutableStateFlow<DailyQuestion?>(null)
    val currentQuestion: StateFlow<DailyQuestion?> = _currentQuestion

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var questionListener: ListenerRegistration? = null

    // MARK: - Configuration et Setup

    fun initializeForCouple(coupleId: String) {
        Log.d(TAG, "Initialisation pour couple: $coupleId")
        setupRealtimeListener(coupleId)
        generateTodayQuestion(coupleId)
    }

    // MARK: - Real-time Listener

    private fun setupRealtimeListener(coupleId: String) {
        questionListener?.remove()

        Log.d(TAG, "Configuration listener temps réel pour couple: $coupleId")

        // 🔑 LISTENER FIREBASE TEMPS RÉEL
        questionListener = firestore.collection(COLLECTION_DAILY_QUESTIONS)
            .whereEqualTo("coupleId", coupleId)
            .orderBy("scheduledDateTime", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erreur listener questions: ${error.message}")
                    return@addSnapshotListener
                }

                Log.d(TAG, "Mise à jour questions reçue: ${snapshot?.documents?.size} documents")

                val questions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        DailyQuestion.fromFirestore(doc)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur parsing question: ${e.message}")
                        null
                    }
                } ?: emptyList()

                // Trouver la question d'aujourd'hui
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayQuestion = questions.find { it.scheduledDate == today }

                _currentQuestion.value = todayQuestion

                // Écouter les réponses pour la question actuelle
                todayQuestion?.let { question ->
                    setupResponsesListener(question.id)
                }

                Log.d(TAG, "Question d'aujourd'hui mise à jour: ${todayQuestion?.questionKey}")
            }
    }

    // MARK: - Génération de Question

    private fun generateTodayQuestion(coupleId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val currentUser = authRepository.getCurrentUser()
                    ?: throw Exception("Utilisateur non connecté")

                // Calculer le jour actuel
                val questionDay = calculateCurrentQuestionDay(coupleId)

                // 🔑 APPEL CLOUD FUNCTION
                val data = hashMapOf(
                    "coupleId" to coupleId,
                    "userId" to currentUser.uid,
                    "questionDay" to questionDay,
                    "timezone" to TimeZone.getDefault().id
                )

                val result = functions.getHttpsCallable("generateDailyQuestion")
                    .call(data)
                    .await()

                val resultData = result.data as? Map<String, Any>
                val success = resultData?.get("success") as? Boolean ?: false

                if (success) {
                    Log.d(TAG, "Question générée avec succès pour le jour $questionDay")

                    analyticsService.logEvent("daily_question_generated") {
                        param("question_day", questionDay.toLong())
                        param("couple_id_hash", coupleId.hashCode().toLong())
                    }
                } else {
                    val message = resultData?.get("message") as? String ?: "Erreur inconnue"
                    Log.e(TAG, "Erreur génération question: $message")
                }

                _isLoading.value = false

            } catch (e: Exception) {
                Log.e(TAG, "Erreur génération question: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    // MARK: - Soumission Réponse

    suspend fun submitResponse(questionId: String, responseText: String): Result<Unit> {
        return try {
            val currentUser = authRepository.getCurrentUser()
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            val response = QuestionResponse(
                userId = currentUser.uid,
                userName = currentUser.displayName ?: "Utilisateur",
                text = responseText,
                timestamp = com.google.firebase.Timestamp.now(),
                status = ResponseStatus.ANSWERED
            )

            // 🔑 SAUVEGARDER DANS SOUS-COLLECTION
            firestore.collection(COLLECTION_DAILY_QUESTIONS)
                .document(questionId)
                .collection("responses")
                .document(currentUser.uid)
                .set(response.toFirestore())
                .await()

            // 📊 Analytics
            analyticsService.logEvent("daily_question_response_submitted") {
                param("question_id_hash", questionId.hashCode().toLong())
                param("response_length", responseText.length.toLong())
            }

            Log.d(TAG, "Réponse soumise avec succès pour question: $questionId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur soumission réponse: ${e.message}")
            Result.failure(e)
        }
    }

    // MARK: - Calcul Jour Actuel

    private suspend fun calculateCurrentQuestionDay(coupleId: String): Int {
        return try {
            val settingsDoc = firestore.collection(COLLECTION_SETTINGS)
                .document(coupleId)
                .get()
                .await()

            if (settingsDoc.exists()) {
                val startDate = settingsDoc.getTimestamp("startDate")?.toDate() ?: Date()
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

                val startOfStart = calendar.apply {
                    time = startDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                val startOfToday = calendar.apply {
                    time = Date()
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                val diffInDays = ((startOfToday.time - startOfStart.time) / (24 * 60 * 60 * 1000)).toInt()
                diffInDays + 1
            } else {
                1 // Défaut pour nouveau couple
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur calcul jour question: ${e.message}")
            1
        }
    }

    fun cleanup() {
        questionListener?.remove()
        responseListener?.remove()
    }
}
```

### 7.3 Modèles de Données Android

```kotlin
// DailyQuestion.kt
data class DailyQuestion(
    val id: String,
    val coupleId: String,
    val questionKey: String,
    val questionDay: Int,
    val scheduledDate: String,
    val scheduledDateTime: com.google.firebase.Timestamp,
    val status: String = "pending",
    val timezone: String = "Europe/Paris",
    val createdAt: com.google.firebase.Timestamp? = null,
    val updatedAt: com.google.firebase.Timestamp? = null,
    val responses: List<QuestionResponse> = emptyList()
) {

    companion object {
        fun fromFirestore(document: DocumentSnapshot): DailyQuestion? {
            return try {
                val data = document.data ?: return null

                DailyQuestion(
                    id = document.id,
                    coupleId = data["coupleId"] as? String ?: "",
                    questionKey = data["questionKey"] as? String ?: "",
                    questionDay = (data["questionDay"] as? Number)?.toInt() ?: 0,
                    scheduledDate = data["scheduledDate"] as? String ?: "",
                    scheduledDateTime = data["scheduledDateTime"] as? com.google.firebase.Timestamp
                        ?: com.google.firebase.Timestamp.now(),
                    status = data["status"] as? String ?: "pending",
                    timezone = data["timezone"] as? String ?: "Europe/Paris",
                    createdAt = data["createdAt"] as? com.google.firebase.Timestamp,
                    updatedAt = data["updatedAt"] as? com.google.firebase.Timestamp
                )
            } catch (e: Exception) {
                Log.e("DailyQuestion", "Erreur parsing Firestore: ${e.message}")
                null
            }
        }
    }

    // 🔑 LOCALISATION DYNAMIQUE
    fun getLocalizedText(context: Context): String {
        val resourceId = context.resources.getIdentifier(
            questionKey,
            "string",
            context.packageName
        )

        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            // Fallback vers clé brute
            questionKey.replace("_", " ").capitalize()
        }
    }

    fun toFirestore(): Map<String, Any> {
        return mapOf(
            "coupleId" to coupleId,
            "questionKey" to questionKey,
            "questionDay" to questionDay,
            "scheduledDate" to scheduledDate,
            "scheduledDateTime" to scheduledDateTime,
            "status" to status,
            "timezone" to timezone,
            "createdAt" to (createdAt ?: com.google.firebase.Timestamp.now()),
            "updatedAt" to com.google.firebase.Timestamp.now()
        )
    }

    val formattedDate: String
        get() {
            val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            return try {
                val date = dateFormatter.parse(scheduledDate)
                date?.let { formatter.format(it) } ?: scheduledDate
            } catch (e: Exception) {
                scheduledDate
            }
        }
}

// QuestionResponse.kt
data class QuestionResponse(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userName: String,
    val text: String,
    val timestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val status: ResponseStatus = ResponseStatus.ANSWERED
) {

    companion object {
        fun fromFirestore(document: DocumentSnapshot): QuestionResponse? {
            return try {
                val data = document.data ?: return null

                QuestionResponse(
                    id = document.id,
                    userId = data["userId"] as? String ?: "",
                    userName = data["userName"] as? String ?: "",
                    text = data["text"] as? String ?: "",
                    timestamp = data["timestamp"] as? com.google.firebase.Timestamp
                        ?: com.google.firebase.Timestamp.now(),
                    status = ResponseStatus.valueOf(
                        data["status"] as? String ?: "ANSWERED"
                    )
                )
            } catch (e: Exception) {
                Log.e("QuestionResponse", "Erreur parsing: ${e.message}")
                null
            }
        }
    }

    fun toFirestore(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "userName" to userName,
            "text" to text,
            "timestamp" to timestamp,
            "status" to status.name
        )
    }
}

enum class ResponseStatus {
    PENDING, ANSWERED
}
```

### 7.4 Interface Android - DailyQuestionScreen Compose

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQuestionScreen(
    viewModel: DailyQuestionViewModel = hiltViewModel(),
    onNavigateToPartnerConnection: () -> Unit,
    onNavigateToSubscription: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    // 🔑 GESTION DES ROUTES
    when (uiState.currentRoute) {
        is DailyContentRoute.Intro -> {
            DailyQuestionIntroScreen(
                showConnectButton = uiState.currentRoute.showConnect,
                onConnectPartner = onNavigateToPartnerConnection,
                onContinue = { viewModel.markIntroSeen() }
            )
        }

        is DailyContentRoute.Paywall -> {
            DailyQuestionPaywallScreen(
                questionDay = uiState.currentRoute.day,
                onSubscribe = onNavigateToSubscription,
                onDismiss = { /* Handle paywall dismiss */ }
            )
        }

        is DailyContentRoute.Main -> {
            DailyQuestionMainScreen(
                question = uiState.currentQuestion,
                responses = uiState.responses,
                isLoading = uiState.isLoading,
                onSubmitResponse = viewModel::submitResponse,
                onRequestNotificationPermission = viewModel::requestNotificationPermission
            )
        }

        is DailyContentRoute.Error -> {
            DailyQuestionErrorScreen(
                message = uiState.currentRoute.message,
                onRetry = { viewModel.retry() }
            )
        }

        DailyContentRoute.Loading -> {
            DailyQuestionLoadingScreen()
        }
    }
}

@Composable
fun DailyQuestionMainScreen(
    question: DailyQuestion?,
    responses: List<QuestionResponse>,
    isLoading: Boolean,
    onSubmitResponse: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    var responseText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 🔑 AUTO-SCROLL VERS LE BAS LORS NOUVEAUX MESSAGES
    LaunchedEffect(responses.size) {
        if (responses.isNotEmpty()) {
            listState.animateScrollToItem(responses.size)
        }
    }

    // 🔑 DEMANDER PERMISSIONS NOTIFICATIONS
    LaunchedEffect(question) {
        if (question != null) {
            onRequestNotificationPermission()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
    ) {
        // Header avec titre
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.daily_question_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFFF7F7F8)
            )
        )

        if (question != null) {
            // 🔑 CARTE QUESTION
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFF6B9D),
                                    Color(0xFFE63C6B)
                                )
                            )
                        )
                        .padding(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Jour ${question.questionDay}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = question.getLocalizedText(context),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp
                        )

                        Text(
                            text = question.formattedDate,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 🔑 SECTION CHAT
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (responses.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.daily_question_start_conversation),
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                        )
                    }
                } else {
                    itemsIndexed(responses) { index, response ->
                        val isCurrentUser = response.userId == getCurrentUserId()
                        val isPreviousSameSender = index > 0 &&
                            responses[index - 1].userId == response.userId

                        ChatMessageBubble(
                            response = response,
                            isCurrentUser = isCurrentUser,
                            isPreviousSameSender = isPreviousSameSender,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Spacer pour auto-scroll
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 🔑 BARRE DE SAISIE
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = responseText,
                        onValueChange = { responseText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.daily_question_type_response),
                                color = Color.Gray
                            )
                        },
                        shape = RoundedCornerShape(25.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (responseText.isNotBlank()) {
                                    onSubmitResponse(responseText.trim())
                                    responseText = ""
                                    keyboardController?.hide()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    FloatingActionButton(
                        onClick = {
                            if (responseText.isNotBlank()) {
                                onSubmitResponse(responseText.trim())
                                responseText = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFFFF6B9D),
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = stringResource(R.string.daily_question_send_button),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // État de chargement
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFF6B9D),
                        modifier = Modifier.size(48.dp)
                    )

                    Text(
                        text = "Génération de la question du jour...",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    response: QuestionResponse,
    isCurrentUser: Boolean,
    isPreviousSameSender: Boolean,
    modifier: Modifier = Modifier
) {
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isCurrentUser) Color(0xFFFF6B9D) else Color(0xFFE5E5E5)
    val textColor = if (isCurrentUser) Color.White else Color.Black

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        ) {
            // Nom de l'expéditeur (seulement si pas même expéditeur précédent)
            if (!isPreviousSameSender) {
                Text(
                    text = response.userName,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Bulle de message
            Card(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(
                        start = if (isCurrentUser) 40.dp else 0.dp,
                        end = if (isCurrentUser) 0.dp else 40.dp
                    ),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isCurrentUser) 20.dp else 6.dp,
                    bottomEnd = if (isCurrentUser) 6.dp else 20.dp
                ),
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = response.text,
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(response.timestamp.toDate()),
                        fontSize = 11.sp,
                        color = if (isCurrentUser) Color.White.copy(alpha = 0.7f) else Color.Gray,
                        modifier = Modifier.align(if (isCurrentUser) Alignment.End else Alignment.Start)
                    )
                }
            }
        }
    }
}
```

### 7.5 Gestion Freemium Android

```kotlin
@Singleton
class FreemiumManager @Inject constructor(
    private val appStateRepository: AppStateRepository,
    private val analyticsService: AnalyticsService
) {

    companion object {
        private const val FREE_DAILY_QUESTION_DAYS = 3
    }

    fun canAccessDailyQuestion(questionDay: Int, currentUser: AppUser?): Boolean {
        // Si l'utilisateur est abonné, accès illimité
        if (currentUser?.isSubscribed == true) {
            return true
        }

        Log.d("FreemiumManager", "Vérification accès jour $questionDay")

        // 🔑 LOGIQUE FREEMIUM : Bloquer après le jour 3
        return questionDay <= FREE_DAILY_QUESTION_DAYS
    }

    suspend fun handleDailyQuestionAccess(
        questionDay: Int,
        currentUser: AppUser?,
        onSuccess: suspend () -> Unit,
        onPaywallRequired: suspend () -> Unit
    ) {
        Log.d("FreemiumManager", "Vérification accès jour $questionDay")

        if (currentUser?.isSubscribed == true) {
            Log.d("FreemiumManager", "Utilisateur premium - Accès autorisé")
            markDailyQuestionUsage(questionDay)
            onSuccess()
            return
        }

        // 🔑 VÉRIFICATION FREEMIUM
        if (questionDay <= FREE_DAILY_QUESTION_DAYS) {
            Log.d("FreemiumManager", "Jour $questionDay/$FREE_DAILY_QUESTION_DAYS - Accès gratuit autorisé")
            markDailyQuestionUsage(questionDay)
            onSuccess()
        } else {
            Log.d("FreemiumManager", "Jour $questionDay > limite ($FREE_DAILY_QUESTION_DAYS) - Paywall requis")

            analyticsService.logEvent("paywall_shown") {
                param("source", "daily_question_freemium")
                param("question_day", questionDay.toLong())
            }

            onPaywallRequired()
        }
    }

    private suspend fun markDailyQuestionUsage(day: Int) {
        analyticsService.logEvent("daily_question_accessed") {
            param("question_day", day.toLong())
            param("user_type", "free")
        }
    }
}
```

### 7.6 Notifications Push Android

```kotlin
@Singleton
class NotificationService @Inject constructor(
    private val context: Context,
    private val analyticsService: AnalyticsService
) {

    companion object {
        private const val CHANNEL_ID = "daily_questions"
        private const val CHANNEL_NAME = "Questions du jour"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications pour les questions du jour et nouveaux messages"
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 🔑 PERMISSION NOTIFICATIONS
    suspend fun requestNotificationPermission(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                // Demander permission
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
                false
            } else {
                // Enregistrer token FCM
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    Log.d("NotificationService", "Token FCM: $token")
                    // Sauvegarder token dans Firestore pour utilisateur
                }
                true
            }
        } else {
            true // Permissions automatiques sur anciennes versions
        }
    }

    // 🔑 NOTIFICATION NOUVEAU MESSAGE
    fun showNewMessageNotification(
        questionId: String,
        senderName: String,
        messageText: String
    ) {
        val notificationId = questionId.hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(notificationId, notification)

            analyticsService.logEvent("notification_shown") {
                param("type", "new_message")
                param("sender_name_hash", senderName.hashCode().toLong())
            }
        }
    }

    // 🔑 NETTOYAGE NOTIFICATIONS
    fun clearNotificationsForQuestion(questionId: String) {
        val notificationId = questionId.hashCode()
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(notificationId)
    }

    fun clearAllNotifications() {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancelAll()
    }

    companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
```

---

## 📋 Conclusion

Le système Questions du Jour de CoupleApp iOS présente une architecture sophistiquée avec connexion partenaire obligatoire, freemium intelligent, et intégration temps réel :

### 🎯 **Points Forts du Système :**

- **Connexion partenaire obligatoire** : Impossible d'accéder sans être connecté à un partenaire
- **Freemium 3 jours gratuits** : Permet de tester avant l'abonnement, conversion optimisée
- **Chat temps réel** : Messages instantanés avec notifications push contextuelles
- **Onboarding intelligent** : Skip paywall si partenaire premium, flow optimisé
- **Génération Firebase** : Questions dynamiques avec clés localisées, gestion timezone
- **Interface immersive** : Cartes swipables, animations, UX premium

### 🔧 **Composants Techniques iOS :**

- `DailyQuestionFlowView` - Router avec logique conditionnelle complexe
- `DailyQuestionService` - Service central avec listeners temps réel Firestore
- `FreemiumManager` - Logique 3 jours gratuits avec analytics détaillées
- `MessageKit` - Chat interface native iOS avec bulles personnalisées
- Cloud Functions - `generateDailyQuestion()` avec nettoyage automatique
- XCStrings - Localisation 8 langues avec clés dynamiques

### 🔥 **Firebase Integration Complète :**

- **Collections** : `dailyQuestions`, `dailyQuestionSettings`, `responses` (sous-collection)
- **Cloud Functions** : Génération quotidienne, nettoyage automatique, gestion timezone
- **Real-time listeners** : Synchronisation instantanée questions et réponses
- **Analytics** : Tracking granulaire usage freemium, conversions, engagement

### 💬 **Messagerie Temps Réel Avancée :**

- **Affichage immédiat** : Messages temporaires avant confirmation Firebase
- **Notifications push** : Nouveau message → Notification instantanée avec nom + contenu
- **Gestion badges** : Nettoyage automatique, compteurs précis
- **UX optimisée** : Auto-scroll, clavier intelligent, états de loading

### 🌍 **Localisation Multilingue :**

- **8 langues supportées** : FR, EN, DE, ES, IT, PT-BR, PT-PT, NL
- **Questions dynamiques** : `daily_question_1` → `daily_question_300+`
- **Interface complète** : Tous les textes UI localisés
- **Adaptation Android** : Conversion XCStrings → strings.xml automatisée

### 🤖 **Architecture Android Robuste :**

- **Repository Pattern** : `DailyQuestionRepository` avec StateFlow reactive
- **Jetpack Compose** : Interface moderne avec Material Design 3
- **Firebase SDK** : Listeners temps réel identiques, Cloud Functions compatibles
- **Notifications** : Support Android 13+, channels, permissions granulaires
- **Freemium Logic** : Logique identique avec adaptations Android natives

### ⚡ **Fonctionnalités Uniques :**

- **Génération intelligente** : 1 question/jour, nettoyage automatique questions passées
- **Partage premium** : Connexion partenaire abonné → Skip paywall automatique
- **Analytics sophistiquées** : Tracking conversion freemium, engagement chat
- **Gestion timezone** : Génération précise selon fuseau horaire couple
- **Performance optimisée** : Cache local, listeners efficaces, UI responsive

### 📊 **Métriques Business :**

- **Conversion freemium** : 3 jours gratuits → Taux conversion élevé
- **Engagement quotidien** : Question/jour → Habitude utilisateur
- **Effet de réseau** : Connexion partenaire → Rétention mutuelle
- **Monétisation** : Premium requis après 3 jours → ARR prévisible

### ⏱️ **Estimation Développement Android :**

- **Phase 1** : Repository + Models + Firebase (3-4 semaines)
- **Phase 2** : UI Compose + Chat interface (4-5 semaines)
- **Phase 3** : Notifications + Permissions (2-3 semaines)
- **Phase 4** : Freemium + Analytics (2-3 semaines)
- **Phase 5** : Tests + Optimisations (2-3 semaines)

**Total estimé : 13-18 semaines** pour une réplication complète du système iOS vers Android.

Ce système Questions du Jour représente un **pilier fondamental** de l'engagement utilisateur avec sa mécanique **habitude quotidienne + connexion partenaire + freemium intelligent**, créant une **rétention exceptionnelle** et un **modèle économique durable**. 🚀

L'architecture est **prête pour l'évolution** avec des fondations solides pour futures fonctionnalités comme recommandations IA, thèmes personnalisés, ou gamification avancée.
