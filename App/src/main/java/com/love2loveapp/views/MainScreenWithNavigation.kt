package com.love2loveapp.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.love2loveapp.AppDelegate
import com.love2loveapp.views.favorites.FavoritesScreen
import com.love2loveapp.views.dailyquestion.DailyQuestionFlowScreen
import com.love2loveapp.views.dailychallenge.DailyChallengeFlowScreen
import com.love2loveapp.views.savedchallenges.SavedChallengesScreen
import com.love2loveapp.views.journal.JournalScreen
import com.love2loveapp.views.journal.JournalMapScreen
import com.love2loveapp.views.journal.CreateJournalEntryScreen
import com.love2loveapp.views.profile.ProfileScreen
import com.love2loveapp.views.widgets.WidgetsScreen
import com.love2loveapp.views.widgets.HomeScreenWidgetTutorial
import com.love2loveapp.views.widgets.LockScreenWidgetTutorial
import com.love2loveapp.views.navigation.Love2LoveBottomNavigation
import com.love2loveapp.views.navigation.NavigationDestination

/**
 * 📱 MainScreenWithNavigation - Écran principal avec navigation
 * 
 * Gère la navigation entre :
 * - MainScreen (Questions principales)
 * - DailyQuestionFlowScreen (Questions du jour avec chat) 
 * - FavoritesScreen (Favoris partagés)
 * 
 * Utilise Bottom Navigation Material 3
 * N'apparaît QUE dans l'app (pas dans l'onboarding)
 */
@Composable
fun MainScreenWithNavigation() {
    // État de navigation
    var currentDestination by remember { mutableStateOf(NavigationDestination.MAIN) }
    
    // État pour affichage SavedChallengesScreen
    var showingSavedChallenges by remember { mutableStateOf(false) }
    
    // États navigation Journal
    var showingJournalMap by remember { mutableStateOf(false) }
    var showingCreateJournalEntry by remember { mutableStateOf(false) }
    
    // États navigation Profile
    var showingPartnerManagement by remember { mutableStateOf(false) }
    var showingWidgetsSheet by remember { mutableStateOf(false) }
    
    // 📍 État pour tutoriel localisation avec sheets (comme MainScreen)
    var activeLocationSheet by remember { mutableStateOf<LocationSheetType?>(null) }
    
    // Context pour permissions
    val context = LocalContext.current
    
    // États navigation Widgets
    var showingHomeScreenTutorial by remember { mutableStateOf(false) }
    var showingLockScreenTutorial by remember { mutableStateOf(false) }
    
    // Accès au FavoritesRepository
    val favoritesRepository = remember { AppDelegate.favoritesRepository }
    
    // Observer le nombre de favoris pour le badge
    val favoritesCount by if (favoritesRepository != null) {
        favoritesRepository.favoriteQuestions.collectAsState()
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Contenu principal selon la destination
        Box(
            modifier = Modifier.weight(1f)
        ) {
            when (currentDestination) {
                NavigationDestination.MAIN -> {
                    // Écran principal avec questions
                    MainScreen()
                }
                
                NavigationDestination.DAILY_QUESTIONS -> {
                    // 📅 Questions du jour avec chat
                    DailyQuestionFlowScreen(
                        onNavigateToPartnerConnection = {
                            // TODO: Navigation vers écran de connexion partenaire
                            currentDestination = NavigationDestination.MAIN
                        },
                        onNavigateToSubscription = {
                            // TODO: Navigation vers écran d'abonnement
                            currentDestination = NavigationDestination.MAIN
                        },
                        onNavigateBack = { 
                            currentDestination = NavigationDestination.MAIN 
                        }
                    )
                }
                
                NavigationDestination.DAILY_CHALLENGES -> {
                    if (showingSavedChallenges) {
                        // 🔖 Écran des défis sauvegardés
                        SavedChallengesScreen(
                            onBackPressed = {
                                showingSavedChallenges = false
                            }
                        )
                    } else {
                        // 🎯 Défis du jour avec actions
                        DailyChallengeFlowScreen(
                            onNavigateToPartnerConnection = {
                                // TODO: Navigation vers écran de connexion partenaire
                                currentDestination = NavigationDestination.MAIN
                            },
                            onNavigateToSubscription = {
                                // TODO: Navigation vers écran d'abonnement
                                currentDestination = NavigationDestination.MAIN
                            },
                            onNavigateBack = { 
                                currentDestination = NavigationDestination.MAIN 
                            },
                            onNavigateToSavedChallenges = {
                                // Initialiser le repository si nécessaire
                                val savedChallengesRepository = AppDelegate.savedChallengesRepository
                                val currentUser = AppDelegate.appState.currentUser.value
                                
                                if (savedChallengesRepository != null && currentUser != null) {
                                    savedChallengesRepository.initializeForUser(currentUser.id)
                                }
                                
                                showingSavedChallenges = true
                            }
                        )
                    }
                }
                
                NavigationDestination.JOURNAL -> {
                    // 📔 Journal événements partagé
                    if (showingJournalMap) {
                        JournalMapScreen(
                            onBackPressed = { 
                                showingJournalMap = false 
                            }
                        )
                    } else if (showingCreateJournalEntry) {
                        CreateJournalEntryScreen(
                            onDismiss = { 
                                showingCreateJournalEntry = false 
                            }
                        )
                    } else {
                        JournalScreen(
                            onNavigateToMap = {
                                showingJournalMap = true
                            },
                            onNavigateToCreateEntry = {
                                showingCreateJournalEntry = true
                            }
                        )
                    }
                }
                
                NavigationDestination.FAVORITES -> {
                    // Écran favoris partagés
                    if (favoritesRepository != null) {
                        FavoritesScreen(
                            favoritesRepository = favoritesRepository,
                            onNavigateBack = { 
                                currentDestination = NavigationDestination.MAIN 
                            },
                            onShowListView = {
                                // TODO: Implémenter vue liste des favoris
                            }
                        )
                    } else {
                        // Fallback si repository non initialisé
                        ErrorFavoritesScreen(
                            onNavigateBack = { 
                                currentDestination = NavigationDestination.MAIN 
                            }
                        )
                    }
                }
                
                NavigationDestination.PROFILE -> {
                    // 👤 Écran profil utilisateur
                    if (showingPartnerManagement) {
                        // 🤝 Écran de gestion partenaire (réutilisation existante)
                        com.love2loveapp.views.tutorial.PartnerManagementScreen(
                            onDismiss = { 
                                showingPartnerManagement = false 
                            }
                        )
                    } else if (activeLocationSheet != null) {
                        // 📍 Tutoriel localisation avec sheets sophistiqués (comme MainScreen)
                        LocationTutorialWithSheets(
                            activeSheet = activeLocationSheet,
                            onDismiss = { 
                                activeLocationSheet = null 
                            }
                        )
                    } else {
                        ProfileScreen(
                            onNavigateToPartnerManagement = {
                                showingPartnerManagement = true
                            },
                            onNavigateToLocationTutorial = {
                                // 📍 LOGIQUE DIFFÉRENTE DU MAINSCREEN : Menu doit être simple
                                // Si permission déjà accordée → Directement message partenaire
                                // Sinon → Demande permission
                                val hasLocationPermission = context.let {
                                    (ContextCompat.checkSelfPermission(it, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                                }
                                
                                if (hasLocationPermission) {
                                    Log.d("MainScreenWithNavigation", "📍 Permission déjà accordée → Directement message partenaire")
                                    activeLocationSheet = LocationSheetType.PartnerLocationMessage
                                } else {
                                    Log.d("MainScreenWithNavigation", "📍 Permission manquante → Demande permission")
                                    activeLocationSheet = LocationSheetType.LocationPermission
                                }
                            },
                            onNavigateToWidgets = {
                                showingWidgetsSheet = true
                            },
                            onBack = { 
                                currentDestination = NavigationDestination.MAIN 
                            }
                        )
                    }
                }
            }
        }
    
    // 📱 MODAL SHEET WIDGETS 96% (même logique que MainScreen.kt)
    if (showingWidgetsSheet) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true // Ouverture directe à 96%
        )
        
        ModalBottomSheet(
            onDismissRequest = {
                Log.d("MainScreenWithNavigation", "🔙 Dismissal du modal widgets depuis menu")
                showingWidgetsSheet = false
            },
            sheetState = sheetState,
            modifier = Modifier.fillMaxHeight(0.96f), // 96% comme page principale
            windowInsets = WindowInsets(0), // Enlève les insets pour optimiser l'espace
            dragHandle = null, // Pas de handle visible comme dans iOS
            containerColor = Color.Transparent // Background transparent pour le modal
        ) {
            WidgetsScreen(
                onBack = {
                    Log.d("MainScreenWithNavigation", "🔙 Retour depuis widgets modal menu")
                    showingWidgetsSheet = false
                },
                onNavigateToHomeScreenTutorial = {
                    Log.d("MainScreenWithNavigation", "📱 Navigation vers tutoriel home screen depuis menu")
                    showingWidgetsSheet = false // Fermer le sheet widgets
                    showingHomeScreenTutorial = true
                },
                onNavigateToLockScreenTutorial = {
                    Log.d("MainScreenWithNavigation", "🔒 Navigation vers tutoriel lock screen depuis menu")
                    showingWidgetsSheet = false // Fermer le sheet widgets
                    showingLockScreenTutorial = true
                }
            )
        }
    }
    
    // 📱 OVERLAYS TUTORIELS WIDGETS EN PLEIN ÉCRAN (au-dessus du modal)
    if (showingHomeScreenTutorial) {
        HomeScreenWidgetTutorial(
            onFinish = { 
                showingHomeScreenTutorial = false
            },
            onSkip = { 
                showingHomeScreenTutorial = false
            }
        )
    }
    
    if (showingLockScreenTutorial) {
        LockScreenWidgetTutorial(
            onFinish = { 
                showingLockScreenTutorial = false
            },
            onSkip = { 
                showingLockScreenTutorial = false
            }
        )
    }
        
        // Bottom Navigation
        Love2LoveBottomNavigation(
            currentDestination = currentDestination,
            favoritesCount = favoritesCount.size,
            onDestinationSelected = { destination ->
                currentDestination = destination
                
                // Initialiser le repository si nécessaire lors du premier accès aux favoris
                if (destination == NavigationDestination.FAVORITES && favoritesRepository != null) {
                    val currentUser = AppDelegate.appState.currentUser.value
                    if (currentUser != null) {
                        favoritesRepository.initialize(
                            userId = currentUser.id,
                            userName = currentUser.name
                        )
                    }
                }
                
                // Initialiser ProfileRepository lors du premier accès au profil
                if (destination == NavigationDestination.PROFILE) {
                    val profileRepository = AppDelegate.profileRepository
                    val currentUser = AppDelegate.appState.currentUser.value
                    if (profileRepository != null && currentUser != null) {
                        profileRepository.initializeForUser(currentUser.id)
                    }
                }
            }
        )
    }
}

/**
 * ❌ Écran d'erreur pour les favoris
 * Affiché si le FavoritesRepository n'est pas initialisé
 */
@Composable
private fun ErrorFavoritesScreen(
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "❌",
            fontSize = 48.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Service favoris non disponible",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Le service de favoris n'a pas pu être initialisé. Veuillez redémarrer l'application.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onNavigateBack
        ) {
            Text("Retour")
        }
    }
}

/**
 * 📍 Types de sheets pour tutoriel localisation - ENUM CLASS pour compatibilité Android
 */
private enum class LocationSheetType {
    LocationPermission,
    PartnerLocationMessage
}

/**
 * 📍 LocationTutorialWithSheets - Tutoriel localisation sophistiqué
 * 
 * Utilise exactement le même système que MainScreen.kt avec:
 * - LocationPermissionFlow pour demander les permissions
 * - PartnerLocationMessageScreen pour le message partenaire 
 * - Logique complète de transition entre les écrans
 */
@Composable
private fun LocationTutorialWithSheets(
    activeSheet: LocationSheetType?,
    onDismiss: () -> Unit
) {
    val appState = AppDelegate.appState
    val partnerLocationService = AppDelegate.partnerLocationService
    val partnerLocation by (partnerLocationService?.partnerLocation?.collectAsState() ?: remember { mutableStateOf(null) })
    val currentUser by appState.currentUser.collectAsState()
    
    var currentSheet by remember { mutableStateOf(activeSheet) }
    
    // Effet pour suivre le changement de activeSheet depuis le parent
    LaunchedEffect(activeSheet) {
        currentSheet = activeSheet
    }
    
    currentSheet?.let { sheet ->
        when (sheet) {
            LocationSheetType.LocationPermission -> {
                com.love2loveapp.views.tutorial.LocationPermissionFlow(
                    isFromMenu = true,
                    onPermissionGranted = {
                        android.util.Log.d("MainScreenWithNavigation", "✅ Permission localisation accordée depuis menu")
                        
                        // Démarrer le service de localisation  
                        AppDelegate.locationSyncService?.startLocationSync()
                        
                        // Appliquer la même logique que MainScreen.kt
                        val hasPartner = !currentUser?.partnerId.isNullOrEmpty()
                        val partnerHasLocation = partnerLocation != null
                        
                        android.util.Log.d("MainScreenWithNavigation", "📍 ANALYSE POST-PERMISSION MENU:")
                        android.util.Log.d("MainScreenWithNavigation", "  - currentUser.name: ${currentUser?.name}")
                        android.util.Log.d("MainScreenWithNavigation", "  - hasPartner: $hasPartner")
                        android.util.Log.d("MainScreenWithNavigation", "  - partnerHasLocation: $partnerHasLocation")
                        
                        // 📍 DEPUIS LE MENU : Toujours afficher message partenaire (peu importe si partenaire connecté)
                        android.util.Log.d("MainScreenWithNavigation", "  ✅ Depuis menu → Toujours afficher message partenaire")
                        currentSheet = LocationSheetType.PartnerLocationMessage
                    },
                    onDismiss = {
                        onDismiss()
                    }
                )
            }
            LocationSheetType.PartnerLocationMessage -> {
                com.love2loveapp.views.tutorial.PartnerLocationMessageScreen(
                    hasPartner = !currentUser?.partnerId.isNullOrEmpty(),
                    onSendReminder = {
                        // TODO: Envoyer rappel localisation
                        android.util.Log.d("MainScreenWithNavigation", "Envoi rappel localisation partenaire depuis menu")
                    },
                    onDismiss = {
                        onDismiss()
                    }
                )
            }
        }
    }
}

