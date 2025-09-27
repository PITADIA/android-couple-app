package com.love2loveapp.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.AppScreen
import com.love2loveapp.views.onboarding.CompleteOnboardingScreen

/**
 * Vue principale de l'application Love2Love
 * Gère la navigation entre les différents écrans principaux
 */
@Composable
fun ContentView() {
    // État de l'application
    val appState = remember { AppDelegate.appState }
    val currentScreen by appState.currentScreen.collectAsState()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            AppScreen.Launch -> {
                // Écran de démarrage avec loading - UserDataIntegrationService détermine l'état
                SplashScreen()
            }
            AppScreen.Welcome -> {
                // Page de présentation avec slogan et boutons
                WelcomeScreen(
                    onStartOnboarding = {
                        appState.startOnboarding()
                    },
                    onSignInWithGoogle = {
                        // TODO: Implémenter Google Sign-In
                        // Pour l'instant, démarrer l'onboarding
                        appState.startOnboarding()
                    }
                )
            }
            AppScreen.Onboarding -> {
                CompleteOnboardingScreen(
                    onComplete = { userData ->
                        // Finaliser l'onboarding et naviguer vers l'écran principal
                        appState.completeOnboarding()
                    }
                )
            }
            AppScreen.Main -> {
                MainScreenWithNavigation()
            }
            AppScreen.Authentication -> {
                // TODO: Écran d'authentification si nécessaire
                CompleteOnboardingScreen(
                    onComplete = { userData ->
                        appState.completeOnboarding()
                    }
                )
            }
        }
    }
}

