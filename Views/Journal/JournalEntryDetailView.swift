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
        ZStack {
            // Fond gris clair identique à la page principale
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            ScrollView {
                    VStack(spacing: 24) {
                        // Header avec boutons intégrés
                        HStack {
                            // Bouton fermer (gauche)
                            Button(action: { dismiss() }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(12)
                                    .background(Color.black.opacity(0.6))
                                    .clipShape(Circle())
                            }
                            
                            Spacer()
                            
                            // Bouton supprimer (droite) - seulement pour l'auteur
                            if isAuthor {
                                Button(action: { 
                                    print("🗑️ JournalEntryDetailView: Clic sur bouton suppression")
                                    showingDeleteAlert = true 
                                }) {
                                    Image(systemName: "trash")
                                        .font(.system(size: 20, weight: .bold))
                                        .foregroundColor(.white)
                                        .padding(12)
                                        .background(Color.black.opacity(0.6))
                                        .clipShape(Circle())
                                }
                                .disabled(isDeleting)
                                .accessibilityLabel("Supprimer ce souvenir")
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                        .padding(.bottom, 10)
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
                                        title: "Date de l'événement",
                                        value: formattedEventDate
                                    )
                                    
                                    InfoRow(
                                        icon: "clock",
                                        title: "Heure",
                                        value: formattedEventTime
                                    )
                                    
                                    InfoRow(
                                        icon: "person.circle",
                                        title: "Créé par",
                                        value: entry.authorName
                                    )
                                    
                                    if let location = entry.location {
                                        InfoRow(
                                            icon: "location",
                                            title: "Lieu",
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
                .padding(.horizontal, 20)
                
                Spacer(minLength: 40)
            }
            .padding(.top, 0) // Pas de padding supplémentaire
                }
                
                // Overlay de suppression
                if isDeleting {
                    Color.black.opacity(0.5)
                        .ignoresSafeArea()
                    
                    VStack {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(1.2)
                        
                        Text("Suppression...")
                            .font(.system(size: 16))
                            .foregroundColor(.white)
                            .padding(.top, 8)
                    }
                }

        }
        .onAppear {
            print("📄 JournalEntryDetailView: Vue apparue - '\(entry.title)' - isAuthor: \(isAuthor)")
        }
        .alert("Supprimer ce souvenir ?", isPresented: $showingDeleteAlert) {
            Button("Annuler", role: .cancel) { }
            Button("Supprimer", role: .destructive) {
                print("🗑️ JournalEntryDetailView: Confirmation suppression")
                deleteEntry()
            }
        } message: {
            Text("Cette action est irréversible. Le souvenir sera supprimé définitivement.")
        }
    }
    
    private var formattedEventDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .full
        formatter.locale = Locale(identifier: "fr_FR")
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
        formatter.locale = Locale(identifier: "fr_FR")
        return formatter.string(from: entry.createdAt)
    }
    
    private func deleteEntry() {
        print("🗑️ JournalEntryDetailView: Début suppression entrée")
        print("🗑️ JournalEntryDetailView: - ID: \(entry.id)")
        print("🗑️ JournalEntryDetailView: - Titre: '\(entry.title)'")
        print("🗑️ JournalEntryDetailView: - Auteur: \(entry.authorName) (\(entry.authorId))")
        print("🗑️ JournalEntryDetailView: - A une image: \(entry.hasImage)")
        print("🗑️ JournalEntryDetailView: - Image URL: \(entry.imageURL ?? "nil")")
        
        isDeleting = true
        print("🗑️ JournalEntryDetailView: Flag isDeleting = true")
        
        Task {
            do {
                print("🗑️ JournalEntryDetailView: Appel de journalService.deleteEntry()")
                try await journalService.deleteEntry(entry)
                print("✅ JournalEntryDetailView: Suppression réussie !")
                
                await MainActor.run {
                    print("🗑️ JournalEntryDetailView: Fermeture de la vue...")
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