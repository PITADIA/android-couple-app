import Foundation

// MARK: - DailyChallenge Model

struct DailyChallenge: Codable, Identifiable, Hashable {
    let id: String
    let challengeKey: String  // "daily_challenge_1", "daily_challenge_2", etc.
    let challengeDay: Int
    let scheduledDate: Date
    let coupleId: String
    var isCompleted: Bool = false
    var completedAt: Date?
    
    enum CodingKeys: String, CodingKey {
        case id
        case challengeKey
        case challengeDay
        case scheduledDate
        case coupleId
        case isCompleted
        case completedAt
    }
    
    init(id: String = UUID().uuidString, challengeKey: String, challengeDay: Int, scheduledDate: Date, coupleId: String, isCompleted: Bool = false, completedAt: Date? = nil) {
        self.id = id
        self.challengeKey = challengeKey
        self.challengeDay = challengeDay
        self.scheduledDate = scheduledDate
        self.coupleId = coupleId
        self.isCompleted = isCompleted
        self.completedAt = completedAt
    }
    
    // MARK: - Computed Properties
    
    /// Retourne le texte localisé du défi
    var localizedText: String {
        return challengeKey.localized(tableName: "DailyChallenges")
    }
    
    /// Génère l'ID unique pour Firestore
    static func generateId(coupleId: String, date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return "\(coupleId)_\(formatter.string(from: date))"
    }
    
    // MARK: - Hashable
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    static func == (lhs: DailyChallenge, rhs: DailyChallenge) -> Bool {
        return lhs.id == rhs.id
    }
}

// MARK: - SavedChallenge Model

struct SavedChallenge: Codable, Identifiable, Hashable {
    let id: String
    let challengeKey: String
    let challengeDay: Int
    let savedAt: Date
    let userId: String
    
    enum CodingKeys: String, CodingKey {
        case id
        case challengeKey
        case challengeDay
        case savedAt
        case userId
    }
    
    init(id: String = UUID().uuidString, challengeKey: String, challengeDay: Int, savedAt: Date = Date(), userId: String) {
        self.id = id
        self.challengeKey = challengeKey
        self.challengeDay = challengeDay
        self.savedAt = savedAt
        self.userId = userId
    }
    
    // MARK: - Computed Properties
    
    /// Retourne le texte localisé du défi sauvegardé
    var localizedText: String {
        return challengeKey.localized(tableName: "DailyChallenges")
    }
    
    /// Génère l'ID unique pour Firestore
    static func generateId(userId: String, challengeKey: String) -> String {
        return "\(userId)_\(challengeKey)"
    }
    
    // MARK: - Hashable
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    static func == (lhs: SavedChallenge, rhs: SavedChallenge) -> Bool {
        return lhs.id == rhs.id
    }
}

// MARK: - DailyChallengeSettings

struct DailyChallengeSettings: Codable {
    let coupleId: String
    let startDate: Date
    let timezone: String
    var currentDay: Int
    let createdAt: Date
    var lastVisitDate: Date
    
    enum CodingKeys: String, CodingKey {
        case coupleId
        case startDate
        case timezone
        case currentDay
        case createdAt
        case lastVisitDate
    }
    
    init(coupleId: String, startDate: Date, timezone: String = "Europe/Paris", currentDay: Int = 1, createdAt: Date = Date(), lastVisitDate: Date = Date()) {
        self.coupleId = coupleId
        self.startDate = startDate
        self.timezone = timezone
        self.currentDay = currentDay
        self.createdAt = createdAt
        self.lastVisitDate = lastVisitDate
    }
}