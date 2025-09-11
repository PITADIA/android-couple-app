// package com.yourapp.ui.subscription

/*
 * Kotlin/Compose port of the SwiftUI SubscriptionView.
 * ✅ Uses Android strings.xml (via stringResource) instead of .xcstrings.
 * ✅ Mirrors layout, plan selection order, and analytics hooks.
 * ✅ Hooks into your existing services (AppState, Receipt/Pricing services).
 *
 * Required strings.xml keys (create them if missing):
 *  - choose_plan
 *  - partner_no_payment
 *  - love_stronger_feature
 *  - love_stronger_description
 *  - memory_box_feature
 *  - memory_box_description
 *  - love_map_feature
 *  - love_map_description
 *  - no_payment_required_now
 *  - no_commitment_cancel_anytime
 *  - loading_caps
 *  - start_trial
 *  - terms
 *  - privacy_policy
 *  - restore
 *  - for_2_users
 *  - per_user_per
 */

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.StateFlow

// ---------- Placeholders / contracts ----------
// These mimic your iOS services. Wire them to your Android implementations.
// If you already have equivalents, keep only the Composables and remove these.

class AppState(
    var currentUser: User? = null,
    val freemiumManager: FreemiumManager? = null
) {
    fun updateUser(user: User) { /* TODO: implement */ }
}

data class User(val isSubscribed: Boolean)

interface FreemiumManager {
    fun dismissSubscription()
    fun trackUpgradePromptShown()
    fun trackConversion()
}

enum class SubscriptionPlanType { WEEKLY, MONTHLY }

data class PlanInfo(
    val displayName: String,
    val price: String,
    val period: String,
    val pricePerUser: String
)

interface PlayBillingPricingService {
    val hasDynamicPrices: StateFlow<Boolean>
    fun refreshPrices()
    fun infoFor(plan: SubscriptionPlanType): PlanInfo
}

interface GoogleReceiptService {
    val isLoading: StateFlow<Boolean>
    val isSubscribed: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>
    val selectedPlan: StateFlow<SubscriptionPlanType>

    fun selectPlan(plan: SubscriptionPlanType)
    fun purchaseSubscription()
    fun restorePurchases()
}

// ---------- Color helpers ----------
private fun hex(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

// ---------- Composable ----------
@Composable
fun SubscriptionScreen(
    appState: AppState,
    receiptService: GoogleReceiptService,
    pricingService: PlayBillingPricingService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Collect service state
    val isLoading by receiptService.isLoading.collectAsState(initial = false)
    val isSubscribed by receiptService.isSubscribed.collectAsState(initial = false)
    val selectedPlan by receiptService.selectedPlan.collectAsState(initial = SubscriptionPlanType.WEEKLY)
    val errorMessage by receiptService.errorMessage.collectAsState(initial = null)
    val hasDynamicPrices by pricingService.hasDynamicPrices.collectAsState(initial = false)

    // On appear: refresh prices if needed + analytics view shown
    LaunchedEffect(hasDynamicPrices) {
        if (!hasDynamicPrices) pricingService.refreshPrices()
    }
    // Mirror SwiftUI .onAppear analytics
    LaunchedEffect(Unit) {
        appState.freemiumManager?.trackUpgradePromptShown()
    }

    // Observe subscription success (like Swift .onReceive)
    LaunchedEffect(isSubscribed) {
        if (isSubscribed) {
            appState.currentUser?.let { current ->
                appState.updateUser(current.copy(isSubscribed = true))
            }
            // Firebase Analytics events
            Firebase.analytics.logEvent("abonnement_reussi", null)
            appState.freemiumManager?.trackConversion()
            appState.freemiumManager?.dismissSubscription()
            onDismiss()
        }
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F9)) // same light grey backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header with close button (top-aligned)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    appState.freemiumManager?.dismissSubscription()
                    onDismiss()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Black
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // Title + subtitle
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

            // Feature bullets
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp),
                verticalArrangement = Arrangement.spacedBy(25.dp)
            ) {
                NewFeatureRow(
                    title = stringResource(R.string.love_stronger_feature),
                    subtitle = stringResource(R.string.love_stronger_description)
                )
                NewFeatureRow(
                    title = stringResource(R.string.memory_box_feature),
                    subtitle = stringResource(R.string.memory_box_description)
                )
                NewFeatureRow(
                    title = stringResource(R.string.love_map_feature),
                    subtitle = stringResource(R.string.love_map_description)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Plans + CTA + legal
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Selection cards (order: WEEKLY first, then MONTHLY)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlanSelectionCard(
                        planType = SubscriptionPlanType.WEEKLY,
                        isSelected = selectedPlan == SubscriptionPlanType.WEEKLY,
                        onTap = { receiptService.selectPlan(SubscriptionPlanType.WEEKLY) },
                        pricingService = pricingService
                    )
                    PlanSelectionCard(
                        planType = SubscriptionPlanType.MONTHLY,
                        isSelected = selectedPlan == SubscriptionPlanType.MONTHLY,
                        onTap = { receiptService.selectPlan(SubscriptionPlanType.MONTHLY) },
                        pricingService = pricingService
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Info line above CTA
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    val infoText = if (selectedPlan == SubscriptionPlanType.MONTHLY) {
                        stringResource(R.string.no_payment_required_now)
                    } else {
                        stringResource(R.string.no_commitment_cancel_anytime)
                    }
                    Text(
                        text = infoText,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }

                // Purchase button
                Button(
                    onClick = {
                        val planTypeStr = if (selectedPlan == SubscriptionPlanType.WEEKLY) "weekly" else "monthly"
                        Firebase.analytics.logEvent("abonnement_demarre", Bundle().apply {
                            putString("type", planTypeStr)
                            putString("source", "subscription_screen")
                        })
                        appState.freemiumManager?.trackUpgradePromptShown()
                        receiptService.purchaseSubscription()
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .padding(horizontal = 30.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = hex("#FD267A"))
                ) {
                    if (isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.loading_caps),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.start_trial),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Legal + Restore
                Row(
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.terms),
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            // Using Apple EULA like iOS snippet; replace with your Terms URL if different on Android
                            openUrl(context, "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")
                        }
                    )
                    Text(
                        text = stringResource(R.string.privacy_policy),
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            val isFr = context.resources.configuration.locales[0]?.language?.startsWith("fr") == true
                            val privacyUrl = if (isFr) {
                                "https://love2lovesite.onrender.com"
                            } else {
                                "https://love2lovesite.onrender.com/privacy-policy.html"
                            }
                            openUrl(context, privacyUrl)
                        }
                    )
                    Text(
                        text = stringResource(R.string.restore),
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            receiptService.restorePurchases()
                        }
                    )
                }
            }
        }

        // Error toast (bottom)
        if (!errorMessage.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(bottom = 100.dp)
                        .padding(horizontal = 30.dp)
                        .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

// ---------- Rows / Cards ----------
@Composable
fun NewFeatureRow(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 15.sp,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
fun PremiumFeatureRow(title: String, subtitle: String) {
    NewFeatureRow(title = title, subtitle = subtitle)
}

@Composable
fun PlanSelectionCard(
    planType: SubscriptionPlanType,
    isSelected: Boolean,
    onTap: () -> Unit,
    pricingService: PlayBillingPricingService
) {
    val info = pricingService.infoFor(planType)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.Black else Color.Black.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = info.displayName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${info.price} / ${info.period}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.for_2_users),
                        fontSize = 13.sp,
                        color = Color.Black
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${info.pricePerUser} ${stringResource(R.string.per_user_per)} ${info.period}",
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }

            // Selection circle
            Box(
                modifier = Modifier
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(2.dp, Color.Black, CircleShape)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black, CircleShape)
                    )
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
