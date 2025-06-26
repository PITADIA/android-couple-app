import Foundation
import SwiftUI
import Combine

class PartnerConnectionNotificationService: ObservableObject {
    static let shared = PartnerConnectionNotificationService()
    
    @Published var shouldShowConnectionSuccess = false
    @Published var connectedPartnerName = ""
    
    private var cancellables = Set<AnyCancellable>()
    
    private init() {
        setupObservers()
    }
    
    private func setupObservers() {
        // Observer les notifications de connexion réussie
        NotificationCenter.default.publisher(for: .partnerConnectionSuccess)
            .sink { [weak self] notification in
                if let userInfo = notification.userInfo,
                   let partnerName = userInfo["partnerName"] as? String {
                    DispatchQueue.main.async {
                        self?.showConnectionSuccess(partnerName: partnerName)
                    }
                }
            }
            .store(in: &cancellables)
        
        // Observer les demandes d'affichage de message en attente
        NotificationCenter.default.publisher(for: .shouldShowConnectionSuccess)
            .sink { [weak self] notification in
                if let userInfo = notification.userInfo,
                   let partnerName = userInfo["partnerName"] as? String {
                    DispatchQueue.main.async {
                        self?.showConnectionSuccess(partnerName: partnerName)
                    }
                }
            }
            .store(in: &cancellables)
    }
    
    func showConnectionSuccess(partnerName: String) {
        print("🎉 PartnerConnectionNotificationService: Affichage message pour: \(partnerName)")
        connectedPartnerName = partnerName
        shouldShowConnectionSuccess = true
    }
    
    func dismissConnectionSuccess() {
        print("🎉 PartnerConnectionNotificationService: Fermeture message")
        shouldShowConnectionSuccess = false
        connectedPartnerName = ""
    }
} 