package com.love2loveapp.data.repository

import android.location.Location
import android.util.Log
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.services.location.LocationService
import com.love2loveapp.core.services.firebase.FirebaseUserService
import com.love2loveapp.domain.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * 🗺️ LocationRepositoryImpl - Implémentation Repository Pattern pour Localisation
 * 
 * Responsabilités :
 * - Gestion des données de localisation (GPS + Firebase)
 * - Calcul de distance entre partenaires
 * - Synchronisation position avec Firebase
 * - État réactif avec Flow/StateFlow
 * 
 * Architecture : Repository + Real-time Updates
 */
class LocationRepositoryImpl(
    private val locationService: LocationService,
    private val firebaseUserService: FirebaseUserService
) : LocationRepository {
    
    companion object {
        private const val TAG = "LocationRepositoryImpl"
    }
    
    // === State Management ===
    private val _currentLocationFlow = MutableStateFlow<Result<Location?>>(Result.Loading)
    override val currentLocationFlow: StateFlow<Result<Location?>> = _currentLocationFlow.asStateFlow()
    
    private val _partnerLocationFlow = MutableStateFlow<Result<Location?>>(Result.Loading)
    override val partnerLocationFlow: StateFlow<Result<Location?>> = _partnerLocationFlow.asStateFlow()
    
    private val _distanceFlow = MutableStateFlow<Result<Double?>>(Result.Loading)
    override val distanceFlow: StateFlow<Result<Double?>> = _distanceFlow.asStateFlow()
    
    private val _isTrackingFlow = MutableStateFlow(false)
    override val isTrackingFlow: StateFlow<Boolean> = _isTrackingFlow.asStateFlow()
    
    init {
        Log.d(TAG, "🗺️ Initialisation LocationRepositoryImpl")
        initializeLocationObservation()
    }
    
    // === Repository Implementation ===
    
    override suspend fun getCurrentLocation(): Result<Location?> {
        return try {
            Log.d(TAG, "📍 Récupération position actuelle")
            
            val locationResult = locationService.getCurrentLocation()
            
            when (locationResult) {
                is Result.Success -> {
                    _currentLocationFlow.value = locationResult
                    
                    // Synchroniser avec Firebase si position valide
                    locationResult.data?.let { location ->
                        syncLocationToFirebase(location)
                    }
                    
                    Log.d(TAG, "✅ Position récupérée: ${locationResult.data}")
                    locationResult
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur récupération position: ${locationResult.exception.message}")
                    _currentLocationFlow.value = locationResult
                    locationResult
                }
                is Result.Loading -> {
                    _currentLocationFlow.value = locationResult
                    locationResult
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception récupération position", e)
            val result = Result.Error(AppException.LocationError("Failed to get current location", e))
            _currentLocationFlow.value = result
            result
        }
    }
    
    override suspend fun startLocationTracking(): Result<Boolean> {
        return try {
            Log.d(TAG, "🎯 Démarrage tracking localisation")
            
            val result = locationService.startLocationUpdates { location ->
                // Callback pour chaque mise à jour de position
                _currentLocationFlow.value = Result.Success(location)
                
                // Synchroniser avec Firebase
                syncLocationToFirebase(location)
                
                // Calculer distance avec partenaire
                calculateDistanceWithPartner()
            }
            
            when (result) {
                is Result.Success -> {
                    _isTrackingFlow.value = result.data
                    Log.d(TAG, "✅ Tracking localisation démarré")
                    result
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur démarrage tracking: ${result.exception.message}")
                    _isTrackingFlow.value = false
                    result
                }
                is Result.Loading -> result
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception démarrage tracking", e)
            _isTrackingFlow.value = false
            Result.Error(AppException.LocationError("Failed to start location tracking", e))
        }
    }
    
    override suspend fun stopLocationTracking(): Result<Boolean> {
        return try {
            Log.d(TAG, "⏹️ Arrêt tracking localisation")
            
            val result = locationService.stopLocationUpdates()
            
            when (result) {
                is Result.Success -> {
                    _isTrackingFlow.value = false
                    Log.d(TAG, "✅ Tracking localisation arrêté")
                    result
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur arrêt tracking: ${result.exception.message}")
                    result
                }
                is Result.Loading -> result
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception arrêt tracking", e)
            Result.Error(AppException.LocationError("Failed to stop location tracking", e))
        }
    }
    
    override suspend fun getPartnerLocation(): Result<Location?> {
        return try {
            Log.d(TAG, "💑 Récupération position partenaire")
            
            val result = firebaseUserService.getPartnerLocation()
            
            when (result) {
                is Result.Success -> {
                    _partnerLocationFlow.value = result
                    Log.d(TAG, "✅ Position partenaire récupérée: ${result.data}")
                    result
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur récupération position partenaire: ${result.exception.message}")
                    _partnerLocationFlow.value = result
                    result
                }
                is Result.Loading -> {
                    _partnerLocationFlow.value = result
                    result
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception récupération position partenaire", e)
            val result = Result.Error(AppException.LocationError("Failed to get partner location", e))
            _partnerLocationFlow.value = result
            result
        }
    }
    
    override suspend fun calculatePartnerDistance(): Result<Double?> {
        return try {
            Log.d(TAG, "📏 Calcul distance avec partenaire")
            
            // 1. Récupérer position actuelle
            val currentLocationResult = getCurrentLocation()
            if (currentLocationResult !is Result.Success || currentLocationResult.data == null) {
                return Result.Error(AppException.LocationError("Current location not available"))
            }
            
            // 2. Récupérer position partenaire
            val partnerLocationResult = getPartnerLocation()
            if (partnerLocationResult !is Result.Success || partnerLocationResult.data == null) {
                return Result.Error(AppException.LocationError("Partner location not available"))
            }
            
            // 3. Calculer distance
            val currentLocation = currentLocationResult.data
            val partnerLocation = partnerLocationResult.data
            
            val distance = currentLocation.distanceTo(partnerLocation).toDouble()
            
            val result = Result.Success(distance)
            _distanceFlow.value = result
            
            Log.d(TAG, "✅ Distance calculée: ${distance}m")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception calcul distance", e)
            val result = Result.Error(AppException.LocationError("Failed to calculate partner distance", e))
            _distanceFlow.value = result
            result
        }
    }
    
    override suspend fun updateLocationPermission(granted: Boolean): Result<Boolean> {
        return try {
            Log.d(TAG, "🔐 Mise à jour permission localisation: $granted")
            
            val result = locationService.updatePermissionStatus(granted)
            
            when (result) {
                is Result.Success -> {
                    if (granted && result.data) {
                        // Permission accordée, démarrer tracking si nécessaire
                        startLocationTracking()
                    } else {
                        // Permission refusée, arrêter tracking
                        stopLocationTracking()
                    }
                    
                    Log.d(TAG, "✅ Permission localisation mise à jour")
                    result
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur mise à jour permission: ${result.exception.message}")
                    result
                }
                is Result.Loading -> result
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception mise à jour permission", e)
            Result.Error(AppException.LocationError("Failed to update location permission", e))
        }
    }
    
    // === Private Methods ===
    
    /**
     * Initialiser l'observation des changements de localisation
     */
    private fun initializeLocationObservation() {
        // Observer les mises à jour de position du service
        locationService.locationUpdatesFlow
            .map { location ->
                if (location != null) {
                    Result.Success(location)
                } else {
                    Result.Error(AppException.LocationError("Location update is null"))
                }
            }
            .collect { locationResult ->
                _currentLocationFlow.value = locationResult
                
                // Auto-calcul distance si position valide
                if (locationResult is Result.Success) {
                    calculateDistanceWithPartner()
                }
            }
    }
    
    /**
     * Synchroniser position avec Firebase
     */
    private suspend fun syncLocationToFirebase(location: Location) {
        try {
            Log.d(TAG, "☁️ Synchronisation position avec Firebase")
            
            val result = firebaseUserService.updateUserLocation(location)
            
            when (result) {
                is Result.Success -> {
                    Log.d(TAG, "✅ Position synchronisée avec Firebase")
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur synchronisation Firebase: ${result.exception.message}")
                }
                is Result.Loading -> {
                    // En cours
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception synchronisation Firebase", e)
        }
    }
    
    /**
     * Calculer distance avec partenaire (async)
     */
    private suspend fun calculateDistanceWithPartner() {
        try {
            calculatePartnerDistance()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception calcul distance auto", e)
        }
    }
    
    // === Utility Methods ===
    
    override fun isLocationTrackingActive(): Boolean {
        return _isTrackingFlow.value
    }
    
    override fun getLastKnownLocation(): Location? {
        return when (val result = _currentLocationFlow.value) {
            is Result.Success -> result.data
            else -> null
        }
    }
    
    override fun getLastKnownDistance(): Double? {
        return when (val result = _distanceFlow.value) {
            is Result.Success -> result.data
            else -> null
        }
    }
}
