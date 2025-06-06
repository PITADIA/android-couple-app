import SwiftUI

struct LaunchScreenView: View {
    @State private var isAnimating = false
    @State private var logoScale: CGFloat = 0.8
    @State private var logoOpacity: Double = 0.0
    
    var body: some View {
        ZStack {
            // Fond dégradé de #FD267A à #FF655B
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.99, green: 0.15, blue: 0.48), // #FD267A
                    Color(red: 1.0, green: 0.40, blue: 0.36)   // #FF655B
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 40) {
                Spacer()
                
                // Logo SVG au centre
                Image("Logo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 200, height: 200)
                    .scaleEffect(logoScale)
                    .opacity(logoOpacity)
                    .animation(
                        Animation.easeInOut(duration: 1.5)
                            .repeatForever(autoreverses: true),
                        value: isAnimating
                    )
                
                // Nom de l'application
                Text("Love2Love")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)
                    .opacity(logoOpacity)
                    .animation(.easeInOut(duration: 1.0).delay(0.5), value: logoOpacity)
                
                Spacer()
                
                // Indicateur de chargement
                VStack(spacing: 20) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(1.5)
                        .opacity(logoOpacity)
                        .animation(.easeInOut(duration: 1.0).delay(1.0), value: logoOpacity)
                    
                    Text("Chargement...")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.white.opacity(0.9))
                        .opacity(logoOpacity)
                        .animation(.easeInOut(duration: 1.0).delay(1.2), value: logoOpacity)
                }
                
                Spacer()
            }
        }
        .onAppear {
            // Animation d'apparition
            withAnimation(.easeInOut(duration: 0.8)) {
                logoOpacity = 1.0
                logoScale = 1.0
            }
            
            // Animation de pulsation continue
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                isAnimating = true
                withAnimation(
                    Animation.easeInOut(duration: 2.0)
                        .repeatForever(autoreverses: true)
                ) {
                    logoScale = 1.1
                }
            }
        }
    }
}

#Preview {
    LaunchScreenView()
} 