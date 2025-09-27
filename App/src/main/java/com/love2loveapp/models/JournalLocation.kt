package com.love2loveapp.models

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import kotlin.math.*

/**
 * 🗺️ JournalLocation - Modèle Localisation Événement Journal
 * Équivalent iOS JournalLocation
 * 
 * Représente une localisation GPS associée à un événement journal
 * avec coordonnées, adresse et informations géographiques.
 */
data class JournalLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null
) {

    companion object {
        private const val TAG = "JournalLocation"
        
        /**
         * 📥 Création depuis document Firestore
         */
        fun fromFirestore(data: Map<String, Any>): JournalLocation? {
            return try {
                val lat = data["latitude"] as? Double ?: return null
                val lng = data["longitude"] as? Double ?: return null

                JournalLocation(
                    latitude = lat,
                    longitude = lng,
                    address = data["address"] as? String,
                    city = data["city"] as? String,
                    country = data["country"] as? String
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur parsing JournalLocation: ${e.message}")
                null
            }
        }

        /**
         * 🔄 Création depuis coordonnées
         */
        fun fromCoordinate(
            latitude: Double,
            longitude: Double,
            address: String? = null,
            city: String? = null,
            country: String? = null
        ): JournalLocation {
            return JournalLocation(
                latitude = latitude,
                longitude = longitude,
                address = address,
                city = city,
                country = country
            )
        }
    }

    /**
     * 📍 Coordonnées pour Google Maps
     */
    val coordinate: com.google.android.gms.maps.model.LatLng
        get() = com.google.android.gms.maps.model.LatLng(latitude, longitude)

    /**
     * 📝 Nom d'affichage intelligent (équivalent iOS displayName)
     */
    val displayName: String
        get() = when {
            city != null && country != null -> "$city, $country"
            address != null -> address!!
            else -> "Localisation"
        }

    /**
     * 📤 Conversion vers Firestore
     */
    fun toFirestore(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "latitude" to latitude,
            "longitude" to longitude
        )
        address?.let { map["address"] = it }
        city?.let { map["city"] = it }
        country?.let { map["country"] = it }
        return map
    }

    /**
     * 📏 Distance vers une autre localisation (en mètres)
     */
    fun distanceTo(other: JournalLocation): Double {
        val R = 6371000.0 // Rayon de la Terre en mètres
        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val deltaLatRad = Math.toRadians(other.latitude - latitude)
        val deltaLngRad = Math.toRadians(other.longitude - longitude)

        val a = sin(deltaLatRad / 2).pow(2) + 
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLngRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    /**
     * 📊 Debug Info
     */
    override fun toString(): String {
        return "JournalLocation(lat=$latitude, lng=$longitude, display='$displayName')"
    }
}
