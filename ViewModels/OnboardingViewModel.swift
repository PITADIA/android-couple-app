import Foundation
import Combine
import UIKit

class OnboardingViewModel: ObservableObject {
    enum OnboardingStep: CaseIterable {
        case name
        case profilePhoto
        case relationshipGoals
        case relationshipDate
        case relationshipImprovement
        case completion
        case loading
        case authentication
        case partnerCode
        case fitnessIntro
        case fitnessIntro2
        case dailyQuestionNotification
        case categoriesPreview
        case subscription
    }
    
    @Published var currentStep: OnboardingStep = .name
    @Published var userName: String = ""
    @Published var birthDate: Date = Date()
    @Published var selectedGoals: [String] = []
    @Published var relationshipDuration: AppUser.RelationshipDuration = .oneToThreeYears
    @Published var relationshipImprovement: String = ""
    @Published var selectedImprovements: [String] = []
    @Published var questionMode: String = "🔄 Questions variées"
    @Published var isLoading: Bool = false
    @Published var shouldSkipSubscription: Bool = false
    @Published var shouldShowPartnerConnectionSuccess = false
    @Published var connectedPartnerName: String = ""
    @Published var relationshipStartDate: Date?
    @Published var profileImage: UIImage?
    @Published var currentLocation: UserLocation?
    
    var appState: AppState?
    private var cancellables = Set<AnyCancellable>()
    private var isCompletingSubscription = false
    
    // Options pour les objectifs de relation
    var relationshipGoals: [String] {
        [
            "goal_create_connection".localized,
            "goal_talk_avoided_subjects".localized,
            "goal_increase_passion".localized,
            "goal_share_more_laughs".localized,
            "goal_find_complicity".localized
        ]
    }

    // Options pour l'amélioration de la relation
    var relationshipImprovements: [String] {
        [
            "improvement_create_strong_moment".localized,
            "improvement_revive_connection".localized,
            "improvement_break_routine".localized,
            "improvement_say_unsaid".localized
        ]
    }
    
    // Options pour le mode de questions
    let questionModes = [
        "🎯 Sérieux",
        "🎉 Fun",
        "🌶️ Hot et Sensuel",
        "💫 Profond"
    ]
    
    // Propriété calculée pour la barre de progression
    var progressValue: Double {
        let totalSteps = Double(OnboardingStep.allCases.count)
        let currentIndex = Double(OnboardingStep.allCases.firstIndex(of: currentStep) ?? 0)
        return (currentIndex + 1) / totalSteps
    }
    
    init(appState: AppState) {
        print("🔥 OnboardingViewModel: Initialisation avec AppState")
        self.appState = appState
        
        // Observer les changements d'abonnement
        NotificationCenter.default.addObserver(
            forName: .subscriptionUpdated,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleSubscriptionUpdate()
        }
        
        // NOUVEAU: Observer les connexions partenaire réussies
        NotificationCenter.default.addObserver(
            forName: .partnerConnectionSuccess,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            if let userInfo = notification.userInfo,
               let partnerName = userInfo["partnerName"] as? String {
                self?.showPartnerConnectionSuccess(partnerName: partnerName)
            }
        }
    }
    
    init() {
        print("🔥 OnboardingViewModel: Initialisation sans AppState")
        self.appState = nil
    }
    
    func updateAppState(_ newAppState: AppState) {
        print("🔥 OnboardingViewModel: Mise à jour AppState")
        self.appState = newAppState
    }
    
    func nextStep() {
        switch currentStep {
        case .name:
            if !userName.isEmpty {
                currentStep = .profilePhoto
            }
        case .profilePhoto:
            currentStep = .relationshipGoals
        case .relationshipGoals:
            currentStep = .relationshipDate
        case .relationshipDate:
            currentStep = .relationshipImprovement
        case .relationshipImprovement:
            currentStep = .completion
        case .completion:
            currentStep = .loading
            completeDataCollection()
        case .loading:
            currentStep = .authentication
        case .authentication:
            if shouldSkipSubscription {
                finalizeOnboarding(withSubscription: true)
            } else {
                currentStep = .partnerCode
            }
        case .partnerCode:
            currentStep = .fitnessIntro
        case .fitnessIntro:
            currentStep = .fitnessIntro2
        case .fitnessIntro2:
            currentStep = .dailyQuestionNotification
        case .dailyQuestionNotification:
            currentStep = .categoriesPreview
        case .categoriesPreview:
            currentStep = .subscription
        case .subscription:
            // L'onboarding doit être finalisé via skipSubscription() ou completeSubscription()
            break
        }
    }
    
    func previousStep() {
        print("🔥 OnboardingViewModel: Retour à l'étape précédente depuis \(currentStep)")
        switch currentStep {
        case .name:
            print("🔥 OnboardingViewModel: Déjà à la première étape")
            break
        case .profilePhoto:
            currentStep = .name
        case .relationshipGoals:
            currentStep = .profilePhoto
        case .relationshipDate:
            currentStep = .relationshipGoals
        case .relationshipImprovement:
            currentStep = .relationshipDate
        case .completion:
            currentStep = .relationshipImprovement
        case .loading:
            currentStep = .completion
        case .authentication:
            print("🔥 OnboardingViewModel: Impossible de revenir en arrière depuis l'authentification")
            break
        case .partnerCode:
            currentStep = .authentication
        case .fitnessIntro:
            currentStep = .partnerCode
        case .fitnessIntro2:
            currentStep = .fitnessIntro
        case .dailyQuestionNotification:
            currentStep = .fitnessIntro2
        case .categoriesPreview:
            currentStep = .dailyQuestionNotification
        case .subscription:
            print("🔥 OnboardingViewModel: Impossible de revenir en arrière depuis l'abonnement")
            break
        }
        print("🔥 OnboardingViewModel: Nouvelle étape: \(currentStep)")
    }
    
    func toggleGoal(_ goal: String) {
        if selectedGoals.contains(goal) {
            selectedGoals.removeAll { $0 == goal }
        } else {
            selectedGoals.append(goal)
        }
    }
    
    private func completeDataCollection() {
        print("🔥 OnboardingViewModel: Début de la finalisation de la collecte de données")
        isLoading = true
        
        // Simuler un traitement
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            print("🔥 OnboardingViewModel: Fin du chargement simulé")
            self.isLoading = false
            self.currentStep = .authentication
        }
    }
    
    func completeAuthentication() {
        print("🔥 OnboardingViewModel: Authentification terminée, passage à l'abonnement")
        currentStep = .partnerCode
    }
    
    func skipSubscription() {
        print("🔥 OnboardingViewModel: Abonnement ignoré, finalisation")
        print("🔥🔥🔥 ONBOARDING SKIP: ABONNEMENT IGNORE - FINALISATION SANS PREMIUM")
        NSLog("🔥🔥🔥 ONBOARDING SKIP: ABONNEMENT IGNORE")
        finalizeOnboarding(withSubscription: false)
    }
    
    func completeSubscription() {
        // Protection contre les doubles appels
        guard !isCompletingSubscription else {
            print("🔥 OnboardingViewModel: ⚠️ Appel ignoré - Finalisation déjà en cours")
            return
        }
        
        isCompletingSubscription = true
        print("🔥 OnboardingViewModel: Abonnement terminé, finalisation")
        print("🔥🔥🔥 ONBOARDING COMPLETE: ABONNEMENT TERMINE - FINALISATION AVEC PREMIUM")
        NSLog("🔥🔥🔥 ONBOARDING COMPLETE: ABONNEMENT TERMINE")
        finalizeOnboarding(withSubscription: true)
    }
    
    func finalizeOnboarding(withSubscription isSubscribed: Bool = false) {
        print("🔥 OnboardingViewModel: Finalisation complète de l'onboarding")
        print("🔥🔥🔥 ONBOARDING FINALIZE: DEBUT FINALISATION")
        print("🔥🔥🔥 ONBOARDING FINALIZE: - Avec abonnement: \(isSubscribed)")
        NSLog("🔥🔥🔥 ONBOARDING: FINALISATION COMPLETE - VOUS DEVRIEZ VOIR CECI!")
        NSLog("🔥🔥🔥 ONBOARDING: AVEC ABONNEMENT: %@", isSubscribed ? "OUI" : "NON")
        
        // NOUVEAU: Désactiver l'overlay de connexion partenaire de l'onboarding
        // car MainView va prendre le relais
        if shouldShowPartnerConnectionSuccess {
            print("🔥 OnboardingViewModel: Désactivation overlay connexion partenaire (MainView prend le relais)")
            shouldShowPartnerConnectionSuccess = false
        }
        
        print("🔥 OnboardingViewModel: Création de l'utilisateur avec:")
        print("  - Nom: \(userName)")
        print("  - Objectifs: \(selectedGoals)")
        print("  - Durée de relation: \(relationshipDuration)")
        print("  - Amélioration souhaitée: \(selectedImprovements)")
        print("  - Mode de questions: \(questionMode)")
        print("  - Abonné: \(isSubscribed)")
        
        NSLog("🔥🔥🔥 ONBOARDING: CREATION USER - NOM: %@", userName)
        NSLog("🔥🔥🔥 ONBOARDING: CREATION USER - ABONNE: %@", isSubscribed ? "OUI" : "NON")
        
        // Convertir le tableau d'améliorations en string pour Firebase
        let improvementString = selectedImprovements.joined(separator: ", ")
        
        // 🔧 CORRECTION: Préserver les données de connexion partenaire existantes
        FirebaseService.shared.finalizeOnboardingWithPartnerData(
            name: userName,
            relationshipGoals: selectedGoals,
            relationshipDuration: relationshipDuration,
            relationshipImprovement: improvementString.isEmpty ? nil : improvementString,
            questionMode: questionMode.isEmpty ? nil : questionMode,
            isSubscribed: isSubscribed,
            relationshipStartDate: relationshipStartDate,
            profileImage: profileImage,
            currentLocation: currentLocation
        ) { [weak self] success, user in
            DispatchQueue.main.async {
                guard let self = self else { return }
                
                if success, let user = user {
                    print("✅ OnboardingViewModel: Finalisation réussie avec préservation données partenaire")
                    print("🔥🔥🔥 ONBOARDING FINALIZE: USER CREE - ABONNE: \(user.isSubscribed)")
                    print("🔥🔥🔥 ONBOARDING FINALIZE: PARTNER ID: \(user.partnerId ?? "none")")
                    NSLog("🔥🔥🔥 ONBOARDING: USER CREE - ID: %@", user.id)
                    
                    guard let appState = self.appState else {
                        print("❌ OnboardingViewModel: AppState manquant!")
                        NSLog("❌❌❌ ONBOARDING: APPSTATE MANQUANT!")
                        return
                    }
                    
                    print("🔥 OnboardingViewModel: Mise à jour de l'utilisateur via AppState")
                    print("🔥🔥🔥 ONBOARDING FINALIZE: SAUVEGARDE FINALE AVEC ONBOARDING TERMINE")
                    NSLog("🔥🔥🔥 ONBOARDING: MISE A JOUR VIA APPSTATE")
                    
                    // NOUVEAU: Marquer la fin du processus d'onboarding dans Firebase et AppState
                    FirebaseService.shared.completeOnboardingProcess()
                    appState.isOnboardingInProgress = false
                    print("🔥🔥🔥 ONBOARDING FINALIZE: FLAGS ONBOARDING REINITIALISES")
                    
                    appState.updateUser(user)
                    appState.completeOnboarding()
                    print("🔥 OnboardingViewModel: Onboarding terminé")
                    print("🔥🔥🔥 ONBOARDING FINALIZE: ONBOARDING MARQUE COMME TERMINE")
                    NSLog("🔥🔥🔥 ONBOARDING: TERMINE AVEC SUCCES!")
                } else {
                    print("❌ OnboardingViewModel: Erreur lors de la finalisation")
                    NSLog("❌❌❌ ONBOARDING: ERREUR FINALISATION!")
                }
                
                // Reset du flag de protection
                self.isCompletingSubscription = false
            }
        }
    }
    
    // DEPRECATED: Ces méthodes ne sont plus utilisées avec le nouveau flux
    func completeOnboardingAfterSubscription() {
        print("⚠️ OnboardingViewModel: DEPRECATED - completeOnboardingAfterSubscription appelée")
        completeAuthentication()
    }
    
    // NOUVEAU: Méthode pour passer l'abonnement suite à un héritage
    func skipSubscriptionDueToInheritance() {
        print("🔥 OnboardingViewModel: Abonnement hérité du partenaire premium - skip subscription")
        shouldSkipSubscription = true
    }
    
    // NOUVEAU: Méthode pour réinitialiser le flag de skip si nécessaire
    func resetSubscriptionSkip() {
        print("🔥 OnboardingViewModel: Reset flag skip subscription")
        shouldSkipSubscription = false
    }
    
    func handleSubscriptionUpdate() {
        print("🔥 OnboardingViewModel: Mise à jour d'abonnement détectée")
        // Gérer les mises à jour d'abonnement si nécessaire
    }
    
    func showPartnerConnectionSuccess(partnerName: String) {
        print("🎉 OnboardingViewModel: Affichage message connexion pour: \(partnerName)")
        connectedPartnerName = partnerName
        shouldShowPartnerConnectionSuccess = true
    }
    
    func dismissPartnerConnectionSuccess() {
        print("🎉 OnboardingViewModel: Fermeture message connexion")
        shouldShowPartnerConnectionSuccess = false
        connectedPartnerName = ""
    }
}
