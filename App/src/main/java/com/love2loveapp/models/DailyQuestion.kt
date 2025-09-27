package com.love2loveapp.models

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*

/**
 * 📅 DailyQuestion - Modèle Question du Jour Android
 * Équivalent iOS DailyQuestion.swift
 * 
 * Représente une question quotidienne générée pour un couple
 * avec chat temps réel intégré et gestion freemium.
 */
data class DailyQuestion(
    val id: String,                              // Format: "coupleId_yyyy-MM-dd"  
    val coupleId: String,                        // ID du couple (partenaires connectés)
    val questionKey: String,                     // Clé dynamique : "daily_question_1", "daily_question_2", etc.
    val questionDay: Int,                        // Jour de la question (1, 2, 3...)
    val scheduledDate: String,                   // Format: "yyyy-MM-dd" (UTC)
    val scheduledDateTime: Timestamp,            // Timestamp Firebase exact
    val status: String = "pending",             // "pending", "active", "completed"
    val timezone: String = "Europe/Paris",      // Timezone du couple
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val responses: List<QuestionResponse> = emptyList()  // Réponses chat temps réel
) {

    companion object {
        private const val TAG = "DailyQuestion"

        /**
         * 🔥 Parsing depuis document Firestore
         * Compatible avec la structure iOS existante
         */
        fun fromFirestore(document: DocumentSnapshot): DailyQuestion? {
            return try {
                val data = document.data ?: return null

                DailyQuestion(
                    id = document.id,
                    coupleId = data["coupleId"] as? String ?: "",
                    questionKey = data["questionKey"] as? String ?: "",
                    questionDay = (data["questionDay"] as? Number)?.toInt() ?: 0,
                    scheduledDate = data["scheduledDate"] as? String ?: "",
                    scheduledDateTime = data["scheduledDateTime"] as? Timestamp
                        ?: Timestamp.now(),
                    status = data["status"] as? String ?: "pending",
                    timezone = data["timezone"] as? String ?: "Europe/Paris",
                    createdAt = data["createdAt"] as? Timestamp,
                    updatedAt = data["updatedAt"] as? Timestamp
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur parsing DailyQuestion: ${e.message}")
                null
            }
        }
    }

    /**
     * 🌍 Récupération texte localisé dynamique 
     * Utilise les clés XML : daily_question_1, daily_question_2, etc.
     */
    fun getLocalizedText(context: Context): String {
        val resourceId = context.resources.getIdentifier(
            questionKey,
            "string", 
            context.packageName
        )

        return if (resourceId != 0) {
            try {
                context.getString(resourceId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur récupération string $questionKey: ${e.message}")
                questionKey.replace("_", " ").replaceFirstChar { it.uppercase() }
            }
        } else {
            Log.w(TAG, "⚠️ Clé de traduction non trouvée: $questionKey")
            // Fallback : Formatage propre de la clé
            questionKey.replace("daily_question_", "Question ")
                .replace("_", " ")
                .replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * 📅 Date formatée pour l'affichage
     */
    val formattedDate: String
        get() {
            val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            val inputFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            return try {
                val date = inputFormatter.parse(scheduledDate)
                date?.let { formatter.format(it) } ?: scheduledDate
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur formatage date: ${e.message}")
                scheduledDate
            }
        }

    /**
     * 📅 Date formatée courte pour l'affichage
     */
    val shortFormattedDate: String
        get() {
            val formatter = SimpleDateFormat("dd MMM", Locale.getDefault())
            val inputFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            return try {
                val date = inputFormatter.parse(scheduledDate)
                date?.let { formatter.format(it) } ?: scheduledDate
            } catch (e: Exception) {
                scheduledDate
            }
        }

    /**
     * 🔥 Conversion vers Firestore
     */
    fun toFirestore(): Map<String, Any> {
        return mapOf(
            "coupleId" to coupleId,
            "questionKey" to questionKey,
            "questionDay" to questionDay,
            "scheduledDate" to scheduledDate,
            "scheduledDateTime" to scheduledDateTime,
            "status" to status,
            "timezone" to timezone,
            "createdAt" to (createdAt ?: Timestamp.now()),
            "updatedAt" to Timestamp.now()
        )
    }

    /**
     * 🔍 Check si la question est d'aujourd'hui
     */
    fun isToday(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return scheduledDate == today
    }

    /**
     * 📊 Nombre de réponses dans le chat
     */
    val responseCount: Int
        get() = responses.size

    /**
     * 👥 Check si les deux partenaires ont répondu
     */
    fun bothPartnersResponded(): Boolean {
        val userIds = responses.map { it.userId }.toSet()
        return userIds.size >= 2
    }

    /**
     * 📝 Dernière réponse reçue
     */
    val lastResponse: QuestionResponse?
        get() = responses.maxByOrNull { it.timestamp.seconds }
}
