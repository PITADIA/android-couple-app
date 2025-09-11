// DailyChallengeFlow.kt
package com.love2love.ui.dailychallenge

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.analytics.FirebaseAnalytics
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

// ---------- Couleurs / Thème locaux ----------
private val PinkPrimary = Color(0xFFFD267A)
private val CardHeaderGradient = Brush.horizontalGradient(
    listOf(Color(0xFFFF6699), Color(0xFFFF99CC))
)
private val CardBodyGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF33242B),
        Color(0xFF664055),
        Color(0xFF99523D)
    )
)
private val PaywallBackdrop = Color(0xFFF7F7FA)

// ---------- Modèle d’état / stubs (à relier à ton app) ----------
data class User(
    val partnerId: String? = null,
    val isSubscribed: Boolean = false
)

data class IntroFlags(val dailyChallenge: Boolean = false)

data class AppState(
    val currentUser: User? = null,
    val introFlags: IntroFlags = IntroFlags(),
    val freemiumManager: FreemiumManager? = null
)

interface FreemiumManager {
    fun canAccessDailyChallenge(day: Int): Boolean
    fun handleDailyChallengeAccess(currentChallengeDay: Int, onAccessGranted: () -> Unit)
}

data class DailyChallenge(val scheduledDate: LocalDate)

data class DailyChallengeSettings(
    val startDate: LocalDate,
    val zoneId: ZoneId = ZoneId.systemDefault()
)

class DailyChallengeService {
    var isLoading by mutableStateOf(false)
        private set

    var currentChallenge by mutableStateOf<DailyChallenge?>(null)
        private set

    var currentSettings by mutableStateOf<DailyChallengeSettings?>(null)
        private set

    fun configure(appState: AppState) {
        // Simule une récupération réseau/calcule du défi du jour
        isLoading = true
        // Si settings déjà présents, on les garde, sinon on initialise à J-3 pour l'exemple
        val settings = currentSettings ?: DailyChallengeSettings(startDate = LocalDate.now().minusDays(3))
        currentSettings = settings
        currentChallenge = DailyChallenge(scheduledDate = LocalDate.now())
        isLoading = false
    }
}

// ---------- Routing ----------
sealed class DailyContentRoute {
    data class Intro(val showConnectButton: Boolean) : DailyContentRoute()
    data class Paywall(val day: Int) : DailyContentRoute()
    data object Main : DailyContentRoute()
    data class Error(val message: String) : DailyContentRoute()
    data object Loading : DailyContentRoute()
}

object DailyContentRouteCalculator {
    fun calculateRoute(
        hasConnectedPartner: Boolean,
        hasSeenIntro: Boolean,
        shouldShowPaywall: Boolean,
        paywallDay: Int,
        serviceHasError: Boolean,
        serviceErrorMessage: String?,
        serviceIsLoading: Boolean
    ): DailyContentRoute {
        if (serviceHasError) return DailyContentRoute.Error(serviceErrorMessage ?: "Unknown error")
        if (serviceIsLoading) return DailyContentRoute.Loading
        if (!hasConnectedPartner) return DailyContentRoute.Intro(showConnectButton = true)
        if (!hasSeenIntro) return DailyContentRoute.Intro(showConnectButton = false)
        if (shouldShowPaywall) return DailyContentRoute.Paywall(paywallDay)
        return DailyContentRoute.Main
    }
}

// ---------- Helpers ----------
private fun calculateExpectedDay(settings: DailyChallengeSettings): Int {
    val today = LocalDate.now(settings.zoneId)
    val days = ChronoUnit.DAYS.between(settings.startDate, today).toInt()
    return max(1, days + 1)
}

private fun isToday(date: LocalDate): Boolean = date == LocalDate.now()

private fun logAnalyticsEvent(context: Context, name: String, params: Bundle.() -> Unit = {}) {
    val bundle = Bundle().apply(params)
    FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
}

// ---------- Flow principal ----------
@Composable
fun DailyChallengeFlowScreen(
    appState: AppState,
    dailyChallengeService: DailyChallengeService = remember { DailyChallengeService() }
) {
    val context = LocalContext.current

    // Configuration "onAppear"
    LaunchedEffect(appState, dailyChallengeService.currentChallenge, dailyChallengeService.currentSettings) {
        configureServiceIfNeeded(appState, dailyChallengeService)
    }

    // État dérivé pour la route actuelle
    val currentRoute by remember(
        appState,
        dailyChallengeService.isLoading,
        dailyChallengeService.currentChallenge,
        dailyChallengeService.currentSettings
    ) {
        mutableStateOf(
            run {
                val hasConnectedPartner = appState.currentUser?.partnerId?.trim()?.isNotEmpty() == true
                val currentDay = dailyChallengeService.currentSettings?.let { calculateExpectedDay(it) } ?: 1
                val isSubscribed = appState.currentUser?.isSubscribed == true
                val shouldShowPaywall =
                    if (isSubscribed) false else appState.freemiumManager?.canAccessDailyChallenge(currentDay)?.not() ?: false
                val serviceIsLoading = dailyChallengeService.isLoading && dailyChallengeService.currentChallenge == null

                DailyContentRouteCalculator.calculateRoute(
                    hasConnectedPartner = hasConnectedPartner,
                    hasSeenIntro = appState.introFlags.dailyChallenge,
                    shouldShowPaywall = shouldShowPaywall,
                    paywallDay = currentDay,
                    serviceHasError = false,
                    serviceErrorMessage = null,
                    serviceIsLoading = serviceIsLoading
                )
            }
        )
    }

    when (val route = currentRoute) {
        is DailyContentRoute.Intro -> {
            DailyChallengeIntroView(
                showConnectButton = route.showConnectButton,
                onShown = {
                    if (!route.showConnectButton) {
                        logAnalyticsEvent(context, "intro_shown") {
                            putString("screen", "daily_challenge")
                        }
                    }
                }
            )
        }
        is DailyContentRoute.Paywall -> {
            DailyChallengePaywallView(
                appState = appState,
                challengeDay = route.day
            )
        }
        is DailyContentRoute.Error -> {
            DailyChallengeErrorView(
                message = route.message,
                onRetry = { configureServiceIfNeeded(appState, dailyChallengeService) }
            )
        }
        DailyContentRoute.Loading -> DailyChallengeLoadingView()
        DailyContentRoute.Main -> DailyChallengeMainView()
    }
}

// Reprise fidèle de la logique Swift configureServiceIfNeeded()
private fun configureServiceIfNeeded(
    appState: AppState,
    dailyChallengeService: DailyChallengeService
) {
    val currentUser = appState.currentUser
    val partnerId = currentUser?.partnerId?.trim()

    // Partenaire requis
    if (partnerId.isNullOrEmpty()) {
        // En attente connexion partenaire
        return
    }
    // L'intro doit avoir été vue
    if (!appState.introFlags.dailyChallenge) {
        // En attente intro utilisateur
        return
    }

    // Si on a déjà un défi d'aujourd'hui, stop
    dailyChallengeService.currentChallenge?.let { challenge ->
        if (isToday(challenge.scheduledDate)) {
            return
        }
    }

    // Calcul du jour courant
    val currentDay = dailyChallengeService.currentSettings?.let { calculateExpectedDay(it) } ?: 1

    // Gérer le freemium AVANT de configurer le service
    appState.freemiumManager?.handleDailyChallengeAccess(currentDay) {
        dailyChallengeService.configure(appState)
    } ?: run {
        // Si pas de freemium manager, on configure directement
        dailyChallengeService.configure(appState)
    }
}

// ---------- Composants UI ----------

@Composable
private fun DailyChallengeIntroView(
    showConnectButton: Boolean,
    onShown: () -> Unit
) {
    LaunchedEffect(Unit) { onShown() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaywallBackdrop),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Daily Challenge",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (showConnectButton)
                    "Connecte ton/ta partenaire pour commencer."
                else
                    "Bienvenue ! Découvre un défi par jour.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            if (showConnectButton) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { /* TODO: navigation vers l’écran de connexion partenaire */ },
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Connecter mon/ma partenaire")
                }
            }
        }
    }
}

@Composable
private fun DailyChallengeMainView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaywallBackdrop),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Daily Challenge — Main",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun DailyChallengeErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaywallBackdrop),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Erreur",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(24.dp)) {
                Text("Réessayer")
            }
        }
    }
}

@Composable
private fun DailyChallengeLoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaywallBackdrop),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengePaywallView(
    appState: AppState,
    challengeDay: Int
) {
    val context = LocalContext.current
    var showSubscriptionSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PaywallBackdrop,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.paywall_page_title_challenges),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(60.dp))

            Text(
                text = stringResource(id = R.string.paywall_challenges_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(30.dp))

            // --------- Carte défi (floutée) ----------
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .heightIn(min = 260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .shadow(10.dp, RoundedCornerShape(20.dp))
            ) {
                // Contenu "réel" de la carte
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CardBodyGradient)
                ) {
                    // Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardHeaderGradient)
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.daily_challenges_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Corps
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Envoyez-lui un message pour lui dire pourquoi vous êtes reconnaissant de l'avoir dans votre vie aujourd'hui et partagez trois choses spécifiques que vous appréciez chez lui.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 30.dp)
                                .blur(8.dp) // FLOU sur le texte
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "🎯  " + (
                                // Si tu ajoutes `day_number` = "Jour %1$d" dans strings.xml
                                // stringResource(id = R.string.day_number, challengeDay)
                                "Jour $challengeDay"
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f)
                            ),
                            modifier = Modifier
                                .padding(bottom = 30.dp)
                                .blur(6.dp) // FLOU sur le footer
                        )
                    }
                }

                // Overlay "glassmorphism + blur" sur le corps uniquement
                Column(modifier = Modifier.fillMaxSize()) {
                    // Laisse l'entête (≈ 60dp) lisible
                    Spacer(Modifier.height(60.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF33242B).copy(alpha = 0.95f),
                                        Color(0xFF664055).copy(alpha = 0.95f),
                                        Color(0xFF99523D).copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .blur(15.dp) // vrai effet de flou
                            .background(Color.White.copy(alpha = 0.35f))
                            .clip(RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💕",
                            style = MaterialTheme.typography.displaySmall.copy(color = Color.White),
                            modifier = Modifier
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(id = R.string.paywall_challenges_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    showSubscriptionSheet = true
                    logAnalyticsEvent(context, "cta_premium_clicked") {
                        putString("source", "daily_challenge_paywall")
                        putInt("challenge_day", challengeDay)
                    }
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
            ) {
                Text(
                    text = stringResource(id = R.string.paywall_continue_button),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }

            Spacer(Modifier.height(160.dp))
        }
    }

    if (showSubscriptionSheet) {
        ModalBottomSheet(onDismissRequest = { showSubscriptionSheet = false }) {
            SubscriptionSheetContent(onClose = { showSubscriptionSheet = false })
        }
    }
}

@Composable
private fun SubscriptionSheetContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Premium",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Débloque tous les défis quotidiens et plus encore.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, shape = RoundedCornerShape(24.dp)) {
            Text("Fermer")
        }
        Spacer(Modifier.height(12.dp))
    }
}
