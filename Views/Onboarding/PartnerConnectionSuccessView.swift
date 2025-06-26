import SwiftUI

struct PartnerConnectionSuccessView: View {
    let partnerName: String
    let onContinue: () -> Void
    @State private var showAnimation = false
    
    var body: some View {
        ZStack {
            // Fond gradient purple/rose comme dans l'image
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.2, green: 0.1, blue: 0.3),
                    Color(red: 0.3, green: 0.1, blue: 0.4)
                ]),
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            VStack(spacing: 40) {
                Spacer()
                
                // Animation de connexion réussie
                VStack(spacing: 20) {
                    // Icône de succès animée
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
                        
                        Image(systemName: "heart.fill")
                            .font(.system(size: 50))
                            .foregroundColor(.white)
                            .scaleEffect(showAnimation ? 1.1 : 1.0)
                            .animation(
                                Animation.easeInOut(duration: 1.5)
                                    .repeatForever(autoreverses: true),
                                value: showAnimation
                            )
                    }
                    
                    // Titre principal
                    Text("Tu es connecté !")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .opacity(showAnimation ? 1.0 : 0.0)
                        .animation(.easeInOut(duration: 1.0).delay(0.5), value: showAnimation)
                    
                    // Message de félicitations avec nom du partenaire
                    Text("Félicitations, tu as réussi à te connecter avec \(partnerName).")
                        .font(.system(size: 18))
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                        .opacity(showAnimation ? 1.0 : 0.0)
                        .animation(.easeInOut(duration: 1.0).delay(1.0), value: showAnimation)
                }
                
                Spacer()
                
                // Bouton Continuer
                Button(action: {
                    print("🎉 PartnerConnectionSuccessView: Bouton Continuer pressé")
                    onContinue()
                }) {
                    Text("Continue")
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
                .padding(.horizontal, 30)
                .padding(.bottom, 50)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(1.5), value: showAnimation)
            }
        }
        .onAppear {
            print("🎉 PartnerConnectionSuccessView: Vue apparue pour partenaire: \(partnerName)")
            showAnimation = true
        }
    }
} 