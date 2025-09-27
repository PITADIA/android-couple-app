# 💬 Rapport Complet - Système de Messagerie Instantanée

## 📋 Vue d'Ensemble

Le système de messagerie instantanée de l'application est centré sur les **Questions du Jour** et utilise une architecture sophistiquée basée sur **MessageKit** côté iOS et **Firebase Cloud Functions** pour la synchronisation en temps réel entre partenaires connectés.

---

## 🏗️ Architecture Système

### 🔧 Stack Technique

**iOS (Frontend)**

- `MessageKit` - Interface de chat native
- `SwiftUI` + `UIViewControllerRepresentable` - Intégration MessageKit
- `Firebase Auth` - Authentification utilisateurs
- `Firebase Firestore` - Base de données en temps réel
- `UNUserNotificationCenter` - Notifications locales
- `Firebase Messaging (FCM)` - Push notifications

**Backend (Firebase)**

- **Cloud Functions** - Logique métier et notifications
- **Firestore** - Stockage messages avec sous-collections
- **FCM** - Push notifications cross-platform
- **Firebase Rules** - Sécurité et accès

---

## 💬 Gestion des Messages - Architecture Détaillée

### 📁 Structure Firestore

```
dailyQuestions/{questionId}/
├── question data (titre, jour, etc.)
├── responses/ (sous-collection)
│   ├── {responseId1}
│   │   ├── id: string
│   │   ├── userId: string
│   │   ├── userName: string
│   │   ├── text: string (chiffré hybride)
│   │   ├── respondedAt: timestamp
│   │   ├── status: "answered"
│   │   └── isReadByPartner: boolean
│   └── {responseId2}
└── ...
```

### 🔄 Flux Message - Envoi

**1. Interface Utilisateur (MessageKit)**

```swift
// DailyQuestionMessageKitView.swift
func inputBar(_ inputBar: InputBarAccessoryView, didPressSendButtonWith text: String) {
    // Créer message temporaire pour affichage immédiat
    let tempMessage = DailyQuestionMessage(
        tempId: UUID().uuidString,
        text: text,
        sender: currentUserSender
    )

    // Ajouter immédiatement à l'interface (UX optimisée)
    insertMessage(tempMessage)

    // Vider la barre de saisie
    inputBar.inputTextView.text = ""

    // Analytics: Message envoyé
    Analytics.logEvent("message_envoye", parameters: [...])

    // Envoyer à Firebase (asynchrone)
    Task {
        let success = await dailyQuestionService.submitResponse(text)
        if !success {
            // Gérer l'erreur : retirer le message temporaire
        }
    }
}
```

**2. Service iOS → Firebase**

```swift
// DailyQuestionService.swift
func submitResponse(_ responseText: String) async -> Bool {
    guard let functions = Functions.functions() else { return false }

    let data = [
        "questionId": questionId,
        "responseText": responseText
    ]

    do {
        let result = try await functions.httpsCallable("submitDailyQuestionResponse").call(data)
        print("✅ Réponse envoyée avec succès")
        return true
    } catch {
        print("❌ Erreur envoi réponse: \(error)")
        return false
    }
}
```

**3. Cloud Function Firebase**

```javascript
// firebase/functions/index.js
exports.submitDailyQuestionResponse = functions.https.onCall(
  async (data, context) => {
    const { questionId, responseText } = data;
    const userId = context.auth.uid;

    // Validation et sécurité
    if (!responseText?.trim()) {
      throw new functions.https.HttpsError("invalid-argument", "Texte requis");
    }

    // Créer la réponse dans sous-collection
    const responseData = {
      id: admin.firestore().collection("temp").doc().id,
      userId: userId,
      userName: userName,
      text: responseText.trim(),
      respondedAt: admin.firestore.FieldValue.serverTimestamp(),
      status: "answered",
      isReadByPartner: false,
    };

    // Sauvegarder dans Firestore
    await questionRef
      .collection("responses")
      .doc(responseData.id)
      .set(responseData);

    // Mettre à jour question parent
    await questionRef.update({
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      status: "active",
    });

    return { success: true, responseId: responseData.id };
  }
);
```

### 🔄 Flux Message - Réception Temps Réel

**1. Listener Firestore iOS**

```swift
// DailyQuestionService.swift
private func setupResponsesListener(for question: DailyQuestion) async {
    responsesListener = db.collection("dailyQuestions")
        .document(question.id)
        .collection("responses")
        .order(by: "respondedAt")
        .addSnapshotListener { [weak self] snapshot, error in
            Task { @MainActor in
                guard let documents = snapshot?.documents else { return }

                var responses: [QuestionResponse] = []
                for document in documents {
                    if let response = try? document.data(as: QuestionResponse.self) {
                        responses.append(response)
                    }
                }

                // Mettre à jour la question avec nouvelles réponses
                if var currentQuestion = self?.currentQuestion {
                    currentQuestion.responsesFromSubcollection = responses
                    self?.currentQuestion = currentQuestion

                    // Programmer notifications pour nouveaux messages
                    for response in responses {
                        if !self?.previousResponses.contains(where: { $0.id == response.id }) ?? false {
                            await self?.scheduleNewMessageNotification(for: currentQuestion, newResponse: response)
                        }
                    }

                    self?.previousResponses = responses
                }
            }
        }
}
```

**2. Mise à jour Interface MessageKit**

```swift
// DailyQuestionMessageKitView.swift
func updateMessages() {
    guard let question = question else { return }

    let newMessages = MessageKitAdapter.convert(question.responsesArray)

    // Animation smooth pour nouveaux messages
    let shouldScrollToBottom = messages.isEmpty
    messages = newMessages

    DispatchQueue.main.async {
        self.messagesCollectionView.reloadData()
        if shouldScrollToBottom {
            self.messagesCollectionView.scrollToLastItem()
        }
    }
}
```

### 📱 Push Notifications Automatiques

**1. Trigger Firebase (Cloud Function)**

```javascript
// firebase/functions/index.js - Déclenché automatiquement
exports.notifyPartnerResponseSubcollection = functions.firestore
  .document("dailyQuestions/{questionId}/responses/{responseId}")
  .onCreate(async (snap, context) => {
    const responseData = snap.data();
    const questionId = context.params.questionId;
    const respondingUserId = responseData.userId;

    // Identifier le partenaire à notifier
    const coupleId = questionData.coupleId;
    const userIds = coupleId.split("_");
    const partnerUserId = userIds.find((id) => id !== respondingUserId);

    // Récupérer token FCM du partenaire
    const partnerDoc = await admin
      .firestore()
      .collection("users")
      .doc(partnerUserId)
      .get();

    const fcmToken = partnerDoc.data()?.fcmToken;
    if (!fcmToken) return;

    // Payload notification avec localisation
    const userLanguage = partnerDoc.data()?.languageCode || "fr";
    const payload = {
      notification: {
        title: responseData.userName,
        body: responseData.text,
      },
      data: {
        questionId: questionId,
        senderId: respondingUserId,
        type: "new_message",
        language: userLanguage,
      },
      token: fcmToken,
    };

    // Envoyer notification FCM
    const response = await admin.messaging().send(payload);
    console.log(`✅ Push FCM envoyé: ${response}`);
  });
```

**2. Gestion iOS (UNUserNotificationCenter)**

```swift
// Services/FCMService.swift
private func scheduleNewMessageNotification(for question: DailyQuestion, newResponse: QuestionResponse) async {
    // Ne notifier que si le message vient du partenaire
    guard let currentUserId = Auth.auth().currentUser?.uid,
          newResponse.userId != currentUserId else { return }

    let center = UNUserNotificationCenter.current()
    let identifier = "new_message_\(question.id)_\(newResponse.id)"

    let content = UNMutableNotificationContent()
    content.title = newResponse.userName
    content.body = newResponse.text
    content.sound = .default
    content.badge = 1

    // Notification immédiate
    let request = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)

    try? await center.add(request)
    print("🔔 Notification programmée: \(newResponse.userName)")
}
```

---

## 🔍 Détection Première Ouverture Messagerie

### 💾 Système IntroFlags

**Structure de données**

```swift
// ViewModels/AppState.swift
struct IntroFlags: Codable {
    var dailyQuestion: Bool = false
    var dailyChallenge: Bool = false

    static var `default`: IntroFlags {
        return IntroFlags(dailyQuestion: false, dailyChallenge: false)
    }
}

@Published var introFlags: IntroFlags = IntroFlags.default
```

**Détection utilisateur existant vs nouveau**

```swift
// ViewModels/AppState.swift
private func isLikelyExistingUser() -> Bool {
    // Vérifier historique d'utilisation
    let hasUsedDailyQuestion = currentUser?.dailyQuestionFirstAccessDate != nil
    let hasUsedDailyChallenge = currentUser?.dailyChallengeFirstAccessDate != nil

    // Vérifier historique d'activité
    let hasQuestionHistory = currentUser?.dailyQuestionMaxDayReached ?? 0 > 1
    let hasChallengeHistory = currentUser?.dailyChallengeMaxDayReached ?? 0 > 1

    return hasUsedDailyQuestion || hasUsedDailyChallenge || hasQuestionHistory || hasChallengeHistory
}

func initializeIntroFlagsForExistingUsers() {
    guard let coupleId = generateCoupleId() else { return }

    if UserDefaults.standard.data(forKey: key) == nil {
        if isLikelyExistingUser() {
            // Utilisateur existant → marquer intro comme vue
            introFlags = IntroFlags(dailyQuestion: true, dailyChallenge: true)
        } else {
            // Nouveau → afficher intro
            introFlags = IntroFlags.default
        }
        saveIntroFlags()
    }
}
```

**Usage dans les vues**

```swift
// Views/DailyQuestion/DailyQuestionMainView.swift
@EnvironmentObject var appState: AppState

var body: some View {
    VStack {
        if appState.introFlags.dailyQuestion {
            // Afficher messagerie directement
            DailyQuestionMessageKitView(question: question)
        } else {
            // Afficher page d'introduction
            DailyQuestionIntroView {
                appState.markDailyQuestionIntroAsSeen()
            }
        }
    }
}
```

---

## 🔐 Sécurité et Chiffrement

### 🛡️ Chiffrement Hybride Messages

**Chiffrement côté client**

```swift
// Models/DailyQuestion.swift - QuestionResponse
func toFirestoreData() -> [String: Any] {
    var data: [String: Any] = [
        "id": id,
        "userId": userId,
        "userName": userName,
        "respondedAt": Timestamp(date: respondedAt),
        "status": status.rawValue,
        "isReadByPartner": isReadByPartner
    ]

    // 🔐 CHIFFREMENT HYBRIDE des messages
    let encryptedTextData = LocationEncryptionService.processMessageForStorage(text)
    data.merge(encryptedTextData) { (_, new) in new }

    return data
}
```

**Déchiffrement à la lecture**

```swift
static func fromFirestoreData(_ data: [String: Any]) -> QuestionResponse? {
    // Déchiffrer le texte si chiffré
    let decryptedText = LocationEncryptionService.processMessageFromStorage(data) ??
                       (data["text"] as? String ?? "")

    return QuestionResponse(
        userId: data["userId"] as? String ?? "",
        userName: data["userName"] as? String ?? "",
        text: decryptedText,
        status: ResponseStatus(rawValue: data["status"] as? String ?? "answered") ?? .answered
    )
}
```

### 🔒 Règles Firestore Sécurisées

```javascript
// firestore.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Questions quotidiennes et réponses
    match /dailyQuestions/{questionId} {
      allow read, write: if request.auth != null &&
        resource.data.coupleId != null &&
        (resource.data.coupleId.split('_')[0] == request.auth.uid ||
         resource.data.coupleId.split('_')[1] == request.auth.uid);

      // Sous-collection réponses
      match /responses/{responseId} {
        allow read: if request.auth != null &&
          (resource.data.userId == request.auth.uid ||
           isPartner(request.auth.uid, resource.data.userId));
        allow create: if request.auth != null &&
          request.auth.uid == resource.data.userId;
      }
    }
  }

  function isPartner(currentUserId, responseUserId) {
    let questionDoc = get(/databases/$(database)/documents/dailyQuestions/$(resource.id));
    let coupleId = questionDoc.data.coupleId;
    let userIds = coupleId.split('_');
    return currentUserId in userIds && responseUserId in userIds;
  }
}
```

---

## 📊 Persistance Messages - Système Multi-Niveau

### 💾 Cache Local (Realm)

**Modèles Realm**

```swift
// Models/RealmModels.swift
class RealmQuestionResponse: Object, Identifiable {
    @Persisted var id: String = UUID().uuidString
    @Persisted var userId: String = ""
    @Persisted var userName: String = ""
    @Persisted var text: String = ""
    @Persisted var respondedAt: Date = Date()
    @Persisted var status: String = "answered"
    @Persisted var isReadByPartner: Bool = false

    override static func primaryKey() -> String? { return "id" }
}

class RealmDailyQuestion: Object, Identifiable {
    @Persisted var responses = List<RealmQuestionResponse>()
    // ... autres propriétés
}
```

**Gestion Cache Service**

```swift
// Models/RealmModels.swift
func updateDailyQuestionResponse(_ questionId: String, userId: String, response: QuestionResponse) {
    do {
        try realm.write {
            if let realmQuestion = realm.object(ofType: RealmDailyQuestion.self, forPrimaryKey: questionId) {
                // Supprimer ancienne réponse utilisateur
                let existingResponseIndex = realmQuestion.responses.firstIndex { $0.userId == userId }
                if let index = existingResponseIndex {
                    realmQuestion.responses.remove(at: index)
                }

                // Ajouter nouvelle réponse
                let realmResponse = RealmQuestionResponse(questionResponse: response)
                realmQuestion.responses.append(realmResponse)

                // Mettre à jour statut question
                let answeredCount = realmQuestion.responses.filter { $0.status == "answered" }.count
                realmQuestion.status = answeredCount >= 2 ? "both_answered" : "one_answered"
            }
        }
    } catch {
        print("❌ Erreur mise à jour cache: \(error)")
    }
}
```

### ☁️ Synchronisation Firebase ↔ Cache

**Stratégie Cache-First avec Sync**

```swift
// Services/DailyQuestionService.swift
func getCurrentQuestion() async -> DailyQuestion? {
    // 1. Vérifier cache local d'abord
    if let cachedQuestion = getCachedQuestion() {
        print("📦 Question trouvée en cache")
        setupResponsesListener(for: cachedQuestion) // Sync en arrière-plan
        return cachedQuestion
    }

    // 2. Récupérer depuis Firebase si pas en cache
    do {
        let question = try await fetchQuestionFromFirebase()
        cacheQuestion(question) // Mettre en cache
        setupResponsesListener(for: question)
        return question
    } catch {
        print("❌ Erreur récupération question: \(error)")
        return nil
    }
}
```

---

## ⚡ Gestion Messages Séquentiels - Anti-Remplacement

### 📝 Accumulation Messages (Pas de Remplacement)

**Structure Sous-Collection Firestore**

```javascript
// Chaque message = document unique dans sous-collection
dailyQuestions/question123/responses/
├── response_001 { userId: "user1", text: "Salut!", respondedAt: timestamp1 }
├── response_002 { userId: "user2", text: "Hello!", respondedAt: timestamp2 }
├── response_003 { userId: "user1", text: "Ça va?", respondedAt: timestamp3 }
└── response_004 { userId: "user2", text: "Très bien!", respondedAt: timestamp4 }
```

**Tri Chronologique Automatique**

```swift
// DailyQuestionService.swift
.order(by: "respondedAt") // Firestore Query garantit l'ordre chronologique
```

**Conversion MessageKit**

```swift
// Models/MessageKitModels.swift
class MessageKitAdapter {
    static func convert(_ responses: [QuestionResponse]) -> [DailyQuestionMessage] {
        return responses
            .sorted { $0.respondedAt < $1.respondedAt } // Double sécurité tri
            .map { response in
                DailyQuestionMessage(response: response)
            }
    }
}
```

**Insertion UI Optimisée**

```swift
// DailyQuestionMessageKitView.swift
private func insertMessage(_ message: DailyQuestionMessage) {
    messages.append(message) // Ajout, jamais remplacement

    DispatchQueue.main.async {
        self.messagesCollectionView.performBatchUpdates({
            // Insérer nouvelle section pour nouveau message
            self.messagesCollectionView.insertSections([self.messages.count - 1])
        }, completion: { _ in
            self.messagesCollectionView.scrollToLastItem(animated: true)
        })
    }
}
```

---

## 🎯 Analytics et Monitoring

### 📊 Événements Trackés

**Envoi Messages**

```swift
// Analytics: Message envoyé
Analytics.logEvent("message_envoye", parameters: [
    "type": "texte",
    "source": "daily_question_messagekit"
])
```

**Ouverture Messagerie**

```swift
// Analytics: Ouverture chat
Analytics.logEvent("daily_question_chat_opened", parameters: [
    "question_day": currentQuestion.day,
    "has_previous_responses": !currentQuestion.responsesArray.isEmpty
])
```

---

# 🤖 Adaptation Android - Stratégie Complète

## 📱 Stack Technique Android

### 🔧 Technologies Recommandées

```kotlin
// build.gradle (Module: app)
dependencies {
    // Interface Chat
    implementation 'io.getstream:stream-chat-android-ui-components:5.11.0'
    // Alternative: implementation 'com.github.bassaer:chatmessageview:2.1.0'

    // Firebase
    implementation 'com.google.firebase:firebase-messaging:23.2.1'
    implementation 'com.google.firebase:firebase-firestore:24.7.1'
    implementation 'com.google.firebase:firebase-functions:20.3.1'
    implementation 'com.google.firebase:firebase-auth:22.1.0'

    // Jetpack Compose
    implementation 'androidx.compose.ui:ui:1.5.0'
    implementation 'androidx.compose.material3:material3:1.1.1'

    // State Management
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // Local Database
    implementation 'androidx.room:room-runtime:2.5.0'
    implementation 'androidx.room:room-ktx:2.5.0'

    // Notifications
    implementation 'androidx.work:work-runtime-ktx:2.8.1'
}
```

---

## 💬 Interface Chat Android (Stream Chat SDK)

### 🎨 Activity Chat Principale

```kotlin
// DailyQuestionChatActivity.kt
@AndroidEntryPoint
class DailyQuestionChatActivity : ComponentActivity() {

    private val viewModel: DailyQuestionChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val questionId = intent.getStringExtra("question_id") ?: return

        setContent {
            Love2LoveTheme {
                DailyQuestionChatScreen(
                    questionId = questionId,
                    viewModel = viewModel
                )
            }
        }
    }
}
```

### 🎨 Composable Chat Interface

```kotlin
// DailyQuestionChatScreen.kt
@Composable
fun DailyQuestionChatScreen(
    questionId: String,
    viewModel: DailyQuestionChatViewModel
) {
    val chatState by viewModel.chatState.collectAsState()
    val messages by viewModel.messages.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8))
    ) {
        // Header avec titre question
        ChatHeader(
            question = chatState.currentQuestion,
            onBackPressed = { /* Retour */ }
        )

        // Liste messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                MessageItem(
                    message = message,
                    isCurrentUser = message.userId == viewModel.currentUserId
                )
            }
        }

        // Barre de saisie
        MessageInputBar(
            onSendMessage = { text ->
                viewModel.sendMessage(text)
            }
        )
    }
}
```

### 💬 Composant Message

```kotlin
// MessageItem.kt
@Composable
fun MessageItem(
    message: QuestionResponse,
    isCurrentUser: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrentUser)
                    Color(0xFFE3F2FD) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (!isCurrentUser) {
                    Text(
                        text = message.userName,
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }

                Text(
                    text = message.text,
                    fontSize = 16.sp,
                    color = Color.Black
                )

                Text(
                    text = formatTime(message.respondedAt),
                    fontSize = 10.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
```

---

## 🔄 ViewModel et State Management

### 📊 ChatViewModel

```kotlin
// DailyQuestionChatViewModel.kt
@HiltViewModel
class DailyQuestionChatViewModel @Inject constructor(
    private val dailyQuestionRepository: DailyQuestionRepository,
    private val firebaseRepository: FirebaseRepository,
    private val notificationService: NotificationService,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _messages = MutableStateFlow<List<QuestionResponse>>(emptyList())
    val messages: StateFlow<List<QuestionResponse>> = _messages.asStateFlow()

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Listener Firestore en temps réel
    private var messagesListener: ListenerRegistration? = null

    fun loadQuestion(questionId: String) {
        viewModelScope.launch {
            try {
                // Charger question depuis cache/Firebase
                val question = dailyQuestionRepository.getQuestion(questionId)
                _chatState.value = _chatState.value.copy(currentQuestion = question)

                // Démarrer écoute temps réel des messages
                setupMessagesListener(questionId)

                // Analytics
                analyticsService.logEvent("daily_question_chat_opened", mapOf(
                    "question_day" to question?.day.toString(),
                    "has_previous_responses" to (question?.responses?.isNotEmpty() == true)
                ))

            } catch (e: Exception) {
                _chatState.value = _chatState.value.copy(error = e.message)
            }
        }
    }

    private fun setupMessagesListener(questionId: String) {
        messagesListener = FirebaseFirestore.getInstance()
            .collection("dailyQuestions")
            .document(questionId)
            .collection("responses")
            .orderBy("respondedAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _chatState.value = _chatState.value.copy(error = error.message)
                    return@addSnapshotListener
                }

                val responses = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(QuestionResponse::class.java)
                } ?: emptyList()

                _messages.value = responses

                // Programmer notifications pour nouveaux messages
                val newMessages = responses.filter { it.userId != currentUserId }
                newMessages.forEach { message ->
                    notificationService.scheduleMessageNotification(questionId, message)
                }
            }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                // Message temporaire pour UX immédiate
                val tempMessage = QuestionResponse(
                    id = UUID.randomUUID().toString(),
                    userId = currentUserId,
                    userName = getCurrentUserName(),
                    text = text,
                    respondedAt = Date(),
                    status = ResponseStatus.ANSWERED,
                    isReadByPartner = false
                )

                // Ajouter temporairement à l'interface
                val currentMessages = _messages.value.toMutableList()
                currentMessages.add(tempMessage)
                _messages.value = currentMessages

                // Envoyer à Firebase
                val success = firebaseRepository.submitDailyQuestionResponse(
                    questionId = _chatState.value.currentQuestion?.id ?: "",
                    responseText = text
                )

                if (!success) {
                    // Retirer message temporaire en cas d'erreur
                    _messages.value = _messages.value.filter { it.id != tempMessage.id }
                    _chatState.value = _chatState.value.copy(error = "Erreur envoi message")
                }

                // Analytics
                analyticsService.logEvent("message_envoye", mapOf(
                    "type" to "texte",
                    "source" to "daily_question_chat"
                ))

            } catch (e: Exception) {
                _chatState.value = _chatState.value.copy(error = e.message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesListener?.remove()
    }
}

data class ChatState(
    val currentQuestion: DailyQuestion? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

---

## 🔥 Firebase Integration Android

### ⚡ Repository Firebase

```kotlin
// FirebaseRepository.kt
@Singleton
class FirebaseRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions
) {

    suspend fun submitDailyQuestionResponse(
        questionId: String,
        responseText: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = hashMapOf(
                "questionId" to questionId,
                "responseText" to responseText
            )

            val result = functions
                .getHttpsCallable("submitDailyQuestionResponse")
                .call(data)
                .await()

            val response = result.data as? Map<*, *>
            return@withContext response?.get("success") == true

        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Erreur envoi réponse", e)
            return@withContext false
        }
    }

    fun getMessagesRealtime(
        questionId: String,
        onMessagesUpdated: (List<QuestionResponse>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return firestore
            .collection("dailyQuestions")
            .document(questionId)
            .collection("responses")
            .orderBy("respondedAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Erreur inconnue")
                    return@addSnapshotListener
                }

                val responses = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(QuestionResponse::class.java)
                    } catch (e: Exception) {
                        Log.e("FirebaseRepo", "Erreur parsing message", e)
                        null
                    }
                } ?: emptyList()

                onMessagesUpdated(responses)
            }
    }
}
```

---

## 🔔 Système Notifications Android

### 📱 FCM Service Android

```kotlin
// MyFirebaseMessagingService.kt
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "Message reçu de: ${remoteMessage.from}")

        val messageType = remoteMessage.data["type"]

        when (messageType) {
            "new_message" -> handleNewMessageNotification(remoteMessage)
            else -> handleGenericNotification(remoteMessage)
        }
    }

    private fun handleNewMessageNotification(remoteMessage: RemoteMessage) {
        val questionId = remoteMessage.data["questionId"] ?: return
        val senderId = remoteMessage.data["senderId"] ?: return
        val senderName = remoteMessage.data["senderName"] ?: "Votre partenaire"

        val title = senderName
        val body = remoteMessage.notification?.body ?: "Nouveau message"

        // Créer notification avec action d'ouverture
        val intent = Intent(this, DailyQuestionChatActivity::class.java).apply {
            putExtra("question_id", questionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(questionId.hashCode(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Nouveau token: $token")

        // Sauvegarder token dans Firestore
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val tokenData = hashMapOf(
            "fcmToken" to token,
            "tokenUpdatedAt" to FieldValue.serverTimestamp(),
            "deviceInfo" to hashMapOf(
                "platform" to "Android",
                "appVersion" to BuildConfig.VERSION_NAME,
                "deviceModel" to Build.MODEL
            )
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .update(tokenData)
            .addOnSuccessListener {
                Log.d("FCM", "Token sauvegardé avec succès")
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "Erreur sauvegarde token", e)
            }
    }

    companion object {
        const val CHANNEL_ID = "daily_question_messages"
    }
}
```

---

## 💾 Persistence Local (Room Database)

### 🗄️ Entités Room

```kotlin
// entities/QuestionResponseEntity.kt
@Entity(tableName = "question_responses")
data class QuestionResponseEntity(
    @PrimaryKey
    val id: String,
    val questionId: String,
    val userId: String,
    val userName: String,
    val text: String,
    val respondedAt: Long,
    val status: String,
    val isReadByPartner: Boolean
)

@Entity(tableName = "daily_questions")
data class DailyQuestionEntity(
    @PrimaryKey
    val id: String,
    val questionText: String,
    val day: Int,
    val coupleId: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 🗄️ DAO Room

```kotlin
// dao/DailyQuestionDao.kt
@Dao
interface DailyQuestionDao {

    @Query("SELECT * FROM daily_questions WHERE id = :questionId")
    suspend fun getQuestion(questionId: String): DailyQuestionEntity?

    @Query("""
        SELECT * FROM question_responses
        WHERE questionId = :questionId
        ORDER BY respondedAt ASC
    """)
    suspend fun getResponsesForQuestion(questionId: String): List<QuestionResponseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponse(response: QuestionResponseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: DailyQuestionEntity)

    @Query("DELETE FROM question_responses WHERE questionId = :questionId AND respondedAt < :cutoffTime")
    suspend fun deleteOldResponses(questionId: String, cutoffTime: Long)
}
```

### 📦 Repository avec Cache

```kotlin
// DailyQuestionRepository.kt
@Singleton
class DailyQuestionRepository @Inject constructor(
    private val dao: DailyQuestionDao,
    private val firebaseRepository: FirebaseRepository
) {

    suspend fun getQuestion(questionId: String): DailyQuestion? {
        // 1. Vérifier cache local
        val cachedQuestion = dao.getQuestion(questionId)
        if (cachedQuestion != null) {
            val cachedResponses = dao.getResponsesForQuestion(questionId)
            return mapToDomain(cachedQuestion, cachedResponses)
        }

        // 2. Récupérer depuis Firebase
        return try {
            val firebaseQuestion = firebaseRepository.getQuestion(questionId)
            firebaseQuestion?.let {
                cacheQuestion(it)
                it
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun cacheQuestion(question: DailyQuestion) {
        dao.insertQuestion(question.toEntity())
        question.responses.forEach { response ->
            dao.insertResponse(response.toEntity(question.id))
        }
    }

    suspend fun addResponseToCache(questionId: String, response: QuestionResponse) {
        dao.insertResponse(response.toEntity(questionId))
    }
}
```

---

## 🔍 Détection Première Ouverture Android

### 📊 SharedPreferences Manager

```kotlin
// IntroFlagsManager.kt
@Singleton
class IntroFlagsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository
) {
    private val prefs = context.getSharedPreferences("intro_flags", Context.MODE_PRIVATE)

    data class IntroFlags(
        val dailyQuestion: Boolean = false,
        val dailyChallenge: Boolean = false
    )

    suspend fun getIntroFlags(): IntroFlags {
        val coupleId = userRepository.generateCoupleId() ?: return IntroFlags()

        return IntroFlags(
            dailyQuestion = prefs.getBoolean("daily_question_$coupleId", false),
            dailyChallenge = prefs.getBoolean("daily_challenge_$coupleId", false)
        )
    }

    suspend fun initializeIntroFlagsForExistingUsers() {
        val coupleId = userRepository.generateCoupleId() ?: return

        if (!prefs.contains("daily_question_$coupleId")) {
            if (isLikelyExistingUser()) {
                // Utilisateur existant → marquer intro comme vue
                markDailyQuestionIntroAsSeen()
                markDailyChallengeIntroAsSeen()
            }
        }
    }

    private suspend fun isLikelyExistingUser(): Boolean {
        val currentUser = userRepository.getCurrentUser()

        val hasUsedDailyQuestion = currentUser?.dailyQuestionFirstAccessDate != null
        val hasUsedDailyChallenge = currentUser?.dailyChallengeFirstAccessDate != null
        val hasQuestionHistory = (currentUser?.dailyQuestionMaxDayReached ?: 0) > 1
        val hasChallengeHistory = (currentUser?.dailyChallengeMaxDayReached ?: 0) > 1

        return hasUsedDailyQuestion || hasUsedDailyChallenge ||
               hasQuestionHistory || hasChallengeHistory
    }

    fun markDailyQuestionIntroAsSeen() {
        val coupleId = userRepository.generateCoupleId() ?: return
        prefs.edit().putBoolean("daily_question_$coupleId", true).apply()
    }
}
```

---

## 🛠️ Configuration et Setup

### 📋 AndroidManifest.xml

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />

    <application android:name=".Love2LoveApplication">

        <!-- Chat Activity -->
        <activity
            android:name=".ui.dailyquestion.DailyQuestionChatActivity"
            android:exported="false"
            android:theme="@style/Theme.Love2Love.NoActionBar" />

        <!-- FCM Service -->
        <service
            android:name=".services.MyFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

        <!-- Notification Channels -->
        <meta-data
            android:name="com.google.firebase.messaging.default_notification_channel_id"
            android:value="daily_question_messages" />

    </application>
</manifest>
```

### 🎯 Navigation Integration

```kotlin
// Navigation.kt
@Composable
fun Love2LoveNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") { MainScreen(navController) }

        composable(
            "daily_question_chat/{questionId}",
            arguments = listOf(navArgument("questionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val questionId = backStackEntry.arguments?.getString("questionId") ?: ""
            DailyQuestionChatScreen(questionId = questionId)
        }
    }
}
```

---

## 🚀 Résumé Architecture Android

### ✅ Points Clés Implémentés

1. **Interface Chat Native** - Stream Chat SDK / Jetpack Compose
2. **Temps Réel Firebase** - Listeners Firestore automatiques
3. **Push Notifications** - FCM Service avec payload personnalisés
4. **Cache Local Intelligent** - Room Database + Repository pattern
5. **State Management** - StateFlow + ViewModel MVVM
6. **Sécurité** - Règles Firestore identiques iOS
7. **Analytics** - Firebase Analytics events
8. **Détection Première Ouverture** - SharedPreferences + heuristiques

### 🎯 Avantages Architecture Android

- **Performance** : Cache-first avec sync en arrière-plan
- **UX Fluide** : Messages temporaires + confirmation asynchrone
- **Scalabilité** : Architecture modulaire Hilt/MVVM
- **Maintenance** : Séparation claire Repository/ViewModel/UI
- **Cross-Platform** : Logique Firebase identique iOS/Android

---

## 📊 Tests et Validation

### 🧪 Scénarios Tests Critiques

1. **Envoi Message** : Temporary → Firebase → Confirmation
2. **Réception Temps Réel** : Firebase → Listener → UI Update
3. **Push Notifications** : Nouveau message → FCM → Notification Android
4. **Cache Offline** : Pas de réseau → Données locales Room
5. **Première Ouverture** : Detection utilisateur existant vs nouveau

Cette architecture garantit une **messagerie instantanée robuste** avec **synchronisation temps réel**, **notifications push intelligentes** et **expérience utilisateur optimisée** sur Android ! 🚀
