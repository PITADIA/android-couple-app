```kotlin
package com.love2loveapp.services.auth

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Android/Kotlin AuthenticationService — Google Sign-In ONLY
 *
 * • Firebase Auth with Google provider
 * • App Check token fetch (best-effort)
 * • StateFlow equivalents of Swift @Published props
 * • strings.xml for all user-facing messages (use app.getString(...))
 */
class AuthenticationService private constructor(private val app: Application) {

    // ===== Observable state (Swift @Published equivalents) =====
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _googleUserDisplayName = MutableStateFlow<String?>(null)
    val googleUserDisplayName: StateFlow<String?> = _googleUserDisplayName

    private val _isProcessingFirebaseAuth = MutableStateFlow(false)
    val isProcessingFirebaseAuth: StateFlow<Boolean> = _isProcessingFirebaseAuth

    // ===== Internal state =====
    private val isSignInInProgress = AtomicBoolean(false)
    private val lastProcessedCredentialTimeMs = AtomicLong(0L)

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    init {
        // Already signed in?
        auth.currentUser?.let { user ->
            Log.d(TAG, "🔥 AuthenticationService: Utilisateur déjà connecté")
            _currentUser.value = user
            _isAuthenticated.value = true
            _googleUserDisplayName.value = user.displayName
        }

        // Observe auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            _isAuthenticated.value = user != null
            if (user != null) {
                Log.d(TAG, "✅ Utilisateur connecté")
                _googleUserDisplayName.value = user.displayName
            } else {
                Log.d(TAG, "❌ Utilisateur déconnecté")
            }
        }
    }

    // ===== Google Sign-In API =====

    /** Build a GoogleSignInClient requesting ID token + email. */
    private fun buildGoogleClient(context: Context): GoogleSignInClient {
        val webClientId = context.getString(R.string.default_web_client_id) // from google-services.json
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Returns an intent to launch with Activity Result API.
     * Usage (Activity/Fragment):
     * val intent = authService.getGoogleSignInIntent(this)
     * launcher.launch(intent)
     */
    fun getGoogleSignInIntent(context: Context): Intent {
        // Prevent concurrent calls
        if (!isSignInInProgress.compareAndSet(false, true)) {
            Log.w(TAG, "⚠️ Sign In déjà en cours")
        }

        // Throttle < 2s
        val now = System.currentTimeMillis()
        if (now - lastProcessedCredentialTimeMs.get() <= 2_000L) {
            Log.w(TAG, "⚠️ Appels trop fréquents")
        }

        _isLoading.value = true
        _errorMessage.value = null

        // Best-effort App Check token fetch (non-blocking)
        FirebaseAppCheck.getInstance().getToken(false)
            .addOnSuccessListener { token ->
                Log.d(TAG, "✅ App Check token disponible - longueur: ${token.token.length}")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "⚠️ App Check token error: ${e.localizedMessage}")
            }

        return buildGoogleClient(context).signInIntent
    }

    /**
     * Handles the activity result Intent of Google sign-in and completes Firebase authentication.
     * Call this from your ActivityResult callback.
     */
    fun handleGoogleSignInResult(context: Context, data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                throw IllegalStateException("Google ID token is null")
            }

            Log.d(TAG, "✅ Google token récupéré - longueur: ${idToken.length}")
            _isProcessingFirebaseAuth.value = true

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { result ->
                    _isLoading.value = false
                    _isProcessingFirebaseAuth.value = false
                    isSignInInProgress.set(false)
                    lastProcessedCredentialTimeMs.set(System.currentTimeMillis())

                    if (result.isSuccessful) {
                        val user = auth.currentUser
                        Log.d(TAG, "✅ Connexion Firebase réussie")
                        _currentUser.value = user
                        _isAuthenticated.value = user != null
                        _googleUserDisplayName.value = user?.displayName
                    } else {
                        val ex = result.exception
                        Log.e(TAG, "❌ Firebase signInWithCredential error: ${ex?.localizedMessage}", ex)
                        _errorMessage.value = ex?.localizedMessage
                            ?: context.getString(R.string.firebase_sign_in_error)
                    }
                }
        } catch (e: ApiException) {
            _isLoading.value = false
            _isProcessingFirebaseAuth.value = false
            isSignInInProgress.set(false)
            Log.e(TAG, "❌ GoogleSignIn ApiException: status=${e.statusCode}", e)
            _errorMessage.value = context.getString(R.string.google_sign_in_error)
        } catch (t: Throwable) {
            _isLoading.value = false
            _isProcessingFirebaseAuth.value = false
            isSignInInProgress.set(false)
            Log.e(TAG, "❌ GoogleSignIn Throwable: ${t.localizedMessage}", t)
            _errorMessage.value = t.localizedMessage ?: context.getString(R.string.auth_error_generic)
        }
    }

    fun signOut(context: Context) {
        Log.d(TAG, "🔥 AuthenticationService: Déconnexion")
        try {
            // Sign out from Google client as well
            buildGoogleClient(context).signOut()
            auth.signOut()
            _googleUserDisplayName.value = null
        } catch (t: Throwable) {
            Log.e(TAG, "🔥 Erreur de déconnexion: ${t.localizedMessage}", t)
            _errorMessage.value = t.localizedMessage ?: context.getString(R.string.sign_out_error)
        }
    }

    companion object {
        private const val TAG = "AuthenticationService"
        @Volatile private var INSTANCE: AuthenticationService? = null

        fun getInstance(app: Application): AuthenticationService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthenticationService(app).also { INSTANCE = it }
            }
    }
}
```

---

```xml
<!-- res/values/strings.xml (excerpt) -->
<resources>
    <string name="auth_sign_in_in_progress">Connexion déjà en cours</string>
    <string name="auth_too_frequent_calls">Appels trop fréquents</string>
    <string name="auth_error_generic">Erreur d'authentification</string>
    <string name="google_sign_in_error">Échec de la connexion Google</string>
    <string name="firebase_sign_in_error">Échec de l'authentification Firebase</string>
    <string name="sign_out_error">Erreur lors de la déconnexion</string>

    <!-- Supplied by google-services plugin -->
    <string name="default_web_client_id">YOUR_WEB_CLIENT_ID</string>
</resources>
```
