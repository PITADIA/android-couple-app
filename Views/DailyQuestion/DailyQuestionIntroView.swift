import SwiftUI

struct DailyQuestionIntroView: View {
    @EnvironmentObject var appState: AppState
    @State private var showingPartnerCodeSheet = false
    
    var body: some View {
        ZStack {
            // Fond dégradé
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(hex: "#FD267A"),
                    Color(hex: "#FF655B")
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 40) {
                Spacer()
                
                VStack(spacing: 30) {
                    // Titre principal avec emoji
                    VStack(spacing: 16) {
                        Text(NSLocalizedString("daily_question_intro_title", tableName: "DailyQuestions", comment: ""))
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 30)
                        
                        Text(NSLocalizedString("daily_question_intro_subtitle", tableName: "DailyQuestions", comment: ""))
                            .font(.system(size: 18))
                            .foregroundColor(.white.opacity(0.9))
                            .multilineTextAlignment(.center)
                            .lineSpacing(6)
                            .padding(.horizontal, 30)
                    }
                    
                    // Carte d'illustration
                    VStack(spacing: 20) {
                        VStack(spacing: 16) {
                            // Icône illustrative
                            HStack(spacing: 12) {
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(Color(hex: "#FD267A"))
                                
                                Image(systemName: "clock.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(Color(hex: "#FF655B"))
                                
                                Image(systemName: "message.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(Color(hex: "#FD267A"))
                            }
                            
                            Text(NSLocalizedString("daily_question_intro_time", tableName: "DailyQuestions", comment: ""))
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.black.opacity(0.8))
                        }
                    }
                    .padding(.vertical, 24)
                    .padding(.horizontal, 20)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(.white)
                            .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
                    )
                    .padding(.horizontal, 30)
                }
                
                Spacer()
                
                VStack(spacing: 20) {
                    // Message de disponibilité
                    Text(NSLocalizedString("daily_question_couples_only", tableName: "DailyQuestions", comment: ""))
                        .font(.system(size: 16))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                    
                    // Bouton principal
                    Button {
                        showingPartnerCodeSheet = true
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "person.2.fill")
                                .font(.system(size: 18))
                            
                            Text(NSLocalizedString("connect_partner_button", tableName: "DailyQuestions", comment: ""))
                                .font(.system(size: 18, weight: .semibold))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            RoundedRectangle(cornerRadius: 28)
                                .fill(.white.opacity(0.2))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 28)
                                        .stroke(.white.opacity(0.3), lineWidth: 1)
                                )
                        )
                    }
                    .padding(.horizontal, 30)
                }
                .padding(.bottom, 50)
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showingPartnerCodeSheet) {
            PartnerCodeStepView(
                viewModel: OnboardingViewModel()
            )
            .environmentObject(appState)
        }
    }
}

#Preview {
    DailyQuestionIntroView()
        .environmentObject(AppState())
} 