package com.love2loveapp.views.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.love2loveapp.services.cache.UserCacheManager

/**
 * 👤 UserProfileImage - Photo de profil utilisateur avec hiérarchie d'affichage
 * 
 * Priorités d'affichage (ordre iOS équivalent):
 * 1. Cache local ultra-rapide (UserCacheManager)
 * 2. URL Firebase avec AsyncImage + mise en cache
 * 3. Initiales colorées (UserInitialsView)
 * 4. Icône générique grise
 * 
 * Design:
 * - Effet surbrillance blur 6dp
 * - Bordure blanche 3dp constante
 * - Taille configurable (défaut 80dp)
 */
@Composable
fun UserProfileImage(
    imageURL: String?,
    userName: String,
    size: Dp,
    userCacheManager: UserCacheManager?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size + 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Effet surbrillance
        Box(
            modifier = Modifier
                .size(size + 12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f))
                .blur(radius = 6.dp)
        )
        
        // PRIORITÉ 1: Cache local ultra-rapide
        val cachedImage = userCacheManager?.getCachedProfileImage()
        if (cachedImage != null) {
            Image(
                bitmap = cachedImage.asImageBitmap(),
                contentDescription = "Photo de profil",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        // PRIORITÉ 2: URL Firebase (TODO: Intégrer Coil AsyncImage)
        else if (!imageURL.isNullOrEmpty()) {
            // Pour l'instant, on utilise les initiales en attendant Coil
            if (userName.isNotEmpty()) {
                UserInitialsView(name = userName, size = size)
            } else {
                // Fallback icône grise
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profil",
                        modifier = Modifier.size(size * 0.4f),
                        tint = Color.Gray
                    )
                }
            }
        }
        // PRIORITÉ 3: Initiales colorées
        else if (userName.isNotEmpty()) {
            UserInitialsView(name = userName, size = size)
        }
        // PRIORITÉ 4: Icône générique grise
        else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profil",
                    modifier = Modifier.size(size * 0.4f),
                    tint = Color.Gray
                )
            }
        }
        
        // Bordure blanche constante
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(3.dp, Color.White, CircleShape)
        )
    }
}
