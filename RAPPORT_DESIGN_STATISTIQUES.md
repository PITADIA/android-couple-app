# 📊 DESIGN SOPHISTIQUÉ - Section Statistiques du Couple

## 🎨 Vue d'Ensemble (`CoupleStatisticsView.swift`)

### Structure Générale

```swift
VStack(alignment: .leading, spacing: 16) {
    // Titre section
    HStack {
        Text("couple_statistics".localized)  // "Vos statistiques de couple"
            .font(.system(size: 22, weight: .semibold))
            .foregroundColor(.black)
            .padding(.horizontal, 20)
        Spacer()
    }

    // Grille 2x2 sophistiquée
    LazyVGrid(columns: [
        GridItem(.flexible(), spacing: 16),
        GridItem(.flexible(), spacing: 16)
    ], spacing: 16) {
        // 4 cartes statistiques avec designs uniques
    }
    .padding(.horizontal, 20)
}
```

**Spacing :** 16dp entre cartes, 20dp padding horizontal

---

## 🏷️ Titre Section

### Design

```swift
Text("couple_statistics".localized)
    .font(.system(size: 22, weight: .semibold))
    .foregroundColor(.black)
    .padding(.horizontal, 20)
```

### Key Localisée

```json
"couple_statistics": {
    "fr": "Vos statistiques de couple",
    "en": "Your couple statistics",
    "de": "Eure Paarstatistiken",
    "es": "Tus estadísticas de pareja"
}
```

---

## 🎴 Cartes Statistiques Sophistiquées

### 1️⃣ Carte "Jours Ensemble" (Rose)

```swift
StatisticCardView(
    title: "days_together".localized,    // "Jours\nensemble"
    value: "\(daysTogetherCount)",       // Ex: "847"
    icon: "jours",                       // jours.png
    iconColor: Color(hex: "#feb5c8"),    // Rose moyen
    backgroundColor: Color(hex: "#fedce3"), // Rose très clair
    textColor: Color(hex: "#db3556")     // Rose foncé
)
```

**Palette Rose :**

- Icône : `#feb5c8` (RGB 254, 181, 200)
- Fond : `#fedce3` (RGB 254, 220, 227)
- Texte : `#db3556` (RGB 219, 53, 86)

### 2️⃣ Carte "Questions Répondues" (Orange)

```swift
StatisticCardView(
    title: "questions_answered".localized, // "Réponses\naux questions"
    value: "\(Int(questionsProgressPercentage))%", // Ex: "67%"
    icon: "qst",                          // qst.png
    iconColor: Color(hex: "#fed397"),     // Orange moyen
    backgroundColor: Color(hex: "#fde9cf"), // Orange très clair
    textColor: Color(hex: "#ffa229")      // Orange foncé
)
```

**Palette Orange :**

- Icône : `#fed397` (RGB 254, 211, 151)
- Fond : `#fde9cf` (RGB 253, 233, 207)
- Texte : `#ffa229` (RGB 255, 162, 41)

### 3️⃣ Carte "Villes Visitées" (Bleu)

```swift
StatisticCardView(
    title: "cities_visited".localized,    // "Villes\nvisitées"
    value: "\(citiesVisitedCount)",       // Ex: "12"
    icon: "ville",                        // ville.png
    iconColor: Color(hex: "#b0d6fe"),     // Bleu clair
    backgroundColor: Color(hex: "#dbecfd"), // Bleu très clair
    textColor: Color(hex: "#0a85ff")      // Bleu vif
)
```

**Palette Bleu :**

- Icône : `#b0d6fe` (RGB 176, 214, 254)
- Fond : `#dbecfd` (RGB 219, 236, 253)
- Texte : `#0a85ff` (RGB 10, 133, 255)

### 4️⃣ Carte "Pays Visités" (Violet)

```swift
StatisticCardView(
    title: "countries_visited".localized, // "Pays\nvisités"
    value: "\(countriesVisitedCount)",    // Ex: "3"
    icon: "pays",                         // pays.png
    iconColor: Color(hex: "#d1b3ff"),     // Violet clair
    backgroundColor: Color(hex: "#e8dcff"), // Violet très clair
    textColor: Color(hex: "#7c3aed")      // Violet foncé
)
```

**Palette Violet :**

- Icône : `#d1b3ff` (RGB 209, 179, 255)
- Fond : `#e8dcff` (RGB 232, 220, 255)
- Texte : `#7c3aed` (RGB 124, 58, 237)

---

## 🎨 Design Individuel des Cartes

### Structure `StatisticCardView`

```swift
VStack(spacing: 0) {
    // 📍 Zone Supérieure : Icône alignée à droite
    HStack {
        Spacer()
        Image(icon)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: 40, height: 40)
            .foregroundColor(iconColor)
    }

    Spacer()  // Espace flexible au centre

    // 📍 Zone Inférieure : Valeur + Titre alignés à gauche
    HStack {
        VStack(alignment: .leading, spacing: 4) {
            // Valeur principale (grande)
            Text(value)
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(textColor)
                .minimumScaleFactor(0.7)
                .lineLimit(1)

            // Titre (petit, multi-lignes)
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
.frame(height: 140)  // Hauteur fixe
.padding(16)         // Padding interne
.background(
    RoundedRectangle(cornerRadius: 16)
        .fill(backgroundColor)
        .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 2)
)
```

### Positionnement Sophistiqué

- **Icône :** Top-right (40x40pt)
- **Valeur :** Bottom-left (32pt, bold)
- **Titre :** Bottom-left sous valeur (14pt, medium)
- **Shadow :** Légère ombre portée (opacity 0.05, radius 8, offset y=2)

---

## 🖼️ Images/Icônes Utilisées

| Statistique             | Asset     | Fichier     | Dimensions |
| ----------------------- | --------- | ----------- | ---------- |
| **Jours Ensemble**      | `"jours"` | `jours.png` | 40x40pt    |
| **Questions Répondues** | `"qst"`   | `qst.png`   | 40x40pt    |
| **Villes Visitées**     | `"ville"` | `ville.png` | 40x40pt    |
| **Pays Visités**        | `"pays"`  | `pays.png`  | 40x40pt    |

### Localisation des Assets

```
Assets.xcassets/
├── jours.imageset/
│   └── jours.png
├── qst.imageset/
│   └── qst.png
├── ville.imageset/
│   └── ville.png
└── pays.imageset/
    └── pays.png
```

---

## 🌐 Keys de Traduction

### Titre Principal

```json
"couple_statistics": {
    "fr": "Vos statistiques de couple",
    "en": "Your couple statistics",
    "de": "Eure Paarstatistiken",
    "es": "Tus estadísticas de pareja"
}
```

### Labels Statistiques

```json
"days_together": {
    "fr": "Jours\nensemble",
    "en": "Days\ntogether",
    "de": "Tage\ngemeinsam"
},

"questions_answered": {
    "fr": "Réponses\naux questions",
    "en": "Questions\nanswered",
    "de": "Fragen\nbeantwortet"
},

"cities_visited": {
    "fr": "Villes\nvisitées",
    "en": "Cities\nvisited",
    "de": "Besuchte\nStädte"
},

"countries_visited": {
    "fr": "Pays\nvisités",
    "en": "Countries\nvisited",
    "de": "Besuchte\nLänder"
}
```

**Note :** Les `\n` permettent le retour à la ligne sur 2 lignes

---

## 🤖 Adaptation Android (Kotlin/Compose)

### 1. Layout Principal

```kotlin
@Composable
fun CoupleStatisticsSection(
    daysTogetherCount: Int,
    questionsProgressPercentage: Float,
    citiesVisitedCount: Int,
    countriesVisitedCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Titre
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.couple_statistics),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // Grille 2x2
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            item { StatisticCard(/* Jours ensemble */) }
            item { StatisticCard(/* Questions répondues */) }
            item { StatisticCard(/* Villes visitées */) }
            item { StatisticCard(/* Pays visités */) }
        }
    }
}
```

### 2. Carte Statistique

```kotlin
@Composable
fun StatisticCard(
    title: String,
    value: String,
    @DrawableRes iconRes: Int,
    iconColor: Color,
    backgroundColor: Color,
    textColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = backgroundColor,
        elevation = 4.dp  // Shadow équivalent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Icône top-right
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd)
            )

            // Valeur + Titre bottom-left
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

### 3. Usage des Cartes

```kotlin
// Dans CoupleStatisticsSection
LazyVerticalGrid(...) {
    // Jours ensemble (Rose)
    item {
        StatisticCard(
            title = stringResource(R.string.days_together),
            value = daysTogetherCount.toString(),
            iconRes = R.drawable.ic_jours,
            iconColor = Color(0xFFFEB5C8),
            backgroundColor = Color(0xFFFEDCE3),
            textColor = Color(0xFFDB3556)
        )
    }

    // Questions répondues (Orange)
    item {
        StatisticCard(
            title = stringResource(R.string.questions_answered),
            value = "${questionsProgressPercentage.toInt()}%",
            iconRes = R.drawable.ic_qst,
            iconColor = Color(0xFFFED397),
            backgroundColor = Color(0xFFFDE9CF),
            textColor = Color(0xFFFFA229)
        )
    }

    // Villes visitées (Bleu)
    item {
        StatisticCard(
            title = stringResource(R.string.cities_visited),
            value = citiesVisitedCount.toString(),
            iconRes = R.drawable.ic_ville,
            iconColor = Color(0xFFB0D6FE),
            backgroundColor = Color(0xFFDBECFD),
            textColor = Color(0xFF0A85FF)
        )
    }

    // Pays visités (Violet)
    item {
        StatisticCard(
            title = stringResource(R.string.countries_visited),
            value = countriesVisitedCount.toString(),
            iconRes = R.drawable.ic_pays,
            iconColor = Color(0xFFD1B3FF),
            backgroundColor = Color(0xFFE8DCFF),
            textColor = Color(0xFF7C3AED)
        )
    }
}
```

### 4. Strings.xml Android

```xml
<resources>
    <!-- Titre section -->
    <string name="couple_statistics">Vos statistiques de couple</string>

    <!-- Labels statistiques -->
    <string name="days_together">Jours\nensemble</string>
    <string name="questions_answered">Réponses\naux questions</string>
    <string name="cities_visited">Villes\nvisitées</string>
    <string name="countries_visited">Pays\nvisités</string>
</resources>
```

### 5. Drawables Android

```
res/drawable/
├── ic_jours.xml (ou .png)
├── ic_qst.xml (ou .png)
├── ic_ville.xml (ou .png)
└── ic_pays.xml (ou .png)
```

### 6. ViewModel Logique

```kotlin
class CoupleStatisticsViewModel : ViewModel() {
    private val _statisticsState = MutableStateFlow(CoupleStatistics())
    val statisticsState = _statisticsState.asStateFlow()

    fun loadStatistics() {
        viewModelScope.launch {
            val stats = CoupleStatistics(
                daysTogetherCount = calculateDaysTogether(),
                questionsProgressPercentage = calculateQuestionsProgress(),
                citiesVisitedCount = getCitiesVisitedCount(),
                countriesVisitedCount = getCountriesVisitedCount()
            )
            _statisticsState.value = stats
        }
    }
}

data class CoupleStatistics(
    val daysTogetherCount: Int = 0,
    val questionsProgressPercentage: Float = 0f,
    val citiesVisitedCount: Int = 0,
    val countriesVisitedCount: Int = 0
)
```

---

## 🎨 Palette Couleurs Complète

### Rose (Jours Ensemble)

```kotlin
val IconColor = Color(0xFFFEB5C8)      // RGB(254, 181, 200)
val BackgroundColor = Color(0xFFFEDCE3) // RGB(254, 220, 227)
val TextColor = Color(0xFFDB3556)      // RGB(219, 53, 86)
```

### Orange (Questions)

```kotlin
val IconColor = Color(0xFFFED397)      // RGB(254, 211, 151)
val BackgroundColor = Color(0xFFFDE9CF) // RGB(253, 233, 207)
val TextColor = Color(0xFFFFA229)      // RGB(255, 162, 41)
```

### Bleu (Villes)

```kotlin
val IconColor = Color(0xFFB0D6FE)      // RGB(176, 214, 254)
val BackgroundColor = Color(0xFFDBECFD) // RGB(219, 236, 253)
val TextColor = Color(0xFF0A85FF)      // RGB(10, 133, 255)
```

### Violet (Pays)

```kotlin
val IconColor = Color(0xFFD1B3FF)      // RGB(209, 179, 255)
val BackgroundColor = Color(0xFFE8DCFF) // RGB(232, 220, 255)
val TextColor = Color(0xFF7C3AED)      // RGB(124, 58, 237)
```

---

## ⚡ Animation & Interactions

### iOS (optionnel)

```swift
.scaleEffect(isPressed ? 0.95 : 1.0)
.animation(.easeInOut(duration: 0.1), value: isPressed)
```

### Android (optionnel)

```kotlin
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.95f else 1.0f,
    animationSpec = tween(100)
)

Modifier.scale(scale)
```

---

## 🎯 Points Sophistiqués du Design

✅ **Grille responsive** 2x2 avec spacing uniforme  
✅ **4 palettes chromatiques** cohérentes par statistique  
✅ **Positionnement asymétrique** (icône top-right, texte bottom-left)  
✅ **Typographie hiérarchisée** (32pt valeur, 14pt label)  
✅ **Ombres subtiles** pour profondeur  
✅ **Multi-lignes** intelligentes avec `\n`  
✅ **Images vectorielles** optimisées  
✅ **Responsive scaling** (`minimumScaleFactor`)

Le design est vraiment sophistiqué avec des couleurs pastel harmonieuses, un positionnement asymétrique élégant, et une hiérarchie typographique parfaite ! 🎨✨
