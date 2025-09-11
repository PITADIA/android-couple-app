// DailyChallengeIntroScreen.kt
package com.yourapp.dailychallenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update

// ⚠️ Remplace com.yourapp.R par ton vrai package R
import com.yourapp.R

/**
 * Recodage Jetpack Compose de la vue SwiftUI "DailyChallengeIntroView".
 * - Localisation: via strings.xml (Compose -> stringResource(R.string.key))
 * - "Sheet" partenaire: ModalBottomSheet Material 3
 * - Overlay succès connexion partenaire: carte animée en fade in/out
 * - "fullScreenCover": écran plein de placeholder pour DailyChallengeMainScreen
 *
 * Clés strings.xml attendues:
 *  - daily_challenges_title
 *  - daily_challenge_intro_title
 *  - daily_challenge_intro_subtitle
 *  - connect_partner_button
 *  - continue_button
 */

// -------------------------------
// App state + services (stubs)
// -------------------------------

@Stable
class AppState {
    var currentUser by mutableStateOf<User?>(null)
        private set

    fun setUser(user: User?) { currentUser = user }

    fun markDailyChallengeIntroAsSeen() {
        // TODO: Analytics, persistance, etc.
    }
}

data class User(
    val id: String = "",
    val name: String? = null,
    val partnerId: String? = null
)

/**
 * Équivalent simplifié de PartnerConnectionNotificationService.shared
 */
object PartnerConnectionNotificationService {
    val shouldShowConnectionSuccess = MutableStateFlow(false)
    val connectedPartnerName = MutableStateFlow<String?>(null)

    fun showConnectionSuccess(partnerName: String?) {
        connectedPartnerName.value = partnerName
        shouldShowConnectionSuccess.value = true
    }

    fun dismissConnectionSuccess() {
        shouldShowConnectionSuccess.value = false
        connectedPartnerName.value = null
    }
}

// -------------------------------
// UI principale
// -------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeIntroScreen(
    showConnectButton: Boolean = true,
    appState: AppState = remember { AppState() },
    partnerService: PartnerConnectionNotificationService = PartnerConnectionNotificationService,
    onNavigateToChallenge: (() -> Unit)? = null, // optionnel si tu utilises NavController
) {
    val context = LocalContext.current

    var showingPartnerCodeSheet by remember { mutableStateOf(false) }
    var navigateToChallenge by remember { mutableStateOf(false) }

    // Observations service partenaire
    val showSuccess by partnerService.shouldShowConnectionSuccess.collectAsState()
    val partnerName by partnerService.connectedPartnerName.collectAsState()

    val hasConnectedPartner by remember(appState.currentUser) {
        mutableStateOf(appState.currentUser?.partnerId?.isNotBlank() == true)
    }

    // Texte du bouton (équiv. computed property SwiftUI)
    val buttonText = remember(showConnectButton, hasConnectedPartner) {
        if (showConnectButton && !hasConnectedPartner) {
            // context.getString(...) possible aussi
            context.getString(R.string.connect_partner_button)
        } else {
            context.getString(R.string.continue_button)
        }
    }

    // État & contenu du sheet (Material3)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.0f))
    ) {
        // Fond gris clair identique
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = androidx.compose.ui.graphics.Color(0xFFF7F7FA))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // Header avec titre centré
            Text(
                text = stringResource(id = R.string.daily_challenges_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(100.dp)) // Espace augmenté entre titre et image

            // Image centrale
            Image(
                painter = painterResource(id = R.drawable.gaougaou), // place ton drawable
                contentDescription = null,
                modifier = Modifier
                    .size(240.dp)
            )

            Spacer(Modifier.height(20.dp))

            // Titre + sous-titre
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.daily_challenge_intro_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = androidx.compose.ui.graphics.Color.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(id = R.string.daily_challenge_intro_subtitle),
                    fontSize = 16.sp,
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bouton principal
            Button(
                onClick = {
                    if (showConnectButton && !hasConnectedPartner) {
                        showingPartnerCodeSheet = true
                    } else {
                        appState.markDailyChallengeIntroAsSeen()
                        if (onNavigateToChallenge != null) {
                            onNavigateToChallenge()
                        } else {
                            navigateToChallenge = true
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFD267A),
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = buttonText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(160.dp)) // Espace pour le menu bas si besoin
        }

        // Overlay succès connexion partenaire (fade)
        AnimatedVisibility(
            visible = showSuccess,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(1000f)
        ) {
            PartnerConnectionSuccessCard(
                partnerName = partnerName,
                onDismiss = { partnerService.dismissConnectionSuccess() }
            )
        }

        // "fullScreenCover" équivalent simple: écran plein par-dessus
        if (navigateToChallenge && onNavigateToChallenge == null) {
            DailyChallengeMainScreen(
                onClose = { navigateToChallenge = false }
            )
        }
    }

    // Sheet partenaire (équiv. .sheet SwiftUI)
    if (showingPartnerCodeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showingPartnerCodeSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DailyQuestionPartnerCodeSheet(
                onDismiss = { showingPartnerCodeSheet = false }
            )
        }
    }
}

// -------------------------------
// Composants auxiliaires
// -------------------------------

@Composable
private fun PartnerConnectionSuccessCard(
    partnerName: String?,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = partnerName?.let { "🎉 $it connecté·e !" } ?: "🎉 Partenaire connecté·e !",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(text = "OK")
            }
        }
    }
}

@Composable
private fun DailyQuestionPartnerCodeSheet(
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Entrer le code partenaire",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "⚠️ Placeholder: intègre ici ton UI de connexion partenaire (saisie de code, etc.).",
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(text = "Fermer")
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Remplace l’écran plein utilisé par .fullScreenCover dans SwiftUI.
 * Ici: simple placeholder. Branche ton vrai écran "DailyChallengeMainView" Android.
 */
@Composable
private fun DailyChallengeMainScreen(
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                text = "Daily Challenge Main (placeholder)",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Button(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Fermer")
            }
        }
    }
}

// -------------------------------
// Preview (facultatif)
// -------------------------------

// @Preview(showSystemUi = true, showBackground = true)
// @Composable
// private fun PreviewDailyChallengeIntroScreen() {
//     val appState = remember { AppState() }.apply {
//         setUser(User(id = "me", partnerId = null))
//     }
//     DailyChallengeIntroScreen(
//         showConnectButton = true,
//         appState = appState
//     )
// }
