// PartnerInviteCard.kt
package com.yourapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite

private val Pink = Color(0xFFFD267A)

@Composable
fun PartnerInviteCard(
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    Card(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .clickable { onTap() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône cœur avec rayons
            HeartWithRays(
                modifier = Modifier.size(50.dp),
                heartColor = Pink,
                raysColor = Pink.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Textes d'invitation
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(id = R.string.invite_partner),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 2
                )
                Text(
                    text = stringResource(id = R.string.invite_partner_description),
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Chevron (dessiné en Canvas pour éviter dépendances d’icônes étendues)
            ChevronRight(
                modifier = Modifier.size(16.dp),
                color = Pink
            )
        }
    }
}

@Composable
private fun HeartWithRays(
    modifier: Modifier = Modifier,
    heartColor: Color,
    raysColor: Color
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Rayons autour du cœur
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val rayOffset = 20.dp.toPx()   // distance du centre
            val rayLength = 8.dp.toPx()    // longueur du rayon
            val stroke = 2.dp.toPx()
            val steps = 8                  // 8 rayons (tous les 45°)

            repeat(steps) { i ->
                val angleDeg = i * 45.0 - 90.0   // commence en haut (-90°)
                val angle = Math.toRadians(angleDeg).toFloat()
                val dx = cos(angle)
                val dy = sin(angle)

                val start = Offset(
                    x = center.x + dx * rayOffset,
                    y = center.y + dy * rayOffset
                )
                val end = Offset(
                    x = start.x + dx * rayLength,
                    y = start.y + dy * rayLength
                )

                drawLine(
                    color = raysColor,
                    start = start,
                    end = end,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }

        // Cœur principal
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = heartColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ChevronRight(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        // Deux segments pour former ">"
        val p1 = Offset(x = size.width * 0.25f, y = size.height * 0.20f)
        val p2 = Offset(x = size.width * 0.70f, y = size.height * 0.50f)
        val p3 = Offset(x = size.width * 0.25f, y = size.height * 0.80f)

        drawLine(color = color, start = p1, end = p2, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = p2, end = p3, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Preview(showBackground = true)
@Composable
private fun PartnerInviteCardPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0310)) // proche de ton fond foncé en preview Swift
            .padding(vertical = 24.dp)
    ) {
        PartnerInviteCard(onTap = { /* preview */ })
    }
}
