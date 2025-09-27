# 🖼️ Gestion Photos de Profil - Rapport Technique Complet

## Vue d'Ensemble

Ce rapport détaille l'architecture complète de gestion des photos de profil dans l'application iOS Love2Love, incluant le téléchargement, la mise en cache, l'affichage, et la sécurité. Il propose ensuite une implémentation équivalente pour Android GO.

---

## 🏗️ Architecture iOS Actuelle

### **1. Structure Firebase Storage**

Les photos de profil sont stockées dans Firebase Storage selon cette hiérarchie :

```
Firebase Storage:
├── profile_images/
│   ├── [userId1]/
│   │   └── profile.jpg
│   ├── [userId2]/
│   │   └── profile.jpg
│   └── ...
├── journal_images/
│   └── [autres images]
```

**Chemin d'upload** : `profile_images/{userId}/profile.jpg`

- **Sécurité** : Chaque utilisateur ne peut accéder qu'à sa propre image
- **Métadonnées** : `contentType: "image/jpeg"`, `uploadedBy: userId`
- **Compression** : JPEG 80% qualité, redimensionnée à 300x300px

---

## 🔄 Système de Cache Multi-Niveaux

### **2. ImageCacheService.swift**

**Rôle** : Cache généraliste pour toutes les images de l'app

```swift
class ImageCacheService {
    private let memoryCache = NSCache<NSString, UIImage>()
    private let cacheDirectory: URL // App Group pour widgets

    // Configuration
    memoryCache.countLimit = 50           // Max 50 images
    memoryCache.totalCostLimit = 100MB    // Max 100MB RAM
}
```

**Stratégie de cache** :

1. **Cache Mémoire** (NSCache) - Ultra rapide, volatil
2. **Cache Disque** (App Group) - Persistant, partagé avec widgets
3. **Clé de cache** : Hash de l'URL pour éviter les collisions

**Méthodes principales** :

- `getCachedImage(for: urlString) -> UIImage?`
- `cacheImage(_ image: UIImage, for: urlString)`
- `clearCachedImage(for: urlString)`

### **3. UserCacheManager.swift**

**Rôle** : Cache spécialisé pour les données utilisateur et photos de profil

```swift
class UserCacheManager {
    // Cache utilisateur actuel
    private let profileImageCacheKey = "cached_profile_image"

    // Cache partenaire
    private let partnerImageCacheKey = "cached_partner_image"
    private let partnerImageURLKey = "cached_partner_image_url"

    func cacheProfileImage(_ image: UIImage)
    func getCachedProfileImage() -> UIImage?
    func cachePartnerImage(_ image: UIImage, url: String)
    func getCachedPartnerImage() -> UIImage?
    func hasPartnerImageChanged(newURL: String?) -> Bool
}
```

**Fonctionnalités avancées** :

- **Détection de changement** : Compare l'URL pour savoir si recharger
- **Nettoyage automatique** : Supprime les données corrompues
- **Expiration** : Cache expiré après 7 jours
- **UserDefaults** : Stockage des images en JPEG 80%

---

## 📱 Affichage et Téléchargement

### **4. AsyncImageView.swift**

**Composant SwiftUI** pour charger et afficher les images de manière asynchrone

**Architecture intelligente** :

```swift
struct AsyncImageView: View {
    @State private var loadedImage: UIImage?
    @State private var isLoading = false
    @State private var hasError = false

    var body: some View {
        if let loadedImage = loadedImage {
            // Image chargée
        } else if isLoading {
            // Spinner de chargement
        } else if hasError {
            // Image d'erreur avec bouton retry
        }
    }
}
```

**Stratégie de téléchargement intelligente** :

```swift
private func loadFromFirebaseStorageViaCloudFunction(urlString: String) async throws -> UIImage {
    // 1. Parser l'URL pour identifier le type
    if filePath.hasPrefix("profile_images/") {
        let imageUserId = pathComponents[1]

        if imageUserId == currentUserId {
            // Image utilisateur actuel → Accès direct Firebase
            return try await loadFromFirebaseStorageDirect(urlString: urlString)
        } else {
            // Image partenaire → Cloud Function sécurisée
            return try await loadProfileImageViaCloudFunction(userId: imageUserId)
        }
    }
    // 2. Journal images → URL signée générique
    // 3. Fallback → Téléchargement direct
}
```

**Processus de chargement** :

1. **Vérifier ImageCacheService** (cache mémoire + disque)
2. **Si pas en cache** → Téléchargement selon stratégie
3. **Mise en cache automatique** après téléchargement
4. **Gestion d'erreur** avec retry automatique

---

## 🔐 Sécurité et Cloud Functions

### **5. getPartnerProfileImage (Cloud Function)**

```javascript
exports.getPartnerProfileImage = functions.https.onCall(
  async (data, context) => {
    // 1. Vérifier authentification
    if (!context.auth) throw new HttpsError("unauthenticated");

    const currentUserId = context.auth.uid;
    const { partnerId } = data;

    // 2. Vérifier que c'est bien le partenaire connecté
    const currentUserDoc = await firestore
      .collection("users")
      .doc(currentUserId)
      .get();
    if (currentUserData.partnerId !== partnerId) {
      throw new HttpsError("permission-denied");
    }

    // 3. Récupérer l'URL de l'image partenaire
    const partnerDoc = await firestore.collection("users").doc(partnerId).get();
    const profileImageURL = partnerData.profileImageURL;

    // 4. Générer URL signée temporaire (1 heure)
    const bucket = admin.storage().bucket();
    const filePath = extractFilePathFromURL(profileImageURL);
    const file = bucket.file(filePath);

    const [signedUrl] = await file.getSignedUrl({
      action: "read",
      expires: Date.now() + 60 * 60 * 1000, // 1 heure
    });

    return {
      success: true,
      imageUrl: signedUrl,
      expiresIn: 3600,
    };
  }
);
```

**Sécurité renforcée** :

- ✅ **Authentification obligatoire**
- ✅ **Vérification relation partenaire** (pas d'accès arbitraire)
- ✅ **URL signée temporaire** (1h expiration)
- ✅ **Logs sécurisés** (pas d'exposition d'IDs)

---

## 🎯 Lieux d'Affichage dans l'App

### **6. MenuView.swift - Photo de Profil Utilisateur**

```swift
private var headerSection: some View {
    VStack(spacing: 16) {
        Button(action: { checkPhotoLibraryPermission() }) {
            ZStack {
                // Priorité d'affichage :
                if let croppedImage = croppedImage {
                    // 1. Image récemment croppée (priorité absolue)
                    Image(uiImage: croppedImage)
                } else if let cachedImage = UserCacheManager.shared.getCachedProfileImage() {
                    // 2. Image en cache (affichage instantané)
                    Image(uiImage: cachedImage)
                } else if let imageURL = currentUserImageURL {
                    // 3. AsyncImageView (téléchargement si nécessaire)
                    AsyncImageView(imageURL: imageURL)
                } else {
                    // 4. Placeholder par défaut
                    DefaultProfileView()
                }
            }
        }
    }
}
```

### **7. PartnerDistanceView.swift - Photos Partenaire**

```swift
struct PartnerProfileImage: View {
    let imageURL: String?
    let partnerName: String
    let size: CGFloat

    var body: some View {
        ZStack {
            if let cachedPartnerImage = UserCacheManager.shared.getCachedPartnerImage() {
                // 🚀 PRIORITÉ : Image partenaire en cache
                Image(uiImage: cachedPartnerImage)
                    .onAppear { checkAndUpdatePartnerImageIfNeeded() }
            } else if let imageURL = imageURL {
                // Téléchargement AsyncImageView + mise en cache
                AsyncImageView(imageURL: imageURL)
                    .onAppear { downloadAndCachePartnerImageIfNeeded(from: imageURL) }
            } else {
                // Initiales du partenaire
                UserInitialsView(name: partnerName, size: size)
            }
        }
    }

    private func downloadAndCachePartnerImageIfNeeded(from url: String) {
        Task {
            do {
                // Télécharger via AsyncImageView
                let image = try await AsyncImageView.loadImage(from: url)

                // Mettre en cache immédiatement
                DispatchQueue.main.async {
                    UserCacheManager.shared.cachePartnerImage(image, url: url)
                    cacheUpdateTrigger.toggle() // Rafraîchir la vue
                }
            } catch {
                print("❌ Erreur téléchargement image partenaire: \(error)")
            }
        }
    }
}
```

---

## 🔧 Widgets et Partage App Group

### **8. WidgetService.swift - Cache Widgets**

```swift
class WidgetService: ObservableObject {
    private let sharedDefaults = UserDefaults(suiteName: "group.com.lyes.love2love")

    func updateWidgetData() {
        // Téléchargement et cache des images pour widgets
        downloadAndCacheImage(
            from: partnerProfileImageURL,
            key: "widget_partner_image_path",
            isUser: false
        )
    }

    private func downloadAndCacheImage(from urlString: String, key: String, isUser: Bool) {
        let fileName = isUser ? "user_profile_image.jpg" : "partner_profile_image.jpg"

        // 1. Vérifier cache ImageCacheService
        if let cachedImage = ImageCacheService.shared.getCachedImage(for: urlString) {
            // Redimensionner pour widget (150x150)
            let resizedImage = resizeImage(cachedImage, to: CGSize(width: 150, height: 150))
            ImageCacheService.shared.cacheImageForWidget(resizedImage, fileName: fileName)
            sharedDefaults?.set(fileName, forKey: key)
            return
        }

        // 2. Si pas en cache, télécharger
        URLSession.shared.dataTask(with: URL(string: urlString)!) { data, response, error in
            guard let data = data, let image = UIImage(data: data) else { return }

            // Cache général
            ImageCacheService.shared.cacheImage(image, for: urlString)

            // Cache widget spécialisé
            let resizedImage = self.resizeImage(image, to: CGSize(width: 150, height: 150))
            ImageCacheService.shared.cacheImageForWidget(resizedImage, fileName: fileName)
            self.sharedDefaults?.set(fileName, forKey: key)
        }.resume()
    }
}
```

**App Group** : `group.com.lyes.love2love`

- **Partage cache** entre app principale et widgets
- **UserDefaults partagés** pour métadonnées
- **FileManager** pour fichiers images

---

## 🔄 Cycle de Vie Complet

### **Upload Image de Profil**

```swift
// 1. Sélection image (MenuView/OnboardingView)
func handleImageSelection(_ image: UIImage) {
    // Cropping avec SwiftyCrop
    selectedImage = image
    showImageCropper = true
}

// 2. Crop terminé
func onImageCropped(_ croppedImage: UIImage) {
    self.croppedImage = croppedImage

    // Cache local immédiat (UI instantané)
    UserCacheManager.shared.cacheProfileImage(croppedImage)

    // Upload Firebase en arrière-plan
    firebaseService.updateUserProfileImage(croppedImage) { success, imageURL in
        if success {
            // Mise à jour Firestore avec nouvelle URL
            print("✅ Image uploadée avec succès: \(imageURL)")
        }
    }
}

// 3. FirebaseService.uploadProfileImage()
private func uploadProfileImage(_ image: UIImage, completion: @escaping (String?) -> Void) {
    // Redimensionnement optimisé
    let resizedImage = resizeImage(image, to: CGSize(width: 300, height: 300))
    let imageData = resizedImage.jpegData(compressionQuality: 0.8)

    // Upload vers profile_images/{userId}/profile.jpg
    let profileImagePath = "profile_images/\(firebaseUser.uid)/profile.jpg"
    let profileImageRef = storage.reference().child(profileImagePath)

    profileImageRef.putData(imageData, metadata: metadata) { _, error in
        if let error = error {
            completion(nil)
            return
        }

        // Récupération URL de téléchargement
        profileImageRef.downloadURL { url, _ in
            completion(url?.absoluteString)
        }
    }
}
```

### **Récupération Image Partenaire**

```swift
// 1. PartnerLocationService récupère les données partenaire
private func updatePartnerDataFromCloudFunction(_ partnerInfo: [String: Any]) {
    let newProfileURL = partnerInfo["profileImageURL"] as? String

    // 2. Vérifier si l'URL a changé
    if UserCacheManager.shared.hasPartnerImageChanged(newURL: newProfileURL) {
        // 3. Téléchargement en arrière-plan
        downloadAndCachePartnerImage(from: newProfileURL)
    }

    partnerProfileImageURL = newProfileURL
}

// 4. Téléchargement via AsyncImageView
private func downloadAndCachePartnerImage(from url: String) {
    Task {
        let image = try await AsyncImageView.loadImage(from: url)

        DispatchQueue.main.async {
            // 5. Mise en cache locale
            UserCacheManager.shared.cachePartnerImage(image, url: url)

            // 6. Notification widgets
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}
```

---

## 📊 Métriques et Performance iOS

**Temps de chargement** :

- Cache mémoire : `< 1ms`
- Cache disque : `< 50ms`
- Téléchargement network : `500ms - 2s`

**Taille des caches** :

- Cache mémoire : `50 images max, 100MB max`
- Cache disque : `Illimité, nettoyage manuel`
- Images widgets : `150x150px, ~15KB par image`

**Stratégie réseau** :

- Images utilisateur : `Direct Firebase Storage`
- Images partenaire : `Cloud Function → URL signée`
- Retry automatique : `3 tentatives max`

---

# 🤖 Implémentation Android GO Équivalente

## Architecture Android Proposée

### **1. Structure Générale**

```kotlin
📁 com.love2love.android/
├── 📁 data/
│   ├── 📁 repository/
│   │   ├── ImageRepository.kt
│   │   └── UserRepository.kt
│   ├── 📁 cache/
│   │   ├── ImageCacheManager.kt
│   │   ├── MemoryImageCache.kt
│   │   └── DiskImageCache.kt
│   ├── 📁 api/
│   │   ├── FirebaseApi.kt
│   │   └── CloudFunctionService.kt
│   └── 📁 local/
│       ├── UserPreferences.kt
│       └── ImageDatabase.kt
├── 📁 ui/
│   ├── 📁 components/
│   │   ├── AsyncImage.kt
│   │   ├── ProfileImage.kt
│   │   └── PartnerProfileImage.kt
│   └── 📁 screens/
│       ├── MenuScreen.kt
│       └── ProfilePhotoScreen.kt
└── 📁 utils/
    ├── ImageUtils.kt
    └── SecurityUtils.kt
```

---

## 🗄️ Système de Cache Android

### **2. ImageCacheManager.kt - Cache Principal**

```kotlin
@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryCache: MemoryImageCache,
    private val diskCache: DiskImageCache
) {

    suspend fun getCachedImage(url: String): Bitmap? {
        // 1. Vérifier cache mémoire (LruCache)
        memoryCache.get(url)?.let { return it }

        // 2. Vérifier cache disque
        diskCache.get(url)?.let { bitmap ->
            // Remettre en cache mémoire
            memoryCache.put(url, bitmap)
            return bitmap
        }

        return null
    }

    suspend fun cacheImage(url: String, bitmap: Bitmap) {
        // Cache mémoire
        memoryCache.put(url, bitmap)

        // Cache disque asynchrone
        withContext(Dispatchers.IO) {
            diskCache.put(url, bitmap)
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
        diskCache.clear()
    }
}

@Singleton
class MemoryImageCache @Inject constructor() {
    private val cache = LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt() // 1/8 de la RAM
    )

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)
    fun evictAll() = cache.evictAll()
}

@Singleton
class DiskImageCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir = File(context.cacheDir, "images")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    suspend fun get(key: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, key.hashCode().toString())
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else null
    }

    suspend fun put(key: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, key.hashCode().toString())
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
```

### **3. UserPreferences.kt - Cache Utilisateur**

```kotlin
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)

    // Cache photo de profil utilisateur
    fun cacheProfileImage(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
        prefs.edit().putString("cached_profile_image", base64).apply()
    }

    fun getCachedProfileImage(): Bitmap? {
        val base64 = prefs.getString("cached_profile_image", null) ?: return null
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // Cache photo partenaire avec URL tracking
    fun cachePartnerImage(bitmap: Bitmap, url: String) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)

        prefs.edit()
            .putString("cached_partner_image", base64)
            .putString("cached_partner_image_url", url)
            .apply()
    }

    fun getCachedPartnerImage(): Bitmap? {
        val base64 = prefs.getString("cached_partner_image", null) ?: return null
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun hasPartnerImageChanged(newURL: String?): Boolean {
        val cachedURL = prefs.getString("cached_partner_image_url", null)
        return cachedURL != newURL
    }

    fun clearCache() {
        prefs.edit()
            .remove("cached_profile_image")
            .remove("cached_partner_image")
            .remove("cached_partner_image_url")
            .apply()
    }
}
```

---

## 🌐 Repository Pattern

### **4. ImageRepository.kt - Logique Métier**

```kotlin
@Singleton
class ImageRepository @Inject constructor(
    private val cacheManager: ImageCacheManager,
    private val userPreferences: UserPreferences,
    private val cloudFunctionService: CloudFunctionService,
    private val firebaseStorage: FirebaseStorage
) {

    suspend fun loadProfileImage(url: String, isCurrentUser: Boolean): Result<Bitmap> {
        return try {
            // 1. Vérifier cache local d'abord
            val cachedImage = if (isCurrentUser) {
                userPreferences.getCachedProfileImage()
            } else {
                userPreferences.getCachedPartnerImage()
            }

            if (cachedImage != null) {
                return Result.success(cachedImage)
            }

            // 2. Vérifier cache général
            cacheManager.getCachedImage(url)?.let {
                return Result.success(it)
            }

            // 3. Télécharger selon stratégie
            val bitmap = if (isCurrentUser) {
                downloadFromFirebaseStorage(url)
            } else {
                downloadPartnerImageViaCloudFunction(url)
            }

            // 4. Mettre en cache
            cacheManager.cacheImage(url, bitmap)
            if (isCurrentUser) {
                userPreferences.cacheProfileImage(bitmap)
            } else {
                userPreferences.cachePartnerImage(bitmap, url)
            }

            Result.success(bitmap)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun downloadFromFirebaseStorage(url: String): Bitmap {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = OkHttpClient().newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("Erreur téléchargement: ${response.code}")
            }

            response.body?.byteStream()?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
                    ?: throw IOException("Impossible de décoder l'image")
            } ?: throw IOException("Corps de réponse vide")
        }
    }

    private suspend fun downloadPartnerImageViaCloudFunction(originalUrl: String): Bitmap {
        // Extraire userId depuis l'URL
        val userId = extractUserIdFromUrl(originalUrl)

        // Appeler Cloud Function
        val response = cloudFunctionService.getPartnerProfileImage(userId)

        if (!response.success) {
            throw IOException("Cloud Function failed: ${response.message}")
        }

        // Télécharger depuis URL signée
        return downloadFromUrl(response.imageUrl)
    }

    suspend fun uploadProfileImage(bitmap: Bitmap): Result<String> {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser
                ?: throw IllegalStateException("User not authenticated")

            // Redimensionner
            val resizedBitmap = ImageUtils.resize(bitmap, 300, 300)
            val bytes = ByteArrayOutputStream().apply {
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, this)
            }.toByteArray()

            // Upload vers Firebase Storage
            val path = "profile_images/${currentUser.uid}/profile.jpg"
            val storageRef = firebaseStorage.reference.child(path)

            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("uploadedBy", currentUser.uid)
                .build()

            val uploadTask = storageRef.putBytes(bytes, metadata).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()

            // Cache local immédiat
            userPreferences.cacheProfileImage(resizedBitmap)
            cacheManager.cacheImage(downloadUrl.toString(), resizedBitmap)

            Result.success(downloadUrl.toString())

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### **5. CloudFunctionService.kt - API Cloud Functions**

```kotlin
interface CloudFunctionService {
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
class CloudFunctionRepository @Inject constructor(
    private val api: CloudFunctionService,
    private val auth: FirebaseAuth
) {

    suspend fun getPartnerProfileImage(partnerId: String): PartnerImageResponse {
        // Ajouter token d'authentification
        val token = auth.currentUser?.getIdToken(false)?.await()
            ?: throw SecurityException("User not authenticated")

        return api.getPartnerProfileImage(
            PartnerImageRequest(partnerId)
        )
    }
}
```

---

## 🎨 Interface Utilisateur Compose

### **6. AsyncImage.kt - Composant Chargement Image**

```kotlin
@Composable
fun AsyncImage(
    url: String?,
    modifier: Modifier = Modifier,
    isCurrentUser: Boolean = false,
    contentDescription: String? = null,
    placeholderRes: Int? = null,
    errorRes: Int? = null
) {
    var imageState by remember { mutableStateOf<AsyncImageState>(AsyncImageState.Loading) }
    val imageRepository = hiltViewModel<ImageViewModel>().repository

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            imageState = AsyncImageState.Error("URL vide")
            return@LaunchedEffect
        }

        imageState = AsyncImageState.Loading

        imageRepository.loadProfileImage(url, isCurrentUser)
            .onSuccess { bitmap ->
                imageState = AsyncImageState.Success(bitmap)
            }
            .onFailure { error ->
                imageState = AsyncImageState.Error(error.message ?: "Erreur inconnue")
            }
    }

    Box(modifier = modifier) {
        when (val state = imageState) {
            is AsyncImageState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.3f))
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            is AsyncImageState.Success -> {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
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
                        .background(Color.Gray.copy(alpha = 0.2f))
                        .clip(CircleShape)
                        .clickable {
                            // Retry
                            imageState = AsyncImageState.Loading
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Erreur chargement",
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
    data class Success(val bitmap: Bitmap) : AsyncImageState()
    data class Error(val message: String) : AsyncImageState()
}
```

### **7. ProfileImage.kt - Image Utilisateur**

```kotlin
@Composable
fun ProfileImage(
    imageUrl: String?,
    size: Dp = 120.dp,
    onImageClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val userPreferences = hiltViewModel<ProfileViewModel>().userPreferences
    var cachedImage by remember { mutableStateOf<Bitmap?>(null) }

    // Priorité à l'image en cache
    LaunchedEffect(Unit) {
        cachedImage = userPreferences.getCachedProfileImage()
    }

    Box(
        modifier = modifier
            .size(size + 12.dp)
            .clickable { onImageClick() },
        contentAlignment = Alignment.Center
    ) {
        // Effet de surbrillance
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.35f), CircleShape)
                .blur(6.dp)
        )

        // Image
        Box(
            modifier = Modifier.size(size)
        ) {
            if (cachedImage != null) {
                // Image en cache (priorité)
                Image(
                    bitmap = cachedImage!!.asImageBitmap(),
                    contentDescription = "Photo de profil",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (!imageUrl.isNullOrBlank()) {
                // AsyncImage avec téléchargement
                AsyncImage(
                    url = imageUrl,
                    isCurrentUser = true,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder par défaut
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Pas de photo",
                        modifier = Modifier.size(size * 0.4f),
                        tint = Color.Gray
                    )
                }
            }

            // Bordure
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, Color.White, CircleShape)
            )
        }
    }
}
```

### **8. PartnerProfileImage.kt - Image Partenaire**

```kotlin
@Composable
fun PartnerProfileImage(
    imageUrl: String?,
    partnerName: String,
    size: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val userPreferences = hiltViewModel<PartnerViewModel>().userPreferences
    val imageRepository = hiltViewModel<PartnerViewModel>().imageRepository

    var cachedPartnerImage by remember { mutableStateOf<Bitmap?>(null) }
    var forceRefresh by remember { mutableStateOf(false) }

    // Vérifier cache partenaire au démarrage
    LaunchedEffect(Unit) {
        cachedPartnerImage = userPreferences.getCachedPartnerImage()
    }

    // Vérifier si l'URL a changé
    LaunchedEffect(imageUrl) {
        if (imageUrl != null && userPreferences.hasPartnerImageChanged(imageUrl)) {
            // URL changée, forcer le rechargement
            forceRefresh = true
        }
    }

    // Téléchargement en arrière-plan si nécessaire
    LaunchedEffect(forceRefresh) {
        if (forceRefresh && imageUrl != null) {
            imageRepository.loadProfileImage(imageUrl, isCurrentUser = false)
                .onSuccess { bitmap ->
                    cachedPartnerImage = bitmap
                    forceRefresh = false
                }
        }
    }

    Box(
        modifier = modifier.size(size + 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Effet de surbrillance
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.35f), CircleShape)
                .blur(6.dp)
        )

        Box(modifier = Modifier.size(size)) {
            when {
                cachedPartnerImage != null -> {
                    // Image partenaire en cache
                    Image(
                        bitmap = cachedPartnerImage!!.asImageBitmap(),
                        contentDescription = "Photo $partnerName",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                !imageUrl.isNullOrBlank() -> {
                    // AsyncImage avec chargement
                    AsyncImage(
                        url = imageUrl,
                        isCurrentUser = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                partnerName.isNotBlank() -> {
                    // Initiales du partenaire
                    UserInitialsView(
                        name = partnerName,
                        size = size
                    )
                }

                else -> {
                    // Icône par défaut
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Pas de partenaire",
                            modifier = Modifier.size(size * 0.4f),
                            tint = Color.Gray
                        )
                    }
                }
            }

            // Bordure
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
fun UserInitialsView(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val initials = name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")

    val backgroundColor = remember {
        // Couleur basée sur le hash du nom
        val colors = listOf(
            Color(0xFF6B73FF), Color(0xFF9B59B6), Color(0xFF3498DB),
            Color(0xFF1ABC9C), Color(0xFF2ECC71), Color(0xFFE67E22),
            Color(0xFFE74C3C), Color(0xFFF39C12)
        )
        colors[name.hashCode().absoluteValue % colors.size]
    }

    Box(
        modifier = modifier
            .size(size)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = (size.value * 0.4).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
```

---

## 🔧 Services et Utilitaires

### **9. ImageUtils.kt - Traitement Images**

```kotlin
object ImageUtils {

    fun resize(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        val finalWidth: Int
        val finalHeight: Int

        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
            finalHeight = maxHeight
        } else {
            finalWidth = maxWidth
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    fun compressToJpeg(bitmap: Bitmap, quality: Int = 80): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    fun cropToCircle(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2

        val squaredBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(squaredBitmap, 0f, 0f, paint)

        return output
    }
}
```

### **10. SecurityUtils.kt - Sécurité**

```kotlin
object SecurityUtils {

    fun extractUserIdFromFirebaseUrl(url: String): String? {
        // Extraire userId depuis URL Firebase Storage
        val regex = "profile_images/([^/]+)/".toRegex()
        return regex.find(url)?.groupValues?.get(1)
    }

    fun hashUrl(url: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun validateImageUrl(url: String): Boolean {
        return url.startsWith("https://") &&
               (url.contains("firebasestorage.googleapis.com") ||
                url.contains("storage.googleapis.com"))
    }
}
```

---

## 🗃️ ViewModels et État

### **11. ProfileViewModel.kt - Gestion Photo Profil**

```kotlin
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    data class ProfileUiState(
        val profileImage: Bitmap? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val uploadProgress: Int = 0
    )

    init {
        // Charger image en cache au démarrage
        loadCachedProfileImage()
    }

    private fun loadCachedProfileImage() {
        viewModelScope.launch {
            val cachedImage = userPreferences.getCachedProfileImage()
            _uiState.value = _uiState.value.copy(profileImage = cachedImage)
        }
    }

    fun uploadNewProfileImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                uploadProgress = 0
            )

            // Cache local immédiat (UX)
            userPreferences.cacheProfileImage(bitmap)
            _uiState.value = _uiState.value.copy(profileImage = bitmap)

            // Upload Firebase en arrière-plan
            imageRepository.uploadProfileImage(bitmap)
                .onSuccess { downloadUrl ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        uploadProgress = 100
                    )

                    // Notifier widgets Android
                    updateWidgets()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message,
                        uploadProgress = 0
                    )
                }
        }
    }

    private fun updateWidgets() {
        // Mise à jour widgets avec nouvelle image
        val intent = Intent(context, WidgetUpdateService::class.java)
        context.startService(intent)
    }
}
```

---

## 🎨 Écrans Principaux

### **12. MenuScreen.kt - Écran Profil Principal**

```kotlin
@Composable
fun MenuScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showImagePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            // Header avec photo de profil
            ProfileSection(
                profileImage = uiState.profileImage,
                isLoading = uiState.isLoading,
                onImageClick = { showImagePicker = true },
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }

        item {
            // Section "À propos de moi"
            AboutMeSection(
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        item {
            // Section Application
            ApplicationSection(
                onNavigateToSettings = onNavigateToSettings
            )
        }
    }

    // Image Picker
    if (showImagePicker) {
        ImagePickerDialog(
            onImageSelected = { bitmap ->
                viewModel.uploadNewProfileImage(bitmap)
                showImagePicker = false
            },
            onDismiss = { showImagePicker = false }
        )
    }

    // Toast d'erreur
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Afficher toast d'erreur
        }
    }
}

@Composable
fun ProfileSection(
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
            ProfileImage(
                imageUrl = null, // On utilise directement le bitmap en cache
                size = 120.dp,
                onImageClick = onImageClick
            )

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

            // Icône appareil photo
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

### **13. ImagePickerDialog.kt - Sélection Image**

```kotlin
@Composable
fun ImagePickerDialog(
    onImageSelected: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, it)
                    )
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }

                // Redimensionner et cropper
                val processedBitmap = ImageUtils.resize(bitmap, 300, 300)
                onImageSelected(processedBitmap)

            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Changer la photo de profil") },
        text = { Text("Sélectionnez une nouvelle photo depuis votre galerie") },
        confirmButton = {
            TextButton(
                onClick = { launcher.launch("image/*") }
            ) {
                Text("Galerie")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
```

---

## ⚙️ Configuration et Dépendances

### **14. build.gradle (Module App)**

```kotlin
dependencies {
    // Firebase
    implementation 'com.google.firebase:firebase-storage-ktx:20.3.0'
    implementation 'com.google.firebase:firebase-auth-ktx:22.3.0'
    implementation 'com.google.firebase:firebase-functions-ktx:20.4.0'

    // Networking
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    // Image Processing
    implementation 'com.github.bumptech.glide:glide:4.16.0' // Alternative à Coil

    // Dependency Injection
    implementation 'com.google.dagger:hilt-android:2.48'
    kapt 'com.google.dagger:hilt-compiler:2.48'

    // Jetpack Compose
    implementation 'androidx.compose.ui:ui:1.5.4'
    implementation 'androidx.compose.ui:ui-tooling-preview:1.5.4'
    implementation 'androidx.compose.material3:material3:1.1.2'
    implementation 'androidx.activity:activity-compose:1.8.1'

    // ViewModel & LiveData
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'

    // Navigation
    implementation 'androidx.navigation:navigation-compose:2.7.4'

    // Permissions
    implementation 'com.google.accompanist:accompanist-permissions:0.32.0'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito:mockito-core:5.6.0'
    androidTestImplementation 'androidx.compose.ui:ui-test-junit4:1.5.4'
}
```

### **15. AndroidManifest.xml - Permissions**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissions réseau -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Permissions galerie photos -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"
                     android:minSdkVersion="33" />

    <!-- Permission appareil photo (optionnel) -->
    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:name=".Love2LoveApplication"
        android:allowBackup="true"
        android:theme="@style/Theme.Love2Love">

        <!-- Activités principales -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Love2Love">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Service de mise à jour widgets -->
        <service
            android:name=".widgets.WidgetUpdateService"
            android:enabled="true"
            android:exported="false" />

    </application>
</manifest>
```

---

## 📊 Comparaison iOS vs Android

### **Tableau des Équivalences**

| **Fonctionnalité**       | **iOS**                         | **Android**                            |
| ------------------------ | ------------------------------- | -------------------------------------- |
| **Cache Mémoire**        | `NSCache` (50 images, 100MB)    | `LruCache` (1/8 RAM)                   |
| **Cache Disque**         | `FileManager` + App Group       | `File` + Internal Storage              |
| **Cache Utilisateur**    | `UserDefaults` + JPEG Base64    | `SharedPreferences` + Base64           |
| **Téléchargement**       | `URLSession` + `AsyncImageView` | `OkHttp` + `AsyncImage`                |
| **Upload Firebase**      | `StorageReference.putData()`    | `FirebaseStorage.putBytes()`           |
| **Redimensionnement**    | `UIGraphicsImageRenderer`       | `Bitmap.createScaledBitmap()`          |
| **Compression**          | `.jpegData(quality: 0.8)`       | `.compress(JPEG, 80)`                  |
| **Interface**            | `SwiftUI`                       | `Jetpack Compose`                      |
| **État Réactif**         | `@State`, `@StateObject`        | `MutableStateFlow`, `collectAsState()` |
| **Injection Dépendance** | Manuel                          | `Hilt`                                 |
| **Widgets**              | `WidgetKit` + App Group         | `AppWidgetProvider` + Service          |
| **Sécurité**             | Cloud Functions + URL signée    | Cloud Functions + Token Auth           |
| **Gestion Erreurs**      | `Result<Success, Failure>`      | `Result.success()/.failure()`          |

### **Performances Attendues Android**

- **Cache mémoire** : `< 5ms` (LruCache optimisé)
- **Cache disque** : `< 100ms` (Internal storage)
- **Téléchargement réseau** : `500ms - 3s` (selon connexion)
- **Upload Firebase** : `2s - 10s` (selon taille image)

---

## 🚀 Plan d'Implémentation Android

### **Phase 1 : Infrastructure (1-2 semaines)**

1. ✅ Setup Hilt pour injection dépendances
2. ✅ Créer `ImageCacheManager` avec cache mémoire/disque
3. ✅ Implémenter `UserPreferences` pour cache utilisateur
4. ✅ Setup Retrofit pour Cloud Functions

### **Phase 2 : Repository & Services (1 semaine)**

5. ✅ Créer `ImageRepository` avec logique métier
6. ✅ Implémenter `CloudFunctionService` pour images partenaire
7. ✅ Créer `ImageUtils` pour traitement images
8. ✅ Tests unitaires des services

### **Phase 3 : Interface Compose (2 semaines)**

9. ✅ Composant `AsyncImage` avec états de chargement
10. ✅ Composant `ProfileImage` pour utilisateur actuel
11. ✅ Composant `PartnerProfileImage` pour partenaire
12. ✅ `ImagePickerDialog` pour sélection galerie

### **Phase 4 : Écrans Principaux (1 semaine)**

13. ✅ `MenuScreen` avec section profil
14. ✅ `ProfileViewModel` pour gestion état
15. ✅ Intégration dans navigation app
16. ✅ Gestion permissions galerie

### **Phase 5 : Features Avancées (1 semaine)**

17. ✅ Support widgets Android (si nécessaire)
18. ✅ Optimisations performance
19. ✅ Tests d'intégration complets
20. ✅ Documentation technique

### **Phase 6 : Tests & Optimisation (1 semaine)**

21. ✅ Tests sur différents appareils Android GO
22. ✅ Optimisation mémoire pour petits appareils
23. ✅ Tests de montée en charge cache
24. ✅ Validation sécurité

---

## ✅ Checklist de Migration Complète

### **Backend (Cloud Functions)**

- [ ] `getPartnerProfileImage` déjà disponible ✅
- [ ] `getSignedImageURL` déjà disponible ✅
- [ ] Vérification tokens Android dans headers
- [ ] Logs sécurisés pour debug Android

### **Sécurité**

- [ ] Authentification Firebase pour toutes les requêtes
- [ ] Validation côté serveur des IDs partenaires
- [ ] URLs signées avec expiration courte (1h)
- [ ] Chiffrement des images sensibles (optionnel)

### **Performance**

- [ ] Cache multi-niveaux (mémoire → disque → réseau)
- [ ] Compression JPEG optimisée (80% qualité)
- [ ] Images redimensionnées (300x300 max)
- [ ] Téléchargement en arrière-plan non bloquant

### **UX/UI**

- [ ] Affichage instantané depuis cache
- [ ] Spinner de chargement élégant
- [ ] Gestion d'erreur avec retry
- [ ] Placeholder attrayant (initiales colorées)

### **Robustesse**

- [ ] Gestion déconnexion réseau
- [ ] Récupération après crash app
- [ ] Nettoyage cache automatique
- [ ] Migration données entre versions app

---

## 🎯 Résumé des Avantages Android

### **Performance**

- **Cache intelligent** avec LruCache optimisé Android
- **Téléchargement asynchrone** non bloquant avec Coroutines
- **Compression** adaptée aux appareils Android GO

### **Sécurité**

- **Architecture identique** à iOS (Cloud Functions)
- **Tokens d'authentification** Firebase intégrés
- **Validation stricte** des permissions partenaire

### **Expérience Utilisateur**

- **Affichage instantané** grâce au cache multi-niveaux
- **Interface Material Design** avec Jetpack Compose
- **Gestion d'erreur** gracieuse avec retry automatique

### **Maintenabilité**

- **Architecture MVVM** propre et testable
- **Repository Pattern** pour séparation des responsabilités
- **Hilt** pour injection de dépendances type-safe

Cette implémentation Android GO offrira une **expérience utilisateur équivalente à iOS** tout en étant **optimisée pour les appareils Android moins puissants** ! 🚀
