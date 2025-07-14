import SwiftUI
import AuthenticationServices
import CryptoKit

struct AppleSignInView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var firebaseService = FirebaseService.shared
    @Environment(\.dismiss) private var dismiss
    @State private var currentNonce: String?
    
    var body: some View {
        ZStack {
            // Fond dégradé rouge/violet comme dans l'image
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.4, green: 0.1, blue: 0.2),
                    Color(red: 0.2, green: 0.05, blue: 0.15)
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header avec bouton retour et barre de progression
                HStack {
                    Button(action: {
                        print("🔥 AppleSignInView: Bouton retour pressé")
                        dismiss()
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .medium))
                            .foregroundColor(.white)
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 60)
                
                // Barre de progression
                HStack {
                    // Barre blanche (complète)
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.white)
                        .frame(height: 4)
                        .frame(maxWidth: .infinity)
                    
                    // Barres grises (incomplètes)
                    ForEach(0..<4, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 2)
                            .fill(Color.white.opacity(0.3))
                            .frame(height: 4)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                
                Spacer()
                
                // Contenu principal
                VStack(spacing: 30) {
                    // Titre principal
                    VStack(spacing: 15) {
                        Text(NSLocalizedString("one_more_step", comment: "One more step title"))
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                        
                        Text(NSLocalizedString("secure_account", comment: "Secure account title"))
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.horizontal, 30)
                    
                    // Description
                    Text(NSLocalizedString("create_account_description", comment: "Create account description"))
                        .font(.system(size: 16))
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                        .padding(.horizontal, 30)
                }
                
                Spacer()
                
                // Icône flamme
                Text("🔥")
                    .font(.system(size: 80))
                    .padding(.bottom, 40)
                
                Spacer()
                
                // Bouton Sign in with Apple
                SignInWithAppleButton(
                    onRequest: { request in
                        print("🔥 AppleSignInView: Début de la requête Apple Sign In")
                        let nonce = randomNonceString()
                        currentNonce = nonce
                        print("🔥 AppleSignInView: Nonce généré: \(nonce)")
                        request.requestedScopes = [.fullName, .email]
                        request.nonce = sha256(nonce)
                        print("🔥 AppleSignInView: Requête configurée avec scopes: fullName, email")
                    },
                    onCompletion: { result in
                        print("🔥 AppleSignInView: Réponse Apple Sign In reçue")
                        handleSignInWithApple(result)
                    }
                )
                .signInWithAppleButtonStyle(.white)
                .frame(height: 56)
                .cornerRadius(28)
                .padding(.horizontal, 30)
                .padding(.bottom, 50)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            print("🔥 AppleSignInView: Vue apparue")
        }
        .onReceive(firebaseService.$isAuthenticated) { isAuthenticated in
            print("🔥 AppleSignInView: Changement d'authentification reçu: \(isAuthenticated)")
            if isAuthenticated {
                // Utilisateur connecté, finaliser l'onboarding
                if let user = firebaseService.currentUser {
                    print("🔥 AppleSignInView: Utilisateur existant trouvé: \(user.name)")
                    appState.authenticate(with: user)
                    appState.completeOnboarding()
                } else {
                    // Nouvel utilisateur, continuer l'onboarding
                    print("🔥 AppleSignInView: Nouvel utilisateur, authentification sans données complètes")
                    appState.isAuthenticated = true
                    // Démarrer automatiquement l'onboarding pour ce nouvel utilisateur
                    appState.startUserOnboarding()
                }
                print("🔥 AppleSignInView: Fermeture de la vue")
                dismiss()
            }
        }
        .overlay(
            // Indicateur de chargement
            Group {
                if firebaseService.isLoading {
                    ZStack {
                        Color.black.opacity(0.3)
                            .ignoresSafeArea()
                        
                        VStack(spacing: 20) {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(1.5)
                            
                            Text(NSLocalizedString("connecting", comment: "Connecting message"))
                                .font(.system(size: 16))
                                .foregroundColor(.white)
                        }
                        .padding(30)
                        .background(Color.black.opacity(0.7))
                        .cornerRadius(15)
                    }
                }
            }
        )
        .alert("Erreur", isPresented: .constant(firebaseService.errorMessage != nil)) {
            Button("OK") {
                firebaseService.errorMessage = nil
            }
        } message: {
            Text(firebaseService.errorMessage ?? "")
        }
    }
    
    private func handleSignInWithApple(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            print("🔥 AppleSignInView: Authentification Apple réussie")
            print("🔥 AppleSignInView: Type de credential: \(type(of: authorization.credential))")
            
            if let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential {
                print("🔥 AppleSignInView: Apple ID Credential reçu")
                print("🔥 AppleSignInView: User ID: \(appleIDCredential.user)")
                print("🔥 AppleSignInView: Email: \(appleIDCredential.email ?? "nil")")
                print("🔥 AppleSignInView: Full Name: \(appleIDCredential.fullName?.givenName ?? "nil")")
                print("🔥 AppleSignInView: Identity Token présent: \(appleIDCredential.identityToken != nil)")
            }
            
            firebaseService.signInWithApple(authorization: authorization)
            
        case .failure(let error):
            print("❌ AppleSignInView: Erreur d'authentification Apple: \(error.localizedDescription)")
            firebaseService.errorMessage = "Erreur d'authentification: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Nonce Generation
    
    private func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remainingLength = length
        
        while remainingLength > 0 {
            let randoms: [UInt8] = (0..<16).map { _ in
                var random: UInt8 = 0
                let errorCode = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
                if errorCode != errSecSuccess {
                    fatalError("Unable to generate nonce. SecRandomCopyBytes failed with OSStatus \(errorCode)")
                }
                return random
            }
            
            randoms.forEach { random in
                if remainingLength == 0 {
                    return
                }
                
                if random < charset.count {
                    result.append(charset[Int(random)])
                    remainingLength -= 1
                }
            }
        }
        
        return result
    }
    
    private func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashedData = SHA256.hash(data: inputData)
        let hashString = hashedData.compactMap {
            String(format: "%02x", $0)
        }.joined()
        
        return hashString
    }
} 