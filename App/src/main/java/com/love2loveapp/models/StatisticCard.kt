package com.love2loveapp.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.love2loveapp.R

/**
 * 🎨 StatisticCard - Modèle données carte statistique individuelle
 * 
 * Équivalent Android de StatisticCardView iOS :
 * - Représente une carte avec valeur, titre, icône et couleurs thématiques
 * - Factory methods pour créer les 4 types de statistiques
 * - Design cohérent avec palette couleurs iOS
 */
data class StatisticCard(
    @StringRes val titleRes: Int,
    val value: String,
    @DrawableRes val iconRes: Int,
    val iconColor: Color,
    val backgroundColor: Color,
    val textColor: Color,
    val theme: StatisticTheme,
    val onClick: (() -> Unit)? = null
) {
    
    /**
     * 🎨 Thèmes visuels par type de statistique
     * Reproduit exactement les couleurs iOS du rapport
     */
    enum class StatisticTheme {
        LOVE,       // Jours ensemble - Rose/Rouge amour ❤️
        KNOWLEDGE,  // Questions - Orange connaissance 🧠 
        URBAN,      // Villes - Bleu urbain 🏙️
        GLOBAL      // Pays - Violet global 🌍
    }
    
    companion object {
        
        /**
         * 🏭 Factory Methods - Création cartes statistiques typées
         * Reproduit les couleurs exactes iOS du rapport technique
         */
        
        /**
         * 💕 Carte Jours Ensemble (Thème Amour)
         * Couleurs iOS: icône #feb5c8, fond #fedce3, texte #db3556
         */
        fun createDaysTogetherCard(
            days: Int,
            onClick: (() -> Unit)? = null
        ): StatisticCard {
            return StatisticCard(
                titleRes = R.string.days_together,
                value = days.toString(),
                iconRes = R.drawable.ic_heart, // TODO: Créer icône "jours" comme iOS
                iconColor = Color(0xFFfeb5c8), // Rose clair icône
                backgroundColor = Color(0xFFfedce3), // Rose pâle fond
                textColor = Color(0xFFdb3556), // Rose foncé texte
                theme = StatisticTheme.LOVE,
                onClick = onClick
            )
        }
        
        /**
         * 🧠 Carte Questions Répondues (Thème Connaissance)  
         * Couleurs iOS: icône #fed397, fond #fde9cf, texte #ffa229
         */
        fun createQuestionsAnsweredCard(
            percentage: Double,
            onClick: (() -> Unit)? = null
        ): StatisticCard {
            return StatisticCard(
                titleRes = R.string.questions_answered,
                value = "${percentage.toInt()}%",
                iconRes = R.drawable.ic_question, // TODO: Créer icône "qst" comme iOS
                iconColor = Color(0xFFfed397), // Orange clair icône
                backgroundColor = Color(0xFFfde9cf), // Orange pâle fond
                textColor = Color(0xFFffa229), // Orange texte
                theme = StatisticTheme.KNOWLEDGE,
                onClick = onClick
            )
        }
        
        /**
         * 🏙️ Carte Villes Visitées (Thème Urbain)
         * Couleurs iOS: icône #b0d6fe, fond #dbecfd, texte #0a85ff
         */
        fun createCitiesVisitedCard(
            cities: Int,
            onClick: (() -> Unit)? = null
        ): StatisticCard {
            return StatisticCard(
                titleRes = R.string.cities_visited,
                value = cities.toString(),
                iconRes = R.drawable.ic_city, // TODO: Créer icône "ville" comme iOS
                iconColor = Color(0xFFb0d6fe), // Bleu clair icône
                backgroundColor = Color(0xFFdbecfd), // Bleu pâle fond
                textColor = Color(0xFF0a85ff), // Bleu texte
                theme = StatisticTheme.URBAN,
                onClick = onClick
            )
        }
        
        /**
         * 🌍 Carte Pays Visités (Thème Global)
         * Couleurs iOS: icône #d1b3ff, fond #e8dcff, texte #7c3aed
         */
        fun createCountriesVisitedCard(
            countries: Int,
            onClick: (() -> Unit)? = null
        ): StatisticCard {
            return StatisticCard(
                titleRes = R.string.countries_visited,
                value = countries.toString(),
                iconRes = R.drawable.ic_globe, // TODO: Créer icône "pays" comme iOS
                iconColor = Color(0xFFd1b3ff), // Violet clair icône
                backgroundColor = Color(0xFFe8dcff), // Violet pâle fond
                textColor = Color(0xFF7c3aed), // Violet foncé texte
                theme = StatisticTheme.GLOBAL,
                onClick = onClick
            )
        }
        
        /**
         * 🏭 Factory générique depuis CoupleStatistics
         * Crée les 4 cartes avec les données et callbacks appropriés
         */
        fun createAllCardsFromStatistics(
            statistics: CoupleStatistics,
            onDaysTogetherClick: (() -> Unit)? = null,
            onQuestionsProgressClick: (() -> Unit)? = null,
            onCitiesVisitedClick: (() -> Unit)? = null,
            onCountriesVisitedClick: (() -> Unit)? = null
        ): List<StatisticCard> {
            return listOf(
                createDaysTogetherCard(
                    days = statistics.daysTogether,
                    onClick = onDaysTogetherClick
                ),
                createQuestionsAnsweredCard(
                    percentage = statistics.questionsProgressPercentage,
                    onClick = onQuestionsProgressClick
                ),
                createCitiesVisitedCard(
                    cities = statistics.citiesVisited,
                    onClick = onCitiesVisitedClick
                ),
                createCountriesVisitedCard(
                    countries = statistics.countriesVisited,
                    onClick = onCountriesVisitedClick
                )
            )
        }
    }
    
    /**
     * 🎨 Propriétés calculées pour l'interface
     */
    
    val isInteractive: Boolean
        get() = onClick != null
        
    val hasValue: Boolean
        get() = value.isNotEmpty() && value != "0" && value != "0%"
        
    val displayValue: String
        get() = if (value.isEmpty()) "—" else value
        
    /**
     * 🎯 Message d'état selon valeur
     */
    val statusMessage: String
        get() = when (theme) {
            StatisticTheme.LOVE -> when {
                value == "0" -> "Définissez votre date de relation"
                else -> "Votre histoire grandit chaque jour"
            }
            StatisticTheme.KNOWLEDGE -> when {
                value == "0%" -> "Commencez à répondre aux questions"
                value.removeSuffix("%").toIntOrNull() ?: 0 < 25 -> "Continuez à découvrir votre partenaire"
                else -> "Excellente progression !"
            }
            StatisticTheme.URBAN -> when {
                value == "0" -> "Ajoutez vos première sorties au journal"
                else -> "Explorez de nouveaux lieux ensemble"
            }
            StatisticTheme.GLOBAL -> when {
                value == "0" -> "Voyagez et documentez vos aventures"
                else -> "Votre carte du monde s'enrichit"
            }
        }
    
    override fun toString(): String {
        return "StatisticCard(theme=$theme, value=$value, interactive=$isInteractive)"
    }
}
