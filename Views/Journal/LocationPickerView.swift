import SwiftUI
import MapKit
import CoreLocation

struct LocationPickerView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedLocation: JournalLocation?
    
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 48.8566, longitude: 2.3522), // Sera remplacé par getDefaultPickerRegion()
        span: MKCoordinateSpan(latitudeDelta: 0.1, longitudeDelta: 0.1)
    )
    @State private var searchText = ""
    @State private var isSearching = false
    @State private var searchResults: [MKMapItem] = []
    @State private var selectedCoordinate: CLLocationCoordinate2D?
    @State private var locationManager = CLLocationManager()
    @State private var currentLocationName = ""
    @State private var debounceTimer: Timer?
    
    var body: some View {
        NavigationView {
            ZStack {
                backgroundView
                
                VStack(spacing: 0) {
                    searchSection
                    mapSection
                }
                
                floatingButton
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            toolbarContent
        }
        .onAppear {
            setupLocationManager()
            if let existingLocation = selectedLocation {
                // Si une location existe déjà, l'utiliser
                region.center = existingLocation.coordinate
                selectedCoordinate = existingLocation.coordinate
                currentLocationName = existingLocation.displayName
            } else {
                // Sinon, utiliser la région par défaut intelligente
                region = getDefaultPickerRegion()
                print("📍 LocationPicker: Région par défaut appliquée")
            }
        }
    }
    
    // MARK: - View Components
    
    private var backgroundView: some View {
        Color(red: 0.1, green: 0.02, blue: 0.05)
            .ignoresSafeArea()
    }
    
    private var searchSection: some View {
        VStack(spacing: 12) {
            searchBar
            searchResultsList
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
    }
    
    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.white.opacity(0.6))
            
            TextField("search_placeholder".localized, text: $searchText)
                .foregroundColor(.white)
                .onSubmit {
                    searchLocation()
                }
            
            if !searchText.isEmpty {
                Button(action: {
                    searchText = ""
                    searchResults = []
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.white.opacity(0.6))
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.1))
        )
    }
    
    @ViewBuilder
    private var searchResultsList: some View {
        if !searchResults.isEmpty {
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(searchResults, id: \.self) { item in
                        SearchResultRow(item: item) {
                            selectSearchResult(item)
                        }
                    }
                }
            }
            .frame(maxHeight: 200)
        }
    }
    
    private var mapSection: some View {
        ZStack {
            mapView
            centerMarker
            locationInfo
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private var mapView: some View {
        MapViewRepresentable(region: $region, onRegionChange: { newRegion in
            handleRegionChange(newRegion)
        })
    }
    
    private var centerMarker: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                Image(systemName: "mappin")
                    .font(.system(size: 30))
                    .foregroundColor(Color(hex: "#FD267A"))
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
    
    @ViewBuilder
    private var locationInfo: some View {
        if !currentLocationName.isEmpty {
            VStack {
                Spacer()
                
                VStack(spacing: 8) {
                    Text("selected_location".localized)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.black)
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                    
                    Text(currentLocationName)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                }
                .padding(16)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.black.opacity(0.8))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.white.opacity(0.2), lineWidth: 1)
                        )
                )
                .padding(.horizontal, 20)
                .padding(.bottom, 100)
            }
        }
    }
    
    private var floatingButton: some View {
        VStack {
            Spacer()
            
            Button("select".localized) {
                confirmSelection()
            }
            .font(.system(size: 18, weight: .semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(
                RoundedRectangle(cornerRadius: 28)
                    .fill(selectedCoordinate != nil ? Color(hex: "#FD267A") : Color.gray.opacity(0.3))
            )
            .disabled(selectedCoordinate == nil)
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
    }
    
    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            Button("cancel".localized) {
                dismiss()
            }
            .foregroundColor(.white)
        }
        
        ToolbarItem(placement: .principal) {
            Text("choose_location".localized)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.black)
                .padding(.horizontal, 20)
                .padding(.top, 20)
        }
        
        ToolbarItem(placement: .navigationBarTrailing) {
            Button(action: {
                requestCurrentLocation()
            }) {
                Image(systemName: "location.circle")
                    .font(.system(size: 20))
                    .foregroundColor(.white)
            }
        }
    }
    
    // MARK: - Methods
    
    // NOUVEAU: Logique intelligente pour la région par défaut du picker
    private func getDefaultPickerRegion() -> MKCoordinateRegion {
        // 1. Priorité : Utiliser la localisation actuelle si disponible
        if let currentLocation = locationManager.location {
            print("📍 LocationPicker: Utilisation localisation actuelle")
            return MKCoordinateRegion(
                center: currentLocation.coordinate,
                span: MKCoordinateSpan(latitudeDelta: 0.1, longitudeDelta: 0.1)
            )
        }
        
        // 2. Fallback : Utiliser la locale/région du téléphone
        let locale = Locale.current
        let languageCode = locale.languageCode ?? "en"
        let regionCode = locale.regionCode ?? "US"
        
        print("📍 LocationPicker: Locale - Langue: \(languageCode), Région: \(regionCode)")
        
        let defaultRegion: MKCoordinateRegion
        
        switch (languageCode, regionCode) {
        // États-Unis
        case ("en", "US"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 39.8283, longitude: -98.5795), // Centre des États-Unis
                span: MKCoordinateSpan(latitudeDelta: 10.0, longitudeDelta: 10.0)
            )
            
        // Canada
        case ("en", "CA"), ("fr", "CA"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 56.1304, longitude: -106.3468), // Centre du Canada
                span: MKCoordinateSpan(latitudeDelta: 10.0, longitudeDelta: 10.0)
            )
            
        // Royaume-Uni
        case ("en", "GB"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 55.3781, longitude: -3.4360), // Centre du Royaume-Uni
                span: MKCoordinateSpan(latitudeDelta: 2.0, longitudeDelta: 2.0)
            )
            
        // Australie
        case ("en", "AU"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: -25.2744, longitude: 133.7751), // Centre de l'Australie
                span: MKCoordinateSpan(latitudeDelta: 10.0, longitudeDelta: 10.0)
            )
            
        // France
        case ("fr", _):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 46.2276, longitude: 2.2137), // Centre de la France
                span: MKCoordinateSpan(latitudeDelta: 2.0, longitudeDelta: 2.0)
            )
            
        // Espagne
        case ("es", "ES"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 40.4637, longitude: -3.7492), // Centre de l'Espagne
                span: MKCoordinateSpan(latitudeDelta: 2.0, longitudeDelta: 2.0)
            )
            
        // Allemagne
        case ("de", "DE"):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 51.1657, longitude: 10.4515), // Centre de l'Allemagne
                span: MKCoordinateSpan(latitudeDelta: 2.0, longitudeDelta: 2.0)
            )
            
        // Europe par défaut (pour les autres pays européens)
        case (_, let regionCodeValue) where ["BE", "NL", "CH", "AT", "PT", "DK", "SE", "NO", "FI", "IT"].contains(regionCodeValue):
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 54.5260, longitude: 15.2551), // Centre de l'Europe
                span: MKCoordinateSpan(latitudeDelta: 5.0, longitudeDelta: 5.0)
            )
            
        // Vue monde par défaut pour les autres cas
        default:
            defaultRegion = MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 20.0, longitude: 0.0), // Vue monde centrée
                span: MKCoordinateSpan(latitudeDelta: 30.0, longitudeDelta: 30.0)
            )
        }
        
        return defaultRegion
    }
    
    private func setupLocationManager() {
        locationManager.requestWhenInUseAuthorization()
        
        // Si l'autorisation est déjà accordée, utiliser immédiatement la localisation actuelle
        if locationManager.authorizationStatus == .authorizedWhenInUse || 
           locationManager.authorizationStatus == .authorizedAlways {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                self.requestCurrentLocation()
            }
        }
    }
    
    private func requestCurrentLocation() {
        guard locationManager.authorizationStatus == .authorizedWhenInUse ||
              locationManager.authorizationStatus == .authorizedAlways else {
            locationManager.requestWhenInUseAuthorization()
            return
        }
        
        if let currentLocation = locationManager.location {
            region.center = currentLocation.coordinate
            selectedCoordinate = currentLocation.coordinate
            reverseGeocode(coordinate: currentLocation.coordinate)
        }
    }
    
    private func searchLocation() {
        guard !searchText.isEmpty else { return }
        
        isSearching = true
        
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = searchText
        request.region = region
        
        let search = MKLocalSearch(request: request)
        search.start { response, error in
            DispatchQueue.main.async {
                self.isSearching = false
                
                if let response = response {
                    self.searchResults = response.mapItems
                } else {
                    self.searchResults = []
                }
            }
        }
    }
    
    private func selectSearchResult(_ item: MKMapItem) {
        let coordinate = item.placemark.coordinate
        region.center = coordinate
        selectedCoordinate = coordinate
                        currentLocationName = item.name ?? item.placemark.title ?? "selected_location_custom".localized
        searchResults = []
        searchText = ""
    }
    
    private func selectCoordinate(_ coordinate: CLLocationCoordinate2D) {
        selectedCoordinate = coordinate
        reverseGeocode(coordinate: coordinate)
    }
    
    private func handleRegionChange(_ newRegion: MKCoordinateRegion) {
        let currentCenter = newRegion.center
        
        // Vérifier si le centre a vraiment changé (éviter les appels inutiles)
        if let lastCoordinate = selectedCoordinate {
            let distance = sqrt(
                pow(currentCenter.latitude - lastCoordinate.latitude, 2) +
                pow(currentCenter.longitude - lastCoordinate.longitude, 2)
            )
            
            // Si le changement est minime, ne rien faire
            if distance < 0.0001 {
                return
            }
        }
        
        // Annuler le timer précédent
        debounceTimer?.invalidate()
        
        // Créer un nouveau timer avec délai
        debounceTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { _ in
            DispatchQueue.main.async {
                self.selectedCoordinate = currentCenter
                self.reverseGeocode(coordinate: currentCenter)
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
                        placemark.name,
                        placemark.locality,
                        placemark.country
                    ].compactMap { $0 }
                    
                    self.currentLocationName = components.joined(separator: ", ")
                } else {
                    self.currentLocationName = "custom_location".localized
                }
            }
        }
    }
    
    private func confirmSelection() {
        guard let coordinate = selectedCoordinate else { return }
        
        let geocoder = CLGeocoder()
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        
        geocoder.reverseGeocodeLocation(location) { placemarks, error in
            DispatchQueue.main.async {
                if let placemark = placemarks?.first {
                    self.selectedLocation = JournalLocation(
                        coordinate: coordinate,
                        address: placemark.name,
                        city: placemark.locality,
                        country: placemark.country
                    )
                } else {
                    self.selectedLocation = JournalLocation(
                        coordinate: coordinate,
                        address: self.currentLocationName,
                        city: nil,
                        country: nil
                    )
                }
                
                self.dismiss()
            }
        }
    }
}

// MARK: - Search Result Row
struct SearchResultRow: View {
    let item: MKMapItem
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.name ?? "location_place".localized)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white)
                    
                    if let address = item.placemark.title {
                        Text(address)
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.7))
                    }
                }
                
                Spacer()
                
                Image(systemName: "location")
                    .foregroundColor(Color(hex: "#FD267A"))
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.white.opacity(0.05))
            )
        }
    }
}

// MARK: - MapView Representable
struct MapViewRepresentable: UIViewRepresentable {
    @Binding var region: MKCoordinateRegion
    let onRegionChange: (MKCoordinateRegion) -> Void
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.region = region
        mapView.showsUserLocation = false
        mapView.isUserInteractionEnabled = true
        return mapView
    }
    
    func updateUIView(_ mapView: MKMapView, context: Context) {
        if mapView.region.center.latitude != region.center.latitude ||
           mapView.region.center.longitude != region.center.longitude {
            mapView.setRegion(region, animated: true)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        let parent: MapViewRepresentable
        
        init(_ parent: MapViewRepresentable) {
            self.parent = parent
        }
        
        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            DispatchQueue.main.async {
                self.parent.region = mapView.region
                self.parent.onRegionChange(mapView.region)
            }
        }
    }
}

// MARK: - Extensions pour améliorer la lisibilité des logs
extension CLAuthorizationStatus {
    var localizedDescription: String {
        switch self {
        case .notDetermined: return "Non déterminé"
        case .restricted: return "Restreint"
        case .denied: return "Refusé"
        case .authorizedAlways: return "Autorisé (toujours)"
        case .authorizedWhenInUse: return "Autorisé (en utilisation)"
        @unknown default: return "Statut inconnu"
        }
    }
}

#Preview {
    LocationPickerView(selectedLocation: .constant(nil))
} 