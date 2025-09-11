// SubscriptionService.kt
package com.lyes.love2love.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Réécriture Android de SubscriptionService (StoreKit -> Google Play Billing).
 * - BillingClient v7+ (queryProductDetailsAsync, queryPurchasesAsync, acknowledgePurchase)
 * - StateFlow pour exposer l'état (équivalent @Published)
 * - Firebase: Auth / Firestore / Analytics
 */
class SubscriptionService private constructor(
    private val appContext: Context
) : PurchasesUpdatedListener {

    companion object {
        @Volatile private var INSTANCE: SubscriptionService? = null

        fun getInstance(context: Context): SubscriptionService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubscriptionService(context.applicationContext).also { it.init() ; INSTANCE = it }
            }
        }

        // Identifiants produits (Play Console)
        private const val WEEKLY = "com.lyes.love2love.subscription.weekly"
        private const val MONTHLY = "com.lyes.love2love.subscription.monthly"
    }

    // ---- State (équivalents @Published) ----
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _lastPurchasedProduct = MutableStateFlow<ProductDetails?>(null)
    val lastPurchasedProduct: StateFlow<ProductDetails?> = _lastPurchasedProduct.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ---- Infra ----
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var billingClient: BillingClient
    private val connected = AtomicBoolean(false)

    // ---- Initialisation ----
    private fun init() {
        // v7+: enablePendingPurchases(PendingPurchasesParams)
        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts() // autorise les pending purchases pour les in-app; ok même si on n’en a pas
            .build()

        billingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(pendingParams)
            .build()

        connectIfNeeded()
        clearPendingErrors()
        // Monitoring périodique comme dans ta version Swift (toutes les heures)
        startSubscriptionStatusMonitoring()
    }

    private fun connectIfNeeded(onReady: (() -> Unit)? = null) {
        if (connected.get()) { onReady?.invoke(); return }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected.set(true)
                    // Charger produits & achats existants
                    scope.launch {
                        loadProductsInternal()
                        restorePurchasesInternal()
                        // Statut depuis Firestore (cohérence multi-appareils)
                        checkSubscriptionStatusFromFirestore()
                    }
                    onReady?.invoke()
                } else {
                    setError(appContext.getString(
                        R.string.error_billing_setup_failed, result.debugMessage ?: "Unknown"
                    ))
                }
            }

            override fun onBillingServiceDisconnected() {
                connected.set(false)
                // Reconnexion soft
                scope.launch {
                    delay(1500)
                    connectIfNeeded()
                }
            }
        })
    }

    // ---- API publique (équivalent Swift) ----
    fun loadProducts() {
        scope.launch { loadProductsInternal() }
    }

    fun purchase(activity: Activity, product: ProductDetails) {
        scope.launch {
            runCatching {
                val offerToken = chooseOfferToken(product)
                    ?: throw IllegalStateException("Aucune offre disponible pour ${product.productId}")

                val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product)
                    .setOfferToken(offerToken)
                    .build()

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .build()

                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                    val result = billingClient.launchBillingFlow(activity, flowParams)
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        _isLoading.value = false
                        setError(appContext.getString(
                            R.string.error_launch_purchase, result.debugMessage ?: "Unknown"
                        ))
                    }
                }
            }.onFailure {
                _isLoading.value = false
                setError(it.localizedMessage ?: appContext.getString(R.string.error_generic))
            }
        }
    }

    fun restorePurchases() {
        scope.launch { restorePurchasesInternal() }
    }

    suspend fun handleSubscriptionExpired() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Log.d("SubscriptionService", "Résiliation pour $uid")
        revokeUserSubscription(uid)
        withContext(Dispatchers.Main) { _isSubscribed.value = false }
        updateSubscriptionStatusInFirestore(false)
    }

    fun startSubscriptionStatusMonitoring() {
        // Vérif toutes les heures (comme Timer.scheduledTimer)
        scope.launch {
            while (isActive) {
                delay(3_600_000L)
                checkSubscriptionStatusFromFirestore()
            }
        }
        Log.d("SubscriptionService", "Monitoring des abonnements démarré")
    }

    // ---- PurchasesUpdatedListener ----
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d("SubscriptionService", "onPurchasesUpdated: code=${result.responseCode}")
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch {
                purchases.forEach { handlePurchase(it) }
            }
        } else {
            _isLoading.value = false
            when (result.responseCode) {
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    // Annulation utilisateur – pas d’erreur UI nécessaire
                }
                else -> setError(appContext.getString(
                    R.string.error_purchase_failed, result.debugMessage ?: "Unknown"
                ))
            }
        }
    }

    // ---- Interne : chargement produits ----
    private suspend fun loadProductsInternal() {
        _isLoading.value = true
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(WEEKLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(MONTHLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        val productsDeferred = CompletableDeferred<List<ProductDetails>>()
        billingClient.queryProductDetailsAsync(query) { br, list ->
            if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                productsDeferred.complete(list)
            } else {
                productsDeferred.completeExceptionally(
                    IllegalStateException("queryProductDetailsAsync: ${br.debugMessage}")
                )
            }
        }

        // Timeout de sécurité (30s) comme ta version Swift
        val details = withTimeoutOrNull(30_000L) { productsDeferred.await() }
        if (details == null) {
            _isLoading.value = false
            setError(appContext.getString(R.string.error_products_timeout))
            return
        }

        _products.value = details
        if (details.isEmpty()) {
            setError(appContext.getString(R.string.error_no_products))
        }
        _isLoading.value = false
    }

    // ---- Interne : restauration / acknowledge ----
    private suspend fun restorePurchasesInternal() {
        if (!connected.get()) connectIfNeeded()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = billingClient.queryPurchasesAsync(params)
        val purchases = result.purchasesList ?: emptyList()
        Log.d("SubscriptionService", "restore: ${purchases.size} purchase(s)")

        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    handlePurchase(purchase) // fera acknowledge si nécessaire
                }
                Purchase.PurchaseState.PENDING -> {
                    // Informer l’utilisateur qu’un achat est en attente s’il faut
                }
                else -> Unit
            }
        }

        // Met à jour l’UI à partir de Firestore (source de vérité distante)
        checkSubscriptionStatusFromFirestore()
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        val tokenMasked = purchase.purchaseToken.take(8) + "…"
        Log.d("SubscriptionService", "handlePurchase: state=${purchase.purchaseState}, token=$tokenMasked")

        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // 1) Acknowledge obligatoire
        if (!purchase.isAcknowledged) {
            val ackResult = CompletableDeferred<BillingResult>()
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { br ->
                ackResult.complete(br)
            }
            val br = ackResult.await()
            if (br.responseCode != BillingClient.BillingResponseCode.OK) {
                setError(appContext.getString(R.string.error_acknowledge_failed, br.debugMessage ?: "Unknown"))
                return
            }
        }

        // 2) (Optionnel mais recommandé) Validation serveur du token (Cloud Function)
        // try { validateGooglePurchaseOnServer(purchase) } catch (_: Exception) {}

        // 3) Mise à jour locale & Firestore
        Firebase.analytics.logEvent("abonnement_reussi", null)
        withContext(Dispatchers.Main) {
            _isSubscribed.value = true
            _isLoading.value = false
        }

        // Marquer comme "direct" puis push statut isSubscribed
        markSubscriptionAsDirect()
        updateSubscriptionStatusInFirestore(true)

        // Mémoriser le ProductDetails correspondant pour l’UI
        val pd = products.value.firstOrNull { it.productId in purchase.products }
        _lastPurchasedProduct.value = pd
    }

    // ---- Firestore helpers ----
    private fun updateSubscriptionStatusInFirestore(isSub: Boolean) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid)
            .update(
                mapOf(
                    "isSubscribed" to isSub,
                    "subscriptionExpiredAt" to if (isSub) FieldValue.delete() else com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener { Log.d("SubscriptionService", "Firestore: isSubscribed=$isSub") }
            .addOnFailureListener { Log.w("SubscriptionService", "Firestore update failed", it) }
    }

    private fun markSubscriptionAsDirect() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid)
            .update(
                mapOf(
                    "subscriptionType" to "direct",
                    "subscriptionPurchasedAt" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener { Log.d("SubscriptionService", "Firestore: subscriptionType=direct") }
            .addOnFailureListener { Log.w("SubscriptionService", "mark direct failed", it) }
    }

    private suspend fun revokeUserSubscription(userId: String) {
        runCatching {
            Firebase.firestore.collection("users").document(userId)
                .update(
                    mapOf(
                        "isSubscribed" to false,
                        "subscriptionExpiredAt" to com.google.firebase.Timestamp.now(),
                        "subscriptionType" to FieldValue.delete()
                    )
                ).awaitUnit()
        }.onSuccess {
            Log.d("SubscriptionService", "Abonnement révoqué pour $userId")
        }.onFailure {
            Log.e("SubscriptionService", "Révocation échouée", it)
        }
    }

    private suspend fun checkSubscriptionStatusFromFirestore() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        runCatching {
            val snap = Firebase.firestore.collection("users").document(uid).get().await()
            val remote = (snap.getBoolean("isSubscribed") ?: false)
            withContext(Dispatchers.Main) { _isSubscribed.value = remote }
        }.onFailure {
            Log.w("SubscriptionService", "check status failed", it)
        }
    }

    // ---- Utilitaires ----
    private fun chooseOfferToken(details: ProductDetails): String? {
        // Simple: prendre la 1ère offer (base plan / offer) disponible
        // Tu peux, si besoin, filtrer via tags/basePlanId pour “weekly”/“monthly”.
        val subs = details.subscriptionOfferDetails ?: return null
        return subs.firstOrNull()?.offerToken
    }

    private fun clearPendingErrors() {
        _errorMessage.value = null
    }

    private fun setError(msg: String) {
        Log.w("SubscriptionService", "UI error: $msg")
        _errorMessage.value = msg
    }
}

// ---- Petites extensions coroutines pour les Tasks Firebase ----
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result, null)
            else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }

private suspend fun com.google.android.gms.tasks.Task<Void>.awaitUnit() =
    suspendCancellableCoroutine<Unit> { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(Unit, null)
            else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
