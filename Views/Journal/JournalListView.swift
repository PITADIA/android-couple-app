import SwiftUI

struct JournalListView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedEntry: JournalEntry?
    
    let onCreateEntry: () -> Void
    
    // NOUVEAU: Observer directement le JournalService
    @ObservedObject private var journalService: JournalService
    
    // Initializer pour passer le service
    init(onCreateEntry: @escaping () -> Void) {
        self.onCreateEntry = onCreateEntry
        self.journalService = JournalService.shared
    }
    
    // NOUVEAU: Computed properties pour la logique freemium
    private var isUserSubscribed: Bool {
        return appState.currentUser?.isSubscribed ?? false
    }
    
    private var hasReachedFreeLimit: Bool {
        return journalService.hasReachedFreeLimit()
    }
    
    private var remainingFreeEntries: Int {
        return journalService.getRemainingFreeEntries()
    }
    
    var body: some View {
        ZStack {
            if journalService.entries.isEmpty {
                // État vide
                EmptyJournalStateView(
                    isSubscribed: isUserSubscribed,
                    hasReachedLimit: hasReachedFreeLimit,
                    onCreateEntry: onCreateEntry
                )
            } else {
                // Liste des entrées
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(sortedMonthGroups, id: \.key) { monthGroup in
                            Section {
                                ForEach(monthGroup.value) { entry in
                                    JournalEntryCardView(
                                        entry: entry,
                                        isUserEntry: isUserEntry(entry),
                                        isSubscribed: isUserSubscribed
                                    ) {
                                        selectedEntry = entry
                                    }
                                }
                            } header: {
                                HStack {
                                    Text(monthGroup.key)
                                        .font(.system(size: 18, weight: .semibold))
                                        .foregroundColor(.black)
                                    
                                    Spacer()
                                }
                                .padding(.horizontal, 20)
                                .padding(.top, 20)
                                .padding(.bottom, 8)
                            }
                        }
                    }
                    .padding(.bottom, 20)
                }
            }
            
            // Indicateur de chargement
            if journalService.isLoading {
                VStack {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(1.2)
                    
                    Text("loading".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.gray)
                        .padding(.top, 8)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black.opacity(0.3))
            }
        }
        .sheet(item: $selectedEntry) { entry in
            JournalEntryDetailView(entry: entry)
                .onAppear {
                    print("🔍 JournalListView: Présentation JournalEntryDetailView pour: '\(entry.title)' (ID: \(entry.id))")
                    print("🔍 JournalListView: JournalEntryDetailView sheet est apparue")
                }
                .onDisappear {
                    print("🔍 JournalListView: JournalEntryDetailView sheet a disparu")
                    selectedEntry = nil
                    print("🔍 JournalListView: selectedEntry remis à nil")
                }
        }
        .onChange(of: selectedEntry) { oldValue, newValue in
            if let entry = newValue {
                print("🔍 JournalListView: selectedEntry changé vers: '\(entry.title)' (ID: \(entry.id))")
            } else {
                print("🔍 JournalListView: selectedEntry remis à nil")
            }
        }
    }
    
    // NOUVEAU: Vérifier si une entrée appartient à l'utilisateur actuel
    private func isUserEntry(_ entry: JournalEntry) -> Bool {
        guard let currentUserId = appState.currentUser?.id else { return false }
        return entry.authorId == currentUserId
    }
    
    // Grouper les entrées par mois/année
    private var groupedEntries: [String: [JournalEntry]] {
        Dictionary(grouping: journalService.entries) { entry in
            let formatter = DateFormatter()
            formatter.dateFormat = "MMMM yyyy"
            formatter.locale = Locale.current
            return formatter.string(from: entry.eventDate)
        }
    }
    
    // NOUVEAU: Trier les groupes par date du plus récent au plus ancien
    private var sortedMonthGroups: [(key: String, value: [JournalEntry])] {
        return groupedEntries
            .map { (key: $0.key, value: $0.value.sorted { $0.eventDate > $1.eventDate }) }
            .sorted { monthGroup1, monthGroup2 in
                // Créer des dates pour comparison basée sur le premier événement de chaque groupe
                guard let date1 = monthGroup1.value.first?.eventDate,
                      let date2 = monthGroup2.value.first?.eventDate else {
                    return false
                }
                return date1 > date2
            }
    }
}

// MARK: - Empty State Views

struct EmptyJournalStateView: View {
    let isSubscribed: Bool
    let hasReachedLimit: Bool
    let onCreateEntry: () -> Void
    
    var body: some View {
        VStack(spacing: 30) {
            Image("jou")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 240, height: 240)
            
            VStack(spacing: 12) {
                Text(hasReachedLimit && !isSubscribed ? "limit_reached".localized : "empty_journal_message".localized)
                    .font(.system(size: 22, weight: .medium))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.center)
                
                Text(ui: "journal_description", comment: "Journal description")
                    .font(.system(size: 16))
                    .foregroundColor(.black.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
            }
            
            // Bouton Créer (style moderne rose, plus petit)
            Button(action: onCreateEntry) {
                HStack(spacing: 8) {
                    Image(systemName: "plus")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                    
                    Text(ui: "create", comment: "Create button")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                }
                .frame(width: 100, height: 38)
                .background(
                    RoundedRectangle(cornerRadius: 19)
                        .fill(Color(hex: "#FD267A"))
                )
            }
            
            if hasReachedLimit && !isSubscribed {
                Button("unlock_premium_button".localized) {
                    // Déclencher le paywall
                    // Cette action sera gérée par le parent
                }
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 200, height: 50)
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
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 40)
    }
}

struct FreemiumEncouragementView: View {
    let remainingEntries: Int
    let onUpgrade: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Image(systemName: "star.fill")
                    .foregroundColor(.yellow)
                
                Text(String(format: "remaining_entries_message".localized, remainingEntries, remainingEntries > 1 ? "s" : "", remainingEntries > 1 ? "s" : ""))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                
                Spacer()
            }
            
                            Text("premium_unlimited".localized)
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 30)
            
            Button(action: onUpgrade) {
                HStack {
                    Image(systemName: "crown.fill")
                        .font(.system(size: 14))
                    
                    Text("unlock_premium_button".localized)
                        .font(.system(size: 16, weight: .semibold))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
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
                .cornerRadius(22)
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.05))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.yellow.opacity(0.3), lineWidth: 1)
                )
        )
        .padding(.horizontal, 20)
        .padding(.top, 16)
    }
}

struct JournalEntryCardView: View {
    let entry: JournalEntry
    let isUserEntry: Bool // NOUVEAU: Indiquer si c'est une entrée de l'utilisateur actuel
    let isSubscribed: Bool // NOUVEAU: Statut d'abonnement
    let onTap: () -> Void
    
    var body: some View {
        Button(action: {
            print("🔍 JournalEntryCardView: Clic sur carte - '\(entry.title)' (ID: \(entry.id))")
            print("🔍 JournalEntryCardView: isUserEntry: \(isUserEntry)")
            print("🔍 JournalEntryCardView: Appel onTap()")
            onTap()
        }) {
            HStack(spacing: 16) {
                // Date
                VStack(spacing: 4) {
                    Text(entry.dayOfMonth)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.black)
                    
                    Text(monthAbbreviation)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.black.opacity(0.7))
                        .textCase(.uppercase)
                }
                .frame(width: 50)
                
                // Contenu
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(entry.title)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.black)
                            .lineLimit(1)
                        
                        Spacer()
                        

                    }
                    
                    if !entry.description.isEmpty {
                        Text(entry.description)
                            .font(.system(size: 14))
                            .foregroundColor(.black.opacity(0.8))
                            .lineLimit(2)
                    }
                    
                    // Heure
                    Text(timeString)
                        .font(.system(size: 12))
                        .foregroundColor(.black.opacity(0.6))
                }
                
                // Image si présente
                if entry.hasImage {
                    AsyncImageView(
                        imageURL: entry.imageURL,
                        width: 60,
                        height: 60,
                        cornerRadius: 8
                    )
                }
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white.opacity(0.8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            // NOUVEAU: Bordure différente pour les entrées de l'utilisateur
                            .stroke(
                                isUserEntry ? Color(hex: "#FD267A").opacity(0.3) : Color.black.opacity(0.1), 
                                lineWidth: 1
                            )
                    )
            )
            .padding(.horizontal, 20)
        }
        .buttonStyle(PlainButtonStyle())
        .onTapGesture {
            print("🔍 JournalEntryCardView: onTapGesture détecté sur carte - '\(entry.title)'")
        }
    }
    
    private var monthAbbreviation: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM"
        formatter.locale = Locale.current
        return formatter.string(from: entry.eventDate)
    }
    
    private var timeString: String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: entry.eventDate)
    }
} 