package com.love2loveapp.core.services.managers

import android.content.Context
import android.util.Log
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.services.system.*
import com.love2loveapp.core.services.firebase.FirebaseUserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ⚙️ SystemServiceManager - Gestionnaire Centralisé des Services Système
 * 
 * Responsabilités :
 * - Coordination de tous les services système (Analytics, Performance, FCM, etc.)
 * - États réactifs pour monitoring et notifications
 * - Gestion centralisée des permissions et configurations
 * - Audit et logging centralisé
 * 
 * Architecture : Service Manager + System Monitoring + Reactive Streams
 */
class SystemServiceManager(
    private val context: Context,
    private val firebaseUserService: FirebaseUserService
) {
    
    companion object {
        private const val TAG = "SystemServiceManager"
    }
    
    // === Coroutine Scope ===
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // === Services Système ===
    private val analyticsService = AnalyticsService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val performanceMonitor = PerformanceMonitor.getInstance()
    
    private val fcmService = FCMService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val auditService = AuditService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val localizationService = LocalizationService.getInstance(context)
    
    private val reviewRequestService = ReviewRequestService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val imageCacheService = ImageCacheService.getInstance(context)
    
    // === États Réactifs - Analytics ===
    private val _analyticsEnabled = MutableStateFlow(true)
    val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()
    
    private val _performanceMetrics = MutableStateFlow<Result<Map<String, Any>>>(Result.Loading())
    val performanceMetrics: StateFlow<Result<Map<String, Any>>> = _performanceMetrics.asStateFlow()
    
    // === États Réactifs - Notifications ===
    private val _fcmTokenResult = MutableStateFlow<Result<String?>>(Result.Loading())
    val fcmTokenResult: StateFlow<Result<String?>> = _fcmTokenResult.asStateFlow()
    
    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()
    
    // === États Réactifs - Système ===
    private val _currentLanguage = MutableStateFlow("fr")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    
    private val _cacheSize = MutableStateFlow<Result<Long>>(Result.Loading())
    val cacheSize: StateFlow<Result<Long>> = _cacheSize.asStateFlow()
    
    private val _systemHealth = MutableStateFlow<SystemHealthStatus>(SystemHealthStatus.UNKNOWN)
    val systemHealth: StateFlow<SystemHealthStatus> = _systemHealth.asStateFlow()
    
    // === États Généraux ===
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        Log.d(TAG, "⚙️ Initialisation SystemServiceManager")
        initializeSystemStreams()
    }
    
    // === Initialisation ===
    
    /**
     * Initialiser les flux réactifs des services système
     */
    private fun initializeSystemStreams() {
        Log.d(TAG, "🌊 Configuration flux système réactifs")
        
        // Observer analytics et performance
        observeAnalyticsAndPerformance()
        
        // Observer notifications FCM
        observeNotifications()
        
        // Observer état système
        observeSystemHealth()
    }
    
    /**
     * Observer analytics et performance
     */
    private fun observeAnalyticsAndPerformance() {
        // Analytics
        analyticsService.isEnabled
            .onEach { enabled ->
                Log.d(TAG, "📊 Analytics enabled: $enabled")
                _analyticsEnabled.value = enabled
            }
            .launchIn(scope)
        
        // Performance metrics
        performanceMonitor.performanceMetrics
            .onEach { metricsResult ->
                Log.d(TAG, "⚡ Performance metrics update: ${metricsResult.javaClass.simpleName}")
                _performanceMetrics.value = metricsResult
            }
            .launchIn(scope)
    }
    
    /**
     * Observer notifications FCM
     */
    private fun observeNotifications() {
        // FCM Token
        fcmService.fcmToken
            .onEach { tokenResult ->
                Log.d(TAG, "🔔 FCM token update: ${tokenResult.javaClass.simpleName}")
                _fcmTokenResult.value = tokenResult
            }
            .launchIn(scope)
        
        // Notification permissions
        fcmService.notificationPermissionGranted
            .onEach { granted ->
                Log.d(TAG, "🔔 Notifications enabled: $granted")
                _notificationsEnabled.value = granted
            }
            .launchIn(scope)
    }
    
    /**
     * Observer état système
     */
    private fun observeSystemHealth() {
        // Combiner métriques pour santé système
        combine(
            _performanceMetrics,
            _fcmTokenResult,
            _cacheSize
        ) { performance, fcmToken, cache ->
            calculateSystemHealth(performance, fcmToken, cache)
        }
        .onEach { health ->
            Log.d(TAG, "🏥 System health: ${health.name}")
            _systemHealth.value = health
        }
        .launchIn(scope)
        
        // Observer langue
        localizationService.currentLanguage
            .onEach { language ->
                Log.d(TAG, "🌐 Language: $language")
                _currentLanguage.value = language
            }
            .launchIn(scope)
    }
    
    /**
     * Calculer santé système
     */
    private fun calculateSystemHealth(
        performance: Result<Map<String, Any>>,
        fcmToken: Result<String?>,
        cache: Result<Long>
    ): SystemHealthStatus {
        val issues = mutableListOf<String>()
        
        // Vérifier performance
        if (performance is Result.Error) {
            issues.add("Performance monitoring failed")
        }
        
        // Vérifier FCM
        if (fcmToken is Result.Error) {
            issues.add("FCM token unavailable")
        }
        
        // Vérifier cache
        if (cache is Result.Error) {
            issues.add("Cache issues detected")
        }
        
        return when {
            issues.isEmpty() -> SystemHealthStatus.HEALTHY
            issues.size <= 1 -> SystemHealthStatus.WARNING
            else -> SystemHealthStatus.CRITICAL
        }
    }
    
    // === Actions Analytics ===
    
    /**
     * Activer/désactiver analytics
     */
    fun setAnalyticsEnabled(enabled: Boolean) {
        Log.d(TAG, "📊 Set analytics enabled: $enabled")
        analyticsService.setEnabled(enabled)
    }
    
    /**
     * Logger événement
     */
    fun logEvent(eventName: String, parameters: Map<String, Any> = emptyMap()) {
        Log.d(TAG, "📊 Log event: $eventName")
        analyticsService.logEvent(eventName, parameters)
    }
    
    /**
     * Logger écran
     */
    fun logScreen(screenName: String) {
        Log.d(TAG, "📊 Log screen: $screenName")
        analyticsService.logScreen(screenName)
    }
    
    // === Actions Performance ===
    
    /**
     * Démarrer monitoring performance
     */
    fun startPerformanceMonitoring() {
        Log.d(TAG, "⚡ Démarrage monitoring performance")
        performanceMonitor.startMonitoring()
    }
    
    /**
     * Arrêter monitoring performance
     */
    fun stopPerformanceMonitoring() {
        Log.d(TAG, "⚡ Arrêt monitoring performance")
        performanceMonitor.stopMonitoring()
    }
    
    /**
     * Logger performance métrique
     */
    fun logPerformanceMetric(metricName: String, value: Double) {
        Log.d(TAG, "⚡ Log performance: $metricName = $value")
        performanceMonitor.logMetric(metricName, value)
    }
    
    // === Actions Notifications ===
    
    /**
     * Demander permission notifications
     */
    suspend fun requestNotificationPermission(): Result<Boolean> {
        Log.d(TAG, "🔔 Demande permission notifications")
        return fcmService.requestNotificationPermission()
    }
    
    /**
     * Actualiser token FCM
     */
    suspend fun refreshFCMToken(): Result<String?> {
        Log.d(TAG, "🔔 Actualisation token FCM")
        return fcmService.refreshToken()
    }
    
    /**
     * Envoyer notification test
     */
    suspend fun sendTestNotification(): Result<Unit> {
        Log.d(TAG, "🔔 Envoi notification test")
        return fcmService.sendTestNotification()
    }
    
    // === Actions Système ===
    
    /**
     * Changer langue
     */
    fun setLanguage(languageCode: String) {
        Log.d(TAG, "🌐 Changement langue: $languageCode")
        localizationService.setLanguage(languageCode)
    }
    
    /**
     * Calculer taille cache
     */
    suspend fun calculateCacheSize(): Result<Long> {
        Log.d(TAG, "💾 Calcul taille cache")
        _isLoading.value = true
        
        return try {
            val size = imageCacheService.getCacheSize()
            val result = Result.Success(size)
            _cacheSize.value = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur calcul cache", e)
            val error = Result.Error<Long>(e)
            _cacheSize.value = error
            error
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Vider cache
     */
    suspend fun clearCache(): Result<Unit> {
        Log.d(TAG, "🗑️ Vidage cache système")
        return try {
            imageCacheService.clearCache()
            
            // Recalculer taille
            calculateCacheSize()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vidage cache", e)
            Result.Error(e)
        }
    }
    
    /**
     * Demander review
     */
    suspend fun requestReview(): Result<Unit> {
        Log.d(TAG, "⭐ Demande review")
        return reviewRequestService.requestReview()
    }
    
    /**
     * Logger audit event
     */
    suspend fun logAuditEvent(eventType: String, details: Map<String, Any>) {
        Log.d(TAG, "🔍 Audit event: $eventType")
        auditService.logEvent(eventType, details)
    }
    
    // === Actions Globales ===
    
    /**
     * Effectuer diagnostic système complet
     */
    suspend fun performSystemDiagnostic(): Result<Map<String, Any>> {
        Log.d(TAG, "🔧 Diagnostic système complet")
        _isLoading.value = true
        
        return try {
            val diagnostics = mutableMapOf<String, Any>()
            
            // Performance
            val performanceResult = performanceMonitor.getCurrentMetrics()
            diagnostics["performance"] = performanceResult
            
            // FCM
            val fcmResult = fcmService.getTokenStatus()
            diagnostics["fcm"] = fcmResult
            
            // Cache
            val cacheResult = calculateCacheSize()
            diagnostics["cache"] = cacheResult
            
            // Analytics
            diagnostics["analytics"] = _analyticsEnabled.value
            
            // Langue
            diagnostics["language"] = _currentLanguage.value
            
            // Santé générale
            diagnostics["systemHealth"] = _systemHealth.value.name
            
            Result.Success(diagnostics)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur diagnostic système", e)
            Result.Error(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    // === Getters Services ===
    
    /**
     * Accès aux services individuels si besoin
     */
    fun getAnalyticsService() = analyticsService
    fun getPerformanceMonitor() = performanceMonitor
    fun getFCMService() = fcmService
    fun getAuditService() = auditService
    fun getLocalizationService() = localizationService
    fun getReviewRequestService() = reviewRequestService
    fun getImageCacheService() = imageCacheService
    
    // === Debug ===
    
    /**
     * État de debug du SystemServiceManager
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "analyticsEnabled" to _analyticsEnabled.value,
            "notificationsEnabled" to _notificationsEnabled.value,
            "currentLanguage" to _currentLanguage.value,
            "systemHealth" to _systemHealth.value.name,
            "fcmTokenAvailable" to (_fcmTokenResult.value is Result.Success),
            "performanceMonitoring" to (_performanceMetrics.value is Result.Success),
            "isLoading" to _isLoading.value
        )
    }
}

/**
 * États de santé système
 */
enum class SystemHealthStatus {
    UNKNOWN,
    HEALTHY,
    WARNING,
    CRITICAL
}
