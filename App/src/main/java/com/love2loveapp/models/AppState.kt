package com.love2loveapp.models

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Flags d'intro pour Questions/Défis du Jour
 * Équivalent iOS IntroFlags
 */
data class IntroFlags(
    val dailyQuestion: Boolean = false,
    val dailyChallenge: Boolean = false
) {
    companion object {
        val DEFAULT = IntroFlags()
    }
}

/**
 * État global de l'application Love2Love
 */
class AppState(private val context: android.content.Context) {
    // === États d'authentification ===
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    // === États d'onboarding ===
    private val _isOnboardingInProgress = MutableStateFlow(false)
    val isOnboardingInProgress: StateFlow<Boolean> = _isOnboardingInProgress.asStateFlow()
    
    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()
    
    // === États de navigation ===
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Welcome)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()
    
    // === État pour tests/debug ===
    private val _forceWelcomeScreen = MutableStateFlow(false)
    val forceWelcomeScreen: StateFlow<Boolean> = _forceWelcomeScreen.asStateFlow()
    
    // === Services ===
    private val _freemiumManager = MutableStateFlow<FreemiumManager?>(null)
    val freemiumManager: StateFlow<FreemiumManager?> = _freemiumManager.asStateFlow()
    
    // === IntroFlags pour Questions/Défis du Jour ===
    private val _introFlags = MutableStateFlow(IntroFlags())
    val introFlags: StateFlow<IntroFlags> = _introFlags.asStateFlow()
    
    // 🔧 Initialisation automatique au démarrage
    init {
        // Charger les intro flags existants au démarrage (pour les sessions persistantes)
        loadIntroFlagsAtStartup()
    }
    
    /**
     * Définit le FreemiumManager pour l'application
     */
    fun setFreemiumManager(manager: FreemiumManager) {
        _freemiumManager.value = manager
        android.util.Log.d("AppState", "✅ FreemiumManager défini: ${manager.javaClass.simpleName}")
    }
    
    // === Méthodes publiques ===
    
    fun setAuthenticated(isAuthenticated: Boolean, user: User? = null) {
        _isAuthenticated.value = isAuthenticated
        _currentUser.value = user
        
        // Si un utilisateur est défini, sauvegarder et charger les intro flags
        if (isAuthenticated && user != null) {
            saveUserData(user)
            // 🔑 CHARGER LES INTRO FLAGS - Fixed !
            loadIntroFlags()
        }
    }
    
    private fun saveUserData(user: User) {
        // Cette méthode sera appelée depuis AppDelegate
        // Pour l'instant, on log juste
        android.util.Log.d("AppState", "Utilisateur à sauvegarder: ${user.name}")
    }
    
    fun showWelcomeScreen() {
        _currentScreen.value = AppScreen.Welcome
    }
    
    /**
     * Force l'affichage de la page de bienvenue (pour tests/debug)
     */
    fun forceShowWelcomeScreen() {
        _forceWelcomeScreen.value = true
        _currentScreen.value = AppScreen.Welcome
        android.util.Log.d("AppState", "🧪 Force affichage WelcomeScreen pour test")
    }
    
    fun startOnboarding() {
        _isOnboardingInProgress.value = true
        _currentScreen.value = AppScreen.Onboarding
    }
    
    fun completeOnboarding() {
        _isOnboardingInProgress.value = false
        _isOnboardingCompleted.value = true
        _currentScreen.value = AppScreen.Main
    }
    
    fun completeOnboardingWithData(goals: List<String>, improvements: List<String>) {
        // Créer un utilisateur avec les données collectées
        val user = User(
            id = "user_${System.currentTimeMillis()}",
            _rawName = "Utilisateur Love2Love", // Nom par défaut
            relationshipGoals = goals,
            relationshipImprovement = improvements.joinToString(", "),
            onboardingInProgress = false
        )
        
        // Définir l'utilisateur et terminer l'onboarding
        setAuthenticated(true, user)
        completeOnboarding()
    }
    
    fun navigateToScreen(screen: AppScreen) {
        _currentScreen.value = screen
        android.util.Log.d("AppState", "🧭 Navigation vers: $screen")
    }
    
    /**
     * Met à jour le statut d'abonnement de l'utilisateur actuel
     * Utilisé par GooglePlayBillingService après un achat réussi
     */
    fun updateUserSubscriptionStatus(isSubscribed: Boolean) {
        val currentUser = _currentUser.value
        if (currentUser != null) {
            val updatedUser = currentUser.copy(isSubscribed = isSubscribed)
            _currentUser.value = updatedUser
            android.util.Log.d("AppState", "✅ Statut abonnement mis à jour: $isSubscribed")
        } else {
            android.util.Log.w("AppState", "⚠️ Impossible de mettre à jour l'abonnement - utilisateur null")
        }
    }
    
    /**
     * Met à jour les données utilisateur complètes
     * Inclut le démarrage du PartnerLocationService si nouveau partenaire détecté
     */
    fun updateUserData(user: User) {
        val previousUser = _currentUser.value
        _currentUser.value = user
        android.util.Log.d("AppState", "✅ Données utilisateur mises à jour: ${user.name}, partenaire: ${user.partnerId != null}")
        
        // 🚨 FILET SÉCURITÉ: Forcer démarrage PartnerLocationService si partenaire détecté
        if (user.partnerId != null && (previousUser?.partnerId != user.partnerId)) {
            android.util.Log.d("AppState", "🚨 FILET SÉCURITÉ: Nouveau partenaire détecté (${user.partnerId}) - Force démarrage PartnerLocationService")
            try {
                // Délai court pour s'assurer que AppDelegate.partnerLocationService est initialisé
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    com.love2loveapp.AppDelegate.partnerLocationService?.let { service ->
                        service.startAutoSyncIfPartnerExists()
                        android.util.Log.d("AppState", "✅ FILET SÉCURITÉ: PartnerLocationService démarré depuis AppState")
                    } ?: android.util.Log.e("AppState", "❌ FILET SÉCURITÉ: PartnerLocationService NULL depuis AppState")
                }, 500) // 500ms de délai
            } catch (e: Exception) {
                android.util.Log.e("AppState", "❌ FILET SÉCURITÉ: Erreur démarrage PartnerLocationService", e)
            }
        }
    }
    
    
    /**
     * Déconnexion complète - nettoie tous les états
     */
    fun signOut() {
        _isAuthenticated.value = false
        _currentUser.value = null
        _isOnboardingCompleted.value = false
        _isOnboardingInProgress.value = false
        _currentScreen.value = AppScreen.Welcome
        android.util.Log.d("AppState", "🚪 Déconnexion complète")
    }
    
    /**
     * 🌍 Mise à jour localisation utilisateur (pour UnifiedLocationService)
     * Synchronise la localisation dans l'état global comme iOS
     */
    fun updateUserLocation(userLocation: UserLocation) {
        val currentUser = _currentUser.value
        if (currentUser != null) {
            val updatedUser = currentUser.copy(currentLocation = userLocation)
            _currentUser.value = updatedUser
            android.util.Log.d("AppState", "🌍 Localisation utilisateur mise à jour: ${userLocation.displayName}")
        } else {
            android.util.Log.w("AppState", "⚠️ Impossible de mettre à jour localisation: utilisateur null")
        }
    }
    
    /**
     * Suppression de compte - équivalent iOS deleteAccount()
     * Nettoie tous les états et redirige vers la page de bienvenue
     */
    fun deleteAccount() {
        _isAuthenticated.value = false
        _currentUser.value = null
        _isOnboardingCompleted.value = false
        _isOnboardingInProgress.value = false
        _currentScreen.value = AppScreen.Welcome
        android.util.Log.d("AppState", "🗑️ Suppression compte - redirection vers page de bienvenue")
    }
    
    // === Méthodes IntroFlags ===
    
    /**
     * Marquer l'intro Questions du Jour comme vue
     * Équivalent iOS markDailyQuestionIntroAsSeen()
     */
    fun markDailyQuestionIntroAsSeen() {
        _introFlags.value = _introFlags.value.copy(dailyQuestion = true)
        
        // 🔑 SAUVEGARDE PERSISTANTE - Fixed !
        val userId = _currentUser.value?.id ?: return
        val sharedPrefs = context.getSharedPreferences("app_intro_flags", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("daily_question_intro_seen_$userId", true).apply()
        
        android.util.Log.d("AppState", "✅ Intro Questions du Jour marquée comme vue ET sauvegardée")
    }
    
    /**
     * Marquer l'intro Défis du Jour comme vue
     * Équivalent iOS markDailyChallengeIntroAsSeen()
     */
    fun markDailyChallengeIntroAsSeen() {
        _introFlags.value = _introFlags.value.copy(dailyChallenge = true)
        
        // 🔑 SAUVEGARDE PERSISTANTE - Fixed !
        val userId = _currentUser.value?.id ?: return
        val sharedPrefs = context.getSharedPreferences("app_intro_flags", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("daily_challenge_intro_seen_$userId", true).apply()
        
        android.util.Log.d("AppState", "✅ Intro Défis du Jour marquée comme vue ET sauvegardée")
    }
    
    /**
     * 📖 Charger les flags d'intro depuis SharedPreferences
     * À appeler au démarrage de l'app et quand l'utilisateur change
     */
    fun loadIntroFlags() {
        val userId = _currentUser.value?.id ?: return
        val sharedPrefs = context.getSharedPreferences("app_intro_flags", android.content.Context.MODE_PRIVATE)
        
        val dailyQuestionSeen = sharedPrefs.getBoolean("daily_question_intro_seen_$userId", false)
        val dailyChallengeSeen = sharedPrefs.getBoolean("daily_challenge_intro_seen_$userId", false)
        
        _introFlags.value = IntroFlags(
            dailyQuestion = dailyQuestionSeen,
            dailyChallenge = dailyChallengeSeen
        )
        
        android.util.Log.d("AppState", "📖 IntroFlags chargés: DQ=$dailyQuestionSeen, DC=$dailyChallengeSeen")
    }
    
    /**
     * 🚀 Charger les intro flags au démarrage de l'application
     * Tente de récupérer l'ID utilisateur depuis Firebase Auth ou SharedPreferences
     */
    private fun loadIntroFlagsAtStartup() {
        try {
            // 1. Essayer d'obtenir l'utilisateur depuis Firebase Auth
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            var userId = firebaseUser?.uid
            
            // 2. Si pas d'utilisateur Firebase, essayer SharedPreferences
            if (userId == null) {
                val prefs = context.getSharedPreferences("love2love_prefs", android.content.Context.MODE_PRIVATE)
                userId = prefs.getString("user_id", null)
            }
            
            // 3. Si on a un ID utilisateur, charger les intro flags
            if (userId != null) {
                val sharedPrefs = context.getSharedPreferences("app_intro_flags", android.content.Context.MODE_PRIVATE)
                
                val dailyQuestionSeen = sharedPrefs.getBoolean("daily_question_intro_seen_$userId", false)
                val dailyChallengeSeen = sharedPrefs.getBoolean("daily_challenge_intro_seen_$userId", false)
                
                _introFlags.value = IntroFlags(
                    dailyQuestion = dailyQuestionSeen,
                    dailyChallenge = dailyChallengeSeen
                )
                
                android.util.Log.d("AppState", "🚀 IntroFlags chargés au démarrage: DQ=$dailyQuestionSeen, DC=$dailyChallengeSeen (UserID: ${userId.take(8)}...)")
            } else {
                android.util.Log.d("AppState", "ℹ️ Aucun utilisateur trouvé au démarrage - IntroFlags restent par défaut")
            }
        } catch (e: Exception) {
            android.util.Log.e("AppState", "❌ Erreur chargement IntroFlags au démarrage: ${e.message}", e)
        }
    }
}

/**
 * Écrans de l'application
 */
enum class AppScreen {
    Launch,
    Welcome,
    Onboarding,
    Main,
    Authentication
}

/**
 * Modèle utilisateur complet pour Love2Love
 * 
 * Équivalent Android du modèle iOS avec mécanisme d'auto-génération de nom
 * identique au système iOS
 */
data class User(
    val id: String,
    private val _rawName: String = "", // Nom brut d'entrée (peut être vide)
    val email: String? = null,
    val partnerId: String? = null,
    val isSubscribed: Boolean = false,
    val relationshipGoals: List<String> = emptyList(),
    val relationshipDuration: String? = null,
    val relationshipImprovement: String? = null,
    val questionMode: String? = null,
    val onboardingInProgress: Boolean = false,
    // Nouvelles propriétés pour le header
    val profileImageURL: String? = null,
    val currentLocation: UserLocation? = null,
    // 📊 PROPRIÉTÉ POUR STATISTIQUES COUPLE
    val relationshipStartDate: java.util.Date? = null
) {
    
    /**
     * 🎯 Propriété calculée pour le nom avec auto-génération (équivalent iOS)
     * 
     * Si _rawName est vide, génère automatiquement :
     * - Français : "Utilisateur" + 4 premiers caractères UUID
     * - Anglais/Autre : "User" + 4 premiers caractères UUID
     */
    val name: String
        get() {
            return if (com.love2loveapp.utils.UserNameGenerator.isNameEmpty(_rawName)) {
                val generated = com.love2loveapp.utils.UserNameGenerator.generateAutomaticName(id)
                android.util.Log.d("User", "🎯 Auto-génération nom: '$generated' (ID: ${id.take(4)})")
                generated
            } else {
                _rawName.trim()
            }
        }
    
    companion object {
        /**
         * Factory method pour créer un User avec nom standard
         * (compatibilité avec le code existant)
         */
        fun create(
            id: String,
            name: String,
            email: String? = null,
            partnerId: String? = null,
            isSubscribed: Boolean = false,
            relationshipGoals: List<String> = emptyList(),
            relationshipDuration: String? = null,
            relationshipImprovement: String? = null,
            questionMode: String? = null,
            onboardingInProgress: Boolean = false,
            profileImageURL: String? = null,
            currentLocation: UserLocation? = null,
            relationshipStartDate: java.util.Date? = null
        ): User {
            return User(
                id = id,
                _rawName = name,
                email = email,
                partnerId = partnerId,
                isSubscribed = isSubscribed,
                relationshipGoals = relationshipGoals,
                relationshipDuration = relationshipDuration,
                relationshipImprovement = relationshipImprovement,
                questionMode = questionMode,
                onboardingInProgress = onboardingInProgress,
                profileImageURL = profileImageURL,
                currentLocation = currentLocation,
                relationshipStartDate = relationshipStartDate
            )
        }
    }
}

/**
 * FreemiumManager - Interface pour la gestion freemium
 * 
 * Équivalent iOS avec toutes les méthodes nécessaires pour le système de cartes
 */
interface FreemiumManager {
    val showingSubscription: Boolean
    val showingSubscriptionFlow: kotlinx.coroutines.flow.Flow<Boolean>
    
    // Méthodes principales (équivalent iOS)
    fun handleCategoryTap(category: QuestionCategory, onSuccess: () -> Unit)
    fun handleQuestionAccess(questionIndex: Int, category: QuestionCategory, onSuccess: () -> Unit)
    fun getMaxFreeQuestions(category: QuestionCategory): Int
    fun dismissSubscription()
}

