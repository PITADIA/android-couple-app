package com.love2loveapp.ui.views.managers

import android.content.Context
import android.util.Log
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.model.DailyQuestion
import com.love2loveapp.model.DailyChallenge
import com.love2loveapp.model.DailyContentRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 📅 DailyContentViewManager - Gestionnaire des Vues de Contenu Quotidien
 * 
 * Responsabilités :
 * - Gestion de toutes les vues DailyQuestion et DailyChallenge
 * - Navigation intelligente basée sur DailyContentRoute
 * - États réactifs pour questions et défis quotidiens
 * - Coordination avec ContentServiceManager
 * 
 * Architecture : Domain View Manager + Daily Content Flow + Reactive Navigation
 */
class DailyContentViewManager(
    private val context: Context,
    private val integratedAppState: IntegratedAppState
) : ViewManagerInterface {
    
    companion object {
        private const val TAG = "DailyContentViewManager"
    }
    
    // === Coroutine Scope ===
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // === Daily Content States ===
    private val _currentQuestionView = MutableStateFlow<DailyQuestionView>(DailyQuestionView.MAIN)
    val currentQuestionView: StateFlow<DailyQuestionView> = _currentQuestionView.asStateFlow()
    
    private val _currentChallengeView = MutableStateFlow<DailyChallengeView>(DailyChallengeView.MAIN)
    val currentChallengeView: StateFlow<DailyChallengeView> = _currentChallengeView.asStateFlow()
    
    private val _contentMode = MutableStateFlow<ContentMode>(ContentMode.QUESTION)
    val contentMode: StateFlow<ContentMode> = _contentMode.asStateFlow()
    
    // === Content Data States ===
    private val _currentQuestion = MutableStateFlow<Result<DailyQuestion?>>(Result.Loading())
    val currentQuestion: StateFlow<Result<DailyQuestion?>> = _currentQuestion.asStateFlow()
    
    private val _currentChallenge = MutableStateFlow<Result<DailyChallenge?>>(Result.Loading())
    val currentChallenge: StateFlow<Result<DailyChallenge?>> = _currentChallenge.asStateFlow()
    
    // === UI States ===
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall.asStateFlow()
    
    init {
        Log.d(TAG, "📅 Initialisation DailyContentViewManager")
    }
    
    // === ViewManagerInterface Implementation ===
    
    override fun initialize() {
        Log.d(TAG, "🚀 Initialisation Daily Content Views")
        initializeDailyContentStreams()
    }
    
    override suspend fun refresh() {
        Log.d(TAG, "🔄 Actualisation Daily Content Views")
        
        // Actualiser contenu via ContentServiceManager
        integratedAppState.contentServiceManager.refreshAllContent()
    }
    
    override fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "current_question_view" to _currentQuestionView.value.name,
            "current_challenge_view" to _currentChallengeView.value.name,
            "content_mode" to _contentMode.value.name,
            "current_question_loaded" to (_currentQuestion.value is Result.Success),
            "current_challenge_loaded" to (_currentChallenge.value is Result.Success),
            "is_loading" to _isLoading.value,
            "show_paywall" to _showPaywall.value
        )
    }
    
    // === Initialization ===
    
    /**
     * Initialiser les flux réactifs du contenu quotidien
     */
    private fun initializeDailyContentStreams() {
        Log.d(TAG, "🌊 Configuration flux contenu quotidien réactifs")
        
        // Observer contenu quotidien depuis ContentServiceManager
        observeDailyContent()
        
        // Observer routes de navigation automatique
        observeNavigationRoutes()
        
        // Observer état abonnement pour paywall
        observeSubscriptionForPaywall()
    }
    
    /**
     * Observer contenu quotidien
     */
    private fun observeDailyContent() {
        // Observer question du jour
        integratedAppState.currentDailyQuestion
            .onEach { questionResult ->
                Log.d(TAG, "📅 Question du jour: ${questionResult.javaClass.simpleName}")
                _currentQuestion.value = questionResult
                
                // Mettre à jour état loading
                _isLoading.value = (questionResult is Result.Loading)
            }
            .launchIn(scope)
        
        // Observer défi du jour
        integratedAppState.currentDailyChallenge
            .onEach { challengeResult ->
                Log.d(TAG, "🎯 Défi du jour: ${challengeResult.javaClass.simpleName}")
                _currentChallenge.value = challengeResult
                
                // Mettre à jour état loading
                _isLoading.value = (challengeResult is Result.Loading)
            }
            .launchIn(scope)
    }
    
    /**
     * Observer routes de navigation automatique
     */
    private fun observeNavigationRoutes() {
        // Observer route question
        integratedAppState.currentDailyQuestionRoute
            .onEach { route ->
                Log.d(TAG, "🧭 Route question: ${route.description}")
                handleQuestionRouteChange(route)
            }
            .launchIn(scope)
        
        // Observer route défi
        integratedAppState.currentDailyChallengeRoute
            .onEach { route ->
                Log.d(TAG, "🧭 Route défi: ${route.description}")
                handleChallengeRouteChange(route)
            }
            .launchIn(scope)
    }
    
    /**
     * Observer abonnement pour paywall
     */
    private fun observeSubscriptionForPaywall() {
        integratedAppState.currentUserResult
            .onEach { userResult ->
                when (userResult) {
                    is Result.Success -> {
                        val hasSubscription = userResult.data?.hasActiveSubscription == true
                        
                        // Cacher paywall si abonnement actif
                        if (hasSubscription && _showPaywall.value) {
                            _showPaywall.value = false
                        }
                    }
                    else -> {
                        // Pas d'action spécifique
                    }
                }
            }
            .launchIn(scope)
    }
    
    // === Navigation Route Handling ===
    
    /**
     * Gérer changement route question
     */
    private fun handleQuestionRouteChange(route: DailyContentRoute) {
        when (route) {
            is DailyContentRoute.Intro -> {
                _currentQuestionView.value = DailyQuestionView.INTRO
            }
            is DailyContentRoute.Main -> {
                _currentQuestionView.value = DailyQuestionView.MAIN
            }
            is DailyContentRoute.Paywall -> {
                _currentQuestionView.value = DailyQuestionView.PAYWALL
                _showPaywall.value = true
            }
            is DailyContentRoute.Error -> {
                _currentQuestionView.value = DailyQuestionView.ERROR
            }
            is DailyContentRoute.Loading -> {
                _isLoading.value = true
            }
        }
    }
    
    /**
     * Gérer changement route défi
     */
    private fun handleChallengeRouteChange(route: DailyContentRoute) {
        when (route) {
            is DailyContentRoute.Intro -> {
                _currentChallengeView.value = DailyChallengeView.INTRO
            }
            is DailyContentRoute.Main -> {
                _currentChallengeView.value = DailyChallengeView.MAIN
            }
            is DailyContentRoute.Paywall -> {
                _currentChallengeView.value = DailyChallengeView.PAYWALL
                _showPaywall.value = true
            }
            is DailyContentRoute.Error -> {
                _currentChallengeView.value = DailyChallengeView.ERROR
            }
            is DailyContentRoute.Loading -> {
                _isLoading.value = true
            }
        }
    }
    
    // === Content Actions ===
    
    /**
     * Changer mode de contenu (Question/Challenge)
     */
    fun switchContentMode(mode: ContentMode) {
        Log.d(TAG, "🔄 Changement mode contenu: ${mode.name}")
        _contentMode.value = mode
        
        // Logger pour analytics
        integratedAppState.systemServiceManager.logEvent(
            eventName = "daily_content_mode_switch",
            parameters = mapOf("mode" to mode.name)
        )
    }
    
    /**
     * Marquer question comme répondue
     */
    suspend fun markQuestionAsAnswered(questionId: String, answer: String): Result<Unit> {
        Log.d(TAG, "✅ Marquer question répondue: $questionId")
        
        return try {
            integratedAppState.contentServiceManager.markQuestionAsAnswered(questionId, answer)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur marquer question répondue", e)
            Result.Error(e)
        }
    }
    
    /**
     * Marquer défi comme terminé
     */
    suspend fun markChallengeAsCompleted(challengeId: String): Result<Unit> {
        Log.d(TAG, "✅ Marquer défi terminé: $challengeId")
        
        return try {
            integratedAppState.contentServiceManager.markChallengeAsCompleted(challengeId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur marquer défi terminé", e)
            Result.Error(e)
        }
    }
    
    /**
     * Ajouter question aux favoris
     */
    suspend fun addQuestionToFavorites(question: DailyQuestion): Result<Unit> {
        Log.d(TAG, "⭐ Ajouter question aux favoris: ${question.id}")
        
        return try {
            integratedAppState.contentServiceManager.addToFavorites(question)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ajouter aux favoris", e)
            Result.Error(e)
        }
    }
    
    /**
     * Sauvegarder défi
     */
    suspend fun saveChallenge(challenge: DailyChallenge): Result<Unit> {
        Log.d(TAG, "💾 Sauvegarder défi: ${challenge.id}")
        
        return try {
            integratedAppState.contentServiceManager.saveChallenge(challenge)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarder défi", e)
            Result.Error(e)
        }
    }
    
    // === View Navigation ===
    
    /**
     * Naviguer vers vue question spécifique
     */
    fun navigateToQuestionView(view: DailyQuestionView) {
        Log.d(TAG, "🧭 Navigation vers vue question: ${view.name}")
        _currentQuestionView.value = view
        _contentMode.value = ContentMode.QUESTION
    }
    
    /**
     * Naviguer vers vue défi spécifique
     */
    fun navigateToChallengeView(view: DailyChallengeView) {
        Log.d(TAG, "🧭 Navigation vers vue défi: ${view.name}")
        _currentChallengeView.value = view
        _contentMode.value = ContentMode.CHALLENGE
    }
    
    /**
     * Afficher paywall
     */
    fun showPaywall() {
        Log.d(TAG, "💰 Afficher paywall")
        _showPaywall.value = true
    }
    
    /**
     * Cacher paywall
     */
    fun hidePaywall() {
        Log.d(TAG, "❌ Cacher paywall")
        _showPaywall.value = false
    }
    
    /**
     * Naviguer vers intro et marquer comme vue
     */
    fun completeIntro(contentType: ContentMode) {
        Log.d(TAG, "✅ Compléter intro: ${contentType.name}")
        
        val introKey = when (contentType) {
            ContentMode.QUESTION -> "dailyQuestion"
            ContentMode.CHALLENGE -> "dailyChallenge"
        }
        
        integratedAppState.markIntroAsSeen(introKey)
    }
}

/**
 * Vues Daily Question disponibles
 */
enum class DailyQuestionView {
    MAIN,               // DailyQuestionMainView.kt
    INTRO,              // DailyQuestionIntroView.kt
    FLOW,               // DailyQuestionFlowView.kt
    PAYWALL,            // DailyQuestionPaywallView.kt
    ERROR,              // DailyQuestionErrorView.kt
    PARTNER_CODE,       // DailyQuestionPartnerCodeView.kt
    PERMISSION,         // DailyQuestionPermissionView.kt
    MESSAGEKIT          // DailyQuestionMessageKitView.kt
}

/**
 * Vues Daily Challenge disponibles
 */
enum class DailyChallengeView {
    MAIN,               // DailyChallengeMainView.kt
    INTRO,              // DailyChallengeIntroView.kt
    FLOW,               // DailyChallengeFlowView.kt
    CARD,               // DailyChallengeCardView.kt
    ERROR,              // DailyChallengeErrorView.kt
    LOADING,            // DailyChallengeLoadingView.kt
    INTEGRATED,         // IntegratedDailyChallengeView.kt
    SAVED               // SavedChallengesView.kt
}

/**
 * Modes de contenu quotidien
 */
enum class ContentMode {
    QUESTION,
    CHALLENGE
}
