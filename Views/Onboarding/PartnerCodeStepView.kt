// PartnerCodeStepScreen.kt
package com.love2love.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ————————————————————————————————————————————————
// Contracts (branche ton service réel ici)
// ————————————————————————————————————————————————
data class PartnerInfo(val isSubscribed: Boolean)

interface PartnerCodeService {
    val generatedCode: StateFlow<String?>
    val isLoading: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>
    val isConnected: StateFlow<Boolean>
    val partnerInfo: StateFlow<PartnerInfo?>
    /** Équivalent de NotificationCenter .subscriptionInherited */
    val subscriptionInheritedEvents: Flow<Unit>

    suspend fun checkExistingConnection()
    suspend fun generatePartnerCode(): Boolean
    suspend fun connectWithPartnerCode(code: String): Boolean
}

// ————————————————————————————————————————————————
// UI
// ————————————————————————————————————————————————
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerCodeStepScreen(
    partnerCodeService: PartnerCodeService,
    onNextStep: () -> Unit,
    onSkipSubscriptionDueToInheritance: () -> Unit,
    onFinalizeOnboarding: (withSubscription: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val analytics = remember { FirebaseAnalytics.getInstance(context) }

    val generatedCode by partnerCodeService.generatedCode.collectAsState(initial = null)
    val isLoading by partnerCodeService.isLoading.collectAsState(initial = false)
    val errorMessage by partnerCodeService.errorMessage.collectAsState(initial = null)
    val isConnected by partnerCodeService.isConnected.collectAsState(initial = false)
    val partnerInfo by partnerCodeService.partnerInfo.collectAsState(initial = null)

    var enteredCode by rememberSaveable { mutableStateOf("") }

    // onAppear
    LaunchedEffect(Unit) {
        Log.d("PartnerCodeStepScreen", "onAppear - Vérification connexion existante")
        partnerCodeService.checkExistingConnection()
        if (!isConnected) {
            Log.d("PartnerCodeStepScreen", "Pas de connexion existante - génération auto du code")
            partnerCodeService.generatePartnerCode()
        }
    }

    // Notification "subscriptionInherited"
    LaunchedEffect(partnerCodeService) {
        partnerCodeService.subscriptionInheritedEvents.collect {
            Log.d("PartnerCodeStepScreen", "Abonnement hérité détecté (event)")
            if (partnerInfo?.isSubscribed == true) {
                Log.d("PartnerCodeStepScreen", "Partenaire premium - finalisation directe")
                onSkipSubscriptionDueToInheritance()
                onFinalizeOnboarding(true)
            } else {
                onNextStep()
            }
        }
    }

    // Navigation quand la connexion s'établit
    LaunchedEffect(isConnected, partnerInfo) {
        if (isConnected) {
            Log.d("PartnerCodeStepScreen", "Connexion partenaire réussie")
            if (partnerInfo?.isSubscribed == true) {
                Log.d("PartnerCodeStepScreen", "Abonnement hérité - finalisation directe")
                onSkipSubscriptionDueToInheritance()
                onFinalizeOnboarding(true)
            } else {
                Log.d("PartnerCodeStepScreen", "Partenaire sans abonnement - aller à la page de paiement")
                onNextStep()
            }
        }
    }

    val backgroundGray = MaterialTheme.colorScheme.surface.copy(alpha = 1f) // on force un ton clair
    val appPink = androidx.compose.ui.graphics.Color(0xFFFD267A)
    val pageBg = androidx.compose.ui.graphics.Color(0xFFF7F7FA)

    Scaffold(
        containerColor = pageBg,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp, vertical = 30.dp)
                ) {
                    Button(
                        onClick = { onNextStep() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appPink,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.continuer),
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))

                // — Titre
                Text(
                    text = stringResource(R.string.connect_with_partner),
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    color = androidx.compose.ui.graphics.Color.Black
                )

                Spacer(Modifier.height(16.dp))

                // — Sous-titre
                Text(
                    text = stringResource(R.string.connect_partner_description),
                    style = TextStyle(fontSize = 16.sp),
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 0.dp),
                )

                Spacer(Modifier.height(40.dp))

                // — Section génération de code
                when {
                    generatedCode != null -> {
                        GeneratedCodeSection(
                            code = generatedCode ?: "",
                            onShare = {
                                Log.d("PartnerCodeStepScreen", "Tentative de partage du code (code masqué)")
                                analytics.logEvent("code_partenaire_partage", Bundle())
                                sharePartnerCode(context, code = generatedCode ?: "")
                            },
                            appPink = appPink
                        )
                    }
                    isLoading -> {
                        LoadingCodeSection()
                    }
                    errorMessage != null -> {
                        ErrorCodeSection(
                            errorMessage = errorMessage,
                            onRetry = {
                                scope.launch { partnerCodeService.generatePartnerCode() }
                            },
                            appPink = appPink
                        )
                    }
                    else -> {
                        // État de repli: loader
                        LoadingCodeSection()
                    }
                }

                Spacer(Modifier.height(32.dp))

                // — Saisie du code partenaire + bouton Connecter
                EnterCodeSection(
                    enteredCode = enteredCode,
                    onCodeChange = { enteredCode = filterDigitsToMax(it, maxLen = 8) },
                    isLoading = isLoading,
                    appPink = appPink,
                    onConnect = {
                        focusManager.safeClear()
                        scope.launch {
                            partnerCodeService.connectWithPartnerCode(enteredCode)
                        }
                    },
                    errorMessage = errorMessage
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GeneratedCodeSection(
    code: String,
    onShare: () -> Unit,
    appPink: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = stringResource(R.string.send_code_to_partner),
            style = TextStyle(fontSize = 16.sp),
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = androidx.compose.ui.graphics.Color.White,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 25.dp, horizontal = 20.dp)
            ) {
                Text(
                    text = code,
                    style = TextStyle(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    ),
                    color = appPink,
                    maxLines = 1
                )
            }
        }

        OutlinedButton(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = androidx.compose.ui.graphics.Color.White,
                contentColor = appPink
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(appPink)
            ),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.send_code),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun LoadingCodeSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = stringResource(R.string.code_generation),
            style = TextStyle(fontSize = 16.sp),
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)
        )
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

@Composable
private fun ErrorCodeSection(
    errorMessage: String?,
    onRetry: () -> Unit,
    appPink: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = stringResource(R.string.generation_error),
            style = TextStyle(fontSize = 16.sp),
            color = androidx.compose.ui.graphics.Color.Red
        )
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = TextStyle(fontSize = 14.sp),
                color = androidx.compose.ui.graphics.Color.Red
            )
        }
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = androidx.compose.ui.graphics.Color.White,
                contentColor = appPink
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(appPink)
            ),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.retry),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun EnterCodeSection(
    enteredCode: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    appPink: androidx.compose.ui.graphics.Color,
    onConnect: () -> Unit,
    errorMessage: String?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = stringResource(R.string.enter_partner_code),
            style = TextStyle(fontSize = 16.sp),
            color = androidx.compose.ui.graphics.Color.Black
        )

        // Champ "blanc + ombre" façon iOS
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = androidx.compose.ui.graphics.Color.White,
            shadowElevation = 2.dp
        ) {
            TextField(
                value = enteredCode,
                onValueChange = onCodeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp)
                    .heightIn(min = 56.dp),
                placeholder = { Text(text = stringResource(R.string.enter_code_placeholder)) },
                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.White,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }

        OutlinedButton(
            onClick = onConnect,
            enabled = enteredCode.length == 8 && !isLoading,
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = androidx.compose.ui.graphics.Color.White,
                contentColor = appPink,
                disabledContentColor = appPink.copy(alpha = 0.5f)
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (enteredCode.length == 8 && !isLoading) appPink else appPink.copy(alpha = 0.5f)
                )
            ),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.connecting_status),
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
            } else {
                Text(
                    text = stringResource(R.string.connect),
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = TextStyle(fontSize = 14.sp),
                color = androidx.compose.ui.graphics.Color.Red
            )
        }
    }
}

// ————————————————————————————————————————————————
// Helpers
// ————————————————————————————————————————————————
private fun filterDigitsToMax(input: String, maxLen: Int): String =
    input.filter { it.isDigit() }.take(maxLen)

private fun FocusManager.safeClear() {
    try { clearFocus() } catch (_: Throwable) { /* no-op */ }
}

private fun sharePartnerCode(context: Context, code: String) {
    // Préfère un string formaté côté res: "share_partner_code_message" -> "Voici mon code partenaire : %1$s"
    val text = context.getString(R.string.share_partner_code_message, code)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, context.getString(R.string.send_code))
    context.startActivity(chooser)
}
