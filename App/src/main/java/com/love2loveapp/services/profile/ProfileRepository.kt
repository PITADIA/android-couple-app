package com.love2loveapp.services.profile

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.love2loveapp.models.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 👤 ProfileRepository - Gestion centralisée du profil utilisateur
 * 
 * Équivalent Android du système Profil iOS avec :
 * - StateFlow pour reactive UI
 * - Firebase Firestore listeners temps réel
 * - Firebase Storage pour images profil
 * - CRUD operations sécurisées
 * - Cache local integration
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "ProfileRepository"
        private const val COLLECTION_USERS = "users"
        private const val STORAGE_PROFILE_IMAGES = "profile_images"
        private const val MAX_IMAGE_SIZE_MB = 5L
        private const val IMAGE_QUALITY = 80
    }

    // 🔥 States réactifs
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // 🎧 Firebase listener
    private var userListener: ListenerRegistration? = null

    // MARK: - Initialization

    /**
     * 🚀 Initialiser pour un utilisateur spécifique
     * Configure le listener temps réel Firestore
     */
    fun initializeForUser(userId: String) {
        Log.d(TAG, "🚀 Initialisation ProfileRepository pour user: $userId")
        setupUserListener(userId)
    }

    /**
     * 🎧 Setup listener Firestore temps réel
     * Met à jour automatiquement le profil en cas de changement
     */
    private fun setupUserListener(userId: String) {
        // Nettoyer listener précédent
        userListener?.remove()

        Log.d(TAG, "🎧 Configuration listener Firestore pour user: $userId")

        userListener = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener profil: ${error.message}", error)
                    _error.value = "Erreur synchronisation profil: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = UserProfile.fromFirestore(snapshot)
                    if (user != null) {
                        Log.d(TAG, "✅ Profil utilisateur mis à jour: [USER_MASKED]")
                        _currentUser.value = user
                        _error.value = null // Clear error on success
                    } else {
                        Log.e(TAG, "❌ Erreur parsing profil utilisateur")
                        _error.value = "Erreur parsing données profil"
                    }
                } else {
                    Log.w(TAG, "⚠️ Document profil utilisateur n'existe pas")
                    _currentUser.value = null
                }
            }
    }

    // MARK: - Profile Updates

    /**
     * ✏️ Mettre à jour le nom utilisateur
     * Update local + Firestore avec rollback en cas d'erreur
     */
    suspend fun updateUserName(newName: String): Result<Unit> {
        return try {
            _isLoading.value = true
            _error.value = null

            val trimmedName = newName.trim()
            if (trimmedName.isEmpty()) {
                return Result.failure(Exception("Le nom ne peut pas être vide"))
            }

            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            Log.d(TAG, "✏️ Mise à jour nom utilisateur: '$trimmedName'")

            // 🔥 Update Firestore
            firestore.collection(COLLECTION_USERS)
                .document(currentUser.uid)
                .update(
                    mapOf(
                        "name" to trimmedName,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            _isLoading.value = false
            Log.d(TAG, "✅ Nom utilisateur mis à jour avec succès")

            Result.success(Unit)

        } catch (e: Exception) {
            _isLoading.value = false
            val errorMessage = "Erreur mise à jour nom: ${e.message}"
            Log.e(TAG, "❌ $errorMessage", e)
            _error.value = errorMessage
            Result.failure(e)
        }
    }

    /**
     * 📸 Mettre à jour la photo de profil
     * ✅ CORRIGÉ: Utilise putBytes() comme iOS putData() au lieu de putFile()
     * Prend directement les bytes de l'image pour éviter les dépendances Context
     */
    suspend fun updateProfileImage(imageBytes: ByteArray): Result<String> {
        return try {
            _isLoading.value = true
            _error.value = null

            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            // 🔒 VÉRIFICATION TOKEN AUTHENTIFICATION (FIX 403)
            currentUser.getIdToken(true).await() // Force refresh token
            Log.d(TAG, "✅ Token Firebase rafraîchi pour: [USER_MASKED]")

            Log.d(TAG, "📸 Upload photo profil pour user: [USER_MASKED]")
            Log.d(TAG, "📏 Image size: ${imageBytes.size} bytes")

            // 🕐 VÉRIFICATION LIMITE FRÉQUENCE (60 secondes comme Firebase Rules)
            val profileImagePath = "profile_images/${currentUser.uid}/profile.jpg"
            val imageRef = storage.reference.child(profileImagePath)
            
            try {
                val existingMetadata = imageRef.metadata.await()
                val lastUploadTime = existingMetadata.creationTimeMillis
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpload = currentTime - lastUploadTime
                
                if (timeSinceLastUpload < 60_000) { // 60 secondes
                    val remainingTime = 60_000 - timeSinceLastUpload + 1000 // +1s sécurité
                    val remainingSeconds = remainingTime / 1000
                    Log.w(TAG, "⏰ Upload trop fréquent. Attendre ${remainingSeconds}s (règle Firebase)")
                    
                    // 🔄 Attendre le temps restant
                    kotlinx.coroutines.delay(remainingTime)
                    Log.d(TAG, "✅ Délai respecté, retry upload")
                }
            } catch (e: Exception) {
                // Pas de fichier existant ou erreur metadata → OK pour uploader
                Log.d(TAG, "ℹ️ Pas de fichier existant ou metadata inaccessible → Upload autorisé")
            }

            // ✅ Métadonnées identiques iOS
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("uploadedBy", currentUser.uid)
                .build()

            // ✅ CRUCIAL: putBytes() au lieu de putFile() - identique iOS putData()
            Log.d(TAG, "📤 Upload vers: $profileImagePath")
            val uploadTask = imageRef.putBytes(imageBytes, metadata).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()

            Log.d(TAG, "📤 Image uploadée avec succès: $downloadUrl")

            // 🔄 Update Firestore avec nouvelle URL (utilise profileImageURL comme iOS)
            firestore.collection(COLLECTION_USERS)
                .document(currentUser.uid)
                .update(
                    mapOf(
                        "profileImageURL" to downloadUrl.toString(), // ✅ Même nom champ que iOS
                        "profileImageUpdatedAt" to com.google.firebase.Timestamp.now(),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            _isLoading.value = false
            Log.d(TAG, "✅ Photo de profil mise à jour avec succès")

            Result.success(downloadUrl.toString())

        } catch (e: Exception) {
            _isLoading.value = false
            
            // 🛡️ GESTION SPÉCIALE ERREUR 403 (FIREBASE RULES)
            val errorMessage = when {
                e.message?.contains("403") == true || e.message?.contains("Permission denied") == true -> {
                    Log.w(TAG, "🛡️ Erreur 403 détectée - Problème authentification ou fréquence")
                    "Upload trop fréquent. Réessayez dans 1 minute."
                }
                e.message?.contains("network") == true -> {
                    "Problème de connexion internet"
                }
                e.message?.contains("size") == true -> {
                    "Image trop volumineuse (max 5MB)"
                }
                else -> "Erreur mise à jour photo: ${e.message}"
            }
            
            Log.e(TAG, "❌ $errorMessage", e)
            _error.value = errorMessage
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * 📅 Mettre à jour la date début relation
     */
    suspend fun updateRelationshipStartDate(date: Date): Result<Unit> {
        return try {
            _isLoading.value = true
            _error.value = null

            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            Log.d(TAG, "📅 Mise à jour date relation: $date")

            firestore.collection(COLLECTION_USERS)
                .document(currentUser.uid)
                .update(
                    mapOf(
                        "relationshipStartDate" to com.google.firebase.Timestamp(date),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            _isLoading.value = false
            Log.d(TAG, "✅ Date relation mise à jour avec succès")
            Result.success(Unit)

        } catch (e: Exception) {
            _isLoading.value = false
            val errorMessage = "Erreur mise à jour date: ${e.message}"
            Log.e(TAG, "❌ $errorMessage", e)
            _error.value = errorMessage
            Result.failure(e)
        }
    }

    /**
     * 🗑️ Supprimer le compte utilisateur selon RAPPORT_SUPPRESSION_COMPTE.md
     * Processus en 4 étapes avec réauthentification et Cloud Function
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            _isLoading.value = true
            _error.value = null

            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            Log.d(TAG, "🗑️ Début suppression compte utilisateur: [USER_MASKED]")

            // Étape 1: Réauthentification requise par Firebase pour opérations sensibles
            try {
                // Forcer une réauthentification récente via Google
                val googleAuthService = com.love2loveapp.services.GoogleAuthService.instance
                googleAuthService.reauthenticate()
                Log.d(TAG, "✅ Réauthentification réussie")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Réauthentification échouée, tentative directe: ${e.message}")
                // Continuer quand même, la Cloud Function peut gérer
            }

            // Étape 2: Appel Cloud Function pour nettoyage serveur (comme iOS)
            try {
                val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
                val serverCleanup = functions
                    .getHttpsCallable("deleteUserAccount")
                    .call()
                    .await()
                
                Log.d(TAG, "✅ Cloud Function de suppression exécutée")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erreur Cloud Function, nettoyage manuel: ${e.message}")
                
                // Fallback: nettoyage manuel comme avant
                // 1. Supprimer document Firestore
                firestore.collection(COLLECTION_USERS)
                    .document(currentUser.uid)
                    .delete()
                    .await()

                // 2. Supprimer images stockées (optionnel)
                try {
                    val imagesRef = storage.reference.child("$STORAGE_PROFILE_IMAGES")
                    val listResult = imagesRef.listAll().await()
                    listResult.items.forEach { item ->
                        if (item.name.startsWith(currentUser.uid)) {
                            item.delete().await()
                            Log.d(TAG, "🗑️ Image supprimée: ${item.name}")
                        }
                    }
                } catch (storageError: Exception) {
                    Log.w(TAG, "⚠️ Erreur suppression images: ${storageError.message}")
                }

                // 3. Supprimer compte Firebase Auth
                currentUser.delete().await()
            }

            _isLoading.value = false
            _currentUser.value = null

            Log.d(TAG, "✅ Compte utilisateur supprimé avec succès")
            Result.success(Unit)

        } catch (e: Exception) {
            _isLoading.value = false
            val errorMessage = "Erreur suppression compte: ${e.message}"
            Log.e(TAG, "❌ $errorMessage", e)
            _error.value = errorMessage
            Result.failure(e)
        }
    }

    /**
     * 🔄 Rafraîchir les données profil manuellement
     */
    suspend fun refreshProfile(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            Log.d(TAG, "🔄 Rafraîchissement profil manuel")

            val document = firestore.collection(COLLECTION_USERS)
                .document(currentUser.uid)
                .get()
                .await()

            if (document.exists()) {
                val user = UserProfile.fromFirestore(document)
                if (user != null) {
                    _currentUser.value = user
                    _error.value = null
                    Log.d(TAG, "✅ Profil rafraîchi avec succès")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Erreur parsing profil"))
                }
            } else {
                Result.failure(Exception("Profil utilisateur non trouvé"))
            }

        } catch (e: Exception) {
            val errorMessage = "Erreur rafraîchissement profil: ${e.message}"
            Log.e(TAG, "❌ $errorMessage", e)
            _error.value = errorMessage
            Result.failure(e)
        }
    }

    /**
     * 🧹 Nettoyer les ressources
     * Appeler lors de la déconnexion ou fermeture app
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage ProfileRepository")
        userListener?.remove()
        userListener = null
        _currentUser.value = null
        _error.value = null
        _isLoading.value = false
    }

    /**
     * ❌ Clear error state
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * 📊 Obtenir les statistiques profil
     */
    fun getProfileStats(): Map<String, Any> {
        val user = _currentUser.value
        return mapOf(
            "completionScore" to (user?.completionScore ?: 0),
            "hasPartner" to (user?.hasPartner ?: false),
            "isSubscribed" to (user?.isSubscribed ?: false),
            "relationshipDays" to (user?.relationshipStartDate?.let {
                ((Date().time - it.time) / (1000 * 60 * 60 * 24)).toInt()
            } ?: 0)
        )
    }
}
