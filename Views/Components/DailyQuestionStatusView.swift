import SwiftUI
import UserNotifications

struct DailyQuestionStatusView: View {
    @EnvironmentObject var appState: AppState
    @State private var hasNotificationPermission = false
    @State private var isCheckingPermission = true
    
    let onStatusTap: (_ showPartnerMessageOnly: Bool) -> Void
    
    // État des notifications (similaire à la distance)
    private var notificationStatus: String {
        guard let currentUser = appState.currentUser else {
            return "notifications_status_unknown".localized
        }
        
        if currentUser.partnerId == nil {
            return "notifications_status_no_partner".localized
        }
        
        if !hasNotificationPermission {
            return "notifications_status_user_needs".localized
        }
        
        // TODO: Vérifier l'état du partenaire via Firebase
        if shouldShowPartnerMessage {
            return "notifications_status_partner_needs".localized
        }
        
        return "notifications_status_ready".localized
    }
    
    // Vérifier si on doit afficher le message partenaire (similaire à la logique de localisation)
    private var shouldShowPartnerMessage: Bool {
        guard let currentUser = appState.currentUser else { return false }
        
        // Si on a un partenaire connecté ET nos notifications mais qu'on n'est pas prêt
        if let partnerId = currentUser.partnerId, 
           !partnerId.isEmpty,
           hasNotificationPermission {
            // TODO: Vérifier l'état des notifications du partenaire via Firebase
            return true // Pour l'instant, toujours vrai si on a un partenaire
        }
        
        return false
    }
    
    // Vérifier si on doit afficher le flow de permission
    private var shouldShowPermissionFlow: Bool {
        return !hasNotificationPermission && appState.currentUser?.partnerId != nil
    }
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: hasNotificationPermission ? "bell.fill" : "bell.slash.fill")
                .font(.system(size: 20))
                .foregroundColor(hasNotificationPermission ? .green : .orange)
            
            Button(action: {
                if shouldShowPermissionFlow {
                    onStatusTap(shouldShowPartnerMessage)
                }
            }) {
                Text(notificationStatus)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.center)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.white.opacity(0.95))
                            .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 1)
                    )
            }
            .buttonStyle(PlainButtonStyle())
            .allowsHitTesting(shouldShowPermissionFlow || shouldShowPartnerMessage)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.gray.opacity(0.1))
        )
        .onAppear {
            checkNotificationPermission()
        }
    }
    
    private func checkNotificationPermission() {
        isCheckingPermission = true
        
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                self.hasNotificationPermission = settings.authorizationStatus == .authorized
                self.isCheckingPermission = false
                print("🔔 DailyQuestionStatusView: Statut notifications: \(settings.authorizationStatus.rawValue)")
            }
        }
    }
}

#Preview {
    DailyQuestionStatusView { showPartnerMessage in
        print("Status tapped: showPartnerMessage = \(showPartnerMessage)")
    }
    .environmentObject(AppState())
} 