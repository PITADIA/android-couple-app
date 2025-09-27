package com.love2loveapp.views.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎯 DistanceButton - Bouton distance cliquable avec logique contextuelle
 * 
 * Comportements iOS équivalents:
 * - Affichage distance formatée ou "km ?" / "? mi"
 * - Cliquable seulement si permissions nécessaires
 * - Callback différentiel selon contexte (utilisateur vs partenaire)
 * - Design : fond blanc 95%, bordures arrondies 20dp, shadow
 */
@Composable
fun DistanceButton(
    distance: String,
    onClick: (Boolean) -> Unit,
    isClickable: Boolean,
    showPartnerMessageOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = if (isClickable) null else null, // Indication visuelle basique
                enabled = isClickable
            ) {
                android.util.Log.d("DistanceButton", "🎯 CLIC DISTANCE BUTTON:")
                android.util.Log.d("DistanceButton", "  - distance: '$distance'")
                android.util.Log.d("DistanceButton", "  - isClickable: $isClickable")
                android.util.Log.d("DistanceButton", "  - showPartnerMessageOnly: $showPartnerMessageOnly")
                
                if (isClickable) {
                    onClick(showPartnerMessageOnly)
                } else {
                    android.util.Log.d("DistanceButton", "  ❌ CLIC IGNORÉ - bouton non cliquable")
                }
            }
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Text(
                text = distance,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                maxLines = 1
            )
        }
    }
}
