import Foundation
import Combine

class AppState: ObservableObject {
    @Published var isOnboardingCompleted: Bool = false
    @Published var isAuthenticated: Bool = false
    @Published var currentUser: User?
    @Published var currentOnboardingStep: Int = 0
    @Published var isLoading: Bool = true
    
    // MARK: - Freemium Manager
    @Published var freemiumManager: FreemiumManager?
    
    // MARK: - Favorites Service
    @Published var favoritesService: FavoritesService?
    
    // Flag pour savoir si l'utilisateur est en cours d'onboarding
    @Published var isOnboardingInProgress: Bool = false
    
    private let firebaseService = FirebaseService.shared
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        print("AppState: Initialisation")
        
        // Initialiser le FreemiumManager
        self.freemiumManager = FreemiumManager(appState: self)
        
        // Initialiser le FavoritesService
        self.favoritesService = FavoritesService()
        print("🔥 AppState: FavoritesService initialisé")
        
        // Observer les changements d'authentification Firebase
        firebaseService.$isAuthenticated
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isAuth in
                print("AppState: Auth changé: \(isAuth)")
                self?.isAuthenticated = isAuth
                
                // Si Firebase a terminé sa vérification, arrêter le chargement
                if !isAuth && self?.firebaseService.currentUser == nil {
                    self?.isLoading = false
                }
            }
            .store(in: &cancellables)
        
        firebaseService.$currentUser
            .receive(on: DispatchQueue.main)
            .sink { [weak self] user in
                print("AppState: User changé: \(user?.name ?? "nil")")
                self?.currentUser = user
                
                // Firebase a terminé sa vérification
                self?.isLoading = false
                
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
        currentOnboardingStep = 0
        currentUser = nil
    }
    
    func deleteAccount() {
        print("AppState: Suppression du compte")
        firebaseService.signOut()
        isOnboardingCompleted = false
        isAuthenticated = false
        isOnboardingInProgress = false
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