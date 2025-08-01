import SwiftUI
import MessageKit
import InputBarAccessoryView
import FirebaseAuth
import FirebaseAnalytics

/// Vue chat utilisant MessageKit pour les questions quotidiennes
struct DailyQuestionMessageKitView: UIViewControllerRepresentable {
    let question: DailyQuestion
    @ObservedObject private var dailyQuestionService = DailyQuestionService.shared
    @EnvironmentObject var appState: AppState
    
    func makeUIViewController(context: Context) -> DailyQuestionChatViewController {
        print("🔥 DailyQuestionMessageKit: makeUIViewController appelé")
        let chatVC = DailyQuestionChatViewController()
        chatVC.question = question
        chatVC.dailyQuestionService = dailyQuestionService
        chatVC.appState = appState
        print("   - ChatVC créé avec question: \(question.id)")
        return chatVC
    }
    
    func updateUIViewController(_ uiViewController: DailyQuestionChatViewController, context: Context) {
        print("🔥 DailyQuestionMessageKit: updateUIViewController appelé")
        uiViewController.question = question
        uiViewController.appState = appState
        print("   - Mise à jour avec question: \(question.id)")
        print("   - Nombre de réponses: \(question.responsesArray.count)")
        uiViewController.updateMessages()
    }
}

/// Controller MessageKit personnalisé pour les questions quotidiennes
class DailyQuestionChatViewController: MessagesViewController {
    
    var question: DailyQuestion?
    var dailyQuestionService: DailyQuestionService?
    var appState: AppState?
    
    private var messages: [DailyQuestionMessage] = []
    private var currentUserSender: MessageSender?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        print("🔥 DailyQuestionMessageKit: viewDidLoad")
        setupCurrentUser()
        configureMessageKit()
        setupInputBar()
        updateMessages()
        print("🔥 DailyQuestionMessageKit: viewDidLoad terminé")
    }
    
    // MARK: - Setup
    
    private func setupCurrentUser() {
        print("🔥 DailyQuestionMessageKit: setupCurrentUser")
        guard let appState = appState else { return }
        currentUserSender = MessageKitAdapter.currentUserSender(appState: appState)
        print("   - Current user sender: \(currentUserSender?.displayName ?? "nil")")
    }
    
    private func configureMessageKit() {
        print("🔥 DailyQuestionMessageKit: Configuration MessageKit")
        messagesCollectionView.messagesDataSource = self
        messagesCollectionView.messagesLayoutDelegate = self
        messagesCollectionView.messagesDisplayDelegate = self
        messagesCollectionView.messageCellDelegate = self
        
        // Style Love2Love
        messagesCollectionView.backgroundColor = UIColor(red: 0.97, green: 0.97, blue: 0.98, alpha: 1.0)
        
        // 🎯 STYLE TWITTER - Les avatars et noms sont masqués via les méthodes de délégué
        // (pas de propriétés directes à configurer ici)
        print("🔥 DailyQuestionMessageKit: Délégués configurés")
        
        // Couleurs personnalisées
        maintainPositionOnInputBarHeightChanged = true
        messageInputBar.backgroundView.backgroundColor = UIColor.systemBackground
        
        scrollsToLastItemOnKeyboardBeginsEditing = true
        print("🔥 DailyQuestionMessageKit: Configuration terminée")
    }
    
    private func setupInputBar() {
        messageInputBar.delegate = self
        
        // Style Love2Love pour l'input bar
        messageInputBar.inputTextView.backgroundColor = UIColor.systemGray6
        messageInputBar.inputTextView.layer.cornerRadius = 20
        messageInputBar.inputTextView.layer.borderWidth = 0
        messageInputBar.inputTextView.font = UIFont.systemFont(ofSize: 16)
        messageInputBar.inputTextView.textContainerInset = UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 50)
        
        // Bouton d'envoi personnalisé
        messageInputBar.sendButton.setTitleColor(UIColor(red: 0.99, green: 0.15, blue: 0.48, alpha: 1.0), for: .normal)
        messageInputBar.sendButton.setTitleColor(UIColor.systemGray, for: .disabled)
        messageInputBar.sendButton.titleLabel?.font = UIFont.boldSystemFont(ofSize: 16)
        
        // Placeholder
        messageInputBar.inputTextView.placeholder = NSLocalizedString("daily_question_type_response", tableName: "DailyQuestions", comment: "")
        messageInputBar.inputTextView.placeholderLabel.textColor = UIColor.systemGray
        
        // Configuration avancée
        messageInputBar.shouldManageSendButtonEnabledState = true
    }
    
    // MARK: - Data Management
    
    func updateMessages() {
        guard let question = question else { return }
        
        print("🔥 DailyQuestionMessageKit: updateMessages appelé")
        print("   - Question ID: \(question.id)")
        print("   - Nombre de réponses: \(question.responsesArray.count)")
        
        let newMessages = MessageKitAdapter.convert(question.responsesArray)
        print("   - Messages convertis: \(newMessages.count)")
        
        // Animation smooth pour les nouveaux messages
        let shouldScrollToBottom = messages.isEmpty
        print("   - shouldScrollToBottom: \(shouldScrollToBottom)")
        print("   - Ancien nombre de messages: \(messages.count)")
        messages = newMessages
        print("   - Nouveau nombre de messages: \(messages.count)")
        
        DispatchQueue.main.async {
            print("🔄 DailyQuestionMessageKit: Reload data sur main thread")
            self.messagesCollectionView.reloadData()
            if shouldScrollToBottom {
                print("📜 DailyQuestionMessageKit: Scroll to bottom")
                self.messagesCollectionView.scrollToLastItem()
            }
        }
    }
    
    private func insertMessage(_ message: DailyQuestionMessage) {
        messages.append(message)
        
        DispatchQueue.main.async {
            self.messagesCollectionView.performBatchUpdates({
                self.messagesCollectionView.insertSections([self.messages.count - 1])
            }, completion: { _ in
                self.messagesCollectionView.scrollToLastItem(animated: true)
            })
        }
    }
}

// MARK: - MessagesDataSource

extension DailyQuestionChatViewController: MessagesDataSource {
    
    var currentSender: SenderType {
        let sender = currentUserSender ?? MessageSender(userId: "unknown", name: "Unknown")
        print("🔍 currentSender appelé → \(sender.displayName) (ID: \(sender.senderId))")
        return sender
    }
    
    func messageForItem(at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> MessageType {
        let message = messages[indexPath.section]
        let textPreview: String
        switch message.kind {
        case .text(let text):
            textPreview = String(text.prefix(20))
        default:
            textPreview = "non-text message"
        }
        print("🔍 messageForItem appelé pour section \(indexPath.section) → sender: \(message.sender.displayName), text: '\(textPreview)...'")
        return message
    }
    
    func numberOfSections(in messagesCollectionView: MessagesCollectionView) -> Int {
        print("🔍 numberOfSections appelé → \(messages.count)")
        return messages.count
    }
    
    func cellTopLabelText(for message: MessageType, at indexPath: IndexPath) -> String? {
        // 🎯 TWITTER STYLE: Aucun label en haut de cellule
        print("🔍 cellTopLabelText appelé pour section \(indexPath.section) → return nil")
        return nil
    }
    
    func cellBottomLabelText(for message: MessageType, at indexPath: IndexPath) -> String? {
        // 🎯 TWITTER STYLE: Aucun label en bas de cellule  
        print("🔍 cellBottomLabelText appelé pour section \(indexPath.section) → return nil")
        return nil
    }
    
    func messageTopLabelText(for message: MessageType, at indexPath: IndexPath) -> String? {
        // 🗑️ SUPPRIMÉ : Affichage des noms au-dessus des messages
        // Plus de noms affichés pour un design épuré comme Twitter
        print("🔍 messageTopLabelText appelé pour section \(indexPath.section), sender: \(message.sender.displayName) → return nil")
        return nil
    }
    
    func messageBottomLabelText(for message: MessageType, at indexPath: IndexPath) -> String? {
        // 🎯 STYLE TWITTER : Afficher l'heure SEULEMENT sur le tout dernier message
        let isVeryLastMessage = indexPath.section == messages.count - 1
        print("🔍 messageBottomLabelText appelé pour section \(indexPath.section)/\(messages.count-1), isLast: \(isVeryLastMessage)")
        
        if isVeryLastMessage {
            let formatter = DateFormatter()
            formatter.dateStyle = .none
            formatter.timeStyle = .short
            let timeString = formatter.string(from: message.sentDate)
            print("🔍 → return time: \(timeString)")
            return timeString
        }
        
        print("🔍 → return nil (pas le dernier message)")
        return nil
    }
}

// MARK: - MessagesLayoutDelegate

extension DailyQuestionChatViewController: MessagesLayoutDelegate {
    
    func messageTopLabelHeight(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> CGFloat {
        // 🗑️ Plus de noms affichés = plus de hauteur nécessaire
        print("🔍 messageTopLabelHeight appelé pour section \(indexPath.section) → return 0")
        return 0
    }
    
    func messageBottomLabelHeight(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> CGFloat {
        let height: CGFloat = messageBottomLabelText(for: message, at: indexPath) != nil ? 16 : 0
        print("🔍 messageBottomLabelHeight appelé pour section \(indexPath.section) → return \(height)")
        return height
    }
    
    func avatarSize(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> CGSize? {
        print("🔍 avatarSize appelé pour section \(indexPath.section) → return .zero")
        return .zero // 🎯 TWITTER STYLE: Aucun avatar
    }
    
    func cellTopLabelHeight(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> CGFloat {
        print("🔍 cellTopLabelHeight appelé pour section \(indexPath.section) → return 0")
        return 0 // 🎯 TWITTER STYLE: Aucun label en haut de cellule
    }
    
    func cellBottomLabelHeight(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> CGFloat {
        print("🔍 cellBottomLabelHeight appelé pour section \(indexPath.section) → return 0")
        return 0 // 🎯 TWITTER STYLE: Aucun label en bas de cellule
    }
    
    func messageLabelInsets(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> UIEdgeInsets {
        let insets = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        print("🔍 messageLabelInsets appelé pour section \(indexPath.section) → return \(insets)")
        return insets
    }
    
    func headerViewSize(for section: Int, in messagesCollectionView: MessagesCollectionView) -> CGSize {
        print("🔍 headerViewSize appelé pour section \(section) → return .zero")
        return .zero // 🎯 TWITTER STYLE: Aucun header
    }
}

// MARK: - MessagesDisplayDelegate

extension DailyQuestionChatViewController: MessagesDisplayDelegate {
    
    func backgroundColor(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> UIColor {
        let color = isFromCurrentSender(message: message) 
            ? UIColor(red: 0.99, green: 0.15, blue: 0.48, alpha: 1.0) // Rose Love2Love
            : UIColor.systemGray5 // Gris pour les messages reçus
        print("🔍 backgroundColor appelé pour section \(indexPath.section), isFromCurrentSender: \(isFromCurrentSender(message: message)) → \(color)")
        return color
    }
    
    func textColor(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> UIColor {
        let color = isFromCurrentSender(message: message) ? UIColor.white : UIColor.label
        print("🔍 textColor appelé pour section \(indexPath.section) → \(color)")
        return color
    }
    
    func configureAvatarView(_ avatarView: AvatarView, for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) {
        // 🎯 TWITTER STYLE: Supprimer complètement les avatars
        print("🔍 configureAvatarView appelé pour section \(indexPath.section), sender: \(message.sender.displayName)")
        print("   → Masquage avatar: isHidden=true, frame=.zero, alpha=0")
        avatarView.isHidden = true
        avatarView.frame = .zero
        avatarView.alpha = 0
        avatarView.backgroundColor = .clear
    }
    
    func configureMediaMessageImageView(_ imageView: UIImageView, for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) {
        // 🎯 TWITTER STYLE: Configuration pour les images si nécessaire
        print("🔍 configureMediaMessageImageView appelé pour section \(indexPath.section)")
    }
    
    func configureAccessoryView(_ accessoryView: UIView, for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) {
        // 🎯 TWITTER STYLE: Pas d'accessoires
        print("🔍 configureAccessoryView appelé pour section \(indexPath.section) → isHidden=true")
        accessoryView.isHidden = true
    }
    
    func detectorAttributes(for detector: DetectorType, and message: MessageType, at indexPath: IndexPath) -> [NSAttributedString.Key: Any] {
        // 🎯 TWITTER STYLE: Attributs pour liens/mentions
        print("🔍 detectorAttributes appelé pour section \(indexPath.section), detector: \(detector)")
        return [
            .foregroundColor: UIColor(red: 0.99, green: 0.15, blue: 0.48, alpha: 1.0),
            .underlineStyle: NSUnderlineStyle.single.rawValue
        ]
    }
    
    func messageStyle(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> MessageStyle {
        // 🎯 TWITTER STYLE: Bulles sans queue puisque pas d'avatars
        print("🔍 messageStyle appelé pour section \(indexPath.section) → return .bubble")
        return .bubble
    }
}

// MARK: - MessageCellDelegate

extension DailyQuestionChatViewController: MessageCellDelegate {
    
    func didTapMessage(in cell: MessageCollectionViewCell) {
        // Actions optionnelles lors du tap sur un message
    }
}

// MARK: - InputBarAccessoryViewDelegate

extension DailyQuestionChatViewController: InputBarAccessoryViewDelegate {
    
    func inputBar(_ inputBar: InputBarAccessoryView, didPressSendButtonWith text: String) {
        guard let currentUserSender = currentUserSender,
              let _ = question,
              let dailyQuestionService = dailyQuestionService else { return }
        
        // Créer un message temporaire pour l'affichage immédiat
        let tempMessage = DailyQuestionMessage(
            tempId: UUID().uuidString,
            text: text,
            sender: currentUserSender
        )
        
        // Ajouter immédiatement à l'interface
        insertMessage(tempMessage)
        
        // Vider la barre de saisie
        inputBar.inputTextView.text = ""
        inputBar.invalidatePlugins()
        
        // 📊 Analytics: Message envoyé
        Analytics.logEvent("message_envoye", parameters: [
            "type": "texte",
            "source": "daily_question_messagekit"
        ])
        print("📊 Événement Firebase: message_envoye - type: texte - source: daily_question_messagekit")
        
        // Envoyer à Firebase (asynchrone)
        Task {
            let success = await dailyQuestionService.submitResponse(text)
            if !success {
                print("❌ Échec de l'envoi du message")
                // Optionnel: Gérer l'échec (retry, message d'erreur...)
            }
        }
    }
} 