import Foundation
import FirebaseMessaging
import FirebaseAuth
import FirebaseFirestore
import UserNotifications
import UIKit

class FCMService: NSObject, ObservableObject {
    static let shared = FCMService()
    
    @Published var fcmToken: String?
    @Published var isConfigured = false
    
    private let db = Firestore.firestore()
    private var currentUserId: String?
    
    override init() {
        super.init()
        print("🔔 FCMService: Initialisation")
        setupFCM()
    }
    
    // MARK: - Configuration
    
    private func setupFCM() {
        print("🔔 FCMService: Configuration FCM")
        
        // Configurer Messaging
        Messaging.messaging().delegate = self
        
        // Vérifier si l'app est déjà enregistrée pour les notifications
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            print("🔔 FCMService: Statut notifications au setup: \(settings.authorizationStatus.rawValue)")
            
            if settings.authorizationStatus == .authorized {
                print("🔔 FCMService: Notifications autorisées - demande d'enregistrement APNS")
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            } else {
                print("🔔 FCMService: Notifications non autorisées - pas d'enregistrement APNS")
            }
        }
        
        // Observer les changements d'authentification
        _ = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            if let user = user {
                print("🔔 FCMService: Utilisateur connecté: \(user.uid)")
                self?.currentUserId = user.uid
                
                // Attendre un peu que l'APNS token soit disponible
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    self?.requestTokenAndSave()
                }
            } else {
                print("🔔 FCMService: Utilisateur déconnecté")
                self?.currentUserId = nil
                self?.fcmToken = nil
            }
        }
        
        isConfigured = true
        print("🔔 FCMService: Configuration terminée")
    }
    
    // MARK: - Token Management
    
    func requestTokenAndSave() {
        print("🔔 FCMService: Demande de token FCM")
        
        // Vérifier d'abord l'état de l'APNS token
        print("🔔 FCMService: Vérification APNS token...")
        
        // Vérifier si on a les permissions
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            print("🔔 FCMService: Permissions notifications: \(settings.authorizationStatus.rawValue)")
            
            if settings.authorizationStatus != .authorized {
                print("❌ FCMService: Notifications non autorisées - impossible d'obtenir token FCM")
                return
            }
            
            // Enregistrer pour les notifications remote si pas déjà fait
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
                
                // Attendre un peu puis demander le token FCM
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    self.attemptFCMTokenRequest()
                }
            }
        }
    }
    
    private func attemptFCMTokenRequest() {
        print("🔔 FCMService: Tentative récupération token FCM...")
        
        Messaging.messaging().token { [weak self] token, error in
            if let error = error {
                print("❌ FCMService: Erreur token FCM - \(error)")
                
                // Si erreur APNS, essayer de re-enregistrer
                if error.localizedDescription.contains("APNS") {
                    print("🔄 FCMService: Erreur APNS détectée - tentative re-enregistrement")
                    DispatchQueue.main.async {
                        UIApplication.shared.registerForRemoteNotifications()
                    }
                }
                return
            }
            
            guard let token = token else {
                print("❌ FCMService: Token FCM nil")
                return
            }
            
            print("✅ FCMService: Token FCM reçu - \(token.prefix(20))...")
            
            DispatchQueue.main.async {
                self?.fcmToken = token
                self?.saveTokenToFirestore(token)
            }
        }
    }
    
    private func saveTokenToFirestore(_ token: String) {
        guard let userId = currentUserId else {
            print("❌ FCMService: Pas d'utilisateur connecté")
            return
        }
        
        print("🔔 FCMService: Sauvegarde token pour utilisateur: \(userId)")
        
        let tokenData: [String: Any] = [
            "fcmToken": token,
            "tokenUpdatedAt": Timestamp(date: Date()),
            "deviceInfo": [
                "platform": "iOS",
                "appVersion": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown",
                "deviceModel": UIDevice.current.model
            ]
        ]
        
        db.collection("users").document(userId).updateData(tokenData) { error in
            if let error = error {
                print("❌ FCMService: Erreur sauvegarde token - \(error)")
            } else {
                print("✅ FCMService: Token sauvegardé avec succès")
            }
        }
    }
    
    // MARK: - Token Refresh
    
    func refreshToken() {
        print("🔔 FCMService: Rafraîchissement token FCM")
        
        // Vérifier d'abord si on a un token APNS
        if Messaging.messaging().apnsToken == nil {
            print("⚠️ FCMService: Pas de token APNS - enregistrement...")
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
            
            // Attendre un peu puis essayer
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                self.requestTokenAndSave()
            }
        } else {
            print("✅ FCMService: Token APNS disponible - demande FCM directe")
            attemptFCMTokenRequest()
        }
    }
    
    // MARK: - Notifications Permission
    
    func requestNotificationPermission() async -> Bool {
        print("🔔 FCMService: Demande permission notifications")
        
        let center = UNUserNotificationCenter.current()
        
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            print("🔔 FCMService: Permission accordée: \(granted)")
            
            if granted {
                await MainActor.run {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
            
            return granted
        } catch {
            print("❌ FCMService: Erreur permission - \(error)")
            return false
        }
    }
    
    // MARK: - Topic Subscription
    
    func subscribeToTopic(_ topic: String) {
        print("🔔 FCMService: Abonnement au topic: \(topic)")
        
        Messaging.messaging().subscribe(toTopic: topic) { error in
            if let error = error {
                print("❌ FCMService: Erreur abonnement topic - \(error)")
            } else {
                print("✅ FCMService: Abonné au topic: \(topic)")
            }
        }
    }
    
    func unsubscribeFromTopic(_ topic: String) {
        print("🔔 FCMService: Désabonnement du topic: \(topic)")
        
        Messaging.messaging().unsubscribe(fromTopic: topic) { error in
            if let error = error {
                print("❌ FCMService: Erreur désabonnement topic - \(error)")
            } else {
                print("✅ FCMService: Désabonné du topic: \(topic)")
            }
        }
    }
}

// MARK: - MessagingDelegate

extension FCMService: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        print("🔔 FCMService: Nouveau token reçu")
        
        guard let token = fcmToken else {
            print("❌ FCMService: Token nil dans delegate")
            return
        }
        
        DispatchQueue.main.async {
            self.fcmToken = token
            self.saveTokenToFirestore(token)
        }
    }
} 