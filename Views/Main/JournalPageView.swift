import SwiftUI

struct JournalPageView: View {
    @EnvironmentObject var appState: AppState
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à l'accueil
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea(.all)
                
                JournalView()
                    .environmentObject(appState)
                    .padding(.bottom, 100) // Espace pour le menu du bas
            }
        }
        .navigationBarHidden(true)
    }
} 