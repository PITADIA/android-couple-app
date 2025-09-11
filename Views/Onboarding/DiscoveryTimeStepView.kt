// DiscoveryTimeStepView.kt
package com.yourapp.onboarding

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Contrat minimal du ViewModel attendu.
 * Remplace par ton propre ViewModel si tu en as déjà un.
 */
interface OnboardingViewModel {
    var discoveryTimeAnswer: String
    fun nextStep()
}

/**
 * Vue Compose portée de DiscoveryTimeStepView (SwiftUI).
 */
@Composable
fun DiscoveryTimeStepView(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val Pink = Color(0xFFFD267A)
    val Background = Color(0xFFF7F7FA)

    // On utilise des IDs de ressources pour l’affichage,
    // et on stocke la clé (nom de res) dans le viewModel pour rester aligné avec iOS.
    val discoveryOptionIds = listOf(
        R.string.discovery_time_yes,
        R.string.discovery_time_no,
        R.string.discovery_time_could_do_better
    )

    var selectedKey by rememberSaveable { mutableStateOf(viewModel.discoveryTimeAnswer) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Espace entre barre de progression et titre
        Spacer(Modifier.height(40.dp))

        // Titre centré à gauche
        Row(
            modifier = Modifier
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.discovery_time_question),
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
                text = stringResource(R.string.private_answer_note),
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.6f)
            )
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))

        // Options de réponse
        Column(
            modifier = Modifier
                .padding(horizontal = 30.dp)
        ) {
            discoveryOptionIds.forEach { resId ->
                val key = remember(resId) {
                    // "discovery_time_yes", etc.
                    context.resources.getResourceEntryName(resId)
                }
                val selected = selectedKey == key

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
                            Log.d("DiscoveryTimeStepView", "🔥 Option sélectionnée: $key")
                            selectedKey = key
                            viewModel.discoveryTimeAnswer = key
                        }
                ) {
                    Text(
                        text = stringResource(resId),
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
            val enabled = selectedKey.isNotEmpty()

            Button(
                onClick = {
                    Log.d("DiscoveryTimeStepView", "🔥 Bouton continuer pressé")
                    viewModel.nextStep()
                },
                enabled = enabled,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    // Pour coller à l’opacité 0.5 quand disabled (comme iOS)
                    .alpha(if (enabled) 1f else 0.5f),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink)
            ) {
                Text(
                    text = stringResource(R.string.action_continue),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/* ---------- (Optionnel) Aperçu avec un faux ViewModel ---------- */
// À retirer si tu utilises un vrai ViewModel AndroidX.
class PreviewOnboardingViewModel : OnboardingViewModel {
    override var discoveryTimeAnswer: String by mutableStateOf("")
    override fun nextStep() { /* no-op for preview */ }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun DiscoveryTimeStepViewPreview() {
    MaterialTheme {
        DiscoveryTimeStepView(viewModel = PreviewOnboardingViewModel())
    }
}
