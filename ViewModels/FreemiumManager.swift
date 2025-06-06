import Foundation
import SwiftUI
import Combine

class FreemiumManager: ObservableObject {
    @Published var showingSubscription = false {
        didSet {
            print("🔥🔥🔥 FREEMIUM MANAGER: showingSubscription changé vers \(showingSubscription)")
            // Envoyer une notification pour synchroniser avec MainView
            NotificationCenter.default.post(name: .freemiumManagerChanged, object: nil)
        }
    }
    @Published var blockedCategoryAttempt: QuestionCategory?
    
    private weak var appState: AppState?
    private var cancellables = Set<AnyCancellable>()
    
    init(appState: AppState) {
        self.appState = appState
        setupObservers()
    }
    
    private func setupObservers() {
        // Observer les changements d'abonnement
        appState?.$currentUser
            .sink { [weak self] user in
                // Réagir aux changements d'abonnement si nécessaire
                print("🔥 FreemiumManager: Utilisateur changé - isSubscribed: \(user?.isSubscribed ?? false)")
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public Methods
    
    /// Vérifie si l'utilisateur peut accéder à une catégorie
    func canAccessCategory(_ category: QuestionCategory) -> Bool {
        // Les catégories gratuites sont toujours accessibles
        if !category.isPremium {
            return true
        }
        
        // Les catégories premium nécessitent un abonnement
        return appState?.currentUser?.isSubscribed ?? false
    }
    
    /// Gère le tap sur une catégorie avec la logique freemium
    func handleCategoryTap(_ category: QuestionCategory, onSuccess: @escaping () -> Void) {
        print("🔥 FreemiumManager: Tap sur catégorie: \(category.title)")
        print("🔥🔥🔥 FREEMIUM TAP: DEBUT GESTION TAP")
        print("🔥🔥🔥 FREEMIUM TAP: - Catégorie: \(category.title)")
        print("🔥🔥🔥 FREEMIUM TAP: - isPremium: \(category.isPremium)")
        print("🔥🔥🔥 FREEMIUM TAP: - showingSubscription AVANT: \(showingSubscription)")
        
        if canAccessCategory(category) {
            print("🔥 FreemiumManager: Accès autorisé à \(category.title)")
            print("🔥🔥🔥 FREEMIUM TAP: ACCES AUTORISE - EXECUTION CALLBACK")
            onSuccess()
        } else {
            print("🔥 FreemiumManager: Accès bloqué à \(category.title) - affichage subscription")
            print("🔥🔥🔥 FREEMIUM TAP: ACCES BLOQUE - PREPARATION AFFICHAGE")
            
            blockedCategoryAttempt = category
            print("🔥🔥🔥 FREEMIUM TAP: - blockedCategoryAttempt défini: \(category.title)")
            
            print("🔥🔥🔥 FREEMIUM TAP: - MISE A JOUR showingSubscription vers TRUE")
            showingSubscription = true
            print("🔥🔥🔥 FREEMIUM TAP: - showingSubscription APRES: \(showingSubscription)")
            
            // Notifier le changement
            NotificationCenter.default.post(name: .freemiumManagerChanged, object: nil)
            print("🔥🔥🔥 FREEMIUM TAP: - NOTIFICATION ENVOYEE")
            
            // Vérification immédiate
            DispatchQueue.main.async {
                print("🔥🔥🔥 FREEMIUM TAP: VERIFICATION ASYNC - showingSubscription: \(self.showingSubscription)")
            }
            
            // Analytics - track blocked category
            trackCategoryBlocked(category)
            
            print("🔥🔥🔥 FREEMIUM TAP: FIN GESTION TAP BLOQUE")
        }
    }
    
    /// Retourne les catégories accessibles selon le statut d'abonnement
    func getAccessibleCategories() -> [QuestionCategory] {
        let isSubscribed = appState?.currentUser?.isSubscribed ?? false
        
        if isSubscribed {
            print("🔥 FreemiumManager: Utilisateur premium - toutes catégories accessibles")
            return QuestionCategory.categories
        } else {
            print("🔥 FreemiumManager: Utilisateur gratuit - catégories limitées")
            // Pour les utilisateurs gratuits, seule la première catégorie gratuite est accessible
            let freeCategories = QuestionCategory.categories.filter { !$0.isPremium }
            return Array(freeCategories.prefix(1)) // Seulement la première catégorie gratuite
        }
    }
    
    /// Retourne toutes les catégories avec leur statut de blocage
    func getAllCategoriesWithStatus() -> [QuestionCategory] {
        return QuestionCategory.categories
    }
    
    /// Vérifie si une catégorie doit être affichée comme bloquée
    func isCategoryBlocked(_ category: QuestionCategory) -> Bool {
        let isSubscribed = appState?.currentUser?.isSubscribed ?? false
        let isBlocked = category.isPremium && !isSubscribed
        
        print("🔥🔥🔥 FREEMIUM BLOCKED CHECK: Catégorie: \(category.title)")
        print("🔥🔥🔥 FREEMIUM BLOCKED CHECK: - isPremium: \(category.isPremium)")
        print("🔥🔥🔥 FREEMIUM BLOCKED CHECK: - isSubscribed: \(isSubscribed)")
        print("🔥🔥🔥 FREEMIUM BLOCKED CHECK: - isBlocked: \(isBlocked)")
        
        return isBlocked
    }
    
    /// Ferme la vue de subscription
    func dismissSubscription() {
        print("🔥 FreemiumManager: Fermeture de la vue subscription")
        print("🔥🔥🔥 FREEMIUM DISMISS: DEBUT FERMETURE")
        print("🔥🔥🔥 FREEMIUM DISMISS: - showingSubscription AVANT: \(showingSubscription)")
        print("🔥🔥🔥 FREEMIUM DISMISS: - blockedCategoryAttempt AVANT: \(blockedCategoryAttempt?.title ?? "nil")")
        
        showingSubscription = false
        blockedCategoryAttempt = nil
        
        // Notifier le changement
        NotificationCenter.default.post(name: .freemiumManagerChanged, object: nil)
        print("🔥🔥🔥 FREEMIUM DISMISS: - NOTIFICATION ENVOYEE")
        
        print("🔥🔥🔥 FREEMIUM DISMISS: - showingSubscription APRES: \(showingSubscription)")
        print("🔥🔥🔥 FREEMIUM DISMISS: - blockedCategoryAttempt APRES: \(blockedCategoryAttempt?.title ?? "nil")")
        print("🔥🔥🔥 FREEMIUM DISMISS: FIN FERMETURE")
    }
    
    // MARK: - Analytics (pour le futur)
    
    private func trackCategoryBlocked(_ category: QuestionCategory) {
        print("🔥 FreemiumManager: Analytics - Catégorie bloquée: \(category.title)")
        // Ici vous pourrez ajouter Firebase Analytics, Mixpanel, etc.
        // Analytics.track("category_blocked", properties: ["category": category.title])
    }
    
    func trackUpgradePromptShown() {
        print("🔥 FreemiumManager: Analytics - Prompt d'upgrade affiché")
        // Analytics.track("upgrade_prompt_shown")
    }
    
    func trackConversion() {
        print("🔥 FreemiumManager: Analytics - Conversion réussie")
        // Analytics.track("freemium_conversion")
    }
}

// MARK: - Extensions pour le futur

extension FreemiumManager {
    /// Fonctionnalités futures - limitation quotidienne
    var dailyQuestionsRemaining: Int {
        // Pour l'instant, pas de limitation
        return 999
    }
    
    /// Fonctionnalités futures - système de crédits
    var credits: Int {
        // Pour l'instant, pas de système de crédits
        return 0
    }
    
    /// Fonctionnalités futures - accès temporaire
    func hasTemporaryAccess(to category: QuestionCategory) -> Bool {
        // Pour l'instant, pas d'accès temporaire
        return false
    }
} 