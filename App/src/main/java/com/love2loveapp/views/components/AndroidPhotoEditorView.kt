package com.love2loveapp.views.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import com.love2loveapp.R
// Services pour cache et upload
// import com.love2loveapp.services.ImageCacheService  
// import com.love2loveapp.services.FirebaseUserService
import com.love2loveapp.services.cache.UserCacheManager
import com.canhub.cropper.*
import com.love2loveapp.utils.rememberCameraPermissionLauncher
import com.love2loveapp.services.profile.ProfileRepository
import com.love2loveapp.AppDelegate
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 🎨 AndroidPhotoEditorView - Équivalent sophistiqué de SwiftyCrop iOS
 * 
 * Fonctionnalités équivalentes à iOS:
 * - Zoom 4x maximum (maxMagnificationScale)
 * - Recadrage circulaire (cropImageCircular) 
 * - Interface intuitive (pinch to zoom, drag to position)
 * - Cache immédiat + upload background
 * - Qualité JPEG 80% 
 * - Redimensionnement à 300x300px
 */
@Composable
fun AndroidPhotoEditorView(
    currentImage: Bitmap? = null,
    onImageUpdated: (Bitmap) -> Unit,
    onError: (String) -> Unit = {},
    profileRepository: ProfileRepository? = null,
    isOnboarding: Boolean = false, // ✅ NOUVEAU : Différencier onboarding/menu
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showActionSheet by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var processedImage by remember { mutableStateOf(currentImage) }
    var isProcessing by remember { mutableStateOf(false) }
    
    // CropImage result launcher
    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract()
    ) { result ->
        if (result.isSuccessful) {
            val resultUri = result.uriContent
            resultUri?.let { uri ->
                Log.d("AndroidPhotoEditor", "✂️ CropImage terminé: $uri")
                try {
                    val bitmap = loadBitmapFromUri(context, uri)
                    bitmap?.let {
                        processedImage = it
                        handleImageProcessed(it, onImageUpdated, context, profileRepository, isOnboarding)
                    }
                } catch (e: Exception) {
                    Log.e("AndroidPhotoEditor", "❌ Erreur chargement image croppée", e)
                    onError("Erreur lors du traitement de l'image")
                }
            }
        } else {
            val error = result.error
            Log.w("AndroidPhotoEditor", "❌ CropImage annulé ou échoué: $error")
        }
        isProcessing = false
    }
    
    fun launchCropImage(sourceUri: Uri) {
        isProcessing = true
        try {
            // Configuration équivalente à SwiftyCrop iOS avec CropImage
            val cropOptions = CropImageContractOptions(
                uri = sourceUri,
                cropImageOptions = CropImageOptions().apply {
                    guidelines = CropImageView.Guidelines.OFF // Pas de grille
                    cropShape = CropImageView.CropShape.OVAL // Équivalent cropImageCircular
                    aspectRatioX = 1 // Ratio carré
                    aspectRatioY = 1
                    fixAspectRatio = true
                    maxZoom = 4 // Équivalent maxMagnificationScale: 4.0
                    outputCompressQuality = 80 // Compression 80%
                    outputRequestWidth = 300 // Redimensionnement
                    outputRequestHeight = 300
                    
                    // Interface utilisateur
                    activityTitle = "Ajustez votre photo"
                    activityMenuIconColor = android.graphics.Color.parseColor("#FD267A")
                    borderLineColor = android.graphics.Color.parseColor("#FD267A")
                    borderCornerColor = android.graphics.Color.parseColor("#FD267A")
                    activityBackgroundColor = android.graphics.Color.parseColor("#F7F7FA")
                    borderLineThickness = 3f
                    borderCornerThickness = 5f
                    borderCornerLength = 14f
                    
                    // Boutons de navigation
                    showCropOverlay = true
                    allowRotation = false
                    allowFlipping = false
                    allowCounterRotation = false
                    showCropLabel = false
                    showProgressBar = true
                }
            )
            
            cropImageLauncher.launch(cropOptions)
            
        } catch (e: Exception) {
            Log.e("AndroidPhotoEditor", "❌ Erreur lancement CropImage", e)
            onError("Impossible d'ouvrir l'éditeur d'image")
            isProcessing = false
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            Log.d("AndroidPhotoEditor", "📸 Photo prise avec la caméra: ${it.width}x${it.height}")
            // Sauvegarder temporairement pour CropImage
            val tempUri = saveBitmapToTempFile(context, it)
            tempUri?.let { uri ->
                selectedImageUri = uri
                launchCropImage(uri)
            }
        }
    }
    
    // 🔐 Gestion des permissions caméra
    val requestCameraPermission = rememberCameraPermissionLauncher(
        onPermissionGranted = {
            Log.d("AndroidPhotoEditor", "📸 Permission caméra accordée")
            cameraLauncher.launch(null)
        },
        onPermissionDenied = {
            Log.w("AndroidPhotoEditor", "❌ Permission caméra refusée")
            onError("L'accès à la caméra est nécessaire pour prendre des photos")
        }
    )

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            Log.d("AndroidPhotoEditor", "🖼️ Image sélectionnée depuis la galerie: $uri")
            selectedImageUri = it
            launchCropImage(it)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Photo de profil actuelle ou placeholder avec design élégant comme les cartes du quiz
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.05f),
                        spotColor = Color.Black.copy(alpha = 0.05f)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { 
                        if (!isProcessing) {
                            showActionSheet = true 
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                processedImage?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Photo de profil",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFFD267A) // Rose de l'app
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.add_photo),
                            fontSize = 14.sp,
                            color = Color.Black.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Overlay de processing
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

        }

        // Action Sheet (Bottom Sheet style)
        if (showActionSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable { showActionSheet = false }
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Choisir une photo",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Option Galerie
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showActionSheet = false
                                    Log.d("AndroidPhotoEditor", "🖼️ Ouverture galerie")
                                    galleryLauncher.launch("image/*")
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFFFD267A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Galerie",
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        }
                        
                        // Option Caméra (avec demande de permission)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showActionSheet = false
                                    Log.d("AndroidPhotoEditor", "📸 Demande permission caméra")
                                    requestCameraPermission()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color(0xFFFD267A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Caméra",
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Bouton Annuler
                        TextButton(
                            onClick = { showActionSheet = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Annuler",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🚀 Traitement sophistiqué équivalent iOS : Cache immédiat + Upload conditionnel
 * 
 * LOGIQUE IDENTIQUE iOS :
 * - ONBOARDING : Cache seulement (upload à la finalisation)  
 * - MENU : Cache + Upload immédiat
 */
private fun handleImageProcessed(
    bitmap: Bitmap, 
    onImageUpdated: (Bitmap) -> Unit,
    context: Context,
    profileRepository: ProfileRepository? = null,
    isOnboarding: Boolean = false
) {
    Log.d("AndroidPhotoEditor", "🎨 Traitement image terminé: ${bitmap.width}x${bitmap.height}")
    Log.d("AndroidPhotoEditor", "📍 Mode: ${if (isOnboarding) "ONBOARDING" else "MENU"}")
    
    // 1. Cache immédiat pour affichage instantané (comme iOS)
    try {
        val userCacheManager = UserCacheManager.getInstance(context)
        userCacheManager.setCachedProfileImage(bitmap, null)
        Log.d("AndroidPhotoEditor", "✅ Image mise en cache immédiatement")
        
        // 2. Callback pour l'UI
        onImageUpdated(bitmap)
        
        // 3. Upload conditionnel selon mode (LOGIQUE iOS EXACTE)
        if (isOnboarding) {
            // 🎓 ONBOARDING : Stockage temporaire uniquement (comme iOS)
            Log.d("AndroidPhotoEditor", "🎓 Mode ONBOARDING: Image cachée, upload différé jusqu'à finalisation")
        } else if (profileRepository != null) {
            // 🍽️ MENU : Upload immédiat (comme iOS)
            Log.d("AndroidPhotoEditor", "🍽️ Mode MENU: Démarrage upload Firebase immédiat...")
            
            // Coroutine background pour upload
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Conversion bitmap vers ByteArray pour upload
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val imageBytes = outputStream.toByteArray()
                    
                    // Upload vers Firebase avec bytes
                    val result = profileRepository.updateProfileImage(imageBytes)
                    result.onSuccess { downloadUrl ->
                        Log.d("AndroidPhotoEditor", "🔥 Upload Firebase réussi: $downloadUrl")
                        // 4. Mise à jour cache avec URL
                        userCacheManager.setCachedProfileImage(bitmap, downloadUrl)
                    }.onFailure { error ->
                        Log.w("AndroidPhotoEditor", "⚠️ Upload Firebase échoué: ${error.message}")
                    }
                } catch (e: Exception) {
                    Log.e("AndroidPhotoEditor", "❌ Erreur upload Firebase: ${e.message}")
                }
            }
        } else {
            Log.d("AndroidPhotoEditor", "ℹ️ Upload Firebase non disponible (ProfileRepository manquant)")
        }
        
    } catch (e: Exception) {
        Log.e("AndroidPhotoEditor", "❌ Erreur traitement image", e)
    }
}

/**
 * Helper pour sauvegarder bitmap temporairement pour UCrop
 */
private fun saveBitmapToTempFile(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        Uri.fromFile(tempFile)
    } catch (e: Exception) {
        Log.e("AndroidPhotoEditor", "❌ Erreur sauvegarde bitmap temporaire", e)
        null
    }
}

/**
 * Helper pour charger bitmap depuis URI
 */
private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        Log.e("AndroidPhotoEditor", "❌ Erreur chargement bitmap depuis URI", e)
        null
    }
}
