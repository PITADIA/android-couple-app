package com.love2loveapp.services.firebase

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import java.util.Date

/**
 * Coordinateur Firebase - Interface unifiée pour tous les services Firebase
 * Remplace l'ancien FirebaseService monolithique par une façade coordonnée
 */
object FirebaseCoordinator {
    private const val TAG = "FirebaseCoordinator"

    // === Initialization ===
    fun initialize(context: Context) {
        FirebaseAuthService.initGoogleSignIn(context)
        FirebaseAuthService.setupAuthListener()
        Log.d(TAG, "🔥 Firebase Coordinator initialisé")
    }

    // === Authentication (délégation vers FirebaseAuthService) ===
    val isAuthenticated: StateFlow<Boolean> get() = FirebaseAuthService.isAuthenticated
    val isLoading: StateFlow<Boolean> get() = FirebaseAuthService.isLoading
    val errorMessage: StateFlow<String?> get() = FirebaseAuthService.errorMessage

    fun getGoogleSignInIntent(context: Context): Intent = 
        FirebaseAuthService.getGoogleSignInIntent(context)

    fun handleGoogleSignInResult(data: Intent?, onResult: (Boolean, String?) -> Unit) = 
        FirebaseAuthService.handleGoogleSignInResult(data, onResult)

    fun signOut(context: Context, onComplete: (() -> Unit)? = null) = 
        FirebaseAuthService.googleSignOut(context, onComplete)

    fun getCurrentUserId(): String? = FirebaseAuthService.getCurrentUserId()

    // === User Management (délégation vers FirebaseUserService) ===
    val currentUser: StateFlow<AppUser?> get() = FirebaseUserService.currentUser
    val userIsLoading: StateFlow<Boolean> get() = FirebaseUserService.isLoading

    fun saveUserData(user: AppUser) = FirebaseUserService.saveUserData(user)
    fun savePartialUserData(user: AppUser) = FirebaseUserService.savePartialUserData(user)
    fun loadUserData(uid: String) = FirebaseUserService.loadUserData(uid)
    fun forceRefreshUserData() = FirebaseUserService.forceRefreshUserData()

    fun updateUserName(newName: String, completion: (Boolean) -> Unit) = 
        FirebaseUserService.updateUserName(newName, completion)

    fun updateRelationshipStartDate(date: Date, completion: (Boolean) -> Unit) = 
        FirebaseUserService.updateRelationshipStartDate(date, completion)

    fun updateUserLocation(location: UserLocation, completion: (Boolean) -> Unit) = 
        FirebaseUserService.updateUserLocation(location, completion)

    fun updateProfileImage(bitmap: Bitmap, completion: (Boolean, String?) -> Unit) = 
        FirebaseUserService.updateProfileImage(bitmap, completion)

    fun updateSubscriptionStatus(isSubscribed: Boolean) = 
        FirebaseUserService.updateSubscriptionStatus(isSubscribed)

    fun startListeningForSubscriptionChanges() = 
        FirebaseUserService.startListeningForSubscriptionChanges()

    fun stopListeningForSubscriptionChanges() = 
        FirebaseUserService.stopListeningForSubscriptionChanges()

    // === Partner & Functions (délégation vers FirebaseFunctionsService) ===
    fun getPartnerInfo(partnerId: String, completion: (AppUser?) -> Unit) = 
        FirebaseFunctionsService.getPartnerInfo(partnerId, completion)

    fun syncPartnerJournalEntries(partnerId: String, completion: (Boolean, String?) -> Unit) = 
        FirebaseFunctionsService.syncPartnerJournalEntries(partnerId, completion)

    fun syncPartnerFavorites(partnerId: String, completion: (Boolean, String?) -> Unit) = 
        FirebaseFunctionsService.syncPartnerFavorites(partnerId, completion)

    // === Daily Questions & Challenges ===
    fun generateDailyQuestion(
        coupleId: String,
        userId: String,
        questionDay: Int,
        timezone: String,
        completion: (Boolean, Map<String, Any>?) -> Unit
    ) = FirebaseFunctionsService.generateDailyQuestion(coupleId, userId, questionDay, timezone, completion)

    fun submitDailyQuestionResponse(
        questionId: String,
        responseText: String,
        userName: String,
        completion: (Boolean, String?) -> Unit
    ) = FirebaseFunctionsService.submitDailyQuestionResponse(questionId, responseText, userName, completion)

    fun getOrCreateDailyQuestionSettings(
        coupleId: String,
        timezone: String,
        completion: (Boolean, Map<String, Any>?) -> Unit
    ) = FirebaseFunctionsService.getOrCreateDailyQuestionSettings(coupleId, timezone, completion)

    fun migrateDailyQuestionResponses(
        coupleId: String,
        completion: (Boolean, String?) -> Unit
    ) = FirebaseFunctionsService.migrateDailyQuestionResponses(coupleId, completion)

    // === Account Management ===
    fun deleteUserAccount(
        userId: String,
        googleIdToken: String?,
        completion: (Boolean, String?) -> Unit
    ) = FirebaseFunctionsService.deleteUserAccount(userId, googleIdToken, completion)

    // === Utility ===
    fun testCloudFunction(completion: (Boolean, String?) -> Unit) = 
        FirebaseFunctionsService.testCloudFunction(completion)

    // === Composite Operations ===
    /**
     * Opération composite : authentification + chargement des données utilisateur
     */
    fun signInAndLoadUser(context: Context, data: Intent?, onResult: (Boolean, String?, AppUser?) -> Unit) {
        handleGoogleSignInResult(data) { authSuccess, authError ->
            if (!authSuccess) {
                onResult(false, authError, null)
                return@handleGoogleSignInResult
            }
            
            val userId = getCurrentUserId()
            if (userId == null) {
                onResult(false, "ID utilisateur introuvable", null)
                return@handleGoogleSignInResult
            }
            
            // Observer les changements d'utilisateur
            loadUserData(userId)
            startListeningForSubscriptionChanges()
            
            onResult(true, null, currentUser.value)
        }
    }

    /**
     * Opération composite : sauvegarde utilisateur + données partagées
     */
    fun saveUserAndUpdateSharedData(user: AppUser) {
        saveUserData(user)
        updateSharedPartnerData(user)
    }

    private fun updateSharedPartnerData(user: AppUser) {
        // Logique de mise à jour des données partagées partenaire
        // (à implémenter selon vos besoins spécifiques)
        Log.d(TAG, "🤝 Mise à jour données partagées partenaire pour ${user.name}")
    }

    /**
     * Nettoyage complet lors de la déconnexion
     */
    fun cleanup() {
        stopListeningForSubscriptionChanges()
        Log.d(TAG, "🧹 Firebase Coordinator nettoyé")
    }
}
