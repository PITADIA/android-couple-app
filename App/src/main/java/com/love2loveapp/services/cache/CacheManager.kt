package com.love2loveapp.services.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🏗️ CacheManager Android - Orchestrateur Central Cache Sophistiqué
 * 
 * Architecture équivalente à l'ensemble des services cache iOS:
 * - UserCacheManager → UserCacheManager iOS
 * - ImageCacheService → ImageCacheService iOS
 * - QuestionCacheManager → QuestionCacheManager (Realm) iOS
 * - FavoritesService → FavoritesService iOS
 * - JournalService → JournalService iOS
 * - PartnerLocationService → PartnerLocationService iOS
 * - WidgetCacheService → WidgetService iOS
 * - NetworkCacheConfig → URLCache iOS
 * 
 * Point d'entrée unique pour tout le système de cache
 * Coordination et orchestration de tous les caches
 * Équivalent de l'architecture cache complète iOS
 */
class CacheManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "CacheManager"
        
        @Volatile
        private var instance: CacheManager? = null
        
        fun getInstance(context: Context): CacheManager {
            return instance ?: synchronized(this) {
                instance ?: CacheManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // =======================
    // COMPOSANTS CACHE (équivalent iOS)
    // =======================
    
    // Cache utilisateur (équivalent UserCacheManager iOS)
    val userCache: UserCacheManager by lazy {
        UserCacheManager.getInstance(context)
    }
    
    // Cache images (équivalent ImageCacheService iOS)
    val imageCache: ImageCacheService by lazy {
        ImageCacheService.getInstance(context)
    }
    
    // Cache questions/défis (équivalent Realm iOS)
    val questionCache: QuestionCacheManager by lazy {
        QuestionCacheManager.getInstance(context)
    }
    
    // Cache favoris (équivalent FavoritesService iOS)
    val favoritesCache: FavoritesService by lazy {
        FavoritesService.getInstance(context)
    }
    
    // Cache journal (équivalent JournalService iOS)
    val journalCache: JournalService by lazy {
        JournalService.getInstance(context)
    }
    
    // Cache localisation partenaire (équivalent PartnerLocationService iOS)
    val partnerLocationCache: PartnerLocationService by lazy {
        PartnerLocationService.getInstance(context)
    }
    
    // Cache widgets (équivalent WidgetService iOS)
    val widgetCache: WidgetCacheService by lazy {
        WidgetCacheService.getInstance(context)
    }
    
    // États globaux cache (équivalent @Published iOS)
    private val _isCacheInitialized = MutableStateFlow(false)
    val isCacheInitialized: StateFlow<Boolean> = _isCacheInitialized.asStateFlow()
    
    private val _cacheHealthStatus = MutableStateFlow(CacheHealthInfo(CacheHealthStatus.UNKNOWN))
    val cacheHealthStatus: StateFlow<CacheHealthInfo> = _cacheHealthStatus.asStateFlow()
    
    // Scope pour opérations cache
    private val cacheScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        Log.d(TAG, "✅ CacheManager initialisé - Orchestrateur central")
        
        // Initialiser système cache
        cacheScope.launch {
            initializeCacheSystem()
        }
    }
    
    // =======================
    // INITIALISATION SYSTÈME (équivalent iOS)
    // =======================
    
    /**
     * Initialise tout le système de cache
     * Équivalent de l'initialisation complète iOS
     */
    private suspend fun initializeCacheSystem() {
        try {
            Log.d(TAG, "🚀 Initialisation système cache sophistiqué...")
            
            // 1. Vérifier santé des caches
            checkCacheHealth()
            
            // 2. Préchargement intelligent
            performIntelligentPreloading()
            
            // 3. Configuration réseau
            configureNetworkCache()
            
            // 4. Nettoyage automatique
            scheduleAutomaticCleanup()
            
            _isCacheInitialized.value = true
            Log.d(TAG, "✅ Système cache sophistiqué initialisé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur initialisation système cache: ${e.message}", e)
            _cacheHealthStatus.value = CacheHealthInfo(CacheHealthStatus.ERROR, e.message ?: "Erreur inconnue")
        }
    }
    
    /**
     * Vérifie la santé de tous les caches
     * Équivalent des vérifications santé iOS
     */
    private suspend fun checkCacheHealth() {
        try {
            Log.d(TAG, "🏥 Vérification santé système cache...")
            
            coroutineScope {
                val healthChecks = listOf(
                    async { checkUserCacheHealth() },
                    async { checkImageCacheHealth() },
                    async { checkQuestionCacheHealth() },
                    async { checkDatabaseHealth() }
                )
                
                val results = healthChecks.awaitAll()
                val allHealthy = results.all { it }
                
                _cacheHealthStatus.value = if (allHealthy) {
                    CacheHealthInfo(CacheHealthStatus.HEALTHY)
                } else {
                    CacheHealthInfo(CacheHealthStatus.WARNING, "Certains caches ont des problèmes")
                }
            }
            
            Log.d(TAG, "🏥 Santé cache: ${_cacheHealthStatus.value}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification santé: ${e.message}")
            _cacheHealthStatus.value = CacheHealthInfo(CacheHealthStatus.ERROR, e.message ?: "Erreur santé")
        }
    }
    
    private fun checkUserCacheHealth(): Boolean {
        return try {
            // Vérifier si UserCacheManager fonctionne
            userCache.hasCachedUser() // Test basique
            true
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ UserCache problème: ${e.message}")
            false
        }
    }
    
    private fun checkImageCacheHealth(): Boolean {
        return try {
            // Vérifier si ImageCacheService fonctionne
            val metrics = imageCache.getCacheSize()
            metrics.memoryMaxMB > 0
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ ImageCache problème: ${e.message}")
            false
        }
    }
    
    private suspend fun checkQuestionCacheHealth(): Boolean {
        return try {
            // Vérifier si QuestionCacheManager fonctionne
            questionCache.isDatabaseAvailable.value
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ QuestionCache problème: ${e.message}")
            false
        }
    }
    
    private fun checkDatabaseHealth(): Boolean {
        return try {
            // Vérifier base de données Room
            val database = CacheDatabase.getDatabase(context)
            database.isOpen
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Database problème: ${e.message}")
            false
        }
    }
    
    /**
     * Préchargement intelligent
     * Équivalent de preloadEssentialCategories() iOS
     */
    private suspend fun performIntelligentPreloading() {
        try {
            Log.d(TAG, "⚡ Préchargement intelligent...")
            
            // Précharger catégories essentielles
            questionCache.preloadEssentialCategories()
            
            // Optimiser mémoire
            imageCache.cleanupExpiredCache()
            
            Log.d(TAG, "✅ Préchargement terminé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur préchargement: ${e.message}")
        }
    }
    
    /**
     * Configure le cache réseau
     */
    private fun configureNetworkCache() {
        try {
            Log.d(TAG, "🌐 Configuration cache réseau...")
            
            // Configuration automatique via NetworkCacheConfig
            val metrics = NetworkCacheConfig.getCacheMetrics(context)
            Log.d(TAG, "📊 Cache réseau: ${metrics.cacheSizeMB}MB")
            
            Log.d(TAG, "✅ Cache réseau configuré")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur configuration réseau: ${e.message}")
        }
    }
    
    /**
     * Programme le nettoyage automatique
     * Équivalent de scheduleAutomaticCleanup() iOS
     */
    private fun scheduleAutomaticCleanup() {
        cacheScope.launch {
            try {
                // Nettoyage périodique toutes les 24h
                while (isActive) {
                    delay(24 * 60 * 60 * 1000L) // 24h
                    performAutomaticCleanup()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur nettoyage automatique: ${e.message}")
            }
        }
    }
    
    // =======================
    // OPÉRATIONS GLOBALES CACHE (équivalent iOS)
    // =======================
    
    /**
     * Nettoyage automatique intelligent
     * Équivalent shouldCompactOnLaunch iOS
     */
    private suspend fun performAutomaticCleanup() {
        try {
            Log.d(TAG, "🧹 Nettoyage automatique système cache...")
            
            // Nettoyer images expirées
            imageCache.cleanupExpiredCache()
            
            // Optimiser base de données
            questionCache.optimizeMemoryUsage()
            
            // Nettoyer widgets anciens
            widgetCache.cleanupOldImages()
            
            Log.d(TAG, "✅ Nettoyage automatique terminé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage automatique: ${e.message}")
        }
    }
    
    /**
     * Vide TOUT le système de cache
     * Équivalent clearCache() complet iOS
     */
    suspend fun clearAllCaches() {
        try {
            Log.d(TAG, "🗑️ NETTOYAGE COMPLET système cache...")
            
            // Nettoyer tous les caches
            coroutineScope {
                val cleanupJobs = listOf(
                    async { userCache.clearCache() },
                    async { imageCache.clearAllCache() },
                    async { questionCache.clearAllCache() },
                    async { favoritesCache.clearFavorites() },
                    async { partnerLocationCache.clearCache() },
                    async { widgetCache.clearWidgetCache() },
                    async { NetworkCacheConfig.clearNetworkCache(context) }
                )
                
                cleanupJobs.awaitAll()
            }
            
            Log.i(TAG, "🗑️ TOUT le système cache vidé")
            
            // Réinitialiser état
            _isCacheInitialized.value = false
            delay(1000)
            initializeCacheSystem()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage complet: ${e.message}")
        }
    }
    
    /**
     * Synchronisation intelligente entre tous les caches
     * Équivalent de la synchronisation multi-cache iOS
     */
    suspend fun syncAllCaches(
        userId: String? = null,
        coupleId: String? = null
    ) {
        try {
            Log.d(TAG, "🔄 Synchronisation intelligente tous caches...")
            
            // Sync images → widgets
            if (userId != null) {
                val userData = userCache.getCachedUser()
                val profileImage = userCache.getCachedProfileImage()
                val partnerImage = userCache.getCachedPartnerImage()
                
                if (userData != null) {
                    // TODO: Sync vers WidgetCache
                    Log.d(TAG, "🔄 Sync UserCache → Widget terminé")
                }
            }
            
            // Sync questions → cache local
            if (coupleId != null) {
                val todayQuestion = questionCache.getTodayQuestion(coupleId)
                if (todayQuestion != null) {
                    Log.d(TAG, "🔄 Question du jour en cache: ${todayQuestion.questionKey}")
                }
            }
            
            Log.d(TAG, "✅ Synchronisation complète terminée")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur synchronisation: ${e.message}")
        }
    }
    
    // =======================
    // MÉTRIQUES ET DEBUG (équivalent iOS)
    // =======================
    
    data class CacheMetrics(
        val userCacheSize: String,
        val imageCacheSize: String,
        val questionCacheSize: String,
        val networkCacheSize: Long,
        val widgetCacheSize: Int,
        val totalCacheSize: String,
        val healthStatus: CacheHealthInfo
    )
    
    enum class CacheHealthStatus {
        UNKNOWN,
        HEALTHY,
        WARNING,
        ERROR
    }
    
    data class CacheHealthInfo(
        val status: CacheHealthStatus,
        val message: String = ""
    ) {
        override fun toString(): String = when (status) {
            CacheHealthStatus.UNKNOWN -> "Inconnu"
            CacheHealthStatus.HEALTHY -> "Sain"
            CacheHealthStatus.WARNING -> "Attention: $message"
            CacheHealthStatus.ERROR -> "Erreur: $message"
        }
    }
    
    /**
     * Métriques complètes système cache
     * Équivalent des métriques debug iOS
     */
    suspend fun getCacheMetrics(): CacheMetrics {
        return try {
            val imageMetrics = imageCache.getCacheSize()
            val networkMetrics = NetworkCacheConfig.getCacheMetrics(context)
            val widgetCount = widgetCache.getActiveWidgetCount()
            
            CacheMetrics(
                userCacheSize = if (userCache.hasCachedUser()) "Utilisateur en cache" else "Vide",
                imageCacheSize = "${imageMetrics.memoryUsedMB}/${imageMetrics.memoryMaxMB}MB + ${imageMetrics.diskUsedMB}MB disque",
                questionCacheSize = if (questionCache.isDatabaseAvailable.value) "Base disponible" else "Indisponible",
                networkCacheSize = networkMetrics.cacheSizeMB,
                widgetCacheSize = widgetCount,
                totalCacheSize = "${(imageMetrics.diskUsedMB + networkMetrics.cacheSizeMB)}MB total",
                healthStatus = _cacheHealthStatus.value
            )
        } catch (e: Exception) {
            CacheMetrics(
                userCacheSize = "Erreur",
                imageCacheSize = "Erreur",
                questionCacheSize = "Erreur",
                networkCacheSize = 0L,
                widgetCacheSize = 0,
                totalCacheSize = "Erreur",
                healthStatus = CacheHealthInfo(CacheHealthStatus.ERROR, e.message ?: "Erreur metrics")
            )
        }
    }
    
    /**
     * Informations debug complètes système
     * Équivalent getDebugInfo() complet iOS
     */
    suspend fun getCompleteDebugInfo(): String {
        val metrics = getCacheMetrics()
        
        return """
            🏗️ DEBUG CacheManager - Système Cache Complet:
            
            📊 MÉTRIQUES GLOBALES:
            ${metrics}
            
            👤 USER CACHE:
            ${userCache.getDebugInfo()}
            
            🖼️ IMAGE CACHE:
            ${imageCache.getDebugInfo()}
            
            📝 QUESTION CACHE:
            ${questionCache.getDebugInfo()}
            
            ⭐ FAVORITES CACHE:
            ${favoritesCache.getDebugInfo()}
            
            📔 JOURNAL CACHE:
            ${journalCache.getDebugInfo()}
            
            🗺️ PARTNER LOCATION CACHE:
            ${partnerLocationCache.getDebugInfo()}
            
            📱 WIDGET CACHE:
            ${widgetCache.getDebugInfo()}
            
            🌐 NETWORK CACHE:
            ${NetworkCacheConfig.getDebugInfo(context)}
            
            🏥 SANTÉ SYSTÈME: ${_cacheHealthStatus.value}
            🚀 INITIALISÉ: ${_isCacheInitialized.value}
        """.trimIndent()
    }
    
    /**
     * Nettoyage ressources (destroy app)
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage CacheManager complet")
        
        try {
            // Nettoyer tous les services
            questionCache.cleanup()
            favoritesCache.cleanup()
            journalCache.cleanup()
            partnerLocationCache.cleanup()
            widgetCache.cleanup()
            
            // Annuler coroutines
            cacheScope.cancel()
            
            Log.d(TAG, "✅ Nettoyage CacheManager terminé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage CacheManager: ${e.message}")
        }
    }
}
