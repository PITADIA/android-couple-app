//
//  Love2LoveWidget.swift
//  Love2LoveWidget
//
//  Created by Lyes  on 24/06/2025.
//

import WidgetKit
import SwiftUI
import MapKit

// MARK: - Color Extension pour les widgets
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

// MARK: - Widget Data Model
struct WidgetData {
    let daysTotal: Int
    let duration: String
    let daysToAnniversary: Int
    let distance: String?
    let message: String?
    let userName: String?
    let partnerName: String?
    let userImageURL: String?
    let partnerImageURL: String?
    let userLatitude: Double?
    let userLongitude: Double?
    let partnerLatitude: Double?
    let partnerLongitude: Double?
    let lastUpdate: Date
    
    static var placeholder: WidgetData {
        WidgetData(
            daysTotal: 365,
            duration: "1 an",
            daysToAnniversary: 30,
            distance: "3.128 km",
            message: "💕 Je pense à toi",
            userName: "Alex",
            partnerName: "Morgan",
            userImageURL: nil,
            partnerImageURL: nil,
            userLatitude: 48.8566,
            userLongitude: 2.3522,
            partnerLatitude: 43.6047,
            partnerLongitude: 1.4442,
            lastUpdate: Date()
        )
    }
    
    static var placeholderUserMissing: WidgetData {
        WidgetData(
            daysTotal: 365,
            duration: "1 an",
            daysToAnniversary: 30,
            distance: nil,
            message: "💕 Je pense à toi",
            userName: "Alex",
            partnerName: "Morgan",
            userImageURL: nil,
            partnerImageURL: nil,
            userLatitude: nil,
            userLongitude: nil,
            partnerLatitude: 43.6047,
            partnerLongitude: 1.4442,
            lastUpdate: Date()
        )
    }
    
    static var placeholderPartnerMissing: WidgetData {
        WidgetData(
            daysTotal: 365,
            duration: "1 an",
            daysToAnniversary: 30,
            distance: nil,
            message: "💕 Je pense à toi",
            userName: "Alex",
            partnerName: "Morgan",
            userImageURL: nil,
            partnerImageURL: nil,
            userLatitude: 48.8566,
            userLongitude: 2.3522,
            partnerLatitude: nil,
            partnerLongitude: nil,
            lastUpdate: Date()
        )
    }
    
    static func loadFromUserDefaults() -> WidgetData? {
        guard let sharedDefaults = UserDefaults(suiteName: "group.com.lyes.love2love") else {
            print("❌ Widget: Impossible d'accéder aux UserDefaults partagés")
            return nil
        }
        
        print("🔍 Widget: Début chargement données UserDefaults...")
        
        let daysTotal = sharedDefaults.integer(forKey: "widget_days_total")
        let duration = sharedDefaults.string(forKey: "widget_duration") ?? ""
        let daysToAnniversary = sharedDefaults.integer(forKey: "widget_days_to_anniversary")
        let distance = sharedDefaults.string(forKey: "widget_distance")
        let message = sharedDefaults.string(forKey: "widget_message")
        let userName = sharedDefaults.string(forKey: "widget_user_name")
        let partnerName = sharedDefaults.string(forKey: "widget_partner_name")
        
        // CORRECTION: Ne récupérer les URLs d'images que si elles existent réellement
        let userImageURL = sharedDefaults.string(forKey: "widget_user_image_url")
        let partnerImageURL = sharedDefaults.string(forKey: "widget_partner_image_url")
        
        print("🔍 Widget: Données récupérées:")
        print("  - daysTotal: \(daysTotal)")
        print("  - userName: \(userName ?? "nil")")
        print("  - partnerName: \(partnerName ?? "nil")")
        print("  - userImageURL: \(userImageURL ?? "nil")")
        print("  - partnerImageURL: \(partnerImageURL ?? "nil")")
        
        // Vérifier le contenu du dossier App Group
        if let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.lyes.love2love") {
            print("🔍 Widget: Container App Group trouvé: \(containerURL.path)")
            
            do {
                let contents = try FileManager.default.contentsOfDirectory(atPath: containerURL.path)
                print("🔍 Widget: Contenu du container App Group:")
                for item in contents {
                    print("  - \(item)")
                }
                
                // Vérifier spécifiquement le dossier ImageCache
                let imageCacheURL = containerURL.appendingPathComponent("ImageCache")
                if FileManager.default.fileExists(atPath: imageCacheURL.path) {
                    let imageContents = try FileManager.default.contentsOfDirectory(atPath: imageCacheURL.path)
                    print("🔍 Widget: Contenu du dossier ImageCache:")
                    for item in imageContents {
                        print("  - \(item)")
                    }
                } else {
                    print("❌ Widget: Dossier ImageCache n'existe pas")
                }
            } catch {
                print("❌ Widget: Erreur lecture container: \(error)")
            }
        } else {
            print("❌ Widget: Container App Group non trouvé")
        }
        
        let userLatitude = sharedDefaults.object(forKey: "widget_user_latitude") as? Double
        let userLongitude = sharedDefaults.object(forKey: "widget_user_longitude") as? Double
        let partnerLatitude = sharedDefaults.object(forKey: "widget_partner_latitude") as? Double
        let partnerLongitude = sharedDefaults.object(forKey: "widget_partner_longitude") as? Double
        let lastUpdateTimestamp = sharedDefaults.double(forKey: "widget_last_update")
        
        // Si pas de données importantes, retourner nil
        guard daysTotal > 0 || !duration.isEmpty else {
            print("❌ Widget: Pas de données importantes trouvées")
            return nil
        }
        
        print("✅ Widget: Création WidgetData avec les données récupérées")
        
        return WidgetData(
            daysTotal: daysTotal,
            duration: duration,
            daysToAnniversary: daysToAnniversary,
            distance: distance,
            message: message,
            userName: userName,
            partnerName: partnerName,
            userImageURL: userImageURL,
            partnerImageURL: partnerImageURL,
            userLatitude: userLatitude,
            userLongitude: userLongitude,
            partnerLatitude: partnerLatitude,
            partnerLongitude: partnerLongitude,
            lastUpdate: Date(timeIntervalSince1970: lastUpdateTimestamp)
        )
    }
}

// MARK: - Timeline Provider
struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> SimpleEntry {
        SimpleEntry(date: Date(), widgetData: .placeholder)
    }

    func getSnapshot(in context: Context, completion: @escaping (SimpleEntry) -> ()) {
        let widgetData = WidgetData.loadFromUserDefaults() ?? .placeholder
        let entry = SimpleEntry(date: Date(), widgetData: widgetData)
        completion(entry)
    }
    
    func getTimeline(in context: Context, completion: @escaping (Timeline<SimpleEntry>) -> ()) {
        let widgetData = WidgetData.loadFromUserDefaults() ?? .placeholder
        let currentDate = Date()
        
        // Créer plusieurs entrées pour mise à jour plus fréquente (toutes les minutes pour les secondes)
        var entries: [SimpleEntry] = []
        
        // Créer 60 entrées (une par minute pour la prochaine heure)
        for i in 0..<60 {
            let entryDate = Calendar.current.date(byAdding: .minute, value: i, to: currentDate)!
            let entry = SimpleEntry(date: entryDate, widgetData: widgetData)
            entries.append(entry)
        }
        
        // Programmer la prochaine mise à jour majeure dans 1 heure
        let nextUpdate = Calendar.current.date(byAdding: .hour, value: 1, to: currentDate)!
        let timeline = Timeline(entries: entries, policy: .after(nextUpdate))
        
        completion(timeline)
    }
}

// MARK: - Timeline Entry
struct SimpleEntry: TimelineEntry {
    let date: Date
    let widgetData: WidgetData
}

// MARK: - Widget Views
struct Love2LoveWidgetEntryView: View {
    var entry: Provider.Entry
    @Environment(\.widgetFamily) var family
    
    var body: some View {
        switch family {
        case .systemSmall:
            SmallWidgetView(data: entry.widgetData)
        case .systemMedium:
            MediumWidgetView(data: entry.widgetData)
        case .accessoryCircular:
            AccessoryCircularWidgetView(data: entry.widgetData)
        default:
            SmallWidgetView(data: entry.widgetData)
        }
    }
}

// MARK: - Time Counter Helper
extension WidgetData {
    func getTimeComponents() -> (days: Int, hours: Int, minutes: Int, seconds: Int) {
        let calendar = Calendar.current
        let now = Date()
        
        // Calculer la date de début à minuit, il y a X jours
        // Si on est ensemble depuis 248 jours, on calcule depuis minuit d'il y a 248 jours
        let daysAgo = calendar.date(byAdding: .day, value: -daysTotal, to: now) ?? now
        
        // Obtenir minuit de ce jour-là (début exact de la relation)
        let startOfRelationship = calendar.startOfDay(for: daysAgo)
        
        // Calculer le temps total écoulé depuis ce minuit
        let totalComponents = calendar.dateComponents([.day, .hour, .minute, .second], from: startOfRelationship, to: now)
        
        // Maintenant on a le total exact depuis le début
        let totalDays = totalComponents.day ?? 0
        let hours = totalComponents.hour ?? 0
        let minutes = totalComponents.minute ?? 0
        let seconds = totalComponents.second ?? 0
        
        return (
            days: totalDays,
            hours: hours,
            minutes: minutes,
            seconds: seconds
        )
    }
}

// MARK: - Small Widget (Photos + Compteur de jours simple)
struct SmallWidgetView: View {
    let data: WidgetData
    
    var body: some View {
        let timeComponents = data.getTimeComponents()
        
        VStack(spacing: 16) {
            // Photos de profil côte à côte (sans cœur)
            HStack(spacing: 12) {
                // Photo utilisateur
                ProfileCircleForWidget(
                    imageURL: data.userImageURL,
                    userName: data.userName,
                    size: 50
                )
                
                // Photo partenaire
                ProfileCircleForWidget(
                    imageURL: data.partnerImageURL,
                    userName: data.partnerName,
                    size: 50
                )
            }
            
            // Texte sur deux lignes
            VStack(spacing: 2) {
                Text("\(timeComponents.days) jours")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundColor(.white)
                
                Text("ensemble")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
            }
            .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .background(
            ZStack {
                // Fond noir avec effet de flou
                Color.black
                    .blur(radius: 10)
                    .opacity(0.8)
                
                // Overlay pour améliorer la lisibilité
                Color.black.opacity(0.3)
            }
        )
    }
}

// MARK: - Small Distance Widget (Photos + Distance en km)
struct SmallDistanceWidgetView: View {
    let data: WidgetData
    
    private var locationStatus: LocationStatus {
        let hasUserLocation = data.userLatitude != nil && data.userLongitude != nil
        let hasPartnerLocation = data.partnerLatitude != nil && data.partnerLongitude != nil
        
        if hasUserLocation && hasPartnerLocation {
            return .bothAvailable
        } else if !hasUserLocation && hasPartnerLocation {
            return .userMissing
        } else if hasUserLocation && !hasPartnerLocation {
            return .partnerMissing
        } else {
            return .bothMissing
        }
    }
    
    private enum LocationStatus {
        case bothAvailable
        case userMissing
        case partnerMissing
        case bothMissing
        
        var message: String {
            switch self {
            case .bothAvailable:
                return ""
            case .userMissing:
                return "Activez votre localisation"
            case .partnerMissing:
                return "Partenaire doit activer sa localisation"
            case .bothMissing:
                return "Activez vos localisations"
            }
        }
        
        var showDistance: Bool {
            return self == .bothAvailable
        }
    }
    
    var body: some View {
        VStack(spacing: 16) {
            // Photos de profil côte à côte (comme le widget jours ensemble)
            HStack(spacing: 12) {
                // Photo utilisateur
                ProfileCircleForWidget(
                    imageURL: data.userImageURL,
                    userName: data.userName,
                    size: 50
                )
                
                // Photo partenaire
                ProfileCircleForWidget(
                    imageURL: data.partnerImageURL,
                    userName: data.partnerName,
                    size: 50
                )
            }
            
            // Distance ou message d'erreur
            if locationStatus.showDistance {
                Text(data.distance ?? "? km")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            } else {
                Text(locationStatus.message)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .background(
            ZStack {
                // Fond noir avec effet de flou
                Color.black
                    .blur(radius: 10)
                    .opacity(0.8)
                
                // Overlay pour améliorer la lisibilité
                Color.black.opacity(0.3)
            }
        )
    }
}

// MARK: - Medium Widget (Compteur jours + Localisation miniature)
struct MediumWidgetView: View {
    let data: WidgetData
    
    private var locationStatus: LocationStatus {
        let hasUserLocation = data.userLatitude != nil && data.userLongitude != nil
        let hasPartnerLocation = data.partnerLatitude != nil && data.partnerLongitude != nil
        
        if hasUserLocation && hasPartnerLocation {
            return .bothAvailable
        } else if !hasUserLocation && hasPartnerLocation {
            return .userMissing
        } else if hasUserLocation && !hasPartnerLocation {
            return .partnerMissing
        } else {
            return .bothMissing
        }
    }
    
    private enum LocationStatus {
        case bothAvailable
        case userMissing
        case partnerMissing
        case bothMissing
        
        var message: String {
            switch self {
            case .bothAvailable:
                return ""
            case .userMissing:
                return "Activez votre localisation"
            case .partnerMissing:
                return "Partenaire doit activer sa localisation"
            case .bothMissing:
                return "Activez vos localisations"
            }
        }
        
        var showDistance: Bool {
            return self == .bothAvailable
        }
    }
    
    var body: some View {
        let timeComponents = data.getTimeComponents()
        
        HStack(spacing: 16) {
            // Section gauche : Compteur de jours avec nouveau design
            VStack(spacing: 8) {
                // Photos de profil miniatures
                HStack(spacing: 8) {
                    ProfileCircleForWidget(
                        imageURL: data.userImageURL,
                        userName: data.userName,
                        size: 35
                    )
                    
                    ProfileCircleForWidget(
                        imageURL: data.partnerImageURL,
                        userName: data.partnerName,
                        size: 35
                    )
                }
                
                // Texte sur deux lignes comme le petit widget
                VStack(spacing: 2) {
                    Text("\(timeComponents.days) jours")
                        .font(.system(size: 24, weight: .medium))
                        .foregroundColor(.white)
                    
                    Text("ensemble")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white.opacity(0.9))
                }
            }
            .frame(maxWidth: .infinity)
            
            // Séparateur vertical
            Rectangle()
                .fill(.white.opacity(0.2))
                .frame(width: 1)
                .padding(.vertical, 8)
            
            // Section droite : Distance simplifiée
            VStack(spacing: 12) {
                // Icône de cœur
                Image(systemName: "heart.fill")
                    .font(.system(size: 20))
                    .foregroundColor(.white.opacity(0.8))
                
                // Distance ou message d'erreur
                if locationStatus.showDistance {
                    VStack(spacing: 2) {
                        Text(data.distance ?? "? km")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                        
                        Text("de distance")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white.opacity(0.8))
                    }
                } else {
                    Text(locationStatus.message)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            ZStack {
                // Fond noir avec effet de flou
                Color.black
                    .blur(radius: 10)
                    .opacity(0.8)
                
                // Overlay pour améliorer la lisibilité
                Color.black.opacity(0.3)
            }
        )
    }
}

// MARK: - Profile Circle pour Widget (Version simplifiée)
struct ProfileCircleForWidget: View {
    let imageURL: String?
    let userName: String?
    let size: CGFloat
    
    var body: some View {
        ZStack {
            // Vérifier si on a une vraie image de profil
            if let imagePath = imageURL, 
               !imagePath.isEmpty,
               hasRealProfileImage(imagePath),
               let localImage = loadLocalImage(from: imagePath) {
                // Afficher l'image de profil réelle
                Image(uiImage: localImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: size, height: size)
                    .clipShape(Circle())
            } else {
                // Afficher l'initiale du nom avec fond transparent
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.15))
                        .frame(width: size, height: size)
                    
                    Text(userInitial)
                        .font(.system(size: size * 0.4, weight: .bold))
                        .foregroundColor(.white)
                }
            }
        }
    }
    
    private var userInitial: String {
        guard let userName = userName, !userName.isEmpty else {
            return "?"
        }
        return String(userName.prefix(1)).uppercased()
    }
    
    // MARK: - Helper pour vérifier si c'est une vraie image de profil
    private func hasRealProfileImage(_ imagePath: String) -> Bool {
        // Les noms de fichiers par défaut utilisés quand il n'y a pas d'image
        let defaultImageNames = [
            "user_profile_image.jpg",
            "partner_profile_image.jpg"
        ]
        
        print("🖼️ Widget: Vérification image profil - Path: \(imagePath)")
        
        // Vérifier si le fichier existe dans le dossier App Group
        guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.lyes.love2love") else {
            print("❌ Widget: Container App Group non trouvé")
            return false
        }
        
        let imageCacheURL = containerURL.appendingPathComponent("ImageCache")
        let imageURL = imageCacheURL.appendingPathComponent(imagePath)
        
        let fileExists = FileManager.default.fileExists(atPath: imageURL.path)
        print("🖼️ Widget: Fichier '\(imagePath)' existe: \(fileExists)")
        
        if fileExists {
            // Vérifier que le fichier n'est pas vide
            do {
                let attributes = try FileManager.default.attributesOfItem(atPath: imageURL.path)
                if let fileSize = attributes[.size] as? NSNumber {
                    let sizeInBytes = fileSize.intValue
                    print("🖼️ Widget: Taille du fichier: \(sizeInBytes) bytes")
                    
                    if sizeInBytes > 0 {
                        print("✅ Widget: Fichier valide trouvé")
                        return true
                    } else {
                        print("❌ Widget: Fichier vide")
                        return false
                    }
                }
            } catch {
                print("❌ Widget: Erreur lecture attributs fichier: \(error)")
                return false
            }
        }
        
        print("❌ Widget: Pas de fichier image valide trouvé")
        return false
    }
    
    // MARK: - Helper pour charger une image locale
    private func loadLocalImage(from imagePath: String) -> UIImage? {
        print("🖼️ Widget: loadLocalImage appelé avec: \(imagePath)")
        
        // Charger directement depuis le container App Group
        guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.lyes.love2love") else {
            print("❌ Widget: Container App Group non trouvé")
            return nil
        }
        
        let imageCacheURL = containerURL.appendingPathComponent("ImageCache")
        let imageURL = imageCacheURL.appendingPathComponent(imagePath)
        
        print("🔍 Widget: Tentative chargement depuis: \(imageURL.path)")
        
        // Vérifier d'abord si le fichier existe
        if !FileManager.default.fileExists(atPath: imageURL.path) {
            print("❌ Widget: Fichier n'existe pas: \(imageURL.path)")
            return nil
        }
        
        // Vérifier la taille du fichier
        do {
            let attributes = try FileManager.default.attributesOfItem(atPath: imageURL.path)
            if let fileSize = attributes[.size] as? NSNumber {
                print("🔍 Widget: Taille du fichier: \(fileSize.intValue) bytes")
                
                if fileSize.intValue == 0 {
                    print("❌ Widget: Fichier vide")
                    return nil
                }
            }
        } catch {
            print("❌ Widget: Erreur lecture attributs: \(error)")
        }
        
        // Tenter de charger l'image
        do {
            let imageData = try Data(contentsOf: imageURL)
            print("🔍 Widget: Données image chargées: \(imageData.count) bytes")
            
            if let image = UIImage(data: imageData) {
                print("✅ Widget: Image chargée avec succès: \(imagePath)")
                print("✅ Widget: Taille image: \(image.size)")
                return image
            } else {
                print("❌ Widget: Impossible de créer UIImage à partir des données")
                return nil
            }
        } catch {
            print("❌ Widget: Erreur chargement données: \(error)")
            return nil
        }
    }
}

// MARK: - Lock Screen Widgets

// MARK: - Accessory Circular Widget (widget circulaire - comme image 1)
struct AccessoryCircularWidgetView: View {
    let data: WidgetData
    
    var body: some View {
        let timeComponents = data.getTimeComponents()
        
        ZStack {
            // Fond noir avec effet de flou pour le widget circulaire
            Circle()
                .fill(Color.black.opacity(0.6))
                .blur(radius: 5)
            
            VStack(spacing: 4) {
                // Deux cœurs espacés (comme l'émoji 💕)
                ZStack {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .offset(x: -4, y: -2)
                    
                    Image(systemName: "heart.fill")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .offset(x: 4, y: 2)
                }
                
                // Nombre de jours
                Text("\(timeComponents.days)")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                
                // Label "days" en français
                Text("jours")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.white)
            }
        }
    }
}

// MARK: - Dashed Line pour Widget
struct DashedLineWidget: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.height / 2))
        path.addLine(to: CGPoint(x: rect.width, y: rect.height / 2))
        return path
    }
}

// MARK: - Widget Configuration
struct Love2LoveWidget: Widget {
    let kind: String = "Love2LoveWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: Provider()) { entry in
            Love2LoveWidgetEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Love2Love")
        .description("Suivez votre relation avec votre partenaire.")
        .supportedFamilies([
            .systemSmall, .systemMedium,           // Écran d'accueil
            .accessoryCircular                     // Lock screen - circulaire seulement
        ])
        .contentMarginsDisabledIfAvailable()
    }
}

// MARK: - Distance Widget Configuration
struct Love2LoveDistanceWidget: Widget {
    let kind: String = "Love2LoveDistanceWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: Provider()) { entry in
            SmallDistanceWidgetView(data: entry.widgetData)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Love2Love Distance")
        .description("Affichez la distance qui vous sépare de votre partenaire.")
        .supportedFamilies([
            .systemSmall                           // Petit widget uniquement
        ])
        .contentMarginsDisabledIfAvailable()
    }
}

// MARK: - Previews
#Preview(as: .systemSmall) {
    Love2LoveWidget()
} timeline: {
    SimpleEntry(date: .now, widgetData: .placeholder)
}

#Preview(as: .systemMedium) {
    Love2LoveWidget()
} timeline: {
    SimpleEntry(date: .now, widgetData: .placeholder)
}

#Preview(as: .accessoryCircular) {
    Love2LoveWidget()
} timeline: {
    SimpleEntry(date: .now, widgetData: .placeholder)
}

#Preview("Distance Widget", as: .systemSmall) {
    Love2LoveDistanceWidget()
} timeline: {
    SimpleEntry(date: .now, widgetData: .placeholder)
}

// MARK: - Previews
struct Love2LoveWidget_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            // Preview du widget classique Medium
            Love2LoveWidgetEntryView(entry: SimpleEntry(date: Date(), widgetData: .placeholder))
                .previewContext(WidgetPreviewContext(family: .systemMedium))
                .previewDisplayName("Widget Classique - Medium")
        }
    }
}

// MARK: - Extension pour compatibilité iOS 16/17 (Solution officielle Apple)
extension WidgetConfiguration {
    func contentMarginsDisabledIfAvailable() -> some WidgetConfiguration {
        #if compiler(>=5.9) // Xcode 15+
            if #available(iOSApplicationExtension 17.0, *) {
                return self.contentMarginsDisabled()
            } else {
                return self
            }
        #else
            return self
        #endif
    }
}

struct Love2LoveMapWidget: Widget {
    let kind: String = "Love2LoveMapWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: Provider()) { entry in
            MapDistanceWidgetEntryView(entry: entry)
                .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Distance Love2Love")
        .description("Ce Widget nécessite la localisation activée des deux partenaires.")
        .supportedFamilies([
            .accessoryRectangular                  // Lock screen uniquement
        ])
        .contentMarginsDisabledIfAvailable()
    }
}

// MARK: - Map Widget Entry View
struct MapDistanceWidgetEntryView: View {
    var entry: Provider.Entry
    @Environment(\.widgetFamily) var family
    
    var body: some View {
        switch family {
        case .accessoryRectangular:
            MapDistanceRectangularWidgetView(data: entry.widgetData)
        default:
            MapDistanceRectangularWidgetView(data: entry.widgetData)
        }
    }
}

// MARK: - Map Distance Lock Screen Widget

// Widget rectangulaire pour la distance
struct MapDistanceRectangularWidgetView: View {
    let data: WidgetData
    
    var body: some View {
        VStack(spacing: 6) {
            // Section haut : "Notre distance" centrée
            Text("Notre distance: \(data.distance ?? "? km")")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .center)
            
            // Section bas : Profils avec lignes pointillées centrés
            HStack(spacing: 0) {
                // Photo utilisateur avec ProfileCircleForWidget
                ProfileCircleForWidget(
                    imageURL: data.userImageURL,
                    userName: data.userName,
                    size: 28
                )
                
                // Ligne pointillée gauche
                DashedLineWidget()
                    .stroke(Color.white, style: StrokeStyle(lineWidth: 1, dash: [3, 2]))
                    .frame(width: 25, height: 1)
                
                // Cœur au centre
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.3))
                        .frame(width: 20, height: 20)
                    
                    Image(systemName: "heart.fill")
                        .font(.system(size: 10))
                        .foregroundColor(.white)
                }
                
                // Ligne pointillée droite
                DashedLineWidget()
                    .stroke(Color.white, style: StrokeStyle(lineWidth: 1, dash: [3, 2]))
                    .frame(width: 25, height: 1)
                
                // Photo partenaire avec ProfileCircleForWidget
                ProfileCircleForWidget(
                    imageURL: data.partnerImageURL,
                    userName: data.partnerName,
                    size: 28
                )
            }
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(
            ZStack {
                // Fond noir avec effet de flou pour le widget rectangulaire
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.black.opacity(0.6))
                    .blur(radius: 5)
                
                // Overlay pour améliorer la lisibilité
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.black.opacity(0.2))
            }
        )
    }
}
