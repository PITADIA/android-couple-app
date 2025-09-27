package com.love2loveapp.views.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.love2loveapp.R

/**
 * 💬 PartnerLocationMessageScreen - Message pour localisation partenaire
 * 
 * Design exact iOS LocationPartnerExplanationView:
 * - Background: Gris clair + gradient rose doux
 * - Layout: Titre → Description → Icône → Bouton (spacing 30dp)
 * - Clés iOS: partner_turn, partner_location_request, continue_button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerLocationMessageScreen(
    hasPartner: Boolean = false,
    onSendReminder: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Background iOS: Color(red: 0.97, green: 0.97, blue: 0.98) + gradient rose doux
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF7F7F8), // Gris clair iOS
            Color(0xFFFDEEF4)  // Rose doux
        )
    )
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(30.dp) // spacing: 30 iOS
            ) {
                Spacer(modifier = Modifier.height(60.dp)) // Top spacing
                
                // Titre iOS: "partner_turn".localized
                Text(
                    text = stringResource(R.string.partner_turn),
                    fontSize = 28.sp, // .font(.system(size: 28, weight: .bold))
                    fontWeight = FontWeight.Bold,
                    color = Color.Black, // .foregroundColor(.black)
                    textAlign = TextAlign.Center
                )
                
                // Description iOS: "partner_location_request".localized
                Text(
                    text = stringResource(R.string.partner_location_request),
                    fontSize = 16.sp, // .font(.system(size: 16))
                    color = Color.Black.copy(alpha = 0.7f), // .foregroundColor(.black.opacity(0.7))
                    textAlign = TextAlign.Center, // .multilineTextAlignment(.center)
                    modifier = Modifier.padding(horizontal = 30.dp) // .padding(.horizontal, 30)
                )
                
                // Icône localisation iOS: Image(systemName: "location.fill")
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp), // .font(.system(size: 80, weight: .medium))
                    tint = Color.Black // .foregroundColor(.black)
                )
                
                Spacer(modifier = Modifier.weight(1f)) // Push button to bottom
                
                // Bouton Continuer iOS: Button("continue_button".localized)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFD267A) // Color(hex: "#FD267A")
                    ),
                    shape = RoundedCornerShape(28.dp), // .clipShape(RoundedRectangle(cornerRadius: 28))
                    modifier = Modifier
                        .fillMaxWidth() // .frame(maxWidth: .infinity, height: 56)
                        .height(56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.continue_button), // "continue_button".localized
                        fontSize = 18.sp, // .font(.system(size: 18, weight: .semibold))
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White // .foregroundColor(.white)
                    )
                }
                
                Spacer(modifier = Modifier.height(40.dp)) // Bottom spacing
            }
        }
    }
}
