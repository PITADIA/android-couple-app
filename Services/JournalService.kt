package com.yourapp.journal

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * Interfaces d’injection (équivalents à AppState.freemiumManager & FirebaseService.shared.currentUser)
 */
interface FreemiumManager {
    fun canAddJournalEntry(currentEntriesCount: Int): Boolean
    fun getRemainingFreeJournalEntries(currentEntriesCount: Int): Int
    fun getMaxFreeJournalEntries(): Int
}

interface CurrentUserProvider {
    val currentUserId: String?
    val currentUserName: String?
    val partnerId: String?
}

/**
 * Erreurs fonctionnelles (liées à l’UX), mappées sur strings.xml
 */
sealed class JournalError(@StringRes val messageRes: Int) : Exception() {
    object UserNotAuthenticated : JournalError(R.string.user_not_authenticated)
    object NotAuthorized : JournalError(R.string.not_authorized_action)
    object ImageProcessingFailed : JournalError(R.string.image_processing_failed)
    object NetworkError : JournalError(R.string.network_error)
    object FreemiumLimitReached : JournalError(R.string.freemium_limit_reached)
    object FreemiumCheckFailed : JournalError(R.string.freemium_check_failed)
}

/**
 * Service principal – équivalent du JournalService Swift
 * - État observable via StateFlow (entries, isLoading, errorMessage).
 * - Listener Firestore temps réel sur les entrées où partnerIds contient l’utilisateur courant.
 */
class JournalService(
    private val context: Context,
    private val freemiumManager: FreemiumManager,
    private val currentUserProvider: CurrentUserProvider
) {

    private val db: FirebaseFirestore = Firebase.firestore
    private val storage = Firebase.storage
    private var listener: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        setupListener()
    }

    fun clear() {
        listener?.remove()
        listener = null
        scope.cancel()
    }

    // --- Helpers Freemium (comme les computed properties Swift) ---

    private fun currentUserEntriesCount(): Int {
        val uid = currentUserProvider.currentUserId ?: return 0
        return _entries.value.count { it.authorId == uid }
    }

    fun canAddEntry(): Boolean {
        return freemiumManager.canAddJournalEntry(currentEntriesCount())
    }

    fun getRemainingFreeEntries(): Int {
        return freemiumManager.getRemainingFreeJournalEntries(currentEntriesCount())
    }

    fun getMaxFreeEntries(): Int = freemiumManager.getMaxFreeJournalEntries()

    fun hasReachedFreeLimit(): Boolean = !canAddEntry()

    // --- Listener Firestore ---

    private fun setupListener() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            // pas connecté
            return
        }

        listener?.remove()
        listener = db.collection("journalEntries")
            .whereArrayContains("partnerIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // on se contente de logguer silencieusement
                    return@addSnapshotListener
                }
                handleSnapshotUpdate(snapshot)
            }
    }

    private fun handleSnapshotUpdate(snapshot: QuerySnapshot?) {
        val docs = snapshot?.documents ?: return
        val newEntries = docs.mapNotNull { JournalEntry.fromSnapshot(it) }
            .sortedByDescending { it.eventDate.time }

        scope.launch {
            _entries.emit(newEntries)
        }
    }

    // --- Rafraîchissement manuel ---

    suspend fun refreshEntries() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            val snap = db.collection("journalEntries")
                .whereArrayContains("partnerIds", uid)
                .get().await()

            val list = snap.documents.mapNotNull { JournalEntry.fromSnapshot(it) }
                .sortedByDescending { it.eventDate.time }

            _entries.emit(list)
        } catch (t: Throwable) {
            // log + pas d’exception UI ici
        }
    }

    // --- CRUD ---

    /**
     * Équivalent de createEntry Swift.
     * @param image Bitmap optionnel (Android) au lieu de UIImage.
     * @param location JournalLocation optionnel (mappé en Map<String,Any?> pour Firestore).
     */
    suspend fun createEntry(
        title: String,
        description: String,
        eventDate: Date,
        image: Bitmap? = null,
        location: JournalLocation? = null
    ) {
        val uid = Firebase.auth.currentUser?.uid
            ?: throw JournalError.UserNotAuthenticated

        // équivalent FirebaseService.shared.currentUser en Swift
        val authorName = currentUserProvider.currentUserName
        val partnerId = currentUserProvider.partnerId
        if (authorName == null) throw JournalError.UserNotAuthenticated

        // Freemium
        if (!canAddEntry()) throw JournalError.FreemiumLimitReached

        _isLoading.emit(true)
        _errorMessage.emit(null)

        try {
            val imageUrl = image?.let { uploadImage(it) } // peut retourner null si pas d’image

            // Toujours inclure l’auteur
            val partnerIds = buildList {
                add(uid)
                if (!partnerId.isNullOrBlank()) add(partnerId)
            }

            val entry = JournalEntry(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                eventDate = eventDate,
                authorId = uid,
                authorName = authorName,
                imageURL = imageUrl,
                partnerIds = partnerIds,
                location = location,
                createdAt = Date(),
                updatedAt = null
            )

            db.collection("journalEntries")
                .document(entry.id)
                .set(entry.toMap())
                .await()

            // forcer un refresh comme en Swift
            refreshEntries()

            _isLoading.emit(false)
        } catch (t: Throwable) {
            _isLoading.emit(false)
            // message UI localisé
            _errorMessage.emit(
                context.getString(
                    when (t) {
                        is JournalError -> t.messageRes
                        is CancellationException -> R.string.network_error
                        else -> R.string.network_error
                    }
                )
            )
            throw t
        }
    }

    suspend fun updateEntry(entry: JournalEntry) {
        val currentUid = Firebase.auth.currentUser?.uid
            ?: throw JournalError.UserNotAuthenticated

        if (currentUid != entry.authorId) throw JournalError.NotAuthorized

        val updated = entry.copy(updatedAt = Date())

        db.collection("journalEntries")
            .document(entry.id)
            .update(updated.toMap())
            .await()
    }

    suspend fun deleteEntry(entry: JournalEntry) {
        val currentUid = Firebase.auth.currentUser?.uid
            ?: throw JournalError.UserNotAuthenticated

        if (currentUid != entry.authorId) throw JournalError.NotAuthorized

        // supprimer d’abord l’image si présente (sans bloquer en cas d’erreur)
        val url = entry.imageURL
        if (!url.isNullOrBlank()) {
            try {
                deleteImage(url)
            } catch (_: Throwable) {
                // on continue quand même
            }
        }

        db.collection("journalEntries")
            .document(entry.id)
            .delete()
            .await()
    }

    // --- Images (Storage) ---

    private suspend fun uploadImage(bitmap: Bitmap): String {
        val uid = Firebase.auth.currentUser?.uid
            ?: throw JournalError.UserNotAuthenticated

        val baos = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        if (!ok) throw JournalError.ImageProcessingFailed

        val bytes = baos.toByteArray()
        val fileName = "${UUID.randomUUID()}.jpg"
        val path = "journal_images/$uid/$fileName"
        val ref = storage.reference.child(path)

        // upload
        ref.putBytes(bytes).await()

        // url publique (download URL)
        val download: Uri = ref.downloadUrl.await()
        return download.toString()
    }

    private suspend fun deleteImage(url: String) {
        try {
            val ref = storage.getReferenceFromUrl(url)
            ref.delete().await()
        } catch (t: Throwable) {
            if (t is StorageException && t.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                // déjà supprimé côté serveur
                return
            }
            // on ne propage pas pour ne pas bloquer la suppression de l’entrée
        }
    }

    // --- Helpers requêtes locales (équivalents Swift) ---

    fun getEntriesForDate(date: Date): List<JournalEntry> {
        val cal = Calendar.getInstance().apply { time = date }
        return _entries.value.filter { entry ->
            isSameDay(entry.eventDate, cal)
        }
    }

    fun getEntriesForMonth(date: Date): List<JournalEntry> {
        val cal = Calendar.getInstance().apply { time = date }
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        return _entries.value.filter { entry ->
            val c = Calendar.getInstance().apply { time = entry.eventDate }
            c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year
        }
    }

    fun hasEntriesForDate(date: Date): Boolean = getEntriesForDate(date).isNotEmpty()

    private fun isSameDay(date: Date, cal: Calendar): Boolean {
        val c = Calendar.getInstance().apply { time = date }
        return c.get(Calendar.ERA) == cal.get(Calendar.ERA) &&
                c.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                c.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }

    // --- Rafraîchir les entrées du partenaire (merge sans doublons) ---

    suspend fun refreshPartnerEntries() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            val snap = db.collection("journalEntries")
                .whereArrayContains("partnerIds", uid)
                .get().await()

            val partnerEntries = snap.documents.mapNotNull { JournalEntry.fromSnapshot(it) }

            val merged = _entries.value.toMutableList().apply {
                partnerEntries.forEach { e ->
                    if (none { it.id == e.id }) add(e)
                }
                sortByDescending { it.eventDate.time }
            }

            _entries.emit(merged)
        } catch (_: Throwable) {
            // no-op
        }
    }
}
