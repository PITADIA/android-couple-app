package com.love2loveapp.core.common

import android.content.Context
import com.love2loveapp.R

/**
 * Hiérarchie d'exceptions unifiée pour l'application
 * Chaque exception peut fournir un message localisé pour l'UI
 */
sealed class AppException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Retourne un message d'erreur localisé pour l'utilisateur final
     */
    abstract fun getLocalizedMessage(context: Context): String
    
    // === Authentification ===
    sealed class Auth(message: String, cause: Throwable? = null) : AppException(message, cause) {
        object UserNotAuthenticated : Auth("User not authenticated") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_user_not_authenticated)
        }
        
        object SignInFailed : Auth("Sign in failed") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_sign_in_failed)
        }
        
        object TokenExpired : Auth("Authentication token expired") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_token_expired)
        }
        
        data class GoogleSignInError(val errorCode: Int) : Auth("Google Sign-In error: $errorCode") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_google_sign_in, errorCode)
        }
    }
    
    // === Réseau ===
    sealed class Network(message: String, cause: Throwable? = null) : AppException(message, cause) {
        object NoConnection : Network("No internet connection") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_no_connection)
        }
        
        object Timeout : Network("Request timeout") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_timeout)
        }
        
        data class HttpError(val code: Int, val serverMessage: String?) : Network("HTTP $code: $serverMessage") {
            override fun getLocalizedMessage(context: Context): String = when (code) {
                404 -> context.getString(R.string.error_not_found)
                500 -> context.getString(R.string.error_server_error)
                else -> context.getString(R.string.error_http_generic, code)
            }
        }
    }
    
    // === Firebase ===
    sealed class Firebase(message: String, cause: Throwable? = null) : AppException(message, cause) {
        object FirestoreError : Firebase("Firestore operation failed") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_firestore)
        }
        
        object StorageError : Firebase("Firebase Storage error") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_storage)
        }
        
        data class FunctionError(val functionName: String, val errorMessage: String) : 
            Firebase("Cloud Function $functionName failed: $errorMessage") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_cloud_function, functionName)
        }
        
        object AppCheckError : Firebase("App Check validation failed") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_app_check)
        }
    }
    
    // === Données ===
    sealed class Data(message: String, cause: Throwable? = null) : AppException(message, cause) {
        object CacheError : Data("Cache operation failed") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_cache)
        }
        
        object SerializationError : Data("Data serialization failed") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_serialization)
        }
        
        data class ValidationError(val field: String) : Data("Validation failed for field: $field") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_validation, field)
        }
    }
    
    // === Localisation ===
    sealed class Location(message: String, cause: Throwable? = null) : AppException(message, cause) {
        object PermissionDenied : Location("Location permission denied") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_location_permission)
        }
        
        object ServiceUnavailable : Location("Location service unavailable") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_location_service)
        }
        
        object EncryptionFailed : Location("Location encryption failed") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_location_encryption)
        }
    }
    
    // === Billing ===
    sealed class Billing(message: String, cause: Throwable? = null) : AppException(message, cause) {
        object ServiceUnavailable : Billing("Billing service unavailable") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_billing_service)
        }
        
        object PurchaseFailed : Billing("Purchase failed") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_purchase_failed)
        }
        
        object ProductNotFound : Billing("Product not found") {
            override fun getLocalizedMessage(context: Context): String = 
                context.getString(R.string.error_product_not_found)
        }
    }
    
    // === Générique ===
    data class Generic(
        val userMessage: String,
        val originalCause: Throwable? = null
    ) : AppException(userMessage, originalCause) {
        override fun getLocalizedMessage(context: Context): String = userMessage
    }
    
    companion object {
        /**
         * Convertit une exception standard en AppException
         */
        fun fromThrowable(throwable: Throwable): AppException = when (throwable) {
            is AppException -> throwable
            is java.net.SocketTimeoutException -> Network.Timeout
            is java.net.UnknownHostException -> Network.NoConnection
            is java.io.IOException -> Network.NoConnection
            is SecurityException -> Location.PermissionDenied
            else -> Generic(
                throwable.localizedMessage ?: throwable.message ?: "Unknown error",
                throwable
            )
        }
        
        /**
         * Crée une exception depuis un code d'erreur Firebase
         */
        fun fromFirebaseException(exception: Exception): AppException = when (exception.message) {
            "PERMISSION_DENIED" -> Firebase.AppCheckError
            "UNAUTHENTICATED" -> Auth.UserNotAuthenticated
            else -> Firebase.FirestoreError
        }
    }
}
