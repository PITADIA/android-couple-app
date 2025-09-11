package com.love2loveapp.model

/**
 * Informations de distance pour les widgets
 * Équivalent Kotlin du DistanceInfo Swift
 */
data class DistanceInfo(
    val distanceInMeters: Double,
    val formattedDistance: String,
    val messages: List<String>,
    val userCity: String?,
    val partnerCity: String?
) {
    companion object {
        /**
         * Crée une DistanceInfo depuis une distance en mètres
         */
        fun fromDistance(
            distanceInMeters: Double,
            userCity: String? = null,
            partnerCity: String? = null,
            locale: String = "fr"
        ): DistanceInfo {
            val formattedDistance = formatDistance(distanceInMeters, locale)
            val messages = generateMessages(userCity, partnerCity, formattedDistance)
            
            return DistanceInfo(
                distanceInMeters = distanceInMeters,
                formattedDistance = formattedDistance,
                messages = messages,
                userCity = userCity,
                partnerCity = partnerCity
            )
        }
        
        private fun formatDistance(distanceInMeters: Double, locale: String): String {
            return when {
                distanceInMeters < 1000 -> {
                    "${distanceInMeters.toInt()} m"
                }
                distanceInMeters < 100000 -> {
                    val km = distanceInMeters / 1000
                    if (locale == "en") {
                        val miles = km * 0.621371
                        String.format("%.1f mi", miles)
                    } else {
                        String.format("%.1f km", km)
                    }
                }
                else -> {
                    val km = distanceInMeters / 1000
                    if (locale == "en") {
                        val miles = km * 0.621371
                        "${miles.toInt()} mi"
                    } else {
                        "${km.toInt()} km"
                    }
                }
            }
        }
        
        private fun generateMessages(
            userCity: String?,
            partnerCity: String?,
            formattedDistance: String
        ): List<String> {
            val messages = mutableListOf<String>()
            
            // Message principal avec distance
            messages.add("Vous êtes à $formattedDistance l'un de l'autre")
            
            // Messages avec villes si disponibles
            if (userCity != null && partnerCity != null) {
                messages.add("$userCity ↔ $partnerCity")
                messages.add("Distance: $formattedDistance")
            }
            
            // Messages d'encouragement
            messages.add("L'amour n'a pas de distance 💕")
            messages.add("Bientôt réunis ❤️")
            
            return messages
        }
        
        /**
         * DistanceInfo par défaut quand les données ne sont pas disponibles
         */
        val placeholder = DistanceInfo(
            distanceInMeters = 0.0,
            formattedDistance = "? km",
            messages = listOf("Distance en cours de calcul..."),
            userCity = null,
            partnerCity = null
        )
    }
}
