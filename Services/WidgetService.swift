import Foundation
import Combine
import CoreLocation
import UIKit
import FirebaseFunctions

class WidgetService: ObservableObject {
    @Published var relationshipStats: RelationshipStats?
    @Published var distanceInfo: DistanceInfo?
    @Published var isLocationUpdateInProgress = false
    
    private let firebaseService = FirebaseService.shared
    private var cancellables = Set<AnyCancellable>()
    private var currentUser: AppUser?
    private var partnerUser: AppUser?
    
    // App Group pour partager avec le widget
    private let sharedDefaults = UserDefaults(suiteName: "group.com.lyes.love2love")
    
    init() {
        setupObservers()
    }
    
    private func setupObservers() {
        // Observer les changements d'utilisateur
        firebaseService.$currentUser
            .receive(on: DispatchQueue.main)
            .sink { [weak self] user in
                self?.currentUser = user
                self?.updateRelationshipStats()
                self?.fetchPartnerInfo()
            }
            .store(in: &cancellables)
            
        // NOUVEAU: Observer les changements de localisation de l'utilisateur
        LocationService.shared.$currentLocation
            .receive(on: DispatchQueue.main)
            .sink { [weak self] location in
                // Mettre à jour la localisation de l'utilisateur actuel si disponible
                if var currentUser = self?.currentUser {
                    currentUser.currentLocation = location
                    self?.currentUser = currentUser
                    print("🔄 WidgetService: Localisation utilisateur mise à jour: \(location?.displayName ?? "nil")")
                    // Recalculer la distance
                    self?.updateDistanceInfo()
                }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Relationship Stats
    
    private func updateRelationshipStats() {
        print("🔄 WidgetService: updateRelationshipStats appelé")
        
        guard let user = currentUser else {
            print("❌ WidgetService: Pas d'utilisateur pour les stats de relation")
            relationshipStats = nil
            saveWidgetData()
            return
        }
        
        guard let startDate = user.relationshipStartDate else {
            print("❌ WidgetService: Pas de date de début de relation")
            print("🔍 WidgetService: relationshipStartDate = \(user.relationshipStartDate?.description ?? "nil")")
            relationshipStats = nil
            saveWidgetData()
            return
        }
        
        print("✅ WidgetService: Date de début de relation trouvée: \(startDate)")
        
        let calendar = Calendar.current
        let now = Date()
        
        // Calculer les jours ensemble
        let dayComponents = calendar.dateComponents([.day], from: startDate, to: now)
        let daysTogether = max(dayComponents.day ?? 0, 0)
        
        // Calculer les années, mois, jours
        let components = calendar.dateComponents([.year, .month, .day], from: startDate, to: now)
        let years = components.year ?? 0
        let months = components.month ?? 0
        let days = components.day ?? 0
        
        // Calculer le temps restant jusqu'au prochain anniversaire
        var nextAnniversary = calendar.dateComponents([.month, .day], from: startDate)
        nextAnniversary.year = calendar.component(.year, from: now)
        
        if let nextAnniversaryDate = calendar.date(from: nextAnniversary),
           nextAnniversaryDate < now {
            nextAnniversary.year = (nextAnniversary.year ?? 0) + 1
        }
        
        let nextAnniversaryDate = calendar.date(from: nextAnniversary) ?? now
        let timeToAnniversary = calendar.dateComponents([.day, .hour, .minute, .second], 
                                                      from: now, to: nextAnniversaryDate)
        
        relationshipStats = RelationshipStats(
            startDate: startDate,
            daysTotal: daysTogether,
            years: years,
            months: months,
            days: days,
            nextAnniversary: nextAnniversaryDate,
            daysToAnniversary: timeToAnniversary.day ?? 0,
            hoursToAnniversary: timeToAnniversary.hour ?? 0,
            minutesToAnniversary: timeToAnniversary.minute ?? 0,
            secondsToAnniversary: timeToAnniversary.second ?? 0
        )
        
        print("✅ WidgetService: Statistiques de relation calculées:")
        print("✅ WidgetService: - Jours ensemble: \(daysTogether)")
        print("✅ WidgetService: - Durée formatée: \(relationshipStats?.formattedDuration ?? "?")")
        print("✅ WidgetService: - Jours jusqu'anniversaire: \(relationshipStats?.daysToAnniversary ?? 0)")
        
        // Sauvegarder pour le widget
        saveWidgetData()
    }
    
    // MARK: - Partner Info & Distance
    
    private func fetchPartnerInfo() {
        guard let user = currentUser,
              let partnerId = user.partnerId,
              !partnerId.isEmpty else {
            print("🔄 WidgetService: Pas de partenaire connecté - Nettoyage des données")
            partnerUser = nil
            distanceInfo = nil
            saveWidgetData()
            return
        }
        
        // CORRECTION: Utiliser la Cloud Function pour récupérer les infos du partenaire
        print("🔄 WidgetService: Récupération infos partenaire via Cloud Function: \(partnerId)")
        
        let functions = Functions.functions()
        functions.httpsCallable("getPartnerInfo").call(["partnerId": partnerId]) { [weak self] result, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ WidgetService: Erreur Cloud Function getPartnerInfo: \(error.localizedDescription)")
                    self?.partnerUser = nil
                    self?.distanceInfo = nil
                    self?.saveWidgetData()
                    return
                }
                
                guard let data = result?.data as? [String: Any],
                      let success = data["success"] as? Bool,
                      success,
                      let partnerInfo = data["partnerInfo"] as? [String: Any] else {
                    print("❌ WidgetService: Format de réponse Cloud Function invalide")
                    self?.partnerUser = nil
                    self?.distanceInfo = nil
                    self?.saveWidgetData()
                    return
                }
                
                // Créer un AppUser minimal avec les données du partenaire (sans localisation pour l'instant)
                let partnerUser = AppUser(
                    id: partnerId,
                    name: partnerInfo["name"] as? String ?? "Partenaire",
                    birthDate: Date(), // Date par défaut
                    relationshipGoals: [],
                    relationshipDuration: .notInRelationship,
                    relationshipImprovement: nil,
                    questionMode: nil,
                    partnerCode: nil,
                    partnerId: nil,
                    partnerConnectedAt: nil,
                    subscriptionInheritedFrom: partnerInfo["subscriptionSharedFrom"] as? String,
                    subscriptionInheritedAt: nil,
                    connectedPartnerCode: nil,
                    connectedPartnerId: nil,
                    connectedAt: nil,
                    isSubscribed: partnerInfo["isSubscribed"] as? Bool ?? false,
                    onboardingInProgress: false,
                    relationshipStartDate: nil,
                    profileImageURL: partnerInfo["profileImageURL"] as? String,
                    currentLocation: nil // Sera rempli par fetchPartnerLocation
                )
                
                print("✅ WidgetService: Données partenaire récupérées via Cloud Function: \(partnerUser.name)")
                if let profileURL = partnerUser.profileImageURL {
                    print("✅ WidgetService: Photo de profil partenaire trouvée: \(profileURL)")
                } else {
                    print("❌ WidgetService: Aucune photo de profil pour le partenaire")
                }
                
                self?.partnerUser = partnerUser
                
                // NOUVEAU: Récupérer aussi la localisation du partenaire
                self?.fetchPartnerLocation(partnerId: partnerId)
            }
        }
    }
    
    // NOUVEAU: Récupérer la localisation du partenaire via Cloud Function
    private func fetchPartnerLocation(partnerId: String) {
        print("🌍 WidgetService: Récupération localisation partenaire via Cloud Function: \(partnerId)")
        
        let functions = Functions.functions()
        functions.httpsCallable("getPartnerLocation").call(["partnerId": partnerId]) { [weak self] result, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ WidgetService: Erreur Cloud Function getPartnerLocation: \(error.localizedDescription)")
                    // Continuer sans localisation - on garde les autres données du partenaire
                    self?.updateDistanceInfo()
                    return
                }
                
                guard let data = result?.data as? [String: Any],
                      let success = data["success"] as? Bool,
                      success,
                      let locationData = data["location"] as? [String: Any] else {
                    print("❌ WidgetService: Pas de localisation partenaire disponible")
                    // Continuer sans localisation
                    self?.updateDistanceInfo()
                    return
                }
                
                // Parser les données de localisation
                let latitude = locationData["latitude"] as? Double ?? 0.0
                let longitude = locationData["longitude"] as? Double ?? 0.0
                let address = locationData["address"] as? String
                let city = locationData["city"] as? String
                let country = locationData["country"] as? String
                
                let partnerLocation = UserLocation(
                    coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                    address: address,
                    city: city,
                    country: country
                )
                
                print("✅ WidgetService: Localisation partenaire récupérée: \(city ?? "inconnue")")
                print("✅ WidgetService: Coordonnées: \(latitude), \(longitude)")
                
                // Mettre à jour le partnerUser avec la localisation
                if var currentPartnerUser = self?.partnerUser {
                    currentPartnerUser.currentLocation = partnerLocation
                    self?.partnerUser = currentPartnerUser
                }
                
                // Maintenant calculer la distance
                self?.updateDistanceInfo()
            }
        }
    }
    
    private func updateDistanceInfo() {
        print("🔄 WidgetService: updateDistanceInfo appelé")
        
        guard let currentUser = currentUser else {
            print("❌ WidgetService: Pas d'utilisateur actuel")
            distanceInfo = nil
            saveWidgetData()
            return
        }
        
        guard let partnerUser = partnerUser else {
            print("❌ WidgetService: Pas de données partenaire")
            distanceInfo = nil
            saveWidgetData()
            return
        }
        
        guard let currentLocation = currentUser.currentLocation else {
            print("❌ WidgetService: Pas de localisation utilisateur")
            distanceInfo = nil
            saveWidgetData()
            return
        }
        
        guard let partnerLocation = partnerUser.currentLocation else {
            print("❌ WidgetService: Pas de localisation partenaire")
            distanceInfo = nil
            saveWidgetData()
            return
        }
        
        let distance = currentLocation.distance(to: partnerLocation)
        
        // Générer des messages basés sur la distance
        let messages = generateDistanceMessages(distance: distance)
        
        distanceInfo = DistanceInfo(
            distance: distance,
            currentUserLocation: currentLocation,
            partnerLocation: partnerLocation,
            messages: messages,
            lastUpdated: min(currentLocation.lastUpdated, partnerLocation.lastUpdated)
        )
        
        print("✅ WidgetService: Distance calculée: \(distanceInfo?.formattedDistance ?? "?")")
        print("✅ WidgetService: Utilisateur: \(currentLocation.displayName)")
        print("✅ WidgetService: Partenaire: \(partnerLocation.displayName)")
        
        // Sauvegarder pour le widget
        saveWidgetData()
    }
    
    private func generateDistanceMessages(distance: Double) -> [String] {
        if distance < 0.1 { // Moins de 100m
            return ["🥰 Tu me manques", "💕 Je pense à toi"]
        } else if distance < 1 { // Moins de 1km
            return ["😘 Tu me manques", "💖 Je pense à toi"]
        } else if distance < 10 { // Moins de 10km
            return ["🤗 Tu me manques", "💝 Je pense à toi"]
        } else if distance < 100 { // Moins de 100km
            return ["😍 Tu me manques", "💓 Je pense à toi"]
        } else if distance < 500 { // Moins de 500km
            return ["🥺 Tu me manques", "💞 Je pense à toi"]
        } else { // Plus de 500km
            return ["😢 Tu me manques tellement", "💔 J'ai hâte de te revoir"]
        }
    }
    
    // MARK: - Location Updates
    
    func updateCurrentLocation(_ location: UserLocation) {
        guard var user = currentUser else { return }
        
        isLocationUpdateInProgress = true
        user.currentLocation = location
        
        // Sauvegarder dans Firebase
        firebaseService.updateUserLocation(location) { [weak self] success in
            DispatchQueue.main.async {
                self?.isLocationUpdateInProgress = false
                if success {
                    self?.currentUser = user
                    self?.updateDistanceInfo()
                }
            }
        }
    }
    
    // MARK: - Public Methods
    
    func refreshData() {
        updateRelationshipStats()
        fetchPartnerInfo()
    }
    
    func startLocationUpdates() {
        // Cette méthode peut être appelée pour démarrer les mises à jour de localisation en arrière-plan
        // Pour l'instant, nous nous contentons de rafraîchir les données
        refreshData()
    }
    
    // NOUVEAU: Forcer le téléchargement immédiat des images de profil
    func forceRefreshProfileImages() {
        guard let sharedDefaults = sharedDefaults else {
            print("❌ WidgetService: Impossible d'accéder aux UserDefaults partagés")
            return
        }
        
        print("🔄 WidgetService: Actualisation forcée des images de profil...")
        
        // Télécharger l'image utilisateur si disponible
        if let currentUser = currentUser, let imageURL = currentUser.profileImageURL {
            print("🔄 WidgetService: Téléchargement image utilisateur: \(imageURL)")
            downloadAndCacheImage(from: imageURL, key: "widget_user_image_url", isUser: true)
        }
        
        // Télécharger l'image partenaire si disponible
        if let partnerUser = partnerUser, let imageURL = partnerUser.profileImageURL {
            print("🔄 WidgetService: Téléchargement image partenaire: \(imageURL)")
            downloadAndCacheImage(from: imageURL, key: "widget_partner_image_url", isUser: false)
        }
    }
    
    // MARK: - Widget Data Sharing
    
    private func saveWidgetData() {
        guard let sharedDefaults = sharedDefaults else {
            print("❌ WidgetService: Impossible d'accéder aux UserDefaults partagés")
            return
        }
        
        // Sauvegarder les statistiques de relation
        if let stats = relationshipStats {
            sharedDefaults.set(stats.daysTotal, forKey: "widget_days_total")
            sharedDefaults.set(stats.formattedDuration, forKey: "widget_duration")
            sharedDefaults.set(stats.daysToAnniversary, forKey: "widget_days_to_anniversary")
            sharedDefaults.set(stats.startDate.timeIntervalSince1970, forKey: "widget_start_date")
        } else {
            // Nettoyer si pas de données
            sharedDefaults.removeObject(forKey: "widget_days_total")
            sharedDefaults.removeObject(forKey: "widget_duration")
            sharedDefaults.removeObject(forKey: "widget_days_to_anniversary")
            sharedDefaults.removeObject(forKey: "widget_start_date")
        }
        
        // Sauvegarder les infos de distance
        if let distance = distanceInfo {
            sharedDefaults.set(distance.formattedDistance, forKey: "widget_distance")
            sharedDefaults.set(distance.randomMessage, forKey: "widget_message")
        } else {
            sharedDefaults.removeObject(forKey: "widget_distance")
            sharedDefaults.removeObject(forKey: "widget_message")
        }
        
        // Sauvegarder les noms d'utilisateurs si disponibles
        if let currentUser = currentUser {
            sharedDefaults.set(currentUser.name, forKey: "widget_user_name")
            
            // AMÉLIORÉ: Télécharger et cacher l'image utilisateur localement
            if let imageURL = currentUser.profileImageURL {
                downloadAndCacheImage(from: imageURL, key: "widget_user_image_url", isUser: true)
            } else {
                sharedDefaults.removeObject(forKey: "widget_user_image_url")
                clearCachedImage(key: "user_profile_image.jpg")
            }
            
            // Sauvegarder les coordonnées utilisateur
            if let location = currentUser.currentLocation {
                sharedDefaults.set(location.latitude, forKey: "widget_user_latitude")
                sharedDefaults.set(location.longitude, forKey: "widget_user_longitude")
            } else {
                sharedDefaults.removeObject(forKey: "widget_user_latitude")
                sharedDefaults.removeObject(forKey: "widget_user_longitude")
            }
        }
        
        if let partnerUser = partnerUser {
            sharedDefaults.set(partnerUser.name, forKey: "widget_partner_name")
            
            // AMÉLIORÉ: Télécharger et cacher l'image partenaire localement
            if let imageURL = partnerUser.profileImageURL {
                downloadAndCacheImage(from: imageURL, key: "widget_partner_image_url", isUser: false)
            } else {
                sharedDefaults.removeObject(forKey: "widget_partner_image_url")
                clearCachedImage(key: "partner_profile_image.jpg")
            }
            
            // Sauvegarder les coordonnées partenaire
            if let location = partnerUser.currentLocation {
                sharedDefaults.set(location.latitude, forKey: "widget_partner_latitude")
                sharedDefaults.set(location.longitude, forKey: "widget_partner_longitude")
            } else {
                sharedDefaults.removeObject(forKey: "widget_partner_latitude")
                sharedDefaults.removeObject(forKey: "widget_partner_longitude")
            }
        }
        
        // Timestamp de dernière mise à jour
        sharedDefaults.set(Date().timeIntervalSince1970, forKey: "widget_last_update")
        
        print("✅ WidgetService: Données sauvegardées pour le widget")
    }
    
    // MARK: - Image Caching pour Widgets
    
    private func downloadAndCacheImage(from urlString: String, key: String, isUser: Bool) {
        // Nom du fichier pour le cache local
        let fileName = isUser ? "user_profile_image.jpg" : "partner_profile_image.jpg"
        
        // 1. Vérifier d'abord si l'image est déjà en cache
        if let cachedImage = ImageCacheService.shared.getCachedImage(for: urlString) {
            print("✅ WidgetService: Image trouvée en cache - Sauvegarde pour widget...")
            // Redimensionner et sauvegarder pour le widget
            let resizedImage = resizeImage(cachedImage, to: CGSize(width: 150, height: 150))
            if let finalImage = resizedImage {
                ImageCacheService.shared.cacheImageForWidget(finalImage, fileName: fileName)
                sharedDefaults?.set(fileName, forKey: key) // Juste le nom du fichier
            }
            return
        }
        
        // 2. Si pas en cache, télécharger
        guard let url = URL(string: urlString) else {
            print("❌ WidgetService: URL invalide: \(urlString)")
            return
        }
        
        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            guard let self = self,
                  let data = data,
                  error == nil,
                  let image = UIImage(data: data) else {
                print("❌ WidgetService: Erreur téléchargement image: \(error?.localizedDescription ?? "Inconnue")")
                return
            }
            
            // 3. Mettre en cache principal
            ImageCacheService.shared.cacheImage(image, for: urlString)
            
            // 4. Redimensionner pour le widget et sauvegarder
            let resizedImage = self.resizeImage(image, to: CGSize(width: 150, height: 150))
            guard let finalImage = resizedImage else {
                print("❌ WidgetService: Erreur redimensionnement image")
                return
            }
            
            // 5. Sauvegarder pour le widget
            ImageCacheService.shared.cacheImageForWidget(finalImage, fileName: fileName)
            
            DispatchQueue.main.async {
                self.sharedDefaults?.set(fileName, forKey: key) // Juste le nom du fichier
                print("✅ WidgetService: Image cachée localement: \(fileName)")
            }
        }.resume()
    }
    
    private func clearCachedImage(key: String) {
        guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.lyes.love2love") else {
            return
        }
        
        let imageURL = containerURL.appendingPathComponent(key)
        try? FileManager.default.removeItem(at: imageURL)
    }
    
    private func resizeImage(_ image: UIImage, to size: CGSize) -> UIImage? {
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        image.draw(in: CGRect(origin: .zero, size: size))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return resizedImage
    }
}

// MARK: - Data Models

struct RelationshipStats {
    let startDate: Date
    let daysTotal: Int
    let years: Int
    let months: Int
    let days: Int
    let nextAnniversary: Date
    let daysToAnniversary: Int
    let hoursToAnniversary: Int
    let minutesToAnniversary: Int
    let secondsToAnniversary: Int
    
    var formattedDuration: String {
        if years > 0 {
            if months > 0 {
                return "\(years) an\(years > 1 ? "s" : "") et \(months) mois"
            } else {
                return "\(years) an\(years > 1 ? "s" : "")"
            }
        } else if months > 0 {
            return "\(months) mois"
        } else {
            return "\(days) jour\(days > 1 ? "s" : "")"
        }
    }
    
    var countdownText: String {
        if daysToAnniversary > 0 {
            return "\(daysToAnniversary):\(String(format: "%02d", hoursToAnniversary)):\(String(format: "%02d", minutesToAnniversary)):\(String(format: "%02d", secondsToAnniversary))"
        } else {
            return "00:00:00:00"
        }
    }
}

struct DistanceInfo {
    let distance: Double
    let currentUserLocation: UserLocation
    let partnerLocation: UserLocation
    let messages: [String]
    let lastUpdated: Date
    
    var formattedDistance: String {
        if distance < 1 {
            return "\(Int(distance * 1000)) m"
        } else {
            return "\(Int(distance)) km"
        }
    }
    
    var randomMessage: String {
        messages.randomElement() ?? "💕 Je pense à toi"
    }
} 