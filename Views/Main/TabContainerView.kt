package com.love2love.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Version Kotlin/Compose du TabContainerView Swift.
 *
 * ✅ Localisation : utilise strings.xml via stringResource()/context.getString().
 * ✅ Barre d'onglets fixe en bas, masquée lorsque le clavier est visible.
 * ✅ Événements Firebase Analytics identiques ("onglet_visite").
 * ✅ Feuilles (sheets) converties en ModalBottomSheet.
 *
 * Remplace les Composables "HomeContentView", "DailyQuestionFlowView", etc. par tes écrans.
 * Ajoute les ressources drawables (home, star, miss, heart, map, profile) et les clés de chaînes.
 */

@Composable
fun TabContainerView(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val analytics = remember { Firebase.analytics }

    var selectedTab by remember { mutableStateOf(0) }
    var activeSheet: SheetType? by remember { mutableStateOf(null) }

    // Détection IME (clavier) : compose 1.5+
    val isKeyboardVisible = WindowInsets.isImeVisible

    LaunchedEffect(Unit) {
        val ts = System.currentTimeMillis() / 1000.0
        Log.d("TabContainer", "🔥 Vue principale apparue [$ts]")
    }
    DisposableEffect(Unit) {
        onDispose {
            val ts = System.currentTimeMillis() / 1000.0
            Log.d("TabContainer", "🔥 Vue principale disparue [$ts]")
        }
    }

    // Écoute simple pour ouvrir la feuille d'abonnement quand demandé (si exposé par appState)
    LaunchedEffect(appState.freemiumManager?.showingSubscription) {
        if (appState.freemiumManager?.showingSubscription == true && activeSheet != SheetType.Subscription) {
            Log.d("TabContainer", "🔥 ONRECEIVE: AFFICHAGE SUBSCRIPTION DEMANDE")
            activeSheet = SheetType.Subscription
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Contenu principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isKeyboardVisible) 1.dp else 45.dp)
        ) {
            when (selectedTab) {
                0 -> HomeContentView(onShowSheet = { activeSheet = it })
                1 -> DailyQuestionFlowView(appState = appState)
                2 -> DailyChallengeFlowView(appState = appState)
                3 -> FavoritesView()
                4 -> JournalPageView(appState = appState)
                5 -> MenuContentView(appState = appState)
                else -> HomeContentView(onShowSheet = { activeSheet = it })
            }
        }

        // Barre d'onglets — masquée quand le clavier est visible
        AnimatedVisibility(
            visible = !isKeyboardVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
        ) {
            BottomTabBar(
                selectedTab = selectedTab,
                onSelect = { index ->
                    when (index) {
                        0 -> {
                            selectedTab = 0
                            analytics.logEvent("onglet_visite") { param("onglet", "accueil") }
                            Log.d("Firebase", "📊 Événement: onglet_visite - onglet: accueil")
                        }
                        1 -> {
                            logTimestampClick("QUESTIONS DU JOUR")
                            selectedTab = 1
                            analytics.logEvent("onglet_visite") { param("onglet", "questions") }
                            Log.d("Firebase", "📊 Événement: onglet_visite - onglet: questions")
                        }
                        2 -> {
                            logTimestampClick("DÉFIS DU JOUR")
                            selectedTab = 2
                            analytics.logEvent("onglet_visite") { param("onglet", "defis") }
                            Log.d("Firebase", "📊 Événement: onglet_visite - onglet: defis")
                        }
                        3 -> {
                            selectedTab = 3
                            analytics.logEvent("onglet_visite") { param("onglet", "favoris") }
                            Log.d("Firebase", "📊 Événement: onglet_visite - onglet: favoris")
                        }
                        4 -> {
                            selectedTab = 4
                            analytics.logEvent("onglet_visite") { param("onglet", "journal") }
                            Log.d("Firebase", "📊 Événement: onglet_visite - onglet: journal")
                        }
                        5 -> {
                            selectedTab = 5
                            analytics.logEvent("onglet_visite") { param("onglet", "profil") }
                            Log.d("Firebase", "📊 Événement: onglet_visite - onglet: profil")
                        }
                    }
                }
            )
        }

        // Sheets (équivalent .sheet(item:))
        val sheet = activeSheet
        if (sheet != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                sheetState = sheetState
            ) {
                when (sheet) {
                    is SheetType.Questions -> {
                        QuestionListView(category = sheet.category)
                        Log.d("TabContainer", "🔥 Sheet: QuestionListView pour ${sheet.category.title}")
                    }
                    SheetType.Subscription -> {
                        SubscriptionView(appState = appState)
                        Log.d("TabContainer", "🔥 Sheet: SubscriptionView")
                        // onDispose similaire à onDisappear Swift
                    }
                    SheetType.Widgets -> {
                        WidgetsView(appState = appState)
                        Log.d("TabContainer", "🔥 Sheet: WidgetsView")
                    }
                    SheetType.WidgetTutorial -> {
                        WidgetTutorialView()
                        Log.d("TabContainer", "🔥 Sheet: WidgetTutorialView")
                    }
                    SheetType.PartnerManagement -> {
                        PartnerManagementView(appState = appState)
                        Log.d("TabContainer", "🔥 Sheet: PartnerManagementView")
                    }
                    SheetType.LocationPermission -> {
                        LocationPermissionFlow(
                            onDisappear = {
                                Log.d("TabContainer", "📍 Sheet: LocationPermissionFlow disparue - Démarrage LocationService")
                                appState.locationService?.startLocationUpdatesIfAuthorized()
                            }
                        )
                        Log.d("TabContainer", "📍 Sheet: LocationPermissionFlow apparue")
                    }
                    SheetType.PartnerLocationMessage -> {
                        LocationPartnerMessageView()
                        Log.d("TabContainer", "📍 Sheet: LocationPartnerMessageView apparue")
                    }
                    SheetType.EventsMap -> {
                        JournalMapView(showBackButton = false, appState = appState)
                        Log.d("TabContainer", "🗺️ Sheet: JournalMapView apparue")
                    }
                    SheetType.LocationTutorial -> {
                        LocationPermissionFlow(
                            onDisappear = {
                                Log.d("TabContainer", "📍 Sheet: LocationPermissionFlow disparue depuis le menu")
                                appState.locationService?.startLocationUpdatesIfAuthorized()
                            }
                        )
                        Log.d("TabContainer", "📍 Sheet: LocationPermissionFlow apparue depuis le menu")
                    }
                    SheetType.DailyQuestionPermission -> {
                        DailyQuestionPermissionView(appState = appState)
                        Log.d("TabContainer", "🔥 Sheet: DailyQuestionPermissionView apparue")
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomTabBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    val pink = Color(0xFFFD267A)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TabIcon(
            selected = selectedTab == 0,
            drawableRes = R.drawable.home,
            contentDesc = stringResource(R.string.tab_home),
            selectedTint = pink,
            onClick = { onSelect(0) },
            selectedSize = 34.dp to 28.dp,
            normalSize = 30.dp to 24.dp
        )
        TabIcon(
            selected = selectedTab == 1,
            drawableRes = R.drawable.star,
            contentDesc = stringResource(R.string.tab_daily_questions),
            selectedTint = pink,
            onClick = { onSelect(1) },
            selectedSize = 34.dp to 28.dp,
            normalSize = 30.dp to 24.dp
        )
        TabIcon(
            selected = selectedTab == 2,
            drawableRes = R.drawable.miss,
            contentDesc = stringResource(R.string.tab_daily_challenges),
            selectedTint = pink,
            onClick = { onSelect(2) },
            selectedSize = 34.dp to 28.dp,
            normalSize = 30.dp to 24.dp
        )
        TabIcon(
            selected = selectedTab == 3,
            drawableRes = R.drawable.heart,
            contentDesc = stringResource(R.string.tab_favorites),
            selectedTint = pink,
            onClick = { onSelect(3) },
            selectedSize = 34.dp to 28.dp,
            normalSize = 30.dp to 24.dp
        )
        TabIcon(
            selected = selectedTab == 4,
            drawableRes = R.drawable.map,
            contentDesc = stringResource(R.string.tab_journal),
            selectedTint = pink,
            onClick = { onSelect(4) },
            selectedSize = 32.dp to 26.dp,
            normalSize = 28.dp to 22.dp,
            normalAlpha = 0.85f
        )
        TabIcon(
            selected = selectedTab == 5,
            drawableRes = R.drawable.profile,
            contentDesc = stringResource(R.string.tab_profile),
            selectedTint = pink,
            onClick = { onSelect(5) },
            selectedSize = 34.dp to 28.dp,
            normalSize = 30.dp to 24.dp
        )
    }
}

@Composable
private fun TabIcon(
    selected: Boolean,
    drawableRes: Int,
    contentDesc: String,
    selectedTint: Color,
    onClick: () -> Unit,
    selectedSize: Pair<Dp, Dp>,
    normalSize: Pair<Dp, Dp>,
    normalAlpha: Float = 1f,
) {
    val scale by animateFloatAsState(targetValue = if (selected) 1.1f else 1f, label = "scale")
    val width = if (selected) selectedSize.first else normalSize.first
    val height = if (selected) selectedSize.second else normalSize.second

    Box(
        modifier = Modifier
            .weight(1f)
            .wrapContentSize()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = contentDesc,
            modifier = Modifier
                .size(width, height)
                .scale(scale),
            alpha = if (selected) 1f else normalAlpha,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                if (selected) selectedTint else Color.Gray
            )
        )
    }
}

private fun logTimestampClick(tag: String) {
    val now: ZonedDateTime = ZonedDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val tz: ZoneId = ZoneId.systemDefault()
    Log.d("TabContainer", "🚀 === CLIC $tag ===")
    Log.d("TabContainer", "🕐 Date/Heure actuelle: ${now.format(formatter)}")
    Log.d("TabContainer", "🌍 Timezone: ${tz.id}")
    Log.d("TabContainer", "📅 Jour de la semaine: ${now.dayOfWeek.value}")
    Log.d("TabContainer", "📊 Jour du mois: ${now.dayOfMonth}")
    Log.d("TabContainer", "📈 Mois: ${now.monthValue}")
    Log.d("TabContainer", "📉 Année: ${now.year}")
}

// ========================
// Types & écrans attendus
// ========================

// Remplace par ton vrai modèle
data class Category(val id: String, val title: String)

sealed class SheetType {
    data class Questions(val category: Category) : SheetType()
    data object Subscription : SheetType()
    data object Widgets : SheetType()
    data object WidgetTutorial : SheetType()
    data object PartnerManagement : SheetType()
    data object LocationPermission : SheetType()
    data object PartnerLocationMessage : SheetType()
    data object EventsMap : SheetType()
    data object LocationTutorial : SheetType()
    data object DailyQuestionPermission : SheetType()
}

// Placeholders : branche tes vrais Composables
@Composable fun HomeContentView(onShowSheet: (SheetType) -> Unit) {}
@Composable fun DailyQuestionFlowView(appState: AppState) {}
@Composable fun DailyChallengeFlowView(appState: AppState) {}
@Composable fun FavoritesView() {}
@Composable fun JournalPageView(appState: AppState) {}
@Composable fun MenuContentView(appState: AppState) {}
@Composable fun QuestionListView(category: Category) {}
@Composable fun SubscriptionView(appState: AppState) {}
@Composable fun WidgetsView(appState: AppState) {}
@Composable fun WidgetTutorialView() {}
@Composable fun PartnerManagementView(appState: AppState) {}
@Composable fun LocationPermissionFlow(onDisappear: () -> Unit) {}
@Composable fun LocationPartnerMessageView() {}
@Composable fun JournalMapView(showBackButton: Boolean, appState: AppState) {}
@Composable fun DailyQuestionPermissionView(appState: AppState) {}

// AppState minimal – adapte-le à ton implémentation
class AppState(
    val freemiumManager: FreemiumManager? = null,
    val locationService: LocationService? = null,
)
class FreemiumManager(var showingSubscription: Boolean)
interface LocationService { fun startLocationUpdatesIfAuthorized() }
