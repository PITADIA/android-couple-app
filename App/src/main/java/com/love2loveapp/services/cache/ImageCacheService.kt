package com.love2loveapp.services.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 🖼️ ImageCacheService - Système de cache multi-niveaux selon rapport iOS
 * Cache mémoire + Cache disque + Chargement asynchrone optimisé
 */
class ImageCacheService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageCacheService"
        private const val DISK_CACHE_SUBDIR = "ImageCache"
        private const val DISK_CACHE_SIZE = 50 * 1024 * 1024L // 50MB comme iOS
        private const val MEMORY_CACHE_SIZE = 100 * 1024 * 1024 // 100MB comme iOS
        
        @Volatile
        private var INSTANCE: ImageCacheService? = null
        
        fun getInstance(context: Context): ImageCacheService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageCacheService(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }
    
    // 🧠 Cache mémoire avec LruCache (équivalent NSCache iOS)
    private val memoryCache: LruCache<String, Bitmap>
    
    // 💾 Dossier cache disque persistant
    private val cacheDirectory: File
    
    init {
        // Configuration cache mémoire (identique iOS: 50 images, 100MB max)
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val memoryCacheSize = minOf(maxMemory / 8, MEMORY_CACHE_SIZE)
        
        memoryCache = object : LruCache<String, Bitmap>(memoryCacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
        
        // Configuration cache disque
        cacheDirectory = File(context.cacheDir, DISK_CACHE_SUBDIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        
        Log.d(TAG, "🖼️ ImageCacheService initialisé: mémoire=${memoryCacheSize/1024}MB, disque=${DISK_CACHE_SIZE/1024/1024}MB")
    }
    
    /**
     * 🔍 Récupération intelligente d'image (identique iOS)
     * 1. Vérifier cache mémoire (plus rapide)
     * 2. Vérifier cache disque
     * 3. Retourner null si pas trouvée
     */
    fun getCachedImage(urlString: String): Bitmap? {
        val cacheKey = cacheKeyForURL(urlString)
        
        // 1. Vérifier cache mémoire d'abord (plus rapide)
        memoryCache.get(cacheKey)?.let { memoryImage ->
            Log.d(TAG, "🖼️ Image trouvée en cache mémoire")
            return memoryImage
        }
        
        // 2. Vérifier cache disque
        loadImageFromDisk(cacheKey)?.let { diskImage ->
            Log.d(TAG, "🖼️ Image trouvée en cache disque")
            // Remettre en cache mémoire pour accès rapide futur
            memoryCache.put(cacheKey, diskImage)
            return diskImage
        }
        
        Log.d(TAG, "🖼️ Aucune image en cache pour: $urlString")
        return null
    }
    
    /**
     * 💾 Mise en cache asynchrone (identique iOS)
     * 1. Mettre en cache mémoire (immédiat)
     * 2. Mettre en cache disque de façon asynchrone (non bloquant)
     */
    fun cacheImage(image: Bitmap, urlString: String) {
        val cacheKey = cacheKeyForURL(urlString)
        
        // 1. Cache mémoire immédiat
        memoryCache.put(cacheKey, image)
        
        // 2. Cache disque asynchrone (non bloquant)
        CoroutineScope(Dispatchers.IO).launch {
            saveImageToDisk(image, cacheKey)
        }
        
        Log.d(TAG, "🖼️ Image mise en cache: $urlString")
    }
    
    /**
     * 🗂️ Générer clé de cache sécurisée à partir de l'URL
     */
    private fun cacheKeyForURL(urlString: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(urlString.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            urlString.hashCode().toString()
        }
    }
    
    /**
     * 📖 Charger image depuis le cache disque
     */
    private fun loadImageFromDisk(cacheKey: String): Bitmap? {
        return try {
            val cacheFile = File(cacheDirectory, cacheKey)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                BitmapFactory.decodeFile(cacheFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement cache disque: ${e.message}")
            null
        }
    }
    
    /**
     * 💾 Sauvegarder image sur le cache disque
     */
    private suspend fun saveImageToDisk(image: Bitmap, cacheKey: String) {
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(cacheDirectory, cacheKey)
                
                // Nettoyer l'ancien cache si nécessaire
                cleanupDiskCacheIfNeeded()
                
                FileOutputStream(cacheFile).use { out ->
                    image.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                
                Log.d(TAG, "💾 Image sauvée sur disque: ${cacheFile.name}")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur sauvegarde cache disque: ${e.message}")
            }
        }
    }
    
    /**
     * 🧹 Nettoyage intelligent du cache disque (évite dépassement 50MB)
     */
    private fun cleanupDiskCacheIfNeeded() {
        try {
            val cacheFiles = cacheDirectory.listFiles() ?: return
            val totalSize = cacheFiles.sumOf { it.length() }
            
            if (totalSize > DISK_CACHE_SIZE) {
                Log.d(TAG, "🧹 Nettoyage cache disque: ${totalSize/1024/1024}MB > ${DISK_CACHE_SIZE/1024/1024}MB")
                
                // Trier par date de modification (plus ancien en premier)
                val sortedFiles = cacheFiles.sortedBy { it.lastModified() }
                
                var deletedSize = 0L
                for (file in sortedFiles) {
                    deletedSize += file.length()
                    file.delete()
                    
                    // Arrêter quand on revient sous la limite
                    if (totalSize - deletedSize < DISK_CACHE_SIZE * 0.8) { // 80% de la limite
                        break
                    }
                }
                
                Log.d(TAG, "🧹 Cache nettoyé: ${deletedSize/1024/1024}MB supprimés")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage cache: ${e.message}")
        }
    }
    
    /**
     * 📊 Statistiques du cache pour debugging
     */
    fun getCacheStats(): CacheStats {
        val memorySizeKB = memoryCache.size()
        val diskFiles = cacheDirectory.listFiles()?.size ?: 0
        val diskSizeMB = (cacheDirectory.listFiles()?.sumOf { it.length() } ?: 0) / 1024 / 1024
        
        return CacheStats(
            memorySizeKB = memorySizeKB,
            diskFiles = diskFiles,
            diskSizeMB = diskSizeMB.toInt()
        )
    }
    
    /**
     * 📏 Obtenir la taille du cache (pour compatibilité CacheManager)
     */
    fun getCacheSize(): CacheMetrics {
        val stats = getCacheStats()
        return CacheMetrics(
            memoryMaxMB = MEMORY_CACHE_SIZE / 1024 / 1024,
            memoryUsedMB = stats.memorySizeKB / 1024,
            diskMaxMB = (DISK_CACHE_SIZE / 1024 / 1024).toInt(),
            diskUsedMB = stats.diskSizeMB
        )
    }
    
    /**
     * 🧹 Nettoyage du cache expiré (pour compatibilité CacheManager)
     */
    suspend fun cleanupExpiredCache() {
        // Notre système nettoie automatiquement, mais on peut forcer le nettoyage
        withContext(Dispatchers.IO) {
            cleanupDiskCacheIfNeeded()
        }
    }
    
    /**
     * 🗑️ Vider tout le cache (pour compatibilité CacheManager)
     */
    suspend fun clearAllCache() {
        withContext(Dispatchers.IO) {
            // Vider le cache mémoire
            memoryCache.evictAll()
            
            // Vider le cache disque
            try {
                cacheDirectory.listFiles()?.forEach { file ->
                    file.delete()
                }
                Log.d(TAG, "🗑️ Cache complètement vidé")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur vidage cache: ${e.message}")
            }
        }
    }
    
    /**
     * 🔍 Informations de debug détaillées (pour compatibilité CacheManager)
     */
    fun getDebugInfo(): String {
        val stats = getCacheStats()
        val metrics = getCacheSize()
        
        return buildString {
            appendLine("ImageCacheService:")
            appendLine("  Mémoire: ${metrics.memoryUsedMB}MB / ${metrics.memoryMaxMB}MB")
            appendLine("  Disque: ${metrics.diskUsedMB}MB / ${metrics.diskMaxMB}MB")
            appendLine("  Fichiers: ${stats.diskFiles}")
            appendLine("  Dossier: ${cacheDirectory.absolutePath}")
        }
    }
    
    data class CacheStats(
        val memorySizeKB: Int,
        val diskFiles: Int,
        val diskSizeMB: Int
    )
    
    data class CacheMetrics(
        val memoryMaxMB: Int,
        val memoryUsedMB: Int,
        val diskMaxMB: Int,
        val diskUsedMB: Int
    )
}