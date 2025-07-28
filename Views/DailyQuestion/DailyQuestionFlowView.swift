import SwiftUI
// 🗑️ SUPPRIMÉ : import UserNotifications
// Plus besoin car on ne gère plus les permissions de notifications pour les questions du jour

struct DailyQuestionFlowView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    // 🗑️ SUPPRIMÉ : Variables liées aux permissions de notifications
    // Plus besoin de hasRequestedNotifications et isCheckingPermission

    var body: some View {
        Group {
            if let user = appState.currentUser,
               let partnerId = user.partnerId,
               !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                // ✅ Partenaire déjà connecté ⇒ Aller directement au chat
                DailyQuestionMainView()
                    .environmentObject(appState)
            } else {
                // ❌ Pas de partenaire ⇒ Intro pour connexion
                    DailyQuestionIntroView()
                        .environmentObject(appState)
            }
        }
        .onAppear {
            // ✅ Toujours configurer le service si un partenaire est déjà connecté
            if let currentUser = appState.currentUser, 
               let partnerId = currentUser.partnerId,
               !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                dailyQuestionService.configure(with: appState)
            }
        }
    }
    
    // 🗑️ FONCTIONS SUPPRIMÉES :
    // - markNotificationsAsRequested()
    // - checkNotificationStatus()
    // Ces fonctions géraient les permissions de notifications pour les questions du jour
    // SUPPRIMÉES car plus besoin de permissions pour accéder aux questions
} 

 