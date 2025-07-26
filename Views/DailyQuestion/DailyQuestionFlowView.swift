import SwiftUI
import UserNotifications

struct DailyQuestionFlowView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    @State private var hasRequestedNotifications = false
    @State private var isCheckingPermission = true

    var body: some View {
        Group {
            if isCheckingPermission {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: "#FD267A")))
            } else {
                // 🎯 NOUVEAU PIPELINE CONFORME APPLE
                if let currentUser = appState.currentUser {
                    if currentUser.partnerId == nil {
                        // Cas 1: Aucun partenaire connecté → Page de connexion OBLIGATOIRE
                        DailyQuestionIntroView()
                            .environmentObject(appState)
                    } else if !hasRequestedNotifications {
                        // Cas 2: Partenaire connecté + jamais demandé notifications → Demander UNE FOIS SEULEMENT
                        DailyQuestionPermissionView(
                            onPermissionGranted: {
                                markNotificationsAsRequested()
                            },
                            onContinueWithoutPermissions: {
                                markNotificationsAsRequested()
                            }
                        )
                        .environmentObject(appState)
                    } else {
                        // Cas 3: Partenaire connecté + notifications déjà demandées → Chat TOUJOURS accessible
                        DailyQuestionMainView()
                            .environmentObject(appState)
                    }
                } else {
                    // Fallback si pas d'utilisateur
                    DailyQuestionIntroView()
                        .environmentObject(appState)
                }
            }
        }
        .onAppear {
            checkNotificationStatus()
            // ✅ Toujours configurer le service si un partenaire est déjà connecté
            if let currentUser = appState.currentUser, currentUser.partnerId != nil {
                dailyQuestionService.configure(with: appState)
            }
        }
    }
    

    
    private func markNotificationsAsRequested() {
        // Marquer que l'utilisateur a été sollicité pour les notifications
        if let currentUser = appState.currentUser {
            let key = "notifications_requested_\(currentUser.id)"
            UserDefaults.standard.set(true, forKey: key)
            hasRequestedNotifications = true
            print("✅ DailyQuestionFlowView: Notifications marquées comme demandées")
        }
    }

    private func checkNotificationStatus() {
        isCheckingPermission = true
        
        // Vérifier si on a déjà demandé les notifications à cet utilisateur
        if let currentUser = appState.currentUser {
            let key = "notifications_requested_\(currentUser.id)"
            hasRequestedNotifications = UserDefaults.standard.bool(forKey: key)
        }
        
        isCheckingPermission = false
        print("🔍 DailyQuestionFlowView: hasRequestedNotifications = \(hasRequestedNotifications)")
    }
} 

 