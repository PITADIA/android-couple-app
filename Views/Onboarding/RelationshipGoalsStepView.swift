import SwiftUI

struct RelationshipGoalsStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 40) {
            // Titre
            VStack(spacing: 10) {
                Text("QU'ATTENDS-TU DE PLUS")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("DANS TA RELATION ?")
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
                ForEach(viewModel.relationshipGoals, id: \.self) { goal in
                    Button(action: {
                        viewModel.toggleGoal(goal)
                    }) {
                        HStack {
                            Text(goal)
                                .font(.system(size: 16))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.leading)
                            
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 16)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(viewModel.selectedGoals.contains(goal) ? Color.white.opacity(0.3) : Color.white.opacity(0.1))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.white.opacity(0.3), lineWidth: 1)
                                )
                        )
                    }
                }
            }
            .padding(.horizontal, 30)
            
            // Message de confidentialité
            VStack(spacing: 15) {
                Text("🔥")
                    .font(.system(size: 40))
                
                Text("Tes réponses sont pour toi seul,\net elles resteront complètement confidentielles\npour personnaliser ton expérience.")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.8))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }
            
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
                    .opacity(viewModel.selectedGoals.isEmpty ? 0.5 : 1.0)
            }
            .disabled(viewModel.selectedGoals.isEmpty)
            .padding(.horizontal, 30)
        }
    }
} 