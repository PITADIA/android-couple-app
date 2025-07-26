import SwiftUI
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import Combine

struct DailyQuestionMainView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var responseText = ""
    @State private var isSubmittingResponse = false
    // @State private var showChatSheet = false // SUPPRIMÉ - Plus besoin du sheet

    @FocusState private var isTextFieldFocused: Bool
    
    // SOLUTION: État stable pour les messages qui évite les problèmes de ForEach
    @State private var stableMessages: [QuestionResponse] = []
    
    private var currentUserId: String? {
        return Auth.auth().currentUser?.uid
    }
    
    private var hasConnectedPartner: Bool {
        guard let partnerId = appState.currentUser?.partnerId else { return false }
        return !partnerId.isEmpty
    }
    
    var body: some View {
        NavigationView {
            GeometryReader { geometry in
            ZStack {
                Color(white: 0.97) // Fond gris clair moderne comme dans les apps de chat
                    .ignoresSafeArea()
                
                if isNoPartnerState {
                    // État sans partenaire
                    noPartnerView
                } else if dailyQuestionService.allQuestionsExhausted {
                    // État d'épuisement des questions
                    exhaustedQuestionsView
                } else if dailyQuestionService.isLoading && dailyQuestionService.currentQuestion == nil {
                    // État de chargement
                    loadingView
                } else if let currentQuestion = dailyQuestionService.currentQuestion {
                    // État normal avec question
                        questionContentView(currentQuestion, geometry: geometry)
                } else {
                    // État sans question disponible
                    noQuestionView
                    }
                }
            }
            .navigationTitle(NSLocalizedString("daily_question_title", tableName: "DailyQuestions", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarColorScheme(.light, for: .navigationBar) // Force le texte en noir
            // .sheet(isPresented: $showChatSheet) { // SUPPRIMÉ - Plus besoin du sheet
            //     if let current = dailyQuestionService.currentQuestion {
            //         DailyQuestionMessageKitView(question: current)
            //             .presentationDetents([.fraction(0.45), .large])
            //             .presentationDragIndicator(.visible)
            //             .environmentObject(appState)
            //     }
            // }
        }
        .onAppear {
            if dailyQuestionService.currentQuestion == nil && !dailyQuestionService.allQuestionsExhausted {
                print("🔍 DailyQuestionMainView.onAppear: Génération question (currentQuestion=nil)")
                Task {
                    await dailyQuestionService.generateTodaysQuestion()
                }
            } else {
                print("🔍 DailyQuestionMainView.onAppear: Pas de génération (currentQuestion existe ou épuisées)")
            }
        }
        .refreshable {
            if !dailyQuestionService.allQuestionsExhausted {
                print("🔍 DailyQuestionMainView.refreshable: Génération forcée")
                Task {
                    await dailyQuestionService.generateTodaysQuestion()
                }
            }
        }
    }
    
    // MARK: - État d'épuisement des questions
    
    private var exhaustedQuestionsView: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Icône de félicitations
                Image(systemName: "trophy.fill")
                    .font(.system(size: 60))
                    .foregroundColor(Color(hex: "#FD267A"))
                    .padding(.top, 40)
                
                // Titre principal
                Text(NSLocalizedString("daily_questions_exhausted_title", tableName: "DailyQuestions", comment: ""))
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.center)
                
                // Message d'explication
                Text(NSLocalizedString("daily_questions_exhausted_message", tableName: "DailyQuestions", comment: ""))
                    .font(.system(size: 16))
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
                
                // Statistiques
                    VStack(spacing: 12) {
                    Text(NSLocalizedString("daily_questions_exhausted_stats_simple", tableName: "DailyQuestions", comment: ""))
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(Color(hex: "#FD267A"))
                        .multilineTextAlignment(.center)
                        
                        Text(NSLocalizedString("daily_questions_congratulations", tableName: "DailyQuestions", comment: ""))
                            .font(.system(size: 14))
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                    }
                    .padding()
                    .background(Color(hex: "#FD267A").opacity(0.1))
                    .cornerRadius(16)
                    .padding(.horizontal, 20)
                
                // Message de félicitations simple
                VStack(spacing: 16) {
                    Text(NSLocalizedString("daily_questions_all_completed", tableName: "DailyQuestions", comment: ""))
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                    
                    Text(NSLocalizedString("daily_questions_new_cycle", tableName: "DailyQuestions", comment: ""))
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                }
                .padding(.vertical, 20)
                

                
                Spacer(minLength: 50)
            }
        }
    }
    
    private var headerView: some View {
        HStack {
            Button(action: {
                dismiss()
            }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundColor(.black)
            }
            
            Spacer()
            
            Text("daily_question_title")
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(.black)
            
            Spacer()
            
            // Placeholder pour équilibrer le header
            Spacer()
                .frame(width: 20)
        }
        .padding(.horizontal, 20)
        .padding(.top, 60)
        .padding(.bottom, 20)
    }
    
    // MARK: - Computed Properties
    
    private var isNoPartnerState: Bool {
        return appState.currentUser?.partnerId == nil
    }
    

    
    // MARK: - Views
    
    private var noPartnerView: some View {
        VStack(spacing: 20) {
            Image(systemName: "person.2.slash")
                .font(.system(size: 60))
                .foregroundColor(.gray)
            
            Text(NSLocalizedString("daily_question_no_partner_title", tableName: "DailyQuestions", comment: ""))
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(.black)
            
            Text(NSLocalizedString("daily_question_no_partner_message", tableName: "DailyQuestions", comment: ""))
                .font(.system(size: 16))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private var loadingView: some View {
        VStack(spacing: 20) {
            ProgressView()
                .scaleEffect(1.5)
                .tint(Color(hex: "#FD267A"))
            
            Text(NSLocalizedString("daily_question_loading", tableName: "DailyQuestions", comment: ""))
                .font(.system(size: 16))
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private var noQuestionView: some View {
        VStack(spacing: 20) {
            Image(systemName: "questionmark.circle")
                .font(.system(size: 60))
                .foregroundColor(.gray)
            
            Text("Aucune question disponible")
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(.black)
            
            Button {
                print("🔍 DailyQuestionMainView.button: Génération manuelle demandée")
                Task {
                    await dailyQuestionService.generateTodaysQuestion()
                }
            } label: {
                Text("Générer une question")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color(hex: "#FD267A"))
                    .cornerRadius(25)
            }
            .padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private func questionContentView(_ question: DailyQuestion, geometry: GeometryProxy) -> some View {
        ZStack {
            // Contenu principal avec ScrollView
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 20) {
                        // Carte de la question
                        DailyQuestionCard(question: question)
                            .padding(.horizontal, 20)
                            .padding(.top, 20)
                        
                        // Section chat
                        chatSection(for: question, proxy: proxy)
                        
                        // Espace pour éviter que le contenu passe sous l'input
                        // Adaptatif selon l'état du clavier
                        Spacer()
                            .frame(height: isTextFieldFocused ? 80 : (120 + max(geometry.safeAreaInsets.bottom, 16)))
                    }
                }
                .onChange(of: stableMessages.count) { _, newCount in
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            proxy.scrollTo("bottom", anchor: .bottom)
                        }
                    }
                }
                .onChange(of: isTextFieldFocused) { _, isFocused in
                    if isFocused {
                        // Scroll vers le bas quand le clavier s'ouvre
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            withAnimation(.easeInOut(duration: 0.3)) {
                                proxy.scrollTo("bottom", anchor: .bottom)
                            }
                        }
                    }
                }
            }
            // .ignoresSafeArea(.keyboard) // SUPPRIMÉ - Permet au clavier de pousser le contenu
            
            // Zone de saisie fixe en bas
            VStack {
                Spacer()
                inputArea(for: question, geometry: geometry)
            }
        }
        .onAppear {
            // Initialiser les messages stables dès l'apparition de la vue
            updateStableMessages(from: question)
        }
    }
    
    private func chatSection(for question: DailyQuestion, proxy: ScrollViewProxy) -> some View {
        VStack(spacing: 16) {
            if stableMessages.isEmpty {
                // Message d'encouragement
                VStack(spacing: 12) {
                    Image(systemName: "bubble.left.and.bubble.right")
                        .font(.system(size: 30))
                        .foregroundColor(Color(hex: "#FD267A"))
                    
                    Text(NSLocalizedString("daily_question_start_conversation", tableName: "DailyQuestions", comment: ""))
                        .font(.system(size: 16))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                }
                .padding(.vertical, 40)
            } else {
                // Messages existants avec état stable
                ForEach(stableMessages, id: \.id) { response in
                    VStack {
                    ChatMessageView(
                        response: response,
                            isCurrentUser: response.userId == currentUserId,
                        partnerName: response.userName
                    )
                    }
                    .id("\(response.id)-stable")
                }
                
                // Message d'attente si nécessaire
                if question.shouldShowWaitingMessage(for: currentUserId ?? "", withSettings: dailyQuestionService.currentSettings) {
                    WaitingMessageView()
                        .id("waiting_message")
                }
                
                // Spacer invisible pour le scroll automatique
                Spacer(minLength: 1)
                    .id("bottom")
            }
        }
        .padding(.horizontal, 20)
        .onAppear {
            updateStableMessages(from: question)
        }
        .onChange(of: question.responsesArray) { _, _ in
            updateStableMessages(from: question)
        }
    }
    
    // MARK: - Helper Functions
    
    private func updateStableMessages(from question: DailyQuestion) {
        let newMessages = question.responsesArray
        
        // Comparer avec les messages actuels
        let currentCount = stableMessages.count
        let newCount = newMessages.count
        
        // Seulement mettre à jour si les messages ont vraiment changé
        if !messagesAreEqual(stableMessages, newMessages) {
            withAnimation(.easeInOut(duration: 0.2)) {
                self.stableMessages = newMessages
            }
            
            // Si de nouveaux messages ont été ajoutés, déclencher le scroll
            if newCount > currentCount {
                // Nouveaux messages détectés - le scroll sera géré automatiquement par onChange
            }
        }
    }
    
    private func messagesAreEqual(_ messages1: [QuestionResponse], _ messages2: [QuestionResponse]) -> Bool {
        guard messages1.count == messages2.count else { return false }
        
        for (index, message1) in messages1.enumerated() {
            let message2 = messages2[index]
            if message1.id != message2.id || message1.text != message2.text {
                return false
            }
        }
        return true
    }
    
    private func inputArea(for question: DailyQuestion, geometry: GeometryProxy) -> some View {
        VStack(spacing: 0) {
            Divider()
                .background(Color.gray.opacity(0.3))
            
            HStack(spacing: 12) {
                // Vrai TextField au lieu du bouton
                TextField(NSLocalizedString("daily_question_type_response", tableName: "DailyQuestions", comment: ""), text: $responseText)
                    .focused($isTextFieldFocused)
                    .textFieldStyle(PlainTextFieldStyle())
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 25)
                            .fill(Color.gray.opacity(0.1))
                    )
                    .onSubmit {
                        submitResponse(question: question)
                    }
                
                // Bouton d'envoi
                Button {
                    submitResponse(question: question)
                } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 32))
                        .foregroundColor(responseText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? .gray : Color(hex: "#FD267A"))
                }
                .disabled(responseText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSubmittingResponse)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .padding(.bottom, isTextFieldFocused ? 0 : max(geometry.safeAreaInsets.bottom, 16))
            // ☝️ CLEF : Pas de padding bottom quand le clavier est ouvert !
        }
        .background(.regularMaterial)
    }
    
    // MARK: - Actions
    
    private func submitResponse(question: DailyQuestion) {
        guard !responseText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        
        isSubmittingResponse = true
        
        // Créer une réponse temporaire pour l'affichage immédiat (UX amélioreée)
        let tempResponse = QuestionResponse(
            userId: currentUserId ?? "",
            userName: appState.currentUser?.name ?? "Vous",
            text: responseText.trimmingCharacters(in: .whitespacesAndNewlines),
            status: .answered
        )
        
        // Ajouter immédiatement à l'interface pour une réaction instantanée
        withAnimation(.easeInOut(duration: 0.2)) {
            stableMessages.append(tempResponse)
        }
        
        // Nettoyer le champ et fermer le clavier immédiatement
        let textToSubmit = responseText.trimmingCharacters(in: .whitespacesAndNewlines)
        responseText = ""
        isTextFieldFocused = false
        
        Task {
            let success = await dailyQuestionService.submitResponse(textToSubmit)
            
            await MainActor.run {
                if !success {
                    // En cas d'échec, restaurer le texte et supprimer le message temporaire
                    responseText = textToSubmit
                    if let tempIndex = stableMessages.firstIndex(where: { $0.id == tempResponse.id }) {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            stableMessages.remove(at: tempIndex)
                        }
                    }
                }
                isSubmittingResponse = false
            }
        }
    }
}

struct DailyQuestionCard: View {
    let question: DailyQuestion
    
    var body: some View {
        VStack(spacing: 0) {
            // Header de la carte
            VStack(spacing: 8) {
                Text(NSLocalizedString("daily_question_card_header", tableName: "DailyQuestions", comment: ""))
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 20)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FD267A"),
                        Color(hex: "#FF655B")
                    ]),
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            
            // Corps de la carte avec la question
            VStack(spacing: 30) {
                Spacer()
                
                Text(question.localizedText)
                    .font(.system(size: 22, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
                    .padding(.horizontal, 30)
                
                Spacer(minLength: 20)
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: 200)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.2, green: 0.1, blue: 0.15),
                        Color(red: 0.4, green: 0.2, blue: 0.3),
                        Color(red: 0.6, green: 0.3, blue: 0.2)
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
        }
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 4)
    }
    

}

struct ChatMessageView: View {
    let response: QuestionResponse
    let isCurrentUser: Bool
    let partnerName: String
    
    var body: some View {
        HStack {
            if isCurrentUser {
                Spacer(minLength: 50)
            
                VStack(alignment: .trailing, spacing: 4) {
                    messageContent
                        .background(
                            RoundedRectangle(cornerRadius: 18)
                                .fill(Color(hex: "#FD267A")) // Rose Love2Love pour l'utilisateur
                        )
                        .foregroundColor(.white)
                    
                    Text(response.respondedAt.formatted(date: .omitted, time: .shortened))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .padding(.trailing, 8)
                }
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .top, spacing: 8) {
                        // Avatar simple avec initiales
                        Circle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(width: 32, height: 32)
                            .overlay(
                                Text(String(partnerName.prefix(1)).uppercased())
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(.gray)
                            )
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(partnerName)
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundColor(.secondary)
                            
                            messageContent
                                .background(
                                    RoundedRectangle(cornerRadius: 18)
                                        .fill(Color(UIColor.systemGray6)) // Gris système pour le partenaire
                                )
                                .foregroundColor(.primary)
                        }
                    }
                    
                    Text(response.respondedAt.formatted(date: .omitted, time: .shortened))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .padding(.leading, 40)
                }
                
                Spacer(minLength: 50)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }
    
    private var messageContent: some View {
        Text(response.text)
            .font(.body)
            .multilineTextAlignment(isCurrentUser ? .trailing : .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .fixedSize(horizontal: false, vertical: true)
            .contextMenu {
                // Menu contextuel pour signalement (seulement pour les messages du partenaire)
                if !isCurrentUser {
                    Button(action: {
                        reportMessage()
                    }) {
                        Label("Signaler ce message", systemImage: "exclamationmark.triangle")
                    }
                    .foregroundColor(.red)
                }
            }
    }
    
    // NOUVEAU: Fonction de signalement
    private func reportMessage() {
        print("🚨 ChatMessageView: Signalement du message: \(response.id)")
        print("🚨 ChatMessageView: Contenu signalé: \(String(response.text.prefix(50)))...")
        
        Task {
            do {
                let functions = Functions.functions()
                let result = try await functions.httpsCallable("reportInappropriateContent").call([
                    "messageId": response.id,
                    "reportedUserId": response.userId,
                    "reportedUserName": response.userName,
                    "messageText": response.text,
                    "reason": "inappropriate_content",
                    "reportedAt": Timestamp(date: Date())
                ])
                
                if let data = result.data as? [String: Any],
                   let success = data["success"] as? Bool,
                   success {
                    print("✅ ChatMessageView: Signalement envoyé avec succès")
                    
                    // Afficher confirmation à l'utilisateur
                    DispatchQueue.main.async {
                        // Vous pouvez ajouter une alerte de confirmation ici
                        print("Message signalé avec succès")
                    }
                } else {
                    print("❌ ChatMessageView: Échec du signalement")
                }
                
            } catch {
                print("❌ ChatMessageView: Erreur signalement - \(error)")
            }
        }
    }
}

struct WaitingMessageView: View {
    var body: some View {
        HStack {
            Image(systemName: "hourglass")
                .font(.title3)
                .foregroundColor(.blue)
            
            VStack(alignment: .leading, spacing: 2) {
                Text("En attente de votre partenaire")
                    .font(.body)
                    .fontWeight(.medium)
                
                Text("Votre partenaire n'a pas encore répondu")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.blue.opacity(0.1))
                .stroke(Color.blue.opacity(0.3), lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

// MARK: - History Preview Card

struct HistoryPreviewCard: View {
    let question: DailyQuestion
    let rank: Int
    
    var body: some View {
        HStack {
            // Numéro de rang
            Text("\(rank)")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 24, height: 24)
                .background(Color(hex: "#FD267A"))
                .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 4) {
                Text(question.localizedText)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.black)
                    .lineLimit(2)
                
                Text(question.formattedDate)
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            // Indicateur de réponses
            HStack(spacing: 4) {
                Image(systemName: "bubble.left.and.bubble.right.fill")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "#FD267A"))
                
                Text("\(question.responses.count)")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color(hex: "#FD267A"))
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 4, x: 0, y: 2)
    }
}

// MARK: - Extensions

extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}

extension Date {
    var timeAgoString: String {
        let now = Date()
        let difference = now.timeIntervalSince(self)
        
        if difference < 60 {
            return NSLocalizedString("time_now", tableName: "DailyQuestions", comment: "")
        } else if difference < 3600 {
            let minutes = Int(difference / 60)
            return String(format: NSLocalizedString("time_minutes_ago", tableName: "DailyQuestions", comment: ""), minutes)
        } else if difference < 86400 {
            let hours = Int(difference / 3600)
            return String(format: NSLocalizedString("time_hours_ago", tableName: "DailyQuestions", comment: ""), hours)
        } else {
            let formatter = DateFormatter()
            formatter.dateStyle = .short
            formatter.timeStyle = .short
            return formatter.string(from: self)
        }
    }
}

#Preview {
    Group {
    DailyQuestionMainView()
        .environmentObject(AppState())
    }
} 