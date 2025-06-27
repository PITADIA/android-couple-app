import SwiftUI

struct WidgetPreviewSection: View {
    let onWidgetTap: () -> Void
    @EnvironmentObject var appState: AppState
    
    // Utiliser le WidgetService global d'AppState
    private var widgetService: WidgetService? {
        return appState.widgetService
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Titre de la section (transparent)
            HStack {
                Text("Widgets")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.black)
                
                Spacer()
            }
            .padding(.horizontal, 20)
            
            // Widgets défilants (fond transparent)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 16) {
                    // Widget 1: Compteur de jours simple
                    CountdownPreviewWidget(stats: widgetService?.relationshipStats)
                        .frame(height: 172) // Hauteur fixe pour uniformiser
                        .onTapGesture { onWidgetTap() }
                    
                    // Widget 2: Compteur de jours + Distance (comme dans l'image)
                    DistancePreviewWidget(distanceInfo: widgetService?.distanceInfo)
                        .frame(height: 172) // Hauteur fixe pour uniformiser
                        .onTapGesture { onWidgetTap() }
                }
                .padding(.horizontal, 20)
            }
        }
        .onAppear {
            widgetService?.refreshData()
        }
    }
    

}

// MARK: - Countdown Preview Widget
struct CountdownPreviewWidget: View {
    let stats: RelationshipStats?
    
    var body: some View {
        VStack(spacing: 12) {
            // Photos de profil avec cœur (comme le vrai widget)
            HStack(spacing: 6) {
                // Photo utilisateur miniature (placeholder)
                Circle()
                    .fill(Color.white.opacity(0.15))
                    .frame(width: 24, height: 24)
                    .overlay(
                        Text("U")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                    )
                
                // Cœur au milieu
                Image(systemName: "heart.fill")
                    .font(.system(size: 12))
                    .foregroundColor(.white)
                
                // Photo partenaire miniature (placeholder)
                Circle()
                    .fill(Color.white.opacity(0.15))
                    .frame(width: 24, height: 24)
                    .overlay(
                        Text("P")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                    )
            }
            
            // Titre
            Text("Ensemble depuis")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.white.opacity(0.9))
            
            // Contenu principal - Compteur de jours
            VStack(spacing: 4) {
                if let stats = stats {
                    Text("\(stats.daysTotal)")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)
                    Text("JOURS")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                } else {
                    Text("0")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)
                    Text("JOURS")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                }
            }
        }
        .frame(width: 200)
        .frame(maxHeight: .infinity)
        .padding(16)
        .background(
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.99, green: 0.15, blue: 0.48), // #FD267A
                    Color(red: 1.0, green: 0.4, blue: 0.36)    // #FF655B
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(16)
    }
}

// MARK: - Distance Preview Widget (Jours + Distance)
struct DistancePreviewWidget: View {
    let distanceInfo: DistanceInfo?
    @EnvironmentObject var appState: AppState
    
    // Accès aux stats de relation via le WidgetService
    private var widgetService: WidgetService? {
        return appState.widgetService
    }
    
    // Utiliser la même logique que le vrai widget
    private var locationStatus: LocationStatus {
        let hasUserLocation = distanceInfo?.currentUserLocation != nil
        let hasPartnerLocation = distanceInfo?.partnerLocation != nil
        
        if hasUserLocation && hasPartnerLocation {
            return .bothAvailable
        } else if !hasUserLocation && hasPartnerLocation {
            return .userMissing
        } else if hasUserLocation && !hasPartnerLocation {
            return .partnerMissing
        } else {
            return .bothMissing
        }
    }
    
    private enum LocationStatus {
        case bothAvailable
        case userMissing
        case partnerMissing
        case bothMissing
        
        var message: String {
            switch self {
            case .bothAvailable:
                return ""
            case .userMissing:
                return "Activez votre localisation"
            case .partnerMissing:
                return "Partenaire doit activer sa localisation"
            case .bothMissing:
                return "Activez vos localisations"
            }
        }
        
        var showDistance: Bool {
            return self == .bothAvailable
        }
    }
    
    // Données utilisateur et partenaire (comme le vrai widget)
    private var currentUserName: String? {
        return appState.currentUser?.name
    }
    
    private var partnerName: String? {
        // Aucun partenaire connecté si pas de partnerId
        guard let partnerId = appState.currentUser?.partnerId, !partnerId.isEmpty else {
            return nil
        }
        // Pour l'instant on retourne nil car on n'a pas les données du partenaire
        return nil
    }
    
    private var distanceText: String {
        if let distanceInfo = distanceInfo {
            return distanceInfo.formattedDistance
        } else {
            return "? km"
        }
    }
    
    var body: some View {
        HStack(spacing: 16) {
            // Section gauche : Compteur de jours
            VStack(spacing: 8) {
                Text("Ensemble depuis")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
                
                VStack(spacing: 4) {
                    if let stats = widgetService?.relationshipStats {
                        Text("\(stats.daysTotal)")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(.white)
                    } else {
                        Text("0")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(.white)
                    }
                    Text("JOURS")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                }
            }
            .frame(maxWidth: .infinity)
            
            // Séparateur vertical
            Rectangle()
                .fill(.white.opacity(0.2))
                .frame(width: 1)
                .padding(.vertical, 8)
            
            // Section droite : Localisation (MÊME LOGIQUE que le vrai widget)
            VStack(spacing: 8) {
                // Distance ou message d'erreur
                if locationStatus.showDistance {
                    Text(distanceText)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                } else {
                    Text(locationStatus.message)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                }
                
                // Photos miniatures avec cœur (MÊME LOGIQUE que ProfileCircleForWidget)
                HStack(spacing: 8) {
                    // Photo utilisateur miniature
                    PreviewProfileCircle(userName: currentUserName, size: 30)
                    
                    // Traits et cœur miniatures
                    HStack(spacing: 0) {
                        // Trait gauche
                        Rectangle()
                            .fill(Color.white.opacity(0.7))
                            .frame(width: 15, height: 1.5)
                        
                        // Cœur miniature
                        ZStack {
                            Circle()
                                .fill(Color.white.opacity(0.2))
                                .frame(width: 20, height: 20)
                            
                            Image(systemName: "heart.fill")
                                .font(.system(size: 8))
                                .foregroundColor(.white)
                        }
                        
                        // Trait droit
                        Rectangle()
                            .fill(Color.white.opacity(0.7))
                            .frame(width: 15, height: 1.5)
                    }
                    .frame(width: 40)
                    
                    // Photo partenaire miniature (AFFICHE "?" si pas de partenaire)
                    PreviewProfileCircle(userName: partnerName, size: 30)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .frame(width: 280)
        .frame(maxHeight: .infinity)
        .padding(16)
        .background(
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.99, green: 0.15, blue: 0.48), // #FD267A
                    Color(red: 1.0, green: 0.4, blue: 0.36)    // #FF655B
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(16)
    }
}

// MARK: - Preview Profile Circle (Même logique que le vrai widget)
struct PreviewProfileCircle: View {
    let userName: String?
    let size: CGFloat
    
    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(0.15))
                .frame(width: size, height: size)
            
            Text(userInitial)
                .font(.system(size: size * 0.4, weight: .bold))
                .foregroundColor(.white)
        }
    }
    
    private var userInitial: String {
        guard let userName = userName, !userName.isEmpty else {
            return "?"
        }
        return String(userName.prefix(1)).uppercased()
    }
}

// MARK: - Preview
struct WidgetPreviewSection_Previews: PreviewProvider {
    static var previews: some View {
        ZStack {
            Color(red: 0.1, green: 0.02, blue: 0.05)
                .ignoresSafeArea()
            
            WidgetPreviewSection(
                onWidgetTap: {
                    print("Widgets tappés")
                }
            )
            .environmentObject(AppState())
        }
    }
} 