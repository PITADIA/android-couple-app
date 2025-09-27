package com.love2loveapp.services.statistics

import android.content.Context
import android.util.Log
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.CoupleStatistics
import com.love2loveapp.models.QuestionCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 📊 StatisticsRepository - Service central statistiques couple Android
 * 
 * Équivalent Android du système CoupleStatisticsView iOS :
 * - Combine sources données multiples (User, CategoryProgress, Journal)
 * - Calculs réactifs temps réel via StateFlow/Flow.combine  
 * - Cache intelligent avec rafraîchissement automatique
 * - Architecture MVVM avec observers optimisés
 */
class StatisticsRepository private constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "StatisticsRepository"
        private const val UPDATE_DEBOUNCE_MS = 1000L // Éviter recalculs trop fréquents
        
        @Volatile
        private var INSTANCE: StatisticsRepository? = null
        
        /**
         * 🏗️ Singleton getInstance pattern
         */
        fun getInstance(context: Context): StatisticsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StatisticsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Coroutine scope pour opérations repository
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // StateFlow principal des statistiques
    private val _statistics = MutableStateFlow(CoupleStatistics.empty())
    val statistics: StateFlow<CoupleStatistics> = _statistics.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    // Services et dépendances (simplifiés pour éviter erreurs)
    private val appState = AppDelegate.appState
    private val journalRepository = AppDelegate.journalRepository
    
    // Observeurs actifs
    private var statisticsObserverJob: Job? = null
    
    init {
        Log.d(TAG, "📊 Initialisation StatisticsRepository")
        startReactiveStatistics()
    }
    
    /**
     * 🔄 Démarrer calculs statistiques réactifs
     * Équivalent iOS des computed properties + @Published observers
     */
    private fun startReactiveStatistics() {
        Log.d(TAG, "🔄 Démarrage calculs statistiques réactifs (version simplifiée)")
        
        statisticsObserverJob = repositoryScope.launch {
            try {
                // Version simplifiée sans services manquants
                // TODO: Réactiver quand tous les services seront disponibles
                val mockStatistics = CoupleStatistics.calculate(
                    relationshipStartDate = null,
                    categoryProgress = emptyMap(),
                    journalEntries = emptyList(),
                    questionCategories = QuestionCategory.categories
                )
                
                _statistics.value = mockStatistics
                _lastError.value = null
                
                Log.d(TAG, "✅ Statistiques mockées initialisées")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur calculs statistiques: ${e.message}", e)
                _lastError.value = "Erreur calcul statistiques: ${e.message}"
            }
        }
    }
    
    /**
     * 🔄 Rafraîchissement manuel des statistiques
     * Force la mise à jour de toutes les sources
     */
    suspend fun refreshStatistics(forceUpdate: Boolean = false): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Rafraîchissement manuel statistiques (force=$forceUpdate)")
                _isLoading.value = true
                _lastError.value = null
                
                // Vérifier si mise à jour nécessaire
                val currentStats = _statistics.value
                if (!forceUpdate && !currentStats.needsUpdate()) {
                    Log.d(TAG, "✅ Statistiques encore fraîches, pas de mise à jour")
                    _isLoading.value = false
                    return@withContext Result.success(Unit)
                }
                
                // 🔄 FORCER RAFRAÎCHISSEMENT DES SOURCES
                
                // 1. Rafraîchir données utilisateur si possible
                // TODO: Ajouter refresh user si disponible
                
                // Version simplifiée - pas de sources de données réelles pour l'instant
                Log.d(TAG, "📝 Rafraîchissement simplifié (pas de vraies sources)")
                
                // Recalculer avec données mock
                val mockStatistics = CoupleStatistics.calculate(
                    relationshipStartDate = null,
                    categoryProgress = emptyMap(),
                    journalEntries = emptyList(),
                    questionCategories = QuestionCategory.categories
                )
                
                _statistics.value = mockStatistics
                
                _isLoading.value = false
                Log.d(TAG, "✅ Rafraîchissement statistiques terminé")
                
                Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur rafraîchissement statistiques: ${e.message}", e)
                _isLoading.value = false
                _lastError.value = "Erreur rafraîchissement: ${e.message}"
                Result.failure(e)
            }
        }
    }
    
    /**
     * 📊 Obtenir statistiques actuelles (synchrone)
     * Pour usage immediate UI sans observer
     */
    fun getCurrentStatistics(): CoupleStatistics {
        return _statistics.value
    }
    
    /**
     * 📈 Statistiques détaillées formatées pour UI
     */
    fun getFormattedStatistics(): FormattedStatistics {
        val stats = _statistics.value
        return FormattedStatistics(
            daysTogetherFormatted = stats.formattedDaysTogether,
            questionsProgressFormatted = stats.formattedQuestionsProgress,
            citiesVisitedFormatted = "${stats.citiesVisited}",
            countriesVisitedFormatted = "${stats.countriesVisited}",
            motivationalMessage = stats.getMotivationalMessage(),
            lastUpdatedFormatted = formatLastUpdated(stats.lastUpdated)
        )
    }
    
    /**
     * 🎯 Actions utilisateur sur statistiques (analytics + navigation)
     */
    fun onDaysTogetherClicked() {
        Log.d(TAG, "🎯 Clic statistique: Jours ensemble")
        // TODO: Analytics event
        // TODO: Navigation vers détail relation
    }
    
    fun onQuestionsProgressClicked() {
        Log.d(TAG, "🎯 Clic statistique: Progression questions")
        // TODO: Analytics event
        // TODO: Navigation vers vue progression détaillée
    }
    
    fun onCitiesVisitedClicked() {
        Log.d(TAG, "🎯 Clic statistique: Villes visitées")
        // TODO: Analytics event  
        // TODO: Navigation vers carte journal avec filtre villes
    }
    
    fun onCountriesVisitedClicked() {
        Log.d(TAG, "🎯 Clic statistique: Pays visités")
        // TODO: Analytics event
        // TODO: Navigation vers carte journal avec filtre pays
    }
    
    /**
     * 📊 Métriques debug pour développement
     */
    fun getDebugInfo(): Map<String, Any> {
        val stats = _statistics.value
        return mapOf(
            "statistics_initialized" to (statisticsObserverJob != null),
            "has_relationship_date" to (stats.relationshipStartDate != null),
            "total_journal_entries" to (journalRepository?.entries?.value?.size ?: 0),
            "total_categories_progress" to 0, // TODO: Réactiver quand service disponible
            "statistics_age_minutes" to ((System.currentTimeMillis() - stats.lastUpdated.time) / (60 * 1000)),
            "is_loading" to _isLoading.value,
            "has_error" to (_lastError.value != null)
        )
    }
    
    /**
     * 🧹 Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleanup StatisticsRepository")
        statisticsObserverJob?.cancel()
        repositoryScope.cancel()
    }
    
    // ==============================================
    // 🛠️ HELPER METHODS PRIVÉS
    // ==============================================
    
    private fun formatLastUpdated(date: java.util.Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time
        val minutes = diff / (60 * 1000)
        
        return when {
            minutes < 1 -> "À l'instant"
            minutes < 60 -> "Il y a ${minutes}min"
            minutes < 1440 -> "Il y a ${minutes / 60}h"
            else -> "Il y a ${minutes / 1440}j"
        }
    }
}

/**
 * 📊 Statistiques formatées pour UI
 */
data class FormattedStatistics(
    val daysTogetherFormatted: String,
    val questionsProgressFormatted: String,
    val citiesVisitedFormatted: String,
    val countriesVisitedFormatted: String,
    val motivationalMessage: String,
    val lastUpdatedFormatted: String
)
