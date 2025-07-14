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
    
    // NOUVEAU: Configuration freemium
    private let questionsPerPack = 32
    private let freePacksLimit = 2 // 2 packs gratuits = 64 questions
    
    // NOUVEAU: Configuration freemium pour le journal
    private let freeJournalEntriesLimit = 5
    
    init(appState: AppState) {
        self.appState = appState
        setupObservers()
    }
    
    private func setupObservers() {
        // Observer les changements d'abonnement
        appState?.$currentUser
            .sink { [weak self] (user: AppUser?) in
                // Réagir aux changements d'abonnement si nécessaire
                print("🔥 FreemiumManager: Utilisateur changé - isSubscribed: \(user?.isSubscribed ?? false)")
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public Methods
    
    /// Vérifie si l'utilisateur peut accéder à une catégorie
    func canAccessCategory(_ category: QuestionCategory) -> Bool {
        // Les catégories premium nécessitent un abonnement
        if category.isPremium {
            return appState?.currentUser?.isSubscribed ?? false
        }
        
        // La catégorie "En couple" est gratuite
        return true
    }
    
    /// NOUVEAU: Vérifie si l'utilisateur peut accéder à une question spécifique dans une catégorie
    func canAccessQuestion(at index: Int, in category: QuestionCategory) -> Bool {
        // Si l'utilisateur est abonné, accès illimité
        if appState?.currentUser?.isSubscribed ?? false {
            return true
        }
        
        // Si c'est une catégorie premium, aucun accès
        if category.isPremium {
            return false
        }
        
        // Pour la catégorie "En couple" gratuite, limiter à 2 packs (64 questions)
        if category.id == "en-couple" {
            let maxFreeQuestions = freePacksLimit * questionsPerPack // 2 * 32 = 64
            return index < maxFreeQuestions
        }
        
        // Autres catégories gratuites (si elles existent)
        return true
    }
    
    /// NOUVEAU: Gère le tap sur une question avec vérification freemium
    func handleQuestionAccess(at index: Int, in category: QuestionCategory, onSuccess: @escaping () -> Void) {
        print("🔥🔥🔥 FREEMIUM QUESTION: Tentative accès question \(index + 1) dans \(category.title)")
        
        if canAccessQuestion(at: index, in: category) {
            print("🔥🔥🔥 FREEMIUM QUESTION: Accès autorisé")
            onSuccess()
        } else {
            print("🔥🔥🔥 FREEMIUM QUESTION: Accès bloqué - Affichage paywall")
            print("🔥🔥🔥 FREEMIUM QUESTION: Limite atteinte (64 questions) pour \(category.title)")
            
            blockedCategoryAttempt = category
            showingSubscription = true
            
            // Analytics - track blocked question
            trackQuestionBlocked(at: index, in: category)
        }
    }
    
    /// Gère le tap sur une catégorie avec la logique freemium
    func handleCategoryTap(_ category: QuestionCategory, onSuccess: @escaping () -> Void) {
        print("🔥 FreemiumManager: Tap sur catégorie: \(category.title)")
        print("🔥🔥🔥 FREEMIUM TAP: DEBUT GESTION TAP")
        print("🔥🔥🔥 FREEMIUM TAP: - Catégorie: \(category.title)")
        print("🔥🔥🔥 FREEMIUM TAP: - isPremium: \(category.isPremium)")
        print("🔥🔥🔥 FREEMIUM TAP: - Utilisateur abonné: \(appState?.currentUser?.isSubscribed ?? false)")
        print("🔥🔥🔥 FREEMIUM TAP: - showingSubscription AVANT: \(showingSubscription)")
        
        // Vérifier si l'utilisateur est abonné
        let isSubscribed = appState?.currentUser?.isSubscribed ?? false
        
        // Si l'utilisateur est abonné, accès illimité
        if isSubscribed {
            print("🔥🔥🔥 FREEMIUM TAP: UTILISATEUR ABONNE - ACCES ILLIMITE")
            onSuccess()
            return
        }
        
        // Si c'est une catégorie premium et l'utilisateur n'est pas abonné
        if category.isPremium {
            print("🔥🔥🔥 FREEMIUM TAP: CATEGORIE PREMIUM - ACCES BLOQUE")
            print("🔥 FreemiumManager: Accès bloqué à \(category.title) - affichage subscription")
            
            blockedCategoryAttempt = category
            print("🔥🔥🔥 FREEMIUM TAP: - blockedCategoryAttempt défini: \(category.title)")
            
            print("🔥🔥🔥 FREEMIUM TAP: - MISE A JOUR showingSubscription vers TRUE")
            showingSubscription = true
            print("🔥🔥🔥 FREEMIUM TAP: - showingSubscription APRES: \(showingSubscription)")
            
            // Notifier le changement
            NotificationCenter.default.post(name: .freemiumManagerChanged, object: nil)
            print("🔥🔥🔥 FREEMIUM TAP: - NOTIFICATION ENVOYEE")
            
            // Analytics - track blocked category
            trackCategoryBlocked(category)
            print("🔥🔥🔥 FREEMIUM TAP: FIN GESTION TAP BLOQUE")
            return
        }
        
        // Pour les catégories gratuites (comme "En couple"), permettre l'accès
        // La limitation se fera au niveau des questions dans QuestionListView
        print("🔥🔥🔥 FREEMIUM TAP: CATEGORIE GRATUITE - ACCES AUTORISE")
        print("🔥 FreemiumManager: Accès autorisé à \(category.title)")
        print("🔥🔥🔥 FREEMIUM TAP: ACCES AUTORISE - EXECUTION CALLBACK")
        onSuccess()
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
    
    /// NOUVEAU: Vérifie si une question doit être affichée comme bloquée
    func isQuestionBlocked(at index: Int, in category: QuestionCategory) -> Bool {
        return !canAccessQuestion(at: index, in: category)
    }
    
    /// NOUVEAU: Retourne le nombre maximum de questions gratuites pour une catégorie
    func getMaxFreeQuestions(for category: QuestionCategory) -> Int {
        if appState?.currentUser?.isSubscribed ?? false {
            return Int.max // Illimité pour les abonnés
        }
        
        if category.isPremium {
            return 0 // Aucune question gratuite pour les catégories premium
        }
        
        if category.id == "en-couple" {
            return freePacksLimit * questionsPerPack // 64 questions
        }
        
        return Int.max // Autres catégories gratuites (si elles existent)
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
    
    /// NOUVEAU: Gère l'accès au widget de distance avec la logique freemium
    func handleDistanceWidgetAccess(onSuccess: @escaping () -> Void) {
        print("🔒 FreemiumManager: Accès au widget de distance demandé")
        
        // Tous les widgets sont maintenant gratuits
        print("✅ FreemiumManager: Accès autorisé au widget de distance (gratuit)")
        onSuccess()
    }
    
    /// NOUVEAU: Vérifie si l'utilisateur peut ajouter une nouvelle entrée journal
    func canAddJournalEntry(currentEntriesCount: Int) -> Bool {
        // Si l'utilisateur est abonné (direct ou hérité), pas de limite
        if appState?.currentUser?.isSubscribed ?? false {
            return true
        }
        
        // Pour les utilisateurs gratuits, limite à 5 entrées
        return currentEntriesCount < freeJournalEntriesLimit
    }
    
    /// NOUVEAU: Gère la tentative d'ajout d'entrée journal avec la logique freemium
    func handleJournalEntryCreation(currentEntriesCount: Int, onSuccess: @escaping () -> Void) {
        print("📝 FreemiumManager: Tentative d'ajout d'entrée journal")
        print("📝 FreemiumManager: Nombre d'entrées actuelles: \(currentEntriesCount)")
        
        // Vérifier si l'utilisateur est abonné (direct ou hérité)
        let isSubscribed = appState?.currentUser?.isSubscribed ?? false
        
        if isSubscribed {
            print("📝 FreemiumManager: Utilisateur premium - Ajout autorisé")
            onSuccess()
        } else if currentEntriesCount < freeJournalEntriesLimit {
            print("📝 FreemiumManager: Utilisateur gratuit - Ajout autorisé (\(currentEntriesCount)/\(freeJournalEntriesLimit))")
            onSuccess()
        } else {
            print("📝 FreemiumManager: Utilisateur gratuit - Limite atteinte (\(freeJournalEntriesLimit) entrées)")
            print("📝 FreemiumManager: Affichage du paywall pour journal")
            
            // Marquer que la tentative d'accès était pour le journal
            showingSubscription = true
            
            // Analytics - track blocked journal entry
            trackJournalEntryBlocked(entriesCount: currentEntriesCount)
        }
    }
    
    /// NOUVEAU: Retourne le nombre maximum d'entrées journal gratuites
    func getMaxFreeJournalEntries() -> Int {
        return freeJournalEntriesLimit
    }
    
    /// NOUVEAU: Retourne le nombre d'entrées restantes pour les utilisateurs gratuits
    func getRemainingFreeJournalEntries(currentEntriesCount: Int) -> Int {
        if appState?.currentUser?.isSubscribed ?? false {
            return Int.max // Illimité pour les abonnés
        }
        
        return max(0, freeJournalEntriesLimit - currentEntriesCount)
    }
    
    // MARK: - Analytics (pour le futur)
    
    private func trackCategoryBlocked(_ category: QuestionCategory) {
        print("🔥 FreemiumManager: Analytics - Catégorie bloquée: \(category.title)")
        // Ici vous pourrez ajouter Firebase Analytics, Mixpanel, etc.
        // Analytics.track("category_blocked", properties: ["category": category.title])
    }
    
    /// NOUVEAU: Analytics pour les questions bloquées
    private func trackQuestionBlocked(at index: Int, in category: QuestionCategory) {
        print("🔥 FreemiumManager: Analytics - Question bloquée: \(index + 1) dans \(category.title)")
        // Analytics.track("question_blocked", properties: ["category": category.title, "questionIndex": index])
    }
    
    /// NOUVEAU: Analytics pour le widget de distance bloqué
    private func trackDistanceWidgetBlocked() {
        print("🔒 FreemiumManager: Analytics - Widget de distance bloqué")
        // Analytics.track("distance_widget_blocked", properties: ["feature": "distance_widget"])
    }
    
    /// NOUVEAU: Analytics pour les entrées journal bloquées
    private func trackJournalEntryBlocked(entriesCount: Int) {
        print("📝 FreemiumManager: Analytics - Entrée journal bloquée à \(entriesCount) entrées")
        // Analytics.track("journal_entry_blocked", properties: ["entries_count": entriesCount, "limit": freeJournalEntriesLimit])
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