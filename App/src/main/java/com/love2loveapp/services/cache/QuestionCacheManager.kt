package com.love2loveapp.services.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import com.love2loveapp.services.cache.dao.*
import com.love2loveapp.services.cache.entities.*
import com.love2loveapp.models.DailyQuestion
import com.love2loveapp.models.DailyChallenge
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 📱 QuestionCacheManager Android - Cache Persistant Sophistiqué
 * 
 * Architecture équivalente Realm iOS QuestionCacheManager:
 * - Room Database → Realm Database iOS
 * - StateFlow @Published → @Published iOS
 * - Compactage automatique → shouldCompactOnLaunch iOS
 * - Cache intelligent avec TTL → logique cache iOS
 * - Performance optimisée → indexation Realm iOS
 * - Observers temps réel → Realm notifications iOS
 * - Équivalent complet du QuestionCacheManager iOS
 */
class QuestionCacheManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "QuestionCacheManager"
        private const val MAX_CACHED_QUESTIONS = 30 // Limite comme iOS
        private const val MAX_CACHED_CHALLENGES = 30
        private const val CACHE_MAX_AGE_DAYS = 30L // 30 jours retention
        
        @Volatile
        private var instance: QuestionCacheManager? = null
        
        fun getInstance(context: Context): QuestionCacheManager {
            return instance ?: synchronized(this) {
                instance ?: QuestionCacheManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Base de données Room (équivalent Realm iOS)
    private val database: CacheDatabase = CacheDatabase.getDatabase(context)
    private val questionsDao: DailyQuestionsDao = database.dailyQuestionsDao()
    private val challengesDao: DailyChallengesDao = database.dailyChallengesDao()
    
    // États observables (équivalent @Published iOS)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _cacheStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val cacheStatus: StateFlow<Map<String, Boolean>> = _cacheStatus.asStateFlow()
    
    private val _isDatabaseAvailable = MutableStateFlow(false)
    val isDatabaseAvailable: StateFlow<Boolean> = _isDatabaseAvailable.asStateFlow()
    
    // Scope pour coroutines (équivalent Task iOS)
    private val cacheScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        Log.d(TAG, "✅ QuestionCacheManager initialisé")
        _isDatabaseAvailable.value = true
        
        // Démarrer nettoyage automatique (équivalent shouldCompactOnLaunch iOS)
        cacheScope.launch {
            scheduleAutomaticCleanup()
        }
    }
    
    // =======================
    // CACHE QUESTIONS QUOTIDIENNES (équivalent iOS)
    // =======================
    
    /**
     * Cache une question quotidienne  
     * Équivalent de cacheDailyQuestion(_ question: DailyQuestion) iOS
     */
    suspend fun cacheDailyQuestion(question: DailyQuestion) {
        if (!_isDatabaseAvailable.value) {
            Log.w(TAG, "⚠️ Database non disponible pour cache question quotidienne")
            return
        }
        
        try {
            val entity = DailyQuestionEntity.fromDailyQuestion(question)
            questionsDao.cacheDailyQuestion(entity)
            
            Log.d(TAG, "✅ Question quotidienne cachée: ${question.questionKey}")
            
            // Mettre à jour status cache
            updateCacheStatus(question.coupleId, true)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur cache question quotidienne: ${e.message}", e)
        }
    }
    
    /**
     * Récupère question quotidienne depuis cache
     * Équivalent de getCachedDailyQuestion(for:date:) iOS
     */
    suspend fun getCachedDailyQuestion(coupleId: String, date: String): DailyQuestion? {
        if (!_isDatabaseAvailable.value) return null
        
        return try {
            val entity = questionsDao.getDailyQuestion(coupleId, date)
            entity?.toDailyQuestion()?.also {
                Log.d(TAG, "🚀 Question quotidienne trouvée en cache: ${it.questionKey}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur récupération cache question: ${e.message}", e)
            null
        }
    }
    
    /**
     * Récupère question d'aujourd'hui depuis cache
     * Équivalent de getTodayQuestion(coupleId:) iOS
     */
    suspend fun getTodayQuestion(coupleId: String): DailyQuestion? {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return getCachedDailyQuestion(coupleId, today)
    }
    
    /**
     * Récupère toutes les questions en cache
     * Équivalent de getCachedDailyQuestions(for:limit:) iOS
     */
    suspend fun getCachedDailyQuestions(coupleId: String, limit: Int = MAX_CACHED_QUESTIONS): List<DailyQuestion> {
        if (!_isDatabaseAvailable.value) return emptyList()
        
        return try {
            questionsDao.getCachedDailyQuestions(coupleId, limit)
                .map { it.toDailyQuestion() }
                .also {
                    Log.d(TAG, "📚 ${it.size} questions trouvées en cache pour couple: $coupleId")
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur récupération cache questions: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * LiveData pour observation temps réel des questions
     * Équivalent de @Published dans iOS
     */
    fun getCachedDailyQuestionsLiveData(coupleId: String, limit: Int = MAX_CACHED_QUESTIONS): LiveData<List<DailyQuestion>> {
        return questionsDao.getCachedDailyQuestionsLiveData(coupleId, limit).map { entities ->
            entities.map { it.toDailyQuestion() }
        }
    }
    
    // =======================
    // CACHE DÉFIS QUOTIDIENS (équivalent iOS)
    // =======================
    
    /**
     * Cache un défi quotidien
     * Équivalent de cacheDailyChallenge(_ challenge: DailyChallenge) iOS
     */
    suspend fun cacheDailyChallenge(challenge: DailyChallenge) {
        if (!_isDatabaseAvailable.value) {
            Log.w(TAG, "⚠️ Database non disponible pour cache défi quotidien")
            return
        }
        
        try {
            val entity = DailyChallengeEntity.fromDailyChallenge(challenge)
            challengesDao.cacheDailyChallenge(entity)
            
            Log.d(TAG, "✅ Défi quotidien caché: ${challenge.challengeKey}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur cache défi quotidien: ${e.message}", e)
        }
    }
    
    /**
     * Récupère le défi d'aujourd'hui
     * Équivalent de getTodayChallenge(coupleId:) iOS
     */
    suspend fun getTodayChallenge(coupleId: String): DailyChallenge? {
        if (!_isDatabaseAvailable.value) return null
        
        return try {
            // Calculer début/fin de journée
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val endOfDay = calendar.timeInMillis
            
            val entity = challengesDao.getDailyChallenge(coupleId, startOfDay, endOfDay)
            entity?.toDailyChallenge()?.also {
                Log.d(TAG, "🚀 Défi quotidien trouvé en cache: ${it.challengeKey}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur récupération cache défi: ${e.message}", e)
            null
        }
    }
    
    /**
     * Marque un défi comme complété
     * Équivalent de markChallengeCompleted(challengeId:) iOS
     */
    suspend fun markChallengeCompleted(challengeId: String) {
        try {
            challengesDao.markChallengeCompleted(challengeId)
            Log.d(TAG, "✅ Défi marqué comme complété: $challengeId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur marquage défi complété: ${e.message}", e)
        }
    }
    
    /**
     * Récupère tous les défis en cache
     */
    suspend fun getCachedDailyChallenges(coupleId: String, limit: Int = MAX_CACHED_CHALLENGES): List<DailyChallenge> {
        if (!_isDatabaseAvailable.value) return emptyList()
        
        return try {
            challengesDao.getCachedDailyChallenges(coupleId, limit)
                .map { it.toDailyChallenge() }
                .also {
                    Log.d(TAG, "🏆 ${it.size} défis trouvés en cache pour couple: $coupleId")
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur récupération cache défis: ${e.message}", e)
            emptyList()
        }
    }
    
    // =======================
    // GESTION CACHE ET NETTOYAGE (équivalent iOS)
    // =======================
    
    /**
     * Met à jour le status du cache pour un couple
     * Équivalent de updateCacheStatus() iOS
     */
    private fun updateCacheStatus(coupleId: String, isLoaded: Boolean) {
        val currentStatus = _cacheStatus.value.toMutableMap()
        currentStatus[coupleId] = isLoaded
        _cacheStatus.value = currentStatus
    }
    
    /**
     * Vérifie si un couple a des données en cache
     */
    suspend fun hasCachedData(coupleId: String): Boolean {
        if (!_isDatabaseAvailable.value) return false
        
        return try {
            val questionCount = questionsDao.getQuestionCount(coupleId)
            questionCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification cache: ${e.message}", e)
            false
        }
    }
    
    /**
     * Supprime toutes les données cache d'un couple
     * Équivalent de clearCacheForCouple() iOS
     */
    suspend fun clearCacheForCouple(coupleId: String) {
        if (!_isDatabaseAvailable.value) return
        
        try {
            questionsDao.clearQuestionsForCouple(coupleId)
            challengesDao.clearChallengesForCouple(coupleId)
            
            Log.d(TAG, "🗑️ Cache vidé pour couple: $coupleId")
            updateCacheStatus(coupleId, false)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage cache couple: ${e.message}", e)
        }
    }
    
    /**
     * Nettoyage automatique programmé  
     * Équivalent de shouldCompactOnLaunch iOS
     */
    private suspend fun scheduleAutomaticCleanup() {
        try {
            // Nettoyer données anciennes de plus de 30 jours
            val cutoffTime = System.currentTimeMillis() - (CACHE_MAX_AGE_DAYS * 24 * 60 * 60 * 1000)
            
            questionsDao.cleanupOldQuestions(cutoffTime)
            challengesDao.cleanupOldChallenges(cutoffTime)
            
            Log.d(TAG, "🧹 Nettoyage automatique cache terminé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage automatique: ${e.message}", e)
        }
    }
    
    /**
     * Force nettoyage complet (équivalent clearCache iOS)
     */
    suspend fun clearAllCache() {
        if (!_isDatabaseAvailable.value) return
        
        try {
            CacheDatabase.nukeDatabase(context)
            _cacheStatus.value = emptyMap()
            _isDatabaseAvailable.value = false
            
            // Réinitialiser database
            delay(100)
            val newDatabase = CacheDatabase.getDatabase(context)
            _isDatabaseAvailable.value = true
            
            Log.i(TAG, "🗑️ Cache complet vidé et réinitialisé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage complet: ${e.message}", e)
        }
    }
    
    /**
     * Précharge les catégories essentielles
     * Équivalent de preloadEssentialCategories() iOS
     */
    suspend fun preloadEssentialCategories() {
        _isLoading.value = true
        
        try {
            // Logique de préchargement selon besoins app
            Log.d(TAG, "⚡ Préchargement catégories essentielles")
            
            delay(500) // Simulation préchargement
            
            Log.d(TAG, "✅ Préchargement terminé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur préchargement: ${e.message}", e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Optimise l'utilisation mémoire
     * Équivalent de optimizeMemoryUsage() iOS
     */
    fun optimizeMemoryUsage() {
        cacheScope.launch {
            try {
                // Déclencher compactage Room si nécessaire
                database.query("VACUUM", null)
                Log.d(TAG, "🧹 Optimisation mémoire terminée")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur optimisation mémoire: ${e.message}", e)
            }
        }
    }
    
    /**
     * Informations de debug complètes
     * Équivalent des méthodes debug iOS
     */
    suspend fun getDebugInfo(): String {
        if (!_isDatabaseAvailable.value) {
            return "❌ Database non disponible"
        }
        
        return try {
            val totalQuestions = questionsDao.getCachedDailyQuestions("", Int.MAX_VALUE).size
            val totalChallenges = challengesDao.getCachedDailyChallenges("", Int.MAX_VALUE).size
            
            """
                📊 DEBUG QuestionCacheManager:
                - Database disponible: ${_isDatabaseAvailable.value}
                - Questions en cache: $totalQuestions
                - Défis en cache: $totalChallenges
                - Status cache: ${_cacheStatus.value}
                - En chargement: ${_isLoading.value}
                - Path database: ${database.openHelper.readableDatabase.path}
            """.trimIndent()
        } catch (e: Exception) {
            "❌ Erreur récupération debug info: ${e.message}"
        }
    }
    
    /**
     * Nettoyage ressources (appelé lors destroy app)
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage QuestionCacheManager")
        cacheScope.cancel()
        database.close()
    }
}
