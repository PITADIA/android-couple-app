package com.love2loveapp.views

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import com.love2loveapp.AppDelegate
import com.love2loveapp.R
import com.love2loveapp.services.GoogleAuthService
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 🚀 SplashScreen - Écran de chargement initial avec gradient et logo
 * Affiche le logo pendant 2 secondes puis laisse UserDataIntegrationService naviguer
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier
) {
    // État pour contrôler l'animation du logo
    var logoScale by remember { mutableStateOf(0.8f) }
    var logoAlpha by remember { mutableStateOf(0f) }
    
    // Animation du logo à l'apparition
    LaunchedEffect(Unit) {
        // Animation d'entrée fluide
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) { value, _ ->
            logoAlpha = value
            logoScale = 0.8f + (0.2f * value) // Scale de 0.8 à 1.0
        }
        // ⏳ Animation terminée - SplashScreen n'est plus utilisé au démarrage
        // La navigation est maintenant gérée par UserDataIntegrationService
        delay(1200)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFD267A), // Rose principal
                        Color(0xFFFF655B)  // Orange-rouge
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset.Infinite
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.leetchi2),
            contentDescription = "Logo Love2Love",
            modifier = Modifier
                .size(200.dp)
                .scale(logoScale)
                .alpha(logoAlpha),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 📱 WelcomeScreen - Page de présentation avec slogan et boutons
 * Équivalent iOS AuthenticationView
 */
@Composable
fun WelcomeScreen(
    onStartOnboarding: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val googleAuthService = GoogleAuthService.instance
    
    // État pour l'UI
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Observer les états du service Google Auth
    val isProcessingAuth by googleAuthService.isProcessingAuth.collectAsState()
    val authError by googleAuthService.authError.collectAsState()
    
    // Initialiser Google Sign-In
    LaunchedEffect(Unit) {
        googleAuthService.initialize(context)
    }
    
    // Observer les erreurs d'authentification
    LaunchedEffect(authError) {
        authError?.let { error ->
            errorMessage = error
            isSigningIn = false
        }
    }
    
    // Launcher pour l'activité Google Sign-In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            coroutineScope.launch {
                val success = googleAuthService.handleSignInResult(result.data)
                if (success) {
                    // L'authentification a réussi
                    // UserDataIntegrationService va gérer la navigation automatiquement
                    // selon le statut d'abonnement et d'onboarding
                } else {
                    isSigningIn = false
                }
            }
        } else {
            isSigningIn = false
        }
    }
    
    // Fonction pour démarrer Google Sign-In
    fun startGoogleSignIn() {
        val signInIntent = googleAuthService.getSignInIntent()
        if (signInIntent != null) {
            isSigningIn = true
            errorMessage = null
            googleSignInLauncher.launch(signInIntent)
        } else {
            errorMessage = "Erreur d'initialisation Google Sign-In"
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8)) // Fond gris clair identique onboarding
    ) {
        // 1. Header avec logo + nom app
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.leetchi),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = stringResource(R.string.app_name),
                    style = TextStyle(
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 2. Contenu principal centré
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.app_tagline),
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            )

            Text(
                text = stringResource(R.string.app_description),
                style = TextStyle(
                    fontSize = 18.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 3. Zone boutons (fixe en bas)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            // Bouton principal
            Button(
                onClick = onStartOnboarding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 30.dp)
            ) {
                Text(
                    text = stringResource(R.string.start_free),
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }

            // Bouton secondaire - Google Sign-In
            TextButton(
                onClick = { startGoogleSignIn() },
                enabled = !isSigningIn && !isProcessingAuth,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSigningIn || isProcessingAuth) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        text = if (isSigningIn || isProcessingAuth) {
                            stringResource(R.string.authentication_in_progress)
                        } else {
                            stringResource(R.string.already_have_account)
                        },
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.6f),
                            textDecoration = if (!isSigningIn && !isProcessingAuth) TextDecoration.Underline else null
                        )
                    )
                }
            }
        }
        
        // Affichage des erreurs
        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text("OK", color = Color.White)
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    SplashScreen()
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    WelcomeScreen(
        onStartOnboarding = { },
        onSignInWithGoogle = { }
    )
}
