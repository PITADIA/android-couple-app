package com.love2loveapp.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PackProgressService - Gestionnaire du système de packs de 32 cartes
 * 
 * Réplication exacte du système iOS :
 * - 32 questions par pack
 * - Déblocage progressif avec carte spéciale à la 32ème question
 * - Animation NewPackReveal lors du déblocage
 * - Persistance des packs débloqués par utilisateur
 * 
 * Équivalent iOS : PackProgressService.swift
 */
class PackProgressService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "PackProgressService"
        private const val QUESTIONS_PER_PACK = 32  // 32 questions par pack (comme iOS)
        private const val PACK_PROGRESS_KEY = "PackProgressKey"
        
        @Volatile
        private var INSTANCE: PackProgressService? = null
        
        fun getInstance(context: Context): PackProgressService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PackProgressService(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        val shared: PackProgressService
            get() = INSTANCE ?: throw IllegalStateException("PackProgressService must be initialized first")
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences("love2love_pack_progress", Context.MODE_PRIVATE)
    
    private val analytics = FirebaseAnalytics.getInstance(context)
    
    // État observable des packs débloqués par catégorie
    private val _packProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val packProgress: StateFlow<Map<String, Int>> = _packProgress.asStateFlow()
    
    init {
        loadProgress()
        Log.d(TAG, "🎯 PackProgressService initialisé")
    }
    
    /**
     * Obtient le nombre de packs débloqués pour une catégorie
     * Par défaut : 1 pack débloqué (32 premières questions)
     */
    fun getUnlockedPacks(categoryId: String): Int {
        val unlockedPacks = _packProgress.value[categoryId] ?: 1
        Log.d(TAG, "📦 $categoryId: $unlockedPacks packs débloqués")
        return unlockedPacks
    }
    
    /**
     * Obtient le nombre total de questions accessibles pour une catégorie
     * Équivalent iOS : getAvailableQuestionsCount(for: categoryId)
     */
    fun getAvailableQuestionsCount(categoryId: String): Int {
        val unlockedPacks = getUnlockedPacks(categoryId)
        val availableCount = unlockedPacks * QUESTIONS_PER_PACK
        Log.d(TAG, "🎯 $categoryId: $availableCount questions accessibles ($unlockedPacks packs)")
        return availableCount
    }
    
    /**
     * Filtre les questions selon les packs débloqués
     * Équivalent iOS : getAccessibleQuestions(questions, categoryId)
     */
    fun getAccessibleQuestions(allQuestions: List<Question>, categoryId: String): List<Question> {
        val maxQuestions = getAvailableQuestionsCount(categoryId)
        val accessibleQuestions = allQuestions.take(maxQuestions)
        
        Log.d(TAG, "✂️ $categoryId: ${accessibleQuestions.size}/${allQuestions.size} questions accessibles")
        return accessibleQuestions
    }
    
    /**
     * Vérifie si nous sommes à la fin d'un pack (question 32, 64, 96...)
     * Équivalent iOS : checkPackCompletion(categoryId, currentIndex)
     */
    fun checkPackCompletion(categoryId: String, currentIndex: Int): Boolean {
        val questionNumber = currentIndex + 1  // Index 0-based → numéro 1-based
        val isLastQuestionOfPack = questionNumber % QUESTIONS_PER_PACK == 0
        
        if (isLastQuestionOfPack) {
            val packNumber = questionNumber / QUESTIONS_PER_PACK
            Log.d(TAG, "🎉 $categoryId: Fin du pack $packNumber atteinte (question $questionNumber)")
        }
        
        return isLastQuestionOfPack
    }
    
    /**
     * Vérifie s'il y a plus de questions à débloquer
     */
    fun hasMorePacksToUnlock(categoryId: String, totalQuestions: Int): Boolean {
        val unlockedPacks = getUnlockedPacks(categoryId)
        val currentAccessibleQuestions = unlockedPacks * QUESTIONS_PER_PACK
        val hasMore = currentAccessibleQuestions < totalQuestions
        
        Log.d(TAG, "🔍 $categoryId: Plus de packs à débloquer ? $hasMore ($currentAccessibleQuestions < $totalQuestions)")
        return hasMore
    }
    
    /**
     * Débloque le pack suivant
     * Équivalent iOS : unlockNextPack(for: categoryId)  
     */
    fun unlockNextPack(categoryId: String): Int {
        val currentUnlockedPacks = getUnlockedPacks(categoryId)
        val newUnlockedPacks = currentUnlockedPacks + 1
        
        // Mettre à jour l'état
        val newProgress = _packProgress.value.toMutableMap()
        newProgress[categoryId] = newUnlockedPacks
        _packProgress.value = newProgress
        
        // Sauvegarder
        saveProgress()
        
        // Analytics Firebase (comme iOS)
        analytics.logEvent("pack_complete") {
            param("categorie", categoryId)
            param("pack_numero", newUnlockedPacks.toLong())
            param("questions_debloquees", (newUnlockedPacks * QUESTIONS_PER_PACK).toLong())
        }
        
        Log.d(TAG, "🔓 $categoryId: Pack $newUnlockedPacks débloqué ! ${newUnlockedPacks * QUESTIONS_PER_PACK} questions accessibles")
        
        return newUnlockedPacks
    }
    
    /**
     * Obtient le numéro du pack actuel pour un index de question
     */
    fun getCurrentPack(questionIndex: Int): Int {
        return (questionIndex / QUESTIONS_PER_PACK) + 1
    }
    
    /**
     * Obtient la position dans le pack actuel (1-32)
     */
    fun getPositionInPack(questionIndex: Int): Int {
        return (questionIndex % QUESTIONS_PER_PACK) + 1
    }
    
    /**
     * Vérifie si on doit afficher la carte PackCompletion
     * Affichée avant la dernière question du pack (comme iOS)
     */
    fun shouldShowPackCompletionCard(
        categoryId: String,
        currentIndex: Int,
        accessibleQuestions: List<Question>,
        totalQuestions: Int
    ): Boolean {
        // On affiche la carte avant la dernière question accessible
        val isBeforeLastAccessible = currentIndex == accessibleQuestions.size - 1
        val hasMorePacks = hasMorePacksToUnlock(categoryId, totalQuestions)
        
        val shouldShow = isBeforeLastAccessible && hasMorePacks
        
        if (shouldShow) {
            Log.d(TAG, "🎴 $categoryId: Affichage PackCompletionCard (question ${currentIndex + 1}/${accessibleQuestions.size})")
        }
        
        return shouldShow
    }
    
    /**
     * Réinitialise les progrès d'une catégorie (pour tests)
     */
    fun resetProgress(categoryId: String) {
        val newProgress = _packProgress.value.toMutableMap()
        newProgress[categoryId] = 1  // Retour au pack par défaut
        _packProgress.value = newProgress
        saveProgress()
        
        Log.d(TAG, "🔄 $categoryId: Progrès réinitialisé")
    }
    
    /**
     * Obtient les statistiques de progression
     */
    fun getProgressStats(categoryId: String, totalQuestions: Int): PackStats {
        val unlockedPacks = getUnlockedPacks(categoryId)
        val accessibleQuestions = unlockedPacks * QUESTIONS_PER_PACK
        val remainingQuestions = maxOf(0, totalQuestions - accessibleQuestions)
        val completionPercentage = if (totalQuestions > 0) {
            (accessibleQuestions.toFloat() / totalQuestions * 100).toInt()
        } else 0
        
        return PackStats(
            categoryId = categoryId,
            unlockedPacks = unlockedPacks,
            accessibleQuestions = accessibleQuestions,
            totalQuestions = totalQuestions,
            remainingQuestions = remainingQuestions,
            completionPercentage = completionPercentage
        )
    }
    
    /**
     * Sauvegarde le progrès dans SharedPreferences
     */
    private fun saveProgress() {
        try {
            val jsonObject = JSONObject()
            _packProgress.value.forEach { (key, value) ->
                jsonObject.put(key, value)
            }
            
            sharedPrefs.edit()
                .putString(PACK_PROGRESS_KEY, jsonObject.toString())
                .apply()
            
            Log.d(TAG, "💾 Progrès sauvegardé: ${_packProgress.value}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde progrès", e)
        }
    }
    
    /**
     * Charge le progrès depuis SharedPreferences
     */
    private fun loadProgress() {
        try {
            val progressJson = sharedPrefs.getString(PACK_PROGRESS_KEY, "{}")
            val jsonObject = JSONObject(progressJson ?: "{}")
            val progress = mutableMapOf<String, Int>()
            
            jsonObject.keys().forEach { key ->
                progress[key] = jsonObject.getInt(key)
            }
            
            _packProgress.value = progress
            Log.d(TAG, "📥 Progrès chargé: $progress")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement progrès", e)
            _packProgress.value = emptyMap()
        }
    }
}

/**
 * Statistiques de progression pour une catégorie
 */
data class PackStats(
    val categoryId: String,
    val unlockedPacks: Int,
    val accessibleQuestions: Int,
    val totalQuestions: Int,
    val remainingQuestions: Int,
    val completionPercentage: Int
) {
    override fun toString(): String {
        return "$categoryId: $unlockedPacks packs, $accessibleQuestions/$totalQuestions questions ($completionPercentage%)"
    }
}
