import SwiftUI
import StoreKit

struct SubscriptionView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var receiptService = AppleReceiptService.shared
    @Environment(\.dismiss) private var dismiss
    @State private var showingAppleSignIn = false
    @State private var showingSuccessMessage = false
    @State private var purchaseCompleted = false
    
    var body: some View {
        ZStack {
            // Fond gris clair identique aux autres pages d'onboarding
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Espacement en haut
                Spacer()
                    .frame(height: 80)
                
                // Titre en haut (design moderne)
                Text("Commencez votre essai gratuit de 3 jours dès maintenant")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
                
                Spacer()
                
                // Contenu principal - Nouvelles fonctionnalités
                VStack(spacing: 25) {
                    NewFeatureRow(
                        title: "✓ Apprenez à mieux vous connaître",
                        subtitle: "Parce qu'aimer, c'est aussi se poser les bonnes questions. Explorez l'univers intérieur de votre partenaire, une question à la fois."
                    )
                    
                    NewFeatureRow(
                        title: "✓ Resserrez le lien qui vous unit",
                        subtitle: "Ravivez la flamme avec des échanges sincères, profonds, et pleins de tendresse."
                    )
                    
                    NewFeatureRow(
                        title: "✓ Aimez-vous encore plus fort",
                        subtitle: "Débloquez nos plus de 2000 questions à la fois fun, profondes, rassurantes, et passez un merveilleux moment ensemble."
                    )
                }
                .padding(.horizontal, 25)
                
                Spacer()
                
                // Section prix et bouton collée en bas (design moderne)
                VStack(spacing: 15) {
                    HStack(spacing: 5) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.black)
                        
                        Text("Essai Gratuit de 3 jours, puis 4,99 € / semaine")
                            .font(.system(size: 14))
                            .foregroundColor(.black)
                    }
                    .padding(.horizontal, 30)
                    
                    // Bouton principal (design moderne)
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
                        .background(Color(hex: "#FD267A"))
                        .cornerRadius(28)
                    }
                    .disabled(receiptService.isLoading)
                    .padding(.horizontal, 30)
                    
                    // Bouton "Continuer sans premium" (nouveau)
                    Button("Continuer sans mon accès Premium") {
                        print("🔥 SubscriptionView: Continuer sans premium")
                        appState.freemiumManager?.dismissSubscription()
                        dismiss()
                    }
                    .font(.system(size: 16))
                    .foregroundColor(.black.opacity(0.6))
                    .padding(.top, 10)
                }
                .padding(.bottom, 20)
                
                // Liens légaux et restaurer tout en bas de l'écran (design moderne)
                HStack(spacing: 15) {
                    Button("Conditions générales") {
                        if let url = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/") {
                            UIApplication.shared.open(url)
                        }
                    }
                    .font(.system(size: 12))
                    .foregroundColor(.black.opacity(0.5))
                    
                    Button("Politique de confidentialité") {
                        if let url = URL(string: "https://love2lovesite.onrender.com") {
                            UIApplication.shared.open(url)
                        }
                    }
                    .font(.system(size: 12))
                    .foregroundColor(.black.opacity(0.5))
                    
                    Button("Restaurer") {
                        print("🔥 SubscriptionView: Tentative de restauration des achats")
                        receiptService.restorePurchases()
                    }
                    .font(.system(size: 12))
                    .foregroundColor(.black.opacity(0.5))
                }
                .padding(.bottom, 30)
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
        .navigationBarHidden(true)
        .onAppear {
            print("🔥 SubscriptionView: Vue apparue")
            // Analytics - tracker l'affichage de la vue subscription
            appState.freemiumManager?.trackUpgradePromptShown()
        }
        .onReceive(receiptService.$isSubscribed) { isSubscribed in
            print("🔥 SubscriptionView: Changement d'état d'abonnement: \(isSubscribed)")
            if isSubscribed {
                // L'utilisateur s'est abonné, mettre à jour Firebase et fermer
                print("🔥 SubscriptionView: Abonnement validé - mise à jour Firebase")
                
                // Mettre à jour l'utilisateur dans Firebase
                if var currentUser = appState.currentUser {
                    currentUser.isSubscribed = true
                    appState.updateUser(currentUser)
                }
                
                // Analytics
                appState.freemiumManager?.trackConversion()
                
                // Fermer la vue
                appState.freemiumManager?.dismissSubscription()
                dismiss()
            }
        }
        .onReceive(receiptService.$errorMessage) { errorMessage in
            if let error = errorMessage {
                print("🔥 SubscriptionView: Erreur reçue: \(error)")
            }
        }
    }
    
    private func purchaseSubscription() {
        print("🔥 SubscriptionView: Tentative d'achat")
        // Analytics - tracker tentative d'achat
        appState.freemiumManager?.trackUpgradePromptShown()
        receiptService.purchaseSubscription()
    }
}



// Composant PremiumFeatureRow - pour compatibilité si utilisé ailleurs
struct PremiumFeatureRow: View {
    let icon: String
    let title: String
    let description: String
    
    var body: some View {
        HStack(spacing: 15) {
            Text(icon)
                .font(.system(size: 24))
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.black)
                
                Text(description)
                    .font(.system(size: 14))
                    .foregroundColor(.black.opacity(0.7))
            }
            
            Spacer()
        }
    }
} 