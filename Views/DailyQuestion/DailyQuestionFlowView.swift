import SwiftUI

struct DailyQuestionFlowView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    
    var body: some View {
        Group {
            switch currentRoute {
            case .intro(let showConnect):
                DailyQuestionIntroView(showConnectButton: showConnect)
                    .environmentObject(appState)
                    .onAppear {
                        if !showConnect {
                            // Analytics: intro montrée même si connecté
                            AnalyticsService.shared.track(.introShown(screen: "daily_question"))
                        }
                    }
                
            case .paywall(let day):
                DailyQuestionPaywallView(questionDay: day)
                    .environmentObject(appState)
                
            case .main:
                DailyQuestionMainView()
                    .environmentObject(appState)
                
            case .error(let message):
                DailyQuestionErrorView(
                    message: message,
                    onRetry: {
                        configureServiceIfNeeded()
                    }
                )
                .environmentObject(appState)
                
            case .loading:
                // ✅ REDIRECTION: Plus de DailyQuestionLoadingView, mais redirection vers .main
                // Le chargement est maintenant géré directement dans DailyQuestionMainView
                DailyQuestionMainView()
                    .environmentObject(appState)
            }
        }
        .onAppear {
            configureServiceIfNeeded()
        }
    }
    
    // MARK: - Computed Properties
    
    /// Route actuelle selon l'état de l'application
    private var currentRoute: DailyContentRoute {
        return DailyContentRouteCalculator.calculateRoute(
            for: .dailyQuestion,
            hasConnectedPartner: hasConnectedPartner,
            hasSeenIntro: appState.introFlags.dailyQuestion,
            shouldShowPaywall: shouldShowPaywall,
            paywallDay: currentQuestionDay,
            serviceHasError: false, // DailyQuestionService n'a pas de gestion d'erreur pour l'instant
            serviceErrorMessage: nil,
            serviceIsLoading: dailyQuestionService.isLoading && dailyQuestionService.currentQuestion == nil
        )
    }
    
    /// Vérifier si un partenaire est connecté
    private var hasConnectedPartner: Bool {
        guard let user = appState.currentUser,
              let partnerId = user.partnerId else { return false }
        return !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    /// Calculer le jour actuel de la question
    private var currentQuestionDay: Int {
        if let settings = dailyQuestionService.currentSettings {
            return calculateExpectedDay(from: settings)
        }
        return 1 // Défaut
    }
    
    /// Vérifier si on doit afficher le paywall
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
        // 🚨 CORRECTION CRITIQUE: Vérifier partenaire ET intro vue
        guard let currentUser = appState.currentUser, 
              let partnerId = currentUser.partnerId,
              !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            print("⏳ DailyQuestionFlowView: En attente connexion partenaire")
            return
        }
        
        // Vérifier que l'intro a été vue
        guard appState.introFlags.dailyQuestion else {
            print("⏳ DailyQuestionFlowView: En attente intro utilisateur")
            return
        }
        
        // 🚀 OPTIMISATION CACHE: Vérifier si les données sont déjà disponibles ET récentes
        if let currentQuestion = dailyQuestionService.currentQuestion {
            // Vérifier si la question est pour aujourd'hui
            let today = DateFormatter.dayFormatter.string(from: Date())
            if currentQuestion.scheduledDate == today {
                print("⚡ DailyQuestionFlowView: Question d'aujourd'hui déjà disponible - Pas de reconfiguration")
                return
            } else {
                print("🔄 DailyQuestionFlowView: Question existante mais pas pour aujourd'hui (\(currentQuestion.scheduledDate) vs \(today))")
            }
        }
        
        // ✅ Gérer l'accès freemium AVANT de configurer le service
        appState.freemiumManager?.handleDailyQuestionAccess(currentQuestionDay: currentQuestionDay) {
            // Accès autorisé - Configurer le service
            print("🔄 DailyQuestionFlowView: Configuration service pour récupérer question du jour")
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

 