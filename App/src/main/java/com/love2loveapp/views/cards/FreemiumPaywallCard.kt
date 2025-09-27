package com.love2loveapp.views.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 💰 FreemiumPaywallCard selon RAPPORT_DESIGN_COLLECTIONS_CARTES.md
 * Carte paywall pour utilisateurs gratuits
 * 
 * Design selon le rapport :
 * - Hauteur fixe 500pt selon le rapport
 * - Dégradé selon catégorie mais plus intense
 * - Strings XML existantes pour localisation
 */
@Composable
fun FreemiumPaywallCard(
    category: QuestionCategory,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Dégradé selon catégorie (comme les cartes normales mais plus intense)
    val gradientColors = when (category.id) {
        "en-couple" -> listOf(Color(0xFFC2185B), Color(0xFFE91E63))     // Rose intense
        "les-plus-hots" -> listOf(Color(0xFFD84315), Color(0xFFFF6B35)) // Rouge intense
        "a-distance" -> listOf(Color(0xFF1565C0), Color(0xFF2196F3))    // Bleu intense
        else -> listOf(Color(0xFF1565C0), Color(0xFF2196F3))           // Bleu par défaut
    }
    
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
                    // Émoji de la catégorie (plus grand)
                    Text(
                        text = category.emoji,
                        fontSize = 60.sp
                    )
                    
                            // Titre félicitations selon le rapport
                            Text(
                                text = stringResource(R.string.congratulations),
                                style = CollectionTypography.H2.copy(color = Color.White),
                                textAlign = TextAlign.Center
                            )

                            // Message d'encouragement selon le rapport
                            Text(
                                text = stringResource(R.string.keep_going_unlock_all),
                                style = CollectionTypography.H6.copy(
                                    color = Color.White.copy(alpha = 0.9f)
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = CollectionDimensions.SpaceXL)
                            )

                            // Bouton continuer avec flèche selon le rapport
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(CollectionDimensions.SpaceS),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = CollectionDimensions.SpaceM)
                            ) {
                                Text(
                                    text = stringResource(R.string.continue_button),
                                    style = CollectionTypography.H4.copy(color = Color.White)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                }
            }
        }
    }
}
