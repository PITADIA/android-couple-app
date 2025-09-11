package com.love2loveapp.views.onboarding

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pink = Color(0xFFFD267A)
private val ScreenBg = Color(0xFFF7F7FA)

@Composable
fun CommunicationEvaluationStepScreen(
    selectedRating: String,
    onRatingSelect: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val options = remember { listOf("1-2", "3-4", "5-6", "7-8", "9-10") }

    Scaffold(
        containerColor = ScreenBg,
        bottomBar = {
            // Zone blanche collée en bas avec bouton "Continuer"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 30.dp)
            ) {
                val enabled = selectedRating.isNotEmpty()
                Button(
                    onClick = {
                        Log.d("CommunicationStep", "Continue pressed with rating: $selectedRating")
                        onContinue()
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Pink,
                        disabledContainerColor = Pink.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(horizontal = 30.dp)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Continue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(40.dp))

            // Titre
            Text(
                text = "On a scale of 1 to 10, how would you rate the communication in your relationship?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 30.dp)
            )

            // Sous-titre
            Text(
                text = "This answer is private and will help us personalize your experience.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .padding(top = 8.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Options
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 30.dp)
            ) {
                options.forEach { option ->
                    val isSelected = selectedRating == option
                    val shape = RoundedCornerShape(12.dp)

                    Surface(
                        shape = shape,
                        color = if (isSelected) Pink else Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Pink else Color.Black.copy(alpha = 0.1f),
                                shape = shape
                            )
                            .clickable { 
                                Log.d("CommunicationStep", "Option selected: $option")
                                onRatingSelect(option)
                            }
                    ) {
                        Text(
                            text = option,
                            fontSize = 16.sp,
                            color = if (isSelected) Color.White else Color.Black,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
