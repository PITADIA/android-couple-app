import SwiftUI

struct SubscriptionRevokedView: View {
    let partnerName: String
    let onContinue: () -> Void
    @State private var showAnimation = false
    
    var body: some View {
        ZStack {
            // Fond gradient rouge/orange pour indiquer la perte
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.8, green: 0.2, blue: 0.2),
                    Color(red: 0.9, green: 0.4, blue: 0.2)
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 40) {
                Spacer()
                
                // Animation de perte d'accès
                VStack(spacing: 20) {
                    // Icône de cadenas fermé
                    ZStack {
                        Circle()
                            .fill(Color.white.opacity(0.2))
                            .frame(width: 120, height: 120)
                            .scaleEffect(showAnimation ? 1.1 : 1.0)
                            .opacity(showAnimation ? 0.3 : 0.8)
                            .animation(
                                Animation.easeInOut(duration: 2.0)
                                    .repeatForever(autoreverses: true),
                                value: showAnimation
                            )
                        
                        Image(systemName: "lock.fill")
                            .font(.system(size: 50))
                            .foregroundColor(.white)
                            .scaleEffect(showAnimation ? 1.1 : 1.0)
                            .animation(
                                Animation.easeInOut(duration: 1.5)
                                    .repeatForever(autoreverses: true),
                                value: showAnimation
                            )
                    }
                    
                    // Titre de révocation
                    Text("Accès Premium perdu")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .opacity(showAnimation ? 1.0 : 0.0)
                        .animation(.easeInOut(duration: 1.0).delay(0.5), value: showAnimation)
                    
                    // Message d'explication avec nom du partenaire
                    Text("\(partnerName) a résilié son abonnement Premium.\n\nTu as maintenant accès aux fonctionnalités gratuites uniquement.")
                        .font(.system(size: 18))
                        .foregroundColor(.white.opacity(0.95))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                        .opacity(showAnimation ? 1.0 : 0.0)
                        .animation(.easeInOut(duration: 1.0).delay(1.0), value: showAnimation)
                    
                    // Liste des limitations
                    VStack(spacing: 12) {
                        limitationRow(icon: "🔒", text: "Catégories premium verrouillées")
                        limitationRow(icon: "📊", text: "64 questions max (catégorie gratuite)")
                        limitationRow(icon: "💡", text: "Contenu premium non accessible")
                    }
                    .opacity(showAnimation ? 1.0 : 0.0)
                    .animation(.easeInOut(duration: 1.0).delay(1.5), value: showAnimation)
                }
                
                Spacer()
                
                // Boutons d'action
                VStack(spacing: 15) {
                    // Bouton pour s'abonner
                    Button(action: {
                        print("🔒 SubscriptionRevokedView: Bouton S'abonner pressé")
                        // Ici on pourrait ouvrir la page d'abonnement
                        onContinue()
                    }) {
                        Text("Obtenir Premium")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 18)
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
                            .cornerRadius(25)
                    }
                    
                    // Bouton pour continuer en gratuit
                    Button(action: {
                        print("🔒 SubscriptionRevokedView: Bouton Continuer gratuit pressé")
                        onContinue()
                    }) {
                        Text("Continuer en version gratuite")
                            .font(.system(size: 16))
                            .foregroundColor(.white.opacity(0.8))
                    }
                }
                .padding(.horizontal, 30)
                .padding(.bottom, 50)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(2.0), value: showAnimation)
            }
        }
        .onAppear {
            print("🔒 SubscriptionRevokedView: Vue apparue pour révocation de: \(partnerName)")
            showAnimation = true
        }
    }
    
    private func limitationRow(icon: String, text: String) -> some View {
        HStack(spacing: 15) {
            Text(icon)
                .font(.system(size: 20))
            
            Text(text)
                .font(.system(size: 16))
                .foregroundColor(.white.opacity(0.9))
            
            Spacer()
        }
        .padding(.horizontal, 40)
    }
} 