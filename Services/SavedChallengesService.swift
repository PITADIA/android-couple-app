import Foundation
import FirebaseFirestore
import FirebaseAuth
import Combine

@MainActor
class SavedChallengesService: ObservableObject {
    static let shared = SavedChallengesService()
    
    @Published var savedChallenges: [SavedChallenge] = []
    @Published var isLoading: Bool = false
    @Published var lastSavedChallenge: SavedChallenge?
    
    private var db = Firestore.firestore()
    private var savedChallengesListener: ListenerRegistration?
    
    // CORRECTION: Référence weak à AppState pour éviter les cycles de référence
    private weak var appState: AppState?
    
    private init() {
        // Les listeners seront configurés via configure(with:)
    }
    
    deinit {
        savedChallengesListener?.remove()
    }
    
    // MARK: - Configuration
    
    func configure(with appState: AppState) {
        self.appState = appState
        setupListener()
    }
    
    // MARK: - Setup Listener
    
    private func setupListener() {
        guard let firebaseUser = Auth.auth().currentUser,
              let currentUser = appState?.currentUser else {
            print("🔥 SavedChallengesService: Aucun utilisateur connecté")
            return
        }
        
        print("🔥 SavedChallengesService: ✅ CORRECTION - Utilisation Firebase UID")
        print("🔥 SavedChallengesService: Firebase UID: \(firebaseUser.uid)")
        print("🔥 SavedChallengesService: App User ID: \(currentUser.id)")
        
        savedChallengesListener?.remove()
        
        savedChallengesListener = db.collection("savedChallenges")
            .whereField("userId", isEqualTo: firebaseUser.uid)
            .order(by: "savedAt", descending: true)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self = self else { return }
                
                if let error = error {
                    print("❌ SavedChallengesService: Erreur listener: \(error)")
                    return
                }
                
                guard let documents = snapshot?.documents else {
                    print("📊 SavedChallengesService: Aucun défi sauvegardé trouvé")
                    self.savedChallenges = []
                    return
                }
                
                print("🎯 SavedChallengesService: Listener déclenché")
                print("📊 SavedChallengesService: \(documents.count) défi(s) sauvegardé(s) trouvé(s)")
                
                var challenges: [SavedChallenge] = []
                
                for document in documents {
                    if let challenge = self.parseSavedChallengeDocument(document: document) {
                        challenges.append(challenge)
                    }
                }
                
                self.savedChallenges = challenges
                print("✅ SavedChallengesService: \(challenges.count) défis sauvegardés chargés")
            }
    }
    
    // MARK: - Document Parsing
    
    private func parseSavedChallengeDocument(document: QueryDocumentSnapshot) -> SavedChallenge? {
        let data = document.data()
        
        guard let challengeKey = data["challengeKey"] as? String,
              let challengeDay = data["challengeDay"] as? Int,
              let savedAt = (data["savedAt"] as? Timestamp)?.dateValue(),
              let userId = data["userId"] as? String else {
            print("❌ SavedChallengesService: Données manquantes dans le document")
            return nil
        }
        
        return SavedChallenge(
            id: document.documentID,
            challengeKey: challengeKey,
            challengeDay: challengeDay,
            savedAt: savedAt,
            userId: userId
        )
    }
    
    // MARK: - Save Challenge
    
    func saveChallenge(_ challenge: DailyChallenge) {
        guard let firebaseUser = Auth.auth().currentUser,
              let currentUser = appState?.currentUser else {
            print("❌ SavedChallengesService: Aucun utilisateur connecté")
            return
        }
        
        print("💾 SavedChallengesService: ✅ CORRECTION - Sauvegarde avec Firebase UID")
        print("💾 SavedChallengesService: Firebase UID: \(firebaseUser.uid)")
        
        isLoading = true
        
        let savedChallenge = SavedChallenge(
            challengeKey: challenge.challengeKey,
            challengeDay: challenge.challengeDay,
            userId: firebaseUser.uid
        )
        
        let documentId = SavedChallenge.generateId(userId: firebaseUser.uid, challengeKey: challenge.challengeKey)
        
        let challengeData: [String: Any] = [
            "challengeKey": savedChallenge.challengeKey,
            "challengeDay": savedChallenge.challengeDay,
            "savedAt": Timestamp(date: savedChallenge.savedAt),
            "userId": savedChallenge.userId
        ]
        
        print("💾 SavedChallengesService: Sauvegarde défi: \(challenge.challengeKey)")
        
        db.collection("savedChallenges").document(documentId).setData(challengeData) { [weak self] error in
            guard let self = self else { return }
            
            self.isLoading = false
            
            if let error = error {
                print("❌ SavedChallengesService: Erreur sauvegarde: \(error)")
            } else {
                print("✅ SavedChallengesService: Défi sauvegardé avec succès")
                self.lastSavedChallenge = savedChallenge
                
                // Réinitialiser après 3 secondes pour l'animation
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                    self.lastSavedChallenge = nil
                }
            }
        }
    }
    
    // MARK: - Delete Challenge
    
    func deleteChallenge(_ savedChallenge: SavedChallenge) {
        guard let currentUser = appState?.currentUser else {
            print("❌ SavedChallengesService: Aucun utilisateur connecté")
            return
        }
        
        print("🗑️ SavedChallengesService: === DEBUG SUPPRESSION ===")
        print("🗑️ SavedChallengesService: Défi à supprimer: \(savedChallenge.challengeKey)")
        print("🗑️ SavedChallengesService: ID du défi: \(savedChallenge.id)")
        print("🗑️ SavedChallengesService: Utilisateur: \(currentUser.id)")
        print("🗑️ SavedChallengesService: Nombre de défis avant suppression: \(savedChallenges.count)")
        
        // Supprimer localement d'abord pour une réponse rapide
        DispatchQueue.main.async {
            self.savedChallenges.removeAll { $0.id == savedChallenge.id }
            print("🗑️ SavedChallengesService: Suppression locale effectuée. Nouveau count: \(self.savedChallenges.count)")
        }
        
        // Puis supprimer de Firebase
        db.collection("savedChallenges").document(savedChallenge.id).delete { error in
            if let error = error {
                print("❌ SavedChallengesService: Erreur suppression Firebase: \(error)")
                
                // En cas d'erreur, restaurer l'élément localement
                DispatchQueue.main.async {
                    self.savedChallenges.append(savedChallenge)
                    print("🔄 SavedChallengesService: Élément restauré suite à l'erreur")
                }
            } else {
                print("✅ SavedChallengesService: Défi supprimé avec succès de Firebase")
            }
        }
    }
    
    // MARK: - Check if Challenge is Saved
    
    func isChallengeAlreadySaved(_ challenge: DailyChallenge) -> Bool {
        return savedChallenges.contains { saved in
            saved.challengeKey == challenge.challengeKey
        }
    }
    
    // MARK: - Public Methods
    
    func refreshSavedChallenges() {
        setupListener()
    }
    
    func getSavedChallengesCount() -> Int {
        return savedChallenges.count
    }
    
    func clearSavedChallenges() {
        guard let currentUser = appState?.currentUser else { return }
        
        print("🗑️ SavedChallengesService: Suppression de tous les défis sauvegardés")
        
        let batch = db.batch()
        
        for savedChallenge in savedChallenges {
            let documentRef = db.collection("savedChallenges").document(savedChallenge.id)
            batch.deleteDocument(documentRef)
        }
        
        batch.commit { error in
            if let error = error {
                print("❌ SavedChallengesService: Erreur suppression batch: \(error)")
            } else {
                print("✅ SavedChallengesService: Tous les défis sauvegardés supprimés")
            }
        }
    }
}