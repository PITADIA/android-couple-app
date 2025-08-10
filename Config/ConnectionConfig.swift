import Foundation

// Fallback placement of ConnectionConfig to ensure it is compiled even if the Config/ folder isn't included in Build Phases.
// If ConnectionConfig already exists in another file, the compiler will complain about a duplicate definition; in that case, remove one.
struct ConnectionConfig {
    static let preparingMinDuration: TimeInterval = 1.5
    static let preparingMaxTimeout: TimeInterval = 8.0
    static let readinessCheckInterval: TimeInterval = 0.3
    static let uiTransitionDelay: TimeInterval = 0.1

    enum AnalyticsEvent {
        case successViewShown(mode: String, context: String)
        case successViewContinue(mode: String, waitTime: TimeInterval)
        case readyTimeout(duration: TimeInterval, context: String)
        case connectStart(source: String)
        case connectSuccess(inheritedSub: Bool, context: String)
        case introShown(screen: String)
        case introContinue(screen: String)

        var eventName: String {
            switch self {
            case .successViewShown: return "success_view_shown"
            case .successViewContinue: return "success_view_continue"
            case .readyTimeout: return "ready_timeout"
            case .connectStart: return "connect_start"
            case .connectSuccess: return "connect_success"
            case .introShown: return "intro_shown"
            case .introContinue: return "intro_continue"
            }
        }

        var parameters: [String: Any] {
            switch self {
            case .successViewShown(let mode, let context): return ["mode": mode, "context": context]
            case .successViewContinue(let mode, let waitTime): return ["mode": mode, "wait_time": waitTime]
            case .readyTimeout(let duration, let context): return ["duration": duration, "context": context]
            case .connectStart(let source): return ["source": source]
            case .connectSuccess(let inheritedSub, let context): return ["inherited_subscription": inheritedSub, "context": context]
            case .introShown(let screen): return ["screen": screen]
            case .introContinue(let screen): return ["screen": screen]
            }
        }
    }

    static func introFlagsKey(for coupleId: String) -> String {
        "introFlags_\(coupleId)"
    }

    enum ConnectionContext: String, CaseIterable {
        case onboarding, menu, profilePhoto = "profile_photo", dailyQuestion = "daily_question", dailyChallenge = "daily_challenge"
    }
}