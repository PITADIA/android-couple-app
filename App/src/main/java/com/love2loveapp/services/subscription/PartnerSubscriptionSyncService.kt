package com.love2loveapp.services.subscription

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🤝 PartnerSubscriptionSyncService - Synchronisation automatique des abonnements
 * 
 * Équivalent de PartnerSubscriptionSyncService iOS avec logique complète de partage.
 * Gère la synchronisation temps réel des abonnements entre partenaires connectés.
 */
class PartnerSubscriptionSyncService private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "PartnerSubscriptionSync"
        private const val USERS_COLLECTION = "users"
        private const val SHARING_LOGS_COLLECTION = "subscription_sharing_logs"
        
        @Volatile
        private var instance: PartnerSubscriptionSyncService? = null
        
        fun getInstance(context: Context): PartnerSubscriptionSyncService {
            return instance ?: synchronized(this) {
                instance ?: PartnerSubscriptionSyncService(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
    
    // Services Firebase
    private val firestore = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Service de notifications
    private val notificationService = SubscriptionNotificationService.getInstance(context)
    
    // Coroutines
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Listeners Firebase
    private var userListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null
    
    // États observables
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()
    
    /**
     * 🎧 Démarrer l'écoute des changements d'abonnement pour l'utilisateur actuel
     * Équivalent iOS startListeningForUser()
     */
    fun startListeningForUser() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "⚠️ Aucun utilisateur authentifié - impossible de démarrer l'écoute")
            return
        }
        
        stopAllListeners() // Arrêter les listeners existants
        
        Log.d(TAG, "🎧 Démarrage écoute changements abonnement pour: [USER_MASKED]")
        
        userListener = firestore.collection(USERS_COLLECTION)
            .document(currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener utilisateur: ${error.message}")
                    return@addSnapshotListener
                }
                
                val data = snapshot?.data ?: return@addSnapshotListener
                val partnerId = data["partnerId"] as? String
                
                Log.d(TAG, "📱 Changement détecté pour utilisateur: [USER_MASKED]")
                Log.d(TAG, "  - partnerId: [PARTNER_ID_MASKED]")
                Log.d(TAG, "  - isSubscribed: ${data["isSubscribed"]}")
                Log.d(TAG, "  - subscriptionType: ${data["subscriptionType"]}")
                
                if (!partnerId.isNullOrEmpty()) {
                    // Synchroniser avec le partenaire
                    Log.d(TAG, "🔄 Déclenchement synchronisation avec partenaire: [PARTNER_ID_MASKED]")
                    syncSubscriptionsWithPartner(currentUser.uid, partnerId)
                }
            }
        
        _isListening.value = true
        Log.d(TAG, "✅ Service d'écoute démarré")
    }
    
    /**
     * 🔄 Synchronisation des abonnements avec le partenaire
     * Équivalent iOS syncSubscriptionsWithPartner()
     */
    private fun syncSubscriptionsWithPartner(userId: String, partnerId: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "🔄 Synchronisation abonnements: [USER_ID_MASKED] ↔ [PARTNER_ID_MASKED]")
                
                val data = hashMapOf(
                    "partnerId" to partnerId
                )
                
                val result = functions.getHttpsCallable("syncPartnerSubscriptions")
                    .call(data)
                    .await()
                
                @Suppress("UNCHECKED_CAST")
                val resultData = result.data as? Map<String, Any>
                val success = resultData?.get("success") as? Boolean ?: false
                
                if (success) {
                    Log.d(TAG, "✅ Synchronisation abonnements réussie")
                    
                    // Vérifier si abonnement hérité
                    val inherited = resultData?.get("subscriptionInherited") as? Boolean ?: false
                    val fromPartnerName = resultData?.get("fromPartnerName") as? String
                    
                    val syncResult = SyncResult.Success(
                        inherited = inherited,
                        fromPartnerName = fromPartnerName
                    )
                    
                    _lastSyncResult.value = syncResult
                    
                    if (inherited && !fromPartnerName.isNullOrEmpty()) {
                        Log.d(TAG, "🎉 Abonnement hérité détecté de: $fromPartnerName")
                        
                        // Analytics et notifications
                        handleSubscriptionInherited(fromPartnerName)
                    }
                } else {
                    Log.w(TAG, "⚠️ Synchronisation abonnements échouée")
                    _lastSyncResult.value = SyncResult.Failure("Synchronisation échouée")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur synchronisation Cloud Function: ${e.message}", e)
                _lastSyncResult.value = SyncResult.Failure(e.message ?: "Erreur inconnue")
            }
        }
    }
    
    /**
     * 🎉 Gestion de l'héritage d'abonnement détecté
     * Équivalent iOS handleSubscriptionInherited()
     */
    private fun handleSubscriptionInherited(partnerName: String) {
        Log.d(TAG, "🎉 Gestion héritage abonnement de: $partnerName")
        
        // Analytics tracking (à implémenter si nécessaire)
        // Analytics.logEvent("abonnement_partage_partenaire")
        
        // Notification système Android
        notificationService.showSubscriptionInheritedNotification(partnerName)
        
        // Notifier les composants de l'app
        // TODO: Utiliser EventBus ou similar pour notifier l'UI si nécessaire
        Log.d(TAG, "📢 Notification héritage envoyée pour: $partnerName")
    }
    
    /**
     * 🤝 Gestion du partage d'abonnement vers un partenaire
     * (appelée quand l'utilisateur actuel partage son abonnement)
     */
    private fun handleSubscriptionSharedToPartner(partnerName: String) {
        Log.d(TAG, "🤝 Gestion partage vers partenaire: $partnerName")
        
        // Notification de confirmation de partage
        notificationService.showSubscriptionSharedNotification(partnerName)
        
        Log.d(TAG, "📢 Notification partage envoyée vers: $partnerName")
    }
    
    /**
     * 💔 Gestion de la perte d'abonnement partagé
     * (appelée quand le partenaire perd son abonnement et que le partage s'arrête)
     */
    private fun handleSubscriptionLost(partnerName: String) {
        Log.d(TAG, "💔 Gestion perte abonnement partagé de: $partnerName")
        
        // Notification de perte d'abonnement
        notificationService.showSubscriptionLostNotification(partnerName)
        
        Log.d(TAG, "📢 Notification perte envoyée pour: $partnerName")
    }
    
    /**
     * ⏹️ Arrêter tous les listeners
     * Équivalent iOS stopAllListeners()
     */
    fun stopAllListeners() {
        Log.d(TAG, "⏹️ Arrêt de tous les listeners")
        
        userListener?.remove()
        partnerListener?.remove()
        
        userListener = null
        partnerListener = null
        
        _isListening.value = false
    }
    
    /**
     * 🧹 Nettoyage du service
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage PartnerSubscriptionSyncService")
        
        stopAllListeners()
        serviceScope.cancel()
        
        // Reset instance
        instance = null
    }
    
    /**
     * 📊 Résultat de synchronisation
     * Remplacé sealed class par abstract class pour compatibilité production Android
     */
    abstract class SyncResult {
        data class Success(
            val inherited: Boolean,
            val fromPartnerName: String? = null
        ) : SyncResult()
        
        data class Failure(val error: String) : SyncResult()
    }
}
