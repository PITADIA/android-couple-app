import Foundation
import FirebaseFirestore
import FirebaseAuth
import RealmSwift

// MARK: - Daily Question Settings (Nouvelle logique)

/// Paramètres de progression pour un couple
struct DailyQuestionSettings: Codable, Identifiable {
    var id: String { coupleId }
    let coupleId: String
    let startDate: Date // Date de première visite (21h du jour)
    let timezone: String // Fuseau horaire du couple
    var currentDay: Int // Jour actuel du cycle (1-20)
    let createdAt: Date
    var lastVisitDate: Date?
    
    init(coupleId: String, startDate: Date, timezone: String) {
        self.coupleId = coupleId
        self.startDate = startDate
        self.timezone = timezone
        self.currentDay = 1
        self.createdAt = Date()
        self.lastVisitDate = nil
    }
}

// MARK: - Question Response Model (pour sous-collections)
struct QuestionResponse: Codable, Equatable, Identifiable {
    let id: String
    let userId: String
    let userName: String
    let text: String
    let respondedAt: Date
    var status: ResponseStatus
    var isReadByPartner: Bool
    
    init(userId: String, userName: String, text: String, status: ResponseStatus = .answered) {
        self.id = UUID().uuidString
        self.userId = userId
        self.userName = userName
        self.text = text
        self.respondedAt = Date()
        self.status = status
        self.isReadByPartner = false
    }
    
    // MARK: - Codable avec migration automatique pour Firestore
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        // ID : nouveau champ obligatoire
        if let id = try? container.decode(String.self, forKey: .id) {
            self.id = id
        } else {
            // Fallback : générer un ID pour les anciennes données
            self.id = UUID().uuidString
        }
        
        self.userId = try container.decode(String.self, forKey: .userId)
        self.userName = try container.decode(String.self, forKey: .userName)
        self.text = try container.decode(String.self, forKey: .text)
        
        // Date avec fallback
        if let timestamp = try? container.decode(Timestamp.self, forKey: .respondedAt) {
            self.respondedAt = timestamp.dateValue()
        } else if let date = try? container.decode(Date.self, forKey: .respondedAt) {
            self.respondedAt = date
        } else {
            self.respondedAt = Date()
        }
        
        self.status = try container.decodeIfPresent(ResponseStatus.self, forKey: .status) ?? .answered
        self.isReadByPartner = try container.decodeIfPresent(Bool.self, forKey: .isReadByPartner) ?? false
    }
    
    private enum CodingKeys: String, CodingKey {
        case id, userId, userName, text, respondedAt, status, isReadByPartner
    }
}

// MARK: - Daily Question Model (compatible sous-collections)
struct DailyQuestion: Identifiable, Codable {
    let id: String
    let coupleId: String
    let questionKey: String
    let questionDay: Int
    let scheduledDate: String
    let scheduledDateTime: Date
    var status: QuestionStatus
    let createdAt: Date
    var updatedAt: Date
    let timezone: String
    
    // NOUVEAU: Support des sous-collections
    var responsesFromSubcollection: [QuestionResponse] = [] // Chargées depuis la sous-collection
    
    // LEGACY: Ancien système (pour compatibilité)
    var legacyResponses: [String: QuestionResponse] = [:] // Ancien dictionnaire
    
    // MARK: - Computed Property unifié
    var responses: [String: QuestionResponse] {
        // Priorité aux sous-collections, fallback sur legacy
        if !responsesFromSubcollection.isEmpty {
            var dict: [String: QuestionResponse] = [:]
            for response in responsesFromSubcollection {
                dict[response.userId] = response
            }
            return dict
        }
        return legacyResponses
    }
    
    var responsesArray: [QuestionResponse] {
        if !responsesFromSubcollection.isEmpty {
            return responsesFromSubcollection.sorted { $0.respondedAt < $1.respondedAt }
        }
        return Array(legacyResponses.values).sorted { $0.respondedAt < $1.respondedAt }
    }
    
    // MARK: - Custom Initializer pour Realm migration
    init(id: String, coupleId: String, questionKey: String, questionDay: Int, 
         scheduledDate: String, scheduledDateTime: Date, status: QuestionStatus,
         createdAt: Date, updatedAt: Date, timezone: String,
         responsesFromSubcollection: [QuestionResponse] = [],
         legacyResponses: [String: QuestionResponse] = [:]) {
        self.id = id
        self.coupleId = coupleId
        self.questionKey = questionKey
        self.questionDay = questionDay
        self.scheduledDate = scheduledDate
        self.scheduledDateTime = scheduledDateTime
        self.status = status
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.timezone = timezone
        self.responsesFromSubcollection = responsesFromSubcollection
        self.legacyResponses = legacyResponses
    }
    
    // MARK: - Computed Properties pour compatibilité
    var localizedText: String {
        return NSLocalizedString(questionKey, tableName: "DailyQuestions", comment: "")
    }
    
    var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        
        // Convertir le scheduledDate string en Date
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        
        if let date = dateFormatter.date(from: scheduledDate) {
            return formatter.string(from: date)
        } else {
            return scheduledDate // Fallback vers le string original
        }
    }
    
    var bothResponded: Bool {
        return responses.values.filter { $0.status == .answered }.count >= 2
    }
    
    var hasAnyResponse: Bool {
        return responses.values.contains { $0.status == .answered }
    }
    
    var isExpired: Bool {
        let calendar = Calendar.current
        let scheduledDateTime = calendar.date(
            bySettingHour: 21,
            minute: 0,
            second: 0,
            of: calendar.dateFromString(scheduledDate) ?? Date()
        ) ?? Date()
        
        return Date().timeIntervalSince(scheduledDateTime) > 24 * 60 * 60 // 24h
    }
    
    var currentUserResponse: QuestionResponse? {
        guard let currentUserId = Auth.auth().currentUser?.uid else { return nil }
        return responses[currentUserId]
    }
    
    var partnerResponse: QuestionResponse? {
        guard let currentUserId = Auth.auth().currentUser?.uid else { return nil }
        return responses.values.first { $0.userId != currentUserId }
    }
    
    var canCurrentUserRespond: Bool {
        guard let currentUserId = Auth.auth().currentUser?.uid else { return false }
        let currentUserResponse = responses[currentUserId]
        return currentUserResponse?.status != .answered && !isExpired
    }
    
    var shouldShowWaitingMessage: Bool {
        guard let currentUserId = Auth.auth().currentUser?.uid else { return false }
        let currentUserResponse = responses[currentUserId]
        let hasAnswered = currentUserResponse?.status == .answered
        let partnerHasAnswered = partnerResponse?.status == .answered
        
        // NOUVELLE LOGIQUE: Si j'ai répondu mais pas mon partenaire
        let waitingForPartner = hasAnswered && !partnerHasAnswered
        
        return waitingForPartner && !isExpired
    }
    
    /// Vérifie si le chat doit être débloqué car une nouvelle question est disponible
    func shouldUnlockChat(withSettings settings: DailyQuestionSettings?) -> Bool {
        guard let settings = settings else { return false }
        
        // Si une nouvelle question devrait être disponible → débloquer le chat
        return DailyQuestionGenerator.shouldShowNewQuestion(settings: settings)
    }
    
    /// Vérifie si le message d'attente doit être affiché pour un utilisateur donné
    func shouldShowWaitingMessage(for userId: String, withSettings settings: DailyQuestionSettings? = nil) -> Bool {
        let currentUserResponse = responses[userId]
        let partnerResponse = responses.values.first { $0.userId != userId }
        
        let hasAnswered = currentUserResponse?.status == .answered
        let partnerHasAnswered = partnerResponse?.status == .answered
        
        // ✅ LOGIQUE CORRIGÉE : Si le partenaire a répondu → toujours débloquer
        if partnerHasAnswered {
            return false // Chat débloqué immédiatement
        }
        
        // Si j'ai répondu mais pas mon partenaire
        let waitingForPartner = hasAnswered && !partnerHasAnswered
        
        // NOUVELLE LOGIQUE: Si une nouvelle question est disponible → débloquer le chat
        let shouldUnlock = shouldUnlockChat(withSettings: settings)
        
        return waitingForPartner && !isExpired && !shouldUnlock
    }
    
    // MARK: - Helper methods
    func getUserResponse(for userId: String) -> QuestionResponse? {
        return responses[userId]
    }
    
    func hasUserResponded(_ userId: String) -> Bool {
        return responses[userId] != nil
    }
    
    func getResponsesArray() -> [QuestionResponse] {
        if !responsesFromSubcollection.isEmpty {
            return responsesFromSubcollection.sorted { $0.respondedAt < $1.respondedAt }
        }
        return Array(legacyResponses.values).sorted { $0.respondedAt < $1.respondedAt }
    }
    
    // MARK: - Migration Support
    var shouldUseLegacyMode: Bool {
        // Utiliser le mode legacy si pas de migration détectée
        return !legacyResponses.isEmpty && responsesFromSubcollection.isEmpty
    }
    
    var migrationVersion: String? {
        // Sera défini par les Cloud Functions lors de la migration
        return nil
    }
    
    // MARK: - Codable Implementation
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        self.id = try container.decode(String.self, forKey: .id)
        self.coupleId = try container.decode(String.self, forKey: .coupleId)
        self.questionKey = try container.decode(String.self, forKey: .questionKey)
        self.questionDay = try container.decode(Int.self, forKey: .questionDay)
        self.scheduledDate = try container.decode(String.self, forKey: .scheduledDate)
        
        // Date avec support Timestamp et Date
        if let timestamp = try? container.decode(Timestamp.self, forKey: .scheduledDateTime) {
            self.scheduledDateTime = timestamp.dateValue()
        } else {
            self.scheduledDateTime = try container.decode(Date.self, forKey: .scheduledDateTime)
        }
        
        if let timestamp = try? container.decode(Timestamp.self, forKey: .createdAt) {
            self.createdAt = timestamp.dateValue()
        } else {
            self.createdAt = try container.decode(Date.self, forKey: .createdAt)
        }
        
        if let timestamp = try? container.decode(Timestamp.self, forKey: .updatedAt) {
            self.updatedAt = timestamp.dateValue()
        } else {
            self.updatedAt = try container.decode(Date.self, forKey: .updatedAt)
        }
        
        self.status = try container.decodeIfPresent(QuestionStatus.self, forKey: .status) ?? .pending
        self.timezone = try container.decodeIfPresent(String.self, forKey: .timezone) ?? "Europe/Paris"
        
        // LEGACY: Charger l'ancien format si présent
        self.legacyResponses = try container.decodeIfPresent([String: QuestionResponse].self, forKey: .legacyResponses) ?? [:]
        
        // NOUVEAU: Les réponses de sous-collection seront chargées séparément
        self.responsesFromSubcollection = []
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        
        try container.encode(id, forKey: .id)
        try container.encode(coupleId, forKey: .coupleId)
        try container.encode(questionKey, forKey: .questionKey)
        try container.encode(questionDay, forKey: .questionDay)
        try container.encode(scheduledDate, forKey: .scheduledDate)
        try container.encode(Timestamp(date: scheduledDateTime), forKey: .scheduledDateTime)
        try container.encode(status, forKey: .status)
        try container.encode(Timestamp(date: createdAt), forKey: .createdAt)
        try container.encode(Timestamp(date: updatedAt), forKey: .updatedAt)
        try container.encode(timezone, forKey: .timezone)
        
        // Ne pas encoder les réponses ici - elles sont dans une sous-collection
        // Garder legacy seulement pour migration
        if !legacyResponses.isEmpty && responsesFromSubcollection.isEmpty {
            try container.encode(legacyResponses, forKey: .legacyResponses)
        }
    }
    
    private enum CodingKeys: String, CodingKey {
        case id, coupleId, questionKey, questionDay, scheduledDate, scheduledDateTime
        case status, createdAt, updatedAt, timezone
        case legacyResponses = "responses" // Mappage pour compatibilité
    }
}

enum QuestionStatus: String, Codable, CaseIterable {
    case pending = "pending"           // Question générée, pas encore de réponse
    case active = "active"             // Question avec au moins une réponse
    case oneAnswered = "one_answered"  // Une personne a répondu
    case bothAnswered = "both_answered" // Les deux ont répondu
    case expired = "expired"           // 24h écoulées sans réponse complète
    case skipped = "skipped"           // Question ignorée
    
    var displayName: String {
        switch self {
        case .pending:
            return NSLocalizedString("status_pending", tableName: "DailyQuestions", comment: "")
        case .active:
            return NSLocalizedString("status_active", tableName: "DailyQuestions", comment: "")
        case .oneAnswered:
            return NSLocalizedString("status_one_answered", tableName: "DailyQuestions", comment: "")
        case .bothAnswered:
            return NSLocalizedString("status_both_answered", tableName: "DailyQuestions", comment: "")
        case .expired:
            return NSLocalizedString("status_expired", tableName: "DailyQuestions", comment: "")
        case .skipped:
            return NSLocalizedString("status_skipped", tableName: "DailyQuestions", comment: "")
        }
    }
}

enum ResponseStatus: String, Codable, CaseIterable {
    case waiting = "waiting"   // En attente de réponse
    case answered = "answered" // A répondu
    case skipped = "skipped"   // A passé cette question
    
    var displayName: String {
        switch self {
        case .waiting:
            return NSLocalizedString("response_waiting", tableName: "DailyQuestions", comment: "")
        case .answered:
            return NSLocalizedString("response_answered", tableName: "DailyQuestions", comment: "")
        case .skipped:
            return NSLocalizedString("response_skipped", tableName: "DailyQuestions", comment: "")
        }
    }
}

// MARK: - Extensions

extension Calendar {
    func dateFromString(_ dateString: String) -> Date? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: dateString)
    }
    
    func stringFromDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
}

extension Date {
    var dailyQuestionDateString: String {
        return Calendar.current.stringFromDate(self)
    }
    
    func scheduledQuestionTime() -> Date {
        let calendar = Calendar.current
        return calendar.date(bySettingHour: 21, minute: 0, second: 0, of: self) ?? self
    }
}

// MARK: - Question Generation Helper

struct DailyQuestionGenerator {
    
    // NOUVEAU: Récupérer dynamiquement le nombre de questions disponibles
    static func getAvailableQuestionsCount() -> Int {
        // Parcourir les clés de localisation pour compter les questions disponibles
        var count = 0
        let bundle = Bundle.main
        
        // Chercher toutes les clés daily_question_X dans DailyQuestions.xcstrings
        for i in 1...1000 { // Limite raisonnable pour éviter les boucles infinies
            let key = "daily_question_\(i)"
            let localized = NSLocalizedString(key, tableName: "DailyQuestions", comment: "")
            
            // Si la localisation renvoie la clé elle-même, elle n'existe pas
            if localized != key {
                count = i
            } else {
                break // Plus de questions trouvées
            }
        }
        
        print("📝 DailyQuestionGenerator: \(count) questions disponibles trouvées")
        return max(count, 20) // Minimum de 20 pour compatibilité
    }
    
    static func calculateCurrentQuestionDay(for coupleId: String, settings: DailyQuestionSettings) -> Int? {
        // CORRECTION TIMEZONE: Utiliser UTC pour éviter les problèmes startOfDay
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        
        // Normaliser les dates à minuit UTC pour un calcul correct
        let startOfDay = calendar.startOfDay(for: settings.startDate)
        let startOfToday = calendar.startOfDay(for: Date())
        
        let daysSinceStart = calendar.dateComponents([.day], from: startOfDay, to: startOfToday).day ?? 0
        
        // NOUVEAU: Incrémenter basé sur le currentDay existant plutôt que recalculer depuis le début
        let shouldIncrement = daysSinceStart >= settings.currentDay
        let nextDay = shouldIncrement ? settings.currentDay + 1 : settings.currentDay
        
        let availableQuestions = getAvailableQuestionsCount()
        
        // Cycle infini à travers les questions disponibles
        let questionDay = ((nextDay - 1) % availableQuestions) + 1
        
        print("📝 DailyQuestionGenerator (UTC FIXED): Question \(questionDay)/\(availableQuestions) pour couple \(coupleId)")
        print("📝 DailyQuestionGenerator: Jours écoulés: \(daysSinceStart), CurrentDay: \(settings.currentDay), NextDay: \(nextDay)")
        
        return questionDay
    }
    
    /// Génère la clé de question pour un jour donné
    static func generateQuestionKey(for day: Int) -> String {
        return "daily_question_\(day)"
    }
    
    /// Crée les settings de première visite pour un couple
    static func createInitialSettings(for coupleId: String, currentTime: Date = Date()) -> DailyQuestionSettings {
        // Détecter le fuseau horaire automatiquement
        let timezone = TimeZone.current.identifier
        
        // Calculer la date de début : minuit (00h) du jour actuel
        let calendar = Calendar.current
        let startDate = calendar.startOfDay(for: currentTime)
        
        print("📅 DailyQuestionGenerator: Création settings pour couple \(coupleId)")
        print("📅 DailyQuestionGenerator: Date de début: \(startDate)")
        print("📅 DailyQuestionGenerator: Fuseau horaire: \(timezone)")
        
        return DailyQuestionSettings(coupleId: coupleId, startDate: startDate, timezone: timezone)
    }
    
    /// Vérifie si toutes les questions ont été épuisées pour un couple donné
    static func areAllQuestionsExhausted(for coupleId: String, settings: DailyQuestionSettings? = nil, at date: Date = Date()) -> Bool {
        guard let settings = settings else { return false }
        return calculateCurrentQuestionDay(for: coupleId, settings: settings) == nil
    }
    
    /// Obtient des statistiques sur les questions pour un couple
    static func getQuestionStats(for coupleId: String, settings: DailyQuestionSettings? = nil, at date: Date = Date()) -> (used: Int, total: Int, remaining: Int) {
        let totalQuestions = getAvailableQuestionsCount()
        
        guard let settings = settings else {
            // Première visite → 0 questions utilisées
            return (used: 0, total: totalQuestions, remaining: totalQuestions)
        }
        
        let calendar = Calendar.current
        let daysSinceStart = calendar.dateComponents([.day], from: settings.startDate, to: date).day ?? 0
        
        let usedQuestions = max(0, min(daysSinceStart + 1, totalQuestions))
        let remainingQuestions = max(0, totalQuestions - usedQuestions)
        
        return (used: usedQuestions, total: totalQuestions, remaining: remainingQuestions)
    }
    
    /// Vérifie si une nouvelle question devrait être disponible
    static func shouldShowNewQuestion(settings: DailyQuestionSettings) -> Bool {
        
        // 🔧 LOGS TIMEZONE DÉTAILLÉS AVANT CALCUL
        print("🕐 shouldShowNewQuestion: TIMEZONE DEBUG DÉTAILLÉ:")
        print("🕐 - Date() maintenant: \(Date())")
        print("🕐 - Date() ISO: \(ISO8601DateFormatter().string(from: Date()))")
        print("🕐 - TimeZone.current: \(TimeZone.current.identifier)")
        print("🕐 - Settings.startDate: \(settings.startDate)")
        print("🕐 - Settings.startDate ISO: \(ISO8601DateFormatter().string(from: settings.startDate))")
        print("🕐 - Settings timezone: \(settings.timezone)")
        
        // CORRECTION TIMEZONE: Utiliser UTC pour éviter les problèmes startOfDay
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        
        // 🔧 COMPARAISON LOCAL vs UTC
        let localCalendar = Calendar.current
        let localStartOfToday = localCalendar.startOfDay(for: Date())
        let localStartOfSettings = localCalendar.startOfDay(for: settings.startDate)
        
        // Normaliser les dates à minuit UTC pour un calcul correct
        let startOfDay = calendar.startOfDay(for: settings.startDate)
        let startOfToday = calendar.startOfDay(for: Date())
        
        let daysSinceStart = calendar.dateComponents([.day], from: startOfDay, to: startOfToday).day ?? 0
        let expectedDay = daysSinceStart + 1
        
        // 🔧 CORRECTION LOGIQUE PREMIER JOUR
        // Pour un nouveau couple (premier jour), autoriser immédiatement la question
        let isFirstDay = settings.currentDay == 1 && daysSinceStart == 0
        let shouldShow = expectedDay > settings.currentDay || isFirstDay
        
        // LOGS SYSTÉMATIQUES pour diagnostic
        print("⚙️ SHOULDSHOWNEWQUESTION (UTC FIXED):")
        print("⚙️ LOCAL CALENDAR:")
        print("⚙️ - localStartOfToday: \(localStartOfToday)")
        print("⚙️ - localStartOfSettings: \(localStartOfSettings)")
        print("⚙️ UTC CALENDAR:")
        print("⚙️ - startOfDay (UTC): \(startOfDay)")
        print("⚙️ - startOfToday (UTC): \(startOfToday)")
        print("⚙️ CALCULS:")
        print("⚙️ - daysSinceStart: \(daysSinceStart)")
        print("⚙️ - expectedDay: \(expectedDay)")
        print("⚙️ - settings.currentDay: \(settings.currentDay)")
        print("⚙️ - isFirstDay: \(isFirstDay) (currentDay=1 && daysSinceStart=0)")
        print("⚙️ - expectedDay > currentDay? \(expectedDay) > \(settings.currentDay) = \(expectedDay > settings.currentDay)")
        print("⚙️ - LOGIQUE COMPLÈTE: (\(expectedDay) > \(settings.currentDay)) || \(isFirstDay) = \(shouldShow)")
        print("⚙️ - RÉSULTAT shouldShow: \(shouldShow)")
        
        // 🔧 LOG SUPPLÉMENTAIRE : Heure exacte du changement
        if shouldShow {
            let timeFormatter = DateFormatter()
            timeFormatter.timeStyle = .long
            timeFormatter.dateStyle = .none
            print("🚨 shouldShowNewQuestion: NOUVELLE QUESTION AUTORISÉE À: \(Date())")
            print("🚨 - Heure système: \(timeFormatter.string(from: Date()))")
            if isFirstDay {
                print("🎉 - RAISON: Premier jour d'un nouveau couple!")
            } else {
                print("🗓️ - RAISON: Nouveau jour détecté (expectedDay > currentDay)")
            }
        } else {
            print("⏰ shouldShowNewQuestion: Nouvelle question PAS ENCORE disponible")
            print("⏰ - Il faut attendre expectedDay > currentDay: \(expectedDay) > \(settings.currentDay)")
            print("⏰ - OU que ce soit le premier jour (currentDay=1 && daysSinceStart=0)")
        }
        
        return shouldShow
    }
} 