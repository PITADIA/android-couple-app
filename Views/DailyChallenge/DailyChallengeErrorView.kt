package com.love2love.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

// Palette (équivalents des couleurs Swift)
private val LightAppBg = Color(0xFFF7F7FA)   // ≈ (0.97, 0.97, 0.98)
private val BrandPink  = Color(0xFFFD267A)   // "#FD267A"
private val WarningOrange = Color(0xFFFF9800)

// -- API 1 : message déjà prêt (String dynamique) --
@Composable
fun DailyChallengeErrorScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    DailyChallengeErrorContent(
        messageText = message,
        onRetry = onRetry,
        modifier = modifier
    )
}

// -- API 2 : message depuis strings.xml --
@Composable
fun DailyChallengeErrorScreen(
    @StringRes messageResId: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    DailyChallengeErrorContent(
        messageText = stringResource(id = messageResId),
        onRetry = onRetry,
        modifier = modifier
    )
}

@Composable
private fun DailyChallengeErrorContent(
    messageText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightAppBg)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // Icône d'erreur
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = stringResource(R.string.error_icon_cd),
                tint = WarningOrange,
                modifier = Modifier.size(60.dp)
            )

            // Titre
            Text(
                text = stringResource(R.string.oops_error_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
            )

            // Message d'erreur (dynamique)
            Text(
                text = messageText,
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
            )

            Spacer(Modifier.weight(1f))

            // Bouton "Réessayer"
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPink),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 40.dp)
            ) {
                Text(
                    text = stringResource(R.string.retry),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F7FA)
@Composable
private fun DailyChallengeErrorPreview() {
    MaterialTheme {
        DailyChallengeErrorScreen(
            message = "Impossible de charger les défis. Vérifie ta connexion internet.",
            onRetry = {}
        )
    }
}
