import SwiftUI

struct PartnerInviteView: View {
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 16) {
                // Icône cœur avec rayons
                ZStack {
                    // Rayons autour du cœur
                    ForEach(0..<8, id: \.self) { index in
                        Rectangle()
                            .fill(Color(hex: "#FD267A").opacity(0.6))
                            .frame(width: 2, height: 8)
                            .offset(y: -20)
                            .rotationEffect(.degrees(Double(index) * 45))
                    }
                    
                    // Cœur principal
                    Image(systemName: "heart.fill")
                        .font(.system(size: 24))
                        .foregroundColor(Color(hex: "#FD267A"))
                }
                .frame(width: 50, height: 50)
                
                // Texte d'invitation
                VStack(alignment: .leading, spacing: 4) {
                    Text("Invite ton partenaire")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.black)
                    
                    Text("Profite ensemble de l'expérience complète de l'application !")
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.leading)
                }
                
                Spacer()
                
                // Flèche
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Color(hex: "#FD267A"))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.95))
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
            )
            .padding(.horizontal, 20)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Preview
struct PartnerInviteView_Previews: PreviewProvider {
    static var previews: some View {
        ZStack {
            Color(red: 0.1, green: 0.02, blue: 0.05)
                .ignoresSafeArea()
            
            PartnerInviteView {
                print("Invitation partenaire tappée")
            }
        }
    }
} 