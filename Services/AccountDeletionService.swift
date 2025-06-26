import Foundation
import FirebaseAuth
import FirebaseFirestore
import AuthenticationServices
import FirebaseFunctions
import UIKit

class AccountDeletionService: NSObject, ObservableObject {
    static let shared = AccountDeletionService()
    
    // Variables pour gérer la ré-authentification
    private var pendingDeletionCompletion: ((Bool) -> Void)?
    private var userToDelete: FirebaseAuth.User?
    
    private override init() {
        super.init()
    }
    
    func deleteAccount(completion: @escaping (Bool) -> Void) {
        print("🔥 AccountDeletionService: Début de la suppression du compte")
        
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ AccountDeletionService: Aucun utilisateur connecté")
            completion(false)
            return
        }
        
        let userId = currentUser.uid
        print("🔥 AccountDeletionService: Suppression du compte pour l'utilisateur: \(userId)")
        
        // Étape 1: Appeler la fonction Firebase pour supprimer côté serveur d'abord
        callServerCleanupFunction(userId: userId) { [weak self] serverSuccess in
            print("🔥 AccountDeletionService: Nettoyage serveur: \(serverSuccess ? "succès" : "échec")")
            
            // Étape 2: Supprimer les données utilisateur de Firestore (même si serveur échoue)
            self?.deleteUserDataFromFirestore(userId: userId) { [weak self] firestoreSuccess in
                print("🔥 AccountDeletionService: Suppression Firestore: \(firestoreSuccess ? "succès" : "échec")")
                
                // Étape 3: Vérifier que les données sont bien supprimées
                self?.verifyDataDeletion(userId: userId) { verificationSuccess in
                    print("🔥 AccountDeletionService: Vérification suppression: \(verificationSuccess ? "succès" : "échec")")
                    
                    // Étape 4: Supprimer le compte Firebase Auth en dernier
                    self?.deleteFirebaseAuthAccount(user: currentUser) { authSuccess in
                        print("🔥 AccountDeletionService: Suppression Auth: \(authSuccess ? "succès" : "échec")")
                        
                        // Considérer comme réussi si au moins Firestore est supprimé
                        let overallSuccess = firestoreSuccess || verificationSuccess
                        print("🔥 AccountDeletionService: Résultat final: \(overallSuccess ? "succès" : "échec")")
                        completion(overallSuccess)
                    }
                }
            }
        }
    }
    
    private func deleteUserDataFromFirestore(userId: String, completion: @escaping (Bool) -> Void) {
        print("🔥 AccountDeletionService: Suppression des données Firestore pour: \(userId)")
        
        let db = Firestore.firestore()
        let userRef = db.collection("users").document(userId)
        
        // ÉTAPE 1: Supprimer les codes partenaires associés
        Task {
            await PartnerCodeService.shared.deleteUserPartnerCode()
            print("✅ AccountDeletionService: Codes partenaires supprimés")
            
            // ÉTAPE 2: Continuer avec la suppression du document utilisateur
            userRef.getDocument { document, error in
                if let error = error {
                    print("❌ AccountDeletionService: Erreur lors de la vérification: \(error.localizedDescription)")
                    completion(false)
                    return
                }
                
                if let document = document, document.exists {
                    print("🔥 AccountDeletionService: Document trouvé, suppression en cours...")
                    print("🔥 AccountDeletionService: Données à supprimer: \(document.data() ?? [:])")
                    
                    // Supprimer le document
                    userRef.delete { deleteError in
                        if let deleteError = deleteError {
                            print("❌ AccountDeletionService: Erreur suppression Firestore: \(deleteError.localizedDescription)")
                            
                            // Tenter une suppression avec overwrite
                            print("🔥 AccountDeletionService: Tentative de suppression par overwrite...")
                            userRef.setData([:]) { overwriteError in
                                if let overwriteError = overwriteError {
                                    print("❌ AccountDeletionService: Échec overwrite: \(overwriteError.localizedDescription)")
                                    completion(false)
                                } else {
                                    print("✅ AccountDeletionService: Document vidé par overwrite")
                                    // Maintenant essayer de supprimer le document vide
                                    userRef.delete { finalDeleteError in
                                        if let finalDeleteError = finalDeleteError {
                                            print("❌ AccountDeletionService: Échec suppression finale: \(finalDeleteError.localizedDescription)")
                                            completion(false)
                                        } else {
                                            print("✅ AccountDeletionService: Document supprimé après overwrite")
                                            completion(true)
                                        }
                                    }
                                }
                            }
                        } else {
                            print("✅ AccountDeletionService: Document utilisateur supprimé de Firestore")
                            completion(true)
                        }
                    }
                } else {
                    print("🔥 AccountDeletionService: Aucun document trouvé pour l'utilisateur \(userId)")
                    completion(true) // Pas de document = déjà supprimé
                }
            }
        }
    }
    
    private func verifyDataDeletion(userId: String, completion: @escaping (Bool) -> Void) {
        print("🔥 AccountDeletionService: Vérification de la suppression pour: \(userId)")
        
        let db = Firestore.firestore()
        let userRef = db.collection("users").document(userId)
        
        userRef.getDocument { document, error in
            if let error = error {
                print("❌ AccountDeletionService: Erreur lors de la vérification: \(error.localizedDescription)")
                completion(false)
            } else if let document = document, document.exists {
                print("❌ AccountDeletionService: PROBLÈME - Les données existent encore dans Firestore!")
                print("❌ AccountDeletionService: Données trouvées: \(document.data() ?? [:])")
                // Tenter une suppression forcée
                userRef.delete { deleteError in
                    if let deleteError = deleteError {
                        print("❌ AccountDeletionService: Échec de la suppression forcée: \(deleteError.localizedDescription)")
                        completion(false)
                    } else {
                        print("✅ AccountDeletionService: Suppression forcée réussie")
                        completion(true)
                    }
                }
            } else {
                print("✅ AccountDeletionService: Vérification réussie - aucune donnée trouvée")
                completion(true)
            }
        }
    }
    
    private func deleteFirebaseAuthAccount(user: FirebaseAuth.User, completion: @escaping (Bool) -> Void) {
        print("🔥 AccountDeletionService: Suppression du compte Firebase Auth")
        
        // Pour Apple Sign In, nous devons parfois ré-authentifier l'utilisateur
        // avant de pouvoir supprimer le compte
        user.delete { error in
            if let error = error {
                print("❌ AccountDeletionService: Erreur suppression Firebase Auth: \(error.localizedDescription)")
                
                // Si l'erreur indique qu'une ré-authentification est nécessaire
                if let authError = error as? AuthErrorCode,
                   authError.code == .requiresRecentLogin {
                    print("🔥 AccountDeletionService: Ré-authentification requise")
                    self.reauthenticateAndDelete(user: user, completion: completion)
                } else {
                    completion(false)
                }
            } else {
                print("✅ AccountDeletionService: Compte Firebase Auth supprimé")
                completion(true)
            }
        }
    }
    
    private func reauthenticateAndDelete(user: FirebaseAuth.User, completion: @escaping (Bool) -> Void) {
        print("🔥 AccountDeletionService: Tentative de ré-authentification pour suppression")
        
        // Stocker la completion pour l'utiliser dans le delegate
        self.pendingDeletionCompletion = completion
        self.userToDelete = user
        
        // Pour Apple Sign In, nous devons demander une nouvelle authentification
        let appleIDProvider = ASAuthorizationAppleIDProvider()
        let request = appleIDProvider.createRequest()
        request.requestedScopes = [] // Pas besoin de scopes pour la ré-auth
        
        let authorizationController = ASAuthorizationController(authorizationRequests: [request])
        authorizationController.delegate = self
        authorizationController.presentationContextProvider = self
        authorizationController.performRequests()
    }
    
    private func callServerCleanupFunction(userId: String, completion: @escaping (Bool) -> Void) {
        print("🔥 AccountDeletionService: Appel de la fonction de nettoyage serveur")
        
        // Appeler la fonction Firebase pour supprimer le compte côté serveur
        let functions = Functions.functions()
        let deleteAccountFunction = functions.httpsCallable("deleteUserAccount")
        
        deleteAccountFunction.call { result, error in
            if let error = error {
                print("❌ AccountDeletionService: Erreur fonction Firebase: \(error.localizedDescription)")
                // Même en cas d'erreur, on considère la suppression locale comme réussie
                completion(true)
            } else {
                print("✅ AccountDeletionService: Fonction Firebase exécutée avec succès")
                completion(true)
            }
        }
    }
    
    // MARK: - Méthodes de test et debug
    
    func forceDeleteFirestoreData(completion: @escaping (Bool) -> Void) {
        print("🔥 AccountDeletionService: SUPPRESSION FORCÉE des données Firestore")
        
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ AccountDeletionService: Aucun utilisateur connecté pour suppression forcée")
            completion(false)
            return
        }
        
        let userId = currentUser.uid
        deleteUserDataFromFirestore(userId: userId, completion: completion)
    }
}

// Extension pour gérer la ré-authentification Apple
extension AccountDeletionService: ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first else {
            return UIWindow()
        }
        return window
    }
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        print("🔥 AccountDeletionService: Ré-authentification Apple réussie")
        
        guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let identityToken = appleIDCredential.identityToken,
              let idTokenString = String(data: identityToken, encoding: .utf8),
              let user = userToDelete else {
            print("❌ AccountDeletionService: Données de ré-authentification invalides")
            pendingDeletionCompletion?(false)
            cleanup()
            return
        }
        
        // Créer les credentials Firebase avec le nouveau token
        let credential = OAuthProvider.credential(withProviderID: "apple.com",
                                                idToken: idTokenString,
                                                accessToken: nil)
        
        // Ré-authentifier l'utilisateur Firebase
        user.reauthenticate(with: credential) { [weak self] result, error in
            if let error = error {
                print("❌ AccountDeletionService: Erreur ré-authentification Firebase: \(error.localizedDescription)")
                self?.pendingDeletionCompletion?(false)
                self?.cleanup()
            } else {
                print("✅ AccountDeletionService: Ré-authentification Firebase réussie")
                // Maintenant essayer de supprimer le compte
                user.delete { deleteError in
                    if let deleteError = deleteError {
                        print("❌ AccountDeletionService: Erreur suppression après ré-auth: \(deleteError.localizedDescription)")
                        self?.pendingDeletionCompletion?(false)
                    } else {
                        print("✅ AccountDeletionService: Compte supprimé après ré-authentification")
                        self?.pendingDeletionCompletion?(true)
                    }
                    self?.cleanup()
                }
            }
        }
    }
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        print("❌ AccountDeletionService: Erreur ré-authentification Apple: \(error.localizedDescription)")
        
        // Vérifier si l'utilisateur a annulé
        if let authError = error as? ASAuthorizationError, authError.code == .canceled {
            print("🔥 AccountDeletionService: Ré-authentification annulée par l'utilisateur")
        }
        
        pendingDeletionCompletion?(false)
        cleanup()
    }
    
    private func cleanup() {
        pendingDeletionCompletion = nil
        userToDelete = nil
    }
} 