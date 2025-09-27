# Rapport Technique : Enregistrement d'Événements Journal - CoupleApp

## 📋 Vue d'Ensemble

Ce document détaille le processus complet d'enregistrement d'événements dans le journal de l'application CoupleApp, depuis la saisie utilisateur jusqu'au partage avec le partenaire. Ce rapport servira de base pour la transposition vers la version Android.

## 🔄 Processus Complet d'Enregistrement

### 1. Interface Utilisateur - Saisie des Données

**Fichier:** `Views/Journal/CreateJournalEntryView.swift`

L'utilisateur saisit les informations suivantes :

- **Titre** de l'événement (`title`)
- **Description** détaillée (`description`)
- **Date de l'événement** (`eventDate`)
- **Image optionnelle** (`selectedImage`)
- **Localisation optionnelle** (`selectedLocation`)

```swift
// Validation avant soumission
private var canSave: Bool {
    !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
    !description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
}
```

### 2. Déclenchement de l'Enregistrement

**Méthode:** `CreateJournalEntryView.createEntry()`

Lorsque l'utilisateur clique sur "Enregistrer" :

```swift
private func createEntry() {
    guard canSave else { return }

    isCreating = true

    Task {
        try await journalService.createEntry(
            title: title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: description.trimmingCharacters(in: .whitespacesAndNewlines),
            eventDate: eventDate,
            image: selectedImage,
            location: selectedLocation
        )

        // Analytics Firebase
        Analytics.logEvent("journal_evenement_ajoute", parameters: [
            "type": selectedImage != nil ? "photo" : "texte"
        ])

        await MainActor.run { dismiss() }
    }
}
```

## 🏗️ Architecture des Services

### 3. Service Principal - JournalService

**Fichier:** `Services/JournalService.swift`

Le `JournalService` est le service central qui orchestre tout le processus :

#### 3.1 Vérifications Préliminaires

```swift
func createEntry(...) async throws {
    // 1. Vérifier l'authentification
    guard let currentUser = Auth.auth().currentUser,
          let userData = FirebaseService.shared.currentUser else {
        throw JournalError.userNotAuthenticated
    }

    // 2. Vérifier les limites freemium
    guard let freemiumManager = appState?.freemiumManager else {
        throw JournalError.freemiumCheckFailed
    }

    let userEntriesCount = currentUserEntriesCount
    guard freemiumManager.canAddJournalEntry(currentEntriesCount: userEntriesCount) else {
        throw JournalError.freemiumLimitReached
    }
}
```

#### 3.2 Gestion des Images

Si une image est présente, elle est uploadée vers Firebase Storage :

```swift
// Upload de l'image si présente
if let image = image {
    imageURL = try await uploadImage(image)
}

private func uploadImage(_ image: UIImage) async throws -> String {
    // Conversion JPEG avec compression
    guard let imageData = image.jpegData(compressionQuality: 0.8) else {
        throw JournalError.imageProcessingFailed
    }

    // Chemin structuré : journal_images/{userId}/{fileName}
    let fileName = "\(UUID().uuidString).jpg"
    let imagePath = "journal_images/\(currentUser.uid)/\(fileName)"

    // Upload vers Firebase Storage avec métadonnées
    let storageRef = storage.reference().child(imagePath)
    let metadata = StorageMetadata()
    metadata.contentType = "image/jpeg"

    // Retourne l'URL de téléchargement
    return downloadURL.absoluteString
}
```

#### 3.3 Détermination des Partenaires

**Point Clé pour Android :** La logique de partage automatique

```swift
// Déterminer les partenaires avec qui partager
var partnerIds: [String] = [currentUser.uid] // Toujours inclure l'auteur
if let partnerId = userData.partnerId {
    partnerIds.append(partnerId) // Inclure automatiquement le partenaire
}
```

### 4. Modèle de Données - JournalEntry

**Fichier:** `Models/JournalEntry.swift`

Structure complète de l'entrée journal :

```swift
struct JournalEntry: Codable, Identifiable, Equatable {
    let id: String                    // UUID unique
    var title: String                 // Titre (chiffré)
    var description: String           // Description (chiffrée)
    var eventDate: Date              // Date de l'événement
    var createdAt: Date              // Date de création
    var updatedAt: Date              // Dernière modification
    var authorId: String             // ID Firebase de l'auteur
    var authorName: String           // Nom de l'auteur
    var imageURL: String?            // URL Firebase Storage
    var localImagePath: String?      // Chemin local temporaire
    var isShared: Bool = true        // Toujours partagé avec partenaire
    var partnerIds: [String]         // IDs avec accès (auteur + partenaire)
    var location: JournalLocation?   // Géolocalisation (chiffrée)
}
```

## 🔐 Sécurité et Chiffrement

### 5. Service de Chiffrement - LocationEncryptionService

**Fichier:** `Services/LocationEncryptionService.swift`

#### 5.1 Clé de Chiffrement Persistante

```swift
private static let symmetricKey: SymmetricKey = {
    let keychainKey = "love2love_location_encryption_key"

    // Récupération depuis Keychain ou génération nouvelle clé
    // Utilise AES-256 via CryptoKit
    return SymmetricKey(data: keyData)
}()
```

#### 5.2 Chiffrement des Données Sensibles

Le système chiffre automatiquement :

**Titre et Description :**

```swift
func toDictionary() -> [String: Any] {
    // Chiffrement hybride des métadonnées sensibles
    let encryptedTitleData = LocationEncryptionService.processMessageForStorage(title)
    let encryptedDescriptionData = LocationEncryptionService.processMessageForStorage(description)

    dict.merge(encryptedTitleData.mapKeys { key in
        return key == "encryptedText" ? "encryptedTitle" : key.replacingOccurrences(of: "text", with: "title")
    })
}
```

**Localisation :**

```swift
static func writeLocation(_ location: CLLocation) -> [String: Any]? {
    guard let encryptedString = encryptLocation(location) else {
        return nil
    }

    return [
        "encryptedLocation": encryptedString,        // Nouveau format chiffré
        "locationVersion": currentVersion,
        "location": [                                // Format legacy
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude
        ],
        "migrationStatus": "hybrid"
    ]
}
```

#### 5.3 Système Hybride et Rétrocompatibilité

Le système maintient deux formats simultanément :

- **Format chiffré** (v2.0) : Pour la sécurité
- **Format legacy** (v1.0) : Pour la transition et compatibilité

### 6. Sauvegarde dans Firestore

**Point crucial :** Une fois l'objet `JournalEntry` préparé avec chiffrement :

```swift
// Sauvegarder dans Firestore
try await db.collection("journalEntries")
    .document(entry.id)
    .setData(entry.toDictionary())

print("✅ JournalService: Entrée créée avec succès")

// Rafraîchissement forcé pour synchronisation immédiate
await refreshEntries()
```

## 🔒 Règles de Sécurité Firestore (Implicites)

### 7. Contrôle d'Accès

Bien qu'il n'y ait pas de fichier `.rules` explicite trouvé, la sécurité repose sur :

#### 7.1 Filtrage par partnerIds

**Requête de lecture :**

```swift
// Seul l'utilisateur peut voir les entrées où il est dans partnerIds
listener = db.collection("journalEntries")
    .whereField("partnerIds", arrayContains: currentUser.uid)
    .addSnapshotListener { snapshot, error in
        // Traitement des entrées
    }
```

#### 7.2 Vérifications Cloud Functions

**Fichier:** `firebase/functions/index.js`

Les Cloud Functions ajoutent des couches de sécurité :

```javascript
// Vérification de l'authentification
if (!context.auth) {
  throw new functions.https.HttpsError(
    "unauthenticated",
    "Utilisateur non authentifié"
  );
}

// Vérification des relations partenaires
if (
  currentUserData.partnerId !== partnerId ||
  partnerUserData.partnerId !== currentUserId
) {
  throw new functions.https.HttpsError(
    "permission-denied",
    "Les utilisateurs ne sont pas connectés en tant que partenaires"
  );
}
```

#### 7.3 Sécurité des Images

```javascript
// Contrôle d'accès aux images journal
if (filePath.startsWith("journal_images/")) {
  const imageOwnerId = pathComponents[1];

  // Permettre l'accès si propriétaire ou partenaire connecté
  if (imageOwnerId === currentUserId) {
    // Propriétaire autorisé
  } else if (currentUserData.partnerId === imageOwnerId) {
    // Partenaire connecté autorisé
  } else {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Accès non autorisé à cette image"
    );
  }
}
```

## 🔄 Synchronisation avec le Partenaire

### 8. Système de Synchronisation Temps Réel

#### 8.1 Listeners Automatiques

Chaque utilisateur a un listener actif qui surveille les changements :

```swift
private func setupListener() {
    listener = db.collection("journalEntries")
        .whereField("partnerIds", arrayContains: currentUser.uid)
        .addSnapshotListener { [weak self] snapshot, error in
            self?.handleSnapshotUpdate(snapshot: snapshot)
        }
}
```

**Pourquoi ça fonctionne :**

- Firestore déclenche automatiquement le listener quand une nouvelle entrée est créée
- L'entrée contient `partnerIds` avec l'ID du partenaire
- Le partenaire reçoit instantanément la notification

#### 8.2 Synchronisation Lors de Connexion

**Cloud Function :** `syncPartnerJournalEntries`

Quand deux utilisateurs se connectent comme partenaires :

```javascript
async function syncPartnerJournalEntriesInternal(currentUserId, partnerId) {
  // 1. Récupérer toutes les entrées de l'utilisateur actuel
  const currentUserEntriesSnapshot = await admin
    .firestore()
    .collection("journalEntries")
    .where("authorId", "==", currentUserId)
    .get();

  // 2. Récupérer toutes les entrées du partenaire
  const partnerEntriesSnapshot = await admin
    .firestore()
    .collection("journalEntries")
    .where("authorId", "==", partnerId)
    .get();

  const batch = admin.firestore().batch();

  // 3. Mettre à jour les partnerIds pour inclure le nouveau partenaire
  for (const doc of currentUserEntriesSnapshot.docs) {
    const currentPartnerIds = entryData.partnerIds || [];
    if (!currentPartnerIds.includes(partnerId)) {
      const updatedPartnerIds = [...currentPartnerIds, partnerId];
      batch.update(doc.ref, {
        partnerIds: updatedPartnerIds,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }
  }

  // 4. Faire de même pour les entrées du partenaire
  // 5. Exécuter toutes les mises à jour en batch
  await batch.commit();
}
```

### 9. Gestion des Conflits et États

#### 9.1 Gestion d'États UI

```swift
class JournalService: ObservableObject {
    @Published var entries: [JournalEntry] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private func handleSnapshotUpdate(snapshot: QuerySnapshot?) {
        let newEntries = documents.compactMap { JournalEntry(from: $0) }

        DispatchQueue.main.async {
            self.entries = newEntries
            self.entries.sort { $0.eventDate > $1.eventDate }
        }
    }
}
```

#### 9.2 Gestion des Erreurs

```swift
enum JournalError: LocalizedError {
    case userNotAuthenticated
    case notAuthorized
    case imageProcessingFailed
    case networkError
    case freemiumLimitReached
    case freemiumCheckFailed
}
```

## 📊 Audit et Analytiques

### 10. Système d'Audit

**Fichier:** `Services/AuditService.swift`

Chaque action importante est auditée :

```swift
struct AuditEvent {
    let type: AuditEventType
    let userId: String?
    let details: [String: Any]
    let timestamp: Date
    let severity: Severity
}

// Sauvegarde sécurisée dans collection audit_events
db.collection("audit_events").addDocument(data: eventData)
```

### 11. Analytics Firebase

```swift
// Événement enregistré à chaque création d'entrée
Analytics.logEvent("journal_evenement_ajoute", parameters: [
    "type": selectedImage != nil ? "photo" : "texte"
])
```

## 🚀 Points Clés pour la Version Android

### 12. Architecture Recommandée

#### 12.1 Services Essentiels

- **JournalRepository** (équivalent JournalService)
- **EncryptionManager** (équivalent LocationEncryptionService)
- **ImageUploadManager** (gestion Firebase Storage)
- **PartnerSyncManager** (synchronisation partenaires)

#### 12.2 Modèles de Données

```kotlin
data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var description: String,
    var eventDate: Date,
    val createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    val authorId: String,
    val authorName: String,
    var imageURL: String? = null,
    var localImagePath: String? = null,
    val isShared: Boolean = true,
    val partnerIds: List<String>,
    var location: JournalLocation? = null
)
```

#### 12.3 Sécurité Android

- **Android Keystore** pour les clés de chiffrement
- **AES-GCM** via `javax.crypto`
- **Listeners Firestore** avec `addSnapshotListener()`
- **Cloud Functions** identiques (déjà côté serveur)

#### 12.4 Synchronisation Temps Réel

```kotlin
// Équivalent du listener iOS
private fun setupListener() {
    FirebaseAuth.getInstance().currentUser?.let { currentUser ->
        db.collection("journalEntries")
            .whereArrayContains("partnerIds", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                handleSnapshotUpdate(snapshot)
            }
    }
}
```

## 🔍 Flux de Données Complet

### 13. Diagramme de Séquence

```
Utilisateur → CreateJournalEntryView → JournalService → LocationEncryptionService
                                                    ↓
                                             [Chiffrement des données]
                                                    ↓
                                        Firebase Storage (images)
                                                    ↓
                                        Firestore (journalEntries)
                                                    ↓
                                        [Déclenchement des listeners]
                                                    ↓
                                    Partenaire reçoit notification temps réel
                                                    ↓
                                        UI mise à jour automatiquement
```

### 14. Points de Validation

**Pourquoi le système fonctionne :**

1. **Sécurité par Design** : Chiffrement automatique des données sensibles
2. **Partage Automatique** : `partnerIds` inclut automatiquement le partenaire
3. **Temps Réel** : Listeners Firestore pour synchronisation instantanée
4. **Robustesse** : Système hybride avec rétrocompatibilité
5. **Auditabilité** : Logs complets pour traçabilité
6. **Scalabilité** : Cloud Functions pour logique côté serveur

**Pourquoi ça fonctionne avec les règles Firestore :**

- Les requêtes utilisent `arrayContains` sur `partnerIds`
- Seuls les utilisateurs autorisés peuvent accéder aux documents
- Cloud Functions ajoutent des vérifications supplémentaires
- Images protégées par vérification de propriété/partenariat

## 🎯 Conclusion

Le système d'enregistrement d'événements journal est robuste, sécurisé et conçu pour la synchronisation temps réel entre partenaires. L'architecture modulaire facilite la transposition vers Android en maintenant les mêmes principes de sécurité et de fonctionnalité.

---

**Date de génération :** September 26, 2025  
**Version CoupleApp :** iOS Current  
**Destiné pour :** Version Android
