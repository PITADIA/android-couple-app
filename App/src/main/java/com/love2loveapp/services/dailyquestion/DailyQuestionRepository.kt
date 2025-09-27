package com.love2loveapp.services.dailyquestion

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.love2loveapp.models.DailyQuestion
import com.love2loveapp.models.QuestionResponse
import com.love2loveapp.models.DailyQuestionSettings
import com.love2loveapp.models.ResponseStatus
import com.love2loveapp.models.DailyContentRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🔥 DailyQuestionRepository - Service Principal Questions du Jour Android
 * Équivalent iOS DailyQuestionService.swift
 * 
 * Fonctionnalités principales :
 * - Génération questions quotidiennes via Cloud Functions
 * - Chat temps réel avec Firestore listeners  
 * - Gestion freemium (3 jours gratuits)
 * - Compatibilité iOS ↔ Android
 * - Notifications push intégrées
 */
class DailyQuestionRepository private constructor(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth
) {
    
    // 🔔 Service de notifications
    private val notificationService: DailyQuestionNotificationService by lazy {
        DailyQuestionNotificationService.getInstance(context)
    }

    companion object {
        private const val TAG = "DailyQuestionRepository"
        private const val COLLECTION_DAILY_QUESTIONS = "dailyQuestions"
        private const val COLLECTION_SETTINGS = "dailyQuestionSettings"
        private const val COLLECTION_RESPONSES = "responses"

        @Volatile
        private var INSTANCE: DailyQuestionRepository? = null

        fun getInstance(context: Context): DailyQuestionRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DailyQuestionRepository(
                    context = context.applicationContext,
                    firestore = FirebaseFirestore.getInstance(),
                    functions = FirebaseFunctions.getInstance(),
                    auth = FirebaseAuth.getInstance()
                ).also { INSTANCE = it }
            }
        }
    }

    // 🔥 ÉTAT GLOBAL DU SERVICE
    private val _currentQuestion = MutableStateFlow<DailyQuestion?>(null)
    val currentQuestion: StateFlow<DailyQuestion?> = _currentQuestion.asStateFlow()

    private val _responses = MutableStateFlow<List<QuestionResponse>>(emptyList())
    val responses: StateFlow<List<QuestionResponse>> = _responses.asStateFlow()

    private val _currentSettings = MutableStateFlow<DailyQuestionSettings?>(null)
    val currentSettings: StateFlow<DailyQuestionSettings?> = _currentSettings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _hasSeenIntro = MutableStateFlow(false)
    val hasSeenIntro: StateFlow<Boolean> = _hasSeenIntro.asStateFlow()

    // 🔧 GESTION LISTENERS FIRESTORE
    private var questionListener: ListenerRegistration? = null
    private var responsesListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null

    // 📊 DONNÉES CACHED
    private var currentCoupleId: String? = null
    private var currentUserId: String? = null

    // MARK: - Initialisation et Configuration

    /**
     * 🚀 Initialisation pour un couple donné
     * Point d'entrée principal du service
     */
    suspend fun initializeForCouple(coupleId: String) {
        Log.d(TAG, "🚀 Initialisation pour couple: $coupleId")
        
        this.currentCoupleId = coupleId
        
        // 🔑 GESTION MODE INVITÉ: Utiliser AppState si Firebase Auth null
        val firebaseUser = auth.currentUser
        val appStateUser = com.love2loveapp.AppDelegate.appState.currentUser.value
        
        this.currentUserId = firebaseUser?.uid ?: appStateUser?.id
        
        if (currentUserId == null) {
            _errorMessage.value = "Aucun utilisateur disponible (ni Firebase Auth ni mode invité)"
            return
        }
        
        Log.d(TAG, "👤 Utilisateur initialisé: ${if (firebaseUser != null) "Firebase Auth" else "Mode invité"} (${currentUserId?.take(8)}...)")

        try {
            // 🔑 1. CACHE-FIRST : Chercher données existantes dans le cache AVANT tout
            val cachedQuestion = getCachedQuestionForToday(coupleId)
            if (cachedQuestion != null) {
                Log.d(TAG, "📦 Question trouvée en cache - Affichage immédiat")
                _currentQuestion.value = cachedQuestion
                _isLoading.value = false  // 🔑 Pas de loading si cache trouvé
                Log.d(TAG, "🏁 Loading arrêté immédiatement (cache hit)")
            } else {
                Log.d(TAG, "📭 Pas de cache - Démarrage loading")
                _isLoading.value = true
                Log.d(TAG, "🔄 Loading démarré (cache miss)")
            }

            _errorMessage.value = null

            // 🔑 2. CONFIGURER LISTENERS TEMPS RÉEL (en arrière-plan)
            setupRealtimeListeners(coupleId)

            // 🔑 3. CHARGER/CRÉER SETTINGS (en arrière-plan)  
            loadOrCreateSettings(coupleId)

            // 🔑 4. GÉNÉRER QUESTION D'AUJOURD'HUI SI NÉCESSAIRE (en arrière-plan)
            generateTodayQuestionIfNeeded(coupleId)

            // 🔑 5. CHARGER STATUS INTRO
            loadIntroStatus()

            Log.d(TAG, "✅ Initialisation terminée pour couple: $coupleId")
            
            // 🔑 IMPORTANT : Toujours arrêter le loading à la fin de l'initialisation !
            _isLoading.value = false
            Log.d(TAG, "🏁 Loading arrêté - Prêt pour affichage")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur initialisation: ${e.message}")
            _errorMessage.value = "Erreur lors de l'initialisation: ${e.message}"
            _isLoading.value = false
            Log.d(TAG, "🏁 Loading arrêté après erreur")
        }
    }

    /**
     * 📦 Récupérer question du jour depuis le cache
     * Implémentation cache-first comme iOS
     */
    private fun getCachedQuestionForToday(coupleId: String): DailyQuestion? {
        val cachedQuestion = _currentQuestion.value
        
        if (cachedQuestion != null) {
            // 🔍 Vérifier que la question est bien pour aujourd'hui (pas obsolète)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (cachedQuestion.scheduledDate == today && cachedQuestion.coupleId == coupleId) {
                Log.d(TAG, "📦 Cache valide trouvé: ${cachedQuestion.questionKey} pour $today")
                return cachedQuestion
            } else {
                Log.d(TAG, "📦 Cache obsolète: ${cachedQuestion.scheduledDate} vs $today")
                // Cache obsolète - le vider
                _currentQuestion.value = null
            }
        }
        
        Log.d(TAG, "📭 Aucun cache valide disponible")
        return null
    }

    // MARK: - Real-time Listeners

    /**
     * 🔥 Configuration des listeners Firestore temps réel
     * Compatible avec l'architecture iOS existante
     */
    private fun setupRealtimeListeners(coupleId: String) {
        Log.d(TAG, "🔥 Configuration listeners temps réel pour couple: $coupleId")

        // Nettoyer anciens listeners
        cleanup()

        // 🔑 LISTENER QUESTIONS
        questionListener = firestore.collection(COLLECTION_DAILY_QUESTIONS)
            .whereEqualTo("coupleId", coupleId)
            .orderBy("scheduledDateTime", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener questions: ${error.message}")
                    _errorMessage.value = "Erreur de synchronisation: ${error.message}"
                    return@addSnapshotListener
                }

                Log.d(TAG, "📥 Mise à jour questions: ${snapshot?.documents?.size} documents")

                val questions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        DailyQuestion.fromFirestore(doc)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur parsing question: ${e.message}")
                        null
                    }
                } ?: emptyList()

                // 🔍 Trouver question d'aujourd'hui
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayQuestion = questions.find { it.scheduledDate == today }

                _currentQuestion.value = todayQuestion

                // 🔥 CONFIGURER LISTENER RÉPONSES pour la question actuelle
                todayQuestion?.let { question ->
                    setupResponsesListener(question.id)
                }

                Log.d(TAG, "✅ Question actuelle: ${todayQuestion?.questionKey ?: "Aucune"}")
            }

        // 🔑 LISTENER SETTINGS
        settingsListener = firestore.collection(COLLECTION_SETTINGS)
            .document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener settings: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot?.exists() == true) {
                    val settings = DailyQuestionSettings.fromFirestore(snapshot)
                    _currentSettings.value = settings
                    Log.d(TAG, "✅ Settings mis à jour: jour ${settings?.currentDay}")
                }
            }
    }

    /**
     * 💬 Listener spécifique pour les réponses chat d'une question
     */
    private fun setupResponsesListener(questionId: String) {
        Log.d(TAG, "💬 Configuration listener réponses pour question: $questionId")

        // Nettoyer ancien listener
        responsesListener?.remove()

        responsesListener = firestore.collection(COLLECTION_DAILY_QUESTIONS)
            .document(questionId)
            .collection(COLLECTION_RESPONSES)
            .orderBy("respondedAt", Query.Direction.ASCENDING) // ✅ MÊME TRI QUE iOS
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener réponses: ${error.message}")
                    return@addSnapshotListener
                }

                val responses = snapshot?.documents?.mapNotNull { doc ->
                    try {
                val response = QuestionResponse.fromFirestore(doc)
                if (response == null) {
                    Log.w(TAG, "⚠️ fromFirestore retourné null pour doc: ${doc.id}")
                }
                        response
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur parsing réponse doc ${doc.id}: ${e.message}", e)
                        null
                    }
                } ?: emptyList()

                _responses.value = responses
                Log.d(TAG, "💬 ${responses.size} réponses mises à jour")

                // 🔔 NOTIFICATION NOUVEAU MESSAGE (seulement si pas de nous)
                handleNewMessageNotification(responses)
            }
    }

    /**
     * 🧹 FONCTION DE NETTOYAGE - Corriger les messages avec mauvais userId
     * À utiliser en cas de données corrompues
     */
    suspend fun fixCorruptedMessages(): Result<Unit> {
        return try {
            Log.d(TAG, "🧹 DÉBUT: Nettoyage des messages corrompus")
            
            val question = _currentQuestion.value
                ?: return Result.failure(Exception("Aucune question active"))
            
            Log.d(TAG, "🧹 Analyse des messages pour question: ${question.id}")
            
            // Récupérer tous les messages
            val messagesSnapshot = firestore.collection(COLLECTION_DAILY_QUESTIONS)
                .document(question.id)
                .collection(COLLECTION_RESPONSES)
                .get()
                .await()
            
            // 🔑 GESTION MODE INVITÉ
            val firebaseUser = auth.currentUser
            val appStateUser = com.love2loveapp.AppDelegate.appState.currentUser.value
            val currentUserId = firebaseUser?.uid ?: appStateUser?.id
            
            Log.d(TAG, "🧹 Utilisateur actuel: [MASKED] (${if (firebaseUser != null) "Firebase" else "Invité"})")
            Log.d(TAG, "🧹 Nombre de messages trouvés: ${messagesSnapshot.documents.size}")
            
            // Analyser chaque message
            messagesSnapshot.documents.forEach { doc ->
                val data = doc.data ?: return@forEach
                val messageUserId = data["userId"] as? String
                val messageText = data["text"] as? String
                
                Log.d(TAG, "🧹 Message analysé")
                // Log.d(TAG, "🧹   userId: [MASKED]")
                // Log.d(TAG, "🧹   text: [MASKED]")
                // Log.d(TAG, "🧹   isCurrentUser: [MASKED]")
            }
            
            Log.d(TAG, "✅ TERMINÉ: Analyse des messages (aucune correction automatique pour sécurité)")
            Log.d(TAG, "💡 CONSEIL: Vérifiez les logs pour identifier les messages avec le mauvais userId")
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage messages: ${e.message}")
            Result.failure(e)
        }
    }

    // MARK: - Génération de Questions

    /**
     * 📅 Génération de la question d'aujourd'hui si nécessaire
     * Utilise la Cloud Function Firebase existante
     */
    private suspend fun generateTodayQuestionIfNeeded(coupleId: String) {
        try {
            val settings = _currentSettings.value
            if (settings == null) {
                Log.w(TAG, "⚠️ Settings non disponibles pour génération question")
                return
            }

            val expectedDay = settings.calculateExpectedDay()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // Vérifier si question existe déjà pour aujourd'hui
            val existingQuestion = _currentQuestion.value
            if (existingQuestion?.scheduledDate == today) {
                Log.d(TAG, "ℹ️ Question d'aujourd'hui déjà existante")
                return
            }

            Log.d(TAG, "🔄 Génération question jour $expectedDay...")

            // 🔑 APPEL CLOUD FUNCTION
            val data = hashMapOf(
                "coupleId" to coupleId,
                "userId" to currentUserId,
                "questionDay" to expectedDay,
                "timezone" to TimeZone.getDefault().id
            )

            val result = functions.getHttpsCallable("generateDailyQuestion")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val resultData = result.data as? Map<String, Any>
            val success = resultData?.get("success") as? Boolean ?: false
            val message = resultData?.get("message") as? String ?: ""

            if (success) {
                Log.d(TAG, "✅ Question générée avec succès: $message")
                
                // 🔔 NOTIFICATION NOUVELLE QUESTION
                val question = _currentQuestion.value
                question?.let { q ->
                    val questionText = q.getLocalizedText(context)
                    notificationService.showNewQuestionNotification(
                        questionText = questionText,
                        questionDay = expectedDay
                    )
                }
            } else {
                Log.e(TAG, "❌ Erreur génération question: $message")
                _errorMessage.value = "Erreur génération: $message"
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur génération question: ${e.message}")
            _errorMessage.value = "Erreur lors de la génération: ${e.message}"
        }
    }

    /**
     * 🔄 Force la regénération d'une question (retry)
     */
    suspend fun regenerateCurrentQuestion(): Result<Unit> {
        return try {
            val coupleId = this.currentCoupleId
                ?: return Result.failure(Exception("Couple ID manquant"))

            _isLoading.value = true
            _errorMessage.value = null

            generateTodayQuestionIfNeeded(coupleId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur regénération: ${e.message}")
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    // MARK: - Gestion des Réponses

    /**
     * 📝 Soumission d'une réponse dans le chat
     * Compatible avec l'architecture iOS (même structure Firestore)
     */
    suspend fun submitResponse(text: String): Result<Unit> {
        return try {
            // 🔑 GESTION MODE INVITÉ: Utiliser AppState si Firebase Auth null
            val firebaseUser = auth.currentUser
            val appStateUser = com.love2loveapp.AppDelegate.appState.currentUser.value
            
            val userId: String
            val userName: String
            
            if (firebaseUser != null) {
                // Mode connecté (Google, etc.)
                userId = firebaseUser.uid
                userName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                    ?: appStateUser?.name?.takeIf { it.isNotBlank() }
                    ?: "Utilisateur Firebase"
                Log.d(TAG, "🔥 Mode Firebase Auth: [USER_MASKED]")
            } else if (appStateUser != null) {
                // Mode invité
                userId = appStateUser.id
                userName = appStateUser.name
                Log.d(TAG, "👤 Mode invité: [USER_MASKED]")
            } else {
                return Result.failure(Exception("Aucun utilisateur disponible"))
            }

            val question = _currentQuestion.value
                ?: return Result.failure(Exception("Aucune question active"))

            Log.d(TAG, "📝 Soumission réponse: [CONTENT_MASKED]")
            
            // 🔐 VALIDATION DONNÉES OBLIGATOIRES
            if (question.id.isBlank() || text.trim().isBlank() || userName.isBlank() || userId.isBlank()) {
                val errorMsg = "Données manquantes: questionId=[MASKED], text=[MASKED], userName=[MASKED], userId=[MASKED]"
                Log.e(TAG, "❌ $errorMsg")
                return Result.failure(Exception("Données manquantes"))
            }

            // 🔑 UTILISER LA MÊME CLOUD FUNCTION QUE iOS POUR COMPATIBILITÉ CROSS-PLATFORM            
            val data = hashMapOf(
                "questionId" to question.id,
                "responseText" to text.trim(),
                "userName" to userName,
                "userId" to userId  // Ajouter userId explicitement pour compatibilité mode invité
            )
            
            Log.d(TAG, "📤 Données envoyées à Cloud Function: [DATA_MASKED]")
            
            val result = functions.getHttpsCallable("submitDailyQuestionResponse")
                .call(data)
                .await()
                
            // Vérifier le succès
            val cloudResponse = result.data as? Map<*, *>
            if (cloudResponse?.get("success") != true) {
                throw Exception("Cloud Function échec: $cloudResponse")
            }

            Log.d(TAG, "✅ Réponse soumise avec succès")
            
            // 📊 Analytics optionnel
            // analyticsService.logEvent("daily_question_response_submitted")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur soumission réponse: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 🔔 Gestion notifications nouveaux messages
     */
    private fun handleNewMessageNotification(responses: List<QuestionResponse>) {
        val currentUserId = this.currentUserId ?: return
        
        // Trouver le dernier message qui n'est pas de nous
        val lastResponse = responses.lastOrNull { it.userId != currentUserId }
        
        if (lastResponse != null) { // 🔑 REMOVED isRecent() restriction
            // TODO: Implémenter système anti-spam plus intelligent
            Log.d(TAG, "🔔 Nouveau message de ${lastResponse.userName}: ${lastResponse.preview}")
            
            // 🔥 NOTIFICATION PUSH
            val currentQuestion = _currentQuestion.value
            val questionText = currentQuestion?.getLocalizedText(context)
            
            notificationService.showNewMessageNotification(
                response = lastResponse,
                partnerName = lastResponse.userName,
                questionText = questionText
            )
        }
    }

    // MARK: - Settings et Préférences

    /**
     * 🔧 Chargement/création des settings couple
     * Compatible avec l'architecture iOS - crée les settings automatiquement
     */
    private suspend fun loadOrCreateSettings(coupleId: String) {
        try {
            val settingsDoc = firestore.collection(COLLECTION_SETTINGS)
                .document(coupleId)
                .get()
                .await()

            if (settingsDoc.exists()) {
                val settings = DailyQuestionSettings.fromFirestore(settingsDoc)
                _currentSettings.value = settings
                Log.d(TAG, "✅ Settings existants chargés")
            } else {
                Log.d(TAG, "🔧 Création nouveaux settings pour couple: $coupleId")
                
                // 🔑 CRÉER NOUVEAUX SETTINGS (comme dans la version iOS)
                val now = Date()
                val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                calendar.time = now
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startDate = calendar.time
                
                val newSettings = mapOf(
                    "coupleId" to coupleId,
                    "startDate" to com.google.firebase.Timestamp(startDate),
                    "currentDay" to 1,
                    "timezone" to java.util.TimeZone.getDefault().id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "isActive" to true
                )
                
                // Sauvegarder dans Firestore
                firestore.collection(COLLECTION_SETTINGS)
                    .document(coupleId)
                    .set(newSettings)
                    .await()
                
                // Créer l'objet settings local
                val settings = DailyQuestionSettings(
                    coupleId = coupleId,
                    startDate = com.google.firebase.Timestamp(startDate),
                    currentDay = 1,
                    timezone = java.util.TimeZone.getDefault().id,
                    createdAt = com.google.firebase.Timestamp(now),
                    updatedAt = com.google.firebase.Timestamp(now),
                    isActive = true
                )
                
                _currentSettings.value = settings
                Log.d(TAG, "✅ Nouveaux settings créés et chargés")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement/création settings: ${e.message}")
            _errorMessage.value = "Erreur lors de l'initialisation: ${e.message}"
        }
    }

    /**
     * 📖 Marquer l'intro comme vue
     */
    fun markIntroAsSeen() {
        val userId = currentUserId ?: return
        val key = "daily_question_intro_seen_$userId"
        
        // Sauvegarder dans SharedPreferences
        val sharedPrefs = context.getSharedPreferences("daily_question_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(key, true).apply()
        
        _hasSeenIntro.value = true
        Log.d(TAG, "✅ Intro marquée comme vue")
    }

    /**
     * 📖 Chargement status intro
     */
    private fun loadIntroStatus() {
        val userId = currentUserId ?: return
        val key = "daily_question_intro_seen_$userId"
        
        val sharedPrefs = context.getSharedPreferences("daily_question_prefs", Context.MODE_PRIVATE)
        val hasSeenIntro = sharedPrefs.getBoolean(key, false)
        
        _hasSeenIntro.value = hasSeenIntro
        Log.d(TAG, "📖 Status intro chargé: $hasSeenIntro")
    }

    /**
     * 🔄 Reset des erreurs
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 📊 Informations de debug
     */
    fun getDebugInfo(): String {
        return """
            📊 DEBUG DailyQuestionRepository:
            - Couple ID: ${currentCoupleId ?: "Non défini"}
            - User ID: ${currentUserId ?: "Non défini"} 
            - Question actuelle: ${_currentQuestion.value?.questionKey ?: "Aucune"}
            - Réponses: ${_responses.value.size}
            - Settings: ${_currentSettings.value?.currentDay ?: "Non chargé"}
            - A vu intro: ${_hasSeenIntro.value}
            - En chargement: ${_isLoading.value}
            - Erreur: ${_errorMessage.value ?: "Aucune"}
        """.trimIndent()
    }

    /**
     * 🧹 Nettoyage des ressources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage DailyQuestionRepository")
        
        questionListener?.remove()
        responsesListener?.remove() 
        settingsListener?.remove()
        
        questionListener = null
        responsesListener = null
        settingsListener = null
        
        // 🔔 Nettoyer notifications
        notificationService.cancelAllNotifications()
    }
}
