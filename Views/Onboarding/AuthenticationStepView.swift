import SwiftUI
import AuthenticationServices
import FirebaseAuth
import CryptoKit

struct AuthenticationStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @EnvironmentObject var appState: AppState
    @StateObject private var firebaseService = FirebaseService.shared
    @StateObject private var authService = AuthenticationService.shared
    
    // État pour éviter les appels multiples
    @State private var hasProcessedAuthentication = false
    
    var body: some View {
        ZStack {
            // Fond gris clair identique aux autres pages d'onboarding
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            // Interface normale - toujours visible
            VStack(spacing: 0) {
                // Espace entre la barre de progression et le titre
                Spacer()
                    .frame(height: 20)
                
                // Contenu en haut
                VStack(spacing: 40) {
                    // Titre
                    Text("create_secure_account".localized)
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)
                }
                
                Spacer()
                
                // Bouton Sign in with Apple (style simple pour éviter double déclenchement)
                Button(action: {
                    print("🔐 Authentification Apple démarrée")
                    authService.signInWithApple()
                }) {
                    HStack {
                        Image(systemName: "applelogo")
                            .font(.system(size: 20, weight: .medium))
                        Text("continue_with_apple".localized)
                            .font(.system(size: 18, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.black)
                    .cornerRadius(28)
                }
                .disabled(authService.isProcessingFirebaseAuth)
                .opacity(authService.isProcessingFirebaseAuth ? 0.6 : 1.0)
                .padding(.horizontal, 30)
                .padding(.bottom, 50)
            }
            
            // Overlay de chargement Firebase - affiché seulement après Face ID
            if authService.isProcessingFirebaseAuth {
                VStack(spacing: 20) {
                    // Spinner Apple-style
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .black))
                        .scaleEffect(1.5)
                    
                    // Texte de chargement
                    Text("authentication_in_progress".localized)
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                }
                .transition(.opacity.combined(with: .scale))
                .animation(.smooth(duration: 0.3), value: authService.isProcessingFirebaseAuth)
            }
        }
        .onAppear {
            
            // Vérifier si l'utilisateur est déjà authentifié
            if appState.isAuthenticated && Auth.auth().currentUser != nil && !hasProcessedAuthentication {
                print("✅ Utilisateur déjà authentifié")
                
                hasProcessedAuthentication = true
                
                // Créer le document utilisateur avec les données d'onboarding collectées
                if let currentUser = Auth.auth().currentUser {
                    createPartialUserDocument(firebaseUser: currentUser)
                }
                
                // Passer directement à l'étape suivante
                viewModel.completeAuthentication()
            } else {
                print("🔐 Prêt pour authentification")
            }
        }
        .task(id: authService.isAuthenticated) {
            // Utiliser task(id:) au lieu de onChange pour éviter les bugs NavigationStack
            guard authService.isAuthenticated && !hasProcessedAuthentication else {
                return
            }
            
            print("✅ Authentification réussie")
            
            hasProcessedAuthentication = true
            
            if let firebaseUser = Auth.auth().currentUser {
                createPartialUserDocument(firebaseUser: firebaseUser)
                viewModel.completeAuthentication()
            } else {
                print("❌ Aucun utilisateur trouvé")
            }
        }
    }
    
    private func createPartialUserDocument(firebaseUser: FirebaseAuth.User) {
        print("📝 Création document utilisateur")
        
        // Marquer le début du processus d'onboarding
        FirebaseService.shared.startOnboardingProcess()
        appState.isOnboardingInProgress = true
        
        // Créer un utilisateur avec les données d'onboarding collectées
        let partialUser = AppUser(
            name: viewModel.userName,
            birthDate: viewModel.birthDate,
            relationshipGoals: viewModel.selectedGoals,
            relationshipDuration: viewModel.relationshipDuration,
            relationshipImprovement: viewModel.selectedImprovements.joined(separator: ", ").isEmpty ? nil : viewModel.selectedImprovements.joined(separator: ", "),
            questionMode: viewModel.questionMode.isEmpty ? nil : viewModel.questionMode,
            partnerCode: nil,
            isSubscribed: false,
            onboardingInProgress: true,
            relationshipStartDate: viewModel.relationshipStartDate,
            profileImageURL: nil,
            currentLocation: viewModel.currentLocation
        )
        
        print("💾 Sauvegarde données partielles")
        
        // Sauvegarder les données partielles
        FirebaseService.shared.savePartialUserData(partialUser)
    }
} 