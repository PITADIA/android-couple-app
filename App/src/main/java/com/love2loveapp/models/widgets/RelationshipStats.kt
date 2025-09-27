package com.love2loveapp.models.widgets

import android.util.Log
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 💕 RelationshipStats - Modèle données statistiques relation
 * 
 * Équivalent Android du RelationshipStats iOS pour widgets :
 * - Calcul jours ensemble depuis date début relation
 * - Formatage dates et anniversaires
 * - Sérialisation JSON pour SharedPreferences
 * - Fonctions utilitaires calcul temps
 */
data class RelationshipStats(
    val relationshipStartDate: Date,
    val daysTotal: Int,
    val monthsTotal: Int,
    val yearsTotal: Int,
    val nextAnniversaryDate: Date,
    val daysUntilNextAnniversary: Int,
    val formattedDuration: String,
    val isAnniversaryToday: Boolean = false,
    val lastUpdated: Date = Date()
) {
    
    companion object {
        private const val TAG = "RelationshipStats"
        
        /**
         * 🔄 Calculer statistiques relation depuis date de début
         * 
         * Méthode principale équivalente iOS pour générer toutes les stats
         * à partir de la date de début de relation
         */
        fun calculateFromStartDate(startDate: Date): RelationshipStats? {
            return try {
                val now = Date()
                
                // Vérifier que la date de début est dans le passé
                if (startDate.after(now)) {
                    Log.w(TAG, "⚠️ Date début relation dans le futur: $startDate")
                    return null
                }
                
                Log.d(TAG, "💕 Calcul stats relation depuis: $startDate")
                
                // 📅 CALCUL JOURS TOTAL
                val daysDifference = calculateDaysBetweenDates(startDate, now)
                Log.d(TAG, "📊 Jours ensemble: $daysDifference")
                
                // 📅 CALCUL MOIS ET ANNÉES
                val calendar = Calendar.getInstance()
                calendar.time = startDate
                val startYear = calendar.get(Calendar.YEAR)
                val startMonth = calendar.get(Calendar.MONTH)
                val startDay = calendar.get(Calendar.DAY_OF_MONTH)
                
                calendar.time = now
                val currentYear = calendar.get(Calendar.YEAR)
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
                
                // Calcul années complètes
                var yearsTotal = currentYear - startYear
                if (currentMonth < startMonth || (currentMonth == startMonth && currentDay < startDay)) {
                    yearsTotal--
                }
                
                // Calcul mois total (approximation)
                val monthsTotal = yearsTotal * 12 + when {
                    currentMonth >= startMonth -> currentMonth - startMonth
                    else -> 12 + currentMonth - startMonth
                }
                
                // 🎉 CALCUL PROCHAIN ANNIVERSAIRE
                val (nextAnniversaryDate, daysUntilAnniversary) = calculateNextAnniversary(startDate, now)
                
                // 🎊 VÉRIFIER SI ANNIVERSAIRE AUJOURD'HUI
                val isAnniversaryToday = daysUntilAnniversary == 0
                if (isAnniversaryToday) {
                    Log.d(TAG, "🎉 C'est votre anniversaire de relation aujourd'hui!")
                }
                
                // 📝 FORMATAGE DURÉE HUMAINE
                val formattedDuration = formatDurationHuman(daysDifference, monthsTotal, yearsTotal)
                Log.d(TAG, "💖 Durée formatée: $formattedDuration")
                
                RelationshipStats(
                    relationshipStartDate = startDate,
                    daysTotal = daysDifference,
                    monthsTotal = monthsTotal,
                    yearsTotal = yearsTotal,
                    nextAnniversaryDate = nextAnniversaryDate,
                    daysUntilNextAnniversary = daysUntilAnniversary,
                    formattedDuration = formattedDuration,
                    isAnniversaryToday = isAnniversaryToday,
                    lastUpdated = now
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur calcul statistiques relation: ${e.message}", e)
                null
            }
        }
        
        /**
         * 📊 Calculer nombre de jours entre deux dates
         * 
         * CORRECTION: Utilise Calendar.dateComponents comme iOS pour précision
         */
        private fun calculateDaysBetweenDates(startDate: Date, endDate: Date): Int {
            val calendar = Calendar.getInstance()
            
            // Convertir en LocalDate pour calcul précis (équivalent iOS dateComponents)
            calendar.time = startDate
            val startYear = calendar.get(Calendar.YEAR)
            val startMonth = calendar.get(Calendar.MONTH)
            val startDay = calendar.get(Calendar.DAY_OF_MONTH)
            
            calendar.time = endDate
            val endYear = calendar.get(Calendar.YEAR)
            val endMonth = calendar.get(Calendar.MONTH)
            val endDay = calendar.get(Calendar.DAY_OF_MONTH)
            
            // Calcul exact des jours (équivalent iOS dateComponents)
            val startLocalDate = java.time.LocalDate.of(startYear, startMonth + 1, startDay)
            val endLocalDate = java.time.LocalDate.of(endYear, endMonth + 1, endDay)
            
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startLocalDate, endLocalDate).toInt()
            
            Log.d(TAG, "📊 Calcul jours: $startLocalDate → $endLocalDate = $daysBetween jours")
            
            return maxOf(daysBetween, 0) // Jamais négatif
        }
        
        /**
         * 🎂 Calculer prochain anniversaire de relation
         * 
         * Trouve la prochaine occurrence de la date anniversaire
         */
        private fun calculateNextAnniversary(startDate: Date, currentDate: Date): Pair<Date, Int> {
            val calendar = Calendar.getInstance()
            calendar.time = startDate
            val startMonth = calendar.get(Calendar.MONTH)
            val startDay = calendar.get(Calendar.DAY_OF_MONTH)
            
            // Année courante pour l'anniversaire
            calendar.time = currentDate
            val currentYear = calendar.get(Calendar.YEAR)
            
            // Date anniversaire cette année
            calendar.set(currentYear, startMonth, startDay, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            val thisYearAnniversary = calendar.time
            
            // Si l'anniversaire de cette année est passé, prendre l'année suivante
            val nextAnniversary = if (thisYearAnniversary.before(currentDate) || thisYearAnniversary == currentDate) {
                calendar.set(currentYear + 1, startMonth, startDay, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.time
            } else {
                thisYearAnniversary
            }
            
            val daysUntil = calculateDaysBetweenDates(currentDate, nextAnniversary)
            
            return Pair(nextAnniversary, daysUntil)
        }
        
        /**
         * 📝 Formater durée en format humain lisible
         * 
         * Crée un texte descriptif de la durée (ex: "2 ans et 3 mois")
         */
        private fun formatDurationHuman(days: Int, months: Int, years: Int): String {
            return when {
                years > 0 -> {
                    val remainingMonths = months % 12
                    when {
                        remainingMonths > 0 -> "$years an${if (years > 1) "s" else ""} et $remainingMonths mois"
                        else -> "$years an${if (years > 1) "s" else ""}"
                    }
                }
                months > 0 -> "$months mois"
                days > 0 -> "$days jour${if (days > 1) "s" else ""}"
                else -> "Aujourd'hui"
            }
        }
        
        /**
         * 🔄 Créer stats vides pour état initial
         */
        fun empty(): RelationshipStats {
            val now = Date()
            return RelationshipStats(
                relationshipStartDate = now,
                daysTotal = 0,
                monthsTotal = 0,
                yearsTotal = 0,
                nextAnniversaryDate = now,
                daysUntilNextAnniversary = 365,
                formattedDuration = "Pas de relation configurée",
                isAnniversaryToday = false,
                lastUpdated = now
            )
        }
    }
    
    /**
     * 🎯 Propriétés calculées utiles pour widgets
     */
    val isRecentRelationship: Boolean
        get() = daysTotal < 30 // Moins d'un mois
    
    val isLongTermRelationship: Boolean
        get() = yearsTotal >= 1 // Plus d'un an
    
    val formattedNextAnniversary: String
        get() = when (daysUntilNextAnniversary) {
            0 -> "Aujourd'hui ! 🎉"
            1 -> "Demain"
            in 2..7 -> "Dans $daysUntilNextAnniversary jours"
            else -> "Dans $daysUntilNextAnniversary jours"
        }
    
    /**
     * 📊 Message motivant selon durée relation
     */
    val motivationalMessage: String
        get() = when {
            isAnniversaryToday -> "Joyeux anniversaire ! 🎉💕"
            daysUntilNextAnniversary == 1 -> "Votre anniversaire est demain ! 🎂"
            daysUntilNextAnniversary <= 7 -> "Votre anniversaire approche ! 💖"
            daysTotal < 30 -> "Nouvelle aventure ensemble ! 💕"
            daysTotal < 365 -> "Votre amour grandit chaque jour ! 💖"
            yearsTotal == 1 -> "Un an d'amour déjà ! 🎊"
            yearsTotal < 5 -> "Plusieurs années de bonheur ! 💝"
            else -> "Un amour qui dure ! 👑💕"
        }
    
    override fun toString(): String {
        return "RelationshipStats(days=$daysTotal, months=$monthsTotal, years=$yearsTotal, anniversary=${formattedNextAnniversary})"
    }
}
