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
                Text("create_memory_card".localized)
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Premier Spacer pour centrer le contenu
            Spacer()
            
            // Image localisée selon la langue
            Image(LocalizationService.localizedImageName(frenchImage: "hajar", defaultImage: "wework"))
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(maxWidth: .infinity, maxHeight: 400)
                .clipped()
                .cornerRadius(30)
                .padding(.horizontal, 30)
                .onAppear {
                    let selectedImage = LocalizationService.localizedImageName(frenchImage: "hajar", defaultImage: "wework")
                    print("🖼️ FitnessIntro2StepView: Vue apparue - Affichage de l'image: \(selectedImage)")
                    print("🖼️ FitnessIntro2StepView: Titre de la page: \"create_memory_card\"")
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
            print("🖼️ FitnessIntro2StepView: 📱 Étape d'onboarding 'create_memory_card' affichée")
            print("🖼️ FitnessIntro2StepView: 🌍 Paramètres système:")
            print("🖼️ FitnessIntro2StepView:   - Langue: \(Locale.current.language.languageCode?.identifier ?? "inconnue")")
            print("🖼️ FitnessIntro2StepView:   - Région: \(Locale.current.region?.identifier ?? "inconnue")")
            print("🖼️ FitnessIntro2StepView:   - Devise: \(Locale.current.currency?.identifier ?? "inconnue")")
        }
    }
}

#Preview {
    FitnessIntro2StepView(viewModel: OnboardingViewModel())
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
} 