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
    @Published var isLoading: Bool = false
    @Published var isOptimizing: Bool = false  // Nouvel état pour l'optimisation timezone
    @Published var allQuestionsExhausted: Bool = false
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
        
        // 🌍 Sauvegarder la langue utilisateur pour les notifications localisées
        saveUserLanguageToFirebase()
        
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
                        // 🔧 LOGS TIMEZONE DÉTAILLÉS POUR SETTINGS
                        let currentTime = Date()
                        let timeFormatter = DateFormatter()
                        timeFormatter.timeStyle = .long
                        timeFormatter.dateStyle = .short
                        
                        print("⚙️ SETTINGS FIRESTORE REÇUS À: \(timeFormatter.string(from: currentTime))")
                        print("⚙️ - Timestamp exact: \(currentTime)")
                        print("⚙️ - Raw data: \(data)")
                        
                        do {
                            let oldCurrentDay = self?.currentSettings?.currentDay ?? 0
                            let settings = try self?.createSettingsFromFirestore(data, coupleId: coupleId)
                            self?.currentSettings = settings
                            
                            print("⚙️ SETTINGS LISTENER UPDATE:")
                            print("⚙️ - Ancien currentDay: \(oldCurrentDay)")
                            print("⚙️ - Nouveau currentDay: \(settings?.currentDay ?? 0)")
                            print("⚙️ - startDate: \(settings?.startDate ?? Date())")
                            print("⚙️ - startDate ISO: \(ISO8601DateFormatter().string(from: settings?.startDate ?? Date()))")
                            print("⚙️ - timezone: \(settings?.timezone ?? "unknown")")
                            
                            if oldCurrentDay != settings?.currentDay {
                                print("🚨 CHANGEMENT CURRENTDAY DÉTECTÉ! \(oldCurrentDay) → \(settings?.currentDay ?? 0)")
                                print("🚨 - MOMENT EXACT: \(timeFormatter.string(from: currentTime))")
                                print("🚨 - DÉCLENCHEUR: settings reçus de Firestore")
                                
                                // 🔧 NOUVEAU: Calculer si c'est normal selon l'heure
                                let localCalendar = Calendar.current
                                let startOfToday = localCalendar.startOfDay(for: Date())
                                print("🚨 - startOfDay local: \(startOfToday)")
                                print("🚨 - Heures depuis minuit: \(Calendar.current.dateComponents([.hour, .minute], from: startOfToday, to: currentTime))")
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
                    
                    print("🔄 DailyQuestionService: Assignation currentQuestion...")
                    print("🔄 - questions.count: \(questions.count)")
                    print("🔄 - questions.first: \(questions.first?.questionKey ?? "nil")")
                    print("🔄 - previousCurrentQuestion: \(previousCurrentQuestion?.questionKey ?? "nil")")
                    print("🔄 - currentQuestion actuelle: \(self?.currentQuestion?.questionKey ?? "nil")")
                    
                    // 🚀 OPTIMISATION: Éviter les doublons si la question a déjà été mise à jour
                    let newQuestion = questions.first
                    if self?.currentQuestion?.id != newQuestion?.id {
                        print("🔄 DailyQuestionService: Nouvelle question détectée via listener")
                        self?.currentQuestion = newQuestion
                    } else {
                        print("🔄 DailyQuestionService: Question déjà à jour (optimisation immédiate)")
                    }
                    
                    print("🔄 DailyQuestionService: Après assignation:")
                    print("🔄 - self.currentQuestion: \(self?.currentQuestion?.questionKey ?? "nil")")
                    
                    if let current = questions.first {
                        print("🎯 DailyQuestionService: Question actuelle définie:")
                        print("   - questionKey: \(current.questionKey)")
                        print("   - questionDay: \(current.questionDay)")
                        print("   - scheduledDate: \(current.scheduledDate)")
                        print("   - id: \(current.id)")
                        print("   - 📱 UI VA SE METTRE À JOUR AVEC CETTE QUESTION")
                        
                        if let previous = previousCurrentQuestion {
                            print("🔄 DailyQuestionService: Changement de question:")
                            print("   - Ancienne: \(previous.questionKey) (jour \(previous.questionDay))")
                            print("   - Nouvelle: \(current.questionKey) (jour \(current.questionDay))")
                        } else {
                            print("🆕 DailyQuestionService: PREMIÈRE QUESTION pour ce couple!")
                        }
                        
                        // Configurer listener pour les réponses de la question actuelle
                        await self?.setupResponsesListener(for: current)
                        
                        // 🗑️ SUPPRIMÉ : Programmation notification de rappel 21h
                        // Plus besoin de notifications locales programmées
                    } else {
                        print("❌ DailyQuestionService: Aucune question actuelle définie")
                        print("❌ - L'UI VA AFFICHER 'Aucune question disponible'")
                        print("❌ - Vérifier la génération de question ou le cache Realm")
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
                        
                        // 🗑️ SUPPRIMÉ : Mise à jour notification de rappel
                        // Plus besoin de notifications locales programmées
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

        // 🔧 NOUVEAUX LOGS TIMEZONE DÉTAILLÉS
        print("🕐 DailyQuestionService: TIMEZONE DEBUG:")
        print("🕐 - Date actuelle: \(Date())")
        print("🕐 - TimeZone current: \(TimeZone.current.identifier)")
        print("🕐 - TimeZone current offset: \(TimeZone.current.secondsFromGMT()) secondes")
        print("🕐 - Calendar timezone: \(Calendar.current.timeZone.identifier)")

        // 🔧 NOUVEAU: Créer les settings s'ils n'existent pas encore
        var settings = currentSettings
        if settings == nil {
            print("⚙️ DailyQuestionService: Aucun settings - Création via Cloud Function")
            
            do {
                let result = try await functions.httpsCallable("getOrCreateDailyQuestionSettings").call([
                    "coupleId": coupleId,
                    "timezone": TimeZone.current.identifier
                ])
                
                if let data = result.data as? [String: Any],
                   let success = data["success"] as? Bool,
                   success,
                   let settingsData = data["settings"] as? [String: Any] {
                    
                    print("✅ DailyQuestionService: Settings créés/récupérés via Cloud Function")
                    
                    // Le listener des settings se déclenchera automatiquement
                    // On attend un petit délai pour que currentSettings soit mis à jour
                    for attempt in 1...5 {
                        if currentSettings != nil {
                            settings = currentSettings
                            print("✅ DailyQuestionService: Settings disponibles après tentative \(attempt)")
                            break
                        }
                        print("⏳ DailyQuestionService: Attente settings (tentative \(attempt)/5)")
                        try await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconde
                    }
                    
                    if settings == nil {
                        print("❌ DailyQuestionService: Settings toujours indisponibles après 2.5s")
                        return
                    }
                } else {
                    print("❌ DailyQuestionService: Erreur réponse getOrCreateDailyQuestionSettings")
                    return
                }
            } catch {
                print("❌ DailyQuestionService: Erreur getOrCreateDailyQuestionSettings: \(error)")
                return
            }
        }
        
        guard let finalSettings = settings else {
            print("❌ DailyQuestionService: Settings toujours indisponibles")
            return
        }
        
        print("⚙️ SETTINGS CHARGÉS:")
        print("⚙️ - startDate: \(finalSettings.startDate)")
        print("⚙️ - startDate ISO: \(ISO8601DateFormatter().string(from: finalSettings.startDate))")
        print("⚙️ - currentDay: \(finalSettings.currentDay)")
        print("⚙️ - timezone: \(finalSettings.timezone)")
        
        // 🔧 NOUVEAU: Comparaison avec différents calendriers
        let localCalendar = Calendar.current
        let utcCalendar = Calendar(identifier: .gregorian)
        var utcCal = utcCalendar
        utcCal.timeZone = TimeZone(identifier: "UTC")!
        
        print("🗓️ CALENDRIER COMPARAISON:")
        print("🗓️ - Local startOfDay: \(localCalendar.startOfDay(for: Date()))")
        print("🗓️ - UTC startOfDay: \(utcCal.startOfDay(for: Date()))")
        print("🗓️ - Settings startDate local: \(localCalendar.startOfDay(for: finalSettings.startDate))")
        print("🗓️ - Settings startDate UTC: \(utcCal.startOfDay(for: finalSettings.startDate))")

        // Vérifier si une nouvelle question doit réellement être générée (après 21h)
        if DailyQuestionGenerator.shouldShowNewQuestion(settings: finalSettings) == false {
            print("ℹ️ DailyQuestionService: Pas encore l'heure de la nouvelle question – génération annulée")
            print("🔍 ANALYSE: settings.currentDay=\(finalSettings.currentDay), expectedDay calculé ci-dessus")
            print("🔍 SUGGESTION: Vérifier la logique shouldShowNewQuestion")
            return
        }
        
        print("✅ DailyQuestionService: shouldShowNewQuestion = true, génération autorisée!")
        
        isLoading = true
        defer { isLoading = false }

        do {
            // Calculer le jour attendu basé sur le temps écoulé
            let expectedDay = DailyQuestionGenerator.calculateCurrentQuestionDay(for: coupleId, settings: finalSettings) ?? finalSettings.currentDay
            
            print("⚙️ CALCUL EXPECTEDDAY:")
            print("⚙️ - expectedDay calculé: \(expectedDay)")
            print("⚙️ - settings.currentDay: \(finalSettings.currentDay)")
            print("⚙️ - Question sera générée pour le jour: \(expectedDay)")
            
            // CORRECTION TIMEZONE: Utiliser UTC pour éviter les problèmes startOfDay
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = TimeZone(identifier: "UTC")!
            let startOfDay = calendar.startOfDay(for: finalSettings.startDate)
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
            print("🌐 - Début appel generateDailyQuestion...")
            
            let result = try await functions.httpsCallable("generateDailyQuestion").call([
                "coupleId": coupleId,
                "userId": currentUser.uid,
                "questionDay": expectedDay,
                "timezone": TimeZone.current.identifier
            ])

            print("📥 RÉPONSE CLOUD FUNCTION:")
            print("📥 - Raw result: \(result)")
            print("📥 - Result data: \(result.data)")
            
            if let data = result.data as? [String: Any],
               let success = data["success"] as? Bool,
               success {
                    print("✅ Question générée avec succès")
                    print("✅ - Données complètes: \(data)")
                
                if let questionData = data["question"] as? [String: Any] {
                    let questionKey = questionData["questionKey"] as? String ?? "inconnu"
                    let questionDay = questionData["questionDay"] as? Int ?? 0
                    print("⚙️ - questionKey reçue: \(questionKey)")
                    print("⚙️ - questionDay retourné: \(questionDay)")
                    print("⚙️ - questionData complète: \(questionData)")
                    
                    // 🚀 OPTIMISATION: Créer immédiatement la question pour l'UI
                    // au lieu d'attendre le listener Firestore
                    if let questionId = questionData["id"] as? String,
                       let scheduledDate = questionData["scheduledDate"] as? String,
                       let status = questionData["status"] as? String {
                        
                        let immediateQuestion = DailyQuestion(
                            id: questionId,
                            coupleId: coupleId,
                            questionKey: questionKey,
                            questionDay: questionDay,
                            scheduledDate: scheduledDate,
                            scheduledDateTime: Date(), // Approximation pour l'UI
                            status: QuestionStatus(rawValue: status) ?? .pending,
                            createdAt: Date(),
                            updatedAt: Date(),
                            timezone: TimeZone.current.identifier,
                            responsesFromSubcollection: [],
                            legacyResponses: [:]
                        )
                        
                        print("🚀 DailyQuestionService: Mise à jour immédiate UI avec question générée")
                        self.currentQuestion = immediateQuestion
                        
                        // Cache immédiatement dans Realm
                        QuestionCacheManager.shared.cacheDailyQuestion(immediateQuestion)
                        print("🚀 DailyQuestionService: Question mise en cache immédiatement")
                    }
                }
                
                if let message = data["message"] as? String {
                    print("💬 - Message serveur: \(message)")
                }
                
                if let existingQuestion = data["existingQuestion"] as? [String: Any] {
                    print("🔄 - Question existante détectée: \(existingQuestion)")
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
                    print("⚠️ DailyQuestionService: Toutes les questions épuisées")
                    }
                } else {
                    print("❌ DailyQuestionService: Échec génération question")
                    print("❌ - Données reçues: \(result.data)")
                    
                    if let data = result.data as? [String: Any] {
                        if let success = data["success"] as? Bool {
                            print("❌ - success = \(success)")
                        }
                        if let error = data["error"] as? String {
                            print("❌ - Erreur serveur: \(error)")
                        }
                        if let message = data["message"] as? String {
                            print("❌ - Message serveur: \(message)")
                        }
                    }
            }
        } catch {
            print("❌ DailyQuestionService: Erreur génération - \(error)")
            print("❌ - Type d'erreur: \(type(of: error))")
            print("❌ - Description complète: \(error.localizedDescription)")
            
            if let functionsError = error as NSError? {
                print("❌ - Functions error code: \(functionsError.code)")
                print("❌ - Functions error domain: \(functionsError.domain)")
                print("❌ - Functions error userInfo: \(functionsError.userInfo)")
            }
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
    
    // 🗑️ FONCTION SUPPRIMÉE : scheduleDailyQuestionReminder
    // Cette fonction programmait des notifications locales iOS à 21h pour rappeler les questions
    // SUPPRIMÉE car seules les notifications de messages entre partenaires sont souhaitées
    
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
        
        // Supprimer les anciennes notifications pour cette question pour éviter l'accumulation
        let questionNotificationPrefix = "new_message_\(question.id)_"
        let pendingRequests = await center.pendingNotificationRequests()
        let oldNotificationIds = pendingRequests
            .filter { $0.identifier.hasPrefix(questionNotificationPrefix) && $0.identifier != identifier }
            .map { $0.identifier }
        
        if !oldNotificationIds.isEmpty {
            center.removePendingNotificationRequests(withIdentifiers: oldNotificationIds)
            print("🗑️ DailyQuestionService: \(oldNotificationIds.count) anciennes notifications supprimées pour question \(question.id)")
        }
        
        let content = UNMutableNotificationContent()
        // 🎯 FORMAT SIMPLIFIÉ : Nom partenaire en titre, message complet en body
        content.title = newResponse.userName
        content.body = newResponse.text
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
    
    /// Nettoie toutes les notifications en attente et remet le badge à 0
    func clearAllNotificationsAndBadge() {
        print("🧹 DailyQuestionService: Nettoyage notifications et badge")
        BadgeManager.clearAllNotificationsAndBadge()
    }
    
    /// Nettoie les notifications spécifiques à une question
    func clearNotificationsForQuestion(_ questionId: String) {
        let questionNotificationPrefix = "new_message_\(questionId)_"
        
        Task {
            let center = UNUserNotificationCenter.current()
            let pendingRequests = await center.pendingNotificationRequests()
            let notificationIds = pendingRequests
                .filter { $0.identifier.hasPrefix(questionNotificationPrefix) }
                .map { $0.identifier }
            
            if !notificationIds.isEmpty {
                center.removePendingNotificationRequests(withIdentifiers: notificationIds)
                print("🗑️ DailyQuestionService: \(notificationIds.count) notifications supprimées pour question \(questionId)")
            }
        }
    }
    
    
    // 🗑️ FONCTION DEBUG SUPPRIMÉE : fixDailyQuestionSettings()
    // Cette fonction était utilisée pour corriger les settings en mode debug
    // SUPPRIMÉE car plus nécessaire après les corrections timezone
    
    // NOUVEAU: Chargement depuis le cache Realm en cas de problème Firestore
    private func loadFromRealmCache(coupleId: String) async {
        print("🔄 DailyQuestionService: Chargement depuis le cache Realm pour couple: \(coupleId)")
        print("🔄 - RAISON: Fallback car Firestore n'a pas de documents ou erreur")
        
        let cachedQuestions = QuestionCacheManager.shared.getCachedDailyQuestions(for: coupleId, limit: 10)
        
        print("📦 DailyQuestionService: \(cachedQuestions.count) questions trouvées dans le cache Realm")
        
        // Logs détaillés des questions en cache
        for (index, question) in cachedQuestions.enumerated() {
            print("📝 Cache \(index + 1): questionKey=\(question.questionKey), day=\(question.questionDay), date=\(question.scheduledDate)")
        }
        
        if !cachedQuestions.isEmpty {
            print("✅ DailyQuestionService: Application des questions du cache...")
            
            self.questionHistory = cachedQuestions
            self.currentQuestion = cachedQuestions.first
            
            print("✅ DailyQuestionService: \(cachedQuestions.count) questions chargées depuis le cache")
            print("✅ - currentQuestion assignée: \(self.currentQuestion?.questionKey ?? "nil")")
            print("✅ - 📱 UI DEVRAIT SE METTRE À JOUR AVEC QUESTION DU CACHE")
            
            if let current = cachedQuestions.first {
                print("🎯 DailyQuestionService: Question actuelle chargée depuis le cache:")
                print("   - questionKey: \(current.questionKey)")
                print("   - questionDay: \(current.questionDay)")
                print("   - scheduledDate: \(current.scheduledDate)")
                print("   - id: \(current.id)")
            }
        } else {
            print("❌ DailyQuestionService: Aucune question trouvée dans le cache")
            print("❌ - self.currentQuestion reste: \(self.currentQuestion?.questionKey ?? "nil")")
            print("❌ - 📱 UI VA AFFICHER 'Aucune question disponible'")
            print("❌ - PROBLÈME: Ni Firestore ni cache Realm n'ont de questions!")
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
    
    // MARK: - 🌍 TIMEZONE OPTIMIZATION WITH REALM CACHE
    
    /// 🚀 Optimisation timezone avec cache Realm - réduit drastiquement les coûts Firebase
    func checkForNewQuestionWithTimezoneOptimization() async {
        // 🎯 ÉTAT DE CHARGEMENT POUR L'UI
        await MainActor.run {
            self.isOptimizing = true
        }
        
        defer {
            Task { @MainActor in
                self.isOptimizing = false
            }
        }
        
        let startTime = Date()
        print("\n🌍 === TIMEZONE OPTIMIZATION START ===")
        print("🕐 Heure locale: \(DateFormatter.timeFormatter.string(from: Date()))")
        print("🌍 Timezone: \(TimeZone.current.identifier)")
        print("🔄 isOptimizing = true → UI va afficher état de chargement")
        
        guard let coupleId = coupleId else {
            print("❌ Pas de coupleId - arrêt")
            return
        }
        
        // 📦 1. CHECK CACHE REALM EN PREMIER
        print("\n📦 Phase 1: Vérification cache Realm")
        let cachedQuestions = QuestionCacheManager.shared.getCachedDailyQuestions(for: coupleId, limit: 5)
        
        print("📋 \(cachedQuestions.count) questions trouvées dans le cache")
        for (index, question) in cachedQuestions.enumerated() {
            print("   \(index + 1). \(question.questionKey) - Jour \(question.questionDay) - \(question.scheduledDate)")
        }
        
        // 🕐 2. ANALYSE TIMEZONE LOCALE
        print("\n🕐 Phase 2: Analyse timezone locale")
        let localTime = Date()
        let calendar = Calendar.current
        let hour = calendar.component(.hour, from: localTime)
        let minute = calendar.component(.minute, from: localTime)
        let today = DateFormatter.dayFormatter.string(from: localTime)
        
        print("⏰ Heure exacte: \(hour):\(String(format: "%02d", minute))")
        print("📅 Date aujourd'hui: \(today)")
        
        // 🎯 3. CHECK SI NOUVELLE QUESTION ATTENDUE
        print("\n🎯 Phase 3: Check nouvelle question attendue")
        
        let todaysQuestion = cachedQuestions.first { $0.scheduledDate == today }
        
        if let existingQuestion = todaysQuestion {
            print("✅ Question d'aujourd'hui déjà en cache:")
            print("   - questionKey: \(existingQuestion.questionKey)")
            print("   - questionDay: \(existingQuestion.questionDay)")
            print("   - Pas besoin d'appel Firebase")
            
            // 🔄 Mettre à jour l'UI si nécessaire
            if currentQuestion?.questionKey != existingQuestion.questionKey {
                print("🔄 Mise à jour UI avec question cachée")
                DispatchQueue.main.async {
                    self.currentQuestion = existingQuestion
                }
            }
            
            print("⚡ Optimisation Realm: 0 appel Firebase nécessaire")
            return
        }
        
        // 🕐 4. CHECK TIMING POUR APPEL FIREBASE
        print("\n🕐 Phase 4: Check timing pour appel Firebase")
        
        // Éviter les appels inutiles - check seulement aux heures critiques
        let shouldCheckFirebase = isCriticalTimeForFirebaseCheck(hour: hour, minute: minute)
        
        // NOUVEAU: Si aucune question en cache OU aucune question pour aujourd'hui, on force l'appel Firebase
        let forceFirebaseBecauseNoCache = cachedQuestions.isEmpty
        let forceFirebaseBecauseNoToday   = todaysQuestion == nil
        if !shouldCheckFirebase && !forceFirebaseBecauseNoCache && !forceFirebaseBecauseNoToday {
            print("⏭️  Heure non critique (\(hour):\(String(format: "%02d", minute))) - Skip appel Firebase")
            print("🎯 Heures critiques: 00:00-00:05, 21:00-21:05")
            
            // Utiliser la dernière question du cache
            if let lastQuestion = cachedQuestions.first {
                print("📦 Utilisation dernière question du cache: \(lastQuestion.questionKey)")
                DispatchQueue.main.async {
                    self.currentQuestion = lastQuestion
                }
            }
            
            let executionTime = Date().timeIntervalSince(startTime)
            print("⚡ Temps d'exécution: \(Int(executionTime * 1000))ms")
            print("🌍 === TIMEZONE OPTIMIZATION END (CACHE ONLY) ===\n")
            return
        }
        if !shouldCheckFirebase {
            print("⚠️ Aucun cache local pour aujourd'hui - Appel Firebase forcé malgré heure non critique")
        }
        
        // 🚀 5. APPEL FIREBASE OPTIMISÉ
        print("\n🚀 Phase 5: Appel Firebase optimisé nécessaire")
        print("⏰ Heure critique détectée - vérification Firebase")
        
        // Sauvegarder les stats d'optimisation  
        let optimizationStats = TimezoneOptimizationStats(
            cacheHits: cachedQuestions.count,
            firebaseCallAvoided: false,
            criticalTime: true,
            timezone: TimeZone.current.identifier,
            localHour: hour
        )
        
        // Appel Firebase intelligent avec les données du cache
        await intelligentFirebaseCall(optimizationStats: optimizationStats)
        
        let executionTime = Date().timeIntervalSince(startTime)
        print("⚡ Temps d'exécution total: \(Int(executionTime * 1000))ms")
        print("🌍 === TIMEZONE OPTIMIZATION END (FIREBASE CALLED) ===\n")
    }
    
    /// 🕐 Détermine si c'est une heure critique pour checker Firebase
    private func isCriticalTimeForFirebaseCheck(hour: Int, minute: Int) -> Bool {
        // ✅ 00:00-00:05 : Nouvelles questions possibles
        if hour == 0 && minute <= 5 {
            print("🎯 Heure critique: Minuit - nouvelles questions possibles")
            return true
        }
        
        // 🔔 21:00-21:05 : Notifications de rappel
        if hour == 21 && minute <= 5 {
            print("🔔 Heure critique: 21h - notifications de rappel")
            return true
        }
        
        // ⏰ Autres heures critiques (si settings spéciaux)
        if let settings = currentSettings {
            // Check personnalisé selon les préférences du couple
            // TODO: Ajouter logique personnalisée si nécessaire
        }
        
        return false
    }
    
    /// 🚀 Appel Firebase intelligent avec optimisations
    private func intelligentFirebaseCall(optimizationStats: TimezoneOptimizationStats) async {
        print("🚀 intelligentFirebaseCall: Début appel optimisé")
        
        do {
            // Préparer les paramètres avec timezone locale
            let callParams: [String: Any] = [
                "timezone": TimeZone.current.identifier,
                "localHour": optimizationStats.localHour,
                "cacheStats": [
                    "cacheHits": optimizationStats.cacheHits,
                    "lastCacheUpdate": Date().timeIntervalSince1970
                ]
            ]
            
            print("📤 Paramètres envoyés:")
            print("   - timezone: \(TimeZone.current.identifier)")
            print("   - localHour: \(optimizationStats.localHour)")
            print("   - cacheHits: \(optimizationStats.cacheHits)")
            
            // Appeler la fonction de génération standard
            await generateTodaysQuestion()
            
            print("✅ Appel Firebase terminé avec succès")
            
        } catch {
            print("❌ Erreur appel Firebase: \(error.localizedDescription)")
            
            // Fallback sur le cache en cas d'erreur
            await fallbackToCache()
        }
    }
    
    /// 📦 Fallback sur le cache en cas d'erreur Firebase
    private func fallbackToCache() async {
        print("📦 Fallback: Utilisation cache Realm suite à erreur Firebase")
        
        guard let coupleId = coupleId else { return }
        
        let cachedQuestions = QuestionCacheManager.shared.getCachedDailyQuestions(for: coupleId, limit: 1)
        
        if let latestQuestion = cachedQuestions.first {
            print("✅ Question de fallback trouvée: \(latestQuestion.questionKey)")
            
            DispatchQueue.main.async {
                self.currentQuestion = latestQuestion
            }
        } else {
            print("❌ Aucune question en cache pour fallback")
        }
    }
    
    // MARK: - 📊 STATS & MONITORING
    
    /// 📊 Stats d'optimisation timezone pour monitoring
    struct TimezoneOptimizationStats {
        let cacheHits: Int
        let firebaseCallAvoided: Bool
        let criticalTime: Bool
        let timezone: String
        let localHour: Int
        let timestamp: Date = Date()
        
        func logSummary() {
            print("📊 OPTIMISATION STATS:")
            print("   💾 Cache hits: \(cacheHits)")
            print("   🚀 Firebase évité: \(firebaseCallAvoided ? "✅" : "❌")")
            print("   🕐 Heure critique: \(criticalTime ? "✅" : "❌")")
            print("   🌍 Timezone: \(timezone)")
            print("   ⏰ Heure locale: \(localHour)h")
        }
    }
    
    /// 🔄 Fonction d'entrée principale optimisée
    func optimizedDailyQuestionCheck() async {
        print("🔄 optimizedDailyQuestionCheck: Démarrage check optimisé")
        
        // Utiliser la nouvelle logique d'optimisation timezone
        await checkForNewQuestionWithTimezoneOptimization()
    }
    
    // MARK: - 🌍 LANGUAGE DETECTION & STORAGE
    
    /// 🌍 Sauvegarde la langue utilisateur dans Firebase pour les notifications localisées
    private func saveUserLanguageToFirebase() {
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ DailyQuestionService: Pas d'utilisateur connecté pour sauvegarder la langue")
            return
        }
        
        // Détecter la langue du système iOS
        let userLanguage = Locale.current.languageCode ?? "fr"
        
        print("🌍 DailyQuestionService: Sauvegarde langue utilisateur: \(userLanguage)")
        
        // Sauvegarder dans les données utilisateur Firebase
        let userRef = Firestore.firestore().collection("users").document(currentUser.uid)
        userRef.updateData([
            "languageCode": userLanguage,
            "languageUpdatedAt": FieldValue.serverTimestamp()
        ]) { error in
            if let error = error {
                print("❌ DailyQuestionService: Erreur sauvegarde langue: \(error)")
            } else {
                print("✅ DailyQuestionService: Langue \(userLanguage) sauvegardée avec succès")
            }
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