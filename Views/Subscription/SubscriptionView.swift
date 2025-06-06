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
            // Fond dégradé - même style que SubscriptionStepView
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.8, green: 0.2, blue: 0.2),
                    Color(red: 0.9, green: 0.4, blue: 0.1)
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header avec bouton fermer et restaurer
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
                    
                    Button(action: {
                        receiptService.restorePurchases()
                    }) {
                        if receiptService.isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.8)
                        } else {
                            Text("Restaurer")
                                .font(.system(size: 16, weight: .medium))
                                .foregroundColor(.white)
                        }
                    }
                    .disabled(receiptService.isLoading)
                }
                .padding(.horizontal, 20)
                .padding(.top, 50)
                
                Spacer()
                
                // Contenu principal
                VStack(spacing: 30) {
                    // Icône
                    Text("🔥")
                        .font(.system(size: 80))
                    
                    // Titre avec contexte de la catégorie bloquée
                    VStack(spacing: 10) {
                        if let blockedCategory = appState.freemiumManager?.blockedCategoryAttempt {
                            Text("DÉBLOQUEZ")
                                .font(.system(size: 32, weight: .bold))
                                .foregroundColor(.white)
                            
                            Text("\"\(blockedCategory.title)\"")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                            
                            Text("ET TOUTES LES")
                                .font(.system(size: 32, weight: .bold))
                                .foregroundColor(.white)
                            
                            Text("FONCTIONNALITÉS")
                                .font(.system(size: 32, weight: .bold))
                                .foregroundColor(.white)
                            
                            Text("PREMIUM")
                                .font(.system(size: 32, weight: .bold))
                                .foregroundColor(.white)
                        } else {
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
                    }
                    .multilineTextAlignment(.center)
                    
                    // Sous-titre
                    Text("Découvrez tout le potentiel de l'app !")
                        .font(.system(size: 16))
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                    
                    // Fonctionnalités - même style que SubscriptionStepView
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
                                    Color.orange,
                                    Color.red
                                ]),
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(28)
                    }
                    .disabled(receiptService.isLoading)
                    .padding(.horizontal, 30)
                    
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