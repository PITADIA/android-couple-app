@file:Suppress("UnusedImport")

package com.yourapp.ui.subscription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.R
import kotlinx.coroutines.delay

/*
 * strings.xml — ajoute ces clés dans res/values/strings.xml :
 *
 * <string name="premium_access_lost">Accès Premium perdu</string>
 * <string name="subscription_revoked_message">Ton/ta partenaire %1$s a révoqué le partage d’abonnement.</string>
 * <string name="premium_categories_locked">Catégories premium verrouillées</string>
 * <string name="max_64_questions">Limite à 64 questions</string>
 * <string name="premium_content_unavailable">Contenu premium indisponible</string>
 * <string name="get_premium">S’abonner</string>
 * <string name="continue_free_version">Continuer en version gratuite</string>
 *
 * // Utilisation Compose des traductions : stringResource(R.string.key)
 * // Hors Compose : LocalContext.current.getString(R.string.key)
 */

@Composable
fun SubscriptionRevokedScreen(
    partnerName: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Pulsation continue pour le lock + halo
    val infinite: InfiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnim"
    )
    val haloAlpha by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlphaAnim"
    )

    // Apparitions échelonnées (liste + boutons)
    var showBody by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Équivalents des delays .delay(1.5s) et .delay(2s)
        delay(800)  // légère latence avant d’afficher le texte et la liste
        showBody = true
        delay(400)
        showActions = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFCC3333), // ~ (0.8, 0.2, 0.2)
                        Color(0xFFE66633)  // ~ (0.9, 0.4, 0.2)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.weight(1f))

            // Animation cadenas + halo
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .scale(pulse)
                        .alpha(haloAlpha)
                )
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .scale(pulse),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Titre
            Text(
                text = stringResource(id = R.string.premium_access_lost),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Message dynamique avec nom du partenaire
            Text(
                text = stringResource(id = R.string.subscription_revoked_message, partnerName),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Liste des limitations (apparition avec fade)
            AnimatedVisibility(visible = showBody) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LimitationRow(icon = "🔒", text = stringResource(R.string.premium_categories_locked))
                    LimitationRow(icon = "📊", text = stringResource(R.string.max_64_questions))
                    LimitationRow(icon = "💡", text = stringResource(R.string.premium_content_unavailable))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Actions (apparition après la liste)
            AnimatedVisibility(visible = showActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // CTA premium avec fond dégradé
                    GradientButton(
                        text = stringResource(R.string.get_premium),
                        onClick = {
                            // Ouvre paywall / navigation — selon ton flux
                            onContinue()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Continuer en gratuit (texte seul)
                    Text(
                        text = stringResource(R.string.continue_free_version),
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LimitationRow(
    icon: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFD267A), // #FD267A
                        Color(0xFFFF655B)  // #FF655B
                    )
                )
            )
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionRevokedScreenPreview() {
    MaterialTheme {
        SubscriptionRevokedScreen(partnerName = "Alex") {}
    }
}
