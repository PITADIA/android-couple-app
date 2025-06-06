import SwiftUI
import StoreKit

struct SubscriptionStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @StateObject private var receiptService = AppleReceiptService.shared
    @Environment(\.dismiss) private var dismiss
    
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
                // Bouton fermer
                HStack {
                    Button(action: {
                        // Fermer sans s'abonner - finaliser l'onboarding
                        print("🔥 SubscriptionStepView: Bouton fermer pressé - finalisation sans abonnement")
                        NSLog("🔥 SubscriptionStepView: Bouton fermer pressé - finalisation sans abonnement")
                        viewModel.skipSubscription()
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 50)
                
                Spacer()
                
                // Contenu principal
                VStack(spacing: 30) {
                    // Icône
                    Text("🔥")
                        .font(.system(size: 80))
                    
                    // Titre
                    VStack(spacing: 10) {
                        Text("DÉBLOQUEZ")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(.white)
                        
                        Text("TOUTES LES")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(.white)
                        
                        Text("FONCTIONNALITÉS")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(.white)
                        
                        Text("PREMIUM")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .multilineTextAlignment(.center)
                    
                    // Sous-titre
                    Text("Votre compte est créé ! Maintenant,\ndécouvrez tout le potentiel de l'app !")
                        .font(.system(size: 16))
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                    
                    // Fonctionnalités
                    VStack(spacing: 15) {
                        FeatureRow(icon: "calendar", text: "Nouveau contenu chaque semaine")
                        FeatureRow(icon: "heart.fill", text: "Mode surprise quotidien")
                        FeatureRow(icon: "key.fill", text: "Accès illimité à tous les packs de cartes")
                        FeatureRow(icon: "lock.fill", text: "Confidentialité garantie")
                        FeatureRow(icon: "clock.fill", text: "Annule quand tu veux")
                    }
                    .padding(.horizontal, 40)
                }
                
                Spacer()
                
                // Section prix et bouton
                VStack(spacing: 15) {
                    Text("Essai gratuit de 3 jours, puis 4,99 € hebdomadaire")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)
                    
                    // Bouton principal
                    Button(action: {
                        purchaseSubscription()
                    }) {
                        HStack {
                            if receiptService.isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.8)
                                Text("CHARGEMENT...")
                            } else {
                                Text("COMMENCER L'ESSAI")
                            }
                        }
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            LinearGradient(
                                gradient: Gradient(colors: [
                                    Color(hex: "#FD267A"),
                                    Color(hex: "#FF655B")
                                ]),
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(28)
                    }
                    .disabled(receiptService.isLoading)
                    .padding(.horizontal, 30)
                    
                    // Bouton "Continuer sans premium"
                    Button("Continuer sans Premium") {
                        print("🔥 SubscriptionStepView: Continuer sans premium")
                        NSLog("🔥 SubscriptionStepView: Continuer sans premium")
                        viewModel.skipSubscription()
                    }
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.8))
                    .padding(.top, 10)
                    
                    // Bouton restaurer
                    Button("Restaurer les achats précédents") {
                        print("🔥 SubscriptionStepView: Tentative de restauration des achats")
                        NSLog("🔥 SubscriptionStepView: Tentative de restauration des achats")
                        receiptService.restorePurchases()
                    }
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.7))
                    
                    // Liens légaux
                    HStack(spacing: 40) {
                        Button("Conditions d'utilisation") {
                            // Ouvrir les conditions
                        }
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.7))
                        
                        Button("Politique de confidentialité") {
                            // Ouvrir la politique
                        }
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.7))
                    }
                }
                .padding(.bottom, 50)
            }
            
            // Affichage des erreurs
            if let errorMessage = receiptService.errorMessage {
                VStack {
                    Spacer()
                    Text(errorMessage)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.red.opacity(0.8))
                        .cornerRadius(10)
                        .padding(.horizontal, 30)
                        .padding(.bottom, 100)
                }
            }
        }
        .onAppear {
            print("🔥 SubscriptionStepView: Vue apparue")
            NSLog("🔥 SubscriptionStepView: Vue apparue")
            print("🔥 SubscriptionStepView: Statut d'abonnement actuel: \(receiptService.isSubscribed)")
            NSLog("🔥 SubscriptionStepView: Statut d'abonnement actuel: \(receiptService.isSubscribed)")
        }
        .onReceive(receiptService.$isSubscribed) { isSubscribed in
            print("🔥 SubscriptionStepView: Changement d'état d'abonnement: \(isSubscribed)")
            NSLog("🔥 SubscriptionStepView: Changement d'état d'abonnement: \(isSubscribed)")
            if isSubscribed {
                // L'utilisateur s'est abonné, finaliser l'onboarding
                print("🔥 SubscriptionStepView: Abonnement validé - finalisation de l'onboarding")
                NSLog("🔥 SubscriptionStepView: Abonnement validé - finalisation de l'onboarding")
                viewModel.completeSubscription()
            }
        }
        .onReceive(receiptService.$errorMessage) { errorMessage in
            if let error = errorMessage {
                print("🔥 SubscriptionStepView: Erreur reçue: \(error)")
                NSLog("🔥 SubscriptionStepView: Erreur reçue: \(error)")
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("SubscriptionValidated"))) { _ in
            print("🔥 SubscriptionStepView: Notification de validation d'abonnement reçue")
            NSLog("🔥 SubscriptionStepView: Notification de validation d'abonnement reçue")
            viewModel.completeSubscription()
        }
    }
    
    private func purchaseSubscription() {
        print("🔥 SubscriptionStepView: Tentative d'achat d'abonnement avec AppleReceiptService")
        NSLog("🔥 SubscriptionStepView: Tentative d'achat d'abonnement avec AppleReceiptService")
        
        receiptService.purchaseSubscription()
    }
}

struct FeatureRow: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack(spacing: 15) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(.white)
                .frame(width: 20)
            
            Text(text)
                .font(.system(size: 14))
                .foregroundColor(.white)
            
            Spacer()
        }
    }
} 