// DailyQuestionSingleStatsView.kt
@file:Suppress("unused")

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// --- Modèle minimal pour l'historique (aligne-toi sur ton vrai modèle)
data class DailyQuestionHistoryItem(
    val bothResponded: Boolean
)

// --- Service (placeholder) : remplace par ton impl réel (Firebase, repo, etc.)
object DailyQuestionService {
    private val _questionHistory =
        MutableStateFlow<List<DailyQuestionHistoryItem>>(emptyList())
    val questionHistory: StateFlow<List<DailyQuestionHistoryItem>> =
        _questionHistory.asStateFlow()

    suspend fun loadQuestionHistory() {
        // TODO: branche ici ton fetch réel
        // Exemple de données factices pour illustrer :
        _questionHistory.value = listOf(
            DailyQuestionHistoryItem(bothResponded = true),
            DailyQuestionHistoryItem(bothResponded = false),
            DailyQuestionHistoryItem(bothResponded = true)
        )
    }
}

// --- ViewModel
class DailyQuestionStatsViewModel(
    private val service: DailyQuestionService = DailyQuestionService
) : ViewModel() {

    val questionHistory: StateFlow<List<DailyQuestionHistoryItem>> =
        service.questionHistory

    fun loadQuestionHistory() {
        viewModelScope.launch {
            service.loadQuestionHistory()
        }
    }
}

// --- Composable principal (équivalent de DailyQuestionSingleStatsView Swift)
@Composable
fun DailyQuestionSingleStatsView(
    modifier: Modifier = Modifier,
    viewModel: DailyQuestionStatsViewModel = viewModel()
) {
    val history by viewModel.questionHistory.collectAsState()
    val answeredQuestionsCount by remember(history) {
        mutableStateOf(history.count { it.bothResponded })
    }

    LaunchedEffect(Unit) {
        viewModel.loadQuestionHistory()
    }

    StatisticCardView(
        title = stringResource(id = R.string.daily_questions_answered),
        value = answeredQuestionsCount.toString(),
        icon = Icons.Filled.CheckCircle,
        iconColor = Color(0xFF4CAF50),
        backgroundColor = Color(0xFFE8F5E8),
        textColor = Color(0xFF2E7D32),
        modifier = modifier
    )
}

// --- Composable réutilisable (équivalent de StatisticCardView Swift)
@Composable
fun StatisticCardView(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // décoratif
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    color = textColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
