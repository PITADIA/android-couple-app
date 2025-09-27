package com.love2loveapp.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Services simplifiés pour Love2Love
 */

// === Question Cache Manager ===
class QuestionCacheManager private constructor() {
    companion object {
        @JvmStatic
        val shared = QuestionCacheManager()
    }
    
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()
    
    fun preloadAllCategories() {
        Log.d("QuestionCacheManager", "🔄 Préchargement de toutes les catégories")
        // Simulation du préchargement
        _isLoaded.value = true
    }
    
    fun optimizeMemoryUsage() {
        Log.d("QuestionCacheManager", "🧹 Optimisation mémoire")
    }
}

// === Performance Monitor ===
class PerformanceMonitor private constructor() {
    companion object {
        @JvmStatic
        val shared = PerformanceMonitor()
    }
    
    private var isMonitoring = false
    
    fun startMonitoring() {
        isMonitoring = true
        Log.d("PerformanceMonitor", "📊 Démarrage monitoring performance")
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        Log.d("PerformanceMonitor", "📊 Arrêt monitoring performance")
    }
}

// === Favorites Service ===
class FavoritesService {
    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()
    
    fun addFavorite(questionId: String) {
        val current = _favorites.value.toMutableList()
        if (!current.contains(questionId)) {
            current.add(questionId)
            _favorites.value = current
            Log.d("FavoritesService", "⭐ Question ajoutée aux favoris: $questionId")
        }
    }
    
    fun removeFavorite(questionId: String) {
        val current = _favorites.value.toMutableList()
        current.remove(questionId)
        _favorites.value = current
        Log.d("FavoritesService", "⭐ Question retirée des favoris: $questionId")
    }
}

// === Pack Progress Service Legacy (Compatibility) ===
class PackProgressServiceLegacy private constructor() {
    companion object {
        @JvmStatic
        val shared = PackProgressServiceLegacy()
    }
    
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()
    
    fun updateProgress(categoryId: String, progress: Float) {
        val current = _progress.value.toMutableMap()
        current[categoryId] = progress
        _progress.value = current
        Log.d("PackProgressServiceLegacy", "📈 Progression mise à jour pour $categoryId: ${(progress * 100).toInt()}%")
    }
}

// === Question Data Manager Legacy (Compatibility) ===
class QuestionDataManagerLegacy private constructor() {
    companion object {
        @JvmStatic
        val shared = QuestionDataManagerLegacy()
    }
    
    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded.asStateFlow()
    
    suspend fun loadInitialData() {
        Log.d("QuestionDataManagerLegacy", "📚 Chargement données initiales")
        // Simulation chargement
        kotlinx.coroutines.delay(1000)
        _isDataLoaded.value = true
        Log.d("QuestionDataManagerLegacy", "✅ Données initiales chargées")
    }
    
    fun preloadEssentialCategories() {
        Log.d("QuestionDataManagerLegacy", "⚡ Préchargement catégories essentielles")
    }
}

// === Location Service ===
interface LocationService {
    fun startLocationUpdatesIfAuthorized()
    fun stopLocationUpdates()
}

class LocationServiceImpl(private val context: Context) : LocationService {
    override fun startLocationUpdatesIfAuthorized() {
        Log.d("LocationService", "📍 Démarrage mises à jour localisation")
    }
    
    override fun stopLocationUpdates() {
        Log.d("LocationService", "📍 Arrêt mises à jour localisation")
    }
}

// === Simple Freemium Manager Implementation ===
class SimpleFreemiumManager : com.love2loveapp.models.FreemiumManager {
    companion object {
        private const val TAG = "FreemiumManager"
        private const val FREE_PACKS_LIMIT = 2     // 2 packs gratuits (64 questions)
        private const val QUESTIONS_PER_PACK = 32  // 32 questions par pack
        private const val FREE_DAILY_QUESTION_DAYS = 3
        private const val FREE_DAILY_CHALLENGE_DAYS = 3  
        private const val FREE_JOURNAL_ENTRIES_LIMIT = 5
    }
    
    private val _showingSubscription = MutableStateFlow(false)
    override val showingSubscriptionFlow = _showingSubscription.asStateFlow()
    
    private val _blockedCategoryAttempt = MutableStateFlow<com.love2loveapp.models.QuestionCategory?>(null)
    val blockedCategoryAttempt: StateFlow<com.love2loveapp.models.QuestionCategory?> = _blockedCategoryAttempt.asStateFlow()
    
    override val showingSubscription: Boolean
        get() = _showingSubscription.value
    
    /**
     * Gère le tap sur une catégorie (équivalent iOS handleCategoryTap)
     */
    override fun handleCategoryTap(
        category: com.love2loveapp.models.QuestionCategory, 
        onSuccess: () -> Unit
    ) {
        Log.d(TAG, "🎯 Tap sur catégorie: ${category.id}")
        
        // Récupérer l'état d'abonnement actuel depuis AppState
        val currentUser = com.love2loveapp.AppDelegate.appState.currentUser.value
        val isSubscribed = currentUser?.isSubscribed ?: false
        
        Log.d(TAG, "🔍 État abonnement: $isSubscribed (utilisateur: [USER_MASKED])")
        
        // 1. Utilisateur abonné → Accès illimité
        if (isSubscribed) {
            Log.d(TAG, "✅ Utilisateur abonné - accès accordé à ${category.id}")
            onSuccess()
            return
        }
        
        // 2. Catégorie Premium + Non abonné → Paywall
        if (category.isPremium) {
            Log.d(TAG, "🔒 Catégorie premium ${category.id} - affichage paywall")
            Log.d(TAG, "🔒 AVANT: _blockedCategoryAttempt = ${_blockedCategoryAttempt.value?.id}")
            Log.d(TAG, "🔒 AVANT: _showingSubscription = ${_showingSubscription.value}")
            
            _blockedCategoryAttempt.value = category
            _showingSubscription.value = true
            
            Log.d(TAG, "🔒 APRES: _blockedCategoryAttempt = ${_blockedCategoryAttempt.value?.id}")
            Log.d(TAG, "🔒 APRES: _showingSubscription = ${_showingSubscription.value}")
            Log.d(TAG, "✅ StateFlow mis à jour - paywall devrait s'afficher")
            
            // TODO: Analytics Firebase
            // Analytics.logEvent("paywall_affiche") { param("source", "freemium_limite") }
            
            return
        }
        
        // 3. Catégorie gratuite → Accès autorisé (limitation dans QuestionListView)
        Log.d(TAG, "🆓 Catégorie gratuite ${category.id} - accès accordé")
        onSuccess()
    }
    
    /**
     * Gère l'accès à une question spécifique (équivalent iOS handleQuestionAccess)
     */
    override fun handleQuestionAccess(
        questionIndex: Int, 
        category: com.love2loveapp.models.QuestionCategory, 
        onSuccess: () -> Unit
    ) {
        Log.d(TAG, "🔍 Accès question ${questionIndex + 1} de ${category.id}")
        
        // Récupérer l'état d'abonnement actuel depuis AppState
        val currentUser = com.love2loveapp.AppDelegate.appState.currentUser.value
        val isSubscribed = currentUser?.isSubscribed ?: false
        
        // Utilisateur abonné → Accès illimité
        if (isSubscribed) {
            Log.d(TAG, "✅ Utilisateur abonné - accès question accordé")
            onSuccess()
            return
        }
        
        // Catégorie premium → Paywall
        if (category.isPremium) {
            Log.d(TAG, "🔒 Question premium - affichage paywall")
            _blockedCategoryAttempt.value = category
            _showingSubscription.value = true
            return
        }
        
        // Catégorie gratuite - vérifier limite
        val maxFreeQuestions = getMaxFreeQuestions(category)
        if (questionIndex >= maxFreeQuestions) {
            Log.d(TAG, "🚫 Limite freemium atteinte (${questionIndex + 1} > $maxFreeQuestions)")
            _blockedCategoryAttempt.value = category
            _showingSubscription.value = true
            return
        }
        
        Log.d(TAG, "✅ Question autorisée (${questionIndex + 1}/$maxFreeQuestions)")
        onSuccess()
    }
    
    /**
     * Obtient le nombre maximum de questions gratuites pour une catégorie
     * Équivalent iOS getMaxFreeQuestions(for: category)
     */
    override fun getMaxFreeQuestions(category: com.love2loveapp.models.QuestionCategory): Int {
        return when {
            category.isPremium -> 0  // Aucune question gratuite pour premium
            category.id == "en-couple" -> FREE_PACKS_LIMIT * QUESTIONS_PER_PACK  // 64 questions
            else -> Int.MAX_VALUE  // Autres catégories gratuites (illimitées)
        }
    }
    
    override fun dismissSubscription() {
        Log.d(TAG, "🔒 DISMISSAL PAYWALL:")
        Log.d(TAG, "🔒 AVANT: _showingSubscription = ${_showingSubscription.value}")
        Log.d(TAG, "🔒 AVANT: _blockedCategoryAttempt = ${_blockedCategoryAttempt.value?.id}")
        
        _showingSubscription.value = false
        _blockedCategoryAttempt.value = null
        
        Log.d(TAG, "🔒 APRES: _showingSubscription = ${_showingSubscription.value}")
        Log.d(TAG, "🔒 APRES: _blockedCategoryAttempt = ${_blockedCategoryAttempt.value?.id}")
        Log.d(TAG, "💰 Écran abonnement fermé")
    }
    
    fun showSubscription() {
        _showingSubscription.value = true
        Log.d(TAG, "💰 Écran abonnement affiché")
    }
    
    /**
     * 📅 Vérification accès questions quotidiennes (équivalent iOS canAccessDailyQuestion)
     */
    fun canAccessDailyQuestion(questionDay: Int): Boolean {
        val currentUser = com.love2loveapp.AppDelegate.appState.currentUser.value
        val isSubscribed = currentUser?.isSubscribed ?: false
        
        if (isSubscribed) {
            return true
        }
        
        // Bloquer après le jour 3 pour les utilisateurs gratuits
        return questionDay <= 3 // FREE_DAILY_QUESTION_DAYS
    }
    
    /**
     * 🎯 Vérification accès défis quotidiens (équivalent iOS canAccessDailyChallenge)
     */
    fun canAccessDailyChallenge(challengeDay: Int): Boolean {
        val currentUser = com.love2loveapp.AppDelegate.appState.currentUser.value
        val isSubscribed = currentUser?.isSubscribed ?: false
        
        if (isSubscribed) {
            return true
        }
        
        // 🔑 LOGIQUE FREEMIUM DÉFIS : Bloquer après le jour 3
        return challengeDay <= 3 // FREE_DAILY_CHALLENGE_DAYS (identique aux questions)
    }
    
    /**
     * 📝 Vérification accès journal (équivalent iOS canAddJournalEntry)
     */
    fun canAddJournalEntry(currentEntriesCount: Int): Boolean {
        val currentUser = com.love2loveapp.AppDelegate.appState.currentUser.value
        val isSubscribed = currentUser?.isSubscribed ?: false
        
        if (isSubscribed) {
            return true
        }
        
        return currentEntriesCount < FREE_JOURNAL_ENTRIES_LIMIT
    }
    
    /**
     * 📖 Gère la création d'entrée journal avec logique freemium (équivalent iOS handleJournalEntryCreation)
     */
    fun handleJournalEntryCreation(currentEntriesCount: Int, onSuccess: () -> Unit) {
        val currentUser = com.love2loveapp.AppDelegate.appState.currentUser.value
        val isSubscribed = currentUser?.isSubscribed ?: false
        
        Log.d(TAG, "📖 Gestion création journal entry: entries=$currentEntriesCount, isSubscribed=$isSubscribed")
        
        if (isSubscribed) {
            Log.d(TAG, "📖 Utilisateur premium - Création journal autorisée")
            onSuccess()
            return
        }
        
        // 🔑 VÉRIFICATION FREEMIUM
        if (currentEntriesCount < FREE_JOURNAL_ENTRIES_LIMIT) {
            Log.d(TAG, "📖 Journal entry ${currentEntriesCount + 1}/$FREE_JOURNAL_ENTRIES_LIMIT - Accès gratuit")
            onSuccess()
        } else {
            Log.d(TAG, "📖 Journal entry ${currentEntriesCount + 1} > limite - Paywall")
            showJournalEntryPaywall()
        }
    }
    
    private fun showJournalEntryPaywall() {
        _showingSubscription.value = true
        Log.d(TAG, "📖 Affichage paywall pour entrée journal")
    }
    
    /**
     * 🔍 Vérification d'accès à une question spécifique (équivalent iOS canAccessQuestion)
     */
    fun canAccessQuestion(index: Int, category: com.love2loveapp.models.QuestionCategory): Boolean {
        val currentUser = com.love2loveapp.AppDelegate.appState.currentUser.value
        val isSubscribed = currentUser?.isSubscribed ?: false
        
        // Si l'utilisateur est abonné, accès illimité
        if (isSubscribed) {
            return true
        }
        
        // Si c'est une catégorie premium, aucun accès
        if (category.isPremium) {
            return false
        }
        
        // Pour la catégorie "En couple" gratuite, limiter à 2 packs (64 questions)
        if (category.id == "en-couple") {
            val maxFreeQuestions = FREE_PACKS_LIMIT * QUESTIONS_PER_PACK // 64
            return index < maxFreeQuestions
        }
        
        // Autres catégories gratuites
        return true
    }
}
