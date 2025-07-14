import SwiftUI

struct DaysTogetherCard: View {
    @EnvironmentObject var appState: AppState
    
    private var daysTogetherText: String {
        guard let currentUser = appState.currentUser,
              let relationshipStartDate = currentUser.relationshipStartDate else {
            return "0"
        }
        
        let calendar = Calendar.current
        let daysDifference = calendar.dateComponents([.day], from: relationshipStartDate, to: Date()).day ?? 0
        return "\(max(0, daysDifference))"
    }
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 8) {
                // Nombre de jours
                Text(daysTogetherText)
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(Color(hex: "#FD267A"))
                
                // Texte "Jours ensemble"
                VStack(alignment: .leading, spacing: 2) {
                    Text("days".localized)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.black)
                    
                    Text("together".localized)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.black)
                }
            }
            
            Spacer()
            
            // Cœur à droite
            Image(systemName: "heart.fill")
                .font(.system(size: 50))
                .foregroundColor(Color(hex: "#FD267A"))
                .opacity(0.8)
        }
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color(hex: "#FD267A").opacity(0.1))
        )
        .padding(.horizontal, 20)
    }
}

#Preview {
    DaysTogetherCard()
        .environmentObject(AppState())
} 