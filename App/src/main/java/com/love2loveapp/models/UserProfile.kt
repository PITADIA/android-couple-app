package com.love2loveapp.models

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*

/**
 * 👤 UserProfile - Modèle de données utilisateur pour le système Profil
 * 
 * Équivalent Android du UserProfile iOS, avec integration Firebase complète
 * Gère profil utilisateur, photo, relation, partenaire, abonnement
 */
data class UserProfile(
    val id: String,
    val name: String,
    val email: String? = null,
    val imageURL: String? = null,
    val relationshipStartDate: Date? = null,
    val partnerId: String? = null,
    val partnerName: String? = null,
    val connectedPartnerCode: String? = null,
    val connectedAt: Date? = null,
    val isSubscribed: Boolean = false,
    val subscription: SubscriptionData? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {

    companion object {
        private const val TAG = "UserProfile"

        /**
         * 🔥 Conversion depuis Firestore Document
         * Parsing sécurisé avec gestion d'erreurs
         */
        fun fromFirestore(document: DocumentSnapshot): UserProfile? {
            return try {
                val data = document.data ?: return null
                
                // 🎯 GÉNÉRATION AUTOMATIQUE IDENTIQUE AU MODÈLE USER
                val rawName = data["name"] as? String ?: ""
                val finalName = if (com.love2loveapp.utils.UserNameGenerator.isNameEmpty(rawName)) {
                    val generatedName = com.love2loveapp.utils.UserNameGenerator.generateAutomaticName(document.id)
                    android.util.Log.d(TAG, "🎯 UserProfile: Auto-génération nom pour '${document.id}' → '$generatedName'")
                    generatedName
                } else {
                    rawName.trim()
                }

                UserProfile(
                    id = document.id,
                    name = finalName,
                    email = data["email"] as? String,
                    imageURL = data["imageURL"] as? String,
                    relationshipStartDate = (data["relationshipStartDate"] as? com.google.firebase.Timestamp)?.toDate(),
                    partnerId = data["connectedPartnerId"] as? String,
                    partnerName = data["partnerName"] as? String,
                    connectedPartnerCode = data["connectedPartnerCode"] as? String,
                    connectedAt = (data["connectedAt"] as? com.google.firebase.Timestamp)?.toDate(),
                    isSubscribed = data["isSubscribed"] as? Boolean ?: false,
                    subscription = (data["subscription"] as? Map<String, Any>)?.let {
                        SubscriptionData.fromMap(it)
                    },
                    createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                    updatedAt = (data["updatedAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erreur parsing UserProfile: ${e.message}", e)
                null
            }
        }
    }

    /**
     * 🔤 Initiales utilisateur pour affichage sans photo
     * Ex: "Jean Dupont" → "JD"
     */
    val initials: String
        get() = name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()

    /**
     * 💕 A-t-il un partenaire connecté ?
     */
    val hasPartner: Boolean
        get() = !partnerId.isNullOrEmpty()

    /**
     * 📅 Date relation formatée pour affichage
     * Format français : "25 décembre 2023"
     */
    val formattedRelationshipDate: String
        get() = relationshipStartDate?.let {
            SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(it)
        } ?: ""

    /**
     * ⏱️ Durée relation formatée
     * Ex: "2 ans et 3 mois"
     */
    val relationshipDuration: String
        get() = relationshipStartDate?.let { startDate ->
            val calendar = Calendar.getInstance()
            val now = calendar.time
            
            val startCalendar = Calendar.getInstance()
            startCalendar.time = startDate
            
            var years = calendar.get(Calendar.YEAR) - startCalendar.get(Calendar.YEAR)
            var months = calendar.get(Calendar.MONTH) - startCalendar.get(Calendar.MONTH)
            
            if (months < 0) {
                years--
                months += 12
            }
            
            when {
                years > 0 && months > 0 -> "$years an${if (years > 1) "s" else ""} et $months mois"
                years > 0 -> "$years an${if (years > 1) "s" else ""}"
                months > 0 -> "$months mois"
                else -> "Moins d'un mois"
            }
        } ?: ""

    /**
     * 💎 Statut abonnement pour affichage
     */
    val subscriptionStatus: String
        get() = when {
            subscription?.isInheritedFromPartner == true -> "Premium (hérité du partenaire)"
            isSubscribed -> "Premium actif"
            else -> "Gratuit"
        }

    /**
     * 🎨 Couleur dégradé pour initiales basée sur nom
     */
    val gradientColors: Pair<Long, Long>
        get() {
            val hash = name.hashCode()
            return when (hash % 5) {
                0 -> Pair(0xFFFF6B9D, 0xFFE63C6B) // Rose original
                1 -> Pair(0xFF667eea, 0xFF764ba2) // Violet
                2 -> Pair(0xFFf093fb, 0xFFf5576c) // Rose-rouge
                3 -> Pair(0xFF4facfe, 0xFF00f2fe) // Bleu
                4 -> Pair(0xFF43e97b, 0xFF38f9d7) // Vert
                else -> Pair(0xFFFF6B9D, 0xFFE63C6B) // Défaut
            }
        }

    /**
     * 🔄 Conversion vers Map pour Firestore
     */
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "email" to email,
            "imageURL" to imageURL,
            "relationshipStartDate" to relationshipStartDate?.let { 
                com.google.firebase.Timestamp(it) 
            },
            "connectedPartnerId" to partnerId,
            "partnerName" to partnerName,
            "connectedPartnerCode" to connectedPartnerCode,
            "connectedAt" to connectedAt?.let { 
                com.google.firebase.Timestamp(it) 
            },
            "isSubscribed" to isSubscribed,
            "subscription" to subscription?.toFirestoreMap(),
            "updatedAt" to com.google.firebase.Timestamp.now()
        )
    }

    /**
     * ✅ Validation profil complet
     */
    val isProfileComplete: Boolean
        get() = name.isNotBlank() && 
                relationshipStartDate != null && 
                (imageURL != null || name.isNotBlank()) // Photo OU nom valide

    /**
     * 📊 Score complétion profil (0-100%)
     */
    val completionScore: Int
        get() {
            var score = 0
            if (name.isNotBlank()) score += 25
            if (imageURL != null) score += 25
            if (relationshipStartDate != null) score += 25
            if (hasPartner) score += 25
            return score
        }
}
