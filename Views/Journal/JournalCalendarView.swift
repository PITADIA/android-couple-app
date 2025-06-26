import SwiftUI

struct JournalCalendarView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedDate = Date()
    @State private var selectedEntry: JournalEntry?
    
    private var journalService: JournalService {
        return appState.journalService ?? JournalService.shared
    }
    
    // NOUVEAU: Computed properties pour la logique freemium
    private var isUserSubscribed: Bool {
        return appState.currentUser?.isSubscribed ?? false
    }
    
    // NOUVEAU: Méthode pour vérifier si une entrée appartient à l'utilisateur actuel
    private func isUserEntry(_ entry: JournalEntry) -> Bool {
        guard let currentUserId = appState.currentUser?.id else { return false }
        return entry.authorId == currentUserId
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Calendrier
            CalendarGridView(
                selectedDate: $selectedDate,
                hasEntriesForDate: journalService.hasEntriesForDate
            )
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
            
            // Entrées pour la date sélectionnée
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("Événements du \(formattedSelectedDate)")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.black)
                    
                    Spacer()
                }
                .padding(.horizontal, 20)
                
                let entriesForDate = journalService.getEntriesForDate(selectedDate)
                
                if entriesForDate.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "calendar.badge.plus")
                            .font(.system(size: 40))
                            .foregroundColor(.black.opacity(0.3))
                        
                        Text("Aucun événement ce jour")
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.6))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(entriesForDate) { entry in
                                JournalEntryCardView(
                                    entry: entry,
                                    isUserEntry: isUserEntry(entry),
                                    isSubscribed: isUserSubscribed
                                ) {
                                    selectedEntry = entry
                                }
                            }
                        }
                        .padding(.bottom, 20)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .sheet(item: $selectedEntry) { entry in
            JournalEntryDetailView(entry: entry)
        }
    }
    
    private var formattedSelectedDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .full
        formatter.locale = Locale(identifier: "fr_FR")
        return formatter.string(from: selectedDate)
    }
}

struct CalendarGridView: View {
    @Binding var selectedDate: Date
    let hasEntriesForDate: (Date) -> Bool
    
    @State private var currentMonth = Date()
    
    private let calendar = Calendar.current
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM yyyy"
        formatter.locale = Locale(identifier: "fr_FR")
        return formatter
    }()
    
    var body: some View {
        VStack(spacing: 20) {
            // Header du mois
            HStack {
                Button(action: previousMonth) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.black)
                }
                
                Spacer()
                
                Text(dateFormatter.string(from: currentMonth).capitalized)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.black)
                
                Spacer()
                
                Button(action: nextMonth) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.black)
                }
            }
            
            // Jours de la semaine
            HStack {
                ForEach(["LUN", "MAR", "MER", "JEU", "VEN", "SAM", "DIM"], id: \.self) { day in
                    Text(day)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.black.opacity(0.6))
                        .frame(maxWidth: .infinity)
                }
            }
            
            // Grille du calendrier
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 8) {
                ForEach(calendarDays, id: \.self) { date in
                    CalendarDayView(
                        date: date,
                        isSelected: calendar.isDate(date, inSameDayAs: selectedDate),
                        isCurrentMonth: calendar.isDate(date, equalTo: currentMonth, toGranularity: .month),
                        hasEntries: hasEntriesForDate(date)
                    ) {
                        selectedDate = date
                    }
                }
            }
        }
        .onAppear {
            currentMonth = selectedDate
        }
    }
    
    private var calendarDays: [Date] {
        guard let monthInterval = calendar.dateInterval(of: .month, for: currentMonth),
              let monthFirstWeek = calendar.dateInterval(of: .weekOfYear, for: monthInterval.start),
              let monthLastWeek = calendar.dateInterval(of: .weekOfYear, for: monthInterval.end) else {
            return []
        }
        
        var days: [Date] = []
        var date = monthFirstWeek.start
        
        while date < monthLastWeek.end {
            days.append(date)
            date = calendar.date(byAdding: .day, value: 1, to: date) ?? date
        }
        
        return days
    }
    
    private func previousMonth() {
        withAnimation(.easeInOut(duration: 0.3)) {
            currentMonth = calendar.date(byAdding: .month, value: -1, to: currentMonth) ?? currentMonth
        }
    }
    
    private func nextMonth() {
        withAnimation(.easeInOut(duration: 0.3)) {
            currentMonth = calendar.date(byAdding: .month, value: 1, to: currentMonth) ?? currentMonth
        }
    }
}

struct CalendarDayView: View {
    let date: Date
    let isSelected: Bool
    let isCurrentMonth: Bool
    let hasEntries: Bool
    let onTap: () -> Void
    
    private let calendar = Calendar.current
    
    var body: some View {
        Button(action: onTap) {
            ZStack {
                // Background
                RoundedRectangle(cornerRadius: 8)
                    .fill(backgroundColor)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(borderColor, lineWidth: isSelected ? 2 : 0)
                    )
                
                VStack(spacing: 2) {
                    // Numéro du jour
                    Text("\(calendar.component(.day, from: date))")
                        .font(.system(size: 16, weight: isSelected ? .bold : .medium))
                        .foregroundColor(textColor)
                    
                    // Indicateur d'entrées
                    if hasEntries {
                        Circle()
                            .fill(Color(hex: "#FD267A"))
                            .frame(width: 6, height: 6)
                    }
                }
            }
        }
        .frame(height: 44)
        .buttonStyle(PlainButtonStyle())
    }
    
    private var backgroundColor: Color {
        if isSelected {
            return Color(hex: "#FD267A").opacity(0.2)
        } else if hasEntries {
            return Color.white.opacity(0.05)
        } else {
            return Color.clear
        }
    }
    
    private var borderColor: Color {
        return Color(hex: "#FD267A")
    }
    
    private var textColor: Color {
        if !isCurrentMonth {
            return .black.opacity(0.3)
        } else if isSelected {
            return Color(hex: "#FD267A")
        } else {
            return .black
        }
    }
} 