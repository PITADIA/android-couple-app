import SwiftUI

struct WidgetPreviewSection: View {
    let onWidgetTap: () -> Void
    let onDistanceWidgetTap: ((_ showPartnerMessageOnly: Bool) -> Void)?
    @EnvironmentObject var appState: AppState
    
    // Utiliser le WidgetService global d'AppState
    private var widgetService: WidgetService? {
        return appState.widgetService
    }
    
    // Vérifier si l'utilisateur peut accéder au widget de distance (maintenant gratuit)
    private var canAccessDistanceWidget: Bool {
        return true // Tous les widgets sont maintenant gratuits
    }
    
    // Vérifier si la localisation est manquante pour afficher le flow de permission
    private var shouldShowLocationPermissionFlow: Bool {
        guard let currentUser = appState.currentUser else { return false }
        
        // Si pas de localisation actuelle
        if currentUser.currentLocation == nil {
            return true
        }
        
        // Si partenaire connecté mais pas de localisation partenaire
        if let partnerId = currentUser.partnerId, !partnerId.isEmpty {
            // Vérifier si on a des données de distance du service widget
            return widgetService?.distanceInfo == nil
        }
        
        return false
    }
    
    // Vérifier si on doit afficher directement l'écran partenaire
    private var shouldShowPartnerLocationMessage: Bool {
        guard let currentUser = appState.currentUser else { return false }
        
        // Si on a notre localisation ET un partenaire connecté mais qu'on n'a pas de distance
        if let partnerId = currentUser.partnerId, 
           !partnerId.isEmpty,
           currentUser.currentLocation != nil,
           widgetService?.distanceInfo == nil {
            return true
        }
        
        return false
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
                    // Widget 1: Distance (ÉCHANGÉ: maintenant en premier)
                    DistancePreviewWidget(
                        distanceInfo: widgetService?.distanceInfo,
                        appState: appState,
                        onDistanceWidgetTap: onDistanceWidgetTap,
                        canAccess: canAccessDistanceWidget
                    )
                        .frame(height: 172) // Hauteur fixe pour uniformiser
                        .onTapGesture { 
                            // Vérifier l'accès premium avant toute action
                            if !canAccessDistanceWidget {
                                // Utilisateur non premium -> Afficher paywall
                                print("🔒 WidgetPreviewSection: Accès widget distance bloqué - Affichage paywall")
                                appState.freemiumManager?.handleDistanceWidgetAccess {
                                    print("🔒 WidgetPreviewSection: Accès autorisé après vérification freemium")
                                    self.handleDistanceWidgetAction()
                                }
                            } else {
                                // Utilisateur premium -> Logique normale
                                handleDistanceWidgetAction()
                            }
                        }
                    
                    // Widget 2: Compteur de jours simple (ÉCHANGÉ: maintenant en deuxième)
                    CountdownPreviewWidget(stats: widgetService?.relationshipStats)
                        .frame(height: 172) // Hauteur fixe pour uniformiser
                        .onTapGesture { onWidgetTap() }
                    
                    // Widget 3: Widget Distance avec carte
                    MapDistancePreviewWidget(
                        distanceInfo: widgetService?.distanceInfo,
                        canAccess: canAccessDistanceWidget
                    )
                        .frame(height: 172) // Hauteur fixe pour uniformiser
                        .onTapGesture { 
                            if !canAccessDistanceWidget {
                                appState.freemiumManager?.handleDistanceWidgetAccess {
                                    print("🔒 WidgetPreviewSection: Accès autorisé pour map widget")
                                }
                            } else {
                                onWidgetTap()
                            }
                        }
                }
                .padding(.horizontal, 20)
            }
        }
        .onAppear {
            widgetService?.refreshData()
        }
    }
    
    // NOUVEAU: Méthode pour gérer l'action du widget distance (logique existante)
    private func handleDistanceWidgetAction() {
        // Si localisation manquante, utiliser l'action spéciale, sinon action normale
        if shouldShowLocationPermissionFlow, let onDistanceWidgetTap = onDistanceWidgetTap {
            onDistanceWidgetTap(shouldShowPartnerLocationMessage)
        } else {
            onWidgetTap()
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

// MARK: - Distance Preview Widget
struct DistancePreviewWidget: View {
    let distanceInfo: DistanceInfo?
    let appState: AppState
    let onDistanceWidgetTap: ((_ showPartnerMessageOnly: Bool) -> Void)?
    let canAccess: Bool
    
    // NOUVEAU: Accès aux stats de relation via le WidgetService
    private var widgetService: WidgetService? {
        return appState.widgetService
    }
    
    private var distanceText: String {
        if !canAccess {
            return "? km"
        }
        
        if let distanceInfo = distanceInfo {
            return distanceInfo.formattedDistance
        } else {
            return "? km"
        }
    }
    
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
    
    var body: some View {
        HStack(spacing: 16) {
            // Section gauche : Compteur de jours (CORRECTION: Utiliser les vraies données)
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
            
            // Section droite : Localisation miniature
            VStack(spacing: 8) {
                // Distance ou message d'erreur
                if locationStatus.showDistance && canAccess {
                    Text(distanceText)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                } else if !canAccess {
                    HStack(spacing: 4) {
                        Image(systemName: "crown.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.yellow)
                        Text("Premium")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)
                    }
                } else {
                    Text(locationStatus.message)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                }
                
                // Photos miniatures avec cœur
                HStack(spacing: 8) {
                    // Photo utilisateur miniature (placeholder)
                    Circle()
                        .fill(Color.white.opacity(0.15))
                        .frame(width: 30, height: 30)
                        .overlay(
                            Text("U")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                        )
                    
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
                            
                            Image(systemName: canAccess ? "heart.fill" : "lock.fill")
                                .font(.system(size: 8))
                                .foregroundColor(.white)
                        }
                        
                        // Trait droit
                        Rectangle()
                            .fill(Color.white.opacity(0.7))
                            .frame(width: 15, height: 1.5)
                    }
                    .frame(width: 40)
                    
                    // Photo partenaire miniature (placeholder)
                    Circle()
                        .fill(Color.white.opacity(0.15))
                        .frame(width: 30, height: 30)
                        .overlay(
                            Text("P")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                        )
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

// MARK: - Map Distance Preview Widget
struct MapDistancePreviewWidget: View {
    let distanceInfo: DistanceInfo?
    let canAccess: Bool
    
    private var distanceText: String {
        if !canAccess {
            return "? km"
        }
        
        if let distanceInfo = distanceInfo {
            return distanceInfo.formattedDistance
        } else {
            return "? km"
        }
    }
    
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
                return "Ta localisation doit être activée pour voir votre distance"
            case .partnerMissing:
                return "Ton partenaire doit activer sa localisation pour voir votre distance"
            case .bothMissing:
                return "Activez vos localisations pour voir votre distance"
            }
        }
        
        var showDistance: Bool {
            return self == .bothAvailable
        }
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Image de fond personnalisée (même que le vrai widget)
                Image("ouioui")
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .clipped()
                
                // Overlay semi-transparent pour améliorer la lisibilité
                Color.black.opacity(0.2)
                
                VStack(spacing: 16) {
                    // Distance en haut
                    if locationStatus.showDistance && canAccess {
                        Text("Notre distance : \(distanceText)")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .shadow(color: .black.opacity(0.5), radius: 1, x: 0, y: 1)
                    } else if !canAccess {
                        HStack(spacing: 6) {
                            Image(systemName: "crown.fill")
                                .font(.system(size: 12))
                                .foregroundColor(.yellow)
                            Text("Widget Premium")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.white)
                        }
                        .shadow(color: .black.opacity(0.5), radius: 1, x: 0, y: 1)
                    } else {
                        Text(locationStatus.message)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 12)
                            .shadow(color: .black.opacity(0.5), radius: 1, x: 0, y: 1)
                    }
                    
                    // Section avec photos et cœur (plus compacte)
                    HStack(spacing: 16) {
                        // Photo utilisateur (placeholder)
                        Circle()
                            .fill(Color.white.opacity(0.15))
                            .frame(width: 50, height: 50)
                            .overlay(
                                Text("U")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.white)
                            )
                        
                        // Traits discontinus avec cœur au milieu
                        HStack(spacing: 0) {
                            // Trait gauche
                            Rectangle()
                                .fill(Color.white.opacity(0.9))
                                .frame(width: 30, height: 2)
                            
                            // Cœur au milieu
                            ZStack {
                                Circle()
                                    .fill(Color.white.opacity(0.3))
                                    .frame(width: 28, height: 28)
                                
                                Image(systemName: canAccess ? "heart.fill" : "lock.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white)
                            }
                            
                            // Trait droit
                            Rectangle()
                                .fill(Color.white.opacity(0.9))
                                .frame(width: 30, height: 2)
                        }
                        .frame(width: 88)
                        
                        // Photo partenaire (placeholder)
                        Circle()
                            .fill(Color.white.opacity(0.15))
                            .frame(width: 50, height: 50)
                            .overlay(
                                Text("P")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.white)
                            )
                    }
                }
                .padding(.horizontal, 16)
            }
        }
        .frame(width: 280)
        .frame(maxHeight: .infinity)
        .cornerRadius(16)
        .clipped()
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
                },
                onDistanceWidgetTap: { showPartnerMessageOnly in
                    print("Widget distance tappé: \(showPartnerMessageOnly)")
                }
            )
            .environmentObject(AppState())
        }
    }
} 