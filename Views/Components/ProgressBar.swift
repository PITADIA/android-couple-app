import SwiftUI

struct ProgressBar: View {
    let progress: Double
    let onBackPressed: (() -> Void)?
    
    init(progress: Double, onBackPressed: (() -> Void)? = nil) {
        self.progress = progress
        self.onBackPressed = onBackPressed
    }
    
    var body: some View {
        HStack {
            // Bouton retour (si pas à la première étape et avant la page de chargement)
            if progress > 0.10 && progress < 0.80 { // Pas à la première étape et avant loading/auth/subscription
                Button(action: {
                    print("🔥 ProgressBar: Bouton retour pressé")
                    onBackPressed?()
                }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 20))
                        .foregroundColor(.white)
                }
            } else {
                Spacer()
                    .frame(width: 30)
            }
            
            Spacer()
            
            // Barre de progression en blanc
            ProgressView(value: progress)
                .progressViewStyle(LinearProgressViewStyle(tint: .white))
                .frame(width: 200)
                .scaleEffect(y: 2)
            
            Spacer()
            
            // Espace pour équilibrer
            Spacer()
                .frame(width: 30)
        }
        .onAppear {
            print("🔥 ProgressBar: Barre de progression apparue avec valeur: \(progress)")
        }
    }
} 