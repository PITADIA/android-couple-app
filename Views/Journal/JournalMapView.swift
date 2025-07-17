import SwiftUI
import MapKit

// NOUVEAU: Structure pour gérer les clusters d'événements avec ID stable
struct JournalCluster: Identifiable, Equatable {
    let id: String // ID stable basé sur les entrées
    let coordinate: CLLocationCoordinate2D
    let entries: [JournalEntry]
    
    var count: Int { entries.count }
    var isCluster: Bool { count > 1 }
    var firstEntry: JournalEntry { entries.first! }
    
    // Créer un ID stable basé sur les IDs des entrées
    static func createId(from entries: [JournalEntry]) -> String {
        let sortedIds = entries.map { $0.id }.sorted()
        return sortedIds.joined(separator: "-")
    }
    
    // Equatable pour éviter les recréations inutiles
    static func == (lhs: JournalCluster, rhs: JournalCluster) -> Bool {
        lhs.id == rhs.id
    }
}

struct JournalMapView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var selectedEntry: JournalEntry?
    @State private var selectedCluster: JournalCluster?
    @State private var mapRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 46.2276, longitude: 2.2137), // Sera remplacé par getDefaultMapRegion()
        span: MKCoordinateSpan(latitudeDelta: 10, longitudeDelta: 10)
    )
    @State private var showingEntryDetail = false
    @State private var showingClusterDetail = false
    @State private var isCardHovered = false
    
    // NOUVEAU: Contrôler le recentrage automatique
    @State private var hasInitializedMap = false
    
    // NOUVEAU: Paramètre pour contrôler l'affichage du bouton retour
    let showBackButton: Bool
    
    // NOUVEAU: Observer directement le JournalService
    @ObservedObject private var journalService = JournalService.shared
    
    // NOUVEAU: Initializer avec paramètre par défaut
    init(showBackButton: Bool = true) {
        self.showBackButton = showBackButton
    }
    
    // Filtrer les entrées qui ont une localisation
    private var entriesWithLocation: [JournalEntry] {
        journalService.entries.filter { $0.location != nil }
    }
    
    // NOUVEAU: Calculer le nombre de pays uniques
    private var uniqueCountriesCount: Int {
        let countries = Set(entriesWithLocation.compactMap { $0.location?.country })
        return countries.count
    }
    
    // NOUVEAU: Calculer le nombre de villes uniques
    private var uniqueCitiesCount: Int {
        let cities = Set(entriesWithLocation.compactMap { $0.location?.city })
        return cities.count
    }
    
    // NOUVEAU: Calculer les clusters de manière stable sans effet de bord
    private var clusters: [JournalCluster] {
        createStableClusters(from: entriesWithLocation, zoomLevel: mapRegion.span.latitudeDelta)
    }
    
    // NOUVEAU: Logique intelligente pour la région par défaut
    private func getDefaultMapRegion() -> MKCoordinateRegion {
        // 1. Priorité : Utiliser la localisation actuelle si disponible
        if let currentLocation = appState.locationService?.currentLocation {
            print("🗺️ JournalMapView: Utilisation localisation actuelle: \(currentLocation.displayName)")
            return MKCoordinateRegion(
                center: currentLocation.coordinate,
                span: MKCoordinateSpan(latitudeDelta: 5.0, longitudeDelta: 5.0)
            )
        }
        
        // 2. Fallback : Utiliser la locale/région du téléphone
        let locale = Locale.current
        let languageCode = locale.languageCode ?? "en"
        let regionCode = locale.regionCode ?? "US"
        
        print("🗺️ JournalMapView: Locale détectée - Langue: \(languageCode), Région: \(regionCode)")
        
        let defaultRegion: MKCoordinateRegion
        
        switch (languageCode, regionCode) {
        // États-Unis
        case ("en", "US"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 39.8283, longitude: -98.5795), // Centre des États-Unis
                span: MKCoordinateSpan(latitudeDelta: 25.0, longitudeDelta: 25.0)
            )
            print("🇺🇸 JournalMapView: Région par défaut - États-Unis")
            
        // Canada
        case ("en", "CA"), ("fr", "CA"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 56.1304, longitude: -106.3468), // Centre du Canada
                span: MKCoordinateSpan(latitudeDelta: 30.0, longitudeDelta: 30.0)
            )
            print("🇨🇦 JournalMapView: Région par défaut - Canada")
            
        // Royaume-Uni
        case ("en", "GB"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 55.3781, longitude: -3.4360), // Centre du Royaume-Uni
                span: MKCoordinateSpan(latitudeDelta: 8.0, longitudeDelta: 8.0)
            )
            print("🇬🇧 JournalMapView: Région par défaut - Royaume-Uni")
            
        // Australie
        case ("en", "AU"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: -25.2744, longitude: 133.7751), // Centre de l'Australie
                span: MKCoordinateSpan(latitudeDelta: 30.0, longitudeDelta: 30.0)
            )
            print("🇦🇺 JournalMapView: Région par défaut - Australie")
            
        // France
        case ("fr", _):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 46.2276, longitude: 2.2137), // Centre de la France
                span: MKCoordinateSpan(latitudeDelta: 8.0, longitudeDelta: 8.0)
            )
            print("🇫🇷 JournalMapView: Région par défaut - France")
            
        // Espagne
        case ("es", "ES"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 40.4637, longitude: -3.7492), // Centre de l'Espagne
                span: MKCoordinateSpan(latitudeDelta: 8.0, longitudeDelta: 8.0)
            )
            print("🇪🇸 JournalMapView: Région par défaut - Espagne")
            
        // Allemagne
        case ("de", "DE"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 51.1657, longitude: 10.4515), // Centre de l'Allemagne
                span: MKCoordinateSpan(latitudeDelta: 8.0, longitudeDelta: 8.0)
            )
            print("🇩🇪 JournalMapView: Région par défaut - Allemagne")
            
        // Italie
        case ("it", "IT"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 41.8719, longitude: 12.5674), // Centre de l'Italie
                span: MKCoordinateSpan(latitudeDelta: 10.0, longitudeDelta: 8.0)
            )
            print("🇮🇹 JournalMapView: Région par défaut - Italie")
            
        // Japon
        case ("ja", "JP"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 36.2048, longitude: 138.2529), // Centre du Japon
                span: MKCoordinateSpan(latitudeDelta: 12.0, longitudeDelta: 10.0)
            )
            print("🇯🇵 JournalMapView: Région par défaut - Japon")
            
        // Brésil
        case ("pt", "BR"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: -14.2350, longitude: -51.9253), // Centre du Brésil
                span: MKCoordinateSpan(latitudeDelta: 25.0, longitudeDelta: 25.0)
            )
            print("🇧🇷 JournalMapView: Région par défaut - Brésil")
            
        // Europe par défaut (pour les autres pays européens)
        case (_, let region) where ["BE", "NL", "CH", "AT", "PT", "DK", "SE", "NO", "FI"].contains(region):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 54.5260, longitude: 15.2551), // Centre de l'Europe
                span: MKCoordinateSpan(latitudeDelta: 15.0, longitudeDelta: 15.0)
            )
            print("🇪🇺 JournalMapView: Région par défaut - Europe")
            
        // Vue monde par défaut pour les autres cas
        default:
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 20.0, longitude: 0.0), // Vue monde centrée
                span: MKCoordinateSpan(latitudeDelta: 120.0, longitudeDelta: 120.0)
            )
            print("🌍 JournalMapView: Région par défaut - Monde")
        }
        
        return defaultRegion
    }
    
    var body: some View {
        ZStack {
            // Carte en plein écran avec clustering
            Group {
                if #available(iOS 17.0, *) {
                    // iOS 17+ : Nouvelle API avec MapContentBuilder
                    Map(position: .constant(.region(mapRegion))) {
                        ForEach(clusters) { cluster in
                            Annotation(cluster.isCluster ? "Cluster" : cluster.firstEntry.title, 
                                     coordinate: cluster.coordinate) {
                                if cluster.isCluster {
                                    OptimizedClusterAnnotationView(cluster: cluster) {
                                        selectedCluster = cluster
                                        showingClusterDetail = true
                                    }
                                } else {
                                    OptimizedJournalMapAnnotationView(entry: cluster.firstEntry) {
                                        selectedEntry = cluster.firstEntry
                                        showingEntryDetail = true
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Fallback pour iOS 16 et antérieur
                    Map(coordinateRegion: $mapRegion, 
                        annotationItems: clusters) { cluster in
                        MapAnnotation(coordinate: cluster.coordinate) {
                            if cluster.isCluster {
                                // Affichage du cluster avec compteur
                                OptimizedClusterAnnotationView(cluster: cluster) {
                                    selectedCluster = cluster
                                    showingClusterDetail = true
                                }
                            } else {
                                // Affichage d'un événement unique
                                OptimizedJournalMapAnnotationView(entry: cluster.firstEntry) {
                                    selectedEntry = cluster.firstEntry
                                    showingEntryDetail = true
                                }
                            }
                        }
                    }
                }
            }
            .ignoresSafeArea(.all) // Plein écran
            .onAppear {
                // MODIFICATION: Initialisation intelligente de la carte
                if !hasInitializedMap {
                    if !entriesWithLocation.isEmpty {
                        // Si des événements existent, centrer sur eux
                        adjustRegionToShowAllEntries()
                        print("🗺️ JournalMapView: Centrage sur les événements existants")
                    } else {
                        // Sinon, utiliser la région par défaut intelligente
                        mapRegion = getDefaultMapRegion()
                        print("🗺️ JournalMapView: Utilisation région par défaut intelligente")
                    }
                    hasInitializedMap = true
                }
            }
            
            // Overlay pour les contrôles en haut
            VStack {
                // Boutons de navigation avec bulles intégrées (conditionnel)
                if showBackButton {
                    HStack {
                        Button(action: {
                            dismiss()
                        }) {
                            HStack(spacing: 8) {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundColor(.white)
                                
                                Text("back".localized)
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 20)
                                    .fill(Color.black.opacity(0.7))
                            )
                        }
                        
                        Spacer()
                        
                        // NOUVEAU: Bulles d'information à droite du bouton retour (verticales)
                        if !entriesWithLocation.isEmpty {
                            VStack(spacing: 6) {
                                // Bulle pays en haut
                                if uniqueCountriesCount > 0 {
                                    LocationInfoBubble(
                                        count: uniqueCountriesCount,
                                        label: uniqueCountriesCount == 1 ? "country".localized : "countries".localized
                                    )
                                }
                                
                                // Bulle villes en dessous
                                if uniqueCitiesCount > 0 {
                                    LocationInfoBubble(
                                        count: uniqueCitiesCount,
                                        label: uniqueCitiesCount == 1 ? "city".localized : "cities".localized
                                    )
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 60) // Espace pour la status bar
                } else {
                    // Quand pas de bouton retour, bulles en haut à droite
                    if !entriesWithLocation.isEmpty {
                        HStack {
                            Spacer()
                            
                            VStack(spacing: 6) {
                                // Bulle pays en haut
                                if uniqueCountriesCount > 0 {
                                    LocationInfoBubble(
                                        count: uniqueCountriesCount,
                                        label: uniqueCountriesCount == 1 ? "country".localized : "countries".localized
                                    )
                                }
                                
                                // Bulle villes en dessous
                                if uniqueCitiesCount > 0 {
                                    LocationInfoBubble(
                                        count: uniqueCitiesCount,
                                        label: uniqueCitiesCount == 1 ? "city".localized : "cities".localized
                                    )
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 60) // Espace pour la status bar
                    }
                }
                
                Spacer()
            }
            
            // Message d'aide si aucune entrée (centré sur la carte)
            if entriesWithLocation.isEmpty {
                Button(action: {
                    // Action pour créer un nouvel événement
                    // On peut laisser vide ou rediriger vers la création
                }) {
                    VStack(spacing: 16) {
                        // Contenu textuel centré
                        VStack(spacing: 8) {
                            Text("add_journal_events".localized)
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                            
                            Text("memories_appear_map".localized)
                                .font(.system(size: 16))
                                .foregroundColor(.black.opacity(0.7))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 30)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 24)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color.white.opacity(isCardHovered ? 1.0 : 0.95))
                            .shadow(
                                color: Color.black.opacity(isCardHovered ? 0.15 : 0.1), 
                                radius: isCardHovered ? 12 : 8, 
                                x: 0, 
                                y: isCardHovered ? 4 : 2
                            )
                    )
                    .scaleEffect(isCardHovered ? 1.02 : 1.0)
                    .animation(.easeInOut(duration: 0.2), value: isCardHovered)
                }
                .buttonStyle(PlainButtonStyle())
                .onLongPressGesture(minimumDuration: 0, maximumDistance: .infinity, pressing: { pressing in
                    withAnimation(.easeInOut(duration: 0.2)) {
                        isCardHovered = pressing
                    }
                }, perform: {})
                .padding(.horizontal, 40)
            }
        }
        .navigationBarHidden(true)
        .sheet(item: $selectedEntry) { entry in
            JournalEntryDetailView(entry: entry)
                .onAppear {
                    print("🔍 JournalMapView: Présentation JournalEntryDetailView pour: '\(entry.title)' (ID: \(entry.id))")
                    print("🔍 JournalMapView: JournalEntryDetailView sheet est apparue")
                }
                .onDisappear {
                    print("🔍 JournalMapView: JournalEntryDetailView sheet a disparu")
                    selectedEntry = nil
                    print("🔍 JournalMapView: selectedEntry remis à nil")
                }
        }
        .sheet(item: $selectedCluster) { cluster in
            ClusterDetailView(cluster: cluster) { entry in
                selectedCluster = nil
                selectedEntry = entry
                showingEntryDetail = true
            }
        }
    }
    
    // MARK: - Computed Properties
    
    private var uniqueCountries: Set<String> {
        Set(entriesWithLocation.compactMap { $0.location?.country })
    }
    
    // MARK: - Methods
    
    private func adjustRegionToShowAllEntries() {
        guard !entriesWithLocation.isEmpty else { return }
        
        let coordinates = entriesWithLocation.compactMap { $0.location?.coordinate }
        
        if coordinates.count == 1 {
            // Une seule entrée, centrer dessus
            mapRegion.center = coordinates.first!
            mapRegion.span = MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
        } else {
            // Plusieurs entrées, calculer la région qui les englobe toutes
            let latitudes = coordinates.map { $0.latitude }
            let longitudes = coordinates.map { $0.longitude }
            
            let minLat = latitudes.min()!
            let maxLat = latitudes.max()!
            let minLon = longitudes.min()!
            let maxLon = longitudes.max()!
            
            let centerLat = (minLat + maxLat) / 2
            let centerLon = (minLon + maxLon) / 2
            
            let spanLat = (maxLat - minLat) * 1.3 // Marge de 30%
            let spanLon = (maxLon - minLon) * 1.3
            
            mapRegion.center = CLLocationCoordinate2D(latitude: centerLat, longitude: centerLon)
            mapRegion.span = MKCoordinateSpan(
                latitudeDelta: max(spanLat, 0.01),
                longitudeDelta: max(spanLon, 0.01)
            )
        }
    }
    
    // MARK: - Stable Clustering Logic (Sans effets de bord)
    
    private func createStableClusters(from entries: [JournalEntry], zoomLevel: Double) -> [JournalCluster] {
        guard !entries.isEmpty else { return [] }
        
        // Distance de clustering basée sur le niveau de zoom - LOGIQUE CORRIGÉE
        // Plus on zoome (zoomLevel petit), plus la distance doit être petite
        let clusterDistance: Double = {
            if zoomLevel > 15.0 { return 2.0 }      // Vue monde = clustering maximal (continents)
            else if zoomLevel > 10.0 { return 1.2 } // Vue pays = clustering très large
            else if zoomLevel > 5.0 { return 0.6 }  // Vue région = clustering large  
            else if zoomLevel > 2.0 { return 0.2 }  // Vue ville = clustering moyen
            else if zoomLevel > 1.0 { return 0.08 } // Vue quartier = clustering réduit
            else if zoomLevel > 0.5 { return 0.03 } // Vue rue = clustering très réduit
            else if zoomLevel > 0.2 { return 0.015 }// Vue détaillée = clustering minimal
            else { return 0.008 }                   // Zoom maximum = presque pas de clustering
        }()
        
        print("🗺️ Clustering: zoomLevel = \(zoomLevel), distance = \(clusterDistance)")
        
        var clusters: [JournalCluster] = []
        var processedEntries: Set<String> = []
        
        // NOUVEAU: Pour les vues très dézoomées, utiliser un clustering par zone géographique
        if zoomLevel > 8.0 {
            return createGeographicalClusters(from: entries, zoomLevel: zoomLevel)
        }
        
        for entry in entries {
            guard !processedEntries.contains(entry.id),
                  let location = entry.location else { continue }
            
            // Trouver tous les événements proches de celui-ci
            var nearbyEntries: [JournalEntry] = [entry]
            processedEntries.insert(entry.id)
            
            for otherEntry in entries {
                guard !processedEntries.contains(otherEntry.id),
                      let otherLocation = otherEntry.location else { continue }
                
                let distance = location.coordinate.distance(to: otherLocation.coordinate)
                
                if distance < clusterDistance {
                    nearbyEntries.append(otherEntry)
                    processedEntries.insert(otherEntry.id)
                }
            }
            
            // Créer le cluster avec la position centrale et ID stable
            let centerCoordinate = calculateCenterCoordinate(for: nearbyEntries)
            let stableId = JournalCluster.createId(from: nearbyEntries)
            
            let cluster = JournalCluster(
                id: stableId,
                coordinate: centerCoordinate,
                entries: nearbyEntries.sorted { $0.eventDate > $1.eventDate } // Plus récents en premier
            )
            
            clusters.append(cluster)
        }
        
        return clusters
    }
    
    // NOUVEAU: Clustering géographique intelligent pour les vues très dézoomées
    private func createGeographicalClusters(from entries: [JournalEntry], zoomLevel: Double) -> [JournalCluster] {
        guard !entries.isEmpty else { return [] }
        
        print("🌍 Clustering géographique activé pour zoomLevel = \(zoomLevel)")
        
        // Grouper par pays d'abord
        let entriesByCountry = Dictionary(grouping: entries.filter { $0.location != nil }) { entry in
                            entry.location?.country ?? "unknown_location".localized
        }
        
        var clusters: [JournalCluster] = []
        
        for (country, countryEntries) in entriesByCountry {
            // MODIFICATION: Toujours grouper par ville, même en vue monde
            // Cela permet de voir la répartition géographique même quand on dézoome
            let entriesByCity = Dictionary(grouping: countryEntries) { entry in
                entry.location?.city ?? "unknown_city".localized
            }
            
            for (city, cityEntries) in entriesByCity {
                let centerCoordinate = calculateCenterCoordinate(for: cityEntries)
                let stableId = JournalCluster.createId(from: cityEntries)
                
                let cluster = JournalCluster(
                    id: stableId,
                    coordinate: centerCoordinate,
                    entries: cityEntries.sorted { $0.eventDate > $1.eventDate }
                )
                clusters.append(cluster)
                print("🏙️ Cluster ville créé: \(city), \(country) avec \(cityEntries.count) événements")
            }
        }
        
        return clusters
    }
    
    private func calculateCenterCoordinate(for entries: [JournalEntry]) -> CLLocationCoordinate2D {
        let coordinates = entries.compactMap { $0.location?.coordinate }
        
        if coordinates.count == 1 {
            return coordinates.first!
        }
        
        let totalLat = coordinates.reduce(0) { $0 + $1.latitude }
        let totalLon = coordinates.reduce(0) { $0 + $1.longitude }
        
        return CLLocationCoordinate2D(
            latitude: totalLat / Double(coordinates.count),
            longitude: totalLon / Double(coordinates.count)
        )
    }
}

// MARK: - Optimized Journal Map Annotation View
struct OptimizedJournalMapAnnotationView: View {
    let entry: JournalEntry
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 4) {
                // Image ou icône avec titre
                VStack(spacing: 6) {
                    if let imageURL = entry.imageURL, !imageURL.isEmpty {
                        // Version ultra-optimisée pour les cartes
                        CachedMapImageView(imageURL: imageURL, size: 60)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.white, lineWidth: 2)
                            )
                            .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
                    } else {
                        // Affichage sans image (juste icône)
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color(hex: "#FD267A"))
                            .frame(width: 60, height: 60)
                            .overlay(
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(.white)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.white, lineWidth: 2)
                            )
                            .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
                    }
                    
                    // Titre
                    Text(entry.title)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Color.white)
                                .shadow(color: .black.opacity(0.2), radius: 2, x: 0, y: 1)
                        )
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 100)
                }
            }
        }
        .scaleEffect(0.9) // Légèrement plus petit pour ne pas encombrer
    }
}

// MARK: - Optimized Cluster Annotation View
struct OptimizedClusterAnnotationView: View {
    let cluster: JournalCluster
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            ZStack {
                // Images empilées des événements (effet de superposition)
                ForEach(Array(cluster.entries.prefix(2).enumerated()), id: \.element.id) { index, entry in
                    if let imageURL = entry.imageURL, !imageURL.isEmpty {
                        // Version optimisée pour les clusters
                        CachedMapImageView(imageURL: imageURL, size: 50)
                            .offset(x: CGFloat(index * 8), y: CGFloat(-index * 8))
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.white, lineWidth: 2)
                                    .offset(x: CGFloat(index * 8), y: CGFloat(-index * 8))
                            )
                    } else {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color(hex: "#FD267A"))
                            .frame(width: 50, height: 50)
                            .overlay(
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(.white)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Color.white, lineWidth: 2)
                            )
                            .offset(x: CGFloat(index * 8), y: CGFloat(-index * 8))
                    }
                }
                
                // Badge avec le nombre d'événements
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Text("\(cluster.count) " + "events_count".localized)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 24, height: 24)
                            .background(
                                Circle()
                                    .fill(Color(hex: "#FD267A"))
                                    .overlay(
                                        Circle()
                                            .stroke(Color.white, lineWidth: 2)
                                    )
                            )
                            .offset(x: 8, y: 8)
                    }
                }
                .frame(width: 58, height: 58) // Taille ajustée pour l'offset
            }
            .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
        }
    }
}

// MARK: - Ultra-optimized Cached Map Image View
struct CachedMapImageView: View {
    let imageURL: String
    let size: CGFloat
    
    @State private var image: UIImage?
    @State private var isLoading = false
    
    private let cacheKey: String
    
    init(imageURL: String, size: CGFloat) {
        self.imageURL = imageURL
        self.size = size
        self.cacheKey = imageURL
        
        // ✅ NOUVEAU: Initialisation synchrone du cache dans init
        self._image = State(initialValue: ImageCacheService.shared.getCachedImage(for: imageURL))
    }
    
    var body: some View {
        Group {
            if let image = image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: size, height: size)
                    .clipped()
                    .cornerRadius(8)
            } else if isLoading {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: size, height: size)
                    .overlay(
                        ProgressView()
                            .scaleEffect(0.7)
                    )
            } else {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.2))
                    .frame(width: size, height: size)
                    .overlay(
                        Image(systemName: "photo")
                            .font(.title2)
                            .foregroundColor(.gray)
                    )
            }
        }
        .task {
            // ✅ NOUVEAU: Utiliser task au lieu de onAppear
            await loadImageIfNeeded()
        }
        .id(cacheKey) // Assurer la stabilité de l'ID
    }
    
    private func loadImageIfNeeded() async {
        // ✅ Si l'image est déjà chargée (depuis le cache synchrone), ne rien faire
        guard image == nil, !isLoading else { return }
        
        // ✅ NOUVEAU: Vérification asynchrone du cache
        if let cachedImage = ImageCacheService.shared.getCachedImage(for: cacheKey) {
            await MainActor.run {
                self.image = cachedImage
            }
            return
        }
        
        // Charger une seule fois
        await MainActor.run {
            self.isLoading = true
        }
        
        do {
            let loadedImage = try await loadImageDirect(from: imageURL)
            
            await MainActor.run {
                self.image = loadedImage
                self.isLoading = false
                // Mettre en cache
                ImageCacheService.shared.cacheImage(loadedImage, for: cacheKey)
            }
        } catch {
            await MainActor.run {
                self.isLoading = false
            }
        }
    }
    
    private func loadImageDirect(from urlString: String) async throws -> UIImage {
        guard let url = URL(string: urlString) else {
            throw AsyncImageError.invalidData
        }
        
        let (data, _) = try await URLSession.shared.data(from: url)
        
        guard let image = UIImage(data: data) else {
            throw AsyncImageError.invalidData
        }
        
        return image
    }
}

// MARK: - Cluster Detail View (liste des événements du cluster)
struct ClusterDetailView: View {
    let cluster: JournalCluster
    let onSelectEntry: (JournalEntry) -> Void
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Header
                VStack(spacing: 8) {
                    Text("\(cluster.count) " + "events_count".localized)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.black)
                    
                    if let location = cluster.entries.first?.location {
                        Text("\(location.city ?? ""), \(location.country ?? "")")
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.7))
                    }
                }
                .padding(.vertical, 20)
                
                // Liste des événements
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(cluster.entries) { entry in
                            Button(action: {
                                onSelectEntry(entry)
                            }) {
                                HStack(spacing: 16) {
                                    // Image ou icône
                                    if let imageURL = entry.imageURL, !imageURL.isEmpty {
                                        AsyncImageView(
                                            imageURL: imageURL,
                                            width: 60,
                                            height: 60,
                                            cornerRadius: 8
                                        )
                                    } else {
                                        RoundedRectangle(cornerRadius: 8)
                                            .fill(Color(hex: "#FD267A"))
                                            .frame(width: 60, height: 60)
                                            .overlay(
                                                Image(systemName: "heart.fill")
                                                    .font(.system(size: 20))
                                                    .foregroundColor(.white)
                                            )
                                    }
                                    
                                    // Informations
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(entry.title)
                                            .font(.system(size: 18, weight: .semibold))
                                            .foregroundColor(.black)
                                            .lineLimit(1)
                                        
                                        if !entry.description.isEmpty {
                                            Text(entry.description)
                                                .font(.system(size: 14))
                                                .foregroundColor(.black.opacity(0.7))
                                                .lineLimit(2)
                                        }
                                        
                                        Text(entry.eventDate.formatted(date: .abbreviated, time: .shortened))
                                            .font(.system(size: 12))
                                            .foregroundColor(.black.opacity(0.5))
                                    }
                                    
                                    Spacer()
                                    
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 14))
                                        .foregroundColor(.black.opacity(0.3))
                                }
                                .padding(16)
                                .background(
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(Color.white)
                                        .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                }
            }
            .background(Color(red: 0.97, green: 0.97, blue: 0.98))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("close".localized) {
                        // La fermeture est gérée par le parent
                    }
                }
            }
        }
    }
}

// MARK: - Extensions

extension CLLocationCoordinate2D {
    func distance(to coordinate: CLLocationCoordinate2D) -> Double {
        let location1 = CLLocation(latitude: self.latitude, longitude: self.longitude)
        let location2 = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        return location1.distance(from: location2) / 1000.0 // Retourner en kilomètres
    }
}

// MARK: - Location Info Bubble Component
struct LocationInfoBubble: View {
    let count: Int
    let label: String
    
    var body: some View {
        HStack(spacing: 6) {
            Text("\(count)")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.black)
            
            Text(label)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.black)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.white.opacity(0.95))
                .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 2)
        )
    }
}

#Preview {
    JournalMapView()
        .environmentObject(AppState())
} 