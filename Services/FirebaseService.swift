import Foundation
import FirebaseAuth
import FirebaseFirestore
import FirebaseStorage
import FirebaseFunctions
import AuthenticationServices
import Combine
import UIKit
import CoreLocation

class FirebaseService: NSObject, ObservableObject {
    static let shared = FirebaseService()
    
    // Published properties
    @Published var isAuthenticated = false
    @Published var currentUser: AppUser?
    @Published var isLoading = true
    @Published var errorMessage: String?
    
    // Private properties
    private let db = Firestore.firestore()
    private var cancellables = Set<AnyCancellable>()
    
    // NOUVEAU: Flag pour éviter les redirections automatiques pendant l'onboarding
    private var isOnboardingInProgress = false
    
    private var authListener: AuthStateDidChangeListenerHandle?
    private var subscriptionListener: ListenerRegistration?
    
    override init() {
        super.init()
        print("🔥 FirebaseService: Initialisation")
        checkAuthenticationState()
    }
    
    // MARK: - Authentication State
    
    func checkAuthenticationState() {
        print("🔥 FirebaseService: Vérification de l'état d'authentification")
        _ = Auth.auth().addStateDidChangeListener { [weak self] _, firebaseUser in
            let timestamp = Date().timeIntervalSince1970
            DispatchQueue.main.async {
                if let firebaseUser = firebaseUser {
                    print("🔥 FirebaseService: Auth listener triggered - UID: \(firebaseUser.uid) [\(timestamp)]")
                    print("🔥 FirebaseService: Providers: \(firebaseUser.providerData.map { $0.providerID })")
                    
                    // Vérifier que c'est bien une authentification Apple
                            // Vérification des providers d'authentification
                    
                    if firebaseUser.providerData.contains(where: { $0.providerID == "apple.com" }) {
                        print("🔥 FirebaseService: Authentification Apple confirmée")
                        self?.loadUserData(uid: firebaseUser.uid)
                    } else {
                        print("⚠️ FirebaseService: Provider non-Apple détecté, mais maintien de la session")
                        print("⚠️ Fournisseur non-Apple détecté")
                        // Charger les données quand même pour éviter les déconnexions en boucle
                        self?.loadUserData(uid: firebaseUser.uid)
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
        print("📝 Début processus onboarding")
        isOnboardingInProgress = true
    }
    
    // NOUVEAU: Méthode pour marquer la fin de l'onboarding
    func completeOnboardingProcess() {
        print("✅ Fin processus onboarding")
        isOnboardingInProgress = false
    }
    
    // 🔧 NOUVELLE MÉTHODE: Finalisation onboarding avec préservation données partenaire
    func finalizeOnboardingWithPartnerData(
        name: String,
        relationshipGoals: [String],
        relationshipDuration: AppUser.RelationshipDuration,
        relationshipImprovement: String?,
        questionMode: String?,
        isSubscribed: Bool,
        relationshipStartDate: Date?,
        profileImage: UIImage?,
        currentLocation: UserLocation?,
        completion: @escaping (Bool, AppUser?) -> Void
    ) {
        print("🔥 FirebaseService: Finalisation onboarding avec préservation données partenaire")
        
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Aucun utilisateur Firebase connecté")
            completion(false, nil)
            return
        }
        
        let uid = firebaseUser.uid
        print("🔥 FirebaseService: Récupération données existantes")
        
        // Récupérer les données existantes pour préserver les infos partenaire
        db.collection("users").document(uid).getDocument { [weak self] document, error in
            guard let self = self else { return }
            
            if let error = error {
                print("❌ FirebaseService: Erreur récupération données: \(error.localizedDescription)")
                completion(false, nil)
                return
            }
            
            // Données existantes (peuvent être vides pour un nouvel utilisateur)
            let existingData = document?.data() ?? [:]
            print("🔥 FirebaseService: Données existantes récupérées: \(existingData.keys)")
            
            // Traiter l'upload de l'image de profil si présente
            var profileImageURL: String?
            let group = DispatchGroup()
            
            if let profileImage = profileImage {
                group.enter()
                self.uploadProfileImage(profileImage) { imageURL in
                    profileImageURL = imageURL
                    group.leave()
                }
            }
            
            group.notify(queue: .main) {
                // Créer l'utilisateur final en fusionnant les données
                let finalUser = AppUser(
                    id: existingData["id"] as? String ?? UUID().uuidString,
                    name: name,
                    birthDate: (existingData["birthDate"] as? Timestamp)?.dateValue() ?? Date(),
                    relationshipGoals: relationshipGoals,
                    relationshipDuration: relationshipDuration,
                    relationshipImprovement: relationshipImprovement,
                    questionMode: questionMode,
                    // 🔧 PRÉSERVATION: Garder les données de connexion partenaire existantes
                    partnerCode: existingData["partnerCode"] as? String,
                    partnerId: existingData["partnerId"] as? String,
                    partnerConnectedAt: (existingData["partnerConnectedAt"] as? Timestamp)?.dateValue(),
                    subscriptionInheritedFrom: existingData["subscriptionSharedFrom"] as? String,  // 🔧 CORRECTION: Utiliser le bon nom de champ
                    subscriptionInheritedAt: (existingData["subscriptionInheritedAt"] as? Timestamp)?.dateValue(),
                    connectedPartnerCode: existingData["connectedPartnerCode"] as? String,
                    connectedPartnerId: existingData["connectedPartnerId"] as? String,
                    connectedAt: (existingData["connectedAt"] as? Timestamp)?.dateValue(),
                    isSubscribed: isSubscribed,
                    onboardingInProgress: false,
                    relationshipStartDate: relationshipStartDate ?? (existingData["relationshipStartDate"] as? Timestamp)?.dateValue(),
                    profileImageURL: profileImageURL ?? existingData["profileImageURL"] as? String,
                    currentLocation: currentLocation ?? self.parseUserLocation(from: existingData["currentLocation"] as? [String: Any])
                )
                
                        print("✅ Utilisateur finalisé")
                
                // Sauvegarder l'utilisateur final
                self.saveUserData(finalUser)
                
                // Retourner l'utilisateur créé
                completion(true, finalUser)
            }
        }
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
        
                                let credential = OAuthProvider.credential(providerID: AuthProviderID.apple,
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
    
    func savePartialUserData(_ user: AppUser) {
        print("💾 Sauvegarde données partielles")
        
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
        
        var userData: [String: Any] = [
            "id": user.id,
            "name": user.name,
            "birthDate": Timestamp(date: user.birthDate),
            "relationshipGoals": user.relationshipGoals,
            "relationshipDuration": user.relationshipDuration.rawValue,
            "partnerCode": user.partnerCode ?? "",
            "partnerId": user.partnerId ?? "",
            "partnerConnectedAt": user.partnerConnectedAt != nil ? Timestamp(date: user.partnerConnectedAt!) : NSNull(),
            "subscriptionInheritedFrom": user.subscriptionInheritedFrom ?? "",
            "subscriptionInheritedAt": user.subscriptionInheritedAt != nil ? Timestamp(date: user.subscriptionInheritedAt!) : NSNull(),
            "connectedPartnerCode": user.connectedPartnerCode ?? "",
            "connectedPartnerId": user.connectedPartnerId ?? "",
            "connectedAt": user.connectedAt != nil ? Timestamp(date: user.connectedAt!) : NSNull(),
            "isSubscribed": user.isSubscribed,
            "appleUserID": firebaseUser.uid,
            "lastLoginDate": Timestamp(date: Date()),
            "createdAt": Timestamp(date: Date()),
            "updatedAt": Timestamp(date: Date()),
            "onboardingInProgress": true,  // IMPORTANT: Marquer l'onboarding comme en cours
            "relationshipImprovement": user.relationshipImprovement ?? "",
            "questionMode": user.questionMode ?? "",
            "languageCode": Locale.current.language.languageCode?.identifier ?? "fr",
            // NOUVEAU: Tracking freemium questions du jour
            "dailyQuestionFirstAccessDate": user.dailyQuestionFirstAccessDate != nil ? Timestamp(date: user.dailyQuestionFirstAccessDate!) : NSNull(),
            "dailyQuestionMaxDayReached": user.dailyQuestionMaxDayReached,
            // NOUVEAU: Tracking freemium défis du jour
            "dailyChallengeFirstAccessDate": user.dailyChallengeFirstAccessDate != nil ? Timestamp(date: user.dailyChallengeFirstAccessDate!) : NSNull(),
            "dailyChallengeMaxDayReached": user.dailyChallengeMaxDayReached
        ]
        
        // Ajouter la date de début de relation si présente
        if let relationshipStartDate = user.relationshipStartDate {
            userData["relationshipStartDate"] = Timestamp(date: relationshipStartDate)
        }
        
            // Ajouter l'URL de photo de profil et sa version si présentes
            if let profileImageURL = user.profileImageURL {
                userData["profileImageURL"] = profileImageURL
            }
            if let profileImageUpdatedAt = user.profileImageUpdatedAt {
                userData["profileImageUpdatedAt"] = Timestamp(date: profileImageUpdatedAt)
            }
        
        // Ajouter la localisation actuelle si présente
        if let currentLocation = user.currentLocation {
            userData["currentLocation"] = [
                "latitude": currentLocation.latitude,
                "longitude": currentLocation.longitude,
                "address": currentLocation.address as Any,
                "city": currentLocation.city as Any,
                "country": currentLocation.country as Any,
                "lastUpdated": Timestamp(date: currentLocation.lastUpdated)
            ]
        }
        
                    // Données partielles - onboarding en cours
        
        db.collection("users").document(firebaseUser.uid).setData(userData, merge: true) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ FirebaseService: Erreur de sauvegarde partielle: \(error.localizedDescription)")
                    self?.errorMessage = "Erreur de sauvegarde: \(error.localizedDescription)"
                } else {
                    print("✅ FirebaseService: Données partielles sauvegardées avec succès")
                    print("✅ Données partielles sauvegardées")
                    // Ne pas mettre à jour currentUser ni isAuthenticated ici
                    // pour éviter de déclencher la redirection
                }
            }
        }
    }
    
    func saveUserData(_ user: AppUser) {
        print("🔥 FirebaseService: Tentative de sauvegarde des données utilisateur")
        print("🔥 FirebaseService: Création utilisateur")
        
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Aucun utilisateur Firebase connecté")
            self.errorMessage = "Utilisateur non connecté"
            return
        }
        
        print("🔥 FirebaseService: Utilisateur Firebase connecté")
        
        // Vérifier que c'est bien Apple ID
        guard firebaseUser.providerData.contains(where: { $0.providerID == "apple.com" }) else {
            print("❌ FirebaseService: Authentification Apple requise")
            self.errorMessage = "Authentification Apple requise"
            return
        }
        
        print("💾 Sauvegarde utilisateur")
        isLoading = true
        
        var userData: [String: Any] = [
            "id": user.id,
            "name": user.name,
            "birthDate": Timestamp(date: user.birthDate),
            "relationshipGoals": user.relationshipGoals,
            "relationshipDuration": user.relationshipDuration.rawValue,
            "partnerCode": user.partnerCode ?? "",
            "partnerId": user.partnerId ?? "",
            "partnerConnectedAt": user.partnerConnectedAt != nil ? Timestamp(date: user.partnerConnectedAt!) : NSNull(),
            "subscriptionInheritedFrom": user.subscriptionInheritedFrom ?? "",
            "subscriptionInheritedAt": user.subscriptionInheritedAt != nil ? Timestamp(date: user.subscriptionInheritedAt!) : NSNull(),
            "connectedPartnerCode": user.connectedPartnerCode ?? "",
            "connectedPartnerId": user.connectedPartnerId ?? "",
            "connectedAt": user.connectedAt != nil ? Timestamp(date: user.connectedAt!) : NSNull(),
            "isSubscribed": user.isSubscribed,
            "appleUserID": firebaseUser.uid,
            "lastLoginDate": Timestamp(date: Date()),
            "createdAt": Timestamp(date: Date()),
            "updatedAt": Timestamp(date: Date()),
            "onboardingInProgress": false,  // NOUVEAU: Marquer l'onboarding comme terminé
            "relationshipImprovement": user.relationshipImprovement ?? "",
            "questionMode": user.questionMode ?? "",
            // NOUVEAU: Tracking freemium questions du jour
            "dailyQuestionFirstAccessDate": user.dailyQuestionFirstAccessDate != nil ? Timestamp(date: user.dailyQuestionFirstAccessDate!) : NSNull(),
            "dailyQuestionMaxDayReached": user.dailyQuestionMaxDayReached,
            // NOUVEAU: Tracking freemium défis du jour
            "dailyChallengeFirstAccessDate": user.dailyChallengeFirstAccessDate != nil ? Timestamp(date: user.dailyChallengeFirstAccessDate!) : NSNull(),
            "dailyChallengeMaxDayReached": user.dailyChallengeMaxDayReached
        ]
        
        // Ajouter la date de début de relation si présente
        if let relationshipStartDate = user.relationshipStartDate {
            userData["relationshipStartDate"] = Timestamp(date: relationshipStartDate)
        }
        
        // Ajouter l'URL de photo de profil et sa version si présentes
        if let profileImageURL = user.profileImageURL {
            userData["profileImageURL"] = profileImageURL
        }
        if let profileImageUpdatedAt = user.profileImageUpdatedAt {
            userData["profileImageUpdatedAt"] = Timestamp(date: profileImageUpdatedAt)
        }
        
        // Ajouter la localisation actuelle si présente
        if let currentLocation = user.currentLocation {
            userData["currentLocation"] = [
                "latitude": currentLocation.latitude,
                "longitude": currentLocation.longitude,
                "address": currentLocation.address as Any,
                "city": currentLocation.city as Any,
                "country": currentLocation.country as Any,
                "lastUpdated": Timestamp(date: currentLocation.lastUpdated)
            ]
        }
        
        // NOUVEAU: Ajouter la langue de l'utilisateur pour localisation des notifications
        let deviceLanguage = Locale.current.language.languageCode?.identifier ?? "fr"
        userData["languageCode"] = user.languageCode ?? deviceLanguage
        
                    // Finalisation des données utilisateur
        
        db.collection("users").document(firebaseUser.uid).setData(userData, merge: true) { [weak self] error in
            DispatchQueue.main.async {
                self?.isLoading = false
                
                if let error = error {
                    print("❌ FirebaseService: Erreur de sauvegarde Firestore: \(error.localizedDescription)")
                    self?.errorMessage = "Erreur de sauvegarde: \(error.localizedDescription)"
                } else {
                    // 💾 NOUVEAU: Mettre à jour le cache après sauvegarde réussie
                    UserCacheManager.shared.cacheUser(user)
                    print("✅ FirebaseService: Données utilisateur sauvegardées avec succès")
                    print("✅ Données utilisateur sauvegardées")
                    self?.currentUser = user
                    self?.isAuthenticated = true
                    print("✅ Données utilisateur sauvegardées avec Apple ID")
                }
            }
        }
    }
    
    func loadUserData(uid: String) {
        print("🔥 FirebaseService: Chargement des données utilisateur")
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
                    
                    // NOUVEAU: Vérifier si l'onboarding a été terminé avec succès
                    let onboardingInProgress = data["onboardingInProgress"] as? Bool ?? false
                    let hasValidData = !(data["name"] as? String ?? "").isEmpty && 
                                      !(data["relationshipGoals"] as? [String] ?? []).isEmpty
                    
                    // Si l'utilisateur a été créé récemment (moins de 5 minutes) et qu'il se reconnecte
                    // MAIS seulement si l'onboarding n'a PAS été terminé avec succès
                    if timeSinceCreation < 300 && timeSinceLastLogin > 60 && 
                       (onboardingInProgress || !hasValidData) {
                        print("🔥 FirebaseService: DÉTECTION - Possible reconnexion après suppression ratée")
                        print("🔥 FirebaseService: - Créé il y a: \(timeSinceCreation) secondes")
                        print("🔥 FirebaseService: - Dernière connexion il y a: \(timeSinceLastLogin) secondes")
                        print("🔥 FirebaseService: - Onboarding en cours: \(onboardingInProgress)")
                        print("🔥 FirebaseService: - Données valides: \(hasValidData)")
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
                    } else if timeSinceCreation < 300 && timeSinceLastLogin > 60 {
                        print("🔥 FirebaseService: Utilisateur récent mais onboarding terminé - Conservation des données")
                        print("🔥 FirebaseService: - Onboarding en cours: \(onboardingInProgress)")
                        print("🔥 FirebaseService: - Données valides: \(hasValidData)")
                    }
                }
                
                // Vérifier que les données sont complètes pour un onboarding terminé
                let name = data["name"] as? String ?? ""
                let relationshipGoals = data["relationshipGoals"] as? [String] ?? []
                let relationshipDuration = data["relationshipDuration"] as? String ?? ""
                let birthDate = data["birthDate"] as? Timestamp
                
                // NOUVEAU: Vérifier si l'utilisateur est en cours d'onboarding
                let onboardingInProgress = data["onboardingInProgress"] as? Bool ?? false
                
                        // Vérification des données utilisateur
                
                // Vérifier si les données d'onboarding sont complètes
                let isOnboardingComplete = !name.isEmpty && 
                                         !relationshipGoals.isEmpty && 
                                         !relationshipDuration.isEmpty && 
                                         birthDate != nil &&
                                         !onboardingInProgress  // NOUVEAU: Ne pas marquer comme terminé si onboarding en cours
                
                if !isOnboardingComplete {
                    print("🔥 Firebase: Onboarding incomplet")
                    if onboardingInProgress {
                        
                        // MODIFICATION: Vérifier si l'onboarding est déjà actif dans l'app
                        if self?.isOnboardingInProgress == true {
                            print("🔥 Firebase: Onboarding déjà actif - ignorer redirection")
                            return
                        }
                        
                        // Vérifier si l'utilisateur vient juste de se créer (moins de 5 minutes)
                        if let createdAt = data["createdAt"] as? Timestamp {
                            let timeSinceCreation = Date().timeIntervalSince(createdAt.dateValue())
                            if timeSinceCreation < 300 { // Moins de 5 minutes
                                print("🔥 Firebase: Utilisateur récent - continuer onboarding")
                                
                                // MODIFICATION: Ne créer un utilisateur partiel QUE si ce n'est pas déjà en cours
                                print("🔥 Firebase: VERIFICATION - Processus onboarding actif: \(self?.isOnboardingInProgress ?? false)")
                                
                                if self?.isOnboardingInProgress != true {
                                    // Créer un utilisateur partiel pour permettre la continuation de l'onboarding
                                    let partialUser = AppUser(
                                        id: data["id"] as? String ?? UUID().uuidString,
                                        name: name,
                                        birthDate: birthDate?.dateValue() ?? Date(),
                                        relationshipGoals: relationshipGoals,
                                        relationshipDuration: AppUser.RelationshipDuration(rawValue: relationshipDuration) ?? .notInRelationship,
                                        partnerCode: data["partnerCode"] as? String,
                                        partnerId: data["partnerId"] as? String,
                                        partnerConnectedAt: (data["partnerConnectedAt"] as? Timestamp)?.dateValue(),
                                        subscriptionInheritedFrom: data["subscriptionSharedFrom"] as? String,  // 🔧 CORRECTION: Utiliser le bon nom de champ
                                        subscriptionInheritedAt: (data["subscriptionInheritedAt"] as? Timestamp)?.dateValue(),
                                        isSubscribed: data["isSubscribed"] as? Bool ?? false,
                                        onboardingInProgress: true,
                                        dailyQuestionFirstAccessDate: (data["dailyQuestionFirstAccessDate"] as? Timestamp)?.dateValue(),
                                        dailyQuestionMaxDayReached: data["dailyQuestionMaxDayReached"] as? Int ?? 0,
                                        dailyChallengeFirstAccessDate: (data["dailyChallengeFirstAccessDate"] as? Timestamp)?.dateValue(),
                                        dailyChallengeMaxDayReached: data["dailyChallengeMaxDayReached"] as? Int ?? 0
                                    )
                                    
                                    // Marquer comme authentifié avec l'utilisateur partiel
                                    self?.isAuthenticated = true
                                    self?.currentUser = partialUser
                                    print("🔥 Firebase: UTILISATEUR PARTIEL CREE POUR CONTINUER ONBOARDING")
                                } else {
                                    print("🔥 Firebase: ONBOARDING DEJA EN COURS - SKIP CREATION USER PARTIEL")
                                }
                                return
                            }
                        }
                        
                        print("🔥 Firebase: REDIRECTION VERS ONBOARDING")
                    } else {
                        print("🔥 Firebase: - Raison: Données incomplètes")
                    }
                    
                    // Données incomplètes, utilisateur doit compléter l'onboarding
                    self?.isAuthenticated = true
                    self?.currentUser = nil
                    return
                }
                
                print("🔥 Firebase: DONNEES COMPLETES - CHARGEMENT UTILISATEUR")
                
                // Convertir les données Firestore en User
                let user = AppUser(
                    id: data["id"] as? String ?? UUID().uuidString,
                    name: name,
                    birthDate: birthDate?.dateValue() ?? Date(),
                    relationshipGoals: relationshipGoals,
                    relationshipDuration: AppUser.RelationshipDuration(rawValue: relationshipDuration) ?? .notInRelationship,
                    relationshipImprovement: data["relationshipImprovement"] as? String,
                    questionMode: data["questionMode"] as? String,
                    partnerCode: data["partnerCode"] as? String,
                    partnerId: data["partnerId"] as? String,
                    partnerConnectedAt: (data["partnerConnectedAt"] as? Timestamp)?.dateValue(),
                    subscriptionInheritedFrom: data["subscriptionSharedFrom"] as? String,  // 🔧 CORRECTION: Utiliser le bon nom de champ
                    subscriptionInheritedAt: (data["subscriptionInheritedAt"] as? Timestamp)?.dateValue(),
                    connectedPartnerCode: data["connectedPartnerCode"] as? String,
                    connectedPartnerId: data["connectedPartnerId"] as? String,
                    connectedAt: (data["connectedAt"] as? Timestamp)?.dateValue(),
                    isSubscribed: data["isSubscribed"] as? Bool ?? false,
                    onboardingInProgress: false,
                    relationshipStartDate: (data["relationshipStartDate"] as? Timestamp)?.dateValue(),
                    profileImageURL: data["profileImageURL"] as? String,
                    profileImageUpdatedAt: (data["profileImageUpdatedAt"] as? Timestamp)?.dateValue(),
                    currentLocation: self?.parseUserLocation(from: data["currentLocation"] as? [String: Any]),
                    languageCode: data["languageCode"] as? String,
                    // NOUVEAU: Tracking freemium questions du jour
                    dailyQuestionFirstAccessDate: (data["dailyQuestionFirstAccessDate"] as? Timestamp)?.dateValue(),
                    dailyQuestionMaxDayReached: data["dailyQuestionMaxDayReached"] as? Int ?? 0,
                    // NOUVEAU: Tracking freemium défis du jour
                    dailyChallengeFirstAccessDate: (data["dailyChallengeFirstAccessDate"] as? Timestamp)?.dateValue(),
                    dailyChallengeMaxDayReached: data["dailyChallengeMaxDayReached"] as? Int ?? 0
                )
                
                print("✅ FirebaseService: Utilisateur chargé avec données complètes: \(user.name)")
                print("🔥 Firebase: - Onboarding en cours: \(user.onboardingInProgress)")
                
                // 💾 NOUVEAU: Mettre à jour le cache utilisateur
                UserCacheManager.shared.cacheUser(user)
                
                self?.currentUser = user
                let authTimestamp = Date().timeIntervalSince1970
                print("🔥 FirebaseService: loadUserData completed, setting isAuthenticated = true [\(authTimestamp)]")
                self?.isAuthenticated = true
                print("✅ Données utilisateur chargées depuis Apple ID")
                
                // Plus besoin de tracker la connexion partenaire pour les reviews
                
                // NOUVEAU: Démarrer l'écoute des changements d'abonnement
                self?.startListeningForSubscriptionChanges()
                
                print("🔥 Firebase: UTILISATEUR CHARGE ET AUTHENTIFIE")
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
        print("🔥 Firebase signout: signOut() appelé")
        print("🔥 Firebase signout: Thread: \(Thread.current)")
        print("🔥 Firebase signout: Stack trace: \(Thread.callStackSymbols.prefix(5))")
        
        do {
            try Auth.auth().signOut()
            print("🔥 FirebaseService: Déconnexion réussie")
            
            // Réinitialiser l'état
            DispatchQueue.main.async {
                self.isAuthenticated = false
                self.currentUser = nil
                self.errorMessage = nil
            }
            
            // Arrêter l'écoute des changements d'abonnement
            stopListeningForSubscriptionChanges()
            
        } catch {
            print("❌ FirebaseService: Erreur déconnexion: \(error)")
            DispatchQueue.main.async {
                self.errorMessage = "Erreur lors de la déconnexion"
            }
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
    
    // MARK: - Observer les changements d'abonnement en temps réel
    
    func startListeningForSubscriptionChanges() {
        guard let user = Auth.auth().currentUser else { return }
        
        subscriptionListener = db.collection("users").document(user.uid)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    print("❌ FirebaseService: Erreur listener abonnement: \(error)")
                    return
                }
                
                guard let data = snapshot?.data() else { return }
                
                let isSubscribed = data["isSubscribed"] as? Bool ?? false
                let subscriptionType = data["subscriptionType"] as? String
                
                // Mettre à jour l'état local si l'abonnement a changé
                if let currentUser = self?.currentUser, currentUser.isSubscribed != isSubscribed {
                    var updatedUser = currentUser
                    updatedUser.isSubscribed = isSubscribed
                    
                    // Mettre à jour les champs d'héritage si nécessaire
                    if subscriptionType == "shared_from_partner" {  // 🔧 CORRECTION: Utiliser le bon type d'abonnement
                        updatedUser.subscriptionInheritedFrom = data["subscriptionSharedFrom"] as? String  // 🔧 CORRECTION: Utiliser le bon nom de champ
                        updatedUser.subscriptionInheritedAt = (data["subscriptionSharedAt"] as? Timestamp)?.dateValue()  // 🔧 CORRECTION: Utiliser le bon nom de champ
                    }
                    
                    DispatchQueue.main.async {
                        self?.currentUser = updatedUser
                        print("🔥 FirebaseService: Abonnement mis à jour localement: \(isSubscribed)")
                        
                        // Notifier le changement d'abonnement
                        NotificationCenter.default.post(name: .subscriptionUpdated, object: nil)
                    }
                }
            }
    }
    
    func stopListeningForSubscriptionChanges() {
        subscriptionListener?.remove()
        subscriptionListener = nil
    }
    
    // MARK: - Widget Support Methods
    
    func getUserData(userId: String, completion: @escaping (AppUser?) -> Void) {
        print("🔥 FirebaseService: Récupération données utilisateur: \(userId)")
        print("🔥 Firestore: Tentative d'accès aux données de: \(userId)")
        
        // Vérifier si c'est l'utilisateur actuel (accès direct autorisé)
        if let currentUser = Auth.auth().currentUser, currentUser.uid == userId {
            print("🔥 Firestore: Accès direct autorisé (utilisateur actuel)")
            getUserDataDirect(userId: userId, completion: completion)
            return
        }
        
        // Pour les partenaires, utiliser la Cloud Function sécurisée
        print("🔥 Firestore: Accès partenaire - Utilisation Cloud Function")
        getPartnerInfoViaCloudFunction(partnerId: userId, completion: completion)
    }
    
    private func getUserDataDirect(userId: String, completion: @escaping (AppUser?) -> Void) {
        print("🔥 Accès direct: Accès direct aux données de: \(userId)")
        
        db.collection("users").document(userId).getDocument { document, error in
            if let error = error {
                print("❌ FirebaseService: Erreur récupération utilisateur (direct): \(error.localizedDescription)")
                completion(nil)
                return
            }
            
            guard let document = document, document.exists,
                  let data = document.data() else {
                print("❌ FirebaseService: Utilisateur non trouvé (direct): \(userId)")
                completion(nil)
                return
            }
            
            // Convertir les données en AppUser
            let user = AppUser(
                id: data["id"] as? String ?? userId,
                name: data["name"] as? String ?? "",
                birthDate: (data["birthDate"] as? Timestamp)?.dateValue() ?? Date(),
                relationshipGoals: data["relationshipGoals"] as? [String] ?? [],
                relationshipDuration: AppUser.RelationshipDuration(rawValue: data["relationshipDuration"] as? String ?? "") ?? .notInRelationship,
                relationshipImprovement: data["relationshipImprovement"] as? String,
                questionMode: data["questionMode"] as? String,
                partnerCode: data["partnerCode"] as? String,
                partnerId: data["partnerId"] as? String,
                partnerConnectedAt: (data["partnerConnectedAt"] as? Timestamp)?.dateValue(),
                subscriptionInheritedFrom: data["subscriptionSharedFrom"] as? String,
                subscriptionInheritedAt: (data["subscriptionInheritedAt"] as? Timestamp)?.dateValue(),
                connectedPartnerCode: data["connectedPartnerCode"] as? String,
                connectedPartnerId: data["connectedPartnerId"] as? String,
                connectedAt: (data["connectedAt"] as? Timestamp)?.dateValue(),
                isSubscribed: data["isSubscribed"] as? Bool ?? false,
                onboardingInProgress: data["onboardingInProgress"] as? Bool ?? false,
                relationshipStartDate: (data["relationshipStartDate"] as? Timestamp)?.dateValue(),
                profileImageURL: data["profileImageURL"] as? String,
                profileImageUpdatedAt: (data["profileImageUpdatedAt"] as? Timestamp)?.dateValue(),
                currentLocation: self.parseUserLocation(from: data["currentLocation"] as? [String: Any]),
                languageCode: data["languageCode"] as? String,
                // NOUVEAU: Tracking freemium questions du jour
                dailyQuestionFirstAccessDate: (data["dailyQuestionFirstAccessDate"] as? Timestamp)?.dateValue(),
                dailyQuestionMaxDayReached: data["dailyQuestionMaxDayReached"] as? Int ?? 0,
                // NOUVEAU: Tracking freemium défis du jour
                dailyChallengeFirstAccessDate: (data["dailyChallengeFirstAccessDate"] as? Timestamp)?.dateValue(),
                dailyChallengeMaxDayReached: data["dailyChallengeMaxDayReached"] as? Int ?? 0
            )
            
            print("✅ FirebaseService: Utilisateur récupéré (direct): \(user.name)")
            completion(user)
        }
    }
    
    private func getPartnerInfoViaCloudFunction(partnerId: String, completion: @escaping (AppUser?) -> Void) {
        print("🔥 Cloud Function: Récupération données partenaire via fonction sécurisée")
        print("🔥 Cloud Function: partnerId: \(partnerId)")
        
        let functions = Functions.functions()
        
        functions.httpsCallable("getPartnerInfo").call(["partnerId": partnerId]) { result, error in
            if let error = error {
                print("❌ FirebaseService: Erreur Cloud Function getPartnerInfo: \(error.localizedDescription)")
                completion(nil)
                return
            }
            
            guard let data = result?.data as? [String: Any],
                  let success = data["success"] as? Bool,
                  success,
                  let partnerInfo = data["partnerInfo"] as? [String: Any] else {
                print("❌ FirebaseService: Format de réponse invalide")
                completion(nil)
                return
            }
            
            // Créer un AppUser minimal avec les données du partenaire
            let partnerUser = AppUser(
                id: partnerId,
                name: partnerInfo["name"] as? String ?? "Partenaire",
                birthDate: Date(), // Date par défaut
                relationshipGoals: [],
                relationshipDuration: .notInRelationship,
                relationshipImprovement: nil,
                questionMode: nil,
                partnerCode: nil,
                partnerId: nil,
                partnerConnectedAt: nil,
                subscriptionInheritedFrom: partnerInfo["subscriptionSharedFrom"] as? String,
                subscriptionInheritedAt: nil,
                connectedPartnerCode: nil,
                connectedPartnerId: nil,
                connectedAt: nil,
                isSubscribed: partnerInfo["isSubscribed"] as? Bool ?? false,
                onboardingInProgress: false,
                relationshipStartDate: nil,
                profileImageURL: partnerInfo["profileImageURL"] as? String, // CORRECTION: Récupérer l'URL photo
                currentLocation: nil
            )
            
            print("✅ FirebaseService: Données partenaire récupérées via Cloud Function: \(partnerUser.name)")
            if let profileURL = partnerUser.profileImageURL {
                print("✅ FirebaseService: Photo de profil partenaire trouvée: \(profileURL)")
            } else {
                print("❌ FirebaseService: Aucune photo de profil pour le partenaire")
            }
            completion(partnerUser)
        }
    }
    
    func updateUserLocation(_ location: UserLocation, completion: @escaping (Bool) -> Void) {
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Aucun utilisateur connecté pour mise à jour localisation")
            completion(false)
            return
        }
        
        print("🔥 FirebaseService: Mise à jour localisation utilisateur")
        
        let locationData: [String: Any] = [
            "latitude": location.latitude,
            "longitude": location.longitude,
            "address": location.address as Any,
            "city": location.city as Any,
            "country": location.country as Any,
            "lastUpdated": Timestamp(date: location.lastUpdated)
        ]
        
        db.collection("users").document(firebaseUser.uid).updateData([
            "currentLocation": locationData,
            "updatedAt": Timestamp(date: Date())
        ]) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ FirebaseService: Erreur mise à jour localisation: \(error.localizedDescription)")
                    completion(false)
                } else {
                    print("✅ FirebaseService: Localisation mise à jour avec succès")
                    // Mettre à jour l'utilisateur local
                    if var currentUser = self?.currentUser {
                        currentUser.currentLocation = location
                        self?.currentUser = currentUser
                        
                        // 🚀 NOUVEAU: Mettre à jour le cache immédiatement
                        UserCacheManager.shared.cacheUser(currentUser)
                        print("💾 FirebaseService: Cache utilisateur mis à jour avec nouvelle localisation")
                    }
                    completion(true)
                }
            }
        }
    }
    
    private func parseUserLocation(from data: [String: Any]?) -> UserLocation? {
        print("🌍 FirebaseService: parseUserLocation - Analyse données localisation")
        print("🌍 FirebaseService: Données reçues: \(data ?? [:])")
        
        guard let data = data else {
            print("❌ FirebaseService: Aucune donnée de localisation fournie")
            return nil
        }
        
        guard let latitude = data["latitude"] as? Double,
              let longitude = data["longitude"] as? Double else {
            print("❌ FirebaseService: Latitude ou longitude manquante/invalide")
            print("❌ FirebaseService: - Latitude: \(data["latitude"] ?? "nil")")
            print("❌ FirebaseService: - Longitude: \(data["longitude"] ?? "nil")")
            return nil
        }
        
        let address = data["address"] as? String
        let city = data["city"] as? String
        let country = data["country"] as? String
        
        let location = UserLocation(
            coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            address: address,
            city: city,
            country: country
        )
        
        print("✅ FirebaseService: Localisation analysée avec succès")
        print("✅ FirebaseService: - Position: \(latitude), \(longitude)")
        print("✅ FirebaseService: - Ville: \(city ?? "non spécifiée")")
        print("✅ FirebaseService: - Pays: \(country ?? "non spécifié")")
        
        return location
    }
    
    // MARK: - User Profile Updates
    
    func updateUserName(_ newName: String, completion: @escaping (Bool) -> Void) {
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Aucun utilisateur connecté pour mise à jour nom")
            completion(false)
            return
        }
        
        print("🔥 FirebaseService: Mise à jour nom utilisateur")
        
        db.collection("users").document(firebaseUser.uid).updateData([
            "name": newName,
            "updatedAt": Timestamp(date: Date())
        ]) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ FirebaseService: Erreur mise à jour nom: \(error.localizedDescription)")
                    completion(false)
                } else {
                    print("✅ FirebaseService: Nom mis à jour avec succès")
                    // Mettre à jour l'utilisateur local
                    if var currentUser = self?.currentUser {
                        currentUser.name = newName
                        self?.currentUser = currentUser
                        
                        // 🚀 NOUVEAU: Mettre à jour le cache immédiatement
                        UserCacheManager.shared.cacheUser(currentUser)
                        print("💾 FirebaseService: Cache utilisateur mis à jour avec nouveau nom")
                    }
                    completion(true)
                }
            }
        }
    }
    
    func updateRelationshipStartDate(_ date: Date, completion: @escaping (Bool) -> Void) {
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Aucun utilisateur connecté pour mise à jour date relation")
            completion(false)
            return
        }
        
        print("🔥 FirebaseService: Mise à jour date début relation: \(date)")
        
        db.collection("users").document(firebaseUser.uid).updateData([
            "relationshipStartDate": Timestamp(date: date),
            "updatedAt": Timestamp(date: Date())
        ]) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    print("❌ FirebaseService: Erreur mise à jour date relation: \(error.localizedDescription)")
                    completion(false)
                } else {
                    print("✅ FirebaseService: Date relation mise à jour avec succès")
                    // Mettre à jour l'utilisateur local
                    if var currentUser = self?.currentUser {
                        currentUser.relationshipStartDate = date
                        self?.currentUser = currentUser
                        
                        // 🚀 NOUVEAU: Mettre à jour le cache immédiatement
                        UserCacheManager.shared.cacheUser(currentUser)
                        print("💾 FirebaseService: Cache utilisateur mis à jour avec nouvelle date relation")
                    }
                    completion(true)
                }
            }
        }
    }
    
    // MARK: - Profile Image Upload
    
    func updateProfileImage(_ image: UIImage, completion: @escaping (Bool, String?) -> Void) {
        print("🔥 FirebaseService: updateProfileImage - Méthode publique")
        
        guard let currentUser = currentUser else {
            print("❌ FirebaseService: Aucun utilisateur actuel pour mise à jour image")
            completion(false, nil)
            return
        }
        
        uploadProfileImage(image) { [weak self] imageURL in
            guard let self = self else { return }
            
            if let imageURL = imageURL {
                print("✅ FirebaseService: Image uploadée avec succès, mise à jour utilisateur...")
                
                // 🚀 NOUVEAU: Mettre en cache l'image immédiatement pour affichage instantané
                UserCacheManager.shared.cacheProfileImage(image)
                print("🖼️ FirebaseService: Image mise en cache pour affichage instantané")
                
                // 🔄 NOUVEAU: Invalider le cache ImageCacheService pour l'ancienne URL
                // pour forcer l'utilisation de notre cache UserCacheManager
                if let oldURL = currentUser.profileImageURL {
                    ImageCacheService.shared.clearCachedImage(for: oldURL)
                    print("🗑️ FirebaseService: Cache ancien URL invalidé: \(oldURL)")
                }
                
                // Mettre à jour l'utilisateur avec la nouvelle URL d'image (avec cache-buster) et timestamp
                var updatedUser = currentUser
                updatedUser = AppUser(
                    id: updatedUser.id,
                    name: updatedUser.name,
                    birthDate: updatedUser.birthDate,
                    relationshipGoals: updatedUser.relationshipGoals,
                    relationshipDuration: updatedUser.relationshipDuration,
                    relationshipImprovement: updatedUser.relationshipImprovement,
                    questionMode: updatedUser.questionMode,
                    partnerCode: updatedUser.partnerCode,
                    partnerId: updatedUser.partnerId,
                    partnerConnectedAt: updatedUser.partnerConnectedAt,
                    subscriptionInheritedFrom: updatedUser.subscriptionInheritedFrom,
                    subscriptionInheritedAt: updatedUser.subscriptionInheritedAt,
                    connectedPartnerCode: updatedUser.connectedPartnerCode,
                    connectedPartnerId: updatedUser.connectedPartnerId,
                    connectedAt: updatedUser.connectedAt,
                    isSubscribed: updatedUser.isSubscribed,
                    onboardingInProgress: updatedUser.onboardingInProgress,
                    relationshipStartDate: updatedUser.relationshipStartDate,
                    profileImageURL: imageURL,
                    profileImageUpdatedAt: Date(),
                    currentLocation: updatedUser.currentLocation
                )
                
                // 🔄 NOUVEAU: Mettre l'image dans le cache ImageCacheService avec la nouvelle URL
                // pour que AsyncImageView la trouve immédiatement
                ImageCacheService.shared.cacheImage(image, for: imageURL)
                print("🖼️ FirebaseService: Image mise en cache ImageCacheService avec nouvelle URL")

                // ✅ NOUVEAU: Propager immédiatement la nouvelle URL dans l'UI
                // pour éviter que d'autres vues n'utilisent l'ancienne URL
                self.currentUser = updatedUser
                UserCacheManager.shared.cacheUser(updatedUser)

                // Sauvegarder l'utilisateur mis à jour (Firestore)
                self.saveUserData(updatedUser)
                completion(true, imageURL)
            } else {
                print("❌ FirebaseService: Échec upload image")
                completion(false, nil)
            }
        }
    }
    
    private func uploadProfileImage(_ image: UIImage, completion: @escaping (String?) -> Void) {
        print("🔥 FirebaseService: uploadProfileImage - Début")
        
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Aucun utilisateur connecté pour upload image")
            completion(nil)
            return
        }
        
        // 🔄 NOUVEAU: Demander du temps d'exécution en arrière-plan pour upload
        var backgroundTask: UIBackgroundTaskIdentifier = .invalid
        
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "ProfileImageUpload") {
            // Cette closure est appelée si le temps d'arrière-plan expire
            print("⏰ FirebaseService: Temps d'arrière-plan expiré pour upload")
            UIApplication.shared.endBackgroundTask(backgroundTask)
            backgroundTask = .invalid
        }
        
        // Fonction helper pour nettoyer la background task
        let endBackgroundTask = {
            if backgroundTask != .invalid {
                print("✅ FirebaseService: Fin de la tâche d'arrière-plan")
                UIApplication.shared.endBackgroundTask(backgroundTask)
                backgroundTask = .invalid
            }
        }
        
        print("🔥 FirebaseService: Utilisateur authentifié: \(firebaseUser.uid)")
        print("🔥 FirebaseService: Providers: \(firebaseUser.providerData.map { $0.providerID })")
        
        // Redimensionner l'image
        guard let resizedImage = resizeImage(image, to: CGSize(width: 300, height: 300)),
              let imageData = resizedImage.jpegData(compressionQuality: 0.8) else {
            print("❌ FirebaseService: Erreur traitement image")
            endBackgroundTask()
            completion(nil)
            return
        }
        
        print("🔥 FirebaseService: Image redimensionnée - Taille finale: \(imageData.count) bytes")
        
        let storage = Storage.storage()
        let storageRef = storage.reference()
        let profileImagePath = "profile_images/\(firebaseUser.uid)/profile.jpg"
        let profileImageRef = storageRef.child(profileImagePath)
        
        print("🔥 FirebaseService: Chemin upload: \(profileImagePath)")
        print("🔥 FirebaseService: Référence Storage: \(profileImageRef.fullPath)")
        print("🔥 FirebaseService: Début upload...")
        
        // Créer des métadonnées explicites
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        metadata.customMetadata = ["uploadedBy": firebaseUser.uid]
        
        profileImageRef.putData(imageData, metadata: metadata) { uploadMetadata, error in
            print("🔥 FirebaseService: Callback upload reçu")
            
            if let error = error {
                print("❌ FirebaseService: Erreur upload image: \(error.localizedDescription)")
                print("❌ FirebaseService: Code erreur: \((error as NSError).code)")
                print("❌ FirebaseService: Domaine erreur: \((error as NSError).domain)")
                
                // Log des détails supplémentaires pour debug
                if let storageError = error as NSError? {
                    print("❌ FirebaseService: UserInfo: \(storageError.userInfo)")
                }
                
                endBackgroundTask()
                completion(nil)
                return
            }
            
            print("✅ FirebaseService: Upload réussi - Métadonnées: \(uploadMetadata?.description ?? "nil")")
            print("🔥 FirebaseService: Récupération URL de téléchargement...")
            
            // 🛡️ SÉCURITÉ: Génération d'URL sécurisée avec expiration limitée
            profileImageRef.downloadURL { url, urlError in
                print("🔥 FirebaseService: Callback downloadURL reçu")
                
                if let urlError = urlError {
                    print("❌ FirebaseService: Erreur récupération URL: \(urlError.localizedDescription)")
                    endBackgroundTask()
                    completion(nil)
                } else if let downloadURL = url {
                    print("✅ FirebaseService: URL de téléchargement sécurisée obtenue")
                    print("🛡️ FirebaseService: URL expiration gérée par Firebase Security Rules")
                    endBackgroundTask()
                    completion(downloadURL.absoluteString)
                } else {
                    print("❌ FirebaseService: URL de téléchargement nil inexpliquée")
                    endBackgroundTask()
                    completion(nil)
                }
            }
        }
    }
    
    private func resizeImage(_ image: UIImage, to size: CGSize) -> UIImage? {
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        image.draw(in: CGRect(origin: .zero, size: size))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return resizedImage
    }
    
    // MARK: - Shared Partner Data (Sécurisé)
    
    func updateSharedPartnerData() {
        guard let firebaseUser = Auth.auth().currentUser,
              let currentUser = currentUser else {
            print("❌ FirebaseService: Aucun utilisateur pour partage données")
            return
        }
        
        // Données sécurisées à partager avec le partenaire (pour widgets)
        let sharedData: [String: Any] = [
            "name": currentUser.name,
            "relationshipStartDate": currentUser.relationshipStartDate != nil ? Timestamp(date: currentUser.relationshipStartDate!) : NSNull(),
            "currentLocation": currentUser.currentLocation != nil ? [
                "latitude": currentUser.currentLocation!.latitude,
                "longitude": currentUser.currentLocation!.longitude,
                "city": currentUser.currentLocation!.city as Any,
                "country": currentUser.currentLocation!.country as Any,
                "lastUpdated": Timestamp(date: currentUser.currentLocation!.lastUpdated)
            ] : NSNull(),
            "lastActive": Timestamp(date: Date()),
            "profileImageURL": currentUser.profileImageURL as Any
        ]
        
        db.collection("sharedPartnerData").document(firebaseUser.uid).setData(sharedData, merge: true) { error in
            if let error = error {
                print("❌ FirebaseService: Erreur partage données: \(error.localizedDescription)")
            } else {
                print("✅ FirebaseService: Données partagées mises à jour")
            }
        }
    }
    
    func getSharedPartnerData(partnerId: String, completion: @escaping (AppUser?) -> Void) {
        print("🔥 FirebaseService: Récupération données partagées partenaire: \(partnerId)")
        
        db.collection("sharedPartnerData").document(partnerId).getDocument { document, error in
            if let error = error {
                print("❌ FirebaseService: Erreur récupération données partagées: \(error.localizedDescription)")
                completion(nil)
                return
            }
            
            guard let document = document, document.exists,
                  let data = document.data() else {
                print("❌ FirebaseService: Données partagées non trouvées: \(partnerId)")
                completion(nil)
                return
            }
            
            // Créer un AppUser avec seulement les données partagées
            let partnerUser = AppUser(
                id: partnerId,
                name: data["name"] as? String ?? "",
                birthDate: Date(), // Non partagé
                relationshipGoals: [], // Non partagé
                relationshipDuration: .none, // Non partagé
                relationshipImprovement: nil, // Non partagé
                questionMode: nil, // Non partagé
                partnerCode: nil, // Non partagé
                partnerId: nil, // Non partagé
                partnerConnectedAt: nil, // Non partagé
                subscriptionInheritedFrom: nil, // Non partagé
                subscriptionInheritedAt: nil, // Non partagé
                connectedPartnerCode: nil, // Non partagé
                connectedPartnerId: nil, // Non partagé
                connectedAt: nil, // Non partagé
                isSubscribed: false, // Non partagé
                onboardingInProgress: false,
                relationshipStartDate: (data["relationshipStartDate"] as? Timestamp)?.dateValue(),
                profileImageURL: data["profileImageURL"] as? String,
                currentLocation: self.parseUserLocation(from: data["currentLocation"] as? [String: Any])
            )
            
            print("✅ FirebaseService: Données partagées récupérées: \(partnerUser.name)")
            completion(partnerUser)
        }
    }
    
    // MARK: - Synchronisation des entrées de journal
    
    func syncPartnerJournalEntries(partnerId: String, completion: @escaping (Bool, String?) -> Void) {
        print("📚 FirebaseService: Début synchronisation entrées journal avec partenaire: \(partnerId)")
        
        guard Auth.auth().currentUser != nil else {
            print("❌ FirebaseService: Aucun utilisateur connecté")
            completion(false, "Utilisateur non connecté")
            return
        }
        
        let functions = Functions.functions()
        let syncFunction = functions.httpsCallable("syncPartnerJournalEntries")
        
        syncFunction.call(["partnerId": partnerId]) { result, error in
            if let error = error {
                print("❌ FirebaseService: Erreur synchronisation journal: \(error.localizedDescription)")
                completion(false, "Erreur lors de la synchronisation: \(error.localizedDescription)")
                return
            }
            
            guard let data = result?.data as? [String: Any],
                  let success = data["success"] as? Bool else {
                print("❌ FirebaseService: Réponse invalide de la fonction")
                completion(false, "Réponse invalide du serveur")
                return
            }
            
            if success {
                let updatedCount = data["updatedEntriesCount"] as? Int ?? 0
                let userEntriesCount = data["userEntriesCount"] as? Int ?? 0
                let partnerEntriesCount = data["partnerEntriesCount"] as? Int ?? 0
                let message = data["message"] as? String ?? "Synchronisation terminée"
                
                print("✅ FirebaseService: Synchronisation journal réussie")
                print("📚 FirebaseService: - Entrées mises à jour: \(updatedCount)")
                print("📚 FirebaseService: - Vos entrées: \(userEntriesCount)")
                print("📚 FirebaseService: - Entrées partenaire: \(partnerEntriesCount)")
                
                completion(true, message)
            } else {
                let errorMessage = data["message"] as? String ?? "Erreur inconnue"
                print("❌ FirebaseService: Échec synchronisation journal: \(errorMessage)")
                completion(false, errorMessage)
            }
        }
    }
    
    // MARK: - Synchronisation des favoris entre partenaires
    
    func syncPartnerFavorites(partnerId: String, completion: @escaping (Bool, String?) -> Void) {
        print("❤️ FirebaseService: Début synchronisation favoris avec partenaire: \(partnerId)")
        
        guard Auth.auth().currentUser != nil else {
            print("❌ FirebaseService: Aucun utilisateur connecté")
            completion(false, "Utilisateur non connecté")
            return
        }
        
        let functions = Functions.functions()
        let syncFunction = functions.httpsCallable("syncPartnerFavorites")
        
        syncFunction.call(["partnerId": partnerId]) { result, error in
            if let error = error {
                print("❌ FirebaseService: Erreur synchronisation favoris: \(error.localizedDescription)")
                completion(false, "Erreur lors de la synchronisation: \(error.localizedDescription)")
                return
            }
            
            guard let data = result?.data as? [String: Any],
                  let success = data["success"] as? Bool else {
                print("❌ FirebaseService: Réponse invalide de la fonction")
                completion(false, "Réponse invalide du serveur")
                return
            }
            
            if success {
                let updatedCount = data["updatedFavoritesCount"] as? Int ?? 0
                let userCount = data["userFavoritesCount"] as? Int ?? 0
                let partnerCount = data["partnerFavoritesCount"] as? Int ?? 0
                
                print("✅ FirebaseService: Synchronisation favoris réussie")
                print("✅ FirebaseService: \(updatedCount) favoris mis à jour")
                print("✅ FirebaseService: Favoris utilisateur: \(userCount), Favoris partenaire: \(partnerCount)")
                
                completion(true, "Synchronisation réussie: \(updatedCount) favoris mis à jour")
            } else {
                let message = data["message"] as? String ?? "Erreur inconnue"
                print("❌ FirebaseService: Échec synchronisation favoris: \(message)")
                completion(false, message)
            }
        }
    }
    
    // MARK: - Public Methods for Data Refresh
    
    func forceRefreshUserData() {
        print("🔄 FirebaseService: Rechargement forcé des données utilisateur")
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Aucun utilisateur Firebase pour rechargement")
            return
        }
        
        print("🔄 FirebaseService: Rechargement pour UID: \(firebaseUser.uid)")
        loadUserData(uid: firebaseUser.uid)
    }
} 