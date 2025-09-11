// UserInitialsAvatar.kt
package com.yourapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.lang.Math.floorMod
import java.util.Locale

@Composable
fun UserInitialsAvatar(
    name: String,
    size: Dp
) {
    val initial = extractFirstLetter(name)
    val bgColor = colorForName(name)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.4f).sp
        )
    }
}

private fun extractFirstLetter(name: String): String {
    val trimmed = name.trim()
    val first = trimmed.firstOrNull() ?: '?'
    return first.toString().uppercase(Locale.getDefault())
}

private fun colorForName(name: String): Color {
    val palette = listOf(
        Color(0xFFFD267A), // Rose principal
        Color(0xFFFF69B4), // Rose vif
        Color(0xFFF06292), // Rose clair
        Color(0xFFE91E63), // Rose intense
        Color(0xFFFF1493), // Fuchsia
        Color(0xFFDA70D6), // Orchidée
        Color(0xFFFF6B9D), // Rose doux
        Color(0xFFE1306C)  // Rose "Instagram"
    )
    val index = floorMod(name.hashCode(), palette.size)
    return palette[index]
}

@Preview(showBackground = true)
@Composable
private fun PreviewUserInitialsAvatar() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        UserInitialsAvatar(name = "Marie", size = 80.dp)
        UserInitialsAvatar(name = "Jean", size = 60.dp)
        UserInitialsAvatar(name = "Sophie", size = 40.dp)
        UserInitialsAvatar(name = "Lucas", size = 100.dp)
    }
}
