package com.love2loveapp.views.cards

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.models.QuestionCategory
import com.love2loveapp.views.collections.CollectionColors
import com.love2loveapp.views.collections.CollectionDimensions
import com.love2loveapp.views.collections.CollectionTypography

/**
 * 🎉 PackCompletionCard selon RAPPORT_DESIGN_COLLECTIONS_CARTES.md
 * Carte de fin de pack avec animation flamme
 * 
 * Design selon le rapport :
 * - Hauteur fixe 500pt selon le rapport
 * - Dégradé rouge/orange selon le rapport (fd267a → ff655b)
 * - Animation de flamme pulsante
 * - Strings XML existantes pour localisation
 */
@Composable
fun PackCompletionCard(
    category: QuestionCategory,
    packNumber: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Animation de la flamme (comme iOS)
    var flameAnimation by remember { mutableStateOf(false) }
    val flameScale by animateFloatAsState(
        targetValue = if (flameAnimation) 1.3f else 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flameScale"
    )
    
    LaunchedEffect(Unit) {
        flameAnimation = true
    }
    
    // Dégradé rouge/orange selon le rapport
    val gradientColors = CollectionColors.PaywallCTAGradient
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(CollectionDimensions.QuestionCardHeight) // Hauteur fixe 500dp selon le rapport
            .clickable { onTap() },
        shape = RoundedCornerShape(CollectionDimensions.CornerRadiusQuestions),
        elevation = CardDefaults.cardElevation(defaultElevation = CollectionDimensions.ElevationMedium),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(colors = gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(30.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                        // Titre principal selon le rapport
                        Text(
                            text = stringResource(R.string.congratulations_pack),
                            style = CollectionTypography.H1.copy(color = Color.White),
                            textAlign = TextAlign.Center
                        )

                        // Sous-titre selon le rapport
                        Text(
                            text = stringResource(R.string.pack_completed),
                            style = CollectionTypography.H6.copy(
                                color = Color.White.copy(alpha = 0.9f)
                            ),
                            textAlign = TextAlign.Center
                        )
                    
                    // Animation flamme (exactement comme iOS)
                    Text(
                        text = "🔥",
                        fontSize = 60.sp,
                        modifier = Modifier.scale(flameScale)
                    )
                    
                        // Instruction tap selon le rapport
                        Text(
                            text = stringResource(R.string.tap_unlock_surprise),
                            style = CollectionTypography.Body.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = CollectionDimensions.SpaceXL)
                        )
                }
            }
        }
    }
}
