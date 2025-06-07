import SwiftUI

struct PackCompletionView: View {
    let packNumber: Int
    let onTapForSurprise: () -> Void
    @State private var isAnimating = false
    
    var body: some View {
        ZStack {
            // Fond sombre comme dans l'image
            Color.black.opacity(0.9)
                .ignoresSafeArea()
            
            VStack(spacing: 40) {
                Spacer()
                
                // Titre principal
                VStack(spacing: 15) {
                    Text("Bravo !")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("Tu as terminé")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("le pack...")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                }
                .multilineTextAlignment(.center)
                
                // Emoji flamme animé
                Button(action: {
                    onTapForSurprise()
                }) {
                    Text("🔥")
                        .font(.system(size: 120))
                        .scaleEffect(isAnimating ? 1.2 : 1.0)
                        .animation(
                            Animation.easeInOut(duration: 1.0)
                                .repeatForever(autoreverses: true),
                            value: isAnimating
                        )
                }
                
                // Message d'interaction
                VStack(spacing: 10) {
                    Text("Tape")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(Color(red: 1.0, green: 0.4, blue: 0.6))
                    + Text(" sur moi")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.white)
                    
                    Text("pour une ")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.white)
                    + Text("surprise")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(Color(red: 1.0, green: 0.4, blue: 0.6))
                    + Text(" !")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.white)
                }
                .multilineTextAlignment(.center)
                
                Spacer()
            }
            .padding(.horizontal, 40)
        }
        .onAppear {
            isAnimating = true
            print("🔥 PackCompletionView: Pack \(packNumber) terminé !")
        }
    }
}

struct NewPackRevealView: View {
    let packNumber: Int
    let questionsCount: Int = 32
    let onContinue: () -> Void
    @State private var showContent = false
    
    var body: some View {
        ZStack {
            // Fond dégradé rouge/orange
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.99, green: 0.15, blue: 0.47), // #FD267A
                    Color(red: 1.0, green: 0.4, blue: 0.36)    // #FF655B
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 50) {
                Spacer()
                
                // Emoji enveloppe
                Text("💌")
                    .font(.system(size: 80))
                    .scaleEffect(showContent ? 1.0 : 0.5)
                    .animation(.spring(response: 0.8, dampingFraction: 0.6), value: showContent)
                
                // Message principal
                VStack(spacing: 20) {
                    Text("On vient de t'ajouter \(questionsCount) nouvelles cartes")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .opacity(showContent ? 1.0 : 0.0)
                        .animation(.easeInOut(duration: 0.8).delay(0.3), value: showContent)
                    
                    Text("Profites-en")
                        .font(.system(size: 20))
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .opacity(showContent ? 1.0 : 0.0)
                        .animation(.easeInOut(duration: 0.8).delay(0.5), value: showContent)
                }
                
                Spacer()
                
                // Bouton "C'est parti !"
                Button(action: {
                    onContinue()
                }) {
                    Text("C'est parti !")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(Color(red: 0.6, green: 0.0, blue: 0.2))
                        .frame(maxWidth: .infinity)
                        .frame(height: 60)
                        .background(Color.white)
                        .cornerRadius(30)
                }
                .padding(.horizontal, 40)
                .opacity(showContent ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 0.8).delay(0.7), value: showContent)
                .padding(.bottom, 60)
            }
        }
        .onAppear {
            showContent = true
            print("🔥 NewPackRevealView: Nouveau pack \(packNumber) révélé !")
        }
    }
}

#Preview {
    PackCompletionView(packNumber: 1) {
        print("Surprise tapped!")
    }
}

#Preview {
    NewPackRevealView(packNumber: 2) {
        print("C'est parti tapped!")
    }
} 