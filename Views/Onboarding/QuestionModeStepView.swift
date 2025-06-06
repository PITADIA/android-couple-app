import SwiftUI

struct QuestionModeStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre la barre de progression et le titre
            Spacer()
                .frame(height: 60)
            
            // Contenu en haut
            VStack(spacing: 40) {
                // Titre
                Text("Choisis ton mode de question préféré")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
                
                // Options de sélection
                VStack(spacing: 15) {
                    ForEach(viewModel.questionModes, id: \.self) { mode in
                        Button(action: {
                            print("🔥 QuestionModeStepView: Mode sélectionné: \(mode)")
                            viewModel.questionMode = mode
                        }) {
                            HStack {
                                Text(mode)
                                    .font(.system(size: 16))
                                    .foregroundColor(.white)
                                    .multilineTextAlignment(.leading)
                                
                                Spacer()
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 16)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(viewModel.questionMode == mode ? Color.white.opacity(0.3) : Color.white.opacity(0.1))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(Color.white.opacity(0.3), lineWidth: 1)
                                    )
                            )
                        }
                    }
                }
                .padding(.horizontal, 30)
            }
            
            Spacer()
            
            // Bouton Continuer collé en bas
            Button(action: {
                print("🔥 QuestionModeStepView: Bouton Continuer pressé")
                if !viewModel.questionMode.isEmpty {
                    print("🔥 QuestionModeStepView: Mode valide, passage à l'étape suivante")
                    viewModel.nextStep()
                } else {
                    print("❌ QuestionModeStepView: Aucun mode sélectionné")
                }
            }) {
                Text("Continuer")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(hex: "#FD267A"))
                    .cornerRadius(28)
                    .opacity(viewModel.questionMode.isEmpty ? 0.5 : 1.0)
            }
            .disabled(viewModel.questionMode.isEmpty)
            .padding(.horizontal, 30)
            .padding(.bottom, 50)
        }
        .onAppear {
            print("🔥 QuestionModeStepView: Vue de mode de questions apparue")
        }
    }
}

#Preview {
    QuestionModeStepView(viewModel: OnboardingViewModel())
} 