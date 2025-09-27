package com.love2loveapp.views.statistics.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.models.StatisticCard as StatisticCardModel

/**
 * 🎨 StatisticCard - Composant carte statistique individuelle
 * 
 * Équivalent Android exact de StatisticCardView iOS :
 * - Layout 140dp hauteur avec icône top-right, valeur+titre bottom-left
 * - Couleurs thématiques par type statistique (rose, orange, bleu, violet)
 * - Hiérarchie visuelle : valeur 32sp bold + titre 14sp medium  
 * - Shadow subtile et animations clics Material3
 */
@Composable
fun StatisticCard(
    statisticCard: StatisticCardModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(140.dp) // Hauteur fixe comme iOS
            .clickable { statisticCard.onClick?.invoke() },
        colors = CardDefaults.cardColors(
            containerColor = statisticCard.backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp,    // Équivalent shadow iOS
            pressedElevation = 12.dp    // Animation clic
        ),
        shape = RoundedCornerShape(16.dp) // Border radius iOS
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            
            // 🔑 ICÔNE EN HAUT À DROITE (équivalent iOS HStack alignement)
            Icon(
                painter = painterResource(statisticCard.iconRes),
                contentDescription = stringResource(statisticCard.titleRes),
                tint = statisticCard.iconColor,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd)
            )
            
            // 🔑 VALEUR + TITRE EN BAS À GAUCHE (équivalent iOS VStack alignment)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.7f), // Éviter collision avec icône
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                
                // 🔑 VALEUR PRINCIPALE (32sp bold, équivalent iOS)
                Text(
                    text = statisticCard.displayValue,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = statisticCard.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
                
                // 🔑 TITRE (14sp medium, 2 lignes max, équivalent iOS)
                Text(
                    text = stringResource(statisticCard.titleRes),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = statisticCard.textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    lineHeight = 18.sp // Espacement lignes optimisé
                )
            }
            
            // 🎯 INDICATEUR INTERACTIF (si cliquable)
            if (statisticCard.isInteractive) {
                // Subtil indicateur visuel pour signaler l'interactivité
                // (Pas dans iOS mais améliore UX Android)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(4.dp)
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(2.dp)
                        )
                ) {
                    // Point discret pour signaler l'interactivité
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = statisticCard.textColor.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(2.dp)
                    ) {}
                }
            }
        }
    }
}

/**
 * 🎨 StatisticCard avec données directes (pour usage simple)
 * Surcharge du composable principal pour usage direct sans modèle
 */
@Composable
fun StatisticCard(
    title: String,
    value: String,
    iconRes: Int,
    iconColor: Color,
    backgroundColor: Color,
    textColor: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(140.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Icône top-right
            Icon(
                painter = painterResource(iconRes),
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd)
            )
            
            // Valeur + titre bottom-left
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.7f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Valeur
                Text(
                    text = if (value.isEmpty()) "—" else value,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Titre
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
