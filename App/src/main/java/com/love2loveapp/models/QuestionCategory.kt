package com.love2loveapp.models

import androidx.annotation.StringRes
import com.love2loveapp.R

/**
 * Modèle des catégories de questions Love2Love
 * Correspond exactement aux catégories iOS définies dans le rapport
 */
data class QuestionCategory(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val emoji: String,
    val isPremium: Boolean
) {
    companion object {
        /**
         * Les 8 catégories de questions exactement comme dans l'iOS
         * 1 gratuite + 7 premium comme décrit dans le rapport
         */
        val categories = listOf(
            // 1. EN COUPLE - Gratuit (seule catégorie gratuite)
            QuestionCategory(
                id = "en-couple",
                titleRes = R.string.category_en_couple_title,
                subtitleRes = R.string.category_en_couple_subtitle,
                emoji = "💞",
                isPremium = false
            ),
            
            // 2. DÉSIRS INAVOUÉS - Premium
            QuestionCategory(
                id = "les-plus-hots",
                titleRes = R.string.category_desirs_inavoues_title,
                subtitleRes = R.string.category_desirs_inavoues_subtitle,
                emoji = "🌶️",
                isPremium = true
            ),
            
            // 3. À DISTANCE - Premium
            QuestionCategory(
                id = "a-distance",
                titleRes = R.string.category_a_distance_title,
                subtitleRes = R.string.category_a_distance_subtitle,
                emoji = "✈️",
                isPremium = true
            ),
            
            // 4. QUESTIONS PROFONDES - Premium
            QuestionCategory(
                id = "questions-profondes",
                titleRes = R.string.category_questions_profondes_title,
                subtitleRes = R.string.category_questions_profondes_subtitle,
                emoji = "✨",
                isPremium = true
            ),
            
            // 5. POUR RIRE - Premium
            QuestionCategory(
                id = "pour-rire-a-deux",
                titleRes = R.string.category_pour_rire_title,
                subtitleRes = R.string.category_pour_rire_subtitle,
                emoji = "😂",
                isPremium = true
            ),
            
            // 6. TU PRÉFÈRES - Premium
            QuestionCategory(
                id = "tu-preferes",
                titleRes = R.string.category_tu_preferes_title,
                subtitleRes = R.string.category_tu_preferes_subtitle,
                emoji = "🤍",
                isPremium = true
            ),
            
            // 7. MIEUX ENSEMBLE - Premium
            QuestionCategory(
                id = "mieux-ensemble",
                titleRes = R.string.category_mieux_ensemble_title,
                subtitleRes = R.string.category_mieux_ensemble_subtitle,
                emoji = "💌",
                isPremium = true
            ),
            
            // 8. POUR UN DATE - Premium
            QuestionCategory(
                id = "pour-un-date",
                titleRes = R.string.category_pour_un_date_title,
                subtitleRes = R.string.category_pour_un_date_subtitle,
                emoji = "🍸",
                isPremium = true
            )
        )
        
        /**
         * Obtient une catégorie par son ID
         */
        fun getCategoryById(id: String): QuestionCategory? {
            return categories.find { it.id == id }
        }
        
        /**
         * Obtient toutes les catégories premium
         */
        val premiumCategories: List<QuestionCategory>
            get() = categories.filter { it.isPremium }
            
        /**
         * Obtient toutes les catégories gratuites
         */
        val freeCategories: List<QuestionCategory>
            get() = categories.filter { !it.isPremium }
    }
}
