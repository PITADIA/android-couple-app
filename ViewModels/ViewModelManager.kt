package com.love2loveapp.core.viewmodels.managers

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.viewmodels.*
import com.love2loveapp.di.ServiceContainer
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
 * 🎯 ViewModelManager - Gestionnaire Centralisé des ViewModels
 * 
 * Responsabilités :
 * - Factory centralisée pour tous les ViewModels
 * - Coordination et synchronisation entre ViewModels
 * - États réactifs globaux des ViewModels
 * - Lifecycle management et cleanup automatique
 * 
 * Architecture : ViewModel Manager + Factory Pattern + Reactive Coordination
 */
class ViewModelManager(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "ViewModelManager"
        
        @Volatile
        private var INSTANCE: ViewModelManager? = null
        
        fun getInstance(context: Context): ViewModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ViewModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // === Coroutine Scope ===
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // === ViewModels Registry ===
    private val viewModelsRegistry = mutableMapOf<String, ViewModel>()
    
    // === ViewModels States ===
    private val _integratedAppState = MutableStateFlow<IntegratedAppState?>(null)
    val integratedAppState: StateFlow<IntegratedAppState?> = _integratedAppState.asStateFlow()
    
    private val _activeViewModels = MutableStateFlow<List<String>>(emptyList())
    val activeViewModels: StateFlow<List<String>> = _activeViewModels.asStateFlow()
    
    private val _viewModelHealth = MutableStateFlow<ViewModelHealthStatus>(ViewModelHealthStatus.UNKNOWN)
    val viewModelHealth: StateFlow<ViewModelHealthStatus> = _viewModelHealth.asStateFlow()
    
    // === Factory ===
    private val viewModelFactory = IntegratedViewModelFactory()
    
    init {
        Log.d(TAG, "🎯 Initialisation ViewModelManager")
        initializeViewModelStreams()
    }
    
    // === Initialization ===
    
    /**
     * Initialiser les flux réactifs des ViewModels
     */
    private fun initializeViewModelStreams() {
        Log.d(TAG, "🌊 Configuration flux ViewModels réactifs")
        
        // Observer santé ViewModels
        observeViewModelHealth()
        
        // Créer IntegratedAppState central
        createCentralAppState()
    }
    
    /**
     * Créer AppState central
     */
    private fun createCentralAppState() {
        Log.d(TAG, "🚀 Création IntegratedAppState central")
        
        val appState = ServiceContainer.createAppState()
        _integratedAppState.value = appState
        
        // Enregistrer dans registry
        registerViewModel("IntegratedAppState", appState)
    }
    
    /**
     * Observer santé ViewModels
     */
    private fun observeViewModelHealth() {
        _activeViewModels
            .onEach { activeVMs ->
                val health = calculateViewModelHealth(activeVMs)
                Log.d(TAG, "🏥 ViewModel health: ${health.name}")
                _viewModelHealth.value = health
            }
            .launchIn(scope)
    }
    
    /**
     * Calculer santé ViewModels
     */
    private fun calculateViewModelHealth(activeViewModels: List<String>): ViewModelHealthStatus {
        return when {
            activeViewModels.isEmpty() -> ViewModelHealthStatus.UNKNOWN
            activeViewModels.size > 10 -> ViewModelHealthStatus.WARNING // Trop de ViewModels actifs
            viewModelsRegistry.size > 20 -> ViewModelHealthStatus.CRITICAL // Memory leak potentiel
            else -> ViewModelHealthStatus.HEALTHY
        }
    }
    
    // === ViewModel Factory ===
    
    /**
     * Créer ou récupérer ViewModel
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : ViewModel> getViewModel(viewModelClass: Class<T>, key: String? = null): T {
        val registryKey = key ?: viewModelClass.simpleName
        
        return viewModelsRegistry[registryKey] as? T ?: run {
            Log.d(TAG, "🏭 Création ViewModel: $registryKey")
            val viewModel = viewModelFactory.create(viewModelClass)
            registerViewModel(registryKey, viewModel)
            viewModel
        }
    }
    
    /**
     * Enregistrer ViewModel
     */
    private fun registerViewModel(key: String, viewModel: ViewModel) {
        viewModelsRegistry[key] = viewModel
        updateActiveViewModels()
        
        Log.d(TAG, "📝 ViewModel enregistré: $key (Total: ${viewModelsRegistry.size})")
    }
    
    /**
     * Mettre à jour liste ViewModels actifs
     */
    private fun updateActiveViewModels() {
        _activeViewModels.value = viewModelsRegistry.keys.toList()
    }
    
    // === Specific ViewModels Getters ===
    
    /**
     * Obtenir IntegratedAppState
     */
    fun getIntegratedAppState(): IntegratedAppState {
        return _integratedAppState.value ?: run {
            createCentralAppState()
            _integratedAppState.value!!
        }
    }
    
    /**
     * Obtenir ConnectedDailyChallengeViewModel
     */
    fun getConnectedDailyChallengeViewModel(): ConnectedDailyChallengeViewModel {
        return getViewModel(ConnectedDailyChallengeViewModel::class.java)
    }
    
    /**
     * Obtenir OnboardingViewModel
     */
    fun getOnboardingViewModel(): OnboardingViewModel {
        return getViewModel(OnboardingViewModel::class.java)
    }
    
    /**
     * Obtenir FreemiumManager
     */
    fun getFreemiumManager(): FreemiumManager {
        return getViewModel(FreemiumManager::class.java)
    }
    
    /**
     * Obtenir WidgetsViewModel
     */
    fun getWidgetsViewModel(): WidgetsViewModel {
        return getViewModel(WidgetsViewModel::class.java)
    }
    
    // === ViewModel Coordination ===
    
    /**
     * Synchroniser tous les ViewModels avec AppState
     */
    fun synchronizeViewModels() {
        Log.d(TAG, "🔄 Synchronisation ViewModels")
        
        val appState = getIntegratedAppState()
        
        viewModelsRegistry.values.forEach { viewModel ->
            when (viewModel) {
                is ConnectedDailyChallengeViewModel -> {
                    viewModel.configureWithAppState(appState)
                }
                is OnboardingViewModel -> {
                    viewModel.configureWithAppState(appState)
                }
                // Ajouter autres ViewModels si nécessaire
            }
        }
    }
    
    /**
     * Actualiser tous les ViewModels
     */
    suspend fun refreshAllViewModels(): Result<Unit> {
        Log.d(TAG, "🔄 Actualisation tous ViewModels")
        
        return try {
            val appState = getIntegratedAppState()
            appState.refreshUserData()
            
            // Actualiser ViewModels spécifiques
            viewModelsRegistry.values.forEach { viewModel ->
                when (viewModel) {
                    is ConnectedDailyChallengeViewModel -> {
                        viewModel.refreshData()
                    }
                    // Ajouter autres actualisations
                }
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur actualisation ViewModels", e)
            Result.Error(e)
        }
    }
    
    // === Lifecycle Management ===
    
    /**
     * Nettoyer ViewModel
     */
    fun clearViewModel(key: String) {
        viewModelsRegistry.remove(key)?.let { viewModel ->
            Log.d(TAG, "🗑️ ViewModel nettoyé: $key")
            if (viewModel is IntegratedAppState) {
                // Ne pas nettoyer l'AppState central
                viewModelsRegistry[key] = viewModel
            }
        }
        updateActiveViewModels()
    }
    
    /**
     * Nettoyer tous les ViewModels (sauf AppState)
     */
    fun clearAllViewModels() {
        Log.d(TAG, "🗑️ Nettoyage tous ViewModels")
        
        val appState = _integratedAppState.value
        viewModelsRegistry.clear()
        
        // Remettre AppState
        if (appState != null) {
            viewModelsRegistry["IntegratedAppState"] = appState
        }
        
        updateActiveViewModels()
    }
    
    /**
     * Effectuer nettoyage mémoire
     */
    fun performMemoryCleanup() {
        Log.d(TAG, "🧹 Nettoyage mémoire ViewModels")
        
        // Supprimer ViewModels inactifs (stratégie simple)
        val toRemove = mutableListOf<String>()
        
        viewModelsRegistry.forEach { (key, viewModel) ->
            if (key != "IntegratedAppState" && shouldCleanupViewModel(viewModel)) {
                toRemove.add(key)
            }
        }
        
        toRemove.forEach { key ->
            clearViewModel(key)
        }
        
        Log.d(TAG, "🧹 ${toRemove.size} ViewModels nettoyés")
    }
    
    /**
     * Déterminer si ViewModel doit être nettoyé
     */
    private fun shouldCleanupViewModel(viewModel: ViewModel): Boolean {
        // Logique simple - peut être améliorée
        return false // Pour l'instant, on garde tous les ViewModels
    }
    
    // === Health Check ===
    
    /**
     * Effectuer diagnostic ViewModels
     */
    suspend fun performViewModelDiagnostic(): Result<Map<String, Any>> {
        Log.d(TAG, "🔧 Diagnostic ViewModels complet")
        
        return try {
            val diagnostics = mutableMapOf<String, Any>()
            
            // Informations générales
            diagnostics["total_viewmodels"] = viewModelsRegistry.size
            diagnostics["active_viewmodels"] = _activeViewModels.value.size
            diagnostics["viewmodel_health"] = _viewModelHealth.value.name
            
            // État AppState
            val appState = _integratedAppState.value
            diagnostics["app_state_available"] = (appState != null)
            diagnostics["app_state_ready"] = appState?.isAppReady?.value ?: false
            
            // Détails ViewModels
            val viewModelDetails = mutableMapOf<String, Map<String, Any>>()
            viewModelsRegistry.forEach { (key, viewModel) ->
                viewModelDetails[key] = mapOf(
                    "class" to viewModel.javaClass.simpleName,
                    "status" to "active"
                )
            }
            diagnostics["viewmodel_details"] = viewModelDetails
            
            Result.Success(diagnostics)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur diagnostic ViewModels", e)
            Result.Error(e)
        }
    }
    
    // === Debug ===
    
    /**
     * État de debug du ViewModelManager
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "total_viewmodels" to viewModelsRegistry.size,
            "active_viewmodels" to _activeViewModels.value,
            "viewmodel_health" to _viewModelHealth.value.name,
            "app_state_available" to (_integratedAppState.value != null)
        )
    }
}

/**
 * Factory pour créer ViewModels avec dépendances
 */
class IntegratedViewModelFactory : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            IntegratedAppState::class.java -> {
                ServiceContainer.createAppState() as T
            }
            ConnectedDailyChallengeViewModel::class.java -> {
                ServiceContainer.createDailyChallengeViewModel() as T
            }
            OnboardingViewModel::class.java -> {
                ServiceContainer.createOnboardingViewModel() as T
            }
            FreemiumManager::class.java -> {
                ServiceContainer.createFreemiumManager(
                    ServiceContainer.createAppState()
                ) as T
            }
            WidgetsViewModel::class.java -> {
                ServiceContainer.createWidgetsViewModel() as T
            }
            else -> {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}

/**
 * États de santé ViewModels
 */
enum class ViewModelHealthStatus {
    UNKNOWN,
    HEALTHY,
    WARNING,
    CRITICAL
}
