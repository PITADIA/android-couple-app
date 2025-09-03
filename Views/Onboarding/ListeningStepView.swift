import SwiftUI

struct ListeningStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    private let listeningOptions = [
        "listening_most_of_time",
        "listening_sometimes",
        "listening_rarely"
    ]
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("listening_question".localizedOnboarding)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Sous-titre explicatif
            HStack {
                Text("private_answer_note".localizedOnboarding)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundColor(.black.opacity(0.6))
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            .padding(.top, 8)
            
            Spacer()
            
            // Options de réponse
            VStack(spacing: 12) {
                ForEach(Array(listeningOptions.enumerated()), id: \.offset) { index, localizationKey in
                    Button(action: {
                        print("🔥 ListeningStepView: Option sélectionnée: \(localizationKey)")
                        viewModel.listeningAnswer = localizationKey
                    }) {
                        Text(localizationKey.localizedOnboarding)
                            .font(.system(size: 16))
                            .foregroundColor(viewModel.listeningAnswer == localizationKey ? .white : .black)
                            .multilineTextAlignment(.leading)
                            .lineLimit(nil)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 16)
                            .padding(.horizontal, 20)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(viewModel.listeningAnswer == localizationKey ? Color(hex: "#FD267A") : Color.white)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(
                                                viewModel.listeningAnswer == localizationKey ? Color(hex: "#FD267A") : Color.black.opacity(0.1),
                                                lineWidth: viewModel.listeningAnswer == localizationKey ? 2 : 1
                                            )
                                    )
                                    .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 4)
                            )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 30)
            
            Spacer()
            
            // Zone blanche collée en bas avec bouton "Continuer"
            VStack(spacing: 0) {
                Button(action: {
                    print("🔥 ListeningStepView: Bouton continuer pressé")
                    viewModel.nextStep()
                }) {
                    Text("continue".localized)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color(hex: "#FD267A"))
                        .cornerRadius(28)
                        .opacity(viewModel.listeningAnswer.isEmpty ? 0.5 : 1.0)
                }
                .disabled(viewModel.listeningAnswer.isEmpty)
                .padding(.horizontal, 30)
            }
            .padding(.vertical, 30)
            .background(Color.white)
        }
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
    }
}

#Preview {
    ListeningStepView(viewModel: OnboardingViewModel())
}
