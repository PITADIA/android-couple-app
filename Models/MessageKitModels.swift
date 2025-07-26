import Foundation
import MessageKit
import CoreLocation
import FirebaseAuth

// MARK: - MessageKit Adapter Models

/// Modèle utilisateur pour MessageKit
struct MessageSender: SenderType {
    var senderId: String
    var displayName: String
    
    init(userId: String, name: String) {
        self.senderId = userId
        self.displayName = name
    }
}

/// Message adapté pour MessageKit
struct DailyQuestionMessage: MessageType {
    var sender: SenderType
    var messageId: String
    var sentDate: Date
    var kind: MessageKind
    
    init(response: QuestionResponse) {
        self.sender = MessageSender(userId: response.userId, name: response.userName)
        self.messageId = response.id
        self.sentDate = response.respondedAt
        self.kind = .text(response.text)
    }
    
    // Initializer pour créer un message temporaire
    init(tempId: String, text: String, sender: MessageSender, date: Date = Date()) {
        self.sender = sender
        self.messageId = tempId
        self.sentDate = date
        self.kind = .text(text)
    }
}

/// Service de conversion QuestionResponse ↔ MessageKit
class MessageKitAdapter {
    
    /// Convertit une QuestionResponse en DailyQuestionMessage
    static func convert(_ response: QuestionResponse) -> DailyQuestionMessage {
        return DailyQuestionMessage(response: response)
    }
    
    /// Convertit un array de QuestionResponse en messages MessageKit
    static func convert(_ responses: [QuestionResponse]) -> [DailyQuestionMessage] {
        return responses
            .sorted { $0.respondedAt < $1.respondedAt }
            .map { convert($0) }
    }
    
    /// Crée un sender pour l'utilisateur actuel
    static func currentUserSender(appState: AppState) -> MessageSender? {
        guard let currentUserId = Auth.auth().currentUser?.uid,
              let currentUser = appState.currentUser else {
            return nil
        }
        return MessageSender(userId: currentUserId, name: currentUser.name)
    }
    
    /// Crée un sender pour le partenaire
    static func partnerSender(from responses: [QuestionResponse], currentUserId: String) -> MessageSender? {
        guard let partnerResponse = responses.first(where: { $0.userId != currentUserId }) else {
            return nil
        }
        return MessageSender(userId: partnerResponse.userId, name: partnerResponse.userName)
    }
} 