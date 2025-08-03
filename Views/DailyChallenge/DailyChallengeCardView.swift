import SwiftUI

struct DailyChallengeCardView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var savedChallengesService = SavedChallengesService.shared
    
    let challenge: DailyChallenge
    let showDeleteButton: Bool
    let onCompleted: (() -> Void)?
    let onSave: (() -> Void)?
    let onDelete: (() -> Void)?
    
    @State private var isCompleted: Bool = false
    @State private var showSaveConfirmation: Bool = false
    @State private var isAlreadySaved: Bool = false
    
    init(challenge: DailyChallenge, 
         showDeleteButton: Bool = false, 
         onCompleted: (() -> Void)? = nil, 
         onSave: (() -> Void)? = nil, 
         onDelete: (() -> Void)? = nil) {
        self.challenge = challenge
        self.showDeleteButton = showDeleteButton
        self.onCompleted = onCompleted
        self.onSave = onSave
        self.onDelete = onDelete
        self._isCompleted = State(initialValue: challenge.isCompleted)
    }
    
    var body: some View {
        VStack(spacing: 20) {
            // 🎯 Carte magnifique - EXACTEMENT comme FavoriteQuestionCardView
            VStack(spacing: 0) {
                // Header de la carte avec titre (design rose identique aux favoris)
                VStack(spacing: 8) {
                    Text("Love2Love")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
                .background(
                    LinearGradient(
                        gradient: Gradient(colors: [
                            Color(red: 1.0, green: 0.4, blue: 0.6),
                            Color(red: 1.0, green: 0.6, blue: 0.8)
                        ]),
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                
                // Corps de la carte avec le défi
                VStack(spacing: 30) {
                    Spacer()
                    
                    Text(challenge.localizedText)
                        .font(.system(size: 22, weight: .medium))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineSpacing(6)
                        .padding(.horizontal, 30)
                    
                    Spacer(minLength: 20)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(
                    LinearGradient(
                        gradient: Gradient(colors: [
                            Color(red: 0.2, green: 0.1, blue: 0.15),
                            Color(red: 0.4, green: 0.2, blue: 0.3),
                            Color(red: 0.6, green: 0.3, blue: 0.2)
                        ]),
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            }
            .frame(maxWidth: .infinity)
            .frame(height: 400)
            .cornerRadius(20)
            .shadow(color: .black.opacity(0.3), radius: 10, x: 0, y: 5)
            
            // 🎯 Boutons magnifiques - Style identique aux favoris
            if showDeleteButton {
                // Bouton supprimer (style rouge pour danger)
                Button("delete_challenge_button".localized(tableName: "DailyChallenges")) {
                    onDelete?()
                }
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(Color.red)
                .cornerRadius(28)
            } else {
                // Boutons pour défi du jour (style favoris rose)
                VStack(spacing: 16) {
                    // Bouton "Défi complété"
                    Button(action: {
                        handleChallengeCompleted()
                    }) {
                        HStack {
                            Spacer()
                            
                            Text("challenge_completed_button".localized(tableName: "DailyChallenges"))
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                            
                            Image(systemName: isCompleted ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 18))
                                .foregroundColor(.white)
                                .padding(.leading, 8)
                            
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                    }
                    .frame(maxWidth: .infinity)
                    .background(Color(red: 1.0, green: 0.4, blue: 0.6))
                    .cornerRadius(28)
                    
                    // Bouton "Sauvegarder le défi"
                    Button(action: {
                        handleChallengeSave()
                    }) {
                        HStack {
                            Spacer()
                            
                            Text("save_challenge_button".localized(tableName: "DailyChallenges"))
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                            
                            if showSaveConfirmation {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white)
                                    .padding(.leading, 8)
                                    .scaleEffect(showSaveConfirmation ? 1.2 : 1.0)
                                    .animation(.spring(response: 0.5), value: showSaveConfirmation)
                            } else {
                                Image(systemName: isAlreadySaved ? "bookmark.fill" : "bookmark")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white)
                                    .padding(.leading, 8)
                            }
                            
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                    }
                    .frame(maxWidth: .infinity)
                    .background(Color(red: 1.0, green: 0.4, blue: 0.6))
                    .cornerRadius(28)
                }
            }
        }
        .onAppear {
            // Vérifier si le défi est déjà sauvegardé
            checkIfAlreadySaved()
        }
        .onChange(of: savedChallengesService.savedChallenges) { _ in
            // Re-vérifier quand la liste change
            checkIfAlreadySaved()
        }
    }
    
    // MARK: - Actions
    
    private func handleChallengeCompleted() {
        let newCompletionState = !isCompleted
        
        withAnimation(.spring(response: 0.6)) {
            isCompleted = newCompletionState
        }
        
        // Persister l'état dans Firebase et cache
        if newCompletionState {
            DailyChallengeService.shared.markChallengeAsCompleted(challenge)
        } else {
            DailyChallengeService.shared.markChallengeAsNotCompleted(challenge)
        }
        
        onCompleted?()
    }
    
    private func handleChallengeSave() {
        if !isAlreadySaved {
            onSave?()
            showSaveConfirmation = true
            isAlreadySaved = true
            
            // Reset après 3 secondes
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                showSaveConfirmation = false
            }
        }
    }
    
    private func checkIfAlreadySaved() {
        isAlreadySaved = savedChallengesService.isChallengeAlreadySaved(challenge)
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 20) {
        DailyChallengeCardView(
            challenge: DailyChallenge(
                challengeKey: "daily_challenge_1",
                challengeDay: 1,
                scheduledDate: Date(),
                coupleId: "test_couple"
            ),
            showDeleteButton: false,
            onCompleted: {
                print("Challenge completed")
            },
            onSave: {
                print("Challenge saved")
            }
        )
        .environmentObject(AppState())
        
        DailyChallengeCardView(
            challenge: DailyChallenge(
                challengeKey: "daily_challenge_2",
                challengeDay: 2,
                scheduledDate: Date(),
                coupleId: "test_couple",
                isCompleted: true
            ),
            showDeleteButton: true,
            onDelete: {
                print("Challenge deleted")
            }
        )
        .environmentObject(AppState())
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}