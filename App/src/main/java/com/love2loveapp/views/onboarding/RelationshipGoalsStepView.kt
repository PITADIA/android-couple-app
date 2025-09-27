package com.love2loveapp.views.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.love2loveapp.R

@Composable
fun RelationshipGoalsStepScreen(
    selectedGoals: List<String>,
    onGoalToggle: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Options d'objectifs relationnels depuis le fichier strings.xml
    val relationshipGoals = listOf(
        stringResource(R.string.goal_create_connection),
        stringResource(R.string.goal_find_complicity),
        stringResource(R.string.goal_increase_passion), 
        stringResource(R.string.goal_share_more_laughs),
        stringResource(R.string.goal_talk_avoided_subjects)
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
                text = stringResource(R.string.relationship_goals_question),
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
                items(relationshipGoals) { goal ->
                    SelectableCard(
                        text = goal,
                        isSelected = selectedGoals.contains(goal),
                        onClick = { onGoalToggle(goal) }
                    )
                }
            }

            // Zone bouton selon les spécifications du rapport
            OnboardingButtonZone(
                onContinueClick = onContinue,
                isContinueEnabled = selectedGoals.isNotEmpty()
            )
        }
    }
}

@Composable
fun SelectableCard(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                OnboardingColors.Primary else OnboardingColors.Surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected)
                OnboardingColors.Primary else OnboardingColors.Outline
        ),
        shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusCard),
        elevation = CardDefaults.cardElevation(
            defaultElevation = OnboardingDimensions.ShadowElevation
        )
    ) {
        Text(
            text = text,
            style = OnboardingTypography.BodyMedium.copy(
                color = if (isSelected) OnboardingColors.Surface else OnboardingColors.OnSurface
            ),
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 20.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun OnboardingButtonZone(
    onContinueClick: () -> Unit,
    isContinueEnabled: Boolean = true,
    showSkip: Boolean = false,
    onSkipClick: (() -> Unit)? = null,
    continueButtonText: String? = null, // Texte personnalisé pour le bouton
    modifier: Modifier = Modifier
) {
    // Zone bouton selon les spécifications du rapport avec shadow
    Column(
        modifier = modifier
            .background(OnboardingColors.Surface)
            .shadow(
                elevation = 10.dp, // shadow selon le rapport
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .padding(OnboardingDimensions.ButtonZoneVerticalPadding)
            .padding(horizontal = OnboardingDimensions.HorizontalPadding)
    ) {
        Button(
            onClick = onContinueClick,
            enabled = isContinueEnabled,
            shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusButton),
            modifier = Modifier
                .fillMaxWidth()
                .height(OnboardingDimensions.ButtonHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = OnboardingColors.Primary,
                contentColor = OnboardingColors.Surface,
                disabledContainerColor = OnboardingColors.Primary.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = continueButtonText ?: stringResource(R.string.continue_button),
                style = OnboardingTypography.ButtonTextAdaptive,
                maxLines = 1
            )
        }
        
        // Bouton Skip optionnel
        if (showSkip && onSkipClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onSkipClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.skip_step),
                    style = OnboardingTypography.BodyMedium.copy(
                        color = OnboardingColors.OnSurfaceVariant,
                        textDecoration = TextDecoration.Underline
                    )
                )
            }
        }
    }
}