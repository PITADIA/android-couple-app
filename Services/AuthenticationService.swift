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
    @Published var appleUserDisplayName: String?
    
    private var currentNonce: String?
    private var isSignInInProgress = false
    private var lastProcessedCredentialTime = Date.distantPast
    
    override init() {
        super.init()
        
        // Vérifier si l'utilisateur est déjà connecté
        if let user = Auth.auth().currentUser {
            print("🔥 AuthenticationService: Utilisateur déjà connecté: \(user.uid)")
            NSLog("🔥 AuthenticationService: Utilisateur déjà connecté: \(user.uid)")
            self.currentUser = user
            self.isAuthenticated = true
            
            // Charger le nom Apple sauvegardé
            loadSavedAppleDisplayName(for: user.uid)
        }
        
        // Écouter les changements d'authentification
        _ = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            DispatchQueue.main.async {
                self?.currentUser = user
                self?.isAuthenticated = user != nil
                
                if let user = user {
                    print("✅ Utilisateur connecté")
                    // Charger le nom Apple sauvegardé pour cet utilisateur
                    self?.loadSavedAppleDisplayName(for: user.uid)
                } else {
                    print("❌ Utilisateur déconnecté")
                }
            }
        }
    }
    
    // MARK: - Public Methods
    
    func signInWithApple() {
        // Protection contre les appels multiples
        guard !isSignInInProgress else {
            print("⚠️ Sign In déjà en cours")
            return
        }
        
        // Protection contre les appels trop fréquents (moins de 2 secondes)
        let timeSinceLastCall = Date().timeIntervalSince(lastProcessedCredentialTime)
        guard timeSinceLastCall > 2.0 else {
            print("⚠️ Appels trop fréquents")
            return
        }
        
        print("🔐 Début authentification Apple")
        
        isSignInInProgress = true
        
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
        print("🔥 SignOut: signOut() appelé")
        print("🔥 SignOut: Thread: \(Thread.current)")
        print("🔥 SignOut: Stack trace: \(Thread.callStackSymbols.prefix(5))")
        print("🔥 AuthenticationService: Déconnexion")
        NSLog("🔥 AuthenticationService: Déconnexion")
        
        do {
            try Auth.auth().signOut()
            // Nettoyer le nom Apple sauvegardé
            DispatchQueue.main.async {
                self.appleUserDisplayName = nil
            }
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
    
    // MARK: - Apple Display Name Persistence
    
    private func loadSavedAppleDisplayName(for userID: String) {
        let savedName = UserDefaults.standard.string(forKey: "AppleDisplayName_\(userID)")
        if let savedName = savedName, !savedName.isEmpty {
            print("📱 Nom Apple restauré")
            DispatchQueue.main.async {
                self.appleUserDisplayName = savedName
            }
        } else {
            DispatchQueue.main.async {
                self.appleUserDisplayName = nil
            }
        }
    }
}

// MARK: - ASAuthorizationControllerDelegate

extension AuthenticationService: ASAuthorizationControllerDelegate {
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        // Protection contre le traitement multiple
        let timeSinceLastProcessing = Date().timeIntervalSince(lastProcessedCredentialTime)
        guard timeSinceLastProcessing > 1.0 else {
            print("⚠️ Credentials déjà traités")
            return
        }
        
        lastProcessedCredentialTime = Date()
        print("✅ Autorisation Apple reçue")
        
        if let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential {
            
            // Récupérer le nom fourni par Apple (si disponible)
            if let fullName = appleIDCredential.fullName {
                let displayName = PersonNameComponentsFormatter.localizedString(from: fullName, style: .long)
                if !displayName.isEmpty {
                    print("✅ Nom Apple récupéré")
                    // Sauvegarder le nom dans UserDefaults pour les futures connexions
                    let userID = appleIDCredential.user
                    UserDefaults.standard.set(displayName, forKey: "AppleDisplayName_\(userID)")
                    DispatchQueue.main.async {
                        self.appleUserDisplayName = displayName
                    }
                } else {
                    // Essayer de charger depuis UserDefaults
                    loadSavedAppleDisplayName(for: appleIDCredential.user)
                }
            } else {
                // Pas de nom fourni, essayer de charger depuis UserDefaults
                loadSavedAppleDisplayName(for: appleIDCredential.user)
            }
            
            guard let nonce = currentNonce else {
                print("❌ Nonce invalide")
                DispatchQueue.main.async {
                    self.isLoading = false
                    self.isSignInInProgress = false
                    self.errorMessage = "Erreur d'authentification"
                }
                return
            }
            
            guard let appleIDToken = appleIDCredential.identityToken else {
                print("❌ Token Apple manquant")
                DispatchQueue.main.async {
                    self.isLoading = false
                    self.isSignInInProgress = false
                    self.errorMessage = "Token manquant"
                }
                return
            }
            
            guard let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
                print("❌ Erreur sérialisation token")
                DispatchQueue.main.async {
                    self.isLoading = false
                    self.isSignInInProgress = false
                    self.errorMessage = "Erreur token"
                }
                return
            }
            
            // Créer les credentials Firebase
            let credential = OAuthProvider.credential(providerID: AuthProviderID.apple,
                                                      idToken: idTokenString,
                                                      rawNonce: nonce)
            
            // Se connecter à Firebase
            Auth.auth().signIn(with: credential) { [weak self] result, error in
                DispatchQueue.main.async {
                    self?.isLoading = false
                    self?.isSignInInProgress = false
                    
                    if let error = error {
                        print("❌ Erreur Firebase: \(error.localizedDescription)")
                        self?.errorMessage = error.localizedDescription
                        return
                    }
                    
                    if let user = result?.user {
                        print("✅ Connexion Firebase réussie")
                        
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
        DispatchQueue.main.async {
            self.isLoading = false
            self.isSignInInProgress = false
            
            if let authError = error as? ASAuthorizationError {
                switch authError.code {
                case .canceled:
                    print("⚠️ Authentification annulée")
                    // Ne pas afficher d'erreur pour une annulation
                    return
                case .failed:
                    self.errorMessage = "Échec de l'authentification"
                case .invalidResponse:
                    self.errorMessage = "Réponse invalide"
                case .notHandled:
                    self.errorMessage = "Authentification non gérée"
                case .unknown:
                    self.errorMessage = "Erreur inconnue"
                default:
                    self.errorMessage = "Erreur d'authentification"
                }
                print("❌ Erreur authentification: \(self.errorMessage ?? "inconnue")")
            } else {
                self.errorMessage = "Erreur système"
                print("❌ Erreur système")
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