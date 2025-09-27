package com.love2loveapp.views.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.love2loveapp.R

@Composable
fun ConfidenceStepScreen(
    selectedAnswer: String,
    onAnswerSelect: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Options selon le rapport: 3 choix prédéfinis depuis strings.xml
    val answerOptions = listOf(
        stringResource(R.string.confidence_completely),
        stringResource(R.string.confidence_most_of_time),
        stringResource(R.string.confidence_not_always)
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = OnboardingColors.Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.height(OnboardingDimensions.TitleContentSpacing))

            // Titre selon les spécifications du rapport (font(.system(size: 28, weight: .bold)))
            Text(
                text = stringResource(R.string.confidence_question),
                style = OnboardingTypography.TitleMedium,
                modifier = Modifier.padding(horizontal = OnboardingDimensions.HorizontalPadding)
            )
            
            // Sous-titre selon le rapport
            Text(
                text = stringResource(R.string.private_answer_note),
                style = OnboardingTypography.BodySmall,
                modifier = Modifier
                    .padding(horizontal = OnboardingDimensions.HorizontalPadding)
                    .padding(top = 8.dp)
            )

            Spacer(Modifier.height(OnboardingDimensions.TitleContentSpacing))

            // Liste des options avec design du rapport
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = OnboardingDimensions.HorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(OnboardingDimensions.OptionSpacing)
            ) {
                items(answerOptions) { answer ->
                    SelectableCard(
                        text = answer,
                        isSelected = selectedAnswer == answer,
                        onClick = { onAnswerSelect(answer) }
                    )
                }
            }

            // Zone bouton selon les spécifications du rapport
            OnboardingButtonZone(
                onContinueClick = onContinue,
                isContinueEnabled = selectedAnswer.isNotEmpty()
            )
        }
    }
}