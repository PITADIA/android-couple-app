import Foundation
import CoreLocation
import Combine
import FirebaseAuth

class LocationService: NSObject, ObservableObject {
    static let shared = LocationService()
    
    private let locationManager = CLLocationManager()
    private let firebaseService = FirebaseService.shared
    private var cancellables = Set<AnyCancellable>()
    private var updateTimer: Timer?
    
    @Published var currentLocation: UserLocation?
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var isUpdatingLocation = false
    
    override init() {
        super.init()
        setupLocationManager()
        setupAuthObserver()
    }
    
    deinit {
        updateTimer?.invalidate()
    }
    
    // MARK: - Setup
    
    private func setupLocationManager() {
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters // Précision moins gourmande
        locationManager.distanceFilter = 500 // Mise à jour tous les 500 mètres
        authorizationStatus = locationManager.authorizationStatus
        
        print("📍 LocationService: Service initialisé - Statut: \(statusDescription(authorizationStatus))")
    }
    
    private func setupAuthObserver() {
        // Observer les changements d'authentification
        firebaseService.$currentUser
            .receive(on: DispatchQueue.main)
            .sink { [weak self] user in
                if user != nil {
                    self?.startLocationUpdatesIfAuthorized()
                } else {
                    self?.stopLocationUpdates()
                }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public Methods
    
    func requestLocationPermission() {
        print("📍 LocationService: Demande de permission de localisation")
        locationManager.requestWhenInUseAuthorization()
    }
    
    func startLocationUpdatesIfAuthorized() {
        guard authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways else {
            print("📍 LocationService: Permission non accordée - Statut: \(statusDescription(authorizationStatus))")
            return
        }
        
        guard firebaseService.currentUser != nil else {
            print("📍 LocationService: Aucun utilisateur connecté - Arrêt des mises à jour")
            return
        }
        
        print("📍 LocationService: Démarrage des mises à jour de localisation")
        locationManager.startUpdatingLocation()
        
        // Timer pour mise à jour périodique (toutes les 30 minutes)
        updateTimer = Timer.scheduledTimer(withTimeInterval: 1800, repeats: true) { [weak self] _ in
            self?.requestLocationUpdate()
        }
    }
    
    func stopLocationUpdates() {
        print("📍 LocationService: Arrêt des mises à jour de localisation")
        locationManager.stopUpdatingLocation()
        updateTimer?.invalidate()
        updateTimer = nil
    }
    
    private func requestLocationUpdate() {
        guard authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways else {
            return
        }
        
        print("📍 LocationService: Demande de mise à jour manuelle de localisation")
        locationManager.requestLocation()
    }
    
    // MARK: - Firebase Update
    
    private func saveLocationToFirebase(_ location: UserLocation) {
        guard firebaseService.currentUser != nil else {
            print("❌ LocationService: Aucun utilisateur connecté pour sauvegarder la localisation")
            return
        }
        
        // NOUVEAU: Éviter la sauvegarde si la localisation n'a pas significativement changé
        if let lastLocation = currentLocation {
            let distance = lastLocation.distance(to: location)
            if distance < 0.1 { // Moins de 100 mètres de différence
                print("📍 LocationService: Localisation similaire ignorée (distance: \(Int(distance * 1000))m)")
                return
            }
        }
        
        isUpdatingLocation = true
        print("📍 LocationService: Sauvegarde nouvelle localisation en Firebase: \(location.displayName)")
        
        firebaseService.updateUserLocation(location) { [weak self] success in
            DispatchQueue.main.async {
                self?.isUpdatingLocation = false
                if success {
                    print("✅ LocationService: Localisation sauvegardée avec succès")
                    self?.currentLocation = location
                } else {
                    print("❌ LocationService: Échec de la sauvegarde de localisation")
                }
            }
        }
    }
}

// MARK: - CLLocationManagerDelegate

extension LocationService: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        DispatchQueue.main.async {
            print("📍 LocationService: Changement d'autorisation: \(self.statusDescription(status))")
            self.authorizationStatus = status
            
            switch status {
            case .authorizedWhenInUse, .authorizedAlways:
                self.startLocationUpdatesIfAuthorized()
            case .denied, .restricted:
                self.stopLocationUpdates()
            case .notDetermined:
                break
            @unknown default:
                break
            }
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        print("📍 LocationService: Nouvelle localisation reçue: \(location.coordinate.latitude), \(location.coordinate.longitude)")
        
        // Géocodage inverse pour obtenir l'adresse
        let geocoder = CLGeocoder()
        geocoder.reverseGeocodeLocation(location) { [weak self] placemarks, error in
            DispatchQueue.main.async {
                let userLocation: UserLocation
                
                if let placemark = placemarks?.first {
                    userLocation = UserLocation(
                        coordinate: location.coordinate,
                        address: [placemark.thoroughfare, placemark.subThoroughfare]
                            .compactMap { $0 }
                            .joined(separator: " "),
                        city: placemark.locality,
                        country: placemark.country
                    )
                    print("📍 LocationService: Adresse résolue: \(userLocation.displayName)")
                } else {
                    userLocation = UserLocation(coordinate: location.coordinate)
                    print("📍 LocationService: Adresse non résolue - Coordonnées uniquement")
                }
                
                self?.saveLocationToFirebase(userLocation)
            }
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("❌ LocationService: Erreur de localisation: \(error.localizedDescription)")
        isUpdatingLocation = false
    }
}

// MARK: - Helper Methods

extension LocationService {
    private func statusDescription(_ status: CLAuthorizationStatus) -> String {
        switch status {
        case .notDetermined:
            return "Non déterminé"
        case .restricted:
            return NSLocalizedString("restricted", comment: "Restricted location status")
        case .denied:
            return "Refusé"
        case .authorizedAlways:
            return NSLocalizedString("always_authorized", comment: "Always authorized location status")
        case .authorizedWhenInUse:
            return NSLocalizedString("when_in_use_authorized", comment: "When in use authorized location status")
        @unknown default:
            return NSLocalizedString("unknown", comment: "Unknown location status")
        }
    }
} 