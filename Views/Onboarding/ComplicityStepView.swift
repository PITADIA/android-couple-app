import SwiftUI

struct ComplicityStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    private let complicityOptions = [
        "complicity_strong_fulfilling",
        "complicity_present",
        "complicity_sometimes_lacking",
        "complicity_need_help"
    ]
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("complicity_question".localizedOnboarding)
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
                ForEach(Array(complicityOptions.enumerated()), id: \.offset) { index, localizationKey in
                    Button(action: {
                        print("🔥 ComplicityStepView: Option sélectionnée: \(localizationKey)")
                        viewModel.complicityAnswer = localizationKey
                    }) {
                        Text(localizationKey.localizedOnboarding)
                            .font(.system(size: 16))
                            .foregroundColor(viewModel.complicityAnswer == localizationKey ? .white : .black)
                            .multilineTextAlignment(.leading)
                            .lineLimit(nil)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 16)
                            .padding(.horizontal, 20)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(viewModel.complicityAnswer == localizationKey ? Color(hex: "#FD267A") : Color.white)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(
                                                viewModel.complicityAnswer == localizationKey ? Color(hex: "#FD267A") : Color.black.opacity(0.1),
                                                lineWidth: viewModel.complicityAnswer == localizationKey ? 2 : 1
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
                    print("🔥 ComplicityStepView: Bouton continuer pressé")
                    viewModel.nextStep()
                }) {
                    Text("continue".localized)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color(hex: "#FD267A"))
                        .cornerRadius(28)
                        .opacity(viewModel.complicityAnswer.isEmpty ? 0.5 : 1.0)
                }
                .disabled(viewModel.complicityAnswer.isEmpty)
                .padding(.horizontal, 30)
            }
            .padding(.vertical, 30)
            .background(Color.white)
        }
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
    }
}

#Preview {
    ComplicityStepView(viewModel: OnboardingViewModel())
}
