import SwiftUI

struct CacheManagementView: View {
    @EnvironmentObject private var questionCacheManager: QuestionCacheManager
    @Environment(\.dismiss) private var dismiss
    
    @State private var showingClearAlert = false
    @State private var cacheStats: (totalQuestions: Int, categories: Int, cacheSize: String) = (0, 0, "0 B")
    
    var body: some View {
        NavigationView {
            List {
                Section("📊 Statistiques du Cache") {
                    HStack {
                        Text(NSLocalizedString("questions_cached", comment: "Questions cached label"))
                            .font(.system(size: 16))
                            .foregroundColor(.black)
                        
                        Spacer()
                        
                        Text("\(cacheStats.totalQuestions)")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.black)
                    }
                    
                    HStack {
                        Text(NSLocalizedString("categories", comment: "Categories label"))
                            .font(.system(size: 16))
                            .foregroundColor(.black)
                        Spacer()
                        Text("\(cacheStats.categories)")
                            .foregroundColor(.secondary)
                    }
                    
                    HStack {
                        Text(NSLocalizedString("cache_size", comment: "Cache size text"))
                        Spacer()
                        Text(cacheStats.cacheSize)
                            .foregroundColor(.secondary)
                    }
                }
                
                Section("🔄 Actions") {
                    Button(action: {
                        Task {
                            await questionCacheManager.preloadAllCategories()
                            updateStats()
                        }
                    }) {
                        HStack {
                            Image(systemName: "arrow.clockwise")
                            Text(NSLocalizedString("reload_cache", comment: "Reload cache button"))
                        }
                    }
                    .disabled(questionCacheManager.isLoading)
                    
                    Button(action: {
                        showingClearAlert = true
                    }) {
                        HStack {
                            Image(systemName: "trash")
                            Text(NSLocalizedString("clear_cache", comment: "Clear cache button"))
                        }
                        .foregroundColor(.red)
                    }
                }
                
                if questionCacheManager.isLoading {
                    Section {
                        HStack {
                            ProgressView()
                                .scaleEffect(0.8)
                            Text(NSLocalizedString("loading_simple", comment: "Loading status"))
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Gestion du Cache")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(NSLocalizedString("close", comment: "Close button")) {
                        dismiss()
                    }
                }
            }
        }
        .onAppear {
            updateStats()
        }
        .alert("Vider le cache", isPresented: $showingClearAlert) {
            Button("Annuler", role: .cancel) { }
            Button("Vider", role: .destructive) {
                questionCacheManager.clearCache()
                updateStats()
            }
        } message: {
            Text(NSLocalizedString("cache_clear_confirmation", comment: "Cache clear confirmation message"))
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
        }
    }
    
    private func updateStats() {
        cacheStats = questionCacheManager.getCacheStatistics()
    }
}

#Preview {
    CacheManagementView()
        .environmentObject(QuestionCacheManager())
} 