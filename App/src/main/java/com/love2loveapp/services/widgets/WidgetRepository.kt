package com.love2loveapp.services.widgets

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.widgets.WidgetData
import com.love2loveapp.models.widgets.RelationshipStats
import com.love2loveapp.models.widgets.DistanceInfo
import com.love2loveapp.models.UserLocation
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * 📱 WidgetRepository - Service central données widgets Android
 * 
 * Équivalent Android du WidgetService iOS :
 * - Gestion données temps réel pour widgets
 * - Synchronisation Firebase via Cloud Functions
 * - Cache local SharedPreferences (équivalent App Group iOS)
 * - Observers changements utilisateur/partenaire/localisation
 * - Notification widgets automatique
 */
class WidgetRepository private constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "WidgetRepository"
        private const val PREFS_NAME = "widget_data_prefs"
        private const val KEY_WIDGET_DATA = "widget_data_json"
        private const val KEY_LAST_UPDATE = "last_update_timestamp"
        
        @Volatile
        private var INSTANCE: WidgetRepository? = null
        
        /**
         * 🏗️ Singleton getInstance pattern
         */
        fun getInstance(context: Context): WidgetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WidgetRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // SharedPreferences pour cache local (équivalent App Group iOS)
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Coroutine scope pour opérations asynchrones
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // StateFlow pour données widgets réactives
    private val _widgetData = MutableStateFlow(WidgetData.empty())
    val widgetData: StateFlow<WidgetData> = _widgetData.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    // 🔥 FIREBASE FUNCTIONS - Récupération sécurisée données partenaire
    private val functions: FirebaseFunctions = Firebase.functions
    
    // Observeurs pour changements app
    private var userObserverJob: Job? = null
    private var locationObserverJob: Job? = null
    private var partnerObserverJob: Job? = null
    
    init {
        Log.d(TAG, "📱 Initialisation WidgetRepository")
        loadCachedData()
        setupObservers()
    }
    
    // ========== PUBLIC API ==========
    
    /**
     * 🔄 Rafraîchir données widgets depuis app
     * 
     * Méthode principale appelée par l'app pour mettre à jour widgets
     */
    suspend fun refreshWidgetData(forceUpdate: Boolean = false): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Début rafraîchissement données widgets (force=$forceUpdate)")
                _isLoading.value = true
                _lastError.value = null
                
                val currentData = _widgetData.value
                
                // Vérifier si mise à jour nécessaire
                if (!forceUpdate && !currentData.needsUpdate()) {
                    Log.d(TAG, "✅ Données widgets encore fraîches, pas de mise à jour")
                    _isLoading.value = false
                    return@withContext Result.success(Unit)
                }
                
                // 🔍 RÉCUPÉRER DONNÉES DEPUIS APP DELEGATE AVEC DEBUG DÉTAILLÉ
                val appState = AppDelegate.appState
                val currentUser = appState?.currentUser?.value
                
                Log.d(TAG, "🔍 === DEBUG PIPELINE DONNÉES ===")
                Log.d(TAG, "👤 AppState: ${if (appState != null) "✅" else "❌ NULL"}")
                Log.d(TAG, "👤 CurrentUser: ${if (currentUser != null) "✅" else "❌ NULL"}")
                
                if (currentUser != null) {
                    Log.d(TAG, "👤 User.name: ${currentUser.name}")
                    Log.d(TAG, "👤 User.id: ${currentUser.id}")
                    Log.d(TAG, "👤 User.partnerId: ${currentUser.partnerId}")
                    Log.d(TAG, "📅 User.relationshipStartDate: ${currentUser.relationshipStartDate}")
                    
                    if (currentUser.relationshipStartDate != null) {
                        Log.d(TAG, "✅ relationshipStartDate TROUVÉE: ${currentUser.relationshipStartDate}")
                        val now = java.util.Date()
                        val diffMillis = now.time - currentUser.relationshipStartDate.time
                        val daysDiff = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
                        Log.d(TAG, "📊 Calcul direct jours: $daysDiff")
                    } else {
                        Log.e(TAG, "❌ relationshipStartDate EST NULL!")
                        Log.e(TAG, "❌ Sans date début, impossible de calculer jours ensemble")
                    }
                } else {
                    Log.e(TAG, "❌ currentUser est NULL - vérifiez UserDataIntegrationService")
                }
                
                // 🎯 RÉCUPÉRER DONNÉES PARTENAIRE VIA FIREBASE si connecté
                val hasPartner = currentUser?.partnerId != null
                val partnerData = if (hasPartner) {
                    Log.d(TAG, "👥 Récupération données partenaire Firebase: ${currentUser!!.partnerId}")
                    getPartnerDataForWidget(currentUser.partnerId!!)
                } else {
                    Log.d(TAG, "❌ Pas de partenaire connecté")
                    null
                }
                
                // 💕 CALCULER STATISTIQUES RELATION avec vraie date utilisateur
                val relationshipStats = if (currentUser?.relationshipStartDate != null) {
                    val startDate = currentUser.relationshipStartDate
                    Log.d(TAG, "💕 === CALCUL STATISTIQUES RELATION ===")
                    Log.d(TAG, "💕 Date début relation: $startDate")
                    Log.d(TAG, "💕 Date actuelle: ${java.util.Date()}")
                    
                    val stats = RelationshipStats.calculateFromStartDate(startDate)
                    
                    if (stats != null) {
                        Log.d(TAG, "✅ Statistiques calculées:")
                        Log.d(TAG, "  - daysTotal: ${stats.daysTotal}")
                        Log.d(TAG, "  - formattedDuration: ${stats.formattedDuration}")
                        Log.d(TAG, "  - daysUntilNextAnniversary: ${stats.daysUntilNextAnniversary}")
                        Log.d(TAG, "  - isAnniversaryToday: ${stats.isAnniversaryToday}")
                    } else {
                        Log.e(TAG, "❌ RelationshipStats.calculateFromStartDate() a retourné NULL!")
                    }
                    
                    stats
                } else {
                    Log.e(TAG, "❌ Impossible de calculer statistiques: relationshipStartDate est NULL")
                    null
                }
                
                // 🌍 RÉCUPÉRER LOCALISATIONS
                val userLocation = currentUser?.currentLocation
                val partnerLocation = partnerData?.location
                
                Log.d(TAG, "📍 Localisation utilisateur: ${userLocation != null}")
                Log.d(TAG, "📍 Localisation partenaire: ${partnerLocation != null}")
                
                // 🧮 CALCULER DISTANCE avec nouvelles fonctions Firebase
                val distanceInfo = if (userLocation != null && partnerLocation != null) {
                    Log.d(TAG, "🌍 Calcul distance partenaires avec Firebase")
                    DistanceInfo.calculateBetweenPartners(userLocation, partnerLocation)
                } else {
                    null
                }
                
                Log.d(TAG, "👥 Partenaire Firebase: ${partnerData?.name ?: "null"}")
                Log.d(TAG, "📍 Distance: ${distanceInfo?.formattedDistance ?: "N/A"}")
                
                // 🔧 CONSTRUIRE NOUVELLES DONNÉES WIDGET avec données Firebase
                val newWidgetData = WidgetData(
                    userName = currentUser?.name,
                    userProfileImageUrl = currentUser?.profileImageURL,
                    partnerName = partnerData?.name,
                    partnerProfileImageUrl = partnerData?.profileImageUrl,
                    hasSubscription = true, // 🆓 Tous les widgets sont gratuits (selon rapport iOS)
                    hasPartner = partnerData != null,
                    relationshipStats = relationshipStats,
                    distanceInfo = distanceInfo,
                    lastUpdated = Date()
                )
                
                Log.d(TAG, "✅ Nouvelles données widgets: $newWidgetData")
                
                // 💾 SAUVEGARDER ET NOTIFIER
                saveWidgetData(newWidgetData)
                _widgetData.value = newWidgetData
                
                // 📢 SYNC AVEC LOVE2LOVE WIDGET SYSTEM
                syncWithLove2LoveWidgets(newWidgetData)
                
                // 📢 NOTIFIER WIDGETS DE LA MISE À JOUR
                notifyWidgetsUpdate()
                
                _isLoading.value = false
                Log.d(TAG, "🎉 Rafraîchissement widgets terminé avec succès")
                
                Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur rafraîchissement widgets: ${e.message}", e)
                _isLoading.value = false
                _lastError.value = "Erreur mise à jour widgets: ${e.message}"
                Result.failure(e)
            }
        }
    }
    
    /**
     * 📖 Obtenir données widgets actuelles
     * 
     * Version synchrone pour AppWidgetProvider
     */
    fun getCurrentWidgetData(): WidgetData {
        return _widgetData.value
    }
    
    /**
     * 🔄 Démarrer observers automatiques
     * 
     * Lance surveillance changements app pour mise à jour auto
     */
    fun startAutoRefresh() {
        Log.d(TAG, "🔄 Démarrage auto-refresh widgets")
        
        // Observer changements utilisateur
        userObserverJob = repositoryScope.launch {
            AppDelegate.appState?.currentUser?.let { user ->
                delay(2000) // Éviter spam updates
                refreshWidgetData()
            }
        }
        
        // Observer changements localisation
        locationObserverJob = repositoryScope.launch {
            // Observer via LocationSyncService si disponible
            while (isActive) {
                delay(30000) // Vérifier toutes les 30 secondes
                val currentData = _widgetData.value
                if (currentData.isDataComplete && currentData.needsUpdate(maxAgeMinutes = 30)) {
                    refreshWidgetData()
                }
            }
        }
    }
    
    /**
     * ⏹️ Arrêter observers automatiques
     */
    fun stopAutoRefresh() {
        Log.d(TAG, "⏹️ Arrêt auto-refresh widgets")
        userObserverJob?.cancel()
        locationObserverJob?.cancel()
        partnerObserverJob?.cancel()
    }
    
    // ========== PRIVATE METHODS ==========
    
    /**
     * 💾 Charger données cachées au démarrage
     */
    private fun loadCachedData() {
        try {
            val cachedJson = sharedPrefs.getString(KEY_WIDGET_DATA, null)
            if (cachedJson != null) {
                val cachedData = WidgetData.fromJson(cachedJson)
                if (cachedData != null) {
                    _widgetData.value = cachedData
                    Log.d(TAG, "💾 Données widgets chargées depuis cache: ${cachedData.lastUpdated}")
                    return
                }
            }
            
            Log.d(TAG, "💾 Aucune donnée cachée, utilisation données vides")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement cache: ${e.message}", e)
        }
    }
    
    /**
     * 💾 Sauvegarder données dans SharedPreferences
     */
    private fun saveWidgetData(data: WidgetData) {
        try {
            val jsonData = data.toJson()
            sharedPrefs.edit()
                .putString(KEY_WIDGET_DATA, jsonData)
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply()
            
            Log.d(TAG, "💾 Données widgets sauvegardées: ${data.lastUpdated}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde: ${e.message}", e)
        }
    }
    
    /**
     * 👂 Configurer observers changements app
     */
    private fun setupObservers() {
        Log.d(TAG, "👂 Configuration observers widgets")
        
        // Observer sera configuré quand AppDelegate sera complètement initialisé
        repositoryScope.launch {
            delay(5000) // Attendre initialisation complète app
            startAutoRefresh()
        }
    }
    
    
    /**
     * 📢 Synchroniser avec le système Love2LoveWidget
     * 
     * Sauvegarde directement dans les SharedPreferences utilisées par Love2LoveWidget
     */
    private fun syncWithLove2LoveWidgets(widgetData: WidgetData) {
        try {
            Log.d(TAG, "🔄 === SYNCHRONISATION LOVE2LOVE WIDGETS ===")
            
            val relationshipStats = widgetData.relationshipStats
            val daysTotal = relationshipStats?.daysTotal ?: 0
            
            Log.d(TAG, "📊 Données à synchroniser:")
            Log.d(TAG, "  - relationshipStats: ${if (relationshipStats != null) "✅" else "❌ NULL"}")
            Log.d(TAG, "  - daysTotal: $daysTotal")
            Log.d(TAG, "  - formattedDuration: ${relationshipStats?.formattedDuration}")
            Log.d(TAG, "  - userName: ${widgetData.userName}")
            Log.d(TAG, "  - partnerName: ${widgetData.partnerName}")
            Log.d(TAG, "  - hasSubscription: ${widgetData.hasSubscription}")
            
            if (daysTotal <= 0) {
                Log.e(TAG, "❌ PROBLÈME CRITIQUE: daysTotal = $daysTotal")
                Log.e(TAG, "❌ Les widgets vont afficher 0 jours!")
                
                if (relationshipStats == null) {
                    Log.e(TAG, "❌ Cause: relationshipStats est NULL")
                } else {
                    Log.e(TAG, "❌ Cause: relationshipStats.daysTotal = ${relationshipStats.daysTotal}")
                }
            }
            
            // Sauvegarder directement dans les SharedPreferences Love2LoveWidget 
            val love2lovePrefs = context.getSharedPreferences("love2love_widget_data", android.content.Context.MODE_PRIVATE)
            val editor = love2lovePrefs.edit()
            
            // Clés SharedPreferences exactes du système Love2LoveWidget
            editor.putInt("widget_days_total", daysTotal)
            editor.putString("widget_duration", relationshipStats?.formattedDuration ?: "")
            editor.putInt("widget_days_to_anniversary", relationshipStats?.daysUntilNextAnniversary ?: 0)
            editor.putString("widget_distance", widgetData.distanceInfo?.formattedDistance)
            editor.putString("widget_user_name", widgetData.userName)
            editor.putString("widget_partner_name", widgetData.partnerName)
            editor.putString("widget_user_image_url", widgetData.userProfileImageUrl)
            editor.putString("widget_partner_image_url", widgetData.partnerProfileImageUrl)
            editor.putBoolean("widget_has_subscription", widgetData.hasSubscription)
            editor.putLong("widget_last_update", System.currentTimeMillis())
            
            // TODO: Ajouter support des coordonnées GPS plus tard
            editor.apply()
            
            Log.d(TAG, "✅ Données sauvées dans SharedPreferences Love2LoveWidget")
            
            // Vérification: relire les données pour confirmer la sauvegarde
            val savedDaysTotal = love2lovePrefs.getInt("widget_days_total", -1)
            Log.d(TAG, "🔍 Vérification: widget_days_total sauvé = $savedDaysTotal")
            
            if (savedDaysTotal != daysTotal) {
                Log.e(TAG, "❌ ERREUR SAUVEGARDE: Attendu $daysTotal, sauvé $savedDaysTotal")
            } else {
                Log.d(TAG, "✅ Sauvegarde confirmée: $savedDaysTotal jours")
            }
            
            // Déclencher la mise à jour des widgets Love2LoveWidget
            triggerLove2LoveWidgetUpdate()
            
            Log.d(TAG, "✅ === SYNCHRONISATION TERMINÉE ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur synchronisation Love2LoveWidget: ${e.message}", e)
        }
    }
    
    /**
     * 📢 Déclencher mise à jour widgets Love2LoveWidget
     */
    private fun triggerLove2LoveWidgetUpdate() {
        try {
            // Broadcast pour déclencher mise à jour des widgets Love2LoveWidget
            val intent = android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            
            Log.d(TAG, "📢 Broadcast Love2LoveWidget update envoyé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur broadcast Love2LoveWidget: ${e.message}")
        }
    }

    /**
     * 📢 Notifier tous les widgets de mise à jour
     * 
     * Équivalent iOS WidgetCenter.shared.reloadAllTimelines()
     */
    private fun notifyWidgetsUpdate() {
        try {
            Log.d(TAG, "📢 Notification mise à jour widgets")
            
            // Utiliser WidgetUpdateService pour broadcaster
            val intent = android.content.Intent("com.love2loveapp.WIDGET_UPDATE")
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            
            Log.d(TAG, "✅ Broadcast widget update envoyé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur notification widgets: ${e.message}")
        }
    }
    
    /**
     * 🧹 Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleanup WidgetRepository")
        stopAutoRefresh()
        repositoryScope.cancel()
    }
    
    /**
     * 📊 Debug info pour analytics
     */
    fun getDebugInfo(): Map<String, Any> {
        val data = _widgetData.value
        return mapOf(
            "repository_initialized" to true,
            "data_complete" to data.isDataComplete,
            "has_subscription" to data.hasSubscription,
            "has_partner" to data.hasPartner,
            "data_age_minutes" to if (data.lastUpdated != Date()) {
                (Date().time - data.lastUpdated.time) / (60 * 1000)
            } else 0,
            "is_loading" to _isLoading.value,
            "last_error" to (_lastError.value != null)
        )
    }
    
    // ==============================================
    // 🔥 FIREBASE CLOUD FUNCTIONS INTEGRATION
    // ==============================================
    
    /**
     * 👥 Récupérer infos partenaire via Cloud Function sécurisée 
     * Équivalent iOS fetchPartnerInfo()
     */
    private suspend fun fetchPartnerInfo(partnerId: String): PartnerInfo? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "👥 Récupération infos partenaire via Cloud Function")
            
            val data = hashMapOf(
                "partnerId" to partnerId
            )
            
            val result = functions
                .getHttpsCallable("getPartnerInfo")
                .call(data)
                .await()
                
            val response = result.data as? Map<String, Any>
            val success = response?.get("success") as? Boolean ?: false
            
            if (!success) {
                Log.e(TAG, "❌ getPartnerInfo: Échec récupération")
                return@withContext null
            }
            
            val partnerInfo = response?.get("partnerInfo") as? Map<String, Any>
            if (partnerInfo == null) {
                Log.e(TAG, "❌ getPartnerInfo: Données partenaire manquantes")
                return@withContext null
            }
            
            val partner = PartnerInfo(
                name = partnerInfo["name"] as? String ?: "Partenaire",
                isSubscribed = partnerInfo["isSubscribed"] as? Boolean ?: false,
                subscriptionType = partnerInfo["subscriptionType"] as? String,
                profileImageURL = partnerInfo["profileImageURL"] as? String
            )
            
            Log.d(TAG, "✅ getPartnerInfo: Infos partenaire récupérées - ${partner.name}")
            Log.d(TAG, "✅ getPartnerInfo: Photo profil: ${if (partner.profileImageURL != null) "Présente" else "Absente"}")
            
            partner
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ fetchPartnerInfo: Erreur Cloud Function", e)
            null
        }
    }
    
    /**
     * 🌍 Récupérer localisation partenaire via Cloud Function sécurisée
     * Équivalent iOS fetchPartnerLocation()
     */
    private suspend fun fetchPartnerLocation(partnerId: String): UserLocation? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🌍 Récupération localisation partenaire via Cloud Function")
            
            val data = hashMapOf(
                "partnerId" to partnerId
            )
            
            val result = functions
                .getHttpsCallable("getPartnerLocation")
                .call(data)
                .await()
                
            val response = result.data as? Map<String, Any>
            val success = response?.get("success") as? Boolean ?: false
            
            if (!success) {
                val reason = response?.get("reason") as? String
                if (reason == "NO_LOCATION") {
                    Log.d(TAG, "🌍 getPartnerLocation: Pas de localisation partenaire")
                } else {
                    Log.e(TAG, "❌ getPartnerLocation: Échec récupération")
                }
                return@withContext null
            }
            
            val locationData = response?.get("location") as? Map<String, Any>
            if (locationData == null) {
                Log.e(TAG, "❌ getPartnerLocation: Données localisation manquantes")
                return@withContext null
            }
            
            val location = UserLocation(
                latitude = locationData["latitude"] as? Double ?: 0.0,
                longitude = locationData["longitude"] as? Double ?: 0.0,
                address = locationData["address"] as? String,
                city = locationData["city"] as? String,
                country = locationData["country"] as? String,
                lastUpdated = System.currentTimeMillis() // Utiliser timestamp actuelle
            )
            
            Log.d(TAG, "✅ getPartnerLocation: Localisation partenaire récupérée")
            
            location
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ fetchPartnerLocation: Erreur Cloud Function", e)
            null
        }
    }
    
    
    
    /**
     * 🔄 Récupérer données partenaire complètes pour widgets
     * Équivalent iOS getPartnerDataForWidget()
     */
    private suspend fun getPartnerDataForWidget(partnerId: String): PartnerWidgetData? = withContext(Dispatchers.IO) {
        return@withContext try {
            // Récupérer infos et localisation en parallèle
            val partnerInfo = repositoryScope.async { fetchPartnerInfo(partnerId) }
            val partnerLocation = repositoryScope.async { fetchPartnerLocation(partnerId) }
            
            val info = partnerInfo.await()
            val location = partnerLocation.await()
            
            if (info != null) {
                PartnerWidgetData(
                    name = info.name,
                    location = location,
                    profileImageUrl = info.profileImageURL,
                    isSubscribed = info.isSubscribed
                )
            } else {
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ getPartnerDataForWidget: Erreur récupération données partenaire", e)
            null
        }
    }
}

// ==============================================
// 🎯 DATA CLASSES POUR DONNÉES PARTENAIRE
// ==============================================

/**
 * 👥 Données partenaire depuis Firebase
 */
data class PartnerInfo(
    val name: String,
    val isSubscribed: Boolean,
    val subscriptionType: String?,
    val profileImageURL: String?
)

/**
 * 🎯 Données partenaire pour widgets
 */
data class PartnerWidgetData(
    val name: String,
    val location: UserLocation?,
    val profileImageUrl: String?,
    val isSubscribed: Boolean
)
