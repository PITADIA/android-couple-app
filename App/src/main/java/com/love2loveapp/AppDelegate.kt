package com.love2loveapp

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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
        
        // Services pour le header (accès global pour composants UI)
        lateinit var userCacheManager: com.love2loveapp.services.cache.UserCacheManager
            private set
        
        var partnerLocationService: com.love2loveapp.services.location.PartnerLocationService? = null
            private set
        
        var firebaseProfileService: com.love2loveapp.services.firebase.FirebaseProfileService? = null
            private set
        
        var locationSyncService: com.love2loveapp.services.location.LocationSyncService? = null
            private set
        
        // 🌍 Service unifié robuste (équivalent iOS LocationService)
        var unifiedLocationService: com.love2loveapp.services.location.UnifiedLocationService? = null
            private set
        
        var userDataIntegrationService: com.love2loveapp.services.integration.UserDataIntegrationService? = null
            private set
        
        var partnerSubscriptionSyncService: com.love2loveapp.services.subscription.PartnerSubscriptionSyncService? = null
            private set
        
        // 💝 Service favoris partagés
        var favoritesRepository: com.love2loveapp.services.favorites.FavoritesRepository? = null
            private set
        
        // 📅 Service questions du jour
        var dailyQuestionRepository: com.love2loveapp.services.dailyquestion.DailyQuestionRepository? = null
            private set
        
        // 👤 Service profil utilisateur
        var profileRepository: com.love2loveapp.services.profile.ProfileRepository? = null
            private set
        
        // 🎯 Gestionnaire centralisé des photos de profil (comme iOS)
        var profileImageManager: com.love2loveapp.services.ProfileImageManager? = null
            private set
        
        // 🔐 Service Cloud Functions sécurisées (architecture iOS)
        var cloudFunctionService: com.love2loveapp.services.CloudFunctionService? = null
            private set
        
        // 🎯 Service défis du jour
        var dailyChallengeRepository: com.love2loveapp.services.dailychallenge.DailyChallengeRepository? = null
            private set
        
        // 🔖 Service défis sauvegardés
        var savedChallengesRepository: com.love2loveapp.services.savedchallenges.SavedChallengesRepository? = null
            private set
        
        // 📔 Service journal événements
        var journalRepository: com.love2loveapp.services.journal.JournalRepository? = null
            private set
        
        // Instance globale pour accès depuis les services
        private lateinit var instance: AppDelegate
        
        fun getInstance(): AppDelegate = instance
    }
    
    // Services principaux
    private lateinit var locationService: LocationService
    private lateinit var freemiumManager: SimpleFreemiumManager
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialiser l'instance globale
        instance = this
        
        Log.i(TAG, "🚀 Love2Love: Initialisation de l'application")
        
        // 🔥 CRITIQUE: Initialiser Firebase AVANT tout le reste
        initializeFirebase()
        
        // Initialiser l'état global EN PREMIER (appState nécessaire pour UserDataIntegrationService)
        initializeAppState()
        
        // Initialiser les services (incluant UserDataIntegrationService)
        initializeServices()
        
        // Démarrer l'intégration Firebase
        startFirebaseIntegration()
        
        // Configuration initiale
        performInitialSetup()
        
        // 📱 DÉMARRER SERVICE WIDGETS pour mise à jour automatique
        try {
            val widgetRepository = com.love2loveapp.services.widgets.WidgetRepository.getInstance(this)
            // Démarrer la mise à jour en arrière-plan
            Thread {
                try {
                    Thread.sleep(5000) // Attendre initialisation complète + UserDataIntegrationService
                    Log.d(TAG, "📱 === DÉMARRAGE WIDGETS AVEC DEBUG ===")
                    
                    // DEBUG: Vérifier l'état avant mise à jour
                    debugWidgetState()
                    
                    // Utiliser runBlocking pour la méthode suspend
                    kotlinx.coroutines.runBlocking {
                        Log.d(TAG, "📱 Force refresh widget data...")
                        val result = widgetRepository.refreshWidgetData(forceUpdate = true)
                        
                        if (result.isSuccess) {
                            Log.d(TAG, "✅ Refresh widgets réussi!")
                        } else {
                            Log.e(TAG, "❌ Échec refresh widgets: ${result.exceptionOrNull()?.message}")
                        }
                    }
                    
                    // DEBUG: Vérifier l'état après mise à jour
                    Thread.sleep(2000) // Laisser le temps à la synchronisation
                    debugWidgetState()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur mise à jour widgets en arrière-plan: ${e.message}", e)
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur initialisation widgets: ${e.message}")
        }
        
        Log.i(TAG, "✅ Love2Love: Application initialisée avec succès")
    }
    
    /**
     * 🔍 DEBUG: État des widgets pour diagnostiquer le problème "0 days"
     */
    private fun debugWidgetState() {
        try {
            Log.d(TAG, "🔍 === DEBUG ÉTAT WIDGETS ===")
            
            // 1. Vérifier AppState et currentUser
            val currentUser = appState?.currentUser?.value
            Log.d(TAG, "👤 CurrentUser: ${if (currentUser != null) "✅" else "❌ NULL"}")
            
            if (currentUser != null) {
                Log.d(TAG, "  - name: ${currentUser.name}")
                Log.d(TAG, "  - relationshipStartDate: ${currentUser.relationshipStartDate}")
                
                if (currentUser.relationshipStartDate != null) {
                    val now = java.util.Date()
                    val diffMillis = now.time - currentUser.relationshipStartDate.time
                    val daysDiff = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
                    Log.d(TAG, "  - Jours calculés directement: $daysDiff")
                } else {
                    Log.e(TAG, "  - ❌ relationshipStartDate EST NULL!")
                }
            }
            
            // 2. Vérifier SharedPreferences Love2LoveWidget
            val love2lovePrefs = getSharedPreferences("love2love_widget_data", MODE_PRIVATE)
            val savedDaysTotal = love2lovePrefs.getInt("widget_days_total", -999)
            val savedUserName = love2lovePrefs.getString("widget_user_name", "NULL")
            val savedPartnerName = love2lovePrefs.getString("widget_partner_name", "NULL")
            val lastUpdate = love2lovePrefs.getLong("widget_last_update", 0)
            
            Log.d(TAG, "📋 SharedPreferences Love2LoveWidget:")
            Log.d(TAG, "  - widget_days_total: $savedDaysTotal")
            Log.d(TAG, "  - widget_user_name: $savedUserName")
            Log.d(TAG, "  - widget_partner_name: $savedPartnerName")
            Log.d(TAG, "  - widget_last_update: ${if (lastUpdate > 0) java.util.Date(lastUpdate) else "JAMAIS"}")
            
            if (savedDaysTotal <= 0) {
                Log.e(TAG, "❌ PROBLÈME DÉTECTÉ: widget_days_total = $savedDaysTotal")
            } else {
                Log.d(TAG, "✅ Données widgets semblent correctes")
            }
            
            // 3. Déclencher mise à jour manuelle du widget
            val intent = android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Log.d(TAG, "📢 Broadcast widget update envoyé")
            
            Log.d(TAG, "🔍 === FIN DEBUG WIDGETS ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur debug widgets: ${e.message}", e)
        }
    }
    
    /**
     * Initialiser Firebase avant tout le reste
     */
    private fun initializeFirebase() {
        try {
            Log.d(TAG, "🔧 Tentative d'initialisation Firebase...")
            
            // Vérifier si le fichier google-services.json est accessible
            val googleServicesResourceId = resources.getIdentifier(
                "google_services_json", "raw", packageName
            )
            Log.d(TAG, "🔧 google-services.json resource ID: $googleServicesResourceId")
            
            val defaultApp = FirebaseApp.initializeApp(this)
            if (defaultApp != null) {
                Log.i(TAG, "✅ Firebase initialisé avec succès")
            } else {
                Log.w(TAG, "⚠️ Firebase default init a échoué, tentative d'initialisation manuelle…")

                try {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:200633504634:android:4f3b5fbbd1e683dd08bcc3") // google_app_id
                        .setProjectId("love2love-26164")
                        .setApiKey("AIzaSyDLQVatTiceT1wy5_dBBHwvlJEeUBm4pC4")
                        .setGcmSenderId("200633504634")
                        .setStorageBucket("love2love-26164.firebasestorage.app")
                        .build()

                    FirebaseApp.initializeApp(this, options)
                    Log.i(TAG, "✅ Firebase initialisé manuellement")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Initialisation Firebase manuelle échouée", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception lors de l'initialisation Firebase", e)
            Log.e(TAG, "❌ Cause: ${e.cause}")
            Log.e(TAG, "❌ Message: ${e.message}")
        }
    }
    
    /**
     * Initialiser l'état global de l'application
     * UserDataIntegrationService déterminera l'état réel basé sur Firebase Auth
     */
    private fun initializeAppState() {
        Log.d(TAG, "🔧 Initialisation AppState")
        
        appState = AppState(this)
        
        // Commencer sur Launch Screen - UserDataIntegrationService déterminera la navigation
        Log.d(TAG, "🚀 Démarrage Launch Screen - UserDataIntegrationService déterminera l'état")
        appState.navigateToScreen(com.love2loveapp.models.AppScreen.Launch)
    }
    
    /**
     * Initialiser tous les services
     */
    private fun initializeServices() {
        Log.d(TAG, "🔧 Initialisation des services")
        
        try {
            // ======= PRIORITÉ 1: User Data Integration Service =======
            // DOIT être initialisé en PREMIER pour déterminer l'état d'authentification
            Log.d(TAG, "🔄 PRIORITÉ: Initialisation UserDataIntegrationService...")
            userDataIntegrationService = com.love2loveapp.services.integration.UserDataIntegrationService.getInstance(appState)
            userDataIntegrationService?.initialize() // ← CRUCIAL : Démarrer l'écoute Firebase Auth
            Log.d(TAG, "✅ UserDataIntegrationService initialisé et démarré")
            
            // Service de localisation
            Log.d(TAG, "📍 Initialisation LocationService...")
            locationService = LocationServiceImpl(this)
            Log.d(TAG, "✅ LocationService initialisé")
            
            // Manager freemium
            Log.d(TAG, "💎 Initialisation FreemiumManager...")
            freemiumManager = SimpleFreemiumManager()
            appState.setFreemiumManager(freemiumManager)
            Log.d(TAG, "✅ FreemiumManager initialisé")
            
            // Initialiser les singletons un par un avec logs
            Log.d(TAG, "💾 Initialisation QuestionCacheManager...")
            QuestionCacheManager.shared
            Log.d(TAG, "✅ QuestionCacheManager initialisé")
            
            Log.d(TAG, "📊 Initialisation PerformanceMonitor...")
            PerformanceMonitor.shared
            Log.d(TAG, "✅ PerformanceMonitor initialisé")
            
            // Initialiser les nouveaux services pour le système de cartes
            Log.d(TAG, "📦 Initialisation QuestionDataManager (nouveau)...")
            val questionDataManager = com.love2loveapp.services.QuestionDataManager.getInstance(this)
            Log.d(TAG, "✅ QuestionDataManager (nouveau) initialisé")
            
            Log.d(TAG, "🎯 Initialisation PackProgressService (nouveau)...")
            val packProgressService = com.love2loveapp.services.PackProgressService.getInstance(this)
            Log.d(TAG, "✅ PackProgressService (nouveau) initialisé")
            
            Log.d(TAG, "📊 Initialisation CategoryProgressService (pour statistiques)...")
            val categoryProgressService = com.love2loveapp.services.CategoryProgressService.getInstance(this)
            Log.d(TAG, "✅ CategoryProgressService (pour statistiques) initialisé")
            
            // Préchargement des catégories essentielles en arrière-plan
            Log.d(TAG, "⚡ Préchargement catégories essentielles...")
            questionDataManager.preloadEssentialCategories()
            
            // ========= NOUVEAUX SERVICES HEADER =========
            // UserCacheManager pour les images de profil
            Log.d(TAG, "🖼️ Initialisation UserCacheManager...")
            userCacheManager = com.love2loveapp.services.cache.UserCacheManager.getInstance(this)
            Log.d(TAG, "✅ UserCacheManager initialisé")
            
            // Firebase Profile Service
            Log.d(TAG, "🔥 Initialisation FirebaseProfileService...")
            firebaseProfileService = com.love2loveapp.services.firebase.FirebaseProfileService.getInstance()
            Log.d(TAG, "✅ FirebaseProfileService initialisé")
            
            // Partner Location Service
            Log.d(TAG, "📍 Initialisation PartnerLocationService...")
            partnerLocationService = com.love2loveapp.services.location.PartnerLocationService.getInstance()
            Log.d(TAG, "✅ PartnerLocationService initialisé")
            
            // Location Sync Service pour synchroniser la position utilisateur (ancien)
            Log.d(TAG, "🌍 Initialisation LocationSyncService...")
            locationSyncService = com.love2loveapp.services.location.LocationSyncService.getInstance(this)
            Log.d(TAG, "✅ LocationSyncService initialisé")
            
            // 🌍 Service unifié robuste (nouveau - équivalent iOS LocationService)
            Log.d(TAG, "🚀 Initialisation UnifiedLocationService (robuste)...")
            try {
                unifiedLocationService = com.love2loveapp.services.location.UnifiedLocationService.getInstance(this)
                Log.d(TAG, "✅ UnifiedLocationService initialisé")
                
                // Injecter AppState dans le service unifié
                unifiedLocationService?.setAppState(appState)
                Log.d(TAG, "✅ AppState injecté dans UnifiedLocationService")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur initialisation UnifiedLocationService: ${e.message}", e)
            }
            
            // Partner Subscription Sync Service pour partage d'abonnements
            Log.d(TAG, "🤝 Initialisation PartnerSubscriptionSyncService...")
            partnerSubscriptionSyncService = com.love2loveapp.services.subscription.PartnerSubscriptionSyncService.getInstance(this)
            Log.d(TAG, "✅ PartnerSubscriptionSyncService initialisé")
            
            // 💝 Favorites Repository pour favoris partagés
            Log.d(TAG, "💝 Initialisation FavoritesRepository...")
            favoritesRepository = com.love2loveapp.services.favorites.FavoritesRepository.getInstance(this)
            Log.d(TAG, "✅ FavoritesRepository initialisé")
            
            // 📅 Daily Question Repository pour questions du jour
            Log.d(TAG, "📅 Initialisation DailyQuestionRepository...")
            dailyQuestionRepository = com.love2loveapp.services.dailyquestion.DailyQuestionRepository.getInstance(this)
            Log.d(TAG, "✅ DailyQuestionRepository initialisé")
            
            // 🎯 Daily Challenge Repository pour défis du jour
            Log.d(TAG, "🎯 Initialisation DailyChallengeRepository...")
            dailyChallengeRepository = com.love2loveapp.services.dailychallenge.DailyChallengeRepository.getInstance(this)
            Log.d(TAG, "✅ DailyChallengeRepository initialisé")
            
            // 🔖 Saved Challenges Repository pour défis sauvegardés
            Log.d(TAG, "🔖 Initialisation SavedChallengesRepository...")
            savedChallengesRepository = com.love2loveapp.services.savedchallenges.SavedChallengesRepository.getInstance(this)
            Log.d(TAG, "✅ SavedChallengesRepository initialisé")
            
            // 📔 Journal Repository pour événements journal
            Log.d(TAG, "📔 Initialisation JournalRepository...")
            journalRepository = com.love2loveapp.services.journal.JournalRepository.getInstance(this)
            Log.d(TAG, "✅ JournalRepository initialisé")
            
            // 👤 Profile Repository pour profil utilisateur
            Log.d(TAG, "👤 Initialisation ProfileRepository...")
            profileRepository = com.love2loveapp.services.profile.ProfileRepository(
                firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                storage = com.google.firebase.storage.FirebaseStorage.getInstance(),
                auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            )
            Log.d(TAG, "✅ ProfileRepository initialisé")
            
            // 🔐 Cloud Function Service sécurisé (architecture iOS)
            Log.d(TAG, "🔐 Initialisation CloudFunctionService...")
            cloudFunctionService = com.love2loveapp.services.CloudFunctionService(
                firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance(),
                functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            )
            Log.d(TAG, "✅ CloudFunctionService initialisé")
            
            // 🎯 Profile Image Manager centralisé (comme iOS)
            Log.d(TAG, "🎯 Initialisation ProfileImageManager...")
            profileImageManager = com.love2loveapp.services.ProfileImageManager(
                firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                storage = com.google.firebase.storage.FirebaseStorage.getInstance(),
                auth = com.google.firebase.auth.FirebaseAuth.getInstance(),
                cloudFunctionService = cloudFunctionService!!
            )
            profileImageManager?.initialize(this)
            Log.d(TAG, "✅ ProfileImageManager initialisé")
            
            // Legacy singletons (pour compatibilité)
            Log.d(TAG, "📋 Initialisation PackProgressServiceLegacy...")
            PackProgressServiceLegacy.shared
            Log.d(TAG, "✅ PackProgressServiceLegacy initialisé")
            
            Log.d(TAG, "📚 Initialisation QuestionDataManagerLegacy...")
            QuestionDataManagerLegacy.shared
            Log.d(TAG, "✅ QuestionDataManagerLegacy initialisé")
            
            // Initialiser GooglePlayBillingService pour vérifier les achats existants au démarrage
            Log.d(TAG, "💳 Initialisation GooglePlayBillingService...")
            try {
                val billingService = com.love2loveapp.services.billing.GooglePlayBillingService.getInstance(this)
                // Le service se connectera automatiquement et vérifiera les achats existants
                Log.d(TAG, "✅ GooglePlayBillingService initialisé")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur initialisation GooglePlayBillingService: ${e.message}")
                // L'application peut continuer à fonctionner même si le billing échoue
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERREUR lors de l'initialisation des services", e)
            Log.e(TAG, "❌ Détails: ${e.message}")
            Log.e(TAG, "❌ Cause: ${e.cause}")
            Log.e(TAG, "❌ Stack: ${e.stackTrace.joinToString("\n")}")
        }
        
        Log.d(TAG, "✅ Services initialisés avec succès")
    }
    
    /**
     * Configuration initiale de l'application
     */
    private fun performInitialSetup() {
        Log.d(TAG, "⚙️ Configuration initiale")
        
        // Démarrer le monitoring de performance
        PerformanceMonitor.shared.startMonitoring()
        
        // Préchargement en arrière-plan (supprimé car causes des conflits)
        // Les services gèrent maintenant leur propre préchargement
        Log.d(TAG, "ℹ️ Préchargement géré par les services individuels")
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
    
    /**
     * 🔥 Démarre l'intégration Firebase temps réel
     */
    private fun startFirebaseIntegration() {
        Log.d(TAG, "🔥 Démarrage intégration Firebase...")
        
        // Démarrer le service d'intégration des données utilisateur
        userDataIntegrationService?.initialize()
        
        Log.d(TAG, "✅ Intégration Firebase démarrée")
    }
    
    /**
     * 🚀 Démarre les services de localisation si l'utilisateur est authentifié
     */
    fun startLocationServicesIfAuthenticated() {
        Log.d(TAG, "🔄 DÉMARRAGE SERVICES LOCALISATION (architecture robuste)...")
        
        // 🌍 NOUVEAU: Démarrer SEULEMENT le service unifié robuste (équivalent iOS)
        unifiedLocationService?.let { service ->
            try {
                Log.d(TAG, "🚀 Démarrage EXCLUSIF UnifiedLocationService (robuste)...")
                service.startAutomatic()
                Log.d(TAG, "✅ UnifiedLocationService démarré avec succès")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur démarrage UnifiedLocationService: ${e.message}", e)
            }
        } ?: Log.e(TAG, "❌ UnifiedLocationService est NULL!")
        
        // 🚫 ANCIEN SERVICE DÉSACTIVÉ pour éviter conflit GPS
        // locationSyncService?.startLocationSync()
        Log.d(TAG, "🚫 LocationSyncService (ancien) désactivé - utilisation UnifiedLocationService uniquement")
        
        // 👥 CRITIQUE: Démarrer la synchronisation partenaire automatique
        Log.d(TAG, "🚀 TENTATIVE démarrage PartnerLocationService...")
        partnerLocationService?.let { service ->
            try {
                Log.d(TAG, "📞 Appel startAutoSyncIfPartnerExists()...")
                service.startAutoSyncIfPartnerExists()
                Log.d(TAG, "✅ PartnerLocationService auto-sync démarré avec succès")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur démarrage PartnerLocationService: ${e.message}", e)
            }
        } ?: Log.e(TAG, "❌ partnerLocationService est NULL dans startLocationServicesIfAuthenticated!")
    }
    
    /**
     * ⏹️ Arrête les services de localisation
     */
    fun stopLocationServices() {
        Log.d(TAG, "⏹️ Arrêt services de localisation...")
        
        // Arrêter service unifié robuste
        unifiedLocationService?.let { service ->
            try {
                service.stop()
                Log.d(TAG, "✅ UnifiedLocationService arrêté")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur arrêt UnifiedLocationService: ${e.message}", e)
            }
        }
        
        // Arrêter ancien service
        locationSyncService?.let { service ->
            try {
                service.stopLocationSync()
                Log.d(TAG, "✅ LocationSyncService (ancien) arrêté")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur arrêt LocationSyncService: ${e.message}", e)
            }
        }
        
        // Arrêter service partenaire
        partnerLocationService?.stopSync()
        
        Log.d(TAG, "✅ Tous les services de localisation arrêtés")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG, "👋 Love2Love: Application terminée")
        
        // Nettoyer les services
        if (::locationService.isInitialized) {
            locationService.stopLocationUpdates()
        }
        
        // Nettoyer les nouveaux services
        locationSyncService?.cleanup()
        partnerLocationService?.cleanup()
        userDataIntegrationService?.cleanup()
        partnerSubscriptionSyncService?.cleanup()
        favoritesRepository?.cleanup()
        dailyQuestionRepository?.cleanup()
        dailyChallengeRepository?.cleanup()
        savedChallengesRepository?.cleanup()
        journalRepository?.cleanup()
        profileRepository?.cleanup()
        
        PerformanceMonitor.shared.stopMonitoring()
    }
}
