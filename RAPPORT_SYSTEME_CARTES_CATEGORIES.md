# Rapport : Système de Cartes et Catégories - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille l'architecture complète du système de cartes et catégories dans l'application iOS CoupleApp, incluant la navigation, l'affichage des cartes, le système de déblocage par packs de 32 cartes, le paywall freemium, les clés de localisation, et les recommandations pour l'adaptation Android.

---

## 🏗️ Architecture Générale du Système

```
┌─────────────────────────────────────────────────────────────────┐
│                    FLUX COMPLET DU SYSTÈME                     │
└─────────────────────────────────────────────────────────────────┘

1. PAGE PRINCIPALE
   ┌─────────────────────┐
   │  CategoryListCard   │ ──► Tap sur catégorie
   │  (8 catégories)     │
   └─────────────────────┘
              │
              ▼
2. GESTION FREEMIUM
   ┌─────────────────────┐
   │  FreemiumManager    │ ──► handleCategoryTap()
   │  - Vérifie premium  │     • Si premium + non abonné → Paywall
   │  - Vérifie abonnement │     • Sinon → QuestionListView
   └─────────────────────┘
              │
              ▼
3. AFFICHAGE CARTES
   ┌─────────────────────┐
   │  QuestionListView   │ ──► Affiche cartes par packs de 32
   │  - Swipe horizontal │     • Questions normales
   │  - System de packs │     • Carte déblocage pack (32ème)
   │  - Limite freemium  │     • Carte paywall freemium
   └─────────────────────┘
              │
              ▼
4. DÉBLOCAGE PACK
   ┌─────────────────────┐
   │ PackProgressService │ ──► Gère déblocage 32 cartes
   │ + PackCompletion    │     • NewPackRevealView
   │ + NewPackReveal     │     • Animation + "C'est parti !"
   └─────────────────────┘
```

---

## 📊 1. Système de Catégories - QuestionCategory

### 1.1 Structure des Catégories

**8 Catégories Disponibles :**

| ID                    | Titre               | Emoji | Status      | Questions | Description                              |
| --------------------- | ------------------- | ----- | ----------- | --------- | ---------------------------------------- |
| `en-couple`           | En Couple           | 💞    | **Gratuit** | ~300+     | Questions sur la relation (64 gratuites) |
| `les-plus-hots`       | Désirs Inavoués     | 🌶️    | **Premium** | ~200+     | Questions intimes                        |
| `a-distance`          | À Distance          | ✈️    | **Premium** | ~150+     | Relation longue distance                 |
| `questions-profondes` | Questions Profondes | ✨    | **Premium** | ~250+     | Réflexions profondes                     |
| `pour-rire-a-deux`    | Pour Rire           | 😂    | **Premium** | ~180+     | Questions amusantes                      |
| `tu-preferes`         | Tu Préfères         | 🤍    | **Premium** | ~200+     | Dilemmes et choix                        |
| `mieux-ensemble`      | Mieux Ensemble      | 💌    | **Premium** | ~160+     | Améliorer la relation                    |
| `pour-un-date`        | Pour un Date        | 🍸    | **Premium** | ~140+     | Questions de rendez-vous                 |

### 1.2 Modèle de Données

```swift
struct QuestionCategory: Identifiable, Codable {
    var id: String              // ID constant (ex: "en-couple")
    let title: String           // Titre localisé
    let subtitle: String        // Sous-titre localisé
    let emoji: String          // Émoji de la catégorie
    let gradientColors: [String] // Couleurs pour UI
    let isPremium: Bool        // Status premium
}
```

**Stockage des Questions :**

- Fichiers JSON locaux : `EnCouple.xcstrings`, `LesPlus Hots.xcstrings`, etc.
- Chargement via `QuestionDataManager.shared.loadQuestions(for: categoryId)`
- Cache intelligent avec `QuestionCacheManager`

---

## 🎯 2. Clic sur Collection - Logique FreemiumManager

### 2.1 Flux de Navigation

```swift
// CategoryListCardView.swift - Gestion du tap
Button(action: {
    if let freemiumManager = appState.freemiumManager {
        freemiumManager.handleCategoryTap(category) {
            // Succès → Ouvre QuestionListView
            activeSheet = .questions(category)
        }
    } else {
        // Fallback direct
        activeSheet = .questions(category)
    }
})
```

### 2.2 Logique FreemiumManager.handleCategoryTap()

```swift
func handleCategoryTap(_ category: QuestionCategory, onSuccess: @escaping () -> Void) {
    let isSubscribed = appState?.currentUser?.isSubscribed ?? false

    // 1. Utilisateur abonné → Accès illimité
    if isSubscribed {
        onSuccess()
        return
    }

    // 2. Catégorie Premium + Non abonné → Paywall
    if category.isPremium {
        blockedCategoryAttempt = category
        showingSubscription = true

        // Analytics tracking
        Analytics.logEvent("paywall_affiche", parameters: [
            "source": "freemium_limite"
        ])

        NotificationCenter.default.post(name: .freemiumManagerChanged, object: nil)
        return
    }

    // 3. Catégorie gratuite → Accès autorisé (limitation dans QuestionListView)
    onSuccess()
}
```

### 2.3 Affichage des Restrictions

**Indicateur Premium :**

```swift
// CategoryListCardView.swift - Cadenas pour premium
if category.isPremium && !(appState.currentUser?.isSubscribed ?? false) {
    Text("🔒")
        .font(.system(size: 14))
}
```

---

## 🃏 3. Affichage des Cartes - QuestionListView

### 3.1 Architecture de l'Affichage

```swift
struct QuestionListView: View {
    // États principaux
    @State private var currentIndex = 0
    @State private var cachedQuestions: [Question] = []
    @State private var accessibleQuestions: [Question] = []

    // États pour pack/paywall
    @State private var showPackCompletionCard = false
    @State private var showNewPackReveal = false
    @State private var completedPackNumber = 1
}
```

### 3.2 Système de Rendu Optimisé

**Affichage Maximum 3 Cartes :**

```swift
private var visibleQuestions: [(Int, Question)] {
    let startIndex = max(0, currentIndex - 1)
    let endIndex = min(accessibleQuestions.count - 1, currentIndex + 1)

    var result: [(Int, Question)] = []
    for i in startIndex...endIndex {
        result.append((i, accessibleQuestions[i]))
    }
    return result
}
```

**Rendu avec Animation :**

```swift
ForEach(visibleQuestions, id: \.0) { indexAndQuestion in
    let (index, question) = indexAndQuestion
    let offset = CGFloat(index - currentQuestionIndex)
    let xPosition = offset * (cardWidth + cardSpacing) + dragOffset.width

    QuestionCardView(
        question: question,
        category: category,
        isBackground: index != currentQuestionIndex
    )
    .frame(width: cardWidth)
    .offset(x: xPosition)
    .scaleEffect(index == currentQuestionIndex ? 1.0 : 0.95)
    .opacity(index == currentQuestionIndex ? 1.0 : 0.8)
}
```

### 3.3 Types de Cartes Affichées

1. **Cartes Questions Normales** - `QuestionCardView`
2. **Carte Déblocage Pack** - `PackCompletionCardView` (32ème question)
3. **Carte Paywall Freemium** - `FreemiumPaywallCardView` (limite gratuite atteinte)

---

## 🔓 4. Système de Déblocage - Packs de 32 Cartes

### 4.1 PackProgressService - Gestion des Packs

```swift
class PackProgressService: ObservableObject {
    private let questionsPerPack = 32  // 32 questions par pack
    @Published private var packProgress: [String: Int] = [:]

    // Obtenir questions disponibles selon packs débloqués
    func getAvailableQuestionsCount(for categoryId: String) -> Int {
        let unlockedPacks = getUnlockedPacks(for: categoryId)
        return unlockedPacks * questionsPerPack
    }

    // Vérifier fin de pack (question 32, 64, 96...)
    func checkPackCompletion(categoryId: String, currentIndex: Int) -> Bool {
        let isLastQuestionOfPack = (currentIndex + 1) % questionsPerPack == 0
        return isLastQuestionOfPack
    }

    // Débloquer pack suivant
    func unlockNextPack(for categoryId: String) {
        let currentUnlockedPacks = getUnlockedPacks(for: categoryId)
        packProgress[categoryId] = currentUnlockedPacks + 1
        saveProgress()
    }
}
```

### 4.2 PackCompletionCardView - Carte de Déblocage

**Apparition de la Carte :**

```swift
// QuestionListView.swift - Logique d'affichage
private func checkForPackCompletionCard() {
    // Afficher à l'avant-dernière question du pack (31ème)
    if currentIndex == accessibleQuestions.count - 1 &&
       accessibleQuestions.count < cachedQuestions.count {

        completedPackNumber = (accessibleQuestions.count - 1) / 32 + 1
        showPackCompletionCard = true
    }
}
```

**Design de la Carte :**

```swift
struct PackCompletionCardView: View {
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 30) {
                VStack(spacing: 20) {
                    Text("congratulations_pack".localized)     // "Félicitations !"
                        .font(.system(size: 36, weight: .bold))

                    Text("pack_completed".localized)          // "Pack terminé"
                        .font(.system(size: 18))

                    Text("🔥")  // Animation de flamme
                        .font(.system(size: 60))
                        .scaleEffect(flameAnimation ? 1.3 : 0.9)

                    Text("tap_unlock_surprise".localized)     // "Tape pour débloquer..."
                        .font(.system(size: 16, weight: .medium))
                }
            }
            // Dégradé rouge/orange background
        }
    }
}
```

### 4.3 NewPackRevealView - Animation de Déblocage

**Écran Full Screen :**

```swift
struct NewPackRevealView: View {
    let packNumber: Int
    let questionsCount: Int = 32

    var body: some View {
        ZStack {
            // Fond dégradé rouge/orange
            LinearGradient(...)

            VStack(spacing: 50) {
                Text("💌")  // Emoji enveloppe
                    .font(.system(size: 80))
                    .scaleEffect(showContent ? 1.0 : 0.5)

                VStack(spacing: 20) {
                    Text("new_cards_added".localized)      // "Nouvelles cartes ajoutées"
                    Text("\(questionsCount) " + "new_cards".localized)  // "32 nouvelles cartes"
                    Text("enjoy_it".localized)             // "Profitez-en bien !"
                }

                Button(action: onContinue) {
                    Text("lets_go".localized)              // "C'est parti !"
                }
            }
        }
    }
}
```

### 4.4 Flux Complet de Déblocage

```
Question 31 → PackCompletionCard visible au swipe
     ↓ (tap sur carte)
NewPackRevealView (full screen)
     ↓ (tap "C'est parti !")
PackProgressService.unlockNextPack()
     ↓
Rechargement 32 nouvelles questions (pack 2)
     ↓
Navigation vers question 33
```

---

## 💰 5. Système Paywall Freemium

### 5.1 Limites Freemium

```swift
class FreemiumManager {
    private let freePacksLimit = 2              // 2 packs gratuits (64 questions)
    private let questionsPerPack = 32           // 32 questions par pack
    private let freeJournalEntriesLimit = 5     // 5 entrées journal gratuites

    func getMaxFreeQuestions(for category: QuestionCategory) -> Int {
        if category.isPremium {
            return 0  // Aucune question gratuite pour premium
        }

        if category.id == "en-couple" {
            return freePacksLimit * questionsPerPack  // 64 questions
        }

        return Int.max  // Autres catégories gratuites (illimitées)
    }
}
```

### 5.2 FreemiumPaywallCardView - Carte de Paywall

**Affichage Conditionnel :**

```swift
// QuestionListView.swift
private var shouldShowFreemiumPaywallPreview: Bool {
    guard let freemiumManager = appState.freemiumManager else { return false }
    let maxFreeQuestions = freemiumManager.getMaxFreeQuestions(for: category)
    let isSubscribed = appState.currentUser?.isSubscribed ?? false

    return !isSubscribed &&
           !category.isPremium &&
           cachedQuestions.count > maxFreeQuestions &&
           accessibleQuestions.count >= maxFreeQuestions
}
```

**Design de la Carte :**

```swift
struct FreemiumPaywallCardView: View {
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 30) {
                VStack(spacing: 20) {
                    Text(category.emoji)                   // Émoji catégorie
                        .font(.system(size: 60))

                    Text("congratulations".localized)      // "Félicitations"
                        .font(.system(size: 32, weight: .bold))

                    Text("keep_going_unlock_all".localized) // "Continuez et débloquez tout"
                        .font(.system(size: 18))

                    HStack(spacing: 8) {
                        Text("continue".localized)          // "Continuer"
                        Image(systemName: "arrow.right.circle.fill")
                    }
                }
            }
            // Dégradé background selon catégorie
        }
    }
}
```

### 5.3 Gestion Tap Paywall

```swift
// QuestionListView.swift
private func handlePaywallTap() {
    appState.freemiumManager?.handleQuestionAccess(at: currentIndex, in: category) {
        // Cette closure ne devrait jamais être appelée (accès bloqué)
    }
}
```

---

## 🌐 6. Clés de Localisation (XCStrings)

### 6.1 Clés pour Pack Completion

**Fichier : `UI.xcstrings`**

```json
{
  "congratulations_pack": {
    "comment": "UI Strings - Pack Completion",
    "extractionState": "manual",
    "localizations": {
      "en": {
        "stringUnit": { "state": "translated", "value": "Congratulations!" }
      },
      "fr": {
        "stringUnit": { "state": "translated", "value": "Félicitations !" }
      },
      "de": {
        "stringUnit": {
          "state": "translated",
          "value": "Herzlichen Glückwunsch!"
        }
      },
      "es": {
        "stringUnit": { "state": "translated", "value": "¡Felicitaciones!" }
      }
    }
  },

  "pack_completed": {
    "extractionState": "manual",
    "localizations": {
      "en": {
        "stringUnit": { "state": "translated", "value": "Pack completed" }
      },
      "fr": {
        "stringUnit": { "state": "translated", "value": "Pack terminé" }
      },
      "de": {
        "stringUnit": { "state": "translated", "value": "Paket abgeschlossen" }
      },
      "es": {
        "stringUnit": { "state": "translated", "value": "Paquete completado" }
      }
    }
  },

  "tap_unlock_surprise": {
    "localizations": {
      "en": { "stringUnit": { "value": "Tap to unlock the surprise" } },
      "fr": { "stringUnit": { "value": "Tapez pour débloquer la surprise" } },
      "de": {
        "stringUnit": {
          "value": "Tippen Sie, um die Überraschung freizuschalten"
        }
      },
      "es": { "stringUnit": { "value": "Toca para desbloquear la sorpresa" } }
    }
  }
}
```

### 6.2 Clés pour New Pack Reveal

```json
{
  "new_cards_added": {
    "extractionState": "manual",
    "localizations": {
      "en": { "stringUnit": { "value": "New cards added" } },
      "fr": { "stringUnit": { "value": "Nouvelles cartes ajoutées" } },
      "de": { "stringUnit": { "value": "Neue Karten hinzugefügt" } },
      "es": { "stringUnit": { "value": "Nuevas cartas añadidas" } }
    }
  },

  "new_cards": {
    "extractionState": "manual",
    "localizations": {
      "en": { "stringUnit": { "value": "new cards" } },
      "fr": { "stringUnit": { "value": "nouvelles cartes" } },
      "de": { "stringUnit": { "value": "neue Karten" } },
      "es": { "stringUnit": { "value": "cartas nuevas" } }
    }
  },

  "enjoy_it": {
    "extractionState": "manual",
    "localizations": {
      "en": { "stringUnit": { "value": "Enjoy it!" } },
      "fr": { "stringUnit": { "value": "Profitez-en bien !" } },
      "de": { "stringUnit": { "value": "Viel Spaß!" } },
      "es": { "stringUnit": { "value": "¡Disfrútenlo!" } }
    }
  },

  "lets_go": {
    "extractionState": "manual",
    "localizations": {
      "en": { "stringUnit": { "value": "Let's go!" } },
      "fr": { "stringUnit": { "value": "C'est parti !" } },
      "de": { "stringUnit": { "value": "Los geht's!" } },
      "es": { "stringUnit": { "value": "¡Vamos!" } }
    }
  }
}
```

### 6.3 Clés pour Paywall Freemium

```json
{
  "congratulations": {
    "extractionState": "manual",
    "localizations": {
      "en": { "stringUnit": { "value": "Congratulations" } },
      "fr": { "stringUnit": { "value": "Félicitations" } },
      "de": { "stringUnit": { "value": "Herzlichen Glückwunsch" } },
      "es": { "stringUnit": { "value": "Felicitaciones" } }
    }
  },

  "keep_going_unlock_all": {
    "extractionState": "manual",
    "localizations": {
      "en": { "stringUnit": { "value": "Keep going and unlock everything" } },
      "fr": { "stringUnit": { "value": "Continuez et débloquez tout" } },
      "de": {
        "stringUnit": { "value": "Weitermachen und alles freischalten" }
      },
      "es": { "stringUnit": { "value": "Continúa y desbloquea todo" } }
    }
  },

  "continue": {
    "comment": "Common UI strings",
    "extractionState": "manual",
    "localizations": {
      "en": { "stringUnit": { "value": "Continue" } },
      "fr": { "stringUnit": { "value": "Continuer" } },
      "de": { "stringUnit": { "value": "Weiter" } },
      "es": { "stringUnit": { "value": "Continuar" } }
    }
  }
}
```

### 6.4 Clés pour Catégories

**Fichier : `Categories.xcstrings`**

```json
{
  "category_en_couple_title": {
    "localizations": {
      "en": { "stringUnit": { "value": "In a Relationship" } },
      "fr": { "stringUnit": { "value": "En couple" } },
      "de": { "stringUnit": { "value": "In einer Beziehung" } },
      "es": { "stringUnit": { "value": "En pareja" } }
    }
  },

  "category_en_couple_subtitle": {
    "localizations": {
      "en": { "stringUnit": { "value": "Strengthen your bond" } },
      "fr": { "stringUnit": { "value": "Renforcez votre lien" } },
      "de": { "stringUnit": { "value": "Stärken Sie Ihre Bindung" } },
      "es": { "stringUnit": { "value": "Fortalece tu vínculo" } }
    }
  },

  "category_desirs_inavoues_title": {
    "localizations": {
      "en": { "stringUnit": { "value": "Unspoken Desires" } },
      "fr": { "stringUnit": { "value": "Désirs inavoués" } },
      "de": { "stringUnit": { "value": "Unausgesprochene Wünsche" } },
      "es": { "stringUnit": { "value": "Deseos no confesados" } }
    }
  }
}
```

---

## 🤖 7. Adaptation Android - Implémentation Complète

### 7.1 Architecture Android Équivalente

```kotlin
// Modèle de données Android équivalent
data class QuestionCategory(
    val id: String,
    val titleResId: Int,        // Resource ID pour titre
    val subtitleResId: Int,     // Resource ID pour sous-titre
    val emoji: String,
    val gradientColors: List<String>,
    val isPremium: Boolean
) {
    companion object {
        val categories = listOf(
            QuestionCategory(
                id = "en-couple",
                titleResId = R.string.category_en_couple_title,
                subtitleResId = R.string.category_en_couple_subtitle,
                emoji = "💞",
                gradientColors = listOf("#E91E63", "#F06292"),
                isPremium = false
            ),
            QuestionCategory(
                id = "les-plus-hots",
                titleResId = R.string.category_desirs_inavoues_title,
                subtitleResId = R.string.category_desirs_inavoues_subtitle,
                emoji = "🌶️",
                gradientColors = listOf("#FF6B35", "#F7931E"),
                isPremium = true
            )
            // ... autres catégories
        )
    }
}

data class Question(
    val id: String,
    val textResId: Int,    // Resource ID pour texte localisé
    val categoryId: String
)
```

### 7.2 FreemiumManager Android

```kotlin
class FreemiumManager(private val context: Context) {
    private val freePacksLimit = 2
    private val questionsPerPack = 32

    private val _showingSubscription = MutableStateFlow(false)
    val showingSubscription: StateFlow<Boolean> = _showingSubscription

    private val _blockedCategoryAttempt = MutableStateFlow<QuestionCategory?>(null)
    val blockedCategoryAttempt: StateFlow<QuestionCategory?> = _blockedCategoryAttempt

    fun handleCategoryTap(
        category: QuestionCategory,
        isSubscribed: Boolean,
        onSuccess: () -> Unit
    ) {
        // Utilisateur abonné → Accès illimité
        if (isSubscribed) {
            onSuccess()
            return
        }

        // Catégorie Premium + Non abonné → Paywall
        if (category.isPremium) {
            _blockedCategoryAttempt.value = category
            _showingSubscription.value = true

            // Analytics Firebase
            Firebase.analytics.logEvent("paywall_affiche") {
                param("source", "freemium_limite")
            }
            return
        }

        // Catégorie gratuite → Accès autorisé
        onSuccess()
    }

    fun getMaxFreeQuestions(category: QuestionCategory): Int {
        return when {
            category.isPremium -> 0
            category.id == "en-couple" -> freePacksLimit * questionsPerPack
            else -> Int.MAX_VALUE
        }
    }

    fun dismissSubscription() {
        _showingSubscription.value = false
        _blockedCategoryAttempt.value = null
    }
}
```

### 7.3 QuestionListScreen Android

```kotlin
@Composable
fun QuestionListScreen(
    category: QuestionCategory,
    freemiumManager: FreemiumManager = LocalFreemiumManager.current,
    packProgressService: PackProgressService = LocalPackProgressService.current
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var accessibleQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var showPackCompletion by remember { mutableStateOf(false) }
    var showNewPackReveal by remember { mutableStateOf(false) }
    var completedPackNumber by remember { mutableIntStateOf(1) }

    // Chargement questions
    LaunchedEffect(category.id) {
        questions = QuestionDataManager.loadQuestions(category.id)
        accessibleQuestions = packProgressService.getAccessibleQuestions(questions, category.id)
    }

    // État paywall freemium
    val shouldShowFreemiumPaywall = remember(currentIndex, accessibleQuestions.size) {
        val maxFreeQuestions = freemiumManager.getMaxFreeQuestions(category)
        !category.isPremium &&
        questions.size > maxFreeQuestions &&
        accessibleQuestions.size >= maxFreeQuestions
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Affichage cartes avec swipe
        HorizontalPager(
            state = rememberPagerState {
                accessibleQuestions.size +
                (if (showPackCompletion) 1 else 0) +
                (if (shouldShowFreemiumPaywall) 1 else 0)
            },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when {
                page < accessibleQuestions.size -> {
                    // Carte question normale
                    QuestionCard(
                        question = accessibleQuestions[page],
                        category = category
                    )
                }
                page == accessibleQuestions.size && showPackCompletion -> {
                    // Carte déblocage pack
                    PackCompletionCard(
                        category = category,
                        packNumber = completedPackNumber,
                        onTap = { showNewPackReveal = true }
                    )
                }
                shouldShowFreemiumPaywall -> {
                    // Carte paywall freemium
                    FreemiumPaywallCard(
                        category = category,
                        onTap = {
                            freemiumManager.handleQuestionAccess(currentIndex, category) {}
                        }
                    )
                }
            }
        }
    }

    // Sheet New Pack Reveal
    if (showNewPackReveal) {
        NewPackRevealSheet(
            packNumber = completedPackNumber + 1,
            onContinue = {
                showNewPackReveal = false
                packProgressService.unlockNextPack(category.id)
                // Recharger questions accessibles
                accessibleQuestions = packProgressService.getAccessibleQuestions(questions, category.id)
                showPackCompletion = false
            }
        )
    }
}
```

### 7.4 Cartes Android - Composants

#### CategoryCard

```kotlin
@Composable
fun CategoryCard(
    category: QuestionCategory,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSubscribed = LocalSubscriptionStatus.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clickable { onTap() },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.titleResId),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(category.subtitleResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    if (category.isPremium && !isSubscribed) {
                        Text(
                            text = "🔒",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = category.emoji,
                fontSize = 28.sp
            )
        }
    }
}
```

#### PackCompletionCard

```kotlin
@Composable
fun PackCompletionCard(
    category: QuestionCategory,
    packNumber: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var flameAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        flameAnimation = true
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFfd267a),
                            Color(0xFFff655b)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(30.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.congratulations_pack),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.pack_completed),
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )

                    // Animation flamme
                    Text(
                        text = "🔥",
                        fontSize = 60.sp,
                        modifier = Modifier
                            .scale(if (flameAnimation) 1.3f else 0.9f)
                            .rotate(if (flameAnimation) 15f else -15f)
                            .offset(y = if (flameAnimation) (-5).dp else 5.dp)
                    )

                    Text(
                        text = stringResource(R.string.tap_unlock_surprise),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}
```

#### NewPackRevealSheet

```kotlin
@Composable
fun NewPackRevealSheet(
    packNumber: Int,
    onContinue: () -> Unit
) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFfd267a),
                        Color(0xFFff655b)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(50.dp)
        ) {
            Text(
                text = "💌",
                fontSize = 80.sp,
                modifier = Modifier
                    .scale(if (showContent) 1.0f else 0.5f)
                    .animateContentSize()
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.new_cards_added),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "32 ${stringResource(R.string.new_cards)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.enjoy_it),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp)
            ) {
                Text(
                    text = stringResource(R.string.lets_go),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF990033)
                )
            }
        }
    }
}
```

### 7.5 PackProgressService Android

```kotlin
class PackProgressService(private val context: Context) {
    companion object {
        private const val QUESTIONS_PER_PACK = 32
        private const val PACK_PROGRESS_KEY = "PackProgressKey"
    }

    private val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val _packProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val packProgress: StateFlow<Map<String, Int>> = _packProgress

    init {
        loadProgress()
    }

    fun getUnlockedPacks(categoryId: String): Int {
        return _packProgress.value[categoryId] ?: 1
    }

    fun getAvailableQuestionsCount(categoryId: String): Int {
        val unlockedPacks = getUnlockedPacks(categoryId)
        return unlockedPacks * QUESTIONS_PER_PACK
    }

    fun getAccessibleQuestions(questions: List<Question>, categoryId: String): List<Question> {
        val maxQuestions = getAvailableQuestionsCount(categoryId)
        return questions.take(maxQuestions)
    }

    fun checkPackCompletion(categoryId: String, currentIndex: Int): Boolean {
        val currentPack = getCurrentPack(currentIndex)
        val unlockedPacks = getUnlockedPacks(categoryId)

        val isLastQuestionOfPack = (currentIndex + 1) % QUESTIONS_PER_PACK == 0
        val isCurrentPackCompleted = currentPack <= unlockedPacks

        return isLastQuestionOfPack && isCurrentPackCompleted
    }

    fun unlockNextPack(categoryId: String) {
        val currentUnlockedPacks = getUnlockedPacks(categoryId)
        val newProgress = _packProgress.value.toMutableMap()
        newProgress[categoryId] = currentUnlockedPacks + 1
        _packProgress.value = newProgress
        saveProgress()

        // Analytics
        Firebase.analytics.logEvent("pack_complete") {
            param("categorie", categoryId)
            param("pack_numero", (currentUnlockedPacks + 1).toLong())
        }
    }

    private fun getCurrentPack(questionIndex: Int): Int {
        return (questionIndex / QUESTIONS_PER_PACK) + 1
    }

    private fun loadProgress() {
        val progressJson = sharedPrefs.getString(PACK_PROGRESS_KEY, "{}")
        // Parse JSON et mettre à jour _packProgress
    }

    private fun saveProgress() {
        val progressJson = Gson().toJson(_packProgress.value)
        sharedPrefs.edit().putString(PACK_PROGRESS_KEY, progressJson).apply()
    }
}
```

### 7.6 Ressources Strings Android

**Fichier : `res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Catégories -->
    <string name="category_en_couple_title">En couple</string>
    <string name="category_en_couple_subtitle">Renforcez votre lien</string>
    <string name="category_desirs_inavoues_title">Désirs inavoués</string>
    <string name="category_desirs_inavoues_subtitle">Questions intimes</string>

    <!-- Pack Completion -->
    <string name="congratulations_pack">Félicitations !</string>
    <string name="pack_completed">Pack terminé</string>
    <string name="tap_unlock_surprise">Tapez pour débloquer la surprise</string>

    <!-- New Pack Reveal -->
    <string name="new_cards_added">Nouvelles cartes ajoutées</string>
    <string name="new_cards">nouvelles cartes</string>
    <string name="enjoy_it">Profitez-en bien !</string>
    <string name="lets_go">C\'est parti !</string>

    <!-- Paywall -->
    <string name="congratulations">Félicitations</string>
    <string name="keep_going_unlock_all">Continuez et débloquez tout</string>
    <string name="continue">Continuer</string>
</resources>
```

**Fichier : `res/values-en/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Categories -->
    <string name="category_en_couple_title">In a Relationship</string>
    <string name="category_en_couple_subtitle">Strengthen your bond</string>
    <string name="category_desirs_inavoues_title">Unspoken Desires</string>
    <string name="category_desirs_inavoues_subtitle">Intimate questions</string>

    <!-- Pack Completion -->
    <string name="congratulations_pack">Congratulations!</string>
    <string name="pack_completed">Pack completed</string>
    <string name="tap_unlock_surprise">Tap to unlock the surprise</string>

    <!-- New Pack Reveal -->
    <string name="new_cards_added">New cards added</string>
    <string name="new_cards">new cards</string>
    <string name="enjoy_it">Enjoy it!</string>
    <string name="lets_go">Let\'s go!</string>

    <!-- Paywall -->
    <string name="congratulations">Congratulations</string>
    <string name="keep_going_unlock_all">Keep going and unlock everything</string>
    <string name="continue">Continue</string>
</resources>
```

### 7.7 Integration Android Complète

**MainActivity Setup :**

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CoupleAppTheme {
                val freemiumManager = remember { FreemiumManager(this@MainActivity) }
                val packProgressService = remember { PackProgressService(this@MainActivity) }

                CompositionLocalProvider(
                    LocalFreemiumManager provides freemiumManager,
                    LocalPackProgressService provides packProgressService
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

// CompositionLocal providers
val LocalFreemiumManager = compositionLocalOf<FreemiumManager> {
    error("FreemiumManager not provided")
}
val LocalPackProgressService = compositionLocalOf<PackProgressService> {
    error("PackProgressService not provided")
}
```

---

## 📋 Conclusion

Le système de cartes et catégories de CoupleApp iOS présente une architecture sophistiquée avec :

### 🎯 **Points Forts du Système :**

- **8 catégories** avec système freemium intelligent (1 gratuite, 7 premium)
- **Déblocage par packs de 32 cartes** avec animations
- **3 types de cartes** : Questions, Déblocage, Paywall
- **Cache multi-niveaux** pour performance optimale
- **Localisation complète** 5 langues (français, anglais, allemand, espagnol, etc.)

### 🔧 **Composants Techniques :**

- `FreemiumManager` - Gestion logique premium/gratuit
- `PackProgressService` - Déblocage packs de 32 questions
- `QuestionListView` - Affichage cartes avec swipe horizontal optimisé
- Cartes spécialisées : `PackCompletionCardView`, `NewPackRevealView`, `FreemiumPaywallCardView`

### 🤖 **Adaptation Android :**

- **Architecture équivalente** avec Jetpack Compose
- **Paging horizontal** avec `HorizontalPager`
- **StateFlow** pour gestion d'état
- **SharedPreferences** pour persistance
- **Resources strings** pour localisation
- **Firebase Analytics** intégré

### ⏱️ **Estimation Développement Android :**

- **Phase 1** : Modèles de données et FreemiumManager (1-2 semaines)
- **Phase 2** : QuestionListScreen et cartes de base (2-3 semaines)
- **Phase 3** : PackProgressService et déblocage (2-3 semaines)
- **Phase 4** : Animations et polish UI (1-2 semaines)

**Total estimé : 6-10 semaines** pour une réplication complète du système iOS vers Android.

L'adaptation Android est **techniquement réalisable** avec Jetpack Compose et suit fidèlement l'architecture iOS existante tout en respectant les conventions Android natives.
