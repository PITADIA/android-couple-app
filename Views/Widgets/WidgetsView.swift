import SwiftUI
import MapKit

struct WidgetsView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var selectedWidget: WidgetType = .countdown
    @State private var currentMessageIndex = 0
    @State private var timer: Timer?
    @State private var showLockScreenTutorial = false
    @State private var showHomeScreenTutorial = false
    @State private var showSubscriptionSheet = false
    
    // Helper pour localisation UI
    private func ui(_ key: String) -> String {
        return LocalizationService.ui(key)
    }
    
    // Utiliser le WidgetService global d'AppState
    private var widgetService: WidgetService? {
        return appState.widgetService
    }
    
    // Vérifier si l'utilisateur a un abonnement
    private var hasSubscription: Bool {
        return appState.currentUser?.isSubscribed ?? false
    }
    
    enum WidgetType: String, CaseIterable {
        case countdown = "countdown"
        case daysTotal = "daysTotal"
        
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
            ZStack {
                // Même fond que la page principale
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea(.all)
                
                ScrollView {
                    VStack(spacing: 30) {
                        // Header avec bouton retour
                        HStack {
                            Button(action: {
                                dismiss()
                            }) {
                                ZStack {
                                    Circle()
                                        .fill(Color.white.opacity(0.9))
                                        .frame(width: 40, height: 40)
                                    
                                    Image(systemName: "chevron.left")
                                        .font(.system(size: 16, weight: .medium))
                                        .foregroundColor(.black)
                                }
                            }
                            
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 20)
                        .padding(.bottom, 20)
                        
                        // Section Écran verrouillé
                        VStack(alignment: .leading, spacing: 16) {
                            Text("lock_screen".localized)
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 20)
                            
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                                                    LockScreenWidgetPreview(title: ui("widget_distance_title"), subtitle: ui("widget_distance_subtitle"), widgetType: .distance, widgetService: widgetService, appState: appState, hasSubscription: hasSubscription, onPremiumTap: {
                    showSubscriptionSheet = true
                })
                LockScreenWidgetPreview(title: ui("widget_days_total_title"), subtitle: ui("widget_days_subtitle"), widgetType: .days, widgetService: widgetService, appState: appState, hasSubscription: hasSubscription, onPremiumTap: {
                                        showSubscriptionSheet = true
                                    })
                                }
                                .padding(.horizontal, 20)
                            }
                        }
                        
                        // Section Écran d'accueil
                        VStack(alignment: .leading, spacing: 16) {
                            Text("home_screen".localized)
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 20)
                            
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 16) {
                                                    HomeScreenWidgetPreview(title: ui("widget_days_total_title"), subtitle: ui("widget_small_subtitle"), isMain: true, widgetService: widgetService, appState: appState, hasSubscription: hasSubscription, onPremiumTap: {
                    showSubscriptionSheet = true
                })
                HomeScreenWidgetPreview(title: ui("widget_distance_title"), subtitle: ui("widget_small_subtitle"), isMain: false, widgetService: widgetService, appState: appState, hasSubscription: hasSubscription, onPremiumTap: {
                    showSubscriptionSheet = true
                })
                HomeScreenWidgetPreview(title: ui("widget_complete_title"), subtitle: ui("widget_large_subtitle"), isMain: false, widgetService: widgetService, appState: appState, hasSubscription: hasSubscription, onPremiumTap: {
                                        showSubscriptionSheet = true
                                    })
                    }
                    .padding(.horizontal, 20)
                }
                        }
                        
                        // Section Comment ajouter
                        VStack(alignment: .leading, spacing: 16) {
                            Text("how_to_add".localized)
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 20)
                
                            VStack(spacing: 12) {
                                // Card pour widgets écran verrouillé
                                Button(action: {
                                    showLockScreenTutorial = true
                                }) {
                                    HStack(spacing: 16) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            Text("lock_screen_widget".localized)
                                                .font(.system(size: 18, weight: .semibold))
                                                .foregroundColor(.black)
                                                .multilineTextAlignment(.center)
                                            
                                            Text("complete_guide".localized)
                                                .font(.system(size: 14))
                                                .foregroundColor(.black.opacity(0.7))
                                                .multilineTextAlignment(.center)
                                        }
                                        
                                        Spacer()
                                        
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 14))
                                            .foregroundColor(.black.opacity(0.5))
                                    }
                                    .padding(.horizontal, 24)
                                    .padding(.vertical, 20)
                                    .background(
                                        RoundedRectangle(cornerRadius: 16)
                                            .fill(Color.white)
                                            .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
                                    )
                                }
                                .buttonStyle(PlainButtonStyle())
                                
                                // Card pour widgets écran d'accueil
                                Button(action: {
                                    showHomeScreenTutorial = true
                                }) {
                                    HStack(spacing: 16) {
                                        VStack(alignment: .leading, spacing: 6) {
                                            Text("home_screen_widget".localized)
                                                .font(.system(size: 18, weight: .semibold))
                                                .foregroundColor(.black)
                                                .multilineTextAlignment(.center)
                                            
                                            Text("complete_guide".localized)
                                                .font(.system(size: 14))
                                                .foregroundColor(.black.opacity(0.7))
                                                .multilineTextAlignment(.center)
                        }
                        
                                        Spacer()
                                        
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 14))
                                            .foregroundColor(.black.opacity(0.5))
                                    }
                                    .padding(.horizontal, 24)
                                    .padding(.vertical, 20)
                                    .background(
                                        RoundedRectangle(cornerRadius: 16)
                                            .fill(Color.white)
                                            .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
                                    )
                                }
                                .buttonStyle(PlainButtonStyle())
                    }
                    .padding(.horizontal, 20)
                }
            }
                    .padding(.bottom, 30) // Padding minimal en bas
            .background(
                        // Même dégradé rose que la page principale
                        VStack {
                    LinearGradient(
                        gradient: Gradient(colors: [
                                    Color(hex: "#FD267A").opacity(0.3),
                                    Color(hex: "#FD267A").opacity(0.1),
                                    Color.white.opacity(0)
                        ]),
                                startPoint: .top,
                                endPoint: .bottom
                            )
                            .frame(height: 350)
                            .ignoresSafeArea(edges: .top)
                            
                            Spacer()
                        }
                        , alignment: .top
                    )
                }
                .ignoresSafeArea(edges: .top)
            }
        }
        .navigationBarHidden(true)
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
        .fullScreenCover(isPresented: $showLockScreenTutorial) {
            LockScreenWidgetTutorialView()
        }
        .fullScreenCover(isPresented: $showHomeScreenTutorial) {
            HomeScreenWidgetTutorialView()
        }
        .sheet(isPresented: $showSubscriptionSheet) {
            SubscriptionView()
                .environmentObject(appState)
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

struct LockScreenWidgetPreview: View {
    let title: String
    let subtitle: String
    let widgetType: WidgetType
    let widgetService: WidgetService?
    let appState: AppState
    let hasSubscription: Bool
    let onPremiumTap: () -> Void
    
    enum WidgetType {
        case distance, days
    }
    
    // Déterminer si ce widget nécessite un abonnement premium
    private var isPremium: Bool {
        return widgetType == .distance // Seul le widget distance est premium
    }
    
    var body: some View {
        // Aperçu du widget avec fond blanc élégant (sans titre/sous-titre)
        Button(action: {
            if isPremium && !hasSubscription {
                onPremiumTap()
            }
        }) {
                ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white)
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
                    .frame(width: widgetType == .distance ? 200 : 140, height: 120)
            
            if widgetType == .distance {
                VStack(spacing: 8) {
                    if let distanceInfo = widgetService?.distanceInfo {
                        Text("our_distance".localized + " " + distanceInfo.formattedDistance)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)
                        
                        HStack(spacing: 8) {
                            Circle()
                                .fill(Color.gray.opacity(0.2))
                                .frame(width: 24, height: 24)
                                .overlay(
                                    Text(String(appState.currentUser?.name.first ?? "Y"))
                                        .font(.system(size: 10, weight: .medium))
                                        .foregroundColor(.black)
                                )
                            
                            Text("dash_placeholder".localized)
                                .font(.system(size: 12))
                                .foregroundColor(.gray)
                            
                            Image(systemName: "heart.fill")
                                .font(.system(size: 12))
                                .foregroundColor(.red)
                            
                            Text("dash_placeholder".localized)
                                .font(.system(size: 12))
                                .foregroundColor(.gray)
                            
                            Circle()
                                .fill(Color.gray.opacity(0.2))
                                .frame(width: 24, height: 24)
                                .overlay(
                                    Text(String(appState.partnerLocationService?.partnerName?.first ?? "L"))
                                        .font(.system(size: 10, weight: .medium))
                                        .foregroundColor(.black)
                                )
                        }
                    } else {
                        Text("our_distance".localized + " " + "widget_dash_m".localized)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)
                        
                        HStack(spacing: 8) {
                    Circle()
                        .fill(Color.gray.opacity(0.2))
                                .frame(width: 24, height: 24)
                                .overlay(
                                    Text("Y")
                                        .font(.system(size: 10, weight: .medium))
                                        .foregroundColor(.black)
                                )
                            
                            Text("dash_placeholder".localized)
                                .font(.system(size: 12))
                                .foregroundColor(.gray)
                    
                            Image(systemName: "heart.fill")
                                .font(.system(size: 12))
                                .foregroundColor(.red)
                            
                            Text("dash_placeholder".localized)
                                .font(.system(size: 12))
                        .foregroundColor(.gray)
                            
                            Circle()
                                .fill(Color.gray.opacity(0.2))
                                .frame(width: 24, height: 24)
                                .overlay(
                                    Text("L")
                                        .font(.system(size: 10, weight: .medium))
                                        .foregroundColor(.black)
                                )
                        }
                    }
                }
            } else {
                VStack(spacing: 8) {
                    Text("💕")
                        .font(.system(size: 20))
                    
                    if let relationshipStats = widgetService?.relationshipStats {
                        Text("\(relationshipStats.daysTotal)")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.black)
                    } else {
                        Text("dash_separator".localized)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.black)
                    }
                    
                    Text("days".localized)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.black)
                }
            }
            
            // Cadenas pour les widgets premium
            if isPremium && !hasSubscription {
                VStack {
                    HStack {
                        Spacer()
                        Text("locked_icon".localized)
                            .font(.system(size: 16))
                            .padding(.top, 8)
                            .padding(.trailing, 8)
                    }
                    Spacer()
                }
            }
        }
        }
        .buttonStyle(PlainButtonStyle())
        .allowsHitTesting(isPremium && !hasSubscription)
    }
}

struct HomeScreenWidgetPreview: View {
    let title: String
    let subtitle: String
    let isMain: Bool
    let widgetService: WidgetService?
    let appState: AppState
    let hasSubscription: Bool
    let onPremiumTap: () -> Void
    
    // Déterminer si ce widget nécessite un abonnement premium
    private var isPremium: Bool {
        return title == LocalizationService.ui("widget_distance_title") || title == LocalizationService.ui("widget_complete_title") // Widgets distance et complet sont premium
    }
    
    var body: some View {
        // Aperçu du widget avec fond blanc comme les widgets d'écran verrouillé (sans titre/sous-titre)
        Button(action: {
            if isPremium && !hasSubscription {
                onPremiumTap()
            }
        }) {
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white)
                    .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
                    .frame(width: isMain ? 160 : 140, height: 120)
            
            if title == LocalizationService.ui("widget_days_total_title") {
                VStack(spacing: 8) {
                    Text("💕")
                        .font(.system(size: 24))
                    
                    if let relationshipStats = widgetService?.relationshipStats {
                        Text("\(relationshipStats.daysTotal) " + "widget_days_text".localized)
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.black)
                    } else {
                        Text(ui: "widget_dash_jours", comment: "Widget dash jours")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.black)
                    }
                    
                    HStack(spacing: 8) {
                        Circle()
                            .fill(Color.gray.opacity(0.2))
                            .frame(width: 24, height: 24)
                            .overlay(
                                Text(String(appState.currentUser?.name.first ?? "Y"))
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.black)
                            )
                        Circle()
                            .fill(Color.gray.opacity(0.2))
                            .frame(width: 24, height: 24)
                            .overlay(
                                Text(String(appState.partnerLocationService?.partnerName?.first ?? "L"))
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.black)
                            )
                    }
                }
            } else if title == LocalizationService.ui("widget_distance_title") {
                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(Color.gray.opacity(0.2))
                            .frame(width: 24, height: 24)
                            .overlay(
                                Text(String(appState.currentUser?.name.first ?? "Y"))
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.black)
                            )
                        Circle()
                            .fill(Color.gray.opacity(0.2))
                            .frame(width: 24, height: 24)
                            .overlay(
                                Text(String(appState.partnerLocationService?.partnerName?.first ?? "L"))
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.black)
                            )
                    }
                    
                    if let distanceInfo = widgetService?.distanceInfo {
                        Text(distanceInfo.formattedDistance)
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.black)
                    } else {
                        Text(ui: "widget_dash_km", comment: "Widget dash km")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.black)
                    }
                    
                    Text(ui: "widget_distance_text", comment: "Widget distance text")
                        .font(.system(size: 12))
                        .foregroundColor(.black.opacity(0.6))
                }
            } else {
                HStack(spacing: 12) {
                    VStack(spacing: 6) {
                        HStack(spacing: 6) {
                            Circle()
                                .fill(Color.gray.opacity(0.2))
                                .frame(width: 20, height: 20)
                                .overlay(
                                    Text(String(appState.currentUser?.name.first ?? "Y"))
                                        .font(.system(size: 8, weight: .medium))
                                        .foregroundColor(.black)
                                )
                            Circle()
                                .fill(Color.gray.opacity(0.2))
                                .frame(width: 20, height: 20)
                                .overlay(
                                    Text(String(appState.partnerLocationService?.partnerName?.first ?? "L"))
                                        .font(.system(size: 8, weight: .medium))
                                        .foregroundColor(.black)
                                )
                        }
                        
                        if let relationshipStats = widgetService?.relationshipStats {
                            Text("\(relationshipStats.daysTotal) " + "widget_days_text".localized)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.black)
                        } else {
                            Text(ui: "widget_dash_jours", comment: "Widget dash jours")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.black)
                        }
                        
                        Text(ui: "widget_together_text", comment: "Widget together text")
                            .font(.system(size: 10))
                            .foregroundColor(.black.opacity(0.6))
                    }
                    
                    Rectangle()
                        .fill(Color.gray.opacity(0.2))
                        .frame(width: 1, height: 50)
                    
                    VStack(spacing: 4) {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.red)
                        
                        if let distanceInfo = widgetService?.distanceInfo {
                            Text(distanceInfo.formattedDistance)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.black)
                        } else {
                            Text(ui: "widget_dash_km", comment: "Widget dash km")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.black)
                        }
                        
                        Text(ui: "widget_distance_text", comment: "Widget distance text")
                            .font(.system(size: 8))
                            .foregroundColor(.black.opacity(0.6))
                    }
                }
            }
            
            // Cadenas pour les widgets premium
            if isPremium && !hasSubscription {
                VStack {
                HStack {
                        Spacer()
                        Text("locked_widget".localized)
                        .font(.system(size: 16))
                            .padding(.top, 8)
                            .padding(.trailing, 8)
                    }
                    Spacer()
                }
            }
            }
        }
        .buttonStyle(PlainButtonStyle())
        .allowsHitTesting(isPremium && !hasSubscription)
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
                    Text("time_together".localized)
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black.opacity(0.8))
                    
                    Text(stats.countdownText)
                        .font(.system(size: 48, weight: .bold))
                        .foregroundColor(.black)
                        .monospaced()
                    
                    HStack(spacing: 20) {
                        Text("days".localized)
                        Text("hours".localized)
                        Text("minutes".localized)
                        Text("seconds".localized)
                    }
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.black.opacity(0.6))
                }
                
                VStack(spacing: 8) {
                    Text("\(stats.formattedDuration)")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundColor(.black)
                    
                    if stats.daysToAnniversary > 0 {
                        Text("more_than_days".localized + " \(stats.daysToAnniversary) " + "days_before_anniversary".localized)
                            .font(.system(size: 14))
                            .foregroundColor(.black.opacity(0.6))
                            .multilineTextAlignment(.center)
                    } else {
                        Text("happy_anniversary".localized)
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(Color(hex: "#FD267A"))
                    }
                }
            } else {
                VStack(spacing: 16) {
                    Image(systemName: "heart.slash")
                        .font(.system(size: 60))
                        .foregroundColor(.black.opacity(0.3))
                    
                    Text("no_relationship_date".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                    
                    Text("add_relationship_date".localized)
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
                    Text("\(stats.daysTotal) " + "days".localized)
                        .font(.system(size: 48, weight: .bold))
                        .foregroundColor(.black)
                    
                    Text("together".localized)
                        .font(.system(size: 24, weight: .medium))
                        .foregroundColor(.black.opacity(0.8))
                }
                
                Text("since_date".localized + " \(DateFormatter.longDateFormatter.string(from: stats.startDate))")
                    .font(.system(size: 16))
                    .foregroundColor(.black.opacity(0.6))
                    .multilineTextAlignment(.center)
            } else {
                VStack(spacing: 16) {
                    Image(systemName: "heart.slash")
                        .font(.system(size: 60))
                        .foregroundColor(.black.opacity(0.3))
                    
                    Text("no_relationship_date".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                    
                    Text("add_relationship_date".localized)
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
        formatter.locale = Locale.current
        return formatter
    }()
    
    static let longDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale.current
        return formatter
    }()
}

struct WidgetsView_Previews: PreviewProvider {
    static var previews: some View {
    WidgetsView()
            .environmentObject(AppState())
    }
} 