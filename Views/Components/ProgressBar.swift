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
                    onBackPressed?()
                }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 20))
                        .foregroundColor(.black)
                }
            } else {
                Spacer()
                    .frame(width: 30)
            }
            
            Spacer()
            
            // Barre de progression avec la couleur principale rose
            ProgressView(value: progress)
                .progressViewStyle(LinearProgressViewStyle(tint: Color(hex: "#FD267A")))
                .frame(width: 200)
                .scaleEffect(y: 2)
            
            Spacer()
            
            // Espace pour équilibrer
            Spacer()
                .frame(width: 30)
        }
    }
} 