package com.love2loveapp.views

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.sp
import com.love2loveapp.AppDelegate
import com.love2loveapp.R
import com.love2loveapp.models.QuestionCategory
import com.love2loveapp.models.JournalEntry
import com.love2loveapp.views.tutorial.LocationPermissionFlow
import java.util.Date
import com.love2loveapp.views.tutorial.PartnerLocationMessageScreen
import com.love2loveapp.views.tutorial.PartnerManagementScreen
import com.love2loveapp.views.subscription.SubscriptionScreen
import com.love2loveapp.views.widgets.components.WidgetPreviewSection
import com.love2loveapp.views.widgets.WidgetsScreen
import com.love2loveapp.views.widgets.HomeScreenWidgetTutorial
import com.love2loveapp.views.widgets.LockScreenWidgetTutorial
import com.love2loveapp.services.widgets.WidgetRepository
import com.love2loveapp.services.SimpleFreemiumManager
import com.love2loveapp.views.main.MainPagesColors
import com.love2loveapp.views.main.MainPagesDimensions
import com.love2loveapp.views.collections.CollectionColors
import com.love2loveapp.views.collections.CollectionTypography
import com.love2loveapp.views.collections.CollectionDimensions
import com.love2loveapp.views.main.MainPagesTypography
import com.love2loveapp.views.onboarding.GooglePlaySubscriptionStepView
import androidx.compose.ui.zIndex

/**
 * 🏠 Écran principal de l'application Love2Love (HomeContentView)
 * Selon RAPPORT_DESIGN_PAGES_PRINCIPALES.md - Page principale avec design unifié
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val appState = remember { AppDelegate.appState }
    val currentUser by appState.currentUser.collectAsState()
    
    // Accès au WidgetRepository pour les previews
    val widgetRepository = remember { WidgetRepository.getInstance(context) }
    
    // État des sheets/navigation
    var activeSheet by remember { mutableStateOf<SheetType?>(null) }
    
    // État des tutoriels widgets (plein écran)
    var showHomeScreenTutorial by remember { mutableStateOf(false) }
    var showLockScreenTutorial by remember { mutableStateOf(false) }
    
    val hasConnectedPartner by remember(currentUser?.partnerId) {
        mutableStateOf(!currentUser?.partnerId.isNullOrEmpty())
    }
    
    // 💰 FreemiumManager et paywall - Observation automatique
    val freemiumManager by appState.freemiumManager.collectAsState()
    val simpleFreemiumManager = freemiumManager as? SimpleFreemiumManager
    val showingSubscription by (simpleFreemiumManager?.showingSubscriptionFlow?.collectAsState() ?: remember { mutableStateOf(false) })
    val blockedCategory by (simpleFreemiumManager?.blockedCategoryAttempt?.collectAsState() ?: remember { mutableStateOf(null) })
    
    Log.d("MainScreen", "🎯 ETAT PAYWALL:")
    Log.d("MainScreen", "  - freemiumManager: ${freemiumManager?.javaClass?.simpleName}")
    Log.d("MainScreen", "  - showingSubscription: $showingSubscription")
    Log.d("MainScreen", "  - blockedCategory: ${blockedCategory?.id}")
    Log.d("MainScreen", "  - activeSheet: $activeSheet")
    
    // Déclencher automatiquement le paywall quand FreemiumManager le demande
    LaunchedEffect(showingSubscription) {
        Log.d("MainScreen", "🔥 LaunchedEffect showingSubscription: $showingSubscription")
        if (showingSubscription) {
            Log.d("MainScreen", "💰 FreemiumManager demande l'affichage du paywall")
            activeSheet = SheetType.Subscription
            Log.d("MainScreen", "✅ activeSheet défini sur Subscription")
        }
    }
    
    Log.d("MainScreen", "🏠 Affichage écran principal HomeContentView")
    Log.d("MainScreen", "👤 Utilisateur: [USER_MASKED]")
    Log.d("MainScreen", "💑 Partenaire connecté: $hasConnectedPartner")

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Fond selon le rapport: Color(red: 0.97, green: 0.97, blue: 0.98) 
            .background(MainPagesColors.Background)
    ) {
        // --- Fond dégradé rose en haut (height 350dp) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            // Color(hex: "#FD267A") avec opacité décroissante
                            Color(0xFFFD267A).copy(alpha = 0.30f),
                            Color(0xFFFD267A).copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        // --- Contenu scrollable ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp) // Espace pour la navigation du bas
        ) {
            // 1. Section photos de profil + distance partenaire
            PartnerDistanceSection(
                currentUser = currentUser,
                onPartnerAvatarTap = {
                    activeSheet = SheetType.PartnerManagement
                },
                onDistanceTap = { showPartnerMessageOnly ->
                    Log.d("MainScreen", "🎯 CLIC SUR DISTANCE:")
                    Log.d("MainScreen", "  - showPartnerMessageOnly: $showPartnerMessageOnly")
                    Log.d("MainScreen", "  - currentUser: [USER_MASKED]")
                    Log.d("MainScreen", "  - hasPartner: [PARTNER_STATUS_MASKED]")
                    Log.d("MainScreen", "  - userLocation: [LOCATION_MASKED]")
                    
                    activeSheet = if (showPartnerMessageOnly) {
                        Log.d("MainScreen", "  ✅ Navigation vers PartnerLocationMessage")
                        SheetType.PartnerLocationMessage
                    } else {
                        Log.d("MainScreen", "  ✅ Navigation vers LocationPermission")
                        SheetType.LocationPermission
                    }
                },
                modifier = Modifier.padding(top = 60.dp) // Espace réduit depuis la status bar
            )

            // 2. Invitation partenaire (si non connecté)
            if (!hasConnectedPartner) {
                PartnerInviteSection(
                    onInviteTap = {
                        activeSheet = SheetType.PartnerManagement
                    },
                    modifier = Modifier.padding(vertical = 20.dp) // Espace vertical au-dessus et en-dessous
                )
            }

            // 3. CategoryListView selon le rapport
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MainPagesDimensions.HorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp) // spacing: 20 selon le rapport
            ) {
                QuestionCategory.categories.forEach { category ->
                    CategoryListCard(
                        category = category,
                        isSubscribed = currentUser?.isSubscribed ?: false,
                        simpleFreemiumManager = simpleFreemiumManager,
                        onClick = {
                            Log.d("MainScreen", "🔥 Catégorie sélectionnée: ${context.getString(category.titleRes)}")
                            activeSheet = SheetType.Questions(category)
                        }
                    )
                }
            }
            
            // Espace entre collections et widgets
            Spacer(modifier = Modifier.height(40.dp))
            
            // 4. Section Widgets selon le rapport  
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MainPagesDimensions.HorizontalPadding)
            ) {
                // Titre section selon le rapport
                Text(
                    text = stringResource(R.string.widgets),
                    style = MainPagesTypography.SectionTitle,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                WidgetPreviewSection(
                    onWidgetTap = {
                        Log.d("MainScreen", "📱 Carte widget tappée, ouverture de la page widgets")
                        activeSheet = SheetType.Widgets
                    }
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // 5. Statistiques du couple selon le rapport
            CoupleStatisticsSection(
                appState = appState,
                modifier = Modifier.padding(top = MainPagesDimensions.SectionSpacing) // .padding(.top, 30) selon le rapport
            )
        }
    }

    // 🎴 Modal Sheet pour Questions selon iOS - 90% de l'écran, 10% fond visible
    val currentSheet = activeSheet
    if (currentSheet is SheetType.Questions) {
        // State pour ouvrir directement en position étendue
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true // Évite la position mi-hauteur
        )
        
        ModalBottomSheet(
            onDismissRequest = {
                Log.d("MainScreen", "🔙 Dismissal du modal questions: ${context.getString(currentSheet.category.titleRes)}")
                activeSheet = null
            },
            sheetState = sheetState, // State pour contrôler l'ouverture
            modifier = Modifier.fillMaxHeight(0.96f), // 96% de hauteur, 4% fond visible
            windowInsets = WindowInsets(0), // Enlève les insets pour optimiser l'espace
            dragHandle = null, // Pas de handle visible comme dans iOS
            containerColor = Color.Transparent // Background transparent pour le modal
        ) {
            // QuestionListScreen dans le modal - s'adapte à l'espace disponible
            QuestionListScreen(
                category = currentSheet.category,
                onBackPressed = {
                    Log.d("MainScreen", "🔙 Retour depuis questions: ${context.getString(currentSheet.category.titleRes)}")
                    activeSheet = null
                }
            )
        }
    }
    
    // Gestion des autres sheets (non-modal)
    activeSheet?.let { sheet ->
        when (sheet) {
            is SheetType.Questions -> {
                // Géré par ModalBottomSheet ci-dessus
            }
            SheetType.PartnerManagement -> {
                PartnerManagementScreen(
                    currentPartnerName = AppDelegate.partnerLocationService?.partnerName?.collectAsState()?.value,
                    onConnectWithCode = { code ->
                        // Connexion avec code gérée automatiquement par le service
                        Log.d("MainScreen", "Connexion avec code: $code")
                    },
                    onDismiss = {
                        activeSheet = null
                    }
                )
            }
            SheetType.PartnerLocationMessage -> {
                PartnerLocationMessageScreen(
                    hasPartner = !currentUser?.partnerId.isNullOrEmpty(),
                    onSendReminder = {
                        // TODO: Envoyer rappel localisation
                        Log.d("MainScreen", "Envoi rappel localisation partenaire")
                    },
                    onDismiss = {
                        activeSheet = null
                    }
                )
            }
            SheetType.LocationPermission -> {
                val partnerLocationService = AppDelegate.partnerLocationService
                val partnerLocation by (partnerLocationService?.partnerLocation?.collectAsState() ?: remember { mutableStateOf(null) })
                val currentUser by appState.currentUser.collectAsState()
                
                LocationPermissionFlow(
                    onPermissionGranted = {
                        Log.d("MainScreen", "✅ Permission localisation accordée")
                        
                        // Démarrer le service de localisation
                        AppDelegate.locationSyncService?.startLocationSync()
                        
                        // Vérifier si on doit afficher le message pour le partenaire
                        val hasPartner = !currentUser?.partnerId.isNullOrEmpty()
                        val partnerHasLocation = partnerLocation != null
                        
                        Log.d("MainScreen", "📍 ANALYSE POST-PERMISSION:")
                        Log.d("MainScreen", "  - currentUser.name: [USER_MASKED]")
                        Log.d("MainScreen", "  - currentUser.partnerId: [PARTNER_ID_MASKED]")
                        Log.d("MainScreen", "  - currentUser.currentLocation: [LOCATION_MASKED]")
                        Log.d("MainScreen", "  - hasPartner: $hasPartner")
                        Log.d("MainScreen", "  - partnerLocation: [PARTNER_LOCATION_MASKED]")
                        Log.d("MainScreen", "  - partnerHasLocation: $partnerHasLocation")
                        
                        if (hasPartner && !partnerHasLocation) {
                            // Utilisateur a localisation MAIS partenaire non → Message partenaire
                            Log.d("MainScreen", "  ✅ Transition vers PartnerLocationMessage - partenaire sans localisation")
                            activeSheet = SheetType.PartnerLocationMessage
                        } else {
                            // Pas de partenaire ou partenaire a déjà localisation → Fermer
                            Log.d("MainScreen", "  ❌ Localisation complète, fermeture - partenaire: $hasPartner, location: $partnerHasLocation")
                            activeSheet = null
                        }
                    },
                    onDismiss = {
                        activeSheet = null
                    }
                )
            }
            SheetType.LocationTutorial -> {
                LocationPermissionFlow(
                    isFromMenu = true,
                    onPermissionGranted = {
                        Log.d("MainScreen", "Permission localisation depuis menu")
                        activeSheet = null
                    },
                    onDismiss = {
                        activeSheet = null
                    }
                )
            }
            SheetType.Widgets -> {
                // 📱 Page Widgets - ModalBottomSheet 96% comme les collections de cartes
                val sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true // Ouverture directe à 96%
                )
                
                ModalBottomSheet(
                    onDismissRequest = {
                        Log.d("MainScreen", "🔙 Dismissal du modal widgets")
                        activeSheet = null
                    },
                    sheetState = sheetState,
                    modifier = Modifier.fillMaxHeight(0.96f), // 96% comme les collections
                    windowInsets = WindowInsets(0), // Enlève les insets pour optimiser l'espace
                    dragHandle = null, // Pas de handle visible comme dans iOS
                    containerColor = Color.Transparent // Background transparent pour le modal
                ) {
                    WidgetsScreen(
                        onBack = {
                            Log.d("MainScreen", "🔙 Retour depuis widgets")
                            activeSheet = null
                        },
                        onNavigateToHomeScreenTutorial = {
                            Log.d("MainScreen", "📱 Navigation vers tutoriel home screen")
                            activeSheet = null // Fermer le sheet widgets
                            showHomeScreenTutorial = true
                        },
                        onNavigateToLockScreenTutorial = {
                            Log.d("MainScreen", "🔒 Navigation vers tutoriel lock screen")
                            activeSheet = null // Fermer le sheet widgets
                            showLockScreenTutorial = true
                        }
                    )
                }
            }
            SheetType.Subscription -> {
                // 💰 Paywall In-App - ModalBottomSheet 96% comme les questions
                val sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true // Ouverture directe à 96%
                )
                
                ModalBottomSheet(
                    onDismissRequest = {
                        Log.d("MainScreen", "🔙 Dismissal du modal paywall")
                        simpleFreemiumManager?.dismissSubscription()
                        activeSheet = null
                    },
                    sheetState = sheetState,
                    modifier = Modifier.fillMaxHeight(0.96f), // 96% de hauteur comme demandé
                    windowInsets = WindowInsets(0),
                    dragHandle = null,
                    containerColor = Color.Transparent
                ) {
                    // Paywall avec design identique à l'onboarding
                    GooglePlaySubscriptionStepView(
                        activity = context as ComponentActivity,
                        onComplete = {
                            Log.d("MainScreen", "✅ Abonnement Google Play validé depuis collection premium")
                            simpleFreemiumManager?.dismissSubscription()
                            activeSheet = null
                        },
                        onSkip = {
                            Log.d("MainScreen", "❌ Paywall fermé - pas d'achat")
                            simpleFreemiumManager?.dismissSubscription()
                            activeSheet = null
                        }
                    )
                }
            }
            else -> {
                Log.w("MainScreen", "Type de sheet non supporté: $sheet")
                activeSheet = null
            }
        }
    }
    
    // 📱 Overlays tutoriels widgets en plein écran
    if (showHomeScreenTutorial) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .windowInsetsPadding(WindowInsets(0)) // Ignore les system bars pour plein écran
                .zIndex(1f) // Z-index pour s'assurer qu'il est au-dessus des autres éléments
        ) {
            HomeScreenWidgetTutorial(
                onFinish = {
                    Log.d("MainScreen", "✅ Tutoriel home screen widget terminé")
                    showHomeScreenTutorial = false
                    activeSheet = SheetType.Widgets // Revenir au sheet widgets
                },
                onSkip = {
                    Log.d("MainScreen", "⏭️ Tutoriel home screen widget sauté")
                    showHomeScreenTutorial = false
                    activeSheet = SheetType.Widgets // Revenir au sheet widgets
                }
            )
        }
    }
    
    if (showLockScreenTutorial) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .windowInsetsPadding(WindowInsets(0)) // Ignore les system bars pour plein écran
                .zIndex(1f) // Z-index pour s'assurer qu'il est au-dessus des autres éléments
        ) {
            LockScreenWidgetTutorial(
                onFinish = {
                    Log.d("MainScreen", "✅ Tutoriel lock screen widget terminé")
                    showLockScreenTutorial = false
                    activeSheet = SheetType.Widgets // Revenir au sheet widgets
                },
                onSkip = {
                    Log.d("MainScreen", "⏭️ Tutoriel lock screen widget sauté")
                    showLockScreenTutorial = false
                    activeSheet = SheetType.Widgets // Revenir au sheet widgets
                }
            )
        }
    }
}

// --- Types de destination/sheet (équivalent iOS SheetType) ---
// Solution compatible Android : Abstract class au lieu de sealed class
abstract class SheetType {
    data class Questions(val category: QuestionCategory) : SheetType()
    
    object PartnerManagement : SheetType()
    object PartnerLocationMessage : SheetType()
    object LocationPermission : SheetType()
    object LocationTutorial : SheetType()
    object Widgets : SheetType()
    object Subscription : SheetType() // 💰 Paywall in-app
    
    // Helper pour identifier le type (optionnel)
    override fun toString(): String = when (this) {
        is Questions -> "Questions(${category})"
        is PartnerManagement -> "PartnerManagement"
        is PartnerLocationMessage -> "PartnerLocationMessage"
        is LocationPermission -> "LocationPermission"
        is LocationTutorial -> "LocationTutorial"
        is Widgets -> "Widgets"
        is Subscription -> "Subscription"
        else -> "Unknown SheetType"
    }
}

// --- Composants de sections ---

@Composable
fun PartnerDistanceSection(
    currentUser: com.love2loveapp.models.User?,
    onPartnerAvatarTap: () -> Unit,
    onDistanceTap: (showPartnerMessageOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Utiliser le nouveau HeaderProfileDistance
    com.love2loveapp.views.components.HeaderProfileDistance(
        currentUser = currentUser,
        onPartnerAvatarTap = onPartnerAvatarTap,
        onDistanceTap = onDistanceTap,
        modifier = modifier
    )
}

@Composable
fun PartnerInviteSection(
    onInviteTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Design exact selon RAPPORT_INVITE_PARTENAIRE.md
    Card(
        onClick = onInviteTap,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f) // .fill(Color.white.opacity(0.95))
        ),
        shape = RoundedCornerShape(16.dp), // cornerRadius: 16
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // shadow(radius: 8, x: 0, y: 2)
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp) // Padding externe
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = CollectionDimensions.SpaceXXL, vertical = CollectionDimensions.SpaceXL), // Même padding que les cards de catégories
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CollectionDimensions.SpaceL) // Même spacing que les cards de catégories
        ) {
            // Icône cœur avec rayons selon le document
            HeartWithRaysIcon(
                modifier = Modifier.size(50.dp) // .frame(width: 50, height: 50)
            )

            // Texte invitation
            Column(
                modifier = Modifier.weight(1f), // Spacer() équivalent
                verticalArrangement = Arrangement.spacedBy(CollectionDimensions.SpaceXS) // Même spacing que les cards de catégories (6.dp)
            ) {
                Text(
                    text = stringResource(R.string.invite_partner), // "invite_partner".localized
                    style = CollectionTypography.H4.copy(
                        color = CollectionColors.OnSurface,
                        fontSize = 16.sp // Taille réduite pour tenir sur une ligne
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip // Pas de ellipsis, texte complet s'affiche
                )

                Text(
                    text = stringResource(R.string.invite_partner_description), // "invite_partner_description".localized
                    style = CollectionTypography.Caption.copy(color = CollectionColors.OnSurfaceVariant), // Même style que les sous-titres des cards de catégories
                    maxLines = 2, // Réduire à 2 lignes pour la compacité
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Flèche droite
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight, // Équivalent de chevron.right
                contentDescription = null,
                tint = Color(0xFFFD267A), // .foregroundColor(Color(hex: "#FD267A"))
                modifier = Modifier.size(16.dp) // .font(.system(size: 16, weight: .semibold))
            )
        }
    }
}

@Composable
fun HeartWithRaysIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 8 rayons disposés à 45° chacun selon le document
        repeat(8) { index ->
            Box(
                modifier = Modifier
                    .size(width = 2.dp, height = 8.dp) // .frame(width: 2, height: 8)
                    .offset(y = (-20).dp) // .offset(y: -20)
                    .rotate(index * 45f) // .rotationEffect(.degrees(Double(index) * 45))
                    .background(
                        Color(0xFFFD267A).copy(alpha = 0.6f), // Color(hex: "#FD267A").opacity(0.6)
                        RectangleShape
                    )
            )
        }

        // Cœur central
        Icon(
            imageVector = Icons.Default.Favorite, // Image(systemName: "heart.fill")
            contentDescription = null,
            tint = Color(0xFFFD267A), // .foregroundColor(Color(hex: "#FD267A"))
            modifier = Modifier.size(24.dp) // .font(.system(size: 24))
        )
    }
}

// === CATEGORY LIST CARD VIEW SELON RAPPORT_DESIGN_COLLECTIONS_CARTES.md ===
@Composable
fun CategoryListCard(
    category: QuestionCategory,
    isSubscribed: Boolean,
    simpleFreemiumManager: SimpleFreemiumManager?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = stringResource(id = category.titleRes)
    val subtitle = stringResource(id = category.subtitleRes)

    // Rectangle HStack(spacing: 16) selon le rapport
    Card(
        onClick = {
            Log.d("CategoryListCard", "🔥 Tap détecté sur $title")
            if (simpleFreemiumManager != null) {
                simpleFreemiumManager.handleCategoryTap(category) { onClick() }
            } else {
                onClick()
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f) // opacity(0.95) selon le rapport
        ),
        shape = RoundedCornerShape(CollectionDimensions.CornerRadiusCards), // cornerRadius(16) selon le rapport
        elevation = CardDefaults.cardElevation(
            defaultElevation = CollectionDimensions.ElevationLight // shadow(radius: 8, x: 0, y: 2) selon le rapport
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CollectionDimensions.SpaceXXL, vertical = CollectionDimensions.SpaceXL), // padding selon le rapport
            horizontalArrangement = Arrangement.spacedBy(CollectionDimensions.SpaceL), // spacing: 16 selon le rapport
            verticalAlignment = Alignment.CenterVertically
        ) {
            // VStack(alignment: .leading, spacing: 6) selon le rapport
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CollectionDimensions.SpaceXS) // spacing: 6 selon le rapport
            ) {
                // Titre principal avec H4 selon le rapport
                Text(
                    text = title,
                    style = CollectionTypography.H4.copy(color = CollectionColors.OnSurface) // font(.system(size: 20, weight: .bold)) selon le rapport
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CollectionDimensions.SpaceXXS)
                ) {
                    // Sous-titre avec Caption selon le rapport
                    Text(
                        text = subtitle,
                        style = CollectionTypography.Caption.copy(color = CollectionColors.OnSurfaceVariant),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Cadenas premium selon le rapport
                    if (category.isPremium && !isSubscribed) {
                        Text(
                            text = "🔒",
                            style = CollectionTypography.Caption
                        )
                    }
                }
            }

            // Emoji selon le rapport (font(.system(size: 28)))
            Text(
                text = category.emoji,
                fontSize = 28.sp
            )
        }
    }
}

// === WIDGET PREVIEW SECTION SELON LE RAPPORT ===
@Composable
fun WidgetPreviewSection(
    onWidgetTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        onClick = onWidgetTap,
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MainPagesColors.Surface.copy(alpha = 0.95f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.add_widgets),
                    style = MainPagesTypography.SectionTitle.copy(fontSize = 18.sp)
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = stringResource(R.string.feel_closer_partner),
                    style = MainPagesTypography.Caption
                )
            }

            androidx.compose.material3.Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Go to widgets",
                tint = Color.Gray
            )
        }
    }
}

// === COUPLE STATISTICS VIEW SELON LE RAPPORT ===
@Composable
fun CoupleStatisticsSection(
    appState: com.love2loveapp.models.AppState,
    modifier: Modifier = Modifier
) {
    val currentUser by appState.currentUser.collectAsState()
    val context = LocalContext.current
    
    // Accès aux services et repositories
    val journalRepository = AppDelegate.journalRepository
    val entries: List<JournalEntry> by journalRepository?.entries?.collectAsState(emptyList()) 
        ?: remember { mutableStateOf(emptyList()) }
    
    // 1️⃣ CALCUL JOURS ENSEMBLE (selon rapport ligne 236-243)
    val daysTogetherCount = remember(currentUser) {
        currentUser?.relationshipStartDate?.let { startDate ->
            val calendar = java.util.Calendar.getInstance()
            val currentTime = calendar.timeInMillis
            val startTime = startDate.time
            val daysDiff = (currentTime - startTime) / (1000L * 60L * 60L * 24L)
            maxOf(daysDiff.toInt(), 0)
        } ?: 0
    }
    
    // 2️⃣ CALCUL PROGRESSION QUESTIONS (selon rapport ligne 258-279)
    val questionsProgressPercentage = remember {
        try {
            val categoryProgressService = com.love2loveapp.services.CategoryProgressService.getInstance(context)
            categoryProgressService.calculateGlobalProgressPercentage()
        } catch (e: Exception) {
            0.0
        }
    }
    
    // 3️⃣ CALCUL VILLES VISITÉES (selon rapport ligne 295-301)
    val citiesVisitedCount = remember(entries) {
        val uniqueCities = entries.mapNotNull { entry: JournalEntry ->
            entry.location?.city?.trim()
        }.filter { city: String -> city.isNotEmpty() }.toSet()
        
        uniqueCities.size
    }
    
    // 4️⃣ CALCUL PAYS VISITÉS (selon rapport ligne 304-310)
    val countriesVisitedCount = remember(entries) {
        val uniqueCountries = entries.mapNotNull { entry: JournalEntry ->
            entry.location?.country?.trim()
        }.filter { country: String -> country.isNotEmpty() }.toSet()
        
        uniqueCountries.size
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Titre selon la documentation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.couple_statistics),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Grille 2x2 avec Column et Row au lieu de LazyVerticalGrid
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Première rangée
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1️⃣ Carte "Jours Ensemble" (Rose) - VRAIES DONNÉES
                StatisticCard(
                    title = stringResource(R.string.days_together),
                    value = daysTogetherCount.toString(),
                    iconRes = R.drawable.jours, // ✅ jours.png selon documentation
                    iconColor = Color(0xFFfeb5c8),
                    backgroundColor = Color(0xFFfedce3),
                    textColor = Color(0xFFdb3556),
                    modifier = Modifier.weight(1f)
                )
                
                // 2️⃣ Carte "Questions Répondues" (Orange) - VRAIES DONNÉES
                StatisticCard(
                    title = stringResource(R.string.questions_answered),
                    value = "${questionsProgressPercentage.toInt()}%",
                    iconRes = R.drawable.qst, // ✅ qst.png selon documentation
                    iconColor = Color(0xFFfed397),
                    backgroundColor = Color(0xFFfde9cf),
                    textColor = Color(0xFFffa229),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Deuxième rangée
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 3️⃣ Carte "Villes Visitées" (Bleu) - VRAIES DONNÉES
                StatisticCard(
                    title = stringResource(R.string.cities_visited),
                    value = citiesVisitedCount.toString(),
                    iconRes = R.drawable.ville, // ✅ ville.png selon documentation
                    iconColor = Color(0xFFb0d6fe),
                    backgroundColor = Color(0xFFdbecfd),
                    textColor = Color(0xFF0a85ff),
                    modifier = Modifier.weight(1f)
                )
                
                // 4️⃣ Carte "Pays Visités" (Violet) - VRAIES DONNÉES
                StatisticCard(
                    title = stringResource(R.string.countries_visited),
                    value = countriesVisitedCount.toString(),
                    iconRes = R.drawable.pays, // ✅ pays.png selon documentation
                    iconColor = Color(0xFFd1b3ff),
                    backgroundColor = Color(0xFFe8dcff),
                    textColor = Color(0xFF7c3aed),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatisticCard(
    title: String,
    value: String,
    iconRes: Int,
    iconColor: Color,
    backgroundColor: Color,
    textColor: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier
            .height(140.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Icône top-right
            // Utiliser Image pour les PNG couleur (l'Icon applique un tint et masque l'image)
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            
            // Valeur + titre bottom-left
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.7f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Valeur
                Text(
                    text = if (value.isEmpty()) "—" else value,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Titre
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

