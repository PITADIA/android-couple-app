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
    func getUnlockedPacks(for categoryId: String) -> Int {
        return packProgress[categoryId] ?? 1 // Au minimum 1 pack débloqué
    }
    
    /// Obtenir le nombre total de questions disponibles pour une catégorie
    func getAvailableQuestionsCount(for categoryId: String) -> Int {
        let unlockedPacks = getUnlockedPacks(for: categoryId)
        return unlockedPacks * questionsPerPack
    }
    
    /// Vérifier si l'utilisateur a terminé un pack
    func checkPackCompletion(categoryId: String, currentIndex: Int) -> Bool {
        let currentPack = getCurrentPack(for: currentIndex)
        let unlockedPacks = getUnlockedPacks(for: categoryId)
        
        // L'utilisateur a terminé un pack s'il est à la dernière question d'un pack débloqué
        let isLastQuestionOfPack = (currentIndex + 1) % questionsPerPack == 0
        let isCurrentPackCompleted = currentPack <= unlockedPacks
        
        return isLastQuestionOfPack && isCurrentPackCompleted
    }
    
    /// Débloquer le pack suivant pour une catégorie
    func unlockNextPack(for categoryId: String) {
        let currentUnlockedPacks = getUnlockedPacks(for: categoryId)
        packProgress[categoryId] = currentUnlockedPacks + 1
        saveProgress()
        
        print("🔥 PackProgressService: Pack \(currentUnlockedPacks + 1) débloqué pour \(categoryId)")
    }
    
    /// Obtenir le numéro du pack actuel basé sur l'index de la question
    func getCurrentPack(for questionIndex: Int) -> Int {
        return (questionIndex / questionsPerPack) + 1
    }
    
    /// Vérifier si une question est accessible (dans un pack débloqué)
    func isQuestionAccessible(categoryId: String, questionIndex: Int) -> Bool {
        let questionPack = getCurrentPack(for: questionIndex)
        let unlockedPacks = getUnlockedPacks(for: categoryId)
        return questionPack <= unlockedPacks
    }
    
    /// Obtenir les questions accessibles pour une catégorie
    func getAccessibleQuestions(from allQuestions: [Question], categoryId: String) -> [Question] {
        let availableCount = getAvailableQuestionsCount(for: categoryId)
        return Array(allQuestions.prefix(availableCount))
    }
    
    /// Réinitialiser la progression pour une catégorie (pour les tests)
    func resetProgress(for categoryId: String) {
        packProgress[categoryId] = 1
        saveProgress()
        print("🔥 PackProgressService: Progression réinitialisée pour \(categoryId)")
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
    func getProgressInfo(for categoryId: String) -> (unlockedPacks: Int, totalQuestions: Int) {
        let unlockedPacks = getUnlockedPacks(for: categoryId)
        let totalQuestions = getAvailableQuestionsCount(for: categoryId)
        return (unlockedPacks: unlockedPacks, totalQuestions: totalQuestions)
    }
    
    /// Obtenir le pourcentage de progression
    func getProgressPercentage(for categoryId: String, totalQuestions: Int) -> Double {
        let availableQuestions = getAvailableQuestionsCount(for: categoryId)
        guard totalQuestions > 0 else { return 0 }
        return Double(availableQuestions) / Double(totalQuestions) * 100.0
    }
} 