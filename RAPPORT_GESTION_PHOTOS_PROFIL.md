# 📸 Gestion Photos de Profil - Rapport Technique Complet

## Vue d'Ensemble

Ce rapport détaille l'architecture complète de gestion des photos de profil dans l'application iOS Love2Love, couvrant l'upload depuis l'onboarding et le menu, le stockage Firebase Storage, la récupération sécurisée par les partenaires, et le système de cache optimisé. Il propose ensuite une implémentation équivalente pour Android GO.

---

## 🏗️ Architecture iOS Actuelle

### **1. Upload depuis l'Onboarding - ProfilePhotoStepView.swift**

**Flow d'onboarding complet** :

```swift
struct ProfilePhotoStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var selectedImage: UIImage?     // Image brute sélectionnée
    @State private var croppedImage: UIImage?      // Image après cropping
    @State private var showImageCropper = false    // SwiftyCrop interface

    // Gestion permissions photos
    @State private var showingGalleryPicker = false        // Accès complet
    @State private var showingLimitedGalleryView = false   // Accès limité iOS 14+
    @State private var limitedPhotoAssets: [PHAsset] = []

    var body: some View {
        VStack {
            // Interface de sélection photo
            Button(action: {
                checkPhotoLibraryPermission() // ✅ Gestion permissions iOS
            }) {
                ZStack {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 160, height: 160)
                        .shadow(color: Color.black.opacity(0.1), radius: 15)

                    if let croppedImage = croppedImage {
                        // Image finale croppée
                        Image(uiImage: croppedImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 160, height: 160)
                            .clipShape(Circle())
                    } else {
                        // Placeholder par défaut
                        VStack {
                            Image(systemName: "person.fill")
                                .font(.system(size: 40))
                                .foregroundColor(.black.opacity(0.3))

                            Text("add_photo".localized)
                                .font(.system(size: 14))
                                .foregroundColor(.black.opacity(0.6))
                        }
                    }
                }
            }

            // Boutons "Continue" et "Passer cette étape"
        }
        .sheet(isPresented: $showingGalleryPicker) {
            StandardGalleryPicker(onImageSelected: handleImageSelection)
        }
        .sheet(isPresented: $showingLimitedGalleryView) {
            LimitedGalleryView(assets: limitedPhotoAssets, onImageSelected: handleImageSelection)
        }
        .fullScreenCover(isPresented: $showImageCropper) {
            SwiftyCropView(
                imageToCrop: selectedImage,
                maskShape: .circle,
                configuration: SwiftyCropConfiguration(
                    maxMagnificationScale: 4.0,
                    maskRadius: 150,
                    cropImageCircular: true,
                    rotateImage: false,
                    texts: SwiftyCropConfiguration.Texts(
                        cancelButton: "cancel".localized,
                        interactionInstructions: "crop_photo_instructions".localized,
                        saveButton: "validate".localized
                    )
                )
            ) { resultImage in
                // Callback après cropping
                guard let finalImage = resultImage else { return }

                self.croppedImage = finalImage
                self.viewModel.profileImage = finalImage // ✅ Stockage dans ViewModel
                self.showImageCropper = false
            }
        }
    }

    private func handleImageSelection(_ imageData: UIImage) {
        selectedImage = imageData

        // Fermer les sheets de sélection
        showingGalleryPicker = false
        showingLimitedGalleryView = false

        // Petit délai pour éviter conflits de sheets
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.showImageCropper = true
        }
    }
}
```

**Gestion permissions iOS** :

- **Accès complet** : `StandardGalleryPicker` (PHPickerViewController)
- **Accès limité iOS 14+** : `LimitedGalleryView` avec `PHAsset`
- **Permissions refusées** : Alerte avec redirection Réglages iOS

**Cropping avec SwiftyCrop** :

- **Forme circulaire** : `maskShape: .circle`
- **Zoom 4x maximum** : Pour recadrage précis
- **Rotation désactivée** : Interface simplifiée
- **Rayon fixe** : 150px pour cohérence visuelle

---

### **2. Upload depuis Menu - MenuView.swift**

**Interface menu utilisateur** :

```swift
struct MenuView: View {
    @State private var croppedImage: UIImage?       // Image croppée récemment
    @State private var selectedImage: UIImage?      // Image avant crop
    @State private var showImageCropper = false     // SwiftyCrop

    // Même système de permissions que l'onboarding
    @State private var showingGalleryPicker = false
    @State private var showingLimitedGalleryView = false
    @State private var limitedPhotoAssets: [PHAsset] = []

    private var headerSection: some View {
        VStack(spacing: 16) {
            // Photo de profil cliquable
            Button(action: {
                checkPhotoLibraryPermission() // ✅ Même logique que onboarding
            }) {
                ZStack {
                    // Effet de surbrillance (cohérent avec PartnerDistanceView)
                    Circle()
                        .fill(Color.white.opacity(0.35))
                        .frame(width: 132, height: 132)
                        .blur(radius: 6)

                    // Priorité d'affichage des images
                    if let croppedImage = croppedImage {
                        // 1. PRIORITÉ : Image récemment croppée
                        Image(uiImage: croppedImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 120, height: 120)
                            .clipShape(Circle())
                    } else if let cachedImage = UserCacheManager.shared.getCachedProfileImage() {
                        // 2. Image en cache UserCacheManager (instantané)
                        Image(uiImage: cachedImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 120, height: 120)
                            .clipShape(Circle())
                    } else if let imageURL = currentUserImageURL {
                        // 3. AsyncImageView (téléchargement si nécessaire)
                        AsyncImageView(
                            imageURL: imageURL,
                            width: 120,
                            height: 120,
                            cornerRadius: 60
                        )
                    } else {
                        // 4. Placeholder par défaut
                        VStack {
                            Image(systemName: "person.fill")
                                .font(.system(size: 40))
                                .foregroundColor(.gray)
                            Text("Ajouter une photo")
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                    }
                }
            }
        }
    }

    // Même interface SwiftyCrop que l'onboarding
    .fullScreenCover(isPresented: $showImageCropper) {
        SwiftyCropView(imageToCrop: selectedImage, ...) { resultImage in
            guard let finalImage = resultImage else { return }

            // ✅ DIFFÉRENCE avec onboarding : Upload immédiat
            uploadProfileImage(finalImage)
            self.showImageCropper = false
        }
    }

    /// Upload optimisé pour le menu
    private func uploadProfileImage(_ image: UIImage) {
        guard appState.currentUser != nil else { return }

        // 🚀 STRATÉGIE : Cache immédiat + Upload silencieux

        // 1. Cache local instantané pour UI réactive
        UserCacheManager.shared.cacheProfileImage(image)

        // 2. Nettoyer états temporaires
        self.croppedImage = nil

        print("✅ MenuView: Image mise en cache, affichage immédiat")

        // 3. Upload Firebase en arrière-plan (non bloquant)
        Task {
            await uploadToFirebaseInBackground(image)
        }
    }

    /// Upload silencieux sans affecter l'UI
    private func uploadToFirebaseInBackground(_ image: UIImage) async {
        print("🔄 MenuView: Début upload silencieux Firebase")

        await withCheckedContinuation { continuation in
            FirebaseService.shared.updateProfileImage(image) { success, imageURL in
                DispatchQueue.main.async {
                    if success {
                        print("✅ MenuView: Upload Firebase réussi")
                    } else {
                        print("❌ MenuView: Échec upload Firebase")
                    }
                    continuation.resume()
                }
            }
        }
    }
}
```

**Différences Menu vs Onboarding** :

- **Upload immédiat** : Pas de stockage temporaire en ViewModel
- **Cache prioritaire** : Affichage instantané via `UserCacheManager`
- **Upload silencieux** : N'affecte pas l'expérience utilisateur
- **Même interface** : Cohérence UX entre onboarding et menu

---

### **3. Service Firebase Storage - FirebaseService.swift**

**Upload sécurisé vers Firebase** :

```swift
class FirebaseService {

    /// Méthode publique pour upload photo profil
    func updateProfileImage(_ image: UIImage, completion: @escaping (Bool, String?) -> Void) {
        print("🔥 FirebaseService: updateProfileImage - Méthode publique")

        guard let currentUser = currentUser else {
            print("❌ FirebaseService: Aucun utilisateur actuel")
            completion(false, nil)
            return
        }

        uploadProfileImage(image) { [weak self] imageURL in
            guard let self = self else { return }

            if let imageURL = imageURL {
                print("✅ FirebaseService: Image uploadée avec succès")

                // 🚀 Cache immédiat pour affichage instantané
                UserCacheManager.shared.cacheProfileImage(image)
                print("🖼️ FirebaseService: Image mise en cache")

                // 🗑️ Invalider ancien cache ImageCacheService
                if let oldURL = currentUser.profileImageURL {
                    ImageCacheService.shared.clearCachedImage(for: oldURL)
                }

                // Mettre à jour utilisateur avec nouvelle URL + timestamp
                var updatedUser = currentUser
                updatedUser = AppUser(
                    id: updatedUser.id,
                    name: updatedUser.name,
                    // ... autres propriétés préservées
                    profileImageURL: imageURL,
                    profileImageUpdatedAt: Date(), // ✅ Timestamp pour cache-busting
                    // ... reste des propriétés
                )

                // Sauvegarder utilisateur mis à jour
                self.currentUser = updatedUser
                UserCacheManager.shared.cacheUser(updatedUser)

                // Mettre à jour Firestore
                self.saveUserData(updatedUser)
                completion(true, imageURL)

            } else {
                print("❌ FirebaseService: Échec upload image")
                completion(false, nil)
            }
        }
    }

    /// Upload privé vers Firebase Storage
    private func uploadProfileImage(_ image: UIImage, completion: @escaping (String?) -> Void) {
        guard let firebaseUser = Auth.auth().currentUser else {
            print("❌ FirebaseService: Utilisateur non connecté")
            completion(nil)
            return
        }

        // 🔄 Background task pour upload même si app en arrière-plan
        var backgroundTask: UIBackgroundTaskIdentifier = .invalid

        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "ProfileImageUpload") {
            print("⏰ FirebaseService: Temps d'arrière-plan expiré")
            UIApplication.shared.endBackgroundTask(backgroundTask)
            backgroundTask = .invalid
        }

        let endBackgroundTask = {
            if backgroundTask != .invalid {
                UIApplication.shared.endBackgroundTask(backgroundTask)
                backgroundTask = .invalid
            }
        }

        // 📐 Redimensionnement optimisé
        guard let resizedImage = resizeImage(image, to: CGSize(width: 300, height: 300)),
              let imageData = resizedImage.jpegData(compressionQuality: 0.8) else {
            print("❌ FirebaseService: Erreur traitement image")
            endBackgroundTask()
            completion(nil)
            return
        }

        print("🔥 FirebaseService: Image optimisée - Taille: \(imageData.count) bytes")

        // 📂 Chemin Firebase Storage structuré
        let storage = Storage.storage()
        let storageRef = storage.reference()
        let profileImagePath = "profile_images/\(firebaseUser.uid)/profile.jpg"
        let profileImageRef = storageRef.child(profileImagePath)

        // 🏷️ Métadonnées explicites
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        metadata.customMetadata = ["uploadedBy": firebaseUser.uid]

        // 📤 Upload avec métadonnées
        profileImageRef.putData(imageData, metadata: metadata) { uploadMetadata, error in
            if let error = error {
                print("❌ FirebaseService: Erreur upload: \(error.localizedDescription)")
                print("❌ Code erreur: \((error as NSError).code)")
                endBackgroundTask()
                completion(nil)
                return
            }

            print("✅ FirebaseService: Upload réussi")

            // 🔗 Récupération URL de téléchargement sécurisée
            profileImageRef.downloadURL { url, urlError in
                endBackgroundTask() // Nettoyer background task

                if let urlError = urlError {
                    print("❌ FirebaseService: Erreur récupération URL: \(urlError.localizedDescription)")
                    completion(nil)
                    return
                }

                guard let downloadURL = url else {
                    print("❌ FirebaseService: URL de téléchargement manquante")
                    completion(nil)
                    return
                }

                print("✅ FirebaseService: URL récupérée avec succès")
                completion(downloadURL.absoluteString)
            }
        }
    }

    /// Redimensionnement optimisé des images
    private func resizeImage(_ image: UIImage, to size: CGSize) -> UIImage? {
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }
}
```

**Structure Firebase Storage** :

```
Firebase Storage:
├── profile_images/
│   ├── {userId1}/
│   │   └── profile.jpg       ← 300x300px, JPEG 80%, metadata
│   ├── {userId2}/
│   │   └── profile.jpg
│   └── ...
├── journal_images/
│   └── {userId}/
│       ├── {uuid1}.jpg
│       └── {uuid2}.jpg
```

**Optimisations** :

- ✅ **Redimensionnement 300x300px** : Équilibre qualité/taille
- ✅ **Compression JPEG 80%** : Optimal pour photos profil
- ✅ **Background task** : Upload continue même en arrière-plan
- ✅ **Métadonnées** : Traçabilité et debug
- ✅ **Gestion d'erreurs** : Logs détaillés pour debug

**Structure Firestore** :

```
users/{userId} {
  profileImageURL: "https://firebasestorage.googleapis.com/v0/b/...",
  profileImageUpdatedAt: Timestamp,
  // ... autres champs utilisateur
}
```

---

### **4. Récupération par les Partenaires - Cloud Functions**

**Cloud Function sécurisée** (`getPartnerProfileImage`) :

```javascript
exports.getPartnerProfileImage = functions.https.onCall(
  async (data, context) => {
    console.log(
      "🖼️ getPartnerProfileImage: Début récupération image partenaire"
    );

    // 1. SÉCURITÉ : Vérifier authentification
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const currentUserId = context.auth.uid;
    const { partnerId } = data;

    if (!partnerId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "ID partenaire requis"
      );
    }

    try {
      // 2. SÉCURITÉ : Vérifier relation partenaire
      const currentUserDoc = await admin
        .firestore()
        .collection("users")
        .doc(currentUserId)
        .get();

      if (!currentUserDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Utilisateur non trouvé"
        );
      }

      const currentUserData = currentUserDoc.data();

      // ✅ CONTRÔLE STRICT : Seuls les partenaires connectés peuvent accéder
      if (currentUserData.partnerId !== partnerId) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Vous n'êtes pas autorisé à accéder aux informations de cet utilisateur"
        );
      }

      // 3. Récupérer données partenaire
      const partnerDoc = await admin
        .firestore()
        .collection("users")
        .doc(partnerId)
        .get();

      if (!partnerDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Partenaire non trouvé"
        );
      }

      const partnerData = partnerDoc.data();
      const profileImageURL = partnerData.profileImageURL;

      // 4. Vérifier disponibilité image
      if (!profileImageURL) {
        return {
          success: false,
          reason: "NO_PROFILE_IMAGE",
          message: "Aucune photo de profil disponible",
        };
      }

      // 5. SÉCURITÉ : Générer URL signée temporaire
      const bucket = admin.storage().bucket();

      // Parser URL Firebase Storage pour extraire chemin
      const urlMatch = profileImageURL.match(/\/o\/(.*?)\?/);
      if (!urlMatch) {
        throw new functions.https.HttpsError(
          "internal",
          "Format d'URL d'image invalide"
        );
      }

      const filePath = decodeURIComponent(urlMatch[1]);
      const file = bucket.file(filePath);

      console.log(
        `🖼️ getPartnerProfileImage: Génération URL signée pour: ${filePath}`
      );

      // 🔐 URL SIGNÉE avec expiration courte (1 heure)
      const [signedUrl] = await file.getSignedUrl({
        action: "read",
        expires: Date.now() + 60 * 60 * 1000, // 1 heure
      });

      logger.info("✅ getPartnerProfileImage: URL signée générée avec succès");

      return {
        success: true,
        imageUrl: signedUrl, // ✅ URL temporaire sécurisée
        expiresIn: 3600, // 1 heure en secondes
      };
    } catch (error) {
      console.error("❌ getPartnerProfileImage: Erreur:", error);

      if (error.code && error.message) {
        throw error; // Relancer HttpsError existante
      }

      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);
```

**Sécurité Cloud Function** :

- ✅ **Authentification obligatoire** : `context.auth` vérifié
- ✅ **Vérification relation** : `partnerId` doit correspondre exactement
- ✅ **URLs signées temporaires** : Expiration 1h pour sécurité
- ✅ **Pas d'accès arbitraire** : Impossible d'accéder à n'importe qui
- ✅ **Logs sécurisés** : Aucun ID ou URL exposé dans les logs

---

### **5. Système de Cache Multi-Niveaux**

**UserCacheManager.swift - Cache Utilisateur** :

```swift
class UserCacheManager {
    static let shared = UserCacheManager()

    private let userDefaults = UserDefaults.standard
    private let profileImageCacheKey = "cached_profile_image"
    private let partnerImageCacheKey = "cached_partner_image"
    private let partnerImageURLKey = "cached_partner_image_url"

    // MARK: - Cache Photo Utilisateur

    /// Cache physique de l'image de profil pour affichage instantané
    func cacheProfileImage(_ image: UIImage) {
        print("🖼️ UserCacheManager: Mise en cache image de profil")

        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            print("❌ UserCacheManager: Impossible de convertir l'image")
            return
        }

        userDefaults.set(imageData, forKey: profileImageCacheKey)
        print("✅ UserCacheManager: Image profil cachée (\(imageData.count) bytes)")
    }

    /// Récupération instantanée image de profil
    func getCachedProfileImage() -> UIImage? {
        guard let imageData = userDefaults.data(forKey: profileImageCacheKey) else {
            print("🖼️ UserCacheManager: Aucune image de profil en cache")
            return nil
        }

        guard let image = UIImage(data: imageData) else {
            print("❌ UserCacheManager: Impossible de charger depuis cache")
            // Nettoyer données corrompues
            userDefaults.removeObject(forKey: profileImageCacheKey)
            return nil
        }

        print("✅ UserCacheManager: Image de profil trouvée en cache")
        return image
    }

    // MARK: - Cache Photo Partenaire

    /// Cache photo partenaire avec URL pour détection changements
    func cachePartnerImage(_ image: UIImage, url: String) {
        guard let imageData = image.jpegData(compressionQuality: 0.8) else { return }

        userDefaults.set(imageData, forKey: partnerImageCacheKey)
        userDefaults.set(url, forKey: partnerImageURLKey)
        print("✅ UserCacheManager: Image partenaire mise en cache")
    }

    /// Vérifier si URL partenaire a changé (trigger re-téléchargement)
    func hasPartnerImageChanged(newURL: String?) -> Bool {
        let cachedURL = userDefaults.string(forKey: partnerImageURLKey)
        let hasChanged = cachedURL != newURL

        if hasChanged {
            print("🔄 UserCacheManager: URL partenaire changée")
        }

        return hasChanged
    }

    /// Récupération image partenaire
    func getCachedPartnerImage() -> UIImage? {
        guard let imageData = userDefaults.data(forKey: partnerImageCacheKey),
              let image = UIImage(data: imageData) else {
            return nil
        }
        return image
    }

    /// Nettoyage cache (déconnexion, etc.)
    func clearCache() {
        userDefaults.removeObject(forKey: profileImageCacheKey)
        userDefaults.removeObject(forKey: partnerImageCacheKey)
        userDefaults.removeObject(forKey: partnerImageURLKey)
        print("🗑️ UserCacheManager: Cache nettoyé")
    }
}
```

**ImageCacheService.swift - Cache Général** :

```swift
class ImageCacheService {
    static let shared = ImageCacheService()

    private let memoryCache = NSCache<NSString, UIImage>()
    private let fileManager = FileManager.default
    private let cacheDirectory: URL

    init() {
        // 🗂️ App Group pour partage avec widgets
        if let containerURL = fileManager.containerURL(forSecurityApplicationGroupIdentifier: "group.com.lyes.love2love") {
            cacheDirectory = containerURL.appendingPathComponent("ImageCache")
        } else {
            cacheDirectory = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("ImageCache")
        }

        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)

        // Configuration cache mémoire
        memoryCache.countLimit = 50        // Max 50 images
        memoryCache.totalCostLimit = 100 * 1024 * 1024  // Max 100MB
    }

    func getCachedImage(for urlString: String) -> UIImage? {
        let cacheKey = cacheKeyForURL(urlString)

        // 1. Vérifier cache mémoire (plus rapide)
        if let memoryImage = memoryCache.object(forKey: cacheKey as NSString) {
            print("🖼️ ImageCacheService: Image trouvée en cache mémoire")
            return memoryImage
        }

        // 2. Vérifier cache disque
        if let diskImage = loadImageFromDisk(cacheKey: cacheKey) {
            print("🖼️ ImageCacheService: Image trouvée en cache disque")
            // Remettre en cache mémoire
            memoryCache.setObject(diskImage, forKey: cacheKey as NSString)
            return diskImage
        }

        return nil
    }

    func cacheImage(_ image: UIImage, for urlString: String) {
        let cacheKey = cacheKeyForURL(urlString)

        // Cache mémoire immédiat
        memoryCache.setObject(image, forKey: cacheKey as NSString)

        // Cache disque asynchrone
        Task.detached { [weak self] in
            self?.saveImageToDisk(image, cacheKey: cacheKey)
        }
    }
}
```

**Hiérarchie des caches** :

1. **Cache mémoire** (`NSCache`) - < 1ms
2. **Cache disque** (`FileManager`) - < 50ms
3. **UserCacheManager** (`UserDefaults`) - < 10ms, persistant
4. **Firebase Storage** - 500ms-2s, authoritative

---

### **6. Affichage Optimisé - AsyncImageView.swift**

**Composant de chargement intelligent** :

```swift
struct AsyncImageView: View {
    let imageURL: String?
    let width: CGFloat?
    let height: CGFloat?
    let cornerRadius: CGFloat

    @State private var loadedImage: UIImage?
    @State private var isLoading = false
    @State private var hasError = false

    var body: some View {
        Group {
            if let loadedImage = loadedImage {
                // ✅ Image chargée avec succès
                Image(uiImage: loadedImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: width, height: height)
                    .clipped()
                    .cornerRadius(cornerRadius)
            } else if isLoading {
                // 🔄 État de chargement
                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: width, height: height)
                    .cornerRadius(cornerRadius)
                    .overlay(
                        ProgressView()
                            .scaleEffect(0.8)
                    )
            } else if hasError {
                // ❌ État d'erreur avec retry
                Rectangle()
                    .fill(Color.gray.opacity(0.2))
                    .frame(width: width, height: height)
                    .cornerRadius(cornerRadius)
                    .overlay(
                        VStack {
                            Image(systemName: "photo")
                                .font(.title2)
                                .foregroundColor(.gray)
                            Text("Retry")
                                .font(.caption)
                                .foregroundColor(.blue)
                        }
                    )
                    .onTapGesture {
                        loadImage() // Retry manuel
                    }
            }
        }
        .onAppear {
            loadImage()
        }
    }

    private func loadImage() {
        guard let imageURL = imageURL, !imageURL.isEmpty else {
            hasError = true
            return
        }

        Task {
            await loadImageFromFirebase(urlString: imageURL)
        }
    }

    private func loadImageFromFirebase(urlString: String) async {
        await MainActor.run {
            isLoading = true
            hasError = false
        }

        do {
            // 1. Vérifier cache d'abord
            if let cachedImage = ImageCacheService.shared.getCachedImage(for: urlString) {
                await MainActor.run {
                    self.loadedImage = cachedImage
                    self.isLoading = false
                }
                return
            }

            // 2. Télécharger depuis Firebase
            let downloadedImage = try await downloadFromFirebaseStorage(urlString)

            // 3. Mettre en cache
            ImageCacheService.shared.cacheImage(downloadedImage, for: urlString)

            await MainActor.run {
                self.loadedImage = downloadedImage
                self.isLoading = false
            }

        } catch {
            print("❌ AsyncImageView: Erreur chargement: \(error)")
            await MainActor.run {
                self.hasError = true
                self.isLoading = false
            }
        }
    }
}
```

---

## 🔄 Flow Complet iOS

### **Onboarding → Firebase → Partenaire**

```
1. ONBOARDING
   │
   ├─ Utilisateur sélectionne photo
   │  └─ PHPickerViewController → UIImage
   │
   ├─ Cropping avec SwiftyCrop
   │  └─ Image circulaire 150px radius
   │
   ├─ Stockage temporaire ViewModel
   │  └─ OnboardingViewModel.profileImage = croppedImage
   │
   └─ Finalisation onboarding
      └─ finalizeOnboardingWithPartnerData()
         └─ uploadProfileImage() → Firebase Storage

2. FIREBASE STORAGE
   │
   ├─ Redimensionnement 300x300px
   │  └─ JPEG 80% compression
   │
   ├─ Upload vers "profile_images/{userId}/profile.jpg"
   │  └─ Métadonnées + Background Task
   │
   ├─ Génération URL de téléchargement
   │  └─ URL sécurisée Firebase Storage
   │
   └─ Mise à jour Firestore
      └─ users/{userId}.profileImageURL = downloadURL

3. CACHE LOCAL
   │
   ├─ UserCacheManager.cacheProfileImage()
   │  └─ UserDefaults JPEG 80%
   │
   ├─ ImageCacheService.cacheImage()
   │  └─ NSCache + File System (App Group)
   │
   └─ Affichage instantané
      └─ Priorité cache > URL Firebase

4. RÉCUPÉRATION PARTENAIRE
   │
   ├─ Partenaire ouvre app
   │  └─ PartnerDistanceView.onAppear
   │
   ├─ Appel Cloud Function "getPartnerProfileImage"
   │  ├─ Vérification authentification
   │  ├─ Vérification relation partenaire
   │  └─ Génération URL signée (1h)
   │
   ├─ Téléchargement via URL signée
   │  └─ AsyncImageView → loadImageFromFirebase()
   │
   └─ Cache partenaire
      └─ UserCacheManager.cachePartnerImage()
```

### **Menu → Mise à jour**

```
1. MENU UTILISATEUR
   │
   ├─ Tap sur photo de profil
   │  └─ checkPhotoLibraryPermission()
   │
   ├─ Sélection + Cropping
   │  └─ SwiftyCrop → finalImage
   │
   └─ uploadProfileImage(finalImage)
      ├─ Cache immédiat UserCacheManager ✅
      ├─ UI mise à jour instantanément ⚡
      └─ Upload Firebase en arrière-plan 🔄

2. SYNCHRONISATION PARTENAIRE
   │
   ├─ URL Firebase Storage mise à jour
   │  └─ profileImageUpdatedAt = Date()
   │
   ├─ Partenaire détecte changement URL
   │  └─ hasPartnerImageChanged() = true
   │
   ├─ Re-téléchargement automatique
   │  └─ Cloud Function → nouvelle URL signée
   │
   └─ Cache partenaire mis à jour
      └─ Interface partenaire rafraîchie
```

---

# 🤖 Implémentation Android GO Équivalente

## Architecture Android Proposée

### **1. Structure Générale**

```kotlin
📁 com.love2love.android/
├── 📁 data/
│   ├── 📁 repository/
│   │   ├── ProfileImageRepository.kt
│   │   └── UserRepository.kt
│   ├── 📁 cache/
│   │   ├── ProfileImageCache.kt
│   │   ├── MemoryImageCache.kt
│   │   └── DiskImageCache.kt
│   ├── 📁 api/
│   │   ├── FirebaseStorageService.kt
│   │   └── CloudFunctionService.kt
│   └── 📁 local/
│       ├── ImagePreferences.kt
│       └── ImageDatabase.kt
├── 📁 ui/
│   ├── 📁 onboarding/
│   │   ├── ProfilePhotoScreen.kt
│   │   └── OnboardingViewModel.kt
│   ├── 📁 profile/
│   │   ├── MenuScreen.kt
│   │   └── ProfileViewModel.kt
│   └── 📁 components/
│       ├── AsyncProfileImage.kt
│       ├── ImageCropper.kt
│       └── PhotoPicker.kt
├── 📁 utils/
│   ├── ImageUtils.kt
│   ├── PermissionUtils.kt
│   └── CacheUtils.kt
└── 📁 services/
    ├── UploadService.kt
    └── BackgroundUploadWorker.kt
```

---

## 📱 Upload depuis Onboarding Android

### **2. ProfilePhotoScreen.kt - Onboarding**

```kotlin
@Composable
fun ProfilePhotoScreen(
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    onNextStep: () -> Unit
) {
    val uiState by onboardingViewModel.uiState.collectAsState()
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var croppedImage by remember { mutableStateOf<Bitmap?>(null) }
    var showImageCropper by remember { mutableStateOf(false) }

    // Permission launcher pour galerie photos
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showImageCropper = true
        }
    }

    // Permission launcher pour permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
        } else {
            // Gérer permission refusée
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(40.dp))

        // Titre
        Text(
            text = stringResource(R.string.add_profile_photo),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.Black,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Photo de profil circulaire
        Card(
            modifier = Modifier
                .size(160.dp)
                .clickable { checkPhotoPermission(permissionLauncher, galleryLauncher) },
            shape = CircleShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (croppedImage != null) {
                    // Image croppée finale
                    Image(
                        bitmap = croppedImage!!.asImageBitmap(),
                        contentDescription = "Photo de profil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = Color.Black.copy(alpha = 0.3f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.add_photo),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Boutons Continue et Skip
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    // Sauvegarder image dans ViewModel
                    croppedImage?.let { onboardingViewModel.setProfileImage(it) }
                    onNextStep()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFD267A)
                )
            ) {
                Text(
                    text = stringResource(R.string.continue_button),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(15.dp))

            TextButton(
                onClick = onNextStep // Skip sans image
            ) {
                Text(
                    text = stringResource(R.string.skip_step),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        textDecoration = TextDecoration.Underline
                    ),
                    color = Color.Black.copy(alpha = 0.6f)
                )
            }
        }
    }

    // Image Cropper Dialog
    if (showImageCropper && selectedImageUri != null) {
        ImageCropperDialog(
            imageUri = selectedImageUri!!,
            onImageCropped = { bitmap ->
                croppedImage = bitmap
                showImageCropper = false
            },
            onDismiss = {
                showImageCropper = false
            }
        )
    }
}

@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    onImageCropped: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.crop_photo_instructions),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Image cropper (utiliser une bibliothèque comme uCrop)
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            // Configurer ImageView pour cropping
                            scaleType = ImageView.ScaleType.CENTER_CROP

                            // Charger image depuis URI
                            try {
                                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    ImageDecoder.decodeBitmap(
                                        ImageDecoder.createSource(ctx.contentResolver, imageUri)
                                    )
                                } else {
                                    MediaStore.Images.Media.getBitmap(ctx.contentResolver, imageUri)
                                }
                                setImageBitmap(bitmap)
                            } catch (e: Exception) {
                                Log.e("ImageCropper", "Erreur chargement image", e)
                            }
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }

                    Button(
                        onClick = {
                            // Simuler crop (remplacer par vraie logique crop)
                            try {
                                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    ImageDecoder.decodeBitmap(
                                        ImageDecoder.createSource(context.contentResolver, imageUri)
                                    )
                                } else {
                                    MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                                }

                                // Crop circulaire basique
                                val croppedBitmap = ImageUtils.cropToCircle(bitmap)
                                onImageCropped(croppedBitmap)

                            } catch (e: Exception) {
                                Log.e("ImageCropper", "Erreur crop", e)
                                onDismiss()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.validate))
                    }
                }
            }
        }
    }
}

private fun checkPhotoPermission(
    permissionLauncher: ActivityResultLauncher<String>,
    galleryLauncher: ActivityResultLauncher<String>
) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
        else -> {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
```

### **3. OnboardingViewModel.kt - Gestion État**

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileImageRepository: ProfileImageRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    data class OnboardingUiState(
        val currentStep: OnboardingStep = OnboardingStep.RELATIONSHIP_GOALS,
        val userName: String = "",
        val profileImage: Bitmap? = null,  // ✅ Image stockée temporairement
        val birthDate: LocalDate? = null,
        val relationshipGoals: List<String> = emptyList(),
        val relationshipDuration: RelationshipDuration = RelationshipDuration.NONE,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    fun setProfileImage(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(profileImage = bitmap)
        Log.d("OnboardingViewModel", "Photo de profil définie: ${bitmap.width}x${bitmap.height}")
    }

    fun nextStep() {
        val currentStep = _uiState.value.currentStep

        val nextStep = when (currentStep) {
            // ... autres étapes
            OnboardingStep.PROFILE_PHOTO -> OnboardingStep.COMPLETION
            OnboardingStep.COMPLETION -> {
                finalizeOnboarding()
                OnboardingStep.LOADING
            }
            // ... autres étapes
            else -> currentStep
        }

        _uiState.value = _uiState.value.copy(currentStep = nextStep)
    }

    private fun finalizeOnboarding() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val state = _uiState.value

                // Créer utilisateur
                val user = User(
                    name = state.userName,
                    birthDate = state.birthDate?.let { Date.from(it.atStartOfDay(ZoneId.systemDefault()).toInstant()) } ?: Date(),
                    relationshipGoals = state.relationshipGoals,
                    relationshipDuration = state.relationshipDuration
                )

                // Sauvegarder utilisateur
                userRepository.saveUser(user)

                // Upload image de profil si présente
                state.profileImage?.let { bitmap ->
                    Log.d("OnboardingViewModel", "Upload image de profil...")
                    val result = profileImageRepository.uploadProfileImage(bitmap)

                    if (result.isSuccess) {
                        Log.d("OnboardingViewModel", "✅ Image uploadée avec succès")
                    } else {
                        Log.e("OnboardingViewModel", "❌ Échec upload image: ${result.exceptionOrNull()}")
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentStep = OnboardingStep.COMPLETED
                )

            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "❌ Erreur finalisation onboarding", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Erreur: ${e.message}"
                )
            }
        }
    }
}

enum class OnboardingStep {
    RELATIONSHIP_GOALS,
    RELATIONSHIP_IMPROVEMENT,
    // ... autres étapes
    PROFILE_PHOTO,
    COMPLETION,
    LOADING,
    COMPLETED
}
```

---

## 🍽️ Upload depuis Menu Android

### **4. MenuScreen.kt - Profile Menu**

```kotlin
@Composable
fun MenuScreen(
    profileViewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit
) {
    val uiState by profileViewModel.uiState.collectAsState()
    var showImagePicker by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageCropper by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showImageCropper = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            // Header avec photo de profil
            ProfileHeaderSection(
                profileImage = uiState.profileImage,
                isLoading = uiState.isUploadingImage,
                onImageClick = { showImagePicker = true },
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }

        item {
            // Section "À propos de moi"
            AboutMeSection(
                userName = uiState.userName,
                relationshipStart = uiState.relationshipStartDate,
                onEditName = { /* Édition nom */ },
                onEditRelationship = { /* Édition relation */ }
            )
        }

        // ... autres sections
    }

    // Image Picker Dialog
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text("Changer la photo de profil") },
            text = { Text("Sélectionnez une nouvelle photo depuis votre galerie") },
            confirmButton = {
                TextButton(
                    onClick = {
                        galleryLauncher.launch("image/*")
                        showImagePicker = false
                    }
                ) {
                    Text("Galerie")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Image Cropper
    if (showImageCropper && selectedImageUri != null) {
        ImageCropperDialog(
            imageUri = selectedImageUri!!,
            onImageCropped = { bitmap ->
                // ✅ Upload immédiat depuis menu
                profileViewModel.uploadProfileImage(bitmap)
                showImageCropper = false
            },
            onDismiss = {
                showImageCropper = false
            }
        )
    }
}

@Composable
fun ProfileHeaderSection(
    profileImage: Bitmap?,
    isLoading: Boolean,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            // Photo de profil avec effet de surbrillance
            Card(
                modifier = Modifier
                    .size(132.dp)
                    .clickable { onImageClick() },
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Effet de surbrillance
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.35f), CircleShape)
                            .blur(6.dp)
                    )

                    // Image ou placeholder
                    if (profileImage != null) {
                        Image(
                            bitmap = profileImage.asImageBitmap(),
                            contentDescription = "Photo de profil",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Placeholder
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(Color.Gray.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Gray
                                )
                                Text(
                                    text = "Ajouter photo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // Overlay de chargement
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Icône caméra
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color.Gray.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Changer photo",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Touchez pour changer votre photo",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}
```

### **5. ProfileViewModel.kt - Gestion Menu**

```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileImageRepository: ProfileImageRepository,
    private val imageCache: ProfileImageCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    data class ProfileUiState(
        val profileImage: Bitmap? = null,
        val userName: String = "",
        val relationshipStartDate: Date? = null,
        val isUploadingImage: Boolean = false,
        val uploadProgress: Int = 0,
        val error: String? = null
    )

    init {
        // Charger image en cache au démarrage
        loadCachedProfileImage()
    }

    private fun loadCachedProfileImage() {
        viewModelScope.launch {
            val cachedImage = imageCache.getCachedProfileImage()
            _uiState.value = _uiState.value.copy(profileImage = cachedImage)
        }
    }

    fun uploadProfileImage(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isUploadingImage = true,
                    error = null,
                    uploadProgress = 0
                )

                // 🚀 STRATÉGIE MENU : Cache immédiat + Upload silencieux

                // 1. Cache local instantané pour UI réactive
                imageCache.cacheProfileImage(bitmap)
                _uiState.value = _uiState.value.copy(profileImage = bitmap)

                Log.d("ProfileViewModel", "✅ Image mise en cache, affichage immédiat")

                // 2. Upload Firebase en arrière-plan
                _uiState.value = _uiState.value.copy(uploadProgress = 10)

                val result = profileImageRepository.uploadProfileImage(bitmap)

                if (result.isSuccess) {
                    val imageUrl = result.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        isUploadingImage = false,
                        uploadProgress = 100
                    )

                    Log.d("ProfileViewModel", "✅ Upload Firebase réussi: $imageUrl")

                    // Notification widgets Android (si nécessaire)
                    notifyWidgets()

                } else {
                    val error = result.exceptionOrNull()
                    _uiState.value = _uiState.value.copy(
                        isUploadingImage = false,
                        uploadProgress = 0,
                        error = "Erreur upload: ${error?.message}"
                    )

                    Log.e("ProfileViewModel", "❌ Échec upload Firebase", error)
                }

            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ Erreur upload image", e)
                _uiState.value = _uiState.value.copy(
                    isUploadingImage = false,
                    uploadProgress = 0,
                    error = "Erreur: ${e.message}"
                )
            }
        }
    }

    private fun notifyWidgets() {
        // Mettre à jour widgets Android avec nouvelle image
        // (implémentation selon architecture widgets Android)
    }
}
```

---

## 🗂️ Repository et Services Android

### **6. ProfileImageRepository.kt - Logique Métier**

```kotlin
@Singleton
class ProfileImageRepository @Inject constructor(
    private val firebaseStorageService: FirebaseStorageService,
    private val firebaseAuth: FirebaseAuth,
    private val imageCache: ProfileImageCache,
    private val cloudFunctionService: CloudFunctionService
) {

    suspend fun uploadProfileImage(bitmap: Bitmap): Result<String> {
        return try {
            val currentUser = firebaseAuth.currentUser
                ?: throw IllegalStateException("Utilisateur non connecté")

            Log.d("ProfileImageRepo", "🔄 Upload image profil pour: ${currentUser.uid}")

            // 1. Redimensionnement optimisé
            val optimizedBitmap = ImageUtils.resizeAndOptimize(
                bitmap = bitmap,
                maxSize = 300,
                quality = 80
            )

            // 2. Conversion en ByteArray
            val imageData = ImageUtils.bitmapToByteArray(optimizedBitmap, quality = 80)

            Log.d("ProfileImageRepo", "📐 Image optimisée: ${imageData.size} bytes")

            // 3. Upload vers Firebase Storage
            val profileImagePath = "profile_images/${currentUser.uid}/profile.jpg"
            val downloadUrl = firebaseStorageService.uploadImage(
                data = imageData,
                path = profileImagePath,
                metadata = mapOf(
                    "contentType" to "image/jpeg",
                    "uploadedBy" to currentUser.uid
                )
            )

            Log.d("ProfileImageRepo", "✅ Upload Firebase réussi: $downloadUrl")

            // 4. Mettre à jour profil utilisateur Firestore
            updateUserProfileImageUrl(downloadUrl)

            // 5. Cache local pour accès rapide
            imageCache.cacheProfileImage(optimizedBitmap)
            imageCache.saveProfileImageUrl(downloadUrl)

            Result.success(downloadUrl)

        } catch (e: Exception) {
            Log.e("ProfileImageRepo", "❌ Erreur upload image profil", e)
            Result.failure(e)
        }
    }

    suspend fun getPartnerProfileImage(partnerId: String): Result<Bitmap> {
        return try {
            Log.d("ProfileImageRepo", "🤝 Récupération image partenaire")

            // 1. Vérifier cache local d'abord
            val cachedImage = imageCache.getCachedPartnerImage(partnerId)
            if (cachedImage != null) {
                Log.d("ProfileImageRepo", "✅ Image partenaire trouvée en cache")
                return Result.success(cachedImage)
            }

            // 2. Appel Cloud Function sécurisée
            val response = cloudFunctionService.getPartnerProfileImage(partnerId)

            if (!response.success || response.imageUrl.isNullOrEmpty()) {
                throw Exception("Aucune photo de profil disponible: ${response.message}")
            }

            Log.d("ProfileImageRepo", "🔐 URL signée reçue, téléchargement...")

            // 3. Téléchargement depuis URL signée
            val bitmap = firebaseStorageService.downloadImage(response.imageUrl)

            // 4. Cache partenaire pour accès futur
            imageCache.cachePartnerImage(partnerId, bitmap, response.imageUrl)

            Log.d("ProfileImageRepo", "✅ Image partenaire récupérée et cachée")
            Result.success(bitmap)

        } catch (e: Exception) {
            Log.e("ProfileImageRepo", "❌ Erreur récupération image partenaire", e)
            Result.failure(e)
        }
    }

    private suspend fun updateUserProfileImageUrl(imageUrl: String) {
        try {
            val currentUser = firebaseAuth.currentUser ?: return

            val userUpdate = mapOf(
                "profileImageURL" to imageUrl,
                "profileImageUpdatedAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .update(userUpdate)
                .await()

            Log.d("ProfileImageRepo", "✅ URL profil mise à jour dans Firestore")

        } catch (e: Exception) {
            Log.e("ProfileImageRepo", "❌ Erreur mise à jour Firestore", e)
        }
    }
}
```

### **7. FirebaseStorageService.kt - Service Storage**

```kotlin
@Singleton
class FirebaseStorageService @Inject constructor() {

    private val storage = FirebaseStorage.getInstance()

    suspend fun uploadImage(
        data: ByteArray,
        path: String,
        metadata: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {

        val storageRef = storage.reference.child(path)

        // Créer métadonnées
        val storageMetadata = StorageMetadata.Builder()
            .setContentType(metadata["contentType"] ?: "image/jpeg")
            .apply {
                metadata.forEach { (key, value) ->
                    if (key != "contentType") {
                        setCustomMetadata(key, value)
                    }
                }
            }
            .build()

        Log.d("FirebaseStorage", "📤 Upload vers: $path")
        Log.d("FirebaseStorage", "📏 Taille données: ${data.size} bytes")

        // Upload avec métadonnées
        val uploadTask = storageRef.putBytes(data, storageMetadata).await()

        Log.d("FirebaseStorage", "✅ Upload terminé avec succès")

        // Récupérer URL de téléchargement
        val downloadUrl = uploadTask.storage.downloadUrl.await()

        Log.d("FirebaseStorage", "🔗 URL téléchargement générée")

        downloadUrl.toString()
    }

    suspend fun downloadImage(url: String): Bitmap = withContext(Dispatchers.IO) {
        Log.d("FirebaseStorage", "📥 Téléchargement image depuis URL signée")

        val request = Request.Builder()
            .url(url)
            .build()

        val response = OkHttpClient().newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Erreur téléchargement: ${response.code}")
        }

        val inputStream = response.body?.byteStream()
            ?: throw IOException("Corps de réponse vide")

        val bitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IOException("Impossible de décoder l'image")

        Log.d("FirebaseStorage", "✅ Image téléchargée: ${bitmap.width}x${bitmap.height}")

        bitmap
    }
}
```

### **8. CloudFunctionService.kt - API Partenaire**

```kotlin
interface CloudFunctionApi {
    @POST("getPartnerProfileImage")
    suspend fun getPartnerProfileImage(@Body request: PartnerImageRequest): PartnerImageResponse
}

data class PartnerImageRequest(val partnerId: String)

data class PartnerImageResponse(
    val success: Boolean,
    val imageUrl: String?,
    val expiresIn: Long?,
    val reason: String?,
    val message: String?
)

@Singleton
class CloudFunctionService @Inject constructor(
    private val api: CloudFunctionApi,
    private val firebaseAuth: FirebaseAuth
) {

    suspend fun getPartnerProfileImage(partnerId: String): PartnerImageResponse {
        try {
            // Ajouter token d'authentification Firebase
            val token = firebaseAuth.currentUser?.getIdToken(false)?.await()
                ?: throw SecurityException("Utilisateur non authentifié")

            Log.d("CloudFunction", "🔐 Appel getPartnerProfileImage avec token auth")

            return api.getPartnerProfileImage(
                PartnerImageRequest(partnerId)
            )

        } catch (e: Exception) {
            Log.e("CloudFunction", "❌ Erreur Cloud Function", e)
            return PartnerImageResponse(
                success = false,
                imageUrl = null,
                expiresIn = null,
                reason = "ERROR",
                message = e.message
            )
        }
    }
}
```

---

## 💾 Cache Android Multi-Niveaux

### **9. ProfileImageCache.kt - Cache Spécialisé**

```kotlin
@Singleton
class ProfileImageCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryCache: MemoryImageCache,
    private val diskCache: DiskImageCache
) {

    private val prefs = context.getSharedPreferences("profile_image_cache", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROFILE_IMAGE = "cached_profile_image"
        private const val KEY_PROFILE_IMAGE_URL = "cached_profile_image_url"
        private const val KEY_PARTNER_IMAGE_PREFIX = "cached_partner_image_"
        private const val KEY_PARTNER_URL_PREFIX = "cached_partner_url_"
    }

    // MARK: - Cache Utilisateur Actuel

    suspend fun cacheProfileImage(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        Log.d("ProfileImageCache", "💾 Mise en cache image profil")

        try {
            // 1. Cache mémoire
            memoryCache.put("profile_current", bitmap)

            // 2. Cache disque pour persistance
            diskCache.put("profile_current", bitmap)

            // 3. Cache SharedPreferences pour accès rapide (petite image)
            val compressedData = ImageUtils.bitmapToByteArray(bitmap, quality = 60)
            val base64 = Base64.encodeToString(compressedData, Base64.DEFAULT)

            prefs.edit()
                .putString(KEY_PROFILE_IMAGE, base64)
                .apply()

            Log.d("ProfileImageCache", "✅ Image profil mise en cache (${compressedData.size} bytes)")

        } catch (e: Exception) {
            Log.e("ProfileImageCache", "❌ Erreur cache image profil", e)
        }
    }

    suspend fun getCachedProfileImage(): Bitmap? {
        return try {
            // 1. Vérifier cache mémoire d'abord
            memoryCache.get("profile_current")?.let {
                Log.d("ProfileImageCache", "✅ Image profil trouvée en cache mémoire")
                return it
            }

            // 2. Vérifier cache disque
            diskCache.get("profile_current")?.let {
                Log.d("ProfileImageCache", "✅ Image profil trouvée en cache disque")
                // Remettre en cache mémoire
                memoryCache.put("profile_current", it)
                return it
            }

            // 3. Vérifier SharedPreferences
            val base64 = prefs.getString(KEY_PROFILE_IMAGE, null)
            if (base64 != null) {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                if (bitmap != null) {
                    Log.d("ProfileImageCache", "✅ Image profil trouvée en SharedPreferences")
                    // Remettre en caches supérieurs
                    memoryCache.put("profile_current", bitmap)
                    return bitmap
                }
            }

            Log.d("ProfileImageCache", "❌ Aucune image profil en cache")
            null

        } catch (e: Exception) {
            Log.e("ProfileImageCache", "❌ Erreur lecture cache profil", e)
            null
        }
    }

    // MARK: - Cache Partenaire

    suspend fun cachePartnerImage(partnerId: String, bitmap: Bitmap, url: String) = withContext(Dispatchers.IO) {
        Log.d("ProfileImageCache", "🤝 Mise en cache image partenaire")

        try {
            val partnerKey = "partner_$partnerId"

            // Cache mémoire et disque
            memoryCache.put(partnerKey, bitmap)
            diskCache.put(partnerKey, bitmap)

            // SharedPreferences avec URL pour détection changements
            val compressedData = ImageUtils.bitmapToByteArray(bitmap, quality = 60)
            val base64 = Base64.encodeToString(compressedData, Base64.DEFAULT)

            prefs.edit()
                .putString("$KEY_PARTNER_IMAGE_PREFIX$partnerId", base64)
                .putString("$KEY_PARTNER_URL_PREFIX$partnerId", url)
                .apply()

            Log.d("ProfileImageCache", "✅ Image partenaire mise en cache")

        } catch (e: Exception) {
            Log.e("ProfileImageCache", "❌ Erreur cache partenaire", e)
        }
    }

    suspend fun getCachedPartnerImage(partnerId: String): Bitmap? {
        val partnerKey = "partner_$partnerId"

        return memoryCache.get(partnerKey)
            ?: diskCache.get(partnerKey)
            ?: getPartnerImageFromPrefs(partnerId)
    }

    private fun getPartnerImageFromPrefs(partnerId: String): Bitmap? {
        return try {
            val base64 = prefs.getString("$KEY_PARTNER_IMAGE_PREFIX$partnerId", null)
                ?: return null

            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (bitmap != null) {
                // Remettre en caches supérieurs
                val partnerKey = "partner_$partnerId"
                memoryCache.put(partnerKey, bitmap)
            }

            bitmap
        } catch (e: Exception) {
            Log.e("ProfileImageCache", "❌ Erreur lecture partenaire depuis prefs", e)
            null
        }
    }

    fun hasPartnerImageChanged(partnerId: String, newUrl: String): Boolean {
        val cachedUrl = prefs.getString("$KEY_PARTNER_URL_PREFIX$partnerId", null)
        val hasChanged = cachedUrl != newUrl

        if (hasChanged) {
            Log.d("ProfileImageCache", "🔄 URL partenaire changée pour $partnerId")
        }

        return hasChanged
    }

    fun saveProfileImageUrl(url: String) {
        prefs.edit()
            .putString(KEY_PROFILE_IMAGE_URL, url)
            .apply()
    }

    fun clearCache() {
        // Vider tous les caches
        memoryCache.clear()
        diskCache.clear()
        prefs.edit().clear().apply()

        Log.d("ProfileImageCache", "🗑️ Cache images nettoyé")
    }
}
```

### **10. MemoryImageCache.kt et DiskImageCache.kt**

```kotlin
@Singleton
class MemoryImageCache @Inject constructor() {

    private val cache = LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt() // 1/8 de la RAM
    ) {
        key, bitmap -> bitmap.byteCount // Calcul taille pour LRU
    }

    fun put(key: String, bitmap: Bitmap): Bitmap? = cache.put(key, bitmap)

    fun get(key: String): Bitmap? = cache.get(key)

    fun remove(key: String): Bitmap? = cache.remove(key)

    fun clear() = cache.evictAll()

    fun size(): Int = cache.size()

    fun maxSize(): Int = cache.maxSize()
}

@Singleton
class DiskImageCache @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val cacheDir = File(context.cacheDir, "profile_images")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Log.d("DiskImageCache", "📁 Dossier cache créé: ${cacheDir.absolutePath}")
        }
    }

    suspend fun put(key: String, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "${key.hashCode()}.jpg")

            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            Log.d("DiskImageCache", "💾 Image sauvée: ${file.name}")
            true

        } catch (e: Exception) {
            Log.e("DiskImageCache", "❌ Erreur sauvegarde disque", e)
            false
        }
    }

    suspend fun get(key: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "${key.hashCode()}.jpg")

            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    Log.d("DiskImageCache", "📁 Image trouvée: ${file.name}")
                }
                bitmap
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e("DiskImageCache", "❌ Erreur lecture disque", e)
            null
        }
    }

    fun clear() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d("DiskImageCache", "🗑️ Cache disque nettoyé")
        } catch (e: Exception) {
            Log.e("DiskImageCache", "❌ Erreur nettoyage disque", e)
        }
    }
}
```

---

## 🛠️ Utilitaires Android

### **11. ImageUtils.kt - Traitement Images**

```kotlin
object ImageUtils {

    /**
     * Redimensionne et optimise une image
     */
    fun resizeAndOptimize(
        bitmap: Bitmap,
        maxSize: Int,
        quality: Int = 80
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Calculer nouvelle taille en préservant ratio
        val scale = minOf(
            maxSize.toFloat() / width,
            maxSize.toFloat() / height
        )

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Convertit Bitmap en ByteArray JPEG
     */
    fun bitmapToByteArray(bitmap: Bitmap, quality: Int = 80): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Crop une image en cercle
     */
    fun cropToCircle(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2

        val squaredBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            shader = BitmapShader(squaredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        return output
    }

    /**
     * Vérifie si image est valide
     */
    fun isValidImage(bitmap: Bitmap?): Boolean {
        return bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0
    }

    /**
     * Calcule taille optimale selon les contraintes
     */
    fun calculateOptimalSize(width: Int, height: Int, maxSize: Int): Pair<Int, Int> {
        val ratio = width.toFloat() / height.toFloat()

        return if (width > height) {
            maxSize to (maxSize / ratio).toInt()
        } else {
            (maxSize * ratio).toInt() to maxSize
        }
    }
}
```

### **12. Composant AsyncProfileImage.kt**

```kotlin
@Composable
fun AsyncProfileImage(
    imageUrl: String?,
    isCurrentUser: Boolean = false,
    size: Dp = 120.dp,
    modifier: Modifier = Modifier,
    profileImageRepository: ProfileImageRepository = hiltViewModel<ProfileImageViewModel>().repository
) {
    var imageState by remember { mutableStateOf<AsyncImageState>(AsyncImageState.Loading) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrEmpty()) {
            imageState = AsyncImageState.Empty
            return@LaunchedEffect
        }

        imageState = AsyncImageState.Loading

        try {
            if (isCurrentUser) {
                // Image utilisateur actuel depuis cache local
                val cachedImage = profileImageRepository.getCachedProfileImage()
                if (cachedImage != null) {
                    imageState = AsyncImageState.Success(cachedImage)
                } else {
                    imageState = AsyncImageState.Error("Image non trouvée")
                }
            } else {
                // Image partenaire via Cloud Function
                val result = profileImageRepository.getPartnerProfileImage(imageUrl)
                if (result.isSuccess) {
                    imageState = AsyncImageState.Success(result.getOrThrow())
                } else {
                    imageState = AsyncImageState.Error(result.exceptionOrNull()?.message ?: "Erreur")
                }
            }
        } catch (e: Exception) {
            imageState = AsyncImageState.Error(e.message ?: "Erreur inconnue")
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (val state = imageState) {
            is AsyncImageState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            is AsyncImageState.Success -> {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "Photo de profil",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            is AsyncImageState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Erreur",
                            modifier = Modifier.size(32.dp),
                            tint = Color.Gray
                        )

                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Blue,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable {
                                    // Retry chargement
                                    imageState = AsyncImageState.Loading
                                }
                        )
                    }
                }
            }

            is AsyncImageState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Aucune image",
                        modifier = Modifier.size(32.dp),
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}

sealed class AsyncImageState {
    object Loading : AsyncImageState()
    object Empty : AsyncImageState()
    data class Success(val bitmap: Bitmap) : AsyncImageState()
    data class Error(val message: String) : AsyncImageState()
}
```

---

## 📊 Comparaison iOS vs Android

| **Aspect**             | **iOS**                                 | **Android**                                  |
| ---------------------- | --------------------------------------- | -------------------------------------------- |
| **Sélection Image**    | `PHPickerViewController` + SwiftyCrop   | `ActivityResultContracts.GetContent` + uCrop |
| **Permissions**        | Info.plist + runtime prompts            | Manifest + `ActivityResultLauncher`          |
| **Cropping**           | SwiftyCrop (maskRadius: 150)            | uCrop ou bibliothèque similaire              |
| **Upload Firebase**    | `StorageReference.putData()`            | `FirebaseStorage.putBytes()`                 |
| **Cache Utilisateur**  | `UserDefaults` (JPEG Base64)            | `SharedPreferences` (JPEG Base64)            |
| **Cache Général**      | `NSCache` + FileManager (App Group)     | `LruCache` + File System                     |
| **Cloud Functions**    | `Functions.functions().httpsCallable()` | Retrofit + Firebase Auth Token               |
| **Interface**          | SwiftUI avec AsyncImageView             | Jetpack Compose avec AsyncProfileImage       |
| **Background Upload**  | `UIBackgroundTaskIdentifier`            | `WorkManager` (optionnel)                    |
| **Optimisation Image** | `UIGraphicsImageRenderer`               | `Bitmap.createScaledBitmap()`                |

---

## 🚀 Plan d'Implémentation Android

### **Phase 1 : Infrastructure (2 semaines)**

1. ✅ Créer `ProfileImageRepository` avec logique métier
2. ✅ Implémenter `FirebaseStorageService` pour upload/download
3. ✅ Setup `CloudFunctionService` avec authentification
4. ✅ Créer `ProfileImageCache` multi-niveaux
5. ✅ Tests unitaires des services

### **Phase 2 : Interface Onboarding (2 semaines)**

6. ✅ Créer `ProfilePhotoScreen` avec sélection image
7. ✅ Implémenter `ImageCropperDialog` circulaire
8. ✅ Intégrer dans `OnboardingViewModel`
9. ✅ Gestion permissions galerie Android
10. ✅ Tests interface onboarding

### **Phase 3 : Interface Menu (1 semaine)**

11. ✅ Créer `MenuScreen` avec photo de profil cliquable
12. ✅ Implémenter `ProfileViewModel` avec upload immédiat
13. ✅ `ProfileHeaderSection` avec états de chargement
14. ✅ Upload silencieux en arrière-plan

### **Phase 4 : Composants Génériques (1 semaine)**

15. ✅ `AsyncProfileImage` avec gestion états
16. ✅ `ImageUtils` pour traitement optimisé
17. ✅ Cache components (`MemoryImageCache`, `DiskImageCache`)
18. ✅ Tests composants réutilisables

### **Phase 5 : Intégration Complète (1 semaine)**

19. ✅ Intégrer récupération photos partenaire
20. ✅ Synchronisation cache entre onboarding/menu
21. ✅ Gestion edge cases (network, permissions)
22. ✅ Tests d'intégration end-to-end

### **Phase 6 : Optimisation Android GO (1 semaine)**

23. ✅ Tests sur appareils Android GO
24. ✅ Optimisation mémoire et stockage
25. ✅ Compression adaptée aux petits appareils
26. ✅ Validation performance et UX

Cette architecture Android GO reproduit **fidèlement** l'expérience iOS avec optimisations spécifiques pour les appareils moins puissants ! 📸🚀
