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
            // Fond dégradé moderne identique à SubscriptionStepView
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
                // Header avec bouton fermer (design moderne)
                HStack {
                    Button(action: {
                        print("🔥 SubscriptionView: Bouton X pressé - retour à MainView avec restrictions")
                        
                        // Notifier le FreemiumManager de la fermeture
                        appState.freemiumManager?.dismissSubscription()
                        
                        // Analytics - tracker que l'utilisateur a fermé sans s'abonner
                        if let blockedCategory = appState.freemiumManager?.blockedCategoryAttempt {
                            print("🔥 SubscriptionView: Fermeture après tentative d'accès à: \(blockedCategory.title)")
                        }
                        
                        dismiss()
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 50)
                
                // Titre en haut juste après la croix (design moderne)
                Text("Commencez votre essai gratuit de 3 jours dès maintenant")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
                    .padding(.top, 30)
                
                Spacer()
                
                // Contenu principal (design moderne)
                VStack(spacing: 30) {
                    // Fonctionnalités - même style que SubscriptionStepView
                    VStack(spacing: 15) {
                        FeatureRow(icon: "heart.fill", text: "Mode surprise quotidien")
                        FeatureRow(icon: "key.fill", text: "Accès illimité à tous les packs de cartes")
                        FeatureRow(icon: "lock.fill", text: "Confidentialité garantie")
                        FeatureRow(icon: "clock.fill", text: "Annule quand tu veux")
                    }
                    .padding(.horizontal, 40)
                }
                
                Spacer()
                
                // Section prix et bouton collée en bas (design moderne)
                VStack(spacing: 15) {
                    HStack(spacing: 5) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                        
                        Text("Essai Gratuit de 3 jours, puis 4,99 € / semaine")
                            .font(.system(size: 14))
                            .foregroundColor(.white)
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
                    .foregroundColor(.white.opacity(0.8))
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
                    .foregroundColor(.white.opacity(0.7))
                    
                    Button("Politique de confidentialité") {
                        if let url = URL(string: "https://love2lovesite.onrender.com") {
                            UIApplication.shared.open(url)
                        }
                    }
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.7))
                    
                    Button("Restaurer") {
                        print("🔥 SubscriptionView: Tentative de restauration des achats")
                        receiptService.restorePurchases()
                    }
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.7))
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
                    .foregroundColor(.white)
                
                Text(description)
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.8))
            }
            
            Spacer()
        }
    }
} 