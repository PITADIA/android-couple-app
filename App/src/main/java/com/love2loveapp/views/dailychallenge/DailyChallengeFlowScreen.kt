package com.love2loveapp.views.dailychallenge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.DailyContentRoute
import com.love2loveapp.models.DailyContentRouteCalculator
import com.love2loveapp.views.tutorial.PartnerManagementScreen
import com.love2loveapp.views.subscription.SubscriptionScreen
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.love2loveapp.R

/**
 * 🎯 DailyChallengeFlowScreen - Router Principal Défis du Jour
 * Équivalent iOS DailyChallengeFlowView.swift
 * 
 * Implémente la logique de navigation conditionnelle complexe :
 * 1. Vérification connexion partenaire (OBLIGATOIRE)
 * 2. Affichage intro si première fois
 * 3. Vérification freemium (3 jours gratuits)  
 * 4. Route vers paywall après limite
 * 5. Interface défis principale avec cartes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeFlowScreen(
    onNavigateToPartnerConnection: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSavedChallenges: () -> Unit
) {
    // 🎭 État des sheets/popups
    var activeSheet by remember { mutableStateOf<DailyChallengeSheetType?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 🔥 Services depuis AppDelegate
    val appState = AppDelegate.appState
    val dailyChallengeRepository = AppDelegate.dailyChallengeRepository
    val freemiumManager = appState.freemiumManager
    
    // 📊 États observés
    val currentUser by appState.currentUser.collectAsStateWithLifecycle()
    val introFlags by appState.introFlags.collectAsStateWithLifecycle()
    val currentChallenge by (dailyChallengeRepository?.currentChallenge?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    val currentSettings by (dailyChallengeRepository?.currentSettings?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    val isLoading by (dailyChallengeRepository?.isLoading?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) })
    val errorMessage by (dailyChallengeRepository?.errorMessage?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })

    // 🔧 Calculs dérivés selon la logique iOS
    val hasConnectedPartner = remember(currentUser) {
        val partnerId = currentUser?.partnerId?.trim()
        !partnerId.isNullOrEmpty()
    }
    val hasSeenIntro = introFlags.dailyChallenge
    val currentChallengeDay = currentSettings?.calculateExpectedDay() ?: 1
    val shouldShowPaywall = remember(currentUser, currentChallengeDay) {
        if (currentUser?.isSubscribed == true) {
            false
        } else {
            // Logique freemium: bloquer après 3 jours
            currentChallengeDay > 3
        }
    }

    // 🔑 CALCUL DE ROUTE PRINCIPAL (réutilise DailyContentRoute)
    val currentRoute = remember(
        hasConnectedPartner,
        hasSeenIntro,
        shouldShowPaywall,
        currentChallengeDay,
        errorMessage,
        isLoading
    ) {
        DailyContentRouteCalculator.calculateRoute(
            hasConnectedPartner = hasConnectedPartner,
            hasSeenIntro = hasSeenIntro,
            shouldShowPaywall = shouldShowPaywall,
            paywallDay = currentChallengeDay,
            serviceHasError = errorMessage != null,
            serviceErrorMessage = errorMessage,
            serviceIsLoading = isLoading
        )
    }

    // 🚀 Initialisation service si nécessaire
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user?.partnerId != null && dailyChallengeRepository != null) {
            // Construire coupleId à partir des UIDs des partenaires (logique iOS)
            val coupleId = listOf(user.id, user.partnerId!!)
                .sorted()
                .joinToString("_")
            
            scope.launch {
                try {
                    dailyChallengeRepository.initializeForCouple(coupleId)
                } catch (e: Exception) {
                    // Gestion d'erreur
                }
            }
        }
    }

    // 🎨 RENDU SELON LA ROUTE CALCULÉE
    when (currentRoute) {
        is DailyContentRoute.Intro -> {
            DailyChallengeIntroScreen(
                showConnectButton = currentRoute.showConnect,
                onConnectPartner = {
                    // 🔥 Afficher popup gestion partenaire au lieu de rediriger
                    activeSheet = DailyChallengeSheetType.PartnerManagement
                },
                onContinue = { 
                    appState.markDailyChallengeIntroAsSeen()
                },
                onNavigateBack = onNavigateBack
            )
        }
        
        is DailyContentRoute.Paywall -> {
            DailyChallengePaywallScreen(
                challengeDay = currentRoute.day,
                onSubscribe = {
                    // 🔥 Afficher popup abonnement au lieu de rediriger  
                    activeSheet = DailyChallengeSheetType.Subscription
                },
                onNavigateBack = onNavigateBack
            )
        }
        
        is DailyContentRoute.Main -> {
            if (dailyChallengeRepository != null) {
                DailyChallengeMainScreen(
                    dailyChallengeRepository = dailyChallengeRepository,
                    onNavigateBack = onNavigateBack,
                    onNavigateToSavedChallenges = onNavigateToSavedChallenges
                )
            } else {
                DailyChallengeErrorScreen(
                    message = "Service non disponible",
                    onRetry = { /* Retry logic */ },
                    onNavigateBack = onNavigateBack
                )
            }
        }
        
        is DailyContentRoute.Error -> {
            DailyChallengeErrorScreen(
                message = currentRoute.message,
                onRetry = { 
                    scope.launch {
                        dailyChallengeRepository?.regenerateCurrentChallenge()
                    }
                },
                onNavigateBack = onNavigateBack
            )
        }
        
        DailyContentRoute.Loading -> {
            DailyChallengeLoadingScreen()
        }
    }

    // 🎭 GESTION DES SHEETS/POPUPS
    activeSheet?.let { sheet ->
        when (sheet) {
            DailyChallengeSheetType.PartnerManagement -> {
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
            
            DailyChallengeSheetType.Subscription -> {
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
 * ⏳ Écran de chargement selon RAPPORT_DEFIS_DU_JOUR.md
 * Background: RGB(247, 247, 247) - Gris clair uniforme (identique Questions)
 * Design identique à la page principale
 */
@Composable
private fun DailyChallengeLoadingScreen() {
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
                    text = stringResource(R.string.daily_challenges_title), // ✅ Clé de traduction
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
                        text = stringResource(R.string.daily_challenge_preparing),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.daily_challenge_preparing_subtitle),
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// 🎭 Types de Sheet pour DailyChallengeFlowScreen
abstract class DailyChallengeSheetType {
    object PartnerManagement : DailyChallengeSheetType()
    object Subscription : DailyChallengeSheetType()
}
