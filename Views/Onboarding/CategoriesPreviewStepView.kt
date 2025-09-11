package com.love2love.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Recode de `CategoriesPreviewStepView` en Jetpack Compose.
 *
 * 🔤 Localisation:
 *  - Remplace les appels `.localized("...")` par `stringResource(R.string.your_key)`
 *    ou l'équivalent `LocalContext.current.getString(resId)`.
 *  - ⚠️ Évite d'utiliser le nom de res `continue` (mot-clé Kotlin). Préfère `action_continue`.
 *  - Si tes catégories stockent des *clés* (ex: "romance_title") plutôt que des chaînes déjà traduites,
 *    utilise le helper [str] ci-dessous pour résoudre dynamiquement les clés `strings.xml`.
 */

interface OnboardingViewModel {
    fun nextStep()
}

data class QuestionCategory(
    val id: String,
    val title: String,    // chaîne déjà localisée OU clé strings.xml
    val subtitle: String, // chaîne déjà localisée OU clé strings.xml
    val emoji: String
)

@Composable
private fun str(key: String): String {
    val ctx = LocalContext.current
    // Résolution dynamique d'une clé de strings.xml -> resId
    val resId = remember(key) { ctx.resources.getIdentifier(key, "string", ctx.packageName) }
    return if (resId != 0) ctx.getString(resId) else key
}

@Composable
fun CategoriesPreviewStepScreen(
    viewModel: OnboardingViewModel,
    categories: List<QuestionCategory>,
    modifier: Modifier = Modifier
) {
    val animationIntervalMs = 300L
    val visibleIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(categories) {
        // Petit délai initial pour mimer `.asyncAfter(0.3)`
        kotlinx.coroutines.delay(300)
        for (cat in categories) {
            visibleIds += cat.id
            kotlinx.coroutines.delay(animationIntervalMs)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Espace entre barre de progression et titre (40)
        Spacer(Modifier.height(40.dp))

        // Titre aligné à gauche
        Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = androidx.compose.ui.res.stringResource(id = com.love2love.R.string.more_than_2000_questions),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
        }

        // Espace entre titre et cartes (60)
        Spacer(Modifier.height(60.dp))

        // Liste des catégories avec animation
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                AnimatedVisibility(
                    visible = visibleIds.contains(category.id),
                    enter = fadeIn() + scaleIn(
                        initialScale = 0.8f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                ) {
                    CategoryPreviewCard(category = category)
                }
            }
        }

        // Zone blanche collée en bas avec ombre + bouton Continuer
        Surface(
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 30.dp)) {
                Button(
                    onClick = { viewModel.nextStep() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 30.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFD267A))
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(id = com.love2love.R.string.action_continue),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryPreviewCard(category: QuestionCategory, useKeys: Boolean = false) {
    val titleText = if (useKeys) str(category.title) else category.title
    val subtitleText = if (useKeys) str(category.subtitle) else category.subtitle

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitleText,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Text(
                text = category.emoji,
                fontSize = 28.sp
            )
        }
    }
}

@Preview(showBackground = true)
private fun CategoriesPreviewStepScreenPreview() {
    val vm = object : OnboardingViewModel { override fun nextStep() {} }
    val sample = listOf(
        QuestionCategory("romance", "Romance", "Questions pour rapprocher", "❤️"),
        QuestionCategory("values", "Valeurs", "Parler de l'essentiel", "✨"),
        QuestionCategory("future", "Futur", "Se projeter à deux", "🔮")
    )
    MaterialTheme {
        CategoriesPreviewStepScreen(
            viewModel = vm,
            categories = sample,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 0.dp)
        )
    }
}
