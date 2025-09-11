package com.love2loveapp.services.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Android/Kotlin port of DailyQuestionService (Swift).
 * - Uses Kotlin coroutines + StateFlow
 * - Firestore/Functions/Auth parity with iOS
 * - Realm cache calls are abstracted via QuestionCacheManager (provide your own impl)
 * - Local notifications via NotificationCompat (immediate only, like iOS schedule removed)
 * - Localized strings via Context.getStringByName(key)
 */
class DailyQuestionService private constructor(
    private val appContext: Context
) {
    companion object {
        @Volatile private var INSTANCE: DailyQuestionService? = null
        fun getInstance(context: Context): DailyQuestionService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DailyQuestionService(context.applicationContext).also { INSTANCE = it }
            }

        const val NOTIF_CHANNEL_MESSAGES = "love2love_messages"
    }

    // Firebase
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Observables
    private val _currentQuestion = MutableStateFlow<DailyQuestion?>(null)
    val currentQuestion: StateFlow<DailyQuestion?> = _currentQuestion.asStateFlow()

    private val _questionHistory = MutableStateFlow<List<DailyQuestion>>(emptyList())
    val questionHistory: StateFlow<List<DailyQuestion>> = _questionHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _allQuestionsExhausted = MutableStateFlow(false)
    val allQuestionsExhausted: StateFlow<Boolean> = _allQuestionsExhausted.asStateFlow()

    private val _currentSettings = MutableStateFlow<DailyQuestionSettings?>(null)
    val currentSettings: StateFlow<DailyQuestionSettings?> = _currentSettings.asStateFlow()

    // Listeners
    private var questionListener: ListenerRegistration? = null
    private var responsesListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null

    // Config cache
    private var isConfigured: Boolean = false
    private var currentCoupleId: String? = null

    // AppState-like holder (minimal)
    data class AppUser(val uid: String, val name: String?, val partnerId: String?)
    data class AppState(val currentUser: AppUser?)

    fun configure(appState: AppState) {
        val newCoupleId = generateCoupleId(appState)
        if (isConfigured && currentCoupleId == newCoupleId) {
            log("⚡ DailyQuestionService: déjà configuré pour ce couple – pas de reconfiguration")
            return
        }
        log("🔄 DailyQuestionService: configuration…")
        this.currentCoupleId = newCoupleId
        this.isConfigured = true

        ensureNotificationChannel()
        saveUserLanguageToFirebase()
        setupListeners(appState)
    }

    private fun generateCoupleId(appState: AppState): String? {
        val currentUser = auth.currentUser ?: return null
        val partnerId = appState.currentUser?.partnerId?.takeIf { it.isNotBlank() } ?: return null
        return listOf(currentUser.uid, partnerId).sorted().joinToString("_")
    }

    private fun setupListeners(appState: AppState) {
        val currentUser = auth.currentUser ?: run {
            log("🔥 DailyQuestionService: aucun utilisateur connecté")
            return
        }
        val partnerId = appState.currentUser?.partnerId?.takeIf { it.isNotBlank() } ?: run {
            log("🔥 DailyQuestionService: aucun partenaire configuré")
            return
        }
        val coupleId = listOf(currentUser.uid, partnerId).sorted().joinToString("_")
        log("🔥 DailyQuestionService: écoute Firestore pour couple configuré")

        scope.launch { loadFromCacheFirst(coupleId) }
        setupSettingsListener(coupleId)
        setupQuestionsListener(coupleId)
    }

    // ————————————————————————————————————————————————————————————————
    // Cache-first
    private suspend fun loadFromCacheFirst(coupleId: String) {
        val cached = QuestionCacheManager.getCachedDailyQuestions(coupleId, limit = 5)
        if (cached.isNotEmpty()) {
            log("⚡ Chargement immédiat depuis cache – ${cached.size} questions")
            _questionHistory.value = cached
            if (_currentQuestion.value == null) {
                _currentQuestion.value = cached.first()
                _isLoading.value = false
            }
        }
    }

    // ————————————————————————————————————————————————————————————————
    // Settings listener
    private fun setupSettingsListener(coupleId: String) {
        settingsListener?.remove()
        settingsListener = db.collection("dailyQuestionSettings")
            .document(coupleId)
            .addSnapshotListener { snap, err ->
                scope.launch {
                    if (err != null) {
                        log("❌ Erreur settings: ${err.message}")
                        return@launch
                    }
                    val data = snap?.data() ?: return@launch
                    val settings = createSettingsFromFirestore(data, coupleId)
                    val old = _currentSettings.value?.currentDay ?: 0
                    _currentSettings.value = settings
                    if (old != settings.currentDay) {
                        log("🚨 currentDay changé: $old → ${settings.currentDay}")
                    } else {
                        log("⚠️ currentDay inchangé: ${settings.currentDay}")
                    }
                }
            }
    }

    private fun createSettingsFromFirestore(data: Map<String, Any>, coupleId: String): DailyQuestionSettings {
        val startDate = (data["startDate"] as? Timestamp)?.toDate() ?: Date()
        val timezone = data["timezone"] as? String ?: "Europe/Paris"
        val currentDay = (data["currentDay"] as? Number)?.toInt() ?: 1
        val lastVisit = (data["lastVisitDate"] as? Timestamp)?.toDate()
        val nextScheduledDate = (data["nextScheduledDate"] as? String)
        return DailyQuestionSettings(
            coupleId = coupleId,
            startDate = startDate,
            timezone = timezone,
            currentDay = currentDay,
            lastVisitDate = lastVisit,
            nextScheduledDate = nextScheduledDate
        )
    }

    // ————————————————————————————————————————————————————————————————
    // Questions listener
    private fun setupQuestionsListener(coupleId: String) {
        questionListener?.remove()
        questionListener = db.collection("dailyQuestions")
            .whereEqualTo("coupleId", coupleId)
            .orderBy("scheduledDateTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snap, err ->
                scope.launch {
                    log("🎯 Questions listener déclenché")
                    if (err != null) {
                        log("❌ Erreur questions: ${err.message}")
                        loadFromRealmCache(coupleId)
                        return@launch
                    }
                    val docs = snap?.documents.orEmpty()
                    if (docs.isEmpty()) {
                        log("⚠️ Aucun document – fallback cache Realm")
                        loadFromRealmCache(coupleId)
                        return@launch
                    }
                    val questions = docs.mapNotNullIndexed { index, d ->
                        val q = d.toDailyQuestion(index)
                        if (q != null) {
                            val withResponses = loadResponsesForQuestion(q)
                            QuestionCacheManager.cacheDailyQuestion(withResponses)
                            withResponses
                        } else null
                    }
                    _questionHistory.value = questions

                    val newQ = questions.firstOrNull()
                    if (_currentQuestion.value?.id != newQ?.id) {
                        log("🔄 nouvelle question détectée via listener")
                        _currentQuestion.value = newQ
                    } else {
                        log("🔄 question déjà à jour (optimisé)")
                    }

                    newQ?.let { setupResponsesListener(it) }
                }
            }
    }

    private fun DocumentSnapshot.toDailyQuestion(index: Int): DailyQuestion? = try {
        val id = id
        val data = data ?: return null
        val questionKey = data["questionKey"] as? String ?: return null
        val questionDay = (data["questionDay"] as? Number)?.toInt() ?: 0
        val scheduledDate = data["scheduledDate"] as? String
        val scheduledDateTime = (data["scheduledDateTime"] as? Timestamp)?.toDate()
        val status = (data["status"] as? String)?.let { QuestionStatus.from(it) } ?: QuestionStatus.PENDING
        val createdAt = (data["createdAt"] as? Timestamp)?.toDate()
        val updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()
        val timezone = data["timezone"] as? String
        DailyQuestion(
            id = id,
            coupleId = data["coupleId"] as? String ?: "",
            questionKey = questionKey,
            questionDay = questionDay,
            scheduledDate = scheduledDate,
            scheduledDateTime = scheduledDateTime,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            timezone = timezone,
            responsesFromSubcollection = emptyList(),
            legacyResponses = emptyMap()
        ).also {
            log("📝 Doc ${index + 1}: id=${id}, key=${questionKey}, day=${questionDay}, date=${scheduledDate}")
        }
    } catch (t: Throwable) {
        log("❌ Erreur décodage question: ${t.message}")
        null
    }

    // ————————————————————————————————————————————————————————————————
    // Subcollection responses
    private suspend fun loadResponsesForQuestion(q: DailyQuestion): DailyQuestion {
        return try {
            val snap = db.collection("dailyQuestions").document(q.id)
                .collection("responses")
                .orderBy("respondedAt")
                .get()
                .await()
            val responses = snap.documents.mapNotNull { d ->
                val data = d.data ?: return@mapNotNull null
                QuestionResponse(
                    id = data["id"] as? String ?: d.id,
                    userId = data["userId"] as? String ?: return@mapNotNull null,
                    userName = data["userName"] as? String ?: "",
                    text = data["text"] as? String ?: "",
                    respondedAt = (data["respondedAt"] as? Timestamp)?.toDate(),
                    status = (data["status"] as? String)?.let { ResponseStatus.from(it) } ?: ResponseStatus.ANSWERED,
                    isReadByPartner = data["isReadByPartner"] as? Boolean ?: false
                )
            }
            q.copy(responsesFromSubcollection = responses)
        } catch (t: Throwable) {
            log("❌ Erreur chargement réponses: ${t.message}")
            q
        }
    }

    private fun setupResponsesListener(question: DailyQuestion) {
        responsesListener?.remove()
        responsesListener = db.collection("dailyQuestions").document(question.id)
            .collection("responses")
            .orderBy("respondedAt")
            .addSnapshotListener { snap, err ->
                scope.launch {
                    if (err != null) {
                        log("❌ Erreur listener réponses: ${err.message}")
                        return@launch
                    }
                    val docs = snap?.documents.orEmpty()
                    val responses = docs.mapNotNull { d ->
                        val data = d.data ?: return@mapNotNull null
                        QuestionResponse(
                            id = data["id"] as? String ?: d.id,
                            userId = data["userId"] as? String ?: return@mapNotNull null,
                            userName = data["userName"] as? String ?: "",
                            text = data["text"] as? String ?: "",
                            respondedAt = (data["respondedAt"] as? Timestamp)?.toDate(),
                            status = (data["status"] as? String)?.let { ResponseStatus.from(it) } ?: ResponseStatus.ANSWERED,
                            isReadByPartner = data["isReadByPartner"] as? Boolean ?: false
                        )
                    }
                    val prevCount = _currentQuestion.value?.responsesFromSubcollection?.size ?: 0
                    val updated = _currentQuestion.value?.copy(responsesFromSubcollection = responses)
                    if (updated != null) {
                        _currentQuestion.value = updated
                        _questionHistory.value = _questionHistory.value.map { if (it.id == updated.id) updated else it }
                    }
                    if (responses.size > prevCount && responses.isNotEmpty()) {
                        val newResponse = responses.last()
                        notifyNewMessage(question, newResponse)
                    }
                }
            }
    }

    // ————————————————————————————————————————————————————————————————
    // Public actions
    fun generateTodaysQuestion() {
        val user = auth.currentUser ?: run { log("❌ Aucun utilisateur connecté"); return }
        val coupleId = currentCoupleId ?: run { log("❌ Aucun coupleId"); return }

        scope.launch {
            logTimezoneDebug()
            // Ensure settings exist (callable)
            if (_currentSettings.value == null) {
                try {
                    val res = functions.getHttpsCallable("getOrCreateDailyQuestionSettings")
                        .call(mapOf(
                            "coupleId" to coupleId,
                            "timezone" to TimeZone.getDefault().id
                        ))
                        .await()
                    val data = res.data as? Map<*, *> ?: emptyMap<String, Any>()
                    if ((data["success"] as? Boolean) == true) {
                        // settings listener will update state
                        // brief wait not necessary here since we reactively listen
                        log("✅ Settings créés/récupérés via Cloud Function")
                    } else {
                        log("❌ Réponse inattendue getOrCreateDailyQuestionSettings: $data")
                    }
                } catch (t: Throwable) {
                    log("❌ Erreur getOrCreateDailyQuestionSettings: ${t.message}")
                    return@launch
                }
            }

            val finalSettings = _currentSettings.value ?: run {
                log("❌ Settings toujours indisponibles")
                return@launch
            }

            val expectedDay = DailyQuestionGenerator.calculateCurrentQuestionDay(finalSettings) ?: finalSettings.currentDay
            if (!DailyQuestionGenerator.shouldShowNewQuestion(finalSettings)) {
                log("ℹ️ Pas encore l'heure de la nouvelle question – annulation")
                return@launch
            }

            _isLoading.value = true
            try {
                val payload = mapOf(
                    "coupleId" to coupleId,
                    "userId" to user.uid,
                    "questionDay" to expectedDay,
                    "timezone" to TimeZone.getDefault().id
                )
                val result = functions.getHttpsCallable("generateDailyQuestion").call(payload).await()
                val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
                if ((data["success"] as? Boolean) == true) {
                    val q = (data["question"] as? Map<*, *>)
                    val questionKey = (q?.get("questionKey") as? String) ?: ""
                    val questionDay = (q?.get("questionDay") as? Number)?.toInt() ?: expectedDay
                    val id = (q?.get("id") as? String) ?: "${coupleId}_${todayString()}"
                    val statusRaw = (q?.get("status") as? String) ?: "pending"
                    val immediate = DailyQuestion(
                        id = id,
                        coupleId = coupleId,
                        questionKey = questionKey,
                        questionDay = questionDay,
                        scheduledDate = q?.get("scheduledDate") as? String,
                        scheduledDateTime = Date(),
                        status = QuestionStatus.from(statusRaw),
                        createdAt = Date(),
                        updatedAt = Date(),
                        timezone = TimeZone.getDefault().id,
                        responsesFromSubcollection = emptyList(),
                        legacyResponses = emptyMap()
                    )
                    _currentQuestion.value = immediate
                    QuestionCacheManager.cacheDailyQuestion(immediate)
                    // Persist currentDay back to Firestore (best-effort)
                    db.collection("dailyQuestionSettings").document(coupleId)
                        .update(mapOf(
                            "currentDay" to expectedDay,
                            "lastVisitDate" to Timestamp.now()
                        ))
                } else {
                    log("❌ Échec génération question – $data")
                }
            } catch (t: Throwable) {
                log("❌ Erreur génération: ${t.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun submitResponse(responseText: String, userName: String?): Boolean {
        val current = _currentQuestion.value ?: return false
        val user = auth.currentUser ?: return false
        scope.launch {
            try {
                val res = functions.getHttpsCallable("submitDailyQuestionResponse")
                    .call(mapOf(
                        "questionId" to current.id,
                        "responseText" to responseText,
                        "userName" to (userName ?: "Utilisateur")
                    ))
                    .await()
                val data = res.data as? Map<*, *> ?: emptyMap<String, Any>()
                if ((data["success"] as? Boolean) == true) {
                    log("✅ Réponse soumise avec succès")
                } else {
                    log("❌ Échec soumission réponse: $data")
                }
            } catch (t: Throwable) {
                log("❌ Erreur soumission: ${t.message}")
            }
        }
        return true
    }

    fun migrateTodaysQuestionToSubcollections() {
        val coupleId = currentCoupleId ?: return
        scope.launch {
            try {
                val res = functions.getHttpsCallable("migrateDailyQuestionResponses")
                    .call(mapOf("coupleId" to coupleId))
                    .await()
                val data = res.data as? Map<*, *> ?: emptyMap<String, Any>()
                if ((data["success"] as? Boolean) == true) {
                    log("✅ Migration réussie")
                } else log("❌ Échec migration: $data")
            } catch (t: Throwable) {
                log("❌ Erreur migration: ${t.message}")
            }
        }
    }

    fun refreshCurrentQuestion(appState: AppState) { setupListeners(appState) }

    // ————————————————————————————————————————————————————————————————
    // Timezone optimization (cache-first)
    fun optimizedDailyQuestionCheck() {
        scope.launch {
            _isOptimizing.value = true
            try {
                val coupleId = currentCoupleId ?: run { log("❌ Pas de coupleId"); return@launch }

                log("\n🌍 === TIMEZONE OPTIMIZATION START ===")
                val cached = QuestionCacheManager.getCachedDailyQuestions(coupleId, 5)
                if (cached.any { it.scheduledDate == todayString() }) {
                    val q = cached.first()
                    _currentQuestion.value = q
                    log("✅ Question d'aujourd'hui déjà en cache – Firebase évité")
                    return@launch
                }
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                val critical = isCriticalTimeForFirebaseCheck(hour, minute)
                if (!critical && cached.isNotEmpty()) {
                    _currentQuestion.value = cached.first()
                    log("⏭️ Heure non critique – utilisation cache seulement")
                    return@launch
                }
                generateTodaysQuestion()
            } finally {
                _isOptimizing.value = false
                log("🌍 === TIMEZONE OPTIMIZATION END ===\n")
            }
        }
    }

    private fun isCriticalTimeForFirebaseCheck(hour: Int, minute: Int): Boolean {
        if (hour == 0 && minute <= 5) return true
        if (hour == 21 && minute <= 5) return true
        return false
    }

    // ————————————————————————————————————————————————————————————————
    // Notifications
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications Love2Love (nouveaux messages)" }
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun notifyNewMessage(question: DailyQuestion, newResponse: QuestionResponse) {
        val currentUid = auth.currentUser?.uid ?: return
        if (newResponse.userId == currentUid) {
            log("🔔 Message de l'utilisateur actuel – pas de notification")
            return
        }
        val identifier = ("new_message_${question.id}_${newResponse.id}").hashCode()
        val title = newResponse.userName
        val body = newResponse.text

        val notif = NotificationCompat.Builder(appContext, NOTIF_CHANNEL_MESSAGES)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(identifier, notif)
    }

    fun clearAllNotifications() {
        NotificationManagerCompat.from(appContext).cancelAll()
    }

    // ————————————————————————————————————————————————————————————————
    // Realm cache fallbacks (provide implementation)
    private suspend fun loadFromRealmCache(coupleId: String) {
        val cached = QuestionCacheManager.getCachedDailyQuestions(coupleId, limit = 10)
        if (cached.isNotEmpty()) {
            _questionHistory.value = cached
            _currentQuestion.value = cached.first()
            log("✅ ${cached.size} questions chargées depuis le cache Realm")
        } else {
            log("❌ Aucune question dans le cache Realm")
        }
    }

    // ————————————————————————————————————————————————————————————————
    // Language → Firestore
    private fun saveUserLanguageToFirebase() {
        val current = auth.currentUser ?: return
        val language = Locale.getDefault().language.ifBlank { "fr" }
        db.collection("users").document(current.uid)
            .update(mapOf(
                "languageCode" to language,
                "languageUpdatedAt" to Timestamp.now()
            ))
            .addOnSuccessListener { log("✅ Langue $language sauvegardée") }
            .addOnFailureListener { log("❌ Erreur sauvegarde langue: ${it.message}") }
    }

    // ————————————————————————————————————————————————————————————————
    // Helpers
    private fun todayString(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val y = cal.get(Calendar.YEAR)
        val m = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val d = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return "$y-$m-$d"
    }

    private fun log(msg: String) { println(msg) }
}

// ————————————————————————————————————————————————————————————————
// Data models

data class DailyQuestion(
    val id: String,
    val coupleId: String,
    val questionKey: String,
    val questionDay: Int,
    val scheduledDate: String? = null,               // YYYY-MM-DD
    val scheduledDateTime: Date? = null,
    val status: QuestionStatus = QuestionStatus.PENDING,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
    val timezone: String? = null,
    val responsesFromSubcollection: List<QuestionResponse> = emptyList(),
    val legacyResponses: Map<String, QuestionResponse> = emptyMap()
) {
    val responses: Map<String, QuestionResponse> get() = legacyResponses
    val isExpired: Boolean get() = false // align with iOS if needed
    val bothResponded: Boolean get() = responsesFromSubcollection.size >= 2

    fun getCurrentUserResponse(userId: String): QuestionResponse? =
        responses[userId]

    fun getPartnerResponse(excludingUserId: String): QuestionResponse? =
        responses.values.firstOrNull { it.userId != excludingUserId }

    fun canUserRespond(userId: String): Boolean =
        (responses[userId]?.status != ResponseStatus.ANSWERED) && !isExpired

    fun shouldShowWaitingMessage(forUserId: String): Boolean =
        responses[forUserId]?.status == ResponseStatus.ANSWERED && !bothResponded
}

data class QuestionResponse(
    val id: String,
    val userId: String,
    val userName: String,
    val text: String,
    val respondedAt: Date? = null,
    val status: ResponseStatus = ResponseStatus.ANSWERED,
    val isReadByPartner: Boolean = false
)

enum class QuestionStatus(val raw: String) {
    PENDING("pending"), ACTIVE("active"), COMPLETED("completed");
    companion object { fun from(s: String) = entries.firstOrNull { it.raw.equals(s, true) } ?: PENDING }
}

enum class ResponseStatus(val raw: String) {
    ANSWERED("answered"), PENDING("pending");
    companion object { fun from(s: String) = entries.firstOrNull { it.raw.equals(s, true) } ?: ANSWERED }
}

data class DailyQuestionSettings(
    val coupleId: String,
    val startDate: Date,
    val timezone: String,
    var currentDay: Int = 1,
    var lastVisitDate: Date? = null,
    val nextScheduledDate: String? = null
)

// ————————————————————————————————————————————————————————————————
// Generator parity with Swift (UTC-only logic)
object DailyQuestionGenerator {
    fun calculateCurrentQuestionDay(settings: DailyQuestionSettings, now: Date = Date()): Int? {
        val startUTC = startOfDayUTC(settings.startDate)
        val nowUTC = startOfDayUTC(now)
        val days = ((nowUTC.time - startUTC.time) / (1000L * 3600L * 24L)).toInt()
        val expected = days + 1
        return expected
    }

    fun shouldShowNewQuestion(settings: DailyQuestionSettings, now: Date = Date()): Boolean {
        val expected = calculateCurrentQuestionDay(settings, now) ?: settings.currentDay
        return expected >= settings.currentDay
    }

    private fun startOfDayUTC(d: Date): Date {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = d
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
}

// ————————————————————————————————————————————————————————————————
// Realm cache abstraction (provide your own implementation)
object QuestionCacheManager {
    fun getCachedDailyQuestions(coupleId: String, limit: Int): List<DailyQuestion> {
        // TODO: implement with Realm Kotlin (io.realm.kotlin)
        return emptyList()
    }
    fun cacheDailyQuestion(question: DailyQuestion) {
        // TODO: persist to Realm
    }
}

// ————————————————————————————————————————————————————————————————
// Android localization helpers
fun Context.getStringByName(name: String, fallback: String = name): String {
    val resId = resources.getIdentifier(name, "string", packageName)
    return if (resId != 0) getString(resId) else fallback
}

/*
================================================================================
strings.xml (exemple) – place this in res/values/strings.xml

<resources>
    <string name="daily_question_1">Quel est ton plus beau souvenir avec moi ?</string>
    <string name="daily_question_2">Qu&#39;attends-tu de nous ce mois-ci ?</string>
    <!-- … etc. Toutes les clés questionKey/daily_challenge_* mappées ici -->
</resources>

Usage côté UI (Compose):

val context = LocalContext.current
val question = viewModel.currentQuestion.collectAsState().value
val text = question?.questionKey?.let { context.getStringByName(it) } ?: ""
Text(text)

Equivalent impératif non-Compose:
val text = context.getStringByName(question.questionKey)
================================================================================
*/
