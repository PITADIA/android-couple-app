import SwiftUI

struct PartnerCodeStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var userCode = "53HAAS"
    
    var body: some View {
        VStack(spacing: 40) {
            // Titre
            VStack(spacing: 10) {
                Text("QUI EST TON PARTENAIRE ?")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 30)
            
            // Description
            Text("Débloque gratuitement le mode daily, les\nwidgets de couple, les stats de couple & plus\nencore.")
                .font(.system(size: 16))
                .foregroundColor(.white.opacity(0.9))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 30)
            
            // Section code utilisateur
            VStack(spacing: 20) {
                VStack(spacing: 15) {
                    Text("Envoie ton code à ton partenaire")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white)
                    
                    // Code utilisateur avec boutons
                    HStack(spacing: 10) {
                        ForEach(Array(userCode.enumerated()), id: \.offset) { index, character in
                            Text(String(character))
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.white)
                                .frame(width: 40, height: 50)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(Color.white.opacity(0.2))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(Color.white.opacity(0.3), lineWidth: 1)
                                        )
                                )
                        }
                        
                        Button(action: {
                            // Action pour copier le code
                            UIPasteboard.general.string = userCode
                        }) {
                            Image(systemName: "doc.on.doc")
                                .font(.system(size: 16))
                                .foregroundColor(.white)
                                .frame(width: 30, height: 30)
                        }
                    }
                    
                    // Bouton partager
                    Button(action: {
                        // Action pour partager le code
                    }) {
                        HStack {
                            Text("Partager Mon Code")
                                .font(.system(size: 16, weight: .medium))
                                .foregroundColor(.white)
                            
                            Image(systemName: "square.and.arrow.up")
                                .font(.system(size: 14))
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 20)
                                .fill(Color.white.opacity(0.2))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 20)
                                        .stroke(Color.white.opacity(0.3), lineWidth: 1)
                                )
                        )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 20)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.white.opacity(0.1))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.white.opacity(0.2), lineWidth: 1)
                        )
                )
            }
            .padding(.horizontal, 30)
            
            // Section code partenaire
            VStack(spacing: 20) {
                Text("Entre le code de ton partenaire")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                
                // Champs pour le code partenaire
                HStack(spacing: 10) {
                    ForEach(0..<6) { index in
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.white.opacity(0.2))
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.white.opacity(0.3), lineWidth: 1)
                            )
                            .frame(width: 40, height: 50)
                            .overlay(
                                Text("-")
                                    .font(.system(size: 24, weight: .bold))
                                    .foregroundColor(.white.opacity(0.5))
                            )
                    }
                }
                
                // Bouton ajouter partenaire
                Button(action: {
                    viewModel.nextStep()
                }) {
                    Text("Ajouter mon partenaire")
                        .font(.system(size: 18, weight: .semibold))
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
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 20)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.1))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.white.opacity(0.2), lineWidth: 1)
                    )
            )
            .padding(.horizontal, 30)
        }
    }
} 