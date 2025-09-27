package com.love2loveapp.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.love2loveapp.R
import com.love2loveapp.MainActivity
import com.love2loveapp.services.widgets.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 📱 HomeScreenWidgetProvider - Widget principal Love2Love
 * 
 * Équivalent Android du Love2LoveWidget iOS (WidgetKit) :
 * - Support Small et Medium widgets
 * - Affichage jours ensemble + noms couple
 * - Distance partenaire si disponible
 * - Gestion premium/freemium
 * - Deep links vers app
 * - Mise à jour temps réel
 */
class HomeScreenWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "HomeScreenWidget"
        
        // Actions widget
        const val ACTION_WIDGET_UPDATE = "com.love2loveapp.WIDGET_UPDATE"
        const val ACTION_WIDGET_CLICK = "com.love2loveapp.WIDGET_CLICK"
        
        // Types widgets
        const val WIDGET_TYPE_MAIN = "main"
        const val EXTRA_WIDGET_TYPE = "widget_type"
        
        /**
         * 🔄 Méthode utilitaire pour forcer mise à jour widgets
         */
        fun updateAllWidgets(context: Context) {
            Log.d(TAG, "🔄 Mise à jour forcée tous widgets")
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HomeScreenWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            if (appWidgetIds.isNotEmpty()) {
                val updateIntent = Intent(context, HomeScreenWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(updateIntent)
                Log.d(TAG, "✅ Broadcast update envoyé pour ${appWidgetIds.size} widgets")
            }
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "📱 onUpdate: Mise à jour ${appWidgetIds.size} widgets")
        
        // Mettre à jour chaque widget individuellement
        appWidgetIds.forEach { widgetId ->
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "✅ onEnabled: Premier widget ajouté")
        
        // Démarrer service mise à jour périodique
        WidgetUpdateService.startPeriodicUpdates(context)
        
        // Rafraîchir données immédiatement
        refreshWidgetDataAsync(context)
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "⏹️ onDisabled: Dernier widget supprimé")
        
        // Arrêter service mise à jour
        WidgetUpdateService.stopPeriodicUpdates(context)
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_WIDGET_UPDATE -> {
                Log.d(TAG, "🔄 Réception broadcast widget update")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, HomeScreenWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            
            ACTION_WIDGET_CLICK -> {
                val widgetType = intent.getStringExtra(EXTRA_WIDGET_TYPE)
                Log.d(TAG, "👆 Click widget type: $widgetType")
                handleWidgetClick(context, widgetType)
            }
        }
    }
    
    /**
     * 🔄 Mettre à jour un widget individuel
     */
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "🔄 Mise à jour widget ID: $appWidgetId")
        
        try {
            // Récupérer données depuis WidgetRepository
            val widgetRepository = WidgetRepository.getInstance(context)
            val widgetData = widgetRepository.getCurrentWidgetData()
            
            Log.d(TAG, "📊 Données widget: $widgetData")
            
            // Déterminer taille widget
            val widgetSize = getWidgetSize(appWidgetManager, appWidgetId)
            Log.d(TAG, "📏 Taille widget: $widgetSize")
            
            // Vérifier abonnement pour premium
            if (widgetData.shouldShowPremiumGate) {
                Log.d(TAG, "💎 Affichage widget premium bloqué")
                showPremiumBlockedWidget(context, appWidgetManager, appWidgetId, widgetSize)
                return
            }
            
            // Créer layout widget selon taille
            val views = when (widgetSize) {
                WidgetSize.SMALL -> createSmallWidget(context, widgetData)
                WidgetSize.MEDIUM -> createMediumWidget(context, widgetData)
                else -> createSmallWidget(context, widgetData) // Fallback
            }
            
            // Configurer click listener
            setupWidgetClickListener(context, views, appWidgetId)
            
            // Appliquer mise à jour
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "✅ Widget $appWidgetId mis à jour avec succès")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur mise à jour widget $appWidgetId: ${e.message}", e)
            showErrorWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    /**
     * 📏 Détecter taille widget (Small/Medium)
     */
    private fun getWidgetSize(appWidgetManager: AppWidgetManager, appWidgetId: Int): WidgetSize {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            
            when {
                width >= 250 -> WidgetSize.MEDIUM  // ~4x2 cellules
                else -> WidgetSize.SMALL           // ~2x2 cellules
            }
        } else {
            WidgetSize.SMALL // Par défaut sur versions anciennes
        }
    }
    
    /**
     * 📱 Créer widget Small (2x2)
     */
    private fun createSmallWidget(context: Context, data: com.love2loveapp.models.widgets.WidgetData): RemoteViews {
        Log.d(TAG, "📱 Création widget Small")
        
        val views = RemoteViews(context.packageName, R.layout.widget_home_screen_small)
        
        // 💕 Jours ensemble (principal)
        val daysCount = data.relationshipStats?.daysTotal ?: 0
        views.setTextViewText(R.id.widget_days_count, daysCount.toString())
        views.setTextViewText(R.id.widget_days_label, context.getString(R.string.widget_days_together))
        
        // 👫 Noms couple
        views.setTextViewText(R.id.widget_couple_names, data.formattedCoupleNames)
        
        // 🎨 Thème couleurs
        applyWidgetTheme(views, data)
        
        return views
    }
    
    /**
     * 📱 Créer widget Medium (4x2)
     */
    private fun createMediumWidget(context: Context, data: com.love2loveapp.models.widgets.WidgetData): RemoteViews {
        Log.d(TAG, "📱 Création widget Medium")
        
        val views = RemoteViews(context.packageName, R.layout.widget_home_screen_medium)
        
        // 💕 Jours ensemble
        val daysCount = data.relationshipStats?.daysTotal ?: 0
        views.setTextViewText(R.id.widget_days_count, daysCount.toString())
        views.setTextViewText(R.id.widget_days_label, context.getString(R.string.widget_days_together))
        
        // 👫 Noms couple
        views.setTextViewText(R.id.widget_couple_names, data.formattedCoupleNames)
        
        // 📅 Durée formatée
        data.relationshipStats?.let { stats ->
            views.setTextViewText(R.id.widget_duration, stats.formattedDuration)
            
            // 🎂 Prochain anniversaire
            if (stats.isAnniversaryToday) {
                views.setTextViewText(R.id.widget_anniversary, "🎉 Joyeux anniversaire !")
            } else {
                views.setTextViewText(R.id.widget_anniversary, stats.formattedNextAnniversary)
            }
        }
        
        // 🌍 Distance partenaire si disponible
        data.distanceInfo?.let { distance ->
            views.setTextViewText(R.id.widget_distance, distance.formattedDistance)
            views.setTextViewText(R.id.widget_distance_message, distance.currentMessage)
        } ?: run {
            views.setTextViewText(R.id.widget_distance, context.getString(R.string.widget_no_location))
            views.setTextViewText(R.id.widget_distance_message, "")
        }
        
        // 🎨 Thème couleurs
        applyWidgetTheme(views, data)
        
        return views
    }
    
    /**
     * 💎 Afficher widget premium bloqué
     */
    private fun showPremiumBlockedWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        size: WidgetSize
    ) {
        Log.d(TAG, "💎 Création widget premium bloqué")
        
        val layoutId = when (size) {
            WidgetSize.SMALL -> R.layout.widget_premium_blocked_small
            WidgetSize.MEDIUM -> R.layout.widget_premium_blocked_medium
        }
        
        val views = RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.blocked_title, context.getString(R.string.widget_premium_required))
            setTextViewText(R.id.blocked_message, context.getString(R.string.widget_unlock_premium))
            
            // Click pour ouvrir paywall
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("show_subscription", true)
                putExtra("from_widget", true)
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
     * ❌ Afficher widget erreur
     */
    private fun showErrorWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_error).apply {
            setTextViewText(R.id.error_message, context.getString(R.string.widget_error_message))
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    /**
     * 👆 Configurer click listener widget
     */
    private fun setupWidgetClickListener(context: Context, views: RemoteViews, widgetId: Int) {
        // Intent pour ouvrir app principale
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("from_widget", true)
            putExtra("widget_type", WIDGET_TYPE_MAIN)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
    }
    
    /**
     * 🎨 Appliquer thème couleurs widget
     */
    private fun applyWidgetTheme(views: RemoteViews, data: com.love2loveapp.models.widgets.WidgetData) {
        val colors = data.getThemeColors()
        
        // TODO: Appliquer couleurs selon thème
        // views.setInt(R.id.widget_background, "setBackgroundColor", Color.parseColor(colors.background))
        
        Log.d(TAG, "🎨 Thème widget appliqué: ${data.widgetTheme}")
    }
    
    /**
     * 👆 Gérer clicks widget
     */
    private fun handleWidgetClick(context: Context, widgetType: String?) {
        Log.d(TAG, "👆 Gestion click widget: $widgetType")
        
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("from_widget", true)
            putExtra("widget_type", widgetType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        context.startActivity(intent)
    }
    
    /**
     * 🔄 Rafraîchir données widget asynchrone
     */
    private fun refreshWidgetDataAsync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = WidgetRepository.getInstance(context)
                repository.refreshWidgetData(forceUpdate = true)
                Log.d(TAG, "🔄 Données widget rafraîchies")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur refresh async: ${e.message}")
            }
        }
    }
}

/**
 * 📏 Enum tailles widget
 */
enum class WidgetSize {
    SMALL,   // 2x2 cellules
    MEDIUM   // 4x2 cellules
}
