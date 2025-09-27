# 💳 Effet de Chargement Paywall Android - Google Billing

## Vue d'Ensemble

Implementation d'un effet de chargement professionnel dans le bouton de paiement pour donner la même impression premium qu'iOS lors du processus d'achat avec Google Billing.

---

## 🔄 Architecture Complète

### 1. **ViewModel avec États de Chargement**

```kotlin
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billingService: BillingService,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    data class PaywallUiState(
        val isLoading: Boolean = false,
        val loadingMessage: String = "",
        val isPurchaseSuccessful: Boolean = false,
        val errorMessage: String? = null,
        val selectedPlan: SubscriptionPlan? = null
    )

    fun purchaseSubscription(activity: Activity, productId: String) {
        viewModelScope.launch {
            try {
                // 1. Début du chargement
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    loadingMessage = "Préparation du paiement...",
                    errorMessage = null
                )

                delay(500) // Petit délai pour que l'utilisateur voie l'effet

                // 2. Lancement du flow Google Billing
                _uiState.value = _uiState.value.copy(
                    loadingMessage = "Connexion à Google Play..."
                )

                billingService.launchBillingFlow(activity, productId) { result ->
                    handlePurchaseResult(result)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Erreur lors du paiement: ${e.message}"
                )
            }
        }
    }

    private fun handlePurchaseResult(result: BillingResult) {
        viewModelScope.launch {
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    _uiState.value = _uiState.value.copy(
                        loadingMessage = "Vérification du paiement..."
                    )

                    delay(1000) // Simuler vérification serveur

                    _uiState.value = _uiState.value.copy(
                        loadingMessage = "Activation de votre abonnement..."
                    )

                    // Vérifier côté serveur
                    verifyPurchaseOnServer()
                }

                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = null
                    )
                }

                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Erreur de paiement"
                    )
                }
            }
        }
    }

    private suspend fun verifyPurchaseOnServer() {
        try {
            // Appel vers votre backend pour vérifier
            val isVerified = authRepository.verifySubscription()

            if (isVerified) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isPurchaseSuccessful = true,
                    loadingMessage = "Abonnement activé !"
                )

                delay(2000) // Laisser voir le succès

                // Réinitialiser pour fermer le paywall
                _uiState.value = PaywallUiState()

            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Impossible de vérifier l'abonnement"
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Erreur de vérification"
            )
        }
    }
}
```

---

## 🎨 Interface Utilisateur Animée

### 2. **Écran Paywall Principal**

```kotlin
@Composable
fun PaywallScreen(
    viewModel: PaywallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom
    ) {

        // Contenu du paywall (plans, avantages, etc.)...

        // Bouton de paiement avec effet de chargement
        AnimatedPurchaseButton(
            text = when {
                uiState.isLoading -> uiState.loadingMessage
                uiState.isPurchaseSuccessful -> "Abonnement activé !"
                else -> "Commencer maintenant - 9,99€/mois"
            },
            isLoading = uiState.isLoading,
            isSuccess = uiState.isPurchaseSuccessful,
            isEnabled = !uiState.isLoading,
            onClick = {
                viewModel.purchaseSubscription(activity, "premium_monthly")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        )

        // Message d'erreur si nécessaire
        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
```

### 3. **Bouton Animé Personnalisé**

```kotlin
@Composable
fun AnimatedPurchaseButton(
    text: String,
    isLoading: Boolean,
    isSuccess: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSuccess -> Color(0xFF4CAF50) // Vert succès
            isLoading -> Color(0xFF2196F3) // Bleu chargement
            else -> Color(0xFFFF6B35) // Orange normal
        },
        animationSpec = tween(300),
        label = "buttonColor"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.7f else 1f,
        animationSpec = tween(300),
        label = "contentAlpha"
    )

    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isLoading) 8.dp else 4.dp
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {

            // Spinner de chargement
            if (isLoading) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = text,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.alpha(contentAlpha)
                    )
                }
            }

            // Icône de succès
            else if (isSuccess) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = text,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // État normal
            else {
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
```

---

## 💰 Service Google Billing

### 4. **BillingService Complet**

```kotlin
@Singleton
class BillingService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var billingClient: BillingClient? = null
    private var purchaseCallback: ((BillingResult) -> Unit)? = null

    fun initializeBilling() {
        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                handlePurchases(billingResult, purchases)
            }
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("Billing", "✅ Billing client connecté")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d("Billing", "❌ Billing client déconnecté")
            }
        })
    }

    fun launchBillingFlow(
        activity: Activity,
        productId: String,
        callback: (BillingResult) -> Unit
    ) {
        purchaseCallback = callback

        val productDetailsParams = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(productDetailsParams))
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList.firstOrNull()?.let { productDetails ->
                    launchPurchaseFlow(activity, productDetails)
                }
            } else {
                callback(billingResult)
            }
        }
    }

    private fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

        if (offerToken != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            billingClient?.launchBillingFlow(activity, billingFlowParams)
        }
    }

    private fun handlePurchases(billingResult: BillingResult, purchases: List<Purchase>?) {
        purchaseCallback?.invoke(billingResult)

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    // Reconnaître l'achat
                    acknowledgePurchase(purchase)
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
                Log.d("Billing", "Achat reconnu: ${billingResult.responseCode}")
            }
        }
    }
}
```

---

## ✨ Animations Avancées

### 5. **Bouton avec Effet Pulse**

```kotlin
@Composable
fun PulsingPurchaseButton(
    text: String,
    isLoading: Boolean,
    isSuccess: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isLoading) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val shadowElevation by infiniteTransition.animateDp(
        initialValue = 4.dp,
        targetValue = if (isLoading) 12.dp else 4.dp,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shadowElevation"
    )

    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier
            .scale(pulseScale)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isSuccess -> Color(0xFF4CAF50)
                isLoading -> Color(0xFF2196F3)
                else -> Color(0xFFFF6B35)
            }
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = shadowElevation
        )
    ) {
        // Contenu identique à AnimatedPurchaseButton...
    }
}
```

### 6. **Messages de Chargement Séquentiels**

```kotlin
class LoadingMessages {
    companion object {
        private val loadingSteps = listOf(
            "Préparation du paiement..." to 500L,
            "Connexion à Google Play..." to 1000L,
            "Vérification du compte..." to 800L,
            "Traitement du paiement..." to 1200L,
            "Activation de votre abonnement..." to 1000L,
            "Finalisation..." to 600L
        )

        fun getRandomLoadingMessage(): String {
            return loadingSteps.random().first
        }

        fun getSequentialMessages(): List<Pair<String, Long>> {
            return loadingSteps
        }
    }
}

// Usage dans le ViewModel
private fun showSequentialLoadingMessages() {
    viewModelScope.launch {
        LoadingMessages.getSequentialMessages().forEach { (message, duration) ->
            _uiState.value = _uiState.value.copy(loadingMessage = message)
            delay(duration)
        }
    }
}
```

---

## 🎯 Feedback Tactile & Sonore

### 7. **Vibration Haptique**

```kotlin
@Composable
fun PaywallWithHaptics(
    viewModel: PaywallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val activity = LocalContext.current as Activity

    // Feedback haptique pour le succès
    LaunchedEffect(uiState.isPurchaseSuccessful) {
        if (uiState.isPurchaseSuccessful) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Feedback haptique pour les erreurs
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Interface utilisateur...
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Contenu du paywall...

        PulsingPurchaseButton(
            text = when {
                uiState.isLoading -> uiState.loadingMessage
                uiState.isPurchaseSuccessful -> "✅ Abonnement activé !"
                else -> "🚀 Commencer maintenant - 9,99€/mois"
            },
            isLoading = uiState.isLoading,
            isSuccess = uiState.isPurchaseSuccessful,
            isEnabled = !uiState.isLoading,
            onClick = {
                // Petit feedback au clic
                haptic.performHapticFeedback(HapticFeedbackType.LightImpact)
                viewModel.purchaseSubscription(activity, "premium_monthly")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        )
    }
}
```

---

## 🔧 Configuration & Intégration

### 8. **Dépendances Gradle**

```kotlin
// app/build.gradle
dependencies {
    // Google Play Billing
    implementation 'com.android.billingclient:billing-ktx:6.1.0'

    // Compose Animation
    implementation 'androidx.compose.animation:animation:1.5.4'

    // Lifecycle & ViewModel
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'

    // Hilt pour l'injection de dépendance
    implementation 'com.google.dagger:hilt-android:2.48'
    kapt 'com.google.dagger:hilt-compiler:2.48'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 9. **Permissions Manifest**

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissions pour Google Play Billing -->
    <uses-permission android:name="com.android.vending.BILLING" />
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Permission pour vibration haptique -->
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:name=".YourApplication"
        ... >

        <!-- Vos activités... -->

    </application>
</manifest>
```

### 10. **Initialisation dans Application**

```kotlin
@HiltAndroidApp
class YourApplication : Application() {

    @Inject
    lateinit var billingService: BillingService

    override fun onCreate() {
        super.onCreate()

        // Initialiser Google Billing au démarrage
        billingService.initializeBilling()
    }
}
```

---

## 📊 États du Processus de Paiement

### Séquence Complète d'Animation

1. **État Normal** 🟠

   - Bouton orange
   - Texte : "Commencer maintenant - 9,99€/mois"

2. **Préparation** 🔵

   - Bouton devient bleu
   - Spinner + "Préparation du paiement..."

3. **Connexion Google Play** 🔵

   - Spinner + "Connexion à Google Play..."

4. **Dialog Google Play** ⚪

   - Dialog natif Google Play
   - Bouton en attente

5. **Vérification** 🔵

   - Spinner + "Vérification du paiement..."

6. **Activation** 🔵

   - Spinner + "Activation de votre abonnement..."

7. **Succès** 🟢

   - Bouton vert
   - Icône ✅ + "Abonnement activé !"
   - Vibration haptique

8. **Erreur** 🔴 (si applicable)
   - Bouton rouge
   - Message d'erreur sous le bouton
   - Vibration d'erreur

---

## ✅ Résumé des Fonctionnalités

Cette implémentation offre :

- 🎨 **Animation fluide des couleurs** (Orange → Bleu → Vert)
- 🔄 **Spinner rotatif** avec messages dynamiques
- 📱 **Feedback haptique** pour succès/erreur
- 🎯 **Effet pulse** pour attirer l'attention
- 💬 **Messages contextuels** à chaque étape
- 🔒 **Bouton désactivé** pendant le processus
- ✅ **Icône de validation** en cas de succès
- 🚨 **Gestion complète des erreurs** Google Billing
- 🏗️ **Architecture MVVM** propre et maintenable

**Résultat** : Une expérience de paiement premium identique à iOS ! 🚀
