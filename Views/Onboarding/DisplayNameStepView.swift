import SwiftUI

struct DisplayNameStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @FocusState private var isTextFieldFocused: Bool
    @StateObject private var authService = AuthenticationService.shared
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("display_name_step_title".localized)
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
                // Champ de saisie sur carte blanche
                VStack(spacing: 0) {
                    ZStack(alignment: .leading) {
                        if viewModel.userName.isEmpty {
                            Text("display_name_placeholder".localized)
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
                            .onTapGesture {
                                // Focus sur le champ de texte uniquement quand l'utilisateur tape dessus
                                isTextFieldFocused = true
                            }
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
            VStack(spacing: 15) {
                Button(action: {
                    print("🔥 DisplayNameStepView: Bouton 'Continue' cliqué")
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
                
                // Bouton "Passer cette étape" identique à ProfilePhotoStepView
                Button(action: {
                    print("🔥 DisplayNameStepView: Bouton 'Passer cette étape' cliqué")
                    print("🔥 DisplayNameStepView: Nom actuel avant skip: '\(viewModel.userName)'")
                    viewModel.userName = "" // Laisser vide pour auto-génération
                    print("🔥 DisplayNameStepView: Nom vidé pour auto-génération")
                    print("🔥 DisplayNameStepView: Appel de nextStep()")
                    viewModel.nextStep()
                }) {
                    Text("skip_step".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.6))
                        .underline()
                }
            }
            .padding(.vertical, 30)
            .background(Color.white)
        }
        .onTapGesture {
            // Fermer le clavier de manière optimisée
            self.hideKeyboard()
        }
        .onAppear {
            print("🔥 DisplayNameStepView: Vue de saisie du nom d'affichage apparue")
            
            // Pré-remplir avec le nom Apple si disponible et si le champ est vide
            if viewModel.userName.isEmpty, let appleDisplayName = authService.appleUserDisplayName {
                print("🔥 DisplayNameStepView: Pré-remplissage avec nom Apple: \(appleDisplayName)")
                viewModel.userName = appleDisplayName
            }
            
            // Ne plus auto-focus le clavier - l'utilisateur doit taper pour l'ouvrir
            isTextFieldFocused = false
            }
        }
    }

