import Foundation
import FirebaseFirestore
import FirebaseAuth
import FirebaseFunctions
import Combine

class PartnerCodeService: ObservableObject {
    static let shared = PartnerCodeService()
    
    private let db = Firestore.firestore()
    
    @Published var generatedCode: String?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isConnected = false
    @Published var partnerInfo: PartnerInfo?
    
    struct PartnerInfo {
        let id: String
        let name: String
        let connectedAt: Date
        let isSubscribed: Bool
    }
    
    private init() {}
    
    // MARK: - Génération d'un code partenaire permanent
    
    func generatePartnerCode() async -> String? {
        print("🔗 PartnerCodeService: Début génération code")
        
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ PartnerCodeService: Utilisateur non connecté")
            await MainActor.run {
                self.errorMessage = "Utilisateur non connecté"
            }
            return nil
        }
        
        print("🔗 PartnerCodeService: Utilisateur connecté: \(currentUser.uid)")
        
        await MainActor.run {
            self.isLoading = true
            self.errorMessage = nil
        }
        
        // Vérifier si l'utilisateur a déjà un code
        do {
            print("🔗 PartnerCodeService: Vérification code existant...")
            let existingCodeSnapshot = try await db.collection("partnerCodes")
                .whereField("userId", isEqualTo: currentUser.uid)
                .getDocuments()
            
            print("🔗 PartnerCodeService: Nombre de codes trouvés: \(existingCodeSnapshot.documents.count)")
            
            // Si un code existe déjà, le retourner
            if let existingDoc = existingCodeSnapshot.documents.first {
                let existingCode = existingDoc.documentID
                print("🔗 PartnerCodeService: Code existant trouvé: \(existingCode)")
                await MainActor.run {
                    self.generatedCode = existingCode
                    self.isLoading = false
                }
                print("✅ PartnerCodeService: Code existant retourné et UI mise à jour")
                return existingCode
            }
            
            print("🔗 PartnerCodeService: Aucun code existant, génération d'un nouveau...")
            
            // Générer un nouveau code unique
            var code: String
            var isUnique = false
            var attempts = 0
            
            repeat {
                code = String(format: "%08d", Int.random(in: 10000000...99999999))
                print("🔗 PartnerCodeService: Tentative \(attempts + 1) - Code généré: \(code)")
                
                // Vérifier si le code existe déjà
                let existingDoc = try await db.collection("partnerCodes").document(code).getDocument()
                isUnique = !existingDoc.exists
                attempts += 1
                
                print("🔗 PartnerCodeService: Code \(code) unique: \(isUnique)")
                
                if attempts > 10 {
                    print("❌ PartnerCodeService: Trop de tentatives, abandon")
                    await MainActor.run {
                        self.errorMessage = "Erreur lors de la génération du code"
                        self.isLoading = false
                    }
                    return nil
                }
            } while !isUnique
            
            print("🔗 PartnerCodeService: Code unique trouvé: \(code), création en base...")
            
            // Créer le nouveau code permanent
            try await db.collection("partnerCodes").document(code).setData([
                "userId": currentUser.uid,
                "createdAt": Timestamp(date: Date()),
                "isActive": true,
                "connectedPartnerId": NSNull() // Pas encore connecté
            ])
            
            print("✅ PartnerCodeService: Code créé en base avec succès")
            
            await MainActor.run {
                self.generatedCode = code
                self.isLoading = false
            }
            
            print("✅ PartnerCodeService: Nouveau code généré et UI mise à jour: \(code)")
            return code
            
        } catch {
            print("❌ PartnerCodeService: Erreur génération code: \(error)")
            print("❌ PartnerCodeService: Détails erreur: \(error.localizedDescription)")
            await MainActor.run {
                self.errorMessage = "Erreur lors de la génération du code: \(error.localizedDescription)"
                self.isLoading = false
            }
            return nil
        }
    }
    
    // MARK: - Connexion avec un code partenaire
    
    func connectWithPartnerCode(_ code: String) async -> Bool {
        print("🔗 PartnerCodeService: connectWithPartnerCode - Code: \(code)")
        
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ PartnerCodeService: Utilisateur non connecté")
            await MainActor.run {
                self.errorMessage = "Utilisateur non connecté"
            }
            return false
        }
        
        print("🔗 PartnerCodeService: Utilisateur connecté: \(currentUser.uid)")
        
        await MainActor.run {
            self.isLoading = true
            self.errorMessage = nil
        }
        
        do {
            // Connexion sécurisée côté serveur
            print("🔗 PartnerCodeService: Connexion sécurisée via Cloud Function...")
            let functions = Functions.functions()
            let connectFunction = functions.httpsCallable("connectPartners")
            
            let result = try await connectFunction.call(["partnerCode": code])
            guard let data = result.data as? [String: Any] else {
                print("❌ PartnerCodeService: Réponse de connexion invalide")
                await MainActor.run {
                    self.errorMessage = "Erreur lors de la connexion"
                    self.isLoading = false
                }
                return false
            }
            
            guard let success = data["success"] as? Bool, success else {
                print("❌ PartnerCodeService: Connexion échouée")
                await MainActor.run {
                    self.errorMessage = "Erreur lors de la connexion"
                    self.isLoading = false
                }
                return false
            }
            
            let partnerName = data["partnerName"] as? String ?? "Partenaire"
            let subscriptionInherited = data["subscriptionInherited"] as? Bool ?? false
            let message = data["message"] as? String ?? "Connexion réussie"
            
            print("✅ PartnerCodeService: Connexion réussie - Partenaire: \(partnerName)")
            print("✅ PartnerCodeService: Abonnement hérité: \(subscriptionInherited)")
            
            // Mettre à jour l'interface utilisateur
            await MainActor.run {
                self.isConnected = true
                self.partnerInfo = PartnerInfo(
                    id: "", // Sera mis à jour par les listeners
                    name: partnerName,
                    connectedAt: Date(),
                    isSubscribed: subscriptionInherited
                )
                self.isLoading = false
                
                // Afficher un message de succès personnalisé
                if subscriptionInherited {
                    print("🎉 PartnerCodeService: Abonnement premium débloqué !")
                }
            }
            
            // Envoyer les notifications de connexion réussie
            notifyConnectionSuccess(partnerName: partnerName, subscriptionInherited: subscriptionInherited)
            
            return true
            
        } catch {
            print("❌ PartnerCodeService: Erreur connexion: \(error)")
            
            // Gérer les erreurs spécifiques de Firebase Functions
            var errorMessage = "Erreur lors de la connexion"
            
            if let functionsError = error as NSError? {
                switch functionsError.code {
                case 3: // INVALID_ARGUMENT
                    errorMessage = functionsError.localizedDescription
                case 5: // NOT_FOUND
                    errorMessage = functionsError.localizedDescription
                case 6: // ALREADY_EXISTS
                    errorMessage = functionsError.localizedDescription
                case 8: // RESOURCE_EXHAUSTED
                    errorMessage = functionsError.localizedDescription
                case 9: // FAILED_PRECONDITION
                    errorMessage = functionsError.localizedDescription
                case 16: // UNAUTHENTICATED
                    errorMessage = "Vous devez être connecté"
                default:
                    errorMessage = functionsError.localizedDescription.isEmpty ? "Erreur lors de la connexion" : functionsError.localizedDescription
                }
            }
            
            await MainActor.run {
                self.errorMessage = errorMessage
                self.isLoading = false
            }
            return false
        }
    }
    
    // MARK: - Notifications de connexion réussie
    
    private func notifyConnectionSuccess(partnerName: String, subscriptionInherited: Bool) {
        // Notifier l'héritage d'abonnement si applicable
        if subscriptionInherited {
            print("✅ PartnerCodeService: Notification héritage abonnement envoyée")
            NotificationCenter.default.post(name: .subscriptionInherited, object: nil)
        }
        
        // Notifier la connexion réussie
        NotificationCenter.default.post(
            name: .partnerConnectionSuccess, 
            object: nil, 
            userInfo: ["partnerName": partnerName, "isSubscribed": subscriptionInherited]
        )
        
        print("✅ PartnerCodeService: Notifications de connexion envoyées")
    }
    
    // MARK: - Vérifier la connexion existante
    
    func checkExistingConnection() async {
        print("🔍 PartnerCodeService: checkExistingConnection - Début vérification")
        guard let currentUser = Auth.auth().currentUser else { 
            print("❌ PartnerCodeService: checkExistingConnection - Utilisateur non connecté")
            return 
        }
        
        do {
            print("🔍 PartnerCodeService: checkExistingConnection - Chargement données utilisateur: \(currentUser.uid)")
            let doc = try await db.collection("users").document(currentUser.uid).getDocument()
            
            if let data = doc.data(),
               let partnerId = data["partnerId"] as? String,
               !partnerId.isEmpty {
                
                print("🔍 PartnerCodeService: checkExistingConnection - Partenaire trouvé: \(partnerId)")
                
                // 🔧 CORRECTION: Utiliser Cloud Function pour récupérer les infos du partenaire
                do {
                    print("🔍 PartnerCodeService: checkExistingConnection - Récupération via Cloud Function")
                    let functions = Functions.functions()
                    let result = try await functions.httpsCallable("getPartnerInfo").call([
                        "partnerId": partnerId
                    ])
                    
                    if let resultData = result.data as? [String: Any],
                       let success = resultData["success"] as? Bool,
                       success,
                       let partnerData = resultData["partnerInfo"] as? [String: Any],
                       let connectedAt = data["partnerConnectedAt"] as? Timestamp {
                        
                        let partnerName = partnerData["name"] as? String ?? "Partenaire"
                        let partnerIsSubscribed = partnerData["isSubscribed"] as? Bool ?? false
                        
                        print("✅ PartnerCodeService: checkExistingConnection - Partenaire: \(partnerName), Abonné: \(partnerIsSubscribed)")
                        
                        await MainActor.run {
                            self.isConnected = true
                            self.partnerInfo = PartnerInfo(
                                id: partnerId,
                                name: partnerName,
                                connectedAt: connectedAt.dateValue(),
                                isSubscribed: partnerIsSubscribed
                            )
                        }
                    } else {
                        print("❌ PartnerCodeService: checkExistingConnection - Échec récupération info partenaire")
                    }
                } catch {
                    print("❌ PartnerCodeService: checkExistingConnection - Erreur Cloud Function: \(error)")
                }
            } else {
                print("🔍 PartnerCodeService: checkExistingConnection - Aucun partenaire connecté")
            }
            
            // Vérifier si l'utilisateur a un code généré
            print("🔍 PartnerCodeService: checkExistingConnection - Vérification code généré")
            let codeSnapshot = try await db.collection("partnerCodes")
                .whereField("userId", isEqualTo: currentUser.uid)
                .getDocuments()
            
            if let codeDoc = codeSnapshot.documents.first {
                print("✅ PartnerCodeService: checkExistingConnection - Code trouvé: \(codeDoc.documentID)")
                await MainActor.run {
                    self.generatedCode = codeDoc.documentID
                }
            } else {
                print("🔍 PartnerCodeService: checkExistingConnection - Aucun code généré")
            }
            
        } catch {
            print("❌ PartnerCodeService: Erreur vérification connexion: \(error)")
        }
    }
    
    // MARK: - Déconnexion du partenaire (SÉCURISÉE)
    
    func disconnectPartner() async -> Bool {
        print("🔗 PartnerCodeService: disconnectPartner - Début déconnexion sécurisée")
        
        guard let currentUser = Auth.auth().currentUser else { 
            print("❌ PartnerCodeService: disconnectPartner - Utilisateur non connecté")
            return false 
        }
        
        await MainActor.run {
            self.isLoading = true
            self.errorMessage = nil
        }
        
        do {
            // Utiliser la fonction Cloud sécurisée pour déconnecter
            print("🔗 PartnerCodeService: disconnectPartner - Appel Cloud Function")
            let functions = Functions.functions()
            let result = try await functions.httpsCallable("disconnectPartners").call([:])
            
            guard let data = result.data as? [String: Any],
                  let success = data["success"] as? Bool,
                  success else {
                print("❌ PartnerCodeService: disconnectPartner - Échec Cloud Function")
                await MainActor.run {
                    self.errorMessage = "Erreur lors de la déconnexion"
                    self.isLoading = false
                }
                return false
            }
            
            await MainActor.run {
                self.isConnected = false
                self.partnerInfo = nil
                self.isLoading = false
            }
            
            // Notifier la déconnexion
            await MainActor.run {
                NotificationCenter.default.post(
                    name: .partnerDisconnected,
                    object: nil
                )
            }
            
            print("✅ PartnerCodeService: Déconnexion réussie via Cloud Function")
            return true
            
        } catch {
            await MainActor.run {
                self.errorMessage = "Erreur lors de la déconnexion: \(error.localizedDescription)"
                self.isLoading = false
            }
            print("❌ PartnerCodeService: Erreur déconnexion: \(error)")
            return false
        }
    }
    
    // MARK: - Suppression du code lors de la suppression du compte
    
    func deleteUserPartnerCode() async {
        guard let currentUser = Auth.auth().currentUser else { return }
        
        do {
            // Supprimer le code de l'utilisateur
            let codeSnapshot = try await db.collection("partnerCodes")
                .whereField("userId", isEqualTo: currentUser.uid)
                .getDocuments()
            
            for document in codeSnapshot.documents {
                try await document.reference.delete()
            }
            
            // Libérer les codes où cet utilisateur était connecté
            let connectedCodesSnapshot = try await db.collection("partnerCodes")
                .whereField("connectedPartnerId", isEqualTo: currentUser.uid)
                .getDocuments()
            
            for document in connectedCodesSnapshot.documents {
                try await document.reference.updateData([
                    "connectedPartnerId": NSNull(),
                    "connectedAt": FieldValue.delete()
                ])
            }
            
            print("✅ PartnerCodeService: Codes partenaire supprimés pour l'utilisateur")
            
        } catch {
            print("❌ PartnerCodeService: Erreur suppression codes: \(error)")
        }
    }
    
    // MARK: - Helpers
    
    func clearGeneratedCode() {
        generatedCode = nil
    }
    
    func clearError() {
        errorMessage = nil
    }
    
    // MARK: - Vérifier les messages de connexion en attente
    
    func checkForPendingConnectionMessage() async {
        print("🔍 PartnerCodeService: checkForPendingConnectionMessage - Début vérification")
        guard let currentUser = Auth.auth().currentUser else { 
            print("❌ PartnerCodeService: checkForPendingConnectionMessage - Utilisateur non connecté")
            return 
        }
        
        do {
            print("🔍 PartnerCodeService: checkForPendingConnectionMessage - Chargement données utilisateur: \(currentUser.uid)")
            let doc = try await db.collection("users").document(currentUser.uid).getDocument()
            
            if let data = doc.data(),
               let hasUnreadConnection = data["hasUnreadPartnerConnection"] as? Bool,
               hasUnreadConnection,
               let partnerId = data["partnerId"] as? String,
               !partnerId.isEmpty {
                
                print("🔍 PartnerCodeService: checkForPendingConnectionMessage - Message en attente pour partenaire: \(partnerId)")
                
                // 🔧 CORRECTION: Utiliser Cloud Function pour récupérer le nom du partenaire
                do {
                    print("🔍 PartnerCodeService: checkForPendingConnectionMessage - Récupération nom via Cloud Function")
                    let functions = Functions.functions()
                    let result = try await functions.httpsCallable("getPartnerInfo").call([
                        "partnerId": partnerId
                    ])
                    
                    if let resultData = result.data as? [String: Any],
                       let success = resultData["success"] as? Bool,
                       success,
                       let partnerData = resultData["partnerInfo"] as? [String: Any],
                       let partnerName = partnerData["name"] as? String {
                        
                        print("✅ PartnerCodeService: checkForPendingConnectionMessage - Nom partenaire: \(partnerName)")
                        
                        // Marquer comme lu
                        try await db.collection("users").document(currentUser.uid).updateData([
                            "hasUnreadPartnerConnection": FieldValue.delete()
                        ])
                        
                        await MainActor.run {
                            // Notifier qu'il faut afficher le message de connexion
                            NotificationCenter.default.post(
                                name: .shouldShowConnectionSuccess,
                                object: nil,
                                userInfo: ["partnerName": partnerName]
                            )
                        }
                        
                        print("✅ PartnerCodeService: checkForPendingConnectionMessage - Notification envoyée")
                    } else {
                        print("❌ PartnerCodeService: checkForPendingConnectionMessage - Échec récupération nom partenaire")
                    }
                } catch {
                    print("❌ PartnerCodeService: checkForPendingConnectionMessage - Erreur Cloud Function: \(error)")
                }
            } else {
                print("🔍 PartnerCodeService: checkForPendingConnectionMessage - Aucun message en attente")
            }
            
        } catch {
            print("❌ PartnerCodeService: Erreur vérification message connexion: \(error)")
        }
    }
}

// MARK: - Notifications
extension Notification.Name {
    static let subscriptionInherited = Notification.Name("subscriptionInherited")
    static let partnerConnected = Notification.Name("partnerConnected")
    static let partnerDisconnected = Notification.Name("partnerDisconnected")
    static let partnerConnectionSuccess = Notification.Name("partnerConnectionSuccess")
    static let shouldShowConnectionSuccess = Notification.Name("shouldShowConnectionSuccess")
    static let subscriptionUpdated = Notification.Name("subscriptionUpdated")
    static let partnerSubscriptionShared = Notification.Name("partnerSubscriptionShared")
    static let partnerSubscriptionRevoked = Notification.Name("partnerSubscriptionRevoked")
} 