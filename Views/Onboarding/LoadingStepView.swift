import SwiftUI

struct LoadingStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var currentMessageIndex = 0
    @State private var loadingTimer: Timer?
    
    private let loadingMessages = [
        "loading_profile".localized,
        "loading_preferences".localized,
        "loading_experience".localized
    ]
    
    var body: some View {
        ZStack {
            // Fond gris clair identique aux autres pages d'onboarding
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            VStack(spacing: 40) {
                Spacer()
                
                // Animation de chargement
                VStack(spacing: 30) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .black))
                        .scaleEffect(2.0)
                    
                    Text(loadingMessages[currentMessageIndex])
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)

                }
                
                Spacer()
            }
        }
        .onAppear {
            print("🔥 LoadingStepView: Vue de chargement apparue")
            print("🔥 LoadingStepView: État de chargement: \(viewModel.isLoading)")
            startLoadingSequence()
        }
        .onDisappear {
            loadingTimer?.invalidate()
        }
    }
    
    private func startLoadingSequence() {
        print("🔥 LoadingStepView: Début de la séquence de chargement")
        
        // Timer pour changer les messages toutes les 5 secondes
        loadingTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { _ in
            withAnimation(.easeInOut(duration: 0.5)) {
                currentMessageIndex = (currentMessageIndex + 1) % loadingMessages.count
            }
            print("🔥 LoadingStepView: Changement de message: \(loadingMessages[currentMessageIndex])")
        }
        
        // SUPPRIMÉ: Le timer automatique de 15 secondes qui forçait l'avancement
        // L'avancement doit maintenant être géré manuellement via completeDataCollection()
        print("🔥 LoadingStepView: Attente de la finalisation manuelle de la collecte de données")
    }
} 