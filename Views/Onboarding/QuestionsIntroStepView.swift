import SwiftUI

struct QuestionsIntroStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                                                              Text("questions_intro_title".localizedOnboarding)
                           .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Espace entre titre et image
            Spacer()
                .frame(height: 40)
            
            // Image de la page de présentation des questions du jour
                    Image("mima") // Image utilisée dans DailyQuestionIntroView
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(maxWidth: .infinity, maxHeight: 280)
            .cornerRadius(20)
            .padding(.horizontal, 30)
                .onAppear {
                    print("🖼️ QuestionsIntroStepView: Vue apparue - Affichage de l'image: mima")
                    print("🖼️ QuestionsIntroStepView: Titre de la page: \"questions_intro_title\"")
                }
            
            // Sous-titre descriptif (style paywall)
            VStack(spacing: 16) {
                                       Text("questions_intro_subtitle".localizedOnboarding)
                    .font(.system(size: 16))
                    .foregroundColor(.black.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 30)
            }
            .padding(.top, 30)
            
            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer()
                
            // Zone blanche collée en bas (style CategoriesPreviewStepView)
            VStack(spacing: 0) {
                Button(action: {
                    print("🔥 QuestionsIntroStepView: Bouton continuer pressé")
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
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
    }
}

#Preview {
    QuestionsIntroStepView(viewModel: OnboardingViewModel())
}
