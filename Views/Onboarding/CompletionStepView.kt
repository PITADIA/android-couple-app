// CompletionStepView.kt
package com.love2love.ui.onboarding

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay

// Lottie (optionnel) si tu veux un effet confetti rapide
import com.airbnb.lottie.compose.*

import com.love2love.R

/**
 * Équivalent Compose de CompletionStepView.
 *
 * Remplace "viewModel.nextStep()" par un callback [onContinue] pour ne pas lier ce composable
 * à une implémentation particulière du ViewModel.
 */
@Composable
fun CompletionStepView(
    onContinue: () -> Unit
) {
    // Déclencheur "onAppear"
    LaunchedEffect(Unit) {
        Log.d("CompletionStepView", "🔥 CompletionStepView: Vue de completion apparue")
    }

    // Affichage ponctuel d'un confetti (Lottie) au montage, pour mimer confettiCounter += 1
    var showConfetti by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        // L'animation sera visible un court instant
        delay(1800)
        showConfetti = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                        // Petit "check" en blanc - on évite l'Icon pack pour rester autonome
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color.White, CircleShape)
                        )
                    }

                    Text(
                        text = stringResource(id = R.string.all_completed),
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
                        text = stringResource(id = R.string.thank_you_for),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 48.sp
                    )
                    Text(
                        text = stringResource(id = R.string.trusting_us),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 48.sp
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                Text(
                    text = stringResource(id = R.string.privacy_promise),
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
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
                        text = stringResource(id = R.string.continue_label),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Overlay confetti (Lottie) optionnel
        if (showConfetti) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.confetti) // place confetti.json dans res/raw/confetti.json
            )
            // Une rafale unique
            LottieAnimation(
                composition = composition,
                iterations = 1,
                modifier = Modifier
                    .fillMaxSize()
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun CompletionStepViewPreview() {
    CompletionStepView(
        onContinue = {}
    )
}
