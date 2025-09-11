package com.love2loveapp.models

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État global de l'application Love2Love
 */
class AppState {
    // === États d'authentification ===
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    // === États d'onboarding ===
    private val _isOnboardingInProgress = MutableStateFlow(false)
    val isOnboardingInProgress: StateFlow<Boolean> = _isOnboardingInProgress.asStateFlow()
    
    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()
    
    // === États de navigation ===
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Launch)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()
    
    // === Services ===
    private val _freemiumManager = MutableStateFlow<FreemiumManager?>(null)
    val freemiumManager: StateFlow<FreemiumManager?> = _freemiumManager.asStateFlow()
    
    // === Méthodes publiques ===
    
    fun setAuthenticated(isAuthenticated: Boolean, user: User? = null) {
        _isAuthenticated.value = isAuthenticated
        _currentUser.value = user
        
        // Si un utilisateur est défini, sauvegarder
        if (isAuthenticated && user != null) {
            saveUserData(user)
        }
    }
    
    private fun saveUserData(user: User) {
        // Cette méthode sera appelée depuis AppDelegate
        // Pour l'instant, on log juste
        android.util.Log.d("AppState", "Utilisateur à sauvegarder: ${user.name}")
    }
    
    fun startOnboarding() {
        _isOnboardingInProgress.value = true
        _currentScreen.value = AppScreen.Onboarding
    }
    
    fun completeOnboarding() {
        _isOnboardingInProgress.value = false
        _isOnboardingCompleted.value = true
        _currentScreen.value = AppScreen.Main
    }
    
    fun completeOnboardingWithData(goals: List<String>, improvements: List<String>) {
        // Créer un utilisateur avec les données collectées
        val user = User(
            id = "user_${System.currentTimeMillis()}",
            name = "Utilisateur Love2Love", // Nom par défaut
            relationshipGoals = goals,
            relationshipImprovement = improvements.joinToString(", "),
            onboardingInProgress = false
        )
        
        // Définir l'utilisateur et terminer l'onboarding
        setAuthenticated(true, user)
        completeOnboarding()
    }
    
    fun navigateToScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }
    
    fun setFreemiumManager(manager: FreemiumManager) {
        _freemiumManager.value = manager
    }
}

/**
 * Écrans de l'application
 */
enum class AppScreen {
    Launch,
    Onboarding,
    Main,
    Authentication
}

/**
 * Modèle utilisateur simplifié
 */
data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val partnerId: String? = null,
    val isSubscribed: Boolean = false,
    val relationshipGoals: List<String> = emptyList(),
    val relationshipDuration: String? = null,
    val relationshipImprovement: String? = null,
    val questionMode: String? = null,
    val onboardingInProgress: Boolean = false
)

/**
 * Manager freemium simplifié
 */
interface FreemiumManager {
    val showingSubscription: Boolean
    val showingSubscriptionFlow: kotlinx.coroutines.flow.Flow<Boolean>
    fun dismissSubscription()
}

/**
 * Catégorie de questions
 */
data class QuestionCategory(
    val id: String,
    val title: String,
    val emoji: String,
    val description: String,
    val isPremium: Boolean = false
) {
    companion object {
        val categories = listOf(
            QuestionCategory("daily", "Questions du jour", "💬", "Découvrez-vous mutuellement"),
            QuestionCategory("challenges", "Défis quotidiens", "🎯", "Renforcez votre complicité"),
            QuestionCategory("intimacy", "Intimité", "💕", "Approfondissez votre connexion", true),
            QuestionCategory("dreams", "Rêves et projets", "🌟", "Construisez votre futur ensemble", true),
            QuestionCategory("communication", "Communication", "🗣️", "Améliorez vos échanges"),
            QuestionCategory("fun", "Moments fun", "😄", "Amusez-vous ensemble")
        )
    }
}
