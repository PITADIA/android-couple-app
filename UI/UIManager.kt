package com.love2loveapp.ui.managers

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.core.services.managers.PartnerServiceManager
import com.love2loveapp.core.services.managers.ContentServiceManager
import com.love2loveapp.core.services.managers.SystemServiceManager
import com.love2loveapp.core.services.navigation.NavigationManager
import com.love2loveapp.di.ServiceContainer
import com.love2loveapp.model.repository.AuthRepository
import com.love2loveapp.model.repository.UserRepository
import com.love2loveapp.model.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🎨 UIManager - Gestionnaire Centralisé de l'Interface Utilisateur
 * 
 * Responsabilités :
 * - Gestion centralisée de tous les composants UI
 * - Injection de dépendances pour l'UI via CompositionLocal
 * - Thème et configuration UI globale
 * - États UI réactifs et navigation
 * 
 * Architecture : UI Manager + CompositionLocal + Theme Management
 */
class UIManager(
    private val context: Context,
    private val integratedAppState: IntegratedAppState
) {
    
    companion object {
        private const val TAG = "UIManager"
    }
    
    // === Coroutine Scope ===
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // === UI States ===
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()
    
    private val _currentTheme = MutableStateFlow(UITheme.LIGHT)
    val currentTheme: StateFlow<UITheme> = _currentTheme.asStateFlow()
    
    private val _isUIReady = MutableStateFlow(false)
    val isUIReady: StateFlow<Boolean> = _isUIReady.asStateFlow()
    
    init {
        Log.d(TAG, "🎨 Initialisation UIManager")
        initializeUI()
    }
    
    // === UI Initialization ===
    
    /**
     * Initialiser l'UI Manager
     */
    private fun initializeUI() {
        Log.d(TAG, "🚀 Configuration UI globale")
        
        // Configuration thème par défaut
        setTheme(UITheme.LIGHT)
        
        // Marquer UI comme prête
        _isUIReady.value = true
    }
    
    // === Theme Management ===
    
    /**
     * Changer le thème de l'application
     */
    fun setTheme(theme: UITheme) {
        Log.d(TAG, "🎨 Changement thème: ${theme.name}")
        _currentTheme.value = theme
        _isDarkMode.value = (theme == UITheme.DARK)
    }
    
    /**
     * Basculer entre mode clair/sombre
     */
    fun toggleDarkMode() {
        val newTheme = if (_isDarkMode.value) UITheme.LIGHT else UITheme.DARK
        setTheme(newTheme)
    }
    
    // === CompositionLocal Providers ===
    
    /**
     * Fournir tous les services via CompositionLocal
     */
    @Composable
    fun ProvideServices(
        content: @Composable () -> Unit
    ) {
        CompositionLocalProvider(
            // === Core Services ===
            LocalIntegratedAppState provides integratedAppState,
            LocalUIManager provides this,
            
            // === Service Managers ===
            LocalPartnerServiceManager provides integratedAppState.partnerServiceManager,
            LocalContentServiceManager provides integratedAppState.contentServiceManager,
            LocalSystemServiceManager provides integratedAppState.systemServiceManager,
            LocalNavigationManager provides integratedAppState.navigationManager,
            
            // === Repositories ===
            LocalAuthRepository provides ServiceContainer.authRepository,
            LocalUserRepository provides ServiceContainer.userRepository,
            LocalLocationRepository provides ServiceContainer.locationRepository,
            
            content = content
        )
    }
    
    // === UI Actions ===
    
    /**
     * Logger événement UI
     */
    fun logUIEvent(screenName: String, action: String, parameters: Map<String, Any> = emptyMap()) {
        Log.d(TAG, "📊 UI Event: $screenName -> $action")
        integratedAppState.systemServiceManager.logEvent(
            eventName = "ui_$action",
            parameters = parameters + mapOf(
                "screen" to screenName,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Logger navigation
     */
    fun logNavigation(fromScreen: String, toScreen: String) {
        Log.d(TAG, "🧭 Navigation: $fromScreen -> $toScreen")
        logUIEvent(
            screenName = toScreen,
            action = "navigate",
            parameters = mapOf("from_screen" to fromScreen)
        )
    }
    
    /**
     * Gérer erreur UI
     */
    fun handleUIError(error: Throwable, context: String) {
        Log.e(TAG, "❌ UI Error in $context", error)
        integratedAppState.systemServiceManager.logEvent(
            eventName = "ui_error",
            parameters = mapOf(
                "context" to context,
                "error_message" to (error.message ?: "Unknown error"),
                "error_type" to error.javaClass.simpleName
            )
        )
    }
    
    // === Debug ===
    
    /**
     * État de debug du UIManager
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "isDarkMode" to _isDarkMode.value,
            "currentTheme" to _currentTheme.value.name,
            "isUIReady" to _isUIReady.value
        )
    }
}

/**
 * Thèmes disponibles
 */
enum class UITheme {
    LIGHT,
    DARK,
    AUTO
}

// === CompositionLocal Definitions ===

val LocalIntegratedAppState = staticCompositionLocalOf<IntegratedAppState> {
    error("IntegratedAppState not provided")
}

val LocalUIManager = staticCompositionLocalOf<UIManager> {
    error("UIManager not provided")
}

val LocalPartnerServiceManager = staticCompositionLocalOf<PartnerServiceManager> {
    error("PartnerServiceManager not provided")
}

val LocalContentServiceManager = staticCompositionLocalOf<ContentServiceManager> {
    error("ContentServiceManager not provided")
}

val LocalSystemServiceManager = staticCompositionLocalOf<SystemServiceManager> {
    error("SystemServiceManager not provided")
}

val LocalNavigationManager = staticCompositionLocalOf<NavigationManager> {
    error("NavigationManager not provided")
}

val LocalAuthRepository = staticCompositionLocalOf<AuthRepository> {
    error("AuthRepository not provided")
}

val LocalUserRepository = staticCompositionLocalOf<UserRepository> {
    error("UserRepository not provided")
}

val LocalLocationRepository = staticCompositionLocalOf<LocationRepository> {
    error("LocationRepository not provided")
}
