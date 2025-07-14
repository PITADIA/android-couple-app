import SwiftUI

struct RelationshipDateStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var selectedDate = Date()
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("relationship_duration_question".localized)
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Premier Spacer pour centrer le contenu
            Spacer()
            
            // Contenu principal centré - Carrousel de date
            VStack(spacing: 30) {
                // Carrousel de date
                DatePickerCarousel(selectedDate: $selectedDate)
            }
            .padding(.horizontal, 30)
            
            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer()
                
            // Zone blanche collée en bas
            VStack(spacing: 0) {
                Button(action: {
                    viewModel.relationshipStartDate = selectedDate
                    viewModel.nextStep()
                }) {
                    Text("continue".localized)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color(hex: "#FD267A"))
                        .cornerRadius(28)
                }
                .padding(.horizontal, 30)
            }
            .padding(.vertical, 30)
            .background(Color.white)
        }
        .onAppear {
            // Initialiser avec une date par défaut (1 an en arrière)
            if selectedDate == Date() {
                selectedDate = Calendar.current.date(byAdding: .year, value: -1, to: Date()) ?? Date()
            }
        }
    }
}

// MARK: - Date Picker Carousel
struct DatePickerCarousel: View {
    @Binding var selectedDate: Date
    
    // Composants de date séparés
    @State private var selectedDay = 1
    @State private var selectedMonth = 1
    @State private var selectedYear = 2000
    
    private var months: [String] {
        [
            "month_january".localized,
            "month_february".localized,
            "month_march".localized,
            "month_april".localized,
            "month_may".localized,
            "month_june".localized,
            "month_july".localized,
            "month_august".localized,
            "month_september".localized,
            "month_october".localized,
            "month_november".localized,
            "month_december".localized
        ]
    }
    
    private let days = Array(1...31)
    private let years = Array(1990...2025).reversed()
    
    var body: some View {
        VStack(spacing: 20) {
            // Carrousel horizontal avec les 3 composants
            HStack(spacing: 0) {
                // Carrousel des mois
                Picker("Mois", selection: $selectedMonth) {
                    ForEach(1...12, id: \.self) { month in
                        Text(months[month - 1])
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.black)
                            .tag(month)
                    }
                }
                .pickerStyle(WheelPickerStyle())
                .frame(maxWidth: .infinity)
                .clipped()
                
                // Carrousel des jours
                Picker("Jour", selection: $selectedDay) {
                    ForEach(1...daysInSelectedMonth, id: \.self) { day in
                        Text("\(day)")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.black)
                            .tag(day)
                    }
                }
                .pickerStyle(WheelPickerStyle())
                .frame(maxWidth: .infinity)
                .clipped()
                
                // Carrousel des années
                Picker("Année", selection: $selectedYear) {
                    ForEach(years, id: \.self) { year in
                        Text("\(year)")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.black)
                            .tag(year)
                    }
                }
                .pickerStyle(WheelPickerStyle())
                .frame(maxWidth: .infinity)
                .clipped()
            }
            .frame(height: 200)
        }
        .onChange(of: selectedDay) { _, _ in 
            DispatchQueue.main.async { updateDate() }
        }
        .onChange(of: selectedMonth) { _, _ in 
            DispatchQueue.main.async { updateDate() }
        }
        .onChange(of: selectedYear) { _, _ in 
            DispatchQueue.main.async { updateDate() }
        }
        .onAppear {
            // Initialiser les composants avec la date sélectionnée sans déclencher onChange
            DispatchQueue.main.async {
                let calendar = Calendar.current
                let components = calendar.dateComponents([.day, .month, .year], from: selectedDate)
                selectedDay = components.day ?? 1
                selectedMonth = components.month ?? 1
                selectedYear = components.year ?? 2000
            }
        }
    }
    
    // Calculer le nombre de jours dans le mois sélectionné
    private var daysInSelectedMonth: Int {
        let calendar = Calendar.current
        let dateComponents = DateComponents(year: selectedYear, month: selectedMonth)
        let date = calendar.date(from: dateComponents) ?? Date()
        let range = calendar.range(of: .day, in: .month, for: date)
        return range?.count ?? 31
    }
    
    // Mettre à jour la date liée avec débouncing pour éviter les hangs
    private func updateDate() {
        guard selectedYear > 0, selectedMonth > 0, selectedDay > 0 else { return }
        
        let calendar = Calendar.current
        let adjustedDay = min(selectedDay, daysInSelectedMonth)
        let dateComponents = DateComponents(
            year: selectedYear,
            month: selectedMonth,
            day: adjustedDay
        )
        
        if let newDate = calendar.date(from: dateComponents) {
            selectedDate = newDate
        }
    }
}

#Preview {
    RelationshipDateStepView(viewModel: OnboardingViewModel())
} 