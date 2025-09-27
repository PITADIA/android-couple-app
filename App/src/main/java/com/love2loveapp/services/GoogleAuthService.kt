package com.love2loveapp.services

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Service d'authentification Google pour remplacer Apple Sign-In
 * Adapté pour Android avec Firebase Auth et Google Sign-In
 */
class GoogleAuthService private constructor() {
    
    private val auth: FirebaseAuth = Firebase.auth
    private var googleSignInClient: GoogleSignInClient? = null
    
    // États observables par Compose
    private val _isProcessingAuth = MutableStateFlow(false)
    val isProcessingAuth: StateFlow<Boolean> = _isProcessingAuth.asStateFlow()
    
    private val _isAuthenticated = MutableStateFlow(auth.currentUser != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()
    
    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()
    
    companion object {
        val instance: GoogleAuthService by lazy { GoogleAuthService() }
        
        // Web Client ID généré automatiquement par google-services.json
        // private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID_FROM_FIREBASE_CONSOLE" // Remplacé par getString
    }
    
    init {
        // Observer les changements d'état d'authentification Firebase
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _isAuthenticated.value = user != null
            _currentUser.value = user
            Log.d("GoogleAuthService", "État d'auth changé: ${if (user != null) "connecté" else "déconnecté"}")
        }
    }
    
    /**
     * Initialise le client Google Sign-In
     * À appeler depuis onCreate() de votre Activity
     */
    fun initialize(context: Context) {
        // Récupération automatique du Web Client ID depuis google-services.json
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) {
            Log.w("GoogleAuthService", "⚠️ default_web_client_id introuvable, utilisation du fallback")
            val fallback = "200633504634-lac9rcnr96r84p100ndj16vlq7deubm9.apps.googleusercontent.com"
            initializeClient(context, fallback)
            return
        }
        val webClientId = context.getString(resId)
        
        Log.d("GoogleAuthService", "🔑 Web Client ID: $webClientId")
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
            
        googleSignInClient = GoogleSignIn.getClient(context, gso)
        Log.d("GoogleAuthService", "Google Sign-In client initialisé")
    }

    /** Helper utilisé pour le fallback */
    private fun initializeClient(context: Context, clientId: String) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
        Log.d("GoogleAuthService", "Google Sign-In client initialisé (fallback)")
    }
    
    /**
     * Lance le processus de connexion Google
     * Retourne l'Intent pour startActivityForResult
     */
    fun getSignInIntent(): Intent? {
        val client = googleSignInClient
        if (client == null) {
            Log.e("GoogleAuthService", "Client Google Sign-In non initialisé")
            _authError.value = "Service non initialisé"
            return null
        }
        
        _isProcessingAuth.value = true
        _authError.value = null
        Log.d("GoogleAuthService", "🔐 Lancement Google Sign-In")
        
        return client.signInIntent
    }
    
    /**
     * Traite le résultat de l'activité Google Sign-In
     * À appeler depuis onActivityResult
     */
    suspend fun handleSignInResult(data: Intent?): Boolean {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            if (account != null) {
                firebaseAuthWithGoogle(account)
            } else {
                _isProcessingAuth.value = false
                _authError.value = "Échec de la connexion Google"
                false
            }
        } catch (e: ApiException) {
            _isProcessingAuth.value = false
            Log.e("GoogleAuthService", "Erreur Google Sign-In: ${e.statusCode}", e)
            _authError.value = "Erreur de connexion: ${e.localizedMessage}"
            false
        }
    }
    
    /**
     * Authentifie avec Firebase en utilisant le compte Google
     */
    private suspend fun firebaseAuthWithGoogle(account: GoogleSignInAccount): Boolean {
        return try {
            Log.d("GoogleAuthService", "🔥 Authentification Firebase avec Google")
            
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            
            _isProcessingAuth.value = false
            
            if (result.user != null) {
                Log.d("GoogleAuthService", "✅ Authentification réussie: [USER_MASKED]")
                // 🛡️ Activer immédiatement la protection anti-déconnexion (avant tout accès Firestore)
                com.love2loveapp.AppDelegate.userDataIntegrationService?.suppressAccountDeletionDetectionTemporarily()
                _authError.value = null
                
                // Gérer nouveaux utilisateurs ET utilisateurs sans document Firestore
                if (result.additionalUserInfo?.isNewUser == true) {
                    Log.d("GoogleAuthService", "📝 Nouvel utilisateur détecté")
                    createPartialUserDocument(result.user!!)
                } else {
                    Log.d("GoogleAuthService", "🔄 Utilisateur existant - vérification document Firestore")
                    verifyAndCreateDocumentIfNeeded(result.user!!)
                }
                
                true
            } else {
                Log.e("GoogleAuthService", "❌ Authentification Firebase échouée")
                _authError.value = "Échec de l'authentification"
                false
            }
        } catch (e: Exception) {
            _isProcessingAuth.value = false
            Log.e("GoogleAuthService", "❌ Erreur Firebase Auth", e)
            _authError.value = "Erreur d'authentification: ${e.localizedMessage}"
            false
        }
    }
    
    /**
     * Crée un document utilisateur partiel pour les nouveaux utilisateurs
     */
    private fun createPartialUserDocument(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        Log.d("GoogleAuthService", "🔥 Création profil utilisateur vide (équivalent iOS)")
        
        // 🛡️ PROTECTION - Désactiver détection suppression temporairement (selon rapport iOS)
        com.love2loveapp.AppDelegate.userDataIntegrationService?.suppressAccountDeletionDetectionTemporarily()
        
        // Données minimales pour nouvel utilisateur (selon document iOS)
        val userData = mapOf(
            "id" to java.util.UUID.randomUUID().toString(),
            "googleUserID" to firebaseUser.uid,
            "email" to (firebaseUser.email ?: ""),
            "name" to (firebaseUser.displayName ?: ""),
            "profileImageURL" to (firebaseUser.photoUrl?.toString() ?: ""),
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "lastLoginDate" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "onboardingInProgress" to true,
            "isSubscribed" to false,
            "partnerCode" to "",
            "partnerId" to "",
            "relationshipGoals" to emptyList<String>(),
            "relationshipDuration" to "notInRelationship"
        )
        
        // Sauvegarde Firestore avec merge (équivalent iOS)
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .document(firebaseUser.uid)
            .set(userData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("GoogleAuthService", "✅ Profil utilisateur vide créé avec succès")
            }
            .addOnFailureListener { error ->
                Log.e("GoogleAuthService", "❌ Erreur création profil: ${error.message}")
                _authError.value = "Erreur création profil: ${error.message}"
            }
    }
    
    /**
     * Vérifie l'existence du document Firestore et le crée si nécessaire
     * Équivalent iOS: loadUserData() avec fallback vers createEmptyUserProfile()
     */
    private fun verifyAndCreateDocumentIfNeeded(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        Log.d("GoogleAuthService", "🔍 Vérification existence document utilisateur: [USER_MASKED]")
        
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .document(firebaseUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Log.d("GoogleAuthService", "✅ Document utilisateur existant trouvé")
                } else {
                    Log.w("GoogleAuthService", "⚠️ Document utilisateur inexistant - création nécessaire")
                    Log.w("GoogleAuthService", "🚨 Scénario: Auth connecté mais Firestore vide = Compte possiblement supprimé")
                    
                    // 🛡️ PROTECTION - Désactiver détection suppression avant création (selon rapport iOS)
                    com.love2loveapp.AppDelegate.userDataIntegrationService?.suppressAccountDeletionDetectionTemporarily()
                    
                    createPartialUserDocument(firebaseUser)
                }
            }
            .addOnFailureListener { error ->
                Log.e("GoogleAuthService", "❌ Erreur vérification document: ${error.message}")
                Log.w("GoogleAuthService", "🔄 Création document par sécurité")
                createPartialUserDocument(firebaseUser)
            }
    }
    
    /**
     * Déconnecte l'utilisateur
     */
    suspend fun signOut() {
        try {
            // Déconnexion Firebase
            auth.signOut()
            
            // Déconnexion Google Sign-In
            googleSignInClient?.signOut()?.await()
            
            Log.d("GoogleAuthService", "🚪 Déconnexion réussie")
        } catch (e: Exception) {
            Log.e("GoogleAuthService", "Erreur lors de la déconnexion", e)
        }
    }
    
    /**
     * Vérifie si l'utilisateur est déjà connecté
     */
    fun checkExistingSignIn(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    /**
     * Obtient le nom d'affichage de l'utilisateur Google (équivalent à appleUserDisplayName)
     */
    fun getGoogleUserDisplayName(): String? {
        return auth.currentUser?.displayName
    }
    
    /**
     * Obtient l'email de l'utilisateur Google
     */
    fun getGoogleUserEmail(): String? {
        return auth.currentUser?.email
    }
    
    /**
     * Obtient l'URL de la photo de profil Google
     */
    fun getGoogleUserPhotoUrl(): String? {
        return auth.currentUser?.photoUrl?.toString()
    }
    
    /**
     * 👤 Authentification anonyme (compte invité)
     * Permet aux utilisateurs de créer un compte temporaire sans Google Sign-In
     */
    suspend fun signInAnonymously(): Boolean {
        return try {
            Log.d("GoogleAuthService", "👤 Début authentification anonyme")
            _isProcessingAuth.value = true
            _authError.value = null
            
            val result = auth.signInAnonymously().await()
            _isProcessingAuth.value = false
            
            if (result.user != null) {
                Log.d("GoogleAuthService", "✅ Authentification anonyme réussie: [USER_MASKED]")
                
                // 🛡️ Activer immédiatement la protection anti-déconnexion
                com.love2loveapp.AppDelegate.userDataIntegrationService?.suppressAccountDeletionDetectionTemporarily()
                
                // Créer un document utilisateur minimal pour les comptes anonymes
                createAnonymousUserDocument(result.user!!)
                
                true
            } else {
                Log.e("GoogleAuthService", "❌ Authentification anonyme échouée")
                _authError.value = "Échec de la création du compte invité"
                false
            }
        } catch (e: Exception) {
            _isProcessingAuth.value = false
            Log.e("GoogleAuthService", "❌ Erreur authentification anonyme", e)
            _authError.value = "Erreur création compte invité: ${e.localizedMessage}"
            false
        }
    }
    
    /**
     * Crée un document utilisateur minimal pour les comptes anonymes
     */
    private fun createAnonymousUserDocument(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        Log.d("GoogleAuthService", "👤 Création profil utilisateur invité")
        
        // 🛡️ PROTECTION - Désactiver détection suppression temporairement
        com.love2loveapp.AppDelegate.userDataIntegrationService?.suppressAccountDeletionDetectionTemporarily()
        
        // Données minimales pour utilisateur invité
        val userData = mapOf(
            "id" to java.util.UUID.randomUUID().toString(),
            "googleUserID" to firebaseUser.uid,
            "email" to "",  // Pas d'email pour les comptes anonymes
            "name" to "",  // 🔥 Nom vide pour utiliser le même mécanisme que Google Sign In
            "profileImageURL" to "",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "lastLoginDate" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "onboardingInProgress" to true,
            "isSubscribed" to false,
            "partnerCode" to "",
            "partnerId" to "",
            "relationshipGoals" to emptyList<String>(),
            "relationshipDuration" to "notInRelationship",
            "isAnonymous" to true  // 🔥 Marquer comme compte anonyme
        )
        
        // Sauvegarde Firestore avec merge
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .document(firebaseUser.uid)
            .set(userData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("GoogleAuthService", "✅ Profil utilisateur invité créé avec succès")
            }
            .addOnFailureListener { error ->
                Log.e("GoogleAuthService", "❌ Erreur création profil invité: ${error.message}")
                _authError.value = "Erreur création profil invité: ${error.message}"
            }
    }
    
    /**
     * 🔗 Lie un compte anonyme existant avec Google Sign-In
     * Permet à un utilisateur invité de sauvegarder son compte sans perdre ses données
     */
    suspend fun linkAnonymousWithGoogle(): Boolean {
        return try {
            val currentUser = auth.currentUser
            if (currentUser?.isAnonymous != true) {
                Log.w("GoogleAuthService", "⚠️ Tentative de liaison sur compte non-anonyme")
                return false
            }
            
            Log.d("GoogleAuthService", "🔗 Début liaison compte anonyme avec Google")
            _isProcessingAuth.value = true
            _authError.value = null
            
            // Obtenir le token Google via le flow standard
            val googleSignInClient = this.googleSignInClient ?: return false
            val signInTask = googleSignInClient.silentSignIn()
            
            val account = if (signInTask.isSuccessful) {
                signInTask.result
            } else {
                // Si silent sign-in échoue, il faut un flow interactif
                Log.w("GoogleAuthService", "⚠️ Liaison nécessite un flow interactif Google")
                _isProcessingAuth.value = false
                return false
            }
            
            // Créer les credentials Google
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            
            // Lier le compte anonyme avec Google
            val result = currentUser.linkWithCredential(credential).await()
            _isProcessingAuth.value = false
            
            if (result.user != null) {
                Log.d("GoogleAuthService", "✅ Liaison réussie - Compte anonyme → Google")
                Log.d("GoogleAuthService", "🎯 UID conservé: [USER_MASKED]")
                Log.d("GoogleAuthService", "📧 Email ajouté: [EMAIL_MASKED]")
                
                // Mettre à jour le document Firestore pour marquer comme non-anonyme
                updateUserDocumentAfterLinking(result.user!!)
                
                true
            } else {
                Log.e("GoogleAuthService", "❌ Liaison échouée")
                _authError.value = "Échec de la liaison avec Google"
                false
            }
            
        } catch (e: Exception) {
            _isProcessingAuth.value = false
            Log.e("GoogleAuthService", "❌ Erreur liaison compte anonyme", e)
            _authError.value = "Erreur liaison Google: ${e.localizedMessage}"
            false
        }
    }
    
    /**
     * Met à jour le document Firestore après liaison pour marquer comme non-anonyme
     */
    private fun updateUserDocumentAfterLinking(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        val updates = mapOf(
            "isAnonymous" to false,
            "email" to (firebaseUser.email ?: ""),
            "name" to (firebaseUser.displayName ?: "Utilisateur Invité"),
            "profileImageURL" to (firebaseUser.photoUrl?.toString() ?: ""),
            "lastLoginDate" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .document(firebaseUser.uid)
            .update(updates)
            .addOnSuccessListener {
                Log.d("GoogleAuthService", "✅ Document utilisateur mis à jour après liaison")
            }
            .addOnFailureListener { error ->
                Log.e("GoogleAuthService", "❌ Erreur mise à jour après liaison: ${error.message}")
            }
    }
    
    /**
     * Efface les erreurs d'authentification
     */
    fun clearError() {
        _authError.value = null
    }
    
    /**
     * Réauthentifie l'utilisateur actuel pour les opérations sensibles
     * Requis pour la suppression de compte selon Firebase
     */
    suspend fun reauthenticate(): Boolean {
        return try {
            val currentUser = auth.currentUser ?: return false
            
            Log.d("GoogleAuthService", "🔄 Réauthentification pour opération sensible")
            
            // Obtenir le dernier compte Google connecté
            val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(
                googleSignInClient?.applicationContext ?: return false
            )
            
            if (lastSignedInAccount?.idToken != null) {
                // Créer les credentials avec le token existant
                val credential = GoogleAuthProvider.getCredential(lastSignedInAccount.idToken, null)
                
                // Réauthentifier avec Firebase
                currentUser.reauthenticate(credential).await()
                
                Log.d("GoogleAuthService", "✅ Réauthentification réussie")
                true
            } else {
                Log.w("GoogleAuthService", "⚠️ Token Google non disponible pour réauthentification")
                false
            }
        } catch (e: Exception) {
            Log.e("GoogleAuthService", "❌ Erreur réauthentification", e)
            _authError.value = "Réauthentification requise: ${e.localizedMessage}"
            false
        }
    }
}

/**
 * Extension pour faciliter l'utilisation dans les Composables
 */
@androidx.compose.runtime.Composable
fun rememberGoogleAuthService(): GoogleAuthService {
    return GoogleAuthService.instance
}
