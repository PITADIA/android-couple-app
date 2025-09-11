package com.love2loveapp.core.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.model.AppConstants
import com.love2loveapp.model.RelationshipStats
import com.love2loveapp.model.DistanceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel pour la gestion des widgets
 * Équivalent Kotlin du WidgetService Swift
 */
class WidgetsViewModel : ViewModel() {
    
    // === State Management ===
    private val _relationshipStats = MutableStateFlow<RelationshipStats?>(null)
    val relationshipStats: StateFlow<RelationshipStats?> = _relationshipStats.asStateFlow()
    
    private val _distanceInfo = MutableStateFlow<DistanceInfo?>(null)
    val distanceInfo: StateFlow<DistanceInfo?> = _distanceInfo.asStateFlow()
    
    private val _hasSubscription = MutableStateFlow(false)
    val hasSubscription: StateFlow<Boolean> = _hasSubscription.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // === Services ===
    private var widgetService: WidgetService? = null
    private var analyticsService: AnalyticsService? = null
    
    /**
     * Configure les services avec AppState
     */
    fun configureServices(appState: AppState) {
        widgetService = appState.widgetService
        analyticsService = appState.analyticsService
        
        // Observer les changements d'abonnement
        observeSubscriptionChanges(appState)
    }
    
    /**
     * Rafraîchit les données des widgets
     */
    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // Rafraîchir les données via le service
                widgetService?.refreshData()
                
                // Mettre à jour les états
                _relationshipStats.value = widgetService?.getRelationshipStats()
                _distanceInfo.value = widgetService?.getDistanceInfo()
                
            } catch (e: Exception) {
                // Log error
                println("Erreur lors du rafraîchissement des widgets: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Force le rafraîchissement des images de profil
     */
    fun forceRefreshProfileImages() {
        viewModelScope.launch {
            try {
                widgetService?.forceRefreshProfileImages()
                // Rafraîchir après la mise à jour des images
                refreshData()
            } catch (e: Exception) {
                println("Erreur lors du rafraîchissement des images: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * Track un événement analytics
     */
    fun trackAnalyticsEvent(eventName: String, parameters: Map<String, String>) {
        analyticsService?.track(eventName, parameters)
        println("📊 Événement Firebase: $eventName - ${parameters.entries.joinToString(", ") { "${it.key}: ${it.value}" }}")
    }
    
    /**
     * Observer les changements d'abonnement
     */
    private fun observeSubscriptionChanges(appState: AppState) {
        viewModelScope.launch {
            appState.currentUser.collect { user ->
                _hasSubscription.value = user?.isSubscribed ?: false
            }
        }
    }
}

/**
 * Interface pour WidgetService (à implémenter)
 */
interface WidgetService {
    suspend fun refreshData()
    suspend fun forceRefreshProfileImages()
    fun getRelationshipStats(): RelationshipStats?
    fun getDistanceInfo(): DistanceInfo?
}
