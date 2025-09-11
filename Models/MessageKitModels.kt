package com.love2love.chat

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.util.Date

// =============================
// Android strings.xml resolver
// =============================
/**
 * Utilitaire pour résoudre une ressource de chaîne par son nom
 * (permet d'utiliser des clés dynamiques style "daily_challenge_1").
 * Exemple d'usage : StringsResolver.byName(context, "daily_challenge_1")
 */
object StringsResolver {
    fun byName(context: Context, key: String): String {
        val resId = context.resources.getIdentifier(key, "string", context.packageName)
        return if (resId != 0) context.getString(resId) else key // fallback sûr
    }
}

// =============================
// Modèles
// =============================

data class MessageSender(
    val senderId: String,
    val displayName: String
)

sealed class MessageKind {
    data class Text(val text: String) : MessageKind()
    // Évolutif : Image, Audio, etc.
}

data class DailyQuestionMessage(
    val sender: MessageSender,
    val messageId: String,
    val sentDate: Date,
    val kind: MessageKind
) {
    companion object {
        fun temporary(
            tempId: String,
            text: String,
            sender: MessageSender,
            date: Date = Date()
        ): DailyQuestionMessage = DailyQuestionMessage(
            sender = sender,
            messageId = tempId,
            sentDate = date,
            kind = MessageKind.Text(text)
        )
    }
}

// =============================
// Modèle minimal QuestionResponse
// (supprimez-le si vous l'avez déjà ailleurs)
// =============================

data class QuestionResponse(
    val id: String,
    val userId: String,
    val userName: String,
    val text: String,
    val respondedAt: Date,
    val status: String? = null,
    val isReadByPartner: Boolean? = null
)

// =============================
// Adapter/convertisseur
// =============================

object MessageAdapter {

    /** Convertit une QuestionResponse en DailyQuestionMessage */
    fun convert(response: QuestionResponse): DailyQuestionMessage =
        DailyQuestionMessage(
            sender = MessageSender(userId = response.userId, displayName = response.userName),
            messageId = response.id,
            sentDate = response.respondedAt,
            kind = MessageKind.Text(response.text)
        )

    /** Convertit une liste de QuestionResponse triée par date ascendante */
    fun convert(responses: List<QuestionResponse>): List<DailyQuestionMessage> =
        responses
            .sortedBy { it.respondedAt }
            .map { convert(it) }

    /** Crée un sender pour l'utilisateur courant (FirebaseAuth) */
    fun currentUserSender(currentUserDisplayName: String? = null): MessageSender? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val name = currentUserDisplayName ?: "User"
        return MessageSender(senderId = uid, displayName = name)
    }

    /**
     * Crée un sender pour le partenaire en trouvant la première réponse dont l'userId
     * est différent de currentUserId.
     */
    fun partnerSender(responses: List<QuestionResponse>, currentUserId: String): MessageSender? {
        val partner = responses.firstOrNull { it.userId != currentUserId } ?: return null
        return MessageSender(senderId = partner.userId, displayName = partner.userName)
    }
}
