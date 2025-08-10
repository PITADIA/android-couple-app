import Foundation
import FirebaseAnalytics

/// Service d'analytics avancé pour la connexion partenaire
final class AnalyticsService {
    static let shared = AnalyticsService()
    
    private init() {}
    
    // MARK: - Analytics Methods
    
    /// Track un événement de connexion partenaire
    func track(_ event: ConnectionConfig.AnalyticsEvent) {
        let eventName = event.eventName
        let parameters = event.parameters
        
        // Firebase Analytics
        Analytics.logEvent(eventName, parameters: parameters)
        
        // Console logging pour debug
        print("📊 Analytics: \(eventName) - \(parameters)")
    }
    
    /// Track spécifiquement pour les diagnostics de performance
    func trackPerformanceDiagnostic(
        context: ConnectionConfig.ConnectionContext,
        duration: TimeInterval,
        isLoading: Bool,
        isOptimizing: Bool,
        hasCurrentQuestion: Bool,
        networkConnected: Bool
    ) {
        let diagnostics: [String: Any] = [
            "context": context.rawValue,
            "duration": duration,
            "is_loading": isLoading,
            "is_optimizing": isOptimizing,
            "has_current_question": hasCurrentQuestion,
            "network_connected": networkConnected,
            "timestamp": Date().timeIntervalSince1970
        ]
        
        Analytics.logEvent("performance_diagnostic", parameters: diagnostics)
        print("🔍 Performance Diagnostic: \(diagnostics)")
    }
    
    /// Track les erreurs de connexion
    func trackConnectionError(
        context: ConnectionConfig.ConnectionContext,
        error: Error,
        step: String
    ) {
        let parameters: [String: Any] = [
            "context": context.rawValue,
            "error_description": error.localizedDescription,
            "step": step,
            "timestamp": Date().timeIntervalSince1970
        ]
        
        Analytics.logEvent("connection_error", parameters: parameters)
        print("❌ Connection Error: \(parameters)")
    }
    
    /// Track les timeouts avec contexte
    func trackTimeout(
        context: ConnectionConfig.ConnectionContext,
        duration: TimeInterval,
        expectedDuration: TimeInterval
    ) {
        track(.readyTimeout(duration: duration, context: context.rawValue))
        
        // Analytics supplémentaires pour les timeouts
        let timeoutParameters: [String: Any] = [
            "context": context.rawValue,
            "actual_duration": duration,
            "expected_duration": expectedDuration,
            "timeout_ratio": duration / expectedDuration
        ]
        
        Analytics.logEvent("timeout_detailed", parameters: timeoutParameters)
    }
}