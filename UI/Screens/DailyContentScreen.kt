package com.love2loveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.viewmodels.DailyContentViewModel
import com.love2loveapp.core.viewmodels.MinimalIntegratedAppState
import com.love2loveapp.ui.extensions.collectAsStateWithLifecycle
import com.love2loveapp.ui.extensions.CollectAsEvents

/**
 * DailyContentScreen - Exemple d'écran utilisant les bonnes pratiques
 * 
 * Démontre:
 * - collectAsStateWithLifecycle() au lieu de collectAsState()
 * - Événements one-shot avec CollectAsEvents
 * - États métier dans ViewModel dédié
 * - SavedStateHandle pour paramètres
 */
@Composable
fun DailyContentScreen(
    appState: MinimalIntegratedAppState,
    day: Int,
    viewModel: DailyContentViewModel = hiltViewModel()
) {
    // === Collecte lifecycle-aware des états ===
    
    val currentQuestion by viewModel.currentQuestion.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    // === Gestion des événements one-shot ===
    
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    viewModel.uiEvents.CollectAsEvents { event ->
        when (event) {
            is DailyContentViewModel.UiEvent.ShowToast -> {
                LaunchedEffect(event.message) {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
            is DailyContentViewModel.UiEvent.ShowError -> {
                LaunchedEffect(event.error) {
                    snackbarHostState.showSnackbar("Erreur: ${event.error}")
                }
            }
            is DailyContentViewModel.UiEvent.NavigateToDetail -> {
                // Navigation via NavigationManager (pas directement ici)
                // appNavigationManager.navigateTo("question_detail/${event.questionId}")
            }
            DailyContentViewModel.UiEvent.QuestionAnswered -> {
                LaunchedEffect(Unit) {
                    snackbarHostState.showSnackbar("Réponse enregistrée !")
                }
            }
        }
    }
    
    // === Sauvegarde du jour actuel dans SavedStateHandle ===
    
    LaunchedEffect(day) {
        appState.currentDay = day
    }
    
    // === UI ===
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Contenu Quotidien - Jour $day",
                style = MaterialTheme.typography.headlineMedium
            )
            
            if (isLoading) {
                CircularProgressIndicator()
            }
            
            when (currentQuestion) {
                is Result.Loading -> {
                    Text("Chargement de la question...")
                }
                is Result.Success -> {
                    currentQuestion.data?.let { question ->
                        Text(
                            text = question.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        Button(
                            onClick = {
                                viewModel.answerQuestion(question.id, "Ma réponse")
                            }
                        ) {
                            Text("Répondre")
                        }
                    }
                }
                is Result.Error -> {
                    Text(
                        text = "Erreur: ${currentQuestion.exception.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    Button(
                        onClick = { viewModel.loadTodaysQuestion() }
                    ) {
                        Text("Réessayer")
                    }
                }
            }
        }
    }
}
