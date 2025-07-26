import SwiftUI
import MessageKit
import InputBarAccessoryView
import FirebaseAuth

/// Vue chat utilisant MessageKit pour les questions quotidiennes
struct DailyQuestionMessageKitView: UIViewControllerRepresentable {
    let question: DailyQuestion
    @ObservedObject private var dailyQuestionService = DailyQuestionService.shared
    @EnvironmentObject var appState: AppState
    
    func makeUIViewController(context: Context) -> DailyQuestionChatViewController {
        let chatVC = DailyQuestionChatViewController()
        chatVC.question = question
        chatVC.dailyQuestionService = dailyQuestionService
        chatVC.appState = appState
        return chatVC
    }
    
    func updateUIViewController(_ uiViewController: DailyQuestionChatViewController, context: Context) {
        uiViewController.question = question
        uiViewController.appState = appState
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
        
        setupCurrentUser()
        configureMessageKit()
        setupInputBar()
        updateMessages()
    }
    
    // MARK: - Setup
    
    private func setupCurrentUser() {
        guard let appState = appState else { return }
        currentUserSender = MessageKitAdapter.currentUserSender(appState: appState)
    }
    
    private func configureMessageKit() {
        messagesCollectionView.messagesDataSource = self
        messagesCollectionView.messagesLayoutDelegate = self
        messagesCollectionView.messagesDisplayDelegate = self
        messagesCollectionView.messageCellDelegate = self
        
        // Style Love2Love
        messagesCollectionView.backgroundColor = UIColor(red: 0.97, green: 0.97, blue: 0.98, alpha: 1.0)
        
        // Masquer les avatars pour une apparence plus propre
        
        // Couleurs personnalisées
        maintainPositionOnKeyboardFrameChanged = true
        messageInputBar.backgroundView.backgroundColor = UIColor.systemBackground
        
        scrollsToLastItemOnKeyboardBeginsEditing = true
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
        
        let newMessages = MessageKitAdapter.convert(question.responsesArray)
        
        // Animation smooth pour les nouveaux messages
        let shouldScrollToBottom = messages.isEmpty
        messages = newMessages
        
        DispatchQueue.main.async {
            self.messagesCollectionView.reloadData()
            if shouldScrollToBottom {
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
        return currentUserSender ?? MessageSender(userId: "unknown", name: "Unknown")
    }
    
    func messageForItem(at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> MessageType {
        return messages[indexPath.section]
    }
    
    func numberOfSections(in messagesCollectionView: MessagesCollectionView) -> Int {
        return messages.count
    }
    
    func messageTopLabelText(for message: MessageType, at indexPath: IndexPath) -> String? {
        // Afficher le nom de l'expéditeur pour le premier message ou après une pause
        if indexPath.section == 0 {
            return message.sender.displayName
        }
        
        let previousMessage = messages[indexPath.section - 1]
        if previousMessage.sender.senderId != message.sender.senderId {
            return message.sender.displayName
        }
        
        return nil
    }
    
    func messageBottomLabelText(for message: MessageType, at indexPath: IndexPath) -> String? {
        // Afficher l'heure pour le dernier message ou avant un changement d'expéditeur
        let isLastMessage = indexPath.section == messages.count - 1
        let isLastFromSender = indexPath.section < messages.count - 1 ? 
            messages[indexPath.section + 1].sender.senderId != message.sender.senderId : true
        
        if isLastMessage || isLastFromSender {
            let formatter = DateFormatter()
            formatter.dateStyle = .none
            formatter.timeStyle = .short
            return formatter.string(from: message.sentDate)
        }
        
        return nil
    }
}

// MARK: - MessagesLayoutDelegate

extension DailyQuestionChatViewController: MessagesLayoutDelegate {
    
    func messageTopLabelHeight(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> CGFloat {
        return messageTopLabelText(for: message, at: indexPath) != nil ? 20 : 0
    }
    
    func messageBottomLabelHeight(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> CGFloat {
        return messageBottomLabelText(for: message, at: indexPath) != nil ? 16 : 0
    }
    
    func avatarSize(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> CGSize {
        return CGSize.zero // Pas d'avatars
    }
}

// MARK: - MessagesDisplayDelegate

extension DailyQuestionChatViewController: MessagesDisplayDelegate {
    
    func backgroundColor(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> UIColor {
        return isFromCurrentSender(message: message) 
            ? UIColor(red: 0.99, green: 0.15, blue: 0.48, alpha: 1.0) // Rose Love2Love
            : UIColor.systemGray5 // Gris pour les messages reçus
    }
    
    func textColor(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> UIColor {
        return isFromCurrentSender(message: message) ? UIColor.white : UIColor.label
    }
    
    func configureAvatarView(_ avatarView: AvatarView, for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) {
        // Masquer complètement les avatars
        avatarView.isHidden = true
        avatarView.frame = CGRect.zero
    }
    
    func messageStyle(for message: MessageType, at indexPath: IndexPath, in messagesCollectionView: MessagesCollectionView) -> MessageStyle {
        return .bubbleTail(isFromCurrentSender(message: message) ? .bottomRight : .bottomLeft, .curved)
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
              let question = question,
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