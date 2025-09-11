@file:Suppress("NAME_SHADOWING")

package com.yourapp.journal

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

// --- Placeholders for your app's types (replace with your real ones) ---
// data class JournalEntry(val id: String, val authorId: String)
// class AppState(
//     val currentUser: User?,
//     val freemiumManager: FreemiumManager?
// )
// object FirebaseService { val currentUser: User? = null }
// object JournalService {
//     val shared: JournalService = this
//     val entries: State<List<JournalEntry>> @Composable get() = remember { mutableStateOf(emptyList()) }
//     fun getMaxFreeEntries(): Int = 10
//     suspend fun refreshEntries() {}
// }
// @Composable fun JournalListView(onCreateEntry: () -> Unit) {}
// @Composable fun CreateJournalEntryView(appState: AppState) {}
// @Composable fun JournalMapView(appState: AppState) {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalView(
    appState: AppState,
    journalService: JournalService = JournalService.shared,
) {
    val context = LocalContext.current
    val analytics = remember { Firebase.analytics }
    val coroutineScope = rememberCoroutineScope()

    var showingCreateEntry by remember { mutableStateOf(false) }
    var showingMapView by remember { mutableStateOf(false) }

    // --- Entries observed from your service (adapt to your implementation) ---
    // If your service exposes a Flow/LiveData, collect/observe it here instead.
    val entries by journalService.entries

    // --- Freemium / subscription logic ---
    val isUserSubscribed = appState.currentUser?.isSubscribed == true
    val currentUserId = FirebaseService.currentUser?.id

    val userEntriesCount by remember(entries, currentUserId) {
        mutableStateOf(
            entries.count { it.authorId == currentUserId }
        )
    }

    val remainingFreeEntries by remember(entries, currentUserId) {
        mutableStateOf(
            (journalService.getMaxFreeEntries() - userEntriesCount).coerceAtLeast(0)
        )
    }

    val canAddEntry by remember(isUserSubscribed, userEntriesCount) {
        mutableStateOf(
            isUserSubscribed || userEntriesCount < journalService.getMaxFreeEntries()
        )
    }

    // --- Handler ported from Swift ---
    val handleAddEntryTap: () -> Unit = remember(userEntriesCount) {
        {
            appState.freemiumManager?.handleJournalEntryCreation(
                currentEntriesCount = userEntriesCount
            ) {
                // Callback called if user can create an entry
                showingCreateEntry = true
            }
        }
    }

    // --- Analytics on appear ---
    LaunchedEffect(Unit) {
        analytics.logEvent("journal_ouvert") {}
        Log.d("Analytics", "📊 Firebase event: journal_ouvert")
    }

    // --- UI ---
    Scaffold(
        containerColor = Color(0xFFF7F7FA) // ~ (0.97, 0.97, 0.98)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF7F7FA))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: map button (no white bubble)
                IconButton(onClick = { showingMapView = true }) {
                    Icon(imageVector = Icons.Filled.Map, contentDescription = "map")
                }

                Spacer(modifier = Modifier.weight(1f))

                // Title
                Text(
                    text = stringResource(R.string.our_journal),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )

                Spacer(modifier = Modifier.weight(1f))

                // Right: plus button (always + even if limit reached)
                IconButton(onClick = { handleAddEntryTap() }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "add")
                }
            }

            // Content (list of entries)
            Box(modifier = Modifier.fillMaxSize()) {
                JournalListView(onCreateEntry = handleAddEntryTap)
            }
        }
    }

    // --- Create entry sheet ---
    if (showingCreateEntry) {
        ModalBottomSheet(
            onDismissRequest = {
                showingCreateEntry = false
                // Force refresh of entries when the sheet closes (port of onDisappear)
                coroutineScope.launch { journalService.refreshEntries() }
            }
        ) {
            CreateJournalEntryView(appState = appState)
        }
    }

    // --- Map sheet ---
    if (showingMapView) {
        ModalBottomSheet(
            onDismissRequest = { showingMapView = false }
        ) {
            JournalMapView(appState = appState)
        }
    }
}

// Optional preview (adjust to your preview infrastructure)
//@Preview(showBackground = true)
//@Composable
//private fun JournalViewPreview() {
//    JournalView(appState = AppState(currentUser = null, freemiumManager = null))
//}
