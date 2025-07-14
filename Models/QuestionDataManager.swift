import Foundation

/// Gestionnaire optimisé pour les données de questions
/// Utilise les String Catalogs (.xcstrings) avec NSLocalizedString
class QuestionDataManager: ObservableObject {
    static let shared = QuestionDataManager()
    
    @Published var isLoading = false
    private var questionsCache: [String: [Question]] = [:]
    
    private init() {}
    
    // MARK: - Question Loading
    
    /// Charge les questions pour une catégorie spécifique
    func loadQuestions(for categoryId: String, language: String = QuestionDataManager.getCurrentLanguage()) -> [Question] {
        let cacheKey = "\(categoryId)_\(language)"
        
        // Vérifier le cache d'abord
        if let cachedQuestions = questionsCache[cacheKey] {
            return cachedQuestions
        }
        
        // Charger depuis les String Catalogs
        let questions = loadQuestionsFromStringCatalogs(categoryId: categoryId)
        questionsCache[cacheKey] = questions
        
        return questions
    }
    
    /// Récupère une chaîne localisée avec un fallback en anglais si introuvable dans la langue courante
    private func localizedString(_ key: String, tableName: String) -> String {
        // Première tentative avec la langue courante
        let primary = NSLocalizedString(key, tableName: tableName, bundle: .main, value: key, comment: "")
        if primary != key {
            return primary
        }
        // Fallback : essayer dans le bundle Anglais si disponible
        if let enPath = Bundle.main.path(forResource: "en", ofType: "lproj"),
           let enBundle = Bundle(path: enPath) {
            let secondary = NSLocalizedString(key, tableName: tableName, bundle: enBundle, value: key, comment: "")
            return secondary
        }
        return key // dernier recours : retourner la clé
    }

    /// Charge les questions depuis les String Catalogs (.xcstrings)
    private func loadQuestionsFromStringCatalogs(categoryId: String) -> [Question] {
        // Mapping des IDs de catégorie vers les préfixes des clés
        //  et vers le nom du String Catalog (tableName)
        let mapping: [String: (prefix: String, table: String, start: Int)] = [
            "en-couple": ("ec_", "EnCouple", 2),
            "les-plus-hots": ("lph_", "LesPlus Hots", 2),
            "pour-rire-a-deux": ("prad_", "PourRire", 2),
            "questions-profondes": ("qp_", "QuestionsProfondes", 2),
            "a-distance": ("ad_", "ADistance", 2),
            "tu-preferes": ("tp_", "TuPreferes", 2),
            "mieux-ensemble": ("me_", "MieuxEnsemble", 2),
            "pour-un-date": ("pud_", "PourUnDate", 2)
        ]
        
        guard let info = mapping[categoryId] else {
            print("⚠️ QuestionDataManager: Catégorie inconnue: \(categoryId)")
            return []
        }
        
        let keyPrefix = info.prefix
        let tableName = info.table
        let startIndex = info.start
        
        // Générer les questions en utilisant la fonction helper avec fallback
        var questions: [Question] = []
        for i in startIndex...300 {
            let key = "\(keyPrefix)\(i)"
            let localizedText = localizedString(key, tableName: tableName)
            if localizedText != key && !localizedText.isEmpty {
                questions.append(Question(id: key, text: localizedText, category: categoryId))
            } else if i > startIndex + 10 && questions.isEmpty {
                break
            }
        }
        print("✅ QuestionDataManager: \(questions.count) questions chargées pour \(categoryId)")
        return questions
    }
    
    // MARK: - Utility Methods
    
    /// Obtient la langue actuelle de l'appareil
    private static func getCurrentLanguage() -> String {
        return Locale.preferredLanguages.first?.prefix(2).lowercased() == "fr" ? "fr" : "en"
    }
    
    /// Précharge toutes les catégories essentielles
    func preloadEssentialCategories() {
        let essentialCategories = ["en-couple"] // Seulement la catégorie gratuite
        let currentLanguage = QuestionDataManager.getCurrentLanguage()
        
        Task {
            isLoading = true
            
            for categoryId in essentialCategories {
                _ = loadQuestions(for: categoryId, language: currentLanguage)
            }
            
            DispatchQueue.main.async {
                self.isLoading = false
                print("QuestionDataManager: Catégories essentielles préchargées")
            }
        }
    }
    
    /// Vide le cache (utile pour les changements de langue)
    func clearCache() {
        questionsCache.removeAll()
    }
}

// MARK: - Data Models

struct QuestionData: Codable {
    let category: String
    let questions: [QuestionJSON]
}

struct QuestionJSON: Codable {
    let id: String
    let text: String
} 