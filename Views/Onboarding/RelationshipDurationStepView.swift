import SwiftUI

struct RelationshipDurationStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 40) {
            // Titre
            VStack(spacing: 10) {
                Text("DEPUIS COMBIEN DE TEMPS")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("ÊTES-VOUS ENSEMBLE ?")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 30)
            
            // Message d'avertissement
            HStack {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.yellow)
                Text("Ton partenaire ne verra pas ça")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.8))
            }
            .padding(.horizontal, 30)
            
            // Options de sélection
            VStack(spacing: 15) {
                ForEach(User.RelationshipDuration.allCases, id: \.self) { duration in
                    Button(action: {
                        viewModel.relationshipDuration = duration
                    }) {
                        HStack {
                            // Icône selon la durée
                            Group {
                                switch duration {
                                case .lessThanYear:
                                    Text("✨")
                                case .oneToThreeYears:
                                    Text("🔥")
                                case .moreThanThreeYears:
                                    Text("⚖️")
                                case .notInRelationship:
                                    Text("💭")
                                }
                            }
                            .font(.system(size: 20))
                            
                            Text(duration.rawValue)
                                .font(.system(size: 16))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.leading)
                            
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 16)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(viewModel.relationshipDuration == duration ? Color.white.opacity(0.3) : Color.white.opacity(0.1))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.white.opacity(0.3), lineWidth: 1)
                                )
                        )
                    }
                }
            }
            .padding(.horizontal, 30)
            
            // Bouton Continuer
            Button(action: {
                viewModel.nextStep()
            }) {
                Text("Continuer")
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
            .padding(.horizontal, 30)
        }
    }
} 