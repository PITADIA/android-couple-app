import SwiftUI
import Combine

struct MainView: View {
    @EnvironmentObject var appState: AppState
    @State private var activeSheet: SheetType?
    
    // Vérifier si un partenaire est connecté
    private var hasConnectedPartner: Bool {
        guard let partnerId = appState.currentUser?.partnerId else { return false }
        return !partnerId.isEmpty
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair pour toute la vue
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea(.all)
                
                VStack(spacing: 0) {
                    // ScrollView avec section distance partenaires et grille des catégories
                    ScrollView {
                        VStack(spacing: 30) {
                            // Section distance entre partenaires (remplace le logo)
                            PartnerDistanceView(
                                onPartnerAvatarTap: {
                                    activeSheet = .partnerManagement
                                },
                                onDistanceTap: { showPartnerMessageOnly in
                                    if showPartnerMessageOnly {
                                        activeSheet = .partnerLocationMessage
                                    } else {
                                        activeSheet = .locationPermission
                                    }
                                }
                            )
                                .environmentObject(appState)
                                .padding(.top, 80) // Ajouter plus d'espace depuis la status bar
                            
                            // Section invitation partenaire (si pas connecté)
                            if !hasConnectedPartner {
                                PartnerInviteView {
                                    activeSheet = .partnerManagement
                                }
                                .padding(.top, -15) // Rapprocher de la section distance
                            }
                            
                            // Liste des catégories (style rectangulaire)
                            VStack(spacing: 20) {
                                // Utiliser toutes les catégories - le FreemiumManager gère l'accès
                                ForEach(QuestionCategory.categories) { category in
                                    CategoryListCardView(category: category) {
                                        print("🔥🔥🔥 MAINVIEW CALLBACK: Catégorie sélectionnée: \(category.title)")
                                        activeSheet = .questions(category)
                                        print("🔥🔥🔥 MAINVIEW CALLBACK: activeSheet = .questions(\(category.title))")
                                    }
                                    .environmentObject(appState)
                                }
                            }
                            .padding(.horizontal, 20)
                            
                            // Carte widget (remplace la section widgets défilants)
                            WidgetPreviewSection(onWidgetTap: {
                                print("📱 MainView: Carte widget tappée, ouverture de la page widgets")
                                activeSheet = .widgets
                            })
                        }
                        .padding(.bottom, 100) // Espace pour le menu du bas
                        .background(
                            // Dégradé rose en arrière-plan du contenu
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
                
                // Menu fixe en bas
                VStack {
                    Spacer()
                    
                    HStack(spacing: 0) {
                        // Accueil
                        Button(action: {
                            // Déjà sur l'accueil
                        }) {
                            Image("home")
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(width: 30, height: 24)
                                .foregroundColor(Color(hex: "#FD267A"))
                        }
                        .frame(maxWidth: .infinity)
                        
                        // Journal
                        Button(action: {
                            activeSheet = .journal
                            print("🔥 MainView: Ouverture du journal")
                        }) {
                            Image("star")
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(width: 30, height: 24)
                                .foregroundColor(Color(hex: "#FD267A"))
                        }
                        .frame(maxWidth: .infinity)
                        
                        // Favoris
                        Button(action: {
                            activeSheet = .favorites
                            print("🔥 MainView: Ouverture des favoris")
                        }) {
                            ZStack {
                                Image("heart")
                                    .resizable()
                                    .aspectRatio(contentMode: .fit)
                                    .frame(width: 30, height: 24)
                                    .foregroundColor(.gray)
                                
                                // Badge avec le nombre de favoris
                                if let favoritesService = appState.favoritesService, favoritesService.getFavoritesCount() > 0 {
                                    Text("\(favoritesService.getFavoritesCount())")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(.white)
                                        .frame(width: 16, height: 16)
                                        .background(Color.red)
                                        .clipShape(Circle())
                                        .offset(x: 13, y: -13)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity)
                        
                        // Profil
                        Button(action: {
                            activeSheet = .menu
                        }) {
                            Image("profile")
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(width: 30, height: 24)
                                .foregroundColor(.gray)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(
                        Color.white.opacity(0.95)
                            .blur(radius: 10)
                    )
                    .background(Color.white.opacity(0.9))
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
                
            case .journal:
                JournalView()
                    .environmentObject(appState)
                    .onAppear {
                        print("🔥 MainView: JournalView apparue dans la sheet")
                    }
                
            case .widgets:
                WidgetsView()
                    .environmentObject(appState)
                    .onAppear {
                        print("🔥 MainView: WidgetsView apparue dans la sheet")
                    }
                
            case .widgetTutorial:
                WidgetTutorialView()
                    .presentationDetents([.fraction(0.55)])
                    .onAppear {
                        print("🔥 MainView: WidgetTutorialView apparue dans la sheet")
                    }
                
            case .partnerManagement:
                PartnerManagementView()
                    .environmentObject(appState)
                    .onAppear {
                        print("🔥 MainView: PartnerManagementView apparue dans la sheet")
                    }
                
            case .locationPermission:
                LocationPermissionFlow()
                    .onAppear {
                        print("📍 MainView: LocationPermissionFlow apparue dans la sheet")
                    }
                
            case .partnerLocationMessage:
                LocationPartnerMessageView()
                    .onAppear {
                        print("📍 MainView: LocationPartnerMessageView apparue dans la sheet")
                    }
                
            case .eventsMap:
                JournalMapView()
                    .environmentObject(appState)
                    .onAppear {
                        print("🗺️ MainView: JournalMapView apparue dans la sheet")
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
            if let freemiumManager = appState.freemiumManager {
                if freemiumManager.showingSubscription && activeSheet != .subscription {
                    print("🔥🔥🔥 MAINVIEW ONRECEIVE: AFFICHAGE SUBSCRIPTION DEMANDE")
                    activeSheet = .subscription
                }
            }
        }
    }
}

// MARK: - Extensions pour les notifications
extension Notification.Name {
    static let freemiumManagerChanged = Notification.Name("freemiumManagerChanged")
} 