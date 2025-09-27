package com.love2loveapp.services.cache

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.love2loveapp.models.widgets.RelationshipStats
import com.love2loveapp.models.widgets.DistanceInfo
import com.love2loveapp.models.widgets.WidgetData
import com.love2loveapp.widgets.HomeScreenWidgetProvider
import com.love2loveapp.widgets.DistanceWidgetProvider
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * 📊 WidgetCacheService Android - Cache App Widgets Sophistiqué
 * 
 * Architecture équivalente iOS WidgetService:
 * - SharedPreferences → App Groups UserDefaults iOS
 * - Cache directory → App Groups container iOS
 * - Widget updates → WidgetKit Timeline iOS
 * - Image optimization → resizeImage() iOS
 * - Data synchronization → saveWidgetData() iOS
 * - Automatic refresh → Widget refresh iOS
 * - Équivalent complet du WidgetService iOS avec App Groups
 */
class WidgetCacheService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "WidgetCacheService"
        private const val PREFS_NAME = "widget_shared_data"
        private const val WIDGET_CACHE_DIR = "WidgetCache"
        
        // Clés données widget (équivalent App Groups keys iOS)
        private const val KEY_DAYS_TOTAL = "widget_days_total"
        private const val KEY_START_DATE = "widget_start_date" 
        private const val KEY_DISTANCE = "widget_distance"
        private const val KEY_DISTANCE_UNIT = "widget_distance_unit"
        private const val KEY_USER_IMAGE_FILE = "widget_user_image_file"
        private const val KEY_PARTNER_IMAGE_FILE = "widget_partner_image_file"
        private const val KEY_USER_NAME = "widget_user_name"
        private const val KEY_PARTNER_NAME = "widget_partner_name"
        private const val KEY_LAST_UPDATE = "widget_last_update"
        
        // Configuration images (équivalent iOS widget sizing)
        private const val WIDGET_IMAGE_SIZE = 150
        private const val IMAGE_COMPRESSION_QUALITY = 80
        
        @Volatile
        private var instance: WidgetCacheService? = null
        
        fun getInstance(context: Context): WidgetCacheService {
            return instance ?: synchronized(this) {
                instance ?: WidgetCacheService(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // SharedPreferences pour partage avec widgets (équivalent App Groups iOS)
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Répertoire cache images widgets (équivalent App Groups container iOS)
    private val widgetCacheDir: File = File(context.cacheDir, WIDGET_CACHE_DIR)
    
    // Scope pour opérations asynchrones
    private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        Log.d(TAG, "✅ WidgetCacheService initialisé")
        
        // Créer répertoire cache si nécessaire
        if (!widgetCacheDir.exists()) {
            widgetCacheDir.mkdirs()
            Log.d(TAG, "📁 Répertoire cache widgets créé: ${widgetCacheDir.path}")
        }
    }
    
    // =======================
    // DONNÉES WIDGET (équivalent iOS saveWidgetData)
    // =======================
    
    data class WidgetData(
        val daysTotal: Int,
        val startDate: String?,
        val distance: String?,
        val distanceUnit: String?,
        val userImageFile: String?,
        val partnerImageFile: String?,
        val userName: String?,
        val partnerName: String?,
        val lastUpdate: Long = System.currentTimeMillis()
    ) {
        fun toJson(): String = Gson().toJson(this)
        
        companion object {
            fun fromJson(json: String): WidgetData? {
                return try {
                    Gson().fromJson(json, WidgetData::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur parsing WidgetData: ${e.message}")
                    null
                }
            }
        }
    }
    
    /**
     * Sauvegarde données complètes widget
     * Équivalent de saveWidgetData() iOS
     */
    suspend fun saveWidgetData(
        relationshipStats: RelationshipStats?,
        distanceInfo: DistanceInfo?,
        userImageBitmap: Bitmap? = null,
        partnerImageBitmap: Bitmap? = null,
        userName: String? = null,
        partnerName: String? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "💾 Sauvegarde données widget...")
                
                var userImageFileName: String? = null
                var partnerImageFileName: String? = null
                
                // Traiter image utilisateur
                if (userImageBitmap != null) {
                    userImageFileName = "user_profile_widget.jpg"
                    saveImageForWidget(userImageBitmap, userImageFileName)
                    Log.d(TAG, "📷 Image utilisateur sauvée: $userImageFileName")
                }
                
                // Traiter image partenaire
                if (partnerImageBitmap != null) {
                    partnerImageFileName = "partner_profile_widget.jpg"
                    saveImageForWidget(partnerImageBitmap, partnerImageFileName)
                    Log.d(TAG, "📷 Image partenaire sauvée: $partnerImageFileName")
                }
                
                // Créer objet données widget
                val widgetData = WidgetData(
                    userName = userName,
                    userProfileImageUrl = userImageFileName,
                    partnerName = partnerName,
                    partnerProfileImageUrl = partnerImageFileName,
                    hasSubscription = true, // TODO: Récupérer vraie valeur
                    hasPartner = partnerName != null,
                    relationshipStats = relationshipStats,
                    distanceInfo = distanceInfo,
                    lastUpdated = Date()
                )
                
                // Sauvegarder dans SharedPreferences
                sharedPrefs.edit()
                    .putInt(KEY_DAYS_TOTAL, relationshipStats?.daysTotal ?: 0)
                    .putString(KEY_START_DATE, relationshipStats?.formattedDuration)
                    .putString(KEY_DISTANCE, distanceInfo?.formattedDistance)
                    .putString(KEY_DISTANCE_UNIT, distanceInfo?.distanceUnit?.name)
                    .putString(KEY_USER_IMAGE_FILE, userImageFileName)
                    .putString(KEY_PARTNER_IMAGE_FILE, partnerImageFileName)
                    .putString(KEY_USER_NAME, userName)
                    .putString(KEY_PARTNER_NAME, partnerName)
                    .putLong(KEY_LAST_UPDATE, widgetData.lastUpdated.time)
                    .apply()
                
                Log.d(TAG, "✅ Données widget sauvegardées")
                
                // Notifier widgets de la mise à jour (équivalent WidgetKit refresh iOS)
                updateAllWidgets()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur sauvegarde données widget: ${e.message}", e)
            }
        }
    }
    
    /**
     * Récupère données widget depuis cache
     * Équivalent de getWidgetData() iOS 
     */
    fun getWidgetData(): WidgetData? {
        return try {
            WidgetData(
                daysTotal = sharedPrefs.getInt(KEY_DAYS_TOTAL, 0),
                startDate = sharedPrefs.getString(KEY_START_DATE, null),
                distance = sharedPrefs.getString(KEY_DISTANCE, null),
                distanceUnit = sharedPrefs.getString(KEY_DISTANCE_UNIT, null),
                userImageFile = sharedPrefs.getString(KEY_USER_IMAGE_FILE, null),
                partnerImageFile = sharedPrefs.getString(KEY_PARTNER_IMAGE_FILE, null),
                userName = sharedPrefs.getString(KEY_USER_NAME, null),
                partnerName = sharedPrefs.getString(KEY_PARTNER_NAME, null),
                lastUpdate = sharedPrefs.getLong(KEY_LAST_UPDATE, 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur récupération données widget: ${e.message}")
            null
        }
    }
    
    // =======================
    // GESTION IMAGES WIDGETS (équivalent iOS downloadAndCacheImage)
    // =======================
    
    /**
     * Sauvegarde image redimensionnée pour widget
     * Équivalent de downloadAndCacheImage() + resizeImage() iOS
     */
    private fun saveImageForWidget(originalBitmap: Bitmap, fileName: String) {
        try {
            // Redimensionner image (équivalent resizeImage iOS)
            val resizedBitmap = resizeImageForWidget(originalBitmap, WIDGET_IMAGE_SIZE)
            
            // Sauvegarder dans cache directory
            val file = File(widgetCacheDir, fileName)
            val outputStream = FileOutputStream(file)
            
            resizedBitmap.compress(
                Bitmap.CompressFormat.JPEG, 
                IMAGE_COMPRESSION_QUALITY, 
                outputStream
            )
            outputStream.close()
            
            Log.d(TAG, "📷 Image widget sauvée: $fileName (${file.length()} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde image widget: ${e.message}")
        }
    }
    
    /**
     * Redimensionne image pour widget (équivalent resizeImage iOS)
     */
    private fun resizeImageForWidget(original: Bitmap, targetSize: Int): Bitmap {
        return try {
            val width = original.width
            val height = original.height
            
            val scaleFactor = minOf(
                targetSize.toFloat() / width,
                targetSize.toFloat() / height
            )
            
            val newWidth = (width * scaleFactor).toInt()
            val newHeight = (height * scaleFactor).toInt()
            
            Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur redimensionnement: ${e.message}")
            original
        }
    }
    
    /**
     * Charge image depuis cache widget
     * Équivalent de loadImageFromWidget() iOS
     */
    fun loadImageFromWidget(fileName: String): Bitmap? {
        return try {
            val file = File(widgetCacheDir, fileName)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement image widget: ${e.message}")
            null
        }
    }
    
    // =======================
    // SYNCHRONISATION DONNÉES (équivalent iOS)
    // =======================
    
    /**
     * Synchronise données depuis ImageCacheService  
     * Équivalent de la réutilisation cache ImageCacheService iOS
     */
    suspend fun syncFromImageCache(
        relationshipStats: RelationshipStats?,
        distanceInfo: DistanceInfo?,
        userImageUrl: String?,
        partnerImageUrl: String?,
        userName: String?,
        partnerName: String?
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Synchronisation depuis ImageCache...")
                
                val imageCache = ImageCacheService.getInstance(context)
                
                var userBitmap: Bitmap? = null  
                var partnerBitmap: Bitmap? = null
                
                // Récupérer images depuis ImageCacheService
                if (userImageUrl != null) {
                    userBitmap = imageCache.getCachedImage(userImageUrl)
                    if (userBitmap == null) {
                        Log.d(TAG, "⚠️ Image utilisateur non trouvée en cache")
                    }
                }
                
                if (partnerImageUrl != null) {
                    partnerBitmap = imageCache.getCachedImage(partnerImageUrl)
                    if (partnerBitmap == null) {
                        Log.d(TAG, "⚠️ Image partenaire non trouvée en cache")
                    }
                }
                
                // Sauvegarder pour widgets
                saveWidgetData(
                    relationshipStats = relationshipStats,
                    distanceInfo = distanceInfo,
                    userImageBitmap = userBitmap,
                    partnerImageBitmap = partnerBitmap,
                    userName = userName,
                    partnerName = partnerName
                )
                
                Log.d(TAG, "✅ Synchronisation ImageCache → Widget terminée")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur synchronisation ImageCache: ${e.message}", e)
            }
        }
    }
    
    /**
     * Mise à jour périodique automatique
     * Équivalent de la logique de refresh automatique iOS
     */
    fun schedulePeriodicUpdate() {
        widgetScope.launch {
            try {
                // Logique de mise à jour périodique
                // TODO: Implémenter avec WorkManager pour persistence
                Log.d(TAG, "⏰ Mise à jour périodique widgets programmée")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur programmation mise à jour: ${e.message}")
            }
        }
    }
    
    // =======================
    // WIDGET MANAGEMENT (équivalent iOS WidgetKit)
    // =======================
    
    /**
     * Met à jour tous les widgets Love2Love
     * Équivalent de WidgetCenter.shared.reloadAllTimelines() iOS
     */
    private fun updateAllWidgets() {
        try {
            val intent = Intent(context, HomeScreenWidgetProvider::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, HomeScreenWidgetProvider::class.java)
            )
            
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            context.sendBroadcast(intent)
            
            Log.d(TAG, "📱 ${widgetIds.size} widgets mis à jour")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur mise à jour widgets: ${e.message}")
        }
    }
    
    /**
     * Vérifie si des widgets sont installés
     */
    fun hasActiveWidgets(): Boolean {
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, HomeScreenWidgetProvider::class.java)
            )
            widgetIds.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification widgets: ${e.message}")
            false
        }
    }
    
    /**
     * Compte le nombre de widgets actifs
     */
    fun getActiveWidgetCount(): Int {
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, HomeScreenWidgetProvider::class.java)
            )
            widgetIds.size
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur compte widgets: ${e.message}")
            0
        }
    }
    
    // =======================
    // NETTOYAGE ET DEBUG (équivalent iOS)
    // =======================
    
    /**
     * Vide le cache widgets
     * Équivalent de clearCache() iOS
     */
    fun clearWidgetCache() {
        try {
            Log.d(TAG, "🗑️ Nettoyage cache widgets...")
            
            // Vider SharedPreferences
            sharedPrefs.edit().clear().apply()
            
            // Supprimer images cache
            if (widgetCacheDir.exists()) {
                widgetCacheDir.listFiles()?.forEach { file ->
                    if (file.delete()) {
                        Log.d(TAG, "🗑️ Image supprimée: ${file.name}")
                    }
                }
            }
            
            // Mettre à jour widgets avec données vides
            updateAllWidgets()
            
            Log.d(TAG, "✅ Cache widgets nettoyé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage cache widgets: ${e.message}")
        }
    }
    
    /**
     * Nettoie les images anciennes non utilisées
     */
    fun cleanupOldImages(maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L) { // 7 jours
        widgetScope.launch {
            try {
                val cutoffTime = System.currentTimeMillis() - maxAgeMillis
                var deletedCount = 0
                
                widgetCacheDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
                
                if (deletedCount > 0) {
                    Log.d(TAG, "🧹 ${deletedCount} images anciennes supprimées")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur nettoyage images anciennes: ${e.message}")
            }
        }
    }
    
    /**
     * Informations de debug complètes
     */
    fun getDebugInfo(): String {
        val widgetData = getWidgetData()
        val activeWidgets = getActiveWidgetCount()
        val cacheSize = if (widgetCacheDir.exists()) {
            widgetCacheDir.listFiles()?.size ?: 0
        } else 0
        
        val lastUpdate = widgetData?.lastUpdate?.let { timestamp ->
            val age = (System.currentTimeMillis() - timestamp) / 1000
            "${age}s ago"
        } ?: "Jamais"
        
        return """
            📊 DEBUG WidgetCacheService:
            - Widgets actifs: $activeWidgets
            - Images en cache: $cacheSize
            - Dernière MAJ: $lastUpdate
            - Données widget:
              * Jours ensemble: ${widgetData?.daysTotal ?: "Non défini"}
              * Distance: ${widgetData?.distance ?: "Non définie"} ${widgetData?.distanceUnit ?: ""}
              * Utilisateur: ${widgetData?.userName ?: "Non défini"}
              * Partenaire: ${widgetData?.partnerName ?: "Non défini"}
            - Cache directory: ${widgetCacheDir.path}
            - SharedPrefs: $PREFS_NAME
        """.trimIndent()
    }
    
    /**
     * Nettoyage ressources (destroy app)
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage WidgetCacheService")
        widgetScope.cancel()
    }
}
