import SwiftUI

struct SubscriptionInheritedView: View {
    let partnerName: String
    let onContinue: () -> Void
    @State private var showAnimation = false
    
    var body: some View {
        ZStack {
            // Fond gradient premium
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(hex: "#FFD700"),
                    Color(hex: "#FFA500"),
                    Color(hex: "#FF8C00")
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 40) {
                Spacer()
                
                // Animation de couronne premium
                VStack(spacing: 20) {
                    // Icône de couronne animée
                    ZStack {
                        Circle()
                            .fill(Color.white.opacity(0.2))
                            .frame(width: 120, height: 120)
                            .scaleEffect(showAnimation ? 1.2 : 1.0)
                            .opacity(showAnimation ? 0.3 : 0.8)
                            .animation(
                                Animation.easeInOut(duration: 2.0)
                                    .repeatForever(autoreverses: true),
                                value: showAnimation
                            )
                        
                        Image(systemName: "crown.fill")
                            .font(.system(size: 50))
                            .foregroundColor(.white)
                            .scaleEffect(showAnimation ? 1.1 : 1.0)
                            .animation(
                                Animation.easeInOut(duration: 1.5)
                                    .repeatForever(autoreverses: true),
                                value: showAnimation
                            )
                    }
                    
                    // Titre premium
                    Text("premium_unlocked".localized)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)
                    
                    // Message avec nom du partenaire
                    Text(String(format: "premium_shared_message".localized, partnerName))
                        .font(.system(size: 18))
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)
                    
                    // Liste des avantages
                    VStack(spacing: 12) {
                                            premiumFeatureRow(icon: "🔓", text: "premium_features_unlocked".localized)
                    premiumFeatureRow(icon: "🔥", text: "unlimited_questions".localized)
                    premiumFeatureRow(icon: "💎", text: "exclusive_premium_content".localized)
                    }
                    .opacity(showAnimation ? 1.0 : 0.0)
                    .animation(.easeInOut(duration: 1.0).delay(1.5), value: showAnimation)
                }
                
                Spacer()
                
                // Bouton Continuer
                Button(action: {
                    print("🎁 SubscriptionInheritedView: Bouton Continuer pressé")
                    onContinue()
                }) {
                    Text("discover_premium".localized)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 18)
                        .background(Color.white)
                        .cornerRadius(25)
                }
                .padding(.horizontal, 30)
                .padding(.bottom, 50)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(2.0), value: showAnimation)
            }
        }
        .onAppear {
            print("🎁 SubscriptionInheritedView: Vue apparue pour héritage de: \(partnerName)")
            showAnimation = true
        }
    }
    
    private func premiumFeatureRow(icon: String, text: String) -> some View {
        HStack(spacing: 15) {
            Text(icon)
                .font(.system(size: 24))
            
            Text(text)
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.white)
            
            Spacer()
        }
        .padding(.horizontal, 40)
    }
} 