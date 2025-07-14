import SwiftUI
import ConfettiSwiftUI

struct CompletionStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var confettiCounter = 0
    
    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // Espace entre barre de progression et titre (harmonisé)
                Spacer()
                    .frame(height: 40)
                
                // Premier Spacer pour centrer le contenu
                Spacer()
                
                // Contenu principal centré
                VStack(spacing: 10) {
                    // Petit titre "Tout est terminé" avec icône
                    HStack(spacing: 8) {
                        // Icône de validation verte
                        ZStack {
                            Circle()
                                .fill(Color.green)
                                .frame(width: 20, height: 20)
                            
                            Image(systemName: "checkmark")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                        }
                        
                        Text("all_completed".localized)
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.gray)
                    }
                    
                    // Grand titre "Merci de nous faire confiance."
                    VStack(spacing: 2) {
                        Text("thank_you_for".localized)
                            .font(.system(size: 48, weight: .bold))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)
                        
                        Text("trusting_us".localized)
                            .font(.system(size: 48, weight: .bold))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 10)
                    
                    Spacer()
                        .frame(height: 15)
                    
                    // Sous-titre gris
                    Text("privacy_promise".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                        .padding(.horizontal, 40)
                }
                
                // Deuxième Spacer pour pousser la zone bouton vers le bas
                Spacer()
                    
                // Zone blanche collée en bas
                VStack(spacing: 0) {
                    Button(action: {
                        viewModel.nextStep()
                    }) {
                        Text("continue".localized)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(Color(hex: "#FD267A"))
                            .cornerRadius(28)
                    }
                    .padding(.horizontal, 30)
                }
                .padding(.vertical, 30)
                .background(Color.white)
            }
            

        }
        .confettiCannon(trigger: $confettiCounter, num: 50, openingAngle: Angle(degrees: 0), closingAngle: Angle(degrees: 360), radius: 200)
        .onAppear {
            print("🔥 CompletionStepView: Vue de completion apparue")
            // Déclencher l'explosion de confettis immédiatement sans délai
            confettiCounter += 1
        }
    }
} 