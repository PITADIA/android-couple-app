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
    
    // Cache pour éviter les appels redondants
    private var lastFetchTime: Date = Date.distantPast
    private var lastLocationFetchTime: Date = Date.distantPast
    private let cacheValidityInterval: TimeInterval = 15 // Réduit à 15 secondes pour iOS 16.4+
    
    private init() {}
    
    deinit {
        partnerListener?.remove()
        refreshTimer?.invalidate()
    }
    
    // MARK: - Setup Partner Listener
    
    func configureListener(for partnerId: String?) {
        // Log sécurisé sans exposer le Partner ID Firebase
        print("🌍 PartnerLocationService: Configuration du listener pour partenaire: \(partnerId != nil && !partnerId!.isEmpty ? "[ID_MASQUÉ]" : "nil")")
        
        guard let partnerId = partnerId, !partnerId.isEmpty else {
            print("🌍 PartnerLocationService: Pas de partenaire - Nettoyage des données")
            resetPartnerData()
            return
        }
        
        // Éviter les appels redondants si c'est le même partenaire
        if self.partnerId == partnerId && partnerName != nil {
            print("🌍 PartnerLocationService: Même partenaire déjà configuré - Récupération localisation uniquement")
            fetchPartnerLocationViaCloudFunction(partnerId: partnerId)
            return
        }
        
        self.partnerId = partnerId
        fetchPartnerDataViaCloudFunction(partnerId: partnerId)
    }
    
    private func fetchPartnerDataViaCloudFunction(partnerId: String) {
        // Vérifier le cache pour éviter les appels trop fréquents
        let now = Date()
        if now.timeIntervalSince(lastFetchTime) < cacheValidityInterval && partnerName != nil {
            print("🌍 PartnerLocationService: Données partenaire en cache - Récupération localisation uniquement")
            fetchPartnerLocationViaCloudFunction(partnerId: partnerId)
            return
        }
        
        print("🌍 PartnerLocationService: Récupération données partenaire via Cloud Function")
        
        isLoading = true
        lastFetchTime = now
        
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
                
                // Récupérer la localisation immédiatement après pour un affichage plus rapide
                self?.fetchPartnerLocationViaCloudFunction(partnerId: partnerId)
            }
        }
    }
    
    private func fetchPartnerLocationViaCloudFunction(partnerId: String) {
        // Cache pour la localisation aussi - éviter les appels trop fréquents
        let now = Date()
        if now.timeIntervalSince(lastLocationFetchTime) < 5 { // Cache réduit à 5 secondes pour améliorer la réactivité
            print("🌍 PartnerLocationService: Localisation récemment récupérée - Attente")
            return
        }
        
        print("🌍 PartnerLocationService: Récupération localisation partenaire via Cloud Function")
        lastLocationFetchTime = now
        
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
                        // Log sécurisé sans exposer les données de localisation
                        print("✅ PartnerLocationService: Localisation partenaire récupérée")
                        self?.updatePartnerLocationFromCloudFunction(locationData)
                        print("🚀 PartnerLocationService: Localisation mise à jour - Notification des observers")
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
        // Log sécurisé sans exposer les coordonnées GPS
        print("✅ PartnerLocationService: Coordonnées partenaire configurées")
    }
    
    private func updatePartnerDataFromCloudFunction(_ partnerInfo: [String: Any]) {
        print("🌍 PartnerLocationService: Mise à jour des données partenaire depuis Cloud Function")
        // Log sécurisé sans exposer les données partenaire complètes
        print("🌍 PartnerLocationService: Données partenaire reçues")
        
        // Récupérer le nom
        partnerName = partnerInfo["name"] as? String
        
        // Récupérer l'URL de l'image de profil
        let newProfileURL = partnerInfo["profileImageURL"] as? String
        
        // Vérifier si l'URL a changé et mettre à jour le cache si nécessaire
        if let newURL = newProfileURL {
            // Log sécurisé sans exposer l'URL avec token
            print("🌍 PartnerLocationService: Photo profil partenaire trouvée")
            
            // Vérifier si l'URL a changé pour déclencher une mise à jour du cache
            if UserCacheManager.shared.hasPartnerImageChanged(newURL: newURL) {
                print("🔄 PartnerLocationService: URL partenaire changée, téléchargement en arrière-plan...")
                downloadAndCachePartnerImage(from: newURL)
            }
        } else {
            print("🌍 PartnerLocationService: Pas de photo profil pour le partenaire")
            
            // Si plus d'image, nettoyer le cache
            if UserCacheManager.shared.hasCachedPartnerImage() {
                UserCacheManager.shared.clearCachedPartnerImage()
                print("🗑️ PartnerLocationService: Cache image partenaire nettoyé (plus d'URL)")
            }
        }
        
        partnerProfileImageURL = newProfileURL
        
        // VÉRIFIER SI LOCALISATION PARTENAIRE PRÉSENTE
        if let locationData = partnerInfo["currentLocation"] as? [String: Any] {
            // Log sécurisé sans exposer les données de localisation
            print("🌍 PartnerLocationService: Localisation partenaire trouvée")
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
        
        // Reset des caches aussi
        lastFetchTime = Date.distantPast
        lastLocationFetchTime = Date.distantPast
    }
    
    // MARK: - Cache Management
    
    /// Télécharge et met en cache l'image du partenaire en arrière-plan
    private func downloadAndCachePartnerImage(from url: String) {
        Task {
            do {
                guard let imageUrl = URL(string: url) else {
                    print("❌ PartnerLocationService: URL invalide: \(url)")
                    return
                }
                
                print("🤝 PartnerLocationService: Téléchargement image partenaire: \(url)")
                let (data, _) = try await URLSession.shared.data(from: imageUrl)
                
                guard let image = UIImage(data: data) else {
                    print("❌ PartnerLocationService: Impossible de créer UIImage depuis les données")
                    return
                }
                
                // Mettre en cache l'image du partenaire
                await MainActor.run {
                    UserCacheManager.shared.cachePartnerImage(image, url: url)
                    print("✅ PartnerLocationService: Image partenaire mise en cache")
                }
                
            } catch {
                print("❌ PartnerLocationService: Erreur téléchargement image partenaire: \(error)")
            }
        }
    }
} 