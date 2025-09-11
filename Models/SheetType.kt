// SheetType.kt
package com.love2loveapp.ui.model

// Représente une catégorie (utilise ta classe existante si tu en as déjà une)
data class QuestionCategory(
    val id: String
    // ... autres champs si besoin (title, emoji, etc.)
)

/**
 * Équivalent Kotlin de l'enum Swift `SheetType: Identifiable, Equatable`.
 * - id stable pour chaque feuille (utile pour clés, navigation, etc.).
 * - Égalité :
 *   - Questions : compare uniquement category.id (comme le switch Equatable Swift).
 *   - Les autres sont des singletons object → égalité référentielle (mêmes cases).
 */
sealed interface SheetType {
    val id: String

    class Questions(val category: QuestionCategory) : SheetType {
        override val id: String = "questions_${category.id}"

        // Égalité basée uniquement sur l'id de la catégorie (mimique du Swift)
        override fun equals(other: Any?): Boolean =
            other is Questions && other.category.id == this.category.id

        override fun hashCode(): Int = category.id.hashCode()
        override fun toString(): String = "SheetType.Questions(id=$id)"
    }

    object Menu : SheetType { override val id: String = "menu" }
    object Subscription : SheetType { override val id: String = "subscription" }
    object Favorites : SheetType { override val id: String = "favorites" }
    object Journal : SheetType { override val id: String = "journal" }
    object Widgets : SheetType { override val id: String = "widgets" }
    object WidgetTutorial : SheetType { override val id: String = "widgetTutorial" }
    object PartnerManagement : SheetType { override val id: String = "partnerManagement" }
    object LocationPermission : SheetType { override val id: String = "locationPermission" }
    object PartnerLocationMessage : SheetType { override val id: String = "partnerLocationMessage" }
    object EventsMap : SheetType { override val id: String = "eventsMap" }
    object LocationTutorial : SheetType { override val id: String = "locationTutorial" }
    object DailyQuestionPermission : SheetType { override val id: String = "dailyQuestionPermission" }
}
