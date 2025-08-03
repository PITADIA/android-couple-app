import SwiftUI

struct SavedChallengesView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var savedChallengesService = SavedChallengesService.shared
    @Environment(\.dismiss) private var dismiss
    @State private var currentIndex = 0
    @State private var showingDeleteAlert = false
    @State private var challengeToDelete: SavedChallenge?
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à la vue favoris
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header avec titre
                    HStack {
                        Button(action: {
                            dismiss()
                        }) {
                            Image(systemName: "xmark")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundColor(.black)
                        }
                        
                        Spacer()
                        
                        Text("saved_challenges_title".localized(tableName: "DailyChallenges"))
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.black)
                        
                        Spacer()
                        
                        // Placeholder pour équilibrer le header
                        Spacer()
                            .frame(width: 18)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 8)
                    
                    // Contenu principal
                    if savedChallengesService.isLoading {
                        // État de chargement
                        VStack(spacing: 20) {
                            Spacer()
                            
                            ProgressView()
                                .scaleEffect(1.2)
                            
                            Text("Chargement de vos défis sauvegardés...")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            
                            Spacer()
                        }
                    } else if savedChallengesService.savedChallenges.isEmpty {
                        // État vide (identique aux favoris)
                        VStack(spacing: 20) {
                            Spacer()
                            
                            Image(systemName: "bookmark")
                                .font(.system(size: 60))
                                .foregroundColor(.gray)
                            
                            Text("Aucun défi sauvegardé")
                                .font(.title2)
                                .fontWeight(.semibold)
                            
                            Text("Sauvegardez vos défis préférés pour les retrouver facilement ici !")
                                .font(.body)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal)
                            
                            Spacer()
                        }
                        .padding()
                    } else {
                        // Vue avec cartes (comme FavoritesCardView)
                        savedChallengesCardView
                    }
                }
            }
            .navigationBarHidden(true)
        }
        .onAppear {
            print("🎯 SavedChallengesView: onAppear - Configuration du service")
            savedChallengesService.configure(with: appState)
            print("🎯 SavedChallengesView: Nombre de défis actuels: \(savedChallengesService.savedChallenges.count)")
        }
        .alert("Supprimer ce défi ?", isPresented: $showingDeleteAlert, presenting: challengeToDelete) { challenge in
            Button("Supprimer", role: .destructive) {
                print("🗑️ SavedChallengesView: Tentative de suppression du défi: \(challenge.challengeKey)")
                savedChallengesService.deleteChallenge(challenge)
                
                // Ajuster l'index si nécessaire
                let updatedChallenges = savedChallengesService.savedChallenges
                if currentIndex >= updatedChallenges.count && currentIndex > 0 {
                    currentIndex = updatedChallenges.count - 1
                }
            }
            Button("Annuler", role: .cancel) { }
        } message: { challenge in
            Text("Cette action est irréversible.")
        }
    }
    
    // MARK: - Card View (identique à FavoritesCardView)
    
    private var savedChallengesCardView: some View {
        VStack(spacing: 0) {
            if !savedChallengesService.savedChallenges.isEmpty {
                let currentChallenge = savedChallengesService.savedChallenges[currentIndex]
                
                VStack(spacing: 40) {
                    Spacer()
                    
                    // Carte du défi (style identique aux favoris)
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
                            
                            Text(currentChallenge.localizedText)
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
                    .frame(maxWidth: .infinity, minHeight: 300)
                    .background(Color.clear)
                    .cornerRadius(20)
                    .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
                    .padding(.horizontal, 20)
                    .gesture(
                        DragGesture()
                            .onEnded { value in
                                handleSwipeGesture(value)
                            }
                    )
                    
                    Spacer()
                    
                    // Bouton Supprimer (design moderne identique aux favoris)
                    Button("delete_challenge_button".localized(tableName: "DailyChallenges")) {
                        challengeToDelete = currentChallenge
                        showingDeleteAlert = true
                    }
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: UIScreen.main.bounds.width - 40) // Même largeur que les cartes
                    .frame(height: 56)
                    .background(Color(red: 1.0, green: 0.4, blue: 0.6)) // Même couleur que le header des cartes
                    .cornerRadius(28)
                    .padding(.top, 10)
                    .padding(.bottom, 30)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 20)
    }
    
    // MARK: - Swipe Gesture
    
    private func handleSwipeGesture(_ value: DragGesture.Value) {
        let threshold: CGFloat = 50
        
        if value.translation.width > threshold && currentIndex > 0 {
            // Swipe vers la droite - défi précédent
            withAnimation(.easeInOut(duration: 0.3)) {
                currentIndex -= 1
            }
        } else if value.translation.width < -threshold && currentIndex < savedChallengesService.savedChallenges.count - 1 {
            // Swipe vers la gauche - défi suivant
            withAnimation(.easeInOut(duration: 0.3)) {
                currentIndex += 1
            }
        }
    }
}

// MARK: - Preview

#Preview {
    SavedChallengesView()
        .environmentObject(AppState())
}