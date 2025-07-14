import SwiftUI
import MapKit
import CoreLocation

struct LocationPickerView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedLocation: JournalLocation?
    
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 48.8566, longitude: 2.3522), // Paris par défaut
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
                region.center = existingLocation.coordinate
                selectedCoordinate = existingLocation.coordinate
                currentLocationName = existingLocation.displayName
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
    
    private func setupLocationManager() {
        locationManager.requestWhenInUseAuthorization()
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

#Preview {
    LocationPickerView(selectedLocation: .constant(nil))
} 