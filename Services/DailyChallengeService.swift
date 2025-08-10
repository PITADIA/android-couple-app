import Foundation
import FirebaseFirestore
import FirebaseAuth
import FirebaseFunctions
import Combine
import RealmSwift
import UserNotifications

@MainActor
class DailyChallengeService: ObservableObject {
    static let shared = DailyChallengeService()
    
    @Published var currentChallenge: DailyChallenge?
    @Published var challengeHistory: [DailyChallenge] = []
    @Published var isLoading: Bool = false
    @Published var currentSettings: DailyChallengeSettings?
    
    private var db = Firestore.firestore()
    private var functions = Functions.functions()
    private var challengeListener: ListenerRegistration?
    private var settingsListener: ListenerRegistration?
    
    // CORRECTION: Référence weak à AppState pour éviter les cycles de référence
    private weak var appState: AppState?
    
    // 🚀 OPTIMISATION: Éviter les reconfigurations redondantes
    private var isConfigured: Bool = false
    private var currentCoupleId: String?
    
    private init() {
        // Les listeners seront configurés via configure(with:)
    }
    
    deinit {
        challengeListener?.remove()
        settingsListener?.remove()
    }
    
    // MARK: - Configuration
    
    func configure(with appState: AppState) {
        // 🚀 OPTIMISATION: Éviter les reconfigurations redondantes
        let newCoupleId = generateCoupleId(from: appState)
        
        if isConfigured && currentCoupleId == newCoupleId {
            print("⚡ DailyChallengeService: Déjà configuré pour couple \(newCoupleId ?? "nil") - Pas de reconfiguration")
            return
        }
        
        print("🔄 DailyChallengeService: Configuration pour couple \(newCoupleId ?? "nil")")
        self.appState = appState
        self.currentCoupleId = newCoupleId
        self.isConfigured = true
        
        // 🌍 Sauvegarder la langue utilisateur pour les notifications localisées
        saveUserLanguageToFirebase()
        
        setupListeners()
    }
    
    private func generateCoupleId(from appState: AppState) -> String? {
        guard let firebaseUser = Auth.auth().currentUser,
              let appUser = appState.currentUser,
              let partnerId = appUser.partnerId,
              !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return [firebaseUser.uid, partnerId].sorted().joined(separator: "_")
    }
    
    // MARK: - Setup et Lifecycle
    
    private func setupListeners() {
        guard let firebaseUser = Auth.auth().currentUser,
              let appUser = appState?.currentUser,
              let partnerId = appUser.partnerId,
              !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            print("🔥 DailyChallengeService: Aucun partenaire connecté")
            return
        }
        
        // 🔧 CORRECTION: Utiliser Firebase UID comme DailyQuestionService
        let coupleId = [firebaseUser.uid, partnerId].sorted().joined(separator: "_")
        print("🔥 DailyChallengeService: ✅ CORRECTION - Utilisation Firebase UID")
        print("🔥 DailyChallengeService: Firebase UID: \(firebaseUser.uid)")
        print("🔥 DailyChallengeService: Partner ID: \(partnerId)")
        print("🔥 DailyChallengeService: CoupleId corrigé: \(coupleId)")
        
        // 🚀 OPTIMISATION CACHE: Charger depuis le cache d'abord pour un affichage immédiat
        Task {
            await loadFromCacheFirst(coupleId: coupleId)
            
            // 🎯 CORRECTION: Ne générer que si aucun défi d'aujourd'hui n'est en cache
            await MainActor.run {
                if shouldGenerateToday() {
                    generateTodaysChallenge(coupleId: coupleId)
                } else {
                    print("⚡ DailyChallengeService: Défi d'aujourd'hui déjà disponible - Pas de génération")
                }
            }
        }
        
        setupChallengeListener(coupleId: coupleId)
        setupSettingsListener(coupleId: coupleId)
    }
    
    /// 🚀 Charge les données depuis le cache pour un affichage immédiat
    private func loadFromCacheFirst(coupleId: String) async {
        let cachedChallenges = QuestionCacheManager.shared.getCachedDailyChallenges(for: coupleId, limit: 5)
        
        if !cachedChallenges.isEmpty {
            print("⚡ DailyChallengeService: Chargement immédiat depuis cache - \(cachedChallenges.count) défis")
            
            await MainActor.run {
                if self.currentChallenge == nil {
                    self.challengeHistory = cachedChallenges
                    self.currentChallenge = cachedChallenges.first
                    // 🚀 Stopper l'état de chargement pour éviter le flash d'intro
                    self.isLoading = false
                    print("⚡ DailyChallengeService: Défi affiché depuis cache: \(cachedChallenges.first?.challengeKey ?? "nil")")
                }
            }
        }
    }
    
    private func setupChallengeListener(coupleId: String) {
        print("🔥 === DEBUG SETUP CHALLENGE LISTENER ===")
        print("🎯 DailyChallengeService: setupChallengeListener appelé pour coupleId: \(coupleId)")
        
        challengeListener?.remove()
        print("🎯 DailyChallengeService: Ancien listener supprimé")
        
        print("🎯 DailyChallengeService: Configuration listener avec coupleId: \(coupleId)")
        
        challengeListener = db.collection("dailyChallenges")
            .whereField("coupleId", isEqualTo: coupleId)
            .order(by: "challengeDay", descending: false)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self = self else { return }
                
                if let error = error {
                    print("❌ DailyChallengeService: Erreur listener: \(error)")
                    // Fallback vers le cache Realm en cas d'erreur
                    Task {
                        await self.loadFromRealmCache(coupleId: coupleId)
                    }
                    return
                }
                
                guard let documents = snapshot?.documents else {
                    print("📊 DailyChallengeService: Aucun document trouvé")
                    // Fallback vers le cache Realm si pas de documents
                    Task {
                        await self.loadFromRealmCache(coupleId: coupleId)
                    }
                    return
                }
                
                print("🎯 DailyChallengeService: Listener déclenché")
                print("📊 DailyChallengeService: \(documents.count) document(s) trouvé(s) dans Firestore")
                
                var challenges: [DailyChallenge] = []
                
                for (index, document) in documents.enumerated() {
                    let data = document.data()
                    print("📝 Document \(index + 1): ID=\(document.documentID), challengeKey=\(data["challengeKey"] ?? "N/A"), day=\(data["challengeDay"] ?? "N/A"), date=\(data["scheduledDate"] ?? "N/A")")
                    
                    print("🔄 DailyChallengeService: Décodage document \(index + 1)...")
                    
                    if let challenge = self.parseChallengeDocument(document: document, data: data) {
                        challenges.append(challenge)
                        print("✅ DailyChallengeService: Défi décodé - \(challenge.challengeKey), jour \(challenge.challengeDay)")
                    }
                }
                
                // Cache local des défis
                self.cacheChallengesToRealm(challenges)
                
                print("📚 DailyChallengeService: \(challenges.count) défi(s) décodé(s) avec succès")
                
                if challenges.isEmpty {
                    print("⚠️ DailyChallengeService: Aucun défi décodé - Fallback vers cache Realm")
                    Task {
                        await self.loadFromRealmCache(coupleId: coupleId)
                    }
                } else {
                    self.updateCurrentChallenge(with: challenges)
                }
                
                print("✅ DailyChallengeService: Historique chargé - \(challenges.count) défis")
            }
    }
    
    private func setupSettingsListener(coupleId: String) {
        print("🔥 === DEBUG SETUP SETTINGS LISTENER ===")
        print("🎯 DailyChallengeService: setupSettingsListener appelé pour coupleId: \(coupleId)")
        
        settingsListener?.remove()
        print("🎯 DailyChallengeService: Ancien settings listener supprimé")
        
        print("🎯 DailyChallengeService: Configuration settings listener avec coupleId: \(coupleId)")
        
        settingsListener = db.collection("dailyChallengeSettings")
            .document(coupleId)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self = self else { return }
                
                if let error = error {
                    print("❌ DailyChallengeService: Erreur listener settings: \(error)")
                    return
                }
                
                guard let data = snapshot?.data() else {
                    print("📊 DailyChallengeService: Aucun settings trouvé")
                    return
                }
                
                if let settings = self.parseSettingsDocument(data: data) {
                    self.currentSettings = settings
                    print("✅ DailyChallengeService: Settings chargés pour couple \(coupleId)")
                }
            }
    }
    
    // MARK: - Parsing Documents
    
    private func parseChallengeDocument(document: QueryDocumentSnapshot, data: [String: Any]) -> DailyChallenge? {
        guard let challengeKey = data["challengeKey"] as? String,
              let challengeDay = data["challengeDay"] as? Int,
              let scheduledDate = (data["scheduledDate"] as? Timestamp)?.dateValue(),
              let coupleId = data["coupleId"] as? String else {
            print("❌ DailyChallengeService: Données manquantes dans le document")
            return nil
        }
        
        let isCompleted = data["isCompleted"] as? Bool ?? false
        let completedAt = (data["completedAt"] as? Timestamp)?.dateValue()
        
        return DailyChallenge(
            id: document.documentID,
            challengeKey: challengeKey,
            challengeDay: challengeDay,
            scheduledDate: scheduledDate,
            coupleId: coupleId,
            isCompleted: isCompleted,
            completedAt: completedAt
        )
    }
    
    private func parseSettingsDocument(data: [String: Any]) -> DailyChallengeSettings? {
        guard let coupleId = data["coupleId"] as? String,
              let startDate = (data["startDate"] as? Timestamp)?.dateValue(),
              let timezone = data["timezone"] as? String,
              let currentDay = data["currentDay"] as? Int,
              let createdAt = (data["createdAt"] as? Timestamp)?.dateValue(),
              let lastVisitDate = (data["lastVisitDate"] as? Timestamp)?.dateValue() else {
            print("❌ DailyChallengeService: Données settings manquantes")
            return nil
        }
        
        return DailyChallengeSettings(
            coupleId: coupleId,
            startDate: startDate,
            timezone: timezone,
            currentDay: currentDay,
            createdAt: createdAt,
            lastVisitDate: lastVisitDate
        )
    }
    
    // MARK: - Challenge Management
    
    private func updateCurrentChallenge(with challenges: [DailyChallenge]) {
        print("🔄 DailyChallengeService: Assignation currentChallenge...")
        print("🔄 - challenges.count: \(challenges.count)")
        print("🔄 - challenges.first: \(challenges.first?.challengeKey ?? "nil")")
        
        let previousCurrentChallenge = currentChallenge
        print("🔄 - previousCurrentChallenge: \(previousCurrentChallenge?.challengeKey ?? "nil")")
        
        // Trier par jour pour avoir l'ordre correct
        let sortedChallenges = challenges.sorted { $0.challengeDay < $1.challengeDay }
        challengeHistory = sortedChallenges
        
        // Prendre le défi le plus récent (dernier jour)
        if let latestChallenge = sortedChallenges.last {
            currentChallenge = latestChallenge
            print("🔄 - currentChallenge actuelle: \(currentChallenge?.challengeKey ?? "nil")")
            
            // Optimisation : éviter les mises à jour inutiles
            if previousCurrentChallenge?.id == latestChallenge.id {
                print("🔄 DailyChallengeService: Défi déjà à jour (optimisation immédiate)")
            }
        } else {
            currentChallenge = nil
            print("🔄 - currentChallenge actuelle: nil")
        }
        
        print("🔄 DailyChallengeService: Après assignation:")
        print("🔄 - self.currentChallenge: \(self.currentChallenge?.challengeKey ?? "nil")")
        
        // Log pour debugging
        if let current = currentChallenge {
            print("🎯 DailyChallengeService: Défi actuel défini:")
            print("   - challengeKey: \(current.challengeKey)")
            print("   - challengeDay: \(current.challengeDay)")
            print("   - scheduledDate: \(current.scheduledDate)")
            print("   - id: \(current.id)")
            print("   - 📱 UI VA SE METTRE À JOUR AVEC CE DÉFI")
        }
        
        // Log pour changement de défi
        if let previous = previousCurrentChallenge, let current = currentChallenge {
            print("🔄 DailyChallengeService: Changement de défi:")
            print("   - Ancien: \(previous.challengeKey) (jour \(previous.challengeDay))")
            print("   - Nouveau: \(current.challengeKey) (jour \(current.challengeDay))")
        }
    }
    
    /// Vérifie si on doit générer le défi d'aujourd'hui
    private func shouldGenerateToday() -> Bool {
        guard let currentChallenge = currentChallenge else {
            return true // Pas de défi → générer
        }
        
        // Vérifier si le défi actuel est pour aujourd'hui
        let today = Date()
        let calendar = Calendar.current
        let challengeDate = currentChallenge.scheduledDate
        
        if calendar.isDate(challengeDate, inSameDayAs: today) {
            return false // Défi d'aujourd'hui déjà disponible
        } else {
            return true // Défi d'un autre jour → générer
        }
    }
    
    // MARK: - Challenge Completion
    
    func markChallengeAsCompleted(_ challenge: DailyChallenge) {
        guard let currentUser = appState?.currentUser else { return }
        
        var updatedChallenge = challenge
        updatedChallenge.isCompleted = true
        updatedChallenge.completedAt = Date()
        
        // Mettre à jour localement
        if let index = challengeHistory.firstIndex(where: { $0.id == challenge.id }) {
            challengeHistory[index] = updatedChallenge
        }
        
        if currentChallenge?.id == challenge.id {
            currentChallenge = updatedChallenge
        }
        
        // Mettre à jour le cache Realm
        QuestionCacheManager.shared.updateDailyChallengeCompletion(
            challenge.id, 
            isCompleted: true, 
            completedAt: Date()
        )
        
        // Mettre à jour Firebase
        db.collection("dailyChallenges").document(challenge.id).updateData([
            "isCompleted": true,
            "completedAt": Timestamp(date: Date())
        ]) { error in
            if let error = error {
                print("❌ DailyChallengeService: Erreur mise à jour completion: \(error)")
            } else {
                print("✅ DailyChallengeService: Défi marqué comme complété")
            }
        }
    }
    
    func markChallengeAsNotCompleted(_ challenge: DailyChallenge) {
        guard let currentUser = appState?.currentUser else { return }
        
        var updatedChallenge = challenge
        updatedChallenge.isCompleted = false
        updatedChallenge.completedAt = nil
        
        // Mettre à jour localement
        if let index = challengeHistory.firstIndex(where: { $0.id == challenge.id }) {
            challengeHistory[index] = updatedChallenge
        }
        
        if currentChallenge?.id == challenge.id {
            currentChallenge = updatedChallenge
        }
        
        // Mettre à jour le cache Realm
        QuestionCacheManager.shared.updateDailyChallengeCompletion(
            challenge.id, 
            isCompleted: false, 
            completedAt: nil
        )
        
        // Mettre à jour Firebase
        db.collection("dailyChallenges").document(challenge.id).updateData([
            "isCompleted": false,
            "completedAt": FieldValue.delete()
        ]) { error in
            if let error = error {
                print("❌ DailyChallengeService: Erreur mise à jour incompletion: \(error)")
            } else {
                print("✅ DailyChallengeService: Défi marqué comme non complété")
            }
        }
    }
    
    // MARK: - Firebase Integration
    
    private func saveUserLanguageToFirebase() {
        guard let user = Auth.auth().currentUser else { return }
        
        let languageCode = Locale.current.language.languageCode?.identifier ?? "fr"
        
        print("🌍 DailyChallengeService: Sauvegarde langue utilisateur: \(languageCode)")
        
        db.collection("users").document(user.uid).updateData([
            "languageCode": languageCode
        ]) { error in
            if let error = error {
                print("❌ DailyChallengeService: Erreur sauvegarde langue: \(error)")
            } else {
                print("✅ DailyChallengeService: Langue \(languageCode) sauvegardée avec succès")
            }
        }
    }
    
    // MARK: - Realm Cache
    
    private func cacheChallengesToRealm(_ challenges: [DailyChallenge]) {
        print("📦 DailyChallengeService: Cache de \(challenges.count) défis dans Realm...")
        
        for challenge in challenges {
            QuestionCacheManager.shared.cacheDailyChallenge(challenge)
        }
        
        print("✅ RealmManager: \(challenges.count) défis quotidiens cachés")
    }
    
    // MARK: - Realm Fallback
    
    private func loadFromRealmCache(coupleId: String) async {
        print("🔄 DailyChallengeService: Chargement depuis le cache Realm pour couple: \(coupleId)")
        print("🔄 - RAISON: Fallback car Firestore n'a pas de documents ou erreur")
        
        let cachedChallenges = QuestionCacheManager.shared.getCachedDailyChallenges(for: coupleId, limit: 10)
        
        print("📦 DailyChallengeService: \(cachedChallenges.count) défis trouvés dans le cache Realm")
        
        // Logs détaillés des défis en cache
        for (index, challenge) in cachedChallenges.enumerated() {
            print("📝 Cache \(index + 1): challengeKey=\(challenge.challengeKey), day=\(challenge.challengeDay), date=\(challenge.scheduledDate)")
        }
        
        if !cachedChallenges.isEmpty {
            print("✅ DailyChallengeService: Application des défis du cache...")
            
            await MainActor.run {
                self.challengeHistory = cachedChallenges
                self.currentChallenge = cachedChallenges.first
                
                print("✅ DailyChallengeService: \(cachedChallenges.count) défis chargés depuis le cache")
                print("✅ - currentChallenge assigné: \(self.currentChallenge?.challengeKey ?? "nil")")
                print("✅ - 📱 UI DEVRAIT SE METTRE À JOUR AVEC DÉFI DU CACHE")
                
                if let current = cachedChallenges.first {
                    print("🎯 DailyChallengeService: Défi actuel chargé depuis le cache:")
                    print("   - challengeKey: \(current.challengeKey)")
                    print("   - challengeDay: \(current.challengeDay)")
                    print("   - scheduledDate: \(current.scheduledDate)")
                    print("   - id: \(current.id)")
                }
            }
        } else {
            print("❌ DailyChallengeService: Aucun défi trouvé dans le cache")
            print("❌ - self.currentChallenge reste: \(self.currentChallenge?.challengeKey ?? "nil")")
            print("❌ - 📱 UI VA AFFICHER 'Aucun défi disponible'")
            print("❌ - PROBLÈME: Ni Firestore ni cache Realm n'ont de défis!")
        }
    }
    
    // MARK: - Firebase Functions Integration
    
    private func generateTodaysChallenge(coupleId: String) {
        print("🔥 === DEBUG GENERATE TODAY'S CHALLENGE ===")
        print("🎯 DailyChallengeService: Appel Firebase Function generateDailyChallenge")
        print("🎯 DailyChallengeService: CoupleId: \(coupleId)")
        print("🎯 DailyChallengeService: Timezone: \(TimeZone.current.identifier)")
        
        isLoading = true
        print("🎯 DailyChallengeService: isLoading défini à true")
        
        let data = [
            "coupleId": coupleId,
            "timezone": TimeZone.current.identifier
        ]
        
        print("🎯 DailyChallengeService: Données envoyées à Firebase Function: \(data)")
        
        functions.httpsCallable("generateDailyChallenge").call(data) { [weak self] result, error in
            DispatchQueue.main.async {
                print("🔥 === DEBUG FIREBASE FUNCTION RESPONSE ===")
                self?.isLoading = false
                print("🎯 DailyChallengeService: isLoading défini à false")
                
                if let error = error {
                    print("❌ DailyChallengeService: Erreur Firebase Function: \(error)")
                    print("❌ DailyChallengeService: Error localizedDescription: \(error.localizedDescription)")
                    if let nsError = error as NSError? {
                        print("❌ DailyChallengeService: Error code: \(nsError.code)")
                        print("❌ DailyChallengeService: Error domain: \(nsError.domain)")
                        print("❌ DailyChallengeService: Error userInfo: \(nsError.userInfo)")
                    }
                    return
                }
                
                print("✅ DailyChallengeService: Aucune erreur Firebase Function")
                
                if let resultData = result?.data {
                    print("🎯 DailyChallengeService: Données de réponse: \(resultData)")
                } else {
                    print("❌ DailyChallengeService: result.data est nil")
                }
                
                guard let data = result?.data as? [String: Any] else {
                    print("❌ DailyChallengeService: Impossible de caster result.data en [String: Any]")
                    return
                }
                
                print("✅ DailyChallengeService: Cast en [String: Any] réussi")
                
                guard let success = data["success"] as? Bool else {
                    print("❌ DailyChallengeService: Clé 'success' manquante ou invalide")
                    return
                }
                
                print("✅ DailyChallengeService: success = \(success)")
                
                guard success == true else {
                    print("❌ DailyChallengeService: success = false")
                    if let message = data["message"] as? String {
                        print("❌ DailyChallengeService: Message d'erreur: \(message)")
                    }
                    return
                }
                
                print("✅ DailyChallengeService: Firebase Function réussie")
                
                // Les listeners vont automatiquement récupérer les nouvelles données
                // Pas besoin de traiter la réponse manuellement
                print("🎯 DailyChallengeService: En attente des listeners pour récupérer les données")
            }
        }
    }
    
    // MARK: - Public Methods
    
    func refreshChallenges() {
        print("🚀 === DEBUG DÉFI DU JOUR - REFRESH CHALLENGES ===")
        
        // 🔧 CORRECTION 1: Éviter les appels multiples simultanés
        guard !isLoading else {
            print("⚠️ DailyChallengeService: Génération déjà en cours, arrêt pour éviter double appel")
            return
        }
        
        // 🔧 CORRECTION 2: Si un défi existe déjà, pas besoin de régénérer
        if let current = currentChallenge {
            print("✅ DailyChallengeService: Défi déjà présent (\(current.challengeKey)), pas de régénération")
            return
        }
        
        // 📅 LOGS DATE/HEURE DEMANDÉS - REFRESH CHALLENGES
        let now = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        formatter.timeZone = TimeZone.current
        print("🕐 DailyChallengeService: Date/Heure actuelle: \(formatter.string(from: now))")
        print("🌍 DailyChallengeService: Timezone: \(TimeZone.current.identifier)")
        print("📅 DailyChallengeService: Jour de la semaine: \(Calendar.current.component(.weekday, from: now))")
        print("📊 DailyChallengeService: Jour du mois: \(Calendar.current.component(.day, from: now))")
        print("📈 DailyChallengeService: Mois: \(Calendar.current.component(.month, from: now))")
        print("📉 DailyChallengeService: Année: \(Calendar.current.component(.year, from: now))")
        
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ DailyChallengeService: Firebase user est nil")
            return
        }
        print("✅ DailyChallengeService: Firebase user trouvé - UID: \(firebaseUser.uid)")
        
        guard let appUser = appState?.currentUser else {
            print("❌ DailyChallengeService: AppUser est nil")
            return
        }
        print("✅ DailyChallengeService: AppUser trouvé - ID: \(appUser.id)")
        
        guard let partnerId = appUser.partnerId else {
            print("❌ DailyChallengeService: partnerId est nil")
            return
        }
        print("✅ DailyChallengeService: partnerId trouvé - ID: \(partnerId)")
        
        guard !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            print("❌ DailyChallengeService: partnerId est vide après trim")
            return
        }
        print("✅ DailyChallengeService: partnerId valide après trim")
        
        // 🔧 CORRECTION: Utiliser Firebase UID comme DailyQuestionService
        let coupleId = [firebaseUser.uid, partnerId].sorted().joined(separator: "_")
        print("🎯 DailyChallengeService: ✅ CORRECTION - CoupleId avec Firebase UID: \(coupleId)")
        print("🎯 DailyChallengeService: isLoading AVANT generateTodaysChallenge: \(isLoading)")
        print("🎯 DailyChallengeService: currentChallenge AVANT: \(currentChallenge?.challengeKey ?? "nil")")
        
        // 🔥 Générer le défi du jour via Firebase Function
        generateTodaysChallenge(coupleId: coupleId)
        
        // Puis écouter les changements
        setupChallengeListener(coupleId: coupleId)
        
        print("🎯 DailyChallengeService: refreshChallenges terminé")
    }
    
    func getCurrentChallengeDay() -> Int {
        return currentSettings?.currentDay ?? 1
    }
    
    // MARK: - 🌍 PERFORMANCE OPTIMIZATION WITH REALM CACHE
    
    /// 🚀 Optimisation performance avec cache Realm - réduit drastiquement les coûts Firebase
    func checkForNewChallengeWithOptimization() async {
        print("\n🌍 === CHALLENGE OPTIMIZATION START ===")
        
        guard let firebaseUser = Auth.auth().currentUser,
              let appUser = appState?.currentUser,
              let partnerId = appUser.partnerId,
              !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            print("🔥 DailyChallengeService: Aucun partenaire connecté - Pas d'optimisation")
            return
        }
        
        let coupleId = [firebaseUser.uid, partnerId].sorted().joined(separator: "_")
        let today = Date()
        
        // 📦 1. CHECK CACHE REALM EN PREMIER
        print("\n📦 Phase 1: Vérification cache Realm")
        let cachedChallenge = QuestionCacheManager.shared.getCachedDailyChallenge(for: coupleId, date: today)
        
        if let cachedChallenge = cachedChallenge {
            print("✅ Cache hit! Défi trouvé dans Realm: \(cachedChallenge.challengeKey)")
            print("   - challengeDay: \(cachedChallenge.challengeDay)")
            print("   - scheduledDate: \(cachedChallenge.scheduledDate)")
            
            await MainActor.run {
                self.currentChallenge = cachedChallenge
                self.challengeHistory = [cachedChallenge]
            }
            
            print("⚡ Optimisation Realm: 0 appel Firebase nécessaire")
            print("🌍 === CHALLENGE OPTIMIZATION END (CACHE ONLY) ===\n")
            return
        }
        
        print("❌ Cache miss: Aucun défi en cache pour aujourd'hui")
        print("🔄 Fallback vers Firebase Function...")
        
        // 📱 2. FALLBACK VERS FIREBASE SI NÉCESSAIRE
        await MainActor.run {
            self.isLoading = true
        }
        
        generateTodaysChallenge(coupleId: coupleId)
        
        print("🌍 === CHALLENGE OPTIMIZATION END (FIREBASE CALLED) ===\n")
    }
    
    /// 🎯 Point d'entrée principal pour vérification optimisée des défis
    func optimizedDailyChallengeCheck() async {
        print("🔄 optimizedDailyChallengeCheck: Démarrage check optimisé")
        
        await checkForNewChallengeWithOptimization()
    }
    
    // MARK: - 📊 Cache Statistics
    
    /// Obtient des statistiques sur le cache des défis
    func getCacheStatistics() -> (count: Int, oldestDate: Date?, newestDate: Date?) {
        guard let firebaseUser = Auth.auth().currentUser,
              let appUser = appState?.currentUser,
              let partnerId = appUser.partnerId else {
            return (0, nil, nil)
        }
        
        let coupleId = [firebaseUser.uid, partnerId].sorted().joined(separator: "_")
        return QuestionCacheManager.shared.getDailyChallengesCacheInfo(for: coupleId)
    }
    
    /// Nettoie le cache des défis pour le couple actuel
    func clearCache() {
        guard let firebaseUser = Auth.auth().currentUser,
              let appUser = appState?.currentUser,
              let partnerId = appUser.partnerId else {
            return
        }
        
        let coupleId = [firebaseUser.uid, partnerId].sorted().joined(separator: "_")
        QuestionCacheManager.shared.clearDailyChallengesCache(for: coupleId)
        print("🗑️ Cache des défis nettoyé pour le couple: \(coupleId)")
    }
}