# 📱 RAPPORT TECHNIQUE COMPLET - Widgets iOS & Android

## Vue d'Ensemble

Love2Love propose **3 types de widgets** différents pour personnaliser l'écran d'accueil et l'écran de verrouillage :

### 📊 **Types de Widgets**

1. **Widget Principal (Love2LoveWidget)** - Jours ensemble ✅ **GRATUIT**

   - **Small (systemSmall)** : Photos + Nombre de jours
   - **Medium (systemMedium)** : Photos + Jours + Distance
   - **Circular (accessoryCircular)** : Cœurs + Jours (Écran verrouillé)

2. **Widget Distance (Love2LoveDistanceWidget)** ✅ **GRATUIT**

   - **Small** : Photos + Distance en km/mi

3. **Widget Carte (Love2LoveMapWidget)** ✅ **GRATUIT**
   - **Rectangular (accessoryRectangular)** : Carte avec initiales + Distance (Écran verrouillé)

---

## 🏗️ Architecture iOS Complète

### 📱 Structure Principale

```
Love2LoveWidget/
├── Love2LoveWidget.swift          # Widgets et logique principale
├── Love2LoveWidgetBundle.swift    # Bundle des 3 widgets
└── Assets.xcassets/               # Resources images

Services/
├── WidgetService.swift            # Service de données principal
└── LocationService.swift         # Géolocalisation

Views/
├── Widgets/WidgetsView.swift      # Page de configuration des widgets
├── Tutorial/                     # Tutoriels d'installation
└── Components/WidgetPreviewSection.swift
```

### 🔄 Cycle de Vie des Données

#### 1. **Collecte des Données (WidgetService)**

```swift
class WidgetService: ObservableObject {
    @Published var relationshipStats: RelationshipStats?
    @Published var distanceInfo: DistanceInfo?
    @Published var isLocationUpdateInProgress = false
    @Published var lastUpdateTime = Date()

    private let firebaseService = FirebaseService.shared
    private let sharedDefaults = UserDefaults(suiteName: "group.com.lyes.love2love")
}
```

**Sources de données :**

- `FirebaseService.shared.$currentUser` → Informations utilisateur
- `LocationService.shared.$currentLocation` → Position GPS
- Cloud Functions Firebase → Informations partenaire

#### 2. **Pipeline de Traitement**

```swift
private func setupObservers() {
    // 1. Observer utilisateur actuel
    firebaseService.$currentUser
        .sink { [weak self] user in
            self?.currentUser = user
            self?.updateRelationshipStats()     // Calculer jours ensemble
            self?.fetchPartnerInfo()           // Récupérer info partenaire
        }

    // 2. Observer localisation
    LocationService.shared.$currentLocation
        .sink { [weak self] location in
            self?.updateDistanceInfo()         // Calculer distance
        }
}
```

**Calcul des Statistiques Relationnelles :**

```swift
private func updateRelationshipStats() {
    guard let startDate = user.relationshipStartDate else { return }

    let calendar = Calendar.current
    let now = Date()

    // Calculer les jours ensemble
    let dayComponents = calendar.dateComponents([.day], from: startDate, to: now)
    let daysTogether = max(dayComponents.day ?? 0, 0)

    // Calculer temps jusqu'au prochain anniversaire
    var nextAnniversary = calendar.dateComponents([.month, .day], from: startDate)
    nextAnniversary.year = calendar.component(.year, from: now)

    relationshipStats = RelationshipStats(
        startDate: startDate,
        daysTotal: daysTogether,
        // ... autres calculs
    )
}
```

#### 3. **Récupération Sécurisée des Données Partenaire**

**Cloud Function `getPartnerInfo` :**

```javascript
exports.getPartnerInfo = functions.https.onCall(async (data, context) => {
  // Vérifier l'authentification
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Utilisateur non authentifié"
    );
  }

  const currentUserId = context.auth.uid;
  const { partnerId } = data;

  // Vérifier que l'utilisateur est bien connecté à ce partenaire
  const currentUserDoc = await admin
    .firestore()
    .collection("users")
    .doc(currentUserId)
    .get();
  const currentUserData = currentUserDoc.data();

  if (currentUserData.partnerId !== partnerId) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Accès non autorisé"
    );
  }

  // Récupérer les infos partenaire
  const partnerDoc = await admin
    .firestore()
    .collection("users")
    .doc(partnerId)
    .get();
  const partnerData = partnerDoc.data();

  return {
    success: true,
    partnerInfo: {
      name: partnerData.name || "Partenaire",
      isSubscribed: partnerData.isSubscribed || false,
      profileImageURL: partnerData.profileImageURL || null,
    },
  };
});
```

**Cloud Function `getPartnerLocation` :**

```javascript
exports.getPartnerLocation = functions.https.onCall(async (data, context) => {
  // Même système de sécurité que getPartnerInfo

  const partnerData = partnerDoc.data();
  const currentLocation = partnerData.currentLocation;

  if (!currentLocation) {
    return {
      success: false,
      reason: "NO_LOCATION",
      message: "Aucune localisation disponible pour ce partenaire",
    };
  }

  return {
    success: true,
    location: {
      latitude: currentLocation.latitude,
      longitude: currentLocation.longitude,
      address: currentLocation.address || null,
      city: currentLocation.city || null,
      country: currentLocation.country || null,
      lastUpdated: currentLocation.lastUpdated,
    },
  };
});
```

#### 4. **Calcul de Distance et Messages**

```swift
private func updateDistanceInfo() {
    guard let userLocation = currentUser?.currentLocation,
          let partnerLocation = partnerUser?.currentLocation else {
        distanceInfo = nil
        return
    }

    // Calculer distance entre les deux positions
    let userCLLocation = CLLocation(
        latitude: userLocation.latitude,
        longitude: userLocation.longitude
    )
    let partnerCLLocation = CLLocation(
        latitude: partnerLocation.latitude,
        longitude: partnerLocation.longitude
    )

    let distanceInMeters = userCLLocation.distance(from: partnerCLLocation)

    // Formatter la distance
    let formattedDistance: String
    if distanceInMeters < 100 {
        formattedDistance = "widget_together_text".localized.capitalized
    } else if distanceInMeters < 1000 {
        formattedDistance = String(format: "%.0f m", distanceInMeters)
    } else {
        let distanceInKm = distanceInMeters / 1000
        if distanceInKm < 10 {
            formattedDistance = String(format: "%.1f km", distanceInKm)
        } else {
            formattedDistance = String(format: "%.0f km", distanceInKm)
        }
    }

    // Messages aléatoires pour les widgets
    let messages = [
        "💕 Je pense à toi",
        "❤️ Tu me manques",
        "🥰 J'ai hâte de te voir",
        // ... autres messages
    ]

    distanceInfo = DistanceInfo(
        distanceInMeters: distanceInMeters,
        formattedDistance: formattedDistance,
        randomMessage: messages.randomElement() ?? "💕 Je pense à toi",
        messages: messages
    )
}
```

### 📦 Système de Cache App Group

#### UserDefaults Partagés

```swift
private let sharedDefaults = UserDefaults(suiteName: "group.com.lyes.love2love")

private func saveWidgetData() {
    // Statistiques de relation
    sharedDefaults.set(stats.daysTotal, forKey: "widget_days_total")
    sharedDefaults.set(stats.formattedDuration, forKey: "widget_duration")
    sharedDefaults.set(stats.daysToAnniversary, forKey: "widget_days_to_anniversary")

    // Informations de distance
    sharedDefaults.set(distance.formattedDistance, forKey: "widget_distance")
    sharedDefaults.set(distance.randomMessage, forKey: "widget_message")

    // Noms d'utilisateurs
    sharedDefaults.set(currentUser.name, forKey: "widget_user_name")
    sharedDefaults.set(partnerUser.name, forKey: "widget_partner_name")

    // Plus besoin de sauvegarder le statut d'abonnement - tous les widgets sont gratuits
    sharedDefaults.set(true, forKey: "widget_has_subscription") // Toujours true pour compatibilité

    // Coordonnées GPS
    sharedDefaults.set(userLocation.latitude, forKey: "widget_user_latitude")
    sharedDefaults.set(userLocation.longitude, forKey: "widget_user_longitude")
    sharedDefaults.set(partnerLocation.latitude, forKey: "widget_partner_latitude")
    sharedDefaults.set(partnerLocation.longitude, forKey: "widget_partner_longitude")

    // Horodatage
    sharedDefaults.set(Date().timeIntervalSince1970, forKey: "widget_last_update")
}
```

#### Cache d'Images App Group

```swift
private func downloadAndCacheImage(from url: String, key: String, isUser: Bool) {
    guard let containerURL = FileManager.default.containerURL(
        forSecurityApplicationGroupIdentifier: "group.com.lyes.love2love"
    ) else { return }

    let imageCacheURL = containerURL.appendingPathComponent("ImageCache")
    let fileName = isUser ? "user_profile_image.jpg" : "partner_profile_image.jpg"
    let localImageURL = imageCacheURL.appendingPathComponent(fileName)

    // Télécharger et sauvegarder l'image
    URLSession.shared.dataTask(with: URL(string: url)!) { data, response, error in
        guard let imageData = data, error == nil else { return }

        try? FileManager.default.createDirectory(at: imageCacheURL, withIntermediateDirectories: true)
        try? imageData.write(to: localImageURL)

        // Sauvegarder le nom du fichier dans UserDefaults
        self.sharedDefaults?.set(fileName, forKey: key)

        // Actualiser les widgets
        WidgetCenter.shared.reloadAllTimelines()
    }.resume()
}
```

### 🎨 Widgets UI Components

#### **Timeline Provider**

```swift
struct Provider: TimelineProvider {
    func getTimeline(in context: Context, completion: @escaping (Timeline<SimpleEntry>) -> ()) {
        let widgetData = WidgetData.loadFromUserDefaults() ?? .placeholder
        let currentDate = Date()

        // Créer 60 entrées (une par minute pour la prochaine heure)
        var entries: [SimpleEntry] = []
        for i in 0..<60 {
            let entryDate = Calendar.current.date(byAdding: .minute, value: i, to: currentDate)!
            let entry = SimpleEntry(date: entryDate, widgetData: widgetData)
            entries.append(entry)
        }

        // Programmer la prochaine mise à jour majeure dans 1 heure
        let nextUpdate = Calendar.current.date(byAdding: .hour, value: 1, to: currentDate)!
        let timeline = Timeline(entries: entries, policy: .after(nextUpdate))

        completion(timeline)
    }
}
```

#### **Widget Principal - Small**

```swift
struct SmallWidgetView: View {
    let data: WidgetData

    var body: some View {
        let timeComponents = data.getTimeComponents()

        VStack(spacing: 16) {
            // Photos de profil côte à côte
            HStack(spacing: 12) {
                ProfileCircleForWidget(imageURL: data.userImageURL, userName: data.userName, size: 50)
                ProfileCircleForWidget(imageURL: data.partnerImageURL, userName: data.partnerName, size: 50)
            }

            // Texte sur deux lignes
            VStack(spacing: 2) {
                Text("\(timeComponents.days) " + "widget_days_label".localized)
                    .font(.system(size: 20, weight: .medium))
                    .foregroundColor(.white)

                Text("widget_together_text".localized)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
            }
        }
        .background(/* Fond noir avec blur */)
    }
}
```

#### **Widget Distance - Small (Premium)**

```swift
struct SmallDistanceWidgetView: View {
    let data: WidgetData

    private func formattedDistanceText() -> String {
        let raw = (data.distance ?? "? km").convertedForLocale()
        if raw.lowercased() == "ensemble" || raw.lowercased() == "together" {
            return raw.capitalized
        }
        return raw
    }

    var body: some View {
        VStack(spacing: 16) {
            // Photos de profil identiques au widget principal
            HStack(spacing: 12) {
                ProfileCircleForWidget(imageURL: data.userImageURL, userName: data.userName, size: 50)
                ProfileCircleForWidget(imageURL: data.partnerImageURL, userName: data.partnerName, size: 50)
            }

            // Distance au lieu du nombre de jours
            Text(formattedDistanceText())
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.white)
        }
    }
}
```

#### **Widget Écran de Verrouillage - Circular**

```swift
struct AccessoryCircularWidgetView: View {
    let data: WidgetData

    private var hasPartner: Bool {
        return data.partnerName != nil && !data.partnerName!.isEmpty
    }

    var body: some View {
        let timeComponents = data.getTimeComponents()

        ZStack {
            Circle().fill(Color.black.opacity(0.6)).blur(radius: 5)

            VStack(spacing: 4) {
                // Cœurs : deux si partenaire connecté, un seul sinon
                if hasPartner {
                    ZStack {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .offset(x: -4, y: -2)

                        Image(systemName: "heart.fill")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .offset(x: 4, y: 2)
                    }
                } else {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                }

                Text("\(timeComponents.days)")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)

                Text("widget_days_label".localized)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.white)
            }
        }
    }
}
```

#### **Contrôle Premium**

```swift
struct Love2LoveWidgetEntryView: View {
    var entry: Provider.Entry
    @Environment(\.widgetFamily) var family

    var body: some View {
        switch family {
        case .systemSmall:
            SmallWidgetView(data: entry.widgetData)
        case .systemMedium:
            // Widget moyen disponible pour tous (gratuit)
            MediumWidgetView(data: entry.widgetData)
        case .accessoryCircular:
            AccessoryCircularWidgetView(data: entry.widgetData)
        }
    }
}
```

#### **Gestion Gratuite des Widgets**

```swift
// Tous les widgets sont maintenant gratuits - plus de logique premium nécessaire
private var canAccessAllWidgets: Bool {
    return true // Tous les widgets sont gratuits
}

enum WidgetType: String, CaseIterable {
    case countdown = "countdown"
    case daysTotal = "daysTotal"

    var requiresPremium: Bool {
        return false // Tous gratuits maintenant
    }
}
```

---

## 🔤 Clés de Traduction Complètes

### **Core Widget Keys**

```yaml
# Widgets principaux
widget_days_label: # "days", "jours", "giorni"...
widget_together_text: # "together", "ensemble", "insieme"...
widget_main_display_name: # "Love2Love"
widget_main_description: # "Suivez votre relation"

# Widgets distance
widget_distance_display_name: # "Love2Love - Distance"
widget_distance_description: # "Distance en temps réel"
widget_our_distance: # "Notre distance"

# Widgets carte
widget_map_display_name: # "Love2Love - Carte"
widget_map_description: # "Localisation sur carte"

# Messages d'erreur géolocalisation
widget_enable_your_location: # "Activez votre localisation"
widget_partner_enable_location: # "Partenaire doit activer sa localisation"
widget_enable_your_locations: # "Activez vos localisations"
```

### **Configuration & Interface**

```yaml
# Page de configuration widgets
add_widgets: # "Ajouter vos widgets"
widgets: # "Widgets"
widgets_available: # "Widgets disponibles"
home_screen: # "Écran d'accueil"
lock_screen_widget: # "Widget écran de verrouillage"
home_screen_widget: # "Widget écran d'accueil"
```

### **Tutoriels Installation**

```yaml
# Écran de verrouillage
swipe_down: # "Balayez vers le bas"
swipe_description: # "Depuis l'écran de verrouillage"
tap_customize: # "Touchez Personnaliser"
customize_description: # "En bas de l'écran"
select_lock_screen: # "Sélectionnez Écran de verrouillage"
lock_screen_description: # "Dans les options"
search_love2love: # "Cherchez Love2Love"
search_description: # "Dans la liste des widgets"

# Écran d'accueil
hold_home_screen: # "Appuyez longuement sur l'écran d'accueil"
hold_description: # "Jusqu'à ce que les icônes bougent"
tap_plus_button: # "Touchez le bouton +"
plus_description: # "En haut à gauche de l'écran"
search_love2love_home: # "Cherchez Love2Love"
search_home_description: # "Dans la liste des widgets"
```

---

## 🔧 Configuration & Intégration

### **App Group Configuration**

```xml
<!-- CoupleApp.entitlements -->
<key>com.apple.security.application-groups</key>
<array>
    <string>group.com.lyes.love2love</string>
</array>
```

```xml
<!-- Love2LoveWidget.entitlements -->
<key>com.apple.security.application-groups</key>
<array>
    <string>group.com.lyes.love2love</string>
</array>
```

### **Bundle Configuration**

```swift
@main
struct Love2LoveWidgetBundle: WidgetBundle {
    var body: some Widget {
        Love2LoveWidget()        // Widget principal
        Love2LoveDistanceWidget() // Widget distance (Premium)
        Love2LoveMapWidget()     // Widget carte (Premium)
    }
}
```

### **Familles de Widgets Supportées**

```swift
// Widget principal (GRATUIT)
.supportedFamilies([
    .systemSmall,      // Écran d'accueil petit
    .systemMedium,     // Écran d'accueil moyen
    .accessoryCircular // Écran de verrouillage circulaire
])

// Widget distance (GRATUIT)
.supportedFamilies([
    .systemSmall       // Écran d'accueil petit
])

// Widget carte (GRATUIT)
.supportedFamilies([
    .accessoryRectangular // Écran de verrouillage rectangulaire
])
```

---

# 🤖 IMPLÉMENTATION ANDROID ÉQUIVALENTE

## Architecture Recommandée

### 🏗️ Structure des Composants

```kotlin
// Modules
app/
├── widgets/                    # Package widgets
│   ├── providers/             # Providers de données pour widgets
│   ├── receivers/             # BroadcastReceivers pour widgets
│   ├── services/              # Services de mise à jour
│   └── ui/                    # Vues des widgets
├── repositories/              # Repositories pour données
└── services/                  # Services partagés

widgets/
├── providers/
│   ├── LoveWidgetProvider.kt         # Widget principal
│   ├── DistanceWidgetProvider.kt     # Widget distance
│   └── MapWidgetProvider.kt          # Widget carte
├── ui/
│   ├── SmallLoveWidgetView.kt
│   ├── MediumLoveWidgetView.kt
│   └── DistanceWidgetView.kt
└── services/
    ├── WidgetUpdateService.kt        # Service de mise à jour
    └── LocationWidgetService.kt      # Service de géolocalisation
```

### 📊 Data Layer Android

#### **Repository Pattern**

```kotlin
interface WidgetRepository {
    suspend fun getRelationshipStats(): Flow<RelationshipStats>
    suspend fun getDistanceInfo(): Flow<DistanceInfo>
    suspend fun getPartnerInfo(): Flow<PartnerInfo>
}

@Singleton
class WidgetRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val locationService: LocationService,
    private val cloudFunctions: FirebaseFunctions
) : WidgetRepository {

    override suspend fun getRelationshipStats(): Flow<RelationshipStats> =
        authRepository.getCurrentUser().flatMapLatest { user ->
            if (user?.relationshipStartDate != null) {
                flow {
                    val stats = calculateRelationshipStats(user.relationshipStartDate)
                    emit(stats)
                }
            } else {
                flowOf(RelationshipStats.empty())
            }
        }

    override suspend fun getDistanceInfo(): Flow<DistanceInfo> =
        combine(
            authRepository.getCurrentUser(),
            getPartnerLocation(),
            locationService.getCurrentLocation()
        ) { user, partnerLocation, userLocation ->
            if (user != null && partnerLocation != null && userLocation != null) {
                calculateDistance(userLocation, partnerLocation)
            } else {
                DistanceInfo.unavailable()
            }
        }

    private suspend fun getPartnerLocation(): Flow<Location?> {
        val user = authRepository.getCurrentUser().first()
        val partnerId = user?.partnerId ?: return flowOf(null)

        return callCloudFunction<LocationResponse>("getPartnerLocation", mapOf("partnerId" to partnerId))
            .map { response ->
                if (response.success) {
                    Location(response.location.latitude, response.location.longitude)
                } else null
            }
    }

    private fun calculateRelationshipStats(startDate: Long): RelationshipStats {
        val start = LocalDate.ofEpochDay(startDate / (24 * 60 * 60 * 1000))
        val today = LocalDate.now()
        val daysTogether = ChronoUnit.DAYS.between(start, today).toInt()

        return RelationshipStats(
            daysTotal = daysTogether,
            formattedDuration = formatDuration(daysTogether),
            daysToAnniversary = calculateDaysToAnniversary(start)
        )
    }

    private fun calculateDistance(userLoc: Location, partnerLoc: Location): DistanceInfo {
        val results = FloatArray(1)
        Location.distanceBetween(
            userLoc.latitude, userLoc.longitude,
            partnerLoc.latitude, partnerLoc.longitude,
            results
        )

        val distanceInMeters = results[0]
        val formattedDistance = formatDistance(distanceInMeters)

        return DistanceInfo(
            distanceInMeters = distanceInMeters,
            formattedDistance = formattedDistance,
            randomMessage = getRandomMessage()
        )
    }
}
```

#### **Shared Preferences pour Cache**

```kotlin
@Singleton
class WidgetPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)

    fun saveWidgetData(data: WidgetData) {
        prefs.edit {
            putInt("days_total", data.daysTotal)
            putString("duration", data.duration)
            putInt("days_to_anniversary", data.daysToAnniversary)
            putString("distance", data.distance)
            putString("message", data.message)
            putString("user_name", data.userName)
            putString("partner_name", data.partnerName)
            putBoolean("has_subscription", data.hasSubscription)
            putFloat("user_latitude", data.userLatitude ?: 0f)
            putFloat("user_longitude", data.userLongitude ?: 0f)
            putFloat("partner_latitude", data.partnerLatitude ?: 0f)
            putFloat("partner_longitude", data.partnerLongitude ?: 0f)
            putLong("last_update", System.currentTimeMillis())
        }
    }

    fun loadWidgetData(): WidgetData? {
        val daysTotal = prefs.getInt("days_total", 0)
        if (daysTotal == 0) return null

        return WidgetData(
            daysTotal = daysTotal,
            duration = prefs.getString("duration", "") ?: "",
            daysToAnniversary = prefs.getInt("days_to_anniversary", 0),
            distance = prefs.getString("distance", null),
            message = prefs.getString("message", null),
            userName = prefs.getString("user_name", null),
            partnerName = prefs.getString("partner_name", null),
            hasSubscription = prefs.getBoolean("has_subscription", false),
            userLatitude = prefs.getFloat("user_latitude", 0f).takeIf { it != 0f },
            userLongitude = prefs.getFloat("user_longitude", 0f).takeIf { it != 0f },
            partnerLatitude = prefs.getFloat("partner_latitude", 0f).takeIf { it != 0f },
            partnerLongitude = prefs.getFloat("partner_longitude", 0f).takeIf { it != 0f },
            lastUpdate = Date(prefs.getLong("last_update", 0))
        )
    }
}
```

### 🎨 Widget Providers Android

#### **Widget Principal**

```kotlin
class LoveWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val widgetData = loadWidgetData(context)
        val views = createRemoteViews(context, widgetData)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun createRemoteViews(context: Context, data: WidgetData?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_love_small)

        if (data != null) {
            // Afficher les données réelles
            views.setTextViewText(R.id.days_text, "${data.daysTotal} ${context.getString(R.string.widget_days_label)}")
            views.setTextViewText(R.id.together_text, context.getString(R.string.widget_together_text))

            // Charger les images de profil
            loadProfileImages(context, views, data)

            // Gérer le clic pour ouvrir l'app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        } else {
            // Afficher les données placeholder
            views.setTextViewText(R.id.days_text, "365 ${context.getString(R.string.widget_days_label)}")
            views.setTextViewText(R.id.together_text, context.getString(R.string.widget_together_text))
            setPlaceholderImages(views)
        }

        return views
    }

    private fun loadProfileImages(context: Context, views: RemoteViews, data: WidgetData) {
        // Charger l'image utilisateur
        if (data.userImageURL != null) {
            Glide.with(context)
                .asBitmap()
                .load(data.userImageURL)
                .circleCrop()
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        views.setImageViewBitmap(R.id.user_profile_image, resource)
                        updateWidget(context)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        } else {
            // Afficher l'initiale
            val initial = data.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
            val bitmap = createInitialBitmap(context, initial)
            views.setImageViewBitmap(R.id.user_profile_image, bitmap)
        }

        // Même logique pour le partenaire
        if (data.partnerImageURL != null) {
            // Charger image partenaire
        } else {
            val partnerInitial = data.partnerName?.firstOrNull()?.uppercaseChar()?.toString() ?: "P"
            val bitmap = createInitialBitmap(context, partnerInitial)
            views.setImageViewBitmap(R.id.partner_profile_image, bitmap)
        }
    }

    private fun createInitialBitmap(context: Context, initial: String): Bitmap {
        val size = context.resources.getDimensionPixelSize(R.dimen.profile_image_size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fond circulaire transparent
        val paint = Paint().apply {
            isAntiAlias = true
            color = ContextCompat.getColor(context, R.color.white_transparent)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Texte de l'initiale
        paint.apply {
            color = Color.WHITE
            textSize = size * 0.4f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val bounds = Rect()
        paint.getTextBounds(initial, 0, initial.length, bounds)
        val y = size / 2f + bounds.height() / 2f
        canvas.drawText(initial, size / 2f, y, paint)

        return bitmap
    }

    private fun updateWidget(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, LoveWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        onUpdate(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, LoveWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, LoveWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(intent)
        }
    }
}
```

#### **Widget Distance (Gratuit)**

```kotlin
class DistanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Widget distance gratuit pour tous les utilisateurs
        for (appWidgetId in appWidgetIds) {
            val views = createDistanceWidget(context)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createDistanceWidget(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_distance)
        val widgetData = loadWidgetData(context)

        if (widgetData != null) {
            // Afficher la distance formatée
            val distanceText = formatDistanceForLocale(context, widgetData.distance ?: "? km")
            views.setTextViewText(R.id.distance_text, distanceText)

            // Charger les images de profil (même logique que widget principal)
            loadProfileImages(context, views, widgetData)

        } else {
            views.setTextViewText(R.id.distance_text, "3.128 km")
            setPlaceholderImages(views)
        }

        // Gérer le clic pour ouvrir l'app
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        return views
    }

    private fun formatDistanceForLocale(context: Context, distance: String): String {
        val locale = context.resources.configuration.locales[0]

        // Conversion km vers miles pour les locales anglaises
        if (locale.language == "en") {
            return convertKmToMiles(distance)
        }

        return distance
    }

    private fun convertKmToMiles(distance: String): String {
        val kmPattern = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*km")
        val mPattern = Regex("([0-9]+)\\s*m")

        kmPattern.find(distance)?.let { match ->
            val kmValue = match.groupValues[1].toDoubleOrNull()
            if (kmValue != null) {
                val milesValue = kmValue * 0.621371
                val formatted = if (milesValue < 10) {
                    "%.1f mi".format(milesValue)
                } else {
                    "${milesValue.toInt()} mi"
                }
                return distance.replace(match.value, formatted)
            }
        }

        mPattern.find(distance)?.let { match ->
            val meters = match.groupValues[1].toDoubleOrNull()
            if (meters != null) {
                val milesValue = meters / 1609.34
                val formatted = if (milesValue < 10) {
                    "%.1f mi".format(milesValue)
                } else {
                    "${milesValue.toInt()} mi"
                }
                return distance.replace(match.value, formatted)
            }
        }

        return distance
    }
}
```

### 🔄 Service de Mise à Jour

#### **Background Service**

```kotlin
@AndroidEntryPoint
class WidgetUpdateService : Service() {

    @Inject lateinit var widgetRepository: WidgetRepository
    @Inject lateinit var widgetPreferences: WidgetPreferences

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_WIDGETS -> updateWidgets()
            ACTION_UPDATE_LOCATION -> updateLocation()
        }
        return START_STICKY
    }

    private fun updateWidgets() {
        serviceScope.launch {
            try {
                // Collecter toutes les données nécessaires
                val relationshipStats = widgetRepository.getRelationshipStats().first()
                val distanceInfo = widgetRepository.getDistanceInfo().first()
                val partnerInfo = widgetRepository.getPartnerInfo().first()

                val widgetData = WidgetData(
                    daysTotal = relationshipStats.daysTotal,
                    duration = relationshipStats.formattedDuration,
                    daysToAnniversary = relationshipStats.daysToAnniversary,
                    distance = distanceInfo.formattedDistance,
                    message = distanceInfo.randomMessage,
                    userName = partnerInfo.userName,
                    partnerName = partnerInfo.partnerName,
                    userImageURL = partnerInfo.userImageURL,
                    partnerImageURL = partnerInfo.partnerImageURL,
                    hasSubscription = partnerInfo.hasSubscription,
                    lastUpdate = Date()
                )

                // Sauvegarder les données
                widgetPreferences.saveWidgetData(widgetData)

                // Télécharger et cacher les images de profil
                downloadAndCacheImages(widgetData)

                // Mettre à jour tous les widgets
                updateAllWidgetProviders()

                Log.d("WidgetUpdateService", "Widgets mis à jour avec succès")

            } catch (e: Exception) {
                Log.e("WidgetUpdateService", "Erreur mise à jour widgets", e)
            }
        }
    }

    private suspend fun downloadAndCacheImages(widgetData: WidgetData) {
        // Télécharger l'image utilisateur
        widgetData.userImageURL?.let { url ->
            try {
                val bitmap = Glide.with(this@WidgetUpdateService)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get()

                // Sauvegarder dans le cache interne
                val file = File(cacheDir, "user_profile_image.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                Log.d("WidgetUpdateService", "Image utilisateur téléchargée et cachée")
            } catch (e: Exception) {
                Log.e("WidgetUpdateService", "Erreur téléchargement image utilisateur", e)
            }
        }

        // Même logique pour l'image partenaire
        widgetData.partnerImageURL?.let { url ->
            // ... téléchargement image partenaire
        }
    }

    private fun updateAllWidgetProviders() {
        // Mettre à jour tous les widgets (tous gratuits)
        LoveWidgetProvider.updateAllWidgets(this)
        DistanceWidgetProvider.updateAllWidgets(this)
        MapWidgetProvider.updateAllWidgets(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_UPDATE_WIDGETS = "com.lyes.love2love.UPDATE_WIDGETS"
        const val ACTION_UPDATE_LOCATION = "com.lyes.love2love.UPDATE_LOCATION"

        fun startUpdateService(context: Context) {
            val intent = Intent(context, WidgetUpdateService::class.java).apply {
                action = ACTION_UPDATE_WIDGETS
            }
            context.startService(intent)
        }
    }
}
```

#### **WorkManager pour Mises à Jour Périodiques**

```kotlin
@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val widgetRepository: WidgetRepository,
    private val widgetPreferences: WidgetPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("WidgetUpdateWorker", "Début mise à jour périodique des widgets")

            // Utiliser le service pour la mise à jour
            WidgetUpdateService.startUpdateService(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Log.e("WidgetUpdateWorker", "Erreur mise à jour périodique", e)
            Result.failure()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context, params: WorkerParameters): WidgetUpdateWorker
    }
}

// Planifier les mises à jour périodiques
object WidgetUpdateScheduler {

    fun schedulePeriodicUpdates(context: Context) {
        val updateRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            repeatInterval = 30, TimeUnit.MINUTES
        )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "widget_periodic_update",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    fun scheduleImmediateUpdate(context: Context) {
        val updateRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(updateRequest)
    }
}
```

### 📱 Configuration Interface Android

#### **Activity de Configuration Widgets**

```kotlin
@AndroidEntryPoint
class WidgetsConfigurationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetsConfigurationBinding
    private val viewModel: WidgetsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetsConfigurationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.apply {
            // Configuration widget principal
            mainWidgetCard.setOnClickListener {
                showWidgetTutorial(WidgetType.MAIN)
            }

            // Configuration widget distance (Gratuit)
            distanceWidgetCard.setOnClickListener {
                showWidgetTutorial(WidgetType.DISTANCE)
            }

            // Actualiser les données
            refreshButton.setOnClickListener {
                viewModel.refreshWidgetData()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.widgetData.collect { data ->
                updatePreviewWidgets(data)
            }
        }

        // Plus de vérification d'abonnement nécessaire - tous les widgets sont gratuits
    }

    private fun updatePreviewWidgets(data: WidgetData?) {
        if (data != null) {
            binding.apply {
                // Mettre à jour les aperçus avec les vraies données
                previewDaysText.text = "${data.daysTotal} ${getString(R.string.widget_days_label)}"
                previewTogetherText.text = getString(R.string.widget_together_text)

                if (data.distance != null) {
                    previewDistanceText.text = data.distance
                }

                // Charger les images de profil
                loadPreviewImages(data)
            }
        }
    }

    private fun showWidgetTutorial(widgetType: WidgetType) {
        val intent = Intent(this, WidgetTutorialActivity::class.java).apply {
            putExtra("widget_type", widgetType)
        }
        startActivity(intent)
    }

    // Plus besoin de dialog d'abonnement - tous les widgets sont gratuits

    enum class WidgetType {
        MAIN, DISTANCE, MAP
    }
}
```

#### **ViewModel pour Widgets**

```kotlin
@HiltViewModel
class WidgetsViewModel @Inject constructor(
    private val widgetRepository: WidgetRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _widgetData = MutableStateFlow<WidgetData?>(null)
    val widgetData: StateFlow<WidgetData?> = _widgetData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadWidgetData()
    }

    private fun loadWidgetData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                combine(
                    widgetRepository.getRelationshipStats(),
                    widgetRepository.getDistanceInfo(),
                    widgetRepository.getPartnerInfo()
                ) { stats, distance, partner ->
                    WidgetData(
                        daysTotal = stats.daysTotal,
                        duration = stats.formattedDuration,
                        daysToAnniversary = stats.daysToAnniversary,
                        distance = distance.formattedDistance,
                        message = distance.randomMessage,
                        userName = partner.userName,
                        partnerName = partner.partnerName,
                        userImageURL = partner.userImageURL,
                        partnerImageURL = partner.partnerImageURL,
                        hasSubscription = partner.hasSubscription,
                        lastUpdate = Date()
                    )
                }.collect { data ->
                    _widgetData.value = data
                }
            } catch (e: Exception) {
                Log.e("WidgetsViewModel", "Erreur chargement données widget", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshWidgetData() {
        // Déclencher une mise à jour immédiate
        WidgetUpdateScheduler.scheduleImmediateUpdate(getApplication())
        loadWidgetData()
    }
}
```

### 🎨 Layouts des Widgets Android

#### **Widget Principal Small**

```xml
<!-- res/layout/widget_love_small.xml -->
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:background="@drawable/widget_background"
    android:padding="16dp">

    <!-- Photos de profil -->
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center"
        android:layout_marginBottom="16dp">

        <ImageView
            android:id="@+id/user_profile_image"
            android:layout_width="50dp"
            android:layout_height="50dp"
            android:layout_marginEnd="12dp"
            android:scaleType="centerCrop"
            android:background="@drawable/profile_circle_background" />

        <ImageView
            android:id="@+id/partner_profile_image"
            android:layout_width="50dp"
            android:layout_height="50dp"
            android:scaleType="centerCrop"
            android:background="@drawable/profile_circle_background" />
    </LinearLayout>

    <!-- Texte jours ensemble -->
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center">

        <TextView
            android:id="@+id/days_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="365 jours"
            android:textSize="20sp"
            android:textStyle="bold"
            android:textColor="@color/white"
            android:gravity="center" />

        <TextView
            android:id="@+id/together_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/widget_together_text"
            android:textSize="16sp"
            android:textColor="@color/white_90"
            android:layout_marginTop="2dp"
            android:gravity="center" />
    </LinearLayout>
</LinearLayout>
```

#### **Widget Distance Small (Premium)**

```xml
<!-- res/layout/widget_distance.xml -->
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:background="@drawable/widget_background"
    android:padding="16dp">

    <!-- Photos de profil (identiques au widget principal) -->
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center"
        android:layout_marginBottom="16dp">

        <ImageView
            android:id="@+id/user_profile_image"
            android:layout_width="50dp"
            android:layout_height="50dp"
            android:layout_marginEnd="12dp"
            android:scaleType="centerCrop"
            android:background="@drawable/profile_circle_background" />

        <ImageView
            android:id="@+id/partner_profile_image"
            android:layout_width="50dp"
            android:layout_height="50dp"
            android:scaleType="centerCrop"
            android:background="@drawable/profile_circle_background" />
    </LinearLayout>

    <!-- Distance -->
    <TextView
        android:id="@+id/distance_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="3.128 km"
        android:textSize="16sp"
        android:textStyle="bold"
        android:textColor="@color/white"
        android:gravity="center" />
</LinearLayout>
```

#### **Configuration Widgets Gratuits**

Tous les widgets étant gratuits, il n'y a plus besoin de layouts bloqués ou de vérifications d'abonnement. Les widgets affichent directement leur contenu pour tous les utilisateurs.

### 🎨 Styles et Ressources

#### **Backgrounds**

```xml
<!-- res/drawable/widget_background.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:type="radial"
        android:centerX="0.5"
        android:centerY="0.5"
        android:gradientRadius="100%p"
        android:startColor="#80000000"
        android:endColor="#CC000000" />
    <corners android:radius="16dp" />
</shape>

<!-- Plus besoin de background premium - tous les widgets sont gratuits -->

<!-- res/drawable/profile_circle_background.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#40FFFFFF" />
</shape>
```

#### **Couleurs**

```xml
<!-- res/values/colors.xml -->
<resources>
    <color name="white">#FFFFFF</color>
    <color name="white_90">#E6FFFFFF</color>
    <color name="white_80">#CCFFFFFF</color>
    <color name="white_transparent">#40FFFFFF</color>
</resources>
```

#### **Strings**

```xml
<!-- res/values/strings.xml -->
<string name="widget_days_label">days</string>
<string name="widget_together_text">together</string>
<string name="widget_our_distance">Our distance</string>

<!-- res/values-fr/strings.xml -->
<string name="widget_days_label">jours</string>
<string name="widget_together_text">ensemble</string>
<string name="widget_our_distance">Notre distance</string>
```

### 📋 Configuration Android

#### **Manifest.xml**

```xml
<!-- Widget Providers -->
<receiver android:name=".widgets.providers.LoveWidgetProvider"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
        android:resource="@xml/love_widget_info" />
</receiver>

<receiver android:name=".widgets.providers.DistanceWidgetProvider"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
        android:resource="@xml/distance_widget_info" />
</receiver>

<!-- Widget Update Service -->
<service android:name=".widgets.services.WidgetUpdateService"
    android:exported="false" />
```

#### **Widget Info Files**

```xml
<!-- res/xml/love_widget_info.xml -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="180dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:initialLayout="@layout/widget_love_small"
    android:previewImage="@drawable/widget_love_preview"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen"
    android:description="@string/widget_main_description" />

<!-- res/xml/distance_widget_info.xml -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="180dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:initialLayout="@layout/widget_distance"
    android:previewImage="@drawable/widget_distance_preview"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen"
    android:description="@string/widget_distance_description" />
```

---

## 🚀 Fonctionnalités Avancées

### 🔄 Synchronisation en Temps Réel

#### **iOS - Actualisation Automatique**

```swift
// Actualiser les widgets quand l'app revient au premier plan
NotificationCenter.default.addObserver(
    forName: UIApplication.willEnterForegroundNotification,
    object: nil,
    queue: .main
) { _ in
    widgetService?.refreshData()
}

// Actualiser après changement de localisation
LocationService.shared.$currentLocation
    .sink { location in
        if location != nil {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
```

#### **Android - Background Sync**

```kotlin
// Synchronisation lors du changement d'état de l'app
class AppLifecycleObserver @Inject constructor(
    private val context: Context
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        // App passée au premier plan - actualiser les widgets
        WidgetUpdateService.startUpdateService(context)
    }

    override fun onStop(owner: LifecycleOwner) {
        // App en arrière-plan - programmer la prochaine mise à jour
        WidgetUpdateScheduler.schedulePeriodicUpdates(context)
    }
}
```

### 📍 Gestion Géolocalisation

#### **iOS - Location Updates pour Widgets**

```swift
class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }

        // Mettre à jour la localisation
        self.currentLocation = UserLocation(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            address: nil,
            city: nil,
            country: nil,
            lastUpdated: Date()
        )

        // Actualiser les widgets avec la nouvelle position
        WidgetCenter.shared.reloadAllTimelines()
    }
}
```

#### **Android - Location Service pour Widgets**

```kotlin
@AndroidEntryPoint
class LocationWidgetService : Service(), LocationListener {

    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient
    @Inject lateinit var widgetPreferences: WidgetPreferences

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                // Sauvegarder la nouvelle position
                val currentData = widgetPreferences.loadWidgetData()
                if (currentData != null) {
                    val updatedData = currentData.copy(
                        userLatitude = location.latitude.toFloat(),
                        userLongitude = location.longitude.toFloat(),
                        lastUpdate = Date()
                    )
                    widgetPreferences.saveWidgetData(updatedData)

                    // Mettre à jour tous les widgets
                    LoveWidgetProvider.updateAllWidgets(this@LocationWidgetService)
                    DistanceWidgetProvider.updateAllWidgets(this@LocationWidgetService)
                }
            }
        }
    }

    fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TimeUnit.MINUTES.toMillis(15)
        ).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }
}
```

---

## 📊 Résumé des Différences iOS vs Android

| Aspect                     | iOS                                                    | Android                                   |
| -------------------------- | ------------------------------------------------------ | ----------------------------------------- |
| **Widgets Supportés**      | Small, Medium, AccessoryCircular, AccessoryRectangular | Small, Medium (via redimensionnement)     |
| **Timeline**               | TimelineProvider avec 60 entrées/heure                 | AppWidgetProvider avec updatePeriodMillis |
| **Cache Partagé**          | App Group + UserDefaults                               | SharedPreferences                         |
| **Images**                 | App Group FileManager                                  | Cache interne + Glide                     |
| **Background Updates**     | WidgetCenter.reloadAllTimelines()                      | BroadcastReceiver + Service               |
| **Géolocalisation**        | LocationService.shared                                 | FusedLocationProviderClient               |
| **Cloud Functions**        | Directement depuis WidgetService                       | Via Repository pattern                    |
| **Mise à Jour Périodique** | Timeline policy                                        | WorkManager                               |

---

## ✅ Checklist d'Implémentation Android

### Phase 1: Structure de Base

- [ ] Créer les Widget Providers (LoveWidgetProvider, DistanceWidgetProvider)
- [ ] Implémenter le système SharedPreferences pour le cache
- [ ] Créer les layouts XML des widgets
- [ ] Configurer les Widget Info dans res/xml/

### Phase 2: Data Layer

- [ ] Implémenter WidgetRepository avec les mêmes Cloud Functions
- [ ] Créer WidgetPreferences pour la gestion du cache
- [ ] Implémenter le calcul des statistiques relationnelles
- [ ] Gérer les appels sécurisés aux Cloud Functions

### Phase 3: Background Services

- [ ] Créer WidgetUpdateService pour la synchronisation
- [ ] Implémenter WorkManager pour les mises à jour périodiques
- [ ] Gérer LocationWidgetService pour les updates GPS
- [ ] Configurer les BroadcastReceivers

### Phase 4: UI et Interface

- [ ] Gérer le téléchargement et cache des images de profil
- [ ] Localisation complète de tous les textes
- [ ] Implémenter la création de bitmaps pour les initiales
- [ ] Gérer les clics et intents vers l'application principale

### Phase 5: Configuration et Tutoriels

- [ ] Créer WidgetsConfigurationActivity
- [ ] Implémenter les tutoriels d'ajout de widgets
- [ ] Gérer les aperçus de widgets
- [ ] Tests et validation

Cette implémentation Android reproduira fidèlement toutes les fonctionnalités des widgets iOS avec la même qualité, tous les widgets étant disponibles gratuitement pour tous les utilisateurs.

<parameter name="explanation">Créer le rapport complet sur les widgets avec toutes les informations demandées
