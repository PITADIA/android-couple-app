// ComplicityStepView.kt
package com.yourapp.onboarding

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel minimal pour refléter la logique Swift :
 * - stocke la réponse sélectionnée (id de res string)
 * - expose une méthode pour mettre à jour la sélection
 */
class OnboardingViewModel : ViewModel() {
    private val _complicityAnswerKey = MutableStateFlow<Int?>(null)
    val complicityAnswerKey: StateFlow<Int?> = _complicityAnswerKey

    fun setComplicityAnswer(@StringRes resId: Int) {
        _complicityAnswerKey.value = resId
    }
}

/**
 * Écran Compose équivalent à ComplicityStepView (SwiftUI).
 * Remplace les appels .localized/.localizedOnboarding par stringResource(...)
 */
@Composable
fun ComplicityStepView(
    viewModel: OnboardingViewModel = viewModel(),
    onContinue: () -> Unit = {} // branche ton routing ici si besoin
) {
    val context = LocalContext.current
    val selectedKey by viewModel.complicityAnswerKey.collectAsState()

    // Équivalent du tableau de clés Swift
    val complicityOptions = listOf(
        R.string.complicity_strong_fulfilling,
        R.string.complicity_present,
        R.string.complicity_sometimes_lacking,
        R.string.complicity_need_help
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA)) // ~ Color(red: 0.97, green: 0.97, blue: 0.98)
    ) {
        // Espace entre la (future) barre de progression et le titre
        Spacer(modifier = Modifier.height(40.dp))

        // Titre, aligné à gauche
        Row(
            modifier = Modifier
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.complicity_question),
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
                text = stringResource(id = R.string.private_answer_note),
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
            complicityOptions.forEach { resId ->
                val isSelected = selectedKey == resId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            Log.d(
                                "ComplicityStepView",
                                "Option sélectionnée: ${context.getString(resId)}"
                            )
                            viewModel.setComplicityAnswer(resId)
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFFFD267A) else Color.White,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFD267A) else Color.Black.copy(alpha = 0.1f)
                    ),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = stringResource(id = resId),
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
            val canContinue = selectedKey != null

            Button(
                onClick = {
                    Log.d("ComplicityStepView", "Bouton continuer pressé")
                    onContinue() // remplace par viewModel.nextStep() si tu exposes cette API côté VM
                },
                enabled = canContinue,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFFD267A).copy(alpha = 0.5f),
                    disabledContentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(id = R.string.action_continue),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
