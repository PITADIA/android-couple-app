# 🔄 GESTION DES ÉTATS - Système Complet iOS & Android

## 🎯 Vue d'Ensemble

Système centralisé de gestion des états dans l'application iOS avec synchronisation Firebase en temps réel, détection de suppression de compte, et gestion des connexions partenaires.

---

## 🏗️ Architecture des États

### AppState - Centre de Contrôle

```swift
class AppState: ObservableObject {
    @Published var isOnboardingCompleted: Bool = false
    @Published var isAuthenticated: Bool = false
    @Published var currentUser: AppUser?
    @Published var isLoading: Bool = true
    @Published var freemiumManager: FreemiumManager?

    // Services d'état connectés
    @Published var favoritesService: FavoritesService?
    @Published var categoryProgressService: CategoryProgressService?
    @Published var partnerConnectionService: PartnerConnectionNotificationService?
    @Published var partnerSubscriptionService: PartnerSubscriptionNotificationService?
    @Published var partnerSubscriptionSyncService: PartnerSubscriptionSyncService?
    @Published var partnerLocationService: PartnerLocationService?
    @Published var journalService: JournalService?
    @Published var widgetService: WidgetService?
    @Published var locationService: LocationService?
    @Published var reviewService: ReviewRequestService?
}
```

---

## 🔐 États d'Authentification

### 1. FirebaseService - Listener Principal

```swift
func checkAuthenticationState() {
    Auth.auth().addStateDidChangeListener { [weak self] _, firebaseUser in
        if let firebaseUser = firebaseUser {
            // Vérifier authentification Apple
            if firebaseUser.providerData.contains(where: { $0.providerID == "apple.com" }) {
                print("🔥 Authentification Apple confirmée")
                self?.loadUserData(uid: firebaseUser.uid)
            } else {
                print("⚠️ Provider non-Apple détecté, maintien session")
                self?.loadUserData(uid: firebaseUser.uid)
            }
        } else {
            print("🔥 Aucun utilisateur Firebase")
            self?.isAuthenticated = false
            self?.currentUser = nil
        }
    }
}
```

### 2. Détection Suppression de Compte

```swift
func loadUserData(uid: String) {
    db.collection("users").document(uid).getDocument { [weak self] document, error in
        guard let document = document, document.exists,
              let data = document.data() else {
            print("🔥 Aucune donnée trouvée - onboarding requis")
            // Utilisateur supprimé ou nouveau
            self?.isAuthenticated = true
            self?.currentUser = nil
            return
        }

        // DÉTECTION SUPPRESSION RATÉE
        let lastLoginDate = data["lastLoginDate"] as? Timestamp
        let createdAt = data["createdAt"] as? Timestamp

        if let lastLogin = lastLoginDate?.dateValue(),
           let creation = createdAt?.dateValue() {
            let timeSinceCreation = Date().timeIntervalSince(creation)
            let timeSinceLastLogin = Date().timeIntervalSince(lastLogin)

            // Si compte récent MAIS dernière connexion ancienne = suppression ratée
            if timeSinceCreation < 300 && timeSinceLastLogin > 60 {
                print("🔥 DÉTECTION - Possible reconnexion après suppression ratée")

                // Supprimer les données résiduelles
                self?.db.collection("users").document(uid).delete()

                // Forcer l'onboarding
                self?.isAuthenticated = true
                self?.currentUser = nil
                return
            }
        }

        // Charger normalement les données utilisateur
        let user = AppUser(from: data)
        self?.currentUser = user
        self?.isAuthenticated = true
    }
}
```

### 3. UserCacheManager - Cache Local

```swift
class UserCacheManager {
    func cacheUser(_ user: AppUser) {
        let encoder = JSONEncoder()
        if let data = try? encoder.encode(user) {
            userDefaults.set(data, forKey: cacheKey)
            userDefaults.set(Date().timeIntervalSince1970, forKey: cacheTimestampKey)
            print("💾 UserCacheManager: Utilisateur mis en cache")
        }
    }

    func getCachedUser() -> AppUser? {
        guard let data = userDefaults.data(forKey: cacheKey) else { return nil }

        // Vérifier âge du cache (7 jours max)
        if let timestamp = userDefaults.object(forKey: cacheTimestampKey) as? TimeInterval {
            let age = Date().timeIntervalSince1970 - timestamp
            if age > 7 * 24 * 3600 {
                print("⏰ Cache expiré, nettoyage")
                clearCache()
                return nil
            }
        }

        return try? JSONDecoder().decode(AppUser.self, from: data)
    }

    func clearCache() {
        userDefaults.removeObject(forKey: cacheKey)
        userDefaults.removeObject(forKey: cacheTimestampKey)
        clearCachedProfileImage()
        clearCachedPartnerImage()
    }
}
```

---

## 💳 États d'Abonnement

### 1. Listener Temps Réel Firebase

```swift
func startListeningForSubscriptionChanges() {
    guard let user = Auth.auth().currentUser else { return }

    subscriptionListener = db.collection("users").document(user.uid)
        .addSnapshotListener { [weak self] snapshot, error in
            guard let data = snapshot?.data() else { return }

            let isSubscribed = data["isSubscribed"] as? Bool ?? false
            let subscriptionType = data["subscriptionType"] as? String

            // Détecter changement d'abonnement
            if let currentUser = self?.currentUser,
               currentUser.isSubscribed != isSubscribed {

                var updatedUser = currentUser
                updatedUser.isSubscribed = isSubscribed

                // Gérer abonnement partagé
                if subscriptionType == "shared_from_partner" {
                    updatedUser.subscriptionInheritedFrom = data["subscriptionSharedFrom"] as? String
                    updatedUser.subscriptionInheritedAt = (data["subscriptionSharedAt"] as? Timestamp)?.dateValue()
                }

                DispatchQueue.main.async {
                    self?.currentUser = updatedUser
                    print("🔥 Abonnement mis à jour localement: \(isSubscribed)")

                    // Notifier changement
                    NotificationCenter.default.post(name: .subscriptionUpdated, object: nil)
                }
            }
        }
}
```

### 2. Synchronisation Abonnement Partenaire

```swift
class PartnerSubscriptionSyncService {
    private func startListeningForUser() {
        guard let currentUser = Auth.auth().currentUser else { return }

        userListener = Firestore.firestore()
            .collection("users")
            .document(currentUser.uid)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error = error {
                    // Gérer erreur de permissions (compte supprimé)
                    if error.localizedDescription.contains("permissions") {
                        print("⚠️ Erreur permissions - Vérification auth")
                        self?.handleAuthenticationError()
                    }
                    return
                }

                guard let data = snapshot?.data() else { return }

                // Si partenaire connecté, écouter ses changements
                if let partnerId = data["partnerId"] as? String, !partnerId.isEmpty {
                    print("🔍 partnerId valide trouvé")
                    self?.startListeningForPartner(partnerId: partnerId)
                } else {
                    print("🔍 Pas de partenaire connecté")
                    self?.stopListeningForPartner()
                }
            }
    }

    private func syncSubscriptionViaCloudFunction(userId: String, partnerId: String) async {
        do {
            let functions = Functions.functions()
            let result = try await functions.httpsCallable("syncPartnerSubscriptions").call([
                "userId": userId,
                "partnerId": partnerId
            ])

            if let resultData = result.data as? [String: Any],
               let success = resultData["success"] as? Bool, success {
                print("✅ Synchronisation réussie")

                // Notifier héritage abonnement
                if let inherited = resultData["subscriptionInherited"] as? Bool,
                   inherited,
                   let fromPartnerName = resultData["fromPartnerName"] as? String {

                    await MainActor.run {
                        NotificationCenter.default.post(
                            name: .partnerSubscriptionShared,
                            object: nil,
                            userInfo: [
                                "partnerId": userId,
                                "fromPartnerName": fromPartnerName
                            ]
                        )
                    }
                }
            }
        } catch {
            print("❌ Erreur synchronisation: \(error)")
        }
    }
}
```

---

## 👥 États de Connexion Partenaire

### 1. Détection Changement Partenaire

```swift
// AppState observer
firebaseService.$currentUser
    .sink { [weak self] user in
        print("🔄 Changement utilisateur détecté")
        if let user = user {
            if let partnerId = user.partnerId, !partnerId.isEmpty {
                print("🔄 Utilisateur reconnecté - Redémarrage services partenaires")
                self?.partnerLocationService?.configureListener(for: partnerId)

                // Reconfigurer services avec partenaire
                Task { @MainActor in
                    DailyQuestionService.shared.configure(with: self!)
                }
            } else {
                print("🔄 Pas de partenaire - Arrêt des services")
                self?.partnerLocationService?.configureListener(for: nil)
            }
        } else {
            print("🔄 Utilisateur nil - Arrêt des services")
            self?.partnerLocationService?.configureListener(for: nil)
        }
    }
    .store(in: &cancellables)
```

### 2. Notifications Système

```swift
extension Notification.Name {
    static let subscriptionInherited = Notification.Name("subscriptionInherited")
    static let partnerConnected = Notification.Name("partnerConnected")
    static let partnerDisconnected = Notification.Name("partnerDisconnected")
    static let partnerConnectionSuccess = Notification.Name("partnerConnectionSuccess")
    static let shouldShowConnectionSuccess = Notification.Name("shouldShowConnectionSuccess")
    static let subscriptionUpdated = Notification.Name("subscriptionUpdated")
    static let partnerSubscriptionShared = Notification.Name("partnerSubscriptionShared")
    static let partnerSubscriptionRevoked = Notification.Name("partnerSubscriptionRevoked")
    static let introFlagsDidReset = Notification.Name("introFlagsDidReset")
}
```

---

## 🗑️ Détection Suppression de Compte

### 1. Méthodes de Détection

```swift
// A. Erreur de permissions Firebase
if error.localizedDescription.contains("permissions") {
    print("⚠️ Erreur permissions - Compte possiblement supprimé")
    self?.handleAuthenticationError()
}

// B. Document inexistant mais auth valide
guard let document = document, document.exists else {
    print("🔥 Document inexistant - Compte supprimé ou nouveau")
    self?.isAuthenticated = true
    self?.currentUser = nil
    return
}

// C. Incohérence temporelle (suppression ratée)
if timeSinceCreation < 300 && timeSinceLastLogin > 60 {
    print("🔥 DÉTECTION - Reconnexion après suppression ratée")
    // Supprimer données résiduelles
    self?.db.collection("users").document(uid).delete()
    // Forcer onboarding
    self?.isAuthenticated = true
    self?.currentUser = nil
}
```

### 2. Processus de Nettoyage

```swift
func deleteAccount() {
    print("AppState: Suppression du compte")

    // Nettoyer cache local
    UserCacheManager.shared.clearCache()

    // Déconnexion RevenueCat
    Purchases.shared.logOut { (customerInfo, error) in
        print("✅ RevenueCat utilisateur déconnecté")
    }

    // Réinitialiser état complet
    firebaseService.signOut()
    isOnboardingCompleted = false
    isAuthenticated = false
    isOnboardingInProgress = false
    currentUser = nil
    isLoading = false
}
```

---

## 🔥 Communication Firebase

### 1. Listeners Temps Réel

```swift
// Utilisateur principal
userListener = db.collection("users").document(userId)
    .addSnapshotListener { snapshot, error in
        // Traitement changements utilisateur
    }

// Abonnements
subscriptionListener = db.collection("users").document(userId)
    .addSnapshotListener { snapshot, error in
        // Traitement changements abonnement
    }

// Partenaire (via services spécialisés)
partnerListener = db.collection("users").document(partnerId)
    .addSnapshotListener { snapshot, error in
        // Traitement changements partenaire
    }
```

### 2. Cloud Functions - Opérations Serveur

```javascript
// index.js - Synchronisation abonnements
exports.syncPartnerSubscriptions = functions.https.onCall(
  async (data, context) => {
    const { userId, partnerId } = data;

    // Récupérer données utilisateur et partenaire
    const userDoc = await admin
      .firestore()
      .collection("users")
      .doc(userId)
      .get();
    const partnerDoc = await admin
      .firestore()
      .collection("users")
      .doc(partnerId)
      .get();

    const userData = userDoc.data();
    const partnerData = partnerDoc.data();

    // Logique de partage d'abonnement
    if (userData.isSubscribed && !partnerData.isSubscribed) {
      // Partenaire hérite de l'abonnement
      await partnerDoc.ref.update({
        isSubscribed: true,
        subscriptionType: "shared_from_partner",
        subscriptionSharedFrom: userId,
        subscriptionSharedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      return {
        success: true,
        subscriptionInherited: true,
        fromPartnerName: userData.name,
      };
    }

    return { success: true, subscriptionInherited: false };
  }
);

// Suppression utilisateur
exports.deleteUserAccount = functions.https.onCall(async (data, context) => {
  const userId = context.auth.uid;

  // 1. Gérer déconnexion partenaire
  const userDoc = await admin.firestore().collection("users").doc(userId).get();
  const userData = userDoc.data();

  if (userData.partnerId) {
    // Déconnecter partenaire
    await admin.firestore().collection("users").doc(userData.partnerId).update({
      partnerId: admin.firestore.FieldValue.delete(),
      connectedPartnerCode: admin.firestore.FieldValue.delete(),
    });

    // Désactiver abonnement hérité si applicable
    if (partnerData.subscriptionSharedFrom === userId) {
      await partnerDoc.ref.update({
        isSubscribed: false,
        subscriptionType: admin.firestore.FieldValue.delete(),
      });
    }
  }

  // 2. Supprimer codes partenaires
  const codes = await admin
    .firestore()
    .collection("partnerCodes")
    .where("userId", "==", userId)
    .get();

  for (const doc of codes.docs) {
    await doc.ref.delete();
  }

  // 3. Supprimer document utilisateur
  await admin.firestore().collection("users").doc(userId).delete();

  // 4. Supprimer compte Auth
  await admin.auth().deleteUser(userId);

  return { success: true };
});
```

---

## 🤖 Adaptation Android (Kotlin/Jetpack Compose)

### 1. Architecture MVVM

```kotlin
// AppState - StateFlow centralisé
class AppStateRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferences: SharedPreferences
) {
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated = _isAuthenticated.asStateFlow()

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _subscriptionStatus = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Unknown)
    val subscriptionStatus = _subscriptionStatus.asStateFlow()

    private val _partnerConnection = MutableStateFlow<PartnerConnectionState>(PartnerConnectionState.NotConnected)
    val partnerConnection = _partnerConnection.asStateFlow()

    init {
        setupAuthListener()
        loadCachedUser()
    }
}
```

### 2. Listeners Firebase Android

```kotlin
class FirebaseStateManager(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private var userListener: ListenerRegistration? = null
    private var subscriptionListener: ListenerRegistration? = null

    fun setupAuthListener() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // Vérifier provider Apple
                val hasAppleProvider = user.providerData.any {
                    it.providerId == "apple.com"
                }

                if (hasAppleProvider || user.providerData.isNotEmpty()) {
                    loadUserData(user.uid)
                }
            } else {
                _isAuthenticated.value = false
                _currentUser.value = null
            }
        }
    }

    private fun loadUserData(uid: String) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val data = document.data ?: return@addOnSuccessListener

                    // Détection suppression ratée (même logique qu'iOS)
                    val lastLogin = data["lastLoginDate"] as? Timestamp
                    val createdAt = data["createdAt"] as? Timestamp

                    if (lastLogin != null && createdAt != null) {
                        val timeSinceCreation = System.currentTimeMillis() - createdAt.toDate().time
                        val timeSinceLastLogin = System.currentTimeMillis() - lastLogin.toDate().time

                        if (timeSinceCreation < 300000 && timeSinceLastLogin > 60000) {
                            // Suppression ratée détectée
                            Log.d("Firebase", "DÉTECTION - Reconnexion après suppression ratée")

                            // Supprimer données résiduelles
                            firestore.collection("users").document(uid).delete()

                            // Forcer onboarding
                            _isAuthenticated.value = true
                            _currentUser.value = null
                            return@addOnSuccessListener
                        }
                    }

                    // Charger utilisateur normalement
                    val user = AppUser.fromMap(data)
                    _currentUser.value = user
                    _isAuthenticated.value = true

                    // Démarrer listeners
                    startSubscriptionListener(uid)

                } else {
                    // Document inexistant - compte supprimé ou nouveau
                    _isAuthenticated.value = true
                    _currentUser.value = null
                }
            }
            .addOnFailureListener { error ->
                Log.e("Firebase", "Erreur chargement utilisateur: ${error.message}")
                if (error.message?.contains("permissions") == true) {
                    handleAccountDeletionDetected()
                }
            }
    }

    private fun startSubscriptionListener(uid: String) {
        subscriptionListener?.remove()

        subscriptionListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (error.message?.contains("permissions") == true) {
                        handleAccountDeletionDetected()
                    }
                    return@addSnapshotListener
                }

                val data = snapshot?.data ?: return@addSnapshotListener
                val isSubscribed = data["isSubscribed"] as? Boolean ?: false
                val subscriptionType = data["subscriptionType"] as? String

                // Mettre à jour état abonnement
                val newStatus = when {
                    isSubscribed && subscriptionType == "shared_from_partner" ->
                        SubscriptionStatus.SharedFromPartner
                    isSubscribed -> SubscriptionStatus.Active
                    else -> SubscriptionStatus.Inactive
                }

                if (_subscriptionStatus.value != newStatus) {
                    _subscriptionStatus.value = newStatus

                    // Broadcast changement
                    LocalBroadcastManager.getInstance(context).sendBroadcast(
                        Intent("subscription_updated")
                    )
                }
            }
    }

    private fun handleAccountDeletionDetected() {
        Log.w("Firebase", "Suppression de compte détectée")

        // Nettoyer cache local
        UserCacheManager.clearCache()

        // Réinitialiser état
        _isAuthenticated.value = false
        _currentUser.value = null
        _isLoading.value = false

        // Déconnexion locale
        auth.signOut()
    }
}
```

### 3. Cache Android

```kotlin
class UserCacheManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun cacheUser(user: AppUser) {
        val json = gson.toJson(user)
        val timestamp = System.currentTimeMillis()

        prefs.edit()
            .putString(CACHE_KEY, json)
            .putLong(TIMESTAMP_KEY, timestamp)
            .apply()

        Log.d("Cache", "Utilisateur mis en cache")
    }

    fun getCachedUser(): AppUser? {
        val json = prefs.getString(CACHE_KEY, null) ?: return null
        val timestamp = prefs.getLong(TIMESTAMP_KEY, 0)

        // Vérifier âge du cache (7 jours)
        val age = System.currentTimeMillis() - timestamp
        if (age > 7 * 24 * 60 * 60 * 1000) {
            Log.d("Cache", "Cache expiré, nettoyage")
            clearCache()
            return null
        }

        return try {
            gson.fromJson(json, AppUser::class.java)
        } catch (e: Exception) {
            Log.e("Cache", "Erreur décodage cache: ${e.message}")
            clearCache()
            null
        }
    }

    fun clearCache() {
        prefs.edit()
            .remove(CACHE_KEY)
            .remove(TIMESTAMP_KEY)
            .apply()

        // Nettoyer aussi images en cache
        clearCachedImages()
    }

    companion object {
        private const val CACHE_KEY = "cached_user"
        private const val TIMESTAMP_KEY = "cache_timestamp"
    }
}
```

### 4. ViewModel Principal Android

```kotlin
class MainViewModel(
    private val appStateRepository: AppStateRepository,
    private val firebaseStateManager: FirebaseStateManager,
    private val partnerSubscriptionService: PartnerSubscriptionService
) : ViewModel() {

    val isAuthenticated = appStateRepository.isAuthenticated
    val currentUser = appStateRepository.currentUser
    val isLoading = appStateRepository.isLoading
    val subscriptionStatus = appStateRepository.subscriptionStatus
    val partnerConnection = appStateRepository.partnerConnection

    // État composite pour UI
    val uiState = combine(
        isAuthenticated,
        currentUser,
        subscriptionStatus,
        partnerConnection
    ) { authenticated, user, subscription, partner ->
        MainUiState(
            isAuthenticated = authenticated,
            user = user,
            subscriptionStatus = subscription,
            partnerConnectionState = partner,
            showOnboarding = authenticated && user == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        // Observer changements pour redémarrer services
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user?.partnerId?.isNotEmpty() == true) {
                    partnerSubscriptionService.startListening(user.partnerId!!)
                } else {
                    partnerSubscriptionService.stopListening()
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            appStateRepository.signOut()
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            appStateRepository.deleteAccount()
        }
    }
}

data class MainUiState(
    val isAuthenticated: Boolean = false,
    val user: AppUser? = null,
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.Unknown,
    val partnerConnectionState: PartnerConnectionState = PartnerConnectionState.NotConnected,
    val showOnboarding: Boolean = false
)

sealed class SubscriptionStatus {
    object Unknown : SubscriptionStatus()
    object Active : SubscriptionStatus()
    object Inactive : SubscriptionStatus()
    object SharedFromPartner : SubscriptionStatus()
}

sealed class PartnerConnectionState {
    object NotConnected : PartnerConnectionState()
    data class Connected(val partnerId: String, val partnerName: String) : PartnerConnectionState()
}
```

### 5. Notifications Android

```kotlin
// Équivalent NotificationCenter
class AppNotificationManager private constructor() {
    private val _events = MutableSharedFlow<AppEvent>()
    val events = _events.asSharedFlow()

    fun postEvent(event: AppEvent) {
        _events.tryEmit(event)
    }

    companion object {
        @Volatile
        private var INSTANCE: AppNotificationManager? = null

        fun getInstance(): AppNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppNotificationManager().also { INSTANCE = it }
            }
        }
    }
}

sealed class AppEvent {
    object SubscriptionUpdated : AppEvent()
    data class PartnerConnected(val partnerId: String) : AppEvent()
    object PartnerDisconnected : AppEvent()
    data class PartnerSubscriptionShared(val partnerName: String) : AppEvent()
    object AccountDeleted : AppEvent()
}
```

---

## 📋 Résumé Système

### ✅ États Gérés

1. **Authentification** → Firebase Auth + Apple Sign In
2. **Utilisateur** → Cache local + Firestore sync
3. **Abonnement** → RevenueCat + Firebase + Partage partenaire
4. **Connexion partenaire** → Codes + Listeners temps réel
5. **Services** → Location, Journal, Favoris, Widgets

### 🔄 Synchronisation

- **Temps réel** via Firebase Listeners
- **Cache local** pour performance
- **Cloud Functions** pour sécurité
- **Notifications** pour coordination services

### 🛡️ Détection Suppression

- **Erreurs permissions** Firebase
- **Documents inexistants** Firestore
- **Incohérences temporelles** (suppression ratée)
- **Nettoyage automatique** données résiduelles

### 📱 Communication

- **iOS** → Combine + NotificationCenter
- **Android** → StateFlow + SharedFlow + BroadcastManager
- **Firebase** → Cloud Functions + Listeners
- **Cache** → UserDefaults (iOS) + SharedPreferences (Android)
