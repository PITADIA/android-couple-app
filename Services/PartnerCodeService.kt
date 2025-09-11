// PartnerCodeService.kt
@file:Suppress("unused")

package com.love2love.data

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.analytics.ktx.analytics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Date
import java.util.Locale

class PartnerCodeService private constructor(
    appContext: Context
) {
    companion object {
        @Volatile private var INSTANCE: PartnerCodeService? = null
        fun getInstance(context: Context): PartnerCodeService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PartnerCodeService(context.applicationContext).also { INSTANCE = it }
            }

        private const val TAG = "PartnerCodeService"
        private const val PREFS = "partner_prefs"
        private const val KEY_LAST_COUPLE_ID = "lastCoupleId"
    }

    // --- Android/Resources
    private val context = appContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Firebase
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val auth get() = FirebaseAuth.getInstance()
    private val analytics = Firebase.analytics

    // --- State (Compose/VM friendly)
    val generatedCode = MutableStateFlow<String?>(null)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val isConnected = MutableStateFlow(false)
    val partnerInfo = MutableStateFlow<PartnerInfo?>(null)

    // --- Event bus (remplace NotificationCenter iOS)
    private val _events = MutableSharedFlow<PartnerEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    data class PartnerInfo(
        val id: String,
        val name: String,
        val connectedAt: Date,
        val isSubscribed: Boolean
    )

    enum class ConnectionContext(val rawValue: String) {
        Onboarding("onboarding"),
        Settings("settings"),
        Other("other")
    }

    object ConnectionConfig {
        fun introFlagsKey(coupleId: String) = "introFlags_$coupleId"
    }

    // MARK: Génération d'un code partenaire (24h, conformité Apple)
    suspend fun generatePartnerCode(): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Début génération code")
        val currentUser = auth.currentUser ?: run {
            errorMessage.value = context.getString(R.string.user_not_connected)
            return@withContext null
        }

        isLoading.value = true
        errorMessage.value = null

        try {
            // 1) Code récent < 24h ?
            val yesterday = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            val recent = db.collection("partnerCodes")
                .whereEqualTo("userId", currentUser.uid)
                .whereGreaterThan("createdAt", Timestamp(yesterday))
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
                .await()

            if (!recent.isEmpty) {
                val code = recent.documents.first().id
                generatedCode.value = code
                isLoading.value = false
                Log.d(TAG, "Code récent existant retourné")
                return@withContext code
            }

            // 2) Legacy actif (sans expiresAt) → migrer avec 72h de grâce
            val legacy = db.collection("partnerCodes")
                .whereEqualTo("userId", currentUser.uid)
                .whereEqualTo("isActive", true)
                .limit(3)
                .get()
                .await()

            legacy.documents.firstOrNull()?.let { doc ->
                val data = doc.data ?: emptyMap()
                if (!data.containsKey("expiresAt")) {
                    val grace = Date(System.currentTimeMillis() + 72L * 60 * 60 * 1000)
                    doc.reference.update(
                        mapOf(
                            "expiresAt" to Timestamp(grace),
                            "migrationGracePeriod" to true,
                            "rotationReason" to "apple_compliance_migration"
                        )
                    ).await()
                    generatedCode.value = doc.id
                    isLoading.value = false
                    Log.d(TAG, "Code legacy migré (72h), renvoyé")
                    return@withContext doc.id
                }
            }

            // 3) Désactiver codes expirés
            legacy.documents.forEach { d ->
                val exp = (d.get("expiresAt") as? Timestamp)?.toDate()
                if (exp != null && exp.before(Date())) {
                    d.reference.update("isActive", false).await()
                }
            }

            // 4) Générer code unique
            var attempts = 0
            var code: String
            do {
                code = String.format(Locale.US, "%08d", (10000000..99999999).random())
                attempts++
                val exists = db.collection("partnerCodes").document(code).get().await().exists
                if (!exists) break
            } while (attempts < 10)

            if (attempts >= 10) {
                errorMessage.value = context.getString(R.string.error_generating_code)
                isLoading.value = false
                return@withContext null
            }

            // 5) Créer le code (24h)
            val now = Date()
            db.collection("partnerCodes").document(code).set(
                mapOf(
                    "userId" to currentUser.uid,
                    "createdAt" to Timestamp(now),
                    "expiresAt" to Timestamp(Date(now.time + 24 * 60 * 60 * 1000)),
                    "isActive" to true,
                    "connectedPartnerId" to null,
                    "rotationReason" to "apple_compliance"
                )
            ).await()

            generatedCode.value = code
            isLoading.value = false
            Log.d(TAG, "Code créé avec succès")
            return@withContext code
        } catch (e: Exception) {
            Log.e(TAG, "Erreur génération code", e)
            errorMessage.value = context.getString(R.string.error_generating_code_with_reason, e.localizedMessage ?: "")
            isLoading.value = false
            null
        }
    }

    // MARK: Connexion avec un code partenaire
    suspend fun connectWithPartnerCode(
        code: String,
        connectContext: ConnectionContext = ConnectionContext.Onboarding
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "connectWithPartnerCode: ${connectContext.rawValue}")
        val currentUser = auth.currentUser ?: run {
            errorMessage.value = context.getString(R.string.user_not_connected)
            return@withContext false
        }

        isLoading.value = true
        errorMessage.value = null

        try {
            // (Optionnel) analytics équivalent
            analytics.logEvent("connect_start", null)

            val result = functions
                .getHttpsCallable("connectPartners")
                .call(mapOf("partnerCode" to code))
                .await()

            val data = (result.data as? Map<*, *>) ?: emptyMap<Any, Any>()
            val success = data["success"] as? Boolean ?: false
            if (!success) {
                errorMessage.value = context.getString(R.string.connection_error)
                isLoading.value = false
                return@withContext false
            }

            val partnerName = (data["partnerName"] as? String).orEmpty().ifEmpty { context.getString(R.string.partner_default_name) }
            val subscriptionInherited = data["subscriptionInherited"] as? Boolean ?: false

            analytics.logEvent("partenaire_connecte", null)

            // Mettre à jour UI
            isConnected.value = true
            partnerInfo.value = PartnerInfo(
                id = "", // mis à jour après refresh
                name = partnerName,
                connectedAt = Date(),
                isSubscribed = subscriptionInherited
            )
            isLoading.value = false

            // Forcer un refresh simple pour récupérer partnerId (équiv. iOS forceRefreshUserData)
            val userDoc = db.collection("users").document(currentUser.uid).get().await()
            val partnerId = userDoc.getString("partnerId").orEmpty()

            if (partnerId.isNotBlank()) {
                resetIntroFlagsForNewCouple(partnerId)
                notifyConnectionSuccess(partnerName, subscriptionInherited, connectContext)
            } else {
                // léger fallback async/retardé
                notifyConnectionSuccess(partnerName, subscriptionInherited, connectContext)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur connexion", e)
            // Mapping simple des codes Functions → message
            errorMessage.value = e.localizedMessage?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.connection_error)
            isLoading.value = false
            false
        }
    }

    // MARK: Reset des flags d’intro (SharedPreferences)
    private fun resetIntroFlagsForNewCouple(partnerId: String) {
        val me = auth.currentUser?.uid ?: return
        val newCoupleId = buildCoupleId(me, partnerId)
        val oldCoupleId = prefs.getString(KEY_LAST_COUPLE_ID, null)

        if (newCoupleId != oldCoupleId) {
            // Remplace IntroFlags par un simple flag JSON “par défaut”
            val key = ConnectionConfig.introFlagsKey(newCoupleId)
            val defaultFlags = JSONObject().apply {
                put("welcomeShown", false)
                put("tipsShown", false)
            }.toString()
            prefs.edit()
                .putString(key, defaultFlags)
                .putString(KEY_LAST_COUPLE_ID, newCoupleId)
                .apply()

            _events.tryEmit(PartnerEvent.IntroFlagsDidReset)
            Log.d(TAG, "Intro flags reset pour couple: $newCoupleId")
        } else {
            Log.d(TAG, "Même couple, pas de reset")
        }
    }

    // MARK: Notifications “connexion réussie” (SharedFlow)
    private fun notifyConnectionSuccess(
        partnerName: String,
        subscriptionInherited: Boolean,
        connectContext: ConnectionContext
    ) {
        analytics.logEvent("connect_success", null)

        if (subscriptionInherited) {
            _events.tryEmit(PartnerEvent.SubscriptionInherited)
        }

        _events.tryEmit(
            PartnerEvent.PartnerConnected(
                partnerName = partnerName,
                isSubscribed = subscriptionInherited,
                context = connectContext.rawValue
            )
        )

        // Compat: déclencher l’affichage d’un message de succès immédiat
        _events.tryEmit(PartnerEvent.ShouldShowConnectionSuccess(partnerName))
        Log.d(TAG, "Notifications de connexion envoyées (${connectContext.rawValue})")
    }

    // MARK: Vérifier la connexion existante
    suspend fun checkExistingConnection() = withContext(Dispatchers.IO) {
        Log.d(TAG, "checkExistingConnection")
        val currentUser = auth.currentUser ?: return@withContext

        try {
            val userDoc = db.collection("users").document(currentUser.uid).get().await()
            val partnerId = userDoc.getString("partnerId").orEmpty()

            if (partnerId.isNotBlank()) {
                // via Cloud Function: getPartnerInfo
                val result = functions
                    .getHttpsCallable("getPartnerInfo")
                    .call(mapOf("partnerId" to partnerId))
                    .await()

                val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
                val ok = data["success"] as? Boolean ?: false

                if (ok) {
                    val info = data["partnerInfo"] as? Map<*, *> ?: emptyMap<String, Any>()
                    val name = (info["name"] as? String).orEmpty().ifEmpty { context.getString(R.string.partner_default_name) }
                    val sub = info["isSubscribed"] as? Boolean ?: false
                    val connectedAt = (userDoc.get("partnerConnectedAt") as? Timestamp)?.toDate() ?: Date()

                    isConnected.value = true
                    partnerInfo.value = PartnerInfo(
                        id = partnerId,
                        name = name,
                        connectedAt = connectedAt,
                        isSubscribed = sub
                    )
                }

            } else {
                Log.d(TAG, "Aucun partenaire connecté")
            }

            // Code existant éventuel
            val codeSnap = db.collection("partnerCodes")
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            generatedCode.value = codeSnap.documents.firstOrNull()?.id

        } catch (e: Exception) {
            Log.e(TAG, "Erreur checkExistingConnection", e)
        }
    }

    // MARK: Déconnexion sécurisée
    suspend fun disconnectPartner(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "disconnectPartner")
        if (auth.currentUser == null) return@withContext false

        isLoading.value = true
        errorMessage.value = null

        try {
            val res = functions.getHttpsCallable("disconnectPartners")
                .call(emptyMap<String, Any>())
                .await()
            val data = res.data as? Map<*, *> ?: emptyMap<String, Any>()
            val ok = data["success"] as? Boolean ?: false
            if (!ok) {
                errorMessage.value = context.getString(R.string.disconnect_error)
                isLoading.value = false
                return@withContext false
            }

            isConnected.value = false
            partnerInfo.value = null
            isLoading.value = false

            // vider lastCoupleId
            prefs.edit().remove(KEY_LAST_COUPLE_ID).apply()

            _events.tryEmit(PartnerEvent.PartnerDisconnected)
            Log.d(TAG, "Déconnexion réussie")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur disconnect", e)
            errorMessage.value = context.getString(R.string.disconnect_error_with_reason, e.localizedMessage ?: "")
            isLoading.value = false
            false
        }
    }

    // MARK: Suppression des codes lors de la suppression du compte
    suspend fun deleteUserPartnerCode() = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext
        try {
            // Supprimer mes codes
            val mine = db.collection("partnerCodes")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()
            mine.documents.forEach { it.reference.delete().await() }

            // Libérer les codes où j’étais connecté
            val connected = db.collection("partnerCodes")
                .whereEqualTo("connectedPartnerId", currentUser.uid)
                .get()
                .await()
            connected.documents.forEach { d ->
                d.reference.update(
                    mapOf(
                        "connectedPartnerId" to null,
                        "connectedAt" to FieldValue.delete()
                    )
                ).await()
            }
            Log.d(TAG, "Codes partenaire supprimés/libérés")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur deleteUserPartnerCode", e)
        }
    }

    // MARK: Helpers
    fun clearGeneratedCode() { generatedCode.value = null }
    fun clearError() { errorMessage.value = null }

    // MARK: Messages de connexion en attente
    suspend fun checkForPendingConnectionMessage() = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext
        try {
            val userDoc = db.collection("users").document(currentUser.uid).get().await()
            val hasUnread = userDoc.getBoolean("hasUnreadPartnerConnection") ?: false
            val partnerId = userDoc.getString("partnerId").orEmpty()

            if (hasUnread && partnerId.isNotBlank()) {
                val result = functions
                    .getHttpsCallable("getPartnerInfo")
                    .call(mapOf("partnerId" to partnerId))
                    .await()

                val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
                val ok = data["success"] as? Boolean ?: false
                if (ok) {
                    val info = data["partnerInfo"] as? Map<*, *> ?: emptyMap<String, Any>()
                    val partnerName = (info["name"] as? String).orEmpty().ifEmpty { context.getString(R.string.partner_default_name) }

                    // Marquer comme lu
                    db.collection("users").document(currentUser.uid)
                        .update("hasUnreadPartnerConnection", FieldValue.delete()).await()

                    _events.tryEmit(PartnerEvent.ShouldShowConnectionSuccess(partnerName))
                    Log.d(TAG, "Message de connexion en attente affiché")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur checkForPendingConnectionMessage", e)
        }
    }

    // --- Utils
    private fun buildCoupleId(a: String, b: String): String =
        listOf(a, b).sorted().joinToString("_")
}

// Événements (équivalents Notification.Name iOS)
sealed class PartnerEvent {
    data object SubscriptionInherited : PartnerEvent()
    data class PartnerConnected(
        val partnerName: String,
        val isSubscribed: Boolean,
        val context: String
    ) : PartnerEvent()
    data object PartnerDisconnected : PartnerEvent()
    data class ShouldShowConnectionSuccess(val partnerName: String) : PartnerEvent()
    data object IntroFlagsDidReset : PartnerEvent()
}
