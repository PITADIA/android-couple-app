package com.love2loveapp.models

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*
import com.love2loveapp.services.QuestionDataManager
import com.love2loveapp.services.Question

/**
 * 💝 FavoriteQuestion - Modèle Local Android
 * Équivalent iOS FavoriteQuestion.swift:1-25
 * 
 * Représente une question favorite localement sur l'appareil
 */
data class FavoriteQuestion(
    val id: String,
    val questionId: String,
    val questionText: String,
    val categoryTitle: String,
    val emoji: String,
    val dateAdded: Date
) {
    /**
     * Convertir en Question standard pour réutilisation
     * Note: textResId ne peut pas être récupéré depuis le texte, utiliser 0 par défaut
     */
    fun toQuestion(): Question {
        return Question(
            id = questionId,
            textResId = 0, // Non applicable depuis les favoris sauvegardés
            categoryId = categoryTitle
        )
    }
    
    companion object {
        /**
         * Créer depuis une Question standard
         */
        fun fromQuestion(
            question: Question,
            questionText: String, // Texte déjà récupéré avec getText(context)
            category: QuestionCategory,
            dateAdded: Date = Date()
        ): FavoriteQuestion {
            return FavoriteQuestion(
                id = UUID.randomUUID().toString(),
                questionId = question.id,
                questionText = questionText,
                categoryTitle = category.id, // Utiliser l'ID de la catégorie
                emoji = category.emoji,
                dateAdded = dateAdded
            )
        }
    }
}

/**
 * 🔥 SharedFavoriteQuestion - Modèle Firestore Partagé
 * Équivalent iOS SharedFavoriteQuestion.swift:29-109
 * 
 * Représente une question favorite partagée dans Firebase
 * Utilisé pour la synchronisation temps réel entre partenaires
 */
data class SharedFavoriteQuestion(
    val id: String = UUID.randomUUID().toString(),
    val questionId: String,
    val questionText: String,
    val categoryTitle: String,
    val emoji: String,
    val dateAdded: Date = Date(),
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),

    // 🔑 CHAMPS DE PARTAGE - Équivalent iOS
    val authorId: String,           // Firebase UID de l'auteur
    val authorName: String,         // Nom affiché de l'auteur
    val isShared: Boolean = true,   // Si visible par le partenaire
    val partnerIds: List<String> = emptyList() // IDs des partenaires autorisés
) {

    companion object {
        private const val TAG = "SharedFavoriteQuestion"
        
        /**
         * 🔥 Créer depuis un DocumentSnapshot Firestore
         * Équivalent iOS init?(from document: DocumentSnapshot)
         */
        fun fromFirestore(document: DocumentSnapshot): SharedFavoriteQuestion? {
            return try {
                val data = document.data ?: return null

                SharedFavoriteQuestion(
                    id = document.id,
                    questionId = data["questionId"] as? String ?: "",
                    questionText = data["questionText"] as? String ?: "",
                    categoryTitle = data["categoryTitle"] as? String ?: "",
                    emoji = data["emoji"] as? String ?: "",
                    
                    // Conversion sécurisée des timestamps
                    dateAdded = (data["dateAdded"] as? Timestamp)?.toDate() ?: Date(),
                    createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: Date(),
                    updatedAt = (data["updatedAt"] as? Timestamp)?.toDate() ?: Date(),
                    
                    // Champs de partage
                    authorId = data["authorId"] as? String ?: "",
                    authorName = data["authorName"] as? String ?: "",
                    isShared = data["isShared"] as? Boolean ?: true,
                    partnerIds = (data["partnerIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur parsing Firestore document ${document.id}: ${e.message}")
                null
            }
        }
    }

    /**
     * 🔥 Convertir en Map pour Firestore
     * Équivalent iOS func toDictionary() -> [String: Any]
     */
    fun toFirestore(): Map<String, Any> {
        return mapOf(
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
    }

    /**
     * 🔄 Convertir en FavoriteQuestion local
     * Équivalent iOS func toLocalFavorite() -> FavoriteQuestion
     */
    fun toLocalFavorite(): FavoriteQuestion {
        return FavoriteQuestion(
            id = id,
            questionId = questionId,
            questionText = questionText,
            categoryTitle = categoryTitle,
            emoji = emoji,
            dateAdded = dateAdded
        )
    }

    /**
     * 📅 Date formatée pour affichage
     * Équivalent iOS var formattedDateAdded: String
     */
    val formattedDateAdded: String
        get() {
            val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            return formatter.format(dateAdded)
        }
        
    /**
     * 🕐 Temps relatif (il y a X minutes)
     */
    val relativeTimeAdded: String
        get() {
            val now = Date()
            val diff = now.time - dateAdded.time
            val minutes = (diff / (1000 * 60)).toInt()
            
            return when {
                minutes < 1 -> "À l'instant"
                minutes < 60 -> "Il y a $minutes min"
                minutes < 1440 -> "Il y a ${minutes / 60}h"
                else -> "Il y a ${minutes / 1440}j"
            }
        }
}

/**
 * 📊 Résultat de synchronisation favoris
 * Utilisé pour les retours de Cloud Functions
 */
data class FavoritesSyncResult(
    val success: Boolean,
    val updatedFavoritesCount: Int = 0,
    val userFavoritesCount: Int = 0,
    val partnerFavoritesCount: Int = 0,
    val message: String = ""
)
