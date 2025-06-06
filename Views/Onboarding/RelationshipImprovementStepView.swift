import SwiftUI

struct RelationshipImprovementStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre la barre de progression et le titre
            Spacer()
                .frame(height: 60)
            
            // Contenu en haut
            VStack(spacing: 40) {
                // Titre
                Text("Si cette application t'aidait à améliorer un seul aspect de ta relation, ce serait lequel ?")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
                
                // Options de sélection
                VStack(spacing: 15) {
                    ForEach(viewModel.relationshipImprovements, id: \.self) { improvement in
                        Button(action: {
                            print("🔥 RelationshipImprovementStepView: Amélioration sélectionnée: \(improvement)")
                            viewModel.relationshipImprovement = improvement
                        }) {
                            HStack {
                                Text(improvement)
                                    .font(.system(size: 16))
                                    .foregroundColor(.white)
                                    .multilineTextAlignment(.leading)
                                
                                Spacer()
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 16)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(viewModel.relationshipImprovement == improvement ? Color.white.opacity(0.3) : Color.white.opacity(0.1))
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
                print("🔥 RelationshipImprovementStepView: Bouton Continuer pressé")
                if !viewModel.relationshipImprovement.isEmpty {
                    print("🔥 RelationshipImprovementStepView: Amélioration valide, passage à l'étape suivante")
                    viewModel.nextStep()
                } else {
                    print("❌ RelationshipImprovementStepView: Aucune amélioration sélectionnée")
                }
            }) {
                Text("Continuer")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(hex: "#FD267A"))
                    .cornerRadius(28)
                    .opacity(viewModel.relationshipImprovement.isEmpty ? 0.5 : 1.0)
            }
            .disabled(viewModel.relationshipImprovement.isEmpty)
            .padding(.horizontal, 30)
            .padding(.bottom, 50)
        }
        .onAppear {
            print("🔥 RelationshipImprovementStepView: Vue d'amélioration de la relation apparue")
        }
    }
}

#Preview {
    RelationshipImprovementStepView(viewModel: OnboardingViewModel())
} 