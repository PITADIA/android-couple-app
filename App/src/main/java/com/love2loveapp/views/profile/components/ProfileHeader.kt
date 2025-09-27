package com.love2loveapp.views.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.love2loveapp.models.UserProfile
import com.love2loveapp.views.UnifiedProfileImageView
import com.love2loveapp.views.ProfileImageType

/**
 * 👤 ProfileHeader - Header avec photo de profil et nom
 * 
 * Équivalent du header iOS MenuView :
 * - Photo de profil cliquable avec effet surbrillance
 * - Fallback initiales avec dégradé personnalisé
 * - Nom utilisateur centré
 * - Indicateur loading pendant upload
 */
@Composable
fun ProfileHeader(
    user: UserProfile?,
    isLoading: Boolean = false,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 🔑 PHOTO DE PROFIL AVEC EFFET SURBRILLANCE
        Box(
            contentAlignment = Alignment.Center
        ) {
            // ✨ Effet surbrillance (identique iOS)
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .background(
                        Color.White.copy(alpha = 0.35f),
                        CircleShape
                    )
                    .blur(6.dp)
            )

            // 🎯 PHOTO DE PROFIL UNIFIÉE - Observe ProfileImageManager StateFlow
            UnifiedProfileImageView(
                imageType = ProfileImageType.USER,
                size = 120.dp,
                userName = user?.name ?: "",
                onClick = { onPhotoClick() },
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )

            // 🔄 Loading overlay pendant upload
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        // 📝 NOM UTILISATEUR
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = user?.name ?: "Utilisateur",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )

            // 📊 Score complétion profil (optionnel)
            if (user != null && user.completionScore < 100) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = user.completionScore / 100f,
                        modifier = Modifier
                            .width(80.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = Color(0xFFFF6B9D),
                        trackColor = Color(0xFFE0E0E0)
                    )
                    Text(
                        text = "${user.completionScore}%",
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 💕 Statut relation (si définie)
            user?.relationshipDuration?.let { duration ->
                if (duration.isNotEmpty() && duration != "Moins d'un mois") {
                    Text(
                        text = "En couple depuis $duration",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * 🎨 ProfileHeaderPlaceholder - Placeholder pendant chargement
 */
@Composable
fun ProfileHeaderPlaceholder(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Placeholder photo
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    Color(0xFFE0E0E0),
                    CircleShape
                )
                .shimmerEffect()
        )

        // Placeholder nom
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(24.dp)
                .background(
                    Color(0xFFE0E0E0),
                    CircleShape
                )
                .shimmerEffect()
        )

        // Placeholder sous-titre
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(16.dp)
                .background(
                    Color(0xFFE0E0E0),
                    CircleShape
                )
                .shimmerEffect()
        )
    }
}

/**
 * ✨ Effet shimmer pour placeholders
 */
@Composable
private fun Modifier.shimmerEffect(): Modifier {
    return this.background(
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFE0E0E0),
                Color(0xFFD0D0D0),
                Color(0xFFE0E0E0)
            ),
            startX = 0f,
            endX = 300f
        )
    )
}
