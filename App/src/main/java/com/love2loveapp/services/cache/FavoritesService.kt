package com.love2loveapp.services.cache

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.love2loveapp.services.cache.dao.FavoritesDao
import com.love2loveapp.services.cache.entities.FavoriteQuestionEntity
import com.love2loveapp.models.FavoriteQuestion
import com.love2loveapp.models.SharedFavoriteQuestion
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * ⭐ FavoritesService Android - Cache Hybride Temps Réel Sophistiqué
 * 
 * Architecture équivalente iOS FavoritesService:
 * - Room Database → Realm local iOS
 * - Firestore listeners → Firestore listeners iOS
 * - LiveData → @Published iOS
 * - Cache hybride local/partagé → même logique iOS  
 * - Synchronisation bidirectionnelle → syncToLocalCache() iOS
 * - Temps réel partenaire → ListenerRegistration iOS
 * - Équivalent complet du FavoritesService iOS
 */
class FavoritesService private constructor(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    companion object {
        private const val TAG = "FavoritesService"
        private const val COLLECTION_FAVORITES = "favoriteQuestions"
        
        @Volatile
        private var instance: FavoritesService? = null
        
        fun getInstance(context: Context): FavoritesService {
            return instance ?: synchronized(this) {
                instance ?: FavoritesService(
                    context.applicationContext,
                    FirebaseFirestore.getInstance(),
                    FirebaseAuth.getInstance()
                ).also { instance = it }
            }
        }
    }
    
    // Base de données locale (équivalent Realm iOS)
    private val database = CacheDatabase.getDatabase(context)
    private val favoritesDao: FavoritesDao = database.favoritesDao()
    
    // LiveData pour UI réactive (équivalent @Published iOS)
    private val _localFavorites = MutableLiveData<List<FavoriteQuestion>>()
    val localFavorites: LiveData<List<FavoriteQuestion>> = _localFavorites
    
    private val _sharedFavorites = MutableLiveData<List<SharedFavoriteQuestion>>()
    val sharedFavorites: LiveData<List<SharedFavoriteQuestion>> = _sharedFavorites
    
    // Firestore listener temps réel (équivalent ListenerRegistration iOS)
    private var firestoreListener: ListenerRegistration? = null
    
    // Scope coroutines pour synchronisation
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Cache de l'utilisateur actuel
    private var currentUserId: String? = null
    
    init {
        Log.d(TAG, "✅ FavoritesService initialisé")
        
        // Observer changements utilisateur
        auth.addAuthStateListener { firebaseAuth ->
            val newUserId = firebaseAuth.currentUser?.uid
            if (newUserId != currentUserId) {
                currentUserId = newUserId
                setupForUser(newUserId)
            }
        }
        
        // Setup initial si utilisateur connecté
        currentUserId = auth.currentUser?.uid
        setupForUser(currentUserId)
    }
    
    /**
     * Configuration pour utilisateur spécifique
     * Équivalent de l'initialisation iOS avec userId
     */
    private fun setupForUser(userId: String?) {
        if (userId == null) {
            Log.d(TAG, "🔐 Aucun utilisateur connecté - arrêt listeners")
            cleanupListeners()
            return
        }
        
        Log.d(TAG, "🔥 Configuration FavoritesService pour utilisateur: $userId")
        
        // Setup Firestore listener temps réel
        setupFirestoreListener(userId)
        
        // Charger favoris locaux
        loadLocalFavorites(userId)
    }
    
    // =======================
    // FIRESTORE TEMPS RÉEL (équivalent iOS)
    // =======================
    
    /**
     * Configuration listener Firestore temps réel
     * Équivalent de setupFirestoreListener() iOS
     */
    private fun setupFirestoreListener(userId: String) {
        // Nettoyer ancien listener
        firestoreListener?.remove()
        
        Log.d(TAG, "🔥 Configuration listener Firestore pour user: $userId")
        
        firestoreListener = firestore.collection(COLLECTION_FAVORITES)
            .whereArrayContains("partnerIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener Firestore: ${error.message}")
                    return@addSnapshotListener
                }
                
                handleFirestoreUpdate(snapshot?.documents?.mapNotNull { doc ->
                    try {
                        SharedFavoriteQuestion.fromFirestore(doc)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur parsing document: ${e.message}")
                        null
                    }
                } ?: emptyList())
            }
    }
    
    /**
     * Traite mise à jour Firestore temps réel
     * Équivalent de handleFirestoreUpdate(snapshot:) iOS  
     */
    private fun handleFirestoreUpdate(newSharedFavorites: List<SharedFavoriteQuestion>) {
        Log.d(TAG, "📥 Mise à jour Firestore: ${newSharedFavorites.size} favoris partagés")
        
        // Mettre à jour LiveData sur main thread
        _sharedFavorites.postValue(newSharedFavorites.sortedByDescending { it.dateAdded })
        
        // Synchroniser avec cache local en arrière-plan
        serviceScope.launch {
            syncToLocalCache(newSharedFavorites)
        }
    }
    
    // =======================
    // SYNCHRONISATION CACHE LOCAL (équivalent iOS)
    // =======================
    
    /**
     * Charge favoris locaux depuis Room
     * Équivalent de loadLocalFavorites() iOS
     */
    private fun loadLocalFavorites(userId: String) {
        // Observer favoris locaux avec LiveData
        val localLiveData = favoritesDao.getFavoritesLiveData(userId)
        localLiveData.observeForever { entities ->
            val favorites = entities.map { it.toFavoriteQuestion() }
            _localFavorites.postValue(favorites)
            Log.d(TAG, "📱 ${favorites.size} favoris locaux chargés")
        }
    }
    
    /**
     * Synchronise favoris Firestore vers cache local
     * Équivalent de syncToLocalCache() iOS
     */
    private suspend fun syncToLocalCache(sharedFavorites: List<SharedFavoriteQuestion>) {
        val userId = currentUserId ?: return
        
        try {
            Log.d(TAG, "🔄 Synchronisation cache local...")
            
            // Récupérer favoris locaux actuels
            val localFavorites = favoritesDao.getFavorites(userId)
            val localQuestionIds = localFavorites.map { it.questionId }.toSet()
            val sharedQuestionIds = sharedFavorites.map { it.questionId }.toSet()
            
            // Supprimer favoris locaux qui ne sont plus partagés
            val idsToRemove = localQuestionIds - sharedQuestionIds
            if (idsToRemove.isNotEmpty()) {
                Log.d(TAG, "🗑️ Suppression ${idsToRemove.size} favoris non partagés")
                favoritesDao.removeNonSharedFavorites(userId, sharedQuestionIds.toList())
            }
            
            // Ajouter nouveaux favoris partagés au cache local
            val idsToAdd = sharedQuestionIds - localQuestionIds
            if (idsToAdd.isNotEmpty()) {
                Log.d(TAG, "➕ Ajout ${idsToAdd.size} nouveaux favoris partagés")
                
                val newFavorites = sharedFavorites
                    .filter { idsToAdd.contains(it.questionId) }
                    .map { sharedFavorite ->
                        val localFavorite = sharedFavorite.toLocalFavorite()
                        FavoriteQuestionEntity.fromFavoriteQuestion(localFavorite, userId)
                    }
                
                favoritesDao.insertFavorites(newFavorites)
            }
            
            Log.d(TAG, "✅ Synchronisation cache terminée")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur synchronisation cache: ${e.message}", e)
        }
    }
    
    // =======================
    // API PUBLIQUE FAVORIS (équivalent iOS)
    // =======================
    
    /**
     * Ajoute une question aux favoris
     * Équivalent de addToFavorites() iOS
     */
    suspend fun addToFavorites(
        question: FavoriteQuestion,
        isShared: Boolean = false
    ): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        
        return try {
            Log.d(TAG, "⭐ Ajout aux favoris: ${question.questionId}")
            
            if (isShared) {
                // Ajouter aux favoris partagés Firestore
                addToSharedFavorites(question, userId)
            } else {
                // Ajouter au cache local uniquement
                val entity = FavoriteQuestionEntity.fromFavoriteQuestion(question, userId)
                favoritesDao.insertFavorite(entity)
                Log.d(TAG, "💾 Favori ajouté localement")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ajout favori: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Supprime une question des favoris
     * Équivalent de removeFromFavorites() iOS
     */
    suspend fun removeFromFavorites(questionId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        
        return try {
            Log.d(TAG, "🗑️ Suppression favori: $questionId")
            
            // Supprimer du cache local
            favoritesDao.removeFavorite(questionId, userId)
            
            // Supprimer des favoris partagés Firestore si existant
            removeFromSharedFavorites(questionId, userId)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur suppression favori: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Vérifie si une question est en favoris  
     * Équivalent de isFavorite() iOS
     */
    suspend fun isFavorite(questionId: String): Boolean {
        val userId = currentUserId ?: return false
        
        return try {
            favoritesDao.isFavorite(userId, questionId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification favori: ${e.message}", e)
            false
        }
    }
    
    /**
     * Combine favoris locaux et partagés
     * Équivalent de getAllFavorites() iOS
     */
    fun getAllFavorites(): LiveData<List<FavoriteQuestion>> {
        return MediatorLiveData<List<FavoriteQuestion>>().apply {
            addSource(localFavorites) { locals ->
                val shared = sharedFavorites.value ?: emptyList()
                postValue(combineWithSharedFavorites(locals, shared))
            }
            addSource(sharedFavorites) { shared ->
                val locals = localFavorites.value ?: emptyList()
                postValue(combineWithSharedFavorites(locals, shared))
            }
        }
    }
    
    /**
     * Fusion intelligente favoris locaux/partagés
     * Équivalent de combineWithSharedFavorites() iOS
     */
    private fun combineWithSharedFavorites(
        locals: List<FavoriteQuestion>,
        shared: List<SharedFavoriteQuestion>
    ): List<FavoriteQuestion> {
        val allFavorites = mutableListOf<FavoriteQuestion>()
        
        // Ajouter favoris locaux
        allFavorites.addAll(locals)
        
        // Ajouter favoris partagés qui ne sont pas déjà locaux
        val localQuestionIds = locals.map { it.questionId }.toSet()
        shared.forEach { sharedFavorite ->
            if (!localQuestionIds.contains(sharedFavorite.questionId)) {
                allFavorites.add(sharedFavorite.toLocalFavorite())
            }
        }
        
        return allFavorites.sortedByDescending { it.dateAdded }
    }
    
    // =======================
    // FIRESTORE OPERATIONS (privé)
    // =======================
    
    /**
     * Ajoute aux favoris partagés Firestore
     */
    private suspend fun addToSharedFavorites(question: FavoriteQuestion, userId: String) {
        try {
            val sharedFavorite = mapOf(
                "questionId" to question.questionId,
                "categoryTitle" to question.categoryTitle,
                "questionText" to question.questionText,
                "emoji" to question.emoji,
                "dateAdded" to com.google.firebase.Timestamp(question.dateAdded),
                "addedByUserId" to userId,
                "partnerIds" to listOf(userId), // TODO: ajouter partenaire
                "createdAt" to com.google.firebase.Timestamp(Date()),
                "updatedAt" to com.google.firebase.Timestamp(Date())
            )
            
            firestore.collection(COLLECTION_FAVORITES)
                .document(question.questionId + "_" + userId)
                .set(sharedFavorite)
            
            Log.d(TAG, "🔥 Favori ajouté à Firestore")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur ajout Firestore: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Supprime des favoris partagés Firestore
     */
    private suspend fun removeFromSharedFavorites(questionId: String, userId: String) {
        try {
            // Rechercher et supprimer documents contenant cette question + user
            val docs = firestore.collection(COLLECTION_FAVORITES)
                .whereEqualTo("questionId", questionId)
                .whereArrayContains("partnerIds", userId)
                .get()
                .await()
                
            for (doc in docs) {
                doc.reference.delete()
            }
            
            Log.d(TAG, "🔥 Favori supprimé de Firestore")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur suppression Firestore: ${e.message}", e)
            // Ne pas throw - suppression locale suffit
        }
    }
    
    // =======================
    // GESTION RESSOURCES (équivalent iOS)
    // =======================
    
    /**
     * Nettoie les listeners
     */
    private fun cleanupListeners() {
        firestoreListener?.remove()
        firestoreListener = null
        Log.d(TAG, "🧹 Listeners Firestore nettoyés")
    }
    
    /**
     * Supprime tous les favoris d'un utilisateur (déconnexion)
     * Équivalent de clearFavorites() iOS
     */
    suspend fun clearFavorites() {
        val userId = currentUserId ?: return
        
        try {
            favoritesDao.clearFavorites(userId)
            cleanupListeners()
            
            _localFavorites.postValue(emptyList())
            _sharedFavorites.postValue(emptyList())
            
            Log.d(TAG, "🗑️ Favoris utilisateur nettoyés")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage favoris: ${e.message}", e)
        }
    }
    
    /**
     * Informations de debug
     */
    suspend fun getDebugInfo(): String {
        val userId = currentUserId ?: return "❌ Aucun utilisateur connecté"
        
        return try {
            val localCount = favoritesDao.getFavoritesCount(userId)
            val sharedCount = _sharedFavorites.value?.size ?: 0
            val listenerActive = firestoreListener != null
            
            """
                📊 DEBUG FavoritesService:
                - Utilisateur: $userId
                - Favoris locaux: $localCount
                - Favoris partagés: $sharedCount  
                - Listener Firestore: $listenerActive
                - Collection: $COLLECTION_FAVORITES
            """.trimIndent()
        } catch (e: Exception) {
            "❌ Erreur debug: ${e.message}"
        }
    }
    
    /**
     * Nettoyage ressources (destroy app)
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage FavoritesService")
        cleanupListeners()
        serviceScope.cancel()
    }
}
