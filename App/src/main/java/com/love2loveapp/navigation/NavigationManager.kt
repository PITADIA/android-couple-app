package com.love2loveapp.navigation

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.love2loveapp.models.QuestionCategory

/**
 * Gestionnaire de navigation pour Love2Love
 */
class NavigationManager {
    
    companion object {
        val instance = NavigationManager()
    }
    
    // État de navigation actuel
    var currentDestination by mutableStateOf<NavigationDestination>(NavigationDestination.Main)
        private set
    
    var selectedCategory by mutableStateOf<QuestionCategory?>(null)
        private set
    
    // Pile de navigation pour gérer le retour
    private val navigationStack = mutableListOf<NavigationDestination>()
    
    /**
     * Naviguer vers une destination
     */
    fun navigateTo(destination: NavigationDestination) {
        Log.d("NavigationManager", "🧭 Navigation vers: ${destination::class.simpleName}")
        
        // Ajouter la destination actuelle à la pile
        if (currentDestination != destination) {
            navigationStack.add(currentDestination)
        }
        
        currentDestination = destination
    }
    
    /**
     * Naviguer vers une catégorie de questions
     */
    fun navigateToCategory(category: QuestionCategory) {
        Log.d("NavigationManager", "📝 Navigation vers catégorie: ${category.title}")
        
        selectedCategory = category
        navigateTo(NavigationDestination.QuestionList)
    }
    
    /**
     * Naviguer vers une question spécifique
     */
    fun navigateToQuestion(questionId: String) {
        Log.d("NavigationManager", "❓ Navigation vers question: $questionId")
        
        navigateTo(NavigationDestination.QuestionDetail(questionId))
    }
    
    /**
     * Retour en arrière
     */
    fun goBack(): Boolean {
        if (navigationStack.isNotEmpty()) {
            val previousDestination = navigationStack.removeLastOrNull()
            if (previousDestination != null) {
                Log.d("NavigationManager", "⬅️ Retour vers: ${previousDestination::class.simpleName}")
                currentDestination = previousDestination
                return true
            }
        }
        return false
    }
    
    /**
     * Retour à l'accueil
     */
    fun goToMain() {
        Log.d("NavigationManager", "🏠 Retour à l'accueil")
        navigationStack.clear()
        selectedCategory = null
        currentDestination = NavigationDestination.Main
    }
    
    /**
     * Vérifier si on peut revenir en arrière
     */
    fun canGoBack(): Boolean = navigationStack.isNotEmpty()
}

/**
 * Destinations de navigation possibles
 */
sealed class NavigationDestination {
    data object Main : NavigationDestination()
    data object QuestionList : NavigationDestination()
    data class QuestionDetail(val questionId: String) : NavigationDestination()
    data object Profile : NavigationDestination()
    data object Settings : NavigationDestination()
    data object Favorites : NavigationDestination()
    data object Journal : NavigationDestination()
    data object PartnerManagement : NavigationDestination()
}
