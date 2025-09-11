package com.love2loveapp.core.repository

import android.content.Context
import android.content.Intent
import com.love2loveapp.core.common.Result
import kotlinx.coroutines.flow.Flow

/**
 * Interface Repository pour l'authentification
 * Abstraction qui permet de découpler la logique métier des implémentations Firebase
 */
interface AuthRepository {
    
    // === Observable State ===
    val isAuthenticated: Flow<Boolean>
    val isLoading: Flow<Boolean>
    val errorMessage: Flow<String?>
    
    // === Authentication Operations ===
    suspend fun signInWithGoogle(context: Context, data: Intent): Result<Unit>
    suspend fun signOut(context: Context): Result<Unit>
    suspend fun getCurrentUserId(): String?
    
    // === Initialization ===
    fun initialize(context: Context)
    fun getGoogleSignInIntent(context: Context): Intent
}

/**
 * Extension pour faciliter l'usage avec les ViewModels
 */
suspend fun AuthRepository.signInAndHandleResult(
    context: Context, 
    data: Intent,
    onSuccess: suspend () -> Unit = {},
    onError: suspend (String) -> Unit = {}
) {
    when (val result = signInWithGoogle(context, data)) {
        is Result.Success -> onSuccess()
        is Result.Error -> onError(result.exception.getLocalizedMessage(context))
        is Result.Loading -> { /* handled by Flow */ }
    }
}
