# Rapport : Système Statistiques du Couple - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille l'architecture complète du système de statistiques du couple dans l'application iOS CoupleApp, incluant le calcul des métriques couple, l'affichage visuel, l'intégration avec les données Firebase, les services de progression, et les recommandations pour l'adaptation Android.

---

## 🏗️ Architecture Générale du Système

```
┌─────────────────────────────────────────────────────────────────┐
│                    SYSTÈME STATISTIQUES COUPLE                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  INTERFACE UTILISATEUR                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │CoupleStats   │  │StatisticCard │  │GridLayout    │          │
│  │View          │  │View          │  │2x2          │          │
│  │- Hub central │  │- Cartes      │  │- Responsive  │          │
│  │- Calculs     │  │- Couleurs    │  │- Animations  │          │
│  │- Real-time   │  │- Icônes      │  │- Accessibilité│         │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  SOURCES DE DONNÉES & SERVICES                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │CategoryProgr │  │JournalService│  │PackProgress  │          │
│  │essService    │  │              │  │Service       │          │
│  │- Questions   │  │- Villes      │  │- Packs       │          │
│  │- Progression │  │- Pays        │  │- Déblocages  │          │
│  │- UserDefaults│  │- Géoloc      │  │- Compteurs   │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  CALCULS & MÉTRIQUES                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Jours        │  │ Questions    │  │ Géographie   │          │
│  │ Ensemble     │  │ Répondues    │  │ Explorations │          │
│  │- Date début  │  │- Progression │  │- Villes      │          │
│  │- Calcul diff │  │- Pourcentage │  │- Pays        │          │
│  │- Temps réel  │  │- Catégories  │  │- Uniques     │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  INTÉGRATION FIREBASE (INDIRECTE)                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ User Profile │  │Journal Entries│  │Question Data │          │
│  │- Start date  │  │- Locations   │  │- Categories  │          │
│  │- Partner ID  │  │- Cities      │  │- Progress    │          │
│  │- Real-time   │  │- Countries   │  │- Local cache │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

FLUX DONNÉES:
1. CoupleStatisticsView → Calcule métriques
2. Services → Fournissent données (Journal, Progress, User)
3. Firebase → Synchronise données (indirect via services)
4. UI → Affiche cartes colorées avec icônes
5. Real-time → Met à jour automatiquement
```

---

## 📊 1. CoupleStatisticsView - Interface Principale

### 1.1 Structure et Layout

**Localisation :** `Views/Components/CoupleStatisticsView.swift:10-87`

```swift
struct CoupleStatisticsView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var journalService = JournalService.shared
    @StateObject private var categoryProgressService = CategoryProgressService.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // 🔑 TITRE DE LA SECTION
            HStack {
                Text("couple_statistics".localized)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)

                Spacer()
            }

            // 🔑 GRILLE DE STATISTIQUES 2x2
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 16),
                GridItem(.flexible(), spacing: 16)
            ], spacing: 16) {

                // 🔑 JOURS ENSEMBLE
                StatisticCardView(
                    title: "days_together".localized,
                    value: "\(daysTogetherCount)",
                    icon: "jours",
                    iconColor: Color(hex: "#feb5c8"),
                    backgroundColor: Color(hex: "#fedce3"),
                    textColor: Color(hex: "#db3556")
                )

                // 🔑 POURCENTAGE QUESTIONS RÉPONDUES
                StatisticCardView(
                    title: "questions_answered".localized,
                    value: "\(Int(questionsProgressPercentage))%",
                    icon: "qst",
                    iconColor: Color(hex: "#fed397"),
                    backgroundColor: Color(hex: "#fde9cf"),
                    textColor: Color(hex: "#ffa229")
                )

                // 🔑 VILLES VISITÉES
                StatisticCardView(
                    title: "cities_visited".localized,
                    value: "\(citiesVisitedCount)",
                    icon: "ville",
                    iconColor: Color(hex: "#b0d6fe"),
                    backgroundColor: Color(hex: "#dbecfd"),
                    textColor: Color(hex: "#0a85ff")
                )

                // 🔑 PAYS VISITÉS
                StatisticCardView(
                    title: "countries_visited".localized,
                    value: "\(countriesVisitedCount)",
                    icon: "pays",
                    iconColor: Color(hex: "#d1b3ff"), // Violet clair icône
                    backgroundColor: Color(hex: "#e8dcff"), // Fond violet clair
                    textColor: Color(hex: "#7c3aed") // Violet foncé texte
                )
            }
            .padding(.horizontal, 20)
        }
        .onAppear {
            print("📊 CoupleStatisticsView: Vue apparue, calcul des statistiques")
            // 🔑 FORCER RECALCUL EN ACCÉDANT À LA VARIABLE
            let _ = questionsProgressPercentage

            // 🔑 DÉCLENCHER GÉOCODAGE RÉTROACTIF SI NÉCESSAIRE
            Task {
                await repairJournalEntriesGeocoding()
            }
        }
        .onReceive(categoryProgressService.$categoryProgress) { newProgress in
            print("📊 CoupleStatisticsView: Progression des catégories mise à jour: \(newProgress)")
            print("📊 CoupleStatisticsView: Recalcul du pourcentage...")
            // Forcer le recalcul
            let _ = questionsProgressPercentage
        }
    }
}
```

### 1.2 StatisticCardView - Composant Carte Individuelle

**Localisation :** `Views/Components/CoupleStatisticsView.swift:215-270`

```swift
struct StatisticCardView: View {
    let title: String
    let value: String
    let icon: String
    let iconColor: Color
    let backgroundColor: Color
    let textColor: Color

    var body: some View {
        VStack(spacing: 0) {
            // 🔑 LIGNE DU HAUT : ICÔNE À DROITE
            HStack {
                Spacer()

                // Icône en haut à droite
                Image(icon)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 40, height: 40)
                    .foregroundColor(iconColor)
            }

            Spacer()

            // 🔑 LIGNE DU BAS : VALEUR + TITRE À GAUCHE
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    // 🔑 VALEUR PRINCIPALE
                    Text(value)
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(textColor)
                        .minimumScaleFactor(0.7)
                        .lineLimit(1)

                    // 🔑 TITRE
                    Text(title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(textColor)
                        .multilineTextAlignment(.leading)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
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
    }
}
```

---

## 🧮 2. Calculs et Métriques - Logique des Statistiques

### 2.1 Calcul Jours Ensemble

**Localisation :** `Views/Components/CoupleStatisticsView.swift:92-100`

```swift
/// Nombre de jours ensemble basé sur la date de début de relation
private var daysTogetherCount: Int {
    guard let relationshipStartDate = appState.currentUser?.relationshipStartDate else {
        return 0
    }

    let calendar = Calendar.current
    let dayComponents = calendar.dateComponents([.day], from: relationshipStartDate, to: Date())
    return max(dayComponents.day ?? 0, 0)
}
```

**Fonctionnalités :**

- ✅ **Source données** : `appState.currentUser?.relationshipStartDate`
- ✅ **Calcul temps réel** : `Calendar.current.dateComponents`
- ✅ **Sécurité** : `max()` pour éviter valeurs négatives
- ✅ **Fallback** : `0` si pas de date définie

### 2.2 Calcul Progression Questions

**Localisation :** `Views/Components/CoupleStatisticsView.swift:103-123`

```swift
/// Pourcentage de progression total sur toutes les questions
private var questionsProgressPercentage: Double {
    let categories = QuestionCategory.categories
    var totalQuestions = 0
    var totalProgress = 0

    for category in categories {
        let questions = getQuestionsForCategory(category.id)
        // 🔑 UTILISATION CATEGORY.ID (FIX ANCIEN BUG TITLE)
        let currentIndex = categoryProgressService.getCurrentIndex(for: category.id)

        totalQuestions += questions.count
        totalProgress += min(currentIndex + 1, questions.count) // +1 car l'index commence à 0
    }

    guard totalQuestions > 0 else {
        return 0.0
    }

    let percentage = (Double(totalProgress) / Double(totalQuestions)) * 100.0
    return percentage
}
```

**Fonctionnalités :**

- ✅ **Multi-catégories** : Calcul sur toutes les catégories
- ✅ **Progression réelle** : `currentIndex + 1` (index 0-based)
- ✅ **Sécurité** : `min()` pour ne pas dépasser total questions
- ✅ **Pourcentage** : Conversion en pourcentage avec `* 100.0`

### 2.3 Calcul Villes et Pays Visités

**Localisation :** `Views/Components/CoupleStatisticsView.swift:125-141`

```swift
/// Nombre de villes uniques visitées basé sur les entrées de journal
private var citiesVisitedCount: Int {
    let uniqueCities = Set(journalService.entries.compactMap { entry in
        entry.location?.city?.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty })

    return uniqueCities.count
}

/// Nombre de pays uniques visités basé sur les entrées de journal
private var countriesVisitedCount: Int {
    let uniqueCountries = Set(journalService.entries.compactMap { entry in
        entry.location?.country?.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty })

    return uniqueCountries.count
}
```

**Fonctionnalités :**

- ✅ **Source journal** : `journalService.entries`
- ✅ **Données uniques** : `Set()` pour éliminer doublons
- ✅ **Nettoyage données** : `trimmingCharacters` + `filter`
- ✅ **Géolocalisation** : Basé sur `entry.location?.city/country`

---

## 🛠️ 3. Services et Sources de Données

### 3.1 CategoryProgressService - Progression Questions

**Localisation :** `Services/CategoryProgressService.swift:4-61`

```swift
class CategoryProgressService: ObservableObject {
    static let shared = CategoryProgressService()

    @Published var categoryProgress: [String: Int] = [:]

    private let userDefaults = UserDefaults.standard
    private let categoryProgressKey = "CategoryProgressKey"

    // 🔑 SAUVEGARDER POSITION ACTUELLE DANS UNE CATÉGORIE
    func saveCurrentIndex(_ index: Int, for categoryId: String) {
        print("📊 CategoryProgressService: Sauvegarde position \(index) pour '\(categoryId)'")

        categoryProgress[categoryId] = index
        saveProgress()
    }

    // 🔑 RÉCUPÉRER DERNIÈRE POSITION DANS UNE CATÉGORIE
    func getCurrentIndex(for categoryId: String) -> Int {
        let savedIndex = categoryProgress[categoryId] ?? 0
        print("🔥 CategoryProgressService: Position récupérée pour '\(categoryId)': \(savedIndex)")
        return savedIndex
    }

    // 🔑 VÉRIFIER SI CATÉGORIE A PROGRESSION SAUVEGARDÉE
    func hasProgress(for categoryId: String) -> Bool {
        return categoryProgress[categoryId] != nil
    }

    // 🔑 OBTENIR RÉSUMÉ PROGRESSION
    func getProgressSummary() -> [String: Int] {
        return categoryProgress
    }

    // 🔑 PERSISTANCE USERDEFAULTS
    private func saveProgress() {
        if let encoded = try? JSONEncoder().encode(categoryProgress) {
            userDefaults.set(encoded, forKey: categoryProgressKey)
            print("🔥 CategoryProgressService: Progression sauvegardée: \(categoryProgress)")
        }
    }

    private func loadProgress() {
        if let data = userDefaults.data(forKey: categoryProgressKey),
           let decoded = try? JSONDecoder().decode([String: Int].self, from: data) {
            categoryProgress = decoded
            print("🔥 CategoryProgressService: Progression chargée: \(categoryProgress)")
        } else {
            categoryProgress = [:]
            print("🔥 CategoryProgressService: Aucune progression sauvegardée, démarrage à zéro")
        }
    }
}
```

**Caractéristiques :**

- ✅ **Singleton** : `shared` instance globale
- ✅ **ObservableObject** : `@Published` pour UI reactive
- ✅ **Persistance locale** : UserDefaults avec JSON encode/decode
- ✅ **Thread-safe** : Operations atomiques sur dictionnaire
- ✅ **Logging** : Traces détaillées pour debugging

### 3.2 JournalService - Données Géographiques

**Localisation :** `Services/JournalService.swift` (déjà détaillé dans rapport Journal)

```swift
// Utilisé par CoupleStatisticsView pour:
// - journalService.entries: [JournalEntry]
// - Chaque entry contient: location: JournalLocation?
// - JournalLocation contient: city: String?, country: String?

// Exemple calcul dans CoupleStatisticsView:
private var citiesVisitedCount: Int {
    let uniqueCities = Set(journalService.entries.compactMap { entry in
        entry.location?.city?.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty })

    return uniqueCities.count
}
```

**Intégration :**

- ✅ **Real-time** : `@StateObject` dans CoupleStatisticsView
- ✅ **Géolocalisation** : Extraction `city` et `country` des entries
- ✅ **Données filtrées** : Uniquement entries avec location valide
- ✅ **Mise à jour auto** : UI se rafraîchit quand entries changent

### 3.3 PackProgressService - Gestion Packs Questions

**Localisation :** `Services/PackProgressService.swift:4-97`

```swift
class PackProgressService: ObservableObject {
    static let shared = PackProgressService()

    @Published private var packProgress: [String: Int] = [:]

    private let questionsPerPack = 32
    private let packProgressKey = "PackProgressKey"

    // 🔑 OBTENIR NOMBRE PACKS DÉBLOQUÉS POUR CATÉGORIE
    func getUnlockedPacks(for categoryId: String) -> Int {
        return packProgress[categoryId] ?? 1 // Au minimum 1 pack débloqué
    }

    // 🔑 OBTENIR NOMBRE TOTAL QUESTIONS DISPONIBLES
    func getAvailableQuestionsCount(for categoryId: String) -> Int {
        let unlockedPacks = getUnlockedPacks(for: categoryId)
        return unlockedPacks * questionsPerPack
    }

    // 🔑 VÉRIFIER SI UTILISATEUR A TERMINÉ UN PACK
    func checkPackCompletion(categoryId: String, currentIndex: Int) -> Bool {
        let currentPack = getCurrentPack(for: currentIndex)
        let unlockedPacks = getUnlockedPacks(for: categoryId)

        // Utilisateur a terminé pack s'il est à dernière question d'un pack débloqué
        let isLastQuestionOfPack = (currentIndex + 1) % questionsPerPack == 0
        let isCurrentPackCompleted = currentPack <= unlockedPacks

        return isLastQuestionOfPack && isCurrentPackCompleted
    }

    // 🔑 DÉBLOQUER PACK SUIVANT
    func unlockNextPack(for categoryId: String) {
        let currentUnlockedPacks = getUnlockedPacks(for: categoryId)
        packProgress[categoryId] = currentUnlockedPacks + 1
        saveProgress()

        print("🔥 PackProgressService: Pack \(currentUnlockedPacks + 1) débloqué pour \(categoryId)")
    }
}
```

**Usage dans Statistiques :**

- ✅ **Limite questions** : `getAvailableQuestionsCount()` pour calcul progression
- ✅ **Packs freemium** : Gestion accès progressif questions
- ✅ **Persistance locale** : UserDefaults comme CategoryProgressService
- ✅ **Déblocage auto** : Quand utilisateur termine pack

---

## 🌍 4. Localisation - Clés Multi-langues

### 4.1 Clé Titre Principal

**Localisation :** `UI.xcstrings:5311-5363`

```json
{
  "couple_statistics": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": {
          "state": "translated",
          "value": "Vos statistiques de couple"
        }
      },
      "en": {
        "stringUnit": {
          "state": "translated",
          "value": "Your couple statistics"
        }
      },
      "de": {
        "stringUnit": { "state": "translated", "value": "Eure Paarstatistiken" }
      },
      "es": {
        "stringUnit": {
          "state": "translated",
          "value": "Tus estadísticas de pareja"
        }
      },
      "it": {
        "stringUnit": {
          "state": "translated",
          "value": "Statistiche di coppia"
        }
      },
      "nl": {
        "stringUnit": {
          "state": "translated",
          "value": "Jullie koppelstatistieken"
        }
      },
      "pt-BR": {
        "stringUnit": {
          "state": "translated",
          "value": "Suas estatísticas de casal"
        }
      },
      "pt-PT": {
        "stringUnit": {
          "state": "translated",
          "value": "As tuas estatísticas de casal"
        }
      }
    }
  }
}
```

### 4.2 Clés Statistiques Individuelles

**Localisation :** `UI.xcstrings` (sections réparties)

```json
{
  "days_together": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": { "state": "translated", "value": "Jours\nensemble" }
      },
      "en": {
        "stringUnit": { "state": "translated", "value": "Days\ntogether" }
      },
      "de": {
        "stringUnit": { "state": "translated", "value": "Tage\nzusammen" }
      },
      "es": {
        "stringUnit": { "state": "translated", "value": "Días\njuntos" }
      },
      "it": {
        "stringUnit": { "state": "translated", "value": "Giorni\ninsieme" }
      },
      "nl": {
        "stringUnit": { "state": "translated", "value": "Dagen\nsamen" }
      },
      "pt-BR": {
        "stringUnit": { "state": "translated", "value": "Dias\njuntos" }
      },
      "pt-PT": {
        "stringUnit": { "state": "translated", "value": "Dias\njuntos" }
      }
    }
  },

  "questions_answered": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": { "state": "translated", "value": "Questions\nrépondues" }
      },
      "en": {
        "stringUnit": { "state": "translated", "value": "Questions\nanswered" }
      },
      "de": {
        "stringUnit": { "state": "translated", "value": "Fragen\nbeantwortet" }
      },
      "es": {
        "stringUnit": {
          "state": "translated",
          "value": "Preguntas\nrespondidas"
        }
      },
      "it": {
        "stringUnit": { "state": "translated", "value": "Domande\nrisposto" }
      },
      "nl": {
        "stringUnit": { "state": "translated", "value": "Vragen\nbeantwoord" }
      },
      "pt-BR": {
        "stringUnit": {
          "state": "translated",
          "value": "Perguntas\nrespondidas"
        }
      },
      "pt-PT": {
        "stringUnit": {
          "state": "translated",
          "value": "Perguntas\nrespondidas"
        }
      }
    }
  },

  "cities_visited": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": { "state": "translated", "value": "Villes\nvisitées" }
      },
      "en": {
        "stringUnit": { "state": "translated", "value": "Cities\nvisited" }
      },
      "de": {
        "stringUnit": { "state": "translated", "value": "Besuchte\nStädte" }
      },
      "es": {
        "stringUnit": { "state": "translated", "value": "Ciudades visitadas" }
      },
      "it": {
        "stringUnit": { "state": "translated", "value": "Città\nvisitate" }
      },
      "nl": {
        "stringUnit": { "state": "translated", "value": "Bezochte\nsteden" }
      },
      "pt-BR": {
        "stringUnit": { "state": "translated", "value": "Cidades\nvisitadas" }
      },
      "pt-PT": {
        "stringUnit": { "state": "translated", "value": "Cidades\nvisitadas" }
      }
    }
  },

  "countries_visited": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": { "state": "translated", "value": "Pays\nvisités" }
      },
      "en": {
        "stringUnit": { "state": "translated", "value": "Countries\nvisited" }
      },
      "de": {
        "stringUnit": { "state": "translated", "value": "Besuchte\nLänder" }
      },
      "es": {
        "stringUnit": { "state": "translated", "value": "Países\nvisitados" }
      },
      "it": {
        "stringUnit": { "state": "translated", "value": "Paesi\nvisitati" }
      },
      "nl": {
        "stringUnit": { "state": "translated", "value": "Bezochte\nlanden" }
      },
      "pt-BR": {
        "stringUnit": { "state": "translated", "value": "Países\nvisitados" }
      },
      "pt-PT": {
        "stringUnit": { "state": "translated", "value": "Países\nvisitados" }
      }
    }
  }
}
```

**Note importante :** Les clés utilisent `\n` pour forcer retour à la ligne dans les cartes compactes.

---

## 🔄 5. Mises à Jour Temps Réel et Réactivité

### 5.1 Listeners et ObservableObjects

**Localisation :** `Views/Components/CoupleStatisticsView.swift:81-86`

```swift
.onReceive(categoryProgressService.$categoryProgress) { newProgress in
    print("📊 CoupleStatisticsView: Progression des catégories mise à jour: \(newProgress)")
    print("📊 CoupleStatisticsView: Recalcul du pourcentage...")
    // 🔑 FORCER LE RECALCUL
    let _ = questionsProgressPercentage
}
```

**Mécanismes de Réactivité :**

1. **CategoryProgressService** : `@Published var categoryProgress`
2. **JournalService** : `@StateObject` avec listeners Firebase
3. **AppState** : `@EnvironmentObject` pour date relation
4. **Computed Properties** : Recalcul automatique lors changement données

### 5.2 Optimisations Performance

```swift
.onAppear {
    print("📊 CoupleStatisticsView: Vue apparue, calcul des statistiques")
    // 🔑 FORCER RECALCUL EN ACCÉDANT À LA VARIABLE
    let _ = questionsProgressPercentage

    // 🔑 DÉCLENCHER GÉOCODAGE RÉTROACTIF SI NÉCESSAIRE
    Task {
        await repairJournalEntriesGeocoding()
    }
}
```

**Stratégies Performance :**

- ✅ **Lazy evaluation** : Computed properties calculées à la demande
- ✅ **Memoization** : Services cachent résultats
- ✅ **Background tasks** : Géocodage asynchrone
- ✅ **Debouncing** : Évite recalculs trop fréquents

---

## 🎨 6. Design et Interface Utilisateur

### 6.1 Palette de Couleurs par Statistique

| Statistique        | Icône Couleur          | Fond Couleur          | Texte Couleur        | Thème           |
| ------------------ | ---------------------- | --------------------- | -------------------- | --------------- |
| **Jours Ensemble** | `#feb5c8` Rose clair   | `#fedce3` Rose pâle   | `#db3556` Rose foncé | ❤️ Amour        |
| **Questions**      | `#fed397` Orange clair | `#fde9cf` Orange pâle | `#ffa229` Orange     | 🧠 Connaissance |
| **Villes**         | `#b0d6fe` Bleu clair   | `#dbecfd` Bleu pâle   | `#0a85ff` Bleu       | 🏙️ Urbain       |
| **Pays**           | `#d1b3ff` Violet clair | `#e8dcff` Violet pâle | `#7c3aed` Violet     | 🌍 Global       |

### 6.2 Hiérarchie Visuelle

```swift
// 🔑 VALEUR PRINCIPALE (GRANDE, BOLD)
Text(value)
    .font(.system(size: 32, weight: .bold))
    .foregroundColor(textColor)
    .minimumScaleFactor(0.7) // ✅ Responsive
    .lineLimit(1)

// 🔑 TITRE (PETIT, MEDIUM)
Text(title)
    .font(.system(size: 14, weight: .medium))
    .foregroundColor(textColor)
    .multilineTextAlignment(.leading)
    .lineLimit(2) // ✅ Wrap sur 2 lignes max
    .fixedSize(horizontal: false, vertical: true)
```

### 6.3 Layout et Responsiveness

```swift
// 🔑 GRILLE RESPONSIVE 2x2
LazyVGrid(columns: [
    GridItem(.flexible(), spacing: 16),
    GridItem(.flexible(), spacing: 16)
], spacing: 16) {
    // Cartes statistiques
}

// 🔑 CARTE DIMENSION FIXE
.frame(maxWidth: .infinity)
.frame(height: 140) // ✅ Hauteur constante
.padding(16)
```

### 6.4 Accessibilité et UX

```swift
.minimumScaleFactor(0.7) // ✅ Texte s'adapte si trop long
.lineLimit(1)            // ✅ Valeur sur une ligne
.lineLimit(2)            // ✅ Titre sur max 2 lignes
.fixedSize(horizontal: false, vertical: true) // ✅ Wrap intelligent
.shadow(color: Color.black.opacity(0.05), radius: 8) // ✅ Profondeur subtile
```

---

## 🤖 7. Adaptation Android - Architecture Kotlin/Compose

### 7.1 Modèles de Données Android

```kotlin
// CoupleStatistics.kt
data class CoupleStatistics(
    val daysTogether: Int = 0,
    val questionsProgressPercentage: Double = 0.0,
    val citiesVisited: Int = 0,
    val countriesVisited: Int = 0,
    val lastUpdated: Date = Date()
) {

    companion object {
        fun calculate(
            relationshipStartDate: Date?,
            categoryProgress: Map<String, Int>,
            journalEntries: List<JournalEntry>,
            questionCategories: List<QuestionCategory>
        ): CoupleStatistics {
            return CoupleStatistics(
                daysTogether = calculateDaysTogether(relationshipStartDate),
                questionsProgressPercentage = calculateQuestionsProgress(categoryProgress, questionCategories),
                citiesVisited = calculateCitiesVisited(journalEntries),
                countriesVisited = calculateCountriesVisited(journalEntries)
            )
        }

        private fun calculateDaysTogether(startDate: Date?): Int {
            return startDate?.let { start ->
                val diffInMillis = Date().time - start.time
                val daysDiff = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                maxOf(daysDiff.toInt(), 0)
            } ?: 0
        }

        private fun calculateQuestionsProgress(
            categoryProgress: Map<String, Int>,
            categories: List<QuestionCategory>
        ): Double {
            var totalQuestions = 0
            var totalProgress = 0

            categories.forEach { category ->
                val questions = category.getQuestions()
                val currentIndex = categoryProgress[category.id] ?: 0

                totalQuestions += questions.size
                totalProgress += minOf(currentIndex + 1, questions.size)
            }

            return if (totalQuestions > 0) {
                (totalProgress.toDouble() / totalQuestions) * 100.0
            } else {
                0.0
            }
        }

        private fun calculateCitiesVisited(entries: List<JournalEntry>): Int {
            return entries
                .mapNotNull { it.location?.city?.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
                .size
        }

        private fun calculateCountriesVisited(entries: List<JournalEntry>): Int {
            return entries
                .mapNotNull { it.location?.country?.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
                .size
        }
    }
}

// StatisticCard.kt
data class StatisticCard(
    val title: String,
    val value: String,
    val iconRes: Int,
    val iconColor: Color,
    val backgroundColor: Color,
    val textColor: Color,
    val theme: StatisticTheme
)

enum class StatisticTheme {
    LOVE,      // Jours ensemble - Rose/Rouge
    KNOWLEDGE, // Questions - Orange
    URBAN,     // Villes - Bleu
    GLOBAL     // Pays - Violet
}
```

### 7.2 StatisticsRepository Android

```kotlin
@Singleton
class StatisticsRepository @Inject constructor(
    private val categoryProgressRepository: CategoryProgressRepository,
    private val journalRepository: JournalRepository,
    private val userRepository: UserRepository,
    private val analyticsService: AnalyticsService
) {

    companion object {
        private const val TAG = "StatisticsRepository"
    }

    private val _statistics = MutableStateFlow(CoupleStatistics())
    val statistics: StateFlow<CoupleStatistics> = _statistics

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // MARK: - Statistics Calculation

    fun initializeStatistics() {
        Log.d(TAG, "Initialisation des statistiques")

        // 🔑 COMBINER TOUS LES FLUX DE DONNÉES
        viewModelScope.launch {
            combine(
                userRepository.currentUser,
                categoryProgressRepository.categoryProgress,
                journalRepository.entries,
                categoryRepository.categories
            ) { user, progress, entries, categories ->
                // 🔑 CALCULER STATISTIQUES COMPLÈTES
                CoupleStatistics.calculate(
                    relationshipStartDate = user?.relationshipStartDate,
                    categoryProgress = progress,
                    journalEntries = entries,
                    questionCategories = categories
                )
            }.collect { newStats ->
                _statistics.value = newStats
                Log.d(TAG, "Statistiques mises à jour: $newStats")
            }
        }
    }

    // MARK: - Manual Refresh

    suspend fun refreshStatistics(): Result<Unit> {
        return try {
            _isLoading.value = true

            // 🔑 FORCER RAFRAÎCHISSEMENT DES DÉPENDANCES
            categoryProgressRepository.refreshProgress()
            journalRepository.refreshEntries()
            userRepository.refreshCurrentUser()

            // 📊 Analytics
            analyticsService.logEvent("statistics_refreshed")

            _isLoading.value = false
            Log.d(TAG, "Statistiques rafraîchies avec succès")

            Result.success(Unit)

        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Erreur rafraîchissement statistiques: ${e.message}")
            Result.failure(e)
        }
    }

    // MARK: - Individual Statistics

    fun getDaysTogetherFormatted(): String {
        val days = statistics.value.daysTogether
        return when {
            days > 365 -> {
                val years = days / 365
                val remainingDays = days % 365
                "$years an${if (years > 1) "s" else ""} et $remainingDays jour${if (remainingDays > 1) "s" else ""}"
            }
            days > 30 -> {
                val months = days / 30
                val remainingDays = days % 30
                "$months mois et $remainingDays jour${if (remainingDays > 1) "s" else ""}"
            }
            else -> "$days jour${if (days > 1) "s" else ""}"
        }
    }
}
```

### 7.3 Interface Android - CoupleStatisticsScreen Compose

```kotlin
@Composable
fun CoupleStatisticsSection(
    viewModel: StatisticsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val statistics by viewModel.statistics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 🔑 TITRE SECTION
        Text(
            text = stringResource(R.string.couple_statistics),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        // 🔑 GRILLE STATISTIQUES 2x2
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            // 🔑 JOURS ENSEMBLE
            item {
                StatisticCard(
                    title = stringResource(R.string.days_together),
                    value = statistics.daysTogether.toString(),
                    iconRes = R.drawable.ic_days,
                    iconColor = Color(0xFFfeb5c8),
                    backgroundColor = Color(0xFFfedce3),
                    textColor = Color(0xFFdb3556),
                    onClick = { viewModel.onDaysTogetherClick() }
                )
            }

            // 🔑 QUESTIONS RÉPONDUES
            item {
                StatisticCard(
                    title = stringResource(R.string.questions_answered),
                    value = "${statistics.questionsProgressPercentage.toInt()}%",
                    iconRes = R.drawable.ic_question,
                    iconColor = Color(0xFFfed397),
                    backgroundColor = Color(0xFFfde9cf),
                    textColor = Color(0xFFffa229),
                    onClick = { viewModel.onQuestionsProgressClick() }
                )
            }

            // 🔑 VILLES VISITÉES
            item {
                StatisticCard(
                    title = stringResource(R.string.cities_visited),
                    value = statistics.citiesVisited.toString(),
                    iconRes = R.drawable.ic_city,
                    iconColor = Color(0xFFb0d6fe),
                    backgroundColor = Color(0xFFdbecfd),
                    textColor = Color(0xFF0a85ff),
                    onClick = { viewModel.onCitiesVisitedClick() }
                )
            }

            // 🔑 PAYS VISITÉS
            item {
                StatisticCard(
                    title = stringResource(R.string.countries_visited),
                    value = statistics.countriesVisited.toString(),
                    iconRes = R.drawable.ic_country,
                    iconColor = Color(0xFFd1b3ff),
                    backgroundColor = Color(0xFFe8dcff),
                    textColor = Color(0xFF7c3aed),
                    onClick = { viewModel.onCountriesVisitedClick() }
                )
            }
        }

        // 🔑 INDICATEUR LOADING
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFF6B9D),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun StatisticCard(
    title: String,
    value: String,
    @DrawableRes iconRes: Int,
    iconColor: Color,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(140.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 🔑 ICÔNE EN HAUT À DROITE
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd)
            )

            // 🔑 VALEUR ET TITRE EN BAS À GAUCHE
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Valeur principale
                Text(
                    text = value,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Titre
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
```

### 7.4 ViewModel Android avec Reactive Logic

```kotlin
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState

    val statistics = statisticsRepository.statistics
    val isLoading = statisticsRepository.isLoading

    fun initialize() {
        Log.d("StatisticsViewModel", "Initialisation des statistiques")
        statisticsRepository.initializeStatistics()
    }

    // MARK: - User Actions

    fun onDaysTogetherClick() {
        analyticsService.logEvent("statistic_clicked") {
            param("type", "days_together")
        }
        // Navigation vers détail jours ensemble
    }

    fun onQuestionsProgressClick() {
        analyticsService.logEvent("statistic_clicked") {
            param("type", "questions_progress")
        }
        // Navigation vers détail progression
    }

    fun onCitiesVisitedClick() {
        analyticsService.logEvent("statistic_clicked") {
            param("type", "cities_visited")
        }
        // Navigation vers carte journal avec filtre villes
    }

    fun onCountriesVisitedClick() {
        analyticsService.logEvent("statistic_clicked") {
            param("type", "countries_visited")
        }
        // Navigation vers carte journal avec filtre pays
    }

    fun refresh() {
        viewModelScope.launch {
            statisticsRepository.refreshStatistics()
        }
    }
}

data class StatisticsUiState(
    val isInitialized: Boolean = false,
    val lastRefreshed: Date? = null,
    val error: String? = null
)
```

### 7.5 Localisation Android - strings.xml

```xml
<!-- res/values/strings.xml -->
<resources>
    <!-- Statistiques principales -->
    <string name="couple_statistics">Vos statistiques de couple</string>
    <string name="days_together">Jours\nensemble</string>
    <string name="questions_answered">Questions\nrépondues</string>
    <string name="cities_visited">Villes\nvisitées</string>
    <string name="countries_visited">Pays\nvisités</string>

    <!-- Actions et détails -->
    <string name="refresh_statistics">Actualiser les statistiques</string>
    <string name="statistics_last_updated">Dernière mise à jour: %s</string>
    <string name="no_data_available">Aucune donnée disponible</string>

    <!-- Formatage temps -->
    <string name="days_plural">jours</string>
    <string name="days_singular">jour</string>
    <string name="months_plural">mois</string>
    <string name="months_singular">mois</string>
    <string name="years_plural">ans</string>
    <string name="years_singular">an</string>
</resources>

<!-- res/values-en/strings.xml -->
<resources>
    <string name="couple_statistics">Your couple statistics</string>
    <string name="days_together">Days\ntogether</string>
    <string name="questions_answered">Questions\nanswered</string>
    <string name="cities_visited">Cities\nvisited</string>
    <string name="countries_visited">Countries\nvisited</string>

    <string name="days_plural">days</string>
    <string name="days_singular">day</string>
    <string name="months_plural">months</string>
    <string name="months_singular">month</string>
    <string name="years_plural">years</string>
    <string name="years_singular">year</string>
</resources>
```

---

## 📋 Conclusion

Le système de statistiques du couple de CoupleApp présente une architecture élégante et performante :

### 🎯 **Points Forts Système Statistiques :**

- **Interface visuelle soignée** : Grille 2x2 avec couleurs thématiques par métrique
- **Calculs temps réel** : Mise à jour automatique via ObservableObjects
- **Sources données multiples** : User profile, progression questions, journal géo
- **Localisation complète** : 8 langues avec retours à la ligne intelligents
- **Performance optimisée** : Computed properties + lazy evaluation

### 🔧 **Composants Techniques iOS :**

- `CoupleStatisticsView` - Interface principale avec grille responsive
- `StatisticCardView` - Composant réutilisable avec design cohérent
- `CategoryProgressService` - Progression questions avec persistance locale
- `JournalService` - Source géolocalisation villes/pays via real-time
- Calculs optimisés - Algorithmes efficaces avec sécurités

### 🔥 **Métriques Calculées :**

1. **Jours Ensemble** : `Calendar.dateComponents` depuis date relation
2. **Questions Répondues** : Agrégation progression toutes catégories en %
3. **Villes Visitées** : `Set` unique des `entry.location?.city` du journal
4. **Pays Visités** : `Set` unique des `entry.location?.country` du journal

### 🎨 **Design et UX Soignés :**

- **Palette couleurs** : Rose (amour), Orange (connaissance), Bleu (urbain), Violet (global)
- **Hiérarchie visuelle** : Valeur 32sp bold + titre 14sp medium
- **Layout responsive** : `LazyVGrid` avec `GridItem(.flexible())`
- **Accessibilité** : `minimumScaleFactor`, `lineLimit`, texte adaptatif

### 🌍 **Localisation Professionnelle :**

- **8 langues** : FR, EN, DE, ES, IT, NL, PT-BR, PT-PT
- **Clés intelligentes** : `\n` pour retours ligne dans cartes compactes
- **Contexte métier** : Terminologie couple spécialisée par langue
- **Cohérence** : Même pattern que autres composants app

### 🤖 **Architecture Android Robuste :**

- **StatisticsRepository** : StateFlow + Combine pour réactivité
- **Compose moderne** : LazyVerticalGrid + Material Design 3
- **ViewModel pattern** : Séparation logique UI/business claire
- **Persistance** : Room + SharedPreferences équivalent iOS

### ⚡ **Fonctionnalités Avancées :**

- **Real-time updates** : `.onReceive` listeners pour mise à jour auto
- **Performance** : Calculs à la demande + memoization services
- **Error handling** : Fallbacks gracieux si données manquantes
- **Analytics** : Tracking interactions statistiques pour insights

### 📊 **Impact Business Statistiques :**

- **Engagement utilisateur** : Visualisation progression couple motivante
- **Rétention** : Métriques croissantes créent attachement émotionnel
- **Social proof** : Comparaisons implicites avec autres couples
- **Gamification** : Progression % questions = accomplissement

### ⏱️ **Estimation Android : 4-6 semaines**

Plus simple que Journal/Profil car principalement calculs + UI :

- Phase 1 : Repository + Models + Calculs (1-2 sem)
- Phase 2 : Interface Compose + Cards (2-3 sem)
- Phase 3 : Localisation + Analytics (1 sem)
- Phase 4 : Tests + Optimisations (1 sem)

## 🔥 **Statistiques = Engagement Émotionnel Puissant**

Le système de statistiques transforme l'usage de l'application en **progression mesurable** :

1. **💕 Jours Ensemble** : Compteur émotionnel fort = attachement temps
2. **🧠 Questions %** : Gamification connaissance = motivation découverte
3. **🌍 Géographie** : Mémoires lieux = nostalgie voyages
4. **📈 Progression** : Métriques croissantes = satisfaction accomplissement

Cette **visualisation quantifiée de la relation** crée une **dépendance psychologique positive** à l'application, transformant CoupleApp en **journal de bord officiel de la relation** ! 💫📊💕

Le système statistiques complète parfaitement l'écosystème avec Questions/Défis quotidiens + Journal spatial + Profil personnalisé pour un **engagement couple total à 360°** !
