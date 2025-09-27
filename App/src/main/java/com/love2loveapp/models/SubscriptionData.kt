package com.love2loveapp.models

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * 💳 SubscriptionData - Modèle de données abonnement
 * 
 * Gère les données d'abonnement utilisateur avec support :
 * - Abonnements Google Play
 * - Héritage abonnement partenaire
 * - Validité et expiration
 */
data class SubscriptionData(
    val isSubscribed: Boolean = false,
    val productId: String? = null,
    val platform: String = "android", // "android" ou "ios"
    val purchaseDate: Date? = null,
    val expiryDate: Date? = null,
    val isInheritedFromPartner: Boolean = false,
    val inheritedFrom: String? = null,
    val inheritedAt: Date? = null,
    val originalTransactionId: String? = null,
    val purchaseToken: String? = null,
    val autoRenewing: Boolean = true,
    val trialPeriod: Boolean = false,
    val gracePeriod: Boolean = false
) {

    companion object {
        private const val TAG = "SubscriptionData"

        /**
         * 🔥 Conversion depuis Map Firestore
         */
        fun fromMap(map: Map<String, Any>): SubscriptionData {
            return try {
                SubscriptionData(
                    isSubscribed = map["isSubscribed"] as? Boolean ?: false,
                    productId = map["productId"] as? String,
                    platform = map["platform"] as? String ?: "android",
                    purchaseDate = (map["purchaseDate"] as? com.google.firebase.Timestamp)?.toDate(),
                    expiryDate = (map["expiryDate"] as? com.google.firebase.Timestamp)?.toDate(),
                    isInheritedFromPartner = map["inheritedFrom"] != null,
                    inheritedFrom = map["inheritedFrom"] as? String,
                    inheritedAt = (map["inheritedAt"] as? com.google.firebase.Timestamp)?.toDate(),
                    originalTransactionId = map["originalTransactionId"] as? String,
                    purchaseToken = map["purchaseToken"] as? String,
                    autoRenewing = map["autoRenewing"] as? Boolean ?: true,
                    trialPeriod = map["trialPeriod"] as? Boolean ?: false,
                    gracePeriod = map["gracePeriod"] as? Boolean ?: false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erreur parsing SubscriptionData: ${e.message}", e)
                SubscriptionData() // Retour par défaut
            }
        }
    }

    /**
     * ⏰ Abonnement encore valide ?
     * Vérifie expiration + grace period
     */
    val isValid: Boolean
        get() = isSubscribed && (expiryDate?.after(Date()) ?: false || gracePeriod)

    /**
     * ⚠️ Abonnement bientôt expiré ? (< 7 jours)
     */
    val isExpiringSoon: Boolean
        get() {
            val expiryDate = expiryDate ?: return false
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, 7) // Dans 7 jours
            return expiryDate.before(calendar.time)
        }

    /**
     * ❌ Abonnement expiré ?
     */
    val isExpired: Boolean
        get() = expiryDate?.before(Date()) ?: false && !gracePeriod

    /**
     * 📅 Date expiration formatée
     */
    val formattedExpiryDate: String
        get() = expiryDate?.let {
            SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(it)
        } ?: ""

    /**
     * 📅 Date achat formatée
     */
    val formattedPurchaseDate: String
        get() = purchaseDate?.let {
            SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(it)
        } ?: ""

    /**
     * 📊 Statut détaillé pour affichage
     */
    val statusDescription: String
        get() = when {
            isInheritedFromPartner -> "Premium hérité du partenaire"
            trialPeriod -> "Période d'essai"
            gracePeriod -> "Période de grâce"
            isExpired -> "Expiré le $formattedExpiryDate"
            isExpiringSoon -> "Expire le $formattedExpiryDate"
            isValid -> "Actif jusqu'au $formattedExpiryDate"
            else -> "Non abonné"
        }

    /**
     * 🎨 Couleur statut pour UI
     */
    val statusColor: Long
        get() = when {
            isInheritedFromPartner -> 0xFF4CAF50 // Vert
            trialPeriod -> 0xFF2196F3 // Bleu
            gracePeriod -> 0xFFFF9800 // Orange
            isExpired -> 0xFFF44336 // Rouge
            isExpiringSoon -> 0xFFFF9800 // Orange
            isValid -> 0xFF4CAF50 // Vert
            else -> 0xFF9E9E9E // Gris
        }

    /**
     * 🏷️ Type abonnement pour affichage
     */
    val subscriptionType: String
        get() = when (productId) {
            "premium_monthly" -> "Premium Mensuel"
            "premium_yearly" -> "Premium Annuel"
            "premium_lifetime" -> "Premium à vie"
            else -> "Premium"
        }

    /**
     * 💰 Prix par mois estimé (approximation)
     */
    val estimatedMonthlyPrice: String
        get() = when (productId) {
            "premium_monthly" -> "4,99€/mois"
            "premium_yearly" -> "39,99€/an (3,33€/mois)"
            "premium_lifetime" -> "99,99€ (unique)"
            else -> "N/A"
        }

    /**
     * 📈 Jours restants
     */
    val daysRemaining: Int
        get() {
            val expiryDate = expiryDate ?: return 0
            val diff = expiryDate.time - Date().time
            return (diff / (1000 * 60 * 60 * 24)).toInt()
        }

    /**
     * 🔄 Conversion vers Map pour Firestore
     */
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "isSubscribed" to isSubscribed,
            "productId" to productId,
            "platform" to platform,
            "purchaseDate" to purchaseDate?.let { com.google.firebase.Timestamp(it) },
            "expiryDate" to expiryDate?.let { com.google.firebase.Timestamp(it) },
            "inheritedFrom" to inheritedFrom,
            "inheritedAt" to inheritedAt?.let { com.google.firebase.Timestamp(it) },
            "originalTransactionId" to originalTransactionId,
            "purchaseToken" to purchaseToken,
            "autoRenewing" to autoRenewing,
            "trialPeriod" to trialPeriod,
            "gracePeriod" to gracePeriod
        )
    }

    /**
     * 🛡️ Abonnement premium valide ? (validation complète)
     */
    val isPremiumValid: Boolean
        get() = isSubscribed && isValid && !isExpired

    /**
     * ⚡ Besoin de renouvellement ?
     */
    val needsRenewal: Boolean
        get() = isSubscribed && !autoRenewing && isExpiringSoon

    /**
     * 📱 Source abonnement pour tracking
     */
    val subscriptionSource: String
        get() = when {
            isInheritedFromPartner -> "partner_inheritance"
            platform == "android" -> "google_play"
            platform == "ios" -> "app_store"
            else -> "unknown"
        }
}
