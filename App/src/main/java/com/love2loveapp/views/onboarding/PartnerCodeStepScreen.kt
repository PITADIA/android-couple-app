package com.love2loveapp.views.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.services.PartnerCodeService
import kotlinx.coroutines.launch

/**
 * Interface sophistiquée pour le système de code partenaire
 * Équivalent du PartnerCodeStepView iOS avec génération, partage et connexion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerCodeStepScreen(
    onNextStep: () -> Unit,
    onSkipSubscriptionDueToInheritance: () -> Unit,
    onPartnerConnected: (String) -> Unit
) {
    val context = LocalContext.current
    val partnerCodeService = remember { PartnerCodeService.shared }
    val coroutineScope = rememberCoroutineScope()
    
    // États locaux
    var enteredCode by remember { mutableStateOf("") }
    // Variable showShareSheet supprimée car non utilisée
    
    // États du service
    val serviceState by partnerCodeService.state.collectAsState()
    
    // Initialisation au montage
    LaunchedEffect(Unit) {
        // Vérifier connexion existante
        partnerCodeService.checkExistingConnection()
        
        if (serviceState.isConnected) {
            // Déjà connecté, passer à l'étape suivante
            serviceState.partnerInfo?.let { partnerInfo ->
                onPartnerConnected(partnerInfo.name)
                if (partnerInfo.isSubscribed) {
                    onSkipSubscriptionDueToInheritance()
                } else {
                    onNextStep()
                }
            }
        } else {
            // Génération automatique du code
            partnerCodeService.generatePartnerCode()
        }
    }
    
    // Gestion de la connexion réussie
    LaunchedEffect(serviceState.isConnected) {
        if (serviceState.isConnected) {
            serviceState.partnerInfo?.let { partnerInfo ->
                onPartnerConnected(partnerInfo.name)
                if (partnerInfo.isSubscribed) {
                    // Abonnement hérité du partenaire premium
                    onSkipSubscriptionDueToInheritance()
                } else {
                    // Partenaire sans abonnement, continuer vers la page de paiement
                    onNextStep()
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        // Titre principal
        Text(
            text = stringResource(id = R.string.connect_with_partner),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Sous-titre explicatif
        Text(
            text = stringResource(id = R.string.connect_partner_description),
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Section génération de code
        AnimatedVisibility(
            visible = !serviceState.isLoading && serviceState.generatedCode != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            serviceState.generatedCode?.let { code ->
                GeneratedCodeSection(
                    code = code,
                    onShareClick = { 
                        partnerCodeService.sharePartnerCode(context, code)
                    }
                )
            }
        }
        
        // Indicateur de chargement
        AnimatedVisibility(
            visible = serviceState.isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFD267A),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.code_generation),
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        }
        
        // Message d'erreur
        serviceState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFEBEE)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error,
                        fontSize = 14.sp,
                        color = Color(0xFFD32F2F),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                partnerCodeService.generatePartnerCode()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFD267A)
                        )
                    ) {
                        Text(stringResource(id = R.string.retry))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Divider "OU"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Divider(
                modifier = Modifier.weight(1f),
                color = Color.Gray.copy(alpha = 0.3f)
            )
            Text(
                text = stringResource(id = R.string.or),
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 14.sp,
                color = Color.Gray
            )
            Divider(
                modifier = Modifier.weight(1f),
                color = Color.Gray.copy(alpha = 0.3f)
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Section saisie de code
        EnterCodeSection(
            enteredCode = enteredCode,
            onCodeChange = { newCode ->
                enteredCode = newCode.take(8).filter { it.isDigit() }
            },
            onConnectClick = {
                coroutineScope.launch {
                    partnerCodeService.connectWithPartnerCode(enteredCode)
                }
            },
            isLoading = serviceState.isLoading,
            isEnabled = enteredCode.length == 8 && !serviceState.isLoading
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Bouton passer (optionnel)
        TextButton(
            onClick = onNextStep,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(id = R.string.skip),
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun GeneratedCodeSection(
    code: String,
    onShareClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.partner_code),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Code affiché avec style monospace et tracking (taille réduite)
            Text(
                text = code,
                fontSize = 24.sp, // Réduit de 48sp à 24sp
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp, // Réduit de 8sp à 2sp
                color = Color(0xFFFD267A),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Boutons d'action (optimisés pour éviter les coupures)
            // Seulement le bouton Partager, bouton Actualiser supprimé
            Button(
                onClick = onShareClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.share),
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Code expires in 24 hours", // Texte temporaire
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnterCodeSection(
    enteredCode: String,
    onCodeChange: (String) -> Unit,
    onConnectClick: () -> Unit,
    isLoading: Boolean,
    isEnabled: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zone cliquable étendue pour le titre et l'espace au-dessus du champ
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        // Zone cliquable pour donner le focus au champ
                        focusRequester.requestFocus() 
                    }
                    .padding(vertical = 8.dp), // Zone cliquable étendue
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.enter_partner_code),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // Champ de saisie
            OutlinedTextField(
                value = enteredCode,
                onValueChange = onCodeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.enter_code_placeholder),
                        fontSize = 20.sp, // Réduit de 24sp à 20sp
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp, // Réduit de 4sp à 2sp
                        textAlign = TextAlign.Center
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 20.sp, // Réduit de 24sp à 20sp
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp, // Réduit de 4sp à 2sp
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFD267A),
                    cursorColor = Color(0xFFFD267A)
                )
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Bouton connecter
            Button(
                onClick = onConnectClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = isEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                    Text(
                        text = if (isLoading) {
                            stringResource(id = R.string.connecting)
                        } else {
                            stringResource(id = R.string.connect)
                        },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
