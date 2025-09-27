# Rapport : Header Principal - Photos de Profil et Distance Partenaire - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille l'architecture complète du header principal de l'application iOS CoupleApp, incluant l'affichage des photos de profil des deux partenaires, le système d'initiales, le calcul de distance, les tutoriels au clic, l'intégration Firebase, et les recommandations pour l'adaptation Android.

---

## 🏗️ Architecture Générale du Header

```
┌─────────────────────────────────────────────────────────────────┐
│                     COMPOSANT HEADER PRINCIPAL                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  HomeContentView.swift                                          │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  PartnerDistanceView                                        │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │ │
│  │  │UserProfile   │  │   Distance   │  │PartnerProfile│      │ │
│  │  │Image (80dp)  │  │CurvedDashedLn│  │Image (80dp)  │      │ │
│  │  │              │  │   (cliquable)│  │  (cliquable) │      │ │
│  │  │ - Photo/URL  │  │   "km ?" ou  │  │ - Photo/URL  │      │ │
│  │  │ - Initiale   │  │   "15.2 km"  │  │ - Initiale   │      │ │
│  │  │ - Icon gris  │  │              │  │ - "?" si vide│      │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘      │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      CALLBACKS ET NAVIGATION                   │
└─────────────────────────────────────────────────────────────────┘

onPartnerAvatarTap → SheetType.partnerManagement
onDistanceTap      → SheetType.locationPermission OR partnerLocationMessage

┌─────────────────────────────────────────────────────────────────┐
│                      INTÉGRATION FIREBASE                      │
└─────────────────────────────────────────────────────────────────┘

Cloud Functions:
- getPartnerInfo()        → Infos partenaire + profileImageURL
- getPartnerProfileImage() → URL signée temporaire (1h)
- getPartnerLocation()    → Coordonnées GPS du partenaire
- getSignedImageURL()     → URL signée pour toute image
```

---

## 👤 1. Système de Photos de Profil

### 1.1 UserProfileImage - Photo Utilisateur

**Localisation :** `Views/Components/PartnerDistanceView.swift:316-365`

#### Hiérarchie d'Affichage (Ordre de Priorité)

```swift
struct UserProfileImage: View {
    let imageURL: String?
    let userName: String
    let size: CGFloat = 80

    var body: some View {
        ZStack {
            // Effet surbrillance
            Circle()
                .fill(Color.white.opacity(0.35))
                .frame(width: size + 12, height: size + 12)
                .blur(radius: 6)

            // PRIORITÉ 1: Cache local ultra-rapide
            if let cachedImage = UserCacheManager.shared.getCachedProfileImage() {
                Image(uiImage: cachedImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: size, height: size)
                    .clipShape(Circle())

            // PRIORITÉ 2: URL Firebase avec AsyncImageView
            } else if let imageURL = imageURL, !imageURL.isEmpty {
                AsyncImageView(
                    imageURL: imageURL,
                    width: size,
                    height: size,
                    cornerRadius: size / 2
                )

            // PRIORITÉ 3: Initiales colorées
            } else if !userName.isEmpty {
                UserInitialsView(name: userName, size: size)

            // PRIORITÉ 4: Icône générique grise
            } else {
                Circle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: size, height: size)

                Image(systemName: "person.fill")
                    .font(.system(size: size * 0.4))
                    .foregroundColor(.gray)
            }

            // Bordure blanche constante
            Circle()
                .stroke(Color.white, lineWidth: 3)
                .frame(width: size, height: size)
        }
    }
}
```

### 1.2 PartnerProfileImage - Photo Partenaire

**Localisation :** `Views/Components/PartnerDistanceView.swift:367-453`

#### Logique d'Affichage Partenaire

```swift
struct PartnerProfileImage: View {
    let hasPartner: Bool
    let imageURL: String?
    let partnerName: String
    let size: CGFloat = 80

    var body: some View {
        ZStack {
            // Surbrillance différentielle
            Circle()
                .fill(Color.white.opacity(hasPartner ? 0.35 : 0.2))
                .frame(width: size + 12, height: size + 12)
                .blur(radius: 6)

            if hasPartner {
                // PRIORITÉ 1: Cache partenaire
                if let cachedPartnerImage = UserCacheManager.shared.getCachedPartnerImage() {
                    Image(uiImage: cachedPartnerImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: size, height: size)
                        .clipShape(Circle())
                        .onAppear {
                            checkAndUpdatePartnerImageIfNeeded()
                        }

                // PRIORITÉ 2: URL Firebase partenaire
                } else if let imageURL = imageURL, !imageURL.isEmpty {
                    AsyncImageView(
                        imageURL: imageURL,
                        width: size,
                        height: size,
                        cornerRadius: size / 2
                    )
                    .onAppear {
                        downloadAndCachePartnerImageIfNeeded(from: imageURL)
                    }

                // PRIORITÉ 3: Initiales partenaire
                } else if !partnerName.isEmpty {
                    UserInitialsView(name: partnerName, size: size)

                // PRIORITÉ 4: Point d'interrogation
                } else {
                    Circle()
                        .fill(Color.gray.opacity(0.4))
                        .frame(width: size, height: size)

                    Text("?")
                        .font(.system(size: size * 0.4, weight: .bold))
                        .foregroundColor(.white)
                }
            } else {
                // Pas de partenaire → Cadenas + invitation
                Circle()
                    .fill(Color.gray.opacity(0.15))
                    .frame(width: size, height: size)

                Text("🔒")
                    .font(.system(size: size * 0.3))
            }

            // Bordure différentielle
            Circle()
                .stroke(Color.white, lineWidth: hasPartner ? 3 : 2)
                .frame(width: size, height: size)
        }
    }
}
```

---

## 🔤 2. Système d'Initiales - UserInitialsView

**Localisation :** `Views/Components/UserInitialsView.swift`

### 2.1 Génération des Initiales

```swift
struct UserInitialsView: View {
    let name: String
    let size: CGFloat

    // Extraction première lettre majuscule
    private var firstLetter: String {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return String(trimmedName.prefix(1)).uppercased()
    }

    // Couleur basée sur le hash du nom (cohérence)
    private var backgroundColor: Color {
        let seed = name.hash
        let colors: [Color] = [
            Color(hex: "#FD267A"), // Rose principal app
            Color(hex: "#FF69B4"), // Rose vif
            Color(hex: "#F06292"), // Rose clair
            Color(hex: "#E91E63"), // Rose intense
            Color(hex: "#FF1493"), // Rose fuchsia
            Color(hex: "#DA70D6"), // Orchidée rose
            Color(hex: "#FF6B9D"), // Rose doux
            Color(hex: "#E1306C")  // Rose Instagram
        ]

        let index = abs(seed) % colors.count
        return colors[index]
    }

    var body: some View {
        Circle()
            .fill(backgroundColor)
            .frame(width: size, height: size)
            .overlay(
                Text(firstLetter)
                    .font(.system(size: size * 0.4, weight: .semibold))
                    .foregroundColor(.white)
            )
    }
}
```

### 2.2 Exemples d'Initiales

| Nom         | Initiale    | Couleur (Hash)            | Résultat           |
| ----------- | ----------- | ------------------------- | ------------------ |
| `Marie`     | `M`         | Rose principal            | ![M rose](M)       |
| `Jean`      | `J`         | Rose vif                  | ![J rose vif](J)   |
| `Sophie`    | `S`         | Rose clair                | ![S rose clair](S) |
| `""` (vide) | Icône grise | `Color.gray.opacity(0.3)` | ![person.fill](👤) |

---

## 📏 3. Système de Distance - Calcul et Affichage

### 3.1 Calcul Intelligent de la Distance

**Localisation :** `Views/Components/PartnerDistanceView.swift:18-85`

#### Cache Multi-Niveaux et Synchronisation

```swift
struct PartnerDistanceView: View {
    // Cache pour éviter recalculs constants
    @State private var cachedDistance: String = "km ?"
    @State private var lastCalculationTime: Date = Date.distantPast

    // Persistance UserDefaults
    private let lastDistanceKey = "last_known_partner_distance"
    private let lastDistanceUpdateKey = "last_distance_update_time"

    private var partnerDistance: String {
        let now = Date()

        // Cache ultra-rapide - ne recalculer que toutes les 2 secondes
        if now.timeIntervalSince(lastCalculationTime) < 2 &&
           cachedDistance != "km ?" && cachedDistance != "? mi" {
            return cachedDistance
        }

        guard let currentUser = appState.currentUser else {
            return "km ?"
        }

        // SYNCHRONISATION: currentUser.currentLocation avec LocationService
        if let locationServiceLocation = appState.locationService?.currentLocation,
           currentUser.currentLocation != locationServiceLocation {
            var updatedUser = currentUser
            updatedUser.currentLocation = locationServiceLocation
            appState.currentUser = updatedUser
        }

        // Vérifier localisation utilisateur
        guard let currentLocation = currentUser.currentLocation else {
            return "km ?" // Localisation manquante
        }

        // Vérifier partenaire connecté
        guard let partnerId = currentUser.partnerId,
              !partnerId.isEmpty else {
            return "km ?" // Pas de partenaire
        }

        // Vérifier localisation partenaire
        guard let partnerLocation = partnerLocationService.partnerLocation else {
            return "km ?" // Partenaire sans localisation
        }

        // CALCUL DISTANCE avec CoreLocation
        let distance = currentLocation.distance(from: partnerLocation)

        // Formatage selon localisation système
        let currentLanguage: String
        if #available(iOS 16.0, *) {
            currentLanguage = Locale.current.language.languageCode?.identifier ?? "en"
        } else {
            currentLanguage = Locale.current.languageCode ?? "en"
        }

        let formattedDistance: String
        if distance < 1000 { // Moins de 1 km
            if currentLanguage.hasPrefix("en") {
                let feet = Int(distance * 3.28084)
                formattedDistance = "\(feet) ft"
            } else {
                formattedDistance = "\(Int(distance)) m"
            }
        } else { // Plus de 1 km
            if currentLanguage.hasPrefix("en") {
                let miles = distance / 1609.34
                formattedDistance = String(format: "%.1f mi", miles)
            } else {
                let kilometers = distance / 1000
                formattedDistance = String(format: "%.1f km", kilometers)
            }
        }

        // Mise à jour cache et persistance
        cachedDistance = formattedDistance
        lastCalculationTime = now

        UserDefaults.standard.set(formattedDistance, forKey: lastDistanceKey)
        UserDefaults.standard.set(now.timeIntervalSince1970, forKey: lastDistanceUpdateKey)

        return formattedDistance
    }
}
```

### 3.2 Interface Distance Cliquable

```swift
// Distance au centre avec ligne courbe
Button(action: {
    if shouldShowLocationPermissionFlow {
        onDistanceTap(shouldShowPartnerLocationMessage)
    }
}) {
    Text(cachedDistance)
        .font(.system(size: 16, weight: .semibold))
        .foregroundColor(.black)
        .multilineTextAlignment(.center)
        .lineLimit(1)
        .fixedSize(horizontal: true, vertical: false)
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.white.opacity(0.95))
                .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
        )
}
.buttonStyle(PlainButtonStyle())
.allowsHitTesting(shouldShowLocationPermissionFlow) // Contrôle interactivité
```

### 3.3 États d'Affichage Distance

| Condition                     | Affichage                | Cliquable | Action                   |
| ----------------------------- | ------------------------ | --------- | ------------------------ |
| Utilisateur sans localisation | `"km ?"`                 | ✅ Oui    | `locationPermission`     |
| Partenaire sans localisation  | `"km ?"`                 | ✅ Oui    | `partnerLocationMessage` |
| Pas de partenaire             | `"km ?"`                 | ❌ Non    | Aucune                   |
| Distance < 1000m              | `"850 m"` / `"2789 ft"`  | ❌ Non    | Aucune                   |
| Distance ≥ 1000m              | `"15.2 km"` / `"9.4 mi"` | ❌ Non    | Aucune                   |

---

## 📐 4. Ligne Courbe Design - CurvedDashedLine

### 4.1 Géométrie de la Ligne

```swift
struct CurvedDashedLine: Shape {
    let screenWidth: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()

        let startPoint = CGPoint(x: 0, y: rect.midY)
        let endPoint = CGPoint(x: rect.width, y: rect.midY)

        // Point de contrôle pour la courbe (au centre, légèrement vers le bas)
        let controlPoint = CGPoint(
            x: rect.width / 2,
            y: rect.midY + 15 // Courbure de 15 points
        )

        path.move(to: startPoint)
        path.addQuadCurve(to: endPoint, control: controlPoint)

        return path
    }
}
```

### 4.2 Rendu Visual

```swift
CurvedDashedLine(screenWidth: geometry.size.width)
    .stroke(Color.white, style: StrokeStyle(lineWidth: 3, dash: [8, 4]))
    .frame(height: 40)
```

**Style :**

- Couleur : Blanc
- Épaisseur : 3 points
- Tirets : 8 points pleins, 4 points vides
- Hauteur frame : 40 points

---

## 🎯 5. Système de Callbacks et Navigation

### 5.1 Intégration dans HomeContentView

**Localisation :** `Views/Main/HomeContentView.swift:24-35`

```swift
PartnerDistanceView(
    onPartnerAvatarTap: {
        // Ouvre gestion partenaire si pas de partenaire connecté
        activeSheet = .partnerManagement
    },
    onDistanceTap: { showPartnerMessageOnly in
        if showPartnerMessageOnly {
            // Message demandant au partenaire d'activer sa localisation
            activeSheet = .partnerLocationMessage
        } else {
            // Flow permission localisation utilisateur
            activeSheet = .locationPermission
        }
    }
)
```

### 5.2 Logique de Clic Avatar Partenaire

```swift
// Photo de profil du partenaire
Button(action: {
    // Si pas de partenaire connecté, ouvrir la vue de gestion des partenaires
    if !hasConnectedPartner {
        onPartnerAvatarTap?()
    }
    // Si partenaire connecté, pas d'action (pas cliquable)
}) {
    PartnerProfileImage(...)
}
```

**Comportement :**

- **Pas de partenaire** : Clic → `PartnerManagementView` (connexion partenaire)
- **Partenaire connecté** : Pas d'action (photo informative seulement)

### 5.3 Logique de Clic Distance

```swift
private var shouldShowLocationPermissionFlow: Bool {
    // Cliquable si localisation manquante (utilisateur OU partenaire)
    guard let currentUser = appState.currentUser else { return false }

    // Si utilisateur n'a pas de localisation → cliquable
    if currentUser.currentLocation == nil {
        return true
    }

    // Si partenaire connecté mais sans localisation → cliquable
    if let partnerId = currentUser.partnerId,
       !partnerId.isEmpty,
       partnerLocationService.partnerLocation == nil {
        return true
    }

    return false
}

private var shouldShowPartnerLocationMessage: Bool {
    // Message partenaire si utilisateur a localisation mais pas partenaire
    guard let currentUser = appState.currentUser,
          currentUser.currentLocation != nil else {
        return false
    }

    // Partenaire connecté mais sans localisation
    if let partnerId = currentUser.partnerId,
       !partnerId.isEmpty,
       partnerLocationService.partnerLocation == nil {
        return true
    }

    return false
}
```

---

## 📱 6. Système de Tutoriels au Clic

### 6.1 SheetType Navigation System

**Localisation :** `Models/SheetType.swift`

#### Types de Sheets Disponibles

```swift
enum SheetType: Identifiable, Equatable {
    // Navigation principale
    case menu
    case subscription
    case questions(QuestionCategory)
    case favorites
    case journal
    case widgets

    // Tutoriels et permissions
    case locationPermission      // Demande permission localisation
    case locationTutorial        // Tutoriel localisation depuis menu
    case partnerLocationMessage  // Message au partenaire
    case widgetTutorial         // Tutoriel widgets

    // Gestion partenaire
    case partnerManagement      // Connexion/déconnexion partenaire
    case eventsMap             // Carte des événements
    case dailyQuestionPermission // Permissions questions quotidiennes
}
```

### 6.2 LocationPermissionFlow - Tutoriel Localisation

**Navigation :**

- `onDistanceTap(false)` → `SheetType.locationPermission`
- Menu → "Tutoriel localisation" → `SheetType.locationTutorial`

```swift
case .locationPermission:
    LocationPermissionFlow()
        .onAppear {
            print("📍 TabContainer: LocationPermissionFlow apparue dans la sheet")
        }
        .onDisappear {
            print("📍 TabContainer: LocationPermissionFlow disparue - Démarrage LocationService")
            // Démarrer immédiatement les mises à jour de localisation
            appState.locationService?.startLocationUpdatesIfAuthorized()
        }

case .locationTutorial:
    LocationPermissionFlow() // Même vue, contexte différent
        .onAppear {
            print("📍 TabContainer: LocationPermissionFlow apparue depuis le menu")
        }
        .onDisappear {
            // Même callback - cohérence UX
            appState.locationService?.startLocationUpdatesIfAuthorized()
        }
```

### 6.3 PartnerManagementView - Gestion Partenaire

**Navigation :** `onPartnerAvatarTap()` → `SheetType.partnerManagement`

**Fonctionnalités :**

- Générer/afficher code partenaire
- Se connecter avec code partenaire
- Déconnecter partenaire actuel
- Gestion permissions partagées

### 6.4 LocationPartnerMessageView - Message Partenaire

**Navigation :** `onDistanceTap(true)` → `SheetType.partnerLocationMessage`

**Contenu :**

- Message explicatif que le partenaire doit activer sa localisation
- Boutons d'actions (rappel, aide, skip)
- Guide pour envoyer lien d'activation

---

## 🔥 7. Intégration Firebase Backend

### 7.1 Cloud Functions pour Photos de Profil

**Localisation :** `firebase/functions/index.js:2369-2766`

#### getPartnerInfo() - Informations Partenaire

```javascript
exports.getPartnerInfo = functions.https.onCall(async (data, context) => {
  console.log("👥 getPartnerInfo: Début récupération info partenaire");

  const currentUserId = context.auth.uid;
  const { partnerId } = data;

  // Validation sécurité
  if (!partnerId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "ID partenaire requis"
    );
  }

  try {
    // Vérifier utilisateur actuel
    const currentUserDoc = await admin
      .firestore()
      .collection("users")
      .doc(currentUserId)
      .get();

    const currentUserData = currentUserDoc.data();

    // SÉCURITÉ: Vérifier que le partenaire demandé est bien connecté
    if (currentUserData.partnerId !== partnerId) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Vous n'êtes pas autorisé à accéder aux informations de cet utilisateur"
      );
    }

    // Récupérer données partenaire
    const partnerDoc = await admin
      .firestore()
      .collection("users")
      .doc(partnerId)
      .get();

    const partnerData = partnerDoc.data();

    // Retourner infos sécurisées
    const partnerInfo = {
      name: partnerData.name || "Partenaire",
      isSubscribed: partnerData.isSubscribed || false,
      subscriptionType: partnerData.subscriptionType || null,
      subscriptionSharedFrom: partnerData.subscriptionSharedFrom || null,
      profileImageURL: partnerData.profileImageURL || null,
    };

    console.log("✅ getPartnerInfo: Informations récupérées avec succès");
    console.log(
      `✅ Photo profil: ${partnerInfo.profileImageURL ? "Présente" : "Absente"}`
    );

    return {
      success: true,
      partnerInfo: partnerInfo,
    };
  } catch (error) {
    console.error("❌ getPartnerInfo: Erreur:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});
```

#### getPartnerProfileImage() - URL Signée Photo Partenaire

```javascript
exports.getPartnerProfileImage = functions.https.onCall(
  async (data, context) => {
    const currentUserId = context.auth.uid;
    const { partnerId } = data;

    try {
      // Vérification sécurité partenaire (même logique)
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

      // Récupérer URL photo partenaire
      const partnerDoc = await admin
        .firestore()
        .collection("users")
        .doc(partnerId)
        .get();
      const partnerData = partnerDoc.data();
      const profileImageURL = partnerData.profileImageURL;

      if (!profileImageURL) {
        return {
          success: false,
          reason: "NO_PROFILE_IMAGE",
          message: "Aucune photo de profil disponible",
        };
      }

      // Générer URL signée temporaire (1 heure)
      const bucket = admin.storage().bucket();

      // Extraire chemin fichier depuis URL Firebase Storage
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

      const [signedUrl] = await file.getSignedUrl({
        action: "read",
        expires: Date.now() + 60 * 60 * 1000, // 1 heure
      });

      return {
        success: true,
        imageUrl: signedUrl,
        expiresIn: 3600, // 1 heure en secondes
      };
    } catch (error) {
      console.error("❌ getPartnerProfileImage: Erreur:", error);
      throw new functions.https.HttpsError("internal", error.message);
    }
  }
);
```

### 7.2 Cloud Functions pour Localisation

#### getPartnerLocation() - Coordonnées GPS Partenaire

```javascript
exports.getPartnerLocation = functions.https.onCall(async (data, context) => {
  console.log(
    "🌍 getPartnerLocation: Début récupération localisation partenaire"
  );

  const currentUserId = context.auth.uid;
  const { partnerId } = data;

  try {
    // Vérification sécurité (même pattern)
    const currentUserDoc = await admin
      .firestore()
      .collection("users")
      .doc(currentUserId)
      .get();
    const currentUserData = currentUserDoc.data();

    if (currentUserData.partnerId !== partnerId) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Vous n'êtes pas autorisé à accéder à la localisation de cet utilisateur"
      );
    }

    // Récupérer localisation partenaire
    const partnerDoc = await admin
      .firestore()
      .collection("users")
      .doc(partnerId)
      .get();
    const partnerData = partnerDoc.data();
    const currentLocation = partnerData.currentLocation;

    console.log(
      "🌍 Localisation partenaire trouvée:",
      currentLocation ? "OUI" : "NON"
    );

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
  } catch (error) {
    console.error("❌ getPartnerLocation: Erreur:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});
```

### 7.3 Sécurité Firebase Storage

#### getSignedImageURL() - URLs Signées Génériques

```javascript
exports.getSignedImageURL = functions.https.onCall(async (data, context) => {
  const currentUserId = context.auth.uid;
  const { filePath } = data;

  try {
    // Vérifier permissions selon type d'image
    if (filePath.startsWith("profile_images/")) {
      const pathComponents = filePath.split("/");
      const imageOwnerId = pathComponents[1];

      // Permettre accès si propriétaire
      if (imageOwnerId === currentUserId) {
        console.log("✅ getSignedImageURL: Accès autorisé - Propriétaire");
      } else {
        // Vérifier si c'est le partenaire connecté
        const currentUserDoc = await admin
          .firestore()
          .collection("users")
          .doc(currentUserId)
          .get();
        const currentUserData = currentUserDoc.data();

        if (currentUserData.partnerId !== imageOwnerId) {
          throw new functions.https.HttpsError(
            "permission-denied",
            "Accès non autorisé à cette image"
          );
        }

        console.log(
          "✅ getSignedImageURL: Accès autorisé - Image du partenaire"
        );
      }
    }

    // Générer URL signée
    const bucket = admin.storage().bucket();
    const file = bucket.file(filePath);

    const [signedUrl] = await file.getSignedUrl({
      action: "read",
      expires: Date.now() + 60 * 60 * 1000, // 1 heure
    });

    return {
      success: true,
      signedUrl: signedUrl,
      expiresIn: 3600,
    };
  } catch (error) {
    console.error("❌ getSignedImageURL: Erreur:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});
```

---

## 📱 8. Cache et Performance - UserCacheManager

### 8.1 Cache Multi-Niveaux Images

```swift
class UserCacheManager {
    static let shared = UserCacheManager()

    // Cache mémoire ultra-rapide
    private var profileImageCache: UIImage?
    private var partnerImageCache: UIImage?

    // URLs pour vérification changements
    private var cachedProfileImageURL: String?
    private var cachedPartnerImageURL: String?

    // PROFIL UTILISATEUR
    func getCachedProfileImage() -> UIImage? {
        return profileImageCache
    }

    func setCachedProfileImage(_ image: UIImage?, url: String?) {
        profileImageCache = image
        cachedProfileImageURL = url

        // Persistance sur disque (optionnel)
        if let image = image {
            saveImageToDisk(image, key: "user_profile")
        }
    }

    // PROFIL PARTENAIRE
    func getCachedPartnerImage() -> UIImage? {
        return partnerImageCache
    }

    func setCachedPartnerImage(_ image: UIImage?, url: String?) {
        partnerImageCache = image
        cachedPartnerImageURL = url

        if let image = image {
            saveImageToDisk(image, key: "partner_profile")
        }
    }

    // Vérification changement URL
    func hasPartnerImageChanged(newURL: String?) -> Bool {
        return cachedPartnerImageURL != newURL
    }
}
```

### 8.2 Performance et Réactivité

**Stratégie d'Affichage :**

1. **Cache mémoire** → Affichage instantané (0ms)
2. **AsyncImageView** → Téléchargement + mise en cache (200-1000ms)
3. **Initiales** → Génération instantané (1ms)
4. **Icône grise** → Fallback système (1ms)

**Optimisations :**

- Limite recalcul distance : 2 secondes minimum
- Persistance UserDefaults pour dernière distance connue
- Synchronisation automatique currentUser ↔ LocationService
- URLs signées temporaires (1h) pour sécurité + performance

---

## 🤖 9. Adaptation Android - Implémentation Complète

### 9.1 Architecture Android Équivalente

#### Composant Principal - HeaderProfileDistance

```kotlin
@Composable
fun HeaderProfileDistance(
    appState: AppState,
    onPartnerAvatarTap: () -> Unit,
    onDistanceTap: (showPartnerMessageOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val partnerLocationService = LocalPartnerLocationService.current
    val userCacheManager = LocalUserCacheManager.current

    // États pour cache distance
    var cachedDistance by remember { mutableStateOf("km ?") }
    var lastCalculationTime by remember { mutableStateOf(0L) }

    // Récupération données utilisateur
    val currentUser = appState.currentUser
    val hasConnectedPartner = !currentUser?.partnerId.isNullOrEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Photo profil utilisateur
        UserProfileImage(
            imageURL = currentUser?.profileImageURL,
            userName = currentUser?.name ?: "",
            size = 80.dp,
            userCacheManager = userCacheManager
        )

        // Distance avec ligne courbe
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Ligne courbe de fond
            CurvedDashedLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )

            // Distance cliquable au centre
            DistanceButton(
                distance = cachedDistance,
                onClick = { showPartnerMessageOnly ->
                    onDistanceTap(showPartnerMessageOnly)
                },
                isClickable = shouldShowLocationPermissionFlow(
                    currentUser,
                    partnerLocationService
                )
            )
        }

        // Photo profil partenaire
        PartnerProfileImage(
            hasPartner = hasConnectedPartner,
            imageURL = partnerLocationService.partnerProfileImageURL,
            partnerName = partnerLocationService.partnerName ?: "",
            size = 80.dp,
            onClick = if (!hasConnectedPartner) onPartnerAvatarTap else null,
            userCacheManager = userCacheManager
        )
    }

    // Calcul distance en temps réel
    LaunchedEffect(
        currentUser?.currentLocation,
        partnerLocationService.partnerLocation
    ) {
        cachedDistance = calculatePartnerDistance(
            currentUser?.currentLocation,
            partnerLocationService.partnerLocation,
            cachedDistance,
            lastCalculationTime
        ) { newDistance, newTime ->
            cachedDistance = newDistance
            lastCalculationTime = newTime
        }
    }
}
```

### 9.2 UserProfileImage Android

```kotlin
@Composable
fun UserProfileImage(
    imageURL: String?,
    userName: String,
    size: Dp,
    userCacheManager: UserCacheManager,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size + 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Effet surbrillance
        Box(
            modifier = Modifier
                .size(size + 12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f))
                .blur(radius = 6.dp)
        )

        // PRIORITÉ 1: Cache local
        val cachedImage = userCacheManager.getCachedProfileImage()
        if (cachedImage != null) {
            Image(
                bitmap = cachedImage.asImageBitmap(),
                contentDescription = "Photo de profil",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        // PRIORITÉ 2: URL Firebase
        else if (!imageURL.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageURL)
                    .crossfade(true)
                    .build(),
                contentDescription = "Photo de profil",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                onSuccess = { result ->
                    // Mettre en cache après chargement
                    userCacheManager.setCachedProfileImage(
                        result.drawable.toBitmap(),
                        imageURL
                    )
                },
                error = {
                    // Fallback vers initiales si échec
                    UserInitialsView(name = userName, size = size)
                }
            )
        }
        // PRIORITÉ 3: Initiales
        else if (userName.isNotEmpty()) {
            UserInitialsView(name = userName, size = size)
        }
        // PRIORITÉ 4: Icône générique
        else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profil",
                    modifier = Modifier.size(size * 0.4f),
                    tint = Color.Gray
                )
            }
        }

        // Bordure blanche constante
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(3.dp, Color.White, CircleShape)
        )
    }
}
```

### 9.3 UserInitialsView Android

```kotlin
@Composable
fun UserInitialsView(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    // Extraction première lettre
    val firstLetter = name.trim().take(1).uppercase()

    // Couleur basée sur hash du nom
    val backgroundColor = remember(name) {
        val seed = name.hashCode()
        val colors = listOf(
            Color(0xFFFD267A), // Rose principal
            Color(0xFFFF69B4), // Rose vif
            Color(0xFFF06292), // Rose clair
            Color(0xFFE91E63), // Rose intense
            Color(0xFFFF1493), // Rose fuchsia
            Color(0xFFDA70D6), // Orchidée rose
            Color(0xFFFF6B9D), // Rose doux
            Color(0xFFE1306C)  // Rose Instagram
        )

        val index = kotlin.math.abs(seed) % colors.size
        colors[index]
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstLetter,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
```

### 9.4 PartnerProfileImage Android

```kotlin
@Composable
fun PartnerProfileImage(
    hasPartner: Boolean,
    imageURL: String?,
    partnerName: String,
    size: Dp,
    onClick: (() -> Unit)?,
    userCacheManager: UserCacheManager,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier.size(size + 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Effet surbrillance différentiel
        Box(
            modifier = Modifier
                .size(size + 12.dp)
                .clip(CircleShape)
                .background(
                    Color.White.copy(alpha = if (hasPartner) 0.35f else 0.2f)
                )
                .blur(radius = 6.dp)
        )

        if (hasPartner) {
            // PRIORITÉ 1: Cache partenaire
            val cachedPartnerImage = userCacheManager.getCachedPartnerImage()
            if (cachedPartnerImage != null) {
                Image(
                    bitmap = cachedPartnerImage.asImageBitmap(),
                    contentDescription = "Photo partenaire",
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                // Vérifier changement URL en arrière-plan
                LaunchedEffect(imageURL) {
                    if (userCacheManager.hasPartnerImageChanged(imageURL)) {
                        // Télécharger nouvelle image
                        downloadAndCachePartnerImage(imageURL, userCacheManager)
                    }
                }
            }
            // PRIORITÉ 2: URL Firebase partenaire
            else if (!imageURL.isNullOrEmpty()) {
                AsyncImage(
                    model = imageURL,
                    contentDescription = "Photo partenaire",
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    onSuccess = { result ->
                        userCacheManager.setCachedPartnerImage(
                            result.drawable.toBitmap(),
                            imageURL
                        )
                    },
                    error = {
                        if (partnerName.isNotEmpty()) {
                            UserInitialsView(name = partnerName, size = size)
                        } else {
                            QuestionMarkView(size = size)
                        }
                    }
                )
            }
            // PRIORITÉ 3: Initiales partenaire
            else if (partnerName.isNotEmpty()) {
                UserInitialsView(name = partnerName, size = size)
            }
            // PRIORITÉ 4: Point d'interrogation
            else {
                QuestionMarkView(size = size)
            }
        } else {
            // Pas de partenaire → Cadenas
            LockIconView(size = size)
        }

        // Bordure différentielle
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(
                    width = if (hasPartner) 3.dp else 2.dp,
                    color = Color.White,
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun QuestionMarkView(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun LockIconView(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🔒",
            fontSize = (size.value * 0.3f).sp
        )
    }
}
```

### 9.5 CurvedDashedLine Android

```kotlin
@Composable
fun CurvedDashedLine(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Dp = 3.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 4.dp
) {
    Canvas(modifier = modifier) {
        val path = Path()

        val startPoint = Offset(0f, size.height / 2)
        val endPoint = Offset(size.width, size.height / 2)
        val controlPoint = Offset(size.width / 2, size.height / 2 + 15.dp.toPx())

        path.moveTo(startPoint.x, startPoint.y)
        path.quadraticBezierTo(
            controlPoint.x, controlPoint.y,
            endPoint.x, endPoint.y
        )

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(
                        dashLength.toPx(),
                        gapLength.toPx()
                    )
                )
            )
        )
    }
}
```

### 9.6 DistanceButton Android

```kotlin
@Composable
fun DistanceButton(
    distance: String,
    onClick: (Boolean) -> Unit,
    isClickable: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = if (isClickable) null else null, // Pas d'indication si pas cliquable
                enabled = isClickable
            ) {
                // Déterminer si c'est pour partenaire ou utilisateur
                val showPartnerMessageOnly = shouldShowPartnerLocationMessage()
                onClick(showPartnerMessageOnly)
            }
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Text(
                text = distance,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}
```

### 9.7 Calcul Distance Android

```kotlin
fun calculatePartnerDistance(
    currentLocation: UserLocation?,
    partnerLocation: UserLocation?,
    cachedDistance: String,
    lastCalculationTime: Long,
    onUpdate: (String, Long) -> Unit
): String {
    val now = System.currentTimeMillis()

    // Cache ultra-rapide - ne recalculer que toutes les 2 secondes
    if (now - lastCalculationTime < 2000 &&
        cachedDistance != "km ?" && cachedDistance != "? mi") {
        return cachedDistance
    }

    // Vérifier données nécessaires
    if (currentLocation == null || partnerLocation == null) {
        return "km ?"
    }

    // Calcul distance avec Android Location
    val results = FloatArray(1)
    Location.distanceBetween(
        currentLocation.latitude,
        currentLocation.longitude,
        partnerLocation.latitude,
        partnerLocation.longitude,
        results
    )

    val distance = results[0]

    // Formatage selon localisation
    val locale = Locale.getDefault()
    val formattedDistance = if (distance < 1000) { // Moins de 1 km
        if (locale.language == "en") {
            val feet = (distance * 3.28084f).toInt()
            "$feet ft"
        } else {
            "${distance.toInt()} m"
        }
    } else { // Plus de 1 km
        if (locale.language == "en") {
            val miles = distance / 1609.34f
            String.format("%.1f mi", miles)
        } else {
            val kilometers = distance / 1000f
            String.format("%.1f km", kilometers)
        }
    }

    // Mise à jour cache et persistance
    onUpdate(formattedDistance, now)

    // Sauvegarder dans SharedPreferences
    val sharedPrefs = context.getSharedPreferences("distance_cache", Context.MODE_PRIVATE)
    sharedPrefs.edit()
        .putString("last_distance", formattedDistance)
        .putLong("last_update_time", now)
        .apply()

    return formattedDistance
}
```

### 9.8 UserCacheManager Android

```kotlin
class UserCacheManager(private val context: Context) {

    // Cache mémoire
    private var profileImageCache: Bitmap? = null
    private var partnerImageCache: Bitmap? = null

    // URLs pour vérification
    private var cachedProfileImageURL: String? = null
    private var cachedPartnerImageURL: String? = null

    private val sharedPrefs = context.getSharedPreferences("image_cache", Context.MODE_PRIVATE)

    // PROFIL UTILISATEUR
    fun getCachedProfileImage(): Bitmap? {
        if (profileImageCache == null) {
            // Tenter chargement depuis disque
            profileImageCache = loadImageFromDisk("user_profile")
        }
        return profileImageCache
    }

    fun setCachedProfileImage(image: Bitmap?, url: String?) {
        profileImageCache = image
        cachedProfileImageURL = url

        if (image != null) {
            saveImageToDisk(image, "user_profile")
        }

        // Sauvegarder URL pour comparaison
        sharedPrefs.edit()
            .putString("user_profile_url", url)
            .apply()
    }

    // PROFIL PARTENAIRE
    fun getCachedPartnerImage(): Bitmap? {
        if (partnerImageCache == null) {
            partnerImageCache = loadImageFromDisk("partner_profile")
        }
        return partnerImageCache
    }

    fun setCachedPartnerImage(image: Bitmap?, url: String?) {
        partnerImageCache = image
        cachedPartnerImageURL = url

        if (image != null) {
            saveImageToDisk(image, "partner_profile")
        }

        sharedPrefs.edit()
            .putString("partner_profile_url", url)
            .apply()
    }

    fun hasPartnerImageChanged(newURL: String?): Boolean {
        val savedURL = sharedPrefs.getString("partner_profile_url", null)
        return savedURL != newURL
    }

    // PERSISTANCE DISQUE
    private fun saveImageToDisk(bitmap: Bitmap, key: String) {
        try {
            val filename = "${key}_cached.jpg"
            val fileOutputStream = context.openFileOutput(filename, Context.MODE_PRIVATE)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
            fileOutputStream.close()
        } catch (e: Exception) {
            Log.e("UserCacheManager", "Erreur sauvegarde image: ${e.message}")
        }
    }

    private fun loadImageFromDisk(key: String): Bitmap? {
        return try {
            val filename = "${key}_cached.jpg"
            val fileInputStream = context.openFileInput(filename)
            val bitmap = BitmapFactory.decodeStream(fileInputStream)
            fileInputStream.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
```

### 9.9 Firebase Integration Android

```kotlin
// Firebase Cloud Functions client
class FirebaseProfileService {
    private val functions = Firebase.functions

    suspend fun getPartnerInfo(partnerId: String): Result<PartnerInfo> {
        return try {
            val data = hashMapOf("partnerId" to partnerId)
            val result = functions
                .getHttpsCallable("getPartnerInfo")
                .call(data)
                .await()

            val response = result.data as Map<String, Any>
            if (response["success"] == true) {
                val partnerInfo = response["partnerInfo"] as Map<String, Any>
                Result.success(
                    PartnerInfo(
                        name = partnerInfo["name"] as String,
                        isSubscribed = partnerInfo["isSubscribed"] as Boolean,
                        profileImageURL = partnerInfo["profileImageURL"] as String?
                    )
                )
            } else {
                Result.failure(Exception("Échec récupération info partenaire"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPartnerLocation(partnerId: String): Result<UserLocation?> {
        return try {
            val data = hashMapOf("partnerId" to partnerId)
            val result = functions
                .getHttpsCallable("getPartnerLocation")
                .call(data)
                .await()

            val response = result.data as Map<String, Any>
            if (response["success"] == true) {
                val location = response["location"] as Map<String, Any>
                Result.success(
                    UserLocation(
                        latitude = location["latitude"] as Double,
                        longitude = location["longitude"] as Double,
                        address = location["address"] as String?,
                        city = location["city"] as String?,
                        country = location["country"] as String?,
                        lastUpdated = location["lastUpdated"] as Long
                    )
                )
            } else {
                Result.success(null) // Pas de localisation disponible
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPartnerProfileImage(partnerId: String): Result<String?> {
        return try {
            val data = hashMapOf("partnerId" to partnerId)
            val result = functions
                .getHttpsCallable("getPartnerProfileImage")
                .call(data)
                .await()

            val response = result.data as Map<String, Any>
            if (response["success"] == true) {
                Result.success(response["imageUrl"] as String)
            } else {
                Result.success(null) // Pas d'image disponible
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class PartnerInfo(
    val name: String,
    val isSubscribed: Boolean,
    val profileImageURL: String?
)

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val city: String?,
    val country: String?,
    val lastUpdated: Long
)
```

### 9.10 Navigation Android

```kotlin
// Équivalent SheetType Android
sealed class BottomSheetContent {
    object PartnerManagement : BottomSheetContent()
    object LocationPermission : BottomSheetContent()
    object PartnerLocationMessage : BottomSheetContent()
    object LocationTutorial : BottomSheetContent()
    object WidgetTutorial : BottomSheetContent()
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val bottomSheetState = rememberModalBottomSheetState()
    var currentBottomSheet by remember { mutableStateOf<BottomSheetContent?>(null) }

    Column {
        // Header avec callbacks
        HeaderProfileDistance(
            appState = viewModel.appState,
            onPartnerAvatarTap = {
                currentBottomSheet = BottomSheetContent.PartnerManagement
            },
            onDistanceTap = { showPartnerMessageOnly ->
                currentBottomSheet = if (showPartnerMessageOnly) {
                    BottomSheetContent.PartnerLocationMessage
                } else {
                    BottomSheetContent.LocationPermission
                }
            }
        )

        // Contenu principal...
    }

    // Bottom sheets
    currentBottomSheet?.let { sheetContent ->
        ModalBottomSheet(
            onDismissRequest = { currentBottomSheet = null },
            sheetState = bottomSheetState
        ) {
            when (sheetContent) {
                is BottomSheetContent.PartnerManagement -> {
                    PartnerManagementScreen(
                        onDismiss = { currentBottomSheet = null }
                    )
                }

                is BottomSheetContent.LocationPermission -> {
                    LocationPermissionFlow(
                        onDismiss = { currentBottomSheet = null },
                        onCompleted = {
                            // Démarrer localisation
                            viewModel.startLocationService()
                            currentBottomSheet = null
                        }
                    )
                }

                is BottomSheetContent.PartnerLocationMessage -> {
                    LocationPartnerMessageScreen(
                        onDismiss = { currentBottomSheet = null }
                    )
                }

                is BottomSheetContent.LocationTutorial -> {
                    LocationPermissionFlow(
                        onDismiss = { currentBottomSheet = null },
                        isFromMenu = true
                    )
                }
            }
        }
    }
}
```

### 9.11 Configuration Gradle Android

```gradle
// app/build.gradle
dependencies {
    // Core Android
    implementation 'androidx.compose.ui:ui:1.5.4'
    implementation 'androidx.compose.ui:ui-tooling-preview:1.5.4'
    implementation 'androidx.compose.material3:material3:1.1.2'

    // Images
    implementation 'io.coil-kt:coil-compose:2.4.0'

    // Firebase
    implementation 'com.google.firebase:firebase-functions-ktx:20.4.0'
    implementation 'com.google.firebase:firebase-storage-ktx:20.3.0'
    implementation 'com.google.firebase:firebase-firestore-ktx:24.9.1'

    // Location
    implementation 'com.google.android.gms:play-services-location:21.0.1'

    // Navigation
    implementation 'androidx.navigation:navigation-compose:2.7.5'

    // Permissions
    implementation 'com.google.accompanist:accompanist-permissions:0.32.0'
}
```

---

## 📋 Conclusion

Le header principal de CoupleApp iOS présente une architecture sophistiquée et une UX soignée :

### 🎯 **Points Forts du Système :**

- **Photos de profil intelligentes** : Cache 4 niveaux (mémoire, disque, Firebase, initiales)
- **Système d'initiales cohérent** : Couleurs basées sur hash nom, 8 variantes rose
- **Distance temps réel** : Calcul optimisé, cache 2s, formatage localisé, persistance
- **Design visuel premium** : Ligne courbe tirets, surbrillances, bordures différentielles
- **Navigation contextuelle** : Callbacks intelligents selon état (partenaire, localisation)

### 🔧 **Composants Techniques :**

- `PartnerDistanceView` - Composant principal header
- `UserProfileImage` / `PartnerProfileImage` - Gestion photos + initiales
- `UserInitialsView` - Génération initiales colorées
- `CurvedDashedLine` - Ligne design entre avatars
- `UserCacheManager` - Cache multi-niveaux performance

### 🔥 **Intégration Firebase :**

- `getPartnerInfo()` - Informations partenaire sécurisées
- `getPartnerProfileImage()` - URLs signées temporaires (1h)
- `getPartnerLocation()` - Coordonnées GPS avec permissions
- `getSignedImageURL()` - URLs signées génériques sécurisées

### 📱 **Tutoriels Intégrés :**

- **Clic avatar partenaire** → Gestion connexion partenaire
- **Clic distance "km ?"** → Permission localisation OU message partenaire
- **Menu tutoriel** → Flow localisation depuis profil
- **Cohérence UX** : Même flow, contextes différents

### 🤖 **Adaptation Android Complète :**

- **Architecture Compose** équivalente avec `HeaderProfileDistance`
- **Cache intelligent** avec `UserCacheManager` + SharedPreferences
- **Firebase Functions** clients Kotlin avec coroutines
- **Navigation moderne** avec `ModalBottomSheet` + sealed classes
- **Performance optimisée** : Coil pour images, calcul distance optimisé

### ⏱️ **Estimation Développement Android :**

- **Phase 1** : Composants de base + cache (2-3 semaines)
- **Phase 2** : Calcul distance + Firebase integration (2-3 semaines)
- **Phase 3** : Tutoriels + navigation (1-2 semaines)
- **Phase 4** : Polish UI + optimisations (1-2 semaines)

**Total estimé : 6-10 semaines** pour une réplication complète du header iOS vers Android.

L'adaptation Android est **techniquement réalisable** avec Jetpack Compose et offre même des avantages (navigation bottom sheets, performance Coil, coroutines Firebase) tout en conservant l'expérience utilisateur premium de la version iOS.
