import SwiftUI
import AuthenticationServices
import FirebaseAuth
import CryptoKit

struct AuthenticationStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @EnvironmentObject var appState: AppState
    @StateObject private var firebaseService = FirebaseService.shared
    @StateObject private var authService = AuthenticationService.shared
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre la barre de progression et le titre
            Spacer()
                .frame(height: 60)
            
            // Contenu en haut
            VStack(spacing: 40) {
                // Titre
                Text("Crée ton compte et sécurise tes données")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
            }
            
            Spacer()
            
            // Bouton Sign in with Apple collé en bas
            Button(action: {
                print("🔥 AuthenticationStepView: Déclenchement de l'authentification Apple via AuthenticationService")
                NSLog("🔥🔥🔥 APPLE SIGN IN: DECLENCHEMENT VIA AUTH SERVICE")
                authService.signInWithApple()
            }) {
                HStack {
                    Image(systemName: "applelogo")
                        .font(.system(size: 20, weight: .medium))
                    Text("Continuer avec Apple")
                        .font(.system(size: 18, weight: .semibold))
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(Color.white)
                .cornerRadius(28)
            }
            .padding(.horizontal, 30)
            .padding(.bottom, 50)
        }
        .onAppear {
            print("🔥 AuthenticationStepView: Vue d'authentification apparue")
            NSLog("🔥🔥🔥 AUTHENTICATION: VUE APPARUE")
            
            // Vérifications de debug détaillées
            print("🔥 AuthenticationStepView: Bundle ID: \(Bundle.main.bundleIdentifier ?? "nil")")
            print("🔥 AuthenticationStepView: Environnement: \(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != nil ? "Simulateur" : "Appareil physique")")
            print("🔥 AuthenticationStepView: Auth.currentUser: \(Auth.auth().currentUser?.uid ?? "nil")")
            print("🔥 AuthenticationStepView: AppState.isAuthenticated: \(appState.isAuthenticated)")
            NSLog("🔥🔥🔥 AUTHENTICATION: BUNDLE ID: %@", Bundle.main.bundleIdentifier ?? "nil")
            
            // Vérifier si l'utilisateur est déjà authentifié
            if appState.isAuthenticated && Auth.auth().currentUser != nil {
                print("🔥 AuthenticationStepView: Utilisateur déjà authentifié, passage direct à l'étape suivante")
                NSLog("🔥🔥🔥 AUTHENTICATION: UTILISATEUR DEJA AUTHENTIFIE")
                
                // Créer le document utilisateur avec les données d'onboarding collectées
                if let currentUser = Auth.auth().currentUser {
                    createPartialUserDocument(firebaseUser: currentUser)
                }
                
                // Passer directement à l'étape suivante
                viewModel.completeAuthentication()
            } else {
                print("🔥 AuthenticationStepView: Utilisateur non authentifié - prêt pour Apple Sign In")
                NSLog("🔥🔥🔥 AUTHENTICATION: UTILISATEUR NON AUTHENTIFIE")
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("UserAuthenticated"))) { _ in
            print("🔥 AuthenticationStepView: Notification d'authentification reçue de AuthenticationService")
            NSLog("🔥🔥🔥 AUTHENTICATION: NOTIFICATION RECUE")
            
            // Attendre un peu que Firebase se synchronise
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                if let firebaseUser = Auth.auth().currentUser {
                    print("🔥 AuthenticationStepView: Utilisateur Firebase trouvé: \(firebaseUser.uid)")
                    NSLog("🔥🔥🔥 AUTHENTICATION: FIREBASE USER TROUVE")
                    
                    // Créer immédiatement un document utilisateur partiel avec les données d'onboarding
                    self.createPartialUserDocument(firebaseUser: firebaseUser)
                    
                    // Passer à l'étape suivante (abonnement)
                    viewModel.completeAuthentication()
                } else {
                    print("❌ AuthenticationStepView: Aucun utilisateur Firebase trouvé")
                    NSLog("❌❌❌ AUTHENTICATION: AUCUN FIREBASE USER")
                }
            }
        }
        .onChange(of: authService.isAuthenticated) { _, isAuth in
            print("🔥 AuthenticationStepView: Changement d'authentification: \(isAuth)")
            if isAuth {
                print("🔥 AuthenticationStepView: Authentification réussie via AuthenticationService")
                NSLog("🔥🔥🔥 AUTHENTICATION: SUCCES VIA AUTH SERVICE")
                
                if let firebaseUser = Auth.auth().currentUser {
                    createPartialUserDocument(firebaseUser: firebaseUser)
                    viewModel.completeAuthentication()
                }
            }
        }
    }
    
    private func createPartialUserDocument(firebaseUser: FirebaseAuth.User) {
        print("🔥 AuthenticationStepView: Création d'un document utilisateur partiel")
        print("🔥🔥🔥 AUTH PARTIAL: CREATION DOCUMENT PARTIEL PENDANT ONBOARDING")
        NSLog("🔥 AuthenticationStepView: Création d'un document utilisateur partiel")
        
        // NOUVEAU: Marquer le début du processus d'onboarding pour éviter les redirections
        FirebaseService.shared.startOnboardingProcess()
        
        // NOUVEAU: Aussi marquer dans AppState que l'onboarding est en cours
        appState.isOnboardingInProgress = true
        print("🔥🔥🔥 AUTH PARTIAL: AppState.isOnboardingInProgress = true")
        
        // Créer un utilisateur avec les données d'onboarding collectées
        let partialUser = User(
            name: viewModel.userName,
            birthDate: viewModel.birthDate,
            relationshipGoals: viewModel.selectedGoals,
            relationshipDuration: viewModel.relationshipDuration,
            relationshipImprovement: viewModel.relationshipImprovement.isEmpty ? nil : viewModel.relationshipImprovement,
            questionMode: viewModel.questionMode.isEmpty ? nil : viewModel.questionMode,
            partnerCode: nil,
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