import Foundation
import FirebaseAuth
import CoreLocation

// MARK: - User Location
struct UserLocation: Codable, Equatable {
    let latitude: Double
    let longitude: Double
    let address: String?
    let city: String?
    let country: String?
    let lastUpdated: Date
    
    init(coordinate: CLLocationCoordinate2D, address: String? = nil, city: String? = nil, country: String? = nil) {
        self.latitude = coordinate.latitude
        self.longitude = coordinate.longitude
        self.address = address
        self.city = city
        self.country = country
        self.lastUpdated = Date()
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
    
    // Calculer la distance avec une autre localisation
    func distance(to otherLocation: UserLocation) -> Double {
        let location1 = CLLocation(latitude: latitude, longitude: longitude)
        let location2 = CLLocation(latitude: otherLocation.latitude, longitude: otherLocation.longitude)
        return location1.distance(from: location2) / 1000 // Retourner en kilomètres
    }
}

struct AppUser: Codable, Identifiable, Equatable {
    let id: String
    var name: String
    var birthDate: Date
    var relationshipGoals: [String]
    var relationshipDuration: RelationshipDuration
    var relationshipImprovement: String?
    var questionMode: String?
    var partnerCode: String?
    var partnerId: String?
    var partnerConnectedAt: Date?
    var subscriptionInheritedFrom: String?
    var subscriptionInheritedAt: Date?
    var connectedPartnerCode: String?
    var connectedPartnerId: String?
    var connectedAt: Date?
    var isSubscribed: Bool
    var onboardingInProgress: Bool
    var relationshipStartDate: Date?
    var profileImageURL: String?
    var currentLocation: UserLocation?
    
    enum RelationshipDuration: String, Codable, CaseIterable {
        case none = "" // État non sélectionné
        case lessThanYear = "Moins d'un an"
        case oneToThreeYears = "Entre 1 et 3 ans"
        case moreThanThreeYears = "Plus de 3 ans"
        case notInRelationship = "Je ne suis pas en couple"
    }
    
    init(id: String = UUID().uuidString, name: String, birthDate: Date, relationshipGoals: [String] = [], relationshipDuration: RelationshipDuration = .none, relationshipImprovement: String? = nil, questionMode: String? = nil, partnerCode: String? = nil, partnerId: String? = nil, partnerConnectedAt: Date? = nil, subscriptionInheritedFrom: String? = nil, subscriptionInheritedAt: Date? = nil, connectedPartnerCode: String? = nil, connectedPartnerId: String? = nil, connectedAt: Date? = nil, isSubscribed: Bool = false, onboardingInProgress: Bool = false, relationshipStartDate: Date? = nil, profileImageURL: String? = nil, currentLocation: UserLocation? = nil) {
        self.id = id
        self.name = name
        self.birthDate = birthDate
        self.relationshipGoals = relationshipGoals
        self.relationshipDuration = relationshipDuration
        self.relationshipImprovement = relationshipImprovement
        self.questionMode = questionMode
        self.partnerCode = partnerCode
        self.partnerId = partnerId
        self.partnerConnectedAt = partnerConnectedAt
        self.subscriptionInheritedFrom = subscriptionInheritedFrom
        self.subscriptionInheritedAt = subscriptionInheritedAt
        self.connectedPartnerCode = connectedPartnerCode
        self.connectedPartnerId = connectedPartnerId
        self.connectedAt = connectedAt
        self.isSubscribed = isSubscribed
        self.onboardingInProgress = onboardingInProgress
        self.relationshipStartDate = relationshipStartDate
        self.profileImageURL = profileImageURL
        self.currentLocation = currentLocation
    }
    
    // MARK: - Equatable
    static func == (lhs: AppUser, rhs: AppUser) -> Bool {
        return lhs.id == rhs.id &&
               lhs.name == rhs.name &&
               lhs.birthDate == rhs.birthDate &&
               lhs.relationshipGoals == rhs.relationshipGoals &&
               lhs.relationshipDuration == rhs.relationshipDuration &&
               lhs.relationshipImprovement == rhs.relationshipImprovement &&
               lhs.questionMode == rhs.questionMode &&
               lhs.partnerCode == rhs.partnerCode &&
               lhs.partnerId == rhs.partnerId &&
               lhs.partnerConnectedAt == rhs.partnerConnectedAt &&
               lhs.subscriptionInheritedFrom == rhs.subscriptionInheritedFrom &&
               lhs.subscriptionInheritedAt == rhs.subscriptionInheritedAt &&
               lhs.connectedPartnerCode == rhs.connectedPartnerCode &&
               lhs.connectedPartnerId == rhs.connectedPartnerId &&
               lhs.connectedAt == rhs.connectedAt &&
               lhs.isSubscribed == rhs.isSubscribed &&
               lhs.onboardingInProgress == rhs.onboardingInProgress
    }
}

// MARK: - Type Alias pour compatibilité
typealias User = AppUser 