import Foundation
import Combine

class AppState: ObservableObject {
    @Published var isOnboardingCompleted: Bool = false
    @Published var isAuthenticated: Bool = false
    @Published var currentUser: User?
    @Published var currentOnboardingStep: Int = 0
    @Published var isLoading: Bool = true
    
    // NOUVEAU: Délai minimum pour l'écran de chargement
    private var hasMinimumLoadingTimeElapsed: Bool = false
    private var firebaseDataLoaded: Bool = false
    
    // MARK: - Freemium Manager
    @Published var freemiumManager: FreemiumManager?
    
    // MARK: - Favorites Service
    @Published var favoritesService: FavoritesService?
    
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
        print("🔥 AppState: FavoritesService initialisé")
        
        // NOUVEAU: Délai minimum pour l'écran de chargement (2.5 secondes)
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
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
            .sink { [weak self] user in
                print("AppState: User changé: \(user?.name ?? "nil")")
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
                        
                        // Configurer le FavoritesService avec l'utilisateur
                        if let favoritesService = self?.favoritesService {
                            Task { @MainActor in
                                favoritesService.setCurrentUser(user.id)
                            }
                        }
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
    
    func authenticate(with user: User) {
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
    
    func updateUser(_ user: User) {
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
        currentUser = nil
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
        currentUser = nil
        isLoading = false
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