package com.love2loveapp.models

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🔖 SavedChallenge - Modèle Défi Sauvegardé Local
 * Équivalent iOS SavedChallenge model
 * 
 * Représente un défi sauvegardé localement par l'utilisateur
 * pour pouvoir le retrouver facilement dans ses bookmarks.
 */
data class SavedChallenge(
    val id: String = "",                         // ID unique Firestore
    val challengeKey: String = "",               // Clé du défi (daily_challenge_X) - PRINCIPAL
    val challengeDay: Int = 0,                   // Jour du défi original - PRINCIPAL
    val savedAt: Timestamp = Timestamp.now(),   // Date de sauvegarde - PRINCIPAL
    val userId: String = "",                     // Firebase UID du propriétaire - PRINCIPAL
    
    // Champs supplémentaires Android (optionnels pour compatibilité)
    val challengeText: String = "",              // Texte complet du défi
    val emoji: String = "",                      // Emoji associé au défi  
    val userName: String = "",                   // Nom utilisateur pour affichage
    
    // DEPRECATED - Compatibilité ancienne version
    val challengeId: String = "",                // ID du défi original
    val dateAdded: Timestamp = savedAt,          // Alias pour savedAt
    val isCompleted: Boolean = false,            // État de completion au moment de la sauvegarde
    val completedAt: Timestamp? = null           // Date de completion si applicable
) {

    companion object {
        /**
         * 🔄 Création depuis un DailyChallenge existant
         */
        fun fromDailyChallenge(
            challenge: DailyChallenge,
            challengeText: String,
            isCompleted: Boolean = false
        ): SavedChallenge {
            return SavedChallenge(
                id = UUID.randomUUID().toString(),
                challengeId = challenge.id,
                challengeKey = challenge.challengeKey,
                challengeDay = challenge.challengeDay,
                challengeText = challengeText,
                emoji = getChallengeEmoji(challenge.challengeDay),
                dateAdded = Timestamp.now(),
                isCompleted = isCompleted,
                completedAt = if (isCompleted) challenge.completedAt else null
            )
        }

        /**
         * 🎨 Emoji selon le jour du défi (même logique que DailyChallengeCard)
         */
        private fun getChallengeEmoji(challengeDay: Int): String {
            return when (challengeDay % 10) {
                1 -> "💌" // Messages, communication
                2 -> "🍳" // Cuisine, activités domestiques
                3 -> "🎁" // Surprises, sorties
                4 -> "💕" // Amour, affection
                5 -> "🌟" // Expériences, découvertes
                6 -> "🎯" // Objectifs, défis
                7 -> "🏡" // Maison, cocooning
                8 -> "🌈" // Créativité, couleurs
                9 -> "⭐" // Excellence, réussite
                0 -> "🎪" // Fun, divertissement
                else -> "🎯" // Default
            }
        }
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
            scheduledDate = dateAdded, // Utilise la date de sauvegarde
            isCompleted = isCompleted,
            completedAt = completedAt,
            createdAt = dateAdded,
            updatedAt = dateAdded
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
     * 📝 Titre pour affichage (Jour X)
     */
    val displayTitle: String
        get() = "Jour $challengeDay"

    /**
     * 🔍 Check si le défi a été sauvegardé récemment (aujourd'hui)
     */
    val wasSavedToday: Boolean
        get() {
            val today = Calendar.getInstance()
            val savedDate = Calendar.getInstance().apply { time = dateAdded.toDate() }
            
            return today.get(Calendar.YEAR) == savedDate.get(Calendar.YEAR) &&
                   today.get(Calendar.DAY_OF_YEAR) == savedDate.get(Calendar.DAY_OF_YEAR)
        }

    /**
     * 📊 Debug Info
     */
    override fun toString(): String {
        return "SavedChallenge(challengeKey='$challengeKey', day=$challengeDay, saved=${formattedDateAdded})"
    }
}
