package com.love2love.sync

import android.util.Log
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Kotlin/Android port of PartnerSubscriptionSyncService (Swift).
 *
 * • Keeps a Firestore listener on current user doc to detect partnerId changes.
 * • When a valid partnerId appears, triggers a Cloud Function: "syncPartnerSubscriptions".
 * • Emits a local event when a subscription is inherited from the partner.
 * • Uses coroutines + Tasks.await() (add dependency: kotlinx-coroutines-play-services).
 * • Logs are "safe" (no raw UIDs printed).
 *
 * NOTE about localization: this service does not display UI strings.
 * For other parts that previously used `.xcstrings` like
 *   `challengeKey.localized(tableName: "DailyChallenges")`
 * on Android you must use `context.getString(R.string.challengeKey)`
 * or the Compose equivalent via `LocalContext.current`.
 */
object PartnerSubscriptionSyncService {

    private const val TAG = "PartnerSubscriptionSync"

    // Firebase
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    // Coroutines
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Listeners
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var userListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null // kept for symmetry; not used (we sync via CF)

    // Event stream for UI to observe (subscription inherited, etc.)
    private val _subscriptionSharedEvents = MutableSharedFlow<PartnerSubscriptionSharedEvent>(extraBufferCapacity = 1)
    val subscriptionSharedEvents = _subscriptionSharedEvents.asSharedFlow()

    init {
        startAuthObserver()
    }

    // --- Public API

    fun restart() {
        Log.d(TAG, "Manual restart")
        stopAllListeners()
        startListeningForUser()
    }

    fun clear() {
        Log.d(TAG, "Clearing service")
        authListener?.let { auth.removeAuthStateListener(it) }
        authListener = null
        stopAllListeners()
    }

    // --- Internals

    private fun startAuthObserver() {
        if (authListener != null) return
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user: FirebaseUser? = firebaseAuth.currentUser
            if (user != null) {
                Log.d(TAG, "User reconnected — restarting listeners")
                scope.launch {
                    delay(1_000)
                    startListeningForUser()
                }
            } else {
                Log.w(TAG, "User signed out — stopping listeners")
                stopAllListeners()
            }
        }
        auth.addAuthStateListener(authListener!!)
    }

    private fun startListeningForUser() {
        val currentUser = auth.currentUser ?: run {
            Log.w(TAG, "User not authenticated, stopping listeners")
            stopAllListeners()
            return
        }

        // Stop previous
        stopAllListeners()

        userListener = db.collection("users")
            .document(currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "User listener error: ${error.message}", error)
                    if (error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.w(TAG, "Permission error — checking auth")
                        handleAuthenticationError()
                    }
                    return@addSnapshotListener
                }

                val data = snapshot?.data
                if (data == null) {
                    Log.w(TAG, "No user data found")
                    return@addSnapshotListener
                }

                val partnerId = (data["partnerId"] as? String)?.trim().orEmpty()
                if (partnerId.isNotEmpty()) {
                    Log.d(TAG, "Valid partnerId found (length=${partnerId.length})")
                    startListeningForPartner(partnerId)
                } else {
                    Log.d(TAG, "No valid partner — stopping partner listener")
                    stopListeningForPartner()
                }
            }
    }

    private fun handleAuthenticationError() {
        Log.d(TAG, "Handling auth error")
        stopAllListeners()

        if (auth.currentUser == null) {
            Log.w(TAG, "User signed out — service idle")
        } else {
            Log.d(TAG, "Restarting listeners after auth error")
            scope.launch {
                delay(2_000)
                startListeningForUser()
            }
        }
    }

    private fun stopAllListeners() {
        userListener?.remove(); userListener = null
        stopListeningForPartner()
    }

    private fun startListeningForPartner(partnerId: String) {
        val pid = partnerId.trim()
        if (pid.isEmpty()) {
            Log.e(TAG, "Empty/invalid partnerId: '$partnerId'")
            stopListeningForPartner()
            return
        }

        // Stop previous partner listener if any (not used in this port)
        stopListeningForPartner()

        // Do NOT listen to partner doc directly — sync through Cloud Function
        Log.d(TAG, "Initial sync with partner (length=${pid.length})")

        val currentUser = auth.currentUser ?: return
        scope.launch(Dispatchers.IO) {
            // small delay to avoid race conditions with concurrent writes
            delay(2_000)
            syncSubscriptionViaCloudFunction(currentUser.uid, pid)
        }
    }

    private fun stopListeningForPartner() {
        partnerListener?.remove()
        partnerListener = null
    }

    private suspend fun syncSubscriptionViaCloudFunction(userId: String, partnerId: String) {
        val pid = partnerId.trim()
        if (pid.isEmpty()) {
            Log.e(TAG, "Empty/invalid partnerId: '$partnerId'")
            return
        }

        try {
            Log.d(TAG, "Sync via Cloud Function — with partner (length=${pid.length})")
            val data = hashMapOf(
                "partnerId" to pid
            )

            val result = functions
                .getHttpsCallable("syncPartnerSubscriptions")
                .call(data)
                .await()

            val map = result.data as? Map<*, *>
            val success = map?.get("success") as? Boolean ?: false
            if (success) {
                Log.d(TAG, "Sync success")
                val inherited = map["subscriptionInherited"] as? Boolean ?: false
                val fromPartnerName = map["fromPartnerName"] as? String

                if (inherited) {
                    // Analytics
                    Firebase.analytics.logEvent("abonnement_partage_partenaire", null)
                    Log.d(TAG, "Analytics event: abonnement_partage_partenaire")

                    // Emit local event to UI
                    _subscriptionSharedEvents.tryEmit(
                        PartnerSubscriptionSharedEvent(
                            partnerId = pid,
                            fromPartnerName = fromPartnerName ?: "Partenaire"
                        )
                    )
                }
            } else {
                Log.e(TAG, "Sync failed (success=false)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Cloud Function sync error: ${t.message}", t)
        }
    }

    data class PartnerSubscriptionSharedEvent(
        val partnerId: String,
        val fromPartnerName: String
    )
}
