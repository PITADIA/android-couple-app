package com.love2loveapp.models

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🔖 SharedSavedChallenge - Modèle Défi Sauvegardé Firestore
 * Équivalent iOS SavedChallengesService (bookmarks personnels)
 * 
 * Représente un défi sauvegardé par un utilisateur comme bookmark personnel.
 * Stocké dans Firestore mais principalement local à chaque utilisateur,
 * comme le système de bookmarks iOS.
 */
data class SharedSavedChallenge(
    val id: String = "",                           // ID Firestore
    val challengeId: String = "",                  // ID du défi original
    val challengeKey: String = "",                 // Clé du défi (daily_challenge_X)
    val challengeDay: Int = 0,                     // Jour du défi
    val challengeText: String = "",                // Texte complet du défi
    val emoji: String = "",                        // Emoji associé au défi
    
    // 👤 CHAMPS UTILISATEUR (bookmarks personnels)
    val authorId: String = "",                     // ID de l'utilisateur qui a sauvegardé
    val authorName: String = "",                   // Nom de l'utilisateur pour affichage
    val partnerIds: List<String> = emptyList(),    // IDs ayant accès (généralement juste l'utilisateur)
    val isShared: Boolean = false,                 // Bookmarks locaux par défaut (comme iOS)
    
    // 📅 DATES
    val dateAdded: Timestamp = Timestamp.now(),   // Date de sauvegarde
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {

    companion object {
        private const val TAG = "SharedSavedChallenge"

        /**
         * 📥 Création depuis document Firestore
         */
        fun fromFirestore(document: DocumentSnapshot): SharedSavedChallenge? {
            return try {
                val data = document.data ?: return null

                SharedSavedChallenge(
                    id = document.id,
                    challengeId = data["challengeId"] as? String ?: "",
                    challengeKey = data["challengeKey"] as? String ?: "",
                    challengeDay = (data["challengeDay"] as? Number)?.toInt() ?: 0,
                    challengeText = data["challengeText"] as? String ?: "",
                    emoji = data["emoji"] as? String ?: "",
                    authorId = data["authorId"] as? String ?: "",
                    authorName = data["authorName"] as? String ?: "",
                    partnerIds = (data["partnerIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    isShared = data["isShared"] as? Boolean ?: true,
                    dateAdded = data["dateAdded"] as? Timestamp ?: Timestamp.now(),
                    createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
                    updatedAt = data["updatedAt"] as? Timestamp ?: Timestamp.now()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur parsing SharedSavedChallenge: ${e.message}")
                null
            }
        }

        /**
         * 🔄 Création depuis SavedChallenge local
         */
        fun fromLocalSavedChallenge(
            savedChallenge: SavedChallenge,
            authorId: String,
            authorName: String,
            partnerIds: List<String>
        ): SharedSavedChallenge {
            return SharedSavedChallenge(
                id = "", // Sera défini par Firestore
                challengeId = savedChallenge.challengeId,
                challengeKey = savedChallenge.challengeKey,
                challengeDay = savedChallenge.challengeDay,
                challengeText = savedChallenge.challengeText,
                emoji = savedChallenge.emoji,
                authorId = authorId,
                authorName = authorName,
                partnerIds = partnerIds,
                isShared = true,
                dateAdded = savedChallenge.dateAdded,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
        }
    }

    /**
     * 📤 Conversion vers Firestore
     */
    fun toFirestore(): Map<String, Any> {
        return mapOf(
            "challengeId" to challengeId,
            "challengeKey" to challengeKey,
            "challengeDay" to challengeDay,
            "challengeText" to challengeText,
            "emoji" to emoji,
            "authorId" to authorId,
            "authorName" to authorName,
            "partnerIds" to partnerIds,
            "isShared" to isShared,
            "dateAdded" to dateAdded,
            "createdAt" to createdAt,
            "updatedAt" to Timestamp.now()
        )
    }

    /**
     * 🔄 Conversion vers SavedChallenge local
     */
    fun toLocalSavedChallenge(): SavedChallenge {
        return SavedChallenge(
            id = id,
            challengeId = challengeId,
            challengeKey = challengeKey,
            challengeDay = challengeDay,
            challengeText = challengeText,
            emoji = emoji,
            dateAdded = dateAdded,
            isCompleted = false, // Non pertinent pour défis sauvegardés
            completedAt = null
        )
    }

    /**
     * 🔄 Conversion vers DailyChallenge (pour réutiliser l'interface)
     */
    fun toDailyChallenge(): DailyChallenge {
        return DailyChallenge(
            id = challengeId,
            coupleId = "", // N'est pas nécessaire pour l'affichage
            challengeKey = challengeKey,
            challengeDay = challengeDay,
            scheduledDate = dateAdded,
            isCompleted = false, // Les défis sauvegardés ne sont pas "complétés"
            completedAt = null,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 📅 Date formatée pour affichage
     */
    val formattedDateAdded: String
        get() {
            return try {
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                dateFormat.format(dateAdded.toDate())
            } catch (e: Exception) {
                ""
            }
        }

    /**
     * 📝 Titre pour affichage (simple, sans auteur car c'est personnel)
     */
    val displayTitle: String
        get() = "Jour $challengeDay"

    /**
     * 🔍 Check si l'utilisateur peut supprimer ce défi
     */
    fun canDelete(userId: String): Boolean {
        return authorId == userId || partnerIds.contains(userId)
    }

    /**
     * 📊 Debug Info
     */
    override fun toString(): String {
        return "SharedSavedChallenge(challengeKey='$challengeKey', day=$challengeDay, author='$authorName', shared=${formattedDateAdded})"
    }
}
