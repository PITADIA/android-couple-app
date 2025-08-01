import Foundation
import RealmSwift
import Combine
import FirebaseFirestore
import FirebaseAuth
import FirebaseFunctions
import FirebaseAnalytics

class FavoritesService: ObservableObject {
    @Published var favoriteQuestions: [FavoriteQuestion] = []
    @Published var sharedFavoriteQuestions: [SharedFavoriteQuestion] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    private var realm: Realm?
    private var currentUserId: String?
    private var currentUserName: String?
    private var appState: AppState?
    
    // Firebase
    private let db = Firestore.firestore()
    private var listener: ListenerRegistration?
    
    init() {
        initializeRealm()
    }
    
    // MARK: - Configuration
    
    func configure(with appState: AppState) {
        self.appState = appState
        
        // Observer les changements d'utilisateur
        appState.$currentUser
            .sink { [weak self] user in
                Task { @MainActor in
                    if let user = user {
                        self?.setCurrentUser(user.id, name: user.name)
                    } else {
                        self?.clearCurrentUser()
                    }
                }
            }
            .store(in: &cancellables)
    }
    
    private var cancellables = Set<AnyCancellable>()
    
    private func initializeRealm() {
        do {
            var config = Realm.Configuration.defaultConfiguration
            config.schemaVersion = 3 // Incrémenté pour les favoris partagés
            config.migrationBlock = { migration, oldSchemaVersion in
                // Migration automatique pour les nouveaux modèles
                if oldSchemaVersion < 3 {
                    // Ajouter les favoris partagés si nécessaire
                }
            }
            
            self.realm = try Realm(configuration: config)
            print("🔥 FavoritesService: Realm initialisé")
        } catch {
            print("❌ FavoritesService: Erreur Realm: \(error)")
            self.realm = nil
        }
    }
    
    // MARK: - User Management
    
    @MainActor
    func setCurrentUser(_ userId: String, name: String) {
        print("🔥 FavoritesService: Configuration utilisateur: \(userId)")
        print("🔥 FavoritesService: Nom utilisateur: \(name)")
        print("🔥 FavoritesService: UID Firebase actuel: \(Auth.auth().currentUser?.uid ?? "nil")")
        print("🔥 FavoritesService: UID correspond: \(userId == Auth.auth().currentUser?.uid)")
        
        self.currentUserId = userId
        self.currentUserName = name
        
        loadLocalFavorites()
        setupFirestoreListener()
    }
    
    @MainActor
    func clearCurrentUser() {
        print("🔥 FavoritesService: Nettoyage utilisateur")
        self.currentUserId = nil
        self.currentUserName = nil
        self.favoriteQuestions = []
        self.sharedFavoriteQuestions = []
        
        listener?.remove()
        listener = nil
    }
    
    // MARK: - Firestore Listener
    
    private func setupFirestoreListener() {
        guard let currentUserId = currentUserId else {
            print("❌ FavoritesService: Aucun utilisateur pour le listener")
            return
        }
        
        print("🔥 FavoritesService: Configuration du listener Firestore pour: \(currentUserId)")
        
        // Arrêter l'ancien listener
        listener?.remove()
        
        // Écouter les favoris partagés
        print("🔥 FavoritesService: Configuration query Firestore...")
        print("🔥 FavoritesService: - Collection: favoriteQuestions")
        print("🔥 FavoritesService: - Filtre: partnerIds array-contains \(currentUserId)")
        
        // Utiliser Firebase UID pour le listener
        let firebaseUID = Auth.auth().currentUser?.uid ?? currentUserId
        print("🔥 FavoritesService: - Listener avec Firebase UID: \(firebaseUID)")
        
        listener = db.collection("favoriteQuestions")
            .whereField("partnerIds", arrayContains: firebaseUID)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    print("❌ FavoritesService: Erreur listener: \(error)")
                    print("❌ FavoritesService: Code erreur: \(error.localizedDescription)")
                    if let firestoreError = error as NSError? {
                        print("❌ FavoritesService: Firestore error code: \(firestoreError.code)")
                        print("❌ FavoritesService: Firestore domain: \(firestoreError.domain)")
                    }
                    return
                }
                
                print("✅ FavoritesService: Réception mise à jour Firestore")
                if let documents = snapshot?.documents {
                    print("✅ FavoritesService: \(documents.count) document(s) reçu(s)")
                } else {
                    print("⚠️ FavoritesService: Snapshot sans documents")
                }
                
                self?.handleFirestoreUpdate(snapshot: snapshot)
            }
    }
    
    private func handleFirestoreUpdate(snapshot: QuerySnapshot?) {
        guard let documents = snapshot?.documents else { 
            print("⚠️ FavoritesService: Aucun document dans le snapshot")
            return 
        }
        
        print("🔥 FavoritesService: Traitement \(documents.count) document(s) Firestore")
        
        let newSharedFavorites = documents.compactMap { document in
            print("🔥 FavoritesService: - Document ID: \(document.documentID)")
            let favorite = SharedFavoriteQuestion(from: document)
            if favorite == nil {
                print("❌ FavoritesService: Échec parsing document \(document.documentID)")
            }
            return favorite
        }
        
        print("🔥 FavoritesService: \(newSharedFavorites.count) favoris parsés avec succès")
        
        DispatchQueue.main.async {
            self.sharedFavoriteQuestions = newSharedFavorites.sorted { $0.dateAdded > $1.dateAdded }
            print("🔥 FavoritesService: \(self.sharedFavoriteQuestions.count) favoris partagés chargés")
            
            // Afficher détails des favoris pour debug
            for (index, favorite) in self.sharedFavoriteQuestions.enumerated() {
                print("🔥 FavoritesService: [\(index)] \(favorite.questionText.prefix(30))... (auteur: \(favorite.authorName))")
            }
            
            // Synchroniser avec le cache local
            self.syncToLocalCache()
        }
    }
    
    // MARK: - Core Favorites Functions (Hybride)
    
    @MainActor
    func toggleFavorite(question: Question, category: QuestionCategory) {
        guard let userId = currentUserId, let userName = currentUserName else {
            print("❌ FavoritesService: Pas d'utilisateur connecté")
            return
        }
        
        if isFavorite(questionId: question.id) {
            removeFavorite(questionId: question.id)
        } else {
            addFavorite(question: question, category: category, userId: userId, userName: userName)
        }
    }
    
    @MainActor
    func addFavorite(question: Question, category: QuestionCategory, userId: String, userName: String) {
        // Vérifier si déjà en favoris
        if isFavorite(questionId: question.id) {
            print("⚠️ FavoritesService: Question déjà en favoris")
            return
        }
        
        isLoading = true
        
        Task {
            do {
                // Déterminer les partenaires avec qui partager (utiliser UNIQUEMENT des Firebase UIDs)
                var partnerIds: [String] = []
                
                // Toujours inclure l'auteur (Firebase UID)
                if let firebaseUID = Auth.auth().currentUser?.uid {
                    partnerIds.append(firebaseUID)
                }
                
                // Ajouter le partenaire (Firebase UID) 
                if let appState = appState, let partnerId = appState.currentUser?.partnerId {
                    partnerIds.append(partnerId)
                }
                
                print("🔥 FavoritesService: - partnerIds construits avec Firebase UIDs: \(partnerIds)")
                
                let sharedFavorite = SharedFavoriteQuestion(
                    questionId: question.id,
                    questionText: question.text,
                    categoryTitle: category.title,
                    emoji: category.emoji,
                    authorId: Auth.auth().currentUser?.uid ?? userId, // Utiliser Firebase UID comme authorId
                    authorName: userName,
                    partnerIds: partnerIds
                )
                
                // Sauvegarder dans Firestore
                let documentRef = db.collection("favoriteQuestions").document(sharedFavorite.id)
                let data = sharedFavorite.toDictionary()
                
                print("🔥 FavoritesService: Sauvegarde Firestore...")
                print("🔥 FavoritesService: - Document ID: \(sharedFavorite.id)")
                print("🔥 FavoritesService: - Author ID: \(sharedFavorite.authorId)")
                print("🔥 FavoritesService: - Partner IDs: \(sharedFavorite.partnerIds)")
                print("🔥 FavoritesService: - Collection: favoriteQuestions")
                print("🔥 FavoritesService: - Firebase UID actuel: \(Auth.auth().currentUser?.uid ?? "nil")")
                print("🔥 FavoritesService: - currentUserId (service): \(currentUserId ?? "nil")")
                print("🔥 FavoritesService: - Data à sauvegarder: \(data)")
                
                try await documentRef.setData(data)
                
                print("✅ FavoritesService: Favori partagé ajouté: \(question.text.prefix(50))...")
                print("✅ FavoritesService: Document Firestore créé avec succès")
                
                // 📊 Analytics: Question mise en favori
                Analytics.logEvent("question_favoriee", parameters: [
                    "question_id": question.id,
                    "categorie": category.id
                ])
                print("📊 Événement Firebase: question_favoriee - \(question.id) - \(category.id)")
                
                // Plus besoin de tracker les favoris - on se base maintenant sur les questions vues
                
                await MainActor.run {
                    self.isLoading = false
                }
                
            } catch {
                print("❌ FavoritesService: Erreur ajout favori partagé: \(error)")
                print("❌ FavoritesService: - Type erreur: \(type(of: error))")
                print("❌ FavoritesService: - Description détaillée: \(error.localizedDescription)")
                if let firestoreError = error as NSError? {
                    print("❌ FavoritesService: - Code erreur: \(firestoreError.code)")
                    print("❌ FavoritesService: - Domaine erreur: \(firestoreError.domain)")
                    print("❌ FavoritesService: - UserInfo: \(firestoreError.userInfo)")
                }
                await MainActor.run {
                    self.isLoading = false
                    self.errorMessage = "Erreur lors de l'ajout aux favoris"
                }
                
                // Fallback: ajouter en local seulement
                addLocalFavorite(question: question, category: category, userId: userId)
            }
        }
    }
    
    @MainActor
    func removeFavorite(questionId: String) {
        guard let userId = currentUserId else {
            print("❌ FavoritesService: Pas d'utilisateur connecté")
            return
        }
        
        print("🔥 FavoritesService: SUPPRESSION - Question ID: \(questionId)")
        print("🔥 FavoritesService: SUPPRESSION - Utilisateur: \(userId)")
        
        isLoading = true
        
        Task {
            do {
                // Trouver le favori partagé correspondant
                if let sharedFavorite = sharedFavoriteQuestions.first(where: { $0.questionId == questionId }) {
                    print("🔥 FavoritesService: SUPPRESSION - Favori trouvé dans Firestore")
                    print("🔥 FavoritesService: SUPPRESSION - Document ID: \(sharedFavorite.id)")
                    print("🔥 FavoritesService: SUPPRESSION - Auteur: \(sharedFavorite.authorId)")
                    
                    let isAuthor = userId == sharedFavorite.authorId
                    let isInPartnerIds = sharedFavorite.partnerIds.contains(userId)
                    let canDelete = isAuthor || isInPartnerIds
                    
                    print("🔥 FavoritesService: SUPPRESSION - Est auteur: \(isAuthor)")
                    print("🔥 FavoritesService: SUPPRESSION - Dans partnerIds: \(isInPartnerIds)")
                    print("🔥 FavoritesService: SUPPRESSION - Peut supprimer: \(canDelete)")
                    
                    if canDelete {
                        // Supprimer de Firestore
                        try await db.collection("favoriteQuestions")
                            .document(sharedFavorite.id)
                            .delete()
                        
                        print("✅ FavoritesService: Favori partagé supprimé de Firestore")
                        
                        // Plus besoin de tracker la suppression de favoris pour les reviews
                    } else {
                        print("❌ FavoritesService: Impossible de supprimer - Permissions insuffisantes")
                        await MainActor.run {
                            self.isLoading = false
                            self.errorMessage = "Vous n'avez pas les permissions pour supprimer ce favori"
                        }
                        return
                    }
                } else {
                    print("⚠️ FavoritesService: SUPPRESSION - Favori non trouvé dans Firestore")
                }
                
                await MainActor.run {
                    self.isLoading = false
                }
                
            } catch {
                print("❌ FavoritesService: Erreur suppression favori partagé: \(error)")
                await MainActor.run {
                    self.isLoading = false
                    self.errorMessage = "Erreur lors de la suppression"
                }
            }
        }
        
        // Supprimer aussi du cache local
        removeLocalFavorite(questionId: questionId)
    }
    
    func isFavorite(questionId: String) -> Bool {
        // Vérifier d'abord dans les favoris partagés
        if sharedFavoriteQuestions.contains(where: { $0.questionId == questionId }) {
            return true
        }
        
        // Puis dans le cache local
        return favoriteQuestions.contains(where: { $0.questionId == questionId })
    }
    
    func canDeleteFavorite(questionId: String) -> Bool {
        guard let userId = currentUserId else { 
            print("❌ FavoritesService: Pas d'utilisateur pour vérifier la suppression")
            return false 
        }
        
        // Vérifier si l'utilisateur peut supprimer le favori partagé (auteur OU dans partnerIds)
        if let sharedFavorite = sharedFavoriteQuestions.first(where: { $0.questionId == questionId }) {
            let isAuthor = userId == sharedFavorite.authorId
            let isInPartnerIds = sharedFavorite.partnerIds.contains(userId)
            let canDelete = isAuthor || isInPartnerIds
            
            print("🔥 FavoritesService: Peut supprimer \(questionId): \(canDelete)")
            print("🔥 FavoritesService: - Est auteur: \(isAuthor)")
            print("🔥 FavoritesService: - Dans partnerIds: \(isInPartnerIds)")
            return canDelete
        }
        
        // Si c'est seulement un favori local, l'utilisateur peut le supprimer
        if favoriteQuestions.contains(where: { $0.questionId == questionId }) {
            print("🔥 FavoritesService: Favori local - peut supprimer")
            return true
        }
        
        print("❌ FavoritesService: Favori non trouvé pour suppression")
        return false
    }
    
    // MARK: - Local Cache Management (Realm)
    
    private func addLocalFavorite(question: Question, category: QuestionCategory, userId: String) {
        guard let realm = realm else {
            print("❌ FavoritesService: Realm non disponible")
            return
        }
        
        do {
            try realm.write {
                let favorite = RealmFavoriteQuestion(question: question, category: category, userId: userId)
                realm.add(favorite)
            }
            
            Task { @MainActor in
                self.loadLocalFavorites()
            }
            print("✅ FavoritesService: Favori local ajouté: \(question.text.prefix(50))...")
            
        } catch {
            print("❌ FavoritesService: Erreur ajout favori local: \(error)")
        }
    }
    
    private func removeLocalFavorite(questionId: String) {
        guard let realm = realm, let userId = currentUserId else {
            print("❌ FavoritesService: Realm ou utilisateur non disponible")
            return
        }
        
        do {
            try realm.write {
                let favorites = realm.objects(RealmFavoriteQuestion.self)
                    .filter("questionId == %@ AND userId == %@", questionId, userId)
                
                realm.delete(favorites)
            }
            
            Task { @MainActor in
                self.loadLocalFavorites()
            }
            print("✅ FavoritesService: Favori local supprimé")
            
        } catch {
            print("❌ FavoritesService: Erreur suppression favori local: \(error)")
        }
    }
    
    @MainActor
    private func loadLocalFavorites() {
        guard let realm = realm, let userId = currentUserId else {
            favoriteQuestions = []
            return
        }
        
        let realmFavorites = realm.objects(RealmFavoriteQuestion.self)
            .filter("userId == %@", userId)
            .sorted(byKeyPath: "dateAdded", ascending: false)
        
        favoriteQuestions = Array(realmFavorites.map { $0.toFavoriteItem() })
        
        print("🔥 FavoritesService: \(favoriteQuestions.count) favoris locaux chargés")
        
        // Plus besoin de synchroniser le compteur de favoris pour les reviews
    }
    
    private func syncToLocalCache() {
        guard let realm = realm, let userId = currentUserId else {
            print("❌ FavoritesService: Realm ou utilisateur non disponible pour sync")
            return
        }
        
        print("🔥 FavoritesService: SYNC CACHE - Début synchronisation")
        print("🔥 FavoritesService: SYNC CACHE - \(sharedFavoriteQuestions.count) favoris partagés")
        
        do {
            try realm.write {
                // 1. Obtenir tous les favoris locaux actuels
                let localFavorites = realm.objects(RealmFavoriteQuestion.self)
                    .filter("userId == %@", userId)
                
                let localQuestionIds = Set(localFavorites.map { $0.questionId })
                let sharedQuestionIds = Set(sharedFavoriteQuestions.map { $0.questionId })
                
                print("🔥 FavoritesService: SYNC CACHE - \(localQuestionIds.count) favoris locaux")
                print("🔥 FavoritesService: SYNC CACHE - Question IDs partagés: \(sharedQuestionIds)")
                print("🔥 FavoritesService: SYNC CACHE - Question IDs locaux: \(localQuestionIds)")
                
                // 2. Supprimer du cache local les favoris qui ne sont plus partagés
                let idsToRemove = localQuestionIds.subtracting(sharedQuestionIds)
                print("🔥 FavoritesService: SYNC CACHE - \(idsToRemove.count) favoris à supprimer: \(idsToRemove)")
                
                for questionId in idsToRemove {
                    let favoritesToDelete = localFavorites.filter("questionId == %@", questionId)
                    realm.delete(favoritesToDelete)
                    print("🔥 FavoritesService: SYNC CACHE - Supprimé du cache: \(questionId)")
                }
                
                // 3. Ajouter les nouveaux favoris partagés au cache local
                let idsToAdd = sharedQuestionIds.subtracting(localQuestionIds)
                print("🔥 FavoritesService: SYNC CACHE - \(idsToAdd.count) favoris à ajouter: \(idsToAdd)")
                
                for sharedFavorite in sharedFavoriteQuestions {
                    if idsToAdd.contains(sharedFavorite.questionId) {
                        let localFavorite = sharedFavorite.toLocalFavorite()
                        let realmFavorite = RealmFavoriteQuestion()
                        realmFavorite.id = localFavorite.id
                        realmFavorite.questionId = localFavorite.questionId
                        realmFavorite.userId = userId
                        realmFavorite.categoryTitle = localFavorite.categoryTitle
                        realmFavorite.questionText = localFavorite.questionText
                        realmFavorite.emoji = localFavorite.emoji
                        realmFavorite.dateAdded = localFavorite.dateAdded
                        
                        realm.add(realmFavorite, update: .modified)
                        print("🔥 FavoritesService: SYNC CACHE - Ajouté au cache: \(sharedFavorite.questionId)")
                    }
                }
            }
            
            print("✅ FavoritesService: SYNC CACHE - Synchronisation terminée")
            
        } catch {
            print("❌ FavoritesService: Erreur sync cache: \(error)")
        }
        
        // Recharger les favoris locaux
        Task { @MainActor in
            self.loadLocalFavorites()
        }
    }
    
    // MARK: - Data Access (Combiné)
    
    func getAllFavorites() -> [FavoriteQuestion] {
        // Combiner favoris partagés et locaux, éviter les doublons
        var allFavorites: [FavoriteQuestion] = []
        
        // Ajouter les favoris partagés convertis
        allFavorites.append(contentsOf: sharedFavoriteQuestions.map { $0.toLocalFavorite() })
        
        // Ajouter les favoris locaux qui ne sont pas déjà dans les partagés
        for localFavorite in favoriteQuestions {
            if !allFavorites.contains(where: { $0.questionId == localFavorite.questionId }) {
                allFavorites.append(localFavorite)
            }
        }
        
        return allFavorites.sorted { $0.dateAdded > $1.dateAdded }
    }
    
    // MARK: - Filtering and Sorting
    
    func getFavoritesByCategory() -> [String: [FavoriteQuestion]] {
        let allFavorites = getAllFavorites()
        var grouped: [String: [FavoriteQuestion]] = [:]
        
        for favorite in allFavorites {
            if grouped[favorite.categoryTitle] == nil {
                grouped[favorite.categoryTitle] = []
            }
            grouped[favorite.categoryTitle]?.append(favorite)
        }
        
        return grouped
    }
    
    func getRecentFavorites(limit: Int = 10) -> [FavoriteQuestion] {
        return Array(getAllFavorites().prefix(limit))
    }
    
    func searchFavorites(query: String) -> [FavoriteQuestion] {
        guard !query.isEmpty else { return getAllFavorites() }
        
        return getAllFavorites().filter { favorite in
            favorite.questionText.localizedCaseInsensitiveContains(query) ||
            favorite.categoryTitle.localizedCaseInsensitiveContains(query)
        }
    }
    
    // MARK: - Statistics
    
    func getFavoritesCount() -> Int {
        return getAllFavorites().count
    }
    
    func getFavoritesCountByCategory() -> [String: Int] {
        let grouped = getFavoritesByCategory()
        return grouped.mapValues { $0.count }
    }
    
    // MARK: - Partner Sync
    
    func syncPartnerFavorites(partnerId: String, completion: @escaping (Bool, String?) -> Void) {
        print("❤️ FavoritesService: Début synchronisation favoris avec partenaire: \(partnerId)")
        
        guard Auth.auth().currentUser != nil else {
            print("❌ FavoritesService: Aucun utilisateur connecté")
            completion(false, "Utilisateur non connecté")
            return
        }
        
        let functions = Functions.functions()
        let syncFunction = functions.httpsCallable("syncPartnerFavorites")
        
        syncFunction.call(["partnerId": partnerId]) { result, error in
            if let error = error {
                print("❌ FavoritesService: Erreur synchronisation favoris: \(error.localizedDescription)")
                completion(false, "Erreur lors de la synchronisation: \(error.localizedDescription)")
                return
            }
            
            guard let data = result?.data as? [String: Any],
                  let success = data["success"] as? Bool else {
                print("❌ FavoritesService: Réponse invalide de la fonction")
                completion(false, "Réponse invalide du serveur")
                return
            }
            
            if success {
                let updatedCount = data["updatedFavoritesCount"] as? Int ?? 0
                print("✅ FavoritesService: Synchronisation réussie - \(updatedCount) favoris mis à jour")
                completion(true, "Synchronisation réussie: \(updatedCount) favoris mis à jour")
            } else {
                let message = data["message"] as? String ?? "Erreur inconnue"
                print("❌ FavoritesService: Échec synchronisation: \(message)")
                completion(false, message)
            }
        }
    }
    
    // MARK: - Cleanup
    
    @MainActor
    func clearAllFavorites() {
        guard let userId = currentUserId else { return }
        
        // Supprimer les favoris partagés
        Task {
            do {
                for sharedFavorite in sharedFavoriteQuestions.filter({ $0.authorId == userId }) {
                    try await db.collection("favoriteQuestions")
                        .document(sharedFavorite.id)
                        .delete()
                }
            } catch {
                print("❌ FavoritesService: Erreur suppression favoris partagés: \(error)")
            }
        }
        
        // Supprimer le cache local
        guard let realm = realm else { return }
        
        do {
            try realm.write {
                let userFavorites = realm.objects(RealmFavoriteQuestion.self)
                    .filter("userId == %@", userId)
                realm.delete(userFavorites)
            }
            
            loadLocalFavorites()
            print("🔥 FavoritesService: Tous les favoris supprimés")
            
        } catch {
            print("❌ FavoritesService: Erreur suppression favoris: \(error)")
        }
    }
    
    deinit {
        listener?.remove()
    }
} 