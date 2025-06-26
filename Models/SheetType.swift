import Foundation

// MARK: - Sheet Type Enum
enum SheetType: Identifiable, Equatable {
    case questions(QuestionCategory)
    case menu
    case subscription
    case favorites
    case journal
    case widgets
    case widgetTutorial
    case partnerManagement
    case locationPermission
    case partnerLocationMessage
    case eventsMap
    
    var id: String {
        switch self {
        case .questions(let category):
            return "questions_\(category.id)"
        case .menu:
            return "menu"
        case .subscription:
            return "subscription"
        case .favorites:
            return "favorites"
        case .journal:
            return "journal"
        case .widgets:
            return "widgets"
        case .widgetTutorial:
            return "widgetTutorial"
        case .partnerManagement:
            return "partnerManagement"
        case .locationPermission:
            return "locationPermission"
        case .partnerLocationMessage:
            return "partnerLocationMessage"
        case .eventsMap:
            return "eventsMap"
        }
    }
    
    static func == (lhs: SheetType, rhs: SheetType) -> Bool {
        switch (lhs, rhs) {
        case (.questions(let lhsCategory), .questions(let rhsCategory)):
            return lhsCategory.id == rhsCategory.id
        case (.menu, .menu),
             (.subscription, .subscription),
             (.favorites, .favorites),
             (.journal, .journal),
             (.widgets, .widgets),
             (.widgetTutorial, .widgetTutorial),
             (.partnerManagement, .partnerManagement),
             (.locationPermission, .locationPermission),
             (.partnerLocationMessage, .partnerLocationMessage),
             (.eventsMap, .eventsMap):
            return true
        default:
            return false
        }
    }
} 