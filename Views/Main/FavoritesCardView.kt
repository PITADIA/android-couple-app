package com.love2love.ui.favorites

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign.Companion.Center
import androidx.compose.ui.text.style.TextAlign.Companion.Start
import androidx.compose.ui.text.style.TextAlign.Companion.Justify
import androidx.compose.ui.text.style.TextAlign.Companion.End
import androidx.compose.ui.text.style.TextAlign.Companion.Left
import androidx.compose.ui.text.style.TextAlign.Companion.Right
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Kotlin/Compose port of the SwiftUI FavoritesCardView.
 *
 * ✅ Uses Android standard localization via strings.xml with stringResource(...) inside @Composable.
 * ✅ Horizontal swipable cards using foundation HorizontalPager (Compose 1.4+).
 * ✅ Remove-from-favorites button with confirmation AlertDialog.
 * ✅ Empty state with locale-based image selection.
 *
 * Notes:
 * - Replace R.string.* and R.drawable.* with your actual resources.
 * - HorizontalPager is part of androidx.compose.foundation:pager. Ensure you have a recent Compose BOM/foundation.
 */

// ------------------------------------------------------------
// Data model
// ------------------------------------------------------------

data class FavoriteQuestion(
    val questionId: String,
    val questionText: String,
    val categoryTitle: String
)

// ------------------------------------------------------------
// Simple in-memory service (replace with your Firestore-backed service)
// ------------------------------------------------------------

object FavoritesService {
    private val _favorites: SnapshotStateList<FavoriteQuestion> = mutableStateListOf()
    fun getAllFavorites(): List<FavoriteQuestion> = _favorites
    fun setAllFavorites(items: List<FavoriteQuestion>) {
        _favorites.clear(); _favorites.addAll(items)
    }
    fun removeFavorite(questionId: String) {
        _favorites.removeAll { it.questionId == questionId }
    }
}

// ------------------------------------------------------------
// Screen
// ------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesCardScreen(
    modifier: Modifier = Modifier,
    // you can inject your real service via params or DI
    favoritesService: FavoritesService = FavoritesService
) {
    val context = LocalContext.current
    val favorites = favoritesService.getAllFavorites()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedIndexForDeletion by remember { mutableStateOf(0) }

    val pageCount = maxOf(favorites.size, 1)
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pageCount }
    )
    val scope = rememberCoroutineScope()

    // If the list shrinks (after deletion), keep pager in bounds
    LaunchedEffect(favorites.size) {
        val lastIndex = (favorites.size - 1).coerceAtLeast(0)
        if (pagerState.currentPage > lastIndex) {
            scope.launch { pagerState.scrollToPage(lastIndex) }
        }
    }

    // Background color identical to iOS page
    Box(
        modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.favorites_title), // "Favoris"
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // Content
            if (favorites.isEmpty()) {
                EmptyFavoritesState()
            } else {
                // Cards pager
                HorizontalPager(
                    state = pagerState,
                    beyondBoundsPageCount = 1,
                    pageSpacing = 30.dp,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val favorite = favorites.getOrNull(page)
                    FavoriteQuestionCard(
                        favorite = favorite,
                        isBackground = page != pagerState.currentPage
                    )
                }

                // Remove button
                Button(
                    onClick = {
                        selectedIndexForDeletion = pagerState.currentPage
                        showDeleteDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(red = 1.0f, green = 0.4f, blue = 0.6f),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(id = R.string.remove_from_favorites),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(30.dp))
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        val current = favorites.getOrNull(selectedIndexForDeletion)
                        if (current != null) {
                            favoritesService.removeFavorite(current.questionId)
                        }
                        showDeleteDialog = false
                    }) {
                        Text(text = stringResource(id = R.string.remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                },
                title = { Text(text = stringResource(id = R.string.remove_from_favorites)) },
                text = { Text(text = stringResource(id = R.string.remove_favorite_confirmation)) }
            )
        }
    }
}

// ------------------------------------------------------------
// Empty state
// ------------------------------------------------------------

@Composable
private fun EmptyFavoritesState() {
    val context = LocalContext.current
    val imageRes = when (Locale.getDefault().language) {
        "fr" -> R.drawable.mili
        "de" -> R.drawable.crypto
        else -> R.drawable.manon
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier
                .size(240.dp)
                .padding(bottom = 12.dp),
            contentScale = ContentScale.Fit
        )

        Text(
            text = stringResource(id = R.string.add_favorite_questions),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = stringResource(id = R.string.add_favorites_description),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 30.dp)
        )
    }
}

// ------------------------------------------------------------
// Card
// ------------------------------------------------------------

@Composable
private fun FavoriteQuestionCard(
    favorite: FavoriteQuestion?,
    isBackground: Boolean
) {
    // Header gradient colors
    val headerGradient = Brush.linearGradient(
        colors = listOf(
            Color(red = 1.0f, green = 0.4f, blue = 0.6f),
            Color(red = 1.0f, green = 0.6f, blue = 0.8f)
        )
    )
    // Body gradient colors
    val bodyGradient = Brush.linearGradient(
        colors = listOf(
            Color(red = 0.2f, green = 0.1f, blue = 0.15f),
            Color(red = 0.4f, green = 0.2f, blue = 0.3f),
            Color(red = 0.6f, green = 0.3f, blue = 0.2f)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .shadow(if (isBackground) 5.dp else 10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
    ) {
        // Header with category title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerGradient)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = favorite?.categoryTitle ?: "",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Body with question + branding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bodyGradient),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = favorite?.questionText ?: "",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                modifier = Modifier.padding(horizontal = 30.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.leetchi2),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Love2Love",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------
// Preview (uses fake data)
// ------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun FavoritesCardScreenPreview() {
    FavoritesService.setAllFavorites(
        listOf(
            FavoriteQuestion("1", "Quelle est la petite attention qui t’a le plus marqué(e) récemment ?", "Questions profondes"),
            FavoriteQuestion("2", "Quel souvenir commun te donne instantanément le sourire ?", "Souvenirs"),
            FavoriteQuestion("3", "Qu’aimerais-tu que l’on crée ensemble ce mois-ci ?", "Projets")
        )
    )
    FavoritesCardScreen()
}

/* ------------------------------------------------------------
   strings.xml — add these keys (FR shown as example)
------------------------------------------------------------

<resources>
    <string name="favorites_title">Favoris</string>
    <string name="add_favorite_questions">Ajoute tes questions préférées</string>
    <string name="add_favorites_description">Marque des questions en favori pour les retrouver facilement ici et revenir dessus quand vous voulez.</string>
    <string name="remove_from_favorites">Retirer des favoris</string>
    <string name="remove">Retirer</string>
    <string name="cancel">Annuler</string>
    <string name="remove_favorite_confirmation">Es-tu sûr de vouloir retirer cette question de tes favoris ?</string>
</resources>

Notes:
- Utilise des répertoires spécifiques à la locale pour les images si tu veux automatiser la sélection (ex: res/drawable-fr/mili.png, res/drawable-de/crypto.png, res/drawable/manon.png en défaut). Le when(Locale…) ci‑dessus est un fallback si tu préfères gérer par code.
- Remplace R.drawable.leetchi2 par ton icône.
- Si tu préfères Context.getString(...) hors d’un @Composable, utilise LocalContext.current.getString(R.string.key).
*/
