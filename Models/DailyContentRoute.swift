import Foundation

/// Machine d'états pour déterminer quelle vue afficher dans les flows Daily Question/Challenge
enum DailyContentRoute: Equatable {
    case intro(showConnect: Bool)    // Page d'introduction (connecté ou non)
    case paywall(day: Int)          // Paywall freemium
    case main                       // Vue principale (question ou défi)
    case error(String)              // État d'erreur avec message
    case loading                    // Chargement en cours
    
    // MARK: - Computed Properties
    
    var description: String {
        switch self {
        case .intro(let showConnect):
            return "intro(showConnect: \(showConnect))"
        case .paywall(let day):
            return "paywall(day: \(day))"
        case .main:
            return "main"
        case .error(let message):
            return "error(\(message))"
        case .loading:
            return "loading"
        }
    }
    
    var isIntro: Bool {
        if case .intro = self { return true }
        return false
    }
    
    var isPaywall: Bool {
        if case .paywall = self { return true }
        return false
    }
    
    var isMain: Bool {
        self == .main
    }
    
    var isError: Bool {
        if case .error = self { return true }
        return false
    }
    
    var isLoading: Bool {
        self == .loading
    }
}

/// Helper pour calculer la route selon l'état de l'application
struct DailyContentRouteCalculator {
    
    static func calculateRoute(
        for contentType: ContentType,
        hasConnectedPartner: Bool,
        hasSeenIntro: Bool,
        shouldShowPaywall: Bool,
        paywallDay: Int,
        serviceHasError: Bool,
        serviceErrorMessage: String?,
        serviceIsLoading: Bool
    ) -> DailyContentRoute {
        
        // 🚨 CORRECTION CRITIQUE: INTRO AVANT LOADING
        
        // 1. Connexion partenaire d'abord
        if !hasConnectedPartner {
            // Pas de partenaire → intro avec demande de connexion
            return .intro(showConnect: true)
        }
        
        // 2. Intro avant tout loading/contenu
        if !hasSeenIntro {
            // Partenaire connecté mais intro pas encore vue → intro sans demande de connexion
            return .intro(showConnect: false)
        }
        
        // 3. Puis états techniques (erreurs avant loading)
        if serviceHasError {
            let errorMessage = serviceErrorMessage ?? "Une erreur est survenue"
            return .error(errorMessage)
        }
        
        // ✅ CHANGEMENT CRITIQUE: .main au lieu de .loading
        // DailyQuestionMainView gère maintenant son propre loading avec isBusy
        if serviceIsLoading {
            return .main  // Pas de .loading → Supprime le double système
        }
        
        // 4. Vérifier paywall freemium
        if shouldShowPaywall {
            return .paywall(day: paywallDay)
        }
        
        // 5. État par défaut - vue principale
        return .main
    }
    
    enum ContentType {
        case dailyQuestion
        case dailyChallenge
        
        var displayName: String {
            switch self {
            case .dailyQuestion: return "Question du Jour"
            case .dailyChallenge: return "Défi du Jour"
            }
        }
    }
}