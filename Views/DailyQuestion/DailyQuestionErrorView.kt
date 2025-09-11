// DailyQuestionErrorScreen.kt
package com.yourapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.yourapp.R

@Composable
fun DailyQuestionErrorScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Couleurs fidèles au design SwiftUI
    val background = Color(0xFFF7F7FA)      // ≈ Color(red: 0.97, green: 0.97, blue: 0.98)
    val brandPink = Color(0xFFFD267A)       // #FD267A
    val warningOrange = Color(0xFFFFA500)   // Orange

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
            Spacer(Modifier.weight(1f))

            // Icône d'erreur
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = warningOrange,
                modifier = Modifier.size(60.dp)
            )

            Spacer(Modifier.height(30.dp))

            // Titre
            Text(
                text = stringResource(id = R.string.daily_question_error_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Message d'erreur
            Text(
                text = message,
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 0.dp)
            )

            Spacer(Modifier.weight(1f))

            // Bouton Réessayer
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 40.dp),
                shape = MaterialTheme.shapes.large, // arrondi important
                colors = ButtonDefaults.buttonColors(containerColor = brandPink)
            ) {
                Text(
                    text = stringResource(id = R.string.retry),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyQuestionErrorScreenPreview() {
    DailyQuestionErrorScreen(
        message = "Impossible de charger les questions. Vérifiez votre connexion internet.",
        onRetry = {}
    )
}
