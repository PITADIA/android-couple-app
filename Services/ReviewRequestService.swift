import Foundation
import StoreKit
import FirebaseAnalytics
import FirebaseAuth
import UIKit

class ReviewRequestService: ObservableObject {
    static let shared = ReviewRequestService()
    
    private let userDefaults = UserDefaults.standard
    
    // ✅ NOUVEAU SYSTÈME : Daily Questions (5 jours complétés)
    private var dailyQuestionsThreshold: Int {
        // A/B testing via UserDefaults override (sera Remote Config plus tard)
        userDefaults.object(forKey: "review_threshold_override") as? Int ?? 5
    }
    
    // ✅ ANCIEN SYSTÈME : Legacy fallback
    private let viewedQuestionsThreshold = 120
    private let cooldownDays = 90
    
    // ✅ Clés UserDefaults avec versioning
    private let lastReviewRequestKey = "lastReviewRequest"
    private let migrationDateKey = "daily_review_migration_date"
    private var hasRequestedReviewKey: String {
        "hasRequestedReview_\(Bundle.main.appVersion ?? "unknown")"
    }
    
    // ✅ DÉBOUNCE SESSION : Empêche multiples demandes dans même session
    private var sessionHasRequestedReview = false
    
    private init() {
        // Marquer la date de migration si première fois
        if userDefaults.object(forKey: migrationDateKey) == nil {
            userDefaults.set(Date(), forKey: migrationDateKey)
            print("🌟 ReviewRequestService: Migration date marquée: \(Date())")
        }
    }
    
    // MARK: - Public Methods
    
    // ✅ NOUVELLE MÉTHODE PRINCIPALE : Demande après action réussie (Daily Questions)
    @MainActor
    func maybeRequestReviewAfterDailyCompletion(in scene: UIWindowScene?) {
        print("🌟 ReviewRequestService: Vérification après completion daily question")
        
        // ✅ DÉBOUNCE SESSION : Une seule demande par session
        guard !sessionHasRequestedReview else {
            print("🌟 ❌ Déjà demandé cette session")
            return
        }
        
        let completedDays = getCompletedDailyQuestionDays()
        print("🌟 - Jours complétés: \(completedDays)/\(dailyQuestionsThreshold)")
        
        // ✅ CONDITIONS ROBUSTES
        guard completedDays >= dailyQuestionsThreshold else {
            print("🌟 ❌ Seuil non atteint (\(completedDays) < \(dailyQuestionsThreshold))")
            Analytics.logEvent("review_check", parameters: [
                "eligible": false,
                "reason": "threshold_not_met",
                "completed_days": completedDays,
                "context": "daily"
            ])
            return
        }
        
        guard !hasRequestedThisVersion() else {
            print("🌟 ❌ Déjà demandé cette version")
            Analytics.logEvent("review_check", parameters: [
                "eligible": false,
                "reason": "already_requested_version",
                "completed_days": completedDays,
                "context": "daily"
            ])
            return
        }
        
        guard isOutOfCooldown() else {
            print("🌟 ❌ Cooldown non respecté")
            Analytics.logEvent("review_check", parameters: [
                "eligible": false,
                "reason": "cooldown_active",
                "completed_days": completedDays,
                "context": "daily"
            ])
            return
        }
        
        guard isSafeToRequestReview() else {
            print("🌟 ❌ Migration trop récente")
            Analytics.logEvent("review_check", parameters: [
                "eligible": false,
                "reason": "migration_too_recent",
                "completed_days": completedDays,
                "context": "daily"
            ])
            return
        }
        
        guard !isModalPresented() else {
            print("🌟 ❌ Modal en cours, report")
            Analytics.logEvent("review_check", parameters: [
                "eligible": false,
                "reason": "modal_presented",
                "completed_days": completedDays,
                "context": "daily"
            ])
            return
        }
        
        // ✅ TOUTES CONDITIONS REMPLIES
        print("🌟 ✅ 5ème JOUR COMPLÉTÉ - DEMANDE D'AVIS!")
        Analytics.logEvent("review_check", parameters: [
            "eligible": true,
            "reason": "all_conditions_met",
            "completed_days": completedDays,
            "context": "daily"
        ])
        
        // ✅ DÉBOUNCE SESSION
        sessionHasRequestedReview = true
        
        // ✅ DÉLAI APPLE-FRIENDLY après action (0.6s)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            self.requestReviewWithScene(scene)
        }
    }
    
    /// FALLBACK: Ancien système pour utilisateurs sans partenaire
    func checkForReviewRequest() {
        print("🌟 ReviewRequestService: Fallback vers système legacy")
        
        // Essayer d'abord le nouveau système
        if hasPartnerConnected() {
            // Les utilisateurs avec partenaire utilisent le nouveau système
            // Cette méthode ne devrait plus être appelée pour eux
            print("🌟 ReviewRequestService: Utilisateur avec partenaire - ignorer legacy")
            return
        }
        
        // Logique legacy pour utilisateurs sans partenaire
        let totalViewedQuestions = getTotalViewedQuestions()
        print("🌟 ReviewRequestService: Legacy - Questions vues: \(totalViewedQuestions)/\(viewedQuestionsThreshold)")
        
        guard totalViewedQuestions >= viewedQuestionsThreshold else {
            print("🌟 ❌ Legacy - Seuil non atteint")
            return
        }
        guard !hasRequestedThisVersion() else {
            print("🌟 ❌ Legacy - Déjà demandé cette version")
            return
        }
        guard isOutOfCooldown() else {
            print("🌟 ❌ Legacy - Cooldown non respecté")
            return
        }
        
        print("🌟 ✅ Legacy - 120 questions vues - DEMANDE D'AVIS!")
        Analytics.logEvent("review_requested", parameters: [
            "context": "legacy",
            "viewed_questions": totalViewedQuestions
        ])
        
        // ✅ MAIN THREAD GARANTI
        DispatchQueue.main.async {
            self.requestReviewLegacy()
        }
    }
    
    // MARK: - Private Methods (Nouvelles méthodes)
    
    // ✅ NOUVELLE MÉTHODE : Compter les jours COMPLÉTÉS (avec réponses utilisateur)
    @MainActor
    private func getCompletedDailyQuestionDays() -> Int {
        let dailyService = DailyQuestionService.shared
        guard let currentUserId = Auth.auth().currentUser?.uid else {
            print("❌ ReviewRequestService: Pas d'utilisateur connecté")
            return 0
        }
        
        // Compter les jours UNIQUES où l'utilisateur a RÉPONDU
        let completedDates = Set(dailyService.questionHistory.compactMap { question in
            // Vérifier si l'utilisateur a répondu à cette question
            let userHasResponded = question.responsesArray.contains { response in
                response.userId == currentUserId
            }
            
            return userHasResponded ? question.scheduledDate : nil
        })
        
        print("📊 ReviewRequestService: Jours complétés uniques: \(completedDates.count)")
        print("📊 ReviewRequestService: Dates complétées: \(Array(completedDates).sorted())")
        
        return completedDates.count
    }
    
    // ✅ NOUVELLE MÉTHODE : Request avec WindowScene robuste + MAIN THREAD GARANTI
    private func requestReviewWithScene(_ scene: UIWindowScene?) {
        // ✅ MAIN THREAD GARANTI : Toute la méthode sur main thread
        DispatchQueue.main.async {
            guard let windowScene = scene ?? UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene }).first else {
                print("❌ ReviewRequestService: WindowScene introuvable")
                return
            }
            
            print("🌟 🎉 DEMANDE D'AVIS APPLE (après action réussie)!")
            
            // ✅ Analytics avant demande
            Analytics.logEvent("review_requested", parameters: [
                "context": "daily_completion",
                "threshold": self.dailyQuestionsThreshold,
                "completed_days": self.getCompletedDailyQuestionDays()
            ])
            
            // ✅ Demande Apple avec bonne WindowScene
            SKStoreReviewController.requestReview(in: windowScene)
            
            // ✅ Marquer pour cette version + timestamp
            self.markReviewRequestedThisVersion()
            self.userDefaults.set(Date(), forKey: self.lastReviewRequestKey)
            
            print("🌟 ReviewRequestService: ✅ Demande d'avis enregistrée (version \(Bundle.main.appVersion ?? "unknown"))")
        }
    }
    
    // ✅ MÉTHODES DE VALIDATION
    
    private func hasRequestedThisVersion() -> Bool {
        return userDefaults.bool(forKey: hasRequestedReviewKey)
    }
    
    private func markReviewRequestedThisVersion() {
        userDefaults.set(true, forKey: hasRequestedReviewKey)
    }
    
    private func isOutOfCooldown() -> Bool {
        guard let lastRequestDate = userDefaults.object(forKey: lastReviewRequestKey) as? Date else {
            return true // Aucune demande précédente
        }
        
        let daysSinceLastRequest = Calendar.current.dateComponents([.day], from: lastRequestDate, to: Date()).day ?? 0
        return daysSinceLastRequest >= cooldownDays
    }
    
    private func isSafeToRequestReview() -> Bool {
        guard let migrationDate = userDefaults.object(forKey: migrationDateKey) as? Date else {
            return true // Pas de date de migration = installation récente
        }
        
        let daysSinceMigration = Calendar.current.dateComponents([.day], from: migrationDate, to: Date()).day ?? 0
        return daysSinceMigration >= 4 // Au moins 4 jours depuis migration
    }
    
    private func isModalPresented() -> Bool {
        // Simple check - peut être étendu plus tard si besoin
        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first,
              let window = windowScene.windows.first else {
            return false
        }
        
        // Vérifier si une modal est présentée
        return window.rootViewController?.presentedViewController != nil
    }
    
    private func hasPartnerConnected() -> Bool {
        // Simple check - sera utilisé pour le fallback legacy
        return Auth.auth().currentUser != nil // Peut être affiné si besoin
    }
    
    // ✅ MÉTHODES LEGACY (conservées pour compatibilité)
    
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
    
    private func getQuestionsForCategory(_ categoryId: String) -> [Question] {
        return QuestionDataManager.shared.loadQuestions(for: categoryId)
    }
    
    private func requestReviewLegacy() {
        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first else {
            print("❌ ReviewRequestService: Impossible de trouver windowScene (legacy)")
            return
        }
        
        print("🌟 ReviewRequestService: 🎉 DEMANDE D'AVIS APPLE (Legacy)!")
        
        // Demander l'avis avec l'API Apple
        SKStoreReviewController.requestReview(in: windowScene)
        
        // Marquer comme demandé
        markReviewRequestedThisVersion()
        userDefaults.set(Date(), forKey: lastReviewRequestKey)
        
        print("🌟 ReviewRequestService: ✅ Demande d'avis legacy enregistrée")
    }
    
    // MARK: - Debug Methods
    
    /// Pour debug/test - réinitialiser TOUS les compteurs
    func resetForTesting() {
        userDefaults.removeObject(forKey: lastReviewRequestKey)
        userDefaults.removeObject(forKey: hasRequestedReviewKey)
        userDefaults.removeObject(forKey: migrationDateKey)
        sessionHasRequestedReview = false
        print("🌟 ReviewRequestService: 🔄 TOUS compteurs réinitialisés pour test")
    }
    
    /// Pour debug - forcer une vérification nouveau système
    @MainActor
    func forceCheckDaily() {
        print("🌟 ReviewRequestService: 🔍 Vérification forcée NOUVEAU système...")
        if let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first {
            maybeRequestReviewAfterDailyCompletion(in: windowScene)
        }
    }
    
    /// Pour debug - forcer une vérification legacy
    func forceCheck() {
        print("🌟 ReviewRequestService: 🔍 Vérification forcée LEGACY...")
        checkForReviewRequest()
    }
    
    /// Obtenir le statut actuel pour debug (NOUVEAU système)
    @MainActor
    func getDebugStatus() -> String {
        let completedDays = getCompletedDailyQuestionDays()
        let hasRequested = hasRequestedThisVersion()
        let isOutOfCooldown = isOutOfCooldown()
        let isSafeToRequest = isSafeToRequestReview()
        let sessionDebounce = sessionHasRequestedReview
        
        // Legacy pour comparaison
        let totalViewed = getTotalViewedQuestions()
        
        return """
        🌟 ReviewRequestService Status (NOUVEAU):
        - Jours complétés: \(completedDays)/\(dailyQuestionsThreshold) \(completedDays >= dailyQuestionsThreshold ? "✅" : "❌")
        - Version non demandée: \(!hasRequested) \(!hasRequested ? "✅" : "❌")
        - Cooldown respecté: \(isOutOfCooldown) \(isOutOfCooldown ? "✅" : "❌")
        - Migration safe: \(isSafeToRequest) \(isSafeToRequest ? "✅" : "❌")
        - Session non demandée: \(!sessionDebounce) \(!sessionDebounce ? "✅" : "❌")
        
        📊 Legacy (comparaison):
        - Questions vues: \(totalViewed)/\(viewedQuestionsThreshold) \(totalViewed >= viewedQuestionsThreshold ? "✅" : "❌")
        """
    }
}

// ✅ EXTENSION : Bundle version helper
extension Bundle {
    var appVersion: String? {
        return infoDictionary?["CFBundleShortVersionString"] as? String
    }
} 