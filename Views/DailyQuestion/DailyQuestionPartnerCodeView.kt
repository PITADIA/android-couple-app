package com.love2love.ui.partner

import android.content.Intent
import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * DailyQuestionPartnerCodeScreen — Jetpack Compose port of the SwiftUI view.
 *
 * ✅ Uses Android's standard string resources (strings.xml) via context.getString(R.string.*)
 * ✅ Single-file drop-in (this file). Wire your own PartnerCodeService implementation.
 * ✅ Gradient background, card styling, share intent, numeric code input (8 digits), keyboard-aware spacing.
 *
 * Required string resources (add these to res/values/strings.xml):
 *  - connect_with_partner
 *  - connect_partner_description
 *  - generating
 *  - send_partner_code
 *  - generation_error
 *  - retry
 *  - send_code_to_partner
 *  - enter_partner_code
 *  - enter_code
 *  - connecting_status
 *  - connect
 *  - share_partner_code_message  (example: "Voici mon code Love2Love : %1$s")
 */

// --- Public API --------------------------------------------------------------

@Composable
fun DailyQuestionPartnerCodeScreen(
    onDismiss: () -> Unit,
    service: PartnerCodeService,
    modifier: Modifier = Modifier,
    viewModel: PartnerCodeViewModel = partnerCodeViewModel(service)
) {
    val ctx = LocalContext.current
    val ui = viewModel.uiState.collectAsState().value
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // "onAppear" equivalent
    LaunchedEffect(Unit) {
        viewModel.checkExistingConnection()
    }

    // If connected => dismiss (navigation handled by caller)
    LaunchedEffect(ui.isConnected) {
        if (ui.isConnected) onDismiss()
        else if (!ui.isLoading && ui.generatedCode == null && ui.errorMessage == null) {
            // Not connected and nothing generated yet => try to generate
            viewModel.generateCode()
        }
    }

    // Keyboard-aware spacing
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }
    val screenHeightDp = configuration.screenHeightDp.dp
    val topSpacer = maxDp(50.dp, ((screenHeightDp.value - imeBottomDp.value) * 0.15f).dp)
    val bottomSpacer = maxDp(
        50.dp,
        if (imeBottomPx > 0) 20.dp else (screenHeightDp.value * 0.15f).dp
    )

    // Background gradient (vertical)
    val bgGradient = Brush.verticalGradient(
        listOf(hexColor("#FFE5F1"), hexColor("#FFF0F8"))
    )

    Box(
        modifier
            .fillMaxSize()
            .background(bgGradient)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(topSpacer))

            // Title & subtitle
            Text(
                text = ctx.getString(R.string.connect_with_partner),
                fontSize = 28.sp,
                lineHeight = 32.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = ctx.getString(R.string.connect_partner_description),
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(40.dp))

            // Code generation section
            when {
                ui.isLoading -> LoadingCodeSection()
                ui.errorMessage != null -> ErrorCodeSection(
                    message = ui.errorMessage,
                    onRetry = { viewModel.generateCode() }
                )
                ui.generatedCode != null -> GeneratedCodeSection(
                    code = ui.generatedCode,
                    onShare = {
                        val shareText = ctx.getString(
                            R.string.share_partner_code_message,
                            ui.generatedCode
                        )
                        shareText(ctx, shareText, ctx.getString(R.string.send_partner_code))
                    }
                )
                else -> LoadingCodeSection() // default placeholder
            }

            Spacer(Modifier.height(40.dp))

            // Enter code section
            EnterCodeSection(
                code = viewModel.enteredCode,
                onCodeChange = viewModel::onEnteredCodeChange,
                isLoading = ui.isLoading || viewModel.isConnecting,
                onConnect = { viewModel.connectWithCode(ConnectContext.DAILY_QUESTION) },
                errorMessage = ui.errorMessage
            )

            Spacer(Modifier.height(bottomSpacer))
        }
    }
}

// --- UI Subsections ---------------------------------------------------------

@Composable
private fun LoadingCodeSection() {
    CardLikeBox {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = LocalContext.current.getString(R.string.generating),
                fontSize = 16.sp,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun ErrorCodeSection(message: String?, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = LocalContext.current.getString(R.string.generation_error),
            fontSize = 16.sp,
            color = Color(0xFFD32F2F)
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = Color(0xFFD32F2F),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))
        CardLikeButton(onClick = onRetry) {
            Text(
                text = LocalContext.current.getString(R.string.retry),
                fontSize = 16.sp,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun GeneratedCodeSection(code: String, onShare: () -> Unit) {
    val pink = hexColor("#FD267A")

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = LocalContext.current.getString(R.string.send_code_to_partner),
            fontSize = 16.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        // Code card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.95f))
                .padding(vertical = 25.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = code,
                fontSize = 48.sp,
                color = pink,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(16.dp))

        // Share button styled as card
        CardLikeButton(onClick = onShare) {
            Text(
                text = LocalContext.current.getString(R.string.send_partner_code),
                fontSize = 16.sp,
                color = Color.Black
            )
        }

        Spacer(Modifier.height(20.dp))
        Divider(color = Color.Black.copy(alpha = 0.3f))
    }
}

@Composable
private fun EnterCodeSection(
    code: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    onConnect: () -> Unit,
    errorMessage: String?
) {
    val ctx = LocalContext.current

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = ctx.getString(R.string.enter_partner_code),
            fontSize = 16.sp,
            color = Color.Black
        )
        Spacer(Modifier.height(12.dp))

        // Input card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.95f))
                .padding(vertical = 2.dp, horizontal = 12.dp)
        ) {
            TextField(
                value = code,
                onValueChange = {
                    // Keep only digits, max 8
                    val digits = it.filter { ch -> ch.isDigit() }
                    onCodeChange(digits.take(8))
                },
                singleLine = true,
                placeholder = { Text(ctx.getString(R.string.enter_code)) },
                textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        val canConnect = code.length == 8 && !isLoading
        GradientActionButton(
            enabled = canConnect,
            onClick = onConnect
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(text = ctx.getString(R.string.connecting_status), fontSize = 18.sp, color = Color.White)
                } else {
                    Text(text = ctx.getString(R.string.connect), fontSize = 18.sp, color = Color.White)
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = errorMessage,
                fontSize = 14.sp,
                color = Color(0xFFD32F2F),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// --- Building blocks --------------------------------------------------------

@Composable
private fun CardLikeBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .padding(vertical = 16.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun CardLikeButton(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        color = Color.White.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .noRippleClickable(onClick)
        ) { content() }
    }
}

@Composable
private fun GradientActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(hexColor("#FD267A"), hexColor("#FF655B"))
    )
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .shadow(10.dp, shape)
            .background(gradient)
            .alphaIf(!enabled, 0.5f)
            .noRippleClickable(onClick, enabled)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// --- ViewModel & Service layer ---------------------------------------------

data class PartnerCodeUiState(
    val generatedCode: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isConnected: Boolean = false,
)

enum class ConnectContext { DAILY_QUESTION }

interface PartnerCodeService {
    /** Return true if the current user already has a connected partner */
    suspend fun checkExistingConnection(): Boolean

    /** Generate a partner code for the current user */
    suspend fun generatePartnerCode(): Result<String>

    /** Attempt to connect with a partner using the provided code */
    suspend fun connectWithPartnerCode(code: String, context: ConnectContext): Result<Unit>
}

class PartnerCodeViewModel(private val service: PartnerCodeService) : ViewModel() {
    private val _uiState = MutableStateFlow(PartnerCodeUiState())
    val uiState: StateFlow<PartnerCodeUiState> = _uiState.asStateFlow()

    var enteredCode by mutableStateOf("")
        private set

    var isConnecting by mutableStateOf(false)
        private set

    fun onEnteredCodeChange(newValue: String) {
        enteredCode = newValue
    }

    fun checkExistingConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { service.checkExistingConnection() }
                .onSuccess { connected ->
                    _uiState.update { it.copy(isConnected = connected, isLoading = false) }
                }
                .onFailure { e ->
                    // Not critical; just continue flow
                    _uiState.update { it.copy(isConnected = false, isLoading = false, errorMessage = e.message) }
                }
        }
    }

    fun generateCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, generatedCode = null) }
            service.generatePartnerCode()
                .onSuccess { code ->
                    _uiState.update { it.copy(isLoading = false, generatedCode = code, errorMessage = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, generatedCode = null, errorMessage = e.message ?: "") }
                }
        }
    }

    fun connectWithCode(context: ConnectContext) {
        val code = enteredCode
        if (code.isBlank()) return
        viewModelScope.launch {
            isConnecting = true
            _uiState.update { it.copy(errorMessage = null) }
            service.connectWithPartnerCode(code, context)
                .onSuccess {
                    _uiState.update { it.copy(isConnected = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = e.message ?: "Unknown error") }
                }
            isConnecting = false
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun factory(service: PartnerCodeService) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PartnerCodeViewModel(service) as T
            }
        }
    }
}

@Composable
private fun partnerCodeViewModel(service: PartnerCodeService): PartnerCodeViewModel {
    return viewModel(factory = PartnerCodeViewModel.factory(service))
}

// --- Utils ------------------------------------------------------------------

private fun hexColor(hex: String): Color {
    var clean = hex.trim().removePrefix("#")
    if (clean.length == 6) clean = "FF$clean" // add full alpha if missing
    val argb = clean.toLong(16).toInt()
    return Color(argb)
}

private fun shareText(context: android.content.Context, text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun maxDp(a: Dp, b: Dp): Dp = if (a >= b) a else b

private fun Modifier.noRippleClickable(onClick: () -> Unit, enabled: Boolean = true): Modifier =
    this.then(Modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onTap = { onClick() })
    })

private fun Modifier.alphaIf(condition: Boolean, alpha: Float): Modifier =
    if (condition) this.then(Modifier.graphicsLayer { this.alpha = alpha }) else this

// --- Preview (replace with your own tooling) --------------------------------
/*
@Preview(showBackground = true)
@Composable
private fun DailyQuestionPartnerCodeScreenPreview() {
    val fakeService = object : PartnerCodeService {
        override suspend fun checkExistingConnection(): Boolean = false
        override suspend fun generatePartnerCode(): Result<String> = Result.success("12345678")
        override suspend fun connectWithPartnerCode(code: String, context: ConnectContext): Result<Unit> = Result.success(Unit)
    }
    DailyQuestionPartnerCodeScreen(onDismiss = {}, service = fakeService)
}
*/
