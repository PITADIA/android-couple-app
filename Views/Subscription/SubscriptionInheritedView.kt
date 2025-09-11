// SubscriptionInheritedView.kt
// Remplace le package par le tien
package com.yourapp.ui.subscription

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.R // ⟵ remplace par le nom de package R de ton app

@Composable
fun SubscriptionInheritedView(
    partnerName: String,
    onContinue: () -> Unit
) {
    // Dégradé premium (or → orange)
    val premiumGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFD700), // #FFD700
            Color(0xFFFFA500), // #FFA500
            Color(0xFFFF8C00)  // #FF8C00
        )
    )

    // Animations “pulse” (cercle et icône)
    val infinite = rememberInfiniteTransition(label = "crownPulse")
    val circleScale by infinite.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circleScale"
    )
    val circleAlpha by infinite.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circleAlpha"
    )
    val iconScale by infinite.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    // Logging équivalent au onAppear Swift
    LaunchedEffect(partnerName) {
        Log.d("SubscriptionInheritedView", "Vue apparue pour héritage de: $partnerName")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // Bloc “couronne” + titres
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    // Cercle pulsant
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer(
                                scaleX = circleScale,
                                scaleY = circleScale,
                                alpha = circleAlpha
                            )
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    )
                    // Icône de “couronne”
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(50.dp)
                            .graphicsLayer(
                                scaleX = iconScale,
                                scaleY = iconScale
                            )
                    )
                }

                // Titre premium
                Text(
                    text = stringResource(R.string.premium_unlocked),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )

                // Message avec nom du partenaire
                Text(
                    text = stringResource(R.string.premium_shared_message, partnerName),
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp)
                )

                // Liste des avantages (apparition retardée comme dans Swift)
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(durationMillis = 1000, delayMillis = 1500))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumFeatureRow(icon = "🔓", text = stringResource(R.string.premium_features_unlocked))
                        PremiumFeatureRow(icon = "🔥", text = stringResource(R.string.unlimited_questions))
                        PremiumFeatureRow(icon = "💎", text = stringResource(R.string.exclusive_premium_content))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Bouton Continuer (apparition retardée)
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(durationMillis = 1000, delayMillis = 2000))
            ) {
                Button(
                    onClick = {
                        Log.d("SubscriptionInheritedView", "🎁 Bouton Continuer pressé")
                        onContinue()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 50.dp),
                    shape = MaterialTheme.shapes.large, // arrondi généreux
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        // Lisibilité: texte “or” sur fond blanc
                        contentColor = Color(0xFFFF8C00)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.discover_premium),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumFeatureRow(icon: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 24.sp)
        Spacer(Modifier.width(15.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Spacer(Modifier.weight(1f))
    }
}
