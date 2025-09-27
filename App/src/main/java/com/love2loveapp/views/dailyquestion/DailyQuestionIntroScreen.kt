package com.love2loveapp.views.dailyquestion

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R

/**
 * 📖 DailyQuestionIntroScreen - Page d'Introduction Questions du Jour
 * Design exact selon RAPPORT_DESIGN_QUESTION_DU_JOUR.md
 * 
 * Background: RGB(247, 247, 250) - Gris très clair
 * Image: mima.png 240x240pt
 * Textes: Centrés avec clés de traduction
 * Bouton: Rose Love2Love #FD267A
 */
@Composable
fun DailyQuestionIntroScreen(
    showConnectButton: Boolean,
    onConnectPartner: () -> Unit,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit
) {
    // 🎨 Background principal - RGB(247, 247, 250)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA)) // Gris très clair selon rapport
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🏷️ Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 100.dp), // Espace augmenté selon rapport
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.daily_question_title), // ✅ Clé de traduction
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
            
            // 🖼️ Image Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Image principale mima - 240x240pt selon rapport
                Image(
                    painter = painterResource(R.drawable.mima), // ✅ Image mima selon rapport
                    contentDescription = null,
                    modifier = Modifier.size(240.dp), // Taille fixe selon rapport
                    contentScale = ContentScale.Fit
                )
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.daily_question_intro_title), // ✅ "A daily love ritual"
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = stringResource(R.string.daily_question_intro_subtitle), // ✅ Description longue
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.7f), // Gris foncé selon rapport
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 30.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 🔘 Bouton Principal avec padding correct
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .padding(bottom = 50.dp) // Marge bottom selon rapport
            ) {
                Button(
                    onClick = if (showConnectButton) onConnectPartner else onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Hauteur selon rapport
                    shape = RoundedCornerShape(28.dp), // cornerRadius(height/2) selon rapport
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFD267A) // ✅ Rose Love2Love selon rapport
                    )
                ) {
                    Text(
                        text = if (showConnectButton) {
                            stringResource(R.string.connect_partner_button) // ✅ "Connect my partner"
                        } else {
                            stringResource(R.string.continue_button) // ✅ "Continue"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}