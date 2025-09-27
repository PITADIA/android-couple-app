package com.love2loveapp.models.widgets

import android.location.Location
import android.util.Log
import com.love2loveapp.models.UserLocation
import java.util.*
import kotlin.math.*

/**
 * 🌍 DistanceInfo - Modèle données distance partenaires
 * 
 * Équivalent Android du DistanceInfo iOS pour widgets :
 * - Calcul distance géographique entre partenaires
 * - Messages émotionnels rotatifs
 * - Gestion états (proche/loin/pas de localisation)
 * - Formatage distance lisible
 */
data class DistanceInfo(
    val distance: Double, // Distance en kilomètres
    val distanceUnit: DistanceUnit,
    val formattedDistance: String,
    val currentMessage: String,
    val messages: List<String>,
    val currentMessageIndex: Int = 0,
    val userLocation: UserLocation?,
    val partnerLocation: UserLocation?,
    val isLocationAvailable: Boolean,
    val lastUpdated: Date = Date(),
    val status: DistanceStatus
) {
    
    enum class DistanceUnit {
        KILOMETERS,
        MILES
    }
    
    enum class DistanceStatus {
        VERY_CLOSE,    // < 1 km
        CLOSE,         // 1-10 km 
        NEARBY,        // 10-100 km
        FAR,           // 100-1000 km
        VERY_FAR,      // > 1000 km
        NO_LOCATION    // Pas de localisation
    }
    
    companion object {
        private const val TAG = "DistanceInfo"
        
        // 🌍 Rayon de la Terre en kilomètres
        private const val EARTH_RADIUS_KM = 6371.0
        
        /**
         * 📍 Calculer distance entre deux partenaires
         * 
         * Méthode principale pour créer DistanceInfo depuis les localisations
         */
        fun calculateBetweenPartners(
            userLocation: UserLocation?,
            partnerLocation: UserLocation?,
            unit: DistanceUnit = DistanceUnit.KILOMETERS
        ): DistanceInfo {
            
            Log.d(TAG, "🌍 Calcul distance entre partenaires")
            Log.d(TAG, "📍 Utilisateur: ${if (userLocation != null) "Présent" else "Absent"}")
            Log.d(TAG, "📍 Partenaire: ${if (partnerLocation != null) "Présent" else "Absent"}")
            
            // Vérifier si les deux localisations sont disponibles
            if (userLocation == null || partnerLocation == null) {
                Log.w(TAG, "⚠️ Localisation manquante pour calcul distance")
                return createNoLocationDistance()
            }
            
            return try {
                // 🧮 CALCUL DISTANCE HAVERSINE
                val distance = calculateHaversineDistance(
                    userLocation.latitude,
                    userLocation.longitude,
                    partnerLocation.latitude,
                    partnerLocation.longitude
                )
                
                Log.d(TAG, "📏 Distance calculée: ${String.format("%.2f", distance)} km")
                
                // 📊 DÉTERMINER STATUT DISTANCE
                val status = determineDistanceStatus(distance)
                Log.d(TAG, "📊 Statut distance: $status")
                
                // 💬 GÉNÉRER MESSAGES ÉMOTIONNELS
                val messages = generateEmotionalMessages(status, distance)
                
                // 📝 FORMATAGE DISTANCE
                val formattedDistance = formatDistance(distance, unit)
                
                DistanceInfo(
                    distance = distance,
                    distanceUnit = unit,
                    formattedDistance = formattedDistance,
                    currentMessage = messages.firstOrNull() ?: "Vous êtes connectés 💕",
                    messages = messages,
                    currentMessageIndex = 0,
                    userLocation = userLocation,
                    partnerLocation = partnerLocation,
                    isLocationAvailable = true,
                    lastUpdated = Date(),
                    status = status
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur calcul distance: ${e.message}", e)
                createErrorDistance()
            }
        }
        
        /**
         * 🧮 Calculer distance Haversine entre deux points GPS
         * 
         * Formule précise pour calcul distance entre coordonnées géographiques
         */
        private fun calculateHaversineDistance(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            
            // Conversion degrés vers radians
            val lat1Rad = Math.toRadians(lat1)
            val lon1Rad = Math.toRadians(lon1)
            val lat2Rad = Math.toRadians(lat2)
            val lon2Rad = Math.toRadians(lon2)
            
            // Différences
            val deltaLat = lat2Rad - lat1Rad
            val deltaLon = lon2Rad - lon1Rad
            
            // Formule Haversine
            val a = sin(deltaLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
            val c = 2 * asin(sqrt(a))
            
            // Distance en kilomètres
            return EARTH_RADIUS_KM * c
        }
        
        /**
         * 📊 Déterminer statut distance selon kilomètres
         */
        private fun determineDistanceStatus(distanceKm: Double): DistanceStatus {
            return when {
                distanceKm < 1.0 -> DistanceStatus.VERY_CLOSE
                distanceKm < 10.0 -> DistanceStatus.CLOSE
                distanceKm < 100.0 -> DistanceStatus.NEARBY
                distanceKm < 1000.0 -> DistanceStatus.FAR
                else -> DistanceStatus.VERY_FAR
            }
        }
        
        /**
         * 💬 Générer messages émotionnels selon distance
         * 
         * Messages rotatifs pour widgets inspirés par la proximité
         */
        private fun generateEmotionalMessages(status: DistanceStatus, distance: Double): List<String> {
            return when (status) {
                DistanceStatus.VERY_CLOSE -> listOf(
                    "Vous êtes tout proches ! 💕",
                    "Quelques pas vous séparent 👫",
                    "Presque dans les bras l'un de l'autre 🤗",
                    "Si proche de votre cœur 💖"
                )
                
                DistanceStatus.CLOSE -> listOf(
                    "Vous êtes dans le même quartier 🏠",
                    "Quelques minutes vous séparent 💕",
                    "Un petit trajet et vous êtes ensemble 🚶",
                    "Proche de votre amour 💝"
                )
                
                DistanceStatus.NEARBY -> listOf(
                    "Dans la même région que votre cœur 🗺️",
                    "Une courte distance vous sépare 🚗",
                    "Pas si loin de votre partenaire 💕",
                    "Quelques kilomètres d'amour 💖",
                    "Proches dans le cœur, proches en distance 💝"
                )
                
                DistanceStatus.FAR -> listOf(
                    "Séparés mais unis par l'amour 💕",
                    "La distance n'efface pas les sentiments 💖",
                    "Loin des yeux, près du cœur 💝",
                    "Votre amour voyage plus vite que la distance 💌",
                    "Les kilomètres ne comptent pas face à l'amour ✨"
                )
                
                DistanceStatus.VERY_FAR -> listOf(
                    "Un amour plus grand que la distance 🌍",
                    "Votre connexion dépasse les frontières 💕",
                    "Séparés par l'espace, unis par le cœur 💖",
                    "L'amour ignore les distances 💝",
                    "Même à l'autre bout du monde, toujours ensemble 🌎💕"
                )
                
                DistanceStatus.NO_LOCATION -> listOf(
                    "Votre connexion est toujours là 💕",
                    "Unis par le cœur 💖",
                    "Distance inconnue, amour certain 💝"
                )
            }
        }
        
        /**
         * 📝 Formater distance en format lisible
         */
        private fun formatDistance(distanceKm: Double, unit: DistanceUnit): String {
            return when (unit) {
                DistanceUnit.KILOMETERS -> {
                    when {
                        distanceKm < 1.0 -> "${String.format("%.0f", distanceKm * 1000)} m"
                        distanceKm < 10.0 -> "${String.format("%.1f", distanceKm)} km"
                        distanceKm < 100.0 -> "${String.format("%.0f", distanceKm)} km"
                        else -> "${String.format("%.0f", distanceKm)} km"
                    }
                }
                
                DistanceUnit.MILES -> {
                    val distanceMiles = distanceKm * 0.621371
                    when {
                        distanceMiles < 0.1 -> "${String.format("%.0f", distanceMiles * 5280)} ft"
                        distanceMiles < 10.0 -> "${String.format("%.1f", distanceMiles)} mi"
                        else -> "${String.format("%.0f", distanceMiles)} mi"
                    }
                }
            }
        }
        
        /**
         * 🚫 Créer DistanceInfo pour absence de localisation
         */
        private fun createNoLocationDistance(): DistanceInfo {
            val messages = listOf(
                "Activez votre localisation 📍",
                "Partagez votre position avec votre partenaire 💕",
                "Connectés par le cœur 💖"
            )
            
            return DistanceInfo(
                distance = 0.0,
                distanceUnit = DistanceUnit.KILOMETERS,
                formattedDistance = "Position inconnue",
                currentMessage = messages.first(),
                messages = messages,
                currentMessageIndex = 0,
                userLocation = null,
                partnerLocation = null,
                isLocationAvailable = false,
                lastUpdated = Date(),
                status = DistanceStatus.NO_LOCATION
            )
        }
        
        /**
         * ❌ Créer DistanceInfo pour erreur
         */
        private fun createErrorDistance(): DistanceInfo {
            val messages = listOf(
                "Erreur calcul distance 💔",
                "Réessayez plus tard 🔄",
                "Toujours connectés 💕"
            )
            
            return DistanceInfo(
                distance = 0.0,
                distanceUnit = DistanceUnit.KILOMETERS,
                formattedDistance = "Erreur",
                currentMessage = messages.first(),
                messages = messages,
                currentMessageIndex = 0,
                userLocation = null,
                partnerLocation = null,
                isLocationAvailable = false,
                lastUpdated = Date(),
                status = DistanceStatus.NO_LOCATION
            )
        }
    }
    
    /**
     * 🔄 Obtenir le prochain message rotatif
     */
    fun getNextMessage(): DistanceInfo {
        val nextIndex = (currentMessageIndex + 1) % messages.size
        return copy(
            currentMessage = messages[nextIndex],
            currentMessageIndex = nextIndex
        )
    }
    
    /**
     * 🎯 Propriétés calculées utiles pour widgets
     */
    val isVeryClose: Boolean
        get() = status == DistanceStatus.VERY_CLOSE
    
    val isVeryFar: Boolean
        get() = status == DistanceStatus.VERY_FAR
    
    val distanceEmoji: String
        get() = when (status) {
            DistanceStatus.VERY_CLOSE -> "🤗"
            DistanceStatus.CLOSE -> "🏠"
            DistanceStatus.NEARBY -> "🚗"
            DistanceStatus.FAR -> "✈️"
            DistanceStatus.VERY_FAR -> "🌍"
            DistanceStatus.NO_LOCATION -> "📍"
        }
    
    val motivationalEmoji: String
        get() = when (status) {
            DistanceStatus.VERY_CLOSE -> "💕"
            DistanceStatus.CLOSE -> "💖"
            DistanceStatus.NEARBY -> "💝"
            DistanceStatus.FAR -> "💌"
            DistanceStatus.VERY_FAR -> "🌎💕"
            DistanceStatus.NO_LOCATION -> "💕"
        }
    
    override fun toString(): String {
        return "DistanceInfo(distance=$formattedDistance, status=$status, available=$isLocationAvailable)"
    }
}
