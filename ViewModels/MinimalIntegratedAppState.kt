package com.love2loveapp.core.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.data.persistence.AppPreferences
import com.love2loveapp.navigation.Route
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MinimalIntegratedAppState - Version allégée d'IntegratedAppState
 * 
 * Responsabilités:
 * - SEULEMENT les états globaux nécessaires à la navigation
 * - Persistance avec DataStore pour restauration après process death
 * - SavedStateHandle pour paramètres de routes
 * 
 * Les états métier (questions, journal, etc.) restent dans leurs ViewModels dédiés
 */
class MinimalIntegratedAppState @Inject constructor(
    private val appPreferences: AppPreferences,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    companion object {
        private const val KEY_CURRENT_DAY = "current_day"
        private const val KEY_SELECTED_CATEGORY = "selected_category"
    }
    
    // === États Globaux Critiques (persistés) ===
    
    /**
     * État d'authentification - persisté pour restauration
     */
    val isAuthenticated: StateFlow<Boolean> = appPreferences.isAuthenticated
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    /**
     * État partenaire - persisté pour restauration
     */
    val hasPartner: StateFlow<Boolean> = appPreferences.hasPartner
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    /**
     * État onboarding - persisté pour restauration
     */
    val onboardingCompleted: StateFlow<Boolean> = appPreferences.onboardingCompleted
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    /**
     * Route calculée automatiquement basée sur les états persistés
     * Recalculée au cold start depuis DataStore
     */
    val currentRoute: StateFlow<Route> = combine(
        isAuthenticated,
        hasPartner,
        onboardingCompleted
    ) { isAuth, hasPartner, onboardingDone ->
        calculateRoute(isAuth, hasPartner, onboardingDone)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Route.Splash
    )
    
    // === Paramètres de Routes (SavedStateHandle) ===
    
    /**
     * Jour actuel pour daily content - survit au process death
     */
    var currentDay: Int
        get() = savedStateHandle.get<Int>(KEY_CURRENT_DAY) ?: 1
        set(value) = savedStateHandle.set(KEY_CURRENT_DAY, value)
    
    /**
     * Catégorie sélectionnée - survit au process death
     */
    var selectedCategory: String?
        get() = savedStateHandle.get<String>(KEY_SELECTED_CATEGORY)
        set(value) = savedStateHandle.set(KEY_SELECTED_CATEGORY, value)
    
    // === Actions Publiques ===
    
    /**
     * Met à jour l'état d'authentification
     */
    fun setAuthenticated(isAuthenticated: Boolean) {
        viewModelScope.launch {
            appPreferences.setAuthenticated(isAuthenticated)
        }
    }
    
    /**
     * Met à jour l'état partenaire
     */
    fun setHasPartner(hasPartner: Boolean, partnerId: String? = null) {
        viewModelScope.launch {
            appPreferences.setHasPartner(hasPartner)
            appPreferences.setPartnerId(partnerId)
        }
    }
    
    /**
     * Marque l'onboarding comme terminé
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            appPreferences.setOnboardingCompleted(true)
        }
    }
    
    /**
     * Déconnexion complète - efface toutes les données
     */
    fun logout() {
        viewModelScope.launch {
            appPreferences.clearUserData()
        }
    }
    
    /**
     * Calcule la route appropriée basée sur l'état persisté
     */
    private fun calculateRoute(
        isAuth: Boolean,
        hasPartner: Boolean,
        onboardingDone: Boolean
    ): Route {
        return when {
            !isAuth -> Route.Onboarding
            !onboardingDone -> Route.Onboarding
            !hasPartner -> Route.PartnerConnection
            else -> Route.Main
        }
    }
}
