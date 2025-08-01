import SwiftUI
import CoreLocation

struct PartnerDistanceView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var partnerLocationService = PartnerLocationService.shared
    let onPartnerAvatarTap: (() -> Void)?
    let onDistanceTap: (_ showPartnerMessageOnly: Bool) -> Void
    
    // Cache pour éviter les recalculs constants + persistance
    @State private var cachedDistance: String = "km ?"
    @State private var lastCalculationTime: Date = Date.distantPast
    
    // Clés pour la persistance dans UserDefaults
    private let lastDistanceKey = "last_known_partner_distance"
    private let lastDistanceUpdateKey = "last_distance_update_time"
    
    // Calculer la distance entre les partenaires avec cache
    private var partnerDistance: String {
        // Cache ultra-rapide - ne recalculer que toutes les 2 secondes
        let now = Date()
        if now.timeIntervalSince(lastCalculationTime) < 2 && cachedDistance != "km ?" && cachedDistance != "? mi" {
            return cachedDistance
        }
        
        guard let currentUser = appState.currentUser else {
            return "km ?"
        }
        
        // Synchroniser currentUser.currentLocation avec LocationService (comme WidgetService)
        if let locationServiceLocation = appState.locationService?.currentLocation,
           currentUser.currentLocation != locationServiceLocation {
            // Mettre à jour la localisation dans currentUser pour synchroniser
            var updatedUser = currentUser
            updatedUser.currentLocation = locationServiceLocation
            appState.currentUser = updatedUser
        }
        
        // Utiliser currentUser.currentLocation (synchronisé)
        guard let currentLocation = currentUser.currentLocation else {
            return "km ?"
        }
        
        // Si pas de partenaire connecté
        guard let partnerId = currentUser.partnerId,
              !partnerId.isEmpty else {
            let currentLanguage: String
            if #available(iOS 16.0, *) {
                currentLanguage = Locale.current.language.languageCode?.identifier ?? "fr"
            } else {
                currentLanguage = Locale.current.languageCode ?? "fr"
            }
            
            if currentLanguage == "en" {
                return "? mi"
            } else {
                return "km ?"
            }
        }
        
        // Utiliser la même logique que WidgetService
        guard let partnerLocation = partnerLocationService.partnerLocation else {
            return "? km".convertedForLocale()
        }
        
        let distance = currentLocation.distance(to: partnerLocation)
        
        // Même formatage que WidgetService
        if distance < 1 {
            return "widget_together_text".localized.capitalized
        }
        
        let baseDistance: String
        if distance < 10 {
            baseDistance = String(format: "%.1f km", distance)
        } else {
            baseDistance = "\(Int(distance)) km"
        }
        
        return baseDistance.convertedForLocale()
    }
    
    // Fonction pour forcer la synchronisation des localisations
    private func forceSyncLocation() {
        guard var currentUser = appState.currentUser else { return }
        
        if let locationServiceLocation = appState.locationService?.currentLocation,
           currentUser.currentLocation != locationServiceLocation {
            print("🔄 PartnerDistanceView: Synchronisation forcée de la localisation")
            print("🔄 LocationService: \(locationServiceLocation.displayName)")
            print("🔄 CurrentUser avant: \(currentUser.currentLocation?.displayName ?? "nil")")
            
            currentUser.currentLocation = locationServiceLocation
            appState.currentUser = currentUser
            
            print("🔄 CurrentUser après: \(currentUser.currentLocation?.displayName ?? "nil")")
        }
    }
    
    // Fonction pour mettre à jour le cache de manière sécurisée
    private func updateCacheIfNeeded() {
        // Forcer la synchronisation avant de calculer
        forceSyncLocation()
        
        let newDistance = partnerDistance
        let now = Date()
        
        // Cache réduit à 2 secondes et forcer la mise à jour si les données changent
        if newDistance != cachedDistance || now.timeIntervalSince(lastCalculationTime) >= 2 {
            if newDistance != cachedDistance {
                print("🌍 PartnerDistanceView: Distance mise à jour: \(cachedDistance) → \(newDistance)")
                
                // Sauvegarder la nouvelle distance dans le cache persistent
                saveDistanceToCache(newDistance)
                
                // Debug détaillé
                if let currentUser = appState.currentUser {
                    print("🌍 Debug - User location: \(currentUser.currentLocation?.displayName ?? "nil")")
                    print("🌍 Debug - Partner location: \(partnerLocationService.partnerLocation?.displayName ?? "nil")")
                    print("🌍 Debug - Partner ID: \(currentUser.partnerId ?? "nil")")
                }
            }
            cachedDistance = newDistance
            lastCalculationTime = now
        }
    }
    
    // Charger la dernière distance connue depuis UserDefaults
    private func loadLastKnownDistance() {
        let savedDistance = UserDefaults.standard.string(forKey: lastDistanceKey) ?? "km ?"
        let savedTime = UserDefaults.standard.object(forKey: lastDistanceUpdateKey) as? Date ?? Date.distantPast
        
        // Si la dernière distance date de moins de 24h, l'utiliser
        if Date().timeIntervalSince(savedTime) < 24 * 60 * 60 && savedDistance != "km ?" {
            cachedDistance = savedDistance
            lastCalculationTime = savedTime
            print("📱 PartnerDistanceView: Distance chargée depuis le cache: \(savedDistance)")
        } else {
            print("📱 PartnerDistanceView: Cache expiré ou vide, démarrage avec valeur par défaut")
        }
    }
    
    // Sauvegarder la distance dans UserDefaults
    private func saveDistanceToCache(_ distance: String) {
        if distance != "km ?" && distance != "? mi" {
            UserDefaults.standard.set(distance, forKey: lastDistanceKey)
            UserDefaults.standard.set(Date(), forKey: lastDistanceUpdateKey)
            print("💾 PartnerDistanceView: Distance sauvegardée dans le cache: \(distance)")
        }
    }
    
    // Fonction pour forcer une mise à jour immédiate (ignorer le cache)
    private func forceUpdateDistance() {
        print("🚀 PartnerDistanceView: Mise à jour forcée de la distance")
        forceSyncLocation()
        let newDistance = partnerDistance
        cachedDistance = newDistance
        lastCalculationTime = Date()
        
        // Sauvegarder dans le cache persistent
        saveDistanceToCache(newDistance)
    }
    
    // Vérifier si la localisation est manquante pour afficher le flow de permission
    private var shouldShowLocationPermissionFlow: Bool {
        // Si on affiche "km ?", alors le tutoriel doit être disponible
        return cachedDistance == "km ?"
    }
    
    // Vérifier si on doit afficher directement l'écran partenaire
    private var shouldShowPartnerLocationMessage: Bool {
        guard let currentUser = appState.currentUser else { return false }
        
        // Synchroniser avec LocationService si nécessaire
        var hasUserLocation = currentUser.currentLocation != nil
        if !hasUserLocation, let locationServiceLocation = appState.locationService?.currentLocation {
            hasUserLocation = true
        }
        
        // Si on a un partenaire connecté ET notre localisation mais qu'on voit toujours "km ?"
        if let partnerId = currentUser.partnerId, 
           !partnerId.isEmpty,
           hasUserLocation,
           cachedDistance == "km ?" {
            return true
        }
        
        return false
    }
    
    // Vérifier si un partenaire est connecté
    private var hasConnectedPartner: Bool {
        guard let partnerId = appState.currentUser?.partnerId else { return false }
        return !partnerId.isEmpty
    }
    
    // URL de l'image du partenaire
    private var partnerImageURL: String? {
        return partnerLocationService.partnerProfileImageURL
    }
    
    var body: some View {
        HStack(spacing: 0) {
            // Photo de profil utilisateur actuel
            UserProfileImage(
                imageURL: appState.currentUser?.profileImageURL,
                userName: appState.currentUser?.name ?? "",
                size: 80
            )
            
            // Ligne discontinue courbe avec distance au centre (sans espacement)
            GeometryReader { geometry in
                ZStack {
                    // Ligne courbe complète en arrière-plan
                    CurvedDashedLine(screenWidth: geometry.size.width)
                        .stroke(Color.white, style: StrokeStyle(lineWidth: 3, dash: [8, 4]))
                        .frame(height: 40)
                    
                    // Distance au centre (cliquable si localisation manquante)
                    HStack {
                        Spacer()
                        
                        Button(action: {
                            if shouldShowLocationPermissionFlow {
                                onDistanceTap(shouldShowPartnerLocationMessage)
                            }
                            // Ne rien faire si pas cliquable, mais pas de .disabled()
                        }) {
                            Text(cachedDistance)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 20)
                                        .fill(Color.white.opacity(0.95))
                                        .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
                                )
                        }
                        .buttonStyle(PlainButtonStyle())
                        .allowsHitTesting(shouldShowLocationPermissionFlow) // Contrôle de l'interactivité sans effet visuel
                        
                        Spacer()
                    }
                }
            }
            .frame(height: 40)
            
            // Photo de profil du partenaire
            Button(action: {
                // Si pas de partenaire connecté, ouvrir la vue de gestion des partenaires
                if !hasConnectedPartner {
                    onPartnerAvatarTap?()
                }
            }) {
                PartnerProfileImage(
                    hasPartner: hasConnectedPartner,
                    imageURL: partnerImageURL,
                    partnerName: partnerLocationService.partnerName ?? "",
                    size: 80
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 40)
        .padding(.vertical, 16)
        .onAppear {
            print("🚀 PartnerDistanceView: onAppear - Initialisation")
            
            // 1. Charger d'abord la dernière distance connue depuis le cache
            loadLastKnownDistance()
            
            // 2. Configurer le listener partenaire en parallèle
            if let partnerId = appState.currentUser?.partnerId {
                partnerLocationService.configureListener(for: partnerId)
            }
            
            // 3. Forcer une mise à jour immédiate pour les nouvelles données
            forceUpdateDistance()
        }
        .onChange(of: appState.locationService?.currentLocation) { oldValue, newValue in
            print("🌍 PartnerDistanceView: LocationService localisation changée: \(newValue?.displayName ?? "nil")")
            // Synchroniser avec currentUser (comme WidgetService)
            if let newLocation = newValue, var currentUser = appState.currentUser {
                currentUser.currentLocation = newLocation
                appState.currentUser = currentUser
                print("🌍 PartnerDistanceView: currentUser.currentLocation synchronisé")
            }
            // Mise à jour immédiate pour affichage instantané
            forceUpdateDistance()
        }
        .onChange(of: partnerLocationService.partnerLocation) { oldValue, newValue in
            print("🌍 PartnerDistanceView: Localisation partenaire changée: \(newValue?.displayName ?? "nil")")
            // Mise à jour immédiate pour affichage instantané
            forceUpdateDistance()
        }
        .onChange(of: appState.currentUser?.partnerId) { oldValue, newValue in
            print("🌍 PartnerDistanceView: Partner ID changé: \(newValue ?? "nil")")
            if let partnerId = newValue {
                partnerLocationService.configureListener(for: partnerId)
            }
            // Mise à jour immédiate pour affichage instantané
            forceUpdateDistance()
        }
        .onReceive(Timer.publish(every: 5, on: .main, in: .common).autoconnect()) { _ in
            // Timer réduit à 5 secondes pour des mises à jour plus fréquentes
            updateCacheIfNeeded()
        }
    }
}

// MARK: - User Profile Image
struct UserProfileImage: View {
    let imageURL: String?
    let userName: String
    let size: CGFloat
    
    var body: some View {
        ZStack {
            // Léger effet de surbrillance autour
            Circle()
                .fill(Color.white.opacity(0.35))
                .frame(width: size + 12, height: size + 12)
                .blur(radius: 6)
            
            if let imageURL = imageURL, !imageURL.isEmpty {
                // Image de profil utilisateur (taille complète)
                AsyncImageView(
                    imageURL: imageURL,
                    width: size,
                    height: size,
                    cornerRadius: size / 2
                )
            } else {
                // Afficher les initiales avec fond coloré si pas d'image
                if !userName.isEmpty {
                    UserInitialsView(name: userName, size: size)
                } else {
                    // Fallback vers l'icône grise si pas de nom
                    Circle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: size, height: size)
                    
                    Image(systemName: "person.fill")
                        .font(.system(size: size * 0.4))
                        .foregroundColor(.gray)
                }
            }
            
            // Bordure blanche
            Circle()
                .stroke(Color.white, lineWidth: 3)
                .frame(width: size, height: size)
        }
    }
}

// MARK: - Partner Profile Image
struct PartnerProfileImage: View {
    let hasPartner: Bool
    let imageURL: String?
    let partnerName: String
    let size: CGFloat
    
    var body: some View {
        ZStack {
            // Léger effet de surbrillance autour
            Circle()
                .fill(Color.white.opacity(hasPartner ? 0.35 : 0.2))
                .frame(width: size + 12, height: size + 12)
                .blur(radius: 6)
            
            if hasPartner {
                if let imageURL = imageURL, !imageURL.isEmpty {
                    // Image de profil du partenaire (taille complète)
                    AsyncImageView(
                        imageURL: imageURL,
                        width: size,
                        height: size,
                        cornerRadius: size / 2
                    )
                } else {
                    // Afficher les initiales avec fond coloré si partenaire connecté et pas d'image
                    if !partnerName.isEmpty {
                        UserInitialsView(name: partnerName, size: size)
                    } else {
                        // Fallback vers l'icône grise si pas de nom
                        Circle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(width: size, height: size)
                        
                        Image(systemName: "person.fill")
                            .font(.system(size: size * 0.4))
                            .foregroundColor(.gray)
                    }
                }
            } else {
                // Cercle de base pour partenaire non connecté
                Circle()
                    .fill(Color.white.opacity(0.3))
                    .frame(width: size, height: size)
                
                // Icône de profil vide
                Image(systemName: "person.fill")
                    .font(.system(size: size * 0.4))
                    .foregroundColor(.white.opacity(0.6))
            }
            
            // Bordure
            Circle()
                .stroke(hasPartner ? Color.white : Color.white.opacity(0.4), lineWidth: 3)
                .frame(width: size, height: size)
        }
    }
}

// MARK: - Curved Dashed Line Shape
struct CurvedDashedLine: Shape {
    let screenWidth: CGFloat
    
    func path(in rect: CGRect) -> Path {
        var path = Path()
        
        // Calculer la hauteur de la courbe basée sur la largeur de l'écran
        // Plus l'écran est large, plus la courbe est prononcée
        let curveHeight = min(screenWidth * 0.03, 15) // Maximum 15 points
        
        // Points de départ et d'arrivée
        let startPoint = CGPoint(x: 0, y: rect.height / 2 + curveHeight)
        let endPoint = CGPoint(x: rect.width, y: rect.height / 2 + curveHeight)
        
        // Point de contrôle au centre pour créer la courbe vers le haut
        let controlPoint = CGPoint(x: rect.width / 2, y: rect.height / 2 - curveHeight)
        
        // Créer la courbe quadratique
        path.move(to: startPoint)
        path.addQuadCurve(to: endPoint, control: controlPoint)
        
        return path
    }
}

// MARK: - Dashed Line Shape (conservé pour compatibilité)
struct DashedLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.height / 2))
        path.addLine(to: CGPoint(x: rect.width, y: rect.height / 2))
        return path
    }
}

 