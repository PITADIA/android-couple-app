import SwiftUI

struct WidgetPreviewSection: View {
    let onWidgetTap: () -> Void
    
    var body: some View {
        Button(action: onWidgetTap) {
            HStack(spacing: 16) {
                // Contenu principal
                VStack(alignment: .leading, spacing: 6) {
                    // Titre principal
                    Text("Ajoutez vos widgets")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.leading)
                    
                    // Sous-titre
                    Text("Pour vous sentir encore plus proche de votre partenaire")
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.leading)
                }
                
                Spacer()
                
                // Icône à droite
                Image(systemName: "chevron.right")
                    .font(.system(size: 14))
                    .foregroundColor(.black.opacity(0.5))
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 20)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.95))
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
            )
        }
        .buttonStyle(PlainButtonStyle())
        .padding(.horizontal, 20)
    }
}

struct WidgetPreviewSection_Previews: PreviewProvider {
    static var previews: some View {
        WidgetPreviewSection(onWidgetTap: {
            print("Widget card tapped!")
        })
        .padding()
        .background(Color.purple)
    }
} 