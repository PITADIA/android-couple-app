package com.love2loveapp.data.cache

import android.content.Context
import androidx.annotation.StringRes
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.Sort
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.love2loveapp.model.AppConstants

/**
 * ⛳️ Kotlin port of your Swift/Realm cache layer (Android).
 *
 * \- Uses Realm Java on Android (stable, easy Date support).
 * \- Stores canonical IDs (slugs) for categories; UI should localize via strings.xml.
 * \- Provides the same API surface you used on iOS wherever possible.
 */

// =========================================================
// Domain contracts (replace with your existing models if any)
// =========================================================

data class Question(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val category: String
)

data class QuestionCategory(
    val title: String,
    val emoji: String
)

data class FavoriteQuestion(
    val id: String = UUID.randomUUID().toString(),
    val questionId: String,
    val questionText: String,
    val categoryTitle: String,
    val emoji: String,
    val dateAdded: Date = Date()
)

enum class ResponseStatus(val raw: String) { ANSWERED("answered"), SENT("sent"), READ("read");
    companion object { fun fromRaw(v: String?) = values().firstOrNull { it.raw == v } ?: ANSWERED }
}

data class QuestionResponse(
    val userId: String,
    val userName: String,
    val text: String,
    val status: ResponseStatus = ResponseStatus.ANSWERED,
    val respondedAt: Date = Date(),
    var isReadByPartner: Boolean = false
)

enum class QuestionStatus(val raw: String) { PENDING("pending"), ONE_ANSWERED("one_answered"), BOTH_ANSWERED("both_answered"), ACTIVE("active");
    companion object { fun fromRaw(v: String?) = values().firstOrNull { it.raw == v } ?: PENDING }
}

data class DailyQuestion(
    val id: String,
    val coupleId: String,
    val questionKey: String,
    val questionDay: Int,
    val scheduledDate: String, // YYYY-MM-DD
    val scheduledDateTime: Date,
    val status: QuestionStatus,
    val createdAt: Date,
    val updatedAt: Date,
    val timezone: String = "Europe/Paris",
    val responsesFromSubcollection: List<QuestionResponse> = emptyList(),
    val legacyResponses: Map<String, QuestionResponse> = emptyMap()
)

data class DailyChallenge(
    val id: String,
    val challengeKey: String,
    val challengeDay: Int,
    val scheduledDate: Date,
    val coupleId: String,
    val isCompleted: Boolean,
    val completedAt: Date? = null
)

// =========================================================
// Realm entities (Android)
// =========================================================

open class RealmQuestion : RealmObject() {
    @PrimaryKey var id: String = UUID.randomUUID().toString()
    var text: String = ""
    var category: String = ""
    var isLiked: Boolean = false
    var createdAt: Date = Date()
    var lastViewed: Date? = null

    fun toDomain(): Question = Question(id = id, text = text, category = category)

    companion object {
        fun fromDomain(q: Question, category: String): RealmQuestion = RealmQuestion().apply {
            this.text = q.text
            this.category = category
        }
    }
}

open class RealmCategory : RealmObject() {
    @PrimaryKey var title: String = ""
    var questionsCount: Int = 0
    var lastUpdated: Date = Date()
    var isPopulated: Boolean = false
}

open class RealmFavoriteQuestion : RealmObject() {
    @PrimaryKey var id: String = UUID.randomUUID().toString()
    var questionId: String = ""
    var userId: String = ""
    var categoryTitle: String = ""
    var questionText: String = ""
    var dateAdded: Date = Date()
    var emoji: String = ""

    fun toDomain(): FavoriteQuestion = FavoriteQuestion(
        id = id,
        questionId = questionId,
        questionText = questionText,
        categoryTitle = categoryTitle,
        emoji = emoji,
        dateAdded = dateAdded
    )

    companion object {
        fun from(question: Question, category: QuestionCategory, userId: String): RealmFavoriteQuestion =
            RealmFavoriteQuestion().apply {
                this.questionId = question.id
                this.userId = userId
                this.categoryTitle = category.title
                this.questionText = question.text
                this.emoji = category.emoji
                this.dateAdded = Date()
            }
    }
}

open class RealmQuestionResponse : RealmObject() {
    @PrimaryKey var id: String = UUID.randomUUID().toString()
    var userId: String = ""
    var userName: String = ""
    var text: String = ""
    var respondedAt: Date = Date()
    @Index var status: String = ResponseStatus.ANSWERED.raw
    var isReadByPartner: Boolean = false

    fun toDomain(): QuestionResponse = QuestionResponse(
        userId = userId,
        userName = userName,
        text = text,
        status = ResponseStatus.fromRaw(status),
        respondedAt = respondedAt,
        isReadByPartner = isReadByPartner
    )

    companion object {
        fun fromDomain(r: QuestionResponse): RealmQuestionResponse = RealmQuestionResponse().apply {
            userId = r.userId
            userName = r.userName
            text = r.text
            respondedAt = r.respondedAt
            status = r.status.raw
            isReadByPartner = r.isReadByPartner
        }
    }
}

open class RealmDailyQuestion : RealmObject() {
    @PrimaryKey var id: String = UUID.randomUUID().toString()
    var questionKey: String = ""
    var scheduledDate: String = "" // YYYY-MM-DD (kept for lexicographic range queries)
    var scheduledTime: Date = Date()
    var coupleId: String = ""
    var responses: RealmList<RealmQuestionResponse> = RealmList()
    @Index var status: String = QuestionStatus.PENDING.raw
    var createdAt: Date = Date()
    var updatedAt: Date = Date()

    fun toDomain(): DailyQuestion {
        val legacyMap = responses.associateBy({ it.userId }, { it.toDomain() })
        return DailyQuestion(
            id = id,
            coupleId = coupleId,
            questionKey = questionKey,
            questionDay = 1, // default for legacy data
            scheduledDate = scheduledDate,
            scheduledDateTime = scheduledTime,
            status = QuestionStatus.fromRaw(status),
            createdAt = createdAt,
            updatedAt = updatedAt,
            timezone = "Europe/Paris",
            responsesFromSubcollection = emptyList(),
            legacyResponses = legacyMap
        )
    }

    companion object {
        fun fromDomain(dq: DailyQuestion): RealmDailyQuestion = RealmDailyQuestion().apply {
            id = dq.id
            questionKey = dq.questionKey
            scheduledDate = dq.scheduledDate
            scheduledTime = dq.scheduledDateTime
            coupleId = dq.coupleId
            status = dq.status.raw
            createdAt = dq.createdAt
            updatedAt = dq.updatedAt
            responses.clear()
            dq.legacyResponses.values.forEach { responses.add(RealmQuestionResponse.fromDomain(it)) }
        }
    }
}

open class RealmDailyChallenge : RealmObject() {
    @PrimaryKey var id: String = UUID.randomUUID().toString()
    var challengeKey: String = ""
    var challengeDay: Int = 0
    var scheduledDate: Date = Date()
    var coupleId: String = ""
    var isCompleted: Boolean = false
    var completedAt: Date? = null
    var createdAt: Date = Date()
    var updatedAt: Date = Date()

    fun toDomain(): DailyChallenge = DailyChallenge(
        id = id,
        challengeKey = challengeKey,
        challengeDay = challengeDay,
        scheduledDate = scheduledDate,
        coupleId = coupleId,
        isCompleted = isCompleted,
        completedAt = completedAt
    )

    companion object {
        fun fromDomain(dc: DailyChallenge): RealmDailyChallenge = RealmDailyChallenge().apply {
            id = dc.id
            challengeKey = dc.challengeKey
            challengeDay = dc.challengeDay
            scheduledDate = dc.scheduledDate
            coupleId = dc.coupleId
            isCompleted = dc.isCompleted
            completedAt = dc.completedAt
            createdAt = Date()
            updatedAt = Date()
        }
    }
}

open class RealmUser : RealmObject() {
    @PrimaryKey var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var birthDate: Date = Date()
    var selectedGoals: RealmList<String> = RealmList()
    var relationshipDuration: String = ""
    var relationshipImprovement: String = ""
    var questionMode: String = ""
    var isSubscribed: Boolean = false
    var partnerCode: String? = null
    var partnerId: String? = null
    var partnerConnectedAt: Date? = null
    var subscriptionInheritedFrom: String? = null
    var subscriptionInheritedAt: Date? = null
}

// =========================================================
// Utilities
// =========================================================

private object Dates {
    private val ymdUtc = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    fun toYmdUtc(date: Date): String = ymdUtc.get().format(date)
    fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
        time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time
    fun nextDay(date: Date): Date = Calendar.getInstance().apply { time = date; add(Calendar.DATE, 1) }.time
}

/** Mapping resource key -> localized string (dynamic). */
fun keyToString(context: Context, key: String): String {
    val resId = context.resources.getIdentifier(key, "string", context.packageName)
    return if (resId != 0) context.getString(resId) else key
}

/** Safer wrapper for getString when you already know the @StringRes id. */
fun Context.str(@StringRes id: Int): String = getString(id)

// =========================================================
// Cache Manager (singleton)
// =========================================================

class QuestionCacheManager private constructor(private val appContext: Context) {

    companion object {
        @Volatile private var INSTANCE: QuestionCacheManager? = null
        fun getInstance(context: Context): QuestionCacheManager =
            INSTANCE ?: synchronized(this) { INSTANCE ?: QuestionCacheManager(context.applicationContext).also { INSTANCE = it } }
    }

    private lateinit var config: RealmConfiguration

    val isLoading = MutableStateFlow(false)
    val cacheStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isRealmAvailable = MutableStateFlow(false)

    init { initializeRealm() }

    private fun initializeRealm() {
        try {
            Realm.init(appContext)
            config = RealmConfiguration.Builder()
                .name(AppConstants.REALM_DB_NAME)
                .schemaVersion(AppConstants.REALM_SCHEMA_VERSION) // \uD83D\uDEE0️ bump if you change entities
                .compactOnLaunch()
                .build()
            // Eager create DB file
            Realm.getInstance(config).use { /* no-op */ }
            isRealmAvailable.value = true
            println("RealmManager: Initialisé")
        } catch (t: Throwable) {
            println("⚠️ RealmManager: Erreur d'initialisation: ${t.message}")
            isRealmAvailable.value = false
        }
    }

    private inline fun <T> withRealm(block: (Realm) -> T): T = Realm.getInstance(config).use { block(it) }

    // =========================
    // Questions cache (per category)
    // =========================

    fun cacheQuestions(category: String, questions: List<Question>) {
        val limited = questions.take(AppConstants.MAX_CACHED_QUESTIONS_PER_CATEGORY)
        withRealm { realm ->
            realm.executeTransaction { r ->
                r.where(RealmQuestion::class.java).equalTo("category", category).findAll().deleteAllFromRealm()
                limited.forEach { q ->
                    val obj = r.createObject(RealmQuestion::class.java, UUID.randomUUID().toString())
                    obj.text = q.text
                    obj.category = category
                    obj.createdAt = Date()
                }
                var cat = r.where(RealmCategory::class.java).equalTo("title", category).findFirst()
                if (cat == null) cat = r.createObject(RealmCategory::class.java, category)
                cat!!.questionsCount = limited.size
                cat.isPopulated = true
                cat.lastUpdated = Date()
            }
        }
        cacheStatus.update { it + (category to true) }
        println("RealmManager: ${limited.size} questions cachées pour '$category'")
    }

    fun getCachedQuestions(category: String): List<Question> = withRealm { realm ->
        realm.where(RealmQuestion::class.java)
            .equalTo("category", category)
            .sort("createdAt", Sort.ASCENDING)
            .findAll()
            .map { it.toDomain() }
    }

    fun isCategoryPopulated(category: String): Boolean = withRealm { realm ->
        realm.where(RealmCategory::class.java).equalTo("title", category).findFirst()?.isPopulated == true
    }

    /** Smart cache flow matching Swift logic. */
    fun getQuestionsWithSmartCache(category: String, fallback: () -> List<Question>): List<Question> {
        val cached = getCachedQuestions(category)
        if (cached.isNotEmpty()) {
            if (category == "en-couple" && cached.size < AppConstants.MIN_QUESTIONS_EN_COUPLE) {
                println("⚠️ QuestionCacheManager: Cache incomplet (${cached.size}) pour 'en-couple' – rafraîchissement…")
            } else return cached
        }
        val fresh = QuestionDataManager.loadQuestions(appContext, category)
        if (fresh.isNotEmpty()) { cacheQuestions(category, fresh); return fresh }
        val fb = fallback()
        if (fb.isNotEmpty()) cacheQuestions(category, fb)
        return fb
    }

    // =========================
    // Preloading
    // =========================

    fun preloadAllCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            isLoading.value = true
            migrateOldCacheKeys()
            val priority = AppConstants.Migration.PRIORITY_CATEGORIES
            priority.forEach { id ->
                if (!isCategoryPopulated(id)) {
                    val q = QuestionDataManager.loadQuestions(appContext, id).take(AppConstants.PRELOAD_PRIORITY_QUESTIONS)
                    if (q.isNotEmpty()) cacheQuestions(id, q)
                }
            }
            isLoading.value = false
            println("RealmManager: Préchargement ultra-rapide terminé (${priority.size} catégorie)")
        }
    }

    fun preloadCategory(categoryId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!isCategoryPopulated(categoryId)) {
                val q = QuestionDataManager.loadQuestions(appContext, categoryId)
                if (q.isNotEmpty()) cacheQuestions(categoryId, q)
                println("RealmManager: Catégorie '$categoryId' préchargée à la demande")
            }
        }
    }

    // =========================
    // Migration des anciennes clés (FR -> slugs)
    // =========================

    private fun migrateOldCacheKeys() = withRealm { realm ->
        val keyMigration = AppConstants.Migration.CATEGORY_KEY_MAPPING
        realm.executeTransaction { r ->
            keyMigration.forEach { (oldKey, newKey) ->
                r.where(RealmQuestion::class.java).equalTo("category", oldKey).findAll().forEach { it.category = newKey }
                val oldCat = r.where(RealmCategory::class.java).equalTo("title", oldKey).findFirst()
                if (oldCat != null) {
                    var newCat = r.where(RealmCategory::class.java).equalTo("title", newKey).findFirst()
                    if (newCat == null) newCat = r.createObject(RealmCategory::class.java, newKey)
                    newCat!!.questionsCount = oldCat.questionsCount
                    newCat.isPopulated = oldCat.isPopulated
                    newCat.lastUpdated = oldCat.lastUpdated
                    oldCat.deleteFromRealm()
                }
            }
        }
        println("RealmManager: Migration du cache terminée")
    }

    // =========================
    // Stats & Cleanup
    // =========================

    data class CacheStats(val totalQuestions: Int, val categories: Int, val cacheSize: String)

    fun getCacheStatistics(): CacheStats = withRealm { realm ->
        val total = realm.where(RealmQuestion::class.java).findAll().size
        val cats = realm.where(RealmCategory::class.java).findAll().size
        val file = File(config.path)
        val size = android.text.format.Formatter.formatFileSize(appContext, file.length())
        CacheStats(totalQuestions = total, categories = cats, cacheSize = size)
    }

    fun clearCache() = withRealm { realm ->
        realm.executeTransaction { it.deleteAll() }
        cacheStatus.value = emptyMap()
        println("RealmManager: Cache vidé")
    }

    fun optimizeMemoryUsage() = withRealm { realm ->
        val sevenDaysAgo = Date(System.currentTimeMillis() - AppConstants.CACHE_CLEANUP_DAYS * 24L * 60 * 60 * 1000)
        realm.executeTransaction { r ->
            val old: RealmResults<RealmQuestion> = r.where(RealmQuestion::class.java)
                .lessThan("lastViewed", sevenDaysAgo)
                .or()
                .isNull("lastViewed")
                .findAll()
            if (old.size > AppConstants.Performance.CACHE_MEMORY_CLEANUP_THRESHOLD) {
                val toDelete = old.subList(0, old.size - AppConstants.Performance.CACHE_MEMORY_KEEP_COUNT)
                toDelete.deleteAllFromRealm()
                println("RealmManager: ${toDelete.size} anciennes questions supprimées")
            }
        }
    }

    // =========================
    // Daily Questions cache
    // =========================

    fun cacheDailyQuestion(question: DailyQuestion) = withRealm { realm ->
        realm.executeTransaction { r ->
            r.insertOrUpdate(RealmDailyQuestion.fromDomain(question))
        }
        println("✅ RealmManager: Question quotidienne cachée: ${question.questionKey}")
    }

    fun getCachedDailyQuestion(coupleId: String, dateYmd: String): DailyQuestion? = withRealm { realm ->
        realm.where(RealmDailyQuestion::class.java)
            .equalTo("coupleId", coupleId)
            .equalTo("scheduledDate", dateYmd)
            .findFirst()
            ?.toDomain()
    }

    fun getCachedDailyQuestions(coupleId: String, limit: Int = AppConstants.DAILY_CHALLENGES_DEFAULT_LIMIT): List<DailyQuestion> = withRealm { realm ->
        realm.where(RealmDailyQuestion::class.java)
            .equalTo("coupleId", coupleId)
            .sort("scheduledDate", Sort.DESCENDING)
            .findAll()
            .take(limit)
            .map { it.toDomain() }
    }

    fun updateDailyQuestionResponse(questionId: String, userId: String, response: QuestionResponse) = withRealm { realm ->
        realm.executeTransaction { r ->
            val rq = r.where(RealmDailyQuestion::class.java).equalTo("id", questionId).findFirst()
            if (rq != null) {
                // remove existing response by userId
                rq.responses.firstOrNull { it.userId == userId }?.deleteFromRealm()
                rq.responses.add(RealmQuestionResponse.fromDomain(response))
                val answeredCount = rq.responses.count { it.status == ResponseStatus.ANSWERED.raw }
                rq.status = if (answeredCount >= 2) QuestionStatus.BOTH_ANSWERED.raw else QuestionStatus.ONE_ANSWERED.raw
                rq.updatedAt = Date()
            }
        }
        println("✅ RealmManager: Réponse mise à jour dans le cache pour question: $questionId")
    }

    fun clearOldDailyQuestions(olderThanDays: Int = AppConstants.DAILY_QUESTIONS_RETENTION_DAYS) = withRealm { realm ->
        val cutoff = Dates.toYmdUtc(Date(System.currentTimeMillis() - olderThanDays * 24L * 60 * 60 * 1000))
        realm.executeTransaction { r ->
            val old = r.where(RealmDailyQuestion::class.java)
                .lessThan("scheduledDate", cutoff)
                .findAll()
            val count = old.size
            old.deleteAllFromRealm()
            println("✅ RealmManager: $count questions quotidiennes anciennes supprimées")
        }
    }

    data class DailyQuestionsStats(val total: Int, val oldestDate: String?, val newestDate: String?)

    fun getDailyQuestionsCacheStatistics(): DailyQuestionsStats = withRealm { realm ->
        val all = realm.where(RealmDailyQuestion::class.java).findAll()
        if (all.isEmpty()) return@withRealm DailyQuestionsStats(0, null, null)
        val oldest = realm.where(RealmDailyQuestion::class.java).sort("scheduledDate", Sort.ASCENDING).findFirst()?.scheduledDate
        val newest = realm.where(RealmDailyQuestion::class.java).sort("scheduledDate", Sort.DESCENDING).findFirst()?.scheduledDate
        DailyQuestionsStats(total = all.size, oldestDate = oldest, newestDate = newest)
    }

    // =========================
    // Daily Challenges cache
    // =========================

    fun cacheDailyChallenge(challenge: DailyChallenge) = withRealm { realm ->
        realm.executeTransaction { r -> r.insertOrUpdate(RealmDailyChallenge.fromDomain(challenge)) }
        println("✅ RealmManager: Défi quotidien caché: ${challenge.challengeKey}")
    }

    fun getCachedDailyChallenge(coupleId: String, date: Date): DailyChallenge? = withRealm { realm ->
        val start = Dates.startOfDay(date)
        val end = Dates.nextDay(start)
        realm.where(RealmDailyChallenge::class.java)
            .equalTo("coupleId", coupleId)
            .greaterThanOrEqualTo("scheduledDate", start)
            .lessThan("scheduledDate", end)
            .findFirst()
            ?.toDomain()
    }

    fun getCachedDailyChallenges(coupleId: String, limit: Int = AppConstants.DAILY_CHALLENGES_DEFAULT_LIMIT): List<DailyChallenge> = withRealm { realm ->
        realm.where(RealmDailyChallenge::class.java)
            .equalTo("coupleId", coupleId)
            .sort("scheduledDate", Sort.DESCENDING)
            .findAll()
            .take(limit)
            .map { it.toDomain() }
    }

    fun updateDailyChallengeCompletion(challengeId: String, isCompleted: Boolean, completedAt: Date?) = withRealm { realm ->
        realm.executeTransaction { r ->
            val rc = r.where(RealmDailyChallenge::class.java).equalTo("id", challengeId).findFirst()
            if (rc != null) {
                rc.isCompleted = isCompleted
                rc.completedAt = completedAt
                rc.updatedAt = Date()
            }
        }
        println("✅ RealmManager: État de completion mis à jour pour défi: $challengeId")
    }

    data class DailyChallengesInfo(val count: Int, val oldestDate: Date?, val newestDate: Date?)

    fun getDailyChallengesCacheInfo(coupleId: String): DailyChallengesInfo = withRealm { realm ->
        val results = realm.where(RealmDailyChallenge::class.java).equalTo("coupleId", coupleId).findAll()
        if (results.isEmpty()) return@withRealm DailyChallengesInfo(0, null, null)
        val oldest = realm.where(RealmDailyChallenge::class.java).equalTo("coupleId", coupleId).sort("scheduledDate", Sort.ASCENDING).findFirst()?.scheduledDate
        val newest = realm.where(RealmDailyChallenge::class.java).equalTo("coupleId", coupleId).sort("scheduledDate", Sort.DESCENDING).findFirst()?.scheduledDate
        DailyChallengesInfo(results.size, oldest, newest)
    }

    fun clearDailyChallengesCache(coupleId: String? = null) = withRealm { realm ->
        realm.executeTransaction { r ->
            val toDelete: RealmResults<RealmDailyChallenge> = if (coupleId == null) {
                r.where(RealmDailyChallenge::class.java).findAll()
            } else {
                r.where(RealmDailyChallenge::class.java).equalTo("coupleId", coupleId).findAll()
            }
            val count = toDelete.size
            toDelete.deleteAllFromRealm()
            println("✅ RealmManager: $count défis quotidiens supprimés du cache")
        }
    }
}

// =========================================================
// QuestionDataManager (loader) — replace with your real implementation
// =========================================================

object QuestionDataManager {
    /** Load questions for a category from local assets / JSON / etc. */
    fun loadQuestions(context: Context, category: String): List<Question> {
        // TODO: replace with the real loader (e.g., assets JSON).
        return emptyList()
    }
}

// =========================================================
// strings.xml guidance (localization)
// =========================================================
/**
 * UI layer: when you have a key like "daily_challenge_12" or "daily_question_7",
 * use [keyToString] to resolve it dynamically from strings.xml.
 *
 * Example (Compose):
 *   val text = remember(challenge.challengeKey) { keyToString(context, challenge.challengeKey) }
 *   Text(text)
 *
 * Or for static ids: context.str(R.string.category_en_couple_title)
 */
