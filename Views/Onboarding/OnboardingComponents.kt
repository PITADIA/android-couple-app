// OnboardingComponents.kt
package com.yourapp.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.R

private val PinkStart = Color(0xFFFD267A)
private val PinkEnd   = Color(0xFFFF6B9D)

/** --- Onboarding Progress Bar --- */
@Composable
fun OnboardingProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalSteps.coerceAtLeast(1)
    val target = (currentStep.coerceIn(0, safeTotal)).toFloat() / safeTotal.toFloat()
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "progressAnim"
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(id = R.string.step_counter, currentStep, totalSteps),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // Track + progress
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(
                        Brush.horizontalGradient(listOf(PinkStart, PinkEnd))
                    )
            )
        }
    }
}

/** --- Onboarding Back Button --- */
@Composable
fun OnboardingBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(25.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(id = R.string.back),
            tint = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(id = R.string.back),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

/** --- Onboarding Next Button (gradient) --- */
@Composable
fun OnboardingNextButton(
    isEnabled: Boolean = true,
    title: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = title ?: stringResource(id = R.string.continue_label)
    val shape = RoundedCornerShape(25.dp)

    val base = Modifier
        .clip(shape)
        .then(
            if (isEnabled) {
                Modifier
                    .background(Brush.horizontalGradient(listOf(PinkStart, PinkEnd)))
                    .clickable(enabled = true, onClick = onClick)
            } else {
                Modifier
                    .background(Color.White.copy(alpha = 0.20f))
                    .semantics { disabled() }
            }
        )
        .padding(horizontal = 20.dp, vertical = 12.dp)

    Row(
        modifier = modifier.then(base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.5f)
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = label,
            tint = if (isEnabled) Color.White else Color.White.copy(alpha = 0.5f)
        )
    }
}

/** --- Preview --- */
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun OnboardingComponentsPreview() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A0C16), Color(0xFF26101B))
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        OnboardingProgressBar(currentStep = 3, totalSteps = 8)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OnboardingBackButton(onClick = {})
            Spacer(modifier = Modifier.weight(1f))
            OnboardingNextButton(
                isEnabled = true,
                // Exemple si tu veux passer une chaîne venant du contexte hors Compose :
                title = context.getString(R.string.continue_label),
                onClick = {}
            )
        }
    }
}
