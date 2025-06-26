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
            print("🖼️ ImageCacheService: Image trouvée en cache mémoire pour: \(urlString)")
            return memoryImage
        }
        
        // 2. Vérifier le cache disque
        if let diskImage = loadImageFromDisk(cacheKey: cacheKey) {
            print("🖼️ ImageCacheService: Image trouvée en cache disque pour: \(urlString)")
            // Remettre en cache mémoire
            memoryCache.setObject(diskImage, forKey: cacheKey as NSString)
            return diskImage
        }
        
        print("🖼️ ImageCacheService: Aucune image en cache pour: \(urlString)")
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
        
        print("🖼️ ImageCacheService: Image mise en cache pour: \(urlString)")
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
            print("🖼️ ImageCacheService: Image sauvée sur disque: \(cacheKey)")
        } catch {
            print("❌ ImageCacheService: Erreur sauvegarde disque: \(error)")
        }
    }
    
    // MARK: - Widget Compatibility
    
    /// Méthode compatible avec WidgetService pour unifier les caches
    func cacheImageForWidget(_ image: UIImage, fileName: String) {
        let fileURL = cacheDirectory.appendingPathComponent(fileName)
        
        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            return
        }
        
        do {
            try imageData.write(to: fileURL)
            print("🖼️ ImageCacheService: Image widget sauvée: \(fileName)")
        } catch {
            print("❌ ImageCacheService: Erreur sauvegarde widget: \(error)")
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