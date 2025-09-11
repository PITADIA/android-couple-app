package com.love2loveapp.core.services.billing

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.love2loveapp.core.common.Result
import com.love2loveapp.model.common.AppException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GooglePlayBillingService - Gestion des abonnements Google Play
 * 
 * Responsabilités:
 * - Validation des achats via Firebase Functions
 * - Synchronisation avec le backend
 * - Gestion des erreurs de validation
 */
@Singleton
class GooglePlayBillingService @Inject constructor(
    private val context: Context,
    private val functions: FirebaseFunctions
) {
    
    companion object {
        private const val TAG = "GooglePlayBillingService"
    }
    
    /**
     * Valide un achat Google Play via Firebase Functions
     * 
     * @param productId L'ID du produit acheté
     * @param purchaseToken Le token de l'achat
     * @return Result avec les détails de validation
     */
    suspend fun validateGooglePurchase(
        productId: String,
        purchaseToken: String
    ): Result<ValidationResponse> {
        return try {
            val callable = functions.getHttpsCallable("validateGooglePurchase")
            
            val data = mapOf(
                "productId" to productId,
                "purchaseToken" to purchaseToken
            )
            
            val result = callable.call(data).await()
            val responseData = result.data as? Map<String, Any>
                ?: return Result.Error(AppException.ValidationError("Invalid response format"))
            
            val validationResponse = ValidationResponse(
                success = responseData["success"] as? Boolean ?: false,
                isSubscribed = responseData["isSubscribed"] as? Boolean ?: false,
                expiresDate = responseData["expiresDate"] as? String
            )
            
            Result.Success(validationResponse)
            
        } catch (e: Exception) {
            Result.Error(AppException.ValidationError("Google Play validation failed", e))
        }
    }
    
    /**
     * Vérifie le statut d'abonnement actuel
     * Utilise la fonction existante checkSubscriptionStatus
     */
    suspend fun checkSubscriptionStatus(): Result<SubscriptionStatus> {
        return try {
            val callable = functions.getHttpsCallable("checkSubscriptionStatus")
            val result = callable.call().await()
            val responseData = result.data as? Map<String, Any>
                ?: return Result.Error(AppException.ValidationError("Invalid response format"))
            
            val status = SubscriptionStatus(
                isSubscribed = responseData["isSubscribed"] as? Boolean ?: false,
                subscriptionType = responseData["subscriptionType"] as? String,
                platform = responseData["platform"] as? String,
                expiresDate = responseData["expiresDate"] as? String
            )
            
            Result.Success(status)
            
        } catch (e: Exception) {
            Result.Error(AppException.ValidationError("Subscription status check failed", e))
        }
    }
    
    /**
     * Réponse de validation d'achat
     */
    data class ValidationResponse(
        val success: Boolean,
        val isSubscribed: Boolean,
        val expiresDate: String?
    )
    
    /**
     * Statut d'abonnement
     */
    data class SubscriptionStatus(
        val isSubscribed: Boolean,
        val subscriptionType: String?,
        val platform: String?,
        val expiresDate: String?
    )
}
