package com.love2loveapp.views.dailyquestion

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.love2loveapp.AppDelegate
import com.love2loveapp.R
import com.love2loveapp.models.DailyQuestion
import com.love2loveapp.models.QuestionResponse
import com.love2loveapp.services.dailyquestion.DailyQuestionRepository
import com.love2loveapp.utils.HandleFirstMessageNotificationPermission
import kotlinx.coroutines.launch

/**
 * 💬 DailyQuestionMainScreen - Page Principale avec Messagerie
 * Design exact selon RAPPORT_DESIGN_QUESTION_DU_JOUR.md
 * 
 * Background: RGB(247, 247, 247) - Gris clair uniforme
 * Carte: Dégradé rose header + corps sombre sophistiqué
 * Chat: Bulles asymétriques avec limitation largeur 70%
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQuestionMainScreen(
    dailyQuestionRepository: DailyQuestionRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // 📊 États observés
    val currentQuestion by dailyQuestionRepository.currentQuestion.collectAsStateWithLifecycle()
    val responses by dailyQuestionRepository.responses.collectAsStateWithLifecycle()
    val isLoading by dailyQuestionRepository.isLoading.collectAsStateWithLifecycle()
    val errorMessage by dailyQuestionRepository.errorMessage.collectAsStateWithLifecycle()

    // 🎯 État local
    var messageText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    
    // 🔔 Gestion permission notifications pour premier message
    val currentUserId = AppDelegate.appState.currentUser.value?.id
    var shouldRequestNotificationPermission by remember { mutableStateOf(false) }
    
    // Vérifier si c'est le premier message de l'utilisateur
    val userHasPostedBefore = responses.any { it.userId == currentUserId }

    // 🔄 Auto-scroll vers le bas lors de nouveaux messages et quand le clavier apparaît
    LaunchedEffect(responses.size) {
        if (responses.isNotEmpty()) {
            listState.animateScrollToItem(responses.size)
        }
    }
    
    // 📱 Auto-scroll vers le bas quand le clavier apparaît
    // Auto-scroll pour nouveaux messages est déjà géré par LaunchedEffect(responses.size)
    
    // 📝 Fonction d'envoi de message avec gestion notification permission
    fun sendMessage() {
        if (messageText.isNotBlank() && !isSubmitting) {
            val isFirstMessage = !userHasPostedBefore
            
            isSubmitting = true
            scope.launch {
                try {
                    dailyQuestionRepository.submitResponse(messageText.trim())
                    messageText = ""
                    keyboardController?.hide()
                    
                    // 🔔 Déclencher demande permission si premier message
                    if (!userHasPostedBefore) {
                        shouldRequestNotificationPermission = true
                    }
                } finally {
                    isSubmitting = false
                }
            }
        }
    }

    // 🎨 Interface avec Scaffold pour une gestion élégante du clavier
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF7F7F7), // Gris clair uniforme selon rapport
        bottomBar = {
            // 📝 Barre de Saisie collée au clavier avec imePadding
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(), // ✅ imePadding sur la bottomBar pour qu'elle suive le clavier
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.daily_question_type_response), // ✅ Clé de traduction
                                color = Color.Gray
                            )
                        },
                        shape = RoundedCornerShape(25.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { sendMessage() }
                        )
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    FloatingActionButton(
                        onClick = { sendMessage() },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFFFD267A), // Rose Love2Love selon rapport
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 4.dp
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = stringResource(R.string.daily_question_send_button), // ✅ Clé de traduction
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // ✅ Utilisation correcte du padding du Scaffold
                .pointerInput(Unit) {
                    // 🎯 Fermer clavier au tap en dehors des champs de saisie
                    detectTapGestures(
                        onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // 📋 Header Principal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.daily_question_title), // ✅ Clé de traduction
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    // TODO: Sous-titre freemium dynamique si nécessaire
                    // Text(subtitle, style = caption, color = secondary)
                }
            }

            if (currentQuestion != null) {
                // 🃏 Carte Question selon rapport - Design signature Love2Love
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp) // Marges réduites selon rapport
                        .padding(top = 12.dp, bottom = 8.dp), // Padding compact selon rapport
                    shape = RoundedCornerShape(20.dp), // Coins arrondis selon rapport
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp) // Ombre portée selon rapport
                ) {
                    Column {
                        // 📍 HEADER ROSE - Design signature Love2Love
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF6699), // RGB(255, 102, 153) selon rapport
                                            Color(0xFFFF99CC)  // RGB(255, 153, 204) selon rapport
                                        )
                                    )
                                )
                                .padding(vertical = 20.dp), // Padding vertical selon rapport
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Love2Love", // Texte fixe selon rapport
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        // 📍 CORPS SOMBRE - Question principale
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp) // Hauteur minimum selon rapport
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF331A26), // RGB(51, 26, 38) selon rapport
                                            Color(0xFF66334D), // RGB(102, 51, 77) selon rapport
                                            Color(0xFF994D33)  // RGB(153, 77, 51) selon rapport
                                        )
                                    )
                                )
                                .padding(horizontal = 30.dp, vertical = 30.dp), // Padding texte selon rapport
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentQuestion!!.getLocalizedText(context), // ✅ Question localisée
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp // lineSpacing(6) selon rapport
                            )
                        }
                    }
                }

                // 💬 Section Chat
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp // ✅ Padding normal, le Scaffold gère l'espace de la bottomBar
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (responses.isEmpty()) {
                        // Message d'encouragement
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.daily_question_start_conversation), // ✅ Clé de traduction
                                    fontSize = 16.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Messages existants avec état stable
                        itemsIndexed(responses) { index, response ->
                            val currentUserId = AppDelegate.appState.currentUser.value?.id
                            val isCurrentUser = response.userId == currentUserId
                            val isPreviousSameSender = index > 0 && 
                                responses[index - 1].userId == response.userId
                            val isLastMessage = response.id == responses.lastOrNull()?.id

                            ChatMessageView(
                                response = response,
                                isCurrentUser = isCurrentUser,
                                isLastMessage = isLastMessage,
                                isPreviousSameSender = isPreviousSameSender,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Spacer invisible pour scroll automatique
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

            } else {
                // État de chargement
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFD267A), // Rose Love2Love
                            modifier = Modifier.size(48.dp)
                        )

                        Text(
                            text = stringResource(R.string.daily_question_preparing),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = stringResource(R.string.daily_question_preparing_subtitle),
                            fontSize = 14.sp,
                            color = Color.Black.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            } // ✅ Fermeture du Column principal
        }
        
        // 🔔 Gestion permission notifications après premier message
        HandleFirstMessageNotificationPermission(
            shouldTrigger = shouldRequestNotificationPermission,
            onPermissionRequestCompleted = {
                shouldRequestNotificationPermission = false
            }
        )
    }
}

/**
 * 💬 Messages Chat selon rapport - Bulles asymétriques avec limitation largeur 70%
 */
@Composable
private fun ChatMessageView(
    response: QuestionResponse,
    isCurrentUser: Boolean,
    isLastMessage: Boolean,
    isPreviousSameSender: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp, // Marges latérales chat selon rapport
                vertical = if (isPreviousSameSender) 1.5.dp else 3.dp // Espacement intelligent selon rapport
            ),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        if (isCurrentUser) {
            // Message utilisateur (droite)
            Spacer(modifier = Modifier.weight(0.3f)) // Limite largeur 70% selon rapport
            
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp), // Coins arrondis selon rapport
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFD267A) // Rose Love2Love selon rapport
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = response.text,
                        fontSize = 17.sp,
                        color = Color.White, // Texte utilisateur selon rapport
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp) // Padding interne bulle selon rapport
                    )
                }
                
                // Heure seulement sur dernier message selon rapport
                if (isLastMessage) {
                    Text(
                        text = "12:34", // TODO: Format time from response.timestamp
                        fontSize = 11.sp, // caption2 selon rapport
                        color = Color.Gray, // secondary selon rapport
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        } else {
            // Message partenaire (gauche)
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp), // Coins arrondis selon rapport
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF2F2F7) // Gris système clair selon rapport
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = response.text,
                        fontSize = 17.sp,
                        color = Color.Black, // Texte partenaire selon rapport
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp) // Padding interne bulle selon rapport
                    )
                }
                
                // Heure seulement sur dernier message selon rapport
                if (isLastMessage) {
                    Text(
                        text = "12:34", // TODO: Format time from response.timestamp
                        fontSize = 11.sp, // caption2 selon rapport
                        color = Color.Gray, // secondary selon rapport
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(0.3f)) // Limite largeur 70% selon rapport
        }
    }
}