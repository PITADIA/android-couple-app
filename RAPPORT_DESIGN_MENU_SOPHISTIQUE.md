# ⚙️ DESIGN COMPLET - Vue Menu Sophistiqué

## 🎯 Vue d'Ensemble des Pages Menu

1. **MenuView/MenuContentView** - Page principale avec scrollview et sections
2. **HeaderSection** - Photo de profil avec effet de surbrillance
3. **AboutMeSection** - Section "À propos de moi" avec ProfileRowView
4. **ApplicationSection** - Section "Application" avec liens externes
5. **ProfileRowView** - Composant de ligne réutilisable
6. **EditNameView/EditRelationshipView** - Vues d'édition modales

---

## 📱 1. PAGE PRINCIPALE MENU (`MenuView`)

### 🎨 Design Global

```swift
NavigationView {
    ZStack {
        // Background principal identique à toute l'app
        Color(red: 0.97, green: 0.97, blue: 0.98)  // Gris très clair
            .ignoresSafeArea(.all)

        ScrollView {
            VStack(spacing: 0) {
                // Header + Sections
            }
        }
    }
}
```

### 📱 Structure ScrollView

```swift
ScrollView {
    VStack(spacing: 0) {
        // Header avec photo de profil
        headerSection

        // Section "À propos de moi"
        aboutMeSection

        // Trait de séparation
        separatorLine

        // Section Application
        applicationSection

        Spacer(minLength: 40)  // Espace final
    }
}
```

---

## 🏷️ 2. HEADER SECTION (`headerSection`)

### 🎨 Photo de Profil Sophistiquée avec Effet Surbrillance

```swift
VStack(spacing: 16) {
    Button(action: { checkPhotoLibraryPermission() }) {
        ZStack {
            // 📍 EFFET SURBRILLANCE IDENTIQUE À PartnerDistanceView
            Circle()
                .fill(Color.white.opacity(0.35))       // Blanc semi-transparent
                .frame(width: 120 + 12, height: 120 + 12)  // 12pt plus grand
                .blur(radius: 6)                       // Flou pour effet halo

            // Photo de profil avec hiérarchie sophistiquée
            if let croppedImage = croppedImage {
                // 1️⃣ PRIORITÉ MAXIMALE : Image croppée récemment
                Image(uiImage: croppedImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 120, height: 120)
                    .clipShape(Circle())
            } else if let cachedImage = UserCacheManager.shared.getCachedProfileImage() {
                // 2️⃣ PRIORITÉ ÉLEVÉE : Image en cache pour affichage instantané
                Image(uiImage: cachedImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 120, height: 120)
                    .clipShape(Circle())
            } else if let imageURL = currentUserImageURL {
                // 3️⃣ PRIORITÉ MOYENNE : Image Firebase avec cache automatique
                AsyncImageView(
                    imageURL: imageURL,
                    width: 120,
                    height: 120,
                    cornerRadius: 60
                )
                .onAppear {
                    downloadAndCacheProfileImageIfNeeded(from: imageURL)
                }
            } else if let profileImage = profileImage {
                // 4️⃣ PRIORITÉ FAIBLE : Image temporaire
                Image(uiImage: profileImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 120, height: 120)
                    .clipShape(Circle())
            } else {
                // 5️⃣ FALLBACK : Initiales ou icône générique
                if !currentUserName.isEmpty && currentUserName != "Non défini" {
                    UserInitialsView(name: currentUserName, size: 120)
                } else {
                    Circle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 120, height: 120)
                        .overlay(
                            Image(systemName: "person.fill")
                                .font(.system(size: 50))
                                .foregroundColor(.gray)
                        )
                }
            }

            // 📍 BORDURE BLANCHE (IDENTIQUE À PartnerDistanceView)
            Circle()
                .stroke(Color.white, lineWidth: 3)
                .frame(width: 120, height: 120)
        }
    }
    .buttonStyle(PlainButtonStyle())
}
.padding(.top, 120)      // Grand espace du haut
.padding(.bottom, 50)    // Espace vers section suivante
```

**Détails Design Header :**

- **Taille photo** : `120x120pt` (grande taille pour importance)
- **Effet surbrillance** : `Color.white.opacity(0.35)` avec `blur(radius: 6)`
- **Bordure** : `Color.white` avec `lineWidth: 3`
- **Fallback intelligent** : Initiales colorées ou icône `"person.fill"`
- **Espacement** : `120pt` top (très spacieux), `50pt` bottom

---

## 👤 3. SECTION "À PROPOS DE MOI" (`aboutMeSection`)

### 🎨 Structure avec Titre et Lignes

```swift
VStack(spacing: 0) {
    // Titre section
    HStack {
        Text("about_me")  // "À propos de moi"
            .font(.system(size: 22, weight: .semibold))
            .foregroundColor(.black)
        Spacer()
    }
    .padding(.horizontal, 20)
    .padding(.bottom, 20)

    // Lignes de profil
    ProfileRowView(
        title: "name".localized,                    // "Nom"
        value: currentUserName,
        showChevron: true,
        action: { showingNameEdit = true }
    )

    ProfileRowView(
        title: "in_relationship_since".localized,  // "En couple depuis"
        value: currentRelationshipStart,
        showChevron: true,
        action: { showingRelationshipEdit = true }
    )

    ProfileRowView(
        title: "partner_code".localized,           // "Code partenaire"
        value: "",
        showChevron: true,
        action: { showingPartnerCode = true }
    )

    ProfileRowView(
        title: "location_tutorial".localized,     // "Tutoriel de localisation"
        value: "",
        showChevron: true,
        action: { /* Navigation tutoriel */ }
    )

    ProfileRowView(
        title: "widgets".localized,               // "Widgets"
        value: "",
        showChevron: true,
        action: { /* Navigation widgets */ }
    )

    ProfileRowView(
        title: "manage_subscription".localized,   // "Gérer son abonnement"
        value: "",
        showChevron: true,
        action: { openSubscriptionSettings() }
    )
}
.padding(.bottom, 30)
```

---

## 📱 4. TRAIT DE SÉPARATION (`separatorLine`)

### 🎨 Design Minimaliste

```swift
Rectangle()
    .fill(Color.gray.opacity(0.3))    // Gris très subtil
    .frame(height: 1)                 // Ligne fine
    .padding(.horizontal, 20)         // Marges latérales
    .padding(.bottom, 30)             // Espace vers section suivante
```

---

## 🔧 5. SECTION APPLICATION (`applicationSection`)

### 🎨 Structure avec Liens Externes

```swift
VStack(spacing: 0) {
    // Titre section
    HStack {
        Text("application")  // "Application"
            .font(.system(size: 22, weight: .semibold))
            .foregroundColor(.black)
        Spacer()
    }
    .padding(.horizontal, 20)
    .padding(.bottom, 20)

    // Lignes d'application
    ProfileRowView(
        title: "contact_us".localized,          // "Contactez-nous"
        value: "",
        showChevron: true,
        action: { openSupportEmail() }         // mailto:contact@love2loveapp.com
    )

    ProfileRowView(
        title: "terms_conditions".localized,   // "Conditions générales"
        value: "",
        showChevron: true,
        action: {
            // Apple EULA standard
            if let url = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/") {
                UIApplication.shared.open(url)
            }
        }
    )

    ProfileRowView(
        title: "privacy_policy".localized,     // "Politique de confidentialité"
        value: "",
        showChevron: true,
        action: {
            // URL localisée selon langue
            let privacyUrl = Locale.preferredLanguages.first?.hasPrefix("fr") == true
                ? "https://love2lovesite.onrender.com"
                : "https://love2lovesite.onrender.com/privacy-policy.html"

            if let url = URL(string: privacyUrl) {
                UIApplication.shared.open(url)
            }
        }
    )

    ProfileRowView(
        title: isDeleting ? "deleting_account" : "delete_account",  // "Supprimer le compte"
        value: "",
        showChevron: false,     // Pas de chevron pour action destructive
        isDestructive: false,   // Pas en rouge (design discret)
        action: { showingDeleteConfirmation = true }
    )
}
.padding(.bottom, 40)
```

---

## 📋 6. COMPOSANT PROFILEROWVIEW (`ProfileRowView`)

### 🎨 Design Ligne Réutilisable

```swift
Button(action: action) {
    HStack {
        // Icône optionnelle (pas utilisée dans menu)
        if let icon = icon {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(Color(hex: "#FD267A"))  // Rose Love2Love
                .frame(width: 20)
        }

        // Titre de la ligne
        Text(title)
            .font(.system(size: 16))
            .foregroundColor(isDestructive ? .red : .black)

        Spacer()

        // Valeur (si présente)
        if !value.isEmpty {
            Text(value)
                .font(.system(size: 16))
                .foregroundColor(.gray)          // Gris pour valeur
        }

        // Chevron de navigation
        if showChevron {
            Image(systemName: "chevron.right")
                .font(.system(size: 14))
                .foregroundColor(.gray)          // Gris subtil
        }
    }
    .padding(.horizontal, 20)                    // Marges latérales standard
    .padding(.vertical, 16)                      // Hauteur touchable confortable
    .frame(maxWidth: .infinity, alignment: .leading)
    .contentShape(Rectangle())                   // Zone tactile complète
}
.buttonStyle(PlainButtonStyle())                 // Pas d'effet visuel bouton
```

**Design ProfileRowView :**

- **Police** : `16pt` system pour uniformité
- **Hauteur** : `16pt` padding vertical = `32pt` hauteur tactile
- **Couleurs** : Noir titre, gris valeur, gris chevron
- **Zone tactile** : Rectangle complet avec `contentShape`
- **Marges** : `20pt` horizontal standard Love2Love

---

## ✏️ 7. VUES D'ÉDITION MODALES

### 📝 EditNameView (Modal Compacte)

```swift
VStack(spacing: 20) {
    // Champ de texte avec background subtil
    TextField("Votre nom", text: $newName)  // Placeholder hardcodé
        .font(.system(size: 16))
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.gray.opacity(0.1))      // Fond gris très clair
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.gray.opacity(0.3), lineWidth: 1)  // Bordure subtile
        )

    // Bouton avec dégradé Love2Love
    Button(action: { onSave(newName); dismiss() }) {
        Text("save")  // "Enregistrer"
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FD267A"),      // Rose Love2Love
                        Color(hex: "#FF655B")       // Rouge-orange
                    ]),
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(25)
    }
    .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    .opacity(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.6 : 1.0)
}
.padding(24)
.background(Color.white)
```

### 📅 EditRelationshipView (Modal DatePicker)

```swift
VStack(spacing: 20) {
    // Sélecteur de date style roue
    DatePicker(
        "",
        selection: $selectedDate,
        displayedComponents: .date
    )
    .datePickerStyle(WheelDatePickerStyle())    // Style roue iOS classique
    .labelsHidden()
    .environment(\.locale, Locale.current)

    // Bouton identique au nom (même dégradé)
    Button(action: {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale.current
        onSave(formatter.string(from: selectedDate))
        dismiss()
    }) {
        Text("save")  // "Enregistrer"
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FD267A"),      // Même dégradé
                        Color(hex: "#FF655B")
                    ]),
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(25)
    }
}
.padding(24)
.background(Color.white)
```

**Configuration Modales :**

- **Nom** : `.presentationDetents([.height(200)])` - Compacte
- **Relation** : `.presentationDetents([.height(350)])` - Plus haute pour DatePicker
- **Drag Indicator** : `.presentationDragIndicator(.visible)` sur les deux

---

## 🛠️ 8. SYSTÈME DE GESTION PHOTOS SOPHISTIQUÉ

### 📸 Gestion Permissions Complète (Identique ProfilePhotoStepView)

```swift
private func checkPhotoLibraryPermission() {
    let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)

    switch status {
    case .authorized:
        // ✅ ACCÈS COMPLET
        showingGalleryPicker = true

    case .limited:
        // ✅ ACCÈS LIMITÉ - Interface personnalisée
        loadLimitedAssets { success in
            DispatchQueue.main.async {
                if success {
                    self.showingLimitedGalleryView = true
                } else {
                    self.showingGalleryPicker = true    // Fallback
                }
            }
        }

    case .notDetermined:
        // ⏳ PREMIÈRE DEMANDE
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
            DispatchQueue.main.async {
                self.checkPhotoLibraryPermission()     // Récursion
            }
        }

    case .denied, .restricted:
        // ❌ ACCÈS REFUSÉ - Alert paramètres
        alertMessage = "photo_access_denied_message_menu"
        showSettingsAlert = true

    @unknown default:
        alertMessage = "photo_access_error_generic"
        showSettingsAlert = true
    }
}
```

### ✂️ Système SwiftyCrop Intégré

```swift
.fullScreenCover(isPresented: $showImageCropper) {
    if let imageToProcess = selectedImage {
        SwiftyCropView(
            imageToCrop: imageToProcess,
            maskShape: .circle,                         // Forme circulaire
            configuration: SwiftyCropConfiguration(
                maxMagnificationScale: 4.0,             // Zoom max 4x
                maskRadius: 150,                        // Rayon masque
                cropImageCircular: true,                // Crop rond
                rotateImage: false,                     // Pas rotation
                rotateImageWithButtons: false,
                zoomSensitivity: 1.0,                   // Sensibilité standard
                texts: SwiftyCropConfiguration.Texts(
                    cancelButton: "Annuler",            // Hardcodé français
                    interactionInstructions: "Ajustez votre photo de profil",
                    saveButton: "Valider"
                )
            )
        ) { resultImage in
            guard let finalImage = resultImage else {
                self.showImageCropper = false
                return
            }
            self.croppedImage = finalImage
            self.profileImage = finalImage
            self.showImageCropper = false
        }
    }
}
```

### 🚀 Upload Stratégie "Cache-First"

```swift
private func uploadProfileImage(_ image: UIImage) {
    // 1️⃣ CACHE LOCAL IMMÉDIAT pour affichage instantané
    UserCacheManager.shared.cacheProfileImage(image)

    // 2️⃣ NETTOYER ÉTATS TEMPORAIRES
    self.croppedImage = nil
    self.profileImage = nil

    print("✅ MenuView: Image mise en cache, affichage immédiat")

    // 3️⃣ UPLOAD FIREBASE EN ARRIÈRE-PLAN (silencieux)
    Task {
        await uploadToFirebaseInBackground(image)
    }
}

private func uploadToFirebaseInBackground(_ image: UIImage) async {
    // Upload sans callback UI - Firebase sync seulement
    FirebaseService.shared.updateProfileImage(image) { success, imageURL in
        if success {
            print("✅ MenuView: Upload Firebase terminé avec succès en arrière-plan")
        } else {
            print("❌ MenuView: Upload Firebase échoué en arrière-plan")
        }
    }
}
```

---

## 🎨 Palette Couleurs Complète

### Background Global

```swift
// Toutes les vues utilisent le même fond
Color(red: 0.97, green: 0.97, blue: 0.98)  // RGB(247, 247, 250) - Gris très clair
```

### Textes et Labels

```swift
// Titres sections
.font(.system(size: 22, weight: .semibold))
.foregroundColor(.black)

// Titres lignes ProfileRowView
.font(.system(size: 16))
.foregroundColor(.black)

// Valeurs ProfileRowView
.font(.system(size: 16))
.foregroundColor(.gray)                      // Gris système
```

### Éléments Interactifs

```swift
// Rose Love2Love (icônes et dégradés)
Color(hex: "#FD267A")                        // RGB(253, 38, 122)

// Chevrons navigation
.foregroundColor(.gray)                      // Gris subtil
.font(.system(size: 14))

// Dégradé boutons modales
LinearGradient(colors: [
    Color(hex: "#FD267A"),                   // Rose Love2Love
    Color(hex: "#FF655B")                    // Rouge-orange
])
```

### Photo de Profil

```swift
// Effet surbrillance
Color.white.opacity(0.35)                   // Blanc semi-transparent
.blur(radius: 6)                            // Flou halo

// Bordure
Color.white                                  // Blanc pur
lineWidth: 3                                 // Épaisseur marquée

// Fallback générique
Color.gray.opacity(0.3)                     // Fond gris clair
.foregroundColor(.gray)                      // Icône grise
```

### Trait Séparateur

```swift
Color.gray.opacity(0.3)                     // Gris très subtil
.frame(height: 1)                           // Ligne fine
```

### Vues Modales

```swift
// Background modales
Color.white                                  // Blanc pur

// Champ texte
Color.gray.opacity(0.1)                     // Fond gris très clair
Color.gray.opacity(0.3)                     // Bordure subtile

// États désactivés
.opacity(0.6)                               // 60% transparence
```

---

## 🖼️ Images et Assets Utilisés

| Composant            | Asset             | Fichier     | Usage                     | Taille |
| -------------------- | ----------------- | ----------- | ------------------------- | ------ |
| **Fallback Photo**   | `"person.fill"`   | SystemIcon  | Icône générique profil    | 50pt   |
| **Navigation**       | `"chevron.right"` | SystemIcon  | Flèches ProfileRowView    | 14pt   |
| **UserInitialsView** | Généré            | Text/Circle | Initiales sur fond coloré | 120pt  |

**Note Importante :** Le menu ne contient **aucune image statique** ! Toutes les images sont soit :

- **Dynamiques** : Photos utilisateur via Firebase/Cache
- **Système** : Icônes SF Symbols
- **Générées** : Initiales colorées via `UserInitialsView`

**UserInitialsView (Référencé) :**

```swift
// Composant séparé pour initiales colorées
// Génère automatiquement couleur de fond selon nom
// Affiche première lettre en blanc sur fond coloré
// Taille adaptative selon paramètre (ici 120pt)
```

---

## 🌐 Keys de Traduction (UI.xcstrings)

### Titres Sections

```json
"about_me": {
    "fr": "À propos de moi",
    "en": "About me",
    "de": "Über mich",
    "es": "Acerca de mí"
},

"application": {
    "fr": "Application",
    "en": "Application",
    "de": "Anwendung",
    "es": "Aplicación"
}
```

### Section "À propos de moi"

```json
"name": {
    "fr": "Nom",
    "en": "Name",
    "de": "Name",
    "es": "Nombre"
},

"in_relationship_since": {
    "fr": "En couple depuis",
    "en": "In relationship since",
    "de": "In Beziehung seit",
    "es": "En pareja desde"
},

"partner_code": {
    "fr": "Code partenaire",
    "en": "Partner code",
    "de": "Partner-Code",
    "es": "Código de pareja"
},

"location_tutorial": {
    "fr": "Tutoriel de localisation",
    "en": "Location tutorial",
    "de": "Standort-Tutorial",
    "es": "Tutorial de ubicación"
},

"widgets": {
    "fr": "Widgets",
    "en": "Widgets",
    "de": "Widgets",
    "es": "Widgets"
},

"manage_subscription": {
    "fr": "Gérer son abonnement",
    "en": "Manage subscription",
    "de": "Abonnement verwalten",
    "es": "Gestionar suscripción"
}
```

### Section Application

```json
"contact_us": {
    "fr": "Contactez-nous",
    "en": "Contact us",
    "de": "Kontaktieren Sie uns",
    "es": "Contáctanos"
},

"terms_conditions": {
    "fr": "Conditions générales",
    "en": "Terms & Conditions",
    "de": "Allgemeine Geschäftsbedingungen",
    "es": "Términos y condiciones"
},

"privacy_policy": {
    "fr": "Politique de confidentialité",
    "en": "Privacy policy",
    "de": "Datenschutzrichtlinie",
    "es": "Política de privacidad"
}
```

### Suppression Compte

```json
"delete_account": {
    "fr": "Supprimer le compte",
    "en": "Delete account",
    "de": "Konto löschen",
    "es": "Eliminar cuenta"
},

"delete_account_confirmation": {
    "fr": "Cette action est irréversible. Toutes vos données seront définitivement supprimées.",
    "en": "This action is irreversible. All your data will be permanently deleted.",
    "de": "Diese Aktion ist unwiderruflich. Alle Ihre Daten werden dauerhaft gelöscht.",
    "es": "Esta acción es irreversible. Todos sus datos se eliminarán permanentemente."
},

"deleting_account": {
    "fr": "Suppression du compte...",
    "en": "Deleting account...",
    "de": "Konto wird gelöscht...",
    "es": "Eliminando cuenta..."
}
```

### Actions Générales

```json
"save": {
    "fr": "Enregistrer",
    "en": "Save",
    "de": "Speichern",
    "es": "Guardar"
},

"cancel": {
    "fr": "Annuler",
    "en": "Cancel",
    "de": "Abbrechen",
    "es": "Cancelar"
},

"close": {
    "fr": "Fermer",
    "en": "Close",
    "de": "Schließen",
    "es": "Cerrar"
}
```

### Gestion Photos

```json
"authorization_required": {
    "fr": "Autorisation requise",
    "en": "Authorization required",
    "de": "Berechtigung erforderlich",
    "es": "Autorización requerida"
},

"photo_access_denied_message_menu": {
    "fr": "L'accès aux photos est requis pour modifier votre photo de profil. Veuillez autoriser l'accès dans les réglages.",
    "en": "Photo access is required to change your profile picture. Please allow access in settings.",
    "de": "Der Zugriff auf Fotos ist erforderlich, um Ihr Profilbild zu ändern. Bitte erlauben Sie den Zugriff in den Einstellungen.",
    "es": "Se requiere acceso a las fotos para cambiar tu foto de perfil. Por favor, permite el acceso en la configuración."
},

"photo_access_error_generic": {
    "fr": "Erreur d'accès aux photos. Veuillez réessayer.",
    "en": "Photo access error. Please try again.",
    "de": "Fehler beim Zugriff auf Fotos. Bitte versuchen Sie es erneut.",
    "es": "Error de acceso a fotos. Por favor, inténtalo de nuevo."
},

"open_settings_button": {
    "fr": "Ouvrir les paramètres",
    "en": "Open settings",
    "de": "Einstellungen öffnen",
    "es": "Abrir configuración"
},

"error_image_not_found": {
    "fr": "Image non trouvée",
    "en": "Image not found",
    "de": "Bild nicht gefunden",
    "es": "Imagen no encontrada"
}
```

---

## 📏 Espacements et Dimensions

### Layout Principal

```swift
.padding(.horizontal, 20)        // Marges latérales standard Love2Love
.padding(.top, 120)             // Grand espace header (très spacieux)
.padding(.bottom, 50)           // Espace header vers contenu
.padding(.bottom, 30)           // Espace sections vers suivant
.padding(.bottom, 40)           // Espace final application
Spacer(minLength: 40)           // Espace scroll final
```

### Photo de Profil

```swift
.frame(width: 120, height: 120)          // Taille photo principale
.frame(width: 120 + 12, height: 120 + 12)  // Halo surbrillance (+12pt)
.blur(radius: 6)                         // Flou effet halo
Circle().stroke(lineWidth: 3)            // Bordure épaisse
VStack(spacing: 16)                      // Espacement header interne
```

### ProfileRowView

```swift
.padding(.horizontal, 20)        // Marges latérales
.padding(.vertical, 16)          // Hauteur tactile (32pt total)
.font(.system(size: 16))         // Police uniforme
.font(.system(size: 14))         // Chevrons plus petits
.frame(width: 20)               // Largeur fixe icônes (alignement)
```

### Titres Sections

```swift
.font(.system(size: 22, weight: .semibold))  // Police titres
.padding(.horizontal, 20)                    // Marges latérales
.padding(.bottom, 20)                        // Espace titre vers contenu
```

### Trait Séparateur

```swift
.frame(height: 1)               // Ligne fine
.padding(.horizontal, 20)       // Marges alignées avec contenu
.padding(.bottom, 30)           // Espace vers section suivante
```

### Vues Modales

```swift
.padding(24)                    // Padding généreux modales
.cornerRadius(10)               // Coins arrondis champs
.cornerRadius(25)               // Coins très arrondis boutons
.padding(.vertical, 14)         // Hauteur interne boutons
VStack(spacing: 20)             // Espacement modal éléments
.presentationDetents([.height(200)])    // Hauteur nom
.presentationDetents([.height(350)])    // Hauteur relation
```

---

## 🤖 Adaptation Android (Kotlin/Compose)

### 1. Structure Générale

```kotlin
@Composable
fun MenuScreen(
    userState: UserState,
    onUpdateName: (String) -> Unit = {},
    onUpdateRelationshipDate: (Date) -> Unit = {},
    onNavigateToPartnerCode: () -> Unit = {},
    onNavigateToLocationTutorial: () -> Unit = {},
    onNavigateToWidgets: () -> Unit = {},
    onDeleteAccount: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))  // RGB(247, 247, 250)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header avec photo de profil
            item {
                ProfileHeaderSection(
                    userState = userState,
                    onProfilePhotoClick = { /* Photo picker */ }
                )
            }

            // Section "À propos de moi"
            item {
                AboutMeSection(
                    userState = userState,
                    onUpdateName = onUpdateName,
                    onUpdateRelationshipDate = onUpdateRelationshipDate,
                    onNavigateToPartnerCode = onNavigateToPartnerCode,
                    onNavigateToLocationTutorial = onNavigateToLocationTutorial,
                    onNavigateToWidgets = onNavigateToWidgets
                )
            }

            // Séparateur
            item {
                SeparatorLine()
            }

            // Section Application
            item {
                ApplicationSection(
                    onDeleteAccount = onDeleteAccount
                )
            }

            // Espace final
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
```

### 2. Header Profil avec Effet Surbrillance

```kotlin
@Composable
fun ProfileHeaderSection(
    userState: UserState,
    onProfilePhotoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 120.dp, bottom = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Photo de profil avec effet surbrillance
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Effet halo surbrillance
            Box(
                modifier = Modifier
                    .size(132.dp)  // 120 + 12
                    .background(
                        Color.White.copy(alpha = 0.35f),
                        CircleShape
                    )
                    .blur(6.dp)
            )

            // Photo de profil cliquable
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clickable { onProfilePhotoClick() }
            ) {
                // Hiérarchie d'affichage photo
                when {
                    userState.cachedProfileImage != null -> {
                        AsyncImage(
                            model = userState.cachedProfileImage,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                    userState.profileImageUrl?.isNotEmpty() == true -> {
                        AsyncImage(
                            model = userState.profileImageUrl,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                    userState.name.isNotEmpty() -> {
                        UserInitialsView(
                            name = userState.name,
                            size = 120.dp
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Gray.copy(alpha = 0.3f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Default profile",
                                tint = Color.Gray,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    }
                }

                // Bordure blanche
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            3.dp,
                            Color.White,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
fun UserInitialsView(
    name: String,
    size: Dp
) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: ""
    val backgroundColor = generateColorFromName(name)

    Box(
        modifier = Modifier
            .size(size)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            fontSize = (size.value * 0.4).sp,  // 40% de la taille
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

fun generateColorFromName(name: String): Color {
    val colors = listOf(
        Color(0xFFFD267A),  // Rose Love2Love
        Color(0xFF3498DB),  // Bleu
        Color(0xFF2ECC71),  // Vert
        Color(0xFFF39C12),  // Orange
        Color(0xFF9B59B6),  // Violet
        Color(0xFFE74C3C)   // Rouge
    )
    val hash = name.hashCode()
    return colors[Math.abs(hash) % colors.size]
}
```

### 3. Section "À propos de moi"

```kotlin
@Composable
fun AboutMeSection(
    userState: UserState,
    onUpdateName: (String) -> Unit,
    onUpdateRelationshipDate: (Date) -> Unit,
    onNavigateToPartnerCode: () -> Unit,
    onNavigateToLocationTutorial: () -> Unit,
    onNavigateToWidgets: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 30.dp)
    ) {
        // Titre section
        Text(
            text = stringResource(R.string.about_me),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        )

        // Lignes de profil
        ProfileRow(
            title = stringResource(R.string.name),
            value = userState.name,
            showChevron = true,
            onClick = { onUpdateName(userState.name) }
        )

        ProfileRow(
            title = stringResource(R.string.in_relationship_since),
            value = userState.formattedRelationshipDate,
            showChevron = true,
            onClick = { onUpdateRelationshipDate(userState.relationshipStartDate) }
        )

        ProfileRow(
            title = stringResource(R.string.partner_code),
            value = "",
            showChevron = true,
            onClick = onNavigateToPartnerCode
        )

        ProfileRow(
            title = stringResource(R.string.location_tutorial),
            value = "",
            showChevron = true,
            onClick = onNavigateToLocationTutorial
        )

        ProfileRow(
            title = stringResource(R.string.widgets),
            value = "",
            showChevron = true,
            onClick = onNavigateToWidgets
        )

        ProfileRow(
            title = stringResource(R.string.manage_subscription),
            value = "",
            showChevron = true,
            onClick = { openSubscriptionSettings() }
        )
    }
}
```

### 4. Composant ProfileRow Réutilisable

```kotlin
@Composable
fun ProfileRow(
    title: String,
    value: String,
    showChevron: Boolean = false,
    isDestructive: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icône optionnelle
        icon?.let { iconVector ->
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = Color(0xFFFD267A),  // Rose Love2Love
                modifier = Modifier.size(16.dp)
            )
        }

        // Titre
        Text(
            text = title,
            fontSize = 16.sp,
            color = if (isDestructive) Color.Red else Color.Black,
            modifier = Modifier.weight(1f)
        )

        // Valeur (si présente)
        if (value.isNotEmpty()) {
            Text(
                text = value,
                fontSize = 16.sp,
                color = Color.Gray
            )
        }

        // Chevron
        if (showChevron) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
```

### 5. Ligne Séparatrice

```kotlin
@Composable
fun SeparatorLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, bottom = 30.dp)
            .height(1.dp)
            .background(Color.Gray.copy(alpha = 0.3f))
    )
}
```

### 6. Section Application

```kotlin
@Composable
fun ApplicationSection(
    onDeleteAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 40.dp)
    ) {
        // Titre section
        Text(
            text = stringResource(R.string.application),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        )

        // Lignes d'application
        ProfileRow(
            title = stringResource(R.string.contact_us),
            value = "",
            showChevron = true,
            onClick = { openSupportEmail() }
        )

        ProfileRow(
            title = stringResource(R.string.terms_conditions),
            value = "",
            showChevron = true,
            onClick = { openTermsConditions() }
        )

        ProfileRow(
            title = stringResource(R.string.privacy_policy),
            value = "",
            showChevron = true,
            onClick = { openPrivacyPolicy() }
        )

        ProfileRow(
            title = stringResource(R.string.delete_account),
            value = "",
            showChevron = false,
            isDestructive = false,  // Design discret
            onClick = onDeleteAccount
        )
    }
}
```

### 7. Vues d'Édition Modales

```kotlin
@Composable
fun EditNameDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.name),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("Votre nom") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFD267A),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                    backgroundColor = Color.Gray.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(10.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(newName)
                    onDismiss()
                },
                enabled = newName.trim().isNotEmpty(),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFFD267A),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = Color.Gray
                )
            }
        }
    )
}

@Composable
fun EditRelationshipDateDialog(
    currentDate: Date,
    onSave: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(currentDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.in_relationship_since),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            // DatePicker Android
            AndroidDatePicker(
                selectedDate = selectedDate,
                onDateSelected = { selectedDate = it }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedDate)
                    onDismiss()
                },
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFFD267A),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = Color.Gray
                )
            }
        }
    )
}
```

### 8. Gestion Photos Android

```kotlin
@Composable
fun ProfilePhotoManager(
    onImageSelected: (Uri) -> Unit
) {
    val context = LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Ouvrir galerie
        } else {
            // Afficher message permission refusée
        }
    }

    // Galerie launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImageSelected(it) }
    }

    // Crop launcher avec UCrop
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUri = UCrop.getOutput(result.data!!)
            croppedUri?.let { onImageSelected(it) }
        }
    }

    fun checkAndRequestPhotoPermission() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                galleryLauncher.launch("image/*")
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    // Fonction exposée pour déclenchement
    LaunchedEffect(Unit) {
        // Attendre déclenchement externe
    }
}
```

### 9. Strings.xml Android

```xml
<resources>
    <!-- Sections -->
    <string name="about_me">À propos de moi</string>
    <string name="application">Application</string>

    <!-- À propos de moi -->
    <string name="name">Nom</string>
    <string name="in_relationship_since">En couple depuis</string>
    <string name="partner_code">Code partenaire</string>
    <string name="location_tutorial">Tutoriel de localisation</string>
    <string name="widgets">Widgets</string>
    <string name="manage_subscription">Gérer son abonnement</string>

    <!-- Application -->
    <string name="contact_us">Contactez-nous</string>
    <string name="terms_conditions">Conditions générales</string>
    <string name="privacy_policy">Politique de confidentialité</string>

    <!-- Suppression compte -->
    <string name="delete_account">Supprimer le compte</string>
    <string name="delete_account_confirmation">Cette action est irréversible. Toutes vos données seront définitivement supprimées.</string>
    <string name="deleting_account">Suppression du compte...</string>

    <!-- Actions -->
    <string name="save">Enregistrer</string>
    <string name="cancel">Annuler</string>
    <string name="close">Fermer</string>

    <!-- Permissions -->
    <string name="authorization_required">Autorisation requise</string>
    <string name="photo_access_denied_message_menu">L\'accès aux photos est requis pour modifier votre photo de profil. Veuillez autoriser l\'accès dans les réglages.</string>
    <string name="photo_access_error_generic">Erreur d\'accès aux photos. Veuillez réessayer.</string>
    <string name="open_settings_button">Ouvrir les paramètres</string>
    <string name="error_image_not_found">Image non trouvée</string>
</resources>
```

---

## 🎯 Points Clés du Design

✅ **Design épuré et fonctionnel** : Lignes simples avec chevrons de navigation  
✅ **Photo profil sophistiquée** : Effet surbrillance, hiérarchie d'affichage, cache-first  
✅ **Initiales colorées** : Fallback élégant avec génération automatique couleur  
✅ **Cohérence Love2Love** : Rose `#FD267A` pour tous éléments interactifs  
✅ **Gestion photos complète** : Permissions, accès limité, SwiftyCrop intégré  
✅ **Upload "cache-first"** : Affichage instantané puis sync Firebase silencieuse  
✅ **Modales optimisées** : Tailles adaptatives avec drag indicator  
✅ **Navigation externe** : Emails, URLs, paramètres iOS natifs  
✅ **Actions destructives discrètes** : Suppression compte sans rouge apparent  
✅ **Localization complète** : URLs et messages selon langue système

Le design du menu présente l'**interface la plus clean** de Love2Love avec une **hiérarchie d'information claire**, une **gestion photo ultra-sophistiquée** et une **intégration native iOS/Android** pour toutes les actions externes ! ⚙️✨

**Fichier :** `RAPPORT_DESIGN_MENU_SOPHISTIQUE.md`
