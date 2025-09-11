package com.love2loveapp.model

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.love2loveapp.R
import com.love2loveapp.model.AppConstants
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * ================================================================
 * Kotlin port (complet) des modèles "Daily Questions" depuis Swift
 * - Localisation via strings.xml (plus de .xcstrings)
 * - Interop Firestore (Timestamp <-> Date) et Auth
 * - Calculs de jour en UTC pour éviter les soucis de fuseaux
 * - Support sous-collections (responses) + compatibilité legacy
 * ================================================================
 */

// --- Service de chiffrement (placeholder à brancher) ---
object LocationEncryptionService {
    /**
     * Prépare les champs chiffrés à fusionner dans le document Firestore.
     * Exemple: { text_enc: ..., iv: ..., alg: ... }
     */
    fun processMessageForStorage(plainText: String): Map<String, Any> {
        // TODO: Brancher votre chiffrement hybride réel (AES/GCM + enveloppe)
        return mapOf("text" to plainText) // ⚠️ DEV ONLY (en clair)
    }

    /**
     * Lit/déchiffre le texte à partir d'une map Firestore.
     */
    fun readMessageFromFirestore(data: Map<String, Any?>): String? {
        // TODO: Déchiffrer selon votre schéma
        return (data["text"] as? String) ?: ""
    }
}

// --- Constantes & helpers date ---
private const val TAG = AppConstants.Tags.DAILY_QUESTION
private const val YMD = AppConstants.DATE_FORMAT_YMD

fun Calendar.dateFromString(dateString: String): Date? = try {
    SimpleDateFormat(AppConstants.DATE_FORMAT_YMD, Locale.getDefault()).parse(dateString)
} catch (_: Exception) { null }

fun Calendar.stringFromDate(date: Date): String =
    SimpleDateFormat(AppConstants.DATE_FORMAT_YMD, Locale.getDefault()).format(date)

val Date.dailyQuestionDateString: String
    get() = Calendar.getInstance().stringFromDate(this)

fun Date.scheduledQuestionTime21hLocal(): Date {
    val cal = Calendar.getInstance().apply {
        time = this@scheduledQuestionTime21hLocal
        set(Calendar.HOUR_OF_DAY, AppConstants.DEFAULT_NOTIFICATION_HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.time
}

fun Calendar.setToStartOfDay() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

// ================================
// Daily Question Settings
// ================================

data class DailyQuestionSettings(
    val coupleId: String,
    val startDate: Date,        // Date de première visite (00:00)
    val timezone: String,       // ID de fuseau (ex: "Europe/Paris")
    var currentDay: Int = 1,    // Jour actuel du cycle
    val createdAt: Date = Date(),
    var lastVisitDate: Date? = null
) {
    val id: String get() = coupleId
}

// ================================
// Question Response (modèle sous-collection)
// ================================

data class QuestionResponse(
    var id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userName: String,
    val text: String,
    var respondedAt: Date = Date(),
    var status: ResponseStatus = ResponseStatus.ANSWERED,
    var isReadByPartner: Boolean = false
) {
    companion object {
        fun createEncrypted(
            userId: String,
            userName: String,
            text: String,
            status: ResponseStatus = ResponseStatus.ANSWERED
        ): QuestionResponse = QuestionResponse(
            userId = userId,
            userName = userName,
            text = text,
            status = status
        )

        fun fromFirestoreMap(data: Map<String, Any?>): QuestionResponse? {
            val id = data["id"] as? String ?: return null
            val userId = data["userId"] as? String ?: return null
            val userName = data["userName"] as? String ?: return null

            // 🔐 Déchiffrement hybride du texte
            val decryptedText = LocationEncryptionService.readMessageFromFirestore(data) ?: ""

            val response = QuestionResponse(
                id = id,
                userId = userId,
                userName = userName,
                text = decryptedText
            )

            (data["respondedAt"] as? Timestamp)?.let { response.respondedAt = it.toDate() }

            response.status = (data["status"] as? String)?.let { ResponseStatus.fromRawValue(it) }
                ?: ResponseStatus.ANSWERED

            response.isReadByPartner = data["isReadByPartner"] as? Boolean ?: false

            return response
        }
    }

    fun toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "userId" to userId,
            "userName" to userName,
            "respondedAt" to Timestamp(respondedAt),
            "status" to status.rawValue,
            "isReadByPartner" to isReadByPartner
        )
        // 🔐 Champs chiffrés
        map.putAll(LocationEncryptionService.processMessageForStorage(text))
        return map
    }
}

// ================================
// Daily Question (agrégat + compatibilité legacy)
// ================================

data class DailyQuestion(
    val id: String,
    val coupleId: String,
    val questionKey: String,        // ex: "daily_question_12"
    val questionDay: Int,
    val scheduledDate: String,      // yyyy-MM-dd
    val scheduledDateTime: Date,
    var status: QuestionStatus,
    val createdAt: Date,
    var updatedAt: Date,
    val timezone: String,
    var responsesFromSubcollection: List<QuestionResponse> = emptyList(),
    var legacyResponses: Map<String, QuestionResponse> = emptyMap()
) {
    // Map unifiée (priorité sous-collection)
    val responses: Map<String, QuestionResponse>
        get() = if (responsesFromSubcollection.isNotEmpty())
            responsesFromSubcollection.associateBy { it.userId } else legacyResponses

    val responsesArray: List<QuestionResponse>
        get() = if (responsesFromSubcollection.isNotEmpty())
            responsesFromSubcollection.sortedBy { it.respondedAt }
        else legacyResponses.values.sortedBy { it.respondedAt }

    // Localisation via strings.xml
    fun localizedText(context: Context): String {
        // questionKey → @string/daily_question_X
        val resId = context.resources.getIdentifier(questionKey, "string", context.packageName)
        return if (resId != 0) context.getString(resId) else questionKey
    }

    val formattedDate: String
        get() = try {
            val df = SimpleDateFormat(AppConstants.DATE_FORMAT_YMD, Locale.getDefault())
            val date = df.parse(scheduledDate)!!
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
        } catch (_: Exception) { scheduledDate }

    val bothResponded: Boolean get() = responses.values.count { it.status == ResponseStatus.ANSWERED } >= 2
    val hasAnyResponse: Boolean get() = responses.values.any { it.status == ResponseStatus.ANSWERED }

    val isExpired: Boolean
        get() {
            val cal = Calendar.getInstance()
            val date = cal.dateFromString(scheduledDate) ?: Date()
            val limit = date.scheduledQuestionTime21hLocal()
            return Date().time - limit.time > AppConstants.NOTIFICATION_EXPIRY_HOURS * 60 * 60 * 1000
        }

    val currentUserResponse: QuestionResponse?
        get() = FirebaseAuth.getInstance().currentUser?.uid?.let { responses[it] }

    val partnerResponse: QuestionResponse?
        get() {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            return responses.values.firstOrNull { it.userId != currentUserId }
        }

    val canCurrentUserRespond: Boolean
        get() {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
            val my = responses[currentUserId]
            return my?.status != ResponseStatus.ANSWERED && !isExpired
        }

    val shouldShowWaitingMessage: Boolean
        get() {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
            return shouldShowWaitingMessageFor(currentUserId, null)
        }

    fun shouldUnlockChat(settings: DailyQuestionSettings?): Boolean {
        if (settings == null) return false
        return DailyQuestionGenerator.shouldShowNewQuestion(settings)
    }

    fun shouldShowWaitingMessageFor(userId: String, settings: DailyQuestionSettings? = null): Boolean {
        val my = responses[userId]
        val partner = responses.values.firstOrNull { it.userId != userId }

        val hasAnswered = my?.status == ResponseStatus.ANSWERED
        val partnerHasAnswered = partner?.status == ResponseStatus.ANSWERED

        // ✅ Si le partenaire a déjà répondu → pas de message d'attente (chat débloqué)
        if (partnerHasAnswered) return false

        val waitingForPartner = hasAnswered && !partnerHasAnswered
        val unlock = shouldUnlockChat(settings)
        return waitingForPartner && !isExpired && !unlock
    }

    // Helpers
    fun getUserResponse(userId: String): QuestionResponse? = responses[userId]
    fun hasUserResponded(userId: String): Boolean = responses[userId] != null
    fun getResponsesArray(): List<QuestionResponse> = responsesArray

    // Compatibilité/migration
    val shouldUseLegacyMode: Boolean get() = legacyResponses.isNotEmpty() && responsesFromSubcollection.isEmpty()
    val migrationVersion: String? get() = null

    // --- Firestore (de)serialization helpers ---
    fun toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "coupleId" to coupleId,
            "questionKey" to questionKey,
            "questionDay" to questionDay,
            "scheduledDate" to scheduledDate,
            "scheduledDateTime" to Timestamp(scheduledDateTime),
            "status" to status.rawValue,
            "createdAt" to Timestamp(createdAt),
            "updatedAt" to Timestamp(updatedAt),
            "timezone" to timezone
        )
        // Ne pas inclure les réponses (sous-collection). Garder legacy si pas migré.
        if (legacyResponses.isNotEmpty() && responsesFromSubcollection.isEmpty()) {
            val legacySerialized = legacyResponses.mapValues { (_, v) -> v.toFirestoreMap() }
            map["responses"] = legacySerialized
        }
        return map
    }

    companion object {
        fun fromFirestoreMap(data: Map<String, Any?>): DailyQuestion? = try {
            val id = data["id"] as? String ?: return null
            val coupleId = data["coupleId"] as? String ?: return null
            val questionKey = data["questionKey"] as? String ?: return null
            val questionDay = (data["questionDay"] as? Number)?.toInt() ?: return null
            val scheduledDate = data["scheduledDate"] as? String ?: return null

            val scheduledDateTime = when (val sdt = data["scheduledDateTime"]) {
                is Timestamp -> sdt.toDate()
                is Date -> sdt
                else -> Date()
            }
            val createdAt = when (val ca = data["createdAt"]) {
                is Timestamp -> ca.toDate()
                is Date -> ca
                else -> Date()
            }
            val updatedAt = when (val ua = data["updatedAt"]) {
                is Timestamp -> ua.toDate()
                is Date -> ua
                else -> Date()
            }

            val status = (data["status"] as? String)?.let { QuestionStatus.fromRawValue(it) }
                ?: QuestionStatus.PENDING
            val timezone = data["timezone"] as? String ?: AppConstants.DEFAULT_TIMEZONE

            // Legacy responses (optionnel)
            val legacyMap: Map<String, QuestionResponse> =
                (data["responses"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val qr = (v as? Map<*, *>)?.let { inner ->
                        @Suppress("UNCHECKED_CAST")
                        QuestionResponse.fromFirestoreMap(inner as Map<String, Any?>)
                    }
                    qr?.let { key to it }
                }?.toMap() ?: emptyMap()

            DailyQuestion(
                id = id,
                coupleId = coupleId,
                questionKey = questionKey,
                questionDay = questionDay,
                scheduledDate = scheduledDate,
                scheduledDateTime = scheduledDateTime,
                status = status,
                createdAt = createdAt,
                updatedAt = updatedAt,
                timezone = timezone,
                responsesFromSubcollection = emptyList(),
                legacyResponses = legacyMap
            )
        } catch (e: Exception) {
            Log.e(TAG, "fromFirestoreMap error", e)
            null
        }
    }
}

// ================================
// Enums (localisés via strings.xml)
// ================================

enum class QuestionStatus(val rawValue: String, @StringRes val resId: Int) {
    PENDING("pending", R.string.status_pending),
    ACTIVE("active", R.string.status_active),
    ONE_ANSWERED("one_answered", R.string.status_one_answered),
    BOTH_ANSWERED("both_answered", R.string.status_both_answered),
    EXPIRED("expired", R.string.status_expired),
    SKIPPED("skipped", R.string.status_skipped);

    fun displayName(context: Context): String = context.getString(resId)

    companion object {
        fun fromRawValue(value: String): QuestionStatus = values().find { it.rawValue == value } ?: PENDING
    }
}

enum class ResponseStatus(val rawValue: String, @StringRes val resId: Int) {
    WAITING("waiting", R.string.response_waiting),
    ANSWERED("answered", R.string.response_answered),
    SKIPPED("skipped", R.string.response_skipped);

    fun displayName(context: Context): String = context.getString(resId)

    companion object {
        fun fromRawValue(value: String): ResponseStatus = values().find { it.rawValue == value } ?: ANSWERED
    }
}

// ================================
// DailyQuestionGenerator (logique UTC + stats)
// ================================

object DailyQuestionGenerator {

    private fun utcCalendar(): Calendar = Calendar.getInstance(TimeZone.getTimeZone(AppConstants.UTC_TIMEZONE))

    fun getAvailableQuestionsCount(context: Context): Int {
        var count = 0
        for (i in 1..1000) {
            val key = "daily_question_$i"
            val resId = context.resources.getIdentifier(key, "string", context.packageName)
            if (resId != 0) count = i else break
        }
        Log.d(TAG, "📝 Questions dispo: $count")
        return count.coerceAtLeast(20)
    }

    fun calculateCurrentQuestionDay(
        context: Context,
        coupleId: String,
        settings: DailyQuestionSettings,
        now: Date = Date()
    ): Int {
        // Minuit UTC pour les bornes de jour
        val startOfSettings = utcCalendar().apply { time = settings.startDate; setToStartOfDay() }.time
        val startOfToday = utcCalendar().apply { time = now; setToStartOfDay() }.time
        val daysSinceStart = ((startOfToday.time - startOfSettings.time) / (24 * 60 * 60 * 1000L)).toInt()

        // Incrément basé sur currentDay
        val shouldIncrement = daysSinceStart >= settings.currentDay
        val nextDay = if (shouldIncrement) settings.currentDay + 1 else settings.currentDay

        val available = getAvailableQuestionsCount(context)
        val day = ((nextDay - 1) % available) + 1

        Log.d(TAG, "📝 (UTC FIXED) couple=$coupleId | days=$daysSinceStart current=${settings.currentDay} next=$nextDay → day=$day/$available")
        return day
    }

    fun generateQuestionKey(day: Int): String = "daily_question_$day"

    fun createInitialSettings(coupleId: String, currentTime: Date = Date()): DailyQuestionSettings {
        val tz = TimeZone.getDefault().id
        val cal = Calendar.getInstance().apply {
            time = currentTime
            setToStartOfDay()
        }
        Log.d(TAG, "📅 Create settings for $coupleId | start=${cal.time} tz=$tz")
        return DailyQuestionSettings(coupleId = coupleId, startDate = cal.time, timezone = tz)
    }

    data class QuestionStats(val used: Int, val total: Int, val remaining: Int)

    fun getQuestionStats(
        context: Context,
        coupleId: String,
        settings: DailyQuestionSettings?,
        at: Date = Date()
    ): QuestionStats {
        val total = getAvailableQuestionsCount(context)
        if (settings == null) return QuestionStats(used = 0, total = total, remaining = total)

        val daysSinceStart = ((at.time - settings.startDate.time) / (24 * 60 * 60 * 1000L)).toInt()
        val used = (daysSinceStart + 1).coerceIn(0, total)
        val remaining = (total - used).coerceAtLeast(0)
        return QuestionStats(used, total, remaining)
    }

    fun areAllQuestionsExhausted(
        context: Context,
        coupleId: String,
        settings: DailyQuestionSettings?,
        at: Date = Date()
    ): Boolean {
        // Cycle infini → jamais épuisé
        return false
    }

    fun shouldShowNewQuestion(settings: DailyQuestionSettings, now: Date = Date()): Boolean {
        // Logs de debug (miroir Swift)
        Log.d(TAG, "🕐 shouldShowNewQuestion DEBUG:")
        Log.d(TAG, "🕐 - now: $now")
        Log.d(TAG, "🕐 - startDate: ${settings.startDate}")
        Log.d(TAG, "🕐 - timezone: ${settings.timezone}")

        val startOfDay = utcCalendar().apply { time = settings.startDate; setToStartOfDay() }.time
        val startOfToday = utcCalendar().apply { time = now; setToStartOfDay() }.time

        val daysSinceStart = ((startOfToday.time - startOfDay.time) / (24 * 60 * 60 * 1000L)).toInt()
        val expectedDay = daysSinceStart + 1

        val isFirstDay = settings.currentDay == 1 && daysSinceStart == 0
        val shouldShow = expectedDay > settings.currentDay || isFirstDay

        Log.d(TAG, "⚙️ daysSinceStart=$daysSinceStart expectedDay=$expectedDay currentDay=${settings.currentDay} isFirstDay=$isFirstDay → shouldShow=$shouldShow")
        return shouldShow
    }
}
