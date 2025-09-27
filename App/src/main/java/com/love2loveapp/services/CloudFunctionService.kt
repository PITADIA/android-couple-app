package com.love2loveapp.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🔐 Cloud Function Service Android
 * Reproduction fidèle de l'architecture iOS pour appels sécurisés
 */
@Singleton
class CloudFunctionService @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val functions: FirebaseFunctions
) {
    
    companion object {
        private const val TAG = "CloudFunctionService"
    }

    /**
     * 🖼️ Récupération sécurisée image partenaire
     * Exactement comme iOS : getPartnerProfileImage Cloud Function
     */
    suspend fun getPartnerProfileImage(partnerId: String): PartnerImageResponse {
        return try {
            Log.d(TAG, "🔐 Appel getPartnerProfileImage pour partenaire: $partnerId")

            // Vérifier authentification utilisateur
            val currentUser = firebaseAuth.currentUser
                ?: return PartnerImageResponse(
                    success = false,
                    imageUrl = null,
                    reason = "UNAUTHENTICATED",
                    message = "Utilisateur non authentifié"
                )

            // Données pour Cloud Function
            val data = hashMapOf(
                "partnerId" to partnerId
            )

            // Appel Cloud Function avec authentification automatique
            val result: HttpsCallableResult = functions
                .getHttpsCallable("getPartnerProfileImage")
                .call(data)
                .await()

            // Parser réponse
            val response = result.data as? Map<String, Any>
                ?: return PartnerImageResponse(
                    success = false,
                    imageUrl = null,
                    reason = "INVALID_RESPONSE",
                    message = "Réponse invalide de la Cloud Function"
                )

            val success = response["success"] as? Boolean ?: false
            
            if (success) {
                val imageUrl = response["imageUrl"] as? String
                val expiresIn = response["expiresIn"] as? Long
                
                Log.d(TAG, "✅ getPartnerProfileImage réussie - URL signée reçue")
                
                PartnerImageResponse(
                    success = true,
                    imageUrl = imageUrl,
                    expiresIn = expiresIn,
                    reason = null,
                    message = "Image partenaire récupérée avec succès"
                )
            } else {
                val reason = response["reason"] as? String ?: "UNKNOWN_ERROR"
                val message = response["message"] as? String ?: "Erreur inconnue"
                
                Log.w(TAG, "⚠️ getPartnerProfileImage échouée: $reason - $message")
                
                PartnerImageResponse(
                    success = false,
                    imageUrl = null,
                    reason = reason,
                    message = message
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur appel getPartnerProfileImage", e)
            
            PartnerImageResponse(
                success = false,
                imageUrl = null,
                reason = "FUNCTION_ERROR",
                message = "Erreur Cloud Function: ${e.message}"
            )
        }
    }

    /**
     * 📄 Récupération infos partenaire (getPartnerInfo)
     * Déjà utilisé ailleurs, mais ajouté ici pour cohérence
     */
    suspend fun getPartnerInfo(partnerId: String): PartnerInfoResponse {
        return try {
            Log.d(TAG, "👥 Appel getPartnerInfo pour partenaire: $partnerId")

            val data = hashMapOf("partnerId" to partnerId)
            val result = functions
                .getHttpsCallable("getPartnerInfo")
                .call(data)
                .await()

            val response = result.data as? Map<String, Any>
                ?: return PartnerInfoResponse(
                    success = false,
                    partnerInfo = null,
                    message = "Réponse invalide"
                )

            val success = response["success"] as? Boolean ?: false
            
            if (success) {
                val partnerInfo = response["partnerInfo"] as? Map<String, Any>
                
                PartnerInfoResponse(
                    success = true,
                    partnerInfo = partnerInfo,
                    message = "Infos partenaire récupérées"
                )
            } else {
                PartnerInfoResponse(
                    success = false,
                    partnerInfo = null,
                    message = "Échec récupération infos partenaire"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur getPartnerInfo", e)
            
            PartnerInfoResponse(
                success = false,
                partnerInfo = null,
                message = "Erreur: ${e.message}"
            )
        }
    }
}

/**
 * 🖼️ Réponse getPartnerProfileImage
 * Structure identique à iOS
 */
data class PartnerImageResponse(
    val success: Boolean,
    val imageUrl: String?,
    val expiresIn: Long? = null,
    val reason: String? = null,
    val message: String? = null
)

/**
 * 👥 Réponse getPartnerInfo
 * Pour cohérence avec le service
 */
data class PartnerInfoResponse(
    val success: Boolean,
    val partnerInfo: Map<String, Any>?,
    val message: String?
)
