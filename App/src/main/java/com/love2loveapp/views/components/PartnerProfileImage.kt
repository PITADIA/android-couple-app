package com.love2loveapp.views.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.services.cache.UserCacheManager
import android.util.Log

private const val TAG = "PartnerProfileImage"

/**
 * 👥 PartnerProfileImage - Photo de profil partenaire avec logique différentielle
 * 
 * Comportements iOS équivalents:
 * - hasPartner = true : Cache → Firebase → Initiales → Point d'interrogation
 * - hasPartner = false : Petit bonhomme blanc + invitation (non cliquable)
 * - Surbrillance différentielle : 35% si partenaire, 20% sinon
 * - Bordure : 3dp si partenaire, 2dp sinon
 * - onClick uniquement si pas de partenaire connecté
 */
@Composable
fun PartnerProfileImage(
    hasPartner: Boolean,
    imageURL: String?,
    partnerName: String,
    size: Dp,
    onClick: (() -> Unit)?,
    userCacheManager: UserCacheManager?,
    modifier: Modifier = Modifier
) {
    // 🔍 LOGS DE TRACE pour debugging
    LaunchedEffect(hasPartner, imageURL, partnerName) {
        Log.d(TAG, "🖼️ PartnerProfileImage - ÉTAT:")
        Log.d(TAG, "   - hasPartner: $hasPartner")
        Log.d(TAG, "   - partnerName: '$partnerName'")
        Log.d(TAG, "   - imageURL: ${imageURL?.let { "${it.take(50)}${if (it.length > 50) "..." else ""}" } ?: "null"}")
        Log.d(TAG, "   - userCacheManager: ${if (userCacheManager != null) "✅ Présent" else "❌ Null"}")
    }
    val clickableModifier = if (onClick != null && !hasPartner) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }
    
    Box(
        modifier = clickableModifier.size(size + 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Effet surbrillance différentiel
        Box(
            modifier = Modifier
                .size(size + 12.dp)
                .clip(CircleShape)
                .background(
                    Color.White.copy(alpha = if (hasPartner) 0.35f else 0.2f)
                )
                .blur(radius = 6.dp)
        )
        
        if (hasPartner) {
            // PARTENAIRE CONNECTÉ
            
            // PRIORITÉ 1: Cache partenaire
            val cachedPartnerImage = userCacheManager?.getCachedPartnerImage()
            Log.d(TAG, "🖼️ PRIORITÉ 1 - Cache: ${if (cachedPartnerImage != null) "✅ IMAGE EN CACHE TROUVÉE" else "❌ PAS D'IMAGE EN CACHE"}")
            
            if (cachedPartnerImage != null) {
                Log.d(TAG, "✅ AFFICHAGE: Image depuis cache")
                Image(
                    bitmap = cachedPartnerImage.asImageBitmap(),
                    contentDescription = "Photo partenaire",
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                // Vérifier changement URL en arrière-plan
                LaunchedEffect(imageURL) {
                    if (imageURL != null && userCacheManager?.hasPartnerImageChanged(imageURL) == true) {
                        Log.d(TAG, "🔄 URL partenaire a changé, besoin de recharger")
                        // TODO: Télécharger nouvelle image si changement détecté
                        // downloadAndCachePartnerImage(imageURL, userCacheManager)
                    }
                }
            }
            // PRIORITÉ 2: URL Firebase partenaire (TODO: Intégrer Coil AsyncImage)
            else if (!imageURL.isNullOrEmpty()) {
                Log.d(TAG, "🖼️ PRIORITÉ 2 - URL Firebase présente, mais pas Coil → affichage initiales")
                // Pour l'instant, on utilise les initiales en attendant Coil
                if (partnerName.isNotEmpty()) {
                    UserInitialsView(name = partnerName, size = size)
                } else {
                    QuestionMarkView(size = size)
                }
            }
            // PRIORITÉ 3: Initiales partenaire
            else if (partnerName.isNotEmpty()) {
                Log.d(TAG, "🖼️ PRIORITÉ 3 - Affichage initiales: '$partnerName'")
                UserInitialsView(name = partnerName, size = size)
            }
            // PRIORITÉ 4: Point d'interrogation
            else {
                Log.d(TAG, "🖼️ PRIORITÉ 4 - Aucune info → point d'interrogation")
                QuestionMarkView(size = size)
            }
        } else {
            Log.d(TAG, "👤 PAS DE PARTENAIRE → icône invitation")
            // PAS DE PARTENAIRE → Petit bonhomme blanc + invitation
            PersonIconView(size = size)
        }
        
        // Bordure différentielle
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(
                    width = if (hasPartner) 3.dp else 2.dp,
                    color = Color.White,
                    shape = CircleShape
                )
        )
    }
}

/**
 * ❓ Point d'interrogation pour partenaire sans nom/photo
 */
@Composable
private fun QuestionMarkView(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * 👤 Petit bonhomme blanc pour utilisateur sans partenaire
 * Design identique à l'image fournie : fond gris clair + icône personne blanche
 */
@Composable
private fun PersonIconView(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.15f)), // Fond gris clair comme sur l'image
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = "Partenaire non connecté",
            tint = Color.White, // Petit bonhomme blanc comme sur l'image
            modifier = Modifier.size(size * 0.6f) // Taille proportionnelle
        )
    }
}
