import SwiftUI

struct DailyQuestionFlowView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyQuestionService = DailyQuestionService.shared

    var body: some View {
        Group {
            if let user = appState.currentUser,
               let partnerId = user.partnerId,
               !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                
                // ✅ Partenaire connecté - Vérifier accès freemium
                if shouldShowPaywall {
                    DailyQuestionPaywallView(questionDay: currentQuestionDay)
                        .environmentObject(appState)
                } else {
                    DailyQuestionMainView()
                        .environmentObject(appState)
                }
            } else {
                // ❌ Pas de partenaire ⇒ Intro pour connexion
                DailyQuestionIntroView()
                    .environmentObject(appState)
            }
        }
        .onAppear {
            configureServiceIfNeeded()
        }
    }
    
    // NOUVEAU: Calculer le jour actuel de la question
    private var currentQuestionDay: Int {
        // Récupérer le jour depuis DailyQuestionService ou settings
        if let settings = dailyQuestionService.currentSettings {
            return calculateExpectedDay(from: settings)
        }
        return 1 // Défaut
    }
    
    // NOUVEAU: Vérifier si on doit afficher le paywall
    private var shouldShowPaywall: Bool {
        let isSubscribed = appState.currentUser?.isSubscribed ?? false
        if isSubscribed {
            return false // Premium = pas de paywall
        }
        
        // Utiliser FreemiumManager pour vérifier l'accès
        guard let freemiumManager = appState.freemiumManager else { return false }
        return !freemiumManager.canAccessDailyQuestion(for: currentQuestionDay)
    }
    
    private func configureServiceIfNeeded() {
        guard let currentUser = appState.currentUser, 
              let partnerId = currentUser.partnerId,
              !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }
        
        // ✅ Gérer l'accès freemium AVANT de configurer le service
        appState.freemiumManager?.handleDailyQuestionAccess(currentQuestionDay: currentQuestionDay) {
            // Accès autorisé - Configurer le service
            dailyQuestionService.configure(with: appState)
        }
    }
    
    // Helper pour calculer le jour attendu (même logique que le backend)
    private func calculateExpectedDay(from settings: DailyQuestionSettings) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let startOfDay = calendar.startOfDay(for: settings.startDate)
        let startOfToday = calendar.startOfDay(for: Date())
        let daysSinceStart = calendar.dateComponents([.day], from: startOfDay, to: startOfToday).day ?? 0
        
        return daysSinceStart + 1 // Jour 1, 2, 3, etc.
    }
}

 