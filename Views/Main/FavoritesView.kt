@file:Suppress("unused")
package com.example.love2love.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Kotlin/Compose port of the SwiftUI FavoritesView + FavoriteQuestionCard.
 *
 * ✅ Uses Android standard localization (strings.xml):
 *    - Inside composables, prefer stringResource(R.string.key)
 *    - For dynamic keys coming from backend (e.g. "dc_001"), see resolveString() helper below.
 *
 * UI notes:
 *  - Light gray background (like the iOS app) + very soft pink gradient overlay
 *  - Bottom padding of 100.dp to leave room for a bottom navigation bar
 */

// -----------------------------
// Data & Repository
// -----------------------------

data class FavoriteQuestion(
    val id: String,
    val emoji: String,
    /**
     * Can be a plain localized title or a key present in strings.xml
     * (e.g. "category_romance"). We'll attempt to resolve it via resolveString().
     */
    val categoryTitleOrKey: String,
    val dateAdded: Instant,
    /**
     * Can be the final localized text or a key present in strings.xml
     * (e.g. "dc_001"). We'll attempt to resolve it via resolveString().
     */
    val questionTextOrKey: String
)

interface FavoritesRepository {
    fun getAllFavorites(): List<FavoriteQuestion>
    fun deleteFavorite(id: String)
}

class InMemoryFavoritesRepository : FavoritesRepository {
    private val items = mutableStateListOf(
        FavoriteQuestion(
            id = "1",
            emoji = "💞",
            categoryTitleOrKey = "category_romance",
            dateAdded = Instant.now(),
            questionTextOrKey = "dc_001"
        ),
        FavoriteQuestion(
            id = "2",
            emoji = "🎯",
            categoryTitleOrKey = "Focus",
            dateAdded = Instant.now().minusSeconds(86_400),
            questionTextOrKey = "What goal do you want us to reach together this month?"
        )
    )

    override fun getAllFavorites(): List<FavoriteQuestion> = items

    override fun deleteFavorite(id: String) {
        items.removeAll { it.id == id }
    }
}

// -----------------------------
// ViewModel
// -----------------------------

class FavoritesViewModel(
    private val repository: FavoritesRepository = InMemoryFavoritesRepository()
) {
    private val _favorites = MutableStateFlow(repository.getAllFavorites())
    val favorites: StateFlow<List<FavoriteQuestion>> = _favorites.asStateFlow()

    fun refresh() {
        _favorites.value = repository.getAllFavorites()
    }

    fun deleteFavorite(id: String) {
        repository.deleteFavorite(id)
        refresh()
    }
}

// -----------------------------
// Composables
// -----------------------------

@Composable
fun FavoritesView(
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = remember { FavoritesViewModel() }
) {
    val favorites by viewModel.favorites.collectAsState()

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA)) // light gray background like iOS
    ) {
        // very soft pink gradient overlay
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFD267A).copy(alpha = 0.03f),
                            Color(0xFFFF655B).copy(alpha = 0.02f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )

        FavoritesCardView(
            favorites = favorites,
            onDelete = { viewModel.deleteFavorite(it.id) },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp) // room for bottom bar
        )
    }
}

@Composable
fun FavoritesCardView(
    favorites: List<FavoriteQuestion>,
    onDelete: (FavoriteQuestion) -> Unit,
    modifier: Modifier = Modifier
) {
    if (favorites.isEmpty()) {
        EmptyFavoritesState(modifier.fillMaxSize())
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = favorites,
                key = { it.id }
            ) { favorite ->
                FavoriteQuestionCard(
                    favorite = favorite,
                    onDelete = { onDelete(favorite) }
                )
            }
        }
    }
}

@Composable
fun FavoriteQuestionCard(
    favorite: FavoriteQuestion,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = Color.Black.copy(alpha = 0.1f)

    val categoryTitle = resolveString(favorite.categoryTitleOrKey)
    val questionText = resolveString(favorite.questionTextOrKey)
    val dateText = rememberLocalizedDate(favorite.dateAdded)

    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.8f))
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(16.dp)
    ) {
        // Header row: category & date + delete button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = favorite.emoji, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = categoryTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateText,
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        tint = Color(0xFFFF3B30) // iOS-like red
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Question text
        Text(
            text = questionText,
            fontSize = 16.sp,
            color = Color.Black,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun EmptyFavoritesState(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.empty_favorites_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.empty_favorites_message),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black.copy(alpha = 0.7f)
        )
    }
}

// -----------------------------
// Helpers
// -----------------------------

/**
 * If [keyOrText] matches a string resource name, returns the localized value.
 * Otherwise, returns [keyOrText] as-is.
 */
@Composable
fun resolveString(keyOrText: String): String {
    val context = LocalContext.current
    val resId = remember(keyOrText) {
        // Look up a string resource by name (e.g. "dc_001")
        context.resources.getIdentifier(keyOrText, "string", context.packageName)
    }
    return if (resId != 0) stringResource(id = resId) else keyOrText
}

@Composable
fun rememberLocalizedDate(instant: Instant): String {
    val context = LocalContext.current
    val locale = remember { context.resources.configuration.locales[0] ?: Locale.getDefault() }
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    return remember(instant, locale) {
        formatter.format(instant.atZone(ZoneId.systemDefault()))
    }
}

// -----------------------------
// Preview
// -----------------------------

@Preview(showBackground = true)
@Composable
private fun FavoriteCardPreview() {
    val sample = FavoriteQuestion(
        id = "preview",
        emoji = "💫",
        categoryTitleOrKey = "category_romance",
        dateAdded = Instant.now(),
        questionTextOrKey = "dc_001"
    )
    FavoriteQuestionCard(favorite = sample, onDelete = {})
}

@Preview(showBackground = true)
@Composable
private fun FavoritesViewPreview() {
    FavoritesView()
}

/*
---------------------------------------
strings.xml (example)
---------------------------------------

<resources>
    <!-- Generic / actions -->
    <string name="delete">Supprimer</string>

    <!-- Empty state -->
    <string name="empty_favorites_title">Aucun favori</string>
    <string name="empty_favorites_message">Enregistre tes questions préférées pour les retrouver ici.</string>

    <!-- Dynamic category and question keys (examples) -->
    <string name="category_romance">Romance</string>
    <string name="dc_001">Quelle petite attention de ma part t&#39;a le plus touché(e) récemment ?</string>
</resources>

Notes:
- Dans les composables, on privilégie stringResource(R.string.xxx). C’est l’équivalent de context.getString(R.string.xxx).
- Si tes favoris stockent des *clés* (ex: "dc_001"), resolveString() tentera de les résoudre via resources.getIdentifier().
  Sinon, le texte brut sera affiché tel quel.
*/
