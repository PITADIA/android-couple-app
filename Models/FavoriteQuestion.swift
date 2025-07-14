import Foundation
import FirebaseFirestore

struct FavoriteQuestion: Identifiable, Codable {
    let id: String
    let questionId: String
    let questionText: String
    let categoryTitle: String
    let emoji: String
    let dateAdded: Date
    
    init(id: String = UUID().uuidString, questionId: String, questionText: String, categoryTitle: String, emoji: String, dateAdded: Date = Date()) {
        self.id = id
        self.questionId = questionId
        self.questionText = questionText
        self.categoryTitle = categoryTitle
        self.emoji = emoji
        self.dateAdded = dateAdded
    }
    
    // Convertir en Question standard
    func toQuestion() -> Question {
        return Question(id: questionId, text: questionText, category: categoryTitle)
    }
}

// MARK: - SharedFavoriteQuestion (Firestore Model)

struct SharedFavoriteQuestion: Codable, Identifiable, Equatable {
    let id: String
    var questionId: String
    var questionText: String
    var categoryTitle: String
    var emoji: String
    var dateAdded: Date
    var createdAt: Date
    var updatedAt: Date
    var authorId: String
    var authorName: String
    var isShared: Bool // Si visible par le partenaire
    var partnerIds: [String] // IDs des partenaires qui peuvent voir ce favori
    
    enum CodingKeys: String, CodingKey {
        case id, questionId, questionText, categoryTitle, emoji, dateAdded
        case createdAt, updatedAt, authorId, authorName, isShared, partnerIds
    }
    
    init(
        id: String = UUID().uuidString,
        questionId: String,
        questionText: String,
        categoryTitle: String,
        emoji: String,
        dateAdded: Date = Date(),
        authorId: String,
        authorName: String,
        isShared: Bool = true,
        partnerIds: [String] = []
    ) {
        self.id = id
        self.questionId = questionId
        self.questionText = questionText
        self.categoryTitle = categoryTitle
        self.emoji = emoji
        self.dateAdded = dateAdded
        self.createdAt = Date()
        self.updatedAt = Date()
        self.authorId = authorId
        self.authorName = authorName
        self.isShared = isShared
        self.partnerIds = partnerIds
    }
    
    // MARK: - Computed Properties
    
    var formattedDateAdded: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        formatter.locale = Locale.current
        return formatter.string(from: dateAdded)
    }
    
    // Convertir en FavoriteQuestion local
    func toLocalFavorite() -> FavoriteQuestion {
        return FavoriteQuestion(
            id: id,
            questionId: questionId,
            questionText: questionText,
            categoryTitle: categoryTitle,
            emoji: emoji,
            dateAdded: dateAdded
        )
    }
    
    // Convertir en Question standard
    func toQuestion() -> Question {
        return Question(id: questionId, text: questionText, category: categoryTitle)
    }
    
    // MARK: - Equatable
    static func == (lhs: SharedFavoriteQuestion, rhs: SharedFavoriteQuestion) -> Bool {
        return lhs.id == rhs.id &&
               lhs.questionId == rhs.questionId &&
               lhs.questionText == rhs.questionText &&
               lhs.authorId == rhs.authorId
    }
}

// MARK: - Firebase Extensions
extension SharedFavoriteQuestion {
    init?(from document: DocumentSnapshot) {
        guard let data = document.data() else { return nil }
        
        self.id = document.documentID
        self.questionId = data["questionId"] as? String ?? ""
        self.questionText = data["questionText"] as? String ?? ""
        self.categoryTitle = data["categoryTitle"] as? String ?? ""
        self.emoji = data["emoji"] as? String ?? ""
        
        if let timestamp = data["dateAdded"] as? Timestamp {
            self.dateAdded = timestamp.dateValue()
        } else {
            self.dateAdded = Date()
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
        self.isShared = data["isShared"] as? Bool ?? true
        self.partnerIds = data["partnerIds"] as? [String] ?? []
    }
    
    func toDictionary() -> [String: Any] {
        return [
            "questionId": questionId,
            "questionText": questionText,
            "categoryTitle": categoryTitle,
            "emoji": emoji,
            "dateAdded": Timestamp(date: dateAdded),
            "createdAt": Timestamp(date: createdAt),
            "updatedAt": Timestamp(date: updatedAt),
            "authorId": authorId,
            "authorName": authorName,
            "isShared": isShared,
            "partnerIds": partnerIds
        ]
    }
} 