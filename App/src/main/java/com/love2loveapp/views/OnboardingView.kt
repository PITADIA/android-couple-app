package com.love2loveapp.views

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

// --- Modèles minimes pour refléter ton AppState / User Swift ---
data class User(
    val name: String = "",
    val birthDate: String? = null,
    val relationshipGoals: List<String> = emptyList(),
    val relationshipDuration: String? = null,
    val relationshipImprovement: String? = null, // ex: "A, B, C"
    val questionMode: String? = null,
    val onboardingInProgress: Boolean = false
)

data class AppState(
    val isOnboardingInProgress: Boolean = false,
    val currentUser: User? = null
)

// --- Enum des étapes (reprend tes cases Swift) ---
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

// --- ViewModel : état + navigation d’étapes + progression ---
class OnboardingViewModel : ViewModel() {

    // Etat UI principal
    var currentStep by mutableStateOf(OnboardingStep.RelationshipGoals)
        private set

    // Données re-hydratées depuis AppState.currentUser
    var userName by mutableStateOf("")
    var birthDate: String? by mutableStateOf(null)
    var selectedGoals by mutableStateOf<List<String>>(emptyList())
    var relationshipDuration: String? by mutableStateOf(null)
    var selectedImprovements by mutableStateOf<List<String>>(emptyList())
    var questionMode by mutableStateOf("")

    // Overlay “connexion partenaire”
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

    // Ordre de flux (tu peux l’ajuster si nécessaire)
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
        if (i > 0) currentStep = orderedFlow[i - 1]
    }

    fun nextStep() {
        val i = orderedFlow.indexOf(currentStep)
        if (i >= 0 && i < orderedFlow.lastIndex) currentStep = orderedFlow[i + 1]
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
    }

    fun updateFromAppState(appState: AppState) {
        val user = appState.currentUser ?: return
        userName = user.name
        birthDate = user.birthDate
        selectedGoals = user.relationshipGoals
        relationshipDuration = user.relationshipDuration
        if (!user.relationshipImprovement.isNullOrBlank()) {
            selectedImprovements = user.relationshipImprovement.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
        }
        questionMode = user.questionMode ?: ""
    }
}

// --- Couleurs ---
private val OnboardingBg = Color(0xFFF7F7FA)

// --- Composable principal (équivalent de ton OnboardingView SwiftUI) ---
@Composable
fun OnboardingScreen(
    appState: AppState,
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current

    // .onAppear équivalent
    LaunchedEffect(Unit) {
        Log.d("OnboardingScreen", "🔥 Vue apparue")
        Log.d("OnboardingScreen", "🔥 Étape actuelle: ${viewModel.currentStep}")

        // Récupération immédiate si onboarding déjà en cours
        if (appState.isOnboardingInProgress) {
            Log.d("OnboardingScreen", "🔥 ONBOARDING DEJA EN COURS - RECUPERER ETAPE")
            val user = appState.currentUser
            if (user?.onboardingInProgress == true) {
                Log.d("OnboardingScreen", "🔥 USER PARTIEL DETECTE - ALLER A SUBSCRIPTION")
                Log.d("OnboardingScreen", "🔥 - Nom: ${user.name}")
                Log.d("OnboardingScreen", "🔥 - Objectifs: ${user.relationshipGoals}")

                // Restaurer dans le ViewModel
                viewModel.updateFromAppState(appState)

                // Aller directement à l'étape d'abonnement
                viewModel.forceGoTo(OnboardingStep.Subscription)
                Log.d("OnboardingScreen", "🔥 ETAPE FORCEE A SUBSCRIPTION")
            }
        }
    }

    // .onChange(of: currentStep)
    LaunchedEffect(viewModel.currentStep) {
        Log.d("OnboardingScreen", "🔥 Changement d'étape vers: ${viewModel.currentStep}")
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Barre de progression (masquée pour certaines pages, comme en Swift)
            if (viewModel.shouldShowProgressBar()) {
                OnboardingProgressBar(
                    progress = viewModel.progressValue,
                    stepIndex = viewModel.currentVisibleIndex,
                    stepCount = viewModel.visibleCount,
                    onBack = { viewModel.previousStep() },
                    modifier = Modifier
                        .padding(top = 20.dp, start = 20.dp, end = 20.dp)
                )
            }

            // Contenu d’étape
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (viewModel.currentStep) {
                    OnboardingStep.RelationshipGoals -> RelationshipGoalsStep(viewModel)
                    OnboardingStep.RelationshipDate -> RelationshipDateStep(viewModel)
                    OnboardingStep.CommunicationEvaluation -> CommunicationEvaluationStep(viewModel)
                    OnboardingStep.DiscoveryTime -> DiscoveryTimeStep(viewModel)
                    OnboardingStep.Listening -> ListeningStep(viewModel)
                    OnboardingStep.Confidence -> ConfidenceStep(viewModel)
                    OnboardingStep.Complicity -> ComplicityStep(viewModel)
                    OnboardingStep.RelationshipImprovement -> RelationshipImprovementStep(viewModel)
                    OnboardingStep.Authentication -> AuthenticationStep(viewModel)
                    OnboardingStep.DisplayName -> DisplayNameStep(viewModel)
                    OnboardingStep.ProfilePhoto -> ProfilePhotoStep(viewModel)
                    OnboardingStep.Completion -> CompletionStep(viewModel)
                    OnboardingStep.Loading -> LoadingStep(viewModel)
                    OnboardingStep.PartnerCode -> PartnerCodeStep(viewModel)
                    OnboardingStep.QuestionsIntro -> QuestionsIntroStep(viewModel)
                    OnboardingStep.CategoriesPreview -> CategoriesPreviewStep(viewModel)
                    OnboardingStep.Subscription -> SubscriptionStep(viewModel)
                }
            }
        }

        // Overlay : message de succès de connexion partenaire (équivalent .overlay + .transition(.opacity))
        AnimatedVisibility(
            visible = viewModel.shouldShowPartnerConnectionSuccess,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PartnerConnectionSuccessCard(
                    partnerName = viewModel.connectedPartnerName,
                    onDismiss = {
                        viewModel.dismissPartnerConnectionSuccess()
                        if (viewModel.currentStep == OnboardingStep.PartnerCode) {
                            viewModel.nextStep()
                        }
                    }
                )
            }
        }
    }
}

// --- Barre de progression (avec texte "Étape X sur Y") ---
@Composable
fun OnboardingProgressBar(
    progress: Float,
    stepIndex: Int,
    stepCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val counter = "Étape ${stepIndex + 1} sur $stepCount"
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = null
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = counter,
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

// --- Carte d’overlay “connexion partenaire réussie” ---
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

// --- Placeholders des écrans d’étape (à remplacer par tes versions détaillées) ---
// NB: j’utilise stringResource(...) pour la localisation côté Compose.
// Si tu as besoin d’une chaîne dans un ViewModel, utilise context.getString(R.string.xxx).

@Composable private fun RelationshipGoalsStep(vm: OnboardingViewModel) =
    StepPlaceholder("RelationshipGoals")

@Composable private fun RelationshipDateStep(vm: OnboardingViewModel) =
    StepPlaceholder("RelationshipDate")

@Composable private fun CommunicationEvaluationStep(vm: OnboardingViewModel) =
    StepPlaceholder("CommunicationEvaluation")

@Composable private fun DiscoveryTimeStep(vm: OnboardingViewModel) =
    StepPlaceholder("DiscoveryTime")

@Composable private fun ListeningStep(vm: OnboardingViewModel) =
    StepPlaceholder("Listening")

@Composable private fun ConfidenceStep(vm: OnboardingViewModel) =
    StepPlaceholder("Confidence")

@Composable private fun ComplicityStep(vm: OnboardingViewModel) =
    StepPlaceholder("Complicity")

@Composable private fun RelationshipImprovementStep(vm: OnboardingViewModel) =
    StepPlaceholder("RelationshipImprovement")

@Composable private fun AuthenticationStep(vm: OnboardingViewModel) =
    StepPlaceholder("Authentication")

@Composable private fun DisplayNameStep(vm: OnboardingViewModel) =
    StepPlaceholder("DisplayName")

@Composable private fun ProfilePhotoStep(vm: OnboardingViewModel) =
    StepPlaceholder("ProfilePhoto")

@Composable private fun CompletionStep(vm: OnboardingViewModel) =
    StepPlaceholder("Completion")

@Composable private fun LoadingStep(vm: OnboardingViewModel) =
    StepPlaceholder("Loading")

@Composable private fun PartnerCodeStep(vm: OnboardingViewModel) =
    StepPlaceholder("PartnerCode")

@Composable private fun QuestionsIntroStep(vm: OnboardingViewModel) =
    StepPlaceholder("QuestionsIntro")

@Composable private fun CategoriesPreviewStep(vm: OnboardingViewModel) =
    StepPlaceholder("CategoriesPreview")

@Composable private fun SubscriptionStep(vm: OnboardingViewModel) =
    StepPlaceholder("Subscription")

@Composable
private fun StepPlaceholder(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp)
    ) {
        Text(
            text = "Étape $name - En cours de développement",
            style = MaterialTheme.typography.titleLarge
        )
    }
}
