package com.yourapp.ui.paywall

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.R

data class QuestionCategory(
    val title: String,
    val emoji: String
)

@Composable
fun FreemiumPaywallCardView(
    category: QuestionCategory,
    questionsUnlocked: Int,
    totalQuestions: Int,
    onTap: () -> Unit
) {
    // Dégradés et forme (équivalents aux couleurs Swift)
    val containerShape = RoundedCornerShape(20.dp)
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(red = 0.2f, green = 0.1f, blue = 0.15f),
            Color(red = 0.4f, green = 0.2f, blue = 0.3f),
            Color(red = 0.6f, green = 0.3f, blue = 0.2f)
        )
    )
    val borderGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFD267A), Color(0xFFFF655B))
    )
    val ctaGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFD267A), Color(0xFFFF655B))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
            .shadow(elevation = 10.dp, shape = containerShape, clip = false)
            .clip(containerShape)
            .background(bgGradient)
            .border(width = 3.dp, brush = borderGradient, shape = containerShape)
            .clickable(onClick = onTap) // équivalent du Button avec PlainButtonStyle
            .padding(horizontal = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Espace haut (comme Spacer()) ---
            Spacer(modifier = Modifier.height(24.dp))

            // --- Contenu central ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 30.dp)
            ) {
                // Emoji de la catégorie
                Text(
                    text = category.emoji,
                    fontSize = 60.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp),
                    textAlign = TextAlign.Center
                )

                // Titre "congratulations"
                Text(
                    text = stringResource(id = R.string.congratulations),
                    // Équivalent context.getString(R.string.congratulations)
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Sous-titre "keep_going_unlock_all"
                Text(
                    text = stringResource(id = R.string.keep_going_unlock_all),
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                // Bouton d'action (gradient + texte + icône)
                Box(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(ctaGradient)
                        .clickable(onClick = onTap)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            // ⚠️ Si ta clé s’appelle littéralement "continue", utilise l’identifiant backtick:
                            // stringResource(id = R.string.`continue`)
                            text = stringResource(id = R.string.continue_label),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // --- Branding bas ---
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.leetchi2),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FreemiumPaywallCardViewPreview() {
    MaterialTheme {
        FreemiumPaywallCardView(
            category = QuestionCategory(title = "En couple", emoji = "💞"),
            questionsUnlocked = 64,
            totalQuestions = 256,
            onTap = {}
        )
    }
}
