package com.love2loveapp.core.services.managers

import android.content.Context
import android.util.Log
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.services.partner.*
import com.love2loveapp.core.services.firebase.FirebaseUserService
import com.love2loveapp.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 👥 PartnerServiceManager - Gestionnaire Centralisé des Services Partenaire
 * 
 * Responsabilités :
 * - Coordination de tous les services liés au partenaire
 * - États réactifs pour connexion, localisation, abonnement
 * - Synchronisation automatique entre services
 * - Point d'entrée unique pour la logique partenaire
 * 
 * Architecture : Service Manager + Reactive Streams + Coordination
 */
class PartnerServiceManager(
    private val context: Context,
    private val firebaseUserService: FirebaseUserService
) {
    
    companion object {
        private const val TAG = "PartnerServiceManager"
    }
    
    // === Coroutine Scope ===
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // === Services Partenaire ===
    private val partnerCodeService = PartnerCodeService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val partnerLocationService = PartnerLocationService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val partnerConnectionNotificationService = PartnerConnectionNotificationService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val partnerSubscriptionNotificationService = PartnerSubscriptionNotificationService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val partnerSubscriptionSyncService = PartnerSubscriptionSyncService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    // === États Réactifs ===
    private val _hasConnectedPartner = MutableStateFlow(false)
    val hasConnectedPartner: StateFlow<Boolean> = _hasConnectedPartner.asStateFlow()
    
    private val _partnerInfo = MutableStateFlow<Result<User?>>(Result.Loading())
    val partnerInfo: StateFlow<Result<User?>> = _partnerInfo.asStateFlow()
    
    private val _partnerLocation = MutableStateFlow<Result<com.love2loveapp.model.UserLocation?>>(Result.Loading())
    val partnerLocation: StateFlow<Result<com.love2loveapp.model.UserLocation?>> = _partnerLocation.asStateFlow()
    
    private val _partnerConnectionStatus = MutableStateFlow<PartnerConnectionStatus>(PartnerConnectionStatus.DISCONNECTED)
    val partnerConnectionStatus: StateFlow<PartnerConnectionStatus> = _partnerConnectionStatus.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        Log.d(TAG, "👥 Initialisation PartnerServiceManager")
        initializePartnerStreams()
    }
    
    // === Initialisation ===
    
    /**
     * Initialiser les flux réactifs des services partenaire
     */
    private fun initializePartnerStreams() {
        Log.d(TAG, "🌊 Configuration flux partenaire réactifs")
        
        // Observer changements de connexion partenaire
        observePartnerConnection()
        
        // Observer localisation partenaire
        observePartnerLocation()
        
        // Observer synchronisation abonnement
        observePartnerSubscriptionSync()
    }
    
    /**
     * Observer connexion partenaire
     */
    private fun observePartnerConnection() {
        combine(
            partnerCodeService.connectionStatus,
            partnerConnectionNotificationService.connectionUpdates
        ) { codeStatus, connectionUpdates ->
            // Combiner les états de connexion
            when {
                codeStatus.isConnected && connectionUpdates.hasActiveConnection -> PartnerConnectionStatus.CONNECTED
                codeStatus.isConnecting || connectionUpdates.isConnecting -> PartnerConnectionStatus.CONNECTING
                codeStatus.hasError || connectionUpdates.hasError -> PartnerConnectionStatus.ERROR
                else -> PartnerConnectionStatus.DISCONNECTED
            }
        }
        .onEach { status ->
            Log.d(TAG, "🔗 Partner connection status: ${status.name}")
            _partnerConnectionStatus.value = status
            _hasConnectedPartner.value = (status == PartnerConnectionStatus.CONNECTED)
        }
        .launchIn(scope)
    }
    
    /**
     * Observer localisation partenaire
     */
    private fun observePartnerLocation() {
        partnerLocationService.partnerLocation
            .onEach { locationResult ->
                Log.d(TAG, "📍 Partner location update: ${locationResult.javaClass.simpleName}")
                _partnerLocation.value = locationResult
            }
            .launchIn(scope)
    }
    
    /**
     * Observer synchronisation abonnement partenaire
     */
    private fun observePartnerSubscriptionSync() {
        partnerSubscriptionSyncService.syncStatus
            .onEach { syncResult ->
                Log.d(TAG, "💎 Partner subscription sync: ${syncResult.javaClass.simpleName}")
                
                // Mettre à jour info partenaire si sync réussie
                if (syncResult is Result.Success) {
                    loadPartnerInfo()
                }
            }
            .launchIn(scope)
    }
    
    // === Actions Partenaire ===
    
    /**
     * Connecter un partenaire via code
     */
    suspend fun connectPartner(partnerCode: String): Result<Unit> {
        Log.d(TAG, "🤝 Connexion partenaire via code: $partnerCode")
        _isLoading.value = true
        
        return try {
            val result = partnerCodeService.connectWithCode(partnerCode)
            
            when (result) {
                is Result.Success -> {
                    // Déclencher notification de connexion
                    partnerConnectionNotificationService.notifyPartnerConnected()
                    
                    // Commencer synchronisation localisation
                    partnerLocationService.startLocationSharing()
                    
                    // Synchroniser abonnements
                    partnerSubscriptionSyncService.syncSubscriptions()
                    
                    Result.Success(Unit)
                }
                is Result.Error -> result
                is Result.Loading -> Result.Loading()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur connexion partenaire", e)
            Result.Error(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Déconnecter le partenaire
     */
    suspend fun disconnectPartner(): Result<Unit> {
        Log.d(TAG, "💔 Déconnexion partenaire")
        _isLoading.value = true
        
        return try {
            // Arrêter partage localisation
            partnerLocationService.stopLocationSharing()
            
            // Notifier déconnexion
            partnerConnectionNotificationService.notifyPartnerDisconnected()
            
            // Déconnecter via code service
            val result = partnerCodeService.disconnect()
            
            if (result is Result.Success) {
                // Reset états
                _hasConnectedPartner.value = false
                _partnerInfo.value = Result.Success(null)
                _partnerLocation.value = Result.Success(null)
                _partnerConnectionStatus.value = PartnerConnectionStatus.DISCONNECTED
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur déconnexion partenaire", e)
            Result.Error(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Charger informations partenaire
     */
    suspend fun loadPartnerInfo(): Result<User?> {
        Log.d(TAG, "👤 Chargement info partenaire")
        _isLoading.value = true
        
        return try {
            val result = partnerCodeService.getPartnerInfo()
            _partnerInfo.value = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement info partenaire", e)
            val error = Result.Error<User?>(e)
            _partnerInfo.value = error
            error
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Générer code partenaire
     */
    suspend fun generatePartnerCode(): Result<String> {
        Log.d(TAG, "🔑 Génération code partenaire")
        return partnerCodeService.generateCode()
    }
    
    /**
     * Démarrer partage localisation
     */
    fun startLocationSharing() {
        Log.d(TAG, "📍 Démarrage partage localisation")
        partnerLocationService.startLocationSharing()
    }
    
    /**
     * Arrêter partage localisation
     */
    fun stopLocationSharing() {
        Log.d(TAG, "📍 Arrêt partage localisation")
        partnerLocationService.stopLocationSharing()
    }
    
    /**
     * Synchroniser abonnements avec partenaire
     */
    suspend fun syncSubscriptions(): Result<Unit> {
        Log.d(TAG, "💎 Synchronisation abonnements")
        return partnerSubscriptionSyncService.syncSubscriptions()
    }
    
    // === Getters Services ===
    
    /**
     * Accès aux services individuels si besoin
     */
    fun getPartnerCodeService() = partnerCodeService
    fun getPartnerLocationService() = partnerLocationService
    fun getPartnerConnectionNotificationService() = partnerConnectionNotificationService
    fun getPartnerSubscriptionNotificationService() = partnerSubscriptionNotificationService
    fun getPartnerSubscriptionSyncService() = partnerSubscriptionSyncService
    
    // === Debug ===
    
    /**
     * État de debug du PartnerServiceManager
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "hasConnectedPartner" to _hasConnectedPartner.value,
            "partnerConnectionStatus" to _partnerConnectionStatus.value.name,
            "partnerInfoLoaded" to (_partnerInfo.value is Result.Success),
            "partnerLocationAvailable" to (_partnerLocation.value is Result.Success),
            "isLoading" to _isLoading.value
        )
    }
}

/**
 * États de connexion partenaire
 */
enum class PartnerConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
