package com.love2loveapp.views.tutorial

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import kotlinx.coroutines.launch
import com.love2loveapp.R
import com.love2loveapp.services.PartnerCodeService
import java.util.Date

/**
 * 👥 PartnerManagementScreen - Gestion connexion partenaire
 * Design identique à PartnerCodeStepScreen pour cohérence
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerManagementScreen(
    currentPartnerName: String? = null,
    onConnectWithCode: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val partnerCodeService = remember { PartnerCodeService.shared }
    val coroutineScope = rememberCoroutineScope()
    
    // États locaux
    var enteredCode by remember { mutableStateOf("") }
    
    // États du service
    val serviceState by partnerCodeService.state.collectAsState()
    
    // Initialisation au montage
    LaunchedEffect(Unit) {
        // 🔍 Vérifier connexion existante TOUJOURS (logique iOS)
        partnerCodeService.checkExistingConnection()
        
        // 🔑 Génération code seulement si NON connecté
        if (!serviceState.isConnected) {
            partnerCodeService.generatePartnerCode()
        }
    }
    
    // 🔄 Gestion de la connexion réussie (SEULEMENT pour nouvelles connexions)
    // Note: La fermeture automatique est supprimée car cette vue sert maintenant
    // aussi à afficher les infos de déconnexion pour les connexions existantes
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F7FA))
        ) {
            // Contenu scrollable avec arrangement conditionnel
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (serviceState.isConnected) Arrangement.Center else Arrangement.Top
            ) {
                // 🎯 ESPACEUR CONDITIONNEL (seulement pour vue non connectée)
                if (!serviceState.isConnected) {
                    Spacer(modifier = Modifier.height(60.dp))
                }
                
                // 🎯 TITRE CONDITIONNEL selon l'état (logique iOS)
                if (serviceState.isConnected) {
                    Text(
                        text = stringResource(R.string.connected_with_partner),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.connect_with_partner),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = stringResource(R.string.connect_partner_description),
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
                
                // 🎯 ESPACEUR CONDITIONNEL (seulement pour vue non connectée)
                if (!serviceState.isConnected) {
                    Spacer(modifier = Modifier.height(40.dp))
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }
                
                // 🔄 CONTENU CONDITIONNEL selon l'état (logique iOS)
                if (serviceState.isConnected) {
                    // ✅ VUE ÉTAT CONNECTÉ - Section Déconnexion (iOS) - DÉJÀ CENTRÉE
                    ConnectedPartnerSection(
                        partnerInfo = serviceState.partnerInfo,
                        isLoading = serviceState.isLoading,
                        onDisconnectClick = {
                            coroutineScope.launch {
                                val success = partnerCodeService.disconnectPartner()
                                // Note: L'utilisateur peut fermer manuellement la vue avec le bouton X
                                // La fermeture automatique est supprimée pour plus de contrôle
                            }
                        }
                    )
                } else {
                    // Section génération de code (identique à PartnerCodeStepScreen)
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
                    
                    // Indicateur de chargement (identique à PartnerCodeStepScreen)
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
                    
                    // Message d'erreur (identique à PartnerCodeStepScreen)
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
                                    Text("Réessayer", color = Color.White)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    // Divider "OU" (identique à PartnerCodeStepScreen)
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
                    
                    // Section saisie de code (identique à PartnerCodeStepScreen)
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
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
            
            // Bouton fermer en haut à droite (par-dessus le contenu)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Fermer",
                    tint = Color.Gray
                )
            }
        }
    }
}              // ferme PartnerManagementScreen

/**
 * 👤 Section Partenaire Connecté - Vue Déconnexion (iOS Design)
 */
@Composable
private fun ConnectedPartnerSection(
    partnerInfo: PartnerCodeService.PartnerInfo?,
    isLoading: Boolean,
    onDisconnectClick: () -> Unit
) {
    var showDisconnectAlert by remember { mutableStateOf(false) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 👤 INFORMATIONS DU PARTENAIRE (Card iOS)
        partnerInfo?.let { info ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Nom du partenaire
                    Text(
                        text = "${stringResource(R.string.partner_name)} ${info.name}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )

                    // Date de connexion
                    Text(
                        text = "${stringResource(R.string.connected_on)} ${formatDate(info.connectedAt)}",
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                    )

                    // 👑 Badge Premium si partenaire abonné (iOS design)
                    if (info.isSubscribed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700), // Or
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.shared_premium),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // ❌ BOUTON DE DÉCONNEXION (iOS Design Rouge Destructif)
        Button(
            onClick = { showDisconnectAlert = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            border = BorderStroke(1.dp, Color.Transparent),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.Red,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.disconnect),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Red // Rouge destructif iOS
                )
            }
        }
    }

    // 🚨 ALERTE DE CONFIRMATION DÉCONNEXION (iOS Style)
    if (showDisconnectAlert) {
        DisconnectConfirmationDialog(
            onConfirm = {
                showDisconnectAlert = false
                onDisconnectClick()
            },
            onDismiss = {
                showDisconnectAlert = false
            }
        )
    }
}

/**
 * 🚨 Dialog de Confirmation Déconnexion (iOS Design)
 */
@Composable
private fun DisconnectConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.disconnect),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = stringResource(R.string.disconnect_confirmation),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(
                    text = stringResource(R.string.disconnect),
                    color = Color.Red,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = Color.Gray
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Formatage de date localisé (comme iOS)
 */
private fun formatDate(date: Date): String {
    val formatter = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
    return formatter.format(date)
}

/**
 * Section d'affichage du code généré (identique à PartnerCodeStepScreen)
 */
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Code affiché
            Text(
                text = code.chunked(4).joinToString(" "),
                fontSize = 24.sp, // Réduit de 48sp à 24sp
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFD267A),
                letterSpacing = 2.sp, // Réduit de 8sp à 2sp
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Bouton partager
            Button(
                onClick = onShareClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A)
                )
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(id = R.string.share),
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.share),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Section de saisie du code partenaire (identique à PartnerCodeStepScreen)
 */
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
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.connect_partner_button),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
