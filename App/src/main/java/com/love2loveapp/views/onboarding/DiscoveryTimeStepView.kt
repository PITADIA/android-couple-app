package com.love2loveapp.views.onboarding

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiscoveryTimeStepScreen(
    selectedAnswer: String,
    onAnswerSelect: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val Pink = Color(0xFFFD267A)
    val Background = Color(0xFFF7F7FA)
    
    // Options from strings.xml
    val discoveryOptions = listOf(
        "Yes", // discovery_time_yes
        "No", // discovery_time_no
        "We could do better" // discovery_time_could_do_better
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Espace entre barre de progression et titre
        Spacer(Modifier.height(40.dp))

        // Titre centré à gauche
        Row(
            modifier = Modifier.padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Do you think you take enough time in your relationship to truly discover each other?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(Modifier.weight(1f))
        }

        // Sous-titre explicatif
        Row(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .padding(top = 8.dp)
        ) {
            Text(
                text = "This answer is private and will help us personalize your experience.",
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.6f)
            )
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))

        // Options de réponse
        Column(
            modifier = Modifier.padding(horizontal = 30.dp)
        ) {
            discoveryOptions.forEach { option ->
                val selected = selectedAnswer == option

                val containerColor = if (selected) Pink else Color.White
                val contentColor = if (selected) Color.White else Color.Black
                val borderColor = if (selected) Pink else Color.Black.copy(alpha = 0.1f)
                val borderWidth = if (selected) 2.dp else 1.dp
                val shape = RoundedCornerShape(12.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp) // spacing 12dp total entre cartes
                        .shadow(elevation = 8.dp, shape = shape)
                        .clip(shape)
                        .background(containerColor, shape)
                        .border(width = borderWidth, color = borderColor, shape = shape)
                        .clickable {
                            Log.d("DiscoveryTimeStepView", "🔥 Option sélectionnée: $option")
                            onAnswerSelect(option)
                        }
                ) {
                    Text(
                        text = option,
                        fontSize = 16.sp,
                        color = contentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Zone blanche collée en bas avec bouton "Continuer"
        Column(
            modifier = Modifier
                .background(Color.White)
                .padding(vertical = 30.dp)
        ) {
            val enabled = selectedAnswer.isNotEmpty()

            Button(
                onClick = {
                    Log.d("DiscoveryTimeStepView", "🔥 Bouton continuer pressé")
                    onContinue()
                },
                enabled = enabled,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.5f),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink)
            ) {
                Text(
                    text = "Continue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
