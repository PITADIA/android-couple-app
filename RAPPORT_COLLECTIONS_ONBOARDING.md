# 📚 PAGE COLLECTIONS ONBOARDING - Design & Animations

## 🎯 Vue d'Ensemble

Page d'onboarding qui présente toutes les collections de questions avec un effet d'apparition progressif en cascade et des animations spring élégantes.

---

## 📱 Structure de la Vue (`CategoriesPreviewStepView`)

### 1. Architecture Générale

```swift
struct CategoriesPreviewStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var visibleCategories: Set<String> = []
    @State private var currentCategoryIndex = 0

    private let animationInterval: TimeInterval = 0.3
}
```

### 2. Layout Principal

```swift
VStack(spacing: 0) {
    // Espace haut (40pt)
    Spacer().frame(height: 40)

    // Titre principal
    HStack {
        Text("more_than_2000_questions".localized)
            .font(.system(size: 28, weight: .bold))
            .foregroundColor(.black)
        Spacer()
    }
    .padding(.horizontal, 30)

    // Espace titre-cartes (60pt)
    Spacer().frame(height: 60)

    // ScrollView avec cartes animées
    ScrollView {
        LazyVStack(spacing: 12) {
            ForEach(QuestionCategory.categories) { category in
                CategoryPreviewCard(category: category)
                    .opacity(visibleCategories.contains(category.id) ? 1 : 0)
                    .scaleEffect(visibleCategories.contains(category.id) ? 1 : 0.8)
                    .animation(
                        .spring(response: 0.6, dampingFraction: 0.8, blendDuration: 0),
                        value: visibleCategories.contains(category.id)
                    )
            }
        }
        .padding(.horizontal, 20)
    }

    Spacer()

    // Zone bouton (fixe en bas)
    VStack {
        Button("continue".localized) { viewModel.nextStep() }
            .buttonStyle(PrimaryButtonStyle)
    }
    .padding(.vertical, 30)
    .background(Color.white)
    .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
}
```

---

## 🎬 Système d'Animation

### 1. Timing et Séquence

```swift
// Délai initial avant première animation
DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
    animateNextCategory()
}

// Animation en cascade (0.3s entre chaque carte)
private func animateNextCategory() {
    guard currentCategoryIndex < QuestionCategory.categories.count else { return }

    let category = QuestionCategory.categories[currentCategoryIndex]
    visibleCategories.insert(category.id)
    currentCategoryIndex += 1

    // Programmer prochaine animation
    DispatchQueue.main.asyncAfter(deadline: .now() + animationInterval) {
        animateNextCategory()
    }
}
```

### 2. Effets Visuels

```swift
// État initial → Final
.opacity(0 → 1)                    // Fade in
.scaleEffect(0.8 → 1.0)           // Scale up légèrement
.animation(.spring(
    response: 0.6,         // Durée spring (600ms)
    dampingFraction: 0.8,  // Amortissement (80%)
    blendDuration: 0       // Pas de blend
))
```

---

## 🎨 Design des Cartes (`CategoryPreviewCard`)

### 1. Structure Layout

```swift
HStack(spacing: 16) {
    // Contenu texte (gauche)
    VStack(alignment: .leading, spacing: 6) {
        // Titre principal
        Text(category.title)
            .font(.system(size: 20, weight: .bold))
            .foregroundColor(.black)

        // Sous-titre descriptif
        Text(category.subtitle)
            .font(.system(size: 14))
            .foregroundColor(.gray)
    }

    Spacer()

    // Emoji (droite)
    Text(category.emoji)
        .font(.system(size: 28))
}
```

### 2. Styling et Apparence

```swift
// Padding interne
.padding(.horizontal, 24)
.padding(.vertical, 20)

// Background avec ombre
.background(
    RoundedRectangle(cornerRadius: 16)
        .fill(Color.white.opacity(0.95))
        .shadow(
            color: Color.black.opacity(0.1),
            radius: 8,
            x: 0,
            y: 2
        )
)
```

### 3. Spécifications Design

- **Corner Radius:** 16pt
- **Padding Horizontal:** 24pt
- **Padding Vertical:** 20pt
- **Spacing entre cartes:** 12pt
- **Background:** Blanc 95% opacité
- **Ombre:** Noire 10% opacité, rayon 8pt, offset (0, 2)

---

## 🎨 Couleurs et Typographie

### 1. Fond de Page

```swift
Color(red: 0.97, green: 0.97, blue: 0.98) // #F7F7F8
```

### 2. Typographie

```swift
// Titre principal
.font(.system(size: 28, weight: .bold))
.foregroundColor(.black)

// Titre carte
.font(.system(size: 20, weight: .bold))
.foregroundColor(.black)

// Sous-titre carte
.font(.system(size: 14))
.foregroundColor(.gray)

// Emoji
.font(.system(size: 28))

// Bouton continuer
.font(.system(size: 18, weight: .semibold))
.foregroundColor(.white)
```

### 3. Couleur Accent

```swift
Color(hex: "#FD267A") // Rose principal de l'app
```

---

## 🔑 Clés XCStrings Utilisées

### 1. Clé Titre Principal

```xml
<!-- Dans UI.xcstrings -->
<string name="more_than_2000_questions">Plus de 1800 questions à poser à votre âme sœur</string>
```

**Traductions:**

- 🇫🇷 **FR:** "Plus de 1800 questions à poser à votre âme sœur"
- 🇬🇧 **EN:** "More than 1800 questions to ask your soulmate"
- 🇩🇪 **DE:** "Mehr als 1800 Fragen für deine:n Seelenverwandte:n"
- 🇪🇸 **ES:** "Más de 1800 preguntas para hacer a tu alma gemela"

### 2. Clé Bouton

```xml
<!-- Dans UI.xcstrings -->
<string name="continue">Continuer</string>
```

### 3. Clés Collections (dans Localizable.xcstrings)

#### Collection "Toi et Moi" 💞

```xml
<string name="category_en_couple_title">Toi et Moi</string>
<string name="category_en_couple_subtitle">Renforcer notre complicité</string>
```

**Traductions titre:**

- 🇫🇷 **FR:** "Toi et Moi"
- 🇬🇧 **EN:** "You and Me"
- 🇩🇪 **DE:** "Du und ich"
- 🇪🇸 **ES:** "Tú y yo"

#### Collection "Désirs Inavoués" 🌶️

```xml
<string name="category_desirs_inavoues_title">Désirs Inavoués</string>
<string name="category_desirs_inavoues_subtitle">Explorer notre intimité</string>
```

**Traductions titre:**

- 🇫🇷 **FR:** "Désirs Inavoués"
- 🇬🇧 **EN:** "Hidden Desires"
- 🇩🇪 **DE:** "Geheime Wünsche"
- 🇪🇸 **ES:** "Deseos ocultos"

#### Collection "Questions Profondes" ✨

```xml
<string name="category_questions_profondes_title">Questions Profondes</string>
<string name="category_questions_profondes_subtitle">Découvrir notre essence</string>
```

#### Collection "À Distance" ✈️

```xml
<string name="category_a_distance_title">À Distance</string>
<string name="category_a_distance_subtitle">Maintenir notre lien</string>
```

#### Collection "Pour Rire" 😂

```xml
<string name="category_pour_rire_title">Pour Rire à Deux</string>
<string name="category_pour_rire_subtitle">Moments de complicité</string>
```

#### Collection "Tu Préfères" 🤍

```xml
<string name="category_tu_preferes_title">Tu Préfères</string>
<string name="category_tu_preferes_subtitle">Choix amusants ensemble</string>
```

#### Collection "Mieux Ensemble" 💌

```xml
<string name="category_mieux_ensemble_title">Mieux Ensemble</string>
<string name="category_mieux_ensemble_subtitle">Guérir notre amour</string>
```

#### Collection "Pour un Date" 🍸

```xml
<string name="category_pour_un_date_title">Pour un Date</string>
<string name="category_pour_un_date_subtitle">Conversations de rendez-vous</string>
```

---

## 📊 Données des Collections

### 1. Structure QuestionCategory

```swift
struct QuestionCategory: Identifiable, Codable {
    var id: String
    let title: String           // Titre localisé
    let subtitle: String        // Sous-titre localisé
    let emoji: String           // Emoji représentatif
    let gradientColors: [String] // Couleurs dégradé (non utilisées dans cette vue)
    let isPremium: Bool         // Statut premium (non affiché dans cette vue)
}
```

### 2. Collections et Emojis

```swift
static let categories: [QuestionCategory] = [
    // 💞 Toi et Moi (Gratuit)
    QuestionCategory(id: "en-couple", emoji: "💞", isPremium: false),

    // 🌶️ Désirs Inavoués (Premium)
    QuestionCategory(id: "les-plus-hots", emoji: "🌶️", isPremium: true),

    // ✈️ À Distance (Premium)
    QuestionCategory(id: "a-distance", emoji: "✈️", isPremium: true),

    // ✨ Questions Profondes (Premium)
    QuestionCategory(id: "questions-profondes", emoji: "✨", isPremium: true),

    // 😂 Pour Rire (Premium)
    QuestionCategory(id: "pour-rire-a-deux", emoji: "😂", isPremium: true),

    // 🤍 Tu Préfères (Premium)
    QuestionCategory(id: "tu-preferes", emoji: "🤍", isPremium: true),

    // 💌 Mieux Ensemble (Premium)
    QuestionCategory(id: "mieux-ensemble", emoji: "💌", isPremium: true),

    // 🍸 Pour un Date (Premium)
    QuestionCategory(id: "pour-un-date", emoji: "🍸", isPremium: true)
]
```

---

## 🤖 Adaptation Android (Kotlin/Jetpack Compose)

### 1. Structure Vue Principale

```kotlin
@Composable
fun CategoriesPreviewScreen(
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visibleCategories by remember { mutableStateOf(setOf<String>()) }
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        delay(300) // Délai initial
        animateCategories(
            categories = QuestionCategory.categories,
            visibleCategories = { visibleCategories },
            updateVisible = { visibleCategories = it },
            currentIndex = { currentIndex },
            updateIndex = { currentIndex = it }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Titre
        Text(
            text = stringResource(R.string.more_than_2000_questions),
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            ),
            modifier = Modifier.padding(horizontal = 30.dp)
        )

        Spacer(modifier = Modifier.height(60.dp))

        // Liste scrollable des catégories
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(QuestionCategory.categories) { category ->
                CategoryPreviewCard(
                    category = category,
                    isVisible = visibleCategories.contains(category.id)
                )
            }
        }

        // Bouton fixe en bas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .shadow(
                    elevation = 10.dp,
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
                .padding(vertical = 30.dp, horizontal = 30.dp)
        ) {
            Button(
                onClick = onContinueClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD267A)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.continue_text),
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        }
    }
}
```

### 2. Composant Carte

```kotlin
@Composable
fun CategoryPreviewCard(
    category: QuestionCategory,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .scale(animatedScale),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Contenu texte
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(category.titleRes),
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )

                Text(
                    text = stringResource(category.subtitleRes),
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                )
            }

            // Emoji
            Text(
                text = category.emoji,
                style = TextStyle(fontSize = 28.sp)
            )
        }
    }
}
```

### 3. Animation en Cascade

```kotlin
suspend fun animateCategories(
    categories: List<QuestionCategory>,
    visibleCategories: () -> Set<String>,
    updateVisible: (Set<String>) -> Unit,
    currentIndex: () -> Int,
    updateIndex: (Int) -> Unit
) {
    repeat(categories.size) { index ->
        if (index < categories.size) {
            val category = categories[index]
            val newVisibleSet = visibleCategories() + category.id
            updateVisible(newVisibleSet)
            updateIndex(index + 1)

            // Attendre 300ms avant prochaine animation
            delay(300)
        }
    }
}
```

### 4. Clés Android (strings.xml)

```xml
<!-- Titre principal -->
<string name="more_than_2000_questions">Plus de 1800 questions à poser à votre âme sœur</string>

<!-- Bouton -->
<string name="continue_text">Continuer</string>

<!-- Collections -->
<string name="category_en_couple_title">Toi et Moi</string>
<string name="category_en_couple_subtitle">Renforcer notre complicité</string>

<string name="category_desirs_inavoues_title">Désirs Inavoués</string>
<string name="category_desirs_inavoues_subtitle">Explorer notre intimité</string>

<string name="category_questions_profondes_title">Questions Profondes</string>
<string name="category_questions_profondes_subtitle">Découvrir notre essence</string>

<string name="category_a_distance_title">À Distance</string>
<string name="category_a_distance_subtitle">Maintenir notre lien</string>

<string name="category_pour_rire_title">Pour Rire à Deux</string>
<string name="category_pour_rire_subtitle">Moments de complicité</string>

<string name="category_tu_preferes_title">Tu Préfères</string>
<string name="category_tu_preferes_subtitle">Choix amusants ensemble</string>

<string name="category_mieux_ensemble_title">Mieux Ensemble</string>
<string name="category_mieux_ensemble_subtitle">Guérir notre amour</string>

<string name="category_pour_un_date_title">Pour un Date</string>
<string name="category_pour_un_date_subtitle">Conversations de rendez-vous</string>
```

---

## 📋 Résumé Technique

### ✅ Animations iOS

- **Type:** Spring animation avec opacity + scale
- **Timing:** 0.3s entre chaque carte
- **Paramètres:** response: 0.6, damping: 0.8
- **Séquence:** Cascade automatique progressive

### ✅ Design

- **Background:** Gris clair (#F7F7F8)
- **Cartes:** Blanches avec ombre légère
- **Corner radius:** 16pt
- **Typography:** Système avec poids variés
- **Accent:** Rose (#FD267A)

### ✅ Contenu

- **8 Collections** au total
- **1 Collection gratuite** ("Toi et Moi")
- **7 Collections premium**
- **Emojis uniques** pour chaque collection
- **Titres et sous-titres localisés**

### ✅ Localisation

- **Fichiers:** `UI.xcstrings` et `Localizable.xcstrings`
- **Langues:** FR, EN, DE, ES, IT, NL, PT
- **Structure:** Clés descriptives avec commentaires

Cette page constitue un **aperçu engageant** des collections disponibles avec une **animation soignée** qui met en valeur le contenu riche de l'application.
