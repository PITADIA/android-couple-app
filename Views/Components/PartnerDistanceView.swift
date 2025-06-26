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
        guard let currentUser = appState.currentUser else { return false }
        
        // Si pas de localisation actuelle OU pas de partenaire connecté
        if currentUser.currentLocation == nil {
            return true
        }
        
        // Si partenaire connecté mais pas de localisation partenaire
        if let partnerId = currentUser.partnerId, !partnerId.isEmpty {
            return partnerLocationService.partnerLocation == nil
        }
        
        return false
    }
    
    // Vérifier si on doit afficher directement l'écran partenaire
    private var shouldShowPartnerLocationMessage: Bool {
        guard let currentUser = appState.currentUser else { return false }
        
        // Si on a notre localisation ET un partenaire connecté mais qu'on voit toujours "km ?"
        if let partnerId = currentUser.partnerId, 
           !partnerId.isEmpty,
           currentUser.currentLocation != nil,
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
            
            Spacer()
            
            // Ligne discontinue avec distance au centre
            HStack(spacing: 8) {
                // Première partie de la ligne
                DashedLine()
                    .stroke(Color.white, style: StrokeStyle(lineWidth: 3, dash: [8, 4]))
                    .frame(height: 3)
                    .frame(maxWidth: .infinity)
                
                // Distance au centre (cliquable si localisation manquante)
                Button(action: {
                    if shouldShowLocationPermissionFlow {
                        onDistanceTap(shouldShowPartnerLocationMessage)
                    }
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
                                .fill(Color.white)
                        )
                }
                .buttonStyle(PlainButtonStyle())
                .disabled(!shouldShowLocationPermissionFlow)
                
                // Deuxième partie de la ligne
                DashedLine()
                    .stroke(Color.white, style: StrokeStyle(lineWidth: 3, dash: [8, 4]))
                    .frame(height: 3)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 8)
            
            Spacer()
            
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
        .onChange(of: appState.locationService?.currentLocation) { _ in
            updateCacheIfNeeded()
        }
        .onChange(of: partnerLocationService.partnerLocation) { _ in
            updateCacheIfNeeded()
        }
        .onChange(of: appState.currentUser?.partnerId) { _ in
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
            // Cercle de base
            Circle()
                .fill(Color(hex: "#FD267A"))
                .frame(width: size, height: size)
            
            if let imageURL = imageURL, !imageURL.isEmpty {
                // Image de profil utilisateur
                AsyncImageView(
                    imageURL: imageURL,
                    width: size - 4,
                    height: size - 4,
                    cornerRadius: (size - 4) / 2
                )
            } else {
                // Image par défaut
                Image(systemName: "person.fill")
                    .font(.system(size: size * 0.4))
                    .foregroundColor(.white)
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
            // Cercle de base
            Circle()
                .fill(hasPartner ? Color(hex: "#FD267A") : Color.white.opacity(0.3))
                .frame(width: size, height: size)
            
            if hasPartner {
                if let imageURL = imageURL, !imageURL.isEmpty {
                    // Image de profil du partenaire
                    AsyncImageView(
                        imageURL: imageURL,
                        width: size - 4,
                        height: size - 4,
                        cornerRadius: (size - 4) / 2
                    )
                } else {
                    // Icône par défaut si pas d'image
                    Image(systemName: "person.fill")
                        .font(.system(size: size * 0.4))
                        .foregroundColor(.white)
                }
            } else {
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

// MARK: - Dashed Line Shape
struct DashedLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.height / 2))
        path.addLine(to: CGPoint(x: rect.width, y: rect.height / 2))
        return path
    }
}

 