// DailyQuestionFlowView.kt
package com.yourapp.daily

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/* ===========================
   ⬇️  REPLACE WITH YOUR TYPES
   =========================== */

data class User(val partnerId: String?, val isSubscribed: Boolean)
data class IntroFlags(val dailyQuestion: Boolean)
data class AppState(
    val currentUser: User?,
    val introFlags: IntroFlags,
    val freemiumManager: FreemiumManager?
)

interface FreemiumManager {
    fun canAccessDailyQuestion(day: Int): Boolean
    fun handleDailyQuestionAccess(currentQuestionDay: Int, onAccessGranted: () -> Unit)
}

data class DailyQuestion(val scheduledDate: String) // format attendu: "yyyy-MM-dd" (UTC)
data class DailyQuestionSettings(val startDateUtc: Instant)

interface DailyQuestionService {
    val isLoading: StateFlow<Boolean>
    val currentQuestion: StateFlow<DailyQuestion?>
    val currentSettings: StateFlow<DailyQuestionSettings?>
    fun configure(appState: AppState)
}

object AnalyticsService {
    fun trackIntroShown(screen: String) { /* TODO: relier à ton analytics */ }
}

/* ===========================
   📍 ROUTING
   =========================== */

sealed class DailyContentRoute {
    data class Intro(val showConnect: Boolean) : DailyContentRoute()
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
        return when {
            !hasConnectedPartner -> DailyContentRoute.Intro(showConnect = true)
            !hasSeenIntro -> DailyContentRoute.Intro(showConnect = false)
            serviceHasError -> DailyContentRoute.Error(serviceErrorMessage ?: "")
            shouldShowPaywall -> DailyContentRoute.Paywall(paywallDay)
            serviceIsLoading -> DailyContentRoute.Loading
            else -> DailyContentRoute.Main
        }
    }
}

/* ===========================
   🌟 MAIN COMPOSABLE
   =========================== */

@Composable
fun DailyQuestionFlowView(
    appState: AppState,
    dailyQuestionService: DailyQuestionService,
    modifier: Modifier = Modifier
) {
    val isLoading by dailyQuestionService.isLoading.collectAsState(initial = false)
    val currentQuestion by dailyQuestionService.currentQuestion.collectAsState(initial = null)
    val currentSettings by dailyQuestionService.currentSettings.collectAsState(initial = null)

    val hasConnectedPartner = remember(appState.currentUser?.partnerId) {
        !appState.currentUser?.partnerId.orEmpty().trim().isEmpty()
    }

    val currentQuestionDay = remember(currentSettings) {
        currentSettings?.let { calculateExpectedDay(it) } ?: 1
    }

    val shouldShowPaywall = remember(appState.currentUser?.isSubscribed, currentQuestionDay) {
        if (appState.currentUser?.isSubscribed == true) {
            false
        } else {
            appState.freemiumManager?.canAccessDailyQuestion(currentQuestionDay) != true
        }
    }

    val route = remember(
        hasConnectedPartner,
        appState.introFlags.dailyQuestion,
        shouldShowPaywall,
        currentQuestionDay,
        isLoading,
        currentQuestion
    ) {
        DailyContentRouteCalculator.calculateRoute(
            hasConnectedPartner = hasConnectedPartner,
            hasSeenIntro = appState.introFlags.dailyQuestion,
            shouldShowPaywall = shouldShowPaywall,
            paywallDay = currentQuestionDay,
            serviceHasError = false,                // Pas de gestion d'erreur dans le service côté Swift
            serviceErrorMessage = null,
            serviceIsLoading = isLoading && currentQuestion == null
        )
    }

    // ⛳️ Configure au premier rendu (et à la demande via Retry)
    LaunchedEffect(Unit) {
        configureServiceIfNeeded(
            appState = appState,
            service = dailyQuestionService,
            hasConnectedPartner = hasConnectedPartner,
            hasSeenIntro = appState.introFlags.dailyQuestion,
            currentQuestion = currentQuestion,
            currentQuestionDay = currentQuestionDay
        )
    }

    // 📈 Analytics: intro affichée sans bouton connect → track
    LaunchedEffect(route) {
        if (route is DailyContentRoute.Intro && !route.showConnect) {
            AnalyticsService.trackIntroShown(screen = "daily_question")
        }
    }

    // 🧭 UI: même mapping que Swift
    when (route) {
        is DailyContentRoute.Intro -> {
            DailyQuestionIntroScreen(
                showConnectButton = route.showConnect,
                modifier = modifier
            )
        }
        is DailyContentRoute.Paywall -> {
            DailyQuestionPaywallScreen(
                questionDay = route.day,
                modifier = modifier
            )
        }
        DailyContentRoute.Main,
        DailyContentRoute.Loading -> {
            // ✅ Aligné avec le commentaire Swift: Loading redirige vers Main
            DailyQuestionMainScreen(modifier = modifier)
        }
        is DailyContentRoute.Error -> {
            DailyQuestionErrorScreen(
                message = route.message,
                onRetry = {
                    configureServiceIfNeeded(
                        appState = appState,
                        service = dailyQuestionService,
                        hasConnectedPartner = hasConnectedPartner,
                        hasSeenIntro = appState.introFlags.dailyQuestion,
                        currentQuestion = currentQuestion,
                        currentQuestionDay = currentQuestionDay
                    )
                },
                modifier = modifier
            )
        }
    }
}

/* ===========================
   🧠 LOGIQUE UTILITAIRE
   =========================== */

private fun configureServiceIfNeeded(
    appState: AppState,
    service: DailyQuestionService,
    hasConnectedPartner: Boolean,
    hasSeenIntro: Boolean,
    currentQuestion: DailyQuestion?,
    currentQuestionDay: Int
) {
    if (!hasConnectedPartner) {
        // ⏳ En attente de connexion partenaire
        return
    }
    if (!hasSeenIntro) {
        // ⏳ En attente que l'utilisateur voie l'intro
        return
    }

    // ⚡️ Cache: si on a déjà la question d'aujourd'hui, ne rien faire
    val todayUtc = LocalDate.now(ZoneOffset.UTC).toString() // "yyyy-MM-dd"
    if (currentQuestion?.scheduledDate == todayUtc) {
        return
    }

    // ✅ Vérifie l'accès freemium avant configuration
    val fm = appState.freemiumManager
    if (fm != null) {
        fm.handleDailyQuestionAccess(currentQuestionDay) {
            service.configure(appState)
        }
    } else {
        service.configure(appState)
    }
}

/**
 * Calcule le jour attendu (1, 2, 3, …) depuis la date de début en UTC, inclusif.
 */
private fun calculateExpectedDay(settings: DailyQuestionSettings): Int {
    val startDate = settings.startDateUtc.atZone(ZoneOffset.UTC).toLocalDate()
    val today = LocalDate.now(ZoneOffset.UTC)
    val daysSinceStart = ChronoUnit.DAYS.between(startDate, today).toInt()
    return daysSinceStart + 1
}

/* ===========================
   🧩 STUBS D'ÉCRANS (à remplacer)
   =========================== */

@Composable
private fun DailyQuestionIntroScreen(
    showConnectButton: Boolean,
    modifier: Modifier = Modifier
) {
    // ⚠️ Remplace par ton écran réel et tes chaînes via strings.xml
    Surface(modifier = modifier) {
        Text(text = if (showConnectButton) "Intro (avec bouton de connexion)" else "Intro (déjà connecté)")
    }
}

@Composable
private fun DailyQuestionPaywallScreen(
    questionDay: Int,
    modifier: Modifier = Modifier
) {
    // ⚠️ Remplace par ton écran paywall réel et tes chaînes via strings.xml
    Surface(modifier = modifier) {
        Text(text = "Paywall – Jour $questionDay")
    }
}

@Composable
private fun DailyQuestionMainScreen(
    modifier: Modifier = Modifier
) {
    // ⚠️ Remplace par ton écran principal réel et tes chaînes via strings.xml
    Surface(modifier = modifier) {
        Text(text = "Daily Question – Main")
    }
}

@Composable
private fun DailyQuestionErrorScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ⚠️ Remplace par ton écran d'erreur réel et tes chaînes via strings.xml
    Surface(modifier = modifier) {
        Column {
            Text(text = message.ifBlank { "Oups, une erreur est survenue" /* stringResource(R.string.oups_error) */ })
            Button(onClick = onRetry) { Text(text = "Réessayer" /* stringResource(R.string.retry) */) }
        }
    }
}
