package com.love2loveapp.services.dailyquestion

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
import com.love2loveapp.models.QuestionResponse

/**
 * 🔔 DailyQuestionNotificationService - Notifications Questions du Jour Android
 * Compatible avec iOS push notifications existantes
 * 
 * Fonctionnalités :
 * - Nouvelles questions disponibles
 * - Nouveaux messages partenaire  
 * - Rappels participation
 * - Challenge quotidiens
 */
class DailyQuestionNotificationService private constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "DailyQuestionNotifications"
        
        // 🔧 CHANNEL IDs pour différents types de notifications
        private const val CHANNEL_NEW_QUESTION = "daily_question_new"
        private const val CHANNEL_NEW_MESSAGE = "daily_question_message"
        private const val CHANNEL_REMINDER = "daily_question_reminder"
        private const val CHANNEL_CHALLENGE = "daily_question_challenge"
        
        // 🆔 NOTIFICATION IDs
        private const val NOTIFICATION_NEW_QUESTION = 3001
        private const val NOTIFICATION_NEW_MESSAGE = 3002
        private const val NOTIFICATION_REMINDER = 3003
        private const val NOTIFICATION_CHALLENGE = 3004

        @Volatile
        private var INSTANCE: DailyQuestionNotificationService? = null

        fun getInstance(context: Context): DailyQuestionNotificationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DailyQuestionNotificationService(
                    context.applicationContext
                ).also { 
                    INSTANCE = it
                    it.createNotificationChannels()
                }
            }
        }
    }

    /**
     * 🔧 Création des channels de notification (Android O+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 🆕 Channel Nouvelles Questions
            val newQuestionChannel = NotificationChannel(
                CHANNEL_NEW_QUESTION,
                "Nouvelles Questions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications pour nouvelles questions du jour"
                enableVibration(true)
                setShowBadge(true)
            }

            // 💬 Channel Nouveaux Messages
            val newMessageChannel = NotificationChannel(
                CHANNEL_NEW_MESSAGE,
                "Nouveaux Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications pour nouveaux messages partenaire"
                enableVibration(true)
                setShowBadge(true)
            }

            // ⏰ Channel Rappels
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER,
                "Rappels Questions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Rappels pour répondre aux questions"
                enableVibration(false)
                setShowBadge(false)
            }

            // 🏆 Channel Challenges
            val challengeChannel = NotificationChannel(
                CHANNEL_CHALLENGE,
                "Challenges Quotidiens",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications pour challenges et récompenses"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(
                newQuestionChannel,
                newMessageChannel,
                reminderChannel,
                challengeChannel
            ))

            Log.d(TAG, "✅ Channels de notification créés")
        }
    }

    /**
     * 🆕 Notification nouvelle question du jour disponible
     */
    fun showNewQuestionNotification(
        questionText: String,
        questionDay: Int,
        partnerName: String? = null
    ) {
        Log.d(TAG, "🆕 Affichage notification nouvelle question: jour $questionDay")

        val title = "Nouvelle question du jour !"
        val content = if (partnerName != null) {
            "Vous et $partnerName avez une nouvelle question à explorer ensemble"
        } else {
            "Question jour $questionDay : ${questionText.take(60)}${if (questionText.length > 60) "..." else ""}"
        }

        val intent = createMainActivityIntent(
            extraAction = "open_daily_questions",
            extraQuestionDay = questionDay
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_QUESTION)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Répondre maintenant",
                intent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_NEW_QUESTION, notification)
            Log.d(TAG, "✅ Notification nouvelle question affichée")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage notification: ${e.message}")
        }
    }

    /**
     * 💬 Notification nouveau message du partenaire
     */
    fun showNewMessageNotification(
        response: QuestionResponse,
        partnerName: String,
        questionText: String? = null
    ) {
        Log.d(TAG, "💬 Affichage notification nouveau message de: $partnerName")

        val title = "💬 $partnerName"
        val content = response.text

        val intent = createMainActivityIntent(
            extraAction = "open_daily_questions_chat",
            extraQuestionId = response.questionId
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_MESSAGE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Répondre",
                intent
            )
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_NEW_MESSAGE, notification)
            Log.d(TAG, "✅ Notification nouveau message affichée")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage notification: ${e.message}")
        }
    }

    /**
     * ⏰ Notification rappel question non répondue
     */
    fun showQuestionReminderNotification(
        questionText: String,
        questionDay: Int,
        partnerHasResponded: Boolean = false
    ) {
        Log.d(TAG, "⏰ Affichage notification rappel: jour $questionDay")

        val title = "Question en attente"
        val content = if (partnerHasResponded) {
            "Votre partenaire a répondu à la question du jour. À votre tour !"
        } else {
            "N'oubliez pas de répondre à la question du jour"
        }

        val intent = createMainActivityIntent(
            extraAction = "open_daily_questions",
            extraQuestionDay = questionDay
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_REMINDER, notification)
            Log.d(TAG, "✅ Notification rappel affichée")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage notification: ${e.message}")
        }
    }

    /**
     * 🏆 Notification challenge/streak accompli
     */
    fun showChallengeNotification(
        streakCount: Int,
        rewardType: String,
        partnerName: String? = null
    ) {
        Log.d(TAG, "🏆 Affichage notification challenge: $streakCount jours")

        val title = "Félicitations ! 🏆"
        val content = if (partnerName != null) {
            "Vous et $partnerName avez maintenu votre série de $streakCount jours !"
        } else {
            "Vous avez une série de $streakCount jours de questions !"
        }

        val intent = createMainActivityIntent(
            extraAction = "open_daily_questions",
            extraStreak = streakCount
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHALLENGE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_CHALLENGE, notification)
            Log.d(TAG, "✅ Notification challenge affichée")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage notification: ${e.message}")
        }
    }

    /**
     * 🛑 Annuler toutes les notifications Questions du Jour
     */
    fun cancelAllNotifications() {
        Log.d(TAG, "🛑 Annulation toutes notifications Questions du Jour")
        
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NOTIFICATION_NEW_QUESTION)
        notificationManager.cancel(NOTIFICATION_NEW_MESSAGE)
        notificationManager.cancel(NOTIFICATION_REMINDER)
        notificationManager.cancel(NOTIFICATION_CHALLENGE)
    }

    /**
     * 🛑 Annuler notification spécifique
     */
    fun cancelNotification(type: NotificationType) {
        val notificationId = when (type) {
            NotificationType.NEW_QUESTION -> NOTIFICATION_NEW_QUESTION
            NotificationType.NEW_MESSAGE -> NOTIFICATION_NEW_MESSAGE
            NotificationType.REMINDER -> NOTIFICATION_REMINDER
            NotificationType.CHALLENGE -> NOTIFICATION_CHALLENGE
        }
        
        NotificationManagerCompat.from(context).cancel(notificationId)
        Log.d(TAG, "🛑 Notification $type annulée")
    }

    /**
     * 🔧 Helper pour créer les intents de navigation
     */
    private fun createMainActivityIntent(
        extraAction: String? = null,
        extraQuestionId: String? = null,
        extraQuestionDay: Int? = null,
        extraStreak: Int? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extraAction?.let { putExtra("notification_action", it) }
            extraQuestionId?.let { putExtra("question_id", it) }
            extraQuestionDay?.let { putExtra("question_day", it) }
            extraStreak?.let { putExtra("streak", it) }
        }

        return PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * 📊 Types de notifications Questions du Jour
 */
enum class NotificationType {
    NEW_QUESTION,
    NEW_MESSAGE,
    REMINDER,
    CHALLENGE
}
