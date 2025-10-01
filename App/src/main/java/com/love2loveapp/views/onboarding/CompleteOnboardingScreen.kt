package com.love2loveapp.views.onboarding

import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.love2loveapp.R
import com.love2loveapp.viewmodels.CompleteOnboardingViewModel
import kotlinx.coroutines.delay
import com.love2loveapp.views.components.AndroidPhotoEditorView
import com.love2loveapp.views.UnifiedProfileImageView
import com.love2loveapp.views.ProfileImageType
import com.love2loveapp.views.onboarding.PartnerCodeStepScreen
import com.love2loveapp.services.profile.ProfileRepository
import com.love2loveapp.AppDelegate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import kotlin.math.sin
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import java.util.concurrent.TimeUnit
import androidx.compose.ui.graphics.toArgb

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
                    showBackButton = viewModel.shouldShowBackButton(),
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
                        RealDisplayNameStepScreen(
                            currentName = viewModel.userName.collectAsState().value,
                            onNameChange = { name -> viewModel.updateUserName(name) },
                            onContinue = { viewModel.nextStep() },
                            onSkip = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.ProfilePhoto -> {
                        RealProfilePhotoStepScreen(
                            viewModel = viewModel, // ✅ Passer le ViewModel
                            onContinue = { viewModel.nextStep() },
                            onSkip = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.Completion -> {
                        RealCompletionStepScreen(
                            onContinue = { viewModel.nextStep() }
                        )
                    }
                    CompleteOnboardingViewModel.OnboardingStep.Loading -> {
                        RealLoadingStepScreen(
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
                            activity = context as ComponentActivity,
                            onComplete = { 
                                Log.d("OnboardingScreen", "✅ Abonnement Google Play validé")
                                viewModel.completeSubscription() 
                            },
                            onSkip = {
                                Log.d("OnboardingScreen", "❌ Paywall fermé - continuation sans premium")
                                viewModel.skipSubscription()
                            }
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
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Retour"
                    )
                }
            } else {
                // Spacer pour équilibrage quand pas de bouton retour
                Spacer(modifier = Modifier.width(48.dp))
            }
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

// PartnerCodeStepScreen est maintenant définie dans PartnerCodeStepScreen.kt
// Import ajouté automatiquement par l'IDE

@Composable
fun QuestionsIntroStepScreen(
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f))
    ) {
        // Espace entre barre de progression et titre (harmonisé)
        Spacer(modifier = Modifier.height(40.dp))
        
        // Titre centré à gauche
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
        ) {
        Text(
            text = stringResource(R.string.discover_premium), // Utilise votre clé existante
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Espace entre titre et image (augmenté pour plus de respiration)
        Spacer(modifier = Modifier.height(80.dp))
        
        // Image de la page de présentation des questions du jour
        Image(
            painter = painterResource(id = R.drawable.mima),
            contentDescription = "Questions intro image",
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(horizontal = 30.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Fit
        )
        
        // Sous-titre descriptif (style paywall)
        Column(
            modifier = Modifier.padding(top = 30.dp)
        ) {
            Text(
                text = stringResource(R.string.daily_question_intro_subtitle),
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
            )
        }
        
        // Deuxième Spacer pour pousser la zone bouton vers le bas
        Spacer(modifier = Modifier.weight(1f))
        
        // Zone blanche collée en bas (style CategoriesPreviewStepView)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .shadow(
                    elevation = 10.dp,
                    spotColor = Color.Black.copy(alpha = 0.1f),
                    ambientColor = Color.Black.copy(alpha = 0.1f)
                )
                .padding(vertical = 30.dp)
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = stringResource(R.string.continue_button),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun CategoriesPreviewStepScreen(
    onContinue: () -> Unit
) {
    // État d'animation en cascade selon le rapport iOS
    var visibleCategories by remember { mutableStateOf(setOf<String>()) }
    var currentCategoryIndex by remember { mutableStateOf(0) }
    
    // Les 8 collections selon le rapport iOS avec les bons emojis
    val categories = remember {
        listOf(
            CategoryData("en-couple", "💞", R.string.category_en_couple_title, R.string.category_en_couple_subtitle, false),
            CategoryData("les-plus-hots", "🌶️", R.string.category_desirs_inavoues_title, R.string.category_desirs_inavoues_subtitle, true),
            CategoryData("a-distance", "✈️", R.string.category_a_distance_title, R.string.category_a_distance_subtitle, true),
            CategoryData("questions-profondes", "✨", R.string.category_questions_profondes_title, R.string.category_questions_profondes_subtitle, true),
            CategoryData("pour-rire-a-deux", "😂", R.string.category_pour_rire_title, R.string.category_pour_rire_subtitle, true),
            CategoryData("tu-preferes", "🤍", R.string.category_tu_preferes_title, R.string.category_tu_preferes_subtitle, true),
            CategoryData("mieux-ensemble", "💌", R.string.category_mieux_ensemble_title, R.string.category_mieux_ensemble_subtitle, true),
            CategoryData("pour-un-date", "🍸", R.string.category_pour_un_date_title, R.string.category_pour_un_date_subtitle, true)
        )
    }
    
    // Animation en cascade selon le rapport iOS : délai initial de 0.3s puis 0.3s entre chaque carte
    LaunchedEffect(Unit) {
        delay(300) // Délai initial selon le rapport
        categories.forEachIndexed { index, category ->
            delay(300) // 0.3s entre chaque carte selon le rapport
            visibleCategories = visibleCategories + category.id
        }
    }

    // Layout principal selon le rapport iOS
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f)), // #F7F7F8 selon le rapport
        horizontalAlignment = Alignment.Start
    ) {
        // Espace haut (40pt selon le rapport)
        Spacer(modifier = Modifier.height(40.dp))
        
        // Titre principal selon le rapport iOS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp) // padding horizontal selon le rapport
        ) {
            Text(
                text = stringResource(R.string.more_than_2000_questions), // Clé du rapport iOS
                fontSize = 28.sp, // font(.system(size: 28, weight: .bold)) selon le rapport
                fontWeight = FontWeight.Bold,
                color = Color.Black, // .foregroundColor(.black) selon le rapport
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Espace titre-cartes (60pt selon le rapport)
        Spacer(modifier = Modifier.height(60.dp))
        
        // ScrollView avec cartes animées selon le rapport iOS
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp), // padding selon le rapport
            verticalArrangement = Arrangement.spacedBy(12.dp) // spacing: 12 selon le rapport
        ) {
            items(categories) { category ->
                CategoryPreviewCardIOS(
                    category = category,
                    isVisible = visibleCategories.contains(category.id)
                )
            }
        }
        
        // Zone bouton (fixe en bas) selon le rapport iOS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White) // .background(Color.white) selon le rapport
                .shadow(
                    elevation = 10.dp, // shadow(radius: 10, x: 0, y: -5) selon le rapport
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
                .padding(vertical = 30.dp, horizontal = 30.dp), // padding selon le rapport
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A), // Rose principal (#FD267A) selon le rapport
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.continue_button),
                    fontSize = 18.sp, // font(.system(size: 18, weight: .semibold)) selon le rapport
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Data class pour les catégories iOS
data class CategoryData(
    val id: String,
    val emoji: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val isPremium: Boolean
)

@Composable
private fun CategoryPreviewCardIOS(
    category: CategoryData,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    // Animation spring selon le rapport iOS
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f, // dampingFraction: 0.8 selon le rapport
            stiffness = Spring.StiffnessMediumLow // response: 0.6 selon le rapport
        ), label = "alpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = 0.8f, // dampingFraction: 0.8 selon le rapport
            stiffness = Spring.StiffnessMediumLow // response: 0.6 selon le rapport
        ), label = "scale"
    )

    // Design de carte selon le rapport iOS
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .scale(animatedScale),
        shape = RoundedCornerShape(16.dp), // cornerRadius: 16 selon le rapport
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f) // Color.white.opacity(0.95) selon le rapport
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp // shadow(radius: 8, x: 0, y: 2) selon le rapport
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp), // padding selon le rapport
            horizontalArrangement = Arrangement.spacedBy(16.dp), // spacing: 16 selon le rapport
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contenu texte (gauche) selon le rapport iOS
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp) // spacing: 6 selon le rapport
            ) {
                // Titre principal selon le rapport
                Text(
                    text = stringResource(category.titleRes),
                    fontSize = 20.sp, // font(.system(size: 20, weight: .bold)) selon le rapport
                    fontWeight = FontWeight.Bold,
                    color = Color.Black // .foregroundColor(.black) selon le rapport
                )

                // Sous-titre descriptif selon le rapport
                Text(
                    text = stringResource(category.subtitleRes),
                    fontSize = 14.sp, // font(.system(size: 14)) selon le rapport
                    color = Color.Gray // .foregroundColor(.gray) selon le rapport
                )
            }

            // Emoji (droite) selon le rapport iOS
            Text(
                text = category.emoji,
                fontSize = 28.sp // font(.system(size: 28)) selon le rapport
            )
        }
    }
}

// GooglePlaySubscriptionStepView est maintenant définie dans GooglePlaySubscriptionStepView.kt
// Import ajouté automatiquement par l'IDE

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

// ===========================================
// VRAIES IMPLEMENTATIONS DES ÉTAPES
// ===========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealDisplayNameStepScreen(
    currentName: String,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    // 🎯 Gestion du clavier et du focus
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(currentName) {
        name = currentName
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // ✅ Gestion automatique de l'espace clavier
            .background(OnboardingColors.Background)
            .pointerInput(Unit) {
                // 🎯 Fermer clavier au tap en dehors des champs de saisie
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OnboardingDimensions.HorizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(OnboardingDimensions.TitleContentSpacing))
            
            // Titre selon le rapport design
            Text(
                text = stringResource(R.string.display_name_step_title),
                style = OnboardingTypography.TitleLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 🛡️ Champ de texte sécurisé contre les crashes Compose hover
            OutlinedTextField(
                value = name,
                onValueChange = { newValue -> 
                    // Protection simple contre les valeurs nulles/problématiques
                    if (newValue.length <= 50) { // Limite raisonnable
                        name = newValue
                        onNameChange(newValue)
                    }
                },
                placeholder = { Text(stringResource(R.string.display_name_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth(),
                singleLine = true,
                maxLines = 1,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFD267A),
                    cursorColor = Color(0xFFFD267A)
                )
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Boutons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OnboardingColors.OnSurfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(R.string.skip_step),
                        style = OnboardingTypography.ButtonTextAdaptive.copy(
                            color = OnboardingColors.OnSurfaceVariant
                        )
                    )
                }
                
                Button(
                    onClick = onContinue,
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnboardingColors.Primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.continue_button),
                        style = OnboardingTypography.ButtonTextAdaptive
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
fun RealProfilePhotoStepScreen(
    viewModel: CompleteOnboardingViewModel, // ✅ NOUVEAU : ViewModel pour stockage temporaire
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    profileRepository: ProfileRepository? = AppDelegate.profileRepository
) {
    var hasSelectedPhoto by remember { mutableStateOf(false) }
    
    Log.d("ProfilePhoto", "🎨 RealProfilePhotoStepScreen avec éditeur sophistiqué")
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OnboardingDimensions.HorizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(OnboardingDimensions.TitleContentSpacing))
            
            // Titre selon le rapport design
            Text(
                text = stringResource(R.string.add_profile_photo),
                style = OnboardingTypography.TitleLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            
            // Centrer verticalement le cercle photo
            Spacer(modifier = Modifier.weight(1f))
            
            // 🎯 BONHOMME BLANC CLIQUABLE - Ouverture directe galerie (comme profil)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                UnifiedProfileImageView(
                    imageType = ProfileImageType.USER,
                    size = 160.dp, // Grande taille pour l'onboarding
                    userName = "", // Pas de nom = bonhomme blanc
                    onImageUpdated = { bitmap ->
                        // 🎯 Callback pour onboarding - stockage temporaire dans ViewModel
                        Log.d("ProfilePhoto", "✅ Image mise à jour: ${bitmap.width}x${bitmap.height}")
                        viewModel.updateProfileImage(bitmap)
                        hasSelectedPhoto = true
                    },
                    modifier = Modifier.size(160.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // Boutons navigation 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        Log.d("ProfilePhoto", "⏭️ Bouton 'Passer' cliqué")
                        onSkip()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OnboardingColors.OnSurfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(R.string.skip_step),
                        style = OnboardingTypography.ButtonTextAdaptive.copy(
                            color = OnboardingColors.OnSurfaceVariant
                        )
                    )
                }
                
                Button(
                    onClick = {
                        Log.d("ProfilePhoto", "✅ Bouton 'Continuer' cliqué (photo: $hasSelectedPhoto)")
                        onContinue()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnboardingColors.Primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.continue_button),
                        style = OnboardingTypography.ButtonTextAdaptive
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
fun RealCompletionStepScreen(
    onContinue: () -> Unit
) {
    // Animation des confettis
    var showConfetti by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        Log.d("CompletionStep", "🎉 Page de completion affichée avec confettis")
        // Afficher les confettis pendant 3 secondes
        delay(3000)
        showConfetti = false
    }
    
    // Utiliser Surface comme les autres pages pour un background cohérent
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = OnboardingColors.Background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(), // Pas de padding horizontal sur Column principale
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(OnboardingDimensions.TitleContentSpacing))
            
            // Premier Spacer pour centrer le contenu
            Spacer(modifier = Modifier.weight(1f))
            
            // Contenu principal centré (comme iOS)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = OnboardingDimensions.HorizontalPadding) // Padding seulement sur le contenu
            ) {
                // Petit titre "Tout est terminé" sans icône
                Text(
                    text = stringResource(R.string.all_completed),
                    style = OnboardingTypography.BodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = OnboardingColors.OnSurfaceVariant
                    )
                )

                // Grand titre en deux lignes (comme iOS)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.thank_you_for),
                        style = OnboardingTypography.TitleXLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.trusting_us),
                        style = OnboardingTypography.TitleXLarge,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Description
                Text(
                    text = stringResource(R.string.privacy_promise),
                    style = OnboardingTypography.BodyMedium.copy(
                        color = OnboardingColors.OnSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Utiliser OnboardingButtonZone comme les autres pages pour la cohérence
            OnboardingButtonZone(
                onContinueClick = {
                    Log.d("CompletionStep", "🚀 Bouton 'Commencer l'aventure' cliqué")
                    onContinue()
                },
                isContinueEnabled = true,
                continueButtonText = stringResource(R.string.completion_button)
            )
            }
            
            // 🎉 Effet confettis optimisé avec Konfetti (plus de saccades !)
            if (showConfetti) {
                OptimizedConfettiAnimation()
            }
        }
    }
}

/**
 * 🎉 Animation de confettis hautement optimisée avec la bibliothèque Konfetti
 * - Élimine les saccades causées par les 50 composables individuels
 * - Utilise le rendu natif optimisé pour GPU
 * - Performance fluide même sur appareils moins puissants
 */
@Composable
fun OptimizedConfettiAnimation() {
    // Configuration des couleurs Love2Love
    val confettiColors = remember {
        listOf<Int>(
            Color(0xFFFD267A).toArgb(), // Rose Love2Love
            Color(0xFF4CAF50).toArgb(), // Vert
            Color(0xFFFFEB3B).toArgb(), // Jaune
            Color(0xFF2196F3).toArgb(), // Bleu
            Color(0xFFFF9800).toArgb(), // Orange
            Color(0xFFE91E63).toArgb()  // Rose foncé
        )
    }
    
    // Configuration de l'explosion depuis le centre (optimisée)
    val party = remember {
        Party(
            speed = 30f, // Vitesse des particules
            maxSpeed = 50f, // Vitesse maximale
            damping = 0.9f, // Amortissement pour effet gravitationnel
            spread = 360, // Explosion complète (360°)
            colors = confettiColors,
            emitter = Emitter(duration = 2, TimeUnit.SECONDS).max(80), // 2s d'émission, 80 particules max
            position = Position.Relative(0.5, 0.5) // Centre de l'écran
        )
    }
    
    // Vue Konfetti optimisée (rendu GPU natif)
    KonfettiView(
        modifier = Modifier.fillMaxSize(),
        parties = listOf(party)
    )
}

@Composable
fun RealLoadingStepScreen(
    isLoading: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(OnboardingDimensions.HorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                // ProgressView selon le rapport (CircularProgressViewStyle(tint: .black), scaleEffect(2.0))
                CircularProgressIndicator(
                    color = OnboardingColors.OnSurface,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(64.dp) // scaleEffect(2.0) simulé
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Messages selon le rapport (font(.system(size: 18, weight: .medium)))
                Text(
                    text = stringResource(R.string.loading_profile),
                    style = OnboardingTypography.BodyMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "✅ Prêt !",
                    style = OnboardingTypography.BodyMedium
                )
            }
        }
    }
}

@Composable
private fun BenefitCard(
    icon: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = icon,
                fontSize = 24.sp
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    emoji: String,
    title: String,
    subtitle: String,
    isPremium: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji
            Text(
                text = emoji,
                fontSize = 28.sp
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Contenu
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                    
                    if (isPremium) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PREMIUM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800),
                            modifier = Modifier
                                .background(
                                    Color(0xFFFF9800).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Flèche
            Text(
                text = "→",
                fontSize = 20.sp,
                color = Color(0xFFFD267A)
            )
        }
    }
}
