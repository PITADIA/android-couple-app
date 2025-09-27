package com.love2loveapp.data.repository

import android.util.Log
import com.love2loveapp.core.common.AppException
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.services.auth.AuthenticationService
import com.love2loveapp.core.services.firebase.FirebaseAuthService
import com.love2loveapp.core.services.firebase.FirebaseUserService
import com.love2loveapp.core.services.cache.UserCacheManager
import com.love2loveapp.domain.repository.UserRepository
import com.love2loveapp.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 🏗️ UserRepositoryImpl - Implémentation Repository Pattern
 * 
 * Responsabilités :
 * - Gestion des données utilisateur (cache + réseau)
 * - Synchronisation Firebase ↔ Cache local
 * - État réactif avec Flow/StateFlow
 * - Gestion d'erreurs centralisée
 * 
 * Architecture : Repository + Cache-First Strategy
 */
class UserRepositoryImpl(
    private val firebaseAuthService: FirebaseAuthService,
    private val firebaseUserService: FirebaseUserService,
    private val userCacheManager: UserCacheManager
) : UserRepository {
    
    companion object {
        private const val TAG = "UserRepositoryImpl"
    }
    
    // === State Management ===
    private val _currentUserFlow = MutableStateFlow<Result<User?>>(Result.Loading)
    override val currentUserFlow: StateFlow<Result<User?>> = _currentUserFlow.asStateFlow()
    
    private val _isLoadingFlow = MutableStateFlow(false)
    override val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow.asStateFlow()
    
    init {
        Log.d(TAG, "🏗️ Initialisation UserRepositoryImpl")
        initializeUserObservation()
    }
    
    // === Repository Implementation ===
    
    override suspend fun getCurrentUser(): Result<User?> {
        return try {
            _isLoadingFlow.value = true
            Log.d(TAG, "👤 Récupération utilisateur actuel")
            
            // 1. Vérifier cache local d'abord (Cache-First)
            val cachedUser = userCacheManager.getCachedUser()
            if (cachedUser != null && userCacheManager.isCacheValid()) {
                Log.d(TAG, "⚡ Utilisateur trouvé en cache: [EMAIL_MASKED]")
                val result = Result.Success(cachedUser)
                _currentUserFlow.value = result
                return result
            }
            
            // 2. Vérifier authentification Firebase
            val authResult = firebaseAuthService.getCurrentAuthUser()
            if (authResult !is Result.Success || authResult.data == null) {
                Log.d(TAG, "🚫 Pas d'utilisateur authentifié")
                val result = Result.Success(null)
                _currentUserFlow.value = result
                return result
            }
            
            // 3. Récupérer données complètes depuis Firestore
            val userResult = firebaseUserService.getUserData(authResult.data.uid)
            when (userResult) {
                is Result.Success -> {
                    val user = userResult.data
                    if (user != null) {
                        // 4. Mettre à jour le cache
                        userCacheManager.cacheUser(user)
                        Log.d(TAG, "✅ Utilisateur récupéré et mis en cache: [EMAIL_MASKED]")
                    }
                    
                    _currentUserFlow.value = userResult
                    userResult
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur récupération utilisateur: ${userResult.exception.message}")
                    _currentUserFlow.value = userResult
                    userResult
                }
                is Result.Loading -> {
                    Result.Loading
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception récupération utilisateur", e)
            val result = Result.Error(AppException.UserError("Failed to get current user", e))
            _currentUserFlow.value = result
            result
        } finally {
            _isLoadingFlow.value = false
        }
    }
    
    override suspend fun updateUser(user: User): Result<User> {
        return try {
            _isLoadingFlow.value = true
            Log.d(TAG, "📝 Mise à jour utilisateur: [EMAIL_MASKED]")
            
            // 1. Mise à jour Firebase
            val updateResult = firebaseUserService.updateUserData(user)
            
            when (updateResult) {
                is Result.Success -> {
                    // 2. Mise à jour cache local
                    userCacheManager.cacheUser(updateResult.data)
                    
                    // 3. Notifier changement
                    _currentUserFlow.value = Result.Success(updateResult.data)
                    
                    Log.d(TAG, "✅ Utilisateur mis à jour avec succès")
                    updateResult
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur mise à jour utilisateur: ${updateResult.exception.message}")
                    updateResult
                }
                is Result.Loading -> updateResult
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception mise à jour utilisateur", e)
            Result.Error(AppException.UserError("Failed to update user", e))
        } finally {
            _isLoadingFlow.value = false
        }
    }
    
    override suspend fun getUserById(userId: String): Result<User?> {
        return try {
            Log.d(TAG, "🔍 Récupération utilisateur par ID: $userId")
            
            // Vérifier cache d'abord
            val cachedUser = userCacheManager.getCachedUserById(userId)
            if (cachedUser != null && userCacheManager.isCacheValid()) {
                Log.d(TAG, "⚡ Utilisateur trouvé en cache: $userId")
                return Result.Success(cachedUser)
            }
            
            // Récupérer depuis Firebase
            val result = firebaseUserService.getUserData(userId)
            
            // Mettre en cache si succès
            if (result is Result.Success && result.data != null) {
                userCacheManager.cacheUser(result.data)
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception récupération utilisateur par ID", e)
            Result.Error(AppException.UserError("Failed to get user by ID", e))
        }
    }
    
    override suspend fun getPartnerInfo(): Result<User?> {
        return try {
            Log.d(TAG, "💑 Récupération informations partenaire")
            
            // 1. Récupérer utilisateur actuel
            val currentUserResult = getCurrentUser()
            if (currentUserResult !is Result.Success || currentUserResult.data == null) {
                return Result.Success(null)
            }
            
            val currentUser = currentUserResult.data
            val partnerId = currentUser.partnerId
            
            if (partnerId.isNullOrEmpty()) {
                Log.d(TAG, "🚫 Pas de partenaire configuré")
                return Result.Success(null)
            }
            
            // 2. Récupérer données du partenaire
            getUserById(partnerId)
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception récupération partenaire", e)
            Result.Error(AppException.UserError("Failed to get partner info", e))
        }
    }
    
    override suspend fun connectPartner(partnerCode: String): Result<User> {
        return try {
            _isLoadingFlow.value = true
            Log.d(TAG, "🔗 Connexion partenaire avec code: $partnerCode")
            
            // Utiliser Firebase Functions pour la connexion sécurisée
            val result = firebaseUserService.connectPartner(partnerCode)
            
            when (result) {
                is Result.Success -> {
                    // Mettre à jour cache local
                    userCacheManager.cacheUser(result.data)
                    _currentUserFlow.value = Result.Success(result.data)
                    
                    Log.d(TAG, "✅ Partenaire connecté avec succès")
                    result
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur connexion partenaire: ${result.exception.message}")
                    result
                }
                is Result.Loading -> result
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception connexion partenaire", e)
            Result.Error(AppException.UserError("Failed to connect partner", e))
        } finally {
            _isLoadingFlow.value = false
        }
    }
    
    override suspend fun disconnectPartner(): Result<User> {
        return try {
            _isLoadingFlow.value = true
            Log.d(TAG, "💔 Déconnexion partenaire")
            
            val result = firebaseUserService.disconnectPartner()
            
            when (result) {
                is Result.Success -> {
                    // Mettre à jour cache local
                    userCacheManager.cacheUser(result.data)
                    _currentUserFlow.value = Result.Success(result.data)
                    
                    Log.d(TAG, "✅ Partenaire déconnecté avec succès")
                    result
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Erreur déconnexion partenaire: ${result.exception.message}")
                    result
                }
                is Result.Loading -> result
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception déconnexion partenaire", e)
            Result.Error(AppException.UserError("Failed to disconnect partner", e))
        } finally {
            _isLoadingFlow.value = false
        }
    }
    
    override suspend fun refreshUserData(): Result<User?> {
        return try {
            Log.d(TAG, "🔄 Rafraîchissement données utilisateur")
            
            // Forcer rechargement depuis Firebase (bypass cache)
            userCacheManager.invalidateCache()
            getCurrentUser()
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception rafraîchissement utilisateur", e)
            Result.Error(AppException.UserError("Failed to refresh user data", e))
        }
    }
    
    // === Private Methods ===
    
    /**
     * Initialiser l'observation des changements utilisateur
     */
    private fun initializeUserObservation() {
        // Observer les changements d'authentification Firebase
        // pour synchroniser automatiquement les données utilisateur
        
        // TODO: Implémenter listener Firebase Auth
        // firebaseAuthService.authStateChanges
        //     .map { authUser ->
        //         if (authUser != null) {
        //             getCurrentUser()
        //         } else {
        //             Result.Success(null)
        //         }
        //     }
        //     .launchIn(scope)
    }
    
    // === Cache Management ===
    
    override fun clearUserCache() {
        Log.d(TAG, "🧹 Nettoyage cache utilisateur")
        userCacheManager.clearCache()
        _currentUserFlow.value = Result.Loading
    }
    
    override fun getCachedUser(): User? {
        return userCacheManager.getCachedUser()
    }
}
