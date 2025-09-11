package com.love2loveapp.core.common

/**
 * Classe sealed pour la gestion unifiée des résultats
 * Inspirée du Result<T> de Kotlin mais adaptée pour l'UI avec Loading
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: AppException) : Result<Nothing>()
    object Loading : Result<Nothing>()
    
    // === Utilitaires ===
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error  
    val isLoading: Boolean get() = this is Loading
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    fun getOrDefault(defaultValue: T): T = when (this) {
        is Success -> data
        else -> defaultValue
    }
    
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (AppException) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }
    
    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }
    
    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(exception: AppException): Result<Nothing> = Error(exception)
        fun error(message: String, cause: Throwable? = null): Result<Nothing> = 
            Error(AppException.Generic(message, cause))
        fun loading(): Result<Nothing> = Loading
    }
}

/**
 * Extensions pour faciliter les transformations
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.success(transform(data))
    is Result.Error -> this
    is Result.Loading -> this
}

inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
    is Result.Success -> transform(data)
    is Result.Error -> this
    is Result.Loading -> this
}

/**
 * Conversion depuis les exceptions Kotlin standard
 */
inline fun <T> runCatchingResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: Exception) {
    Result.error(AppException.fromThrowable(e))
}
