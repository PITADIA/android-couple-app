import SwiftUI

struct FavoritesPreviewCard: View {
    @EnvironmentObject private var favoritesService: FavoritesService
    let onTapViewAll: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 16))
                        .foregroundColor(.red)
                    
                    Text(NSLocalizedString("my_favorites", comment: "My favorites title"))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.black)
                }
                
                Spacer()
                
                Button(NSLocalizedString("view_all", comment: "View all button")) {
                    onTapViewAll()
                }
                .font(.system(size: 16))
                .foregroundColor(Color(hex: "#FD267A"))
            }
            
            // Contenu
            if favoritesService.getAllFavorites().isEmpty {
                VStack(spacing: 8) {
                    Text("❤️")
                        .font(.system(size: 24))
                    
                    Text(NSLocalizedString("no_favorites_yet", comment: "No favorites yet message"))
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.6))
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
            } else {
                VStack(spacing: 8) {
                    ForEach(favoritesService.getRecentFavorites(limit: 3)) { favorite in
                        HStack(spacing: 8) {
                            Text(favorite.emoji)
                                .font(.system(size: 12))
                            
                            Text(favorite.questionText)
                                .font(.system(size: 12))
                                .foregroundColor(.black.opacity(0.9))
                                .lineLimit(2)
                            
                            Spacer()
                        }
                    }
                    
                    if favoritesService.getFavoritesCount() > 3 {
                        Text(NSLocalizedString("and_others", comment: "And others text") + " \(favoritesService.getFavoritesCount() - 3) " + NSLocalizedString("others", comment: "Others text"))
                            .font(.system(size: 11))
                            .foregroundColor(.black.opacity(0.6))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
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

struct FavoritesPreviewCard_Previews: PreviewProvider {
    static var previews: some View {
        FavoritesPreviewCard {
            // Action
        }
        .environmentObject(FavoritesService())
        .padding()
        .background(Color.purple)
    }
} 