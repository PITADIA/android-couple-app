import SwiftUI
import FirebaseAnalytics

struct PartnerConnectionSuccessView: View {
    
    // MARK: - Mode Enum
    
    enum Mode {
        case simpleDismiss        // Menu/Photo de profil - fermeture immédiate
        case waitForServices      // Questions/Défis - attendre services
        
        var displayName: String {
            switch self {
            case .simpleDismiss: return "simple"
            case .waitForServices: return "wait_services"
            }
        }
    }
    
    // MARK: - Properties
    
    let partnerName: String
    let mode: Mode
    let context: ConnectionConfig.ConnectionContext
    let onContinue: () -> Void
    
    @State private var showAnimation = false
    @State private var isWaiting = false
    @State private var waitStartTime: Date?
    @State private var isCancelled = false
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    
    // MARK: - Initializer
    
    init(
        partnerName: String,
        mode: Mode = .waitForServices,
        context: ConnectionConfig.ConnectionContext = .onboarding,
        onContinue: @escaping () -> Void
    ) {
        self.partnerName = partnerName
        self.mode = mode
        self.context = context
        self.onContinue = onContinue
    }
    
    var body: some View {
        ZStack {
            // Fond gris clair identique à l'app avec dégradé rose doux en arrière-plan
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea(.all)
            
            // Dégradé rose très doux en arrière-plan
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(hex: "#FD267A").opacity(0.03),
                    Color(hex: "#FF655B").opacity(0.02)
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 30) {
                VStack(spacing: 16) {
                    Text("connection_successful".localized)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.black)
                        .opacity(showAnimation ? 1.0 : 0.0)
                        .animation(.easeInOut(duration: 1.0).delay(0.5), value: showAnimation)
                }
                .padding(.top, 60)
                
                Spacer().frame(height: 50)
                
                // Carte avec connexion partenaire - Style sophistiqué identique au tutoriel
                HStack(spacing: 16) {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.white)
                        .frame(width: 50, height: 50)
                        .background(Color(hex: "#FD267A"))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    
                    Text(String(format: "connected_with".localized, partnerName))
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black)
                    
                    Spacer()
                    
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.green)
                }
                .padding(20)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(Color.white)
                        .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                        .shadow(color: Color.black.opacity(0.04), radius: 1, x: 0, y: 1)
                )
                .padding(.horizontal, 20)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(1.2), value: showAnimation)
                
                Spacer().frame(height: 40)
                
                // Instructions sur les nouvelles fonctionnalités
                VStack(alignment: .leading, spacing: 12) {
                    Text("new_features_unlocked".localized)
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.black)
                        .padding(.bottom, 16)
                    
                    HStack(spacing: 12) {
                        Text("💕")
                            .font(.system(size: 24))
                        
                        Text("shared_favorite_questions".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                    }
                    
                    HStack(spacing: 12) {
                        Text("📍")
                            .font(.system(size: 24))
                        
                        Text("real_time_distance".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                    }
                    
                    HStack(spacing: 12) {
                        Text("⭐")
                            .font(.system(size: 24))
                        
                        Text("shared_journal".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                    }
                    
                    HStack(spacing: 12) {
                        Text("📱")
                            .font(.system(size: 24))
                        
                        Text("widgets_available".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black)
                    }
                    
                    HStack(spacing: 12) {
                        Text("💬")
                            .font(.system(size: 24))
                        
                        Text("daily_questions_available".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                    }
                }
                .padding(.horizontal, 20)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(1.5), value: showAnimation)
                
                Spacer()
                
                // Bouton Continuer - Style adapté selon le mode
                Button(action: {
                    Task {
                        await handleContinue()
                    }
                }) {
                    HStack(spacing: 12) {
                        if isWaiting && mode == .waitForServices {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.9)
                        }
                        
                        Text(buttonText)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(
                        Color(hex: "#FD267A")
                            .opacity((isWaiting && mode == .waitForServices) ? 0.7 : 1.0)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 28))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(2.0), value: showAnimation)
            }
        }
        .onAppear {
            print("🎉 PartnerConnectionSuccessView: Vue apparue pour partenaire: \(partnerName) - Mode: \(mode.displayName)")
            showAnimation = true
            
            // Analytics
            AnalyticsService.shared.track(.successViewShown(
                mode: mode.displayName,
                context: context.rawValue
            ))
        }
        .onDisappear {
            // Annuler l'attente si la vue disparaît
            isCancelled = true
        }
    }
    
    // MARK: - Computed Properties
    
    /// Texte du bouton selon le mode et l'état
    private var buttonText: String {
        switch mode {
        case .simpleDismiss:
            return "continue".localized
        case .waitForServices:
            if isWaiting {
                return "preparation_in_progress".localized
            } else {
                return "continue".localized
            }
        }
    }
    
    // MARK: - Methods
    
    /// Gère le clic sur "Continuer" selon le mode
    @MainActor
    private func handleContinue() async {
        let startTime = Date()
        print("🎉 PartnerConnectionSuccessView: Bouton Continuer pressé - Mode: \(mode.displayName)")
        
        switch mode {
        case .simpleDismiss:
            // Mode simple : fermeture immédiate
            print("🎉 Fermeture immédiate (mode simple)")
            AnalyticsService.shared.track(.successViewContinue(
                mode: mode.displayName,
                waitTime: 0
            ))
            onContinue()
            
        case .waitForServices:
            // Mode attente : préparer les services
            isWaiting = true
            waitStartTime = startTime
            
            let success = await waitForServicesReady(timeout: ConnectionConfig.preparingMaxTimeout)
            let totalWaitTime = Date().timeIntervalSince(startTime)
            
            await MainActor.run {
                isWaiting = false
                
                // Analytics
                AnalyticsService.shared.track(.successViewContinue(
                    mode: mode.displayName,
                    waitTime: totalWaitTime
                ))
                
                if !success {
                    AnalyticsService.shared.track(.readyTimeout(
                        duration: totalWaitTime,
                        context: context.rawValue
                    ))
                }
                
                print("🎉 PartnerConnectionSuccessView: Préparation terminée - Fermeture (success: \(success))")
                onContinue()
            }
        }
    }
    
    /// Attendre que les services soient prêts avec timeout
    private func waitForServicesReady(timeout: TimeInterval) async -> Bool {
        let start = Date()
        
        // Délai minimum pour l'UX
        let minimumWait = ConnectionConfig.preparingMinDuration
        let minimumEndTime = start.addingTimeInterval(minimumWait)
        
        // Vérification périodique de readiness
        while Date().timeIntervalSince(start) < timeout && !isCancelled {
            let isServiceReady = !dailyQuestionService.isLoading && 
                                !dailyQuestionService.isOptimizing &&
                                (dailyQuestionService.currentQuestion != nil || dailyQuestionService.allQuestionsExhausted)
            
            if isServiceReady {
                // Attendre au minimum le délai UX
                let now = Date()
                if now < minimumEndTime {
                    let remainingMinWait = minimumEndTime.timeIntervalSince(now)
                    print("🎉 Service prêt, mais attente minimum restante: \(remainingMinWait)s")
                    try? await Task.sleep(nanoseconds: UInt64(remainingMinWait * 1_000_000_000))
                }
                
                print("🎉 Services prêts après \(Date().timeIntervalSince(start))s")
                return true
            }
            
            // Attendre avant la prochaine vérification
            try? await Task.sleep(nanoseconds: UInt64(ConnectionConfig.readinessCheckInterval * 1_000_000_000))
        }
        
        // Timeout ou annulation
        let duration = Date().timeIntervalSince(start)
        print("⚠️ Timeout ou annulation après \(duration)s (cancelled: \(isCancelled))")
        return false
    }
} 