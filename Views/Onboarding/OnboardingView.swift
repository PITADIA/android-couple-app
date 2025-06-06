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
                    // Barre de progression
                    ProgressBar(progress: viewModel.progressValue) {
                        viewModel.previousStep()
                    }
                    .padding(.top, 60)
                    .padding(.horizontal, 20)
                    
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
        }
        .onChange(of: viewModel.currentStep) { _, newStep in
            print("🔥 OnboardingView: Changement d'étape vers: \(newStep)")
        }
    }
} 