import SwiftUI

struct LoadingStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var currentMessageIndex = 0
    @State private var loadingTimer: Timer?
    
    private let loadingMessages = [
        "Préparation de votre profil...",
        "Analyse de vos préférences...",
        "Personnalisation de votre expérience..."
    ]
    
    var body: some View {
        VStack(spacing: 40) {
            Spacer()
            
            // Animation de chargement
            VStack(spacing: 30) {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(2.0)
                
                Text(loadingMessages[currentMessageIndex])
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .animation(.easeInOut(duration: 0.5), value: currentMessageIndex)
            }
            
            Spacer()
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
        print("🔥 LoadingStepView: Début de la séquence de chargement de 15 secondes")
        
        // Timer pour changer les messages toutes les 5 secondes
        loadingTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { _ in
            withAnimation(.easeInOut(duration: 0.5)) {
                currentMessageIndex = (currentMessageIndex + 1) % loadingMessages.count
            }
            print("🔥 LoadingStepView: Changement de message: \(loadingMessages[currentMessageIndex])")
        }
        
        // Timer pour terminer le chargement après 15 secondes
        DispatchQueue.main.asyncAfter(deadline: .now() + 15.0) {
            print("🔥 LoadingStepView: Fin du chargement après 15 secondes")
            loadingTimer?.invalidate()
            viewModel.nextStep()
        }
    }
} 