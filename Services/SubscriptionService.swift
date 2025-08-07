import Foundation
import StoreKit
import Combine
import FirebaseFirestore
import FirebaseAuth
import FirebaseAnalytics

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
        
        // Nettoyer les transactions en attente au démarrage pour éviter les blocages
        clearPendingTransactions()
        
        // Charger les produits après un court délai pour éviter les conflits
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            self.loadProducts()
        }
        
        checkSubscriptionStatus()
    }
    
    deinit {
        SKPaymentQueue.default().remove(self)
    }
    
    func loadProducts() {
        print("🔥 SubscriptionService: Chargement des produits...")
        
        let request = SKProductsRequest(productIdentifiers: productIdentifiers)
        request.delegate = self
        request.start()
        
        // Timeout de sécurité
        DispatchQueue.main.asyncAfter(deadline: .now() + 30) { [weak self] in
            if self?.products.isEmpty == true {
                print("⚠️ SubscriptionService: Timeout - Aucune réponse d'Apple après 30s")
                DispatchQueue.main.async {
                    self?.errorMessage = "Impossible de charger les offres d'abonnement. Vérifiez votre connexion et réessayez."
                }
            }
        }
    }
    
    func purchase(product: SKProduct) {
        print("🔥 SubscriptionService: Tentative d'achat: \(product.productIdentifier)")
        
        guard SKPaymentQueue.canMakePayments() else {
            print("❌ SubscriptionService: Achats non autorisés sur cet appareil")
            errorMessage = "Les achats ne sont pas autorisés sur cet appareil"
            return
        }
        
        isLoading = true
        let payment = SKPayment(product: product)
        SKPaymentQueue.default().add(payment)
    }
    
    func restorePurchases() {
        print("🔥 SubscriptionService: Restauration des achats...")
        
        isLoading = true
        SKPaymentQueue.default().restoreCompletedTransactions()
    }
    
    /// Nettoyer les transactions en attente qui peuvent bloquer les achats
    private func clearPendingTransactions() {
        let pendingTransactions = SKPaymentQueue.default().transactions
        
        guard !pendingTransactions.isEmpty else { return }
        
        print("🔧 SubscriptionService: Nettoyage de \(pendingTransactions.count) transaction(s) en attente")
        
        for transaction in pendingTransactions {
            switch transaction.transactionState {
            case .purchased, .restored, .failed:
                SKPaymentQueue.default().finishTransaction(transaction)
            case .purchasing:
                print("⚠️ SubscriptionService: Transaction en cours d'achat détectée")
            case .deferred:
                print("⚠️ SubscriptionService: Transaction différée détectée")
            @unknown default:
                break
            }
        }
        
        // Réinitialiser l'état de chargement
        DispatchQueue.main.async {
            self.isLoading = false
            self.errorMessage = nil
        }
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
                // S'assurer que le loading reste actif pendant l'achat
                DispatchQueue.main.async {
                    self.isLoading = true
                }
                
                // Timeout de sécurité pour éviter le chargement infini
                DispatchQueue.main.asyncAfter(deadline: .now() + 60) { [weak self] in
                    // Vérifier si la transaction est toujours en cours après 60s
                    if let currentTransaction = queue.transactions.first(where: { $0.transactionIdentifier == transaction.transactionIdentifier }),
                       currentTransaction.transactionState == .purchasing {
                        print("⚠️ SubscriptionService: Transaction bloquée en état purchasing depuis 60s")
                        NSLog("⚠️ SubscriptionService: Transaction bloquée en état purchasing depuis 60s")
                        DispatchQueue.main.async {
                            self?.isLoading = false
                            self?.errorMessage = "La transaction a pris trop de temps. Veuillez réessayer."
                        }
                    }
                }
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
        
        // 📊 Analytics: Abonnement réussi avec le bon type
        let planType = transaction.payment.productIdentifier.contains("weekly") ? "weekly" : "monthly"
        Analytics.logEvent("abonnement_reussi", parameters: [
            "type": planType,
            "source": "storekit_success"
        ])
        print("📊 Événement Firebase: abonnement_reussi - type: \(planType) - source: storekit_success")
        
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
        
        // 📊 Analytics: Achat restauré
        Analytics.logEvent("achat_restaure", parameters: [:])
        print("📊 Événement Firebase: achat_restaure")
        
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
        print("🔥 SubscriptionService: Réponse reçue - \(response.products.count) produits valides")
        
        if !response.invalidProductIdentifiers.isEmpty {
            print("❌ SubscriptionService: Identifiants invalides: \(response.invalidProductIdentifiers)")
        }
        
        DispatchQueue.main.async {
            self.products = response.products
            
            if self.products.isEmpty {
                print("❌ SubscriptionService: Aucun produit chargé")
                self.errorMessage = "Aucun produit disponible. Vérifiez votre connexion."
            }
        }
    }
    
    func request(_ request: SKRequest, didFailWithError error: Error) {
        print("❌ SubscriptionService: Erreur chargement produits: \(error.localizedDescription)")
        
        DispatchQueue.main.async {
            self.errorMessage = "Erreur lors du chargement des produits: \(error.localizedDescription)"
        }
    }
} 