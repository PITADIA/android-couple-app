package com.love2loveapp.core.services

import android.graphics.Bitmap
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.model.AppUser
import com.love2loveapp.core.model.UserLocation
import com.love2loveapp.core.services.firebase.FirebaseUserService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Date

/**
 * Service utilisateur basé sur Flow<Result<T>>
 * Standardise toutes les opérations avec le pattern Flow<Result<T>>
 */
class FlowBasedUserService {
    
    // === State Management ===
    private val _operationState = MutableStateFlow<Result<String>>(Result.success("idle"))
    
    // === Observable State avec Result<T> ===
    val currentUser: Flow<Result<AppUser?>> = combine(
        FirebaseUserService.currentUser,
        FirebaseUserService.isLoading,
        FirebaseUserService.errorMessage
    ) { user, isLoading, error ->
        when {
            isLoading -> Result.loading()
            error != null -> Result.error(AppException.Generic(error))
            else -> Result.success(user)
        }
    }
    
    val subscriptionStatus: Flow<Result<Boolean>> = currentUser.map { userResult ->
        when (userResult) {
            is Result.Success -> Result.success(userResult.data?.isSubscribed ?: false)
            is Result.Error -> userResult
            is Result.Loading -> Result.loading()
        }
    }
    
    val profileImageUrl: Flow<Result<String?>> = currentUser.map { userResult ->
        when (userResult) {
            is Result.Success -> Result.success(userResult.data?.profileImageURL)
            is Result.Error -> userResult
            is Result.Loading -> Result.loading()
        }
    }
    
    val userLocation: Flow<Result<UserLocation?>> = currentUser.map { userResult ->
        when (userResult) {
            is Result.Success -> Result.success(userResult.data?.currentLocation)
            is Result.Error -> userResult
            is Result.Loading -> Result.loading()
        }
    }
    
    // === Operations avec Result<T> ===
    suspend fun saveUser(user: AppUser): Flow<Result<Unit>> = flow {
        emit(Result.loading())
        try {
            FirebaseUserService.saveUserData(user)
            // Attendre que l'état soit mis à jour
            currentUser.collect { result ->
                when (result) {
                    is Result.Success -> {
                        emit(Result.success(Unit))
                        return@collect
                    }
                    is Result.Error -> {
                        emit(result)
                        return@collect
                    }
                    is Result.Loading -> { /* continue waiting */ }
                }
            }
        } catch (e: Exception) {
            emit(Result.error(AppException.fromThrowable(e)))
        }
    }
    
    suspend fun updateUserName(newName: String): Flow<Result<Unit>> = flow {
        emit(Result.loading())
        try {
            var completed = false
            FirebaseUserService.updateUserName(newName) { success ->
                completed = true
                if (success) {
                    emit(Result.success(Unit))
                } else {
                    emit(Result.error(AppException.Data.ValidationError("name")))
                }
            }
            // Timeout après 30s
            kotlinx.coroutines.delay(30_000)
            if (!completed) {
                emit(Result.error(AppException.Network.Timeout))
            }
        } catch (e: Exception) {
            emit(Result.error(AppException.fromThrowable(e)))
        }
    }
    
    suspend fun updateProfileImage(bitmap: Bitmap): Flow<Result<String>> = flow {
        emit(Result.loading())
        try {
            var completed = false
            var resultUrl: String? = null
            
            FirebaseUserService.updateProfileImage(bitmap) { success, url ->
                completed = true
                if (success && url != null) {
                    resultUrl = url
                    emit(Result.success(url))
                } else {
                    emit(Result.error(AppException.Firebase.StorageError))
                }
            }
            
            // Timeout après 60s (upload peut être long)
            kotlinx.coroutines.delay(60_000)
            if (!completed) {
                emit(Result.error(AppException.Network.Timeout))
            }
        } catch (e: Exception) {
            emit(Result.error(AppException.fromThrowable(e)))
        }
    }
    
    suspend fun updateSubscriptionStatus(isSubscribed: Boolean): Flow<Result<Unit>> = flow {
        emit(Result.loading())
        try {
            FirebaseUserService.updateSubscriptionStatus(isSubscribed)
            emit(Result.success(Unit))
        } catch (e: Exception) {
            emit(Result.error(AppException.fromThrowable(e)))
        }
    }
    
    // === Utility Methods ===
    fun startListeningForSubscriptionChanges() {
        FirebaseUserService.startListeningForSubscriptionChanges()
    }
    
    fun stopListeningForSubscriptionChanges() {
        FirebaseUserService.stopListeningForSubscriptionChanges()
    }
    
    fun forceRefresh() {
        FirebaseUserService.forceRefreshUserData()
    }
}

// === Extensions pour faciliter l'usage ===
suspend fun <T> Flow<Result<T>>.awaitSuccess(): T? {
    collect { result ->
        when (result) {
            is Result.Success -> return result.data
            is Result.Error -> return null
            is Result.Loading -> { /* continue waiting */ }
        }
    }
    return null
}

suspend fun <T> Flow<Result<T>>.awaitResult(): Result<T> {
    var lastResult: Result<T>? = null
    collect { result ->
        lastResult = result
        if (result !is Result.Loading) {
            return result
        }
    }
    return lastResult ?: Result.error(AppException.Generic("No result received"))
}
