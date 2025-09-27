package com.love2loveapp.services.firebase

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import com.love2loveapp.models.UserLocation

/**
 * 🔥 FirebaseProfileService - Client Android pour Cloud Functions photos/localisation
 * 
 * Intégration avec les Cloud Functions existantes dans index.js:
 * - getPartnerInfo() - Informations partenaire + profileImageURL
 * - getPartnerProfileImage() - URL signée temporaire (1h)
 * - getPartnerLocation() - Coordonnées GPS partenaire
 * - getSignedImageURL() - URLs signées génériques
 */
class FirebaseProfileService {
    
    companion object {
        private const val TAG = "FirebaseProfileService"
        
        @Volatile
        private var instance: FirebaseProfileService? = null
        
        fun getInstance(): FirebaseProfileService {
            return instance ?: synchronized(this) {
                instance ?: FirebaseProfileService().also { instance = it }
            }
        }
    }
    
    private val functions = FirebaseFunctions.getInstance()
    
    /**
     * 👥 Récupère les informations du partenaire (nom, abonnement, URL photo)
     * Équivalent Cloud Function: getPartnerInfo
     */
    suspend fun getPartnerInfo(partnerId: String): Result<PartnerInfo> {
        return try {
            Log.d(TAG, "👥 Récupération info partenaire: $partnerId")
            
            val data = hashMapOf("partnerId" to partnerId)
            val result = functions
                .getHttpsCallable("getPartnerInfo")
                .call(data)
                .await()
            
            val response = result.data as Map<String, Any>
            if (response["success"] == true) {
                val partnerInfo = response["partnerInfo"] as Map<String, Any>
                val info = PartnerInfo(
                    name = partnerInfo["name"] as? String ?: "Partenaire",
                    isSubscribed = partnerInfo["isSubscribed"] as? Boolean ?: false,
                    profileImageURL = partnerInfo["profileImageURL"] as? String
                )
                
                Log.d(TAG, "✅ Info partenaire récupérées: ${info.name}, photo: ${info.profileImageURL != null}")
                Result.success(info)
            } else {
                Log.w(TAG, "⚠️ Échec récupération info partenaire")
                Result.failure(Exception("Échec récupération info partenaire"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur getPartnerInfo: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 📍 Récupère la localisation du partenaire
     * Équivalent Cloud Function: getPartnerLocation
     */
    suspend fun getPartnerLocation(partnerId: String): Result<UserLocation?> {
        return try {
            Log.d(TAG, "📍 Récupération localisation partenaire: $partnerId")
            
            val data = hashMapOf("partnerId" to partnerId)
            val result = functions
                .getHttpsCallable("getPartnerLocation")
                .call(data)
                .await()
            
            val response = result.data as Map<String, Any>
            if (response["success"] == true) {
                val location = response["location"] as Map<String, Any>
                val userLocation = UserLocation(
                    latitude = location["latitude"] as Double,
                    longitude = location["longitude"] as Double,
                    address = location["address"] as? String,
                    city = location["city"] as? String,
                    country = location["country"] as? String,
                    lastUpdated = (location["lastUpdated"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
                
                Log.d(TAG, "✅ Localisation partenaire trouvée: ${userLocation.city}")
                Result.success(userLocation)
            } else {
                val reason = response["reason"] as? String
                Log.d(TAG, "ℹ️ Pas de localisation partenaire: $reason")
                Result.success(null) // Pas de localisation disponible
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur getPartnerLocation: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 🖼️ Récupère une URL signée temporaire pour la photo du partenaire
     * Équivalent Cloud Function: getPartnerProfileImage
     */
    suspend fun getPartnerProfileImage(partnerId: String): Result<String?> {
        return try {
            Log.d(TAG, "🖼️ Récupération URL photo partenaire: $partnerId")
            
            val data = hashMapOf("partnerId" to partnerId)
            val result = functions
                .getHttpsCallable("getPartnerProfileImage")
                .call(data)
                .await()
            
            val response = result.data as Map<String, Any>
            if (response["success"] == true) {
                val imageUrl = response["imageUrl"] as String
                val expiresIn = response["expiresIn"] as? Number ?: 3600
                
                Log.d(TAG, "✅ URL signée photo partenaire (expire dans ${expiresIn}s)")
                Result.success(imageUrl)
            } else {
                val reason = response["reason"] as? String
                Log.d(TAG, "ℹ️ Pas de photo partenaire: $reason")
                Result.success(null) // Pas d'image disponible
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur getPartnerProfileImage: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 🔒 Génère une URL signée pour n'importe quelle image
     * Équivalent Cloud Function: getSignedImageURL
     */
    suspend fun getSignedImageURL(filePath: String): Result<String?> {
        return try {
            Log.d(TAG, "🔒 Génération URL signée: $filePath")
            
            val data = hashMapOf("filePath" to filePath)
            val result = functions
                .getHttpsCallable("getSignedImageURL")
                .call(data)
                .await()
            
            val response = result.data as Map<String, Any>
            if (response["success"] == true) {
                val signedUrl = response["signedUrl"] as String
                val expiresIn = response["expiresIn"] as? Number ?: 3600
                
                Log.d(TAG, "✅ URL signée générée (expire dans ${expiresIn}s)")
                Result.success(signedUrl)
            } else {
                Log.w(TAG, "⚠️ Échec génération URL signée")
                Result.failure(Exception("Échec génération URL signée"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur getSignedImageURL: ${e.message}", e)
            Result.failure(e)
        }
    }
}

/**
 * 📊 Modèle des informations partenaire
 */
data class PartnerInfo(
    val name: String,
    val isSubscribed: Boolean,
    val profileImageURL: String?
)
