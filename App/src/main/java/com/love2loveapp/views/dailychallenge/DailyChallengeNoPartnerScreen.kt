package com.love2loveapp.views.dailychallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.love2loveapp.R

/**
 * 🚫 DailyChallengeNoPartnerScreen - Page Sans Partenaire pour Défis
 * Design exact selon RAPPORT_DEFIS_DU_JOUR.md
 * 
 * Background: RGB(247, 247, 247) - Gris clair uniforme
 * Icône: Défi nécessite partenaire
 * Textes: Centrés avec clés de traduction spécifiques aux défis
 * Header: Unifié avec bouton retour
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeNoPartnerScreen(
    onNavigateBack: () -> Unit,
    onConnectPartner: () -> Unit
) {
    // 🎨 Background principal - RGB(247, 247, 247) selon rapport
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7)) // Gris clair uniforme selon rapport
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 🏷️ Header Unifié selon rapport
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 60.dp, bottom = 20.dp), // Padding selon rapport
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton retour
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp) // Taille selon rapport
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Titre centré
                Text(
                    text = stringResource(R.string.daily_challenges_title), // ✅ Clé de traduction
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Spacer équilibrage selon rapport
                Spacer(modifier = Modifier.width(20.dp))
            }
            
            // 🚫 Contenu Sans Partenaire centré
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp) // Espacement selon rapport
                ) {
                    // Icône défis nécessitent partenaire - 60pt selon rapport
                    Text(
                        text = "🎯", // Icône défi
                        fontSize = 60.sp,
                        color = Color.Gray
                    )
                    
                    Text(
                        text = "Partenaire requis", // TODO: Clé de traduction spécifique défis
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    
                    Text(
                        text = "Vous devez vous connecter avec votre partenaire pour accéder aux défis quotidiens.", // TODO: Clé de traduction
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp) // Padding selon rapport
                    )
                }
            }
        }
    }
}
