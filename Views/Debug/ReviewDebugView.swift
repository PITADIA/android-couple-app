import SwiftUI

struct ReviewDebugView: View {
    @State private var reviewStatus: (hasPartner: Bool, favoritesCount: Int, canRequest: Bool) = (false, 0, false)
    
    var body: some View {
        VStack(spacing: 20) {
            Text(NSLocalizedString("debug_review_system", comment: "Debug review system title"))
                .font(.title)
                .padding()
            
            VStack(alignment: .leading, spacing: 10) {
                Text(NSLocalizedString("current_status", comment: "Current status text"))
                    .font(.headline)
                
                Text(NSLocalizedString("partner_connected", comment: "Partner connected text") + " \(reviewStatus.hasPartner ? "✅" : "❌")")
                Text(NSLocalizedString("favorites_count", comment: "Favorites count text") + " \(reviewStatus.favoritesCount)/20")
                Text(NSLocalizedString("can_request_review", comment: "Can request review text") + " \(reviewStatus.canRequest ? "✅" : "❌")")
            }
            .padding()
            .background(Color.gray.opacity(0.1))
            .cornerRadius(10)
            
            VStack(spacing: 15) {
                Button(NSLocalizedString("simulate_partner_connection", comment: "Simulate partner connection button")) {
                    ReviewRequestService.shared.trackPartnerConnected()
                    updateStatus()
                }
                .buttonStyle(.borderedProminent)
                
                Button(NSLocalizedString("add_5_favorites", comment: "Add 5 favorites button")) {
                    for _ in 0..<5 {
                        ReviewRequestService.shared.trackFavoriteAdded()
                    }
                    updateStatus()
                }
                .buttonStyle(.bordered)
                
                Button(NSLocalizedString("add_1_favorite", comment: "Add 1 favorite button")) {
                    ReviewRequestService.shared.trackFavoriteAdded()
                    updateStatus()
                }
                .buttonStyle(.bordered)
                
                Button(NSLocalizedString("remove_1_favorite", comment: "Remove 1 favorite button")) {
                    ReviewRequestService.shared.trackFavoriteRemoved()
                    updateStatus()
                }
                .buttonStyle(.bordered)
                
                Button(NSLocalizedString("reset", comment: "Reset button")) {
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