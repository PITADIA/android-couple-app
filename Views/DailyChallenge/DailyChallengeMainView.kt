package com.love2loveapp.core.ui.views.dailychallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Target
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.love2loveapp.R
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.ui.extensions.ChatText
import com.love2loveapp.core.viewmodels.AppState
import com.love2loveapp.core.viewmodels.DailyChallengeViewModel
import com.love2loveapp.model.AppConstants
import com.love2loveapp.model.DailyChallenge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Vue principale des défis du jour - Équivalent Kotlin Compose de DailyChallengeMainView.swift
 * 
 * Fonctionnalités :
 * - Affichage du défi du jour avec freemium
 * - Pull-to-refresh pour actualiser
 * - Navigation vers les défis sauvegardés
 * - Analytics et haptic feedback
 * - Gestion des états Loading/Success/Error
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeMainView(
    appState: AppState,
    onNavigateToSavedChallenges: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyChallengeViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // États observables
    val currentChallenge by viewModel.currentChallenge.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // État pour les sheets
    var showingSavedChallenges by remember { mutableStateOf(false) }
    
    // Configuration services au premier affichage
    LaunchedEffect(Unit) {
        println("🚀 === DEBUG DAILYCHALLENGE MAIN VIEW - ON APPEAR ===")
        println("🎯 DailyChallengeMainView: Composable affiché")
        
        // 📅 LOGS DATE/HEURE (équivalent Swift)
        val now = Date()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()
        
        println("🕐 DailyChallengeMainView: Date/Heure actuelle: ${formatter.format(now)}")
        println("🌍 DailyChallengeMainView: Timezone: ${TimeZone.getDefault().id}")
        
        val calendar = Calendar.getInstance()
        println("📅 DailyChallengeMainView: Jour de la semaine: ${calendar.get(Calendar.DAY_OF_WEEK)}")
        println("📊 DailyChallengeMainView: Jour du mois: ${calendar.get(Calendar.DAY_OF_MONTH)}")
        println("📈 DailyChallengeMainView: Mois: ${calendar.get(Calendar.MONTH) + 1}")
        println("📉 DailyChallengeMainView: Année: ${calendar.get(Calendar.YEAR)}")
        
        viewModel.configureServices(appState)
        println("✅ DailyChallengeMainView: configureServices() terminé")
        
        // 🎯 GÉNÉRATION DÉFI: Même logique que Swift
        when (val challenge = currentChallenge) {
            is Result.Success -> {
                if (challenge.data != null) {
                    println("✅ DailyChallengeMainView: currentChallenge déjà présent: ${challenge.data.challengeKey}")
                } else {
                    println("❌ DailyChallengeMainView: currentChallenge est nil - Lancement refreshChallenges()")
                    viewModel.refreshChallenges()
                }
            }
            is Result.Loading -> {
                println("⏳ DailyChallengeMainView: currentChallenge en cours de chargement")
            }
            is Result.Error -> {
                println("❌ DailyChallengeMainView: Erreur currentChallenge - Lancement refreshChallenges()")
                viewModel.refreshChallenges()
            }
        }
    }
    
    // Pull-to-refresh state
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8)) // Fond gris clair identique à Swift
    ) {
        // Dégradé de fond (équivalent LinearGradient Swift)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFD267A).copy(alpha = 0.3f),
                            Color(0xFFFD267A).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = {
                coroutineScope.launch {
                    viewModel.refreshChallenges()
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                item {
                    // Header avec titre et bouton défis sauvegardés
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Titre centré avec sous-titre freemium
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(2f)
                        ) {
                            Text(
                                text = stringResource(R.string.daily_challenges_title),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                            
                            // Sous-titre freemium
                            val subtitle = getDailyChallengeSubtitle(appState)
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        
                        // Bouton défis sauvegardés
                        IconButton(
                            onClick = { 
                                showingSavedChallenges = true
                                onNavigateToSavedChallenges()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Défis sauvegardés",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(20.dp)) }
                
                // Contenu principal
                item {
                    when (val challengeResult = currentChallenge) {
                        is Result.Success -> {
                            val challenge = challengeResult.data
                            if (challenge != null) {
                                DailyChallengeCardView(
                                    challenge = challenge,
                                    showDeleteButton = false,
                                    onCompleted = { handleChallengeCompleted(viewModel, challenge) },
                                    onSave = { handleChallengeSave(viewModel, challenge) },
                                    appState = appState
                                )
                            } else {
                                NoChallengeAvailableView()
                            }
                        }
                        is Result.Loading -> {
                            LoadingChallengeView()
                        }
                        is Result.Error -> {
                            NoChallengeAvailableView()
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}

/**
 * Composant pour l'état de chargement
 */
@Composable
private fun LoadingChallengeView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color(0xFFFD267A)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = stringResource(R.string.loading_challenge),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.6f)
        )
    }
}

/**
 * Composant pour l'état "aucun défi disponible"
 */
@Composable
private fun NoChallengeAvailableView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp, horizontal = 20.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Target,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(60.dp)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text(
            text = stringResource(R.string.no_challenge_available),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.come_back_tomorrow_challenge),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Helper pour obtenir le sous-titre freemium des défis du jour
 */
private fun getDailyChallengeSubtitle(appState: AppState): String? {
    // Seulement si l'utilisateur a un partenaire connecté
    val user = appState.currentUser.value ?: return null
    val partnerId = user.partnerId?.trim()
    if (partnerId.isNullOrEmpty()) return null
    
    // Calculer le jour actuel
    val currentDay = calculateCurrentChallengeDay(appState)
    
    // Retourner le sous-titre approprié via FreemiumManager
    return appState.freemiumManager?.getDailyChallengeSubtitle(currentDay)
}

/**
 * Helper pour calculer le jour actuel du défi
 */
private fun calculateCurrentChallengeDay(appState: AppState): Int {
    val user = appState.currentUser.value ?: return 1
    val relationshipStartDate = user.relationshipStartDate ?: return 1
    
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val startOfDay = calendar.apply {
        time = relationshipStartDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
    
    val startOfToday = calendar.apply {
        time = Date()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
    
    val daysSinceStart = ((startOfToday.time - startOfDay.time) / (1000 * 60 * 60 * 24)).toInt()
    return daysSinceStart + 1
}

/**
 * Gestion de la completion d'un défi
 */
private fun handleChallengeCompleted(viewModel: DailyChallengeViewModel, challenge: DailyChallenge) {
    viewModel.markChallengeAsCompleted(challenge)
    
    // TODO: Haptic feedback - Android equivalent
    // HapticFeedbackManager.performHapticFeedback(HapticFeedbackType.Medium)
    
    // Analytics
    viewModel.trackAnalyticsEvent(
        AppConstants.Analytics.DAILY_CHALLENGE_COMPLETED,
        mapOf(
            "challenge_key" to challenge.challengeKey,
            "challenge_day" to challenge.challengeDay.toString()
        )
    )
}

/**
 * Gestion de la sauvegarde d'un défi
 */
private fun handleChallengeSave(viewModel: DailyChallengeViewModel, challenge: DailyChallenge) {
    // Vérifier si déjà sauvegardé
    if (viewModel.isChallengeAlreadySaved(challenge)) {
        return
    }
    
    viewModel.saveChallenge(challenge)
    
    // TODO: Haptic feedback - Android equivalent
    // HapticFeedbackManager.performHapticFeedback(HapticFeedbackType.Light)
    
    // Analytics
    viewModel.trackAnalyticsEvent(
        "daily_challenge_saved",
        mapOf(
            "challenge_key" to challenge.challengeKey,
            "challenge_day" to challenge.challengeDay.toString()
        )
    )
}
