import SwiftUI
import AuthenticationServices

struct AuthenticationView: View {
    @EnvironmentObject var appState: AppState
    @State private var showingOnboarding = false
    
    var body: some View {
        ZStack {
            // Fond dégradé personnalisé avec les nouvelles couleurs
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
                // Nom de l'app en haut
                Text("Love2Love")
                    .font(.system(size: 50, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.top, 60)
                    .padding(.bottom, 40)
                
                Spacer()
                
                // Titre principal
                VStack(spacing: 20) {
                    Text("Et si vous retombiez amoureux en vous parlant vraiment ?")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 20)
                
                // Sous-titre
                    Text("Améliorez votre relation à travers des questions profondes, conçues pour vous rapprocher toujours plus.")
                        .font(.system(size: 18))
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 30)
                }
                
                Spacer()
                
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
                            .background(Color(hex: "#FD267A"))
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