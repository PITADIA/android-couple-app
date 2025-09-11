@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.love2love.ui

/*
 * Jetpack Compose port of SwiftUI MainView.
 *
 * ✅ i18n — strings.xml usage
 *   - Inside Composables: use stringResource(R.string.your_key)
 *   - Outside Composables: use context.getString(R.string.your_key)
 *
 * Example strings.xml entries you may need:
 * <resources>
 *     <string name="premium_categories_subscribed">Toutes les catégories sont débloquées.</string>
 *     <string name="premium_categories_not_subscribed">Certaines catégories sont Premium.</string>
 *     <string name="parametres">Paramètres</string>
 * </resources>
 *
 * Icons expected in /res/drawable/ : home, star, heart, profile
 */

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.analytics.FirebaseAnalytics
import com.love2love.R
import kotlinx.coroutines.flow.collectLatest

private const val TAG = "MainView"
private val LightBackground = Color(0xFFF7F7FA) // ~ (0.97, 0.97, 0.98)
private val PrimaryPink = Color(0xFFFD267A)
private val IconGrey = Color(0xFF9E9E9E)

// --- Sheet routing -----------------------------------------------------------
sealed class SheetType {
    data class Questions(val category: QuestionCategory) : SheetType()
    data object Menu : SheetType()
    data object Subscription : SheetType()
    data object Favorites : SheetType()
    data object Journal : SheetType()
    data object Widgets : SheetType()
    data object WidgetTutorial : SheetType()
    data object PartnerManagement : SheetType()
    data object LocationPermission : SheetType()
    data object PartnerLocationMessage : SheetType()
    data object EventsMap : SheetType()
    data object LocationTutorial : SheetType()
    data object DailyQuestionPermission : SheetType()
}

@Composable
fun MainView(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val analytics = remember(context) { FirebaseAnalytics.getInstance(context) }

    var activeSheet by remember { mutableStateOf<SheetType?>(null) }

    // Vérifier si un partenaire est connecté
    val hasConnectedPartner by remember(appState.currentUser?.partnerId) {
        mutableStateOf(!appState.currentUser?.partnerId.isNullOrEmpty())
    }

    // Logs d'apparition (équivalent onAppear)
    LaunchedEffect(Unit) {
        Log.d(TAG, "🔥 MainView: Vue principale apparue")
        Log.d(TAG, "🔥🔥🔥 MAINVIEW ONAPPEAR: ETAT INITIAL")
        Log.d(TAG, "🔥🔥🔥 MAINVIEW ONAPPEAR: - FreemiumManager disponible: ${appState.freemiumManager != null}")
        appState.freemiumManager?.let {
            Log.d(TAG, "🔥🔥🔥 MAINVIEW ONAPPEAR: - FreemiumManager.showingSubscription: ${it.showingSubscription}")
            Log.d(TAG, "🔥 MainView: FreemiumManager disponible")
        } ?: run {
            Log.d(TAG, "🔥🔥🔥 MAINVIEW ONAPPEAR: - FreemiumManager: NIL!")
        }
    }

    // Remplace NotificationCenter: on réagit si FreemiumManager émet showingSubscription=true
    LaunchedEffect(appState.freemiumManager) {
        appState.freemiumManager?.showingSubscriptionFlow?.collectLatest { showing ->
            if (showing && activeSheet !is SheetType.Subscription) {
                Log.d(TAG, "🔥🔥🔥 MAINVIEW ONRECEIVE: AFFICHAGE SUBSCRIPTION DEMANDE")
                activeSheet = SheetType.Subscription
            }
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        // Dégradé rose en arrière-plan du contenu (en haut)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            PrimaryPink.copy(alpha = 0.30f),
                            PrimaryPink.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(0f, 2000f)
                    )
                )
        )

        // Contenu principal scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 100.dp) // Espace pour le menu du bas
        ) {
            Spacer(Modifier.height(80.dp)) // Espace depuis la status bar

            // Section distance entre partenaires
            PartnerDistanceView(
                onPartnerAvatarTap = {
                    activeSheet = SheetType.PartnerManagement
                },
                onDistanceTap = { showPartnerMessageOnly ->
                    activeSheet = if (showPartnerMessageOnly) {
                        SheetType.PartnerLocationMessage
                    } else {
                        SheetType.LocationPermission
                    }
                }
            )

            // Section invitation partenaire (si pas connecté)
            if (!hasConnectedPartner) {
                Spacer(Modifier.height(15.dp))
                PartnerInviteView(onInviteTap = {
                    activeSheet = SheetType.PartnerManagement
                })
            }

            // Liste des catégories (style rectangulaire)
            Spacer(Modifier.height(15.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Utiliser toutes les catégories - le FreemiumManager gère l'accès
                QuestionCategory.categories.forEach { category ->
                    CategoryListCardView(
                        category = category,
                        onClick = {
                            Log.d(TAG, "🔥🔥🔥 MAINVIEW CALLBACK: Catégorie sélectionnée: ${category.title}")
                            activeSheet = SheetType.Questions(category)
                            Log.d(TAG, "🔥🔥🔥 MAINVIEW CALLBACK: activeSheet = .questions(${category.title})")
                        }
                    )
                }
            }

            // Carte widget (aperçu)
            Spacer(Modifier.height(20.dp))
            WidgetPreviewSection(onWidgetTap = {
                Log.d(TAG, "📱 MainView: Carte widget tappée, ouverture de la page widgets")
                activeSheet = SheetType.Widgets
            })

            // Section Statistiques sur le couple
            Spacer(Modifier.height(30.dp))
            CoupleStatisticsView(appState = appState)
        }

        // Menu fixe en bas
        BottomMenu(
            onHome = { /* Déjà sur l'accueil */ },
            onJournal = {
                activeSheet = SheetType.Journal
                Log.d(TAG, "🔥 MainView: Ouverture du journal")
            },
            onFavorites = {
                activeSheet = SheetType.Favorites
                Log.d(TAG, "🔥 MainView: Ouverture des favoris")
            },
            onProfile = {
                activeSheet = SheetType.Menu
                analytics.logEvent("parametres_ouverts", null)
                Log.d(TAG, "📊 Événement Firebase: parametres_ouverts")
            }
        )

        // Présentation des feuilles (sheets)
        val openSheet = activeSheet
        if (openSheet != null) {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null }
            ) {
                when (val sheet = openSheet) {
                    is SheetType.Questions -> {
                        QuestionListView(category = sheet.category)
                    }

                    SheetType.Menu -> {
                        MenuView(
                            onLocationTutorialTap = {
                                activeSheet = SheetType.LocationTutorial
                            },
                            onWidgetsTap = {
                                activeSheet = SheetType.Widgets
                            }
                        )
                        Log.d(TAG, "🔥 MainView: MenuView apparue dans la sheet")
                    }

                    SheetType.Subscription -> {
                        SubscriptionView(appState = appState)
                        Log.d(TAG, "🔥 MainView: SubscriptionView apparue dans la sheet")
                        DisposableEffect(Unit) {
                            onDispose {
                                Log.d(TAG, "🔥 MainView: SubscriptionView disparue de la sheet")
                                // S'assurer que le FreemiumManager est notifié de la fermeture
                                appState.freemiumManager?.dismissSubscription()
                            }
                        }
                    }

                    SheetType.Favorites -> {
                        FavoritesCardView(appState = appState)
                        Log.d(TAG, "🔥 MainView: FavoritesCardView apparue dans la sheet")
                    }

                    SheetType.Journal -> {
                        JournalView(appState = appState)
                        Log.d(TAG, "🔥 MainView: JournalView apparue dans la sheet")
                    }

                    SheetType.Widgets -> {
                        WidgetsView(appState = appState)
                        Log.d(TAG, "🔥 MainView: WidgetsView apparue dans la sheet")
                    }

                    SheetType.WidgetTutorial -> {
                        // Pas d'équivalent direct de .presentationDetents(fraction) ici
                        WidgetTutorialView()
                        Log.d(TAG, "🔥 MainView: WidgetTutorialView apparue dans la sheet")
                    }

                    SheetType.PartnerManagement -> {
                        PartnerManagementView(appState = appState)
                        Log.d(TAG, "🔥 MainView: PartnerManagementView apparue dans la sheet")
                    }

                    SheetType.LocationPermission -> {
                        LocationPermissionFlow()
                        Log.d(TAG, "📍 MainView: LocationPermissionFlow apparue dans la sheet")
                    }

                    SheetType.PartnerLocationMessage -> {
                        LocationPartnerMessageView()
                        Log.d(TAG, "📍 MainView: LocationPartnerMessageView apparue dans la sheet")
                    }

                    SheetType.EventsMap -> {
                        JournalMapView(showBackButton = false, appState = appState)
                        Log.d(TAG, "🗺️ MainView: JournalMapView apparue dans la sheet")
                    }

                    SheetType.LocationTutorial -> {
                        LocationPermissionFlow()
                        Log.d(TAG, "📍 MainView: LocationPermissionFlow apparue depuis le menu")
                        DisposableEffect(Unit) {
                            onDispose {
                                Log.d(TAG, "📍 MainView: LocationPermissionFlow disparue depuis le menu")
                                // Démarrer immédiatement les mises à jour de localisation
                                appState.locationService?.startLocationUpdatesIfAuthorized()
                            }
                        }
                    }

                    SheetType.DailyQuestionPermission -> {
                        DailyQuestionPermissionView(appState = appState)
                        Log.d(TAG, "🔥 MainView: DailyQuestionPermissionView apparue dans la sheet")
                    }
                }
            }
        }
    }
}

// MARK: - Menu bas (fixe)
@Composable
private fun BoxScope.BottomMenu(
    onHome: () -> Unit,
    onJournal: () -> Unit,
    onFavorites: () -> Unit,
    onProfile: () -> Unit
) {
    Surface(
        color = Color.White,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onHome, modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(id = R.drawable.home),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    colorFilter = ColorFilter.tint(PrimaryPink)
                )
            }
            IconButton(onClick = onJournal, modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(id = R.drawable.star),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    colorFilter = ColorFilter.tint(PrimaryPink)
                )
            }
            IconButton(onClick = onFavorites, modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(id = R.drawable.heart),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    colorFilter = ColorFilter.tint(IconGrey)
                )
            }
            IconButton(onClick = onProfile, modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(id = R.drawable.profile),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    colorFilter = ColorFilter.tint(IconGrey)
                )
            }
        }
    }
}

// MARK: - Helper i18n (si tu veux un équivalent de getPremiumCategoriesSubtitle)
@Composable
private fun premiumCategoriesSubtitle(appState: AppState): String {
    return if (appState.currentUser?.isSubscribed == true) {
        stringResource(id = R.string.premium_categories_subscribed)
    } else {
        stringResource(id = R.string.premium_categories_not_subscribed)
    }
}

// -----------------------------------------------------------------------------
// NOTE: Les Composables référencés (PartnerDistanceView, PartnerInviteView, etc.)
// sont supposés exister dans ton code Android. Les signatures utilisées ici sont
// cohérentes avec les versions SwiftUI fournies. Adapte les imports/params si
// nécessaire.
// -----------------------------------------------------------------------------

// Exemples d'interfaces types attendues côté Android (à adapter/retirer si déjà présents):
interface AppState {
    val currentUser: User?
    val freemiumManager: FreemiumManager?
    val favoritesService: FavoritesService?
    val locationService: LocationService?
}

interface User { val id: String; val partnerId: String?; val isSubscribed: Boolean }

interface FreemiumManager {
    val showingSubscription: Boolean // pour logs
    val showingSubscriptionFlow: kotlinx.coroutines.flow.Flow<Boolean>
    fun dismissSubscription()
}

interface FavoritesService
interface LocationService { fun startLocationUpdatesIfAuthorized() }

class QuestionCategory(val id: String, val title: String) {
    companion object { val categories: List<QuestionCategory> = emptyList() }
}

@Composable fun PartnerDistanceView(onPartnerAvatarTap: () -> Unit, onDistanceTap: (showPartnerMessageOnly: Boolean) -> Unit) {}
@Composable fun PartnerInviteView(onInviteTap: () -> Unit) {}
@Composable fun CategoryListCardView(category: QuestionCategory, onClick: () -> Unit) {}
@Composable fun WidgetPreviewSection(onWidgetTap: () -> Unit) {}
@Composable fun CoupleStatisticsView(appState: AppState) {}
@Composable fun QuestionListView(category: QuestionCategory) {}
@Composable fun MenuView(onLocationTutorialTap: () -> Unit, onWidgetsTap: () -> Unit) {}
@Composable fun SubscriptionView(appState: AppState) {}
@Composable fun FavoritesCardView(appState: AppState) {}
@Composable fun JournalView(appState: AppState) {}
@Composable fun WidgetsView(appState: AppState) {}
@Composable fun WidgetTutorialView() {}
@Composable fun PartnerManagementView(appState: AppState) {}
@Composable fun LocationPermissionFlow() {}
@Composable fun LocationPartnerMessageView() {}
@Composable fun JournalMapView(showBackButton: Boolean, appState: AppState) {}
@Composable fun DailyQuestionPermissionView(appState: AppState) {}
