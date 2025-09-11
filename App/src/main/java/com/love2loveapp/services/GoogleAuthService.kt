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
        
        // Remplacez par votre Web Client ID depuis Firebase Console
        private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID_FROM_FIREBASE_CONSOLE"
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
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
            
        googleSignInClient = GoogleSignIn.getClient(context, gso)
        Log.d("GoogleAuthService", "Google Sign-In client initialisé")
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
                Log.d("GoogleAuthService", "✅ Authentification réussie: ${result.user?.displayName}")
                _authError.value = null
                
                // Créer un document utilisateur partiel si c'est un nouvel utilisateur
                if (result.additionalUserInfo?.isNewUser == true) {
                    Log.d("GoogleAuthService", "📝 Nouvel utilisateur détecté")
                    createPartialUserDocument(result.user!!)
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
        Log.d("GoogleAuthService", "📝 Création document utilisateur partiel")
        
        // TODO: Intégrer avec votre FirebaseService pour créer le document utilisateur
        // Exemple de données à sauvegarder:
        val userData = mapOf(
            "uid" to firebaseUser.uid,
            "name" to (firebaseUser.displayName ?: ""),
            "email" to (firebaseUser.email ?: ""),
            "photoURL" to (firebaseUser.photoUrl?.toString() ?: ""),
            "isNewUser" to true,
            "onboardingCompleted" to false,
            "createdAt" to System.currentTimeMillis()
        )
        
        Log.d("GoogleAuthService", "👤 Données utilisateur: $userData")
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
     * Efface les erreurs d'authentification
     */
    fun clearError() {
        _authError.value = null
    }
}

/**
 * Extension pour faciliter l'utilisation dans les Composables
 */
@androidx.compose.runtime.Composable
fun rememberGoogleAuthService(): GoogleAuthService {
    return GoogleAuthService.instance
}
