package com.love2loveapp.views.dailyquestion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.love2loveapp.AppDelegate
import com.love2loveapp.R
import com.love2loveapp.models.DailyContentRoute
import com.love2loveapp.models.DailyContentRouteCalculator
import com.love2loveapp.views.tutorial.PartnerManagementScreen
import com.love2loveapp.views.subscription.SubscriptionScreen
import kotlinx.coroutines.launch

/**
 * 🗺️ DailyQuestionFlowScreen - Router Principal Questions du Jour
 * Équivalent iOS DailyQuestionFlowView.swift
 * 
 * Implémente la logique de navigation conditionnelle complexe :
 * 1. Vérification connexion partenaire (OBLIGATOIRE)
 * 2. Affichage intro si première fois
 * 3. Vérification freemium (3 jours gratuits)  
 * 4. Route vers paywall après limite
 * 5. Interface chat principale
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQuestionFlowScreen(
    onNavigateToPartnerConnection: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateBack: () -> Unit
) {
    // 🎭 État des sheets/popups
    var activeSheet by remember { mutableStateOf<DailyQuestionSheetType?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 🔥 Services depuis AppDelegate
    val appState = AppDelegate.appState
    val dailyQuestionRepository = AppDelegate.dailyQuestionRepository
    val freemiumManager = appState.freemiumManager
    
    // 📊 États observés
    val currentUser by appState.currentUser.collectAsStateWithLifecycle()
    val introFlags by appState.introFlags.collectAsStateWithLifecycle()
    val currentQuestion by (dailyQuestionRepository?.currentQuestion?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    val currentSettings by (dailyQuestionRepository?.currentSettings?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    val isLoading by (dailyQuestionRepository?.isLoading?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) })
    val errorMessage by (dailyQuestionRepository?.errorMessage?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })

    // 🔧 Calculs dérivés selon la logique iOS
    val hasConnectedPartner = remember(currentUser) {
        val partnerId = currentUser?.partnerId?.trim()
        !partnerId.isNullOrEmpty()
    }
    val hasSeenIntro = introFlags.dailyQuestion
    val currentQuestionDay = currentSettings?.calculateExpectedDay() ?: 1
    val shouldShowPaywall = remember(currentUser, currentQuestionDay) {
        if (currentUser?.isSubscribed == true) {
            false
        } else {
            // Logique freemium: bloquer après 3 jours
            currentQuestionDay > 3
        }
    }

    // 🔑 CALCUL DE ROUTE PRINCIPAL
    val currentRoute = remember(
        hasConnectedPartner,
        hasSeenIntro,
        shouldShowPaywall,
        currentQuestionDay,
        errorMessage,
        isLoading
    ) {
        val route = DailyContentRouteCalculator.calculateRoute(
            hasConnectedPartner = hasConnectedPartner,
            hasSeenIntro = hasSeenIntro,
            shouldShowPaywall = shouldShowPaywall,
            paywallDay = currentQuestionDay,
            serviceHasError = errorMessage != null,
            serviceErrorMessage = errorMessage,
            serviceIsLoading = isLoading
        )
        
        // 🔍 LOGS DE DEBUG
        android.util.Log.d("DailyQuestionFlow", """
            🎯 Route Calculation:
            - hasConnectedPartner: $hasConnectedPartner
            - hasSeenIntro: $hasSeenIntro
            - shouldShowPaywall: $shouldShowPaywall
            - currentQuestionDay: $currentQuestionDay
            - serviceHasError: ${errorMessage != null}
            - serviceIsLoading: $isLoading
            - partnerId: ${currentUser?.partnerId}
            - introFlags: $introFlags
            → Route: $route
        """.trimIndent())
        
        route
    }

    // 🚀 Initialisation service si nécessaire
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user?.partnerId != null && dailyQuestionRepository != null) {
            // Construire coupleId à partir des UIDs des partenaires (logique iOS)
            val coupleId = listOf(user.id, user.partnerId!!)
                .sorted()
                .joinToString("_")
            
            scope.launch {
                try {
                    dailyQuestionRepository.initializeForCouple(coupleId)
                } catch (e: Exception) {
                    // Gestion d'erreur
                }
            }
        }
    }

    // 🎨 RENDU SELON LA ROUTE CALCULÉE
    when (currentRoute) {
        is DailyContentRoute.Intro -> {
            DailyQuestionIntroScreen(
                showConnectButton = currentRoute.showConnect,
                onConnectPartner = {
                    // 🔥 Afficher popup gestion partenaire au lieu de rediriger
                    activeSheet = DailyQuestionSheetType.PartnerManagement
                },
                onContinue = { 
                    appState.markDailyQuestionIntroAsSeen()
                },
                onNavigateBack = onNavigateBack
            )
        }
        
        is DailyContentRoute.Paywall -> {
            DailyQuestionPaywallScreen(
                questionDay = currentRoute.day,
                onSubscribe = {
                    // 🔥 Afficher popup abonnement au lieu de rediriger  
                    activeSheet = DailyQuestionSheetType.Subscription
                },
                onNavigateBack = onNavigateBack
            )
        }
        
        is DailyContentRoute.Main -> {
            if (dailyQuestionRepository != null) {
                DailyQuestionMainScreen(
                    dailyQuestionRepository = dailyQuestionRepository,
                    onNavigateBack = onNavigateBack
                )
            } else {
                DailyQuestionErrorScreen(
                    message = "Service non disponible",
                    onRetry = { /* Retry logic */ },
                    onNavigateBack = onNavigateBack
                )
            }
        }
        
        is DailyContentRoute.Error -> {
            DailyQuestionErrorScreen(
                message = currentRoute.message,
                onRetry = { 
                    scope.launch {
                        dailyQuestionRepository?.regenerateCurrentQuestion()
                    }
                },
                onNavigateBack = onNavigateBack
            )
        }
        
        DailyContentRoute.Loading -> {
            DailyQuestionLoadingScreen()
        }
    }

    // 🎭 GESTION DES SHEETS/POPUPS
    activeSheet?.let { sheet ->
        when (sheet) {
            DailyQuestionSheetType.PartnerManagement -> {
                val sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true // Ouverture directe à 96%
                )
                
                ModalBottomSheet(
                    onDismissRequest = { activeSheet = null },
                    sheetState = sheetState,
                    modifier = Modifier.fillMaxHeight(0.96f), // 96% de hauteur comme MainScreen
                    windowInsets = WindowInsets(0), // Enlève les insets
                    dragHandle = null, // Pas de handle comme MainScreen
                    containerColor = Color.Transparent // Background transparent
                ) {
                    PartnerManagementScreen(
                        currentPartnerName = AppDelegate.partnerLocationService?.partnerName?.collectAsState()?.value,
                        onConnectWithCode = { code ->
                            // Connexion code gérée par le service  
                        },
                        onDismiss = {
                            activeSheet = null
                        }
                    )
                }
            }
            
            DailyQuestionSheetType.Subscription -> {
                val sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true // Ouverture directe à 96%
                )
                
                ModalBottomSheet(
                    onDismissRequest = { activeSheet = null },
                    sheetState = sheetState,
                    modifier = Modifier.fillMaxHeight(0.96f), // 96% de hauteur comme MainScreen
                    windowInsets = WindowInsets(0), // Enlève les insets
                    dragHandle = null, // Pas de handle comme MainScreen
                    containerColor = Color.Transparent // Background transparent
                ) {
                    val currentFreemiumManager by freemiumManager.collectAsState()
                    val manager = currentFreemiumManager
                    if (manager is com.love2loveapp.services.SimpleFreemiumManager) {
                        SubscriptionScreen(
                            freemiumManager = manager,
                            blockedCategory = null, // Pas de catégorie spécifique bloquée
                            onDismiss = {
                                activeSheet = null
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * ⏳ Écran de chargement selon RAPPORT_DESIGN_QUESTION_DU_JOUR.md
 * Background: RGB(247, 247, 247) - Gris clair uniforme
 * Design identique à la page principale
 */
@Composable
private fun DailyQuestionLoadingScreen() {
    // 🎨 Background principal - RGB(247, 247, 247) selon rapport
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7)) // Gris clair uniforme selon rapport
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 📋 Header Principal identique
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.daily_question_title), // ✅ Clé de traduction
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // État de chargement centré
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFD267A), // Rose Love2Love selon rapport
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    
                    Text(
                        text = stringResource(R.string.daily_question_preparing), // ✅ Clé de traduction
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = stringResource(R.string.daily_question_preparing_subtitle), // ✅ Clé de traduction
                        fontSize = 14.sp,
                        color = Color.Gray.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// 🎭 Types de Sheet pour DailyQuestionFlowScreen
abstract class DailyQuestionSheetType {
    object PartnerManagement : DailyQuestionSheetType()
    object Subscription : DailyQuestionSheetType()
}
