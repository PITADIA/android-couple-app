import SwiftUI

struct FitnessIntro2StepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("Créez votre carte à souvenirs")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Premier Spacer pour centrer le contenu
            Spacer()
            
            // Image de Hajar centrée
            Image("hajar")
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(maxWidth: .infinity, maxHeight: 400)
                .clipped()
                .cornerRadius(30)
                .padding(.horizontal, 30)
            
            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer()
                
            // Zone blanche collée en bas
            VStack(spacing: 0) {
                Button("Continuer") {
                    viewModel.nextStep()
                }
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Color(hex: "#FD267A"))
                .cornerRadius(28)
                .padding(.horizontal, 30)
                .padding(.vertical, 30)
            }
            .background(Color.white)
            .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
        }
    }
}

#Preview {
    FitnessIntro2StepView(viewModel: OnboardingViewModel())
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
} 