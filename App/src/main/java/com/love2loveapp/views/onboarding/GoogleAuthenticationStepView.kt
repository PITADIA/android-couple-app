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
import androidx.compose.material3.Surface
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
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = OnboardingColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OnboardingDimensions.HorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(OnboardingDimensions.TitleContentSpacing))

            // Titre
            Text(
                text = stringResource(id = R.string.create_secure_account),
                style = OnboardingTypography.TitleLarge,
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
                            text = stringResource(id = R.string.continue_with_google),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                // Espacement minimal entre les boutons
                Spacer(modifier = Modifier.height(1.dp))
                
                // 👤 Bouton Compte Invité - Design identique à Google mais couleurs inversées
                Button(
                    onClick = {
                        Log.d("GuestAuth", "👤 Début création compte invité")
                        scope.launch {
                            googleAuthService.clearError()
                            val success = googleAuthService.signInAnonymously()
                            if (success) {
                                Log.d("GuestAuth", "✅ Compte invité créé avec succès")
                                // La navigation sera gérée automatiquement par UserDataIntegrationService
                            } else {
                                Log.e("GuestAuth", "❌ Échec création compte invité")
                            }
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, // Fond blanc comme demandé
                        contentColor = Color.Black  // Texte noir comme demandé
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
                        // Texte traduit selon le rapport
                        Text(
                            text = stringResource(id = R.string.continue_as_guest),
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
                        color = OnboardingColors.Primary
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(id = R.string.authentication_in_progress),
                        style = OnboardingTypography.BodyMedium.copy(color = Color.White),
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
