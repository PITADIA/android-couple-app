import SwiftUI

struct NameStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @FocusState private var isTextFieldFocused: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            
            // Contenu centré
        VStack(spacing: 40) {
                // Titre et sous-titre
                VStack(spacing: 20) {
                    Text("Comment tu t'appelles ?")
                        .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                
                    Text("Cette information nous permettra de personnaliser ton expérience")
                        .font(.system(size: 18))
                        .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 30)
            
            // Champ de saisie
                ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.white.opacity(0.2))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.white.opacity(0.3), lineWidth: 1)
                            )
                        .frame(height: 56)
                    
                    if viewModel.userName.isEmpty {
                        Text("Entrez votre prénom")
                            .foregroundColor(.white.opacity(0.7))
                            .font(.system(size: 18))
                            .padding(.horizontal, 20)
                    }
                    
                    TextField("", text: $viewModel.userName)
                        .font(.system(size: 18))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 16)
                    .focused($isTextFieldFocused)
                    .onChange(of: viewModel.userName) { _, newName in
                        print("🔥 NameStepView: Nom saisi: '\(newName)'")
                    }
                        .accentColor(.white)
                }
                .padding(.horizontal, 30)
            }
            
            Spacer()
                
            // Bouton Continuer collé en bas
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
                    .background(Color(hex: "#FD267A"))
                        .cornerRadius(28)
                        .opacity(viewModel.userName.isEmpty ? 0.5 : 1.0)
                }
                .disabled(viewModel.userName.isEmpty)
            .padding(.horizontal, 30)
            .padding(.bottom, 50)
        }
        .onAppear {
            print("🔥 NameStepView: Vue de saisie du nom apparue")
            NSLog("🔥🔥🔥 ONBOARDING DEBUT: NAME STEP APPARUE - DEBUT DU PROCESSUS!")
        }
    }
} 
