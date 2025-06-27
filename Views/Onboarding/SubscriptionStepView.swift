import SwiftUI
import StoreKit

struct SubscriptionStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @StateObject private var receiptService = AppleReceiptService.shared
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack {
            // Fond gris clair identique aux autres pages d'onboarding
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Espace en haut
                Spacer()
                    .frame(height: 80)
                
                // Titre principal
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
                
                // Section prix et bouton collée en bas
                VStack(spacing: 15) {
                    VStack(spacing: 5) {
                        HStack(spacing: 5) {
                            Image(systemName: "checkmark")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.black)
                            
                            Text("Essai Gratuit de 3 jours, puis 4,99 € / semaine")
                                .font(.system(size: 14))
                                .foregroundColor(.black)
                        }
                        
                        Text("Abonnement pour 2 utilisateurs donc 2,50€ / utilisateur / semaine")
                            .font(.system(size: 12))
                            .foregroundColor(.black.opacity(0.7))
                    }
                    .padding(.horizontal, 30)
                    
                    // Bouton principal
                    Button(action: {
                        purchaseSubscription()
                    }) {
                        HStack {
                            if receiptService.isLoading {
                                HStack(spacing: 8) {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .scaleEffect(0.8)
                                    Text("CHARGEMENT...")
                                }
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
                    
                    // Bouton "Continuer sans premium"
                    Button("Continuer sans mon accès Premium") {
                        print("🔥 SubscriptionStepView: Continuer sans premium")
                        NSLog("🔥 SubscriptionStepView: Continuer sans premium")
                        viewModel.skipSubscription()
                    }
                    .font(.system(size: 16))
                    .foregroundColor(.black.opacity(0.6))
                    .padding(.top, 10)
                }
                .padding(.bottom, 20)
                
                // Liens légaux et restaurer tout en bas de l'écran
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
                        print("🔥 SubscriptionStepView: Tentative de restauration des achats")
                        NSLog("🔥 SubscriptionStepView: Tentative de restauration des achats")
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

    }
    
    private func purchaseSubscription() {
        print("🔥 SubscriptionStepView: Tentative d'achat d'abonnement avec AppleReceiptService")
        NSLog("🔥 SubscriptionStepView: Tentative d'achat d'abonnement avec AppleReceiptService")
        
        receiptService.purchaseSubscription()
    }
}

// Nouveau composant pour les fonctionnalités avec titre et sous-titre
struct NewFeatureRow: View {
    let title: String
    let subtitle: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.black)
                .multilineTextAlignment(.leading)
            
            Text(subtitle)
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.7))
                .multilineTextAlignment(.leading)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

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