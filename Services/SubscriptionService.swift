import Foundation
import StoreKit
import Combine

class SubscriptionService: NSObject, ObservableObject, SKPaymentTransactionObserver {
    static let shared = SubscriptionService()
    
    @Published var products: [SKProduct] = []
    @Published var isSubscribed: Bool = false
    @Published var lastPurchasedProduct: SKProduct?
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    
    private let productIdentifiers: Set<String> = [
        "com.lyes.love2love.subscription.weekly"
    ]
    
    private var cancellables = Set<AnyCancellable>()
    
    override init() {
        super.init()
        SKPaymentQueue.default().add(self)
        loadProducts()
        checkSubscriptionStatus()
    }
    
    deinit {
        SKPaymentQueue.default().remove(self)
    }
    
    func loadProducts() {
        print("🔥 SubscriptionService: Début du chargement des produits")
        NSLog("🔥 SubscriptionService: Début du chargement des produits")
        print("🔥 SubscriptionService: Identifiants de produits: \(productIdentifiers)")
        NSLog("🔥 SubscriptionService: Identifiants de produits: \(productIdentifiers)")
        
        let request = SKProductsRequest(productIdentifiers: productIdentifiers)
        request.delegate = self
        request.start()
        
        print("🔥 SubscriptionService: Requête de produits lancée")
        NSLog("🔥 SubscriptionService: Requête de produits lancée")
    }
    
    func purchase(product: SKProduct) {
        print("🔥 SubscriptionService: Tentative d'achat du produit: \(product.productIdentifier)")
        NSLog("🔥 SubscriptionService: Tentative d'achat du produit: \(product.productIdentifier)")
        
        guard SKPaymentQueue.canMakePayments() else {
            print("🔥 SubscriptionService: ERREUR - Les achats ne sont pas autorisés sur cet appareil")
            NSLog("🔥 SubscriptionService: ERREUR - Les achats ne sont pas autorisés sur cet appareil")
            errorMessage = "Les achats ne sont pas autorisés sur cet appareil"
            return
        }
        
        print("🔥 SubscriptionService: Les achats sont autorisés, création du paiement...")
        NSLog("🔥 SubscriptionService: Les achats sont autorisés, création du paiement...")
        
        isLoading = true
        let payment = SKPayment(product: product)
        
        print("🔥 SubscriptionService: Ajout du paiement à la queue StoreKit...")
        NSLog("🔥 SubscriptionService: Ajout du paiement à la queue StoreKit...")
        
        SKPaymentQueue.default().add(payment)
        
        print("🔥 SubscriptionService: Paiement ajouté à la queue - en attente de la réponse Apple")
        NSLog("🔥 SubscriptionService: Paiement ajouté à la queue - en attente de la réponse Apple")
    }
    
    func restorePurchases() {
        print("🔥 SubscriptionService: Début de la restauration des achats")
        NSLog("🔥 SubscriptionService: Début de la restauration des achats")
        
        isLoading = true
        SKPaymentQueue.default().restoreCompletedTransactions()
        
        print("🔥 SubscriptionService: Commande de restauration envoyée")
        NSLog("🔥 SubscriptionService: Commande de restauration envoyée")
    }
    
    private func checkSubscriptionStatus() {
        // Vérifier le statut d'abonnement depuis Firebase
        if let user = FirebaseService.shared.currentUser {
            isSubscribed = user.isSubscribed
        }
    }
    
    // MARK: - SKPaymentTransactionObserver
    
    func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        print("🔥 SubscriptionService: Mise à jour des transactions - \(transactions.count) transaction(s)")
        NSLog("🔥 SubscriptionService: Mise à jour des transactions - \(transactions.count) transaction(s)")
        
        for transaction in transactions {
            print("🔥 SubscriptionService: Transaction \(transaction.transactionIdentifier ?? "unknown") - État: \(transaction.transactionState.rawValue)")
            NSLog("🔥 SubscriptionService: Transaction \(transaction.transactionIdentifier ?? "unknown") - État: \(transaction.transactionState.rawValue)")
            
            switch transaction.transactionState {
            case .purchased:
                print("🔥 SubscriptionService: Transaction PURCHASED")
                NSLog("🔥 SubscriptionService: Transaction PURCHASED")
                handlePurchased(transaction)
            case .restored:
                print("🔥 SubscriptionService: Transaction RESTORED")
                NSLog("🔥 SubscriptionService: Transaction RESTORED")
                handleRestored(transaction)
            case .failed:
                print("🔥 SubscriptionService: Transaction FAILED")
                NSLog("🔥 SubscriptionService: Transaction FAILED")
                handleFailed(transaction)
            case .deferred:
                print("🔥 SubscriptionService: Transaction DEFERRED")
                NSLog("🔥 SubscriptionService: Transaction DEFERRED")
            case .purchasing:
                print("🔥 SubscriptionService: Transaction PURCHASING - Sheet Apple devrait apparaître")
                NSLog("🔥 SubscriptionService: Transaction PURCHASING - Sheet Apple devrait apparaître")
            @unknown default:
                print("🔥 SubscriptionService: Transaction état inconnu: \(transaction.transactionState.rawValue)")
                NSLog("🔥 SubscriptionService: Transaction état inconnu: \(transaction.transactionState.rawValue)")
                break
            }
        }
    }
    
    private func handlePurchased(_ transaction: SKPaymentTransaction) {
        print("🔥 SubscriptionService: ✅ Achat réussi: \(transaction.payment.productIdentifier)")
        NSLog("🔥 SubscriptionService: ✅ Achat réussi: \(transaction.payment.productIdentifier)")
        
        // Marquer comme abonné
        isSubscribed = true
        isLoading = false
        
        // Mettre à jour Firebase
        FirebaseService.shared.updateSubscriptionStatus(isSubscribed: true)
        
        // Trouver le produit correspondant
        if let product = products.first(where: { $0.productIdentifier == transaction.payment.productIdentifier }) {
            lastPurchasedProduct = product
            print("🔥 SubscriptionService: Produit acheté: \(product.localizedTitle)")
            NSLog("🔥 SubscriptionService: Produit acheté: \(product.localizedTitle)")
        }
        
        // Finaliser la transaction
        SKPaymentQueue.default().finishTransaction(transaction)
        
        // Notifier le succès
        NotificationCenter.default.post(
            name: NSNotification.Name("SubscriptionPurchased"),
            object: nil
        )
        
        print("🔥 SubscriptionService: Transaction finalisée et notification envoyée")
        NSLog("🔥 SubscriptionService: Transaction finalisée et notification envoyée")
    }
    
    private func handleRestored(_ transaction: SKPaymentTransaction) {
        print("🔥 SubscriptionService: ✅ Achat restauré: \(transaction.payment.productIdentifier)")
        NSLog("🔥 SubscriptionService: ✅ Achat restauré: \(transaction.payment.productIdentifier)")
        
        isSubscribed = true
        isLoading = false
        
        // Mettre à jour Firebase
        FirebaseService.shared.updateSubscriptionStatus(isSubscribed: true)
        
        SKPaymentQueue.default().finishTransaction(transaction)
        
        // Notifier la restauration réussie
        NotificationCenter.default.post(
            name: NSNotification.Name("SubscriptionRestored"),
            object: nil
        )
        
        print("🔥 SubscriptionService: Restauration finalisée")
        NSLog("🔥 SubscriptionService: Restauration finalisée")
    }
    
    private func handleFailed(_ transaction: SKPaymentTransaction) {
        print("🔥 SubscriptionService: ❌ Transaction échouée")
        NSLog("🔥 SubscriptionService: ❌ Transaction échouée")
        
        isLoading = false
        
        if let error = transaction.error as? SKError {
            print("🔥 SubscriptionService: Code d'erreur SKError: \(error.code.rawValue)")
            NSLog("🔥 SubscriptionService: Code d'erreur SKError: \(error.code.rawValue)")
            
            if error.code != .paymentCancelled {
                print("🔥 SubscriptionService: ❌ Erreur d'achat: \(error.localizedDescription)")
                NSLog("🔥 SubscriptionService: ❌ Erreur d'achat: \(error.localizedDescription)")
                errorMessage = "Erreur d'achat: \(error.localizedDescription)"
            } else {
                print("🔥 SubscriptionService: Achat annulé par l'utilisateur")
                NSLog("🔥 SubscriptionService: Achat annulé par l'utilisateur")
            }
        } else if let error = transaction.error {
            print("🔥 SubscriptionService: ❌ Erreur générale: \(error.localizedDescription)")
            NSLog("🔥 SubscriptionService: ❌ Erreur générale: \(error.localizedDescription)")
        }
        
        SKPaymentQueue.default().finishTransaction(transaction)
        print("🔥 SubscriptionService: Transaction échouée finalisée")
        NSLog("🔥 SubscriptionService: Transaction échouée finalisée")
    }
    
    func paymentQueue(_ queue: SKPaymentQueue, restoreCompletedTransactionsFailedWithError error: Error) {
        print("🔥 SubscriptionService: ❌ Erreur de restauration: \(error.localizedDescription)")
        NSLog("🔥 SubscriptionService: ❌ Erreur de restauration: \(error.localizedDescription)")
        
        isLoading = false
        errorMessage = "Erreur de restauration: \(error.localizedDescription)"
    }
    
    func paymentQueueRestoreCompletedTransactionsFinished(_ queue: SKPaymentQueue) {
        print("🔥 SubscriptionService: ✅ Restauration terminée - \(queue.transactions.count) transaction(s)")
        NSLog("🔥 SubscriptionService: ✅ Restauration terminée - \(queue.transactions.count) transaction(s)")
        
        isLoading = false
        if queue.transactions.isEmpty {
            errorMessage = "Aucun achat à restaurer trouvé"
            print("🔥 SubscriptionService: Aucun achat à restaurer")
            NSLog("🔥 SubscriptionService: Aucun achat à restaurer")
        }
    }
}

// MARK: - SKProductsRequestDelegate

extension SubscriptionService: SKProductsRequestDelegate {
    func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
        print("🔥 SubscriptionService: ✅ Réponse des produits reçue")
        NSLog("🔥 SubscriptionService: ✅ Réponse des produits reçue")
        print("🔥 SubscriptionService: Produits valides: \(response.products.count)")
        NSLog("🔥 SubscriptionService: Produits valides: \(response.products.count)")
        print("🔥 SubscriptionService: Identifiants invalides: \(response.invalidProductIdentifiers.count)")
        NSLog("🔥 SubscriptionService: Identifiants invalides: \(response.invalidProductIdentifiers.count)")
        
        if !response.invalidProductIdentifiers.isEmpty {
            print("🔥 SubscriptionService: ❌ Identifiants invalides: \(response.invalidProductIdentifiers)")
            NSLog("🔥 SubscriptionService: ❌ Identifiants invalides: \(response.invalidProductIdentifiers)")
        }
        
        DispatchQueue.main.async {
            self.products = response.products
            print("🔥 SubscriptionService: ✅ Produits chargés: \(self.products.count)")
            NSLog("🔥 SubscriptionService: ✅ Produits chargés: \(self.products.count)")
            
            for product in self.products {
                print("🔥 SubscriptionService: Produit: \(product.localizedTitle) - \(product.priceLocale.currencySymbol ?? "")\(product.price)")
                NSLog("🔥 SubscriptionService: Produit: \(product.localizedTitle) - \(product.priceLocale.currencySymbol ?? "")\(product.price)")
                print("🔥 SubscriptionService: ID: \(product.productIdentifier)")
                NSLog("🔥 SubscriptionService: ID: \(product.productIdentifier)")
                print("🔥 SubscriptionService: Description: \(product.localizedDescription)")
                NSLog("🔥 SubscriptionService: Description: \(product.localizedDescription)")
            }
            
            if self.products.isEmpty {
                print("🔥 SubscriptionService: ❌ AUCUN PRODUIT CHARGÉ - Vérifiez App Store Connect")
                NSLog("🔥 SubscriptionService: ❌ AUCUN PRODUIT CHARGÉ - Vérifiez App Store Connect")
                self.errorMessage = "Aucun produit disponible. Vérifiez votre connexion."
            }
        }
    }
    
    func request(_ request: SKRequest, didFailWithError error: Error) {
        print("🔥 SubscriptionService: ❌ Erreur lors du chargement des produits: \(error.localizedDescription)")
        NSLog("🔥 SubscriptionService: ❌ Erreur lors du chargement des produits: \(error.localizedDescription)")
        
        DispatchQueue.main.async {
            self.errorMessage = "Erreur lors du chargement des produits: \(error.localizedDescription)"
        }
    }
} 