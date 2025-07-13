import SwiftUI

struct ReviewDebugView: View {
    @State private var reviewStatus: (hasPartner: Bool, favoritesCount: Int, canRequest: Bool) = (false, 0, false)
    
    var body: some View {
        VStack(spacing: 20) {
            Text("Debug Review System")
                .font(.title)
                .padding()
            
            VStack(alignment: .leading, spacing: 10) {
                Text("Status actuel:")
                    .font(.headline)
                
                Text("Partenaire connecté: \(reviewStatus.hasPartner ? "✅" : "❌")")
                Text("Favoris: \(reviewStatus.favoritesCount)/20")
                Text("Peut demander review: \(reviewStatus.canRequest ? "✅" : "❌")")
            }
            .padding()
            .background(Color.gray.opacity(0.1))
            .cornerRadius(10)
            
            VStack(spacing: 15) {
                Button("Simuler connexion partenaire") {
                    ReviewRequestService.shared.trackPartnerConnected()
                    updateStatus()
                }
                .buttonStyle(.borderedProminent)
                
                Button("Ajouter 5 favoris") {
                    for _ in 0..<5 {
                        ReviewRequestService.shared.trackFavoriteAdded()
                    }
                    updateStatus()
                }
                .buttonStyle(.bordered)
                
                Button("Ajouter 1 favori") {
                    ReviewRequestService.shared.trackFavoriteAdded()
                    updateStatus()
                }
                .buttonStyle(.bordered)
                
                Button("Supprimer 1 favori") {
                    ReviewRequestService.shared.trackFavoriteRemoved()
                    updateStatus()
                }
                .buttonStyle(.bordered)
                
                Button("Réinitialiser") {
                    ReviewRequestService.shared.resetReviewStatus()
                    updateStatus()
                }
                .buttonStyle(.borderedProminent)
                .foregroundColor(.red)
            }
            
            Spacer()
        }
        .padding()
        .onAppear {
            updateStatus()
        }
    }
    
    private func updateStatus() {
        reviewStatus = ReviewRequestService.shared.getCurrentStatus()
    }
}

#Preview {
    ReviewDebugView()
} 