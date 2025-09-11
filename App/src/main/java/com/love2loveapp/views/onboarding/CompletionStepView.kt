package com.love2loveapp.views.onboarding

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.delay

@Composable
fun CompletionStepScreen(
    selectedGoals: List<String>,
    selectedImprovements: List<String>,
    onContinue: () -> Unit
) {
    // Affichage ponctuel d'un confetti au montage
    var showConfetti by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        Log.d("CompletionStepView", "🔥 CompletionStepView: Vue de completion apparue")
        // L'animation sera visible un court instant
        delay(1800)
        showConfetti = false
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Espace entre "barre de progression" et titre (40)
            Spacer(modifier = Modifier.height(40.dp))

            // Premier Spacer pour centrer le contenu
            Spacer(modifier = Modifier.weight(1f))

            // Contenu principal centré
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Petit titre "Tout est terminé" avec icône
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)), // vert
                        contentAlignment = Alignment.Center
                    ) {
                        // Petit "check" en blanc
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color.White, CircleShape)
                        )
                    }

                    Text(
                        text = "Tout est terminé",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }

                // Grand titre en deux lignes : "Merci de nous faire confiance."
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        text = "Merci de nous faire",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 48.sp
                    )
                    Text(
                        text = "confiance.",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 48.sp
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                Text(
                    text = "Vos données sont sécurisées et ne seront jamais partagées.",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )

                // Récapitulatif des choix
                if (selectedGoals.isNotEmpty() || selectedImprovements.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Vos objectifs :",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black.copy(alpha = 0.8f)
                        )
                        
                        selectedGoals.take(2).forEach { goal ->
                            Text(
                                text = "• $goal",
                                fontSize = 12.sp,
                                color = Color.Black.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        if (selectedGoals.size > 2) {
                            Text(
                                text = "et ${selectedGoals.size - 2} autre(s)...",
                                fontSize = 12.sp,
                                color = Color.Black.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }

            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer(modifier = Modifier.weight(1f))

            // Zone blanche collée en bas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFD267A),
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.extraLarge, // arrondi généreux
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 30.dp)
                ) {
                    Text(
                        text = "Commencer l'aventure Love2Love",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Effet confetti simple (juste un texte animé pour l'instant)
        if (showConfetti) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎉",
                    fontSize = 100.sp,
                    modifier = Modifier.offset(y = (-100).dp)
                )
            }
        }
    }
}
