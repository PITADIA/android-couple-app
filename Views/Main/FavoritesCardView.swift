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
        let allFavorites = favoritesService.getAllFavorites()
        guard !allFavorites.isEmpty else { return [] }
        
        let startIndex = max(0, currentIndex - 1)
        let endIndex = min(allFavorites.count - 1, currentIndex + 1)
        
        var result: [(Int, FavoriteQuestion)] = []
        for i in startIndex...endIndex {
            result.append((i, allFavorites[i]))
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
                    
                    // Titre
                    VStack(spacing: 4) {
                        Text("Favoris")
                            .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.black)
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 20)
                
                // Contenu principal
                if favoritesService.getAllFavorites().isEmpty {
                    // État vide avec layout identique au journal
                    VStack(spacing: 30) {
                        Image(LocalizationService.localizedImageName(frenchImage: "mili", defaultImage: "manon"))
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 240, height: 240)
                        
                        VStack(spacing: 12) {
                            Text("add_favorite_questions".localized)
                                .font(.system(size: 22, weight: .medium))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                            
                            Text("add_favorites_description".localized)
                                .font(.system(size: 16))
                                .foregroundColor(.black.opacity(0.7))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 30)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.horizontal, 20)
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
                                            if currentFavoriteIndex < favoritesService.getAllFavorites().count - 1 {
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
                    
                    // Bouton Retirer des favoris (design moderne)
                    Button("remove_from_favorites".localized) {
                        showingDeleteAlert = true
                    }
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: UIScreen.main.bounds.width - 40) // Même largeur que les cartes
                    .frame(height: 56)
                    .background(
                        // Même couleur que le header des cartes
                        Color(red: 1.0, green: 0.4, blue: 0.6)
                    )
                    .cornerRadius(28)
                    .padding(.top, 20) // Réduit de 40 à 20 pour rapprocher du contenu
                    .padding(.bottom, 30) // Réduit de 50 à 30
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showingListView) {
            FavoritesView()
                .environmentObject(appState)
                .environmentObject(favoritesService)
        }
        .alert("remove_from_favorites".localized, isPresented: $showingDeleteAlert) {
            Button("cancel".localized, role: .cancel) { }
            Button("remove".localized, role: .destructive) {
                let allFavorites = favoritesService.getAllFavorites()
                if currentFavoriteIndex < allFavorites.count {
                    let currentFavorite = allFavorites[currentFavoriteIndex]
                    Task { @MainActor in
                        favoritesService.removeFavorite(questionId: currentFavorite.questionId)
                        
                        // Ajuster l'index si nécessaire
                        let updatedFavorites = favoritesService.getAllFavorites()
                        if currentIndex >= updatedFavorites.count && currentIndex > 0 {
                            currentIndex -= 1
                        }
                        
                        print("🔥 FavoritesCardView: Favori supprimé: \(currentFavorite.questionText.prefix(50))...")
                    }
                }
            }
        } message: {
            Text("remove_favorite_confirmation".localized)
        }
        .onAppear {
            print("🔥 FavoritesCardView: Vue des cartes favoris apparue")
            // Les favoris sont automatiquement chargés via le listener Firestore
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
        .frame(height: 400) // Réduit de 500 à 400 pour des cartes moins hautes
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