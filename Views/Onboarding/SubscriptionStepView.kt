@file:Suppress("UnusedImport")

/*
 * SubscriptionStepScreen.kt
 * Jetpack Compose implementation of your Swift SubscriptionStepView
 * 👉 Android-only: uses Google Play Billing (NOT Apple Pay) and shows how to wire Google Sign‑In elsewhere.
 *
 * ⚠️ Important notes:
 * 1) On Android, subscriptions must go through Google Play Billing, not Google Pay.
 * 2) In Compose, prefer stringResource(R.string.key). Outside Compose, use context.getString(R.string.key).
 * 3) Replace the PRODUCT_IDs with your real Play Console product IDs (base plan/offer must exist).
 * 4) Add the string keys in your res/values/strings.xml.
 * 5) Dependencies (build.gradle):
 *    implementation("androidx.compose.material3:material3:<latest>")
 *    implementation("com.android.billingclient:billing-ktx:6.1.0")
 *    implementation("com.google.android.gms:play-services-auth:21.2.0") // Google Sign-In
 */

package com.love2love.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

// -------------------------------
// Replace with your real product IDs from Play Console
// -------------------------------
private const val WEEKLY_PRODUCT_ID = "love2love_premium_weekly"
private const val MONTHLY_PRODUCT_ID = "love2love_premium_monthly"

private val Pink = Color(0xFFFD267A)

// -------------------------------
// Public screen API
// -------------------------------
@Composable
fun SubscriptionStepScreen(
    viewModel: OnboardingViewModel, // your existing VM with skipSubscription() & completeSubscription()
    billingManager: BillingManager = rememberBillingManager()
) {
    val context = LocalContext.current

    // Local processing flag (mirrors your Swift isProcessingPurchase)
    var isProcessingPurchase by remember { mutableStateOf(false) }

    // Observe billing state
    val uiState by billingManager.uiState.collectAsState()

    // Connect & fetch products
    LaunchedEffect(Unit) {
        billingManager.connectIfNeeded(context)
        billingManager.queryProducts(listOf(WEEKLY_PRODUCT_ID, MONTHLY_PRODUCT_ID))
        billingManager.queryActivePurchases()
    }

    // Forward subscription success to complete onboarding
    LaunchedEffect(uiState.isSubscribed) {
        if (uiState.isSubscribed) {
            isProcessingPurchase = false
            viewModel.completeSubscription()
        }
    }

    // Stop spinner on error
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            isProcessingPurchase = false
        }
    }

    // ----- UI -----
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(red = 0.97f, green = 0.97f, blue = 0.98f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with close (top-left)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipSubscription() }) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = "close",
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

            // New features
            Column(modifier = Modifier.padding(horizontal = 25.dp), verticalArrangement = Arrangement.spacedBy(25.dp)) {
                NewFeatureRow(
                    title = stringResource(id = R.string.feature_love_stronger),
                    subtitle = stringResource(id = R.string.feature_love_stronger_description)
                )
                NewFeatureRow(
                    title = stringResource(id = R.string.feature_memory_chest),
                    subtitle = stringResource(id = R.string.feature_memory_chest_description)
                )
                NewFeatureRow(
                    title = stringResource(id = R.string.feature_love_map),
                    subtitle = stringResource(id = R.string.feature_love_map_description)
                )
            }

            Spacer(Modifier.weight(1f))

            // Plans + CTA area
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Plan selection (weekly first, then monthly) like your Swift order
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlanSelectionCard(
                        planType = PlanType.WEEKLY,
                        isSelected = uiState.selectedPlan == PlanType.WEEKLY,
                        price = uiState.formattedPrice[PlanType.WEEKLY],
                        onTap = { billingManager.updateSelection(PlanType.WEEKLY) }
                    )
                    PlanSelectionCard(
                        planType = PlanType.MONTHLY,
                        isSelected = uiState.selectedPlan == PlanType.MONTHLY,
                        price = uiState.formattedPrice[PlanType.MONTHLY],
                        onTap = { billingManager.updateSelection(PlanType.MONTHLY) }
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Info text above CTA
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        // Simple white checkmark dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val infoText = if (uiState.selectedPlan == PlanType.MONTHLY)
                        stringResource(id = R.string.no_payment_required_now)
                    else stringResource(id = R.string.no_commitment_cancel_anytime)
                    Text(
                        text = infoText,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
                Spacer(Modifier.height(12.dp))

                // CTA button
                val activity = context.findActivity()
                Button(
                    onClick = {
                        if (activity != null) {
                            isProcessingPurchase = true
                            billingManager.launchPurchase(activity, uiState.selectedPlan)
                        }
                    },
                    enabled = !isProcessingPurchase,
                    modifier = Modifier
                        .padding(horizontal = 30.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink)
                ) {
                    if (isProcessingPurchase) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.loading_caps), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    } else {
                        val label = if (uiState.selectedPlan == PlanType.WEEKLY)
                            stringResource(id = R.string.continue_caps)
                        else stringResource(id = R.string.start_trial)
                        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Legal links
                Row(
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.terms),
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            // Google Play Terms of Service (you can replace with your own EULA URL)
                            context.openUrl("https://play.google.com/about/play-terms/")
                        }
                    )
                    Text(
                        text = stringResource(id = R.string.privacy_policy),
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            // Choose FR/EN policy as in Swift if needed
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
                        modifier = Modifier.clickable { billingManager.queryActivePurchases() }
                    )
                }
                Spacer(Modifier.height(5.dp))
            }
        }

        // Error banner (bottom)
        uiState.errorMessage?.let { msg ->
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = msg,
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

// -------------------------------
// Simple New Feature row
// -------------------------------
@Composable
private fun NewFeatureRow(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        Spacer(Modifier.height(4.dp))
        Text(text = subtitle, fontSize = 14.sp, color = Color.Black.copy(alpha = 0.7f))
    }
}

// -------------------------------
// Plan card
// -------------------------------
@Composable
private fun PlanSelectionCard(
    planType: PlanType,
    isSelected: Boolean,
    price: String?,
    onTap: () -> Unit
) {
    val borderColor = if (isSelected) Pink else Color.Black.copy(alpha = 0.1f)
    val labelRes = when (planType) {
        PlanType.WEEKLY -> R.string.plan_weekly
        PlanType.MONTHLY -> R.string.plan_monthly
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onTap() },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = Color.White,
        border = BorderStroke(width = 2.dp, color = borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = labelRes),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = price ?: "…",
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Pink),
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

// -------------------------------
// Billing manager (minimal, UI-friendly)
// -------------------------------
class BillingManager(private val appScope: CoroutineScope) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState

    private val productMap = mutableMapOf<PlanType, ProductDetails>()

    fun connectIfNeeded(context: Context) {
        if (billingClient?.isReady == true) return
        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener(this)
            .build()
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // ready
                } else {
                    postError("Billing setup failed: ${'$'}{billingResult.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                // will retry automatically on next call
            }
        })
    }

    fun updateSelection(plan: PlanType) {
        _uiState.value = _uiState.value.copy(selectedPlan = plan)
    }

    fun queryProducts(productIds: List<String>) {
        val client = billingClient ?: return
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(productIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            })
            .build()
        client.queryProductDetailsAsync(query) { billingResult, detailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                postError("Products query failed: ${'$'}{billingResult.debugMessage}")
                return@queryProductDetailsAsync
            }
            // map to PlanType
            detailsList.forEach { pd ->
                when (pd.productId) {
                    WEEKLY_PRODUCT_ID -> productMap[PlanType.WEEKLY] = pd
                    MONTHLY_PRODUCT_ID -> productMap[PlanType.MONTHLY] = pd
                }
            }
            // compute formatted prices
            val priceMap = mutableMapOf<PlanType, String>()
            productMap.forEach { (plan, pd) ->
                priceMap[plan] = pd.firstPricingPhase()?.let { phase ->
                    formatPrice(phase.priceAmountMicros, phase.priceCurrencyCode, plan)
                } ?: "—"
            }
            _uiState.value = _uiState.value.copy(formattedPrice = priceMap)
        }
    }

    fun launchPurchase(activity: Activity, plan: PlanType) {
        val client = billingClient ?: run { postError("Billing client not connected"); return }
        val pd = productMap[plan] ?: run { postError("Product not loaded for ${'$'}plan"); return }
        val offerToken = pd.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: run { postError("No subscription offer configured for ${'$'}{pd.productId}"); return }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    fun queryActivePurchases() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { br, purchases ->
            if (br.responseCode != BillingClient.BillingResponseCode.OK) {
                postError("Restore failed: ${'$'}{br.debugMessage}")
                return@queryPurchasesAsync
            }
            handlePurchases(purchases)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // user canceled — not an error banner
        } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            postError("Purchase failed: ${'$'}{result.debugMessage}")
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        // You should verify purchase on your backend and acknowledge it.
        val anyActive = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                || purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        _uiState.value = _uiState.value.copy(isSubscribed = anyActive, errorMessage = null)

        // Acknowledge if needed (demo only — move to repository/back-end in production)
        val client = billingClient ?: return
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { p ->
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(p.purchaseToken)
                    .build()
                client.acknowledgePurchase(ackParams) { br ->
                    if (br.responseCode != BillingClient.BillingResponseCode.OK) {
                        postError("Acknowledge failed: ${'$'}{br.debugMessage}")
                    }
                }
            }
    }

    private fun postError(msg: String) {
        _uiState.value = _uiState.value.copy(errorMessage = msg)
    }
}

@Stable
data class SubscriptionUiState(
    val selectedPlan: PlanType = PlanType.WEEKLY,
    val formattedPrice: Map<PlanType, String> = emptyMap(),
    val isSubscribed: Boolean = false,
    val errorMessage: String? = null
)

enum class PlanType { WEEKLY, MONTHLY }

private fun ProductDetails.firstPricingPhase(): ProductDetails.PricingPhase? {
    return subscriptionOfferDetails?.firstOrNull()
        ?.pricingPhases?.pricingPhaseList?.firstOrNull()
}

private fun formatPrice(micros: Long, currencyCode: String, plan: PlanType): String {
    val nf = NumberFormat.getCurrencyInstance()
    nf.currency = Currency.getInstance(currencyCode)
    val amount = micros / 1_000_000.0
    val period = when (plan) {
        PlanType.WEEKLY -> "/" + "week"
        PlanType.MONTHLY -> "/" + "month"
    }
    return nf.format(amount) + period
}

// -------------------------------
// Helpers & remember
// -------------------------------
@Composable
fun rememberBillingManager(): BillingManager {
    val scope = remember { CoroutineScope(Dispatchers.Main) }
    return remember { BillingManager(scope) }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = (ctx as ContextWrapper).baseContext
    }
    return null
}

private fun Context.openUrl(url: String) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (_: Throwable) { }
}

// -------------------------------
// OnboardingViewModel contract used by this screen
// -------------------------------
interface OnboardingViewModel {
    fun skipSubscription()
    fun completeSubscription()
}

/*
 * 🔐 Google Sign‑In (not used directly on this screen)
 * Elsewhere in your app, integrate Google Sign‑In (play‑services‑auth) instead of Apple Sign In.
 * Example setup (outside Compose screen):
 * val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
 *     .requestEmail()
 *     .build()
 * val client = GoogleSignIn.getClient(context, gso)
 * startActivityForResult(client.signInIntent, ...)
 *
 * 🧾 Notes on "Restore": on Android, restore is automatic after install if you call queryPurchasesAsync.
 * The "Restore" text here just triggers queryActivePurchases().
 */
