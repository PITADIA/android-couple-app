import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var viewModel = OnboardingViewModel()
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond dégradé rouge/orange
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.8, green: 0.2, blue: 0.2),
                        Color(red: 0.9, green: 0.4, blue: 0.1)
                    ]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Barre de progression
                    ProgressBar(progress: viewModel.progressValue)
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
                        case .partnerCode:
                            PartnerCodeStepView(viewModel: viewModel)
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