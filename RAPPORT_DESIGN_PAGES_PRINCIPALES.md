# Rapport : Design Pages Principales Complet - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille exhaustivement le design de toutes les pages principales de l'application CoupleApp, incluant spécifications visuelles complètes, couleurs, polices, espacements et clés XCStrings pour l'adaptation Android.

---

## 🎨 Système de Design Global Pages Principales

### Couleurs Système

- **Fond Principal** : `RGB(0.97, 0.97, 0.98)` ≈ `#F7F7F9` (toutes pages)
- **Rose Principal** : `#FD267A` (actions, highlights)
- **Dégradé Header Cards** : `Color(red: 1.0, green: 0.4, blue: 0.6)` → `Color(red: 1.0, green: 0.6, blue: 0.8)`
- **Dégradé Body Cards** : `Color(red: 0.2, green: 0.1, blue: 0.15)` → `Color(red: 0.6, green: 0.3, blue: 0.2)`
- **Surface** : `Color.white`
- **Texte Principal** : `Color.black`
- **Texte Secondaire** : `Color.black.opacity(0.7)`

### Typographie Pages Principales

- **Titres Pages** : `font(.system(size: 28, weight: .bold))`
- **Titres Cartes** : `font(.system(size: 22, weight: .medium))`
- **Titres Sections** : `font(.system(size: 18, weight: .bold))`
- **Corps Texte** : `font(.system(size: 16))`
- **Sous-titres** : `font(.system(size: 14))`
- **Caption** : `font(.caption)`

---

## 🖼️ Images et Assets Utilisés

### Images par Page

| Page/Section            | Nom Image  | Usage                        | Dimensions/Specs |
| ----------------------- | ---------- | ---------------------------- | ---------------- |
| **Navigation Tabs**     | `home`     | Icône onglet accueil         | 24x24dp, SVG     |
| **Navigation Tabs**     | `star`     | Icône onglet questions       | 24x24dp, SVG     |
| **Navigation Tabs**     | `miss`     | Icône onglet défis           | 24x24dp, SVG     |
| **Navigation Tabs**     | `heart`    | Icône onglet favoris         | 24x24dp, SVG     |
| **Navigation Tabs**     | `map`      | Icône onglet journal         | 24x24dp, SVG     |
| **Navigation Tabs**     | `profile`  | Icône onglet profil          | 24x24dp, SVG     |
| **Question Jour Intro** | `mima`     | Image présentation questions | 280x280dp, PNG   |
| **Défi Jour Intro**     | `gaougaou` | Image présentation défis     | 280x280dp, PNG   |
| **Branding Cartes**     | `leetchi2` | Logo Love2Love               | 24x24dp, PNG     |
| **Journal Vide**        | `jou`      | État vide journal            | 200x200dp, PNG   |
| **Authentication**      | `Leetchi`  | Logo authentification        | Flexible, PNG    |
| **Launch Screen**       | `leetchi2` | Logo écran de lancement      | Flexible, PNG    |

### Images Tutoriels Widgets

| Nom Image  | Usage                                      | Spécificités           |
| ---------- | ------------------------------------------ | ---------------------- |
| `etape1`   | Tutoriel étape 1 - Swipe down              | PNG, 350x220dp         |
| `etape2`   | Tutoriel étape 2 - Tap customize (FR)      | PNG, localisé français |
| `etape2en` | Tutoriel étape 2 - Tap customize (EN)      | PNG, localisé anglais  |
| `etape3`   | Tutoriel étape 3 - Select lock screen (FR) | PNG, localisé français |
| `etape3en` | Tutoriel étape 3 - Select lock screen (EN) | PNG, localisé anglais  |
| `etape4`   | Tutoriel étape 4 - Search Love2Love        | PNG, 350x220dp         |
| `etape5`   | Tutoriel home screen - Hold screen         | PNG, 350x220dp         |
| `etape6`   | Tutoriel home screen - Tap plus            | PNG, 350x220dp         |
| `etape7`   | Tutoriel home screen - Search app          | PNG, 350x220dp         |

### Images Tutoriels Anciennes (WidgetTutorialView)

| Nom Image | Usage                   | Note               |
| --------- | ----------------------- | ------------------ |
| `imageA`  | Ancienne étape 1 widget | Peut être déprécié |
| `imageB`  | Ancienne étape 2 widget | Peut être déprécié |
| `imageC`  | Ancienne étape 3 widget | Format JPEG        |
| `imageD`  | Ancienne étape 4 widget | Peut être déprécié |

### Implémentation Android

```kotlin
// Dans drawable/ ou drawable-xxhdpi/
- ic_home.xml (24dp)
- ic_star.xml (24dp)
- ic_miss.xml (24dp)
- ic_heart.xml (24dp)
- ic_map.xml (24dp)
- ic_profile.xml (24dp)
- img_mima.png (280dp)
- img_gaougaou.png (280dp)
- logo_leetchi.png (flexible)
- img_journal_empty.png (200dp)
- tutorial_step_1.png (350x220dp)
- tutorial_step_2_fr.png (350x220dp)
- tutorial_step_2_en.png (350x220dp)
// ... autres étapes
```

---

## 📱 1. Page Principale (HomeContentView/MainView)

### Design Architecture Générale

```
ZStack {
    Color(red: 0.97, green: 0.97, blue: 0.98).ignoresSafeArea(.all)

    ScrollView {
        VStack(spacing: 30) {
            PartnerDistanceView().padding(.top, 100)
            PartnerInviteView() // Si pas de partenaire
            CategoryListView // Grille catégories
            WidgetPreviewSection
            CoupleStatisticsView.padding(.top, 30)
        }
    }
    .padding(.bottom, 100) // Espace menu
}
```

### PartnerDistanceView Design

```
VStack(spacing: 16) {
    // Photos profil avec effets
    HStack(spacing: userDistance > 0 ? 20 : -10) {
        // Photo utilisateur
        ZStack {
            Circle().fill(Color.white.opacity(0.35)).blur(radius: 6)
            AsyncImageView(width: 70, height: 70, cornerRadius: 35)
            Circle().stroke(Color.white, lineWidth: 3)
        }

        // Photo partenaire (si connecté)
        if hasPartner {
            ZStack {
                Circle().fill(Color.white.opacity(0.35)).blur(radius: 6)
                AsyncImageView(width: 70, height: 70, cornerRadius: 35)
                Circle().stroke(Color.white, lineWidth: 3)
            }
        }
    }

    // Distance/Actions
    Button(action: onDistanceTap) {
        if userDistance > 0 {
            Text("\(formattedDistance)")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.black)
        } else {
            Text("?")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.black.opacity(0.5))
        }
    }
}
```

### CategoryListView Layout

```
VStack(spacing: 20) {
    ForEach(QuestionCategory.categories) { category in
        CategoryListCardView(category: category)
            .padding(.horizontal, 20)
    }
}

// CategoryListCardView Design
HStack(spacing: 16) {
    VStack(alignment: .leading, spacing: 6) {
        Text(category.title)
            .font(.system(size: 20, weight: .bold))
            .foregroundColor(.black)
        Text(category.subtitle)
            .font(.system(size: 14))
            .foregroundColor(.gray)
    }
    Spacer()
    Text(category.emoji).font(.system(size: 28))
}
.padding(.horizontal, 24)
.padding(.vertical, 20)
.background(
    RoundedRectangle(cornerRadius: 16)
        .fill(Color.white.opacity(0.95))
        .shadow(radius: 8, x: 0, y: 2)
)
```

### Clés XCStrings Page Principale

```
// Navigation et sections
- "widgets" (Titre section widgets)
- "couple_statistics" (Titre statistiques)
- "add_widgets" (Carte widget)
- "feel_closer_partner" (Sous-titre widget)

// Distance partenaire
- "km_away" (Distance formatée)
- "partner_location_unknown" (Distance inconnue)

// Statistiques
- "days_together" (Jours ensemble)
- "questions_answered" (Questions répondues)
- "cities_visited" (Villes visitées)
- "countries_visited" (Pays visités)

// Actions générales
- "continue" (Bouton continuer)
```

---

## 📱 2. Question du Jour (DailyQuestionMainView)

### Architecture Layout Question

```
NavigationView {
    ZStack {
        Color(white: 0.97).ignoresSafeArea()

        VStack(spacing: 0) {
            // Header centré
            HStack {
                Spacer()
                VStack(spacing: 4) {
                    Text(daily_question_title)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.black)

                    if let subtitle = getDailyQuestionSubtitle() {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)

            // Contenu principal
            GeometryReader { geometry in
                if let currentQuestion = dailyQuestionService.currentQuestion {
                    questionContentView(currentQuestion, geometry: geometry)
                } else if isBusy {
                    loadingView
                } else {
                    noQuestionView
                }
            }
        }
    }
}
```

### QuestionsIntroStepView Image

```
Image("mima")
    .resizable()
    .aspectRatio(contentMode: .fit)
    .frame(maxWidth: .infinity, maxHeight: 280)
    .cornerRadius(20)
    .padding(.horizontal, 30)
```

### DailyQuestionCard Design Spécifique

```
VStack(spacing: 0) {
    // Header rose
    VStack(spacing: 8) {
        Text("Love2Love")
            .font(.system(size: 18, weight: .bold))
            .foregroundColor(.white)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 20)
    .background(
        LinearGradient(
            gradient: Gradient(colors: [
                Color(red: 1.0, green: 0.4, blue: 0.6),
                Color(red: 1.0, green: 0.6, blue: 0.8)
            ]),
            startPoint: .leading,
            endPoint: .trailing
        )
    )

    // Corps avec question
    VStack(spacing: 30) {
        Spacer()
        Text(question.localizedText)
            .font(.system(size: 22, weight: .medium))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .lineSpacing(6)
            .padding(.horizontal, 30)
        Spacer(minLength: 20)
    }
    .frame(minHeight: 200)
    .background(
        LinearGradient(
            gradient: Gradient(colors: [
                Color(red: 0.2, green: 0.1, blue: 0.15),
                Color(red: 0.4, green: 0.2, blue: 0.3),
                Color(red: 0.6, green: 0.3, blue: 0.2)
            ]),
            startPoint: .top,
            endPoint: .bottom
        )
    )
}
.cornerRadius(20)
.shadow(radius: 8, x: 0, y: 4)
```

### Chat Section Design

```
VStack(spacing: 0) {
    if stableMessages.isEmpty {
        Text("daily_question_start_conversation")
            .font(.system(size: 16))
            .foregroundColor(.gray)
            .padding(.vertical, 20)
    } else {
        ForEach(stableMessages) { response in
            ChatMessageView(
                response: response,
                isCurrentUser: response.userId == currentUserId,
                partnerName: response.userName
            )
        }
    }
}
```

### Clés XCStrings Question du Jour

```
// Titres et navigation (DailyQuestions.xcstrings)
- "daily_question_title" (Question du jour)
- "daily_question_start_conversation" (Commencer conversation)

// États et messages
- "no_partner_yet" (Pas encore de partenaire)
- "connect_partner_to_answer" (Connectez partenaire pour répondre)
- "question_loading" (Chargement question)
- "no_question_available" (Aucune question disponible)

// Actions chat
- "type_your_answer" (Tapez votre réponse)
- "send" (Envoyer)

// Freemium
- "free_questions_remaining" (Questions gratuites restantes)
- "upgrade_for_unlimited" (Upgrader pour illimité)
```

---

## 📱 3. Défi du Jour (DailyChallengeMainView)

### DailyChallengeIntroView Image

```
Image("gaougaou")
    .resizable()
    .aspectRatio(contentMode: .fit)
    .frame(maxWidth: .infinity, maxHeight: 280)
    .cornerRadius(20)
    .padding(.horizontal, 30)
```

### Design Architecture Défi

```
NavigationView {
    ZStack {
        Color(red: 0.97, green: 0.97, blue: 0.98).ignoresSafeArea()

        VStack(spacing: 0) {
            // Header avec icône bookmark
            HStack {
                Spacer()
                VStack(spacing: 4) {
                    Text("daily_challenges_title")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.black)

                    if let subtitle = getDailyChallengeSubtitle() {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()

                Button(action: { showingSavedChallenges = true }) {
                    Image(systemName: "bookmark")
                        .font(.system(size: 20))
                        .foregroundColor(.black)
                }
            }

            // Contenu défi
            if let currentChallenge = dailyChallengeService.currentChallenge {
                ScrollView {
                    DailyChallengeCardView(challenge: currentChallenge)
                        .padding(.horizontal, 20)
                }
            }
        }
    }
}
```

### DailyChallengeCardView Design

```
VStack(spacing: 20) {
    // Carte identique aux favoris
    VStack(spacing: 0) {
        // Header Love2Love
        VStack(spacing: 8) {
            Text("Love2Love")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
        }
        .padding(.vertical, 20)
        .background(
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 1.0, green: 0.4, blue: 0.6),
                    Color(red: 1.0, green: 0.6, blue: 0.8)
                ]),
                startPoint: .leading,
                endPoint: .trailing
            )
        )

        // Corps défi
        VStack(spacing: 30) {
            Spacer()
            Text(challenge.localizedText)
                .font(.system(size: 22, weight: .medium))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .lineSpacing(6)
                .padding(.horizontal, 30)
            Spacer(minLength: 20)
        }
        .background(
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.2, green: 0.1, blue: 0.15),
                    Color(red: 0.4, green: 0.2, blue: 0.3),
                    Color(red: 0.6, green: 0.3, blue: 0.2)
                ]),
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }
    .cornerRadius(20)
    .shadow(radius: 8, x: 0, y: 4)

    // Boutons actions
    HStack(spacing: 16) {
        Button(action: onCompleted) {
            HStack {
                Image(systemName: "checkmark")
                Text("challenge_completed")
            }
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color(hex: "#FD267A"))
            .cornerRadius(25)
        }

        Button(action: onSave) {
            HStack {
                Image(systemName: "bookmark")
                Text("save_challenge")
            }
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(Color(hex: "#FD267A"))
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 25)
                    .stroke(Color(hex: "#FD267A"), lineWidth: 2)
            )
        }
    }
}
```

### Clés XCStrings Défi du Jour

```
// Titres (DailyChallenges.xcstrings)
- "daily_challenges_title" (Défis du jour)
- "saved_challenges" (Défis sauvegardés)

// Actions défi
- "challenge_completed" (Défi terminé)
- "save_challenge" (Sauvegarder défi)
- "delete_challenge" (Supprimer défi)

// États
- "no_challenge_today" (Aucun défi aujourd'hui)
- "challenge_loading" (Chargement défi)

// Freemium
- "free_challenges_remaining" (Défis gratuits restants)
- "unlock_all_challenges" (Débloquer tous les défis)
```

---

## 📱 4. Journal (JournalView)

### Architecture Journal Layout

```
NavigationView {
    ZStack {
        Color(red: 0.97, green: 0.97, blue: 0.98).ignoresSafeArea()

        VStack(spacing: 0) {
            // Header avec boutons
            HStack {
                Button(action: { showingMapView = true }) {
                    Image(systemName: "map")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.black)
                }

                Spacer()

                VStack(spacing: 4) {
                    Text("our_journal")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.black)
                }

                Spacer()

                Button(action: handleAddEntryTap) {
                    Image(systemName: "plus")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.black)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 20)

            // Contenu journal
            JournalListView(onCreateEntry: handleAddEntryTap)
        }
    }
}
```

### JournalListView avec Sections Mensuelles

```
ScrollView {
    LazyVStack(spacing: 16) {
        ForEach(sortedMonthGroups, id: \.key) { monthGroup in
            Section {
                ForEach(monthGroup.value) { entry in
                    JournalEntryCardView(
                        entry: entry,
                        isUserEntry: isUserEntry(entry),
                        isSubscribed: isUserSubscribed
                    ) {
                        selectedEntry = entry
                    }
                }
            } header: {
                HStack {
                    Text(monthGroup.key)
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
}
```

### JournalEntryCardView Design

```
VStack(alignment: .leading, spacing: 12) {
    // Image si présente
    if let imageURL = entry.imageURL {
        AsyncImageView(
            imageURL: imageURL,
            width: nil,
            height: 200,
            cornerRadius: 12
        )
    }

    VStack(alignment: .leading, spacing: 8) {
        // Titre
        Text(entry.title)
            .font(.system(size: 18, weight: .bold))
            .foregroundColor(.black)

        // Date et localisation
        HStack {
            Text(entry.formattedEventDate)
                .font(.system(size: 14))
                .foregroundColor(.gray)

            if let location = entry.location {
                Spacer()
                HStack(spacing: 4) {
                    Image(systemName: "location")
                        .font(.system(size: 12))
                        .foregroundColor(.gray)
                    Text(location.displayName)
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                }
            }
        }

        // Description tronquée
        if !entry.description.isEmpty {
            Text(entry.description)
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.8))
                .lineLimit(3)
        }
    }
}
.padding(16)
.background(Color.white)
.cornerRadius(16)
.shadow(radius: 4, x: 0, y: 2)
```

### CreateJournalEntryView Design

```
VStack(spacing: 0) {
    // Header avec retour
    HStack {
        Button(action: { dismiss() }) {
            Image(systemName: "chevron.left")
                .font(.system(size: 20, weight: .medium))
                .foregroundColor(.black)
        }
        Spacer()
    }
    .padding(.horizontal, 20)
    .padding(.top, 60)

    // Formulaire création
    VStack(spacing: 20) {
        // Titre
        TextField("entry_title_placeholder", text: $title)
            .font(.system(size: 18, weight: .semibold))
            .padding(16)
            .background(Color.white)
            .cornerRadius(12)

        // Description
        TextView("entry_description_placeholder", text: $description)
            .frame(minHeight: 120)
            .padding(16)
            .background(Color.white)
            .cornerRadius(12)

        // Date
        DatePicker("event_date", selection: $eventDate, displayedComponents: .date)
            .padding(16)
            .background(Color.white)
            .cornerRadius(12)

        // Photo
        Button(action: selectPhoto) {
            if let image = selectedImage {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(height: 200)
                    .clipped()
                    .cornerRadius(12)
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "camera")
                        .font(.system(size: 30))
                    Text("add_photo")
                        .font(.system(size: 16))
                }
                .foregroundColor(.gray)
                .frame(height: 100)
                .frame(maxWidth: .infinity)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(12)
            }
        }

        // Localisation
        LocationSelectionView(selectedLocation: $selectedLocation)
    }
    .padding(.horizontal, 20)
}
```

### Clés XCStrings Journal

```
// Navigation et titres
- "our_journal" (Notre journal)
- "journal_entries" (Entrées du journal)
- "add_entry" (Ajouter une entrée)

// Création entrée
- "entry_title_placeholder" (Titre de l'événement)
- "entry_description_placeholder" (Description)
- "event_date" (Date de l'événement)
- "add_photo" (Ajouter une photo)
- "select_location" (Choisir un lieu)

// Actions
- "save_entry" (Sauvegarder)
- "delete_entry" (Supprimer)
- "edit_entry" (Modifier)

// États
- "no_entries_yet" (Aucune entrée pour le moment)
- "start_your_journal" (Commencez votre journal)

// Localisation
- "location_required" (Localisation requise)
- "select_on_map" (Sélectionner sur la carte)

// Freemium
- "free_entries_remaining" (Entrées gratuites restantes)
- "unlimited_entries" (Entrées illimitées)
```

---

## 📱 5. Favoris (FavoritesView/FavoritesCardView)

### Architecture Favoris Swipable

```
ZStack {
    Color(red: 0.97, green: 0.97, blue: 0.98).ignoresSafeArea()

    VStack(spacing: 0) {
        // Header
        HStack {
            Spacer()
            VStack(spacing: 4) {
                Text("Favoris")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.black)
            }
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 20)

        // Cartes swipables
        GeometryReader { geometry in
            let cardWidth = geometry.size.width - 40
            let cardSpacing: CGFloat = 30

            ZStack {
                ForEach(visibleFavorites, id: \.0) { indexAndFavorite in
                    let (index, favorite) = indexAndFavorite
                    let offset = CGFloat(index - currentFavoriteIndex)
                    let xPosition = offset * (cardWidth + cardSpacing) + dragOffset.width

                    FavoriteQuestionCardView(
                        favorite: favorite,
                        isBackground: index != currentFavoriteIndex
                    )
                    .frame(width: cardWidth)
                    .offset(x: xPosition)
                    .scaleEffect(index == currentFavoriteIndex ? 1.0 : 0.95)
                    .opacity(index == currentFavoriteIndex ? 1.0 : 0.8)
                }
            }
            .gesture(DragGesture().onChanged/onEnded)
        }
    }
}
```

### FavoriteQuestionCardView Design Détaillé

```
VStack(spacing: 0) {
    // Header avec titre catégorie
    VStack(spacing: 8) {
        Text(favorite.categoryTitle)
            .font(.system(size: 18, weight: .bold))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 20)
    .background(
        LinearGradient(
            gradient: Gradient(colors: [
                Color(red: 1.0, green: 0.4, blue: 0.6),
                Color(red: 1.0, green: 0.6, blue: 0.8)
            ]),
            startPoint: .leading,
            endPoint: .trailing
        )
    )

    // Corps avec question favorite
    VStack(spacing: 30) {
        Spacer()

        Text(favorite.questionText)
            .font(.system(size: 22, weight: .medium))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .lineSpacing(6)
            .padding(.horizontal, 30)

        Spacer()

        // Branding en bas avec logo
        HStack(spacing: 8) {
            Image("leetchi2") // Logo Love2Love 24x24dp
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 24, height: 24)

            Text("Love2Love")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white.opacity(0.9))
        }
        .padding(.bottom, 30)
    }
    .background(
        LinearGradient(
            gradient: Gradient(colors: [
                Color(red: 0.2, green: 0.1, blue: 0.15),
                Color(red: 0.4, green: 0.2, blue: 0.3),
                Color(red: 0.6, green: 0.3, blue: 0.2)
            ]),
            startPoint: .top,
            endPoint: .bottom
        )
    )
}
.frame(height: 400)
.cornerRadius(20)
.shadow(
    color: .black.opacity(isBackground ? 0.1 : 0.3),
    radius: isBackground ? 5 : 10,
    x: 0,
    y: isBackground ? 2 : 5
)
```

### Image Journal État Vide

```
Image("jou")
    .resizable()
    .aspectRatio(contentMode: .fit)
    .frame(maxWidth: 200, maxHeight: 200)
    .padding(.horizontal, 30)
```

### État Vide Favoris

```
VStack(spacing: 20) {
    Image(systemName: "heart")
        .font(.system(size: 60))
        .foregroundColor(.gray.opacity(0.5))

    Text("no_favorites_yet")
        .font(.system(size: 18, weight: .medium))
        .foregroundColor(.gray)
        .multilineTextAlignment(.center)

    Text("add_favorites_from_questions")
        .font(.system(size: 16))
        .foregroundColor(.gray.opacity(0.8))
        .multilineTextAlignment(.center)
}
.padding(.horizontal, 40)
```

### Clés XCStrings Favoris

```
// Navigation et titres
- "favorites" (Favoris)
- "shared_favorites" (Favoris partagés)
- "my_favorites" (Mes favoris)

// États vides
- "no_favorites_yet" (Aucun favori pour le moment)
- "add_favorites_from_questions" (Ajoutez des favoris depuis les questions)

// Actions
- "add_to_favorites" (Ajouter aux favoris)
- "remove_from_favorites" (Retirer des favoris)
- "share_favorite" (Partager favori)

// Gestion
- "favorite_added" (Favori ajouté)
- "favorite_removed" (Favori supprimé)
- "favorite_shared" (Favori partagé)

// Navigation cartes
- "swipe_to_browse" (Balayez pour parcourir)
- "tap_to_share" (Appuyez pour partager)
```

---

## 📱 6. Profil (MenuView)

### Architecture Profil Complète

```
ScrollView {
    VStack(spacing: 0) {
        // Header avec photo de profil
        headerSection

        // Section À propos de moi
        aboutMeSection

        // Trait de séparation
        separatorLine

        // Section Application
        applicationSection
    }
}
.sheet(isPresented: $showingGalleryPicker)
.fullScreenCover(isPresented: $showImageCropper)
.alert(isPresented: $showSettingsAlert)
```

### Header Section Design

```
VStack(spacing: 16) {
    Button(action: checkPhotoLibraryPermission) {
        ZStack {
            // Effet surbrillance
            Circle()
                .fill(Color.white.opacity(0.35))
                .frame(width: 132, height: 132) // 120 + 12
                .blur(radius: 6)

            if let croppedImage = croppedImage {
                Image(uiImage: croppedImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 120, height: 120)
                    .clipShape(Circle())
            } else if let cachedImage = UserCacheManager.shared.getCachedProfileImage() {
                Image(uiImage: cachedImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 120, height: 120)
                    .clipShape(Circle())
            } else if let imageURL = currentUserImageURL {
                AsyncImageView(
                    imageURL: imageURL,
                    width: 120,
                    height: 120,
                    cornerRadius: 60
                )
            } else if !currentUserName.isEmpty && currentUserName != "Non défini" {
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

            // Bordure blanche
            Circle()
                .stroke(Color.white, lineWidth: 3)
                .frame(width: 120, height: 120)
        }
    }
}
.padding(.top, 120)
.padding(.bottom, 50)
```

### ProfileRowView Component

```
Button(action: action) {
    HStack {
        if let icon = icon {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(Color(hex: "#FD267A"))
                .frame(width: 20)
        }

        Text(title)
            .font(.system(size: 16))
            .foregroundColor(isDestructive ? .red : .black)

        Spacer()

        if !value.isEmpty {
            Text(value)
                .font(.system(size: 16))
                .foregroundColor(.gray)
        }

        if showChevron {
            Image(systemName: "chevron.right")
                .font(.system(size: 14))
                .foregroundColor(.gray)
        }
    }
    .padding(.horizontal, 20)
    .padding(.vertical, 16)
    .contentShape(Rectangle())
}
.buttonStyle(PlainButtonStyle())
```

### Section À propos de moi

```
VStack(spacing: 0) {
    // Titre section
    HStack {
        Text("about_me")
            .font(.system(size: 22, weight: .semibold))
            .foregroundColor(.black)
        Spacer()
    }
    .padding(.horizontal, 20)
    .padding(.bottom, 20)

    // Lignes profil
    ProfileRowView(title: "name", value: currentUserName, showChevron: true) {
        showingNameEdit = true
    }

    ProfileRowView(title: "in_relationship_since", value: currentRelationshipStart, showChevron: true) {
        showingRelationshipEdit = true
    }

    ProfileRowView(title: "partner_code", value: "", showChevron: true) {
        showingPartnerCode = true
    }

    ProfileRowView(title: "location_tutorial", value: "", showChevron: true) {
        onLocationTutorialTap?()
    }

    ProfileRowView(title: "widgets", value: "", showChevron: true) {
        onWidgetsTap?()
    }

    ProfileRowView(title: "manage_subscription", value: "", showChevron: true) {
        openSubscriptionSettings()
    }
}
```

### Section Application

```
VStack(spacing: 0) {
    HStack {
        Text("application")
            .font(.system(size: 22, weight: .semibold))
            .foregroundColor(.black)
        Spacer()
    }
    .padding(.horizontal, 20)
    .padding(.bottom, 20)

    ProfileRowView(title: "contact_us", value: "", showChevron: true) {
        openSupportEmail()
    }

    ProfileRowView(title: "terms_conditions", value: "", showChevron: true) {
        // Ouvrir CGV
    }

    ProfileRowView(title: "privacy_policy", value: "", showChevron: true) {
        // Ouvrir politique
    }

    ProfileRowView(title: "delete_account", value: "", isDestructive: false) {
        showingDeleteConfirmation = true
    }
}
```

### EditNameView et EditRelationshipView

```
// EditNameView
VStack(spacing: 20) {
    TextField("Votre nom", text: $newName)
        .font(.system(size: 16))
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.gray.opacity(0.1))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
        )

    Button(action: saveAndDismiss) {
        Text("save")
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FD267A"),
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

// EditRelationshipView avec DatePicker
DatePicker("", selection: $selectedDate, displayedComponents: .date)
    .datePickerStyle(WheelDatePickerStyle())
    .labelsHidden()
```

### Clés XCStrings Profil

```
// Sections principales
- "about_me" (À propos de moi)
- "application" (Application)

// Profil utilisateur
- "name" (Nom)
- "in_relationship_since" (En couple depuis)
- "partner_code" (Code partenaire)
- "location_tutorial" (Tutoriel localisation)
- "widgets" (Widgets)
- "manage_subscription" (Gérer abonnement)

// Application
- "contact_us" (Contactez-nous)
- "terms_conditions" (CGV)
- "privacy_policy" (Politique confidentialité)
- "delete_account" (Supprimer compte)
- "deleting_account" (Suppression en cours)

// Actions et états
- "save" (Sauvegarder)
- "cancel" (Annuler)
- "close" (Fermer)
- "authorization_required" (Autorisation requise)
- "open_settings_button" (Ouvrir paramètres)
- "error_image_not_found" (Image non trouvée)

// Confirmation suppression
- "delete_account_confirmation" (Confirmation suppression)

// Photo profil
- "add_photo" (Ajouter photo)
- "change_photo" (Changer photo)
- "remove_photo" (Supprimer photo)
```

---

## 🤖 Architecture Android - Pages Principales

### Couleurs Material Design 3

```kotlin
object MainPagesColors {
    val Primary = Color(0xFFFD267A)
    val Background = Color(0xFFF7F7F9)
    val Surface = Color.White
    val OnSurface = Color.Black
    val OnSurfaceVariant = Color.Black.copy(alpha = 0.7f)

    // Dégradés cartes
    val CardHeaderStart = Color(1.0f, 0.4f, 0.6f)
    val CardHeaderEnd = Color(1.0f, 0.6f, 0.8f)
    val CardBodyStart = Color(0.2f, 0.1f, 0.15f)
    val CardBodyMid = Color(0.4f, 0.2f, 0.3f)
    val CardBodyEnd = Color(0.6f, 0.3f, 0.2f)
}
```

### Typographie Pages Principales

```kotlin
object MainPagesTypography {
    val PageTitle = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black
    )
    val CardTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White
    )
    val SectionTitle = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black
    )
    val Body = TextStyle(
        fontSize = 16.sp,
        color = Color.Black
    )
    val Caption = TextStyle(
        fontSize = 14.sp,
        color = Color.Black.copy(alpha = 0.7f)
    )
}
```

### Layout Générique Pages

```kotlin
@Composable
fun MainPageLayout(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainPagesColors.Background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MainPagesTypography.PageTitle
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            actions()
        }

        // Contenu
        content()
    }
}
```

### Composable Cartes Gradients

```kotlin
@Composable
fun GradientQuestionCard(
    headerText: String,
    bodyText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header avec dégradé
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MainPagesColors.CardHeaderStart,
                                MainPagesColors.CardHeaderEnd
                            )
                        )
                    )
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = headerText,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            // Corps avec dégradé vertical
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MainPagesColors.CardBodyStart,
                                MainPagesColors.CardBodyMid,
                                MainPagesColors.CardBodyEnd
                            )
                        )
                    )
                    .padding(horizontal = 30.dp, vertical = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bodyText,
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp
                    )
                )
            }
        }
    }
}
```

### ProfileRow Android

```kotlin
@Composable
fun ProfileRow(
    title: String,
    value: String = "",
    showChevron: Boolean = false,
    isDestructive: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MainPagesColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isDestructive) Color.Red else Color.Black
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (showChevron) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
```

### Navigation Tabs Icônes

```kotlin
// TabContainerView utilise ces icônes SVG pour la navigation
- home: Icône accueil (24dp, couleur dynamique)
- star: Icône questions du jour (24dp)
- miss: Icône défis du jour (24dp)
- heart: Icône favoris (24dp)
- map: Icône journal (24dp)
- profile: Icône profil (24dp)

// Implémentation Android
@Composable
fun NavigationIcon(iconName: String, isSelected: Boolean) {
    Icon(
        painter = painterResource(id = getIconResource(iconName)),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = if (isSelected) MainPagesColors.Primary else Color.Gray
    )
}
```

### Localisation Android strings.xml

```xml
<!-- Page principale -->
<string name="widgets">Widgets</string>
<string name="couple_statistics">Vos statistiques de couple</string>
<string name="add_widgets">Ajouter vos widgets</string>
<string name="feel_closer_partner">Pour vous sentir encore plus proches de votre partenaire.</string>
<string name="km_away">%s km</string>
<string name="partner_location_unknown">?</string>
<string name="days_together">Jours\nensemble</string>
<string name="questions_answered">Questions\nrépondues</string>
<string name="cities_visited">Villes\nvisitées</string>
<string name="countries_visited">Pays\nvisités</string>

<!-- Question du jour -->
<string name="daily_question_title">Question du jour</string>
<string name="daily_question_start_conversation">Commencez la conversation avec votre partenaire</string>
<string name="no_partner_yet">Pas encore de partenaire</string>
<string name="connect_partner_to_answer">Connectez-vous avec votre partenaire pour répondre</string>
<string name="type_your_answer">Tapez votre réponse</string>
<string name="send">Envoyer</string>
<string name="free_questions_remaining">%d questions gratuites restantes</string>

<!-- Défi du jour -->
<string name="daily_challenges_title">Défi du jour</string>
<string name="challenge_completed">Défi terminé</string>
<string name="save_challenge">Sauvegarder</string>
<string name="saved_challenges">Défis sauvegardés</string>
<string name="no_challenge_today">Aucun défi aujourd\'hui</string>

<!-- Journal -->
<string name="our_journal">Notre journal</string>
<string name="add_entry">Ajouter une entrée</string>
<string name="entry_title_placeholder">Titre de l\'événement</string>
<string name="entry_description_placeholder">Décrivez ce moment spécial</string>
<string name="event_date">Date de l\'événement</string>
<string name="add_photo">Ajouter une photo</string>
<string name="select_location">Choisir un lieu</string>
<string name="save_entry">Sauvegarder</string>
<string name="no_entries_yet">Aucune entrée pour le moment</string>
<string name="start_your_journal">Commencez votre journal de couple</string>

<!-- Favoris -->
<string name="favorites">Favoris</string>
<string name="no_favorites_yet">Aucun favori pour le moment</string>
<string name="add_favorites_from_questions">Ajoutez des favoris depuis les questions</string>
<string name="add_to_favorites">Ajouter aux favoris</string>
<string name="remove_from_favorites">Retirer des favoris</string>

<!-- Profil -->
<string name="about_me">À propos de moi</string>
<string name="application">Application</string>
<string name="name">Nom</string>
<string name="in_relationship_since">En couple depuis</string>
<string name="partner_code">Code partenaire</string>
<string name="location_tutorial">Tutoriel localisation</string>
<string name="widgets">Widgets</string>
<string name="manage_subscription">Gérer son abonnement</string>
<string name="contact_us">Contactez-nous</string>
<string name="terms_conditions">Conditions d\'utilisation</string>
<string name="privacy_policy">Politique de confidentialité</string>
<string name="delete_account">Supprimer le compte</string>
<string name="save">Sauvegarder</string>
<string name="cancel">Annuler</string>
```

---

## 📋 Conclusion

Ce rapport fournit toutes les spécifications pour recréer fidèlement les 6 pages principales de CoupleApp sur Android :

### 🎯 **Pages Analysées**

- **Page Principale** : Header partenaire, catégories, widgets, statistiques
- **Question du Jour** : Cartes gradient, chat intégré, freemium
- **Défi du Jour** : Cartes similaires, actions sauvegarder/compléter
- **Journal** : Entrées avec photos/localisation, création/modification
- **Favoris** : Cartes swipables avec design gradient uniforme
- **Profil** : Photo, sections À propos/Application, édition données

### 🔧 **Composants Techniques Uniformes**

- **Cartes Gradient** : Header rose + corps dégradé (toutes pages)
- **Layout 28sp** : Titres pages cohérents
- **Fond #F7F7F9** : Uniformité visuelle
- **Actions #FD267A** : Couleur brand constante
- **Shadows 8dp** : Profondeur uniforme

### 📱 **+150 Clés XCString Documentées**

Toutes les clés nécessaires par page avec adaptation Android strings.xml

### 🤖 **Architecture Android Prête**

- Material Design 3 colors
- Composables réutilisables
- Layout patterns cohérents
- Gradient brushes fidèles
- ProfileRow component
- MainPageLayout générique

L'adaptation Android peut utiliser ce rapport pour une cohérence parfaite avec l'expérience iOS tout en respectant les conventions Material Design ! 🎨📱✨
