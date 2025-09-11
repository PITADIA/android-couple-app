// QuestionDataManager.kt
package com.yourapp.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Gestionnaire optimisé pour les données de questions (Android).
 * Remplace l'usage de .xcstrings par strings.xml (resources Android).
 *
 * - Localisation : context.getString(R.string.key)
 * - Résolution dynamique : resources.getIdentifier("ec_2", "string", packageName)
 * - Fallback : si la ressource n'existe pas dans la langue courante, on tente l'anglais.
 */
object QuestionDataManager {

    // État de chargement (Compose-friendly)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    // Cache: clé = "${categoryId}_${language}"
    private val questionsCache: MutableMap<String, List<Question>> = HashMap()

    // Mapping catégorie → (préfixe des clés, index de départ)
    private val CATEGORY_MAPPING: Map<String, CategoryInfo> = mapOf(
        "en-couple"           to CategoryInfo(prefix = "ec_",  startIndex = 2),
        "les-plus-hots"       to CategoryInfo(prefix = "lph_", startIndex = 2),
        "pour-rire-a-deux"    to CategoryInfo(prefix = "prad_",startIndex = 2),
        "questions-profondes" to CategoryInfo(prefix = "qp_",  startIndex = 2),
        "a-distance"          to CategoryInfo(prefix = "ad_",  startIndex = 2),
        "tu-preferes"         to CategoryInfo(prefix = "tp_",  startIndex = 2),
        "mieux-ensemble"      to CategoryInfo(prefix = "me_",  startIndex = 2),
        "pour-un-date"        to CategoryInfo(prefix = "pud_", startIndex = 2),
    )

    data class CategoryInfo(
        val prefix: String,
        val startIndex: Int
    )

    // -------- Public API --------

    /**
     * Charge les questions pour une catégorie donnée.
     * Le cache est indexé par catégorie + langue courante (fr/en).
     */
    fun loadQuestions(
        context: Context,
        categoryId: String,
        language: String = getCurrentLanguage(context)
    ): List<Question> {
        val cacheKey = "${categoryId}_$language"
        questionsCache[cacheKey]?.let { return it }

        val questions = loadQuestionsFromResources(context, categoryId)
        questionsCache[cacheKey] = questions
        return questions
    }

    /**
     * Précharge les catégories essentielles (gratuit).
     * Appelle-le depuis un ViewModel (scope) si tu veux lier à l’UI.
     */
    suspend fun preloadEssentialCategories(context: Context) {
        _isLoading.emit(true)
        try {
            val essential = listOf("en-couple")
            val lang = getCurrentLanguage(context)
            essential.forEach { loadQuestions(context, it, lang) }
        } finally {
            _isLoading.emit(false)
        }
    }

    /**
     * Vide le cache (utile après un changement de langue manuel).
     */
    fun clearCache() {
        questionsCache.clear()
    }

    // -------- Internal --------

    private fun loadQuestionsFromResources(
        context: Context,
        categoryId: String
    ): List<Question> {
        val info = CATEGORY_MAPPING[categoryId]
        if (info == null) {
            println("⚠️ QuestionDataManager: Catégorie inconnue: $categoryId")
            return emptyList()
        }

        val keyPrefix = info.prefix
        val startIndex = info.startIndex

        val found = mutableListOf<Question>()
        // On scanne jusqu’à 300 par cohérence avec ta version Swift.
        for (i in startIndex..300) {
            val key = "${keyPrefix}${i}"
            val text = localizedString(context, key)
            if (!text.isNullOrBlank()) {
                found += Question(id = key, text = text, category = categoryId)
            } else if (i > startIndex + 10 && found.isEmpty()) {
                // Même heuristique que ta version Swift : si rien trouvé
                // après un petit offset, on s’arrête tôt.
                break
            }
        }

        println("✅ QuestionDataManager: ${found.size} questions chargées pour $categoryId")
        return found
    }

    /**
     * Essaie de lire la ressource string nommée [key] dans la langue courante,
     * puis retombe sur l’anglais si indisponible.
     *
     * Équivalent Android de:
     *   challengeKey.localized(tableName: ...)  →  context.getString(R.string.challengeKey)
     * Ici, comme la clé est construite dynamiquement (ex: "ec_12"),
     * on utilise getIdentifier pour retrouver l'ID.
     */
    private fun localizedString(
        context: Context,
        key: String,
        englishFallback: Boolean = true
    ): String? {
        val resId = context.resources.getIdentifier(key, "string", context.packageName)
        if (resId == 0) return null // la clé n’existe pas du tout

        // 1) Tentative dans la langue courante
        val primary = safeGetString(context, resId)
        if (!primary.isNullOrBlank()) return primary

        // 2) Fallback anglais explicite (si souhaité)
        return if (englishFallback) getStringInLocale(context, resId, Locale.ENGLISH) else null
    }

    private fun safeGetString(context: Context, resId: Int): String? =
        try { context.getString(resId) } catch (_: Throwable) { null }

    private fun getStringInLocale(context: Context, resId: Int, locale: Locale): String? {
        return try {
            val conf = Configuration(context.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                conf.setLocale(locale)
            } else {
                @Suppress("DEPRECATION")
                conf.locale = locale
            }
            val localizedCtx = context.createConfigurationContext(conf)
            localizedCtx.getString(resId)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Détermine une étiquette simple de langue pour le cache ("fr" ou "en").
     * Android gère déjà automatiquement la sélection de ressources locales.
     * Ici c’est seulement pour éviter de mélanger le cache quand l’UI change de locale.
     */
    private fun getCurrentLanguage(context: Context): String {
        val lang = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)?.language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale?.language
        } ?: Locale.getDefault().language

        return if (lang.lowercase(Locale.ROOT).startsWith("fr")) "fr" else "en"
    }
}

// -------- Data Models --------

data class Question(
    val id: String,
    val text: String,
    val category: String
)

data class QuestionData(
    val category: String,
    val questions: List<QuestionJSON>
)

data class QuestionJSON(
    val id: String,
    val text: String
)
