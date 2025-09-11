package com.yourapp.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.shape.RoundedCornerShape

private val Pink = Color(0xFFFD267A)
private val ScreenBg = Color(0xFFF7F7FA) // ≈ (0.97, 0.97, 0.98)

@Composable
fun RelationshipImprovementStepScreen(
    @StringRes improvements: List<Int>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = ScreenBg,
        bottomBar = {
            BottomContinueBar(
                enabled = selected.isNotEmpty(),
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
                    text = stringResource(id = R.string.relationship_improvement_question),
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
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(improvements) { @StringRes optionId ->
                    val isSelected = optionId in selected
                    SelectableCard(
                        labelRes = optionId,
                        selected = isSelected,
                        onClick = { onToggle(optionId) }
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
                // Si ta ressource s’appelle "continue", utilise: stringResource(id = R.string.`continue`)
                Text(text = stringResource(id = R.string.action_continue))
            }
        }
    }
}

@Composable
private fun SelectableCard(
    @StringRes labelRes: Int,
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
            text = stringResource(id = labelRes),
            color = textColor,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

/* ---------- Exemple d’usage “stateful” prêt à brancher ---------- */

@Composable
fun RelationshipImprovementStepRoute(
    onContinue: () -> Unit
) {
    // Liste d’options -> ids de string resources
    val improvements = listOf(
        R.string.improve_communication,
        R.string.improve_conflict_resolution,
        R.string.improve_trust,
        R.string.improve_quality_time,
        R.string.improve_intimacy,
        R.string.improve_fun
    )

    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }

    RelationshipImprovementStepScreen(
        improvements = improvements,
        selected = selected,
        onToggle = { id ->
            selected = if (id in selected) selected - id else selected + id
        },
        onContinue = onContinue
    )
}
