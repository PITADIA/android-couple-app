import SwiftUI

struct NameStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @FocusState private var isTextFieldFocused: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("Comment tu t'appelles ?")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Premier Spacer pour centrer le contenu
            Spacer()
            
            // Contenu principal centré
            VStack(spacing: 30) {
                // Sous-titre
                Text("Cette information nous permettra de personnaliser ton expérience.")
                    .font(.system(size: 18))
                    .foregroundColor(.black.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 30)
                
                // Champ de saisie sur carte blanche
                VStack(spacing: 0) {
                    ZStack(alignment: .leading) {
                        if viewModel.userName.isEmpty {
                            Text("Entrez votre prénom")
                                .foregroundColor(.black.opacity(0.5))
                                .font(.system(size: 18))
                                .padding(.horizontal, 20)
                        }
                        
                        TextField("", text: $viewModel.userName)
                            .font(.system(size: 18))
                            .foregroundColor(.black)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 16)
                            .focused($isTextFieldFocused)
                            .accentColor(Color(hex: "#FD267A"))
                    }
                }
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white)
                        .shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 4)
                )
                .padding(.horizontal, 30)
            }
            
            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer()
                
            // Zone blanche collée en bas
            VStack(spacing: 0) {
                Button(action: {
                    if !viewModel.userName.isEmpty {
                        viewModel.nextStep()
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
            }
            .padding(.vertical, 30)
            .background(Color.white)
        }
        .onAppear {
            print("🔥 NameStepView: Vue de saisie du nom apparue")
            // Activer automatiquement le focus pour ouvrir le clavier immédiatement
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                isTextFieldFocused = true
            }
        }
    }
} 
