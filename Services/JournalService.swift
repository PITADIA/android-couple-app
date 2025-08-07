import Foundation
import FirebaseFirestore
import FirebaseAuth
import FirebaseStorage
import Combine
import UIKit

class JournalService: ObservableObject {
    static let shared = JournalService()
    
    private let db = Firestore.firestore()
    private let storage = Storage.storage()
    
    @Published var entries: [JournalEntry] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    private var listener: ListenerRegistration?
    private var cancellables = Set<AnyCancellable>()
    
    // NOUVEAU: Référence faible à AppState pour accéder au FreemiumManager
    private weak var appState: AppState?
    
    // NOUVEAU: Computed property pour compter les entrées créées par l'utilisateur actuel
    var currentUserEntriesCount: Int {
        guard let currentUserId = Auth.auth().currentUser?.uid else { return 0 }
        return entries.filter { $0.authorId == currentUserId }.count
    }
    
    // NOUVEAU: Computed property pour vérifier si l'utilisateur peut ajouter une entrée
    var canAddEntry: Bool {
        guard let freemiumManager = appState?.freemiumManager else { return true }
        return freemiumManager.canAddJournalEntry(currentEntriesCount: currentUserEntriesCount)
    }
    
    private init() {
        setupListener()
    }
    
    deinit {
        listener?.remove()
    }
    
    // NOUVEAU: Méthode pour configurer l'AppState
    func configure(with appState: AppState) {
        self.appState = appState
        print("📝 JournalService: Configuration avec AppState")
    }
    
    // MARK: - Setup Listener
    
    private func setupListener() {
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ JournalService: Utilisateur non connecté")
            return
        }
        
        print("🔥 JournalService: Configuration du listener utilisateur")
        
        // Utiliser un seul listener avec une requête composite
        listener = db.collection("journalEntries")
            .whereField("partnerIds", arrayContains: currentUser.uid)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    print("❌ JournalService: Erreur listener: \(error)")
                    return
                }
                
                self?.handleSnapshotUpdate(snapshot: snapshot)
            }
    }
    
    private func handleSnapshotUpdate(snapshot: QuerySnapshot?) {
        guard let documents = snapshot?.documents else { return }
        
        let newEntries = documents.compactMap { JournalEntry(from: $0) }
        
        DispatchQueue.main.async {
            // Remplacer toutes les entrées
            self.entries = newEntries
            
            // Trier par date d'événement (plus récent en premier)
            self.entries.sort { $0.eventDate > $1.eventDate }
            
            print("🔥 JournalService: \(self.entries.count) entrées chargées")
        }
    }
    
    // MARK: - Manual Refresh (pour forcer la mise à jour)
    
    func refreshEntries() async {
        guard let currentUser = Auth.auth().currentUser else { return }
        
        print("🔄 JournalService: Rafraîchissement manuel des entrées")
        
        do {
            let snapshot = try await db.collection("journalEntries")
                .whereField("partnerIds", arrayContains: currentUser.uid)
                .getDocuments()
            
            let entries = snapshot.documents.compactMap { JournalEntry(from: $0) }
            
            await MainActor.run {
                self.entries = entries.sorted { $0.eventDate > $1.eventDate }
                print("🔄 JournalService: \(self.entries.count) entrées rafraîchies")
            }
            
        } catch {
            print("❌ JournalService: Erreur rafraîchissement: \(error)")
        }
    }
    
    // MARK: - CRUD Operations
    
    func createEntry(
        title: String,
        description: String,
        eventDate: Date,
        image: UIImage? = nil,
        location: JournalLocation? = nil
    ) async throws {
        guard let currentUser = Auth.auth().currentUser,
              let userData = FirebaseService.shared.currentUser else {
            throw JournalError.userNotAuthenticated
        }
        
        // NOUVEAU: Vérifier la limite freemium avant la création
        guard let freemiumManager = appState?.freemiumManager else {
            throw JournalError.freemiumCheckFailed
        }
        
        let userEntriesCount = currentUserEntriesCount
        guard freemiumManager.canAddJournalEntry(currentEntriesCount: userEntriesCount) else {
            print("📝 JournalService: Limite freemium atteinte (\(userEntriesCount) entrées)")
            throw JournalError.freemiumLimitReached
        }
        
        print("🔥 JournalService: Création d'une nouvelle entrée")
        print("📝 JournalService: Entrées utilisateur actuel: \(userEntriesCount)")
        
        await MainActor.run {
            self.isLoading = true
            self.errorMessage = nil
        }
        
        do {
            var imageURL: String?
            
            // Upload de l'image si présente
            if let image = image {
                imageURL = try await uploadImage(image)
            }
            
            // Déterminer les partenaires avec qui partager
            var partnerIds: [String] = [currentUser.uid] // Toujours inclure l'auteur
            if let partnerId = userData.partnerId {
                partnerIds.append(partnerId)
            }
            
            let entry = JournalEntry(
                title: title,
                description: description,
                eventDate: eventDate,
                authorId: currentUser.uid,
                authorName: userData.name,
                imageURL: imageURL,
                partnerIds: partnerIds,
                location: location
            )
            
            // Sauvegarder dans Firestore
            try await db.collection("journalEntries")
                .document(entry.id)
                .setData(entry.toDictionary())
            
            print("✅ JournalService: Entrée créée avec succès")
            
            // NOUVEAU: Forcer le rafraîchissement après création
            await refreshEntries()
            
            await MainActor.run {
                self.isLoading = false
            }
            
        } catch {
            print("❌ JournalService: Erreur création entrée: \(error)")
            await MainActor.run {
                self.isLoading = false
                self.errorMessage = "Erreur lors de la création de l'entrée"
            }
            throw error
        }
    }
    
    func updateEntry(_ entry: JournalEntry) async throws {
        guard Auth.auth().currentUser?.uid == entry.authorId else {
            throw JournalError.notAuthorized
        }
        
        print("🔥 JournalService: Mise à jour de l'entrée: \(entry.id)")
        
        var updatedEntry = entry
        updatedEntry.updatedAt = Date()
        
        try await db.collection("journalEntries")
            .document(entry.id)
            .updateData(updatedEntry.toDictionary())
        
        print("✅ JournalService: Entrée mise à jour avec succès")
    }
    
    func deleteEntry(_ entry: JournalEntry) async throws {
        print("🗑️ JournalService: === DÉBUT SUPPRESSION ENTRÉE ===")
        print("🗑️ JournalService: Entry ID: \(entry.id)")
        print("🗑️ JournalService: Entry titre: '\(entry.title)'")
        print("🗑️ JournalService: Entry authorId: \(entry.authorId)")
        
        guard let currentUserUID = Auth.auth().currentUser?.uid else {
            print("❌ JournalService: Utilisateur non connecté (Auth.auth().currentUser?.uid = nil)")
            throw JournalError.userNotAuthenticated
        }
        
        print("🗑️ JournalService: Current user UID: \(currentUserUID)")
        
        guard currentUserUID == entry.authorId else {
            print("❌ JournalService: Pas autorisé - Current user: \(currentUserUID), Entry author: \(entry.authorId)")
            throw JournalError.notAuthorized
        }
        
        print("✅ JournalService: Autorisation vérifiée - utilisateur est l'auteur")
        
        // Supprimer l'image du Storage si elle existe
        if let imageURL = entry.imageURL, !imageURL.isEmpty {
            print("🗑️ JournalService: Suppression de l'image: \(imageURL)")
            do {
                try await deleteImage(from: imageURL)
                print("✅ JournalService: Image supprimée avec succès")
            } catch {
                print("⚠️ JournalService: Erreur suppression image (continuons): \(error)")
                // On continue même si l'image ne peut pas être supprimée
            }
        } else {
            print("🗑️ JournalService: Aucune image à supprimer")
        }
        
        // Supprimer l'entrée de Firestore
        print("🗑️ JournalService: Suppression de l'entrée Firestore: \(entry.id)")
        print("🗑️ JournalService: Collection: journalEntries, Document: \(entry.id)")
        
        do {
            try await db.collection("journalEntries")
                .document(entry.id)
                .delete()
            print("✅ JournalService: Entrée Firestore supprimée avec succès")
        } catch {
            print("❌ JournalService: Erreur suppression Firestore: \(error)")
            print("❌ JournalService: Type d'erreur: \(type(of: error))")
            print("❌ JournalService: Message: \(error.localizedDescription)")
            throw error
        }
        
        print("🗑️ JournalService: === FIN SUPPRESSION ENTRÉE (SUCCÈS) ===")
    }
    
    // MARK: - Image Management
    
    private func uploadImage(_ image: UIImage) async throws -> String {
        guard let currentUser = Auth.auth().currentUser else {
            throw JournalError.userNotAuthenticated
        }
        
        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            throw JournalError.imageProcessingFailed
        }
        
        let fileName = "\(UUID().uuidString).jpg"
        let imagePath = "journal_images/\(currentUser.uid)/\(fileName)"
        
        print("🔥 JournalService: Upload de l'image vers Firebase Storage: \(imagePath)")
        
        return try await withCheckedThrowingContinuation { continuation in
            let storageRef = storage.reference().child(imagePath)
            let metadata = StorageMetadata()
            metadata.contentType = "image/jpeg"
            
            let uploadTask = storageRef.putData(imageData, metadata: metadata) { metadata, error in
                if let error = error {
                    print("❌ JournalService: Erreur upload: \(error)")
                    continuation.resume(throwing: error)
                    return
                }
                
                // Récupérer l'URL de téléchargement
                storageRef.downloadURL { url, error in
                    if let error = error {
                        print("❌ JournalService: Erreur récupération URL: \(error)")
                        continuation.resume(throwing: error)
                        return
                    }
                    
                    guard let downloadURL = url else {
                        print("❌ JournalService: URL de téléchargement nulle")
                        continuation.resume(throwing: JournalError.imageProcessingFailed)
                        return
                    }
                    
                    print("✅ JournalService: Image uploadée avec succès: \(downloadURL.absoluteString)")
                    continuation.resume(returning: downloadURL.absoluteString)
                }
            }
            
            // Observer le progrès de l'upload (optionnel)
            uploadTask.observe(.progress) { snapshot in
                let percentComplete = 100.0 * Double(snapshot.progress!.completedUnitCount) / Double(snapshot.progress!.totalUnitCount)
                print("🔥 JournalService: Progrès upload: \(Int(percentComplete))%")
            }
        }
    }
    
    private func deleteImage(from url: String) async throws {
        guard !url.isEmpty else { return }
        
        print("🔥 JournalService: Suppression de l'image: \(url)")
        
        return try await withCheckedThrowingContinuation { continuation in
            let storageRef = storage.reference(forURL: url)
            
            storageRef.delete { error in
                if let error = error {
                    print("❌ JournalService: Erreur suppression image: \(error)")
                    // Ne pas faire échouer la suppression de l'entrée pour une erreur d'image
                    continuation.resume(returning: ())
                } else {
                    print("✅ JournalService: Image supprimée avec succès")
                    continuation.resume(returning: ())
                }
            }
        }
    }
    
    // MARK: - Helper Methods
    
    func getEntriesForDate(_ date: Date) -> [JournalEntry] {
        let calendar = Calendar.current
        return entries.filter { entry in
            calendar.isDate(entry.eventDate, inSameDayAs: date)
        }
    }
    
    func getEntriesForMonth(_ date: Date) -> [JournalEntry] {
        let calendar = Calendar.current
        return entries.filter { entry in
            calendar.isDate(entry.eventDate, equalTo: date, toGranularity: .month)
        }
    }
    
    func hasEntriesForDate(_ date: Date) -> Bool {
        return !getEntriesForDate(date).isEmpty
    }
    
    // MARK: - Partner Management
    
    func refreshPartnerEntries() async {
        guard let currentUser = Auth.auth().currentUser else { return }
        
        print("🔥 JournalService: Actualisation des entrées partenaire")
        
        do {
            let snapshot = try await db.collection("journalEntries")
                .whereField("partnerIds", arrayContains: currentUser.uid)
                .getDocuments()
            
            let partnerEntries = snapshot.documents.compactMap { JournalEntry(from: $0) }
            
            await MainActor.run {
                // Ajouter les nouvelles entrées partenaire
                for entry in partnerEntries {
                    if !self.entries.contains(where: { $0.id == entry.id }) {
                        self.entries.append(entry)
                    }
                }
                
                // Retrier
                self.entries.sort { $0.eventDate > $1.eventDate }
            }
            
        } catch {
            print("❌ JournalService: Erreur actualisation partenaire: \(error)")
        }
    }
    
    // MARK: - Freemium Helper Methods
    
    /// NOUVEAU: Retourne le nombre d'entrées restantes pour les utilisateurs gratuits
    func getRemainingFreeEntries() -> Int {
        guard let freemiumManager = appState?.freemiumManager else { return Int.max }
        return freemiumManager.getRemainingFreeJournalEntries(currentEntriesCount: currentUserEntriesCount)
    }
    
    /// NOUVEAU: Retourne le nombre maximum d'entrées gratuites
    func getMaxFreeEntries() -> Int {
        guard let freemiumManager = appState?.freemiumManager else { return Int.max }
        return freemiumManager.getMaxFreeJournalEntries()
    }
    
    /// NOUVEAU: Vérifie si l'utilisateur a atteint la limite gratuite
    func hasReachedFreeLimit() -> Bool {
        guard let freemiumManager = appState?.freemiumManager else { return false }
        return !freemiumManager.canAddJournalEntry(currentEntriesCount: currentUserEntriesCount)
    }
}

// MARK: - Error Types
enum JournalError: LocalizedError {
    case userNotAuthenticated
    case notAuthorized
    case imageProcessingFailed
    case networkError
    case freemiumLimitReached
    case freemiumCheckFailed
    
    var errorDescription: String? {
        switch self {
        case .userNotAuthenticated:
            return "Utilisateur non connecté"
        case .notAuthorized:
            return "Vous n'êtes pas autorisé à effectuer cette action"
        case .imageProcessingFailed:
            return "Erreur lors du traitement de l'image"
        case .networkError:
            return "Erreur de connexion"
        case .freemiumLimitReached:
            return "Limite d'entrées gratuites atteinte"
        case .freemiumCheckFailed:
            return "Erreur de vérification des limites"
        }
    }
} 