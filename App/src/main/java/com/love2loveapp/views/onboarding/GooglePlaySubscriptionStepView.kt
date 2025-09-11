package com.love2loveapp.views.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.love2loveapp.R
import com.love2loveapp.services.GooglePlayBillingService
import kotlinx.coroutines.launch
import java.util.*

/**
 * Écran d'abonnement Google Play (remplace SubscriptionStepView.swift)
 * Utilise Google Play Billing au lieu de StoreKit pour Android
 * 
 * Fonctionnalités :
 * - Affichage des plans d'abonnement (hebdomadaire/mensuel)
 * - Gestion des achats via Google Play Billing
 * - Interface similaire à l'iOS mais adaptée pour Android
 * - Localisation via strings.xml
 */
@Composable
fun GooglePlaySubscriptionStepView(
    onSubscriptionComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    
    val billingService = GooglePlayBillingService.instance
    
    // Observer les états du service de facturation
    val connectionState by billingService.connectionState.collectAsStateWithLifecycle()
    val availableProducts by billingService.availableProducts.collectAsStateWithLifecycle()
    val isSubscribed by billingService.isSubscribed.collectAsStateWithLifecycle()
    val purchaseState by billingService.purchaseState.collectAsStateWithLifecycle()
    val errorMessage by billingService.errorMessage.collectAsStateWithLifecycle()
    
    // État local pour le plan sélectionné
    var selectedPlan by remember { mutableStateOf(GooglePlayBillingService.WEEKLY_SUBSCRIPTION_ID) }
    
    // Initialiser le service de facturation
    LaunchedEffect(Unit) {
        billingService.initialize(context)
    }
    
    // Gérer la finalisation de l'abonnement
    LaunchedEffect(isSubscribed) {
        if (isSubscribed) {
            Log.d("GooglePlaySubscription", "✅ Abonnement confirmé")
            onSubscriptionComplete()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header avec bouton fermer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSkip) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Color.Black
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // Titre et sous-titre
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.choose_plan),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.partner_no_payment),
                    fontSize = 16.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))

            // Nouvelles fonctionnalités
            Column(
                modifier = Modifier.padding(horizontal = 25.dp),
                verticalArrangement = Arrangement.spacedBy(25.dp)
            ) {
                FeatureRow(
                    title = stringResource(id = R.string.feature_love_stronger),
                    subtitle = stringResource(id = R.string.feature_love_stronger_description)
                )
                FeatureRow(
                    title = stringResource(id = R.string.feature_memory_chest),
                    subtitle = stringResource(id = R.string.feature_memory_chest_description)
                )
                FeatureRow(
                    title = stringResource(id = R.string.feature_love_map),
                    subtitle = stringResource(id = R.string.feature_love_map_description)
                )
            }

            Spacer(Modifier.weight(1f))

            // Section plans et CTA
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sélection de plan
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Plan hebdomadaire
                    PlanCard(
                        planId = GooglePlayBillingService.WEEKLY_SUBSCRIPTION_ID,
                        title = stringResource(id = R.string.plan_weekly),
                        price = billingService.getFormattedPrice(GooglePlayBillingService.WEEKLY_SUBSCRIPTION_ID) ?: "...",
                        isSelected = selectedPlan == GooglePlayBillingService.WEEKLY_SUBSCRIPTION_ID,
                        onSelect = { selectedPlan = GooglePlayBillingService.WEEKLY_SUBSCRIPTION_ID }
                    )
                    
                    // Plan mensuel
                    PlanCard(
                        planId = GooglePlayBillingService.MONTHLY_SUBSCRIPTION_ID,
                        title = "Plan Mensuel",
                        price = billingService.getFormattedPrice(GooglePlayBillingService.MONTHLY_SUBSCRIPTION_ID) ?: "...",
                        isSelected = selectedPlan == GooglePlayBillingService.MONTHLY_SUBSCRIPTION_ID,
                        onSelect = { selectedPlan = GooglePlayBillingService.MONTHLY_SUBSCRIPTION_ID }
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Info au-dessus du CTA
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val infoText = if (selectedPlan == GooglePlayBillingService.MONTHLY_SUBSCRIPTION_ID)
                        stringResource(id = R.string.no_payment_required_now)
                    else stringResource(id = R.string.no_commitment_cancel_anytime)
                    Text(
                        text = infoText,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Bouton CTA
                val isProcessing = purchaseState == GooglePlayBillingService.PurchaseState.PURCHASING
                Button(
                    onClick = {
                        if (activity != null) {
                            scope.launch {
                                Log.d("GooglePlaySubscription", "🛒 Lancement achat: $selectedPlan")
                                billingService.clearError()
                                billingService.purchaseSubscription(activity, selectedPlan)
                            }
                        }
                    },
                    enabled = !isProcessing && connectionState == GooglePlayBillingService.BillingConnectionState.CONNECTED,
                    modifier = Modifier
                        .padding(horizontal = 30.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFD267A))
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.loading_caps),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    } else {
                        val label = if (selectedPlan == GooglePlayBillingService.WEEKLY_SUBSCRIPTION_ID)
                            "CONTINUER"
                        else "COMMENCER L'ESSAI"
                        Text(
                            text = label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Liens légaux
                Row(
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.terms),
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            context.openUrl("https://play.google.com/about/play-terms/")
                        }
                    )
                    Text(
                        text = stringResource(id = R.string.privacy_policy),
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            val locale = Locale.getDefault().language
                            val url = if (locale.startsWith("fr"))
                                "https://love2lovesite.onrender.com"
                            else
                                "https://love2lovesite.onrender.com/privacy-policy.html"
                            context.openUrl(url)
                        }
                    )
                    Text(
                        text = stringResource(id = R.string.restore),
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { 
                            billingService.queryPurchases()
                        }
                    )
                }
                Spacer(Modifier.height(5.dp))
            }
        }

        // Bannière d'erreur (en bas)
        errorMessage?.let { error ->
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = error,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(horizontal = 30.dp)
                        .padding(bottom = 100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Red.copy(alpha = 0.8f))
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = Color.Black.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PlanCard(
    planId: String,
    title: String,
    price: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFFFD267A) else Color.Black.copy(alpha = 0.1f)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(width = 2.dp, color = borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = price,
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFD267A)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

// Extension helpers
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: Exception) {
        Log.e("GooglePlaySubscription", "Erreur ouverture URL: $url", e)
    }
}
