import Foundation
import FirebaseAuth
import FirebaseFirestore
import AuthenticationServices
import Combine

class FirebaseService: NSObject, ObservableObject {
    static let shared = FirebaseService()
    
    @Published var isAuthenticated = false
    @Published var currentUser: User?
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    private let db = Firestore.firestore()
    private var cancellables = Set<AnyCancellable>()
    
    // NOUVEAU: Flag pour éviter les redirections automatiques pendant l'onboarding
    private var isOnboardingInProgress = false
    
    override init() {
        super.init()
        print("🔥 FirebaseService: Initialisation")
        checkAuthenticationState()
    }
    
    // MARK: - Authentication State
    
    func checkAuthenticationState() {
        print("🔥 FirebaseService: Vérification de l'état d'authentification")
        Auth.auth().addStateDidChangeListener { [weak self] _, firebaseUser in
            DispatchQueue.main.async {
                if let firebaseUser = firebaseUser {
                    print("🔥 FirebaseService: Utilisateur Firebase trouvé - UID: \(firebaseUser.uid)")
                    print("🔥 FirebaseService: Providers: \(firebaseUser.providerData.map { $0.providerID })")
                    
                    // Vérifier que c'est bien une authentification Apple
                    if firebaseUser.providerData.contains(where: { $0.providerID == "apple.com" }) {
                        print("🔥 FirebaseService: Authentification Apple confirmée")
                        self?.loadUserData(uid: firebaseUser.uid)
                    } else {
                        print("❌ FirebaseService: Authentification non-Apple détectée, déconnexion")
                        self?.signOut()
                    }
                } else {
                    print("🔥 FirebaseService: Aucun utilisateur Firebase")
                    self?.isAuthenticated = false
                    self?.currentUser = nil
                }
            }
        }
    }
    
    // NOUVEAU: Méthode pour marquer le début de l'onboarding
    func startOnboardingProcess() {
        print("🔥🔥🔥 FIREBASE: DEBUT PROCESSUS ONBOARDING - BLOCAGE REDIRECTIONS AUTO")
        isOnboardingInProgress = true
    }
    
    // NOUVEAU: Méthode pour marquer la fin de l'onboarding
    func completeOnboardingProcess() {
        print("🔥🔥🔥 FIREBASE: FIN PROCESSUS ONBOARDING - AUTORISATION REDIRECTIONS")
        isOnboardingInProgress = false
    }
    
    // MARK: - Sign in with Apple (UNIQUEMENT)
    
    func signInWithApple(authorization: ASAuthorization) {
        guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            self.errorMessage = "Erreur d'authentification Apple"
            return
        }
        
        guard let nonce = currentNonce else {
            self.errorMessage = "Erreur de sécurité"
            return
        }
        
        guard let appleIDToken = appleIDCredential.identityToken else {
            self.errorMessage = "Token Apple manquant"
            return
        }
        
        guard let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
            self.errorMessage = "Erreur de décodage du token"
            return
        }
        
        let credential = OAuthProvider.credential(withProviderID: "apple.com",
                                                  idToken: idTokenString,
                                                  rawNonce: nonce)
        
        isLoading = true
        
        Auth.auth().signIn(with: credential) { [weak self] result, error in
            DispatchQueue.main.async {
                self?.isLoading = false
                
                if let error = error {
                    self?.errorMessage = "Erreur de connexion: \(error.localizedDescription)"
                    return
                }
                
                guard let firebaseUser = result?.user else {
                    self?.errorMessage = "Erreur de connexion"
                    return
                }
                
                // Vérifier si c'est un nouvel utilisateur
                if result?.additionalUserInfo?.isNewUser == true {
                    // Créer un profil utilisateur vide pour un nouvel utilisateur
                    self?.createEmptyUserProfile(
                        uid: firebaseUser.uid, 
                        email: firebaseUser.email,
                        name: appleIDCredential.fullName?.givenName
                    )
                } else {
                    // Charger les données existantes
                    self?.loadUserData(uid: firebaseUser.uid)
                }
            }
        }
    }
    
    // MARK: - User Data Management
    
    func savePartialUserData(_ user: User) {
        print("🔥🔥🔥 FIREBASE PARTIAL: SAUVEGARDE PARTIELLE PENDANT ONBOARDING")
        print("🔥🔥🔥 FIREBASE PARTIAL: - Utilisateur: \(user.name)")
        
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Aucun utilisateur Firebase connecté")
            self.errorMessage = "Utilisateur non connecté"
            return
        }
        
        // Vérifier que c'est bien Apple ID
        guard firebaseUser.providerData.contains(where: { $0.providerID == "apple.com" }) else {
            print("❌ FirebaseService: Authentification Apple requise")
            self.errorMessage = "Authentification Apple requise"
            return
        }
        
        let userData: [String: Any] = [
            "id": user.id,
            "name": user.name,
            "birthDate": Timestamp(date: user.birthDate),
            "relationshipGoals": user.relationshipGoals,
            "relationshipDuration": user.relationshipDuration.rawValue,
            "partnerCode": user.partnerCode ?? "",
            "isSubscribed": user.isSubscribed,
            "appleUserID": firebaseUser.uid,
            "lastLoginDate": Timestamp(date: Date()),
            "createdAt": Timestamp(date: Date()),
            "updatedAt": Timestamp(date: Date()),
            "onboardingInProgress": true  // IMPORTANT: Marquer l'onboarding comme en cours
        ]
        
        print("🔥🔥🔥 FIREBASE PARTIAL: DONNEES PARTIELLES A SAUVEGARDER:")
        print("🔥🔥🔥 FIREBASE PARTIAL: - onboardingInProgress: true (en cours)")
        
        db.collection("users").document(firebaseUser.uid).setData(userData, merge: true) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ FirebaseService: Erreur de sauvegarde partielle: \(error.localizedDescription)")
                    self?.errorMessage = "Erreur de sauvegarde: \(error.localizedDescription)"
                } else {
                    print("✅ FirebaseService: Données partielles sauvegardées avec succès")
                    print("🔥🔥🔥 FIREBASE PARTIAL: SAUVEGARDE PARTIELLE REUSSIE - ONBOARDING EN COURS")
                    // Ne pas mettre à jour currentUser ni isAuthenticated ici
                    // pour éviter de déclencher la redirection
                }
            }
        }
    }
    
    func saveUserData(_ user: User) {
        print("🔥 FirebaseService: Tentative de sauvegarde des données utilisateur")
        print("🔥 FirebaseService: Nom: \(user.name)")
        
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Aucun utilisateur Firebase connecté")
            self.errorMessage = "Utilisateur non connecté"
            return
        }
        
        print("🔥 FirebaseService: Utilisateur Firebase UID: \(firebaseUser.uid)")
        
        // Vérifier que c'est bien Apple ID
        guard firebaseUser.providerData.contains(where: { $0.providerID == "apple.com" }) else {
            print("❌ FirebaseService: Authentification Apple requise")
            self.errorMessage = "Authentification Apple requise"
            return
        }
        
        print("🔥🔥🔥 FIREBASE SAVE: DEBUT DE LA SAUVEGARDE")
        print("🔥🔥🔥 FIREBASE SAVE: - Utilisateur: \(user.name)")
        print("🔥🔥🔥 FIREBASE SAVE: - Abonné: \(user.isSubscribed)")
        isLoading = true
        
        let userData: [String: Any] = [
            "id": user.id,
            "name": user.name,
            "birthDate": Timestamp(date: user.birthDate),
            "relationshipGoals": user.relationshipGoals,
            "relationshipDuration": user.relationshipDuration.rawValue,
            "partnerCode": user.partnerCode ?? "",
            "isSubscribed": user.isSubscribed,
            "appleUserID": firebaseUser.uid, // Lier à l'Apple ID
            "lastLoginDate": Timestamp(date: Date()),
            "createdAt": Timestamp(date: Date()),
            "updatedAt": Timestamp(date: Date()),
            "onboardingInProgress": false  // NOUVEAU: Marquer l'onboarding comme terminé
        ]
        
        print("🔥🔥🔥 FIREBASE SAVE: DONNEES A SAUVEGARDER:")
        print("🔥🔥🔥 FIREBASE SAVE: - onboardingInProgress: false (terminé)")
        
        db.collection("users").document(firebaseUser.uid).setData(userData, merge: true) { [weak self] error in
            DispatchQueue.main.async {
                self?.isLoading = false
                
                if let error = error {
                    print("❌ FirebaseService: Erreur de sauvegarde Firestore: \(error.localizedDescription)")
                    self?.errorMessage = "Erreur de sauvegarde: \(error.localizedDescription)"
                } else {
                    print("✅ FirebaseService: Données utilisateur sauvegardées avec succès")
                    print("🔥🔥🔥 FIREBASE SAVE: SAUVEGARDE REUSSIE - ONBOARDING TERMINE")
                    self?.currentUser = user
                    self?.isAuthenticated = true
                    print("✅ Données utilisateur sauvegardées avec Apple ID")
                }
            }
        }
    }
    
    func loadUserData(uid: String) {
        print("🔥 FirebaseService: Chargement des données pour UID: \(uid)")
        isLoading = true
        
        db.collection("users").document(uid).getDocument { [weak self] document, error in
            DispatchQueue.main.async {
                self?.isLoading = false
                
                if let error = error {
                    print("❌ FirebaseService: Erreur de chargement: \(error.localizedDescription)")
                    self?.errorMessage = "Erreur de chargement: \(error.localizedDescription)"
                    return
                }
                
                guard let document = document, document.exists,
                      let data = document.data() else {
                    print("🔥 FirebaseService: Aucune donnée trouvée pour l'utilisateur, onboarding requis")
                    // Aucune donnée trouvée, utilisateur doit compléter l'onboarding
                    self?.isAuthenticated = true
                    self?.currentUser = nil
                    return
                }
                
                print("🔥 FirebaseService: Données trouvées: \(data)")
                
                // SOLUTION TEMPORAIRE: Vérifier si c'est un utilisateur qui se reconnecte après suppression
                // Si l'utilisateur a des données mais qu'il vient de faire l'onboarding, c'est suspect
                let lastLoginDate = data["lastLoginDate"] as? Timestamp
                let createdAt = data["createdAt"] as? Timestamp
                let now = Date()
                
                if let lastLogin = lastLoginDate?.dateValue(),
                   let created = createdAt?.dateValue() {
                    let timeSinceLastLogin = now.timeIntervalSince(lastLogin)
                    let timeSinceCreation = now.timeIntervalSince(created)
                    
                    // Si l'utilisateur a été créé récemment (moins de 5 minutes) et qu'il se reconnecte
                    // c'est probablement un cas de suppression ratée
                    if timeSinceCreation < 300 && timeSinceLastLogin > 60 {
                        print("🔥 FirebaseService: DÉTECTION - Possible reconnexion après suppression ratée")
                        print("🔥 FirebaseService: - Créé il y a: \(timeSinceCreation) secondes")
                        print("🔥 FirebaseService: - Dernière connexion il y a: \(timeSinceLastLogin) secondes")
                        print("🔥 FirebaseService: SUPPRESSION FORCÉE des données résiduelles")
                        
                        // Supprimer les données résiduelles
                        self?.db.collection("users").document(uid).delete { deleteError in
                            if let deleteError = deleteError {
                                print("❌ FirebaseService: Erreur suppression forcée: \(deleteError.localizedDescription)")
                            } else {
                                print("✅ FirebaseService: Données résiduelles supprimées avec succès")
                            }
                        }
                        
                        // Forcer l'onboarding
                        self?.isAuthenticated = true
                        self?.currentUser = nil
                        return
                    }
                }
                
                // Vérifier que les données sont complètes pour un onboarding terminé
                let name = data["name"] as? String ?? ""
                let relationshipGoals = data["relationshipGoals"] as? [String] ?? []
                let relationshipDuration = data["relationshipDuration"] as? String ?? ""
                let birthDate = data["birthDate"] as? Timestamp
                
                // NOUVEAU: Vérifier si l'utilisateur est en cours d'onboarding
                let onboardingInProgress = data["onboardingInProgress"] as? Bool ?? false
                
                print("🔥🔥🔥 FIREBASE LOAD: VERIFICATION DES DONNEES")
                print("🔥🔥🔥 FIREBASE LOAD: - Nom: '\(name)' (vide: \(name.isEmpty))")
                print("🔥🔥🔥 FIREBASE LOAD: - Objectifs: \(relationshipGoals.count) éléments")
                print("🔥🔥🔥 FIREBASE LOAD: - Durée relation: '\(relationshipDuration)' (vide: \(relationshipDuration.isEmpty))")
                print("🔥🔥🔥 FIREBASE LOAD: - Date naissance: \(birthDate != nil ? "présente" : "manquante")")
                print("🔥🔥🔥 FIREBASE LOAD: - Onboarding en cours: \(onboardingInProgress)")
                print("🔥🔥🔥 FIREBASE LOAD: - Processus onboarding actif: \(self?.isOnboardingInProgress ?? false)")
                
                // Vérifier si les données d'onboarding sont complètes
                let isOnboardingComplete = !name.isEmpty && 
                                         !relationshipGoals.isEmpty && 
                                         !relationshipDuration.isEmpty && 
                                         birthDate != nil &&
                                         !onboardingInProgress  // NOUVEAU: Ne pas marquer comme terminé si onboarding en cours
                
                if !isOnboardingComplete {
                    print("🔥🔥🔥 FIREBASE LOAD: ONBOARDING INCOMPLET")
                    if onboardingInProgress {
                        print("🔥🔥🔥 FIREBASE LOAD: - Raison: Onboarding en cours de progression")
                        
                        // Vérifier si l'utilisateur vient juste de se créer (moins de 5 minutes)
                        if let createdAt = data["createdAt"] as? Timestamp {
                            let timeSinceCreation = Date().timeIntervalSince(createdAt.dateValue())
                            if timeSinceCreation < 300 { // Moins de 5 minutes
                                print("🔥🔥🔥 FIREBASE LOAD: UTILISATEUR RECENT - CONTINUER ONBOARDING SANS REDIRECTION")
                                print("🔥🔥🔥 FIREBASE LOAD: Créé il y a \(timeSinceCreation) secondes")
                                
                                // Créer un utilisateur partiel pour permettre la continuation de l'onboarding
                                let partialUser = User(
                                    id: data["id"] as? String ?? UUID().uuidString,
                                    name: name,
                                    birthDate: birthDate?.dateValue() ?? Date(),
                                    relationshipGoals: relationshipGoals,
                                    relationshipDuration: User.RelationshipDuration(rawValue: relationshipDuration) ?? .notInRelationship,
                                    partnerCode: data["partnerCode"] as? String,
                                    isSubscribed: data["isSubscribed"] as? Bool ?? false,
                                    onboardingInProgress: true
                                )
                                
                                // Marquer comme authentifié avec l'utilisateur partiel
                                self?.isAuthenticated = true
                                self?.currentUser = partialUser
                                print("🔥🔥🔥 FIREBASE LOAD: UTILISATEUR PARTIEL CREE POUR CONTINUER ONBOARDING")
                                return
                            }
                        }
                        
                        print("🔥🔥🔥 FIREBASE LOAD: REDIRECTION VERS ONBOARDING")
                    } else {
                        print("🔥🔥🔥 FIREBASE LOAD: - Raison: Données incomplètes")
                    }
                    
                    // Données incomplètes, utilisateur doit compléter l'onboarding
                    self?.isAuthenticated = true
                    self?.currentUser = nil
                    return
                }
                
                print("🔥🔥🔥 FIREBASE LOAD: DONNEES COMPLETES - CHARGEMENT UTILISATEUR")
                
                // Convertir les données Firestore en User
                let user = User(
                    id: data["id"] as? String ?? UUID().uuidString,
                    name: name,
                    birthDate: birthDate?.dateValue() ?? Date(),
                    relationshipGoals: relationshipGoals,
                    relationshipDuration: User.RelationshipDuration(rawValue: relationshipDuration) ?? .notInRelationship,
                    partnerCode: data["partnerCode"] as? String,
                    isSubscribed: data["isSubscribed"] as? Bool ?? false,
                    onboardingInProgress: onboardingInProgress
                )
                
                print("✅ FirebaseService: Utilisateur chargé avec données complètes: \(user.name)")
                print("🔥🔥🔥 FIREBASE LOAD: - Onboarding en cours: \(user.onboardingInProgress)")
                self?.currentUser = user
                self?.isAuthenticated = true
                print("✅ Données utilisateur chargées depuis Apple ID")
            }
        }
    }
    
    private func createEmptyUserProfile(uid: String, email: String?, name: String?) {
        let userData: [String: Any] = [
            "id": uid,
            "email": email ?? "",
            "name": name ?? "",
            "appleUserID": uid,
            "authProvider": "apple.com",
            "createdAt": Timestamp(date: Date()),
            "onboardingCompleted": false
        ]
        
        db.collection("users").document(uid).setData(userData) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.errorMessage = "Erreur de création de profil: \(error.localizedDescription)"
                } else {
                    self?.isAuthenticated = true
                    self?.currentUser = nil // Pas de données complètes, doit faire l'onboarding
                    print("✅ Profil utilisateur créé avec Apple ID")
                }
            }
        }
    }
    
    // MARK: - Subscription Management
    
    func updateSubscriptionStatus(isSubscribed: Bool) {
        guard let firebaseUser = Auth.auth().currentUser else { return }
        
        // Vérifier que c'est bien Apple ID
        guard firebaseUser.providerData.contains(where: { $0.providerID == "apple.com" }) else {
            self.errorMessage = "Authentification Apple requise"
            return
        }
        
        db.collection("users").document(firebaseUser.uid).updateData([
            "isSubscribed": isSubscribed,
            "subscriptionDate": isSubscribed ? Timestamp(date: Date()) : FieldValue.delete(),
            "updatedAt": Timestamp(date: Date())
        ]) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.errorMessage = "Erreur de mise à jour d'abonnement: \(error.localizedDescription)"
                } else {
                    self?.currentUser?.isSubscribed = isSubscribed
                    print("✅ Statut d'abonnement mis à jour pour Apple ID")
                }
            }
        }
    }
    
    // MARK: - Sign Out
    
    func signOut() {
        do {
            try Auth.auth().signOut()
            DispatchQueue.main.async {
                self.isAuthenticated = false
                self.currentUser = nil
                self.currentNonce = nil
            }
        } catch {
            self.errorMessage = "Erreur de déconnexion: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Nonce for Apple Sign In
    
    private var currentNonce: String?
    
    func generateNonce() -> String {
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remainingLength = 32
        
        while remainingLength > 0 {
            let randoms: [UInt8] = (0..<16).map { _ in
                var random: UInt8 = 0
                let errorCode = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
                if errorCode != errSecSuccess {
                    fatalError("Unable to generate nonce. SecRandomCopyBytes failed with OSStatus \(errorCode)")
                }
                return random
            }
            
            randoms.forEach { random in
                if remainingLength == 0 {
                    return
                }
                
                if random < charset.count {
                    result.append(charset[Int(random)])
                    remainingLength -= 1
                }
            }
        }
        
        currentNonce = result
        return result
    }
} 