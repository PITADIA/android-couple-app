package com.love2loveapp.services.integration

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.AppState
import com.love2loveapp.models.User
import com.love2loveapp.models.UserLocation
import kotlinx.coroutines.*

/**
 * 🔄 UserDataIntegrationService - Intégration données Firebase avec AppState local
 * 
 * Responsabilités:
 * - Synchroniser les données utilisateur Firebase avec AppState
 * - Écouter les changements temps réel
 * - Démarrer/arrêter les services selon l'état d'authentification
 * - Conversion entre modèles Firebase et modèles locaux
 */
class UserDataIntegrationService private constructor(
    private val appState: AppState
) {
    
    // 🛡️ Protection temporaire contre la détection de suppression (selon rapport iOS)
    private var suppressAccountDeletionDetection = false
    private val suppressionTimeoutMs = 15000L // 15 secondes comme recommandé par dev iOS
    
    companion object {
        private const val TAG = "UserDataIntegration"
        private const val USERS_COLLECTION = "users"
        
        @Volatile
        private var instance: UserDataIntegrationService? = null
        
        fun getInstance(appState: AppState): UserDataIntegrationService {
            return instance ?: synchronized(this) {
                instance ?: UserDataIntegrationService(appState).also { instance = it }
            }
        }
    }
    
    // Firebase services
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Listeners Firebase
    private var userDataListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    
    /**
     * 🚀 Initialise l'intégration Firebase
     */
    fun initialize() {
        Log.d(TAG, "🚀 Initialisation UserDataIntegrationService")
        
        // Écouter les changements d'authentification
        setupAuthStateListener()
        
        // Si déjà authentifié, démarrer immédiatement
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "👤 Utilisateur déjà connecté: [USER_MASKED]")
            startUserDataSync(currentUser.uid)
        } else {
            Log.d(TAG, "ℹ️ Aucun utilisateur connecté")
        }
    }
    
    /**
     * 🛡️ Supprime temporairement la détection de suppression de compte
     * Utilisé pendant la création de nouveau profil utilisateur
     * Équivalent à la logique iOS décrite dans RAPPORT_PROBLEME_PERMISSIONS_FIRESTORE.md
     */
    fun suppressAccountDeletionDetectionTemporarily(durationMs: Long = suppressionTimeoutMs) {
        Log.d(TAG, "🛡️ Activation protection suppression pour ${durationMs}ms")
        suppressAccountDeletionDetection = true
        
        // Auto-réactivation après timeout
        serviceScope.launch {
            delay(durationMs)
            suppressAccountDeletionDetection = false
            Log.d(TAG, "🛡️ Protection suppression désactivée après timeout")
        }
    }
    
    /**
     * 🔐 Configure l'écoute des changements d'authentification
     */
    private fun setupAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            
            if (firebaseUser != null) {
                Log.d(TAG, "🔐 Utilisateur connecté: [USER_MASKED]")
                
                // Marquer comme authentifié dans AppState
                appState.setAuthenticated(true, null) // Sera mis à jour par le listener Firestore
                
                // 🔑 FLUSH TOKEN FCM POUR NOTIFICATIONS - Fixed !
                // AppState a maintenant un context - utilisons-le
                com.love2loveapp.MyFirebaseMessagingService.flushStashedTokenIfAny(AppDelegate.getInstance().applicationContext)
                
                // Démarrer la synchronisation des données
                startUserDataSync(firebaseUser.uid)
                
                // Démarrer les services de localisation et abonnement via AppDelegate
                serviceScope.launch {
                    try {
                        // 📍 Démarrer EXCLUSIVEMENT le service de localisation robuste (éviter conflits GPS)
                        AppDelegate.getInstance().startLocationServicesIfAuthenticated()
                        Log.d(TAG, "📍 Service de localisation robuste démarré (GPS exclusif)")
                        
                        // 👥 CRITIQUE: Démarrer PartnerLocationService avec logs détaillés
                        Log.d(TAG, "🚀 DÉMARRAGE CRITIQUE PartnerLocationService...")
                        AppDelegate.partnerLocationService?.let { service ->
                            service.startAutoSyncIfPartnerExists()
                            Log.d(TAG, "✅ PartnerLocationService.startAutoSyncIfPartnerExists() appelé")
                        } ?: Log.e(TAG, "❌ AppDelegate.partnerLocationService est NULL!")
                        
                        // 🤝 Démarrer la synchronisation des abonnements partenaires
                        AppDelegate.partnerSubscriptionSyncService?.startListeningForUser()
                        Log.d(TAG, "🤝 Service de partage d'abonnements démarré")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur démarrage services: ${e.message}", e)
                    }
                }
                
                // 🛫 Navigation sera décidée par le listener Firestore selon onboardingInProgress
                Log.d(TAG, "🚀 Authentification détectée - attente données Firestore pour navigation")
                
            } else {
                Log.d(TAG, "🔓 Utilisateur déconnecté")
                
                // Marquer comme non authentifié
                appState.setAuthenticated(false, null)
                
                // Arrêter la synchronisation
                stopUserDataSync()
                
                // Arrêter les services de localisation et abonnement
                AppDelegate.locationSyncService?.stopLocationSync()
                AppDelegate.partnerLocationService?.stopSync()
                
                // 🤝 Arrêter la synchronisation des abonnements partenaires
                AppDelegate.partnerSubscriptionSyncService?.stopAllListeners()
                Log.d(TAG, "🤝 Service de partage d'abonnements arrêté")
                
                // Navigation vers Welcome (utilisateur non authentifié)
                Log.d(TAG, "🚀 Navigation automatique vers Welcome - utilisateur non authentifié")
                appState.navigateToScreen(com.love2loveapp.models.AppScreen.Welcome)
            }
        }
        
        auth.addAuthStateListener(authStateListener!!)
    }
    
    /**
     * 📊 Démarre la synchronisation des données utilisateur
     */
    private fun startUserDataSync(userId: String) {
        Log.d(TAG, "📊 Démarrage sync données utilisateur: [USER_MASKED]")
        
        // Arrêter l'ancien listener s'il existe
        stopUserDataSync()
        
        // Créer un listener temps réel sur le document utilisateur
        userDataListener = db.collection(USERS_COLLECTION)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Erreur listener données utilisateur: ${error.message}", error)
                    
                    // 🚨 DÉTECTION SUPPRESSION - Erreur de permissions
                    // Équivalent iOS: if error.localizedDescription.contains("permissions")
                    if (error.message?.contains("permissions", ignoreCase = true) == true ||
                        error.message?.contains("Missing or insufficient permissions", ignoreCase = true) == true) {
                        
                        // 🛡️ PROTECTION - Vérifier si détection temporairement désactivée
                        if (suppressAccountDeletionDetection) {
                            Log.d(TAG, "🛡️ Erreur permissions mais protection active - création profil en cours")
                            return@addSnapshotListener
                        }
                        
                        Log.w(TAG, "🚨 DÉTECTION - Erreur permissions Firestore = Compte supprimé")
                        handleAccountDeletionDetected(userId)
                    }
                    
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val firestoreData = snapshot.data ?: return@addSnapshotListener
                        
                        // Convertir les données Firestore en modèle User local
                        val user = convertFirestoreToUser(userId, firestoreData)
                        
                        // 🔥 CRITIQUE: Mettre à jour AppState avec détection partenaire
                        appState.setAuthenticated(true, null) // Authentifier d'abord
                        appState.updateUserData(user) // Puis mettre à jour données avec détection partenaire
                        
                        // Sauvegarder dans SharedPreferences pour persistance
                        saveUserToSharedPreferences(user)
                        
                        Log.d(TAG, "✅ Données utilisateur mises à jour: [USER_MASKED]")
                        
                        // 🛫 Navigation intelligente basée sur l'état d'onboarding et d'abonnement
                        if (user.onboardingInProgress) {
                            // Utilisateur avec onboarding en cours
                            if (user.isSubscribed) {
                                // Utilisateur payant : aller directement à l'app principale
                                Log.d(TAG, "💳 Utilisateur payant avec onboarding en cours - direction MainScreen")
                                appState.navigateToScreen(com.love2loveapp.models.AppScreen.Main)
                            } else {
                                // 🔥 CORRECTION: Ne pas naviguer si on est déjà en train de faire l'onboarding
                                val currentScreen = appState.currentScreen.value
                                if (currentScreen == com.love2loveapp.models.AppScreen.Welcome || 
                                    currentScreen == com.love2loveapp.models.AppScreen.Onboarding) {
                                    Log.d(TAG, "⏭️ Onboarding déjà en cours (écran: $currentScreen) - pas de navigation automatique")
                                } else {
                                    // Utilisateur non payant : afficher Welcome pour choix utilisateur
                                    Log.d(TAG, "🆓 Utilisateur non payant avec onboarding en cours - affichage Welcome")
                                    appState.navigateToScreen(com.love2loveapp.models.AppScreen.Welcome)
                                }
                            }
                        } else {
                            // Onboarding terminé, aller à l'app principale
                            Log.d(TAG, "🏠 Onboarding terminé - direction MainScreen")
                            appState.navigateToScreen(com.love2loveapp.models.AppScreen.Main)
                        }
                        
                        // Si localisation a changé, mettre à jour le service
                        user.currentLocation?.let { location ->
                            // La localisation sera gérée par LocationSyncService
                            Log.d(TAG, "📍 Localisation utilisateur: [LOCATION_MASKED]")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur parsing données utilisateur: ${e.message}", e)
                    }
                } else {
                    // 🛡️ PROTECTION - Vérifier si détection de suppression est temporairement désactivée
                    if (suppressAccountDeletionDetection) {
                        Log.d(TAG, "🛡️ Document inexistant mais protection active - création profil en cours")
                        return@addSnapshotListener
                    }
                    
                    // 🚨 DÉTECTION SUPPRESSION DE COMPTE
                    // Document Firestore inexistant mais Firebase Auth connecté = compte supprimé
                    Log.w(TAG, "🚨 DÉTECTION - Document utilisateur inexistant: $userId")
                    Log.w(TAG, "🚨 Firebase Auth connecté mais Firestore vide = Compte supprimé")
                    
                    handleAccountDeletionDetected(userId)
                }
            }
    }
    
    /**
     * ⏹️ Arrête la synchronisation des données utilisateur
     */
    private fun stopUserDataSync() {
        userDataListener?.remove()
        userDataListener = null
    }
    
    /**
     * 🚨 Gère la détection de suppression de compte
     * Équivalent iOS: handleAccountDeletionDetected()
     */
    private fun handleAccountDeletionDetected(userId: String) {
        // 🛡️ Si la protection est active, on NE fait RIEN
        if (suppressAccountDeletionDetection) {
            Log.d(TAG, "🛡️ Suppression détectée mais protection active - on ignore")
            return
        }
        
        Log.w(TAG, "🚨 Gestion suppression de compte détectée pour: $userId")
        
        serviceScope.launch {
            try {
                // 1. Nettoyer cache local (équivalent UserCacheManager.clearCache())
                clearUserCache()
                
                // 2. Déconnexion RevenueCat (si disponible)
                // TODO: Implémenter si RevenueCat est utilisé
                
                // 3. Réinitialiser AppState complet
                appState.deleteAccount()
                
                // 4. Forcer déconnexion Firebase Auth pour éviter les boucles
                auth.signOut()
                
                Log.w(TAG, "✅ Nettoyage complet après suppression de compte")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors du nettoyage après suppression: ${e.message}", e)
                
                // En cas d'erreur, forcer quand même la déconnexion
                try {
                    auth.signOut()
                } catch (signOutError: Exception) {
                    Log.e(TAG, "❌ Impossible de déconnecter Firebase Auth: ${signOutError.message}")
                }
            }
        }
    }
    
    /**
     * 🧹 Nettoie le cache local utilisateur
     * Équivalent iOS: UserCacheManager.clearCache()
     */
    private fun clearUserCache() {
        try {
            val context = AppDelegate.getInstance()
            val prefs = context.getSharedPreferences("love2love_prefs", android.content.Context.MODE_PRIVATE)
            
            // Nettoyer toutes les données utilisateur
            prefs.edit().clear().apply()
            
            // Nettoyer aussi le cache images si disponible
            AppDelegate.userCacheManager?.clearAllImageCache()
            
            Log.d(TAG, "🧹 Cache utilisateur nettoyé")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur nettoyage cache: ${e.message}", e)
        }
    }
    
    /**
     * 🔄 Convertit les données Firestore en modèle User local
     */
    private fun convertFirestoreToUser(userId: String, data: Map<String, Any>): User {
        // Extraction des données de base
        val name = data["name"] as? String ?: ""
        val email = data["email"] as? String
        val partnerId = data["partnerId"] as? String
        val isSubscribed = data["isSubscribed"] as? Boolean ?: false
        val relationshipGoals = (data["relationshipGoals"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val relationshipDuration = data["relationshipDuration"] as? String
        val relationshipImprovement = data["relationshipImprovement"] as? String
        val questionMode = data["questionMode"] as? String
        val onboardingInProgress = data["onboardingInProgress"] as? Boolean ?: false
        val profileImageURL = data["profileImageURL"] as? String
        
        // 📅 EXTRACTION RELATIONSHIP START DATE (CRUCIAL POUR STATISTIQUES)
        val relationshipStartDate = try {
            (data["relationshipStartDate"] as? com.google.firebase.Timestamp)?.toDate()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur parsing relationshipStartDate: ${e.message}", e)
            null
        }
        
        // Extraction de la localisation
        val locationData = data["currentLocation"] as? Map<String, Any>
        val currentLocation = locationData?.let { locData ->
            val latitude = locData["latitude"] as? Double
            val longitude = locData["longitude"] as? Double
            val address = locData["address"] as? String
            val city = locData["city"] as? String
            val country = locData["country"] as? String
            val lastUpdated = (locData["lastUpdated"] as? Number)?.toLong() ?: System.currentTimeMillis()
            
            if (latitude != null && longitude != null) {
                UserLocation(latitude, longitude, address, city, country, lastUpdated)
            } else null
        }
        
        return User(
            id = userId,
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
            relationshipStartDate = relationshipStartDate // 📊 AJOUT POUR STATISTIQUES
        )
    }
    
    /**
     * 💾 Sauvegarde les données utilisateur dans Firestore
     */
    fun saveUserData(user: User) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.w(TAG, "⚠️ Utilisateur non connecté, impossible de sauvegarder")
            return
        }
        
        serviceScope.launch {
            try {
                val firestoreData = convertUserToFirestore(user)
                
                db.collection(USERS_COLLECTION)
                    .document(currentUserId)
                    .set(firestoreData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Données utilisateur sauvegardées dans Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Erreur sauvegarde utilisateur: ${e.message}", e)
                    }
                    
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sauvegarde utilisateur: ${e.message}", e)
            }
        }
    }
    
    /**
     * 🔄 Convertit le modèle User local en données Firestore
     */
    private fun convertUserToFirestore(user: User): Map<String, Any?> {
        val firestoreData = mutableMapOf<String, Any?>()
        
        Log.d(TAG, "🎯 SAUVEGARDE FIRESTORE - DONNÉES USER:")
        Log.d(TAG, "  - user.id: '[MASKED]'")
        Log.d(TAG, "  - user.name (propriété calc): '[MASKED]'")
        
        // 🔥 PERSISTANCE: Sauvegarder le nom généré automatiquement en Firestore
        // pour éviter de régénérer à chaque chargement et maintenir la cohérence
        firestoreData["name"] = user.name
        firestoreData["email"] = user.email
        firestoreData["partnerId"] = user.partnerId
        firestoreData["isSubscribed"] = user.isSubscribed
        firestoreData["relationshipGoals"] = user.relationshipGoals
        firestoreData["relationshipDuration"] = user.relationshipDuration
        firestoreData["relationshipImprovement"] = user.relationshipImprovement
        firestoreData["questionMode"] = user.questionMode
        firestoreData["onboardingInProgress"] = user.onboardingInProgress
        firestoreData["profileImageURL"] = user.profileImageURL
        
        // 📅 RELATIONSHIP START DATE (CRUCIAL POUR STATISTIQUES)
        user.relationshipStartDate?.let { date ->
            firestoreData["relationshipStartDate"] = com.google.firebase.Timestamp(date)
        }
        
        // Localisation
        user.currentLocation?.let { location ->
            firestoreData["currentLocation"] = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "address" to location.address,
                "city" to location.city,
                "country" to location.country,
                "lastUpdated" to location.lastUpdated
            )
        }
        
        return firestoreData
    }
    
    /**
     * 💾 Sauvegarde les données utilisateur dans SharedPreferences pour persistance
     */
    private fun saveUserToSharedPreferences(user: User) {
        try {
            val context = AppDelegate.getInstance()
            val prefs = context.getSharedPreferences("love2love_prefs", android.content.Context.MODE_PRIVATE)
            
            prefs.edit().apply {
                putBoolean("onboarding_completed", true)
                putString("user_id", user.id)
                putString("user_name", user.name)
                putString("user_email", user.email)
                putString("user_partner_id", user.partnerId)
                putBoolean("user_is_subscribed", user.isSubscribed)
                putStringSet("user_goals", user.relationshipGoals.toSet())
                putString("user_duration", user.relationshipDuration)
                putString("user_improvement", user.relationshipImprovement)
                putString("user_question_mode", user.questionMode)
                putBoolean("user_onboarding_in_progress", user.onboardingInProgress)
                apply()
            }
            
            Log.d(TAG, "💾 Données utilisateur sauvegardées dans SharedPreferences")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur sauvegarde SharedPreferences: ${e.message}", e)
        }
    }
    
    /**
     * 🧹 Nettoie les ressources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage UserDataIntegrationService")
        
        authStateListener?.let { listener ->
            auth.removeAuthStateListener(listener)
        }
        authStateListener = null
        
        stopUserDataSync()
        serviceScope.cancel()
    }
}
