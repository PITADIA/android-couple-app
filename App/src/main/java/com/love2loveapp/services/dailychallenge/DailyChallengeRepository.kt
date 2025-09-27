package com.love2loveapp.services.dailychallenge

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.love2loveapp.models.DailyChallenge
import com.love2loveapp.models.DailyChallengeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🎯 DailyChallengeRepository - Service Principal Défis du Jour Android
 * Équivalent iOS DailyChallengeService.swift
 * 
 * Fonctionnalités principales :
 * - Génération défis quotidiens via Cloud Functions
 * - Système completion temps réel avec Firestore
 * - Gestion freemium (3 jours gratuits)
 * - Compatibilité iOS ↔ Android
 * - Cache et performance optimisés
 */
class DailyChallengeRepository private constructor(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "DailyChallengeRepository"
        private const val COLLECTION_DAILY_CHALLENGES = "dailyChallenges"
        private const val COLLECTION_SETTINGS = "dailyChallengeSettings"

        @Volatile
        private var INSTANCE: DailyChallengeRepository? = null

        fun getInstance(context: Context): DailyChallengeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DailyChallengeRepository(
                    context = context.applicationContext,
                    firestore = FirebaseFirestore.getInstance(),
                    functions = FirebaseFunctions.getInstance(),
                    auth = FirebaseAuth.getInstance()
                ).also { INSTANCE = it }
            }
        }
    }

    // 🔥 ÉTAT GLOBAL DU SERVICE
    private val _currentChallenge = MutableStateFlow<DailyChallenge?>(null)
    val currentChallenge: StateFlow<DailyChallenge?> = _currentChallenge.asStateFlow()

    private val _challengeHistory = MutableStateFlow<List<DailyChallenge>>(emptyList())
    val challengeHistory: StateFlow<List<DailyChallenge>> = _challengeHistory.asStateFlow()

    private val _currentSettings = MutableStateFlow<DailyChallengeSettings?>(null)
    val currentSettings: StateFlow<DailyChallengeSettings?> = _currentSettings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _hasSeenIntro = MutableStateFlow(false)
    val hasSeenIntro: StateFlow<Boolean> = _hasSeenIntro.asStateFlow()

    // 🔧 GESTION LISTENERS FIRESTORE
    private var challengeListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null

    // 📊 DONNÉES CACHED
    private var currentCoupleId: String? = null
    private var currentUserId: String? = null

    // MARK: - Initialisation et Configuration

    /**
     * 🚀 Initialisation pour un couple donné
     * Point d'entrée principal du service
     * 
     * ✅ STRATÉGIE CACHE-FIRST (comme DailyQuestionRepository) :
     * 1. Afficher immédiatement les données disponibles
     * 2. Configurer listeners temps réel 
     * 3. Générer défi en arrière-plan sans bloquer l'UI
     */
    suspend fun initializeForCouple(coupleId: String) {
        Log.d(TAG, "🚀 Initialisation défis pour couple: $coupleId")
        
        try {
            _errorMessage.value = null

            this.currentCoupleId = coupleId
            this.currentUserId = auth.currentUser?.uid

            if (currentUserId == null) {
                throw Exception("Utilisateur non authentifié")
            }

            // ✅ AFFICHAGE IMMÉDIAT - Pas de loading qui bloque
            Log.d(TAG, "⚡ Cache-first: Configuration listeners sans bloquer UI")

            // 🔑 1. CONFIGURER LISTENERS TEMPS RÉEL
            setupRealtimeListeners(coupleId)

            // ✅ ARRÊT LOADING APRÈS LISTENERS (CACHE-FIRST STRATEGY)
            _isLoading.value = false
            Log.d(TAG, "⚡ Loading arrêté - Prêt pour affichage immédiat !")

            // 🔑 2. TRAITEMENT ARRIÈRE-PLAN (non bloquant)
            
            // 🔑 3. CHARGER STATUS INTRO
            loadIntroStatus()

            // 🔑 4. CHARGER/CRÉER SETTINGS EN ARRIÈRE-PLAN
            loadOrCreateSettings(coupleId)

            // 🔑 5. GÉNÉRER DÉFI D'AUJOURD'HUI EN ARRIÈRE-PLAN
            generateTodayChallengeIfNeeded(coupleId)

            Log.d(TAG, "✅ Initialisation défis terminée pour couple: $coupleId")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur initialisation défis: ${e.message}")
            _errorMessage.value = "Erreur lors de l'initialisation: ${e.message}"
            _isLoading.value = false
        }
    }

    // MARK: - Real-time Listeners

    /**
     * 🔥 Configuration des listeners Firestore temps réel
     * Compatible avec l'architecture iOS existante
     */
    private fun setupRealtimeListeners(coupleId: String) {
        Log.d(TAG, "🔥 Configuration listeners temps réel défis pour couple: $coupleId")

        // Nettoyer anciens listeners
        cleanup()

        // 🔑 LISTENER DÉFIS
        challengeListener = firestore.collection(COLLECTION_DAILY_CHALLENGES)
            .whereEqualTo("coupleId", coupleId)
            .orderBy("challengeDay", Query.Direction.ASCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener défis: ${error.message}")
                    _errorMessage.value = "Erreur de synchronisation: ${error.message}"
                    return@addSnapshotListener
                }

                Log.d(TAG, "📥 Mise à jour défis: ${snapshot?.documents?.size} documents")

                val challenges = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        DailyChallenge.fromFirestore(doc)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur parsing défi: ${e.message}")
                        null
                    }
                } ?: emptyList()

                // 🔍 Trouver défi d'aujourd'hui
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayChallenge = challenges.find { challenge ->
                    val challengeDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(challenge.scheduledDate.toDate())
                    challengeDate == today
                }

                _currentChallenge.value = todayChallenge
                _challengeHistory.value = challenges

                // ✅ ARRÊT LOADING - DONNÉES PRÊTES POUR AFFICHAGE
                if (_isLoading.value) {
                    _isLoading.value = false
                    Log.d(TAG, "⚡ Loading arrêté après mise à jour défis - Prêt pour affichage")
                }

                Log.d(TAG, "✅ Défi actuel: ${todayChallenge?.challengeKey ?: "Aucun"}")
            }

        // 🔑 LISTENER SETTINGS
        settingsListener = firestore.collection(COLLECTION_SETTINGS)
            .document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener settings défis: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot?.exists() == true) {
                    val settings = DailyChallengeSettings.fromFirestore(snapshot)
                    _currentSettings.value = settings
                    Log.d(TAG, "✅ Settings défis mis à jour: jour ${settings?.currentDay}")
                }
            }
    }

    // MARK: - Génération de Défis

    /**
     * 📅 Génération du défi d'aujourd'hui si nécessaire
     * Utilise la Cloud Function Firebase existante
     * ✅ VERSION CACHE-FIRST : Ne bloque plus l'UI
     */
    private suspend fun generateTodayChallengeIfNeeded(coupleId: String) {
        try {
            // 🔍 Vérifier si défi existe déjà pour aujourd'hui
            val existingChallenge = _currentChallenge.value
            if (existingChallenge?.isToday() == true) {
                Log.d(TAG, "ℹ️ Défi d'aujourd'hui déjà existant")
                return
            }

            val settings = _currentSettings.value
            val expectedDay = if (settings != null) {
                settings.calculateExpectedDay()
            } else {
                // 🔥 Si pas de settings, commencer au jour 1 (Cloud Function les créera)
                Log.w(TAG, "⚠️ Settings non disponibles - démarrage jour 1")
                1
            }
            
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            Log.d(TAG, "🔄 Génération défi jour $expectedDay...")

            // 🔑 APPEL CLOUD FUNCTION
            val data = hashMapOf(
                "coupleId" to coupleId,
                "userId" to currentUserId,
                "challengeDay" to expectedDay,
                "timezone" to TimeZone.getDefault().id
            )

            val result = functions.getHttpsCallable("generateDailyChallenge")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val resultData = result.data as? Map<String, Any>
            val success = resultData?.get("success") as? Boolean ?: false
            val message = resultData?.get("message") as? String ?: ""

            if (success) {
                Log.d(TAG, "✅ Défi généré avec succès: $message")
                
                // 📊 Analytics optionnel
                // analyticsService.logEvent("daily_challenge_generated")
            } else {
                Log.e(TAG, "❌ Erreur génération défi: $message")
                _errorMessage.value = "Erreur génération: $message"
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur génération défi: ${e.message}")
            _errorMessage.value = "Erreur lors de la génération: ${e.message}"
        }
    }

    /**
     * 🔄 Force la regénération d'un défi (retry)
     */
    suspend fun regenerateCurrentChallenge(): Result<Unit> {
        return try {
            val coupleId = this.currentCoupleId
                ?: return Result.failure(Exception("Couple ID manquant"))

            _isLoading.value = true
            _errorMessage.value = null

            generateTodayChallengeIfNeeded(coupleId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur regénération défi: ${e.message}")
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    // MARK: - Challenge Completion

    /**
     * ✅ Marquer un défi comme complété
     * Sauvegarde dans Firestore avec gestion optimiste
     */
    suspend fun markChallengeAsCompleted(challengeId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            Log.d(TAG, "✅ Marquage défi comme complété: $challengeId")

            // 🔑 METTRE À JOUR FIRESTORE
            firestore.collection(COLLECTION_DAILY_CHALLENGES)
                .document(challengeId)
                .update(
                    mapOf(
                        "isCompleted" to true,
                        "completedAt" to com.google.firebase.Timestamp.now(),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            // 📊 Analytics
            // analyticsService.logEvent("daily_challenge_completed", challengeId)

            Log.d(TAG, "✅ Défi marqué comme complété: $challengeId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur completion défi: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * ❌ Marquer un défi comme non complété
     */
    suspend fun markChallengeAsNotCompleted(challengeId: String): Result<Unit> {
        return try {
            Log.d(TAG, "❌ Annulation completion défi: $challengeId")

            firestore.collection(COLLECTION_DAILY_CHALLENGES)
                .document(challengeId)
                .update(
                    mapOf(
                        "isCompleted" to false,
                        "completedAt" to null,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            Log.d(TAG, "✅ Défi marqué comme non complété: $challengeId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur unmark completion: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 🔄 Toggle completion d'un défi
     */
    suspend fun toggleChallengeCompletion(challengeId: String): Result<Unit> {
        val currentChallenge = _currentChallenge.value
            ?: return Result.failure(Exception("Défi actuel introuvable"))

        return if (currentChallenge.isCompleted) {
            markChallengeAsNotCompleted(challengeId)
        } else {
            markChallengeAsCompleted(challengeId)
        }
    }

    // MARK: - Settings et Préférences

    /**
     * 🔧 Chargement/création des settings couple
     */
    private suspend fun loadOrCreateSettings(coupleId: String) {
        try {
            val settingsDoc = firestore.collection(COLLECTION_SETTINGS)
                .document(coupleId)
                .get()
                .await()

            if (settingsDoc.exists()) {
                val settings = DailyChallengeSettings.fromFirestore(settingsDoc)
                _currentSettings.value = settings
                Log.d(TAG, "✅ Settings défis existants chargés")
            } else {
                // Settings seront créés par la Cloud Function lors de la première génération
                Log.d(TAG, "ℹ️ Pas de settings défis existants - seront créés automatiquement")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement settings défis: ${e.message}")
        }
    }

    /**
     * 📖 Marquer l'intro comme vue
     */
    fun markIntroAsSeen() {
        val userId = currentUserId ?: return
        val key = "daily_challenge_intro_seen_$userId"
        
        // Sauvegarder dans SharedPreferences
        val sharedPrefs = context.getSharedPreferences("daily_challenge_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(key, true).apply()
        
        _hasSeenIntro.value = true
        Log.d(TAG, "✅ Intro défis marquée comme vue")
    }

    /**
     * 📖 Chargement status intro
     */
    private fun loadIntroStatus() {
        val userId = currentUserId ?: return
        val key = "daily_challenge_intro_seen_$userId"
        
        val sharedPrefs = context.getSharedPreferences("daily_challenge_prefs", Context.MODE_PRIVATE)
        val hasSeenIntro = sharedPrefs.getBoolean(key, false)
        
        _hasSeenIntro.value = hasSeenIntro
        Log.d(TAG, "📖 Status intro défis chargé: $hasSeenIntro")
    }

    /**
     * 🔄 Reset des erreurs
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 📊 Informations de debug
     */
    fun getDebugInfo(): String {
        return """
            📊 DEBUG DailyChallengeRepository:
            - Couple ID: ${currentCoupleId ?: "Non défini"}
            - User ID: ${currentUserId ?: "Non défini"} 
            - Défi actuel: ${_currentChallenge.value?.challengeKey ?: "Aucun"}
            - Historique: ${_challengeHistory.value.size} défis
            - Settings: ${_currentSettings.value?.currentDay ?: "Non chargé"}
            - A vu intro: ${_hasSeenIntro.value}
            - En chargement: ${_isLoading.value}
            - Erreur: ${_errorMessage.value ?: "Aucune"}
        """.trimIndent()
    }

    /**
     * 🧹 Nettoyage des ressources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage DailyChallengeRepository")
        
        challengeListener?.remove()
        settingsListener?.remove()
        
        challengeListener = null
        settingsListener = null
    }
}
