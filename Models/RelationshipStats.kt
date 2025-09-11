package com.love2loveapp.model

import java.util.*

/**
 * Statistiques de relation pour les widgets
 * Équivalent Kotlin du RelationshipStats Swift
 */
data class RelationshipStats(
    val startDate: Date,
    val daysTotal: Int,
    val formattedDuration: String,
    val countdownText: String,
    val daysToAnniversary: Int
) {
    companion object {
        /**
         * Crée des statistiques depuis une date de début de relation
         */
        fun fromStartDate(startDate: Date): RelationshipStats {
            val calendar = Calendar.getInstance()
            val today = calendar.time
            
            // Calculer les jours totaux
            val diffInMillis = today.time - startDate.time
            val daysTotal = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
            
            // Calculer la durée formatée
            val years = daysTotal / 365
            val remainingDays = daysTotal % 365
            val months = remainingDays / 30
            val days = remainingDays % 30
            
            val formattedDuration = when {
                years > 0 -> "$years an${if (years > 1) "s" else ""}, $months mois"
                months > 0 -> "$months mois, $days jour${if (days > 1) "s" else ""}"
                else -> "$days jour${if (days > 1) "s" else ""}"
            }
            
            // Calculer le countdown (années, mois, jours, heures, minutes, secondes)
            val countdownText = formatCountdown(diffInMillis)
            
            // Calculer les jours jusqu'au prochain anniversaire
            val nextAnniversary = getNextAnniversary(startDate)
            val daysToAnniversary = ((nextAnniversary.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
            
            return RelationshipStats(
                startDate = startDate,
                daysTotal = daysTotal,
                formattedDuration = formattedDuration,
                countdownText = countdownText,
                daysToAnniversary = daysToAnniversary
            )
        }
        
        private fun formatCountdown(diffInMillis: Long): String {
            val seconds = diffInMillis / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            
            val displayDays = days % 365
            val displayHours = hours % 24
            val displayMinutes = minutes % 60
            val displaySeconds = seconds % 60
            
            return String.format(
                "%03d:%02d:%02d:%02d",
                displayDays,
                displayHours,
                displayMinutes,
                displaySeconds
            )
        }
        
        private fun getNextAnniversary(startDate: Date): Date {
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            
            calendar.time = startDate
            calendar.set(Calendar.YEAR, currentYear)
            
            // Si l'anniversaire de cette année est passé, prendre celui de l'année prochaine
            if (calendar.time.before(Date())) {
                calendar.set(Calendar.YEAR, currentYear + 1)
            }
            
            return calendar.time
        }
    }
}
