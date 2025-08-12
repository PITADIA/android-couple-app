import SwiftUI

struct DailyQuestionNotificationStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("daily_question_onboarding_title".localized)
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Premier Spacer pour centrer le contenu
            Spacer()
            
            // Image localisée selon la langue (FR/EN existants + DE ajouté)
            Image(LocalizationService.localizedImageName(frenchImage: "mimarouf", defaultImage: "aroufmima", germanImage: "mimallemand"))
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxWidth: .infinity, maxHeight: 500)
                .cornerRadius(30)
                .padding(.horizontal, 30)
                .onAppear {
                    let selectedImage = LocalizationService.localizedImageName(frenchImage: "mimarouf", defaultImage: "aroufmima", germanImage: "mimallemand")
                    print("🖼️ DailyQuestionNotificationStepView: Vue apparue - Affichage de l'image: \(selectedImage)")
                    print("🖼️ DailyQuestionNotificationStepView: Titre de la page: \"daily_question_onboarding_title\"")
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
            print("🖼️ DailyQuestionNotificationStepView: 📱 Étape d'onboarding 'daily_question_onboarding_title' affichée")
            print("🖼️ DailyQuestionNotificationStepView: 🌍 Paramètres système:")
            print("🖼️ DailyQuestionNotificationStepView:   - Langue: \(Locale.current.language.languageCode?.identifier ?? "inconnue")")
            print("🖼️ DailyQuestionNotificationStepView:   - Région: \(Locale.current.region?.identifier ?? "inconnue")")
            print("🖼️ DailyQuestionNotificationStepView:   - Devise: \(Locale.current.currency?.identifier ?? "inconnue")")
        }
    }
}

#Preview {
    DailyQuestionNotificationStepView(viewModel: OnboardingViewModel())
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
} 