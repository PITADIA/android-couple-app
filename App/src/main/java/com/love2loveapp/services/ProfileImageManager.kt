package com.love2loveapp.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.love2loveapp.services.cache.UserCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 🎯 GESTIONNAIRE CENTRALISÉ DES PHOTOS DE PROFIL
 * 
 * Architecture identique à iOS ProfileImageManager :
 * - Cache multi-niveaux (mémoire, disque, UserDefaults)
 * - Upload Storage + mise à jour Firestore
 * - Download / génération d'URL signée pour le partenaire
 * - Invalidation automatique du cache
 * - Point d'entrée unique pour toute l'application
 */
@Singleton
class ProfileImageManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val cloudFunctionService: CloudFunctionService
) {
    companion object {
        private const val TAG = "ProfileImageManager"
        private const val COLLECTION_USERS = "users"
    }

    // 🔄 États observables
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 📸 Image utilisateur actuel (cache mémoire)
    private val _currentUserImage = MutableStateFlow<Bitmap?>(null)
    val currentUserImage: StateFlow<Bitmap?> = _currentUserImage.asStateFlow()

    // 👥 Image partenaire (cache mémoire)
    private val _partnerImage = MutableStateFlow<Bitmap?>(null)
    val partnerImage: StateFlow<Bitmap?> = _partnerImage.asStateFlow()

    // 🏠 Cache manager instance
    private var cacheManager: UserCacheManager? = null

    /**
     * 🚀 INITIALISATION
     * À appeler au démarrage de l'application
     */
    fun initialize(context: Context) {
        Log.d(TAG, "🚀 Initialisation ProfileImageManager")
        cacheManager = UserCacheManager.getInstance(context)
        
        // Chargement initial depuis le cache
        loadUserImageFromCache()
        loadPartnerImageFromCache()
    }

    // ═══════════════════════════════════════════════════════════════
    // 📸 GESTION IMAGE UTILISATEUR
    // ═══════════════════════════════════════════════════════════════

    /**
     * 📂 CHARGEMENT IMAGE UTILISATEUR DEPUIS CACHE
     * Hiérarchie : mémoire → disque → null
     */
    private fun loadUserImageFromCache() {
        Log.d(TAG, "📂 Chargement image utilisateur depuis cache...")
        
        // Priorité 1: Cache mémoire
        if (_currentUserImage.value != null) {
            Log.d(TAG, "✅ Image utilisateur déjà en mémoire")
            return
        }

        // Priorité 2: Cache disque
        try {
            val cachedImage = cacheManager?.getCachedProfileImage()
            if (cachedImage != null) {
                _currentUserImage.value = cachedImage
                Log.d(TAG, "✅ Image utilisateur chargée depuis cache disque")
            } else {
                Log.d(TAG, "ℹ️ Aucune image utilisateur en cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement cache utilisateur: ${e.message}")
        }
    }

    /**
     * 🎨 MISE À JOUR IMAGE UTILISATEUR (TEMPORAIRE)
     * Pour onboarding - stockage temporaire sans upload
     */
    fun setTemporaryUserImage(bitmap: Bitmap) {
        Log.d(TAG, "🎨 Stockage temporaire image utilisateur: ${bitmap.width}x${bitmap.height}")
        _currentUserImage.value = bitmap
        
        // Cache immédiat pour affichage (sans URL car pas encore uploadée)
        cacheManager?.setCachedProfileImage(bitmap, null)
    }

    /**
     * 🔥 UPLOAD IMAGE UTILISATEUR VERS FIREBASE
     * Pour menu - upload immédiat + mise à jour cache
     */
    suspend fun uploadUserImage(bitmap: Bitmap): Result<String> {
        return try {
            _isLoading.value = true
            _error.value = null

            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            // 🔒 VÉRIFICATION TOKEN AUTHENTIFICATION (FIX 403)
            currentUser.getIdToken(true).await() // Force refresh token
            Log.d(TAG, "✅ Token Firebase rafraîchi pour: ${currentUser.uid}")

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
                    
                    // 🔄 Attendre le temps restant + 1 seconde de sécurité
                    kotlinx.coroutines.delay(remainingTime)
                    Log.d(TAG, "✅ Délai respecté, retry upload")
                }
            } catch (e: Exception) {
                // Pas de fichier existant ou erreur metadata → OK pour uploader
                Log.d(TAG, "ℹ️ Pas de fichier existant ou metadata inaccessible → Upload autorisé")
            }

            Log.d(TAG, "🔥 Upload image utilisateur pour: ${currentUser.uid}")

            // Conversion bitmap → ByteArray
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val imageBytes = outputStream.toByteArray()

            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("uploadedBy", currentUser.uid)
                .build()

            val uploadTask = imageRef.putBytes(imageBytes, metadata).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()

            Log.d(TAG, "✅ Upload réussi: $downloadUrl")

            // Mise à jour Firestore (champs identiques iOS)
            firestore.collection(COLLECTION_USERS)
                .document(currentUser.uid)
                .update(
                    mapOf(
                        "profileImageURL" to downloadUrl.toString(),
                        "profileImageUpdatedAt" to com.google.firebase.Timestamp.now(),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            // Mise à jour cache avec URL
            cacheManager?.setCachedProfileImage(bitmap, downloadUrl.toString())
            
            // 🔄 FORCER MISE À JOUR STATEFLOW IMMÉDIATE
            _currentUserImage.value = bitmap
            Log.d(TAG, "🔄 StateFlow utilisateur forcé avec nouvelle image: ${bitmap.width}x${bitmap.height}")

            _isLoading.value = false
            Log.d(TAG, "✅ Image utilisateur mise à jour avec succès")

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
                else -> "Erreur upload image: ${e.message}"
            }
            
            Log.e(TAG, "❌ $errorMessage", e)
            _error.value = errorMessage
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * 🎯 FINALISATION ONBOARDING
     * Upload de l'image temporaire à la fin de l'onboarding
     */
    suspend fun finalizeOnboardingImage(): Result<String?> {
        val tempImage = _currentUserImage.value
        return if (tempImage != null) {
            Log.d(TAG, "🎯 Finalisation image onboarding...")
            uploadUserImage(tempImage)
        } else {
            Log.d(TAG, "ℹ️ Pas d'image à finaliser")
            Result.success(null)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 👥 GESTION IMAGE PARTENAIRE
    // ═══════════════════════════════════════════════════════════════

    /**
     * 📂 CHARGEMENT IMAGE PARTENAIRE DEPUIS CACHE
     */
    private fun loadPartnerImageFromCache() {
        Log.d(TAG, "📂 Chargement image partenaire depuis cache...")
        
        try {
            val cachedImage = cacheManager?.getCachedPartnerImage()
            if (cachedImage != null) {
                _partnerImage.value = cachedImage
                Log.d(TAG, "✅ Image partenaire chargée depuis cache")
            } else {
                Log.d(TAG, "ℹ️ Aucune image partenaire en cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement cache partenaire: ${e.message}")
        }
    }

    /**
     * 🔄 SYNCHRONISATION IMAGE PARTENAIRE
     * Architecture modifiée pour utiliser Cloud Function comme iOS
     */
    suspend fun syncPartnerImage(partnerImageURL: String?, partnerImageUpdatedAt: com.google.firebase.Timestamp?) {
        Log.d(TAG, "🔄 Synchronisation image partenaire...")
        Log.d(TAG, "   URL: $partnerImageURL")
        Log.d(TAG, "   UpdatedAt: $partnerImageUpdatedAt")

        if (partnerImageURL.isNullOrEmpty()) {
            Log.d(TAG, "ℹ️ Pas d'URL partenaire - effacement cache")
            _partnerImage.value = null
            cacheManager?.setCachedPartnerImage(null, null)
            return
        }

        try {
            // 🔍 EXTRACTION ID PARTENAIRE DE L'URL FIREBASE STORAGE
            val partnerId = extractPartnerIdFromUrl(partnerImageURL)
            if (partnerId == null) {
                Log.e(TAG, "❌ Impossible d'extraire ID partenaire de l'URL: $partnerImageURL")
                return
            }

            Log.d(TAG, "🎯 ID partenaire extrait: $partnerId")

            // Vérifier si on a déjà cette version en cache
            val cachedURL = cacheManager?.getCachedPartnerProfileImageURL()
            val cachedUpdatedAt = cacheManager?.getCachedPartnerProfileImageUpdatedAt()

            val needsUpdate = cachedURL != partnerImageURL || 
                            cachedUpdatedAt != partnerImageUpdatedAt?.toDate()?.time

            if (!needsUpdate && _partnerImage.value != null) {
                Log.d(TAG, "✅ Image partenaire déjà à jour")
                return
            }

            // 🔐 NOUVELLE APPROCHE : Cloud Function sécurisée comme iOS
            Log.d(TAG, "🔐 Téléchargement sécurisé via Cloud Function...")
            downloadAndCachePartnerImageSecure(partnerId, partnerImageURL, partnerImageUpdatedAt?.toDate()?.time)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sync image partenaire: ${e.message}")
        }
    }

    /**
     * 🔍 EXTRACTION ID PARTENAIRE DE L'URL FIREBASE STORAGE
     * Parse l'URL pour extraire l'UID du partenaire
     */
    private fun extractPartnerIdFromUrl(firebaseUrl: String): String? {
        return try {
            // URL Format: https://firebasestorage.googleapis.com/v0/b/PROJECT_ID.appspot.com/o/profile_images%2FUSER_ID%2Fprofile.jpg?alt=media&token=...
            // 🎯 ÉTAPE 1 : Extraire le chemin complet (comme iOS)
            val urlPattern = "/o/(.*?)\\?".toRegex()
            val urlMatch = urlPattern.find(firebaseUrl)
            
            if (urlMatch == null) {
                Log.e(TAG, "❌ Format URL Firebase invalide - pas de match /o/...?")
                return null
            }
            
            val encodedPath = urlMatch.groupValues[1]
            Log.d(TAG, "🔍 Chemin encodé extrait: $encodedPath")
            
            // 🎯 ÉTAPE 2 : Décoder les caractères échappés (comme iOS decodeURIComponent)
            val decodedPath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
            Log.d(TAG, "🔍 Chemin décodé: $decodedPath")
            
            // 🎯 ÉTAPE 3 : Extraire l'ID depuis le chemin décodé
            // Format : "profile_images/USER_ID/profile.jpg"
            val pathPattern = "profile_images/([^/]+)/".toRegex()
            val pathMatch = pathPattern.find(decodedPath)
            
            val partnerId = pathMatch?.groupValues?.get(1)
            
            if (partnerId != null) {
                Log.d(TAG, "✅ ID partenaire extrait avec succès: $partnerId")
            } else {
                Log.e(TAG, "❌ Impossible d'extraire ID depuis le chemin décodé: $decodedPath")
            }
            
            Log.d(TAG, "🔍 Extraction ID partenaire: '$partnerId' depuis URL")
            partnerId
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur extraction ID partenaire: ${e.message}")
            null
        }
    }

    /**
     * 🔐 TÉLÉCHARGEMENT SÉCURISÉ IMAGE PARTENAIRE via Cloud Function
     * Architecture identique à iOS - utilise getPartnerProfileImage
     */
    private suspend fun downloadAndCachePartnerImageSecure(partnerId: String, originalUrl: String, updatedAt: Long?) {
        Log.d(TAG, "🔐 Début téléchargement sécurisé pour partenaire: $partnerId")
        
        try {
            // 1. APPEL CLOUD FUNCTION sécurisée (comme iOS)
            val cloudResponse = cloudFunctionService.getPartnerProfileImage(partnerId)
            
            if (!cloudResponse.success) {
                Log.w(TAG, "⚠️ Cloud Function échouée: ${cloudResponse.reason} - ${cloudResponse.message}")
                
                // Gérer cas spéciaux
                when (cloudResponse.reason) {
                    "NO_PROFILE_IMAGE" -> {
                        Log.d(TAG, "ℹ️ Partenaire n'a pas de photo de profil")
                        _partnerImage.value = null
                        cacheManager?.setCachedPartnerImage(null, null)
                    }
                    "PERMISSION_DENIED" -> {
                        Log.e(TAG, "🛡️ Permission refusée pour accéder à l'image partenaire")
                        _partnerImage.value = null
                        cacheManager?.setCachedPartnerImage(null, null)
                    }
                    else -> {
                        Log.e(TAG, "❌ Erreur Cloud Function: ${cloudResponse.message}")
                        _partnerImage.value = null
                        cacheManager?.setCachedPartnerImage(null, null)
                    }
                }
                return
            }

            // 2. TÉLÉCHARGEMENT depuis URL signée
            val signedUrl = cloudResponse.imageUrl
            if (signedUrl.isNullOrEmpty()) {
                Log.e(TAG, "❌ URL signée vide reçue de Cloud Function")
                _partnerImage.value = null
                cacheManager?.setCachedPartnerImage(null, null)
                return
            }

            Log.d(TAG, "🔗 URL signée reçue - téléchargement...")
            
            // 3. TÉLÉCHARGEMENT HTTP avec OkHttp (URLs signées sont publiques temporairement)
            val bitmap = downloadImageFromSignedUrl(signedUrl)
            
            if (bitmap != null) {
                Log.d(TAG, "✅ Image partenaire téléchargée: ${bitmap.width}x${bitmap.height}")
                
                // 4. MISE EN CACHE avec URL originale pour détection changements
                cacheManager?.setCachedPartnerImage(bitmap, originalUrl, updatedAt)
                _partnerImage.value = bitmap
                
                Log.d(TAG, "✅ Image partenaire sécurisée mise en cache et StateFlow mis à jour")
            } else {
                Log.e(TAG, "❌ Échec décodage image depuis URL signée")
                _partnerImage.value = null
                cacheManager?.setCachedPartnerImage(null, null)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur téléchargement sécurisé partenaire: ${e.message}", e)
            _partnerImage.value = null
            cacheManager?.setCachedPartnerImage(null, null)
        }
    }

    /**
     * 📥 TÉLÉCHARGEMENT HTTP depuis URL signée
     * Utilise OkHttp pour accéder aux URLs temporaires générées par Cloud Function
     */
    private suspend fun downloadImageFromSignedUrl(signedUrl: String): Bitmap? {
        return try {
            Log.d(TAG, "📥 Téléchargement HTTP depuis URL signée...")
            
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            
            val request = Request.Builder()
                .url(signedUrl)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Réponse HTTP échouée: ${response.code} ${response.message}")
                return null
            }
            
            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                Log.e(TAG, "❌ Corps de réponse vide")
                return null
            }
            
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            response.close()
            
            if (bitmap != null) {
                Log.d(TAG, "✅ Image décodée depuis URL signée: ${bitmap.width}x${bitmap.height}")
            }
            
            bitmap
            
        } catch (e: IOException) {
            Log.e(TAG, "❌ Erreur réseau téléchargement: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur téléchargement depuis URL signée: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 🧹 UTILITAIRES
    // ═══════════════════════════════════════════════════════════════

    /**
     * 🧹 NETTOYAGE CACHE
     */
    fun clearCache() {
        Log.d(TAG, "🧹 Nettoyage cache ProfileImageManager")
        _currentUserImage.value = null
        _partnerImage.value = null
        cacheManager?.setCachedProfileImage(null, null)
        cacheManager?.setCachedPartnerImage(null, null)
    }

    /**
     * 🔄 RECHARGEMENT COMPLET
     */
    fun refresh() {
        Log.d(TAG, "🔄 Rechargement complet ProfileImageManager")
        loadUserImageFromCache()
        loadPartnerImageFromCache()
    }

    /**
     * 🔄 FORCER RECHARGEMENT UTILISATEUR
     * À utiliser quand on sait que le cache a été modifié depuis l'extérieur
     */
    fun forceRefreshUserImage() {
        Log.d(TAG, "🔄 FORCE refresh image utilisateur")
        loadUserImageFromCache()
        
        // Également vérifier s'il y a une nouvelle version sur Firebase
        auth.currentUser?.uid?.let { userId ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val userDoc = firestore.collection("users").document(userId).get().await()
                    if (userDoc.exists()) {
                        val newImageUrl = userDoc.getString("profileImageURL")
                        val newImageUpdatedAt = userDoc.getTimestamp("profileImageUpdatedAt")?.toDate()?.time
                        
                        // Vérifier si on a une version plus récente
                        val cachedUrl = cacheManager?.getCachedProfileImageURL()
                        if (newImageUrl != cachedUrl && !newImageUrl.isNullOrEmpty()) {
                            Log.d(TAG, "🔥 Nouvelle version détectée sur Firebase - téléchargement...")
                            downloadAndCacheUserImageFromUrl(newImageUrl, newImageUpdatedAt)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur vérification Firebase pour refresh: ${e.message}")
                }
            }
        }
    }
    
    /**
     * ⬇️ TÉLÉCHARGEMENT IMAGE UTILISATEUR DEPUIS URL
     * Similaire au téléchargement partenaire mais pour l'utilisateur actuel
     */
    private suspend fun downloadAndCacheUserImageFromUrl(imageUrl: String, updatedAt: Long?) {
        Log.d(TAG, "⬇️ Téléchargement image utilisateur depuis: $imageUrl")
        
        try {
            val imageRef = storage.getReferenceFromUrl(imageUrl)
            val imageBytes = imageRef.getBytes(5 * 1024 * 1024).await()
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            if (bitmap != null) {
                Log.d(TAG, "✅ Image utilisateur téléchargée: ${bitmap.width}x${bitmap.height}")
                
                // Mettre à jour cache et StateFlow
                cacheManager?.setCachedProfileImage(bitmap, imageUrl)
                _currentUserImage.value = bitmap
                
                Log.d(TAG, "✅ Image utilisateur mise à jour via téléchargement Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur téléchargement image utilisateur: ${e.message}")
        }
    }

    /**
     * 🎯 MISE À JOUR EXTERNE DU CACHE
     * À appeler quand une autre partie du code modifie le cache
     * (par exemple depuis AndroidPhotoEditorView legacy)
     */
    fun notifyCacheChanged() {
        Log.d(TAG, "🔔 Notification changement cache externe - rechargement StateFlow")
        loadUserImageFromCache()
        loadPartnerImageFromCache()
    }
}
