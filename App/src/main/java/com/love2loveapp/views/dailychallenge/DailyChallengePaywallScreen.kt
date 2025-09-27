package com.love2loveapp.views.dailychallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.views.onboarding.*

/**
 * 💰 DailyChallengePaywallScreen - Paywall avec design unifié
 * 
 * Utilise le système de design de l'onboarding pour une cohérence parfaite
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengePaywallScreen(
    challengeDay: Int,
    onSubscribe: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = OnboardingColors.Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header avec navigation selon le rapport
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 10.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onNavigateBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OnboardingColors.OnSurface
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OnboardingDimensions.HorizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Titre avec contexte jour selon le rapport
                Text(
                    text = "Débloquez tous les défis",
                    style = OnboardingTypography.TitleLarge.copy(
                        fontSize = 32.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Badge jour
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = OnboardingColors.Primary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "Jour $challengeDay",
                        style = OnboardingTypography.BodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnboardingColors.Primary
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sous-titre selon le rapport
                Text(
                    text = stringResource(R.string.partner_no_payment),
                    style = OnboardingTypography.BodyMedium.copy(
                        color = OnboardingColors.OnSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Message freemium
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3CD)
                    ),
                    shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusCard)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🎯",
                            fontSize = 32.sp
                        )

                        Text(
                            text = "Vous avez profité de 3 jours gratuits de défis ! Continuez avec l'abonnement Love2Love",
                            style = OnboardingTypography.BodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF856404)
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Features selon le rapport (même design que SubscriptionStepView)
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureRow(
                        title = stringResource(R.string.feature_love_map),
                        description = stringResource(R.string.feature_love_map_description)
                    )
                    
                    FeatureRow(
                        title = stringResource(R.string.feature_love_stronger),
                        description = stringResource(R.string.feature_love_stronger_description)
                    )
                    
                    FeatureRow(
                        title = stringResource(R.string.feature_memory_chest),
                        description = stringResource(R.string.feature_memory_chest_description)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Zone bouton selon les spécifications du rapport
            Column(
                modifier = Modifier
                    .background(OnboardingColors.Surface)
                    .padding(OnboardingDimensions.ButtonZoneVerticalPadding)
                    .padding(horizontal = OnboardingDimensions.HorizontalPadding)
            ) {
                Button(
                    onClick = onSubscribe,
                    shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusButton),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OnboardingDimensions.ButtonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnboardingColors.Primary,
                        contentColor = OnboardingColors.Surface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.start_trial),
                        style = OnboardingTypography.ButtonText
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Message partage abonnement
                Text(
                    text = "L'abonnement est automatiquement partagé avec votre partenaire",
                    style = OnboardingTypography.BodySmall.copy(
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Réutilise le composant FeatureRow de SubscriptionStepView
@Composable
private fun FeatureRow(
    title: String,
    description: String
) {
    // VStack(alignment: .leading, spacing: 4) selon le rapport
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = OnboardingTypography.BodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = description,
            style = OnboardingTypography.BodySmall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}