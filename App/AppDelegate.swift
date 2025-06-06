import UIKit
import StoreKit
import FirebaseCore

class AppDelegate: NSObject, UIApplicationDelegate {
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        print("🔥 AppDelegate: Démarrage de l'application")
        
        // Configuration Firebase
        print("🔥 AppDelegate: Configuration Firebase...")
        FirebaseApp.configure()
        print("🔥 AppDelegate: Firebase configuré avec succès")
        
        // Configuration des achats in-app
        print("🔥 AppDelegate: Configuration StoreKit...")
        configureStoreKit()
        print("🔥 AppDelegate: StoreKit configuré")
        
        print("🔥 AppDelegate: Initialisation terminée")
        return true
    }
    
    private func configureStoreKit() {
        print("🔥 AppDelegate: Ajout de l'observateur StoreKit")
        // Configuration pour les achats in-app
        SKPaymentQueue.default().add(SubscriptionService.shared)
    }
    
    func applicationWillTerminate(_ application: UIApplication) {
        print("🔥 AppDelegate: Arrêt de l'application")
        // Nettoyer les observateurs StoreKit
        SKPaymentQueue.default().remove(SubscriptionService.shared)
        print("🔥 AppDelegate: Observateurs StoreKit supprimés")
    }
} 