import SwiftUI

// MARK: - Color Extensions

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }

        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - AppUser Extensions (removed old Realm-based User extension) 

// MARK: - String Localization Extensions

extension String {
    /// Localise une chaîne en utilisant UI.xcstrings par défaut
    var localized: String {
        return NSLocalizedString(self, tableName: "UI", comment: "")
    }
    
    /// Localise une chaîne avec un commentaire spécifique
    func localized(comment: String = "") -> String {
        return NSLocalizedString(self, tableName: "UI", comment: comment)
    }
    
    /// Localise une chaîne avec une table spécifique
    func localized(tableName: String, comment: String = "") -> String {
        return NSLocalizedString(self, tableName: tableName, comment: comment)
    }
    
    /// Convertit une distance en km vers miles pour la localisation anglaise
    func convertedForLocale() -> String {
        // Déterminer la langue courante
        let currentLanguage = Locale.current.languageCode ?? "en"
        
        // Si c'est déjà en anglais et contient "km", convertir en miles
        if currentLanguage == "en" {
            return self.convertKmToMiles()
        }
        
        return self
    }
    
    /// Convertit une chaîne contenant des km en miles
    private func convertKmToMiles() -> String {
        // Cas spécial « ? km »
        if self.trimmingCharacters(in: .whitespaces) == "? km" {
            return self.replacingOccurrences(of: "? km", with: "? mi")
        }

        // Regex pour km ou m
        let kmPattern = #"([0-9]+(?:\.[0-9]+)?)\s*km"#
        let mPattern  = #"([0-9]+)\s*m"#
        
        // Vérifier d'abord le format kilomètres
        if let regex = try? NSRegularExpression(pattern: kmPattern, options: []),
           let match = regex.firstMatch(in: self, options: [], range: NSRange(location: 0, length: self.count)),
           let kmRange = Range(match.range(at: 1), in: self),
           let kmValue = Double(self[kmRange]) {
            let milesValue = kmValue * 0.621371
            return replaceWithMiles(originalKmString: String(self[kmRange]) + " km", milesValue: milesValue)
        }
        
        // Vérifier le format mètres (ex : "750 m")
        if let regex = try? NSRegularExpression(pattern: mPattern, options: []),
           let match = regex.firstMatch(in: self, options: [], range: NSRange(location: 0, length: self.count)),
           let mRange = Range(match.range(at: 1), in: self),
           let meters = Double(self[mRange]) {
            let milesValue = meters / 1609.34
            return replaceWithMiles(originalKmString: String(self[mRange]) + " m", milesValue: milesValue)
        }
        
        // Aucun format reconnu -> retourner la chaîne d'origine
        return self
    }
    
    /// Helper pour formater les miles et remplacer la sous-chaîne d'origine
    private func replaceWithMiles(originalKmString: String, milesValue: Double) -> String {
        let formatted: String
        if milesValue < 10 {
            formatted = String(format: "%.1f mi", milesValue)
        } else {
            formatted = "\(Int(milesValue)) mi"
        }
        return self.replacingOccurrences(of: originalKmString, with: formatted)
    }
}

// MARK: - SwiftUI Text Localization Helper

extension Text {
    /// Crée un Text avec une chaîne localisée depuis UI.xcstrings
    init(ui key: String, comment: String = "") {
        self.init(LocalizationService.ui(key, comment: comment))
    }
    
    /// Crée un Text avec une chaîne localisée depuis une table spécifique
    init(localized key: String, tableName: String, comment: String = "") {
        self.init(LocalizationService.shared.localizedString(for: key, tableName: tableName, comment: comment))
    }
} 