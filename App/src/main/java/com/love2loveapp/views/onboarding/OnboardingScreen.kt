package com.love2loveapp.views.onboarding

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Enum des étapes d'onboarding
enum class OnboardingStep {
    RelationshipGoals,
    RelationshipDate,
    CommunicationEvaluation,
    DiscoveryTime,
    Listening,
    Confidence,
    Complicity,
    RelationshipImprovement,
    Authentication,
    DisplayName,
    ProfilePhoto,
    Completion,
    Loading,
    PartnerCode,
    QuestionsIntro,
    CategoriesPreview,
    Subscription
}

// ViewModel pour gérer l'état de l'onboarding
@Composable
fun rememberOnboardingState(
    initialStep: OnboardingStep = OnboardingStep.RelationshipGoals
): OnboardingState {
    return remember {
        OnboardingState(initialStep)
    }
}

class OnboardingState(initialStep: OnboardingStep) {
    var currentStep by mutableStateOf(initialStep)
        private set
    
    // Données collectées pendant l'onboarding
    var selectedGoals by mutableStateOf<List<String>>(emptyList())
    var relationshipStartDate by mutableStateOf<java.time.LocalDate?>(null)
    var communicationRating by mutableStateOf("")
    var discoveryTimeAnswer by mutableStateOf("")
    var listeningAnswer by mutableStateOf("")
    var confidenceAnswer by mutableStateOf("")
    var complicityAnswer by mutableStateOf("")
    var selectedImprovements by mutableStateOf<List<String>>(emptyList())
    var userName by mutableStateOf("")
    var questionMode by mutableStateOf("")
    
    // Overlay "connexion partenaire"
    var shouldShowPartnerConnectionSuccess by mutableStateOf(false)
        private set
    var connectedPartnerName by mutableStateOf("")
    
    private val hiddenProgressSteps = setOf(
        OnboardingStep.Authentication,
        OnboardingStep.DisplayName,
        OnboardingStep.ProfilePhoto,
        OnboardingStep.Completion,
        OnboardingStep.Loading,
        OnboardingStep.Subscription,
        OnboardingStep.QuestionsIntro,
        OnboardingStep.CategoriesPreview
    )
    
    // Ordre de flux
    private val orderedFlow = listOf(
        OnboardingStep.RelationshipGoals,
        OnboardingStep.RelationshipDate,
        OnboardingStep.CommunicationEvaluation,
        OnboardingStep.DiscoveryTime,
        OnboardingStep.Listening,
        OnboardingStep.Confidence,
        OnboardingStep.Complicity,
        OnboardingStep.RelationshipImprovement,
        OnboardingStep.PartnerCode,
        OnboardingStep.QuestionsIntro,
        OnboardingStep.CategoriesPreview,
        OnboardingStep.Subscription,
        OnboardingStep.Authentication,
        OnboardingStep.DisplayName,
        OnboardingStep.ProfilePhoto,
        OnboardingStep.Loading,
        OnboardingStep.Completion
    )
    
    private val visibleForProgress = orderedFlow.filterNot { it in hiddenProgressSteps }
    
    val progressValue: Float
        get() {
            val idx = visibleForProgress.indexOf(currentStep).coerceAtLeast(0)
            return if (visibleForProgress.isEmpty()) 0f
            else (idx + 1f) / visibleForProgress.size.toFloat()
        }
    
    val currentVisibleIndex: Int
        get() = visibleForProgress.indexOf(currentStep).coerceAtLeast(0)
    
    val visibleCount: Int
        get() = visibleForProgress.size
    
    fun shouldShowProgressBar(step: OnboardingStep = currentStep): Boolean =
        step !in hiddenProgressSteps
    
    fun previousStep() {
        val i = orderedFlow.indexOf(currentStep)
        if (i > 0) {
            currentStep = orderedFlow[i - 1]
            Log.d("OnboardingState", "🔙 Étape précédente: $currentStep")
        }
    }
    
    fun nextStep() {
        val i = orderedFlow.indexOf(currentStep)
        if (i >= 0 && i < orderedFlow.lastIndex) {
            currentStep = orderedFlow[i + 1]
            Log.d("OnboardingState", "➡️ Étape suivante: $currentStep")
        }
    }
    
    fun showPartnerConnectionSuccess(name: String, show: Boolean = true) {
        connectedPartnerName = name
        shouldShowPartnerConnectionSuccess = show
    }
    
    fun dismissPartnerConnectionSuccess() {
        shouldShowPartnerConnectionSuccess = false
    }
    
    fun forceGoTo(step: OnboardingStep) {
        currentStep = step
        Log.d("OnboardingState", "🎯 Étape forcée: $step")
    }
    
    // Méthodes pour gérer les données
    fun toggleGoal(goal: String) {
        selectedGoals = if (selectedGoals.contains(goal)) {
            selectedGoals - goal
        } else {
            selectedGoals + goal
        }
        Log.d("OnboardingState", "🎯 Objectifs sélectionnés: $selectedGoals")
    }
    
    fun toggleImprovement(improvement: String) {
        selectedImprovements = if (selectedImprovements.contains(improvement)) {
            selectedImprovements - improvement
        } else {
            selectedImprovements + improvement
        }
        Log.d("OnboardingState", "🔧 Améliorations sélectionnées: $selectedImprovements")
    }
    
    fun updateRelationshipStartDate(date: java.time.LocalDate) {
        relationshipStartDate = date
        Log.d("OnboardingState", "📅 Date de relation: $date")
    }
    
    fun updateCommunicationRating(rating: String) {
        communicationRating = rating
        Log.d("OnboardingState", "📊 Évaluation communication: $rating")
    }
    
    fun updateDiscoveryTimeAnswer(answer: String) {
        discoveryTimeAnswer = answer
        Log.d("OnboardingState", "⏰ Réponse discovery time: $answer")
    }
    
    fun updateListeningAnswer(answer: String) {
        listeningAnswer = answer
        Log.d("OnboardingState", "👂 Réponse listening: $answer")
    }
    
    fun updateConfidenceAnswer(answer: String) {
        confidenceAnswer = answer
        Log.d("OnboardingState", "💪 Réponse confidence: $answer")
    }
    
    fun updateComplicityAnswer(answer: String) {
        complicityAnswer = answer
        Log.d("OnboardingState", "🤝 Réponse complicity: $answer")
    }
}

// Couleurs
private val OnboardingBg = Color(0xFFF7F7FA)

@Composable
fun OnboardingScreen(
    onComplete: (goals: List<String>, improvements: List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val onboardingState = rememberOnboardingState()
    
    LaunchedEffect(Unit) {
        Log.d("OnboardingScreen", "🔥 Vue d'onboarding apparue")
        Log.d("OnboardingScreen", "🔥 Étape actuelle: ${onboardingState.currentStep}")
    }
    
    LaunchedEffect(onboardingState.currentStep) {
        Log.d("OnboardingScreen", "🔥 Changement d'étape vers: ${onboardingState.currentStep}")
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Barre de progression (masquée pour certaines pages)
            if (onboardingState.shouldShowProgressBar()) {
                OnboardingProgressBar(
                    progress = onboardingState.progressValue,
                    stepIndex = onboardingState.currentVisibleIndex,
                    stepCount = onboardingState.visibleCount,
                    onBack = { onboardingState.previousStep() },
                    modifier = Modifier
                        .padding(top = 20.dp, start = 20.dp, end = 20.dp)
                )
            }
            
            // Contenu d'étape
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (onboardingState.currentStep) {
                    OnboardingStep.RelationshipGoals -> {
                        RelationshipGoalsStepScreen(
                            selectedGoals = onboardingState.selectedGoals,
                            onGoalToggle = { goal -> onboardingState.toggleGoal(goal) },
                            onContinue = { onboardingState.nextStep() }
                        )
                    }
                    OnboardingStep.RelationshipDate -> {
                        RelationshipDateStepScreen(
                            selectedDate = onboardingState.relationshipStartDate,
                            onDateChange = { date -> onboardingState.updateRelationshipStartDate(date) },
                            onContinue = { onboardingState.nextStep() }
                        )
                    }
                    OnboardingStep.CommunicationEvaluation -> {
                        CommunicationEvaluationStepScreen(
                            selectedRating = onboardingState.communicationRating,
                            onRatingSelect = { rating -> onboardingState.updateCommunicationRating(rating) },
                            onContinue = { onboardingState.nextStep() }
                        )
                    }
                    OnboardingStep.DiscoveryTime -> {
                        DiscoveryTimeStepScreen(
                            selectedAnswer = onboardingState.discoveryTimeAnswer,
                            onAnswerSelect = { answer -> onboardingState.updateDiscoveryTimeAnswer(answer) },
                            onContinue = { onboardingState.nextStep() }
                        )
                    }
                    OnboardingStep.Listening -> {
                        ListeningStepScreen(
                            selectedAnswer = onboardingState.listeningAnswer,
                            onAnswerSelect = { answer -> onboardingState.updateListeningAnswer(answer) },
                            onContinue = { onboardingState.nextStep() }
                        )
                    }
                    OnboardingStep.Confidence -> {
                        ConfidenceStepScreen(
                            selectedAnswer = onboardingState.confidenceAnswer,
                            onAnswerSelect = { answer -> onboardingState.updateConfidenceAnswer(answer) },
                            onContinue = { onboardingState.nextStep() }
                        )
                    }
                    OnboardingStep.Complicity -> {
                        ComplicityStepScreen(
                            selectedAnswer = onboardingState.complicityAnswer,
                            onAnswerSelect = { answer -> onboardingState.updateComplicityAnswer(answer) },
                            onContinue = { onboardingState.nextStep() }
                        )
                    }
                    OnboardingStep.RelationshipImprovement -> {
                        RelationshipImprovementStepScreen(
                            selectedImprovements = onboardingState.selectedImprovements,
                            onToggle = { improvement -> onboardingState.toggleImprovement(improvement) },
                            onContinue = { onboardingState.nextStep() }
                        )
                    }
                    OnboardingStep.Authentication -> StepPlaceholder("Authentication", onboardingState::nextStep)
                    OnboardingStep.DisplayName -> StepPlaceholder("DisplayName", onboardingState::nextStep)
                    OnboardingStep.ProfilePhoto -> StepPlaceholder("ProfilePhoto", onboardingState::nextStep)
                    OnboardingStep.Completion -> {
                        CompletionStepScreen(
                            selectedGoals = onboardingState.selectedGoals,
                            selectedImprovements = onboardingState.selectedImprovements,
                            onContinue = {
                                Log.d("OnboardingScreen", "✅ Onboarding terminé!")
                                onComplete(onboardingState.selectedGoals, onboardingState.selectedImprovements)
                            }
                        )
                    }
                    OnboardingStep.Loading -> StepPlaceholder("Loading", onboardingState::nextStep)
                    OnboardingStep.PartnerCode -> StepPlaceholder("PartnerCode", onboardingState::nextStep)
                    OnboardingStep.QuestionsIntro -> StepPlaceholder("QuestionsIntro", onboardingState::nextStep)
                    OnboardingStep.CategoriesPreview -> StepPlaceholder("CategoriesPreview", onboardingState::nextStep)
                    OnboardingStep.Subscription -> StepPlaceholder("Subscription", onboardingState::nextStep)
                }
            }
        }
        
        // Overlay : message de succès de connexion partenaire
        AnimatedVisibility(
            visible = onboardingState.shouldShowPartnerConnectionSuccess,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PartnerConnectionSuccessCard(
                    partnerName = onboardingState.connectedPartnerName,
                    onDismiss = {
                        onboardingState.dismissPartnerConnectionSuccess()
                        if (onboardingState.currentStep == OnboardingStep.PartnerCode) {
                            onboardingState.nextStep()
                        }
                    }
                )
            }
        }
    }
}

// Barre de progression avec texte "Étape X sur Y"
@Composable
fun OnboardingProgressBar(
    progress: Float,
    stepIndex: Int,
    stepCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Étape ${stepIndex + 1} sur $stepCount",
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
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// Carte d'overlay "connexion partenaire réussie"
@Composable
fun PartnerConnectionSuccessCard(
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
                text = "$partnerName s'est connecté à votre aventure Love2Love !",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss) {
                Text(text = "OK")
            }
        }
    }
}

// Placeholder temporaire pour les étapes non encore implémentées
@Composable
private fun StepPlaceholder(name: String, onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Étape $name",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "En cours de développement",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Button(onClick = onContinue) {
                Text("Continuer")
            }
        }
    }
}
