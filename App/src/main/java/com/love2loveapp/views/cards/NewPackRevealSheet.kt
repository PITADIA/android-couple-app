package com.love2loveapp.views.cards

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.love2loveapp.R

/**
 * NewPackRevealSheet - Animation full-screen de déblocage de nouveau pack
 * 
 * Réplication exacte de l'iOS :
 * - Fond dégradé rouge/orange identique
 * - Animation d'apparition de l'enveloppe 💌
 * - Textes "Nouvelles cartes ajoutées", "32 nouvelles cartes", "Profitez-en bien !"
 * - Bouton "C'est parti !" pour continuer
 * 
 * Équivalent iOS : NewPackRevealView.swift
 */
@Composable
fun NewPackRevealSheet(
    packNumber: Int,
    questionsCount: Int = 32,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    
    // Animation d'apparition du contenu
    var showContent by remember { mutableStateOf(false) }
    val contentScale by animateFloatAsState(
        targetValue = if (showContent) 1.0f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "contentScale"
    )
    
    LaunchedEffect(Unit) {
        showContent = true
    }
    
    // Dégradé exactement identique iOS
    val gradientColors = listOf(
        Color(0xFFfd267a), // Rouge iOS
        Color(0xFFff655b)  // Orange iOS  
    )
    
    // Full-screen dialog
    Dialog(
        onDismissRequest = { /* Non dismissible */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(colors = gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(50.dp),
                modifier = Modifier.padding(40.dp)
            ) {
                // Animation enveloppe (comme iOS)
                Text(
                    text = "💌",
                    fontSize = 80.sp,
                    modifier = Modifier.scale(contentScale)
                )
                
                // Textes informatifs
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Nouvelles cartes ajoutées",  // TODO: stringResource(R.string.new_cards_added)
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "$questionsCount nouvelles cartes",  // TODO: "$questionsCount ${stringResource(R.string.new_cards)}"
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Profitez-en bien !",  // TODO: stringResource(R.string.enjoy_it)
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                
                // Bouton "C'est parti !" (style iOS)
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.9f),
                        contentColor = Color(0xFF990033) // Rouge foncé pour contraste
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
                ) {
                    Text(
                        text = "C'est parti !",  // TODO: stringResource(R.string.lets_go)
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
