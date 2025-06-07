import Foundation
import Combine

class PackProgressService: ObservableObject {
    static let shared = PackProgressService()
    
    @Published private var packProgress: [String: Int] = [:]
    
    private let questionsPerPack = 32
    private let userDefaults = UserDefaults.standard
    private let packProgressKey = "PackProgressKey"
    
    private init() {
        loadProgress()
    }
    
    // MARK: - Public Methods
    
    /// Obtenir le nombre de packs débloqués pour une catégorie
    func getUnlockedPacks(for categoryTitle: String) -> Int {
        return packProgress[categoryTitle] ?? 1 // Au minimum 1 pack débloqué
    }
    
    /// Obtenir le nombre total de questions disponibles pour une catégorie
    func getAvailableQuestionsCount(for categoryTitle: String) -> Int {
        let unlockedPacks = getUnlockedPacks(for: categoryTitle)
        return unlockedPacks * questionsPerPack
    }
    
    /// Vérifier si l'utilisateur a terminé un pack
    func checkPackCompletion(categoryTitle: String, currentIndex: Int) -> Bool {
        let currentPack = getCurrentPack(for: currentIndex)
        let unlockedPacks = getUnlockedPacks(for: categoryTitle)
        
        // L'utilisateur a terminé un pack s'il est à la dernière question d'un pack débloqué
        let isLastQuestionOfPack = (currentIndex + 1) % questionsPerPack == 0
        let isCurrentPackCompleted = currentPack <= unlockedPacks
        
        return isLastQuestionOfPack && isCurrentPackCompleted
    }
    
    /// Débloquer le pack suivant pour une catégorie
    func unlockNextPack(for categoryTitle: String) {
        let currentUnlockedPacks = getUnlockedPacks(for: categoryTitle)
        packProgress[categoryTitle] = currentUnlockedPacks + 1
        saveProgress()
        
        print("🔥 PackProgressService: Pack \(currentUnlockedPacks + 1) débloqué pour \(categoryTitle)")
    }
    
    /// Obtenir le numéro du pack actuel basé sur l'index de la question
    func getCurrentPack(for questionIndex: Int) -> Int {
        return (questionIndex / questionsPerPack) + 1
    }
    
    /// Vérifier si une question est accessible (dans un pack débloqué)
    func isQuestionAccessible(categoryTitle: String, questionIndex: Int) -> Bool {
        let questionPack = getCurrentPack(for: questionIndex)
        let unlockedPacks = getUnlockedPacks(for: categoryTitle)
        return questionPack <= unlockedPacks
    }
    
    /// Obtenir les questions accessibles pour une catégorie
    func getAccessibleQuestions(from allQuestions: [Question], categoryTitle: String) -> [Question] {
        let availableCount = getAvailableQuestionsCount(for: categoryTitle)
        return Array(allQuestions.prefix(availableCount))
    }
    
    /// Réinitialiser la progression pour une catégorie (pour les tests)
    func resetProgress(for categoryTitle: String) {
        packProgress[categoryTitle] = 1
        saveProgress()
        print("🔥 PackProgressService: Progression réinitialisée pour \(categoryTitle)")
    }
    
    /// Réinitialiser toute la progression (pour les tests)
    func resetAllProgress() {
        packProgress.removeAll()
        saveProgress()
        print("🔥 PackProgressService: Toute la progression réinitialisée")
    }
    
    // MARK: - Private Methods
    
    private func loadProgress() {
        if let data = userDefaults.data(forKey: packProgressKey),
           let decoded = try? JSONDecoder().decode([String: Int].self, from: data) {
            packProgress = decoded
            print("🔥 PackProgressService: Progression chargée: \(packProgress)")
        } else {
            packProgress = [:]
            print("🔥 PackProgressService: Aucune progression sauvegardée, démarrage à zéro")
        }
    }
    
    private func saveProgress() {
        if let encoded = try? JSONEncoder().encode(packProgress) {
            userDefaults.set(encoded, forKey: packProgressKey)
            print("🔥 PackProgressService: Progression sauvegardée: \(packProgress)")
        }
    }
}

// MARK: - Extensions pour faciliter l'utilisation

extension PackProgressService {
    /// Obtenir des informations de progression formatées pour l'affichage
    func getProgressInfo(for categoryTitle: String) -> (unlockedPacks: Int, totalQuestions: Int) {
        let unlockedPacks = getUnlockedPacks(for: categoryTitle)
        let totalQuestions = getAvailableQuestionsCount(for: categoryTitle)
        return (unlockedPacks: unlockedPacks, totalQuestions: totalQuestions)
    }
    
    /// Obtenir le pourcentage de progression
    func getProgressPercentage(for categoryTitle: String, totalQuestions: Int) -> Double {
        let availableQuestions = getAvailableQuestionsCount(for: categoryTitle)
        guard totalQuestions > 0 else { return 0 }
        return Double(availableQuestions) / Double(totalQuestions) * 100.0
    }
} 