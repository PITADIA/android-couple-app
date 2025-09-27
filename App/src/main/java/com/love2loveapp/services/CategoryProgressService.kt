package com.love2loveapp.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * 📊 CategoryProgressService - Gestionnaire de progression par catégorie
 * 
 * Équivalent Android exact du CategoryProgressService iOS :
 * - Sauvegarde position actuelle dans chaque catégorie
 * - Persistance dans SharedPreferences (équivalent UserDefaults iOS)
 * - État observable avec StateFlow
 * - Méthodes identiques : saveCurrentIndex, getCurrentIndex, hasProgress
 */
class CategoryProgressService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "CategoryProgressService"
        private const val CATEGORY_PROGRESS_KEY = "CategoryProgressKey"
        
        @Volatile
        private var INSTANCE: CategoryProgressService? = null
        
        fun getInstance(context: Context): CategoryProgressService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CategoryProgressService(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        val shared: CategoryProgressService
            get() = INSTANCE ?: throw IllegalStateException("CategoryProgressService must be initialized first")
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences("love2love_category_progress", Context.MODE_PRIVATE)
    
    // État observable de la progression par catégorie
    private val _categoryProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val categoryProgress: StateFlow<Map<String, Int>> = _categoryProgress.asStateFlow()
    
    init {
        loadProgress()
        Log.d(TAG, "📊 CategoryProgressService initialisé")
    }
    
    /**
     * 🔑 SAUVEGARDER POSITION ACTUELLE DANS UNE CATÉGORIE
     * Équivalent iOS : func saveCurrentIndex(_ index: Int, for categoryId: String)
     */
    fun saveCurrentIndex(index: Int, categoryId: String) {
        Log.d(TAG, "📊 Sauvegarde position $index pour '$categoryId'")
        
        val currentProgress = _categoryProgress.value.toMutableMap()
        currentProgress[categoryId] = index
        _categoryProgress.value = currentProgress
        
        saveProgress()
    }
    
    /**
     * 🔑 RÉCUPÉRER DERNIÈRE POSITION DANS UNE CATÉGORIE
     * Équivalent iOS : func getCurrentIndex(for categoryId: String) -> Int
     */
    fun getCurrentIndex(categoryId: String): Int {
        val savedIndex = _categoryProgress.value[categoryId] ?: 0
        Log.d(TAG, "🔥 Position récupérée pour '$categoryId': $savedIndex")
        return savedIndex
    }
    
    /**
     * 🔑 VÉRIFIER SI CATÉGORIE A PROGRESSION SAUVEGARDÉE
     * Équivalent iOS : func hasProgress(for categoryId: String) -> Bool
     */
    fun hasProgress(categoryId: String): Boolean {
        return _categoryProgress.value.containsKey(categoryId)
    }
    
    /**
     * 🔑 OBTENIR RÉSUMÉ PROGRESSION
     * Équivalent iOS : func getProgressSummary() -> [String: Int]
     */
    fun getProgressSummary(): Map<String, Int> {
        return _categoryProgress.value
    }
    
    /**
     * 📊 CALCULER POURCENTAGE PROGRESSION GLOBAL (pour statistiques)
     * Équivalent iOS : calcul dans CoupleStatisticsView
     */
    fun calculateGlobalProgressPercentage(): Double {
        val categories = com.love2loveapp.models.QuestionCategory.categories
        var totalQuestions = 0
        var totalProgress = 0
        
        categories.forEach { category ->
            // Utiliser QuestionDataManager pour obtenir le nombre de questions
            val questions = com.love2loveapp.services.QuestionDataManager.getInstance(context)
                .loadQuestions(category.id)
            val currentIndex = getCurrentIndex(category.id)
            val progressForCategory = minOf(currentIndex + 1, questions.size)
            
            totalQuestions += questions.size
            totalProgress += progressForCategory
        }
        
        return if (totalQuestions > 0) {
            (totalProgress.toDouble() / totalQuestions.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * 💾 Sauvegarde du progrès dans SharedPreferences
     * Équivalent iOS : private func saveProgress()
     */
    private fun saveProgress() {
        try {
            val jsonObject = JSONObject()
            _categoryProgress.value.forEach { (key, value) ->
                jsonObject.put(key, value)
            }
            
            sharedPrefs.edit()
                .putString(CATEGORY_PROGRESS_KEY, jsonObject.toString())
                .apply()
            
            Log.d(TAG, "💾 Progression sauvegardée: ${_categoryProgress.value}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde progression", e)
        }
    }
    
    /**
     * 📥 Chargement du progrès depuis SharedPreferences
     * Équivalent iOS : private func loadProgress()
     */
    private fun loadProgress() {
        try {
            val progressJson = sharedPrefs.getString(CATEGORY_PROGRESS_KEY, "{}")
            val jsonObject = JSONObject(progressJson ?: "{}")
            val progress = mutableMapOf<String, Int>()
            
            jsonObject.keys().forEach { key ->
                progress[key] = jsonObject.getInt(key)
            }
            
            _categoryProgress.value = progress
            Log.d(TAG, "📥 Progression chargée: $progress")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement progression", e)
            _categoryProgress.value = emptyMap()
        }
    }
}
