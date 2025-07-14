import Foundation

/// Service de localisation pour gérer les traductions de l'application
class LocalizationService {
    static let shared = LocalizationService()
    
    private init() {}
    
    /// Obtient une chaîne localisée avec un fallback en anglais
    func localizedString(for key: String, tableName: String? = nil, comment: String = "") -> String {
        let tableName = tableName ?? "UI" // Par défaut, utiliser UI.xcstrings
        
        // Première tentative avec la langue courante
        let translation = NSLocalizedString(key, tableName: tableName, comment: comment)
        if translation != key {
            return translation
        }
        
        // Fallback : essayer dans le bundle Anglais si disponible
        if let enPath = Bundle.main.path(forResource: "en", ofType: "lproj"),
           let enBundle = Bundle(path: enPath) {
            let fallback = NSLocalizedString(key, tableName: tableName, bundle: enBundle, value: key, comment: comment)
            return fallback
        }
        
        return key // dernier recours : retourner la clé
    }
    
    /// Helper spécifique pour les textes d'interface utilisateur (utilise UI.xcstrings)
    static func ui(_ key: String, comment: String = "") -> String {
        return shared.localizedString(for: key, tableName: "UI", comment: comment)
    }
    
    /// Helper pour les catégories (utilise Categories.xcstrings)
    static func category(_ key: String, comment: String = "") -> String {
        return shared.localizedString(for: key, tableName: "Categories", comment: comment)
    }
    
    /// Obtient une chaîne localisée avec un fallback en anglais (méthode générique)
    func localizedString(for key: String, comment: String = "") -> String {
        let translation = NSLocalizedString(key, comment: "")
        return translation != key ? translation : key
    }
}

// MARK: - Extension pour Question

extension Question {
    /// Version optimisée qui utilise le service de localisation
    var optimizedLocalizedText: String {
        // Avec le nouveau système QuestionDataManager, le texte est déjà localisé
        return text
    }
}

// MARK: - Extension pour QuestionCategory

extension QuestionCategory {
    /// Version optimisée des catégories avec lazy loading
    var optimizedTitle: String {
        return LocalizationService.shared.localizedString(for: "category_\(id)_title", comment: "Category title")
    }
    
    var optimizedSubtitle: String {
        return LocalizationService.shared.localizedString(for: "category_\(id)_subtitle", comment: "Category subtitle")
    }
} 