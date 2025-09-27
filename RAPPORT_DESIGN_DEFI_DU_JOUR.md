# 🎯 DESIGN COMPLET - Défi du Jour

## 🎯 Vue d'Ensemble des 3 Pages

1. **DailyChallengeIntroView** - Page d'introduction/présentation
2. **DailyChallengeMainView (Loading/NoChallenge)** - États de chargement et sans défi
3. **DailyChallengeMainView (WithChallenge)** - Vue principale avec carte défi

---

## 📱 1. PAGE D'INTRODUCTION (`DailyChallengeIntroView`)

### 🎨 Design Global

```swift
ZStack {
    // Background principal
    Color(red: 0.97, green: 0.97, blue: 0.98)  // Gris très clair
        .ignoresSafeArea()

    VStack(spacing: 0) {
        // Header + Contenu + Bouton
    }
}
```

### 🏷️ Header Section

```swift
HStack {
    Spacer()
    VStack(spacing: 4) {
        Text("daily_challenges_title")  // "Daily Challenge"
            .font(.system(size: 28, weight: .bold))
            .foregroundColor(.black)
    }
    Spacer()
}
.padding(.horizontal, 20)
.padding(.top, 20)
.padding(.bottom, 100)  // Espace augmenté entre titre et image
```

### 🖼️ Image Section

```swift
VStack(spacing: 20) {
    Image("gaougaou")  // 🎯 IMAGE PRINCIPALE
        .resizable()
        .aspectRatio(contentMode: .fit)
        .frame(width: 240, height: 240)  // Taille fixe

    VStack(spacing: 12) {
        Text("daily_challenge_intro_title")  // "Take on every challenge together 🎯"
            .font(.system(size: 22, weight: .medium))
            .foregroundColor(.black)
            .multilineTextAlignment(.center)

        Text("daily_challenge_intro_subtitle")  // Description longue
            .font(.system(size: 16))
            .foregroundColor(.black.opacity(0.7))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 30)
    }
}
```

### 🔘 Bouton Principal

```swift
Button(action: { /* Navigation */ }) {
    Text(buttonText)  // "Connect my partner" OU "Continue"
        .font(.system(size: 16, weight: .semibold))
        .foregroundColor(.white)
        .padding(.horizontal, 24)
        .frame(height: 56)
        .background(
            RoundedRectangle(cornerRadius: 28)
                .fill(Color(hex: "#FD267A"))  // Rose Love2Love
        )
}
.padding(.bottom, 160)  // Espace pour le menu du bas
```

**Couleurs Introduction :**

- Background : `Color(red: 0.97, green: 0.97, blue: 0.98)` - RGB(247, 247, 250)
- Texte principal : `.black`
- Texte secondaire : `.black.opacity(0.7)` - Gris foncé
- Bouton : `Color(hex: "#FD267A")` - Rose Love2Love

---

## 📱 2. PAGE PRINCIPALE (`DailyChallengeMainView`)

### 🎨 Design Global

```swift
ZStack {
    Color(red: 0.97, green: 0.97, blue: 0.98)  // Même gris clair
        .ignoresSafeArea()

    VStack(spacing: 0) {
        // Header + Contenu conditionnel
    }
}
```

### 🏷️ Header Avec Icône Favoris

```swift
HStack {
    Spacer()
    VStack(spacing: 4) {
        Text("daily_challenges_title")  // "Daily Challenge"
            .font(.system(size: 28, weight: .bold))
            .foregroundColor(.black)

        // Sous-titre freemium dynamique
        if let subtitle = getDailyChallengeSubtitle() {
            Text(subtitle)
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 4)
        }
    }
    Spacer()

    // 📌 ICÔNE DÉFIS SAUVEGARDÉS
    Button(action: { showingSavedChallenges = true }) {
        Image(systemName: "bookmark")
            .font(.system(size: 20))
            .foregroundColor(.black)
    }
}
.padding(.horizontal, 20)
.padding(.top, 16)
.padding(.bottom, 8)
```

### ⏳ État de Chargement

```swift
VStack(spacing: 20) {
    Spacer()

    ProgressView()
        .scaleEffect(1.2)

    Text("loading_challenge")  // "Loading your challenge..."
        .font(.subheadline)
        .foregroundColor(.secondary)

    Spacer()
}
```

### 🚫 État Sans Défi Disponible

```swift
VStack(spacing: 20) {
    Spacer()

    Image(systemName: "target")  // 🎯 Icône système
        .font(.system(size: 60))
        .foregroundColor(.gray)

    Text("no_challenge_available")  // "No challenge available"
        .font(.title2)
        .fontWeight(.semibold)

    Text("come_back_tomorrow_challenge")  // Message d'attente
        .font(.body)
        .foregroundColor(.secondary)
        .multilineTextAlignment(.center)

    Spacer()
}
.padding()
```

### 📜 État Avec Défi (ScrollView)

```swift
ScrollView {
    VStack(spacing: 20) {
        Spacer(minLength: 20)

        DailyChallengeCardView(
            challenge: currentChallenge,
            showDeleteButton: false,
            onCompleted: { handleChallengeCompleted(currentChallenge) },
            onSave: { handleChallengeSave(currentChallenge) }
        )

        Spacer(minLength: 40)
    }
    .padding(.horizontal, 20)
}
```

---

## 🃏 3. CARTE DÉFI (`DailyChallengeCardView`)

### 🎨 Structure Complète

```swift
VStack(spacing: 20) {
    // 📍 CARTE PRINCIPALE - Design signature Love2Love
    VStack(spacing: 0) {
        // Header rose (identique aux questions)
        VStack(spacing: 8) {
            Text("Love2Love")
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

        // Corps sombre avec défi
        VStack(spacing: 30) {
            Spacer()

            Text(challenge.localizedText)  // Texte du défi
                .font(.system(size: 22, weight: .medium))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .lineSpacing(6)
                .padding(.horizontal, 30)

            Spacer(minLength: 20)
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
    .frame(height: 400)  // Hauteur fixe plus grande
    .cornerRadius(20)
    .shadow(color: .black.opacity(0.3), radius: 10, x: 0, y: 5)  // Ombre plus marquée
}
```

### 🔘 Boutons d'Action

```swift
VStack(spacing: 16) {
    // 📍 BOUTON COMPLÉTER DÉFI
    Button(action: { handleChallengeCompleted() }) {
        HStack {
            Spacer()

            Text("challenge_completed_button")  // "Complete challenge"
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)

            Image(systemName: isCompleted ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 18))
                .foregroundColor(.white)
                .padding(.leading, 8)

            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }
    .frame(maxWidth: .infinity)
    .background(Color(red: 1.0, green: 0.4, blue: 0.6))  // Rose Love2Love
    .cornerRadius(28)

    // 📍 BOUTON SAUVEGARDER DÉFI
    Button(action: { handleChallengeSave() }) {
        HStack {
            Spacer()

            Text("save_challenge_button")  // "Save challenge"
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)

            if showSaveConfirmation {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .padding(.leading, 8)
                    .scaleEffect(showSaveConfirmation ? 1.2 : 1.0)  // Animation
                    .animation(.spring(response: 0.5), value: showSaveConfirmation)
            } else {
                Image(systemName: isAlreadySaved ? "bookmark.fill" : "bookmark")
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .padding(.leading, 8)
            }

            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }
    .frame(maxWidth: .infinity)
    .background(Color(red: 1.0, green: 0.4, blue: 0.6))  // Rose Love2Love
    .cornerRadius(28)
}
```

### 🗑️ Bouton Suppression (Mode Sauvegardés)

```swift
Button("delete_challenge_button") {  // "Delete challenge"
    onDelete?()
}
.font(.system(size: 18, weight: .semibold))
.foregroundColor(.white)
.frame(maxWidth: .infinity)
.frame(height: 56)
.background(Color.red)  // Rouge danger
.cornerRadius(28)
```

---

## 🎨 Palette Couleurs Complète

### Background Principal

```swift
// Toutes les vues utilisent le même fond
Color(red: 0.97, green: 0.97, blue: 0.98)  // RGB(247, 247, 250)
```

### Carte Défi (Dégradés)

```swift
// Header rose Love2Love (identique aux questions)
LinearGradient(colors: [
    Color(red: 1.0, green: 0.4, blue: 0.6),  // RGB(255, 102, 153)
    Color(red: 1.0, green: 0.6, blue: 0.8)   // RGB(255, 153, 204)
])

// Corps sombre sophistiqué (identique aux questions)
LinearGradient(colors: [
    Color(red: 0.2, green: 0.1, blue: 0.15),  // RGB(51, 26, 38)
    Color(red: 0.4, green: 0.2, blue: 0.3),   // RGB(102, 51, 77)
    Color(red: 0.6, green: 0.3, blue: 0.2)    // RGB(153, 77, 51)
])
```

### Boutons d'Action

```swift
// Boutons principaux (Compléter + Sauvegarder)
Color(red: 1.0, green: 0.4, blue: 0.6)      // RGB(255, 102, 153) - Rose Love2Love

// Bouton suppression
Color.red                                    // Rouge système

// Bouton introduction
Color(hex: "#FD267A")                        // RGB(253, 38, 122) - Rose Love2Love
```

### Éléments UI

```swift
// Textes principaux
.black                                       // Titres et textes importants

// Textes secondaires
.black.opacity(0.7)                          // RGB(0, 0, 0, 0.7) - Gris foncé
.secondary                                   // Gris système adaptatif
.gray                                        // Gris système

// Icônes d'état
.gray                                        // Icônes neutres (target, bookmark)
.white                                       // Icônes sur boutons colorés
```

---

## 🖼️ Images et Assets Utilisés

| Composant        | Asset                     | Fichier        | Usage                    | Taille    |
| ---------------- | ------------------------- | -------------- | ------------------------ | --------- |
| **Introduction** | `"gaougaou"`              | `gaougaou.png` | Image principale d'intro | 240x240pt |
| **Sans défi**    | `"target"`                | SystemIcon     | Icône état sans défi     | 60pt      |
| **Chargement**   | `ProgressView()`          | SystemUI       | Indicateur de chargement | 1.2x      |
| **Favoris**      | `"bookmark"`              | SystemIcon     | Icône défis sauvegardés  | 20pt      |
| **Complétion**   | `"checkmark.circle.fill"` | SystemIcon     | État complété            | 18pt      |
| **Sauvegarde**   | `"bookmark.fill"`         | SystemIcon     | État sauvegardé          | 18pt      |

**Localisation des Assets :**

```
Assets.xcassets/
└── gaougaou.imageset/
    └── gaougaou.png      // Image d'introduction défi
```

---

## 🌐 Keys de Traduction (DailyChallenges.xcstrings)

### Titres Principaux

```json
"daily_challenges_title": {
    "fr": "Défi du Jour",
    "en": "Daily Challenge",
    "de": "Tagesherausforderung",
    "es": "Desafío Diario"
}
```

### Page Introduction

```json
"daily_challenge_intro_title": {
    "fr": "Relevez chaque défi ensemble 🎯",
    "en": "Take on every challenge together 🎯",
    "de": "Meistert jede Herausforderung zusammen 🎯",
    "es": "Afronten cada desafío juntos 🎯"
},

"daily_challenge_intro_subtitle": {
    "fr": "Chaque jour, un nouveau défi pour renforcer vos liens et faire grandir votre relation à travers des actions partagées significatives.\nConnectez-vous pour commencer le voyage ensemble.",
    "en": "Every day, a new challenge to strengthen your bond and grow your relationship through meaningful shared actions.\nConnect to start the journey together.",
    "de": "Jeden Tag eine neue Herausforderung, um Ihre Bindung zu stärken und Ihre Beziehung durch bedeutungsvolle gemeinsame Aktionen zu verbessern.\nVerbinden Sie sich, um die Reise gemeinsam zu beginnen.",
    "es": "Cada día, un nuevo desafío para fortalecer vuestros lazos y hacer crecer vuestra relación a través de acciones compartidas significativas.\nConéctense para comenzar el viaje juntos."
}
```

### Boutons d'Action

```json
"connect_partner_button": {
    "fr": "Connecter mon partenaire",
    "en": "Connect my partner",
    "de": "Meinen Partner verbinden",
    "es": "Conectar mi pareja"
},

"continue_button": {
    "fr": "Continuer",
    "en": "Continue",
    "de": "Fortfahren",
    "es": "Continuar"
}
```

### États de la Vue Principale

```json
"loading_challenge": {
    "fr": "Chargement de votre défi...",
    "en": "Loading your challenge...",
    "de": "Lade deine Herausforderung...",
    "es": "Cargando tu desafío..."
},

"no_challenge_available": {
    "fr": "Aucun défi disponible",
    "en": "No challenge available",
    "de": "Keine Herausforderung verfügbar",
    "es": "No hay desafío disponible"
},

"come_back_tomorrow_challenge": {
    "fr": "Revenez demain pour découvrir un nouveau défi !",
    "en": "Come back tomorrow to discover a new challenge!",
    "de": "Komme morgen zurück, um eine neue Herausforderung zu entdecken!",
    "es": "¡Vuelve mañana para descubrir un nuevo desafío!"
}
```

### Boutons Carte Défi

```json
"challenge_completed_button": {
    "fr": "Compléter le défi",
    "en": "Complete challenge",
    "de": "Herausforderung abschließen",
    "es": "Completar desafío"
},

"save_challenge_button": {
    "fr": "Sauvegarder le défi",
    "en": "Save challenge",
    "de": "Herausforderung speichern",
    "es": "Guardar desafío"
},

"delete_challenge_button": {
    "fr": "Supprimer le défi",
    "en": "Delete challenge",
    "de": "Herausforderung löschen",
    "es": "Eliminar desafío"
}
```

---

## 📏 Espacements et Dimensions

### Layout Principal

```swift
.padding(.horizontal, 20)        // Marges latérales standard
.padding(.top, 16)              // Header top (compact)
.padding(.bottom, 8)            // Header bottom (compact)
.padding(.bottom, 160)          // Bouton intro (espace menu)
```

### Carte Défi

```swift
.frame(height: 400)             // Hauteur fixe plus grande que questions
.cornerRadius(20)               // Coins arrondis carte
.shadow(radius: 10, x: 0, y: 5) // Ombre plus marquée
.padding(.horizontal, 30)       // Padding texte défi
.padding(.vertical, 20)         // Padding header
```

### Boutons d'Action

```swift
.frame(height: 56)              // Hauteur bouton intro
.cornerRadius(28)               // Coins arrondis boutons
.padding(.horizontal, 20)       // Padding interne boutons carte
.padding(.vertical, 12)         // Padding vertical boutons carte
```

### Espacements ScrollView

```swift
Spacer(minLength: 20)           // Espace avant carte
Spacer(minLength: 40)           // Espace après carte
.padding(.horizontal, 20)       // Marges ScrollView
VStack(spacing: 16)             // Espacement entre boutons
```

---

## 🤖 Adaptation Android (Kotlin/Compose)

### 1. Structure Générale

```kotlin
@Composable
fun DailyChallengeScreen(
    state: DailyChallengeState,
    onBackClick: () -> Unit = {},
    onConnectPartner: () -> Unit = {},
    onCompleteChallenge: (DailyChallenge) -> Unit = {},
    onSaveChallenge: (DailyChallenge) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))  // RGB(247, 247, 250)
    ) {
        when (state) {
            is DailyChallengeState.Introduction -> IntroductionContent()
            is DailyChallengeState.Loading -> LoadingContent()
            is DailyChallengeState.NoChallenge -> NoChallengeContent()
            is DailyChallengeState.WithChallenge -> MainContent(state.challenge)
        }
    }
}
```

### 2. Page Introduction

```kotlin
@Composable
fun DailyChallengeIntroductionContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = stringResource(R.string.daily_challenges_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(top = 20.dp, bottom = 100.dp)
        )

        // Image principale
        Image(
            painter = painterResource(R.drawable.gaougaou),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Titres
        Text(
            text = stringResource(R.string.daily_challenge_intro_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.daily_challenge_intro_subtitle),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 30.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Bouton principal
        Button(
            onClick = { /* Navigation */ },
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .height(56.dp)
                .padding(bottom = 160.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFFFD267A)  // Rose Love2Love
            )
        ) {
            Text(
                text = buttonText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
```

### 3. Header Principal avec Favoris

```kotlin
@Composable
fun DailyChallengeHeader(
    subtitle: String? = null,
    onSavedChallengesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.daily_challenges_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Icône défis sauvegardés
        IconButton(onClick = onSavedChallengesClick) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "Saved challenges",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

### 4. États de Contenu

```kotlin
@Composable
fun LoadingChallengeContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.scale(1.2f),
            color = Color(0xFFFD267A)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.loading_challenge),
            fontSize = 14.sp,
            color = MaterialTheme.colors.secondary
        )
    }
}

@Composable
fun NoChallengeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Target,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.no_challenge_available),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.come_back_tomorrow_challenge),
            fontSize = 16.sp,
            color = MaterialTheme.colors.secondary,
            textAlign = TextAlign.Center
        )
    }
}
```

### 5. Carte Défi

```kotlin
@Composable
fun DailyChallengeCard(
    challenge: DailyChallenge,
    isCompleted: Boolean,
    isAlreadySaved: Boolean,
    showSaveConfirmation: Boolean,
    onCompleteClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Carte principale
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = 10.dp
        ) {
            Column {
                // Header rose
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
                        text = "Love2Love",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Corps sombre
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
                    Text(
                        text = challenge.localizedText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp
                    )
                }
            }
        }

        // Boutons d'action
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bouton compléter
            Button(
                onClick = onCompleteClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFFF6699)
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.challenge_completed_button),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = if (isCompleted) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bouton sauvegarder
            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFFF6699)
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.save_challenge_button),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = when {
                            showSaveConfirmation -> Icons.Default.CheckCircle
                            isAlreadySaved -> Icons.Default.Bookmark
                            else -> Icons.Default.BookmarkBorder
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .let {
                                if (showSaveConfirmation) it.scale(1.2f) else it
                            }
                    )
                }
            }
        }
    }
}
```

### 6. Strings.xml Android

```xml
<resources>
    <!-- Titres principaux -->
    <string name="daily_challenges_title">Défi du Jour</string>

    <!-- Introduction -->
    <string name="daily_challenge_intro_title">Relevez chaque défi ensemble 🎯</string>
    <string name="daily_challenge_intro_subtitle">Chaque jour, un nouveau défi pour renforcer vos liens et faire grandir votre relation à travers des actions partagées significatives.\nConnectez-vous pour commencer le voyage ensemble.</string>

    <!-- Boutons navigation -->
    <string name="connect_partner_button">Connecter mon partenaire</string>
    <string name="continue_button">Continuer</string>

    <!-- États -->
    <string name="loading_challenge">Chargement de votre défi...</string>
    <string name="no_challenge_available">Aucun défi disponible</string>
    <string name="come_back_tomorrow_challenge">Revenez demain pour découvrir un nouveau défi !</string>

    <!-- Boutons carte -->
    <string name="challenge_completed_button">Compléter le défi</string>
    <string name="save_challenge_button">Sauvegarder le défi</string>
    <string name="delete_challenge_button">Supprimer le défi</string>
</resources>
```

---

## 🎯 Points Clés du Design

✅ **Cohérence avec Questions** : Même design de carte (header rose + corps sombre)  
✅ **Image spécifique** : `"gaougaou"` pour les défis vs `"mima"` pour les questions  
✅ **Boutons d'action uniques** : Compléter + Sauvegarder avec animations  
✅ **États multiples** : Chargement, sans défi, défi disponible  
✅ **Icône favoris** : Accès rapide aux défis sauvegardés  
✅ **Hauteur carte** : 400pt (plus grande que questions à 200pt minimum)  
✅ **Ombre marquée** : `radius: 10, opacity: 0.3` (vs questions `radius: 8, opacity: 0.1`)  
✅ **Animations interactives** : Confirmation sauvegarde avec scale 1.2x  
✅ **Design Love2Love** : Palette rose cohérente dans toute l'app

Le design des défis est **identique visuellement** aux questions mais avec une **logique d'interaction différente** (compléter + sauvegarder vs favoriser + messagerie) ! 🎯✨
