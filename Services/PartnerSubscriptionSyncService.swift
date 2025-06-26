import Foundation
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions

class PartnerSubscriptionSyncService: ObservableObject {
    static let shared = PartnerSubscriptionSyncService()
    
    private var cancellables = Set<AnyCancellable>()
    private var userListener: ListenerRegistration?
    private var partnerListener: ListenerRegistration?
    
    private init() {
        startListeningForUser()
        setupAuthObserver()
    }
    
    private func setupAuthObserver() {
        // Observer les changements d'authentification
        Auth.auth().addStateDidChangeListener { [weak self] (auth: Auth, user: FirebaseAuth.User?) in
            if user != nil {
                print("🔄 PartnerSubscriptionSyncService: Utilisateur reconnecté - Redémarrage listeners")
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    self?.startListeningForUser()
                }
            } else {
                print("⚠️ PartnerSubscriptionSyncService: Utilisateur déconnecté - Arrêt listeners")
                self?.stopAllListeners()
            }
        }
    }
    
    // MARK: - Écoute des changements utilisateur
    
    private func startListeningForUser() {
        guard let currentUser = Auth.auth().currentUser else { 
            print("⚠️ PartnerSubscriptionSyncService: Utilisateur non authentifié, arrêt des listeners")
            return 
        }
        
        // Arrêter les anciens listeners
        stopAllListeners()
        
        userListener = Firestore.firestore()
            .collection("users")
            .document(currentUser.uid)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    print("❌ PartnerSubscriptionSyncService: Erreur listener utilisateur: \(error)")
                    
                    // Si erreur de permissions, vérifier l'authentification
                    if error.localizedDescription.contains("permissions") {
                        print("⚠️ PartnerSubscriptionSyncService: Erreur de permissions - Vérification auth")
                        self?.handleAuthenticationError()
                    }
                    return
                }
                
                guard let data = snapshot?.data() else { 
                    print("⚠️ PartnerSubscriptionSyncService: Aucune donnée utilisateur trouvée")
                    return 
                }
                
                // 🔧 DEBUG: Logs détaillés pour identifier le problème
                let partnerIdRaw = data["partnerId"]
                print("🔍 PartnerSubscriptionSyncService: partnerId brut: \(partnerIdRaw ?? "nil")")
                print("🔍 PartnerSubscriptionSyncService: Type: \(type(of: partnerIdRaw))")
                
                // Si l'utilisateur a un partenaire connecté, écouter ses changements d'abonnement
                if let partnerId = data["partnerId"] as? String, !partnerId.isEmpty {
                    print("🔍 PartnerSubscriptionSyncService: partnerId valide trouvé: '\(partnerId)'")
                    self?.startListeningForPartner(partnerId: partnerId)
                } else {
                    print("🔍 PartnerSubscriptionSyncService: Aucun partenaire valide - arrêt écoute")
                    if let partnerIdString = data["partnerId"] as? String {
                        print("🔍 PartnerSubscriptionSyncService: partnerId est chaîne vide: '\(partnerIdString)'")
                    }
                    // Pas de partenaire, arrêter l'écoute
                    self?.stopListeningForPartner()
                }
            }
    }
    
    private func handleAuthenticationError() {
        print("🔧 PartnerSubscriptionSyncService: Gestion erreur authentification")
        
        // Arrêter tous les listeners
        stopAllListeners()
        
        // Vérifier si l'utilisateur est encore authentifié
        if Auth.auth().currentUser == nil {
            print("⚠️ PartnerSubscriptionSyncService: Utilisateur déconnecté - Arrêt du service")
        } else {
            print("🔄 PartnerSubscriptionSyncService: Redémarrage des listeners après erreur auth")
            // Redémarrer les listeners après un délai
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                self.startListeningForUser()
            }
        }
    }
    
    private func stopAllListeners() {
        userListener?.remove()
        userListener = nil
        stopListeningForPartner()
    }
    
    private func startListeningForPartner(partnerId: String) {
        // 🔧 CORRECTION: Vérification robuste du partnerId
        guard !partnerId.isEmpty, partnerId.trimmingCharacters(in: .whitespacesAndNewlines).count > 0 else {
            print("❌ PartnerSubscriptionSyncService: partnerId vide ou invalide reçu: '\(partnerId)'")
            stopListeningForPartner()
            return
        }
        
        // Arrêter l'ancienne écoute
        stopListeningForPartner()
        
        // 🔧 CORRECTION: Ne plus écouter directement les données du partenaire
        // La synchronisation se fera via Cloud Functions lors des changements d'abonnement
        print("🔄 PartnerSubscriptionSyncService: Synchronisation initiale avec partenaire: \(partnerId)")
        print("🔄 PartnerSubscriptionSyncService: partnerId validé - longueur: \(partnerId.count)")
        
        guard let currentUser = Auth.auth().currentUser else { return }
        
        // Faire une synchronisation initiale avec un petit délai pour éviter les conditions de course
        Task {
            // 🔧 CORRECTION: Petit délai pour laisser le temps aux mises à jour d'abonnement de se terminer
            try? await Task.sleep(nanoseconds: 2_000_000_000) // 2 secondes
            await self.syncSubscriptionViaCloudFunction(
                userId: currentUser.uid,
                partnerId: partnerId
            )
        }
    }
    
    private func stopListeningForPartner() {
        partnerListener?.remove()
        partnerListener = nil
    }
    
    // MARK: - Synchronisation des abonnements via Cloud Functions
    
    private func syncSubscriptionViaCloudFunction(userId: String, partnerId: String) async {
        // 🔧 CORRECTION: Vérification robuste du partnerId avant l'appel
        guard !partnerId.isEmpty, partnerId.trimmingCharacters(in: .whitespacesAndNewlines).count > 0 else {
            print("❌ PartnerSubscriptionSyncService: partnerId vide ou invalide: '\(partnerId)'")
            return
        }
        
        do {
            print("🔄 PartnerSubscriptionSyncService: Synchronisation via Cloud Function")
            print("🔄 PartnerSubscriptionSyncService: partnerId: '\(partnerId)' (longueur: \(partnerId.count))")
            
            let functions = Functions.functions()
            let data = [
                "partnerId": partnerId
            ]
            
            let result = try await functions.httpsCallable("syncPartnerSubscriptions").call(data)
            
            if let resultData = result.data as? [String: Any],
               let success = resultData["success"] as? Bool,
               success {
                print("✅ PartnerSubscriptionSyncService: Synchronisation réussie")
                
                // Notifier les changements si nécessaire
                if let inherited = resultData["subscriptionInherited"] as? Bool,
                   inherited,
                   let fromPartnerName = resultData["fromPartnerName"] as? String {
                    await MainActor.run {
                        NotificationCenter.default.post(
                            name: .partnerSubscriptionShared,
                            object: nil,
                            userInfo: [
                                "partnerId": userId,
                                "fromPartnerName": fromPartnerName
                            ]
                        )
                    }
                }
            } else {
                print("❌ PartnerSubscriptionSyncService: Échec synchronisation")
            }
            
        } catch {
            print("❌ PartnerSubscriptionSyncService: Erreur synchronisation Cloud Function: \(error)")
        }
    }
    

    
    deinit {
        userListener?.remove()
        partnerListener?.remove()
    }
    
    // MARK: - Méthode publique pour redémarrage
    
    func restart() {
        print("🔄 PartnerSubscriptionSyncService: Redémarrage manuel")
        stopAllListeners()
        startListeningForUser()
    }
} 