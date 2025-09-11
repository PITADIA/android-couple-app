@file:Suppress("unused")

package com.love2love.data

import android.content.Context
import androidx.annotation.StringRes
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * FavoritesService (Android/Kotlin)
 *
 * Parité fonctionnelle avec la version Swift :
 *  - Cache local via Realm (Android)
 *  - Synchronisation Firestore (collection "favoriteQuestions")
 *  - Listener temps réel pour les favoris partagés (whereArrayContains on partnerIds)
 *  - Ajout/Suppression de favoris (partagés et locaux)
 *  - Méthodes utilitaires (search, group, stats, isFavorite, canDelete, clearAll)
 *  - Firebase Functions : syncPartnerFavorites
 *  - Firebase Analytics : logEvent("question_favoriee")
 *
 * Localisation :
 *  - Remplace les appels de type `challengeKey.localized(tableName: "DailyChallenges")`
 *    par l’équivalent Android : `context.getString(R.string.challengeKey)`.
 *  - Dans ce service, les titres des catégories passent par `@StringRes` et sont
 *    résolus au moment de l’ajout (`context.getString(category.titleResId)`).
 */
class FavoritesService(
    private val context: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    // --- État observable (équivalent @Published Swift) ---
    private val _favoriteQuestions = MutableStateFlow<List<FavoriteQuestion>>(emptyList())
    val favoriteQuestions: StateFlow<List<FavoriteQuestion>> = _favoriteQuestions.asStateFlow()

    private val _sharedFavoriteQuestions = MutableStateFlow<List<SharedFavoriteQuestion>>(emptyList())
    val sharedFavoriteQuestions: StateFlow<List<SharedFavoriteQuestion>> = _sharedFavoriteQuestions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Utilisateur courant ---
    private var currentUserId: String? = null
    private var currentUserName: String? = null

    // --- Firestore Listener ---
    private var listener: ListenerRegistration? = null

    // --- Realm ---
    private var realm: Realm? = null

    // --- Coroutine scope (Main/UI) ---
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        initializeRealm()
    }

    /**
     * À appeler une seule fois au démarrage de l’app (idéalement dans Application),
     * mais on le garde ici pour robustesse.
     */
    private fun initializeRealm() {
        try {
            // Sûr même si déjà appelé au niveau Application
            Realm.init(context)

            // Schéma minimal contenant uniquement le cache des favoris
            val config = RealmConfiguration.Builder()
                .name("love2love.realm")
                .schemaVersion(3) // incrémenté par rapport aux changements de modèle
                .modules() // utilise le module par défaut (toutes classes du classloader)
                // .migration { realm, oldVersion, newVersion ->
                //     // Ajoute ta logique de migration ici si besoin
                // }
                .build()

            realm = Realm.getInstance(config)
        } catch (t: Throwable) {
            println("❌ FavoritesService: Erreur Realm init: ${t.message}")
            realm = null
        }
    }

    // =========================
    // Gestion utilisateur
    // =========================

    fun setCurrentUser(userId: String, name: String) {
        println("🔥 FavoritesService: Configuration utilisateur (Android)")
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
        println("🔥 FavoritesService: UID correspond: ${userId == firebaseUid}")

        currentUserId = userId
        currentUserName = name

        loadLocalFavorites()
        setupFirestoreListener()
    }

    fun clearCurrentUser() {
        println("🔥 FavoritesService: Nettoyage utilisateur")
        currentUserId = null
        currentUserName = null
        _favoriteQuestions.value = emptyList()
        _sharedFavoriteQuestions.value = emptyList()

        listener?.remove()
        listener = null
    }

    // =========================
    // Firestore Listener
    // =========================

    private fun setupFirestoreListener() {
        val userId = currentUserId ?: run {
            println("❌ FavoritesService: Aucun utilisateur pour le listener")
            return
        }

        println("🔥 FavoritesService: Configuration du listener Firestore")
        listener?.remove()

        val firebaseUID = FirebaseAuth.getInstance().currentUser?.uid ?: userId
        println("🔥 FavoritesService: Listener avec Firebase UID: $firebaseUID")

        listener = db.collection("favoriteQuestions")
            .whereArrayContains("partnerIds", firebaseUID)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("❌ FavoritesService: Erreur listener: ${error.message}")
                    _errorMessage.value = error.localizedMessage
                    return@addSnapshotListener
                }

                handleFirestoreUpdate(snapshot)
            }
    }

    private fun handleFirestoreUpdate(snapshot: QuerySnapshot?) {
        val docs = snapshot?.documents ?: run {
            println("⚠️ FavoritesService: Snapshot vide")
            return
        }

        println("✅ FavoritesService: ${docs.size} document(s) reçu(s)")

        val parsed = docs.mapNotNull { doc ->
            SharedFavoriteQuestion.from(doc).also { favorite ->
                if (favorite == null) {
                    println("❌ FavoritesService: Échec parsing doc ${doc.id}")
                }
            }
        }.sortedByDescending { it.dateAdded }

        _sharedFavoriteQuestions.value = parsed

        // Synchroniser avec le cache local
        syncToLocalCache()
    }

    // =========================
    // Noyau Favoris (Hybride)
    // =========================

    fun toggleFavorite(question: Question, category: QuestionCategory) {
        val uid = currentUserId
        val name = currentUserName
        if (uid == null || name == null) {
            println("❌ FavoritesService: Pas d'utilisateur connecté")
            return
        }

        if (isFavorite(question.id)) {
            removeFavorite(question.id)
        } else {
            addFavorite(question, category, uid, name)
        }
    }

    fun addFavorite(
        question: Question,
        category: QuestionCategory,
        userId: String,
        userName: String
    ) {
        if (isFavorite(question.id)) {
            println("⚠️ FavoritesService: Question déjà en favoris")
            return
        }

        _isLoading.value = true

        scope.launch(Dispatchers.IO) {
            try {
                // Construire la liste des partenaires (Firebase UIDs uniquement)
                val partnerIds = mutableListOf<String>()

                FirebaseAuth.getInstance().currentUser?.uid?.let { partnerIds.add(it) }
                // Si tu as un partnerId côté app, ajoute-le ici (Firebase UID attendu)
                // Exemple : appState.currentUser?.partnerId?.let(partnerIds::add)

                val sharedFavorite = SharedFavoriteQuestion(
                    questionId = question.id,
                    questionText = question.text,
                    categoryTitle = context.getString(category.titleResId), // ← localisation Android
                    emoji = category.emoji,
                    authorId = FirebaseAuth.getInstance().currentUser?.uid ?: userId,
                    authorName = userName,
                    partnerIds = partnerIds
                )

                val documentRef = db.collection("favoriteQuestions").document(sharedFavorite.id)
                val data = sharedFavorite.toMap()

                println("🔥 FavoritesService: Sauvegarde Firestore → ${sharedFavorite.id}")
                documentRef.set(data).addOnFailureListener { e ->
                    println("❌ FavoritesService: Erreur setData: ${e.message}")
                }.await()

                // Analytics
                Firebase.analytics.logEvent("question_favoriee") {
                    param("question_id", question.id)
                    param("categorie", category.id)
                }
                println("📊 Événement Firebase: question_favoriee - ${question.id} - ${category.id}")

                _isLoading.value = false
            } catch (t: Throwable) {
                println("❌ FavoritesService: Erreur ajout favori partagé: ${t.message}")
                _isLoading.value = false
                _errorMessage.value = "Erreur lors de l'ajout aux favoris"

                // Fallback local
                addLocalFavorite(question, category, userId)
            }
        }
    }

    fun removeFavorite(questionId: String) {
        val uid = currentUserId ?: run {
            println("❌ FavoritesService: Pas d'utilisateur connecté")
            return
        }

        println("🔥 FavoritesService: SUPPRESSION - Question ID: $questionId")

        _isLoading.value = true

        scope.launch(Dispatchers.IO) {
            try {
                val shared = _sharedFavoriteQuestions.value.firstOrNull { it.questionId == questionId }
                if (shared != null) {
                    val isAuthor = uid == shared.authorId
                    val isInPartnerIds = shared.partnerIds.contains(uid)
                    val canDelete = isAuthor || isInPartnerIds

                    println("🔥 FavoritesService: SUPPRESSION - isAuthor=$isAuthor inPartners=$isInPartnerIds can=$canDelete")

                    if (canDelete) {
                        db.collection("favoriteQuestions").document(shared.id).delete().await()
                        println("✅ FavoritesService: Favori partagé supprimé Firestore")
                    } else {
                        _isLoading.value = false
                        _errorMessage.value = "Tu n'as pas les permissions pour supprimer ce favori"
                        return@launch
                    }
                } else {
                    println("⚠️ FavoritesService: Favori non trouvé dans Firestore")
                }

                _isLoading.value = false
            } catch (t: Throwable) {
                println("❌ FavoritesService: Erreur suppression favori partagé: ${t.message}")
                _isLoading.value = false
                _errorMessage.value = "Erreur lors de la suppression"
            }
        }

        // Toujours nettoyer le cache local
        removeLocalFavorite(questionId)
    }

    fun isFavorite(questionId: String): Boolean {
        if (_sharedFavoriteQuestions.value.any { it.questionId == questionId }) return true
        return _favoriteQuestions.value.any { it.questionId == questionId }
    }

    fun canDeleteFavorite(questionId: String): Boolean {
        val uid = currentUserId ?: run {
            println("❌ FavoritesService: Pas d'utilisateur pour vérifier la suppression")
            return false
        }

        _sharedFavoriteQuestions.value.firstOrNull { it.questionId == questionId }?.let { shared ->
            val isAuthor = uid == shared.authorId
            val isInPartnerIds = shared.partnerIds.contains(uid)
            val can = isAuthor || isInPartnerIds
            println("🔥 FavoritesService: Peut supprimer $questionId → $can (author=$isAuthor partner=$isInPartnerIds)")
            return can
        }

        if (_favoriteQuestions.value.any { it.questionId == questionId }) {
            println("🔥 FavoritesService: Favori local - peut supprimer")
            return true
        }

        println("❌ FavoritesService: Favori non trouvé pour suppression")
        return false
    }

    // =========================
    // Cache local (Realm)
    // =========================

    private fun addLocalFavorite(question: Question, category: QuestionCategory, userId: String) {
        val r = realm ?: run {
            println("❌ FavoritesService: Realm non disponible")
            return
        }
        try {
            r.executeTransaction { tr ->
                val obj = tr.createObject(RealmFavoriteQuestion::class.java, UUID.randomUUID().toString())
                obj.questionId = question.id
                obj.userId = userId
                obj.categoryTitle = context.getString(category.titleResId) // ← localisation Android
                obj.questionText = question.text
                obj.emoji = category.emoji
                obj.dateAdded = System.currentTimeMillis()
            }
            loadLocalFavorites()
            println("✅ FavoritesService: Favori local ajouté: ${question.text.take(50)}…")
        } catch (t: Throwable) {
            println("❌ FavoritesService: Erreur ajout favori local: ${t.message}")
        }
    }

    private fun removeLocalFavorite(questionId: String) {
        val r = realm ?: run {
            println("❌ FavoritesService: Realm ou utilisateur non disponible")
            return
        }
        val uid = currentUserId ?: return
        try {
            r.executeTransaction { tr ->
                val toDelete: RealmResults<RealmFavoriteQuestion> = tr.where(RealmFavoriteQuestion::class.java)
                    .equalTo("questionId", questionId)
                    .equalTo("userId", uid)
                    .findAll()
                toDelete.deleteAllFromRealm()
            }
            loadLocalFavorites()
            println("✅ FavoritesService: Favori local supprimé")
        } catch (t: Throwable) {
            println("❌ FavoritesService: Erreur suppression favori local: ${t.message}")
        }
    }

    private fun loadLocalFavorites() {
        val r = realm ?: run {
            _favoriteQuestions.value = emptyList()
            return
        }
        val uid = currentUserId ?: run {
            _favoriteQuestions.value = emptyList()
            return
        }
        val results: RealmResults<RealmFavoriteQuestion> = r.where(RealmFavoriteQuestion::class.java)
            .equalTo("userId", uid)
            .sort("dateAdded", io.realm.Sort.DESCENDING)
            .findAll()

        _favoriteQuestions.value = results.map { it.toFavoriteItem() }
        println("🔥 FavoritesService: ${_favoriteQuestions.value.size} favoris locaux chargés")
    }

    private fun syncToLocalCache() {
        val r = realm ?: run {
            println("❌ FavoritesService: Realm non disponible pour sync")
            return
        }
        val uid = currentUserId ?: run {
            println("❌ FavoritesService: Utilisateur non disponible pour sync")
            return
        }

        val shared = _sharedFavoriteQuestions.value
        println("🔥 FavoritesService: SYNC CACHE - ${shared.size} favoris partagés")

        try {
            r.executeTransaction { tr ->
                val local = tr.where(RealmFavoriteQuestion::class.java)
                    .equalTo("userId", uid)
                    .findAll()

                val localIds = local.map { it.questionId }.toSet()
                val sharedIds = shared.map { it.questionId }.toSet()

                // Supprimer du cache local les favoris qui ne sont plus partagés
                val idsToRemove = localIds - sharedIds
                if (idsToRemove.isNotEmpty()) {
                    val toDelete = tr.where(RealmFavoriteQuestion::class.java)
                        .`in`("questionId", idsToRemove.toTypedArray())
                        .equalTo("userId", uid)
                        .findAll()
                    toDelete.deleteAllFromRealm()
                }

                // Ajouter les nouveaux favoris partagés
                val idsToAdd = sharedIds - localIds
                if (idsToAdd.isNotEmpty()) {
                    shared.filter { it.questionId in idsToAdd }.forEach { sf ->
                        val obj = tr.createObject(RealmFavoriteQuestion::class.java, UUID.randomUUID().toString())
                        obj.questionId = sf.questionId
                        obj.userId = uid
                        obj.categoryTitle = sf.categoryTitle
                        obj.questionText = sf.questionText
                        obj.emoji = sf.emoji
                        obj.dateAdded = sf.dateAdded
                    }
                }
            }
            loadLocalFavorites()
            println("✅ FavoritesService: SYNC CACHE - Synchronisation terminée")
        } catch (t: Throwable) {
            println("❌ FavoritesService: Erreur sync cache: ${t.message}")
        }
    }

    // =========================
    // Accès données combinées
    // =========================

    fun getAllFavorites(): List<FavoriteQuestion> {
        val combined = mutableListOf<FavoriteQuestion>()
        combined += _sharedFavoriteQuestions.value.map { it.toLocalFavorite() }

        favoriteQuestions.value.forEach { local ->
            if (combined.none { it.questionId == local.questionId }) {
                combined += local
            }
        }
        return combined.sortedByDescending { it.dateAdded }
    }

    fun getFavoritesByCategory(): Map<String, List<FavoriteQuestion>> {
        val grouped = linkedMapOf<String, MutableList<FavoriteQuestion>>()
        getAllFavorites().forEach { fav ->
            grouped.getOrPut(fav.categoryTitle) { mutableListOf() }.add(fav)
        }
        return grouped
    }

    fun getRecentFavorites(limit: Int = 10): List<FavoriteQuestion> = getAllFavorites().take(limit)

    fun searchFavorites(query: String): List<FavoriteQuestion> {
        if (query.isBlank()) return getAllFavorites()
        val q = query.lowercase()
        return getAllFavorites().filter { fav ->
            fav.questionText.lowercase().contains(q) || fav.categoryTitle.lowercase().contains(q)
        }
    }

    fun getFavoritesCount(): Int = getAllFavorites().size

    fun getFavoritesCountByCategory(): Map<String, Int> = getFavoritesByCategory().mapValues { it.value.size }

    // =========================
    // Sync partenaire (Cloud Function)
    // =========================

    fun syncPartnerFavorites(partnerId: String, completion: (Boolean, String?) -> Unit) {
        println("❤️ FavoritesService: Début synchronisation favoris avec partenaire: $partnerId")

        if (FirebaseAuth.getInstance().currentUser == null) {
            println("❌ FavoritesService: Aucun utilisateur connecté")
            completion(false, "Utilisateur non connecté")
            return
        }

        functions
            .getHttpsCallable("syncPartnerFavorites")
            .call(mapOf("partnerId" to partnerId))
            .addOnSuccessListener { result ->
                val data = result.data as? Map<*, *> ?: run {
                    println("❌ FavoritesService: Réponse invalide de la fonction")
                    completion(false, "Réponse invalide du serveur")
                    return@addOnSuccessListener
                }
                val success = data["success"] as? Boolean ?: false
                if (success) {
                    val updated = (data["updatedFavoritesCount"] as? Number)?.toInt() ?: 0
                    println("✅ FavoritesService: Synchronisation réussie - $updated favoris mis à jour")
                    completion(true, "Synchronisation réussie: $updated favoris mis à jour")
                } else {
                    val message = data["message"] as? String ?: "Erreur inconnue"
                    println("❌ FavoritesService: Échec synchronisation: $message")
                    completion(false, message)
                }
            }
            .addOnFailureListener { e ->
                println("❌ FavoritesService: Erreur synchronisation favoris: ${e.message}")
                completion(false, "Erreur lors de la synchronisation: ${e.localizedMessage}")
            }
    }

    // =========================
    // Nettoyage
    // =========================

    fun clearAllFavorites() {
        val uid = currentUserId ?: return

        // Supprimer les favoris partagés dont on est auteur (sécurité côté règles Firestore recommandée)
        scope.launch(Dispatchers.IO) {
            try {
                val authored = _sharedFavoriteQuestions.value.filter { it.authorId == uid }
                authored.forEach { sf ->
                    db.collection("favoriteQuestions").document(sf.id).delete()
                }
            } catch (t: Throwable) {
                println("❌ FavoritesService: Erreur suppression favoris partagés: ${t.message}")
            }
        }

        // Supprimer le cache local
        val r = realm ?: return
        try {
            r.executeTransaction { tr ->
                val userFavs = tr.where(RealmFavoriteQuestion::class.java)
                    .equalTo("userId", uid)
                    .findAll()
                userFavs.deleteAllFromRealm()
            }
            loadLocalFavorites()
            println("🔥 FavoritesService: Tous les favoris supprimés")
        } catch (t: Throwable) {
            println("❌ FavoritesService: Erreur suppression favoris: ${t.message}")
        }
    }

    fun close() {
        listener?.remove(); listener = null
        realm?.close(); realm = null
    }
}

// =============================================================
// Modèles & helpers
// =============================================================

data class Question(
    val id: String,
    val text: String
)

data class QuestionCategory(
    val id: String,
    @StringRes val titleResId: Int,
    val emoji: String
)

data class FavoriteQuestion(
    val id: String,
    val questionId: String,
    val questionText: String,
    val categoryTitle: String,
    val emoji: String,
    val dateAdded: Long
)

/** Modèle côté Firestore pour les favoris partagés */
data class SharedFavoriteQuestion(
    val id: String = UUID.randomUUID().toString(),
    val questionId: String,
    val questionText: String,
    val categoryTitle: String,
    val emoji: String,
    val authorId: String,
    val authorName: String,
    val partnerIds: List<String>,
    val dateAdded: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "questionId" to questionId,
        "questionText" to questionText,
        "categoryTitle" to categoryTitle,
        "emoji" to emoji,
        "authorId" to authorId,
        "authorName" to authorName,
        "partnerIds" to partnerIds,
        "dateAdded" to dateAdded
    )

    fun toLocalFavorite(): FavoriteQuestion = FavoriteQuestion(
        id = id,
        questionId = questionId,
        questionText = questionText,
        categoryTitle = categoryTitle,
        emoji = emoji,
        dateAdded = dateAdded
    )

    companion object {
        fun from(doc: DocumentSnapshot): SharedFavoriteQuestion? {
            return try {
                val id = doc.getString("id") ?: doc.id
                val questionId = doc.getString("questionId") ?: return null
                val questionText = doc.getString("questionText") ?: ""
                val categoryTitle = doc.getString("categoryTitle") ?: ""
                val emoji = doc.getString("emoji") ?: ""
                val authorId = doc.getString("authorId") ?: return null
                val authorName = doc.getString("authorName") ?: "Utilisateur"
                val partnerIds = (doc.get("partnerIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val dateAdded = (doc.getLong("dateAdded") ?: System.currentTimeMillis())

                SharedFavoriteQuestion(
                    id = id,
                    questionId = questionId,
                    questionText = questionText,
                    categoryTitle = categoryTitle,
                    emoji = emoji,
                    authorId = authorId,
                    authorName = authorName,
                    partnerIds = partnerIds,
                    dateAdded = dateAdded
                )
            } catch (t: Throwable) {
                println("❌ SharedFavoriteQuestion.from: ${t.message}")
                null
            }
        }
    }
}

/** Modèle Realm local pour le cache */
open class RealmFavoriteQuestion : RealmObject() {
    @PrimaryKey var id: String = ""
    var questionId: String = ""
    var userId: String = ""
    var categoryTitle: String = ""
    var questionText: String = ""
    var emoji: String = ""
    var dateAdded: Long = 0L

    fun toFavoriteItem(): FavoriteQuestion = FavoriteQuestion(
        id = id,
        questionId = questionId,
        questionText = questionText,
        categoryTitle = categoryTitle,
        emoji = emoji,
        dateAdded = dateAdded
    )
}

// =============================================================
// Extensions utilitaires pour Tasks -> suspend (await)
// =============================================================

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result, onCancellation = {})
            } else {
                cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
    }
}
