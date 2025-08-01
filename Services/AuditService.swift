import Foundation
import FirebaseFirestore
import FirebaseAuth
import CryptoKit

/// Service d'audit et de logs sécurisés pour traçabilité
class AuditService {
    
    static let shared = AuditService()
    private let db = Firestore.firestore()
    
    private init() {}
    
    // MARK: - Types d'événements
    
    enum AuditEventType: String, CaseIterable {
        case partnerConnection = "partner_connection"
        case partnerDisconnection = "partner_disconnection"
        case accessAttempt = "access_attempt"
        case accessDenied = "access_denied"
        case dataEncryption = "data_encryption"
        case dataDecryption = "data_decryption"
        case loginSuccess = "login_success"
        case loginFailure = "login_failure"
        case sensitiveDataAccess = "sensitive_data_access"
        case securityViolation = "security_violation"
    }
    
    // MARK: - Structure des événements
    
    struct AuditEvent {
        let type: AuditEventType
        let userId: String?
        let details: [String: Any]
        let timestamp: Date
        let ipAddress: String?
        let userAgent: String?
        let severity: Severity
        
        enum Severity: String {
            case low = "low"
            case medium = "medium"
            case high = "high"
            case critical = "critical"
        }
    }
    
    // MARK: - Logging des événements
    
    /// Logger un événement d'audit
    func logEvent(_ event: AuditEvent) {
        // Log local pour debug
        print("🔍 AuditService: \(event.type.rawValue) - Severity: \(event.severity.rawValue)")
        
        // Préparer les données pour Firestore
        var eventData: [String: Any] = [
            "type": event.type.rawValue,
            "timestamp": Timestamp(date: event.timestamp),
            "severity": event.severity.rawValue,
            "details": event.details
        ]
        
        // Ajouter userId de manière sécurisée
        if let userId = event.userId {
            // En production, hasher l'ID pour la confidentialité
            eventData["userId"] = hashUserId(userId)
            eventData["hashedUser"] = true
        }
        
        // Ajouter métadonnées techniques si disponibles
        if let ipAddress = event.ipAddress {
            eventData["ipAddress"] = ipAddress
        }
        if let userAgent = event.userAgent {
            eventData["userAgent"] = userAgent
        }
        
        // Sauvegarder dans Firestore
        db.collection("audit_events").addDocument(data: eventData) { error in
            if let error = error {
                print("❌ AuditService: Erreur sauvegarde audit - \(error)")
            } else {
                print("✅ AuditService: Événement d'audit sauvegardé")
            }
        }
    }
    
    // MARK: - Méthodes spécialisées
    
    /// Logger une connexion partenaire
    func logPartnerConnection(userId: String, partnerId: String, method: String) {
        let event = AuditEvent(
            type: .partnerConnection,
            userId: userId,
            details: [
                "partnerId": hashUserId(partnerId),
                "connectionMethod": method,
                "action": "successful_connection"
            ],
            timestamp: Date(),
            ipAddress: nil,
            userAgent: nil,
            severity: .medium
        )
        logEvent(event)
    }
    
    /// Logger une déconnexion partenaire
    func logPartnerDisconnection(userId: String, partnerId: String, reason: String) {
        let event = AuditEvent(
            type: .partnerDisconnection,
            userId: userId,
            details: [
                "partnerId": hashUserId(partnerId),
                "disconnectionReason": reason,
                "action": "partner_disconnected"
            ],
            timestamp: Date(),
            ipAddress: nil,
            userAgent: nil,
            severity: .medium
        )
        logEvent(event)
    }
    
    /// Logger un accès refusé
    func logAccessDenied(userId: String?, resource: String, reason: String) {
        let event = AuditEvent(
            type: .accessDenied,
            userId: userId,
            details: [
                "resource": resource,
                "denialReason": reason,
                "action": "access_blocked"
            ],
            timestamp: Date(),
            ipAddress: nil,
            userAgent: nil,
            severity: .high
        )
        logEvent(event)
    }
    
    /// Logger un accès aux données sensibles
    func logSensitiveDataAccess(userId: String, dataType: String, action: String) {
        let event = AuditEvent(
            type: .sensitiveDataAccess,
            userId: userId,
            details: [
                "dataType": dataType,
                "accessAction": action,
                "timestamp": ISO8601DateFormatter().string(from: Date())
            ],
            timestamp: Date(),
            ipAddress: nil,
            userAgent: nil,
            severity: .medium
        )
        logEvent(event)
    }
    
    /// Logger une violation de sécurité
    func logSecurityViolation(userId: String?, violationType: String, details: [String: Any]) {
        let event = AuditEvent(
            type: .securityViolation,
            userId: userId,
            details: details.merging([
                "violationType": violationType,
                "severity": "critical"
            ]) { (_, new) in new },
            timestamp: Date(),
            ipAddress: nil,
            userAgent: nil,
            severity: .critical
        )
        logEvent(event)
    }
    
    // MARK: - Utilitaires de sécurité
    
    /// Hasher un userId pour la confidentialité
    private func hashUserId(_ userId: String) -> String {
        // Utiliser SHA-256 pour hasher l'ID utilisateur
        let data = Data(userId.utf8)
        let hashed = SHA256.hash(data: data)
        return hashed.compactMap { String(format: "%02x", $0) }.joined()
    }
    
    /// Obtenir le userId actuel de manière sécurisée
    private func getCurrentUserId() -> String? {
        return Auth.auth().currentUser?.uid
    }
    
    // MARK: - Monitoring et alertes
    
    /// Vérifier les événements suspects récents
    func checkSuspiciousActivity(for userId: String, completion: @escaping (Bool) -> Void) {
        let oneDayAgo = Date().addingTimeInterval(-24 * 60 * 60)
        
        db.collection("audit_events")
            .whereField("userId", isEqualTo: hashUserId(userId))
            .whereField("severity", isEqualTo: "high")
            .whereField("timestamp", isGreaterThan: Timestamp(date: oneDayAgo))
            .getDocuments { snapshot, error in
                if let error = error {
                    print("❌ AuditService: Erreur vérification activité - \(error)")
                    completion(false)
                    return
                }
                
                let suspiciousCount = snapshot?.documents.count ?? 0
                let isSuspicious = suspiciousCount > 5 // Plus de 5 événements à risque en 24h
                
                if isSuspicious {
                    print("⚠️ AuditService: Activité suspecte détectée pour utilisateur")
                }
                
                completion(isSuspicious)
            }
    }
}

// MARK: - Extensions pour faciliter l'utilisation

extension AuditService {
    
    /// Logger rapidement un accès aux données de géolocalisation
    func logLocationAccess(action: String) {
        guard let userId = getCurrentUserId() else { return }
        logSensitiveDataAccess(userId: userId, dataType: "geolocation", action: action)
    }
    
    /// Logger rapidement un accès aux photos
    func logPhotoAccess(action: String) {
        guard let userId = getCurrentUserId() else { return }
        logSensitiveDataAccess(userId: userId, dataType: "profile_photo", action: action)
    }
    
    /// Logger rapidement un chiffrement/déchiffrement
    func logEncryptionActivity(type: String, success: Bool) {
        guard let userId = getCurrentUserId() else { return }
        let eventType: AuditEventType = type.contains("encrypt") ? .dataEncryption : .dataDecryption
        let severity: AuditEvent.Severity = success ? .low : .medium
        
        let event = AuditEvent(
            type: eventType,
            userId: userId,
            details: [
                "encryptionType": type,
                "success": success,
                "timestamp": ISO8601DateFormatter().string(from: Date())
            ],
            timestamp: Date(),
            ipAddress: nil,
            userAgent: nil,
            severity: severity
        )
        logEvent(event)
    }
}