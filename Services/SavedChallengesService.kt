@file:Suppress("unused")

package com.love2love.data

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.lang.ref.WeakReference

/**
 * Kotlin rewrite of the Swift `SavedChallengesService`.
 *
 * Differences / Android specifics:
 * - Uses StateFlow instead of @Published.
 * - Uses Firebase Firestore & Auth Android SDKs.
 * - For localization of a dynamic challenge key (e.g. "daily_challenge_1"),
 *   use [localizedChallengeText] which resolves a string resource by name
 *   from `strings.xml` (see helper at bottom).
 */
class SavedChallengesService private constructor(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    companion object {
        val shared: SavedChallengesService by lazy { SavedChallengesService() }
        private const val TAG = "SavedChallengesService"
        private const val COLLECTION = "savedChallenges"
    }

    // === Observables ===
    private val _savedChallenges = MutableStateFlow<List<SavedChallenge>>(emptyList())
    val savedChallenges: StateFlow<List<SavedChallenge>> = _savedChallenges.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastSavedChallenge = MutableStateFlow<SavedChallenge?>(null)
    val lastSavedChallenge: StateFlow<SavedChallenge?> = _lastSavedChallenge.asStateFlow()

    // Listener
    private var savedChallengesListener: ListenerRegistration? = null

    // Weak reference to app state (optional, to mirror Swift API and avoid strong cycles)
    private var appStateRef: WeakReference<Any>? = null

    /**
     * Configure and immediately start listening the user's saved challenges.
     * Keep the signature similar to Swift: `configure(with: appState)`.
     */
    fun configure(appState: Any) {
        appStateRef = WeakReference(appState)
        setupListener()
    }

    fun refreshSavedChallenges() {
        setupListener()
    }

    fun dispose() {
        savedChallengesListener?.remove()
        savedChallengesListener = null
    }

    // === Private: Listener setup ===
    private fun setupListener() {
        val user = auth.currentUser
        if (user == null) {
            Log.w(TAG, "🔥 No Firebase user. Aborting listener setup.")
            _savedChallenges.value = emptyList()
            return
        }

        Log.d(TAG, "🔥 Using Firebase UID: ${user.uid}")

        savedChallengesListener?.remove()
        savedChallengesListener = db.collection(COLLECTION)
            .whereEqualTo("userId", user.uid)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Listener error", error)
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: run {
                    Log.d(TAG, "📊 No saved challenges found")
                    _savedChallenges.value = emptyList()
                    return@addSnapshotListener
                }

                Log.d(TAG, "🎯 Listener fired. Documents: ${docs.size}")

                val parsed = docs.mapNotNull { parseSavedChallengeDocument(it) }
                _savedChallenges.value = parsed
                Log.d(TAG, "✅ Loaded ${parsed.size} saved challenge(s)")
            }
    }

    // === Private: Document parsing ===
    private fun parseSavedChallengeDocument(document: DocumentSnapshot): SavedChallenge? {
        val data = document.data ?: return null

        val challengeKey = data["challengeKey"] as? String ?: run {
            Log.w(TAG, "❌ Missing challengeKey in doc ${document.id}")
            return null
        }
        val challengeDay = (data["challengeDay"] as? Number)?.toInt() ?: run {
            Log.w(TAG, "❌ Missing/invalid challengeDay in doc ${document.id}")
            return null
        }
        val savedAt = (data["savedAt"] as? Timestamp)?.toDate() ?: Date()
        val userId = data["userId"] as? String ?: run {
            Log.w(TAG, "❌ Missing userId in doc ${document.id}")
            return null
        }

        return SavedChallenge(
            id = document.id,
            challengeKey = challengeKey,
            challengeDay = challengeDay,
            savedAt = savedAt,
            userId = userId
        )
    }

    // === Public: Save ===
    fun saveChallenge(challenge: DailyChallenge) {
        val user = auth.currentUser
        if (user == null) {
            Log.e(TAG, "❌ No Firebase user. Cannot save challenge.")
            return
        }

        Log.d(TAG, "💾 Saving challenge with Firebase UID: ${user.uid}")
        _isLoading.value = true

        val now = Date()
        val savedChallenge = SavedChallenge(
            id = SavedChallenge.generateId(user.uid, challenge.challengeKey),
            challengeKey = challenge.challengeKey,
            challengeDay = challenge.challengeDay,
            savedAt = now,
            userId = user.uid
        )

        val data = mapOf(
            "challengeKey" to savedChallenge.challengeKey,
            "challengeDay" to savedChallenge.challengeDay,
            "savedAt" to Timestamp(now),
            "userId" to savedChallenge.userId
        )

        db.collection(COLLECTION).document(savedChallenge.id)
            .set(data)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Challenge saved successfully: ${challenge.challengeKey}")
                    scope.launch {
                        _lastSavedChallenge.value = savedChallenge
                        delay(3_000)
                        _lastSavedChallenge.value = null
                    }
                } else {
                    Log.e(TAG, "❌ Error saving challenge", task.exception)
                }
            }
    }

    // === Public: Delete ===
    fun deleteChallenge(savedChallenge: SavedChallenge) {
        Log.d(TAG, "🗑️ Deleting saved challenge: key=${savedChallenge.challengeKey}, id=${savedChallenge.id}")
        val previous = _savedChallenges.value

        // Optimistic local update for snappy UI
        _savedChallenges.value = previous.filterNot { it.id == savedChallenge.id }

        db.collection(COLLECTION).document(savedChallenge.id)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "✅ Challenge deleted from Firebase: ${savedChallenge.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error deleting challenge; restoring locally", e)
                _savedChallenges.value = previous // restore
            }
    }

    // === Queries / helpers ===
    fun isChallengeAlreadySaved(challenge: DailyChallenge): Boolean =
        _savedChallenges.value.any { it.challengeKey == challenge.challengeKey }

    fun getSavedChallengesCount(): Int = _savedChallenges.value.size

    fun clearSavedChallenges() {
        val current = _savedChallenges.value
        if (current.isEmpty()) return

        Log.d(TAG, "🗑️ Clearing all saved challenges (count=${current.size})")
        val batch = db.batch()
        current.forEach { sc ->
            val ref = db.collection(COLLECTION).document(sc.id)
            batch.delete(ref)
        }
        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "✅ All saved challenges cleared")
                _savedChallenges.value = emptyList()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error clearing saved challenges", e)
            }
    }
}

// === Models ===

data class DailyChallenge(
    val challengeKey: String,
    val challengeDay: Int
)

data class SavedChallenge(
    val id: String = "",
    val challengeKey: String,
    val challengeDay: Int,
    val savedAt: Date = Date(),
    val userId: String
) {
    companion object {
        fun generateId(userId: String, challengeKey: String): String =
            "${userId}_${challengeKey}"
    }
}

// === Localization Helpers ===

/**
 * Resolve a dynamic `challengeKey` like "daily_challenge_5" to a string in `strings.xml`.
 * If not found, returns the key itself as a safe fallback.
 *
 * In `strings.xml`, declare:
 *   <string name="daily_challenge_1">…</string>
 *   <string name="daily_challenge_2">…</string>
 *   … etc.
 */
fun localizedChallengeText(context: Context, challengeKey: String): String {
    val resId = context.resources.getIdentifier(challengeKey, "string", context.packageName)
    return if (resId != 0) context.getString(resId) else challengeKey
}

/*
// Compose usage example:
@Composable
fun ChallengeTitleText(challengeKey: String) {
    val context = LocalContext.current
    val title = remember(challengeKey) { localizedChallengeText(context, challengeKey) }
    Text(text = title)
}
*/
