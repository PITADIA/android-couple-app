package com.love2love.daily

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * DailyQuestionMainScreen — Jetpack Compose port of the SwiftUI DailyQuestionMainView.
 *
 * \uD83D\uDD11 Localisation: All NSLocalizedString(...) calls have been migrated to
 * Compose's stringResource(R.string.<key>). Add the string keys listed at the bottom
 * of this file to your res/values/strings.xml.
 *
 * \uD83D\uDCE3 Notes d'intégration:
 * - Remplace les stubs (DailyQuestionService, PartnerConnectionNotificationService, AppState)
 *   par tes implémentations existantes. Les signatures imitent de près celles utilisées côté iOS.
 * - Passe un onDismiss() (par ex. navController::popBackStack) pour gérer dismiss().
 * - Pour la permission Android 13+ POST_NOTIFICATIONS, la demande est effectuée automatiquement
 *   une seule fois par utilisateur (persistée via SharedPreferences) lorsque l'écran apparaît.
 * - Les notifications programmées devraient être taguées avec "new_message_<questionId>" pour
 *   permettre clearAllNotificationsForQuestion(...). Voir les TODO dans la fonction correspondante.
 */

@Composable
fun DailyQuestionMainScreen(
    appState: AppState,
    dailyQuestionService: DailyQuestionService = DailyQuestionService.shared,
    partnerService: PartnerConnectionNotificationService = PartnerConnectionNotificationService.shared,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    // Service state (replace by collectAsState() if your service exposes StateFlow)
    val isLoading by dailyQuestionService.isLoading.collectAsState()
    val isOptimizing by dailyQuestionService.isOptimizing.collectAsState()
    val currentQuestion by dailyQuestionService.currentQuestion.collectAsState()
    val allQuestionsExhausted by dailyQuestionService.allQuestionsExhausted.collectAsState()

    val isBusy = isLoading || isOptimizing

    // Partner overlay state
    val shouldShowConnectionSuccess by partnerService.shouldShowConnectionSuccess.collectAsState()
    val connectedPartnerName by partnerService.connectedPartnerName.collectAsState()

    // Stable messages cache to avoid LazyColumn recomposition churn
    val stableMessages = remember { mutableStateListOf<QuestionResponse>() }

    // Text input state
    var responseText by remember { mutableStateOf("") }
    var isSubmittingResponse by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Notifications: request permission once
    RequestNotificationPermissionIfNeeded(appState = appState)

    // Clear notifications for current question when entering
    LaunchedEffect(currentQuestion?.id) {
        currentQuestion?.let { clearAllNotificationsForQuestion(context, it.id, dailyQuestionService) }
        BadgeManager.clearBadge(context)
    }

    // Load if needed (idempotent guard similar to iOS code)
    LaunchedEffect(Unit) {
        if (!isLoading && !isOptimizing && currentQuestion == null && !allQuestionsExhausted) {
            dailyQuestionService.checkForNewQuestionWithTimezoneOptimization()
        }
    }

    // Keep stable messages in sync (sorted by respondedAt)
    LaunchedEffect(currentQuestion?.responses) {
        val newMessages = (currentQuestion?.responses ?: emptyList()).sortedBy { it.respondedAt }
        if (newMessages.size != stableMessages.size || newMessages.lastOrNull()?.id != stableMessages.lastOrNull()?.id) {
            stableMessages.clear()
            stableMessages.addAll(newMessages)
        }
    }

    // Whole screen background + header
    Surface(color = Color(0xFFF7F7FA)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(24.dp)) // balance visual since title is centered
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.daily_question_title),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )

                        getDailyQuestionSubtitle(appState)?.let { subtitle ->
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // Content states
                Box(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isTextFieldFocused) {
                        detectTapGestures(onTap = {
                            if (isTextFieldFocused) {
                                focusManager.clearFocus()
                                keyboard?.hide()
                            } else {
                                onDismiss()
                            }
                        })
                    }) {
                    when {
                        isNoPartnerState(appState) -> {
                            NoPartnerView()
                        }
                        allQuestionsExhausted -> {
                            ExhaustedQuestionsView()
                        }
                        isBusy && currentQuestion == null -> {
                            LoadingView()
                        }
                        currentQuestion != null -> {
                            QuestionContent(
                                appState = appState,
                                question = currentQuestion!!,
                                stableMessages = stableMessages,
                                responseText = responseText,
                                onResponseTextChange = { responseText = it },
                                isTextFieldFocused = isTextFieldFocused,
                                onTextFieldFocusChange = { isTextFieldFocused = it },
                                onSend = {
                                    val textToSubmit = responseText.trim()
                                    if (textToSubmit.isNotEmpty()) {
                                        isSubmittingResponse = true

                                        // optimistic UI
                                        val temp = QuestionResponse(
                                            id = UUID.randomUUID().toString(),
                                            userId = appState.currentUser?.id.orEmpty(),
                                            userName = appState.currentUser?.name ?: "Vous",
                                            text = textToSubmit,
                                            status = ResponseStatus.ANSWERED,
                                            respondedAt = System.currentTimeMillis()
                                        )
                                        stableMessages.add(temp)
                                        responseText = ""
                                        focusManager.clearFocus()
                                        keyboard?.hide()

                                        // Firebase Analytics event (optional)
                                        try {
                                            dailyQuestionService.logMessageSent()
                                        } catch (_: Throwable) { }

                                        scope.launch {
                                            val success = dailyQuestionService.submitResponse(textToSubmit)
                                            if (success) {
                                                // optional in-app review request
                                                try { dailyQuestionService.maybeRequestReviewAfterDailyCompletion() } catch (_: Throwable) {}
                                                delay(300)
                                                onDismiss()
                                            } else {
                                                // rollback optimistic message
                                                stableMessages.removeAll { it.id == temp.id }
                                                responseText = textToSubmit
                                            }
                                            isSubmittingResponse = false
                                        }
                                    }
                                },
                                onRefresh = {
                                    scope.launch {
                                        if (!allQuestionsExhausted) {
                                            dailyQuestionService.checkForNewQuestionWithTimezoneOptimization()
                                        }
                                    }
                                },
                            )
                        }
                        else -> {
                            NoQuestionView(onGenerate = {
                                scope.launch { dailyQuestionService.checkForNewQuestionWithTimezoneOptimization() }
                            })
                        }
                    }
                }
            }

            // Partner connection success overlay
            AnimatedVisibility(visible = shouldShowConnectionSuccess) {
                PartnerConnectionSuccessOverlay(
                    partnerName = connectedPartnerName,
                    onDismiss = { partnerService.dismissConnectionSuccess() }
                )
            }
        }
    }
}

// ==== Sub-views ==============================================================

@Composable
private fun NoPartnerView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(60.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.daily_question_no_partner_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.daily_question_no_partner_message),
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}

@Composable
private fun LoadingView() {
    val infinite = rememberInfiniteTransition(label = "spinner")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "spin"
    )
    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Simple ring via Box with border (for brevity, using text placeholder)
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.Transparent, shape = CircleShape)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.daily_question_preparing),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.daily_question_preparing_subtitle),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 30.dp)
        )
    }
}

@Composable
private fun NoQuestionView(onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(60.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_question_available),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onGenerate,
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .height(50.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.generate_question),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ExhaustedQuestionsView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(40.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFD267A), modifier = Modifier.size(60.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.daily_questions_exhausted_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.daily_questions_exhausted_message),
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFD267A).copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.daily_questions_exhausted_stats_simple),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFD267A),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.daily_questions_congratulations),
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.daily_questions_all_completed),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.daily_questions_new_cycle),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun QuestionContent(
    appState: AppState,
    question: DailyQuestion,
    stableMessages: List<QuestionResponse>,
    responseText: String,
    onResponseTextChange: (String) -> Unit,
    isTextFieldFocused: Boolean,
    onTextFieldFocusChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new message arrives or focus changes
    LaunchedEffect(stableMessages.size, isTextFieldFocused) {
        kotlinx.coroutines.delay(100)
        listState.animateScrollToItem(maxOf(stableMessages.size - 1, 0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Card with question text
        DailyQuestionCard(question = question)
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp, bottom = 8.dp)

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (stableMessages.isEmpty()) {
                item(key = "encouragement") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.daily_question_start_conversation),
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(stableMessages, key = { _, it -> it.id }) { index, msg ->
                    val isCurrentUser = msg.userId == appState.currentUser?.id
                    val isLast = msg.id == stableMessages.lastOrNull()?.id
                    val isPrevSame = index > 0 && stableMessages[index - 1].userId == msg.userId
                    ChatMessageBubble(
                        response = msg,
                        isCurrentUser = isCurrentUser,
                        isLastMessage = isLast,
                        isPreviousSameSender = isPrevSame,
                        partnerName = msg.userName,
                        onReport = { reportMessage(msg) }
                    )
                }
                item(key = "bottom-spacer") { Spacer(modifier = Modifier.height(1.dp)) }
            }
        }

        // Input bar
        InputBar(
            value = responseText,
            onValueChange = onResponseTextChange,
            placeholderRes = R.string.daily_question_type_response,
            onFocusChange = onTextFieldFocusChange,
            onSend = onSend
        )
    }
}

@Composable
private fun DailyQuestionCard(question: DailyQuestion) {
    val context = LocalContext.current
    val text = question.localizedText(context)

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFFF6699), Color(0xFFFF99CC))
                    )
                )
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Love2Love",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
        // Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF332633),
                            Color(0xFF66334D),
                            Color(0xFF994C33)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 30.dp)
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    response: QuestionResponse,
    isCurrentUser: Boolean,
    partnerName: String,
    isLastMessage: Boolean,
    isPreviousSameSender: Boolean,
    onReport: () -> Unit,
) {
    val context = LocalContext.current
    val timeString = DateFormat.getTimeFormat(context).format(Date(response.respondedAt))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = if (isPreviousSameSender) 1.5.dp else 3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (isCurrentUser) {
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 0.dp)
                        .widthIn(max = 280.dp)
                        .background(Color(0xFFFD267A), RoundedCornerShape(18.dp))
                        .clickable(onLongClick = onReport, onClick = {} )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(response.text, color = Color.White, fontSize = 17.sp)
                }
                if (isLastMessage) {
                    Text(
                        text = timeString,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp)
                    )
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.Start) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 0.dp)
                        .widthIn(max = 280.dp)
                        .background(Color(0xFFF2F2F7), RoundedCornerShape(18.dp))
                        .clickable(onLongClick = onReport, onClick = {})
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(response.text, color = Color(0xFF111111), fontSize = 17.sp)
                }
                if (isLastMessage) {
                    Text(
                        text = timeString,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp, start = 8.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes placeholderRes: Int,
    onFocusChange: (Boolean) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Divider(color = Color(0x4D000000))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            var internalFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(placeholderRes), fontSize = 16.sp, color = Color.Gray) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp)
                    .onFocusChanged { st ->
                        internalFocused = st.isFocused
                        onFocusChange(st.isFocused)
                    },
                maxLines = 6,
                shape = RoundedCornerShape(25.dp)
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onSend,
                enabled = value.trim().isNotEmpty(),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFD267A))
            ) {
                Text("✈\uFE0F", color = Color.White, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PartnerConnectionSuccessOverlay(
    partnerName: String?,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = partnerName?.let { "${it} connecté(e) !" } ?: "Partenaire connecté !",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}

// ==== Helpers / Logic ========================================================

private fun isNoPartnerState(appState: AppState): Boolean {
    val pid = appState.currentUser?.partnerId?.trim().orEmpty()
    return pid.isEmpty()
}

private fun getDailyQuestionSubtitle(appState: AppState): String? {
    val user = appState.currentUser ?: return null
    val partnerId = user.partnerId?.trim().orEmpty()
    if (partnerId.isEmpty()) return null
    val day = calculateCurrentQuestionDay(user)
    return appState.freemiumManager?.getDailyQuestionSubtitle(day)
}

private fun calculateCurrentQuestionDay(user: User): Int {
    val start = user.relationshipStartDate ?: return 1
    val startUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    startUtc.timeInMillis = start
    val todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val days = ((todayUtc.timeInMillis - startUtc.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
    return days + 1
}

private fun reportMessage(response: QuestionResponse) {
    // TODO: Bridge to your Firebase Functions:
    // functions.getHttpsCallable("reportInappropriateContent").call(mapOf(...))
    Log.d("DailyQuestion", "Report message id=${response.id} user=${response.userId}")
}

private fun clearAllNotificationsForQuestion(
    context: Context,
    questionId: String,
    dailyQuestionService: DailyQuestionService
) {
    try {
        // 1) Cancel scheduled work by tag (if you scheduled via WorkManager)
        WorkManager.getInstance(context).cancelAllWorkByTag("new_message_${questionId}")
        // 2) Cancel delivered notifications using tag + any ID you used
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // If you used notify(tag, id):
        // nm.cancel("new_message_${questionId}", NOTIF_ID)
        // As a fallback, you could clear all, but prefer targeted cancel:
        // nm.cancelAll()
        // 3) Keep service in sync (mirror of iOS clearNotificationsForQuestion)
        dailyQuestionService.clearNotificationsForQuestion(questionId)
    } catch (t: Throwable) {
        Log.w("DailyQuestion", "Failed to clear notifications: ${t.message}")
    }
}

@Composable
private fun RequestNotificationPermissionIfNeeded(appState: AppState) {
    val context = LocalContext.current
    var hasRequestedThisSession by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                try { DailyQuestionService.shared.requestFcmTokenAndSave() } catch (_: Throwable) {}
            }
            markNotificationsRequested(context, appState.currentUser?.id)
        }
    )

    LaunchedEffect(appState.currentUser?.id) {
        if (!hasRequestedThisSession && shouldAskNotificationPermission(context, appState.currentUser?.id)) {
            hasRequestedThisSession = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                if (status != PackageManager.PERMISSION_GRANTED) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    DailyQuestionService.shared.requestFcmTokenAndSave()
                    markNotificationsRequested(context, appState.currentUser?.id)
                }
            } else {
                // No runtime permission pre-Android 13
                DailyQuestionService.shared.requestFcmTokenAndSave()
                markNotificationsRequested(context, appState.currentUser?.id)
            }
        }
    }
}

private fun shouldAskNotificationPermission(context: Context, userId: String?): Boolean {
    if (userId.isNullOrBlank()) return false
    val key = "notifications_requested_${userId}"
    return !context.getSharedPreferences("dq_prefs", Context.MODE_PRIVATE).getBoolean(key, false)
}

private fun markNotificationsRequested(context: Context, userId: String?) {
    if (userId.isNullOrBlank()) return
    val key = "notifications_requested_${userId}"
    context.getSharedPreferences("dq_prefs", Context.MODE_PRIVATE)
        .edit().putBoolean(key, true).apply()
}

// ==== Models / Service stubs (replace with your real implementations) ========

data class User(
    val id: String,
    val name: String?,
    val partnerId: String?,
    /** relationshipStartDate in epoch millis (UTC) */
    val relationshipStartDate: Long?
)

class AppState(
    val currentUser: User?,
    val freemiumManager: FreemiumManager?
)

interface FreemiumManager {
    fun getDailyQuestionSubtitle(currentDay: Int): String?
}

data class DailyQuestion(
    val id: String,
    /** If your questions are static & localized via resources, provide a string key like "dq_001" */
    val stringKey: String? = null,
    /** Fallback/raw text (e.g., remote, dynamic). Used if no string resource found. */
    val rawText: String? = null,
    val responses: List<QuestionResponse> = emptyList(),
)

fun DailyQuestion.localizedText(context: Context): String {
    val key = stringKey
    if (!key.isNullOrBlank()) {
        val resId = context.resources.getIdentifier(key, "string", context.packageName)
        if (resId != 0) return context.getString(resId)
    }
    return rawText ?: ""
}

data class QuestionResponse(
    val id: String,
    val userId: String,
    val userName: String,
    val text: String,
    val status: ResponseStatus,
    val respondedAt: Long
)

enum class ResponseStatus { ANSWERED }

interface DailyQuestionService {
    val isLoading: State<Boolean>
    val isOptimizing: State<Boolean>
    val currentQuestion: State<DailyQuestion?>
    val allQuestionsExhausted: State<Boolean>

    suspend fun checkForNewQuestionWithTimezoneOptimization()
    suspend fun submitResponse(text: String): Boolean
    fun clearNotificationsForQuestion(questionId: String)

    fun requestFcmTokenAndSave() { /* optional */ }
    fun logMessageSent() { /* optional analytics */ }
    fun maybeRequestReviewAfterDailyCompletion() { /* optional */ }

    companion object {
        // Provide your real singleton here
        val shared: DailyQuestionService = object : DailyQuestionService {
            override val isLoading: State<Boolean> = mutableStateOf(false)
            override val isOptimizing: State<Boolean> = mutableStateOf(false)
            override val currentQuestion: State<DailyQuestion?> = mutableStateOf(null)
            override val allQuestionsExhausted: State<Boolean> = mutableStateOf(false)
            override suspend fun checkForNewQuestionWithTimezoneOptimization() {}
            override suspend fun submitResponse(text: String): Boolean = true
            override fun clearNotificationsForQuestion(questionId: String) {}
        }
    }
}

interface PartnerConnectionNotificationService {
    val shouldShowConnectionSuccess: State<Boolean>
    val connectedPartnerName: State<String?>
    fun dismissConnectionSuccess()

    companion object {
        val shared: PartnerConnectionNotificationService = object : PartnerConnectionNotificationService {
            override val shouldShowConnectionSuccess: State<Boolean> = mutableStateOf(false)
            override val connectedPartnerName: State<String?> = mutableStateOf(null)
            override fun dismissConnectionSuccess() {}
        }
    }
}

object BadgeManager {
    fun clearBadge(context: Context) {
        // No-op on stock Android; some OEMs expose shortcuts badging APIs.
    }
}

// ==== OPTIONAL: History preview card (port of SwiftUI version) ==============

@Composable
fun HistoryPreviewCard(question: DailyQuestion, rank: Int) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFFFD267A), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$rank", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(question.rawText ?: question.stringKey ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black, maxLines = 2)
                Text(DateFormat.getDateFormat(LocalContext.current).format(Date()), fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDCAC", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("${question.responses.size}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFD267A))
            }
        }
    }
}

/* ============================================================================
 * \uD83D\uDCCC Strings à ajouter dans res/values/strings.xml
 * ----------------------------------------------------------------------------
 * <string name="daily_question_title">Questions du jour</string>
 * <string name="daily_questions_exhausted_title">Bravo, vous avez tout complété !</string>
 * <string name="daily_questions_exhausted_message">Vous avez parcouru toutes les questions disponibles pour le moment.</string>
 * <string name="daily_questions_exhausted_stats_simple">Super progression !</string>
 * <string name="daily_questions_congratulations">Félicitations pour votre constance.</string>
 * <string name="daily_questions_all_completed">Toutes les questions sont complétées.</string>
 * <string name="daily_questions_new_cycle">Un nouveau cycle arrive très bientôt.</string>
 * <string name="daily_question_no_partner_title">Aucun partenaire connecté</string>
 * <string name="daily_question_no_partner_message">Connecte ton partenaire pour commencer à échanger vos réponses.</string>
 * <string name="daily_question_preparing">Préparation en cours…</string>
 * <string name="daily_question_preparing_subtitle">Nous configurons vos questions du jour, cela ne prend qu’un instant.</string>
 * <string name="no_question_available">Aucune question disponible</string>
 * <string name="generate_question">Générer une question</string>
 * <string name="daily_question_start_conversation">Commence la conversation avec un premier message ✨</string>
 * <string name="daily_question_type_response">Écris ta réponse…</string>
 * <string name="time_now">à l’instant</string>
 * <string name="time_minutes_ago">il y a %1$d min</string>
 * <string name="time_hours_ago">il y a %1$d h</string>
 * (ajoute aussi les clés propres à tes questions si tu utilises des resources pour leur texte)
 * ============================================================================
 */
