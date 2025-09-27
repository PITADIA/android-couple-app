# 🗺️ FONCTIONNALITÉ COMPLÈTE - Carte Journal avec Sélection Position

## 🎯 Vue d'Ensemble des Composants Carte

1. **LocationPickerView** - Sélecteur position avec pin central
2. **JournalMapView** - Affichage événements avec clustering
3. **MapViewRepresentable** - Wrapper UIKit pour MKMapView
4. **JournalLocation/JournalEntry** - Modèles données avec Firebase
5. **CreateJournalEntryView** - Interface création avec localisation
6. **JournalService** - Logique métier avec chiffrement hybride

---

## 📍 1. SÉLECTEUR POSITION (`LocationPickerView`)

### 🎨 Design Interface Sélection

```swift
NavigationView {
    ZStack {
        // Background sombre sophistiqué
        Color(red: 0.1, green: 0.02, blue: 0.05)  // RGB(25, 5, 13) - Noir rougeâtre
            .ignoresSafeArea()

        VStack(spacing: 0) {
            mapSection  // Carte plein écran
        }

        floatingButton  // Bouton "Sélectionner" flottant
    }
}
```

### 🗺️ Carte Interactive avec Pin Central

```swift
ZStack {
    // Carte MKMapView native via UIViewRepresentable
    MapViewRepresentable(region: $region, onRegionChange: { newRegion in
        handleRegionChange(newRegion)  // Debouncing 0.5s pour éviter spam
    })

    // Pin central fixe au centre de l'écran
    VStack {
        Spacer()
        HStack {
            Spacer()
            Image(systemName: "mappin")
                .font(.system(size: 30))
                .foregroundColor(Color(hex: "#FD267A"))  // Rose Love2Love
                .background(
                    Circle()
                        .fill(.white)
                        .frame(width: 40, height: 40)
                )
            Spacer()
        }
        Spacer()
    }
}
```

**Détails Pin Central :**

- **Icône** : `"mappin"` SF Symbol de 30pt
- **Couleur** : Rose Love2Love `#FD267A`
- **Background** : Cercle blanc de 40pt
- **Position** : Toujours au centre exact de l'écran
- **Comportement** : Pin fixe, carte bouge dessous

### 📍 Système Région par Défaut Intelligent

```swift
private func getDefaultPickerRegion() -> MKCoordinateRegion {
    // 1️⃣ PRIORITÉ MAXIMALE: Localisation actuelle si disponible
    if let currentLocation = locationManager.location {
        return MKCoordinateRegion(
            center: currentLocation.coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.1, longitudeDelta: 0.1)  // Zoom précis
        )
    }

    // 2️⃣ FALLBACK INTELLIGENT: Locale/région du téléphone
    let locale = Locale.current
    let languageCode = locale.language.languageCode?.identifier ?? "en"
    let regionCode = locale.region?.identifier ?? "US"

    switch (languageCode, regionCode) {
    case ("fr", _):           // France pour tous français
        center: CLLocationCoordinate2D(latitude: 46.2276, longitude: 2.2137)
        span: MKCoordinateSpan(latitudeDelta: 2.0, longitudeDelta: 2.0)

    case ("en", "US"):        // États-Unis
        center: CLLocationCoordinate2D(latitude: 39.8283, longitude: -98.5795)
        span: MKCoordinateSpan(latitudeDelta: 25.0, longitudeDelta: 25.0)

    case ("en", "CA"):        // Canada
        center: CLLocationCoordinate2D(latitude: 56.1304, longitude: -106.3468)

    case ("de", "DE"):        // Allemagne
        center: CLLocationCoordinate2D(latitude: 51.1657, longitude: 10.4515)

    case ("es", "ES"):        // Espagne
        center: CLLocationCoordinate2D(latitude: 40.4637, longitude: -3.7492)

    default:                  // Vue monde par défaut
        center: CLLocationCoordinate2D(latitude: 20.0, longitude: 0.0)
        span: MKCoordinateSpan(latitudeDelta: 30.0, longitudeDelta: 30.0)
    }
}
```

**Logique Région :**

- **14 pays/régions** pré-configurés avec coordonnées précises
- **Zoom adaptatif** : 2° pour Europe, 25° pour grands pays, 30° monde
- **Détection automatique** : Utilise `Locale.current` iOS
- **Fallback robuste** : Vue monde si région inconnue

### 🎯 Info Localisation Temps Réel

```swift
@ViewBuilder
private var locationInfo: some View {
    if !currentLocationName.isEmpty {
        VStack {
            Spacer()

            VStack(spacing: 8) {
                Text("selected_location")  // "Position sélectionnée"
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.black)
                    .padding(.horizontal, 20)
                    .padding(.top, 10)

                Text(currentLocationName)  // "Paris, France"
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.black.opacity(0.8))         // Fond noir semi-transparent
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.2), lineWidth: 1)  // Bordure subtile
                    )
            )
            .padding(.horizontal, 20)
            .padding(.bottom, 100)  // Au-dessus du bouton flottant
        }
    }
}
```

### 🚀 Geocoding Inverse Temps Réel (Debouncing)

```swift
private func handleRegionChange(_ newRegion: MKCoordinateRegion) {
    let currentCenter = newRegion.center

    // Éviter appels inutiles si changement minime
    if let lastCoordinate = selectedCoordinate {
        let distance = sqrt(
            pow(currentCenter.latitude - lastCoordinate.latitude, 2) +
            pow(currentCenter.longitude - lastCoordinate.longitude, 2)
        )
        if distance < 0.0001 { return }  // Seuil 0.0001° ≈ 10m
    }

    // DEBOUNCING avec Timer 0.5s
    debounceTimer?.invalidate()
    debounceTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { _ in
        DispatchQueue.main.async {
            self.selectedCoordinate = currentCenter
            self.reverseGeocode(coordinate: currentCenter)  // CLGeocoder
        }
    }
}

private func reverseGeocode(coordinate: CLLocationCoordinate2D) {
    let geocoder = CLGeocoder()
    let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)

    geocoder.reverseGeocodeLocation(location) { placemarks, error in
        DispatchQueue.main.async {
            if let placemark = placemarks?.first {
                let components = [
                    placemark.name,        // Nom lieu spécifique
                    placemark.locality,    // Ville
                    placemark.country      // Pays
                ].compactMap { $0 }

                self.currentLocationName = components.joined(separator: ", ")
            } else {
                self.currentLocationName = "custom_location"  // "Position personnalisée"
            }
        }
    }
}
```

### 🎯 Bouton Flottant Validation

```swift
private var floatingButton: some View {
    VStack {
        Spacer()

        Button("select") {  // "Sélectionner"
            confirmSelection()
        }
        .font(.system(size: 18, weight: .semibold))
        .foregroundColor(.white)
        .frame(maxWidth: .infinity)
        .frame(height: 56)                    // Hauteur tactile confortable
        .background(
            RoundedRectangle(cornerRadius: 28)
                .fill(selectedCoordinate != nil ? Color(hex: "#FD267A") : Color.gray.opacity(0.3))
        )
        .disabled(selectedCoordinate == nil)  // Désactivé si pas de sélection
        .padding(.horizontal, 20)
        .padding(.bottom, 20)
    }
}

private func confirmSelection() {
    guard let coordinate = selectedCoordinate else { return }

    let geocoder = CLGeocoder()
    let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)

    geocoder.reverseGeocodeLocation(location) { placemarks, error in
        DispatchQueue.main.async {
            if let placemark = placemarks?.first {
                // ✅ Création JournalLocation avec détails complets
                self.selectedLocation = JournalLocation(
                    coordinate: coordinate,
                    address: placemark.name,        // Adresse précise
                    city: placemark.locality,       // Ville
                    country: placemark.country      // Pays
                )
            } else {
                // ❌ Fallback si geocoding échoue
                self.selectedLocation = JournalLocation(
                    coordinate: coordinate,
                    address: self.currentLocationName,
                    city: nil,
                    country: nil
                )
            }

            self.dismiss()  // Fermer modal et renvoyer JournalLocation
        }
    }
}
```

### 📱 Toolbar Navigation Sophistiquée

```swift
@ToolbarContentBuilder
private var toolbarContent: some ToolbarContent {
    // Bouton Annuler (gauche)
    ToolbarItem(placement: .navigationBarLeading) {
        Button("cancel") { dismiss() }
            .foregroundColor(.white)
    }

    // Titre centré
    ToolbarItem(placement: .principal) {
        Text("choose_location")  // "Choisir une position"
            .font(.system(size: 20, weight: .bold))
            .foregroundColor(.black)
            .padding(.horizontal, 20)
            .padding(.top, 20)
    }

    // Bouton localisation actuelle (droite)
    ToolbarItem(placement: .navigationBarTrailing) {
        Button(action: { requestCurrentLocation() }) {
            Image(systemName: "location.circle")
                .font(.system(size: 20))
                .foregroundColor(.white)
        }
    }
}

private func requestCurrentLocation() {
    guard locationManager.authorizationStatus == .authorizedWhenInUse ||
          locationManager.authorizationStatus == .authorizedAlways else {
        locationManager.requestWhenInUseAuthorization()  // Demander permission
        return
    }

    if let currentLocation = locationManager.location {
        region.center = currentLocation.coordinate
        selectedCoordinate = currentLocation.coordinate
        reverseGeocode(coordinate: currentLocation.coordinate)
    }
}
```

---

## 🗺️ 2. AFFICHAGE CARTE JOURNAL (`JournalMapView`)

### 🎨 Carte Plein Écran avec Clustering

```swift
ZStack {
    // Carte native avec API iOS 17+ et fallback iOS 16
    Group {
        if #available(iOS 17.0, *) {
            // 🚀 iOS 17+ : Nouvelle API MapKit
            Map(position: .constant(.region(mapRegion))) {
                ForEach(clusters) { cluster in
                    Annotation("", coordinate: cluster.coordinate) {
                        if cluster.isCluster {
                            // 📍 CLUSTER (plusieurs événements proches)
                            OptimizedClusterAnnotationView(cluster: cluster) {
                                selectedCluster = cluster
                                showingClusterDetail = true
                            }
                        } else {
                            // 📍 ÉVÉNEMENT UNIQUE
                            OptimizedJournalMapAnnotationView(entry: cluster.firstEntry) {
                                selectedEntry = cluster.firstEntry
                                showingEntryDetail = true
                            }
                        }
                    }
                }
            }
        } else {
            // 📱 iOS 16 : API MapKit classique (fallback)
            Map(coordinateRegion: .constant(mapRegion), annotationItems: clusters) { cluster in
                MapAnnotation(coordinate: cluster.coordinate) {
                    // Mêmes composants annotation
                }
            }
        }
    }
}
```

### 🔗 Système Clustering Sophistiqué

```swift
// NOUVEAU: Calculer les clusters de manière stable sans effet de bord
private var clusters: [JournalCluster] {
    createStableClusters(from: entriesWithLocation, zoomLevel: mapRegion.span.latitudeDelta)
}

private func createStableClusters(from entries: [JournalEntry], zoomLevel: Double) -> [JournalCluster] {
    guard !entries.isEmpty else { return [] }

    // Distance de clustering selon le zoom (plus proche = plus de clusters)
    let clusterDistance: Double
    switch zoomLevel {
    case 0...0.01:      clusterDistance = 0.001      // Zoom très proche
    case 0.01...0.1:    clusterDistance = 0.01       // Zoom proche
    case 0.1...1.0:     clusterDistance = 0.1        // Zoom moyen
    case 1.0...10.0:    clusterDistance = 1.0        // Zoom éloigné
    default:            clusterDistance = 5.0        // Zoom très éloigné
    }

    var clusters: [JournalCluster] = []
    var processedEntries: Set<String> = []

    for entry in entries {
        guard !processedEntries.contains(entry.id),
              let location = entry.location else { continue }

        // Trouver toutes les entrées proches
        let nearbyEntries = entries.filter { otherEntry in
            guard let otherLocation = otherEntry.location,
                  !processedEntries.contains(otherEntry.id) else { return false }

            let distance = sqrt(
                pow(location.latitude - otherLocation.latitude, 2) +
                pow(location.longitude - otherLocation.longitude, 2)
            )

            return distance <= clusterDistance
        }

        // Marquer comme traitées
        nearbyEntries.forEach { processedEntries.insert($0.id) }

        if nearbyEntries.count > 1 {
            // Créer un cluster
            let centerLat = nearbyEntries.map { $0.location!.latitude }.reduce(0, +) / Double(nearbyEntries.count)
            let centerLng = nearbyEntries.map { $0.location!.longitude }.reduce(0, +) / Double(nearbyEntries.count)

            clusters.append(JournalCluster(
                id: "cluster_\(entry.id)",
                coordinate: CLLocationCoordinate2D(latitude: centerLat, longitude: centerLng),
                entries: nearbyEntries,
                isCluster: true
            ))
        } else {
            // Entrée unique
            clusters.append(JournalCluster(
                id: entry.id,
                coordinate: location.coordinate,
                entries: [entry],
                isCluster: false
            ))
        }
    }

    return clusters
}
```

### 🔢 Logique Région par Défaut Intelligente

```swift
private func getDefaultMapRegion() -> MKCoordinateRegion {
    // 1️⃣ PRIORITÉ : Localisation actuelle si disponible
    if let currentLocation = appState.locationService?.currentLocation {
        return MKCoordinateRegion(
            center: currentLocation.coordinate,
            span: MKCoordinateSpan(latitudeDelta: 5.0, longitudeDelta: 5.0)  // Zoom régional
        )
    }

    // 2️⃣ FALLBACK : Même logique que LocationPickerView mais zoom plus large
    // (Même switch case avec span plus important pour vue générale)
    let locale = Locale.current
    // ... logique identique avec span plus grand pour vue globale
}

// Filtrer les entrées qui ont une localisation
private var entriesWithLocation: [JournalEntry] {
    journalService.entries.filter { $0.location != nil }
}

// Calculer le nombre de pays uniques pour statistiques
private var uniqueCountriesCount: Int {
    let countries = Set(entriesWithLocation.compactMap { $0.location?.country })
    return countries.count
}

// Calculer le nombre de villes uniques pour statistiques
private var uniqueCitiesCount: Int {
    let cities = Set(entriesWithLocation.compactMap { $0.location?.city })
    return cities.count
}
```

---

## 📦 3. MODÈLE DONNÉES (`JournalLocation` & `JournalEntry`)

### 🗺️ Structure JournalLocation

```swift
struct JournalLocation: Codable, Equatable {
    let latitude: Double         // Coordonnée précise
    let longitude: Double        // Coordonnée précise
    let address: String?         // Adresse complète (ex: "123 Rue de la Paix")
    let city: String?            // Ville (ex: "Paris")
    let country: String?         // Pays (ex: "France")

    init(coordinate: CLLocationCoordinate2D, address: String? = nil, city: String? = nil, country: String? = nil) {
        self.latitude = coordinate.latitude
        self.longitude = coordinate.longitude
        self.address = address
        self.city = city
        self.country = country
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    var displayName: String {
        if let city = city, let country = country {
            return "\(city), \(country)"           // "Paris, France"
        } else if let address = address {
            return address                         // "123 Rue de la Paix"
        } else {
            return "Localisation"                  // Fallback générique
        }
    }
}
```

### 📝 Structure JournalEntry avec Localisation

```swift
struct JournalEntry: Codable, Identifiable, Equatable {
    let id: String
    var title: String                    // Titre événement
    var description: String              // Description détaillée
    var eventDate: Date                  // Date/heure événement
    var createdAt: Date                  // Date création
    var updatedAt: Date                  // Dernière modification
    var authorId: String                 // UID auteur Firebase
    var authorName: String               // Nom auteur pour affichage
    var imageURL: String?                // URL image Firebase Storage
    var localImagePath: String?          // Chemin local temporaire
    var isShared: Bool                   // Visible par le partenaire
    var partnerIds: [String]             // IDs partenaires autorisés
    var location: JournalLocation?       // 🗺️ LOCALISATION OPTIONNELLE

    init(
        id: String = UUID().uuidString,
        title: String,
        description: String,
        eventDate: Date,
        authorId: String,
        authorName: String,
        imageURL: String? = nil,
        localImagePath: String? = nil,
        isShared: Bool = true,
        partnerIds: [String] = [],
        location: JournalLocation? = nil   // 📍 Paramètre localisation
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.eventDate = eventDate
        self.createdAt = Date()
        self.updatedAt = Date()
        self.authorId = authorId
        self.authorName = authorName
        self.imageURL = imageURL
        self.localImagePath = localImagePath
        self.isShared = isShared
        self.partnerIds = partnerIds
        self.location = location
    }
}
```

---

## 🔐 4. INTÉGRATION FIREBASE (`JournalService` & Chiffrement)

### 🚀 Création Événement avec Localisation

```swift
func createEntry(
    title: String,
    description: String,
    eventDate: Date,
    image: UIImage? = nil,
    location: JournalLocation? = nil    // 📍 Paramètre localisation
) async throws {
    guard let currentUser = Auth.auth().currentUser,
          let userData = FirebaseService.shared.currentUser else {
        throw JournalError.userNotAuthenticated
    }

    // Vérifier limite freemium
    guard let freemiumManager = appState?.freemiumManager else {
        throw JournalError.freemiumCheckFailed
    }

    let userEntriesCount = currentUserEntriesCount
    guard freemiumManager.canAddJournalEntry(currentEntriesCount: userEntriesCount) else {
        throw JournalError.freemiumLimitReached
    }

    await MainActor.run { self.isLoading = true }

    do {
        var imageURL: String?

        // Upload image si présente
        if let image = image {
            imageURL = try await uploadImage(image)
        }

        // Déterminer partage partenaire
        var partnerIds: [String] = [currentUser.uid]  // Toujours inclure auteur
        if let partnerId = userData.partnerId {
            partnerIds.append(partnerId)              // Inclure partenaire si connecté
        }

        let entry = JournalEntry(
            title: title,
            description: description,
            eventDate: eventDate,
            authorId: currentUser.uid,
            authorName: userData.name,
            imageURL: imageURL,
            partnerIds: partnerIds,
            location: location              // 📍 INCLURE LOCALISATION
        )

        // Sauvegarder dans Firestore avec chiffrement
        try await db.collection("journalEntries")
            .document(entry.id)
            .setData(entry.toDictionary())  // Chiffrement automatique via toDictionary()

        print("✅ JournalService: Entrée créée avec localisation")
        await refreshEntries()

    } catch {
        print("❌ JournalService: Erreur création: \(error)")
        throw error
    }
}
```

### 🔐 Sérialisation Firebase avec Chiffrement Hybride

```swift
func toDictionary() -> [String: Any] {
    var dict: [String: Any] = [
        "eventDate": Timestamp(date: eventDate),
        "createdAt": Timestamp(date: createdAt),
        "updatedAt": Timestamp(date: updatedAt),
        "authorId": authorId,
        "authorName": authorName,
        "imageURL": imageURL as Any,
        "isShared": isShared,
        "partnerIds": partnerIds
    ]

    // 🔐 CHIFFREMENT HYBRIDE des métadonnées sensibles
    let encryptedTitleData = LocationEncryptionService.processMessageForStorage(title)
    dict.merge(encryptedTitleData.mapKeys { key in
        return key == "encryptedText" ? "encryptedTitle" : key.replacingOccurrences(of: "text", with: "title")
    }) { (_, new) in new }

    let encryptedDescriptionData = LocationEncryptionService.processMessageForStorage(description)
    dict.merge(encryptedDescriptionData.mapKeys { key in
        return key == "encryptedText" ? "encryptedDescription" : key.replacingOccurrences(of: "text", with: "description")
    }) { (_, new) in new }

    // 🗺️ ÉCRITURE LOCALISATION avec chiffrement coordonnées + métadonnées publiques
    if let location = location {
        let clLocation = CLLocation(latitude: location.latitude, longitude: location.longitude)

        // ✅ NOUVEAU FORMAT CHIFFRÉ pour coordonnées sensibles
        let encryptedLocationData = LocationEncryptionService.processLocationForStorage(clLocation)
        if !encryptedLocationData.isEmpty {
            dict.merge(encryptedLocationData) { (_, new) in new }
        }

        // ✅ MÉTADONNÉES PUBLIQUES (non sensibles) pour statistiques
        if let address = location.address {
            dict["locationAddress"] = address
        }
        if let city = location.city {
            dict["locationCity"] = city          // 📊 Utilisé pour statistiques villes
        }
        if let country = location.country {
            dict["locationCountry"] = country     // 📊 Utilisé pour statistiques pays
        }

        print("✅ JournalEntry: Localisation sauvegardée avec chiffrement hybride")
    }

    return dict
}
```

### 🔓 Désérialisation Firebase avec Déchiffrement

```swift
init?(from document: DocumentSnapshot) {
    guard let data = document.data() else { return nil }

    self.id = document.documentID

    // 🔓 DÉCHIFFREMENT HYBRIDE des métadonnées
    self.title = LocationEncryptionService.readMessageFromFirestore(
        data.mapKeys { key in
            return key == "encryptedTitle" ? "encryptedText" : key.replacingOccurrences(of: "title", with: "text")
        }
    ) ?? data["title"] as? String ?? ""

    self.description = LocationEncryptionService.readMessageFromFirestore(
        data.mapKeys { key in
            return key == "encryptedDescription" ? "encryptedText" : key.replacingOccurrences(of: "description", with: "text")
        }
    ) ?? data["description"] as? String ?? ""

    // ... autres champs standards ...

    // 🗺️ MIGRATION HYBRIDE - Désérialiser localisation (Nouveau + Ancien format)
    if let locationData = LocationEncryptionService.readLocation(from: data) {
        let coordinate = locationData.toCLLocation().coordinate

        // Récupérer métadonnées additionnelles
        var address: String?
        var city: String?
        var country: String?

        // ✅ NOUVEAU FORMAT : Métadonnées publiques séparées
        address = data["locationAddress"] as? String
        city = data["locationCity"] as? String
        country = data["locationCountry"] as? String

        // ❌ ANCIEN FORMAT : Fallback pour migration
        if city == nil && country == nil {
            if let legacyLocation = data["location"] as? [String: Any] {
                address = address ?? (legacyLocation["address"] as? String)
                city = legacyLocation["city"] as? String
                country = legacyLocation["country"] as? String
            }
        }

        self.location = JournalLocation(
            coordinate: coordinate,
            address: address,
            city: city,
            country: country
        )

        print("✅ JournalEntry: Localisation chargée (chiffré: \(locationData.isEncrypted))")
    } else {
        self.location = nil
    }
}
```

### 🔄 Synchronisation Partenaire (Cloud Function)

```javascript
// firebase/functions/index.js
async function syncPartnerJournalEntriesInternal(currentUserId, partnerId) {
  console.log(
    `🔄 Journal: Synchronisation entrées entre ${currentUserId} et ${partnerId}`
  );

  try {
    // Récupérer toutes les entrées de l'utilisateur actuel
    const userEntriesSnapshot = await admin
      .firestore()
      .collection("journalEntries")
      .where("authorId", "==", currentUserId)
      .get();

    // Récupérer toutes les entrées du partenaire
    const partnerEntriesSnapshot = await admin
      .firestore()
      .collection("journalEntries")
      .where("authorId", "==", partnerId)
      .get();

    const batch = admin.firestore().batch();
    let updateCount = 0;

    // Ajouter le partenaire aux entrées de l'utilisateur
    for (const doc of userEntriesSnapshot.docs) {
      const data = doc.data();
      const partnerIds = data.partnerIds || [];

      if (!partnerIds.includes(partnerId)) {
        partnerIds.push(partnerId);
        batch.update(doc.ref, { partnerIds });
        updateCount++;
      }
    }

    // Ajouter l'utilisateur aux entrées du partenaire
    for (const doc of partnerEntriesSnapshot.docs) {
      const data = doc.data();
      const partnerIds = data.partnerIds || [];

      if (!partnerIds.includes(currentUserId)) {
        partnerIds.push(currentUserId);
        batch.update(doc.ref, { partnerIds });
        updateCount++;
      }
    }

    if (updateCount > 0) {
      await batch.commit();
      console.log(
        `✅ Journal: ${updateCount} entrées synchronisées avec succès`
      );
    } else {
      console.log(`✅ Journal: Aucune synchronisation nécessaire`);
    }

    return { success: true, updatedCount: updateCount };
  } catch (error) {
    console.error("❌ Journal: Erreur synchronisation:", error);
    throw error;
  }
}
```

---

## 🎨 5. INTERFACE CRÉATION ÉVÉNEMENT (`CreateJournalEntryView`)

### 📍 Bouton Sélection Position

```swift
// Section localisation dans CreateJournalEntryView
VStack(spacing: 0) {
    Button(action: {
        showingLocationPicker = true  // Ouvrir LocationPickerView
    }) {
        HStack {
            Image(systemName: "location")
                .font(.system(size: 16))
                .foregroundColor(Color(hex: "#FD267A"))  // Rose Love2Love

            Text(selectedLocation?.displayName ?? "choose_location")  // "Choisir une position"
                .font(.system(size: 16))
                .foregroundColor(selectedLocation != nil ? .black : .gray)

            Spacer()

            if selectedLocation != nil {
                Button(action: {
                    selectedLocation = nil  // Supprimer localisation
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundColor(.gray)
                }
            } else {
                Image(systemName: "chevron.right")
                    .font(.system(size: 14))
                    .foregroundColor(.gray)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(Color.white)
        .overlay(
            Rectangle()
                .frame(height: 1)
                .foregroundColor(Color.gray.opacity(0.3)),
            alignment: .bottom
        )
    }
    .buttonStyle(PlainButtonStyle())
}
.sheet(isPresented: $showingLocationPicker) {
    LocationPickerView(selectedLocation: $selectedLocation)  // Modal sélection
}
```

### 💾 Création avec Localisation

```swift
private func createEntry() {
    guard canSave else { return }

    isCreating = true

    Task {
        do {
            try await journalService.createEntry(
                title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                description: description.trimmingCharacters(in: .whitespacesAndNewlines),
                eventDate: eventDate,
                image: selectedImage,
                location: selectedLocation    // 📍 INCLURE LOCALISATION SÉLECTIONNÉE
            )

            // Analytics : Type événement
            let entryType = selectedLocation != nil ? "avec_localisation" :
                          (selectedImage != nil ? "photo" : "texte")
            Analytics.logEvent("journal_evenement_ajoute", parameters: [
                "type": entryType,
                "has_location": selectedLocation != nil
            ])

            await MainActor.run { dismiss() }

        } catch {
            await MainActor.run {
                if let freemiumError = error as? JournalError,
                   freemiumError == .freemiumLimitReached {
                    freemiumErrorMessage = "journal_freemium_limit_message"
                    showingFreemiumAlert = true
                } else {
                    // Autres erreurs
                }
                isCreating = false
            }
        }
    }
}
```

---

## 🖼️ Images et Assets Utilisés

| Composant            | Asset                 | Usage                           | Couleur/Style                 |
| -------------------- | --------------------- | ------------------------------- | ----------------------------- |
| **Pin Central**      | `"mappin"`            | Marqueur sélection position     | Rose #FD267A, fond blanc 40pt |
| **Localisation**     | `"location"`          | Bouton ajout position           | Rose #FD267A, 16pt            |
| **Position GPS**     | `"location.circle"`   | Bouton localisation actuelle    | Blanc, 20pt                   |
| **Suppression**      | `"xmark.circle.fill"` | Supprimer position sélectionnée | Gris, 16pt                    |
| **Navigation**       | `"chevron.right"`     | Flèche bouton position          | Gris, 14pt                    |
| **Clusters Carte**   | Dynamique             | Annotations clustering          | Rose #FD267A avec nombre      |
| **Événements Carte** | `"heart.fill"`        | Marqueur événement unique       | Couleur selon image/type      |

**Note :** Toutes les icônes utilisent **SF Symbols** natifs iOS. Pas d'assets personnalisés requis.

---

## 🌐 Keys de Traduction (UI.xcstrings)

### Interface LocationPicker

```json
"choose_location": {
    "fr": "Choisir une position",
    "en": "Choose location",
    "de": "Standort wählen",
    "es": "Elegir ubicación"
},

"selected_location": {
    "fr": "Position sélectionnée",
    "en": "Selected location",
    "de": "Ausgewählter Standort",
    "es": "Ubicación seleccionada"
},

"custom_location": {
    "fr": "Position personnalisée",
    "en": "Custom location",
    "de": "Benutzerdefinierter Standort",
    "es": "Ubicación personalizada"
}
```

### Actions et Boutons

```json
"select": {
    "fr": "Sélectionner",
    "en": "Select",
    "de": "Auswählen",
    "es": "Seleccionar"
},

"cancel": {
    "fr": "Annuler",
    "en": "Cancel",
    "de": "Abbrechen",
    "es": "Cancelar"
}
```

### Messages Journa

```json
"journal_freemium_limit_message": {
    "fr": "Vous avez atteint la limite d'événements gratuits. Passez à la version premium pour en ajouter plus.",
    "en": "You've reached the free event limit. Upgrade to premium to add more.",
    "de": "Sie haben das Limit für kostenlose Ereignisse erreicht. Upgraden Sie auf Premium, um mehr hinzuzufügen.",
    "es": "Has alcanzado el límite de eventos gratuitos. Actualiza a premium para añadir más."
}
```

---

## 📏 Espacements et Dimensions

### LocationPickerView

```swift
// Background sombre
Color(red: 0.1, green: 0.02, blue: 0.05)  // RGB(25, 5, 13)

// Pin central
.font(.system(size: 30))                   // Icône 30pt
.frame(width: 40, height: 40)              // Background cercle 40pt

// Info localisation
.padding(16)                               // Padding interne card
.cornerRadius(12)                          // Coins arrondis card
.padding(.horizontal, 20)                  // Marges latérales
.padding(.bottom, 100)                     // Au-dessus bouton

// Bouton flottant
.frame(height: 56)                         // Hauteur tactile
.cornerRadius(28)                          // Coins très arrondis (56/2)
.padding(.horizontal, 20)                  // Marges latérales
.padding(.bottom, 20)                      // Marge bottom sécurité

// Toolbar
.font(.system(size: 20, weight: .bold))    // Titre toolbar
.padding(.horizontal, 20)                  // Marges titre
.padding(.top, 20)                         // Espace top titre
```

### JournalMapView

```swift
// Région par défaut - Zoom adaptatif selon pays
span: MKCoordinateSpan(latitudeDelta: 2.0, longitudeDelta: 2.0)    // Europe
span: MKCoordinateSpan(latitudeDelta: 25.0, longitudeDelta: 25.0)  // États-Unis
span: MKCoordinateSpan(latitudeDelta: 10.0, longitudeDelta: 10.0)  // Canada

// Distance clustering selon zoom
case 0...0.01:      clusterDistance = 0.001    // Très proche
case 0.01...0.1:    clusterDistance = 0.01     // Proche
case 0.1...1.0:     clusterDistance = 0.1      // Moyen
case 1.0...10.0:    clusterDistance = 1.0      // Éloigné
default:            clusterDistance = 5.0      // Très éloigné
```

### CreateJournalEntry - Section Position

```swift
.padding(.horizontal, 20)                  // Marges latérales bouton
.padding(.vertical, 16)                    // Hauteur tactile bouton
.font(.system(size: 16))                   // Police texte et icônes
.overlay(                                  // Séparateur bottom
    Rectangle()
        .frame(height: 1)
        .foregroundColor(Color.gray.opacity(0.3))
)
```

---

## 🤖 Adaptation Android (Kotlin/Compose + Google Maps)

### 1. Sélecteur Position Android

```kotlin
@Composable
fun LocationPickerScreen(
    selectedLocation: JournalLocation?,
    onLocationSelected: (JournalLocation) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var cameraPositionState by remember { mutableStateOf(
        CameraPositionState(
            position = CameraPosition.fromLatLngZoom(
                getDefaultLocationForRegion(context), // Logique région identique iOS
                10f
            )
        )
    ) }
    var selectedCoordinate by remember { mutableStateOf<LatLng?>(null) }
    var locationName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF190D05))  // RGB(25, 13, 5)
    ) {
        // Google Maps Compose
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { /* Pas utilisé - pin fixe */ },
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = true,
                myLocationButtonEnabled = false
            )
        ) {
            // Pas de marqueurs - pin fixe au centre
        }

        // Pin central fixe
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Pin",
                    tint = Color(0xFFFD267A),
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Info localisation en bas
        if (locationName.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 100.dp)
                    .background(
                        Color.Black.copy(alpha = 0.8f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.selected_location),
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = locationName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bouton flottant sélection
        Button(
            onClick = { confirmSelection(selectedCoordinate, onLocationSelected) },
            enabled = selectedCoordinate != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (selectedCoordinate != null) Color(0xFFFD267A) else Color.Gray.copy(alpha = 0.3f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = stringResource(R.string.select),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Toolbar
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.choose_location),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            },
            navigationIcon = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = { requestCurrentLocation() }) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Current location",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            backgroundColor = Color.Transparent,
            elevation = 0.dp
        )
    }

    // Observer changement position caméra
    LaunchedEffect(cameraPositionState.position) {
        val center = cameraPositionState.position.target
        selectedCoordinate = center

        // Debouncing avec coroutines
        delay(500)
        reverseGeocode(context, center) { name ->
            locationName = name
        }
    }
}

fun getDefaultLocationForRegion(context: Context): LatLng {
    val locale = Locale.getDefault()
    val language = locale.language
    val country = locale.country

    return when (language to country) {
        "fr" to null -> LatLng(46.2276, 2.2137)      // France
        "en" to "US" -> LatLng(39.8283, -98.5795)    // États-Unis
        "en" to "CA" -> LatLng(56.1304, -106.3468)   // Canada
        "de" to "DE" -> LatLng(51.1657, 10.4515)     // Allemagne
        "es" to "ES" -> LatLng(40.4637, -3.7492)     // Espagne
        else -> LatLng(20.0, 0.0)                    // Monde
    }
}

fun reverseGeocode(
    context: Context,
    latLng: LatLng,
    onResult: (String) -> Unit
) {
    val geocoder = Geocoder(context, Locale.getDefault())

    try {
        val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
        if (addresses?.isNotEmpty() == true) {
            val address = addresses[0]
            val components = listOfNotNull(
                address.featureName,
                address.locality,
                address.countryName
            )
            onResult(components.joinToString(", "))
        } else {
            onResult(context.getString(R.string.custom_location))
        }
    } catch (e: Exception) {
        onResult(context.getString(R.string.custom_location))
    }
}

fun confirmSelection(
    coordinate: LatLng?,
    onLocationSelected: (JournalLocation) -> Unit
) {
    coordinate?.let { coord ->
        // Geocoding pour détails complets
        val location = JournalLocation(
            coordinate = CLLocationCoordinate2D(coord.latitude, coord.longitude),
            address = null,  // À remplir via geocoding
            city = null,     // À remplir via geocoding
            country = null   // À remplir via geocoding
        )
        onLocationSelected(location)
    }
}
```

### 2. Affichage Carte Journal Android

```kotlin
@Composable
fun JournalMapScreen(
    entries: List<JournalEntry>,
    onEntrySelected: (JournalEntry) -> Unit,
    onClusterSelected: (List<JournalEntry>) -> Unit
) {
    val entriesWithLocation = entries.filter { it.location != null }
    val cameraPositionState = remember {
        CameraPositionState(
            position = CameraPosition.fromLatLngZoom(
                getDefaultLocationForRegion(LocalContext.current),
                5f
            )
        )
    }

    // Calculer clusters selon zoom
    val clusters by remember(entriesWithLocation, cameraPositionState.position.zoom) {
        derivedStateOf {
            createClusters(entriesWithLocation, cameraPositionState.position.zoom)
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false)
    ) {
        clusters.forEach { cluster ->
            if (cluster.isCluster) {
                // Cluster de plusieurs entrées
                MarkerInfoWindow(
                    state = MarkerState(
                        position = LatLng(
                            cluster.coordinate.latitude,
                            cluster.coordinate.longitude
                        )
                    )
                ) {
                    ClusterMarkerView(
                        count = cluster.entries.size,
                        onClick = { onClusterSelected(cluster.entries) }
                    )
                }
            } else {
                // Entrée unique
                MarkerInfoWindow(
                    state = MarkerState(
                        position = LatLng(
                            cluster.coordinate.latitude,
                            cluster.coordinate.longitude
                        )
                    )
                ) {
                    EntryMarkerView(
                        entry = cluster.entries.first(),
                        onClick = { onEntrySelected(cluster.entries.first()) }
                    )
                }
            }
        }
    }
}

@Composable
fun ClusterMarkerView(
    count: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color(0xFFFD267A), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EntryMarkerView(
    entry: JournalEntry,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color(0xFFFD267A), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (entry.hasImage) {
            // Afficher aperçu image
            AsyncImage(
                model = entry.imageURL,
                contentDescription = "Event image",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Icône par défaut
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Event",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

data class JournalCluster(
    val coordinate: CLLocationCoordinate2D,
    val entries: List<JournalEntry>,
    val isCluster: Boolean
)

fun createClusters(
    entries: List<JournalEntry>,
    zoomLevel: Float
): List<JournalCluster> {
    if (entries.isEmpty()) return emptyList()

    val clusterDistance = when (zoomLevel) {
        in 0f..5f -> 5.0      // Zoom éloigné
        in 5f..10f -> 1.0     // Zoom moyen
        in 10f..15f -> 0.1    // Zoom proche
        else -> 0.01          // Zoom très proche
    }

    val clusters = mutableListOf<JournalCluster>()
    val processedEntries = mutableSetOf<String>()

    entries.forEach { entry ->
        if (processedEntries.contains(entry.id)) return@forEach
        val location = entry.location ?: return@forEach

        // Trouver entrées proches
        val nearbyEntries = entries.filter { other ->
            val otherLocation = other.location ?: return@filter false
            if (processedEntries.contains(other.id)) return@filter false

            val distance = sqrt(
                (location.latitude - otherLocation.latitude).pow(2) +
                (location.longitude - otherLocation.longitude).pow(2)
            )

            distance <= clusterDistance
        }

        nearbyEntries.forEach { processedEntries.add(it.id) }

        if (nearbyEntries.size > 1) {
            // Créer cluster
            val centerLat = nearbyEntries.map { it.location!!.latitude }.average()
            val centerLng = nearbyEntries.map { it.location!!.longitude }.average()

            clusters.add(
                JournalCluster(
                    coordinate = CLLocationCoordinate2D(centerLat, centerLng),
                    entries = nearbyEntries,
                    isCluster = true
                )
            )
        } else {
            // Entrée unique
            clusters.add(
                JournalCluster(
                    coordinate = CLLocationCoordinate2D(location.latitude, location.longitude),
                    entries = listOf(entry),
                    isCluster = false
                )
            )
        }
    }

    return clusters
}
```

### 3. Modèles Données Android

```kotlin
data class JournalLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null
) {
    val coordinate: CLLocationCoordinate2D
        get() = CLLocationCoordinate2D(latitude, longitude)

    val displayName: String
        get() = when {
            city != null && country != null -> "$city, $country"
            address != null -> address
            else -> "Localisation"
        }
}

data class JournalEntry(
    val id: String,
    var title: String,
    var description: String,
    var eventDate: Date,
    var createdAt: Date,
    var updatedAt: Date,
    var authorId: String,
    var authorName: String,
    var imageURL: String? = null,
    var localImagePath: String? = null,
    var isShared: Boolean = true,
    var partnerIds: List<String> = emptyList(),
    var location: JournalLocation? = null
) {
    val hasImage: Boolean
        get() = imageURL != null || localImagePath != null

    val formattedEventDate: String
        get() = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(eventDate)
}
```

### 4. Service Journal Android

```kotlin
class JournalService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) {
    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    suspend fun createEntry(
        title: String,
        description: String,
        eventDate: Date,
        image: Uri? = null,
        location: JournalLocation? = null
    ): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

            var imageURL: String? = null
            if (image != null) {
                imageURL = uploadImage(image)
            }

            val entry = JournalEntry(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                eventDate = eventDate,
                createdAt = Date(),
                updatedAt = Date(),
                authorId = currentUser.uid,
                authorName = "User", // À récupérer du profil
                imageURL = imageURL,
                partnerIds = listOf(currentUser.uid), // + partenaire si connecté
                location = location
            )

            firestore.collection("journalEntries")
                .document(entry.id)
                .set(entry.toFirebaseMap())
                .await()

            refreshEntries()
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun uploadImage(uri: Uri): String {
        val imageRef = storage.reference
            .child("journal/${UUID.randomUUID()}.jpg")

        imageRef.putFile(uri).await()
        return imageRef.downloadUrl.await().toString()
    }

    private suspend fun refreshEntries() {
        try {
            val currentUser = auth.currentUser ?: return

            val snapshot = firestore.collection("journalEntries")
                .whereArrayContains("partnerIds", currentUser.uid)
                .get()
                .await()

            val entries = snapshot.documents.mapNotNull { doc ->
                doc.toJournalEntry()
            }.sortedByDescending { it.eventDate }

            _entries.value = entries

        } catch (e: Exception) {
            // Handle error
        }
    }
}

fun JournalEntry.toFirebaseMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "title" to title,
        "description" to description,
        "eventDate" to Timestamp(eventDate),
        "createdAt" to Timestamp(createdAt),
        "updatedAt" to Timestamp(updatedAt),
        "authorId" to authorId,
        "authorName" to authorName,
        "isShared" to isShared,
        "partnerIds" to partnerIds
    )

    imageURL?.let { map["imageURL"] = it }

    location?.let { loc ->
        map["locationLatitude"] = loc.latitude
        map["locationLongitude"] = loc.longitude
        loc.address?.let { map["locationAddress"] = it }
        loc.city?.let { map["locationCity"] = it }
        loc.country?.let { map["locationCountry"] = it }
    }

    return map
}

fun DocumentSnapshot.toJournalEntry(): JournalEntry? {
    return try {
        val data = data ?: return null

        val location = if (data.containsKey("locationLatitude")) {
            JournalLocation(
                latitude = data["locationLatitude"] as Double,
                longitude = data["locationLongitude"] as Double,
                address = data["locationAddress"] as? String,
                city = data["locationCity"] as? String,
                country = data["locationCountry"] as? String
            )
        } else null

        JournalEntry(
            id = id,
            title = data["title"] as String,
            description = data["description"] as String,
            eventDate = (data["eventDate"] as Timestamp).toDate(),
            createdAt = (data["createdAt"] as Timestamp).toDate(),
            updatedAt = (data["updatedAt"] as Timestamp).toDate(),
            authorId = data["authorId"] as String,
            authorName = data["authorName"] as String,
            imageURL = data["imageURL"] as? String,
            isShared = data["isShared"] as? Boolean ?: true,
            partnerIds = data["partnerIds"] as? List<String> ?: emptyList(),
            location = location
        )
    } catch (e: Exception) {
        null
    }
}
```

### 5. Strings.xml Android

```xml
<resources>
    <!-- Sélection position -->
    <string name="choose_location">Choisir une position</string>
    <string name="selected_location">Position sélectionnée</string>
    <string name="custom_location">Position personnalisée</string>
    <string name="select">Sélectionner</string>
    <string name="cancel">Annuler</string>

    <!-- Journal -->
    <string name="journal_freemium_limit_message">Vous avez atteint la limite d\'événements gratuits. Passez à la version premium pour en ajouter plus.</string>

    <!-- Permissions -->
    <string name="location_permission_required">L\'autorisation de localisation est requise pour utiliser cette fonctionnalité.</string>
    <string name="location_permission_denied">Autorisation de localisation refusée.</string>
</resources>
```

---

## 🎯 Points Clés Fonctionnalité Carte

✅ **Pin central fixe** : Interface intuitive avec marqueur au centre de l'écran  
✅ **Région intelligente** : Détection automatique selon langue/pays du téléphone  
✅ **Geocoding temps réel** : Affichage nom lieu avec debouncing 0.5s  
✅ **Clustering dynamique** : Regroupement selon zoom avec distance adaptative  
✅ **Chiffrement hybride** : Coordonnées chiffrées + métadonnées publiques pour stats  
✅ **Synchronisation partenaire** : Cloud Function pour partage automatique  
✅ **Freemium intégré** : Limite événements avec localisation  
✅ **Fallbacks robustes** : Gestion erreurs geocoding et permissions  
✅ **Performance optimisée** : Debouncing, clustering intelligent, cache  
✅ **UX cohérente** : Design Love2Love avec rose #FD267A partout

La fonctionnalité carte du journal présente un **système de sélection position ultra-intuitif** avec pin fixe, un **clustering dynamique sophistiqué**, et une **intégration Firebase sécurisée** avec chiffrement hybride pour protéger les coordonnées sensibles tout en permettant les statistiques villes/pays ! 🗺️✨

**Fichier :** `RAPPORT_CARTE_JOURNAL_COMPLET.md`
