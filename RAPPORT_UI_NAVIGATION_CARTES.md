# 🃏 UI NAVIGATION - Interface Cartes Questions

## 🎨 Design Général (`QuestionListView.swift`)

### Background Principal

```swift
// Fond rouge sombre
Color(red: 0.15, green: 0.03, blue: 0.08)
    .ignoresSafeArea()
```

**Couleur :** RGB(38, 8, 20) - Rouge très foncé

---

## 🔝 Header Navigation

### Structure Header

```swift
HStack {
    // 1️⃣ Bouton Retour (Gauche)
    Button(action: { dismiss() }) {
        Image(systemName: "chevron.left")
            .font(.system(size: 20, weight: .medium))
            .foregroundColor(.white)
    }

    Spacer()

    // 2️⃣ Compteur Questions (Centre)
    Text("\(currentQuestionIndex + 1) " + "on_count".localized + " \(totalItems)")
        .font(.system(size: 18, weight: .semibold))
        .foregroundColor(.white)

    Spacer()

    // 3️⃣ Bouton Refresh (Droite)
    Button(action: { /* Reset logic */ }) {
        Image(systemName: "arrow.clockwise")
            .font(.system(size: 20, weight: .medium))
            .foregroundColor(.white)
    }
}
.padding(.horizontal, 20)
.padding(.top, 60)
.padding(.bottom, 40)
```

### Éléments Header

| Élément      | Position | Icône/Texte       | Couleur | Action         |
| ------------ | -------- | ----------------- | ------- | -------------- |
| **Retour**   | Gauche   | `chevron.left`    | Blanc   | `dismiss()`    |
| **Compteur** | Centre   | `"2 on_count 24"` | Blanc   | -              |
| **Refresh**  | Droite   | `arrow.clockwise` | Blanc   | Reset position |

### Key Localisée

```swift
"on_count" // "sur" (FR) / "of" (EN)
```

---

## 🔄 Fonction Refresh

### Logique Reset

```swift
Button(action: {
    // 1️⃣ Reset index position
    currentIndex = 0
    dragOffset = .zero
    showPackCompletionCard = false

    // 2️⃣ Reset progression packs
    packProgressService.resetProgress(for: category.id)
    categoryProgressService.saveCurrentIndex(0, for: category.id)

    // 3️⃣ Recharger questions accessibles
    accessibleQuestions = packProgressService.getAccessibleQuestions(
        from: cachedQuestions,
        categoryId: category.id
    )

    print("🔄 RESET COMPLET: Progression réinitialisée pour \(category.title)")
}) {
    Image(systemName: "arrow.clockwise")
}
```

**Action :** Remet l'utilisateur à la première carte + reset progression

---

## ❤️ Bouton Favoris (Bas)

### Structure Bouton

```swift
Button(action: {
    // Toggle favoris
    let currentQuestion = accessibleQuestions[currentQuestionIndex]
    favoritesService.toggleFavorite(question: currentQuestion, category: category)
}) {
    let isCurrentlyFavorite = favoritesService.isFavorite(questionId: currentQuestion.id)

    HStack(spacing: 12) {
        // Texte dynamique
        Text(isCurrentlyFavorite ? "remove_from_favorites".localized : "add_to_favorites".localized)
            .font(.system(size: 18, weight: .semibold))
            .foregroundColor(.white)

        // Icône cœur (vide/plein)
        Image(systemName: isCurrentlyFavorite ? "heart.fill" : "heart")
            .font(.system(size: 20, weight: .medium))
            .foregroundColor(.white)
    }
    .frame(maxWidth: .infinity)
    .frame(height: 56)
    .background(Color(red: 1.0, green: 0.4, blue: 0.6))  // Rose
    .cornerRadius(28)
    .scaleEffect(isCurrentlyFavorite ? 1.02 : 1.0)  // Animation micro
}
.padding(.horizontal, 20)
.padding(.bottom, 50)
```

### États Bouton Favoris

| État           | Texte                   | Icône        | Animation     |
| -------------- | ----------------------- | ------------ | ------------- |
| **Non favori** | "add_to_favorites"      | `heart`      | `scale(1.0)`  |
| **Favori**     | "remove_from_favorites" | `heart.fill` | `scale(1.02)` |

### Keys Utilisées

```swift
"add_to_favorites"      // "Ajouter aux favoris"
"remove_from_favorites" // "Retirer des favoris"
```

### Couleur Bouton

```swift
Color(red: 1.0, green: 0.4, blue: 0.6)  // RGB(255, 102, 153) - Rose
```

---

## 🤖 Adaptation Android (Kotlin/Compose)

### 1. Background

```kotlin
@Composable
fun QuestionNavigationScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF260814)) // RGB(38, 8, 20)
    ) {
        Column {
            NavigationHeader()
            QuestionCards()
            FavoriteButton()
        }
    }
}
```

### 2. Header Navigation

```kotlin
@Composable
fun NavigationHeader(
    currentIndex: Int,
    totalItems: Int,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 60.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton retour
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Retour",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Compteur
        Text(
            text = "${currentIndex + 1} ${stringResource(R.string.on_count)} $totalItems",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        // Bouton refresh
        IconButton(onClick = onRefreshClick) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Recommencer",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

### 3. Bouton Favoris

```kotlin
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.02f else 1.0f,
        animationSpec = tween(200)
    )

    Button(
        onClick = onToggleFavorite,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp)
            .padding(bottom = 50.dp)
            .scale(scale),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color(0xFFFF6699) // RGB(255, 102, 153)
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isFavorite) {
                    stringResource(R.string.remove_from_favorites)
                } else {
                    stringResource(R.string.add_to_favorites)
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Icon(
                imageVector = if (isFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Default.FavoriteBorder
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

### 4. Logique Reset Android

```kotlin
fun resetQuestionProgress(categoryId: String) {
    viewModelScope.launch {
        // Reset index
        _currentIndex.value = 0
        _dragOffset.value = Offset.Zero

        // Reset progression
        packProgressService.resetProgress(categoryId)
        categoryProgressService.saveCurrentIndex(0, categoryId)

        // Recharger questions accessibles
        val accessibleQuestions = packProgressService.getAccessibleQuestions(
            cachedQuestions, categoryId
        )
        _accessibleQuestions.value = accessibleQuestions

        Log.d(TAG, "🔄 RESET COMPLET: Progression réinitialisée pour $categoryId")
    }
}
```

### 5. Strings.xml Android

```xml
<resources>
    <!-- Navigation -->
    <string name="on_count">sur</string>

    <!-- Favoris -->
    <string name="add_to_favorites">Ajouter aux favoris</string>
    <string name="remove_from_favorites">Retirer des favoris</string>
</resources>
```

### 6. State Management

```kotlin
@Composable
fun QuestionNavigationViewModel() {
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex = _currentIndex.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite = _isFavorite.asStateFlow()

    fun toggleFavorite(question: Question, category: QuestionCategory) {
        viewModelScope.launch {
            favoritesService.toggleFavorite(question, category)
            _isFavorite.value = favoritesService.isFavorite(question.id)
        }
    }
}
```

---

## 🎯 Couleurs Clés

| Élément            | Couleur iOS                                 | Couleur Android     | Usage                     |
| ------------------ | ------------------------------------------- | ------------------- | ------------------------- |
| **Background**     | `Color(red: 0.15, green: 0.03, blue: 0.08)` | `Color(0xFF260814)` | Fond principal            |
| **Icônes header**  | `.white`                                    | `Color.White`       | Retour, refresh, compteur |
| **Bouton favoris** | `Color(red: 1.0, green: 0.4, blue: 0.6)`    | `Color(0xFFFF6699)` | Background bouton         |
| **Textes**         | `.white`                                    | `Color.White`       | Tous les textes           |

---

## 🔧 Fonctionnalités

✅ **Bouton retour** → Ferme le sheet  
✅ **Compteur dynamique** → "2 sur 24"  
✅ **Refresh** → Reset position + progression  
✅ **Favoris animé** → Cœur vide ↔ plein avec micro scale  
✅ **Navigation fluide** → Gesture drag entre cartes

---

## 📱 Responsive Design

**iOS :** Sheet modal avec background rouge foncé  
**Android :** Dialog/Screen avec background identique  
**Animation :** Micro scale (1.02x) sur favoris actif  
**Padding :** Header 60dp top, bouton 50dp bottom
