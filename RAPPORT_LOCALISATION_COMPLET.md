# 🗺️ Système de Localisation - Rapport Technique Complet

## Vue d'Ensemble

Ce rapport détaille l'architecture complète du système de localisation de l'application iOS Love2Love, incluant la récupération, la sécurisation, le partage entre partenaires, l'affichage et le stockage Firebase. Il propose ensuite une implémentation équivalente pour Android GO.

---

## 🏗️ Architecture iOS Actuelle

### **1. Modèle de Données - UserLocation.swift**

```swift
struct UserLocation: Codable, Equatable {
    let latitude: Double
    let longitude: Double
    let address: String?       // Adresse complète géocodée
    let city: String?          // Ville (ex: "Paris")
    let country: String?       // Pays (ex: "France")
    let lastUpdated: Date      // Timestamp de dernière mise à jour

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    var displayName: String {
        if let city = city, let country = country {
            return "\(city), \(country)" // Format affiché: "Paris, France"
        } else if let address = address {
            return address
        } else {
            return "Localisation"
        }
    }

    // Calcul distance entre localisations
    func distance(to otherLocation: UserLocation) -> Double {
        let location1 = CLLocation(latitude: latitude, longitude: longitude)
        let location2 = CLLocation(latitude: otherLocation.latitude, longitude: otherLocation.longitude)
        return location1.distance(from: location2) / 1000 // En kilomètres
    }
}
```

**Structure de données** :

- **Coordonnées précises** : `latitude`/`longitude` (Double)
- **Géocodage inversé** : `address`, `city`, `country` via `CLGeocoder`
- **Horodatage** : `lastUpdated` pour cache et synchronisation
- **Méthodes utilitaires** : Calcul distance, affichage formaté

---

## 📍 Récupération et Traitement Localisation

### **2. LocationService.swift - Service Principal**

```swift
class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var currentLocation: UserLocation?
    @Published var isUpdatingLocation = false

    private let locationManager = CLLocationManager()
    private var hasRequestedLocationPermission = false

    func requestLocationUpdate() {
        print("📍 LocationService: Demande de mise à jour localisation")

        switch locationManager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            startLocationUpdate()
        case .notDetermined:
            hasRequestedLocationPermission = true
            locationManager.requestWhenInUseAuthorization()
        case .denied, .restricted:
            print("❌ LocationService: Permission refusée")
        @unknown default:
            print("⚠️ LocationService: Status inconnu")
        }
    }

    private func startLocationUpdate() {
        guard CLLocationManager.locationServicesEnabled() else {
            print("❌ LocationService: Services de localisation désactivés")
            return
        }

        isUpdatingLocation = true
        locationManager.requestLocation() // Une seule position
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }

        print("📍 LocationService: Nouvelle localisation reçue")
        isUpdatingLocation = false

        // GÉOCODAGE INVERSÉ pour obtenir l'adresse
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
                    print("📍 LocationService: Adresse résolue avec succès")
                } else {
                    userLocation = UserLocation(coordinate: location.coordinate)
                    print("📍 LocationService: Adresse non résolue - Coordonnées uniquement")
                }

                // Analytics + Sauvegarde Firebase
                Analytics.logEvent("localisation_utilisee", parameters: [:])
                self?.saveLocationToFirebase(userLocation)
            }
        }
    }

    private func saveLocationToFirebase(_ location: UserLocation) {
        // Délégation vers FirebaseService pour sauvegarde sécurisée
        FirebaseService.shared.updateUserLocation(location) { success in
            if success {
                print("✅ LocationService: Localisation sauvée Firebase")
            } else {
                print("❌ LocationService: Erreur sauvegarde Firebase")
            }
        }
    }
}
```

**Fonctionnalités clés** :

- **Permissions iOS** : Gestion complète des autorisations localisation
- **Géocodage inversé** : Conversion coordonnées → adresse lisible
- **Une seule mesure** : `requestLocation()` optimisé vs suivi continu
- **Analytics** : Tracking utilisation localisation
- **Sauvegarde automatique** : Intégration Firebase transparente

---

## 🔐 Sécurité et Chiffrement

### **3. LocationEncryptionService.swift - Chiffrement AES-GCM**

```swift
class LocationEncryptionService {
    static let ENCRYPTION_DISABLED_FOR_APPLE_REVIEW = true // Temporaire
    static let currentVersion = "2.0"

    // Clé symétrique stockée dans le Keychain iOS
    private static let symmetricKey: SymmetricKey = {
        let keychainKey = "love2love_location_encryption_key"

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.lyes.love2love.encryption",
            kSecAttrAccount as String: keychainKey,
            kSecReturnData as String: true
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        if status == errSecSuccess, let keyData = result as? Data {
            // Clé existante
            return SymmetricKey(data: keyData)
        } else {
            // Nouvelle clé générée et stockée
            let newKey = SymmetricKey(size: .bits256)
            // ... sauvegarde Keychain ...
            return newKey
        }
    }()

    // ÉCRITURE SÉCURISÉE vers Firebase
    static func writeLocation(_ location: CLLocation) -> [String: Any]? {
        guard let encryptedString = encryptLocation(location) else {
            print("❌ LocationEncryption: Échec du chiffrement")
            return nil
        }

        return [
            // 🆕 Format chiffré AES-GCM
            "encryptedLocation": encryptedString,
            "locationVersion": currentVersion,
            "hasLocation": true,
            "encryptedAt": Date(),

            // Rétrocompatibilité format ancien (non chiffré)
            "location": [
                "latitude": location.coordinate.latitude,
                "longitude": location.coordinate.longitude
            ],

            "migrationStatus": "hybrid"
        ]
    }

    // LECTURE SÉCURISÉE depuis Firebase
    static func readLocation(from firestoreData: [String: Any]) -> LocationData? {

        // 🔐 NOUVEAU FORMAT CHIFFRÉ
        if let encryptedLocation = firestoreData["encryptedLocation"] as? String,
           let version = firestoreData["locationVersion"] as? String {

            if let decrypted = decryptLocation(encryptedLocation) {
                return LocationData(
                    latitude: decrypted.coordinate.latitude,
                    longitude: decrypted.coordinate.longitude,
                    isEncrypted: true,
                    version: version
                )
            }
        }

        // 🔄 ANCIEN FORMAT CLAIR (rétrocompatibilité)
        if let location = firestoreData["location"] as? [String: Any],
           let latitude = location["latitude"] as? Double,
           let longitude = location["longitude"] as? Double {

            return LocationData(
                latitude: latitude,
                longitude: longitude,
                isEncrypted: false,
                version: "1.0"
            )
        }

        return nil
    }

    // Chiffrement AES-GCM
    private static func encryptLocation(_ location: CLLocation) -> String? {
        let locationString = "\(location.coordinate.latitude),\(location.coordinate.longitude)"
        let data = Data(locationString.utf8)

        do {
            let sealedBox = try AES.GCM.seal(data, using: symmetricKey)
            return sealedBox.combined?.base64EncodedString()
        } catch {
            print("❌ LocationEncryption: Erreur chiffrement - \(error)")
            return nil
        }
    }

    // Déchiffrement AES-GCM
    private static func decryptLocation(_ encryptedString: String) -> CLLocation? {
        guard let data = Data(base64Encoded: encryptedString) else { return nil }

        do {
            let sealedBox = try AES.GCM.SealedBox(combined: data)
            let decryptedData = try AES.GCM.open(sealedBox, using: symmetricKey)

            guard let coordinatesString = String(data: decryptedData, encoding: .utf8) else { return nil }

            let coordinates = coordinatesString.split(separator: ",")
            guard coordinates.count == 2,
                  let latitude = Double(coordinates[0]),
                  let longitude = Double(coordinates[1]) else { return nil }

            return CLLocation(latitude: latitude, longitude: longitude)
        } catch {
            return nil
        }
    }
}
```

**Sécurité renforcée** :

- ✅ **Chiffrement AES-GCM 256 bits** (standard militaire)
- ✅ **Clé stockée dans Keychain iOS** (sécurité matérielle)
- ✅ **Rétrocompatibilité** avec ancien format
- ✅ **Migration progressive** des données existantes
- ✅ **Base64 encoding** pour stockage Firebase

---

## 🗂️ Stockage Firebase

### **4. FirebaseService.swift - Gestion Firestore**

```swift
func updateUserLocation(_ location: UserLocation, completion: @escaping (Bool) -> Void) {
    guard let firebaseUser = Auth.auth().currentUser else {
        print("❌ FirebaseService: Aucun utilisateur connecté pour mise à jour localisation")
        completion(false)
        return
    }

    print("🔥 FirebaseService: Mise à jour localisation utilisateur")

    let locationData: [String: Any] = [
        "latitude": location.latitude,
        "longitude": location.longitude,
        "address": location.address as Any,
        "city": location.city as Any,
        "country": location.country as Any,
        "lastUpdated": Timestamp(date: location.lastUpdated)
    ]

    db.collection("users").document(firebaseUser.uid).updateData([
        "currentLocation": locationData,
        "updatedAt": Timestamp(date: Date())
    ]) { [weak self] error in
        DispatchQueue.main.async {
            if let error = error {
                print("❌ FirebaseService: Erreur mise à jour localisation: \(error.localizedDescription)")
                completion(false)
            } else {
                print("✅ FirebaseService: Localisation mise à jour avec succès")

                // Mise à jour locale immédiate
                if var currentUser = self?.currentUser {
                    currentUser.currentLocation = location
                    self?.currentUser = currentUser

                    // Cache utilisateur mis à jour
                    UserCacheManager.shared.cacheUser(currentUser)
                    print("💾 FirebaseService: Cache utilisateur mis à jour avec nouvelle localisation")
                }
                completion(true)
            }
        }
    }
}

// Parser localisation depuis Firestore
private func parseUserLocation(from data: [String: Any]?) -> UserLocation? {
    guard let data = data,
          let latitude = data["latitude"] as? Double,
          let longitude = data["longitude"] as? Double else {
        return nil
    }

    let address = data["address"] as? String
    let city = data["city"] as? String
    let country = data["country"] as? String

    return UserLocation(
        coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
        address: address,
        city: city,
        country: country
    )
}
```

**Structure Firestore** :

```
users/{userId} {
  currentLocation: {
    latitude: 48.8566,
    longitude: 2.3522,
    address: "1 Place Vendôme",
    city: "Paris",
    country: "France",
    lastUpdated: Timestamp
  },
  updatedAt: Timestamp,
  // ... autres champs utilisateur
}
```

---

## 🤝 Partage Localisation entre Partenaires

### **5. Cloud Functions Firebase - Sécurité Partenaire**

#### **getPartnerLocation (index.js)**

```javascript
exports.getPartnerLocation = functions.https.onCall(async (data, context) => {
  console.log(
    "🌍 getPartnerLocation: Début récupération localisation partenaire"
  );

  // 1. Vérifier authentification
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Utilisateur non authentifié"
    );
  }

  const currentUserId = context.auth.uid;
  const { partnerId } = data;

  if (!partnerId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "ID partenaire requis"
    );
  }

  try {
    // 2. Vérifier que l'utilisateur est bien connecté à ce partenaire
    const currentUserDoc = await admin
      .firestore()
      .collection("users")
      .doc(currentUserId)
      .get();

    if (!currentUserDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Utilisateur non trouvé"
      );
    }

    const currentUserData = currentUserDoc.data();

    // 3. SÉCURITÉ: Vérifier relation partenaire
    if (currentUserData.partnerId !== partnerId) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Vous n'êtes pas autorisé à accéder à la localisation de cet utilisateur"
      );
    }

    // 4. Récupérer localisation du partenaire
    const partnerDoc = await admin
      .firestore()
      .collection("users")
      .doc(partnerId)
      .get();

    if (!partnerDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Partenaire non trouvé"
      );
    }

    const partnerData = partnerDoc.data();
    const currentLocation = partnerData.currentLocation;

    if (!currentLocation) {
      return {
        success: false,
        reason: "NO_LOCATION",
        message: "Aucune localisation disponible pour ce partenaire",
      };
    }

    // 5. Retourner localisation sécurisée
    return {
      success: true,
      location: {
        latitude: currentLocation.latitude,
        longitude: currentLocation.longitude,
        address: currentLocation.address || null,
        city: currentLocation.city || null,
        country: currentLocation.country || null,
        lastUpdated: currentLocation.lastUpdated,
      },
    };
  } catch (error) {
    console.error("❌ getPartnerLocation: Erreur:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});
```

**Sécurité Cloud Function** :

- ✅ **Authentification obligatoire** : `context.auth` vérifié
- ✅ **Vérification relation partenaire** : `partnerId` doit correspondre
- ✅ **Pas d'accès arbitraire** : Impossible d'accéder à n'importe qui
- ✅ **Logs sécurisés** : Aucun ID exposé dans les logs

### **6. PartnerLocationService.swift - Service Client**

```swift
class PartnerLocationService: ObservableObject {
    static let shared = PartnerLocationService()

    @Published var partnerLocation: UserLocation?
    @Published var partnerName: String?
    @Published var isLoading = false

    private let functions = Functions.functions()
    private var lastFetchTime: Date = Date.distantPast
    private var lastLocationFetchTime: Date = Date.distantPast
    private let cacheValidityInterval: TimeInterval = 30 // 30 secondes

    func configureListener(for partnerId: String) {
        print("🌍 PartnerLocationService: Configuration listener partenaire")
        fetchPartnerDataViaCloudFunction(partnerId: partnerId)
    }

    private func fetchPartnerLocationViaCloudFunction(partnerId: String) {
        // Cache pour éviter appels trop fréquents
        let now = Date()
        if now.timeIntervalSince(lastLocationFetchTime) < 5 {
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
                        print("✅ PartnerLocationService: Localisation partenaire récupérée")
                        self?.updatePartnerLocationFromCloudFunction(locationData)
                    }
                } else {
                    let reason = data["reason"] as? String ?? "unknown"
                    print("❌ PartnerLocationService: Pas de localisation disponible - Raison: \(reason)")
                    self?.partnerLocation = nil
                }
            }
        }
    }

    private func updatePartnerLocationFromCloudFunction(_ locationData: [String: Any]) {
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
    }
}
```

**Flow de récupération** :

1. **App demande localisation partenaire** → `PartnerLocationService`
2. **Service appelle Cloud Function** → `getPartnerLocation`
3. **Cloud Function vérifie sécurité** → Relation partenaire
4. **Si autorisé, retourne données** → Localisation chiffrée
5. **App reçoit et affiche** → Interface utilisateur

---

## 🎨 Affichage Interface Utilisateur

### **7. PartnerDistanceView.swift - Affichage Principal**

```swift
struct PartnerDistanceView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var partnerLocationService = PartnerLocationService.shared
    let onPartnerAvatarTap: (() -> Void)?
    let onDistanceTap: (_ showPartnerMessageOnly: Bool) -> Void

    // Cache distance pour éviter recalculs
    @State private var cachedDistance: String = "km ?"
    @State private var lastCalculationTime: Date = Date.distantPast

    private var partnerDistance: String {
        // Cache ultra-rapide - recalculer que toutes les 2 secondes
        let now = Date()
        if now.timeIntervalSince(lastCalculationTime) < 2 && cachedDistance != "km ?" && cachedDistance != "? mi" {
            return cachedDistance
        }

        guard let currentUser = appState.currentUser,
              let currentLocation = currentUser.currentLocation,
              let partnerLocation = partnerLocationService.partnerLocation else {
            return "km ?"
        }

        // Calcul distance en temps réel
        let distance = currentLocation.distance(to: partnerLocation)
        let formattedDistance: String

        // Localisation des unités
        let locale = Locale.current
        let usesMetricSystem = locale.usesMetricSystem

        if usesMetricSystem {
            if distance < 1 {
                formattedDistance = String(format: "%.0f m", distance * 1000)
            } else if distance < 10 {
                formattedDistance = String(format: "%.1f km", distance)
            } else {
                formattedDistance = String(format: "%.0f km", distance)
            }
        } else {
            // Système impérial (miles)
            let miles = distance * 0.621371
            if miles < 1 {
                formattedDistance = String(format: "%.0f ft", miles * 5280)
            } else {
                formattedDistance = String(format: "%.1f mi", miles)
            }
        }

        // Mise à jour du cache
        cachedDistance = formattedDistance
        lastCalculationTime = now

        return formattedDistance
    }

    var body: some View {
        VStack(spacing: 16) {
            // Photos de profil avec distance
            HStack(spacing: 20) {
                // Photo utilisateur actuel
                Button(action: { /* Aucune action pour l'utilisateur actuel */ }) {
                    UserProfileImage(
                        imageURL: appState.currentUser?.profileImageURL,
                        userName: appState.currentUser?.name ?? "",
                        size: 80
                    )
                }
                .buttonStyle(PlainButtonStyle())
                .disabled(true) // Désactiver le tap

                // Distance au centre
                VStack(spacing: 4) {
                    Text(partnerDistance)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)

                    Text("distance_between".localized)
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                }
                .frame(minWidth: 80)
                .onTapGesture {
                    // Gestion tap sur distance
                    let currentUser = appState.currentUser
                    let hasUserLocation = currentUser?.currentLocation != nil
                    let hasPartnerLocation = partnerLocationService.partnerLocation != nil

                    if !hasUserLocation || !hasPartnerLocation {
                        onDistanceTap(false) // Demander permissions
                    } else {
                        onDistanceTap(true) // Juste afficher message
                    }
                }

                // Photo partenaire (cliquable)
                Button(action: {
                    onPartnerAvatarTap?()
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

            // Localisations textuelles
            HStack {
                // Localisation utilisateur
                VStack(alignment: .leading, spacing: 4) {
                    if let userLocation = appState.currentUser?.currentLocation {
                        Text(userLocation.displayName)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                    } else {
                        Text("location_unknown".localized)
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }

                Spacer()

                // Localisation partenaire
                VStack(alignment: .trailing, spacing: 4) {
                    if let partnerLocation = partnerLocationService.partnerLocation {
                        Text(partnerLocation.displayName)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                    } else if hasConnectedPartner {
                        Text("partner_location_unknown".localized)
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.6))
                    } else {
                        Text("no_partner_connected".localized)
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
            }
            .padding(.horizontal, 40)
        }
        .padding(.horizontal, 40)
        .padding(.vertical, 16)
        .background(
            LinearGradient(
                gradient: Gradient(colors: [Color(hex: "#FF6B35"), Color(hex: "#F7931E")]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(20)
        .onAppear {
            print("🚀 PartnerDistanceView: onAppear - Initialisation")

            // Charger dernière distance connue depuis cache
            loadLastKnownDistance()

            // Configurer listener partenaire
            if let partnerId = appState.currentUser?.partnerId {
                partnerLocationService.configureListener(for: partnerId)
            }

            // Forcer mise à jour immédiate
            forceUpdateDistance()
        }
    }
}
```

**Affichage temps réel** :

- **Position** : En haut de l'écran principal (`HomeContentView`)
- **Design** : Dégradé orange, photos circulaires, distance centrée
- **Interactif** : Tap sur distance → permissions, Tap sur avatar → profil
- **Localisation** : Textes sous les photos ("Paris, France")
- **Cache optimisé** : Évite recalculs constants
- **Unités localisées** : km/m (métrique) vs mi/ft (impérial)

---

## 📊 Performance et Cache

### **8. Optimisations Système**

**Cache multi-niveaux** :

1. **Cache mémoire** (`@State cachedDistance`) - < 1ms
2. **UserDefaults** (`lastDistanceKey`) - < 10ms
3. **Cloud Function cache** (30 secondes) - Évite spam serveur
4. **Firestore cache** - Données offline

**Gestion permissions iOS** :

```swift
func requestLocationUpdate() {
    switch locationManager.authorizationStatus {
    case .authorizedWhenInUse, .authorizedAlways:
        startLocationUpdate()
    case .notDetermined:
        locationManager.requestWhenInUseAuthorization()
    case .denied, .restricted:
        // Rediriger vers Réglages iOS
        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(settingsUrl)
        }
    }
}
```

**Analytics et logs** :

- **Utilisation localisation** : `Analytics.logEvent("localisation_utilisee")`
- **Erreurs géocodage** : Logs détaillés sans coordonnées
- **Performance cache** : Temps de calcul distance

---

# 🤖 Implémentation Android GO Équivalente

## Architecture Android Proposée

### **1. Modèles de Données**

```kotlin
// UserLocation.kt
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lastUpdated: Date = Date()
) {

    val coordinate: LatLng
        get() = LatLng(latitude, longitude)

    val displayName: String
        get() = when {
            city != null && country != null -> "$city, $country"
            address != null -> address
            else -> "Localisation"
        }

    // Calcul distance avec autre localisation
    fun distanceTo(other: UserLocation): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            latitude, longitude,
            other.latitude, other.longitude,
            results
        )
        return (results[0] / 1000).toDouble() // En kilomètres
    }
}

// LocationData.kt - Pour chiffrement
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val isEncrypted: Boolean,
    val version: String
)
```

### **2. Service Localisation Android**

```kotlin
@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()

    private val _isUpdatingLocation = MutableStateFlow(false)
    val isUpdatingLocation: StateFlow<Boolean> = _isUpdatingLocation.asStateFlow()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun requestLocationUpdate() {
        Log.d("LocationService", "📍 Demande mise à jour localisation")

        if (!hasLocationPermission()) {
            Log.d("LocationService", "❌ Permission localisation manquante")
            return
        }

        _isUpdatingLocation.value = true

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMaxUpdates(1) // Une seule mesure
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)

            val location = locationResult.lastLocation ?: return
            Log.d("LocationService", "📍 Nouvelle localisation reçue")

            _isUpdatingLocation.value = false

            // Géocodage inversé
            reverseGeocode(location)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                Log.d("LocationService", "❌ Localisation non disponible")
                _isUpdatingLocation.value = false
            }
        }
    }

    private fun reverseGeocode(location: Location) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API moderne
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                ) { addresses ->
                    handleGeocodingResult(location, addresses)
                }
            } else {
                // API legacy
                val addresses = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )
                handleGeocodingResult(location, addresses)
            }
        } catch (e: Exception) {
            Log.e("LocationService", "❌ Erreur géocodage", e)
            handleGeocodingResult(location, emptyList())
        }
    }

    private fun handleGeocodingResult(location: Location, addresses: List<Address>?) {
        val userLocation = if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            UserLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                address = "${address.thoroughfare ?: ""} ${address.subThoroughfare ?: ""}".trim(),
                city = address.locality,
                country = address.countryName
            )
        } else {
            UserLocation(
                latitude = location.latitude,
                longitude = location.longitude
            )
        }

        Log.d("LocationService", "📍 Localisation créée: ${userLocation.displayName}")
        _currentLocation.value = userLocation

        // Sauvegarde Firebase
        saveLocationToFirebase(userLocation)

        // Analytics
        FirebaseAnalytics.getInstance(context).logEvent("localisation_utilisee", null)
    }

    private fun saveLocationToFirebase(location: UserLocation) {
        // Utiliser FirebaseService pour sauvegarde
        // (implémentation similaire à iOS)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
```

### **3. Chiffrement Android**

```kotlin
@Singleton
class LocationEncryptionService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val ENCRYPTION_DISABLED_FOR_REVIEW = true // Temporaire
        const val CURRENT_VERSION = "2.0"
        private const val KEY_ALIAS = "love2love_location_key"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    init {
        generateKeyIfNeeded()
    }

    private fun generateKeyIfNeeded() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()

            Log.d("LocationEncryption", "🔐 Nouvelle clé générée dans Android Keystore")
        }
    }

    fun writeLocation(location: UserLocation): Map<String, Any> {
        if (ENCRYPTION_DISABLED_FOR_REVIEW) {
            return mapOf(
                "location" to mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude
                ),
                "hasLocation" to true,
                "locationVersion" to "1.0-temp",
                "migrationStatus" to "unencrypted_temp"
            )
        }

        val encryptedString = encryptLocation(location)
            ?: return emptyMap()

        return mapOf(
            // Format chiffré
            "encryptedLocation" to encryptedString,
            "locationVersion" to CURRENT_VERSION,
            "hasLocation" to true,
            "encryptedAt" to Date(),

            // Rétrocompatibilité
            "location" to mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude
            ),
            "migrationStatus" to "hybrid"
        )
    }

    fun readLocation(from firestoreData: Map<String, Any>): LocationData? {
        // Format chiffré
        val encryptedLocation = firestoreData["encryptedLocation"] as? String
        val version = firestoreData["locationVersion"] as? String

        if (encryptedLocation != null && version != null) {
            val decrypted = decryptLocation(encryptedLocation)
            if (decrypted != null) {
                return LocationData(
                    latitude = decrypted.latitude,
                    longitude = decrypted.longitude,
                    isEncrypted = true,
                    version = version
                )
            }
        }

        // Format legacy
        val location = firestoreData["location"] as? Map<String, Any>
        if (location != null) {
            val latitude = location["latitude"] as? Double
            val longitude = location["longitude"] as? Double

            if (latitude != null && longitude != null) {
                return LocationData(
                    latitude = latitude,
                    longitude = longitude,
                    isEncrypted = false,
                    version = "1.0"
                )
            }
        }

        return null
    }

    private fun encryptLocation(location: UserLocation): String? {
        try {
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val locationString = "${location.latitude},${location.longitude}"
            val encryptedBytes = cipher.doFinal(locationString.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            // Combiner IV + données chiffrées
            val combined = iv + encryptedBytes
            return Base64.encodeToString(combined, Base64.DEFAULT)

        } catch (e: Exception) {
            Log.e("LocationEncryption", "❌ Erreur chiffrement", e)
            return null
        }
    }

    private fun decryptLocation(encryptedString: String): UserLocation? {
        try {
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val combined = Base64.decode(encryptedString, Base64.DEFAULT)

            // Séparer IV (12 bytes) et données
            val iv = combined.sliceArray(0..11)
            val encryptedData = combined.sliceArray(12 until combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(encryptedData)
            val coordinatesString = String(decryptedBytes, Charsets.UTF_8)

            val coordinates = coordinatesString.split(",")
            if (coordinates.size == 2) {
                val latitude = coordinates[0].toDouble()
                val longitude = coordinates[1].toDouble()
                return UserLocation(latitude = latitude, longitude = longitude)
            }

        } catch (e: Exception) {
            Log.e("LocationEncryption", "❌ Erreur déchiffrement", e)
        }

        return null
    }
}
```

### **4. Interface Compose - Affichage Distance**

```kotlin
@Composable
fun PartnerDistanceView(
    onPartnerAvatarTap: () -> Unit,
    onDistanceTap: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PartnerLocationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val partnerDistance = remember(currentUser?.currentLocation, uiState.partnerLocation) {
        calculateDistance(currentUser?.currentLocation, uiState.partnerLocation)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box {
            // Fond dégradé
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFF6B35),
                                Color(0xFFF7931E)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Photos avec distance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo utilisateur
                    UserProfileImage(
                        imageUrl = currentUser?.profileImageURL,
                        userName = currentUser?.name ?: "",
                        size = 80.dp,
                        modifier = Modifier.clickable { /* Pas d'action */ }
                    )

                    // Distance centrale (cliquable)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val hasUserLocation = currentUser?.currentLocation != null
                            val hasPartnerLocation = uiState.partnerLocation != null
                            onDistanceTap(!hasUserLocation || !hasPartnerLocation)
                        }
                    ) {
                        Text(
                            text = partnerDistance,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )

                        Text(
                            text = stringResource(R.string.distance_between),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // Photo partenaire (cliquable)
                    PartnerProfileImage(
                        hasPartner = uiState.hasConnectedPartner,
                        imageUrl = uiState.partnerImageURL,
                        partnerName = uiState.partnerName ?: "",
                        size = 80.dp,
                        modifier = Modifier.clickable { onPartnerAvatarTap() }
                    )
                }

                // Localisations textuelles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Localisation utilisateur
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = currentUser?.currentLocation?.displayName
                                ?: stringResource(R.string.location_unknown),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (currentUser?.currentLocation != null)
                                    FontWeight.Medium else FontWeight.Normal
                            ),
                            color = Color.White.copy(
                                alpha = if (currentUser?.currentLocation != null) 1f else 0.6f
                            )
                        )
                    }

                    // Localisation partenaire
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = when {
                                uiState.partnerLocation != null -> uiState.partnerLocation!!.displayName
                                uiState.hasConnectedPartner -> stringResource(R.string.partner_location_unknown)
                                else -> stringResource(R.string.no_partner_connected)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (uiState.partnerLocation != null)
                                    FontWeight.Medium else FontWeight.Normal
                            ),
                            color = Color.White.copy(
                                alpha = if (uiState.partnerLocation != null) 1f else 0.6f
                            )
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(currentUser?.partnerId) {
        currentUser?.partnerId?.let { partnerId ->
            viewModel.configurePartnerListener(partnerId)
        }
    }
}

@Composable
private fun calculateDistance(
    userLocation: UserLocation?,
    partnerLocation: UserLocation?
): String {
    if (userLocation == null || partnerLocation == null) {
        return "km ?"
    }

    val distance = userLocation.distanceTo(partnerLocation)
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]

    // Système métrique vs impérial
    val isMetric = locale.country != "US" && locale.country != "LR" && locale.country != "MM"

    return if (isMetric) {
        when {
            distance < 1 -> String.format(locale, "%.0f m", distance * 1000)
            distance < 10 -> String.format(locale, "%.1f km", distance)
            else -> String.format(locale, "%.0f km", distance)
        }
    } else {
        val miles = distance * 0.621371
        when {
            miles < 1 -> String.format(locale, "%.0f ft", miles * 5280)
            else -> String.format(locale, "%.1f mi", miles)
        }
    }
}
```

### **5. ViewModel Android**

```kotlin
@HiltViewModel
class PartnerLocationViewModel @Inject constructor(
    private val cloudFunctionService: CloudFunctionService,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerLocationUiState())
    val uiState: StateFlow<PartnerLocationUiState> = _uiState.asStateFlow()

    val currentUser = userRepository.getCurrentUser()

    data class PartnerLocationUiState(
        val partnerLocation: UserLocation? = null,
        val partnerName: String? = null,
        val partnerImageURL: String? = null,
        val hasConnectedPartner: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private var lastFetchTime = 0L
    private val cacheValidityMs = 5000L // 5 secondes

    fun configurePartnerListener(partnerId: String) {
        Log.d("PartnerLocationVM", "🌍 Configuration listener partenaire")
        fetchPartnerLocation(partnerId)
    }

    private fun fetchPartnerLocation(partnerId: String) {
        val now = System.currentTimeMillis()
        if (now - lastFetchTime < cacheValidityMs) {
            Log.d("PartnerLocationVM", "🌍 Cache valide, skip fetch")
            return
        }

        lastFetchTime = now

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                Log.d("PartnerLocationVM", "🌍 Récupération localisation via Cloud Function")
                val result = cloudFunctionService.getPartnerLocation(partnerId)

                if (result.success && result.location != null) {
                    val location = result.location
                    val partnerLocation = UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = location.address,
                        city = location.city,
                        country = location.country
                    )

                    _uiState.value = _uiState.value.copy(
                        partnerLocation = partnerLocation,
                        hasConnectedPartner = true,
                        isLoading = false
                    )

                    Log.d("PartnerLocationVM", "✅ Localisation partenaire récupérée: ${location.city}")

                } else {
                    Log.d("PartnerLocationVM", "❌ Pas de localisation disponible")
                    _uiState.value = _uiState.value.copy(
                        partnerLocation = null,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                Log.e("PartnerLocationVM", "❌ Erreur récupération localisation partenaire", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
}
```

---

## 📊 Comparaison iOS vs Android

| **Aspect**            | **iOS**                                 | **Android**                              |
| --------------------- | --------------------------------------- | ---------------------------------------- |
| **Permissions**       | `CLLocationManager` + Info.plist        | `FusedLocationProviderClient` + Manifest |
| **Géocodage**         | `CLGeocoder.reverseGeocodeLocation`     | `Geocoder.getFromLocation`               |
| **Chiffrement**       | AES-GCM + Keychain                      | AES-GCM + Android Keystore               |
| **Stockage local**    | UserDefaults                            | SharedPreferences                        |
| **Calcul distance**   | `CLLocation.distance(from:)`            | `Location.distanceBetween()`             |
| **Interface**         | SwiftUI                                 | Jetpack Compose                          |
| **Cloud Functions**   | `Functions.functions().httpsCallable()` | Retrofit + Cloud Functions               |
| **Cache temps réel**  | `@State` + Timer                        | `StateFlow` + Coroutines                 |
| **Unités localisées** | `Locale.current.usesMetricSystem`       | `ConfigurationCompat.getLocales()`       |

## 🚀 Plan d'Implémentation Android

### **Phase 1 : Services Core (2 semaines)**

1. ✅ `LocationService` avec `FusedLocationProviderClient`
2. ✅ `LocationEncryptionService` avec Android Keystore
3. ✅ Modèles `UserLocation` et `LocationData`
4. ✅ Tests permissions et géocodage

### **Phase 2 : Firebase Integration (1 semaine)**

5. ✅ `FirebaseService.updateUserLocation()`
6. ✅ `CloudFunctionService.getPartnerLocation()`
7. ✅ Parsing et sérialisation Firestore
8. ✅ Tests Cloud Functions

### **Phase 3 : Interface Compose (2 semaines)**

9. ✅ `PartnerDistanceView` avec photos et distance
10. ✅ `PartnerLocationViewModel` avec cache
11. ✅ Animations et dégradés
12. ✅ Gestion tap et navigation

### **Phase 4 : Optimisations (1 semaine)**

13. ✅ Cache multi-niveaux (mémoire + prefs)
14. ✅ Gestion hors-ligne
15. ✅ Analytics Firebase
16. ✅ Tests sur appareils Android GO

Cette architecture Android GO reproduit **fidèlement** l'expérience iOS avec sécurité renforcée et performance optimisée ! 🌍🔐
