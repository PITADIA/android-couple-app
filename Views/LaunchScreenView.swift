import SwiftUI

struct LaunchScreenView: View {
    var body: some View {
        ZStack {
            // Même fond dégradé que les pages d'onboarding
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(hex: "#FD267A"),
                    Color(hex: "#FF655B")
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            // Logo leetchi centré
            Image("Leetchi")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 200, height: 200)
        }
        .onAppear {
            print("🚀 LaunchScreenView: Écran de chargement affiché")
        }
        .onDisappear {
            print("🚀 LaunchScreenView: Écran de chargement masqué")
        }
    }
}

#Preview {
    LaunchScreenView()
} 