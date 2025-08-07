import Foundation
import StoreKit
import FirebaseFunctions
import FirebaseAnalytics

class AppleReceiptService: NSObject, ObservableObject {
    static let shared = AppleReceiptService()
    
    @Published var isSubscribed: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var selectedPlan: SubscriptionPlanType = .monthly // Défaut: mensuel
    
    private let functions = Functions.functions()
    private let productIdentifiers = [
        SubscriptionPlanType.weekly.rawValue,
        SubscriptionPlanType.monthly.rawValue
    ]
    
    override init() {
        super.init()
        SKPaymentQueue.default().add(self)
        checkSubscriptionStatus()
    }
    
    deinit {
        SKPaymentQueue.default().remove(self)
    }
    
    // MARK: - Public Methods
    
    func purchaseSubscription() {
        print("🔥 AppleReceiptService: Début de l'achat d'abonnement")
        NSLog("🔥 AppleReceiptService: Début de l'achat d'abonnement")
        print("🔥 AppleReceiptService: Plan sélectionné: \(selectedPlan.rawValue)")
        NSLog("🔥 AppleReceiptService: Plan sélectionné: \(selectedPlan.rawValue)")
        
        guard SKPaymentQueue.canMakePayments() else {
            print("🔥 AppleReceiptService: Les achats ne sont pas autorisés")
            NSLog("🔥 AppleReceiptService: Les achats ne sont pas autorisés")
            errorMessage = NSLocalizedString("purchases_not_authorized", comment: "Purchases not authorized error")
            return
        }
        
        // Créer une requête de produit avec tous les identifiants
        let productRequest = SKProductsRequest(productIdentifiers: Set(productIdentifiers))
        productRequest.delegate = self
        productRequest.start()
        
        isLoading = true
        print("🔥 AppleReceiptService: Requête de produits lancée")
        NSLog("🔥 AppleReceiptService: Requête de produits lancée")
    }
    
    func restorePurchases() {
        print("🔥 AppleReceiptService: Restauration des achats")
        NSLog("🔥 AppleReceiptService: Restauration des achats")
        
        isLoading = true
        SKPaymentQueue.default().restoreCompletedTransactions()
    }
    
    func checkSubscriptionStatus() {
        print("🔥 AppleReceiptService: Vérification du statut d'abonnement")
        NSLog("🔥 AppleReceiptService: Vérification du statut d'abonnement")
        
        let checkStatus = functions.httpsCallable("checkSubscriptionStatus")
        
        checkStatus.call { [weak self] result, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("🔥 AppleReceiptService: Erreur lors de la vérification: \(error.localizedDescription)")
                    NSLog("🔥 AppleReceiptService: Erreur lors de la vérification: \(error.localizedDescription)")
                    return
                }
                
                if let data = result?.data as? [String: Any],
                   let isSubscribed = data["isSubscribed"] as? Bool {
                    print("🔥 AppleReceiptService: Statut d'abonnement: \(isSubscribed)")
                    NSLog("🔥 AppleReceiptService: Statut d'abonnement: \(isSubscribed)")
                    self?.isSubscribed = isSubscribed
                }
            }
        }
    }
    
    // MARK: - Private Methods
    
    private func validateReceiptWithFirebase() {
        print("🔥 AppleReceiptService: Début de la validation du reçu")
        NSLog("🔥 AppleReceiptService: Début de la validation du reçu")
        
        guard let receiptURL = Bundle.main.appStoreReceiptURL,
              let receiptData = try? Data(contentsOf: receiptURL) else {
            print("🔥 AppleReceiptService: Impossible de lire le reçu local")
            NSLog("🔥 AppleReceiptService: Impossible de lire le reçu local")
                            errorMessage = NSLocalizedString("cannot_read_receipt", comment: "Cannot read receipt error")
            isLoading = false
            return
        }
        
        let receiptString = receiptData.base64EncodedString()
        print("🔥 AppleReceiptService: Reçu encodé en base64 (longueur: \(receiptString.count))")
        NSLog("🔥 AppleReceiptService: Reçu encodé en base64 (longueur: \(receiptString.count))")
        
        let validateReceipt = functions.httpsCallable("validateAppleReceipt")
        
        validateReceipt.call([
            "receiptData": receiptString,
            "productId": selectedPlan.rawValue
        ]) { [weak self] result, error in
            DispatchQueue.main.async {
                self?.isLoading = false
                
                if let error = error {
                    print("🔥 AppleReceiptService: Erreur de validation: \(error.localizedDescription)")
                    NSLog("🔥 AppleReceiptService: Erreur de validation: \(error.localizedDescription)")
                    self?.errorMessage = "Erreur de validation: \(error.localizedDescription)"
                    return
                }
                
                if let data = result?.data as? [String: Any],
                   let success = data["success"] as? Bool, success {
                    print("🔥 AppleReceiptService: ✅ Validation réussie!")
                    NSLog("🔥 AppleReceiptService: ✅ Validation réussie!")
                    self?.isSubscribed = true
                    self?.errorMessage = nil
                    
                    // Notifier le succès
                    NotificationCenter.default.post(
                        name: NSNotification.Name("SubscriptionValidated"),
                        object: nil
                    )
                } else {
                    print("🔥 AppleReceiptService: ❌ Validation échouée")
                    NSLog("🔥 AppleReceiptService: ❌ Validation échouée")
                    self?.errorMessage = NSLocalizedString("receipt_validation_failed", comment: "Receipt validation failed error")
                }
            }
        }
    }
}

// MARK: - SKProductsRequestDelegate

extension AppleReceiptService: SKProductsRequestDelegate {
    func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
        print("🔥 AppleReceiptService: Produits reçus: \(response.products.count)")
        
        if !response.invalidProductIdentifiers.isEmpty {
            print("❌ AppleReceiptService: IDs invalides: \(response.invalidProductIdentifiers)")
        }
        
        // Log simplifié des produits trouvés
        if !response.products.isEmpty {
            for product in response.products {
                print("🔥 AppleReceiptService: Produit: \(product.productIdentifier)")
            }
        }
        
        DispatchQueue.main.async {
            // Chercher le produit correspondant au plan sélectionné
            if let product = response.products.first(where: { $0.productIdentifier == self.selectedPlan.rawValue }) {
                print("🔥 AppleReceiptService: Lancement de l'achat pour: \(product.productIdentifier)")
                NSLog("🔥 AppleReceiptService: Lancement de l'achat pour: \(product.productIdentifier)")
                
                let payment = SKPayment(product: product)
                SKPaymentQueue.default().add(payment)
            } else {
                print("🔥 AppleReceiptService: Produit non trouvé pour le plan: \(self.selectedPlan.rawValue)")
                NSLog("🔥 AppleReceiptService: Produit non trouvé pour le plan: \(self.selectedPlan.rawValue)")
                self.errorMessage = NSLocalizedString("product_not_available", comment: "Product not available error")
                self.isLoading = false
            }
        }
    }
    
    func request(_ request: SKRequest, didFailWithError error: Error) {
        print("❌ AppleReceiptService: Erreur requête: \(error.localizedDescription)")
        
        DispatchQueue.main.async {
            self.errorMessage = NSLocalizedString("product_loading_error", comment: "Product loading error")
            self.isLoading = false
        }
    }
}

// MARK: - SKPaymentTransactionObserver

extension AppleReceiptService: SKPaymentTransactionObserver {
    func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        print("🔥 AppleReceiptService: Transactions mises à jour: \(transactions.count)")
        NSLog("🔥 AppleReceiptService: Transactions mises à jour: \(transactions.count)")
        
        for transaction in transactions {
            print("🔥 AppleReceiptService: Transaction état: \(transaction.transactionState.rawValue)")
            NSLog("🔥 AppleReceiptService: Transaction état: \(transaction.transactionState.rawValue)")
            
            switch transaction.transactionState {
            case .purchased:
                print("🔥 AppleReceiptService: ✅ Achat réussi!")
                NSLog("🔥 AppleReceiptService: ✅ Achat réussi!")
                handlePurchased(transaction)
                
            case .restored:
                print("🔥 AppleReceiptService: ✅ Achat restauré!")
                NSLog("🔥 AppleReceiptService: ✅ Achat restauré!")
                handleRestored(transaction)
                
            case .failed:
                print("🔥 AppleReceiptService: ❌ Achat échoué")
                NSLog("🔥 AppleReceiptService: ❌ Achat échoué")
                handleFailed(transaction)
                
            case .purchasing:
                print("🔥 AppleReceiptService: 🔄 Achat en cours...")
                NSLog("🔥 AppleReceiptService: 🔄 Achat en cours...")
                // S'assurer que le chargement reste actif pendant l'achat
                DispatchQueue.main.async {
                    self.isLoading = true
                }
                
            case .deferred:
                print("🔥 AppleReceiptService: ⏳ Achat différé")
                NSLog("🔥 AppleReceiptService: ⏳ Achat différé")
                
            @unknown default:
                print("🔥 AppleReceiptService: État inconnu: \(transaction.transactionState.rawValue)")
                NSLog("🔥 AppleReceiptService: État inconnu: \(transaction.transactionState.rawValue)")
            }
        }
    }
    
    private func handlePurchased(_ transaction: SKPaymentTransaction) {
        // Finaliser la transaction
        SKPaymentQueue.default().finishTransaction(transaction)
        
        // Valider le reçu avec Firebase (l'utilisateur est maintenant authentifié)
        validateReceiptWithFirebase()
    }
    
    private func handleRestored(_ transaction: SKPaymentTransaction) {
        // Finaliser la transaction
        SKPaymentQueue.default().finishTransaction(transaction)
        
        print("🔥 AppleReceiptService: Transaction restaurée: \(transaction.payment.productIdentifier)")
        NSLog("🔥 AppleReceiptService: Transaction restaurée: \(transaction.payment.productIdentifier)")
        
        // Ne pas valider avec Firebase ici car paymentQueueRestoreCompletedTransactionsFinished
        // sera appelé à la fin et gérera la validation
    }
    
    private func handleFailed(_ transaction: SKPaymentTransaction) {
        DispatchQueue.main.async {
            self.isLoading = false
            
            if let error = transaction.error as? SKError {
                if error.code != .paymentCancelled {
                    print("🔥 AppleReceiptService: Erreur: \(error.localizedDescription)")
                    NSLog("🔥 AppleReceiptService: Erreur: \(error.localizedDescription)")
                    self.errorMessage = error.localizedDescription
                } else {
                    print("🔥 AppleReceiptService: Achat annulé par l'utilisateur")
                    NSLog("🔥 AppleReceiptService: Achat annulé par l'utilisateur")
                }
            }
        }
        
        SKPaymentQueue.default().finishTransaction(transaction)
    }
    
    func paymentQueueRestoreCompletedTransactionsFinished(_ queue: SKPaymentQueue) {
        print("🔥 AppleReceiptService: Restauration terminée")
        NSLog("🔥 AppleReceiptService: Restauration terminée")
        
        DispatchQueue.main.async {
            self.isLoading = false
            if queue.transactions.isEmpty {
                self.errorMessage = NSLocalizedString("no_purchase_to_restore", comment: "No purchase to restore error")
            } else {
                print("🔥 AppleReceiptService: Transactions restaurées, vérification du statut d'onboarding")
                
                // Vérifier si on est en cours d'onboarding
                // Si oui, marquer simplement comme abonné localement sans validation Firebase
                // La validation complète se fera après l'onboarding
                
                // Pour l'instant, marquer comme abonné pour permettre la navigation
                self.isSubscribed = true
                self.errorMessage = nil
                
                // 📊 Analytics: Achat restauré
                Analytics.logEvent("achat_restaure", parameters: [:])
                print("📊 Événement Firebase: achat_restaure")
                
                print("🔥 AppleReceiptService: ✅ Abonnement restauré avec succès")
                NSLog("🔥 AppleReceiptService: ✅ Abonnement restauré avec succès")
                
                // Notifier le succès
                NotificationCenter.default.post(
                    name: NSNotification.Name("SubscriptionValidated"),
                    object: nil
                )
            }
        }
    }
    
    func paymentQueue(_ queue: SKPaymentQueue, restoreCompletedTransactionsFailedWithError error: Error) {
        print("🔥 AppleReceiptService: Erreur de restauration: \(error.localizedDescription)")
        NSLog("🔥 AppleReceiptService: Erreur de restauration: \(error.localizedDescription)")
        
        DispatchQueue.main.async {
            self.isLoading = false
            self.errorMessage = "Erreur de restauration: \(error.localizedDescription)"
        }
    }
} 
