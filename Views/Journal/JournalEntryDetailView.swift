import SwiftUI
import FirebaseAuth

struct JournalEntryDetailView: View {
    let entry: JournalEntry
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var showingDeleteAlert = false
    @State private var isDeleting = false
    @ObservedObject private var firebaseService = FirebaseService.shared
    
    private var journalService: JournalService {
        return appState.journalService ?? JournalService.shared
    }
    
    private var isAuthor: Bool {
        // Utiliser l'UID Firebase pour la comparaison (comme dans JournalService)
        guard let firebaseUID = Auth.auth().currentUser?.uid else { return false }
        return entry.authorId == firebaseUID
    }
    
    var body: some View {
        ZStack(alignment: .top) {
            // Fond
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            // Contenu scrollable
            ScrollView {
                VStack(spacing: 24) {
                    Spacer().frame(height: 80) // Espace pour le header fixé
                    
                    // Image si présente
                    if let imageURL = entry.imageURL, !imageURL.isEmpty {
                        AsyncImageView(
                            imageURL: imageURL,
                            width: nil,
                            height: 250,
                            cornerRadius: 16
                        )
                        .padding(.horizontal, 20)
                    }
                    
                    // Contenu
                    VStack(alignment: .leading, spacing: 20) {
                        // Grande card titre + description avec effet sophistiqué
                        VStack(alignment: .leading, spacing: 16) {
                            // Titre
                            Text(entry.title)
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)
                            
                            // Date
                            Text(entry.formattedEventDate)
                                .font(.system(size: 16))
                                .foregroundColor(.black.opacity(0.6))
                            
                            // Description si présente
                            if !entry.description.isEmpty {
                                Divider()
                                    .background(Color.black.opacity(0.1))
                                    .padding(.vertical, 4)
                                
                                Text(entry.description)
                                    .font(.system(size: 16))
                                    .foregroundColor(.black.opacity(0.8))
                                    .lineSpacing(4)
                            }
                        }
                        .padding(24)
                        .background(
                            RoundedRectangle(cornerRadius: 20)
                                .fill(Color.white)
                                .shadow(
                                    color: Color.black.opacity(0.08),
                                    radius: 20,
                                    x: 0,
                                    y: 8
                                )
                                .shadow(
                                    color: Color.black.opacity(0.04),
                                    radius: 6,
                                    x: 0,
                                    y: 2
                                )
                        )
                        
                        // Card métadonnées avec effet sophistiqué
                        VStack(alignment: .leading, spacing: 12) {
                            VStack(spacing: 8) {
                                InfoRow(
                                    icon: "calendar",
                                    title: NSLocalizedString("event_date", comment: "Event date label"),
                                    value: formattedEventDate
                                )
                                
                                InfoRow(
                                    icon: "clock",
                                    title: NSLocalizedString("event_time", comment: "Event time label"),
                                    value: formattedEventTime
                                )
                                
                                InfoRow(
                                    icon: "person.circle",
                                    title: NSLocalizedString("created_by", comment: "Created by label"),
                                    value: entry.authorName
                                )
                                
                                if let location = entry.location {
                                    InfoRow(
                                        icon: "location",
                                        title: NSLocalizedString("location", comment: "Location label"),
                                        value: location.displayName
                                    )
                                }
                            }
                        }
                        .padding(24)
                        .background(
                            RoundedRectangle(cornerRadius: 20)
                                .fill(Color.white)
                                .shadow(
                                    color: Color.black.opacity(0.08),
                                    radius: 20,
                                    x: 0,
                                    y: 8
                                )
                                .shadow(
                                    color: Color.black.opacity(0.04),
                                    radius: 6,
                                    x: 0,
                                    y: 2
                                )
                        )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            
            // HEADER FIXE
            HStack {
                // Fermer
                Button(action: {
                    print("❌ JournalEntryDetailView: Fermer détecté")
                    dismiss()
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .padding(12)
                        .background(Color.black.opacity(0.6))
                        .clipShape(Circle())
                }
                .padding(.leading, 20)
                
                Spacer()
                
                // Supprimer si auteur
                if isAuthor {
                    Button(action: {
                        print("🗑️ JournalEntryDetailView: Supprimer détecté")
                        showingDeleteAlert = true
                    }) {
                        Image(systemName: "trash")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .padding(12)
                            .background(Color.black.opacity(0.6))
                            .clipShape(Circle())
                    }
                    .padding(.trailing, 20)
                    .disabled(isDeleting)
                }
            }
            .padding(.top, 20) // espace distance top safe area
        }
        .onAppear {
            print("📄 JournalEntryDetailView: Vue apparue - '\(entry.title)' - isAuthor: \(isAuthor)")
            print("📄 JournalEntryDetailView: entry.id: \(entry.id)")
            print("📄 JournalEntryDetailView: entry.authorId: \(entry.authorId)")
            print("📄 JournalEntryDetailView: currentUser.uid: \(Auth.auth().currentUser?.uid ?? "nil")")
            print("📄 JournalEntryDetailView: AppState présent: true")
            print("📄 JournalEntryDetailView: JournalService présent: true")
        }
        .onDisappear {
            print("📄 JournalEntryDetailView: Vue disparue - '\(entry.title)'")
        }
        .alert("Supprimer ce souvenir ?", isPresented: $showingDeleteAlert) {
            Button("Annuler", role: .cancel) { 
                print("🗑️ JournalEntryDetailView: Suppression annulée")
            }
            Button("Supprimer", role: .destructive) {
                print("🗑️ JournalEntryDetailView: Confirmation suppression")
                deleteEntry()
            }
        } message: {
            Text(NSLocalizedString("irreversible_action", comment: "Irreversible action message"))
        }
        .onChange(of: showingDeleteAlert) { oldValue, newValue in
            print("🗑️ JournalEntryDetailView: showingDeleteAlert changé: \(newValue)")
        }
        .onChange(of: isDeleting) { oldValue, newValue in
            print("🗑️ JournalEntryDetailView: isDeleting changé: \(newValue)")
        }
    }
    
    private var formattedEventDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .full
        formatter.locale = Locale.current
        return formatter.string(from: entry.eventDate)
    }
    
    private var formattedEventTime: String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: entry.eventDate)
    }
    
    private var formattedCreatedDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        formatter.locale = Locale.current
        return formatter.string(from: entry.createdAt)
    }
    
    private func deleteEntry() {
        print("🗑️ JournalEntryDetailView: Début suppression entrée")
        print("🗑️ JournalEntryDetailView: - ID: \(entry.id)")
        print("🗑️ JournalEntryDetailView: - Titre: '\(entry.title)'")
        print("🗑️ JournalEntryDetailView: - Auteur: \(entry.authorName) (\(entry.authorId))")
        print("🗑️ JournalEntryDetailView: - A une image: \(entry.hasImage)")
        print("🗑️ JournalEntryDetailView: - Image URL: \(entry.imageURL ?? "nil")")
        print("🗑️ JournalEntryDetailView: - JournalService disponible: true")
        
        isDeleting = true
        print("🗑️ JournalEntryDetailView: Flag isDeleting = true")
        
        Task {
            do {
                print("🗑️ JournalEntryDetailView: Appel de journalService.deleteEntry()")
                try await journalService.deleteEntry(entry)
                print("✅ JournalEntryDetailView: Suppression réussie !")
                
                await MainActor.run {
                    print("🗑️ JournalEntryDetailView: Fermeture de la vue...")
                    isDeleting = false
                    dismiss()
                    print("✅ JournalEntryDetailView: Vue fermée avec succès")
                }
                
            } catch {
                print("❌ JournalEntryDetailView: Erreur lors de la suppression")
                print("❌ JournalEntryDetailView: Type d'erreur: \(type(of: error))")
                print("❌ JournalEntryDetailView: Message: \(error.localizedDescription)")
                print("❌ JournalEntryDetailView: Détails: \(error)")
                
                await MainActor.run {
                    isDeleting = false
                    print("🗑️ JournalEntryDetailView: Flag isDeleting = false (erreur)")
                    // TODO: Afficher l'erreur à l'utilisateur
                    print("❌ Erreur suppression entrée: \(error)")
                }
            }
        }
    }
}

struct InfoRow: View {
    let icon: String
    let title: String
    let value: String
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(.black)
                .frame(width: 20)
            
            Text(title)
                .font(.system(size: 14))
                .foregroundColor(.black.opacity(0.6))
            
            Spacer()
            
            Text(value)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.black)
        }
    }
} 