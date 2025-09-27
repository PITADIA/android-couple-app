package com.love2loveapp.services.favorites

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.love2loveapp.models.*
import com.love2loveapp.services.QuestionDataManager
import com.love2loveapp.services.Question
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * 🔥 FavoritesRepository - Service Principal Favoris Android
 * Équivalent iOS FavoritesService.swift
 * 
 * Gère la synchronisation temps réel des favoris partagés entre partenaires
 * Architecture: Repository pattern avec StateFlow reactive
 */
class FavoritesRepository private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "FavoritesRepository"
        private const val COLLECTION_FAVORITES = "favoriteQuestions"
        
        @Volatile
        private var instance: FavoritesRepository? = null
        
        fun getInstance(context: Context): FavoritesRepository {
            return instance ?: synchronized(this) {
                instance ?: FavoritesRepository(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }

    // Services Firebase
    private val firestore = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // 🔑 ÉTATS OBSERVABLES - Équivalent iOS @Published
    private val _favoriteQuestions = MutableStateFlow<List<FavoriteQuestion>>(emptyList())
    val favoriteQuestions: StateFlow<List<FavoriteQuestion>> = _favoriteQuestions.asStateFlow()

    private val _sharedFavoriteQuestions = MutableStateFlow<List<SharedFavoriteQuestion>>(emptyList())
    val sharedFavoriteQuestions: StateFlow<List<SharedFavoriteQuestion>> = _sharedFavoriteQuestions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Listener Firestore
    private var favoritesListener: ListenerRegistration? = null
    
    // Données utilisateur
    private var currentUserId: String? = null
    private var userName: String? = null

    /**
     * 🔧 Initialisation avec données utilisateur
     * Équivalent iOS init(userId: String?, userName: String?)
     */
    fun initialize(userId: String?, userName: String?) {
        Log.d(TAG, "🔧 Initialisation FavoritesRepository")
        this.currentUserId = userId
        this.userName = userName
        
        if (userId != null) {
            setupFirestoreListener(userId)
        } else {
            Log.w(TAG, "⚠️ Pas d'utilisateur - listener non configuré")
        }
    }
    
    /**
     * 🔥 Configuration Listener Firestore Temps Réel
     * Équivalent iOS setupFirestoreListener()
     */
    private fun setupFirestoreListener(userId: String) {
        // Arrêter l'ancien listener
        favoritesListener?.remove()
        
        Log.d(TAG, "🔥 Configuration listener Firestore pour: $userId")
        
        // 🔑 LISTENER TEMPS RÉEL - arrayContains sur partnerIds
        favoritesListener = firestore.collection(COLLECTION_FAVORITES)
            .whereArrayContains("partnerIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener favoris: ${error.message}")
                    _errorMessage.value = "Erreur synchronisation: ${error.message}"
                    return@addSnapshotListener
                }

                Log.d(TAG, "✅ Mise à jour favoris reçue: ${snapshot?.documents?.size} documents")
                handleFirestoreUpdate(snapshot?.documents)
            }
    }
    
    /**
     * 📥 Gestion des mises à jour Firestore
     * Équivalent iOS handleFirestoreUpdate(snapshot: QuerySnapshot?)
     */
    private fun handleFirestoreUpdate(documents: List<com.google.firebase.firestore.DocumentSnapshot>?) {
        if (documents == null) {
            Log.d(TAG, "📥 Pas de documents dans la mise à jour")
            return
        }

        val updatedSharedFavorites = documents.mapNotNull { document ->
            try {
                val shared = SharedFavoriteQuestion.fromFirestore(document)
                Log.d(TAG, "📄 Document: ${shared?.questionText?.take(20)}... (partnerIds: ${shared?.partnerIds?.size})")
                shared
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur parsing document ${document.id}: ${e.message}")
                null
            }
        }

        Log.d(TAG, "🔄 ${updatedSharedFavorites.size} favoris partagés UNIQUES mis à jour (pas de doublons)")
        Log.d(TAG, "🔑 Questions reçues: ${updatedSharedFavorites.map { it.questionText.take(15) }}")
        
        // Mettre à jour les StateFlow sur le thread principal
        _sharedFavoriteQuestions.value = updatedSharedFavorites
        
        // Mettre à jour aussi la liste combinée
        updateCombinedFavorites()
    }
    
    /**
     * 🔄 Mise à jour de la liste des favoris depuis Firestore uniquement
     * Équivalent iOS getAllFavorites() -> [FavoriteQuestion]
     * 🔑 PAS de favoris locaux - tout passe par les SharedFavoriteQuestion partagés
     */
    private fun updateCombinedFavorites() {
        // 🔑 SEULS les favoris partagés Firestore comptent (comme iOS)
        val favoritesList = _sharedFavoriteQuestions.value
            .map { it.toLocalFavorite() }
            .sortedByDescending { it.dateAdded }
        
        _favoriteQuestions.value = favoritesList
        
        Log.d(TAG, "🔄 Favoris mis à jour depuis Firestore uniquement: ${favoritesList.size} favoris")
        
        // Debug: Vérifier qu'il n'y a pas de doublons
        debugFavoritesUnicity()
    }

    /**
     * ❤️ Ajouter une question aux favoris avec partage automatique
     * Équivalent iOS addFavorite(question: Question, category: QuestionCategory)
     */
    suspend fun addFavorite(
        question: Question,
        questionText: String, // Texte déjà résolu depuis question.getText(context) 
        category: QuestionCategory,
        partnerId: String? = null
    ): Result<Unit> {
        val userId = currentUserId
        val userDisplayName = userName
        
        if (userId == null || userDisplayName == null) {
            Log.e(TAG, "❌ Données utilisateur manquantes")
            return Result.failure(Exception("Utilisateur non connecté"))
        }

        Log.d(TAG, "❤️ Ajout favori: ${questionText.take(50)}...")
        _isLoading.value = true

        return try {
            // 🔑 CONSTRUIRE LES PARTNER IDS - Toujours inclure l'auteur
            val partnerIds = mutableListOf<String>().apply {
                add(userId) // Auteur
                partnerId?.let { add(it) } // Partenaire si connecté
            }

            Log.d(TAG, "🔥 partnerIds construits: [PARTNER_IDS_MASKED]")

            // 🔑 CRÉER LE FAVORI PARTAGÉ
            val sharedFavorite = SharedFavoriteQuestion(
                questionId = question.id,
                questionText = questionText,
                categoryTitle = category.id, // TODO: Récupérer titre localisé depuis resources
                emoji = category.emoji,
                authorId = userId,
                authorName = userDisplayName,
                partnerIds = partnerIds
            )

            // 🔑 SAUVEGARDER DANS FIRESTORE
            firestore.collection(COLLECTION_FAVORITES)
                .document(sharedFavorite.id)
                .set(sharedFavorite.toFirestore())
                .await()

            Log.d(TAG, "✅ UNIQUE SharedFavorite sauvegardé: ${sharedFavorite.id}")
            Log.d(TAG, "✅ Question: ${sharedFavorite.questionText.take(30)}...")
            Log.d(TAG, "✅ Author ID: ${sharedFavorite.authorId}")
            Log.d(TAG, "✅ Partner IDs: ${sharedFavorite.partnerIds}")
            Log.d(TAG, "🔑 Ce document sera visible par les ${sharedFavorite.partnerIds.size} partenaires")

            _isLoading.value = false
            _errorMessage.value = null
            
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ajout favori: ${e.message}")
            _isLoading.value = false
            _errorMessage.value = "Erreur ajout favori: ${e.message}"
            Result.failure(e)
        }
    }

    /**
     * 🗑️ Supprimer un favori avec contrôle d'autorisation
     * Équivalent iOS removeFavorite(questionId: String)
     */
    suspend fun removeFavorite(questionId: String): Result<Unit> {
        val userId = currentUserId
        if (userId == null) {
            Log.e(TAG, "❌ Pas d'utilisateur connecté pour suppression")
            return Result.failure(Exception("Utilisateur non connecté"))
        }

        Log.d(TAG, "🗑️ Suppression favori: $questionId")
        _isLoading.value = true

        return try {
            // 🔑 TROUVER ET VÉRIFIER PERMISSIONS
            val sharedFavorite = _sharedFavoriteQuestions.value.find { it.questionId == questionId }
            if (sharedFavorite != null) {
                Log.d(TAG, "🔥 Favori trouvé dans Firestore")

                val isAuthor = userId == sharedFavorite.authorId
                val isInPartnerIds = sharedFavorite.partnerIds.contains(userId)
                val canDelete = isAuthor || isInPartnerIds

                Log.d(TAG, "🔒 Est auteur: $isAuthor")
                Log.d(TAG, "🔒 Dans partnerIds: [PARTNER_STATUS_MASKED]")
                Log.d(TAG, "🔒 Peut supprimer: $canDelete")

                if (canDelete) {
                    // 🔑 SUPPRESSION FIRESTORE
                    firestore.collection(COLLECTION_FAVORITES)
                        .document(sharedFavorite.id)
                        .delete()
                        .await()

                    Log.d(TAG, "✅ Favori partagé supprimé")
                } else {
                    Log.w(TAG, "❌ Pas d'autorisation de suppression")
                    return Result.failure(Exception("Pas d'autorisation"))
                }
            } else {
                Log.w(TAG, "⚠️ Favori non trouvé dans partagés")
                return Result.failure(Exception("Favori non trouvé"))
            }

            _isLoading.value = false
            _errorMessage.value = null
            
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur suppression favori: ${e.message}")
            _isLoading.value = false
            _errorMessage.value = "Erreur suppression: ${e.message}"
            Result.failure(e)
        }
    }

    /**
     * ✅ Vérifier si un favori peut être supprimé
     * Équivalent iOS canDeleteFavorite(questionId: String) -> Bool
     */
    fun canDeleteFavorite(questionId: String): Boolean {
        val userId = currentUserId ?: return false

        val sharedFavorite = _sharedFavoriteQuestions.value.find { it.questionId == questionId }
        if (sharedFavorite != null) {
            val isAuthor = userId == sharedFavorite.authorId
            val isInPartnerIds = sharedFavorite.partnerIds.contains(userId)
            val canDelete = isAuthor || isInPartnerIds

            Log.d(TAG, "🔒 Peut supprimer $questionId: $canDelete")
            return canDelete
        }

        return false
    }

    /**
     * ✅ Vérifier si une question est en favoris
     */
    fun isFavorite(questionId: String): Boolean {
        return _sharedFavoriteQuestions.value.any { it.questionId == questionId }
    }

    /**
     * 🤝 Synchroniser les favoris avec un partenaire
     * Équivalent iOS syncPartnerFavorites(partnerId: String)
     */
    suspend fun syncPartnerFavorites(partnerId: String): Result<FavoritesSyncResult> {
        Log.d(TAG, "🤝 Synchronisation favoris avec partenaire")

        return try {
            val data = hashMapOf("partnerId" to partnerId)

            val result = functions.getHttpsCallable("syncPartnerFavorites")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val resultData = result.data as? Map<String, Any>
                ?: return Result.failure(Exception("Réponse invalide"))

            val success = resultData["success"] as? Boolean ?: false
            if (!success) {
                val message = resultData["message"] as? String ?: "Erreur inconnue"
                return Result.failure(Exception(message))
            }

            val syncResult = FavoritesSyncResult(
                success = true,
                updatedFavoritesCount = (resultData["updatedFavoritesCount"] as? Number)?.toInt() ?: 0,
                userFavoritesCount = (resultData["userFavoritesCount"] as? Number)?.toInt() ?: 0,
                partnerFavoritesCount = (resultData["partnerFavoritesCount"] as? Number)?.toInt() ?: 0,
                message = resultData["message"] as? String ?: ""
            )

            Log.d(TAG, "✅ Synchronisation réussie: ${syncResult.updatedFavoritesCount} favoris mis à jour")
            
            Result.success(syncResult)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur synchronisation: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 📊 Obtenir le nombre de favoris
     */
    fun getFavoritesCount(): Int {
        return _favoriteQuestions.value.size
    }

    /**
     * 📊 Obtenir les informations sur un favori partagé
     */
    fun getSharedFavoriteInfo(questionId: String): SharedFavoriteQuestion? {
        return _sharedFavoriteQuestions.value.find { it.questionId == questionId }
    }

    /**
     * 🔍 Debug - Vérifier l'unicité des favoris (pas de doublons)
     */
    fun debugFavoritesUnicity() {
        val sharedFavorites = _sharedFavoriteQuestions.value
        val localFavorites = _favoriteQuestions.value
        
        Log.d(TAG, "🔍 DEBUG UNICITÉ FAVORIS:")
        Log.d(TAG, "📊 ${sharedFavorites.size} favoris partagés Firestore")
        Log.d(TAG, "📊 ${localFavorites.size} favoris affichés")
        
        // Vérifier doublons par questionId
        val questionIds = localFavorites.map { it.questionId }
        val uniqueQuestionIds = questionIds.toSet()
        
        if (questionIds.size == uniqueQuestionIds.size) {
            Log.d(TAG, "✅ AUCUN doublon détecté - système correct")
        } else {
            Log.e(TAG, "❌ DOUBLONS DÉTECTÉS: ${questionIds.size} favoris mais seulement ${uniqueQuestionIds.size} uniques")
            
            // Identifier les doublons
            val duplicates = questionIds.groupingBy { it }.eachCount().filter { it.value > 1 }
            Log.e(TAG, "❌ Questions dupliquées: $duplicates")
        }
        
        // Afficher détails des favoris
        localFavorites.forEachIndexed { index, favorite ->
            Log.d(TAG, "📄 Favori $index: ${favorite.questionText.take(25)}... (ID: ${favorite.questionId})")
        }
    }

    /**
     * 🧹 Nettoyer les ressources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage FavoritesRepository")
        favoritesListener?.remove()
        favoritesListener = null
    }

    /**
     * 🔄 Réinitialiser les états d'erreur
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
