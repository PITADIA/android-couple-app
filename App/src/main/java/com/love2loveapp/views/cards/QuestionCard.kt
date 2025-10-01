package com.love2loveapp.views.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.love2loveapp.services.Question
import com.love2loveapp.views.collections.CollectionDimensions
import com.love2loveapp.views.collections.CollectionGradients
import com.love2loveapp.views.collections.CollectionTypography
import com.love2loveapp.views.collections.Love2LoveBranding

/**
 * 🃏 QuestionCard selon RAPPORT_DESIGN_COLLECTIONS_CARTES.md
 * Carte de questions avec design exact du rapport iOS
 * 
 * Architecture selon le rapport :
 * - Header gradient rose universel (horizontal)
 * - Body gradient sombre universel (vertical 3 couleurs)
 * - Hauteur fixe 500pt selon le rapport
 * - Branding Love2Love avec logo leetchi2 24x24pt
 * - Titre catégorie dans header, question dans body
 */
@Composable
fun QuestionCard(
    question: Question,
    category: QuestionCategory,
    questionNumber: Int = 1,
    totalQuestions: Int = 1,
    isFavorite: Boolean = false,
    onFavoriteClick: ((Question, QuestionCategory) -> Unit)? = null,
    isBackground: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Obtenir le texte de la question
    val questionText = remember(question.id) {
        question.getText(context)
    }
    
    // Utiliser un Box au lieu d'une Card pour permettre l'affichage correct des gradients
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CollectionDimensions.QuestionCardHeight) // Hauteur fixe 500dp selon le rapport
            .clip(RoundedCornerShape(CollectionDimensions.CornerRadiusQuestions))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header avec nom de catégorie selon le rapport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CollectionGradients.getQuestionHeaderBrush())
                    .padding(vertical = CollectionDimensions.SpaceXL),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(category.titleRes),
                    style = CollectionTypography.H5.copy(color = Color.White),
                    textAlign = TextAlign.Center
                )
            }

            // Corps avec gradient sombre selon le rapport
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CollectionGradients.getQuestionBodyBrush())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CollectionDimensions.SpaceXXXL),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    // Texte de la question selon le rapport
                    Text(
                        text = questionText,
                        style = CollectionTypography.H3.copy(
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        ),
                        modifier = Modifier.padding(horizontal = CollectionDimensions.SpaceXXXL)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 💝 Bouton Favoris ou Branding selon le rapport
                    if (onFavoriteClick != null) {
                        FavoriteButton(
                            isFavorite = isFavorite,
                            onClick = { onFavoriteClick(question, category) }
                        )
                    } else {
                        // Branding Love2Love selon le rapport
                        Love2LoveBranding(
                            modifier = Modifier.padding(bottom = CollectionDimensions.SpaceXXXL)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 💝 FavoriteButton selon le rapport - Bouton favoris avec corner radius 28pt
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.2f),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(CollectionDimensions.CornerRadiusFavorites), // 28dp selon le rapport
        contentPadding = PaddingValues(
            horizontal = CollectionDimensions.SpaceL, 
            vertical = CollectionDimensions.SpaceS
        ),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CollectionDimensions.SpaceS)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) 
                    stringResource(R.string.remove_from_favorites) else 
                    stringResource(R.string.add_to_favorites),
                tint = Color.White, // ✅ Toujours blanc pour une meilleure visibilité
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = if (isFavorite) 
                    stringResource(R.string.remove_from_favorites) else 
                    stringResource(R.string.add_to_favorites),
                style = CollectionTypography.Caption.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            )
        }
    }
}
