package com.love2loveapp.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 🔤 UserInitialsView - Génération d'initiales colorées cohérentes
 * 
 * Équivalent exact de UserInitialsView iOS:
 * - Extraction première lettre majuscule du nom
 * - Couleur basée sur hash du nom (cohérence garantie)
 * - 8 variantes de rose thématiques Love2Love
 * - Texte blanc semi-bold, taille 40% du cercle
 */
@Composable
fun UserInitialsView(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    // Extraction première lettre majuscule
    val firstLetter = name.trim().take(1).uppercase()
    
    // Couleur basée sur hash du nom (cohérence parfaite iOS ↔ Android)
    val backgroundColor = remember(name) {
        val seed = name.hashCode()
        val colors = listOf(
            Color(0xFFFD267A), // Rose principal Love2Love
            Color(0xFFFF69B4), // Rose vif
            Color(0xFFF06292), // Rose clair
            Color(0xFFE91E63), // Rose intense
            Color(0xFFFF1493), // Rose fuchsia
            Color(0xFFDA70D6), // Orchidée rose
            Color(0xFFFF6B9D), // Rose doux
            Color(0xFFE1306C)  // Rose Instagram
        )
        
        val index = abs(seed) % colors.size
        colors[index]
    }
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstLetter,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
