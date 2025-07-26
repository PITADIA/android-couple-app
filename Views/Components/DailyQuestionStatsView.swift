import SwiftUI

struct DailyQuestionStatsView: View {
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    
    var body: some View {
        VStack(spacing: 16) {
            HStack(spacing: 16) {
                // Questions répondues
                StatisticCardView(
                    title: "daily_questions_answered".localized(tableName: "DailyQuestions"),
                    value: "\(answeredQuestionsCount)",
                    icon: "checkmark.circle.fill",
                    iconColor: Color(hex: "#4CAF50"),
                    backgroundColor: Color(hex: "#E8F5E8"),
                    textColor: Color(hex: "#2E7D32")
                )
                
                // Streak actuelle
                StatisticCardView(
                    title: "daily_questions_streak".localized(tableName: "DailyQuestions"),
                    value: "\(currentStreak)",
                    icon: "flame.fill",
                    iconColor: Color(hex: "#FD267A"),
                    backgroundColor: Color(hex: "#FFE4EE"),
                    textColor: Color(hex: "#FD267A")
                )
            }
            
            HStack(spacing: 16) {
                // Taux de completion
                StatisticCardView(
                    title: "daily_questions_completion".localized(tableName: "DailyQuestions"),
                    value: "\(Int(completionRate * 100))%",
                    icon: "chart.line.uptrend.xyaxis",
                    iconColor: Color(hex: "#2196F3"),
                    backgroundColor: Color(hex: "#E3F2FD"),
                    textColor: Color(hex: "#1976D2")
                )
                
                // Total questions
                StatisticCardView(
                    title: "daily_questions_total".localized(tableName: "DailyQuestions"),
                    value: "\(totalQuestionsCount)",
                    icon: "questionmark.circle.fill",
                    iconColor: Color(hex: "#FF9800"),
                    backgroundColor: Color(hex: "#FFF3E0"),
                    textColor: Color(hex: "#F57C00")
                )
            }
        }
        .onAppear {
            loadQuestionHistory()
        }
    }
    
    // MARK: - Computed Properties
    
    private var answeredQuestionsCount: Int {
        return dailyQuestionService.questionHistory.filter { $0.bothResponded }.count
    }
    
    private var currentStreak: Int {
        let sortedQuestions = dailyQuestionService.questionHistory.sorted { $0.scheduledDate > $1.scheduledDate }
        var streak = 0
        
        for question in sortedQuestions {
            if question.bothResponded {
                streak += 1
            } else {
                break
            }
        }
        
        return streak
    }
    
    private var completionRate: Double {
        let total = dailyQuestionService.questionHistory.count
        guard total > 0 else { return 0.0 }
        return Double(answeredQuestionsCount) / Double(total)
    }
    
    private var totalQuestionsCount: Int {
        return dailyQuestionService.questionHistory.count
    }
    
    // MARK: - Methods
    
    private func loadQuestionHistory() {
        Task {
            await dailyQuestionService.loadQuestionHistory()
        }
    }
}

#Preview {
    DailyQuestionStatsView()
        .padding()
} 