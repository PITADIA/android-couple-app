package com.love2love.journal

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import com.love2love.journal.R
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

// ------------------------------------------------------------
// Models & Service Abstraction
// ------------------------------------------------------------

@Immutable
data class User(
    val id: String,
    val isSubscribed: Boolean
)

@Immutable
data class AppState(
    val currentUser: User?
)

@Immutable
data class JournalEntry(
    val id: String,
    val title: String,
    val description: String,
    val authorId: String,
    val eventEpochMillis: Long,
    val imageUrl: String? = null
) {
    val hasImage: Boolean get() = !imageUrl.isNullOrBlank()
}

/**
 * Service attendu par l'écran. Implémente-le côté Android (ViewModel + repository, etc.).
 * On expose seulement ce dont la vue a besoin, de manière réactive.
 */
interface JournalService {
    val entries: StateFlow<List<JournalEntry>>
    val isLoading: StateFlow<Boolean>
    fun hasReachedFreeLimit(): Boolean
    fun getRemainingFreeEntries(): Int
}

// ------------------------------------------------------------
// Composables (équivalents Kotlin/Compose du SwiftUI fourni)
// ------------------------------------------------------------

@Composable
fun JournalListScreen(
    appState: AppState,
    journalService: JournalService,
    onCreateEntry: () -> Unit,
    onUnlockPremium: () -> Unit,
    onOpenEntry: (JournalEntry) -> Unit
) {
    val entries by journalService.entries.collectAsState()
    val loading by journalService.isLoading.collectAsState()

    val isUserSubscribed = appState.currentUser?.isSubscribed ?: false
    val hasReachedFreeLimit = journalService.hasReachedFreeLimit()

    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            EmptyJournalState(
                isSubscribed = isUserSubscribed,
                hasReachedLimit = hasReachedFreeLimit,
                onCreateEntry = onCreateEntry,
                onUnlockPremium = onUnlockPremium
            )
        } else {
            val monthGroups = remember(entries) { groupEntriesByMonth(entries) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                monthGroups.forEach { (monthKey, group) ->
                    item(key = "header-$monthKey") {
                        MonthHeader(monthKey)
                    }
                    items(items = group, key = { it.id }) { entry ->
                        JournalEntryCard(
                            entry = entry,
                            isUserEntry = isUserEntry(appState, entry),
                            isSubscribed = isUserSubscribed,
                            onClick = {
                                Log.d("JournalEntryCard", "Click on '${entry.title}' (id=${entry.id})")
                                selectedEntry = entry
                                onOpenEntry(entry) // navigation/feuille gérée par le parent
                            }
                        )
                    }
                }
            }
        }

        if (loading) {
            LoadingOverlay()
        }
    }

    // Si tu préfères afficher une feuille modale ici, tu peux la brancher sur selectedEntry.
    // Ex. Material3 ModalBottomSheet. On laisse la navigation au parent pour rester flexible.
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.loading),
                fontSize = 16.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MonthHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmptyJournalState(
    isSubscribed: Boolean,
    hasReachedLimit: Boolean,
    onCreateEntry: () -> Unit,
    onUnlockPremium: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image statique (mets ton drawable "jou" dans res/drawable)
        Image(
            painter = painterResource(id = R.drawable.jou),
            contentDescription = null,
            modifier = Modifier
                .width(240.dp)
                .height(240.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = if (hasReachedLimit && !isSubscribed) {
                stringResource(id = R.string.limit_reached)
            } else {
                stringResource(id = R.string.empty_journal_message)
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(id = R.string.journal_description),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 30.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Bouton "Créer"
        Button(
            onClick = onCreateEntry,
            shape = RoundedCornerShape(19.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PinkStart)
        ) {
            Row(
                modifier = Modifier.height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_plus), // fournis un pictogramme ou utilise Icons.Default.Add
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.create),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        if (hasReachedLimit && !isSubscribed) {
            Spacer(modifier = Modifier.height(16.dp))
            PremiumCTAButton(onUnlockPremium)
        }
    }
}

@Composable
private fun PremiumCTAButton(onUnlockPremium: () -> Unit) {
    Button(
        onClick = onUnlockPremium,
        shape = RoundedCornerShape(25.dp),
        modifier = Modifier
            .width(200.dp)
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.horizontalGradient(listOf(PinkStart, PinkEnd)),
                    shape = RoundedCornerShape(25.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_crown),
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.unlock_premium_button),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun FreemiumEncouragementCard(
    remainingEntries: Int,
    onUpgrade: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.Yellow.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_star_filled),
                contentDescription = null,
                tint = Color.Yellow
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                // Utiliser un pluriel Android plutôt que des "s"
                text = pluralStringResource(
                    id = R.plurals.remaining_entries,
                    count = remainingEntries,
                    remainingEntries
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(id = R.string.premium_unlimited),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onUpgrade,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.horizontalGradient(listOf(PinkStart, PinkEnd)),
                        shape = RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_crown),
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.unlock_premium_button),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun JournalEntryCard(
    entry: JournalEntry,
    isUserEntry: Boolean,
    isSubscribed: Boolean,
    onClick: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val zdt = remember(entry.eventEpochMillis) {
        Instant.ofEpochMilli(entry.eventEpochMillis).atZone(zone)
    }
    val dayOfMonth = remember(zdt) { zdt.dayOfMonth.toString() }
    val monthAbbrev = remember(zdt) {
        zdt.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).uppercase()
    }
    val timeText = remember(zdt) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(zdt)
    }

    // Bordure rose légère si entrée de l'utilisateur
    val borderColor = if (isUserEntry) PinkStart.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.1f)

    Button(
        onClick = {
            Log.d("JournalEntryCard", "Tap on card - '${entry.title}' (id=${entry.id}), isUserEntry=$isUserEntry")
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.8f)),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date (colonne gauche)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(50.dp)
            ) {
                Text(dayOfMonth, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(monthAbbrev, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Black.copy(alpha = 0.7f))
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Contenu
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (entry.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.description,
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.8f),
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = timeText,
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.6f)
                )
            }

            // Image à droite si présente
            if (entry.hasImage) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

// ------------------------------------------------------------
// Utils
// ------------------------------------------------------------

private fun isUserEntry(appState: AppState, entry: JournalEntry): Boolean {
    val currentUserId = appState.currentUser?.id ?: return false
    return entry.authorId == currentUserId
}

/**
 * Groupe les entrées par mois/année (ex: "septembre 2025"),
 * puis ordonne les groupes du plus récent au plus ancien.
 */
private fun groupEntriesByMonth(entries: List<JournalEntry>): List<Pair<String, List<JournalEntry>>> {
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    val sorted = entries.sortedByDescending { it.eventEpochMillis }
    val grouped: Map<String, List<JournalEntry>> = sorted.groupBy { entry ->
        Instant.ofEpochMilli(entry.eventEpochMillis).atZone(zone).format(fmt)
    }

    return grouped.entries
        .map { (key, value) -> key to value.sortedByDescending { it.eventEpochMillis } }
        .sortedByDescending { (_, value) -> value.firstOrNull()?.eventEpochMillis ?: 0L }
}

// ------------------------------------------------------------
// Couleurs (équivalents du SwiftUI Color(hex: "#FD267A") etc.)
// ------------------------------------------------------------

private val PinkStart = Color(0xFFFD267A)
private val PinkEnd = Color(0xFFFF6B9D)

/* ------------------------------------------------------------
   STRINGS.XML — Exemples de clés utilisées (ajoute-les à res/values/strings.xml)
   (Français illustratif ; adapte selon ton ton et ta localisation.)

<resources>
    <string name="loading">Chargement…</string>
    <string name="limit_reached">Tu as atteint la limite d’entrées gratuites.</string>
    <string name="empty_journal_message">Ton journal est vide. Commence par créer ta première entrée pour garder vos souvenirs vivants.</string>
    <string name="journal_description">Capture vos moments, petits et grands. Écris ce que tu ressens, ajoute une photo, et revis-les ensemble.</string>
    <string name="create">Créer</string>
    <string name="unlock_premium_button">Débloquer Premium</string>
    <string name="premium_unlimited">Premium : entrées illimitées et fonctionnalités avancées.</string>

    <plurals name="remaining_entries">
        <item quantity="one">Il te reste %d entrée gratuite aujourd’hui.</item>
        <item quantity="other">Il te reste %d entrées gratuites aujourd’hui.</item>
    </plurals>
</resources>

   Remplace en Kotlin/Compose tout appel du type
   "challengeKey.localized(tableName: …)" par
   stringResource(R.string.challengeKey) (dans un @Composable)
   ou context.getString(R.string.challengeKey) (hors @Composable).

   Dépendance image (Coil):
   implementation("io.coil-kt:coil-compose:2.6.0")

   Icônes (placeholder):
   - R.drawable.ic_plus
   - R.drawable.ic_crown
   - R.drawable.ic_star_filled
   - R.drawable.jou
   Fournis-les ou remplace par Icons.Default.* si tu utilises Compose Material Icons.
------------------------------------------------------------ */
