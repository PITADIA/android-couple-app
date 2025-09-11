package com.love2loveapp.core.services.repository

import android.content.Context
import android.content.Intent
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.common.runCatchingResult
import com.love2loveapp.core.repository.AuthRepository
import com.love2loveapp.core.services.firebase.FirebaseAuthService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implémentation Firebase du AuthRepository
 * Délègue vers FirebaseAuthService mais expose une interface Repository clean
 */
class FirebaseAuthRepositoryImpl : AuthRepository {
    
    // === Observable State (délégation vers service) ===
    override val isAuthenticated: Flow<Boolean> = FirebaseAuthService.isAuthenticated
    override val isLoading: Flow<Boolean> = FirebaseAuthService.isLoading
    override val errorMessage: Flow<String?> = FirebaseAuthService.errorMessage
    
    // === Initialization ===
    override fun initialize(context: Context) {
        FirebaseAuthService.initGoogleSignIn(context)
        FirebaseAuthService.setupAuthListener()
    }
    
    override fun getGoogleSignInIntent(context: Context): Intent {
        return FirebaseAuthService.getGoogleSignInIntent(context)
    }
    
    // === Authentication Operations ===
    override suspend fun signInWithGoogle(context: Context, data: Intent): Result<Unit> = 
        runCatchingResult {
            suspendCancellableCoroutine { continuation ->
                FirebaseAuthService.handleGoogleSignInResult(data) { success, error ->
                    if (success) {
                        continuation.resume(Unit)
                    } else {
                        val exception = when {
                            error?.contains("Google", ignoreCase = true) == true -> 
                                AppException.Auth.GoogleSignInError(0)
                            else -> AppException.Auth.SignInFailed
                        }
                        continuation.resume(throw exception)
                    }
                }
            }
        }
    
    override suspend fun signOut(context: Context): Result<Unit> = 
        runCatchingResult {
            suspendCancellableCoroutine { continuation ->
                FirebaseAuthService.googleSignOut(context) {
                    continuation.resume(Unit)
                }
            }
        }
    
    override suspend fun getCurrentUserId(): String? {
        return FirebaseAuthService.getCurrentUserId()
    }
}
