# 🎯 RAPPORT COMPLET - DESIGN COLLECTIONS ET CARTES

## 📋 Table des Matières

1. [🎨 Architecture des Collections](#architecture-collections)
2. [📱 CategoryListCardView - Cartes Liste Rectangulaires](#category-list-cards)
3. [⚫ CategoryCardView - Cartes Noires Carrées](#category-cards)
4. [🃏 QuestionCardView - Cartes de Questions](#question-cards)
5. [💰 FreemiumPaywallCardView - Carte Paywall](#paywall-cards)
6. [🏆 PackCompletionCardView - Carte Fin de Pack](#completion-cards)
7. [🎨 Système de Couleurs par Catégorie](#color-system)
8. [🔑 Clés XCStrings par Collection](#xcstrings-keys)
9. [📏 Design System Unifié](#design-system)
10. [🤖 Adaptation Android](#android-adaptation)

---

## 🎨 Architecture des Collections {#architecture-collections}

### Structure des Catégories

| ID Catégorie              | Nom Français        | Emoji | Statut      | Couleurs Gradient     |
| ------------------------- | ------------------- | ----- | ----------- | --------------------- |
| **`en-couple`**           | En couple           | 💞    | **GRATUIT** | `#E91E63` → `#F06292` |
| **`les-plus-hots`**       | Désirs inavoués     | 🌶️    | **PREMIUM** | `#FF6B35` → `#F7931E` |
| **`a-distance`**          | À distance          | ✈️    | **PREMIUM** | `#00BCD4` → `#26C6DA` |
| **`questions-profondes`** | Questions profondes | ✨    | **PREMIUM** | `#FFD700` → `#FFA500` |
| **`pour-rire-a-deux`**    | Pour rire à deux    | 😂    | **PREMIUM** | `#FFD700` → `#FFA500` |
| **`tu-preferes`**         | Tu préfères         | 🤍    | **PREMIUM** | `#9B59B6` → `#8E44AD` |
| **`mieux-ensemble`**      | Mieux ensemble      | 💌    | **PREMIUM** | `#673AB7` → `#9C27B0` |
| **`pour-un-date`**        | Pour un date        | 🍸    | **PREMIUM** | `#3498DB` → `#2980B9` |

### Clés XCStrings par Catégorie

#### Collection "En couple" (GRATUIT)

```xml
<string name="category_en_couple_title">En couple</string>
<string name="category_en_couple_subtitle">Questions pour couples amoureux</string>
```

#### Collection "Désirs inavoués" (PREMIUM)

```xml
<string name="category_desirs_inavoues_title">Désirs inavoués</string>
<string name="category_desirs_inavoues_subtitle">Questions intimes et sensuelles</string>
```

#### Collection "À distance" (PREMIUM)

```xml
<string name="category_a_distance_title">À distance</string>
<string name="category_a_distance_subtitle">Maintenir la connexion à distance</string>
```

#### Collection "Questions profondes" (PREMIUM)

```xml
<string name="category_questions_profondes_title">Questions profondes</string>
<string name="category_questions_profondes_subtitle">Explorer vos âmes</string>
```

#### Collection "Pour rire à deux" (PREMIUM)

```xml
<string name="category_pour_rire_title">Pour rire à deux</string>
<string name="category_pour_rire_subtitle">Moments de complicité amusants</string>
```

#### Collection "Tu préfères" (PREMIUM)

```xml
<string name="category_tu_preferes_title">Tu préfères</string>
<string name="category_tu_preferes_subtitle">Choix difficiles en couple</string>
```

#### Collection "Mieux ensemble" (PREMIUM)

```xml
<string name="category_mieux_ensemble_title">Mieux ensemble</string>
<string name="category_mieux_ensemble_subtitle">Guérir et renforcer l'amour</string>
```

#### Collection "Pour un date" (PREMIUM)

```xml
<string name="category_pour_un_date_title">Pour un date</string>
<string name="category_pour_un_date_subtitle">Questions pour vos soirées</string>
```

---

## 📱 CategoryListCardView - Cartes Liste Rectangulaires {#category-list-cards}

### Design Architecture

```swift
HStack(spacing: 16) {
    // Contenu principal
    VStack(alignment: .leading, spacing: 6) {
        // Titre principal
        Text(category.title)
            .font(.system(size: 20, weight: .bold))
            .foregroundColor(.black)
            .multilineTextAlignment(.leading)

        // Sous-titre avec cadenas premium
        HStack(spacing: 4) {
            Text(category.subtitle)
                .font(.system(size: 14))
                .foregroundColor(.gray)
                .multilineTextAlignment(.leading)

            // Cadenas premium
            if category.isPremium && !isSubscribed {
                Text("🔒")
                    .font(.system(size: 14))
            }
        }
    }

    Spacer()

    // Emoji à droite
    Text(category.emoji)
        .font(.system(size: 28))
}
.padding(.horizontal, 24)
.padding(.vertical, 20)
.background(
    RoundedRectangle(cornerRadius: 16)
        .fill(Color.white.opacity(0.95))
        .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
)
```

### Spécifications Design

| Élément                | Valeur                                | Détails                          |
| ---------------------- | ------------------------------------- | -------------------------------- |
| **Corner Radius**      | 16pt                                  | Coins arrondis                   |
| **Padding Horizontal** | 24pt                                  | Espacement interne gauche/droite |
| **Padding Vertical**   | 20pt                                  | Espacement interne haut/bas      |
| **Spacing Internal**   | 16pt                                  | Entre contenu et emoji           |
| **Background**         | `Color.white.opacity(0.95)`           | Blanc semi-transparent           |
| **Shadow**             | `radius: 8, x: 0, y: 2, opacity: 0.1` | Ombre légère                     |
| **Button Style**       | `PlainButtonStyle()`                  | Pas d'effet de pression          |

### Typographie

| Élément             | Font   | Taille | Poids   | Couleur  |
| ------------------- | ------ | ------ | ------- | -------- |
| **Titre**           | System | 20pt   | Bold    | Noir     |
| **Sous-titre**      | System | 14pt   | Regular | Gris     |
| **Emoji**           | System | 28pt   | -       | Original |
| **Cadenas Premium** | System | 14pt   | -       | Émoji 🔒 |

### États Visuels

- **Normal** : Fond blanc transparent, ombre légère
- **Premium Verrouillé** : Cadenas 🔒 visible après le sous-titre
- **Tap** : Gestion via FreemiumManager (paywall si premium non payé)

---

## ⚫ CategoryCardView - Cartes Noires Carrées {#category-cards}

### Design Architecture

```swift
ZStack {
    // Fond noir avec bordure blanche
    RoundedRectangle(cornerRadius: 20)
        .fill(Color.black)
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white, lineWidth: 2)
        )

    VStack(spacing: 10) {
        // Emoji en haut
        Text(category.emoji)
            .font(.system(size: 40))

        // Titre
        Text(category.title)
            .font(.system(size: 18, weight: .bold))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .lineLimit(nil)

        // Sous-titre avec cadenas premium
        VStack(spacing: 4) {
            HStack(spacing: 4) {
                Text(category.subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                    .lineLimit(nil)
                    .lineSpacing(2)

                // Cadenas premium
                if category.isPremium && !isSubscribed {
                    Text("🔒")
                        .font(.system(size: 12))
                }
            }
        }
    }
    .padding(.horizontal, 12)
    .padding(.vertical, 16)
}
```

### Spécifications Design

| Élément                | Valeur                      | Détails                          |
| ---------------------- | --------------------------- | -------------------------------- |
| **Corner Radius**      | 20pt                        | Coins arrondis                   |
| **Background**         | `Color.black`               | Fond noir plein                  |
| **Border**             | `Color.white, lineWidth: 2` | Bordure blanche 2pt              |
| **Padding Horizontal** | 12pt                        | Espacement interne gauche/droite |
| **Padding Vertical**   | 16pt                        | Espacement interne haut/bas      |
| **Internal Spacing**   | 10pt                        | Entre emoji, titre, sous-titre   |
| **Subtitle Spacing**   | 4pt                         | Dans le VStack du sous-titre     |
| **Scale Effect**       | 1.0                         | Pas d'animation d'échelle        |

### Typographie

| Élément             | Font   | Taille | Poids   | Couleur  |
| ------------------- | ------ | ------ | ------- | -------- |
| **Emoji**           | System | 40pt   | -       | Original |
| **Titre**           | System | 18pt   | Bold    | Blanc    |
| **Sous-titre**      | System | 12pt   | Regular | Gris     |
| **Cadenas Premium** | System | 12pt   | -       | Émoji 🔒 |

### Dimensions Standard

- **Largeur** : 160pt
- **Hauteur** : 200pt
- **Usage** : Grille de catégories, onboarding preview

---

## 🃏 QuestionCardView - Cartes de Questions {#question-cards}

### Design Architecture

```swift
VStack(spacing: 0) {
    // Header avec nom de catégorie
    VStack(spacing: 8) {
        Text(category.title)
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

    // Corps avec question
    VStack(spacing: 30) {
        Spacer()

        Text(question.text)
            .font(.system(size: 22, weight: .medium))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .lineSpacing(6)
            .padding(.horizontal, 30)

        Spacer()

        // Branding Love2Love
        HStack(spacing: 8) {
            Image("leetchi2") // 24x24pt
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
.frame(maxWidth: .infinity)
.frame(height: 500)
.cornerRadius(20)
.shadow(
    color: .black.opacity(isBackground ? 0.1 : 0.3),
    radius: isBackground ? 5 : 10,
    x: 0,
    y: isBackground ? 2 : 5
)
```

### Header Gradient (Rose)

| Couleur       | Valeur RGB               | Usage      |
| ------------- | ------------------------ | ---------- |
| **Start**     | `rgb(1.0, 0.4, 0.6)`     | Rose foncé |
| **End**       | `rgb(1.0, 0.6, 0.8)`     | Rose clair |
| **Direction** | `.leading` → `.trailing` | Horizontal |

### Body Gradient (Sombre)

| Couleur       | Valeur RGB            | Usage       |
| ------------- | --------------------- | ----------- |
| **Top**       | `rgb(0.2, 0.1, 0.15)` | Brun sombre |
| **Middle**    | `rgb(0.4, 0.2, 0.3)`  | Brun moyen  |
| **Bottom**    | `rgb(0.6, 0.3, 0.2)`  | Brun clair  |
| **Direction** | `.top` → `.bottom`    | Vertical    |

### Spécifications Design

| Élément                     | Valeur | Détails                    |
| --------------------------- | ------ | -------------------------- |
| **Hauteur Totale**          | 500pt  | Fixe                       |
| **Corner Radius**           | 20pt   | Coins arrondis             |
| **Header Padding Vertical** | 20pt   | Espacement titre catégorie |
| **Body Spacing**            | 30pt   | Entre éléments internes    |
| **Question Padding**        | 30pt   | Horizontal pour le texte   |
| **Line Spacing**            | 6pt    | Espacement lignes question |
| **Branding Padding**        | 30pt   | Bottom pour le logo        |

### Typographie Questions

| Élément             | Font   | Taille | Poids    | Couleur   |
| ------------------- | ------ | ------ | -------- | --------- |
| **Titre Catégorie** | System | 18pt   | Bold     | Blanc     |
| **Texte Question**  | System | 22pt   | Medium   | Blanc     |
| **Logo Text**       | System | 16pt   | Semibold | Blanc 90% |

### Images Utilisées

- **`leetchi2`** : Logo Love2Love 24x24pt dans le branding

---

## 💰 FreemiumPaywallCardView - Carte Paywall {#paywall-cards}

### Design Architecture

```swift
VStack(spacing: 30) {
    Spacer()

    VStack(spacing: 20) {
        // Emoji de la catégorie
        Text(category.emoji)
            .font(.system(size: 60))
            .padding(.bottom, 10)

        Text("congratulations".localized)
            .font(.system(size: 32, weight: .bold))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 30)

        Text("keep_going_unlock_all".localized)
            .font(.system(size: 18))
            .foregroundColor(.white.opacity(0.9))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 30)

        // Bouton CTA avec gradient
        HStack(spacing: 8) {
            Text("continue".localized)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            Image(systemName: "arrow.right.circle.fill")
                .font(.system(size: 20))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)
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
        .padding(.top, 20)
    }

    Spacer()

    // Branding
    HStack(spacing: 8) {
        Image("leetchi2")
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: 24, height: 24)

        Text("app_name".localized)
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(.white.opacity(0.9))
    }
    .padding(.bottom, 30)
}
.frame(maxWidth: .infinity, maxHeight: .infinity)
.frame(height: 500)
.cornerRadius(20)
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
.overlay(
    RoundedRectangle(cornerRadius: 20)
        .stroke(
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(hex: "#FD267A"),
                    Color(hex: "#FF655B")
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            lineWidth: 3
        )
)
.shadow(color: .black.opacity(0.3), radius: 10, x: 0, y: 5)
```

### Gradient CTA Button

| Couleur       | Hex                      | Usage        |
| ------------- | ------------------------ | ------------ |
| **Start**     | `#FD267A`                | Rose/Magenta |
| **End**       | `#FF655B`                | Orange/Rouge |
| **Direction** | `.leading` → `.trailing` | Horizontal   |

### Gradient Border

| Couleur        | Hex                               | Usage           |
| -------------- | --------------------------------- | --------------- |
| **Start**      | `#FD267A`                         | Rose/Magenta    |
| **End**        | `#FF655B`                         | Orange/Rouge    |
| **Direction**  | `.topLeading` → `.bottomTrailing` | Diagonal        |
| **Line Width** | 3pt                               | Bordure épaisse |

### Clés XCStrings Paywall

```xml
<string name="congratulations">Félicitations !</string>
<string name="keep_going_unlock_all">Continuez et débloquez tout</string>
<string name="continue">Continuer</string>
<string name="app_name">Love2Love</string>
```

### Typographie Paywall

| Élément             | Font   | Taille | Poids    | Couleur   |
| ------------------- | ------ | ------ | -------- | --------- |
| **Emoji**           | System | 60pt   | -        | Original  |
| **Titre Principal** | System | 32pt   | Bold     | Blanc     |
| **Sous-titre**      | System | 18pt   | Regular  | Blanc 90% |
| **Bouton CTA**      | System | 18pt   | Bold     | Blanc     |
| **Icon CTA**        | System | 20pt   | -        | Blanc     |
| **Logo Text**       | System | 16pt   | Semibold | Blanc 90% |

---

## 🏆 PackCompletionCardView - Carte Fin de Pack {#completion-cards}

### Design Architecture

```swift
VStack(spacing: 30) {
    Spacer()

    VStack(spacing: 20) {
        Text("congratulations_pack".localized)
            .font(.system(size: 36, weight: .bold))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)

        Text("pack_completed".localized)
            .font(.system(size: 18))
            .foregroundColor(.white.opacity(0.9))
            .multilineTextAlignment(.center)

        // Flamme animée
        Text("🔥")
            .font(.system(size: 60))
            .scaleEffect(flameAnimation ? 1.3 : 0.9)
            .rotationEffect(.degrees(flameAnimation ? 15 : -15))
            .offset(y: flameAnimation ? -5 : 5)
            .shadow(color: .orange, radius: flameAnimation ? 10 : 5)
            .onAppear {
                withAnimation(
                    Animation.easeInOut(duration: 0.6)
                        .repeatForever(autoreverses: true)
                ) {
                    flameAnimation = true
                }
            }

        Text("tap_unlock_surprise".localized)
            .font(.system(size: 16, weight: .medium))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 20)
    }

    Spacer()

    // Branding identique aux autres cartes
    HStack(spacing: 8) {
        Image("leetchi2")
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
.frame(height: 500)
.cornerRadius(20)
.overlay(
    RoundedRectangle(cornerRadius: 20)
        .stroke(Color.white, lineWidth: 3)
        .shadow(color: .white.opacity(0.5), radius: 8, x: 0, y: 0)
)
.shadow(color: .black.opacity(0.3), radius: 10, x: 0, y: 5)
```

### Animation Flamme 🔥

| Propriété         | Valeur Min | Valeur Max | Duration | Type         |
| ----------------- | ---------- | ---------- | -------- | ------------ |
| **Scale**         | 0.9        | 1.3        | 0.6s     | easeInOut    |
| **Rotation**      | -15°       | +15°       | 0.6s     | easeInOut    |
| **Offset Y**      | +5pt       | -5pt       | 0.6s     | easeInOut    |
| **Shadow Radius** | 5pt        | 10pt       | 0.6s     | easeInOut    |
| **Repeat**        | Infini     | -          | -        | autoreverses |

### Border & Shadow Completion

| Élément           | Couleur   | Width/Radius | Effet          |
| ----------------- | --------- | ------------ | -------------- |
| **Border**        | Blanc     | 3pt          | Stroke simple  |
| **Border Shadow** | Blanc 50% | 8pt          | Effet lumineux |
| **Card Shadow**   | Noir 30%  | 10pt         | Ombre standard |

### Clés XCStrings Completion

```xml
<string name="congratulations_pack">Félicitations !</string>
<string name="pack_completed">Pack terminé</string>
<string name="tap_unlock_surprise">Appuyez pour débloquer la surprise</string>
```

### Typographie Completion

| Élément             | Font   | Taille | Poids    | Couleur   |
| ------------------- | ------ | ------ | -------- | --------- |
| **Titre Principal** | System | 36pt   | Bold     | Blanc     |
| **Sous-titre**      | System | 18pt   | Regular  | Blanc 90% |
| **Flamme**          | System | 60pt   | -        | Émoji 🔥  |
| **Instructions**    | System | 16pt   | Medium   | Blanc     |
| **Logo Text**       | System | 16pt   | Semibold | Blanc 90% |

---

## 🎨 Système de Couleurs par Catégorie {#color-system}

### Couleurs Principales

| Nom                      | Hex       | Usage                   | RGB               |
| ------------------------ | --------- | ----------------------- | ----------------- |
| **Background App**       | -         | `rgb(0.97, 0.97, 0.98)` | Gris très clair   |
| **Questions Background** | -         | `rgb(0.15, 0.03, 0.08)` | Rouge/Brun sombre |
| **Primary Rose**         | `#FD267A` | CTA, accents            | Rose/Magenta      |
| **Secondary Orange**     | `#FF655B` | Fin gradient            | Orange/Rouge      |

### Gradients par Collection

#### En couple (💞)

```swift
LinearGradient(colors: [
    Color(hex: "#E91E63"), // Rose foncé
    Color(hex: "#F06292")  // Rose clair
])
```

#### Désirs inavoués (🌶️)

```swift
LinearGradient(colors: [
    Color(hex: "#FF6B35"), // Orange foncé
    Color(hex: "#F7931E")  // Orange clair
])
```

#### À distance (✈️)

```swift
LinearGradient(colors: [
    Color(hex: "#00BCD4"), // Cyan foncé
    Color(hex: "#26C6DA")  // Cyan clair
])
```

#### Questions profondes (✨)

```swift
LinearGradient(colors: [
    Color(hex: "#FFD700"), // Or foncé
    Color(hex: "#FFA500")  // Orange doré
])
```

#### Pour rire à deux (😂)

```swift
LinearGradient(colors: [
    Color(hex: "#FFD700"), // Or (identique profondes)
    Color(hex: "#FFA500")  // Orange doré
])
```

#### Tu préfères (🤍)

```swift
LinearGradient(colors: [
    Color(hex: "#9B59B6"), // Violet foncé
    Color(hex: "#8E44AD")  // Violet clair
])
```

#### Mieux ensemble (💌)

```swift
LinearGradient(colors: [
    Color(hex: "#673AB7"), // Violet indigo foncé
    Color(hex: "#9C27B0")  // Violet magenta
])
```

#### Pour un date (🍸)

```swift
LinearGradient(colors: [
    Color(hex: "#3498DB"), // Bleu foncé
    Color(hex: "#2980B9")  // Bleu clair
])
```

### Background Questions Universal

```swift
LinearGradient(colors: [
    Color(red: 0.2, green: 0.1, blue: 0.15), // Brun sombre
    Color(red: 0.4, green: 0.2, blue: 0.3),  // Brun moyen
    Color(red: 0.6, green: 0.3, blue: 0.2)   // Brun clair
])
```

### Header Questions Universal

```swift
LinearGradient(colors: [
    Color(red: 1.0, green: 0.4, blue: 0.6), // Rose foncé
    Color(red: 1.0, green: 0.6, blue: 0.8)  // Rose clair
])
```

---

## 🔑 Clés XCStrings par Collection {#xcstrings-keys}

### UI Générale Collections

```xml
<!-- Navigation et compteurs -->
<string name="on_count">sur</string>

<!-- Actions favoris -->
<string name="add_to_favorites">Ajouter aux favoris</string>
<string name="remove_from_favorites">Retirer des favoris</string>

<!-- États système -->
<string name="locked_content">Contenu verrouillé</string>
<string name="subscribe_access_questions">Abonnez-vous pour accéder</string>

<!-- Paywall freemium -->
<string name="congratulations">Félicitations !</string>
<string name="keep_going_unlock_all">Continuez et débloquez tout</string>
<string name="continue">Continuer</string>
<string name="app_name">Love2Love</string>

<!-- Fin de pack -->
<string name="congratulations_pack">Félicitations !</string>
<string name="pack_completed">Pack terminé</string>
<string name="tap_unlock_surprise">Appuyez pour débloquer la surprise</string>
```

### Catégories String Catalogs

#### EnCouple.xcstrings

```xml
<!-- Préfixe: ec_ -->
<string name="ec_2">Première question en couple...</string>
<string name="ec_3">Deuxième question en couple...</string>
<!-- ... jusqu'à ec_300+ -->
```

#### LesPlus Hots.xcstrings

```xml
<!-- Préfixe: lph_ -->
<string name="lph_2">Première question sensuelle...</string>
<string name="lph_3">Deuxième question sensuelle...</string>
<!-- ... jusqu'à lph_300+ -->
```

#### ADistance.xcstrings

```xml
<!-- Préfixe: ad_ -->
<string name="ad_2">Première question distance...</string>
<string name="ad_3">Deuxième question distance...</string>
<!-- ... jusqu'à ad_300+ -->
```

#### QuestionsProfondes.xcstrings

```xml
<!-- Préfixe: qp_ -->
<string name="qp_2">Première question profonde...</string>
<string name="qp_3">Deuxième question profonde...</string>
<!-- ... jusqu'à qp_300+ -->
```

#### PourRire.xcstrings

```xml
<!-- Préfixe: prad_ -->
<string name="prd_2">Première question drôle...</string>
<string name="prd_3">Deuxième question drôle...</string>
<!-- ... jusqu'à prd_300+ -->
```

#### TuPreferes.xcstrings

```xml
<!-- Préfixe: tp_ -->
<string name="tp_2">Tu préfères A ou B...</string>
<string name="tp_3">Tu préfères C ou D...</string>
<!-- ... jusqu'à tp_300+ -->
```

#### MieuxEnsemble.xcstrings

```xml
<!-- Préfixe: me_ -->
<string name="me_2">Première question guérison...</string>
<string name="me_3">Deuxième question guérison...</string>
<!-- ... jusqu'à me_300+ -->
```

#### PourUnDate.xcstrings

```xml
<!-- Préfixe: pud_ -->
<string name="pud_2">Première question date...</string>
<string name="pud_3">Deuxième question date...</string>
<!-- ... jusqu'à pud_300+ -->
```

---

## 📏 Design System Unifié {#design-system}

### Typographie Hiérarchique

| Niveau      | Font   | Taille | Poids   | Usage                    |
| ----------- | ------ | ------ | ------- | ------------------------ |
| **H1**      | System | 36pt   | Bold    | Titres principaux cartes |
| **H2**      | System | 32pt   | Bold    | Paywall titre            |
| **H3**      | System | 22pt   | Medium  | Texte questions          |
| **H4**      | System | 20pt   | Bold    | Titres cartes liste      |
| **H5**      | System | 18pt   | Bold    | Titres cartes noires     |
| **H6**      | System | 18pt   | Regular | Sous-titres paywall      |
| **Body**    | System | 16pt   | Medium  | Instructions             |
| **Caption** | System | 14pt   | Regular | Sous-titres              |
| **Small**   | System | 12pt   | Regular | Détails                  |

### Spacing Système

| Nom      | Valeur | Usage                       |
| -------- | ------ | --------------------------- |
| **XXS**  | 4pt    | Éléments très proches       |
| **XS**   | 6pt    | Line spacing                |
| **S**    | 8pt    | Espacement cartes           |
| **M**    | 10pt   | Espacement VStack cartes    |
| **L**    | 16pt   | Espacement général          |
| **XL**   | 20pt   | Padding vertical            |
| **XXL**  | 24pt   | Padding horizontal          |
| **XXXL** | 30pt   | Espacement cartes questions |

### Corner Radius Standards

| Élément             | Radius | Usage                         |
| ------------------- | ------ | ----------------------------- |
| **Cards Liste**     | 16pt   | CategoryListCardView          |
| **Cards Noires**    | 20pt   | CategoryCardView              |
| **Cards Questions** | 20pt   | QuestionCardView, PaywallCard |
| **Boutons CTA**     | 25pt   | Boutons paywall               |
| **Boutons Favoris** | 28pt   | Bouton ajouter favoris        |

### Shadow Standards

| Type                 | Color                | Radius | Offset     | Usage                  |
| -------------------- | -------------------- | ------ | ---------- | ---------------------- |
| **Light**            | `black.opacity(0.1)` | 8pt    | `x:0, y:2` | Cards liste            |
| **Medium**           | `black.opacity(0.3)` | 10pt   | `x:0, y:5` | Cards actives          |
| **Background Cards** | `black.opacity(0.1)` | 5pt    | `x:0, y:2` | Cards arrière-plan     |
| **Glow**             | `white.opacity(0.5)` | 8pt    | `x:0, y:0` | Border glow completion |
| **Flame**            | `orange`             | 5-10pt | Animé      | Flamme animation       |

---

## 🤖 Adaptation Android {#android-adaptation}

### Architecture Android

#### 1. Structure des Packages

```kotlin
com.love2loveapp.collections/
├── presentation/
│   ├── CollectionListActivity.kt
│   ├── QuestionFlowActivity.kt
│   └── composables/
│       ├── CategoryListCard.kt
│       ├── CategoryGridCard.kt
│       ├── QuestionCard.kt
│       ├── PaywallCard.kt
│       └── CompletionCard.kt
├── data/
│   ├── models/
│   │   ├── QuestionCategory.kt
│   │   ├── Question.kt
│   │   └── CategoryProgress.kt
│   ├── repositories/
│   │   ├── CategoryRepository.kt
│   │   └── QuestionRepository.kt
│   └── local/
│       └── CategoryDao.kt
├── domain/
│   ├── usecases/
│   │   ├── GetCategoriesUseCase.kt
│   │   ├── GetQuestionsUseCase.kt
│   │   └── ManageProgressUseCase.kt
│   └── models/
└── utils/
    ├── CollectionTheme.kt
    └── GradientUtils.kt
```

#### 2. QuestionCategory Model

```kotlin
data class QuestionCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val gradientColors: List<String>,
    val isPremium: Boolean
) {
    companion object {
        val categories = listOf(
            QuestionCategory(
                id = "en-couple",
                title = "En couple",
                subtitle = "Questions pour couples amoureux",
                emoji = "💞",
                gradientColors = listOf("#E91E63", "#F06292"),
                isPremium = false
            ),
            QuestionCategory(
                id = "les-plus-hots",
                title = "Désirs inavoués",
                subtitle = "Questions intimes et sensuelles",
                emoji = "🌶️",
                gradientColors = listOf("#FF6B35", "#F7931E"),
                isPremium = true
            ),
            // ... autres catégories
        )
    }
}
```

#### 3. CategoryListCard Composable

```kotlin
@Composable
fun CategoryListCard(
    category: QuestionCategory,
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contenu principal
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Titre principal
                Text(
                    text = stringResource(getCategoryTitleRes(category.id)),
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )

                // Sous-titre avec cadenas
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(getCategorySubtitleRes(category.id)),
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = Color.Gray
                        ),
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Cadenas premium
                    if (category.isPremium && !isSubscribed) {
                        Text(
                            text = "🔒",
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Emoji
            Text(
                text = category.emoji,
                fontSize = 28.sp
            )
        }
    }
}
```

#### 4. CategoryGridCard Composable

```kotlin
@Composable
fun CategoryGridCard(
    category: QuestionCategory,
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(width = 160.dp, height = 200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(2.dp, Color.White),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Emoji
            Text(
                text = category.emoji,
                fontSize = 40.sp
            )

            // Titre
            Text(
                text = stringResource(getCategoryTitleRes(category.id)),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                ),
                maxLines = Int.MAX_VALUE
            )

            // Sous-titre avec cadenas
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(getCategorySubtitleRes(category.id)),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    ),
                    maxLines = Int.MAX_VALUE
                )

                if (category.isPremium && !isSubscribed) {
                    Text(
                        text = "🔒",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
```

#### 5. QuestionCard Composable

```kotlin
@Composable
fun QuestionCard(
    question: Question,
    category: QuestionCategory,
    isBackground: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isBackground) 5.dp else 10.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header avec gradient rose
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFF6699), // rgb(1.0, 0.4, 0.6)
                                Color(0xFFFF99CC)  // rgb(1.0, 0.6, 0.8)
                            )
                        )
                    )
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(getCategoryTitleRes(category.id)),
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                )
            }

            // Corps avec gradient sombre
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF331A26), // rgb(0.2, 0.1, 0.15)
                                Color(0xFF66334D), // rgb(0.4, 0.2, 0.3)
                                Color(0xFF994D33)  // rgb(0.6, 0.3, 0.2)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    // Texte de la question
                    Text(
                        text = question.text,
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Branding
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_leetchi2),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )

                        Text(
                            text = "Love2Love",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        )
                    }
                }
            }
        }
    }
}
```

#### 6. PaywallCard Composable

```kotlin
@Composable
fun FreemiumPaywallCard(
    category: QuestionCategory,
    questionsUnlocked: Int,
    totalQuestions: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 3.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFD267A),
                    Color(0xFFFF655B)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        onClick = onTap
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF331A26),
                            Color(0xFF66334D),
                            Color(0xFF994D33)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Emoji de la catégorie
                    Text(
                        text = category.emoji,
                        fontSize = 60.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Text(
                        text = stringResource(R.string.congratulations),
                        style = TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    )

                    Text(
                        text = stringResource(R.string.keep_going_unlock_all),
                        style = TextStyle(
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    )

                    // Bouton CTA
                    Button(
                        onClick = onTap,
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFD267A),
                                        Color(0xFFFF655B)
                                    )
                                ),
                                shape = RoundedCornerShape(25.dp)
                            )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.continue_btn),
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )

                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Branding
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_leetchi2),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )

                    Text(
                        text = stringResource(R.string.app_name),
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    )
                }
            }
        }
    }
}
```

#### 7. Ressources Android strings.xml

```xml
<resources>
    <!-- Catégories Titres -->
    <string name="category_en_couple_title">En couple</string>
    <string name="category_desirs_inavoues_title">Désirs inavoués</string>
    <string name="category_a_distance_title">À distance</string>
    <string name="category_questions_profondes_title">Questions profondes</string>
    <string name="category_pour_rire_title">Pour rire à deux</string>
    <string name="category_tu_preferes_title">Tu préfères</string>
    <string name="category_mieux_ensemble_title">Mieux ensemble</string>
    <string name="category_pour_un_date_title">Pour un date</string>

    <!-- Catégories Sous-titres -->
    <string name="category_en_couple_subtitle">Questions pour couples amoureux</string>
    <string name="category_desirs_inavoues_subtitle">Questions intimes et sensuelles</string>
    <string name="category_a_distance_subtitle">Maintenir la connexion à distance</string>
    <string name="category_questions_profondes_subtitle">Explorer vos âmes</string>
    <string name="category_pour_rire_subtitle">Moments de complicité amusants</string>
    <string name="category_tu_preferes_subtitle">Choix difficiles en couple</string>
    <string name="category_mieux_ensemble_subtitle">Guérir et renforcer l\'amour</string>
    <string name="category_pour_un_date_subtitle">Questions pour vos soirées</string>

    <!-- UI Générale -->
    <string name="on_count">sur</string>
    <string name="add_to_favorites">Ajouter aux favoris</string>
    <string name="remove_from_favorites">Retirer des favoris</string>
    <string name="locked_content">Contenu verrouillé</string>
    <string name="subscribe_access_questions">Abonnez-vous pour accéder</string>

    <!-- Paywall -->
    <string name="congratulations">Félicitations !</string>
    <string name="keep_going_unlock_all">Continuez et débloquez tout</string>
    <string name="continue_btn">Continuer</string>
    <string name="app_name">Love2Love</string>

    <!-- Fin de pack -->
    <string name="congratulations_pack">Félicitations !</string>
    <string name="pack_completed">Pack terminé</string>
    <string name="tap_unlock_surprise">Appuyez pour débloquer la surprise</string>

    <!-- Questions par catégorie -->
    <!-- EnCouple -->
    <string name="ec_2">Quelle est la chose la plus romantique que je pourrais faire pour toi ?</string>
    <string name="ec_3">Quel est ton souvenir préféré de nous deux ?</string>

    <!-- LesPlus Hots -->
    <string name="lph_2">Qu\'est-ce qui t\'excite le plus chez moi ?</string>
    <string name="lph_3">Quel est ton fantasme secret ?</string>

    <!-- ADistance -->
    <string name="ad_2">Comment garder notre connexion forte malgré la distance ?</string>
    <string name="ad_3">Que feras-tu en premier quand nous nous reverrons ?</string>

    <!-- QuestionsProfondes -->
    <string name="qp_2">Quelle est ta plus grande peur dans la vie ?</string>
    <string name="qp_3">Qu\'est-ce qui donne vraiment du sens à ta vie ?</string>

    <!-- PourRire -->
    <string name="prd_2">Quelle est la chose la plus embarrassante qui te soit arrivée ?</string>
    <string name="prd_3">Si tu étais un super-héros, quel serait ton pouvoir ?</string>

    <!-- TuPreferes -->
    <string name="tp_2">Tu préfères être invisible ou pouvoir voler ?</string>
    <string name="tp_3">Tu préfères vivre dans le passé ou dans le futur ?</string>

    <!-- MieuxEnsemble -->
    <string name="me_2">Comment puis-je mieux te soutenir quand tu es triste ?</string>
    <string name="me_3">Quelle habitude aimerais-tu que nous développions ensemble ?</string>

    <!-- PourUnDate -->
    <string name="pud_2">Quel est ton restaurant idéal pour un rendez-vous ?</string>
    <string name="pud_3">Quelle activité aimerais-tu qu\'on essaie ensemble ?</string>
</resources>
```

#### 8. Theme.kt Collections

```kotlin
object CollectionColors {
    // Primary colors
    val Background = Color(0xFFF7F7F8) // rgb(0.97, 0.97, 0.98)
    val QuestionsBackground = Color(0xFF260508) // rgb(0.15, 0.03, 0.08)
    val PrimaryRose = Color(0xFFFD267A)
    val SecondaryOrange = Color(0xFFFF655B)

    // Category gradients
    val EnCoupleGradient = listOf(Color(0xFFE91E63), Color(0xFFF06292))
    val DesirsGradient = listOf(Color(0xFFFF6B35), Color(0xFFF7931E))
    val DistanceGradient = listOf(Color(0xFF00BCD4), Color(0xFF26C6DA))
    val ProfondGradient = listOf(Color(0xFFFFD700), Color(0xFFFFA500))
    val RireGradient = listOf(Color(0xFFFFD700), Color(0xFFFFA500))
    val PreferesGradient = listOf(Color(0xFF9B59B6), Color(0xFF8E44AD))
    val EnsembleGradient = listOf(Color(0xFF673AB7), Color(0xFF9C27B0))
    val DateGradient = listOf(Color(0xFF3498DB), Color(0xFF2980B9))

    // Question card gradients
    val QuestionHeaderGradient = listOf(Color(0xFFFF6699), Color(0xFFFF99CC))
    val QuestionBodyGradient = listOf(
        Color(0xFF331A26), Color(0xFF66334D), Color(0xFF994D33)
    )

    // Paywall gradient
    val PaywallCTAGradient = listOf(Color(0xFFFD267A), Color(0xFFFF655B))
}

object CollectionTypography {
    val H1 = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold)
    val H2 = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)
    val H3 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium)
    val H4 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val H5 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val H6 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal)
    val Body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val Small = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
}

object CollectionDimensions {
    // Spacing
    val SpaceXXS = 4.dp
    val SpaceXS = 6.dp
    val SpaceS = 8.dp
    val SpaceM = 10.dp
    val SpaceL = 16.dp
    val SpaceXL = 20.dp
    val SpaceXXL = 24.dp
    val SpaceXXXL = 30.dp

    // Corner radius
    val CornerRadiusCards = 16.dp
    val CornerRadiusCardsBlack = 20.dp
    val CornerRadiusQuestions = 20.dp
    val CornerRadiusCTA = 25.dp
    val CornerRadiusFavorites = 28.dp

    // Elevations
    val ElevationLight = 8.dp
    val ElevationMedium = 10.dp
    val ElevationBackground = 5.dp

    // Sizes
    val CategoryCardWidth = 160.dp
    val CategoryCardHeight = 200.dp
    val QuestionCardHeight = 500.dp
    val LogoSize = 24.dp
}
```

---

## 📊 Résumé Technique

### 🎯 Système Complet

- **8 collections** : 1 gratuite (En couple) + 7 premium
- **5 types de cartes** : Liste, grille, questions, paywall, completion
- **Design unifié** : Même branding, typographie, couleurs
- **Système freemium** : Limitation questions + paywall intégré
- **Animation avancée** : Flamme 🔥 avec 4 propriétés animées

### 🎨 Design System Cohérent

- **Typographie hiérarchique** : 9 niveaux H1 à Small
- **Spacing system** : 8 valeurs standardisées 4pt à 30pt
- **Corner radius** : 5 standards 16pt à 28pt
- **Shadow system** : 5 types Light à Glow
- **Color gradients** : 8 gradients par catégorie + universels

### 🔑 XCStrings Exhaustives

- **Catégories** : 16 clés (8 titres + 8 sous-titres)
- **UI générale** : 12 clés (favoris, navigation, états)
- **String catalogs** : 8 fichiers avec préfixes (ec*, lph*, ad\_, etc.)
- **Questions** : ~300 par catégorie = 2400+ questions totales

### 🤖 Android Architecture

- **MVVM Clean** : Repository pattern + Use cases
- **Jetpack Compose** : Material 3 + composables custom
- **Room Database** : Cache local questions + progression
- **SharedPreferences** : État freemium + paramètres
- **Firebase** : Synchronisation questions + analytics

Ce système de collections offre une expérience premium complète avec un design cohérent et une architecture scalable pour le portage Android ! 🎯✨
