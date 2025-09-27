package com.love2loveapp.ui.views.dailychallenge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.core.viewmodels.ConnectedDailyChallengeViewModel
import com.love2loveapp.di.ServiceContainer
import com.love2loveapp.model.DailyContentRoute
import com.love2loveapp.ui.components.IntegratedErrorView
import com.love2loveapp.ui.components.IntegratedLoadingView

/**
 * 🎯 IntegratedDailyChallengeView - Vue Intégrée avec Navigation Automatique
 * 
 * Responsabilités :
 * - Navigation automatique basée sur NavigationManager
 * - États réactifs depuis IntegratedAppState
 * - Injection de dépendances via ServiceContainer
 * - UI purement déclarative sans logique métier
 * 
 * Architecture : Compose + State-Driven UI + Automatic Navigation
 */
@Composable
fun IntegratedDailyChallengeView(
    integratedAppState: IntegratedAppState,
    onShowSubscription: () -> Unit,
    onNavigateToSavedChallenges: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectedDailyChallengeViewModel = viewModel { 
        ServiceContainer.createDailyChallengeViewModel() 
    }
) {
    // Configuration automatique du ViewModel
    LaunchedEffect(integratedAppState) {
        viewModel.configureWithAppState(integratedAppState)
    }
    
    // États observables depuis NavigationManager (via IntegratedAppState)
    val currentRoute by integratedAppState.currentDailyChallengeRoute.collectAsState()
    
    // États observables depuis ViewModel
    val currentChallenge by viewModel.currentChallenge.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val canAccessPremium by viewModel.canAccessPremiumChallenges.collectAsState()
    
    // UI réactive basée sur la route calculée automatiquement
    Box(modifier = modifier.fillMaxSize()) {
        when (currentRoute) {
            is DailyContentRoute.Intro -> {
                IntegratedDailyChallengeIntroView(
                    showConnectButton = currentRoute.showConnect,
                    onConnectPartner = {
                        // TODO: Navigation vers connexion partenaire
                    },
                    onContinue = {
                        // Marquer intro comme vue
                        integratedAppState.navigationManager.markIntroAsSeen(
                            com.love2loveapp.model.DailyContentRouteCalculator.ContentType.DAILY_CHALLENGE
                        )
                    }
                )
            }
            
            is DailyContentRoute.Main -> {
                IntegratedDailyChallengeMainView(
                    currentChallenge = currentChallenge,
                    isLoading = isLoading,
                    canAccessPremium = canAccessPremium,
                    onChallengeCompleted = { challengeId ->
                        viewModel.markChallengeAsCompleted(challengeId)
                    },
                    onShowSavedChallenges = onNavigateToSavedChallenges,
                    onRefresh = {
                        viewModel.refreshData()
                    }
                )
            }
            
            is DailyContentRoute.Paywall -> {
                IntegratedDailyChallengePaywallView(
                    challengeDay = currentRoute.day,
                    onShowSubscription = onShowSubscription,
                    onDismiss = {
                        // Retour à l'état précédent
                        integratedAppState.navigationManager.forceNavigateToDailyChallengeMain()
                    }
                )
            }
            
            is DailyContentRoute.Error -> {
                IntegratedErrorView(
                    title = "Erreur Daily Challenge",
                    message = currentRoute.message,
                    onRetry = {
                        viewModel.refreshData()
                    },
                    onDismiss = {
                        viewModel.clearError()
                    }
                )
            }
            
            is DailyContentRoute.Loading -> {
                IntegratedLoadingView(
                    message = "Chargement de votre défi..."
                )
            }
        }
        
        // Afficher erreur en overlay si présente
        errorMessage?.let { error ->
            IntegratedErrorView(
                title = "Erreur",
                message = error,
                onRetry = {
                    viewModel.refreshData()
                },
                onDismiss = {
                    viewModel.clearError()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * Vue d'introduction Daily Challenge
 */
@Composable
private fun IntegratedDailyChallengeIntroView(
    showConnectButton: Boolean,
    onConnectPartner: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bienvenue dans Daily Challenge",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Relevez des défis quotidiens avec votre partenaire pour renforcer votre relation.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        if (showConnectButton) {
            androidx.compose.material3.Button(
                onClick = onConnectPartner,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("Connecter mon partenaire")
            }
        } else {
            androidx.compose.material3.Button(
                onClick = onContinue,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("Commencer")
            }
        }
    }
}

/**
 * Vue principale Daily Challenge
 */
@Composable
private fun IntegratedDailyChallengeMainView(
    currentChallenge: com.love2loveapp.model.DailyChallenge?,
    isLoading: Boolean,
    canAccessPremium: Boolean,
    onChallengeCompleted: (String) -> Unit,
    onShowSavedChallenges: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (currentChallenge != null) {
            // Afficher le défi
            Text(
                text = currentChallenge.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = currentChallenge.description,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            if (!currentChallenge.isCompleted) {
                androidx.compose.material3.Button(
                    onClick = { onChallengeCompleted(currentChallenge.id) },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Marquer comme terminé")
                }
            } else {
                Text(
                    text = "✅ Défi terminé !",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        } else {
            // Aucun défi disponible
            Text(
                text = "Aucun défi disponible aujourd'hui",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        
        // Actions
        androidx.compose.material3.OutlinedButton(
            onClick = onShowSavedChallenges,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Voir les défis sauvegardés")
        }
        
        androidx.compose.material3.TextButton(
            onClick = onRefresh,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("Actualiser")
        }
    }
}

/**
 * Vue paywall Daily Challenge
 */
@Composable
private fun IntegratedDailyChallengePaywallView(
    challengeDay: Int,
    onShowSubscription: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Défi Premium",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Vous avez atteint le défi jour $challengeDay. Débloquez l'accès premium pour continuer !",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        androidx.compose.material3.Button(
            onClick = onShowSubscription,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("Débloquer Premium")
        }
        
        androidx.compose.material3.TextButton(
            onClick = onDismiss,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("Plus tard")
        }
    }
}
