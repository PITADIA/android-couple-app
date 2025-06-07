import SwiftUI
import AuthenticationServices
import CryptoKit

struct AuthenticationView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var firebaseService = FirebaseService.shared
    @StateObject private var authService = AuthenticationService.shared
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
            
            VStack(spacing: 0) {
                // Logo et nom de l'app en haut
                HStack(spacing: 15) {
                    // Logo Leetchi à gauche
                    Image("Leetchi")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 40, height: 40)
                    
                    Text("Love2Love")
                        .font(.system(size: 50, weight: .bold))
                        .foregroundColor(.white)
                }
                .padding(.top, 60)
                .padding(.bottom, 40)
                
                // Spacer pour centrer le titre au milieu
                Spacer()
                
                // Titre principal centré au milieu de l'écran
                VStack(spacing: 20) {
                    Text("Et Si Vous Retombiez Amoureux En Vous Parlant Vraiment ?")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 20)
                    
                    // Sous-titre
                    Text("Redécouvrez-vous à travers des questions qui raviveront votre amour.")
                        .font(.system(size: 18))
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 30)
                }
                
                // Spacer pour pousser les boutons vers le bas
                Spacer()
                
                // Boutons d'action collés en bas
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
                        print("🔥 AuthenticationView: Bouton 'J'ai déjà un compte' pressé")
                        performAppleSignIn()
                    }) {
                        Text("J'ai déjà un compte")
                            .font(.system(size: 16))
                            .foregroundColor(.white)
                            .underline()
                    }
                }
                .padding(.bottom, 50)
            }
        }
        .fullScreenCover(isPresented: $showingOnboarding) {
            OnboardingView()
                .environmentObject(appState)
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("UserAuthenticated"))) { _ in
            print("🔥 AuthenticationView: Notification d'authentification reçue de AuthenticationService")
            print("🔥 AuthenticationView: Utilisateur connecté via 'J'ai déjà un compte'")
            
            // Attendre un peu que Firebase se synchronise
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                if let user = self.firebaseService.currentUser {
                    print("🔥 AuthenticationView: Utilisateur existant trouvé: \(user.name)")
                    self.appState.authenticate(with: user)
                    self.appState.completeOnboarding()
                } else {
                    print("🔥 AuthenticationView: Nouvel utilisateur via Apple Sign In, démarrage onboarding")
                    self.appState.startUserOnboarding()
                    self.showingOnboarding = true
                }
            }
        }
    }
    
    private func startOnboarding() {
        print("🔥 AuthenticationView: Démarrage manuel de l'onboarding")
        appState.startUserOnboarding()
        showingOnboarding = true
    }
    
    private func performAppleSignIn() {
        print("🔥 AuthenticationView: Déclenchement de l'authentification Apple via AuthenticationService")
        authService.signInWithApple()
    }
    

} 