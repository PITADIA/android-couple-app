import SwiftUI

struct FavoritesView: View {
    @EnvironmentObject var appState: AppState
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à l'accueil
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea(.all)
                
                FavoritesCardView()
                    .environmentObject(appState)
                    .environmentObject(appState.favoritesService ?? FavoritesService())
                    .padding(.bottom, 100) // Espace pour le menu du bas
            }
        }
        .navigationBarHidden(true)
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
                        .foregroundColor(.black)
                }
                
                Spacer()
                
                HStack(spacing: 12) {
                    Text(favorite.dateAdded, style: .date)
                        .font(.system(size: 12))
                        .foregroundColor(.black.opacity(0.7))
                    
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
                .foregroundColor(.black)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.8))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.black.opacity(0.1), lineWidth: 1)
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