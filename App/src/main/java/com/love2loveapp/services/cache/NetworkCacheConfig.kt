package com.love2loveapp.services.cache

import android.content.Context
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 🌐 NetworkCacheConfig Android - Cache Réseau Sophistiqué
 * 
 * Architecture équivalente iOS URLCache + URLSession:
 * - OkHttp Cache → URLCache iOS
 * - Interceptors → URLSessionConfiguration iOS
 * - Cache headers → HTTP cache headers iOS
 * - Automatic caching → URLSession cache automatique iOS
 * - Cache size limits → URLCache memory/disk limits iOS
 * - Network/offline → Cache-Control policies iOS
 * - Équivalent complet du cache réseau iOS
 */
object NetworkCacheConfig {
    private const val TAG = "NetworkCacheConfig"
    
    // Configuration cache (équivalent URLCache iOS)
    private const val HTTP_CACHE_SIZE = 50L * 1024L * 1024L // 50 MB comme iOS
    private const val CACHE_MAX_AGE = 5 * 60 // 5 minutes
    private const val CACHE_MAX_STALE = 7 * 24 * 60 * 60 // 7 jours offline
    
    // Timeouts réseau
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    
    /**
     * Crée OkHttpClient avec cache sophistiqué
     * Équivalent de URLSession.shared avec configuration cache iOS
     */
    fun createCachedOkHttpClient(context: Context, isDebug: Boolean = false): OkHttpClient {
        val cacheDirectory = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDirectory, HTTP_CACHE_SIZE)
        
        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(createCacheInterceptor())
            .addNetworkInterceptor(createNetworkCacheInterceptor())
            .addInterceptor(createLoggingInterceptor(isDebug))
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
            .also {
                android.util.Log.d(TAG, "✅ OkHttpClient configuré avec cache: ${cacheDirectory.path}")
                android.util.Log.d(TAG, "   - Taille cache: ${HTTP_CACHE_SIZE / (1024 * 1024)}MB")
                android.util.Log.d(TAG, "   - Max age: ${CACHE_MAX_AGE / 60}min")
                android.util.Log.d(TAG, "   - Max stale: ${CACHE_MAX_STALE / (24 * 60 * 60)} jours")
            }
    }
    
    /**
     * Interceptor pour cache offline/online
     * Équivalent de URLRequestCachePolicy iOS
     */
    private fun createCacheInterceptor(): Interceptor {
        return Interceptor { chain ->
            var request = chain.request()
            
            // Force utilisation cache si pas de réseau
            if (!NetworkUtils.isNetworkAvailable()) {
                android.util.Log.d(TAG, "📡 Pas de réseau - Force cache: ${request.url}")
                request = request.newBuilder()
                    .cacheControl(
                        CacheControl.Builder()
                            .onlyIfCached()
                            .maxStale(CACHE_MAX_STALE, TimeUnit.SECONDS)
                            .build()
                    )
                    .build()
            }
            
            chain.proceed(request)
        }
    }
    
    /**
     * Interceptor réseau pour headers de cache
     * Équivalent de URLResponse cache headers iOS
     */
    private fun createNetworkCacheInterceptor(): Interceptor {
        return Interceptor { chain ->
            val response = chain.proceed(chain.request())
            
            // Ajouter headers cache si pas présents
            val cacheControl = response.header("Cache-Control")
            
            if (cacheControl == null || cacheControl.contains("no-cache")) {
                android.util.Log.d(TAG, "🔧 Ajout headers cache: ${response.request.url}")
                
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=$CACHE_MAX_AGE")
                    .removeHeader("Pragma") // Supprimer pragma no-cache
                    .build()
            } else {
                response
            }
        }
    }
    
    /**
     * Interceptor logging (équivalent NSURLSessionTaskDelegate iOS)
     */
    private fun createLoggingInterceptor(isDebug: Boolean): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            android.util.Log.d("OkHttp", message)
        }.apply {
            level = if (isDebug) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }
    
    /**
     * Crée Retrofit avec cache OkHttp
     * Équivalent de URLSession + JSONDecoder iOS
     */
    fun createCachedRetrofit(
        baseUrl: String, 
        context: Context, 
        isDebug: Boolean = false
    ): Retrofit {
        val okHttpClient = createCachedOkHttpClient(context, isDebug)
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .also {
                android.util.Log.d(TAG, "✅ Retrofit configuré avec cache pour: $baseUrl")
            }
    }
    
    /**
     * Utilitaires réseau
     */
    private object NetworkUtils {
        fun isNetworkAvailable(): Boolean {
            // Implémentation basique - à améliorer avec ConnectivityManager
            return try {
                val runtime = Runtime.getRuntime()
                val process = runtime.exec("ping -c 1 8.8.8.8")
                process.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Configuration cache pour requêtes spécifiques
     */
    object CacheStrategies {
        
        /**
         * Cache agressif pour données statiques (images, etc.)
         */
        fun createStaticCacheControl(): CacheControl {
            return CacheControl.Builder()
                .maxAge(24, TimeUnit.HOURS) // Cache 24h
                .maxStale(7, TimeUnit.DAYS)  // Jusqu'à 7 jours offline
                .build()
        }
        
        /**
         * Cache court pour données dynamiques (profils, etc.)
         */
        fun createDynamicCacheControl(): CacheControl {
            return CacheControl.Builder()
                .maxAge(5, TimeUnit.MINUTES)  // Cache 5min
                .maxStale(1, TimeUnit.HOURS)  // Jusqu'à 1h offline
                .build()
        }
        
        /**
         * Cache ultra-court pour données temps réel
         */
        fun createRealtimeCacheControl(): CacheControl {
            return CacheControl.Builder()
                .maxAge(30, TimeUnit.SECONDS) // Cache 30s seulement
                .maxStale(5, TimeUnit.MINUTES) // Jusqu'à 5min offline
                .build()
        }
        
        /**
         * Pas de cache (toujours fresh)
         */
        fun createNoCacheControl(): CacheControl {
            return CacheControl.Builder()
                .noCache()
                .noStore()
                .build()
        }
    }
    
    /**
     * Exemple d'utilisation avec Firebase Functions
     */
    object FirebaseFunctionsCacheConfig {
        
        fun createFirebaseFunctionsClient(context: Context, baseUrl: String): Retrofit {
            val okHttpClient = OkHttpClient.Builder()
                .cache(Cache(File(context.cacheDir, "firebase_cache"), 20L * 1024 * 1024)) // 20MB
                .addInterceptor { chain ->
                    val request = chain.request()
                    val url = request.url.toString()
                    
                    // Stratégie cache selon endpoint
                    val cacheControl = when {
                        url.contains("getPartnerInfo") -> CacheStrategies.createDynamicCacheControl()
                        url.contains("getPartnerLocation") -> CacheStrategies.createRealtimeCacheControl()
                        url.contains("generateDailyQuestion") -> CacheStrategies.createNoCacheControl()
                        else -> CacheStrategies.createDynamicCacheControl()
                    }
                    
                    val newRequest = request.newBuilder()
                        .cacheControl(cacheControl)
                        .build()
                        
                    android.util.Log.d(TAG, "🔥 Firebase Function: ${request.url.encodedPath} (cache: ${cacheControl.maxAgeSeconds}s)")
                    
                    chain.proceed(newRequest)
                }
                .build()
            
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }
    
    /**
     * Métriques et debug du cache
     */
    fun getCacheMetrics(context: Context): CacheMetrics {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cacheSize = if (cacheDir.exists()) {
            cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else 0L
        
        val fileCount = if (cacheDir.exists()) {
            cacheDir.listFiles()?.size ?: 0
        } else 0
        
        return CacheMetrics(
            cacheSizeMB = cacheSize / (1024 * 1024),
            fileCount = fileCount,
            maxSizeMB = HTTP_CACHE_SIZE / (1024 * 1024),
            cacheDirectory = cacheDir.path,
            hitRate = 0.0f // TODO: Implémenter compteur hits réels
        )
    }
    
    data class CacheMetrics(
        val cacheSizeMB: Long,
        val fileCount: Int,
        val maxSizeMB: Long,
        val cacheDirectory: String,
        val hitRate: Float
    )
    
    /**
     * Vide le cache réseau
     * Équivalent de URLCache.shared.removeAllCachedResponses() iOS
     */
    fun clearNetworkCache(context: Context) {
        val cacheDir = File(context.cacheDir, "http_cache")
        val firebaseCacheDir = File(context.cacheDir, "firebase_cache")
        
        try {
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                android.util.Log.d(TAG, "🗑️ Cache HTTP vidé")
            }
            
            if (firebaseCacheDir.exists()) {
                firebaseCacheDir.deleteRecursively()
                android.util.Log.d(TAG, "🗑️ Cache Firebase Functions vidé")
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Erreur nettoyage cache réseau: ${e.message}")
        }
    }
    
    /**
     * Informations debug cache réseau
     */
    fun getDebugInfo(context: Context): String {
        val metrics = getCacheMetrics(context)
        
        return """
            📊 DEBUG NetworkCacheConfig:
            - Taille cache: ${metrics.cacheSizeMB}MB / ${metrics.maxSizeMB}MB
            - Fichiers: ${metrics.fileCount}
            - Hit rate: ${(metrics.hitRate * 100).toInt()}%
            - Répertoire: ${metrics.cacheDirectory}
            - Max age: ${CACHE_MAX_AGE / 60}min
            - Max stale: ${CACHE_MAX_STALE / (24 * 60 * 60)} jours
            - Timeouts: ${CONNECT_TIMEOUT}s connect / ${READ_TIMEOUT}s read
        """.trimIndent()
    }
}
