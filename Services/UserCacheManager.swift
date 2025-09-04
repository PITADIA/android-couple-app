import Foundation
import UIKit

class UserCacheManager {
    static let shared = UserCacheManager()
    
    private let userDefaults = UserDefaults.standard
    private let cacheKey = "cached_user_data"
    private let cacheTimestampKey = "cached_user_timestamp"
    private let profileImageCacheKey = "cached_profile_image"
    private let partnerImageCacheKey = "cached_partner_image"
    private let partnerImageURLKey = "cached_partner_image_url"
    
    private init() {}
    
    // MARK: - Cache User Data
    
    /// Cache l'utilisateur après chargement Firebase
    func cacheUser(_ user: AppUser) {
        print("💾 UserCacheManager: Mise en cache utilisateur: \(user.name)")
        
        do {
            let encoder = JSONEncoder()
            let data = try encoder.encode(user)
            userDefaults.set(data, forKey: cacheKey)
            userDefaults.set(Date().timeIntervalSince1970, forKey: cacheTimestampKey)
            
            print("✅ UserCacheManager: Utilisateur mis en cache avec succès")
        } catch {
            print("❌ UserCacheManager: Erreur encodage utilisateur: \(error)")
        }
    }
    
    /// Récupère l'utilisateur en cache (instantané)
    func getCachedUser() -> AppUser? {
        guard let data = userDefaults.data(forKey: cacheKey) else {
            print("📂 UserCacheManager: Aucun utilisateur en cache")
            return nil
        }
        
        // Vérifier l'âge du cache (optionnel - pour éviter des données trop anciennes)
        if let timestamp = userDefaults.object(forKey: cacheTimestampKey) as? TimeInterval {
            let age = Date().timeIntervalSince1970 - timestamp
            let maxAge: TimeInterval = 7 * 24 * 3600 // 7 jours
            
            if age > maxAge {
                print("⏰ UserCacheManager: Cache expiré (\(Int(age/3600))h), nettoyage")
                clearCache()
                return nil
            }
        }
        
        do {
            let decoder = JSONDecoder()
            let user = try decoder.decode(AppUser.self, from: data)
            print("🚀 UserCacheManager: Utilisateur trouvé en cache: \(user.name)")
            return user
        } catch {
            print("❌ UserCacheManager: Erreur décodage cache: \(error)")
            // Nettoyer le cache corrompu
            clearCache()
            return nil
        }
    }
    
    /// Vérifie si un utilisateur est en cache
    func hasCachedUser() -> Bool {
        return userDefaults.data(forKey: cacheKey) != nil
    }
    
    /// Nettoie le cache (déconnexion, suppression compte, etc.)
    func clearCache() {
        print("🗑️ UserCacheManager: Nettoyage cache utilisateur")
        userDefaults.removeObject(forKey: cacheKey)
        userDefaults.removeObject(forKey: cacheTimestampKey)
        // Nettoyer aussi les images en cache
        clearCachedProfileImage()
        clearCachedPartnerImage()
    }
    
    // MARK: - Cache Profile Image
    
    /// Cache l'image de profil physiquement pour affichage instantané
    func cacheProfileImage(_ image: UIImage) {
        print("🖼️ UserCacheManager: Mise en cache image de profil")
        
        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            print("❌ UserCacheManager: Impossible de convertir l'image en données")
            return
        }
        
        userDefaults.set(imageData, forKey: profileImageCacheKey)
        print("✅ UserCacheManager: Image de profil mise en cache (\(imageData.count) bytes)")
    }
    
    // MARK: - Simplification: Plus besoin du système "pending"
    // Le cache local est maintenant la source de vérité pour l'affichage
    // Firebase se synchronise en arrière-plan sans affecter l'UI
    
    /// Récupère l'image de profil en cache (instantané)
    func getCachedProfileImage() -> UIImage? {
        guard let imageData = userDefaults.data(forKey: profileImageCacheKey) else {
            print("🖼️ UserCacheManager: Aucune image de profil en cache")
            return nil
        }
        
        guard let image = UIImage(data: imageData) else {
            print("❌ UserCacheManager: Impossible de charger l'image depuis le cache")
            // Nettoyer les données corrompues
            userDefaults.removeObject(forKey: profileImageCacheKey)
            return nil
        }
        
        print("✅ UserCacheManager: Image de profil trouvée en cache")
        return image
    }
    
    /// Vérifie si une image de profil est en cache
    func hasCachedProfileImage() -> Bool {
        return userDefaults.data(forKey: profileImageCacheKey) != nil
    }
    
    /// Nettoie seulement l'image de profil en cache
    func clearCachedProfileImage() {
        print("🗑️ UserCacheManager: Nettoyage image de profil en cache")
        userDefaults.removeObject(forKey: profileImageCacheKey)
    }
    
    // MARK: - Cache Partner Image
    
    /// Cache l'image de profil du partenaire avec son URL pour comparaison future
    func cachePartnerImage(_ image: UIImage, url: String) {
        print("🤝 UserCacheManager: Mise en cache image partenaire")
        
        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            print("❌ UserCacheManager: Impossible de convertir l'image partenaire en données")
            return
        }
        
        userDefaults.set(imageData, forKey: partnerImageCacheKey)
        userDefaults.set(url, forKey: partnerImageURLKey)
        // Log sécurisé sans exposer l'URL avec token
        print("✅ UserCacheManager: Image partenaire mise en cache (\(imageData.count) bytes)")
    }
    
    /// Récupère l'image du partenaire en cache
    func getCachedPartnerImage() -> UIImage? {
        guard let imageData = userDefaults.data(forKey: partnerImageCacheKey) else {
            print("🤝 UserCacheManager: Aucune image partenaire en cache")
            return nil
        }
        
        guard let image = UIImage(data: imageData) else {
            print("❌ UserCacheManager: Impossible de charger l'image partenaire depuis le cache")
            clearCachedPartnerImage()
            return nil
        }
        
        print("✅ UserCacheManager: Image partenaire trouvée en cache")
        return image
    }
    
    /// Vérifie si l'URL du partenaire a changé (pour savoir si on doit recharger)
    func hasPartnerImageChanged(newURL: String?) -> Bool {
        let cachedURL = userDefaults.string(forKey: partnerImageURLKey)
        let hasChanged = cachedURL != newURL
        
        if hasChanged {
            // Log sécurisé sans exposer les URLs avec tokens
            print("🔄 UserCacheManager: URL partenaire changée")
        } else {
            // Log sécurisé sans exposer les URLs avec tokens
            print("✅ UserCacheManager: URL partenaire inchangée")
        }
        
        return hasChanged
    }
    
    /// Vérifie si une image partenaire est en cache
    func hasCachedPartnerImage() -> Bool {
        return userDefaults.data(forKey: partnerImageCacheKey) != nil
    }
    
    /// Nettoie l'image partenaire en cache
    func clearCachedPartnerImage() {
        print("🗑️ UserCacheManager: Nettoyage image partenaire en cache")
        userDefaults.removeObject(forKey: partnerImageCacheKey)
        userDefaults.removeObject(forKey: partnerImageURLKey)
    }
    
    /// Met à jour seulement certains champs sans remplacer tout le cache
    func updateCachedUserField<T: Codable>(_ keyPath: WritableKeyPath<AppUser, T>, value: T) {
        guard var cachedUser = getCachedUser() else {
            print("⚠️ UserCacheManager: Impossible de mettre à jour - pas d'utilisateur en cache")
            return
        }
        
        cachedUser[keyPath: keyPath] = value
        cacheUser(cachedUser)
    }
    
    // MARK: - Cache Info & Recovery
    
    func getCacheInfo() -> (hasCache: Bool, lastUpdated: Date?, userCount: Int) {
        let hasCache = hasCachedUser()
        var lastUpdated: Date?
        
        if let timestamp = userDefaults.object(forKey: cacheTimestampKey) as? TimeInterval {
            lastUpdated = Date(timeIntervalSince1970: timestamp)
        }
        
        return (hasCache: hasCache, lastUpdated: lastUpdated, userCount: hasCache ? 1 : 0)
    }
    
    // MARK: - Plus besoin de détection d'incohérence
    // Avec la nouvelle approche, le cache local est toujours la source de vérité
    // Firebase se synchronise en arrière-plan sans impact sur l'expérience utilisateur
}