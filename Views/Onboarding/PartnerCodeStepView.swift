import SwiftUI

struct PartnerCodeStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @StateObject private var partnerCodeService = PartnerCodeService.shared
    @State private var enteredCode = ""
    @State private var showingShareSheet = false
    
    var body: some View {
        ZStack {
            // Fond gris clair identique aux autres pages d'onboarding
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                Spacer()
                
                VStack(spacing: 40) {
                    // Titre principal
                    Text("connect_with_partner".localized)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 30)
                    
                    // Sous-titre
                    Text("connect_partner_description".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)
                    
                    // Section génération de code
                    if let generatedCode = partnerCodeService.generatedCode {
                        generatedCodeSection(code: generatedCode)
                    } else if partnerCodeService.isLoading {
                        // Afficher un état de chargement
                        loadingCodeSection
                    } else if let errorMessage = partnerCodeService.errorMessage {
                        // Afficher l'erreur avec possibilité de réessayer
                        errorCodeSection
                    } else {
                        // État initial - ne devrait jamais se produire car on génère automatiquement
                        loadingCodeSection
                    }
                    
                    // Section saisie de code
                    enterCodeSection
                }
                
                Spacer()
                
                // Zone bouton en bas avec fond blanc
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
        }
        .onAppear {
            print("🔗 PartnerCodeStepView: onAppear - Vérification connexion existante")
            Task {
                await partnerCodeService.checkExistingConnection()
                if partnerCodeService.isConnected {
                    print("🔗 PartnerCodeStepView: Déjà connecté, passage à l'étape suivante")
                    viewModel.nextStep()
                } else {
                    print("🔗 PartnerCodeStepView: Pas de connexion existante")
                    print("🔗 PartnerCodeStepView: Génération automatique du code...")
                    await partnerCodeService.generatePartnerCode()
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .subscriptionInherited)) { _ in
            print("🔗 PartnerCodeStepView: Abonnement hérité détecté")
            // Vérifier que le partenaire a vraiment un abonnement avant de skip
            if partnerCodeService.partnerInfo?.isSubscribed == true {
                print("🔗 PartnerCodeStepView: Confirmation - partenaire premium, skip subscription")
                print("🔥🔥🔥 INHERITANCE: PARTENAIRE PREMIUM DETECTE - SKIP VERS FINALISATION DIRECTE")
                NSLog("🔗 PartnerCodeStepView: PARTENAIRE PREMIUM - FINALISATION DIRECTE")
                viewModel.skipSubscriptionDueToInheritance()
                // NOUVEAU: Finaliser directement l'onboarding avec abonnement hérité
                viewModel.finalizeOnboarding(withSubscription: true)
            } else {
                print("🔗 PartnerCodeStepView: Partenaire non premium, continuer vers subscription")
                viewModel.nextStep()
            }
        }
        .onChange(of: partnerCodeService.isConnected) { _, isConnected in
            if isConnected {
                print("🔗 PartnerCodeStepView: Connexion partenaire réussie")
                // Vérifier si un abonnement a été hérité
                if partnerCodeService.partnerInfo?.isSubscribed == true {
                    print("🔗 PartnerCodeStepView: Abonnement hérité du partenaire - skip subscription")
                    print("🔥🔥🔥 INHERITANCE: CONNEXION ETABLIE AVEC PARTENAIRE PREMIUM - FINALISATION DIRECTE")
                    NSLog("🔗 PartnerCodeStepView: CONNEXION PREMIUM - FINALISATION DIRECTE")
                    viewModel.skipSubscriptionDueToInheritance()
                    // NOUVEAU: Finaliser directement l'onboarding avec abonnement hérité
                    viewModel.finalizeOnboarding(withSubscription: true)
                } else {
                    print("🔗 PartnerCodeStepView: Partenaire sans abonnement - affichage page de paiement")
                    // Ne pas skip l'abonnement, l'utilisateur verra la page de paiement
                    viewModel.nextStep()
                }
            }
        }
        .onChange(of: partnerCodeService.generatedCode) { _, newCode in
            print("🔗 PartnerCodeStepView: Code généré changé: \(newCode ?? "nil")")
        }
        .sheet(isPresented: $showingShareSheet) {
            if let code = partnerCodeService.generatedCode {
                ShareSheet(activityItems: [createShareMessage(code: code)])
            }
        }
    }
    
    private var loadingCodeSection: some View {
        VStack(spacing: 20) {
            Text("code_generation".localized)
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.6))
            
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .black))
                .scaleEffect(0.8)
        }
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
            .foregroundColor(Color(hex: "#FD267A"))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 28)
                    .stroke(Color(hex: "#FD267A"), lineWidth: 2)
            )
            .cornerRadius(28)
        }
        .padding(.horizontal, 30)
    }
    
    private func generatedCodeSection(code: String) -> some View {
        VStack(spacing: 20) {
            Text("send_code_to_partner".localized)
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.7))
                .multilineTextAlignment(.center)
            
            // Code avec style adapté au fond gris
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
                    .fill(Color.white)
                    .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
            )
            
            // Bouton "Envoyer le code"
            Button("send_code".localized) {
                shareCode(code)
            }
            .font(.system(size: 16, weight: .medium))
            .foregroundColor(Color(hex: "#FD267A"))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 28)
                    .stroke(Color(hex: "#FD267A"), lineWidth: 2)
            )
            .cornerRadius(28)
        }
        .padding(.horizontal, 30)
    }
    
    private var enterCodeSection: some View {
        VStack(spacing: 20) {
            Text("enter_partner_code".localized)
                .font(.system(size: 16))
                .foregroundColor(.black)
            
            // Champ de saisie adapté au fond gris
            TextField("enter_code_placeholder".localized, text: $enteredCode)
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(.black)
                .multilineTextAlignment(.center)
                .padding(.vertical, 16)
                .padding(.horizontal, 20)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white)
                        .shadow(color: .black.opacity(0.1), radius: 2, x: 0, y: 1)
                )
                .keyboardType(.numberPad)
                .onChange(of: enteredCode) { _, newValue in
                    // Limiter à 8 chiffres
                    if newValue.count > 8 {
                        enteredCode = String(newValue.prefix(8))
                    }
                    // Garder seulement les chiffres
                    enteredCode = newValue.filter { $0.isNumber }
                }
            
            // Bouton "Connecter"
            Button(action: {
                Task {
                    let success = await partnerCodeService.connectWithPartnerCode(enteredCode)
                    if success {
                        // La navigation sera gérée par onChange de isConnected
                    }
                }
            }) {
                HStack {
                    if partnerCodeService.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: "#FD267A")))
                            .scaleEffect(0.8)
                        Text("connecting_status".localized)
                    } else {
                        Text("connect".localized)
                    }
                }
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Color(hex: "#FD267A"))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 28)
                        .stroke(Color(hex: "#FD267A"), lineWidth: 2)
                        .opacity(enteredCode.count == 8 && !partnerCodeService.isLoading ? 1.0 : 0.5)
                )
                .cornerRadius(28)
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
    
    private func shareCode(_ code: String) {
        print("🔗 PartnerCodeStepView: Tentative de partage du code: \(code)")
        showingShareSheet = true
    }
    
    private func createShareMessage(code: String) -> String {
        "share_partner_code_message".localized.replacingOccurrences(of: "{code}", with: code)
    }
}

// MARK: - ShareSheet Component
struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]
    let applicationActivities: [UIActivity]? = nil
    
    func makeUIViewController(context: UIViewControllerRepresentableContext<ShareSheet>) -> UIActivityViewController {
        let controller = UIActivityViewController(
            activityItems: activityItems,
            applicationActivities: applicationActivities
        )
        
        // Masquer les activités non désirées (tout ce qui est en dessous d'AirDrop)
        controller.excludedActivityTypes = [
            .addToReadingList,
            .assignToContact,
            .copyToPasteboard,
            .openInIBooks,
            .postToFacebook,
            .postToFlickr,
            .postToTencentWeibo,
            .postToTwitter,
            .postToVimeo,
            .postToWeibo,
            .print,
            .saveToCameraRoll,
            .markupAsPDF,
            .sharePlay
        ]
        
        // Configuration pour iPad
        if let popover = controller.popoverPresentationController {
            popover.sourceView = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first { $0.isKeyWindow }
            popover.sourceRect = CGRect(x: UIScreen.main.bounds.width / 2, y: UIScreen.main.bounds.height / 2, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        
        return controller
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: UIViewControllerRepresentableContext<ShareSheet>) {}
}

#Preview {
    PartnerCodeStepView(viewModel: OnboardingViewModel())
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
} 