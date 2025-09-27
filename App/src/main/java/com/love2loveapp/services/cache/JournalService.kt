package com.love2loveapp.services.cache

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.love2loveapp.models.JournalEntry
import com.love2loveapp.models.JournalLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * 📔 JournalService Android - Cache Mémoire Temps Réel Sophistiqué
 * 
 * Architecture équivalente iOS JournalService:
 * - StateFlow → @Published iOS
 * - Firestore listeners → ListenerRegistration iOS
 * - Cache mémoire seulement → Array @Published iOS
 * - Temps réel partenaire → whereField partnerIds iOS
 * - Upload Firebase Storage → Firebase Storage iOS
 * - Pas de cache persistant → choix délibéré iOS
 * - Performance optimisée → listener filtré iOS
 * - Équivalent complet du JournalService iOS
 */
class JournalService private constructor(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
) {
    
    companion object {
        private const val TAG = "JournalService"
        private const val COLLECTION_JOURNAL_ENTRIES = "journalEntries"
        private const val STORAGE_PATH_JOURNAL = "journal_images" // ✅ Identique iOS
        
        @Volatile
        private var instance: JournalService? = null
        
        fun getInstance(context: Context): JournalService {
            return instance ?: synchronized(this) {
                instance ?: JournalService(
                    context.applicationContext,
                    FirebaseFirestore.getInstance(),
                    FirebaseAuth.getInstance(),
                    FirebaseStorage.getInstance()
                ).also { instance = it }
            }
        }
    }
    
    // Cache mémoire principal (équivalent @Published iOS)
    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Firestore listener temps réel (équivalent ListenerRegistration iOS)
    private var firestoreListener: ListenerRegistration? = null
    
    // Cache utilisateur actuel
    private var currentUserId: String? = null
    
    init {
        Log.d(TAG, "✅ JournalService initialisé avec cache mémoire temps réel")
        
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
            cleanupListener()
            _entries.value = emptyList()
            return
        }
        
        Log.d(TAG, "🔥 Configuration JournalService pour utilisateur: $userId")
        setupFirestoreListener(userId)
    }
    
    // =======================
    // FIRESTORE LISTENER TEMPS RÉEL (équivalent iOS)
    // =======================
    
    /**
     * Configuration listener Firestore temps réel
     * Équivalent de setupListener() iOS
     */
    private fun setupFirestoreListener(userId: String) {
        // Nettoyer ancien listener
        firestoreListener?.remove()
        
        Log.d(TAG, "🔥 Configuration listener Journal pour user: $userId")
        
        firestoreListener = firestore.collection(COLLECTION_JOURNAL_ENTRIES)
            .whereArrayContains("partnerIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener Journal: ${error.message}")
                    _errorMessage.value = "Erreur de synchronisation: ${error.message}"
                    return@addSnapshotListener
                }
                
                handleFirestoreUpdate(snapshot?.documents?.mapNotNull { doc ->
                    try {
                        JournalEntry.fromFirestore(doc)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur parsing entry: ${e.message}")
                        null
                    }
                } ?: emptyList())
            }
    }
    
    /**
     * Traite mise à jour Firestore temps réel
     * Équivalent de handleSnapshotUpdate(snapshot:) iOS
     */
    private fun handleFirestoreUpdate(newEntries: List<JournalEntry>) {
        Log.d(TAG, "📥 Mise à jour Journal Firestore: ${newEntries.size} entrées")
        
        // Trier par date événement (plus récent en premier)
        val sortedEntries = newEntries.sortedByDescending { it.eventDate }
        
        // Mettre à jour cache mémoire
        _entries.value = sortedEntries
        
        // Effacer erreurs précédentes
        _errorMessage.value = null
        
        Log.d(TAG, "🔥 ${sortedEntries.size} entrées Journal chargées en cache mémoire")
    }
    
    // =======================
    // CRÉATION ENTRÉES JOURNAL (équivalent iOS)
    // =======================
    
    /**
     * Crée une nouvelle entrée journal
     * Équivalent de createEntry() iOS
     */
    suspend fun createEntry(
        title: String,
        description: String,
        eventDate: Date,
        imageUri: Uri? = null,
        location: JournalLocation? = null
    ): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        
        return try {
            _isLoading.value = true
            _errorMessage.value = null
            
            Log.d(TAG, "📝 Création entrée Journal: '$title'")
            
            // 1. Upload image si fournie
            var imageUrl: String? = null
            if (imageUri != null) {
                Log.d(TAG, "📷 Upload image Journal...")
                imageUrl = uploadImage(imageUri)
                Log.d(TAG, "✅ Image uploadée: [URL_MASKED]")
            }
            
            // 2. Récupérer informations utilisateur
            val currentUser = auth.currentUser
            val userName = currentUser?.displayName ?: "Utilisateur"
            
            // 3. TODO: Récupérer partnerId depuis profil utilisateur
            val partnerIds = listOf(userId) // + partnerId quand disponible
            
            // 4. Créer entrée Journal
            val entry = JournalEntry(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                eventDate = eventDate,
                createdAt = Date(),
                updatedAt = Date(),
                authorId = userId,
                authorName = userName,
                imageURL = imageUrl,
                partnerIds = partnerIds,
                location = location
            )
            
            // 5. Sauvegarder dans Firestore
            Log.d(TAG, "🔥 Sauvegarde Firestore...")
            firestore.collection(COLLECTION_JOURNAL_ENTRIES)
                .document(entry.id)
                .set(entry.toFirestore())
                .await()
            
            Log.d(TAG, "✅ Entrée Journal créée avec succès: ${entry.id}")
            
            // Le listener temps réel mettra à jour automatiquement _entries
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur création entrée Journal: ${e.message}", e)
            _errorMessage.value = "Erreur lors de la sauvegarde: ${e.message}"
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Met à jour une entrée Journal existante
     * Équivalent de updateEntry() iOS
     */
    suspend fun updateEntry(
        entryId: String,
        title: String? = null,
        description: String? = null,
        eventDate: Date? = null,
        imageUri: Uri? = null,
        location: JournalLocation? = null
    ): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        
        return try {
            _isLoading.value = true
            _errorMessage.value = null
            
            Log.d(TAG, "✏️ Mise à jour entrée Journal: $entryId")
            
            val updates = mutableMapOf<String, Any>()
            updates["updatedAt"] = com.google.firebase.Timestamp(Date())
            
            // Mettre à jour champs fournis
            title?.let { updates["title"] = it.trim() }
            description?.let { updates["description"] = it.trim() }
            eventDate?.let { updates["eventDate"] = com.google.firebase.Timestamp(it) }
            location?.let { updates["location"] = it.toFirestore() }
            
            // Upload nouvelle image si fournie
            if (imageUri != null) {
                Log.d(TAG, "📷 Upload nouvelle image...")
                val imageUrl = uploadImage(imageUri)
                updates["imageURL"] = imageUrl
            }
            
            // Sauvegarder mises à jour
            firestore.collection(COLLECTION_JOURNAL_ENTRIES)
                .document(entryId)
                .update(updates)
                .await()
            
            Log.d(TAG, "✅ Entrée Journal mise à jour: $entryId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur mise à jour entrée: ${e.message}", e)
            _errorMessage.value = "Erreur lors de la mise à jour: ${e.message}"
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Supprime une entrée Journal
     * Équivalent de deleteEntry() iOS
     */
    suspend fun deleteEntry(entryId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        
        return try {
            _isLoading.value = true
            _errorMessage.value = null
            
            Log.d(TAG, "🗑️ Suppression entrée Journal: $entryId")
            
            // Vérifier que l'utilisateur est l'auteur
            val entry = _entries.value.find { it.id == entryId }
            if (entry == null) {
                return Result.failure(Exception("Entrée non trouvée"))
            }
            
            if (entry.authorId != userId) {
                return Result.failure(Exception("Permission refusée - vous n'êtes pas l'auteur"))
            }
            
            // Supprimer de Firestore
            firestore.collection(COLLECTION_JOURNAL_ENTRIES)
                .document(entryId)
                .delete()
                .await()
            
            // Supprimer image associée si existante
            entry.imageURL?.let { imageUrl ->
                try {
                    val imageRef = storage.getReferenceFromUrl(imageUrl)
                    imageRef.delete().await()
                    Log.d(TAG, "🗑️ Image supprimée du storage")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Erreur suppression image: ${e.message}")
                    // Ne pas échouer pour ça
                }
            }
            
            Log.d(TAG, "✅ Entrée Journal supprimée: $entryId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur suppression entrée: ${e.message}", e)
            _errorMessage.value = "Erreur lors de la suppression: ${e.message}"
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    // =======================
    // UPLOAD IMAGES (équivalent iOS)
    // =======================
    
    /**
     * Upload image vers Firebase Storage (identique iOS)
     * Équivalent de uploadImage() iOS
     */
    private suspend fun uploadImage(imageUri: Uri): String {
        val currentUser = auth.getCurrentUser()
        if (currentUser == null) {
            throw Exception("Utilisateur non connecté")
        }
        
        // ✅ Chemin structuré identique iOS: journal_images/{userId}/{fileName}
        val fileName = "${UUID.randomUUID()}.jpg"
        val imagePath = "$STORAGE_PATH_JOURNAL/${currentUser.uid}/$fileName"
        val storageRef = storage.reference.child(imagePath)
        
        // ✅ Métadonnées comme iOS
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()
        
        storageRef.putFile(imageUri, metadata).await()
        
        // Récupérer URL de téléchargement
        return storageRef.downloadUrl.await().toString()
    }
    
    // =======================
    // MÉTHODES UTILITAIRES (équivalent iOS)
    // =======================
    
    /**
     * Récupération manuelle des entrées (fallback)
     * Équivalent de refreshEntries() iOS
     */
    suspend fun refreshEntries(): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(Exception("Utilisateur non connecté"))
        
        return try {
            _isLoading.value = true
            _errorMessage.value = null
            
            Log.d(TAG, "🔄 Rafraîchissement manuel entrées Journal")
            
            val snapshot = firestore.collection(COLLECTION_JOURNAL_ENTRIES)
                .whereArrayContains("partnerIds", userId)
                .get()
                .await()
            
            val entries = snapshot.documents.mapNotNull { doc ->
                try {
                    JournalEntry.fromFirestore(doc)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur parsing entrée: ${e.message}")
                    null
                }
            }
            
            handleFirestoreUpdate(entries)
            
            Log.d(TAG, "✅ Rafraîchissement terminé: ${entries.size} entrées")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur rafraîchissement: ${e.message}", e)
            _errorMessage.value = "Erreur lors du rafraîchissement: ${e.message}"
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Filtre entrées par mois
     * Équivalent de getEntriesForMonth() iOS
     */
    fun getEntriesForMonth(year: Int, month: Int): List<JournalEntry> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1, 0, 0, 0) // Month is 0-based
        val startOfMonth = calendar.time
        
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endOfMonth = calendar.time
        
        return _entries.value.filter { entry ->
            entry.eventDate >= startOfMonth && entry.eventDate <= endOfMonth
        }
    }
    
    /**
     * Compte nombre d'entrées
     */
    fun getEntriesCount(): Int = _entries.value.size
    
    /**
     * Récupère entrée par ID
     */
    fun getEntryById(entryId: String): JournalEntry? {
        return _entries.value.find { it.id == entryId }
    }
    
    /**
     * Efface message d'erreur
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    // =======================
    // GESTION RESSOURCES (équivalent iOS)
    // =======================
    
    /**
     * Nettoie le listener Firestore
     */
    private fun cleanupListener() {
        firestoreListener?.remove()
        firestoreListener = null
        Log.d(TAG, "🧹 Listener Firestore nettoyé")
    }
    
    /**
     * Informations de debug
     */
    fun getDebugInfo(): String {
        val userId = currentUserId ?: return "❌ Aucun utilisateur connecté"
        val entriesCount = _entries.value.size
        val listenerActive = firestoreListener != null
        val isLoading = _isLoading.value
        val error = _errorMessage.value
        
        return """
            📊 DEBUG JournalService:
            - Utilisateur: $userId
            - Entrées en cache mémoire: $entriesCount
            - Listener Firestore actif: $listenerActive
            - En chargement: $isLoading
            - Erreur: ${error ?: "Aucune"}
            - Collection: $COLLECTION_JOURNAL_ENTRIES
        """.trimIndent()
    }
    
    /**
     * Nettoyage ressources (destroy app)
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage JournalService")
        cleanupListener()
        _entries.value = emptyList()
    }
}
