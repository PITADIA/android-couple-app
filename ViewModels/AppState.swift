import Foundation
import Combine
import FirebaseAuth
import RevenueCat

// MARK: - IntroFlags Structure

/// Flags pour traquer si l'utilisateur a vu les pages d'introduction
struct IntroFlags: Codable {
    var dailyQuestion: Bool = false
    var dailyChallenge: Bool = false
    
    static let `default` = IntroFlags()
}

class AppState: ObservableObject {
    @Published var isOnboardingCompleted: Bool = false
    @Published var isAuthenticated: Bool = false
    @Published var currentUser: AppUser?
    @Published var currentOnboardingStep: Int = 0
    @Published var isLoading: Bool = true
    
    // NOUVEAU: Délai minimum pour l'écran de chargement
    private var hasMinimumLoadingTimeElapsed: Bool = false
    private var firebaseDataLoaded: Bool = false
    
    // MARK: - Freemium Manager
    @Published var freemiumManager: FreemiumManager?
    
    // MARK: - Favorites Service
    @Published var favoritesService: FavoritesService?
    
    // MARK: - Category Progress Service
    @Published var categoryProgressService: CategoryProgressService?
    
    // MARK: - Partner Connection Notification Service
    @Published var partnerConnectionService: PartnerConnectionNotificationService?
    
    // MARK: - Partner Subscription Notification Service
    @Published var partnerSubscriptionService: PartnerSubscriptionNotificationService?
    
    // MARK: - Partner Subscription Sync Service
    @Published var partnerSubscriptionSyncService: PartnerSubscriptionSyncService?
    
    // MARK: - Partner Location Service
    @Published var partnerLocationService: PartnerLocationService?
    
    // NOUVEAU: Journal Service
    @Published var journalService: JournalService?
    
    // NOUVEAU: Widget Service (global pour toute l'app)
    @Published var widgetService: WidgetService?
    
    // NOUVEAU: Location Service (pour sauvegarder automatiquement la localisation)
    @Published var locationService: LocationService?
    
    // NOUVEAU: Review Request Service
    @Published var reviewService: ReviewRequestService?
    
    // NOUVEAU: Daily Challenge Service
    @Published var dailyChallengeService: DailyChallengeService?
    
    // NOUVEAU: Saved Challenges Service
    @Published var savedChallengesService: SavedChallengesService?
    
    // Flag pour savoir si l'utilisateur est en cours d'onboarding
    @Published var isOnboardingInProgress: Bool = false
    
    // NOUVEAU: Flag pour forcer l'onboarding même si l'utilisateur a des données complètes
    @Published var forceOnboarding: Bool = false
    
    // Flag pour savoir si l'utilisateur a volontairement commencé l'onboarding
    @Published var hasUserStartedOnboarding: Bool = false
    
    // NOUVEAU: Flags d'introduction par couple
    @Published var introFlags: IntroFlags = IntroFlags.default
    
    private let firebaseService = FirebaseService.shared
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        print("AppState: Initialisation")
        
        // 🚀 NOUVEAU: Charger l'utilisateur depuis le cache IMMÉDIATEMENT
        if let cachedUser = UserCacheManager.shared.getCachedUser() {
            print("🚀 AppState: Utilisateur trouvé en cache: \(cachedUser.name)")
            self.currentUser = cachedUser
            self.isAuthenticated = true
            self.isOnboardingCompleted = !cachedUser.onboardingInProgress
            
            // 🎯 CORRECTION: Charger les introFlags IMMÉDIATEMENT pour éviter le flash d'intro
            self.loadIntroFlagsSync()
            
            // Délai très court pour affichage fluide (0.3s)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                print("AppState: ✅ Cache utilisateur → Fin chargement immédiate")
                self.hasMinimumLoadingTimeElapsed = true
                self.firebaseDataLoaded = true // Marquer comme chargé depuis le cache
                self.checkIfLoadingComplete()
            }
        }
        
        // Initialiser le FreemiumManager
        self.freemiumManager = FreemiumManager(appState: self)
        
        // Initialiser le FavoritesService
        self.favoritesService = FavoritesService()
        self.favoritesService?.configure(with: self)
        print("🔥 AppState: FavoritesService initialisé et configuré")
        
        // Initialiser le CategoryProgressService
        self.categoryProgressService = CategoryProgressService.shared
        print("🔥 AppState: CategoryProgressService initialisé")
        
        // Initialiser le PartnerConnectionNotificationService
        self.partnerConnectionService = PartnerConnectionNotificationService.shared
        print("🔥 AppState: PartnerConnectionNotificationService initialisé")
        
        // Initialiser le PartnerSubscriptionNotificationService
        self.partnerSubscriptionService = PartnerSubscriptionNotificationService.shared
        print("🔥 AppState: PartnerSubscriptionNotificationService initialisé")
        
        // Initialiser le PartnerSubscriptionSyncService
        self.partnerSubscriptionSyncService = PartnerSubscriptionSyncService.shared
        print("🔥 AppState: PartnerSubscriptionSyncService initialisé")
        
        // Initialiser le PartnerLocationService
        self.partnerLocationService = PartnerLocationService.shared
        print("🔥 AppState: PartnerLocationService initialisé")
        
        // NOUVEAU: Initialiser et configurer le JournalService
        self.journalService = JournalService.shared
        self.journalService?.configure(with: self)
        print("🔥 AppState: JournalService initialisé et configuré")
        
        // NOUVEAU: Initialiser le WidgetService
        self.widgetService = WidgetService()
        print("🔥 AppState: WidgetService initialisé")
        
        // NOUVEAU: Initialiser le LocationService
        self.locationService = LocationService.shared
        print("🔥 AppState: LocationService initialisé")
        
        // NOUVEAU: Initialiser le ReviewRequestService
        self.reviewService = ReviewRequestService.shared
        print("🔥 AppState: ReviewRequestService initialisé")
        
        // NOUVEAU: Initialiser le DailyChallengeService
        self.dailyChallengeService = DailyChallengeService.shared
        print("🔥 AppState: DailyChallengeService initialisé")
        
        // NOUVEAU: Initialiser le SavedChallengesService
        self.savedChallengesService = SavedChallengesService.shared
        print("🔥 AppState: SavedChallengesService initialisé")
        
    // NOUVEAU: Initialiser le FCMService
    _ = FCMService.shared // Pas de demande de permission, juste initialisation
    print("🔥 AppState: FCMService initialisé (sans demande de permission)")
    
    // NOUVEAU: Initialiser et configurer le DailyQuestionService
    Task { @MainActor in
        print("🔥 AppState: Configuration DailyQuestionService...")
        DailyQuestionService.shared.configure(with: self)
        print("🔥 AppState: DailyQuestionService configuré")
    }
        
        // NOUVEAU: Délai minimum pour l'écran de chargement (1.0 seconde)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            print("AppState: Délai minimum écoulé")
            self.hasMinimumLoadingTimeElapsed = true
            self.checkIfLoadingComplete()
        }
        
        // Observer les changements d'authentification Firebase
        firebaseService.$isAuthenticated
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isAuth in
                let timestamp = Date().timeIntervalSince1970
                print("AppState: received isAuthenticated = \(isAuth) [\(timestamp)]")
                
                // 🚀 NOUVEAU: Protéger le cache - Ne pas écraser isAuthenticated=true avec false
                if self?.isAuthenticated == true && isAuth == false && self?.currentUser != nil {
                    print("🛡️ AppState: Cache protégé - isAuthenticated=false ignoré (utilisateur en cache)")
                    return
                }
                
                self?.isAuthenticated = isAuth
                
                // MODIFIÉ: Ne plus arrêter le chargement ici directement
                // Le chargement s'arrêtera via checkIfLoadingComplete()
            }
            .store(in: &cancellables)
        
        firebaseService.$currentUser
            .receive(on: DispatchQueue.main)
            .sink { [weak self] (user: AppUser?) in
                let timestamp = Date().timeIntervalSince1970
                print("AppState: User changé: \(user?.name ?? "nil") [\(timestamp)]")
                
                // 🚀 NOUVEAU: Protéger le cache - Ne pas écraser si on a déjà un utilisateur du cache
                if self?.currentUser != nil && user == nil {
                    print("🛡️ AppState: Cache protégé - Utilisateur Firebase nil ignoré")
                    // Ne pas écraser le cache avec nil, juste marquer Firebase comme chargé
                    print("AppState: currentUser arrived, setting firebaseDataLoaded = true [\(timestamp)]")
                    self?.firebaseDataLoaded = true
                    self?.checkIfLoadingComplete()
                    return
                }
                
                // NOUVEAU: Détecter les changements d'abonnement
                if let oldUser = self?.currentUser, let newUser = user {
                    if oldUser.isSubscribed != newUser.isSubscribed {
                        print("🔒 AppState: Changement d'abonnement détecté: \(oldUser.isSubscribed) -> \(newUser.isSubscribed)")
                        // Mettre à jour les widgets avec le nouveau statut
                        self?.widgetService?.refreshData()
                    }
                }
                
                // 🚀 NOUVELLE APPROCHE: Cache local = source de vérité
                // Plus besoin de détecter des incohérences d'upload
                
                // Protection contre snapshots obsolètes pour l'image de profil
                if let incoming = user, let existing = self?.currentUser,
                   let existingTs = existing.profileImageUpdatedAt, let incomingTs = incoming.profileImageUpdatedAt,
                   incomingTs < existingTs {
                    print("🛡️ AppState: Snapshot obsolète ignoré (profileImageUpdatedAt)")
                    // Ne pas écraser; continuer avec l'utilisateur existant
                } else {
                    // Seulement mettre à jour si on a une vraie donnée Firebase OU pas de cache
                    if user != nil || self?.currentUser == nil {
                        self?.currentUser = user
                    } else {
                        print("🛡️ AppState: Cache preservé - Firebase user=nil ignoré")
                    }
                }
                
                // MODIFIÉ: Marquer que Firebase a terminé, mais ne pas arrêter le chargement directement
                print("AppState: currentUser arrived, setting firebaseDataLoaded = true [\(timestamp)]")
                self?.firebaseDataLoaded = true
                self?.checkIfLoadingComplete()
                
                // MODIFICATION: Vérifier si on force l'onboarding
                if self?.forceOnboarding == true {
                    print("🔥🔥🔥 AppState: ONBOARDING FORCE - Pas de redirection automatique")
                    self?.isOnboardingCompleted = false
                    self?.isOnboardingInProgress = true
                    return
                }
                
                // Marquer l'onboarding comme terminé si l'utilisateur a des données complètes
                if let user = user {
                    let hasCompleteData = !user.name.isEmpty && 
                                        !user.relationshipGoals.isEmpty
                    
                    let isOnboardingComplete = hasCompleteData && !user.onboardingInProgress
                    
                    if isOnboardingComplete {
                        print("AppState: Onboarding terminé")
                        self?.isOnboardingCompleted = true
                        self?.isOnboardingInProgress = false
                        
                        // Configurer le FavoritesService avec l'UID Firebase
                        if let favoritesService = self?.favoritesService,
                           let firebaseUID = Auth.auth().currentUser?.uid {
                            Task { @MainActor in
                                favoritesService.setCurrentUser(firebaseUID, name: user.name)
                            }
                        }
                        
                        // NOUVEAU: Charger les flags d'intro pour le couple actuel
                        self?.loadIntroFlags()
                        
                        // NOUVEAU: Initialiser les flags pour les utilisateurs existants (migration)
                        self?.initializeIntroFlagsForExistingUsers()
                        
                        // Configurer le PartnerLocationService si un partenaire est connecté
                        // NOTE: Ce sera géré par l'observer firebaseService.$currentUser plus bas
                        
                        // NOUVEAU: Forcer le rafraîchissement des images de profil pour les widgets
                        self?.widgetService?.forceRefreshProfileImages()
                        print("🔄 AppState: Rafraîchissement des images de profil pour widgets")
                    } else if user.onboardingInProgress {
                        print("AppState: Onboarding en cours")
                        self?.isOnboardingInProgress = true
                    } else {
                        print("AppState: Données incomplètes")
                        self?.isOnboardingInProgress = false
                    }
                } else {
                    // Si l'utilisateur est authentifié mais sans données complètes
                    if self?.isAuthenticated == true {
                        print("AppState: Continuer onboarding")
                        // Ne pas marquer l'onboarding comme terminé si les données sont incomplètes
                        self?.isOnboardingCompleted = false
                        self?.isOnboardingInProgress = false // user est nil ici, donc pas d'onboarding en cours
                    } else {
                        print("AppState: Onboarding requis")
                        self?.isOnboardingCompleted = false
                        self?.isOnboardingInProgress = false
                    }
                }
                
                // NOUVEAU: Vérifier les messages de connexion en attente
                Task {
                    await PartnerCodeService.shared.checkForPendingConnectionMessage()
                }
            }
            .store(in: &cancellables)
        
        // Observer les changements d'utilisateur pour redémarrer les services partenaires
        firebaseService.$currentUser
            .sink { [weak self] user in
                print("🔄 AppState: Changement utilisateur détecté")
                if let user = user {
                    print("🔄 AppState: - Utilisateur: \(user.name)")
                    // Log sécurisé sans exposer le Partner ID
                    print("🔄 AppState: - Partner ID: '\(user.partnerId != nil && !user.partnerId!.isEmpty ? "[ID_MASQUÉ]" : "nil")'")
                    print("🔄 AppState: - Partner ID isEmpty: \(user.partnerId?.isEmpty ?? true)")
                    
                    if let partnerId = user.partnerId, !partnerId.isEmpty {
                        // Log sécurisé sans exposer le Partner ID
                        print("🔄 AppState: Utilisateur reconnecté - Redémarrage des services partenaires")
                        self?.partnerLocationService?.configureListener(for: partnerId)
                        
                        // NOUVEAU: Reconfigurer le DailyQuestionService avec le partenaire
                        print("🔄 AppState: Reconfiguration DailyQuestionService avec partenaire")
                        Task { @MainActor in
                            DailyQuestionService.shared.configure(with: self!)
                        }
                    } else {
                        print("🔄 AppState: Pas de partenaire connecté - Arrêt des services")
                        // Log sécurisé sans exposer le Partner ID
                        print("🔄 AppState: - Raison: partnerId vide ou nil")
                        self?.partnerLocationService?.configureListener(for: nil)
                    }
                } else {
                    print("🔄 AppState: Utilisateur nil - Arrêt des services")
                    self?.partnerLocationService?.configureListener(for: nil)
                }
            }
            .store(in: &cancellables)
        
        // NOUVEAU: Observer les changements de connexion partenaire pour rafraîchir les données
        NotificationCenter.default.publisher(for: .partnerConnected)
            .sink { [weak self] _ in
                print("📱 AppState: Partenaire connecté - Rechargement données utilisateur")
                
                // Plus besoin de tracker la connexion partenaire pour les reviews
                
                self?.refreshCurrentUserData()
            }
            .store(in: &cancellables)
        
        NotificationCenter.default.publisher(for: .partnerDisconnected)
            .sink { [weak self] _ in
                print("📱 AppState: Partenaire déconnecté - Rechargement données utilisateur")
                self?.refreshCurrentUserData()
            }
            .store(in: &cancellables)
        
        // NOUVEAU: Observer les changements d'abonnement pour mettre à jour les widgets
        NotificationCenter.default.publisher(for: .subscriptionUpdated)
            .sink { [weak self] _ in
                print("🔒 AppState: Abonnement mis à jour - Rafraîchissement des données widget")
                self?.widgetService?.refreshData()
            }
            .store(in: &cancellables)
        
        // NOUVEAU: Listener pour reset des introFlags
        NotificationCenter.default.publisher(for: .introFlagsDidReset)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                print("🔄 AppState: Reset des introFlags détecté - Rechargement")
                self?.loadIntroFlags()
            }
            .store(in: &cancellables)
    }
    
    // NOUVEAU: Vérifier si le chargement peut se terminer
    private func checkIfLoadingComplete() {
        let timestamp = Date().timeIntervalSince1970
        print("AppState: Vérification fin de chargement [\(timestamp)]")
        print("AppState: - Délai minimum écoulé: \(hasMinimumLoadingTimeElapsed)")
        print("AppState: - Données Firebase chargées: \(firebaseDataLoaded)")
        print("AppState: - isAuthenticated: \(isAuthenticated)")
        
        // RETOUR À LA LOGIQUE SIMPLE DE L'ANCIEN CODE:
        // Le chargement se termine quand TOUTES les conditions sont remplies:
        // 1. Le délai minimum s'est écoulé (1s pour voir le LaunchScreen)
        // 2. Firebase a terminé de charger les données (même si currentUser = nil)
        if hasMinimumLoadingTimeElapsed && firebaseDataLoaded {
            print("AppState: ✅ Conditions remplies - Fin du chargement [\(timestamp)]")
            self.isLoading = false
        } else {
            print("AppState: ⏳ Attente des conditions pour finir le chargement [\(timestamp)]")
        }
    }
    
    // NOUVEAU: Méthode pour forcer l'onboarding
    func startOnboardingFlow() {
        print("🔥🔥🔥 AppState: DEMARRAGE FORCE DE L'ONBOARDING")
        forceOnboarding = true
        isOnboardingCompleted = false
        isOnboardingInProgress = true
        currentOnboardingStep = 0
    }
    
    // Méthode pour démarrer l'onboarding manuellement depuis AuthenticationView
    func startUserOnboarding() {
        print("🔥🔥🔥 AppState: UTILISATEUR A DEMARRE L'ONBOARDING MANUELLEMENT")
        hasUserStartedOnboarding = true
        isOnboardingCompleted = false
        isOnboardingInProgress = true
    }
    
    func authenticate(with user: AppUser) {
        print("AppState: Authentification: \(user.name)")
        self.currentUser = user
        self.isAuthenticated = true
        
        // Sauvegarder dans Firebase
        firebaseService.saveUserData(user)
    }
    
    func completeOnboarding() {
        print("AppState: Finalisation onboarding")
        isOnboardingCompleted = true
        isOnboardingInProgress = false
        forceOnboarding = false // NOUVEAU: Réinitialiser le flag
        hasUserStartedOnboarding = false // Réinitialiser le flag de démarrage manuel
        currentOnboardingStep = 0
    }
    
    func updateUser(_ user: AppUser) {
        self.currentUser = user
        
        // Configuration RevenueCat avec l'ID utilisateur Firebase
        if let firebaseUserId = Auth.auth().currentUser?.uid {
            // Log sécurisé sans exposer l'Apple User ID Firebase
            print("💰 AppState: Configuration RevenueCat avec userID utilisateur connecté")
            Purchases.shared.logIn(firebaseUserId) { (customerInfo, created, error) in
                if let error = error {
                    print("❌ AppState: Erreur RevenueCat logIn: \(error)")
                } else {
                    print("✅ AppState: RevenueCat utilisateur configuré - created: \(created)")
                }
            }
        }
        
        // Sauvegarder dans Firebase
        firebaseService.saveUserData(user)
    }
    
    func signOut() {
        // 🗑️ NOUVEAU: Nettoyer le cache utilisateur
        UserCacheManager.shared.clearCache()
        
        // Déconnexion RevenueCat
        print("💰 AppState: Déconnexion RevenueCat")
        Purchases.shared.logOut { (customerInfo, error) in
            if let error = error {
                print("❌ AppState: Erreur RevenueCat logOut: \(error)")
            } else {
                print("✅ AppState: RevenueCat utilisateur déconnecté")
            }
        }
        
        firebaseService.signOut()
        isOnboardingCompleted = false
        isOnboardingInProgress = false
        forceOnboarding = false // NOUVEAU: Réinitialiser le flag
        hasUserStartedOnboarding = false
        currentOnboardingStep = 0
        currentUser = nil as AppUser?
    }
    
    func deleteAccount() {
        print("AppState: Suppression du compte")
        
        // 🗑️ NOUVEAU: Nettoyer le cache utilisateur
        UserCacheManager.shared.clearCache()
        
        // Déconnexion RevenueCat lors de la suppression
        print("💰 AppState: Déconnexion RevenueCat (suppression compte)")
        Purchases.shared.logOut { (customerInfo, error) in
            if let error = error {
                print("❌ AppState: Erreur RevenueCat logOut (suppression): \(error)")
            } else {
                print("✅ AppState: RevenueCat utilisateur déconnecté (suppression)")
            }
        }
        
        firebaseService.signOut()
        isOnboardingCompleted = false
        isAuthenticated = false
        isOnboardingInProgress = false
        forceOnboarding = false // NOUVEAU: Réinitialiser le flag
        hasUserStartedOnboarding = false
        currentOnboardingStep = 0
        currentUser = nil as AppUser?
        isLoading = false
    }
    
    // NOUVEAU: Méthode pour rafraîchir les données utilisateur après changement de connexion partenaire
    private func refreshCurrentUserData() {
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ AppState: Impossible de rafraîchir - pas d'utilisateur Firebase connecté")
            return
        }
        
        print("🔄 AppState: Rafraîchissement des données utilisateur: \(firebaseUser.uid)")
        firebaseService.forceRefreshUserData()
    }
    
    func nextOnboardingStep() {
        currentOnboardingStep += 1
    }
    
    func previousOnboardingStep() {
        if currentOnboardingStep > 0 {
            currentOnboardingStep -= 1
        }
    }
    
    // MARK: - IntroFlags Management
    
    /// Génère l'ID du couple basé sur les UIDs triés
    private func generateCoupleId() -> String? {
        guard let firebaseUser = Auth.auth().currentUser,
              let appUser = currentUser,
              let partnerId = appUser.partnerId,
              !partnerId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        
        return [firebaseUser.uid, partnerId].sorted().joined(separator: "_")
    }
    
    /// Charge les flags d'intro depuis UserDefaults pour le couple actuel
    func loadIntroFlags() {
        guard let coupleId = generateCoupleId() else {
            print("🔍 IntroFlags: Impossible de générer coupleId - pas de partenaire connecté")
            introFlags = IntroFlags.default
            return
        }
        
        let key = ConnectionConfig.introFlagsKey(for: coupleId)
        
        if let data = UserDefaults.standard.data(forKey: key),
           let flags = try? JSONDecoder().decode(IntroFlags.self, from: data) {
            introFlags = flags
            print("✅ IntroFlags: Flags chargés pour couple \(coupleId): \(flags)")
        } else {
            introFlags = IntroFlags.default
            print("🔍 IntroFlags: Aucun flag sauvegardé pour couple \(coupleId) - valeurs par défaut")
        }
    }
    
    /// Version synchrone de loadIntroFlags() pour le chargement initial dans init()
    private func loadIntroFlagsSync() {
        guard let coupleId = generateCoupleId() else {
            print("🔍 IntroFlags (SYNC): Impossible de générer coupleId - pas de partenaire connecté")
            introFlags = IntroFlags.default
            return
        }
        
        let key = ConnectionConfig.introFlagsKey(for: coupleId)
        
        if let data = UserDefaults.standard.data(forKey: key),
           let flags = try? JSONDecoder().decode(IntroFlags.self, from: data) {
            introFlags = flags
            print("✅ IntroFlags (SYNC): Flags chargés immédiatement pour couple \(coupleId): \(flags)")
        } else {
            introFlags = IntroFlags.default
            print("🔍 IntroFlags (SYNC): Aucun flag sauvegardé pour couple \(coupleId) - valeurs par défaut")
        }
    }
    
    /// Sauvegarde les flags d'intro dans UserDefaults pour le couple actuel
    func saveIntroFlags() {
        guard let coupleId = generateCoupleId() else {
            print("❌ IntroFlags: Impossible de sauvegarder - pas de partenaire connecté")
            return
        }
        
        let key = ConnectionConfig.introFlagsKey(for: coupleId)
        
        if let data = try? JSONEncoder().encode(introFlags) {
            UserDefaults.standard.set(data, forKey: key)
            print("✅ IntroFlags: Flags sauvegardés pour couple \(coupleId): \(introFlags)")
        } else {
            print("❌ IntroFlags: Erreur encodage flags pour couple \(coupleId)")
        }
    }
    
    /// Reset les flags lors d'un changement de partenaire
    func resetIntroFlagsOnPartnerChange() {
        introFlags = IntroFlags.default
        
        // Sauvegarder immédiatement pour le nouveau couple (ou absence de couple)
        if let coupleId = generateCoupleId() {
            let key = ConnectionConfig.introFlagsKey(for: coupleId)
            if let data = try? JSONEncoder().encode(introFlags) {
                UserDefaults.standard.set(data, forKey: key)
                print("🔄 IntroFlags: Flags reset et sauvegardés pour nouveau couple \(coupleId)")
            }
        } else {
            print("🔄 IntroFlags: Flags reset - aucun partenaire connecté")
        }
    }
    
    /// Marque l'intro Daily Question comme vue
    func markDailyQuestionIntroAsSeen() {
        introFlags.dailyQuestion = true
        saveIntroFlags()
        AnalyticsService.shared.track(.introContinue(screen: "daily_question"))
    }
    
    /// Marque l'intro Daily Challenge comme vue
    func markDailyChallengeIntroAsSeen() {
        introFlags.dailyChallenge = true
        saveIntroFlags()
        AnalyticsService.shared.track(.introContinue(screen: "daily_challenge"))
    }
    
    /// Initialise les flags d'intro pour les utilisateurs existants (migration)
    func initializeIntroFlagsForExistingUsers() {
        guard let coupleId = generateCoupleId() else { return }
        
        let key = ConnectionConfig.introFlagsKey(for: coupleId)
        
        // Si aucun flag n'existe, vérifier si c'est vraiment un utilisateur existant
        if UserDefaults.standard.data(forKey: key) == nil {
            // 🚨 HEURISTIQUE: Différencier utilisateur existant vs nouvelle connexion
            if isLikelyExistingUser() {
                // Utilisateur existant → marquer comme vu pour éviter l'intro
                introFlags = IntroFlags(dailyQuestion: true, dailyChallenge: true)
                print("🔄 IntroFlags: Migration - utilisateur existant détecté - flags marqués comme vus")
            } else {
                // Nouvelle connexion → garder comme non vu pour afficher l'intro
                introFlags = IntroFlags.default
                print("🔄 IntroFlags: Nouvelle connexion détectée - intro sera affichée")
            }
            saveIntroFlags()
        }
    }
    
    /// Heuristique pour déterminer si c'est un utilisateur existant vs nouvelle connexion
    private func isLikelyExistingUser() -> Bool {
        // Vérifier si l'utilisateur a déjà utilisé les features Daily Question/Challenge
        let hasUsedDailyQuestion = currentUser?.dailyQuestionFirstAccessDate != nil
        let hasUsedDailyChallenge = currentUser?.dailyChallengeFirstAccessDate != nil
        
        // Vérifier si l'utilisateur a un historique d'activité
        let hasQuestionHistory = currentUser?.dailyQuestionMaxDayReached ?? 0 > 1
        let hasChallengeHistory = currentUser?.dailyChallengeMaxDayReached ?? 0 > 1
        
        let isExistingUser = hasUsedDailyQuestion || hasUsedDailyChallenge || hasQuestionHistory || hasChallengeHistory
        
        if isExistingUser {
            print("✅ IntroFlags: Utilisateur existant - Historique trouvé (Q:\(hasUsedDailyQuestion), C:\(hasUsedDailyChallenge), MaxQ:\(currentUser?.dailyQuestionMaxDayReached ?? 0), MaxC:\(currentUser?.dailyChallengeMaxDayReached ?? 0))")
        } else {
            print("🆕 IntroFlags: Nouvel utilisateur - Aucun historique trouvé")
        }
        
        return isExistingUser
    }
} 
