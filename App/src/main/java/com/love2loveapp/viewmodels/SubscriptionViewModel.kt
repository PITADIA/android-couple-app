package com.love2loveapp.viewmodels

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.love2loveapp.services.billing.GooglePlayBillingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel pour la gestion des abonnements - Équivalent SubscriptionService iOS
 * 
 * Responsabilités :
 * - Gestion centralisée de l'état des abonnements
 * - Interface entre UI et GooglePlayBillingService
 * - Synchronisation avec l'état global de l'app
 */
class SubscriptionViewModel(
    private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "SubscriptionViewModel"
    }
    
    // Service de billing
    private val billingService = GooglePlayBillingService.getInstance(context)
    
    // États exposés
    private val _subscriptionState = MutableStateFlow(SubscriptionState())
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()
    
    data class SubscriptionState(
        val isSubscribed: Boolean = false,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val selectedPlan: GooglePlayBillingService.SubscriptionPlanType = GooglePlayBillingService.SubscriptionPlanType.MONTHLY,
        val isReady: Boolean = false,
        val productPrices: Map<String, String> = emptyMap()
    )
    
    init {
        Log.d(TAG, "🚀 SubscriptionViewModel initialisé")
        observeBillingService()
    }
    
    /**
     * Observer les changements du service de billing
     */
    private fun observeBillingService() {
        viewModelScope.launch {
            // Observer l'état d'abonnement
            billingService.isSubscribed.collect { isSubscribed ->
                Log.d(TAG, "📊 État abonnement changé: $isSubscribed")
                _subscriptionState.value = _subscriptionState.value.copy(
                    isSubscribed = isSubscribed
                )
            }
        }
        
        viewModelScope.launch {
            // Observer l'état de chargement
            billingService.isLoading.collect { isLoading ->
                _subscriptionState.value = _subscriptionState.value.copy(
                    isLoading = isLoading
                )
            }
        }
        
        viewModelScope.launch {
            // Observer les erreurs
            billingService.errorMessage.collect { errorMessage ->
                _subscriptionState.value = _subscriptionState.value.copy(
                    errorMessage = errorMessage
                )
            }
        }
        
        viewModelScope.launch {
            // Observer le plan sélectionné
            billingService.selectedPlan.collect { selectedPlan ->
                _subscriptionState.value = _subscriptionState.value.copy(
                    selectedPlan = selectedPlan
                )
            }
        }
        
        viewModelScope.launch {
            // Observer l'état de connexion billing
            billingService.billingConnectionState.collect { connectionState ->
                val isReady = connectionState == GooglePlayBillingService.BillingConnectionState.CONNECTED
                _subscriptionState.value = _subscriptionState.value.copy(
                    isReady = isReady
                )
            }
        }
        
        viewModelScope.launch {
            // Observer les détails produits pour les prix
            billingService.productDetails.collect { productDetails ->
                val prices = productDetails.mapValues { (planType, price) ->
                    price
                }.mapKeys { (planType, _) -> planType.productId }
                
                _subscriptionState.value = _subscriptionState.value.copy(
                    productPrices = prices
                )
            }
        }
    }
    
    /**
     * Démarrer un achat d'abonnement
     */
    fun purchaseSubscription(activity: Activity) {
        Log.d(TAG, "🛒 Démarrage achat via ViewModel")
        viewModelScope.launch {
            billingService.purchaseSubscription(activity)
        }
    }
    
    /**
     * Sélectionner un plan d'abonnement
     */
    fun selectPlan(plan: GooglePlayBillingService.SubscriptionPlanType) {
        Log.d(TAG, "📋 Sélection plan via ViewModel: $plan")
        billingService.selectPlan(plan)
    }
    
    /**
     * Restaurer les achats
     */
    fun restorePurchases() {
        Log.d(TAG, "🔄 Restauration achats via ViewModel")
        viewModelScope.launch {
            billingService.restorePurchases()
        }
    }
    
    /**
     * Effacer l'erreur actuelle
     */
    fun clearError() {
        Log.d(TAG, "🧹 Effacement erreur via ViewModel")
        billingService.clearError()
    }
    
    /**
     * Obtenir le prix formaté pour un produit
     */
    fun getFormattedPrice(productId: String): String {
        return _subscriptionState.value.productPrices[productId] ?: "Prix non disponible"
    }
    
    /**
     * Vérifier si un abonnement est actif
     */
    fun hasActiveSubscription(): Boolean {
        return _subscriptionState.value.isSubscribed
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 SubscriptionViewModel nettoyé")
        billingService.cleanup()
    }
}
