import SwiftUI

struct TabContainerView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedTab = 0
    @State private var activeSheet: SheetType?
    
    // NOUVEAU: Observer l'état de focus des TextFields pour cacher le menu
    @State private var isKeyboardVisible = false
    
    var body: some View {
        ZStack {
            // Contenu principal selon l'onglet sélectionné
            Group {
                switch selectedTab {
                case 0:
                    HomeContentView(activeSheet: $activeSheet)
                case 1:
                    // Questions du jour
                    DailyQuestionFlowView()
                        .environmentObject(appState)
                case 2:
                    FavoritesView()
                case 3:
                    JournalPageView()
                case 4:
                    MenuContentView()
                default:
                    HomeContentView(activeSheet: $activeSheet)
                }
            }
            // NOUVEAU: Padding pour éviter que le contenu passe sous le menu
            .padding(.bottom, isKeyboardVisible ? 1 : 45) // Adaptatif : 6px quand clavier ouvert, 60px quand fermé
        }
        // NOUVEAU: Menu positionné avec overlay pour qu'il reste TOUJOURS en bas
        .overlay(
            // Menu fixe en bas - CACHÉ quand le clavier est visible
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
                    
                    // Questions du jour
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
                    
                    // Favoris
                    Button(action: {
                        selectedTab = 2
                    }) {
                        Image("heart")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(
                                width: selectedTab == 2 ? 34 : 30,
                                height: selectedTab == 2 ? 28 : 24
                            )
                            .foregroundColor(selectedTab == 2 ? Color(hex: "#FD267A") : .gray)
                            .fontWeight(selectedTab == 2 ? .bold : .regular)
                            .scaleEffect(selectedTab == 2 ? 1.1 : 1.0)
                            .animation(.easeInOut(duration: 0.2), value: selectedTab)
                    }
                    .frame(maxWidth: .infinity)
                    
                    // Journal
                    Button(action: {
                        selectedTab = 3
                    }) {
                        Image("map")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(
                                width: selectedTab == 3 ? 32 : 28,
                                height: selectedTab == 3 ? 26 : 22
                            )
                            .foregroundColor(selectedTab == 3 ? Color(hex: "#FD267A") : .gray.opacity(0.8))
                            .opacity(selectedTab == 3 ? 1.0 : 0.85)
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
                // CRUCIAL: Le menu ignore complètement le clavier et reste en bas
                .ignoresSafeArea(.keyboard, edges: .bottom)
                // NOUVEAU: Cacher le menu quand le clavier est visible
                .opacity(isKeyboardVisible ? 0 : 1)
                // L'animation est gérée dans les observateurs de clavier
            }
        )
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
                
            case .dailyQuestionPermission:
                DailyQuestionPermissionView()
                    .environmentObject(appState)
                    .onAppear {
                        print("🔥 TabContainer: DailyQuestionPermissionView apparue dans la sheet")
                    }
                

                
            default:
                EmptyView()
            }
        }
        .onAppear {
            print("🔥 TabContainer: Vue principale apparue")
            
            // NOUVEAU: Observer les notifications de clavier pour cacher le menu
            NotificationCenter.default.addObserver(
                forName: UIResponder.keyboardWillShowNotification,
                object: nil,
                queue: .main
            ) { _ in
                withAnimation(.easeInOut(duration: 0.3)) {
                    isKeyboardVisible = true
                }
            }
            
            NotificationCenter.default.addObserver(
                forName: UIResponder.keyboardWillHideNotification,
                object: nil,
                queue: .main
            ) { _ in
                withAnimation(.easeInOut(duration: 0.3)) {
                    isKeyboardVisible = false
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .freemiumManagerChanged)) { _ in
            if let freemiumManager = appState.freemiumManager {
                if freemiumManager.showingSubscription && activeSheet != .subscription {
                    print("🔥🔥🔥 TABCONTAINER ONRECEIVE: AFFICHAGE SUBSCRIPTION DEMANDE")
                    activeSheet = .subscription
                }
            }
        }
        .onDisappear {
            // NOUVEAU: Les observateurs avec closure sont automatiquement nettoyés
            // Pas besoin de removeObserver explicite
            print("🔥 TabContainer: Vue principale disparue")
        }
    }
} 