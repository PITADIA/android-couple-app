package com.love2loveapp.models

import android.location.Location

/**
 * 📍 UserLocation - Modèle de localisation utilisateur
 * 
 * Équivalent Android de UserLocation iOS avec interopérabilité Android Location
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Conversion vers Android Location pour calculs de distance
     */
    fun toAndroidLocation(provider: String = "cached"): Location {
        return Location(provider).apply {
            latitude = this@UserLocation.latitude
            longitude = this@UserLocation.longitude
            time = this@UserLocation.lastUpdated
        }
    }
    
    /**
     * Nom d'affichage basé sur les données disponibles
     */
    val displayName: String
        get() = when {
            !city.isNullOrBlank() && !country.isNullOrBlank() -> "$city, $country"
            !address.isNullOrBlank() -> address
            else -> String.format("%.4f,%.4f", latitude, longitude)
        }
    
    /**
     * Distance en kilomètres vers une autre localisation
     */
    fun distanceKmTo(other: UserLocation): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            latitude, longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0].toDouble() / 1000.0
    }
}
