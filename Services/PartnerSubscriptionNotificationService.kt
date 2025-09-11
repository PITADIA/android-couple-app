package com.yourapp.services

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yourapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Service de notification des changements d'abonnement partenaire.
 * iOS: ObservableObject/@Published -> Android: StateFlow pour intégration Compose.
 */
object PartnerSubscriptionNotificationService {

    private const val TAG = "PartnerSubNotifService"

    // ---- State exposé à l’UI (Compose) ----
    private val _shouldShowSubscriptionInheritedMessage = MutableStateFlow(false)
    val shouldShowSubscriptionInheritedMessage = _shouldShowSubscriptionInheritedMessage.asStateFlow()

    private val _partnerName = MutableStateFlow("")
    val partnerName = _partnerName.asStateFlow()

    private val _shouldShowSubscriptionRevokedMessage = MutableStateFlow(false)
    val shouldShowSubscriptionRevokedMessage = _shouldShowSubscriptionRevokedMessage.asStateFlow()

    private val _revokedPartnerName = MutableStateFlow("")
    val revokedPartnerName = _revokedPartnerName.asStateFlow()

    // ---- Infra ----
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var partnerListener: ListenerRegistration? = null
    private lateinit var appContext: Context

    /**
     * À appeler une fois (ex: Application.onCreate ou LaunchedEffect dans l’écran root)
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        startListeningForPartnerSubscription()
    }

    /**
     * Démarre/Relance le listener Firestore sur le document de l’utilisateur courant.
     */
    fun startListeningForPartnerSubscription() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "Utilisateur non authentifié – listener non démarré.")
            return
        }

        // Stop ancien listener le cas échéant
        partnerListener?.remove()

        partnerListener = FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erreur listener: ${error.message}", error)
                    if (error.message?.contains("permission", ignoreCase = true) == true) {
                        Log.w(TAG, "Erreur de permissions – arrêt du listener")
                        partnerListener?.remove()
                        partnerListener = null
                    }
                    return@addSnapshotListener
                }

                val data = snapshot?.data ?: return@addSnapshotListener

                // Héritage d’abonnement (équiv. subscriptionSharedFrom + isSubscribed)
                val isSubscribed = (data["isSubscribed"] as? Boolean) ?: false
                val inheritedFrom = (data["subscriptionSharedFrom"] as? String)?.trim().orEmpty()
                if (inheritedFrom.isNotEmpty() && isSubscribed) {
                    scope.launch { handleSubscriptionInherited(inheritedFrom) }
                }

                // Révocation (isSubscribed == false && presence de subscriptionExpiredAt)
                val wasSubscribed = (data["isSubscribed"] as? Boolean) ?: false
                val hasExpiredFlag = data.containsKey("subscriptionExpiredAt")
                if (!wasSubscribed && hasExpiredFlag) {
                    scope.launch { handleSubscriptionRevoked() }
                }
            }
    }

    /**
     * Récupère le nom du partenaire qui a partagé l’abonnement et déclenche l’affichage.
     */
    private suspend fun handleSubscriptionInherited(partnerId: String) {
        val id = partnerId.trim()
        if (id.isEmpty()) {
            Log.e(TAG, "partnerId vide ou invalide: '$partnerId'")
            return
        }

        try {
            Log.d(TAG, "Récupération du nom partenaire: '$id'")
            val doc = FirebaseFirestore.getInstance()
                .collection("users")
                .document(id)
                .get()
                .await()

            val name = (doc.data?.get("name") as? String).orEmpty()
            if (name.isNotEmpty()) {
                _partnerName.value = name
                _shouldShowSubscriptionInheritedMessage.value = true
                Log.d(TAG, "Affichage message héritage de: $name")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur récupération nom partenaire", t)
        }
    }

    /**
     * Définit un nom générique via strings.xml et déclenche l’affichage.
     * (Remplace NSLocalizedString(...) côté iOS)
     */
    private suspend fun handleSubscriptionRevoked() {
        val generic = if (::appContext.isInitialized) {
            appContext.getString(R.string.generic_partner)
        } else {
            "Partenaire"
        }
        _revokedPartnerName.value = generic
        _shouldShowSubscriptionRevokedMessage.value = true
        Log.d(TAG, "Affichage message révocation")
    }

    // ---- Actions UI ----
    fun dismissInheritedMessage() {
        _shouldShowSubscriptionInheritedMessage.value = false
        _partnerName.value = ""
        Log.d(TAG, "Fermeture message héritage")
    }

    fun dismissRevokedMessage() {
        _shouldShowSubscriptionRevokedMessage.value = false
        _revokedPartnerName.value = ""
        Log.d(TAG, "Fermeture message révocation")
    }

    /**
     * À appeler à la déconnexion si tu veux libérer le listener.
     */
    fun clear() {
        partnerListener?.remove()
        partnerListener = null
    }
}
