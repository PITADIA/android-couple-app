package com.love2loveapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.core.viewmodels.ConnectedDailyChallengeViewModel
import com.love2loveapp.ui.components.IntegratedTopBar
import com.love2loveapp.ui.components.IntegratedBottomNavigation
import com.love2loveapp.ui.screens.IntegratedHomeScreen
import com.love2loveapp.ui.screens.IntegratedAuthScreen
import com.love2loveapp.ui.screens.IntegratedLoadingScreen

/**
 * 🎯 IntegratedContentScreen - Écran Principal Intégré
 * 
 * Responsabilités :
 * - Routing principal basé sur l'état de IntegratedAppState
 * - Navigation entre authentification/onboarding/app principale
 * - Gestion des états de chargement/erreur centralisés
 * - Interface utilisateur réactive aux changements d'état
 * 
 * Architecture : Compose + State-Driven UI + Reactive Navigation
 */
@Composable
fun IntegratedContentScreen(
    integratedAppState: IntegratedAppState,
    dailyChallengeViewModel: ConnectedDailyChallengeViewModel,
    onShowSubscription: () -> Unit,
    onDeepLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // États observables depuis IntegratedAppState
    val isAppReady by integratedAppState.isAppReady.collectAsState()
    val isAuthenticated by integratedAppState.isAuthenticated.collectAsState()
    val currentUserResult by integratedAppState.currentUserResult.collectAsState()
    val hasPartner by integratedAppState.hasPartner.collectAsState()
    val isOnboardingInProgress by integratedAppState.isOnboardingInProgress.collectAsState()
    val isLoading by integratedAppState.isLoading.collectAsState()
    
    // Routing basé sur l'état global
    when {
        // 1. App pas encore prête - Écran de chargement
        !isAppReady || isLoading -> {
            IntegratedLoadingScreen(
                message = "Initialisation de l'application...",
                modifier = modifier
            )
        }
        
        // 2. Utilisateur non authentifié - Écran d'authentification
        !isAuthenticated -> {
            IntegratedAuthScreen(
                integratedAppState = integratedAppState,
                onAuthenticationSuccess = {
                    // L'état sera mis à jour automatiquement via les flows
                },
                modifier = modifier
            )
        }
        
        // 3. Utilisateur authentifié mais erreur de chargement
        currentUserResult is Result.Error -> {
            IntegratedErrorScreen(
                error = currentUserResult.exception,
                onRetry = {
                    integratedAppState.loadCurrentUser()
                },
                modifier = modifier
            )
        }
        
        // 4. Onboarding en cours
        isOnboardingInProgress -> {
            IntegratedOnboardingScreen(
                integratedAppState = integratedAppState,
                onOnboardingComplete = {
                    // L'état sera mis à jour automatiquement
                },
                modifier = modifier
            )
        }
        
        // 5. Application principale - Utilisateur authentifié et prêt
        else -> {
            IntegratedMainAppScreen(
                integratedAppState = integratedAppState,
                dailyChallengeViewModel = dailyChallengeViewModel,
                onShowSubscription = onShowSubscription,
                onDeepLink = onDeepLink,
                modifier = modifier
            )
        }
    }
}

/**
 * Écran principal de l'application (utilisateur authentifié)
 */
@Composable
private fun IntegratedMainAppScreen(
    integratedAppState: IntegratedAppState,
    dailyChallengeViewModel: ConnectedDailyChallengeViewModel,
    onShowSubscription: () -> Unit,
    onDeepLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            IntegratedTopBar(
                integratedAppState = integratedAppState,
                onProfileClick = {
                    // TODO: Navigation vers profil
                },
                onSettingsClick = {
                    // TODO: Navigation vers paramètres
                }
            )
        },
        bottomBar = {
            IntegratedBottomNavigation(
                onNavigationItemClick = { route ->
                    // TODO: Navigation entre onglets
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        IntegratedHomeScreen(
            integratedAppState = integratedAppState,
            dailyChallengeViewModel = dailyChallengeViewModel,
            onShowSubscription = onShowSubscription,
            onDeepLink = onDeepLink,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

/**
 * Écran d'erreur intégré
 */
@Composable
private fun IntegratedErrorScreen(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Une erreur s'est produite",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = error.message ?: "Erreur inconnue",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            androidx.compose.material3.Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Réessayer")
            }
        }
    }
}

/**
 * Écran d'onboarding intégré
 */
@Composable
private fun IntegratedOnboardingScreen(
    integratedAppState: IntegratedAppState,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // TODO: Implémenter écran d'onboarding complet
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bienvenue dans Love2Love",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Configuration de votre compte...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            CircularProgressIndicator()
        }
    }
    
    // Auto-complétion pour le moment
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onOnboardingComplete()
    }
}
