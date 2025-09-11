// File: DailyQuestionChatScreen.kt
package com.love2love.dailyquestion.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ---------------------------
// MODELS (simples, toutes plateformes)
// ---------------------------

data class AppUser(
    val uid: String,
    val displayName: String? = null
)

data class AppState(
    val currentUser: AppUser? = null
)

data class MessageSender(
    val userId: String,
    val name: String
) {
    val senderId: String get() = userId
    val displayName: String get() = name
}

data class DailyQuestionResponse(
    val id: String,
    val text: String,
    val userId: String,
    val userName: String? = null,
    val createdAt: Long // epoch millis
)

data class DailyQuestion(
    val id: String,
    val responsesArray: List<DailyQuestionResponse> = emptyList()
)

data class DailyQuestionMessage(
    val id: String,
    val text: String,
    val sender: MessageSender,
    val sentAt: Long,
    val isMine: Boolean
)

// ---------------------------
// SERVICE (Firebase placeholder, prêt à brancher)
// ---------------------------

class DailyQuestionService {

    /**
     * Envoie une réponse texte dans Firestore :
     *   /dailyQuestions/{questionId}/responses/{autoId}
     */
    suspend fun submitResponse(questionId: String, text: String): Boolean {
        Log.d("DailyQuestion", "🚀 submitResponse called (questionId=$questionId, text='${text.take(20)}...')")
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val db = Firebase.firestore

        return try {
            val data = hashMapOf(
                "text" to text,
                "userId" to user.uid,
                "userName" to (user.displayName ?: ""),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("dailyQuestions")
                .document(questionId)
                .collection("responses")
                .add(data)
                .await()
            Log.d("DailyQuestion", "✅ submitResponse success")
            true
        } catch (e: Exception) {
            Log.e("DailyQuestion", "❌ submitResponse error", e)
            false
        }
    }

    /**
     * Conversion utilitaire : responses -> messages pour l’UI
     */
    fun convert(responses: List<DailyQuestionResponse>, currentUserId: String?): List<DailyQuestionMessage> {
        return responses.sortedBy { it.createdAt }.map { r ->
            val sender = MessageSender(
                userId = r.userId,
                name = r.userName ?: "Unknown"
            )
            DailyQuestionMessage(
                id = r.id,
                text = r.text,
                sender = sender,
                sentAt = r.createdAt,
                isMine = (currentUserId != null && currentUserId == r.userId)
            )
        }
    }
}

// ---------------------------
// UI PRINCIPALE
// ---------------------------

@Composable
fun DailyQuestionChatScreen(
    question: DailyQuestion,
    appState: AppState,
    service: DailyQuestionService = remember { DailyQuestionService() }
) {
    Log.d("DailyQuestion", "🔥 Compose DailyQuestionChatScreen started (qid=${question.id})")

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val lovePink = Color(0xFFFD267A)                  // Rose Love2Love (#FD267A)
    val bgGray = Color(0xFFF7F7FA)                    // Fond 0.97/0.97/0.98
    val bubbleGray = Color(0xFFE7E8EC)                // Gris clair bulles reçues

    val currentUid = appState.currentUser?.uid ?: FirebaseAuth.getInstance().currentUser?.uid
    val currentName = appState.currentUser?.displayName
        ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "Me"

    // État UI : liste des messages + champ de saisie
    val messages = remember { mutableStateListOf<DailyQuestionMessage>() }
    var input by remember { mutableStateOf("") }

    // Au premier affichage / et quand responsesArray change → convertir
    LaunchedEffect(question.id, question.responsesArray) {
        Log.d("DailyQuestion", "🔥 updateMessages called (qid=${question.id}, count=${question.responsesArray.size})")
        val converted = service.convert(question.responsesArray, currentUid)
        messages.clear()
        messages.addAll(converted)
        Log.d("DailyQuestion", "   - messages now: ${messages.size}")
    }

    val listState = rememberLazyListState()

    // Scroll vers le bas si les messages changent (comme iOS: scrollToLastItem au démarrage / nouveau msg)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
            Log.d("DailyQuestion", "📜 Scroll to bottom (index=${messages.lastIndex})")
        }
    }

    Scaffold(
        containerColor = bgGray,
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGray)
                .padding(padding)
        ) {
            // ----- LISTE DE MESSAGES (style Twitter : pas d’avatar ni noms) -----
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                itemsIndexed(messages) { index, msg ->
                    val isMine = msg.isMine
                    val alignment =
                        if (isMine) Arrangement.End else Arrangement.Start

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = alignment
                        ) {
                            MessageBubble(
                                text = msg.text,
                                isMine = isMine,
                                lovePink = lovePink,
                                bubbleGray = bubbleGray
                            )
                        }

                        // STYLE TWITTER : Afficher l’heure uniquement sous le tout dernier message
                        val isVeryLast = index == messages.lastIndex
                        if (isVeryLast) {
                            Text(
                                text = formatTime(msg.sentAt),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .align(if (isMine) Alignment.End else Alignment.Start),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // ----- BARRE D’ENTRÉE -----
            InputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    if (input.isBlank()) return@InputBar

                    // Message temporaire immédiat
                    val temp = DailyQuestionMessage(
                        id = "temp-${UUID.randomUUID()}",
                        text = input,
                        sender = MessageSender(
                            userId = currentUid ?: "unknown",
                            name = currentName
                        ),
                        sentAt = System.currentTimeMillis(),
                        isMine = true
                    )
                    messages.add(temp)
                    Log.d("DailyQuestion", "➕ Insert temp message (size=${messages.size})")

                    // Vider l’input
                    input = ""

                    // Analytics
                    Firebase.analytics.logEvent("message_envoye") {
                        param("type", "texte")
                        param("source", "daily_question_compose")
                    }
                    Log.d("DailyQuestion", "📊 Firebase event: message_envoye (type=texte, source=daily_question_compose)")

                    // Envoi asynchrone
                    scope.launch {
                        val ok = service.submitResponse(question.id, temp.text)
                        if (!ok) {
                            Log.e("DailyQuestion", "❌ Échec de l’envoi du message")
                            snackbar.showSnackbar(
                                message = context.getString(R.string.daily_question_send_failed),
                                withDismissAction = true
                            )
                        }
                    }
                }
            )
        }
    }
}

// ---------------------------
// COMPOSANTS UI
// ---------------------------

@Composable
private fun MessageBubble(
    text: String,
    isMine: Boolean,
    lovePink: Color,
    bubbleGray: Color
) {
    val bubbleColor = if (isMine) lovePink else bubbleGray
    val textColor = if (isMine) Color.White else Color.Unspecified

    Surface(
        color = bubbleColor,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier
                .widthIn(min = 40.dp, max = 280.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val placeholderText = stringResource(R.string.daily_question_type_response) // ✅ strings.xml

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            placeholder = {
                Text(
                    placeholderText,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            },
            singleLine = false,
            maxLines = 4,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color(0xFFF0F1F4),
                focusedContainerColor = Color(0xFFF0F1F4),
                disabledContainerColor = Color(0xFFF0F1F4),
                cursorColor = Color.Black,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        val canSend = value.isNotBlank()
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (canSend) Color(0xFFFD267A) else Color(0xFFBDBDBD),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = stringResource(id = R.string.daily_question_send),
                tint = Color.White
            )
        }
    }
}

// ---------------------------
/**
 * Utilitaires
 */
// ---------------------------

private fun formatTime(timestamp: Long, locale: Locale = Locale.getDefault()): String {
    if (timestamp <= 0) return ""
    val sdf = SimpleDateFormat("HH:mm", locale)
    return sdf.format(Date(timestamp))
}

/**
 * Hors Compose (ex. ViewModel/Repository), utilise:
 *   val ctx = ...; ctx.getString(R.string.ma_chaine)
 * En Compose, préfère stringResource(R.string.ma_chaine).
 */
