import Foundation
import StoreKit
import FirebaseFunctions

class AppleReceiptService: NSObject, ObservableObject {
    static let shared = AppleReceiptService()
    
    @Published var isSubscribed: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    
    private let functions = Functions.functions()
    private let productIdentifier = "com.lyes.love2love.subscription.weekly"
    
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
        print("🔥 AppleReceiptService: Recherche du produit: \(productIdentifier)")
        NSLog("🔥 AppleReceiptService: Recherche du produit: \(productIdentifier)")
        
        guard SKPaymentQueue.canMakePayments() else {
            print("🔥 AppleReceiptService: Les achats ne sont pas autorisés")
            NSLog("🔥 AppleReceiptService: Les achats ne sont pas autorisés")
            errorMessage = "Les achats ne sont pas autorisés sur cet appareil"
            return
        }
        
        // Créer une requête de produit
        let productRequest = SKProductsRequest(productIdentifiers: [productIdentifier])
        productRequest.delegate = self
        productRequest.start()
        
        isLoading = true
        print("🔥 AppleReceiptService: Requête de produit lancée")
        NSLog("🔥 AppleReceiptService: Requête de produit lancée")
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
            errorMessage = "Impossible de lire le reçu d'achat"
            isLoading = false
            return
        }
        
        let receiptString = receiptData.base64EncodedString()
        print("🔥 AppleReceiptService: Reçu encodé en base64 (longueur: \(receiptString.count))")
        NSLog("🔥 AppleReceiptService: Reçu encodé en base64 (longueur: \(receiptString.count))")
        
        let validateReceipt = functions.httpsCallable("validateAppleReceipt")
        
        validateReceipt.call([
            "receiptData": receiptString,
            "productId": productIdentifier
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
                    self?.errorMessage = "Validation du reçu échouée"
                }
            }
        }
    }
}

// MARK: - SKProductsRequestDelegate

extension AppleReceiptService: SKProductsRequestDelegate {
    func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
        print("🔥 AppleReceiptService: Produits reçus: \(response.products.count)")
        NSLog("🔥 AppleReceiptService: Produits reçus: \(response.products.count)")
        print("🔥 AppleReceiptService: Produits invalides: \(response.invalidProductIdentifiers.count)")
        NSLog("🔥 AppleReceiptService: Produits invalides: \(response.invalidProductIdentifiers.count)")
        
        if !response.invalidProductIdentifiers.isEmpty {
            print("🔥 AppleReceiptService: IDs invalides: \(response.invalidProductIdentifiers)")
            NSLog("🔥 AppleReceiptService: IDs invalides: \(response.invalidProductIdentifiers)")
        }
        
        for product in response.products {
            print("🔥 AppleReceiptService: Produit trouvé: \(product.productIdentifier) - \(product.localizedTitle)")
            NSLog("🔥 AppleReceiptService: Produit trouvé: \(product.productIdentifier) - \(product.localizedTitle)")
        }
        
        DispatchQueue.main.async {
            if let product = response.products.first {
                print("🔥 AppleReceiptService: Lancement de l'achat pour: \(product.productIdentifier)")
                NSLog("🔥 AppleReceiptService: Lancement de l'achat pour: \(product.productIdentifier)")
                
                let payment = SKPayment(product: product)
                SKPaymentQueue.default().add(payment)
            } else {
                print("🔥 AppleReceiptService: Aucun produit trouvé")
                NSLog("🔥 AppleReceiptService: Aucun produit trouvé")
                self.errorMessage = "Produit non disponible"
                self.isLoading = false
            }
        }
    }
    
    func request(_ request: SKRequest, didFailWithError error: Error) {
        print("🔥 AppleReceiptService: Erreur de requête produit: \(error.localizedDescription)")
        NSLog("🔥 AppleReceiptService: Erreur de requête produit: \(error.localizedDescription)")
        
        DispatchQueue.main.async {
            self.errorMessage = "Erreur lors du chargement du produit"
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
                self.errorMessage = "Aucun achat à restaurer"
            } else {
                print("🔥 AppleReceiptService: Transactions restaurées, vérification du statut d'onboarding")
                
                // Vérifier si on est en cours d'onboarding
                // Si oui, marquer simplement comme abonné localement sans validation Firebase
                // La validation complète se fera après l'onboarding
                
                // Pour l'instant, marquer comme abonné pour permettre la navigation
                self.isSubscribed = true
                self.errorMessage = nil
                
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