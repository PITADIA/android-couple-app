package com.love2loveapp.views

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.love2loveapp.AppDelegate
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.ui.graphics.Color

/**
 * 🎯 ÉDITEUR UNIFIÉ POUR PHOTOS DE PROFIL
 * 
 * Architecture identique iOS ProfileImageEditor :
 * - Point d'entrée unique pour édition photos
 * - Gestion automatique onboarding vs menu
 * - Upload conditionnel via ProfileImageManager
 * - Interface utilisateur cohérente
 */
@Composable
fun UnifiedProfileImageEditor(
    isOnboarding: Boolean = false,
    currentImage: Bitmap? = null,
    onImageUpdated: (Bitmap) -> Unit = {},
    onError: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileImageManager = AppDelegate.profileImageManager
    
    var showImagePicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    
    // ✂️ Lanceur pour crop d'image (défini avant galleryLauncher)
    val cropLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract()
    ) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            croppedUri?.let { uri ->
                Log.d("UnifiedProfileImageEditor", "✂️ Image croppée: $uri")
                
                scope.launch {
                    try {
                        isProcessing = true
                        
                        // Conversion Uri → Bitmap
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        
                        if (bitmap != null) {
                            Log.d("UnifiedProfileImageEditor", "🎨 Bitmap créé: ${bitmap.width}x${bitmap.height}")
                            
                            if (isOnboarding) {
                                // 🎓 MODE ONBOARDING: Stockage temporaire seulement
                                Log.d("UnifiedProfileImageEditor", "🎓 Mode onboarding - stockage temporaire")
                                profileImageManager?.setTemporaryUserImage(bitmap)
                                onImageUpdated(bitmap)
                            } else {
                                // 🍽️ MODE MENU: Upload immédiat
                                Log.d("UnifiedProfileImageEditor", "🍽️ Mode menu - upload immédiat")
                                
                                if (profileImageManager != null) {
                                    Log.d("UnifiedProfileImageEditor", "✅ ProfileImageManager disponible - upload via nouveau système")
                                    val result = profileImageManager.uploadUserImage(bitmap)
                                    result.onSuccess { downloadUrl ->
                                        Log.d("UnifiedProfileImageEditor", "✅ Upload réussi: $downloadUrl")
                                        
                                        // 🔔 Notifier tous les composants observant ProfileImageManager
                                        AppDelegate.profileImageManager?.notifyCacheChanged()
                                        
                                        onImageUpdated(bitmap)
                                    }.onFailure { error ->
                                        Log.e("UnifiedProfileImageEditor", "❌ Upload échoué: ${error.message}")
                                        onError("Erreur upload: ${error.message}")
                                    }
                                } else {
                                    Log.e("UnifiedProfileImageEditor", "❌ ProfileImageManager NULL - fallback ancien système")
                                    onError("ProfileImageManager non disponible")
                                }
                            }
                        } else {
                            Log.e("UnifiedProfileImageEditor", "❌ Impossible de créer bitmap depuis URI")
                            onError("Impossible de traiter l'image")
                        }
                    } catch (e: Exception) {
                        Log.e("UnifiedProfileImageEditor", "❌ Erreur traitement image: ${e.message}")
                        onError("Erreur traitement: ${e.message}")
                    } finally {
                        isProcessing = false
                    }
                }
            }
        } else {
            Log.w("UnifiedProfileImageEditor", "⚠️ Crop annulé ou échoué")
        }
    }
    
    // 📸 Lanceur pour sélection image depuis galerie
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            Log.d("UnifiedProfileImageEditor", "📸 Image sélectionnée depuis galerie: $uri")
            // Lancer le crop après sélection
            cropLauncher.launch(
                CropImageContractOptions(
                    uri = uri,
                    cropImageOptions = CropImageOptions().apply {
                        aspectRatioX = 1
                        aspectRatioY = 1
                        fixAspectRatio = true
                    }
                )
            )
        }
    }
    
    // 📷 Lanceur pour prise de photo
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            Log.d("UnifiedProfileImageEditor", "📷 Photo prise: ${bitmap.width}x${bitmap.height}")
            
            scope.launch {
                try {
                    isProcessing = true
                    
                    if (isOnboarding) {
                        // 🎓 MODE ONBOARDING: Stockage temporaire seulement
                        Log.d("UnifiedProfileImageEditor", "🎓 Mode onboarding - stockage temporaire")
                        profileImageManager?.setTemporaryUserImage(bitmap)
                        onImageUpdated(bitmap)
                    } else {
                        // 🍽️ MODE MENU: Upload immédiat
                        Log.d("UnifiedProfileImageEditor", "🍽️ Mode menu - upload immédiat")
                        
                        if (profileImageManager != null) {
                            Log.d("UnifiedProfileImageEditor", "✅ ProfileImageManager disponible - upload via nouveau système")
                            val result = profileImageManager.uploadUserImage(bitmap)
                            result.onSuccess { downloadUrl ->
                                Log.d("UnifiedProfileImageEditor", "✅ Upload réussi: $downloadUrl")
                                
                                // 🔔 Notifier tous les composants observant ProfileImageManager
                                AppDelegate.profileImageManager?.notifyCacheChanged()
                                
                                onImageUpdated(bitmap)
                            }.onFailure { error ->
                                Log.e("UnifiedProfileImageEditor", "❌ Upload échoué: ${error.message}")
                                onError("Erreur upload: ${error.message}")
                            }
                        } else {
                            Log.e("UnifiedProfileImageEditor", "❌ ProfileImageManager NULL - fallback ancien système")
                            onError("ProfileImageManager non disponible")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UnifiedProfileImageEditor", "❌ Erreur traitement photo: ${e.message}")
                    onError("Erreur traitement: ${e.message}")
                } finally {
                    isProcessing = false
                }
            }
        }
    }
    
    // 🎨 Interface utilisateur
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 🖼️ Aperçu image actuelle
        if (currentImage != null) {
            UnifiedProfileImageView(
                imageType = ProfileImageType.USER,
                size = 120.dp,
                userName = "", // Pas d'initiales si on a une image
                modifier = Modifier.padding(16.dp)
            )
        }
        
        // 🔄 Indicateur de traitement
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color(0xFFFD267A)
            )
            Text("Traitement en cours...")
        } else {
            // 🎯 Ouverture automatique de la galerie (pas de boutons)
            LaunchedEffect(Unit) {
                Log.d("UnifiedProfileImageEditor", "🖼️ Ouverture automatique de la galerie")
                galleryLauncher.launch("image/*")
            }
            
            // Message d'information pendant l'ouverture
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Ouverture de la galerie...",
                    color = Color(0xFF666666)
                )
            }
        }
    }
}

/**
 * 🎯 DIALOG UNIFIÉ POUR ÉDITION PHOTO PROFIL
 * Utilisé depuis le menu ou autres écrans
 */
@Composable
fun UnifiedProfileImageEditorDialog(
    currentImage: Bitmap? = null,
    onImageUpdated: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Modifier la photo de profil",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                UnifiedProfileImageEditor(
                    isOnboarding = false, // Mode menu
                    currentImage = currentImage,
                    onImageUpdated = { bitmap ->
                        onImageUpdated(bitmap)
                        onDismiss()
                    },
                    onError = { error ->
                        Log.e("UnifiedProfileImageEditorDialog", "❌ Erreur: $error")
                        // TODO: Afficher toast d'erreur
                    }
                )
                
                // Bouton fermer
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Annuler")
                }
            }
        }
    }
}
