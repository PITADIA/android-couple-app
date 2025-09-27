package com.love2loveapp.services

import android.content.Context
import android.util.Log
import com.love2loveapp.R

/**
 * QuestionDataManager - Gestionnaire de chargement des questions
 * 
 * Équivalent iOS du système de chargement depuis fichiers locaux
 * Charge les questions depuis les ressources XML Android
 * 
 * Architecture similaire iOS :
 * - Cache intelligent des questions
 * - Chargement par catégorie  
 * - Support des traductions multiples
 */
class QuestionDataManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "QuestionDataManager"
        
        @Volatile
        private var INSTANCE: QuestionDataManager? = null
        
        fun getInstance(context: Context): QuestionDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuestionDataManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        val shared: QuestionDataManager
            get() = INSTANCE ?: throw IllegalStateException("QuestionDataManager must be initialized first")
    }
    
    // Cache des questions par catégorie
    private val questionCache = mutableMapOf<String, List<Question>>()
    
    // Mapping ID catégorie → préfixe XML
    private val categoryPrefixes = mapOf(
        "en-couple" to "ec_",           // 256 questions - GRATUIT
        "les-plus-hots" to "lph_",      // 255 questions - PREMIUM
        "a-distance" to "ad_",          // 383 questions - PREMIUM  
        "questions-profondes" to "qp_", // 255 questions - PREMIUM
        "pour-rire-a-deux" to "prad_",  // 126 questions - PREMIUM
        "tu-preferes" to "tp_",         // 255 questions - PREMIUM
        "mieux-ensemble" to "me_",      // 275 questions - PREMIUM
        "pour-un-date" to "pud_"        // 255 questions - PREMIUM
    )
    
    // Nombre de questions par catégorie (pour optimisation)
    private val questionCounts = mapOf(
        "en-couple" to 256,
        "les-plus-hots" to 255,
        "a-distance" to 383,
        "questions-profondes" to 255,
        "pour-rire-a-deux" to 126,
        "tu-preferes" to 255,
        "mieux-ensemble" to 275,
        "pour-un-date" to 255
    )
    
    /**
     * Charge toutes les questions d'une catégorie depuis XML
     * Équivalent iOS : QuestionDataManager.shared.loadQuestions(for: categoryId)
     */
    fun loadQuestions(categoryId: String): List<Question> {
        // Vérifier le cache d'abord
        questionCache[categoryId]?.let { cachedQuestions ->
            Log.d(TAG, "📋 Questions $categoryId chargées depuis cache: ${cachedQuestions.size}")
            return cachedQuestions
        }
        
        Log.d(TAG, "📦 Chargement questions pour catégorie: $categoryId")
        
        val prefix = categoryPrefixes[categoryId]
        if (prefix == null) {
            Log.e(TAG, "❌ Préfixe non trouvé pour catégorie: $categoryId")
            return emptyList()
        }
        
        val questionCount = questionCounts[categoryId] ?: 0
        if (questionCount == 0) {
            Log.e(TAG, "❌ Nombre de questions non défini pour: $categoryId")
            return emptyList()
        }
        
        val questions = mutableListOf<Question>()
        
        try {
            // Charger toutes les questions de 1 à questionCount
            for (i in 1..questionCount) {
                val questionKey = "${prefix}$i"
                
                // Obtenir l'ID de ressource dynamiquement
                val resourceId = context.resources.getIdentifier(
                    questionKey,
                    "string", 
                    context.packageName
                )
                
                if (resourceId != 0) {
                    // Créer l'objet Question
                    val question = Question(
                        id = questionKey,
                        textResId = resourceId,
                        categoryId = categoryId
                    )
                    questions.add(question)
                } else {
                    Log.w(TAG, "⚠️ Question non trouvée: $questionKey")
                }
            }
            
            Log.d(TAG, "✅ $categoryId: ${questions.size}/$questionCount questions chargées")
            
            // Mettre en cache
            questionCache[categoryId] = questions
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement questions $categoryId", e)
        }
        
        return questions
    }
    
    /**
     * Précharge toutes les catégories en arrière-plan
     * Équivalent iOS : preloadAllCategories()
     */
    fun preloadAllCategories() {
        Log.d(TAG, "🔄 Préchargement de toutes les catégories...")
        
        categoryPrefixes.keys.forEach { categoryId ->
            try {
                loadQuestions(categoryId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur préchargement $categoryId", e)
            }
        }
        
        Log.d(TAG, "✅ Préchargement terminé: ${questionCache.size} catégories en cache")
    }
    
    /**
     * Précharge uniquement les catégories essentielles (gratuite + 2 premium populaires)  
     * Équivalent iOS : preloadEssentialCategories()
     */
    fun preloadEssentialCategories() {
        Log.d(TAG, "⚡ Préchargement catégories essentielles...")
        
        val essentialCategories = listOf(
            "en-couple",          // Gratuit - toujours nécessaire
            "les-plus-hots",      // Premium populaire
            "questions-profondes" // Premium populaire
        )
        
        essentialCategories.forEach { categoryId ->
            try {
                loadQuestions(categoryId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur préchargement essentiel $categoryId", e)
            }
        }
        
        Log.d(TAG, "✅ Catégories essentielles chargées")
    }
    
    /**
     * Obtient le nombre total de questions pour une catégorie
     */
    fun getTotalQuestionCount(categoryId: String): Int {
        return questionCounts[categoryId] ?: 0
    }
    
    /**
     * Vérifie si une catégorie est disponible
     */
    fun isCategoryAvailable(categoryId: String): Boolean {
        return categoryPrefixes.containsKey(categoryId)
    }
    
    /**
     * Obtient les IDs de toutes les catégories disponibles
     */
    fun getAvailableCategoryIds(): List<String> {
        return categoryPrefixes.keys.toList()
    }
    
    /**
     * Vide le cache (pour tests ou changement de langue)
     */
    fun clearCache() {
        Log.d(TAG, "🗑️ Cache vidé")
        questionCache.clear()
    }
    
    /**
     * Obtient les statistiques du cache
     */
    fun getCacheStats(): String {
        val totalCached = questionCache.values.sumOf { it.size }
        return "Cache: ${questionCache.size} catégories, $totalCached questions"
    }
}

/**
 * Modèle de données pour une question
 * Équivalent iOS : struct Question
 */
data class Question(
    val id: String,         // ID unique (ex: "ec_1", "lph_15")
    val textResId: Int,     // Resource ID pour le texte traduit
    val categoryId: String  // ID de la catégorie parente
) {
    /**
     * Obtient le texte traduit de la question
     */
    fun getText(context: Context): String {
        return try {
            context.getString(textResId)
        } catch (e: Exception) {
            Log.e("Question", "❌ Erreur récupération texte pour $id", e)
            "Question non disponible"
        }
    }
}
