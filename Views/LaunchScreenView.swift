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
            Image("leetchi2")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 200, height: 200)
        }
        .onAppear {
            let timestamp = Date().timeIntervalSince1970
            print("🚀 LaunchScreenView: Écran de chargement affiché [\(timestamp)]")
        }
        .onDisappear {
            let timestamp = Date().timeIntervalSince1970
            print("🚀 LaunchScreenView: Écran de chargement masqué [\(timestamp)]")
        }
    }
}

#Preview {
    LaunchScreenView()
} 