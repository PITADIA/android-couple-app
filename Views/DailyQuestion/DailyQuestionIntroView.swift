import SwiftUI

struct DailyQuestionIntroView: View {
    @EnvironmentObject var appState: AppState
    @State private var showingPartnerCodeSheet = false
    @State private var navigateToChat = false
    
    // Vérifier si l'utilisateur a un partenaire connecté
    private var hasConnectedPartner: Bool {
        guard let partnerId = appState.currentUser?.partnerId else { return false }
        return !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    // Texte du bouton selon le statut
    private var buttonText: String {
        if hasConnectedPartner {
            return "Continuer"
        } else {
            return NSLocalizedString("connect_partner_button", tableName: "DailyQuestions", comment: "")
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
                            Text(NSLocalizedString("daily_question_title", tableName: "DailyQuestions", comment: ""))
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
                        Image("mima")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 240, height: 240)
                        
                        VStack(spacing: 12) {
                            Text(NSLocalizedString("daily_question_intro_title", tableName: "DailyQuestions", comment: ""))
                                .font(.system(size: 22, weight: .medium))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                            
                            Text(NSLocalizedString("daily_question_intro_subtitle", tableName: "DailyQuestions", comment: ""))
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
                            if hasConnectedPartner {
                                // Naviguer vers le chat
                                navigateToChat = true
                            } else {
                                // Montrer le sheet de connexion partenaire
                        showingPartnerCodeSheet = true
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
            DailyQuestionPartnerCodeView(
                onDismiss: {
                    showingPartnerCodeSheet = false
                }
            )
            .environmentObject(appState)
        }
        .fullScreenCover(isPresented: $navigateToChat) {
            DailyQuestionMainView()
                .environmentObject(appState)
        }
        .overlay(
            Group {
                if let partnerService = appState.partnerConnectionService,
                   partnerService.shouldShowConnectionSuccess {
                    PartnerConnectionSuccessView(
                        partnerName: partnerService.connectedPartnerName
                    ) {
                        // Fermer le succès et naviguer vers le chat
                        partnerService.dismissConnectionSuccess()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                            navigateToChat = true
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
    DailyQuestionIntroView()
        .environmentObject(AppState())
} 