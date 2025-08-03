import SwiftUI
import FirebaseAnalytics

struct DailyChallengeMainView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyChallengeService = DailyChallengeService.shared
    @StateObject private var savedChallengesService = SavedChallengesService.shared
    @State private var showingSavedChallenges = false
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à la vue favoris
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header avec titre centré et sous-titre freemium
                    HStack {
                        Spacer()
                        VStack(spacing: 4) {
                            Text(NSLocalizedString("daily_challenges_title", tableName: "DailyChallenges", comment: ""))
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)
                            
                            // Sous-titre freemium sous le titre
                            if let subtitle = getDailyChallengeSubtitle() {
                                Text(subtitle)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.top, 4)
                            }
                        }
                        Spacer()
                        
                        // Icône défis sauvegardés en haut à droite
                        Button(action: {
                            showingSavedChallenges = true
                        }) {
                            Image(systemName: "bookmark.fill")
                                .font(.system(size: 20))
                                .foregroundColor(.black)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 8)
                    
                    // Contenu principal
                    if let currentChallenge = dailyChallengeService.currentChallenge {
                        ScrollView {
                            VStack(spacing: 20) {
                                Spacer(minLength: 20)
                                
                                DailyChallengeCardView(
                                    challenge: currentChallenge,
                                    showDeleteButton: false,
                                    onCompleted: {
                                        handleChallengeCompleted(currentChallenge)
                                    },
                                    onSave: {
                                        handleChallengeSave(currentChallenge)
                                    }
                                )
                                .environmentObject(appState)
                                
                                Spacer(minLength: 40)
                            }
                            .padding(.horizontal, 20)
                        }
                    } else if dailyChallengeService.isLoading {
                        // État de chargement
                        VStack(spacing: 20) {
                            Spacer()
                            
                            ProgressView()
                                .scaleEffect(1.2)
                            
                            Text("loading_challenge".localized(tableName: "DailyChallenges"))
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            
                            Spacer()
                        }
                    } else {
                        // Aucun défi disponible
                        VStack(spacing: 20) {
                            Spacer()
                            
                            Image(systemName: "target")
                                .font(.system(size: 60))
                                .foregroundColor(.gray)
                            
                            Text("no_challenge_available".localized(tableName: "DailyChallenges"))
                                .font(.title2)
                                .fontWeight(.semibold)
                            
                            Text("come_back_tomorrow_challenge".localized(tableName: "DailyChallenges"))
                                .font(.body)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                            
                            Spacer()
                        }
                        .padding()
                    }
                }
            }
        }
        .onAppear {
            print("🚀 === DEBUG DAILYCHALLENGE MAIN VIEW - ON APPEAR ===")
            print("🎯 DailyChallengeMainView: onAppear appelé")
            
            // 📅 LOGS DATE/HEURE DEMANDÉS
            let now = Date()
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
            formatter.timeZone = TimeZone.current
            print("🕐 DailyChallengeMainView: Date/Heure actuelle: \(formatter.string(from: now))")
            print("🌍 DailyChallengeMainView: Timezone: \(TimeZone.current.identifier)")
            print("📅 DailyChallengeMainView: Jour de la semaine: \(Calendar.current.component(.weekday, from: now))")
            print("📊 DailyChallengeMainView: Jour du mois: \(Calendar.current.component(.day, from: now))")
            print("📈 DailyChallengeMainView: Mois: \(Calendar.current.component(.month, from: now))")
            print("📉 DailyChallengeMainView: Année: \(Calendar.current.component(.year, from: now))")
            
            configureServices()
            print("✅ DailyChallengeMainView: configureServices() terminé")
            
            // 🎯 GÉNÉRATION DÉFI: Même logique que questions du jour
            let currentChallengeExists = dailyChallengeService.currentChallenge != nil
            print("🎯 DailyChallengeMainView: currentChallenge existe: \(currentChallengeExists)")
            
            if currentChallengeExists {
                print("✅ DailyChallengeMainView: currentChallenge déjà présent: \(dailyChallengeService.currentChallenge?.challengeKey ?? "nil")")
            } else {
                print("❌ DailyChallengeMainView: currentChallenge est nil - Lancement refreshChallenges()")
                // Générer le défi du jour via Firebase Function
                dailyChallengeService.refreshChallenges()
            }
        }
        .refreshable {
            // Permettre de rafraîchir manuellement les défis
            dailyChallengeService.refreshChallenges()
        }
        .sheet(isPresented: $showingSavedChallenges) {
            SavedChallengesView()
                .environmentObject(appState)
        }
    }
    
    private func configureServices() {
        dailyChallengeService.configure(with: appState)
        savedChallengesService.configure(with: appState)
    }
    
    private func handleChallengeCompleted(_ challenge: DailyChallenge) {
        dailyChallengeService.markChallengeAsCompleted(challenge)
        
        // Haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
        
        // Analytics
        Analytics.logEvent("daily_challenge_completed", parameters: [
            "challenge_key": challenge.challengeKey,
            "challenge_day": challenge.challengeDay
        ])
    }
    
    private func handleChallengeSave(_ challenge: DailyChallenge) {
        // Vérifier si déjà sauvegardé
        if savedChallengesService.isChallengeAlreadySaved(challenge) {
            return
        }
        
        savedChallengesService.saveChallenge(challenge)
        
        // Haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .light)
        impactFeedback.impactOccurred()
        
        // Analytics
        Analytics.logEvent("daily_challenge_saved", parameters: [
            "challenge_key": challenge.challengeKey,
            "challenge_day": challenge.challengeDay
        ])
    }
    
    // MARK: - Freemium Subtitle Helpers
    
    /// Helper pour obtenir le sous-titre freemium des défis du jour
    private func getDailyChallengeSubtitle() -> String? {
        // Seulement si l'utilisateur a un partenaire connecté
        guard let user = appState.currentUser,
              let partnerId = user.partnerId,
              !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        
        // Calculer le jour actuel
        let currentDay = calculateCurrentChallengeDay()
        
        // Retourner le sous-titre approprié
        return appState.freemiumManager?.getDailyChallengeSubtitle(for: currentDay)
    }
    
    /// Helper pour calculer le jour actuel du défi
    private func calculateCurrentChallengeDay() -> Int {
        // Estimation basée sur la date de début de relation
        guard let user = appState.currentUser,
              let relationshipStartDate = user.relationshipStartDate else {
            return 1
        }
        
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let startOfDay = calendar.startOfDay(for: relationshipStartDate)
        let startOfToday = calendar.startOfDay(for: Date())
        let daysSinceStart = calendar.dateComponents([.day], from: startOfDay, to: startOfToday).day ?? 0
        
        return daysSinceStart + 1
    }
}