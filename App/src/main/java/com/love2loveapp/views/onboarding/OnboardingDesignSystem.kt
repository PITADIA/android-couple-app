package com.love2loveapp.views.onboarding

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎨 Système de Design Onboarding - CoupleApp
 * Basé sur le rapport RAPPORT_DESIGN_ONBOARDING.md
 */

object OnboardingColors {
    // Couleurs Primaires
    val Primary = Color(0xFFFD267A)         // Rose Principal #FD267A
    val PrimaryVariant = Color(0xFFFF6B9D)  // Rose Secondaire #FF6B9D  
    val Background = Color(0xFFF7F7F9)      // Fond Principal RGB(0.97, 0.97, 0.98)
    val Surface = Color.White               // Zone Boutons
    val OnSurface = Color.Black             // Texte Principal
    val OnSurfaceVariant = Color.Black.copy(alpha = 0.7f)  // Texte Secondaire
    val Outline = Color.Black.copy(alpha = 0.1f)           // Bordures Non-sélectionnées
}

object OnboardingTypography {
    // Titres Principaux
    val TitleLarge = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = OnboardingColors.OnSurface
    )
    
    // Titres Secondaires  
    val TitleMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = OnboardingColors.OnSurface
    )
    
    // Boutons
    val ButtonText = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )
    
    // Texte Options
    val BodyMedium = TextStyle(
        fontSize = 16.sp,
        color = OnboardingColors.OnSurface
    )
    
    // Sous-titres
    val BodySmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = OnboardingColors.OnSurfaceVariant
    )
    
    // Grand Titre Completion
    val TitleXLarge = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        color = OnboardingColors.OnSurface
    )
    
    // Code Partenaire (ajusté pour Android selon feedback utilisateur)
    val CodeDisplay = TextStyle(
        fontSize = 24.sp, // Réduit encore à 24sp pour éviter débordement
        fontWeight = FontWeight.Bold,
        color = OnboardingColors.Primary,
        letterSpacing = 2.sp // Réduit encore l'espacement des lettres
    )
    
    // Texte adaptatif pour boutons (nouveau)
    val ButtonTextAdaptive = TextStyle(
        fontSize = 16.sp, // Légèrement plus petit pour s'adapter aux boutons
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )
}

object OnboardingDimensions {
    // Espacements Standards
    val HorizontalPadding = 30.dp
    val TitleContentSpacing = 40.dp  
    val OptionSpacing = 12.dp
    val ButtonZoneVerticalPadding = 30.dp
    val ButtonHeight = 56.dp
    
    // Rayons de Courbure
    val CornerRadiusCard = 12.dp
    val CornerRadiusButton = 28.dp
    
    // Élévations
    val ShadowElevation = 8.dp
    
    // Autres dimensions
    val ProfilePhotoSize = 160.dp
    val DatePickerHeight = 200.dp
    val ProgressBarWidth = 200.dp
    val ProgressBarScaleY = 2f
}

// Animations
object OnboardingAnimations {
    const val CategoryAnimationInterval = 0.3f
    const val SpringResponse = 0.6f
    const val SpringDamping = 0.8f
}
