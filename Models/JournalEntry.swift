import Foundation
import FirebaseFirestore
import CoreLocation

// MARK: - Location Data
struct JournalLocation: Codable, Equatable {
    let latitude: Double
    let longitude: Double
    let address: String?
    let city: String?
    let country: String?
    
    init(coordinate: CLLocationCoordinate2D, address: String? = nil, city: String? = nil, country: String? = nil) {
        self.latitude = coordinate.latitude
        self.longitude = coordinate.longitude
        self.address = address
        self.city = city
        self.country = country
    }
    
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
    
    var displayName: String {
        if let city = city, let country = country {
            return "\(city), \(country)"
        } else if let address = address {
            return address
        } else {
            return "Localisation"
        }
    }
}

struct JournalEntry: Codable, Identifiable, Equatable {
    let id: String
    var title: String
    var description: String
    var eventDate: Date
    var createdAt: Date
    var updatedAt: Date
    var authorId: String
    var authorName: String
    var imageURL: String?
    var localImagePath: String? // Pour les images non encore uploadées
    var isShared: Bool // Si visible par le partenaire
    var partnerIds: [String] // IDs des partenaires qui peuvent voir cette entrée
    var location: JournalLocation? // Nouvelle propriété de localisation
    
    enum CodingKeys: String, CodingKey {
        case id, title, description, eventDate, createdAt, updatedAt
        case authorId, authorName, imageURL, localImagePath, isShared, partnerIds, location
    }
    
    init(
        id: String = UUID().uuidString,
        title: String,
        description: String,
        eventDate: Date,
        authorId: String,
        authorName: String,
        imageURL: String? = nil,
        localImagePath: String? = nil,
        isShared: Bool = true,
        partnerIds: [String] = [],
        location: JournalLocation? = nil
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.eventDate = eventDate
        self.createdAt = Date()
        self.updatedAt = Date()
        self.authorId = authorId
        self.authorName = authorName
        self.imageURL = imageURL
        self.localImagePath = localImagePath
        self.isShared = isShared
        self.partnerIds = partnerIds
        self.location = location
    }
    
    // MARK: - Computed Properties
    
    var formattedEventDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        formatter.locale = Locale.current
        return formatter.string(from: eventDate)
    }
    
    var dayOfMonth: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "d"
        formatter.locale = Locale.current
        return formatter.string(from: eventDate)
    }
    
    var monthAbbreviation: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM"
        formatter.locale = Locale.current
        return formatter.string(from: eventDate)
    }
    
    var monthYear: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM yyyy"
        formatter.locale = Locale.current
        return formatter.string(from: eventDate)
    }
    
    var hasImage: Bool {
        return imageURL != nil || localImagePath != nil
    }
    
    // MARK: - Equatable
    static func == (lhs: JournalEntry, rhs: JournalEntry) -> Bool {
        return lhs.id == rhs.id &&
               lhs.title == rhs.title &&
               lhs.description == rhs.description &&
               lhs.eventDate == rhs.eventDate &&
               lhs.imageURL == rhs.imageURL
    }
}

// MARK: - Firebase Extensions
extension JournalEntry {
    init?(from document: DocumentSnapshot) {
        guard let data = document.data() else { return nil }
        
        self.id = document.documentID
        // 🔐 DÉCHIFFREMENT HYBRIDE des métadonnées (Phase 2)
        self.title = LocationEncryptionService.readMessageFromFirestore(
            data.mapKeys { key in
                return key == "encryptedTitle" ? "encryptedText" : key.replacingOccurrences(of: "title", with: "text")
            }
        ) ?? data["title"] as? String ?? ""
        
        self.description = LocationEncryptionService.readMessageFromFirestore(
            data.mapKeys { key in
                return key == "encryptedDescription" ? "encryptedText" : key.replacingOccurrences(of: "description", with: "text")
            }
        ) ?? data["description"] as? String ?? ""
        
        if let timestamp = data["eventDate"] as? Timestamp {
            self.eventDate = timestamp.dateValue()
        } else {
            return nil
        }
        
        if let timestamp = data["createdAt"] as? Timestamp {
            self.createdAt = timestamp.dateValue()
        } else {
            self.createdAt = Date()
        }
        
        if let timestamp = data["updatedAt"] as? Timestamp {
            self.updatedAt = timestamp.dateValue()
        } else {
            self.updatedAt = Date()
        }
        
        self.authorId = data["authorId"] as? String ?? ""
        self.authorName = data["authorName"] as? String ?? ""
        self.imageURL = data["imageURL"] as? String
        self.localImagePath = nil // Ne pas persister les chemins locaux
        self.isShared = data["isShared"] as? Bool ?? true
        self.partnerIds = data["partnerIds"] as? [String] ?? []
        
        // 🔄 MIGRATION HYBRIDE - Désérialiser la localisation (Nouveau + Ancien format)
        if let locationData = LocationEncryptionService.readLocation(from: data) {
            let coordinate = locationData.toCLLocation().coordinate
            
            // Récupérer les métadonnées additionnelles si disponibles
            var address: String?
            var city: String?
            var country: String?
            
            // 🔧 NOUVEAU: Essayer de récupérer depuis le nouveau format d'abord
            address = data["locationAddress"] as? String
            city = data["locationCity"] as? String
            country = data["locationCountry"] as? String
            
            // Si pas trouvé, essayer l'ancien format comme fallback
            if city == nil && country == nil {
                if let legacyLocation = data["location"] as? [String: Any] {
                    address = address ?? (legacyLocation["address"] as? String)
                    city = legacyLocation["city"] as? String
                    country = legacyLocation["country"] as? String
                }
            }
            
            self.location = JournalLocation(
                coordinate: coordinate,
                address: address,
                city: city,
                country: country
            )
            
            print("✅ JournalEntry: Localisation chargée (chiffré: \(locationData.isEncrypted))")
        } else {
            self.location = nil
        }
    }
    
    func toDictionary() -> [String: Any] {
        var dict: [String: Any] = [
            "eventDate": Timestamp(date: eventDate),
            "createdAt": Timestamp(date: createdAt),
            "updatedAt": Timestamp(date: updatedAt),
            "authorId": authorId,
            "authorName": authorName,
            "imageURL": imageURL as Any,
            "isShared": isShared,
            "partnerIds": partnerIds
        ]
        
        // 🔐 CHIFFREMENT HYBRIDE des métadonnées sensibles (Phase 2)
        let encryptedTitleData = LocationEncryptionService.processMessageForStorage(title)
        dict.merge(encryptedTitleData.mapKeys { key in
            return key == "encryptedText" ? "encryptedTitle" : key.replacingOccurrences(of: "text", with: "title")
        }) { (_, new) in new }
        
        let encryptedDescriptionData = LocationEncryptionService.processMessageForStorage(description)
        dict.merge(encryptedDescriptionData.mapKeys { key in
            return key == "encryptedText" ? "encryptedDescription" : key.replacingOccurrences(of: "text", with: "description")
        }) { (_, new) in new }
        
        // 🔐 ÉCRITURE HYBRIDE - Nouveau format chiffré + Legacy pour transition
        if let location = location {
            let clLocation = CLLocation(latitude: location.latitude, longitude: location.longitude)
            
            // Nouveau format chiffré
            let encryptedLocationData = LocationEncryptionService.processLocationForStorage(clLocation)
            if !encryptedLocationData.isEmpty {
                dict.merge(encryptedLocationData) { (_, new) in new }
            }
            
            // Métadonnées additionnelles (non sensibles)
            if let address = location.address {
                dict["locationAddress"] = address
            }
            if let city = location.city {
                dict["locationCity"] = city
            }
            if let country = location.country {
                dict["locationCountry"] = country
            }
            
            print("✅ JournalEntry: Localisation sauvegardée avec chiffrement hybride")
        }
        
        return dict
    }
} 