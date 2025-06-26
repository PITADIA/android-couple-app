import SwiftUI

struct PartnerManagementView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var partnerCodeService = PartnerCodeService.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var enteredCode = ""
    @State private var showingDisconnectAlert = false
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond dégradé rose clair identique à la vue de permission de localisation
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FFE5F1"),
                        Color(hex: "#FFF0F8")
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                // Contenu centré verticalement et horizontalement
                VStack {
                    Spacer() // Pousse le contenu vers le centre verticalement
                    
                    VStack(spacing: 40) {
                        // Titre principal - conditionnel selon l'état de connexion
                        if partnerCodeService.isConnected {
                            Text("Vous êtes connecté 💞")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                        } else {
                            VStack(spacing: 20) {
                                Text("Connecte-toi avec ton partenaire")
                                    .font(.system(size: 28, weight: .bold))
                                    .foregroundColor(.black)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(nil)
                                    .fixedSize(horizontal: false, vertical: true)
                                
                                // Sous-titre seulement si pas connecté
                                Text("Connecte-toi avec ton partenaire pour partager ton journal avec lui, et profiter de tous les avantages de l'application.")
                                    .font(.system(size: 16))
                                    .foregroundColor(.black.opacity(0.7))
                                    .multilineTextAlignment(.center)
                                    .lineLimit(nil)
                            }
                            .padding(.horizontal, 30)
                        }
                        
                        if partnerCodeService.isConnected {
                            // État connecté
                            connectedSection
                        } else {
                            // État non connecté
                            disconnectedSection
                        }
                    }
                    .frame(maxWidth: .infinity) // Centrage horizontal
                    
                    Spacer() // Pousse le contenu vers le centre verticalement
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            Task {
                await partnerCodeService.checkExistingConnection()
            }
        }
        .alert("Déconnecter le partenaire", isPresented: $showingDisconnectAlert) {
            Button("Annuler", role: .cancel) { }
            Button("Déconnecter", role: .destructive) {
                Task {
                    await partnerCodeService.disconnectPartner()
                }
            }
        } message: {
            Text("Êtes-vous sûr de vouloir vous déconnecter de votre partenaire ? Cette action supprimera le partage de vos données.")
        }
        // NOUVEAU: Overlay pour le message de connexion partenaire
        .overlay(
            Group {
                if let partnerService = appState.partnerConnectionService,
                   partnerService.shouldShowConnectionSuccess {
                    PartnerConnectionSuccessView(
                        partnerName: partnerService.connectedPartnerName
                    ) {
                        partnerService.dismissConnectionSuccess()
                        // Fermer la vue après connexion réussie
                        dismiss()
                    }
                    .transition(.opacity)
                    .zIndex(1000)
                }
            }
        )
    }
    
    private var connectedSection: some View {
        VStack(spacing: 25) {
            // Informations du partenaire - centré
            if let partnerInfo = partnerCodeService.partnerInfo {
                VStack(spacing: 15) {
                    Text("Partenaire: \(partnerInfo.name)")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                    
                    Text("Connecté le \(formatDate(partnerInfo.connectedAt))")
                        .font(.system(size: 14))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                    
                    if partnerInfo.isSubscribed {
                        HStack {
                            Image(systemName: "crown.fill")
                                .foregroundColor(.yellow)
                            Text("Abonnement Premium partagé")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.black)
                        }
                        .padding(.top, 5)
                    }
                }
                .padding(.vertical, 20)
                .padding(.horizontal, 25)
                .frame(maxWidth: .infinity)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.white.opacity(0.95))
                        .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
                )
                .padding(.horizontal, 30)
            }
            
            // Bouton de déconnexion avec effet de carte
            Button("Déconnecter") {
                showingDisconnectAlert = true
            }
            .font(.system(size: 16, weight: .medium))
            .foregroundColor(.red)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.95))
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
            )
            .padding(.horizontal, 30)
        }
    }
    
    private var disconnectedSection: some View {
        VStack(spacing: 30) {
            // Section génération de code
            if let generatedCode = partnerCodeService.generatedCode {
                generatedCodeSection(code: generatedCode)
            } else {
                generateCodeButton
            }
            
            // Section saisie de code
            enterCodeSection
        }
    }
    
    private var generateCodeButton: some View {
        Button(action: {
            Task {
                await partnerCodeService.generatePartnerCode()
            }
        }) {
            HStack {
                if partnerCodeService.isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .black))
                        .scaleEffect(0.8)
                    Text("Génération...")
                        .font(.system(size: 16))
                        .foregroundColor(.black)
                } else {
                    Text("Envoyer un code à ton partenaire")
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
        }
        .disabled(partnerCodeService.isLoading)
        .padding(.horizontal, 30)
    }
    
    private func generatedCodeSection(code: String) -> some View {
        VStack(spacing: 20) {
            Text("Envoie ce code à ton partenaire")
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
            Button("Envoyer son code") {
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
            Text("Saisis le code de ton partenaire")
                .font(.system(size: 16))
                .foregroundColor(.black)
            
            // Champ de saisie avec effet de carte sophistiqué
            TextField("Saisis le code", text: $enteredCode)
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
                    await partnerCodeService.connectWithPartnerCode(enteredCode)
                }
            }) {
                HStack {
                    if partnerCodeService.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                        Text("Connexion...")
                    } else {
                        Text("Connecter")
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
    
    private func shareCode(_ code: String) {
        let message = "Voici mon code partenaire Love2Love: \(code)"
        
        let activityVC = UIActivityViewController(
            activityItems: [message],
            applicationActivities: nil
        )
        
        // Configuration pour iPad
        if let popover = activityVC.popoverPresentationController {
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
               let window = windowScene.windows.first {
                popover.sourceView = window
                popover.sourceRect = CGRect(x: UIScreen.main.bounds.width / 2, y: UIScreen.main.bounds.height / 2, width: 0, height: 0)
                popover.permittedArrowDirections = []
            }
        }
        
        // Trouver le contrôleur présenté le plus haut pour éviter les conflits
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let window = windowScene.windows.first {
            
            var topController = window.rootViewController
            while let presentedController = topController?.presentedViewController {
                topController = presentedController
            }
            
            topController?.present(activityVC, animated: true)
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        formatter.locale = Locale(identifier: "fr_FR")
        return formatter.string(from: date)
    }
}

#Preview {
    PartnerManagementView()
        .environmentObject(AppState())
} 
