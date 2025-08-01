import SwiftUI
import FirebaseAnalytics

struct JournalView: View {
    @EnvironmentObject var appState: AppState
    @State private var showingCreateEntry = false
    @State private var showingMapView = false
    
    // NOUVEAU: Observer directement le JournalService
    @ObservedObject private var journalService = JournalService.shared
    
    // NOUVEAU: Computed properties pour la logique freemium
    private var isUserSubscribed: Bool {
        return appState.currentUser?.isSubscribed ?? false
    }
    
    private var userEntriesCount: Int {
        guard let currentUser = FirebaseService.shared.currentUser else { return 0 }
        return journalService.entries.filter { $0.authorId == currentUser.id }.count
    }
    
    private var remainingFreeEntries: Int {
        let maxFreeEntries = journalService.getMaxFreeEntries()
        return max(0, maxFreeEntries - userEntriesCount)
    }
    
    private var canAddEntry: Bool {
        if isUserSubscribed {
            return true
        }
        return userEntriesCount < journalService.getMaxFreeEntries()
    }
    
    private func handleAddEntryTap() {
        // Utiliser le FreemiumManager pour gérer la création d'entrée
        appState.freemiumManager?.handleJournalEntryCreation(currentEntriesCount: userEntriesCount) {
            // Callback appelé si l'utilisateur peut créer une entrée
            showingCreateEntry = true
        }
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique aux autres pages
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header avec titre et boutons
                    HStack {
                        // Bouton carte à gauche (sans bulle blanche)
                        Button(action: {
                            showingMapView = true
                        }) {
                            Image(systemName: "map")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.black)
                        }
                        
                        Spacer()
                        
                        // Titre
                        VStack(spacing: 4) {
                            Text(ui: "our_journal", comment: "Our journal title")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)
                        }
                        
                        Spacer()
                        
                        // Bouton + à droite (toujours + même si limite atteinte)
                        Button(action: {
                            handleAddEntryTap()
                        }) {
                            Image(systemName: "plus")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.black)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 20)
                    
                    // Contenu du journal (liste des entrées)
                    JournalListView(onCreateEntry: handleAddEntryTap)
                        .environmentObject(appState)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            // 📊 Analytics: Journal ouvert
            Analytics.logEvent("journal_ouvert", parameters: [:])
            print("📊 Événement Firebase: journal_ouvert")
        }
        .sheet(isPresented: $showingCreateEntry) {
            CreateJournalEntryView()
                .environmentObject(appState)
                .onDisappear {
                    print("🔥 JournalView: CreateJournalEntryView fermée")
                    // NOUVEAU: Forcer le rafraîchissement des entrées
                    Task {
                        await journalService.refreshEntries()
                    }
                }
        }
        .sheet(isPresented: $showingMapView) {
            JournalMapView()
                .environmentObject(appState)
        }
    }
}

struct JournalView_Previews: PreviewProvider {
    static var previews: some View {
        JournalView()
            .environmentObject(AppState())
    }
} 