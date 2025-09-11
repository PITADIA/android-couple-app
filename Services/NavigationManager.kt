package com.love2loveapp.core.services.navigation

import android.content.Context
import android.util.Log
import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.model.DailyContentRoute
import com.love2loveapp.model.DailyContentRouteCalculator
import com.love2loveapp.model.SheetType
import com.love2loveapp.core.common.Result
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
 * 🧭 NavigationManager - Service de Navigation Réactive
 * 
 * Responsabilités :
 * - Calcul automatique des routes basé sur l'état global
 * - Gestion des modales et sheets
 * - Navigation réactive avec Flow/StateFlow
 * - Intégration complète avec IntegratedAppState
 * 
 * Architecture : Navigation + State Management + Reactive Streams
 */
class NavigationManager(
    private val context: Context,
    private val appState: IntegratedAppState
) {
    
    companion object {
        private const val TAG = "NavigationManager"
    }
    
    // === Coroutine Scope ===
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // === Navigation States ===
    private val _currentDailyQuestionRoute = MutableStateFlow<DailyContentRoute>(DailyContentRoute.Loading)
    val currentDailyQuestionRoute: StateFlow<DailyContentRoute> = _currentDailyQuestionRoute.asStateFlow()
    
    private val _currentDailyChallengeRoute = MutableStateFlow<DailyContentRoute>(DailyContentRoute.Loading)
    val currentDailyChallengeRoute: StateFlow<DailyContentRoute> = _currentDailyChallengeRoute.asStateFlow()
    
    // === Sheet Management ===
    private val _currentSheet = MutableStateFlow<SheetType?>(null)
    val currentSheet: StateFlow<SheetType?> = _currentSheet.asStateFlow()
    
    private val _isSheetPresented = MutableStateFlow(false)
    val isSheetPresented: StateFlow<Boolean> = _isSheetPresented.asStateFlow()
    
    // === Tab Navigation ===
    private val _currentTab = MutableStateFlow(TabRoute.HOME)
    val currentTab: StateFlow<TabRoute> = _currentTab.asStateFlow()
    
    init {
        Log.d(TAG, "🧭 Initialisation NavigationManager")
        initializeNavigationStreams()
    }
    
    // === Navigation Route Calculation ===
    
    /**
     * Initialiser les flux de navigation réactifs
     */
    private fun initializeNavigationStreams() {
        Log.d(TAG, "🌊 Configuration flux navigation réactifs")
        
        // Observer changements pour Daily Question
        observeDailyQuestionNavigation()
        
        // Observer changements pour Daily Challenge  
        observeDailyChallengeNavigation()
        
        // Observer changements de sheets
        observeSheetNavigation()
    }
    
    /**
     * Observer navigation Daily Question
     */
    private fun observeDailyQuestionNavigation() {
        combine(
            appState.currentUserResult,
            appState.hasPartner,
            appState.introFlags
        ) { userResult, hasPartner, introFlags ->
            calculateDailyContentRoute(
                contentType = DailyContentRouteCalculator.ContentType.DAILY_QUESTION,
                userResult = userResult,
                hasPartner = hasPartner,
                hasSeenIntro = introFlags.dailyQuestion
            )
        }
        .onEach { route ->
            Log.d(TAG, "📅 Daily Question route: ${route.description}")
            _currentDailyQuestionRoute.value = route
        }
        .launchIn(scope)
    }
    
    /**
     * Observer navigation Daily Challenge
     */
    private fun observeDailyChallengeNavigation() {
        combine(
            appState.currentUserResult,
            appState.hasPartner,
            appState.introFlags
        ) { userResult, hasPartner, introFlags ->
            calculateDailyContentRoute(
                contentType = DailyContentRouteCalculator.ContentType.DAILY_CHALLENGE,
                userResult = userResult,
                hasPartner = hasPartner,
                hasSeenIntro = introFlags.dailyChallenge
            )
        }
        .onEach { route ->
            Log.d(TAG, "🎯 Daily Challenge route: ${route.description}")
            _currentDailyChallengeRoute.value = route
        }
        .launchIn(scope)
    }
    
    /**
     * Observer navigation des sheets
     */
    private fun observeSheetNavigation() {
        // Observer les changements d'abonnement pour gérer les paywalls
        appState.currentUserResult
            .onEach { userResult ->
                when (userResult) {
                    is Result.Success -> {
                        val user = userResult.data
                        val hasSubscription = user?.hasActiveSubscription == true
                        
                        // Auto-fermer paywall si abonnement activé
                        if (hasSubscription && _currentSheet.value is SheetType.Paywall) {
                            dismissSheet()
                        }
                    }
                    else -> {
                        // Pas d'action spécifique
                    }
                }
            }
            .launchIn(scope)
    }
    
    /**
     * Calculer route Daily Content basée sur l'état
     */
    private fun calculateDailyContentRoute(
        contentType: DailyContentRouteCalculator.ContentType,
        userResult: Result<com.love2loveapp.model.User?>,
        hasPartner: Boolean,
        hasSeenIntro: Boolean
    ): DailyContentRoute {
        
        // 1. Vérifier état utilisateur
        when (userResult) {
            is Result.Loading -> return DailyContentRoute.Loading
            is Result.Error -> return DailyContentRoute.Error(
                userResult.exception.message ?: "User error"
            )
            is Result.Success -> {
                val user = userResult.data
                if (user == null) {
                    return DailyContentRoute.Error("User not found")
                }
                
                // 2. Calculer via DailyContentRouteCalculator
                return DailyContentRouteCalculator.calculateRoute(
                    context = context,
                    contentType = contentType,
                    hasConnectedPartner = hasPartner,
                    hasSeenIntro = hasSeenIntro,
                    shouldShowPaywall = !appState.hasActiveSubscription(),
                    paywallDay = calculateCurrentDay(contentType),
                    serviceHasError = false, // TODO: Intégrer avec service errors
                    serviceErrorMessage = null,
                    serviceIsLoading = appState.isLoading.value
                )
            }
        }
    }
    
    /**
     * Calculer jour actuel pour paywall
     */
    private fun calculateCurrentDay(contentType: DailyContentRouteCalculator.ContentType): Int {
        // TODO: Intégrer avec DailyQuestionService/DailyChallengeService
        return when (contentType) {
            DailyContentRouteCalculator.ContentType.DAILY_QUESTION -> 1
            DailyContentRouteCalculator.ContentType.DAILY_CHALLENGE -> 1
        }
    }
    
    // === Sheet Management ===
    
    /**
     * Présenter une sheet
     */
    fun presentSheet(sheetType: SheetType) {
        Log.d(TAG, "📋 Présenter sheet: ${sheetType.javaClass.simpleName}")
        _currentSheet.value = sheetType
        _isSheetPresented.value = true
    }
    
    /**
     * Fermer la sheet actuelle
     */
    fun dismissSheet() {
        Log.d(TAG, "❌ Fermer sheet")
        _currentSheet.value = null
        _isSheetPresented.value = false
    }
    
    /**
     * Présenter paywall pour contenu premium
     */
    fun presentPaywall(contentType: DailyContentRouteCalculator.ContentType, day: Int) {
        val paywallType = when (contentType) {
            DailyContentRouteCalculator.ContentType.DAILY_QUESTION -> SheetType.Paywall.DailyQuestion(day)
            DailyContentRouteCalculator.ContentType.DAILY_CHALLENGE -> SheetType.Paywall.DailyChallenge(day)
        }
        presentSheet(paywallType)
    }
    
    // === Tab Navigation ===
    
    /**
     * Naviguer vers un onglet
     */
    fun navigateToTab(tab: TabRoute) {
        Log.d(TAG, "🏠 Navigation onglet: ${tab.name}")
        _currentTab.value = tab
    }
    
    // === Manual Navigation Overrides ===
    
    /**
     * Forcer navigation vers Daily Question Main (bypass auto-calculation)
     */
    fun forceNavigateToDailyQuestionMain() {
        Log.d(TAG, "🔄 Force navigation Daily Question Main")
        _currentDailyQuestionRoute.value = DailyContentRoute.Main
    }
    
    /**
     * Forcer navigation vers Daily Challenge Main (bypass auto-calculation)
     */
    fun forceNavigateToDailyChallengeMain() {
        Log.d(TAG, "🔄 Force navigation Daily Challenge Main")
        _currentDailyChallengeRoute.value = DailyContentRoute.Main
    }
    
    /**
     * Marquer intro comme vue et recalculer navigation
     */
    fun markIntroAsSeen(contentType: DailyContentRouteCalculator.ContentType) {
        Log.d(TAG, "✅ Marquer intro vue: ${contentType.name}")
        
        val introKey = when (contentType) {
            DailyContentRouteCalculator.ContentType.DAILY_QUESTION -> "dailyQuestion"
            DailyContentRouteCalculator.ContentType.DAILY_CHALLENGE -> "dailyChallenge"
        }
        
        appState.markIntroAsSeen(introKey)
        // Navigation sera recalculée automatiquement via les flows
    }
    
    // === Debug Methods ===
    
    /**
     * État de debug du NavigationManager
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "currentDailyQuestionRoute" to _currentDailyQuestionRoute.value.description,
            "currentDailyChallengeRoute" to _currentDailyChallengeRoute.value.description,
            "currentSheet" to (_currentSheet.value?.javaClass?.simpleName ?: "null"),
            "isSheetPresented" to _isSheetPresented.value,
            "currentTab" to _currentTab.value.name
        )
    }
}

/**
 * Routes des onglets principaux
 */
enum class TabRoute {
    HOME,
    DAILY_QUESTION,
    DAILY_CHALLENGE,
    JOURNAL,
    PROFILE
}
