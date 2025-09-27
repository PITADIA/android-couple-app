package com.love2loveapp.services.subscription

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.love2loveapp.MainActivity
import com.love2loveapp.R

/**
 * 🔔 SubscriptionNotificationService - Notifications partage d'abonnements
 * 
 * Équivalent Android de PartnerSubscriptionNotificationService iOS
 * Gère l'affichage des notifications système pour les événements d'abonnement
 */
class SubscriptionNotificationService private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SubscriptionNotification"
        
        // IDs des canaux de notification
        private const val CHANNEL_SUBSCRIPTION = "subscription_channel"
        private const val CHANNEL_PARTNER_SHARING = "partner_sharing_channel"
        
        // IDs des notifications
        private const val NOTIFICATION_ID_INHERITED = 1001
        private const val NOTIFICATION_ID_SHARED = 1002
        private const val NOTIFICATION_ID_LOST = 1003
        
        @Volatile
        private var instance: SubscriptionNotificationService? = null
        
        fun getInstance(context: Context): SubscriptionNotificationService {
            return instance ?: synchronized(this) {
                instance ?: SubscriptionNotificationService(context.applicationContext).also { 
                    instance = it
                    it.createNotificationChannels()
                }
            }
        }
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    /**
     * 🔧 Création des canaux de notification (Android O+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal pour les abonnements généraux
            val subscriptionChannel = NotificationChannel(
                CHANNEL_SUBSCRIPTION,
                "Abonnements Love2Love",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications liées à votre abonnement Love2Love"
                enableVibration(true)
                enableLights(true)
            }
            
            // Canal pour le partage entre partenaires
            val partnerSharingChannel = NotificationChannel(
                CHANNEL_PARTNER_SHARING,
                "Partage Partenaire",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications de partage d'abonnement entre partenaires"
                enableVibration(true)
                enableLights(true)
            }
            
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(subscriptionChannel)
            systemNotificationManager.createNotificationChannel(partnerSharingChannel)
            
            Log.d(TAG, "✅ Canaux de notification créés")
        }
    }
    
    /**
     * 🎉 Notification d'héritage d'abonnement depuis le partenaire
     * Équivalent iOS showSubscriptionInheritedNotification()
     */
    fun showSubscriptionInheritedNotification(partnerName: String) {
        Log.d(TAG, "🎉 Affichage notification héritage de: $partnerName")
        
        try {
            // Intent pour ouvrir l'app au tap
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("source", "subscription_inherited")
                putExtra("partner_name", partnerName)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context, 
                NOTIFICATION_ID_INHERITED,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            
            // Construction de la notification
            val notification = NotificationCompat.Builder(context, CHANNEL_PARTNER_SHARING)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Icône système par défaut
                .setContentTitle("🎉 Abonnement Premium Activé !")
                .setContentText("$partnerName a partagé son abonnement avec vous. Vous avez maintenant accès à toutes les fonctionnalités premium !")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$partnerName a partagé son abonnement Love2Love avec vous ! 🎉\n\n" +
                               "Vous pouvez maintenant accéder à :\n" +
                               "• Toutes les catégories de questions\n" +
                               "• Questions quotidiennes illimitées\n" +
                               "• Journal personnel complet\n" +
                               "• Et bien plus encore !\n\n" +
                               "Touchez pour ouvrir l'application.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 250, 250, 250)) // Vibration pattern
                .setLights(0xFFFF4081.toInt(), 1000, 1000) // Rose Love2Love
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.ic_media_play, // Icône système "Play"
                    "Découvrir",
                    pendingIntent
                )
                .build()
            
            // Afficher la notification
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID_INHERITED, notification)
                Log.d(TAG, "✅ Notification héritage affichée")
            } else {
                Log.w(TAG, "⚠️ Notifications désactivées par l'utilisateur")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage notification héritage: ${e.message}", e)
        }
    }
    
    /**
     * 🤝 Notification de partage réussi vers le partenaire
     */
    fun showSubscriptionSharedNotification(partnerName: String) {
        Log.d(TAG, "🤝 Affichage notification partage vers: $partnerName")
        
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("source", "subscription_shared")
                putExtra("partner_name", partnerName)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_SHARED,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_PARTNER_SHARING)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("✨ Abonnement Partagé !")
                .setContentText("Votre abonnement Premium est maintenant partagé avec $partnerName.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Votre abonnement Love2Love Premium est maintenant partagé avec $partnerName ! ✨\n\n" +
                               "$partnerName peut désormais profiter de toutes les fonctionnalités premium gratuitement grâce à votre abonnement.\n\n" +
                               "C'est ça, l'amour qui partage ! 💕")
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID_SHARED, notification)
                Log.d(TAG, "✅ Notification partage affichée")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage notification partage: ${e.message}", e)
        }
    }
    
    /**
     * 💔 Notification de perte d'abonnement partagé
     */
    fun showSubscriptionLostNotification(partnerName: String) {
        Log.d(TAG, "💔 Affichage notification perte d'abonnement de: $partnerName")
        
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("source", "subscription_lost")
                putExtra("partner_name", partnerName)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_LOST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_PARTNER_SHARING)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("📱 Changement d'abonnement")
                .setContentText("L'abonnement partagé par $partnerName a expiré.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("L'abonnement Love2Love Premium partagé par $partnerName a expiré. 📱\n\n" +
                               "Vous pouvez toujours :\n" +
                               "• Accéder aux questions gratuites\n" +
                               "• Utiliser le journal (5 entrées)\n" +
                               "• Questions quotidiennes (3 jours)\n\n" +
                               "Ou souscrire à votre propre abonnement Premium ! ✨")
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_preferences, // Icône système "Paramètres/Upgrade"
                    "S'abonner",
                    pendingIntent
                )
                .build()
            
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID_LOST, notification)
                Log.d(TAG, "✅ Notification perte affichée")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage notification perte: ${e.message}", e)
        }
    }
    
    /**
     * 🎊 Notification de succès d'achat propre
     */
    fun showSubscriptionPurchaseSuccessNotification() {
        Log.d(TAG, "🎊 Affichage notification succès achat")
        
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("source", "subscription_purchased")
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                1004,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_SUBSCRIPTION)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🎊 Bienvenue chez Love2Love Premium !")
                .setContentText("Votre abonnement est maintenant actif. Explorez toutes les fonctionnalités !")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Félicitations ! Votre abonnement Love2Love Premium est maintenant actif ! 🎊\n\n" +
                               "Vous avez maintenant accès à :\n" +
                               "• Toutes les catégories de questions premium\n" +
                               "• Questions quotidiennes illimitées\n" +
                               "• Journal personnel complet\n" +
                               "• Et votre partenaire en profite aussi ! 💕\n\n" +
                               "Commencez votre aventure Love2Love Premium !")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 100, 100, 100, 100, 100))
                .setLights(0xFF4CAF50.toInt(), 1000, 1000) // Vert succès
                .setContentIntent(pendingIntent)
                .build()
            
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                notificationManager.notify(1004, notification)
                Log.d(TAG, "✅ Notification succès achat affichée")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur notification succès achat: ${e.message}", e)
        }
    }
    
    /**
     * 🧹 Nettoyage des notifications d'abonnement
     */
    fun clearSubscriptionNotifications() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_INHERITED)
            notificationManager.cancel(NOTIFICATION_ID_SHARED)
            notificationManager.cancel(NOTIFICATION_ID_LOST)
            Log.d(TAG, "🧹 Notifications d'abonnement nettoyées")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage notifications: ${e.message}", e)
        }
    }
    
    /**
     * 📊 Vérifier si les notifications sont autorisées
     */
    fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    /**
     * 📱 Ouvrir les paramètres de notification de l'app
     */
    fun openNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent().apply {
                    action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent().apply {
                    action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = android.net.Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            
            context.startActivity(intent)
            Log.d(TAG, "📱 Ouverture paramètres notifications")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ouverture paramètres: ${e.message}", e)
        }
    }
}
