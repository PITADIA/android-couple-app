import Foundation
import StoreKit
import Combine
import FirebaseFirestore
import FirebaseAuth

class SubscriptionService: NSObject, ObservableObject, SKPaymentTransactionObserver {
    static let shared = SubscriptionService()
    
    @Published var products: [SKProduct] = []
    @Published var isSubscribed: Bool = false
    @Published var lastPurchasedProduct: SKProduct?
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    
    private let productIdentifiers: Set<String> = [
        "com.lyes.love2love.subscription.weekly",
        "com.lyes.love2love.subscription.monthly"
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
                handleSuccessfulPurchase(transaction)
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
    
    private func handleSuccessfulPurchase(_ transaction: SKPaymentTransaction) {
        print("🔥 SubscriptionService: ✅ Achat réussi: \(transaction.payment.productIdentifier)")
        NSLog("🔥 SubscriptionService: ✅ Achat réussi: \(transaction.payment.productIdentifier)")
        
        isSubscribed = true
        isLoading = false
        
        // NOUVEAU: Marquer comme abonnement direct AVANT de mettre à jour Firebase
        Task {
            await markSubscriptionAsDirect()
            
            // Mettre à jour Firebase APRÈS avoir marqué l'abonnement comme direct
            await MainActor.run {
                FirebaseService.shared.updateSubscriptionStatus(isSubscribed: true)
            }
            
            // Le partage automatique sera géré par PartnerSubscriptionSyncService
            print("🔥 SubscriptionService: Synchronisation automatique via PartnerSubscriptionSyncService")
        }
        
        SKPaymentQueue.default().finishTransaction(transaction)
        
        // Notifier le succès
        NotificationCenter.default.post(
            name: NSNotification.Name("SubscriptionPurchased"),
            object: nil
        )
        
        print("🔥 SubscriptionService: Achat finalisé et partagé avec partenaire")
        NSLog("🔥 SubscriptionService: Achat finalisé et partagé avec partenaire")
    }
    
    private func handleRestored(_ transaction: SKPaymentTransaction) {
        print("🔥 SubscriptionService: ✅ Achat restauré: \(transaction.payment.productIdentifier)")
        NSLog("🔥 SubscriptionService: ✅ Achat restauré: \(transaction.payment.productIdentifier)")
        
        isSubscribed = true
        isLoading = false
        
        // NOUVEAU: Marquer comme abonnement direct AVANT de mettre à jour Firebase
        Task {
            await markSubscriptionAsDirect()
            
            // Mettre à jour Firebase APRÈS avoir marqué l'abonnement comme direct
            await MainActor.run {
                FirebaseService.shared.updateSubscriptionStatus(isSubscribed: true)
            }
            
            // Le partage automatique sera géré par PartnerSubscriptionSyncService
            print("🔥 SubscriptionService: Synchronisation automatique via PartnerSubscriptionSyncService")
        }
        
        SKPaymentQueue.default().finishTransaction(transaction)
        
        // Notifier la restauration réussie
        NotificationCenter.default.post(
            name: NSNotification.Name("SubscriptionRestored"),
            object: nil
        )
        
        print("🔥 SubscriptionService: Restauration finalisée et partagée avec partenaire")
        NSLog("🔥 SubscriptionService: Restauration finalisée et partagée avec partenaire")
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
    
    // MARK: - Gestion de la résiliation d'abonnement
    
    func handleSubscriptionExpired() async {
        guard let currentUser = Auth.auth().currentUser else {
            print("🔥 SubscriptionService: Pas d'utilisateur connecté pour résiliation")
            return
        }
        
        print("🔥 SubscriptionService: Gestion résiliation abonnement pour: \(currentUser.uid)")
        
        // Révoquer l'abonnement de l'utilisateur principal
        await revokeUserSubscription(userId: currentUser.uid)
        
        // Mettre à jour l'état local
        await MainActor.run {
            self.isSubscribed = false
        }
        
        // Mettre à jour Firebase
        FirebaseService.shared.updateSubscriptionStatus(isSubscribed: false)
        
        // La synchronisation avec le partenaire sera automatiquement gérée par PartnerSubscriptionSyncService
        print("✅ SubscriptionService: Résiliation effectuée, synchronisation automatique en cours")
    }
    
    private func revokeUserSubscription(userId: String) async {
        do {
            try await Firestore.firestore()
                .collection("users")
                .document(userId)
                .updateData([
                    "isSubscribed": false,
                    "subscriptionExpiredAt": Timestamp(date: Date()),
                    "subscriptionType": FieldValue.delete()
                ])
            
            print("✅ SubscriptionService: Abonnement révoqué pour utilisateur: \(userId)")
            
        } catch {
            print("❌ SubscriptionService: Erreur révocation utilisateur: \(error)")
        }
    }
    
    // MARK: - Vérification périodique des abonnements
    
    func startSubscriptionStatusMonitoring() {
        // Timer pour vérifier l'état des abonnements toutes les heures
        Timer.scheduledTimer(withTimeInterval: 3600, repeats: true) { _ in
            Task {
                await self.checkSubscriptionStatus()
            }
        }
        
        print("🔥 SubscriptionService: Monitoring des abonnements démarré")
    }
    
    private func checkSubscriptionStatus() async {
        guard let currentUser = Auth.auth().currentUser else { return }
        
        print("🔥 SubscriptionService: Vérification statut abonnement...")
        
        // TODO: Ici vous devriez implémenter la vérification Apple Receipt
        // Pour l'instant, simulation basée sur l'état Firebase
        
        do {
            let userDoc = try await Firestore.firestore()
                .collection("users")
                .document(currentUser.uid)
                .getDocument()
            
            guard let userData = userDoc.data(),
                  let isSubscribed = userData["isSubscribed"] as? Bool else { return }
            
            // Si l'utilisateur était abonné localement mais ne l'est plus dans Firebase
            if self.isSubscribed && !isSubscribed {
                print("🔥 SubscriptionService: Détection de résiliation d'abonnement")
                await handleSubscriptionExpired()
            }
            
        } catch {
            print("❌ SubscriptionService: Erreur vérification statut: \(error)")
        }
    }
    
    private func markSubscriptionAsDirect() async {
        guard let currentUser = Auth.auth().currentUser else { return }
        
        do {
            try await Firestore.firestore()
                .collection("users")
                .document(currentUser.uid)
                .updateData([
                    "subscriptionType": "direct",
                    "subscriptionPurchasedAt": Timestamp(date: Date())
                ])
            
            print("✅ SubscriptionService: Abonnement marqué comme direct")
            
        } catch {
            print("❌ SubscriptionService: Erreur marquage abonnement direct: \(error)")
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