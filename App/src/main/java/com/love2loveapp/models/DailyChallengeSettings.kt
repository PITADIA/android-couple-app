package com.love2loveapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🔧 DailyChallengeSettings - Configuration Défis du Jour Couple
 * Équivalent iOS DailyChallengeSettings
 * 
 * Utilisé pour :
 * - Tracking jour actuel du couple pour les défis
 * - Planification prochains défis
 * - Gestion timezone et dates
 * - Compatibilité iOS ↔ Android
 */
data class DailyChallengeSettings(
    val coupleId: String = "",
    val startDate: Timestamp = Timestamp.now(),
    val currentDay: Int = 1,
    val timezone: String = "Europe/Paris",
    val isActive: Boolean = true,
    val lastGeneratedDate: String? = null,
    val totalChallengesCompleted: Int = 0,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {

    companion object {
        private const val TAG = "DailyChallengeSettings"

        /**
         * 📥 Création depuis Firestore
         */
        fun fromFirestore(document: DocumentSnapshot): DailyChallengeSettings? {
            return try {
                DailyChallengeSettings(
                    coupleId = document.getString("coupleId") ?: "",
                    startDate = document.getTimestamp("startDate") ?: Timestamp.now(),
                    currentDay = (document.getLong("currentDay") ?: 1).toInt(),
                    timezone = document.getString("timezone") ?: "Europe/Paris",
                    isActive = document.getBoolean("isActive") ?: true,
                    lastGeneratedDate = document.getString("lastGeneratedDate"),
                    totalChallengesCompleted = (document.getLong("totalChallengesCompleted") ?: 0).toInt(),
                    createdAt = document.getTimestamp("createdAt") ?: Timestamp.now(),
                    updatedAt = document.getTimestamp("updatedAt") ?: Timestamp.now()
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Erreur parsing DailyChallengeSettings: ${e.message}")
                null
            }
        }
    }

    /**
     * 📤 Conversion vers Firestore
     */
    fun toFirestore(): Map<String, Any> {
        return mapOf(
            "coupleId" to coupleId,
            "startDate" to startDate,
            "currentDay" to currentDay,
            "timezone" to timezone,
            "isActive" to isActive,
            "lastGeneratedDate" to (lastGeneratedDate ?: ""),
            "totalChallengesCompleted" to totalChallengesCompleted,
            "createdAt" to createdAt,
            "updatedAt" to Timestamp.now()
        )
    }

    /**
     * 📅 Calcul du jour attendu selon la logique iOS
     */
    fun calculateExpectedDay(): Int {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(today)
            val startDateFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(startDate.toDate())
            val startDateParsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(startDateFormatted)
            
            if (todayDate != null && startDateParsed != null) {
                val daysDiff = ((todayDate.time - startDateParsed.time) / (1000 * 60 * 60 * 24)).toInt()
                maxOf(1, daysDiff + 1)
            } else {
                currentDay
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Erreur calcul jour attendu défis: ${e.message}")
            currentDay
        }
    }

    /**
     * 🔍 Vérification si un nouveau défi est nécessaire
     */
    fun needsNewChallenge(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return lastGeneratedDate != today
    }

    /**
     * 📊 Debug Info
     */
    override fun toString(): String {
        return "DailyChallengeSettings(coupleId='$coupleId', currentDay=$currentDay, timezone='$timezone', isActive=$isActive, completed=$totalChallengesCompleted)"
    }
}
