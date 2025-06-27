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
        
        var icon: String {
            switch self {
            case .countdown: return "timer"
            case .daysTotal: return "heart.fill"
            }
        }
        
        var requiresPremium: Bool {
            switch self {
            case .countdown, .daysTotal:
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