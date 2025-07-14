import Foundation

struct QuestionCategory: Identifiable, Codable {
    var id: String
    let title: String
    let subtitle: String
    let emoji: String
    let gradientColors: [String]
    let isPremium: Bool
    
    init(id: String = UUID().uuidString, title: String, subtitle: String, emoji: String, gradientColors: [String], isPremium: Bool) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.emoji = emoji
        self.gradientColors = gradientColors
        self.isPremium = isPremium
    }
    
    static let categories: [QuestionCategory] = [
        QuestionCategory(
            id: "en-couple",
            title: NSLocalizedString("category_en_couple_title", comment: "Title for In a Relationship category"),
            subtitle: NSLocalizedString("category_en_couple_subtitle", comment: "Subtitle for In a Relationship category"),
            emoji: "💞",
            gradientColors: ["#E91E63", "#F06292"],
            isPremium: false
        ),
        QuestionCategory(
            id: "les-plus-hots",
            title: NSLocalizedString("category_desirs_inavoues_title", comment: "Title for Unspoken Desires category"),
            subtitle: NSLocalizedString("category_desirs_inavoues_subtitle", comment: "Subtitle for Unspoken Desires category"),
            emoji: "🌶️",
            gradientColors: ["#FF6B35", "#F7931E"],
            isPremium: true
        ),
        QuestionCategory(
            id: "pour-rire-a-deux",
            title: NSLocalizedString("category_pour_rire_title", comment: "Title for Laugh Together category"),
            subtitle: NSLocalizedString("category_pour_rire_subtitle", comment: "Subtitle for Laugh Together category"),
            emoji: "😂",
            gradientColors: ["#FFD700", "#FFA500"],
            isPremium: true
        ),
        QuestionCategory(
            id: "questions-profondes",
            title: NSLocalizedString("category_questions_profondes_title", comment: "Title for Deep Questions category"),
            subtitle: NSLocalizedString("category_questions_profondes_subtitle", comment: "Subtitle for Deep Questions category"),
            emoji: "✨",
            gradientColors: ["#FFD700", "#FFA500"],
            isPremium: true
        ),
        QuestionCategory(
            id: "a-distance",
            title: NSLocalizedString("category_a_distance_title", comment: "Title for Through Distance category"),
            subtitle: NSLocalizedString("category_a_distance_subtitle", comment: "Subtitle for Through Distance category"),
            emoji: "✈️",
            gradientColors: ["#00BCD4", "#26C6DA"],
            isPremium: true
        ),
        QuestionCategory(
            id: "tu-preferes",
            title: NSLocalizedString("category_tu_preferes_title", comment: "Title for Would You Rather category"),
            subtitle: NSLocalizedString("category_tu_preferes_subtitle", comment: "Subtitle for Would You Rather category"),
            emoji: "🤍",
            gradientColors: ["#9B59B6", "#8E44AD"],
            isPremium: true
        ),
        QuestionCategory(
            id: "mieux-ensemble",
            title: NSLocalizedString("category_mieux_ensemble_title", comment: "Title for Healing Our Love category"),
            subtitle: NSLocalizedString("category_mieux_ensemble_subtitle", comment: "Subtitle for Healing Our Love category"),
            emoji: "💌",
            gradientColors: ["#673AB7", "#9C27B0"],
            isPremium: true
        ),
        QuestionCategory(
            id: "pour-un-date",
            title: NSLocalizedString("category_pour_un_date_title", comment: "Title for On a Date category"),
            subtitle: NSLocalizedString("category_pour_un_date_subtitle", comment: "Subtitle for On a Date category"),
            emoji: "🍸",
            gradientColors: ["#3498DB", "#2980B9"],
            isPremium: true
        )
    ]
}

struct Question: Identifiable, Codable {
    var id: String
    let text: String
    let category: String
    
    init(id: String = UUID().uuidString, text: String, category: String) {
        self.id = id
        self.text = text
        self.category = category
    }

    // OPTIMISÉ: Plus besoin de propriété calculée complexe
    var localizedText: String {
        return text // Le texte est déjà dans la bonne langue depuis les fichiers JSON
    }
}

// MARK: - Question Loading Helper

extension QuestionCategory {
    /// Charge les questions pour cette catégorie depuis les fichiers JSON
    func loadQuestions() -> [Question] {
        return QuestionDataManager.shared.loadQuestions(for: self.id)
    }
    
    /// Obtient le nombre total de questions pour cette catégorie
    func getQuestionCount() -> Int {
        return loadQuestions().count
    }
} 