package com.love2loveapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.core.viewmodels.ConnectedDailyChallengeViewModel
import com.love2loveapp.di.ServiceContainer
import com.love2loveapp.ui.IntegratedContentScreen
import com.love2loveapp.ui.theme.CoupleAppTheme

/**
 * 🎯 IntegratedMainActivity - Point d'Entrée UI Intégré
 * 
 * Responsabilités :
 * - Initialisation de l'UI avec AppState central
 * - Injection de dépendances via ServiceContainer
 * - Gestion des deep links coordonnée
 * - Lifecycle management intégré
 * 
 * Architecture : Activity + Compose + MVVM + DI
 */
class IntegratedMainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "IntegratedMainActivity"
    }
    
    // AppState central - Injecté via ServiceContainer
    private lateinit var integratedAppState: IntegratedAppState
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🚀 Initialisation IntegratedMainActivity")
        
        // 1. Vérifier que l'app est prête
        if (!IntegratedAppDelegate.instance.isAppReady()) {
            Log.e(TAG, "💥 Application pas prête - ServiceContainer non initialisé")
            finish()
            return
        }
        
        // 2. Obtenir AppState central via ServiceContainer
        integratedAppState = ServiceContainer.createAppState()
        
        // 3. Traiter deep links
        handleDeepLinkIntent(intent)
        
        // 4. Configuration UI avec Compose
        setContent {
            CoupleAppTheme {
                // ViewModels avec injection de dépendances
                val dailyChallengeViewModel: ConnectedDailyChallengeViewModel = viewModel {
                    ServiceContainer.createDailyChallengeViewModel()
                }
                
                // États observables depuis AppState central
                val isAppReady by integratedAppState.isAppReady.collectAsState()
                val isAuthenticated by integratedAppState.isAuthenticated.collectAsState()
                val currentUser by integratedAppState.currentUserResult.collectAsState()
                
                // Configuration des ViewModels avec AppState
                LaunchedEffect(integratedAppState) {
                    Log.d(TAG, "🔗 Configuration ViewModels avec IntegratedAppState")
                    dailyChallengeViewModel.configureWithAppState(integratedAppState)
                }
                
                // Démarrage du flow applicatif
                LaunchedEffect(Unit) {
                    Log.d(TAG, "🎯 Démarrage flow applicatif")
                    integratedAppState.startApplicationFlow()
                }
                
                // Composition Local Provider pour injection dans l'arbre Compose
                CompositionLocalProvider(
                    LocalIntegratedAppState provides integratedAppState,
                    LocalServiceContainer provides ServiceContainer
                ) {
                    IntegratedContentScreen(
                        integratedAppState = integratedAppState,
                        dailyChallengeViewModel = dailyChallengeViewModel,
                        onShowSubscription = {
                            // TODO: Navigation vers écran abonnement
                            Log.d(TAG, "📱 Navigation vers abonnement")
                        },
                        onDeepLink = { deepLink ->
                            handleDeepLink(deepLink)
                        }
                    )
                }
            }
        }
        
        Log.d(TAG, "✅ IntegratedMainActivity initialisée")
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "🔗 Nouveau intent reçu")
        handleDeepLinkIntent(intent)
    }
    
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "▶️ Activity started")
        
        // Clear badges quand l'app devient visible
        clearAppBadges()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "⏯️ Activity resumed")
        
        // Rafraîchir données si nécessaire
        if (::integratedAppState.isInitialized) {
            integratedAppState.loadCurrentUser()
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "⏸️ Activity paused")
    }
    
    override fun onStop() {
        super.onStop()
        Log.d(TAG, "⏹️ Activity stopped")
    }
    
    // === Deep Links Handling ===
    
    /**
     * Traiter les intents de deep links
     */
    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent?.data != null) {
            val deepLinkUri = intent.data.toString()
            Log.d(TAG, "🔗 Deep link reçu: $deepLinkUri")
            handleDeepLink(deepLinkUri)
        }
        
        // Traiter les extras FCM
        intent?.extras?.let { extras ->
            for (key in extras.keySet()) {
                val value = extras.get(key)
                Log.d(TAG, "📦 Intent extra: $key = $value")
            }
        }
    }
    
    /**
     * Router les deep links vers les bonnes actions
     */
    private fun handleDeepLink(deepLink: String) {
        Log.d(TAG, "🎯 Traitement deep link: $deepLink")
        
        when {
            deepLink.contains("coupleapp://subscription") -> {
                Log.d(TAG, "💎 Deep link abonnement")
                // TODO: Navigation vers écran abonnement
            }
            deepLink.contains("coupleapp://challenge") -> {
                Log.d(TAG, "🎯 Deep link défi")
                // TODO: Navigation vers défi spécifique
            }
            deepLink.contains("coupleapp://partner") -> {
                Log.d(TAG, "💑 Deep link partenaire")
                // TODO: Navigation vers connexion partenaire
            }
            else -> {
                Log.d(TAG, "❓ Deep link non reconnu: $deepLink")
            }
        }
    }
    
    // === Badge Management ===
    
    /**
     * Effacer les badges de l'app
     */
    private fun clearAppBadges() {
        try {
            // Utiliser reflection pour ShortcutBadger si disponible
            val shortcutBadgerClass = Class.forName("me.leolin.shortcutbadger.ShortcutBadger")
            val removeCountMethod = shortcutBadgerClass.getMethod("removeCount", android.content.Context::class.java)
            removeCountMethod.invoke(null, this)
            
            Log.d(TAG, "✅ Badges effacés")
            
        } catch (e: Exception) {
            Log.d(TAG, "⚠️ ShortcutBadger non disponible ou erreur: ${e.message}")
        }
    }
    
    // === Debug Methods ===
    
    /**
     * Obtenir l'état de debug de l'activité
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "isAppStateInitialized" to ::integratedAppState.isInitialized,
            "appDelegate" to IntegratedAppDelegate.instance.getDebugInfo(),
            "appState" to if (::integratedAppState.isInitialized) {
                integratedAppState.getDebugInfo()
            } else {
                "Not initialized"
            }
        )
    }
}

// === Composition Locals ===

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal pour IntegratedAppState - Accès dans tout l'arbre Compose
 */
val LocalIntegratedAppState = staticCompositionLocalOf<IntegratedAppState> {
    error("IntegratedAppState not provided")
}

/**
 * CompositionLocal pour ServiceContainer - Accès aux services dans Compose
 */
val LocalServiceContainer = staticCompositionLocalOf<ServiceContainer.type> {
    error("ServiceContainer not provided")
}
