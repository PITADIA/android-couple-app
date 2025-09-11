package com.love2love.progress

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * Portage Kotlin de CategoryProgressService (Swift/Combine -> Kotlin/Flow).
 *\n * - Persistance via SharedPreferences (clé unique JSON sérialisant Map<String, Int>).
 * - Exposition d'un StateFlow pour une intégration fluide avec Jetpack Compose.
 * - API équivalente : saveCurrentIndex, getCurrentIndex, hasProgress, resetProgress,
 *   resetAllProgress, getProgressSummary, getProgressInfo, moveToNext, moveToPrevious.
 * - Logs détaillés (Logcat) alignés sur les prints Swift.
 *
 * \uD83D\uDCE3 Localisation : ce service n'appuie aucun texte localisé.
 * Pour afficher des chaînes côté UI, utilise `context.getString(R.string.your_key)`
 * (ou `stringResource(id = R.string.your_key)` en Compose).
 */
class CategoryProgressService private constructor(
    context: Context
) {
    companion object {
        private const val TAG = "CategoryProgressService"
        private const val PREFS_NAME = "category_progress_prefs"
        private const val CATEGORY_PROGRESS_KEY = "CategoryProgressKey"

        @Volatile private var instance: CategoryProgressService? = null

        /**
         * Récupère l'instance singleton, basée sur l'Application Context.
         */
        fun getInstance(context: Context): CategoryProgressService {
            return instance ?: synchronized(this) {
                instance ?: CategoryProgressService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _categoryProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Flux lisible de l'état courant (équivalent @Published en Swift). */
    val categoryProgress: StateFlow<Map<String, Int>> = _categoryProgress.asStateFlow()

    init { loadProgress() }

    // ==== Public API ========================================================

    /** Sauvegarder la position actuelle dans une catégorie. */
    fun saveCurrentIndex(index: Int, categoryId: String) {
        Log.d(TAG, "\uD83D\uDCCA === SAUVEGARDE PROGRESSION ===")
        Log.d(TAG, "\uD83D\uDCCA Sauvegarde position $index pour '$categoryId'")
        Log.d(TAG, "\uD83D\uDCCA Avant - categoryProgress: ${_categoryProgress.value}")

        _categoryProgress.update { current ->
            current.toMutableMap().apply { this[categoryId] = index }
        }
        saveProgress()

        Log.d(TAG, "\uD83D\uDCCA Après - categoryProgress: ${_categoryProgress.value}")
        Log.d(TAG, "\uD83D\uDCCA ✅ Position $index sauvegardée pour '$categoryId'")
    }

    /** Récupérer la dernière position dans une catégorie. */
    fun getCurrentIndex(categoryId: String): Int {
        val savedIndex = _categoryProgress.value[categoryId] ?: 0
        Log.d(TAG, "\uD83D\uDD25 Position récupérée pour '$categoryId': $savedIndex")
        return savedIndex
    }

    /** Vérifier si une catégorie a une position sauvegardée. */
    fun hasProgress(categoryId: String): Boolean =
        _categoryProgress.value.containsKey(categoryId)

    /** Réinitialiser la progression d'une catégorie. */
    fun resetProgress(categoryId: String) {
        _categoryProgress.update { current ->
            current.toMutableMap().apply { this[categoryId] = 0 }
        }
        saveProgress()
        Log.d(TAG, "\uD83D\uDD25 Progression réinitialisée pour '$categoryId'")
    }

    /** Réinitialiser toute la progression. */
    fun resetAllProgress() {
        _categoryProgress.value = emptyMap()
        saveProgress()
        Log.d(TAG, "\uD83D\uDD25 Toute la progression réinitialisée")
    }

    /** Obtenir un résumé de la progression. */
    fun getProgressSummary(): Map<String, Int> = _categoryProgress.value

    /** Informations formatées pour l'affichage. */
    fun getProgressInfo(categoryId: String, totalQuestions: Int): ProgressInfo {
        val currentIndex = getCurrentIndex(categoryId)
        val percentage = if (totalQuestions > 0) {
            ((currentIndex + 1).toDouble() / totalQuestions.toDouble()) * 100.0
        } else 0.0
        return ProgressInfo(currentIndex, percentage)
    }

    /** Avancer à la question suivante. */
    fun moveToNext(categoryId: String, maxIndex: Int) {
        val currentIndex = getCurrentIndex(categoryId)
        val nextIndex = (currentIndex + 1).coerceAtMost(maxIndex)
        saveCurrentIndex(nextIndex, categoryId)
    }

    /** Reculer à la question précédente. */
    fun moveToPrevious(categoryId: String) {
        val currentIndex = getCurrentIndex(categoryId)
        val previousIndex = (currentIndex - 1).coerceAtLeast(0)
        saveCurrentIndex(previousIndex, categoryId)
    }

    data class ProgressInfo(val currentIndex: Int, val percentage: Double)

    // ==== Private ===========================================================

    private fun loadProgress() {
        val json = prefs.getString(CATEGORY_PROGRESS_KEY, null)
        if (json.isNullOrEmpty()) {
            _categoryProgress.value = emptyMap()
            Log.d(TAG, "\uD83D\uDD25 Aucune progression sauvegardée, démarrage à zéro")
            return
        }

        try {
            val jsonObj = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObj.optInt(key, 0)
            }
            _categoryProgress.value = map
            Log.d(TAG, "\uD83D\uDD25 Progression chargée: $map")
        } catch (t: Throwable) {
            _categoryProgress.value = emptyMap()
            Log.w(TAG, "⚠️ Erreur de parsing des préférences, réinitialisation.", t)
        }
    }

    private fun saveProgress() {
        try {
            val jsonObj = JSONObject()
            _categoryProgress.value.forEach { (k, v) -> jsonObj.put(k, v) }
            prefs.edit().putString(CATEGORY_PROGRESS_KEY, jsonObj.toString()).apply()
            Log.d(TAG, "\uD83D\uDD25 Progression sauvegardée: ${_categoryProgress.value}")
        } catch (t: Throwable) {
            Log.e(TAG, "❌ Erreur lors de la sauvegarde de la progression", t)
        }
    }
}
