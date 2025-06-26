import SwiftUI
import CoreLocation

struct LocationPermissionFlow: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var locationManager = PermissionLocationManager()
    @State private var currentStep = 0
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à l'app avec dégradé rose doux en arrière-plan
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea(.all)
                
                // Dégradé rose très doux en arrière-plan
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FD267A").opacity(0.03),
                        Color(hex: "#FF655B").opacity(0.02)
                    ]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                VStack {                    
                    Spacer()
                    
                    // Contenu selon l'étape
                    Group {
                        switch currentStep {
                        case 0:
                            LocationServiceExplanationView {
                                currentStep = 1
                            }
                        case 1:
                            LocationPermissionView {
                                currentStep = 2
                            }
                        case 2:
                            LocationPartnerExplanationView {
                                dismiss()
                            }
                        default:
                            EmptyView()
                        }
                    }
                    
                    Spacer()
                }
            }
        }
        .navigationBarHidden(true)
    }
}

// MARK: - Étape 1: Explication du service de localisation
struct LocationServiceExplanationView: View {
    let onContinue: () -> Void
    
    var body: some View {
        VStack(spacing: 30) {
            VStack(spacing: 16) {
                Text("Service de localisation")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.black)
                
                Text("Les services de localisation sont nécessaires pour que l'appli puisse calculer la distance entre toi et ton partenaire.")
                    .font(.system(size: 16))
                    .foregroundColor(.black.opacity(0.8))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
            }
            .padding(.top, 60)
            
            Spacer().frame(height: 50)
            
            // Carte avec service de localisation - Style sophistiqué
            HStack(spacing: 16) {
                Image(systemName: "hand.raised.fill")
                    .font(.system(size: 24))
                    .foregroundColor(.white)
                    .frame(width: 50, height: 50)
                    .background(Color.blue)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                
                Text("Service de localisation")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.black)
                
                Spacer()
                
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 24))
                    .foregroundColor(.green)
            }
            .padding(20)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.white)
                    .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                    .shadow(color: Color.black.opacity(0.04), radius: 1, x: 0, y: 1)
            )
            .padding(.horizontal, 20)
            
            Spacer()
            
            // Bouton Continuer
            Button(action: onContinue) {
                Text("Continuer")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(hex: "#FD267A"))
                    .clipShape(RoundedRectangle(cornerRadius: 28))
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 40)
        }
    }
}

// MARK: - Étape 2: Demande de permission
struct LocationPermissionView: View {
    let onContinue: () -> Void
    @StateObject private var locationManager = PermissionLocationManager()
    
    private var buttonText: String {
        switch locationManager.authorizationStatus {
        case .notDetermined:
            return "Autoriser"
        case .denied, .restricted:
            return "Ouvrir les Réglages"
        case .authorizedWhenInUse, .authorizedAlways:
            return "Continuer"
        @unknown default:
            return "Autoriser"
        }
    }
    
    private var buttonColor: Color {
        switch locationManager.authorizationStatus {
        case .denied, .restricted:
            return Color.orange
        default:
            return Color(hex: "#FD267A")
        }
    }
    
    private var statusIcon: String {
        switch locationManager.authorizationStatus {
        case .notDetermined:
            return "questionmark.circle.fill"
        case .denied, .restricted:
            return "xmark.circle.fill"
        case .authorizedWhenInUse, .authorizedAlways:
            return "checkmark.circle.fill"
        @unknown default:
            return "questionmark.circle.fill"
        }
    }
    
    private var statusColor: Color {
        switch locationManager.authorizationStatus {
        case .notDetermined:
            return .orange
        case .denied, .restricted:
            return .red
        case .authorizedWhenInUse, .authorizedAlways:
            return .green
        @unknown default:
            return .gray
        }
    }
    
    var body: some View {
        VStack(spacing: 30) {
            VStack(spacing: 16) {
                Text("Permission")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.black)
                
                Text("Les services de localisation sont nécessaires pour que l'appli puisse calculer la distance entre toi et ton partenaire.")
                    .font(.system(size: 16))
                    .foregroundColor(.black.opacity(0.8))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
            }
            .padding(.top, 60)
            
            Spacer().frame(height: 50)
            
            // Carte avec statut de localisation - Style sophistiqué
            HStack(spacing: 16) {
                Image(systemName: "location.fill")
                    .font(.system(size: 24))
                    .foregroundColor(.white)
                    .frame(width: 50, height: 50)
                    .background(Color.blue)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                
                Text("Autorisations d'accéder à la position")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.black)
                
                Spacer()
                
                Image(systemName: statusIcon)
                    .font(.system(size: 24))
                    .foregroundColor(statusColor)
            }
            .padding(20)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.white)
                    .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                    .shadow(color: Color.black.opacity(0.04), radius: 1, x: 0, y: 1)
            )
            .padding(.horizontal, 20)
            
            Spacer().frame(height: 40)
            
            // Instructions
            VStack(alignment: .leading, spacing: 12) {
                Text("Comment obtenir la permission")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.black)
                
                HStack(spacing: 12) {
                    Image(systemName: "hand.tap.fill")
                        .font(.system(size: 18))
                        .foregroundColor(.orange)
                        .frame(width: 30, height: 30)
                        .background(Color.orange.opacity(0.1))
                        .clipShape(Circle())
                    
                    Text("Appuyer sur Autoriser")
                        .font(.system(size: 16))
                        .foregroundColor(.black)
                }
                
                HStack(spacing: 12) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundColor(.green)
                        .frame(width: 30, height: 30)
                        .background(Color.green.opacity(0.1))
                        .clipShape(Circle())
                    
                    Text("Sélectionner Autoriser lorsque l'app est active")
                        .font(.system(size: 16))
                        .foregroundColor(.black)
                }
            }
            .padding(.horizontal, 20)
            
            Spacer()
            
            // Bouton Autoriser
            Button(action: {
                locationManager.requestLocationPermission()
                
                // Attendre un peu pour voir si la permission change
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    // Si la permission est toujours pas déterminée après la demande,
                    // on continue quand même (cas où l'utilisateur ferme la popup sans répondre)
                    if locationManager.authorizationStatus == .authorizedWhenInUse ||
                       locationManager.authorizationStatus == .authorizedAlways ||
                       locationManager.hasRequestedPermission {
                        onContinue()
                    }
                }
            }) {
                Text(buttonText)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(buttonColor)
                    .clipShape(RoundedRectangle(cornerRadius: 28))
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 40)
        }
    }
}

// MARK: - Étape 3: Explication pour le partenaire
struct LocationPartnerExplanationView: View {
    let onContinue: () -> Void
    
    var body: some View {
        VStack(spacing: 30) {
            VStack(spacing: 16) {
                Text("Au tour de ton partenaire")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.black)
                
                Text("Demande à ton partenaire de faire la même démarche et d'ajouter le widget sur son écran, sinon ton widget de distance ne fonctionnera pas correctement.")
                    .font(.system(size: 16))
                    .foregroundColor(.black.opacity(0.8))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
            }
            .padding(.top, 60)
            
            Spacer().frame(height: 50)
            
            // Widget preview de distance avec le vrai MapDistancePreviewWidget
            VStack(spacing: 24) {
                // Titre du widget
                Text("Widget Distance")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.black)
                
                // Utilisation du vrai composant MapDistancePreviewWidget avec données simulées
                MapDistancePreviewWidget(
                    distanceInfo: createSampleDistanceInfo(),
                    canAccess: true
                )
                .frame(width: 280, height: 172)
            }
            
            Spacer()
            
            // Bouton Continuer
            Button(action: onContinue) {
                Text("Continuer")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(hex: "#FD267A"))
                    .clipShape(RoundedRectangle(cornerRadius: 28))
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 40)
        }
    }
    
    // Créer des données d'exemple pour le widget preview
    private func createSampleDistanceInfo() -> DistanceInfo {
        let userCoordinate = CLLocationCoordinate2D(latitude: 48.8566, longitude: 2.3522)
        let userLocation = UserLocation(coordinate: userCoordinate)
        
        let partnerCoordinate = CLLocationCoordinate2D(latitude: 43.6047, longitude: 1.4442)
        let partnerLocation = UserLocation(coordinate: partnerCoordinate)
        
        return DistanceInfo(
            distance: 2.5, // 2.5 km comme exemple
            currentUserLocation: userLocation,
            partnerLocation: partnerLocation,
            messages: ["💕 Je pense à toi"],
            lastUpdated: Date()
        )
    }
}

// MARK: - Permission Location Manager
class PermissionLocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let locationManager = CLLocationManager()
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var hasRequestedPermission = false
    
    override init() {
        super.init()
        locationManager.delegate = self
        authorizationStatus = locationManager.authorizationStatus
        print("📍 PermissionLocationManager: Statut initial: \(authorizationStatus.description)")
    }
    
    func requestLocationPermission() {
        print("📍 PermissionLocationManager: Demande de permission - Statut actuel: \(authorizationStatus.description)")
        
        switch authorizationStatus {
        case .notDetermined:
            print("📍 PermissionLocationManager: Première demande de permission")
            hasRequestedPermission = true
            locationManager.requestWhenInUseAuthorization()
            
        case .denied, .restricted:
            print("📍 PermissionLocationManager: Permission refusée/restreinte - Redirection vers Réglages")
            openSettings()
            
        case .authorizedWhenInUse, .authorizedAlways:
            print("📍 PermissionLocationManager: Permission déjà accordée")
            
        @unknown default:
            print("📍 PermissionLocationManager: Statut inconnu")
            locationManager.requestWhenInUseAuthorization()
        }
    }
    
    private func openSettings() {
        guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else { return }
        
        DispatchQueue.main.async {
            if UIApplication.shared.canOpenURL(settingsUrl) {
                UIApplication.shared.open(settingsUrl)
            }
        }
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        DispatchQueue.main.async {
            print("📍 PermissionLocationManager: Changement d'autorisation: \(manager.authorizationStatus.description)")
            self.authorizationStatus = manager.authorizationStatus
        }
    }
}

// MARK: - Extension pour les descriptions
extension CLAuthorizationStatus {
    var description: String {
        switch self {
        case .notDetermined:
            return "Non déterminé"
        case .restricted:
            return "Restreint"
        case .denied:
            return "Refusé"
        case .authorizedAlways:
            return "Autorisé toujours"
        case .authorizedWhenInUse:
            return "Autorisé en utilisation"
        @unknown default:
            return "Inconnu"
        }
    }
}

#Preview {
    LocationPermissionFlow()
} 