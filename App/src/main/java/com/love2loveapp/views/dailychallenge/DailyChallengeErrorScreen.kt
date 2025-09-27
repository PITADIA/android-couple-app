package com.love2loveapp.views.dailychallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ❌ DailyChallengeErrorScreen - Écran d'Erreur Défis du Jour
 * Équivalent iOS DailyChallengeErrorView (adapté from Questions)
 * 
 * Présente les erreurs avec :
 * - Message d'erreur contextuel
 * - Suggestions de résolution
 * - Bouton retry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
    ) {
        // 🔙 Header avec bouton retour
        TopAppBar(
            title = { 
                Text(
                    text = "Défis du Jour",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color(0xFF333333)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFFF7F7F8)
            )
        )

        // 🔑 CONTENU D'ERREUR
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 🔑 ICÔNE D'ERREUR
                    Text(
                        text = "😕",
                        fontSize = 64.sp
                    )

                    // 🔑 TITRE D'ERREUR
                    Text(
                        text = "Oups, quelque chose s'est mal passé",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center
                    )

                    // 🔑 MESSAGE D'ERREUR
                    Text(
                        text = message,
                        fontSize = 16.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    // 🔑 SUGGESTIONS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF8F9FA)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "💡 Que pouvez-vous faire :",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF333333)
                            )

                            Text(
                                text = "• Vérifiez votre connexion internet",
                                fontSize = 13.sp,
                                color = Color(0xFF666666)
                            )

                            Text(
                                text = "• Assurez-vous d'être connecté à votre partenaire",
                                fontSize = 13.sp,
                                color = Color(0xFF666666)
                            )

                            Text(
                                text = "• Réessayez dans quelques instants",
                                fontSize = 13.sp,
                                color = Color(0xFF666666)
                            )
                        }
                    }

                    // 🔑 BOUTONS D'ACTION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Retour",
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B9D)
                            )
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Réessayer",
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
