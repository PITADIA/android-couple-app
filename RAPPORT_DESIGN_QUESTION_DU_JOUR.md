# 💌 DESIGN COMPLET - Question du Jour

## 🎯 Vue d'Ensemble des 3 Pages

1. **DailyQuestionIntroView** - Page d'introduction/présentation
2. **DailyQuestionMainView (NoPartner)** - Vue sans partenaire connecté
3. **DailyQuestionMainView (WithPartner)** - Vue principale avec messagerie

---

## 📱 1. PAGE D'INTRODUCTION (`DailyQuestionIntroView`)

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
        Text("daily_question_title")  // "Question of the Day"
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
    Image("mima")  // 🎯 IMAGE PRINCIPALE
        .resizable()
        .aspectRatio(contentMode: .fit)
        .frame(width: 240, height: 240)  // Taille fixe

    VStack(spacing: 12) {
        Text("daily_question_intro_title")  // "A daily love ritual"
            .font(.system(size: 22, weight: .medium))
            .foregroundColor(.black)
            .multilineTextAlignment(.center)

        Text("daily_question_intro_subtitle")  // Description longue
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
        .font(.system(size: 18, weight: .semibold))
        .foregroundColor(.white)
        .frame(maxWidth: .infinity)
        .frame(height: 56)
        .background(Color(hex: "#FD267A"))  // Rose Love2Love
        .cornerRadius(28)
}
.padding(.horizontal, 30)
.padding(.bottom, 50)
```

**Couleurs Introduction :**

- Background : `Color(red: 0.97, green: 0.97, blue: 0.98)` - RGB(247, 247, 250)
- Texte principal : `.black`
- Texte secondaire : `.black.opacity(0.7)` - Gris foncé
- Bouton : `Color(hex: "#FD267A")` - Rose Love2Love

---

## 🚫 2. PAGE SANS PARTENAIRE (`NoPartnerView`)

### 🎨 Design Global

```swift
ZStack {
    Color(white: 0.97)  // Même gris clair que l'intro
        .ignoresSafeArea()

    VStack(spacing: 0) {
        headerView  // Header avec titre centré
        // Contenu sans partenaire
    }
}
```

### 🏷️ Header Unifié

```swift
HStack {
    Button(action: { dismiss() }) {
        Image(systemName: "chevron.left")
            .font(.system(size: 20, weight: .medium))
            .foregroundColor(.black)
    }

    Spacer()

    Text("daily_question_title")  // "Question of the Day"
        .font(.system(size: 28, weight: .bold))
        .foregroundColor(.black)

    Spacer()

    Spacer().frame(width: 20)  // Équilibrage
}
.padding(.horizontal, 20)
.padding(.top, 60)
.padding(.bottom, 20)
```

### 🚫 Contenu Sans Partenaire

```swift
VStack(spacing: 5) {
    Image(systemName: "person.2.slash")  // 👥 Icône système
        .font(.system(size: 60))
        .foregroundColor(.gray)

    Text("daily_question_no_partner_title")  // "Partner Required"
        .font(.system(size: 20, weight: .semibold))
        .foregroundColor(.black)

    Text("daily_question_no_partner_message")  // Message explicatif
        .font(.system(size: 16))
        .foregroundColor(.gray)
        .multilineTextAlignment(.center)
        .padding(.horizontal, 40)
}
.frame(maxWidth: .infinity, maxHeight: .infinity)
```

**Couleurs Sans Partenaire :**

- Background : `Color(white: 0.97)` - RGB(247, 247, 247)
- Icône principale : `.gray` - Gris système
- Titre : `.black`
- Message : `.gray`
- Bouton retour : `.black`

---

## 💬 3. PAGE PRINCIPALE AVEC MESSAGERIE

### 🎨 Design Global

```swift
NavigationView {
    ZStack {
        Color(white: 0.97)  // Fond gris clair uniforme
            .ignoresSafeArea()

        VStack(spacing: 0) {
            // Header avec titre + sous-titre freemium
            // GeometryReader avec contenu principal
        }
    }
}
```

### 📋 Header Principal

```swift
HStack {
    Spacer()
    VStack(spacing: 4) {
        Text("daily_question_title")  // "Question of the Day"
            .font(.system(size: 28, weight: .bold))
            .foregroundColor(.black)

        // Sous-titre freemium dynamique
        if let subtitle = getDailyQuestionSubtitle() {
            Text(subtitle)
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.top, 4)
        }
    }
    Spacer()
}
.padding(.horizontal, 20)
.padding(.top, 16)
.padding(.bottom, 8)
```

### 🃏 Carte Question (`DailyQuestionCard`)

```swift
VStack(spacing: 0) {
    // 📍 HEADER ROSE - Design signature Love2Love
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

    // 📍 CORPS SOMBRE - Question principale
    VStack(spacing: 30) {
        Spacer()

        Text(question.localizedText)  // Texte de la question
            .font(.system(size: 22, weight: .medium))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .lineSpacing(6)
            .padding(.horizontal, 30)

        Spacer(minLength: 20)
    }
    .frame(maxWidth: .infinity)
    .frame(minHeight: 200)
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
.cornerRadius(20)
.shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 4)
.padding(.horizontal, 12)  // Marges réduites
.padding(.top, 12)         // Padding top compact
.padding(.bottom, 8)       // Espace après question
```

### 💬 Section Chat

```swift
VStack(spacing: 0) {
    if stableMessages.isEmpty {
        // Message d'encouragement
        VStack(spacing: 8) {
            Text("daily_question_start_conversation")  // "Start the conversation..."
                .font(.system(size: 16))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
        }
        .padding(.vertical, 20)
    } else {
        // Messages existants
        ForEach(Array(stableMessages.enumerated()), id: \.element.id) { index, response in
            ChatMessageView(
                response: response,
                isCurrentUser: response.userId == currentUserId,
                partnerName: response.userName,
                isLastMessage: response.id == stableMessages.last?.id,
                isPreviousSameSender: isPreviousSameSender
            )
        }
    }
}
```

### 💬 Messages Chat (`ChatMessageView`)

```swift
HStack(alignment: .bottom, spacing: 0) {
    if isCurrentUser {
        // Message utilisateur (droite)
        Spacer(minLength: 80)  // Limite largeur 70%

        VStack(alignment: .trailing, spacing: 4) {
            messageContent
                .background(
                    RoundedRectangle(cornerRadius: 18)
                        .fill(Color(hex: "#FD267A"))  // Rose Love2Love
                )
                .foregroundColor(.white)

            // Heure seulement sur dernier message
            if isLastMessage {
                Text(response.respondedAt.formatted(date: .omitted, time: .shortened))
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .padding(.trailing, 8)
            }
        }
    } else {
        // Message partenaire (gauche)
        VStack(alignment: .leading, spacing: 4) {
            messageContent
                .background(
                    RoundedRectangle(cornerRadius: 18)
                        .fill(Color(UIColor.systemGray6))  // Gris système
                )
                .foregroundColor(.primary)

            // Heure seulement sur dernier message
            if isLastMessage {
                Text(response.respondedAt.formatted(date: .omitted, time: .shortened))
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .padding(.leading, 8)
            }
        }

        Spacer(minLength: 80)  // Limite largeur 70%
    }
}
.padding(.horizontal, 16)  // Marges des bords
.padding(.vertical, isPreviousSameSender ? 1.5 : 3)  // Espacement intelligent
```

**Couleurs Chat :**

- Message utilisateur : `Color(hex: "#FD267A")` - Rose Love2Love
- Message partenaire : `Color(UIColor.systemGray6)` - Gris système clair
- Texte utilisateur : `.white`
- Texte partenaire : `.primary` (Noir adaptatif)
- Horodatage : `.secondary` (Gris système)

---

## 🎨 Palette Couleurs Complète

### Background Principal

```swift
// Toutes les vues utilisent le même fond
Color(red: 0.97, green: 0.97, blue: 0.98)  // RGB(247, 247, 250)
Color(white: 0.97)                          // RGB(247, 247, 247) - Alternative
```

### Carte Question (Dégradés)

```swift
// Header rose Love2Love
LinearGradient(colors: [
    Color(red: 1.0, green: 0.4, blue: 0.6),  // RGB(255, 102, 153)
    Color(red: 1.0, green: 0.6, blue: 0.8)   // RGB(255, 153, 204)
])

// Corps sombre sophistiqué
LinearGradient(colors: [
    Color(red: 0.2, green: 0.1, blue: 0.15),  // RGB(51, 26, 38)
    Color(red: 0.4, green: 0.2, blue: 0.3),   // RGB(102, 51, 77)
    Color(red: 0.6, green: 0.3, blue: 0.2)    // RGB(153, 77, 51)
])
```

### Messages Chat

```swift
// Bulles utilisateur
Color(hex: "#FD267A")                    // RGB(253, 38, 122) - Rose Love2Love

// Bulles partenaire
Color(UIColor.systemGray6)               // Gris système adaptatif

// Textes
.white                                   // Blanc (messages utilisateur)
.primary                                 // Noir adaptatif (messages partenaire)
.secondary                               // Gris (horodatage)
```

### Éléments UI

```swift
// Boutons principaux
Color(hex: "#FD267A")                    // RGB(253, 38, 122) - Rose Love2Love

// Textes principaux
.black                                   // Titres et textes importants

// Textes secondaires
.black.opacity(0.7)                      // RGB(0, 0, 0, 0.7) - Gris foncé
.gray                                    // Gris système
.secondary                               // Gris système adaptatif
```

---

## 🖼️ Images et Assets Utilisés

| Composant            | Asset              | Fichier        | Usage                      | Taille    |
| -------------------- | ------------------ | -------------- | -------------------------- | --------- |
| **Introduction**     | `"mima"`           | `mima.png`     | Image principale d'intro   | 240x240pt |
| **Sans partenaire**  | `"person.2.slash"` | SystemIcon     | Icône état sans partenaire | 60pt      |
| **Carte question**   | `"Love2Love"`      | Texte          | Header carte (pas d'image) | -         |
| **Exemple question** | `"leetchi2"`       | `leetchi2.png` | Logo branding (exemple)    | 24x24pt   |

**Localisation des Assets :**

```
Assets.xcassets/
├── mima.imageset/
│   └── mima.png          // Image d'introduction
└── leetchi2.imageset/
    └── leetchi2.png      // Logo branding (pour exemples)
```

---

## 🌐 Keys de Traduction (DailyQuestions.xcstrings)

### Titres Principaux

```json
"daily_question_title": {
    "fr": "Question du Jour",
    "en": "Question of the Day",
    "de": "Tagesfrage",
    "es": "Pregunta del Día"
}
```

### Page Introduction

```json
"daily_question_intro_title": {
    "fr": "Un rituel d'amour quotidien",
    "en": "A daily love ritual",
    "de": "Ein tägliches Liebesritual",
    "es": "Un ritual de amor diario"
},

"daily_question_intro_subtitle": {
    "fr": "Chaque jour, découvrez une question unique spécialement conçue pour renforcer vos liens, approfondir votre amour et nourrir la connexion qui vous unit.",
    "en": "Every day, discover a unique question specially designed to strengthen your bond, deepen your love, and nurture the connection between you.",
    "de": "Entdecken Sie jeden Tag eine einzigartige Frage, die speziell entwickelt wurde, um Ihre Bindung zu stärken, Ihre Liebe zu vertiefen und die Verbindung zwischen Ihnen zu nähren.",
    "es": "Cada día, descubre una pregunta única especialmente diseñada para fortalecer vuestros lazos, profundizar vuestro amor y nutrir la conexión que os une."
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

### État Sans Partenaire

```json
"daily_question_no_partner_title": {
    "fr": "Partenaire requis",
    "en": "Partner Required",
    "de": "Partner erforderlich",
    "es": "Pareja requerida"
},

"daily_question_no_partner_message": {
    "fr": "Vous devez vous connecter avec votre partenaire pour accéder aux questions quotidiennes.",
    "en": "You need to connect with your partner to access daily questions.",
    "de": "Sie müssen sich mit Ihrem Partner verbinden, um auf tägliche Fragen zuzugreifen.",
    "es": "Necesitas conectarte con tu pareja para acceder a las preguntas diarias."
}
```

### Messages Chat

```json
"daily_question_start_conversation": {
    "fr": "Commencez la conversation en partageant votre réponse.",
    "en": "Start the conversation by sharing your answer.",
    "de": "Beginnen Sie das Gespräch, indem Sie Ihre Antwort teilen.",
    "es": "Comienza la conversación compartiendo tu respuesta."
}
```

### Exemples Questions

```json
"daily_question_example_header": {
    "fr": "Question Quotidienne",
    "en": "Daily Question",
    "de": "Tägliche Frage",
    "es": "Pregunta Diaria"
},

"daily_question_example_text": {
    "fr": "Que pensez-vous que nous devrions prioriser pour améliorer notre relation ?",
    "en": "What do you think we should prioritize to improve our relationship?",
    "de": "Was denkst du, sollten wir priorisieren, um unsere Beziehung zu verbessern?",
    "es": "¿Qué crees que deberíamos priorizar para mejorar nuestra relación?"
}
```

---

## 📏 Espacements et Dimensions

### Layout Principal

```swift
.padding(.horizontal, 20)        // Marges latérales standard
.padding(.top, 60)              // Header top (avec safe area)
.padding(.bottom, 20)           // Header bottom
.padding(.vertical, 20)         // Espacement vertical général
```

### Carte Question

```swift
.cornerRadius(20)               // Coins arrondis carte
.shadow(radius: 8, x: 0, y: 4)  // Ombre portée
.frame(minHeight: 200)          // Hauteur minimum carte
.padding(.horizontal, 12)        // Marges latérales réduites
.padding(.vertical, 20)         // Padding interne header
.padding(.horizontal, 30)       // Padding texte question
```

### Messages Chat

```swift
.cornerRadius(18)               // Coins arrondis messages
.padding(.horizontal, 12)        // Padding interne bulle
.padding(.vertical, 8)          // Padding vertical bulle
.padding(.horizontal, 16)       // Marges latérales chat
.padding(.vertical, 1.5...3)    // Espacement entre messages
Spacer(minLength: 80)           // Limite largeur messages (70%)
```

### Boutons

```swift
.frame(height: 56)              // Hauteur standard boutons
.cornerRadius(28)               // Coins arrondis (height/2)
.padding(.horizontal, 30)       // Marges latérales
.padding(.bottom, 50)           // Marge bottom
```

---

## 🤖 Adaptation Android (Kotlin/Compose)

### 1. Structure Générale

```kotlin
@Composable
fun DailyQuestionScreen(
    state: DailyQuestionState,
    onBackClick: () -> Unit = {},
    onConnectPartner: () -> Unit = {},
    onSendMessage: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA))  // RGB(247, 247, 250)
    ) {
        when (state) {
            is DailyQuestionState.Introduction -> IntroductionContent()
            is DailyQuestionState.NoPartner -> NoPartnerContent()
            is DailyQuestionState.WithPartner -> MainContent(state.question, state.messages)
        }
    }
}
```

### 2. Page Introduction

```kotlin
@Composable
fun IntroductionContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = stringResource(R.string.daily_question_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(top = 20.dp, bottom = 100.dp)
        )

        // Image principale
        Image(
            painter = painterResource(R.drawable.mima),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Titres
        Text(
            text = stringResource(R.string.daily_question_intro_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.daily_question_intro_subtitle),
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
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 30.dp, bottom = 50.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFFFD267A)  // Rose Love2Love
            )
        ) {
            Text(
                text = buttonText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
```

### 3. Carte Question

```kotlin
@Composable
fun DailyQuestionCard(
    question: DailyQuestion,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = 4.dp
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
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF33261A),  // RGB(51, 26, 38)
                                Color(0xFF66334D),  // RGB(102, 51, 77)
                                Color(0xFF994D33)   // RGB(153, 77, 51)
                            )
                        )
                    )
                    .padding(horizontal = 30.dp, vertical = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = question.localizedText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
        }
    }
}
```

### 4. Messages Chat

```kotlin
@Composable
fun ChatMessageItem(
    response: QuestionResponse,
    isCurrentUser: Boolean,
    isLastMessage: Boolean,
    isPreviousSameSender: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = if (isPreviousSameSender) 1.5.dp else 3.dp
            ),
        horizontalArrangement = if (isCurrentUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        if (!isCurrentUser) {
            // Message partenaire
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.7f)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 1.dp
                ) {
                    Text(
                        text = response.text,
                        fontSize = 17.sp,
                        color = MaterialTheme.colors.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                if (isLastMessage) {
                    Text(
                        text = response.respondedAt.format(DateTimeFormatter.ofPattern("HH:mm")),
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }
        } else {
            // Message utilisateur
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.7f)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = Color(0xFFFD267A),  // Rose Love2Love
                    elevation = 1.dp
                ) {
                    Text(
                        text = response.text,
                        fontSize = 17.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                if (isLastMessage) {
                    Text(
                        text = response.respondedAt.format(DateTimeFormatter.ofPattern("HH:mm")),
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 8.dp, top = 4.dp)
                    )
                }
            }
        }
    }
}
```

### 5. Strings.xml Android

```xml
<resources>
    <!-- Titres principaux -->
    <string name="daily_question_title">Question du Jour</string>

    <!-- Introduction -->
    <string name="daily_question_intro_title">Un rituel d\'amour quotidien</string>
    <string name="daily_question_intro_subtitle">Chaque jour, découvrez une question unique spécialement conçue pour renforcer vos liens, approfondir votre amour et nourrir la connexion qui vous unit.</string>

    <!-- Boutons -->
    <string name="connect_partner_button">Connecter mon partenaire</string>
    <string name="continue_button">Continuer</string>

    <!-- Sans partenaire -->
    <string name="daily_question_no_partner_title">Partenaire requis</string>
    <string name="daily_question_no_partner_message">Vous devez vous connecter avec votre partenaire pour accéder aux questions quotidiennes.</string>

    <!-- Chat -->
    <string name="daily_question_start_conversation">Commencez la conversation en partageant votre réponse.</string>

    <!-- Exemples -->
    <string name="daily_question_example_header">Question Quotidienne</string>
    <string name="daily_question_example_text">Que pensez-vous que nous devrions prioriser pour améliorer notre relation ?</string>
</resources>
```

---

## 🎯 Points Clés du Design

✅ **Cohérence visuelle** : Même fond gris clair sur toutes les pages  
✅ **Identité forte** : Dégradé rose Love2Love sur header carte  
✅ **Contraste sophistiqué** : Corps sombre multi-couleur pour lisibilité  
✅ **Chat moderne** : Bulles asymétriques avec limitation largeur 70%  
✅ **Typographie hiérarchisée** : 28pt titre, 22pt question, 18pt header, 16-17pt contenu  
✅ **Espacements intelligents** : Moins d'espace entre messages du même expéditeur  
✅ **Ombres subtiles** : Profondeur sans surcharge  
✅ **Images optimisées** : 240x240pt pour intro, 24x24pt pour branding  
✅ **Multi-langues** : 28+ clés de traduction dans DailyQuestions.xcstrings

Le design est vraiment sophistiqué avec cette identité visuelle Love2Love omniprésente ! 💌✨
