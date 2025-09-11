// CommunicationEvaluationStepScreen.kt
package com.love2love.onboarding.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2love.R

private val Pink = Color(0xFFFD267A)
private val ScreenBg = Color(0xFFF7F7FA)

/**
 * Adaptateur ViewModel -> UI.
 * Hypothèse : viewModel.communicationRating est un état Compose (mutableStateOf)
 * et nextStep() avance l’onboarding.
 *
 * Si ton VM expose un StateFlow ou LiveData, adapte en utilisant collectAsState()/observeAsState().
 */
@Composable
fun CommunicationEvaluationStepScreen(
    viewModel: OnboardingViewModel
) {
    CommunicationEvaluationStepContent(
        selected = viewModel.communicationRating,
        onSelect = { option ->
            Log.d("CommunicationStep", "Option selected: $option")
            viewModel.communicationRating = option
        },
        onContinue = {
            Log.d("CommunicationStep", "Continue pressed")
            viewModel.nextStep()
        }
    )
}

/**
 * Composable stateless : plus simple à tester et à prévisualiser.
 * Tu peux l’utiliser même sans ViewModel (injection d’état par paramètres).
 */
@Composable
private fun CommunicationEvaluationStepContent(
    selected: String,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit
) {
    val options = remember { listOf("1-4", "4-6", "6-8", "8-10") }

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
                val enabled = selected.isNotEmpty()
                Button(
                    onClick = onContinue,
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
                        text = stringResource(id = R.string.action_continue),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(40.dp))

            // Titre
            Text(
                text = stringResource(id = R.string.communication_evaluation_question),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 30.dp)
            )

            // Sous-titre
            Text(
                text = stringResource(id = R.string.private_answer_note),
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
                    val isSelected = selected == option
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
                            .clickable { onSelect(option) }
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

/* --------- Preview sans ViewModel (facultatif) --------- */

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun CommunicationEvaluationStepPreview() {
    var sel by remember { mutableStateOf("") }
    CommunicationEvaluationStepContent(
        selected = sel,
        onSelect = { sel = it },
        onContinue = {}
    )
}

/* --------- Contrat minimal du ViewModel attendu ---------
   Ajuste selon ton implémentation réelle (StateFlow, LiveData, etc.)
*/
interface OnboardingViewModel {
    var communicationRating: String
    fun nextStep()
}
