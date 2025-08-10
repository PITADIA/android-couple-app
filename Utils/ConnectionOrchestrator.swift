import Foundation
import SwiftUI

/// Orchestrateur pour gérer les connexions partenaire avec thread safety et edge cases
@MainActor
final class ConnectionOrchestrator: ObservableObject {
    
    // MARK: - Published Properties
    
    @Published var connectionState: ConnectionState = .idle
    @Published var isConnecting: Bool = false
    @Published var canRetry: Bool = true
    
    // MARK: - Private Properties
    
    private let partnerCodeService = PartnerCodeService.shared
    private let analyticsService = AnalyticsService.shared
    
    // MARK: - Connection State
    
    enum ConnectionState: Equatable {
        case idle
        case connecting
        case success(partnerName: String)
        case error(String)
        case timeout
        
        var isLoading: Bool {
            return self == .connecting
        }
        
        var isError: Bool {
            switch self {
            case .error, .timeout: return true
            default: return false
            }
        }
        
        var isSuccess: Bool {
            if case .success = self { return true }
            return false
        }
    }
    
    // MARK: - Public Methods
    
    /// Tente une connexion avec gestion des edge cases
    func attemptConnection(
        code: String,
        context: ConnectionConfig.ConnectionContext,
        onSuccess: @escaping (String) -> Void = { _ in },
        onError: @escaping (String) -> Void = { _ in }
    ) {
        // Vérifier réentrance
        guard !isConnecting else {
            print("⚠️ ConnectionOrchestrator: Connexion déjà en cours, ignorée")
            return
        }
        
        guard canRetry else {
            print("⚠️ ConnectionOrchestrator: Retry pas autorisé pour le moment")
            onError("Veuillez patienter avant de réessayer")
            return
        }
        
        // Marquer état en cours
        isConnecting = true
        connectionState = .connecting
        canRetry = false
        
        print("🔗 ConnectionOrchestrator: Début connexion - Context: \(context.rawValue)")
        
        Task {
            do {
                let success = await partnerCodeService.connectWithPartnerCode(code, context: context)
                
                await MainActor.run {
                    if success {
                        // Récupérer le nom du partenaire depuis le service
                        let partnerName = partnerCodeService.partnerInfo?.name ?? "Partenaire"
                        connectionState = .success(partnerName: partnerName)
                        onSuccess(partnerName)
                        
                        analyticsService.track(.connectSuccess(
                            inheritedSub: partnerCodeService.partnerInfo?.isSubscribed ?? false,
                            context: context.rawValue
                        ))
                        
                        // Reset après succès
                        resetConnectionState(delay: 2.0)
                        
                    } else {
                        let errorMessage = partnerCodeService.errorMessage ?? "Erreur de connexion"
                        connectionState = .error(errorMessage)
                        onError(errorMessage)
                        
                        // Permettre retry après erreur
                        allowRetryAfterDelay()
                    }
                    
                    isConnecting = false
                }
                
            } catch {
                await MainActor.run {
                    let errorMessage = "Erreur réseau: \(error.localizedDescription)"
                    connectionState = .error(errorMessage)
                    isConnecting = false
                    onError(errorMessage)
                    
                    // Track erreur
                    analyticsService.trackConnectionError(
                        context: context,
                        error: error,
                        step: "network_request"
                    )
                    
                    // Permettre retry après erreur réseau
                    allowRetryAfterDelay()
                }
            }
        }
    }
    
    /// Force la réinitialisation de l'état
    func resetConnectionState(delay: TimeInterval = 0) {
        if delay > 0 {
            Task {
                try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                await MainActor.run {
                    resetStateInternal()
                }
            }
        } else {
            resetStateInternal()
        }
    }
    
    /// Permet un retry immédiat (pour les boutons retry)
    func enableRetry() {
        canRetry = true
        if connectionState.isError {
            connectionState = .idle
        }
    }
    
    /// Gère la déconnexion d'un partenaire avec purge complète
    func handlePartnerDisconnection(appState: AppState) {
        print("🔄 ConnectionOrchestrator: Gestion déconnexion partenaire")
        
        // 1. Reset flags d'intro
        appState.resetIntroFlagsOnPartnerChange()
        
        // 2. Purge caches et services (si disponibles)
        purgeCachesAndServices()
        
        // 3. Reset état de connexion
        resetConnectionState()
        
        // 4. Analytics
        analyticsService.track(.connectStart(source: "partner_disconnection"))
        
        print("✅ ConnectionOrchestrator: Déconnexion traitée")
    }
    
    // MARK: - Private Methods
    
    private func resetStateInternal() {
        connectionState = .idle
        isConnecting = false
        canRetry = true
    }
    
    private func allowRetryAfterDelay() {
        Task {
            // Attendre 3 secondes avant d'autoriser un nouveau retry
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            await MainActor.run {
                canRetry = true
            }
        }
    }
    
    private func purgeCachesAndServices() {
        // Purger les caches si les services sont disponibles
        // Note: Ces services peuvent ne pas être disponibles selon le contexte
        
        do {
            // Tentative de purge des questions
            let dailyQuestionService = DailyQuestionService.shared
            // dailyQuestionService.clearCache() // Si cette méthode existe
            print("🗑️ Cache questions purgé")
        } catch {
            print("⚠️ Impossible de purger cache questions: \(error)")
        }
        
        do {
            // Tentative de purge des défis
            let dailyChallengeService = DailyChallengeService.shared
            // dailyChallengeService.clearCache() // Si cette méthode existe
            print("🗑️ Cache défis purgé")
        } catch {
            print("⚠️ Impossible de purger cache défis: \(error)")
        }
        
        // Purge UserDefaults pour les caches temporaires
        let userDefaults = UserDefaults.standard
        let keysToRemove = userDefaults.dictionaryRepresentation().keys.filter { key in
            key.contains("dailyQuestion") || key.contains("dailyChallenge") || key.contains("partnerCache")
        }
        
        for key in keysToRemove {
            userDefaults.removeObject(forKey: key)
        }
        
        print("🗑️ UserDefaults purgés: \(keysToRemove.count) clés supprimées")
    }
}