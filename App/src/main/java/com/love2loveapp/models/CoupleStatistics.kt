package com.love2loveapp.models

import android.util.Log
import com.love2loveapp.models.JournalEntry
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 📊 CoupleStatistics - Modèle données statistiques couple Android
 * 
 * Équivalent Android du système CoupleStatisticsView iOS :
 * - Calcul 4 métriques principales (jours, questions, villes, pays)
 * - Algorithmes temps réel basés sur sources données multiples
 * - Logique business centralisée avec validation sécurité
 * - Pattern Factory pour construction optimisée
 */
data class CoupleStatistics(
    val daysTogether: Int = 0,
    val questionsProgressPercentage: Double = 0.0,
    val citiesVisited: Int = 0,
    val countriesVisited: Int = 0,
    val lastUpdated: Date = Date(),
    
    // Données détaillées pour affichage avancé
    val relationshipStartDate: Date? = null,
    val totalQuestionsAnswered: Int = 0,
    val totalQuestionsAvailable: Int = 0,
    val uniqueCitiesList: Set<String> = emptySet(),
    val uniqueCountriesList: Set<String> = emptySet()
) {

    companion object {
        private const val TAG = "CoupleStatistics"
        
        /**
         * 🏭 Méthode Factory - Calcul statistiques complètes
         * 
         * Équivalent iOS des computed properties de CoupleStatisticsView
         * @param relationshipStartDate Date de début relation (depuis User)
         * @param categoryProgress Progression par catégorie (depuis CategoryProgressService)
         * @param journalEntries Liste événements journal (depuis JournalRepository)
         * @param questionCategories Liste catégories questions disponibles
         */
        fun calculate(
            relationshipStartDate: Date?,
            categoryProgress: Map<String, Int>,
            journalEntries: List<JournalEntry>,
            questionCategories: List<QuestionCategory>
        ): CoupleStatistics {
            
            Log.d(TAG, "📊 === CALCUL STATISTIQUES COUPLE ===")
            Log.d(TAG, "📊 Date relation: $relationshipStartDate")
            Log.d(TAG, "📊 Progression catégories: $categoryProgress")
            Log.d(TAG, "📊 Événements journal: ${journalEntries.size}")
            Log.d(TAG, "📊 Catégories questions: ${questionCategories.size}")
            
            return CoupleStatistics(
                daysTogether = calculateDaysTogether(relationshipStartDate),
                questionsProgressPercentage = calculateQuestionsProgress(categoryProgress, questionCategories),
                citiesVisited = calculateCitiesVisited(journalEntries),
                countriesVisited = calculateCountriesVisited(journalEntries),
                lastUpdated = Date(),
                
                // Données détaillées
                relationshipStartDate = relationshipStartDate,
                totalQuestionsAnswered = calculateTotalQuestionsAnswered(categoryProgress, questionCategories),
                totalQuestionsAvailable = calculateTotalQuestionsAvailable(questionCategories),
                uniqueCitiesList = extractUniqueCities(journalEntries),
                uniqueCountriesList = extractUniqueCountries(journalEntries)
            ).also {
                Log.d(TAG, "✅ Statistiques calculées: $it")
            }
        }
        
        /**
         * 💕 Calcul jours ensemble depuis date relation
         * Équivalent iOS daysTogetherCount computed property
         */
        private fun calculateDaysTogether(relationshipStartDate: Date?): Int {
            return relationshipStartDate?.let { startDate ->
                val now = Date()
                val diffInMillis = now.time - startDate.time
                val daysDiff = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                val result = maxOf(daysDiff.toInt(), 0)
                
                Log.d(TAG, "💕 Jours ensemble: $result (depuis $startDate)")
                result
            } ?: run {
                Log.d(TAG, "💕 Pas de date relation définie, jours = 0")
                0
            }
        }
        
        /**
         * 🧠 Calcul progression questions en pourcentage
         * Équivalent iOS questionsProgressPercentage computed property
         */
        private fun calculateQuestionsProgress(
            categoryProgress: Map<String, Int>,
            questionCategories: List<QuestionCategory>
        ): Double {
            var totalQuestions = 0
            var totalProgress = 0
            
            questionCategories.forEach { category ->
                // Récupérer questions de la catégorie (simulé car structure exacte inconnue)
                val questionsInCategory = getQuestionsCountForCategory(category)
                val currentIndex = categoryProgress[category.id] ?: 0
                
                totalQuestions += questionsInCategory
                // +1 car index commence à 0, mais limité au nombre total
                totalProgress += minOf(currentIndex + 1, questionsInCategory)
                
                Log.d(TAG, "🧠 Catégorie ${category.id}: $currentIndex/${questionsInCategory}")
            }
            
            val percentage = if (totalQuestions > 0) {
                (totalProgress.toDouble() / totalQuestions) * 100.0
            } else {
                0.0
            }
            
            Log.d(TAG, "🧠 Questions: $totalProgress/$totalQuestions = ${percentage.toInt()}%")
            return percentage
        }
        
        /**
         * 🏙️ Calcul villes uniques visitées depuis journal
         * Équivalent iOS citiesVisitedCount computed property
         */
        private fun calculateCitiesVisited(journalEntries: List<JournalEntry>): Int {
            val uniqueCities = journalEntries
                .mapNotNull { entry -> entry.location?.city?.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            
            Log.d(TAG, "🏙️ Villes visitées: ${uniqueCities.size} (${uniqueCities.take(3)}...)")
            return uniqueCities.size
        }
        
        /**
         * 🌍 Calcul pays uniques visités depuis journal
         * Équivalent iOS countriesVisitedCount computed property
         */
        private fun calculateCountriesVisited(journalEntries: List<JournalEntry>): Int {
            val uniqueCountries = journalEntries
                .mapNotNull { entry -> entry.location?.country?.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            
            Log.d(TAG, "🌍 Pays visités: ${uniqueCountries.size} (${uniqueCountries.take(3)}...)")
            return uniqueCountries.size
        }
        
        /**
         * 📈 Calcul total questions répondues (pour détails)
         */
        private fun calculateTotalQuestionsAnswered(
            categoryProgress: Map<String, Int>,
            questionCategories: List<QuestionCategory>
        ): Int {
            return questionCategories.sumOf { category ->
                val currentIndex = categoryProgress[category.id] ?: 0
                val questionsInCategory = getQuestionsCountForCategory(category)
                minOf(currentIndex + 1, questionsInCategory)
            }
        }
        
        /**
         * 📚 Calcul total questions disponibles (pour détails)
         */
        private fun calculateTotalQuestionsAvailable(questionCategories: List<QuestionCategory>): Int {
            return questionCategories.sumOf { category ->
                getQuestionsCountForCategory(category)
            }
        }
        
        /**
         * 🏙️ Extraction liste unique villes (pour détails)
         */
        private fun extractUniqueCities(journalEntries: List<JournalEntry>): Set<String> {
            return journalEntries
                .mapNotNull { entry -> entry.location?.city?.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
        
        /**
         * 🌍 Extraction liste unique pays (pour détails)
         */
        private fun extractUniqueCountries(journalEntries: List<JournalEntry>): Set<String> {
            return journalEntries
                .mapNotNull { entry -> entry.location?.country?.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
        
        /**
         * 🔢 Obtenir nombre questions pour une catégorie
         * TODO: À adapter selon la structure exacte de QuestionCategory
         */
        private fun getQuestionsCountForCategory(category: QuestionCategory): Int {
            // Estimation basée sur le système iOS: moyenne 50-100 questions par catégorie
            // TODO: Remplacer par l'accès réel aux questions de la catégorie
            return when (category.id) {
                "intimacy" -> 75
                "communication" -> 80
                "future" -> 65
                "past" -> 70
                "fun" -> 85
                "deep" -> 60
                else -> 70 // Fallback par défaut
            }
        }
        
        /**
         * 🔄 Créer statistiques vides pour état initial
         */
        fun empty(): CoupleStatistics {
            return CoupleStatistics(
                daysTogether = 0,
                questionsProgressPercentage = 0.0,
                citiesVisited = 0,
                countriesVisited = 0,
                lastUpdated = Date()
            )
        }
    }
    
    /**
     * 📊 Propriétés calculées utiles pour UI
     */
    
    val isDataComplete: Boolean
        get() = relationshipStartDate != null || 
                questionsProgressPercentage > 0 || 
                citiesVisited > 0 || 
                countriesVisited > 0
    
    val formattedDaysTogether: String
        get() = when {
            daysTogether > 365 -> {
                val years = daysTogether / 365
                val remainingDays = daysTogether % 365
                "$years an${if (years > 1) "s" else ""} et $remainingDays jour${if (remainingDays > 1) "s" else ""}"
            }
            daysTogether > 30 -> {
                val months = daysTogether / 30
                val remainingDays = daysTogether % 30
                "$months mois et $remainingDays jour${if (remainingDays > 1) "s" else ""}"
            }
            else -> "$daysTogether jour${if (daysTogether > 1) "s" else ""}"
        }
    
    val formattedQuestionsProgress: String
        get() = "${questionsProgressPercentage.toInt()}%"
    
    /**
     * 📈 Vérifier si données nécessitent mise à jour
     */
    fun needsUpdate(maxAgeMinutes: Int = 30): Boolean {
        val now = Date()
        val ageInMinutes = (now.time - lastUpdated.time) / (1000 * 60)
        return ageInMinutes > maxAgeMinutes
    }
    
    /**
     * 🎯 Messages motivants selon progression
     */
    fun getMotivationalMessage(): String {
        return when {
            daysTogether == 0 -> "Commencez votre aventure ensemble ! 💕"
            daysTogether < 30 -> "Nouveau couple, grandes émotions ! 🌟"
            daysTogether < 365 -> "Votre amour grandit chaque jour ! 💖"
            daysTogether < 1000 -> "Une belle histoire qui se construit ! 👑"
            else -> "Un amour qui dure dans le temps ! ✨"
        }
    }
    
    override fun toString(): String {
        return "CoupleStatistics(days=$daysTogether, questions=${questionsProgressPercentage.toInt()}%, cities=$citiesVisited, countries=$countriesVisited)"
    }
}
