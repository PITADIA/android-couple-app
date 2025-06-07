import SwiftUI

struct RelationshipDurationStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre la barre de progression et le titre
            Spacer()
                .frame(height: 60)
            
            // Contenu en haut
            VStack(spacing: 40) {
                // Titre
                Text("Depuis combien de temps êtes-vous ensemble ?")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
                
                // Options de sélection
                VStack(spacing: 15) {
                    ForEach(User.RelationshipDuration.allCases.filter { $0 != .none }, id: \.self) { duration in
                        Button(action: {
                            viewModel.relationshipDuration = duration
                        }) {
                            HStack {
                                // Icône selon la durée
                                Group {
                                    switch duration {
                                    case .lessThanYear:
                                        Text("🫶") // Nouvelle relation qui pousse
                                    case .oneToThreeYears:
                                        Text("💕") // Amour qui se développe
                                    case .moreThanThreeYears:
                                        Text("❤️") // Relation stable et établie
                                    case .notInRelationship:
                                        Text("🙏") // À la recherche de l'amour
                                    case .none:
                                        Text("")
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
            }
            
            Spacer()
            
            // Bouton Continuer collé en bas
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
                    .opacity(viewModel.relationshipDuration == .none ? 0.5 : 1.0)
            }
            .disabled(viewModel.relationshipDuration == .none)
            .padding(.horizontal, 30)
            .padding(.bottom, 50)
        }
    }
} 