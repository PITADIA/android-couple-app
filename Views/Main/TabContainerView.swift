import SwiftUI

struct TabContainerView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedTab = 0
    @State private var activeSheet: SheetType?
    
    var body: some View {
        ZStack {
            // Contenu principal selon l'onglet sélectionné
            Group {
                switch selectedTab {
                case 0:
                    HomeContentView(activeSheet: $activeSheet)
                case 1:
                    JournalPageView()
                case 2:
                    // Carte des événements
                    JournalMapView(showBackButton: false)
                        .environmentObject(appState)
                case 3:
                    FavoritesView()
                case 4:
                    MenuContentView()
                default:
                    HomeContentView(activeSheet: $activeSheet)
                }
            }
            
            // Menu fixe en bas
            VStack {
                Spacer()
                
                HStack(spacing: 0) {
                    // Accueil
                    Button(action: {
                        selectedTab = 0
                    }) {
                        Image("home")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(
                                width: selectedTab == 0 ? 34 : 30,
                                height: selectedTab == 0 ? 28 : 24
                            )
                            .foregroundColor(selectedTab == 0 ? Color(hex: "#FD267A") : .gray)
                            .fontWeight(selectedTab == 0 ? .bold : .regular)
                            .scaleEffect(selectedTab == 0 ? 1.1 : 1.0)
                            .animation(.easeInOut(duration: 0.2), value: selectedTab)
                    }
                    .frame(maxWidth: .infinity)
                    
                    // Journal
                    Button(action: {
                        selectedTab = 1
                    }) {
                        Image("star")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(
                                width: selectedTab == 1 ? 34 : 30,
                                height: selectedTab == 1 ? 28 : 24
                            )
                            .foregroundColor(selectedTab == 1 ? Color(hex: "#FD267A") : .gray)
                            .fontWeight(selectedTab == 1 ? .bold : .regular)
                            .scaleEffect(selectedTab == 1 ? 1.1 : 1.0)
                            .animation(.easeInOut(duration: 0.2), value: selectedTab)
                    }
                    .frame(maxWidth: .infinity)
                    
                    // Carte des événements
                    Button(action: {
                        selectedTab = 2
                    }) {
                        Image("map")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(
                                width: selectedTab == 2 ? 32 : 28,
                                height: selectedTab == 2 ? 26 : 22
                            )
                            .foregroundColor(selectedTab == 2 ? Color(hex: "#FD267A") : .gray.opacity(0.8))
                            .opacity(selectedTab == 2 ? 1.0 : 0.85)
                            .scaleEffect(selectedTab == 2 ? 1.1 : 1.0)
                            .animation(.easeInOut(duration: 0.2), value: selectedTab)
                    }
                    .frame(maxWidth: .infinity)
                    
                    // Favoris
                    Button(action: {
                        selectedTab = 3
                    }) {
                        Image("heart")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(
                                width: selectedTab == 3 ? 34 : 30,
                                height: selectedTab == 3 ? 28 : 24
                            )
                            .foregroundColor(selectedTab == 3 ? Color(hex: "#FD267A") : .gray)
                            .fontWeight(selectedTab == 3 ? .bold : .regular)
                            .scaleEffect(selectedTab == 3 ? 1.1 : 1.0)
                            .animation(.easeInOut(duration: 0.2), value: selectedTab)
                    }
                    .frame(maxWidth: .infinity)
                    
                    // Profil
                    Button(action: {
                        selectedTab = 4
                    }) {
                        Image("profile")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(
                                width: selectedTab == 4 ? 34 : 30,
                                height: selectedTab == 4 ? 28 : 24
                            )
                            .foregroundColor(selectedTab == 4 ? Color(hex: "#FD267A") : .gray)
                            .fontWeight(selectedTab == 4 ? .bold : .regular)
                            .scaleEffect(selectedTab == 4 ? 1.1 : 1.0)
                            .animation(.easeInOut(duration: 0.2), value: selectedTab)
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(Color.white)
            }
        }
        .navigationBarHidden(true)
        .sheet(item: $activeSheet) { sheetType in
            switch sheetType {
            case .questions(let category):
                QuestionListView(category: category)
                    .onAppear {
                        print("🔥🔥🔥 TABCONTAINER SHEET: Affichage QuestionListView pour \(category.title)")
                    }
                
            case .subscription:
                SubscriptionView()
                    .environmentObject(appState)
                    .onAppear {
                        print("🔥 TabContainer: SubscriptionView apparue dans la sheet")
                    }
                    .onDisappear {
                        print("🔥 TabContainer: SubscriptionView disparue de la sheet")
                        appState.freemiumManager?.dismissSubscription()
                    }
                
            case .widgets:
                WidgetsView()
                    .environmentObject(appState)
                    .onAppear {
                        print("🔥 TabContainer: WidgetsView apparue dans la sheet")
                    }
                
            case .widgetTutorial:
                WidgetTutorialView()
                    .presentationDetents([.fraction(0.55)])
                    .onAppear {
                        print("🔥 TabContainer: WidgetTutorialView apparue dans la sheet")
                    }
                
            case .partnerManagement:
                PartnerManagementView()
                    .environmentObject(appState)
                    .onAppear {
                        print("🔥 TabContainer: PartnerManagementView apparue dans la sheet")
                    }
                
            case .locationPermission:
                LocationPermissionFlow()
                    .onAppear {
                        print("📍 TabContainer: LocationPermissionFlow apparue dans la sheet")
                    }
                    .onDisappear {
                        print("📍 TabContainer: LocationPermissionFlow disparue - Démarrage LocationService")
                        // NOUVEAU: Démarrer immédiatement les mises à jour de localisation
                        appState.locationService?.startLocationUpdatesIfAuthorized()
                    }
                
            case .partnerLocationMessage:
                LocationPartnerMessageView()
                    .onAppear {
                        print("📍 TabContainer: LocationPartnerMessageView apparue dans la sheet")
                    }
                
            case .eventsMap:
                JournalMapView(showBackButton: false)
                    .environmentObject(appState)
                    .onAppear {
                        print("🗺️ TabContainer: JournalMapView apparue dans la sheet")
                    }
                
            case .locationTutorial:
                LocationPermissionFlow()
                    .onAppear {
                        print("📍 TabContainer: LocationPermissionFlow apparue depuis le menu")
                    }
                    .onDisappear {
                        print("📍 TabContainer: LocationPermissionFlow disparue depuis le menu")
                        // Démarrer immédiatement les mises à jour de localisation
                        appState.locationService?.startLocationUpdatesIfAuthorized()
                    }
                
            default:
                EmptyView()
            }
        }
        .onAppear {
            print("🔥 TabContainer: Vue principale apparue")
        }
        .onReceive(NotificationCenter.default.publisher(for: .freemiumManagerChanged)) { _ in
            if let freemiumManager = appState.freemiumManager {
                if freemiumManager.showingSubscription && activeSheet != .subscription {
                    print("🔥🔥🔥 TABCONTAINER ONRECEIVE: AFFICHAGE SUBSCRIPTION DEMANDE")
                    activeSheet = .subscription
                }
            }
        }
    }
} 