import SwiftUI

struct NameStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @FocusState private var isTextFieldFocused: Bool
    
    var body: some View {
        VStack(spacing: 40) {
            // Titre
            VStack(spacing: 10) {
                Text("AVANT DE COMMENCER,")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("COMMENT TU T'APPELLES ?")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 30)
            
            // Champ de saisie
            VStack(spacing: 20) {
                TextField("Entrez votre prénom", text: $viewModel.userName)
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.white.opacity(0.2))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.white.opacity(0.3), lineWidth: 1)
                            )
                    )
                    .focused($isTextFieldFocused)
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                            isTextFieldFocused = true
                        }
                    }
                    .onChange(of: viewModel.userName) { _, newName in
                        print("🔥 NameStepView: Nom saisi: '\(newName)'")
                    }
                
                // Bouton Continuer
                Button(action: {
                    print("🔥 NameStepView: Bouton Continuer pressé")
                    if !viewModel.userName.isEmpty {
                        print("🔥 NameStepView: Nom valide, passage à l'étape suivante")
                        viewModel.nextStep()
                    } else {
                        print("❌ NameStepView: Nom vide, impossible de continuer")
                    }
                }) {
                    Text("Continuer")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            LinearGradient(
                                gradient: Gradient(colors: [
                                    Color.orange,
                                    Color.red
                                ]),
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(28)
                        .opacity(viewModel.userName.isEmpty ? 0.5 : 1.0)
                }
                .disabled(viewModel.userName.isEmpty)
            }
            .padding(.horizontal, 30)
            
            // Suggestions de prénoms
            HStack(spacing: 20) {
                ForEach(["je", "tu", "c'est"], id: \.self) { suggestion in
                    Button(suggestion) {
                        print("🔥 NameStepView: Suggestion sélectionnée: '\(suggestion)'")
                        viewModel.userName = suggestion
                    }
                    .foregroundColor(.white.opacity(0.7))
                    .font(.system(size: 16))
                }
            }
            
            Spacer()
        }
        .onAppear {
            print("🔥 NameStepView: Vue de saisie du nom apparue")
            NSLog("🔥🔥🔥 ONBOARDING DEBUT: NAME STEP APPARUE - DEBUT DU PROCESSUS!")
        }
    }
} 