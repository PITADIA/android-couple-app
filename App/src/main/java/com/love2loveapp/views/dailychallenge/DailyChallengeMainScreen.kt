package com.love2loveapp.views.dailychallenge

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.love2loveapp.R
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.DailyChallenge
import com.love2loveapp.services.dailychallenge.DailyChallengeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🎯 DailyChallengeMainScreen - Page Principale avec Cartes d'Actions
 * Design exact selon RAPPORT_DEFIS_DU_JOUR.md
 * 
 * Background: RGB(247, 247, 247) - Gris clair uniforme (identique Questions)
 * Cartes: Dégradé rose avec système completion + sauvegarde
 * Actions: Marquer comme fait + Sauvegarder défi
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeMainScreen(
    dailyChallengeRepository: DailyChallengeRepository,
    onNavigateBack: () -> Unit,
    onNavigateToSavedChallenges: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 📊 États observés
    val currentChallenge by dailyChallengeRepository.currentChallenge.collectAsStateWithLifecycle()
    val isLoading by dailyChallengeRepository.isLoading.collectAsStateWithLifecycle()
    val errorMessage by dailyChallengeRepository.errorMessage.collectAsStateWithLifecycle()

    // 🎨 Background principal - RGB(247, 247, 247) selon rapport
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7)) // Gris clair uniforme selon rapport
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 📋 Header Principal avec icône Défis Sauvegardés
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(48.dp)) // Équilibrage
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.daily_challenges_title), // ✅ Clé de traduction
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    // TODO: Sous-titre freemium dynamique si nécessaire
                    // Text(subtitle, style = caption, color = secondary)
                }
                
                // 🔖 ICÔNE DÉFIS SAUVEGARDÉS selon rapport
                IconButton(onClick = onNavigateToSavedChallenges) {
                    Icon(
                        Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(R.string.saved_challenges_title), // ✅ Clé de traduction
                        tint = Color(0xFFFD267A), // Rose Love2Love selon rapport
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (currentChallenge != null) {
                // 🎯 Contenu Principal selon rapport iOS - ScrollView avec carte + boutons séparés
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(20.dp)) }
                    
                    // 🃏 CARTE DÉFI PRINCIPALE - Design exact iOS
                    item {
                        DailyChallengeCard(
                            challenge = currentChallenge!!,
                            context = context,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // 🔘 BOUTONS D'ACTION SÉPARÉS - Design exact iOS
                    item {
                        DailyChallengeActionButtons(
                            challenge = currentChallenge!!,
                            onToggleCompletion = {
                                scope.launch {
                                    if (currentChallenge!!.isCompleted) {
                                        dailyChallengeRepository.markChallengeAsNotCompleted(currentChallenge!!.id)
                                    } else {
                                        dailyChallengeRepository.markChallengeAsCompleted(currentChallenge!!.id)
                                    }
                                }
                            },
                            onSaveChallenge = {
                                scope.launch {
                                    try {
                                        val currentUser = AppDelegate.appState.currentUser.value
                                        val repository = AppDelegate.savedChallengesRepository
                                        
                                        if (currentUser != null && repository != null) {
                                            val result = repository.saveChallenge(
                                                challenge = currentChallenge!!,
                                                challengeText = currentChallenge!!.getLocalizedText(context),
                                                userId = currentUser.id,
                                                userName = currentUser.name
                                            )
                                            
                                            if (result.isFailure) {
                                                Log.e("DailyChallengeMainScreen", "❌ Sauvegarde échouée: ${result.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("DailyChallengeMainScreen", "💥 Exception sauvegarde: ${e.message}")
                                    }
                                }
                            }
                        )
                    }
                    
                    item { Spacer(modifier = Modifier.height(40.dp)) }
                }
            } else {
                // État de chargement
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFD267A), // Rose Love2Love
                            modifier = Modifier.size(48.dp)
                        )

                        Text(
                            text = "Génération du défi du jour...", // TODO: Clé de traduction
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 🎯 Carte Défi avec Système Completion selon rapport
 */
@Composable
private fun DailyChallengeCard(
    challenge: DailyChallenge,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    // 📍 CARTE PRINCIPALE - Design signature Love2Love selon rapport iOS
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 🔑 HEADER ROSE - Dégradé horizontal selon rapport iOS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFF6699), // RGB(255, 102, 153) - Exact rapport
                                Color(0xFFFF99CC)  // RGB(255, 153, 204) - Exact rapport
                            )
                        )
                    )
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Love2Love",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            
            // 🔑 CORPS SOMBRE - Dégradé vertical 3 couleurs sophistiqué selon rapport iOS
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF331A26), // RGB(51, 26, 38) - Exact rapport
                                Color(0xFF66334D), // RGB(102, 51, 77) - Exact rapport
                                Color(0xFF994D33)  // RGB(153, 77, 51) - Exact rapport
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // 🔑 TEXTE DÉFI LOCALISÉ - Style exact selon rapport iOS
                    Text(
                        text = challenge.getLocalizedText(context),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp // Équivalent lineSpacing 6 iOS
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 🔘 BOUTONS D'ACTION SÉPARÉS - Design exact selon rapport iOS
 * 2 boutons sous la carte : Compléter + Sauvegarder
 */
@Composable
private fun DailyChallengeActionButtons(
    challenge: DailyChallenge,
    onToggleCompletion: () -> Unit,
    onSaveChallenge: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 📍 BOUTON COMPLÉTER DÉFI - Design exact rapport iOS
        Button(
            onClick = onToggleCompletion,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF6699), // RGB(255, 102, 153) - Rose Love2Love exact
                contentColor = Color.White
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = "Compléter le défi", // TODO: stringResource(R.string.challenge_completed_button)
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                
                Icon(
                    imageVector = if (challenge.isCompleted) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Outlined.Circle
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 8.dp)
                )
                
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        // 📍 BOUTON SAUVEGARDER DÉFI - Design exact rapport iOS
        Button(
            onClick = {
                onSaveChallenge()
                showSaveConfirmation = true
                scope.launch {
                    delay(3000)
                    showSaveConfirmation = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF6699), // RGB(255, 102, 153) - Rose Love2Love exact
                contentColor = Color.White
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = "Sauvegarder le défi", // TODO: stringResource(R.string.save_challenge_button)
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                
                // 🔑 ICÔNE AVEC ANIMATION - Selon rapport iOS
                val animatedScale by animateFloatAsState(
                    targetValue = if (showSaveConfirmation) 1.2f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                
                Icon(
                    imageVector = if (showSaveConfirmation) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.BookmarkBorder
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 8.dp)
                        .scale(animatedScale)
                )
                
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}