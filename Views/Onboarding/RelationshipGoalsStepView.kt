// RelationshipGoalsStepScreen.kt
package com.love2love.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

private val BgGray = Color(0xFFF7F7FA)      // ≈ Color(red: 0.97, green: 0.97, blue: 0.98)
private val BrandPink = Color(0xFFFD267A)   // #FD267A

class RelationshipGoalsViewModel : ViewModel() {
    // Utilise des IDs de ressources pour profiter de strings.xml
    val relationshipGoals: List<Int> = listOf(
        R.string.goal_improve_communication,
        R.string.goal_strengthen_trust,
        R.string.goal_quality_time,
        R.string.goal_nurture_intimacy,
        R.string.goal_long_term_plans
    )

    // Sélections
    val selectedGoals = mutableStateListOf<Int>()

    fun toggle(@StringRes goal: Int) {
        if (selectedGoals.contains(goal)) {
            selectedGoals.remove(goal)
        } else {
            selectedGoals.add(goal)
        }
    }

    fun nextStep() {
        // TODO: navigation / analytics
    }
}

@Composable
fun RelationshipGoalsStepScreen(
    vm: RelationshipGoalsViewModel = viewModel(),
    onContinue: () -> Unit = { vm.nextStep() }
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgGray
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Spacer(Modifier.height(40.dp))

            // Titre aligné à gauche
            Row(
                modifier = Modifier.padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.relationship_goals_question),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    lineHeight = 40.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            // Espace pour rapprocher du centre (équivalent "premier Spacer")
            Spacer(Modifier.height(12.dp))

            // Liste des options (cartes blanches sélectionnables)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 0.dp),
            ) {
                items(vm.relationshipGoals) { goalRes ->
                    GoalCard(
                        labelRes = goalRes,
                        selected = vm.selectedGoals.contains(goalRes),
                        onClick = { vm.toggle(goalRes) }
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
                val enabled = vm.selectedGoals.isNotEmpty()

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
                        text = stringResource(id = R.string.continue_label),
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
    @StringRes labelRes: Int,
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
            text = stringResource(id = labelRes),
            fontSize = 16.sp,
            color = textColor,
            lineHeight = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp)
        )
    }
}
