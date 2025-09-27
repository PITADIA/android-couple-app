package com.love2loveapp.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.love2loveapp.R
import com.love2loveapp.MainActivity
import com.love2loveapp.services.widgets.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 🌍 DistanceWidgetProvider - Widget distance partenaires
 * 
 * Équivalent Android du widget distance iOS (Small uniquement) :
 * - Affichage distance formatée entre partenaires
 * - Messages émotionnels rotatifs
 * - Gestion états (proche/loin/pas de localisation)
 * - Premium gating
 * - Deep link vers page localisation app
 */
class DistanceWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "DistanceWidget"
        
        // Actions widget distance
        const val ACTION_DISTANCE_WIDGET_UPDATE = "com.love2loveapp.DISTANCE_WIDGET_UPDATE"
        const val ACTION_DISTANCE_WIDGET_CLICK = "com.love2loveapp.DISTANCE_WIDGET_CLICK"
        
        // Type widget
        const val WIDGET_TYPE_DISTANCE = "distance"
        
        /**
         * 🔄 Mise à jour forcée tous widgets distance
         */
        fun updateAllWidgets(context: Context) {
            Log.d(TAG, "🔄 Mise à jour forcée widgets distance")
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, DistanceWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            if (appWidgetIds.isNotEmpty()) {
                val updateIntent = Intent(context, DistanceWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(updateIntent)
                Log.d(TAG, "✅ Broadcast update distance envoyé pour ${appWidgetIds.size} widgets")
            }
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "🌍 onUpdate: Mise à jour ${appWidgetIds.size} widgets distance")
        
        // Mettre à jour chaque widget distance
        appWidgetIds.forEach { widgetId ->
            updateDistanceWidget(context, appWidgetManager, widgetId)
        }
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "✅ onEnabled: Premier widget distance ajouté")
        
        // Si c'est le premier widget de l'app, démarrer service
        val totalWidgets = getTotalWidgetCount(context)
        if (totalWidgets == 1) {
            WidgetUpdateService.startPeriodicUpdates(context)
        }
        
        // Rafraîchir données
        refreshWidgetDataAsync(context)
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "⏹️ onDisabled: Dernier widget distance supprimé")
        
        // Si plus aucun widget, arrêter service
        val remainingWidgets = getTotalWidgetCount(context) - 1
        if (remainingWidgets == 0) {
            WidgetUpdateService.stopPeriodicUpdates(context)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_DISTANCE_WIDGET_UPDATE -> {
                Log.d(TAG, "🔄 Réception broadcast distance widget update")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, DistanceWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            
            ACTION_DISTANCE_WIDGET_CLICK -> {
                Log.d(TAG, "👆 Click widget distance")
                handleDistanceWidgetClick(context)
            }
        }
    }
    
    /**
     * 🌍 Mettre à jour widget distance individuel
     */
    private fun updateDistanceWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "🌍 Mise à jour widget distance ID: $appWidgetId")
        
        try {
            // Récupérer données depuis WidgetRepository
            val widgetRepository = WidgetRepository.getInstance(context)
            val widgetData = widgetRepository.getCurrentWidgetData()
            
            Log.d(TAG, "📊 Données distance: ${widgetData.distanceInfo}")
            
            // Vérifier abonnement premium
            if (widgetData.shouldShowPremiumGate) {
                Log.d(TAG, "💎 Affichage widget distance premium bloqué")
                showDistancePremiumBlockedWidget(context, appWidgetManager, appWidgetId)
                return
            }
            
            // Créer layout widget distance
            val views = createDistanceWidget(context, widgetData)
            
            // Configurer click listener
            setupDistanceWidgetClickListener(context, views, appWidgetId)
            
            // Appliquer mise à jour
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "✅ Widget distance $appWidgetId mis à jour avec succès")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur mise à jour widget distance $appWidgetId: ${e.message}", e)
            showDistanceErrorWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    /**
     * 🎨 Créer layout widget distance
     */
    private fun createDistanceWidget(context: Context, data: com.love2loveapp.models.widgets.WidgetData): RemoteViews {
        Log.d(TAG, "🎨 Création widget distance")
        
        val views = RemoteViews(context.packageName, R.layout.widget_distance_small)
        
        val distanceInfo = data.distanceInfo
        
        if (distanceInfo != null && distanceInfo.isLocationAvailable) {
            // 🌍 Distance disponible
            views.setTextViewText(R.id.widget_distance_value, distanceInfo.formattedDistance)
            views.setTextViewText(R.id.widget_distance_emoji, distanceInfo.distanceEmoji)
            views.setTextViewText(R.id.widget_distance_message, distanceInfo.currentMessage)
            
            // 🎨 Couleur selon proximité
            val textColor = when {
                distanceInfo.isVeryClose -> android.R.color.holo_green_dark
                distanceInfo.isVeryFar -> android.R.color.holo_red_dark
                else -> android.R.color.black
            }
            views.setTextColor(R.id.widget_distance_value, context.getColor(textColor))
            
            Log.d(TAG, "🌍 Widget distance: ${distanceInfo.formattedDistance} - ${distanceInfo.currentMessage}")
            
        } else {
            // 🚫 Pas de localisation
            views.setTextViewText(R.id.widget_distance_value, context.getString(R.string.widget_no_location))
            views.setTextViewText(R.id.widget_distance_emoji, "📍")
            views.setTextViewText(R.id.widget_distance_message, context.getString(R.string.widget_enable_location))
            
            Log.d(TAG, "📍 Widget distance: Pas de localisation")
        }
        
        // 👫 Noms partenaires si disponible
        if (data.hasPartner) {
            views.setTextViewText(R.id.widget_partner_names, data.formattedCoupleNames)
        } else {
            views.setTextViewText(R.id.widget_partner_names, context.getString(R.string.widget_connect_partner))
        }
        
        // 🎨 Appliquer thème
        applyDistanceWidgetTheme(views, data)
        
        return views
    }
    
    /**
     * 💎 Widget distance premium bloqué
     */
    private fun showDistancePremiumBlockedWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "💎 Création widget distance premium bloqué")
        
        val views = RemoteViews(context.packageName, R.layout.widget_distance_premium_blocked).apply {
            setTextViewText(R.id.blocked_title, context.getString(R.string.widget_distance_premium_title))
            setTextViewText(R.id.blocked_message, context.getString(R.string.widget_distance_premium_message))
            
            // Click pour ouvrir paywall
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("show_subscription", true)
                putExtra("from_widget", true)
                putExtra("widget_type", "distance")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            setOnClickPendingIntent(R.id.blocked_container, pendingIntent)
        }
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    /**
     * ❌ Widget distance erreur
     */
    private fun showDistanceErrorWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_distance_error).apply {
            setTextViewText(R.id.error_message, context.getString(R.string.widget_distance_error))
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    /**
     * 👆 Configurer click listener widget distance
     */
    private fun setupDistanceWidgetClickListener(context: Context, views: RemoteViews, widgetId: Int) {
        // Intent pour ouvrir page localisation/distance app
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("from_widget", true)
            putExtra("widget_type", WIDGET_TYPE_DISTANCE)
            putExtra("navigate_to", "location") // Navigation vers page distance/localisation
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        views.setOnClickPendingIntent(R.id.widget_distance_container, pendingIntent)
    }
    
    /**
     * 🎨 Appliquer thème widget distance
     */
    private fun applyDistanceWidgetTheme(views: RemoteViews, data: com.love2loveapp.models.widgets.WidgetData) {
        val colors = data.getThemeColors()
        
        // TODO: Appliquer couleurs thème distance
        Log.d(TAG, "🎨 Thème widget distance: ${data.widgetTheme}")
    }
    
    /**
     * 👆 Gérer click widget distance
     */
    private fun handleDistanceWidgetClick(context: Context) {
        Log.d(TAG, "👆 Gestion click widget distance")
        
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("from_widget", true)
            putExtra("widget_type", WIDGET_TYPE_DISTANCE)
            putExtra("navigate_to", "location")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        context.startActivity(intent)
    }
    
    /**
     * 🔢 Compter total widgets dans app
     */
    private fun getTotalWidgetCount(context: Context): Int {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        
        // Compter widgets HomeScreen
        val homeScreenIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, HomeScreenWidgetProvider::class.java)
        ).size
        
        // Compter widgets Distance
        val distanceIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, DistanceWidgetProvider::class.java)
        ).size
        
        val total = homeScreenIds + distanceIds
        Log.d(TAG, "🔢 Total widgets: $total (HomeScreen: $homeScreenIds, Distance: $distanceIds)")
        
        return total
    }
    
    /**
     * 🔄 Rafraîchir données widget asynchrone
     */
    private fun refreshWidgetDataAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = WidgetRepository.getInstance(context)
                repository.refreshWidgetData(forceUpdate = true)
                Log.d(TAG, "🔄 Données widget distance rafraîchies")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur refresh distance async: ${e.message}")
            }
        }
    }
}
