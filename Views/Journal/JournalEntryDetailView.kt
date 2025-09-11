package com.love2love.journal

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Android/Compose port of the SwiftUI JournalEntryDetailView.
 * - Localization: uses strings.xml via stringResource(...) (Compose equivalent to context.getString(...)).
 * - Authorship check uses FirebaseAuth currentUser.uid vs entry.authorId.
 * - Image loading via Coil's AsyncImage.
 * - Delete confirmation with AlertDialog; deletion delegated to JournalService.
 */

// --- Models -----------------------------------------------------------------

data class JournalLocation(
    val displayName: String
)

data class JournalEntry(
    val id: String,
    val title: String,
    val description: String,
    val eventDate: Instant,
    val createdAt: Instant,
    val authorId: String,
    val authorName: String,
    val imageURL: String? = null,
    val location: JournalLocation? = null
) {
    val hasImage: Boolean get() = !imageURL.isNullOrBlank()
}

// --- Service ---------------------------------------------------------------

interface JournalService {
    suspend fun deleteEntry(entry: JournalEntry)
}

/**
 * Provide your own implementation and inject it. This placeholder exists for compilation.
 */
object JournalServiceSingleton : JournalService {
    override suspend fun deleteEntry(entry: JournalEntry) {
        // TODO: Implement with your Firestore/Storage cleanup if needed.
        // For parity with Swift: journalService.deleteEntry(entry)
        Log.d("JournalService", "deleteEntry id=${entry.id}")
    }
}

// --- ViewModel-ish controller ---------------------------------------------

class JournalEntryDetailController(
    private val journalService: JournalService,
    private val scope: CoroutineScope,
) {
    var isDeleting by mutableStateOf(false)
        private set

    fun deleteEntry(entry: JournalEntry, onResult: (Boolean) -> Unit) {
        Log.d("JournalEntryDetail", "🗑️ Start deleting '${entry.title}' (${entry.id})")
        isDeleting = true
        scope.launch {
            runCatching { journalService.deleteEntry(entry) }
                .onSuccess {
                    Log.d("JournalEntryDetail", "✅ Deletion success")
                    isDeleting = false
                    onResult(true)
                }
                .onFailure { e ->
                    Log.e("JournalEntryDetail", "❌ Deletion error: ${e.message}", e)
                    isDeleting = false
                    onResult(false)
                }
        }
    }
}

// --- Date formatting helpers ----------------------------------------------

private fun formatFullDate(instant: Instant, locale: Locale = Locale.getDefault()): String {
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    return fmt.format(instant.atZone(ZoneId.systemDefault()))
}

private fun formatShortTime(instant: Instant, locale: Locale = Locale.getDefault()): String {
    val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    return fmt.format(instant.atZone(ZoneId.systemDefault()))
}

// --- UI --------------------------------------------------------------------

@Composable
fun JournalEntryDetailScreen(
    entry: JournalEntry,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
    journalService: JournalService = JournalServiceSingleton,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(journalService) { JournalEntryDetailController(journalService, scope) }

    val bg = Color(0xFFF7F8FA) // ≈ RGB 0.97, 0.97, 0.98

    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val isAuthor = remember(entry.authorId, currentUid) { currentUid != null && entry.authorId == currentUid }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp)
        ) {
            Spacer(Modifier.height(80.dp)) // space for fixed header

            if (entry.hasImage) {
                AsyncImage(
                    model = entry.imageURL,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(24.dp))
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Title + description card
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = entry.title,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            lineHeight = 32.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = formatFullDate(entry.eventDate),
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                        if (entry.description.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            androidx.compose.material3.Divider(color = Color.Black.copy(alpha = 0.1f))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = entry.description,
                                fontSize = 16.sp,
                                color = Color.Black.copy(alpha = 0.8f),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Metadata card
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        InfoRow(
                            icon = Icons.Filled.DateRange,
                            title = stringResource(R.string.event_date),
                            value = formatFullDate(entry.eventDate)
                        )
                        Spacer(Modifier.height(12.dp))
                        InfoRow(
                            icon = Icons.Filled.AccessTime,
                            title = stringResource(R.string.event_time),
                            value = formatShortTime(entry.eventDate)
                        )
                        Spacer(Modifier.height(12.dp))
                        InfoRow(
                            icon = Icons.Filled.Person,
                            title = stringResource(R.string.created_by),
                            value = entry.authorName
                        )
                        if (entry.location != null) {
                            Spacer(Modifier.height(12.dp))
                            InfoRow(
                                icon = Icons.Filled.Place,
                                title = stringResource(R.string.location),
                                value = entry.location.displayName
                            )
                        }
                    }
                }
            }
        }

        // Fixed header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .padding(horizontal = 20.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { Log.d("JournalEntryDetail", "❌ Close tapped"); onClose() }
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }

            Spacer(Modifier.weight(1f))

            if (isAuthor) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(enabled = !controller.isDeleting) {
                            Log.d("JournalEntryDetail", "🗑️ Delete tapped")
                            showDeleteDialog = true
                        }
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = Color.White
                    )
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = {
                    Log.d("JournalEntryDetail", "🗑️ Delete dialog dismissed")
                    showDeleteDialog = false
                },
                title = { Text(text = stringResource(R.string.delete_memory_title), style = MaterialTheme.typography.titleLarge) },
                text = { Text(text = stringResource(R.string.irreversible_action)) },
                confirmButton = {
                    TextButton(
                        enabled = !controller.isDeleting,
                        onClick = {
                            showDeleteDialog = false
                            controller.deleteEntry(entry) { ok ->
                                if (ok) onDeleted() else Log.e("JournalEntryDetail", "Deletion failed")
                            }
                        }
                    ) { Text(text = stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            color = Color.Black.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/*
strings.xml keys referenced (place in res/values/strings.xml):

<resources>
    <string name="event_date">Date</string>
    <string name="event_time">Heure</string>
    <string name="created_by">Créé par</string>
    <string name="location">Lieu</string>
    <string name="delete_memory_title">Supprimer ce souvenir ?</string>
    <string name="irreversible_action">Cette action est irréversible.</string>
    <string name="cancel">Annuler</string>
    <string name="delete">Supprimer</string>
    <string name="close">Fermer</string>
</resources>

Gradle dependency for Coil (build.gradle):
implementation("io.coil-kt:coil-compose:2.6.0")

Usage example:
JournalEntryDetailScreen(
    entry = entry,
    onClose = { navController.popBackStack() },
    onDeleted = { navController.popBackStack() },
    journalService = yourInjectedService
)
*/
