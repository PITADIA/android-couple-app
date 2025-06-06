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
                        Text("Questions en cache")
                        Spacer()
                        Text("\(cacheStats.totalQuestions)")
                            .foregroundColor(.secondary)
                    }
                    
                    HStack {
                        Text("Catégories")
                        Spacer()
                        Text("\(cacheStats.categories)")
                            .foregroundColor(.secondary)
                    }
                    
                    HStack {
                        Text("Taille du cache")
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
                            Text("Recharger le cache")
                        }
                    }
                    .disabled(questionCacheManager.isLoading)
                    
                    Button(action: {
                        showingClearAlert = true
                    }) {
                        HStack {
                            Image(systemName: "trash")
                            Text("Vider le cache")
                        }
                        .foregroundColor(.red)
                    }
                }
                
                if questionCacheManager.isLoading {
                    Section {
                        HStack {
                            ProgressView()
                                .scaleEffect(0.8)
                            Text("Chargement en cours...")
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Gestion du Cache")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Fermer") {
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
            Text("Cette action supprimera toutes les questions en cache. Elles seront rechargées automatiquement lors de la prochaine utilisation.")
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