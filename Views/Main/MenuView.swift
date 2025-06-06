import SwiftUI
import AuthenticationServices

struct MenuView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var showingFavorites = false
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond dégradé identique à l'app
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.15, green: 0.05, blue: 0.2),
                        Color(red: 0.25, green: 0.1, blue: 0.3)
                    ]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header
                    HStack {
                        Button("Fermer") {
                            dismiss()
                        }
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                        
                        Spacer()
                        
                        Text("Paramètres")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                        
                        Spacer()
                        
                        // Espace pour équilibrer
                        Text("Fermer")
                            .font(.system(size: 16))
                            .foregroundColor(.clear)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 60)
                    .padding(.bottom, 40)
                    
                    // Options du menu
                    VStack(spacing: 20) {
                        MenuOptionView(
                            icon: "heart.fill",
                            title: "Mes Favoris",
                            subtitle: "\(appState.favoritesService?.getFavoritesCount() ?? 0) questions sauvegardées"
                        ) {
                            showingFavorites = true
                        }
                        
                        MenuOptionView(
                            icon: "questionmark.circle",
                            title: "Aide & Support",
                            subtitle: "Besoin d'aide ?"
                        ) {
                            openSupportEmail()
                        }
                        
                        MenuOptionView(
                            icon: "doc.text",
                            title: "Conditions d'utilisation",
                            subtitle: "Lire nos conditions"
                        ) {
                            openTermsOfService()
                        }
                        
                        MenuOptionView(
                            icon: "hand.raised",
                            title: "Politique de confidentialité",
                            subtitle: "Protection de vos données"
                        ) {
                            // Action confidentialité
                        }
                        
                        Divider()
                            .background(Color.white.opacity(0.3))
                            .padding(.vertical, 10)
                    }
                    .padding(.horizontal, 20)
                    
                    Spacer()
                    
                    // Section supprimer le compte en bas
                    VStack(spacing: 15) {
                        MenuOptionView(
                            icon: "trash",
                            title: "Supprimer le compte",
                            subtitle: "Suppression définitive",
                            isDestructive: false
                        ) {
                            // Action supprimer le compte
                        }
                        
                        // Version de l'app
                        Text("Version 1.0.0")
                            .font(.system(size: 12))
                            .foregroundColor(.white.opacity(0.6))
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 40)
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showingFavorites) {
            FavoritesCardView()
                .environmentObject(appState)
                .environmentObject(appState.favoritesService ?? FavoritesService())
        }
    }
    
    
    private func openSupportEmail() {
        print("🔥 MenuView: Ouverture de l'email de support")
        if let url = URL(string: "mailto:contact@love2loveapp.com") {
            UIApplication.shared.open(url)
        }
    }
    
    private func openTermsOfService() {
        print("🔥 MenuView: Ouverture des conditions d'utilisation")
        if let url = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/") {
            UIApplication.shared.open(url)
        }
    }
}

struct MenuOptionView: View {
    let icon: String
    let title: String
    let subtitle: String
    let isDestructive: Bool
    let action: () -> Void
    
    init(icon: String, title: String, subtitle: String, isDestructive: Bool = false, action: @escaping () -> Void) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.isDestructive = isDestructive
        self.action = action
    }
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 15) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(isDestructive ? .red : .white)
                    .frame(width: 30)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(isDestructive ? .red : .white)
                        .multilineTextAlignment(.leading)
                    
                    Text(subtitle)
                        .font(.system(size: 14))
                        .foregroundColor(isDestructive ? .red.opacity(0.7) : .white.opacity(0.7))
                        .multilineTextAlignment(.leading)
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 14))
                    .foregroundColor(isDestructive ? .red.opacity(0.7) : .white.opacity(0.5))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 15)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.1))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.2), lineWidth: 1)
                    )
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    MenuView()
        .environmentObject(AppState())
} 