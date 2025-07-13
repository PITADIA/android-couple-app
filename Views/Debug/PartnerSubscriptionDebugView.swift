import SwiftUI
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions

struct PartnerSubscriptionDebugView: View {
    @State private var userInfo: UserInfo?
    @State private var partnerInfo: UserInfo?
    @State private var isLoading = false
    
    struct UserInfo {
        let id: String
        let name: String
        let isSubscribed: Bool
        let subscriptionType: String?
        let sharedFrom: String?
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("🔄 Debug - Synchronisation Abonnements")
                    .font(.largeTitle.bold())
                    .padding(.bottom, 20)
                
                // Informations utilisateur actuel
                userSection
                
                // Informations partenaire
                partnerSection
                
                // Actions de test
                testActionsSection
                
                Spacer()
            }
            .padding()
        }
        .onAppear {
            loadUserData()
        }
        .refreshable {
            loadUserData()
        }
    }
    
    private var userSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("👤 Utilisateur Actuel")
                .font(.title2.bold())
                .foregroundColor(.blue)
            
            if let user = userInfo {
                VStack(alignment: .leading, spacing: 5) {
                    Text("ID: \(user.id)")
                        .font(.caption.monospaced())
                        .foregroundColor(.secondary)
                    
                    Text("Nom: \(user.name)")
                        .font(.headline)
                    
                    HStack {
                        Circle()
                            .fill(user.isSubscribed ? .green : .red)
                            .frame(width: 10, height: 10)
                        
                        Text(user.isSubscribed ? "Premium" : "Gratuit")
                            .font(.subheadline.bold())
                            .foregroundColor(user.isSubscribed ? .green : .red)
                    }
                    
                    if let type = user.subscriptionType {
                        Text("Type: \(type)")
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.orange.opacity(0.2))
                            .cornerRadius(8)
                    }
                    
                    if let sharedFrom = user.sharedFrom {
                        Text("Partagé par: \(sharedFrom)")
                            .font(.caption)
                            .foregroundColor(.orange)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(12)
            } else {
                ProgressView("Chargement...")
                    .padding()
            }
        }
    }
    
    private var partnerSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("👥 Partenaire Connecté")
                .font(.title2.bold())
                .foregroundColor(.purple)
            
            if let partner = partnerInfo {
                VStack(alignment: .leading, spacing: 5) {
                    Text("ID: \(partner.id)")
                        .font(.caption.monospaced())
                        .foregroundColor(.secondary)
                    
                    Text("Nom: \(partner.name)")
                        .font(.headline)
                    
                    HStack {
                        Circle()
                            .fill(partner.isSubscribed ? .green : .red)
                            .frame(width: 10, height: 10)
                        
                        Text(partner.isSubscribed ? "Premium" : "Gratuit")
                            .font(.subheadline.bold())
                            .foregroundColor(partner.isSubscribed ? .green : .red)
                    }
                    
                    if let type = partner.subscriptionType {
                        Text("Type: \(type)")
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.orange.opacity(0.2))
                            .cornerRadius(8)
                    }
                    
                    if let sharedFrom = partner.sharedFrom {
                        Text("Partagé par: \(sharedFrom)")
                            .font(.caption)
                            .foregroundColor(.orange)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(12)
            } else {
                Text("Aucun partenaire connecté")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
    }
    
    private var testActionsSection: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("⚡️ Actions de Test")
                .font(.title2.bold())
                .foregroundColor(.orange)
            
            VStack(spacing: 10) {
                Button {
                    simulateSubscription()
                } label: {
                    Text("🔥 Simuler Abonnement")
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.blue)
                        .cornerRadius(10)
                }
                .disabled(isLoading)
                
                Button {
                    simulateUnsubscription()
                } label: {
                    Text("❌ Simuler Résiliation")
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.red)
                        .cornerRadius(10)
                }
                .disabled(isLoading)
                
                Button {
                    loadUserData()
                } label: {
                    Text("🔄 Actualiser")
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.gray)
                        .cornerRadius(10)
                }
                .disabled(isLoading)
                
                Button {
                    cleanupOrphanedCodes()
                } label: {
                    Text("🧹 Nettoyer codes orphelins")
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.orange)
                        .cornerRadius(10)
                }
                .disabled(isLoading)
            }
        }
    }
    
    private func loadUserData() {
        print("🔍 Debug: loadUserData - Début chargement")
        guard let currentUser = Auth.auth().currentUser else { 
            print("❌ Debug: loadUserData - Utilisateur non connecté")
            return 
        }
        
        print("🔍 Debug: loadUserData - Utilisateur: \(currentUser.uid)")
        isLoading = true
        
        Task {
            do {
                print("🔍 Debug: loadUserData - Chargement données utilisateur")
                // Charger les données utilisateur
                let userDoc = try await Firestore.firestore()
                    .collection("users")
                    .document(currentUser.uid)
                    .getDocument()
                
                if let userData = userDoc.data() {
                    print("🔍 Debug: loadUserData - Données utilisateur trouvées")
                    print("🔍 Debug: loadUserData - partnerId: \(userData["partnerId"] as? String ?? "nil")")
                    
                    let user = UserInfo(
                        id: currentUser.uid,
                        name: userData["name"] as? String ?? "Utilisateur",
                        isSubscribed: userData["isSubscribed"] as? Bool ?? false,
                        subscriptionType: userData["subscriptionType"] as? String,
                        sharedFrom: userData["subscriptionSharedFrom"] as? String
                    )
                    
                    await MainActor.run {
                        self.userInfo = user
                    }
                    
                    print("✅ Debug: loadUserData - Utilisateur mis à jour: \(user.name)")
                    
                    // 🔧 CORRECTION: Utiliser Cloud Function pour charger les données du partenaire
                    if let partnerId = userData["partnerId"] as? String, !partnerId.isEmpty {
                        print("🔍 Debug: loadUserData - Chargement partenaire via Cloud Function: \(partnerId)")
                        
                        do {
                            let functions = Functions.functions()
                            let result = try await functions.httpsCallable("getPartnerInfo").call([
                                "partnerId": partnerId
                            ])
                            
                            if let resultData = result.data as? [String: Any],
                               let success = resultData["success"] as? Bool,
                               success,
                               let partnerData = resultData["partnerInfo"] as? [String: Any] {
                                
                                print("✅ Debug: loadUserData - Données partenaire récupérées")
                                
                                let partner = UserInfo(
                                    id: partnerId,
                                    name: partnerData["name"] as? String ?? "Partenaire",
                                    isSubscribed: partnerData["isSubscribed"] as? Bool ?? false,
                                    subscriptionType: partnerData["subscriptionType"] as? String,
                                    sharedFrom: partnerData["subscriptionSharedFrom"] as? String
                                )
                                
                                print("✅ Debug: loadUserData - Partenaire: \(partner.name), Abonné: \(partner.isSubscribed)")
                                
                                await MainActor.run {
                                    self.partnerInfo = partner
                                }
                            } else {
                                print("❌ Debug: loadUserData - Échec récupération données partenaire")
                                await MainActor.run {
                                    self.partnerInfo = nil
                                }
                            }
                        } catch {
                            print("❌ Debug: loadUserData - Erreur Cloud Function: \(error)")
                            await MainActor.run {
                                self.partnerInfo = nil
                            }
                        }
                    } else {
                        print("🔍 Debug: loadUserData - Aucun partenaire connecté")
                        await MainActor.run {
                            self.partnerInfo = nil
                        }
                    }
                } else {
                    print("❌ Debug: loadUserData - Aucune donnée utilisateur trouvée")
                }
                
                await MainActor.run {
                    self.isLoading = false
                }
                
                print("✅ Debug: loadUserData - Chargement terminé")
                
            } catch {
                print("❌ Debug: Erreur chargement données: \(error)")
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
    
    private func simulateSubscription() {
        guard let currentUser = Auth.auth().currentUser else { return }
        
        isLoading = true
        
        Task {
            do {
                let updateData: [String: Any] = [
                    "isSubscribed": true,
                    "subscriptionType": "direct",
                    "subscriptionStartedAt": Timestamp(date: Date())
                ]
                
                try await Firestore.firestore()
                    .collection("users")
                    .document(currentUser.uid)
                    .updateData(updateData)
                
                await MainActor.run {
                    self.isLoading = false
                }
                
                // Recharger les données
                loadUserData()
                
            } catch {
                print("❌ Debug: Erreur simulation abonnement: \(error)")
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
    
    private func simulateUnsubscription() {
        guard let currentUser = Auth.auth().currentUser else { return }
        
        isLoading = true
        
        Task {
            do {
                let updateData: [String: Any] = [
                    "isSubscribed": false,
                    "subscriptionType": FieldValue.delete(),
                    "subscriptionExpiredAt": Timestamp(date: Date())
                ]
                
                try await Firestore.firestore()
                    .collection("users")
                    .document(currentUser.uid)
                    .updateData(updateData)
                
                await MainActor.run {
                    self.isLoading = false
                }
                
                // Recharger les données
                loadUserData()
                
            } catch {
                print("❌ Debug: Erreur simulation résiliation: \(error)")
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
    
    private func cleanupOrphanedCodes() {
        isLoading = true
        
        Task {
            do {
                // Appeler la fonction Cloud Firebase
                let functions = Functions.functions()
                let cleanupFunction = functions.httpsCallable("cleanupOrphanedPartnerCodes")
                
                print("🧹 Debug: Appel de la fonction de nettoyage...")
                let result = try await cleanupFunction.call()
                
                if let data = result.data as? [String: Any],
                   let message = data["message"] as? String {
                    print("✅ Debug: Nettoyage terminé - \(message)")
                } else {
                    print("✅ Debug: Nettoyage terminé")
                }
                
                await MainActor.run {
                    self.isLoading = false
                }
                
                // Recharger les données
                loadUserData()
                
            } catch {
                print("❌ Debug: Erreur nettoyage: \(error)")
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
}

#Preview {
    PartnerSubscriptionDebugView()
} 