package com.love2loveapp.views.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.ComponentActivity
import com.love2loveapp.AppDelegate
import com.love2loveapp.models.QuestionCategory
import com.love2loveapp.services.SimpleFreemiumManager
import com.love2loveapp.views.onboarding.OnboardingColors
import com.love2loveapp.views.onboarding.OnboardingDimensions
import com.love2loveapp.views.onboarding.OnboardingTypography
import com.love2loveapp.views.onboarding.SubscriptionStepView
import com.love2loveapp.R

/**
 * 💰 SubscriptionScreen - Paywall In-App avec design unifié
 * 
 * Utilise SubscriptionStepView avec les mêmes clés et design que l'onboarding
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    freemiumManager: SimpleFreemiumManager,
    blockedCategory: QuestionCategory? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Observer les changements d'abonnement
    val currentUser by AppDelegate.appState.currentUser.collectAsState()
    val isSubscribed = currentUser?.isSubscribed ?: false
    
    // Fermer automatiquement si l'abonnement est confirmé
    LaunchedEffect(isSubscribed) {
        if (isSubscribed) {
            freemiumManager.dismissSubscription()
            onDismiss()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingColors.Background) // Utilise le système de design de l'onboarding
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Message contextuel si catégorie bloquée
            blockedCategory?.let { category ->
                CategoryBlockedMessage(
                    category = category,
                    modifier = Modifier.padding(horizontal = OnboardingDimensions.HorizontalPadding)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // Paywall principal avec design unifié de l'onboarding
            SubscriptionStepView(
                onContinue = {
                    // Abonnement réussi
                    freemiumManager.dismissSubscription()
                    onDismiss()
                },
                onSkip = {
                    // Fermer sans achat
                    freemiumManager.dismissSubscription()
                    onDismiss()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 🚫 Message contextuel pour catégorie bloquée avec design unifié
 */
@Composable
private fun CategoryBlockedMessage(
    category: QuestionCategory,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = OnboardingColors.Surface),
        shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusCard),
        elevation = CardDefaults.cardElevation(
            defaultElevation = OnboardingDimensions.ShadowElevation
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Émoji de la catégorie
            Text(
                text = category.emoji,
                fontSize = 48.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Titre catégorie bloquée
            Text(
                text = "Catégorie Premium",
                style = OnboardingTypography.TitleMedium.copy(fontSize = 20.sp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Message explicatif
            Text(
                text = "Déverrouillez ${category.emoji} cette catégorie et toutes les autres fonctionnalités premium avec un abonnement.",
                style = OnboardingTypography.BodyMedium.copy(
                    color = OnboardingColors.OnSurfaceVariant
                ),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

/**
 * 🎉 Écran de succès après achat avec design unifié
 */
@Composable
fun SubscriptionSuccessScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(OnboardingDimensions.HorizontalPadding),
            colors = CardDefaults.cardColors(containerColor = OnboardingColors.Surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = OnboardingDimensions.ShadowElevation
            )
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉",
                    fontSize = 64.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Abonnement Activé !",
                    style = OnboardingTypography.TitleMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Vous avez maintenant accès à toutes les fonctionnalités premium de Love2Love.",
                    style = OnboardingTypography.BodyMedium.copy(
                        color = OnboardingColors.OnSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                
                Spacer(modifier = Modifier.height(30.dp))
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnboardingColors.Primary
                    ),
                    shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusButton),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.continue_button),
                        style = OnboardingTypography.ButtonText
                    )
                }
            }
        }
    }
}