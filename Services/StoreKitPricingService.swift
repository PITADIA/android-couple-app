import Foundation
import StoreKit
import Combine

// MARK: - Erreurs du service de tarification

enum StoreKitPricingError: LocalizedError {
    case noProductsAvailable
    case productProcessingFailed
    case partiallyLoaded
    case storeKitUnavailable
    
    var errorDescription: String? {
        switch self {
        case .noProductsAvailable:
            return "Aucun produit disponible depuis l'App Store"
        case .productProcessingFailed:
            return "Erreur lors du traitement des produits"
        case .partiallyLoaded:
            return "Certains prix n'ont pas pu être chargés"
        case .storeKitUnavailable:
            return "StoreKit n'est pas disponible"
        }
    }
}

/// Service pour récupérer les prix localisés dynamiquement depuis StoreKit
class StoreKitPricingService: ObservableObject {
    static let shared = StoreKitPricingService()
    
    // MARK: - Published Properties
    @Published var localizedPrices: [String: LocalizedPrice] = [:]
    @Published var isLoading: Bool = false
    @Published var lastError: Error?
    
    // MARK: - Private Properties
    private let productIdentifiers: Set<String> = [
        "com.lyes.love2love.subscription.weekly.mi",
        "com.lyes.love2love.subscription.monthly.mi"
    ]
    
    private var cancellables = Set<AnyCancellable>()
    
    // 🔇 Tracking pour éviter les logs répétitifs
    private var loggedDynamicPrices = Set<String>()
    private var loggedFallbackPrices = Set<String>()
    
    // MARK: - Initialization
    private init() {
        // Observer les produits du SubscriptionService
        SubscriptionService.shared.$products
            .sink { [weak self] products in
                self?.updateLocalizedPrices(from: products)
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public Methods
    
    /// Récupère le prix localisé pour un produit spécifique
    func getLocalizedPrice(for productId: String) -> String {
        // Vérifier si nous avons des prix dynamiques disponibles
        if let localizedPrice = localizedPrices[productId] {
            // 🔇 Log réduit : seulement lors du premier accès par produit
            if !loggedDynamicPrices.contains(productId) {
                print("💰 StoreKitPricingService: Prix dynamique activé pour \(productId): \(localizedPrice.formattedPrice)")
                loggedDynamicPrices.insert(productId)
            }
            return localizedPrice.formattedPrice
        }
        
        // Log de l'utilisation du fallback (une seule fois par produit)
        if !loggedFallbackPrices.contains(productId) {
            print("⚠️ StoreKitPricingService: Prix fallback utilisé pour \(productId)")
            if let error = lastError {
                print("⚠️ StoreKitPricingService: Raison: \(error.localizedDescription)")
            }
            loggedFallbackPrices.insert(productId)
        }
        
        // Fallback vers les prix hardcodés si StoreKit n'est pas disponible
        return getFallbackPrice(for: productId)
    }
    
    /// Récupère le prix par utilisateur pour un produit spécifique (prix / 2)
    func getPricePerUser(for productId: String) -> String {
        // Vérifier si nous avons des prix dynamiques disponibles
        if let localizedPrice = localizedPrices[productId] {
            // 🔇 Pas de log répétitif - déjà loggé dans getLocalizedPrice
            return localizedPrice.formattedPricePerUser
        }
        
        // 🔇 Pas de log répétitif - déjà loggé dans getLocalizedPrice
        // Fallback vers les prix hardcodés
        return getFallbackPricePerUser(for: productId)
    }
    
    /// Force le rechargement des prix depuis StoreKit
    func refreshPrices() {
        SubscriptionService.shared.loadProducts()
    }
    
    /// Vérifie si les prix dynamiques sont disponibles
    var hasDynamicPrices: Bool {
        return !localizedPrices.isEmpty
    }
    
    /// Obtient un diagnostic des prix chargés pour le debugging
    func getPricingDiagnostic() -> String {
        var diagnostic = "=== StoreKitPricingService Diagnostic ===\n"
        diagnostic += "Prix dynamiques disponibles: \(hasDynamicPrices)\n"
        diagnostic += "Nombre de produits: \(localizedPrices.count)\n"
        diagnostic += "Loading: \(isLoading)\n"
        
        if let error = lastError {
            diagnostic += "Dernière erreur: \(error.localizedDescription)\n"
        }
        
        diagnostic += "\nProduits chargés:\n"
        for (productId, price) in localizedPrices {
            diagnostic += "  - \(productId): \(price.formattedPrice) (\(price.currencyCode))\n"
            diagnostic += "    Prix/utilisateur: \(price.formattedPricePerUser)\n"
        }
        
        if localizedPrices.isEmpty {
            diagnostic += "  Aucun produit chargé - utilisation des prix fallback\n"
        }
        
        diagnostic += "=======================================\n"
        return diagnostic
    }
    
    // MARK: - Private Methods
    
    private func updateLocalizedPrices(from products: [SKProduct]) {
        guard !products.isEmpty else {
            print("⚠️ StoreKitPricingService: Aucun produit reçu - Utilisation des prix hardcodés")
            DispatchQueue.main.async {
                self.isLoading = false
                self.lastError = StoreKitPricingError.noProductsAvailable
            }
            return
        }
        

        
        var updatedPrices: [String: LocalizedPrice] = [:]
        var hasErrors = false
        
        for product in products {
            do {
                let localizedPrice = LocalizedPrice(product: product)
                updatedPrices[product.productIdentifier] = localizedPrice
                

            } catch {
                print("❌ StoreKitPricingService: Erreur traitement produit \(product.productIdentifier): \(error)")
                hasErrors = true
            }
        }
        
        // Vérifier qu'on a les produits essentiels
        let requiredProducts = ["com.lyes.love2love.subscription.weekly.mi", "com.lyes.love2love.subscription.monthly.mi"]
        let missingProducts = requiredProducts.filter { !updatedPrices.keys.contains($0) }
        
        if !missingProducts.isEmpty {
            print("⚠️ StoreKitPricingService: Produits manquants: \(missingProducts)")
            hasErrors = true
        }
        
        DispatchQueue.main.async {
            self.localizedPrices = updatedPrices
            self.isLoading = false
            
            // 🔄 Reset les tracking de logs pour les nouveaux prix
            self.loggedDynamicPrices.removeAll()
            self.loggedFallbackPrices.removeAll()
            
            if hasErrors && updatedPrices.isEmpty {
                self.lastError = StoreKitPricingError.productProcessingFailed
            } else if hasErrors {
                self.lastError = StoreKitPricingError.partiallyLoaded
            } else {
                self.lastError = nil
            }
            
            // Imprimer le diagnostic en mode debug
            #if DEBUG
            print(self.getPricingDiagnostic())
            #endif
        }
    }
    
    private func getFallbackPrice(for productId: String) -> String {
        switch productId {
        case "com.lyes.love2love.subscription.weekly.mi":
            let basePrice = "plan_weekly_price".localized
            return LocalizationService.localizedCurrencySymbol(for: basePrice)
        case "com.lyes.love2love.subscription.monthly.mi":
            let basePrice = "plan_monthly_price".localized
            return LocalizationService.localizedCurrencySymbol(for: basePrice)
        default:
            return "Prix non disponible"
        }
    }
    
    private func getFallbackPricePerUser(for productId: String) -> String {
        // 🔇 Log déjà fait dans getLocalizedPrice - pas de doublon
        
        switch productId {
        case "com.lyes.love2love.subscription.weekly.mi":
            let basePrice = "plan_weekly_price_per_user".localized
            return LocalizationService.localizedCurrencySymbol(for: basePrice)
        case "com.lyes.love2love.subscription.monthly.mi":
            let basePrice = "plan_monthly_price_per_user".localized
            return LocalizationService.localizedCurrencySymbol(for: basePrice)
        default:
            return "Prix non disponible"
        }
    }
}

// MARK: - LocalizedPrice Model

struct LocalizedPrice {
    let productId: String
    let price: NSDecimalNumber
    let locale: Locale
    let currencyCode: String
    let formattedPrice: String
    let formattedPricePerUser: String
    
    init(product: SKProduct) {
        self.productId = product.productIdentifier
        self.price = product.price
        self.locale = product.priceLocale
        // 🎯 Choisir la locale appropriée pour le formatage
        let systemLocale = Locale.current
        let systemCurrency = systemLocale.currency?.identifier ?? "USD"
        let storeKitCurrency = product.priceLocale.currency?.identifier ?? "USD"
        
        // Utiliser la locale système si les devises ne correspondent pas
        let shouldUseSystemLocale = systemCurrency != storeKitCurrency
        let formatterLocale = shouldUseSystemLocale ? systemLocale : product.priceLocale
        
        // Devise (utiliser celle de la locale choisie)
        if shouldUseSystemLocale {
            self.currencyCode = systemCurrency
        } else {
            if #available(iOS 16.0, *) {
                self.currencyCode = product.priceLocale.currency?.identifier ?? "EUR"
            } else {
                self.currencyCode = product.priceLocale.currencyCode ?? "EUR"
            }
        }
        
        // Formatter pour le prix principal
        let priceFormatter = NumberFormatter()
        priceFormatter.numberStyle = .currency
        priceFormatter.locale = formatterLocale
        self.formattedPrice = priceFormatter.string(from: product.price) ?? "\(product.price)"
        
        // Calcul du prix par utilisateur (prix / 2)
        let pricePerUser = product.price.dividing(by: NSDecimalNumber(value: 2))
        self.formattedPricePerUser = priceFormatter.string(from: pricePerUser) ?? "\(pricePerUser)"
        
        // Log de la correction si locale système utilisée
        if shouldUseSystemLocale {
            print("🔧 LocalizedPrice: CORRECTION APPLIQUÉE")
            print("🔧   StoreKit: \(product.priceLocale.identifier) (\(storeKitCurrency))")
            print("🔧   Système: \(systemLocale.identifier) (\(systemCurrency))")
            print("🔧   Prix corrigé: '\(self.formattedPrice)' (était potentiellement en \(storeKitCurrency))")
        }
        

    }
}

// MARK: - Extensions pour compatibilité

extension StoreKitPricingService {
    /// Récupère le prix localisé pour un SubscriptionPlanType
    func getLocalizedPrice(for planType: SubscriptionPlanType) -> String {
        return getLocalizedPrice(for: planType.rawValue)
    }
    
    /// Récupère le prix par utilisateur pour un SubscriptionPlanType
    func getPricePerUser(for planType: SubscriptionPlanType) -> String {
        return getPricePerUser(for: planType.rawValue)
    }
    
    /// Récupère les détails complets du prix pour un plan
    func getPriceDetails(for planType: SubscriptionPlanType) -> LocalizedPrice? {
        return localizedPrices[planType.rawValue]
    }
} 