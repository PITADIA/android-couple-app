import Foundation
import Combine

class OnboardingViewModel: ObservableObject {
    enum OnboardingStep: CaseIterable {
        case name
        case birthDate
        case relationshipGoals
        case relationshipDuration
        case relationshipImprovement
        case questionMode
        case completion
        case loading
        case authentication
        case subscription
    }
    
    @Published var currentStep: OnboardingStep = .name
    @Published var userName: String = ""
    @Published var birthDate: Date = Date()
    @Published var selectedGoals: [String] = []
    @Published var relationshipDuration: User.RelationshipDuration = .none
    @Published var relationshipImprovement: String = ""
    @Published var questionMode: String = ""
    @Published var isLoading: Bool = false
    
    var appState: AppState?
    private var cancellables = Set<AnyCancellable>()
    
    // Options pour les objectifs de relation
    let relationshipGoals = [
        "👫 Créer une vraie connexion",
        "🔥 Parler enfin des sujets qu'on évite",
        "🌶️ Faire monter la passion entre nous",
        "🎉 Partager plus de rires ensemble",
        "💕 Retrouver une vraie complicité"
    ]

    // Options pour l'amélioration de la relation
    let relationshipImprovements = [
        "✨ Créer un moment fort à deux",
        "💕 Raviver notre connexion",
        "🔄 Sortir de la routine",
        "💬 Se dire ce qu'on n'a jamais dit"
    ]
    
    // Options pour le mode de questions
    let questionModes = [
        "🎯 Sérieux",
        "🎉 Fun",
        "🔥 Hot et Sensuel",
        "💭 Profond"
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
                currentStep = .birthDate
            }
        case .birthDate:
            currentStep = .relationshipGoals
        case .relationshipGoals:
            currentStep = .relationshipDuration
        case .relationshipDuration:
            if relationshipDuration != .none {
                currentStep = .relationshipImprovement
            }
        case .relationshipImprovement:
            currentStep = .questionMode
        case .questionMode:
            currentStep = .completion
        case .completion:
            currentStep = .loading
            completeDataCollection()
        case .loading:
            currentStep = .authentication
        case .authentication:
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
        case .birthDate:
            currentStep = .name
        case .relationshipGoals:
            currentStep = .birthDate
        case .relationshipDuration:
            currentStep = .relationshipGoals
        case .relationshipImprovement:
            currentStep = .relationshipDuration
        case .questionMode:
            currentStep = .relationshipImprovement
        case .completion:
            currentStep = .questionMode
        case .loading:
            currentStep = .completion
        case .authentication:
            print("🔥 OnboardingViewModel: Impossible de revenir en arrière depuis l'authentification")
            break
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
        currentStep = .subscription
    }
    
    func skipSubscription() {
        print("🔥 OnboardingViewModel: Abonnement ignoré, finalisation")
        print("🔥🔥🔥 ONBOARDING SKIP: ABONNEMENT IGNORE - FINALISATION SANS PREMIUM")
        NSLog("🔥🔥🔥 ONBOARDING SKIP: ABONNEMENT IGNORE")
        finalizeOnboarding(withSubscription: false)
    }
    
    func completeSubscription() {
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
        
        print("🔥 OnboardingViewModel: Création de l'utilisateur avec:")
        print("  - Nom: \(userName)")
        print("  - Date de naissance: \(birthDate)")
        print("  - Objectifs: \(selectedGoals)")
        print("  - Durée de relation: \(relationshipDuration)")
        print("  - Amélioration souhaitée: \(relationshipImprovement)")
        print("  - Mode de questions: \(questionMode)")
        print("  - Abonné: \(isSubscribed)")
        
        NSLog("🔥🔥🔥 ONBOARDING: CREATION USER - NOM: %@", userName)
        NSLog("🔥🔥🔥 ONBOARDING: CREATION USER - ABONNE: %@", isSubscribed ? "OUI" : "NON")
        
        let user = User(
            name: userName,
            birthDate: birthDate,
            relationshipGoals: selectedGoals,
            relationshipDuration: relationshipDuration,
            relationshipImprovement: relationshipImprovement.isEmpty ? nil : relationshipImprovement,
            questionMode: questionMode.isEmpty ? nil : questionMode,
            partnerCode: nil,
            isSubscribed: isSubscribed,
            onboardingInProgress: false
        )
        
        print("🔥 OnboardingViewModel: Utilisateur créé avec ID: \(user.id)")
        print("🔥🔥🔥 ONBOARDING FINALIZE: USER CREE - ABONNE: \(user.isSubscribed)")
        NSLog("🔥🔥🔥 ONBOARDING: USER CREE - ID: %@", user.id)
        
        guard let appState = appState else {
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
        
        // IMPORTANT: Sauvegarder avec saveUserData pour marquer l'onboarding comme terminé
        FirebaseService.shared.saveUserData(user)
        
        appState.updateUser(user)
        appState.completeOnboarding()
        print("🔥 OnboardingViewModel: Onboarding terminé")
        print("🔥🔥🔥 ONBOARDING FINALIZE: ONBOARDING MARQUE COMME TERMINE")
        NSLog("🔥🔥🔥 ONBOARDING: TERMINE AVEC SUCCES!")
    }
    
    // DEPRECATED: Ces méthodes ne sont plus utilisées avec le nouveau flux
    func completeOnboardingAfterSubscription() {
        print("⚠️ OnboardingViewModel: DEPRECATED - completeOnboardingAfterSubscription appelée")
        completeAuthentication()
    }
}
