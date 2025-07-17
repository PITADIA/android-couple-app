import SwiftUI
import StoreKit

struct SubscriptionStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @StateObject private var receiptService = AppleReceiptService.shared
    @StateObject private var pricingService = StoreKitPricingService.shared
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack {
            // Fond gris clair identique aux autres pages d'onboarding
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header avec croix collée en haut
                HStack {
                    Button(action: {
                        print("🔥 SubscriptionStepView: Fermeture via croix - continuer sans premium")
                        NSLog("🔥 SubscriptionStepView: Fermeture via croix - continuer sans premium")
                        viewModel.skipSubscription()
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
                        title: "feature_love_stronger".localized,
                        subtitle: "feature_love_stronger_description".localized
                    )

                    NewFeatureRow(
                        title: "feature_memory_chest".localized,
                        subtitle: "feature_memory_chest_description".localized
                    )

                    NewFeatureRow(
                        title: "feature_love_map".localized,
                        subtitle: "feature_love_map_description".localized
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
                    
                    // Bouton Commencer l'essai
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
                                Text(receiptService.selectedPlan == .weekly ? "continue".localized.uppercased() : "start_trial".localized)
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
                    
                    // Section liens légaux - collée en bas
                    HStack(spacing: 15) {
                        Button("terms".localized) {
                            if let url = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/") {
                                UIApplication.shared.open(url)
                            }
                        }
                        .font(.system(size: 12))
                        .foregroundColor(.black.opacity(0.5))
                        
                        Button("privacy_policy".localized) {
                            let privacyUrl = Locale.preferredLanguages.first?.hasPrefix("fr") == true 
                                ? "https://love2lovesite.onrender.com"
                                : "https://love2lovesite.onrender.com/privacy-policy.html"
                            
                            if let url = URL(string: privacyUrl) {
                                UIApplication.shared.open(url)
                            }
                        }
                        .font(.system(size: 12))
                        .foregroundColor(.black.opacity(0.5))
                        
                        Button("restore".localized) {
                            print("🔥 SubscriptionStepView: Tentative de restauration des achats")
                            NSLog("🔥 SubscriptionStepView: Tentative de restauration des achats")
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
        .onAppear {
            print("🔥 SubscriptionStepView: Vue apparue")
            NSLog("🔥 SubscriptionStepView: Vue apparue")
            
            // Force le rechargement des prix si ils ne sont pas disponibles
            if !pricingService.hasDynamicPrices {
                print("💰 SubscriptionStepView: Rechargement des prix StoreKit...")
                pricingService.refreshPrices()
            }
            
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

    }
    
    private func purchaseSubscription() {
        print("🔥 SubscriptionStepView: Tentative d'achat d'abonnement avec AppleReceiptService")
        NSLog("🔥 SubscriptionStepView: Tentative d'achat d'abonnement avec AppleReceiptService")
        
        receiptService.purchaseSubscription()
    }
}

// NewFeatureRow est défini dans SubscriptionView.swift pour éviter la duplication

// Ancien composant conservé pour compatibilité si nécessaire
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