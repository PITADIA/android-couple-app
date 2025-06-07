import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var viewModel = OnboardingViewModel()
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond dégradé personnalisé avec les nouvelles couleurs
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FD267A"),
                        Color(hex: "#FF655B")
                    ]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Barre de progression (masquée pour la page de paiement)
                    if viewModel.currentStep != .subscription {
                        ProgressBar(progress: viewModel.progressValue) {
                            viewModel.previousStep()
                        }
                        .padding(.top, 60)
                        .padding(.horizontal, 20)
                    }
                    
                    // Contenu de l'étape actuelle
                    Group {
                        switch viewModel.currentStep {
                        case .name:
                            NameStepView(viewModel: viewModel)
                        case .birthDate:
                            BirthDateStepView(viewModel: viewModel)
                        case .relationshipGoals:
                            RelationshipGoalsStepView(viewModel: viewModel)
                        case .relationshipDuration:
                            RelationshipDurationStepView(viewModel: viewModel)
                        case .relationshipImprovement:
                            RelationshipImprovementStepView(viewModel: viewModel)
                        case .questionMode:
                            QuestionModeStepView(viewModel: viewModel)
                        case .completion:
                            CompletionStepView(viewModel: viewModel)
                        case .loading:
                            LoadingStepView(viewModel: viewModel)
                        case .subscription:
                            SubscriptionStepView(viewModel: viewModel)
                        case .authentication:
                            AuthenticationStepView(viewModel: viewModel)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            print("🔥 OnboardingView: Vue apparue")
            print("🔥 OnboardingView: Étape actuelle: \(viewModel.currentStep)")
            viewModel.updateAppState(appState)
            
            // NOUVEAU: Vérifier si l'utilisateur était déjà dans le processus d'onboarding
            // Ajouter un délai pour s'assurer que les données utilisateur sont chargées
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                if appState.isOnboardingInProgress {
                    print("🔥🔥🔥 OnboardingView: ONBOARDING DEJA EN COURS - RECUPERER ETAPE")
                    // Si l'utilisateur était dans l'onboarding et qu'il a des données partielles,
                    // le diriger vers l'étape d'abonnement
                    if let user = appState.currentUser, user.onboardingInProgress {
                        print("🔥🔥🔥 OnboardingView: USER PARTIEL DETECTE - ALLER A SUBSCRIPTION")
                        print("🔥🔥🔥 OnboardingView: - Nom: \(user.name)")
                        print("🔥🔥🔥 OnboardingView: - Objectifs: \(user.relationshipGoals)")
                        
                        // Restaurer les données dans le viewModel
                        viewModel.userName = user.name
                        viewModel.birthDate = user.birthDate
                        viewModel.selectedGoals = user.relationshipGoals
                        viewModel.relationshipDuration = user.relationshipDuration
                        viewModel.relationshipImprovement = user.relationshipImprovement ?? ""
                        viewModel.questionMode = user.questionMode ?? ""
                        
                        // Aller directement à l'étape d'abonnement
                        viewModel.currentStep = .subscription
                        print("🔥🔥🔥 OnboardingView: ETAPE FORCEE A SUBSCRIPTION")
                    }
                }
            }
        }
        .onChange(of: viewModel.currentStep) { _, newStep in
            print("🔥 OnboardingView: Changement d'étape vers: \(newStep)")
        }
    }
} 