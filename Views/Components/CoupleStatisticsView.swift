import SwiftUI

struct CoupleStatisticsView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var journalService = JournalService.shared
    @StateObject private var categoryProgressService = CategoryProgressService.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Titre de la section
            HStack {
                Text("Statistiques de votre couple")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 20)
                
                Spacer()
            }
            
            // Grille de statistiques 2x3
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 16),
                GridItem(.flexible(), spacing: 16)
            ], spacing: 16) {
                
                // Jours ensemble
                StatisticCardView(
                    title: "Jours\nensemble",
                    value: "\(daysTogetherCount)",
                    icon: "jours",
                    iconColor: Color(hex: "#feb5c8"),
                    backgroundColor: Color(hex: "#fedce3"),
                    textColor: Color(hex: "#db3556")
                )
                
                // Pourcentage de questions ouvertes
                StatisticCardView(
                    title: "Réponses\naux questions",
                    value: "\(Int(questionsProgressPercentage))%",
                    icon: "qst",
                    iconColor: Color(hex: "#fed397"),
                    backgroundColor: Color(hex: "#fde9cf"),
                    textColor: Color(hex: "#ffa229")
                )
                
                // Villes visitées
                StatisticCardView(
                    title: "Villes\nvisitées",
                    value: "\(citiesVisitedCount)",
                    icon: "ville",
                    iconColor: Color(hex: "#b0d6fe"),
                    backgroundColor: Color(hex: "#dbecfd"),
                    textColor: Color(hex: "#0a85ff")
                )
                
                // Pays visités
                StatisticCardView(
                    title: "Pays\nvisités",
                    value: "\(countriesVisitedCount)",
                    icon: "pays",
                    iconColor: Color(hex: "#ead3f6"),
                    backgroundColor: Color(hex: "#f3e6fa"),
                    textColor: Color(hex: "#bf47ff")
                )
            }
            .padding(.horizontal, 20)
        }
    }
    
    // MARK: - Computed Properties
    
    /// Nombre de jours ensemble basé sur la date de début de relation
    private var daysTogetherCount: Int {
        guard let relationshipStartDate = appState.currentUser?.relationshipStartDate else {
            return 0
        }
        
        let calendar = Calendar.current
        let dayComponents = calendar.dateComponents([.day], from: relationshipStartDate, to: Date())
        return max(dayComponents.day ?? 0, 0)
    }
    
    /// Pourcentage de progression total sur toutes les questions
    private var questionsProgressPercentage: Double {
        let categories = QuestionCategory.categories
        var totalQuestions = 0
        var totalProgress = 0
        
        for category in categories {
            let questions = getQuestionsForCategory(category.title)
            let currentIndex = categoryProgressService.getCurrentIndex(for: category.title)
            
            totalQuestions += questions.count
            totalProgress += min(currentIndex + 1, questions.count) // +1 car l'index commence à 0
        }
        
        guard totalQuestions > 0 else { return 0.0 }
        return (Double(totalProgress) / Double(totalQuestions)) * 100.0
    }
    
    /// Nombre de villes uniques visitées basé sur les entrées de journal
    private var citiesVisitedCount: Int {
        let uniqueCities = Set(journalService.entries.compactMap { entry in
            entry.location?.city?.trimmingCharacters(in: .whitespacesAndNewlines)
        }.filter { !$0.isEmpty })
        
        return uniqueCities.count
    }
    
    /// Nombre de pays uniques visités basé sur les entrées de journal
    private var countriesVisitedCount: Int {
        let uniqueCountries = Set(journalService.entries.compactMap { entry in
            entry.location?.country?.trimmingCharacters(in: .whitespacesAndNewlines)
        }.filter { !$0.isEmpty })
        
        return uniqueCountries.count
    }
    
    // MARK: - Helper Methods
    
    /// Récupère les questions pour une catégorie donnée via les données statiques
    private func getQuestionsForCategory(_ categoryTitle: String) -> [Question] {
        // Pour l'instant, utiliser les données statiques directement
        // TODO: Intégrer avec QuestionCacheManager quand la hiérarchie EnvironmentObject sera correcte
        return getQuestionsSampleForCategory(categoryTitle)
    }
    
    /// Fallback vers les questions samples basé sur la structure existante
    private func getQuestionsSampleForCategory(_ categoryTitle: String) -> [Question] {
        switch categoryTitle {
        case "Désirs Inavoués":
            return Question.sampleQuestions["LES PLUS HOTS"] ?? []
        case "Pour rigoler à deux":
            return Question.sampleQuestions["POUR RIRE À DEUX"] ?? []
        case "En couple":
            // Estimation réaliste pour les autres catégories (à remplacer quand les vraies questions seront ajoutées)
            return generatePlaceholderQuestions(count: 80, categoryTitle: categoryTitle)
        case "Des questions profondes":
            return generatePlaceholderQuestions(count: 90, categoryTitle: categoryTitle)
        case "À travers la distance":
            return generatePlaceholderQuestions(count: 70, categoryTitle: categoryTitle)
        case "Tu préfères quoi ?":
            return generatePlaceholderQuestions(count: 85, categoryTitle: categoryTitle)
        case "Réparer notre amour":
            return generatePlaceholderQuestions(count: 75, categoryTitle: categoryTitle)
        case "En date":
            return generatePlaceholderQuestions(count: 60, categoryTitle: categoryTitle)
        default:
            return []
        }
    }
    
    /// Génère des questions placeholder pour les catégories non encore implémentées
    private func generatePlaceholderQuestions(count: Int, categoryTitle: String) -> [Question] {
        return Array(0..<count).map { index in
            Question(id: "placeholder-\(categoryTitle.lowercased().replacingOccurrences(of: " ", with: "-"))-\(index)", 
                    text: "Question \(index + 1) pour \(categoryTitle)", 
                    category: categoryTitle)
        }
    }
}

// MARK: - Carte de statistique individuelle

struct StatisticCardView: View {
    let title: String
    let value: String
    let icon: String
    let iconColor: Color
    let backgroundColor: Color
    let textColor: Color
    
    var body: some View {
        VStack(spacing: 0) {
            // Ligne du haut : Icône à droite
            HStack {
                Spacer()
                
                // Icône en haut à droite
                Image(icon)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 40, height: 40)
                    .foregroundColor(iconColor)
            }
            
            Spacer()
            
            // Ligne du bas : Valeur + Titre à gauche
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    // Valeur principale
                    Text(value)
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(textColor)
                        .minimumScaleFactor(0.7)
                        .lineLimit(1)
                    
                    // Titre
                    Text(title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(textColor)
                        .multilineTextAlignment(.leading)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                
                Spacer()
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 140)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(backgroundColor)
                .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 2)
        )
    }
}

#Preview {
    CoupleStatisticsView()
        .environmentObject(AppState())
} 