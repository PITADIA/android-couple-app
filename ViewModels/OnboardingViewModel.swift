import Foundation
import Combine
import UIKit
import FirebaseAnalytics

class OnboardingViewModel: ObservableObject {
    enum OnboardingStep: CaseIterable {
        case relationshipGoals
        case relationshipDate
        case relationshipImprovement
        case authentication
        case displayName
        case profilePhoto
        case completion
        case loading
        case partnerCode
        case fitnessIntro
        case fitnessIntro2
        case dailyQuestionNotification
        case categoriesPreview
        case subscription
    }
    
    @Published var currentStep: OnboardingStep = .relationshipGoals
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
        let stepNumber = OnboardingStep.allCases.firstIndex(of: currentStep) ?? 0
        
        // 📊 Analytics: Progression onboarding
        Analytics.logEvent("onboarding_etape", parameters: ["etape": stepNumber])
        print("📊 Événement Firebase: onboarding_etape - étape: \(stepNumber)")
        
        switch currentStep {
        case .relationshipGoals:
            currentStep = .relationshipDate
        case .relationshipDate:
            currentStep = .relationshipImprovement
        case .relationshipImprovement:
            currentStep = .authentication
        case .authentication:
            currentStep = .displayName
        case .displayName:
            // Toujours permettre de passer à profilePhoto, même avec nom vide (auto-génération)
            print("🔥 OnboardingViewModel: displayName -> profilePhoto (nom: '\(userName)')")
            currentStep = .profilePhoto
        case .profilePhoto:
            currentStep = .completion
        case .completion:
            currentStep = .loading
            completeDataCollection()
        case .loading:
            if shouldSkipSubscription {
                finalizeOnboarding(withSubscription: true)
            } else {
                currentStep = .partnerCode
            }
        case .partnerCode:
            currentStep = .categoriesPreview
        case .categoriesPreview:
            currentStep = .fitnessIntro
        case .fitnessIntro:
            currentStep = .fitnessIntro2
        case .fitnessIntro2:
            currentStep = .dailyQuestionNotification
        case .dailyQuestionNotification:
            currentStep = .subscription
        case .subscription:
            // L'onboarding doit être finalisé via skipSubscription() ou completeSubscription()
            break
        }
    }
    
    func previousStep() {
        print("🔥 OnboardingViewModel: Retour à l'étape précédente depuis \(currentStep)")
        switch currentStep {
        case .relationshipGoals:
            print("🔥 OnboardingViewModel: Déjà à la première étape")
            break
        case .relationshipDate:
            currentStep = .relationshipGoals
        case .relationshipImprovement:
            currentStep = .relationshipDate
        case .authentication:
            currentStep = .relationshipImprovement
        case .displayName:
            currentStep = .authentication
        case .profilePhoto:
            // Retour conditionnel : si on a un nom Apple, retourner à auth, sinon à displayName
            if let appleDisplayName = AuthenticationService.shared.appleUserDisplayName, !appleDisplayName.isEmpty {
                currentStep = .authentication
            } else {
                currentStep = .displayName
            }
        case .completion:
            currentStep = .profilePhoto
        case .loading:
            currentStep = .completion
        case .partnerCode:
            currentStep = .loading
        case .categoriesPreview:
            currentStep = .partnerCode
        case .fitnessIntro:
            currentStep = .categoriesPreview
        case .fitnessIntro2:
            currentStep = .fitnessIntro
        case .dailyQuestionNotification:
            currentStep = .fitnessIntro2
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
            self.currentStep = .partnerCode
        }
    }
    
    func completeAuthentication() {
        print("🔥 OnboardingViewModel: Authentification terminée")
        print("🔥 OnboardingViewModel: Attente du traitement Apple Name...")
        
        // NOUVEAU: Attendre que AuthenticationService ait fini de traiter appleUserDisplayName
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.checkAppleNameAndProceed()
        }
    }
    
    private func checkAppleNameAndProceed() {
        print("🔥 OnboardingViewModel: Vérification nom Apple...")
        
        let appleDisplayName = AuthenticationService.shared.appleUserDisplayName
        print("🔥 OnboardingViewModel: appleUserDisplayName = '\(appleDisplayName ?? "nil")'")
        print("🔥 OnboardingViewModel: isEmpty = \(appleDisplayName?.isEmpty ?? true)")
        
        // Vérifier si Apple a fourni un nom d'affichage
        if let appleDisplayName = appleDisplayName, !appleDisplayName.isEmpty {
            print("🔥 OnboardingViewModel: ✅ Nom Apple fourni (\(appleDisplayName)) - Skip DisplayNameStepView")
            // Utiliser le nom fourni par Apple
            self.userName = appleDisplayName
            print("🔥 OnboardingViewModel: userName défini à: '\(self.userName)'")
            // Passer directement à la photo de profil
            print("🔥 OnboardingViewModel: Navigation vers .profilePhoto")
            self.currentStep = .profilePhoto
        } else {
            print("🔥 OnboardingViewModel: ❌ Aucun nom Apple fourni - Afficher DisplayNameStepView")
            // Pas de nom fourni, demander un pseudonyme
            print("🔥 OnboardingViewModel: Navigation vers .displayName")
            self.currentStep = .displayName
        }
    }
    
    func skipSubscription() {
        print("🔥 OnboardingViewModel: Abonnement ignoré, finalisation")
        print("⏭️ Onboarding sans abonnement")
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
        print("✅ Onboarding avec abonnement")
        finalizeOnboarding(withSubscription: true)
    }
    
    func finalizeOnboarding(withSubscription isSubscribed: Bool = false) {
        print("🔥 OnboardingViewModel: Finalisation complète de l'onboarding")
        print("🎯 Finalisation onboarding")
        
        // NOUVEAU: Désactiver l'overlay de connexion partenaire de l'onboarding
        // car MainView va prendre le relais
        if shouldShowPartnerConnectionSuccess {
            print("🔥 OnboardingViewModel: Désactivation overlay connexion partenaire (MainView prend le relais)")
            shouldShowPartnerConnectionSuccess = false
        }
        
        #if DEBUG
        print("🔥 OnboardingViewModel: Création de l'utilisateur avec:")
        print("  - Nom: \(userName)")
        print("  - Objectifs: \(selectedGoals)")
        print("  - Durée de relation: \(relationshipDuration)")
        print("  - Amélioration souhaitée: \(selectedImprovements)")
        print("  - Mode de questions: \(questionMode)")
        print("  - Abonné: \(isSubscribed)")
        #else
        print("🔥 OnboardingViewModel: Création de l'utilisateur terminée")
        #endif
        
        print("👤 Création utilisateur final")
        
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
                            print("✅ Utilisateur créé avec succès")
                    
                    guard let appState = self.appState else {
                        print("❌ OnboardingViewModel: AppState manquant!")
                        NSLog("❌❌❌ ONBOARDING: APPSTATE MANQUANT!")
                        return
                    }
                    
                    print("🔥 OnboardingViewModel: Mise à jour de l'utilisateur via AppState")
                            print("📱 Mise à jour interface")
                    
                    // 📊 Analytics: Onboarding terminé
                    Analytics.logEvent("onboarding_complete", parameters: [:])
                    print("📊 Événement Firebase: onboarding_complete")
                    
                    // NOUVEAU: Marquer la fin du processus d'onboarding dans Firebase et AppState
                    FirebaseService.shared.completeOnboardingProcess()
                    appState.isOnboardingInProgress = false
                    // Réinitialisation des flags d'onboarding
                    
                    appState.updateUser(user)
                    appState.completeOnboarding()
                    print("🔥 OnboardingViewModel: Onboarding terminé")
                            print("🎉 Onboarding terminé avec succès")
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
