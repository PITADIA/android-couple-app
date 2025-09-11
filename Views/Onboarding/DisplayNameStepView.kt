// DisplayNameStepScreen.kt
package com.yourapp.onboarding

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.yourapp.R

// Couleur d'accent #FD267A
private val AccentPink = Color(0xFFFD267A)
// Fond gris clair (≈ 0.97, 0.97, 0.98)
private val LightBackground = Color(0xFFF7F7FA)

/**
 * ViewModel minimal d’exemple : adapte-le à ton architecture actuelle.
 * Tu peux remplacer les setters directs par des events si tu utilises MVI.
 */
class OnboardingViewModel : ViewModel() {
    var userName by mutableStateOf("")
    fun nextStep() { /* TODO: navigation ou update de l’étape */ }
}

/**
 * Service d’auth fictif pour pré-remplir (équivalent à AuthenticationService.shared.appleUserDisplayName).
 * Remplace par ton implémentation (DI/Hilt, singleton, etc.).
 */
object AuthenticationService {
    var appleUserDisplayName: String? = null
    val shared: AuthenticationService get() = this
}

@Composable
fun DisplayNameStepScreen(
    viewModel: OnboardingViewModel,
    authService: AuthenticationService = AuthenticationService.shared
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Strings via strings.xml (équivalents de .localized)
    val title = stringResource(id = R.string.display_name_step_title)
    val placeholder = stringResource(id = R.string.display_name_placeholder)
    // Si tu tiens au nom de clé "continue", utilise l’accès échappé avec backticks :
    val continueLabel = stringResource(id = R.string.`continue`)
    val skipLabel = stringResource(id = R.string.skip_step)

    // Pré-remplir le nom (équivalent onAppear)
    LaunchedEffect(Unit) {
        if (viewModel.userName.isBlank()) {
            authService.appleUserDisplayName?.let {
                Log.d("DisplayNameStepView", "🔥 Pré-remplissage avec nom Apple: $it")
                viewModel.userName = it
            }
        }
    }

    // Tap en dehors pour fermer le clavier
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Espace entre barre de progression et titre (40)
            Spacer(modifier = Modifier.height(40.dp))

            // Titre aligné à gauche
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
            }

            // Premier Spacer pour centrer le contenu
            Spacer(modifier = Modifier.weight(1f))

            // Contenu principal centré : carte + TextField
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Carte blanche avec ombre, coins 12dp
                Box(
                    modifier = Modifier
                        .padding(horizontal = 30.dp)
                        .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp))
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .fillMaxWidth()
                ) {
                    // TextField Material3 avec placeholder
                    TextField(
                        value = viewModel.userName,
                        onValueChange = { viewModel.userName = it },
                        placeholder = {
                            Text(
                                text = placeholder,
                                fontSize = 18.sp,
                                color = Color.Black.copy(alpha = 0.5f)
                            )
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 18.sp,
                            color = Color.Black
                        ),
                        colors = TextFieldDefaults.textFieldColors(
                            containerColor = Color.Transparent,
                            cursorColor = AccentPink,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedLabelColor = Color.Black,
                            unfocusedLabelColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }

            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer(modifier = Modifier.weight(1f))

            // Zone blanche collée en bas
            Surface(color = Color.White) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Bouton "Continuer"
                    Button(
                        onClick = {
                            Log.d("DisplayNameStepView", "🔥 Bouton 'Continue' cliqué")
                            viewModel.nextStep()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .padding(horizontal = 30.dp)
                            .height(56.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = continueLabel,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(15.dp))

                    // Bouton "Passer cette étape"
                    TextButton(
                        onClick = {
                            Log.d("DisplayNameStepView", "🔥 Bouton 'Passer cette étape' cliqué")
                            Log.d("DisplayNameStepView", "🔥 Nom actuel avant skip: '${viewModel.userName}'")
                            viewModel.userName = "" // laisser vide pour auto-génération
                            Log.d("DisplayNameStepView", "🔥 Nom vidé pour auto-génération")
                            Log.d("DisplayNameStepView", "🔥 Appel de nextStep()")
                            viewModel.nextStep()
                        }
                    ) {
                        Text(
                            text = skipLabel,
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.6f),
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
            }
        }
    }
}
