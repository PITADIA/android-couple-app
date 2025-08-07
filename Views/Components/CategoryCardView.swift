import SwiftUI

struct CategoryCardView: View {
    let category: QuestionCategory
    let action: () -> Void
    @EnvironmentObject var appState: AppState
    
    var body: some View {
        Button(action: {
            print("🔥 CategoryCardView: Tap détecté sur \(category.title)")
            print("🔥 Category tap: DEBUT GESTION")
            print("🔥 Category tap: - Catégorie: \(category.title)")
            print("🔥 Category tap: - FreemiumManager disponible: \(appState.freemiumManager != nil)")
            
            // Utiliser le FreemiumManager pour gérer le tap
            if let freemiumManager = appState.freemiumManager {
                print("🔥 Category tap: APPEL handleCategoryTap")
                print("🔥 Category tap: FreemiumManager trouvé: \(String(describing: freemiumManager))")
                print("🔥 Category tap: Avant appel handleCategoryTap")
                
                freemiumManager.handleCategoryTap(category) {
                    print("🔥 Category tap: CALLBACK EXECUTE - ACCES AUTORISE")
                    action()
                }
                
                print("🔥 Category tap: Après appel handleCategoryTap")
            } else {
                print("🔥 Category tap: FREEMIUM MANAGER MANQUANT - FALLBACK")
                print("🔥 Category tap: appState: \(appState)")
                print("🔥 Category tap: appState.freemiumManager: \(String(describing: appState.freemiumManager))")
                // Fallback si FreemiumManager n'est pas disponible
                action()
            }
        }) {
            ZStack {
                // Fond avec bordure arrondie (design noir original)
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.black)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.white, lineWidth: 2)
                    )
                
                VStack(spacing: 10) {
                    // Emoji en haut
                    Text(category.emoji)
                        .font(.system(size: 40))
                    
                    // Titre
                    Text(category.title)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                    
                    // Sous-titre avec cadenas si premium
                    VStack(spacing: 4) {
                        HStack(spacing: 4) {
                            Text(category.subtitle)
                                .font(.system(size: 12))
                                .foregroundColor(.gray)
                                .multilineTextAlignment(.center)
                                .lineLimit(nil)
                                .fixedSize(horizontal: false, vertical: true)
                                .lineSpacing(2)
                            
                            // Cadenas pour les catégories premium - emoji traditionnel
                            if let freemiumManager = appState.freemiumManager,
                               category.isPremium && !(appState.currentUser?.isSubscribed ?? false) {
                                Text("🔒")
                                    .font(.system(size: 12))
                            }
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 16)
            }
        }
        .buttonStyle(PlainButtonStyle())
        .scaleEffect(1.0)
    }
}

struct CategoryCardView_Previews: PreviewProvider {
    static var previews: some View {
        CategoryCardView(
            category: QuestionCategory.categories[0],
            action: {}
        )
        .frame(width: 160, height: 200)
        .padding()
        .background(Color.purple)
    }
} 