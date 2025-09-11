package com.love2love.model

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ------------------------------------------------------------
// Modèle Question "standard" (équivalent de ton type Swift)
// ------------------------------------------------------------
data class Question(
    val id: String,
    val text: String,
    val category: String
)

// ------------------------------------------------------------
// FavoriteQuestion (local)
// ------------------------------------------------------------
data class FavoriteQuestion(
    val id: String = UUID.randomUUID().toString(),
    val questionId: String,
    val questionText: String,
    val categoryTitle: String,
    val emoji: String,
    val dateAdded: Date = Date()
) {
    /** Conversion simple vers le modèle Question (texte déjà résolu). */
    fun toQuestion(): Question = Question(
        id = questionId,
        text = questionText,
        category = categoryTitle
    )

    /**
     * Variante si `questionText` / `categoryTitle` sont des *clés* de ressources `strings.xml`.
     * Exemple : "daily_question_1" -> context.getString(R.string.daily_question_1).
     * Si la clé n'existe pas, on retombe sur la valeur brute.
     */
    fun toQuestion(
        context: Context,
        textIsResName: Boolean,
        categoryIsResName: Boolean
    ): Question = Question(
        id = questionId,
        text = if (textIsResName) resolveStringByName(context, questionText) ?: questionText else questionText,
        category = if (categoryIsResName) resolveStringByName(context, categoryTitle) ?: categoryTitle else categoryTitle
    )
}

// ------------------------------------------------------------
// SharedFavoriteQuestion (Firestore)
// ------------------------------------------------------------
data class SharedFavoriteQuestion(
    val id: String = UUID.randomUUID().toString(),
    var questionId: String,
    var questionText: String,
    var categoryTitle: String,
    var emoji: String,
    var dateAdded: Date = Date(),
    var createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    var authorId: String,
    var authorName: String,
    var isShared: Boolean = true,
    var partnerIds: List<String> = emptyList()
) {
    // --- Propriété calculée (formatage date) ---
    fun formattedDateAdded(locale: Locale = Locale.getDefault()): String {
        val df: DateFormat = SimpleDateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, DateFormat.SHORT, locale
        )
        return df.format(dateAdded)
    }

    // --- Conversions ---
    fun toLocalFavorite(): FavoriteQuestion = FavoriteQuestion(
        id = id,
        questionId = questionId,
        questionText = questionText,
        categoryTitle = categoryTitle,
        emoji = emoji,
        dateAdded = dateAdded
    )

    fun toQuestion(): Question = Question(
        id = questionId,
        text = questionText,
        category = categoryTitle
    )

    /**
     * Variante localisée si `questionText` / `categoryTitle` sont des *clés* `strings.xml`.
     * En UI Compose, tu utiliseras plutôt `stringResource(R.string.ta_clef)` directement dans la vue.
     */
    fun toQuestion(
        context: Context,
        textIsResName: Boolean,
        categoryIsResName: Boolean
    ): Question = Question(
        id = questionId,
        text = if (textIsResName) resolveStringByName(context, questionText) ?: questionText else questionText,
        category = if (categoryIsResName) resolveStringByName(context, categoryTitle) ?: categoryTitle else categoryTitle
    )

    // --- Mapping Firestore ---
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "questionId" to questionId,
        "questionText" to questionText,
        "categoryTitle" to categoryTitle,
        "emoji" to emoji,
        "dateAdded" to Timestamp(dateAdded),
        "createdAt" to Timestamp(createdAt),
        "updatedAt" to Timestamp(updatedAt),
        "authorId" to authorId,
        "authorName" to authorName,
        "isShared" to isShared,
        "partnerIds" to partnerIds
    )

    companion object {
        fun fromDocument(document: DocumentSnapshot): SharedFavoriteQuestion? {
            val data = document.data ?: return null

            val dateAdded = (data["dateAdded"] as? Timestamp)?.toDate() ?: Date()
            val createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: Date()
            val updatedAt = (data["updatedAt"] as? Timestamp)?.toDate() ?: Date()

            @Suppress("UNCHECKED_CAST")
            val partners: List<String> = (data["partnerIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            return SharedFavoriteQuestion(
                id = document.id,
                questionId = data["questionId"] as? String ?: "",
                questionText = data["questionText"] as? String ?: "",
                categoryTitle = data["categoryTitle"] as? String ?: "",
                emoji = data["emoji"] as? String ?: "",
                dateAdded = dateAdded,
                createdAt = createdAt,
                updatedAt = updatedAt,
                authorId = data["authorId"] as? String ?: "",
                authorName = data["authorName"] as? String ?: "",
                isShared = data["isShared"] as? Boolean ?: true,
                partnerIds = partners
            )
        }
    }

    // --- Équivalence "au sens Swift" (id + questionId + questionText + authorId) ---
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SharedFavoriteQuestion) return false
        return id == other.id &&
                questionId == other.questionId &&
                questionText == other.questionText &&
                authorId == other.authorId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + questionId.hashCode()
        result = 31 * result + questionText.hashCode()
        result = 31 * result + authorId.hashCode()
        return result
    }
}

// ------------------------------------------------------------
// Utilitaire : résolution dynamique d'une clé de res string
// "daily_challenge_1" -> context.getString(R.string.daily_challenge_1)
// ------------------------------------------------------------
private fun resolveStringByName(context: Context, resName: String): String? {
    val resId = context.resources.getIdentifier(resName, "string", context.packageName)
    return if (resId != 0) context.getString(resId) else null
}
