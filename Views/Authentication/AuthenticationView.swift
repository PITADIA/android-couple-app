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
            // Fond gris clair identique aux pages d'onboarding
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Logo et nom de l'app en haut
                VStack(spacing: 20) {
                    HStack(spacing: 15) {
                        // Logo Leetchi à gauche
                        Image("Leetchi")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 40, height: 40)
                        
                        Text("Love2Love")
                            .font(.system(size: 50, weight: .bold))
                            .foregroundColor(.black)
                    }
                    .padding(.top, 60)
                }
                
                // Premier Spacer pour pousser le contenu vers le centre
                Spacer()
                
                // Titre principal centré - directement sur le background
                VStack(spacing: 20) {
                    Text("L'application qui vous rapproche")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 30)
                    
                    // Sous-titre
                    Text("Redécouvrez-vous à travers des questions qui raviveront votre amour et sauvegardez les moments passés ensemble.")
                        .font(.system(size: 18))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 30)
                }
                
                // Deuxième Spacer pour pousser les boutons vers le bas
                Spacer()
                
                // Zone blanche collée en bas avec les boutons
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
                            .foregroundColor(.black.opacity(0.6))
                            .underline()
                    }
                }
                .padding(.vertical, 20)
                .background(Color.white)
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