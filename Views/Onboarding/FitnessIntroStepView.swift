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
                Text("save_moments_together".localized)
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Premier Spacer pour centrer le contenu
            Spacer()
            
            // Image localisée selon la langue
            Image(LocalizationService.localizedImageName(frenchImage: "marie", defaultImage: "macafee"))
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxWidth: .infinity, maxHeight: 700)
                .cornerRadius(20)
                .padding(.horizontal, 15)
                .onAppear {
                    let selectedImage = LocalizationService.localizedImageName(frenchImage: "marie", defaultImage: "macafee")
                    print("🖼️ FitnessIntroStepView: Vue apparue - Affichage de l'image: \(selectedImage)")
                    print("🖼️ FitnessIntroStepView: Titre de la page: \"save_moments_together\"")
                }
            
            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer()
                
            // Zone blanche collée en bas
            VStack(spacing: 0) {
                Button(action: {
                    viewModel.nextStep()
                }) {
                    Text("continue".localized)
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
        .onAppear {
            print("🖼️ FitnessIntroStepView: 📱 Étape d'onboarding 'save_moments_together' affichée")
            print("🖼️ FitnessIntroStepView: 🌍 Paramètres système:")
            print("🖼️ FitnessIntroStepView:   - Langue: \(Locale.current.language.languageCode?.identifier ?? "inconnue")")
            print("🖼️ FitnessIntroStepView:   - Région: \(Locale.current.region?.identifier ?? "inconnue")")
            print("🖼️ FitnessIntroStepView:   - Devise: \(Locale.current.currency?.identifier ?? "inconnue")")
        }
    }
}

#Preview {
    FitnessIntroStepView(viewModel: OnboardingViewModel())
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
} 