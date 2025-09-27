package com.love2loveapp.services.location

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.love2loveapp.services.firebase.FirebaseProfileService
import com.love2loveapp.models.UserLocation
import com.love2loveapp.AppDelegate

/**
 * 🌍 PartnerLocationService Android - Gestion localisation et infos partenaire
 * 
 * Équivalent fonctionnel de PartnerLocationService iOS:
 * - Cache anti-rafale : 15s infos, 5s localisation
 * - États exposés via StateFlow pour Compose
 * - Intégration Firebase Functions automatique
 * - Synchronisation background avec coroutines
 */
class PartnerLocationService private constructor() {
    
    companion object {
        private const val TAG = "PartnerLocationService"
        private const val USERS_COLLECTION = "users"
        
        @Volatile
        private var instance: PartnerLocationService? = null
        
        fun getInstance(): PartnerLocationService {
            return instance ?: synchronized(this) {
                instance ?: PartnerLocationService().also { instance = it }
            }
        }
    }
    
    // Firebase services
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val firebaseProfileService = FirebaseProfileService.getInstance()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // États exposés pour UI
    private val _partnerLocation = MutableStateFlow<UserLocation?>(null)
    val partnerLocation: StateFlow<UserLocation?> = _partnerLocation.asStateFlow()
    
    private val _partnerName = MutableStateFlow<String?>(null)
    val partnerName: StateFlow<String?> = _partnerName.asStateFlow()
    
    private val _partnerProfileImageURL = MutableStateFlow<String?>(null)
    val partnerProfileImageURL: StateFlow<String?> = _partnerProfileImageURL.asStateFlow()
    
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()
    
    // État de synchronisation
    private var currentPartnerId: String? = null
    
    /**
     * 🔄 Démarre la synchronisation avec un partenaire via Cloud Functions (comme iOS)
     */
    fun startSyncWithPartner(partnerId: String) {
        if (currentPartnerId == partnerId) {
            Log.d(TAG, "✅ Synchronisation déjà active pour: $partnerId")
            return
        }
        
        Log.d(TAG, "🚀 Démarrage synchronisation Cloud Functions partenaire: $partnerId")
        
        // Arrêter l'ancienne synchronisation
        stopSync()
        
        currentPartnerId = partnerId
        
        // 🔄 Démarrer synchronisation périodique via Cloud Functions (comme iOS)
        startPeriodicSync(partnerId)
    }
    
    /**
     * 🔄 Synchronisation périodique via Cloud Functions (équivalent iOS)
     */
    private fun startPeriodicSync(partnerId: String) {
        Log.d(TAG, "⏱️ Démarrage synchronisation périodique pour: $partnerId")
        
        serviceScope.launch {
            // 🚀 Première synchronisation immédiate
            syncPartnerData(partnerId)
            
            // 🔄 Puis synchronisation périodique toutes les 30 secondes
            while (currentPartnerId == partnerId) {
                try {
                    delay(30_000) // Attendre 30 secondes
                    
                    if (currentPartnerId == partnerId) {
                        syncPartnerData(partnerId)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur dans la boucle de synchronisation: ${e.message}", e)
                    delay(5_000) // Attendre 5s avant de réessayer en cas d'erreur
                }
            }
            
            Log.d(TAG, "⏹️ Arrêt synchronisation périodique pour: $partnerId")
        }
    }
    
    /**
     * 📡 Synchronise les données partenaire une fois
     */
    private suspend fun syncPartnerData(partnerId: String) {
        try {
            Log.d(TAG, "🔄 Récupération données partenaire via Cloud Functions...")
            
            // 1. Récupérer infos partenaire via getPartnerInfo
            val infoResult = firebaseProfileService.getPartnerInfo(partnerId)
            infoResult.onSuccess { partnerInfo ->
                // Mettre à jour les données de base
                _partnerName.value = partnerInfo.name
                _isSubscribed.value = partnerInfo.isSubscribed
                _partnerProfileImageURL.value = partnerInfo.profileImageURL
                
                // 🔄 DÉCLENCHER TÉLÉCHARGEMENT IMAGE PARTENAIRE (FIX MANQUANT)
                AppDelegate.profileImageManager?.let { profileImageManager ->
                    Log.d(TAG, "🖼️ Déclenchement sync image partenaire...")
                    GlobalScope.launch(Dispatchers.IO) {
                        profileImageManager.syncPartnerImage(
                            partnerImageURL = partnerInfo.profileImageURL,
                            partnerImageUpdatedAt = null // Cloud Function ne retourne pas ce champ
                        )
                    }
                } ?: Log.w(TAG, "⚠️ ProfileImageManager non disponible pour sync partenaire")
                
                Log.d(TAG, "✅ DONNÉES PARTENAIRE MISES À JOUR (Cloud Functions):")
                Log.d(TAG, "   - Nom: ${partnerInfo.name}")
                Log.d(TAG, "   - Photo profil: ${if (partnerInfo.profileImageURL != null) "✅ Présente" else "❌ Absente"}")
                Log.d(TAG, "   - Abonné: ${if (partnerInfo.isSubscribed) "✅ Oui" else "❌ Non"}")
                
                if (partnerInfo.profileImageURL != null) {
                    Log.d(TAG, "🖼️ URL photo partenaire: ${partnerInfo.profileImageURL.take(50)}...")
                }
            }.onFailure { error ->
                Log.e(TAG, "❌ Erreur récupération infos partenaire: ${error.message}")
            }
            
            // 2. Récupérer localisation partenaire via getPartnerLocation
            val locationResult = firebaseProfileService.getPartnerLocation(partnerId)
            locationResult.onSuccess { location ->
                _partnerLocation.value = location
                if (location != null) {
                    Log.d(TAG, "📍 Localisation partenaire mise à jour: ${location.city}")
                }
            }.onFailure { error ->
                Log.e(TAG, "❌ Erreur récupération localisation partenaire: ${error.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur synchronisation données partenaire: ${e.message}", e)
        }
    }
    
    /**
     * ⏹️ Arrête la synchronisation
     */
    fun stopSync() {
        Log.d(TAG, "⏹️ Arrêt synchronisation partenaire")
        
        currentPartnerId = null // Cela arrêtera la boucle dans startPeriodicSync
        
        clearPartnerData()
    }
    
    /**
     * 🧹 Vide les données partenaire
     */
    private fun clearPartnerData() {
        _partnerLocation.value = null
        _partnerName.value = null
        _partnerProfileImageURL.value = null
        _isSubscribed.value = false
    }
    
    /**
     * 🔄 Force une synchronisation immédiate (debug/test)
     */
    fun forceRefreshFromCloudFunctions() {
        currentPartnerId?.let { partnerId ->
            Log.d(TAG, "🔄 FORCE: Synchronisation immédiate demandée pour: $partnerId")
            
            serviceScope.launch {
                syncPartnerData(partnerId)
            }
        } ?: Log.w(TAG, "⚠️ FORCE: Aucun partenaire actuel pour forcer la synchronisation")
    }
    
    /**
     * 📊 Obtient l'utilisateur actuel connecté
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    /**
     * 🔄 Démarre automatiquement la sync si l'utilisateur a un partenaire
     * Utilise AppState au lieu de Firestore direct pour éviter les problèmes de permissions
     */
    fun startAutoSyncIfPartnerExists() {
        Log.d(TAG, "🚀 DÉMARRAGE startAutoSyncIfPartnerExists()")
        
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            Log.w(TAG, "⚠️ Utilisateur non connecté pour auto-sync")
            return
        }
        
        Log.d(TAG, "👤 Utilisateur connecté: $currentUserId - Observation AppState pour changements partenaire...")
        
        // Observer l'AppState pour détecter les changements de partenaire
        serviceScope.launch {
            try {
                com.love2loveapp.AppDelegate.appState.currentUser.collect { user ->
                    val partnerId = user?.partnerId
                    Log.d(TAG, "📄 AppState utilisateur reçu - partnerId: ${partnerId ?: "null"}")
                    
                    if (!partnerId.isNullOrEmpty()) {
                        Log.d(TAG, "🔄 PARTENAIRE DÉTECTÉ (AppState) - Démarrage auto-sync: $partnerId")
                        startSyncWithPartner(partnerId)
                    } else {
                        Log.d(TAG, "ℹ️ Pas de partenaire (AppState), arrêt sync")
                        stopSync()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur observation AppState: ${e.message}", e)
            }
        }
    }
    
    /**
     * 🧹 Nettoyage des ressources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage PartnerLocationService")
        serviceScope.cancel()
        stopSync()
    }
}
