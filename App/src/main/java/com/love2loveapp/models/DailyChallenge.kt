package com.love2loveapp.models

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🎯 DailyChallenge - Modèle Défi du Jour Android
 * Équivalent iOS DailyChallenge.swift
 * 
 * Représente un défi quotidien actionnable pour un couple
 * avec système completion et sauvegarde.
 */
data class DailyChallenge(
    val id: String,                              // Format: "coupleId_yyyy-MM-dd"  
    val coupleId: String,                        // ID du couple (partenaires connectés)
    val challengeKey: String,                    // Clé dynamique : "daily_challenge_1", "daily_challenge_2", etc.
    val challengeDay: Int,                       // Jour du défi (1, 2, 3...)
    val scheduledDate: Timestamp,                // Timestamp Firebase exact
    val isCompleted: Boolean = false,            // État completion (fait/pas fait)
    val completedAt: Timestamp? = null,          // Date de completion si fait
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {

    companion object {
        private const val TAG = "DailyChallenge"

        /**
         * 🔥 Parsing depuis document Firestore
         * Compatible avec la structure Firebase existante
         */
        fun fromFirestore(document: DocumentSnapshot): DailyChallenge? {
            return try {
                val data = document.data ?: return null

                DailyChallenge(
                    id = document.id,
                    coupleId = data["coupleId"] as? String ?: "",
                    challengeKey = data["challengeKey"] as? String ?: "",
                    challengeDay = (data["challengeDay"] as? Number)?.toInt() ?: 1,
                    scheduledDate = data["scheduledDate"] as? Timestamp ?: Timestamp.now(),
                    isCompleted = data["isCompleted"] as? Boolean ?: false,
                    completedAt = data["completedAt"] as? Timestamp,
                    createdAt = data["createdAt"] as? Timestamp,
                    updatedAt = data["updatedAt"] as? Timestamp
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur parsing DailyChallenge: ${e.message}")
                null
            }
        }
    }

    /**
     * 🔥 Conversion vers Firestore
     */
    fun toFirestore(): Map<String, Any> {
        return mapOf(
            "coupleId" to coupleId,
            "challengeKey" to challengeKey,
            "challengeDay" to challengeDay,
            "scheduledDate" to scheduledDate,
            "isCompleted" to isCompleted,
            "completedAt" to (completedAt ?: ""),
            "createdAt" to (createdAt ?: Timestamp.now()),
            "updatedAt" to Timestamp.now()
        )
    }

    /**
     * 🌍 Texte localisé du défi depuis strings.xml
     * Récupération dynamique : daily_challenge_1 → R.string.daily_challenge_1
     */
    fun getLocalizedText(context: Context): String {
        return try {
            val resourceId = context.resources.getIdentifier(
                challengeKey,
                "string",
                context.packageName
            )

            if (resourceId != 0) {
                context.getString(resourceId)
            } else {
                // Fallback en cas de clé non trouvée
                Log.w(TAG, "⚠️ Clé de défi non trouvée: $challengeKey")
                challengeKey.replace("_", " ").replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur récupération texte localisé: ${e.message}")
            challengeKey
        }
    }

    /**
     * 🔍 Check si le défi est d'aujourd'hui
     */
    fun isToday(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val challengeDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(scheduledDate.toDate())
        return challengeDate == today
    }

    /**
     * 📅 Date formatée pour affichage
     */
    val formattedDate: String
        get() {
            return try {
                SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(scheduledDate.toDate())
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur formatage date: ${e.message}")
                ""
            }
        }

    /**
     * ⏱️ Check si le défi a été complété récemment (aujourd'hui)
     */
    val wasCompletedToday: Boolean
        get() {
            val completionDate = completedAt?.toDate() ?: return false
            val today = Calendar.getInstance()
            val completionCal = Calendar.getInstance().apply { time = completionDate }
            
            return today.get(Calendar.YEAR) == completionCal.get(Calendar.YEAR) &&
                   today.get(Calendar.DAY_OF_YEAR) == completionCal.get(Calendar.DAY_OF_YEAR)
        }

    /**
     * 📊 Debug Info
     */
    override fun toString(): String {
        return "DailyChallenge(id='$id', challengeKey='$challengeKey', day=$challengeDay, completed=$isCompleted, today=${isToday()})"
    }
}
