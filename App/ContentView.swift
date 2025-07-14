import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState
    @State private var isTransitioning = false
    @State private var isOnboardingActive = false
    
    var body: some View {
        ZStack {
            Group {
                if appState.isLoading {
                    LaunchScreenView()
                } else if isTransitioning {
                    LoadingTransitionView()
                } else if !appState.isAuthenticated {
                    // Utilisateur non authentifié -> Page de présentation
                    AuthenticationView()
                } else if appState.isAuthenticated && !appState.hasUserStartedOnboarding && !appState.isOnboardingCompleted {
                    // Utilisateur authentifié automatiquement par Firebase mais qui n'a pas démarré manuellement l'onboarding
                    // -> Montrer la page de présentation
                    AuthenticationView()
                } else if appState.isAuthenticated && (appState.hasUserStartedOnboarding || appState.forceOnboarding) && !appState.isOnboardingCompleted {
                    // Utilisateur authentifié qui a démarré l'onboarding manuellement -> Processus d'onboarding
                    OnboardingView()
                        .onAppear {
                            isOnboardingActive = true
                        }
                        .onDisappear {
                            isOnboardingActive = false
                        }
                } else {
                    // Utilisateur authentifié avec onboarding terminé -> Application principale
                    TabContainerView()
                }
            }
            .preferredColorScheme(.light)
        }
        .onAppear {
            print("ContentView: Vue principale apparue")
            setupObservers()
        }
        .onChange(of: appState.isAuthenticated) { _, isAuth in
            print("ContentView: Changement authentification: \(isAuth)")
        }
        .onChange(of: appState.isOnboardingCompleted) { _, isCompleted in
            print("ContentView: Changement onboarding: \(isCompleted)")
        }
        .onChange(of: appState.currentUser) { _, user in
            print("ContentView: Changement utilisateur: \(user?.name ?? "nil")")
        }
        .onChange(of: appState.isLoading) { _, isLoading in
            print("ContentView: Changement chargement: \(isLoading)")
        }
        .onOpenURL { url in
            print("🔗 ContentView: URL reçue: \(url)")
            handleDeepLink(url)
        }
    }
    
    private func setupObservers() {
        print("ContentView: Configuration des observateurs")
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("AuthenticationStateChanged"),
            object: nil,
            queue: .main
        ) { notification in
            print("ContentView: Notification AuthenticationStateChanged reçue")
            if let isAuthenticated = notification.userInfo?["isAuthenticated"] as? Bool, isAuthenticated {
                print("ContentView: Début de la transition")
                self.isTransitioning = true
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    print("ContentView: Fin de la transition")
                    self.isTransitioning = false
                }
            }
        }
    }
    
    private func handleDeepLink(_ url: URL) {
        print("🔗 ContentView: Traitement deep link: \(url.absoluteString)")
        
        guard url.scheme == "coupleapp" else {
            print("❌ ContentView: Scheme non reconnu: \(url.scheme ?? "nil")")
            return
        }
        
        switch url.host {
        case "subscription":
            print("🔗 ContentView: Redirection vers abonnement depuis widget")
            // Ouvrir la vue d'abonnement
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                self.appState.freemiumManager?.showingSubscription = true
            }
            
        default:
            print("❌ ContentView: Host non reconnu: \(url.host ?? "nil")")
        }
    }
}

struct LoadingTransitionView: View {
    var body: some View {
        ZStack {
            Color.white.edgesIgnoringSafeArea(.all)
            
            VStack {
                Spacer()
                
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 60))
                    .foregroundColor(.green)
                    .padding()
                
                Text(NSLocalizedString("connection_successful_status", comment: "Connection successful status"))
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.black)
                
                Spacer()
            }
        }
        .onAppear {
            print("LoadingTransitionView: Vue de transition apparue")
        }
    }
}

struct LoadingSplashView: View {
    @State private var isAnimating = false
    
    var body: some View {
        ZStack {
            // Fond dégradé personnalisé avec les nouvelles couleurs
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(hex: "#FD267A"),
                    Color(hex: "#FF655B")
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 30) {
                // Logo/Icône de l'app
                Text("🔥")
                    .font(.system(size: 80))
                    .scaleEffect(isAnimating ? 1.2 : 1.0)
                    .onAppear {
                        withAnimation(
                            Animation.easeInOut(duration: 1.0)
                                .repeatForever(autoreverses: true)
                        ) {
                            isAnimating = true
                        }
                    }
                
                // Nom de l'app
                Text("Love2Love")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)
                
                // Indicateur de chargement
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(1.2)
                
                Text("Chargement...")
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.8))
            }
        }
        .onAppear {
            print("LoadingSplashView: Écran de chargement affiché")
        }
    }
} 