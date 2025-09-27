package com.love2loveapp.views.savedchallenges

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.SavedChallenge
import kotlinx.coroutines.launch

/**
 * 🔖 SavedChallengesScreen - Écran Défis Sauvegardés
 * Équivalent iOS SavedChallengesView
 * 
 * Affiche tous les défis sauvegardés par le couple avec navigation par swipe.
 * Permet de supprimer les défis avec confirmations.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SavedChallengesScreen(
    onBackPressed: () -> Unit
) {
    val savedChallengesRepository = AppDelegate.savedChallengesRepository
    val currentUser = AppDelegate.appState.currentUser.value
    
    // 🔧 CONFIGURATION CRITIQUE : Initialiser le repository comme iOS
    LaunchedEffect(Unit) {
        if (savedChallengesRepository != null && currentUser != null) {
            Log.d("SavedChallengesScreen", "🔧 Initialisation repository pour: ${currentUser.id}")
            savedChallengesRepository.initializeForUser(currentUser.id)
        } else {
            Log.e("SavedChallengesScreen", "❌ Repository ou utilisateur manquant")
            Log.e("SavedChallengesScreen", "  - Repository: ${savedChallengesRepository != null}")
            Log.e("SavedChallengesScreen", "  - User: ${currentUser != null}")
        }
    }
    
    // États observables
    val savedChallenges by savedChallengesRepository?.savedChallenges?.collectAsState(emptyList()) 
        ?: remember { mutableStateOf(emptyList<SavedChallenge>()) }
    val isLoading by savedChallengesRepository?.isLoading?.collectAsState(false) 
        ?: remember { mutableStateOf(false) }
    val errorMessage by savedChallengesRepository?.errorMessage?.collectAsState(null) 
        ?: remember { mutableStateOf(null as String?) }
    
    // États locaux
    var challengeToDelete by remember { mutableStateOf<SavedChallenge?>(null) }
    val scope = rememberCoroutineScope()
    
    // État du pager
    val pagerState = rememberPagerState(pageCount = { savedChallenges.size })
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8)) // Même fond que favoris
    ) {
        
        // 📱 TOP BAR AVEC TITRE
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.saved_challenges_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.Black
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackPressed
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.Black
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        
        // 📊 CONTENU PRINCIPAL
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            
            when {
                // ⏳ ÉTAT CHARGEMENT
                isLoading -> {
                    LoadingSavedChallengesView()
                }
                
                // ❌ ÉTAT ERREUR
                errorMessage != null -> {
                    ErrorSavedChallengesView(
                        errorMessage = errorMessage!!,
                        onRetry = {
                            // TODO: Retry logic
                        }
                    )
                }
                
                // 📭 AUCUN DÉFI SAUVEGARDÉ
                savedChallenges.isEmpty() -> {
                    EmptySavedChallengesView()
                }
                
                // 📋 LISTE DES DÉFIS SAUVEGARDÉS
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val challenge = savedChallenges[page]
                        
                        SavedChallengeCard(
                            challenge = challenge,
                            onDelete = {
                                challengeToDelete = challenge
                            }
                        )
                    }
                }
            }
        }
        
        // 🗑️ DIALOG CONFIRMATION SUPPRESSION
        challengeToDelete?.let { challenge ->
            AlertDialog(
                onDismissRequest = {
                    challengeToDelete = null
                },
                title = {
                    Text(
                        text = stringResource(R.string.delete_challenge_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Jour ${challenge.challengeDay}",
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = challenge.challengeText,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.delete_challenge_message),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val currentUser = AppDelegate.appState.currentUser.value
                                if (currentUser != null) {
                                    savedChallengesRepository?.removeSavedChallenge(
                                        challengeId = challenge.id,
                                        userId = currentUser.id
                                    )
                                }
                                challengeToDelete = null
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.Red
                        )
                    ) {
                        Text(stringResource(R.string.delete_action))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            challengeToDelete = null
                        }
                    ) {
                        Text(stringResource(R.string.cancel_action))
                    }
                }
            )
        }
    }
}

/**
 * 🔖 SavedChallengeCard - Carte d'un défi sauvegardé - DESIGN EXACT iOS
 * Structure: Header Rose + Corps Sombre selon RAPPORT_DESIGN_CARTES_DEFIS_SAUVEGARDES.md
 */
@Composable
private fun SavedChallengeCard(
    challenge: SavedChallenge,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 20.dp)
    ) {
        
        // 🎨 CARTE PRINCIPALE - DESIGN EXACT iOS avec Header + Corps
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp), // Même hauteur que carte vue principale défis
            shape = RoundedCornerShape(20.dp), // 20dp radius selon rapport iOS
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 📌 HEADER ROSE - Gradient horizontal selon rapport iOS
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
                        .padding(vertical = 20.dp), // 20dp vertical selon rapport
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Love2Love",
                        fontSize = 18.sp, // 18sp selon rapport iOS
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                
                // 🌙 CORPS SOMBRE - Gradient vertical 3 couleurs selon rapport iOS
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF33191A), // RGB(51, 26, 26) - Brun foncé exact rapport
                                    Color(0xFF66334C), // RGB(102, 51, 76) - Brun moyen exact rapport  
                                    Color(0xFF994D33)  // RGB(153, 77, 51) - Brun roux exact rapport
                                )
                            )
                        )
                        .padding(horizontal = 30.dp), // 30dp horizontal selon rapport
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 📝 TEXTE DU DÉFI - Spécifications exactes iOS
                        Text(
                            text = challenge.challengeText,
                            fontSize = 22.sp, // 22sp selon rapport iOS
                            fontWeight = FontWeight.Medium, // Medium weight selon rapport
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp, // Line spacing 6sp → lineHeight 28sp
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // 🗑️ BOUTON SUPPRESSION - Design exact iOS (pleine largeur en bas)
        Button(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp), // 56dp hauteur selon rapport iOS
            shape = RoundedCornerShape(28.dp), // 28dp radius selon rapport iOS
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF6699), // Rose Love2Love exact rapport
                contentColor = Color.White
            )
        ) {
            Text(
                text = stringResource(R.string.delete_challenge_button),
                fontSize = 18.sp, // 18sp selon rapport iOS
                fontWeight = FontWeight.SemiBold // SemiBold selon rapport
            )
        }
    }
}

/**
 * ⏳ Vue de chargement
 */
@Composable
private fun LoadingSavedChallengesView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFFFF6B9D),
                strokeWidth = 3.dp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.loading_saved_challenges),
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 📭 Vue défis vides - DESIGN SIMPLIFIÉ iOS (sans icône cahier)
 */
@Composable
private fun EmptySavedChallengesView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(40.dp)
        ) {
            Text(
                text = stringResource(R.string.no_saved_challenges),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Text(
                text = stringResource(R.string.save_challenges_description),
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

/**
 * ❌ Vue d'erreur
 */
@Composable
private fun ErrorSavedChallengesView(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(40.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 64.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            
            Text(
                text = "Erreur",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Text(
                text = errorMessage,
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            
            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF6B9D)
                )
            ) {
                Text("Réessayer")
            }
        }
    }
}
