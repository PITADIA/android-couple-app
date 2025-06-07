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
                    AuthenticationView()
                } else if !appState.isOnboardingCompleted || appState.forceOnboarding {
                    OnboardingView()
                        .onAppear {
                            isOnboardingActive = true
                        }
                        .onDisappear {
                            isOnboardingActive = false
                        }
                } else {
                    MainView()
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
                
                Text("Connexion réussie...")
                    .font(.title2)
                    .fontWeight(.semibold)
                
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
                    .animation(
                        Animation.easeInOut(duration: 1.0)
                            .repeatForever(autoreverses: true),
                        value: isAnimating
                    )
                
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
            isAnimating = true
        }
    }
} 