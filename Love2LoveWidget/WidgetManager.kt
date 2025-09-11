package com.love2loveapp.widget

import android.content.Context
import android.util.Log
import java.util.*

/**
 * Manager pour gérer les widgets Love2Love depuis l'app principale
 * Équivalent du système de UserDefaults/App Groups iOS
 */
object WidgetManager {
    private const val TAG = "WidgetManager"
    
    /**
     * Met à jour les données des widgets avec les informations actuelles de l'utilisateur
     * À appeler depuis l'app principale quand les données changent
     */
    fun updateWidgetData(
        context: Context,
        daysTotal: Int,
        duration: String,
        daysToAnniversary: Int,
        distance: String? = null,
        message: String? = null,
        userName: String? = null,
        partnerName: String? = null,
        userImageURL: String? = null,
        partnerImageURL: String? = null,
        userLatitude: Double? = null,
        userLongitude: Double? = null,
        partnerLatitude: Double? = null,
        partnerLongitude: Double? = null,
        hasSubscription: Boolean = false
    ) {
        Log.d(TAG, "🔄 Mise à jour des données widgets")
        Log.d(TAG, "  - daysTotal: $daysTotal")
        Log.d(TAG, "  - userName: $userName")
        Log.d(TAG, "  - partnerName: $partnerName")
        Log.d(TAG, "  - hasSubscription: $hasSubscription")
        
        val widgetData = WidgetData(
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
            lastUpdate = Date()
        )
        
        // Sauvegarder et déclencher la mise à jour des widgets
        WidgetData.saveToSharedPreferences(context, widgetData)
        
        Log.d(TAG, "✅ Données widgets mises à jour et widgets rafraîchis")
    }
    
    /**
     * Met à jour uniquement le statut d'abonnement
     * Utile quand l'utilisateur souscrit/annule son abonnement
     */
    fun updateSubscriptionStatus(context: Context, hasSubscription: Boolean) {
        Log.d(TAG, "🔄 Mise à jour statut abonnement: $hasSubscription")
        
        val currentData = WidgetData.loadFromSharedPreferences(context)
        if (currentData != null) {
            val updatedData = currentData.copy(
                hasSubscription = hasSubscription,
                lastUpdate = Date()
            )
            WidgetData.saveToSharedPreferences(context, updatedData)
            Log.d(TAG, "✅ Statut abonnement widgets mis à jour")
        } else {
            Log.w(TAG, "⚠️ Impossible de mettre à jour le statut abonnement - pas de données existantes")
        }
    }
    
    /**
     * Met à jour uniquement les données de localisation
     * Utile quand les coordonnées GPS changent
     */
    fun updateLocationData(
        context: Context,
        userLatitude: Double? = null,
        userLongitude: Double? = null,
        partnerLatitude: Double? = null,
        partnerLongitude: Double? = null,
        distance: String? = null
    ) {
        Log.d(TAG, "🔄 Mise à jour données localisation")
        Log.d(TAG, "  - distance: $distance")
        
        val currentData = WidgetData.loadFromSharedPreferences(context)
        if (currentData != null) {
            val updatedData = currentData.copy(
                userLatitude = userLatitude ?: currentData.userLatitude,
                userLongitude = userLongitude ?: currentData.userLongitude,
                partnerLatitude = partnerLatitude ?: currentData.partnerLatitude,
                partnerLongitude = partnerLongitude ?: currentData.partnerLongitude,
                distance = distance ?: currentData.distance,
                lastUpdate = Date()
            )
            WidgetData.saveToSharedPreferences(context, updatedData)
            Log.d(TAG, "✅ Données localisation widgets mises à jour")
        } else {
            Log.w(TAG, "⚠️ Impossible de mettre à jour la localisation - pas de données existantes")
        }
    }
    
    /**
     * Efface toutes les données des widgets
     * Utile lors de la déconnexion
     */
    fun clearWidgetData(context: Context) {
        Log.d(TAG, "🗑️ Effacement des données widgets")
        
        val prefs = context.getSharedPreferences("love2love_widget_data", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // Déclencher la mise à jour des widgets avec des données vides
        Love2LoveWidgetUpdater.updateAllWidgets(context)
        
        Log.d(TAG, "✅ Données widgets effacées")
    }
    
    /**
     * Vérifie si des widgets sont actuellement installés sur l'écran d'accueil
     */
    fun hasActiveWidgets(context: Context): Boolean {
        return try {
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            
            // Vérifier les widgets principaux
            val mainWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, Love2LoveWidgetProvider::class.java)
            )
            
            // Vérifier les widgets de distance
            val distanceWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, Love2LoveDistanceWidgetProvider::class.java)
            )
            
            val totalWidgets = mainWidgetIds.size + distanceWidgetIds.size
            Log.d(TAG, "📊 Widgets actifs: $totalWidgets (${mainWidgetIds.size} principaux + ${distanceWidgetIds.size} distance)")
            
            totalWidgets > 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification widgets actifs", e)
            false
        }
    }
    
    /**
     * Force la mise à jour de tous les widgets
     * Utile après des changements importants dans l'app
     */
    fun forceUpdateAllWidgets(context: Context) {
        Log.d(TAG, "🔄 Force la mise à jour de tous les widgets")
        Love2LoveWidgetUpdater.updateAllWidgets(context)
    }
}

/**
 * Extension pour faciliter les appels depuis l'app principale
 * Exemple d'utilisation dans votre AppState ou ViewModel :
 * 
 * // Mise à jour complète
 * WidgetManager.updateWidgetData(
 *     context = context,
 *     daysTotal = relationshipDays,
 *     duration = "2 ans",
 *     daysToAnniversary = 30,
 *     userName = currentUser.name,
 *     partnerName = partner.name,
 *     hasSubscription = freemiumManager.hasActiveSubscription
 * )
 * 
 * // Mise à jour abonnement seulement
 * WidgetManager.updateSubscriptionStatus(context, true)
 * 
 * // Mise à jour localisation seulement  
 * WidgetManager.updateLocationData(
 *     context = context,
 *     userLatitude = 48.8566,
 *     userLongitude = 2.3522,
 *     distance = "3.2 km"
 * )
 */
