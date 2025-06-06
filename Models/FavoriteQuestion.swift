import Foundation

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