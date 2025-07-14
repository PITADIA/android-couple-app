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
                // Header avec croix collée en haut
                HStack {
                    Button(action: {
                        print("🔥 SubscriptionView: Fermeture via croix")
                        appState.freemiumManager?.dismissSubscription()
                        dismiss()
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.black)
                    }
                    .padding(.leading, 20)
                    
                    Spacer()
                }
                .padding(.top, 10)
                
                // Titre principal collé sous la croix
                VStack(spacing: 8) {
                    Text("choose_plan".localized)
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                    
                    // Sous-titre
                    Text("partner_no_payment".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, 20)
                .padding(.top, 15)
                
                Spacer()
                    .frame(height: 20)
                
                // Contenu principal - Nouvelles fonctionnalités
                VStack(spacing: 25) {
                    
                    NewFeatureRow(
                        title: "love_stronger_feature".localized,
                        subtitle: "love_stronger_description".localized
                    )

                    NewFeatureRow(
                        title: "memory_box_feature".localized,
                        subtitle: "memory_box_description".localized
                    )

                    NewFeatureRow(
                        title: "love_map_feature".localized,
                        subtitle: "love_map_description".localized
                    )
                }
                .padding(.horizontal, 25)
                
                Spacer()
                
                // Garder l'espace entre les boutons de choix et le bouton commencer
                VStack(spacing: 0) {
                    // Section de sélection des plans - ORDRE INVERSÉ
                    VStack(spacing: 8) {
                        // Plan Hebdomadaire (maintenant en premier)
                        PlanSelectionCard(
                            planType: .weekly,
                            isSelected: receiptService.selectedPlan == .weekly,
                            onTap: {
                                receiptService.selectedPlan = .weekly
                            }
                        )
                        
                        // Plan Mensuel (maintenant en second) sans badge externe
                        PlanSelectionCard(
                            planType: .monthly,
                            isSelected: receiptService.selectedPlan == .monthly,
                            onTap: {
                                receiptService.selectedPlan = .monthly
                            }
                        )
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 3)
                    
                    Spacer()
                        .frame(height: 18)
                    
                    // Texte informatif - AU-DESSUS DU BOUTON
                    HStack(spacing: 5) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.black)
                        
                        Text(receiptService.selectedPlan == .monthly ? 
                                             "no_payment_required_now".localized :
                "no_commitment_cancel_anytime".localized)
                            .font(.system(size: 14))
                            .foregroundColor(.black)
                    }
                    .padding(.bottom, 12)
                    
                    // Bouton d'achat
                    Button(action: {
                        purchaseSubscription()
                    }) {
                        HStack {
                            if receiptService.isLoading {
                                HStack(spacing: 8) {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .scaleEffect(0.8)
                                    Text("loading_caps".localized)
                                }
                            } else {
                                Text("start_trial".localized)
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
                    
                    Spacer()
                        .frame(height: 12)
                    
                    // Liens légaux et restaurer tout en bas de l'écran (design moderne)
                    HStack(spacing: 15) {
                        Button("terms".localized) {
                            if let url = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/") {
                                UIApplication.shared.open(url)
                            }
                        }
                        .font(.system(size: 12))
                        .foregroundColor(.black.opacity(0.5))
                        
                        Button("privacy_policy".localized) {
                            if let url = URL(string: "https://love2lovesite.onrender.com") {
                                UIApplication.shared.open(url)
                            }
                        }
                        .font(.system(size: 12))
                        .foregroundColor(.black.opacity(0.5))
                        
                        Button("restore".localized) {
                            print("🔥 SubscriptionView: Tentative de restauration des achats")
                            receiptService.restorePurchases()
                        }
                        .font(.system(size: 12))
                        .foregroundColor(.black.opacity(0.5))
                    }
                    .padding(.bottom, 5)
                }
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

// Composant pour les cartes de sélection de plan - VERSION MODIFIÉE
struct PlanSelectionCard: View {
    let planType: SubscriptionPlanType
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 15) {
                // Contenu du plan
                VStack(alignment: .leading, spacing: 5) {
                    HStack {
                        Text(planType.displayName)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.black)
                        
                        Spacer()
                    }
                    
                    HStack(spacing: 0) {
                        Text("\(planType.price) / \(planType.period)")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.black)
                        Text("for_2_users".localized)
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(.black)
                    }
                    
                                            Text("\(planType.pricePerUser) " + "per_user_per".localized + " \(planType.period)")
                        .font(.system(size: 12))
                        .foregroundColor(.black.opacity(0.7))
                }
                
                Spacer()
                
                // Coche de sélection à droite
                ZStack {
                    Circle()
                        .stroke(Color.black, lineWidth: 2)
                        .frame(width: 24, height: 24)
                    
                    if isSelected {
                        Circle()
                            .fill(Color.black)
                            .frame(width: 24, height: 24)
                        
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(isSelected ? Color.black : Color.black.opacity(0.3), lineWidth: isSelected ? 2 : 1)
                    )
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// Composant PremiumFeatureRow - pour compatibilité si utilisé ailleurs
struct PremiumFeatureRow: View {
    let title: String
    let subtitle: String
    
    var body: some View {
        NewFeatureRow(title: title, subtitle: subtitle)
    }
}

// Composant NewFeatureRow - design moderne
struct NewFeatureRow: View {
    let title: String
    let subtitle: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 19, weight: .bold))
                .foregroundColor(.black)
                .multilineTextAlignment(.leading)
            
            Text(subtitle)
                .font(.system(size: 15))
                .foregroundColor(.black.opacity(0.7))
                .multilineTextAlignment(.leading)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
} 