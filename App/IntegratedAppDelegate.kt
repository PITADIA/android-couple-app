package com.love2loveapp

import android.app.Application
import android.util.Log
import com.love2loveapp.di.ServiceContainer

/**
 * 🚀 IntegratedAppDelegate - Point d'Entrée de l'Application Intégrée
 * 
 * Responsabilités :
 * - Initialisation du ServiceContainer (DI)
 * - Configuration Firebase coordonnée
 * - Setup RevenueCat avec clés sécurisées
 * - Gestion du cycle de vie applicatif
 * 
 * Architecture : Application + Dependency Injection
 */
class IntegratedAppDelegate : Application() {
    
    companion object {
        private const val TAG = "IntegratedAppDelegate"
        
        // Instance globale pour accès depuis Activities
        lateinit var instance: IntegratedAppDelegate
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "🚀 Initialisation IntegratedAppDelegate")
        instance = this
        
        // 1. Initialiser le ServiceContainer (DI central)
        initializeServiceContainer()
        
        // 2. Configuration Firebase coordonnée
        configureFirebase()
        
        // 3. Configuration RevenueCat sécurisée
        configureRevenueCat()
        
        // 4. Configuration App Check (sécurité)
        configureAppCheck()
        
        Log.d(TAG, "✅ IntegratedAppDelegate initialisé avec succès")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "🧹 Nettoyage IntegratedAppDelegate")
        
        // Cleanup des services
        ServiceContainer.cleanup()
    }
    
    // === Private Configuration Methods ===
    
    /**
     * Initialisation du ServiceContainer - Point central de DI
     */
    private fun initializeServiceContainer() {
        Log.d(TAG, "🏗️ Initialisation ServiceContainer")
        
        try {
            ServiceContainer.initialize(this)
            
            // Vérifier l'état des services
            val servicesStatus = ServiceContainer.getServicesStatus()
            Log.d(TAG, "📊 État des services: $servicesStatus")
            
            // Validation critique
            if (!ServiceContainer.isInitialized) {
                throw IllegalStateException("ServiceContainer initialization failed")
            }
            
            Log.d(TAG, "✅ ServiceContainer initialisé avec succès")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Erreur initialisation ServiceContainer", e)
            throw e // Critical error - app cannot continue
        }
    }
    
    /**
     * Configuration Firebase coordonnée via ServiceContainer
     */
    private fun configureFirebase() {
        Log.d(TAG, "🔥 Configuration Firebase")
        
        try {
            // Firebase est configuré automatiquement via ServiceContainer
            val firebaseCoordinator = ServiceContainer.firebaseCoordinator
            
            // Vérifier que Firebase est prêt
            if (!firebaseCoordinator.isInitialized()) {
                Log.w(TAG, "⚠️ Firebase Coordinator pas encore initialisé")
            }
            
            Log.d(TAG, "✅ Firebase configuré")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Erreur configuration Firebase", e)
            // Firebase error is not critical for app startup
        }
    }
    
    /**
     * Configuration RevenueCat avec clés sécurisées
     */
    private fun configureRevenueCat() {
        Log.d(TAG, "💰 Configuration RevenueCat")
        
        try {
            // Récupérer clé API depuis BuildConfig (sécurisée)
            val revenueCatKey = BuildConfig.REVENUECAT_GOOGLE_API_KEY
            
            if (revenueCatKey.isEmpty() || revenueCatKey == "default_key") {
                Log.w(TAG, "⚠️ RevenueCat API key non configurée")
                return
            }
            
            // Configuration RevenueCat
            com.revenuecat.purchases.Purchases.configure(
                com.revenuecat.purchases.PurchasesConfiguration.Builder(this, revenueCatKey)
                    .appUserID(null) // Will be set when user authenticates
                    .build()
            )
            
            Log.d(TAG, "✅ RevenueCat configuré")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Erreur configuration RevenueCat", e)
            // RevenueCat error is not critical for app startup
        }
    }
    
    /**
     * Configuration App Check pour sécurité Firebase
     */
    private fun configureAppCheck() {
        Log.d(TAG, "🛡️ Configuration App Check")
        
        try {
            // Configuration via AppCheckConfig (existant)
            val appCheckConfig = com.love2loveapp.config.AppCheckConfig
            appCheckConfig.appContext = this
            
            // App Check sera configuré automatiquement
            Log.d(TAG, "✅ App Check configuré")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Erreur configuration App Check", e)
            // App Check error is not critical for app startup
        }
    }
    
    // === Public Methods ===
    
    /**
     * Obtenir l'AppState central intégré
     */
    fun getIntegratedAppState(): com.love2loveapp.core.viewmodels.IntegratedAppState {
        return ServiceContainer.createAppState()
    }
    
    /**
     * Vérifier si l'application est correctement initialisée
     */
    fun isAppReady(): Boolean {
        return ServiceContainer.isInitialized
    }
    
    /**
     * Obtenir l'état de debug de l'application
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "isAppReady" to isAppReady(),
            "servicesStatus" to ServiceContainer.getServicesStatus(),
            "buildConfig" to mapOf(
                "debugLoggingEnabled" to BuildConfig.DEBUG_LOGGING_ENABLED,
                "hasRevenueCatKey" to (BuildConfig.REVENUECAT_GOOGLE_API_KEY != "default_key"),
                "hasFirebaseDebugToken" to BuildConfig.FIREBASE_DEBUG_TOKEN.isNotEmpty()
            )
        )
    }
}
