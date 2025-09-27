package com.love2loveapp.core.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.services.content.DailyQuestionService
import com.love2loveapp.domain.repository.UserRepository
import com.love2loveapp.model.DailyQuestion
import com.love2loveapp.model.DailyChallenge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DailyContentViewModel - États métier pour le contenu quotidien
 * 
 * Responsabilités:
 * - États locaux pour questions et défis quotidiens
 * - Événements one-shot (toasts, navigation)
 * - Collecte lifecycle-aware avec stateIn
 */
class DailyContentViewModel @Inject constructor(
    private val dailyQuestionService: DailyQuestionService,
    private val userRepository: UserRepository
) : ViewModel() {
    
    // === États Métier (StateFlow avec stateIn) ===
    
    /**
     * Question quotidienne actuelle
     */
    val currentQuestion: StateFlow<Result<DailyQuestion?>> = dailyQuestionService.currentQuestion
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Result.Loading()
        )
    
    /**
     * Défi quotidien actuel
     */
    val currentChallenge: StateFlow<Result<DailyChallenge?>> = dailyQuestionService.currentChallenge
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Result.Loading()
        )
    
    /**
     * État de chargement
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
    
    // === Événements One-Shot (SharedFlow replay=0) ===
    
    private val _uiEvents = MutableSharedFlow<UiEvent>(replay = 0)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()
    
    /**
     * Événements UI one-shot
     */
    sealed interface UiEvent {
        data class ShowToast(val message: String) : UiEvent
        data class ShowError(val error: String) : UiEvent
        data class NavigateToDetail(val questionId: String) : UiEvent
        object QuestionAnswered : UiEvent
    }
    
    // === Actions Publiques ===
    
    /**
     * Charge la question quotidienne
     */
    fun loadTodaysQuestion() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                dailyQuestionService.loadTodaysQuestion()
                _uiEvents.emit(UiEvent.ShowToast("Question chargée"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Erreur de chargement: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Répond à une question
     */
    fun answerQuestion(questionId: String, answer: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = dailyQuestionService.answerQuestion(questionId, answer)
                if (result is Result.Success) {
                    _uiEvents.emit(UiEvent.QuestionAnswered)
                    _uiEvents.emit(UiEvent.ShowToast("Réponse enregistrée"))
                } else {
                    _uiEvents.emit(UiEvent.ShowError("Erreur lors de l'enregistrement"))
                }
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Erreur: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Marque un défi comme terminé
     */
    fun completeChallenge(challengeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = dailyQuestionService.completeChallenge(challengeId)
                if (result is Result.Success) {
                    _uiEvents.emit(UiEvent.ShowToast("Défi terminé !"))
                } else {
                    _uiEvents.emit(UiEvent.ShowError("Erreur lors de la validation"))
                }
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowError("Erreur: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Navigue vers le détail d'une question
     */
    fun navigateToQuestionDetail(questionId: String) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.NavigateToDetail(questionId))
        }
    }
}
