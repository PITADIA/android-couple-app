package com.love2loveapp.views.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.love2loveapp.R
import kotlinx.coroutines.delay

@Composable
fun LoadingStepScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Messages rotatifs selon le rapport (Timer 3 secondes)
    val loadingMessages = listOf(
        stringResource(R.string.loading_profile),
        stringResource(R.string.loading_preferences),
        stringResource(R.string.loading_experience)
    )
    
    var currentMessageIndex by remember { mutableStateOf(0) }
    var loadingComplete by remember { mutableStateOf(false) }
    
    // Timer pour rotation des messages (3 secondes selon le rapport)
    LaunchedEffect(Unit) {
        while (!loadingComplete) {
            delay(3000) // 3 secondes selon le rapport
            currentMessageIndex = (currentMessageIndex + 1) % loadingMessages.size
            
            // Simuler la fin du loading après avoir affiché tous les messages
            if (currentMessageIndex == 0) { // Revenu au début
                delay(1000) // Un peu plus pour le dernier message
                loadingComplete = true
                onComplete()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = OnboardingColors.Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ProgressView selon le rapport (CircularProgressViewStyle(tint: .black), scaleEffect(2.0))
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp), // scaleEffect(2.0) simulé
                color = OnboardingColors.OnSurface,
                strokeWidth = 4.dp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Messages Rotatifs selon le rapport (font(.system(size: 18, weight: .medium)))
            AnimatedContent(
                targetState = loadingMessages[currentMessageIndex],
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { message ->
                Text(
                    text = message,
                    style = OnboardingTypography.BodyMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = OnboardingDimensions.HorizontalPadding)
                )
            }
        }
    }
}
