@file:Suppress("MemberVisibilityCanBePrivate", "UnusedPrivateMember")

package com.love2loveapp.core.services.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Service de tarification pour Android/Play Store.
 * - Récupère dynamiquement les prix via Google Play Billing (ProductDetails).
 * - Fournit un fallback propre basé sur strings.xml (ex.: plan_weekly_price, plan_monthly_price, etc.),
 *   avec formatage local (symbole de devise) à la volée.
 *
 * ATTENTES strings.xml :
 *   <string name="plan_weekly_price">4.99</string>
 *   <string name="plan_monthly_price">9.99</string>
 *   <string name="plan_weekly_price_per_user">2.49</string>
 *   <string name="plan_monthly_price_per_user">4.99</string>
 * (Ces valeurs sont des exemples — tu gardes tes chiffres.)
 *
 * NOTE devise : si la devise du système != devise Play, on formate avec la locale système (aligné
 * à la logique Swift fournie où la "correction" force la locale système quand mismatch).
 */
class PlayStorePricingService private constructor(private val appContext: Context) :
    PurchasesUpdatedListener {

    // ----- Erreurs équivalentes à StoreKitPricingError -----
    sealed class PricingError(message: String) : Exception(message) {
        object NoProductsAvailable : PricingError("Aucun produit Play Store")
        object ProductProcessingFailed : PricingError("Erreur traitement produits")
        object PartiallyLoaded : PricingError("Chargement partiel des prix")
        object BillingUnavailable : PricingError("BillingClient indisponible")
    }

    // ----- Modèle équivalent à LocalizedPrice -----
    data class LocalizedPrice(
        val productId: String,
        val amount: BigDecimal,
        val localeUsed: Locale,
        val currencyCode: String,
        val formattedPrice: String,
        val formattedPricePerUser: String
    )

    companion object {
        private const val TAG = "PlayStorePricingService"

        // Ids produits (équivalents aux ids iOS)
        private val PRODUCT_IDS = setOf(
            "com.lyes.love2love.subscription.weekly",
            "com.lyes.love2love.subscription.monthly"
        )

        @Volatile
        private var INSTANCE: PlayStorePricingService? = null

        fun getInstance(context: Context): PlayStorePricingService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayStorePricingService(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ----- State exposé (équivalent @Published) -----
    private val _localizedPrices = MutableStateFlow<Map<String, LocalizedPrice>>(emptyMap())
    val localizedPrices: StateFlow<Map<String, LocalizedPrice>> = _localizedPrices

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _lastError = MutableStateFlow<Throwable?>(null)
    val lastError: StateFlow<Throwable?> = _lastError

    // Logs anti-spam
    private val loggedDynamicPrices = mutableSetOf<String>()
    private val loggedFallbackPrices = mutableSetOf<String>()

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Billing
    private val billingClient: BillingClient = BillingClient
        .newBuilder(appContext)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    // ----- API publique -----

    /** Récupère le prix localisé pour un productId. */
    fun getLocalizedPrice(productId: String): String {
        _localizedPrices.value[productId]?.let { lp ->
            if (loggedDynamicPrices.add(productId)) {
                Log.d(TAG, "💰 Prix dynamique activé pour $productId : ${lp.formattedPrice}")
            }
            return lp.formattedPrice
        }
        if (loggedFallbackPrices.add(productId)) {
            Log.w(TAG, "⚠️ Fallback utilisé pour $productId")
            _lastError.value?.let { Log.w(TAG, "⚠️ Raison: ${it.localizedMessage}") }
        }
        return getFallbackPrice(productId)
    }

    /** Récupère le prix par utilisateur (moitié du prix). */
    fun getPricePerUser(productId: String): String {
        _localizedPrices.value[productId]?.let { lp ->
            return lp.formattedPricePerUser // déjà en demi
        }
        return getFallbackPricePerUser(productId)
    }

    /** Forcer un rechargement des ProductDetails depuis Play Billing. */
    fun refreshPrices() {
        scope.launch {
            _isLoading.value = true
            val ok = ensureConnected()
            if (!ok) {
                _isLoading.value = false
                _lastError.value = PricingError.BillingUnavailable
                Log.e(TAG, "❌ Billing non prêt")
                return@launch
            }
            try {
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        PRODUCT_IDS.map {
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(it)
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build()
                        }
                    )
                    .build()

                billingClient.queryProductDetailsAsync(params) { result, list ->
                    if (result.responseCode != BillingResponseCode.OK) {
                        Log.e(TAG, "❌ queryProductDetailsAsync: ${result.debugMessage}")
                        _lastError.value = PricingError.NoProductsAvailable
                        _isLoading.value = false
                        return@queryProductDetailsAsync
                    }
                    updateLocalizedPrices(list)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "❌ refreshPrices failed", t)
                _lastError.value = PricingError.ProductProcessingFailed
                _isLoading.value = false
            }
        }
    }

    /** Indique si des prix dynamiques sont disponibles. */
    val hasDynamicPrices: Boolean
        get() = _localizedPrices.value.isNotEmpty()

    /** Diagnostic lisible (équivalent Swift). */
    fun getPricingDiagnostic(): String {
        val sb = StringBuilder("=== PlayStorePricingService Diagnostic ===\n")
        sb.append("Prix dynamiques disponibles: ").append(hasDynamicPrices).append('\n')
        sb.append("Nombre de produits: ").append(_localizedPrices.value.size).append('\n')
        sb.append("Loading: ").append(_isLoading.value).append('\n')
        _lastError.value?.let { sb.append("Dernière erreur: ").append(it.localizedMessage).append('\n') }
        sb.append("\nProduits chargés:\n")
        _localizedPrices.value.forEach { (id, p) ->
            sb.append("  - ").append(id).append(": ")
                .append(p.formattedPrice).append(" (").append(p.currencyCode).append(")\n")
                .append("    Prix/utilisateur: ").append(p.formattedPricePerUser).append('\n')
        }
        if (_localizedPrices.value.isEmpty()) {
            sb.append("  Aucun produit chargé - utilisation des prix fallback\n")
        }
        sb.append("=======================================\n")
        return sb.toString()
    }

    /** À appeler pour libérer si nécessaire (ex.: ViewModel.onCleared). */
    fun close() {
        try {
            billingClient.endConnection()
        } catch (_: Throwable) { /* no-op */ }
        scope.cancel()
    }

    // ----- Impl PurchasesUpdatedListener (non utilisé ici) -----
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        // Ce service ne gère que les prix (pas les achats).
    }

    // ----- Interne : connexion Billing -----
    private suspend fun ensureConnected(): Boolean = suspendCancellableCoroutine { cont ->
        if (billingClient.isReady) {
            cont.resume(true); return@suspendCancellableCoroutine
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                cont.resume(billingResult.responseCode == BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                // On laissera l'appelant réessayer ultérieurement
                Log.w(TAG, "⚠️ BillingService déconnecté")
            }
        })
    }

    // ----- Interne : mise à jour des prix dynamiques -----
    private fun updateLocalizedPrices(productDetailsList: List<ProductDetails>) {
        if (productDetailsList.isEmpty()) {
            Log.w(TAG, "⚠️ Aucun ProductDetails — fallback strings.xml")
            _lastError.value = PricingError.NoProductsAvailable
            _isLoading.value = false
            return
        }

        val updated = mutableMapOf<String, LocalizedPrice>()
        var hadErrors = false

        productDetailsList.forEach { pd ->
            try {
                val phase = chooseRecurringPhase(pd)
                    ?: throw IllegalStateException("Aucune PricingPhase récurrente pour ${pd.productId}")

                val storeCurrency = phase.priceCurrencyCode // ex: "EUR"
                val systemCurrency = safeSystemCurrencyCode()
                val useSystemLocale = !storeCurrency.equals(systemCurrency, ignoreCase = true)

                val amount = microsToBigDecimal(phase.priceAmountMicros)
                val localeToUse = if (useSystemLocale) Locale.getDefault() else Locale.getDefault()
                val currencyToUse = if (useSystemLocale)
                    Currency.getInstance(systemCurrency)
                else
                    Currency.getInstance(storeCurrency)

                val fmt = NumberFormat.getCurrencyInstance(localeToUse).apply {
                    currency = currencyToUse
                }
                val formatted = fmt.format(amount.setScale(2, RoundingMode.HALF_UP))
                val half = amount.divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
                val formattedHalf = fmt.format(half)

                if (useSystemLocale) {
                    Log.d(TAG, "🔧 Correction devise: Store=$storeCurrency / System=$systemCurrency → affichage avec la locale système")
                }

                updated[pd.productId] = LocalizedPrice(
                    productId = pd.productId,
                    amount = amount,
                    localeUsed = localeToUse,
                    currencyCode = currencyToUse.currencyCode,
                    formattedPrice = formatted,
                    formattedPricePerUser = formattedHalf
                )
            } catch (t: Throwable) {
                hadErrors = true
                Log.e(TAG, "❌ Erreur traitement ${pd.productId}", t)
            }
        }

        _localizedPrices.value = updated
        _isLoading.value = false
        loggedDynamicPrices.clear()
        loggedFallbackPrices.clear()

        _lastError.value = when {
            updated.isEmpty() && hadErrors -> PricingError.ProductProcessingFailed
            updated.isNotEmpty() && hadErrors -> PricingError.PartiallyLoaded
            else -> null
        }
    }

    /**
     * Sélectionne la phase de prix "récurrente" (INFINITE_RECURRING) si présente,
     * sinon la dernière phase (souvent le prix courant).
     */
    private fun chooseRecurringPhase(pd: ProductDetails): ProductDetails.PricingPhase? {
        val allPhases = pd.subscriptionOfferDetails
            ?.flatMap { it.pricingPhases.pricingPhaseList }
            .orEmpty()

        return allPhases.firstOrNull {
            it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
        } ?: allPhases.lastOrNull()
    }

    // ----- Fallback strings.xml (remplace .localized(...) par context.getString(R.string.xxx)) -----

    private fun getFallbackPrice(productId: String): String = when (productId) {
        "com.lyes.love2love.subscription.weekly" -> {
            val base = appContext.getString(
                appContext.resources.getIdentifier("plan_weekly_price", "string", appContext.packageName)
            )
            formatWithCurrencySymbol(base)
        }
        "com.lyes.love2love.subscription.monthly" -> {
            val base = appContext.getString(
                appContext.resources.getIdentifier("plan_monthly_price", "string", appContext.packageName)
            )
            formatWithCurrencySymbol(base)
        }
        else -> "Prix non disponible"
    }

    private fun getFallbackPricePerUser(productId: String): String = when (productId) {
        "com.lyes.love2love.subscription.weekly" -> {
            val id = appContext.resources.getIdentifier("plan_weekly_price_per_user", "string", appContext.packageName)
            val base = if (id != 0) appContext.getString(id)
            else {
                // si pas de clé *_per_user, diviser par 2 le prix principal
                val full = getFallbackPrice("com.lyes.love2love.subscription.weekly")
                return halfFromFormatted(full)
            }
            formatWithCurrencySymbol(base)
        }
        "com.lyes.love2love.subscription.monthly" -> {
            val id = appContext.resources.getIdentifier("plan_monthly_price_per_user", "string", appContext.packageName)
            val base = if (id != 0) appContext.getString(id)
            else {
                val full = getFallbackPrice("com.lyes.love2love.subscription.monthly")
                return halfFromFormatted(full)
            }
            formatWithCurrencySymbol(base)
        }
        else -> "Prix non disponible"
    }

    /**
     * Si la chaîne contient déjà un symbole/lettres (€, $, £…), on la renvoie telle quelle.
     * Sinon on l'interprète comme un nombre (ex: "4.99" ou "4,99") et on ajoute le symbole selon la locale.
     */
    private fun formatWithCurrencySymbol(raw: String): String {
        val s = raw.trim()
        val hasCurrencyLike = s.any { it == '€' || it == '$' || it == '£' } || s.any { it.isLetter() }
        if (hasCurrencyLike) return s

        val normalized = s.replace(",", ".")
        val number = normalized.toBigDecimalOrNull() ?: return s
        val fmt = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            currency = Currency.getInstance(safeSystemCurrencyCode())
        }
        return fmt.format(number)
    }

    /**
     * Tente de diviser par 2 un prix déjà formaté. Si parsing impossible, renvoie tel quel.
     */
    private fun halfFromFormatted(formatted: String): String {
        // heuristique simple: extraire les chiffres/virgules/points → BigDecimal → /2 → reformatter
        val digits = formatted.filter { it.isDigit() || it == '.' || it == ',' }
        val normalized = digits.replace(",", ".")
        val number = normalized.toBigDecimalOrNull() ?: return formatted
        val half = number.divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
        val fmt = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            currency = Currency.getInstance(safeSystemCurrencyCode())
        }
        return fmt.format(half)
    }

    private fun microsToBigDecimal(micros: Long): BigDecimal =
        BigDecimal(micros).divide(BigDecimal(1_000_000), 6, RoundingMode.HALF_UP)

    private fun safeSystemCurrencyCode(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrElse { "EUR" }
}

/**
 * Enum utilitaire optionnel (si tu as déjà le tien, garde-le et adapte les extensions ci-dessous).
 */
enum class SubscriptionPlanType(val rawValue: String) {
    WEEKLY("com.lyes.love2love.subscription.weekly"),
    MONTHLY("com.lyes.love2love.subscription.monthly")
}

/** Extensions "compatibilité Swift" */
fun PlayStorePricingService.getLocalizedPrice(planType: SubscriptionPlanType): String =
    getLocalizedPrice(planType.rawValue)

fun PlayStorePricingService.getPricePerUser(planType: SubscriptionPlanType): String =
    getPricePerUser(planType.rawValue)

fun PlayStorePricingService.getPriceDetails(planType: SubscriptionPlanType): PlayStorePricingService.LocalizedPrice? =
    localizedPrices.value[planType.rawValue]
