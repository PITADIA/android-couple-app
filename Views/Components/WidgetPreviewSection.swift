import SwiftUI

struct WidgetPreviewSection: View {
    let onWidgetTap: () -> Void
    
    var body: some View {
        Button(action: onWidgetTap) {
            HStack(spacing: 16) {
                // Contenu principal
                VStack(alignment: .leading, spacing: 6) {
                    // Titre principal
                    Text(ui: "add_widgets", comment: "Add widgets title")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                    
                    // Sous-titre
                    Text(ui: "feel_closer_partner", comment: "Feel closer partner subtitle")
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
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