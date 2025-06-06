import SwiftUI
import AuthenticationServices
import FirebaseAuth
import CryptoKit

struct AuthenticationStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @StateObject private var firebaseService = FirebaseService.shared
    @State private var currentNonce: String?
    
    var body: some View {
        VStack(spacing: 40) {
            // Titre
            VStack(spacing: 15) {
                Text("CRÉONS TON COMPTE !")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("SÉCURISE TES DONNÉES")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 30)
            
            // Description
            Text("Crée ton compte maintenant pour sauvegarder tes préférences et accéder à toutes les fonctionnalités premium !")
                .font(.system(size: 16))
                .foregroundColor(.white.opacity(0.9))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 30)
            
            Spacer()
            
            // Icône
            Text("🔥")
                .font(.system(size: 100))
            
            // Avantages de créer un compte
            VStack(spacing: 15) {
                HStack(spacing: 15) {
                    Image(systemName: "icloud.fill")
                        .foregroundColor(.white)
                        .font(.system(size: 20))
                    Text("Sauvegarde automatique")
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                    Spacer()
                }
                
                HStack(spacing: 15) {
                    Image(systemName: "devices")
                        .foregroundColor(.white)
                        .font(.system(size: 20))
                    Text("Synchronisation multi-appareils")
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                    Spacer()
                }
                
                HStack(spacing: 15) {
                    Image(systemName: "heart.fill")
                        .foregroundColor(.white)
                        .font(.system(size: 20))
                    Text("Historique de tes conversations")
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                    Spacer()
                }
            }
            .padding(.horizontal, 30)
            .padding()
            .background(Color.white.opacity(0.1))
            .cornerRadius(15)
            .padding(.horizontal, 30)
            
            Spacer()
            
            // Bouton Sign in with Apple
            SignInWithAppleButton(
                onRequest: { request in
                    print("🔥 AuthenticationStepView: Début de la requête Apple Sign In")
                    NSLog("🔥🔥🔥 APPLE SIGN IN: DEBUT DE LA REQUETE")
                    
                    // Générer le nonce
                    let nonce = randomNonceString()
                    currentNonce = nonce
                    request.nonce = sha256(nonce)
                    
                    print("🔥 AuthenticationStepView: Bundle ID: \(Bundle.main.bundleIdentifier ?? "nil")")
                    print("🔥 AuthenticationStepView: Nonce généré et configuré")
                    NSLog("🔥🔥🔥 APPLE SIGN IN: BUNDLE ID: %@", Bundle.main.bundleIdentifier ?? "nil")
                    NSLog("🔥🔥🔥 APPLE SIGN IN: NONCE GENERE")
                    
                    request.requestedScopes = [.fullName, .email]
                    print("🔥 AuthenticationStepView: Scopes demandés: fullName, email")
                    NSLog("🔥🔥🔥 APPLE SIGN IN: SCOPES DEMANDES")
                },
                onCompletion: { result in
                    print("🔥 AuthenticationStepView: Réponse Apple Sign In reçue")
                    NSLog("🔥🔥🔥 APPLE SIGN IN: REPONSE RECUE")
                    handleSignInWithApple(result)
                }
            )
            .signInWithAppleButtonStyle(.white)
            .frame(height: 56)
            .cornerRadius(28)
            .padding(.horizontal, 30)
            
            // Bouton Skip (pour le debug)
            Button("⚠️ SKIP AUTH (DEBUG)") {
                print("🔥 AuthenticationStepView: Skip auth pour debug")
                NSLog("🔥🔥🔥 APPLE SIGN IN: SKIP POUR DEBUG")
                viewModel.completeAuthentication()
            }
            .font(.system(size: 12))
            .foregroundColor(.white.opacity(0.7))
            .padding(.top, 20)
        }
        .onAppear {
            print("🔥 AuthenticationStepView: Vue d'authentification apparue")
            NSLog("🔥🔥🔥 AUTHENTICATION: VUE APPARUE")
            
            // Vérifications de debug
            print("🔥 AuthenticationStepView: Bundle ID: \(Bundle.main.bundleIdentifier ?? "nil")")
            NSLog("🔥🔥🔥 AUTHENTICATION: BUNDLE ID: %@", Bundle.main.bundleIdentifier ?? "nil")
        }
    }
    
    private func handleSignInWithApple(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            print("✅ AuthenticationStepView: Authentification Apple réussie")
            NSLog("🔥🔥🔥 APPLE SIGN IN: SUCCES!")
            
            guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                print("❌ AuthenticationStepView: Credential Apple ID manquant")
                NSLog("❌❌❌ APPLE SIGN IN: CREDENTIAL MANQUANT")
                // Ne pas continuer si les credentials sont manquants
                return
            }
            
            guard let nonce = currentNonce else {
                print("❌ AuthenticationStepView: Nonce manquant")
                NSLog("❌❌❌ APPLE SIGN IN: NONCE MANQUANT")
                // Ne pas continuer si le nonce est manquant
                return
            }
            
            guard let appleIDToken = appleIDCredential.identityToken else {
                print("❌ AuthenticationStepView: Token Apple manquant")
                NSLog("❌❌❌ APPLE SIGN IN: TOKEN MANQUANT")
                // Ne pas continuer si le token est manquant
                return
            }
            
            guard let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
                print("❌ AuthenticationStepView: Erreur de décodage du token")
                NSLog("❌❌❌ APPLE SIGN IN: ERREUR DECODAGE TOKEN")
                // Ne pas continuer si le décodage échoue
                return
            }
            
            print("🔥 AuthenticationStepView: Création des credentials Firebase")
            NSLog("🔥🔥🔥 APPLE SIGN IN: CREATION CREDENTIALS FIREBASE")
            
            let credential = OAuthProvider.credential(withProviderID: "apple.com",
                                                      idToken: idTokenString,
                                                      rawNonce: nonce)
            
            // Authentification Firebase
            Auth.auth().signIn(with: credential) { result, error in
                DispatchQueue.main.async {
                    if let error = error {
                        print("❌ AuthenticationStepView: Erreur Firebase: \(error.localizedDescription)")
                        NSLog("❌❌❌ FIREBASE AUTH: ERREUR: %@", error.localizedDescription)
                        // Ne pas continuer en cas d'erreur Firebase
                        return
                    }
                    
                    guard let firebaseUser = result?.user else {
                        print("❌ AuthenticationStepView: Utilisateur Firebase manquant")
                        NSLog("❌❌❌ FIREBASE AUTH: USER MANQUANT")
                        // Ne pas continuer si l'utilisateur Firebase est manquant
                        return
                    }
                    
                    print("✅ AuthenticationStepView: Authentification Firebase réussie!")
                    print("🔥 AuthenticationStepView: Firebase UID: \(firebaseUser.uid)")
                    print("🔥 AuthenticationStepView: Email: \(firebaseUser.email ?? "nil")")
                    NSLog("✅✅✅ FIREBASE AUTH: SUCCES! UID: %@", firebaseUser.uid)
                    
                    // Créer immédiatement un document utilisateur partiel avec les données d'onboarding
                    self.createPartialUserDocument(firebaseUser: firebaseUser)
                    
                    // Passer à l'étape suivante (abonnement)
                    viewModel.completeAuthentication()
                }
            }
            
        case .failure(let error):
            print("❌ AuthenticationStepView: Erreur d'authentification Apple: \(error.localizedDescription)")
            NSLog("❌❌❌ APPLE SIGN IN: ERREUR: %@", error.localizedDescription)
            
            // Diagnostics supplémentaires
            if let nsError = error as NSError? {
                print("❌ AuthenticationStepView: Code d'erreur: \(nsError.code)")
                print("❌ AuthenticationStepView: Domaine: \(nsError.domain)")
                NSLog("❌❌❌ APPLE SIGN IN: CODE ERREUR: %ld", nsError.code)
            }
            
            // Vérifier si c'est une annulation par l'utilisateur
            if let authError = error as? ASAuthorizationError {
                switch authError.code {
                case .canceled:
                    print("🔥 AuthenticationStepView: Authentification annulée par l'utilisateur - rester sur cette étape")
                    NSLog("🔥 AuthenticationStepView: Authentification annulée par l'utilisateur")
                    // Ne pas appeler completeAuthentication() - rester sur l'étape d'authentification
                    return
                case .failed, .invalidResponse, .notHandled, .unknown:
                    print("🔥 AuthenticationStepView: Erreur d'authentification - rester sur cette étape")
                    NSLog("🔥 AuthenticationStepView: Erreur d'authentification")
                    // Ne pas appeler completeAuthentication() - rester sur l'étape d'authentification
                    return
                @unknown default:
                    print("🔥 AuthenticationStepView: Erreur d'authentification inconnue - rester sur cette étape")
                    NSLog("🔥 AuthenticationStepView: Erreur d'authentification inconnue")
                    // Ne pas appeler completeAuthentication() - rester sur l'étape d'authentification
                    return
                }
            }
            
            // Pour d'autres types d'erreurs, rester aussi sur cette étape
            print("🔥 AuthenticationStepView: Erreur non-Apple - rester sur cette étape")
            NSLog("🔥 AuthenticationStepView: Erreur non-Apple")
            // Ne pas appeler completeAuthentication() - l'utilisateur peut réessayer
        }
    }
    
    // MARK: - Nonce Generation
    
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
    
    private func createPartialUserDocument(firebaseUser: FirebaseAuth.User) {
        print("🔥 AuthenticationStepView: Création d'un document utilisateur partiel")
        print("🔥🔥🔥 AUTH PARTIAL: CREATION DOCUMENT PARTIEL PENDANT ONBOARDING")
        NSLog("🔥 AuthenticationStepView: Création d'un document utilisateur partiel")
        
        // NOUVEAU: Marquer le début du processus d'onboarding pour éviter les redirections
        FirebaseService.shared.startOnboardingProcess()
        
        // Créer un utilisateur avec les données d'onboarding collectées
        let partialUser = User(
            name: viewModel.userName,
            birthDate: viewModel.birthDate,
            relationshipGoals: viewModel.selectedGoals,
            relationshipDuration: viewModel.relationshipDuration,
            partnerCode: viewModel.partnerCode.isEmpty ? nil : viewModel.partnerCode,
            isSubscribed: false, // Sera mis à jour après l'abonnement
            onboardingInProgress: true // IMPORTANT: Marquer l'onboarding comme en cours
        )
        
        print("🔥 AuthenticationStepView: Sauvegarde des données partielles pour: \(partialUser.name)")
        print("🔥🔥🔥 AUTH PARTIAL: SAUVEGARDE PARTIELLE POUR: \(partialUser.name)")
        NSLog("🔥 AuthenticationStepView: Sauvegarde des données partielles pour: \(partialUser.name)")
        
        // IMPORTANT: Utiliser savePartialUserData pour marquer l'onboarding comme en cours
        FirebaseService.shared.savePartialUserData(partialUser)
    }
} 