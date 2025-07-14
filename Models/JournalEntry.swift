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
        self.title = data["title"] as? String ?? ""
        self.description = data["description"] as? String ?? ""
        
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
        
        // Désérialiser la localisation
        if let locationData = data["location"] as? [String: Any] {
            let latitude = locationData["latitude"] as? Double ?? 0.0
            let longitude = locationData["longitude"] as? Double ?? 0.0
            let address = locationData["address"] as? String
            let city = locationData["city"] as? String
            let country = locationData["country"] as? String
            
            self.location = JournalLocation(
                coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                address: address,
                city: city,
                country: country
            )
        } else {
            self.location = nil
        }
    }
    
    func toDictionary() -> [String: Any] {
        var dict: [String: Any] = [
            "title": title,
            "description": description,
            "eventDate": Timestamp(date: eventDate),
            "createdAt": Timestamp(date: createdAt),
            "updatedAt": Timestamp(date: updatedAt),
            "authorId": authorId,
            "authorName": authorName,
            "imageURL": imageURL as Any,
            "isShared": isShared,
            "partnerIds": partnerIds
        ]
        
        // Sérialiser la localisation
        if let location = location {
            dict["location"] = [
                "latitude": location.latitude,
                "longitude": location.longitude,
                "address": location.address as Any,
                "city": location.city as Any,
                "country": location.country as Any
            ]
        }
        
        return dict
    }
} 