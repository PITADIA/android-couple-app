import SwiftUI
import Firebase
import StoreKit

@main
struct CoupleApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var appState = AppState()
    @StateObject private var questionCacheManager = QuestionCacheManager.shared
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
                .environmentObject(PackProgressService.shared)
                .onAppear {
                    print("CoupleApp: Application démarrée")
                    
                    // Démarrer le monitoring de performance
                    performanceMonitor.startMonitoring()
                    
                    // OPTIMISATION: Préchargement ultra-rapide avec le nouveau système
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        print("CoupleApp: Début préchargement ultra-optimisé")
                        QuestionDataManager.shared.preloadEssentialCategories()
                        
                        // Préchargement en arrière-plan pour les anciennes données (compatibilité)
                        Task {
                            questionCacheManager.preloadAllCategories()
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