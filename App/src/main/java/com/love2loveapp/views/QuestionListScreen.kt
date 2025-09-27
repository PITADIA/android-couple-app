package com.love2loveapp.views

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.QuestionCategory
import com.love2loveapp.services.PackProgressService  
import com.love2loveapp.services.QuestionDataManager
import com.love2loveapp.views.cards.QuestionCard
import com.love2loveapp.views.cards.PackCompletionCard
import com.love2loveapp.views.cards.FreemiumPaywallCard
import com.love2loveapp.views.cards.NewPackRevealSheet
import kotlinx.coroutines.launch
import com.love2loveapp.services.favorites.FavoritesRepository
import com.love2loveapp.services.Question
import com.love2loveapp.R

/**
 * QuestionListScreen - Écran d'affichage des cartes avec swipe horizontal
 * 
 * Réplication complète du système iOS QuestionListView :
 * - Swipe horizontal optimisé (max 3 cartes visibles)
 * - 3 types de cartes : Questions, PackCompletion, FreemiumPaywall
 * - Système de déblocage par packs de 32
 * - Gestion freemium intelligente
 * 
 * Équivalent iOS : QuestionListView.swift
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun QuestionListScreen(
    category: QuestionCategory,
    onBackPressed: () -> Unit
) {
    val TAG = "QuestionListScreen"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    Log.d(TAG, "🎴 Ouverture écran cartes pour ${category.id}")
    
    // Services via AppDelegate singleton 
    val appState = AppDelegate.appState
    val freemiumManager = appState.freemiumManager
    val currentUser by appState.currentUser.collectAsState()
    val isSubscribed = currentUser?.isSubscribed ?: false
    val questionDataManager = com.love2loveapp.services.QuestionDataManager.getInstance(context)
    val packProgressService = com.love2loveapp.services.PackProgressService.getInstance(context)
    val favoritesRepository = AppDelegate.favoritesRepository
    
    // États des questions
    var allQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var accessibleQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // États pour les cartes spéciales
    var showNewPackReveal by remember { mutableStateOf(false) }
    var completedPackNumber by remember { mutableIntStateOf(1) }
    
    // La carte de déblocage est disponible dès qu'il y a plus de packs à débloquer
    val shouldShowPackCompletion = remember(accessibleQuestions.size, allQuestions.size) {
        if (accessibleQuestions.size < allQuestions.size) {
            // Il y a plus de questions → on peut débloquer un pack
            val hasMorePacks = packProgressService.hasMorePacksToUnlock(category.id, allQuestions.size)
            hasMorePacks
        } else {
            false
        }
    }
    
    // État freemium paywall - simplifié pour compilation
    val shouldShowFreemiumPaywall: Boolean = remember(accessibleQuestions.size, allQuestions.size, isSubscribed) {
        if (isSubscribed || category.isPremium) {
            false
        } else {
            // Pour catégorie gratuite (en-couple) : paywall après 64 questions (2 packs)
            val maxFreeQuestions = if (category.id == "en-couple") 64 else Int.MAX_VALUE
            allQuestions.size > maxFreeQuestions && accessibleQuestions.size >= maxFreeQuestions
        }
    }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Configuration du pager
    val totalPages = accessibleQuestions.size + 
                    (if (shouldShowPackCompletion) 1 else 0) + 
                    (if (shouldShowFreemiumPaywall) 1 else 0)
    
    val pagerState = rememberPagerState(pageCount = { totalPages })
    
    // Chargement initial des questions
    LaunchedEffect(category.id) {
        Log.d(TAG, "📦 Chargement questions pour ${category.id}")
        isLoading = true
        
        try {
            // Charger toutes les questions
            allQuestions = questionDataManager.loadQuestions(category.id)
            Log.d(TAG, "✅ ${allQuestions.size} questions chargées pour ${category.id}")
            
            // Filtrer selon packs débloqués
            accessibleQuestions = packProgressService.getAccessibleQuestions(allQuestions, category.id)
            Log.d(TAG, "🎯 ${accessibleQuestions.size} questions accessibles")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement questions", e)
        } finally {
            isLoading = false
        }
    }
    
    // Mise à jour du numéro de pack pour l'affichage
    LaunchedEffect(shouldShowPackCompletion, accessibleQuestions.size) {
        if (shouldShowPackCompletion) {
            // Le pack qu'on vient de terminer (celui avec la carte de déblocage)
            completedPackNumber = accessibleQuestions.size / 32
            Log.d(TAG, "🎴 PackCompletion disponible - pack $completedPackNumber terminé, déblocage du pack ${completedPackNumber + 1}")
        }
    }
    
    // Background rouge foncé selon le rapport UI_NAVIGATION_CARTES.md
    // Dans un ModalBottomSheet, on prend tout l'espace disponible
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF260814)) // RGB(38, 8, 20) - Rouge très foncé selon le rapport
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Navigation selon le rapport - Optimisé pour modal
            NavigationHeader(
                currentIndex = pagerState.currentPage,
                totalItems = accessibleQuestions.size + (if (shouldShowPackCompletion) 1 else 0),
                onBackClick = onBackPressed,
                onRefreshClick = {
                    // Reset logic selon le rapport
                    scope.launch {
                        Log.d(TAG, "🔄 RESET demandé - retour à la première carte")
                        pagerState.scrollToPage(0)
                    }
                }
            )
            
            // Contenu principal avec cartes - S'adapte à l'espace modal disponible
            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (isLoading) {
                // État de chargement
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Chargement des questions...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (accessibleQuestions.isEmpty()) {
                // Aucune question disponible
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "😔",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = "Aucune question disponible",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Vérifiez votre abonnement ou réessayez plus tard",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // Affichage des cartes avec HorizontalPager - Optimisé pour modal
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 16.dp,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp) // Padding optimisé
                ) { page ->
                    when {
                        // Cartes questions normales
                        page < accessibleQuestions.size -> {
                            val question = accessibleQuestions[page]
                            
                            // 💝 État favori pour cette question
                            val isFavorite by remember(question.id) {
                                if (favoritesRepository != null) {
                                    derivedStateOf { favoritesRepository.isFavorite(question.id) }
                                } else {
                                    mutableStateOf(false)
                                }
                            }
                            
                            QuestionCard(
                                question = question,
                                category = category,
                                questionNumber = page + 1,
                                totalQuestions = accessibleQuestions.size,
                                isFavorite = false, // Favoris gérés par le bouton en bas selon le rapport
                                onFavoriteClick = null // Désactivé car géré par le bouton en bas
                            )
                        }
                        
                        // Carte PackCompletion (après les questions accessibles)
                        page == accessibleQuestions.size && shouldShowPackCompletion -> {
                            PackCompletionCard(
                                category = category,
                                packNumber = completedPackNumber,
                                onTap = {
                                    Log.d(TAG, "🎉 Tap sur PackCompletion - déblocage pack $completedPackNumber")
                                    showNewPackReveal = true
                                }
                            )
                        }
                        
                        // Carte Freemium Paywall (limite gratuite atteinte)
                        shouldShowFreemiumPaywall -> {
                                FreemiumPaywallCard(
                                    category = category,
                                    onTap = {
                                        Log.d(TAG, "💰 Tap sur FreemiumPaywall - redirection vers paywall")
                                        // TODO: Afficher paywall d'abonnement
                                    }
                                )
                        }
                    }
                }
            }
        }
            
            // Bouton Favoris en bas selon le rapport
            if (accessibleQuestions.isNotEmpty() && pagerState.currentPage < accessibleQuestions.size) {
                val currentQuestion = accessibleQuestions[pagerState.currentPage]
                val isFavorite by remember(currentQuestion.id) {
                    if (favoritesRepository != null) {
                        derivedStateOf { favoritesRepository.isFavorite(currentQuestion.id) }
                    } else {
                        mutableStateOf(false)
                    }
                }
                
                FavoriteButton(
                    isFavorite = isFavorite,
                    onToggleFavorite = {
                        scope.launch {
                            if (favoritesRepository != null) {
                                if (isFavorite) {
                                    Log.d(TAG, "💔 Suppression favori: ${currentQuestion.id}")
                                    favoritesRepository.removeFavorite(currentQuestion.id)
                                } else {
                                    Log.d(TAG, "💝 Ajout favori: ${currentQuestion.id}")
                                    val partnerId = currentUser?.partnerId
                                    val questionText = currentQuestion.getText(context)
                                    favoritesRepository.addFavorite(
                                        question = currentQuestion,
                                        questionText = questionText,
                                        category = category,
                                        partnerId = partnerId
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
    
    // Sheet NewPackReveal (animation full-screen)
    if (showNewPackReveal) {
        NewPackRevealSheet(
            packNumber = completedPackNumber + 1,
            questionsCount = 32,
            onContinue = {
                coroutineScope.launch {
                    Log.d(TAG, "🚀 Déblocage pack ${completedPackNumber + 1}")
                    
                    // Débloquer le pack suivant
                    packProgressService.unlockNextPack(category.id)
                    
                    // Recharger les questions accessibles
                    accessibleQuestions = packProgressService.getAccessibleQuestions(allQuestions, category.id)
                    
                    // Masquer la révélation (showPackCompletion se mettra à jour automatiquement)
                    showNewPackReveal = false
                    
                    // Naviguer vers la première nouvelle question du pack débloqué
                    val firstNewQuestionIndex = (completedPackNumber + 1) * 32
                    if (firstNewQuestionIndex < accessibleQuestions.size) {
                        pagerState.animateScrollToPage(firstNewQuestionIndex)
                        Log.d(TAG, "✅ Navigation vers question ${firstNewQuestionIndex + 1}")
                    } else {
                        Log.d(TAG, "✅ Déblocage terminé, retour aux questions")
                    }
                }
            }
        )
    }
}

/**
 * 🔝 Header Navigation selon RAPPORT_UI_NAVIGATION_CARTES.md
 * 3 éléments : Retour (gauche) - Compteur (centre) - Refresh (droite)
 */
@Composable
private fun NavigationHeader(
    currentIndex: Int,
    totalItems: Int,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 20.dp), // Padding réduit pour modal - garde l'esprit du rapport
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton retour (gauche)
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Retour",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Compteur questions (centre) - Format "2 sur 24"
        Text(
            text = "${currentIndex + 1} ${stringResource(R.string.on_count)} $totalItems",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        // Bouton refresh (droite)
        IconButton(onClick = onRefreshClick) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Recommencer",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * ❤️ Bouton Favoris selon RAPPORT_UI_NAVIGATION_CARTES.md
 * Background rose, animation scale, texte + icône dynamiques
 * Optimisé pour modal avec hauteur fixe et texte adaptatif
 */
@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    // Animation scale légère selon le rapport (1.02x quand favori)
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.02f else 1.0f,
        animationSpec = tween(200), label = "favorite_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 30.dp) // Padding pour modal
    ) {
        Button(
            onClick = onToggleFavorite,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp) // Hauteur fixe selon le rapport
                .scale(scale),
            shape = RoundedCornerShape(28.dp), // Corner radius selon le rapport
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF6699) // RGB(255, 102, 153) selon le rapport
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp) // Padding interne optimisé
        ) {
            Row(
                horizontalArrangement = Arrangement.Center, // Centre le contenu dans le bouton
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Texte dynamique selon l'état favori - Centré
                Text(
                    text = if (isFavorite) {
                        stringResource(R.string.remove_from_favorites)
                    } else {
                        stringResource(R.string.add_to_favorites)
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1, // Une ligne pour éviter l'écrasement
                    overflow = TextOverflow.Ellipsis, // Points de suspension si trop long
                    textAlign = TextAlign.Center // Centre le texte
                )

                // Espacement entre texte et icône
                Spacer(modifier = Modifier.width(12.dp))

                // Icône cœur dynamique (vide/plein) - Centrée
                Icon(
                    imageVector = if (isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
