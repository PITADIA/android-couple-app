import SwiftUI
import FirebaseAnalytics

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
                    DailyQuestionFlowView()
                        .environmentObject(appState)
                case 2:
                    DailyChallengeFlowView()
                        .environmentObject(appState)
                case 3:
                    FavoritesView()
                case 4:
                    JournalPageView()
                case 5:
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
                        // 📊 Analytics: Navigation onglet
                        Analytics.logEvent("onglet_visite", parameters: ["onglet": "accueil"])
                        print("📊 Événement Firebase: onglet_visite - onglet: accueil")
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
                        // 📅 LOGS DATE/HEURE DEMANDÉS - CLIC QUESTIONS DU JOUR
                        let now = Date()
                        let formatter = DateFormatter()
                        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
                        formatter.timeZone = TimeZone.current
                        print("🚀 === CLIC QUESTIONS DU JOUR ===")
                        print("🕐 CLIC QUESTIONS: Date/Heure actuelle: \(formatter.string(from: now))")
                        print("🌍 CLIC QUESTIONS: Timezone: \(TimeZone.current.identifier)")
                        print("📅 CLIC QUESTIONS: Jour de la semaine: \(Calendar.current.component(.weekday, from: now))")
                        print("📊 CLIC QUESTIONS: Jour du mois: \(Calendar.current.component(.day, from: now))")
                        print("📈 CLIC QUESTIONS: Mois: \(Calendar.current.component(.month, from: now))")
                        print("📉 CLIC QUESTIONS: Année: \(Calendar.current.component(.year, from: now))")
                        
                        selectedTab = 1
                        // 📊 Analytics: Navigation onglet
                        Analytics.logEvent("onglet_visite", parameters: ["onglet": "questions"])
                        print("📊 Événement Firebase: onglet_visite - onglet: questions")
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
                    
                    // Défis du jour
                    Button(action: {
                        // 📅 LOGS DATE/HEURE DEMANDÉS - CLIC DÉFIS DU JOUR
                        let now = Date()
                        let formatter = DateFormatter()
                        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
                        formatter.timeZone = TimeZone.current
                        print("🚀 === CLIC DÉFIS DU JOUR ===")
                        print("🕐 CLIC DÉFIS: Date/Heure actuelle: \(formatter.string(from: now))")
                        print("🌍 CLIC DÉFIS: Timezone: \(TimeZone.current.identifier)")
                        print("📅 CLIC DÉFIS: Jour de la semaine: \(Calendar.current.component(.weekday, from: now))")
                        print("📊 CLIC DÉFIS: Jour du mois: \(Calendar.current.component(.day, from: now))")
                        print("📈 CLIC DÉFIS: Mois: \(Calendar.current.component(.month, from: now))")
                        print("📉 CLIC DÉFIS: Année: \(Calendar.current.component(.year, from: now))")
                        
                        selectedTab = 2
                        // 📊 Analytics: Navigation onglet
                        Analytics.logEvent("onglet_visite", parameters: ["onglet": "defis"])
                        print("📊 Événement Firebase: onglet_visite - onglet: defis")
                    }) {
                        Image("miss")
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
                    
                    // Favoris
                    Button(action: {
                        selectedTab = 3
                        // 📊 Analytics: Navigation onglet
                        Analytics.logEvent("onglet_visite", parameters: ["onglet": "favoris"])
                        print("📊 Événement Firebase: onglet_visite - onglet: favoris")
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
                    
                    // Journal
                    Button(action: {
                        selectedTab = 4
                        // 📊 Analytics: Navigation onglet
                        Analytics.logEvent("onglet_visite", parameters: ["onglet": "journal"])
                        print("📊 Événement Firebase: onglet_visite - onglet: journal")
                    }) {
                        Image("map")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(
                                width: selectedTab == 4 ? 32 : 28,
                                height: selectedTab == 4 ? 26 : 22
                            )
                            .foregroundColor(selectedTab == 4 ? Color(hex: "#FD267A") : .gray.opacity(0.8))
                            .opacity(selectedTab == 4 ? 1.0 : 0.85)
                            .scaleEffect(selectedTab == 4 ? 1.1 : 1.0)
                            .animation(.easeInOut(duration: 0.2), value: selectedTab)
                    }
                    .frame(maxWidth: .infinity)
                    
                    // Profil
                    Button(action: {
                        selectedTab = 5
                        // 📊 Analytics: Navigation onglet
                        Analytics.logEvent("onglet_visite", parameters: ["onglet": "profil"])
                        print("📊 Événement Firebase: onglet_visite - onglet: profil")
                    }) {
                        Image("profile")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(
                                width: selectedTab == 5 ? 34 : 30,
                                height: selectedTab == 5 ? 28 : 24
                            )
                            .foregroundColor(selectedTab == 5 ? Color(hex: "#FD267A") : .gray)
                            .fontWeight(selectedTab == 5 ? .bold : .regular)
                            .scaleEffect(selectedTab == 5 ? 1.1 : 1.0)
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