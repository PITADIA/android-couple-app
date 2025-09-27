package com.love2loveapp.models

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*

/**
 * 📔 JournalEntry - Modèle Entrée Journal
 * Équivalent iOS JournalEntry
 * 
 * Représente un événement du journal partagé entre partenaires.
 * Inclut titre, description, date/heure, image optionnelle et géolocalisation.
 */
data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val eventDate: Date,                          // 🔑 DATE/HEURE ÉVÉNEMENT
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val authorId: String,                         // 🔑 AUTEUR (USER UID)
    val authorName: String,
    val imageURL: String? = null,                 // 🔑 IMAGE FIREBASE STORAGE
    val localImagePath: String? = null,           // Pour upload en cours
    val isShared: Boolean = true,                 // 🔑 PARTAGE PARTENAIRE
    val partnerIds: List<String> = emptyList(),   // 🔑 IDS PARTENAIRES AUTORISÉS
    val location: JournalLocation? = null         // 🔑 GÉOLOCALISATION
) {

    companion object {
        private const val TAG = "JournalEntry"

        /**
         * 📥 Création depuis document Firestore
         */
        fun fromFirestore(document: DocumentSnapshot): JournalEntry? {
            return try {
                val data = document.data ?: return null

                JournalEntry(
                    id = document.id,
                    title = data["title"] as? String ?: "",
                    description = data["description"] as? String ?: "",
                    eventDate = (data["eventDate"] as? Timestamp)?.toDate() ?: Date(),
                    createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: Date(),
                    updatedAt = (data["updatedAt"] as? Timestamp)?.toDate() ?: Date(),
                    authorId = data["authorId"] as? String ?: "",
                    authorName = data["authorName"] as? String ?: "",
                    imageURL = data["imageURL"] as? String,
                    isShared = data["isShared"] as? Boolean ?: true,
                    partnerIds = (data["partnerIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    location = (data["location"] as? Map<String, Any>)?.let { locationData ->
                        JournalLocation.fromFirestore(locationData)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur parsing JournalEntry: ${e.message}")
                null
            }
        }
    }

    /**
     * 📤 Conversion vers Firestore
     */
    fun toFirestore(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "eventDate" to Timestamp(eventDate),
            "createdAt" to Timestamp(createdAt),
            "updatedAt" to Timestamp(Date()), // Toujours maintenant pour updatedAt
            "authorId" to authorId,
            "authorName" to authorName,
            "isShared" to isShared,
            "partnerIds" to partnerIds
        )

        // Image URL si présente
        imageURL?.let { map["imageURL"] = it }

        // Localisation si présente
        location?.let { map["location"] = it.toFirestore() }

        return map
    }

    /**
     * 📅 Date formatée pour affichage (équivalent iOS formattedEventDate)
     */
    val formattedEventDate: String
        get() {
            val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            return formatter.format(eventDate)
        }

    /**
     * 📅 Date courte pour listes
     */
    val shortFormattedDate: String
        get() {
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            return formatter.format(eventDate)
        }

    /**
     * ⏰ Heure formatée
     */
    val formattedTime: String
        get() {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            return formatter.format(eventDate)
        }

    /**
     * 🏷️ Titre d'affichage (limite caractères)
     */
    val displayTitle: String
        get() = if (title.length > 50) {
            title.take(47) + "..."
        } else {
            title
        }

    /**
     * 📝 Description d'affichage (limite caractères)
     */
    val displayDescription: String
        get() = if (description.length > 100) {
            description.take(97) + "..."
        } else {
            description
        }

    /**
     * 🖼️ A une image
     */
    val hasImage: Boolean
        get() = imageURL?.isNotEmpty() == true

    /**
     * 📍 A une localisation
     */
    val hasLocation: Boolean
        get() = location != null

    /**
     * 👤 Peut être modifié par l'utilisateur donné
     */
    fun canBeEditedBy(userId: String): Boolean {
        return authorId == userId
    }

    /**
     * 🗑️ Peut être supprimé par l'utilisateur donné
     */
    fun canBeDeletedBy(userId: String): Boolean {
        return authorId == userId
    }

    /**
     * 🔄 Copie avec mise à jour du timestamp
     */
    fun withUpdatedTimestamp(): JournalEntry {
        return copy(updatedAt = Date())
    }

    /**
     * 📊 Debug Info
     */
    override fun toString(): String {
        return "JournalEntry(id='$id', title='$title', author='$authorName', eventDate=$formattedEventDate)"
    }
}
