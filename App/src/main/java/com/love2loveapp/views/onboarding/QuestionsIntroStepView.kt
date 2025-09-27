package com.love2loveapp.views.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.love2loveapp.R

@Composable
fun QuestionsIntroStepScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                text = stringResource(R.string.questions_intro_title),
                style = OnboardingTypography.TitleLarge,
                modifier = Modifier.padding(horizontal = OnboardingDimensions.HorizontalPadding)
            )

            Spacer(Modifier.height(OnboardingDimensions.TitleContentSpacing))

            // Image selon le rapport
            // Image("mima") - aspectRatio(.fit) - frame(maxWidth: .infinity, maxHeight: 280)
            // cornerRadius(20) - padding(.horizontal, 30)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mima),
                    contentDescription = "Questions intro image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp) // maxHeight: 280 selon le rapport
                        .padding(horizontal = OnboardingDimensions.HorizontalPadding)
                        .clip(RoundedCornerShape(20.dp)), // cornerRadius(20) selon le rapport
                    contentScale = ContentScale.Fit // aspectRatio(.fit) selon le rapport
                )

                Spacer(modifier = Modifier.height(30.dp))

                // Description selon le rapport
                // font(.system(size: 16)) - foregroundColor(.black.opacity(0.7))
                // multilineTextAlignment(.center) - lineLimit(nil)
                // fixedSize(horizontal: false, vertical: true)
                Text(
                    text = stringResource(R.string.questions_intro_subtitle),
                    style = OnboardingTypography.BodyMedium.copy(
                        color = OnboardingColors.OnSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OnboardingDimensions.HorizontalPadding)
                )
            }

            // Zone Bouton selon le rapport avec shadow
            // shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
            Column(
                modifier = Modifier
                    .background(OnboardingColors.Surface)
                    .shadow(
                        elevation = 10.dp, // radius: 10 selon le rapport
                        ambientColor = Color.Black.copy(alpha = 0.1f),
                        spotColor = Color.Black.copy(alpha = 0.1f)
                    )
                    .padding(OnboardingDimensions.ButtonZoneVerticalPadding)
                    .padding(horizontal = OnboardingDimensions.HorizontalPadding)
            ) {
                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusButton),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OnboardingDimensions.ButtonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnboardingColors.Primary,
                        contentColor = OnboardingColors.Surface
                    )
                ) {
                    Text(
                        text = stringResource(R.string.continue_button),
                        style = OnboardingTypography.ButtonText
                    )
                }
            }
        }
    }
}
