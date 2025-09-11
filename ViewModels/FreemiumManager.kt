package com.love2loveapp.core.viewmodels.freemium

import android.content.Context
import android.util.Log
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.model.AppConstants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Kotlin/Android version of the Swift FreemiumManager.
 *
 * - Observability: uses StateFlow/SharedFlow instead of @Published/NotificationCenter
 * - Analytics: FirebaseAnalytics
 * - Strings: Android strings.xml via context.getString(R.string.*)
 * - Time: uses epoch millis for persistence-friendly dates
 *
 * Expected strings.xml keys (create them if they don't exist):
 *  - daily_question_subtitle_subscribed
 *  - daily_question_subtitle_one_day_remaining
 *  - daily_question_subtitle_multiple_days_remaining  -> with "%1$d" placeholder
 *  - daily_question_subtitle_subscription_required
 *  - daily_challenge_subtitle_subscribed
 *  - daily_challenge_subtitle_one_day_remaining
 *  - daily_challenge_subtitle_multiple_days_remaining -> with "%1$d" placeholder
 *  - daily_challenge_subtitle_subscription_required
 */
class FreemiumManager(
    private val appState: AppState,
    private val context: Context
) : ViewModel() {

    private val TAG = "FreemiumManager"
    private val analytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)

    // === Observable state ===
    private val _showingSubscription = MutableStateFlow(false)
    val showingSubscription: StateFlow<Boolean> = _showingSubscription.asStateFlow()

    private val _blockedCategoryAttempt = MutableStateFlow<QuestionCategory?>(null)
    val blockedCategoryAttempt: StateFlow<QuestionCategory?> = _blockedCategoryAttempt.asStateFlow()

    // Simple event bus to replace NotificationCenter.default.post(name: .freemiumManagerChanged, ...)
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    // === Freemium configuration (utilise constantes centralisées) ===
    private val questionsPerPack = AppConstants.Freemium.QUESTIONS_PER_PACK
    private val freePacksLimit = AppConstants.Freemium.FREE_PACKS_LIMIT
    private val freeJournalEntriesLimit = AppConstants.Freemium.FREE_JOURNAL_ENTRIES_LIMIT
    private val freeDailyQuestionDays = AppConstants.Freemium.FREE_DAILY_QUESTION_DAYS
    private val freeDailyChallengesDays = AppConstants.Freemium.FREE_DAILY_CHALLENGE_DAYS

    init {
        observeUserSubscriptionChanges()
    }

    private fun observeUserSubscriptionChanges() {
        viewModelScope.launch {
            appState.currentUserFlow.collect { user ->
                Log.d(TAG, "🔥 Utilisateur changé - isSubscribed: ${'$'}{user?.isSubscribed ?: false}")
            }
        }
    }

    private fun emitChanged() {
        _events.tryEmit(Event.FreemiumManagerChanged)
    }

    // === Public API (parité Swift) ===

    fun canAccessCategory(category: QuestionCategory): Boolean {
        // Premium categories require subscription
        if (category.isPremium) return appState.currentUser?.isSubscribed == true
        // "En couple" is free by design in your Swift code
        return true
    }

    fun canAccessQuestion(index: Int, category: QuestionCategory): Boolean {
        // Unlimited if subscribed
        if (appState.currentUser?.isSubscribed == true) return true

        // Block entire premium categories when not subscribed
        if (category.isPremium) return false

        // For the free "en-couple" category: limit to 2 packs * 32 = 64
        return if (category.id == "en-couple") {
            val maxFree = freePacksLimit * questionsPerPack
            index < maxFree
        } else {
            true // other free categories, if any
        }
    }

    fun handleQuestionAccess(index: Int, category: QuestionCategory, onSuccess: () -> Unit) {
        Log.d(TAG, "🔥 QUESTION: Tentative accès question ${'$'}{index + 1} dans ${'$'}{category.title}")
        if (canAccessQuestion(index, category)) {
            Log.d(TAG, "🔥 QUESTION: Accès autorisé")
            onSuccess()
        } else {
            Log.d(TAG, "🔥 QUESTION: Accès bloqué - Affichage paywall (limite atteinte)")
            _blockedCategoryAttempt.value = category
            setShowingSubscription(true)

            // Analytics
            analytics.logEvent("paywall_affiche", bundleOf(
                "source" to "freemium_limite"
            ))
            Log.d(TAG, "📊 FirebaseAnalytics: paywall_affiche — source=freemium_limite")

            trackQuestionBlocked(index, category)
        }
    }

    fun handleCategoryTap(category: QuestionCategory, onSuccess: () -> Unit) {
        val isSubscribed = appState.currentUser?.isSubscribed == true
        Log.d(TAG, "🔥 TAP: Catégorie='${'$'}{category.title}', isPremium=${'$'}{category.isPremium}, isSubscribed=${'$'}isSubscribed, showingSubscription=${'$'}{_showingSubscription.value}")

        if (isSubscribed) {
            Log.d(TAG, "🔥 TAP: UTILISATEUR ABONNÉ - ACCÈS ILLIMITÉ")
            onSuccess()
            return
        }

        if (category.isPremium) {
            Log.d(TAG, "🔥 TAP: CATEGORIE PREMIUM - ACCÈS BLOQUÉ → Paywall")
            _blockedCategoryAttempt.value = category
            setShowingSubscription(true)

            analytics.logEvent("paywall_affiche", bundleOf(
                "source" to "freemium_limite"
            ))
            Log.d(TAG, "📊 FirebaseAnalytics: paywall_affiche — source=freemium_limite")

            trackCategoryBlocked(category)
            return
        }

        // Free categories: allowed (per question limits enforced elsewhere)
        Log.d(TAG, "🔥 TAP: CATEGORIE GRATUITE - ACCÈS AUTORISÉ")
        onSuccess()
    }

    fun getAccessibleCategories(all: List<QuestionCategory>): List<QuestionCategory> {
        val isSubscribed = appState.currentUser?.isSubscribed == true
        return if (isSubscribed) {
            Log.d(TAG, "🔥 Utilisateur premium — toutes catégories accessibles")
            all
        } else {
            Log.d(TAG, "🔥 Utilisateur gratuit — catégories limitées (seulement la 1ère gratuite)")
            all.filter { !it.isPremium }.take(1)
        }
    }

    fun getAllCategoriesWithStatus(all: List<QuestionCategory>): List<QuestionCategory> = all

    fun isCategoryBlocked(category: QuestionCategory): Boolean {
        val isSubscribed = appState.currentUser?.isSubscribed == true
        val isBlocked = category.isPremium && !isSubscribed
        Log.d(TAG, "🔥 BLOCKED CHECK: Cat='${'$'}{category.title}', isPremium=${'$'}{category.isPremium}, isSubscribed=${'$'}isSubscribed, isBlocked=${'$'}isBlocked")
        return isBlocked
    }

    fun isQuestionBlocked(index: Int, category: QuestionCategory): Boolean = !canAccessQuestion(index, category)

    fun getMaxFreeQuestions(category: QuestionCategory): Int {
        if (appState.currentUser?.isSubscribed == true) return Int.MAX_VALUE
        if (category.isPremium) return 0
        return if (category.id == "en-couple") freePacksLimit * questionsPerPack else Int.MAX_VALUE
    }

    fun dismissSubscription() {
        Log.d(TAG, "🔥 DISMISS: Fermeture Subscription — before showing=${'$'}{_showingSubscription.value}, blocked='${'$'}{_blockedCategoryAttempt.value?.title ?: "nil"}'")
        _showingSubscription.value = false
        _blockedCategoryAttempt.value = null
        emitChanged()
        Log.d(TAG, "🔥 DISMISS: after showing=${'$'}{_showingSubscription.value}, blocked='${'$'}{_blockedCategoryAttempt.value?.title ?: "nil"}'")
    }

    fun handleDistanceWidgetAccess(onSuccess: () -> Unit) {
        Log.d(TAG, "🔒 Distance Widget: demandé → désormais gratuit")
        onSuccess()
    }

    fun canAddJournalEntry(currentEntriesCount: Int): Boolean {
        if (appState.currentUser?.isSubscribed == true) return true
        return currentEntriesCount < freeJournalEntriesLimit
    }

    fun handleJournalEntryCreation(currentEntriesCount: Int, onSuccess: () -> Unit) {
        Log.d(TAG, "📝 Journal: Tentative d'ajout — count=${'$'}currentEntriesCount")
        val isSubscribed = appState.currentUser?.isSubscribed == true
        when {
            isSubscribed -> {
                Log.d(TAG, "📝 Journal: Premium — Ajout autorisé")
                onSuccess()
            }
            currentEntriesCount < freeJournalEntriesLimit -> {
                Log.d(TAG, "📝 Journal: Gratuit — Ajout autorisé ${'$'}currentEntriesCount/${'$'}freeJournalEntriesLimit")
                onSuccess()
            }
            else -> {
                Log.d(TAG, "📝 Journal: Limite atteinte (${'$'}freeJournalEntriesLimit) → Paywall")
                setShowingSubscription(true)

                analytics.logEvent("paywall_affiche", bundleOf(
                    "source" to "popup_journal"
                ))
                Log.d(TAG, "📊 FirebaseAnalytics: paywall_affiche — source=popup_journal")

                trackJournalEntryBlocked(currentEntriesCount)
            }
        }
    }

    fun getMaxFreeJournalEntries(): Int = freeJournalEntriesLimit

    fun getRemainingFreeJournalEntries(currentEntriesCount: Int): Int {
        if (appState.currentUser?.isSubscribed == true) return Int.MAX_VALUE
        return (freeJournalEntriesLimit - currentEntriesCount).coerceAtLeast(0)
    }

    // === Daily Questions ===

    fun canAccessDailyQuestion(questionDay: Int): Boolean {
        if (appState.currentUser?.isSubscribed == true) return true
        Log.d(TAG, "📅 DailyQuestion: Vérification accès jour ${'$'}questionDay")
        return questionDay <= freeDailyQuestionDays
    }

    fun handleDailyQuestionAccess(currentQuestionDay: Int, onSuccess: () -> Unit) {
        val isSubscribed = appState.currentUser?.isSubscribed == true
        Log.d(TAG, "📅 DailyQuestion: Vérification jour ${'$'}currentQuestionDay, isSubscribed=${'$'}isSubscribed")
        if (isSubscribed) {
            markDailyQuestionUsage(currentQuestionDay)
            onSuccess()
            return
        }
        if (currentQuestionDay <= freeDailyQuestionDays) {
            Log.d(TAG, "📅 DailyQuestion: ${'$'}currentQuestionDay/${'$'}freeDailyQuestionDays — Accès gratuit")
            markDailyQuestionUsage(currentQuestionDay)
            onSuccess()
        } else {
            Log.d(TAG, "📅 DailyQuestion: Jour ${'$'}currentQuestionDay > ${'$'}freeDailyQuestionDays — Paywall")
            showDailyQuestionPaywall()
        }
    }

    private fun markDailyQuestionUsage(day: Int) {
        val user = appState.currentUser ?: return
        if (user.dailyQuestionFirstAccessDate == null) {
            user.dailyQuestionFirstAccessDate = System.currentTimeMillis()
            Log.d(TAG, "📅 DailyQuestion: Premier accès enregistré")
        }
        if (day > user.dailyQuestionMaxDayReached) {
            user.dailyQuestionMaxDayReached = day
            Log.d(TAG, "📅 DailyQuestion: Nouveau jour max atteint: ${'$'}day")
            appState.updateUser(user)
        }
    }

    private fun showDailyQuestionPaywall() {
        setShowingSubscription(true)
        analytics.logEvent("paywall_affiche", bundleOf(
            "source" to "daily_question_freemium"
        ))
        Log.d(TAG, "📊 FirebaseAnalytics: paywall_affiche — source=daily_question_freemium")
    }

    fun getDailyQuestionSubtitle(questionDay: Int): String {
        return if (appState.currentUser?.isSubscribed == true) {
            context.getString(R.string.daily_question_subtitle_subscribed)
        } else {
            if (questionDay <= freeDailyQuestionDays) {
                val remaining = (freeDailyQuestionDays - questionDay + 1).coerceAtLeast(0)
                if (remaining == 1) {
                    context.getString(R.string.daily_question_subtitle_one_day_remaining)
                } else {
                    context.getString(R.string.daily_question_subtitle_multiple_days_remaining, remaining)
                }
            } else {
                context.getString(R.string.daily_question_subtitle_subscription_required)
            }
        }
    }

    fun getRemainingFreeDaysForDailyQuestion(currentDay: Int): Int {
        if (appState.currentUser?.isSubscribed == true) return Int.MAX_VALUE
        return (freeDailyQuestionDays - currentDay + 1).coerceAtLeast(0)
    }

    // === Daily Challenges ===

    fun canAccessDailyChallenge(challengeDay: Int): Boolean {
        if (appState.currentUser?.isSubscribed == true) return true
        Log.d(TAG, "📅 DailyChallenge: Vérification accès défi jour ${'$'}challengeDay")
        return challengeDay <= freeDailyChallengesDays
    }

    fun handleDailyChallengeAccess(currentChallengeDay: Int, onSuccess: () -> Unit) {
        val isSubscribed = appState.currentUser?.isSubscribed == true
        Log.d(TAG, "📅 DailyChallenge: Vérification jour ${'$'}currentChallengeDay, isSubscribed=${'$'}isSubscribed")
        if (isSubscribed) {
            markDailyChallengeUsage(currentChallengeDay)
            onSuccess()
            return
        }
        if (currentChallengeDay <= freeDailyChallengesDays) {
            Log.d(TAG, "📅 DailyChallenge: ${'$'}currentChallengeDay/${'$'}freeDailyChallengesDays — Accès gratuit")
            markDailyChallengeUsage(currentChallengeDay)
            onSuccess()
        } else {
            Log.d(TAG, "📅 DailyChallenge: Jour ${'$'}currentChallengeDay > ${'$'}freeDailyChallengesDays — Paywall")
            showDailyChallengePaywall()
        }
    }

    private fun markDailyChallengeUsage(day: Int) {
        val user = appState.currentUser ?: return
        if (user.dailyChallengeFirstAccessDate == null) {
            user.dailyChallengeFirstAccessDate = System.currentTimeMillis()
            Log.d(TAG, "📅 DailyChallenge: Premier accès enregistré")
        }
        if (day > user.dailyChallengeMaxDayReached) {
            user.dailyChallengeMaxDayReached = day
            Log.d(TAG, "📅 DailyChallenge: Nouveau jour max atteint: ${'$'}day")
            appState.updateUser(user)
        }
        analytics.logEvent("freemium_daily_challenge_accessed", bundleOf(
            "challenge_day" to day,
            "is_subscribed" to false
        ))
    }

    private fun showDailyChallengePaywall() {
        setShowingSubscription(true)
        analytics.logEvent("paywall_viewed", bundleOf(
            "source" to "daily_challenge_freemium",
            "day" to 1
        ))
        analytics.logEvent("paywall_affiche", bundleOf(
            "source" to "daily_challenge_freemium"
        ))
        Log.d(TAG, "📊 FirebaseAnalytics: paywall_affiche — source=daily_challenge_freemium")
    }

    fun getDailyChallengeSubtitle(challengeDay: Int): String {
        return if (appState.currentUser?.isSubscribed == true) {
            context.getString(R.string.daily_challenge_subtitle_subscribed)
        } else {
            if (challengeDay <= freeDailyChallengesDays) {
                val remaining = (freeDailyChallengesDays - challengeDay + 1).coerceAtLeast(0)
                if (remaining == 1) {
                    context.getString(R.string.daily_challenge_subtitle_one_day_remaining)
                } else {
                    context.getString(R.string.daily_challenge_subtitle_multiple_days_remaining, remaining)
                }
            } else {
                context.getString(R.string.daily_challenge_subtitle_subscription_required)
            }
        }
    }

    fun getRemainingFreeDaysForDailyChallenge(currentDay: Int): Int {
        if (appState.currentUser?.isSubscribed == true) return Int.MAX_VALUE
        return (freeDailyChallengesDays - currentDay + 1).coerceAtLeast(0)
    }

    // === Analytics convenience ===

    private fun trackCategoryBlocked(category: QuestionCategory) {
        Log.d(TAG, "🔥 Analytics: Catégorie bloquée: ${'$'}{category.title}")
        // Add extra analytics here if needed
    }

    private fun trackQuestionBlocked(index: Int, category: QuestionCategory) {
        Log.d(TAG, "🔥 Analytics: Question bloquée: ${'$'}{index + 1} dans ${'$'}{category.title}")
        // Add extra analytics here if needed
    }

    private fun trackJournalEntryBlocked(entriesCount: Int) {
        Log.d(TAG, "📝 Analytics: Entrée journal bloquée à ${'$'}entriesCount entrées (limite=${'$'}freeJournalEntriesLimit)")
        // Add extra analytics here if needed
    }

    fun trackUpgradePromptShown() {
        Log.d(TAG, "🔥 Analytics: Prompt d'upgrade affiché")
        // analytics.logEvent("upgrade_prompt_shown", null)
    }

    fun trackConversion() {
        Log.d(TAG, "🔥 Analytics: Conversion réussie")
        // analytics.logEvent("freemium_conversion", null)
    }

    // === Helpers ===
    fun setShowingSubscription(value: Boolean) {
        _showingSubscription.value = value
        emitChanged()
        Log.d(TAG, "🔥 showingSubscription changé vers ${'$'}value")
    }

    fun setBlockedCategoryAttempt(category: QuestionCategory?) {
        _blockedCategoryAttempt.value = category
        emitChanged()
    }

    // === Result<T> based operations ===
    
    /**
     * Vérifie l'accès à une catégorie avec Result<T>
     */
    fun checkCategoryAccess(category: QuestionCategory): Result<Boolean> = try {
        val canAccess = canAccessCategory(category)
        if (canAccess) {
            Result.success(true)
        } else {
            Result.error(AppException.Data.ValidationError("premium_category_blocked"))
        }
    } catch (e: Exception) {
        Result.error(AppException.fromThrowable(e))
    }
    
    /**
     * Vérifie l'accès à une question avec Result<T>
     */
    fun checkQuestionAccess(index: Int, category: QuestionCategory): Result<Boolean> = try {
        val canAccess = canAccessQuestion(index, category)
        if (canAccess) {
            Result.success(true)
        } else {
            Result.error(AppException.Data.ValidationError("question_limit_reached"))
        }
    } catch (e: Exception) {
        Result.error(AppException.fromThrowable(e))
    }
    
    /**
     * Vérifie l'accès au journal avec Result<T>
     */
    fun checkJournalAccess(currentEntriesCount: Int): Result<Boolean> = try {
        val canAccess = canAddJournalEntry(currentEntriesCount)
        if (canAccess) {
            Result.success(true)
        } else {
            Result.error(AppException.Data.ValidationError("journal_limit_reached"))
        }
    } catch (e: Exception) {
        Result.error(AppException.fromThrowable(e))
    }
    
    /**
     * État des catégories accessibles avec Result<T>
     */
    fun getAccessibleCategoriesResult(all: List<QuestionCategory>): Result<List<QuestionCategory>> = try {
        val accessible = getAccessibleCategories(all)
        Result.success(accessible)
    } catch (e: Exception) {
        Result.error(AppException.fromThrowable(e))
    }

    // === Event types ===
    sealed class Event {
        object FreemiumManagerChanged : Event()
    }
}

// === Minimal app-level contracts to mirror your Swift types ===

data class AppUser(
    var isSubscribed: Boolean = false,
    var dailyQuestionFirstAccessDate: Long? = null,
    var dailyQuestionMaxDayReached: Int = 0,
    var dailyChallengeFirstAccessDate: Long? = null,
    var dailyChallengeMaxDayReached: Int = 0,
)

interface AppState {
    val currentUserFlow: StateFlow<AppUser?>
    var currentUser: AppUser?
    fun updateUser(user: AppUser)
}

/**
 * Port of your QuestionCategory used in Swift.
 * Replace companion object with your real repository/provider.
 */
data class QuestionCategory(
    val id: String,
    val title: String,
    val isPremium: Boolean
) {
    companion object {
        // Replace with your actual categories source
        val categories: List<QuestionCategory> = emptyList()
    }
}
