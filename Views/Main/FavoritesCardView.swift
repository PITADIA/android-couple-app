import SwiftUI

struct FavoritesCardView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var favoritesService: FavoritesService
    @EnvironmentObject private var appState: AppState
    
    @State private var currentIndex = 0
    @State private var dragOffset = CGSize.zero
    @State private var showingListView = false
    @State private var showingDeleteAlert = false
    
    private var currentFavoriteIndex: Int {
        return currentIndex
    }
    
    // Favoris visibles (3 maximum pour la performance)
    private var visibleFavorites: [(Int, FavoriteQuestion)] {
        guard !favoritesService.favoriteQuestions.isEmpty else { return [] }
        
        let startIndex = max(0, currentIndex - 1)
        let endIndex = min(favoritesService.favoriteQuestions.count - 1, currentIndex + 1)
        
        var result: [(Int, FavoriteQuestion)] = []
        for i in startIndex...endIndex {
            result.append((i, favoritesService.favoriteQuestions[i]))
        }
        return result
    }
    
    var body: some View {
        ZStack {
            // Fond gris clair identique aux autres pages
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header avec navigation
                HStack {
                    Spacer()
                    
                    // Compteur de favoris
                    Text("\(currentFavoriteIndex + 1) sur \(favoritesService.favoriteQuestions.count)")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.black)
                    
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 60)
                .padding(.bottom, 40)
                
                // Zone des cartes
                if favoritesService.favoriteQuestions.isEmpty {
                    // État vide
                    VStack(spacing: 30) {
                        Spacer()
                        
                        Text("❤️")
                            .font(.system(size: 80))
                        
                        Text("Ajoutez des questions en favoris\nen appuyant sur le coeur en-dessous des cartes \npuis vous les verrez apparaître ici")
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                        
                        Spacer()
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    // Cartes de favoris avec le même design
                    GeometryReader { geometry in
                        let cardWidth = geometry.size.width - 40
                        let cardSpacing: CGFloat = 30
                        
                        ZStack {
                            ForEach(visibleFavorites, id: \.0) { indexAndFavorite in
                                let (index, favorite) = indexAndFavorite
                                let offset = CGFloat(index - currentFavoriteIndex)
                                let xPosition = offset * (cardWidth + cardSpacing) + dragOffset.width
                                
                                FavoriteQuestionCardView(
                                    favorite: favorite,
                                    isBackground: index != currentFavoriteIndex
                                )
                                .frame(width: cardWidth)
                                .offset(x: xPosition)
                                .scaleEffect(index == currentFavoriteIndex ? 1.0 : 0.95)
                                .opacity(index == currentFavoriteIndex ? 1.0 : 0.8)
                            }
                        }
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
                        .gesture(
                            DragGesture()
                                .onChanged { value in
                                    dragOffset = value.translation
                                }
                                .onEnded { value in
                                    let threshold: CGFloat = 80
                                    let velocity = value.predictedEndTranslation.width - value.translation.width
                                    
                                    withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) {
                                        if value.translation.width > threshold || velocity > 500 {
                                            // Swipe vers la droite - favori précédent
                                            if currentFavoriteIndex > 0 {
                                                currentIndex -= 1
                                            }
                                        } else if value.translation.width < -threshold || velocity < -500 {
                                            // Swipe vers la gauche - favori suivant
                                            if currentFavoriteIndex < favoritesService.favoriteQuestions.count - 1 {
                                                currentIndex += 1
                                            }
                                        }
                                        
                                        // Remettre la carte en place
                                        dragOffset = .zero
                                    }
                                }
                        )
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.horizontal, 20)
                }
                
                // Bouton Retirer des favoris (design moderne)
                if !favoritesService.favoriteQuestions.isEmpty {
                    Button(action: {
                        showingDeleteAlert = true
                    }) {
                        Text("Retirer des favoris")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                        .frame(width: UIScreen.main.bounds.width - 40) // Même largeur que les cartes
                        .frame(height: 56)
                        .background(
                            // Même couleur que le header des cartes
                            Color(red: 1.0, green: 0.4, blue: 0.6)
                        )
                        .cornerRadius(28)
                    }
                    .padding(.top, 40) // Ajout d'espace au-dessus du bouton
                    .padding(.bottom, 50)
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showingListView) {
            FavoritesView()
                .environmentObject(appState)
                .environmentObject(favoritesService)
        }
        .alert("Supprimer des favoris", isPresented: $showingDeleteAlert) {
            Button("Annuler", role: .cancel) { }
            Button("Supprimer", role: .destructive) {
                if currentFavoriteIndex < favoritesService.favoriteQuestions.count {
                    let currentFavorite = favoritesService.favoriteQuestions[currentFavoriteIndex]
                    Task { @MainActor in
                        favoritesService.removeFavorite(questionId: currentFavorite.questionId)
                        
                        // Ajuster l'index si nécessaire
                        if currentIndex >= favoritesService.favoriteQuestions.count && currentIndex > 0 {
                            currentIndex -= 1
                        }
                        
                        print("🔥 FavoritesCardView: Favori supprimé: \(currentFavorite.questionText.prefix(50))...")
                    }
                }
            }
        } message: {
            Text("Voulez-vous supprimer cette question de vos favoris ?")
        }
        .onAppear {
            print("🔥 FavoritesCardView: Vue des cartes favoris apparue")
            Task { @MainActor in
                favoritesService.loadFavorites()
            }
        }
    }
}

struct FavoriteQuestionCardView: View {
    let favorite: FavoriteQuestion
    let isBackground: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            // Header de la carte avec nom de catégorie (design moderne)
            VStack(spacing: 8) {
                Text(favorite.categoryTitle)
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
            
            // Corps de la carte avec la question favorite
            VStack(spacing: 30) {
                Spacer()
                
                Text(favorite.questionText)
                    .font(.system(size: 22, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
                    .padding(.horizontal, 30)
                
                Spacer()
                
                // Logo/Branding en bas (design moderne)
                HStack(spacing: 8) {
                    Image("leetchi2")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 24, height: 24)
                    
                    Text("Love2Love")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white.opacity(0.9))
                }
                .padding(.bottom, 30)
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
        .frame(height: 500)
        .cornerRadius(20)
        .shadow(color: .black.opacity(isBackground ? 0.1 : 0.3), radius: isBackground ? 5 : 10, x: 0, y: isBackground ? 2 : 5)
    }
}

struct FavoritesCardView_Previews: PreviewProvider {
    static var previews: some View {
        FavoritesCardView()
            .environmentObject(FavoritesService())
    }
} 