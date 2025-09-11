// AsyncImageView.kt — Jetpack Compose + Firebase (Storage, Functions, Auth)
// ---------------------------------------------------------------
// ✅ Points clés par rapport à ta version Swift
// - Traductions via strings.xml (ex: stringResource(R.string.image_not_available))
// - Cache mémoire local (LruCache) comme ImageCacheService.shared
// - Chargement via Cloud Functions pour:
//     • getPartnerProfileImage(partnerId)
//     • getSignedImageURL(filePath)
// - Fallback direct Firebase Storage (getReferenceFromUrl)
// - Placeholders / état de chargement / erreur, coins arrondis
// - Logs sécurisés (pas d’URL complète dans les logs)
// ---------------------------------------------------------------
// Dépendances Gradle (module app) à vérifier :
//
// implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
// implementation("com.google.firebase:firebase-storage-ktx")
// implementation("com.google.firebase:firebase-functions-ktx")
// implementation("com.google.firebase:firebase-auth-ktx")
// implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
// implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
// implementation("androidx.compose.ui:ui:1.7.2")
// implementation("androidx.compose.material3:material3:1.3.0")
// implementation("androidx.compose.material:material-icons-extended:1.7.2")
// ---------------------------------------------------------------
// strings.xml (extrait — à placer dans res/values/strings.xml)
// <resources>
//     <string name="image_not_available">Image non disponible</string>
// </resources>
// ---------------------------------------------------------------

package com.love2loveapp.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.love2loveapp.core.R
import com.love2loveapp.core.utils.ImageCacheService
import com.love2loveapp.core.utils.FirebaseImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AsyncImageView(
    imageUrl: String?,
    width: Dp? = null,
    height: Dp? = null,
    cornerRadius: Dp = 12.dp,
) {
    val TAG = "AsyncImageView"
    val context = LocalContext.current

    var loadedImage by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(imageUrl) { mutableStateOf(false) }
    var hasError by remember(imageUrl) { mutableStateOf(false) }

    // Détermine le modifier en fonction des dimensions fournies
    var modifier = Modifier.clip(RoundedCornerShape(cornerRadius))
    modifier = when {
        width != null && height != null -> modifier.size(width, height)
        width != null -> modifier.width(width)
        height != null -> modifier.height(height)
        else -> modifier
    }

    // UI
    Box(modifier = modifier) {
        when {
            loadedImage != null -> {
                Image(
                    bitmap = loadedImage!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            hasError -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(id = R.string.image_not_available),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            else -> {
                // Placeholder initial (sans erreur et sans chargement)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null
                    )
                }
            }
        }
    }

    // Chargement côté effet
    val scope = rememberCoroutineScope()
    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            Log.d(TAG, "loadImageIfNeeded: URL manquante — rien à charger")
            loadedImage = null
            isLoading = false
            hasError = false
            return@LaunchedEffect
        }

        if (loadedImage != null || isLoading) {
            Log.d(TAG, "loadImageIfNeeded: déjà chargé ou en cours")
            return@LaunchedEffect
        }

        isLoading = true
        hasError = false
        Log.d(TAG, "Tentative de chargement de l'image: [URL MASQUÉE]")

        scope.launch {
            try {
                // 1) Cache
                ImageCacheService.getCached(imageUrl)?.let { bmp ->
                    Log.d(TAG, "Image trouvée en cache — pas de téléchargement")
                    loadedImage = bmp.asImageBitmap()
                    isLoading = false
                    hasError = false
                    return@launch
                }

                // 2) Placeholder géré côté UI si préfixe
                if (imageUrl.startsWith("placeholder_image_")) {
                    Log.d(TAG, "Placeholder détecté — aucun téléchargement nécessaire")
                    loadedImage = null // on laisse l'UI afficher un placeholder neutre
                    isLoading = false
                    hasError = false
                    return@launch
                }

                // 3) Téléchargement selon type d'URL
                val bmp = withContext(Dispatchers.IO) {
                    FirebaseImageRepository.loadBitmapSmart(urlString = imageUrl)
                }

                // 4) Cache + rendu
                ImageCacheService.put(imageUrl, bmp)
                loadedImage = bmp.asImageBitmap()
                isLoading = false
                hasError = false
                Log.d(TAG, "Image chargée avec succès")
            } catch (t: Throwable) {
                Log.e(TAG, "Erreur de chargement [URL MASQUÉE]", t)
                isLoading = false
                hasError = true
            }
        }
    }
}

// ---------------------------------------------------------------
// FirebaseImageRepository — logique de chargement (Cloud Functions / Storage / HTTP)
// ---------------------------------------------------------------
package com.love2loveapp.core.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object FirebaseImageRepository {
    private const val TAG = "FirebaseImageRepo"

    // ⚠️ Adapte ce bucket si besoin pour les fallbacks "gs://" quand on n'a que le chemin
    private const val FALLBACK_GS_BUCKET = "gs://love2love-26164.firebasestorage.app"

    suspend fun loadBitmapSmart(urlString: String): Bitmap {
        Log.d(TAG, "loadBitmapSmart: début (URL masquée)")

        return when {
            urlString.contains("firebasestorage.googleapis.com") -> {
                // Utiliser Cloud Functions selon le type d'image
                loadFromFirebaseStorageViaCloudFunction(urlString)
            }
            urlString.startsWith("gs://") -> {
                loadFromFirebaseStorageDirect(urlString)
            }
            urlString.startsWith("http://") || urlString.startsWith("https://") -> {
                loadFromHttp(urlString)
            }
            else -> {
                Log.w(TAG, "URL non reconnue — tentative Storage direct")
                loadFromFirebaseStorageDirect(urlString)
            }
        }
    }

    // --------------------- Sous-fonctions ---------------------

    private suspend fun loadFromFirebaseStorageViaCloudFunction(urlString: String): Bitmap {
        Log.d(TAG, "loadFromFirebaseStorageViaCloudFunction: URL Firebase détectée")
        val filePath = extractFilePathFromDownloadUrl(urlString)
        if (filePath == null) {
            Log.w(TAG, "Impossible d'extraire le chemin — fallback direct")
            return loadFromFirebaseStorageDirect(urlString)
        }

        return when {
            filePath.startsWith("profile_images/") -> {
                val parts = filePath.split('/')
                val imageUserId = parts.getOrNull(1)
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

                if (imageUserId != null && currentUserId != null && imageUserId == currentUserId) {
                    Log.d(TAG, "Image du user courant → download direct")
                    loadFromFirebaseStorageDirect(urlString)
                } else {
                    Log.d(TAG, "Image d'un partenaire → Cloud Function getPartnerProfileImage")
                    loadProfileImageViaCloudFunction(imageUserId)
                }
            }
            filePath.startsWith("journal_images/") -> {
                Log.d(TAG, "Image journal → Cloud Function getSignedImageURL")
                loadImageViaSignedURL(filePath)
            }
            else -> {
                Log.d(TAG, "Type non identifié → fallback direct")
                loadFromFirebaseStorageDirect(urlString)
            }
        }
    }

    private suspend fun loadProfileImageViaCloudFunction(userId: String?): Bitmap {
        require(!userId.isNullOrBlank()) { "userId requis pour getPartnerProfileImage" }
        val data = mapOf("partnerId" to userId)
        val result = FirebaseFunctions.getInstance()
            .getHttpsCallable("getPartnerProfileImage")
            .call(data)
            .await()

        val map = result.data as? Map<*, *> ?: error("Réponse invalide")
        val success = map["success"] as? Boolean ?: false
        val signedUrl = map["imageUrl"] as? String
        require(success && !signedUrl.isNullOrBlank()) { "Cloud Function getPartnerProfileImage a échoué" }

        return loadFromHttp(signedUrl!!)
    }

    private suspend fun loadImageViaSignedURL(filePath: String): Bitmap {
        val data = mapOf("filePath" to filePath)
        return try {
            val result = FirebaseFunctions.getInstance()
                .getHttpsCallable("getSignedImageURL")
                .call(data)
                .await()
            val map = result.data as? Map<*, *> ?: error("Réponse invalide")
            val success = map["success"] as? Boolean ?: false
            val signedUrl = map["signedUrl"] as? String
            if (success && !signedUrl.isNullOrBlank()) {
                loadFromHttp(signedUrl!!)
            } else {
                Log.w(TAG, "Réponse Cloud Function invalide → fallback direct")
                loadFromFirebaseStorageDirect("$FALLBACK_GS_BUCKET/$filePath")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "getSignedImageURL a échoué → fallback direct", t)
            loadFromFirebaseStorageDirect("$FALLBACK_GS_BUCKET/$filePath")
        }
    }

    private suspend fun loadFromFirebaseStorageDirect(urlOrGs: String): Bitmap {
        Log.d(TAG, "Download direct depuis Firebase Storage (URL masquée)")
        val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(urlOrGs)
        val bytes = storageRef.getBytes(10L * 1024L * 1024L).await()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun extractFilePathFromDownloadUrl(downloadUrl: String): String? {
        // Cherche entre "/o/" et "?"
        val regex = "/o/([^?]+)".toRegex()
        val match = regex.find(downloadUrl) ?: return null
        val encoded = match.groupValues.getOrNull(1) ?: return null
        return try {
            java.net.URLDecoder.decode(encoded, "UTF-8")
        } catch (_: Throwable) {
            encoded
        }
    }

    private suspend fun loadFromHttp(urlString: String): Bitmap {
        Log.d(TAG, "HTTP download (URL masquée)")
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = true
        }
        return connection.inputStream.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }
}

// ---------------------------------------------------------------
// ImageCacheService — cache mémoire (équivalent du service Swift)
// ---------------------------------------------------------------
package com.love2loveapp.core.utils

import android.graphics.Bitmap
import android.util.LruCache

object ImageCacheService {
    private val cache: LruCache<String, Bitmap>

    init {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxKb / 8 // ~12.5% de la RAM
        cache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    fun getCached(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
    fun clear() { cache.evictAll() }
}

// ---------------------------------------------------------------
// Exemple d'utilisation (Composable)
// ---------------------------------------------------------------
package com.love2loveapp.feature.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.love2loveapp.core.ui.AsyncImageView

@Composable
fun AsyncImageViewPreview() {
    Column(Modifier.padding(16.dp)) {
        AsyncImageView(imageUrl = null, width = 200.dp, height = 150.dp)
        AsyncImageView(imageUrl = "", width = 200.dp, height = 150.dp)
        AsyncImageView(imageUrl = "invalid-url", width = 200.dp, height = 150.dp)
    }
}
