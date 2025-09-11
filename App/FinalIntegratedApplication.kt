package com.love2loveapp

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.love2loveapp.core.services.firebase.AppCheckConfig
import com.love2loveapp.di.ServiceContainer
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

/**
 * 🚀 FinalIntegratedApplication - Application Complète avec Architecture Finale
 * 
 * Responsabilités :
 * - Initialisation complète de tous les managers
 * - Configuration Firebase, RevenueCat, App Check
 * - Point d'entrée unique pour l'architecture finale
 * - Coordination de tous les systèmes
 * 
 * Architecture : Application + Complete Integration + All Managers
 */
class FinalIntegratedApplication : Application() {
    
    companion object {
        private const val TAG = "FinalIntegratedApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "🚀 Initialisation application finale intégrée")
        
        // === Initialisation Core ===
        initializeServiceContainer()
        
        // === Initialisation Firebase ===
        initializeFirebase()
        
        // === Initialisation RevenueCat ===
        initializeRevenueCat()
        
        // === Initialisation Managers ===
        initializeAllManagers()
        
        // === Post-Initialization ===
        performPostInitialization()
        
        Log.d(TAG, "✅ Application finale intégrée initialisée avec succès")
    }
    
    /**
     * Initialiser ServiceContainer central
     */
    private fun initializeServiceContainer() {
        Log.d(TAG, "🏗️ Initialisation ServiceContainer")
        ServiceContainer.initialize(this)
    }
    
    /**
     * Initialiser Firebase
     */
    private fun initializeFirebase() {
        Log.d(TAG, "🔥 Initialisation Firebase")
        
        // Firebase App
        FirebaseApp.initializeApp(this)
        
        // Firebase App Check
        configureFirebaseAppCheck()
    }
    
    /**
     * Configurer Firebase App Check
     */
    private fun configureFirebaseAppCheck() {
        try {
            AppCheckConfig.appContext = this
            AppCheckConfig.configureAppCheck()
            Log.d(TAG, "✅ Firebase App Check configuré")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur configuration App Check", e)
        }
    }
    
    /**
     * Initialiser RevenueCat
     */
    private fun initializeRevenueCat() {
        Log.d(TAG, "💰 Initialisation RevenueCat")
        
        try {
            val apiKey = BuildConfig.REVENUECAT_GOOGLE_API_KEY
            
            if (apiKey.isNotBlank()) {
                Purchases.configure(
                    PurchasesConfiguration.Builder(this, apiKey)
                        .build()
                )
                Log.d(TAG, "✅ RevenueCat configuré")
            } else {
                Log.w(TAG, "⚠️ RevenueCat API Key manquante")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur configuration RevenueCat", e)
        }
    }
    
    /**
     * Initialiser tous les managers
     */
    private fun initializeAllManagers() {
        Log.d(TAG, "🎯 Initialisation de tous les managers")
        
        try {
            // === Service Managers ===
            initializeServiceManagers()
            
            // === Additional Managers ===
            initializeAdditionalManagers()
            
            // === Synchronisation ===
            synchronizeManagers()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur initialisation managers", e)
        }
    }
    
    /**
     * Initialiser Service Managers
     */
    private fun initializeServiceManagers() {
        Log.d(TAG, "🔧 Initialisation Service Managers")
        
        // Partner Service Manager
        val partnerManager = ServiceContainer.partnerServiceManager
        Log.d(TAG, "👥 PartnerServiceManager initialisé")
        
        // Content Service Manager
        val contentManager = ServiceContainer.contentServiceManager
        Log.d(TAG, "📚 ContentServiceManager initialisé")
        
        // System Service Manager
        val systemManager = ServiceContainer.systemServiceManager
        Log.d(TAG, "⚙️ SystemServiceManager initialisé")
    }
    
    /**
     * Initialiser Additional Managers
     */
    private fun initializeAdditionalManagers() {
        Log.d(TAG, "➕ Initialisation Additional Managers")
        
        // Test Manager
        val testManager = ServiceContainer.testManager
        Log.d(TAG, "🧪 TestManager initialisé")
        
        // Utils Manager
        val utilsManager = ServiceContainer.utilsManager
        Log.d(TAG, "🛠️ UtilsManager initialisé")
        
        // ViewModel Manager
        val viewModelManager = ServiceContainer.viewModelManager
        Log.d(TAG, "🎯 ViewModelManager initialisé")
    }
    
    /**
     * Synchroniser tous les managers
     */
    private fun synchronizeManagers() {
        Log.d(TAG, "🔄 Synchronisation des managers")
        
        try {
            // Créer AppState central
            val appState = ServiceContainer.createAppState()
            
            // Synchroniser ViewModels
            ServiceContainer.viewModelManager.synchronizeViewModels()
            
            // Démarrer monitoring performance
            ServiceContainer.systemServiceManager.startPerformanceMonitoring()
            
            Log.d(TAG, "✅ Managers synchronisés")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur synchronisation managers", e)
        }
    }
    
    /**
     * Post-initialisation
     */
    private fun performPostInitialization() {
        Log.d(TAG, "🏁 Post-initialisation")
        
        try {
            // Logger événement démarrage app
            ServiceContainer.systemServiceManager.logEvent(
                eventName = "app_startup",
                parameters = mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "version" to BuildConfig.VERSION_NAME
                )
            )
            
            // Effectuer health check initial
            performInitialHealthCheck()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur post-initialisation", e)
        }
    }
    
    /**
     * Health check initial
     */
    private fun performInitialHealthCheck() {
        Log.d(TAG, "🏥 Health check initial")
        
        try {
            // Test santé système
            val systemHealth = ServiceContainer.systemServiceManager.systemHealth.value
            Log.d(TAG, "🏥 System health: ${systemHealth.name}")
            
            // Test santé ViewModels
            val viewModelHealth = ServiceContainer.viewModelManager.viewModelHealth.value
            Log.d(TAG, "🎯 ViewModel health: ${viewModelHealth.name}")
            
            // Test santé utilitaires
            val utilsHealth = ServiceContainer.utilsManager.utilsHealth.value
            Log.d(TAG, "🛠️ Utils health: ${utilsHealth.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur health check", e)
        }
    }
    
    /**
     * Obtenir état complet de l'application
     */
    fun getApplicationState(): Map<String, Any> {
        return try {
            mapOf(
                "service_container_initialized" to ServiceContainer.isInitialized(),
                "partner_service_manager" to ServiceContainer.partnerServiceManager.getDebugInfo(),
                "content_service_manager" to ServiceContainer.contentServiceManager.getDebugInfo(),
                "system_service_manager" to ServiceContainer.systemServiceManager.getDebugInfo(),
                "test_manager" to ServiceContainer.testManager.getDebugInfo(),
                "utils_manager" to ServiceContainer.utilsManager.getDebugInfo(),
                "viewmodel_manager" to ServiceContainer.viewModelManager.getDebugInfo()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur état application", e)
            mapOf("error" to e.message)
        }
    }
}
