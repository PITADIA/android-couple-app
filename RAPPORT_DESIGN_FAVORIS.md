# ❤️ DESIGN COMPLET - Vue Favoris

## 🎯 Vue d'Ensemble des Pages Favoris

1. **FavoritesView/FavoritesCardView** - Page principale avec cartes swipeables
2. **EmptyFavoritesStateView** - État vide avec images localisées
3. **FavoriteQuestionCardView** - Cartes Love2Love avec dégradés sophistiqués
4. **FavoritesPreviewCard** - Carte preview dans la page principale
5. **Actions et Suppression** - Système de gestion des favoris

---

## 📱 1. PAGE PRINCIPALE FAVORIS (`FavoritesCardView`)

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

### 🏷️ Header Simple et Élégant

```swift
HStack {
    Spacer()

    // Titre centré (codé en dur)
    VStack(spacing: 4) {
        Text("Favoris")  // ⚠️ Pas de localisation (hardcodé)
            .font(.system(size: 28, weight: .bold))
            .foregroundColor(.black)
    }

    Spacer()
}
.padding(.horizontal, 20)
.padding(.top, 20)
.padding(.bottom, 20)
```

### 📋 État Vide Sophistiqué avec Images Localisées

```swift
if favoritesService.getAllFavorites().isEmpty {
    VStack(spacing: 30) {
        // 🎯 IMAGES LOCALISÉES SELON LA LANGUE
        Image(LocalizationService.localizedImageName(
            frenchImage: "mili",      // 🇫🇷 Image française
            defaultImage: "manon",    // 🇬🇧 Image par défaut (anglais + autres)
            germanImage: "crypto"     // 🇩🇪 Image allemande
        ))
        .resizable()
        .aspectRatio(contentMode: .fit)
        .frame(width: 240, height: 240)  // Taille standard des états vides

        VStack(spacing: 12) {
            // Titre principal
            Text("add_favorite_questions")  // "Ajoutez des questions favorites"
                .font(.system(size: 22, weight: .medium))
                .foregroundColor(.black)
                .multilineTextAlignment(.center)

            // Description détaillée
            Text("add_favorites_description")  // Description complète
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 30)
        }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .padding(.horizontal, 20)
}
```

### 🃏 Système de Cartes Swipeables

```swift
GeometryReader { geometry in
    let cardWidth = geometry.size.width - 40        // Largeur adaptative
    let cardSpacing: CGFloat = 30                   // Espace entre cartes

    ZStack {
        // Système sophistiqué de cartes visibles (3 max pour performance)
        ForEach(visibleFavorites, id: \.0) { indexAndFavorite in
            let (index, favorite) = indexAndFavorite
            let offset = CGFloat(index - currentFavoriteIndex)
            let xPosition = offset * (cardWidth + cardSpacing) + dragOffset.width

            FavoriteQuestionCardView(
                favorite: favorite,
                isBackground: index != currentFavoriteIndex  // Effet de profondeur
            )
            .frame(width: cardWidth)
            .offset(x: xPosition)                          // Position dynamique
            .scaleEffect(index == currentFavoriteIndex ? 1.0 : 0.95)  // Carte active plus grande
            .opacity(index == currentFavoriteIndex ? 1.0 : 0.8)       // Carte active plus visible
        }
    }
    .gesture(
        DragGesture()
            .onChanged { value in
                dragOffset = value.translation  // Drag en temps réel
            }
            .onEnded { value in
                let threshold: CGFloat = 80      // Seuil de déclenchement
                let velocity = value.predictedEndTranslation.width - value.translation.width

                withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) {
                    if value.translation.width > threshold || velocity > 500 {
                        // Swipe droite → Favori précédent
                        if currentFavoriteIndex > 0 {
                            currentIndex -= 1
                        }
                    } else if value.translation.width < -threshold || velocity < -500 {
                        // Swipe gauche → Favori suivant
                        if currentFavoriteIndex < favoritesService.getAllFavorites().count - 1 {
                            currentIndex += 1
                        }
                    }

                    dragOffset = .zero  // Remettre en place
                }
            }
    )
}
```

### 🔘 Bouton de Suppression

```swift
Button("remove_from_favorites") {  // "Retirer des favoris"
    showingDeleteAlert = true
}
.font(.system(size: 18, weight: .semibold))
.foregroundColor(.white)
.frame(width: UIScreen.main.bounds.width - 40)  // Même largeur que cartes
.frame(height: 56)
.background(
    Color(red: 1.0, green: 0.4, blue: 0.6)     // Même rose que header cartes
)
.cornerRadius(28)
.padding(.top, 20)
.padding(.bottom, 30)
```

---

## 🃏 2. CARTE FAVORITE (`FavoriteQuestionCardView`)

### 🎨 Design Love2Love Signature

```swift
VStack(spacing: 0) {
    // 📍 HEADER AVEC DÉGRADÉ ROSE
    VStack(spacing: 8) {
        Text(favorite.categoryTitle)  // Nom de la catégorie
            .font(.system(size: 18, weight: .bold))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 20)
    .background(
        LinearGradient(
            gradient: Gradient(colors: [
                Color(red: 1.0, green: 0.4, blue: 0.6),  // RGB(255, 102, 153)
                Color(red: 1.0, green: 0.6, blue: 0.8)   // RGB(255, 153, 204)
            ]),
            startPoint: .leading,
            endPoint: .trailing
        )
    )

    // 📍 CORPS AVEC DÉGRADÉ SOMBRE SOPHISTIQUÉ
    VStack(spacing: 30) {
        Spacer()

        // Question favorite
        Text(favorite.questionText)
            .font(.system(size: 22, weight: .medium))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .lineSpacing(6)                              // Espacement entre lignes
            .padding(.horizontal, 30)

        Spacer()

        // 📍 BRANDING LOVE2LOVE EN BAS
        HStack(spacing: 8) {
            Image("leetchi2")  // 🎯 IMAGE LOGO PRINCIPALE
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 24, height: 24)

            Text("Love2Love")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white.opacity(0.9))   // Légèrement transparent
        }
        .padding(.bottom, 30)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(
        LinearGradient(
            gradient: Gradient(colors: [
                Color(red: 0.2, green: 0.1, blue: 0.15),  // RGB(51, 26, 38)
                Color(red: 0.4, green: 0.2, blue: 0.3),   // RGB(102, 51, 77)
                Color(red: 0.6, green: 0.3, blue: 0.2)    // RGB(153, 77, 51)
            ]),
            startPoint: .top,
            endPoint: .bottom
        )
    )
}
.frame(maxWidth: .infinity)
.frame(height: 400)                                     // Hauteur fixe optimisée
.cornerRadius(20)                                       // Coins arrondis modernes
.shadow(
    color: .black.opacity(isBackground ? 0.1 : 0.3),   // Ombre adaptative
    radius: isBackground ? 5 : 10,                      // Radius adaptatif
    x: 0,
    y: isBackground ? 2 : 5                            // Décalage adaptatif
)
```

---

## 📋 3. CARTE PREVIEW (`FavoritesPreviewCard`)

### 🎨 Design Minimaliste pour Page Principale

```swift
VStack(alignment: .leading, spacing: 12) {
    // Header avec icône cœur
    HStack {
        HStack(spacing: 8) {
            Image(systemName: "heart.fill")  // 🎯 ICÔNE CŒUR ROUGE
                .font(.system(size: 16))
                .foregroundColor(.red)          // Rouge système

            Text("my_favorites")  // "Mes favoris"
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.black)
        }

        Spacer()

        // Bouton "Tout voir"
        Button("view_all") {  // "Voir tout"
            onTapViewAll()
        }
        .font(.system(size: 16))
        .foregroundColor(Color(hex: "#FD267A"))     // Rose Love2Love
    }

    // Contenu conditionnel
    if favoritesService.getAllFavorites().isEmpty {
        VStack(spacing: 8) {
            Text("❤️")  // 🎯 EMOJI CŒUR GRAND
                .font(.system(size: 24))

            Text("no_favorites_yet")  // "Pas encore de favoris"
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.6))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
    } else {
        // Liste des favoris (limité pour preview)
    }
}
```

---

## 🗑️ 4. SYSTÈME DE SUPPRESSION

### 🚨 Alert de Confirmation

```swift
.alert("remove_from_favorites", isPresented: $showingDeleteAlert) {
    Button("cancel", role: .cancel) { }         // Bouton annuler
    Button("remove", role: .destructive) {      // Bouton destructif rouge
        let allFavorites = favoritesService.getAllFavorites()
        if currentFavoriteIndex < allFavorites.count {
            let currentFavorite = allFavorites[currentFavoriteIndex]
            Task { @MainActor in
                favoritesService.removeFavorite(questionId: currentFavorite.questionId)

                // Ajustement automatique de l'index
                let updatedFavorites = favoritesService.getAllFavorites()
                if currentIndex >= updatedFavorites.count && currentIndex > 0 {
                    currentIndex -= 1
                }
            }
        }
    }
} message: {
    Text("remove_favorite_confirmation")  // Message de confirmation détaillé
}
```

---

## 🎨 Palette Couleurs Complète

### Background Global

```swift
// Toutes les vues utilisent le même fond
Color(red: 0.97, green: 0.97, blue: 0.98)  // RGB(247, 247, 250) - Gris très clair
```

### Cartes Love2Love (Dégradés Signatures)

```swift
// Header rose Love2Love (identique questions/défis)
LinearGradient(colors: [
    Color(red: 1.0, green: 0.4, blue: 0.6),  // RGB(255, 102, 153)
    Color(red: 1.0, green: 0.6, blue: 0.8)   // RGB(255, 153, 204)
])

// Corps sombre sophistiqué (identique questions/défis)
LinearGradient(colors: [
    Color(red: 0.2, green: 0.1, blue: 0.15),  // RGB(51, 26, 38)
    Color(red: 0.4, green: 0.2, blue: 0.3),   // RGB(102, 51, 77)
    Color(red: 0.6, green: 0.3, blue: 0.2)    // RGB(153, 77, 51)
])
```

### Textes et UI

```swift
// Textes principaux
.black                                       // Titres et labels importants
.black.opacity(0.7)                         // Description état vide
.black.opacity(0.6)                         // Message "pas de favoris"

// Éléments interactifs
Color(hex: "#FD267A")                        // RGB(253, 38, 122) - Rose Love2Love
.red                                         // Rouge système pour icône cœur
.white                                       // Texte sur cartes sombres
.white.opacity(0.9)                         // Branding Love2Love
```

### Ombres Adaptatives

```swift
// Carte active (premier plan)
.shadow(color: .black.opacity(0.3), radius: 10, x: 0, y: 5)

// Cartes arrière-plan
.shadow(color: .black.opacity(0.1), radius: 5, x: 0, y: 2)
```

---

## 🖼️ Images et Assets Utilisés

| Composant                 | Asset          | Fichier        | Usage               | Taille/Localisation |
| ------------------------- | -------------- | -------------- | ------------------- | ------------------- |
| **État Vide (FR)**        | `"mili"`       | `mili.png`     | Image pour français | 240x240pt           |
| **État Vide (EN+Autres)** | `"manon"`      | `manon.png`    | Image par défaut    | 240x240pt           |
| **État Vide (DE)**        | `"crypto"`     | `crypto.png`   | Image pour allemand | 240x240pt           |
| **Branding Cartes**       | `"leetchi2"`   | `leetchi2.png` | Logo Love2Love      | 24x24pt             |
| **Preview Card**          | `"heart.fill"` | SystemIcon     | Icône cœur rouge    | 16pt                |
| **État Vide Preview**     | `"❤️"`         | Emoji          | Cœur émoji          | 24pt                |

**Localisation Automatique :**

```swift
LocalizationService.localizedImageName(
    frenchImage: "mili",      // 🇫🇷 Français
    defaultImage: "manon",    // 🇬🇧 Anglais + autres
    germanImage: "crypto"     // 🇩🇪 Allemand
)
```

**Localisation des Assets :**

```
Assets.xcassets/
├── mili.imageset/
│   └── mili.png          // Image état vide français
├── manon.imageset/
│   └── manon.png         // Image état vide défaut
├── crypto.imageset/
│   └── crypto.png        // Image état vide allemand
└── leetchi2.imageset/
    └── leetchi2.png      // Logo Love2Love
```

---

## 🌐 Keys de Traduction (UI.xcstrings)

### Titres et Labels

```json
"my_favorites": {
    "fr": "Mes favoris",
    "en": "My favorites",
    "de": "Meine Favoriten",
    "es": "Mis favoritos"
}
```

### État Vide Principal

```json
"add_favorite_questions": {
    "fr": "Ajoutez des questions favorites",
    "en": "Add favorite questions",
    "de": "Lieblingsfragen hinzufügen",
    "es": "Agregar preguntas favoritas"
},

"add_favorites_description": {
    "fr": "Ajoutez en favoris les questions qui vous plaisent le plus pour les retrouver facilement et les partager avec votre partenaire.",
    "en": "Add your favorite questions to easily find them and share them with your partner.",
    "de": "Füge deine Lieblingsfragen hinzu, um sie einfach zu finden und mit deinem Partner zu teilen.",
    "es": "Agrega tus preguntas favoritas para encontrarlas fácilmente y compartirlas con tu pareja."
}
```

### État Vide Preview

```json
"no_favorites_yet": {
    "fr": "Pas encore de favoris",
    "en": "No favorites yet",
    "de": "Noch keine Favoriten",
    "es": "Aún no hay favoritos"
}
```

### Actions

```json
"view_all": {
    "fr": "Voir tout",
    "en": "View all",
    "de": "Alle anzeigen",
    "es": "Ver todo"
},

"remove_from_favorites": {
    "fr": "Retirer des favoris",
    "en": "Remove from favorites",
    "de": "Aus Favoriten entfernen",
    "es": "Eliminar de favoritos"
}
```

### Suppression et Confirmation

```json
"remove": {
    "fr": "Retirer",
    "en": "Remove",
    "de": "Entfernen",
    "es": "Eliminar"
},

"cancel": {
    "fr": "Annuler",
    "en": "Cancel",
    "de": "Abbrechen",
    "es": "Cancelar"
},

"remove_favorite_confirmation": {
    "fr": "Cette question sera retirée de vos favoris. Vous pourrez la remettre en favoris depuis sa catégorie.",
    "en": "This question will be removed from your favorites. You can add it back to favorites from its category.",
    "de": "Diese Frage wird aus deinen Favoriten entfernt. Du kannst sie aus ihrer Kategorie wieder zu den Favoriten hinzufügen.",
    "es": "Esta pregunta será eliminada de tus favoritos. Puedes volver a agregarla a favoritos desde su categoría."
}
```

---

## 📏 Espacements et Dimensions

### Layout Principal

```swift
.padding(.horizontal, 20)        // Marges latérales standard
.padding(.top, 20)              // Header top
.padding(.bottom, 20)           // Header bottom
.padding(.bottom, 100)          // Espace pour menu du bas (FavoritesView)
```

### État Vide

```swift
VStack(spacing: 30)             // Espacement principal entre éléments
.frame(width: 240, height: 240) // Image principale (standard états vides)
VStack(spacing: 12)             // Espacement titre/description
.padding(.horizontal, 30)       // Marges texte description
.frame(maxWidth: .infinity, maxHeight: .infinity)  // Expansion complète
.padding(.horizontal, 20)       // Marges conteneur
```

### Cartes Swipeables

```swift
let cardWidth = geometry.size.width - 40    // Largeur adaptative
let cardSpacing: CGFloat = 30               // Espace entre cartes
.frame(height: 400)                         // Hauteur cartes fixe
.cornerRadius(20)                           // Coins arrondis cartes
.scaleEffect(index == currentFavoriteIndex ? 1.0 : 0.95)  // Effet profondeur
.opacity(index == currentFavoriteIndex ? 1.0 : 0.8)       // Transparence arrière-plan
```

### Carte Favorite Design

```swift
.padding(.vertical, 20)         // Padding header carte
.padding(.horizontal, 30)       // Padding texte question
.padding(.bottom, 30)           // Padding branding
VStack(spacing: 30)             // Espacement vertical principal
.lineSpacing(6)                 // Espacement entre lignes
HStack(spacing: 8)              // Espacement logo/texte branding
.frame(width: 24, height: 24)   // Taille logo Love2Love
```

### Bouton Suppression

```swift
.frame(width: UIScreen.main.bounds.width - 40)  // Même largeur que cartes
.frame(height: 56)              // Hauteur bouton standard
.cornerRadius(28)               // Coins arrondis (hauteur/2)
.padding(.top, 20)              // Espace depuis cartes
.padding(.bottom, 30)           // Espace vers bas
```

### Preview Card

```swift
VStack(alignment: .leading, spacing: 12)    // Structure principale
HStack(spacing: 8)              // Espacement icône/titre
.font(.system(size: 16))        // Police standard preview
.font(.system(size: 24))        // Police emoji grande
.padding(.vertical, 20)         // Padding état vide preview
VStack(spacing: 8)              // Espacement emoji/message
```

---

## 🤖 Adaptation Android (Kotlin/Compose)

### 1. Structure Générale

```kotlin
@Composable
fun FavoritesScreen(
    favoritesState: FavoritesState,
    onRemoveFavorite: (String) -> Unit = {},
    onViewAllFavorites: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))  // RGB(247, 247, 250)
    ) {
        when (favoritesState) {
            is FavoritesState.Empty -> EmptyFavoritesContent()
            is FavoritesState.WithFavorites -> FavoritesCardsContent(favoritesState.favorites)
        }
    }
}
```

### 2. Header Simple

```kotlin
@Composable
fun FavoritesHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Favoris",  // Hardcodé comme iOS
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}
```

### 3. État Vide avec Images Localisées

```kotlin
@Composable
fun EmptyFavoritesContent() {
    val context = LocalContext.current

    // Logique de localisation des images
    val imageName = when (Locale.getDefault().language) {
        "fr" -> "mili"
        "de" -> "crypto"
        else -> "manon"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image localisée
        Image(
            painter = painterResource(
                id = context.resources.getIdentifier(
                    imageName, "drawable", context.packageName
                )
            ),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(30.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.add_favorite_questions),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.add_favorites_description),
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
```

### 4. Système de Cartes Swipeables

```kotlin
@Composable
fun FavoritesCardsContent(
    favorites: List<FavoriteQuestion>,
    onRemoveFavorite: (FavoriteQuestion) -> Unit = {}
) {
    var currentIndex by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { favorites.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        currentIndex = pagerState.currentPage
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        // Pager horizontal pour cartes
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
            pageSpacing = 30.dp
        ) { page ->
            FavoriteQuestionCard(
                favorite = favorites[page],
                isActive = page == currentIndex,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bouton retirer des favoris
        Button(
            onClick = {
                if (currentIndex < favorites.size) {
                    onRemoveFavorite(favorites[currentIndex])
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFFFF6699)  // Même rose que header cartes
            )
        ) {
            Text(
                text = stringResource(R.string.remove_from_favorites),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
```

### 5. Carte Favorite Love2Love

```kotlin
@Composable
fun FavoriteQuestionCard(
    favorite: FavoriteQuestion,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(400.dp)
            .scale(if (isActive) 1.0f else 0.95f)
            .alpha(if (isActive) 1.0f else 0.8f),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 10.dp else 5.dp
        )
    ) {
        Column {
            // Header rose Love2Love
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFF6699),  // RGB(255, 102, 153)
                                Color(0xFFFF99CC)   // RGB(255, 153, 204)
                            )
                        )
                    )
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = favorite.categoryTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            // Corps sombre sophistiqué
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF33261A),  // RGB(51, 26, 38)
                                Color(0xFF66334D),  // RGB(102, 51, 77)
                                Color(0xFF994D33)   // RGB(153, 77, 51)
                            )
                        )
                    )
                    .padding(horizontal = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    // Question favorite
                    Text(
                        text = favorite.questionText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp  // Équivalent de lineSpacing(6)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Branding Love2Love
                    Row(
                        modifier = Modifier.padding(bottom = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.leetchi2),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            contentScale = ContentScale.Fit
                        )

                        Text(
                            text = "Love2Love",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
```

### 6. Preview Card pour Page Principale

```kotlin
@Composable
fun FavoritesPreviewCard(
    favorites: List<FavoriteQuestion>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = stringResource(R.string.my_favorites),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }

                TextButton(onClick = onViewAll) {
                    Text(
                        text = stringResource(R.string.view_all),
                        fontSize = 16.sp,
                        color = Color(0xFFFD267A)
                    )
                }
            }

            // Contenu
            if (favorites.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "❤️",
                        fontSize = 24.sp
                    )

                    Text(
                        text = stringResource(R.string.no_favorites_yet),
                        fontSize = 16.sp,
                        color = Color.Black.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Liste limitée pour preview
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(favorites.take(3)) { favorite ->
                        FavoritePreviewItem(favorite = favorite)
                    }
                }
            }
        }
    }
}
```

### 7. Système de Suppression avec Dialog

```kotlin
@Composable
fun RemoveFavoriteDialog(
    favorite: FavoriteQuestion?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (favorite != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.remove_from_favorites),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.remove_favorite_confirmation)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.remove),
                        color = MaterialTheme.colors.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel)
                    )
                }
            }
        )
    }
}
```

### 8. Strings.xml Android

```xml
<resources>
    <!-- Titres et labels -->
    <string name="my_favorites">Mes favoris</string>

    <!-- État vide principal -->
    <string name="add_favorite_questions">Ajoutez des questions favorites</string>
    <string name="add_favorites_description">Ajoutez en favoris les questions qui vous plaisent le plus pour les retrouver facilement et les partager avec votre partenaire.</string>

    <!-- État vide preview -->
    <string name="no_favorites_yet">Pas encore de favoris</string>

    <!-- Actions -->
    <string name="view_all">Voir tout</string>
    <string name="remove_from_favorites">Retirer des favoris</string>

    <!-- Suppression et confirmation -->
    <string name="remove">Retirer</string>
    <string name="cancel">Annuler</string>
    <string name="remove_favorite_confirmation">Cette question sera retirée de vos favoris. Vous pourrez la remettre en favoris depuis sa catégorie.</string>
</resources>
```

### 9. Localisation des Images Android

```kotlin
// Dans un fichier LocalizationHelper.kt
object LocalizationHelper {
    fun getLocalizedImageName(
        context: Context,
        frenchImage: String,
        defaultImage: String,
        germanImage: String
    ): Int {
        val language = Locale.getDefault().language

        val imageName = when (language) {
            "fr" -> frenchImage
            "de" -> germanImage
            else -> defaultImage
        }

        return context.resources.getIdentifier(
            imageName,
            "drawable",
            context.packageName
        )
    }
}

// Usage dans le composable
@Composable
fun EmptyFavoritesImage() {
    val context = LocalContext.current

    val imageResource = LocalizationHelper.getLocalizedImageName(
        context = context,
        frenchImage = "mili",
        defaultImage = "manon",
        germanImage = "crypto"
    )

    Image(
        painter = painterResource(imageResource),
        contentDescription = null,
        modifier = Modifier.size(240.dp)
    )
}
```

---

## 🎯 Points Clés du Design

✅ **Cohérence Love2Love** : Même dégradés que questions/défis (rose + sombre)  
✅ **Images localisées** : `"mili"` (FR), `"manon"` (EN), `"crypto"` (DE) selon langue système  
✅ **Swipe sophistiqué** : Système de 3 cartes visibles avec effets de profondeur  
✅ **Ombres adaptatives** : Plus marquées pour carte active, subtiles pour arrière-plan  
✅ **Titre hardcodé** : "Favoris" non localisé (particularité de cette vue)  
✅ **Branding intégré** : Logo `"leetchi2"` + texte "Love2Love" dans chaque carte  
✅ **Performance optimisée** : Maximum 3 cartes visibles simultanément  
✅ **Animations fluides** : `.spring(response: 0.6, dampingFraction: 0.8)`  
✅ **Gestion seuils** : 80pt minimum pour swipe + prise en compte vélocité  
✅ **Preview intégrée** : Carte résumé avec émoji ❤️ pour page principale

Le design des favoris utilise le **système de cartes le plus sophistiqué** de Love2Love avec des **dégradés identiques** aux questions/défis mais une **logique d'interaction unique** (swipe + suppression) et des **images localisées** selon la langue ! ❤️✨

**Fichier :** `RAPPORT_DESIGN_FAVORIS.md`
