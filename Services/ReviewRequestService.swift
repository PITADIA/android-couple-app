import Foundation
import StoreKit
import UIKit

class ReviewRequestService: ObservableObject {
    static let shared = ReviewRequestService()
    
    private let userDefaults = UserDefaults.standard
    private let favoritesThreshold = 20
    private let cooldownDays = 90
    
    // Clés UserDefaults
    private let hasPartnerConnectedKey = "hasPartnerConnected"
    private let favoritesCountKey = "favoritesCount"
    private let lastReviewRequestKey = "lastReviewRequest"
    private let hasRequestedReviewKey = "hasRequestedReview"
    
    private init() {}
    
    // MARK: - Public Methods
    
    func trackPartnerConnected() {
        print("🌟 ReviewRequestService: Partenaire connecté")
        userDefaults.set(true, forKey: hasPartnerConnectedKey)
        checkForReviewRequest()
    }
    
    func trackFavoriteAdded() {
        let currentCount = userDefaults.integer(forKey: favoritesCountKey)
        let newCount = currentCount + 1
        userDefaults.set(newCount, forKey: favoritesCountKey)
        
        print("🌟 ReviewRequestService: Favoris ajouté (\(newCount)/\(favoritesThreshold))")
        checkForReviewRequest()
    }
    
    func trackFavoriteRemoved() {
        let currentCount = userDefaults.integer(forKey: favoritesCountKey)
        let newCount = max(0, currentCount - 1)
        userDefaults.set(newCount, forKey: favoritesCountKey)
        
        print("🌟 ReviewRequestService: Favoris supprimé (\(newCount)/\(favoritesThreshold))")
    }
    
    func syncFavoritesCount(actualCount: Int) {
        let storedCount = userDefaults.integer(forKey: favoritesCountKey)
        if storedCount != actualCount {
            print("🌟 ReviewRequestService: Synchronisation compteur favoris: \(storedCount) → \(actualCount)")
            userDefaults.set(actualCount, forKey: favoritesCountKey)
        }
    }
    
    // MARK: - Private Methods
    
    private func checkForReviewRequest() {
        guard shouldRequestReview() else { return }
        
        print("🌟 ReviewRequestService: Conditions remplies - Demande de review")
        
        DispatchQueue.main.async {
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                SKStoreReviewController.requestReview(in: windowScene)
                self.recordReviewRequest()
            }
        }
    }
    
    private func shouldRequestReview() -> Bool {
        // Vérifier si déjà demandé
        if userDefaults.bool(forKey: hasRequestedReviewKey) {
            print("🌟 ReviewRequestService: Review déjà demandée")
            return false
        }
        
        // Vérifier le cooldown
        if hasRequestedReviewRecently() {
            print("🌟 ReviewRequestService: Cooldown actif")
            return false
        }
        
        // Vérifier les conditions
        let hasPartner = userDefaults.bool(forKey: hasPartnerConnectedKey)
        let favoritesCount = userDefaults.integer(forKey: favoritesCountKey)
        
        let conditionsMet = hasPartner && favoritesCount >= favoritesThreshold
        
        print("🌟 ReviewRequestService: Partenaire connecté: \(hasPartner)")
        print("🌟 ReviewRequestService: Favoris: \(favoritesCount)/\(favoritesThreshold)")
        print("🌟 ReviewRequestService: Conditions remplies: \(conditionsMet)")
        
        return conditionsMet
    }
    
    private func hasRequestedReviewRecently() -> Bool {
        guard let lastRequest = userDefaults.object(forKey: lastReviewRequestKey) as? Date else {
            return false
        }
        
        let daysSinceLastRequest = Calendar.current.dateComponents([.day], from: lastRequest, to: Date()).day ?? 0
        return daysSinceLastRequest < cooldownDays
    }
    
    private func recordReviewRequest() {
        userDefaults.set(Date(), forKey: lastReviewRequestKey)
        userDefaults.set(true, forKey: hasRequestedReviewKey)
        print("🌟 ReviewRequestService: Review demandée et enregistrée")
    }
    
    // MARK: - Debug Methods
    
    func resetReviewStatus() {
        userDefaults.removeObject(forKey: hasPartnerConnectedKey)
        userDefaults.removeObject(forKey: favoritesCountKey)
        userDefaults.removeObject(forKey: lastReviewRequestKey)
        userDefaults.removeObject(forKey: hasRequestedReviewKey)
        print("🌟 ReviewRequestService: Statut de review réinitialisé")
    }
    
    func getCurrentStatus() -> (hasPartner: Bool, favoritesCount: Int, canRequest: Bool) {
        let hasPartner = userDefaults.bool(forKey: hasPartnerConnectedKey)
        let favoritesCount = userDefaults.integer(forKey: favoritesCountKey)
        let canRequest = shouldRequestReview()
        
        return (hasPartner, favoritesCount, canRequest)
    }
} 