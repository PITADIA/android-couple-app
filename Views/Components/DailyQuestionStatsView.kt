package com.love2loveapp.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

// -----------------------------
// Model
// -----------------------------
data class DailyQuestion(
    val bothResponded: Boolean,
    val scheduledDate: LocalDate
)

// -----------------------------
// Service (singleton simple)
// Remplace DailyQuestionService.shared
// -----------------------------
object DailyQuestionService {
    private val _questionHistory = MutableStateFlow<List<DailyQuestion>>(emptyList())
    val questionHistory: StateFlow<List<DailyQuestion>> = _questionHistory.asStateFlow()

    // TODO: branche Firebase/Repository ici
    suspend fun loadQuestionHistory() {
        // Stub de démonstration
        delay(100)
        _questionHistory.value = listOf(
            DailyQuestion(bothResponded = true,  scheduledDate = LocalDate.now()),
            DailyQuestion(bothResponded = true,  scheduledDate = LocalDate.now().minusDays(1)),
            DailyQuestion(bothResponded = false, scheduledDate = LocalDate.now().minusDays(2)),
            DailyQuestion(bothResponded = true,  scheduledDate = LocalDate.now().minusDays(3)),
            DailyQuestion(bothResponded = true,  scheduledDate = LocalDate.now().minusDays(4))
        )
    }
}

// -----------------------------
// UI Screen
// -----------------------------
@Composable
fun DailyQuestionStatsScreen(
    modifier: Modifier = Modifier
) {
    val history by DailyQuestionService.questionHistory.collectAsState()

    LaunchedEffect(Unit) {
        DailyQuestionService.loadQuestionHistory()
    }

    DailyQuestionStatsContent(
        history = history,
        modifier = modifier
    )
}

@Composable
private fun DailyQuestionStatsContent(
    history: List<DailyQuestion>,
    modifier: Modifier = Modifier
) {
    val answeredCount = remember(history) { history.count { it.bothResponded } }

    val currentStreak = remember(history) {
        val sorted = history.sortedByDescending { it.scheduledDate }
        var streak = 0
        for (q in sorted) {
            if (q.bothResponded) streak++ else break
        }
        streak
    }

    val completionRatePct = remember(history, answeredCount) {
        if (history.isEmpty()) 0 else ((answeredCount.toDouble() / history.size.toDouble()) * 100.0).toInt()
    }

    val totalCount = history.size

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatisticCard(
                title = stringResource(R.string.daily_questions_answered),
                value = answeredCount.toString(),
                iconTint = Color(0xFF4CAF50),
                background = Color(0xFFE8F5E8),
                textColor = Color(0xFF2E7D32),
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.weight(1f)
            )

            StatisticCard(
                title = stringResource(R.string.daily_questions_streak),
                value = currentStreak.toString(),
                iconTint = Color(0xFFFD267A),
                background = Color(0xFFFFE4EE),
                textColor = Color(0xFFFD267A),
                icon = Icons.Filled.Whatshot,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatisticCard(
                title = stringResource(R.string.daily_questions_completion),
                value = "$completionRatePct%",
                iconTint = Color(0xFF2196F3),
                background = Color(0xFFE3F2FD),
                textColor = Color(0xFF1976D2),
                icon = Icons.Filled.ShowChart,
                modifier = Modifier.weight(1f)
            )

            StatisticCard(
                title = stringResource(R.string.daily_questions_total),
                value = totalCount.toString(),
                iconTint = Color(0xFFFF9800),
                background = Color(0xFFFFF3E0),
                textColor = Color(0xFFF57C00),
                icon = Icons.Filled.Help,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// -----------------------------
// Carte statistique (Compose)
// -----------------------------
@Composable
private fun StatisticCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(color = textColor)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// -----------------------------
// Preview (avec données fictives)
// -----------------------------
@Preview(showBackground = true)
@Composable
private fun PreviewDailyQuestionStats() {
    val sample = listOf(
        DailyQuestion(true,  LocalDate.now()),
        DailyQuestion(true,  LocalDate.now().minusDays(1)),
        DailyQuestion(false, LocalDate.now().minusDays(2)),
        DailyQuestion(true,  LocalDate.now().minusDays(3))
    )
    DailyQuestionStatsContent(history = sample, modifier = Modifier.padding(16.dp))
}
