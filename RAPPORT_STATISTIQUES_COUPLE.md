# 📊 RAPPORT TECHNIQUE - Fonctionnalité Statistiques Couple

## Vue d'Ensemble

La fonctionnalité de statistiques couple dans Love2Love affiche 4 métriques principales sur la page d'accueil :

1. **Jours ensemble** - Nombre de jours depuis le début de la relation
2. **Questions répondues** - Pourcentage de progression sur toutes les catégories
3. **Villes visitées** - Nombre de villes uniques basé sur les entrées de journal
4. **Pays visités** - Nombre de pays uniques basé sur les entrées de journal

## Architecture et Composants

### 📱 Vue Principale : `CoupleStatisticsView`

**Fichier :** `Views/Components/CoupleStatisticsView.swift`

La vue principale utilise une grille 2x2 avec des cartes personnalisées (`StatisticCardView`).

```swift
// Grille de statistiques 2x2
LazyVGrid(columns: [
    GridItem(.flexible(), spacing: 16),
    GridItem(.flexible(), spacing: 16)
], spacing: 16)
```

**Services injectés :**

- `@EnvironmentObject var appState: AppState` - État global de l'app
- `@StateObject private var journalService = JournalService.shared` - Gestion du journal
- `@StateObject private var categoryProgressService = CategoryProgressService.shared` - Progression des catégories

### 📊 Cycle de vie et Actualisation

**Au chargement de la vue (`onAppear`) :**

1. Force le recalcul du pourcentage de questions
2. Lance le géocodage rétroactif des entrées de journal manquantes (max 3 par session)

**Écoute des changements :**

```swift
.onReceive(categoryProgressService.$categoryProgress) { newProgress in
    // Recalcule automatiquement le pourcentage quand la progression change
}
```

---

## 📈 Détail de Chaque Statistique

### 1. 📅 **Jours Ensemble**

**Source de données :** `appState.currentUser?.relationshipStartDate`

**Calcul :**

```swift
private var daysTogetherCount: Int {
    guard let relationshipStartDate = appState.currentUser?.relationshipStartDate else {
        return 0
    }

    let calendar = Calendar.current
    let dayComponents = calendar.dateComponents([.day], from: relationshipStartDate, to: Date())
    return max(dayComponents.day ?? 0, 0)
}
```

**Processus :**

1. Récupère la date de début de relation depuis `User.relationshipStartDate`
2. Calcule la différence en jours avec `Date()` actuelle
3. Utilise `Calendar.dateComponents` pour un calcul précis
4. Retourne 0 si pas de date configurée

**Cache :** Aucun - calcul en temps réel basé sur l'utilisateur connecté.

**Affichage :**

- **Icône :** "jours" (image asset)
- **Couleurs :** Rose (`#feb5c8`, `#fedce3`, `#db3556`)
- **Format :** Nombre entier (ex: "142")

---

### 2. ❓ **Questions Répondues (%)**

**Source de données :** `CategoryProgressService` + `QuestionDataManager`

**Calcul complexe :**

```swift
private var questionsProgressPercentage: Double {
    let categories = QuestionCategory.categories
    var totalQuestions = 0
    var totalProgress = 0

    for category in categories {
        let questions = getQuestionsForCategory(category.id)
        let currentIndex = categoryProgressService.getCurrentIndex(for: category.id)

        totalQuestions += questions.count
        totalProgress += min(currentIndex + 1, questions.count)
    }

    guard totalQuestions > 0 else { return 0.0 }
    let percentage = (Double(totalProgress) / Double(totalQuestions)) * 100.0
    return percentage
}
```

**Processus détaillé :**

1. **Récupération des catégories :**

   - Utilise `QuestionCategory.categories` (8 catégories définies)
   - Itère sur chaque catégorie (gratuite + premium)

2. **Chargement des questions par catégorie :**

   ```swift
   private func getQuestionsForCategory(_ categoryId: String) -> [Question] {
       return QuestionDataManager.shared.loadQuestions(for: categoryId)
   }
   ```

3. **Récupération de la progression :**

   - `categoryProgressService.getCurrentIndex(for: category.id)` depuis UserDefaults
   - Index 0-based, donc +1 pour le nombre de questions répondues

4. **Calcul global :**
   - Somme toutes les questions de toutes les catégories
   - Somme toute la progression (questions répondues)
   - Pourcentage = (progression / total) × 100

**Système de Cache Multi-Niveaux :**

#### Niveau 1 : Cache In-Memory (`QuestionDataManager`)

```swift
private var questionsCache: [String: [Question]] = [:]

func loadQuestions(for categoryId: String, language: String) -> [Question] {
    let cacheKey = "\(categoryId)_\(language)"

    // Vérifier le cache d'abord
    if let cachedQuestions = questionsCache[cacheKey] {
        return cachedQuestions
    }

    // Charger et cacher
    let questions = loadQuestionsFromStringCatalogs(categoryId: categoryId)
    questionsCache[cacheKey] = questions
    return questions
}
```

#### Niveau 2 : Cache Realm (`QuestionCacheManager`)

```swift
func getQuestionsWithSmartCache(for category: String, fallback: () -> [Question]) -> [Question] {
    // 1. Cache Realm d'abord
    let cachedQuestions = getCachedQuestions(for: category)
    if !cachedQuestions.isEmpty {
        return cachedQuestions
    }

    // 2. QuestionDataManager (String Catalogs)
    let freshQuestions = QuestionDataManager.shared.loadQuestions(for: category)
    if !freshQuestions.isEmpty {
        cacheQuestions(for: category, questions: freshQuestions)
        return freshQuestions
    }

    // 3. Fallback
    return fallback()
}
```

#### Niveau 3 : Source Principale (String Catalogs `.xcstrings`)

```swift
private func loadQuestionsFromStringCatalogs(categoryId: String) -> [Question] {
    let mapping: [String: (prefix: String, table: String, start: Int)] = [
        "en-couple": ("ec_", "EnCouple", 2),
        "les-plus-hots": ("lph_", "LesPlus Hots", 2),
        "pour-rire-a-deux": ("prad_", "PourRire", 2),
        // ... autres catégories
    ]

    // Génère les clés et récupère via NSLocalizedString
    for i in startIndex...300 {
        let key = "\(keyPrefix)\(i)"
        let localizedText = localizedString(key, tableName: tableName)
        if localizedText != key && !localizedText.isEmpty {
            questions.append(Question(id: key, text: localizedText, category: categoryId))
        }
    }
}
```

**Progression Persistante :**

```swift
// Sauvegarde dans UserDefaults
private func saveProgress() {
    if let encoded = try? JSONEncoder().encode(categoryProgress) {
        userDefaults.set(encoded, forKey: categoryProgressKey)
    }
}

// Récupération
func getCurrentIndex(for categoryId: String) -> Int {
    return categoryProgress[categoryId] ?? 0
}
```

**Affichage :**

- **Icône :** "qst" (image asset)
- **Couleurs :** Orange (`#fed397`, `#fde9cf`, `#ffa229`)
- **Format :** Pourcentage entier avec "%" (ex: "67%")

---

### 3. 🏙️ **Villes Visitées**

**Source de données :** `JournalService.entries` avec géolocalisation

**Calcul :**

```swift
private var citiesVisitedCount: Int {
    let uniqueCities = Set(journalService.entries.compactMap { entry in
        entry.location?.city?.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty })

    return uniqueCities.count
}
```

**Structure des Données :**

#### `JournalEntry` avec Géolocalisation

```swift
struct JournalEntry {
    var location: JournalLocation? // Propriété de localisation
    // ... autres propriétés
}

struct JournalLocation: Codable {
    let latitude: Double
    let longitude: Double
    let address: String?
    let city: String?        // ← Utilisé pour les statistiques
    let country: String?     // ← Utilisé pour les statistiques
}
```

**Processus de Récupération des Données :**

1. **Listener Firestore Temps Réel :**

```swift
private func setupListener() {
    listener = db.collection("journalEntries")
        .whereField("partnerIds", arrayContains: currentUser.uid)
        .addSnapshotListener { [weak self] snapshot, error in
            self?.handleSnapshotUpdate(snapshot: snapshot)
        }
}
```

2. **Traitement des Entrées :**

```swift
private func handleSnapshotUpdate(snapshot: QuerySnapshot?) {
    let newEntries = documents.compactMap { JournalEntry(from: $0) }

    DispatchQueue.main.async {
        self.entries = newEntries
        self.entries.sort { $0.eventDate > $1.eventDate }
    }
}
```

3. **Déchiffrement des Coordonnées :**

```swift
// Dans JournalEntry.init(from document:)
if let locationData = LocationEncryptionService.readLocation(from: data) {
    let coordinate = locationData.toCLLocation().coordinate

    // Récupérer les métadonnées
    address = data["locationAddress"] as? String
    city = data["locationCity"] as? String      // ← Données utilisées
    country = data["locationCountry"] as? String // ← Données utilisées

    self.location = JournalLocation(
        coordinate: coordinate,
        address: address,
        city: city,
        country: country
    )
}
```

**Géocodage Rétroactif :**

Pour réparer les entrées existantes sans ville/pays :

```swift
private func repairJournalEntriesGeocoding() async {
    let entriesToRepair = journalService.entries.filter { entry in
        guard let location = entry.location else { return false }
        let needsRepair = location.city == nil || location.country == nil ||
                         location.city?.isEmpty == true || location.country?.isEmpty == true
        return needsRepair
    }

    // Limiter à 3 réparations par session pour éviter de surcharger l'API
    let limitedEntries = Array(entriesToRepair.prefix(3))

    for entry in limitedEntries {
        await repairSingleEntryGeocoding(entry)
    }
}

private func repairSingleEntryGeocoding(_ entry: JournalEntry) async {
    let clLocation = CLLocation(latitude: location.latitude, longitude: location.longitude)
    let geocoder = CLGeocoder()

    let placemarks = try await geocoder.reverseGeocodeLocation(clLocation)

    if let placemark = placemarks.first {
        let repairedLocation = JournalLocation(
            coordinate: location.coordinate,
            address: location.address ?? placemark.name,
            city: placemark.locality,        // ← Ajout de la ville
            country: placemark.country       // ← Ajout du pays
        )

        var updatedEntry = entry
        updatedEntry.location = repairedLocation
        updatedEntry.updatedAt = Date()

        try await journalService.updateEntry(updatedEntry)
    }
}
```

**Cache et Performance :**

- **Listener temps réel :** Les entrées sont automatiquement synchronisées
- **Pas de cache spécifique :** Les données viennent directement de `journalService.entries`
- **Optimisation :** Calcul uniquement avec `Set()` pour éliminer les doublons

**Affichage :**

- **Icône :** "ville" (image asset)
- **Couleurs :** Bleu (`#b0d6fe`, `#dbecfd`, `#0a85ff`)
- **Format :** Nombre entier (ex: "12")

---

### 4. 🌍 **Pays Visités**

**Source de données :** `JournalService.entries` avec géolocalisation (même que villes)

**Calcul :**

```swift
private var countriesVisitedCount: Int {
    let uniqueCountries = Set(journalService.entries.compactMap { entry in
        entry.location?.country?.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty })

    return uniqueCountries.count
}
```

**Processus identique aux villes mais utilise :**

- `entry.location?.country` au lieu de `city`
- Même géocodage rétroactif avec `placemark.country`
- Même système de chiffrement/déchiffrement des coordonnées

**Affichage :**

- **Icône :** "pays" (image asset)
- **Couleurs :** Violet (`#d1b3ff`, `#e8dcff`, `#7c3aed`)
- **Format :** Nombre entier (ex: "5")

---

## 🔐 Sécurité et Chiffrement

### Chiffrement des Coordonnées

**Service :** `LocationEncryptionService`

```swift
// Écriture chiffrée
static func processLocationForStorage(_ location: CLLocation) -> [String: Any] {
    return writeLocation(location) ?? [:]
}

// Lecture déchiffrée
static func extractLocation(from journalData: [String: Any]) -> CLLocation? {
    return readLocation(from: journalData)?.toCLLocation()
}
```

**Stockage Hybride dans Firestore :**

```swift
// Nouveau format chiffré pour les coordonnées sensibles
let encryptedLocationData = LocationEncryptionService.processLocationForStorage(clLocation)
dict.merge(encryptedLocationData) { (_, new) in new }

// Métadonnées non sensibles en clair
if let city = location.city {
    dict["locationCity"] = city     // ← Utilisé pour les statistiques
}
if let country = location.country {
    dict["locationCountry"] = country // ← Utilisé pour les statistiques
}
```

---

## 🎨 Interface Utilisateur

### Composant `StatisticCardView`

Chaque statistique utilise une carte personnalisée avec :

```swift
VStack(spacing: 0) {
    // Ligne du haut : Icône à droite
    HStack {
        Spacer()
        Image(icon)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: 40, height: 40)
            .foregroundColor(iconColor)
    }

    Spacer()

    // Ligne du bas : Valeur + Titre à gauche
    HStack {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(textColor)

            Text(title)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(textColor)
        }
        Spacer()
    }
}
.frame(maxWidth: .infinity)
.frame(height: 140)
.padding(16)
.background(
    RoundedRectangle(cornerRadius: 16)
        .fill(backgroundColor)
        .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 2)
)
```

### Intégration dans la Page Principale

**Fichier :** `Views/Main/MainView.swift`

```swift
// Section Statistiques sur le couple
CoupleStatisticsView()
    .environmentObject(appState)
    .padding(.top, 30)
```

Position dans l'interface :

1. Header avec nom d'utilisateur
2. Catégories de questions
3. Section widget
4. **📊 Section statistiques couple** ← Ici
5. Menu de navigation

---

## 🚀 Performance et Optimisations

### 1. **Cache Multi-Niveaux pour les Questions**

```
Level 1: QuestionDataManager in-memory cache
    ↓ (Miss)
Level 2: Realm database cache
    ↓ (Miss)
Level 3: String Catalogs (.xcstrings files)
```

### 2. **Limite du Géocodage Rétroactif**

- Maximum 3 réparations par session
- Évite de surcharger l'API Core Location
- Traitement asynchrone en arrière-plan

### 3. **Calculs Optimisés**

- `Set()` pour éliminer les doublons automatiquement
- `compactMap` pour filtrer les valeurs nil
- Calculs lazy (computed properties) recalculés seulement si nécessaire

### 4. **Listener Temps Réel Optimisé**

```swift
// Une seule requête pour toutes les entrées du couple
.whereField("partnerIds", arrayContains: currentUser.uid)
```

---

# 📱 IMPLÉMENTATION ANDROID GO ÉQUIVALENTE

## Architecture Recommandée

### 🏗️ Structure des Composants

```kotlin
// Composable principal
@Composable
fun CoupleStatisticsSection(
    modifier: Modifier = Modifier,
    viewModel: CoupleStatisticsViewModel
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 4 statistiques identiques à iOS
        item { StatisticCard(statistic = viewModel.daysTogetherStat) }
        item { StatisticCard(statistic = viewModel.questionsProgressStat) }
        item { StatisticCard(statistic = viewModel.citiesVisitedStat) }
        item { StatisticCard(statistic = viewModel.countriesVisitedStat) }
    }
}
```

### 📊 ViewModel avec State Management

```kotlin
class CoupleStatisticsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val journalRepository: JournalRepository,
    private val categoryProgressRepository: CategoryProgressRepository,
    private val questionRepository: QuestionRepository
) : ViewModel() {

    private val _statisticsState = MutableStateFlow(CoupleStatisticsState())
    val statisticsState = _statisticsState.asStateFlow()

    data class CoupleStatisticsState(
        val daysTogetherCount: Int = 0,
        val questionsProgressPercentage: Double = 0.0,
        val citiesVisitedCount: Int = 0,
        val countriesVisitedCount: Int = 0,
        val isLoading: Boolean = false
    )

    init {
        observeDataChanges()
    }

    private fun observeDataChanges() {
        viewModelScope.launch {
            combine(
                userRepository.getCurrentUser(),
                journalRepository.getAllEntries(),
                categoryProgressRepository.getAllProgress()
            ) { user, entries, progress ->
                calculateStatistics(user, entries, progress)
            }.collect { stats ->
                _statisticsState.value = stats
            }
        }
    }
}
```

---

## 📈 Implémentation de Chaque Statistique

### 1. 📅 **Jours Ensemble - Android**

```kotlin
// Dans CoupleStatisticsViewModel
private fun calculateDaysTogether(user: User?): Int {
    val relationshipStartDate = user?.relationshipStartDate ?: return 0

    val startLocalDate = Instant.ofEpochMilli(relationshipStartDate)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    val today = LocalDate.now()

    return ChronoUnit.DAYS.between(startLocalDate, today).toInt()
        .coerceAtLeast(0)
}
```

**Repository Pattern :**

```kotlin
interface UserRepository {
    suspend fun getCurrentUser(): Flow<User?>
    suspend fun updateRelationshipStartDate(date: Long)
}

@Repository
class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : UserRepository {

    override suspend fun getCurrentUser(): Flow<User?> =
        authRepository.getCurrentUserId().flatMapLatest { userId ->
            if (userId != null) {
                firestore.collection("users")
                    .document(userId)
                    .snapshots()
                    .map { it.toObject<User>() }
            } else {
                flowOf(null)
            }
        }
}
```

---

### 2. ❓ **Questions Répondues (%) - Android**

```kotlin
private suspend fun calculateQuestionsProgress(): Double {
    val categories = questionRepository.getAllCategories()
    var totalQuestions = 0
    var totalProgress = 0

    categories.forEach { category ->
        val questions = questionRepository.getQuestionsForCategory(category.id)
        val currentIndex = categoryProgressRepository.getCurrentIndex(category.id)

        totalQuestions += questions.size
        totalProgress += minOf(currentIndex + 1, questions.size)
    }

    return if (totalQuestions > 0) {
        (totalProgress.toDouble() / totalQuestions.toDouble()) * 100.0
    } else {
        0.0
    }
}
```

**Cache Multi-Niveaux Android :**

#### Level 1: In-Memory Cache

```kotlin
class QuestionCacheManager @Singleton constructor() {
    private val memoryCache = LruCache<String, List<Question>>(10) // 10 catégories max

    fun getQuestions(categoryId: String): List<Question>? {
        return memoryCache.get(categoryId)
    }

    fun cacheQuestions(categoryId: String, questions: List<Question>) {
        memoryCache.put(categoryId, questions)
    }
}
```

#### Level 2: Room Database Cache

```kotlin
@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE category = :categoryId")
    suspend fun getQuestionsForCategory(categoryId: String): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)
}

@Database(
    entities = [QuestionEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
}
```

#### Level 3: String Resources (équivalent String Catalogs)

```kotlin
class QuestionResourceLoader @Inject constructor(
    private val context: Context
) {
    private val categoryMapping = mapOf(
        "en-couple" to Pair("ec_", "en_couple"),
        "les-plus-hots" to Pair("lph_", "les_plus_hots"),
        "pour-rire-a-deux" to Pair("prd_", "pour_rire"),
        // ... autres catégories
    )

    fun loadQuestionsFromResources(categoryId: String): List<Question> {
        val (prefix, resourceName) = categoryMapping[categoryId] ?: return emptyList()
        val questions = mutableListOf<Question>()

        for (i in 2..300) {
            val resourceId = context.resources.getIdentifier(
                "${prefix}${i}",
                "string",
                context.packageName
            )

            if (resourceId != 0) {
                val questionText = context.getString(resourceId)
                questions.add(Question(
                    id = "${prefix}${i}",
                    text = questionText,
                    category = categoryId
                ))
            } else if (i > 12 && questions.isEmpty()) {
                break
            }
        }

        return questions
    }
}
```

**Repository avec Cache Smart :**

```kotlin
class QuestionRepositoryImpl @Inject constructor(
    private val questionDao: QuestionDao,
    private val resourceLoader: QuestionResourceLoader,
    private val cacheManager: QuestionCacheManager
) : QuestionRepository {

    override suspend fun getQuestionsForCategory(categoryId: String): List<Question> {
        // Level 1: Memory cache
        cacheManager.getQuestions(categoryId)?.let { return it }

        // Level 2: Room database
        val cachedQuestions = questionDao.getQuestionsForCategory(categoryId)
        if (cachedQuestions.isNotEmpty()) {
            val questions = cachedQuestions.map { it.toQuestion() }
            cacheManager.cacheQuestions(categoryId, questions)
            return questions
        }

        // Level 3: Resources
        val resourceQuestions = resourceLoader.loadQuestionsFromResources(categoryId)
        if (resourceQuestions.isNotEmpty()) {
            // Cache in database and memory
            questionDao.insertQuestions(resourceQuestions.map { it.toEntity(categoryId) })
            cacheManager.cacheQuestions(categoryId, resourceQuestions)
        }

        return resourceQuestions
    }
}
```

**Progression Persistante avec DataStore :**

```kotlin
@Singleton
class CategoryProgressRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val CATEGORY_PROGRESS_KEY = "category_progress"

    suspend fun saveCurrentIndex(categoryId: String, index: Int) {
        dataStore.edit { preferences ->
            val currentProgress = preferences[stringPreferencesKey(CATEGORY_PROGRESS_KEY)]
                ?.let { Json.decodeFromString<Map<String, Int>>(it) } ?: emptyMap()

            val updatedProgress = currentProgress + (categoryId to index)
            preferences[stringPreferencesKey(CATEGORY_PROGRESS_KEY)] =
                Json.encodeToString(updatedProgress)
        }
    }

    suspend fun getCurrentIndex(categoryId: String): Int {
        return dataStore.data.first().let { preferences ->
            val progressMap = preferences[stringPreferencesKey(CATEGORY_PROGRESS_KEY)]
                ?.let { Json.decodeFromString<Map<String, Int>>(it) } ?: emptyMap()
            progressMap[categoryId] ?: 0
        }
    }

    fun getAllProgress(): Flow<Map<String, Int>> = dataStore.data.map { preferences ->
        preferences[stringPreferencesKey(CATEGORY_PROGRESS_KEY)]
            ?.let { Json.decodeFromString<Map<String, Int>>(it) } ?: emptyMap()
    }
}
```

---

### 3. 🏙️ **Villes Visitées - Android**

```kotlin
private suspend fun calculateCitiesVisited(entries: List<JournalEntry>): Int {
    return entries
        .mapNotNull { entry ->
            entry.location?.city?.trim()?.takeIf { it.isNotBlank() }
        }
        .toSet()
        .size
}
```

**Structure des Données Android :**

```kotlin
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val eventDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val authorId: String,
    val authorName: String,
    val imageURL: String?,
    val localImagePath: String?,
    val isShared: Boolean,
    val partnerIds: String, // JSON Array as String
    // Données de localisation
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    val locationAddress: String?,
    val locationCity: String?,      // ← Pour statistiques villes
    val locationCountry: String?    // ← Pour statistiques pays
)

data class JournalLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null
) {
    val displayName: String
        get() = when {
            city != null && country != null -> "$city, $country"
            address != null -> address
            else -> "Localisation"
        }
}

data class JournalEntry(
    val id: String,
    // ... autres propriétés
    val location: JournalLocation?
)
```

**Repository avec Firestore Listener :**

```kotlin
class JournalRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val journalDao: JournalDao,
    private val locationEncryption: LocationEncryptionService
) : JournalRepository {

    override fun getAllEntries(): Flow<List<JournalEntry>> =
        authRepository.getCurrentUserId().flatMapLatest { userId ->
            if (userId != null) {
                firestore.collection("journalEntries")
                    .whereArrayContains("partnerIds", userId)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            parseJournalEntry(doc)
                        }
                    }
            } else {
                flowOf(emptyList())
            }
        }

    private fun parseJournalEntry(document: DocumentSnapshot): JournalEntry? {
        val data = document.data ?: return null

        // Déchiffrer les coordonnées si présentes
        val location = parseLocation(data)

        return JournalEntry(
            id = document.id,
            // ... autres champs
            location = location
        )
    }

    private fun parseLocation(data: Map<String, Any>): JournalLocation? {
        // 1. Essayer de déchiffrer les coordonnées
        val coordinates = locationEncryption.extractLocation(data)

        if (coordinates != null) {
            // 2. Récupérer les métadonnées non chiffrées
            val address = data["locationAddress"] as? String
            val city = data["locationCity"] as? String
            val country = data["locationCountry"] as? String

            return JournalLocation(
                latitude = coordinates.latitude,
                longitude = coordinates.longitude,
                address = address,
                city = city,
                country = country
            )
        }

        return null
    }
}
```

**Géocodage Rétroactif Android :**

```kotlin
class LocationRepairService @Inject constructor(
    private val geocoder: Geocoder,
    private val journalRepository: JournalRepository
) {

    suspend fun repairMissingLocationData() {
        val entriesToRepair = journalRepository.getAllEntries()
            .first() // Get current snapshot
            .filter { entry ->
                entry.location?.let { location ->
                    location.city.isNullOrBlank() || location.country.isNullOrBlank()
                } ?: false
            }
            .take(3) // Limiter à 3 par session

        entriesToRepair.forEach { entry ->
            repairSingleEntry(entry)
        }
    }

    private suspend fun repairSingleEntry(entry: JournalEntry) {
        val location = entry.location ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API moderne
                val addresses = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )

                addresses?.firstOrNull()?.let { address ->
                    updateEntryWithGeocodedData(entry, address)
                }
            } else {
                // API legacy avec callback
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            addresses.firstOrNull()?.let { address ->
                                viewModelScope.launch {
                                    updateEntryWithGeocodedData(entry, address)
                                }
                            }
                        }
                        override fun onError(errorMessage: String?) {
                            // Géocodage échoué, continuer silencieusement
                        }
                    }
                )
            }
        } catch (e: Exception) {
            // Géocodage échoué, continuer
        }
    }

    private suspend fun updateEntryWithGeocodedData(entry: JournalEntry, address: Address) {
        val repairedLocation = entry.location?.copy(
            address = entry.location.address ?: address.getAddressLine(0),
            city = address.locality,
            country = address.countryName
        )

        if (repairedLocation != null) {
            val updatedEntry = entry.copy(
                location = repairedLocation,
                updatedAt = System.currentTimeMillis()
            )

            journalRepository.updateEntry(updatedEntry)
        }
    }
}
```

---

### 4. 🌍 **Pays Visités - Android**

```kotlin
private suspend fun calculateCountriesVisited(entries: List<JournalEntry>): Int {
    return entries
        .mapNotNull { entry ->
            entry.location?.country?.trim()?.takeIf { it.isNotBlank() }
        }
        .toSet()
        .size
}
```

Même logique que les villes mais utilise `entry.location?.country`.

---

## 🔐 Chiffrement Android

```kotlin
class LocationEncryptionService @Inject constructor(
    private val keyManager: EncryptionKeyManager
) {

    fun processLocationForStorage(latitude: Double, longitude: Double): Map<String, Any> {
        return try {
            val locationData = "$latitude,$longitude"
            val encryptedData = encrypt(locationData)
            mapOf(
                "encryptedLocation" to encryptedData,
                "locationVector" to generateInitVector(),
                "isEncrypted" to true
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun extractLocation(data: Map<String, Any>): LatLng? {
        return try {
            val encryptedLocation = data["encryptedLocation"] as? String ?: return null
            val vector = data["locationVector"] as? String ?: return null

            val decryptedData = decrypt(encryptedLocation, vector)
            val coords = decryptedData.split(",")

            if (coords.size == 2) {
                LatLng(coords[0].toDouble(), coords[1].toDouble())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun encrypt(data: String): String {
        val key = keyManager.getLocationEncryptionKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val encryptedBytes = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }

    private fun decrypt(encryptedData: String, initVector: String): String {
        val key = keyManager.getLocationEncryptionKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        val spec = GCMParameterSpec(128, Base64.decode(initVector, Base64.DEFAULT))
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT))
        return String(decryptedBytes)
    }
}
```

---

## 🎨 Interface Utilisateur Android

### Composable de Carte Statistique

```kotlin
@Composable
fun StatisticCard(
    statistic: StatisticData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = statistic.backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Icône en haut à droite
            Image(
                painter = painterResource(id = statistic.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd),
                colorFilter = ColorFilter.tint(statistic.iconColor)
            )

            // Valeur et titre en bas à gauche
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = statistic.value,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = statistic.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = statistic.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = statistic.textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

data class StatisticData(
    val title: String,
    val value: String,
    @DrawableRes val iconRes: Int,
    val iconColor: Color,
    val backgroundColor: Color,
    val textColor: Color
)
```

### Couleurs et Ressources

```kotlin
object StatisticColors {
    // Jours ensemble - Rose
    val DaysTogetherIcon = Color(0xFFfeb5c8)
    val DaysTogetherBackground = Color(0xFFfedce3)
    val DaysTogetherText = Color(0xFFdb3556)

    // Questions - Orange
    val QuestionsIcon = Color(0xFFfed397)
    val QuestionsBackground = Color(0xFFfde9cf)
    val QuestionsText = Color(0xFFffa229)

    // Villes - Bleu
    val CitiesIcon = Color(0xFFb0d6fe)
    val CitiesBackground = Color(0xFFdbecfd)
    val CitiesText = Color(0xFF0a85ff)

    // Pays - Violet
    val CountriesIcon = Color(0xFFd1b3ff)
    val CountriesBackground = Color(0xFFe8dcff)
    val CountriesText = Color(0xFF7c3aed)
}
```

---

## 🚀 Performance et Optimisations Android

### 1. **LaunchedEffect pour le Calcul Initial**

```kotlin
@Composable
fun CoupleStatisticsSection(
    viewModel: CoupleStatisticsViewModel
) {
    val state by viewModel.statisticsState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadInitialStatistics()
        viewModel.startLocationRepair()
    }

    // UI Components...
}
```

### 2. **StateFlow pour Réactivité**

```kotlin
class CoupleStatisticsViewModel {
    private val _statisticsState = MutableStateFlow(CoupleStatisticsState())
    val statisticsState = _statisticsState.asStateFlow()

    private fun observeDataChanges() {
        viewModelScope.launch {
            combine(
                userRepository.getCurrentUser(),
                journalRepository.getAllEntries(),
                categoryProgressRepository.getAllProgress()
            ) { user, entries, progress ->
                CoupleStatisticsState(
                    daysTogetherCount = calculateDaysTogether(user),
                    questionsProgressPercentage = calculateQuestionsProgress(progress),
                    citiesVisitedCount = calculateCitiesVisited(entries),
                    countriesVisitedCount = calculateCountriesVisited(entries)
                )
            }.collect { newState ->
                _statisticsState.value = newState
            }
        }
    }
}
```

### 3. **Optimisation Mémoire avec Paging**

Pour de gros volumes d'entrées journal :

```kotlin
class JournalRepositoryImpl {
    override fun getEntriesPaged(): Flow<PagingData<JournalEntry>> {
        return Pager(
            config = PagingConfig(pageSize = 50),
            pagingSourceFactory = { JournalPagingSource(firestore, authRepository) }
        ).flow
    }
}

// Mais pour les statistiques, charger seulement les données nécessaires :
override fun getLocationDataForStatistics(): Flow<List<JournalLocationData>> {
    return authRepository.getCurrentUserId().flatMapLatest { userId ->
        firestore.collection("journalEntries")
            .whereArrayContains("partnerIds", userId ?: "")
            .select("locationCity", "locationCountry") // Sélection spécifique
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    JournalLocationData(
                        city = doc.getString("locationCity"),
                        country = doc.getString("locationCountry")
                    )
                }
            }
    }
}
```

### 4. **WorkManager pour Géocodage en Arrière-Plan**

```kotlin
class LocationRepairWorker(
    context: Context,
    params: WorkerParameters,
    private val locationRepairService: LocationRepairService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            locationRepairService.repairMissingLocationData()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

// Planifier le worker périodiquement
fun scheduleLocationRepair(context: Context) {
    val request = PeriodicWorkRequestBuilder<LocationRepairWorker>(
        repeatInterval = 1, TimeUnit.DAYS
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "location_repair",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
```

---

## 📋 Résumé des Différences iOS vs Android GO

| Aspect               | iOS (Swift)                           | Android GO (Kotlin)                  |
| -------------------- | ------------------------------------- | ------------------------------------ |
| **Architecture**     | `@StateObject` + `@EnvironmentObject` | `ViewModel` + `StateFlow`            |
| **UI Framework**     | SwiftUI (`LazyVGrid`)                 | Jetpack Compose (`LazyVerticalGrid`) |
| **Cache Level 1**    | Dictionary in-memory                  | `LruCache`                           |
| **Cache Level 2**    | Realm database                        | Room database                        |
| **Cache Level 3**    | String Catalogs (.xcstrings)          | String Resources                     |
| **Persistence**      | UserDefaults                          | DataStore Preferences                |
| **Database**         | Firestore listeners                   | Firestore + Flow                     |
| **Geocoding**        | `CLGeocoder`                          | `Geocoder`                           |
| **Encryption**       | Custom service                        | AES/GCM with Android Keystore        |
| **Background Tasks** | `Task` async/await                    | `WorkManager`                        |
| **Reactive Updates** | `@Published` + `onReceive`            | `StateFlow` + `collectAsState`       |

---

## ✅ Checklist d'Implémentation Android

### Phase 1: Structure de Base

- [ ] Créer `CoupleStatisticsViewModel` avec `StateFlow`
- [ ] Implémenter `StatisticCard` Composable
- [ ] Créer `CoupleStatisticsSection` avec grille 2x2
- [ ] Définir les couleurs et ressources identiques à iOS

### Phase 2: Repositories et Données

- [ ] `UserRepository` avec observer du user courant
- [ ] `JournalRepository` avec listener Firestore temps réel
- [ ] `CategoryProgressRepository` avec DataStore
- [ ] `QuestionRepository` avec cache multi-niveaux

### Phase 3: Cache et Performance

- [ ] `QuestionCacheManager` avec LruCache in-memory
- [ ] Cache Room pour questions hors-ligne
- [ ] Chargement depuis String Resources
- [ ] Optimisation requêtes Firestore (select specific fields)

### Phase 4: Géolocalisation

- [ ] `LocationEncryptionService` avec Android Keystore
- [ ] `LocationRepairService` avec géocodage rétroactif
- [ ] `LocationRepairWorker` en arrière-plan
- [ ] Structure `JournalLocation` avec city/country

### Phase 5: Intégration et Tests

- [ ] Intégrer dans l'écran principal
- [ ] Tests unitaires des calculs
- [ ] Tests d'intégration avec repositories
- [ ] Validation des performances

---

## 🔧 Configuration Firebase Android

```kotlin
// build.gradle (Module: app)
dependencies {
    // Firestore
    implementation 'com.google.firebase:firebase-firestore-ktx:24.9.1'

    // Location
    implementation 'com.google.android.gms:play-services-location:21.0.1'

    // Cache
    implementation 'androidx.room:room-runtime:2.5.0'
    implementation 'androidx.room:room-ktx:2.5.0'

    // Background work
    implementation 'androidx.work:work-runtime-ktx:2.8.1'

    // Preferences
    implementation 'androidx.datastore:datastore-preferences:1.0.0'
}
```

Cette implémentation Android GO sera entièrement équivalente à la version iOS en termes de fonctionnalités, performance et expérience utilisateur.
