package com.love2loveapp.core.repository

import android.app.Activity
import android.location.Location
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.model.UserLocation
import kotlinx.coroutines.flow.Flow

/**
 * Interface Repository pour la gestion de la localisation
 * Abstraction des services de localisation et chiffrement
 */
interface LocationRepository {
    
    // === Observable State ===
    val currentLocation: Flow<Result<UserLocation?>>
    val authorizationStatus: Flow<AuthorizationStatus>
    val isUpdatingLocation: Flow<Boolean>
    
    // === Permission Management ===
    suspend fun requestLocationPermission(activity: Activity): Result<Unit>
    suspend fun hasForegroundPermission(): Boolean
    suspend fun hasBackgroundPermission(): Boolean
    
    // === Location Operations ===
    suspend fun startLocationUpdates(): Result<Unit>
    suspend fun stopLocationUpdates(): Result<Unit>
    suspend fun getCurrentLocation(): Result<UserLocation>
    
    // === Data Persistence ===
    suspend fun saveLocationToFirebase(location: UserLocation): Result<Unit>
    suspend fun getLocationFromFirebase(userId: String): Result<UserLocation?>
    
    // === Encryption ===
    suspend fun encryptLocation(location: Location): Result<String>
    suspend fun decryptLocation(encryptedData: String): Result<Location>
}

/**
 * États d'autorisation de localisation
 */
enum class AuthorizationStatus {
    NOT_DETERMINED,
    DENIED_OR_RESTRICTED,
    GRANTED_FOREGROUND,
    GRANTED_BACKGROUND,
    UNKNOWN
}

/**
 * Extensions utilitaires
 */
suspend fun LocationRepository.requestPermissionIfNeeded(activity: Activity): Result<Unit> {
    return if (!hasForegroundPermission()) {
        requestLocationPermission(activity)
    } else {
        Result.success(Unit)
    }
}

suspend fun LocationRepository.startLocationUpdatesIfAuthorized(): Result<Unit> {
    return if (hasForegroundPermission()) {
        startLocationUpdates()
    } else {
        Result.error("Location permission not granted")
    }
}
