package com.love2loveapp.views.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.models.FavoriteQuestion
import com.love2loveapp.services.favorites.FavoritesRepository
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ❤️ FavoritesScreen selon RAPPORT_DESIGN_FAVORIS.md
 * Page principale des favoris avec design Love2Love complet
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    favoritesRepository: FavoritesRepository,
    onNavigateBack: () -> Unit = {},
    onShowListView: () -> Unit = {}
) {
    // Coroutine scope pour les opérations asynchrones
    val scope = rememberCoroutineScope()
    
    // États observables du repository
    val favoriteQuestions by favoritesRepository.favoriteQuestions.collectAsState()
    val isLoading by favoritesRepository.isLoading.collectAsState()

    // État local pour les alertes de suppression - SUPPRIMÉ pour suppression directe

    // Pager state pour les cartes swipeables
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { favoriteQuestions.size }
    )

    // ⭐ BACKGROUND PRINCIPAL selon le rapport - Gris très clair Love2Love
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f))  // RGB(247, 247, 250)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 🏷️ HEADER SIMPLE ET ÉLÉGANT selon le rapport
            FavoritesSimpleHeader()

            Spacer(modifier = Modifier.height(20.dp))

            // 📋 CONTENU PRINCIPAL
            if (favoriteQuestions.isEmpty()) {
                // 📋 ÉTAT VIDE AVEC IMAGES LOCALISÉES
                EmptyFavoritesContent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                // 🃏 SYSTÈME DE CARTES SWIPEABLES selon le rapport
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Cartes swipeables avec espacement
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),  // Hauteur fixe optimisée selon le rapport
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        pageSpacing = 30.dp  // Espace entre cartes selon le rapport
                    ) { page ->
                        val favorite = favoriteQuestions[page]
                        val isCurrentPage = pagerState.currentPage == page

                        FavoriteQuestionCard(
                            favorite = favorite,
                            isActive = isCurrentPage,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 🔘 BOUTON DE SUPPRESSION selon le rapport - SUPPRESSION DIRECTE
                    Button(
                        onClick = {
                            if (pagerState.currentPage < favoriteQuestions.size) {
                                val favoriteToDelete = favoriteQuestions[pagerState.currentPage]
                                scope.launch {
                                    favoritesRepository.removeFavorite(favoriteToDelete.questionId)
                                    // Ajustement automatique de l'index
                                    if (pagerState.currentPage >= favoriteQuestions.size - 1 && pagerState.currentPage > 0) {
                                        // Le pager se réajustera automatiquement
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6699)  // Même rose que header cartes
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(56.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.remove_from_favorites),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        // 📱 LOADING OVERLAY
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFD267A)
                )
            }
        }
    }

    // 🚨 ALERT DE CONFIRMATION - SUPPRIMÉ pour suppression directe
}

// 🏷️ HEADER SIMPLE ET ÉLÉGANT selon le rapport - Juste titre centré
@Composable
fun FavoritesSimpleHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        // Titre centré hardcodé selon le rapport (pas de localisation)
        Text(
            text = "Favoris",  // ⚠️ Hardcodé selon le rapport iOS
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

// 📋 ÉTAT VIDE SOPHISTIQUÉ avec images localisées selon le rapport
@Composable
fun EmptyFavoritesContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 🎯 LOGIQUE DE LOCALISATION DES IMAGES selon le rapport
    val imageName = when (Locale.getDefault().language) {
        "fr" -> "mili"      // 🇫🇷 Français
        "de" -> "crypto"    // 🇩🇪 Allemand
        else -> "manon"     // 🇬🇧 Anglais + autres
    }

    // Obtenir l'ID de ressource de l'image
    val imageResId = context.resources.getIdentifier(
        imageName,
        "drawable",
        context.packageName
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 🖼️ IMAGE LOCALISÉE selon le rapport (240x240dp)
        if (imageResId != 0) {
            Image(
                painter = painterResource(imageResId),
                contentDescription = null,
                modifier = Modifier.size(240.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Titre principal selon le rapport
            Text(
                text = stringResource(R.string.add_favorite_questions),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            // Description détaillée selon le rapport
            Text(
                text = stringResource(R.string.add_favorites_description),
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 30.dp)
            )
        }
    }
}


