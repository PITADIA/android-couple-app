import UIKit
import Foundation

class ImageCacheService {
    static let shared = ImageCacheService()
    
    private let memoryCache = NSCache<NSString, UIImage>()
    private let fileManager = FileManager.default
    private let cacheDirectory: URL
    
    private init() {
        // Utiliser le dossier App Group pour partager avec les widgets
        if let containerURL = fileManager.containerURL(forSecurityApplicationGroupIdentifier: "group.com.lyes.love2love") {
            cacheDirectory = containerURL.appendingPathComponent("ImageCache")
        } else {
            // Fallback vers le cache Documents de l'app
            cacheDirectory = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("ImageCache")
        }
        
        // Créer le dossier de cache s'il n'existe pas
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        
        // Configuration du cache mémoire
        memoryCache.countLimit = 50 // Maximum 50 images en mémoire
        memoryCache.totalCostLimit = 100 * 1024 * 1024 // Maximum 100MB en mémoire
        
        print("🖼️ ImageCacheService: Cache initialisé - Dossier: \(cacheDirectory.path)")
    }
    
    // MARK: - Public Methods
    
    func getCachedImage(for urlString: String) -> UIImage? {
        let cacheKey = cacheKeyForURL(urlString)
        
        // 1. Vérifier le cache mémoire d'abord (plus rapide)
        if let memoryImage = memoryCache.object(forKey: cacheKey as NSString) {
            // Log sécurisé sans exposer l'URL complète avec token
            print("🖼️ ImageCacheService: Image trouvée en cache mémoire")
            return memoryImage
        }
        
        // 2. Vérifier le cache disque
        if let diskImage = loadImageFromDisk(cacheKey: cacheKey) {
            // Log sécurisé sans exposer l'URL complète avec token
            print("🖼️ ImageCacheService: Image trouvée en cache disque")
            // Remettre en cache mémoire
            memoryCache.setObject(diskImage, forKey: cacheKey as NSString)
            return diskImage
        }
        
        // Log sécurisé sans exposer l'URL complète avec token
        print("🖼️ ImageCacheService: Aucune image en cache")
        return nil
    }
    
    func cacheImage(_ image: UIImage, for urlString: String) {
        let cacheKey = cacheKeyForURL(urlString)
        
        // 1. Mettre en cache mémoire
        memoryCache.setObject(image, forKey: cacheKey as NSString)
        
        // 2. Mettre en cache disque de façon asynchrone
        Task.detached { [weak self] in
            self?.saveImageToDisk(image, cacheKey: cacheKey)
        }
        
        // Log sécurisé sans exposer l'URL complète avec token
        print("🖼️ ImageCacheService: Image mise en cache")
    }
    
    func clearCachedImage(for urlString: String) {
        let cacheKey = cacheKeyForURL(urlString)
        
        // 1. Supprimer du cache mémoire
        memoryCache.removeObject(forKey: cacheKey as NSString)
        
        // 2. Supprimer du cache disque
        Task.detached { [weak self] in
            guard let self = self else { return }
            let fileURL = self.cacheDirectory.appendingPathComponent("\(cacheKey).jpg")
            try? self.fileManager.removeItem(at: fileURL)
        }
        
        // Log sécurisé sans exposer l'URL complète avec token
        print("🗑️ ImageCacheService: Image supprimée du cache")
    }
    
    func clearCache() {
        // Vider le cache mémoire
        memoryCache.removeAllObjects()
        
        // Vider le cache disque
        Task.detached { [weak self] in
            guard let self = self else { return }
            try? self.fileManager.removeItem(at: self.cacheDirectory)
            try? self.fileManager.createDirectory(at: self.cacheDirectory, withIntermediateDirectories: true)
        }
        
        print("🖼️ ImageCacheService: Cache vidé")
    }
    
    func getCacheSize() -> (memorySize: String, diskSize: String) {
        let memorySize = "~\(memoryCache.totalCostLimit / 1024 / 1024)MB"
        
        var diskSize = "0MB"
        if let enumerator = fileManager.enumerator(at: cacheDirectory, includingPropertiesForKeys: [.fileSizeKey]) {
            var totalSize: Int64 = 0
            for case let fileURL as URL in enumerator {
                if let resourceValues = try? fileURL.resourceValues(forKeys: [.fileSizeKey]),
                   let fileSize = resourceValues.fileSize {
                    totalSize += Int64(fileSize)
                }
            }
            diskSize = ByteCountFormatter.string(fromByteCount: totalSize, countStyle: .file)
        }
        
        return (memorySize, diskSize)
    }
    
    // MARK: - Private Methods
    
    private func cacheKeyForURL(_ urlString: String) -> String {
        // Créer une clé unique basée sur l'URL
        let url = URL(string: urlString)
        if let path = url?.path, let query = url?.query {
            return "\(path.replacingOccurrences(of: "/", with: "_"))_\(query.hash)".replacingOccurrences(of: "=", with: "_")
        } else if let path = url?.path {
            return path.replacingOccurrences(of: "/", with: "_")
        } else {
            return urlString.hash.description
        }
    }
    
    private func loadImageFromDisk(cacheKey: String) -> UIImage? {
        let fileURL = cacheDirectory.appendingPathComponent("\(cacheKey).jpg")
        
        guard fileManager.fileExists(atPath: fileURL.path),
              let imageData = try? Data(contentsOf: fileURL),
              let image = UIImage(data: imageData) else {
            return nil
        }
        
        return image
    }
    
    private func saveImageToDisk(_ image: UIImage, cacheKey: String) {
        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            print("❌ ImageCacheService: Impossible de convertir l'image en données")
            return
        }
        
        let fileURL = cacheDirectory.appendingPathComponent("\(cacheKey).jpg")
        
        do {
            try imageData.write(to: fileURL)
            // Log sécurisé sans exposer le nom de fichier avec UID
            print("🖼️ ImageCacheService: Image sauvée sur disque")
        } catch {
            print("❌ ImageCacheService: Erreur sauvegarde disque: \(error)")
        }
    }
    
    // MARK: - Widget Compatibility
    
    /// Méthode compatible avec WidgetService pour unifier les caches
    func cacheImageForWidget(_ image: UIImage, fileName: String) {
        print("🖼️ ImageCacheService: cacheImageForWidget appelé")
        print("  - FileName: \(fileName)")
        print("  - CacheDirectory: \(cacheDirectory.path)")
        
        let fileURL = cacheDirectory.appendingPathComponent(fileName)
        print("  - FileURL: \(fileURL.path)")
        
        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            print("❌ ImageCacheService: Impossible de convertir l'image en JPEG")
            return
        }
        
        print("  - ImageData size: \(imageData.count) bytes")
        
        do {
            try imageData.write(to: fileURL)
            print("✅ ImageCacheService: Image widget sauvée: \(fileName)")
            
            // Vérifier que le fichier existe bien
            if FileManager.default.fileExists(atPath: fileURL.path) {
                print("✅ ImageCacheService: Vérification - Fichier existe: \(fileURL.path)")
                
                // Vérifier la taille du fichier
                if let attributes = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
                   let fileSize = attributes[.size] as? NSNumber {
                    print("✅ ImageCacheService: Taille fichier: \(fileSize.intValue) bytes")
                }
            } else {
                print("❌ ImageCacheService: Vérification - Fichier n'existe pas après sauvegarde!")
            }
        } catch {
            print("❌ ImageCacheService: Erreur sauvegarde widget: \(error)")
            print("❌ ImageCacheService: Error details: \(error.localizedDescription)")
        }
    }
    
    func getCachedWidgetImage(fileName: String) -> UIImage? {
        let fileURL = cacheDirectory.appendingPathComponent(fileName)
        
        guard let imageData = try? Data(contentsOf: fileURL),
              let image = UIImage(data: imageData) else {
            return nil
        }
        
        return image
    }
} 