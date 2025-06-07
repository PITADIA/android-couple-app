import SwiftUI

struct CategoryCardView: View {
    let category: QuestionCategory
    let action: () -> Void
    @EnvironmentObject var appState: AppState
    
    var body: some View {
        Button(action: {
            print("🔥 CategoryCardView: Tap détecté sur \(category.title)")
            print("🔥🔥🔥 CATEGORY TAP: DEBUT GESTION")
            print("🔥🔥🔥 CATEGORY TAP: - Catégorie: \(category.title)")
            print("🔥🔥🔥 CATEGORY TAP: - FreemiumManager disponible: \(appState.freemiumManager != nil)")
            
            // Utiliser le FreemiumManager pour gérer le tap
            if let freemiumManager = appState.freemiumManager {
                print("🔥🔥🔥 CATEGORY TAP: APPEL handleCategoryTap")
                freemiumManager.handleCategoryTap(category) {
                    print("🔥🔥🔥 CATEGORY TAP: CALLBACK EXECUTE - ACCES AUTORISE")
                    action()
                }
            } else {
                print("🔥🔥🔥 CATEGORY TAP: FREEMIUM MANAGER MANQUANT - FALLBACK")
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
                        .font(.system(size: 28))
                    
                    // Titre
                    Text(category.title)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                    
                    // Sous-titre
                    Text(category.subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                        .lineSpacing(2)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 16)
                
                // Overlay de blocage pour les catégories premium non accessibles
                if let freemiumManager = appState.freemiumManager,
                   category.isPremium && !(appState.currentUser?.isSubscribed ?? false) {
                    ZStack {
                        // Fond semi-transparent
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.black.opacity(0.75))
                        
                        VStack(spacing: 8) {
                            // Icône de cadenas
                            Image(systemName: "lock.fill")
                                .font(.system(size: 24))
                                .foregroundColor(.yellow)
                            
                            // Texte "Premium"
                            Text("Premium")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.yellow)
                            
                            // Texte "Débloquer"
                            Text("Toucher pour débloquer")
                                .font(.system(size: 10))
                                .foregroundColor(.white.opacity(0.8))
                                .multilineTextAlignment(.center)
                        }
                    }
                }
            }
        }
        .buttonStyle(PlainButtonStyle())
        .scaleEffect(1.0)
        .animation(.easeInOut(duration: 0.1), value: false)
    }
}

// Extension pour convertir les couleurs hex en Color
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }

        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
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