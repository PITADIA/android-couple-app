import Foundation
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseFirestore

class PartnerSubscriptionNotificationService: ObservableObject {
    static let shared = PartnerSubscriptionNotificationService()
    
    @Published var shouldShowSubscriptionInheritedMessage = false
    @Published var partnerName = ""
    
    @Published var shouldShowSubscriptionRevokedMessage = false
    @Published var revokedPartnerName = ""
    
    private var cancellables = Set<AnyCancellable>()
    private var partnerListener: ListenerRegistration?
    
    private init() {
        setupObservers()
        startListeningForPartnerSubscription()
    }
    
    private func setupObservers() {
        // Observer les notifications de partage d'abonnement
        NotificationCenter.default.publisher(for: .partnerSubscriptionShared)
            .sink { _ in
                print("🎁 PartnerSubscriptionNotificationService: Notification d'abonnement partagé reçue")
            }
            .store(in: &cancellables)
        
        // Observer les notifications de révocation d'abonnement
        NotificationCenter.default.publisher(for: .partnerSubscriptionRevoked)
            .sink { _ in
                print("🔒 PartnerSubscriptionNotificationService: Notification d'abonnement révoqué reçue")
                // Le partenaire sera notifié via le listener Firebase
            }
            .store(in: &cancellables)
    }
    
    private func startListeningForPartnerSubscription() {
        guard let currentUser = Auth.auth().currentUser else {
            print("⚠️ PartnerSubscriptionNotificationService: Utilisateur non authentifié")
            return
        }
        
        // Arrêter l'ancien listener
        partnerListener?.remove()
        
        partnerListener = Firestore.firestore()
            .collection("users")
            .document(currentUser.uid)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    print("❌ PartnerSubscriptionNotificationService: Erreur listener: \(error)")
                    
                    // Si erreur de permissions, gérer la déconnexion
                    if error.localizedDescription.contains("permissions") {
                        print("⚠️ PartnerSubscriptionNotificationService: Erreur de permissions - Arrêt du listener")
                        self?.partnerListener?.remove()
                        self?.partnerListener = nil
                    }
                    return
                }
                
                guard let data = snapshot?.data() else { return }
                
                // Vérifier si l'utilisateur a hérité d'un abonnement
                // 🔧 CORRECTION: Utiliser le bon nom de champ (subscriptionSharedFrom au lieu de subscriptionInheritedFrom)
                if let inheritedFrom = data["subscriptionSharedFrom"] as? String,
                   !inheritedFrom.isEmpty,  // 🔧 CORRECTION: Vérifier que le partnerId n'est pas vide
                   let isSubscribed = data["isSubscribed"] as? Bool,
                   isSubscribed {
                    Task {
                        await self?.handleSubscriptionInherited(from: inheritedFrom)
                    }
                }
                
                // Vérifier si l'utilisateur a perdu son abonnement hérité
                if let wasSubscribed = data["isSubscribed"] as? Bool,
                   !wasSubscribed,
                   let _ = data["subscriptionExpiredAt"] {
                    Task {
                        await self?.handleSubscriptionRevoked()
                    }
                }
            }
    }
    
    private func handleSubscriptionInherited(from partnerId: String) async {
        // 🔧 CORRECTION: Vérification robuste du partnerId
        guard !partnerId.isEmpty, partnerId.trimmingCharacters(in: .whitespacesAndNewlines).count > 0 else {
            print("❌ PartnerSubscriptionNotificationService: partnerId vide ou invalide: '\(partnerId)'")
            return
        }
        
        do {
            print("🔍 PartnerSubscriptionNotificationService: Récupération nom partenaire: '\(partnerId)'")
            // Récupérer le nom du partenaire qui a partagé
            let partnerDoc = try await Firestore.firestore()
                .collection("users")
                .document(partnerId)
                .getDocument()
            
            if let partnerData = partnerDoc.data(),
               let partnerName = partnerData["name"] as? String {
                
                await MainActor.run {
                    self.partnerName = partnerName
                    self.shouldShowSubscriptionInheritedMessage = true
                    print("🎁 PartnerSubscriptionNotificationService: Affichage message héritage de: \(partnerName)")
                }
            }
            
        } catch {
            print("❌ PartnerSubscriptionNotificationService: Erreur récupération nom partenaire: \(error)")
        }
    }
    
    private func handleSubscriptionRevoked() async {
        // Pour les révocations, on peut utiliser un nom générique ou récupérer depuis l'historique
        await MainActor.run {
            self.revokedPartnerName = NSLocalizedString("generic_partner", comment: "Generic partner name")
            self.shouldShowSubscriptionRevokedMessage = true
            print("🔒 PartnerSubscriptionNotificationService: Affichage message révocation")
        }
    }
    
    func dismissInheritedMessage() {
        print("🎁 PartnerSubscriptionNotificationService: Fermeture message héritage")
        shouldShowSubscriptionInheritedMessage = false
        partnerName = ""
    }
    
    func dismissRevokedMessage() {
        print("🔒 PartnerSubscriptionNotificationService: Fermeture message révocation")
        shouldShowSubscriptionRevokedMessage = false
        revokedPartnerName = ""
    }
    
    deinit {
        partnerListener?.remove()
    }
} 