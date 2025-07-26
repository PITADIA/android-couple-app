import Foundation
import FirebaseFirestore
import FirebaseAuth
import FirebaseFunctions
import Combine
import RealmSwift
import UserNotifications

@MainActor
class DailyQuestionService: ObservableObject {
    static let shared = DailyQuestionService()
    
    @Published var currentQuestion: DailyQuestion?
    @Published var questionHistory: [DailyQuestion] = []
    @Published var isLoading = false
    @Published var allQuestionsExhausted = false
    @Published var currentSettings: DailyQuestionSettings?
    
    private var db = Firestore.firestore()
    private var functions = Functions.functions()
    private var questionListener: ListenerRegistration?
    private var responsesListener: ListenerRegistration? // NOUVEAU: Listener pour sous-collections
    private var settingsListener: ListenerRegistration?
    
    // CORRECTION: Référence weak à AppState pour éviter les cycles de référence
    private weak var appState: AppState?
    
    private init() {
        // Les listeners seront configurés via configure(with:)
    }
    
    deinit {
        questionListener?.remove()
        responsesListener?.remove()
        settingsListener?.remove()
    }
    
    // MARK: - Configuration
    
    func configure(with appState: AppState) {
        self.appState = appState
        setupListeners()
    }
    
    // MARK: - Setup et Lifecycle
    
    private func setupListeners() {
        guard let currentUser = Auth.auth().currentUser,
              let appState = appState,
              let currentAppUser = appState.currentUser,
              let partnerId = currentAppUser.partnerId,
              !partnerId.isEmpty else {
            print("🔥 DailyQuestionService: Pas d'utilisateur connecté ou de couple configuré")
            return
        }
        
        // CORRECTION: Créer le coupleId comme dans le reste de l'app
        let coupleId = [currentUser.uid, partnerId].sorted().joined(separator: "_")
        print("🔥 DailyQuestionService: Écoute des questions pour couple: \(coupleId)")
            
        // Écouter les settings
        setupSettingsListener(for: coupleId)
        
        // Écouter les questions
        setupQuestionsListener(for: coupleId)
            }
            
    private func setupSettingsListener(for coupleId: String) {
        settingsListener?.remove()
        
        settingsListener = db.collection("dailyQuestionSettings")
            .document(coupleId)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    if let error = error {
                        print("❌ DailyQuestionService: Erreur settings - \(error)")
            return
        }
        
                    if let data = snapshot?.data() {
                        print("⚙️ SETTINGS FIRESTORE REÇUS:")
                        print("⚙️ - Raw data: \(data)")
                        
                        do {
                            let oldCurrentDay = self?.currentSettings?.currentDay ?? 0
                            let settings = try self?.createSettingsFromFirestore(data, coupleId: coupleId)
                            self?.currentSettings = settings
                            
                            print("⚙️ SETTINGS LISTENER UPDATE:")
                            print("⚙️ - Ancien currentDay: \(oldCurrentDay)")
                            print("⚙️ - Nouveau currentDay: \(settings?.currentDay ?? 0)")
                            print("⚙️ - startDate: \(settings?.startDate ?? Date())")
                            print("⚙️ - timezone: \(settings?.timezone ?? "unknown")")
                            
                            if oldCurrentDay != settings?.currentDay {
                                print("🚨 CHANGEMENT CURRENTDAY DÉTECTÉ! \(oldCurrentDay) → \(settings?.currentDay ?? 0)")
                            } else {
                                print("⚠️ CURRENTDAY INCHANGÉ: \(settings?.currentDay ?? 0)")
                            }
                            
                print("✅ DailyQuestionService: Settings chargés pour couple \(coupleId)")
                        } catch {
                            print("❌ DailyQuestionService: Erreur décodage settings - \(error)")
                        }
                    }
                }
            }
    }
    
    // CORRECTION: Méthode pour créer DailyQuestionSettings depuis Firestore
    private func createSettingsFromFirestore(_ data: [String: Any], coupleId: String) throws -> DailyQuestionSettings {
        let startDate: Date
        if let timestamp = data["startDate"] as? Timestamp {
            startDate = timestamp.dateValue()
        } else {
            startDate = Date()
        }
        
        let timezone = data["timezone"] as? String ?? "Europe/Paris"
        let currentDay = data["currentDay"] as? Int ?? 1
        
        var settings = DailyQuestionSettings(coupleId: coupleId, startDate: startDate, timezone: timezone)
        settings.currentDay = currentDay
        
        if let lastVisitTimestamp = data["lastVisitDate"] as? Timestamp {
            settings.lastVisitDate = lastVisitTimestamp.dateValue()
        }
        
        return settings
    }
    
    private func setupQuestionsListener(for coupleId: String) {
        questionListener?.remove()
        
        print("🔥 DailyQuestionService: Écoute des questions pour couple: \(coupleId)")
        
        questionListener = db.collection("dailyQuestions")
            .whereField("coupleId", isEqualTo: coupleId)
            .order(by: "scheduledDateTime", descending: true)
            .limit(to: 10)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    print("🎯 DailyQuestionService: Listener déclenché")
                    
                    if let error = error {
                        print("❌ DailyQuestionService: Erreur questions - \(error)")
                        
                        // Fallback vers le cache Realm en cas d'erreur
                        await self?.loadFromRealmCache(coupleId: coupleId)
                        return
                    }
                    
                    guard let documents = snapshot?.documents else { 
                        print("❌ DailyQuestionService: Aucun document dans snapshot")
                        // Fallback vers le cache Realm si pas de documents
                        await self?.loadFromRealmCache(coupleId: coupleId)
                        return 
                    }
                    
                    print("📊 DailyQuestionService: \(documents.count) document(s) trouvé(s) dans Firestore")
                    
                    // Logs détaillés des documents trouvés
                    for (index, document) in documents.enumerated() {
                        let data = document.data()
                        let questionKey = data["questionKey"] as? String ?? "inconnu"
                        let questionDay = data["questionDay"] as? Int ?? 0
                        let scheduledDate = data["scheduledDate"] as? String ?? "inconnu"
                        print("📝 Document \(index + 1): ID=\(document.documentID), questionKey=\(questionKey), day=\(questionDay), date=\(scheduledDate)")
                    }
                    
                    // Si pas de documents dans Firestore, essayer le cache
                    if documents.isEmpty {
                        print("⚠️ DailyQuestionService: Aucun document - Fallback vers cache Realm")
                        await self?.loadFromRealmCache(coupleId: coupleId)
                        return
                    }
                    
                    var questions: [DailyQuestion] = []
                    for (index, document) in documents.enumerated() {
                        do {
                            print("🔄 DailyQuestionService: Décodage document \(index + 1)...")
                            var question = try document.data(as: DailyQuestion.self)
                            
                            print("✅ DailyQuestionService: Question décodée - \(question.questionKey), jour \(question.questionDay)")
                            
                            // NOUVEAU: Charger les réponses depuis la sous-collection
                            question = await self?.loadResponsesForQuestion(question) ?? question
                            
                            questions.append(question)
                            
                            // Cache dans Realm pour lecture hors ligne
                            QuestionCacheManager.shared.cacheDailyQuestion(question)
                        } catch {
                            print("❌ DailyQuestionService: Erreur décodage question \(index + 1) - \(error)")
                        }
                    }
                    
                    print("📚 DailyQuestionService: \(questions.count) question(s) décodée(s) avec succès")
                    
                    self?.questionHistory = questions
                    let previousCurrentQuestion = self?.currentQuestion
                    self?.currentQuestion = questions.first
                    
                    if let current = questions.first {
                        print("🎯 DailyQuestionService: Question actuelle définie:")
                        print("   - questionKey: \(current.questionKey)")
                        print("   - questionDay: \(current.questionDay)")
                        print("   - scheduledDate: \(current.scheduledDate)")
                        print("   - id: \(current.id)")
                        
                        if let previous = previousCurrentQuestion {
                            print("🔄 DailyQuestionService: Changement de question:")
                            print("   - Ancienne: \(previous.questionKey) (jour \(previous.questionDay))")
                            print("   - Nouvelle: \(current.questionKey) (jour \(current.questionDay))")
                        }
                        
                        // Configurer listener pour les réponses de la question actuelle
                        await self?.setupResponsesListener(for: current)
                        
                        // Programmer la notification de rappel à 21h si nécessaire
                        await self?.scheduleDailyQuestionReminder(for: current)
                    } else {
                        print("❌ DailyQuestionService: Aucune question actuelle définie")
                    }
                    
                    print("✅ DailyQuestionService: Historique chargé - \(questions.count) questions")
            }
        }
    }
    
    // NOUVEAU: Charger les réponses depuis la sous-collection
    private func loadResponsesForQuestion(_ question: DailyQuestion) async -> DailyQuestion {
        do {
            let responsesSnapshot = try await db.collection("dailyQuestions")
                .document(question.id)
                .collection("responses")
                .order(by: "respondedAt")
                .getDocuments()
            
            var responses: [QuestionResponse] = []
            for document in responsesSnapshot.documents {
                if let response = try? document.data(as: QuestionResponse.self) {
                    responses.append(response)
                }
            }
            
            var updatedQuestion = question
            updatedQuestion.responsesFromSubcollection = responses
            
            return updatedQuestion
        } catch {
            print("❌ DailyQuestionService: Erreur chargement réponses - \(error)")
            return question
        }
    }
    
    // NOUVEAU: Écouter les changements dans les réponses de la question actuelle
    private func setupResponsesListener(for question: DailyQuestion) async {
        responsesListener?.remove()
        
        responsesListener = db.collection("dailyQuestions")
            .document(question.id)
            .collection("responses")
            .order(by: "respondedAt")
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                if let error = error {
                        print("❌ DailyQuestionService: Erreur listener réponses - \(error)")
                    return
                }
                
                    guard let documents = snapshot?.documents else { return }
                    
                    var responses: [QuestionResponse] = []
                    for document in documents {
                        if let response = try? document.data(as: QuestionResponse.self) {
                            responses.append(response)
                        }
                    }
                    
                    // Mettre à jour la question actuelle avec les nouvelles réponses
                    if var currentQuestion = self?.currentQuestion, currentQuestion.id == question.id {
                        let previousResponsesCount = currentQuestion.responsesFromSubcollection.count
                        currentQuestion.responsesFromSubcollection = responses
                        self?.currentQuestion = currentQuestion
                        
                        // Mettre à jour aussi dans l'historique
                        if let index = self?.questionHistory.firstIndex(where: { $0.id == question.id }) {
                            self?.questionHistory[index] = currentQuestion
                        }
                        
                        print("✅ DailyQuestionService: Réponses mises à jour - \(responses.count) réponses")
                        
                        // NOUVEAU: Notifier si nouvelles réponses détectées
                        if responses.count > previousResponsesCount,
                           let newResponse = responses.last {
                            await self?.scheduleNewMessageNotification(for: currentQuestion, newResponse: newResponse)
                        }
                        
                        // Mettre à jour notification après changement de réponses
                        await self?.scheduleDailyQuestionReminder(for: currentQuestion)
                    }
                }
            }
    }
    
    // MARK: - Computed Properties
    
    var coupleId: String? {
        guard let currentUser = Auth.auth().currentUser,
              let appUser = appState?.currentUser,
              let partnerId = appUser.partnerId,
              !partnerId.isEmpty else {
            return nil
        }
        
        return [currentUser.uid, partnerId].sorted().joined(separator: "_")
    }
    
    // MARK: - Actions publiques
    
    func generateTodaysQuestion() async {
        guard let currentUser = Auth.auth().currentUser,
              let coupleId = coupleId else {
            print("❌ DailyQuestionService: Pas d'utilisateur connecté")
            return
        }
        
        print("🚀 DailyQuestionService: Début génération question pour couple: \(coupleId)")

        // NOUVEAU: Bloquer si pas de settings disponibles
        guard let settings = currentSettings else {
            print("⚙️ DailyQuestionService: Pas encore de settings – on attend le listener")
            return
        }
        
        print("⚙️ SETTINGS CHARGÉS:")
        print("⚙️ - startDate: \(settings.startDate)")
        print("⚙️ - currentDay: \(settings.currentDay)")
        print("⚙️ - timezone: \(settings.timezone)")

        // Vérifier si une nouvelle question doit réellement être générée (après 21h)
        if DailyQuestionGenerator.shouldShowNewQuestion(settings: settings) == false {
            print("ℹ️ DailyQuestionService: Pas encore l'heure de la nouvelle question – génération annulée")
            return
        }
        
        isLoading = true
        defer { isLoading = false }

        do {
            // Calculer le jour attendu basé sur le temps écoulé
            let expectedDay = DailyQuestionGenerator.calculateCurrentQuestionDay(for: coupleId, settings: settings) ?? settings.currentDay
            
            print("⚙️ CALCUL EXPECTEDDAY:")
            print("⚙️ - expectedDay calculé: \(expectedDay)")
            print("⚙️ - settings.currentDay: \(settings.currentDay)")
            
            // CORRECTION TIMEZONE: Utiliser UTC pour éviter les problèmes startOfDay
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = TimeZone(identifier: "UTC")!
            let startOfDay = calendar.startOfDay(for: settings.startDate)
            let startOfToday = calendar.startOfDay(for: Date())
            let daysSinceStart = calendar.dateComponents([.day], from: startOfDay, to: startOfToday).day ?? 0
            
            print("⚙️ DATES (UTC FIXED):")
            print("⚙️ - startOfDay: \(startOfDay)")
            print("⚙️ - startOfToday: \(startOfToday)")
            print("⚙️ - daysSinceStart: \(daysSinceStart)")
            
            print("🌐 APPEL CLOUD FUNCTION:")
            print("🌐 - coupleId: \(coupleId)")
            print("🌐 - userId: \(currentUser.uid)")
            print("🌐 - questionDay: \(expectedDay)")
            print("🌐 - timezone: \(TimeZone.current.identifier)")
            
            let result = try await functions.httpsCallable("generateDailyQuestion").call([
                "coupleId": coupleId,
                "userId": currentUser.uid,
                "questionDay": expectedDay,
                "timezone": TimeZone.current.identifier
            ])

            print("📥 RÉPONSE CLOUD FUNCTION:")
            
            if let data = result.data as? [String: Any],
               let success = data["success"] as? Bool,
               success {
                    print("✅ Question générée avec succès")
                
                if let questionData = data["question"] as? [String: Any] {
                    let questionKey = questionData["questionKey"] as? String ?? "inconnu"
                    let questionDay = questionData["questionDay"] as? Int ?? 0
                    print("⚙️ - questionKey reçue: \(questionKey)")
                    print("⚙️ - questionDay retourné: \(questionDay)")
                }
                
                // Mettre à jour le jour courant vers le jour attendu
                if var settings = self.currentSettings {
                    settings.currentDay = expectedDay
                    self.currentSettings = settings
                    print("⚙️ Settings mis à jour localement - currentDay: \(expectedDay)")

                    // Mettre à jour Firestore pour persister le nouveau currentDay
                    if let coupleId = self.coupleId {
                        do {
                            try await db.collection("dailyQuestionSettings")
                                .document(coupleId)
                                .updateData(["currentDay": settings.currentDay,
                                             "lastVisitDate": Timestamp(date: Date())])
                            print("✅ DailyQuestionService: currentDay mis à jour → \(settings.currentDay)")
                        } catch {
                            print("❌ DailyQuestionService: Impossible de mettre à jour currentDay - \(error)")
                        }
                    }
                }

                if let exhausted = data["allQuestionsExhausted"] as? Bool, exhausted {
                    allQuestionsExhausted = true
                    }
                } else {
                    print("❌ DailyQuestionService: Échec génération question")
            }
        } catch {
            print("❌ DailyQuestionService: Erreur génération - \(error)")
        }
    }
    
    // NOUVEAU: Soumettre une réponse via le système de sous-collections
    func submitResponse(_ responseText: String) async -> Bool {
        guard let currentUser = Auth.auth().currentUser,
              let userName = appState?.currentUser?.name,
              let question = currentQuestion else {
            print("❌ DailyQuestionService: Données manquantes pour la réponse")
            return false
        }
        
        print("🔥 DailyQuestionService: Soumission réponse:")
        print("   - Question ID: \(question.id)")
        print("   - Utilisateur: \(currentUser.uid)")
        print("   - Nom: \(userName)")
        print("   - Texte: '\(responseText)'")
        print("🔥 DailyQuestionService: Cette action devrait déclencher notifyPartnerResponseSubcollection")
        
        do {
            let result = try await functions.httpsCallable("submitDailyQuestionResponse").call([
                "questionId": question.id,
                "responseText": responseText,
                "userName": userName
            ])
            
            if let data = result.data as? [String: Any],
               let success = data["success"] as? Bool,
               success {
            print("✅ DailyQuestionService: Réponse soumise avec succès")
            print("📨 DailyQuestionService: Cloud Function devrait envoyer push au partenaire maintenant")
            return true
            } else {
                print("❌ DailyQuestionService: Échec soumission réponse")
                if let data = result.data as? [String: Any],
                   let message = data["message"] as? String {
                    print("❌ DailyQuestionService: Message: \(message)")
                }
                return false
            }
        } catch {
            print("❌ DailyQuestionService: Erreur soumission - \(error)")
            return false
        }
    }
    
    // MARK: - Migration Support
    
    func migrateTodaysQuestionToSubcollections() async -> Bool {
        guard let currentUser = Auth.auth().currentUser,
              let coupleId = coupleId else {
            print("❌ DailyQuestionService: Pas d'utilisateur connecté pour migration")
            return false
        }
        
        do {
            let result = try await functions.httpsCallable("migrateDailyQuestionResponses").call([
                "coupleId": coupleId
            ])
            
            if let data = result.data as? [String: Any],
               let success = data["success"] as? Bool,
               success {
                print("✅ DailyQuestionService: Migration réussie")
                return true
            } else {
                print("❌ DailyQuestionService: Échec migration")
                return false
            }
        } catch {
            print("❌ DailyQuestionService: Erreur migration - \(error)")
            return false
        }
    }
    
    // MARK: - Helpers
    
    private func scheduleDailyQuestionReminder(for question: DailyQuestion) async {
        // NOUVEAU: Ne programmer que si L'UTILISATEUR ACTUEL n'a pas répondu
        // (peu importe si le partenaire a répondu ou non)
        guard let currentUserId = Auth.auth().currentUser?.uid,
              question.currentUserResponse == nil else {
            print("🔔 DailyQuestionService: L'utilisateur actuel a déjà répondu – pas de notification à programmer")
            UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["daily_question_reminder_\(question.id)"])
            return
        }
        
        let center = UNUserNotificationCenter.current()
        let identifier = "daily_question_reminder_\(question.id)"
        
        // Toujours nettoyer d'abord
        center.removePendingNotificationRequests(withIdentifiers: [identifier])
        
        // Calculer 21h locale aujourd'hui
        var components = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        components.hour = 21
        components.minute = 0
        components.second = 0
        guard let today21h = Calendar.current.date(from: components) else { return }
        let fireDate: Date
        if today21h > Date() {
            fireDate = today21h
        } else {
            fireDate = Calendar.current.date(byAdding: .day, value: 1, to: today21h) ?? today21h
        }
        
        // Construire le trigger calendrier
        let triggerComponents = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fireDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComponents, repeats: false)
        
        let content = UNMutableNotificationContent()
        content.title = NSLocalizedString("daily_question_notification_title", tableName: "DailyQuestions", comment: "")
        // Corps personnalisé si disponible dans .xcstrings sinon fallback
        let localizedBodyKey = "\(question.questionKey)_notification"
        let bodyLocalized = NSLocalizedString(localizedBodyKey, tableName: "DailyQuestions", comment: "")
        content.body = bodyLocalized == localizedBodyKey ? question.localizedText : bodyLocalized
        content.sound = .default
        content.badge = 1
        
        print("🔔 DailyQuestionService: Programmation notification locale:")
        print("   - ID: \(identifier)")
        print("   - Date: \(fireDate)")
        print("   - Title: \(content.title)")
        print("   - Body: \(content.body)")
        
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
        do {
            try await center.add(request)
            print("✅ DailyQuestionService: Notification quotidienne programmée avec succès")
        } catch {
            print("❌ DailyQuestionService: Erreur programmation notification - \(error)")
        }
    }
    
    // MARK: - Notifications pour nouveaux messages
    
    private func scheduleNewMessageNotification(for question: DailyQuestion, newResponse: QuestionResponse) async {
        // Ne notifier que si le message vient du partenaire
        guard let currentUserId = Auth.auth().currentUser?.uid,
              newResponse.userId != currentUserId else {
            print("🔔 DailyQuestionService: Message de l'utilisateur actuel – pas de notification")
            return
        }
        
        let center = UNUserNotificationCenter.current()
        let identifier = "new_message_\(question.id)_\(newResponse.id)"
        
        let content = UNMutableNotificationContent()
        content.title = NSLocalizedString("daily_question_notification_title", tableName: "DailyQuestions", comment: "")
        content.body = "💬 \(newResponse.userName): \(String(newResponse.text.prefix(50)))\(newResponse.text.count > 50 ? "..." : "")"
        content.sound = .default
        content.badge = 1
        
        // Notification immédiate pour nouveau message
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)
        
        print("🔔 DailyQuestionService: Programmation notification nouveau message:")
        print("   - ID: \(identifier)")
        print("   - De: \(newResponse.userName)")
        print("   - Preview: \(String(newResponse.text.prefix(30)))...")
        
        do {
            try await center.add(request)
            print("✅ DailyQuestionService: Notification nouveau message programmée")
        } catch {
            print("❌ DailyQuestionService: Erreur notification nouveau message - \(error)")
        }
    }
    
    
    /// CORRECTION TEMPORAIRE : Corriger les settings de questions quotidiennes
    func fixDailyQuestionSettings() async {
        guard let currentUser = Auth.auth().currentUser,
              let coupleId = coupleId else {
            print("❌ DailyQuestionService: Pas d'utilisateur pour correction")
            return
        }
        
        print("🔧 DailyQuestionService: Correction des settings...")
        
        do {
            let result = try await functions.httpsCallable("fixDailyQuestionSettings").call([
                "coupleId": coupleId
            ])
            
            if let data = result.data as? [String: Any],
               let success = data["success"] as? Bool,
               success {
                print("✅ DailyQuestionService: Settings corrigés avec succès")
                print("✅ Message: \(data["message"] as? String ?? "")")
                
                // Recharger les settings après correction
                if let currentCoupleId = self.coupleId {
                    setupSettingsListener(for: currentCoupleId)
                }
                
                // Regénérer la question d'aujourd'hui
                await generateTodaysQuestion()
            } else {
                print("❌ DailyQuestionService: Échec correction settings")
            }
        } catch {
            print("❌ DailyQuestionService: Erreur correction - \(error)")
        }
    }
    
    // NOUVEAU: Chargement depuis le cache Realm en cas de problème Firestore
    private func loadFromRealmCache(coupleId: String) async {
        print("🔄 DailyQuestionService: Chargement depuis le cache Realm pour couple: \(coupleId)")
        
        let cachedQuestions = QuestionCacheManager.shared.getCachedDailyQuestions(for: coupleId, limit: 10)
        
        print("📦 DailyQuestionService: \(cachedQuestions.count) questions trouvées dans le cache Realm")
        
        // Logs détaillés des questions en cache
        for (index, question) in cachedQuestions.enumerated() {
            print("📝 Cache \(index + 1): questionKey=\(question.questionKey), day=\(question.questionDay), date=\(question.scheduledDate)")
        }
        
        if !cachedQuestions.isEmpty {
            self.questionHistory = cachedQuestions
            self.currentQuestion = cachedQuestions.first
            
            print("✅ DailyQuestionService: \(cachedQuestions.count) questions chargées depuis le cache")
            
            if let current = cachedQuestions.first {
                print("🎯 DailyQuestionService: Question actuelle chargée depuis le cache:")
                print("   - questionKey: \(current.questionKey)")
                print("   - questionDay: \(current.questionDay)")
                print("   - scheduledDate: \(current.scheduledDate)")
            }
        } else {
            print("❌ DailyQuestionService: Aucune question trouvée dans le cache")
        }
    }
    
    func shouldAutoMigrate() -> Bool {
        // Auto-migrer si on détecte l'ancien format
        return currentQuestion?.shouldUseLegacyMode == true
    }
    
    func refreshCurrentQuestion() async {
        // Forcer un refresh des données
        setupListeners()
    }
    
    func loadQuestionHistory() async {
        // Pour les statistiques seulement : charger l'historique récent
        guard let coupleId = coupleId else {
            print("❌ DailyQuestionService: Pas de coupleId pour l'historique")
            return
        }
        
        do {
            let snapshot = try await db.collection("dailyQuestions")
                .whereField("coupleId", isEqualTo: coupleId)
                .order(by: "scheduledDate", descending: true)
                .limit(to: 30) // Seulement les 30 dernières pour les stats
                .getDocuments()
            
            var questions: [DailyQuestion] = []
            
            for document in snapshot.documents {
                do {
                    var question = try document.data(as: DailyQuestion.self)
                    question = await loadResponsesForQuestion(question)
                    questions.append(question)
                } catch {
                    print("❌ DailyQuestionService: Erreur décodage question historique - \(error)")
                }
            }
            
            questionHistory = questions
            print("📊 DailyQuestionService: \(questions.count) questions chargées pour statistiques")
        } catch {
            print("❌ DailyQuestionService: Erreur chargement historique - \(error)")
        }
    }
}

// MARK: - Extensions pour DailyQuestion

extension DailyQuestion {
    func getCurrentUserResponse(userId: String) -> QuestionResponse? {
        return responses[userId]
    }
    
    func getPartnerResponse(excluding userId: String) -> QuestionResponse? {
        return responses.values.first { $0.userId != userId }
    }
    
    func canUserRespond(userId: String) -> Bool {
        let userResponse = responses[userId]
        return userResponse?.status != .answered && !isExpired
    }
    
    func shouldShowWaitingMessage(for userId: String) -> Bool {
        let userResponse = responses[userId]
        return userResponse?.status == .answered && !bothResponded
    }
} 