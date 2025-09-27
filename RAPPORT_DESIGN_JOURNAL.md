# 📔 DESIGN COMPLET - Journal de Souvenirs

## 🎯 Vue d'Ensemble des Pages Journal

1. **JournalView/JournalPageView** - Page principale avec header et liste
2. **EmptyJournalStateView** - État vide avec présentation
3. **JournalListView** - Liste des entrées avec groupes par mois
4. **CreateJournalEntryView** - Formulaire de création d'entrée
5. **JournalEntryDetailView** - Vue détail d'une entrée
6. **JournalMapView** - Carte interactive avec clustering

---

## 📱 1. PAGE PRINCIPALE JOURNAL (`JournalView`)

### 🎨 Design Global

```swift
NavigationView {
    ZStack {
        // Background principal identique à toute l'app
        Color(red: 0.97, green: 0.97, blue: 0.98)  // Gris très clair
            .ignoresSafeArea()

        VStack(spacing: 0) {
            // Header + Contenu
        }
    }
}
```

### 🏷️ Header Navigation

```swift
HStack {
    // Bouton carte (gauche)
    Button(action: { showingMapView = true }) {
        Image(systemName: "map")
            .font(.system(size: 18, weight: .semibold))
            .foregroundColor(.black)
    }

    Spacer()

    // Titre centré
    VStack(spacing: 4) {
        Text("our_journal")  // "Notre journal"
            .font(.system(size: 28, weight: .bold))
            .foregroundColor(.black)
    }

    Spacer()

    // Bouton + (droite)
    Button(action: { handleAddEntryTap() }) {
        Image(systemName: "plus")
            .font(.system(size: 20, weight: .semibold))
            .foregroundColor(.black)
    }
}
.padding(.horizontal, 20)
.padding(.top, 20)
.padding(.bottom, 20)
```

### 📋 Contenu - État Vide (`EmptyJournalStateView`)

```swift
VStack(spacing: 30) {
    // Image principale
    Image("jou")  // 🎯 IMAGE PRINCIPALE DU JOURNAL
        .resizable()
        .aspectRatio(contentMode: .fit)
        .frame(width: 240, height: 240)  // Taille fixe grande

    VStack(spacing: 12) {
        // Titre contextuel (limite atteinte ou vide)
        Text(hasReachedLimit && !isSubscribed ? "limit_reached" : "empty_journal_message")
            .font(.system(size: 22, weight: .medium))
            .foregroundColor(.black)
            .multilineTextAlignment(.center)

        // Description du journal
        Text("journal_description")  // Description complète
            .font(.system(size: 16))
            .foregroundColor(.black.opacity(0.7))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 30)
    }

    // Bouton Créer (style rose moderne, compact)
    Button(action: onCreateEntry) {
        HStack(spacing: 8) {
            Image(systemName: "plus")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)

            Text("create")  // "Créer"
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
        }
        .frame(width: 100, height: 38)  // Bouton compact
        .background(
            RoundedRectangle(cornerRadius: 19)
                .fill(Color(hex: "#FD267A"))  // Rose Love2Love
        )
    }
}
```

---

## 📋 2. LISTE DES ENTRÉES (`JournalListView`)

### 🎨 Structure avec Groupes Mensuels

```swift
ScrollView {
    LazyVStack(spacing: 16) {
        ForEach(sortedMonthGroups, id: \.key) { monthGroup in
            Section {
                // Entrées du mois
                ForEach(monthGroup.value) { entry in
                    JournalEntryCardView(
                        entry: entry,
                        isUserEntry: isUserEntry(entry),
                        isSubscribed: isUserSubscribed
                    ) { selectedEntry = entry }
                }
            } header: {
                // Header de section (mois/année)
                HStack {
                    Text(monthGroup.key)  // "Mars 2024"
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.black)

                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 8)
            }
        }
    }
    .padding(.bottom, 20)  // Espace pour menu du bas
}
```

**Design Headers :**

- **Texte:** `.font(.system(size: 18, weight: .semibold))`
- **Couleur:** `.foregroundColor(.black)`
- **Espacement:** `padding(.top, 20)`, `padding(.bottom, 8)`
- **Format:** "Janvier 2024", "Décembre 2023", etc.

---

## ✏️ 3. CRÉATION D'ENTRÉE (`CreateJournalEntryView`)

### 🎨 Design Global

```swift
ZStack {
    Color(red: 0.97, green: 0.97, blue: 0.98)  // Même fond gris clair
        .ignoresSafeArea(.all)

    VStack(spacing: 0) {
        // Header + Contenu principal + Barre d'outils
    }
}
```

### 🏷️ Header Simple

```swift
HStack {
    // Bouton retour uniquement
    Button(action: { dismiss() }) {
        Image(systemName: "chevron.left")
            .font(.system(size: 20, weight: .medium))
            .foregroundColor(.black)
    }

    Spacer()  // Pas de titre dans le header
}
.padding(.horizontal, 20)
.padding(.top, 60)    // Safe area + espace
.padding(.bottom, 30) // Espace vers le contenu
```

### ✍️ Formulaire Principal

```swift
VStack(spacing: 20) {
    // Titre (champ principal)
    VStack(alignment: .leading, spacing: 8) {
        TextField("memory_title_placeholder", text: $title)  // "Titre de votre souvenir"
            .font(.system(size: 24, weight: .medium))      // Police grande et importante
            .foregroundColor(.black)
            .textFieldStyle(PlainTextFieldStyle())         // Style épuré
    }
    .padding(.horizontal, 20)

    // Informations contextuelles (date + lieu)
    HStack {
        Text(formattedEventDate)  // "15 mars 2024 à 14:30"
            .font(.system(size: 16))
            .foregroundColor(.black.opacity(0.6))         // Gris discret

        // Localisation si sélectionnée
        if let location = selectedLocation {
            Text("• \(location.displayName)")              // "• Paris, France"
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.6))
        }

        Spacer()
    }
    .padding(.horizontal, 20)

    // Description (champ secondaire)
    VStack(alignment: .leading, spacing: 8) {
        TextField("memory_description_placeholder", text: $description, axis: .vertical)
            .font(.system(size: 16))                       // Police normale
            .foregroundColor(.black.opacity(0.7))         // Gris plus foncé que date
            .textFieldStyle(PlainTextFieldStyle())
            .lineLimit(5...10)                            // Expansion automatique
    }
    .padding(.horizontal, 20)

    Spacer()  // Pousser la barre d'outils vers le bas
}
```

### 🛠️ Barre d'Outils Interactive

```swift
HStack {
    // Icônes à gauche (fonctionnalités)
    HStack(spacing: 20) {
        // Icône photo avec preview
        Button(action: { checkPhotoLibraryPermission() }) {
            if let image = selectedImage {
                // Affichage image sélectionnée (60x60)
                ZStack(alignment: .topTrailing) {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: 60, height: 60)
                        .clipShape(RoundedRectangle(cornerRadius: 8))

                    // Bouton X supprimer
                    Button(action: { selectedImage = nil }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                            .background(Color.black.opacity(0.6))
                            .clipShape(Circle())
                    }
                    .offset(x: 8, y: -8)
                }
            } else {
                // Icône photo normale
                Image(systemName: "photo")
                    .font(.system(size: 24))
                    .foregroundColor(.black)
            }
        }

        // Icône calendrier (date)
        Button(action: { showingDatePicker = true }) {
            Image(systemName: "calendar")
                .font(.system(size: 24))
                .foregroundColor(.black)
        }

        // Icône horloge (heure)
        Button(action: { showingTimePicker = true }) {
            Image(systemName: "clock")
                .font(.system(size: 24))
                .foregroundColor(.black)
        }

        // Icône localisation (état adaptatif)
        Button(action: { showingLocationPicker = true }) {
            Image(systemName: selectedLocation != nil ? "location.fill" : "location")
                .font(.system(size: 24))
                .foregroundColor(.black)
        }
    }

    Spacer()

    // Bouton Enregistrer (style Love2Love)
    Button(action: createEntry) {
        HStack(spacing: 8) {
            if isCreating {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(0.8)
            }

            Text(isCreating ? "saving" : "save")  // "Enregistrement..." | "Enregistrer"
                .font(.system(size: 16, weight: .semibold))
        }
        .foregroundColor(.white)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(canSave && !isCreating ? Color(hex: "#FD267A") : Color(hex: "#FD267A").opacity(0.5))
        )
    }
    .disabled(!canSave || isCreating)
}
.padding(.horizontal, 20)
.padding(.bottom, 40)  // Espace sûr en bas
```

---

## 📋 4. VUE DÉTAIL ENTRÉE (`JournalEntryDetailView`)

### 🎨 Design Global

```swift
ZStack(alignment: .top) {
    // Fond identique
    Color(red: 0.97, green: 0.97, blue: 0.98)
        .ignoresSafeArea()

    // Contenu scrollable + Header fixe
}
```

### 📸 Image Principale (Si Présente)

```swift
if let imageURL = entry.imageURL, !imageURL.isEmpty {
    AsyncImageView(
        imageURL: imageURL,
        width: nil,           // Pleine largeur
        height: 250,          // Hauteur fixe importante
        cornerRadius: 16      // Coins arrondis modernes
    )
    .padding(.horizontal, 20)
}
```

### 📋 Carte Principale (Titre + Description)

```swift
VStack(alignment: .leading, spacing: 16) {
    // Titre principal
    Text(entry.title)
        .font(.system(size: 28, weight: .bold))      // Police très grande
        .foregroundColor(.black)

    // Date de l'événement
    Text(entry.formattedEventDate)  // "Mercredi 15 mars 2024"
        .font(.system(size: 16))
        .foregroundColor(.black.opacity(0.6))

    // Description (si présente)
    if !entry.description.isEmpty {
        Divider()
            .background(Color.black.opacity(0.1))
            .padding(.vertical, 4)

        Text(entry.description)
            .font(.system(size: 16))
            .foregroundColor(.black.opacity(0.8))
            .lineSpacing(4)                          // Espacement entre lignes
    }
}
.padding(24)                                        // Padding généreux interne
.background(
    RoundedRectangle(cornerRadius: 20)
        .fill(Color.white)                          // Fond blanc pur
        .shadow(color: Color.black.opacity(0.08), radius: 20, x: 0, y: 8)  // Ombre principale
        .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)   // Ombre secondaire
)
```

### 📋 Carte Métadonnées

```swift
VStack(alignment: .leading, spacing: 12) {
    VStack(spacing: 8) {
        InfoRow(icon: "calendar", title: "event_date", value: formattedEventDate)
        InfoRow(icon: "clock", title: "event_time", value: formattedEventTime)
        InfoRow(icon: "person.circle", title: "created_by", value: entry.authorName)

        if let location = entry.location {
            InfoRow(icon: "location", title: "location", value: location.displayName)
        }
    }
}
.padding(24)
.background(
    RoundedRectangle(cornerRadius: 20)
        .fill(Color.white)
        .shadow(color: Color.black.opacity(0.08), radius: 20, x: 0, y: 8)
        .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
)
```

### 📋 Composant InfoRow

```swift
struct InfoRow: View {
    let icon: String
    let title: String
    let value: String

    var body: some View {
        HStack(spacing: 12) {
            // Icône avec largeur fixe pour alignement
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(.black)
                .frame(width: 20)

            // Titre
            Text(title)
                .font(.system(size: 14))
                .foregroundColor(.black.opacity(0.6))

            Spacer()

            // Valeur
            Text(value)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.black)
        }
    }
}
```

### 🏷️ Header Fixe avec Actions

```swift
HStack {
    // Bouton fermer
    Button(action: { dismiss() }) {
        Image(systemName: "xmark")
            .font(.system(size: 20, weight: .bold))
            .foregroundColor(.white)
            .padding(12)
            .background(Color.black.opacity(0.6))    // Bulle semi-transparente
            .clipShape(Circle())
    }
    .padding(.leading, 20)

    Spacer()

    // Bouton supprimer (si auteur)
    if isAuthor {
        Button(action: { showingDeleteAlert = true }) {
            Image(systemName: "trash")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white)
                .padding(12)
                .background(Color.black.opacity(0.6))
                .clipShape(Circle())
        }
        .padding(.trailing, 20)
        .disabled(isDeleting)
    }
}
.padding(.top, 20)  // Espace depuis safe area
```

---

## 🗺️ 5. CARTE INTERACTIVE (`JournalMapView`)

### 🎨 Design Global

```swift
ZStack {
    // Carte en plein écran
    Map(coordinateRegion: $mapRegion, annotationItems: clusters) { cluster in
        MapAnnotation(coordinate: cluster.coordinate) {
            // Annotations personnalisées
        }
    }
    .ignoresSafeArea(.all)  // Plein écran immersif

    // Overlays UI
}
```

### 🏷️ Header Overlay avec Bulles

```swift
VStack {
    HStack {
        if showBackButton {
            // Bouton retour (bulle)
            Button(action: { dismiss() }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.black)
                    .padding(12)
                    .background(Color.white.opacity(0.9))  // Bulle semi-transparente blanche
                    .clipShape(Circle())
                    .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
            }
        }

        Spacer()

        // Statistiques (bulles groupées)
        HStack(spacing: 12) {
            // Événements totaux
            StatBubble(
                icon: "heart.fill",
                value: "\(entriesWithLocation.count)",
                color: Color(hex: "#FD267A")
            )

            // Villes visitées
            StatBubble(
                icon: "building.2.fill",
                value: "\(uniqueCitiesCount)",
                color: Color.blue
            )

            // Pays visités
            StatBubble(
                icon: "globe.europe.africa.fill",
                value: "\(uniqueCountriesCount)",
                color: Color.green
            )
        }
    }
    .padding(.horizontal, 20)
    .padding(.top, 60)  // Safe area

    Spacer()  // Pousser vers le haut
}
```

### 🏷️ Composant StatBubble

```swift
struct StatBubble: View {
    let icon: String
    let value: String
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(color)

            Text(value)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.black)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color.white.opacity(0.9))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
    }
}
```

### 📍 Annotations d'Entrées

```swift
// Entrée avec image
if let imageURL = entry.imageURL, !imageURL.isEmpty {
    CachedMapImageView(imageURL: imageURL, size: 60)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.white, lineWidth: 2)  // Bordure blanche
        )
        .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
} else {
    // Entrée sans image (icône Love2Love)
    RoundedRectangle(cornerRadius: 8)
        .fill(Color(hex: "#FD267A"))               // Rose Love2Love
        .frame(width: 60, height: 60)
        .overlay(
            Image(systemName: "heart.fill")
                .font(.system(size: 24))
                .foregroundColor(.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.white, lineWidth: 2)
        )
        .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
}

// Titre de l'annotation
Text(entry.title)
    .font(.system(size: 12, weight: .semibold))
    .foregroundColor(.black)
    .padding(.horizontal, 8)
    .padding(.vertical, 4)
    .background(Color.white.opacity(0.9))
    .cornerRadius(8)
    .shadow(color: .black.opacity(0.2), radius: 2, x: 0, y: 1)
```

### 📍 Annotations de Clusters

```swift
// Bulle de clustering
Circle()
    .fill(Color(hex: "#FD267A"))
    .frame(width: 50, height: 50)
    .overlay(
        Text("\(cluster.entries.count)")  // Nombre d'entrées
            .font(.system(size: 16, weight: .bold))
            .foregroundColor(.white)
    )
    .overlay(
        Circle()
            .stroke(Color.white, lineWidth: 3)
    )
    .shadow(color: .black.opacity(0.3), radius: 6, x: 0, y: 3)
```

---

## 🎨 Palette Couleurs Complète

### Background Global

```swift
// Toutes les vues utilisent le même fond
Color(red: 0.97, green: 0.97, blue: 0.98)  // RGB(247, 247, 250) - Gris très clair
```

### Textes et UI

```swift
// Textes principaux
.black                                       // Titres, labels importants
.black.opacity(0.8)                         // Texte de description
.black.opacity(0.7)                         // Placeholder description
.black.opacity(0.6)                         // Informations contextuelles (date, lieu, métadonnées)

// Éléments interactifs
Color(hex: "#FD267A")                        // RGB(253, 38, 122) - Rose Love2Love principal
Color(hex: "#FD267A").opacity(0.5)          // Version désactivée
```

### Cartes et Surfaces

```swift
// Cartes de contenu (blanc pur)
Color.white                                  // Fond des cartes de contenu

// Ombres sophistiquées (double ombre)
.shadow(color: Color.black.opacity(0.08), radius: 20, x: 0, y: 8)  // Ombre principale
.shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)   // Ombre secondaire

// Bulles et overlays
Color.white.opacity(0.9)                     // Bulles semi-transparentes
Color.black.opacity(0.6)                     // Boutons overlay sombres
```

### Carte Interactive

```swift
// Annotations sans image
Color(hex: "#FD267A")                        // Fond rose Love2Love
Color.white                                  // Bordures et texte sur annotations
Color.blue                                   // Statistique villes
Color.green                                  // Statistique pays
```

---

## 🖼️ Images et Assets Utilisés

| Composant             | Asset                                                    | Fichier    | Usage                         | Taille    |
| --------------------- | -------------------------------------------------------- | ---------- | ----------------------------- | --------- |
| **État Vide**         | `"jou"`                                                  | `jou.png`  | Image principale journal vide | 240x240pt |
| **Header Navigation** | `"map"`                                                  | SystemIcon | Bouton accès carte            | 18pt      |
| **Header Navigation** | `"plus"`                                                 | SystemIcon | Bouton ajouter entrée         | 20pt      |
| **Création**          | `"chevron.left"`                                         | SystemIcon | Bouton retour                 | 20pt      |
| **Création**          | `"photo"`                                                | SystemIcon | Sélection image               | 24pt      |
| **Création**          | `"calendar"`                                             | SystemIcon | Sélection date                | 24pt      |
| **Création**          | `"clock"`                                                | SystemIcon | Sélection heure               | 24pt      |
| **Création**          | `"location"`/`"location.fill"`                           | SystemIcon | Sélection lieu                | 24pt      |
| **Création**          | `"xmark.circle.fill"`                                    | SystemIcon | Supprimer image sélectionnée  | 20pt      |
| **Détail**            | `"xmark"`                                                | SystemIcon | Fermer détail                 | 20pt      |
| **Détail**            | `"trash"`                                                | SystemIcon | Supprimer entrée              | 20pt      |
| **Détail**            | `"calendar"`, `"clock"`, `"person.circle"`, `"location"` | SystemIcon | Métadonnées                   | 16pt      |
| **Carte**             | `"heart.fill"`                                           | SystemIcon | Annotation sans image + stats | 24pt/12pt |
| **Carte**             | `"building.2.fill"`                                      | SystemIcon | Statistique villes            | 12pt      |
| **Carte**             | `"globe.europe.africa.fill"`                             | SystemIcon | Statistique pays              | 12pt      |

**Localisation des Assets :**

```
Assets.xcassets/
└── jou.imageset/
    └── jou.png           // Image d'état vide du journal
```

---

## 🌐 Keys de Traduction (UI.xcstrings)

### Titres Principaux

```json
"our_journal": {
    "fr": "Notre journal",
    "en": "Our journal",
    "de": "Unser Journal",
    "es": "Nuestro diario"
}
```

### État Vide

```json
"empty_journal_message": {
    "fr": "Votre journal de souvenirs est vide",
    "en": "Your memory journal is empty",
    "de": "Dein Erinnerungsjournal ist leer",
    "es": "Tu diario de recuerdos está vacío"
},

"journal_description": {
    "fr": "Sauvegardez ici vos moments préférés et rapprochez-vous encore plus.\n\nVotre partenaire voit tout ce que vous ajoutez.",
    "en": "Save your favorite moments here and feel even closer.\n\nYour partner sees everything you add.",
    "de": "Speichere hier eure Lieblingsmomente und fühlt euch noch näher.\n\nDein:e Partner:in sieht alles, was du hinzufügst.",
    "es": "Guarda aquí vuestros momentos favoritos y sentíos aún más cerca.\n\nTu pareja ve todo lo que añades."
}
```

### Formulaire de Création

```json
"memory_title_placeholder": {
    "fr": "Titre de votre souvenir",
    "en": "Title of your memory",
    "de": "Titel deiner Erinnerung",
    "es": "Título de tu recuerdo"
},

"memory_description_placeholder": {
    "fr": "Décrivez ce qui a rendu ce moment spécial",
    "en": "Describe what made this moment special",
    "de": "Beschreibe, was diesen Moment besonders gemacht hat",
    "es": "Describe qué hizo especial este momento"
}
```

### Actions et Boutons

```json
"create": {
    "fr": "Créer",
    "en": "Create",
    "de": "Erstellen",
    "es": "Crear"
},

"save": {
    "fr": "Enregistrer",
    "en": "Save",
    "de": "Speichern",
    "es": "Guardar"
},

"saving": {
    "fr": "Enregistrement...",
    "en": "Saving...",
    "de": "Speichern...",
    "es": "Guardando..."
}
```

### Sélecteurs Date/Heure

```json
"choose_date": {
    "fr": "Choisir une date",
    "en": "Choose a date",
    "de": "Datum wählen",
    "es": "Elegir una fecha"
},

"choose_time": {
    "fr": "Choisir une heure",
    "en": "Choose a time",
    "de": "Zeit wählen",
    "es": "Elegir una hora"
},

"cancel": {
    "fr": "Annuler",
    "en": "Cancel",
    "de": "Abbrechen",
    "es": "Cancelar"
},

"ok": {
    "fr": "OK",
    "en": "OK",
    "de": "OK",
    "es": "OK"
}
```

### Métadonnées et Détails

```json
"event_date": {
    "fr": "Date de l'événement",
    "en": "Event date",
    "de": "Ereignisdatum",
    "es": "Fecha del evento"
},

"event_time": {
    "fr": "Heure",
    "en": "Time",
    "de": "Uhrzeit",
    "es": "Hora"
},

"created_by": {
    "fr": "Créé par",
    "en": "Created by",
    "de": "Erstellt von",
    "es": "Creado por"
},

"location": {
    "fr": "Lieu",
    "en": "Location",
    "de": "Standort",
    "es": "Ubicación"
}
```

### Suppression et Sécurité

```json
"irreversible_action": {
    "fr": "Cette action ne peut pas être annulée. Le souvenir sera définitivement supprimé.",
    "en": "This action cannot be undone. The memory will be permanently deleted.",
    "de": "Diese Aktion kann nicht rückgängig gemacht werden. Die Erinnerung wird dauerhaft gelöscht.",
    "es": "Esta acción no se puede deshacer. El recuerdo será eliminado permanentemente."
}
```

### Autorisations Photos

```json
"authorization_required": {
    "fr": "Autorisation requise",
    "en": "Authorization required",
    "de": "Berechtigung erforderlich",
    "es": "Autorización requerida"
},

"gallery_access_required": {
    "fr": "L'accès à la galerie est requis pour ajouter des photos à vos souvenirs.",
    "en": "Gallery access is required to add photos to your memories.",
    "de": "Der Zugriff auf die Galerie ist erforderlich, um Fotos zu deinen Erinnerungen hinzuzufügen.",
    "es": "Se requiere acceso a la galería para agregar fotos a tus recuerdos."
},

"open_settings_button": {
    "fr": "Ouvrir les paramètres",
    "en": "Open settings",
    "de": "Einstellungen öffnen",
    "es": "Abrir configuración"
}
```

### Messages d'Erreur Photos

```json
"no_accessible_photos_title": {
    "fr": "Aucune photo accessible",
    "en": "No accessible photos",
    "de": "Keine zugänglichen Fotos",
    "es": "No hay fotos accesibles"
},

"select_more_photos_settings": {
    "fr": "Sélectionnez plus de photos dans les paramètres de confidentialité pour avoir plus de choix.",
    "en": "Select more photos in privacy settings to have more choices.",
    "de": "Wähle mehr Fotos in den Datenschutzeinstellungen aus, um mehr Auswahlmöglichkeiten zu haben.",
    "es": "Selecciona más fotos en la configuración de privacidad para tener más opciones."
}
```

---

## 📏 Espacements et Dimensions

### Layout Principal

```swift
.padding(.horizontal, 20)        // Marges latérales standard
.padding(.top, 20)              // Header top
.padding(.bottom, 20)           // Header bottom
.padding(.bottom, 100)          // Espace pour menu du bas
```

### État Vide

```swift
VStack(spacing: 30)             // Espacement principal entre éléments
.frame(width: 240, height: 240) // Image principale
VStack(spacing: 12)             // Espacement titre/description
.padding(.horizontal, 30)       // Marges texte description
.frame(width: 100, height: 38)  // Bouton créer compact
.cornerRadius(19)               // Coins bouton (hauteur/2)
```

### Création d'Entrée

```swift
VStack(spacing: 20)             // Espacement formulaire
.padding(.horizontal, 20)       // Marges champs
.padding(.top, 60)              // Header depuis safe area
.padding(.bottom, 30)           // Header vers contenu
.padding(.bottom, 40)           // Barre outils vers bas
.font(.system(size: 24, weight: .medium))  // Titre principal
.font(.system(size: 16))        // Texte secondaire
.lineLimit(5...10)              // Expansion description
.frame(width: 60, height: 60)   // Image preview
.cornerRadius(8)                // Coins image
.offset(x: 8, y: -8)            // Position bouton X
HStack(spacing: 20)             // Espacement icônes toolbar
```

### Vue Détail

```swift
VStack(spacing: 24)             // Espacement principal
Spacer().frame(height: 80)      // Espace header fixe
.frame(height: 250)             // Hauteur image
.cornerRadius(16)               // Coins image
.padding(24)                    // Padding cartes internes
.cornerRadius(20)               // Coins cartes
.shadow(radius: 20, x: 0, y: 8) // Ombre principale
.shadow(radius: 6, x: 0, y: 2)  // Ombre secondaire
VStack(alignment: .leading, spacing: 16)  // Structure carte
VStack(spacing: 8)              // Métadonnées
HStack(spacing: 12)             // InfoRow
.frame(width: 20)               // Largeur icône fixe
.padding(.horizontal, 20)       // Marges contenu
.padding(.bottom, 40)           // Espace sûr bas
```

### Carte Interactive

```swift
.padding(.horizontal, 20)       // Marges header
.padding(.top, 60)              // Header depuis safe area
HStack(spacing: 12)             // Espacement bulles stats
.padding(.horizontal, 12)       // Padding bulles
.padding(.vertical, 8)          // Padding vertical bulles
.cornerRadius(16)               // Coins bulles
.frame(width: 60, height: 60)   // Taille annotations
.frame(width: 50, height: 50)   // Taille clusters
.cornerRadius(8)                // Coins annotations
.shadow(radius: 4, x: 0, y: 2)  // Ombres annotations
```

---

## 🤖 Adaptation Android (Kotlin/Compose)

### 1. Structure Générale

```kotlin
@Composable
fun JournalScreen(
    journalState: JournalState,
    onCreateEntry: () -> Unit = {},
    onViewMap: () -> Unit = {},
    onEntryClick: (JournalEntry) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))  // RGB(247, 247, 250)
    ) {
        when (journalState) {
            is JournalState.Empty -> EmptyJournalContent(onCreateEntry)
            is JournalState.WithEntries -> JournalListContent(journalState.entries)
        }
    }
}
```

### 2. Header Navigation

```kotlin
@Composable
fun JournalHeader(
    onMapClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton carte
        IconButton(onClick = onMapClick) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = "View map",
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }

        // Titre centré
        Text(
            text = stringResource(R.string.our_journal),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        // Bouton ajouter
        IconButton(onClick = onAddClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add entry",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

### 3. État Vide

```kotlin
@Composable
fun EmptyJournalContent(
    onCreateEntry: () -> Unit,
    isSubscribed: Boolean = false,
    hasReachedLimit: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image principale
        Image(
            painter = painterResource(R.drawable.jou),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Titre contextuel
        Text(
            text = if (hasReachedLimit && !isSubscribed) {
                stringResource(R.string.limit_reached)
            } else {
                stringResource(R.string.empty_journal_message)
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Description
        Text(
            text = stringResource(R.string.journal_description),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Bouton créer
        Button(
            onClick = onCreateEntry,
            modifier = Modifier.size(width = 100.dp, height = 38.dp),
            shape = RoundedCornerShape(19.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFFFD267A)
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = stringResource(R.string.create),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
```

### 4. Liste avec Groupes Mensuels

```kotlin
@Composable
fun JournalListContent(
    entries: List<JournalEntry>,
    onEntryClick: (JournalEntry) -> Unit = {}
) {
    val groupedEntries = entries.groupBy { entry ->
        // Grouper par mois/année
        val calendar = Calendar.getInstance()
        calendar.time = entry.eventDate
        "${calendar.get(Calendar.MONTH)} ${calendar.get(Calendar.YEAR)}"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        groupedEntries.forEach { (monthYear, entriesInMonth) ->
            item {
                // Header de section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = monthYear, // Format localisé
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            items(entriesInMonth) { entry ->
                JournalEntryCard(
                    entry = entry,
                    onClick = { onEntryClick(entry) }
                )
            }
        }
    }
}
```

### 5. Création d'Entrée

```kotlin
@Composable
fun CreateJournalEntryScreen(
    onSave: (title: String, description: String, eventDate: Date, image: Bitmap?, location: JournalLocation?) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(Date()) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var selectedLocation by remember { mutableStateOf<JournalLocation?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header simple
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 60.dp, bottom = 30.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Titre principal
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.memory_title_placeholder),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black.copy(alpha = 0.5f)
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Informations contextuelles
                Row {
                    Text(
                        text = formatEventDate(eventDate),
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.6f)
                    )

                    selectedLocation?.let { location ->
                        Text(
                            text = " • ${location.displayName}",
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.memory_description_placeholder),
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 200.dp),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.weight(1f))
            }

            // Barre d'outils
            CreateEntryToolbar(
                selectedImage = selectedImage,
                eventDate = eventDate,
                selectedLocation = selectedLocation,
                canSave = title.isNotBlank(),
                onImageSelect = { selectedImage = it },
                onDateSelect = { eventDate = it },
                onTimeSelect = { eventDate = it },
                onLocationSelect = { selectedLocation = it },
                onSave = {
                    onSave(title.trim(), description.trim(), eventDate, selectedImage, selectedLocation)
                }
            )
        }
    }
}
```

### 6. Vue Détail Sophistiquée

```kotlin
@Composable
fun JournalEntryDetailScreen(
    entry: JournalEntry,
    isAuthor: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))
    ) {
        // Contenu scrollable
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }

            // Image si présente
            entry.imageURL?.let { imageUrl ->
                item {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }

            // Carte principale
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 20.dp,
                        hoveredElevation = 6.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = entry.title,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Text(
                            text = formatEventDate(entry.eventDate),
                            fontSize = 16.sp,
                            color = Color.Black.copy(alpha = 0.6f)
                        )

                        if (entry.description.isNotEmpty()) {
                            Divider(
                                color = Color.Black.copy(alpha = 0.1f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Text(
                                text = entry.description,
                                fontSize = 16.sp,
                                color = Color.Black.copy(alpha = 0.8f),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // Carte métadonnées
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoRow(
                                icon = Icons.Default.Calendar,
                                title = stringResource(R.string.event_date),
                                value = formatEventDate(entry.eventDate)
                            )

                            InfoRow(
                                icon = Icons.Default.AccessTime,
                                title = stringResource(R.string.event_time),
                                value = formatEventTime(entry.eventDate)
                            )

                            InfoRow(
                                icon = Icons.Default.Person,
                                title = stringResource(R.string.created_by),
                                value = entry.authorName
                            )

                            entry.location?.let { location ->
                                InfoRow(
                                    icon = Icons.Default.LocationOn,
                                    title = stringResource(R.string.location),
                                    value = location.displayName
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Header fixe
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Bouton fermer
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Bouton supprimer si auteur
            if (isAuthor) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(16.dp)
        )

        Text(
            text = title,
            fontSize = 14.sp,
            color = Color.Black.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
    }
}
```

### 7. Carte Interactive

```kotlin
@Composable
fun JournalMapScreen(
    entries: List<JournalEntry>,
    onBack: () -> Unit,
    onEntryClick: (JournalEntry) -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // GoogleMap avec annotations personnalisées
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            // Configuration de la carte
        ) {
            // Clusters et annotations d'entrées
            entries.forEach { entry ->
                entry.location?.let { location ->
                    MarkerInfoWindow(
                        state = MarkerState(
                            position = LatLng(
                                location.coordinate.latitude,
                                location.coordinate.longitude
                            )
                        ),
                        onClick = {
                            onEntryClick(entry)
                            true
                        }
                    ) {
                        JournalMapAnnotation(entry = entry)
                    }
                }
            }
        }

        // Header overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 60.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton retour
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(
                            Color.White.copy(alpha = 0.9f),
                            CircleShape
                        )
                        .shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Statistiques
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatBubble(
                        icon = Icons.Default.Favorite,
                        value = "${entries.size}",
                        color = Color(0xFFFD267A)
                    )

                    StatBubble(
                        icon = Icons.Default.LocationCity,
                        value = "${getUniqueCitiesCount(entries)}",
                        color = Color.Blue
                    )

                    StatBubble(
                        icon = Icons.Default.Public,
                        value = "${getUniqueCountriesCount(entries)}",
                        color = Color.Green
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun StatBubble(
    icon: ImageVector,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .background(
                Color.White.copy(alpha = 0.9f),
                RoundedCornerShape(16.dp)
            )
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
fun JournalMapAnnotation(
    entry: JournalEntry
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Annotation visuelle
        if (entry.imageURL != null) {
            AsyncImage(
                model = entry.imageURL,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    .shadow(4.dp, RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        Color(0xFFFD267A),
                        RoundedCornerShape(8.dp)
                    )
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    .shadow(4.dp, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Titre
        Text(
            text = entry.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier
                .background(
                    Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(8.dp)
                )
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
```

### 8. Strings.xml Android

```xml
<resources>
    <!-- Titres principaux -->
    <string name="our_journal">Notre journal</string>

    <!-- État vide -->
    <string name="empty_journal_message">Votre journal de souvenirs est vide</string>
    <string name="journal_description">Sauvegardez ici vos moments préférés et rapprochez-vous encore plus.\n\nVotre partenaire voit tout ce que vous ajoutez.</string>

    <!-- Formulaire création -->
    <string name="memory_title_placeholder">Titre de votre souvenir</string>
    <string name="memory_description_placeholder">Décrivez ce qui a rendu ce moment spécial</string>

    <!-- Actions -->
    <string name="create">Créer</string>
    <string name="save">Enregistrer</string>
    <string name="saving">Enregistrement...</string>

    <!-- Sélecteurs -->
    <string name="choose_date">Choisir une date</string>
    <string name="choose_time">Choisir une heure</string>
    <string name="cancel">Annuler</string>
    <string name="ok">OK</string>

    <!-- Métadonnées -->
    <string name="event_date">Date de l\'événement</string>
    <string name="event_time">Heure</string>
    <string name="created_by">Créé par</string>
    <string name="location">Lieu</string>

    <!-- Suppression -->
    <string name="irreversible_action">Cette action ne peut pas être annulée. Le souvenir sera définitivement supprimé.</string>

    <!-- Autorisations -->
    <string name="authorization_required">Autorisation requise</string>
    <string name="gallery_access_required">L\'accès à la galerie est requis pour ajouter des photos à vos souvenirs.</string>
    <string name="open_settings_button">Ouvrir les paramètres</string>
    <string name="no_accessible_photos_title">Aucune photo accessible</string>
    <string name="select_more_photos_settings">Sélectionnez plus de photos dans les paramètres de confidentialité pour avoir plus de choix.</string>
</resources>
```

---

## 🎯 Points Clés du Design

✅ **Cohérence visuelle** : Même fond gris `Color(red: 0.97, green: 0.97, blue: 0.98)` partout  
✅ **Image spécifique** : `"jou"` pour l'état vide du journal (240x240pt)  
✅ **Design épuré** : Headers simples avec actions contextuelles  
✅ **Formulaire moderne** : Champs sans bordures, police adaptative (24pt titre, 16pt description)  
✅ **Barre d'outils interactive** : Icônes 24pt avec preview image et états adaptatifs  
✅ **Cartes sophistiquées** : Double ombres, coins arrondis 20pt, padding généreux 24pt  
✅ **Carte interactive** : Clustering intelligent, annotations personnalisées, bulles statistiques  
✅ **Groupement mensuel** : Sections automatiques par mois/année  
✅ **Actions sécurisées** : Confirmation suppression avec message détaillé  
✅ **Rose Love2Love** : `Color(hex: "#FD267A")` pour tous les éléments interactifs

Le design du journal maintient la **cohérence Love2Love** avec une approche **minimaliste et fonctionnelle**, mettant l'accent sur la **lisibilité** et l'**expérience utilisateur fluide** ! 📔✨
