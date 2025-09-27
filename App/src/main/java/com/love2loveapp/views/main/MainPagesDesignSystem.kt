package com.love2loveapp.views.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎨 Système de Design des Pages Principales
 * Basé sur RAPPORT_DESIGN_PAGES_PRINCIPALES.md
 */

// === COULEURS SYSTÈME ===
object MainPagesColors {
    // Couleurs principales selon le rapport
    val Primary = Color(0xFFFD267A) // Rose Principal
    val Background = Color(0xFFF7F7F9) // Fond Principal RGB(0.97, 0.97, 0.98)
    val Surface = Color.White
    val OnSurface = Color.Black
    val OnSurfaceVariant = Color.Black.copy(alpha = 0.7f) // Texte Secondaire

    // Dégradés cartes selon le rapport
    val CardHeaderStart = Color(1.0f, 0.4f, 0.6f) // Color(red: 1.0, green: 0.4, blue: 0.6)
    val CardHeaderEnd = Color(1.0f, 0.6f, 0.8f)   // Color(red: 1.0, green: 0.6, blue: 0.8)
    
    val CardBodyStart = Color(0.2f, 0.1f, 0.15f)  // Color(red: 0.2, green: 0.1, blue: 0.15)
    val CardBodyMid = Color(0.4f, 0.2f, 0.3f)     // Color(red: 0.4, green: 0.2, blue: 0.3)
    val CardBodyEnd = Color(0.6f, 0.3f, 0.2f)     // Color(red: 0.6, green: 0.3, blue: 0.2)
}

// === TYPOGRAPHIE PAGES PRINCIPALES ===
object MainPagesTypography {
    // Titres Pages : font(.system(size: 28, weight: .bold))
    val PageTitle = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black
    )
    
    // Titres Cartes : font(.system(size: 22, weight: .medium))
    val CardTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White
    )
    
    // Titres Sections : font(.system(size: 18, weight: .bold))
    val SectionTitle = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black
    )
    
    // Corps Texte : font(.system(size: 16))
    val Body = TextStyle(
        fontSize = 16.sp,
        color = Color.Black
    )
    
    // Sous-titres : font(.system(size: 14))
    val Caption = TextStyle(
        fontSize = 14.sp,
        color = Color.Black.copy(alpha = 0.7f)
    )
    
    // Caption selon le rapport : font(.caption)
    val SystemCaption = TextStyle(
        fontSize = 12.sp,
        color = Color.Gray
    )
}

// === DIMENSIONS PAGES PRINCIPALES ===
object MainPagesDimensions {
    val HorizontalPadding = 20.dp
    val SectionSpacing = 30.dp
    val CardCornerRadius = 20.dp
    val CardShadowElevation = 8.dp
    val ProfilePhotoSize = 120.dp
    val IconSize = 24.dp
    
    // Spécifique aux cartes gradient
    val CardHeaderPadding = 20.dp
    val CardBodyPadding = 30.dp
    val CardMinHeight = 200.dp
}

// === LAYOUT GÉNÉRIQUE PAGES ===
@Composable
fun MainPageLayout(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MainPagesColors.Background)
    ) {
        // Header selon le rapport
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MainPagesDimensions.HorizontalPadding)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Actions à gauche
            actions()
            
            Spacer(modifier = Modifier.weight(1f))

            // Titre centré selon le rapport
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MainPagesTypography.PageTitle
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MainPagesTypography.SystemCaption
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Contenu principal
        content()
    }
}

// === CARTE GRADIENT SELON LE RAPPORT ===
@Composable
fun GradientQuestionCard(
    headerText: String,
    bodyText: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    Card(
        onClick = onTap,
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = MainPagesDimensions.CardShadowElevation,
                shape = RoundedCornerShape(MainPagesDimensions.CardCornerRadius)
            ),
        shape = RoundedCornerShape(MainPagesDimensions.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header avec dégradé selon le rapport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MainPagesColors.CardHeaderStart,
                                MainPagesColors.CardHeaderEnd
                            )
                        )
                    )
                    .padding(vertical = MainPagesDimensions.CardHeaderPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = headerText,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            // Corps avec dégradé vertical selon le rapport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MainPagesDimensions.CardMinHeight)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MainPagesColors.CardBodyStart,
                                MainPagesColors.CardBodyMid,
                                MainPagesColors.CardBodyEnd
                            )
                        )
                    )
                    .padding(horizontal = MainPagesDimensions.CardBodyPadding, vertical = MainPagesDimensions.CardBodyPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(30.dp)
                ) {
                    Spacer(modifier = Modifier.height(1.dp))
                    
                    Text(
                        text = bodyText,
                        style = MainPagesTypography.CardTitle.copy(
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp // lineSpacing(6) selon le rapport
                        )
                    )
                    
                    Spacer(modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}

// === CARTE GRADIENT AVEC BRANDING ===
@Composable
fun GradientCardWithBranding(
    headerText: String,
    bodyText: String,
    showBranding: Boolean = true,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    Card(
        onClick = onTap,
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = MainPagesDimensions.CardShadowElevation,
                shape = RoundedCornerShape(MainPagesDimensions.CardCornerRadius)
            ),
        shape = RoundedCornerShape(MainPagesDimensions.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MainPagesColors.CardHeaderStart,
                                MainPagesColors.CardHeaderEnd
                            )
                        )
                    )
                    .padding(vertical = MainPagesDimensions.CardHeaderPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = headerText,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            // Corps
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MainPagesColors.CardBodyStart,
                                MainPagesColors.CardBodyMid,
                                MainPagesColors.CardBodyEnd
                            )
                        )
                    )
                    .padding(horizontal = MainPagesDimensions.CardBodyPadding)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(30.dp)
                ) {
                    Spacer(modifier = Modifier.height(30.dp))
                    
                    Text(
                        text = bodyText,
                        style = MainPagesTypography.CardTitle.copy(
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Branding en bas selon le rapport
                    if (showBranding) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Logo Love2Love 24x24dp selon le rapport
                            // Image("leetchi2") remplacé par texte pour l'instant
                            Text(
                                text = "💕",
                                fontSize = 24.sp
                            )
                            
                            Text(
                                text = "Love2Love",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}
