package com.love2loveapp.services.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.love2loveapp.model.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Service Google Play Billing - Équivalent AppleReceiptService iOS
 * 
 * Architecture similaire à iOS :
 * - Gestion des états avec @Published (StateFlow)
 * - Validation Firebase côté serveur
 * - Support des plans hebdomadaire et mensuel
 * - Gestion des erreurs et retry automatique
 */
class GooglePlayBillingService private constructor(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    
    companion object {
        private const val TAG = "GooglePlayBilling"
        
        @Volatile
        private var INSTANCE: GooglePlayBillingService? = null
        
        fun getInstance(context: Context): GooglePlayBillingService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GooglePlayBillingService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // === États Observables (équivalent @Published iOS) ===
    
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _selectedPlan = MutableStateFlow(SubscriptionPlanType.MONTHLY)
    val selectedPlan: StateFlow<SubscriptionPlanType> = _selectedPlan.asStateFlow()
    
    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()
    
    private val _billingConnectionState = MutableStateFlow(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
    val billingConnectionState: StateFlow<Int> = _billingConnectionState.asStateFlow()

    // === Configuration ===
    
    enum class SubscriptionPlanType(val productId: String) {
        WEEKLY(AppConstants.AndroidProducts.WEEKLY_SUBSCRIPTION),
        MONTHLY(AppConstants.AndroidProducts.MONTHLY_SUBSCRIPTION);
        
        val rawValue: String get() = productId
    }
    
    private val productIdentifiers = listOf(
        AppConstants.AndroidProducts.WEEKLY_SUBSCRIPTION,
        AppConstants.AndroidProducts.MONTHLY_SUBSCRIPTION
    )
    
    // === Billing Client ===
    
    private var billingClient: BillingClient? = null
    
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        handlePurchasesUpdated(billingResult, purchases)
    }
    
    private val billingClientStateListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(billingResult: BillingResult) {
            Log.d(TAG, "🔥 Billing setup finished: ${billingResult.responseCode}")
            _billingConnectionState.value = billingResult.responseCode
            
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "✅ Billing client connecté avec succès")
                queryProductDetails()
                queryExistingPurchases()
            } else {
                Log.e(TAG, "❌ Erreur connexion billing: ${billingResult.debugMessage}")
                _errorMessage.value = "Erreur de connexion au Play Store: ${billingResult.debugMessage}"
            }
        }
        
        override fun onBillingServiceDisconnected() {
            Log.d(TAG, "🔌 Billing service disconnected")
            _billingConnectionState.value = BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
        }
    }

    // === Initialisation ===
    
    init {
        initializeBillingClient()
    }
    
    private fun initializeBillingClient() {
        Log.d(TAG, "🚀 Initialisation Google Play Billing")
        
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
        
        connectToBillingService()
    }
    
    private fun connectToBillingService() {
        billingClient?.startConnection(billingClientStateListener)
    }

    // === Gestion des Produits (équivalent StoreKitPricingService) ===
    
    private fun queryProductDetails() {
        val productList = productIdentifiers.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "✅ Produits chargés: ${productDetailsList.size}")
                
                val detailsMap = productDetailsList.associateBy { it.productId }
                _productDetails.value = detailsMap
                
                // Log des prix pour debug
                productDetailsList.forEach { product ->
                    val price = product.subscriptionOfferDetails?.firstOrNull()
                        ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        ?.formattedPrice
                    Log.d(TAG, "📦 Produit: ${product.productId} - Prix: $price")
                }
            } else {
                Log.e(TAG, "❌ Erreur chargement produits: ${billingResult.debugMessage}")
                _errorMessage.value = "Erreur chargement des prix: ${billingResult.debugMessage}"
            }
        }
    }

    // === Achat d'Abonnement (équivalent purchaseSubscription iOS) ===
    
    /**
     * Lance le processus d'achat - équivalent iOS purchaseSubscription()
     */
    fun purchaseSubscription(activity: Activity) {
        Log.d(TAG, "🛒 Démarrage achat abonnement: ${_selectedPlan.value}")
        
        if (_billingConnectionState.value != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "❌ Billing client non connecté")
            _errorMessage.value = "Service de paiement non disponible"
            return
        }
        
        val productId = _selectedPlan.value.productId
        val productDetails = _productDetails.value[productId]
        
        if (productDetails == null) {
            Log.e(TAG, "❌ Détails produit non trouvés pour: $productId")
            _errorMessage.value = "Produit non disponible"
            return
        }
        
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.e(TAG, "❌ Token d'offre non trouvé pour: $productId")
            _errorMessage.value = "Offre d'abonnement non disponible"
            return
        }
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        
        _isLoading.value = true
        _errorMessage.value = null
        
        val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)
        if (billingResult?.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "❌ Erreur lancement achat: ${billingResult?.debugMessage}")
            _isLoading.value = false
            _errorMessage.value = "Erreur lancement achat: ${billingResult?.debugMessage}"
        }
    }
    
    /**
     * Sélectionner un plan d'abonnement
     */
    fun selectPlan(plan: SubscriptionPlanType) {
        Log.d(TAG, "📋 Plan sélectionné: $plan")
        _selectedPlan.value = plan
    }

    // === Gestion des Transactions ===
    
    private fun handlePurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        Log.d(TAG, "🔄 Mise à jour achats: ${billingResult.responseCode}")
        
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    Log.d(TAG, "✅ Achat réussi: ${purchase.products}")
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "🚫 Achat annulé par l'utilisateur")
                _isLoading.value = false
                _errorMessage.value = null // Pas d'erreur pour annulation
            }
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                Log.e(TAG, "❌ Service Google Play non disponible")
                _isLoading.value = false
                _errorMessage.value = "Service Google Play non disponible"
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                Log.e(TAG, "❌ Facturation non disponible")
                _isLoading.value = false
                _errorMessage.value = "Facturation non disponible sur cet appareil"
            }
            else -> {
                Log.e(TAG, "❌ Erreur achat: ${billingResult.debugMessage}")
                _isLoading.value = false
                _errorMessage.value = "Erreur d'achat: ${billingResult.debugMessage}"
            }
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                // Valider avec Firebase avant d'acquitter (comme iOS)
                validatePurchaseWithFirebase(purchase)
            } else {
                // Déjà validé et acquitté
                _isSubscribed.value = true
                
                // 🔥 CRITIQUE: Mettre à jour aussi l'AppState pour débloquer les catégories premium
                try {
                    com.love2loveapp.AppDelegate.appState.updateUserSubscriptionStatus(true)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur mise à jour AppState pour abonnement validé", e)
                }
                
                _isLoading.value = false
                Log.d(TAG, "✅ Abonnement déjà validé")
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "⏳ Achat en attente (approbation parentale?)")
            _isLoading.value = false
            _errorMessage.value = "Achat en attente d'approbation"
        }
    }

    // === Validation Firebase (équivalent validateReceiptWithFirebase iOS) ===
    
    private fun validatePurchaseWithFirebase(purchase: Purchase) {
        Log.d(TAG, "🔥 Validation Firebase pour: ${purchase.products}")
        
        val productId = purchase.products.firstOrNull()
        if (productId == null) {
            Log.e(TAG, "❌ Produit ID manquant")
            _isLoading.value = false
            _errorMessage.value = "Erreur: produit non identifié"
            return
        }
        
        val validatePurchase = functions.getHttpsCallable("validateGooglePurchase")
        
        validatePurchase.call(mapOf(
            "productId" to productId,
            "purchaseToken" to purchase.purchaseToken
        )).addOnCompleteListener { task ->
            _isLoading.value = false
            
            if (task.isSuccessful) {
                val data = task.result?.data as? Map<String, Any>
                val success = data?.get("success") as? Boolean ?: false
                
                if (success) {
                    Log.d(TAG, "✅ Validation Firebase réussie")
                    _isSubscribed.value = true
                    _errorMessage.value = null
                    
                    // Acquitter l'achat après validation Firebase
                    acknowledgePurchase(purchase)
                } else {
                    Log.e(TAG, "❌ Validation Firebase échouée")
                    _errorMessage.value = "Erreur de validation de l'abonnement"
                }
            } else {
                Log.e(TAG, "❌ Erreur appel Firebase: ${task.exception?.message}")
                _errorMessage.value = "Erreur de validation: ${task.exception?.localizedMessage}"
            }
        }
    }
    
    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "✅ Achat acquitté avec succès")
            } else {
                Log.e(TAG, "❌ Erreur acquittement: ${billingResult.debugMessage}")
            }
        }
    }

    // === Restauration d'Achats (équivalent restorePurchases iOS) ===
    
    fun restorePurchases() {
        Log.d(TAG, "🔄 Restauration des achats")
        
        if (_billingConnectionState.value != BillingClient.BillingResponseCode.OK) {
            _errorMessage.value = "Service de paiement non disponible"
            return
        }
        
        _isLoading.value = true
        queryExistingPurchases()
    }
    
    private fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        
        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "✅ Achats existants récupérés: ${purchases.size}")
                
                val activePurchases = purchases.filter { 
                    it.purchaseState == Purchase.PurchaseState.PURCHASED 
                }
                
                if (activePurchases.isNotEmpty()) {
                    Log.d(TAG, "🎉 Abonnements actifs trouvés: ${activePurchases.size}")
                    _isSubscribed.value = true
                    
                    // Valider chaque achat actif avec Firebase
                    activePurchases.forEach { purchase ->
                        if (!purchase.isAcknowledged) {
                            validatePurchaseWithFirebase(purchase)
                        }
                    }
                } else {
                    Log.d(TAG, "📭 Aucun abonnement actif trouvé")
                    _isSubscribed.value = false
                }
                
                _isLoading.value = false
            } else {
                Log.e(TAG, "❌ Erreur récupération achats: ${billingResult.debugMessage}")
                _isLoading.value = false
                _errorMessage.value = "Erreur lors de la restauration"
            }
        }
    }

    // === Utilitaires ===
    
    /**
     * Obtenir le prix formaté pour un produit
     */
    fun getFormattedPrice(productId: String): String {
        val product = _productDetails.value[productId]
        return product?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()
            ?.formattedPrice ?: "Prix non disponible"
    }
    
    /**
     * Vérifier si le billing client est prêt
     */
    fun isReady(): Boolean {
        return _billingConnectionState.value == BillingClient.BillingResponseCode.OK
    }
    
    /**
     * Nettoyer les ressources
     */
    fun disconnect() {
        Log.d(TAG, "🔌 Déconnexion du service billing")
        billingClient?.endConnection()
    }
    
    /**
     * Réinitialiser les erreurs
     */
    fun clearError() {
        _errorMessage.value = null
    }
}