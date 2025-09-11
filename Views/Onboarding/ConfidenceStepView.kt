package com.love2love.onboarding

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

// --- UI constants ---
private val AccentPink = Color(0xFFFD267A)
private val ScreenBg = Color(0xFFF7F7FA)
private val CardBorder = Color.Black.copy(alpha = 0.10f)

// --- Model for options ---
data class ConfidenceOption(
    val key: String,                 // ex: "confidence_completely"
    @StringRes val labelRes: Int     // ex: R.string.confidence_completely
)

// --- ViewModel équivalent ---
class OnboardingViewModel : ViewModel() {
    var confidenceAnswer by mutableStateOf("")   // garde la clé (ex: "confidence_completely")
        private set

    fun selectAnswer(key: String) {
        Log.d("🔥 ConfidenceStepView", "Option sélectionnée: $key")
        confidenceAnswer = key
    }

    fun nextStep() {
        Log.d("🔥 ConfidenceStepView", "Bouton continuer pressé avec réponse: $confidenceAnswer")
        // TODO: navigation/étape suivante
    }
}

@Composable
fun ConfidenceStepScreen(
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current

    // Mapping des options -> resources Android (labels dans strings.xml)
    val confidenceOptions = remember {
        listOf(
            ConfidenceOption("confidence_completely",  R.string.confidence_completely),
            ConfidenceOption("confidence_most_of_time", R.string.confidence_most_of_time),
            ConfidenceOption("confidence_not_always",   R.string.confidence_not_always)
        )
    }

    val canContinue = viewModel.confidenceAnswer.isNotEmpty()

    Column(
        modifier = Modifier
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
                text = context.getString(R.string.confidence_question),
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
                text = context.getString(R.string.private_answer_note),
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
                val selected = viewModel.confidenceAnswer == option.key
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
                        .clickable { viewModel.selectAnswer(option.key) }
                ) {
                    Text(
                        text = context.getString(option.labelRes),
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
                onClick = { viewModel.nextStep() },
                enabled = canContinue,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .alpha(if (canContinue) 1f else 0.5f)
            ) {
                Text(
                    text = context.getString(R.string.action_continue),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
