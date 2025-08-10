import SwiftUI

struct DailyQuestionPartnerCodeView: View {
    let onDismiss: () -> Void
    
    @EnvironmentObject var appState: AppState
    @StateObject private var partnerCodeService = PartnerCodeService.shared
    @State private var enteredCode = ""
    @State private var showingShareSheet = false
    @FocusState private var isCodeFieldFocused: Bool
    @State private var keyboardHeight: CGFloat = 0
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond dégradé rose clair identique à PartnerManagementView
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FFE5F1"),
                        Color(hex: "#FFF0F8")
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                // ScrollView pour permettre le défilement quand le clavier est ouvert
                ScrollView {
                    VStack {
                        // Padding du haut adaptatif
                        Spacer()
                            .frame(height: max(50, (UIScreen.main.bounds.height - keyboardHeight) * 0.15))
                        
                        VStack(spacing: 40) {
                            // Titre principal
                            VStack(spacing: 20) {
                                Text("connect_with_partner".localized)
                                    .font(.system(size: 28, weight: .bold))
                                    .foregroundColor(.black)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(nil)
                                    .fixedSize(horizontal: false, vertical: true)
                                
                                // Sous-titre
                                Text("connect_partner_description".localized)
                                    .font(.system(size: 16))
                                    .foregroundColor(.black.opacity(0.7))
                                    .multilineTextAlignment(.center)
                                    .lineLimit(nil)
                            }
                            .padding(.horizontal, 30)
                            
                            // Section génération de code
                            if let generatedCode = partnerCodeService.generatedCode {
                                generatedCodeSection(code: generatedCode)
                            } else if partnerCodeService.isLoading {
                                loadingCodeSection
                            } else if partnerCodeService.errorMessage != nil {
                                errorCodeSection
                            } else {
                                loadingCodeSection
                            }
                            
                            // Section saisie de code
                            enterCodeSection
                        }
                        .frame(maxWidth: .infinity) // Centrage horizontal
                        
                        // Padding du bas adaptatif pour le clavier
                        Spacer()
                            .frame(height: max(50, keyboardHeight > 0 ? 20 : (UIScreen.main.bounds.height * 0.15)))
                    }
                }
                .scrollDismissesKeyboard(.interactively)
                .onTapGesture {
                    // Cacher le clavier quand on clique sur le fond du ScrollView
                    isCodeFieldFocused = false
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            print("🔗 DailyQuestionPartnerCodeView: onAppear")
            Task {
                await partnerCodeService.checkExistingConnection()
                if partnerCodeService.isConnected {
                    print("🔗 DailyQuestionPartnerCodeView: Déjà connecté")
                    onDismiss()
                } else {
                    print("🔗 DailyQuestionPartnerCodeView: Génération du code...")
                    _ = await partnerCodeService.generatePartnerCode()
                }
            }
            // Observer les changements de clavier
            setupKeyboardObservers()
        }
        .onDisappear {
            removeKeyboardObservers()
        }
        .onChange(of: partnerCodeService.isConnected) { _, isConnected in
            if isConnected {
                print("🔗 DailyQuestionPartnerCodeView: Connexion partenaire réussie")
                // Le PartnerCodeService s'occupe déjà d'envoyer les notifications
                // Le message de succès sera affiché automatiquement via partnerConnectionService
                onDismiss()
            }
        }
        .sheet(isPresented: $showingShareSheet) {
            if let code = partnerCodeService.generatedCode {
                ShareSheet(items: [createShareMessage(code: code)])
            }
        }
    }
    
    // MARK: - Sections (identiques au PartnerManagementView)
    
    private var loadingCodeSection: some View {
        VStack(spacing: 20) {
            if partnerCodeService.isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .black))
                    .scaleEffect(0.8)
                Text("generating".localized)
                    .font(.system(size: 16))
                    .foregroundColor(.black)
            } else {
                Text("send_partner_code".localized)
                    .font(.system(size: 16))
                    .foregroundColor(.black)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.95))
                .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
        )
        .disabled(partnerCodeService.isLoading)
        .padding(.horizontal, 30)
    }
    
    private var errorCodeSection: some View {
        VStack(spacing: 20) {
            Text("generation_error".localized)
                .font(.system(size: 16))
                .foregroundColor(.red)
            
            if let errorMessage = partnerCodeService.errorMessage {
                Text(errorMessage)
                    .font(.system(size: 14))
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
            }
            
            Button("retry".localized) {
                Task {
                    await partnerCodeService.generatePartnerCode()
                }
            }
            .font(.system(size: 16, weight: .medium))
            .foregroundColor(.black)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.95))
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
            )
        }
        .padding(.horizontal, 30)
    }
    
    private func generatedCodeSection(code: String) -> some View {
        VStack(spacing: 20) {
            Text("send_code_to_partner".localized)
                .font(.system(size: 16))
                .foregroundColor(.black)
                .multilineTextAlignment(.center)
            
            // Code avec effet de carte sophistiqué et élévation
            VStack(spacing: 10) {
                Text(code)
                    .font(.system(size: 48, weight: .bold, design: .monospaced))
                    .foregroundColor(Color(hex: "#FD267A"))
                    .tracking(8)
            }
            .padding(.vertical, 25)
            .padding(.horizontal, 30)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.95))
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
            )
            
            // Bouton "Envoyer son code" avec effet de carte
            Button("send_partner_code".localized) {
                shareCode(code)
            }
            .font(.system(size: 16, weight: .medium))
            .foregroundColor(.black)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.95))
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
            )
            
            // Trait sous le bouton
            Rectangle()
                .fill(Color.black.opacity(0.3))
                .frame(height: 1)
                .padding(.horizontal, 20)
                .padding(.top, 20)
        }
        .padding(.horizontal, 30)
    }
    
    private var enterCodeSection: some View {
        VStack(spacing: 20) {
            Text("enter_partner_code".localized)
                .font(.system(size: 16))
                .foregroundColor(.black)
            
            // Champ de saisie avec effet de carte sophistiqué
            TextField("enter_code".localized, text: $enteredCode)
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(.black)
                .multilineTextAlignment(.center)
                .padding(.vertical, 16)
                .padding(.horizontal, 20)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white.opacity(0.95))
                        .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
                )
                .keyboardType(.numberPad)
                .focused($isCodeFieldFocused)
                .onChange(of: enteredCode) { _, newValue in
                    // Limiter à 8 chiffres
                    if newValue.count > 8 {
                        enteredCode = String(newValue.prefix(8))
                    }
                    // Garder seulement les chiffres
                    enteredCode = newValue.filter { $0.isNumber }
                }
            
            // Bouton "Connecter" avec effet de carte et élévation
            Button(action: {
                Task {
                    await connectWithCode()
                }
            }) {
                HStack {
                    if partnerCodeService.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                        Text("connecting_status".localized)
                    } else {
                        Text("connect".localized)
                    }
                }
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    RoundedRectangle(cornerRadius: 28)
                        .fill(
                            LinearGradient(
                                gradient: Gradient(colors: [
                                    Color(hex: "#FD267A"),
                                    Color(hex: "#FF655B")
                                ]),
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .shadow(color: Color(hex: "#FD267A").opacity(0.4), radius: 10, x: 0, y: 5)
                        .opacity(enteredCode.count == 8 && !partnerCodeService.isLoading ? 1.0 : 0.5)
                )
            }
            .disabled(enteredCode.count != 8 || partnerCodeService.isLoading)
            
            // Message d'erreur
            if let errorMessage = partnerCodeService.errorMessage {
                Text(errorMessage)
                    .font(.system(size: 14))
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
                    .padding(.top, 10)
            }
        }
        .padding(.horizontal, 30)
    }
    
    // MARK: - Actions
    
    private func connectWithCode() async {
        guard !enteredCode.isEmpty else { return }
        
        _ = await partnerCodeService.connectWithPartnerCode(enteredCode, context: .dailyQuestion)
        
        // La connexion sera gérée par onChange(of: partnerCodeService.isConnected)
    }
    
    private func shareCode(_ code: String) {
        print("🔗 DailyQuestionPartnerCodeView: Tentative de partage du code: \(code)")
        showingShareSheet = true
    }
    
    private func createShareMessage(code: String) -> String {
        return "share_partner_code_message".localized.replacingOccurrences(of: "{code}", with: code)
    }
    
    // MARK: - Keyboard Observers
    
    private func setupKeyboardObservers() {
        NotificationCenter.default.addObserver(forName: UIResponder.keyboardWillShowNotification, object: nil, queue: .main) { notification in
            if let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect {
                keyboardHeight = keyboardFrame.height
            }
        }
        
        NotificationCenter.default.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { _ in
            keyboardHeight = 0
        }
    }
    
    private func removeKeyboardObservers() {
        NotificationCenter.default.removeObserver(self, name: UIResponder.keyboardWillShowNotification, object: nil)
        NotificationCenter.default.removeObserver(self, name: UIResponder.keyboardWillHideNotification, object: nil)
    }
}

#Preview {
    DailyQuestionPartnerCodeView(
        onDismiss: {
            print("Dismissed")
        }
    )
    .environmentObject(AppState())
} 