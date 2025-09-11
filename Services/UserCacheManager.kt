package com.love2loveapp.services.cache

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.util.Date
import com.love2loveapp.model.AppConstants

/**
 * Équivalent Kotlin/Android du UserCacheManager (Swift).
 *
 * ✅ JSON de l'utilisateur en SharedPreferences (Gson)
 * ✅ Images de profil & partenaire en Base64 (JPEG, qualité 0.8) dans SharedPreferences
 * ✅ TTL du cache utilisateur : 7 jours
 * ✅ Helpers: hasCache, clearCache, updateCachedUser (transform)
 *
 * ⚠️ Remarque performance: pour des images lourdes, préférer un cache disque (ex: fichiers dans cacheDir)
 *    ou une lib (Coil/Glide). Cette implémentation reste 1:1 avec l’approche UserDefaults de la version iOS.
 *
 * Utilisation :
 *   UserCacheManager.init(appContext)
 *   UserCacheManager.cacheUser(user)
 *   val cached = UserCacheManager.getCachedUser()
 */
object UserCacheManager {

    private const val TAG = "UserCacheManager"
    private const val PREFS_NAME = "cached_user_prefs"

    // Keys
    private const val KEY_USER_JSON = "cached_user_data"
    private const val KEY_USER_TS = "cached_user_timestamp"
    private const val KEY_PROFILE_IMG = "cached_profile_image"
    private const val KEY_PARTNER_IMG = "cached_partner_image"
    private const val KEY_PARTNER_IMG_URL = "cached_partner_image_url"

    // TTL 7 jours
    private const val CACHE_MAX_AGE_MS = AppConstants.Cache.USER_CACHE_MAX_AGE_MS

    private lateinit var prefs: SharedPreferences
    private val gson by lazy { Gson() }

    @Volatile private var initialized = false

    /** À appeler une seule fois (ex: dans Application.onCreate()) */
    fun init(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    initialized = true
                    Log.d(TAG, "✅ init: SharedPreferences initialisés")
                }
            }
        }
    }

    // --- Sécurité interne ---
    private fun requireInit() {
        check(initialized) { "UserCacheManager.init(context) doit être appelé avant usage." }
    }

    // ============
    // USER CACHE
    // ============

    /** Cache l'utilisateur après chargement Firebase */
    fun cacheUser(user: AppUser) {
        requireInit()
        return try {
            Log.d(TAG, "💾 Mise en cache utilisateur: ${user.name}")
            val json = gson.toJson(user)
            prefs.edit()
                .putString(KEY_USER_JSON, json)
                .putLong(KEY_USER_TS, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "✅ Utilisateur mis en cache avec succès")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Erreur encodage utilisateur: ${t.message}", t)
        }
    }

    /** Récupère l'utilisateur en cache (instantané, TTL 7j) */
    fun getCachedUser(): AppUser? {
        requireInit()
        val json = prefs.getString(KEY_USER_JSON, null) ?: run {
            Log.d(TAG, "📂 Aucun utilisateur en cache")
            return null
        }

        // Vérifier l'âge du cache
        prefs.getLong(KEY_USER_TS, 0L).let { ts ->
            if (ts > 0) {
                val age = System.currentTimeMillis() - ts
                if (age > CACHE_MAX_AGE_MS) {
                    Log.d(TAG, "⏰ Cache expiré (${age / 3600000}h), nettoyage")
                    clearCache()
                    return null
                }
            }
        }

        return try {
            val user = gson.fromJson(json, AppUser::class.java)
            Log.d(TAG, "🚀 Utilisateur trouvé en cache: ${user.name}")
            user
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Erreur décodage cache: ${t.message}. Nettoyage…", t)
            clearCache()
            null
        }
    }

    /** Vérifie si un utilisateur est en cache */
    fun hasCachedUser(): Boolean {
        requireInit()
        return prefs.contains(KEY_USER_JSON)
    }

    /** Nettoie le cache (déconnexion, suppression compte, etc.) */
    fun clearCache() {
        requireInit()
        Log.d(TAG, "🗑️ Nettoyage cache utilisateur")
        prefs.edit()
            .remove(KEY_USER_JSON)
            .remove(KEY_USER_TS)
            .apply()
        clearCachedProfileImage()
        clearCachedPartnerImage()
    }

    // =================
    // PROFILE IMAGE
    // =================

    /** Cache l'image de profil (JPEG 80%) */
    fun cacheProfileImage(bitmap: Bitmap) {
        requireInit()
        try {
            Log.d(TAG, "🖼️ Mise en cache image de profil")
            val bytes = bitmap.toJpegBytes(quality = AppConstants.Cache.IMAGE_CACHE_QUALITY)
            prefs.edit().putString(KEY_PROFILE_IMG, bytes.base64()).apply()
            Log.d(TAG, "✅ Image de profil mise en cache (${bytes.size} bytes)")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Impossible de convertir l'image de profil: ${t.message}", t)
        }
    }

    /** Récupère l'image de profil en cache */
    fun getCachedProfileImage(): Bitmap? {
        requireInit()
        val b64 = prefs.getString(KEY_PROFILE_IMG, null) ?: run {
            Log.d(TAG, "🖼️ Aucune image de profil en cache")
            return null
        }
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
                Log.e(TAG, "❌ Decode image profil échoué, nettoyage clé")
                prefs.edit().remove(KEY_PROFILE_IMG).apply()
                null
            }.also {
                if (it != null) Log.d(TAG, "✅ Image de profil trouvée en cache")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Erreur chargement image profil: ${t.message}", t)
            prefs.edit().remove(KEY_PROFILE_IMG).apply()
            null
        }
    }

    /** Présence image de profil */
    fun hasCachedProfileImage(): Boolean {
        requireInit()
        return prefs.contains(KEY_PROFILE_IMG)
    }

    /** Nettoie seulement l'image de profil en cache */
    fun clearCachedProfileImage() {
        requireInit()
        Log.d(TAG, "🗑️ Nettoyage image de profil en cache")
        prefs.edit().remove(KEY_PROFILE_IMG).apply()
    }

    // =================
    // PARTNER IMAGE
    // =================

    /** Cache l'image du partenaire + son URL source (pour détection de changement) */
    fun cachePartnerImage(bitmap: Bitmap, url: String) {
        requireInit()
        try {
            Log.d(TAG, "🤝 Mise en cache image partenaire")
            val bytes = bitmap.toJpegBytes(quality = AppConstants.Cache.IMAGE_CACHE_QUALITY)
            prefs.edit()
                .putString(KEY_PARTNER_IMG, bytes.base64())
                .putString(KEY_PARTNER_IMG_URL, url)
                .apply()
            Log.d(TAG, "✅ Image partenaire mise en cache (${bytes.size} bytes)")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Impossible de convertir l'image partenaire: ${t.message}", t)
        }
    }

    /** Récupère l'image partenaire en cache */
    fun getCachedPartnerImage(): Bitmap? {
        requireInit()
        val b64 = prefs.getString(KEY_PARTNER_IMG, null) ?: run {
            Log.d(TAG, "🤝 Aucune image partenaire en cache")
            return null
        }
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
                Log.e(TAG, "❌ Decode image partenaire échoué, nettoyage clés")
                clearCachedPartnerImage()
                null
            }.also {
                if (it != null) Log.d(TAG, "✅ Image partenaire trouvée en cache")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Erreur chargement image partenaire: ${t.message}", t)
            clearCachedPartnerImage()
            null
        }
    }

    /** Vérifie si l’URL partenaire a changé (pour décider si on recharge) */
    fun hasPartnerImageChanged(newURL: String?): Boolean {
        requireInit()
        val cached = prefs.getString(KEY_PARTNER_IMG_URL, null)
        val changed = cached != newURL
        if (changed) Log.d(TAG, "🔄 URL partenaire changée")
        else Log.d(TAG, "✅ URL partenaire inchangée")
        return changed
    }

    /** Présence image partenaire */
    fun hasCachedPartnerImage(): Boolean {
        requireInit()
        return prefs.contains(KEY_PARTNER_IMG)
    }

    /** Nettoie l’image partenaire */
    fun clearCachedPartnerImage() {
        requireInit()
        Log.d(TAG, "🗑️ Nettoyage image partenaire en cache")
        prefs.edit()
            .remove(KEY_PARTNER_IMG)
            .remove(KEY_PARTNER_IMG_URL)
            .apply()
    }

    // ==========================
    // UPDATE PARTIEL UTILISATEUR
    // ==========================

    /**
     * Met à jour l’utilisateur en cache via une transformation immuable.
     * Exemple :
     *   UserCacheManager.updateCachedUser { it.copy(name = "Nouveau nom") }
     */
    fun updateCachedUser(transform: (AppUser) -> AppUser) {
        requireInit()
        val current = getCachedUser()
        if (current == null) {
            Log.w(TAG, "⚠️ Impossible de mettre à jour - pas d'utilisateur en cache")
            return
        }
        val updated = try {
            transform(current)
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Erreur transform utilisateur: ${t.message}", t)
            return
        }
        cacheUser(updated)
    }

    // ===========
    // INFO CACHE
    // ===========

    data class CacheInfo(
        val hasCache: Boolean,
        val lastUpdated: Date?,
        val userCount: Int
    )

    fun getCacheInfo(): CacheInfo {
        requireInit()
        val hasCache = hasCachedUser()
        val ts = prefs.getLong(KEY_USER_TS, 0L)
        val lastUpdated = if (ts > 0) Date(ts) else null
        return CacheInfo(hasCache, lastUpdated, if (hasCache) 1 else 0)
    }

    // ====================
    // Helpers internes
    // ====================

    private fun Bitmap.toJpegBytes(quality: Int = 80): ByteArray {
        val bos = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(0, 100), bos)
        return bos.toByteArray()
    }

    private fun ByteArray.base64(): String =
        Base64.encodeToString(this, Base64.DEFAULT)
}

/**
 * Ton modèle Kotlin doit être sérialisable par Gson.
 * Exemple minimal :
 *
 * data class AppUser(
 *   val id: String,
 *   val name: String,
 *   val email: String? = null,
 *   // ... autres champs (doivent rester compatibles JSON)
 * )
 */
data class AppUser(
    val id: String,
    val name: String,
    val email: String? = null
    // ajoute ici les champs dont tu as besoin
)
