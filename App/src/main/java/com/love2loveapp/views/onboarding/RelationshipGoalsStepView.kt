package com.love2loveapp.views.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgGray = Color(0xFFF7F7FA)      // ≈ Color(red: 0.97, green: 0.97, blue: 0.98)
private val BrandPink = Color(0xFFFD267A)   // #FD267A

@Composable
fun RelationshipGoalsStepScreen(
    selectedGoals: List<String>,
    onGoalToggle: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Options d'objectifs relationnels depuis le fichier strings.xml
    val relationshipGoals = listOf(
        "Build a deeper connection", // goal_create_connection
        "Feel close again, like before", // goal_find_complicity  
        "Increase the passion between us", // goal_increase_passion
        "Share more laughs together", // goal_share_more_laughs
        "Talk about what we usually avoid" // goal_talk_avoided_subjects
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = BgGray
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.height(40.dp))

            // Titre aligné à gauche
            Row(
                modifier = Modifier.padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "What are your relationship goals?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    lineHeight = 40.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            // Espace pour rapprocher du centre
            Spacer(Modifier.height(12.dp))

            // Liste des options (cartes blanches sélectionnables)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 0.dp),
            ) {
                items(relationshipGoals) { goal ->
                    GoalCard(
                        label = goal,
                        selected = selectedGoals.contains(goal),
                        onClick = { onGoalToggle(goal) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Zone blanche collée en bas avec le bouton Continuer
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .padding(vertical = 30.dp, horizontal = 30.dp)
            ) {
                val enabled = selectedGoals.isNotEmpty()

                Button(
                    onClick = onContinue,
                    enabled = enabled,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        // Pour mimer l'opacité 0.5 quand désactivé
                        .alpha(if (enabled) 1f else 0.5f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPink,
                        contentColor = Color.White,
                        disabledContainerColor = BrandPink, // on contrôle l'alpha nous-mêmes
                        disabledContentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Continuer",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) BrandPink else Color.White
    val borderColor = if (selected) BrandPink else Color.Black.copy(alpha = 0.1f)
    val textColor = if (selected) Color.White else Color.Black
    val borderWidth = if (selected) 2.dp else 1.dp

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = textColor,
            lineHeight = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp)
        )
    }
}
