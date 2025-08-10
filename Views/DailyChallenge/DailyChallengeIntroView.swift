import SwiftUI

struct DailyChallengeIntroView: View {
    let showConnectButton: Bool
    
    @EnvironmentObject var appState: AppState
    @ObservedObject private var partnerService = PartnerConnectionNotificationService.shared  // ✅ Référence directe
    @State private var showingPartnerCodeSheet = false
    @State private var navigateToChallenge = false
    
    // MARK: - Initializer
    
    init(showConnectButton: Bool = true) {
        self.showConnectButton = showConnectButton
    }
    
    // MARK: - Computed Properties
    
    // Vérifier si l'utilisateur a un partenaire connecté
    private var hasConnectedPartner: Bool {
        guard let partnerId = appState.currentUser?.partnerId else { return false }
        return !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    // Texte du bouton selon le statut et le paramètre
    private var buttonText: String {
        if showConnectButton && !hasConnectedPartner {
            return NSLocalizedString("connect_partner_button", tableName: "DailyChallenges", comment: "")
        } else {
            return NSLocalizedString("continue_button", tableName: "DailyChallenges", comment: "")
        }
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à la page journal
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header avec titre
                    HStack {
                        Spacer()
                        
                        // Titre
                        VStack(spacing: 4) {
                            Text(NSLocalizedString("daily_challenges_title", tableName: "DailyChallenges", comment: ""))
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)
                        }
                        
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 100) // Espace augmenté entre titre et image
                    
                    // Contenu principal centré avec mêmes spacings que journal
                    VStack(spacing: 20) {
                        Image("gaougaou")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 240, height: 240)
                        
                        VStack(spacing: 12) {
                            Text(NSLocalizedString("daily_challenge_intro_title", tableName: "DailyChallenges", comment: ""))
                                .font(.system(size: 22, weight: .medium))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                            
                            Text(NSLocalizedString("daily_challenge_intro_subtitle", tableName: "DailyChallenges", comment: ""))
                                .font(.system(size: 16))
                                .foregroundColor(.black.opacity(0.7))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 30)
                        }
                    }
                    
                    Spacer()
                    
                    // Bouton principal conditionnel
                    VStack {
                        Button {
                            if showConnectButton && !hasConnectedPartner {
                                // Montrer le sheet de connexion partenaire
                                showingPartnerCodeSheet = true
                            } else {
                                // Marquer l'intro comme vue et naviguer
                                appState.markDailyChallengeIntroAsSeen()
                                navigateToChallenge = true
                            }
                        } label: {
                            Text(buttonText)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 24)
                                .frame(height: 56)
                                                        .background(
                            RoundedRectangle(cornerRadius: 28)
                                .fill(Color(hex: "#FD267A"))
                        )
                        }
                        .padding(.bottom, 160) // Espace pour le menu du bas
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showingPartnerCodeSheet) {
            // Réutiliser la vue de connexion partenaire des questions
            DailyQuestionPartnerCodeView(
                onDismiss: {
                    showingPartnerCodeSheet = false
                }
            )
            .environmentObject(appState)
        }
        .fullScreenCover(isPresented: $navigateToChallenge) {
            DailyChallengeMainView()
                .environmentObject(appState)
        }
        .overlay(
            Group {
                if partnerService.shouldShowConnectionSuccess {  // ✅ Référence directe simplifiée
                    PartnerConnectionSuccessView(
                        partnerName: partnerService.connectedPartnerName,
                        mode: .simpleDismiss,
                        context: .dailyChallenge
                    ) {
                        // ✅ Fermeture fluide avec animation
                        withAnimation(.easeOut(duration: 0.3)) {
                            partnerService.dismissConnectionSuccess()
                        }
                    }
                    .transition(.opacity)
                    .zIndex(1000)
                }
            }
        )
    }
}

#Preview {
    DailyChallengeIntroView()
        .environmentObject(AppState())
}