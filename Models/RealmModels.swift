import Foundation
import RealmSwift

// MARK: - Realm Models

class RealmQuestion: Object {
    @Persisted var id: String = UUID().uuidString
    @Persisted var text: String = ""
    @Persisted var category: String = ""
    @Persisted var isLiked: Bool = false
    @Persisted var createdAt: Date = Date()
    @Persisted var lastViewed: Date?
    
    override static func primaryKey() -> String? {
        return "id"
    }
    
    convenience init(question: Question, category: String) {
        self.init()
        self.text = question.text
        self.category = category
    }
    
    func toQuestion() -> Question {
        return Question(text: self.text, category: self.category)
    }
}

class RealmCategory: Object {
    @Persisted var title: String = ""
    @Persisted var questionsCount: Int = 0
    @Persisted var lastUpdated: Date = Date()
    @Persisted var isPopulated: Bool = false
    
    override static func primaryKey() -> String? {
        return "title"
    }
}

// MARK: - Favorites Model

class RealmFavoriteQuestion: Object {
    @Persisted var id: String = UUID().uuidString
    @Persisted var questionId: String = ""
    @Persisted var userId: String = ""
    @Persisted var categoryTitle: String = ""
    @Persisted var questionText: String = ""
    @Persisted var dateAdded: Date = Date()
    @Persisted var emoji: String = ""
    
    override static func primaryKey() -> String? {
        return "id"
    }
    
    convenience init(question: Question, category: QuestionCategory, userId: String) {
        self.init()
        self.questionId = question.id
        self.userId = userId
        self.categoryTitle = category.title
        self.questionText = question.text
        self.emoji = category.emoji
        self.dateAdded = Date()
    }
    
    func toFavoriteItem() -> FavoriteQuestion {
        return FavoriteQuestion(
            id: self.id,
            questionId: self.questionId,
            questionText: self.questionText,
            categoryTitle: self.categoryTitle,
            emoji: self.emoji,
            dateAdded: self.dateAdded
        )
    }
}

// MARK: - Question Cache Manager

@MainActor
class QuestionCacheManager: ObservableObject {
    private var realm: Realm?
    
    @Published var isLoading = false
    @Published var cacheStatus: [String: Bool] = [:]
    @Published var isRealmAvailable = false
    
    init() {
        initializeRealm()
    }
    
    private func initializeRealm() {
        do {
            // Configuration Realm optimisée pour la performance
            var config = Realm.Configuration.defaultConfiguration
            config.schemaVersion = 2 // Mise à jour pour les favoris
            config.migrationBlock = { migration, oldSchemaVersion in
                // Migration automatique pour les favoris
                if oldSchemaVersion < 2 {
                    // Ajouter les favoris si nécessaire
                }
            }
            config.shouldCompactOnLaunch = { (totalBytes: Int, usedBytes: Int) in
                let maxSize = 20 * 1024 * 1024 // RÉDUIT: 20MB au lieu de 50MB
                let usageRatio = Double(usedBytes) / Double(totalBytes)
                return totalBytes > maxSize && usageRatio < 0.5
            }
            
            self.realm = try Realm(configuration: config)
            self.isRealmAvailable = true
            print("RealmManager: Initialisé")
        } catch {
            print("⚠️ RealmManager: Erreur d'initialisation: \(error)")
            self.realm = nil
            self.isRealmAvailable = false
        }
    }
    
    // MARK: - Cache Questions
    
    func cacheQuestions(for category: String, questions: [Question]) {
        guard let realm = realm else {
            print("⚠️ RealmManager: Realm non disponible")
            return
        }
        
        // OPTIMISATION: Limiter le nombre de questions cachées par catégorie
        let limitedQuestions = Array(questions.prefix(100)) // MAX 100 questions par catégorie
        
        do {
            try realm.write {
                // Supprimer les anciennes questions de cette catégorie
                let oldQuestions = realm.objects(RealmQuestion.self).filter("category == %@", category)
                realm.delete(oldQuestions)
                
                // Ajouter les nouvelles questions (limitées)
                for question in limitedQuestions {
                    let realmQuestion = RealmQuestion(question: question, category: category)
                    realm.add(realmQuestion)
                }
                
                // Mettre à jour le statut de la catégorie
                let realmCategory = RealmCategory()
                realmCategory.title = category
                realmCategory.questionsCount = limitedQuestions.count
                realmCategory.isPopulated = true
                realmCategory.lastUpdated = Date()
                
                realm.add(realmCategory, update: .modified)
            }
            
            cacheStatus[category] = true
            print("RealmManager: \(limitedQuestions.count) questions cachées pour '\(category)'")
            
        } catch {
            print("⚠️ RealmManager: Erreur cache: \(error)")
        }
    }
    
    func getCachedQuestions(for category: String) -> [Question] {
        guard let realm = realm else {
            return []
        }
        
        let realmQuestions = realm.objects(RealmQuestion.self)
            .filter("category == %@", category)
            .sorted(byKeyPath: "createdAt")
        
        let questions = Array(realmQuestions.map { $0.toQuestion() })
        
        if !questions.isEmpty {
            print("RealmManager: \(questions.count) questions du cache pour '\(category)'")
        }
        
        return questions
    }
    
    func isCategoryPopulated(_ category: String) -> Bool {
        guard let realm = realm else {
            return false
        }
        
        let realmCategory = realm.object(ofType: RealmCategory.self, forPrimaryKey: category)
        return realmCategory?.isPopulated ?? false
    }
    
    // MARK: - Smart Loading (OPTIMISÉ)
    
    func getQuestionsWithSmartCache(for category: String, fallback: () -> [Question]) -> [Question] {
        // 1. Essayer le cache d'abord
        let cachedQuestions = getCachedQuestions(for: category)
        
        // Si le cache existe mais est incomplet (moins de 64 questions pour la catégorie gratuite),
        // rafraîchir pour garantir le bon fonctionnement du paywall/packs.
        if !cachedQuestions.isEmpty {
            if category == "en-couple" && cachedQuestions.count < 64 {
                print("⚠️ QuestionCacheManager: Cache incomplet (\(cachedQuestions.count) questions) pour 'en-couple' – rafraîchissement…")
            } else {
                return cachedQuestions
            }
        }
        
        // 2. Utiliser le nouveau QuestionDataManager
        let freshQuestions = QuestionDataManager.shared.loadQuestions(for: category)
        
        if !freshQuestions.isEmpty {
            cacheQuestions(for: category, questions: freshQuestions)
            return freshQuestions
        }
        
        // 3. Fallback seulement si le nouveau système échoue
        let fallbackQuestions = fallback()
        
        if !fallbackQuestions.isEmpty {
            cacheQuestions(for: category, questions: fallbackQuestions)
        }
        
        return fallbackQuestions
    }
    
    // MARK: - Preloading ULTRA-OPTIMISÉ
    
    func preloadAllCategories() {
        Task {
            isLoading = true
            
            // MIGRATION: Nettoyer l'ancien cache avec les titres localisés
            migrateOldCacheKeys()
            
            // OPTIMISATION EXTRÊME: Précharger seulement la première catégorie
            let priorityCategories = [
                ("EN COUPLE", "en-couple") // Seule la catégorie gratuite
            ]
            
            for (_, categoryId) in priorityCategories {
                if !isCategoryPopulated(categoryId) {
                    let questions = QuestionDataManager.shared.loadQuestions(for: categoryId)
                    // Limiter encore plus : seulement les 32 premières questions
                    let limitedQuestions = Array(questions.prefix(32))
                    cacheQuestions(for: categoryId, questions: limitedQuestions)
                }
            }
            
            isLoading = false
            print("RealmManager: Préchargement ultra-rapide terminé (\(priorityCategories.count) catégorie)")
        }
    }
    
    // NOUVEAU: Préchargement à la demande
    func preloadCategory(_ categoryId: String) {
        Task {
            guard !isCategoryPopulated(categoryId) else { return }
            
            let questions = QuestionDataManager.shared.loadQuestions(for: categoryId)
            if !questions.isEmpty {
                cacheQuestions(for: categoryId, questions: questions)
                print("RealmManager: Catégorie '\(categoryId)' préchargée à la demande")
            }
        }
    }
    
    // MARK: - Migration
    
    private func migrateOldCacheKeys() {
        guard let realm = realm else { return }
        
        // Anciennes clés (titres localisés français) -> nouvelles clés (IDs)
        let keyMigration = [
            "En couple": "en-couple",
            "Désirs Inavoués": "les-plus-hots",
            "Pour rigoler à deux": "pour-rire-a-deux",
            "Des questions profondes": "questions-profondes",
            "À travers la distance": "a-distance",
            "Tu préfères quoi ?": "tu-preferes",
            "Réparer notre amour": "mieux-ensemble",
            "En date": "pour-un-date"
        ]
        
        do {
            try realm.write {
                for (oldKey, newKey) in keyMigration {
                    // Migrer les questions
                    let oldQuestions = realm.objects(RealmQuestion.self).filter("category == %@", oldKey)
                    for question in oldQuestions {
                        question.category = newKey
                    }
                    
                    // Migrer les catégories
                    if let oldCategory = realm.object(ofType: RealmCategory.self, forPrimaryKey: oldKey) {
                        let newCategory = RealmCategory()
                        newCategory.title = newKey
                        newCategory.questionsCount = oldCategory.questionsCount
                        newCategory.isPopulated = oldCategory.isPopulated
                        newCategory.lastUpdated = oldCategory.lastUpdated
                        
                        realm.add(newCategory, update: .modified)
                        realm.delete(oldCategory)
                    }
                }
            }
            print("RealmManager: Migration du cache terminée")
        } catch {
            print("⚠️ RealmManager: Erreur migration: \(error)")
        }
    }
    
    // MARK: - Statistics
    
    func getCacheStatistics() -> (totalQuestions: Int, categories: Int, cacheSize: String) {
        guard let realm = realm else {
            return (0, 0, "0 bytes")
        }
        
        let totalQuestions = realm.objects(RealmQuestion.self).count
        let categories = realm.objects(RealmCategory.self).count
        
        let fileSize = realm.configuration.fileURL?.fileSize ?? 0
        let cacheSizeString = ByteCountFormatter.string(fromByteCount: Int64(fileSize), countStyle: .file)
        
        return (totalQuestions, categories, cacheSizeString)
    }
    
    // MARK: - Cleanup
    
    func clearCache() {
        guard let realm = realm else {
            return
        }
        
        do {
            try realm.write {
                realm.deleteAll()
            }
            cacheStatus.removeAll()
            print("RealmManager: Cache vidé")
        } catch {
            print("⚠️ RealmManager: Erreur nettoyage: \(error)")
        }
    }
    
    // NOUVELLE FONCTION: Nettoyage intelligent de la mémoire
    func optimizeMemoryUsage() {
        guard let realm = realm else { return }
        
        do {
            // Supprimer les questions anciennes et peu utilisées
            try realm.write {
                let oldQuestions = realm.objects(RealmQuestion.self)
                    .filter("lastViewed < %@ OR lastViewed == nil", Date().addingTimeInterval(-7*24*60*60)) // Plus de 7 jours
                
                if oldQuestions.count > 500 {
                    let toDelete = Array(oldQuestions.prefix(oldQuestions.count - 300))
                    realm.delete(toDelete)
                    print("RealmManager: \(toDelete.count) anciennes questions supprimées")
                }
            }
        } catch {
            print("⚠️ RealmManager: Erreur optimisation: \(error)")
        }
    }
}

// MARK: - Extensions

extension URL {
    var fileSize: Int {
        do {
            let resourceValues = try resourceValues(forKeys: [.fileSizeKey])
            return resourceValues.fileSize ?? 0
        } catch {
            return 0
        }
    }
}

class RealmUser: Object, Identifiable {
    @Persisted var id: String = UUID().uuidString
    @Persisted var name: String = ""
    @Persisted var birthDate: Date = Date()
    @Persisted var selectedGoals: List<String> = List<String>()
    @Persisted var relationshipDuration: String = ""
    @Persisted var relationshipImprovement: String = ""
    @Persisted var questionMode: String = ""
    @Persisted var isSubscribed: Bool = false
    @Persisted var partnerCode: String?
    @Persisted var partnerId: String?
    @Persisted var partnerConnectedAt: Date?
    @Persisted var subscriptionInheritedFrom: String?
    @Persisted var subscriptionInheritedAt: Date?
} 