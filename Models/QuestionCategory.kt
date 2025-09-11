package com.love2love.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.love2love.R
import java.util.UUID

// ---------------------------
// Models
// ---------------------------

data class QuestionCategory(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val emoji: String,
    val gradientColors: List<String>,
    val isPremium: Boolean
) {
    // Usage Android View
    fun title(context: Context): String = context.getString(titleRes)
    fun subtitle(context: Context): String = context.getString(subtitleRes)

    // Usage Jetpack Compose
    @Composable fun title(): String = stringResource(id = titleRes)
    @Composable fun subtitle(): String = stringResource(id = subtitleRes)

    // Helpers de chargement (équivalent extension Swift)
    fun loadQuestions(): List<Question> = QuestionDataManager.loadQuestions(id)
    fun getQuestionCount(): Int = loadQuestions().size
}

data class Question(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val category: String
) {
    // Le texte est déjà localisé si tu le fournis via tes JSON localisés.
    val localizedText: String get() = text
}

// ---------------------------
// Données statiques (équivalent static let categories)
// ---------------------------

object QuestionCategories {
    val categories: List<QuestionCategory> = listOf(
        QuestionCategory(
            id = "en-couple",
            titleRes = R.string.category_en_couple_title,
            subtitleRes = R.string.category_en_couple_subtitle,
            emoji = "💞",
            gradientColors = listOf("#E91E63", "#F06292"),
            isPremium = false
        ),
        QuestionCategory(
            id = "les-plus-hots",
            titleRes = R.string.category_desirs_inavoues_title,
            subtitleRes = R.string.category_desirs_inavoues_subtitle,
            emoji = "🌶️",
            gradientColors = listOf("#FF6B35", "#F7931E"),
            isPremium = true
        ),
        QuestionCategory(
            id = "a-distance",
            titleRes = R.string.category_a_distance_title,
            subtitleRes = R.string.category_a_distance_subtitle,
            emoji = "✈️",
            gradientColors = listOf("#00BCD4", "#26C6DA"),
            isPremium = true
        ),
        QuestionCategory(
            id = "questions-profondes",
            titleRes = R.string.category_questions_profondes_title,
            subtitleRes = R.string.category_questions_profondes_subtitle,
            emoji = "✨",
            gradientColors = listOf("#FFD700", "#FFA500"),
            isPremium = true
        ),
        QuestionCategory(
            id = "pour-rire-a-deux",
            titleRes = R.string.category_pour_rire_title,
            subtitleRes = R.string.category_pour_rire_subtitle,
            emoji = "😂",
            gradientColors = listOf("#FFD700", "#FFA500"),
            isPremium = true
        ),
        QuestionCategory(
            id = "tu-preferes",
            titleRes = R.string.category_tu_preferes_title,
            subtitleRes = R.string.category_tu_preferes_subtitle,
            emoji = "🤍",
            gradientColors = listOf("#9B59B6", "#8E44AD"),
            isPremium = true
        ),
        QuestionCategory(
            id = "mieux-ensemble",
            titleRes = R.string.category_mieux_ensemble_title,
            subtitleRes = R.string.category_mieux_ensemble_subtitle,
            emoji = "💌",
            gradientColors = listOf("#673AB7", "#9C27B0"),
            isPremium = true
        ),
        QuestionCategory(
            id = "pour-un-date",
            titleRes = R.string.category_pour_un_date_title,
            subtitleRes = R.string.category_pour_un_date_subtitle,
            emoji = "🍸",
            gradientColors = listOf("#3498DB", "#2980B9"),
            isPremium = true
        )
    )
}

// ---------------------------
// Data manager (branche sur ton implémentation réelle)
// ---------------------------

object QuestionDataManager {
    /**
     * Implémente ici le chargement depuis tes JSON/Firestore/etc.
     * Conserve la signature pour rester iso avec ton Swift.
     */
    fun loadQuestions(categoryId: String): List<Question> {
        // TODO: brancher sur ta source réelle
        return emptyList()
    }
}
