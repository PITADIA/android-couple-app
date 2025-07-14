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
        return LocalizationService.ui(self)
    }
    
    /// Localise une chaîne avec un commentaire spécifique
    func localized(comment: String = "") -> String {
        return LocalizationService.ui(self, comment: comment)
    }
    
    /// Localise une chaîne avec une table spécifique
    func localized(tableName: String, comment: String = "") -> String {
        return LocalizationService.shared.localizedString(for: self, tableName: tableName, comment: comment)
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