package com.love2loveapp.services.journal

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.JournalEntry
import com.love2loveapp.models.JournalLocation
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
 * 📔 JournalRepository - Service Central Journal
 * Équivalent iOS JournalService
 * 
 * Gère les opérations CRUD sur les événements journal avec :
 * - Real-time synchronisation Firebase
 * - Upload/suppression images Firebase Storage
 * - Partage automatique entre partenaires
 * - Vérification des autorisations (seul auteur peut modifier/supprimer)
 */
class JournalRepository private constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "JournalRepository"
        private const val COLLECTION_JOURNAL = "journalEntries"
        private const val STORAGE_PATH_JOURNAL = "journal_images" // ✅ Identique iOS
        
        @Volatile
        private var INSTANCE: JournalRepository? = null
        
        fun getInstance(context: Context): JournalRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JournalRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 📊 ÉTATS OBSERVABLES
    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // 🔄 LISTENERS FIRESTORE
    private var entriesListener: ListenerRegistration? = null
    
    /**
     * 🚀 Initialisation pour un utilisateur (équivalent iOS setupListener)
     */
    fun initializeForUser(userId: String) {
        Log.d(TAG, "🚀 Initialisation JournalRepository pour utilisateur: $userId")
        
        repositoryScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                setupRealtimeListener(userId)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur initialisation JournalRepository: ${e.message}")
                _errorMessage.value = "Erreur lors du chargement du journal"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 👂 Configuration listener temps réel Firestore (équivalent iOS setupListener)
     */
    private fun setupRealtimeListener(userId: String) {
        Log.d(TAG, "👂 Configuration listener journal pour: $userId")
        
        // Stopper l'ancien listener
        entriesListener?.remove()
        
        // 🔑 LISTENER TEMPS RÉEL AVEC FILTRE PARTENAIRE (identique iOS)
        entriesListener = firestore.collection(COLLECTION_JOURNAL)
            .whereArrayContains("partnerIds", userId)
            .orderBy("eventDate", Query.Direction.DESCENDING) // Trier par date événement décroissante
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener journal: ${error.message}")
                    _errorMessage.value = "Erreur synchronisation journal"
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val entries = snapshot.documents.mapNotNull { doc ->
                        JournalEntry.fromFirestore(doc)
                    }
                    
                    Log.d(TAG, "📥 Reçu ${entries.size} entrées journal depuis Firestore")
                    
                    // 🔑 REMPLACER ET TRIER PAR DATE ÉVÉNEMENT (identique iOS)
                    _entries.value = entries.sortedByDescending { it.eventDate }
                    _errorMessage.value = null
                }
            }
    }
    
    /**
     * ➕ Créer une nouvelle entrée journal (équivalent iOS createEntry)
     */
    suspend fun createEntry(
        title: String,
        description: String,
        eventDate: Date,
        imageUri: Uri? = null,
        location: JournalLocation? = null
    ): Result<Unit> {
        return try {
            Log.d(TAG, "➕ Création entrée journal: $title")
            
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("Utilisateur non connecté"))
            }
            
            val appUser = AppDelegate.appState.currentUser.value
            if (appUser == null) {
                return Result.failure(Exception("Données utilisateur non disponibles"))
            }
            
            _isLoading.value = true
            _errorMessage.value = null
            
            var imageURL: String? = null
            
            // 🔑 UPLOAD IMAGE SI PRÉSENTE (identique iOS)
            if (imageUri != null) {
                imageURL = uploadImage(imageUri).getOrThrow()
                Log.d(TAG, "📷 Image uploadée: $imageURL")
            }
            
            // 🔑 DÉTERMINER PARTENAIRES POUR PARTAGE (identique iOS)
            val partnerIds = mutableListOf(currentUser.uid) // Toujours inclure l'auteur
            appUser.partnerId?.let { partnerId ->
                partnerIds.add(partnerId)
                Log.d(TAG, "👥 Partage avec partenaire: [PARTNER_ID_MASKED]")
            }
            
            val entry = JournalEntry(
                title = title.trim(),
                description = description.trim(),
                eventDate = eventDate,
                authorId = currentUser.uid,
                authorName = appUser.name,
                imageURL = imageURL,
                partnerIds = partnerIds,      // 🔑 PARTAGE AUTOMATIQUE
                location = location           // 🔑 GPS OPTIONAL
            )
            
            // 🔑 SAUVEGARDER FIRESTORE (identique iOS)
            firestore.collection(COLLECTION_JOURNAL)
                .document(entry.id)
                .set(entry.toFirestore())
                .await()
            
            Log.d(TAG, "✅ Entrée journal créée avec succès: ${entry.id}")
            
            _isLoading.value = false
            Result.success(Unit)
            
        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "❌ Erreur création entrée journal: ${e.message}")
            _errorMessage.value = "Erreur lors de la création de l'entrée"
            Result.failure(e)
        }
    }
    
    /**
     * ✏️ Mettre à jour une entrée journal (équivalent iOS updateEntry)
     */
    suspend fun updateEntry(entry: JournalEntry): Result<Unit> {
        return try {
            Log.d(TAG, "✏️ Mise à jour entrée journal: ${entry.id}")
            
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("Utilisateur non connecté"))
            }
            
            // 🔑 VÉRIFICATION AUTORISATION (SEUL AUTEUR PEUT MODIFIER) - identique iOS
            if (currentUser.uid != entry.authorId) {
                return Result.failure(Exception("Seul l'auteur peut modifier cette entrée"))
            }
            
            val updatedEntry = entry.withUpdatedTimestamp()
            
            firestore.collection(COLLECTION_JOURNAL)
                .document(entry.id)
                .set(updatedEntry.toFirestore())
                .await()
            
            Log.d(TAG, "✅ Entrée journal mise à jour avec succès")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur mise à jour entrée: ${e.message}")
            _errorMessage.value = "Erreur lors de la mise à jour"
            Result.failure(e)
        }
    }
    
    /**
     * 🗑️ Supprimer une entrée journal (équivalent iOS deleteEntry)
     */
    suspend fun deleteEntry(entry: JournalEntry): Result<Unit> {
        return try {
            Log.d(TAG, "🗑️ === DÉBUT SUPPRESSION ENTRÉE ===")
            Log.d(TAG, "🗑️ Suppression entrée journal: ${entry.id}")
            
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("Utilisateur non connecté"))
            }
            
            // 🔑 SEUL L'AUTEUR PEUT SUPPRIMER (identique iOS)
            if (currentUser.uid != entry.authorId) {
                return Result.failure(Exception("Seul l'auteur peut supprimer cette entrée"))
            }
            
            // 🔑 SUPPRIMER IMAGE DU STORAGE SI PRÉSENTE (identique iOS)
            if (entry.imageURL?.isNotEmpty() == true) {
                try {
                    deleteImage(entry.imageURL!!).getOrThrow()
                    Log.d(TAG, "✅ Image supprimée avec succès")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erreur suppression image (continuons): ${e.message}")
                    // On continue même si l'image ne peut pas être supprimée
                }
            }
            
            // 🔑 SUPPRIMER ENTRÉE FIRESTORE (identique iOS)
            firestore.collection(COLLECTION_JOURNAL)
                .document(entry.id)
                .delete()
                .await()
            
            Log.d(TAG, "✅ Entrée Firestore supprimée avec succès")
            Log.d(TAG, "🗑️ === FIN SUPPRESSION ENTRÉE (SUCCÈS) ===")
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur suppression entrée: ${e.message}")
            Log.e(TAG, "🗑️ === FIN SUPPRESSION ENTRÉE (ÉCHEC) ===")
            _errorMessage.value = "Erreur lors de la suppression"
            Result.failure(e)
        }
    }
    
    /**
     * 📷 Upload image vers Firebase Storage (identique iOS)
     */
    private suspend fun uploadImage(imageUri: Uri): Result<String> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("Utilisateur non connecté"))
            }
            
            // ✅ Chemin structuré identique iOS: journal_images/{userId}/{fileName}
            val fileName = "${UUID.randomUUID()}.jpg"
            val imagePath = "$STORAGE_PATH_JOURNAL/${currentUser.uid}/$fileName"
            val imageRef = storage.reference.child(imagePath)
            
            Log.d(TAG, "📷 Upload image vers: $imagePath")
            
            // ✅ Métadonnées comme iOS
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()
            
            val uploadTask = imageRef.putFile(imageUri, metadata).await()
            val downloadUrl = imageRef.downloadUrl.await()
            
            Log.d(TAG, "✅ Image uploadée avec succès: $downloadUrl")
            Result.success(downloadUrl.toString())
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur upload image: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 🗑️ Supprimer image de Firebase Storage
     */
    private suspend fun deleteImage(imageURL: String): Result<Unit> {
        return try {
            val imageRef = storage.getReferenceFromUrl(imageURL)
            imageRef.delete().await()
            
            Log.d(TAG, "✅ Image supprimée du Storage: $imageURL")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur suppression image: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 📊 Obtenir le nombre d'entrées de l'utilisateur (pour freemium)
     */
    fun getUserEntriesCount(userId: String): Int {
        return _entries.value.count { it.authorId == userId }
    }
    
    /**
     * 📍 Obtenir les entrées avec localisation (pour carte)
     */
    fun getEntriesWithLocation(): List<JournalEntry> {
        return _entries.value.filter { it.hasLocation }
    }
    
    /**
     * 🔧 Nettoyage ressources
     */
    fun cleanup() {
        Log.d(TAG, "🔧 Nettoyage JournalRepository")
        
        entriesListener?.remove()
        entriesListener = null
        
        _entries.value = emptyList()
        _errorMessage.value = null
        _isLoading.value = false
    }
    
    /**
     * 🐛 Debug - Afficher informations journal
     */
    fun debugJournalEntries() {
        val entries = _entries.value
        Log.d(TAG, "📊 DEBUG - ${entries.size} entrées journal:")
        entries.forEach { entry ->
            Log.d(TAG, "  - ${entry.title} par ${entry.authorName} le ${entry.formattedEventDate}")
        }
    }
}
