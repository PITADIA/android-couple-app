package com.love2loveapp.ui.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.love2loveapp.R

/**
 * Kotlin/Compose réécriture de AuthenticationView.swift
 * - Localisation: strings.xml via stringResource()/context.getString()
 * - Auth: Google Sign-In uniquement (aucun Apple Sign-In)
 * - Layout: fond gris clair, logo + nom en haut, titre + sous-titre au centre,
 *           zone blanche en bas avec 2 boutons (Démarrer, J'ai déjà un compte)
 *
 * Dépendances à déclarer (build.gradle):
 *   implementation("com.google.android.gms:play-services-auth:21.2.0")
 *   implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
 *
 * Ressources requises:
 *   - drawable/leetchi.png (ou svg) -> R.drawable.leetchi
 *   - strings.xml: app_name, app_tagline, app_description, start_free, already_have_account,
 *                  default_web_client_id (fourni par Firebase, google-services.json)
 */

// --- Modèle utilisateur minimal (à ajuster selon ton domaine) ---
data class AppUser(
    val uid: String,
    val name: String?,
    val email: String?
)

// --- Service Firebase minimaliste (placeholder) ---
object FirebaseService {
    // Dans ton app réelle, ceci doit refléter l'utilisateur chargé depuis Firestore/Realtime DB
    var currentUser: AppUser? = null

    suspend fun refreshCurrentUserProfile(): AppUser? {
        // TODO: implémenter la récupération du profil utilisateur Firebase
        return currentUser
    }
}

// --- AppState (remplace l'EnvironmentObject Swift) ---
class AppState : ViewModel() {
    val isOnboardingInProgress = MutableStateFlow(false)

    fun startUserOnboarding() {
        Log.d("AppState", "🔥 startUserOnboarding()")
        isOnboardingInProgress.value = true
    }

    fun authenticate(with: AppUser) {
        Log.d("AppState", "🔥 authenticate(with=${with.uid})")
        // TODO: stocker l'utilisateur authentifié si besoin
    }

    fun completeOnboarding() {
        Log.d("AppState", "🔥 completeOnboarding()")
        isOnboardingInProgress.value = false
    }
}

// --- Événements d'auth ---
sealed interface AuthEvent {
    data class Success(val isNewUser: Boolean, val user: AppUser?) : AuthEvent
    data class Error(val message: String) : AuthEvent
}

// --- ViewModel d'authentification (Google Sign-In -> FirebaseAuth) ---
class AuthenticationViewModel(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _events = MutableSharedFlow<AuthEvent>()
    val events = _events.asSharedFlow()

    var isLoading by mutableStateOf(false)
        private set

    fun googleClient(context: Context): GoogleSignInClient {
        val webClientId = context.getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun handleGoogleResult(data: Intent?, context: Context) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrEmpty()) {
                viewModelScope.launch { _events.emit(AuthEvent.Error("ID Token manquant")) }
                return
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            isLoading = true
            firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener { result ->
                    isLoading = false
                    if (result.isSuccessful) {
                        val user = firebaseAuth.currentUser
                        val appUser = AppUser(
                            uid = user?.uid.orEmpty(),
                            name = user?.displayName,
                            email = user?.email
                        )
                        // L'info isNewUser est disponible via additionalUserInfo
                        val isNew = result.result?.additionalUserInfo?.isNewUser == true
                        viewModelScope.launch { _events.emit(AuthEvent.Success(isNewUser = isNew, user = appUser)) }
                    } else {
                        val msg = result.exception?.localizedMessage ?: "Unknown error"
                        viewModelScope.launch { _events.emit(AuthEvent.Error(msg)) }
                    }
                }
        } catch (e: ApiException) {
            viewModelScope.launch { _events.emit(AuthEvent.Error("GoogleSignIn ApiException: ${e.statusCode}")) }
        } catch (t: Throwable) {
            viewModelScope.launch { _events.emit(AuthEvent.Error(t.message ?: "Sign-in failed")) }
        }
    }
}

// --- Composable principal ---
@Composable
fun AuthenticationScreen(
    appState: AppState = viewModel(),
    viewModel: AuthenticationViewModel = viewModel(),
    onStartOnboarding: () -> Unit,
    onAuthenticatedExistingUser: (AppUser) -> Unit
) {
    val context = LocalContext.current

    // Google sign-in launcher
    val googleClient = remember { viewModel.googleClient(context) }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleGoogleResult(result.data, context)
        } else {
            Log.w("AuthenticationScreen", "Google Sign-In annulé / code=${result.resultCode}")
        }
    }

    // Logs d'apparition / disparition
    LaunchedEffect(Unit) {
        val ts = System.currentTimeMillis() / 1000.0
        Log.d("AuthenticationView", "🔥 appear [$ts]")
    }
    DisposableEffect(Unit) {
        onDispose {
            val ts = System.currentTimeMillis() / 1000.0
            Log.d("AuthenticationView", "🔥 disappear [$ts]")
        }
    }

    // Écoute des événements d'auth
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.Success -> {
                    Log.d("AuthenticationView", "🔥 Auth success (isNew=${event.isNewUser})")

                    if (appState.isOnboardingInProgress.value) {
                        Log.d("AuthenticationView", "🔥 Onboarding déjà en cours - on ignore")
                        return@collect
                    }

                    // Synchronisation avec le profil Firebase (analogue à firebaseService.currentUser)
                    val profile = FirebaseService.refreshCurrentUserProfile()
                    if (profile != null) {
                        Log.d("AuthenticationView", "🔥 Utilisateur existant: ${profile.name}")
                        appState.authenticate(profile)
                        appState.completeOnboarding()
                        onAuthenticatedExistingUser(profile)
                    } else {
                        Log.d("AuthenticationView", "🔥 Nouvel utilisateur via Google Sign-In, démarrage onboarding")
                        appState.startUserOnboarding()
                        onStartOnboarding()
                    }
                }
                is AuthEvent.Error -> {
                    Log.e("AuthenticationView", "❌ ${event.message}")
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- UI ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA)) // Fond gris clair
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top: logo + nom app
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 60.dp)
                ) {
                    Spacer(modifier = Modifier.width(15.dp))
                    Image(
                        painter = painterResource(id = R.drawable.leetchi),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(15.dp))
                    Text(
                        text = stringResource(id = R.string.app_name),
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            // Premier spacer (pousse vers le centre)
            Spacer(modifier = Modifier.weight(1f))

            // Titre + sous-titre
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.app_tagline),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    lineHeight = 42.sp,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(id = R.string.app_description),
                    fontSize = 18.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 30.dp)
                )
            }

            // Deuxième spacer (pousse les boutons vers le bas)
            Spacer(modifier = Modifier.weight(1f))

            // Zone blanche en bas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bouton principal: démarrer l'onboarding
                Button(
                    onClick = {
                        Log.d("AuthenticationView", "🔥 Démarrage manuel de l'onboarding")
                        appState.startUserOnboarding()
                        onStartOnboarding()
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .padding(horizontal = 30.dp)
                        .height(56.dp)
                        .fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFD267A))
                ) {
                    Text(
                        text = stringResource(id = R.string.start_free),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Bouton secondaire: "J'ai déjà un compte" -> Google Sign-In
                Text(
                    text = stringResource(id = R.string.already_have_account),
                    fontSize = 16.sp,
                    color = Color.Black.copy(alpha = 0.6f),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(horizontal = 30.dp)
                        .noRippleClickable {
                            Log.d("AuthenticationView", "🔥 Bouton 'J'ai déjà un compte' pressé -> Google Sign-In")
                            googleSignInLauncher.launch(googleClient.signInIntent)
                        }
                )
            }
        }
    }
}

// Petit utilitaire pour un Text cliquable sans ripple (optionnel)
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier
        .padding(0.dp)
        .let {
            androidx.compose.foundation.clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onClick() }
        }
    )
