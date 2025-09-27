package com.love2loveapp.views.dailychallenge.components

import android.util.Log
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.models.DailyChallenge
import com.love2loveapp.AppDelegate
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 🎯 DailyChallengeCard - Carte Défi Individuelle
 * Équivalent iOS DailyChallengeCardView.swift
 * 
 * Présente un défi avec :
 * - Texte localisé du défi
 * - Bouton completion avec animation
 * - Bouton sauvegarde avec feedback
 * - Design premium avec gradients
 */
@Composable
fun DailyChallengeCard(
    challenge: DailyChallenge,
    onToggleCompletion: (String) -> Unit,
    onSaveChallenge: (DailyChallenge) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedChallengesRepository = AppDelegate.savedChallengesRepository
    val currentUser = AppDelegate.appState.currentUser.collectAsState().value
    var showSaveConfirmation by remember { mutableStateOf(false) }
    
    // 🔖 Vérifier si le défi est déjà sauvegardé
    val isAlreadySaved = remember(challenge.challengeKey, currentUser?.id) {
        currentUser?.let { user ->
            savedChallengesRepository?.isChallengeAlreadySaved(challenge.challengeKey, user.id) ?: false
        } ?: false
    }

    // 🎨 Animation completion
    val completionScale by animateFloatAsState(
        targetValue = if (challenge.isCompleted) 1.1f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "completion_scale"
    )

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF6B9D),
                            Color(0xFFE63C6B)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 🔑 EN-TÊTE JOUR
                Text(
                    text = "Jour ${challenge.challengeDay}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )

                // 🔑 ICÔNE DÉFI (selon le type de défi)
                Text(
                    text = getChallengeEmoji(challenge.challengeDay),
                    fontSize = 48.sp
                )

                // 🔑 TEXTE DÉFI LOCALISÉ
                Text(
                    text = challenge.getLocalizedText(context),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )

                // 🔑 DATE
                Text(
                    text = challenge.formattedDate,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 🔑 ACTIONS COMPLETION ET SAUVEGARDE
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 🔑 BOUTON COMPLETION
                    Button(
                        onClick = { 
                            onToggleCompletion(challenge.id)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (challenge.isCompleted)
                                Color.White else Color.White.copy(alpha = 0.2f),
                            contentColor = if (challenge.isCompleted)
                                Color(0xFFFF6B9D) else Color.White
                        ),
                        shape = RoundedCornerShape(25.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .scale(completionScale)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (challenge.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )

                            Text(
                                text = if (challenge.isCompleted)
                                    stringResource(R.string.challenge_completed_button)
                                else
                                    stringResource(R.string.challenge_completed_button),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 🔑 BOUTON SAUVEGARDE
                    IconButton(
                        onClick = {
                            if (!isAlreadySaved && currentUser != null && savedChallengesRepository != null) {
                                 scope.launch {
                                     try {
                                         val challengeText = challenge.getLocalizedText(context)
                                         val result = savedChallengesRepository.saveChallenge(
                                             challenge = challenge,
                                             challengeText = challengeText,
                                             userId = currentUser.id,
                                             userName = currentUser.name
                                         )
                                         
                                        if (result.isSuccess) {
                                            showSaveConfirmation = true
                                            delay(3000)
                                            showSaveConfirmation = false
                                        }
                                        
                                    } catch (e: Exception) {
                                        Log.e("DailyChallengeCard", "Erreur sauvegarde: ${e.message}")
                                    }
                                }
                                
                                onSaveChallenge(challenge)
                            }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                if (isAlreadySaved) 
                                    Color.White.copy(alpha = 0.3f)
                                else 
                                    Color.White.copy(alpha = 0.2f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (isAlreadySaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.saved_challenges_title),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 🔑 CONFIRMATION SAUVEGARDE
                if (showSaveConfirmation) {
                    LaunchedEffect(showSaveConfirmation) {
                        delay(3000)
                        showSaveConfirmation = false
                    }

                    Text(
                        text = stringResource(R.string.challenge_saved_confirmation),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * 🎨 Emoji selon le jour du défi
 * Logique simplifiée pour avoir des icônes visuellement attractives
 */
private fun getChallengeEmoji(challengeDay: Int): String {
    return when (challengeDay % 10) {
        1 -> "💌" // Messages, communication
        2 -> "🍳" // Cuisine, activités domestiques
        3 -> "🎁" // Surprises, sorties
        4 -> "💕" // Amour, affection
        5 -> "🌟" // Expériences, découvertes
        6 -> "🎯" // Objectifs, défis
        7 -> "🏡" // Maison, cocooning
        8 -> "🌈" // Créativité, couleurs
        9 -> "⭐" // Excellence, réussite
        0 -> "🎪" // Fun, divertissement
        else -> "🎯" // Default
    }
}
