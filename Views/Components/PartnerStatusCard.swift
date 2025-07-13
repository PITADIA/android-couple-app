import SwiftUI
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions

struct PartnerStatusCard: View {
    @State private var currentUserInfo: UserSubscriptionInfo?
    @State private var partnerInfo: UserSubscriptionInfo?
    @State private var isLoading = true
    @State private var hasPartner = false
    
    struct UserSubscriptionInfo {
        let id: String
        let name: String
        let isSubscribed: Bool
        let subscriptionType: String?
        let sharedFrom: String?
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("👥 Statut Partenaires")
                    .font(.headline.bold())
                    .foregroundColor(.primary)
                
                Spacer()
                
                if isLoading {
                    ProgressView()
                        .scaleEffect(0.7)
                }
            }
            
            if hasPartner {
                // Affichage des deux partenaires
                HStack(spacing: 15) {
                    // Utilisateur actuel
                    UserStatusView(
                        title: "Vous",
                        userInfo: currentUserInfo,
                        isCurrentUser: true
                    )
                    
                    // Flèche de connexion
                    VStack {
                        Image(systemName: "arrow.left.arrow.right")
                            .foregroundColor(.purple)
                            .font(.title2)
                    }
                    .frame(width: 30)
                    
                    // Partenaire
                    UserStatusView(
                        title: "Partenaire",
                        userInfo: partnerInfo,
                        isCurrentUser: false
                    )
                }
                
                // Résumé de l'accès
                accessSummaryView
                
                // Actions rapides de simulation
                quickActionsView
                
            } else {
                // Pas de partenaire connecté
                VStack(spacing: 8) {
                    HStack {
                        Image(systemName: "person.slash")
                            .foregroundColor(.gray)
                        Text("Aucun partenaire connecté")
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    
                    if let user = currentUserInfo {
                        HStack {
                            Circle()
                                .fill(user.isSubscribed ? .green : .red)
                                .frame(width: 8, height: 8)
                            Text(user.isSubscribed ? "Premium (Solo)" : "Gratuit")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Spacer()
                        }
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(10)
            }
        }
        .padding()
        .background(Color.white.opacity(0.05))
        .cornerRadius(15)
        .onAppear {
            loadData()
        }
        .onReceive(NotificationCenter.default.publisher(for: .subscriptionUpdated)) { _ in
            loadData()
        }
        .onReceive(NotificationCenter.default.publisher(for: .partnerConnected)) { _ in
            loadData()
        }
        .onReceive(NotificationCenter.default.publisher(for: .partnerDisconnected)) { _ in
            loadData()
        }
    }
    
    private var accessSummaryView: some View {
        VStack(alignment: .leading, spacing: 5) {
            Divider()
                .background(Color.white.opacity(0.3))
            
            HStack {
                Text("🎯 Accès Premium:")
                    .font(.caption.bold())
                    .foregroundColor(.secondary)
                
                Spacer()
                
                let bothHaveAccess = (currentUserInfo?.isSubscribed ?? false) && (partnerInfo?.isSubscribed ?? false)
                
                if bothHaveAccess {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.caption)
                        Text("Les deux ont accès")
                            .font(.caption.bold())
                            .foregroundColor(.green)
                    }
                } else if currentUserInfo?.isSubscribed == true || partnerInfo?.isSubscribed == true {
                    HStack(spacing: 4) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.orange)
                            .font(.caption)
                        Text("Synchronisation en cours...")
                            .font(.caption.bold())
                            .foregroundColor(.orange)
                    }
                } else {
                    HStack(spacing: 4) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.red)
                            .font(.caption)
                        Text("Aucun accès premium")
                            .font(.caption.bold())
                            .foregroundColor(.red)
                    }
                }
            }
        }
    }
    
    private var quickActionsView: some View {
        VStack(alignment: .leading, spacing: 8) {
            Divider()
                .background(Color.white.opacity(0.3))
            
            Text("⚡️ Actions Rapides")
                .font(.caption.bold())
                .foregroundColor(.secondary)
            
            HStack(spacing: 10) {
                // Bouton s'abonner
                Button {
                    simulateSubscription()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "plus.circle.fill")
                            .font(.caption)
                        Text("S'abonner")
                            .font(.caption.bold())
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.blue)
                    .cornerRadius(8)
                }
                .disabled(isLoading || (currentUserInfo?.isSubscribed == true))
                
                // Bouton résilier
                Button {
                    simulateUnsubscription()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "minus.circle.fill")
                            .font(.caption)
                        Text("Résilier")
                            .font(.caption.bold())
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.red)
                    .cornerRadius(8)
                }
                .disabled(isLoading || (currentUserInfo?.isSubscribed != true))
                
                Spacer()
                
                // Bouton actualiser
                Button {
                    loadData()
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .font(.caption)
                        .foregroundColor(.white)
                        .padding(6)
                        .background(Color.gray)
                        .cornerRadius(6)
                }
                .disabled(isLoading)
            }
        }
    }
    
    private func loadData() {
        guard let currentUser = Auth.auth().currentUser else {
            isLoading = false
            return
        }
        
        isLoading = true
        
        Task {
            do {
                // Charger les données utilisateur
                let userDoc = try await Firestore.firestore()
                    .collection("users")
                    .document(currentUser.uid)
                    .getDocument()
                
                if let userData = userDoc.data() {
                    let user = UserSubscriptionInfo(
                        id: currentUser.uid,
                        name: userData["name"] as? String ?? "Vous",
                        isSubscribed: userData["isSubscribed"] as? Bool ?? false,
                        subscriptionType: userData["subscriptionType"] as? String,
                        sharedFrom: userData["subscriptionSharedFrom"] as? String
                    )
                    
                    await MainActor.run {
                        self.currentUserInfo = user
                    }
                    
                    // Vérifier s'il y a un partenaire
                    if let partnerId = userData["partnerId"] as? String, !partnerId.isEmpty {
                        // 🔧 CORRECTION: Utiliser Cloud Function pour récupérer info partenaire
                        do {
                            let functions = Functions.functions()
                            let result = try await functions.httpsCallable("getPartnerInfo").call([
                                "partnerId": partnerId
                            ])
                            
                            if let resultData = result.data as? [String: Any],
                               let success = resultData["success"] as? Bool,
                               success,
                               let partnerData = resultData["partnerInfo"] as? [String: Any] {
                                
                                let partner = UserSubscriptionInfo(
                                    id: partnerId,
                                    name: partnerData["name"] as? String ?? "Partenaire",
                                    isSubscribed: partnerData["isSubscribed"] as? Bool ?? false,
                                    subscriptionType: partnerData["subscriptionType"] as? String,
                                    sharedFrom: partnerData["subscriptionSharedFrom"] as? String
                                )
                                
                                await MainActor.run {
                                    self.partnerInfo = partner
                                    self.hasPartner = true
                                    self.isLoading = false
                                }
                            } else {
                                await MainActor.run {
                                    self.hasPartner = false
                                    self.isLoading = false
                                }
                            }
                        } catch {
                            print("❌ PartnerStatusCard: Erreur récupération info partenaire: \(error)")
                            await MainActor.run {
                                self.hasPartner = false
                                self.isLoading = false
                            }
                        }
                    } else {
                        await MainActor.run {
                            self.hasPartner = false
                            self.partnerInfo = nil
                            self.isLoading = false
                        }
                    }
                } else {
                    await MainActor.run {
                        self.isLoading = false
                    }
                }
                
            } catch {
                print("❌ PartnerStatusCard: Erreur chargement: \(error)")
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
                
                // Recharger les données après 1 seconde pour voir la synchronisation
                try await Task.sleep(nanoseconds: 1_000_000_000)
                loadData()
                
            } catch {
                print("❌ PartnerStatusCard: Erreur simulation abonnement: \(error)")
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
                
                // Recharger les données après 1 seconde pour voir la synchronisation
                try await Task.sleep(nanoseconds: 1_000_000_000)
                loadData()
                
            } catch {
                print("❌ PartnerStatusCard: Erreur simulation résiliation: \(error)")
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
}

struct UserStatusView: View {
    let title: String
    let userInfo: PartnerStatusCard.UserSubscriptionInfo?
    let isCurrentUser: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.bold())
                .foregroundColor(.secondary)
            
            if let user = userInfo {
                VStack(alignment: .leading, spacing: 4) {
                    Text(user.name)
                        .font(.subheadline.bold())
                        .lineLimit(1)
                    
                    HStack(spacing: 4) {
                        Circle()
                            .fill(user.isSubscribed ? .green : .red)
                            .frame(width: 8, height: 8)
                        
                        Text(user.isSubscribed ? "Premium" : "Gratuit")
                            .font(.caption)
                            .foregroundColor(user.isSubscribed ? .green : .red)
                    }
                    
                    if let type = user.subscriptionType {
                        Text(typeDisplayName(type))
                            .font(.system(size: 10))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(typeColor(type).opacity(0.2))
                            .foregroundColor(typeColor(type))
                            .cornerRadius(4)
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    Text("...")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    
                    HStack(spacing: 4) {
                        Circle()
                            .fill(.gray)
                            .frame(width: 8, height: 8)
                        Text("Chargement...")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(10)
    }
    
    private func typeDisplayName(_ type: String) -> String {
        switch type {
        case "direct":
            return "Payé"
        case "shared_from_partner":
            return "Partagé"
        case "inherited":
            return "Hérité"
        default:
            return type
        }
    }
    
    private func typeColor(_ type: String) -> Color {
        switch type {
        case "direct":
            return .blue
        case "shared_from_partner":
            return .purple
        case "inherited":
            return .orange
        default:
            return .gray
        }
    }
}

#Preview {
    PartnerStatusCard()
        .padding()
        .background(Color.black)
} 