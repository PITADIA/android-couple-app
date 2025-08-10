import SwiftUI

struct DailyChallengeLoadingView: View {
    @State private var isAnimating = false
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à l'app
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()
                
                VStack(spacing: 30) {
                    Spacer()
                    
                    // Animation de chargement
                    ZStack {
                        Circle()
                            .stroke(Color.gray.opacity(0.3), lineWidth: 4)
                            .frame(width: 60, height: 60)
                        
                        Circle()
                            .trim(from: 0, to: 0.75)
                            .stroke(Color(hex: "#FD267A"), lineWidth: 4)
                            .frame(width: 60, height: 60)
                            .rotationEffect(Angle(degrees: isAnimating ? 360 : 0))
                            .animation(
                                Animation.linear(duration: 1.0).repeatForever(autoreverses: false),
                                value: isAnimating
                            )
                    }
                    
                    // Titre
                    Text("Préparation en cours...")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                    
                    // Sous-titre
                    Text("Nous préparons vos défis quotidiens")
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)
                    
                    Spacer()
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            isAnimating = true
        }
    }
}

#Preview {
    DailyChallengeLoadingView()
}