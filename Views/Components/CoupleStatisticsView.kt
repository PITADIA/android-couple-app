// CoupleStatisticsScreen.kt
package com.love2loveapp.features.couple.stats

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * ⚙️ Dépendances attendues côté Android (placeholders/interfaces) :
 * - AppState.shared.currentUser?.relationshipStartDate: java.util.Date? ou LocalDate?
 * - JournalService.shared.entries: List<JournalEntry>
 * - JournalService.shared.updateEntry(entry)
 * - CategoryProgressService.shared.getCurrentIndex(categoryId: String): Int
 * - QuestionDataManager.shared.loadQuestions(categoryId: String): List<Question>
 * - QuestionCategory.categories: List<QuestionCategory> avec .id
 *
 * Adapte les imports/DI (Hilt, Koin, etc.) à ton projet.
 */

// --- Modèles minimalistes pour aider l’intégration (adapte/branche sur tes vrais types) ---
data class AppUser(val relationshipStartDate: java.util.Date?)
data class JournalLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val city: String?,
    val country: String?
)
data class JournalEntry(
    val id: String,
    var location: JournalLocation?,
    var updatedAt: java.util.Date?
)
data class Question(val id: String)
data class QuestionCategory(val id: String, val title: String)

// --- Singletons placeholders (remplace par tes services réels) ---
object AppState {
    var currentUser: AppUser? = null
    // Fournis un accès partagé si nécessaire
    val shared = this
}

object JournalService {
    // Dans ton app réelle, expose en Flow/LiveData. Ici on simplifie.
    var entries: List<JournalEntry> = emptyList()
    suspend fun updateEntry(updated: JournalEntry) { /* write to storage */ }
    val shared = this
}

object CategoryProgressService {
    fun getCurrentIndex(categoryId: String): Int = 0
    val shared = this
}

object QuestionDataManager {
    fun loadQuestions(categoryId: String): List<Question> = emptyList()
    val shared = this
}

object QuestionCategoryRepository {
    val categories: List<QuestionCategory> = emptyList()
}

// --- UI State ---
data class StatsUiState(
    val daysTogether: Int = 0,
    val questionsProgressPercent: Int = 0,
    val citiesVisited: Int = 0,
    val countriesVisited: Int = 0
)

// --- ViewModel ---
class CoupleStatisticsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appState = AppState.shared
    private val journalService = JournalService.shared
    private val categoryProgressService = CategoryProgressService.shared
    private val questionDataManager = QuestionDataManager.shared

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    // Libellés localisés nécessaires côté VM (équivalent à context.getString(...))
    private val unknownCity: String =
        application.getString(R.string.unknown_city)
    private val unknownLocation: String =
        application.getString(R.string.unknown_location)

    fun onAppear() {
        Log.d("📊 CoupleStatistics", "Vue apparue, calcul des statistiques")
        recomputeAll()
        // Réparation géocodage (max 3)
        viewModelScope.launch { repairJournalEntriesGeocoding() }
    }

    fun onCategoryProgressChanged() {
        Log.d("📊 CoupleStatistics", "Progression catégories mise à jour → recalcul")
        recomputeQuestionsProgressOnly()
    }

    private fun recomputeAll() {
        val days = computeDaysTogether()
        val qPercent = computeQuestionsProgressPercent()
        val (cities, countries) = computeVisitedPlacesCounts()
        _uiState.value = StatsUiState(
            daysTogether = days,
            questionsProgressPercent = qPercent,
            citiesVisited = cities,
            countriesVisited = countries
        )
    }

    private fun recomputeQuestionsProgressOnly() {
        val current = _uiState.value
        _uiState.value = current.copy(
            questionsProgressPercent = computeQuestionsProgressPercent()
        )
    }

    private fun computeDaysTogether(): Int {
        val start = appState.currentUser?.relationshipStartDate ?: return 0
        val startLocal = start.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        val days = ChronoUnit.DAYS.between(startLocal, today).toInt()
        return days.coerceAtLeast(0)
    }

    private fun computeQuestionsProgressPercent(): Int {
        val categories = if (QuestionCategoryRepository.categories.isNotEmpty())
            QuestionCategoryRepository.categories
        else
            emptyList()

        var totalQuestions = 0
        var totalProgress = 0

        for (category in categories) {
            val questions = questionDataManager.loadQuestions(category.id)
            val currentIndex = categoryProgressService.getCurrentIndex(category.id)
            totalQuestions += questions.size
            totalProgress += minOf(currentIndex + 1, questions.size) // index base 0 → +1
        }

        if (totalQuestions == 0) return 0
        return ((totalProgress.toDouble() / totalQuestions.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun computeVisitedPlacesCounts(): Pair<Int, Int> {
        val entries = journalService.entries

        val uniqueCities = entries.mapNotNull { it.location?.city?.trim() }
            .filter { it.isNotEmpty() && it != unknownCity }
            .toSet()

        val uniqueCountries = entries.mapNotNull { it.location?.country?.trim() }
            .filter { it.isNotEmpty() && it != unknownLocation }
            .toSet()

        return uniqueCities.size to uniqueCountries.size
    }

    private suspend fun repairJournalEntriesGeocoding() {
        val entriesToRepair = journalService.entries.filter { entry ->
            val loc = entry.location
            if (loc == null) return@filter false
            val cityMissing = loc.city.isNullOrBlank() || loc.city == unknownCity
            val countryMissing = loc.country.isNullOrBlank() || loc.country == unknownLocation
            (cityMissing || countryMissing)
        }.take(3)

        if (entriesToRepair.isEmpty()) return

        @Suppress("DEPRECATION")
        val geocoder = Geocoder(getApplication(), Locale.getDefault())

        viewModelScope.launch(Dispatchers.IO) {
            entriesToRepair.forEach { entry ->
                runCatching {
                    val loc = entry.location ?: return@runCatching
                    val results = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    if (!results.isNullOrEmpty()) {
                        val a = results[0]
                        val repaired = loc.copy(
                            address = loc.address ?: a.featureName,
                            city = a.locality ?: a.subAdminArea ?: loc.city ?: unknownCity,
                            country = a.countryName ?: loc.country ?: unknownLocation
                        )
                        val updated = entry.copy(
                            location = repaired,
                            updatedAt = java.util.Date()
                        )
                        journalService.updateEntry(updated)
                    }
                }.onFailure {
                    // Géocodage échoué → on continue silencieusement
                }
            }
        }
    }
}

// --- UI Composables ---
@Composable
fun CoupleStatisticsScreen(
    viewModel: CoupleStatisticsViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 🎯 i18n via strings.xml
    val title = stringResource(id = R.string.couple_statistics)
    val daysTitle = stringResource(id = R.string.days_together)
    val questionsAnsweredTitle = stringResource(id = R.string.questions_answered)
    val citiesVisitedTitle = stringResource(id = R.string.cities_visited)
    val countriesVisitedTitle = stringResource(id = R.string.countries_visited)

    // Déclencheurs "onAppear" & "onReceive"
    LaunchedEffect(Unit) {
        viewModel.onAppear()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        // Titre
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grille 2x2
        val cards = listOf(
            StatCardData(
                title = daysTitle,
                value = uiState.daysTogether.toString(),
                iconRes = R.drawable.jours,              // 🖼️ place ton asset dans res/drawable
                iconColor = colorFromHex("#feb5c8"),
                background = colorFromHex("#fedce3"),
                textColor = colorFromHex("#db3556")
            ),
            StatCardData(
                title = questionsAnsweredTitle,
                value = "${uiState.questionsProgressPercent}%",
                iconRes = R.drawable.qst,                // 🖼️
                iconColor = colorFromHex("#fed397"),
                background = colorFromHex("#fde9cf"),
                textColor = colorFromHex("#ffa229")
            ),
            StatCardData(
                title = citiesVisitedTitle,
                value = uiState.citiesVisited.toString(),
                iconRes = R.drawable.ville,              // 🖼️
                iconColor = colorFromHex("#b0d6fe"),
                background = colorFromHex("#dbecfd"),
                textColor = colorFromHex("#0a85ff")
            ),
            StatCardData(
                title = countriesVisitedTitle,
                value = uiState.countriesVisited.toString(),
                iconRes = R.drawable.pays,               // 🖼️
                iconColor = colorFromHex("#d1b3ff"),
                background = colorFromHex("#e8dcff"),
                textColor = colorFromHex("#7c3aed")
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(cards) { card ->
                StatisticCard(
                    title = card.title,
                    value = card.value,
                    iconRes = card.iconRes,
                    iconTint = card.iconColor,
                    background = card.background,
                    textColor = card.textColor
                )
            }
        }
    }
}

data class StatCardData(
    val title: String,
    val value: String,
    val iconRes: Int,
    val iconColor: Color,
    val background: Color,
    val textColor: Color
)

@Composable
fun StatisticCard(
    title: String,
    value: String,
    iconRes: Int,
    iconTint: Color,
    background: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .height(140.dp)
            .fillMaxWidth()
            .background(background, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Icône top-right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            // Si tu veux teinter un VectorDrawable, remplace Image par Icon et passe tint = iconTint
        }

        // Valeur + titre bottom-left
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.BottomStart)
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                color = textColor,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
        }
    }
}

// --- Utils ---
fun colorFromHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    val parsed = clean.toLong(radix = 16)
    return when (clean.length) {
        6 -> Color(0xFF000000 or parsed)
        8 -> Color(parsed)
        else -> Color.Unspecified
    }
}
