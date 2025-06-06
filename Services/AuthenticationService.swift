import Foundation
import AuthenticationServices
import FirebaseAuth
import CryptoKit

class AuthenticationService: NSObject, ObservableObject {
    static let shared = AuthenticationService()
    
    @Published var isAuthenticated: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var currentUser: FirebaseAuth.User?
    
    private var currentNonce: String?
    
    override init() {
        super.init()
        
        // Vérifier si l'utilisateur est déjà connecté
        if let user = Auth.auth().currentUser {
            print("🔥 AuthenticationService: Utilisateur déjà connecté: \(user.uid)")
            NSLog("🔥 AuthenticationService: Utilisateur déjà connecté: \(user.uid)")
            self.currentUser = user
            self.isAuthenticated = true
        }
        
        // Écouter les changements d'authentification
        Auth.auth().addStateDidChangeListener { [weak self] _, user in
            DispatchQueue.main.async {
                self?.currentUser = user
                self?.isAuthenticated = user != nil
                
                if let user = user {
                    print("🔥 AuthenticationService: Utilisateur connecté: \(user.uid)")
                    NSLog("🔥 AuthenticationService: Utilisateur connecté: \(user.uid)")
                } else {
                    print("🔥 AuthenticationService: Utilisateur déconnecté")
                    NSLog("🔥 AuthenticationService: Utilisateur déconnecté")
                }
            }
        }
    }
    
    // MARK: - Public Methods
    
    func signInWithApple() {
        print("🔥 AuthenticationService: Début de la connexion Apple ID")
        NSLog("🔥 AuthenticationService: Début de la connexion Apple ID")
        
        let nonce = randomNonceString()
        currentNonce = nonce
        
        let appleIDProvider = ASAuthorizationAppleIDProvider()
        let request = appleIDProvider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)
        
        let authorizationController = ASAuthorizationController(authorizationRequests: [request])
        authorizationController.delegate = self
        authorizationController.presentationContextProvider = self
        authorizationController.performRequests()
        
        isLoading = true
        errorMessage = nil
    }
    
    func signOut() {
        print("🔥 AuthenticationService: Déconnexion")
        NSLog("🔥 AuthenticationService: Déconnexion")
        
        do {
            try Auth.auth().signOut()
        } catch {
            print("🔥 AuthenticationService: Erreur de déconnexion: \(error.localizedDescription)")
            NSLog("🔥 AuthenticationService: Erreur de déconnexion: \(error.localizedDescription)")
            errorMessage = error.localizedDescription
        }
    }
    
    // MARK: - Private Methods
    
    private func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        let charset: [Character] =
        Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remainingLength = length
        
        while remainingLength > 0 {
            let randoms: [UInt8] = (0 ..< 16).map { _ in
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

// MARK: - ASAuthorizationControllerDelegate

extension AuthenticationService: ASAuthorizationControllerDelegate {
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        print("🔥 AuthenticationService: Autorisation Apple ID reçue")
        NSLog("🔥 AuthenticationService: Autorisation Apple ID reçue")
        
        if let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential {
            guard let nonce = currentNonce else {
                print("🔥 AuthenticationService: Erreur - Nonce invalide")
                NSLog("🔥 AuthenticationService: Erreur - Nonce invalide")
                DispatchQueue.main.async {
                    self.isLoading = false
                    self.errorMessage = "Erreur d'authentification"
                }
                return
            }
            
            guard let appleIDToken = appleIDCredential.identityToken else {
                print("🔥 AuthenticationService: Erreur - Token Apple ID manquant")
                NSLog("🔥 AuthenticationService: Erreur - Token Apple ID manquant")
                DispatchQueue.main.async {
                    self.isLoading = false
                    self.errorMessage = "Token d'authentification manquant"
                }
                return
            }
            
            guard let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
                print("🔥 AuthenticationService: Erreur - Impossible de sérialiser le token")
                NSLog("🔥 AuthenticationService: Erreur - Impossible de sérialiser le token")
                DispatchQueue.main.async {
                    self.isLoading = false
                    self.errorMessage = "Erreur de sérialisation du token"
                }
                return
            }
            
            // Créer les credentials Firebase
            let credential = OAuthProvider.credential(withProviderID: "apple.com",
                                                    idToken: idTokenString,
                                                    rawNonce: nonce)
            
            // Se connecter à Firebase
            Auth.auth().signIn(with: credential) { [weak self] result, error in
                DispatchQueue.main.async {
                    self?.isLoading = false
                    
                    if let error = error {
                        print("🔥 AuthenticationService: Erreur Firebase: \(error.localizedDescription)")
                        NSLog("🔥 AuthenticationService: Erreur Firebase: \(error.localizedDescription)")
                        self?.errorMessage = error.localizedDescription
                        return
                    }
                    
                    if let user = result?.user {
                        print("🔥 AuthenticationService: ✅ Connexion réussie: \(user.uid)")
                        NSLog("🔥 AuthenticationService: ✅ Connexion réussie: \(user.uid)")
                        
                        // Notifier le succès de l'authentification
                        NotificationCenter.default.post(
                            name: NSNotification.Name("UserAuthenticated"),
                            object: nil
                        )
                    }
                }
            }
        }
    }
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        print("🔥 AuthenticationService: Erreur d'autorisation: \(error.localizedDescription)")
        NSLog("🔥 AuthenticationService: Erreur d'autorisation: \(error.localizedDescription)")
        
        DispatchQueue.main.async {
            self.isLoading = false
            
            if let authError = error as? ASAuthorizationError {
                switch authError.code {
                case .canceled:
                    print("🔥 AuthenticationService: Connexion annulée par l'utilisateur")
                    NSLog("🔥 AuthenticationService: Connexion annulée par l'utilisateur")
                    // Ne pas afficher d'erreur pour une annulation
                    return
                case .failed:
                    self.errorMessage = "Échec de l'authentification"
                case .invalidResponse:
                    self.errorMessage = "Réponse d'authentification invalide"
                case .notHandled:
                    self.errorMessage = "Authentification non gérée"
                case .unknown:
                    self.errorMessage = "Erreur d'authentification inconnue"
                @unknown default:
                    self.errorMessage = "Erreur d'authentification"
                }
            } else {
                self.errorMessage = error.localizedDescription
            }
        }
    }
}

// MARK: - ASAuthorizationControllerPresentationContextProviding

extension AuthenticationService: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first else {
            fatalError("No window available")
        }
        return window
    }
} 