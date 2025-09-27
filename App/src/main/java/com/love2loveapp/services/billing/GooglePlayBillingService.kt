package com.love2loveapp.services.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.love2loveapp.models.AppConstants
import com.love2loveapp.services.subscription.SubscriptionNotificationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlin.coroutines.resume

/**
 * Service Google Play Billing - Équivalent AppleReceiptService iOS
 * 
 * Architecture similaire à iOS :
 * - Gestion des états avec StateFlow (équivalent @Published)
 * - Validation Firebase côté serveur
 * - Support des plans hebdomadaire et mensuel
 * - Gestion des erreurs et retry automatique
 */
class GooglePlayBillingService private constructor(private val context: Context) : PurchasesUpdatedListener {
    
    companion object {
        private const val TAG = "GooglePlayBillingService"
        
        @Volatile
        private var INSTANCE: GooglePlayBillingService? = null
        
        fun getInstance(context: Context): GooglePlayBillingService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GooglePlayBillingService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // === Types d'abonnement (équivalent enum iOS) ===
    enum class SubscriptionPlanType(val productId: String) {
        WEEKLY(AppConstants.AndroidProducts.WEEKLY_SUBSCRIPTION),
        MONTHLY(AppConstants.AndroidProducts.MONTHLY_SUBSCRIPTION)
    }
    
    // === États de connexion ===
    enum class BillingConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    // === Client de billing ===
    private var billingClient: BillingClient? = null
    private val functions = FirebaseFunctions.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Service de notifications
    private val notificationService by lazy { SubscriptionNotificationService.getInstance(context) }
    
    // === États observables (équivalent @Published iOS) ===
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _selectedPlan = MutableStateFlow(SubscriptionPlanType.MONTHLY)
    val selectedPlan: StateFlow<SubscriptionPlanType> = _selectedPlan.asStateFlow()
    
    // Structure pour stocker prix + devise de façon précise
    data class ProductPricing(
        val formattedPrice: String,        // Prix formaté par Google (ex: "5,99 €")
        val priceAmountMicros: Long,       // Prix en micros (ex: 5990000 pour 5,99)
        val priceCurrencyCode: String      // Code devise (ex: "EUR", "USD")
    )
    
    private val _productDetails = MutableStateFlow<Map<SubscriptionPlanType, String>>(emptyMap())
    val productDetails: StateFlow<Map<SubscriptionPlanType, String>> = _productDetails.asStateFlow()
    
    private val _productPricing = MutableStateFlow<Map<SubscriptionPlanType, ProductPricing>>(emptyMap())
    val productPricing: StateFlow<Map<SubscriptionPlanType, ProductPricing>> = _productPricing.asStateFlow()
    
    private val _billingConnectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val billingConnectionState: StateFlow<BillingConnectionState> = _billingConnectionState.asStateFlow()
    
    // CoroutineScope pour les opérations en arrière-plan
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        try {
            Log.d(TAG, "🏠 Début initialisation GooglePlayBillingService...")
            Log.d(TAG, "📱 Context type: ${context.javaClass.simpleName}")
            Log.d(TAG, "🔌 Thread: ${Thread.currentThread().name}")
            Log.d(TAG, "🔌 Process ID: ${android.os.Process.myPid()}")
            
            // Vérifier que Google Play Services est disponible
            checkGooglePlayServicesAvailability()
            
            // Initialiser avec délai pour éviter les crashs
            initializeBillingClientSafely()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERREUR CRITIQUE dans init GooglePlayBillingService", e)
            Log.e(TAG, "❌ Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "❌ Message: ${e.message}")
            Log.e(TAG, "❌ Cause: ${e.cause}")
            e.printStackTrace()
        }
    }
    
    // === Initialisation sécurisée ===
    private fun checkGooglePlayServicesAvailability() {
        try {
            Log.d(TAG, "🔍 Vérification disponibilité Google Play Services...")
            val packageInfo = context.packageManager.getPackageInfo("com.android.vending", 0)
            Log.d(TAG, "✅ Google Play Store détecté version: ${packageInfo.versionName}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Google Play Store non détecté", e)
        }
    }
    
    private fun initializeBillingClientSafely() {
        Log.d(TAG, "🏗️ Initialisation sécurisée du client Google Play Billing")
        
        try {
            Log.d(TAG, "🔨 Création BillingClient.Builder...")
            
            val builder = BillingClient.newBuilder(context)
            Log.d(TAG, "✅ BillingClient.Builder créé")
            
            Log.d(TAG, "🔊 Configuration du listener...")
            builder.setListener(this)
            Log.d(TAG, "✅ Listener configuré")
            
            Log.d(TAG, "📋 Activation des achats en attente...")
            builder.enablePendingPurchases()
            Log.d(TAG, "✅ Achats en attente activés")
            
            Log.d(TAG, "🏠 Construction du BillingClient...")
            billingClient = builder.build()
            Log.d(TAG, "✅ BillingClient construit avec succès")
            
            // Attendre un peu avant de se connecter pour éviter les crashes
            serviceScope.launch {
                kotlinx.coroutines.delay(1000) // 1 seconde de délai
                Log.d(TAG, "🔌 Démarrage connexion au service avec délai...")
                connectToBillingServiceSafely()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERREUR lors de l'initialisation du BillingClient", e)
            Log.e(TAG, "❌ Détails: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // === Initialisation legacy (backup) ===
    private fun initializeBillingClient() {
        Log.d(TAG, "🏗️ Initialisation du client Google Play Billing")
        
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
            
        connectToBillingService()
    }
    
    private fun connectToBillingServiceSafely() {
        try {
            Log.d(TAG, "🔒 Connexion sécurisée au service Google Play Billing...")
            
            billingClient?.let { client ->
                Log.d(TAG, "🔍 Vérification état du client...")
                
                if (client.isReady) {
                    Log.d(TAG, "✅ Client déjà connecté")
                    _billingConnectionState.value = BillingConnectionState.CONNECTED
                    loadProductDetails()
                    return
                }
                
                Log.d(TAG, "🔌 Démarrage connexion au service Google Play Billing...")
                _billingConnectionState.value = BillingConnectionState.CONNECTING
                
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        try {
                            Log.d(TAG, "📝 Résultat connexion: code=${billingResult.responseCode}, debug=${billingResult.debugMessage}")
                            
                            if (billingResult.responseCode == BillingResponseCode.OK) {
                                Log.d(TAG, "✅ Connexion établie avec Google Play Billing")
                                _billingConnectionState.value = BillingConnectionState.CONNECTED
                                loadProductDetails()
                                checkExistingPurchases()
                            } else {
                                Log.e(TAG, "❌ Erreur de connexion: code=${billingResult.responseCode}, debug=${billingResult.debugMessage}")
                                _billingConnectionState.value = BillingConnectionState.ERROR
                                _errorMessage.value = "Erreur de connexion au service de paiement (code: ${billingResult.responseCode})"
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ ERREUR dans onBillingSetupFinished", e)
                        }
                    }
                    
                    override fun onBillingServiceDisconnected() {
                        try {
                            Log.w(TAG, "⚠️ Service Google Play Billing déconnecté")
                            _billingConnectionState.value = BillingConnectionState.DISCONNECTED
                            
                            // Retry de connexion après délai
                            serviceScope.launch {
                                kotlinx.coroutines.delay(3000) // Attendre 3 secondes
                                Log.d(TAG, "🔄 Tentative de reconnexion...")
                                connectToBillingServiceSafely()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ ERREUR dans onBillingServiceDisconnected", e)
                        }
                    }
                })
            } ?: run {
                Log.e(TAG, "❌ BillingClient est null!")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERREUR lors de la connexion au service", e)
            e.printStackTrace()
        }
    }
    
    private fun connectToBillingService() {
        billingClient?.let { client ->
            if (client.isReady) {
                Log.d(TAG, "✅ Client déjà connecté")
                _billingConnectionState.value = BillingConnectionState.CONNECTED
                loadProductDetails()
                return
            }
            
            Log.d(TAG, "🔌 Connexion au service Google Play Billing...")
            _billingConnectionState.value = BillingConnectionState.CONNECTING
            
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingResponseCode.OK) {
                        Log.d(TAG, "✅ Connexion établie avec Google Play Billing")
                        _billingConnectionState.value = BillingConnectionState.CONNECTED
                        loadProductDetails()
                        checkExistingPurchases()
                    } else {
                        Log.e(TAG, "❌ Erreur de connexion: ${billingResult.debugMessage}")
                        _billingConnectionState.value = BillingConnectionState.ERROR
                        _errorMessage.value = "Erreur de connexion au service de paiement"
                    }
                }
                
                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "⚠️ Service Google Play Billing déconnecté")
                    _billingConnectionState.value = BillingConnectionState.DISCONNECTED
                }
            })
        }
    }
    
    // === Chargement des détails produits ===
    private fun loadProductDetails() {
        Log.d(TAG, "📦 Chargement des détails produits...")
        
        val productList = SubscriptionPlanType.values().map { plan ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(plan.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
            
        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                Log.d(TAG, "✅ Détails produits chargés: ${productDetailsList.size} produits")
                
                val detailsMap = mutableMapOf<SubscriptionPlanType, String>()
                val pricingMap = mutableMapOf<SubscriptionPlanType, ProductPricing>()
                
                productDetailsList.forEach { productDetails ->
                    val planType = SubscriptionPlanType.values().find { it.productId == productDetails.productId }
                    planType?.let { plan ->
                        val pricingPhase = productDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        val price = pricingPhase?.formattedPrice ?: "N/A"
                        detailsMap[plan] = price
                        
                        if (pricingPhase != null) {
                            pricingMap[plan] = ProductPricing(
                                formattedPrice = price,
                                priceAmountMicros = pricingPhase.priceAmountMicros,
                                priceCurrencyCode = pricingPhase.priceCurrencyCode
                            )
                            Log.d(TAG, "💰 ${plan.name}: $price (${pricingPhase.priceAmountMicros} micros, ${pricingPhase.priceCurrencyCode})")
                        } else {
                            Log.d(TAG, "💰 ${plan.name}: $price")
                        }
                    }
                }
                
                _productDetails.value = detailsMap
                _productPricing.value = pricingMap
            } else {
                Log.e(TAG, "❌ Erreur chargement produits: ${billingResult.debugMessage}")
                _errorMessage.value = "Impossible de charger les informations d'abonnement"
            }
        }
    }
    
    // === Vérification des achats existants ===
    private fun checkExistingPurchases() {
        Log.d(TAG, "🔍 Vérification des achats existants...")
        
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
            
        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                Log.d(TAG, "✅ Achats trouvés: ${purchases.size}")
                
                // Trouver un abonnement Google Play actif
                val activePurchase = purchases.find { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    SubscriptionPlanType.values().any { it.productId in purchase.products }
                }
                
                if (activePurchase != null) {
                    Log.d(TAG, "🔍 Abonnement Google Play trouvé - Validation serveur...")
                    
                    // 🔥 CRITIQUE: Mettre immédiatement à jour l'AppState pour éviter de garder les catégories verrouillées
                    _isSubscribed.value = true
                    try {
                        com.love2loveapp.AppDelegate.appState.updateUserSubscriptionStatus(true)
                        Log.d(TAG, "✅ AppState mis à jour pour abonnement existant")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur mise à jour AppState pour abonnement existant", e)
                    }
                    
                    validateSubscriptionWithServer(activePurchase)
                } else {
                    Log.d(TAG, "❌ Aucun abonnement Google Play actif")
                    _isSubscribed.value = false
                    try {
                        com.love2loveapp.AppDelegate.appState.updateUserSubscriptionStatus(false)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur mise à jour AppState pour aucun abonnement", e)
                    }
                }
            } else {
                Log.e(TAG, "❌ Erreur vérification achats: ${billingResult.debugMessage}")
                _isSubscribed.value = false
            }
        }
    }
    
    /**
     * Valide un abonnement avec le serveur Firebase
     * Vérifie que le compte Firebase existe et que l'abonnement est toujours valide
     */
    private fun validateSubscriptionWithServer(purchase: Purchase) {
        serviceScope.launch {
            try {
                Log.d(TAG, "🔍 Validation serveur de l'abonnement...")
                
                // Vérifier si l'utilisateur Firebase existe et a un abonnement valide
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.d(TAG, "❌ Utilisateur Firebase non connecté - abonnement non valide")
                    _isSubscribed.value = false
                    return@launch
                }
                
                // Vérifier le document utilisateur dans Firestore
                val userDoc = firestore.collection("users").document(currentUser.uid)
                    .get()
                    .await()
                
                if (!userDoc.exists()) {
                    Log.d(TAG, "❌ Document utilisateur inexistant - compte supprimé")
                    _isSubscribed.value = false
                    return@launch
                }
                
                // Vérifier l'état d'abonnement côté serveur
                val userData = userDoc.data
                val isSubscribedOnServer = userData?.get("isSubscribed") as? Boolean ?: false
                
                if (isSubscribedOnServer) {
                    Log.d(TAG, "✅ Abonnement validé côté serveur")
                    _isSubscribed.value = true
                } else {
                    Log.d(TAG, "⚠️ Abonnement Google Play actif mais non validé côté serveur")
                    // Tenter de synchroniser avec Firebase Cloud Functions
                    validatePurchaseWithCloudFunction(purchase)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur validation serveur: ${e.message}", e)
                // En cas d'erreur, être conservateur et ne pas activer l'abonnement
                _isSubscribed.value = false
            }
        }
    }
    
    /**
     * Valide l'abonnement via Firebase Cloud Functions
     */
    private fun validatePurchaseWithCloudFunction(purchase: Purchase) {
        serviceScope.launch {
            try {
                Log.d(TAG, "☁️ Validation via Cloud Functions...")
                
                val productId = purchase.products.first()
                val data = hashMapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to productId
                )
                
                val result = functions
                    .getHttpsCallable("validateGooglePlaySubscription")
                    .call(data)
                    .await()
                
                val resultData = result.data as? Map<*, *>
                val isValid = resultData?.get("isValid") as? Boolean ?: false
                
                if (isValid) {
                    Log.d(TAG, "✅ Abonnement validé par Cloud Functions")
                    _isSubscribed.value = true
                } else {
                    Log.d(TAG, "❌ Abonnement rejeté par Cloud Functions")
                    _isSubscribed.value = false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur validation Cloud Functions: ${e.message}", e)
                _isSubscribed.value = false
            }
        }
    }
    
    // === Méthodes publiques (équivalent aux méthodes iOS) ===
    
    fun selectPlan(plan: SubscriptionPlanType) {
        Log.d(TAG, "📋 Plan sélectionné: ${plan.name}")
        _selectedPlan.value = plan
    }
    
    /**
     * Calcule le prix par utilisateur avec la devise correcte
     */
    fun calculatePricePerUser(planType: SubscriptionPlanType): String {
        val pricing = _productPricing.value[planType] ?: return "N/A"
        
        // Divise le prix en micros par 2 pour avoir le prix par utilisateur
        val pricePerUserMicros = pricing.priceAmountMicros / 2
        
        // Convertit les micros en unité monétaire (1 000 000 micros = 1 unité)
        val pricePerUserAmount = pricePerUserMicros / 1_000_000.0
        
        // Formate avec la devise correcte selon la locale du système
        return try {
            val currency = java.util.Currency.getInstance(pricing.priceCurrencyCode)
            val formatter = java.text.NumberFormat.getCurrencyInstance()
            formatter.currency = currency
            formatter.format(pricePerUserAmount)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erreur formatage devise ${pricing.priceCurrencyCode}: ${e.message}")
            // Fallback : utilise le formatage simple avec le code devise
            String.format("%.2f %s", pricePerUserAmount, pricing.priceCurrencyCode)
        }
    }
    
    suspend fun purchaseSubscription(activity: Activity): Boolean {
        Log.d(TAG, "💳 Démarrage de l'achat pour ${_selectedPlan.value.name}")
        _isLoading.value = true
        _errorMessage.value = null
        
        return try {
            val success = launchBillingFlow(activity, _selectedPlan.value)
            if (!success) {
                _errorMessage.value = "Impossible de démarrer le processus d'achat"
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de l'achat", e)
            _errorMessage.value = "Erreur lors de l'achat: ${e.message}"
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    private suspend fun launchBillingFlow(activity: Activity, plan: SubscriptionPlanType): Boolean {
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "🚀 Lancement du flow de billing pour ${plan.name}")
            
            try {
                // Vérifier que le client est connecté
                val client = billingClient
                if (client == null || !client.isReady) {
                    Log.e(TAG, "❌ Client billing non connecté")
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }

                // Récupérer les détails du produit depuis le cache
                val productList = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(plan.productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )

                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

                client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                    if (billingResult.responseCode == BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                        val productDetails = productDetailsList.first()
                        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

                        if (offerToken != null) {
                            // Créer les paramètres de billing flow
                            val productDetailsParamsList = listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .setOfferToken(offerToken)
                                    .build()
                            )

                            val billingFlowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(productDetailsParamsList)
                                .build()

                            // Lancer le flow de billing
                            val result = client.launchBillingFlow(activity, billingFlowParams)
                            val success = result.responseCode == BillingResponseCode.OK
                            
                            Log.d(TAG, if (success) "✅ Flow de billing lancé avec succès" else "❌ Erreur lancement flow: ${result.debugMessage}")
                            continuation.resume(success)
                        } else {
                            Log.e(TAG, "❌ Offer token non trouvé")
                            continuation.resume(false)
                        }
                    } else {
                        Log.e(TAG, "❌ Erreur récupération détails produit: ${billingResult.debugMessage}")
                        continuation.resume(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception lors du lancement du flow", e)
                continuation.resume(false)
            }
        }
    }
    
    suspend fun restorePurchases(): Boolean {
        Log.d(TAG, "🔄 Restauration des achats...")
        _isLoading.value = true
        _errorMessage.value = null
        
        return try {
            checkExistingPurchases()
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur restauration", e)
            _errorMessage.value = "Erreur lors de la restauration: ${e.message}"
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    // === Validation Firebase (équivalent iOS) ===
    private suspend fun validatePurchaseWithFirebase(purchase: Purchase): Boolean {
        return try {
            Log.d(TAG, "🔥 Validation Firebase pour ${purchase.products}")
            
            val data = hashMapOf(
                "productId" to purchase.products.first(),
                "purchaseToken" to purchase.purchaseToken
            )
            
            val result = functions
                .getHttpsCallable("validateGooglePurchase")
                .call(data)
                .await()
                
            val isValid = result.data as? Boolean ?: false
            Log.d(TAG, if (isValid) "✅ Achat validé par Firebase" else "❌ Achat rejeté par Firebase")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur validation Firebase", e)
            
            // 🔥 FALLBACK: Si la validation Firebase échoue, on met à jour Firestore localement
            // pour éviter de perdre l'abonnement au redémarrage
            try {
                Log.w(TAG, "🔄 Fallback: Mise à jour Firestore locale pour éviter la perte d'abonnement")
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    firestore.collection("users").document(userId)
                        .set(mapOf("isSubscribed" to true), com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener { 
                            Log.d(TAG, "✅ Fallback Firestore réussi")
                        }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "❌ Fallback Firestore échoué", error)
                        }
                } else {
                    Log.w(TAG, "⚠️ Impossible de faire le fallback - utilisateur non connecté")
                }
            } catch (fallbackError: Exception) {
                Log.e(TAG, "❌ Erreur fallback Firestore", fallbackError)
            }
            
            false
        }
    }
    
    // === Helper pour les codes de réponse ===
    private fun getResponseCodeName(responseCode: Int): String {
        return when (responseCode) {
            BillingResponseCode.OK -> "OK"
            BillingResponseCode.USER_CANCELED -> "USER_CANCELED"
            BillingResponseCode.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
            BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE"
            BillingResponseCode.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE"
            BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
            BillingResponseCode.ERROR -> "ERROR"
            BillingResponseCode.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED"
            BillingResponseCode.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED"
            else -> "UNKNOWN_$responseCode"
        }
    }
    
    // === Callback des achats ===
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        try {
            Log.d(TAG, "📦 CALLBACK onPurchasesUpdated reçu")
            Log.d(TAG, "📊 Code: ${billingResult.responseCode} (${getResponseCodeName(billingResult.responseCode)})")
            Log.d(TAG, "📝 Debug: ${billingResult.debugMessage}")
            Log.d(TAG, "🛒 Achats: ${purchases?.size ?: "null"}")
            Log.d(TAG, "🔌 Thread: ${Thread.currentThread().name}")
            
            // Analyser en détail le response code
            when (billingResult.responseCode) {
                BillingResponseCode.OK -> Log.d(TAG, "✅ Response: OK")
                BillingResponseCode.USER_CANCELED -> Log.d(TAG, "❌ Response: USER_CANCELED")
                BillingResponseCode.SERVICE_UNAVAILABLE -> Log.d(TAG, "❌ Response: SERVICE_UNAVAILABLE") 
                BillingResponseCode.BILLING_UNAVAILABLE -> Log.d(TAG, "❌ Response: BILLING_UNAVAILABLE")
                BillingResponseCode.ITEM_UNAVAILABLE -> Log.d(TAG, "❌ Response: ITEM_UNAVAILABLE")
                BillingResponseCode.DEVELOPER_ERROR -> Log.d(TAG, "❌ Response: DEVELOPER_ERROR")
                BillingResponseCode.ERROR -> Log.d(TAG, "❌ Response: ERROR (problème Google Play)")
                BillingResponseCode.ITEM_ALREADY_OWNED -> Log.d(TAG, "⚠️ Response: ITEM_ALREADY_OWNED")
                BillingResponseCode.ITEM_NOT_OWNED -> Log.d(TAG, "⚠️ Response: ITEM_NOT_OWNED")
                else -> Log.d(TAG, "❓ Response: CODE INCONNU ${billingResult.responseCode}")
            }
            
            // Gérer spécifiquement le code 6 (ERROR) qui cause le crash
            if (billingResult.responseCode == BillingResponseCode.ERROR) {
                Log.w(TAG, "⚠️ Code erreur 6 reçu - Gestion spéciale pour éviter le crash")
                Log.w(TAG, "📋 Détail erreur: ${billingResult.debugMessage}")
                _errorMessage.value = "Problème temporaire avec Google Play. Veuillez réessayer."
                _isLoading.value = false
                return
            }
            
            if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {
                Log.d(TAG, "✅ Achat mis à jour: ${purchases.size} achats")
                
                purchases.forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            Log.d(TAG, "🎉 Achat réussi: [PRODUCT_MASKED]")
                            handleSuccessfulPurchase(purchase)
                        }
                        Purchase.PurchaseState.PENDING -> {
                            Log.d(TAG, "⏳ Achat en attente: ${purchase.products}")
                            _errorMessage.value = "Achat en cours de traitement..."
                        }
                        else -> {
                            Log.w(TAG, "⚠️ État d'achat inattendu: ${purchase.purchaseState}")
                        }
                    }
                }
            } else {
                Log.e(TAG, "❌ Erreur achat code=${billingResult.responseCode}: ${billingResult.debugMessage}")
                handlePurchaseError(billingResult.responseCode, billingResult.debugMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception dans onPurchasesUpdated", e)
            _errorMessage.value = "Erreur inattendue lors de l'achat"
        } finally {
            _isLoading.value = false
        }
    }
    
    private fun handleSuccessfulPurchase(purchase: Purchase) {
        try {
            // 👤 Identifier le type de compte pour les logs
            val currentUser = auth.currentUser
            val accountType = if (currentUser?.isAnonymous == true) "Compte invité" else "Compte Google"
            Log.d(TAG, "🎯 Traitement achat pour $accountType - UID: [MASKED]")
            
            // Marquer immédiatement comme abonné pour éviter les états incohérents
            _isSubscribed.value = true
            
            // 🔥 CRITIQUE: Mettre à jour immédiatement l'AppState pour débloquer les catégories premium
            try {
                com.love2loveapp.AppDelegate.appState.updateUserSubscriptionStatus(true)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur mise à jour AppState après achat", e)
            }
            
            // 🎉 Afficher notification de succès d'achat
            notificationService.showSubscriptionPurchaseSuccessNotification()
            Log.d(TAG, "🎉 Notification succès d'achat affichée")
            
            // Validation Firebase en arrière-plan (sans bloquer l'UI)
            // Dans une coroutine pour éviter de bloquer le thread principal
            serviceScope.launch {
                try {
                    val isValid = validatePurchaseWithFirebase(purchase)
                    if (!isValid) {
                        Log.w(TAG, "⚠️ Validation Firebase échouée mais achat accepté localement")
                        // En cas d'échec de validation, on garde l'abonnement actif 
                        // pour éviter de frustrer l'utilisateur
                    }
                    
                    // Acknowledger l'achat si nécessaire
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur post-achat", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur traitement achat réussi", e)
        }
    }
    
    private fun handlePurchaseError(responseCode: Int, debugMessage: String?) {
        _errorMessage.value = when (responseCode) {
            BillingResponseCode.USER_CANCELED -> {
                // Ne pas afficher de message d'erreur pour les annulations
                // L'utilisateur revient simplement au paywall
                Log.d(TAG, "👋 Utilisateur a annulé l'achat - retour au paywall")
                null
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Si l'utilisateur possède déjà l'item, mettre à jour l'état
                _isSubscribed.value = true
                
                // 🔥 CRITIQUE: Mettre à jour aussi l'AppState pour débloquer les catégories premium
                try {
                    com.love2loveapp.AppDelegate.appState.updateUserSubscriptionStatus(true)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur mise à jour AppState pour abonnement existant", e)
                }
                
                "Vous êtes déjà abonné"
            }
            BillingResponseCode.SERVICE_UNAVAILABLE -> "Service indisponible, réessayez plus tard"
            BillingResponseCode.BILLING_UNAVAILABLE -> "Facturation non disponible sur cet appareil"
            BillingResponseCode.DEVELOPER_ERROR -> "Erreur de configuration (ID produit invalide)"
            BillingResponseCode.ERROR -> "Erreur de connexion, vérifiez votre connexion"
            BillingResponseCode.SERVICE_DISCONNECTED -> {
                // Reconnecter automatiquement
                connectToBillingService()
                "Connexion perdue, reconnexion en cours..."
            }
            else -> "Erreur lors de l'achat: $debugMessage"
        }
    }
    
    private suspend fun acknowledgePurchase(purchase: Purchase) {
        try {
            billingClient?.let { client ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                    
                val result = withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine<BillingResult> { continuation ->
                        client.acknowledgePurchase(params) { billingResult ->
                            continuation.resume(billingResult)
                        }
                    }
                }
                
                if (result.responseCode == BillingResponseCode.OK) {
                    Log.d(TAG, "✅ Achat acknowledgé avec succès")
                } else {
                    Log.w(TAG, "⚠️ Échec acknowledgement: ${result.debugMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur acknowledgement", e)
        }
    }
    
    // === Nettoyage ===
    fun cleanup() {
        Log.d(TAG, "🧹 Nettoyage du service billing")
        try {
            serviceScope.coroutineContext.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erreur annulation des coroutines", e)
        }
        billingClient?.endConnection()
        billingClient = null
    }
}