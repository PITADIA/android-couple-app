import SwiftUI
import MapKit

struct JournalMapView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var selectedEntry: JournalEntry?
    @State private var mapRegion = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 46.2276, longitude: 2.2137), // Centre de la France
        span: MKCoordinateSpan(latitudeDelta: 10, longitudeDelta: 10)
    )
    @State private var showingEntryDetail = false
    @State private var isCardHovered = false
    
    // NOUVEAU: Observer directement le JournalService
    @ObservedObject private var journalService = JournalService.shared
    
    // Filtrer les entrées qui ont une localisation
    private var entriesWithLocation: [JournalEntry] {
        journalService.entries.filter { $0.location != nil }
    }
    
    var body: some View {
        ZStack {
            // Carte en plein écran
            Map(coordinateRegion: $mapRegion, 
                annotationItems: entriesWithLocation) { entry in
                MapAnnotation(coordinate: entry.location!.coordinate) {
                    JournalMapAnnotationView(entry: entry) {
                        selectedEntry = entry
                        showingEntryDetail = true
                    }
                }
            }
            .ignoresSafeArea(.all) // Plein écran
            .onAppear {
                if !entriesWithLocation.isEmpty {
                    adjustRegionToShowAllEntries()
                }
            }
            
            // Overlay pour le bouton retour en haut
            VStack {
                HStack {
                    Button(action: {
                        dismiss()
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                            
                            Text("Retour")
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
                }
                .padding(.horizontal, 20)
                .padding(.top, 60) // Espace pour la status bar
                
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
                            Text("Ajoutez des événements à votre journal")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                            
                            Text("Tous vos souvenirs passés ensemble apparaîtront sur cette carte")
                                .font(.system(size: 14))
                                .foregroundColor(.gray)
                                .multilineTextAlignment(.center)
                                .lineLimit(nil)
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
}

// MARK: - Journal Map Annotation View (pour les pins sur la carte)
struct JournalMapAnnotationView: View {
    let entry: JournalEntry
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 4) {
                // Image ou icône avec titre
                VStack(spacing: 6) {
                    if let imageURL = entry.imageURL, !imageURL.isEmpty {
                        // Affichage avec image
                        AsyncImageView(
                            imageURL: imageURL,
                            width: 60,
                            height: 60,
                            cornerRadius: 8
                        )
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

#Preview {
    JournalMapView()
        .environmentObject(AppState())
} 