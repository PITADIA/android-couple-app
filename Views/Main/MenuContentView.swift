import SwiftUI
import FirebaseFirestore
import FirebaseFunctions
import UserNotifications

struct MenuContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var activeSheet: SheetType?
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair pour toute la vue
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea(.all)
                
                // ScrollView avec le même style que HomeContentView
                ScrollView {
                    MenuView(
                        onLocationTutorialTap: {
                            activeSheet = .locationTutorial
                        },
                        onWidgetsTap: {
                            activeSheet = .widgets
                        }
                    )
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
                

                
                // 🗑️ SECTION DEBUG SUPPRIMÉE
                // La fonctionnalité de correction des questions quotidiennes a été supprimée
                
                // MARK: - Informations section
            }
        }
        .navigationBarHidden(true)
        .sheet(item: $activeSheet) { sheetType in
            switch sheetType {
            case .locationTutorial:
                LocationPermissionFlow()
                    .onAppear {
                        print("📍 MenuContentView: LocationPermissionFlow apparue depuis le menu")
                    }
            case .widgets:
                WidgetsView()
                    .environmentObject(appState)
                    .onAppear {
                        print("📱 MenuContentView: WidgetsView apparue depuis le menu")
                    }
            default:
                EmptyView()
            }
        }
    }
} 

 