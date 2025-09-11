// ProgressBar.kt
package com.yourapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yourapp.R

@Composable
fun ProgressBar(
    progress: Float,                      // attendu entre 0f et 1f
    onBackPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val normalized = progress.coerceIn(0f, 1f)
    val showBack = normalized > 0.10f && normalized < 0.80f
    val primaryPink = Color(0xFFFD267A)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton retour (ou espace fixe de 30.dp)
        if (showBack) {
            IconButton(onClick = { onBackPressed?.invoke() }) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button_cd),
                    tint = Color.Black
                )
            }
        } else {
            Spacer(modifier = Modifier.width(30.dp))
        }

        // Centre la ProgressBar
        Spacer(modifier = Modifier.weight(1f))

        LinearProgressIndicator(
            progress = normalized,
            modifier = Modifier
                .width(200.dp)
                .height(8.dp) // équivalent du scaleEffect(y: 2)
                .clip(RoundedCornerShape(50)),
            color = primaryPink,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        // Espace pour équilibrer (symétrie avec la zone de gauche)
        Spacer(modifier = Modifier.width(30.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressBarPreview() {
    ProgressBar(progress = 0.45f)
}
