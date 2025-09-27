# 💾 SYSTÈME DE CACHE COMPLET - Gestion 360° des Données

## 🎯 Vue d'Ensemble Architecture Cache

**Love2Love utilise un système de cache multi-niveaux sophistiqué :**

1. **UserCacheManager** - Cache UserDefaults pour données utilisateur et images de profil
2. **ImageCacheService** - Cache dual mémoire/disque pour toutes les images avec App Groups
3. **QuestionCacheManager (Realm)** - Cache persistant pour questions/défis du jour
4. **FavoritesService** - Cache hybride Realm + Firestore temps réel
5. **JournalService** - Cache mémoire avec Firestore listener temps réel
6. **PartnerLocationService** - Cache temporel pour données et localisation partenaire
7. **WidgetService** - Cache App Groups partagé entre app et widgets
8. **URLSession Cache** - Cache réseau natif iOS automatique

---

## 🗂️ 1. USERCACHEMANAGER - Cache Données Utilisateur

### 📦 Structure et Configuration

```swift
class UserCacheManager {
    static let shared = UserCacheManager()

    private let userDefaults = UserDefaults.standard
    private let cacheKey = "cached_user_data"
    private let cacheTimestampKey = "cached_user_timestamp"
    private let profileImageCacheKey = "cached_profile_image"
    private let partnerImageCacheKey = "cached_partner_image"
    private let partnerImageURLKey = "cached_partner_image_url"

    private init() {}
}
```

**Utilise UserDefaults standard pour :**

- **Données utilisateur** : `AppUser` sérialisé en JSON
- **Images profil** : `Data` (JPEG 80% qualité)
- **Images partenaire** : `Data` + URL de référence
- **Timestamps** : Validation âge du cache (7 jours max)

### 💾 Cache Données Utilisateur

```swift
func cacheUser(_ user: AppUser) {
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

func getCachedUser() -> AppUser? {
    guard let data = userDefaults.data(forKey: cacheKey) else {
        return nil
    }

    // Vérification âge du cache (7 jours max)
    if let timestamp = userDefaults.object(forKey: cacheTimestampKey) as? TimeInterval {
        let age = Date().timeIntervalSince1970 - timestamp
        let maxAge: TimeInterval = 7 * 24 * 3600

        if age > maxAge {
            print("⏰ UserCacheManager: Cache expiré, nettoyage")
            clearCache()
            return nil
        }
    }

    do {
        let decoder = JSONDecoder()
        let user = try decoder.decode(AppUser.self, from: data)
        return user
    } catch {
        print("❌ UserCacheManager: Erreur décodage cache: \(error)")
        clearCache()
        return nil
    }
}
```

**Fonctionnalités Avancées :**

- **Expiration automatique** : 7 jours
- **Auto-nettoyage** : En cas de corruption données
- **Validation intégrité** : Try/catch avec fallback
- **Logs sécurisés** : Pas d'exposition données sensibles

### 🖼️ Cache Images de Profil

```swift
func cacheProfileImage(_ image: UIImage) {
    guard let imageData = image.jpegData(compressionQuality: 0.8) else {
        print("❌ UserCacheManager: Impossible de convertir l'image en données")
        return
    }

    userDefaults.set(imageData, forKey: profileImageCacheKey)
    print("✅ UserCacheManager: Image de profil mise en cache (\(imageData.count) bytes)")
}

func getCachedProfileImage() -> UIImage? {
    guard let imageData = userDefaults.data(forKey: profileImageCacheKey) else {
        return nil
    }

    guard let image = UIImage(data: imageData) else {
        // Nettoyer données corrompues
        userDefaults.removeObject(forKey: profileImageCacheKey)
        return nil
    }

    return image
}
```

**Stratégie "Cache-First" :**

1. **Affichage instantané** : Cache local prioritaire sur Firebase
2. **Upload silencieux** : Firebase sync en arrière-plan
3. **UX optimale** : Aucun délai d'affichage

### 🤝 Cache Images Partenaire

```swift
func cachePartnerImage(_ image: UIImage, url: String) {
    guard let imageData = image.jpegData(compressionQuality: 0.8) else {
        return
    }

    userDefaults.set(imageData, forKey: partnerImageCacheKey)
    userDefaults.set(url, forKey: partnerImageURLKey)
    print("✅ UserCacheManager: Image partenaire mise en cache")
}

func hasPartnerImageChanged(newURL: String?) -> Bool {
    let cachedURL = userDefaults.string(forKey: partnerImageURLKey)
    return cachedURL != newURL
}
```

**Logique Intelligente :**

- **Détection changement** : Comparaison URL pour éviter re-téléchargement
- **Cache partagé** : Une seule image partenaire à la fois
- **Nettoyage automatique** : Lors déconnexion/suppression compte

---

## 🖼️ 2. IMAGECACHESERVICE - Cache Double Niveau Images

### 🏗️ Architecture Sophistiquée

```swift
class ImageCacheService {
    static let shared = ImageCacheService()

    private let memoryCache = NSCache<NSString, UIImage>()  // Niveau 1: Mémoire
    private let fileManager = FileManager.default
    private let cacheDirectory: URL                         // Niveau 2: Disque

    private init() {
        // PRIORITÉ: App Group pour partage avec widgets
        if let containerURL = fileManager.containerURL(forSecurityApplicationGroupIdentifier: "group.com.lyes.love2love") {
            cacheDirectory = containerURL.appendingPathComponent("ImageCache")
        } else {
            // Fallback vers cache app normale
            cacheDirectory = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("ImageCache")
        }

        // Configuration cache mémoire
        memoryCache.countLimit = 50                    // Max 50 images
        memoryCache.totalCostLimit = 100 * 1024 * 1024 // Max 100MB
    }
}
```

**App Groups Integration :**

- **Identifiant** : `"group.com.lyes.love2love"`
- **Partage widgets** : Images accessibles depuis widgets
- **Fallback robuste** : Cache app si App Group indisponible

### ⚡ Récupération Cache Multi-Niveau

```swift
func getCachedImage(for urlString: String) -> UIImage? {
    let cacheKey = cacheKeyForURL(urlString)

    // 1️⃣ NIVEAU 1: Cache mémoire (plus rapide)
    if let memoryImage = memoryCache.object(forKey: cacheKey as NSString) {
        print("🖼️ ImageCacheService: Image trouvée en cache mémoire")
        return memoryImage
    }

    // 2️⃣ NIVEAU 2: Cache disque
    if let diskImage = loadImageFromDisk(cacheKey: cacheKey) {
        print("🖼️ ImageCacheService: Image trouvée en cache disque")
        // Remettre en cache mémoire pour prochaine fois
        memoryCache.setObject(diskImage, forKey: cacheKey as NSString)
        return diskImage
    }

    return nil
}
```

### 💾 Sauvegarde Cache Asynchrone

```swift
func cacheImage(_ image: UIImage, for urlString: String) {
    let cacheKey = cacheKeyForURL(urlString)

    // 1️⃣ Cache mémoire immédiat (UI thread)
    memoryCache.setObject(image, forKey: cacheKey as NSString)

    // 2️⃣ Cache disque asynchrone (background thread)
    Task.detached { [weak self] in
        self?.saveImageToDisk(image, cacheKey: cacheKey)
    }
}

private func saveImageToDisk(_ image: UIImage, cacheKey: String) {
    guard let imageData = image.jpegData(compressionQuality: 0.8) else {
        return
    }

    let fileURL = cacheDirectory.appendingPathComponent("\(cacheKey).jpg")

    do {
        try imageData.write(to: fileURL)
        print("🖼️ ImageCacheService: Image sauvée sur disque")
    } catch {
        print("❌ ImageCacheService: Erreur sauvegarde disque: \(error)")
    }
}
```

### 🔑 Génération Clé Cache Sécurisée

```swift
private func cacheKeyForURL(_ urlString: String) -> String {
    let url = URL(string: urlString)
    if let path = url?.path, let query = url?.query {
        return "\(path.replacingOccurrences(of: "/", with: "_"))_\(query.hash)"
            .replacingOccurrences(of: "=", with: "_")
    } else if let path = url?.path {
        return path.replacingOccurrences(of: "/", with: "_")
    } else {
        return urlString.hash.description
    }
}
```

**Sécurité Clé :**

- **Hash URL** : Évite exposition tokens Firebase dans noms fichiers
- **Caractères sécurisés** : Remplacement `/` et `=`
- **Unicité garantie** : Hash collision très faible

---

## 📱 3. QUESTIONCACHEMANAGER (REALM) - Cache Persistant

### 🏗️ Configuration Realm Optimisée

```swift
@MainActor
class QuestionCacheManager: ObservableObject {
    static let shared = QuestionCacheManager()

    private var realm: Realm?
    @Published var isLoading = false
    @Published var cacheStatus: [String: Bool] = [:]
    @Published var isRealmAvailable = false

    private func initializeRealm() {
        do {
            var config = Realm.Configuration.defaultConfiguration
            config.schemaVersion = 2
            config.migrationBlock = { migration, oldSchemaVersion in
                if oldSchemaVersion < 2 {
                    // Migration automatique pour favoris
                }
            }

            // Compactage automatique
            config.shouldCompactOnLaunch = { (totalBytes: Int, usedBytes: Int) in
                let maxSize = 20 * 1024 * 1024  // 20MB max
                let usageRatio = Double(usedBytes) / Double(totalBytes)
                return totalBytes > maxSize && usageRatio < 0.5
            }

            self.realm = try Realm(configuration: config)
            self.isRealmAvailable = true
        } catch {
            print("⚠️ RealmManager: Erreur d'initialisation: \(error)")
            self.realm = nil
            self.isRealmAvailable = false
        }
    }
}
```

**Optimisations Realm :**

- **Schema versioning** : Migration automatique
- **Compactage intelligent** : Si >20MB et <50% utilisé
- **Error handling** : Fallback gracieux si Realm indisponible

### 📝 Cache Questions du Jour

```swift
func cacheDailyQuestion(_ question: DailyQuestion) {
    guard let realm = realm else {
        print("⚠️ RealmManager: Realm non disponible pour cache question quotidienne")
        return
    }

    do {
        try realm.write {
            let realmQuestion = RealmDailyQuestion(dailyQuestion: question)
            realm.add(realmQuestion, update: .modified)
        }
        print("✅ RealmManager: Question quotidienne cachée: \(question.questionKey)")
    } catch {
        print("❌ RealmManager: Erreur cache question quotidienne: \(error)")
    }
}

func getCachedDailyQuestion(for coupleId: String, date: String) -> DailyQuestion? {
    guard let realm = realm else { return nil }

    let realmQuestion = realm.objects(RealmDailyQuestion.self)
        .filter("coupleId == %@ AND scheduledDate == %@", coupleId, date)
        .first

    return realmQuestion?.toDailyQuestion()
}
```

### 🏆 Cache Défis du Jour

```swift
func cacheDailyChallenge(_ challenge: DailyChallenge) {
    guard let realm = realm else {
        print("⚠️ RealmManager: Realm non disponible pour cache défi quotidien")
        return
    }

    do {
        try realm.write {
            let realmChallenge = RealmDailyChallenge(dailyChallenge: challenge)
            realm.add(realmChallenge, update: .modified)
        }
        print("✅ RealmManager: Défi quotidien caché: \(challenge.challengeKey)")
    } catch {
        print("❌ RealmManager: Erreur cache défi quotidien: \(error)")
    }
}
```

**Avantages Cache Realm :**

- **Persistance offline** : Données disponibles sans réseau
- **Performance** : Pas d'appels Firebase répétés
- **Synchronisation** : Update via Firebase quand connexion
- **Limite intelligente** : 30 questions/défis max en cache

---

## ⭐ 4. FAVORITESSERVICE - Cache Hybride Temps Réel

### 🔄 Architecture Hybride Sophistiquée

```swift
@MainActor
class FavoritesService: ObservableObject {
    @Published var favoriteQuestions: [FavoriteQuestion] = []        // Cache local
    @Published var sharedFavoriteQuestions: [SharedFavoriteQuestion] = [] // Cache Firestore

    private var firestoreListener: ListenerRegistration?
    private var realm: Realm?

    init() {
        initializeRealm()
        setupFirestoreListener()
        loadLocalFavorites()
    }
}
```

**Stratégie Double Cache :**

- **Local (Realm)** : `FavoriteQuestion` pour performance offline
- **Partagé (Firestore)** : `SharedFavoriteQuestion` pour sync temps réel
- **Fusion intelligente** : `getAllFavorites()` combine les deux sources

### 🔥 Firestore Listener Temps Réel

```swift
private func setupFirestoreListener() {
    guard let userId = currentUserId else { return }

    firestoreListener = db.collection("favoriteQuestions")
        .whereField("partnerIds", arrayContains: userId)
        .addSnapshotListener { [weak self] snapshot, error in
            if let error = error {
                print("❌ FavoritesService: Erreur listener: \(error)")
                return
            }
            self?.handleFirestoreUpdate(snapshot: snapshot)
        }
}

private func handleFirestoreUpdate(snapshot: QuerySnapshot?) {
    guard let documents = snapshot?.documents else { return }

    let newSharedFavorites = documents.compactMap { document in
        SharedFavoriteQuestion(from: document)
    }

    DispatchQueue.main.async {
        self.sharedFavoriteQuestions = newSharedFavorites.sorted { $0.dateAdded > $1.dateAdded }
        // Synchroniser avec cache local
        self.syncToLocalCache()
    }
}
```

### 🔄 Synchronisation Bidirectionnelle

```swift
private func syncToLocalCache() {
    guard let realm = realm, let userId = currentUserId else { return }

    do {
        try realm.write {
            let localFavorites = realm.objects(RealmFavoriteQuestion.self)
                .filter("userId == %@", userId)

            let localQuestionIds = Set(localFavorites.map { $0.questionId })
            let sharedQuestionIds = Set(sharedFavoriteQuestions.map { $0.questionId })

            // Supprimer du cache local les favoris qui ne sont plus partagés
            let idsToRemove = localQuestionIds.subtracting(sharedQuestionIds)
            for questionId in idsToRemove {
                let favoritesToDelete = localFavorites.filter("questionId == %@", questionId)
                realm.delete(favoritesToDelete)
            }

            // Ajouter les nouveaux favoris partagés au cache local
            let idsToAdd = sharedQuestionIds.subtracting(localQuestionIds)
            for sharedFavorite in sharedFavoriteQuestions {
                if idsToAdd.contains(sharedFavorite.questionId) {
                    let realmFavorite = RealmFavoriteQuestion()
                    // ... conversion et ajout
                    realm.add(realmFavorite, update: .modified)
                }
            }
        }
    } catch {
        print("❌ FavoritesService: Erreur sync cache: \(error)")
    }

    // Recharger favoris locaux
    Task { @MainActor in
        self.loadLocalFavorites()
    }
}
```

**Synchronisation Cloud Function :**

```javascript
// firebase/functions/index.js
async function syncPartnerFavoritesInternal(currentUserId, partnerId) {
  // Ajouter partnerId aux favoris de currentUser
  // Ajouter currentUserId aux favoris de partner
  // Batch update pour performance
}
```

---

## 📔 5. JOURNALSERVICE - Cache Mémoire Temps Réel

### ⚡ Cache Mémoire avec Firestore Listener

```swift
@MainActor
class JournalService: ObservableObject {
    @Published var entries: [JournalEntry] = []  // Cache mémoire principal
    @Published var isLoading = false

    private var listener: ListenerRegistration?

    private func setupListener() {
        guard let currentUser = Auth.auth().currentUser else { return }

        listener = db.collection("journalEntries")
            .whereField("partnerIds", arrayContains: currentUser.uid)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    print("❌ JournalService: Erreur listener: \(error)")
                    return
                }
                self?.handleSnapshotUpdate(snapshot: snapshot)
            }
    }

    private func handleSnapshotUpdate(snapshot: QuerySnapshot?) {
        guard let documents = snapshot?.documents else { return }

        let newEntries = documents.compactMap { JournalEntry(from: $0) }

        DispatchQueue.main.async {
            self.entries = newEntries.sorted { $0.eventDate > $1.eventDate }
            print("🔥 JournalService: \(self.entries.count) entrées chargées")
        }
    }
}
```

**Pas de Cache Persistant :**

- **Choix délibéré** : Journal nécessite sync temps réel partenaire
- **Cache mémoire** : Array `@Published` pour UI réactive
- **Performance** : Firestore listener optimisé avec `whereField`
- **Fallback** : `refreshEntries()` pour récupération manuelle

---

## 🗺️ 6. PARTNERLOCATIONSERVICE - Cache Temporel

### ⏱️ Cache Basé Temps avec Niveaux

```swift
class PartnerLocationService: ObservableObject {
    @Published var partnerName: String?
    @Published var currentDistance: String?
    @Published var isLoading = false

    private var lastFetchTime = Date(timeIntervalSince1970: 0)
    private var lastLocationFetchTime = Date(timeIntervalSince1970: 0)

    private let cacheValidityInterval: TimeInterval = 120    // 2 minutes données
    private let locationCacheInterval: TimeInterval = 5      // 5 secondes position
}
```

### 🎯 Cache Multi-Niveau Intelligent

```swift
private func fetchPartnerDataViaCloudFunction(partnerId: String) {
    // Vérifier cache pour éviter appels trop fréquents
    let now = Date()
    if now.timeIntervalSince(lastFetchTime) < cacheValidityInterval && partnerName != nil {
        print("🌍 PartnerLocationService: Données partenaire en cache - Récupération localisation uniquement")
        fetchPartnerLocationViaCloudFunction(partnerId: partnerId)
        return
    }

    // Récupérer données complètes si cache expiré
    lastFetchTime = now
    // ... Cloud Function getPartnerInfo
}

private func fetchPartnerLocationViaCloudFunction(partnerId: String) {
    let now = Date()
    if now.timeIntervalSince(lastLocationFetchTime) < 5 {
        print("🌍 PartnerLocationService: Localisation récemment récupérée - Attente")
        return
    }

    lastLocationFetchTime = now
    // ... Cloud Function getPartnerLocation
}
```

**Optimisation Cache :**

- **Données générales** : 2 minutes (nom, etc.)
- **Localisation** : 5 secondes (position GPS)
- **Évite spam** : Cloud Functions protégées
- **UX fluide** : Données immédiates si cache valide

---

## 📊 7. WIDGETSERVICE - Cache App Groups Partagé

### 🔗 Partage App/Widget via App Groups

```swift
class WidgetService: ObservableObject {
    @Published var relationshipStats: RelationshipStats?
    @Published var distanceInfo: DistanceInfo?

    // App Group pour partager avec widgets
    private let sharedDefaults = UserDefaults(suiteName: "group.com.lyes.love2love")

    private func saveWidgetData() {
        guard let stats = relationshipStats else { return }

        // Sauvegarder pour widgets via App Groups
        sharedDefaults?.set(stats.daysTotal, forKey: "widget_days_total")
        sharedDefaults?.set(stats.startDateString, forKey: "widget_start_date")

        if let distance = distanceInfo {
            sharedDefaults?.set(distance.distance, forKey: "widget_distance")
            sharedDefaults?.set(distance.unit, forKey: "widget_distance_unit")
        }
    }
}
```

### 🖼️ Cache Images Widgets Optimisé

```swift
private func downloadAndCacheImage(from urlString: String, key: String, isUser: Bool) {
    let fileName = isUser ? "user_profile_image.jpg" : "partner_profile_image.jpg"

    // 1️⃣ Vérifier cache ImageCacheService d'abord
    if let cachedImage = ImageCacheService.shared.getCachedImage(for: urlString) {
        let resizedImage = resizeImage(cachedImage, to: CGSize(width: 150, height: 150))
        if let finalImage = resizedImage {
            ImageCacheService.shared.cacheImageForWidget(finalImage, fileName: fileName)
            sharedDefaults?.set(fileName, forKey: key)
        }
        return
    }

    // 2️⃣ Télécharger si pas en cache
    URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
        // ... téléchargement et redimensionnement
        DispatchQueue.main.async {
            self.sharedDefaults?.set(fileName, forKey: key)
        }
    }.resume()
}
```

**Stratégie Widget :**

- **Réutilisation cache** : ImageCacheService partagé
- **Redimensionnement** : 150x150 pour performance widgets
- **App Groups** : `"group.com.lyes.love2love"`
- **Clés standardisées** : Convention nommage cohérente

---

## 🌐 8. CACHE RÉSEAU (URLSession/URLCache)

### ⚙️ Configuration Automatique iOS

```swift
// URLSession utilise URLCache automatiquement
let (data, _) = try await URLSession.shared.data(from: url)

// Configuration implicite URLCache :
// - Cache mémoire : ~10MB
// - Cache disque : ~50MB
// - Headers cache HTTP respectés
// - Expiration automatique selon réponse serveur
```

**AsyncImageView Integration :**

```swift
private func loadImageFromFirebase(urlString: String) async throws -> UIImage {
    // 1️⃣ Vérifier ImageCacheService d'abord
    if let cachedImage = ImageCacheService.shared.getCachedImage(for: urlString) {
        return cachedImage
    }

    // 2️⃣ Télécharger (URLCache automatique)
    let (data, _) = try await URLSession.shared.data(from: url)
    guard let image = UIImage(data: data) else {
        throw AsyncImageError.invalidData
    }

    // 3️⃣ Mettre en cache ImageCacheService
    ImageCacheService.shared.cacheImage(image, for: urlString)

    return image
}
```

---

## 📊 STRATÉGIES CACHE PAR COMPOSANT

| Composant                  | Type Cache        | Durée      | Taille   | Partage    |
| -------------------------- | ----------------- | ---------- | -------- | ---------- |
| **UserCacheManager**       | UserDefaults      | 7 jours    | ~5MB     | Non        |
| **ImageCacheService**      | NSCache + Disk    | Permanent  | 100MB    | App Groups |
| **QuestionCacheManager**   | Realm             | 30 entrées | 20MB     | Non        |
| **FavoritesService**       | Realm + Firestore | Temps réel | Variable | Non        |
| **JournalService**         | Mémoire           | Session    | Variable | Non        |
| **PartnerLocationService** | Variables         | 2min/5s    | Minimal  | Non        |
| **WidgetService**          | App Groups        | Permanent  | ~1MB     | Widgets    |
| **URLCache**               | Automatique       | Variable   | 50MB     | Non        |

---

## 🎯 PERFORMANCES ET OPTIMISATIONS

### ⚡ Hiérarchie Performance (du plus rapide au plus lent)

1. **Variables mémoire** (`@Published`, `@State`) - **Immédiat**
2. **NSCache mémoire** - **~1ms**
3. **UserDefaults** - **~5ms**
4. **Realm local** - **~10ms**
5. **Cache disque** - **~50ms**
6. **URLCache réseau** - **~200ms**
7. **Firebase Firestore** - **~500ms+**
8. **Firebase Storage** - **~1s+**

### 🔄 Stratégies d'Invalidation

```swift
// Invalidation manuelle
UserCacheManager.shared.clearCache()           // Déconnexion
ImageCacheService.shared.clearAllCache()       // Maintenance

// Invalidation automatique
- UserDefaults : 7 jours
- ImageCache : Jamais (gestion manuelle)
- Realm : Compactage automatique si >20MB
- PartnerLocation : 2min/5s selon type
- URLCache : Headers HTTP serveur

// Invalidation conditionnelle
- Images partenaire : Si URL change
- Questions jour : Si date change
- Favoris : Sync temps réel Firestore
```

---

## 🤖 ADAPTATION ANDROID COMPLÈTE

### 1. UserCacheManager → SharedPreferences + Room

```kotlin
class UserCacheManager private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: UserCacheManager? = null

        fun getInstance(context: Context): UserCacheManager {
            return INSTANCE ?: synchronized(this) {
                val instance = UserCacheManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val sharedPrefs = context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Cache données utilisateur
    fun cacheUser(user: AppUser) {
        val json = gson.toJson(user)
        val timestamp = System.currentTimeMillis()

        sharedPrefs.edit()
            .putString("cached_user_data", json)
            .putLong("cached_user_timestamp", timestamp)
            .apply()

        Log.d("UserCacheManager", "✅ Utilisateur mis en cache: ${user.name}")
    }

    fun getCachedUser(): AppUser? {
        val json = sharedPrefs.getString("cached_user_data", null) ?: return null
        val timestamp = sharedPrefs.getLong("cached_user_timestamp", 0)

        // Vérifier âge du cache (7 jours)
        val age = System.currentTimeMillis() - timestamp
        val maxAge = 7 * 24 * 60 * 60 * 1000L

        if (age > maxAge) {
            clearCache()
            return null
        }

        return try {
            gson.fromJson(json, AppUser::class.java)
        } catch (e: Exception) {
            Log.e("UserCacheManager", "❌ Erreur décodage cache: ${e.message}")
            clearCache()
            null
        }
    }

    fun clearCache() {
        sharedPrefs.edit().clear().apply()
        clearCachedImages()
    }

    // Cache images de profil
    fun cacheProfileImage(bitmap: Bitmap) {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val imageBytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)

        sharedPrefs.edit()
            .putString("cached_profile_image", base64)
            .apply()

        Log.d("UserCacheManager", "✅ Image de profil mise en cache (${imageBytes.size} bytes)")
    }

    fun getCachedProfileImage(): Bitmap? {
        val base64 = sharedPrefs.getString("cached_profile_image", null) ?: return null

        return try {
            val imageBytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("UserCacheManager", "❌ Erreur chargement image cache")
            sharedPrefs.edit().remove("cached_profile_image").apply()
            null
        }
    }

    private fun clearCachedImages() {
        sharedPrefs.edit()
            .remove("cached_profile_image")
            .remove("cached_partner_image")
            .apply()
    }
}
```

### 2. ImageCacheService → Glide + DiskLruCache

```kotlin
class ImageCacheService private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: ImageCacheService? = null

        fun getInstance(context: Context): ImageCacheService {
            return INSTANCE ?: synchronized(this) {
                val instance = ImageCacheService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val memoryCache = LruCache<String, Bitmap>(getMemoryCacheSize())
    private val diskCache: DiskLruCache

    init {
        // Cache disque dans répertoire partagé (équivalent App Groups)
        val cacheDir = File(context.cacheDir, "ImageCache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        diskCache = DiskLruCache.open(
            cacheDir,
            1, // Version
            1, // Entries par clé
            100 * 1024 * 1024L // 100MB max
        )

        Log.d("ImageCacheService", "🖼️ Cache initialisé - Dossier: ${cacheDir.path}")
    }

    private fun getMemoryCacheSize(): Int {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return maxMemory / 8 // 1/8 de la mémoire disponible
    }

    fun getCachedImage(urlString: String): Bitmap? {
        val cacheKey = getCacheKey(urlString)

        // 1️⃣ Vérifier cache mémoire
        memoryCache.get(cacheKey)?.let { bitmap ->
            Log.d("ImageCacheService", "🖼️ Image trouvée en cache mémoire")
            return bitmap
        }

        // 2️⃣ Vérifier cache disque
        try {
            diskCache.get(cacheKey)?.let { snapshot ->
                val inputStream = snapshot.getInputStream(0)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                snapshot.close()

                if (bitmap != null) {
                    Log.d("ImageCacheService", "🖼️ Image trouvée en cache disque")
                    // Remettre en cache mémoire
                    memoryCache.put(cacheKey, bitmap)
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Log.e("ImageCacheService", "❌ Erreur lecture cache disque: ${e.message}")
        }

        return null
    }

    fun cacheImage(bitmap: Bitmap, urlString: String) {
        val cacheKey = getCacheKey(urlString)

        // 1️⃣ Cache mémoire immédiat
        memoryCache.put(cacheKey, bitmap)

        // 2️⃣ Cache disque asynchrone
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val editor = diskCache.edit(cacheKey)
                editor?.let {
                    val outputStream = it.newOutputStream(0)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    outputStream.close()
                    it.commit()
                    Log.d("ImageCacheService", "🖼️ Image sauvée sur disque")
                }
            } catch (e: Exception) {
                Log.e("ImageCacheService", "❌ Erreur sauvegarde disque: ${e.message}")
            }
        }
    }

    private fun getCacheKey(urlString: String): String {
        return urlString.hashCode().toString()
    }

    fun clearAllCache() {
        memoryCache.evictAll()
        try {
            diskCache.delete()
        } catch (e: Exception) {
            Log.e("ImageCacheService", "❌ Erreur nettoyage cache: ${e.message}")
        }
    }
}

// Utilisation avec Glide pour AsyncImage équivalent
class AsyncImageLoader {
    companion object {
        fun loadImage(
            imageView: ImageView,
            urlString: String,
            placeholder: Int? = null,
            error: Int? = null
        ) {
            // Vérifier cache custom d'abord
            ImageCacheService.getInstance(imageView.context)
                .getCachedImage(urlString)?.let { cachedBitmap ->
                    imageView.setImageBitmap(cachedBitmap)
                    return
                }

            // Utiliser Glide avec cache custom
            val requestBuilder = Glide.with(imageView.context)
                .asBitmap()
                .load(urlString)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)

            placeholder?.let { requestBuilder.placeholder(it) }
            error?.let { requestBuilder.error(it) }

            requestBuilder
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Bitmap>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e("AsyncImageLoader", "❌ Erreur chargement image: ${e?.message}")
                        return false
                    }

                    override fun onResourceReady(
                        resource: Bitmap?,
                        model: Any?,
                        target: Target<Bitmap>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        resource?.let { bitmap ->
                            // Mettre en cache custom après chargement Glide
                            ImageCacheService.getInstance(imageView.context)
                                .cacheImage(bitmap, urlString)
                        }
                        return false
                    }
                })
                .into(imageView)
        }
    }
}
```

### 3. QuestionCacheManager → Room Database

```kotlin
// Entities Room
@Entity(tableName = "daily_questions")
data class DailyQuestionEntity(
    @PrimaryKey val id: String,
    val coupleId: String,
    val scheduledDate: String,
    val questionKey: String,
    val questionDay: Int,
    val questionText: String,
    val categoryTitle: String,
    val emoji: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_challenges")
data class DailyChallengeEntity(
    @PrimaryKey val id: String,
    val coupleId: String,
    val scheduledDate: Long,
    val challengeKey: String,
    val challengeDay: Int,
    val challengeText: String,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// DAO
@Dao
interface DailyQuestionsDao {
    @Query("SELECT * FROM daily_questions WHERE coupleId = :coupleId AND scheduledDate = :date LIMIT 1")
    suspend fun getDailyQuestion(coupleId: String, date: String): DailyQuestionEntity?

    @Query("SELECT * FROM daily_questions WHERE coupleId = :coupleId ORDER BY scheduledDate DESC LIMIT :limit")
    suspend fun getCachedDailyQuestions(coupleId: String, limit: Int = 30): List<DailyQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheDailyQuestion(question: DailyQuestionEntity)

    @Query("DELETE FROM daily_questions WHERE coupleId = :coupleId")
    suspend fun clearQuestionsForCouple(coupleId: String)
}

@Dao
interface DailyChallengesDao {
    @Query("SELECT * FROM daily_challenges WHERE coupleId = :coupleId AND scheduledDate >= :startOfDay AND scheduledDate < :endOfDay LIMIT 1")
    suspend fun getDailyChallenge(coupleId: String, startOfDay: Long, endOfDay: Long): DailyChallengeEntity?

    @Query("SELECT * FROM daily_challenges WHERE coupleId = :coupleId ORDER BY scheduledDate DESC LIMIT :limit")
    suspend fun getCachedDailyChallenges(coupleId: String, limit: Int = 30): List<DailyChallengeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheDailyChallenge(challenge: DailyChallengeEntity)

    @Update
    suspend fun updateDailyChallenge(challenge: DailyChallengeEntity)
}

// Database
@Database(
    entities = [DailyQuestionEntity::class, DailyChallengeEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyQuestionsDao(): DailyQuestionsDao
    abstract fun dailyChallengesDao(): DailyChallengesDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "love2love_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration automatique Room
            }
        }
    }
}

// Cache Manager
class QuestionCacheManager @Inject constructor(
    private val database: AppDatabase,
    private val dailyQuestionsDao: DailyQuestionsDao = database.dailyQuestionsDao(),
    private val dailyChallengesDao: DailyChallengesDao = database.dailyChallengesDao()
) {

    suspend fun cacheDailyQuestion(question: DailyQuestion) {
        try {
            val entity = DailyQuestionEntity(
                id = question.id,
                coupleId = question.coupleId,
                scheduledDate = question.scheduledDate,
                questionKey = question.questionKey,
                questionDay = question.questionDay,
                questionText = question.questionText,
                categoryTitle = question.categoryTitle,
                emoji = question.emoji
            )
            dailyQuestionsDao.cacheDailyQuestion(entity)
            Log.d("QuestionCache", "✅ Question quotidienne cachée: ${question.questionKey}")
        } catch (e: Exception) {
            Log.e("QuestionCache", "❌ Erreur cache question: ${e.message}")
        }
    }

    suspend fun getCachedDailyQuestion(coupleId: String, date: String): DailyQuestion? {
        return try {
            val entity = dailyQuestionsDao.getDailyQuestion(coupleId, date)
            entity?.toDailyQuestion()
        } catch (e: Exception) {
            Log.e("QuestionCache", "❌ Erreur récupération cache: ${e.message}")
            null
        }
    }

    suspend fun getCachedDailyQuestions(coupleId: String, limit: Int = 30): List<DailyQuestion> {
        return try {
            dailyQuestionsDao.getCachedDailyQuestions(coupleId, limit)
                .map { it.toDailyQuestion() }
        } catch (e: Exception) {
            Log.e("QuestionCache", "❌ Erreur récupération cache liste: ${e.message}")
            emptyList()
        }
    }

    // Méthodes similaires pour challenges...
}

// Extensions de conversion
fun DailyQuestionEntity.toDailyQuestion(): DailyQuestion {
    return DailyQuestion(
        id = id,
        coupleId = coupleId,
        scheduledDate = scheduledDate,
        questionKey = questionKey,
        questionDay = questionDay,
        questionText = questionText,
        categoryTitle = categoryTitle,
        emoji = emoji
    )
}
```

### 4. FavoritesService → Room + Firestore LiveData

```kotlin
@Entity(tableName = "favorite_questions")
data class FavoriteQuestionEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val userId: String,
    val categoryTitle: String,
    val questionText: String,
    val emoji: String,
    val dateAdded: Long = System.currentTimeMillis()
)

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorite_questions WHERE userId = :userId ORDER BY dateAdded DESC")
    fun getFavoritesLiveData(userId: String): LiveData<List<FavoriteQuestionEntity>>

    @Query("SELECT * FROM favorite_questions WHERE userId = :userId ORDER BY dateAdded DESC")
    suspend fun getFavorites(userId: String): List<FavoriteQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteQuestionEntity)

    @Query("DELETE FROM favorite_questions WHERE questionId = :questionId AND userId = :userId")
    suspend fun removeFavorite(questionId: String, userId: String)

    @Query("DELETE FROM favorite_questions WHERE userId = :userId")
    suspend fun clearFavorites(userId: String)
}

class FavoritesService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val favoritesDao: FavoritesDao,
    private val auth: FirebaseAuth
) {

    private val _localFavorites = MutableLiveData<List<FavoriteQuestion>>()
    val localFavorites: LiveData<List<FavoriteQuestion>> = _localFavorites

    private val _sharedFavorites = MutableLiveData<List<SharedFavoriteQuestion>>()
    val sharedFavorites: LiveData<List<SharedFavoriteQuestion>> = _sharedFavorites

    private var firestoreListener: ListenerRegistration? = null

    init {
        setupFirestoreListener()
        loadLocalFavorites()
    }

    private fun setupFirestoreListener() {
        val userId = auth.currentUser?.uid ?: return

        firestoreListener = firestore.collection("favoriteQuestions")
            .whereArrayContains("partnerIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FavoritesService", "❌ Erreur listener: ${error.message}")
                    return@addSnapshotListener
                }

                val sharedFavorites = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        SharedFavoriteQuestion.fromDocument(doc)
                    } catch (e: Exception) {
                        Log.e("FavoritesService", "❌ Erreur parsing document: ${e.message}")
                        null
                    }
                } ?: emptyList()

                _sharedFavorites.postValue(sharedFavorites.sortedByDescending { it.dateAdded })

                // Synchroniser avec cache local
                GlobalScope.launch {
                    syncToLocalCache(sharedFavorites)
                }
            }
    }

    private fun loadLocalFavorites() {
        val userId = auth.currentUser?.uid ?: return

        favoritesDao.getFavoritesLiveData(userId).observeForever { entities ->
            val favorites = entities.map { it.toFavoriteQuestion() }
            _localFavorites.postValue(favorites)
        }
    }

    private suspend fun syncToLocalCache(sharedFavorites: List<SharedFavoriteQuestion>) {
        val userId = auth.currentUser?.uid ?: return

        try {
            // Récupérer favoris locaux actuels
            val localFavorites = favoritesDao.getFavorites(userId)
            val localQuestionIds = localFavorites.map { it.questionId }.toSet()
            val sharedQuestionIds = sharedFavorites.map { it.questionId }.toSet()

            // Supprimer favoris locaux qui ne sont plus partagés
            val idsToRemove = localQuestionIds - sharedQuestionIds
            for (questionId in idsToRemove) {
                favoritesDao.removeFavorite(questionId, userId)
            }

            // Ajouter nouveaux favoris partagés
            val idsToAdd = sharedQuestionIds - localQuestionIds
            for (sharedFavorite in sharedFavorites) {
                if (idsToAdd.contains(sharedFavorite.questionId)) {
                    val localFavorite = sharedFavorite.toLocalFavorite()
                    val entity = FavoriteQuestionEntity(
                        id = localFavorite.id,
                        questionId = localFavorite.questionId,
                        userId = userId,
                        categoryTitle = localFavorite.categoryTitle,
                        questionText = localFavorite.questionText,
                        emoji = localFavorite.emoji,
                        dateAdded = localFavorite.dateAdded.time
                    )
                    favoritesDao.insertFavorite(entity)
                }
            }

            Log.d("FavoritesService", "✅ Synchronisation cache terminée")

        } catch (e: Exception) {
            Log.e("FavoritesService", "❌ Erreur sync cache: ${e.message}")
        }
    }

    // Combiner favoris locaux et partagés
    fun getAllFavorites(): LiveData<List<FavoriteQuestion>> {
        return MediatorLiveData<List<FavoriteQuestion>>().apply {
            addSource(localFavorites) { locals ->
                val current = sharedFavorites.value ?: emptyList()
                val combined = combineWithSharedFavorites(locals, current)
                postValue(combined)
            }
            addSource(sharedFavorites) { shared ->
                val current = localFavorites.value ?: emptyList()
                val combined = combineWithSharedFavorites(current, shared)
                postValue(combined)
            }
        }
    }

    private fun combineWithSharedFavorites(
        locals: List<FavoriteQuestion>,
        shared: List<SharedFavoriteQuestion>
    ): List<FavoriteQuestion> {
        val allFavorites = mutableListOf<FavoriteQuestion>()

        // Ajouter favoris locaux
        allFavorites.addAll(locals)

        // Ajouter favoris partagés qui ne sont pas déjà locaux
        val localQuestionIds = locals.map { it.questionId }.toSet()
        shared.forEach { sharedFavorite ->
            if (!localQuestionIds.contains(sharedFavorite.questionId)) {
                allFavorites.add(sharedFavorite.toLocalFavorite())
            }
        }

        return allFavorites.sortedByDescending { it.dateAdded }
    }
}

// Extensions de conversion
fun FavoriteQuestionEntity.toFavoriteQuestion(): FavoriteQuestion {
    return FavoriteQuestion(
        id = id,
        questionId = questionId,
        categoryTitle = categoryTitle,
        questionText = questionText,
        emoji = emoji,
        dateAdded = Date(dateAdded)
    )
}
```

### 5. JournalService → StateFlow + Firestore

```kotlin
class JournalService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var firestoreListener: ListenerRegistration? = null

    init {
        setupListener()
    }

    private fun setupListener() {
        val userId = auth.currentUser?.uid ?: return

        firestoreListener = firestore.collection("journalEntries")
            .whereArrayContains("partnerIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("JournalService", "❌ Erreur listener: ${error.message}")
                    return@addSnapshotListener
                }

                val newEntries = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        JournalEntry.fromDocument(doc)
                    } catch (e: Exception) {
                        Log.e("JournalService", "❌ Erreur parsing entry: ${e.message}")
                        null
                    }
                } ?: emptyList()

                _entries.value = newEntries.sortedByDescending { it.eventDate }
                Log.d("JournalService", "🔥 ${newEntries.size} entrées chargées")
            }
    }

    suspend fun createEntry(
        title: String,
        description: String,
        eventDate: Date,
        imageUri: Uri? = null,
        location: JournalLocation? = null
    ): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Utilisateur non connecté")

            _isLoading.value = true

            var imageUrl: String? = null
            if (imageUri != null) {
                imageUrl = uploadImage(imageUri)
            }

            val entry = JournalEntry(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                eventDate = eventDate,
                createdAt = Date(),
                updatedAt = Date(),
                authorId = userId,
                authorName = "User", // À récupérer du profil
                imageURL = imageUrl,
                partnerIds = listOf(userId), // + partenaire si connecté
                location = location
            )

            firestore.collection("journalEntries")
                .document(entry.id)
                .set(entry.toFirestoreMap())
                .await()

            _isLoading.value = false
            Result.success(Unit)

        } catch (e: Exception) {
            _isLoading.value = false
            Log.e("JournalService", "❌ Erreur création entrée: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun uploadImage(imageUri: Uri): String {
        // Upload vers Firebase Storage
        val storageRef = FirebaseStorage.getInstance()
            .reference
            .child("journal/${UUID.randomUUID()}.jpg")

        storageRef.putFile(imageUri).await()
        return storageRef.downloadUrl.await().toString()
    }

    fun clearListener() {
        firestoreListener?.remove()
        firestoreListener = null
    }
}
```

### 6. PartnerLocationService → SharedPreferences + Timer

```kotlin
class PartnerLocationService @Inject constructor(
    private val context: Context,
    private val functions: FirebaseFunctions
) {

    private val sharedPrefs = context.getSharedPreferences("partner_location", Context.MODE_PRIVATE)

    private val _partnerName = MutableStateFlow<String?>(null)
    val partnerName: StateFlow<String?> = _partnerName.asStateFlow()

    private val _currentDistance = MutableStateFlow<String?>(null)
    val currentDistance: StateFlow<String?> = _currentDistance.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var lastFetchTime = 0L
    private var lastLocationFetchTime = 0L

    private val cacheValidityInterval = 2 * 60 * 1000L      // 2 minutes
    private val locationCacheInterval = 5 * 1000L           // 5 secondes

    fun fetchPartnerData(partnerId: String) {
        val now = System.currentTimeMillis()

        // Vérifier cache pour données générales
        if (now - lastFetchTime < cacheValidityInterval && _partnerName.value != null) {
            Log.d("PartnerLocation", "🌍 Données partenaire en cache - Localisation uniquement")
            fetchPartnerLocation(partnerId)
            return
        }

        _isLoading.value = true
        lastFetchTime = now

        // Récupérer données complètes
        GlobalScope.launch {
            try {
                val result = functions.getHttpsCallable("getPartnerInfo")
                    .call(mapOf("partnerId" to partnerId))
                    .await()

                val data = result.data as Map<String, Any>
                val success = data["success"] as Boolean

                if (success) {
                    val partnerInfo = data["partnerInfo"] as Map<String, Any>
                    updatePartnerData(partnerInfo)

                    // Récupérer localisation immédiatement
                    fetchPartnerLocation(partnerId)
                }

            } catch (e: Exception) {
                Log.e("PartnerLocation", "❌ Erreur Cloud Function: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchPartnerLocation(partnerId: String) {
        val now = System.currentTimeMillis()

        if (now - lastLocationFetchTime < locationCacheInterval) {
            Log.d("PartnerLocation", "🌍 Localisation récemment récupérée - Attente")
            return
        }

        lastLocationFetchTime = now

        GlobalScope.launch {
            try {
                val result = functions.getHttpsCallable("getPartnerLocation")
                    .call(mapOf("partnerId" to partnerId))
                    .await()

                val data = result.data as Map<String, Any>
                val success = data["success"] as Boolean

                if (success && data.containsKey("location")) {
                    val locationData = data["location"] as Map<String, Any>
                    updatePartnerLocation(locationData)
                }

            } catch (e: Exception) {
                Log.e("PartnerLocation", "❌ Erreur récupération localisation: ${e.message}")
            }
        }
    }

    private fun updatePartnerData(partnerInfo: Map<String, Any>) {
        val name = partnerInfo["name"] as? String
        _partnerName.value = name

        // Cache dans SharedPreferences
        sharedPrefs.edit()
            .putString("partner_name", name)
            .putLong("partner_data_timestamp", System.currentTimeMillis())
            .apply()
    }

    private fun updatePartnerLocation(locationData: Map<String, Any>) {
        val distance = locationData["distance"] as? String
        _currentDistance.value = distance

        // Cache dans SharedPreferences
        sharedPrefs.edit()
            .putString("partner_distance", distance)
            .putLong("partner_location_timestamp", System.currentTimeMillis())
            .apply()
    }

    // Récupérer données cachées au démarrage
    fun loadCachedData() {
        val cachedName = sharedPrefs.getString("partner_name", null)
        val cachedDistance = sharedPrefs.getString("partner_distance", null)

        _partnerName.value = cachedName
        _currentDistance.value = cachedDistance
    }
}
```

### 7. WidgetService → App Widgets + SharedPreferences

```kotlin
// Configuration Widget Provider
class Love2LoveAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Récupérer données partagées
        val widgetData = WidgetDataManager.getInstance(context).getWidgetData()

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, widgetData)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        widgetData: WidgetData?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_love2love)

        if (widgetData != null) {
            views.setTextViewText(R.id.widget_days_total, widgetData.daysTotal.toString())
            views.setTextViewText(R.id.widget_distance, widgetData.distance ?: "?")

            // Charger images de profil depuis cache partagé
            loadWidgetImages(context, views, widgetData)
        } else {
            // État par défaut
            views.setTextViewText(R.id.widget_days_total, "?")
            views.setTextViewText(R.id.widget_distance, "?")
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun loadWidgetImages(context: Context, views: RemoteViews, widgetData: WidgetData) {
        try {
            // Récupérer images depuis cache partagé (équivalent App Groups)
            val cacheDir = File(context.cacheDir, "WidgetCache")

            if (widgetData.userImageFileName != null) {
                val userImageFile = File(cacheDir, widgetData.userImageFileName)
                if (userImageFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(userImageFile.absolutePath)
                    views.setImageViewBitmap(R.id.widget_user_image, bitmap)
                }
            }

            if (widgetData.partnerImageFileName != null) {
                val partnerImageFile = File(cacheDir, widgetData.partnerImageFileName)
                if (partnerImageFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(partnerImageFile.absolutePath)
                    views.setImageViewBitmap(R.id.widget_partner_image, bitmap)
                }
            }

        } catch (e: Exception) {
            Log.e("WidgetProvider", "❌ Erreur chargement images widget: ${e.message}")
        }
    }
}

// Manager données widget
class WidgetDataManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: WidgetDataManager? = null

        fun getInstance(context: Context): WidgetDataManager {
            return INSTANCE ?: synchronized(this) {
                val instance = WidgetDataManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val sharedPrefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
    private val cacheDir = File(context.cacheDir, "WidgetCache")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    data class WidgetData(
        val daysTotal: Int,
        val distance: String?,
        val userImageFileName: String?,
        val partnerImageFileName: String?
    )

    fun saveWidgetData(
        daysTotal: Int,
        distance: String?,
        userImageBitmap: Bitmap? = null,
        partnerImageBitmap: Bitmap? = null
    ) {
        var userImageFileName: String? = null
        var partnerImageFileName: String? = null

        // Sauvegarder images dans cache partagé
        userImageBitmap?.let { bitmap ->
            userImageFileName = "user_profile_widget.jpg"
            saveImageToCache(bitmap, userImageFileName!!)
        }

        partnerImageBitmap?.let { bitmap ->
            partnerImageFileName = "partner_profile_widget.jpg"
            saveImageToCache(bitmap, partnerImageFileName!!)
        }

        // Sauvegarder données texte
        sharedPrefs.edit()
            .putInt("widget_days_total", daysTotal)
            .putString("widget_distance", distance)
            .putString("widget_user_image", userImageFileName)
            .putString("widget_partner_image", partnerImageFileName)
            .putLong("widget_last_update", System.currentTimeMillis())
            .apply()

        // Notifier widgets de la mise à jour
        updateAllWidgets()
    }

    fun getWidgetData(): WidgetData? {
        return try {
            WidgetData(
                daysTotal = sharedPrefs.getInt("widget_days_total", 0),
                distance = sharedPrefs.getString("widget_distance", null),
                userImageFileName = sharedPrefs.getString("widget_user_image", null),
                partnerImageFileName = sharedPrefs.getString("widget_partner_image", null)
            )
        } catch (e: Exception) {
            Log.e("WidgetDataManager", "❌ Erreur récupération données widget: ${e.message}")
            null
        }
    }

    private fun saveImageToCache(bitmap: Bitmap, fileName: String) {
        try {
            val file = File(cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.close()
            Log.d("WidgetDataManager", "✅ Image widget sauvée: $fileName")
        } catch (e: Exception) {
            Log.e("WidgetDataManager", "❌ Erreur sauvegarde image widget: ${e.message}")
        }
    }

    private fun updateAllWidgets() {
        val intent = Intent(context, Love2LoveAppWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, Love2LoveAppWidgetProvider::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)

        context.sendBroadcast(intent)
    }
}

// Service synchronisation (équivalent WidgetService iOS)
class WidgetSyncService @Inject constructor(
    private val widgetDataManager: WidgetDataManager,
    private val imageCache: ImageCacheService,
    private val userCache: UserCacheManager
) {

    fun syncWidgetData(
        relationshipStats: RelationshipStats?,
        distanceInfo: DistanceInfo?,
        userImageUrl: String?,
        partnerImageUrl: String?
    ) {
        GlobalScope.launch {
            try {
                var userBitmap: Bitmap? = null
                var partnerBitmap: Bitmap? = null

                // Récupérer images depuis cache ou télécharger
                userImageUrl?.let { url ->
                    userBitmap = imageCache.getCachedImage(url)
                        ?: downloadAndResizeImage(url, 150)
                }

                partnerImageUrl?.let { url ->
                    partnerBitmap = imageCache.getCachedImage(url)
                        ?: downloadAndResizeImage(url, 150)
                }

                // Sauvegarder données widget
                widgetDataManager.saveWidgetData(
                    daysTotal = relationshipStats?.daysTotal ?: 0,
                    distance = distanceInfo?.let { "${it.distance} ${it.unit}" },
                    userImageBitmap = userBitmap,
                    partnerImageBitmap = partnerBitmap
                )

                Log.d("WidgetSyncService", "✅ Données widget synchronisées")

            } catch (e: Exception) {
                Log.e("WidgetSyncService", "❌ Erreur sync widget: ${e.message}")
            }
        }
    }

    private suspend fun downloadAndResizeImage(url: String, size: Int): Bitmap? {
        return try {
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(url)
                .submit(size, size)
                .get()

            // Mettre en cache pour utilisation future
            imageCache.cacheImage(bitmap, url)
            bitmap

        } catch (e: Exception) {
            Log.e("WidgetSyncService", "❌ Erreur téléchargement image: ${e.message}")
            null
        }
    }
}
```

### 8. Configuration Réseau Android (Retrofit + OkHttp)

```kotlin
// Configuration cache réseau (équivalent URLCache iOS)
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheSize = 50L * 1024L * 1024L // 50 MB
        val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize)

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .addNetworkInterceptor { chain ->
                // Ajouter headers de cache
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=300") // 5 minutes
                    .build()
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://your-firebase-functions-url/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

// Utilisation avec cache automatique
interface ApiService {
    @GET("getPartnerInfo")
    suspend fun getPartnerInfo(@Query("partnerId") partnerId: String): Response<PartnerInfoResponse>

    @GET("getPartnerLocation")
    suspend fun getPartnerLocation(@Query("partnerId") partnerId: String): Response<LocationResponse>
}

// Repository avec cache intelligent
class PartnerRepository @Inject constructor(
    private val apiService: ApiService,
    private val cacheManager: PartnerCacheManager
) {

    suspend fun getPartnerInfo(partnerId: String, forceRefresh: Boolean = false): Result<PartnerInfo> {
        return try {
            // Vérifier cache local si pas de refresh forcé
            if (!forceRefresh) {
                val cachedInfo = cacheManager.getCachedPartnerInfo(partnerId)
                if (cachedInfo != null && !cacheManager.isPartnerInfoExpired(partnerId)) {
                    return Result.success(cachedInfo)
                }
            }

            // Appel réseau avec cache HTTP automatique
            val response = apiService.getPartnerInfo(partnerId)

            if (response.isSuccessful) {
                val partnerInfo = response.body()?.toPartnerInfo()
                if (partnerInfo != null) {
                    // Mettre en cache local
                    cacheManager.cachePartnerInfo(partnerId, partnerInfo)
                    Result.success(partnerInfo)
                } else {
                    Result.failure(Exception("Réponse vide"))
                }
            } else {
                Result.failure(Exception("Erreur HTTP: ${response.code()}"))
            }

        } catch (e: Exception) {
            // Fallback vers cache local même expiré si erreur réseau
            val cachedInfo = cacheManager.getCachedPartnerInfo(partnerId)
            if (cachedInfo != null) {
                Log.w("PartnerRepository", "🔄 Utilisation cache expiré en fallback")
                Result.success(cachedInfo)
            } else {
                Result.failure(e)
            }
        }
    }
}
```

---

## 🎯 STRATÉGIES OPTIMISATIONS ANDROID

### ⚡ Performances Cache

```kotlin
// 1. Préchargement intelligent
class CachePreloader @Inject constructor(
    private val userCache: UserCacheManager,
    private val imageCache: ImageCacheService,
    private val questionCache: QuestionCacheManager
) {

    suspend fun preloadCriticalData() {
        withContext(Dispatchers.IO) {
            // Précharger données essentielles en parallèle
            val userDeferred = async { userCache.getCachedUser() }
            val questionsDeferred = async { questionCache.getCachedDailyQuestions("", 5) }

            val user = userDeferred.await()
            val questions = questionsDeferred.await()

            Log.d("CachePreloader", "✅ Préchargement terminé - User: ${user != null}, Questions: ${questions.size}")
        }
    }
}

// 2. Nettoyage automatique
class CacheCleanupManager @Inject constructor(
    private val context: Context,
    private val imageCache: ImageCacheService
) {

    fun schedulePeriodicCleanup() {
        val workRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresCharging(false)
                    .build()
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("cache_cleanup", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }
}

class CacheCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Nettoyer caches selon critères
            cleanupExpiredCache()
            cleanupOversizedCache()
            Result.success()
        } catch (e: Exception) {
            Log.e("CacheCleanup", "❌ Erreur nettoyage: ${e.message}")
            Result.failure()
        }
    }

    private suspend fun cleanupExpiredCache() {
        // Nettoyer données utilisateur expirées
        val userCache = UserCacheManager.getInstance(applicationContext)
        userCache.getCachedUser() // Auto-nettoyage si expiré

        // Nettoyer images non utilisées depuis 30 jours
        val cacheDir = File(applicationContext.cacheDir, "ImageCache")
        val cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)

        cacheDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
                Log.d("CacheCleanup", "🗑️ Supprimé: ${file.name}")
            }
        }
    }

    private suspend fun cleanupOversizedCache() {
        val cacheDir = File(applicationContext.cacheDir, "ImageCache")
        val maxSize = 200L * 1024L * 1024L // 200MB max

        var currentSize = cacheDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()

        if (currentSize > maxSize) {
            // Supprimer les plus anciens fichiers jusqu'à être sous la limite
            val files = cacheDir.listFiles()
                ?.sortedBy { it.lastModified() }
                ?: emptyArray()

            for (file in files) {
                if (currentSize <= maxSize) break
                currentSize -= file.length()
                file.delete()
                Log.d("CacheCleanup", "🗑️ Cache oversized - Supprimé: ${file.name}")
            }
        }
    }
}

// 3. Métriques cache
class CacheMetricsManager @Inject constructor(
    private val context: Context
) {

    data class CacheMetrics(
        val memoryCacheHitRate: Float,
        val diskCacheSize: Long,
        val totalCacheSize: Long,
        val lastCleanup: Long
    )

    fun getCacheMetrics(): CacheMetrics {
        val cacheDir = File(context.cacheDir)
        val totalSize = cacheDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()

        val imageCacheSize = File(cacheDir, "ImageCache")
            .walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()

        val sharedPrefs = context.getSharedPreferences("cache_metrics", Context.MODE_PRIVATE)
        val hitRate = sharedPrefs.getFloat("memory_hit_rate", 0f)
        val lastCleanup = sharedPrefs.getLong("last_cleanup", 0)

        return CacheMetrics(
            memoryCacheHitRate = hitRate,
            diskCacheSize = imageCacheSize,
            totalCacheSize = totalSize,
            lastCleanup = lastCleanup
        )
    }
}
```

---

## 🎯 POINTS CLÉS SYSTÈME CACHE

✅ **Architecture Multi-Niveaux** : 8 types cache différents selon besoins  
✅ **Performance Optimisée** : Mémoire > UserDefaults > Realm > Disque > Réseau  
✅ **Cache-First Strategy** : Affichage instantané + sync background  
✅ **App Groups Integration** : Partage app/widgets via container partagé  
✅ **Invalidation Intelligente** : Expiration automatique + détection changements  
✅ **Offline Capable** : Realm + ImageCache permettent usage hors ligne  
✅ **Security Aware** : Hash URLs, logs sécurisés, pas d'exposition tokens  
✅ **Memory Management** : NSCache avec limites, compactage Realm automatique  
✅ **Error Recovery** : Fallback gracieux, nettoyage données corrompues  
✅ **Real-time Sync** : Firestore listeners pour favoris/journal temps réel

Le système de cache Love2Love présente une **architecture sophistiquée multi-niveaux** avec une **stratégie cache-first** pour une **UX instantanée**, une **intégration App Groups** pour les widgets, et une **adaptation Android complète** utilisant Room, Glide, SharedPreferences et App Widgets ! 💾✨

**Fichier :** `RAPPORT_SYSTEME_CACHE_COMPLET.md`
