import SwiftUI
import CoreLocation

struct PartnerDistanceView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var partnerLocationService = PartnerLocationService.shared
    let onPartnerAvatarTap: (() -> Void)?
    let onDistanceTap: (_ showPartnerMessageOnly: Bool) -> Void
    
    // NOUVEAU: Cache pour éviter les recalculs constants
    @State private var cachedDistance: String = "km ?"
    @State private var lastCalculationTime: Date = Date.distantPast
    
    // Calculer la distance entre les partenaires avec cache
    private var partnerDistance: String {
        // NOUVEAU: Cache - ne recalculer que toutes les 30 secondes
        let now = Date()
        if now.timeIntervalSince(lastCalculationTime) < 30 && cachedDistance != "km ?" {
            return cachedDistance
        }
        
        guard let currentUser = appState.currentUser else {
            return "km ?"
        }
        
        // NOUVEAU: Utiliser LocationService plutôt que currentUser.currentLocation
        guard let currentLocation = appState.locationService?.currentLocation else {
            return "km ?"
        }
        
        // Si pas de partenaire connecté
        guard let partnerId = currentUser.partnerId,
              !partnerId.isEmpty else {
            return "km ?"
        }
        
        // Calculer la distance avec le service
        let distance = partnerLocationService.calculateDistance(from: currentLocation)
        
        // Reformater pour avoir "km X" au lieu de "X km"
        let formattedDistance: String
        if distance == "? km" {
            formattedDistance = "km ?"
        } else if distance.hasSuffix(" km") {
            let number = distance.replacingOccurrences(of: " km", with: "")
            formattedDistance = "km \(number)"
        } else {
            formattedDistance = distance
        }
        
        return formattedDistance
    }
    
    // NOUVEAU: Fonction pour mettre à jour le cache de manière sécurisée
    private func updateCacheIfNeeded() {
        let newDistance = partnerDistance
        let now = Date()
        
        if newDistance != cachedDistance || now.timeIntervalSince(lastCalculationTime) >= 30 {
            if newDistance != cachedDistance {
                print("🌍 PartnerDistanceView: Distance mise à jour: \(newDistance)")
            }
            cachedDistance = newDistance
            lastCalculationTime = now
        }
    }
    
    // Vérifier si la localisation est manquante pour afficher le flow de permission
    private var shouldShowLocationPermissionFlow: Bool {
        // Si on affiche "km ?", alors le tutoriel doit être disponible
        return cachedDistance == "km ?"
    }
    
    // Vérifier si on doit afficher directement l'écran partenaire
    private var shouldShowPartnerLocationMessage: Bool {
        guard let currentUser = appState.currentUser else { return false }
        
        // Si on a un partenaire connecté ET notre localisation (Firebase ou service) mais qu'on voit toujours "km ?"
        if let partnerId = currentUser.partnerId, 
           !partnerId.isEmpty,
           (currentUser.currentLocation != nil || appState.locationService?.currentLocation != nil),
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
                    size: 80
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 40)
        .padding(.vertical, 16)
        .onAppear {
            updateCacheIfNeeded()
            if let partnerId = appState.currentUser?.partnerId {
                partnerLocationService.configureListener(for: partnerId)
            }
        }
        .onChange(of: appState.locationService?.currentLocation) { oldValue, newValue in
            updateCacheIfNeeded()
        }
        .onChange(of: partnerLocationService.partnerLocation) { oldValue, newValue in
            updateCacheIfNeeded()
        }
        .onChange(of: appState.currentUser?.partnerId) { oldValue, newValue in
            updateCacheIfNeeded()
        }
        .onReceive(Timer.publish(every: 30, on: .main, in: .common).autoconnect()) { _ in
            updateCacheIfNeeded()
        }
    }
}

// MARK: - User Profile Image
struct UserProfileImage: View {
    let imageURL: String?
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
                // Cercle de base seulement si pas d'image
                Circle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: size, height: size)
                
                // Image par défaut
                Image(systemName: "person.fill")
                    .font(.system(size: size * 0.4))
                    .foregroundColor(.gray)
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
                    // Cercle de base seulement si pas d'image
                    Circle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: size, height: size)
                    
                    // Icône par défaut si pas d'image
                    Image(systemName: "person.fill")
                        .font(.system(size: size * 0.4))
                        .foregroundColor(.gray)
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

 