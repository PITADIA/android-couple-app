import SwiftUI
import Combine

// MARK: - Sheet Type Enum
enum SheetType: Identifiable {
    case questions(QuestionCategory)
    case menu
    case subscription
    case favorites
    
    var id: String {
        switch self {
        case .questions(let category):
            return "questions_\(category.id)"
        case .menu:
            return "menu"
        case .subscription:
            return "subscription"
        case .favorites:
            return "favorites"
        }
    }
}

struct MainView: View {
    @EnvironmentObject var appState: AppState
    @State private var activeSheet: SheetType?
    
    let columns = [
        GridItem(.flexible(), spacing: 16),
        GridItem(.flexible(), spacing: 16)
    ]
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond dégradé sombre comme dans l'image
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.15, green: 0.05, blue: 0.2),
                        Color(red: 0.25, green: 0.1, blue: 0.3)
                    ]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Menu fixe en haut (hamburger et cœur)
                    HStack {
                        Button(action: {
                            activeSheet = .menu
                        }) {
                            Image(systemName: "line.3.horizontal")
                                .font(.system(size: 20))
                                .foregroundColor(.white)
                        }
                        
                        Spacer()
                        
                        Button(action: {
                            activeSheet = .favorites
                            print("🔥 MainView: Ouverture des favoris")
                        }) {
                            ZStack {
                                Image(systemName: "heart")
                                    .font(.system(size: 20))
                                    .foregroundColor(.white)
                                
                                // Badge avec le nombre de favoris
                                if let favoritesService = appState.favoritesService, favoritesService.getFavoritesCount() > 0 {
                                    Text("\(favoritesService.getFavoritesCount())")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(.white)
                                        .frame(width: 16, height: 16)
                                        .background(Color.red)
                                        .clipShape(Circle())
                                        .offset(x: 10, y: -10)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 10)
                    
                    // ScrollView avec logo et grille des catégories
                    ScrollView {
                        VStack(spacing: 30) {
                            // Logo principal qui scroll avec le contenu
                            Image("LogoMain")
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(width: 60, height: 60)
                                .padding(.top, 20)
                            
                            // Grille des catégories
                            LazyVGrid(columns: columns, spacing: 16) {
                                // Utiliser toutes les catégories - le FreemiumManager gère l'accès
                                ForEach(QuestionCategory.categories) { category in
                                    CategoryCardView(category: category) {
                                        print("🔥🔥🔥 MAINVIEW CALLBACK: Catégorie sélectionnée: \(category.title)")
                                        activeSheet = .questions(category)
                                        print("🔥🔥🔥 MAINVIEW CALLBACK: activeSheet = .questions(\(category.title))")
                                    }
                                    .frame(height: 200)
                                    .environmentObject(appState)
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                        .padding(.bottom, 40)
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(item: $activeSheet) { sheetType in
            switch sheetType {
            case .questions(let category):
                QuestionListView(category: category)
                    .onAppear {
                        print("🔥🔥🔥 MAINVIEW SHEET: Affichage QuestionListView pour \(category.title)")
            }
                
            case .menu:
                MenuView()
                    .environmentObject(appState)
                    .onAppear {
                        print("🔥 MainView: MenuView apparue dans la sheet")
                    }
                
            case .subscription:
            SubscriptionView()
                .environmentObject(appState)
                .onAppear {
                    print("🔥 MainView: SubscriptionView apparue dans la sheet")
                    print("🔥🔥🔥 MAINVIEW SHEET: SUBSCRIPTIONVIEW APPARUE!")
                }
                .onDisappear {
                    print("🔥 MainView: SubscriptionView disparue de la sheet")
                    print("🔥🔥🔥 MAINVIEW SHEET: SUBSCRIPTIONVIEW DISPARUE!")
                    // S'assurer que le FreemiumManager est notifié de la fermeture
                    appState.freemiumManager?.dismissSubscription()
                }
                
            case .favorites:
                FavoritesCardView()
                    .environmentObject(appState)
                    .environmentObject(appState.favoritesService ?? FavoritesService())
                    .onAppear {
                        print("🔥 MainView: FavoritesCardView apparue dans la sheet")
                    }
                }
        }

        .onAppear {
            print("🔥 MainView: Vue principale apparue")
            print("🔥🔥🔥 MAINVIEW ONAPPEAR: ETAT INITIAL")
            print("🔥🔥🔥 MAINVIEW ONAPPEAR: - FreemiumManager disponible: \(appState.freemiumManager != nil)")
            
            if let appFreemiumManager = appState.freemiumManager {
                print("🔥🔥🔥 MAINVIEW ONAPPEAR: - FreemiumManager.showingSubscription: \(appFreemiumManager.showingSubscription)")
                print("🔥 MainView: FreemiumManager disponible")
            } else {
                print("🔥🔥🔥 MAINVIEW ONAPPEAR: - FreemiumManager: NIL!")
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .freemiumManagerChanged)) { _ in
            // Synchroniser l'état local avec le FreemiumManager
            if let freemiumManager = appState.freemiumManager, freemiumManager.showingSubscription {
                print("🔥🔥🔥 MAINVIEW NOTIFICATION: FreemiumManager demande subscription")
                activeSheet = .subscription
            }
        }
    }
}

// MARK: - Extensions pour les notifications
extension Notification.Name {
    static let freemiumManagerChanged = Notification.Name("freemiumManagerChanged")
} 