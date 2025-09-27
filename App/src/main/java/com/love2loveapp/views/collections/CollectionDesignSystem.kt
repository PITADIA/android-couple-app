package com.love2loveapp.views.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎨 CollectionDesignSystem selon RAPPORT_DESIGN_COLLECTIONS_CARTES.md
 * Système de design unifié pour toutes les cartes et collections
 * 
 * Architecture complète :
 * - CollectionColors : Couleurs et gradients par catégorie
 * - CollectionTypography : 9 niveaux hiérarchiques H1 à Small
 * - CollectionDimensions : Spacing, corner radius, elevations
 * - Composables helper : MainPageLayout, cartes prêtes à utiliser
 */

// === COULEURS ET GRADIENTS SELON LE RAPPORT ===
object CollectionColors {
    // Couleurs principales selon le rapport
    val Background = Color(0xFFF7F7F8) // rgb(0.97, 0.97, 0.98)
    val QuestionsBackground = Color(0xFF260508) // rgb(0.15, 0.03, 0.08)
    val PrimaryRose = Color(0xFFFD267A)
    val SecondaryOrange = Color(0xFFFF655B)

    // Gradients par catégorie selon le rapport
    val EnCoupleGradient = listOf(Color(0xFFE91E63), Color(0xFFF06292)) // En couple
    val DesirsGradient = listOf(Color(0xFFFF6B35), Color(0xFFF7931E)) // Désirs inavoués
    val DistanceGradient = listOf(Color(0xFF00BCD4), Color(0xFF26C6DA)) // À distance
    val ProfondGradient = listOf(Color(0xFFFFD700), Color(0xFFFFA500)) // Questions profondes
    val RireGradient = listOf(Color(0xFFFFD700), Color(0xFFFFA500)) // Pour rire à deux
    val PreferesGradient = listOf(Color(0xFF9B59B6), Color(0xFF8E44AD)) // Tu préfères
    val EnsembleGradient = listOf(Color(0xFF673AB7), Color(0xFF9C27B0)) // Mieux ensemble
    val DateGradient = listOf(Color(0xFF3498DB), Color(0xFF2980B9)) // Pour un date

    // Question card gradients universels selon le rapport
    val QuestionHeaderGradient = listOf(
        Color(0xFFFF6699), // rgb(1.0, 0.4, 0.6) - Rose foncé
        Color(0xFFFF99CC)  // rgb(1.0, 0.6, 0.8) - Rose clair
    )
    
    val QuestionBodyGradient = listOf(
        Color(0xFF331A26), // rgb(0.2, 0.1, 0.15) - Brun sombre
        Color(0xFF66334D), // rgb(0.4, 0.2, 0.3) - Brun moyen
        Color(0xFF994D33)  // rgb(0.6, 0.3, 0.2) - Brun clair
    )

    // Paywall gradient selon le rapport
    val PaywallCTAGradient = listOf(Color(0xFFFD267A), Color(0xFFFF655B))
    val PaywallBorderGradient = listOf(Color(0xFFFD267A), Color(0xFFFF655B))
    
    // Couleurs texte standard
    val OnSurface = Color.Black
    val OnSurfaceVariant = Color.Black.copy(alpha = 0.7f)
    val Surface = Color.White
    val SurfaceVariant = Color.Gray
}

// === TYPOGRAPHIE HIÉRARCHIQUE SELON LE RAPPORT ===
object CollectionTypography {
    // 9 niveaux hiérarchiques selon le rapport
    val H1 = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold) // Titres principaux cartes
    val H2 = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold) // Paywall titre
    val H3 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium) // Texte questions
    val H4 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold) // Titres cartes liste
    val H5 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold) // Titres cartes noires
    val H6 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal) // Sous-titres paywall
    val Body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium) // Instructions
    val Caption = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal) // Sous-titres
    val Small = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal) // Détails
}

// === DIMENSIONS ET SPACING SELON LE RAPPORT ===
object CollectionDimensions {
    // Spacing system selon le rapport
    val SpaceXXS = 4.dp   // Éléments très proches
    val SpaceXS = 6.dp    // Line spacing
    val SpaceS = 8.dp     // Espacement cartes
    val SpaceM = 10.dp    // Espacement VStack cartes
    val SpaceL = 16.dp    // Espacement général
    val SpaceXL = 20.dp   // Padding vertical
    val SpaceXXL = 24.dp  // Padding horizontal
    val SpaceXXXL = 30.dp // Espacement cartes questions

    // Corner radius standards selon le rapport
    val CornerRadiusCards = 16.dp        // CategoryListCardView
    val CornerRadiusCardsBlack = 20.dp   // CategoryCardView
    val CornerRadiusQuestions = 20.dp    // QuestionCardView, PaywallCard
    val CornerRadiusCTA = 25.dp          // Boutons paywall
    val CornerRadiusFavorites = 28.dp    // Bouton ajouter favoris

    // Elevations selon le rapport
    val ElevationLight = 8.dp      // Cards liste
    val ElevationMedium = 10.dp    // Cards actives
    val ElevationBackground = 5.dp // Cards arrière-plan

    // Sizes spécifiques selon le rapport
    val CategoryCardWidth = 160.dp
    val CategoryCardHeight = 200.dp
    val QuestionCardHeight = 500.dp // Hauteur fixe selon le rapport
    val LogoSize = 24.dp
}

// === HELPER FUNCTIONS POUR GRADIENTS ===
object CollectionGradients {
    
    /**
     * Obtient le gradient pour une catégorie donnée
     */
    fun getCategoryGradient(categoryId: String): List<Color> {
        return when (categoryId) {
            "en-couple" -> CollectionColors.EnCoupleGradient
            "les-plus-hots" -> CollectionColors.DesirsGradient
            "a-distance" -> CollectionColors.DistanceGradient
            "questions-profondes" -> CollectionColors.ProfondGradient
            "pour-rire-a-deux" -> CollectionColors.RireGradient
            "tu-preferes" -> CollectionColors.PreferesGradient
            "mieux-ensemble" -> CollectionColors.EnsembleGradient
            "pour-un-date" -> CollectionColors.DateGradient
            else -> CollectionColors.EnCoupleGradient // Par défaut
        }
    }
    
    /**
     * Crée un Brush vertical gradient pour une catégorie
     */
    @Composable
    fun getCategoryBrush(categoryId: String): Brush {
        val colors = getCategoryGradient(categoryId)
        return Brush.verticalGradient(colors = colors)
    }
    
    /**
     * Crée le gradient header universel pour questions selon le rapport
     */
    @Composable
    fun getQuestionHeaderBrush(): Brush {
        return Brush.horizontalGradient(colors = CollectionColors.QuestionHeaderGradient)
    }
    
    /**
     * Crée le gradient body universel pour questions selon le rapport
     */
    @Composable
    fun getQuestionBodyBrush(): Brush {
        return Brush.verticalGradient(colors = CollectionColors.QuestionBodyGradient)
    }
    
    /**
     * Crée le gradient CTA pour paywall selon le rapport
     */
    @Composable
    fun getPaywallCTABrush(): Brush {
        return Brush.horizontalGradient(colors = CollectionColors.PaywallCTAGradient)
    }
    
    /**
     * Crée le gradient border diagonal pour paywall selon le rapport
     */
    @Composable
    fun getPaywallBorderBrush(): Brush {
        return Brush.linearGradient(colors = CollectionColors.PaywallBorderGradient)
    }
}

// === COMPOSABLES HELPER SELON LE RAPPORT ===

/**
 * MainPageLayout - Layout principal pour pages collections
 * Fond gris clair avec dégradé rose en haut selon le rapport
 */
@Composable
fun CollectionPageLayout(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CollectionColors.Background)
    ) {
        // Dégradé rose en haut (350dp) selon le rapport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            CollectionColors.PrimaryRose.copy(alpha = 0.3f),
                            CollectionColors.PrimaryRose.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 30.dp),
            content = content
        )
    }
}

/**
 * Branding Love2Love avec logo selon le rapport
 * Logo leetchi2 24x24pt + texte Love2Love
 */
@Composable
fun Love2LoveBranding(
    modifier: Modifier = Modifier,
    textColor: Color = Color.White.copy(alpha = 0.9f)
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // TODO: Remplacer par vraie image quand disponible
        Box(
            modifier = Modifier
                .size(CollectionDimensions.LogoSize)
                .background(
                    Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "💕",
                fontSize = 16.sp
            )
        }

        Text(
            text = "Love2Love",
            style = CollectionTypography.Body.copy(
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        )
    }
}

/**
 * Shadow helper selon les standards du rapport
 */
@Composable
fun CollectionCard(
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = CollectionDimensions.ElevationMedium,
    cornerRadius: androidx.compose.ui.unit.Dp = CollectionDimensions.CornerRadiusQuestions,
    backgroundColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }
    
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}
