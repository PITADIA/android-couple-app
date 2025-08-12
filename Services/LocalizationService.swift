import Foundation

/// Service de localisation pour gérer les traductions de l'application
class LocalizationService {
    static let shared = LocalizationService()
    
    // Variable statique pour éviter les logs répétitifs
    private static var lastLoggedLanguage: String = ""
    
    private init() {}
    
    /// Obtient une chaîne localisée avec String Catalogs
    func localizedString(for key: String, tableName: String? = nil, comment: String = "") -> String {
        let tableName = tableName ?? "UI" // Par défaut, utiliser UI.xcstrings
        
        // Utiliser NSLocalizedString directement avec String Catalogs
        let translation = NSLocalizedString(key, tableName: tableName, comment: comment)
        return translation
    }
    
    /// Helper spécifique pour les textes d'interface utilisateur (utilise UI.xcstrings)
    static func ui(_ key: String, comment: String = "") -> String {
        return NSLocalizedString(key, tableName: "UI", comment: comment)
    }
    
    /// Helper pour les catégories (utilise Categories.xcstrings)
    static func category(_ key: String, comment: String = "") -> String {
        return NSLocalizedString(key, tableName: "Categories", comment: comment)
    }
    
    /// Obtient une chaîne localisée avec String Catalogs (méthode générique)
    func localizedString(for key: String, comment: String = "") -> String {
        return NSLocalizedString(key, comment: comment)
    }
    
    /// Change le symbole de devise selon la région du téléphone (garde le même prix)
    /// ⚠️ DEPRECATED: Utilisé uniquement comme fallback si StoreKit n'est pas disponible
    static func localizedCurrencySymbol(for priceString: String) -> String {
        // Utiliser la région système plutôt que la devise locale de l'App Store
        let regionCode = Locale.current.region?.identifier ?? "FR"
        let languageCode = Locale.current.language.languageCode?.identifier ?? "fr"
        
        // Extraire le prix numérique (ex: "4,99€" -> "4,99")
        let numericPrice = priceString.replacingOccurrences(of: "€", with: "")
        
        // Déterminer la devise selon la région/langue
        let currencySymbol: String
        
        // Prioriser la langue d'interface si elle est anglaise
        if languageCode == "en" {
            switch regionCode {
            case "US":
                currencySymbol = "$"
            case "GB":
                currencySymbol = "£"
            case "CA":
                currencySymbol = "CAD$"
            case "AU":
                currencySymbol = "AUD$"
            default:
                currencySymbol = "$" // Par défaut dollar pour interface anglaise
            }
        } else {
            // Sinon utiliser la région
            switch regionCode {
            case "US", "PR", "VI", "GU", "AS", "MP":
                currencySymbol = "$"
            case "GB", "IM", "JE", "GG":
                currencySymbol = "£"
            case "CA":
                currencySymbol = "CAD$"
            case "CH", "LI":
                currencySymbol = "CHF"
            case "JP":
                currencySymbol = "¥"
            case "AU":
                currencySymbol = "AUD$"
            default:
                currencySymbol = "€"
            }
        }
        
        // Formater selon le symbole
        if currencySymbol.contains("$") && !currencySymbol.contains("CAD") && !currencySymbol.contains("AUD") {
            return "$\(numericPrice)" // Dollar avant
        } else if currencySymbol == "£" {
            return "£\(numericPrice)" // Livre avant
        } else if currencySymbol == "¥" {
            return "¥\(numericPrice)" // Yen avant
        } else if currencySymbol.contains("CAD") || currencySymbol.contains("AUD") || currencySymbol.contains("CHF") {
            return "\(numericPrice) \(currencySymbol)" // Après pour CAD, AUD, CHF
        } else {
            return priceString // Garder € par défaut
        }
    }
    
    /// Divise un prix formaté par 2 pour calculer le prix par utilisateur
    /// Utilisé par StoreKitPricingService pour maintenir la cohérence avec l'ancien système
    static func dividePrice(formattedPrice: String, by divisor: Double = 2.0) -> String {
        // Extraire le prix numérique du string formaté
        let cleanPrice = formattedPrice.replacingOccurrences(of: "[^0-9.,]", with: "", options: .regularExpression)
        
        if let price = Double(cleanPrice.replacingOccurrences(of: ",", with: ".")), price > 0 {
            let dividedPrice = price / divisor
            
            // Réappliquer le format original
            let formatter = NumberFormatter()
            formatter.numberStyle = .currency
            formatter.currencyCode = getCurrencyCode(from: formattedPrice)
            formatter.locale = Locale.current
            
            return formatter.string(from: NSNumber(value: dividedPrice)) ?? formattedPrice
        }
        
        return formattedPrice
    }
    
    /// Extrait le code de devise d'un prix formaté
    private static func getCurrencyCode(from formattedPrice: String) -> String {
        if formattedPrice.contains("$") {
            return "USD"
        } else if formattedPrice.contains("£") {
            return "GBP"
        } else if formattedPrice.contains("€") {
            return "EUR"
        } else if formattedPrice.contains("¥") {
            return "JPY"
        } else if formattedPrice.contains("CHF") {
            return "CHF"
        } else {
            return "EUR" // Par défaut
        }
    }
    
    /// Retourne le nom de l'image localisée selon la langue du téléphone
    static func localizedImageName(frenchImage: String, defaultImage: String) -> String {
        let languageCode = Locale.current.language.languageCode?.identifier ?? "en"
        
        // 🔇 Log réduit : seulement lors du premier appel ou changement de langue
        if lastLoggedLanguage != languageCode {
            print("🖼️ LocalizationService: Langue système détectée: \(languageCode)")
            lastLoggedLanguage = languageCode
        }
        
        // Si la langue est française, utiliser l'image française
        if languageCode == "fr" {
            return frenchImage
        } else {
            // Pour toutes les autres langues (anglais, etc.), utiliser l'image par défaut
            return defaultImage
        }
    }

    /// Retourne le nom de l'image localisée avec support spécifique pour l'allemand
    /// - Parameters:
    ///   - frenchImage: nom de l'image en français
    ///   - defaultImage: nom de l'image par défaut (anglais et autres)
    ///   - germanImage: nom de l'image pour l'allemand
    /// - Returns: le nom d'image correspondant à la langue système
    static func localizedImageName(frenchImage: String, defaultImage: String, germanImage: String) -> String {
        let languageCode = Locale.current.language.languageCode?.identifier ?? "en"

        if lastLoggedLanguage != languageCode {
            print("🖼️ LocalizationService: Langue système détectée: \(languageCode)")
            lastLoggedLanguage = languageCode
        }

        switch languageCode {
        case "fr":
            return frenchImage
        case "de":
            return germanImage
        default:
            return defaultImage
        }
    }
}

// MARK: - Extension pour Question

extension Question {
    /// Version optimisée qui utilise le service de localisation
    var optimizedLocalizedText: String {
        // Avec le nouveau système String Catalogs, le texte est déjà localisé
        return text
    }
}

// MARK: - Extensions pour l'UI

extension String {
    /// Helper pour obtenir le texte localisé depuis UI.xcstrings
    var localizedUI: String {
        return LocalizationService.ui(self)
    }
    
    /// Helper pour obtenir le texte localisé depuis Categories.xcstrings  
    var localizedCategory: String {
        return LocalizationService.category(self)
    }
} 