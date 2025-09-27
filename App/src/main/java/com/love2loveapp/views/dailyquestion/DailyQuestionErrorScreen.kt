package com.love2loveapp.views.dailyquestion

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ❌ DailyQuestionErrorScreen - Écran d'Erreur Questions du Jour
 * Équivalent iOS DailyQuestionErrorView.swift
 * 
 * Affiche les erreurs avec :
 * - Message d'erreur contextualisé
 * - Bouton retry
 * - Suggestions utilisateur
 * - Design cohérent Love2Love
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQuestionErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF7F7F8),
                        Color(0xFFFFE1EC)
                    )
                )
            )
    ) {
        // 🔙 Header
        TopAppBar(
            title = { 
                Text(
                    text = stringResource(com.love2loveapp.R.string.daily_question_title),
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
                containerColor = Color.Transparent
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 🎨 Carte d'erreur principale
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
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
                    // 😔 Icône d'erreur
                    Text(
                        text = "😔",
                        fontSize = 64.sp
                    )

                    // Titre d'erreur
                    Text(
                        text = "Oups, une erreur s'est produite",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center
                    )

                    // Message d'erreur
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF5F5)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = message,
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    // Suggestions
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Suggestions :",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333),
                            textAlign = TextAlign.Center
                        )

                        ErrorSuggestionItem(
                            text = "Vérifiez votre connexion Internet"
                        )

                        ErrorSuggestionItem(
                            text = "Assurez-vous d'être connecté à votre partenaire"
                        )

                        ErrorSuggestionItem(
                            text = "Essayez de relancer l'application"
                        )
                    }

                    // Bouton retry
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B9D)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Réessayer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            // 💡 Informations supplémentaires
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE3F2FD)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "💡",
                        fontSize = 20.sp
                    )

                    Text(
                        text = "Si le problème persiste, contactez le support depuis les paramètres de l'application",
                        fontSize = 13.sp,
                        color = Color(0xFF1565C0),
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 📝 Composant suggestion d'erreur
 */
@Composable
private fun ErrorSuggestionItem(
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            fontSize = 14.sp,
            color = Color(0xFFFF6B9D),
            fontWeight = FontWeight.Bold
        )

        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFF666666),
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
