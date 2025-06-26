import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var viewModel = OnboardingViewModel()
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique aux pages journal
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Barre de progression (masquée pour certaines pages)
                    if viewModel.currentStep != .subscription && viewModel.currentStep != .fitnessIntro && viewModel.currentStep != .fitnessIntro2 && viewModel.currentStep != .categoriesPreview {
                        ProgressBar(progress: viewModel.progressValue) {
                            viewModel.previousStep()
                        }
                        .padding(.top, 20)
                        .padding(.horizontal, 20)
                    }
                    
                    // Contenu de l'étape actuelle
                    Group {
                        switch viewModel.currentStep {
                        case .name:
                            NameStepView(viewModel: viewModel)
                        case .profilePhoto:
                            ProfilePhotoStepView(viewModel: viewModel)
                        case .relationshipGoals:
                            RelationshipGoalsStepView(viewModel: viewModel)
                        case .relationshipDate:
                            RelationshipDateStepView(viewModel: viewModel)
                        case .relationshipImprovement:
                            RelationshipImprovementStepView(viewModel: viewModel)
                        case .partnerCode:
                            PartnerCodeStepView(viewModel: viewModel)
                        case .fitnessIntro:
                            FitnessIntroStepView(viewModel: viewModel)
                        case .fitnessIntro2:
                            FitnessIntro2StepView(viewModel: viewModel)
                        case .categoriesPreview:
                            CategoriesPreviewStepView(viewModel: viewModel)
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
            
            // Vérification immédiate sans délai pour éviter les hangs
            if appState.isOnboardingInProgress {
                print("🔥🔥🔥 OnboardingView: ONBOARDING DEJA EN COURS - RECUPERER ETAPE")
                if let user = appState.currentUser, user.onboardingInProgress {
                    print("🔥🔥🔥 OnboardingView: USER PARTIEL DETECTE - ALLER A SUBSCRIPTION")
                    print("🔥🔥🔥 OnboardingView: - Nom: \(user.name)")
                    print("🔥🔥🔥 OnboardingView: - Objectifs: \(user.relationshipGoals)")
                    
                    // Restaurer les données dans le viewModel
                    viewModel.userName = user.name
                    viewModel.birthDate = user.birthDate
                    viewModel.selectedGoals = user.relationshipGoals
                    viewModel.relationshipDuration = user.relationshipDuration
                    if let improvementString = user.relationshipImprovement, !improvementString.isEmpty {
                        viewModel.selectedImprovements = improvementString.components(separatedBy: ", ")
                    }
                    viewModel.questionMode = user.questionMode ?? ""
                    
                    // Aller directement à l'étape d'abonnement
                    viewModel.currentStep = .subscription
                    print("🔥🔥🔥 OnboardingView: ETAPE FORCEE A SUBSCRIPTION")
                }
            }
        }
        .onChange(of: viewModel.currentStep) { _, newStep in
            print("🔥 OnboardingView: Changement d'étape vers: \(newStep)")
        }
        // NOUVEAU: Overlay pour le message de connexion partenaire réussie
        .overlay(
            Group {
                if viewModel.shouldShowPartnerConnectionSuccess {
                    PartnerConnectionSuccessView(
                        partnerName: viewModel.connectedPartnerName
                    ) {
                        viewModel.dismissPartnerConnectionSuccess()
                        // Si on était sur l'étape partenaire, continuer l'onboarding
                        if viewModel.currentStep == .partnerCode {
                            viewModel.nextStep()
                        }
                    }
                    .transition(.opacity)
                    .zIndex(1000)
                }
            }
        )
    }
} 