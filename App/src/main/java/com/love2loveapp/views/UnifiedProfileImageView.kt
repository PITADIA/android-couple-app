package com.love2loveapp.views

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.love2loveapp.AppDelegate
import androidx.compose.runtime.collectAsState

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
    showEditIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    val profileImageManager = AppDelegate.profileImageManager
    
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
                if (onClick != null) {
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
            // 👤 Placeholder par défaut
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = Color.White,
                    fontSize = (size.value * 0.4).sp,
                    fontWeight = FontWeight.Bold
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
 * 🎨 GÉNÉRATION INITIALES
 */
private fun getInitials(name: String): String {
    if (name.isEmpty()) return "?"
    
    val words = name.trim().split(" ")
    return when {
        words.size >= 2 -> "${words[0].firstOrNull()?.uppercase()}${words[1].firstOrNull()?.uppercase()}"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "?"
    }
}

/**
 * 🌈 GÉNÉRATION COULEUR BASÉE SUR LE NOM
 */
private fun getColorForName(name: String): Color {
    val colors = listOf(
        Color(0xFF6B73FF), // Bleu
        Color(0xFF9B59B6), // Violet
        Color(0xFF3498DB), // Bleu clair
        Color(0xFF1ABC9C), // Turquoise
        Color(0xFF2ECC71), // Vert
        Color(0xFFF39C12), // Orange
        Color(0xFFE74C3C), // Rouge
        Color(0xFF95A5A6), // Gris
        Color(0xFFE67E22), // Orange foncé
        Color(0xFF34495E)  // Bleu foncé
    )
    
    val hash = name.hashCode()
    val index = kotlin.math.abs(hash) % colors.size
    return colors[index]
}

/**
 * 🏷️ TYPE D'IMAGE PROFIL
 */
enum class ProfileImageType {
    USER,    // Image utilisateur actuel
    PARTNER  // Image partenaire
}
