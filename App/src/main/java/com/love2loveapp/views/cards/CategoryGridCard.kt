package com.love2loveapp.views.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.models.QuestionCategory
import com.love2loveapp.views.collections.CollectionColors
import com.love2loveapp.views.collections.CollectionDimensions
import com.love2loveapp.views.collections.CollectionTypography

/**
 * 🟫 CategoryGridCard selon RAPPORT_DESIGN_COLLECTIONS_CARTES.md
 * Carte de catégorie carrée pour affichage en grille
 * 
 * Design selon le rapport :
 * - Taille fixe 160x200dp selon le rapport
 * - Fond noir avec border blanc 2pt
 * - Corner radius 20pt selon le rapport
 * - Émoji 40pt, titre H5 blanc, sous-titre Small gris
 */
@Composable
fun CategoryGridCard(
    category: QuestionCategory,
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = stringResource(id = category.titleRes)
    val subtitle = stringResource(id = category.subtitleRes)

    Card(
        modifier = modifier.size(
            width = CollectionDimensions.CategoryCardWidth,
            height = CollectionDimensions.CategoryCardHeight
        ),
        shape = RoundedCornerShape(CollectionDimensions.CornerRadiusCardsBlack), // 20dp selon le rapport
        colors = CardDefaults.cardColors(containerColor = Color.Black), // Fond noir selon le rapport
        border = BorderStroke(2.dp, Color.White), // Border blanc 2pt selon le rapport
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = CollectionDimensions.SpaceL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CollectionDimensions.SpaceM) // spacing: 10 selon le rapport
        ) {
            // Émoji selon le rapport (font(.system(size: 40)))
            Text(
                text = category.emoji,
                fontSize = 40.sp
            )

            // Titre principal selon le rapport
            Text(
                text = title,
                style = CollectionTypography.H5.copy(
                    color = Color.White,
                    textAlign = TextAlign.Center
                ),
                maxLines = Int.MAX_VALUE
            )

            Spacer(modifier = Modifier.weight(1f))

            // Sous-titre avec cadenas selon le rapport
            Row(
                horizontalArrangement = Arrangement.spacedBy(CollectionDimensions.SpaceXXS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subtitle,
                    style = CollectionTypography.Small.copy(
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    ),
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Cadenas premium selon le rapport
                if (category.isPremium && !isSubscribed) {
                    Text(
                        text = "🔒",
                        style = CollectionTypography.Small
                    )
                }
            }
        }
    }
}
