import Foundation
import RealmSwift
import Combine

class FavoritesService: ObservableObject {
    @Published var favoriteQuestions: [FavoriteQuestion] = []
    @Published var isLoading = false
    
    private var realm: Realm?
    private var currentUserId: String?
    
    init() {
        initializeRealm()
    }
    
    private func initializeRealm() {
        do {
            var config = Realm.Configuration.defaultConfiguration
            config.schemaVersion = 2 // Incrémenté pour les favoris
            config.migrationBlock = { migration, oldSchemaVersion in
                // Migration automatique pour les nouveaux modèles
                if oldSchemaVersion < 2 {
                    // Ajouter les favoris si nécessaire
                }
            }
            
            self.realm = try Realm(configuration: config)
            print("🔥 FavoritesService: Realm initialisé")
        } catch {
            print("❌ FavoritesService: Erreur Realm: \(error)")
            self.realm = nil
        }
    }
    
    // MARK: - User Management
    
    @MainActor
    func setCurrentUser(_ userId: String) {
        self.currentUserId = userId
        loadFavorites()
        print("🔥 FavoritesService: Utilisateur défini: \(userId)")
    }
    
    // MARK: - Core Favorites Functions
    
    @MainActor
    func toggleFavorite(question: Question, category: QuestionCategory) {
        guard let userId = currentUserId else {
            print("❌ FavoritesService: Pas d'utilisateur connecté")
            return
        }
        
        if isFavorite(questionId: question.id) {
            removeFavorite(questionId: question.id)
        } else {
            addFavorite(question: question, category: category, userId: userId)
        }
    }
    
    @MainActor
    func addFavorite(question: Question, category: QuestionCategory, userId: String) {
        guard let realm = realm else {
            print("❌ FavoritesService: Realm non disponible")
            return
        }
        
        // Vérifier si déjà en favoris
        if isFavorite(questionId: question.id) {
            print("⚠️ FavoritesService: Question déjà en favoris")
            return
        }
        
        do {
            try realm.write {
                let favorite = RealmFavoriteQuestion(question: question, category: category, userId: userId)
                realm.add(favorite)
            }
            
            loadFavorites() // Recharger la liste
            print("✅ FavoritesService: Question ajoutée aux favoris: \(question.text.prefix(50))...")
            
        } catch {
            print("❌ FavoritesService: Erreur ajout favori: \(error)")
        }
    }
    
    @MainActor
    func removeFavorite(questionId: String) {
        guard let realm = realm, let userId = currentUserId else {
            print("❌ FavoritesService: Realm ou utilisateur non disponible")
            return
        }
        
        do {
            try realm.write {
                let favorites = realm.objects(RealmFavoriteQuestion.self)
                    .filter("questionId == %@ AND userId == %@", questionId, userId)
                
                realm.delete(favorites)
            }
            
            loadFavorites() // Recharger la liste
            print("✅ FavoritesService: Question retirée des favoris")
            
        } catch {
            print("❌ FavoritesService: Erreur suppression favori: \(error)")
        }
    }
    
    func isFavorite(questionId: String) -> Bool {
        guard let realm = realm, let userId = currentUserId else {
            return false
        }
        
        let favorite = realm.objects(RealmFavoriteQuestion.self)
            .filter("questionId == %@ AND userId == %@", questionId, userId)
            .first
        
        return favorite != nil
    }
    
    // MARK: - Data Loading
    
    @MainActor
    func loadFavorites() {
        guard let realm = realm, let userId = currentUserId else {
            favoriteQuestions = []
            return
        }
        
        let realmFavorites = realm.objects(RealmFavoriteQuestion.self)
            .filter("userId == %@", userId)
            .sorted(byKeyPath: "dateAdded", ascending: false)
        
        favoriteQuestions = Array(realmFavorites.map { $0.toFavoriteItem() })
        
        print("🔥 FavoritesService: \(favoriteQuestions.count) favoris chargés")
    }
    
    // MARK: - Filtering and Sorting
    
    func getFavoritesByCategory() -> [String: [FavoriteQuestion]] {
        var grouped: [String: [FavoriteQuestion]] = [:]
        
        for favorite in favoriteQuestions {
            if grouped[favorite.categoryTitle] == nil {
                grouped[favorite.categoryTitle] = []
            }
            grouped[favorite.categoryTitle]?.append(favorite)
        }
        
        return grouped
    }
    
    func getRecentFavorites(limit: Int = 10) -> [FavoriteQuestion] {
        return Array(favoriteQuestions.prefix(limit))
    }
    
    func searchFavorites(query: String) -> [FavoriteQuestion] {
        guard !query.isEmpty else { return favoriteQuestions }
        
        return favoriteQuestions.filter { favorite in
            favorite.questionText.localizedCaseInsensitiveContains(query) ||
            favorite.categoryTitle.localizedCaseInsensitiveContains(query)
        }
    }
    
    // MARK: - Statistics
    
    func getFavoritesCount() -> Int {
        return favoriteQuestions.count
    }
    
    func getFavoritesCountByCategory() -> [String: Int] {
        let grouped = getFavoritesByCategory()
        return grouped.mapValues { $0.count }
    }
    
    // MARK: - Cleanup
    
    @MainActor
    func clearAllFavorites() {
        guard let realm = realm, let userId = currentUserId else {
            return
        }
        
        do {
            try realm.write {
                let userFavorites = realm.objects(RealmFavoriteQuestion.self)
                    .filter("userId == %@", userId)
                realm.delete(userFavorites)
            }
            
            loadFavorites()
            print("🔥 FavoritesService: Tous les favoris supprimés")
            
        } catch {
            print("❌ FavoritesService: Erreur suppression favoris: \(error)")
        }
    }
    
    // MARK: - Export/Import (pour le futur)
    
    func exportFavorites() -> [FavoriteQuestion] {
        return favoriteQuestions
    }
    
    @MainActor
    func importFavorites(_ favorites: [FavoriteQuestion]) {
        guard let realm = realm, let userId = currentUserId else {
            return
        }
        
        do {
            try realm.write {
                for favorite in favorites {
                    let realmFavorite = RealmFavoriteQuestion()
                    realmFavorite.questionId = favorite.questionId
                    realmFavorite.userId = userId
                    realmFavorite.categoryTitle = favorite.categoryTitle
                    realmFavorite.questionText = favorite.questionText
                    realmFavorite.emoji = favorite.emoji
                    realmFavorite.dateAdded = favorite.dateAdded
                    
                    realm.add(realmFavorite, update: .modified)
                }
            }
            
            loadFavorites()
            print("🔥 FavoritesService: \(favorites.count) favoris importés")
            
        } catch {
            print("❌ FavoritesService: Erreur import favoris: \(error)")
        }
    }
} 