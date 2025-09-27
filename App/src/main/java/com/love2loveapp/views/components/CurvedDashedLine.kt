package com.love2loveapp.views.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 📐 CurvedDashedLine - Ligne courbe tirets design entre avatars
 * 
 * Équivalent exact de CurvedDashedLine iOS:
 * - Courbe quadratique de liaison entre les avatars
 * - Point de contrôle au centre, courbure vers le bas (15dp)
 * - Style tirets : 8 points pleins, 4 points vides
 * - Couleur blanche, épaisseur 3dp
 * - Hauteur frame 40dp
 */
@Composable
fun CurvedDashedLine(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Dp = 3.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 4.dp,
    curveOffset: Dp = 15.dp
) {
    Canvas(modifier = modifier) {
        val path = Path()
        
        // Points de départ et fin (extrémités horizontales)
        val startPoint = Offset(0f, size.height / 2)
        val endPoint = Offset(size.width, size.height / 2)
        
        // Point de contrôle pour courbe (au centre, légèrement vers le bas)
        val controlPoint = Offset(
            size.width / 2,
            size.height / 2 + curveOffset.toPx()
        )
        
        // Construction du chemin courbe quadratique
        path.moveTo(startPoint.x, startPoint.y)
        path.quadraticBezierTo(
            controlPoint.x, controlPoint.y,
            endPoint.x, endPoint.y
        )
        
        // Dessin avec style tirets
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(
                        dashLength.toPx(),
                        gapLength.toPx()
                    ),
                    phase = 0f
                )
            )
        )
    }
}
