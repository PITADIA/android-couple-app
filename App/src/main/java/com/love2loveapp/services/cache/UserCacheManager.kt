package com.love2loveapp.services.cache

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.love2loveapp.models.UserProfile
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * 📱 UserCacheManager Android - Cache Sophistiqué Utilisateur + Images
 * 
 * Architecture équivalente iOS UserCacheManager:
 * - Cache données utilisateur JSON avec TTL 7 jours
 * - Cache mémoire ultra-rapide (Bitmap en RAM) 
 * - Cache disque persistant (Base64 SharedPreferences)
 * - URLs pour détection changements
 * - TTL et invalidation automatique
 * - Performance optimisée pour composants UI
 * - Équivalent complet du UserCacheManager iOS
 */
class UserCacheManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "UserCacheManager"
        private const val PREFS_NAME = "user_cache_complete"
        
        // Keys Cache Données Utilisateur (équivalent iOS)
        private const val KEY_USER_DATA = "cached_user_data"
        private const val KEY_USER_TIMESTAMP = "cached_user_timestamp"
        
        // Keys Cache Images (équivalent iOS)
        private const val KEY_PROFILE_IMAGE = "cached_profile_image"
        private const val KEY_PROFILE_URL = "cached_profile_url"
        private const val KEY_PARTNER_IMAGE = "cached_partner_image"
        private const val KEY_PARTNER_URL = "cached_partner_url"
        private const val KEY_PARTNER_UPDATED_AT = "cached_partner_updated_at"
        
        // TTL Cache - 7 jours comme iOS
        private const val USER_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000L
        
        @Volatile
        private var instance: UserCacheManager? = null
        
        fun getInstance(context: Context): UserCacheManager {
            return instance ?: synchronized(this) {
                instance ?: UserCacheManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Cache mémoire ultra-rapide images
    private var profileImageCache: Bitmap? = null
    private var partnerImageCache: Bitmap? = null
    
    // URLs pour vérification changements
    private var cachedProfileImageURL: String? = null
    private var cachedPartnerImageURL: String? = null
    
    init {
        Log.d(TAG, "✅ UserCacheManager initialisé avec cache utilisateur + images")
        // Charger URLs depuis SharedPreferences
        cachedProfileImageURL = prefs.getString(KEY_PROFILE_URL, null)
        cachedPartnerImageURL = prefs.getString(KEY_PARTNER_URL, null)
    }
    
    // ============
    // CACHE DONNÉES UTILISATEUR (équivalent iOS)
    // ============
    
    /**
     * Cache l'utilisateur après chargement Firebase
     * Équivalent de cacheUser() iOS
     */
    fun cacheUser(user: UserProfile) {
        try {
            Log.d(TAG, "💾 Mise en cache utilisateur: ${user.name}")
            val json = gson.toJson(user)
            val timestamp = System.currentTimeMillis()
            
            prefs.edit()
                .putString(KEY_USER_DATA, json)
                .putLong(KEY_USER_TIMESTAMP, timestamp)
                .apply()
                
            Log.d(TAG, "✅ Utilisateur mis en cache avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur encodage utilisateur: ${e.message}", e)
        }
    }
    
    /**
     * Récupère l'utilisateur en cache (instantané, TTL 7 jours)
     * Équivalent de getCachedUser() iOS
     */
    fun getCachedUser(): UserProfile? {
        val json = prefs.getString(KEY_USER_DATA, null)
        if (json.isNullOrEmpty()) {
            Log.d(TAG, "📂 Aucun utilisateur en cache")
            return null
        }
        
        // Vérifier l'âge du cache (7 jours)
        val timestamp = prefs.getLong(KEY_USER_TIMESTAMP, 0L)
        if (timestamp > 0) {
            val age = System.currentTimeMillis() - timestamp
            if (age > USER_CACHE_MAX_AGE_MS) {
                Log.d(TAG, "⏰ Cache expiré (${age / (60 * 60 * 1000)}h), nettoyage")
                clearUserCache()
                return null
            }
        }
        
        return try {
            val user = gson.fromJson(json, UserProfile::class.java)
            Log.d(TAG, "🚀 Utilisateur trouvé en cache: ${user.name}")
            user
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur décodage cache: ${e.message}. Nettoyage…", e)
            clearUserCache()
            null
        }
    }
    
    /**
     * Vérifie si un utilisateur est en cache
     * Équivalent de hasCachedUser() iOS  
     */
    fun hasCachedUser(): Boolean {
        return prefs.contains(KEY_USER_DATA)
    }
    
    /**
     * Met à jour un utilisateur en cache avec transformation
     * Équivalent de updateCachedUser() iOS
     */
    fun updateCachedUser(transform: (UserProfile) -> UserProfile) {
        val cachedUser = getCachedUser()
        if (cachedUser != null) {
            val updatedUser = transform(cachedUser)
            cacheUser(updatedUser)
            Log.d(TAG, "🔄 Utilisateur en cache mis à jour")
        }
    }
    
    /**
     * Nettoie le cache utilisateur (pas les images)
     * Équivalent partiel de clearCache() iOS
     */
    private fun clearUserCache() {
        prefs.edit()
            .remove(KEY_USER_DATA)
            .remove(KEY_USER_TIMESTAMP)
            .apply()
        Log.d(TAG, "🗑️ Cache données utilisateur nettoyé")
    }
    
    // ============
    // CACHE IMAGES DE PROFIL (équivalent iOS)
    // ============
    
    /**
     * Récupère image de profil depuis cache (mémoire puis disque)
     * Équivalent de getCachedProfileImage() iOS
     */
    fun getCachedProfileImage(): Bitmap? {
        Log.d(TAG, "🔍 getCachedProfileImage() - DÉBUT recherche")
        
        // PRIORITÉ 1: Cache mémoire
        Log.d(TAG, "📊 Cache mémoire profileImageCache: ${if (profileImageCache == null) "NULL" else "PRÉSENT (${profileImageCache?.width}x${profileImageCache?.height})"}")
        if (profileImageCache != null) {
            Log.d(TAG, "🚀 SUCCESS! Image profil depuis cache mémoire - ${profileImageCache?.width}x${profileImageCache?.height}")
            return profileImageCache
        }
        
        // PRIORITÉ 2: Cache disque (SharedPreferences)
        Log.d(TAG, "💾 Cache mémoire vide - Tentative lecture SharedPreferences...")
        try {
            Log.d(TAG, "🔧 Lecture clé '$KEY_PROFILE_IMAGE' depuis SharedPreferences...")
            val base64String = prefs.getString(KEY_PROFILE_IMAGE, null)
            Log.d(TAG, "📄 Base64String: ${if (base64String.isNullOrEmpty()) "NULL/EMPTY - AUCUNE DONNÉE" else "PRÉSENT (${base64String.length} chars)"}")
            
            if (!base64String.isNullOrEmpty()) {
                Log.d(TAG, "🔄 Décodage Base64 en Bitmap...")
                val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                Log.d(TAG, "📊 Bytes décodés: ${decodedBytes.size} bytes")
                
                profileImageCache = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                Log.d(TAG, "🖼️ BitmapFactory résultat: ${if (profileImageCache == null) "NULL - ÉCHEC DÉCODAGE" else "SUCCESS (${profileImageCache?.width}x${profileImageCache?.height})"}")
                
                if (profileImageCache != null) {
                    Log.d(TAG, "✅ SUCCESS! Image profil chargée depuis disque et mise en cache mémoire")
                    return profileImageCache
                } else {
                    Log.e(TAG, "❌ ERREUR: BitmapFactory a retourné null - données corrompues?")
                }
            } else {
                Log.w(TAG, "❌ PROBLÈME: Base64String vide - Image n'a jamais été mise en cache!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERREUR CRITIQUE chargement cache disque profil: ${e.message}")
            Log.e(TAG, "❌ StackTrace: ${e.stackTraceToString()}")
        }
        
        Log.w(TAG, "❌ ÉCHEC TOTAL: Aucune image profil en cache (mémoire + disque)")
        return null
    }
    
    /**
     * Met en cache l'image de profil (mémoire + disque)
     * Équivalent de setCachedProfileImage() iOS
     */
    fun setCachedProfileImage(image: Bitmap?, url: String?) {
        Log.d(TAG, "💾 setCachedProfileImage() - DÉBUT sauvegarde")
        Log.d(TAG, "📊 Paramètres - image: ${if (image == null) "NULL" else "PRÉSENT (${image.width}x${image.height})"}")
        Log.d(TAG, "📊 Paramètres - url: ${url ?: "NULL"}")
        
        profileImageCache = image
        cachedProfileImageURL = url
        Log.d(TAG, "🧠 Cache mémoire mis à jour")
        
        if (image != null) {
            try {
                Log.d(TAG, "🔄 Compression JPEG qualité 80%...")
                val outputStream = ByteArrayOutputStream()
                val compressSuccess = image.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                Log.d(TAG, "📊 Compression: ${if (compressSuccess) "SUCCESS" else "ÉCHEC"}")
                
                if (compressSuccess) {
                    val compressedBytes = outputStream.toByteArray()
                    Log.d(TAG, "📊 Données compressées: ${compressedBytes.size} bytes")
                    
                    Log.d(TAG, "🔄 Encodage Base64...")
                    val base64String = Base64.encodeToString(compressedBytes, Base64.DEFAULT)
                    Log.d(TAG, "📊 Base64: ${base64String.length} chars")
                    
                    Log.d(TAG, "💾 Sauvegarde SharedPreferences...")
                    val editor = prefs.edit()
                    editor.putString(KEY_PROFILE_IMAGE, base64String)
                    editor.putString(KEY_PROFILE_URL, url)
                    val commitSuccess = editor.commit() // Utiliser commit() pour sauvegarde immédiate
                    
                    Log.d(TAG, "📊 Sauvegarde SharedPreferences: ${if (commitSuccess) "SUCCESS" else "ÉCHEC"}")
                    
                    // Vérification immédiate
                    val verification = prefs.getString(KEY_PROFILE_IMAGE, null)
                    Log.d(TAG, "🔍 Vérification immédiate: ${if (verification != null) "DONNÉE PRÉSENTE (${verification.length} chars)" else "ÉCHEC - DONNÉE MANQUANTE!"}")
                    
                    Log.d(TAG, "✅ SUCCESS! Image profil mise en cache (mémoire + disque)")
                } else {
                    Log.e(TAG, "❌ ERREUR: Échec compression JPEG")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ ERREUR CRITIQUE sauvegarde cache profil: ${e.message}")
                Log.e(TAG, "❌ StackTrace: ${e.stackTraceToString()}")
            }
        } else {
            Log.w(TAG, "⚠️ Image null - Seules les URLs sont mises à jour")
        }
        
        Log.d(TAG, "🏁 setCachedProfileImage() - TERMINÉ")
    }
    
    // ============
    // PROFIL PARTENAIRE
    // ============
    
    /**
     * Récupère image partenaire depuis cache
     * Équivalent de getCachedPartnerImage() iOS
     */
    fun getCachedPartnerImage(): Bitmap? {
        // Cache mémoire
        if (partnerImageCache != null) {
            Log.d(TAG, "🚀 Image partenaire depuis cache mémoire")
            return partnerImageCache
        }
        
        // Cache disque
        try {
            val base64String = prefs.getString(KEY_PARTNER_IMAGE, null)
            if (!base64String.isNullOrEmpty()) {
                val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                partnerImageCache = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                Log.d(TAG, "💾 Image partenaire chargée depuis disque")
                return partnerImageCache
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erreur chargement cache disque partenaire: ${e.message}")
        }
        
        Log.d(TAG, "❌ Aucune image partenaire en cache")
        return null
    }
    
    /**
     * Met en cache l'image partenaire
     * Équivalent de setCachedPartnerImage() iOS
     */
    fun setCachedPartnerImage(image: Bitmap?, url: String?, updatedAt: Long? = null) {
        partnerImageCache = image
        cachedPartnerImageURL = url
        
        if (image != null) {
            try {
                val outputStream = ByteArrayOutputStream()
                image.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64String = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                
                val editor = prefs.edit()
                    .putString(KEY_PARTNER_IMAGE, base64String)
                    .putString(KEY_PARTNER_URL, url)
                
                if (updatedAt != null) {
                    editor.putLong(KEY_PARTNER_UPDATED_AT, updatedAt)
                }
                
                editor.apply()
                
                Log.d(TAG, "✅ Image partenaire mise en cache")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur sauvegarde cache partenaire: ${e.message}")
            }
        } else {
            // Si image null, nettoyer le cache
            prefs.edit()
                .remove(KEY_PARTNER_IMAGE)
                .remove(KEY_PARTNER_URL)
                .remove(KEY_PARTNER_UPDATED_AT)
                .apply()
            Log.d(TAG, "🗑️ Cache partenaire nettoyé")
        }
    }
    
    /**
     * Vérifie si l'URL partenaire a changé (pour rechargement)
     * Équivalent de hasPartnerImageChanged() iOS
     */
    fun hasPartnerImageChanged(newURL: String?): Boolean {
        val hasChanged = cachedPartnerImageURL != newURL
        if (hasChanged) {
            Log.d(TAG, "🔄 URL partenaire changée: '$cachedPartnerImageURL' → '$newURL'")
        }
        return hasChanged
    }

    /**
     * Récupère l'URL de l'image utilisateur en cache
     */
    fun getCachedProfileImageURL(): String? {
        return cachedProfileImageURL ?: prefs.getString(KEY_PROFILE_URL, null)
    }

    /**
     * Récupère l'URL de l'image partenaire en cache
     */
    fun getCachedPartnerProfileImageURL(): String? {
        return cachedPartnerImageURL ?: prefs.getString(KEY_PARTNER_URL, null)
    }

    /**
     * Récupère le timestamp de mise à jour de l'image partenaire
     */
    fun getCachedPartnerProfileImageUpdatedAt(): Long? {
        val timestamp = prefs.getLong(KEY_PARTNER_UPDATED_AT, 0L)
        return if (timestamp > 0) timestamp else null
    }
    
    // ============
    // UTILS & CLEANUP
    // ============
    
    /**
     * Vide le cache utilisateur
     */
    fun clearProfileImageCache() {
        profileImageCache = null
        cachedProfileImageURL = null
        prefs.edit()
            .remove(KEY_PROFILE_IMAGE)
            .remove(KEY_PROFILE_URL)
            .apply()
        Log.d(TAG, "🗑️ Cache profil utilisateur vidé")
    }
    
    /**
     * Vide le cache partenaire
     */
    fun clearPartnerImageCache() {
        partnerImageCache = null
        cachedPartnerImageURL = null
        prefs.edit()
            .remove(KEY_PARTNER_IMAGE)
            .remove(KEY_PARTNER_URL)
            .remove(KEY_PARTNER_UPDATED_AT)
            .apply()
        Log.d(TAG, "🗑️ Cache partenaire vidé")
    }
    
    /**
     * Vide tous les caches images
     */
    fun clearAllImageCache() {
        clearProfileImageCache()
        clearPartnerImageCache()
        Log.d(TAG, "🗑️ Tous les caches images vidés")
    }
    
    /**
     * Nettoie TOUT le cache utilisateur + images (équivalent clearCache() iOS)
     * Utilisé lors déconnexion, suppression compte, etc.
     */
    fun clearCache() {
        clearUserCache()
        clearAllImageCache()
        Log.d(TAG, "🗑️ Cache utilisateur complet nettoyé")
    }
    
    /**
     * Informations de debug sur l'état du cache
     * Équivalent des méthodes debug iOS
     */
    fun getDebugInfo(): String {
        val userCached = hasCachedUser()
        val userTimestamp = prefs.getLong(KEY_USER_TIMESTAMP, 0L)
        val userAge = if (userTimestamp > 0) {
            (System.currentTimeMillis() - userTimestamp) / (60 * 60 * 1000)
        } else 0
        
        val profileImageCached = profileImageCache != null || prefs.contains(KEY_PROFILE_IMAGE)
        val partnerImageCached = partnerImageCache != null || prefs.contains(KEY_PARTNER_IMAGE)
        
        return """
            📊 DEBUG UserCacheManager:
            - Utilisateur en cache: $userCached (âge: ${userAge}h)
            - Image profil en cache: $profileImageCached
            - Image partenaire en cache: $partnerImageCached  
            - URL profil: $cachedProfileImageURL
            - URL partenaire: $cachedPartnerImageURL
        """.trimIndent()
    }
}
