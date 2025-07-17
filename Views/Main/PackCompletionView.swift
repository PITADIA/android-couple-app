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
                    Text("congratulations_pack".localized)
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("pack_completed".localized)
                        .font(.system(size: 28, weight: .medium))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    Text("pack_name".localized)
                        .font(.system(size: 28, weight: .medium))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                }
                .multilineTextAlignment(.center)
                
                // Emoji flamme animé
                Button(action: {
                    onTapForSurprise()
                }) {
                    Text("🔥")
                        .font(.system(size: 120))
                        .scaleEffect(isAnimating ? 1.2 : 1.0)
                        .onAppear {
                            withAnimation(
                                Animation.easeInOut(duration: 1.0)
                                    .repeatForever(autoreverses: true)
                            ) {
                                isAnimating = true
                            }
                        }
                }
                
                // Message d'interaction
                VStack(spacing: 10) {
                    Text("tap_on_me".localized)
                        .font(.system(size: 24, weight: .medium))
                        .foregroundColor(.white)
                    
                    + Text("on_me".localized)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("for_surprise".localized)
                        .font(.system(size: 24, weight: .medium))
                        .foregroundColor(.white)
                    
                    + Text("surprise".localized)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                    
                    + Text("exclamation".localized)
                        .font(.system(size: 24, weight: .medium))
                        .foregroundColor(.white)
                }
                .multilineTextAlignment(.center)
                
                Spacer()
            }
            .padding(.horizontal, 40)
        }
        .onAppear {
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
                
                // Message principal
                VStack(spacing: 20) {
                    Text("new_cards_added".localized)
                        .font(.system(size: 20, weight: .medium))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    Text("\(questionsCount) " + "new_cards".localized)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    Text("enjoy_it".localized)
                        .font(.system(size: 20, weight: .medium))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                }
                
                Spacer()
                
                // Bouton "C'est parti !"
                Button(action: {
                    onContinue()
                }) {
                    Text("lets_go".localized)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(Color(red: 0.6, green: 0.0, blue: 0.2))
                        .frame(maxWidth: .infinity)
                        .frame(height: 60)
                        .background(Color.white)
                        .cornerRadius(30)
                }
                .padding(.horizontal, 40)
                .opacity(showContent ? 1.0 : 0.0)
                .padding(.bottom, 60)
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.8, dampingFraction: 0.6)) {
                showContent = true
            }
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