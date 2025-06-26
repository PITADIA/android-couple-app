import SwiftUI

struct LocationPartnerMessageView: View {
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à l'app avec dégradé rose doux en arrière-plan
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea(.all)
                
                // Dégradé rose très doux en arrière-plan
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FD267A").opacity(0.03),
                        Color(hex: "#FF655B").opacity(0.02)
                    ]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                VStack {
                    Spacer()
                    
                    // Contenu du message partenaire
                    LocationPartnerExplanationView {
                        dismiss()
                    }
                    
                    Spacer()
                }
            }
        }
        .navigationBarHidden(true)
    }
}

#Preview {
    LocationPartnerMessageView()
} 