package com.love2loveapp.views.onboarding

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ComplicityStepScreen(
    selectedAnswer: String,
    onAnswerSelect: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val Pink = Color(0xFFFD267A)
    val Background = Color(0xFFF7F7FA)

    // Options based on complicity levels
    val complicityOptions = listOf(
        "Strong and fulfilling", // complicity_strong_fulfilling
        "Present", // complicity_present
        "Sometimes lacking", // complicity_sometimes_lacking
        "We need help" // complicity_need_help
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Espace entre la (future) barre de progression et le titre
        Spacer(modifier = Modifier.height(40.dp))

        // Titre, aligné à gauche
        Row(
            modifier = Modifier.padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "How would you describe your complicity in your relationship today?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }

        // Sous-titre explicatif
        Row(
            modifier = Modifier
                .padding(horizontal = 30.dp, vertical = 8.dp)
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

        // Options de réponse
        Column(
            modifier = Modifier.padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            complicityOptions.forEach { option ->
                val isSelected = selectedAnswer == option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            Log.d("ComplicityStepView", "Option sélectionnée: $option")
                            onAnswerSelect(option)
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Pink else Color.White,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Pink else Color.Black.copy(alpha = 0.1f)
                    ),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = option,
                        fontSize = 16.sp,
                        color = if (isSelected) Color.White else Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Zone basse blanche avec bouton "Continuer"
        Column(
            modifier = Modifier
                .background(Color.White)
                .padding(vertical = 30.dp)
        ) {
            val canContinue = selectedAnswer.isNotEmpty()

            Button(
                onClick = {
                    Log.d("ComplicityStepView", "Bouton continuer pressé")
                    onContinue()
                },
                enabled = canContinue,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Pink,
                    contentColor = Color.White,
                    disabledContainerColor = Pink.copy(alpha = 0.5f),
                    disabledContentColor = Color.White
                )
            ) {
                Text(
                    text = "Continue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
