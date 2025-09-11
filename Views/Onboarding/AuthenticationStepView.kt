package com.love2love.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * Écran d’authentification (Compose) – remplace AuthenticationStepView.swift
 *
 * Localisation : utilise stringResource(R.string.xxx) / context.getString(R.string.xxx)
 * Pas d’xcstrings : tout est dans res/values/strings.xml
 */
@Composable
fun AuthenticationStepScreen(
    viewModel: OnboardingViewModel,
    appState: AppState,
    modifier: Modifier = Modifier,
    authService: AuthenticationService = AuthenticationService.instance,
    firebaseService: FirebaseService = FirebaseService.instance
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    // États exposés par le service d’auth
    val isProcessing by authService.isProcessingFirebaseAuth.collectAsState()
    val isAuthenticated by authService.isAuthenticated.collectAsState()

    // Anti double-traitement (équivalent hasProcessedAuthentication)
    var hasProcessed by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA)) // ~ 0.97, 0.97, 0.98
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Titre
            Text(
                text = stringResource(id = R.string.create_secure_account),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 0.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bouton "Sign in with Apple"
            Button(
                onClick = {
                    Log.d("Auth", "🔐 Authentification Apple démarrée")
                    if (activity != null) {
                        authService.signInWithApple(activity)
                    } else {
                        Log.e("Auth", "Activity introuvable pour lancer le flux Apple")
                    }
                },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                // Remplace l’icône ci-dessous par ta ressource (ic_apple) si tu en as une
                // Icon(painter = painterResource(id = R.drawable.ic_apple), contentDescription = null)
                Text(
                    text = stringResource(id = R.string.continue_with_apple),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(50.dp))
        }

        // Overlay de chargement (affiché seulement après FaceID / pendant Firebase)
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(id = R.string.authentication_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Équivalent .onAppear : si déjà authentifié, on enchaîne
    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (appState.isAuthenticated && currentUser != null && !hasProcessed) {
            Log.d("Auth", "✅ Utilisateur déjà authentifié")
            hasProcessed = true
            createPartialUserDocument(
                firebaseUserUid = currentUser.uid,
                viewModel = viewModel,
                appState = appState,
                firebaseService = firebaseService
            )
            viewModel.completeAuthentication()
        } else {
            Log.d("Auth", "🔐 Prêt pour authentification")
        }
    }

    // Équivalent .task(id: authService.isAuthenticated) – réagit au succès d’auth
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated && !hasProcessed) {
            Log.d("Auth", "✅ Authentification réussie")
            hasProcessed = true
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                createPartialUserDocument(
                    firebaseUserUid = firebaseUser.uid,
                    viewModel = viewModel,
                    appState = appState,
                    firebaseService = firebaseService
                )
                viewModel.completeAuthentication()
            } else {
                Log.e("Auth", "❌ Aucun utilisateur trouvé")
            }
        }
    }
}

/* ---------- Helpers & logique "createPartialUserDocument" ---------- */

private fun createPartialUserDocument(
    firebaseUserUid: String,
    viewModel: OnboardingViewModel,
    appState: AppState,
    firebaseService: FirebaseService
) {
    Log.d("Auth", "📝 Création document utilisateur (uid=$firebaseUserUid)")

    // Marquer le début du processus d’onboarding
    firebaseService.startOnboardingProcess()
    appState.isOnboardingInProgress = true

    // Construire l’objet AppUser avec les données collectées en onboarding
    val partialUser = AppUser(
        uid = firebaseUserUid,
        name = viewModel.userName,
        birthDate = viewModel.birthDate,
        relationshipGoals = viewModel.selectedGoals,
        relationshipDuration = viewModel.relationshipDuration,
        relationshipImprovement = viewModel.selectedImprovements.joinToString(", ")
            .ifBlank { null },
        questionMode = viewModel.questionMode.ifBlank { null },
        partnerCode = null,
        isSubscribed = false,
        onboardingInProgress = true,
        relationshipStartDate = viewModel.relationshipStartDate,
        profileImageURL = null,
        currentLocation = viewModel.currentLocation
    )

    Log.d("Auth", "💾 Sauvegarde données partielles")
    CoroutineScope(Dispatchers.IO).launch {
        firebaseService.savePartialUserData(partialUser)
    }
}

/* ---------- Extensions ---------- */

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/* ---------- Modèles & services (squelettes minimalistes) ---------- */

data class AppUser(
    val uid: String,
    val name: String?,
    val birthDate: LocalDate?,
    val relationshipGoals: List<String>,
    val relationshipDuration: String?,
    val relationshipImprovement: String?,
    val questionMode: String?,
    val partnerCode: String?,
    val isSubscribed: Boolean,
    val onboardingInProgress: Boolean,
    val relationshipStartDate: LocalDate?,
    val profileImageURL: String?,
    val currentLocation: String?
)

class OnboardingViewModel(
    val userName: String?,
    val birthDate: LocalDate?,
    val selectedGoals: List<String>,
    val relationshipDuration: String?,
    val selectedImprovements: List<String>,
    val questionMode: String,
    val relationshipStartDate: LocalDate?,
    val currentLocation: String?
) {
    fun completeAuthentication() {
        // Navigation / état vers l’étape suivante de l’onboarding
        Log.d("Onboarding", "➡️ Étape d’auth complétée")
    }
}

class AppState(
    var isAuthenticated: Boolean = false,
    var isOnboardingInProgress: Boolean = false
)

/**
 * Service d’authentification Firebase + Apple (Android via OAuthProvider)
 */
class AuthenticationService private constructor() {
    private val auth = FirebaseAuth.getInstance()

    // États observables par Compose
    private val _isProcessing = mutableStateOf(false)
    private val _isAuthenticated = mutableStateOf(auth.currentUser != null)

    val isProcessingFirebaseAuth: State<Boolean> = _isProcessing
    val isAuthenticated: State<Boolean> = _isAuthenticated

    fun signInWithApple(activity: Activity) {
        _isProcessing.value = true

        val provider = OAuthProvider.newBuilder("apple.com").apply {
            scopes = listOf("name", "email")
            // Optionnel : passer la locale
            addCustomParameter("locale", Locale.getDefault().toLanguageTag())
        }.build()

        val pending = auth.pendingAuthResult
        if (pending != null) {
            pending
                .addOnSuccessListener {
                    _isProcessing.value = false
                    _isAuthenticated.value = true
                    Log.d("Auth", "✅ Apple (pending) OK")
                }
                .addOnFailureListener { e ->
                    _isProcessing.value = false
                    Log.e("Auth", "❌ Apple (pending) fail : ${e.message}", e)
                }
        } else {
            auth.startActivityForSignInWithProvider(activity, provider)
                .addOnSuccessListener {
                    _isProcessing.value = false
                    _isAuthenticated.value = true
                    Log.d("Auth", "✅ Apple OK")
                }
                .addOnFailureListener { e ->
                    _isProcessing.value = false
                    Log.e("Auth", "❌ Apple fail : ${e.message}", e)
                }
        }
    }

    companion object {
        val instance: AuthenticationService by lazy { AuthenticationService() }
    }
}

/**
 * Service Firebase (squelette) pour marquer l’onboarding et sauvegarder les données partielles
 */
class FirebaseService private constructor() {
    fun startOnboardingProcess() {
        // Exemple : écrire un flag dans Firestore/Realtime DB, ou mémoire locale
        Log.d("FirebaseService", "🚩 Onboarding démarré")
    }

    fun savePartialUserData(user: AppUser) {
        // TODO: écriture Firestore (collection "users" / doc uid)
        // Firebase.firestore.collection("users").document(user.uid).set(...)
        Log.d("FirebaseService", "📄 Données partielles envoyées pour ${user.uid}")
    }

    companion object {
        val instance: FirebaseService by lazy { FirebaseService() }
    }
}
