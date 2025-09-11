package com.love2loveapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.love2loveapp.R
import com.love2loveapp.MainActivity

/**
 * Widget de distance Love2Love (équivalent du widget iOS de distance)
 * Affiche uniquement la distance entre les partenaires
 */
class Love2LoveDistanceWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "Love2LoveDistanceWidget"
        const val ACTION_DISTANCE_WIDGET_CLICK = "com.love2loveapp.widget.DISTANCE_WIDGET_CLICK"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "🔄 onUpdate appelé pour ${appWidgetIds.size} widgets de distance")
        
        // Mettre à jour chaque widget
        for (appWidgetId in appWidgetIds) {
            updateDistanceWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "✅ Widget de distance activé pour la première fois")
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "❌ Dernier widget de distance supprimé")
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_DISTANCE_WIDGET_CLICK -> {
                Log.d(TAG, "🔗 Widget de distance cliqué")
                handleDistanceWidgetClick(context)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                Log.d(TAG, "🔄 Mise à jour automatique widget de distance déclenchée")
            }
        }
    }

    private fun updateDistanceWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "🔄 Mise à jour widget de distance ID: $appWidgetId")
        
        // Charger les données depuis SharedPreferences
        val widgetData = WidgetData.loadFromSharedPreferences(context)
        
        val views = createDistanceWidgetView(context, widgetData, appWidgetId)
        
        // Mettre à jour le widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "✅ Widget de distance $appWidgetId mis à jour")
    }

    private fun createDistanceWidgetView(
        context: Context,
        widgetData: WidgetData?,
        appWidgetId: Int
    ): RemoteViews {
        
        if (widgetData != null) {
            // Vérifier l'abonnement pour le widget de distance
            if (!widgetData.hasSubscription) {
                Log.w(TAG, "❌ Distance widget: Pas d'abonnement - Affichage widget bloqué")
                return createPremiumBlockedDistanceView(context, appWidgetId)
            }
            
            Log.d(TAG, "✅ Distance widget: Abonnement valide - Affichage widget normal")
        }
        
        val views = RemoteViews(context.packageName, R.layout.widget_small_distance)
        
        if (widgetData != null) {
            // Mettre à jour les initiales des profils
            views.setTextViewText(
                R.id.widget_user_initial,
                getUserInitial(widgetData.userName, context)
            )
            
            views.setTextViewText(
                R.id.widget_partner_initial,
                getPartnerInitial(widgetData.partnerName)
            )
            
            // Gérer l'affichage de la distance
            val locationStatus = getLocationStatus(widgetData)
            if (locationStatus.showDistance) {
                val distanceText = formatDistanceForLocale(widgetData.distance ?: "? km", context)
                views.setTextViewText(R.id.widget_distance_text, distanceText)
                views.setViewVisibility(R.id.widget_distance_text, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_location_error, android.view.View.GONE)
                
                Log.d(TAG, "📍 Distance widget: Affichage distance - $distanceText")
            } else {
                val errorMessage = getLocationErrorMessage(locationStatus, context)
                views.setTextViewText(R.id.widget_location_error, errorMessage)
                views.setViewVisibility(R.id.widget_distance_text, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_location_error, android.view.View.VISIBLE)
                
                Log.d(TAG, "⚠️ Distance widget: Erreur localisation - $errorMessage")
            }
            
        } else {
            // Données placeholder
            views.setTextViewText(R.id.widget_user_initial, "A")
            views.setTextViewText(R.id.widget_partner_initial, "M")
            views.setTextViewText(R.id.widget_distance_text, "3.128 km")
            views.setViewVisibility(R.id.widget_distance_text, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_location_error, android.view.View.GONE)
            
            Log.w(TAG, "⚠️ Utilisation des données placeholder pour widget de distance")
        }
        
        // Configurer le clic
        setupDistanceWidgetClick(context, views, appWidgetId)
        
        return views
    }

    private fun createPremiumBlockedDistanceView(
        context: Context,
        appWidgetId: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_premium_blocked_small)
        
        views.setTextViewText(
            R.id.widget_premium_title,
            context.getString(R.string.requires_subscription)
        )
        
        views.setTextViewText(
            R.id.widget_premium_subtitle,
            context.getString(R.string.tap_to_unlock)
        )
        
        // Configurer le clic vers l'abonnement
        setupSubscriptionClick(context, views, appWidgetId)
        
        Log.d(TAG, "🔒 Widget de distance premium bloqué créé")
        return views
    }

    private fun setupDistanceWidgetClick(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_widget", true)
            putExtra("widget_type", "distance")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 2000, // Offset pour éviter les conflits avec le widget principal
            intent,
            getPendingIntentFlags()
        )
        
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
    }

    private fun setupSubscriptionClick(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int
    ) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("coupleapp://subscription")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 3000, // Offset pour éviter les conflits
            intent,
            getPendingIntentFlags()
        )
        
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
    }

    private fun handleDistanceWidgetClick(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_widget", true)
            putExtra("widget_type", "distance")
        }
        
        context.startActivity(intent)
    }

    private fun getPendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    // Helper functions (réutilisées depuis Love2LoveWidgetProvider)
    private fun getUserInitial(userName: String?, context: Context): String {
        return if (userName.isNullOrEmpty()) {
            "U" // Fallback universel
        } else {
            userName.first().uppercaseChar().toString()
        }
    }

    private fun getPartnerInitial(partnerName: String?): String {
        return if (partnerName.isNullOrEmpty()) {
            "?" 
        } else {
            partnerName.first().uppercaseChar().toString()
        }
    }

    private fun formatDistanceForLocale(distance: String, context: Context): String {
        // Conversion km vers miles pour locale anglais (équivalent iOS)
        val currentLanguage = java.util.Locale.getDefault().language
        return if (currentLanguage == "en") {
            convertKmToMiles(distance)
        } else {
            distance
        }.let { dist ->
            // Capitaliser si c'est "ensemble" ou "together"
            if (dist.lowercase() == "ensemble" || dist.lowercase() == "together") {
                dist.replaceFirstChar { it.uppercase() }
            } else {
                dist
            }
        }
    }

    private fun convertKmToMiles(distance: String): String {
        // Cas spécial « ? km »
        if (distance.trim() == "? km") {
            return distance.replace("? km", "? mi")
        }

        // Pattern pour km
        val kmRegex = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*km")
        val kmMatch = kmRegex.find(distance)
        if (kmMatch != null) {
            val kmValue = kmMatch.groupValues[1].toDoubleOrNull()
            if (kmValue != null) {
                val milesValue = kmValue * 0.621371
                val formatted = if (milesValue < 10) {
                    String.format("%.1f mi", milesValue)
                } else {
                    "${milesValue.toInt()} mi"
                }
                return distance.replace(kmMatch.value, formatted)
            }
        }

        // Pattern pour mètres
        val mRegex = Regex("([0-9]+)\\s*m")
        val mMatch = mRegex.find(distance)
        if (mMatch != null) {
            val meters = mMatch.groupValues[1].toDoubleOrNull()
            if (meters != null) {
                val milesValue = meters / 1609.34
                val formatted = if (milesValue < 10) {
                    String.format("%.1f mi", milesValue)
                } else {
                    "${milesValue.toInt()} mi"
                }
                return distance.replace(mMatch.value, formatted)
            }
        }

        return distance
    }

    private fun getLocationStatus(widgetData: WidgetData): LocationStatus {
        val hasUserLocation = widgetData.userLatitude != null && widgetData.userLongitude != null
        val hasPartnerLocation = widgetData.partnerLatitude != null && widgetData.partnerLongitude != null
        
        return when {
            hasUserLocation && hasPartnerLocation -> LocationStatus.BOTH_AVAILABLE
            !hasUserLocation && hasPartnerLocation -> LocationStatus.USER_MISSING
            hasUserLocation && !hasPartnerLocation -> LocationStatus.PARTNER_MISSING
            else -> LocationStatus.BOTH_MISSING
        }
    }

    private fun getLocationErrorMessage(locationStatus: LocationStatus, context: Context): String {
        return when (locationStatus) {
            LocationStatus.USER_MISSING -> context.getString(R.string.widget_enable_your_location)
            LocationStatus.PARTNER_MISSING -> context.getString(R.string.widget_partner_enable_location)
            LocationStatus.BOTH_MISSING -> context.getString(R.string.widget_enable_your_locations)
            else -> ""
        }
    }

    private enum class LocationStatus(val showDistance: Boolean) {
        BOTH_AVAILABLE(true),
        USER_MISSING(false),
        PARTNER_MISSING(false),
        BOTH_MISSING(false)
    }
}
