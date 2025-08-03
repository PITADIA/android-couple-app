import Foundation
import CoreLocation
import CryptoKit

/// Service de chiffrement/déchiffrement de géolocalisation avec rétrocompatibilité
class LocationEncryptionService {
    
    // MARK: - Configuration
    
    static let ENCRYPTION_DISABLED_FOR_APPLE_REVIEW = true
    
    /// Version actuelle du service (pour la migration progressive)
    static let currentVersion = "2.0"
    
    /// Clé de chiffrement persistante dans le Keychain
    private static let symmetricKey: SymmetricKey = {
        let keychainKey = "love2love_location_encryption_key"
        
        // Tenter de récupérer la clé existante du Keychain
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "com.lyes.love2love.encryption",
            kSecAttrAccount as String: keychainKey,
            kSecReturnData as String: true
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        if status == errSecSuccess, let keyData = result as? Data {
            // Clé existante trouvée
            print("🔐 LocationEncryption: Clé existante récupérée du Keychain")
            return SymmetricKey(data: keyData)
        } else {
            // Générer une nouvelle clé et la sauvegarder
            let newKey = SymmetricKey(size: .bits256)
            let keyData = newKey.withUnsafeBytes { Data($0) }
            
            let addQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: "com.lyes.love2love.encryption",
                kSecAttrAccount as String: keychainKey,
                kSecValueData as String: keyData
            ]
            
            let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
            if addStatus == errSecSuccess {
                print("🔐 LocationEncryption: Nouvelle clé générée et sauvegardée dans Keychain")
            } else {
                print("❌ LocationEncryption: Erreur sauvegarde Keychain: \(addStatus)")
            }
            
            return newKey
        }
    }()
    
    // MARK: - Migration Progressive
    
    /// Structure hybride pour supporter les deux formats
    struct LocationData {
        let latitude: Double
        let longitude: Double
        let isEncrypted: Bool
        let version: String?
        
        /// Convertir vers CLLocation
        func toCLLocation() -> CLLocation {
            return CLLocation(latitude: latitude, longitude: longitude)
        }
    }
    
    // MARK: - Lecture Hybride (Backward Compatible)
    
    /// Lire une localisation depuis Firestore (supporte ancien ET nouveau format)
    static func readLocation(from firestoreData: [String: Any]) -> LocationData? {
        
        // 🆕 NOUVEAU FORMAT - Chiffré (v2.0+)
        if let encryptedLocation = firestoreData["encryptedLocation"] as? String,
           let version = firestoreData["locationVersion"] as? String {
            
            print("🔐 LocationEncryption: Lecture format chiffré v\(version)")
            
            if let decrypted = decryptLocation(encryptedLocation) {
                return LocationData(
                    latitude: decrypted.coordinate.latitude,
                    longitude: decrypted.coordinate.longitude,
                    isEncrypted: true,
                    version: version
                )
            }
        }
        
        // 🔄 ANCIEN FORMAT - Clair (v1.x - RÉTROCOMPATIBILITÉ)
        if let location = firestoreData["location"] as? [String: Any],
           let latitude = location["latitude"] as? Double,
           let longitude = location["longitude"] as? Double {
            
            print("📍 LocationEncryption: Lecture format legacy (non chiffré)")
            
            return LocationData(
                latitude: latitude,
                longitude: longitude,
                isEncrypted: false,
                version: "1.0"
            )
        }
        
        print("❌ LocationEncryption: Format de localisation non reconnu")
        return nil
    }
    
    // MARK: - Écriture Smart (Nouveau Format)
    
    /// Écrire une localisation vers Firestore (NOUVEAU format chiffré)
    static func writeLocation(_ location: CLLocation) -> [String: Any]? {
        if ENCRYPTION_DISABLED_FOR_APPLE_REVIEW {
            print("⚠️ LocationEncryption: Mode non-chiffré activé")
            return [
                // Format standard non chiffré
                "location": [
                    "latitude": location.coordinate.latitude,
                    "longitude": location.coordinate.longitude
                ],
                "hasLocation": true,
                "locationVersion": "1.0-temp",
                "migrationStatus": "unencrypted_temp",
                "clientVersion": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
            ]
        }
        
        guard let encryptedString = encryptLocation(location) else {
            print("❌ LocationEncryption: Échec du chiffrement")
            return nil
        }
        
        return [
            // 🆕 Nouveau format chiffré
            "encryptedLocation": encryptedString,
            "locationVersion": currentVersion,
            "hasLocation": true,
            "encryptedAt": Date(),
            
            // Rétrocompatibilité avec ancien format
            "location": [
                "latitude": location.coordinate.latitude,
                "longitude": location.coordinate.longitude
            ],
            
            // Métadonnées pour la migration
            "migrationStatus": "hybrid",
            "clientVersion": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        ]
    }
    
    // MARK: - Chiffrement/Déchiffrement
    
    /// Chiffrer une localisation
    private static func encryptLocation(_ location: CLLocation) -> String? {
        let locationString = "\(location.coordinate.latitude),\(location.coordinate.longitude)"
        let data = Data(locationString.utf8)
        
        do {
            let sealedBox = try AES.GCM.seal(data, using: symmetricKey)
            return sealedBox.combined?.base64EncodedString()
        } catch {
            print("❌ LocationEncryption: Erreur chiffrement - \(error)")
            return nil
        }
    }
    
    /// Déchiffrer une localisation
    private static func decryptLocation(_ encryptedString: String) -> CLLocation? {
        guard let data = Data(base64Encoded: encryptedString) else {
            print("❌ LocationEncryption: Données base64 invalides")
            return nil
        }
        
        do {
            let sealedBox = try AES.GCM.SealedBox(combined: data)
            let decryptedData = try AES.GCM.open(sealedBox, using: symmetricKey)
            
            guard let coordinatesString = String(data: decryptedData, encoding: .utf8) else {
                print("❌ LocationEncryption: Déchiffrement UTF8 échoué")
                return nil
            }
            
            let coordinates = coordinatesString.split(separator: ",")
            guard coordinates.count == 2,
                  let latitude = Double(coordinates[0]),
                  let longitude = Double(coordinates[1]) else {
                print("❌ LocationEncryption: Format coordonnées invalide")
                return nil
            }
            
            return CLLocation(latitude: latitude, longitude: longitude)
            
        } catch {
            print("❌ LocationEncryption: Erreur déchiffrement - \(error)")
            return nil
        }
    }
    
    // MARK: - Migration Tools
    
    /// Vérifier si une entrée doit être migrée
    static func needsMigration(_ firestoreData: [String: Any]) -> Bool {
        // Si on a seulement l'ancien format, on doit migrer
        return firestoreData["location"] != nil && firestoreData["encryptedLocation"] == nil
    }
    
    /// Migrer une entrée existante (à utiliser lors de la lecture/modification)
    static func migrateEntry(_ firestoreData: inout [String: Any]) {
        if let locationData = readLocation(from: firestoreData),
           !locationData.isEncrypted {
            
            let location = locationData.toCLLocation()
            if let newFormat = writeLocation(location) {
                // Support hybride ancien et nouveau format
                firestoreData.merge(newFormat) { (_, new) in new }
                firestoreData["migrationDate"] = Date()
                
                print("✅ LocationEncryption: Entrée migrée vers format chiffré")
            }
        }
    }
}

// MARK: - Chiffrement de Texte (Phase 2)

extension LocationEncryptionService {
    
    /// Chiffrer un texte (messages, réponses, métadonnées)
    static func encryptText(_ text: String) -> String? {
        if ENCRYPTION_DISABLED_FOR_APPLE_REVIEW {
            print("⚠️ LocationEncryption: Mode texte non-chiffré activé")
            return text // Retourner le texte en clair
        }
        
        let data = Data(text.utf8)
        
        do {
            let sealedBox = try AES.GCM.seal(data, using: symmetricKey)
            return sealedBox.combined?.base64EncodedString()
        } catch {
            print("❌ LocationEncryption: Erreur chiffrement texte - \(error)")
            return nil
        }
    }
    
    /// Déchiffrer un texte
    static func decryptText(_ encryptedString: String) -> String? {
        if ENCRYPTION_DISABLED_FOR_APPLE_REVIEW {
            print("⚠️ LocationEncryption: Mode déchiffrement non-chiffré activé")
            return encryptedString // Retourner le texte tel quel (déjà en clair)
        }
        
        guard let data = Data(base64Encoded: encryptedString) else {
            print("❌ LocationEncryption: Données base64 invalides pour texte")
            return nil
        }
        
        do {
            let sealedBox = try AES.GCM.SealedBox(combined: data)
            let decryptedData = try AES.GCM.open(sealedBox, using: symmetricKey)
            return String(data: decryptedData, encoding: .utf8)
        } catch {
            print("❌ LocationEncryption: Erreur déchiffrement texte - \(error)")
            return nil
        }
    }
    
    /// Traitement hybride des messages (nouveau format chiffré + ancien clair)
    static func processMessageForStorage(_ message: String) -> [String: Any] {
        var result: [String: Any] = [:]
        
        if ENCRYPTION_DISABLED_FOR_APPLE_REVIEW {
            print("⚠️ LocationEncryption: Mode stockage message non-chiffré activé")
            result["text"] = message
            result["textVersion"] = "1.0-temp"
            result["migrationStatus"] = "unencrypted_temp"
            return result
        }
        
        // Nouveau format chiffré
        if let encryptedMessage = encryptText(message) {
            result["encryptedText"] = encryptedMessage
            result["textVersion"] = currentVersion
        }
        
        // Format legacy pour rétrocompatibilité
        result["text_legacy"] = message
        result["migrationStatus"] = "hybrid_text"
        
        return result
    }
    
    /// Lecture hybride des messages (supporte ancien et nouveau format)
    static func readMessageFromFirestore(_ data: [String: Any]) -> String? {
        // Essayer d'abord le nouveau format chiffré
        if let encryptedText = data["encryptedText"] as? String {
            if let decryptedMessage = decryptText(encryptedText) {
                print("✅ LocationEncryption: Message déchiffré avec succès")
                return decryptedMessage
            }
        }
        
        // Fallback vers l'ancien format
        if let legacyText = data["text_legacy"] as? String {
            print("📄 LocationEncryption: Lecture format legacy pour message")
            return legacyText
        }
        
        // Compatibilité avec anciens champs
        if let oldText = data["text"] as? String {
            print("📄 LocationEncryption: Lecture ancien format message")
            return oldText
        }
        
        return nil
    }
}

// MARK: - Extensions pour faciliter l'utilisation

extension LocationEncryptionService {
    
    /// Méthode helper pour JournalService
    static func processLocationForStorage(_ location: CLLocation) -> [String: Any] {
        return writeLocation(location) ?? [:]
    }
    
    /// Méthode helper pour lire depuis JournalEntry
    static func extractLocation(from journalData: [String: Any]) -> CLLocation? {
        return readLocation(from: journalData)?.toCLLocation()
    }
}