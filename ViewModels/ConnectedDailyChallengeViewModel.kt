package com.love2loveapp.core.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.services.DailyQuestionService
import com.love2loveapp.core.services.firebase.FirebaseFunctionsService
import com.love2loveapp.domain.repository.UserRepository
import com.love2loveapp.model.DailyChallenge
import com.love2loveapp.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 🎯 ConnectedDailyChallengeViewModel - ViewModel Intégré avec DI
 * 
 * Responsabilités :
 * - Gestion des défis quotidiens avec état réactif
 * - Integration avec AppState central
 * - Communication avec Repository layer
 * - Synchronisation automatique des données
 * 
 * Architecture : MVVM + Repository + Reactive Streams
 */
class ConnectedDailyChallengeViewModel(
    private val userRepository: UserRepository,
    private val dailyQuestionService: DailyQuestionService,
    private val questionDataManager: QuestionDataManager,
    private val firebaseFunctionsService: FirebaseFunctionsService
) : ViewModel() {
    
    companion object {
        private const val TAG = "ConnectedDailyChallengeVM"
    }
    
    // === State Management ===
    private val _currentChallenge = MutableStateFlow<DailyChallenge?>(null)
    val currentChallenge: StateFlow<DailyChallenge?> = _currentChallenge.asStateFlow()
    
    private val _challengeHistory = MutableStateFlow<List<DailyChallenge>>(emptyList())
    val challengeHistory: StateFlow<List<DailyChallenge>> = _challengeHistory.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _canAccessPremiumChallenges = MutableStateFlow(false)
    val canAccessPremiumChallenges: StateFlow<Boolean> = _canAccessPremiumChallenges.asStateFlow()
    
    // === Navigation State (depuis NavigationManager) ===
    private val _currentRoute = MutableStateFlow<DailyContentRoute>(DailyContentRoute.Loading)
    val currentRoute: StateFlow<DailyContentRoute> = _currentRoute.asStateFlow()
    
    // === AppState Integration ===
    private var integratedAppState: IntegratedAppState? = null
    
    init {
        Log.d(TAG, "🎯 Initialisation ConnectedDailyChallengeViewModel")
        observeRepositoryChanges()
    }
    
    // === AppState Configuration ===
    
    /**
     * Configuration avec IntegratedAppState - Point central d'intégration
     */
    fun configureWithAppState(appState: IntegratedAppState) {
        Log.d(TAG, "🔗 Configuration avec IntegratedAppState")
        integratedAppState = appState
        
        // Observer les changements d'utilisateur depuis AppState
        observeUserChanges(appState)
        
        // Observer les changements d'abonnement
        observeSubscriptionChanges(appState)
        
        // Observer les changements de navigation automatique
        observeNavigationChanges(appState)
        
        // Charger les données initiales si utilisateur connecté
        appState.getCurrentUser()?.let { user ->
            loadChallengesForUser(user)
        }
    }
    
    // === Public Methods ===
    
    /**
     * Charger le défi du jour pour l'utilisateur
     */
    fun loadTodaysChallenge() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d(TAG, "📅 Chargement défi du jour")
                
                val currentUser = integratedAppState?.getCurrentUser()
                if (currentUser == null) {
                    _errorMessage.value = "User not authenticated"
                    return@launch
                }
                
                // Vérifier si partenaire connecté
                if (currentUser.partnerId.isNullOrEmpty()) {
                    _errorMessage.value = "Partner not connected"
                    return@launch
                }
                
                // Générer/récupérer défi via Firebase Functions
                val challengeResult = generateDailyChallenge(currentUser)
                
                when (challengeResult) {
                    is Result.Success -> {
                        _currentChallenge.value = challengeResult.data
                        Log.d(TAG, "✅ Défi du jour chargé: ${challengeResult.data?.title}")
                    }
                    is Result.Error -> {
                        _errorMessage.value = challengeResult.exception.message
                        Log.e(TAG, "❌ Erreur chargement défi: ${challengeResult.exception.message}")
                    }
                    is Result.Loading -> {
                        // Reste en loading
                    }
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load today's challenge"
                Log.e(TAG, "💥 Exception chargement défi", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Marquer un défi comme complété
     */
    fun markChallengeAsCompleted(challengeId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "✅ Marquage défi complété: $challengeId")
                
                val result = firebaseFunctionsService.markChallengeCompleted(challengeId)
                
                when (result) {
                    is Result.Success -> {
                        // Mettre à jour le défi local
                        _currentChallenge.value?.let { challenge ->
                            if (challenge.id == challengeId) {
                                _currentChallenge.value = challenge.copy(isCompleted = true)
                            }
                        }
                        Log.d(TAG, "✅ Défi marqué comme complété")
                    }
                    is Result.Error -> {
                        _errorMessage.value = "Failed to mark challenge as completed"
                        Log.e(TAG, "❌ Erreur marquage défi complété: ${result.exception.message}")
                    }
                    is Result.Loading -> {
                        // En cours
                    }
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to mark challenge as completed"
                Log.e(TAG, "💥 Exception marquage défi complété", e)
            }
        }
    }
    
    /**
     * Charger l'historique des défis
     */
    fun loadChallengeHistory() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "📚 Chargement historique défis")
                
                val currentUser = integratedAppState?.getCurrentUser()
                if (currentUser == null) {
                    _errorMessage.value = "User not authenticated"
                    return@launch
                }
                
                val historyResult = firebaseFunctionsService.getChallengeHistory(currentUser.uid)
                
                when (historyResult) {
                    is Result.Success -> {
                        _challengeHistory.value = historyResult.data
                        Log.d(TAG, "✅ Historique défis chargé: ${historyResult.data.size} défis")
                    }
                    is Result.Error -> {
                        _errorMessage.value = "Failed to load challenge history"
                        Log.e(TAG, "❌ Erreur chargement historique: ${historyResult.exception.message}")
                    }
                    is Result.Loading -> {
                        // En cours
                    }
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load challenge history"
                Log.e(TAG, "💥 Exception chargement historique", e)
            }
        }
    }
    
    /**
     * Rafraîchir les données
     */
    fun refreshData() {
        Log.d(TAG, "🔄 Rafraîchissement données")
        loadTodaysChallenge()
        loadChallengeHistory()
    }
    
    /**
     * Effacer les erreurs
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    // === Private Methods ===
    
    /**
     * Observer les changements de repository
     */
    private fun observeRepositoryChanges() {
        // Observer les changements d'utilisateur du repository
        userRepository.currentUserFlow
            .onEach { userResult ->
                when (userResult) {
                    is Result.Success -> {
                        val user = userResult.data
                        if (user != null) {
                            loadChallengesForUser(user)
                        }
                    }
                    is Result.Error -> {
                        _errorMessage.value = "User data error: ${userResult.exception.message}"
                    }
                    is Result.Loading -> {
                        // User en cours de chargement
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Observer les changements d'utilisateur depuis AppState
     */
    private fun observeUserChanges(appState: IntegratedAppState) {
        combine(
            appState.currentUserResult,
            appState.hasPartner
        ) { userResult, hasPartner ->
            Pair(userResult, hasPartner)
        }
            .onEach { (userResult, hasPartner) ->
                when (userResult) {
                    is Result.Success -> {
                        val user = userResult.data
                        if (user != null && hasPartner) {
                            Log.d(TAG, "👤 Utilisateur avec partenaire détecté, chargement défis")
                            loadChallengesForUser(user)
                        } else if (user != null && !hasPartner) {
                            Log.d(TAG, "👤 Utilisateur sans partenaire, pas de défis")
                            _currentChallenge.value = null
                            _challengeHistory.value = emptyList()
                        }
                    }
                    is Result.Error -> {
                        _errorMessage.value = "User authentication error"
                        _currentChallenge.value = null
                    }
                    is Result.Loading -> {
                        _isLoading.value = true
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Observer les changements d'abonnement
     */
    private fun observeSubscriptionChanges(appState: IntegratedAppState) {
        appState.currentUserResult
            .onEach { userResult ->
                when (userResult) {
                    is Result.Success -> {
                        val user = userResult.data
                        val hasSubscription = user?.hasActiveSubscription == true
                        _canAccessPremiumChallenges.value = hasSubscription
                        
                        Log.d(TAG, "💎 Abonnement actif: $hasSubscription")
                    }
                    else -> {
                        _canAccessPremiumChallenges.value = false
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Observer les changements de navigation automatique
     */
    private fun observeNavigationChanges(appState: IntegratedAppState) {
        appState.currentDailyChallengeRoute
            .onEach { route ->
                Log.d(TAG, "🧭 Navigation route changed: ${route.description}")
                _currentRoute.value = route
                
                // Réagir aux changements de route
                handleRouteChange(route)
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Gérer les changements de route automatiques
     */
    private fun handleRouteChange(route: DailyContentRoute) {
        when (route) {
            is DailyContentRoute.Main -> {
                Log.d(TAG, "🎯 Route Main - Charger défi du jour")
                loadTodaysChallenge()
            }
            is DailyContentRoute.Paywall -> {
                Log.d(TAG, "💰 Route Paywall - Jour ${route.day}")
                // Paywall géré par NavigationManager
            }
            is DailyContentRoute.Error -> {
                Log.e(TAG, "❌ Route Error: ${route.message}")
                _errorMessage.value = route.message
            }
            is DailyContentRoute.Loading -> {
                Log.d(TAG, "⏳ Route Loading")
                _isLoading.value = true
            }
            is DailyContentRoute.Intro -> {
                Log.d(TAG, "👋 Route Intro - ShowConnect: ${route.showConnect}")
                // Intro géré par NavigationManager
            }
        }
    }
    
    /**
     * Charger les défis pour un utilisateur spécifique
     */
    private fun loadChallengesForUser(user: User) {
        viewModelScope.launch {
            Log.d(TAG, "👤 Chargement défis pour utilisateur: ${user.email}")
            
            // Charger défi du jour
            loadTodaysChallenge()
            
            // Charger historique en arrière-plan
            loadChallengeHistory()
        }
    }
    
    /**
     * Générer/récupérer défi quotidien via Firebase Functions
     */
    private suspend fun generateDailyChallenge(user: User): Result<DailyChallenge> {
        return try {
            Log.d(TAG, "🎲 Génération défi quotidien")
            
            val params = mapOf(
                "userId" to user.uid,
                "partnerId" to (user.partnerId ?: ""),
                "timezone" to "Europe/Paris", // TODO: Récupérer timezone utilisateur
                "language" to "fr" // TODO: Récupérer langue utilisateur
            )
            
            firebaseFunctionsService.callFunction("generateDailyChallenge", params)
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception génération défi", e)
            Result.Error(AppException.NetworkError("Failed to generate daily challenge", e))
        }
    }
    
    // === Debug Methods ===
    
    /**
     * Obtenir l'état de debug du ViewModel
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "currentChallenge" to (_currentChallenge.value?.title ?: "null"),
            "challengeHistoryCount" to _challengeHistory.value.size,
            "isLoading" to _isLoading.value,
            "errorMessage" to (_errorMessage.value ?: "null"),
            "canAccessPremium" to _canAccessPremiumChallenges.value,
            "isAppStateConfigured" to (integratedAppState != null)
        )
    }
}
