package com.love2loveapp.widget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.*

/**
 * Modèle de données pour les widgets Love2Love (équivalent Swift WidgetData)
 */
data class WidgetData(
    val daysTotal: Int,
    val duration: String,
    val daysToAnniversary: Int,
    val distance: String?,
    val message: String?,
    val userName: String?,
    val partnerName: String?,
    val userImageURL: String?,
    val partnerImageURL: String?,
    val userLatitude: Double?,
    val userLongitude: Double?,
    val partnerLatitude: Double?,
    val partnerLongitude: Double?,
    val hasSubscription: Boolean,
    val lastUpdate: Date
) {
    
    companion object {
        private const val TAG = "WidgetData"
        private const val SHARED_PREFS_NAME = "love2love_widget_data"
        
        // Clés SharedPreferences (équivalent UserDefaults iOS)
        private const val KEY_DAYS_TOTAL = "widget_days_total"
        private const val KEY_DURATION = "widget_duration"
        private const val KEY_DAYS_TO_ANNIVERSARY = "widget_days_to_anniversary"
        private const val KEY_DISTANCE = "widget_distance"
        private const val KEY_MESSAGE = "widget_message"
        private const val KEY_USER_NAME = "widget_user_name"
        private const val KEY_PARTNER_NAME = "widget_partner_name"
        private const val KEY_USER_IMAGE_URL = "widget_user_image_url"
        private const val KEY_PARTNER_IMAGE_URL = "widget_partner_image_url"
        private const val KEY_USER_LATITUDE = "widget_user_latitude"
        private const val KEY_USER_LONGITUDE = "widget_user_longitude"
        private const val KEY_PARTNER_LATITUDE = "widget_partner_latitude"
        private const val KEY_PARTNER_LONGITUDE = "widget_partner_longitude"
        private const val KEY_HAS_SUBSCRIPTION = "widget_has_subscription"
        private const val KEY_LAST_UPDATE = "widget_last_update"
        
        /**
         * Données placeholder pour les previews et tests
         */
        val placeholder = WidgetData(
            daysTotal = 365,
            duration = "1 an",
            daysToAnniversary = 30,
            distance = "3.128 km",
            message = "💕 Je pense à toi",
            userName = "Alex",
            partnerName = "Morgan",
            userImageURL = null,
            partnerImageURL = null,
            userLatitude = 48.8566,
            userLongitude = 2.3522,
            partnerLatitude = 43.6047,
            partnerLongitude = 1.4442,
            hasSubscription = true,
            lastUpdate = Date()
        )
        
        val placeholderUserMissing = WidgetData(
            daysTotal = 365,
            duration = "1 an",
            daysToAnniversary = 30,
            distance = null,
            message = "💕 Je pense à toi",
            userName = "Alex",
            partnerName = "Morgan",
            userImageURL = null,
            partnerImageURL = null,
            userLatitude = null,
            userLongitude = null,
            partnerLatitude = 43.6047,
            partnerLongitude = 1.4442,
            hasSubscription = false,
            lastUpdate = Date()
        )
        
        val placeholderPartnerMissing = WidgetData(
            daysTotal = 365,
            duration = "1 an",
            daysToAnniversary = 30,
            distance = null,
            message = "💕 Je pense à toi",
            userName = "Alex",
            partnerName = "Morgan",
            userImageURL = null,
            partnerImageURL = null,
            userLatitude = 48.8566,
            userLongitude = 2.3522,
            partnerLatitude = null,
            partnerLongitude = null,
            hasSubscription = false,
            lastUpdate = Date()
        )
        
        /**
         * Charge les données depuis SharedPreferences (équivalent UserDefaults iOS)
         */
        fun loadFromSharedPreferences(context: Context): WidgetData? {
            Log.d(TAG, "🔍 Widget: Début chargement données SharedPreferences...")
            
            val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
            
            val daysTotal = prefs.getInt(KEY_DAYS_TOTAL, 0)
            val duration = prefs.getString(KEY_DURATION, "") ?: ""
            val daysToAnniversary = prefs.getInt(KEY_DAYS_TO_ANNIVERSARY, 0)
            val distance = prefs.getString(KEY_DISTANCE, null)
            val message = prefs.getString(KEY_MESSAGE, null)
            val userName = prefs.getString(KEY_USER_NAME, null)
            val partnerName = prefs.getString(KEY_PARTNER_NAME, null)
            val userImageURL = prefs.getString(KEY_USER_IMAGE_URL, null)
            val partnerImageURL = prefs.getString(KEY_PARTNER_IMAGE_URL, null)
            val hasSubscription = prefs.getBoolean(KEY_HAS_SUBSCRIPTION, false)
            
            Log.d(TAG, "🔍 Widget: Données récupérées:")
            Log.d(TAG, "  - daysTotal: $daysTotal")
            Log.d(TAG, "  - userName: $userName")
            Log.d(TAG, "  - partnerName: $partnerName")
            Log.d(TAG, "  - hasSubscription: $hasSubscription")
            
            // Coordonnées (peuvent être null)
            val userLatitude = if (prefs.contains(KEY_USER_LATITUDE)) {
                prefs.getFloat(KEY_USER_LATITUDE, 0f).toDouble()
            } else null
            
            val userLongitude = if (prefs.contains(KEY_USER_LONGITUDE)) {
                prefs.getFloat(KEY_USER_LONGITUDE, 0f).toDouble()
            } else null
            
            val partnerLatitude = if (prefs.contains(KEY_PARTNER_LATITUDE)) {
                prefs.getFloat(KEY_PARTNER_LATITUDE, 0f).toDouble()
            } else null
            
            val partnerLongitude = if (prefs.contains(KEY_PARTNER_LONGITUDE)) {
                prefs.getFloat(KEY_PARTNER_LONGITUDE, 0f).toDouble()
            } else null
            
            val lastUpdateTimestamp = prefs.getLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            
            // Si pas de données importantes, retourner null
            if (daysTotal <= 0 && duration.isEmpty()) {
                Log.w(TAG, "❌ Widget: Pas de données importantes trouvées")
                return null
            }
            
            Log.d(TAG, "✅ Widget: Création WidgetData avec les données récupérées")
            
            return WidgetData(
                daysTotal = daysTotal,
                duration = duration,
                daysToAnniversary = daysToAnniversary,
                distance = distance,
                message = message,
                userName = userName,
                partnerName = partnerName,
                userImageURL = userImageURL,
                partnerImageURL = partnerImageURL,
                userLatitude = userLatitude,
                userLongitude = userLongitude,
                partnerLatitude = partnerLatitude,
                partnerLongitude = partnerLongitude,
                hasSubscription = hasSubscription,
                lastUpdate = Date(lastUpdateTimestamp)
            )
        }
        
        /**
         * Sauvegarde les données dans SharedPreferences
         * À appeler depuis l'app principale quand les données changent
         */
        fun saveToSharedPreferences(context: Context, widgetData: WidgetData) {
            Log.d(TAG, "💾 Widget: Sauvegarde données dans SharedPreferences")
            
            val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            editor.putInt(KEY_DAYS_TOTAL, widgetData.daysTotal)
            editor.putString(KEY_DURATION, widgetData.duration)
            editor.putInt(KEY_DAYS_TO_ANNIVERSARY, widgetData.daysToAnniversary)
            editor.putString(KEY_DISTANCE, widgetData.distance)
            editor.putString(KEY_MESSAGE, widgetData.message)
            editor.putString(KEY_USER_NAME, widgetData.userName)
            editor.putString(KEY_PARTNER_NAME, widgetData.partnerName)
            editor.putString(KEY_USER_IMAGE_URL, widgetData.userImageURL)
            editor.putString(KEY_PARTNER_IMAGE_URL, widgetData.partnerImageURL)
            editor.putBoolean(KEY_HAS_SUBSCRIPTION, widgetData.hasSubscription)
            editor.putLong(KEY_LAST_UPDATE, widgetData.lastUpdate.time)
            
            // Coordonnées (gestion des null)
            if (widgetData.userLatitude != null) {
                editor.putFloat(KEY_USER_LATITUDE, widgetData.userLatitude.toFloat())
            } else {
                editor.remove(KEY_USER_LATITUDE)
            }
            
            if (widgetData.userLongitude != null) {
                editor.putFloat(KEY_USER_LONGITUDE, widgetData.userLongitude.toFloat())
            } else {
                editor.remove(KEY_USER_LONGITUDE)
            }
            
            if (widgetData.partnerLatitude != null) {
                editor.putFloat(KEY_PARTNER_LATITUDE, widgetData.partnerLatitude.toFloat())
            } else {
                editor.remove(KEY_PARTNER_LATITUDE)
            }
            
            if (widgetData.partnerLongitude != null) {
                editor.putFloat(KEY_PARTNER_LONGITUDE, widgetData.partnerLongitude.toFloat())
            } else {
                editor.remove(KEY_PARTNER_LONGITUDE)
            }
            
            editor.apply()
            
            Log.d(TAG, "✅ Widget: Données sauvegardées")
            
            // Déclencher la mise à jour des widgets
            Love2LoveWidgetUpdater.updateAllWidgets(context)
        }
    }
    
    /**
     * Calcule les composants de temps (jours, heures, minutes, secondes)
     * 
     * CORRECTION: Logique simplifiée comme iOS - utilise directement daysTotal
     * sans calcul inverse compliqué
     */
    fun getTimeComponents(): TimeComponents {
        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.time = now
        
        // Utiliser daysTotal directement (déjà calculé correctement par RelationshipStats)
        val days = maxOf(daysTotal, 0)
        
        // Heures/minutes/secondes actuelles (comme iOS timeline)
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        
        return TimeComponents(
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds
        )
    }
}

/**
 * Composants de temps calculés
 */
data class TimeComponents(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int
)

/**
 * Helper pour gérer les images de profil dans les widgets
 */
object WidgetImageHelper {
    private const val TAG = "WidgetImageHelper"
    
    /**
     * Vérifie si une vraie image de profil existe
     * Équivalent de hasRealProfileImage() iOS
     */
    fun hasRealProfileImage(context: Context, imagePath: String?): Boolean {
        if (imagePath.isNullOrEmpty()) return false
        
        Log.d(TAG, "🖼️ Widget: Vérification image profil - Path: $imagePath")
        
        // Sur Android, on utilise le stockage interne de l'app
        val imageFile = context.getFileStreamPath("profile_images/$imagePath")
        
        val fileExists = imageFile?.exists() == true
        Log.d(TAG, "🖼️ Widget: Fichier '$imagePath' existe: $fileExists")
        
        if (fileExists && imageFile != null) {
            val fileSize = imageFile.length()
            Log.d(TAG, "🖼️ Widget: Taille du fichier: $fileSize bytes")
            
            if (fileSize > 0) {
                Log.d(TAG, "✅ Widget: Fichier valide trouvé")
                return true
            } else {
                Log.d(TAG, "❌ Widget: Fichier vide")
                return false
            }
        }
        
        Log.d(TAG, "❌ Widget: Pas de fichier image valide trouvé")
        return false
    }
    
    /**
     * Charge une image locale pour les widgets
     * Note: Sur Android, les widgets utilisent RemoteViews donc pas de chargement direct d'images
     * Cette fonction est gardée pour compatibilité future
     */
    fun loadLocalImagePath(context: Context, imagePath: String?): String? {
        if (imagePath.isNullOrEmpty()) return null
        
        val imageFile = context.getFileStreamPath("profile_images/$imagePath")
        return if (imageFile?.exists() == true && imageFile.length() > 0) {
            imageFile.absolutePath
        } else {
            null
        }
    }
}
