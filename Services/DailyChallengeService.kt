@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.love2love.daily

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------
// Models & App State (stubs)
// ---------------------------

data class DailyChallenge(
    val id: String,
    val challengeKey: String,
    val challengeDay: Int,
    val scheduledDate: Date,
    val coupleId: String,
    val isCompleted: Boolean = false,
    val completedAt: Date? = null
)

data class DailyChallengeSettings(
    val coupleId: String,
    val startDate: Date,
    val timezone: String,
    val currentDay: Int,
    val createdAt: Date,
    val lastVisitDate: Date
)

// Représente ton user applicatif (côté app, pas FirebaseAuth)
data class AppUser(
    val id: String,
    val partnerId: String?
)

data class AppState(
    val currentUser: AppUser?
)

// ---------------------------
// Cache Manager (stub Realm)
// ---------------------------
object QuestionCacheManager {
    // Récupère les N derniers défis pour un couple
    fun getCachedDailyChallenges(forCoupleId: String, limit: Int): List<DailyChallenge> = emptyList()

    // Récupère le défi du jour si présent
    fun getCachedDailyChallenge(forCoupleId: String, date: Date): DailyChallenge? = null

    // Met en cache un défi
    fun cacheDailyChallenge(challenge: DailyChallenge) { /* TODO: Impl Realm/Room */ }

    // Mise à jour du statut completion dans le cache
    fun updateDailyChallengeCompletion(
        challengeId: String,
        isCompleted: Boolean,
        completedAt: Date?
    ) { /* TODO */ }

    // Statistiques du cache
    fun getDailyChallengesCacheInfo(forCoupleId: String): Triple<Int, Date?, Date?> =
        Triple(0, null, null)

    // Clear du cache pour un couple
    fun clearDailyChallengesCache(forCoupleId: String) { /* TODO */ }
}

// ---------------------------------
// Helpers de localisation Android
// ---------------------------------
fun Context.getStringByName(name: String, vararg args: Any): String {
    val resId = resources.getIdentifier(name, "string", packageName)
    return if (resId != 0) {
        if (args.isNotEmpty()) getString(resId, *args) else getString(resId)
    } else {
        // Fallback : retourne la clé si la res n'existe pas (utile en dev)
        name
    }
}

/**
 * Exemple Compose : obtenir la string localisée d’une clé de défi
 */
@Composable
fun rememberLocalizedChallengeText(challengeKey: String): String {
    val context = LocalContext.current
    return remember(challengeKey) { context.getStringByName(challengeKey) }
}

// ---------------------------------
// DailyChallengeService (Android)
// ---------------------------------
class DailyChallengeService private constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "DailyChallengeService"

        @Volatile
        private var INSTANCE: DailyChallengeService? = null

        fun getInstance(context: Context): DailyChallengeService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DailyChallengeService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Firebase
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    // Listeners
    private var challengeListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null

    // App state (faible référence pour éviter les cycles)
    private var appStateRef: WeakReference<AppState>? = null

    // Flags
    private var isConfigured: Boolean = false
    private var currentCoupleId: String? = null

    // Scoping
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Observables (équivalents @Published)
    private val _currentChallenge = MutableStateFlow<DailyChallenge?>(null)
    val currentChallenge: StateFlow<DailyChallenge?> = _currentChallenge

    private val _challengeHistory = MutableStateFlow<List<DailyChallenge>>(emptyList())
    val challengeHistory: StateFlow<List<DailyChallenge>> = _challengeHistory

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _currentSettings = MutableStateFlow<DailyChallengeSettings?>(null)
    val currentSettings: StateFlow<DailyChallengeSettings?> = _currentSettings

    // ---------------------------
    // Configuration
    // ---------------------------
    @MainThread
    fun configure(appState: AppState) {
        val newCoupleId = generateCoupleId(appState)

        if (isConfigured && currentCoupleId == newCoupleId) {
            Log.d(TAG, "⚡ Déjà configuré pour couple $newCoupleId — pas de reconfiguration")
            return
        }

        Log.d(TAG, "🔄 Configuration pour couple $newCoupleId")
        appStateRef = WeakReference(appState)
        currentCoupleId = newCoupleId
        isConfigured = true

        // Sauvegarder la langue pour les notifications localisées
        saveUserLanguageToFirebase()

        setupListeners()
    }

    private fun generateCoupleId(appState: AppState): String? {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return null
        val partnerId = appState.currentUser?.partnerId?.trim().orEmpty()
        if (partnerId.isEmpty()) return null
        return listOf(firebaseUser.uid, partnerId).sorted().joinToString("_")
    }

    // ---------------------------
    // Setup & Lifecycle
    // ---------------------------
    private fun setupListeners() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val partnerId = appStateRef?.get()?.currentUser?.partnerId?.trim()

        if (firebaseUser == null || partnerId.isNullOrEmpty()) {
            Log.w(TAG, "🔥 Aucun partenaire connecté")
            return
        }

        val coupleId = listOf(firebaseUser.uid, partnerId).sorted().joinToString("_")
        Log.d(TAG, "🔥 ✅ Utilisation Firebase UID — coupleId=$coupleId")

        scope.launch {
            loadFromCacheFirst(coupleId)
            if (shouldGenerateToday()) {
                generateTodaysChallenge(coupleId)
            } else {
                Log.d(TAG, "⚡ Défi d'aujourd'hui déjà disponible — pas de génération")
            }
        }

        setupChallengeListener(coupleId)
        setupSettingsListener(coupleId)
    }

    private suspend fun loadFromCacheFirst(coupleId: String) {
        val cached = QuestionCacheManager.getCachedDailyChallenges(forCoupleId = coupleId, limit = 5)
        if (cached.isNotEmpty()) {
            Log.d(TAG, "⚡ Chargement immédiat depuis cache — ${cached.size} défi(s)")
            if (_currentChallenge.value == null) {
                _challengeHistory.value = cached
                _currentChallenge.value = cached.first()
                _isLoading.value = false
                Log.d(TAG, "⚡ Défi affiché depuis cache: ${cached.first().challengeKey}")
            }
        }
    }

    private fun setupChallengeListener(coupleId: String) {
        Log.d(TAG, "🔥 === SETUP CHALLENGE LISTENER ===")

        challengeListener?.remove()
        Log.d(TAG, "🎯 Ancien challenge listener supprimé")

        challengeListener = db.collection("dailyChallenges")
            .whereEqualTo("coupleId", coupleId)
            .orderBy("challengeDay")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener défis", error)
                    // Fallback cache
                    scope.launch { loadFromRealmCache(coupleId) }
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: run {
                    Log.d(TAG, "📊 Aucun document trouvé — fallback cache")
                    scope.launch { loadFromRealmCache(coupleId) }
                    return@addSnapshotListener
                }

                Log.d(TAG, "🎯 Listener déclenché — ${docs.size} document(s)")
                val challenges = docs.mapNotNull { parseChallengeDocument(it) }

                cacheChallengesToRealm(challenges)

                if (challenges.isEmpty()) {
                    Log.w(TAG, "⚠️ Aucun défi décodé — fallback cache")
                    scope.launch { loadFromRealmCache(coupleId) }
                } else {
                    updateCurrentChallenge(challenges)
                }

                Log.d(TAG, "✅ Historique chargé — ${challenges.size} défis")
            }
    }

    private fun setupSettingsListener(coupleId: String) {
        Log.d(TAG, "🔥 === SETUP SETTINGS LISTENER ===")

        settingsListener?.remove()
        Log.d(TAG, "🎯 Ancien settings listener supprimé")

        settingsListener = db.collection("dailyChallengeSettings")
            .document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener settings", error)
                    return@addSnapshotListener
                }
                val data = snapshot?.data ?: run {
                    Log.d(TAG, "📊 Aucun settings trouvé")
                    return@addSnapshotListener
                }
                parseSettingsDocument(data)?.let {
                    _currentSettings.value = it
                    Log.d(TAG, "✅ Settings chargés pour couple $coupleId")
                }
            }
    }

    // ---------------------------
    // Parsing Firestore Documents
    // ---------------------------
    private fun parseChallengeDocument(doc: DocumentSnapshot): DailyChallenge? {
        val data = doc.data ?: return null
        val challengeKey = data["challengeKey"] as? String ?: return null
        val challengeDay = (data["challengeDay"] as? Number)?.toInt() ?: return null
        val scheduledTs = data["scheduledDate"] as? Timestamp ?: return null
        val coupleId = data["coupleId"] as? String ?: return null

        val isCompleted = data["isCompleted"] as? Boolean ?: false
        val completedAt = (data["completedAt"] as? Timestamp)?.toDate()

        return DailyChallenge(
            id = doc.id,
            challengeKey = challengeKey,
            challengeDay = challengeDay,
            scheduledDate = scheduledTs.toDate(),
            coupleId = coupleId,
            isCompleted = isCompleted,
            completedAt = completedAt
        )
    }

    private fun parseSettingsDocument(data: Map<String, Any>): DailyChallengeSettings? {
        val coupleId = data["coupleId"] as? String ?: return null
        val startDate = (data["startDate"] as? Timestamp)?.toDate() ?: return null
        val timezone = data["timezone"] as? String ?: return null
        val currentDay = (data["currentDay"] as? Number)?.toInt() ?: return null
        val createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: return null
        val lastVisitDate = (data["lastVisitDate"] as? Timestamp)?.toDate() ?: return null

        return DailyChallengeSettings(
            coupleId = coupleId,
            startDate = startDate,
            timezone = timezone,
            currentDay = currentDay,
            createdAt = createdAt,
            lastVisitDate = lastVisitDate
        )
    }

    // ---------------------------
    // Challenge Management
    // ---------------------------
    private fun updateCurrentChallenge(challenges: List<DailyChallenge>) {
        val sorted = challenges.sortedBy { it.challengeDay }
        _challengeHistory.value = sorted
        val latest = sorted.lastOrNull()
        _currentChallenge.value = latest

        latest?.let {
            Log.d(TAG, "🎯 Défi actuel: key=${it.challengeKey}, day=${it.challengeDay}, date=${it.scheduledDate}, id=${it.id}")
        }
    }

    private fun shouldGenerateToday(): Boolean {
        val current = _currentChallenge.value ?: return true
        val cal = Calendar.getInstance()
        val today = cal.time

        val sameDay = Calendar.getInstance().apply {
            time = current.scheduledDate
        }.let { c ->
            c.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            c.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
        }
        return !sameDay
    }

    // ---------------------------
    // Completion toggles
    // ---------------------------
    fun markChallengeAsCompleted(challenge: DailyChallenge) {
        val updated = challenge.copy(isCompleted = true, completedAt = Date())

        // Local update (history + current)
        _challengeHistory.value = _challengeHistory.value.map { if (it.id == challenge.id) updated else it }
        if (_currentChallenge.value?.id == challenge.id) _currentChallenge.value = updated

        // Cache
        QuestionCacheManager.updateDailyChallengeCompletion(challenge.id, true, updated.completedAt)

        // Firestore
        db.collection("dailyChallenges").document(challenge.id)
            .update(mapOf(
                "isCompleted" to true,
                "completedAt" to Timestamp.now()
            ))
            .addOnSuccessListener { Log.d(TAG, "✅ Défi marqué comme complété") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ Erreur maj completion", e) }
    }

    fun markChallengeAsNotCompleted(challenge: DailyChallenge) {
        val updated = challenge.copy(isCompleted = false, completedAt = null)

        _challengeHistory.value = _challengeHistory.value.map { if (it.id == challenge.id) updated else it }
        if (_currentChallenge.value?.id == challenge.id) _currentChallenge.value = updated

        QuestionCacheManager.updateDailyChallengeCompletion(challenge.id, false, null)

        db.collection("dailyChallenges").document(challenge.id)
            .update(mapOf(
                "isCompleted" to false,
                "completedAt" to com.google.firebase.firestore.FieldValue.delete()
            ))
            .addOnSuccessListener { Log.d(TAG, "✅ Défi marqué comme non complété") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ Erreur maj incompletion", e) }
    }

    // ---------------------------
    // Firebase Functions
    // ---------------------------
    private fun generateTodaysChallenge(coupleId: String) {
        Log.d(TAG, "🔥 === GENERATE TODAY'S CHALLENGE ===")
        _isLoading.value = true

        val data = hashMapOf(
            "coupleId" to coupleId,
            "timezone" to (java.util.TimeZone.getDefault().id)
        )

        functions
            .getHttpsCallable("generateDailyChallenge")
            .call(data)
            .addOnCompleteListener(OnCompleteListener { task ->
                _isLoading.value = false

                if (!task.isSuccessful) {
                    val ex = task.exception
                    Log.e(TAG, "❌ Firebase Function error", ex)
                    return@OnCompleteListener
                }

                val resultData = task.result?.data
                Log.d(TAG, "✅ Firebase Function success — data=$resultData")
                // Les listeners Firestore récupéreront les données
            })
    }

    // ---------------------------
    // Public: Refresh & Optimized Check
    // ---------------------------
    fun refreshChallenges() {
        Log.d(TAG, "🚀 Refresh challenges demandé")

        if (_isLoading.value) {
            Log.d(TAG, "⚠️ Génération déjà en cours — on stoppe pour éviter double appel")
            return
        }

        _currentChallenge.value?.let {
            Log.d(TAG, "✅ Défi déjà présent (${it.challengeKey}) — pas de régénération")
            return
        }

        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: run {
            Log.e(TAG, "❌ Firebase user nul")
            return
        }
        val appUser = appStateRef?.get()?.currentUser ?: run {
            Log.e(TAG, "❌ AppUser nul")
            return
        }
        val partnerId = appUser.partnerId ?: run {
            Log.e(TAG, "❌ partnerId nul")
            return
        }
        if (partnerId.trim().isEmpty()) {
            Log.e(TAG, "❌ partnerId vide après trim")
            return
        }

        val coupleId = listOf(firebaseUser.uid, partnerId).sorted().joinToString("_")
        Log.d(TAG, "🎯 CoupleId=$coupleId — appel function...")
        generateTodaysChallenge(coupleId)
        setupChallengeListener(coupleId)
    }

    fun optimizedDailyChallengeCheck() {
        scope.launch {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            val partnerId = appStateRef?.get()?.currentUser?.partnerId?.trim()

            if (firebaseUser == null || partnerId.isNullOrEmpty()) {
                Log.d(TAG, "🔥 Aucun partenaire connecté — pas d’optimisation")
                return@launch
            }

            val coupleId = listOf(firebaseUser.uid, partnerId).sorted().joinToString("_")
            val today = Date()

            Log.d(TAG, "📦 Phase 1: vérification cache Realm")
            val cached = QuestionCacheManager.getCachedDailyChallenge(coupleId, today)
            if (cached != null) {
                Log.d(TAG, "✅ Cache hit! ${cached.challengeKey}")
                _currentChallenge.value = cached
                _challengeHistory.value = listOf(cached)
                Log.d(TAG, "⚡ 0 appel Firebase nécessaire")
                return@launch
            }

            Log.d(TAG, "❌ Cache miss — fallback Functions")
            _isLoading.value = true
            generateTodaysChallenge(coupleId)
        }
    }

    // ---------------------------
    // Cache utils
    // ---------------------------
    private fun cacheChallengesToRealm(challenges: List<DailyChallenge>) {
        Log.d(TAG, "📦 Cache de ${challenges.size} défi(s) dans Realm...")
        challenges.forEach { QuestionCacheManager.cacheDailyChallenge(it) }
        Log.d(TAG, "✅ ${challenges.size} défis cachés")
    }

    private suspend fun loadFromRealmCache(coupleId: String) {
        Log.d(TAG, "🔄 Chargement depuis cache Realm pour couple=$coupleId (fallback)")
        val cached = QuestionCacheManager.getCachedDailyChallenges(forCoupleId = coupleId, limit = 10)

        if (cached.isNotEmpty()) {
            _challengeHistory.value = cached
            _currentChallenge.value = cached.first()
            Log.d(TAG, "✅ ${cached.size} défis chargés depuis le cache — current=${cached.first().challengeKey}")
        } else {
            Log.e(TAG, "❌ Aucun défi dans le cache")
        }
    }

    fun getCacheStatistics(): Triple<Int, Date?, Date?> {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return Triple(0, null, null)
        val partnerId = appStateRef?.get()?.currentUser?.partnerId ?: return Triple(0, null, null)
        val coupleId = listOf(firebaseUser.uid, partnerId).sorted().joinToString("_")
        return QuestionCacheManager.getDailyChallengesCacheInfo(forCoupleId = coupleId)
    }

    fun clearCache() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return
        val partnerId = appStateRef?.get()?.currentUser?.partnerId ?: return
        val coupleId = listOf(firebaseUser.uid, partnerId).sorted().joinToString("_")
        QuestionCacheManager.clearDailyChallengesCache(forCoupleId = coupleId)
        Log.d(TAG, "🗑️ Cache des défis nettoyé pour couple=$coupleId")
    }

    // ---------------------------
    // User language → Firestore
    // ---------------------------
    private fun saveUserLanguageToFirebase() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val lang = Locale.getDefault().language.ifBlank { "fr" }
        Log.d(TAG, "🌍 Sauvegarde langue utilisateur: $lang")

        db.collection("users").document(user.uid)
            .update(mapOf("languageCode" to lang))
            .addOnSuccessListener { Log.d(TAG, "✅ Langue $lang sauvegardée") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ Erreur sauvegarde langue", e) }
    }

    // ---------------------------
    // Localisation d’un challengeKey
    // ---------------------------
    fun localizeChallengeKey(challengeKey: String): String {
        // Remplace `challengeKey.localized(tableName: "DailyChallenges")`
        // par l’équivalent Android : context.getString(R.string.challengeKey)
        // via getIdentifier (ressource nommée = challengeKey)
        return context.getStringByName(challengeKey)
    }

    // ---------------------------
    // Cleanup
    // ---------------------------
    fun dispose() {
        challengeListener?.remove()
        settingsListener?.remove()
        challengeListener = null
        settingsListener = null
        isConfigured = false
    }
}
