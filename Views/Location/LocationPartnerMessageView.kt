// LocationPartnerMessageScreen.kt
package com.yourapp.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yourapp.R

@Composable
fun LocationPartnerMessageScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // Fond gris clair (≈ Color(0xFFF7F7FA))
            .background(Color(0xFFF7F7FA))
            // Dégradé rose très doux en arrière-plan
            .drawBehind {
                val gradient = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFD267A).copy(alpha = 0.03f),
                        Color(0xFFFF655B).copy(alpha = 0.02f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                drawRect(brush = gradient)
            }
    ) {
        // Contenu centré verticalement, comme avec Spacer()/Spacer() en SwiftUI
        LocationPartnerExplanation(
            onClose = onDismiss,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
        )
    }
}

@Composable
private fun LocationPartnerExplanation(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.location_partner_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.location_partner_message),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) {
                    Text(text = stringResource(R.string.close))
                }
            }
        }
    }

    // Si tu préfères l’API Android classique :
    // val context = LocalContext.current
    // val title = context.getString(R.string.location_partner_title)
    // Text(title)
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F7FA)
@Composable
private fun PreviewLocationPartnerMessageScreen() {
    MaterialTheme {
        LocationPartnerMessageScreen(onDismiss = {})
    }
}
