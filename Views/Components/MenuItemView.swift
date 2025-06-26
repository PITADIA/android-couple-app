import SwiftUI

struct MenuItemView: View {
    let title: String
    let subtitle: String
    let icon: String
    let color: Color
    
    var body: some View {
        HStack(spacing: 15) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(color)
                .frame(width: 30)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                
                Text(subtitle)
                    .font(.system(size: 14))
                    .foregroundColor(.black.opacity(0.7))
                    .multilineTextAlignment(.leading)
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .font(.system(size: 14))
                .foregroundColor(.black.opacity(0.5))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 15)
        .background(Color.white.opacity(0.8))
        .cornerRadius(12)
    }
}

#Preview {
    MenuItemView(
        title: "Code Partenaire",
        subtitle: "Gérer votre connexion",
        icon: "person.2.fill",
        color: .purple
    )
    .padding()
    .background(Color.black)
} 