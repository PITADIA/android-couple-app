package com.love2loveapp.core.repository

import android.graphics.Bitmap
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.model.AppUser
import com.love2loveapp.core.model.UserLocation
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Interface Repository pour la gestion des utilisateurs
 * Abstraction des opérations CRUD sur les profils utilisateur
 */
interface UserRepository {
    
    // === Observable State ===
    val currentUser: Flow<Result<AppUser?>>
    val isLoading: Flow<Boolean>
    
    // === User Management ===
    suspend fun saveUser(user: AppUser): Result<Unit>
    suspend fun savePartialUser(user: AppUser): Result<Unit>
    suspend fun loadUser(uid: String): Result<AppUser?>
    suspend fun refreshCurrentUser(): Result<AppUser?>
    suspend fun deleteUser(uid: String): Result<Unit>
    
    // === Profile Updates ===
    suspend fun updateUserName(newName: String): Result<Unit>
    suspend fun updateRelationshipStartDate(date: Date): Result<Unit>
    suspend fun updateUserLocation(location: UserLocation): Result<Unit>
    suspend fun updateProfileImage(bitmap: Bitmap): Result<String>
    
    // === Subscription Management ===
    suspend fun updateSubscriptionStatus(isSubscribed: Boolean): Result<Unit>
    fun startListeningForSubscriptionChanges()
    fun stopListeningForSubscriptionChanges()
    
    // === Cache Management ===
    suspend fun getCachedUser(): AppUser?
    suspend fun clearCache(): Result<Unit>
}

/**
 * Extensions utilitaires pour les ViewModels
 */
suspend fun UserRepository.updateUserSafely(
    update: suspend () -> Result<Unit>,
    onSuccess: suspend () -> Unit = {},
    onError: suspend (String) -> Unit = {}
) {
    when (val result = update()) {
        is Result.Success -> onSuccess()
        is Result.Error -> onError(result.exception.message ?: "Update failed")
        is Result.Loading -> { /* handled by Flow */ }
    }
}

suspend fun UserRepository.saveUserAndRefresh(user: AppUser): Result<AppUser?> {
    return when (val saveResult = saveUser(user)) {
        is Result.Success -> refreshCurrentUser()
        is Result.Error -> Result.error(saveResult.exception)
        is Result.Loading -> Result.loading()
    }
}
