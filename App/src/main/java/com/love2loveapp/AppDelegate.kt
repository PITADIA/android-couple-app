package com.love2loveapp

import android.app.Application
import android.util.Log
import com.love2loveapp.models.AppState
import com.love2loveapp.services.*

/**
 * AppDelegate simplifié pour Love2Love
 * 
 * Version initiale sans Firebase pour éviter les erreurs de dépendances
 */
class AppDelegate : Application() {
    
    companion object {
        private const val TAG = "AppDelegate"
        
        // Instance globale de l'état de l'app
        lateinit var appState: AppState
            private set
    }
    
    // Services principaux
    private lateinit var locationService: LocationService
    private lateinit var freemiumManager: SimpleFreemiumManager
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "🚀 Love2Love: Initialisation de l'application")
        
        // Initialiser l'état global
        initializeAppState()
        
        // Initialiser les services
        initializeServices()
        
        // Configuration initiale
        performInitialSetup()
        
        Log.i(TAG, "✅ Love2Love: Application initialisée avec succès")
    }
    
    /**
     * Initialiser l'état global de l'application
     */
    private fun initializeAppState() {
        Log.d(TAG, "🔧 Initialisation AppState")
        
        appState = AppState()
        
        // Configuration initiale basée sur l'état utilisateur
        val prefs = getSharedPreferences("love2love_prefs", MODE_PRIVATE)
        val isOnboardingCompleted = prefs.getBoolean("onboarding_completed", false)
        val hasUser = prefs.contains("user_name") // Vérifier si un utilisateur existe
        
        when {
            !isOnboardingCompleted || !hasUser -> {
                Log.d(TAG, "👋 Onboarding requis - Démarrage Launch Screen")
                appState.navigateToScreen(com.love2loveapp.models.AppScreen.Launch)
            }
            else -> {
                Log.d(TAG, "🔄 Utilisateur configuré - Interface principale")
                // Restaurer l'utilisateur depuis les préférences
                val userName = prefs.getString("user_name", "") ?: ""
                val user = com.love2loveapp.models.User(
                    id = prefs.getString("user_id", "user_${System.currentTimeMillis()}") ?: "user_${System.currentTimeMillis()}",
                    name = userName,
                    relationshipGoals = prefs.getStringSet("user_goals", emptySet())?.toList() ?: emptyList(),
                    relationshipDuration = prefs.getString("user_duration", null),
                    relationshipImprovement = prefs.getString("user_improvement", null),
                    questionMode = prefs.getString("user_question_mode", null),
                    onboardingInProgress = false
                )
                appState.setAuthenticated(true, user)
                appState.navigateToScreen(com.love2loveapp.models.AppScreen.Main)
            }
        }
    }
    
    /**
     * Initialiser tous les services
     */
    private fun initializeServices() {
        Log.d(TAG, "🔧 Initialisation des services")
        
        // Service de localisation
        locationService = LocationServiceImpl(this)
        
        // Manager freemium
        freemiumManager = SimpleFreemiumManager()
        appState.setFreemiumManager(freemiumManager)
        
        // Initialiser les singletons
        QuestionCacheManager.shared
        PerformanceMonitor.shared
        PackProgressService.shared
        QuestionDataManager.shared
        
        Log.d(TAG, "✅ Services initialisés")
    }
    
    /**
     * Configuration initiale de l'application
     */
    private fun performInitialSetup() {
        Log.d(TAG, "⚙️ Configuration initiale")
        
        // Démarrer le monitoring de performance
        PerformanceMonitor.shared.startMonitoring()
        
        // Préchargement en arrière-plan
        Thread {
            try {
                Thread.sleep(2000) // Attendre que l'UI soit chargée
                QuestionCacheManager.shared.preloadAllCategories()
                QuestionDataManager.shared.preloadEssentialCategories()
                Log.d(TAG, "✅ Préchargement terminé")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur préchargement", e)
            }
        }.start()
    }
    
    
    /**
     * Méthode pour vérifier si l'app est prête
     */
    fun isAppReady(): Boolean {
        return try {
            appState
            true
        } catch (e: UninitializedPropertyAccessException) {
            false
        }
    }
    
    /**
     * Sauvegarder l'utilisateur et marquer l'onboarding comme terminé
     */
    fun saveUserAndCompleteOnboarding(user: com.love2loveapp.models.User) {
        val prefs = getSharedPreferences("love2love_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("onboarding_completed", true)
            putString("user_id", user.id)
            putString("user_name", user.name)
            putStringSet("user_goals", user.relationshipGoals.toSet())
            putString("user_duration", user.relationshipDuration)
            putString("user_improvement", user.relationshipImprovement)
            putString("user_question_mode", user.questionMode)
            apply()
        }
        Log.d(TAG, "✅ Utilisateur sauvegardé et onboarding marqué comme terminé")
    }
    
    /**
     * Réinitialiser l'app (pour les tests)
     */
    fun resetApp() {
        val prefs = getSharedPreferences("love2love_prefs", MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "🔄 App réinitialisée - prochain lancement montrera l'onboarding")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG, "👋 Love2Love: Application terminée")
        
        // Nettoyer les services
        if (::locationService.isInitialized) {
            locationService.stopLocationUpdates()
        }
        
        PerformanceMonitor.shared.stopMonitoring()
    }
}
