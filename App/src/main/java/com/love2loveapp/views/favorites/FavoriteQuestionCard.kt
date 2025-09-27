package com.love2loveapp.views.favorites

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.models.FavoriteQuestion

/**
 * 🃏 FavoriteQuestionCard - Carte Love2Love selon RAPPORT_DESIGN_FAVORIS.md
 * Design signature avec dégradés roses/sombres et branding Love2Love
 */
@Composable
fun FavoriteQuestionCard(
    favorite: FavoriteQuestion,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .scale(if (isActive) 1.0f else 0.95f)     // Effet de profondeur selon le rapport
            .alpha(if (isActive) 1.0f else 0.8f),     // Transparence arrière-plan selon le rapport
        shape = RoundedCornerShape(20.dp),             // Coins arrondis modernes selon le rapport
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 10.dp else 5.dp  // Ombre adaptative selon le rapport
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 📍 HEADER AVEC DÉGRADÉ ROSE selon le rapport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFF6699),    // RGB(255, 102, 153) selon le rapport
                                Color(0xFFFF99CC)     // RGB(255, 153, 204) selon le rapport
                            )
                        )
                    )
                    .padding(vertical = 20.dp),  // Padding vertical selon le rapport
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = favorite.categoryTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            // 📍 CORPS AVEC DÉGRADÉ SOMBRE SOPHISTIQUÉ selon le rapport
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF33261A),    // RGB(51, 26, 38) selon le rapport
                                Color(0xFF66334D),    // RGB(102, 51, 77) selon le rapport
                                Color(0xFF994D33)     // RGB(153, 77, 51) selon le rapport
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 30.dp)  // Padding horizontal selon le rapport
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    // 💬 QUESTION FAVORITE selon le rapport
                    Text(
                        text = favorite.questionText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,  // Équivalent lineSpacing(6) selon le rapport
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 📍 BRANDING LOVE2LOVE EN BAS selon le rapport
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 30.dp)  // Padding bottom selon le rapport
                    ) {
                        // 🎯 LOGO LOVE2LOVE selon le rapport
                        Image(
                            painter = painterResource(R.drawable.leetchi2),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),  // Taille selon le rapport
                            contentScale = ContentScale.Fit
                        )

                        Text(
                            text = "Love2Love",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)  // Légèrement transparent selon le rapport
                        )
                    }
                }
            }
        }
    }
}
