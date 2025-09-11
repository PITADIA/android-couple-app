// CategoryListCard.kt
package com.love2loveapp.ui.components

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Modèle côté Android : on référence les libellés via strings.xml
data class QuestionCategory(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val emoji: String,
    val isPremium: Boolean
)

// Fun interface pour matcher l’appel freemiumManager.handleCategoryTap(category) { action() }
fun interface FreemiumManager {
    fun handleCategoryTap(category: QuestionCategory, onAllowed: () -> Unit)
}

@Composable
fun CategoryListCard(
    category: QuestionCategory,
    isSubscribed: Boolean,
    freemiumManager: FreemiumManager? = null,
    onClick: () -> Unit
) {
    val title = stringResource(id = category.titleRes)
    val subtitle = stringResource(id = category.subtitleRes)

    Card(
        onClick = {
            Log.d("CategoryListCard", "🔥 Tap détecté sur $title")
            if (freemiumManager != null) {
                freemiumManager.handleCategoryTap(category) { onClick() }
            } else {
                onClick()
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Titre principal (original)
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                // Sous-titre + cadenas premium si non abonné
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f, fill = true)
                    )
                    if (category.isPremium && !isSubscribed) {
                        Text(text = "🔒", fontSize = 14.sp)
                    }
                }
            }

            // Émoji original à droite
            Text(
                text = category.emoji,
                fontSize = 28.sp
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0514)
@Composable
private fun CategoryListCardPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.1f, green = 0.02f, blue = 0.05f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pour la preview, on réutilise des IDs système juste pour illustrer
        val sample = QuestionCategory(
            titleRes = android.R.string.copy,
            subtitleRes = android.R.string.paste,
            emoji = "💞",
            isPremium = true
        )
        CategoryListCard(
            category = sample,
            isSubscribed = false,
            freemiumManager = FreemiumManager { _, allow -> allow() }
        ) {}

        CategoryListCard(
            category = sample.copy(emoji = "🎯", isPremium = false),
            isSubscribed = true
        ) {}
    }
}
