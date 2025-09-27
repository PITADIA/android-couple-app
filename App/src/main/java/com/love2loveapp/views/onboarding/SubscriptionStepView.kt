package com.love2loveapp.views.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.love2loveapp.R

@Composable
fun SubscriptionStepView(
    onContinue: () -> Unit,
    onSkip: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // État de sélection du plan (mensuel par défaut)
    var selectedPlan by remember { mutableStateOf("monthly") }
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = OnboardingColors.Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Croix selon le rapport
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 10.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = { onSkip?.invoke() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
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

                // Titre Principal selon le rapport (font(.system(size: 32, weight: .bold)))
                Text(
                    text = stringResource(R.string.choose_plan),
                    style = OnboardingTypography.TitleLarge.copy(
                        fontSize = 32.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

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

                // Features selon le rapport (NewFeatureRow)
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureRow(
                        title = stringResource(R.string.feature_love_stronger),
                        description = stringResource(R.string.feature_love_stronger_description)
                    )
                    
                    FeatureRow(
                        title = stringResource(R.string.feature_memory_chest),
                        description = stringResource(R.string.feature_memory_chest_description)
                    )
                    
                    FeatureRow(
                        title = stringResource(R.string.feature_love_map),
                        description = stringResource(R.string.feature_love_map_description)
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // PlanSelectionCard selon le rapport
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Plan Mensuel avec Badge "POPULAIRE"
                    PlanCard(
                        planType = "monthly",
                        title = "Mensuel",
                        subtitle = stringResource(R.string.no_payment_required_now),
                        price = "9,99 €",
                        period = "/mois",
                        isPopular = true,
                        isSelected = selectedPlan == "monthly",
                        onClick = { selectedPlan = "monthly" }
                    )
                    
                    // Plan Hebdomadaire
                    PlanCard(
                        planType = "weekly", 
                        title = "Hebdomadaire",
                        subtitle = stringResource(R.string.no_commitment_cancel_anytime),
                        price = "2,99 €",
                        period = "/semaine",
                        isPopular = false,
                        isSelected = selectedPlan == "weekly",
                        onClick = { selectedPlan = "weekly" }
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
                // Bouton principal selon le rapport (font(.system(size: 18, weight: .bold)))
                Button(
                    onClick = {
                        isLoading = true
                        // Simuler l'achat
                        // Dans une vraie implémentation, ici on appellerait le service de billing
                        onContinue()
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusButton),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OnboardingDimensions.ButtonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnboardingColors.Primary,
                        contentColor = OnboardingColors.Surface
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = OnboardingColors.Surface,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LOADING...",
                            style = OnboardingTypography.ButtonText
                        )
                    } else {
                        Text(
                            text = if (selectedPlan == "monthly") 
                                stringResource(R.string.start_trial) else stringResource(R.string.continue_button),
                            style = OnboardingTypography.ButtonText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Liens légaux selon le rapport (HStack(spacing: 15))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { /* Ouvrir Terms */ }) {
                        Text(
                            text = stringResource(R.string.terms),
                            style = OnboardingTypography.BodySmall.copy(
                                fontSize = 12.sp
                            )
                        )
                    }
                    
                    Text(
                        text = "•",
                        style = OnboardingTypography.BodySmall.copy(
                            fontSize = 12.sp
                        )
                    )
                    
                    TextButton(onClick = { /* Ouvrir Privacy Policy */ }) {
                        Text(
                            text = stringResource(R.string.privacy_policy),
                            style = OnboardingTypography.BodySmall.copy(
                                fontSize = 12.sp
                            )
                        )
                    }
                    
                    Text(
                        text = "•",
                        style = OnboardingTypography.BodySmall.copy(
                            fontSize = 12.sp
                        )
                    )
                    
                    TextButton(onClick = { /* Restaurer */ }) {
                        Text(
                            text = stringResource(R.string.restore),
                            style = OnboardingTypography.BodySmall.copy(
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

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

@Composable
private fun PlanCard(
    planType: String,
    title: String,
    subtitle: String,
    price: String,
    period: String,
    isPopular: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                OnboardingColors.Primary.copy(alpha = 0.1f) else OnboardingColors.Surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected)
                OnboardingColors.Primary else OnboardingColors.Outline
        ),
        shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusCard),
        elevation = CardDefaults.cardElevation(
            defaultElevation = OnboardingDimensions.ShadowElevation
        )
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = OnboardingTypography.BodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = subtitle,
                        style = OnboardingTypography.BodySmall
                    )
                }
                
                // Prix avec style selon le rapport
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = price,
                        style = OnboardingTypography.ButtonText.copy(
                            color = OnboardingColors.Primary,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = period,
                        style = OnboardingTypography.BodySmall.copy(
                            fontSize = 14.sp
                        )
                    )
                }
            }
            
            // Badge "POPULAIRE" en overlay selon le rapport
            if (isPopular) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = (-8).dp),
                    colors = CardDefaults.cardColors(containerColor = OnboardingColors.Primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "POPULAIRE",
                        style = OnboardingTypography.BodySmall.copy(
                            color = OnboardingColors.Surface,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
