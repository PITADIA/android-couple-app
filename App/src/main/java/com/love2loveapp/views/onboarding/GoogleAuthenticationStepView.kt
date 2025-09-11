package com.love2loveapp.views.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.love2loveapp.R
import com.love2loveapp.services.GoogleAuthService
import kotlinx.coroutines.launch

/**
 * Écran d'authentification Google (remplace AuthenticationStepView.swift)
 * Utilise Google Sign-In au lieu d'Apple Sign-In pour Android
 * 
 * Localisation : utilise stringResource(R.string.xxx)
 * Intégration : Firebase Auth + Google Sign-In
 */
@Composable
fun GoogleAuthenticationStepView(
    onAuthenticationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    
    val googleAuthService = GoogleAuthService.instance
    
    // Observer les états du service
    val isProcessing by googleAuthService.isProcessingAuth.collectAsStateWithLifecycle()
    val isAuthenticated by googleAuthService.isAuthenticated.collectAsStateWithLifecycle()
    val authError by googleAuthService.authError.collectAsStateWithLifecycle()
    
    // Anti double-traitement
    var hasProcessed by rememberSaveable { mutableStateOf(false) }
    
    // Initialiser le service Google Auth
    LaunchedEffect(Unit) {
        googleAuthService.initialize(context)
    }
    
    // Launcher pour le résultat de Google Sign-In
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            val success = googleAuthService.handleSignInResult(result.data)
            if (success) {
                Log.d("GoogleAuth", "✅ Authentification Google réussie")
            } else {
                Log.e("GoogleAuth", "❌ Authentification Google échouée")
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA)) // Fond gris clair
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

            // Logo Google + Bouton Sign in with Google
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Icône Google (optionnel)
                /*
                Image(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google",
                    modifier = Modifier.size(64.dp)
                )
                */
                
                Button(
                    onClick = {
                        if (activity != null) {
                            Log.d("GoogleAuth", "🔐 Lancement Google Sign-In")
                            googleAuthService.clearError()
                            val signInIntent = googleAuthService.getSignInIntent()
                            if (signInIntent != null) {
                                signInLauncher.launch(signInIntent)
                            }
                        } else {
                            Log.e("GoogleAuth", "❌ Activity introuvable")
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Icône Google inline (remplacez par votre ressource si disponible)
                        /*
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_small),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        */
                        
                        Text(
                            text = "Continuer avec Google",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                // Message d'erreur si présent
                authError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(50.dp))
        }

        // Overlay de chargement (pendant l'authentification)
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFFD267A)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(id = R.string.authentication_in_progress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Gestion de l'état d'authentification
    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (isAuthenticated && currentUser != null && !hasProcessed) {
            Log.d("GoogleAuth", "✅ Utilisateur déjà authentifié")
            hasProcessed = true
            onAuthenticationComplete()
        } else {
            Log.d("GoogleAuth", "🔐 Prêt pour authentification Google")
        }
    }

    // Réaction au succès d'authentification
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated && !hasProcessed) {
            Log.d("GoogleAuth", "✅ Authentification Google réussie")
            hasProcessed = true
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                Log.d("GoogleAuth", "👤 Utilisateur: ${firebaseUser.displayName}")
                onAuthenticationComplete()
            } else {
                Log.e("GoogleAuth", "❌ Aucun utilisateur Firebase trouvé")
            }
        }
    }
}

// Extension helper
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
