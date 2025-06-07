import SwiftUI
import ConfettiSwiftUI

struct CompletionStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var confettiCounter = 0
    
    var body: some View {
        ZStack {
            VStack(spacing: 40) {
                Spacer()
                
                // Icône de validation avec animation
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.2))
                        .frame(width: 80, height: 80)
                    
                    Image(systemName: "checkmark")
                        .font(.system(size: 40, weight: .bold))
                        .foregroundColor(.white)
                }
                .scaleEffect(confettiCounter > 0 ? 1.2 : 1.0)
                .animation(.easeInOut(duration: 0.6), value: confettiCounter)
                
                // Titre principal
                Text("Tout est terminé")
                    .font(.system(size: 24, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                // Titre principal
                VStack(spacing: 10) {
                    Text("Merci de nous")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    Text("faire confiance")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                }
                
                // Sous-titre avec origine des confettis
                ZStack {
                    Text("Nous promettons de toujours garder vos informations personnelles privées et sécurisées")
                        .font(.system(size: 16))
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                        .padding(.horizontal, 40)
                    
                    // Point invisible pour les confettis au niveau du sous-titre
                    Rectangle()
                        .fill(Color.clear)
                        .frame(width: 1, height: 1)
                        .confettiCannon(trigger: $confettiCounter, num: 200, radius: 400)
                }
                
                Spacer()
                
                // Bouton continuer
                Button(action: {
                    viewModel.nextStep()
                }) {
                    Text("Continuer")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color(hex: "#FD267A"))
                        .cornerRadius(28)
                }
                .padding(.horizontal, 30)
                .padding(.bottom, 50)
            }
        }
        .onAppear {
            print("🔥 CompletionStepView: Vue de confirmation apparue")
            // Déclencher l'animation des confettis immédiatement
            confettiCounter += 1
        }
    }
} 