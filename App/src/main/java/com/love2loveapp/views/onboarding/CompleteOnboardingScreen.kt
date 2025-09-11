package com.love2loveapp.views.onboarding

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.love2loveapp.R
import com.love2loveapp.viewmodels.CompleteOnboardingViewModel

// Couleurs
private val OnboardingBg = Color(0xFFF7F7FA)
private val AccentPink = Color(0xFFFD267A)

/**
 * Écran d'onboarding complet avec toutes les 17 étapes
 * Intègre toutes les pages traduites du dossier Views/Onboarding
 */
@Composable
fun CompleteOnboardingScreen(
    viewModel: CompleteOnboardingViewModel = viewModel(),
    onComplete: (userData: Map<String, Any>) -> Unit
) {
    val context = LocalContext.current
    val currentStep by viewModel.currentStep.collectAsState()
    val shouldShowPartnerConnectionSuccess by viewModel.shouldShowPartnerConnectionSuccess.collectAsState()
    val connectedPartnerName by viewModel.connectedPartnerName.collectAsState()

    // Logs d'apparition
    LaunchedEffect(Unit) {
        Log.d("CompleteOnboardingScreen", "🔥 Écran d'onboarding complet lancé")
        Log.d("CompleteOnboardingScreen", "🔥 Étape actuelle: $currentStep")
    }

    // Observer les changements d'étape
    LaunchedEffect(currentStep) {
        Log.d("CompleteOnboardingScreen", "🔥 Changement d'étape vers: $currentStep")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Barre de progression (masquée pour certaines pages)
            if (viewModel.shouldShowProgressBar()) {
                val progress by viewModel.progress.collectAsState()
                OnboardingProgressBar(
                    progress = progress,
                    onBack = { viewModel.previousStep() },
                    modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp)
                )
            }

            // Contenu d'étape
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentStep) {
                    CompleteOnboardingViewModel.OnboardingStep.RelationshipGoals -> {
                        RelationshipGoalsStepScreen(
                            selectedGoals = viewModel.selectedGoals.collectAsState().value,
                            onGoalToggle = { goal -> viewModel.toggleGoal(goal) },
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.RelationshipImprovement -> {
                        RelationshipImprovementStepScreen(
                            selectedImprovements = viewModel.selectedImprovements.collectAsState().value,
                            onToggle = { improvement -> viewModel.toggleImprovement(improvement) },
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.RelationshipDate -> {
                        RelationshipDateStepScreen(
                            selectedDate = viewModel.relationshipStartDate.collectAsState().value,
                            onDateChange = { date -> viewModel.updateRelationshipStartDate(date) },
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.CommunicationEvaluation -> {
                        CommunicationEvaluationStepScreen(
                            selectedRating = viewModel.communicationRating.collectAsState().value,
                            onRatingSelect = { rating -> viewModel.updateCommunicationRating(rating) },
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.DiscoveryTime -> {
                        DiscoveryTimeStepScreen(
                            selectedAnswer = viewModel.discoveryTimeAnswer.collectAsState().value,
                            onAnswerSelect = { answer -> viewModel.updateDiscoveryTimeAnswer(answer) },
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.Listening -> {
                        ListeningStepScreen(
                            selectedAnswer = viewModel.listeningAnswer.collectAsState().value,
                            onAnswerSelect = { answer -> viewModel.updateListeningAnswer(answer) },
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.Confidence -> {
                        ConfidenceStepScreen(
                            selectedAnswer = viewModel.confidenceAnswer.collectAsState().value,
                            onAnswerSelect = { answer -> viewModel.updateConfidenceAnswer(answer) },
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.Complicity -> {
                        ComplicityStepScreen(
                            selectedAnswer = viewModel.complicityAnswer.collectAsState().value,
                            onAnswerSelect = { answer -> viewModel.updateComplicityAnswer(answer) },
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.Authentication -> {
                        GoogleAuthenticationStepView(
                            onAuthenticationComplete = { viewModel.completeAuthentication() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.DisplayName -> {
                        DisplayNameStepScreen(
                            currentName = viewModel.userName.collectAsState().value,
                            onNameChange = { name -> viewModel.updateUserName(name) },
                            onContinue = { viewModel.nextStep() },
                            onSkip = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.ProfilePhoto -> {
                        ProfilePhotoStepScreenPlaceholder(
                            onContinue = { viewModel.nextStep() },
                            onSkip = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.Completion -> {
                        CompletionStepScreen(
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.Loading -> {
                        LoadingStepScreen(
                            isLoading = viewModel.isLoading.collectAsState().value
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.PartnerCode -> {
                        PartnerCodeStepScreen(
                            onNextStep = { viewModel.nextStep() },
                            onSkipSubscriptionDueToInheritance = { viewModel.skipSubscriptionDueToInheritance() },
                            onPartnerConnected = { partnerName -> viewModel.showPartnerConnectionSuccess(partnerName) }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.QuestionsIntro -> {
                        QuestionsIntroStepScreen(
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.CategoriesPreview -> {
                        CategoriesPreviewStepScreen(
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.Subscription -> {
                        GooglePlaySubscriptionStepView(
                            onSubscriptionComplete = { viewModel.completeSubscription() },
                            onSkip = { viewModel.skipSubscription() }
                        )
                    }
                }
            }
        }

        // Overlay : message de succès de connexion partenaire
        AnimatedVisibility(
            visible = shouldShowPartnerConnectionSuccess,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PartnerConnectionSuccessCardInternal(
                    partnerName = connectedPartnerName,
                    onDismiss = {
                        viewModel.dismissPartnerConnectionSuccess()
                        if (currentStep == CompleteOnboardingViewModel.OnboardingStep.PartnerCode) {
                            viewModel.nextStep()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingProgressBar(
    progress: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Retour"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Progression",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                    .background(AccentPink)
            )
        }
    }
}

@Composable
private fun PartnerConnectionSuccessCardInternal(
    partnerName: String,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Partenaire connecté !",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Vous êtes maintenant connecté avec $partnerName",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
            ) {
                Text(
                    text = "OK",
                    color = Color.White
                )
            }
        }
    }
}

// Placeholders pour les écrans qui seront intégrés depuis les fichiers existants
@Composable
fun GoogleAuthenticationStepScreen(
    onAuthenticationComplete: () -> Unit
) {
    // TODO: Intégrer le vrai GoogleAuthenticationStepView avec Google Sign-In
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔐 Google Authentication")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAuthenticationComplete) {
                Text("Simuler Google Sign-In")
            }
        }
    }
}

@Composable
fun DisplayNameStepScreen(
    currentName: String,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    // TODO: Intégrer le vrai DisplayNameStepView
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👤 Display Name: $currentName")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onContinue) {
                Text("Continuer")
            }
        }
    }
}

@Composable
fun ProfilePhotoStepScreenPlaceholder(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    // TODO: Intégrer le vrai ProfilePhotoStepView
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷 Profile Photo")
            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = onContinue) {
                    Text("Continuer")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onSkip) {
                    Text("Passer")
                }
            }
        }
    }
}

@Composable
fun LoadingStepScreen(
    isLoading: Boolean
) {
    // TODO: Intégrer le vrai LoadingStepView
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text("⏳ Loading...")
        }
    }
}

@Composable
fun PartnerCodeStepScreen(
    onNextStep: () -> Unit,
    onSkipSubscriptionDueToInheritance: () -> Unit,
    onPartnerConnected: (String) -> Unit
) {
    // TODO: Intégrer le vrai PartnerCodeStepView
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("💕 Partner Code")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNextStep) {
                Text("Continuer")
            }
        }
    }
}

@Composable
fun QuestionsIntroStepScreen(
    onContinue: () -> Unit
) {
    // TODO: Intégrer le vrai QuestionsIntroStepView
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("❓ Questions Intro")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onContinue) {
                Text("Continuer")
            }
        }
    }
}

@Composable
fun CategoriesPreviewStepScreen(
    onContinue: () -> Unit
) {
    // TODO: Intégrer le vrai CategoriesPreviewStepView
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📚 Categories Preview")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onContinue) {
                Text("Continuer")
            }
        }
    }
}

@Composable
fun GooglePlaySubscriptionStepScreen(
    onSubscriptionComplete: () -> Unit,
    onSkip: () -> Unit
) {
    // TODO: Intégrer le vrai SubscriptionStepView avec Google Play Billing
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("💳 Google Play Subscription")
            Spacer(Modifier.height(16.dp))
            Row {
                Button(onClick = onSubscriptionComplete) {
                    Text("S'abonner")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onSkip) {
                    Text("Passer")
                }
            }
        }
    }
}

@Composable
fun CompletionStepScreen(
    onContinue: () -> Unit
) {
    // TODO: Intégrer le vrai CompletionStepView
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉 Completion")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onContinue) {
                Text("Finaliser")
            }
        }
    }
}
