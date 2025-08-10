import SwiftUI
import FirebaseAnalytics

struct DailyChallengeFlowView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyChallengeService = DailyChallengeService.shared
    
    var body: some View {
        Group {
            switch currentRoute {
            case .intro(let showConnect):
                DailyChallengeIntroView(showConnectButton: showConnect)
                    .environmentObject(appState)
                    .onAppear {
                        if !showConnect {
                            // Analytics: intro montrée même si connecté
                            AnalyticsService.shared.track(.introShown(screen: "daily_challenge"))
                        }
                    }
                
            case .paywall(let day):
                DailyChallengePaywallView(challengeDay: day)
                    .environmentObject(appState)
                
            case .main:
                DailyChallengeMainView()
                    .environmentObject(appState)
                
            case .error(let message):
                DailyChallengeErrorView(
                    message: message,
                    onRetry: {
                        configureServiceIfNeeded()
                    }
                )
                .environmentObject(appState)
                
            case .loading:
                DailyChallengeLoadingView()
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
            for: .dailyChallenge,
            hasConnectedPartner: hasConnectedPartner,
            hasSeenIntro: appState.introFlags.dailyChallenge,
            shouldShowPaywall: shouldShowPaywall,
            paywallDay: currentChallengeDay,
            serviceHasError: false, // DailyChallengeService n'a pas de gestion d'erreur pour l'instant
            serviceErrorMessage: nil,
            serviceIsLoading: dailyChallengeService.isLoading && dailyChallengeService.currentChallenge == nil
        )
    }
    
    /// Vérifier si un partenaire est connecté
    private var hasConnectedPartner: Bool {
        guard let user = appState.currentUser,
              let partnerId = user.partnerId else { return false }
        return !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    /// Calculer le jour actuel du défi
    private var currentChallengeDay: Int {
        if let settings = dailyChallengeService.currentSettings {
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
        return !freemiumManager.canAccessDailyChallenge(for: currentChallengeDay)
    }
    
    private func configureServiceIfNeeded() {
        // 🚨 CORRECTION CRITIQUE: Vérifier partenaire ET intro vue
        guard let currentUser = appState.currentUser, 
              let partnerId = currentUser.partnerId,
              !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            print("⏳ DailyChallengeFlowView: En attente connexion partenaire")
            return
        }
        
        // Vérifier que l'intro a été vue
        guard appState.introFlags.dailyChallenge else {
            print("⏳ DailyChallengeFlowView: En attente intro utilisateur")
            return
        }
        
        // 🚀 OPTIMISATION CACHE: Vérifier si les données sont déjà disponibles ET récentes
        if let currentChallenge = dailyChallengeService.currentChallenge {
            // Vérifier si le défi est pour aujourd'hui
            let today = Date()
            let calendar = Calendar.current
            let challengeDate = currentChallenge.scheduledDate
            
            if calendar.isDate(challengeDate, inSameDayAs: today) {
                print("⚡ DailyChallengeFlowView: Défi d'aujourd'hui déjà disponible - Pas de reconfiguration")
                return
            } else {
                let formatter = DateFormatter()
                formatter.dateStyle = .short
                print("🔄 DailyChallengeFlowView: Défi existant mais pas pour aujourd'hui (\(formatter.string(from: challengeDate)) vs \(formatter.string(from: today)))")
            }
        }
        
        // ✅ Gérer l'accès freemium AVANT de configurer le service
        appState.freemiumManager?.handleDailyChallengeAccess(currentChallengeDay: currentChallengeDay) {
            // Accès autorisé - Configurer le service
            print("🔄 DailyChallengeFlowView: Configuration service pour récupérer défi du jour")
            dailyChallengeService.configure(with: appState)
        }
    }
    
    private func calculateExpectedDay(from settings: DailyChallengeSettings) -> Int {
        let calendar = Calendar.current
        let startOfToday = calendar.startOfDay(for: Date())
        let startOfStartDate = calendar.startOfDay(for: settings.startDate)
        
        let daysSinceStart = calendar.dateComponents([.day], from: startOfStartDate, to: startOfToday).day ?? 0
        return max(1, daysSinceStart + 1)
    }
}



// MARK: - DailyChallengePaywallView

struct DailyChallengePaywallView: View {
    @EnvironmentObject var appState: AppState
    let challengeDay: Int
    @State private var showSubscriptionSheet = false
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à la page journal
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header avec titre
                    HStack {
                        Spacer()
                        
                        // Titre
                        VStack(spacing: 4) {
                            Text("paywall_page_title_challenges".localized(tableName: "DailyChallenges"))
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)
                        }
                        
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 40) // ✅ PLUS D'ESPACE EN HAUT
                    .padding(.bottom, 100) // Espace augmenté entre titre et image
                    
                    // Contenu principal avec carte floutée
                    VStack(spacing: 30) {
                        // Titre principal
                        Text("paywall_challenges_title".localized(tableName: "DailyChallenges"))
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                        
                        // Carte de défi floutée
                        ZStack {
                            // Carte simulée avec texte simulé
                            VStack(spacing: 0) {
                                // Header de la carte
                                VStack(spacing: 8) {
                                    Text("daily_challenges_title".localized(tableName: "DailyChallenges"))
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundColor(.white)
                                        .multilineTextAlignment(.center)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 20)
                                .background(
                                    LinearGradient(
                                        gradient: Gradient(colors: [
                                            Color(red: 1.0, green: 0.4, blue: 0.6),
                                            Color(red: 1.0, green: 0.6, blue: 0.8)
                                        ]),
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                
                                // Corps de la carte avec texte simulé
                                VStack(spacing: 30) {
                                    Spacer()
                                    
                                    Text("Envoyez-lui un message pour lui dire pourquoi vous êtes reconnaissant de l'avoir dans votre vie aujourd'hui et partagez trois choses spécifiques que vous appréciez chez lui.")
                                        .font(.system(size: 22, weight: .medium))
                                        .foregroundColor(.white)
                                        .multilineTextAlignment(.center)
                                        .lineSpacing(6)
                                        .padding(.horizontal, 30)
                                        .blur(radius: 8) // FLOU APPLIQUÉ AU TEXTE
                                    
                                    Spacer()
                                    
                                    // Logo/Branding en bas
                                    HStack(spacing: 8) {
                                        Image(systemName: "target")
                                            .font(.system(size: 20))
                                            .foregroundColor(.white.opacity(0.9))
                                        
                                        Text("Jour 4")
                                            .font(.system(size: 16, weight: .semibold))
                                            .foregroundColor(.white.opacity(0.9))
                                    }
                                    .blur(radius: 6) // FLOU APPLIQUÉ AU LOGO
                                    .padding(.bottom, 30)
                                }
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .background(
                                    LinearGradient(
                                        gradient: Gradient(colors: [
                                            Color(red: 0.2, green: 0.1, blue: 0.15),
                                            Color(red: 0.4, green: 0.2, blue: 0.3),
                                            Color(red: 0.6, green: 0.3, blue: 0.2)
                                        ]),
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                )
                            }
                            .frame(maxWidth: .infinity)
                            .cornerRadius(20)
                            .shadow(color: .black.opacity(0.3), radius: 10, x: 0, y: 5)
                            .padding(.horizontal, 20)
                            
                            // Overlay flou et cadenas - SOLUTION AMÉLIORÉE
                            VStack(spacing: 0) {
                                Color.clear
                                    .frame(height: 60) // Espace pour le header non flouté
                                
                                ZStack {
                                    // Fond coloré qui imite la carte défis + flou
                                    LinearGradient(
                                        gradient: Gradient(colors: [
                                            Color(red: 0.2, green: 0.1, blue: 0.15).opacity(0.95),
                                            Color(red: 0.4, green: 0.2, blue: 0.3).opacity(0.95),
                                            Color(red: 0.6, green: 0.3, blue: 0.2).opacity(0.95)
                                        ]),
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                    .blur(radius: 15) // VRAI EFFET DE FLOU
                                    
                                    // Overlay Material pour l'effet glassmorphism
                                    Rectangle()
                                        .fill(.ultraThickMaterial)
                                        .opacity(0.8)
                                    
                                    // Icône coeur
                                    Text("💕")
                                        .font(.system(size: 60))
                                        .shadow(color: .black.opacity(0.3), radius: 2, x: 0, y: 1)
                                }
                            }
                            .cornerRadius(20)
                            .padding(.horizontal, 20)
                        }
                        
                        // Sous-titre
                        Text("paywall_challenges_subtitle".localized(tableName: "DailyChallenges"))
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.7))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 30)
                            .lineLimit(nil) // ✅ PERMETTRE AFFICHAGE COMPLET
                            .fixedSize(horizontal: false, vertical: true) // ✅ TAILLE FLEXIBLE
                    }
                    
                    Spacer(minLength: 40) // ✅ PLUS D'ESPACE AVANT LE BOUTON
                    
                    // Bouton principal conditionnel
                    VStack {
                        Button {
                            showSubscriptionSheet = true
                            
                            // Analytics
                            Analytics.logEvent("cta_premium_clicked", parameters: [
                                "source": "daily_challenge_paywall",
                                "challenge_day": challengeDay
                            ])
                        } label: {
                            Text("paywall_continue_button".localized(tableName: "DailyChallenges"))
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 24)
                                .frame(height: 56)
                                .background(
                                    RoundedRectangle(cornerRadius: 28)
                                        .fill(Color(hex: "#FD267A"))
                                )
                        }
                        .padding(.bottom, 160) // Espace pour le menu du bas
                    }
                }
            }
        }

        .sheet(isPresented: $showSubscriptionSheet) {
            SubscriptionView()
                .environmentObject(appState)
        }
    }
}

