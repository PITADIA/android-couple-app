package com.love2loveapp.views.onboarding

import androidx.compose.foundation.background
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
fun RelationshipImprovementStepScreen(
    selectedImprovements: List<String>,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Options d'amélioration depuis le fichier strings.xml
    val improvements = listOf(
        stringResource(R.string.improvement_create_strong_moment),
        stringResource(R.string.improvement_revive_connection),
        stringResource(R.string.improvement_break_routine),
        stringResource(R.string.improvement_say_unsaid)
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = OnboardingColors.Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.height(OnboardingDimensions.TitleContentSpacing))

            // Titre selon les spécifications du rapport
            Text(
                text = stringResource(R.string.relationship_improvement_question),
                style = OnboardingTypography.TitleLarge,
                modifier = Modifier.padding(horizontal = OnboardingDimensions.HorizontalPadding)
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
                items(improvements) { improvement ->
                    SelectableCard(
                        text = improvement,
                        isSelected = selectedImprovements.contains(improvement),
                        onClick = { onToggle(improvement) }
                    )
                }
            }

            // Zone bouton selon les spécifications du rapport
            OnboardingButtonZone(
                onContinueClick = onContinue,
                isContinueEnabled = selectedImprovements.isNotEmpty()
            )
        }
    }
}