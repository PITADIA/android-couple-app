import SwiftUI

struct FavoritesView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var favoritesService: FavoritesService
    @EnvironmentObject private var appState: AppState
    
    @State private var searchText = ""
    @State private var selectedCategory = "Toutes"
    @State private var showingDeleteAlert = false
    @State private var favoriteToDelete: FavoriteQuestion?
    
    private var filteredFavorites: [FavoriteQuestion] {
        let searchFiltered = searchText.isEmpty ? 
            favoritesService.favoriteQuestions : 
            favoritesService.searchFavorites(query: searchText)
        
        if selectedCategory == "Toutes" {
            return searchFiltered
        } else {
            return searchFiltered.filter { $0.categoryTitle == selectedCategory }
        }
    }
    
    private var availableCategories: [String] {
        let categories = Set(favoritesService.favoriteQuestions.map { $0.categoryTitle })
        return ["Toutes"] + Array(categories).sorted()
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                // Même fond que la page des cartes de favoris
                Color(red: 0.15, green: 0.03, blue: 0.08)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header personnalisé
                    HStack {
                        Button(action: {
                            dismiss()
                        }) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 20, weight: .medium))
                                .foregroundColor(.white)
                        }
                        
                        Spacer()
                        
                        VStack(spacing: 4) {
                            Text("Mes Favoris")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)
                            
                            Text("\(favoritesService.getFavoritesCount()) questions")
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.8))
                        }
                        
                        Spacer()
                        
                        Menu {
                            Button("Tout supprimer", role: .destructive) {
                                showingDeleteAlert = true
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 20, weight: .medium))
                                .foregroundColor(.white)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 60)
                    .padding(.bottom, 20)
                    
                    // Barre de recherche
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.white.opacity(0.7))
                        
                        TextField("Rechercher dans mes favoris...", text: $searchText)
                            .foregroundColor(.white)
                            .font(.system(size: 16))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.white.opacity(0.15))
                    )
                    .padding(.horizontal, 20)
                    .padding(.bottom, 16)
                    
                    // Filtres par catégorie
                    if availableCategories.count > 1 {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(availableCategories, id: \.self) { category in
                                    Button(action: {
                                        selectedCategory = category
                                    }) {
                                        Text(category)
                                            .font(.system(size: 14, weight: .medium))
                                            .foregroundColor(selectedCategory == category ? .black : .white)
                                            .padding(.horizontal, 16)
                                            .padding(.vertical, 8)
                                            .background(
                                                RoundedRectangle(cornerRadius: 20)
                                                    .fill(selectedCategory == category ? Color.white : Color.white.opacity(0.2))
                                            )
                                    }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                        .padding(.bottom, 16)
                    }
                    
                    // Liste des favoris
                    if filteredFavorites.isEmpty {
                        // État vide
                        VStack(spacing: 20) {
                            Spacer()
                            
                            Text("❤️")
                                .font(.system(size: 60))
                            
                            Text(searchText.isEmpty ? "Aucun favori pour le moment" : "Aucun résultat")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                            
                            Text(searchText.isEmpty ? 
                                "Ajoutez des questions en favoris en appuyant sur ❤️ dans les cartes" :
                                "Essayez avec d'autres mots-clés")
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.7))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 40)
                            
                            Spacer()
                        }
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 12) {
                                ForEach(filteredFavorites) { favorite in
                                    FavoriteQuestionCard(
                                        favorite: favorite,
                                        onDelete: {
                                            favoriteToDelete = favorite
                                            showingDeleteAlert = true
                                        }
                                    )
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.bottom, 40)
                        }
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .alert("Supprimer", isPresented: $showingDeleteAlert) {
            Button("Annuler", role: .cancel) {
                favoriteToDelete = nil
            }
            Button("Supprimer", role: .destructive) {
                Task { @MainActor in
                    if let favorite = favoriteToDelete {
                        favoritesService.removeFavorite(questionId: favorite.questionId)
                    } else {
                        favoritesService.clearAllFavorites()
                    }
                    favoriteToDelete = nil
                }
            }
        } message: {
            if favoriteToDelete != nil {
                Text("Voulez-vous supprimer cette question de vos favoris ?")
            } else {
                Text("Voulez-vous supprimer tous vos favoris ? Cette action est irréversible.")
            }
        }
        .onAppear {
            print("🔥 FavoritesView: Vue des favoris apparue")
            Task { @MainActor in
                favoritesService.loadFavorites()
            }
        }
    }
}

struct FavoriteQuestionCard: View {
    let favorite: FavoriteQuestion
    let onDelete: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header avec catégorie et date
            HStack {
                HStack(spacing: 8) {
                    Text(favorite.emoji)
                        .font(.system(size: 16))
                    
                    Text(favorite.categoryTitle)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                }
                
                Spacer()
                
                HStack(spacing: 12) {
                    Text(favorite.dateAdded, style: .date)
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.7))
                    
                    Button(action: onDelete) {
                        Image(systemName: "trash")
                            .font(.system(size: 14))
                            .foregroundColor(.red)
                    }
                }
            }
            
            // Question
            Text(favorite.questionText)
                .font(.system(size: 16))
                .foregroundColor(.white)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.1))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
        )
    }
}

struct FavoritesView_Previews: PreviewProvider {
    static var previews: some View {
        FavoritesView()
            .environmentObject(FavoritesService())
    }
} 