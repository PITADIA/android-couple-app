import UIKit
import StoreKit
import FirebaseCore
import FirebaseMessaging
import UserNotifications
import FirebaseAppCheck
import RevenueCat

/// Factory personnalisée pour App Check Provider
class CoupleAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        if #available(iOS 14.0, *) {
            return AppAttestProvider(app: app)
        } else {
            return DeviceCheckProvider(app: app)
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        print("🔥 AppDelegate: Démarrage de l'application")
        
        // 🛡️ Configuration App Check AVANT Firebase (MODE SURVEILLANCE)
        print("🛡️ AppDelegate: Configuration App Check...")
        configureAppCheck()
        print("🛡️ AppDelegate: App Check configuré en mode surveillance")
        
        // Configuration Firebase
        print("🔥 AppDelegate: Configuration Firebase...")
        FirebaseApp.configure()
        print("🔥 AppDelegate: Firebase configuré avec succès")
        
        // Configuration RevenueCat (tracking uniquement)
        print("💰 AppDelegate: Configuration RevenueCat...")
        configureRevenueCat()
        print("💰 AppDelegate: RevenueCat configuré")
        
        // Configuration des achats in-app
        print("🔥 AppDelegate: Configuration StoreKit...")
        configureStoreKit()
        print("🔥 AppDelegate: StoreKit configuré")
        
        // Configure User Notifications
        configureUserNotifications()
        
        print("🔥 AppDelegate: Initialisation terminée")
        return true
    }
    
    private func configureRevenueCat() {
        print("💰 AppDelegate: Initialisation RevenueCat pour tracking...")
        
        // Configuration RevenueCat en mode tracking uniquement
        Purchases.logLevel = .info
        Purchases.configure(withAPIKey: "appl_pMKnixURdQHqWmKnsoicGCXeiJL")
        
        print("💰 AppDelegate: RevenueCat initialisé - mode tracking actif")
        print("💰 AppDelegate: Les transactions StoreKit seront automatiquement trackées")
    }
    
    private func configureStoreKit() {
        print("🔥 AppDelegate: Ajout de l'observateur StoreKit")
        // Configuration pour les achats in-app
        SKPaymentQueue.default().add(SubscriptionService.shared)
    }
    
    private func configureUserNotifications() {
        print("🔥 AppDelegate: Configuration notifications…")
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        print("🔥 AppDelegate: UNUserNotificationCenter delegate défini")
    }
    
    /// 🛡️ Configuration App Check en mode SURVEILLANCE (aucun blocage)
    private func configureAppCheck() {
        print("🛡️ AppCheck: Initialisation du provider...")
        
        #if DEBUG
        // En debug : utiliser le debug provider
        let providerFactory = AppCheckDebugProviderFactory()
        print("🛡️ AppCheck: Mode DEBUG - Provider de développement")
        #else
        // En production : utiliser App Attest (déjà configuré dans Firebase)
        let providerFactory = CoupleAppCheckProviderFactory()
        print("🛡️ AppCheck: Mode PRODUCTION - App Attest provider")
        #endif
        
        AppCheck.setAppCheckProviderFactory(providerFactory)
        
        // 🎯 IMPORTANT: Mode surveillance uniquement - aucun blocage
        print("🛡️ AppCheck: ⚠️ Mode SURVEILLANCE activé - aucun service ne sera bloqué")
        print("🛡️ AppCheck: Les métriques seront collectées pendant 2-3 semaines")
        print("🛡️ AppCheck: Activation de l'enforcement sera faite manuellement plus tard")
    }
    
    func applicationWillTerminate(_ application: UIApplication) {
        print("🔥 AppDelegate: Arrêt de l'application")
        // Nettoyer les observateurs StoreKit
        SKPaymentQueue.default().remove(SubscriptionService.shared)
        print("🔥 AppDelegate: Observateurs StoreKit supprimés")
    }
    
    /// Appelé quand l'app devient active (premier plan)
    func applicationDidBecomeActive(_ application: UIApplication) {
        print("🔔 AppDelegate: App devenue active")
        
        // 🎯 DOUBLE NOTIFICATION FIX: Nettoyer spécifiquement les notifications de messages
        Task {
            let center = UNUserNotificationCenter.current()
            let deliveredNotifications = await center.deliveredNotifications()
            
            // Trouver toutes les notifications de messages
            let messageNotificationIds = deliveredNotifications
                .filter { $0.request.identifier.hasPrefix("new_message_") }
                .map { $0.request.identifier }
            
            if !messageNotificationIds.isEmpty {
                print("🗑️ AppDelegate: Suppression \(messageNotificationIds.count) notifications de messages obsolètes")
                center.removeDeliveredNotifications(withIdentifiers: messageNotificationIds)
            }
            
            // Nettoyer aussi toutes les notifications en attente et le badge
            await MainActor.run {
                BadgeManager.clearAllNotificationsAndBadge()
            }
        }
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    // Afficher les notifications même lorsque l'app est au premier plan (bannière + son + badge)
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        print("🔔 AppDelegate: willPresent notification - ID: \(notification.request.identifier)")
        
        // 🎯 FIX DOUBLE NOTIFICATION: Ne pas afficher les notifications de message quand l'app est au premier plan
        if notification.request.identifier.hasPrefix("new_message_") {
            print("🔔 AppDelegate: Notification de message détectée - suppression pour éviter double affichage")
            // Supprimer immédiatement cette notification
            center.removeDeliveredNotifications(withIdentifiers: [notification.request.identifier])
            // Ne pas afficher la bannière (app déjà ouverte)
            completionHandler([])
            return
        }
        
        // Afficher les autres types de notifications normalement
        completionHandler([.banner, .list, .sound, .badge])
    }
    
    // Gérer la réponse de l'utilisateur aux notifications
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        print("🔔 AppDelegate: didReceive response - ID: \(response.notification.request.identifier)")
        
        // Gérer les notifications de messages
        let userInfo = response.notification.request.content.userInfo
        if let questionId = userInfo["questionId"] as? String {
            print("🔔 AppDelegate: Ouverture question: \(questionId)")
            // TODO: Naviguer vers la question
        }
        
        completionHandler()
    }
    
    // Gérer les notifications push reçues
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        print("🔔 AppDelegate: Token APNS reçu - \(deviceToken.count) bytes")
        print("🔔 AppDelegate: Token APNS hex: \(deviceToken.map { String(format: "%02.2hhx", $0) }.joined())")
        
        // Assigner le token APNS à Firebase Messaging
        Messaging.messaging().apnsToken = deviceToken
        
        // Déclencher une nouvelle tentative de récupération du token FCM
        print("🔔 AppDelegate: Token APNS assigné - demande refresh FCM")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            FCMService.shared.refreshToken()
        }
    }
    
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("❌ AppDelegate: Erreur enregistrement APNS - \(error)")
        print("❌ AppDelegate: Description: \(error.localizedDescription)")
    }
} 