package com.love2loveapp.core.utils.managers

import android.content.Context
import android.util.Log
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.utils.ConnectionOrchestrator
import com.love2loveapp.core.utils.Extensions
import com.love2loveapp.core.utils.DictionaryExtensions
import com.love2loveapp.core.services.firebase.FirebaseUserService
import com.love2loveapp.core.services.firebase.FirebaseFunctionsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🛠️ UtilsManager - Gestionnaire Centralisé des Utilitaires
 * 
 * Responsabilités :
 * - Coordination de tous les utilitaires (Extensions, ConnectionOrchestrator, etc.)
 * - États réactifs pour connexions et orchestrations
 * - Helpers centralisés et fonctions utilitaires
 * - Point d'entrée unique pour toute la logique utilitaire
 * 
 * Architecture : Utils Manager + Orchestration + Reactive Utilities
 */
class UtilsManager(
    private val context: Context,
    private val firebaseUserService: FirebaseUserService,
    private val firebaseFunctionsService: FirebaseFunctionsService
) {
    
    companion object {
        private const val TAG = "UtilsManager"
    }
    
    // === Coroutine Scope ===
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // === Utils Components ===
    private val connectionOrchestrator = ConnectionOrchestrator.getInstance(context).apply {
        configure(firebaseUserService, firebaseFunctionsService)
    }
    
    // === Utils States ===
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _orchestrationInProgress = MutableStateFlow(false)
    val orchestrationInProgress: StateFlow<Boolean> = _orchestrationInProgress.asStateFlow()
    
    private val _utilsHealth = MutableStateFlow<UtilsHealthStatus>(UtilsHealthStatus.UNKNOWN)
    val utilsHealth: StateFlow<UtilsHealthStatus> = _utilsHealth.asStateFlow()
    
    // === Performance Tracking ===
    private val _performanceMetrics = MutableStateFlow<Map<String, Long>>(emptyMap())
    val performanceMetrics: StateFlow<Map<String, Long>> = _performanceMetrics.asStateFlow()
    
    init {
        Log.d(TAG, "🛠️ Initialisation UtilsManager")
        initializeUtilsStreams()
    }
    
    // === Initialization ===
    
    /**
     * Initialiser les flux réactifs des utilitaires
     */
    private fun initializeUtilsStreams() {
        Log.d(TAG, "🌊 Configuration flux utilitaires réactifs")
        
        // Observer état connexion
        observeConnectionStatus()
        
        // Observer santé utilitaires
        observeUtilsHealth()
    }
    
    /**
     * Observer statut connexion
     */
    private fun observeConnectionStatus() {
        connectionOrchestrator.connectionStatus
            .onEach { status ->
                Log.d(TAG, "🔗 Connection status: ${status.name}")
                _connectionStatus.value = status
            }
            .launchIn(scope)
    }
    
    /**
     * Observer santé utilitaires
     */
    private fun observeUtilsHealth() {
        // Calculer santé basée sur connexion et performance
        combine(
            _connectionStatus,
            _performanceMetrics
        ) { connection, metrics ->
            calculateUtilsHealth(connection, metrics)
        }
        .onEach { health ->
            Log.d(TAG, "🏥 Utils health: ${health.name}")
            _utilsHealth.value = health
        }
        .launchIn(scope)
    }
    
    /**
     * Calculer santé utilitaires
     */
    private fun calculateUtilsHealth(
        connection: ConnectionStatus,
        metrics: Map<String, Long>
    ): UtilsHealthStatus {
        return when {
            connection == ConnectionStatus.ERROR -> UtilsHealthStatus.CRITICAL
            connection == ConnectionStatus.CONNECTING -> UtilsHealthStatus.WARNING
            connection == ConnectionStatus.CONNECTED && metrics.isNotEmpty() -> UtilsHealthStatus.HEALTHY
            else -> UtilsHealthStatus.UNKNOWN
        }
    }
    
    // === Connection Orchestration ===
    
    /**
     * Démarrer orchestration de connexion
     */
    suspend fun startConnectionOrchestration(partnerCode: String): Result<Unit> {
        Log.d(TAG, "🚀 Démarrage orchestration connexion: $partnerCode")
        _orchestrationInProgress.value = true
        
        return try {
            val startTime = System.currentTimeMillis()
            
            val result = connectionOrchestrator.orchestrateConnection(partnerCode)
            
            val executionTime = System.currentTimeMillis() - startTime
            updatePerformanceMetric("connection_orchestration", executionTime)
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur orchestration connexion", e)
            Result.Error(e)
        } finally {
            _orchestrationInProgress.value = false
        }
    }
    
    /**
     * Arrêter orchestration
     */
    suspend fun stopConnectionOrchestration(): Result<Unit> {
        Log.d(TAG, "⏹️ Arrêt orchestration connexion")
        
        return try {
            connectionOrchestrator.stopOrchestration()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur arrêt orchestration", e)
            Result.Error(e)
        }
    }
    
    /**
     * Redémarrer orchestration
     */
    suspend fun restartConnectionOrchestration(): Result<Unit> {
        Log.d(TAG, "🔄 Redémarrage orchestration")
        
        return try {
            stopConnectionOrchestration()
            // Attendre un peu avant de redémarrer
            kotlinx.coroutines.delay(1000)
            
            val lastPartnerCode = connectionOrchestrator.getLastPartnerCode()
            if (lastPartnerCode != null) {
                startConnectionOrchestration(lastPartnerCode)
            } else {
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur redémarrage orchestration", e)
            Result.Error(e)
        }
    }
    
    // === Extensions Utilities ===
    
    /**
     * Formater texte avec extensions
     */
    fun formatText(text: String, options: Map<String, Any> = emptyMap()): String {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = Extensions.formatText(text, options)
            val executionTime = System.currentTimeMillis() - startTime
            updatePerformanceMetric("format_text", executionTime)
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur formatage texte", e)
            text
        }
    }
    
    /**
     * Valider données avec extensions
     */
    fun validateData(data: Map<String, Any>, rules: Map<String, Any>): Result<Boolean> {
        val startTime = System.currentTimeMillis()
        
        return try {
            val isValid = Extensions.validateData(data, rules)
            val executionTime = System.currentTimeMillis() - startTime
            updatePerformanceMetric("validate_data", executionTime)
            
            Result.Success(isValid)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur validation données", e)
            Result.Error(e)
        }
    }
    
    /**
     * Transformer données avec extensions
     */
    fun transformData(data: Map<String, Any>, transformations: List<String>): Result<Map<String, Any>> {
        val startTime = System.currentTimeMillis()
        
        return try {
            val transformed = Extensions.transformData(data, transformations)
            val executionTime = System.currentTimeMillis() - startTime
            updatePerformanceMetric("transform_data", executionTime)
            
            Result.Success(transformed)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur transformation données", e)
            Result.Error(e)
        }
    }
    
    // === Dictionary Utilities ===
    
    /**
     * Fusionner dictionnaires avec extensions
     */
    fun mergeDictionaries(
        dict1: Map<String, Any>,
        dict2: Map<String, Any>,
        strategy: MergeStrategy = MergeStrategy.OVERRIDE
    ): Map<String, Any> {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = DictionaryExtensions.merge(dict1, dict2, strategy)
            val executionTime = System.currentTimeMillis() - startTime
            updatePerformanceMetric("merge_dictionaries", executionTime)
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur fusion dictionnaires", e)
            dict1
        }
    }
    
    /**
     * Filtrer dictionnaire
     */
    fun filterDictionary(
        dictionary: Map<String, Any>,
        predicate: (Map.Entry<String, Any>) -> Boolean
    ): Map<String, Any> {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = DictionaryExtensions.filter(dictionary, predicate)
            val executionTime = System.currentTimeMillis() - startTime
            updatePerformanceMetric("filter_dictionary", executionTime)
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur filtrage dictionnaire", e)
            dictionary
        }
    }
    
    // === Performance Utilities ===
    
    /**
     * Mesurer performance d'une opération
     */
    suspend fun <T> measurePerformance(
        operationName: String,
        operation: suspend () -> T
    ): Result<T> {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = operation()
            val executionTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "⚡ $operationName: ${executionTime}ms")
            updatePerformanceMetric(operationName, executionTime)
            
            Result.Success(result)
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ $operationName failed after ${executionTime}ms", e)
            updatePerformanceMetric("${operationName}_error", executionTime)
            
            Result.Error(e)
        }
    }
    
    /**
     * Mettre à jour métrique de performance
     */
    private fun updatePerformanceMetric(metricName: String, executionTime: Long) {
        val currentMetrics = _performanceMetrics.value.toMutableMap()
        currentMetrics[metricName] = executionTime
        _performanceMetrics.value = currentMetrics
    }
    
    /**
     * Obtenir métriques de performance
     */
    fun getPerformanceMetrics(): Map<String, Long> {
        return _performanceMetrics.value
    }
    
    /**
     * Réinitialiser métriques de performance
     */
    fun resetPerformanceMetrics() {
        Log.d(TAG, "🔄 Reset métriques performance")
        _performanceMetrics.value = emptyMap()
    }
    
    // === Health Check ===
    
    /**
     * Effectuer diagnostic complet des utilitaires
     */
    suspend fun performUtilsDiagnostic(): Result<Map<String, Any>> {
        Log.d(TAG, "🔧 Diagnostic utilitaires complet")
        
        return try {
            val diagnostics = mutableMapOf<String, Any>()
            
            // Test ConnectionOrchestrator
            diagnostics["connection_orchestrator"] = testConnectionOrchestrator()
            
            // Test Extensions
            diagnostics["extensions"] = testExtensions()
            
            // Test DictionaryExtensions
            diagnostics["dictionary_extensions"] = testDictionaryExtensions()
            
            // Métriques performance
            diagnostics["performance_metrics"] = _performanceMetrics.value
            
            // Santé générale
            diagnostics["utils_health"] = _utilsHealth.value.name
            
            Result.Success(diagnostics)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur diagnostic utilitaires", e)
            Result.Error(e)
        }
    }
    
    private suspend fun testConnectionOrchestrator(): Map<String, Any> {
        return try {
            mapOf(
                "status" to "healthy",
                "connection_status" to _connectionStatus.value.name,
                "orchestration_in_progress" to _orchestrationInProgress.value
            )
        } catch (e: Exception) {
            mapOf("status" to "error", "error" to e.message)
        }
    }
    
    private fun testExtensions(): Map<String, Any> {
        return try {
            val testText = "Test"
            val formatted = formatText(testText)
            
            mapOf(
                "status" to "healthy",
                "format_test" to (formatted.isNotEmpty())
            )
        } catch (e: Exception) {
            mapOf("status" to "error", "error" to e.message)
        }
    }
    
    private fun testDictionaryExtensions(): Map<String, Any> {
        return try {
            val dict1 = mapOf("a" to 1)
            val dict2 = mapOf("b" to 2)
            val merged = mergeDictionaries(dict1, dict2)
            
            mapOf(
                "status" to "healthy",
                "merge_test" to (merged.size == 2)
            )
        } catch (e: Exception) {
            mapOf("status" to "error", "error" to e.message)
        }
    }
    
    // === Getters ===
    
    /**
     * Accès au ConnectionOrchestrator
     */
    fun getConnectionOrchestrator() = connectionOrchestrator
    
    // === Debug ===
    
    /**
     * État de debug du UtilsManager
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "connectionStatus" to _connectionStatus.value.name,
            "orchestrationInProgress" to _orchestrationInProgress.value,
            "utilsHealth" to _utilsHealth.value.name,
            "performanceMetricsCount" to _performanceMetrics.value.size
        )
    }
}

/**
 * États de connexion
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * États de santé utilitaires
 */
enum class UtilsHealthStatus {
    UNKNOWN,
    HEALTHY,
    WARNING,
    CRITICAL
}

/**
 * Stratégies de fusion dictionnaires
 */
enum class MergeStrategy {
    OVERRIDE,
    MERGE,
    PRESERVE
}
