import SwiftUI

struct RelationshipGoalsStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("relationship_goals_question".localized)
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Premier Spacer pour centrer le contenu
            Spacer()
            
            // Contenu principal centré
            VStack(spacing: 30) {
                // Options sur cartes blanches
                VStack(spacing: 12) {
                    ForEach(viewModel.relationshipGoals, id: \.self) { goal in
                        Button(action: {
                            if viewModel.selectedGoals.contains(goal) {
                                viewModel.selectedGoals.removeAll { $0 == goal }
                            } else {
                                viewModel.selectedGoals.append(goal)
                            }
                        }) {
                            Text(goal)
                                .font(.system(size: 16))
                                .foregroundColor(viewModel.selectedGoals.contains(goal) ? .white : .black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .padding(.horizontal, 20)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(viewModel.selectedGoals.contains(goal) ? Color(hex: "#FD267A") : Color.white)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(
                                                    viewModel.selectedGoals.contains(goal) ? Color(hex: "#FD267A") : Color.black.opacity(0.1),
                                                    lineWidth: viewModel.selectedGoals.contains(goal) ? 2 : 1
                                                )
                                        )
                                        .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 4)
                                )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 30)
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
                        .opacity(viewModel.selectedGoals.isEmpty ? 0.5 : 1.0)
                }
                .disabled(viewModel.selectedGoals.isEmpty)
                .padding(.horizontal, 30)
            }
            .padding(.vertical, 30)
            .background(Color.white)
        }
    }
} 