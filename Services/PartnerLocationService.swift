import Foundation
import FirebaseFirestore
import FirebaseAuth
import FirebaseFunctions
import CoreLocation
import Combine

class PartnerLocationService: ObservableObject {
    static let shared = PartnerLocationService()
    
    private let db = Firestore.firestore()
    private let functions = Functions.functions()
    
    @Published var partnerLocation: UserLocation?
    @Published var partnerProfileImageURL: String?
    @Published var partnerName: String?
    @Published var isLoading = false
    
    private var partnerListener: ListenerRegistration?
    private var cancellables = Set<AnyCancellable>()
    private var refreshTimer: Timer?
    private var partnerId: String?
    
    private init() {}
    
    deinit {
        partnerListener?.remove()
        refreshTimer?.invalidate()
    }
    
    // MARK: - Setup Partner Listener
    
    func configureListener(for partnerId: String?) {
        print("🌍 PartnerLocationService: Configuration du listener pour partenaire: \(partnerId ?? "nil")")
        
        guard let partnerId = partnerId, !partnerId.isEmpty else {
            print("🌍 PartnerLocationService: Pas de partenaire - Nettoyage des données")
            resetPartnerData()
            return
        }
        
        self.partnerId = partnerId
        fetchPartnerDataViaCloudFunction(partnerId: partnerId)
    }
    
    private func fetchPartnerDataViaCloudFunction(partnerId: String) {
        print("🌍 PartnerLocationService: Récupération données partenaire via Cloud Function")
        
        isLoading = true
        
        // Récupérer les infos de base du partenaire
        functions.httpsCallable("getPartnerInfo").call(["partnerId": partnerId]) { [weak self] result, error in
            DispatchQueue.main.async {
                self?.isLoading = false
                
                if let error = error {
                    print("❌ PartnerLocationService: Erreur Cloud Function getPartnerInfo: \(error.localizedDescription)")
                    return
                }
                
                guard let data = result?.data as? [String: Any],
                      let success = data["success"] as? Bool,
                      success,
                      let partnerInfo = data["partnerInfo"] as? [String: Any] else {
                    print("❌ PartnerLocationService: Format de réponse invalide pour getPartnerInfo")
                    return
                }
                
                self?.updatePartnerDataFromCloudFunction(partnerInfo)
                
                // Maintenant récupérer la localisation séparément
                self?.fetchPartnerLocationViaCloudFunction(partnerId: partnerId)
            }
        }
    }
    
    private func fetchPartnerLocationViaCloudFunction(partnerId: String) {
        print("🌍 PartnerLocationService: Récupération localisation partenaire via Cloud Function")
        
        functions.httpsCallable("getPartnerLocation").call(["partnerId": partnerId]) { [weak self] result, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ PartnerLocationService: Erreur Cloud Function getPartnerLocation: \(error.localizedDescription)")
                    return
                }
                
                guard let data = result?.data as? [String: Any],
                      let success = data["success"] as? Bool else {
                    print("❌ PartnerLocationService: Format de réponse invalide pour getPartnerLocation")
                    return
                }
                
                if success {
                    if let locationData = data["location"] as? [String: Any] {
                        print("✅ PartnerLocationService: Localisation partenaire récupérée: \(locationData)")
                        self?.updatePartnerLocationFromCloudFunction(locationData)
                    }
                } else {
                    let reason = data["reason"] as? String ?? "unknown"
                    print("❌ PartnerLocationService: Localisation non disponible - Raison: \(reason)")
                    self?.partnerLocation = nil
                }
            }
        }
    }
    
    private func updatePartnerLocationFromCloudFunction(_ locationData: [String: Any]) {
        print("🌍 PartnerLocationService: Mise à jour localisation depuis Cloud Function")
        
        let latitude = locationData["latitude"] as? Double ?? 0.0
        let longitude = locationData["longitude"] as? Double ?? 0.0
        let address = locationData["address"] as? String
        let city = locationData["city"] as? String
        let country = locationData["country"] as? String
        
        partnerLocation = UserLocation(
            coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            address: address,
            city: city,
            country: country
        )
        
        print("✅ PartnerLocationService: Localisation partenaire configurée: \(city ?? "ville inconnue")")
        print("✅ PartnerLocationService: Coordonnées: \(latitude), \(longitude)")
    }
    
    private func updatePartnerDataFromCloudFunction(_ partnerInfo: [String: Any]) {
        print("🌍 PartnerLocationService: Mise à jour des données partenaire depuis Cloud Function")
        print("🌍 PartnerLocationService: Données reçues: \(partnerInfo)")
        
        // Récupérer le nom
        partnerName = partnerInfo["name"] as? String
        
        // Récupérer l'URL de l'image de profil
        partnerProfileImageURL = partnerInfo["profileImageURL"] as? String
        
        if let profileURL = partnerProfileImageURL {
            print("🌍 PartnerLocationService: Photo profil partenaire: \(profileURL)")
        } else {
            print("🌍 PartnerLocationService: Pas de photo profil pour le partenaire")
        }
        
        // VÉRIFIER SI LOCALISATION PARTENAIRE PRÉSENTE
        if let locationData = partnerInfo["currentLocation"] as? [String: Any] {
            print("🌍 PartnerLocationService: Localisation partenaire trouvée: \(locationData)")
            let latitude = locationData["latitude"] as? Double ?? 0.0
            let longitude = locationData["longitude"] as? Double ?? 0.0
            let address = locationData["address"] as? String
            let city = locationData["city"] as? String
            let country = locationData["country"] as? String
            
            partnerLocation = UserLocation(
                coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                address: address,
                city: city,
                country: country
            )
            
            print("✅ PartnerLocationService: Localisation partenaire configurée: \(city ?? "ville inconnue")")
        } else {
            print("❌ PartnerLocationService: AUCUNE LOCALISATION PARTENAIRE dans les données reçues")
            partnerLocation = nil
        }
        
        // Note: La localisation n'est pas incluse dans getPartnerInfo pour des raisons de confidentialité
        // Si nécessaire, créer une Cloud Function séparée pour la localisation
        print("🌍 PartnerLocationService: Données partenaire mises à jour: \(partnerName ?? "inconnu")")
        print("🌍 PartnerLocationService: État final - Nom: \(partnerName ?? "nil"), Localisation: \(partnerLocation?.displayName ?? "nil")")
    }
    
    // MARK: - ANCIENNE MÉTHODE - gardée pour référence mais plus utilisée
    private func updatePartnerData(from data: [String: Any]) {
        print("🌍 PartnerLocationService: [DEPRECATED] Mise à jour des données partenaire")
        
        // Récupérer le nom
        partnerName = data["name"] as? String
        
        // Récupérer l'URL de l'image de profil
        partnerProfileImageURL = data["profileImageURL"] as? String
        
        // Récupérer la localisation
        if let locationData = data["currentLocation"] as? [String: Any] {
            let latitude = locationData["latitude"] as? Double ?? 0.0
            let longitude = locationData["longitude"] as? Double ?? 0.0
            let address = locationData["address"] as? String
            let city = locationData["city"] as? String
            let country = locationData["country"] as? String
            
            partnerLocation = UserLocation(
                coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                address: address,
                city: city,
                country: country
            )
            
            print("🌍 PartnerLocationService: Localisation partenaire mise à jour: \(city ?? "inconnue")")
        } else {
            partnerLocation = nil
            print("🌍 PartnerLocationService: Pas de localisation pour le partenaire")
        }
    }
    
    // MARK: - Distance Calculation
    
    func calculateDistance(from userLocation: UserLocation) -> String {
        guard let partnerLocation = partnerLocation else {
            return "? km".convertedForLocale()
        }
        
        let distance = userLocation.distance(to: partnerLocation)
        
        // ✅ NOUVEAU : Si distance < 1 km → afficher "ensemble / together"
        if distance < 1 {
            return "widget_together_text".localized.capitalized
        }
        
        let baseDistance: String
        if distance < 10 {
            // Moins de 10 km, afficher avec 1 décimale
            baseDistance = String(format: "%.1f km", distance)
        } else {
            // Plus de 10 km, afficher en entier
            baseDistance = "\(Int(distance)) km"
        }
        
        // Appliquer la conversion selon la locale
        return baseDistance.convertedForLocale()
    }
    
    // MARK: - Clear Partner Data
    
    func clearPartnerData() {
        print("🌍 PartnerLocationService: Nettoyage des données partenaire")
        partnerListener?.remove()
        refreshTimer?.invalidate()
        partnerListener = nil
        refreshTimer = nil
        partnerLocation = nil
        partnerProfileImageURL = nil
        partnerName = nil
        isLoading = false
    }
    
    // NOUVEAU: Méthode pour nettoyer les données partenaire
    private func resetPartnerData() {
        partnerName = nil
        partnerLocation = nil
        partnerProfileImageURL = nil
        partnerId = nil
        isLoading = false
    }
} 