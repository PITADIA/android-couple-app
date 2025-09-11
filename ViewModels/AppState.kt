// AppState.kt
package com.love2loveapp.core.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.model.AppConstants
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

// ==============================
// IntroFlags (équivalent Swift)
// ==============================
data class IntroFlags(
    val dailyQuestion: Boolean = false,
    val dailyChallenge: Boolean = false
) {
    companion object {
        val DEFAULT = IntroFlags()
    }
}

// ===================================================
// AppState (équivalent Swift ObservableObject → ViewModel)
// ===================================================
class AppState(
    private val context: Context,
    private val firebaseService: FirebaseService = FirebaseService.getInstance(), // TODO: branche ton service réel
    private val userCache: UserCacheManager = UserCacheManager.getInstance(context), // TODO: branche ton cache réel
    private val analytics: AnalyticsService = AnalyticsService.getInstance(), // TODO
    private val widgetService: WidgetService = WidgetService(), // TODO
    private val fcmService: FCMService = FCMService.getInstance(context), // init passif
) : ViewModel() {

    // region --- State exposé (Compose-friendly avec Result<T>) ---
    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()
    
    // === Result<T> State Management ===
    val currentUserResult: StateFlow<Result<AppUser?>> = combine(
        _currentUser,
        _isLoading,
        firebaseService.errorMessage.catch { emit(null) }
    ) { user, loading, error ->
        when {
            loading -> Result.loading()
            error != null -> Result.error(AppException.Generic(error))
            else -> Result.success(user)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, Result.loading())
    
    val authenticationResult: StateFlow<Result<Boolean>> = combine(
        _isAuthenticated,
        _isLoading,
        firebaseService.errorMessage.catch { emit(null) }
    ) { isAuth, loading, error ->
        when {
            loading -> Result.loading()
            error != null -> Result.error(AppException.Auth.SignInFailed)
            else -> Result.success(isAuth)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, Result.loading())

    private val _currentOnboardingStep = MutableStateFlow(0)
    val currentOnboardingStep: StateFlow<Int> = _currentOnboardingStep.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Services "publiés" (si tu veux les exposer à la UI)
    // Ici on laisse des stubs / références, à toi de les brancher à tes classes
    val freemiumManager = FreemiumManager(this) // TODO
    val favoritesService = FavoritesService().apply { configure(this@AppState) } // TODO
    val categoryProgressService = CategoryProgressService.getInstance() // TODO
    val partnerConnectionService = PartnerConnectionNotificationService.getInstance() // TODO
    val partnerSubscriptionService = PartnerSubscriptionNotificationService.getInstance() // TODO
    val partnerSubscriptionSyncService = PartnerSubscriptionSyncService.getInstance() // TODO
    val partnerLocationService = PartnerLocationService.getInstance() // TODO
    val journalService = JournalService.getInstance().apply { configure(this@AppState) } // TODO
    val reviewService = ReviewRequestService.getInstance() // TODO
    val dailyChallengeService = DailyChallengeService.getInstance() // TODO
    val savedChallengesService = SavedChallengesService.getInstance() // TODO

    // Flags meta
    private var hasMinimumLoadingTimeElapsed = false
    private var firebaseDataLoaded = false
    private val crashGuard = CoroutineExceptionHandler { _, e ->
        Log.e("AppState", "Coroutine error: ${e.message}", e)
    }

    // Flags onboarding
    private val _isOnboardingInProgress = MutableStateFlow(false)
    val isOnboardingInProgress: StateFlow<Boolean> = _isOnboardingInProgress.asStateFlow()

    private val _forceOnboarding = MutableStateFlow(false)
    val forceOnboarding: StateFlow<Boolean> = _forceOnboarding.asStateFlow()

    private val _hasUserStartedOnboarding = MutableStateFlow(false)
    val hasUserStartedOnboarding: StateFlow<Boolean> = _hasUserStartedOnboarding.asStateFlow()

    // Intro flags par couple (SharedPreferences)
    private val _introFlags = MutableStateFlow(IntroFlags.DEFAULT)
    val introFlags: StateFlow<IntroFlags> = _introFlags.asStateFlow()
    // endregion

    init {
        Log.d("AppState", "Initialisation")

        // 🚀 Charger util. cache IMMÉDIATEMENT
        userCache.getCachedUser()?.let { cached ->
            Log.d("AppState", "🚀 Utilisateur en cache: ${cached.name}")
            _currentUser.value = cached
            _isAuthenticated.value = true
            _isOnboardingCompleted.value = !cached.onboardingInProgress

            // Charger les introFlags immédiatement (sync) pour éviter le flash d'intro
            loadIntroFlagsSync()

            // Simuler un rendu fluide: mini délai
            viewModelScope.launch(crashGuard) {
                delay(300)
                Log.d("AppState", "✅ Cache utilisateur → Fin chargement immédiate")
                hasMinimumLoadingTimeElapsed = true
                firebaseDataLoaded = true
                checkIfLoadingComplete()
            }
        }

        // Délai minimum pour l'écran de chargement (utilise constante centralisée)
        viewModelScope.launch(crashGuard) {
            delay(AppConstants.ViewModels.MINIMUM_LOADING_TIME_MS)
            Log.d("AppState", "Délai minimum écoulé")
            hasMinimumLoadingTimeElapsed = true
            checkIfLoadingComplete()
        }

        // Observers Firebase
        viewModelScope.launch(crashGuard) {
            firebaseService.isAuthenticated.collect { isAuth ->
                val ts = System.currentTimeMillis()
                Log.d("AppState", "isAuthenticated=$isAuth [$ts]")

                // Ne pas écraser un état déjà "true" (cache protégé)
                if (_isAuthenticated.value && !isAuth && _currentUser.value != null) {
                    Log.d("AppState", "🛡️ Cache protégé - isAuthenticated=false ignoré")
                    return@collect
                }
                _isAuthenticated.value = isAuth
            }
        }

        viewModelScope.launch(crashGuard) {
            firebaseService.currentUser.collect { user ->
                val ts = System.currentTimeMillis()
                Log.d("AppState", "User changé: ${user?.name} [$ts]")

                if (_currentUser.value != null && user == null) {
                    Log.d("AppState", "🛡️ Cache protégé - Firebase user=null ignoré")
                    firebaseDataLoaded = true
                    checkIfLoadingComplete()
                    return@collect
                }

                // Changement d'abonnement → rafraîchir widgets
                _currentUser.value?.let { old ->
                    user?.let { new ->
                        if (old.isSubscribed != new.isSubscribed) {
                            Log.d("AppState", "🔒 Abonnement changé: ${old.isSubscribed} -> ${new.isSubscribed}")
                            widgetService.refreshData()
                        }
                    }
                }

                // Protection image profil obsolète: laisse à ta logique métier si besoin
                _currentUser.value = user ?: _currentUser.value

                Log.d("AppState", "Firebase data loaded")
                firebaseDataLoaded = true
                checkIfLoadingComplete()

                // Onboarding flow
                if (_forceOnboarding.value) {
                    Log.d("AppState", "🔥🔥🔥 ONBOARDING FORCE - pas de redirection auto")
                    _isOnboardingCompleted.value = false
                    _isOnboardingInProgress.value = true
                    return@collect
                }

                user?.let { u ->
                    val hasCompleteData = u.name.isNotBlank() && u.relationshipGoals.isNotEmpty()
                    val onboardingDone = hasCompleteData && !u.onboardingInProgress

                    if (onboardingDone) {
                        Log.d("AppState", "Onboarding terminé")
                        _isOnboardingCompleted.value = true
                        _isOnboardingInProgress.value = false

                        // Config FavoritesService avec UID Firebase
                        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            favoritesService.setCurrentUser(uid, u.name)
                        }

                        // Charger flags intro
                        loadIntroFlags()
                        // Migration auto flags existants
                        initializeIntroFlagsForExistingUsers()

                        // Forcer refresh des images pour widgets
                        widgetService.forceRefreshProfileImages()
                        Log.d("AppState", "🔄 Rafraîchissement images profil widgets")
                    } else if (u.onboardingInProgress) {
                        Log.d("AppState", "Onboarding en cours")
                        _isOnboardingInProgress.value = true
                    } else {
                        Log.d("AppState", "Données incomplètes")
                        _isOnboardingInProgress.value = false
                    }
                } ?: run {
                    if (_isAuthenticated.value) {
                        Log.d("AppState", "Continuer onboarding (user null mais auth=true)")
                        _isOnboardingCompleted.value = false
                        _isOnboardingInProgress.value = false
                    } else {
                        Log.d("AppState", "Onboarding requis")
                        _isOnboardingCompleted.value = false
                        _isOnboardingInProgress.value = false
                    }
                }

                // Vérifier messages de connexion partenaire en attente (stub)
                viewModelScope.launch(crashGuard) {
                    PartnerCodeService.getInstance().checkForPendingConnectionMessage()
                }
            }
        }

        // Redémarrer services partenaires si partnerId change
        viewModelScope.launch(crashGuard) {
            firebaseService.currentUser.collect { user ->
                Log.d("AppState", "🔄 Changement utilisateur détecté")
                if (user != null) {
                    Log.d("AppState", "🔄 Utilisateur: ${user.name}")
                    val partnerIdMasked = if (user.partnerId?.isNotBlank() == true) "[ID_MASQUÉ]" else "nil"
                    Log.d("AppState", "🔄 Partner ID: '$partnerIdMasked'")

                    val partnerId = user.partnerId
                    if (!partnerId.isNullOrBlank()) {
                        Log.d("AppState", "🔄 Reconnexion - redémarrage des services partenaires")
                        partnerLocationService.configureListener(partnerId)
                        // Reconfigurer DailyQuestionService si besoin
                        DailyQuestionService.getInstance().configure(this@AppState)
                    } else {
                        Log.d("AppState", "🔄 Pas de partenaire connecté - arrêt services")
                        partnerLocationService.configureListener(null)
                    }
                } else {
                    Log.d("AppState", "🔄 Utilisateur null - arrêt services")
                    partnerLocationService.configureListener(null)
                }
            }
        }

        // Abonnements à des événements (remplace NotificationCenter)
        viewModelScope.launch(crashGuard) {
            PartnerEvents.partnerConnected.collect {
                Log.d("AppState", "📱 Partenaire connecté - refresh user")
                refreshCurrentUserData()
            }
        }
        viewModelScope.launch(crashGuard) {
            PartnerEvents.partnerDisconnected.collect {
                Log.d("AppState", "📱 Partenaire déconnecté - refresh user")
                refreshCurrentUserData()
            }
        }
        viewModelScope.launch(crashGuard) {
            SubscriptionEvents.subscriptionUpdated.collect {
                Log.d("AppState", "🔒 Abonnement mis à jour - refresh widgets")
                widgetService.refreshData()
            }
        }
        viewModelScope.launch(crashGuard) {
            IntroFlagsEvents.introFlagsDidReset.collect {
                Log.d("AppState", "🔄 Reset des introFlags - rechargement")
                loadIntroFlags()
            }
        }

        // Initialisation FCM (passive)
        fcmService.initSilently()
        Log.d("AppState", "🔥 FCMService initialisé (sans demande de permission)")

        // Config DailyQuestionService (asynchrone)
        viewModelScope.launch(crashGuard) {
            Log.d("AppState", "🔥 Configuration DailyQuestionService...")
            DailyQuestionService.getInstance().configure(this@AppState)
            Log.d("AppState", "🔥 DailyQuestionService configuré")
        }
    }

    // region --- Méthodes publiques (UI) ---
    fun startOnboardingFlow() {
        Log.d("AppState", "🔥🔥🔥 DEMARRAGE FORCE DE L'ONBOARDING")
        _forceOnboarding.value = true
        _isOnboardingCompleted.value = false
        _isOnboardingInProgress.value = true
        _currentOnboardingStep.value = 0
    }

    fun startUserOnboarding() {
        Log.d("AppState", "🔥🔥🔥 UTILISATEUR A DEMARRÉ L'ONBOARDING")
        _hasUserStartedOnboarding.value = true
        _isOnboardingCompleted.value = false
        _isOnboardingInProgress.value = true
    }

    fun authenticate(user: AppUser) {
        Log.d("AppState", "Authentification: ${user.name}")
        _currentUser.value = user
        _isAuthenticated.value = true
        firebaseService.saveUserData(user)
    }

    fun completeOnboarding() {
        Log.d("AppState", "Finalisation onboarding")
        _isOnboardingCompleted.value = true
        _isOnboardingInProgress.value = false
        _forceOnboarding.value = false
        _hasUserStartedOnboarding.value = false
        _currentOnboardingStep.value = 0
    }

    fun updateUser(user: AppUser) {
        _currentUser.value = user

        // RevenueCat Android
        FirebaseAuth.getInstance().currentUser?.uid?.let { firebaseUserId ->
            Log.d("AppState", "💰 RevenueCat logIn avec Firebase UID")
            Purchases.sharedInstance.logIn(firebaseUserId) { customerInfo: CustomerInfo?, created: Boolean ->
                Log.d("AppState", "RevenueCat logIn → created=$created, hasInfo=${customerInfo != null}")
            }
        }

        firebaseService.saveUserData(user)
    }

    fun signOut() {
        // Nettoyage cache
        userCache.clearCache()

        // RevenueCat logout
        Log.d("AppState", "💰 RevenueCat logOut (signOut)")
        Purchases.sharedInstance.logOut { /* no-op */ }

        firebaseService.signOut()
        _isOnboardingCompleted.value = false
        _isOnboardingInProgress.value = false
        _forceOnboarding.value = false
        _hasUserStartedOnboarding.value = false
        _currentOnboardingStep.value = 0
        _currentUser.value = null
        _isAuthenticated.value = false
    }

    fun deleteAccount() {
        Log.d("AppState", "Suppression du compte")
        userCache.clearCache()
        Log.d("AppState", "💰 RevenueCat logOut (deleteAccount)")
        Purchases.sharedInstance.logOut { /* no-op */ }

        firebaseService.signOut()
        _isOnboardingCompleted.value = false
        _isAuthenticated.value = false
        _isOnboardingInProgress.value = false
        _forceOnboarding.value = false
        _hasUserStartedOnboarding.value = false
        _currentOnboardingStep.value = 0
        _currentUser.value = null
        _isLoading.value = false
    }

    fun nextOnboardingStep() {
        _currentOnboardingStep.value = _currentOnboardingStep.value + 1
    }

    fun previousOnboardingStep() {
        if (_currentOnboardingStep.value > 0) {
            _currentOnboardingStep.value = _currentOnboardingStep.value - 1
        }
    }
    // endregion

    // region --- IntroFlags Management ---
    private fun generateCoupleId(): String? {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return null
        val partnerId = _currentUser.value?.partnerId?.trim().orEmpty()
        if (partnerId.isEmpty()) return null
        return listOf(firebaseUser.uid, partnerId).sorted().joinToString("_")
    }

    fun loadIntroFlags() {
        val coupleId = generateCoupleId() ?: run {
            Log.d("AppState", "🔍 IntroFlags: coupleId introuvable (pas de partenaire)")
            _introFlags.value = IntroFlags.DEFAULT
            return
        }
        val key = ConnectionConfig.introFlagsKey(coupleId)
        _introFlags.value = userCache.readIntroFlags(key) ?: IntroFlags.DEFAULT
        Log.d("AppState", "✅ IntroFlags chargés pour couple $coupleId: ${_introFlags.value}")
    }

    private fun loadIntroFlagsSync() {
        val coupleId = generateCoupleId() ?: run {
            Log.d("AppState", "🔍 IntroFlags(SYNC): coupleId introuvable")
            _introFlags.value = IntroFlags.DEFAULT
            return
        }
        val key = ConnectionConfig.introFlagsKey(coupleId)
        _introFlags.value = userCache.readIntroFlags(key) ?: IntroFlags.DEFAULT
        Log.d("AppState", "✅ IntroFlags(SYNC) chargés pour couple $coupleId: ${_introFlags.value}")
    }

    fun saveIntroFlags() {
        val coupleId = generateCoupleId() ?: run {
            Log.e("AppState", "❌ IntroFlags: impossible de sauvegarder - pas de partenaire")
            return
        }
        val key = ConnectionConfig.introFlagsKey(coupleId)
        userCache.writeIntroFlags(key, _introFlags.value)
        Log.d("AppState", "✅ IntroFlags sauvegardés pour couple $coupleId: ${_introFlags.value}")
    }

    fun resetIntroFlagsOnPartnerChange() {
        _introFlags.value = IntroFlags.DEFAULT
        generateCoupleId()?.let { coupleId ->
            val key = ConnectionConfig.introFlagsKey(coupleId)
            userCache.writeIntroFlags(key, _introFlags.value)
            Log.d("AppState", "🔄 IntroFlags reset & sauvegardés pour nouveau couple $coupleId")
        } ?: Log.d("AppState", "🔄 IntroFlags reset - aucun partenaire connecté")
    }

    fun markDailyQuestionIntroAsSeen() {
        _introFlags.value = _introFlags.value.copy(dailyQuestion = true)
        saveIntroFlags()
        analytics.trackIntroContinue("daily_question")
    }

    fun markDailyChallengeIntroAsSeen() {
        _introFlags.value = _introFlags.value.copy(dailyChallenge = true)
        saveIntroFlags()
        analytics.trackIntroContinue("daily_challenge")
    }

    fun initializeIntroFlagsForExistingUsers() {
        val coupleId = generateCoupleId() ?: return
        val key = ConnectionConfig.introFlagsKey(coupleId)
        if (!userCache.hasIntroFlags(key)) {
            if (isLikelyExistingUser()) {
                _introFlags.value = IntroFlags(dailyQuestion = true, dailyChallenge = true)
                Log.d("AppState", "🔄 Migration flags: utilisateur existant → intro vues")
            } else {
                _introFlags.value = IntroFlags.DEFAULT
                Log.d("AppState", "🔄 Nouvelle connexion → intro à afficher")
            }
            saveIntroFlags()
        }
    }

    private fun isLikelyExistingUser(): Boolean {
        val u = _currentUser.value ?: return false
        val hasUsedQ = u.dailyQuestionFirstAccessDate != null
        val hasUsedC = u.dailyChallengeFirstAccessDate != null
        val hasQHistory = (u.dailyQuestionMaxDayReached ?: 0) > 1
        val hasCHistory = (u.dailyChallengeMaxDayReached ?: 0) > 1
        val existing = hasUsedQ || hasUsedC || hasQHistory || hasCHistory
        if (existing) {
            Log.d(
                "AppState",
                "✅ Utilisateur existant (Q:$hasUsedQ, C:$hasUsedC, MaxQ:${u.dailyQuestionMaxDayReached ?: 0}, MaxC:${u.dailyChallengeMaxDayReached ?: 0})"
            )
        } else {
            Log.d("AppState", "🆕 Nouvel utilisateur - aucun historique")
        }
        return existing
    }
    // endregion

    // region --- Privé ---
    private fun checkIfLoadingComplete() {
        val ts = System.currentTimeMillis()
        Log.d("AppState", "Vérif fin de chargement [$ts]")
        Log.d("AppState", "- Délai minimum: $hasMinimumLoadingTimeElapsed")
        Log.d("AppState", "- Données Firebase chargées: $firebaseDataLoaded")
        Log.d("AppState", "- isAuthenticated: ${_isAuthenticated.value}")

        if (hasMinimumLoadingTimeElapsed && firebaseDataLoaded) {
            Log.d("AppState", "✅ Conditions remplies - fin du chargement")
            _isLoading.value = false
        } else {
            Log.d("AppState", "⏳ Attente conditions…")
        }
    }

    private fun refreshCurrentUserData() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: run {
            Log.e("AppState", "❌ Impossible de refresh - pas d'utilisateur Firebase")
            return
        }
        Log.d("AppState", "🔄 Refresh user data: ${firebaseUser.uid}")
        firebaseService.forceRefreshUserData()
    }
    // endregion

    // region --- Localisation utilitaire (remplace .localized(...)) ---
    /**
     * Si tu as une clé dynamique (ex: "daily_challenge_1"), tu peux faire :
     * val text = context.getStringByName("daily_challenge_1") ?: "fallback"
     * …ou, si la clé est connue à la compilation : context.getString(R.string.daily_challenge_1)
     */
    private fun Context.getStringByName(name: String): String? {
        val resId = resources.getIdentifier(name, "string", packageName)
        return if (resId != 0) getString(resId) else null
    }

    // Exemple d’équivalence à `challengeKey.localized(tableName: "DailyChallenges")`
    // fun getLocalizedChallengeText(challengeKey: String): String =
    //     context.getStringByName(challengeKey) ?: challengeKey
    // endregion
}

// ===================================================
// Stubs & Config (à remplacer par tes vraies classes)
// ===================================================
object ConnectionConfig {
    fun introFlagsKey(coupleId: String) = "intro_flags_$coupleId"
}

data class AppUser(
    val uid: String,
    val name: String = "",
    val onboardingInProgress: Boolean = false,
    val partnerId: String? = null,
    val isSubscribed: Boolean = false,
    val relationshipGoals: List<String> = emptyList(),
    val profileImageUpdatedAt: Long? = null, // epoch millis
    val dailyQuestionFirstAccessDate: Long? = null,
    val dailyChallengeFirstAccessDate: Long? = null,
    val dailyQuestionMaxDayReached: Int? = null,
    val dailyChallengeMaxDayReached: Int? = null
)

class FirebaseService private constructor() {
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser

    fun saveUserData(user: AppUser) {
        // TODO: save to Firestore
        _currentUser.value = user
        _isAuthenticated.value = true
    }

    fun forceRefreshUserData() {
        // TODO: reload from Firestore
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        _isAuthenticated.value = false
        _currentUser.value = null
    }

    companion object {
        private var INSTANCE: FirebaseService? = null
        fun getInstance(): FirebaseService = INSTANCE ?: FirebaseService().also { INSTANCE = it }
    }
}

class UserCacheManager private constructor(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)

    fun getCachedUser(): AppUser? {
        val json = prefs.getString("cached_user", null) ?: return null
        return runCatching { jsonToUser(json) }.getOrNull()
    }

    fun clearCache() {
        prefs.edit().remove("cached_user").apply()
    }

    fun readIntroFlags(key: String): IntroFlags? {
        val json = prefs.getString(key, null) ?: return null
        return runCatching { jsonToIntroFlags(json) }.getOrNull()
    }

    fun writeIntroFlags(key: String, flags: IntroFlags) {
        prefs.edit().putString(key, introFlagsToJson(flags)).apply()
    }

    // --- mini (dé)serialisation sans lib externe ---
    private fun jsonToUser(json: String): AppUser {
        val o = JSONObject(json)
        return AppUser(
            uid = o.optString("uid"),
            name = o.optString("name", ""),
            onboardingInProgress = o.optBoolean("onboardingInProgress", false),
            partnerId = if (o.has("partnerId")) o.optString("partnerId") else null,
            isSubscribed = o.optBoolean("isSubscribed", false),
            relationshipGoals = o.optJSONArray("relationshipGoals")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            } ?: emptyList(),
            profileImageUpdatedAt = if (o.has("profileImageUpdatedAt")) o.optLong("profileImageUpdatedAt") else null,
            dailyQuestionFirstAccessDate = if (o.has("dailyQuestionFirstAccessDate")) o.optLong("dailyQuestionFirstAccessDate") else null,
            dailyChallengeFirstAccessDate = if (o.has("dailyChallengeFirstAccessDate")) o.optLong("dailyChallengeFirstAccessDate") else null,
            dailyQuestionMaxDayReached = if (o.has("dailyQuestionMaxDayReached")) o.optInt("dailyQuestionMaxDayReached") else null,
            dailyChallengeMaxDayReached = if (o.has("dailyChallengeMaxDayReached")) o.optInt("dailyChallengeMaxDayReached") else null
        )
    }

    private fun jsonToIntroFlags(json: String): IntroFlags {
        val o = JSONObject(json)
        return IntroFlags(
            dailyQuestion = o.optBoolean("dailyQuestion", false),
            dailyChallenge = o.optBoolean("dailyChallenge", false)
        )
    }

    private fun introFlagsToJson(flags: IntroFlags): String =
        JSONObject()
            .put("dailyQuestion", flags.dailyQuestion)
            .put("dailyChallenge", flags.dailyChallenge)
            .toString()

    companion object {
        private var INSTANCE: UserCacheManager? = null
        fun getInstance(context: Context): UserCacheManager =
            INSTANCE ?: UserCacheManager(context.applicationContext).also { INSTANCE = it }
    }
}

// ----- Événements "NotificationCenter" → SharedFlows -----
object PartnerEvents {
    val partnerConnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val partnerDisconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
object SubscriptionEvents {
    val subscriptionUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
object IntroFlagsEvents {
    val introFlagsDidReset = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

// ----- Stubs services (branche tes implémentations) -----
class FreemiumManager(private val appState: AppState)
class FavoritesService {
    fun configure(appState: AppState) {}
    fun setCurrentUser(uid: String, name: String) {}
}
class CategoryProgressService private constructor() {
    companion object { private val I = CategoryProgressService(); fun getInstance() = I }
}
class PartnerConnectionNotificationService private constructor() {
    companion object { private val I = PartnerConnectionNotificationService(); fun getInstance() = I }
}
class PartnerSubscriptionNotificationService private constructor() {
    companion object { private val I = PartnerSubscriptionNotificationService(); fun getInstance() = I }
}
class PartnerSubscriptionSyncService private constructor() {
    companion object { private val I = PartnerSubscriptionSyncService(); fun getInstance() = I }
}
class PartnerLocationService private constructor() {
    fun configureListener(partnerId: String?) {}
    companion object { private val I = PartnerLocationService(); fun getInstance() = I }
}
class JournalService private constructor() {
    fun configure(appState: AppState) {}
    companion object { private val I = JournalService(); fun getInstance() = I }
}
class ReviewRequestService private constructor() {
    companion object { private val I = ReviewRequestService(); fun getInstance() = I }
}
class DailyChallengeService private constructor() {
    companion object { private val I = DailyChallengeService(); fun getInstance() = I }
}
class SavedChallengesService private constructor() {
    companion object { private val I = SavedChallengesService(); fun getInstance() = I }
}
class DailyQuestionService private constructor() {
    fun configure(appState: AppState) {}
    companion object { private val I = DailyQuestionService(); fun getInstance() = I }
}
class WidgetService {
    fun refreshData() {}
    fun forceRefreshProfileImages() {}
}
class FCMService private constructor(private val ctx: Context) {
    fun initSilently() { /* no-op */ }
    companion object {
        private var I: FCMService? = null
        fun getInstance(ctx: Context) = I ?: FCMService(ctx.applicationContext).also { I = it }
    }
}
class AnalyticsService private constructor() {
    fun trackIntroContinue(screen: String) { /* ex: Firebase Analytics logEvent */ }
    companion object { private val I = AnalyticsService(); fun getInstance() = I }
}
class PartnerCodeService private constructor() {
    suspend fun checkForPendingConnectionMessage() { /* TODO */ }
    companion object { private val I = PartnerCodeService(); fun getInstance() = I }
}
