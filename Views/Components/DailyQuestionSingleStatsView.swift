import SwiftUI

struct DailyQuestionSingleStatsView: View {
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    
    var body: some View {
        StatisticCardView(
            title: "daily_questions_answered".localized(tableName: "DailyQuestions"),
            value: "\(answeredQuestionsCount)",
            icon: "checkmark.circle.fill",
            iconColor: Color(hex: "#4CAF50"),
            backgroundColor: Color(hex: "#E8F5E8"),
            textColor: Color(hex: "#2E7D32")
        )
        .onAppear {
            loadQuestionHistory()
        }
    }
    
    // MARK: - Computed Properties
    
    private var answeredQuestionsCount: Int {
        return dailyQuestionService.questionHistory.filter { $0.bothResponded }.count
    }
    
    // MARK: - Methods
    
    private func loadQuestionHistory() {
        Task {
            await dailyQuestionService.loadQuestionHistory()
        }
    }
}

#Preview {
    DailyQuestionSingleStatsView()
        .padding()
} 