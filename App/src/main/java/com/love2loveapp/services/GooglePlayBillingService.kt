package com.love2loveapp.services

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Service Google Play Billing pour remplacer StoreKit iOS
 * Gère les abonnements Love2Love via Google Play
 */
class GooglePlayBillingService private constructor() : PurchasesUpdatedListener {
    
    private var billingClient: BillingClient? = null
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    
    // États observables
    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()
    
    private val _availableProducts = MutableStateFlow<List<SubscriptionProduct>>(emptyList())
    val availableProducts: StateFlow<List<SubscriptionProduct>> = _availableProducts.asStateFlow()
    
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()
    
    private val _purchaseState = MutableStateFlow(PurchaseState.IDLE)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    companion object {
        val instance: GooglePlayBillingService by lazy { GooglePlayBillingService() }
        
        // IDs des produits d'abonnement (remplacez par vos vrais IDs du Play Console)
        const val WEEKLY_SUBSCRIPTION_ID = "love2love_premium_weekly"
        const val MONTHLY_SUBSCRIPTION_ID = "love2love_premium_monthly"
        
        val SUBSCRIPTION_PRODUCT_IDS = listOf(
            WEEKLY_SUBSCRIPTION_ID,
            MONTHLY_SUBSCRIPTION_ID
        )
    }
    
    enum class BillingConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    enum class PurchaseState {
        IDLE,
        PURCHASING,
        PURCHASED,
        ERROR,
        CANCELLED
    }
    
    data class SubscriptionProduct(
        val productId: String,
        val title: String,
        val description: String,
        val price: String,
        val priceCurrencyCode: String,
        val priceAmountMicros: Long,
        val subscriptionPeriod: String, // P1W, P1M, etc.
        val productDetails: ProductDetails
    )
    
    /**
     * Initialise et connecte le client de facturation
     */
    fun initialize(context: Context) {
        if (billingClient?.isReady == true) {
            Log.d("GooglePlayBilling", "✅ Client déjà connecté")
            return
        }
        
        Log.d("GooglePlayBilling", "🔄 Initialisation du client de facturation")
        _connectionState.value = BillingConnectionState.CONNECTING
        
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        
        startConnection()
    }
    
    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    Log.d("GooglePlayBilling", "✅ Connexion réussie")
                    _connectionState.value = BillingConnectionState.CONNECTED
                    _errorMessage.value = null
                    
                    // Charger les produits disponibles
                    querySubscriptionProducts()
                    // Vérifier les achats existants
                    queryPurchases()
                } else {
                    Log.e("GooglePlayBilling", "❌ Erreur de connexion: ${billingResult.debugMessage}")
                    _connectionState.value = BillingConnectionState.ERROR
                    _errorMessage.value = "Erreur de connexion au Play Store"
                }
            }
            
            override fun onBillingServiceDisconnected() {
                Log.w("GooglePlayBilling", "⚠️ Connexion perdue")
                _connectionState.value = BillingConnectionState.DISCONNECTED
            }
        })
    }
    
    /**
     * Charge les détails des produits d'abonnement
     */
    private fun querySubscriptionProducts() {
        val productList = SUBSCRIPTION_PRODUCT_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                Log.d("GooglePlayBilling", "✅ Produits chargés: ${productDetailsList.size}")
                
                val subscriptionProducts = productDetailsList.map { productDetails ->
                    productDetailsMap[productDetails.productId] = productDetails
                    
                    val subscriptionOffer = productDetails.subscriptionOfferDetails?.firstOrNull()
                    val pricingPhase = subscriptionOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    
                    SubscriptionProduct(
                        productId = productDetails.productId,
                        title = productDetails.title,
                        description = productDetails.description,
                        price = pricingPhase?.formattedPrice ?: "",
                        priceCurrencyCode = pricingPhase?.priceCurrencyCode ?: "",
                        priceAmountMicros = pricingPhase?.priceAmountMicros ?: 0L,
                        subscriptionPeriod = subscriptionOffer?.basePlanId ?: "",
                        productDetails = productDetails
                    )
                }
                
                _availableProducts.value = subscriptionProducts
            } else {
                Log.e("GooglePlayBilling", "❌ Erreur chargement produits: ${billingResult.debugMessage}")
                _errorMessage.value = "Impossible de charger les abonnements"
            }
        }
    }
    
    /**
     * Vérifie les achats existants
     */
    fun queryPurchases() {
        if (billingClient?.isReady != true) {
            Log.w("GooglePlayBilling", "⚠️ Client non connecté")
            return
        }
        
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        
        billingClient?.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                Log.d("GooglePlayBilling", "✅ Achats vérifiés: ${purchasesList.size}")
                handlePurchases(purchasesList)
            } else {
                Log.e("GooglePlayBilling", "❌ Erreur vérification achats: ${billingResult.debugMessage}")
            }
        }
    }
    
    /**
     * Lance l'achat d'un abonnement
     */
    suspend fun purchaseSubscription(activity: Activity, productId: String): Boolean {
        if (billingClient?.isReady != true) {
            Log.e("GooglePlayBilling", "❌ Client non connecté")
            _errorMessage.value = "Service de facturation non disponible"
            return false
        }
        
        val productDetails = productDetailsMap[productId]
        if (productDetails == null) {
            Log.e("GooglePlayBilling", "❌ Produit non trouvé: $productId")
            _errorMessage.value = "Produit non disponible"
            return false
        }
        
        val subscriptionOffer = productDetails.subscriptionOfferDetails?.firstOrNull()
        if (subscriptionOffer == null) {
            Log.e("GooglePlayBilling", "❌ Aucune offre d'abonnement pour: $productId")
            _errorMessage.value = "Offre d'abonnement non disponible"
            return false
        }
        
        Log.d("GooglePlayBilling", "🛒 Lancement achat: $productId")
        _purchaseState.value = PurchaseState.PURCHASING
        _errorMessage.value = null
        
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(subscriptionOffer.offerToken)
            .build()
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        
        val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)
        
        return if (billingResult?.responseCode == BillingResponseCode.OK) {
            Log.d("GooglePlayBilling", "✅ Flux d'achat lancé")
            true
        } else {
            Log.e("GooglePlayBilling", "❌ Erreur lancement achat: ${billingResult?.debugMessage}")
            _purchaseState.value = PurchaseState.ERROR
            _errorMessage.value = "Impossible de lancer l'achat"
            false
        }
    }
    
    /**
     * Callback des mises à jour d'achat
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                Log.d("GooglePlayBilling", "✅ Achat réussi")
                purchases?.let { handlePurchases(it) }
                _purchaseState.value = PurchaseState.PURCHASED
            }
            BillingResponseCode.USER_CANCELED -> {
                Log.d("GooglePlayBilling", "⏹️ Achat annulé par l'utilisateur")
                _purchaseState.value = PurchaseState.CANCELLED
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d("GooglePlayBilling", "ℹ️ Produit déjà possédé")
                _isSubscribed.value = true
                _purchaseState.value = PurchaseState.PURCHASED
            }
            else -> {
                Log.e("GooglePlayBilling", "❌ Erreur d'achat: ${billingResult.debugMessage}")
                _purchaseState.value = PurchaseState.ERROR
                _errorMessage.value = "Erreur lors de l'achat"
            }
        }
    }
    
    /**
     * Traite les achats reçus
     */
    private fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                Log.d("GooglePlayBilling", "✅ Achat confirmé: ${purchase.products}")
                _isSubscribed.value = true
                
                // Acknowledger l'achat si nécessaire
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
                
                // TODO: Vérifier l'achat côté serveur via Firebase Cloud Functions
                // validatePurchaseOnServer(purchase)
            }
        }
    }
    
    /**
     * Acknowledger un achat
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                Log.d("GooglePlayBilling", "✅ Achat acknowledgé")
            } else {
                Log.e("GooglePlayBilling", "❌ Erreur acknowledgement: ${billingResult.debugMessage}")
            }
        }
    }
    
    /**
     * Obtient le prix formaté d'un produit
     */
    fun getFormattedPrice(productId: String): String? {
        val productDetails = productDetailsMap[productId]
        return productDetails?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
    }
    
    /**
     * Vérifie si un produit spécifique est disponible
     */
    fun isProductAvailable(productId: String): Boolean {
        return productDetailsMap.containsKey(productId)
    }
    
    /**
     * Nettoie les ressources
     */
    fun cleanup() {
        billingClient?.endConnection()
        billingClient = null
        productDetailsMap.clear()
        _connectionState.value = BillingConnectionState.DISCONNECTED
    }
    
    /**
     * Efface les erreurs
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Remet à zéro l'état d'achat
     */
    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.IDLE
    }
}
