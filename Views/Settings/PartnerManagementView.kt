package com.love2love.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.love2love.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

/**
 * Jetpack Compose port of PartnerManagementView (SwiftUI).
 * - Replaces `.localized` with Android resources via stringResource/context.getString.
 * - Handles connected / disconnected states, code generation & sharing, connect by code, and disconnect alert.
 * - Uses gradient background, card-like sections, IME-safe scrolling, and a success overlay.
 */
@Composable
fun PartnerManagementScreen(
    appState: AppState = remember { AppState() },
    viewModel: PartnerManagementViewModel = viewModel(),
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Local UI state
    var enteredCode by remember { mutableStateOf("") }
    var showDisconnectAlert by remember { mutableStateOf(false) }

    // Run side-effect similar to onAppear
    LaunchedEffect(Unit) {
        viewModel.checkExistingConnection()
    }

    // When connection becomes true, show success overlay once
    LaunchedEffect(viewModel.isConnected) {
        if (viewModel.isConnected && !appState.partnerConnectionService.shouldShowConnectionSuccess) {
            val name = viewModel.partnerInfo?.name.orEmpty()
            appState.partnerConnectionService.markConnected(name)
        }
    }

    // Root
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFE5F1), Color(0xFFFFF0F8))
                )
            )
            .pointerInput(Unit) {
                // Dismiss keyboard on tap outside
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        // Scrollable content (with IME padding so buttons won't be covered)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Title block depends on connection state
            if (viewModel.isConnected) {
                Text(
                    text = stringResource(R.string.connected_with_partner),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 30.dp)
                ) {
                    Text(
                        text = stringResource(R.string.connect_with_partner),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.connect_partner_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (viewModel.isConnected) {
                ConnectedSection(
                    partnerInfo = viewModel.partnerInfo,
                    onDisconnectClick = { showDisconnectAlert = true }
                )
            } else {
                DisconnectedSection(
                    isLoading = viewModel.isLoading,
                    generatedCode = viewModel.generatedCode,
                    errorMessage = viewModel.errorMessage,
                    enteredCode = enteredCode,
                    onEnteredCodeChanged = { new ->
                        val digits = new.filter { it.isDigit() }.take(8)
                        enteredCode = digits
                    },
                    onGenerateCode = { viewModel.generatePartnerCode() },
                    onShareCode = { code -> sharePartnerCode(context, code) },
                    onConnect = {
                        viewModel.connectWithPartnerCode(enteredCode, ConnectionContext.MENU)
                    }
                )
            }

            Spacer(Modifier.height(48.dp))
        }

        // Disconnect confirmation dialog
        if (showDisconnectAlert) {
            AlertDialog(
                onDismissRequest = { showDisconnectAlert = false },
                title = { Text(stringResource(R.string.disconnect_partner_title)) },
                text = { Text(stringResource(R.string.disconnect_confirmation)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDisconnectAlert = false
                            viewModel.disconnectPartner()
                        }
                    ) { Text(stringResource(R.string.disconnect_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDisconnectAlert = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Success overlay, similar to PartnerConnectionSuccessView
        if (appState.partnerConnectionService.shouldShowConnectionSuccess) {
            PartnerConnectionSuccessOverlay(
                partnerName = appState.partnerConnectionService.connectedPartnerName,
                onDismiss = {
                    appState.partnerConnectionService.dismissConnectionSuccess()
                    onClose()
                }
            )
        }
    }
}

@Composable
private fun ConnectedSection(
    partnerInfo: PartnerInfo?,
    onDisconnectClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Partner card
        if (partnerInfo != null) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 25.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.partner_name, partnerInfo.name),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.connected_on,
                            formatDate(partnerInfo.connectedAt, context.resources.configuration.locales[0])
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    if (partnerInfo.isSubscribed) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.shared_premium),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Disconnect button (card-like)
        Surface(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onDisconnectClick() },
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.disconnect),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFB00020) // Material red-ish
                )
            }
        }
    }
}

@Composable
private fun DisconnectedSection(
    isLoading: Boolean,
    generatedCode: String?,
    errorMessage: String?,
    enteredCode: String,
    onEnteredCodeChanged: (String) -> Unit,
    onGenerateCode: () -> Unit,
    onShareCode: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Generate or show code
        if (generatedCode == null) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isLoading) { onGenerateCode() },
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.generating), color = Color.Black)
                        }
                    } else {
                        Text(stringResource(R.string.send_partner_code), color = Color.Black)
                    }
                }
            }
        } else {
            GeneratedCodeSection(
                code = generatedCode,
                onShare = { onShareCode(generatedCode) }
            )
        }

        Spacer(Modifier.height(30.dp))

        // Enter code section
        Column(modifier = Modifier.padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.enter_partner_code),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = enteredCode,
                onValueChange = onEnteredCodeChanged,
                singleLine = true,
                textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text(stringResource(R.string.enter_code)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))

            val connectEnabled = enteredCode.length == 8 && !isLoading
            GradientButton(
                text = if (isLoading) stringResource(R.string.connecting_status) else stringResource(R.string.connect),
                enabled = connectEnabled,
                onClick = onConnect
            )

            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB00020),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GeneratedCodeSection(code: String, onShare: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.send_code_to_partner),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 25.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFD267A),
                        letterSpacing = 8.sp
                    ),
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onShare() },
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.send_partner_code),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.Black
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Divider(color = Color.Black.copy(alpha = 0.3f), thickness = 1.dp)
    }
}

@Composable
private fun GradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val alpha = if (enabled) 1f else 0.5f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(listOf(Color(0xFFFD267A), Color(0xFFFF655B)))
            )
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 16.dp)
            .graphicsLayerAlpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

// Modifier extension to apply alpha to entire Box content
@Stable
private fun Modifier.graphicsLayerAlpha(alpha: Float): Modifier = this.then(Modifier.graphicsLayer { this.alpha = alpha })

// Success overlay akin to PartnerConnectionSuccessView(mode: .simpleDismiss)
@Composable
private fun PartnerConnectionSuccessOverlay(partnerName: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp,
            shadowElevation = 12.dp,
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFD267A))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.connected_success_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.connected_success_message, partnerName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

// -------- ViewModel & simple data layer stubs --------

data class PartnerInfo(
    val name: String,
    val connectedAt: Long,
    val isSubscribed: Boolean
)

enum class ConnectionContext { MENU }

class PartnerManagementViewModel(
    private val repo: PartnerCodeRepository = PartnerCodeRepository()
) : ViewModel() {
    var isConnected by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var generatedCode by mutableStateOf<String?>(null)
        private set
    var partnerInfo by mutableStateOf<PartnerInfo?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun checkExistingConnection() {
        viewModelScope.launch {
            isLoading = true
            try {
                val result = repo.checkExistingConnection()
                isConnected = result.first
                partnerInfo = result.second
                errorMessage = null
            } catch (t: Throwable) {
                errorMessage = t.message
            } finally {
                isLoading = false
            }
        }
    }

    fun generatePartnerCode() {
        viewModelScope.launch {
            isLoading = true
            try {
                generatedCode = repo.generatePartnerCode()
                errorMessage = null
            } catch (t: Throwable) {
                errorMessage = t.message
            } finally {
                isLoading = false
            }
        }
    }

    fun connectWithPartnerCode(code: String, context: ConnectionContext) {
        if (code.length != 8) return
        viewModelScope.launch {
            isLoading = true
            try {
                val info = repo.connectWithPartnerCode(code, context)
                isConnected = true
                partnerInfo = info
                errorMessage = null
            } catch (t: Throwable) {
                errorMessage = t.message
            } finally {
                isLoading = false
            }
        }
    }

    fun disconnectPartner() {
        viewModelScope.launch {
            try {
                repo.disconnectPartner()
            } finally {
                // Reset state
                isConnected = false
                partnerInfo = null
                generatedCode = null
            }
        }
    }
}

class PartnerCodeRepository {
    // TODO: Replace with your real implementation (e.g., Firebase Functions/Firestore).

    suspend fun checkExistingConnection(): Pair<Boolean, PartnerInfo?> {
        // Simulate no connection by default
        delay(200)
        return false to null
    }

    suspend fun generatePartnerCode(): String {
        delay(400)
        // 8-digit demo code
        return (10000000..99999999).random().toString()
    }

    suspend fun connectWithPartnerCode(code: String, context: ConnectionContext): PartnerInfo {
        delay(600)
        if (code != "12345678") {
            // Simulate success for any 8-digit code, or throw for specific cases
            return PartnerInfo(name = "Alex", connectedAt = System.currentTimeMillis(), isSubscribed = true)
        } else {
            throw IllegalArgumentException("Code invalide ou expiré.")
        }
    }

    suspend fun disconnectPartner() {
        delay(200)
    }
}

// -------- AppState & PartnerConnectionService stubs --------

class AppState(
    val partnerConnectionService: PartnerConnectionService = PartnerConnectionService()
)

class PartnerConnectionService {
    var shouldShowConnectionSuccess by mutableStateOf(false)
        private set
    var connectedPartnerName by mutableStateOf("")
        private set

    fun markConnected(name: String) {
        connectedPartnerName = name
        shouldShowConnectionSuccess = true
    }

    fun dismissConnectionSuccess() {
        shouldShowConnectionSuccess = false
    }
}

// -------- Helpers --------

fun formatDate(timeMillis: Long, locale: Locale): String {
    return DateFormat.getDateInstance(DateFormat.LONG, locale).format(Date(timeMillis))
}

fun sharePartnerCode(context: Context, code: String) {
    val message = context.getString(R.string.share_partner_code_message, code)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_via)))
}

/* --------------------------- strings.xml (à ajouter) ---------------------------
<resources>
    <string name="connected_with_partner">Connecté(e) avec ton partenaire</string>
    <string name="connect_with_partner">Connecte-toi à ton partenaire</string>
    <string name="connect_partner_description">Pour profiter de Love2Love à deux, partage ton code ou saisis celui de ton partenaire.</string>

    <string name="partner_name">Nom du partenaire : %1$s</string>
    <string name="connected_on">Connecté depuis : %1$s</string>
    <string name="shared_premium">Premium partagé</string>

    <string name="disconnect">Déconnecter</string>
    <string name="disconnect_partner_title">Déconnecter le partenaire</string>
    <string name="disconnect_confirmation">Es-tu sûr de vouloir te déconnecter de ton partenaire ?</string>
    <string name="disconnect_confirm">Déconnecter</string>
    <string name="cancel">Annuler</string>

    <string name="send_partner_code">Envoyer mon code</string>
    <string name="send_code_to_partner">Envoie ce code à ton partenaire</string>

    <string name="enter_partner_code">Saisis le code de ton partenaire</string>
    <string name="enter_code">Entrer le code</string>

    <string name="connecting_status">Connexion en cours…</string>
    <string name="connect">Connecter</string>
    <string name="generating">Génération…</string>

    <string name="share_partner_code_message">Voici mon code partenaire Love2Love : %1$s</string>
    <string name="share_via">Partager via</string>

    <string name="connected_success_title">Connexion réussie</string>
    <string name="connected_success_message">Tu es maintenant connecté(e) avec %1$s !</string>
    <string name="ok">OK</string>
</resources>
------------------------------------------------------------------------------- */
