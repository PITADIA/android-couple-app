package com.love2loveapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.love2loveapp.R
import com.love2loveapp.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

/**
 * Widget principal Love2Love (équivalent du widget iOS principal)
 * Gère les widgets Small et Medium avec compteur de jours
 */
class Love2LoveWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "Love2LoveWidget"
        const val ACTION_WIDGET_CLICK = "com.love2loveapp.widget.WIDGET_CLICK"
        const val EXTRA_WIDGET_TYPE = "widget_type"
        
        // Types de widgets
        const val WIDGET_TYPE_SMALL = "small"
        const val WIDGET_TYPE_MEDIUM = "medium"
        
        // Mise à jour automatique toutes les 30 minutes
        const val UPDATE_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "🔄 onUpdate appelé pour ${appWidgetIds.size} widgets")
        
        // Mettre à jour chaque widget
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "✅ Widget activé pour la première fois")
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "❌ Dernier widget supprimé")
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_WIDGET_CLICK -> {
                val widgetType = intent.getStringExtra(EXTRA_WIDGET_TYPE)
                Log.d(TAG, "🔗 Widget cliqué: $widgetType")
                handleWidgetClick(context, widgetType)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                Log.d(TAG, "🔄 Mise à jour automatique déclenchée")
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "🔄 Mise à jour widget ID: $appWidgetId")
        
        // Charger les données depuis SharedPreferences
        val widgetData = WidgetData.loadFromSharedPreferences(context)
        
        // Déterminer la taille du widget
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        
        Log.d(TAG, "📐 Widget dimensions: ${minWidth}x${minHeight}dp")
        
        // Choisir le layout approprié selon la taille
        val views = when {
            minWidth >= 250 -> {
                Log.d(TAG, "📱 Widget MEDIUM détecté")
                createMediumWidgetView(context, widgetData, appWidgetId)
            }
            else -> {
                Log.d(TAG, "📱 Widget SMALL détecté")
                createSmallWidgetView(context, widgetData, appWidgetId)
            }
        }
        
        // Mettre à jour le widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "✅ Widget $appWidgetId mis à jour")
    }

    private fun createSmallWidgetView(
        context: Context,
        widgetData: WidgetData?,
        appWidgetId: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_small_love2love)
        
        if (widgetData != null) {
            // Calculer les composants de temps
            val timeComponents = widgetData.getTimeComponents()
            
            // Mettre à jour le texte principal
            views.setTextViewText(
                R.id.widget_days_count,
                "${timeComponents.days}"
            )
            
            views.setTextViewText(
                R.id.widget_days_label,
                context.getString(R.string.widget_days_label)
            )
            
            views.setTextViewText(
                R.id.widget_together_text,
                context.getString(R.string.widget_together_text)
            )
            
            // Mettre à jour les initiales des profils
            views.setTextViewText(
                R.id.widget_user_initial,
                getUserInitial(widgetData.userName, context)
            )
            
            views.setTextViewText(
                R.id.widget_partner_initial,
                getPartnerInitial(widgetData.partnerName)
            )
            
            Log.d(TAG, "📊 Small widget: ${timeComponents.days} jours ensemble")
        } else {
            // Données placeholder
            views.setTextViewText(R.id.widget_days_count, "365")
            views.setTextViewText(R.id.widget_days_label, context.getString(R.string.widget_days_label))
            views.setTextViewText(R.id.widget_together_text, context.getString(R.string.widget_together_text))
            views.setTextViewText(R.id.widget_user_initial, "A")
            views.setTextViewText(R.id.widget_partner_initial, "M")
            
            Log.w(TAG, "⚠️ Utilisation des données placeholder pour small widget")
        }
        
        // Configurer le clic
        setupWidgetClick(context, views, WIDGET_TYPE_SMALL, appWidgetId)
        
        return views
    }

    private fun createMediumWidgetView(
        context: Context,
        widgetData: WidgetData?,
        appWidgetId: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_medium_love2love)
        
        if (widgetData != null) {
            // Vérifier l'abonnement pour le widget medium
            if (!widgetData.hasSubscription) {
                Log.w(TAG, "❌ Medium widget: Pas d'abonnement - Affichage widget bloqué")
                return createPremiumBlockedView(context, "Complet", appWidgetId)
            }
            
            // Calculer les composants de temps
            val timeComponents = widgetData.getTimeComponents()
            
            // Section gauche - Compteur
            views.setTextViewText(R.id.widget_days_count, "${timeComponents.days}")
            views.setTextViewText(R.id.widget_days_label, context.getString(R.string.widget_days_label))
            views.setTextViewText(R.id.widget_together_text, context.getString(R.string.widget_together_text))
            
            // Initiales des profils miniatures
            views.setTextViewText(R.id.widget_user_initial, getUserInitial(widgetData.userName, context))
            views.setTextViewText(R.id.widget_partner_initial, getPartnerInitial(widgetData.partnerName))
            
            // Section droite - Distance
            val locationStatus = getLocationStatus(widgetData)
            if (locationStatus.showDistance) {
                val distanceText = formatDistanceForLocale(widgetData.distance ?: "? km", context)
                views.setTextViewText(R.id.widget_distance_text, distanceText)
                views.setViewVisibility(R.id.widget_distance_text, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_location_error, android.view.View.GONE)
            } else {
                views.setTextViewText(R.id.widget_location_error, locationStatus.message)
                views.setViewVisibility(R.id.widget_distance_text, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_location_error, android.view.View.VISIBLE)
            }
            
            Log.d(TAG, "📊 Medium widget: ${timeComponents.days} jours, abonnement: ${widgetData.hasSubscription}")
        } else {
            // Données placeholder
            views.setTextViewText(R.id.widget_days_count, "365")
            views.setTextViewText(R.id.widget_days_label, context.getString(R.string.widget_days_label))
            views.setTextViewText(R.id.widget_together_text, context.getString(R.string.widget_together_text))
            views.setTextViewText(R.id.widget_user_initial, "A")
            views.setTextViewText(R.id.widget_partner_initial, "M")
            views.setTextViewText(R.id.widget_distance_text, "3.128 km")
            
            Log.w(TAG, "⚠️ Utilisation des données placeholder pour medium widget")
        }
        
        // Configurer le clic
        setupWidgetClick(context, views, WIDGET_TYPE_MEDIUM, appWidgetId)
        
        return views
    }

    private fun createPremiumBlockedView(
        context: Context,
        widgetType: String,
        appWidgetId: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_premium_blocked)
        
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
        
        Log.d(TAG, "🔒 Widget premium bloqué créé pour type: $widgetType")
        return views
    }

    private fun setupWidgetClick(
        context: Context,
        views: RemoteViews,
        widgetType: String,
        appWidgetId: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_widget", true)
            putExtra("widget_type", widgetType)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
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
            appWidgetId + 1000, // Offset pour éviter les conflits
            intent,
            getPendingIntentFlags()
        )
        
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
    }

    private fun handleWidgetClick(context: Context, widgetType: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_widget", true)
            putExtra("widget_type", widgetType)
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

    // Helper functions
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
        val currentLanguage = Locale.getDefault().language
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

    private enum class LocationStatus(val showDistance: Boolean, val messageResId: Int) {
        BOTH_AVAILABLE(true, 0),
        USER_MISSING(false, R.string.widget_enable_your_location),
        PARTNER_MISSING(false, R.string.widget_partner_enable_location),
        BOTH_MISSING(false, R.string.widget_enable_your_locations);
        
        val message: String
            get() = if (messageResId != 0) {
                // Note: Context needed for string resolution, handled in calling code
                ""
            } else ""
    }
}

/**
 * Extension pour mettre à jour tous les widgets Love2Love
 */
object Love2LoveWidgetUpdater {
    
    fun updateAllWidgets(context: Context) {
        Log.d("Love2LoveWidgetUpdater", "🔄 Mise à jour de tous les widgets Love2Love")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                
                // Mettre à jour les widgets principaux
                val mainWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, Love2LoveWidgetProvider::class.java)
                )
                
                if (mainWidgetIds.isNotEmpty()) {
                    val updateIntent = Intent(context, Love2LoveWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, mainWidgetIds)
                    }
                    context.sendBroadcast(updateIntent)
                    Log.d("Love2LoveWidgetUpdater", "✅ ${mainWidgetIds.size} widgets principaux mis à jour")
                }
                
                // Mettre à jour les widgets de distance
                val distanceWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, Love2LoveDistanceWidgetProvider::class.java)
                )
                
                if (distanceWidgetIds.isNotEmpty()) {
                    val updateIntent = Intent(context, Love2LoveDistanceWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, distanceWidgetIds)
                    }
                    context.sendBroadcast(updateIntent)
                    Log.d("Love2LoveWidgetUpdater", "✅ ${distanceWidgetIds.size} widgets de distance mis à jour")
                }
                
            } catch (e: Exception) {
                Log.e("Love2LoveWidgetUpdater", "❌ Erreur mise à jour widgets", e)
            }
        }
    }
}
