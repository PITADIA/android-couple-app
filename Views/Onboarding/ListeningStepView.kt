// ListeningStepScreen.kt
package com.yourapp.onboarding

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

// Couleurs (équivalents de ton Swift)
private val Pink = Color(0xFFFD267A)
private val LightBg = Color(0xFFF7F7FA)
private val BorderDefault = Color.Black.copy(alpha = 0.10f)

// Option avec clé (persistée dans le ViewModel) + resId (affichage localisé)
private data class ListeningOption(
    val key: String,
    @StringRes val labelRes: Int
)

@Composable
fun ListeningStepScreen(
    viewModel: OnboardingViewModel = viewModel()
) {
    val tag = "ListeningStepScreen"
    val options = remember {
        listOf(
            ListeningOption("listening_most_of_time", R.string.listening_most_of_time),
            ListeningOption("listening_sometimes",   R.string.listening_sometimes),
            ListeningOption("listening_rarely",      R.string.listening_rarely)
        )
    }

    Box(
        modifier = Modifier
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
                    text = stringResource(R.string.listening_question),
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
                    text = stringResource(R.string.private_answer_note),
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
                    val selected = viewModel.listeningAnswer == option.key
                    OptionCard(
                        text = stringResource(option.labelRes),
                        selected = selected,
                        onClick = {
                            Log.d(tag, "🔥 Option sélectionnée: ${option.key} (index=$index)")
                            viewModel.listeningAnswer = option.key
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
            val enabled = viewModel.listeningAnswer.isNotEmpty()
            Button(
                onClick = {
                    Log.d(tag, "🔥 Bouton continuer pressé")
                    viewModel.nextStep()
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
                // NOTE: si ta clé est exactement "continue", R.string.`continue` (backticks) est requis en Kotlin
                Text(
                    text = stringResource(R.string.`continue`),
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
            .noRippleClickable(onClick),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = fg
        )
    }
}

/**
 * Extension utilitaire pour un clic sans effet ripple
 * (équivalent du PlainButtonStyle() côté SwiftUI).
 */
import androidx.compose.foundation.clickable
import androidx.compose.ui.composed

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.then(
        Modifier.clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        ) { onClick() }
    )
}

/* ---------- Exemple de ViewModel minimal ----------
   Adapte-le si tu as déjà un OnboardingViewModel côté Android.
*/
class OnboardingViewModel : ViewModel() {
    var listeningAnswer by mutableStateOf("")
    fun nextStep() {
        // TODO: navigation / logique suivante
    }
}
