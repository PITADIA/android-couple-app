package com.love2loveapp.models

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*

/**
 * 💬 QuestionResponse - Modèle Réponse Chat Android
 * Équivalent iOS QuestionResponse.swift
 * 
 * Représente une réponse dans le chat temps réel des questions du jour.
 * Compatible iOS ↔ Android via Firestore.
 */
data class QuestionResponse(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,                          // Firebase UID de l'expéditeur
    val userName: String,                        // Nom affiché de l'expéditeur
    val text: String,                           // Message texte
    val timestamp: Timestamp = Timestamp.now(), // Horodatage Firebase
    val status: ResponseStatus = ResponseStatus.ANSWERED,
    val questionId: String = "",                // ID de la question parente (optionnel)
    val isTemporary: Boolean = false            // Flag pour messages temporaires (UX)
) {

    companion object {
        private const val TAG = "QuestionResponse"

        /**
         * 🔥 Parsing depuis document Firestore - Compatible iOS ↔ Android
         * Support iOS "respondedAt" et Android "timestamp"
         */
        fun fromFirestore(document: DocumentSnapshot): QuestionResponse? {
            return try {
                val data = document.data ?: run {
                    Log.w(TAG, "🚫 Document ${document.id} sans data")
                    return null
                }
                
                // Parse document ${document.id}

                // 🔄 PRIORISER iOS FORMAT (respondedAt) puis Android fallback
                val timestamp = data["respondedAt"] as? Timestamp 
                    ?: data["timestamp"] as? Timestamp 
                    ?: Timestamp.now()
                    
                // Timestamp parsing completed

                val response = QuestionResponse(
                    id = document.id,
                    userId = data["userId"] as? String ?: "",
                    userName = data["userName"] as? String ?: "",
                    text = data["text"] as? String ?: "",
                    timestamp = timestamp,
                    status = try {
                        ResponseStatus.valueOf(data["status"] as? String ?: "ANSWERED")
                    } catch (e: Exception) {
                        ResponseStatus.ANSWERED
                    },
                    questionId = data["questionId"] as? String ?: "",
                    isTemporary = false // Les documents Firestore ne sont jamais temporaires
                )
                
                // QuestionResponse parsed successfully
                response
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur parsing QuestionResponse: ${e.message}")
                null
            }
        }

        /**
         * 📱 Créer réponse temporaire pour UX immédiate
         * Affichage instantané avant confirmation Firestore
         */
        fun createTemporary(
            userId: String,
            userName: String,
            text: String,
            questionId: String = ""
        ): QuestionResponse {
            return QuestionResponse(
                id = "temp_${UUID.randomUUID()}",
                userId = userId,
                userName = userName,
                text = text,
                timestamp = Timestamp.now(),
                status = ResponseStatus.PENDING,
                questionId = questionId,
                isTemporary = true
            )
        }
    }

    /**
     * 🔥 Conversion vers Firestore - Compatible iOS ↔ Android
     */
    fun toFirestore(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "userName" to userName,
            "text" to text,
            "timestamp" to timestamp,      // Android format
            "respondedAt" to timestamp,    // iOS format (compatibilité)
            "status" to status.name,
            "questionId" to questionId,
            "isReadByPartner" to false     // iOS field (défaut false)
            // Note: isTemporary n'est pas sauvé (seulement pour UX locale)
        )
    }

    /**
     * 🕐 Horodatage formaté pour l'affichage
     */
    val formattedTime: String
        get() {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            return try {
                formatter.format(timestamp.toDate())
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur formatage timestamp: ${e.message}")
                ""
            }
        }

    /**
     * 📅 Date formatée complète
     */
    val formattedDateTime: String
        get() {
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return try {
                formatter.format(timestamp.toDate())
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur formatage datetime: ${e.message}")
                ""
            }
        }

    /**
     * 🔍 Check si le message a été envoyé aujourd'hui
     */
    fun isToday(): Boolean {
        val today = Calendar.getInstance()
        val messageDate = Calendar.getInstance().apply {
            time = timestamp.toDate()
        }
        
        return today.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * ⏱️ Check si le message est récent (moins de 5 minutes)
     */
    fun isRecent(): Boolean {
        val now = System.currentTimeMillis()
        val messageTime = timestamp.toDate().time
        val fiveMinutesInMs = 5 * 60 * 1000
        
        return (now - messageTime) < fiveMinutesInMs
    }

    /**
     * 📏 Longueur du message
     */
    val textLength: Int
        get() = text.length

    /**
     * 📝 Aperçu du message (limité à 50 caractères)
     */
    val preview: String
        get() = if (text.length > 50) {
            "${text.take(47)}..."
        } else {
            text
        }
}

/**
 * 📊 États des réponses de questions
 */
enum class ResponseStatus {
    PENDING,    // En attente (message temporaire)
    ANSWERED,   // Réponse confirmée 
    FAILED      // Échec d'envoi
}
