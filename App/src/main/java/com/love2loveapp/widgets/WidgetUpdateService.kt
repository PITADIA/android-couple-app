package com.love2loveapp.widgets

import android.content.Context
import android.util.Log
import androidx.work.*
import com.love2loveapp.services.widgets.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * ⏰ WidgetUpdateService - Service mise à jour périodique widgets
 * 
 * Équivalent Android des Timeline iOS WidgetKit :
 * - WorkManager pour mise à jour périodique
 * - Gestion intelligente fréquence updates
 * - Économie batterie avec contraintes
 * - Synchronisation données app
 */
class WidgetUpdateService : CoroutineWorker {
    
    constructor(context: Context, params: WorkerParameters) : super(context, params)
    
    companion object {
        private const val TAG = "WidgetUpdateService"
        private const val WORK_NAME = "widget_periodic_update"
        
        // Fréquences mise à jour (minutes)
        private const val UPDATE_INTERVAL_MINUTES = 30L      // Mise à jour normale
        private const val UPDATE_INTERVAL_FAST_MINUTES = 15L // Mise à jour rapide si données importantes
        
        /**
         * 🚀 Démarrer mises à jour périodiques
         * 
         * Appelé quand premier widget est ajouté
         */
        fun startPeriodicUpdates(context: Context) {
            Log.d(TAG, "🚀 Démarrage mises à jour périodiques widgets")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // WiFi ou données
                .setRequiresBatteryNotLow(true)                 // Batterie pas faible
                .build()
            
            val periodicWorkRequest = PeriodicWorkRequestBuilder<WidgetUpdateService>(
                UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("widget_update")
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,  // Garder travail existant
                    periodicWorkRequest
                )
            
            Log.d(TAG, "✅ Travail périodique widget configuré (${UPDATE_INTERVAL_MINUTES}min)")
        }
        
        /**
         * ⏹️ Arrêter mises à jour périodiques
         * 
         * Appelé quand dernier widget est supprimé
         */
        fun stopPeriodicUpdates(context: Context) {
            Log.d(TAG, "⏹️ Arrêt mises à jour périodiques widgets")
            
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
            
            Log.d(TAG, "✅ Travail périodique widget annulé")
        }
        
        /**
         * 🔄 Forcer mise à jour immédiate
         * 
         * Utilitaire pour mise à jour on-demand
         */
        fun forceUpdate(context: Context) {
            Log.d(TAG, "🔄 Force mise à jour immédiate widgets")
            
            val immediateWorkRequest = OneTimeWorkRequestBuilder<WidgetUpdateService>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("widget_force_update")
                .build()
            
            WorkManager.getInstance(context)
                .enqueue(immediateWorkRequest)
        }
    }
    
    /**
     * 🔄 Exécution travail mise à jour
     * 
     * Méthode principale appelée périodiquement
     */
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "🔄 Début travail mise à jour widgets")
            
            // 📊 Vérifier si mise à jour nécessaire
            val widgetRepository = WidgetRepository.getInstance(applicationContext)
            val currentData = widgetRepository.getCurrentWidgetData()
            
            Log.d(TAG, "📊 Données actuelles: age=${getDataAgeMinutes(currentData)}min")
            
            // Mise à jour seulement si données périmées
            if (currentData.needsUpdate(maxAgeMinutes = UPDATE_INTERVAL_MINUTES.toInt())) {
                Log.d(TAG, "🔄 Données périmées, rafraîchissement...")
                
                val refreshResult = widgetRepository.refreshWidgetData(forceUpdate = true)
                
                if (refreshResult.isSuccess) {
                    Log.d(TAG, "✅ Rafraîchissement widgets réussi")
                    
                    // Notifier widgets de la mise à jour
                    HomeScreenWidgetProvider.updateAllWidgets(applicationContext)
                    DistanceWidgetProvider.updateAllWidgets(applicationContext)
                    
                    // Décider fréquence prochaine mise à jour
                    scheduleNextUpdate(applicationContext, currentData)
                    
                    Result.success()
                } else {
                    Log.w(TAG, "⚠️ Échec rafraîchissement, retry plus tard")
                    Result.retry()
                }
            } else {
                Log.d(TAG, "✅ Données encore fraîches, pas de mise à jour nécessaire")
                Result.success()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur travail mise à jour widgets: ${e.message}", e)
            Result.failure()
        }
    }
    
    /**
     * ⏰ Planifier prochaine mise à jour selon contexte
     */
    private fun scheduleNextUpdate(context: Context, data: com.love2loveapp.models.widgets.WidgetData) {
        
        // Déterminer fréquence selon importance données
        val intervalMinutes = when {
            // Plus fréquent si anniversaire proche
            data.relationshipStats?.daysUntilNextAnniversary ?: Int.MAX_VALUE <= 1 -> UPDATE_INTERVAL_FAST_MINUTES
            
            // Plus fréquent si partenaire très proche géographiquement
            data.distanceInfo?.isVeryClose == true -> UPDATE_INTERVAL_FAST_MINUTES
            
            // Fréquence normale sinon
            else -> UPDATE_INTERVAL_MINUTES
        }
        
        if (intervalMinutes != UPDATE_INTERVAL_MINUTES) {
            Log.d(TAG, "⏰ Fréquence adaptée: ${intervalMinutes}min (contexte important)")
            
            // Reprogrammer avec nouvelle fréquence
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val newPeriodicWork = PeriodicWorkRequestBuilder<WidgetUpdateService>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("widget_update_adaptive")
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "${WORK_NAME}_adaptive",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    newPeriodicWork
                )
        }
    }
    
    /**
     * 📊 Calculer âge données en minutes
     */
    private fun getDataAgeMinutes(data: com.love2loveapp.models.widgets.WidgetData): Long {
        val now = System.currentTimeMillis()
        val dataTime = data.lastUpdated.time
        return (now - dataTime) / (1000 * 60)
    }
    
    /**
     * 🔍 Détecter si données importantes ont changé
     * 
     * Optimisation pour éviter mises à jour inutiles
     */
    private fun hasSignificantDataChanges(
        oldData: com.love2loveapp.models.widgets.WidgetData,
        newData: com.love2loveapp.models.widgets.WidgetData
    ): Boolean {
        return oldData.relationshipStats?.daysTotal != newData.relationshipStats?.daysTotal ||
                oldData.distanceInfo?.formattedDistance != newData.distanceInfo?.formattedDistance ||
                oldData.hasSubscription != newData.hasSubscription ||
                oldData.hasPartner != newData.hasPartner
    }
    
    /**
     * 📈 Analytics travail widget
     */
    private fun logWorkAnalytics(success: Boolean, duration: Long) {
        Log.d(TAG, "📈 Widget work: success=$success, duration=${duration}ms")
        
        // TODO: Envoyer à Firebase Analytics si nécessaire
        // val analytics = FirebaseAnalytics.getInstance(applicationContext)
        // analytics.logEvent("widget_update_work", Bundle().apply {
        //     putBoolean("success", success)
        //     putLong("duration_ms", duration)
        // })
    }
}
