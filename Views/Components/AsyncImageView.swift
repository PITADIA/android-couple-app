import SwiftUI
import FirebaseStorage
import FirebaseFunctions

struct AsyncImageView: View {
    let imageURL: String?
    let width: CGFloat?
    let height: CGFloat?
    let cornerRadius: CGFloat
    
    @State private var loadedImage: UIImage?
    @State private var isLoading = false
    @State private var hasError = false
    
    init(
        imageURL: String?,
        width: CGFloat? = nil,
        height: CGFloat? = nil,
        cornerRadius: CGFloat = 12
    ) {
        self.imageURL = imageURL
        self.width = width
        self.height = height
        self.cornerRadius = cornerRadius
    }
    
    var body: some View {
        Group {
            if let loadedImage = loadedImage {
                Image(uiImage: loadedImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: width, height: height)
                    .clipped()
                    .cornerRadius(cornerRadius)
            } else if isLoading {
                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: width, height: height)
                    .cornerRadius(cornerRadius)
                    .overlay(
                        ProgressView()
                            .scaleEffect(0.8)
                    )
            } else if hasError {
                Rectangle()
                    .fill(Color.gray.opacity(0.2))
                    .frame(width: width, height: height)
                    .cornerRadius(cornerRadius)
                    .overlay(
                        VStack(spacing: 4) {
                            Image(systemName: "photo")
                                .font(.title2)
                                .foregroundColor(.gray)
                            Text("Image non disponible")
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                    )
            } else {
                Rectangle()
                    .fill(Color.gray.opacity(0.2))
                    .frame(width: width, height: height)
                    .cornerRadius(cornerRadius)
                    .overlay(
                        Image(systemName: "photo")
                            .font(.title2)
                            .foregroundColor(.gray)
                    )
            }
        }
        .onAppear {
            loadImageIfNeeded()
        }
        .onChange(of: imageURL) { oldValue, newValue in
            loadImageIfNeeded()
        }
    }
    
    private func loadImageIfNeeded() {
        guard let imageURL = imageURL,
              !imageURL.isEmpty,
              loadedImage == nil,
              !isLoading else {
            print("🖼️ AsyncImageView: loadImageIfNeeded - Conditions non remplies")
            print("🖼️ AsyncImageView: - imageURL: \(imageURL ?? "nil")")
            print("🖼️ AsyncImageView: - isEmpty: \(imageURL?.isEmpty ?? true)")
            print("🖼️ AsyncImageView: - loadedImage: \(loadedImage != nil ? "présente" : "nil")")
            print("🖼️ AsyncImageView: - isLoading: \(isLoading)")
            return
        }
        
        print("🖼️ AsyncImageView: Tentative de chargement de l'image: \(imageURL)")
        loadImage(from: imageURL)
    }
    
    private func loadImage(from urlString: String) {
        print("🖼️ AsyncImageView: loadImage démarré pour: \(urlString)")
        isLoading = true
        hasError = false
        
        Task {
            do {
                let image = try await loadImageFromFirebase(urlString: urlString)
                
                print("✅ AsyncImageView: Image chargée avec succès pour: \(urlString)")
                await MainActor.run {
                    self.loadedImage = image
                    self.isLoading = false
                    self.hasError = false
                }
            } catch {
                print("❌ AsyncImageView: Erreur chargement image pour \(urlString): \(error)")
                
                await MainActor.run {
                    self.isLoading = false
                    self.hasError = true
                }
            }
        }
    }
    
    private func loadImageFromFirebase(urlString: String) async throws -> UIImage {
        print("🖼️ AsyncImageView: loadImageFromFirebase appelé avec: \(urlString)")
        
        // 1. NOUVEAU: Vérifier le cache d'abord
        if let cachedImage = ImageCacheService.shared.getCachedImage(for: urlString) {
            print("🖼️ AsyncImageView: Image trouvée en cache ! Aucun téléchargement nécessaire")
            return cachedImage
        }
        
        // 2. Si pas en cache, télécharger selon le type d'URL
        let downloadedImage: UIImage
        
        if urlString.contains("firebasestorage.googleapis.com") {
            // 🔧 NOUVEAU: Utiliser Cloud Function pour TOUTES les images Firebase Storage
            print("🖼️ AsyncImageView: URL Firebase Storage détectée - Utilisation Cloud Function...")
            downloadedImage = try await loadFromFirebaseStorageViaCloudFunction(urlString: urlString)
        } else if urlString.hasPrefix("placeholder_image_") {
            // Placeholder
            print("🖼️ AsyncImageView: Image placeholder détectée, génération d'une image par défaut")
            downloadedImage = try await createPlaceholderImage()
        } else {
            // URL normale
            print("🖼️ AsyncImageView: Tentative de chargement URL normale: \(urlString)")
            guard let url = URL(string: urlString) else {
                print("❌ AsyncImageView: URL invalide: \(urlString)")
                throw AsyncImageError.invalidData
            }
            
            let (data, _) = try await URLSession.shared.data(from: url)
            
            guard let image = UIImage(data: data) else {
                print("❌ AsyncImageView: Impossible de créer UIImage depuis les données")
                throw AsyncImageError.invalidData
            }
            
            print("✅ AsyncImageView: Image chargée depuis URL: \(urlString)")
            downloadedImage = image
        }
        
        // 3. NOUVEAU: Mettre l'image téléchargée en cache
        ImageCacheService.shared.cacheImage(downloadedImage, for: urlString)
        print("🖼️ AsyncImageView: Image mise en cache pour utilisation future")
        
        return downloadedImage
    }
    
    // 🔧 NOUVELLE MÉTHODE: Utiliser Cloud Function pour contourner les règles Firebase Storage
    private func loadFromFirebaseStorageViaCloudFunction(urlString: String) async throws -> UIImage {
        print("🔧 AsyncImageView: Tentative de chargement via Cloud Function pour: \(urlString)")
        
        // Extraire le chemin du fichier depuis l'URL
        guard let urlMatch = urlString.range(of: "/o/(.+?)\\?", options: .regularExpression),
              let encodedPath = String(urlString[urlMatch]).components(separatedBy: "/o/").last?.components(separatedBy: "?").first else {
            print("❌ AsyncImageView: Impossible d'extraire le chemin du fichier depuis l'URL")
            // Fallback vers la méthode directe
            return try await loadFromFirebaseStorageDirect(urlString: urlString)
        }
        
        let filePath = encodedPath.removingPercentEncoding ?? encodedPath
        print("🔧 AsyncImageView: Chemin extrait: \(filePath)")
        
        // Déterminer le type d'image et l'ID utilisateur
        if filePath.hasPrefix("profile_images/") {
            // Image de profil - extraire l'ID utilisateur
            let pathComponents = filePath.components(separatedBy: "/")
            if pathComponents.count >= 2 {
                let userId = pathComponents[1]
                print("🔧 AsyncImageView: Image de profil détectée pour utilisateur: \(userId)")
                return try await loadProfileImageViaCloudFunction(userId: userId)
            }
        } else if filePath.hasPrefix("journal_images/") {
            // Image du journal - utiliser URL signée générique
            print("🔧 AsyncImageView: Image du journal détectée")
            return try await loadImageViaSignedURL(filePath: filePath)
        }
        
        // Fallback vers la méthode directe si on ne peut pas identifier le type
        print("🔧 AsyncImageView: Type d'image non identifié, fallback vers méthode directe")
        return try await loadFromFirebaseStorageDirect(urlString: urlString)
    }
    
    // Charger image de profil via Cloud Function existante
    private func loadProfileImageViaCloudFunction(userId: String) async throws -> UIImage {
        return try await withCheckedThrowingContinuation { continuation in
            let functions = Functions.functions()
            
            functions.httpsCallable("getPartnerProfileImage").call(["partnerId": userId]) { result, error in
                if let error = error {
                    print("❌ AsyncImageView: Erreur Cloud Function getPartnerProfileImage: \(error)")
                    continuation.resume(throwing: error)
                    return
                }
                
                guard let data = result?.data as? [String: Any],
                      let success = data["success"] as? Bool,
                      success,
                      let signedUrl = data["imageUrl"] as? String else {
                    print("❌ AsyncImageView: Réponse Cloud Function invalide")
                    continuation.resume(throwing: AsyncImageError.loadingFailed)
                    return
                }
                
                print("✅ AsyncImageView: URL signée obtenue via Cloud Function: \(signedUrl)")
                
                // Charger l'image depuis l'URL signée
                Task {
                    do {
                        guard let url = URL(string: signedUrl) else {
                            continuation.resume(throwing: AsyncImageError.invalidData)
                            return
                        }
                        
                        let (imageData, _) = try await URLSession.shared.data(from: url)
                        
                        guard let image = UIImage(data: imageData) else {
                            continuation.resume(throwing: AsyncImageError.invalidData)
                            return
                        }
                        
                        print("✅ AsyncImageView: Image de profil chargée via Cloud Function")
                        continuation.resume(returning: image)
                    } catch {
                        print("❌ AsyncImageView: Erreur chargement depuis URL signée: \(error)")
                        continuation.resume(throwing: error)
                    }
                }
            }
        }
    }
    
    // Nouvelle méthode pour charger via URL signée générique
    private func loadImageViaSignedURL(filePath: String) async throws -> UIImage {
        return try await withCheckedThrowingContinuation { continuation in
            let functions = Functions.functions()
            
            functions.httpsCallable("getSignedImageURL").call(["filePath": filePath]) { result, error in
                if let error = error {
                    print("❌ AsyncImageView: Erreur Cloud Function getSignedImageURL: \(error)")
                    // Fallback vers méthode directe
                    Task {
                        do {
                            let image = try await self.loadFromFirebaseStorageDirect(urlString: "gs://love2love-26164.firebasestorage.app/\(filePath)")
                            continuation.resume(returning: image)
                        } catch {
                            continuation.resume(throwing: error)
                        }
                    }
                    return
                }
                
                guard let data = result?.data as? [String: Any],
                      let success = data["success"] as? Bool,
                      success,
                      let signedUrl = data["signedUrl"] as? String else {
                    print("❌ AsyncImageView: Réponse Cloud Function getSignedImageURL invalide")
                    // Fallback vers méthode directe
                    Task {
                        do {
                            let image = try await self.loadFromFirebaseStorageDirect(urlString: "gs://love2love-26164.firebasestorage.app/\(filePath)")
                            continuation.resume(returning: image)
                        } catch {
                            continuation.resume(throwing: error)
                        }
                    }
                    return
                }
                
                print("✅ AsyncImageView: URL signée obtenue pour image du journal: \(signedUrl)")
                
                // Charger l'image depuis l'URL signée
                Task {
                    do {
                        guard let url = URL(string: signedUrl) else {
                            continuation.resume(throwing: AsyncImageError.invalidData)
                            return
                        }
                        
                        let (imageData, _) = try await URLSession.shared.data(from: url)
                        
                        guard let image = UIImage(data: imageData) else {
                            continuation.resume(throwing: AsyncImageError.invalidData)
                            return
                        }
                        
                        print("✅ AsyncImageView: Image du journal chargée via URL signée")
                        continuation.resume(returning: image)
                    } catch {
                        print("❌ AsyncImageView: Erreur chargement depuis URL signée: \(error)")
                        continuation.resume(throwing: error)
                    }
                }
            }
        }
    }
    
    // Méthode directe en fallback (ancienne méthode)
    private func loadFromFirebaseStorageDirect(urlString: String) async throws -> UIImage {
        print("🔧 AsyncImageView: Fallback vers méthode directe Firebase Storage")
        return try await withCheckedThrowingContinuation { continuation in
            let storageRef = Storage.storage().reference(forURL: urlString)
            
            // Télécharger l'image avec une taille maximale de 10MB
            storageRef.getData(maxSize: 10 * 1024 * 1024) { data, error in
                if let error = error {
                    print("❌ AsyncImageView: Erreur téléchargement Firebase Storage direct: \(error)")
                    continuation.resume(throwing: error)
                    return
                }
                
                guard let data = data,
                      let image = UIImage(data: data) else {
                    print("❌ AsyncImageView: Données Firebase Storage invalides")
                    continuation.resume(throwing: AsyncImageError.invalidData)
                    return
                }
                
                print("✅ AsyncImageView: Image chargée depuis Firebase Storage direct")
                continuation.resume(returning: image)
            }
        }
    }
    
    private func createPlaceholderImage() async throws -> UIImage {
        return await withCheckedContinuation { continuation in
            let size = CGSize(width: 300, height: 200)
            let renderer = UIGraphicsImageRenderer(size: size)
            
            let image = renderer.image { context in
                // Fond dégradé
                let colors = [UIColor.systemPink.withAlphaComponent(0.3).cgColor,
                             UIColor.systemPurple.withAlphaComponent(0.3).cgColor]
                let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                        colors: colors as CFArray,
                                        locations: nil)!
                
                context.cgContext.drawLinearGradient(gradient,
                                                   start: CGPoint(x: 0, y: 0),
                                                   end: CGPoint(x: size.width, y: size.height),
                                                   options: [])
                
                // Icône cœur au centre
                let heartSize: CGFloat = 40
                let heartRect = CGRect(x: (size.width - heartSize) / 2,
                                     y: (size.height - heartSize) / 2,
                                     width: heartSize,
                                     height: heartSize)
                
                UIColor.systemPink.setFill()
                let heartPath = UIBezierPath()
                let centerX = heartRect.midX
                let centerY = heartRect.midY
                
                // Dessiner un cœur simple
                heartPath.move(to: CGPoint(x: centerX, y: centerY + 10))
                heartPath.addCurve(to: CGPoint(x: centerX - 15, y: centerY - 5),
                                 controlPoint1: CGPoint(x: centerX - 5, y: centerY + 5),
                                 controlPoint2: CGPoint(x: centerX - 15, y: centerY))
                heartPath.addCurve(to: CGPoint(x: centerX, y: centerY - 15),
                                 controlPoint1: CGPoint(x: centerX - 15, y: centerY - 10),
                                 controlPoint2: CGPoint(x: centerX - 5, y: centerY - 15))
                heartPath.addCurve(to: CGPoint(x: centerX + 15, y: centerY - 5),
                                 controlPoint1: CGPoint(x: centerX + 5, y: centerY - 15),
                                 controlPoint2: CGPoint(x: centerX + 15, y: centerY - 10))
                heartPath.addCurve(to: CGPoint(x: centerX, y: centerY + 10),
                                 controlPoint1: CGPoint(x: centerX + 15, y: centerY),
                                 controlPoint2: CGPoint(x: centerX + 5, y: centerY + 5))
                heartPath.close()
                heartPath.fill()
            }
            
            continuation.resume(returning: image)
        }
    }
}

enum AsyncImageError: LocalizedError {
    case invalidData
    case loadingFailed
    
    var errorDescription: String? {
        switch self {
        case .invalidData:
            return "Données d'image invalides"
        case .loadingFailed:
            return "Échec du chargement de l'image"
        }
    }
}

#Preview {
    VStack(spacing: 20) {
        AsyncImageView(imageURL: nil, width: 200, height: 150)
        AsyncImageView(imageURL: "", width: 200, height: 150)
        AsyncImageView(imageURL: "invalid-url", width: 200, height: 150)
    }
    .padding()
} 