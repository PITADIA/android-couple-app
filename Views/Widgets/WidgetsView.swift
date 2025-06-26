import SwiftUI
import MapKit

struct WidgetsView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedWidget: WidgetType = .countdown
    @State private var currentMessageIndex = 0
    @State private var timer: Timer?
    
    // Utiliser le WidgetService global d'AppState
    private var widgetService: WidgetService? {
        return appState.widgetService
    }
    
    enum WidgetType: String, CaseIterable {
        case countdown = "Compteur"
        case daysTotal = "Jours ensemble"
        case distance = "Notre distance"
        
        var icon: String {
            switch self {
            case .countdown: return "timer"
            case .daysTotal: return "heart.fill"
            case .distance: return "location.fill"
            }
        }
        
        var requiresPremium: Bool {
            switch self {
            case .countdown, .daysTotal, .distance:
                return false
            }
        }
    }
    
    private var canAccessPremiumWidgets: Bool {
        return true // Tous les widgets sont maintenant gratuits
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                        ForEach(WidgetType.allCases, id: \.self) { type in
                            WidgetSelectorButton(
                                type: type,
                                isSelected: selectedWidget == type,
                                canAccess: !type.requiresPremium || canAccessPremiumWidgets
                            ) {
                                if type.requiresPremium && !canAccessPremiumWidgets {
                                    print("🔒 WidgetsView: Accès widget premium bloqué - Affichage paywall")
                                    appState.freemiumManager?.handleDistanceWidgetAccess {
                                        selectedWidget = type
                                    }
                                } else {
                                    selectedWidget = type
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.vertical, 16)
                .padding(.top, 20)
                
                ScrollView {
                    VStack(spacing: 20) {
                        switch selectedWidget {
                        case .countdown:
                            CountdownWidgetView(stats: widgetService?.relationshipStats)
                        case .daysTotal:
                            DaysTotalWidgetView(stats: widgetService?.relationshipStats)
                        case .distance:
                            DistanceWidgetView(
                                distanceInfo: widgetService?.distanceInfo,
                                currentMessageIndex: $currentMessageIndex
                            )
                        }
                        
                        Spacer(minLength: 100)
                    }
                    .padding(.horizontal, 20)
                }
            }
            .background(
                // Fond gris clair identique à l'app avec dégradé rose doux en arrière-plan
                ZStack {
                    Color(red: 0.97, green: 0.97, blue: 0.98)
                        .ignoresSafeArea(.all)
                    
                    LinearGradient(
                        gradient: Gradient(colors: [
                            Color(hex: "#FD267A").opacity(0.03),
                            Color(hex: "#FF655B").opacity(0.02)
                        ]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .ignoresSafeArea()
                }
            )
            .navigationTitle("Widgets")
            .navigationBarTitleDisplayMode(.large)
            .foregroundColor(.black)
            .onAppear {
                widgetService?.refreshData()
                startMessageRotation()
                
                if selectedWidget.requiresPremium && !canAccessPremiumWidgets {
                    selectedWidget = .countdown
                }
            }
            .onDisappear {
                timer?.invalidate()
            }
            .onChange(of: canAccessPremiumWidgets) { _, hasAccess in
                if !hasAccess && selectedWidget.requiresPremium {
                    selectedWidget = .countdown
                }
            }
        }
    }
    
    private func startMessageRotation() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { _ in
            if let distanceInfo = widgetService?.distanceInfo,
               !distanceInfo.messages.isEmpty {
                withAnimation(.easeInOut(duration: 0.5)) {
                    currentMessageIndex = (currentMessageIndex + 1) % distanceInfo.messages.count
                }
            }
        }
    }
}

struct PremiumBlockedWidgetView: View {
    let widgetType: WidgetsView.WidgetType
    let onPremiumTap: () -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            VStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(Color.gray.opacity(0.2))
                        .frame(width: 80, height: 80)
                    
                    Image(systemName: "lock.fill")
                        .font(.system(size: 40))
                        .foregroundColor(.gray)
                }
                
                VStack(spacing: 8) {
                    Text("Widget Premium")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.black)
                    
                    Text("Ce widget nécessite un abonnement premium")
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                }
            }
            
            VStack(spacing: 12) {
                Text("Avec Premium, vous pourrez :")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.black.opacity(0.8))
                
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text("Voir votre distance en temps réel")
                            .font(.system(size: 14))
                            .foregroundColor(.black.opacity(0.7))
                    }
                    
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text("Visualiser vos positions sur une carte")
                            .font(.system(size: 14))
                            .foregroundColor(.black.opacity(0.7))
                    }
                    
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text("Recevoir des messages personnalisés")
                            .font(.system(size: 14))
                            .foregroundColor(.black.opacity(0.7))
                    }
                }
            }
            
            Button(action: onPremiumTap) {
                HStack {
                    Image(systemName: "crown.fill")
                        .font(.system(size: 16))
                    
                    Text("Débloquer Premium")
                        .font(.system(size: 18, weight: .semibold))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    LinearGradient(
                        gradient: Gradient(colors: [
                            Color(hex: "#FD267A"),
                            Color(hex: "#FF6B9D")
                        ]),
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .cornerRadius(25)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
        .padding(.horizontal, 24)
        .background(
            RoundedRectangle(cornerRadius: 24)
                .fill(Color.white)
                .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                .shadow(color: Color.black.opacity(0.04), radius: 1, x: 0, y: 1)
        )
    }
}

struct WidgetSelectorButton: View {
    let type: WidgetsView.WidgetType
    let isSelected: Bool
    let canAccess: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: canAccess ? type.icon : "lock.fill")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(isSelected ? .white : (canAccess ? .black : .gray))
                
                Text(type.rawValue)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(isSelected ? .white : (canAccess ? .black : .gray))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(isSelected ? Color(hex: "#FD267A") : Color.white)
                    .shadow(color: Color.black.opacity(0.08), radius: 4, x: 0, y: 2)
            )
        }
        .disabled(!canAccess)
    }
}

struct CountdownWidgetView: View {
    let stats: RelationshipStats?
    
    var body: some View {
        VStack(spacing: 24) {
            if let stats = stats {
                VStack(spacing: 8) {
                    Text("Temps ensemble")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black.opacity(0.8))
                    
                    Text(stats.countdownText)
                        .font(.system(size: 48, weight: .bold))
                        .foregroundColor(.black)
                        .monospaced()
                    
                    HStack(spacing: 20) {
                        Text("jours")
                        Text("heures")
                        Text("minutes")
                        Text("secondes")
                    }
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.black.opacity(0.6))
                }
                
                VStack(spacing: 8) {
                    Text("\(stats.formattedDuration)")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundColor(.black)
                    
                    if stats.daysToAnniversary > 0 {
                        Text("Plus que \(stats.daysToAnniversary) jours avant votre prochain anniversaire")
                            .font(.system(size: 14))
                            .foregroundColor(.black.opacity(0.6))
                            .multilineTextAlignment(.center)
                    } else {
                        Text("Joyeux anniversaire ! 🎉")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(Color(hex: "#FD267A"))
                    }
                }
            } else {
                VStack(spacing: 16) {
                    Image(systemName: "heart.slash")
                        .font(.system(size: 60))
                        .foregroundColor(.black.opacity(0.3))
                    
                    Text("Aucune date de relation définie")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black.opacity(0.7))
                    
                    Text("Ajoutez votre date de début de relation dans les paramètres")
                        .font(.system(size: 14))
                        .foregroundColor(.black.opacity(0.5))
                        .multilineTextAlignment(.center)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
        .padding(.horizontal, 24)
        .background(
            RoundedRectangle(cornerRadius: 24)
                .fill(Color.white)
                .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                .shadow(color: Color.black.opacity(0.04), radius: 1, x: 0, y: 1)
        )
    }
}

struct DaysTotalWidgetView: View {
    let stats: RelationshipStats?
    
    var body: some View {
        VStack(spacing: 24) {
            if let stats = stats {
                VStack(spacing: 8) {
                    Text("\(stats.daysTotal) jours")
                        .font(.system(size: 48, weight: .bold))
                        .foregroundColor(.black)
                    
                    Text("ensemble")
                        .font(.system(size: 24, weight: .medium))
                        .foregroundColor(.black.opacity(0.8))
                }
                
                Text("Depuis le \(DateFormatter.longDateFormatter.string(from: stats.startDate))")
                    .font(.system(size: 16))
                    .foregroundColor(.black.opacity(0.6))
                    .multilineTextAlignment(.center)
            } else {
                VStack(spacing: 16) {
                    Image(systemName: "heart.slash")
                        .font(.system(size: 60))
                        .foregroundColor(.black.opacity(0.3))
                    
                    Text("Aucune date de relation définie")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black.opacity(0.7))
                    
                    Text("Ajoutez votre date de début de relation dans les paramètres")
                        .font(.system(size: 14))
                        .foregroundColor(.black.opacity(0.5))
                        .multilineTextAlignment(.center)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
        .padding(.horizontal, 24)
        .background(
            RoundedRectangle(cornerRadius: 24)
                .fill(Color.white)
                .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                .shadow(color: Color.black.opacity(0.04), radius: 1, x: 0, y: 1)
        )
    }
}

struct DistanceWidgetView: View {
    let distanceInfo: DistanceInfo?
    @Binding var currentMessageIndex: Int
    
    var body: some View {
        VStack(spacing: 24) {
            if let distanceInfo = distanceInfo {
                mapView
                    .frame(height: 200)
                    .cornerRadius(16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.black.opacity(0.1), lineWidth: 1)
                    )
                
                VStack(spacing: 8) {
                    Text("\(distanceInfo.formattedDistance)")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.black)
                    
                    Text("nous séparent")
                        .font(.system(size: 18))
                        .foregroundColor(.black.opacity(0.7))
                }
                
                if !distanceInfo.messages.isEmpty {
                    VStack(spacing: 12) {
                        Text(distanceInfo.messages[currentMessageIndex])
                            .font(.system(size: 20, weight: .medium))
                            .foregroundColor(Color(hex: "#FD267A"))
                            .multilineTextAlignment(.center)
                            .animation(.easeInOut(duration: 0.5), value: currentMessageIndex)
                    }
                    .frame(height: 50)
                }
                
                Text("Mis à jour \(timeAgoString)")
                    .font(.system(size: 12))
                    .foregroundColor(.black.opacity(0.5))
            } else {
                VStack(spacing: 16) {
                    Image(systemName: "location.slash")
                        .font(.system(size: 60))
                        .foregroundColor(.black.opacity(0.3))
                    
                    Text("Localisation non disponible")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black.opacity(0.7))
                    
                    Text("Activez la localisation pour voir la distance avec votre partenaire")
                        .font(.system(size: 14))
                        .foregroundColor(.black.opacity(0.5))
                        .multilineTextAlignment(.center)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
        .padding(.horizontal, 24)
        .background(
            RoundedRectangle(cornerRadius: 24)
                .fill(Color.white)
                .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                .shadow(color: Color.black.opacity(0.04), radius: 1, x: 0, y: 1)
        )
    }
    
    private var mapView: some View {
        Map(coordinateRegion: .constant(mapRegion),
            annotationItems: mapAnnotations) { annotation in
            MapPin(coordinate: annotation.coordinate, tint: annotation.color)
        }
    }
    
    private var mapAnnotations: [MapPinData] {
        guard let distanceInfo = distanceInfo else { return [] }
        
        return [
            MapPinData(
                id: "current",
                coordinate: distanceInfo.currentUserLocation.coordinate,
                color: Color(hex: "#FD267A")
            ),
            MapPinData(
                id: "partner",
                coordinate: distanceInfo.partnerLocation.coordinate,
                color: Color(hex: "#FF6B9D")
            )
        ]
    }
    
    private var mapRegion: MKCoordinateRegion {
        guard let distanceInfo = distanceInfo else {
            return MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 46.2276, longitude: 2.2137),
                span: MKCoordinateSpan(latitudeDelta: 10, longitudeDelta: 10)
            )
        }
        
        let currentLat = distanceInfo.currentUserLocation.latitude
        let currentLng = distanceInfo.currentUserLocation.longitude
        let partnerLat = distanceInfo.partnerLocation.latitude
        let partnerLng = distanceInfo.partnerLocation.longitude
        
        let centerLat = (currentLat + partnerLat) / 2
        let centerLng = (currentLng + partnerLng) / 2
        
        let latDelta = abs(currentLat - partnerLat) * 1.5
        let lngDelta = abs(currentLng - partnerLng) * 1.5
        
        return MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: centerLat, longitude: centerLng),
            span: MKCoordinateSpan(
                latitudeDelta: max(latDelta, 0.1),
                longitudeDelta: max(lngDelta, 0.1)
            )
        )
    }
    
    private var timeAgoString: String {
        guard let distanceInfo = distanceInfo else { return "" }
        
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Locale(identifier: "fr_FR")
        return formatter.localizedString(for: distanceInfo.lastUpdated, relativeTo: Date())
    }
}

struct MapPinData: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let color: Color
}

extension DateFormatter {
    static let anniversaryFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale(identifier: "fr_FR")
        return formatter
    }()
    
    static let longDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale(identifier: "fr_FR")
        return formatter
    }()
}

#Preview {
    WidgetsView()
} 