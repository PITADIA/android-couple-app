import SwiftUI
import AuthenticationServices

struct AuthenticationView: View {
    @EnvironmentObject var appState: AppState
    @State private var showingOnboarding = false
    
    var body: some View {
        ZStack {
            // Fond dégradé rouge/orange comme dans l'app originale
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.8, green: 0.2, blue: 0.2),
                    Color(red: 0.9, green: 0.4, blue: 0.1)
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 40) {
                Spacer()
                
                // Logo principal de l'application
                Image("LogoMain")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 140, height: 140)
                    .padding(.bottom, 20)
                
                // Titre principal
                VStack(spacing: 15) {
                    Text("PRÊT À")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("(RE)DÉCOUVRIR")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("VOTRE ÂME SŒUR ?")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                }
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
                
                // Sous-titre
                Text("Boostez vos conversations avec des questions bien\nplus profondes que 'Comment ça va ?'")
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
                
                Spacer()
                
                // Section des évaluations
                VStack(spacing: 15) {
                    Text("+450k Downloads")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                    
                    HStack(spacing: 5) {
                        ForEach(0..<5) { _ in
                            Image(systemName: "star.fill")
                                .foregroundColor(.yellow)
                                .font(.system(size: 20))
                        }
                    }
                    
                    HStack {
                        Image(systemName: "applelogo")
                            .foregroundColor(.white)
                            .font(.system(size: 16))
                        Text("4.6")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
                .padding(.bottom, 40)
                
                // Boutons d'action
                VStack(spacing: 15) {
                    // Bouton principal
                    Button(action: {
                        startOnboarding()
                    }) {
                        Text("Commencer Gratuitement")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(
                                LinearGradient(
                                    gradient: Gradient(colors: [
                                        Color.orange,
                                        Color.red
                                    ]),
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(28)
                    }
                    .padding(.horizontal, 30)
                    
                    // Bouton secondaire
                    Button(action: {
                        // Action pour "J'ai déjà un compte"
                        startOnboarding()
                    }) {
                        Text("J'ai déjà un compte")
                            .font(.system(size: 16))
                            .foregroundColor(.white)
                            .underline()
                    }
                }
                
                Spacer()
                    .frame(height: 50)
            }
        }
        .fullScreenCover(isPresented: $showingOnboarding) {
            OnboardingView()
                .environmentObject(appState)
        }
    }
    
    private func startOnboarding() {
        showingOnboarding = true
    }
} 