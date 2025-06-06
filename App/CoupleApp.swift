import SwiftUI
import Firebase
import StoreKit

@main
struct CoupleApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var appState = AppState()
    @StateObject private var questionCacheManager = QuestionCacheManager()
    @StateObject private var performanceMonitor = PerformanceMonitor.shared
    
    init() {
        print("CoupleApp: Initialisation de l'application")
        configureAppearance()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .environmentObject(questionCacheManager)
                .environmentObject(performanceMonitor)
                .environmentObject(appState.favoritesService ?? FavoritesService())
                .onAppear {
                    print("CoupleApp: Application démarrée")
                    
                    // Démarrer le monitoring de performance
                    performanceMonitor.startMonitoring()
                    
                    // OPTIMISATION: Préchargement en arrière-plan avec délai
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        Task {
                            print("CoupleApp: Début préchargement optimisé")
                            await questionCacheManager.preloadAllCategories()
                            
                            // Nettoyage mémoire après préchargement
                            questionCacheManager.optimizeMemoryUsage()
                        }
                    }
                }
        }
    }
    
    private func configureAppearance() {
        print("CoupleApp: Configuration de l'apparence")
        
        // Configuration de la navigation bar
        let appearance = UINavigationBarAppearance()
        appearance.configureWithTransparentBackground()
        appearance.titleTextAttributes = [.foregroundColor: UIColor.white]
        appearance.largeTitleTextAttributes = [.foregroundColor: UIColor.white]
        
        UINavigationBar.appearance().standardAppearance = appearance
        UINavigationBar.appearance().compactAppearance = appearance
        UINavigationBar.appearance().scrollEdgeAppearance = appearance
        
        print("CoupleApp: Configuration terminée")
    }
} 