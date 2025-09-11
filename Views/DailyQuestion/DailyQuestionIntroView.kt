@file:Suppress("unused")

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

/**
 * Kotlin / Jetpack Compose port of the SwiftUI DailyQuestionIntroView.
 *
 * - Uses Android standard localization via strings.xml (stringResource / context.getString).
 * - Replaces NSLocalizedString / `challengeKey.localized` calls with stringResource(R.string.key).
 * - Keeps the UI/UX close to your Swift version (header title, center illustration, subtitle, CTA button,
 *   conditional partner-connect sheet, and success overlay).
 * - All-in-one file, with light placeholders for your app services.
 */

// ------------------------------
// Simple app state & models (placeholders to match your Swift environment object usage)
// ------------------------------

data class User(
    val partnerId: String? = null,
    val name: String? = null
)

class AppState {
    // Mimic Swift EnvironmentObject
    var currentUser by mutableStateOf<User?>(null)
        private set

    fun setCurrentUser(user: User?) {
        currentUser = user
    }

    fun markDailyQuestionIntroAsSeen() {
        // TODO: Analytics: intro shown ("daily_question")
    }
}

// Singleton-like notification service, observable via Compose state
object PartnerConnectionNotificationService {
    var shouldShowConnectionSuccess by mutableStateOf(false)
    var connectedPartnerName by mutableStateOf("")

    fun showConnectionSuccess(partnerName: String) {
        connectedPartnerName = partnerName
        shouldShowConnectionSuccess = true
    }

    fun dismissConnectionSuccess() {
        shouldShowConnectionSuccess = false
        connectedPartnerName = ""
    }
}

// Brand / design colors
private val AppBackground = Color(0xFFF7F7FA) // ~ Color(red: 0.97, green: 0.97, blue: 0.98)
private val BrandPink = Color(0xFFFD267A)     // hex "#FD267A"

// ------------------------------
// Public Composable
// ------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQuestionIntroScreen(
    showConnectButton: Boolean = true,
    appState: AppState,
    partnerService: PartnerConnectionNotificationService = PartnerConnectionNotificationService,
    onNavigateToMain: () -> Unit
) {
    val context = LocalContext.current
    val hasConnectedPartner = appState.currentUser?.partnerId?.isNotBlank() == true

    // Button text logic from Swift `buttonText`
    val buttonText = if (showConnectButton && !hasConnectedPartner) {
        // Either form is fine; using the Compose equivalent
        stringResource(R.string.connect_partner_button)
        // Or: context.getString(R.string.connect_partner_button)
    } else {
        stringResource(R.string.continue_button)
    }

    var showingPartnerCodeSheet by remember { mutableStateOf(false) }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.daily_question_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(100.dp)) // space between title and image

            // Illustration
            Image(
                painter = painterResource(id = R.drawable.mima), // Provide drawable "mima"
                contentDescription = null,
                modifier = Modifier
                    .size(240.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(20.dp))

            // Title & subtitle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.daily_question_intro_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.daily_question_intro_subtitle),
                    fontSize = 16.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            // Primary CTA button (conditional behavior)
            Button(
                onClick = {
                    if (showConnectButton && !hasConnectedPartner) {
                        showingPartnerCodeSheet = true
                    } else {
                        appState.markDailyQuestionIntroAsSeen()
                        onNavigateToMain()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPink)
            ) {
                Text(
                    text = buttonText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(160.dp)) // space for bottom bar
        }

        // Success overlay (like PartnerConnectionSuccessView with fade)
        AnimatedVisibility(
            visible = partnerService.shouldShowConnectionSuccess,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.partner_connected_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = context.getString(
                                R.string.partner_connected_message,
                                partnerService.connectedPartnerName
                            ),
                            fontSize = 15.sp,
                            color = Color.Black.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { partnerService.dismissConnectionSuccess() },
                            shape = CircleShape
                        ) {
                            Text(text = stringResource(R.string.ok))
                        }
                    }
                }
            }
        }

        // Partner code sheet (ModalBottomSheet)
        if (showingPartnerCodeSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showingPartnerCodeSheet = false },
                sheetState = sheetState,
            ) {
                DailyQuestionPartnerCodeSheet(
                    onDismiss = { showingPartnerCodeSheet = false }
                )
            }
        }
    }
}

// ------------------------------
// Partner code sheet content (placeholder – plug your own UI)
// ------------------------------

@Composable
fun DailyQuestionPartnerCodeSheet(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.connect_partner_sheet_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.connect_partner_sheet_subtitle),
            fontSize = 14.sp,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDismiss, shape = CircleShape) {
            Text(text = stringResource(R.string.close))
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ------------------------------
// Navigation target placeholder (your DailyQuestionMainView equivalent)
// ------------------------------

@Composable
fun DailyQuestionMainScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "DailyQuestionMainScreen()\n— placeholder —")
    }
}

// ------------------------------
// Usage example (drop-in where you need it)
// ------------------------------
/*
@Composable
fun EntryPoint() {
    val appState = remember { AppState().apply { setCurrentUser(User(partnerId = "")) } }

    var goMain by remember { mutableStateOf(false) }

    if (goMain) {
        DailyQuestionMainScreen()
    } else {
        DailyQuestionIntroScreen(
            showConnectButton = true,
            appState = appState,
            onNavigateToMain = { goMain = true }
        )
    }
}
*/

// ------------------------------
// strings.xml (add these keys to your res/values/strings.xml)
// ------------------------------
/*
<resources>
    <string name="daily_question_title">Question du jour</string>
    <string name="daily_question_intro_title">Un rendez-vous quotidien pour mieux vous connaître</string>
    <string name="daily_question_intro_subtitle">Chaque jour, recevez une question conçue pour nourrir votre complicité et ouvrir des conversations profondes.</string>

    <string name="connect_partner_button">Connecter mon partenaire</string>
    <string name="continue_button">Continuer</string>

    <string name="partner_connected_title">Partenaire connecté</string>
    <string name="partner_connected_message">Tu es désormais connecté(e) avec %1$s.</string>
    <string name="ok">OK</string>

    <string name="connect_partner_sheet_title">Relier votre compte</string>
    <string name="connect_partner_sheet_subtitle">Saisis le code de ton/ta partenaire ou partage le tien pour vous connecter.</string>
    <string name="close">Fermer</string>
</resources>
*/

// ------------------------------
// Notes d’intégration
// ------------------------------
/*
- Remplace painterResource(R.drawable.mima) par ton illustration (res/drawable/mima.png).
- Les appels iOS `NSLocalizedString` et `localized(tableName:)` sont remplacés par `stringResource(R.string.key)`
  (ou `context.getString(R.string.key)` si besoin hors @Composable).
- `markDailyQuestionIntroAsSeen()` : branche ton Analytics (Firebase Analytics, etc.).
- Le "success overlay" reproduit PartnerConnectionSuccessView avec un simple dismiss.
- La navigation vers l’écran principal est gérée via le callback `onNavigateToMain()`.
*/
