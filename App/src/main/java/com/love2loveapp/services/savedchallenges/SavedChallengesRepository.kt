package com.love2loveapp.services.savedchallenges

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.love2loveapp.models.DailyChallenge
import com.love2loveapp.models.SavedChallenge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * 🔖 SavedChallengesRepository - Gestion Défis Sauvegardés
 * Équivalent iOS SavedChallengesService
 * 
 * Gère la sauvegarde locale et Firebase des défis préférés.
 * Permet aux couples de créer une collection partagée de défis.
 */
class SavedChallengesRepository private constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "SavedChallengesRepo"
        private const val COLLECTION_SAVED_CHALLENGES = "savedChallenges"
        
        @Volatile
        private var INSTANCE: SavedChallengesRepository? = null
        
        fun getInstance(context: Context): SavedChallengesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SavedChallengesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 📊 ÉTATS OBSERVABLES
    private val _savedChallenges = MutableStateFlow<List<SavedChallenge>>(emptyList())
    val savedChallenges: StateFlow<List<SavedChallenge>> = _savedChallenges.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // 🔄 LISTENERS FIRESTORE
    private var savedChallengesListener: ListenerRegistration? = null
    
    /**
     * 🚀 Initialisation pour un utilisateur
     */
    fun initializeForUser(userId: String) {
        Log.d(TAG, "🚀 Initialisation SavedChallengesRepository pour utilisateur: $userId")
        
        repositoryScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                setupRealtimeListener(userId)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur initialisation SavedChallengesRepository: ${e.message}")
                _errorMessage.value = "Erreur lors du chargement des défis sauvegardés"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 👂 Configuration listener temps réel Firestore
     */
    private fun setupRealtimeListener(userId: String) {
        Log.d(TAG, "👂 Configuration listener défis sauvegardés pour: $userId")
        
        // Stopper l'ancien listener
        savedChallengesListener?.remove()
        
        // 🔑 REQUÊTE PERSONNELLE comme iOS - Défis sauvegardés ne sont PAS partagés !
        savedChallengesListener = firestore.collection(COLLECTION_SAVED_CHALLENGES)
            .whereEqualTo("userId", userId)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener défis sauvegardés: ${error.message}")
                    _errorMessage.value = "Erreur synchronisation défis sauvegardés"
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    if (snapshot.documents.isNotEmpty()) {
                        Log.d(TAG, "📥 ${snapshot.documents.size} défis sauvegardés synchronisés")
                    }
                    
                    val challenges = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            
                            SavedChallenge(
                                id = doc.id,
                                challengeKey = data["challengeKey"] as? String ?: "",
                                challengeDay = (data["challengeDay"] as? Number)?.toInt() ?: 0,
                                challengeText = data["challengeText"] as? String ?: "",
                                emoji = data["emoji"] as? String ?: "",
                                userId = data["userId"] as? String ?: "",
                                userName = data["userName"] as? String ?: "",
                                savedAt = data["savedAt"] as? Timestamp ?: Timestamp.now()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erreur parsing défi sauvegardé ${doc.id}: ${e.message}")
                            null
                        }
                    }
                    
                    _savedChallenges.value = challenges
                    _errorMessage.value = null
                }
            }
    }
    
    /**
     * 💾 Sauvegarder un défi (système bookmark local)
     */
    suspend fun saveChallenge(
        challenge: DailyChallenge,
        challengeText: String,
        userId: String,
        userName: String
    ): Result<Unit> {
        return try {
            Log.d(TAG, "💾 Sauvegarde défi: ${challenge.challengeKey}")
            
            // Vérifier si déjà sauvegardé
            if (isChallengeAlreadySaved(challenge.challengeKey, userId)) {
                Log.w(TAG, "⚠️ Défi ${challenge.challengeKey} déjà sauvegardé par $userId")
                return Result.failure(Exception("Ce défi est déjà dans vos favoris"))
            }
            
            // 🔑 CRÉER DONNÉES PERSONNELLES (comme iOS) - Juste userId, pas partnerIds !
            val challengeData = mapOf(
                "challengeKey" to challenge.challengeKey,
                "challengeDay" to challenge.challengeDay,
                "challengeText" to challengeText,
                "emoji" to getChallengeEmoji(challenge.challengeDay),
                "userId" to userId,
                "userName" to userName,
                "savedAt" to Timestamp.now(),
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
            
            // Sauvegarder dans Firestore avec structure iOS
            val documentRef = firestore.collection(COLLECTION_SAVED_CHALLENGES).document()
            documentRef.set(challengeData).await()
            
            Log.d(TAG, "✅ Défi sauvegardé avec succès: ${documentRef.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde défi: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 🗑️ Supprimer un défi sauvegardé
     */
    suspend fun removeSavedChallenge(challengeId: String, userId: String): Result<Unit> {
        return try {
            Log.d(TAG, "🗑️ Suppression défi sauvegardé: $challengeId par $userId")
            
            // Trouver le document à supprimer
            val challenge = _savedChallenges.value.find { it.id == challengeId }
            if (challenge == null) {
                return Result.failure(Exception("Défi sauvegardé introuvable"))
            }
            
            // Vérifier les permissions (système personnel)
            if (challenge.userId != userId) {
                return Result.failure(Exception("Vous ne pouvez supprimer que vos propres défis sauvegardés"))
            }
            
            // Supprimer de Firestore
            firestore.collection(COLLECTION_SAVED_CHALLENGES)
                .document(challengeId)
                .delete()
                .await()
            
            Log.d(TAG, "✅ Défi sauvegardé supprimé avec succès")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur suppression défi sauvegardé: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 🔍 Vérifier si un défi est déjà sauvegardé (système personnel iOS)
     */
    fun isChallengeAlreadySaved(challengeKey: String, userId: String): Boolean {
        return _savedChallenges.value.any { 
            it.challengeKey == challengeKey && it.userId == userId
        }
    }
    
    /**
     * 🔄 Synchronisation optionnelle (les défis sauvegardés sont principalement locaux)
     * Identique iOS : pas de synchronisation automatique, juste bookmarks personnels
     */
    suspend fun syncPartnerSavedChallenges(): Result<Unit> {
        return try {
            Log.d(TAG, "🔄 Les défis sauvegardés sont des bookmarks locaux")
            Log.d(TAG, "✅ Pas de synchronisation nécessaire (comme iOS)")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 📊 Obtenir le nombre total de défis sauvegardés
     */
    fun getSavedChallengesCount(): Int {
        return _savedChallenges.value.size
    }
    
    /**
     * 🎨 Emoji selon le jour du défi
     */
    private fun getChallengeEmoji(challengeDay: Int): String {
        return when (challengeDay % 10) {
            1 -> "💌" // Messages, communication
            2 -> "🍳" // Cuisine, activités domestiques
            3 -> "🎁" // Surprises, sorties
            4 -> "💕" // Amour, affection
            5 -> "🌟" // Expériences, découvertes
            6 -> "🎯" // Objectifs, défis
            7 -> "🏡" // Maison, cocooning
            8 -> "🌈" // Créativité, couleurs
            9 -> "⭐" // Excellence, réussite
            0 -> "🎪" // Fun, divertissement
            else -> "🎯" // Default
        }
    }
    
    /**
     * 🔧 Nettoyage ressources
     */
    fun cleanup() {
        Log.d(TAG, "🔧 Nettoyage SavedChallengesRepository")
        
        savedChallengesListener?.remove()
        savedChallengesListener = null
        
        _savedChallenges.value = emptyList()
        _errorMessage.value = null
        _isLoading.value = false
    }
    
    /**
     * 🐛 Debug - Afficher informations défis sauvegardés
     */
    fun debugSavedChallenges() {
        val challenges = _savedChallenges.value
        Log.d(TAG, "📊 DEBUG - ${challenges.size} défis sauvegardés:")
        challenges.forEach { challenge ->
            Log.d(TAG, "  - ${challenge.challengeKey} (Jour ${challenge.challengeDay}) par ${challenge.userName}")
        }
    }
}
