// SavedChallengesScreen.kt
@file:Suppress("unused")

package com.yourapp.savedchallenges

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// -------------------------------
// Data models
// -------------------------------
data class SavedChallenge(
    val id: String,
    val challengeKey: String,      // pour logs/diagnostic
    val localizedText: String      // texte déjà localisé côté Android
)

// -------------------------------
// Repository (remplace Fake par ta vraie implémentation Firebase/Firestore)
// -------------------------------
interface SavedChallengesRepository {
    suspend fun loadAll(): List<SavedChallenge>
    suspend fun delete(challenge: SavedChallenge)
}

class FakeSavedChallengesRepository : SavedChallengesRepository {
    private val store = mutableListOf(
        SavedChallenge("1", "dc_001", "Écris une lettre d’admiration à ton/ta partenaire ✍️"),
        SavedChallenge("2", "dc_002", "Partage 3 souvenirs qui te font sourire ensemble 😊"),
        SavedChallenge("3", "dc_003", "Planifiez un mini-rituel du soir rien qu’à vous 🌙")
    )
    override suspend fun loadAll(): List<SavedChallenge> {
        delay(650) // simulate network
        return store.toList()
    }
    override suspend fun delete(challenge: SavedChallenge) {
        delay(200)
        store.removeIf { it.id == challenge.id }
    }
}

// -------------------------------
// ViewModel-like (sans Android ViewModel pour rester 1 fichier)
// -------------------------------
class SavedChallengesState(
    private val repo: SavedChallengesRepository
) {
    var isLoading by mutableStateOf(true)
        private set
    var savedChallenges by mutableStateOf(listOf<SavedChallenge>())
        private set

    suspend fun configure(/* appState: AppState? = null */) {
        // Ici tu peux brancher appState si besoin
        refresh()
    }

    suspend fun refresh() {
        isLoading = true
        savedChallenges = repo.loadAll()
        isLoading = false
    }

    suspend fun delete(challenge: SavedChallenge) {
        repo.delete(challenge)
        savedChallenges = repo.loadAll()
    }
}

// -------------------------------
// Composable root
// -------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedChallengesScreen(
    onClose: () -> Unit,
    repository: SavedChallengesRepository = FakeSavedChallengesRepository() // injecte le tien ici
) {
    val scope = rememberCoroutineScope()
    val state = remember { SavedChallengesState(repository) }

    // Équivalent de onAppear
    LaunchedEffect(Unit) {
        state.configure()
    }

    val bg = Color(red = 0.97f, green = 0.97f, blue = 0.98f)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.saved_challenges_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1
                    )
                },
                actions = { // espace pour équilibrer la barre (comme le Spacer à droite)
                    Spacer(modifier = Modifier.width(48.dp))
                }
            )
        },
        containerColor = bg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(padding)
        ) {
            when {
                state.isLoading -> LoadingState()
                state.savedChallenges.isEmpty() -> EmptyState()
                else -> SavedChallengesContent(
                    challenges = state.savedChallenges,
                    onDelete = { ch ->
                        scope.launch { state.delete(ch) }
                    }
                )
            }
        }
    }
}

// -------------------------------
// Loading
// -------------------------------
@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.saved_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// -------------------------------
// Empty
// -------------------------------
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = Color.Gray
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.saved_empty_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.saved_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// -------------------------------
// Content (carte + swipe + bouton Supprimer)
// -------------------------------
@Composable
private fun SavedChallengesContent(
    challenges: List<SavedChallenge>,
    onDelete: (SavedChallenge) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var showDelete by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<SavedChallenge?>(null) }

    // Ajuste l'index si la liste change (après suppression)
    LaunchedEffect(challenges.size) {
        if (challenges.isNotEmpty() && currentIndex > challenges.lastIndex) {
            currentIndex = challenges.lastIndex
        }
    }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 50.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(challenges, currentIndex) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount -> /* consume only */ },
                        onDragEnd = { /* no-op */ },
                        onDragCancel = { /* no-op */ }
                    )
                }
        ) {
            if (challenges.isNotEmpty()) {
                val current = challenges[currentIndex]
                ChallengeCard(
                    text = current.localizedText,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .pointerInput(currentIndex) {
                            var totalX = 0f
                            detectHorizontalDragGestures(onHorizontalDrag = { _, dx ->
                                totalX += dx
                            }, onDragEnd = {
                                when {
                                    totalX > swipeThresholdPx && currentIndex > 0 -> {
                                        currentIndex -= 1
                                    }
                                    totalX < -swipeThresholdPx && currentIndex < challenges.lastIndex -> {
                                        currentIndex += 1
                                    }
                                }
                                totalX = 0f
                            })
                        }
                )
            }
        }

        // Bouton Supprimer
        val pink = Color(1.0f, 0.4f, 0.6f)
        Button(
            onClick = {
                toDelete = challenges.getOrNull(currentIndex)
                showDelete = (toDelete != null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = pink),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = stringResource(R.string.delete_challenge_button),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showDelete && toDelete != null) {
        DeleteDialog(
            onConfirm = {
                toDelete?.let { onDelete(it) }
                showDelete = false
                toDelete = null
            },
            onCancel = {
                showDelete = false
                toDelete = null
            }
        )
    }
}

// -------------------------------
// Card UI (double dégradé + typo)
// -------------------------------
@Composable
private fun ChallengeCard(
    text: String,
    modifier: Modifier = Modifier
) {
    val headerGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(1.0f, 0.4f, 0.6f), // #FF6699-ish
            Color(1.0f, 0.6f, 0.8f)  // plus clair
        )
    )
    val bodyGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0.2f, 0.1f, 0.15f),
            Color(0.4f, 0.2f, 0.3f),
            Color(0.6f, 0.3f, 0.2f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Transparent)
    ) {
        // Header Love2Love
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerGradient)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Love2Love",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Corps
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(bodyGradient)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 30.dp)
            )
        }
    }
}

// -------------------------------
// Delete Dialog
// -------------------------------
@Composable
private fun DeleteDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(text = stringResource(R.string.delete_confirmation_title))
        },
        text = {
            Text(text = stringResource(R.string.delete_confirmation_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
