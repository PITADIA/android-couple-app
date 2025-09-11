package com.lyes.love2love.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import kotlin.math.max

/**
 * ImageCacheService (Android)
 * - Cache mémoire via LruCache (limité en octets)
 * - Cache disque dans <app>/cache/ImageCache
 * - Methods : getCachedImage, cacheImage, clearCachedImage, clearCache, getCacheSize
 * - Compatibilité widgets : cacheImageForWidget, getCachedWidgetImage
 *
 * Usage :
 *   val cache = ImageCacheService.getInstance(context)
 *   cache.cacheImage(bitmap, url)
 *   val bmp = cache.getCachedImage(url)
 */
class ImageCacheService private constructor(
    private val appContext: Context
) {

    companion object {
        private const val TAG = "ImageCacheService"

        // Limites mémoire (alignées sur ton Swift : ~100 MB)
        private const val MEMORY_MAX_BYTES: Int = 100 * 1024 * 1024 // 100 MB
        // Swift limitait à 50 images, LruCache ne gère pas un "countLimit" nativement.
        // On se repose ici sur la limite en octets.

        @Volatile
        private var INSTANCE: ImageCacheService? = null

        fun getInstance(context: Context): ImageCacheService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageCacheService(context.applicationContext).also { INSTANCE = it }
            }
    }

    // --- Mémoire ---
    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            // Compte la taille réelle du bitmap
            return value.byteCount
        }
    }

    // --- Disque ---
    private val cacheDirectory: File = File(appContext.cacheDir, "ImageCache").apply {
        if (!exists()) mkdirs()
    }

    // --- Concurrence disque ---
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Log.i(TAG, "🖼️ Cache initialisé — Dossier: ${cacheDirectory.absolutePath}")
    }

    // =====================
    // Public API
    // =====================

    /** Retourne un Bitmap si présent en cache mémoire ou disque, sinon null. */
    fun getCachedImage(urlString: String): Bitmap? {
        val key = cacheKeyForURL(urlString)

        // 1) Mémoire
        memoryCache.get(key)?.let {
            Log.d(TAG, "🖼️ Image trouvée en cache mémoire")
            return it
        }

        // 2) Disque
        loadImageFromDisk(key)?.let { diskBmp ->
            Log.d(TAG, "🖼️ Image trouvée en cache disque")
            memoryCache.put(key, diskBmp) // Remonte en mémoire
            return diskBmp
        }

        Log.d(TAG, "🖼️ Aucune image en cache")
        return null
    }

    /** Ajoute/Met à jour une image en cache (mémoire + disque asynchrone). */
    fun cacheImage(image: Bitmap, urlString: String) {
        val key = cacheKeyForURL(urlString)

        // Mémoire
        memoryCache.put(key, image)

        // Disque (asynchrone)
        ioScope.launch {
            saveImageToDisk(image, key)
        }

        Log.d(TAG, "🖼️ Image mise en cache")
    }

    /** Supprime une image spécifique du cache (mémoire + disque). */
    fun clearCachedImage(urlString: String) {
        val key = cacheKeyForURL(urlString)

        // Mémoire
        memoryCache.remove(key)

        // Disque
        ioScope.launch {
            val file = File(cacheDirectory, "$key.jpg")
            if (file.exists()) file.delete()
        }

        Log.d(TAG, "🗑️ Image supprimée du cache")
    }

    /** Vide tout le cache (mémoire + disque). */
    fun clearCache() {
        memoryCache.evictAll()
        ioScope.launch {
            cacheDirectory.deleteRecursively()
            cacheDirectory.mkdirs()
        }
        Log.i(TAG, "🖼️ Cache vidé")
    }

    /** Taille du cache (mémoire théorique max + disque réel). */
    fun getCacheSize(): CacheSize {
        val memorySizeHuman = "~${MEMORY_MAX_BYTES / 1024 / 1024}MB"

        var diskBytes = 0L
        cacheDirectory.walkTopDown().forEach { f ->
            if (f.isFile) diskBytes += f.length()
        }
        val diskSizeHuman = humanReadableByteCount(diskBytes)

        return CacheSize(memorySizeHuman, diskSizeHuman)
    }

    // =====================
    // Widget Compatibility
    // =====================

    /**
     * Sauvegarde synchrone pour widgets (nom de fichier fourni).
     * Remarque : sur Android, le widget ne lit pas directement le fichier ;
     * c’est l’app qui charge l’image et l’assigne au RemoteViews/Glance.
     */
    fun cacheImageForWidget(image: Bitmap, fileName: String) {
        Log.d(TAG, "🖼️ cacheImageForWidget(file=$fileName)")
        val file = File(cacheDirectory, fileName)
        try {
            FileOutputStream(file).use { out ->
                image.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            if (file.exists()) {
                Log.d(TAG, "✅ Image widget sauvée: ${file.name} (${humanReadableByteCount(file.length())})")
            } else {
                Log.e(TAG, "❌ Fichier widget introuvable après écriture")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde widget: ${e.message}", e)
        }
    }

    /** Charge un bitmap destiné à un widget à partir d’un nom de fichier. */
    fun getCachedWidgetImage(fileName: String): Bitmap? {
        val file = File(cacheDirectory, fileName)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeStream(FileInputStream(file))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lecture image widget: ${e.message}", e)
            null
        }
    }

    // =====================
    // Privates
    // =====================

    private fun cacheKeyForURL(urlString: String): String {
        // Clé stable et “safe” pour système de fichiers
        return try {
            val uri = Uri.parse(urlString)
            val path = (uri.path ?: urlString).replace("/", "_")
            val queryHash = uri.query?.hashCode()?.toString()
            val base = if (!queryHash.isNullOrEmpty()) "${path}_$queryHash" else path

            // Nettoyage des caractères problématiques + limite longueur
            val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (cleaned.length <= 200) cleaned
            else cleaned.take(150) + "_" + cleaned.hashCode()
        } catch (_: Exception) {
            // Fallback ultra-robuste
            urlString.hashCode().toString()
        }
    }

    private fun loadImageFromDisk(cacheKey: String): Bitmap? {
        val file = File(cacheDirectory, "$cacheKey.jpg")
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeStream(FileInputStream(file))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lecture disque: ${e.message}", e)
            null
        }
    }

    private fun saveImageToDisk(image: Bitmap, cacheKey: String) {
        val file = File(cacheDirectory, "$cacheKey.jpg")
        try {
            FileOutputStream(file).use { out ->
                image.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            Log.d(TAG, "🖼️ Image sauvée sur disque")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde disque: ${e.message}", e)
        }
    }

    private fun humanReadableByteCount(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val unit = 1024.0
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit)).toInt()
        val pre = "KMGTPE"[exp - 1]
        val df = DecimalFormat("#.#")
        return "${df.format(bytes / Math.pow(unit, exp.toDouble()))} ${pre}B"
    }

    data class CacheSize(
        val memorySize: String,
        val diskSize: String
    )
}
