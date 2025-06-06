import SwiftUI

struct ProgressBar: View {
    let progress: Double
    
    var body: some View {
        HStack {
            // Bouton retour (si pas à la première étape)
            if progress > 0.125 { // Pas à la première étape
                Button(action: {
                    print("🔥 ProgressBar: Bouton retour pressé")
                    // Cette action sera gérée par le parent
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
            
            // Barre de progression
            ProgressView(value: progress)
                .progressViewStyle(LinearProgressViewStyle(tint: .orange))
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