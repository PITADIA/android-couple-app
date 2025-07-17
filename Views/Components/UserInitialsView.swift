import SwiftUI

struct UserInitialsView: View {
    let name: String
    let size: CGFloat
    
    private var firstLetter: String {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return String(trimmedName.prefix(1)).uppercased()
    }
    
    private var backgroundColor: Color {
        // Générer une couleur aléatoire mais cohérente basée sur le nom
        let seed = name.hash
        let colors: [Color] = [
            Color(hex: "#FD267A"), // Rose principal de l'app
            Color(hex: "#FF69B4"), // Rose vif
            Color(hex: "#F06292"), // Rose clair
            Color(hex: "#E91E63"), // Rose intense
            Color(hex: "#FF1493"), // Rose fuchsia
            Color(hex: "#DA70D6"), // Orchidée rose
            Color(hex: "#FF6B9D"), // Rose doux
            Color(hex: "#E1306C")  // Rose Instagram
        ]
        
        let index = abs(seed) % colors.count
        return colors[index]
    }
    
    var body: some View {
        Circle()
            .fill(backgroundColor)
            .frame(width: size, height: size)
            .overlay(
                Text(firstLetter)
                    .font(.system(size: size * 0.4, weight: .semibold))
                    .foregroundColor(.white)
            )
    }
}

#Preview {
    VStack(spacing: 20) {
        UserInitialsView(name: "Marie", size: 80)
        UserInitialsView(name: "Jean", size: 60)
        UserInitialsView(name: "Sophie", size: 40)
        UserInitialsView(name: "Lucas", size: 100)
    }
    .padding()
} 