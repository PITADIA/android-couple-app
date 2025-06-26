import SwiftUI

struct MenuContentView: View {
    @EnvironmentObject var appState: AppState
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair pour toute la vue
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea(.all)
                
                // ScrollView avec le même style que HomeContentView
                ScrollView {
                    MenuView()
                        .environmentObject(appState)
                        .padding(.bottom, 100) // Espace pour le menu du bas
                        .background(
                            // Dégradé rose en arrière-plan du contenu (exactement comme HomeContentView)
                            VStack {
                                LinearGradient(
                                    gradient: Gradient(colors: [
                                        Color(hex: "#FD267A").opacity(0.3),
                                        Color(hex: "#FD267A").opacity(0.1),
                                        Color.white.opacity(0)
                                    ]),
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                                .frame(height: 350) // Augmenter la hauteur pour couvrir plus d'espace
                                .ignoresSafeArea(edges: .top)
                                
                                Spacer()
                            }
                            , alignment: .top
                        )
                }
                .ignoresSafeArea(edges: .top) // Permettre au ScrollView de remonter jusqu'en haut
            }
        }
        .navigationBarHidden(true)
    }
} 