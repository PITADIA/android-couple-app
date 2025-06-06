import SwiftUI

struct LoadingStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 40) {
            Spacer()
            
            // Animation de chargement
            VStack(spacing: 30) {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(2.0)
                
                Text("Préparation de votre profil...")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            
            Spacer()
        }
        .onAppear {
            print("🔥 LoadingStepView: Vue de chargement apparue")
            print("🔥 LoadingStepView: État de chargement: \(viewModel.isLoading)")
        }
    }
} 