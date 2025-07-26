import Foundation
import Combine
import FirebaseAuth

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
    
    // Flag pour savoir si l'utilisateur est en cours d'onboarding
    @Published var isOnboardingInProgress: Bool = false
    
    // NOUVEAU: Flag pour forcer l'onboarding même si l'utilisateur a des données complètes
    @Published var forceOnboarding: Bool = false
    
    // Flag pour savoir si l'utilisateur a volontairement commencé l'onboarding
    @Published var hasUserStartedOnboarding: Bool = false
    
    private let firebaseService = FirebaseService.shared
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        print("AppState: Initialisation")
        
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
                print("AppState: Auth changé: \(isAuth)")
                self?.isAuthenticated = isAuth
                
                // MODIFIÉ: Ne plus arrêter le chargement ici directement
                // Le chargement s'arrêtera via checkIfLoadingComplete()
            }
            .store(in: &cancellables)
        
        firebaseService.$currentUser
            .receive(on: DispatchQueue.main)
            .sink { [weak self] (user: AppUser?) in
                print("AppState: User changé: \(user?.name ?? "nil")")
                
                // NOUVEAU: Détecter les changements d'abonnement
                if let oldUser = self?.currentUser, let newUser = user {
                    if oldUser.isSubscribed != newUser.isSubscribed {
                        print("🔒 AppState: Changement d'abonnement détecté: \(oldUser.isSubscribed) -> \(newUser.isSubscribed)")
                        // Mettre à jour les widgets avec le nouveau statut
                        self?.widgetService?.refreshData()
                    }
                }
                
                self?.currentUser = user
                
                // MODIFIÉ: Marquer que Firebase a terminé, mais ne pas arrêter le chargement directement
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
                    print("🔄 AppState: - Partner ID: '\(user.partnerId ?? "nil")'")
                    print("🔄 AppState: - Partner ID isEmpty: \(user.partnerId?.isEmpty ?? true)")
                    
                    if let partnerId = user.partnerId, !partnerId.isEmpty {
                        print("🔄 AppState: Utilisateur reconnecté - Redémarrage des services partenaires pour: \(partnerId)")
                        self?.partnerLocationService?.configureListener(for: partnerId)
                        
                        // NOUVEAU: Reconfigurer le DailyQuestionService avec le partenaire
                        print("🔄 AppState: Reconfiguration DailyQuestionService avec partenaire")
                        Task { @MainActor in
                            DailyQuestionService.shared.configure(with: self!)
                        }
                    } else {
                        print("🔄 AppState: Pas de partenaire connecté - Arrêt des services")
                        print("🔄 AppState: - Raison: partnerId = '\(user.partnerId ?? "nil")'")
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
    }
    
    // NOUVEAU: Vérifier si le chargement peut se terminer
    private func checkIfLoadingComplete() {
        print("AppState: Vérification fin de chargement")
        print("AppState: - Délai minimum écoulé: \(hasMinimumLoadingTimeElapsed)")
        print("AppState: - Données Firebase chargées: \(firebaseDataLoaded)")
        
        // Le chargement se termine seulement quand TOUTES les conditions sont remplies:
        // 1. Le délai minimum s'est écoulé (2.5s pour voir le LaunchScreen)
        // 2. Firebase a terminé de charger les données
        if hasMinimumLoadingTimeElapsed && firebaseDataLoaded {
            print("AppState: ✅ Conditions remplies - Fin du chargement")
            self.isLoading = false
        } else {
            print("AppState: ⏳ Attente des conditions pour finir le chargement")
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
        
        // Sauvegarder dans Firebase
        firebaseService.saveUserData(user)
    }
    
    func signOut() {
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
} 