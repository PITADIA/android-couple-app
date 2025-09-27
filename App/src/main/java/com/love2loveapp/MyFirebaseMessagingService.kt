package com.love2loveapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "📱 Nouveau token FCM: [TOKEN_MASKED]")
        // Sauvegarde immédiate si user connecté, sinon on stash en SharedPreferences
        if (!updateFcmTokenForCurrentUser(this, token)) {
            stashTokenLocally(this, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Logs utiles (sans PII)
        Log.i(TAG, "✉️ FCM reçu - dataKeys=${remoteMessage.data.keys} " +
                "hasNotification=${remoteMessage.notification != null}")

        // 📊 AUDIT: Enregistrer la réception de notification dans Firestore
        auditNotificationReceived(remoteMessage)

        // Anti-doublon: si l'app est au premier plan, on NE montre pas de notif système
        if (isAppInForeground()) {
            Log.i(TAG, "⏭️ App au premier plan → pas d'affichage de notification (anti-double)")
            return
        }

        // Channel (sécurité, si AppDelegate ne l’a pas déjà créé)
        ensureDefaultChannel()

        // Extraire les champs (format aligné avec ton index.js)
        val data = remoteMessage.data
        val type = data["type"] ?: "new_message"
        val questionId = data["questionId"]
        val senderName = data["senderName"] ?: remoteMessage.notification?.title ?: "Votre partenaire"

        // Titre/Body : ton backend met le nom en title, message complet en body
        val title = remoteMessage.notification?.title ?: senderName
        val body = remoteMessage.notification?.body ?: data["body"] ?: ""

        // Intent d’ouverture (→ MainActivity) avec deep-link minimal par extras
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TYPE, type)
            if (!questionId.isNullOrBlank()) putExtra(EXTRA_QUESTION_ID, questionId)
            putExtra(EXTRA_SENDER_NAME, senderName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(), // requestCode unique
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        // ID stable si possible (questionId) sinon random
        val notificationId = (remoteMessage.messageId?.hashCode()
            ?: questionId?.hashCode()
            ?: Random.nextInt())

        // Construction de la notification
        val smallIconRes = resolveSmallIcon()
        val notification = NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Push !
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    // --- Helpers ---

    private fun isAppInForeground(): Boolean {
        val state = ProcessLifecycleOwner.get().lifecycle.currentState
        return state.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun ensureDefaultChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val existing = mgr.getNotificationChannel(DEFAULT_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    DEFAULT_CHANNEL_ID,
                    "Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                mgr.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Met à jour users/{uid}.fcmToken dans Firestore.
     * Retourne true si écrit immédiatement, false si on doit stasher.
     */
    private fun updateFcmTokenForCurrentUser(context: Context, token: String): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(user.uid).update(
                mapOf(
                    "fcmToken" to token,
                    "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()
                )
            ).addOnSuccessListener {
                Log.i(TAG, "✅ fcmToken enregistré pour user=[MASKED]")
                clearStashedToken(context)
            }.addOnFailureListener {
                Log.w(TAG, "⚠️ Échec écriture fcmToken, stash local", it)
                stashTokenLocally(context, token)
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Exception écriture fcmToken, stash local", e)
            stashTokenLocally(context, token)
        }
        return true
    }

    /** Si un token est stocké localement et que l'utilisateur se connecte, appelle ceci depuis ton Auth listener (AppDelegate.kt) */
    fun flushStashedTokenIfAny() {
        val stashed = getStashedToken(this) ?: return
        if (updateFcmTokenForCurrentUser(this, stashed)) {
            clearStashedToken(this)
        }
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        const val DEFAULT_CHANNEL_ID = "default_channel"

        // Extras pour deep-link
        const val EXTRA_TYPE = "type"
        const val EXTRA_QUESTION_ID = "questionId"
        const val EXTRA_SENDER_NAME = "senderName"
        
        // Clé pour SharedPreferences
        private const val KEY_STASHED_TOKEN = "stashed_fcm_token"

        /** Version statique pour AppDelegate.kt - flush token si user connecté */
        fun flushStashedTokenIfAny(context: Context) {
            val user = FirebaseAuth.getInstance().currentUser ?: return
            val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            val stashed = prefs.getString(KEY_STASHED_TOKEN, null) ?: return
            
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(user.uid).update(
                    mapOf(
                        "fcmToken" to stashed,
                        "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnSuccessListener {
                    Log.i(TAG, "✅ fcmToken flushed pour user=[MASKED]")
                    prefs.edit().remove(KEY_STASHED_TOKEN).apply()
                }.addOnFailureListener {
                    Log.w(TAG, "⚠️ Échec flush fcmToken", it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Exception flush fcmToken", e)
            }
        }
    }

    // --- Stash token en SharedPreferences en attendant une session Firebase ---

    private fun prefs(context: Context) =
        context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)

    private fun stashTokenLocally(context: Context, token: String) {
        prefs(context).edit().putString(KEY_STASHED_TOKEN, token).apply()
    }

    private fun getStashedToken(context: Context): String? =
        prefs(context).getString(KEY_STASHED_TOKEN, null)

    private fun clearStashedToken(context: Context) {
        prefs(context).edit().remove(KEY_STASHED_TOKEN).apply()
    }

    private fun pendingIntentImmutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    /** Utilise ton icône de notif (mets-en une : res/drawable/ic_notification.xml). Fallback sur l’icône d’app. */
    private fun resolveSmallIcon(): Int {
        val explicit = resources.getIdentifier("ic_notification", "drawable", packageName)
        return if (explicit != 0) explicit else applicationInfo.icon
    }

    /**
     * 📊 AUDIT: Enregistre la réception de notification dans Firestore pour metrics/debug
     */
    private fun auditNotificationReceived(remoteMessage: RemoteMessage) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.d(TAG, "📊 Audit notification skipped - user non connecté")
            return
        }

        try {
            val db = FirebaseFirestore.getInstance()
            val auditData = mapOf(
                "userId" to user.uid,
                "messageId" to (remoteMessage.messageId ?: "unknown"),
                "receivedAt" to FieldValue.serverTimestamp(),
                "dataKeys" to remoteMessage.data.keys.toList(),
                "hasNotificationPayload" to (remoteMessage.notification != null),
                "type" to (remoteMessage.data["type"] ?: "unknown"),
                "questionId" to remoteMessage.data["questionId"],
                "appInForeground" to isAppInForeground(),
                "deviceInfo" to mapOf(
                    "platform" to "android",
                    "appVersion" to BuildConfig.VERSION_NAME,
                    "buildNumber" to BuildConfig.VERSION_CODE
                )
            )

            // Collection audit similaire à celle utilisée côté backend pour security_events
            db.collection("notification_audit")
                .add(auditData)
                .addOnSuccessListener { documentRef ->
                    Log.d(TAG, "📊 Audit notification enregistré: ${documentRef.id}")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "⚠️ Échec audit notification", e)
                    // Pas critique - ne pas bloquer le flow normal
                }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Exception audit notification", e)
            // Pas critique - ne pas bloquer le flow normal
        }
    }

}
