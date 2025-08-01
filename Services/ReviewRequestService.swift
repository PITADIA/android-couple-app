import Foundation
import StoreKit
import FirebaseAnalytics
import UIKit

class ReviewRequestService: ObservableObject {
    static let shared = ReviewRequestService()
    
    private let userDefaults = UserDefaults.standard
    private let viewedQuestionsThreshold = 120  // 120 questions vues
    private let cooldownDays = 90
    
    // Clés UserDefaults
    private let lastReviewRequestKey = "lastReviewRequest"
    private let hasRequestedReviewKey = "hasRequestedReview"
    
    private init() {}
    
    // MARK: - Public Methods
    
    /// NOUVEAU: Vérifier si on doit demander un avis basé sur les questions vues
    func checkForReviewRequest() {
        print("🌟 ReviewRequestService: Vérification des conditions pour demande d'avis...")
        
        // Condition 1: 120 questions vues
        let totalViewedQuestions = getTotalViewedQuestions()
        print("🌟 ReviewRequestService: - Questions vues: \(totalViewedQuestions)/\(viewedQuestionsThreshold)")
        
        // Condition 2: Première demande
        let hasRequested = userDefaults.bool(forKey: hasRequestedReviewKey)
        print("🌟 ReviewRequestService: - Première demande: \(!hasRequested)")
        
        // Condition 3: Cooldown respecté
        let canRequest = canRequestReview()
        print("🌟 ReviewRequestService: - Cooldown respecté: \(canRequest)")
        
        // Vérifier toutes les conditions (plus besoin de partenaire connecté)
        if totalViewedQuestions >= viewedQuestionsThreshold && !hasRequested && canRequest {
            print("🌟 ReviewRequestService: ✅ TOUTES LES CONDITIONS REMPLIES - DEMANDE D'AVIS!")
            requestReview()
        } else {
            print("🌟 ReviewRequestService: ❌ Conditions non remplies pour la demande d'avis")
        }
    }
    
    /// Calculer le total de questions vues (réutilise la logique de CoupleStatisticsView)
    private func getTotalViewedQuestions() -> Int {
        let categories = QuestionCategory.categories
        var totalProgress = 0
        
        // Utiliser CategoryProgressService pour obtenir les indices actuels
        let categoryProgressService = CategoryProgressService.shared
        
        for category in categories {
            let questions = getQuestionsForCategory(category.id)
            let currentIndex = categoryProgressService.getCurrentIndex(for: category.id)
            
            // +1 car l'index commence à 0, et on compte les questions vues
            totalProgress += min(currentIndex + 1, questions.count)
        }
        
        return totalProgress
    }
    
    /// Récupère les questions pour une catégorie donnée
    private func getQuestionsForCategory(_ categoryId: String) -> [Question] {
        return QuestionDataManager.shared.loadQuestions(for: categoryId)
    }
    
    // MARK: - Private Methods (conservées)
    
    private func canRequestReview() -> Bool {
        guard let lastRequestDate = userDefaults.object(forKey: lastReviewRequestKey) as? Date else {
            return true // Aucune demande précédente
        }
        
        let daysSinceLastRequest = Calendar.current.dateComponents([.day], from: lastRequestDate, to: Date()).day ?? 0
        return daysSinceLastRequest >= cooldownDays
    }
    
    private func requestReview() {
        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first else {
            print("❌ ReviewRequestService: Impossible de trouver windowScene")
            return
        }
        
        print("🌟 ReviewRequestService: 🎉 DEMANDE D'AVIS APPLE DÉCLENCHÉE!")
        
        // 📊 Analytics: Demande d'avis
        Analytics.logEvent("avis_demande", parameters: [:])
        print("📊 Événement Firebase: avis_demande")
        
        // Demander l'avis avec l'API Apple
        SKStoreReviewController.requestReview(in: windowScene)
        
        // Marquer comme demandé
        userDefaults.set(true, forKey: hasRequestedReviewKey)
        userDefaults.set(Date(), forKey: lastReviewRequestKey)
        
        print("🌟 ReviewRequestService: ✅ Demande d'avis enregistrée")
    }
    
    // MARK: - Debug Methods
    
    /// Pour debug/test - réinitialiser les compteurs
    func resetForTesting() {
        userDefaults.removeObject(forKey: lastReviewRequestKey)
        userDefaults.removeObject(forKey: hasRequestedReviewKey)
        print("🌟 ReviewRequestService: 🔄 Compteurs réinitialisés pour test")
    }
    
    /// Pour debug - forcer une vérification
    func forceCheck() {
        print("🌟 ReviewRequestService: 🔍 Vérification forcée...")
        checkForReviewRequest()
    }
    
    /// Obtenir le statut actuel pour debug
    func getDebugStatus() -> String {
        let totalViewed = getTotalViewedQuestions()
        let hasRequested = userDefaults.bool(forKey: hasRequestedReviewKey)
        let canRequest = canRequestReview()
        
        return """
        📊 Statut ReviewRequestService:
        - Questions vues: \(totalViewed)/\(viewedQuestionsThreshold) \(totalViewed >= viewedQuestionsThreshold ? "✅" : "❌")
        - Première demande: \(!hasRequested ? "✅" : "❌")
        - Cooldown respecté: \(canRequest ? "✅" : "❌")
        """
    }
} 