# 💾 Rapport Complet - Système Défis Sauvegardés

## 📋 Vue d'Ensemble

Le système de défis sauvegardés permet aux utilisateurs de **sauvegarder leurs défis préférés** pour les retrouver facilement plus tard. Contrairement aux favoris partagés (questions), les défis sauvegardés sont **personnels à chaque utilisateur** et ne sont **pas partagés automatiquement** avec le partenaire.

---

## 🏗️ Architecture Système

### 🔧 Stack Technique

**iOS (Frontend)**

- `SavedChallengesService` - Service principal de gestion
- `SwiftUI` - Interface utilisateur native
- `Firebase Firestore` - Base de données en temps réel
- `Firebase Auth` - Authentification et sécurité
- `Combine` - Reactive Programming

**Backend (Firebase)**

- **Firestore Collection** - `savedChallenges`
- **Firebase Rules** - Sécurité basée sur `userId`
- **Real-time Listeners** - Synchronisation automatique
- **Composite Indexes** - Performance des requêtes

---

## 📊 Structure des Données

### 🗃️ Modèle SavedChallenge

```swift
// Models/DailyChallenge.swift
struct SavedChallenge: Codable, Identifiable, Hashable {
    let id: String
    let challengeKey: String      // Clé de localisation du défi
    let challengeDay: Int         // Jour du défi original
    let savedAt: Date            // Date de sauvegarde
    let userId: String           // Firebase UID du propriétaire

    // Propriété calculée pour le texte localisé
    var localizedText: String {
        return challengeKey.localized(tableName: "DailyChallenges")
    }

    // Génération ID unique pour éviter les doublons
    static func generateId(userId: String, challengeKey: String) -> String {
        return "\(userId)_\(challengeKey)"
    }
}
```

### 🔥 Structure Firestore

```javascript
// Collection: savedChallenges
savedChallenges/{documentId}/
├── id: string                    // ID unique généré
├── challengeKey: string         // "daily_challenge_42"
├── challengeDay: number         // 42
├── savedAt: timestamp          // Date sauvegarde
└── userId: string              // Firebase UID propriétaire
```

**Exemple document Firestore :**

```json
{
  "id": "gWEioZkgz3U4COFI1uJUTlGgQif2_daily_challenge_42",
  "challengeKey": "daily_challenge_42",
  "challengeDay": 42,
  "savedAt": "2024-03-15T10:30:00Z",
  "userId": "gWEioZkgz3U4COFI1uJUTlGgQif2"
}
```

---

## 🛠️ Service Principal - SavedChallengesService

### 📱 Architecture du Service

```swift
// Services/SavedChallengesService.swift
@MainActor
class SavedChallengesService: ObservableObject {
    static let shared = SavedChallengesService()

    // États observables
    @Published var savedChallenges: [SavedChallenge] = []
    @Published var isLoading: Bool = false
    @Published var lastSavedChallenge: SavedChallenge?

    private var db = Firestore.firestore()
    private var savedChallengesListener: ListenerRegistration?
    private weak var appState: AppState?

    // Configuration avec AppState
    func configure(with appState: AppState) {
        self.appState = appState
        setupListener()
    }
}
```

### 🔄 Configuration Listener Temps Réel

```swift
// Services/SavedChallengesService.swift
private func setupListener() {
    guard let firebaseUser = Auth.auth().currentUser else {
        print("🔥 SavedChallengesService: Aucun utilisateur connecté")
        return
    }

    print("🔥 SavedChallengesService: Configuration listener pour: \(firebaseUser.uid)")

    savedChallengesListener?.remove()

    // 🎯 REQUÊTE SÉCURISÉE : Filtrer par userId + tri par date
    savedChallengesListener = db.collection("savedChallenges")
        .whereField("userId", isEqualTo: firebaseUser.uid)
        .order(by: "savedAt", descending: true)
        .addSnapshotListener { [weak self] snapshot, error in
            guard let self = self else { return }

            if let error = error {
                print("❌ SavedChallengesService: Erreur listener: \(error)")
                return
            }

            guard let documents = snapshot?.documents else {
                self.savedChallenges = []
                return
            }

            // Parser les documents Firestore
            var challenges: [SavedChallenge] = []
            for document in documents {
                if let challenge = self.parseSavedChallengeDocument(document: document) {
                    challenges.append(challenge)
                }
            }

            self.savedChallenges = challenges
            print("✅ SavedChallengesService: \(challenges.count) défis sauvegardés chargés")
        }
}
```

### 💾 Sauvegarde d'un Défi

```swift
// Services/SavedChallengesService.swift
func saveChallenge(_ challenge: DailyChallenge) {
    guard let firebaseUser = Auth.auth().currentUser else {
        print("❌ SavedChallengesService: Aucun utilisateur connecté")
        return
    }

    isLoading = true

    // Créer le défi sauvegardé
    let savedChallenge = SavedChallenge(
        challengeKey: challenge.challengeKey,
        challengeDay: challenge.challengeDay,
        userId: firebaseUser.uid
    )

    // 🔑 ID UNIQUE : Évite les doublons (utilisateur + défi)
    let documentId = SavedChallenge.generateId(
        userId: firebaseUser.uid,
        challengeKey: challenge.challengeKey
    )

    // Données Firestore
    let challengeData: [String: Any] = [
        "challengeKey": savedChallenge.challengeKey,
        "challengeDay": savedChallenge.challengeDay,
        "savedAt": Timestamp(date: savedChallenge.savedAt),
        "userId": savedChallenge.userId
    ]

    // 🔥 SAUVEGARDE FIRESTORE avec ID prévisible
    db.collection("savedChallenges").document(documentId).setData(challengeData) { [weak self] error in
        self?.isLoading = false

        if let error = error {
            print("❌ SavedChallengesService: Erreur sauvegarde: \(error)")
        } else {
            print("✅ SavedChallengesService: Défi sauvegardé avec succès")
            self?.lastSavedChallenge = savedChallenge

            // Animation de confirmation (3 secondes)
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                self?.lastSavedChallenge = nil
            }
        }
    }
}
```

### 🗑️ Suppression d'un Défi

```swift
// Services/SavedChallengesService.swift
func deleteChallenge(_ challenge: SavedChallenge) {
    guard let firebaseUser = Auth.auth().currentUser else { return }

    isLoading = true

    // 🔑 SUPPRESSION par ID unique
    let documentId = SavedChallenge.generateId(
        userId: firebaseUser.uid,
        challengeKey: challenge.challengeKey
    )

    db.collection("savedChallenges").document(documentId).delete { [weak self] error in
        self?.isLoading = false

        if let error = error {
            print("❌ SavedChallengesService: Erreur suppression: \(error)")
        } else {
            print("✅ SavedChallengesService: Défi supprimé avec succès")
        }
    }
}
```

### 🔍 Vérification État Sauvegarde

```swift
// Services/SavedChallengesService.swift
func isChallengeAlreadySaved(_ challenge: DailyChallenge) -> Bool {
    return savedChallenges.contains { saved in
        saved.challengeKey == challenge.challengeKey
    }
}

func getSavedChallengesCount() -> Int {
    return savedChallenges.count
}
```

---

## 🎨 Interface Utilisateur

### 📱 Vue Principale - DailyChallengeMainView

```swift
// Views/DailyChallenge/DailyChallengeMainView.swift
struct DailyChallengeMainView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyChallengeService = DailyChallengeService.shared
    @StateObject private var savedChallengesService = SavedChallengesService.shared
    @State private var showingSavedChallenges = false

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Header avec icône défis sauvegardés
                HStack {
                    Spacer()

                    VStack(spacing: 4) {
                        Text(NSLocalizedString("daily_challenges_title", tableName: "DailyChallenges", comment: ""))
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.black)
                    }

                    Spacer()

                    // 🔖 BOUTON DÉFIS SAUVEGARDÉS
                    Button(action: {
                        showingSavedChallenges = true
                    }) {
                        Image(systemName: "bookmark")
                            .font(.system(size: 20))
                            .foregroundColor(.black)
                    }
                }

                // Carte défi avec bouton sauvegarde
                if let currentChallenge = dailyChallengeService.currentChallenge {
                    DailyChallengeCardView(
                        challenge: currentChallenge,
                        onSave: {
                            handleChallengeSave(currentChallenge)
                        }
                    )
                }
            }
        }
        .sheet(isPresented: $showingSavedChallenges) {
            SavedChallengesView()
                .environmentObject(appState)
        }
        .onAppear {
            // 🔧 CONFIGURATION SERVICES
            dailyChallengeService.configure(with: appState)
            savedChallengesService.configure(with: appState)
        }
    }

    // 💾 GESTION SAUVEGARDE
    private func handleChallengeSave(_ challenge: DailyChallenge) {
        savedChallengesService.saveChallenge(challenge)

        // Analytics
        Analytics.logEvent("challenge_saved", parameters: [
            "challenge_key": challenge.challengeKey,
            "challenge_day": challenge.challengeDay
        ])
    }
}
```

### 📋 Vue Liste - SavedChallengesView

```swift
// Views/DailyChallenge/SavedChallengesView.swift
struct SavedChallengesView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var savedChallengesService = SavedChallengesService.shared
    @Environment(\.dismiss) private var dismiss
    @State private var showingDeleteAlert = false
    @State private var challengeToDelete: SavedChallenge?

    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    // Header avec fermeture
                    HStack {
                        Button(action: { dismiss() }) {
                            Image(systemName: "xmark")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundColor(.black)
                        }

                        Spacer()

                        Text("saved_challenges_title".localized(tableName: "DailyChallenges"))
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.black)

                        Spacer()

                        Spacer().frame(width: 18)
                    }
                    .padding(.horizontal, 20)

                    // Contenu principal
                    if savedChallengesService.isLoading {
                        VStack(spacing: 20) {
                            Spacer()
                            ProgressView().scaleEffect(1.2)
                            Text("Chargement de vos défis sauvegardés...")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            Spacer()
                        }
                    } else if savedChallengesService.savedChallenges.isEmpty {
                        // 📭 ÉTAT VIDE
                        VStack(spacing: 20) {
                            Spacer()

                            Image(systemName: "bookmark")
                                .font(.system(size: 60))
                                .foregroundColor(.gray)

                            Text("Aucun défi sauvegardé")
                                .font(.title2)
                                .fontWeight(.semibold)

                            Text("Sauvegardez vos défis préférés pour les retrouver facilement ici !")
                                .font(.body)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal)

                            Spacer()
                        }
                    } else {
                        // 📋 LISTE DES DÉFIS SAUVEGARDÉS
                        savedChallengesCardView
                    }
                }
            }
        }
        .onAppear {
            savedChallengesService.configure(with: appState)
        }
    }

    // 🎴 VUE CARTES DÉFIS (Style TabView)
    private var savedChallengesCardView: some View {
        TabView {
            ForEach(savedChallengesService.savedChallenges) { savedChallenge in
                SavedChallengeCard(
                    savedChallenge: savedChallenge,
                    onDelete: {
                        challengeToDelete = savedChallenge
                        showingDeleteAlert = true
                    }
                )
            }
        }
        .tabViewStyle(PageTabViewStyle(indexDisplayMode: .automatic))
        .alert("Supprimer ce défi ?", isPresented: $showingDeleteAlert) {
            Button("Annuler", role: .cancel) { }
            Button("Supprimer", role: .destructive) {
                if let challenge = challengeToDelete {
                    savedChallengesService.deleteChallenge(challenge)
                }
            }
        }
    }
}
```

### 🎴 Composant Carte - DailyChallengeCardView

```swift
// Views/DailyChallenge/DailyChallengeCardView.swift
struct DailyChallengeCardView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var savedChallengesService = SavedChallengesService.shared

    let challenge: DailyChallenge
    let onSave: (() -> Void)?

    @State private var showSaveConfirmation: Bool = false
    @State private var isAlreadySaved: Bool = false

    var body: some View {
        VStack(spacing: 24) {
            // Contenu du défi
            VStack(spacing: 16) {
                Text(challenge.localizedText)
                    .font(.title2)
                    .fontWeight(.medium)
                    .foregroundColor(.black)
                    .multilineTextAlignment(.center)
            }

            // Boutons d'action
            HStack(spacing: 20) {
                // 💾 BOUTON SAUVEGARDER
                Button(action: {
                    if !isAlreadySaved {
                        savedChallengesService.saveChallenge(challenge)
                        onSave?()

                        withAnimation(.spring()) {
                            showSaveConfirmation = true
                        }

                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                            withAnimation(.spring()) {
                                showSaveConfirmation = false
                            }
                        }
                    }
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: isAlreadySaved ? "bookmark.fill" : "bookmark")
                            .font(.system(size: 16, weight: .medium))

                        Text(isAlreadySaved ? "Déjà sauvegardé" : "Sauvegarder")
                            .font(.system(size: 16, weight: .medium))
                    }
                    .foregroundColor(isAlreadySaved ? .gray : .blue)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 25)
                            .fill(Color.white)
                            .shadow(color: .black.opacity(0.1), radius: 8, x: 0, y: 4)
                    )
                }
                .disabled(isAlreadySaved)
            }

            // 🎉 CONFIRMATION SAUVEGARDE
            if showSaveConfirmation {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundColor(.green)

                    Text("Défi sauvegardé !")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.green)
                }
                .transition(.scale.combined(with: .opacity))
            }
        }
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.white)
                .shadow(color: .black.opacity(0.08), radius: 12, x: 0, y: 6)
        )
        .onAppear {
            // Vérifier si déjà sauvegardé
            isAlreadySaved = savedChallengesService.isChallengeAlreadySaved(challenge)
        }
        .onChange(of: savedChallengesService.savedChallenges) { _ in
            // Mettre à jour l'état si modification
            isAlreadySaved = savedChallengesService.isChallengeAlreadySaved(challenge)
        }
    }
}
```

---

## 🔐 Sécurité et Permissions Firebase

### 🛡️ Règles Firestore Sécurisées

```javascript
// firebase/firestore.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // 💾 DÉFIS SAUVEGARDÉS - Accès personnel uniquement
    match /savedChallenges/{challengeId} {
      // ✅ LECTURE : Utilisateur propriétaire uniquement
      allow read: if request.auth != null &&
        request.auth.uid == resource.data.userId;

      // ✅ ÉCRITURE : Utilisateur propriétaire uniquement
      allow create: if request.auth != null &&
        request.auth.uid == request.resource.data.userId &&
        validateSavedChallengeData();

      // ✅ MISE À JOUR : Propriétaire + validation structure
      allow update: if request.auth != null &&
        request.auth.uid == resource.data.userId &&
        request.auth.uid == request.resource.data.userId &&
        validateSavedChallengeData();

      // ✅ SUPPRESSION : Propriétaire uniquement
      allow delete: if request.auth != null &&
        request.auth.uid == resource.data.userId;
    }
  }

  // 🔍 VALIDATION STRUCTURE DONNÉES
  function validateSavedChallengeData() {
    let data = request.resource.data;
    return data.keys().hasAll(['challengeKey', 'challengeDay', 'savedAt', 'userId']) &&
           data.challengeKey is string &&
           data.challengeKey.size() > 0 &&
           data.challengeDay is number &&
           data.challengeDay > 0 &&
           data.savedAt is timestamp &&
           data.userId is string &&
           data.userId == request.auth.uid;
  }
}
```

### 📊 Index Firestore Optimisé

```json
// firebase/firestore.indexes.json
{
  "indexes": [
    {
      "collectionGroup": "savedChallenges",
      "queryScope": "COLLECTION",
      "fields": [
        {
          "fieldPath": "userId",
          "order": "ASCENDING"
        },
        {
          "fieldPath": "savedAt",
          "order": "DESCENDING"
        }
      ]
    }
  ]
}
```

### 🚫 Prévention Erreurs "Missing Permission"

**1. Authentification Obligatoire**

```swift
// Toujours vérifier l'authentification avant accès
guard let firebaseUser = Auth.auth().currentUser else {
    print("❌ Utilisateur non authentifié - Accès refusé")
    return
}
```

**2. Filtrage Strict par UserId**

```swift
// Requête sécurisée avec filtre utilisateur
.whereField("userId", isEqualTo: firebaseUser.uid)
```

**3. Gestion d'Erreurs Robuste**

```swift
// Listener avec gestion d'erreurs
.addSnapshotListener { snapshot, error in
    if let error = error {
        print("❌ Erreur Firestore: \(error.localizedDescription)")

        // Vérifier si erreur de permissions
        if error.localizedDescription.contains("PERMISSION_DENIED") {
            print("🚫 Permissions insuffisantes - Réauthentification nécessaire")
            // Rediriger vers login si nécessaire
        }
        return
    }
}
```

---

## 🔄 Cycle de Vie et Gestion d'État

### ⚡ Configuration Service dans AppState

```swift
// ViewModels/AppState.swift
class AppState: ObservableObject {

    func configureServices() {
        // Configuration des services après authentification
        if isAuthenticated && currentUser != nil {
            DailyChallengeService.shared.configure(with: self)
            SavedChallengesService.shared.configure(with: self)
        }
    }

    func clearServicesOnLogout() {
        // Nettoyage à la déconnexion
        SavedChallengesService.shared.savedChallenges = []
    }
}
```

### 🔄 Synchronisation Temps Réel

**Avantages du Listener Firestore :**

```swift
// Mise à jour automatique UI quand :
// - Nouvel appareil sauvegarde défi
// - Suppression depuis autre session
// - Modifications données serveur
```

**Optimisation Performance :**

```swift
// Listener configuré une seule fois par session
// Réutilisation service Singleton
// Pagination automatique Firestore si besoin
```

---

## ❌ Défis NON Partagés Entre Partenaires

### 🔒 Architecture Personnel vs Partagé

**Défis Sauvegardés (Personnel)**

```
savedChallenges/{userId_challengeKey}/
├── userId: "user1_id"
├── challengeKey: "daily_challenge_42"
└── ... données personnelles
```

**Questions Favorites (Partagées)**

```
favoriteQuestions/{questionId}/
├── partnerIds: ["user1_id", "user2_id"]  // ← PARTAGE
├── questionKey: "question_123"
└── ... données partagées
```

### 🎯 Raisons Architecturales

1. **Défis = Préférences personnelles** (comme bookmarks)
2. **Questions = Contenu partagé couple** (discussions communes)
3. **Simplicité technique** - Pas de synchronisation partenaire complexe
4. **Performance** - Pas de règles cross-user complexes

### 🔧 Si Partage Souhaité (Future Feature)

```swift
// Extension possible SavedChallenge
struct SharedSavedChallenge {
    let id: String
    let challengeKey: String
    let challengeDay: Int
    let savedAt: Date
    let authorId: String        // Qui a sauvegardé
    let partnerIds: [String]    // Couple accès
    let isSharedWithPartner: Bool = true
}
```

---

# 🤖 Adaptation Android - Architecture Complète

## 📱 Stack Technique Android

### 🔧 Technologies Recommandées

```kotlin
// build.gradle (Module: app)
dependencies {
    // Firebase
    implementation 'com.google.firebase:firebase-firestore:24.7.1'
    implementation 'com.google.firebase:firebase-auth:22.1.0'

    // Jetpack Compose
    implementation 'androidx.compose.ui:ui:1.5.0'
    implementation 'androidx.compose.material3:material3:1.1.1'

    // State Management
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // Local Database
    implementation 'androidx.room:room-runtime:2.5.0'
    implementation 'androidx.room:room-ktx:2.5.0'

    // Dependency Injection
    implementation 'com.google.dagger:hilt-android:2.47'

    // UI Components
    implementation 'androidx.compose.foundation:foundation:1.5.0'
    implementation 'com.google.accompanist:accompanist-pager:0.32.0'
}
```

---

## 📊 Modèles de Données Android

### 🗃️ Entity Room (Cache Local)

```kotlin
// data/entities/SavedChallengeEntity.kt
@Entity(tableName = "saved_challenges")
data class SavedChallengeEntity(
    @PrimaryKey
    val id: String,
    val challengeKey: String,
    val challengeDay: Int,
    val savedAt: Long,
    val userId: String
) {
    companion object {
        fun generateId(userId: String, challengeKey: String): String {
            return "${userId}_${challengeKey}"
        }
    }
}
```

### 📦 Data Class Domaine

```kotlin
// domain/models/SavedChallenge.kt
data class SavedChallenge(
    val id: String = UUID.randomUUID().toString(),
    val challengeKey: String,
    val challengeDay: Int,
    val savedAt: Date = Date(),
    val userId: String
) {

    // Texte localisé du défi
    fun getLocalizedText(context: Context): String {
        val resourceId = context.resources.getIdentifier(
            challengeKey,
            "string",
            context.packageName
        )
        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            challengeKey // Fallback
        }
    }

    // Conversion vers Entity Room
    fun toEntity(): SavedChallengeEntity {
        return SavedChallengeEntity(
            id = id,
            challengeKey = challengeKey,
            challengeDay = challengeDay,
            savedAt = savedAt.time,
            userId = userId
        )
    }

    companion object {
        // Création depuis Entity Room
        fun fromEntity(entity: SavedChallengeEntity): SavedChallenge {
            return SavedChallenge(
                id = entity.id,
                challengeKey = entity.challengeKey,
                challengeDay = entity.challengeDay,
                savedAt = Date(entity.savedAt),
                userId = entity.userId
            )
        }

        // Création depuis Firestore
        fun fromFirestore(document: DocumentSnapshot): SavedChallenge? {
            return try {
                SavedChallenge(
                    id = document.id,
                    challengeKey = document.getString("challengeKey") ?: return null,
                    challengeDay = document.getLong("challengeDay")?.toInt() ?: return null,
                    savedAt = document.getTimestamp("savedAt")?.toDate() ?: Date(),
                    userId = document.getString("userId") ?: return null
                )
            } catch (e: Exception) {
                null
            }
        }

        fun generateId(userId: String, challengeKey: String): String {
            return "${userId}_${challengeKey}"
        }
    }
}
```

---

## 🗄️ Couche Données - Repository Pattern

### 📊 DAO Room

```kotlin
// data/dao/SavedChallengeDao.kt
@Dao
interface SavedChallengeDao {

    @Query("SELECT * FROM saved_challenges WHERE userId = :userId ORDER BY savedAt DESC")
    fun getSavedChallengesFlow(userId: String): Flow<List<SavedChallengeEntity>>

    @Query("SELECT * FROM saved_challenges WHERE userId = :userId ORDER BY savedAt DESC")
    suspend fun getSavedChallenges(userId: String): List<SavedChallengeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedChallenge(challenge: SavedChallengeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedChallenges(challenges: List<SavedChallengeEntity>)

    @Delete
    suspend fun deleteSavedChallenge(challenge: SavedChallengeEntity)

    @Query("DELETE FROM saved_challenges WHERE id = :id")
    suspend fun deleteSavedChallengeById(id: String)

    @Query("DELETE FROM saved_challenges WHERE userId = :userId")
    suspend fun deleteAllSavedChallenges(userId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_challenges WHERE userId = :userId AND challengeKey = :challengeKey)")
    suspend fun isChallengeAlreadySaved(userId: String, challengeKey: String): Boolean
}
```

### 🔥 Repository Firebase + Cache

```kotlin
// data/repositories/SavedChallengesRepository.kt
@Singleton
class SavedChallengesRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val dao: SavedChallengeDao,
    private val auth: FirebaseAuth
) {

    // Flow combiné Cache + Firebase
    fun getSavedChallengesFlow(): Flow<List<SavedChallenge>> {
        val currentUser = auth.currentUser ?: return flowOf(emptyList())

        return dao.getSavedChallengesFlow(currentUser.uid)
            .map { entities ->
                entities.map { SavedChallenge.fromEntity(it) }
            }
    }

    // Listener Firebase temps réel
    fun setupFirestoreListener(): Flow<List<SavedChallenge>> = callbackFlow {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            trySend(emptyList())
            return@callbackFlow
        }

        val listener = firestore.collection("savedChallenges")
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("SavedChallengesRepo", "Erreur listener Firestore", error)
                    return@addSnapshotListener
                }

                val challenges = snapshot?.documents?.mapNotNull { doc ->
                    SavedChallenge.fromFirestore(doc)
                } ?: emptyList()

                // Mettre à jour cache local
                CoroutineScope(Dispatchers.IO).launch {
                    dao.insertSavedChallenges(challenges.map { it.toEntity() })
                }

                trySend(challenges)
            }

        awaitClose { listener.remove() }
    }

    // Sauvegarder défi
    suspend fun saveChallenge(challenge: DailyChallenge): Result<SavedChallenge> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser
                    ?: return@withContext Result.failure(Exception("Utilisateur non connecté"))

                val savedChallenge = SavedChallenge(
                    challengeKey = challenge.challengeKey,
                    challengeDay = challenge.challengeDay,
                    userId = currentUser.uid
                )

                val documentId = SavedChallenge.generateId(currentUser.uid, challenge.challengeKey)

                val challengeData = mapOf(
                    "challengeKey" to savedChallenge.challengeKey,
                    "challengeDay" to savedChallenge.challengeDay,
                    "savedAt" to Timestamp(savedChallenge.savedAt),
                    "userId" to savedChallenge.userId
                )

                // Sauvegarder dans Firestore
                firestore.collection("savedChallenges")
                    .document(documentId)
                    .set(challengeData)
                    .await()

                // Mettre à jour cache local
                dao.insertSavedChallenge(savedChallenge.toEntity())

                Result.success(savedChallenge)

            } catch (e: Exception) {
                Log.e("SavedChallengesRepo", "Erreur sauvegarde", e)
                Result.failure(e)
            }
        }
    }

    // Supprimer défi
    suspend fun deleteChallenge(savedChallenge: SavedChallenge): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser
                    ?: return@withContext Result.failure(Exception("Utilisateur non connecté"))

                val documentId = SavedChallenge.generateId(currentUser.uid, savedChallenge.challengeKey)

                // Supprimer de Firestore
                firestore.collection("savedChallenges")
                    .document(documentId)
                    .delete()
                    .await()

                // Supprimer du cache local
                dao.deleteSavedChallengeById(savedChallenge.id)

                Result.success(Unit)

            } catch (e: Exception) {
                Log.e("SavedChallengesRepo", "Erreur suppression", e)
                Result.failure(e)
            }
        }
    }

    // Vérifier si défi déjà sauvegardé
    suspend fun isChallengeAlreadySaved(challenge: DailyChallenge): Boolean {
        return withContext(Dispatchers.IO) {
            val currentUser = auth.currentUser ?: return@withContext false
            dao.isChallengeAlreadySaved(currentUser.uid, challenge.challengeKey)
        }
    }
}
```

---

## 🎯 ViewModel MVVM

### 📊 SavedChallengesViewModel

```kotlin
// presentation/viewmodels/SavedChallengesViewModel.kt
@HiltViewModel
class SavedChallengesViewModel @Inject constructor(
    private val repository: SavedChallengesRepository,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedChallengesUiState())
    val uiState: StateFlow<SavedChallengesUiState> = _uiState.asStateFlow()

    private val _savedChallenges = MutableStateFlow<List<SavedChallenge>>(emptyList())
    val savedChallenges: StateFlow<List<SavedChallenge>> = _savedChallenges.asStateFlow()

    init {
        setupFirestoreListener()
        loadSavedChallenges()
    }

    private fun setupFirestoreListener() {
        viewModelScope.launch {
            repository.setupFirestoreListener()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
                .collect { challenges ->
                    _savedChallenges.value = challenges
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null
                    )
                }
        }
    }

    private fun loadSavedChallenges() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.getSavedChallengesFlow()
                .collect { challenges ->
                    _savedChallenges.value = challenges
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun saveChallenge(challenge: DailyChallenge) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = repository.saveChallenge(challenge)) {
                is Result.success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        lastSavedChallenge = result.getOrNull(),
                        showSaveConfirmation = true
                    )

                    // Analytics
                    analyticsService.logEvent("challenge_saved", mapOf(
                        "challenge_key" to challenge.challengeKey,
                        "challenge_day" to challenge.challengeDay
                    ))

                    // Masquer confirmation après 3 secondes
                    delay(3000)
                    _uiState.value = _uiState.value.copy(
                        showSaveConfirmation = false,
                        lastSavedChallenge = null
                    )
                }

                is Result.failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    fun deleteChallenge(savedChallenge: SavedChallenge) {
        viewModelScope.launch {
            when (val result = repository.deleteChallenge(savedChallenge)) {
                is Result.success -> {
                    analyticsService.logEvent("challenge_deleted", mapOf(
                        "challenge_key" to savedChallenge.challengeKey
                    ))
                }

                is Result.failure -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    suspend fun isChallengeAlreadySaved(challenge: DailyChallenge): Boolean {
        return repository.isChallengeAlreadySaved(challenge)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

// État UI
data class SavedChallengesUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastSavedChallenge: SavedChallenge? = null,
    val showSaveConfirmation: Boolean = false
)
```

---

## 🎨 Interface Utilisateur Jetpack Compose

### 📱 Écran Principal - DailyChallengeScreen

```kotlin
// presentation/screens/DailyChallengeScreen.kt
@Composable
fun DailyChallengeScreen(
    navController: NavController,
    viewModel: DailyChallengeViewModel = hiltViewModel(),
    savedChallengesViewModel: SavedChallengesViewModel = hiltViewModel()
) {
    val challengeState by viewModel.uiState.collectAsState()
    val savedChallengesState by savedChallengesViewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header avec titre et icône défis sauvegardés
            HeaderSection(
                onSavedChallengesClick = {
                    navController.navigate("saved_challenges")
                }
            )

            // Contenu principal
            when {
                challengeState.isLoading -> {
                    LoadingSection()
                }

                challengeState.currentChallenge != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item {
                            DailyChallengeCard(
                                challenge = challengeState.currentChallenge!!,
                                onSave = { challenge ->
                                    savedChallengesViewModel.saveChallenge(challenge)
                                }
                            )
                        }
                    }
                }

                else -> {
                    EmptyStateSection()
                }
            }
        }

        // Confirmation sauvegarde
        AnimatedVisibility(
            visible = savedChallengesState.showSaveConfirmation,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            SaveConfirmationDialog()
        }
    }
}

@Composable
private fun HeaderSection(
    onSavedChallengesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(24.dp))

        // Titre centré
        Text(
            text = stringResource(R.string.daily_challenges_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        // 🔖 BOUTON DÉFIS SAUVEGARDÉS
        IconButton(
            onClick = onSavedChallengesClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bookmark),
                contentDescription = "Défis sauvegardés",
                tint = Color.Black
            )
        }
    }
}
```

### 📋 Écran Liste - SavedChallengesScreen

```kotlin
// presentation/screens/SavedChallengesScreen.kt
@Composable
fun SavedChallengesScreen(
    navController: NavController,
    viewModel: SavedChallengesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedChallenges by viewModel.savedChallenges.collectAsState()

    val pagerState = rememberPagerState { savedChallenges.size }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var challengeToDelete by remember { mutableStateOf<SavedChallenge?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            SavedChallengesHeader(
                onBackClick = { navController.navigateUp() }
            )

            // Contenu
            when {
                uiState.isLoading -> {
                    LoadingSection()
                }

                savedChallenges.isEmpty() -> {
                    EmptyStateSection()
                }

                else -> {
                    // 🎴 PAGER HORIZONTAL STYLE iOS
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) { page ->
                        val challenge = savedChallenges[page]

                        SavedChallengeCard(
                            savedChallenge = challenge,
                            onDelete = {
                                challengeToDelete = challenge
                                showDeleteDialog = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Indicateur de page
                    HorizontalPagerIndicator(
                        pagerState = pagerState,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(16.dp)
                    )
                }
            }
        }
    }

    // Dialog suppression
    if (showDeleteDialog && challengeToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer ce défi ?") },
            text = { Text("Cette action est irréversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        challengeToDelete?.let { viewModel.deleteChallenge(it) }
                        showDeleteDialog = false
                        challengeToDelete = null
                    }
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun SavedChallengesHeader(
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Fermer",
                tint = Color.Black
            )
        }

        Text(
            text = stringResource(R.string.saved_challenges_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun EmptyStateSection() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bookmark),
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Aucun défi sauvegardé",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sauvegardez vos défis préférés pour les retrouver facilement ici !",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
```

### 🎴 Composant Carte - DailyChallengeCard

```kotlin
// presentation/components/DailyChallengeCard.kt
@Composable
fun DailyChallengeCard(
    challenge: DailyChallenge,
    onSave: (DailyChallenge) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedChallengesViewModel = hiltViewModel()
) {
    var isAlreadySaved by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(challenge) {
        isAlreadySaved = viewModel.isChallengeAlreadySaved(challenge)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Contenu du défi
            Text(
                text = challenge.getLocalizedText(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Boutons d'action
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 💾 BOUTON SAUVEGARDER
                Button(
                    onClick = {
                        if (!isAlreadySaved) {
                            onSave(challenge)
                            showSaveConfirmation = true
                            isAlreadySaved = true
                        }
                    },
                    enabled = !isAlreadySaved,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAlreadySaved) Color.Gray else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(25.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isAlreadySaved) R.drawable.ic_bookmark_filled
                                else R.drawable.ic_bookmark
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = if (isAlreadySaved) "Déjà sauvegardé" else "Sauvegarder",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 🎉 CONFIRMATION SAUVEGARDE
            AnimatedVisibility(
                visible = showSaveConfirmation,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                LaunchedEffect(Unit) {
                    delay(2000)
                    showSaveConfirmation = false
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = "Défi sauvegardé !",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}
```

---

## 🛠️ Configuration et Setup Android

### 📋 AndroidManifest.xml

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".Love2LoveApplication"
        android:allowBackup="true">

        <!-- Saved Challenges Activity -->
        <activity
            android:name=".presentation.SavedChallengesActivity"
            android:exported="false"
            android:theme="@style/Theme.Love2Love.NoActionBar" />

    </application>
</manifest>
```

### 🎯 Navigation Compose

```kotlin
// navigation/Love2LoveNavigation.kt
@Composable
fun Love2LoveNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(navController)
        }

        composable("daily_challenges") {
            DailyChallengeScreen(navController)
        }

        composable("saved_challenges") {
            SavedChallengesScreen(navController)
        }
    }
}
```

### 💾 Database Room

```kotlin
// data/database/Love2LoveDatabase.kt
@Database(
    entities = [
        SavedChallengeEntity::class,
        // ... autres entités
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class Love2LoveDatabase : RoomDatabase() {

    abstract fun savedChallengeDao(): SavedChallengeDao

    companion object {
        @Volatile
        private var INSTANCE: Love2LoveDatabase? = null

        fun getDatabase(context: Context): Love2LoveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    Love2LoveDatabase::class.java,
                    "love2love_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

### 🔧 Module Hilt

```kotlin
// di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): Love2LoveDatabase {
        return Love2LoveDatabase.getDatabase(context)
    }

    @Provides
    fun provideSavedChallengeDao(database: Love2LoveDatabase): SavedChallengeDao {
        return database.savedChallengeDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return Firebase.firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }
}
```

---

## 🔐 Sécurité Firebase Identique

### 🛡️ Règles Firestore (Identiques iOS)

Les règles Firestore sont **identiques** entre iOS et Android :

```javascript
// firebase/firestore.rules - IDENTIQUE CROSS-PLATFORM
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /savedChallenges/{challengeId} {
      allow read, write: if request.auth != null &&
        request.auth.uid == resource.data.userId;
    }
  }
}
```

### 🚫 Gestion Erreurs Permissions Android

```kotlin
// repositories/SavedChallengesRepository.kt
private fun handleFirestoreError(exception: Exception): String {
    return when (exception) {
        is FirebaseFirestoreException -> {
            when (exception.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Accès refusé. Veuillez vous reconnecter."
                }
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                    "Utilisateur non authentifié."
                }
                FirebaseFirestoreException.Code.UNAVAILABLE -> {
                    "Service temporairement indisponible."
                }
                else -> {
                    "Erreur de synchronisation: ${exception.message}"
                }
            }
        }
        else -> "Erreur inconnue: ${exception.message}"
    }
}
```

---

## 🚀 Résumé Architecture Android

### ✅ Points Clés Implémentés

1. **Repository Pattern** - Abstraction claire données locales/distantes
2. **Cache-First Strategy** - Room Database + Firestore sync
3. **MVVM Architecture** - ViewModel + StateFlow pour réactivité
4. **Jetpack Compose** - UI déclarative moderne
5. **Real-time Sync** - Listeners Firestore automatiques
6. **Sécurité Identique** - Règles Firebase cross-platform
7. **Hilt DI** - Injection dépendances robuste
8. **Error Handling** - Gestion erreurs permissions complète

### 🎯 Avantages Architecture

- **Performance** : Cache local Room + sync arrière-plan
- **UX Fluide** : StateFlow réactif + animations Compose
- **Maintenance** : Séparation claire couches (Data/Domain/Presentation)
- **Scalabilité** : Architecture modulaire extensible
- **Cross-Platform** : Logique métier Firebase identique iOS

---

## 📊 Tests et Validation

### 🧪 Scénarios Tests Critiques

1. **Sauvegarde** : Challenge → Repository → Firestore → Cache local
2. **Suppression** : UI → ViewModel → Repository → Firestore + Room
3. **Sync Temps Réel** : Firestore change → Listener → UI update
4. **Permissions** : Accès refusé → Error handling → UI feedback
5. **Cache Offline** : Pas de réseau → Room data → UI fonctionnelle

Cette architecture garantit des **défis sauvegardés robustes** avec **synchronisation temps réel**, **sécurité Firebase**, et **expérience utilisateur optimisée** sur Android ! 💾🚀
