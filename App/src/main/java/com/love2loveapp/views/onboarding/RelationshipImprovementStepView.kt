package com.love2loveapp.views.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pink = Color(0xFFFD267A)
private val ScreenBg = Color(0xFFF7F7FA) // ≈ (0.97, 0.97, 0.98)

@Composable
fun RelationshipImprovementStepScreen(
    selectedImprovements: List<String>,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Options d'amélioration depuis le fichier strings.xml
    val improvements = listOf(
        "Share something meaningful together", // improvement_create_strong_moment
        "Revive our connection", // improvement_revive_connection
        "Break out of the routine", // improvement_break_routine
        "Open up like never before" // improvement_say_unsaid
    )

    Scaffold(
        containerColor = ScreenBg,
        bottomBar = {
            BottomContinueBar(
                enabled = selectedImprovements.isNotEmpty(),
                onClick = onContinue
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Spacer(Modifier.height(40.dp))

            // Titre aligné à gauche
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "What would you like to improve in your relationship?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
            }

            // Contenu principal (liste de cartes sélectionnables)
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(improvements) { improvement ->
                    val isSelected = improvement in selectedImprovements
                    SelectableCard(
                        label = improvement,
                        selected = isSelected,
                        onClick = { onToggle(improvement) }
                    )
                }
                // Ajoute un espace en bas pour ne pas être caché par la bottomBar
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun BottomContinueBar(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 30.dp)
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .alpha(if (enabled) 1f else 0.5f)
            ) {
                Text(
                    text = "Continuer",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SelectableCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth = if (selected) 2.dp else 1.dp
    val borderColor = if (selected) Pink else Color.Black.copy(alpha = 0.10f)
    val bgColor = if (selected) Pink else Color.White
    val textColor = if (selected) Color.White else Color.Black

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}
