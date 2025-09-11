package com.yourapp.ui.dailychallenge

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yourapp.R

/**
 * Écran de chargement "Défis quotidiens" – version Jetpack Compose
 * - Fond gris clair (≈ 0.97, 0.97, 0.98)
 * - Cercle de fond gris 30% d'opacité
 * - Arc rose (#FD267A) sur 75% avec rotation continue
 * - Titres localisés via strings.xml
 */
@Composable
fun DailyChallengeLoadingScreen(
    modifier: Modifier = Modifier
) {
    // Couleurs
    val background = Color(0xFFF7F7FA)   // ≈ (0.97, 0.97, 0.98)
    val track = Color.Black.copy(alpha = 0.30f)
    val accent = Color(0xFFFD267A)

    // Animation de rotation continue (0 -> 360 en 1s)
    val infiniteTransition = rememberInfiniteTransition(label = "loader-rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Loader
            Box(
                modifier = Modifier.size(60.dp),
                contentAlignment = Alignment.Center
            ) {
                // Cercle de fond
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = track,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                // Arc de 270° (0.75) en rotation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotation }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = accent,
                            startAngle = 0f,
                            sweepAngle = 270f, // 0.75 de 360°
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Titre
            Text(
                text = stringResource(id = R.string.daily_challenge_loading_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Sous-titre
            Text(
                text = stringResource(id = R.string.daily_challenge_loading_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyChallengeLoadingScreenPreview() {
    MaterialTheme {
        DailyChallengeLoadingScreen()
    }
}
