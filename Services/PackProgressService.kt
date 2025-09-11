package com.yourapp.progress

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * Gestion de la progression par "packs" (32 questions par pack).
 * - Stockage: SharedPreferences (Map<String, Int> sérialisée en JSON)
 * - Observation: StateFlow pour intégration directe avec Compose
 */
class PackProgressService private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // State observable (équivalent @Published)
    private val _packProgress = MutableStateFlow<Map<String, Int>>(loadProgress())
    val packProgress: StateFlow<Map<String, Int>> = _packProgress.asStateFlow()

    // --- Public Methods ---

    /** Obtenir le nombre de packs débloqués pour une catégorie (min 1). */
    fun getUnlockedPacks(categoryId: String): Int {
        return _packProgress.value[categoryId] ?: 1
    }

    /** Obtenir le nombre total de questions disponibles pour une catégorie. */
    fun getAvailableQuestionsCount(categoryId: String): Int {
        val unlockedPacks = getUnlockedPacks(categoryId)
        return unlockedPacks * QUESTIONS_PER_PACK
    }

    /** Vérifier si l'utilisateur a terminé un pack. */
    fun checkPackCompletion(categoryId: String, currentIndex: Int): Boolean {
        val currentPack = getCurrentPack(currentIndex)
        val unlockedPacks = getUnlockedPacks(categoryId)
        val isLastQuestionOfPack = ((currentIndex + 1) % QUESTIONS_PER_PACK == 0)
        val isCurrentPackCompleted = currentPack <= unlockedPacks
        return isLastQuestionOfPack && isCurrentPackCompleted
    }

    /** Débloquer le pack suivant pour une catégorie. */
    fun unlockNextPack(categoryId: String) {
        val next = getUnlockedPacks(categoryId) + 1
        _packProgress.update { it.toMutableMap().apply { this[categoryId] = next } }
        saveProgress()
        Log.d(TAG, "🔥 PackProgressService: Pack $next débloqué pour $categoryId")
    }

    /** Obtenir le numéro de pack courant à partir de l'index de question (0-based). */
    fun getCurrentPack(questionIndex: Int): Int {
        return (questionIndex / QUESTIONS_PER_PACK) + 1
    }

    /** Vérifier si une question est accessible (dans un pack débloqué). */
    fun isQuestionAccessible(categoryId: String, questionIndex: Int): Boolean {
        val questionPack = getCurrentPack(questionIndex)
        val unlockedPacks = getUnlockedPacks(categoryId)
        return questionPack <= unlockedPacks
    }

    /** Retourner la tranche de questions accessibles pour la catégorie. */
    fun <T> getAccessibleQuestions(allQuestions: List<T>, categoryId: String): List<T> {
        val availableCount = getAvailableQuestionsCount(categoryId)
        return allQuestions.take(availableCount)
    }

    /** Réinitialiser la progression pour une catégorie. */
    fun resetProgress(categoryId: String) {
        _packProgress.update { it.toMutableMap().apply { this[categoryId] = 1 } }
        saveProgress()
        Log.d(TAG, "🔥 PackProgressService: Progression réinitialisée pour $categoryId")
    }

    /** Réinitialiser toute la progression. */
    fun resetAllProgress() {
        _packProgress.value = emptyMap()
        saveProgress()
        Log.d(TAG, "🔥 PackProgressService: Toute la progression réinitialisée")
    }

    // --- Extensions "utilitaires" équivalentes ---

    data class ProgressInfo(val unlockedPacks: Int, val totalQuestions: Int)

    /** Obtenir des infos formatées pour l’affichage. */
    fun getProgressInfo(categoryId: String): ProgressInfo {
        val unlocked = getUnlockedPacks(categoryId)
        val total = getAvailableQuestionsCount(categoryId)
        return ProgressInfo(unlocked, total)
    }

    /** Obtenir le pourcentage de progression (en %). */
    fun getProgressPercentage(categoryId: String, totalQuestions: Int): Double {
        val available = getAvailableQuestionsCount(categoryId)
        if (totalQuestions <= 0) return 0.0
        return (available.toDouble() / totalQuestions.toDouble()) * 100.0
    }

    // --- Persistence ---

    private fun loadProgress(): Map<String, Int> {
        val json = prefs.getString(PREF_KEY_PACK_PROGRESS, null)
        return if (json.isNullOrBlank()) {
            Log.d(TAG, "🔥 PackProgressService: Aucune progression sauvegardée, démarrage à zéro")
            emptyMap()
        } else {
            try {
                val obj = JSONObject(json)
                val map = mutableMapOf<String, Int>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = obj.optInt(key, 1)
                }
                Log.d(TAG, "🔥 PackProgressService: Progression chargée: $map")
                map
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erreur parsing préférences, réinitialisation", e)
                emptyMap()
            }
        }
    }

    private fun saveProgress() {
        val json = JSONObject()
        for ((key, value) in _packProgress.value) {
            json.put(key, value)
        }
        prefs.edit().putString(PREF_KEY_PACK_PROGRESS, json.toString()).apply()
        Log.d(TAG, "🔥 PackProgressService: Progression sauvegardée: ${_packProgress.value}")
    }

    companion object {
        private const val TAG = "PackProgressService"
        private const val PREFS_FILE = "pack_progress_prefs"
        private const val PREF_KEY_PACK_PROGRESS = "PackProgressKey"
        private const val QUESTIONS_PER_PACK = 32

        @Volatile
        private var INSTANCE: PackProgressService? = null

        /** Singleton à la Swift: `PackProgressService.shared` → `getInstance(context)` */
        fun getInstance(context: Context): PackProgressService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PackProgressService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
