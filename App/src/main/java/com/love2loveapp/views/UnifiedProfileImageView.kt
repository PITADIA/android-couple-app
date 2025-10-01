package com.love2loveapp.views

import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.trace
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.love2loveapp.AppDelegate
import androidx.compose.runtime.collectAsState
import com.love2loveapp.services.profile.ProfileRepository

/**
 * 🎯 COMPOSANT UNIFIÉ POUR AFFICHAGE IMAGES PROFIL
 * 
 * Architecture identique iOS ProfileImageView :
 * - Point d'entrée unique pour toutes les images profil
 * - Gestion automatique du cache via ProfileImageManager
 * - Affichage conditionnel : image → initiales → placeholder
 * - Mise à jour automatique via StateFlow
 * - Support utilisateur ET partenaire
 */
@Composable
fun UnifiedProfileImageView(
    imageType: ProfileImageType,
    size: Dp = 50.dp,
    userName: String = "",
    partnerName: String = "",
    onClick: (() -> Unit)? = null,
    onImageUpdated: ((Bitmap) -> Unit)? = null, // 🎯 Nouveau callback pour onboarding
    showEditIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    val profileImageManager = AppDelegate.profileImageManager
    val context = LocalContext.current
    
    // 🎯 Launcher pour CropImage (doit être défini AVANT galleryLauncher)
    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract()
    ) { result ->
        if (result.isSuccessful) {
            val resultUri = result.uriContent
            resultUri?.let { uri ->
                Log.d("UnifiedProfileImageView", "✅ Image croppée avec succès: $uri")
                try {
                    val bitmap = loadBitmapFromUri(context, uri)
                    bitmap?.let {
                        // Cache immédiat pour affichage
                        handleImageProcessed(it, context, AppDelegate.profileRepository, false)
                        
                        // 🎯 Notifier le callback si fourni (pour onboarding)
                        onImageUpdated?.invoke(it)
                        Log.d("UnifiedProfileImageView", "✅ Callback onImageUpdated appelé")
                    }
                } catch (e: Exception) {
                    Log.e("UnifiedProfileImageView", "❌ Erreur chargement image croppée", e)
                }
            }
        } else {
            Log.e("UnifiedProfileImageView", "❌ Erreur CropImage: ${result.error}")
        }
    }

    // 🎯 Launcher pour ouvrir la galerie directement
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            Log.d("UnifiedProfileImageView", "🖼️ Image sélectionnée depuis la galerie: $uri")
            // Lancer directement l'éditeur CropImage avec bonne syntaxe
            val cropOptions = CropImageContractOptions(
                uri = uri,
                cropImageOptions = CropImageOptions().apply {
                    guidelines = com.canhub.cropper.CropImageView.Guidelines.OFF
                    cropShape = com.canhub.cropper.CropImageView.CropShape.OVAL
                    aspectRatioX = 1
                    aspectRatioY = 1
                    fixAspectRatio = true
                    maxZoom = 4
                    outputCompressQuality = 80
                    outputRequestWidth = 300
                    outputRequestHeight = 300
                    activityTitle = "Ajustez votre photo"
                    activityMenuIconColor = android.graphics.Color.parseColor("#FD267A")
                    borderLineColor = android.graphics.Color.parseColor("#FD267A")
                    borderCornerColor = android.graphics.Color.parseColor("#FD267A")
                }
            )
            cropImageLauncher.launch(cropOptions)
        }
    }
    
    // 🔄 Observation des images via StateFlow (comme iOS)
    val userImage by profileImageManager?.currentUserImage?.collectAsState() ?: remember { mutableStateOf(null) }
    val partnerImage by profileImageManager?.partnerImage?.collectAsState() ?: remember { mutableStateOf(null) }
    
    // 🎯 Sélection de l'image selon le type
    val displayImage: Bitmap? = when (imageType) {
        ProfileImageType.USER -> userImage
        ProfileImageType.PARTNER -> partnerImage
    }
    
    val displayName = when (imageType) {
        ProfileImageType.USER -> userName
        ProfileImageType.PARTNER -> partnerName
    }
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (imageType == ProfileImageType.USER) {
                    // 🎯 Pour USER: ouvrir directement la galerie
                    Modifier.clickable { 
                        Log.d("UnifiedProfileImageView", "🖼️ Clic photo utilisateur - ouverture galerie")
                        galleryLauncher.launch("image/*")
                    }
                } else if (onClick != null) {
                    // Pour PARTNER: utiliser onClick personnalisé
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (displayImage != null) {
            // ✅ Affichage image bitmap
            Image(
                bitmap = displayImage.asImageBitmap(),
                contentDescription = "Photo de profil $displayName",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (displayName.isNotEmpty()) {
            // 📝 Affichage initiales
            UserInitialsView(
                name = displayName,
                size = size,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 👤 Bonhomme blanc pour partenaire non connecté (au lieu du "?")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Partenaire non connecté",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
        }
        
        // 🎨 Icône d'édition (optionnel)
        if (showEditIcon && imageType == ProfileImageType.USER) {
            // TODO: Ajouter icône d'édition en overlay
        }
    }
}

/**
 * 📝 COMPOSANT INITIALES UTILISATEUR
 * Identique à iOS UserInitialsView
 */
@Composable
private fun UserInitialsView(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val initials = getInitials(name)
    val backgroundColor = getColorForName(name)
    
    Box(
        modifier = modifier
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = (size.value * 0.35).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 📝 Extraire initiales du nom complet
 */
private fun getInitials(name: String): String {
    return name
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
}

/**
 * 🎨 Couleur basée sur le nom (hash)
 * Identique à iOS avec palette cohérente
 */
private fun getColorForName(name: String): Color {
    val colors = listOf(
        Color(0xFF6B73FF), // Bleu violet
        Color(0xFF9B59B6), // Violet
        Color(0xFF3498DB), // Bleu
        Color(0xFF1ABC9C), // Turquoise
        Color(0xFF2ECC71), // Vert
        Color(0xFFF39C12), // Orange
        Color(0xFFE74C3C), // Rouge
        Color(0xFFE91E63), // Rose
        Color(0xFF9C27B0), // Purple
        Color(0xFF607D8B)  // Bleu gris
    )
    
    val hash = name.hashCode()
    val index = kotlin.math.abs(hash) % colors.size
    return colors[index]
}

/**
 * 🌈 Génère une couleur dégradée pour les initiales
 */
private fun generateGradientColor(name: String): Color {
    val hash = name.hashCode()
    val hue = (kotlin.math.abs(hash) % 360).toFloat()
    val saturation = 0.7f
    val value = 0.9f
    
    val hsv = floatArrayOf(hue, saturation, value)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * 🎨 Traitement image après CropImage
 * Mode profil uniquement : Cache + Upload immédiat
 */
private fun handleImageProcessed(
    bitmap: Bitmap, 
    context: Context,
    profileRepository: ProfileRepository? = null,
    isOnboarding: Boolean = false
) {
    Log.d("UnifiedProfileImageView", "🎨 Traitement image terminé: ${bitmap.width}x${bitmap.height}")
    
    // 1. Cache immédiat pour affichage instantané (comme iOS)
    val userCacheManager = AppDelegate.userCacheManager
    userCacheManager.setCachedProfileImage(bitmap, null)
    Log.d("UnifiedProfileImageView", "💾 Image mise en cache local")

    // 2. Upload conditionnel selon le mode
    if (!isOnboarding && profileRepository != null) {
        Log.d("UnifiedProfileImageView", "☁️ Upload Firebase depuis le menu profil")
        val profileImageManager = AppDelegate.profileImageManager
        profileImageManager?.setTemporaryUserImage(bitmap)
    } else {
        Log.d("UnifiedProfileImageView", "⏳ Image gardée en cache (mode onboarding)")
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
        Log.e("UnifiedProfileImageView", "❌ Erreur chargement bitmap depuis URI", e)
        null
    }
}

/**
 * 🔍 Énumérations pour types d'images profil
 */
enum class ProfileImageType {
    USER,
    PARTNER
}