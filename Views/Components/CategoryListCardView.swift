import SwiftUI

struct CategoryListCardView: View {
    let category: QuestionCategory
    let action: () -> Void
    @EnvironmentObject var appState: AppState
    
    // Garder les textes et émojis originaux
    
    var body: some View {
        Button(action: {
            print("🔥 CategoryListCardView: Tap détecté sur \(category.title)")
            
            // Utiliser le FreemiumManager pour gérer le tap
            if let freemiumManager = appState.freemiumManager {
                freemiumManager.handleCategoryTap(category) {
                    action()
                }
            } else {
                action()
            }
        }) {
            HStack(spacing: 16) {
                                 // Contenu principal
                 VStack(alignment: .leading, spacing: 6) {
                     // Titre principal (original)
                     Text(category.title)
                         .font(.system(size: 20, weight: .bold))
                         .foregroundColor(.black)
                         .multilineTextAlignment(.leading)
                     
                     // Sous-titre (original)
                     HStack(spacing: 4) {
                         Text(category.subtitle)
                             .font(.system(size: 14))
                             .foregroundColor(.gray)
                             .multilineTextAlignment(.leading)
                         
                         // Cadenas pour les catégories premium - emoji traditionnel
                         if category.isPremium && !(appState.currentUser?.isSubscribed ?? false) {
                             Text("🔒")
                                 .font(.system(size: 14))
                         }
                     }
                 }
                 
                 Spacer()
                 
                 // Emoji original à droite
                 Text(category.emoji)
                     .font(.system(size: 28))
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 20)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.95))
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Preview
struct CategoryListCardView_Previews: PreviewProvider {
    static var previews: some View {
        ZStack {
            Color(red: 0.1, green: 0.02, blue: 0.05)
                .ignoresSafeArea()
            
            VStack(spacing: 12) {
                ForEach(Array(QuestionCategory.categories.prefix(3))) { category in
                    CategoryListCardView(category: category) {
                        print("Catégorie sélectionnée: \(category.title)")
                    }
                    .environmentObject(AppState())
                }
            }
            .padding(.horizontal, 20)
        }
    }
} 