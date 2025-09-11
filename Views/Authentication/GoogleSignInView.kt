// File: GoogleSignInScreen.kt
package com.love2loveapp.ui.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun GoogleSignInScreen(
    onBack: () -> Unit,
    onAuthenticatedExistingUser: (FirebaseUser) -> Unit,
    onAuthenticatedNewUser: (FirebaseUser) -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Launcher pour Google Sign-In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        viewModel.handleGoogleSignInResult(
            result = result,
            contextString = { resId -> context.getString(resId) },
        ) { user, isNew ->
            if (isNew) onAuthenticatedNewUser(user) else onAuthenticatedExistingUser(user)
        }
    }

    // Dégradé rouge/violet (équivalent de la version Swift)
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(red = 0.4f, green = 0.1f, blue = 0.2f),
            Color(red = 0.2f, green = 0.05f, blue = 0.15f)
        ),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(horizontal = 20.dp)
    ) {
        // Header avec bouton retour
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                Log.d("🔥 GoogleSignInScreen", "Bouton retour pressé")
                onBack()
            }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
        }

        // Barre de progression (1 blanche + 4 grises)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 100.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index == 0) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                )
                if (index < 4) Spacer(Modifier.width(8.dp))
            }
        }

        // Contenu principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(160.dp))

            // Titres
            Text(
                text = stringResource(R.string.one_more_step),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.secure_account),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // Description
            Text(
                text = stringResource(R.string.create_account_description),
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.weight(1f))

            // Icône flamme
            Text("🔥", fontSize = 80.sp, color = Color.White, modifier = Modifier.padding(bottom = 40.dp))

            Spacer(Modifier.weight(1f))

            // Bouton "Sign in with Google"
            Button(
                onClick = {
                    Log.d("🔥 GoogleSignInScreen", "Début de la requête Google Sign-In")
                    viewModel.launchGoogleSignInIntent(
                        contextString = { resId -> context.getString(resId) },
                        launch = { intent -> googleSignInLauncher.launch(intent) }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(28.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = stringResource(R.string.sign_in_with_google),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(50.dp))
        }

        // Overlay chargement
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(30.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.connecting),
                        color = Color.White
                    )
                }
            }
        }

        // Alerte d'erreur
        if (!errorMessage.isNullOrEmpty()) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.error)) },
                text = { Text(errorMessage ?: "") }
            )
        }
    }
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> get() = _isLoading

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> get() = _errorMessage

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Prépare l'intent Google Sign-In et le lance via le launcher fourni.
     */
    fun launchGoogleSignInIntent(
        contextString: (Int) -> String,
        launch: (Intent) -> Unit
    ) {
        _isLoading.value = true
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                // IMPORTANT : ce string est injecté par google-services.json
                .requestIdToken(contextString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            val client = GoogleSignIn.getClient(
                // Le context n'est pas directement passé ; l'intent n'en a pas besoin ici
                // (GoogleSignIn.getClient lira le context interne du signInIntent au lancement)
                /* context = */ androidx.compose.ui.platform.AndroidUriHandlerAmbient.current as? android.content.Context
                    ?: throw IllegalStateException("Context non disponible")
                , gso
            )

            launch(client.signInIntent)
        } catch (e: Exception) {
            _isLoading.value = false
            _errorMessage.value = e.localizedMessage ?: contextString(R.string.generic_auth_error)
            Log.e("❌ AuthViewModel", "launchGoogleSignInIntent error", e)
        }
    }

    /**
     * Traite le résultat d'ActivityResult du Google Sign-In, puis authentifie Firebase.
     */
    fun handleGoogleSignInResult(
        result: ActivityResult,
        contextString: (Int) -> String,
        onDone: (FirebaseUser, isNewUser: Boolean) -> Unit
    ) {
        if (result.resultCode != Activity.RESULT_OK) {
            _isLoading.value = false
            _errorMessage.value = contextString(R.string.sign_in_canceled)
            Log.w("❌ AuthViewModel", "Sign-In annulé ou échoué: code=${result.resultCode}")
            return
        }

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken.isNullOrEmpty()) {
                _isLoading.value = false
                _errorMessage.value = contextString(R.string.missing_id_token)
                Log.e("❌ AuthViewModel", "ID Token manquant")
                return
            }

            Log.d("🔥 AuthViewModel", "Google account récupéré, démarrage auth Firebase")
            _isLoading.value = true

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { authTask ->
                    _isLoading.value = false
                    if (authTask.isSuccessful) {
                        val user = auth.currentUser
                        val isNew = authTask.result?.additionalUserInfo?.isNewUser ?: false
                        if (user != null) {
                            Log.d("🔥 AuthViewModel", "Firebase auth OK, isNew=$isNew, uid=${user.uid}")
                            onDone(user, isNew)
                        } else {
                            _errorMessage.value = contextString(R.string.generic_auth_error)
                        }
                    } else {
                        val msg = authTask.exception?.localizedMessage
                            ?: contextString(R.string.generic_auth_error)
                        _errorMessage.value = msg
                        Log.e("❌ AuthViewModel", "Firebase auth failed", authTask.exception)
                    }
                }

        } catch (e: ApiException) {
            _isLoading.value = false
            _errorMessage.value = e.localizedMessage ?: contextString(R.string.generic_auth_error)
            Log.e("❌ AuthViewModel", "GoogleSignIn ApiException", e)
        } catch (e: Exception) {
            _isLoading.value = false
            _errorMessage.value = e.localizedMessage ?: contextString(R.string.generic_auth_error)
            Log.e("❌ AuthViewModel", "handleGoogleSignInResult error", e)
        }
    }
}
