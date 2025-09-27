package com.love2loveapp.views.dailyquestion.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.models.DailyQuestion

/**
 * 🎯 DailyQuestionCard - Carte Question du Jour
 * Équivalent iOS carte question dans DailyQuestionMainView
 * 
 * Affiche la question actuelle avec :
 * - Gradient background Love2Love  
 * - Numéro du jour
 * - Texte localisé de la question
 * - Date formatée
 * - Animation subtile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQuestionCard(
    question: DailyQuestion,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        onClick = { onClick?.invoke() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF6B9D), // Rose Love2Love
                            Color(0xFFE63C6B)  // Rose plus foncé
                        )
                    )
                )
                .padding(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 📅 Badge numéro du jour
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Jour ${question.questionDay}",
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // ❓ Question principale
                Text(
                    text = question.getLocalizedText(context),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // 📅 Date
                Text(
                    text = question.formattedDate,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Normal
                )

                // 💬 Indicateur de réponses si applicable
                if (question.responseCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "💬 ${question.responseCount} réponse${if (question.responseCount > 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 🎨 Variante carte compacte pour liste ou aperçu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyQuestionCardCompact(
    question: DailyQuestion,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF6B9D),
                            Color(0xFFFF8CC8)
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Badge jour
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.width(50.dp)
            ) {
                Text(
                    text = "J${question.questionDay}",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            // Question tronquée
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = question.getLocalizedText(context),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    lineHeight = 18.sp
                )
                
                Text(
                    text = question.shortFormattedDate,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Indicateur réponses
            if (question.responseCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${question.responseCount}",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
