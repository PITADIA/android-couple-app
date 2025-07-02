import SwiftUI

struct FitnessIntroStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("Sauvegardez vos moments passés ensemble")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Premier Spacer pour centrer le contenu
            Spacer()
            
            // Image de Marie centrée
            Image("marie")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxWidth: .infinity, maxHeight: 700)
                .cornerRadius(20)
                .padding(.horizontal, 15)
            
            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer()
                
            // Zone blanche collée en bas
            VStack(spacing: 0) {
                Button(action: {
                    viewModel.nextStep()
                }) {
                    Text("Continuer")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color(hex: "#FD267A"))
                        .cornerRadius(28)
                }
                .padding(.horizontal, 30)
            }
            .padding(.vertical, 30)
            .background(Color.white)
            .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
        }
    }
}

#Preview {
    FitnessIntroStepView(viewModel: OnboardingViewModel())
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
} 