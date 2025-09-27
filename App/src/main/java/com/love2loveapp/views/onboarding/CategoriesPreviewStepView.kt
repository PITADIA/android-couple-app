package com.love2loveapp.views.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import kotlinx.coroutines.delay

// Data class pour les catégories selon le rapport iOS
data class QuestionCategory(
    val id: String,
    val emoji: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val isPremium: Boolean = true
) {
    companion object {
        // 8 Collections selon le rapport iOS avec les mêmes emojis et ordre
        val categories = listOf(
            // 💞 Toi et Moi (Gratuit)
            QuestionCategory(
                id = "en-couple", 
                emoji = "💞", 
                titleRes = R.string.category_en_couple_title,
                subtitleRes = R.string.category_en_couple_subtitle,
                isPremium = false
            ),
            // 🌶️ Désirs Inavoués (Premium)
            QuestionCategory(
                id = "les-plus-hots", 
                emoji = "🌶️", 
                titleRes = R.string.category_desirs_inavoues_title,
                subtitleRes = R.string.category_desirs_inavoues_subtitle
            ),
            // ✈️ À Distance (Premium)
            QuestionCategory(
                id = "a-distance", 
                emoji = "✈️", 
                titleRes = R.string.category_a_distance_title,
                subtitleRes = R.string.category_a_distance_subtitle
            ),
            // ✨ Questions Profondes (Premium)
            QuestionCategory(
                id = "questions-profondes", 
                emoji = "✨", 
                titleRes = R.string.category_questions_profondes_title,
                subtitleRes = R.string.category_questions_profondes_subtitle
            ),
            // 😂 Pour Rire (Premium)
            QuestionCategory(
                id = "pour-rire-a-deux", 
                emoji = "😂", 
                titleRes = R.string.category_pour_rire_title,
                subtitleRes = R.string.category_pour_rire_subtitle
            ),
            // 🤍 Tu Préfères (Premium)
            QuestionCategory(
                id = "tu-preferes", 
                emoji = "🤍", 
                titleRes = R.string.category_tu_preferes_title,
                subtitleRes = R.string.category_tu_preferes_subtitle
            ),
            // 💌 Mieux Ensemble (Premium)
            QuestionCategory(
                id = "mieux-ensemble", 
                emoji = "💌", 
                titleRes = R.string.category_mieux_ensemble_title,
                subtitleRes = R.string.category_mieux_ensemble_subtitle
            ),
            // 🍸 Pour un Date (Premium)
            QuestionCategory(
                id = "pour-un-date", 
                emoji = "🍸", 
                titleRes = R.string.category_pour_un_date_title,
                subtitleRes = R.string.category_pour_un_date_subtitle
            )
        )
    }
}

@Composable
fun CategoriesPreviewStepScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    // État d'animation en cascade selon le rapport iOS
    var visibleCategories by remember { mutableStateOf(setOf<String>()) }
    var currentCategoryIndex by remember { mutableStateOf(0) }
    
    // Animation en cascade selon le rapport iOS : délai initial de 0.3s puis 0.3s entre chaque carte
    LaunchedEffect(Unit) {
        delay(300) // Délai initial selon le rapport
        animateNextCategory(
            categories = QuestionCategory.categories,
            currentIndex = currentCategoryIndex,
            visibleCategories = visibleCategories,
            onUpdateIndex = { currentCategoryIndex = it },
            onUpdateVisible = { visibleCategories = it }
        )
    }

    // Layout principal selon le rapport iOS
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f)), // #F7F7F8 selon le rapport
        horizontalAlignment = Alignment.Start
    ) {
        // Espace haut (40pt selon le rapport)
        Spacer(modifier = Modifier.height(40.dp))
        
        // Titre principal selon le rapport iOS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp) // padding horizontal selon le rapport
        ) {
            Text(
                text = stringResource(R.string.more_than_2000_questions), // Clé du rapport iOS
                fontSize = 28.sp, // font(.system(size: 28, weight: .bold)) selon le rapport
                fontWeight = FontWeight.Bold,
                color = Color.Black, // .foregroundColor(.black) selon le rapport
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Espace titre-cartes (60pt selon le rapport)
        Spacer(modifier = Modifier.height(60.dp))
        
        // ScrollView avec cartes animées selon le rapport iOS
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp), // padding selon le rapport
            verticalArrangement = Arrangement.spacedBy(12.dp) // spacing: 12 selon le rapport
        ) {
            items(QuestionCategory.categories) { category ->
                CategoryPreviewCard(
                    category = category,
                    isVisible = visibleCategories.contains(category.id)
                )
            }
        }
        
        // Zone bouton (fixe en bas) selon le rapport iOS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White) // .background(Color.white) selon le rapport
                .shadow(
                    elevation = 10.dp, // shadow(radius: 10, x: 0, y: -5) selon le rapport
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
                .padding(vertical = 30.dp, horizontal = 30.dp), // padding selon le rapport
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A), // Rose principal (#FD267A) selon le rapport
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.continue_button),
                    fontSize = 18.sp, // font(.system(size: 18, weight: .semibold)) selon le rapport
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Animation en cascade selon le rapport iOS
private suspend fun animateNextCategory(
    categories: List<QuestionCategory>,
    currentIndex: Int,
    visibleCategories: Set<String>,
    onUpdateIndex: (Int) -> Unit,
    onUpdateVisible: (Set<String>) -> Unit
) {
    if (currentIndex < categories.size) {
        val category = categories[currentIndex]
        onUpdateVisible(visibleCategories + category.id)
        onUpdateIndex(currentIndex + 1)
        
        // Programmer prochaine animation avec délai de 0.3s selon le rapport
        delay(300)
        animateNextCategory(
            categories = categories,
            currentIndex = currentIndex + 1,
            visibleCategories = visibleCategories + category.id,
            onUpdateIndex = onUpdateIndex,
            onUpdateVisible = onUpdateVisible
        )
    }
}

@Composable
private fun CategoryPreviewCard(
    category: QuestionCategory,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    // Animation spring selon le rapport iOS
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f, // dampingFraction: 0.8 selon le rapport
            stiffness = Spring.StiffnessMediumLow // response: 0.6 selon le rapport
        ), label = "alpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = 0.8f, // dampingFraction: 0.8 selon le rapport
            stiffness = Spring.StiffnessMediumLow // response: 0.6 selon le rapport
        ), label = "scale"
    )

    // Design de carte selon le rapport iOS
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .scale(animatedScale),
        shape = RoundedCornerShape(16.dp), // cornerRadius: 16 selon le rapport
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f) // Color.white.opacity(0.95) selon le rapport
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp // shadow(radius: 8, x: 0, y: 2) selon le rapport
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp), // padding selon le rapport
            horizontalArrangement = Arrangement.spacedBy(16.dp), // spacing: 16 selon le rapport
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contenu texte (gauche) selon le rapport iOS
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp) // spacing: 6 selon le rapport
            ) {
                // Titre principal selon le rapport
                Text(
                    text = stringResource(category.titleRes),
                    fontSize = 20.sp, // font(.system(size: 20, weight: .bold)) selon le rapport
                    fontWeight = FontWeight.Bold,
                    color = Color.Black // .foregroundColor(.black) selon le rapport
                )

                // Sous-titre descriptif selon le rapport
                Text(
                    text = stringResource(category.subtitleRes),
                    fontSize = 14.sp, // font(.system(size: 14)) selon le rapport
                    color = Color.Gray // .foregroundColor(.gray) selon le rapport
                )
            }

            // Emoji (droite) selon le rapport iOS
            Text(
                text = category.emoji,
                fontSize = 28.sp // font(.system(size: 28)) selon le rapport
            )
        }
    }
}