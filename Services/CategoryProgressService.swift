import Foundation
import Combine

class CategoryProgressService: ObservableObject {
    static let shared = CategoryProgressService()
    
    @Published private var categoryProgress: [String: Int] = [:]
    
    private let userDefaults = UserDefaults.standard
    private let categoryProgressKey = "CategoryProgressKey"
    
    private init() {
        loadProgress()
    }
    
    // MARK: - Public Methods
    
    /// Sauvegarder la position actuelle dans une catégorie
    func saveCurrentIndex(_ index: Int, for categoryTitle: String) {
        categoryProgress[categoryTitle] = index
        saveProgress()
        print("🔥 CategoryProgressService: Position \(index) sauvegardée pour '\(categoryTitle)'")
    }
    
    /// Récupérer la dernière position dans une catégorie
    func getCurrentIndex(for categoryTitle: String) -> Int {
        let savedIndex = categoryProgress[categoryTitle] ?? 0
        print("🔥 CategoryProgressService: Position récupérée pour '\(categoryTitle)': \(savedIndex)")
        return savedIndex
    }
    
    /// Vérifier si une catégorie a une position sauvegardée
    func hasProgress(for categoryTitle: String) -> Bool {
        return categoryProgress[categoryTitle] != nil
    }
    
    /// Réinitialiser la progression d'une catégorie
    func resetProgress(for categoryTitle: String) {
        categoryProgress[categoryTitle] = 0
        saveProgress()
        print("🔥 CategoryProgressService: Progression réinitialisée pour '\(categoryTitle)'")
    }
    
    /// Réinitialiser toute la progression
    func resetAllProgress() {
        categoryProgress.removeAll()
        saveProgress()
        print("🔥 CategoryProgressService: Toute la progression réinitialisée")
    }
    
    /// Obtenir un résumé de la progression
    func getProgressSummary() -> [String: Int] {
        return categoryProgress
    }
    
    // MARK: - Private Methods
    
    private func loadProgress() {
        if let data = userDefaults.data(forKey: categoryProgressKey),
           let decoded = try? JSONDecoder().decode([String: Int].self, from: data) {
            categoryProgress = decoded
            print("🔥 CategoryProgressService: Progression chargée: \(categoryProgress)")
        } else {
            categoryProgress = [:]
            print("🔥 CategoryProgressService: Aucune progression sauvegardée, démarrage à zéro")
        }
    }
    
    private func saveProgress() {
        if let encoded = try? JSONEncoder().encode(categoryProgress) {
            userDefaults.set(encoded, forKey: categoryProgressKey)
            print("🔥 CategoryProgressService: Progression sauvegardée: \(categoryProgress)")
        }
    }
}

// MARK: - Extensions pour faciliter l'utilisation

extension CategoryProgressService {
    /// Obtenir des informations formatées pour l'affichage
    func getProgressInfo(for categoryTitle: String, totalQuestions: Int) -> (currentIndex: Int, percentage: Double) {
        let currentIndex = getCurrentIndex(for: categoryTitle)
        let percentage = totalQuestions > 0 ? Double(currentIndex + 1) / Double(totalQuestions) * 100.0 : 0.0
        return (currentIndex: currentIndex, percentage: percentage)
    }
    
    /// Avancer à la question suivante
    func moveToNext(for categoryTitle: String, maxIndex: Int) {
        let currentIndex = getCurrentIndex(for: categoryTitle)
        let nextIndex = min(currentIndex + 1, maxIndex)
        saveCurrentIndex(nextIndex, for: categoryTitle)
    }
    
    /// Reculer à la question précédente
    func moveToPrevious(for categoryTitle: String) {
        let currentIndex = getCurrentIndex(for: categoryTitle)
        let previousIndex = max(currentIndex - 1, 0)
        saveCurrentIndex(previousIndex, for: categoryTitle)
    }
} 