package com.love2loveapp.core.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.viewmodels.AppState
import com.love2loveapp.model.AppConstants
import com.love2loveapp.model.DailyChallenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel pour la gestion des défis du jour
 * Équivalent Kotlin du DailyChallengeService Swift
 */
class DailyChallengeViewModel : ViewModel() {
    
    // === State Management ===
    private val _currentChallenge = MutableStateFlow<Result<DailyChallenge?>>(Result.loading())
    val currentChallenge: StateFlow<Result<DailyChallenge?>> = _currentChallenge.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // === Services ===
    private var dailyChallengeService: DailyChallengeService? = null
    private var savedChallengesService: SavedChallengesService? = null
    private var analyticsService: AnalyticsService? = null
    
    /**
     * Configure les services avec AppState
     */
    fun configureServices(appState: AppState) {
        dailyChallengeService = appState.dailyChallengeService
        savedChallengesService = appState.savedChallengesService
        analyticsService = appState.analyticsService
        
        // Observer les changements du service
        observeChallengeUpdates()
    }
    
    /**
     * Rafraîchit les défis depuis le serveur
     */
    fun refreshChallenges() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val result = dailyChallengeService?.refreshChallenges()
                if (result == true) {
                    val challenge = dailyChallengeService?.getCurrentChallenge()
                    _currentChallenge.value = Result.success(challenge)
                } else {
                    _currentChallenge.value = Result.error(AppException.Generic("Failed to refresh challenges"))
                }
            } catch (e: Exception) {
                _currentChallenge.value = Result.error(AppException.fromThrowable(e))
                _errorMessage.value = e.localizedMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Marque un défi comme terminé
     */
    fun markChallengeAsCompleted(challenge: DailyChallenge) {
        viewModelScope.launch {
            try {
                dailyChallengeService?.markChallengeAsCompleted(challenge)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage
            }
        }
    }
    
    /**
     * Sauvegarde un défi
     */
    fun saveChallenge(challenge: DailyChallenge) {
        viewModelScope.launch {
            try {
                savedChallengesService?.saveChallenge(challenge)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage
            }
        }
    }
    
    /**
     * Vérifie si un défi est déjà sauvegardé
     */
    fun isChallengeAlreadySaved(challenge: DailyChallenge): Boolean {
        return savedChallengesService?.isChallengeAlreadySaved(challenge) ?: false
    }
    
    /**
     * Track un événement analytics
     */
    fun trackAnalyticsEvent(eventName: String, parameters: Map<String, String>) {
        analyticsService?.track(eventName, parameters)
    }
    
    /**
     * Observer les mises à jour du service
     */
    private fun observeChallengeUpdates() {
        // TODO: Implémenter l'observation des changements du service
        // En attendant, on peut utiliser des callbacks ou des Flow
    }
}

/**
 * Interface pour DailyChallengeService (à implémenter)
 */
interface DailyChallengeService {
    suspend fun refreshChallenges(): Boolean
    fun getCurrentChallenge(): DailyChallenge?
    suspend fun markChallengeAsCompleted(challenge: DailyChallenge)
}

/**
 * Interface pour SavedChallengesService (à implémenter)
 */
interface SavedChallengesService {
    suspend fun saveChallenge(challenge: DailyChallenge)
    fun isChallengeAlreadySaved(challenge: DailyChallenge): Boolean
}

/**
 * Interface pour AnalyticsService (à implémenter)
 */
interface AnalyticsService {
    fun track(eventName: String, parameters: Map<String, String>)
}
