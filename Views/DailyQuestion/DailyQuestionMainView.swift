import SwiftUI
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import Combine
import UserNotifications
import FirebaseAnalytics

struct DailyQuestionMainView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    @ObservedObject private var partnerService = PartnerConnectionNotificationService.shared  // ✅ Référence directe
    @Environment(\.dismiss) private var dismiss
    
    @State private var responseText = ""
    @State private var isSubmittingResponse = false
    // @State private var showChatSheet = false // SUPPRIMÉ - Plus besoin du sheet

    @FocusState private var isTextFieldFocused: Bool
    
    // SOLUTION: État stable pour les messages qui évite les problèmes de ForEach
    @State private var stableMessages: [QuestionResponse] = []
    // SUPPRIMÉ: tabBarHeight plus nécessaire avec la nouvelle structure VStack
    
    // SUPPRIMÉ: Observateur de clavier - on laisse iOS gérer automatiquement
    // @State private var keyboardHeight: CGFloat = 0
    
    // 🔔 NOTIFICATIONS: Demande de permission (une seule fois)
    @State private var hasRequestedNotificationsThisSession = false
    
    // ✅ ANIMATION pour le loader unifié
    @State private var isAnimating = false
    
    // 🎯 EXPERT SOLUTION: Extension UIKit pour fermeture clavier
    private func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
    
    private var currentUserId: String? {
        return Auth.auth().currentUser?.uid
    }
    
    private var hasConnectedPartner: Bool {
        guard let partnerId = appState.currentUser?.partnerId else { return false }
        return !partnerId.isEmpty
    }
    
    // ✅ CONDITION UNIFIÉE pour centraliser le chargement
    private var isBusy: Bool {
        dailyQuestionService.isLoading || dailyQuestionService.isOptimizing
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                // 🎯 SOLUTION AMÉLIORÉE: Background tapGesture seulement sur zone de contenu
                Color(white: 0.97)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header avec titre centré et sous-titre freemium
                    HStack {
                        Spacer()
                        VStack(spacing: 4) {
                            Text(NSLocalizedString("daily_question_title", tableName: "DailyQuestions", comment: ""))
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)
                            
                            // Sous-titre freemium sous le titre
                            if let subtitle = getDailyQuestionSubtitle() {
                                Text(subtitle)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.top, 4)
                            }
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 8)
                    
                    // Contenu principal
                    GeometryReader { geometry in
                    if isNoPartnerState {
                        // État sans partenaire
                        noPartnerView
                            .contentShape(Rectangle())
                            .onTapGesture {
                                handleBackgroundTap()
                            }
                    } else if dailyQuestionService.allQuestionsExhausted {
                        // État d'épuisement des questions
                        exhaustedQuestionsView
                            .contentShape(Rectangle())
                            .onTapGesture {
                                handleBackgroundTap()
                            }
                    } else if isBusy && dailyQuestionService.currentQuestion == nil {
                        // ✅ ÉTAT DE CHARGEMENT UNIFIÉ avec condition isBusy
                        loadingView
                            .contentShape(Rectangle())
                            .onTapGesture {
                                handleBackgroundTap()
                            }
                    } else if let currentQuestion = dailyQuestionService.currentQuestion {
                        // État normal avec question
                        questionContentView(currentQuestion, geometry: geometry)
                    } else {
                        // État sans question disponible
                        noQuestionView
                            .contentShape(Rectangle())
                            .onTapGesture {
                                handleBackgroundTap()
                            }
                    }
                }
                }
            }
            .ignoresSafeArea(.keyboard)
            // ☝️ NOUVEAU: Ignorer safe area clavier car on gère manuellement avec keyboardHeight
            .navigationBarHidden(true)
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
            print("🚀 === DEBUG DAILYQUESTION MAIN VIEW - ON APPEAR ===")
            
            // 📅 LOGS DATE/HEURE DEMANDÉS
            let now = Date()
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
            formatter.timeZone = TimeZone.current
            print("🕐 DailyQuestionMainView: Date/Heure actuelle: \(formatter.string(from: now))")
            print("🌍 DailyQuestionMainView: Timezone: \(TimeZone.current.identifier)")
            print("📅 DailyQuestionMainView: Jour de la semaine: \(Calendar.current.component(.weekday, from: now))")
            print("📊 DailyQuestionMainView: Jour du mois: \(Calendar.current.component(.day, from: now))")
            print("📈 DailyQuestionMainView: Mois: \(Calendar.current.component(.month, from: now))")
            print("📉 DailyQuestionMainView: Année: \(Calendar.current.component(.year, from: now))")
            
            // 🧹 NETTOYER LES NOTIFICATIONS ET BADGE QUAND L'UTILISATEUR OUVRE LA VUE
            if let currentQuestion = dailyQuestionService.currentQuestion {
                // 🎯 DOUBLE NOTIFICATION FIX: Nettoyer notifications en attente ET déjà délivrées
                Task {
                    await clearAllNotificationsForQuestion(currentQuestion.id)
                }
            }
            // Reset général du badge
            BadgeManager.clearBadge()
            
            // ✅ GARDE IDEMPOTENTE - Éviter double appel et flicker
            if !dailyQuestionService.isLoading && 
               !dailyQuestionService.isOptimizing &&
               dailyQuestionService.currentQuestion == nil && 
               !dailyQuestionService.allQuestionsExhausted {
                Task {
                    await dailyQuestionService.checkForNewQuestionWithTimezoneOptimization()
                }
            }
            
            // 🔔 DEMANDE IMMÉDIATE DE PERMISSION (UNE SEULE FOIS)
            self.requestNotificationIfNeeded()
        }
        // plus de timer -> onDisappear inutile
        .refreshable {
            if !dailyQuestionService.allQuestionsExhausted {
                Task {
                    await dailyQuestionService.checkForNewQuestionWithTimezoneOptimization()
                }
            }
        }
        .overlay(
            // 🎉 SYSTÈME DE NOTIFICATION DE CONNEXION PARTENAIRE
            Group {
                if partnerService.shouldShowConnectionSuccess {  // ✅ Référence directe simplifiée
                    PartnerConnectionSuccessView(
                        partnerName: partnerService.connectedPartnerName,
                        mode: .simpleDismiss,
                        context: .dailyQuestion
                    ) {
                        // ✅ Fermeture fluide avec animation
                        withAnimation(.easeOut(duration: 0.3)) {
                            partnerService.dismissConnectionSuccess()
                        }
                    }
                    .transition(.opacity)
                    .zIndex(1000)
                }
            }
        )
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
                .font(.system(size: 28, weight: .bold))
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
        guard let partnerId = appState.currentUser?.partnerId else { return true }
        return partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    

    
    // MARK: - Views
    
    private var noPartnerView: some View {
        VStack(spacing: 5) {
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
    
    // ✅ LOADING VIEW UNIFIÉ - même style que DailyQuestionLoadingView
    private var loadingView: some View {
        VStack(spacing: 30) {
            Spacer()
            
            // Animation identique à DailyQuestionLoadingView
            ZStack {
                Circle()
                    .stroke(Color.gray.opacity(0.3), lineWidth: 4)
                    .frame(width: 60, height: 60)
                
                Circle()
                    .trim(from: 0, to: 0.75)
                    .stroke(Color(hex: "#FD267A"), lineWidth: 4)
                    .frame(width: 60, height: 60)
                    .rotationEffect(Angle(degrees: isAnimating ? 360 : 0))
                    .animation(
                        Animation.linear(duration: 1.0).repeatForever(autoreverses: false),
                        value: isAnimating
                    )
            }
            
            // ✅ TRADUCTIONS SPÉCIALISÉES DAILY QUESTION
            Text(NSLocalizedString("daily_question_preparing", tableName: "DailyQuestions", comment: ""))
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.black)
                .multilineTextAlignment(.center)
            
            Text(NSLocalizedString("daily_question_preparing_subtitle", tableName: "DailyQuestions", comment: ""))
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 30)
            
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear { isAnimating = true }
    }
    
    private var noQuestionView: some View {
        VStack(spacing: 20) {
            Image(systemName: "questionmark.circle")
                .font(.system(size: 60))
                .foregroundColor(.gray)
            
            Text(NSLocalizedString("no_question_available", tableName: "DailyQuestions", comment: ""))
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(.black)
            
            Button {
                Task {
                    await dailyQuestionService.checkForNewQuestionWithTimezoneOptimization()
                }
            } label: {
                Text(NSLocalizedString("generate_question", tableName: "DailyQuestions", comment: ""))
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
        VStack(spacing: 0) { // 🎯 EXPERT: Pas d'espacement par défaut
            // NOUVEAU: VStack au lieu de ZStack (recommandé par tous les forums dev)
            
            // Contenu principal (ScrollView) 
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 0) { // 🎯 EXPERT: Contrôler manuellement tout l'espacement
                        // Carte de la question
                        DailyQuestionCard(question: question)
                            .padding(.horizontal, 12) // 🎯 COMPACT: Réduire marges (20→12)
                            .padding(.top, 12)        // 🎯 COMPACT: Réduire padding top (20→12)
                            .padding(.bottom, 8)      // 🎯 COMPACT: Petit espace après question
                        
                        // Section chat
                        chatSection(for: question, proxy: proxy)
                        
                        // Espace en bas pour la zone de texte + menu (Spacer recommandé)
                        Color.clear.frame(height: 8) // 🎯 EXPERT: Frame fixe au lieu de Spacer
                    }
                }
                .contentShape(Rectangle()) // 🎯 ZONE TAPABLE: Définir zone de contenu uniquement
                .onTapGesture {
                    handleBackgroundTap() // 🎯 GESTE SÉCURISÉ: Seulement sur zone de contenu
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
                        // Scroll vers le bas quand le focus change
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            withAnimation(.easeInOut(duration: 0.3)) {
                                proxy.scrollTo("bottom", anchor: .bottom)
                            }
                        }
                    }
                }
            }
            // ☝️ SUPPRESSION de .ignoresSafeArea(.keyboard) qui causait le conflit
            
            // Zone de texte DIRECTEMENT dans la VStack (solution recommandée)
            inputArea(for: question, geometry: geometry)
        }
        // NOUVEAU: Background appliqué avec .background() au lieu de ZStack  
        .background(Color(white: 0.97))
        // ☝️ CLEF : .background() au lieu d'un élément dans ZStack
        .onAppear {
            // Initialiser les messages stables dès l'apparition de la vue
            updateStableMessages(from: question)
        }
    }
    
    private func chatSection(for question: DailyQuestion, proxy: ScrollViewProxy) -> some View {
        VStack(spacing: 0) { // 🎯 EXPERT: Supprimer espacement par défaut VStack
            if stableMessages.isEmpty {
                // Message d'encouragement
                VStack(spacing: 8) { // 🎯 COMPACT: Réduire espacement encouragement
                    Text(NSLocalizedString("daily_question_start_conversation", tableName: "DailyQuestions", comment: ""))
                        .font(.system(size: 16))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                }
                .padding(.vertical, 20) // 🎯 COMPACT: Réduire padding encouragement
            } else {
                // Messages existants avec état stable
                ForEach(Array(stableMessages.enumerated()), id: \.element.id) { index, response in
                    let isPreviousSameSender = index > 0 && stableMessages[index - 1].userId == response.userId
                    VStack(spacing: 0) { // 🎯 EXPERT: Pas d'espacement dans wrapper
                    ChatMessageView(
                        response: response,
                            isCurrentUser: response.userId == currentUserId,
                        partnerName: response.userName,
                        isLastMessage: response.id == stableMessages.last?.id, // 🎯 TWITTER STYLE: Déterminer si c'est le dernier message
                        isPreviousSameSender: isPreviousSameSender // 🎯 ESPACEMENT: Déterminer si même expéditeur que précédent
                    )
                    }
                    .id("\(response.id)-stable")
                }
                
                // Message d'attente si nécessaire
                if question.shouldShowWaitingMessage(for: currentUserId ?? "", withSettings: dailyQuestionService.currentSettings) {
                    WaitingMessageView()
                        .id("waiting_message")
                        .padding(.top, 8) // 🎯 COMPACT: Petit espacement pour message attente
                }
                
                // Spacer invisible pour le scroll automatique
                Color.clear.frame(height: 1) // 🎯 EXPERT: Remplacer Spacer par frame fixe
                    .id("bottom")
            }
        }
        .padding(.horizontal, 12) // 🎯 COMPACT: Réduire marges horizontales (20→12)
        .onAppear {
            updateStableMessages(from: question)
        }
        .onChange(of: question.responsesArray) { _, _ in
            updateStableMessages(from: question)
        }
    }
    
    // MARK: - Helper Functions
    
    /// 🎯 DOUBLE NOTIFICATION FIX: Nettoie TOUTES les notifications d'une question (en attente + délivrées)
    private func clearAllNotificationsForQuestion(_ questionId: String) async {
        let center = UNUserNotificationCenter.current()
        let questionNotificationPrefix = "new_message_\(questionId)_"
        
        // 1. Supprimer les notifications en attente
        let pendingRequests = await center.pendingNotificationRequests()
        let pendingIds = pendingRequests
            .filter { $0.identifier.hasPrefix(questionNotificationPrefix) }
            .map { $0.identifier }
        
        // 2. Supprimer les notifications déjà délivrées
        let deliveredNotifications = await center.deliveredNotifications()
        let deliveredIds = deliveredNotifications
            .filter { $0.request.identifier.hasPrefix(questionNotificationPrefix) }
            .map { $0.request.identifier }
        
        // 3. Nettoyer tout
        if !pendingIds.isEmpty {
            center.removePendingNotificationRequests(withIdentifiers: pendingIds)
            print("🗑️ DailyQuestionMainView: \(pendingIds.count) notifications en attente supprimées pour question \(questionId)")
        }
        
        if !deliveredIds.isEmpty {
            center.removeDeliveredNotifications(withIdentifiers: deliveredIds)
            print("🗑️ DailyQuestionMainView: \(deliveredIds.count) notifications délivrées supprimées pour question \(questionId)")
        }
        
        // 4. Appeler aussi le service pour cohérence
        dailyQuestionService.clearNotificationsForQuestion(questionId)
    }
    
    private func updateStableMessages(from question: DailyQuestion) {
        let newMessages = question.responsesArray.sorted { $0.respondedAt < $1.respondedAt }
        
        // Éviter les mises à jour inutiles qui causent des recomposition
        if newMessages.count != stableMessages.count || 
           newMessages.last?.id != stableMessages.last?.id {
            stableMessages = newMessages
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
                // 🎯 SOLUTION: TextEditor pour retours à la ligne naturels
                ZStack(alignment: .topLeading) {
                    // Placeholder personnalisé
                    if responseText.isEmpty {
                        Text(NSLocalizedString("daily_question_type_response", tableName: "DailyQuestions", comment: ""))
                            .foregroundColor(.gray)
                            .font(.system(size: 16)) // Même police que le TextEditor
                            .padding(.horizontal, 16)
                            .padding(.vertical, 20) // Ajusté pour aligner avec TextEditor
                            .allowsHitTesting(false) // Permet de taper à travers
                    }
                    
                    TextEditor(text: $responseText)
                        .focused($isTextFieldFocused)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 12)
                        .background(Color.clear)
                        .frame(minHeight: 40, maxHeight: 120)
                        .fixedSize(horizontal: false, vertical: true)
                        .scrollContentBackground(.hidden) // Masquer le background par défaut
                        .font(.system(size: 16)) // Taille de police cohérente
                }
                .background(
                    RoundedRectangle(cornerRadius: 25)
                        .fill(Color.gray.opacity(0.1))
                )
                
                Button {
                    submitResponse(question: question)
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(.white)
                        .frame(width: 35, height: 35)
                        .background(Color(hex: "#FD267A"))
                        .clipShape(Circle())
                }
                .disabled(responseText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSubmittingResponse)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            // NOUVEAU: Padding simple - le menu reste maintenant en bas !
            .padding(.bottom, 8)
        }
        .background(.regularMaterial)
        // SUPPRIMÉ: Plus d'animation manuelle - laissé à iOS
    }
    
    // MARK: - Actions
    
    /// Gère les taps en dehors de la zone de saisie pour fermer le chat ou le clavier
    private func handleBackgroundTap() {
        // 🎯 LOGIQUE AMÉLIORÉE: Gestion intelligente des taps background
        if isTextFieldFocused {
            // Si le clavier est ouvert → fermer seulement le clavier
            isTextFieldFocused = false
            hideKeyboard()
        } else {
            // Si le clavier est fermé → fermer le chat avec délai
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                dismiss()
            }
        }
    }
    
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
        
                // 📊 Analytics: Message envoyé
        Analytics.logEvent("message_envoye", parameters: [
            "type": "texte",
            "source": "daily_question_main"
        ])
        print("📊 Événement Firebase: message_envoye - type: texte - source: daily_question_main")
        
        Task {
            let success = await dailyQuestionService.submitResponse(textToSubmit)

            await MainActor.run {
                if success {
                    // ✅ NOUVEAU: Demande d'avis après action réussie (4ème jour complété)
                    if let windowScene = UIApplication.shared.connectedScenes
                        .compactMap({ $0 as? UIWindowScene }).first {
                        ReviewRequestService.shared.maybeRequestReviewAfterDailyCompletion(in: windowScene)
                    }
                    
                    // 🎯 NOUVEAU: Fermer le chat après envoi réussi du message
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        dismiss()
                    }
                } else {
                    // En cas d'échec, restaurer le texte et supprimer le message temporaire
                    responseText = textToSubmit
                    if let tempIndex = stableMessages.firstIndex(where: { $0.id == tempResponse.id }) {
                        _ = withAnimation(.easeInOut(duration: 0.2)) {
                            stableMessages.remove(at: tempIndex)
                        }
                    }
                }
                isSubmittingResponse = false
            }
        }
    }
    
    // MARK: - Keyboard Management
    
    // SUPPRIMÉ: Keyboard Management - laissé à iOS
    
    // MARK: - Freemium Subtitle Helpers
    
    /// Helper pour obtenir le sous-titre freemium des questions du jour
    private func getDailyQuestionSubtitle() -> String? {
        guard let user = appState.currentUser,
              let partnerId = user.partnerId,
              !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        let currentDay = calculateCurrentQuestionDay()
        return appState.freemiumManager?.getDailyQuestionSubtitle(for: currentDay)
    }
    
    /// Calcul du jour actuel de question
    private func calculateCurrentQuestionDay() -> Int {
        guard let user = appState.currentUser,
              let relationshipStartDate = user.relationshipStartDate else { return 1 }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: relationshipStartDate), to: calendar.startOfDay(for: Date())).day ?? 0
        return days + 1
    }
    
    // MARK: - 🔔 NOTIFICATIONS (DailyQuestionMainView)
    
    /// Vérifie et demande immédiatement les permissions de notifications (une seule fois)
    private func requestNotificationIfNeeded() {
        guard let currentUser = appState.currentUser,
              !hasRequestedNotificationsThisSession else {
            return
        }
        let userKey = "notifications_requested_\(currentUser.id)"
        let hasAlreadyRequested = UserDefaults.standard.bool(forKey: userKey)
        if hasAlreadyRequested {
            return
        }
        Task { @MainActor in
            await self.requestNotificationPermissions()
        }
    }
    
    /// Demande les permissions de notifications avec la popup native iOS
    @MainActor
    private func requestNotificationPermissions() async {
        guard let currentUser = appState.currentUser else { return }
        hasRequestedNotificationsThisSession = true
        do {
            let granted = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
            if granted {
                FCMService.shared.requestTokenAndSave()
            }
            let userKey = "notifications_requested_\(currentUser.id)"
            UserDefaults.standard.set(true, forKey: userKey)
        } catch {
            print("❌ Notifications: Erreur lors de la demande - \(error)")
        }
    }


struct DailyQuestionCard: View {
    let question: DailyQuestion
    
    var body: some View {
        VStack(spacing: 0) {
            // Header de la carte
            VStack(spacing: 8) {
                Text("Love2Love")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 20)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 1.0, green: 0.4, blue: 0.6),
                        Color(red: 1.0, green: 0.6, blue: 0.8)
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
    let isLastMessage: Bool // 🎯 TWITTER STYLE: Savoir si c'est le dernier message
    let isPreviousSameSender: Bool // 🎯 ESPACEMENT: Déterminer si même expéditeur que précédent
    
    var body: some View {
        HStack(alignment: .bottom, spacing: 0) {
            if isCurrentUser {
                // Espace libre à gauche
                Spacer(minLength: 80) // 🎯 LARGEUR LIMITÉE: 70% max pour les messages
                
                // Message utilisateur aligné à droite
                VStack(alignment: .trailing, spacing: 4) {
                    messageContent
                        .background(
                            RoundedRectangle(cornerRadius: 18)
                                .fill(Color(hex: "#FD267A")) // Rose Love2Love pour l'utilisateur
                        )
                        .foregroundColor(.white)
                    
                    // 🎯 TWITTER STYLE: Heure seulement sur dernier message
                    if isLastMessage {
                        Text(response.respondedAt.formatted(date: .omitted, time: .shortened))
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .padding(.trailing, 8)
                    }
                }
            } else {
                // Message partenaire aligné à gauche
                VStack(alignment: .leading, spacing: 4) {
                    messageContent
                        .background(
                            RoundedRectangle(cornerRadius: 18)
                                .fill(Color(UIColor.systemGray6)) // Gris système pour le partenaire
                        )
                        .foregroundColor(.primary)
                    
                    // 🎯 TWITTER STYLE: Heure seulement sur dernier message
                    if isLastMessage {
                        Text(response.respondedAt.formatted(date: .omitted, time: .shortened))
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .padding(.leading, 8)
                    }
                }
                
                // Espace libre à droite
                Spacer(minLength: 80) // 🎯 LARGEUR LIMITÉE: 70% max pour les messages
            }
        }
        .padding(.horizontal, 16) // 🎯 MARGES: Espace des bords de l'écran
        .padding(.vertical, isPreviousSameSender ? 1.5 : 3) // 🎯 ESPACEMENT +1px: Plus d'air entre les messages
    }
    
    private var messageContent: some View {
        ChatText(
            response.text,
            font: UIFont.systemFont(ofSize: 17), // Font.body équivalent
            textColor: isCurrentUser ? UIColor.white : UIColor.label,
            textAlignment: .left, // 🎯 ALIGNEMENT UNIFORME: Texte toujours aligné à gauche dans sa bulle
            isCurrentUser: isCurrentUser
        )
        .padding(.horizontal, 12) // 🎯 COMPACT: Padding interne réduit (16→12)
        .padding(.vertical, 8) // 🎯 COMPACT: Padding vertical réduit (12→8)
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
                    
                    // Afficher confirmation à l'utilisateur
                } else {
                }
                
            } catch {
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
} // Accolade fermante finale pour DailyQuestionMainView

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
