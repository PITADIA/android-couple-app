package com.love2love.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Kotlin/Compose port of the SwiftUI JournalCalendarView.
 * - Uses strings.xml via stringResource / context.getString(...)
 * - Mirrors month header, weekday row, 7xN grid, and entries list for selected date
 * - Includes simple stubs for AppState/JournalService/JournalEntry card & detail
 */

// ---- THEME / COLORS ---------------------------------------------------------
private val PinkAccent = Color(0xFFFD267A)

// ---- MODELS & SERVICES (stubs, replace with your own) -----------------------

data class User(val id: String, val isSubscribed: Boolean)

data class JournalEntry(
    val id: String,
    val title: String,
    val date: LocalDate,
    val authorId: String,
    val description: String? = null
)

interface JournalService {
    fun hasEntriesForDate(date: LocalDate): Boolean
    fun getEntriesForDate(date: LocalDate): List<JournalEntry>
}

object NoopJournalService : JournalService {
    override fun hasEntriesForDate(date: LocalDate) = false
    override fun getEntriesForDate(date: LocalDate) = emptyList<JournalEntry>()
}

data class AppState(
    val currentUser: User? = null,
    val journalService: JournalService? = null
)

// ---- TOP-LEVEL SCREEN -------------------------------------------------------
@Composable
fun JournalCalendarScreen(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }

    val journalService = appState.journalService ?: NoopJournalService
    val isUserSubscribed = appState.currentUser?.isSubscribed ?: false
    fun isUserEntry(entry: JournalEntry): Boolean =
        appState.currentUser?.id?.let { it == entry.authorId } == true

    Column(modifier = modifier.fillMaxSize()) {
        // Calendar
        CalendarGrid(
            selectedDate = selectedDate,
            onSelectDate = { selectedDate = it },
            hasEntriesForDate = { date -> journalService.hasEntriesForDate(date) },
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        )

        // Entries for selected date
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val label = context.getString(R.string.events_of_date)
                Text(
                    text = "$label ${formatLocalDateFull(selectedDate)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            val entriesForDate by remember(selectedDate) {
                mutableStateOf(journalService.getEntriesForDate(selectedDate))
            }

            if (entriesForDate.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowRight, // simple placeholder icon
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.Black.copy(alpha = 0.3f)
                    )
                    Text(
                        text = stringResource(id = R.string.no_events_today),
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = 20.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(entriesForDate) { entry ->
                        JournalEntryCard(
                            entry = entry,
                            isUserEntry = isUserEntry(entry),
                            isSubscribed = isUserSubscribed,
                            onClick = { selectedEntry = entry }
                        )
                    }
                }
            }
        }
    }

    // Detail sheet (ModalBottomSheet from Material3)
    if (selectedEntry != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedEntry = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            JournalEntryDetailSheet(
                entry = selectedEntry!!,
                onClose = { selectedEntry = null }
            )
        }
    }
}

// ---- CALENDAR GRID ----------------------------------------------------------
@Composable
private fun CalendarGrid(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    hasEntriesForDate: (LocalDate) -> Boolean,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val weekFields = remember(locale) { WeekFields.of(locale) }
    var currentMonth by rememberSaveable(selectedDate, locale) {
        mutableStateOf(YearMonth.from(selectedDate))
    }

    val monthTitle = remember(currentMonth, locale) {
        val fmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        currentMonth.format(fmt).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    Column(modifier = modifier) {
        // Month header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = {
                currentMonth = currentMonth.minusMonths(1)
            }) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = Color.Black
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = monthTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                currentMonth = currentMonth.plusMonths(1)
            }) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Weekday labels
        Row(modifier = Modifier.fillMaxWidth()) {
            val days = listOf(
                stringResource(R.string.calendar_monday),
                stringResource(R.string.calendar_tuesday),
                stringResource(R.string.calendar_wednesday),
                stringResource(R.string.calendar_thursday),
                stringResource(R.string.calendar_friday),
                stringResource(R.string.calendar_saturday),
                stringResource(R.string.calendar_sunday)
            )
            days.forEach { day ->
                Text(
                    text = day,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar grid 7 columns
        val daysInGrid = remember(currentMonth, weekFields) { buildMonthGrid(currentMonth, weekFields) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(daysInGrid) { date ->
                val isSelected = date == selectedDate
                val isCurrentMonth = (date.month == currentMonth.month && date.year == currentMonth.year)
                val hasEntries = hasEntriesForDate(date)
                CalendarDay(
                    date = date,
                    isSelected = isSelected,
                    isCurrentMonth = isCurrentMonth,
                    hasEntries = hasEntries,
                    onClick = { onSelectDate(date) }
                )
            }
        }
    }
}

private fun buildMonthGrid(yearMonth: YearMonth, weekFields: WeekFields): List<LocalDate> {
    val firstOfMonth = yearMonth.atDay(1)
    val lastOfMonth = yearMonth.atEndOfMonth()

    val firstDayOfGrid = firstOfMonth.with(TemporalAdjusters.previousOrSame(weekFields.firstDayOfWeek))
    val lastDayOfWeek = weekFields.firstDayOfWeek.plus(6) // end of the 7-day week
    val lastDayOfGrid = lastOfMonth.with(TemporalAdjusters.nextOrSame(lastDayOfWeek))

    val days = mutableListOf<LocalDate>()
    var d = firstDayOfGrid
    while (!d.isAfter(lastDayOfGrid)) {
        days.add(d)
        d = d.plusDays(1)
    }
    return days
}

// ---- DAY CELL ---------------------------------------------------------------
@Composable
private fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    isCurrentMonth: Boolean,
    hasEntries: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected -> PinkAccent.copy(alpha = 0.2f)
        hasEntries -> Color.White.copy(alpha = 0.05f)
        else -> Color.Transparent
    }
    val textColor = when {
        !isCurrentMonth -> Color.Black.copy(alpha = 0.3f)
        isSelected -> PinkAccent
        else -> Color.Black
    }

    Column(
        modifier = Modifier
            .height(44.dp)
            .fillMaxWidth()
            .background(bg, shape = RoundedCornerShape(8.dp))
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = PinkAccent,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
        if (hasEntries) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(PinkAccent, shape = RoundedCornerShape(50))
            )
        }
    }
}

// ---- ENTRY CARD & DETAIL (stubs) -------------------------------------------
@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    isUserEntry: Boolean,
    isSubscribed: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Example badges (optional):
                if (!isUserEntry) {
                    AssistChip(onClick = {}, label = { Text(text = "Partner") })
                }
                if (!isSubscribed) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, label = { Text(text = "Pro") })
                }
            }
            entry.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, fontSize = 14.sp, color = Color.DarkGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun JournalEntryDetailSheet(entry: JournalEntry, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(text = entry.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text = formatLocalDateFull(entry.date), color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text(text = entry.description ?: "", fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
            Text(text = stringResource(id = android.R.string.ok))
        }
    }
}

// ---- HELPERS ----------------------------------------------------------------
@Composable
private fun formatLocalDateFull(date: LocalDate): String {
    val locale = Locale.getDefault()
    val fmt = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    return remember(date, locale) { date.format(fmt) }
}
