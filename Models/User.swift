import Foundation

struct User: Codable, Identifiable, Equatable {
    let id: String
    var name: String
    var birthDate: Date
    var relationshipGoals: [String]
    var relationshipDuration: RelationshipDuration
    var partnerCode: String?
    var isSubscribed: Bool
    var onboardingInProgress: Bool
    
    enum RelationshipDuration: String, Codable, CaseIterable {
        case lessThanYear = "Moins d'un an"
        case oneToThreeYears = "Entre 1 et 3 ans"
        case moreThanThreeYears = "Plus de 3 ans"
        case notInRelationship = "Je ne suis pas en couple"
    }
    
    init(id: String = UUID().uuidString, name: String, birthDate: Date, relationshipGoals: [String] = [], relationshipDuration: RelationshipDuration = .notInRelationship, partnerCode: String? = nil, isSubscribed: Bool = false, onboardingInProgress: Bool = false) {
        self.id = id
        self.name = name
        self.birthDate = birthDate
        self.relationshipGoals = relationshipGoals
        self.relationshipDuration = relationshipDuration
        self.partnerCode = partnerCode
        self.isSubscribed = isSubscribed
        self.onboardingInProgress = onboardingInProgress
    }
    
    // MARK: - Equatable
    static func == (lhs: User, rhs: User) -> Bool {
        return lhs.id == rhs.id &&
               lhs.name == rhs.name &&
               lhs.birthDate == rhs.birthDate &&
               lhs.relationshipGoals == rhs.relationshipGoals &&
               lhs.relationshipDuration == rhs.relationshipDuration &&
               lhs.partnerCode == rhs.partnerCode &&
               lhs.isSubscribed == rhs.isSubscribed &&
               lhs.onboardingInProgress == rhs.onboardingInProgress
    }
} 