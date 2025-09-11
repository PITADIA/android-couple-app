package com.love2loveapp.core.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.services.auth.AuthenticationService
import com.love2loveapp.core.services.performance.PerformanceMonitor
import com.love2loveapp.core.services.firebase.FirebaseCoordinator
import com.love2loveapp.domain.repository.UserRepository
import com.love2loveapp.domain.repository.LocationRepository
import com.love2loveapp.model.AppConstants
import com.love2loveapp.model.User
import com.love2loveapp.navigation.Route
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 🎯 IntegratedAppState - Hub Réactif Central de l'Application
 * 
 * Responsabilités :
 * - État global synchronisé avec Flow/StateFlow
 * - Coordination entre tous les services
 * - Point d'entrée unique pour les ViewModels
 * - Gestion du cycle de vie applicatif
 * 
 * Architecture : MVVM + Repository Pattern + Reactive Streams
 */
class IntegratedAppState(
    private val userRepository: UserRepository,
    private val locationRepository: LocationRepository,
    private val authenticationService: AuthenticationService,
    private val performanceMonitor: PerformanceMonitor,
    private val firebaseCoordinator: FirebaseCoordinator
) : ViewModel() {
    
    companion object {
        private const val TAG = "IntegratedAppState"
    }
    
    // === Exception Handler ===
    private val crashGuard = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Coroutine error: ${e.message}", e)
    }
    
    // === User State (Central) ===
    private val _currentUserResult = MutableStateFlow<Result<User?>>(Result.Loading)
    val currentUserResult: StateFlow<Result<User?>> = _currentUserResult.asStateFlow()
    
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    private val _hasPartner = MutableStateFlow(false)
    val hasPartner: StateFlow<Boolean> = _hasPartner.asStateFlow()
    
    // === Authentication State ===
    private val _authenticationResult = MutableStateFlow<Result<Boolean>>(Result.Loading)
    val authenticationResult: StateFlow<Result<Boolean>> = _authenticationResult.asStateFlow()
    
    // === Location State ===
    private val _currentLocation = MutableStateFlow<Result<android.location.Location?>>(Result.Loading)
    val currentLocation: StateFlow<Result<android.location.Location?>> = _currentLocation.asStateFlow()
    
    private val _partnerDistance = MutableStateFlow<Result<Double?>>(Result.Loading)
    val partnerDistance: StateFlow<Result<Double?>> = _partnerDistance.asStateFlow()
    
    // === App Lifecycle State ===
    private val _isAppReady = MutableStateFlow(false)
    val isAppReady: StateFlow<Boolean> = _isAppReady.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // === Navigation State ===
    /**
     * Route calculée automatiquement basée sur l'état global
     * Utilisée par AppNavigator pour la navigation
     */
    val currentRoute: StateFlow<Route> = combine(
        _isAuthenticated,
        _hasPartner,
        _isAppReady,
        _isLoading,
        _isOnboardingInProgress
    ) { isAuth, hasPartner, isReady, isLoading, onboarding ->
        calculateCurrentRoute(isAuth, hasPartner, isReady, isLoading, onboarding)
    }.asStateFlow()
    
    // === Onboarding State ===
    private val _isOnboardingInProgress = MutableStateFlow(false)
    val isOnboardingInProgress: StateFlow<Boolean> = _isOnboardingInProgress.asStateFlow()
    
    private val _introFlags = MutableStateFlow(IntroFlags.DEFAULT)
    val introFlags: StateFlow<IntroFlags> = _introFlags.asStateFlow()
    
    // === Services Exposure (pour compatibilité) ===
    val freemiumManager by lazy { 
        com.love2loveapp.di.ServiceContainer.createFreemiumManager(this)
    }
    
    val navigationManager by lazy {
        com.love2loveapp.di.ServiceContainer.createNavigationManager(this)
    }
    
    // === Service Managers (Domaines Métier) ===
    val partnerServiceManager by lazy { 
        com.love2loveapp.di.ServiceContainer.partnerServiceManager
    }
    
    val contentServiceManager by lazy { 
        com.love2loveapp.di.ServiceContainer.contentServiceManager
    }
    
    val systemServiceManager by lazy { 
        com.love2loveapp.di.ServiceContainer.systemServiceManager
    }
    
    // === Additional Managers (Tests, UI, Utils, ViewModels) ===
    val testManager by lazy { 
        com.love2loveapp.di.ServiceContainer.testManager
    }
    
    val utilsManager by lazy { 
        com.love2loveapp.di.ServiceContainer.utilsManager
    }
    
    val viewModelManager by lazy { 
        com.love2loveapp.di.ServiceContainer.viewModelManager
    }
    
    val uiManager by lazy {
        com.love2loveapp.di.ServiceContainer.createUIManager(this)
    }
    
    val viewManagerOrchestrator by lazy {
        com.love2loveapp.di.ServiceContainer.createViewManagerOrchestrator(this)
    }
    
    // === Navigation States (Exposés depuis NavigationManager) ===
    val currentDailyQuestionRoute by lazy { navigationManager.currentDailyQuestionRoute }
    val currentDailyChallengeRoute by lazy { navigationManager.currentDailyChallengeRoute }
    val currentSheet by lazy { navigationManager.currentSheet }
    val isSheetPresented by lazy { navigationManager.isSheetPresented }
    val currentTab by lazy { navigationManager.currentTab }
    
    // === Partner States (Exposés depuis PartnerServiceManager) ===
    val hasConnectedPartner by lazy { partnerServiceManager.hasConnectedPartner }
    val partnerInfo by lazy { partnerServiceManager.partnerInfo }
    val partnerLocation by lazy { partnerServiceManager.partnerLocation }
    val partnerConnectionStatus by lazy { partnerServiceManager.partnerConnectionStatus }
    
    // === Content States (Exposés depuis ContentServiceManager) ===
    val currentDailyQuestion by lazy { contentServiceManager.currentDailyQuestion }
    val currentDailyChallenge by lazy { contentServiceManager.currentDailyChallenge }
    val favoriteQuestions by lazy { contentServiceManager.favoriteQuestions }
    val journalEntries by lazy { contentServiceManager.journalEntries }
    val savedChallenges by lazy { contentServiceManager.savedChallenges }
    
    // === System States (Exposés depuis SystemServiceManager) ===
    val analyticsEnabled by lazy { systemServiceManager.analyticsEnabled }
    val notificationsEnabled by lazy { systemServiceManager.notificationsEnabled }
    val systemHealth by lazy { systemServiceManager.systemHealth }
    val currentLanguage by lazy { systemServiceManager.currentLanguage }
    
    init {
        Log.d(TAG, "🚀 IntegratedAppState: Initialisation du hub central")
        initializeReactiveStreams()
        startApplicationFlow()
    }
    
    // === Reactive Streams Setup ===
    
    /**
     * Configuration des flux réactifs entre services
     */
    private fun initializeReactiveStreams() {
        Log.d(TAG, "🌊 Configuration des flux réactifs")
        
        // 1. Observer les changements d'authentification
        observeAuthenticationChanges()
        
        // 2. Observer les changements utilisateur
        observeUserChanges()
        
        // 3. Observer les changements de localisation
        observeLocationChanges()
        
        // 4. Synchroniser l'état global
        synchronizeGlobalState()
    }
    
    /**
     * Observer les changements d'authentification
     */
    private fun observeAuthenticationChanges() {
        authenticationService.authStateFlow
            .onEach { authResult ->
                Log.d(TAG, "🔐 Changement d'authentification: $authResult")
                _authenticationResult.value = authResult
                
                when (authResult) {
                    is Result.Success -> {
                        _isAuthenticated.value = authResult.data
                        if (authResult.data) {
                            loadCurrentUser()
                        } else {
                            _currentUserResult.value = Result.Success(null)
                        }
                    }
                    is Result.Error -> {
                        _isAuthenticated.value = false
                        _currentUserResult.value = Result.Error(authResult.exception)
                    }
                    is Result.Loading -> {
                        _isAuthenticated.value = false
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Observer les changements utilisateur
     */
    private fun observeUserChanges() {
        userRepository.currentUserFlow
            .onEach { userResult ->
                Log.d(TAG, "👤 Changement utilisateur: $userResult")
                _currentUserResult.value = userResult
                
                when (userResult) {
                    is Result.Success -> {
                        val user = userResult.data
                        _hasPartner.value = user?.partnerId?.isNotEmpty() == true
                        
                        // Si utilisateur connecté avec partenaire, démarrer location
                        if (user != null && _hasPartner.value) {
                            startLocationTracking()
                        }
                    }
                    is Result.Error -> {
                        _hasPartner.value = false
                    }
                    is Result.Loading -> {
                        // Garde l'état actuel pendant le chargement
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Observer les changements de localisation
     */
    private fun observeLocationChanges() {
        locationRepository.currentLocationFlow
            .onEach { locationResult ->
                Log.d(TAG, "📍 Changement localisation: $locationResult")
                _currentLocation.value = locationResult
                
                // Calculer distance avec partenaire si disponible
                if (locationResult is Result.Success && _hasPartner.value) {
                    calculatePartnerDistance()
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Synchroniser l'état global de l'app
     */
    private fun synchronizeGlobalState() {
        // Combiner auth + user + location pour déterminer si l'app est prête
        combine(
            _isAuthenticated,
            _currentUserResult,
            _currentLocation
        ) { isAuth, userResult, locationResult ->
            val isUserReady = userResult is Result.Success
            val isLocationReady = locationResult is Result.Success || locationResult is Result.Error
            
            isAuth && isUserReady && isLocationReady
        }
            .onEach { isReady ->
                Log.d(TAG, "🎯 État app prête: $isReady")
                _isAppReady.value = isReady
                _isLoading.value = !isReady
            }
            .launchIn(viewModelScope)
    }
    
    // === Public Methods (API pour ViewModels) ===
    
    /**
     * Calcule la route appropriée basée sur l'état global
     */
    private fun calculateCurrentRoute(
        isAuth: Boolean,
        hasPartner: Boolean,
        isReady: Boolean,
        isLoading: Boolean,
        onboarding: Boolean
    ): Route {
        return when {
            isLoading -> Route.Splash
            !isAuth -> Route.Onboarding
            onboarding -> Route.Onboarding
            !hasPartner -> Route.PartnerConnection
            else -> Route.Main
        }
    }

    /**
     * Démarrer le flow principal de l'application
     */
    fun startApplicationFlow() {
        viewModelScope.launch(crashGuard) {
            Log.d(TAG, "🚀 Démarrage du flow principal")
            
            // 1. Initialiser Firebase
            firebaseCoordinator.initialize()
            
            // 2. Vérifier l'authentification
            authenticationService.checkCurrentAuth()
            
            // 3. Démarrer monitoring performance
            performanceMonitor.startMonitoring()
        }
    }
    
    /**
     * Charger l'utilisateur actuel
     */
    fun loadCurrentUser() {
        viewModelScope.launch(crashGuard) {
            Log.d(TAG, "👤 Chargement utilisateur actuel")
            userRepository.getCurrentUser()
        }
    }
    
    /**
     * Démarrer le tracking de localisation
     */
    fun startLocationTracking() {
        viewModelScope.launch(crashGuard) {
            Log.d(TAG, "📍 Démarrage tracking localisation")
            locationRepository.startLocationTracking()
        }
    }
    
    /**
     * Calculer la distance avec le partenaire
     */
    private fun calculatePartnerDistance() {
        viewModelScope.launch(crashGuard) {
            try {
                val distance = locationRepository.calculatePartnerDistance()
                _partnerDistance.value = Result.Success(distance)
            } catch (e: Exception) {
                _partnerDistance.value = Result.Error(AppException.LocationError("Distance calculation failed", e))
            }
        }
    }
    
    /**
     * Authentifier l'utilisateur
     */
    fun authenticate(email: String, password: String) {
        viewModelScope.launch(crashGuard) {
            Log.d(TAG, "🔐 Authentification utilisateur")
            authenticationService.signIn(email, password)
        }
    }
    
    /**
     * Déconnexion
     */
    fun signOut() {
        viewModelScope.launch(crashGuard) {
            Log.d(TAG, "🚪 Déconnexion utilisateur")
            authenticationService.signOut()
            
            // Reset states
            _currentUserResult.value = Result.Success(null)
            _hasPartner.value = false
            _currentLocation.value = Result.Loading
            _partnerDistance.value = Result.Loading
        }
    }
    
    /**
     * Marquer intro comme vue
     */
    fun markIntroAsSeen(introType: String) {
        viewModelScope.launch(crashGuard) {
            val currentFlags = _introFlags.value
            val updatedFlags = when (introType) {
                "dailyQuestion" -> currentFlags.copy(dailyQuestion = true)
                "dailyChallenge" -> currentFlags.copy(dailyChallenge = true)
                else -> currentFlags
            }
            _introFlags.value = updatedFlags
            
            // Sauvegarder en cache
            // TODO: Implémenter sauvegarde persistante
        }
    }
    
    // === Helper Methods ===
    
    /**
     * Obtenir l'utilisateur actuel (safe)
     */
    fun getCurrentUser(): User? {
        return when (val result = _currentUserResult.value) {
            is Result.Success -> result.data
            else -> null
        }
    }
    
    /**
     * Vérifier si l'utilisateur a un abonnement
     */
    fun hasActiveSubscription(): Boolean {
        return getCurrentUser()?.hasActiveSubscription == true
    }
    
    /**
     * État de debug des services
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "isAuthenticated" to _isAuthenticated.value,
            "hasPartner" to _hasPartner.value,
            "isAppReady" to _isAppReady.value,
            "isLoading" to _isLoading.value,
            "currentUser" to (getCurrentUser()?.email ?: "null"),
            "introFlags" to _introFlags.value
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 Nettoyage IntegratedAppState")
        performanceMonitor.stopMonitoring()
    }
}

// === IntroFlags Data Class ===
data class IntroFlags(
    val dailyQuestion: Boolean = false,
    val dailyChallenge: Boolean = false
) {
    companion object {
        val DEFAULT = IntroFlags()
    }
}
