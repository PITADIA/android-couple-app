package com.love2loveapp.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.models.CoupleStatistics
import com.love2loveapp.models.StatisticCard
import com.love2loveapp.services.statistics.StatisticsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * 📊 StatisticsViewModel - ViewModel statistiques couple Android
 * 
 * Équivalent Android du système reactive CoupleStatisticsView iOS :
 * - Architecture MVVM sans Hilt (pour simplifier)
 * - StateFlow pour UI reactive
 * - Actions utilisateur avec analytics
 * - Gestion états loading/error
 */
class StatisticsViewModel(
    private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "StatisticsViewModel"
    }
    
    // Repository central des statistiques
    private val statisticsRepository = StatisticsRepository.getInstance(context)
    
    // États UI observables
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()
    
    // Données principales depuis repository
    val statistics = statisticsRepository.statistics
    val isLoading = statisticsRepository.isLoading
    val lastError = statisticsRepository.lastError
    
    // Cartes statistiques réactives 
    val statisticCards: StateFlow<List<StatisticCard>> = statistics
        .map { stats ->
            StatisticCard.createAllCardsFromStatistics(
                statistics = stats,
                onDaysTogetherClick = { onDaysTogetherClick() },
                onQuestionsProgressClick = { onQuestionsProgressClick() },
                onCitiesVisitedClick = { onCitiesVisitedClick() },
                onCountriesVisitedClick = { onCountriesVisitedClick() }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        Log.d(TAG, "📊 Initialisation StatisticsViewModel")
        initializeStatistics()
    }
    
    /**
     * 🏗️ Initialiser système statistiques
     */
    private fun initializeStatistics() {
        Log.d(TAG, "🏗️ Initialisation système statistiques")
        
        viewModelScope.launch {
            // Observer les changements d'erreur
            lastError.collect { error ->
                _uiState.update { it.copy(errorMessage = error) }
            }
        }
        
        // Marquer comme initialisé
        _uiState.update { it.copy(isInitialized = true, lastRefreshed = Date()) }
    }
    
    /**
     * 🔄 Rafraîchissement manuel des statistiques
     */
    fun refresh(forceUpdate: Boolean = false) {
        Log.d(TAG, "🔄 Rafraîchissement statistiques (force=$forceUpdate)")
        
        viewModelScope.launch {
            val result = statisticsRepository.refreshStatistics(forceUpdate)
            
            if (result.isSuccess) {
                _uiState.update { 
                    it.copy(
                        lastRefreshed = Date(),
                        errorMessage = null
                    )
                }
                Log.d(TAG, "✅ Rafraîchissement réussi")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Erreur inconnue"
                _uiState.update { it.copy(errorMessage = error) }
                Log.e(TAG, "❌ Échec rafraîchissement: $error")
            }
        }
    }
    
    // ==============================================
    // 🎯 ACTIONS UTILISATEUR AVEC ANALYTICS
    // ==============================================
    
    /**
     * 💕 Clic sur statistique "Jours Ensemble"
     */
    fun onDaysTogetherClick() {
        Log.d(TAG, "🎯 Action utilisateur: Clic Jours Ensemble")
        
        // 📈 Analytics
        logStatisticInteraction("days_together", statistics.value.daysTogether)
        
        // 🔄 Repository action
        statisticsRepository.onDaysTogetherClicked()
        
        // 📱 UI action (navigation future)
        _uiState.update { 
            it.copy(lastInteraction = StatisticInteraction.DaysTogether)
        }
        
        // TODO: Navigation vers détail relation/calendrier
    }
    
    /**
     * 🧠 Clic sur statistique "Questions Répondues"
     */
    fun onQuestionsProgressClick() {
        Log.d(TAG, "🎯 Action utilisateur: Clic Progression Questions")
        
        // 📈 Analytics
        logStatisticInteraction("questions_progress", statistics.value.questionsProgressPercentage.toInt())
        
        // 🔄 Repository action
        statisticsRepository.onQuestionsProgressClicked()
        
        // 📱 UI action
        _uiState.update { 
            it.copy(lastInteraction = StatisticInteraction.QuestionsProgress)
        }
        
        // TODO: Navigation vers vue progression détaillée
    }
    
    /**
     * 🏙️ Clic sur statistique "Villes Visitées"
     */
    fun onCitiesVisitedClick() {
        Log.d(TAG, "🎯 Action utilisateur: Clic Villes Visitées")
        
        // 📈 Analytics
        logStatisticInteraction("cities_visited", statistics.value.citiesVisited)
        
        // 🔄 Repository action  
        statisticsRepository.onCitiesVisitedClicked()
        
        // 📱 UI action
        _uiState.update { 
            it.copy(lastInteraction = StatisticInteraction.CitiesVisited)
        }
        
        // TODO: Navigation vers JournalMapScreen avec filtre villes
    }
    
    /**
     * 🌍 Clic sur statistique "Pays Visités"
     */
    fun onCountriesVisitedClick() {
        Log.d(TAG, "🎯 Action utilisateur: Clic Pays Visités")
        
        // 📈 Analytics
        logStatisticInteraction("countries_visited", statistics.value.countriesVisited)
        
        // 🔄 Repository action
        statisticsRepository.onCountriesVisitedClicked()
        
        // 📱 UI action
        _uiState.update { 
            it.copy(lastInteraction = StatisticInteraction.CountriesVisited)
        }
        
        // TODO: Navigation vers JournalMapScreen avec filtre pays
    }
    
    /**
     * ❌ Dismisser message d'erreur
     */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    // ==============================================
    // 🛠️ HELPER METHODS PRIVÉS
    // ==============================================
    
    /**
     * 📈 Log interaction statistique pour analytics
     */
    private fun logStatisticInteraction(type: String, value: Any) {
        // TODO: Intégrer Firebase Analytics
        Log.d(TAG, "📈 Analytics: statistic_clicked(type=$type, value=$value)")
        
        /*
        analytics.logEvent("statistic_clicked") {
            param("statistic_type", type)
            param("statistic_value", value.toString())
            param("screen_name", "main_statistics")
            param("timestamp", System.currentTimeMillis())
        }
        */
    }
    
    /**
     * 📊 Obtenir statistiques formatées pour debug
     */
    fun getDebugInfo(): Map<String, Any> {
        return statisticsRepository.getDebugInfo() + mapOf(
            "viewmodel_initialized" to _uiState.value.isInitialized,
            "ui_last_refresh" to (_uiState.value.lastRefreshed?.time ?: 0L),
            "ui_last_interaction" to (_uiState.value.lastInteraction?.name ?: "none"),
            "cards_count" to statisticCards.value.size
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 StatisticsViewModel onCleared")
        statisticsRepository.cleanup()
    }
}

/**
 * 📱 État UI du ViewModel statistiques
 */
data class StatisticsUiState(
    val isInitialized: Boolean = false,
    val lastRefreshed: Date? = null,
    val errorMessage: String? = null,
    val lastInteraction: StatisticInteraction? = null
) {
    val hasError: Boolean get() = errorMessage != null
    val isRefreshing: Boolean get() = lastRefreshed != null
}

/**
 * 🎯 Types d'interactions utilisateur sur statistiques
 */
enum class StatisticInteraction {
    DaysTogether,
    QuestionsProgress, 
    CitiesVisited,
    CountriesVisited
}
