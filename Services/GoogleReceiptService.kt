package com.love2love.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Équivalent Kotlin/Android de AppleReceiptService (StoreKit) :
 * - Utilise Google Play Billing (abonnements)
 * - Valide côté serveur via Firebase Functions ("validateGooglePurchase")
 * - Vérifie le statut via "checkSubscriptionStatus"
 * - Localisation: strings.xml (context.getString(R.string.xxx))
 *
 * ⚠️ Hypothèse: Tu as (ou auras) une Cloud Function "validateGooglePurchase"
 * qui prend { purchaseToken, productId } et renvoie { success: Boolean }.
 */
class BillingService private constructor(
    private val appContext: Context
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val TAG = "BillingService"

        @Volatile private var INSTANCE: BillingService? = null
        fun getInstance(context: Context): BillingService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // --- Observables (équivalent @Published) ---
    val isSubscribed = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val selectedPlan = MutableStateFlow(SubscriptionPlanType.MONTHLY)
    val onSubscriptionValidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // --- Firebase ---
    private val functions = FirebaseFunctions.getInstance()
    private val analytics = Firebase.analytics

    // --- Billing ---
    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    private var productDetailsCache: Map<String, ProductDetails> = emptyMap()

    init {
        SK_log("Init & startConnection()")
        billingClient.startConnection(this)
        checkSubscriptionStatus() // équivalent au init iOS
    }

    // region Public API

    /**
     * Lance l’achat pour le plan sélectionné.
     * Nécessite une Activity (exigence Google Play).
     */
    fun purchaseSubscription(activity: Activity) {
        val planId = selectedPlan.value.productId
        SK_log("Début achat - plan=$planId")
        if (!billingClient.isReady) {
            SK_warn("BillingClient non prêt, tentative de reconnexion…")
            billingClient.startConnection(this)
        }

        isLoading.value = true

        // S’assure d’avoir les ProductDetails en cache
        if (productDetailsCache[planId] == null) {
            queryProducts {
                launchBillingFlowOrFail(activity)
            }
        } else {
            launchBillingFlowOrFail(activity)
        }
    }

    /**
     * Restaure les achats (équivalent iOS). Sur Android, on interroge les
     * abonnements actifs de l’utilisateur et on vérifie côté serveur.
     */
    fun restorePurchases(silent: Boolean = false) {
        SK_log("Restauration des achats (queryPurchasesAsync)")
        if (!silent) isLoading.value = true

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.isNullOrEmpty()) {
                    if (!silent) {
                        errorMessage.value = appContext.getString(R.string.no_purchase_to_restore)
                        isLoading.value = false
                    }
                    return@queryPurchasesAsync
                }

                // Valider toutes les purchases actives côté serveur
                var atLeastOneValidated = false
                purchases.forEach { p ->
                    validatePurchaseWithFirebase(
                        purchaseToken = p.purchaseToken,
                        productId = p.products.firstOrNull().orEmpty(),
                        onSuccess = {
                            atLeastOneValidated = true
                            isSubscribed.value = true
                            analytics.logEvent("achat_restaure", null)
                            SK_log("✅ Abonnement restauré/validé serveur")
                            onSubscriptionValidated.tryEmit(Unit)
                        },
                        onError = { msg ->
                            SK_err("Erreur validation (restore): $msg")
                        },
                        finally = {
                            isLoading.value = false
                        }
                    )
                }

                if (!atLeastOneValidated && !silent) {
                    isLoading.value = false
                    errorMessage.value = appContext.getString(R.string.no_purchase_to_restore)
                }
            } else {
                if (!silent) {
                    isLoading.value = false
                    errorMessage.value = appContext.getString(R.string.product_loading_error)
                }
                SK_err("queryPurchasesAsync failed: ${result.debugMessage}")
            }
        }
    }

    /**
     * Vérifie l’état d’abonnement côté serveur (Cloud Function).
     */
    fun checkSubscriptionStatus() {
        SK_log("Vérification statut d’abonnement")
        // ⚠️ la fonction doit être callable avec l’utilisateur Firebase Auth courant
        functions
            .getHttpsCallable("checkSubscriptionStatus")
            .call()
            .addOnSuccessListener { r ->
                val data = r.data as? Map<*, *>
                val subscribed = (data?.get("isSubscribed") as? Boolean) == true
                SK_log("Statut d’abonnement: $subscribed")
                isSubscribed.value = subscribed
            }
            .addOnFailureListener { e ->
                SK_err("Erreur checkSubscriptionStatus: ${e.message}")
            }
    }

    // endregion

    // region Billing flow helpers

    private fun launchBillingFlowOrFail(activity: Activity) {
        val productId = selectedPlan.value.productId
        val details = productDetailsCache[productId]
        if (details == null) {
            SK_err("Produit non trouvé pour le plan $productId")
            errorMessage.value = appContext.getString(R.string.product_not_available)
            isLoading.value = false
            return
        }

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken.isNullOrEmpty()) {
            SK_err("Aucune offre/offerToken pour $productId")
            errorMessage.value = appContext.getString(R.string.product_not_available)
            isLoading.value = false
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams
            .newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams
            .newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val br = billingClient.launchBillingFlow(activity, flowParams)
        if (br.responseCode != BillingClient.BillingResponseCode.OK) {
            SK_err("launchBillingFlow échec: ${br.debugMessage}")
            errorMessage.value = appContext.getString(R.string.purchases_not_authorized)
            isLoading.value = false
        } else {
            SK_log("Billing flow lancé pour $productId")
        }
    }

    private fun queryProducts(onComplete: () -> Unit = {}) {
        SK_log("Chargement ProductDetails pour WEEKLY/MONTHLY")
        val products = listOf(
            QueryProductDetailsParams.Product
                .newBuilder()
                .setProductId(SubscriptionPlanType.WEEKLY.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product
                .newBuilder()
                .setProductId(SubscriptionPlanType.MONTHLY.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsCache = list.associateBy { it.productId }
                SK_log("Produits reçus: ${list.size} -> ${list.map { it.productId }}")
                onComplete()
            } else {
                SK_err("Erreur chargement produits: ${result.debugMessage}")
                errorMessage.value = appContext.getString(R.string.product_loading_error)
                isLoading.value = false
            }
        }
    }

    // endregion

    // region PurchasesUpdatedListener

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        SK_log("Transactions mises à jour: code=${billingResult.responseCode}")

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    purchases.forEach { handlePurchase(it) }
                } else {
                    isLoading.value = false
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                SK_log("Achat annulé par l’utilisateur")
                errorMessage.value = appContext.getString(R.string.purchase_canceled)
                isLoading.value = false
            }
            else -> {
                SK_err("Achat échoué: ${billingResult.debugMessage}")
                errorMessage.value = appContext.getString(R.string.receipt_validation_failed)
                isLoading.value = false
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        SK_log("handlePurchase state=${purchase.purchaseState}, acknowledged=${purchase.isAcknowledged}")

        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            // En cours / différé
            SK_log("Achat en cours/différé…")
            return
        }

        // 1) Valider côté serveur (IMPORTANT)
        val productId = purchase.products.firstOrNull().orEmpty()
        validatePurchaseWithFirebase(
            purchaseToken = purchase.purchaseToken,
            productId = productId,
            onSuccess = {
                // 2) Acknowledge côté client seulement si la validation serveur a réussi
                if (!purchase.isAcknowledged) {
                    val params = AcknowledgePurchaseParams
                        .newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient.acknowledgePurchase(params) { br ->
                        if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                            SK_log("✅ Purchase acknowledged")
                        } else {
                            SK_warn("Acknowledge échec: ${br.debugMessage}")
                        }
                    }
                }

                isSubscribed.value = true
                errorMessage.value = null
                analytics.logEvent("achat_valide", null)
                onSubscriptionValidated.tryEmit(Unit)
                SK_log("✅ Validation réussie & abonnement actif")
                isLoading.value = false
            },
            onError = { msg ->
                SK_err("Erreur validation: $msg")
                errorMessage.value = appContext.getString(R.string.receipt_validation_failed)
                isLoading.value = false
            }
        )
    }

    // endregion

    // region BillingClientStateListener

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            SK_log("BillingClient prêt")
            // Précharge les produits et tente une restauration silencieuse
            queryProducts()
            restorePurchases(silent = true)
        } else {
            SK_err("Billing setup failed: ${billingResult.debugMessage}")
        }
    }

    override fun onBillingServiceDisconnected() {
        SK_warn("Billing service déconnecté — reconnexion au prochain besoin.")
    }

    // endregion

    // region Firebase validation

    private fun validatePurchaseWithFirebase(
        purchaseToken: String,
        productId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        finally: (() -> Unit)? = null
    ) {
        SK_log("Validation serveur (validateGooglePurchase) – productId=$productId, token.len=${purchaseToken.length}")
        val payload = hashMapOf(
            "purchaseToken" to purchaseToken,
            "productId" to productId
        )

        functions
            .getHttpsCallable("validateGooglePurchase")
            .call(payload)
            .addOnSuccessListener { r ->
                val data = r.data as? Map<*, *>
                val success = (data?.get("success") as? Boolean) == true
                if (success) onSuccess() else onError("Server returned success=false")
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Unknown server error")
            }
            .addOnCompleteListener {
                finally?.invoke()
            }
    }

    // endregion

    // region Logging helpers

    private fun SK_log(msg: String) = Log.i(TAG, msg)
    private fun SK_warn(msg: String) = Log.w(TAG, msg)
    private fun SK_err(msg: String) = Log.e(TAG, msg)

    // endregion
}
