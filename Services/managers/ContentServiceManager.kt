package com.love2loveapp.core.services.managers

import android.content.Context
import android.util.Log
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.services.content.*
import com.love2loveapp.core.services.firebase.FirebaseUserService
import com.love2loveapp.core.services.firebase.FirebaseFunctionsService
import com.love2loveapp.model.DailyQuestion
import com.love2loveapp.model.DailyChallenge
import com.love2loveapp.model.FavoriteQuestion
import com.love2loveapp.model.JournalEntry
import com.love2loveapp.model.QuestionCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 📚 ContentServiceManager - Gestionnaire Centralisé du Contenu
 * 
 * Responsabilités :
 * - Coordination de tous les services de contenu (Questions, Défis, Journal, Favoris)
 * - États réactifs pour contenu quotidien et sauvegardé
 * - Synchronisation automatique entre services
 * - Cache intelligent et stratégies de chargement
 * 
 * Architecture : Service Manager + Content Strategy + Reactive Streams
 */
class ContentServiceManager(
    private val context: Context,
    private val firebaseUserService: FirebaseUserService,
    private val firebaseFunctionsService: FirebaseFunctionsService
) {
    
    companion object {
        private const val TAG = "ContentServiceManager"
    }
    
    // === Coroutine Scope ===
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // === Services Content ===
    private val dailyQuestionService = DailyQuestionService.getInstance(context).apply {
        configure(firebaseUserService, firebaseFunctionsService)
    }
    
    private val dailyChallengeService = DailyChallengeService.getInstance(context).apply {
        configure(firebaseUserService, firebaseFunctionsService)
    }
    
    private val journalService = JournalService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val favoritesService = FavoritesService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val savedChallengesService = SavedChallengesService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val categoryProgressService = CategoryProgressService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    private val packProgressService = PackProgressService.getInstance(context).apply {
        configure(firebaseUserService)
    }
    
    // === États Réactifs - Daily Content ===
    private val _currentDailyQuestion = MutableStateFlow<Result<DailyQuestion?>>(Result.Loading())
    val currentDailyQuestion: StateFlow<Result<DailyQuestion?>> = _currentDailyQuestion.asStateFlow()
    
    private val _currentDailyChallenge = MutableStateFlow<Result<DailyChallenge?>>(Result.Loading())
    val currentDailyChallenge: StateFlow<Result<DailyChallenge?>> = _currentDailyChallenge.asStateFlow()
    
    // === États Réactifs - Saved Content ===
    private val _favoriteQuestions = MutableStateFlow<Result<List<FavoriteQuestion>>>(Result.Loading())
    val favoriteQuestions: StateFlow<Result<List<FavoriteQuestion>>> = _favoriteQuestions.asStateFlow()
    
    private val _journalEntries = MutableStateFlow<Result<List<JournalEntry>>>(Result.Loading())
    val journalEntries: StateFlow<Result<List<JournalEntry>>> = _journalEntries.asStateFlow()
    
    private val _savedChallenges = MutableStateFlow<Result<List<DailyChallenge>>>(Result.Loading())
    val savedChallenges: StateFlow<Result<List<DailyChallenge>>> = _savedChallenges.asStateFlow()
    
    // === États Réactifs - Progress ===
    private val _categoryProgress = MutableStateFlow<Result<Map<QuestionCategory, Int>>>(Result.Loading())
    val categoryProgress: StateFlow<Result<Map<QuestionCategory, Int>>> = _categoryProgress.asStateFlow()
    
    private val _packProgress = MutableStateFlow<Result<Map<String, Int>>>(Result.Loading())
    val packProgress: StateFlow<Result<Map<String, Int>>> = _packProgress.asStateFlow()
    
    // === États Généraux ===
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _lastRefresh = MutableStateFlow<Long>(0L)
    val lastRefresh: StateFlow<Long> = _lastRefresh.asStateFlow()
    
    init {
        Log.d(TAG, "📚 Initialisation ContentServiceManager")
        initializeContentStreams()
    }
    
    // === Initialisation ===
    
    /**
     * Initialiser les flux réactifs des services contenu
     */
    private fun initializeContentStreams() {
        Log.d(TAG, "🌊 Configuration flux contenu réactifs")
        
        // Observer contenu quotidien
        observeDailyContent()
        
        // Observer contenu sauvegardé
        observeSavedContent()
        
        // Observer progrès
        observeProgress()
    }
    
    /**
     * Observer contenu quotidien
     */
    private fun observeDailyContent() {
        // Daily Question
        dailyQuestionService.currentQuestion
            .onEach { questionResult ->
                Log.d(TAG, "📅 Daily question update: ${questionResult.javaClass.simpleName}")
                _currentDailyQuestion.value = questionResult
            }
            .launchIn(scope)
        
        // Daily Challenge
        dailyChallengeService.currentChallenge
            .onEach { challengeResult ->
                Log.d(TAG, "🎯 Daily challenge update: ${challengeResult.javaClass.simpleName}")
                _currentDailyChallenge.value = challengeResult
            }
            .launchIn(scope)
    }
    
    /**
     * Observer contenu sauvegardé
     */
    private fun observeSavedContent() {
        // Favoris
        favoritesService.favoriteQuestions
            .onEach { favoritesResult ->
                Log.d(TAG, "⭐ Favorites update: ${favoritesResult.javaClass.simpleName}")
                _favoriteQuestions.value = favoritesResult
            }
            .launchIn(scope)
        
        // Journal
        journalService.journalEntries
            .onEach { journalResult ->
                Log.d(TAG, "📖 Journal update: ${journalResult.javaClass.simpleName}")
                _journalEntries.value = journalResult
            }
            .launchIn(scope)
        
        // Saved Challenges
        savedChallengesService.savedChallenges
            .onEach { savedResult ->
                Log.d(TAG, "💾 Saved challenges update: ${savedResult.javaClass.simpleName}")
                _savedChallenges.value = savedResult
            }
            .launchIn(scope)
    }
    
    /**
     * Observer progrès
     */
    private fun observeProgress() {
        // Category Progress
        categoryProgressService.categoryProgress
            .onEach { progressResult ->
                Log.d(TAG, "📊 Category progress update: ${progressResult.javaClass.simpleName}")
                _categoryProgress.value = progressResult
            }
            .launchIn(scope)
        
        // Pack Progress
        packProgressService.packProgress
            .onEach { packResult ->
                Log.d(TAG, "📦 Pack progress update: ${packResult.javaClass.simpleName}")
                _packProgress.value = packResult
            }
            .launchIn(scope)
    }
    
    // === Actions Daily Content ===
    
    /**
     * Charger question du jour
     */
    suspend fun loadTodaysQuestion(): Result<DailyQuestion?> {
        Log.d(TAG, "📅 Chargement question du jour")
        _isLoading.value = true
        
        return try {
            val result = dailyQuestionService.getTodaysQuestion()
            _currentDailyQuestion.value = result
            updateLastRefresh()
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement question du jour", e)
            val error = Result.Error<DailyQuestion?>(e)
            _currentDailyQuestion.value = error
            error
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Charger défi du jour
     */
    suspend fun loadTodaysChallenge(): Result<DailyChallenge?> {
        Log.d(TAG, "🎯 Chargement défi du jour")
        _isLoading.value = true
        
        return try {
            val result = dailyChallengeService.getTodaysChallenge()
            _currentDailyChallenge.value = result
            updateLastRefresh()
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement défi du jour", e)
            val error = Result.Error<DailyChallenge?>(e)
            _currentDailyChallenge.value = error
            error
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Marquer question comme répondue
     */
    suspend fun markQuestionAsAnswered(questionId: String, answer: String): Result<Unit> {
        Log.d(TAG, "✅ Marquer question répondue: $questionId")
        return dailyQuestionService.markAsAnswered(questionId, answer)
    }
    
    /**
     * Marquer défi comme terminé
     */
    suspend fun markChallengeAsCompleted(challengeId: String): Result<Unit> {
        Log.d(TAG, "✅ Marquer défi terminé: $challengeId")
        return dailyChallengeService.markAsCompleted(challengeId)
    }
    
    // === Actions Saved Content ===
    
    /**
     * Ajouter question aux favoris
     */
    suspend fun addToFavorites(question: DailyQuestion): Result<Unit> {
        Log.d(TAG, "⭐ Ajouter aux favoris: ${question.id}")
        return favoritesService.addToFavorites(question)
    }
    
    /**
     * Retirer des favoris
     */
    suspend fun removeFromFavorites(questionId: String): Result<Unit> {
        Log.d(TAG, "❌ Retirer des favoris: $questionId")
        return favoritesService.removeFromFavorites(questionId)
    }
    
    /**
     * Ajouter entrée journal
     */
    suspend fun addJournalEntry(entry: JournalEntry): Result<Unit> {
        Log.d(TAG, "📖 Ajouter entrée journal: ${entry.id}")
        return journalService.addEntry(entry)
    }
    
    /**
     * Sauvegarder défi
     */
    suspend fun saveChallenge(challenge: DailyChallenge): Result<Unit> {
        Log.d(TAG, "💾 Sauvegarder défi: ${challenge.id}")
        return savedChallengesService.saveChallenge(challenge)
    }
    
    // === Actions Progress ===
    
    /**
     * Mettre à jour progrès catégorie
     */
    suspend fun updateCategoryProgress(category: QuestionCategory, progress: Int): Result<Unit> {
        Log.d(TAG, "📊 Mise à jour progrès catégorie: ${category.name} -> $progress")
        return categoryProgressService.updateProgress(category, progress)
    }
    
    /**
     * Mettre à jour progrès pack
     */
    suspend fun updatePackProgress(packId: String, progress: Int): Result<Unit> {
        Log.d(TAG, "📦 Mise à jour progrès pack: $packId -> $progress")
        return packProgressService.updateProgress(packId, progress)
    }
    
    // === Actions Globales ===
    
    /**
     * Actualiser tout le contenu
     */
    suspend fun refreshAllContent(): Result<Unit> {
        Log.d(TAG, "🔄 Actualisation complète du contenu")
        _isLoading.value = true
        
        return try {
            // Charger contenu quotidien
            loadTodaysQuestion()
            loadTodaysChallenge()
            
            // Charger contenu sauvegardé
            favoritesService.loadFavorites()
            journalService.loadEntries()
            savedChallengesService.loadSavedChallenges()
            
            // Charger progrès
            categoryProgressService.loadProgress()
            packProgressService.loadProgress()
            
            updateLastRefresh()
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur actualisation contenu", e)
            Result.Error(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Vider le cache contenu
     */
    suspend fun clearCache(): Result<Unit> {
        Log.d(TAG, "🗑️ Vidage cache contenu")
        
        return try {
            dailyQuestionService.clearCache()
            dailyChallengeService.clearCache()
            journalService.clearCache()
            favoritesService.clearCache()
            savedChallengesService.clearCache()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vidage cache", e)
            Result.Error(e)
        }
    }
    
    // === Helpers ===
    
    private fun updateLastRefresh() {
        _lastRefresh.value = System.currentTimeMillis()
    }
    
    // === Getters Services ===
    
    /**
     * Accès aux services individuels si besoin
     */
    fun getDailyQuestionService() = dailyQuestionService
    fun getDailyChallengeService() = dailyChallengeService
    fun getJournalService() = journalService
    fun getFavoritesService() = favoritesService
    fun getSavedChallengesService() = savedChallengesService
    fun getCategoryProgressService() = categoryProgressService
    fun getPackProgressService() = packProgressService
    
    // === Debug ===
    
    /**
     * État de debug du ContentServiceManager
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "currentDailyQuestionLoaded" to (_currentDailyQuestion.value is Result.Success),
            "currentDailyChallengeLoaded" to (_currentDailyChallenge.value is Result.Success),
            "favoriteQuestionsCount" to ((_favoriteQuestions.value as? Result.Success)?.data?.size ?: 0),
            "journalEntriesCount" to ((_journalEntries.value as? Result.Success)?.data?.size ?: 0),
            "savedChallengesCount" to ((_savedChallenges.value as? Result.Success)?.data?.size ?: 0),
            "isLoading" to _isLoading.value,
            "lastRefresh" to _lastRefresh.value
        )
    }
}
