import SwiftUI
import UserNotifications
import UIKit

// MARK: - UIDevice Extensions

extension UIDevice {
    var modelName: String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let machineMirror = Mirror(reflecting: systemInfo.machine)
        let identifier = machineMirror.children.reduce("") { identifier, element in
            guard let value = element.value as? Int8, value != 0 else { return identifier }
            return identifier + String(UnicodeScalar(UInt8(value)))
        }
        
        switch identifier {
        case "iPhone11,2": return "iPhone XS"
        case "iPhone11,4", "iPhone11,6": return "iPhone XS Max"
        case "iPhone11,8": return "iPhone XR"
        case "iPhone12,1": return "iPhone 11"
        case "iPhone12,3": return "iPhone 11 Pro"
        case "iPhone12,5": return "iPhone 11 Pro Max"
        case "iPhone13,1": return "iPhone 12 mini"
        case "iPhone13,2": return "iPhone 12"
        case "iPhone13,3": return "iPhone 12 Pro"
        case "iPhone13,4": return "iPhone 12 Pro Max"
        case "iPhone14,2": return "iPhone 13 mini"
        case "iPhone14,3": return "iPhone 13"
        case "iPhone14,4": return "iPhone 13 Pro"
        case "iPhone14,5": return "iPhone 13 Pro Max"
        case "iPhone14,6": return "iPhone SE (3rd generation)"
        case "iPhone14,7": return "iPhone 14"
        case "iPhone14,8": return "iPhone 14 Plus"
        case "iPhone15,2": return "iPhone 14 Pro"
        case "iPhone15,3": return "iPhone 14 Pro Max"
        case "iPhone15,4": return "iPhone 15"
        case "iPhone15,5": return "iPhone 15 Plus"
        case "iPhone16,1": return "iPhone 15 Pro"
        case "iPhone16,2": return "iPhone 15 Pro Max"
        case "x86_64", "i386": return "Simulator"
        default: return identifier
        }
    }
}

// MARK: - Badge Management Utility

/// Utilitaire global pour gérer les badges de l'app
struct BadgeManager {

    /// Remet le badge de l'app à zéro
    static func clearBadge() {
        DispatchQueue.main.async {
            if #available(iOS 17.0, *) {
                UNUserNotificationCenter.current().setBadgeCount(0) { error in
                    if let error = error {
                        print("❌ BadgeManager: Erreur lors de la remise à 0 du badge: \(error)")
                    } else {
                        print("✅ BadgeManager: Badge remis à 0")
                    }
                }
            } else {
                UIApplication.shared.applicationIconBadgeNumber = 0
                print("✅ BadgeManager: Badge remis à 0 (iOS < 17)")
            }
        }
    }

    /// Définit le badge avec un nombre spécifique
    static func setBadge(count: Int) {
        DispatchQueue.main.async {
            if #available(iOS 17.0, *) {
                UNUserNotificationCenter.current().setBadgeCount(count) { error in
                    if let error = error {
                        print("❌ BadgeManager: Erreur lors de la définition du badge à \(count): \(error)")
                    } else {
                        print("🔔 BadgeManager: Badge défini à \(count)")
                    }
                }
            } else {
                UIApplication.shared.applicationIconBadgeNumber = count
                print("🔔 BadgeManager: Badge défini à \(count) (iOS < 17)")
            }
        }
    }

    /// Nettoie toutes les notifications en attente et remet le badge à 0
    static func clearAllNotificationsAndBadge() {
        // Supprimer toutes les notifications en attente
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()

        // Supprimer toutes les notifications déjà livrées du centre de notifications
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()

        // Remettre le badge à 0
        clearBadge()

        print("🧹 BadgeManager: Toutes les notifications et badge nettoyés")
    }
}

// MARK: - Optimized Text Wrapper for Chat Messages

/// UILabel wrapper optimisé avec césure Instagram-style (naturelle pour partenaire, nette pour utilisateur)
struct ChatText: View {
    let text: String
    let font: UIFont
    let textColor: UIColor
    let textAlignment: NSTextAlignment
    let isCurrentUser: Bool
    
    @State private var dynamicHeight: CGFloat = .zero
    
    init(
        _ text: String,
        font: UIFont = UIFont.systemFont(ofSize: 16),
        textColor: UIColor = .label,
        textAlignment: NSTextAlignment = .natural,
        isCurrentUser: Bool = false
    ) {
        self.text = text
        self.font = font
        self.textColor = textColor
        self.textAlignment = textAlignment
        self.isCurrentUser = isCurrentUser
    }
    
         var body: some View {
         GeometryReader { geometry in
             InternalChatLabel(
                 text: text,
                 font: font,
                 textColor: textColor,
                 textAlignment: textAlignment,
                 isCurrentUser: isCurrentUser,
                 availableWidth: geometry.size.width,
                 dynamicHeight: $dynamicHeight
             )
         }
         .frame(height: dynamicHeight)
     }
}

/// Wrapper UILabel interne avec césure Instagram-style
struct InternalChatLabel: UIViewRepresentable {
    let text: String
    let font: UIFont
    let textColor: UIColor
    let textAlignment: NSTextAlignment
    let isCurrentUser: Bool
    let availableWidth: CGFloat
    @Binding var dynamicHeight: CGFloat
    
    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        
        // 🎯 CÉSURE ADAPTATIVE: Configuration selon l'expéditeur
        label.numberOfLines = 0
        label.lineBreakMode = .byWordWrapping
        
        // ✨ CÉSURE INSTAGRAM-STYLE: pushOut pour partenaire, standard pour utilisateur
        if #available(iOS 14.0, *) {
            if !isCurrentUser {
                // Messages du partenaire (gauche) : césure naturelle évitant les mots orphelins
                label.lineBreakStrategy = .pushOut
            } else {
                // Messages utilisateur (droite) : césure standard pour lignes plus nettes
                label.lineBreakStrategy = .standard
            }
        }
        
        // 🔧 LAYOUT: Compression resistance pour bon fonctionnement dans SwiftUI
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        label.setContentCompressionResistancePriority(.required, for: .vertical)
        label.setContentHuggingPriority(.defaultLow, for: .horizontal)
        label.setContentHuggingPriority(.required, for: .vertical)
        
        return label
    }
    
    func updateUIView(_ uiView: UILabel, context: Context) {
        uiView.text = text
        uiView.font = font
        uiView.textColor = textColor
        uiView.textAlignment = textAlignment
        uiView.preferredMaxLayoutWidth = availableWidth
        
        // 📏 CALCUL HAUTEUR DYNAMIQUE: Essentiel pour le multilignes
        DispatchQueue.main.async {
            let targetSize = CGSize(width: availableWidth, height: .greatestFiniteMagnitude)
            let newHeight = uiView.sizeThatFits(targetSize).height
            if dynamicHeight != newHeight {
                dynamicHeight = newHeight
            }
        }
    }
}

// MARK: - Color Extension

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
    
    /// Localise une chaîne depuis Onboarding.xcstrings
    var localizedOnboarding: String {
        return NSLocalizedString(self, tableName: "Onboarding", comment: "")
    }
    
    /// Convertit une distance en km vers miles pour la localisation anglaise
    func convertedForLocale() -> String {
        // Déterminer la langue courante
        let currentLanguage: String
        if #available(iOS 16.0, *) {
            currentLanguage = Locale.current.language.languageCode?.identifier ?? "en"
        } else {
            currentLanguage = Locale.current.languageCode ?? "en"
        }
        
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

// MARK: - 🕐 DateFormatter Extensions pour Timezone Optimization

extension DateFormatter {
    /// ⏰ Formatter pour l'heure avec timezone (utilisé dans les logs)
    static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeStyle = .medium
        formatter.dateStyle = .none
        return formatter
    }()
    
    /// 📅 Formatter pour la date du jour (utilisé pour cache Realm)
    static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone.current // Utilise timezone locale
        return formatter
    }()
    
    /// 🌍 Formatter pour debug timezone avec offset
    static let timezoneDebugFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss zzz"
        return formatter
    }()
} 

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        return controller
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

// MARK: - Keyboard Extension

extension View {
    func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
} 