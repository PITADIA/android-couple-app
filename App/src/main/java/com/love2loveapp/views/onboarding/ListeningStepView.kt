package com.love2loveapp.views.onboarding

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

@Composable
fun ListeningStepScreen(
    selectedAnswer: String,
    onAnswerSelect: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val Pink = Color(0xFFFD267A)
    val LightBg = Color(0xFFF7F7FA)
    val BorderDefault = Color.Black.copy(alpha = 0.10f)

    // Options based on the listening patterns from strings.xml
    val options = listOf(
        "Most of the time", // listening_most_of_time
        "Sometimes", // listening_sometimes  
        "Rarely" // listening_rarely
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(40.dp))

            // Titre
            Row(
                modifier = Modifier.padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "When a disagreement arises in your relationship, do you feel heard and understood?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(Modifier.weight(1f))
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
                    color = Color.Black.copy(alpha = 0.6f)
                )
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))

            // Options
            Column(
                modifier = Modifier.padding(horizontal = 30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val selected = selectedAnswer == option
                    OptionCard(
                        text = option,
                        selected = selected,
                        onClick = {
                            Log.d("ListeningStepScreen", "🔥 Option sélectionnée: $option (index=$index)")
                            onAnswerSelect(option)
                        }
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            // Espace pour ne pas être masqué par la zone blanche du bas
            Spacer(Modifier.height(120.dp))
        }

        // Zone blanche collée en bas + bouton Continuer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 30.dp)
        ) {
            val enabled = selectedAnswer.isNotEmpty()
            Button(
                onClick = {
                    Log.d("ListeningStepScreen", "🔥 Bouton continuer pressé")
                    onContinue()
                },
                enabled = enabled,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.5f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Pink,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp)
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

@Composable
private fun OptionCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val Pink = Color(0xFFFD267A)
    val BorderDefault = Color.Black.copy(alpha = 0.10f)
    
    val bg = if (selected) Pink else Color.White
    val fg = if (selected) Color.White else Color.Black
    val borderColor = if (selected) Pink else BorderDefault
    val borderWidth = if (selected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp), clip = false)
            .background(bg, RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .padding(vertical = 16.dp, horizontal = 20.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = fg
        )
    }
}
