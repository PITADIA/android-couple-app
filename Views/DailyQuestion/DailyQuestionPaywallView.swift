import SwiftUI
import FirebaseAnalytics

struct DailyQuestionPaywallView: View {
    @EnvironmentObject var appState: AppState
    let questionDay: Int
    @State private var showSubscriptionSheet = false
    

    
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
                            Text("paywall_page_title_questions".localized(tableName: "DailyQuestions"))
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)
                        }
                        
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 40) // ✅ PLUS D'ESPACE EN HAUT
                    .padding(.bottom, 100) // Espace augmenté entre titre et image
                    
                    // Contenu principal avec carte floutée
                    VStack(spacing: 30) {
                        // Titre principal
                        Text("paywall_questions_title".localized(tableName: "DailyQuestions"))
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                        
                        // Carte de question floutée
                        ZStack {
                            // Carte simulée avec texte simulé
                            VStack(spacing: 0) {
                                // Header de la carte
                                VStack(spacing: 8) {
                                    Text("Love2Love")
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundColor(.white)
                                        .multilineTextAlignment(.center)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 20)
                                .background(
                                    LinearGradient(
                                        gradient: Gradient(colors: [
                                            Color(red: 1.0, green: 0.4, blue: 0.6),
                                            Color(red: 1.0, green: 0.6, blue: 0.8)
                                        ]),
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                
                                // Corps de la carte avec texte simulé
                                VStack(spacing: 30) {
                                    Spacer()
                                    
                                    Text("Quelle est la chose que tu apprécies le plus chez ton partenaire et que tu aimerais lui dire plus souvent ? Comment penses-tu que cela pourrait renforcer votre relation ?")
                                        .font(.system(size: 22, weight: .medium))
                                        .foregroundColor(.white)
                                        .multilineTextAlignment(.center)
                                        .lineSpacing(6)
                                        .padding(.horizontal, 30)
                                        .blur(radius: 8) // FLOU APPLIQUÉ AU TEXTE
                                    
                                    Spacer(minLength: 20)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(minHeight: 200)
                                .background(
                                    LinearGradient(
                                        gradient: Gradient(colors: [
                                            Color(red: 0.2, green: 0.1, blue: 0.15),
                                            Color(red: 0.4, green: 0.2, blue: 0.3),
                                            Color(red: 0.6, green: 0.3, blue: 0.2)
                                        ]),
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                )
                            }
                            .cornerRadius(20)
                            .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 4)
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 20)
                            
                            // Overlay flou et cadenas - SOLUTION AMÉLIORÉE
                            VStack(spacing: 0) {
                                Color.clear
                                    .frame(height: 60) // Espace pour le header non flouté
                                
                                ZStack {
                                    // Fond coloré qui imite la carte + flou
                                    LinearGradient(
                                        gradient: Gradient(colors: [
                                            Color(red: 0.2, green: 0.1, blue: 0.15).opacity(0.95),
                                            Color(red: 0.4, green: 0.2, blue: 0.3).opacity(0.95),
                                            Color(red: 0.6, green: 0.3, blue: 0.2).opacity(0.95)
                                        ]),
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                    .blur(radius: 15) // VRAI EFFET DE FLOU
                                    
                                    // Overlay Material pour l'effet glassmorphism
                                    Rectangle()
                                        .fill(.ultraThickMaterial)
                                        .opacity(0.8)
                                    
                                    // Icône coeur
                                    Text("💕")
                                        .font(.system(size: 60))
                                        .shadow(color: .black.opacity(0.3), radius: 2, x: 0, y: 1)
                                }
                            }
                            .cornerRadius(20)
                            .padding(.horizontal, 20)
                        }
                        
                        // Sous-titre
                        Text("paywall_questions_subtitle".localized(tableName: "DailyQuestions"))
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.7))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 30)
                            .lineLimit(nil) // ✅ PERMETTRE AFFICHAGE COMPLET
                            .fixedSize(horizontal: false, vertical: true) // ✅ TAILLE FLEXIBLE
                    }
                    
                    Spacer(minLength: 40) // ✅ PLUS D'ESPACE AVANT LE BOUTON
                    
                    // Bouton principal conditionnel
                    VStack {
                        Button {
                            showSubscriptionSheet = true
                            
                            // Analytics
                            Analytics.logEvent("cta_premium_clicked", parameters: [
                                "source": "daily_question_paywall",
                                "question_day": questionDay
                            ])
                        } label: {
                            Text("paywall_continue_button".localized(tableName: "DailyQuestions"))
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
        .sheet(isPresented: $showSubscriptionSheet) {
            SubscriptionView()
                .environmentObject(appState)
        }
        .onAppear {
            // Analytics: Paywall vu
            Analytics.logEvent("paywall_viewed", parameters: [
                "source": "daily_question_freemium",
                "question_day": questionDay
            ])
            print("📊 Événement Firebase: paywall_viewed - source: daily_question_freemium, day: \(questionDay)")
        }
    }
}

#Preview {
    DailyQuestionPaywallView(questionDay: 4)
        .environmentObject(AppState())
}