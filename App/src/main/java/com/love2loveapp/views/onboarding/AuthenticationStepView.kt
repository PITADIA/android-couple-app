package com.love2loveapp.views.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.love2loveapp.R
import com.love2loveapp.services.GoogleAuthService
import kotlinx.coroutines.launch

@Composable
fun AuthenticationStepScreen(
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

    // ZStack avec overlay chargement selon le rapport
    Surface(
        modifier = modifier.fillMaxSize(),
        color = OnboardingColors.Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.height(OnboardingDimensions.TitleContentSpacing))

            // Titre selon les spécifications du rapport (font(.system(size: 36, weight: .bold)))
            Text(
                text = stringResource(R.string.create_secure_account),
                style = OnboardingTypography.TitleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = OnboardingDimensions.HorizontalPadding)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bouton Google (remplace Apple) selon le rapport
            Column(
                modifier = Modifier.padding(horizontal = OnboardingDimensions.HorizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bouton Google avec le design du rapport (remplace Apple par Google)
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
                    shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusButton),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OnboardingDimensions.ButtonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black, // background(Color.black) selon le rapport
                        contentColor = Color.White // foregroundColor(.white) selon le rapport
                    )
                ) {
                    // HStack avec logo selon le rapport
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Logo Google (remplace applelogo)
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Google",
                            tint = Color.White
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Texte selon le rapport (font(.system(size: 18, weight: .semibold)))
                        Text(
                            text = stringResource(R.string.continue_with_google),
                            style = OnboardingTypography.ButtonText
                        )
                    }
                }
                
                // Espacement entre les boutons
                Spacer(modifier = Modifier.height(16.dp))
                
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
                    shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusButton),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OnboardingDimensions.ButtonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, // Fond blanc comme demandé
                        contentColor = Color.Black  // Texte noir comme demandé
                    )
                ) {
                    // Structure identique au bouton Google
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Icône utilisateur invité
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Invité",
                            tint = Color.Black
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Texte traduit selon le rapport
                        Text(
                            text = stringResource(R.string.continue_as_guest),
                            style = OnboardingTypography.ButtonText.copy(color = Color.Black)
                        )
                    }
                }

                // Message d'erreur si présent
                authError?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = OnboardingTypography.BodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(50.dp))
        }

        // Overlay Chargement selon le rapport
        // transition(.opacity.combined(with: .scale))
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(animationSpec = tween(300))
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
                    // ProgressView() scaleEffect(1.5) selon le rapport
                    CircularProgressIndicator(
                        color = OnboardingColors.Primary,
                        modifier = Modifier.size(36.dp), // scaleEffect(1.5) simulé
                        strokeWidth = 4.dp
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // font(.system(size: 18, weight: .medium)) selon le rapport
                    Text(
                        text = stringResource(R.string.authentication_in_progress),
                        style = OnboardingTypography.BodyMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        ),
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
