package com.love2loveapp.models.widgets

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.util.*

/**
 * 📱 WidgetData - Modèle données central widgets Android
 * 
 * Équivalent Android du système App Group iOS :
 * - Centralise toutes les données widgets
 * - Sérialisation JSON pour SharedPreferences
 * - Gestion états utilisateur et partenaire
 * - Intégration premium/freemium
 * - Cache données Firebase
 */
data class WidgetData(
    @SerializedName("user_name")
    val userName: String?,
    
    @SerializedName("user_profile_image_url")
    val userProfileImageUrl: String?,
    
    @SerializedName("partner_name") 
    val partnerName: String?,
    
    @SerializedName("partner_profile_image_url")
    val partnerProfileImageUrl: String?,
    
    @SerializedName("has_subscription")
    val hasSubscription: Boolean,
    
    @SerializedName("has_partner")
    val hasPartner: Boolean,
    
    @SerializedName("relationship_stats")
    val relationshipStats: RelationshipStats?,
    
    @SerializedName("distance_info")
    val distanceInfo: DistanceInfo?,
    
    @SerializedName("last_updated")
    val lastUpdated: Date,
    
    @SerializedName("app_version")
    val appVersion: String = "1.0",
    
    @SerializedName("widget_theme")
    val widgetTheme: WidgetTheme = WidgetTheme.LOVE_PINK
) {
    
    enum class WidgetTheme {
        LOVE_PINK,      // Thème principal rose Love2Love
        DARK_MODE,      // Mode sombre  
        CLASSIC_WHITE   // Mode classique blanc
    }
    
    companion object {
        private const val TAG = "WidgetData"
        
        /**
         * 🔄 Créer WidgetData vide pour état initial
         */
        fun empty(): WidgetData {
            Log.d(TAG, "📱 Création WidgetData vide")
            return WidgetData(
                userName = null,
                userProfileImageUrl = null,
                partnerName = null,
                partnerProfileImageUrl = null,
                hasSubscription = false,
                hasPartner = false,
                relationshipStats = null,
                distanceInfo = null,
                lastUpdated = Date(),
                appVersion = "1.0",
                widgetTheme = WidgetTheme.LOVE_PINK
            )
        }
        
        /**
         * 🔧 Créer WidgetData depuis données utilisateur app
         * 
         * Méthode factory pour construire depuis l'état app principal
         */
        fun fromUserData(
            userName: String?,
            userProfileImageUrl: String?,
            partnerName: String?,
            partnerProfileImageUrl: String?,
            hasSubscription: Boolean,
            relationshipStartDate: Date?,
            userLocation: com.love2loveapp.models.UserLocation?,
            partnerLocation: com.love2loveapp.models.UserLocation?
        ): WidgetData {
            
            Log.d(TAG, "🔧 Construction WidgetData depuis données utilisateur")
            Log.d(TAG, "👤 Utilisateur: ${userName ?: "Anonyme"}")
            Log.d(TAG, "👥 Partenaire: ${partnerName ?: "Non connecté"}")
            Log.d(TAG, "💎 Abonnement: $hasSubscription")
            
            // 💕 CALCUL STATISTIQUES RELATION
            val relationshipStats = relationshipStartDate?.let { startDate ->
                RelationshipStats.calculateFromStartDate(startDate)
            }
            
            // 🌍 CALCUL DISTANCE PARTENAIRE
            val distanceInfo = if (userLocation != null && partnerLocation != null) {
                DistanceInfo.calculateBetweenPartners(userLocation, partnerLocation)
            } else {
                null
            }
            
            return WidgetData(
                userName = userName,
                userProfileImageUrl = userProfileImageUrl,
                partnerName = partnerName,
                partnerProfileImageUrl = partnerProfileImageUrl,
                hasSubscription = hasSubscription,
                hasPartner = !partnerName.isNullOrEmpty(),
                relationshipStats = relationshipStats,
                distanceInfo = distanceInfo,
                lastUpdated = Date(),
                appVersion = "1.0",
                widgetTheme = WidgetTheme.LOVE_PINK
            )
        }
        
        /**
         * 📄 Parser WidgetData depuis JSON string
         * 
         * Méthode pour désérialisation SharedPreferences
         */
        fun fromJson(jsonString: String): WidgetData? {
            return try {
                Log.d(TAG, "📄 Parse WidgetData depuis JSON")
                val gson = createGson()
                gson.fromJson(jsonString, WidgetData::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur parse JSON WidgetData: ${e.message}", e)
                null
            }
        }
        
        /**
         * 📝 Créer instance Gson avec sérialisation Date
         */
        private fun createGson(): Gson {
            return GsonBuilder()
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create()
        }
    }
    
    /**
     * 📝 Sérialiser en JSON pour SharedPreferences
     */
    fun toJson(): String {
        return try {
            Log.d(TAG, "📝 Sérialisation WidgetData vers JSON")
            val gson = createGson()
            gson.toJson(this)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sérialisation JSON: ${e.message}", e)
            "{}"
        }
    }
    
    /**
     * 🎯 Propriétés calculées pour widgets
     */
    val isDataComplete: Boolean
        get() = !userName.isNullOrEmpty() && hasPartner && relationshipStats != null
    
    val isLocationAvailable: Boolean
        get() = distanceInfo?.isLocationAvailable == true
    
    val shouldShowPremiumGate: Boolean
        get() = false // 🆓 TOUS LES WIDGETS SONT GRATUITS (selon rapport iOS)
    
    val formattedCoupleNames: String
        get() = when {
            !userName.isNullOrEmpty() && !partnerName.isNullOrEmpty() -> "$userName & $partnerName"
            !userName.isNullOrEmpty() -> userName
            !partnerName.isNullOrEmpty() -> "Vous & $partnerName"
            else -> "Vous & Votre partenaire"
        }
    
    val widgetTitle: String
        get() = when {
            relationshipStats != null -> "${relationshipStats.daysTotal} jours ensemble"
            hasPartner -> "Connecté à $partnerName"
            else -> "Love2Love"
        }
    
    val widgetSubtitle: String
        get() = when {
            relationshipStats?.isAnniversaryToday == true -> "🎉 Joyeux anniversaire !"
            relationshipStats != null -> relationshipStats.formattedDuration
            hasPartner -> "Couple connecté 💕"
            else -> "Connectez votre partenaire"
        }
    
    /**
     * ⏰ Vérifier si données nécessitent mise à jour
     */
    fun needsUpdate(maxAgeMinutes: Int = 15): Boolean {
        val now = Date()
        val ageInMinutes = (now.time - lastUpdated.time) / (1000 * 60)
        val needsUpdate = ageInMinutes > maxAgeMinutes
        
        if (needsUpdate) {
            Log.d(TAG, "⏰ Données widgets périmées: ${ageInMinutes}min > ${maxAgeMinutes}min")
        }
        
        return needsUpdate
    }
    
    /**
     * 🔄 Créer copie avec nouvelles données relationship stats
     */
    fun updateRelationshipStats(newStats: RelationshipStats?): WidgetData {
        return copy(
            relationshipStats = newStats,
            lastUpdated = Date()
        )
    }
    
    /**
     * 🌍 Créer copie avec nouvelles données distance
     */
    fun updateDistanceInfo(newDistance: DistanceInfo?): WidgetData {
        return copy(
            distanceInfo = newDistance,
            lastUpdated = Date()
        )
    }
    
    /**
     * 💎 Créer copie avec nouveau statut abonnement
     */
    fun updateSubscriptionStatus(hasSubscription: Boolean): WidgetData {
        return copy(
            hasSubscription = hasSubscription,
            lastUpdated = Date()
        )
    }
    
    /**
     * 📊 Obtenir métriques pour debug et analytics
     */
    fun getDebugMetrics(): Map<String, Any> {
        return mapOf(
            "has_user_name" to !userName.isNullOrEmpty(),
            "has_partner_name" to !partnerName.isNullOrEmpty(),
            "has_subscription" to hasSubscription,
            "has_partner" to hasPartner,
            "has_relationship_stats" to (relationshipStats != null),
            "has_distance_info" to (distanceInfo != null),
            "is_location_available" to isLocationAvailable,
            "data_age_minutes" to ((Date().time - lastUpdated.time) / (1000 * 60)),
            "is_data_complete" to isDataComplete
        )
    }
    
    /**
     * 🎨 Obtenir couleurs thème widget
     */
    fun getThemeColors(): WidgetThemeColors {
        return when (widgetTheme) {
            WidgetTheme.LOVE_PINK -> WidgetThemeColors(
                primary = "#FD267A",
                secondary = "#FF655B", 
                background = "#FFFFFF",
                text = "#000000",
                textSecondary = "#666666"
            )
            WidgetTheme.DARK_MODE -> WidgetThemeColors(
                primary = "#FF6B9D",
                secondary = "#FF8FA3",
                background = "#1C1C1E",
                text = "#FFFFFF",
                textSecondary = "#AEAEB2"
            )
            WidgetTheme.CLASSIC_WHITE -> WidgetThemeColors(
                primary = "#007AFF",
                secondary = "#34C759",
                background = "#FFFFFF",
                text = "#000000",
                textSecondary = "#8E8E93"
            )
        }
    }
    
    override fun toString(): String {
        return "WidgetData(user=${userName ?: "null"}, partner=${partnerName ?: "null"}, " +
                "subscription=$hasSubscription, stats=${relationshipStats != null}, " +
                "distance=${distanceInfo != null}, updated=$lastUpdated)"
    }
}

/**
 * 🎨 WidgetThemeColors - Couleurs thème widget
 */
data class WidgetThemeColors(
    val primary: String,
    val secondary: String,
    val background: String,
    val text: String,
    val textSecondary: String
)
