package com.love2love.core.push

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Équivalent Android de FCMService (Swift) :
 * - Écoute l'état d'auth Firebase
 * - Récupère/rafraîchit le token FCM
 * - Sauvegarde dans Firestore avec deviceInfo
 * - Abonne/désabonne aux topics
 * - Vérifie/demande la permission notifications (Android 13+)
 *
 * NB localisation : ce module n'utilise pas de chaînes localisées. Pour tout texte UI,
 * utilise `context.getString(R.string.xxx)` via strings.xml comme demandé.
 */
object FcmService {
    private const val TAG = "FCMService"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private var currentUserId: String? = null
    private var authListenerAdded = false

    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    /** À appeler une seule fois (ex: Application.onCreate). */
    fun setup(context: Context) {
        if (_isConfigured.value) return
        Log.d(TAG, "🔔 FCMService: Initialisation / configuration")

        if (!authListenerAdded) {
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    Log.d(TAG, "🔔 FCMService: Utilisateur connecté")
                    currentUserId = user.uid
                    // Petite latence inutile côté Android : on peut directement demander le token
                    requestTokenAndSave(context)
                } else {
                    Log.d(TAG, "🔔 FCMService: Utilisateur déconnecté")
                    currentUserId = null
                    _fcmToken.value = null
                }
            }
            authListenerAdded = true
        }

        _isConfigured.value = true
        Log.d(TAG, "🔔 FCMService: Configuration terminée")
    }

    /** Demande le token FCM si la permission notifications est accordée, puis sauvegarde dans Firestore. */
    fun requestTokenAndSave(context: Context) {
        Log.d(TAG, "🔔 FCMService: Demande de token FCM")

        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "❌ FCMService: Notifications non autorisées - impossible d'obtenir token FCM")
            return
        }

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "❌ FCMService: Erreur récupération token FCM", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                if (token.isNullOrBlank()) {
                    Log.e(TAG, "❌ FCMService: Token FCM null/vide")
                    return@addOnCompleteListener
                }
                Log.d(TAG, "✅ FCMService: Token FCM reçu - ${token.take(20)}...")
                _fcmToken.value = token
                saveTokenToFirestore(context, token)
            }
    }

    /** Rafraîchir explicitement le token côté client (relance la récupération + sauvegarde). */
    fun refreshToken(context: Context) {
        Log.d(TAG, "🔔 FCMService: Rafraîchissement token FCM (client)")
        requestTokenAndSave(context)
    }

    /** Enregistre le token sous /users/{uid} avec merge pour ne rien écraser. */
    private fun saveTokenToFirestore(context: Context, token: String) {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            Log.e(TAG, "❌ FCMService: Pas d'utilisateur connecté")
            return
        }

        Log.d(TAG, "🔔 FCMService: Sauvegarde token FCM pour utilisateur connecté (merge)")

        val appVersion = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }

        val data = hashMapOf(
            "fcmToken" to token,
            "tokenUpdatedAt" to FieldValue.serverTimestamp(),
            "deviceInfo" to hashMapOf(
                "platform" to "Android",
                "appVersion" to appVersion,
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        )

        db.collection("users").document(userId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "✅ FCMService: Token sauvegardé avec succès") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ FCMService: Erreur sauvegarde token", e) }
    }

    /** Abonnement / désabonnement topic */
    fun subscribeToTopic(topic: String) {
        Log.d(TAG, "🔔 FCMService: Abonnement au topic: $topic")
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnSuccessListener { Log.d(TAG, "✅ FCMService: Abonné au topic: $topic") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ FCMService: Erreur abonnement topic", e) }
    }

    fun unsubscribeFromTopic(topic: String) {
        Log.d(TAG, "🔔 FCMService: Désabonnement du topic: $topic")
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnSuccessListener { Log.d(TAG, "✅ FCMService: Désabonné du topic: $topic") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ FCMService: Erreur désabonnement topic", e) }
    }

    /** Android 13+ : permission runtime POST_NOTIFICATIONS + état global des notifications. */
    fun hasNotificationPermission(context: Context): Boolean {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!enabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    /** À appeler depuis une Activity pour demander la permission (Android 13+). */
    fun requestNotificationPermission(activity: Activity, requestCode: Int = 1001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    requestCode
                )
            }
        }
    }

    /** Utilisé par le service lorsque Google rafraîchit le token. */
    internal fun onNewTokenFromService(context: Context, token: String) {
        Log.d(TAG, "🔔 FCMService: Nouveau token reçu (service) - ${token.take(20)}...")
        _fcmToken.value = token
        saveTokenToFirestore(context, token)
    }
}

/**
 * Service FCM pour capter onNewToken (équivalent de MessagingDelegate.didReceiveRegistrationToken).
 * Déclare ce service dans AndroidManifest.xml avec l'intent-filter FCM.
 */
class AppFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmService.onNewTokenFromService(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // Ici tu peux construire une notification personnalisée si besoin.
        Log.d("FCMService", "📩 Message reçu: ${remoteMessage.data}")
    }
}
