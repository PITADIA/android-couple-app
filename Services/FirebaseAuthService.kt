package com.love2loveapp.services.firebase

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.love2loveapp.model.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

/**
 * Service d'authentification Firebase - Google Sign-In uniquement
 * Responsable de la gestion de l'authentification et de la création de profils utilisateur
 */
object FirebaseAuthService {
    private const val TAG = "FirebaseAuthService"

    // Firebase instances
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Google Sign-In
    private var googleClient: GoogleSignInClient? = null

    // Observable state
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // === Google Sign-In Configuration ===
    fun initGoogleSignIn(context: Context) {
        if (googleClient != null) return
        
        val webClientId = context.getString(
            context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        )
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(context, gso)
        Log.d(TAG, "🔐 GoogleSignIn initialisé")
    }

    fun getGoogleSignInIntent(context: Context): Intent {
        initGoogleSignIn(context)
        return googleClient!!.signInIntent
    }

    // === Authentication Flow ===
    fun handleGoogleSignInResult(data: Intent?, onResult: (Boolean, String?) -> Unit) {
        val task: Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount> =
            GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                onResult(false, "ID token Google manquant")
                return
            }
            
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            _isLoading.value = true
            
            auth.signInWithCredential(credential)
                .addOnCompleteListener { t: Task<AuthResult> ->
                    _isLoading.value = false
                    if (t.isSuccessful) {
                        val res = t.result
                        val user = res?.user
                        if (user == null) {
                            onResult(false, "Utilisateur Firebase introuvable")
                            return@addOnCompleteListener
                        }
                        
                        if (res.additionalUserInfo?.isNewUser == true) {
                            createEmptyUserProfile(
                                uid = user.uid,
                                email = user.email,
                                name = user.displayName
                            )
                        }
                        
                        _isAuthenticated.value = true
                        onResult(true, null)
                    } else {
                        onResult(false, t.exception?.localizedMessage ?: "Erreur de connexion")
                    }
                }
        } catch (e: ApiException) {
            _isLoading.value = false
            onResult(false, e.localizedMessage)
        }
    }

    fun googleSignOut(context: Context, onComplete: (() -> Unit)? = null) {
        initGoogleSignIn(context)
        googleClient?.signOut()?.addOnCompleteListener { _ ->
            try { 
                auth.signOut() 
            } catch (_: Throwable) {}
            
            _isAuthenticated.value = false
            _errorMessage.value = null
            onComplete?.invoke()
        }
    }

    // === Auth State Management ===
    fun setupAuthListener() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _isAuthenticated.value = user != null
        }
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // === User Profile Creation ===
    private fun createEmptyUserProfile(uid: String, email: String?, name: String?) {
        val data = hashMapOf(
            "id" to uid,
            "email" to (email ?: ""),
            "name" to (name ?: ""),
            "googleUserID" to uid,
            "authProvider" to "google.com",
            "createdAt" to Timestamp(Date()),
            "onboardingCompleted" to false,
            "languageCode" to defaultDeviceLanguage()
        )
        
        db.collection(AppConstants.Firestore.USERS).document(uid).set(data)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Profil vide créé (Google)")
            }
            .addOnFailureListener { e -> 
                _errorMessage.value = "Erreur création profil: ${e.localizedMessage}"
            }
    }

    private fun defaultDeviceLanguage(): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val tag = android.os.LocaleList.getDefault()[0]
            tag.language
        } else {
            java.util.Locale.getDefault().language
        }
    } catch (_: Throwable) { "fr" }
}
