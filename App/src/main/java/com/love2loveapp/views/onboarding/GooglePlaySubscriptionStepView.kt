package com.love2loveapp.views.onboarding

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.love2loveapp.R
import com.love2loveapp.services.billing.GooglePlayBillingService

/**
 * Page d'abonnement Google Play - Design iOS sophistiqué selon le rapport
 */
@Composable
fun GooglePlaySubscriptionStepView(
    activity: ComponentActivity,
    onComplete: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Services - Protection contre initialisation multiple
    val billingService = remember { GooglePlayBillingService.getInstance(context) }
    
    // États observables du service billing avec gestion d'erreur
    val isSubscribed by billingService.isSubscribed.collectAsState()
    val isLoading by billingService.isLoading.collectAsState()
    val errorMessage by billingService.errorMessage.collectAsState()
    val selectedPlan by billingService.selectedPlan.collectAsState()
    val productDetails by billingService.productDetails.collectAsState()
    val billingConnectionState by billingService.billingConnectionState.collectAsState()
    
    // Protection contre les crashs UI
    var hasUIError by remember { mutableStateOf(false) }
    
    // Catch des erreurs UI Compose
    LaunchedEffect(Unit) {
        try {
            // Initialisation stable après rendu initial
            kotlinx.coroutines.delay(200)
        } catch (e: Exception) {
            Log.e("SubscriptionStep", "❌ Erreur UI Compose: ${e.message}", e)
            hasUIError = true
        }
    }
    
    // État local pour l'affichage des erreurs
    var showingError by remember { mutableStateOf(false) }
    
    // Observer les erreurs pour déclencher l'affichage
    LaunchedEffect(errorMessage) {
        showingError = errorMessage != null
    }
    
    // État local pour la vérification serveur
    var isValidatingSubscription by remember { mutableStateOf(false) }
    
    // Gestion de la completion automatique si abonné
    LaunchedEffect(isSubscribed, isValidatingSubscription) {
        // Protection contre les changements d'état rapides qui peuvent causer des bugs Compose
        kotlinx.coroutines.delay(100) // Petit délai pour stabiliser l'UI
        
        if (isSubscribed && !isValidatingSubscription) {
            Log.d("SubscriptionStep", "✅ Abonnement actif détecté - Passage à l'étape suivante")
            onComplete()
        }
    }
    
    // Observer les changements d'état de validation du BillingService
    LaunchedEffect(isLoading) {
        if (isLoading) {
            isValidatingSubscription = true
            Log.d("SubscriptionStep", "🔍 Validation d'abonnement en cours...")
        } else {
            isValidatingSubscription = false
        }
    }
    
    // Gestion d'erreur UI - Fallback si Compose crash
    if (hasUIError) {
        FallbackSubscriptionScreen(
            onComplete = onComplete,
            onSkip = onSkip
        )
        return
    }
    
    // Layout principal selon le rapport iOS
    // Workaround pour bug Compose hover events
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f)) // #F7F7F8 selon le rapport
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()), // Ajout du scroll manquant
            verticalArrangement = Arrangement.spacedBy(0.dp) // VStack(spacing: 0) selon le rapport
        ) {
            // 1. Header avec croix (padding top: 10pt) selon le rapport iOS
            if (onSkip != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 10.dp), // Padding selon le rapport
                    horizontalArrangement = Arrangement.Start // Collé en haut à gauche
                ) {
                    IconButton(
                        onClick = {
                            Log.d("SubscriptionStep", "❌ Fermeture paywall - sécurisation navigation")
                            try {
                                // 1. Nettoyer l'état du billing service
                                billingService.clearError()
                                
                                // 2. Fermer toute connexion billing en cours
                                activity.lifecycleScope.launch {
                                    try {
                                        // Arrêter les opérations billing en cours
                                        billingService.cleanup()
                                    } catch (e: Exception) {
                                        Log.w("SubscriptionStep", "Erreur cleanup billing: ${e.message}")
                                    }
                                    
                                    // 3. Navigation sécurisée vers la suite
                                    try {
                                        onSkip()
                                        Log.d("SubscriptionStep", "✅ Navigation sans premium réussie")
                                    } catch (e: Exception) {
                                        Log.e("SubscriptionStep", "Erreur navigation onSkip: ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SubscriptionStep", "Erreur fermeture paywall: ${e.message}")
                                // Fallback : essayer la navigation directe
                                try {
                                    onSkip()
                                } catch (fallbackError: Exception) {
                                    Log.e("SubscriptionStep", "Erreur fallback: ${fallbackError.message}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Continuer sans premium",
                            tint = Color.Black, // Noir selon le rapport
                            modifier = Modifier.size(18.dp) // Taille 18pt selon le rapport
                        )
                    }
                }
            }
            
            // 2. Section Titre + sous-titre (padding top réduit)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp), // Réduit de 15pt à 8pt
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing: 8pt selon le rapport
            ) {
                Text(
                    text = stringResource(R.string.choose_plan), // Clé du rapport iOS
                    fontSize = 28.sp, // Réduit de 32pt à 28pt
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = stringResource(R.string.partner_no_payment), // Clé du rapport iOS
                    fontSize = 14.sp, // Réduit de 16pt à 14pt
                    color = Color.Black.copy(alpha = 0.7f), // Noir 70% opacité selon le rapport
                    textAlign = TextAlign.Center
                )
            }
            
            // 3. Spacer fixe (20pt) selon le rapport
            Spacer(modifier = Modifier.height(20.dp))
            
            // 4. Section Fonctionnalités Premium (padding horizontal: 25pt) selon le rapport iOS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp), // Padding selon le rapport
                verticalArrangement = Arrangement.spacedBy(25.dp) // Spacing: 25pt selon le rapport
            ) {
                NewFeatureRow(
                    title = stringResource(R.string.feature_love_stronger), // Clé du rapport iOS
                    subtitle = stringResource(R.string.feature_love_stronger_description)
                )
                
                NewFeatureRow(
                    title = stringResource(R.string.feature_memory_chest), // Clé du rapport iOS
                    subtitle = stringResource(R.string.feature_memory_chest_description)
                )
                
                NewFeatureRow(
                    title = stringResource(R.string.feature_love_map), // Clé du rapport iOS
                    subtitle = stringResource(R.string.feature_love_map_description)
                )
            }
            
            // 5. Spacer flexible selon le rapport
            Spacer(modifier = Modifier.weight(1f))
            
            // 5.5. Espace supplémentaire entre fonctionnalités et plans
            Spacer(modifier = Modifier.height(24.dp))
            
            // 6. Section Sélection Plans selon le rapport iOS
            PlanSelectionSection(
                selectedPlan = selectedPlan,
                productDetails = productDetails,
                billingService = billingService,
                onPlanSelected = { plan ->
                    Log.d("SubscriptionStep", "📋 Plan sélectionné: $plan")
                    billingService.selectPlan(plan)
                }
            )
            
            // 7. Bouton principal selon le rapport iOS
            SubscriptionButtonIOS(
                selectedPlan = selectedPlan,
                isLoading = isLoading,
                isReady = billingConnectionState == GooglePlayBillingService.BillingConnectionState.CONNECTED,
                onPurchase = {
                    Log.d("SubscriptionStep", "🛒 Démarrage achat: $selectedPlan")
                    billingService.clearError()
                    activity.lifecycleScope.launch {
                        billingService.purchaseSubscription(activity)
                    }
                }
            )
            
            // 8. Spacer final (12pt) selon le rapport
            Spacer(modifier = Modifier.height(12.dp))
            
            // 9. Section Légale (bas de page) selon le rapport iOS
            LegalLinksSectionIOS()
        }
        
        // Overlay d'erreur selon le rapport iOS
        AnimatedVisibility(
            visible = showingError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ErrorOverlay(
                message = errorMessage ?: "",
                onDismiss = {
                    showingError = false
                    billingService.clearError()
                },
                onRetry = {
                    showingError = false
                    billingService.clearError()
                    activity.lifecycleScope.launch {
                        billingService.purchaseSubscription(activity)
                    }
                }
            )
        }
    }
}

// Composant NewFeatureRow selon le rapport iOS
@Composable
private fun NewFeatureRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing: 8pt selon le rapport
    ) {
        Text(
            text = title,
            fontSize = 17.sp, // Réduit de 19pt à 17pt
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        Text(
            text = subtitle,
            fontSize = 13.sp, // Réduit de 15pt à 13pt
            color = Color.Black.copy(alpha = 0.7f), // Noir 70% opacité selon le rapport
            lineHeight = 18.sp // Réduit proportionnellement
        )
    }
}

// Section Sélection Plans selon le rapport iOS
@Composable
private fun PlanSelectionSection(
    selectedPlan: GooglePlayBillingService.SubscriptionPlanType,
    productDetails: Map<GooglePlayBillingService.SubscriptionPlanType, String>,
    billingService: GooglePlayBillingService,
    onPlanSelected: (GooglePlayBillingService.SubscriptionPlanType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp) // VStack(spacing: 0) selon le rapport
    ) {
        // Plans selection avec spacing: 8pt selon le rapport
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 3.dp), // Padding selon le rapport
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Plan Hebdomadaire (première position) selon le rapport
            PlanSelectionCardIOS(
                planType = GooglePlayBillingService.SubscriptionPlanType.WEEKLY,
                isSelected = selectedPlan == GooglePlayBillingService.SubscriptionPlanType.WEEKLY,
                productDetails = productDetails,
                billingService = billingService,
                onTap = { onPlanSelected(GooglePlayBillingService.SubscriptionPlanType.WEEKLY) }
            )
            
            // Plan Mensuel (seconde position) selon le rapport
            PlanSelectionCardIOS(
                planType = GooglePlayBillingService.SubscriptionPlanType.MONTHLY,
                isSelected = selectedPlan == GooglePlayBillingService.SubscriptionPlanType.MONTHLY,
                productDetails = productDetails,
                billingService = billingService,
                onTap = { onPlanSelected(GooglePlayBillingService.SubscriptionPlanType.MONTHLY) }
            )
        }
        
        // Spacer fixe (18pt) selon le rapport
        Spacer(modifier = Modifier.height(18.dp))
        
        // Texte informatif avec checkmark selon le rapport iOS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp), // Padding bottom: 12pt selon le rapport
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), // Spacing: 5pt selon le rapport
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(12.dp) // Réduit de 14dp à 12dp
            )
            
            Text(
                text = if (selectedPlan == GooglePlayBillingService.SubscriptionPlanType.MONTHLY) {
                    stringResource(R.string.no_payment_required_now) // Clé du rapport iOS
                } else {
                    stringResource(R.string.no_commitment_cancel_anytime) // Clé du rapport iOS
                },
                fontSize = 12.sp, // Réduit de 14pt à 12pt
                color = Color.Black
            )
        }
    }
}

// Composant PlanSelectionCard selon RAPPORT_PRIX_BOUTONS_PAYWALL.md
@Composable
private fun PlanSelectionCardIOS(
    planType: GooglePlayBillingService.SubscriptionPlanType,
    isSelected: Boolean,
    productDetails: Map<GooglePlayBillingService.SubscriptionPlanType, String>,
    billingService: GooglePlayBillingService,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Prix dynamiques depuis Google Play
    val totalPrice = productDetails[planType] ?: "N/A"
    
    // Calculer le prix par utilisateur (÷ 2) en extrayant le nombre du prix formaté
    // Calcul du prix par utilisateur avec la devise correcte (utilise les micros de Google)
    val pricePerUser = billingService.calculatePricePerUser(planType)
    
    Card(
        onClick = onTap,
        colors = CardDefaults.cardColors(containerColor = Color.White), // Background: blanc selon le rapport
        shape = RoundedCornerShape(12.dp), // Corner radius: 12pt selon le rapport
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp, // Border selon le rapport
            color = if (isSelected) Color.Black else Color.Black.copy(alpha = 0.3f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp), // Padding selon le rapport
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // VStack alignement gauche selon le rapport
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp) // Spacing: 5 selon le rapport
            ) {
                // 1. TITRE DU PLAN (réduit à 15pt)
                Text(
                    text = when (planType) {
                        GooglePlayBillingService.SubscriptionPlanType.WEEKLY -> stringResource(R.string.plan_weekly)
                        GooglePlayBillingService.SubscriptionPlanType.MONTHLY -> stringResource(R.string.plan_monthly_free_trial)
                    },
                    fontSize = 15.sp, // Réduit de 17pt à 15pt
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                // 2. PRIX TOTAL + PÉRIODE + "POUR 2 UTILISATEURS" (avec prix dynamiques)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = "$totalPrice / ${stringResource(
                            when (planType) {
                                GooglePlayBillingService.SubscriptionPlanType.WEEKLY -> R.string.period_week
                                GooglePlayBillingService.SubscriptionPlanType.MONTHLY -> R.string.period_month
                            }
                        )}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = " ${stringResource(R.string.for_2_users)}", // Espace ajouté avant "für"
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.Black
                    )
                }
                
                // 3. PRIX PAR UTILISATEUR (calculé dynamiquement)
                Text(
                    text = "$pricePerUser ${stringResource(R.string.per_user_per)} ${stringResource(
                        when (planType) {
                            GooglePlayBillingService.SubscriptionPlanType.WEEKLY -> R.string.period_week
                            GooglePlayBillingService.SubscriptionPlanType.MONTHLY -> R.string.period_month
                        }
                    )}",
                    fontSize = 10.sp,
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// Bouton principal selon le rapport iOS
@Composable
private fun SubscriptionButtonIOS(
    selectedPlan: GooglePlayBillingService.SubscriptionPlanType,
    isLoading: Boolean,
    isReady: Boolean,
    onPurchase: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onPurchase,
        enabled = isReady && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFD267A), // Rose (#FD267A) selon le rapport
            disabledContainerColor = Color(0xFFFD267A).copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(28.dp), // Corner radius: 28pt selon le rapport
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp) // Height: 56pt selon le rapport
            .padding(horizontal = 30.dp) // Padding horizontal: 30pt selon le rapport
    ) {
        if (isLoading) {
            // État loading avec ProgressView + texte selon le rapport
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp), // Spacing: 8pt selon le rapport
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp) // ScaleEffect: 0.8 selon le rapport
                )
                Text(
                    text = stringResource(R.string.loading_caps), // Clé du rapport iOS
                    fontSize = 16.sp, // Réduit de 18pt à 16pt
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            Text(
                text = if (selectedPlan == GooglePlayBillingService.SubscriptionPlanType.WEEKLY) {
                    stringResource(R.string.continue_button).uppercase() // CONTINUER selon le rapport
                } else {
                    stringResource(R.string.start_trial) // COMMENCER L'ESSAI GRATUIT selon le rapport
                },
                fontSize = 16.sp, // Réduit de 18pt à 16pt
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// Section Légale selon le rapport iOS - FONCTIONNELLE
@Composable
private fun LegalLinksSectionIOS(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val billingService = GooglePlayBillingService.getInstance(context)
    
    // Seul lien restant - centré
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp), // Padding bottom: 5pt selon le rapport
        horizontalArrangement = Arrangement.Center // Centré pour un seul lien
    ) {
        // Politique de confidentialité - lien Love2Love
        TextButton(
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://love2lovesite.onrender.com/privacy-policy.html"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("LegalLinks", "Erreur ouverture Privacy Policy: ${e.message}")
                }
            }
        ) {
            Text(
                text = stringResource(R.string.privacy_policy), // Clé du rapport iOS
                fontSize = 10.sp, // Réduit de 12pt à 10pt
                color = Color.Black.copy(alpha = 0.5f) // Noir 50% opacité selon le rapport
            )
        }
    }
}

// Composant ErrorOverlay (existant dans le code original)
@Composable
private fun ErrorOverlay(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Erreur",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = message,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Fermer")
                    }
                    
                    Button(onClick = onRetry) {
                        Text("Réessayer")
                    }
                }
            }
        }
    }
}

/**
 * Écran de fallback simple en cas d'erreur UI Compose
 */
@Composable
private fun FallbackSubscriptionScreen(
    onComplete: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Abonnement Premium",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Débloquez toutes les fonctionnalités premium de Love2Love",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = {
                // Pas d'abonnement détecté après validation serveur
                onSkip?.invoke()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF990033)
            )
        ) {
            Text(
                text = "Continuer sans Premium",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}