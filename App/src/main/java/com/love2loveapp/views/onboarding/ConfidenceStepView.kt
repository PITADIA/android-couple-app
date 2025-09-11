package com.love2loveapp.views.onboarding

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConfidenceStepScreen(
    selectedAnswer: String,
    onAnswerSelect: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val AccentPink = Color(0xFFFD267A)
    val ScreenBg = Color(0xFFF7F7FA)
    val CardBorder = Color.Black.copy(alpha = 0.10f)

    // Options based on confidence levels
    val confidenceOptions = listOf(
        "Completely", // confidence_completely
        "Most of the time", // confidence_most_of_time
        "Not always" // confidence_not_always
    )

    val canContinue = selectedAnswer.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Espace entre barre de progression et titre (40)
        Spacer(modifier = Modifier.height(40.dp))

        // Titre
        Row(
            modifier = Modifier.padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Would you say you feel completely confident being yourself with your partner?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }

        // Sous-titre
        Row(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "This answer is private and will help us personalize your experience.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black.copy(alpha = 0.6f),
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Options
        Column(
            modifier = Modifier.padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            confidenceOptions.forEach { option ->
                val selected = selectedAnswer == option
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) AccentPink else Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) AccentPink else CardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            Log.d("ConfidenceStepView", "🔥 Option sélectionnée: $option")
                            onAnswerSelect(option)
                        }
                ) {
                    Text(
                        text = option,
                        fontSize = 16.sp,
                        color = if (selected) Color.White else Color.Black,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Zone blanche en bas + bouton Continuer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 30.dp)
        ) {
            Button(
                onClick = { 
                    Log.d("ConfidenceStepView", "🔥 Bouton continuer pressé")
                    onContinue()
                },
                enabled = canContinue,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .alpha(if (canContinue) 1f else 0.5f)
            ) {
                Text(
                    text = "Continue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
