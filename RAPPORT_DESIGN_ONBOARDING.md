# Rapport : Design Onboarding Complet - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille exhaustivement le design de chaque page de l'onboarding CoupleApp, incluant toutes les spécifications visuelles, couleurs, polices, espacements, et clés de localisation pour l'adaptation Android.

---

## 🎨 Système de Design Global

### Couleurs Primaires

- **Rose Principal** : `#FD267A` (Boutons, sélections)
- **Rose Secondaire** : `#FF6B9D` (Dégradés)
- **Fond Principal** : `RGB(0.97, 0.97, 0.98)` ≈ `#F7F7F9`
- **Zone Boutons** : `Color.white`
- **Texte Principal** : `Color.black`
- **Texte Secondaire** : `Color.black.opacity(0.7)`
- **Bordures Non-sélectionnées** : `Color.black.opacity(0.1)`

### Typographie

- **Titres Principaux** : `font(.system(size: 36, weight: .bold))`
- **Titres Secondaires** : `font(.system(size: 28, weight: .bold))`
- **Boutons** : `font(.system(size: 18, weight: .semibold))`
- **Texte Options** : `font(.system(size: 16))`
- **Sous-titres** : `font(.system(size: 14, weight: .regular))`

### Espacements Standards

- **Padding Horizontal** : `30dp`
- **Espace Titre-Contenu** : `Spacer().frame(height: 40)`
- **Espacement Options** : `spacing: 12`
- **Zone Boutons** : `padding(.vertical, 30)`
- **Hauteur Boutons** : `frame(height: 56)`

---

## 📱 1. RelationshipGoalsStepView

### Design Specifications

```
Fond : RGB(0.97, 0.97, 0.98)
Layout : VStack(spacing: 0)
Titre : font(.system(size: 36, weight: .bold))
       foregroundColor(.black)
       padding(.horizontal, 30)
Options : RoundedRectangle(cornerRadius: 12)
         padding(.vertical, 16) + padding(.horizontal, 20)
         shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 4)
```

### États des Cartes

- **Non-sélectionnée** :
  - Fond : `Color.white`
  - Bordure : `Color.black.opacity(0.1), lineWidth: 1`
  - Texte : `Color.black`
- **Sélectionnée** :
  - Fond : `Color(hex: "#FD267A")`
  - Bordure : `Color(hex: "#FD267A"), lineWidth: 2`
  - Texte : `Color.white`

### Clés XCStrings

```
- "relationship_goals_question" (Titre principal)
- "continue" (Bouton continuer)
```

---

## 📱 2. RelationshipImprovementStepView

### Design Specifications

```
Identique à RelationshipGoalsStepView
Titre : font(.system(size: 36, weight: .bold))
Cartes : Même système sélection multiple
Bouton : Désactivé si aucune sélection
```

### Clés XCStrings

```
- "relationship_improvement_question" (Titre principal)
- "continue" (Bouton continuer)
```

---

## 📱 3. RelationshipDateStepView

### Design Specifications

```
DatePickerCarousel :
- HStack(spacing: 0) avec 3 Pickers
- WheelPickerStyle()
- frame(height: 200)
- font(.system(size: 18, weight: .medium))
- foregroundColor(.black)

Composants :
- Mois : months[month-1] (localisés)
- Jours : 1...daysInSelectedMonth
- Années : Array(1990...2025).reversed()
```

### Clés XCStrings

```
- "relationship_duration_question" (Titre principal)
- "month_january" à "month_december" (Mois localisés)
- "continue" (Bouton continuer)
```

---

## 📱 4. CommunicationEvaluationStepView

### Design Specifications

```
Titre : font(.system(size: 28, weight: .bold))
Sous-titre : font(.system(size: 14, weight: .regular))
            foregroundColor(.black.opacity(0.6))
            padding(.top, 8)

Options : ["1-4", "4-6", "6-8", "8-10"]
Cartes : Même système que RelationshipGoals
```

### Clés XCStrings

```
- "communication_evaluation_question" (Titre)
- "private_answer_note" (Sous-titre explicatif)
- "continue" (Bouton)
```

---

## 📱 5. DiscoveryTimeStepView

### Design Specifications

```
Titre : font(.system(size: 28, weight: .bold))
Options : 3 choix prédéfinis
Cartes : RoundedRectangle(cornerRadius: 12)
```

### Clés XCStrings

```
- "discovery_time_question" (Titre)
- "private_answer_note" (Sous-titre)
- "discovery_time_yes" (Oui)
- "discovery_time_no" (Non)
- "discovery_time_could_do_better" (On pourrait faire mieux)
- "continue" (Bouton)
```

---

## 📱 6. ListeningStepView

### Design Specifications

```
Identique aux autres vues évaluation
Options : 3 choix prédéfinis
Système de sélection unique
```

### Clés XCStrings

```
- "listening_question" (Titre)
- "private_answer_note" (Sous-titre)
- "listening_most_of_time" (Oui, la plupart du temps)
- "listening_sometimes" (Parfois)
- "listening_rarely" (Rarement)
- "continue" (Bouton)
```

---

## 📱 7. ConfidenceStepView

### Design Specifications

```
Même structure que ListeningStepView
3 options de réponse
Cartes avec animation sélection
```

### Clés XCStrings

```
- "confidence_question" (Titre)
- "private_answer_note" (Sous-titre)
- "confidence_completely" (Oui, complètement)
- "confidence_most_of_time" (La plupart du temps)
- "confidence_not_always" (Pas toujours)
- "continue" (Bouton)
```

---

## 📱 8. ComplicityStepView

### Design Specifications

```
4 options au lieu de 3
Cartes plus hautes pour texte
Même système couleurs
```

### Clés XCStrings

```
- "complicity_question" (Titre)
- "private_answer_note" (Sous-titre)
- "complicity_strong_fulfilling" (Forte et épanouissante)
- "complicity_present" (Présente)
- "complicity_sometimes_lacking" (Parfois en manque)
- "complicity_need_help" (On a besoin d'aide)
- "continue" (Bouton)
```

---

## 📱 9. AuthenticationStepView

### Design Specifications

```
ZStack avec overlay chargement
Fond : RGB(0.97, 0.97, 0.98)

Titre : font(.system(size: 36, weight: .bold))
       padding(.horizontal, 30)

Bouton Apple :
- HStack avec applelogo
- font(.system(size: 18, weight: .semibold))
- foregroundColor(.white)
- background(Color.black)
- cornerRadius(28)
- frame(height: 56)

Overlay Chargement :
- ProgressView() scaleEffect(1.5)
- font(.system(size: 18, weight: .medium))
- transition(.opacity.combined(with: .scale))
```

### Clés XCStrings

```
- "create_secure_account" (Titre principal)
- "continue_with_apple" (Bouton Apple)
- "authentication_in_progress" (Texte chargement)
```

---

## 📱 10. DisplayNameStepView

### Design Specifications

```
TextField Custom :
- ZStack avec placeholder
- font(.system(size: 18))
- padding(.horizontal, 20) + padding(.vertical, 16)
- background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
- shadow(color: Color.black.opacity(0.08), radius: 10, x: 0, y: 4)
- accentColor(Color(hex: "#FD267A"))

Bouton Skip :
- font(.system(size: 16))
- foregroundColor(.black.opacity(0.6))
- underline()
```

### Clés XCStrings

```
- "display_name_step_title" (Titre principal)
- "display_name_placeholder" (Placeholder TextField)
- "continue" (Bouton principal)
- "skip_step" (Bouton passer)
```

---

## 📱 11. ProfilePhotoStepView

### Design Specifications

```
Photo Circulaire :
- Circle() frame(width: 160, height: 160)
- shadow(color: Color.black.opacity(0.1), radius: 15, x: 0, y: 8)
- clipShape(Circle()) pour image

Placeholder :
- person.fill icon font(.system(size: 40))
- foregroundColor(.black.opacity(0.3))
- "add_photo" text

Cropper SwiftyCrop :
- maskShape: .circle
- maskRadius: 150
- cropImageCircular: true
- maxMagnificationScale: 4.0
```

### Clés XCStrings

```
- "add_profile_photo" (Titre principal)
- "add_photo" (Texte placeholder)
- "continue" (Bouton principal)
- "skip_step" (Bouton passer)
- "cancel" (Bouton cropper)
- "validate" (Bouton cropper)
- "crop_photo_instructions" (Instructions cropper)
- "authorization_required" (Titre alerte)
- "open_settings_button" (Bouton paramètres)
- "error_image_not_found" (Erreur image)
- "close" (Fermer)
```

---

## 📱 12. CompletionStepView

### Design Specifications

```
Icône Validation :
- Circle() fill(Color.green) frame(width: 20, height: 20)
- checkmark font(.system(size: 12, weight: .bold))

Grand Titre :
- font(.system(size: 48, weight: .bold))
- foregroundColor(.black)
- multilineTextAlignment(.center)

Confettis :
- confettiCannon(trigger: $confettiCounter, num: 50)
- openingAngle: Angle(degrees: 0)
- closingAngle: Angle(degrees: 360)
- radius: 200
```

### Clés XCStrings

```
- "all_completed" (Petit titre avec icône)
- "thank_you_for" (Première ligne grand titre)
- "trusting_us" (Deuxième ligne grand titre)
- "privacy_promise" (Sous-titre descriptif)
- "continue" (Bouton continuer)
```

---

## 📱 13. LoadingStepView

### Design Specifications

```
ProgressView :
- CircularProgressViewStyle(tint: .black)
- scaleEffect(2.0)

Messages Rotatifs :
- font(.system(size: 18, weight: .medium))
- foregroundColor(.black)
- Timer 3 secondes
```

### Clés XCStrings

```
- "loading_profile" (Message 1)
- "loading_preferences" (Message 2)
- "loading_experience" (Message 3)
```

---

## 📱 14. PartnerCodeStepView

### Design Specifications

```
Fond : RGB(0.97, 0.97, 0.98)
Zone Bouton : shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)

Code Affiché :
- font(.system(size: 48, weight: .bold, design: .monospaced))
- foregroundColor(Color(hex: "#FD267A"))
- tracking(8)
- minimumScaleFactor(0.5)
- background(RoundedRectangle(cornerRadius: 16).fill(Color.white))

TextField Code :
- keyboardType(.numberPad)
- multilineTextAlignment(.center)
- onChange pour limiter 8 chiffres

Boutons Secondaires :
- foregroundColor(Color(hex: "#FD267A"))
- overlay(RoundedRectangle stroke lineWidth: 2)
- cornerRadius(28)
```

### Clés XCStrings

```
- "connect_with_partner" (Titre principal)
- "connect_partner_description" (Sous-titre)
- "code_generation" (État génération)
- "generation_error" (Erreur génération)
- "retry" (Bouton réessayer)
- "send_code_to_partner" (Instruction envoi)
- "send_code" (Bouton envoyer)
- "enter_partner_code" (Instruction saisie)
- "enter_code_placeholder" (Placeholder saisie)
- "connecting_status" (État connexion)
- "connect" (Bouton connecter)
- "continue" (Bouton continuer)
- "share_partner_code_message" (Message partage)
```

---

## 📱 15. QuestionsIntroStepView

### Design Specifications

```
Image :
- Image("mima")
- aspectRatio(.fit)
- frame(maxWidth: .infinity, maxHeight: 280)
- cornerRadius(20)
- padding(.horizontal, 30)

Description :
- font(.system(size: 16))
- foregroundColor(.black.opacity(0.7))
- multilineTextAlignment(.center)
- lineLimit(nil)
- fixedSize(horizontal: false, vertical: true)

Zone Bouton :
- shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
```

### Clés XCStrings

```
- "questions_intro_title" (Titre principal)
- "questions_intro_subtitle" (Description longue)
- "continue" (Bouton continuer)
```

---

## 📱 16. CategoriesPreviewStepView

### Design Specifications

```
Animation Séquentielle :
- opacity(visibleCategories.contains(category.id) ? 1 : 0)
- scaleEffect(visibleCategories.contains(category.id) ? 1 : 0.8)
- spring(response: 0.6, dampingFraction: 0.8)
- animationInterval: 0.3 secondes

CategoryPreviewCard :
- HStack(spacing: 16)
- padding(.horizontal, 24) + padding(.vertical, 20)
- RoundedRectangle(cornerRadius: 16)
- Color.white.opacity(0.95)
- shadow(radius: 8, x: 0, y: 2)

Titre Catégorie :
- font(.system(size: 20, weight: .bold))
- foregroundColor(.black)

Emoji :
- font(.system(size: 28))
```

### Clés XCStrings

```
- "more_than_2000_questions" (Titre principal)
- "continue" (Bouton continuer)
+ Titres et sous-titres des catégories (depuis QuestionCategory)
```

---

## 📱 17. SubscriptionStepView (Paywall)

### Design Specifications

```
Header Croix :
- Image(systemName: "xmark")
- font(.system(size: 18, weight: .medium))
- foregroundColor(.black)
- padding(.leading, 20) + padding(.top, 10)

Titre Principal :
- font(.system(size: 32, weight: .bold))
- foregroundColor(.black)
- multilineTextAlignment(.center)

Sous-titre :
- font(.system(size: 16))
- foregroundColor(.black.opacity(0.7))

NewFeatureRow :
- Icône + Titre + Sous-titre
- VStack(alignment: .leading, spacing: 4)

PlanSelectionCard :
- RoundedRectangle sélectionnable
- Badge "POPULAIRE" en overlay
- Prix avec style

Bouton Principal :
- font(.system(size: 18, weight: .bold))
- frame(height: 56)
- background(Color(hex: "#FD267A"))
- cornerRadius(28)

Liens Légaux :
- HStack(spacing: 15)
- font(.system(size: 12))
- foregroundColor(.black.opacity(0.5))
```

### Clés XCStrings

```
- "choose_plan" (Titre principal)
- "partner_no_payment" (Sous-titre)
- "feature_love_stronger" (Fonctionnalité 1)
- "feature_love_stronger_description" (Description 1)
- "feature_memory_chest" (Fonctionnalité 2)
- "feature_memory_chest_description" (Description 2)
- "feature_love_map" (Fonctionnalité 3)
- "feature_love_map_description" (Description 3)
- "no_payment_required_now" (Info mensuel)
- "no_commitment_cancel_anytime" (Info hebdomadaire)
- "start_trial" (Bouton essai)
- "continue" (Bouton continuer)
- "loading_caps" (État chargement)
- "terms" (CGV)
- "privacy_policy" (Confidentialité)
- "restore" (Restaurer)
```

---

## 📱 18. ProgressBar Component

### Design Specifications

```
Bouton Retour :
- Image(systemName: "chevron.left")
- font(.system(size: 20))
- foregroundColor(.black)
- Visible si progress > 0.10 && progress < 0.80

Barre Progression :
- ProgressView(value: progress)
- LinearProgressViewStyle(tint: Color(hex: "#FD267A"))
- frame(width: 200)
- scaleEffect(y: 2)

Layout :
- HStack avec bouton + barre + espaces équilibrés
- Spacer().frame(width: 30) pour équilibrage
```

---

## 🤖 Adaptation Android - Spécifications Techniques

### Architecture Composable

```kotlin
// Couleurs Material Design 3
object OnboardingColors {
    val Primary = Color(0xFFFD267A)
    val PrimaryVariant = Color(0xFFFF6B9D)
    val Background = Color(0xFFF7F7F9)
    val Surface = Color.White
    val OnSurface = Color.Black
    val OnSurfaceVariant = Color.Black.copy(alpha = 0.7f)
    val Outline = Color.Black.copy(alpha = 0.1f)
}

// Typographie
object OnboardingTypography {
    val TitleLarge = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black
    )
    val TitleMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black
    )
    val ButtonText = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )
    val BodyMedium = TextStyle(
        fontSize = 16.sp,
        color = Color.Black
    )
    val BodySmall = TextStyle(
        fontSize = 14.sp,
        color = Color.Black.copy(alpha = 0.6f)
    )
}

// Espacements
object OnboardingDimensions {
    val HorizontalPadding = 30.dp
    val TitleContentSpacing = 40.dp
    val OptionSpacing = 12.dp
    val ButtonHeight = 56.dp
    val ButtonZoneVerticalPadding = 30.dp
    val CornerRadiusCard = 12.dp
    val CornerRadiusButton = 28.dp
    val ShadowElevation = 8.dp
}
```

### Composable Type Générique

```kotlin
@Composable
fun OnboardingScreen(
    title: String,
    subtitle: String? = null,
    showProgressBar: Boolean = true,
    progress: Float = 0f,
    content: @Composable () -> Unit,
    onBackClick: (() -> Unit)? = null,
    onContinueClick: () -> Unit,
    isContinueEnabled: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingColors.Background)
    ) {
        // ProgressBar
        if (showProgressBar) {
            OnboardingProgressBar(
                progress = progress,
                onBackClick = onBackClick,
                modifier = Modifier.padding(top = 20.dp, horizontal = 20.dp)
            )
        }

        // Titre
        Text(
            text = title,
            style = OnboardingTypography.TitleLarge,
            modifier = Modifier
                .padding(horizontal = OnboardingDimensions.HorizontalPadding)
                .padding(top = OnboardingDimensions.TitleContentSpacing)
        )

        // Sous-titre optionnel
        subtitle?.let {
            Text(
                text = it,
                style = OnboardingTypography.BodySmall,
                modifier = Modifier
                    .padding(horizontal = OnboardingDimensions.HorizontalPadding)
                    .padding(top = 8.dp)
            )
        }

        // Contenu personnalisable
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            content()
        }

        // Zone bouton
        OnboardingButtonZone(
            onContinueClick = onContinueClick,
            isContinueEnabled = isContinueEnabled
        )
    }
}
```

### Cartes Sélectionnables

```kotlin
@Composable
fun SelectableCard(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                OnboardingColors.Primary else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected)
                OnboardingColors.Primary else OnboardingColors.Outline
        ),
        shape = RoundedCornerShape(OnboardingDimensions.CornerRadiusCard),
        elevation = CardDefaults.cardElevation(
            defaultElevation = OnboardingDimensions.ShadowElevation
        )
    ) {
        Text(
            text = text,
            style = OnboardingTypography.BodyMedium.copy(
                color = if (isSelected) Color.White else Color.Black
            ),
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 20.dp)
                .fillMaxWidth()
        )
    }
}
```

### Localisation Android strings.xml

```xml
<!-- Titres principaux -->
<string name="relationship_goals_question">Quels sont vos objectifs de couple ?</string>
<string name="relationship_improvement_question">Dans quels domaines souhaitez-vous que votre couple s\'améliore ?</string>
<string name="relationship_duration_question">Depuis quand êtes-vous en couple ?</string>
<string name="communication_evaluation_question">Sur une échelle de 1 à 10, à combien noteriez-vous la communication dans votre couple ?</string>
<string name="discovery_time_question">Pensez-vous que vous prenez assez de temps dans votre couple pour vraiment vous découvrir ?</string>
<string name="listening_question">Quand un désaccord arrive dans votre couple, vous sentez-vous écouté(e) et compris(e) ?</string>
<string name="confidence_question">Diriez-vous que vous vous sentez totalement en confiance pour être vous-même avec votre partenaire ?</string>
<string name="complicity_question">Comment décririez-vous aujourd\'hui votre complicité dans votre couple ?</string>
<string name="create_secure_account">Créez votre compte sécurisé</string>
<string name="display_name_step_title">Comment aimeriez-vous que votre partenaire vous appelle ?</string>
<string name="add_profile_photo">Ajoutez votre photo de profil</string>
<string name="all_completed">Tout est terminé</string>
<string name="thank_you_for">Merci de</string>
<string name="trusting_us">nous faire confiance.</string>
<string name="connect_with_partner">Se connecter avec son partenaire</string>
<string name="questions_intro_title">Des questions destinées à vous rapprocher.</string>
<string name="more_than_2000_questions">Plus de 2000 questions</string>
<string name="choose_plan">Choisissez votre formule</string>

<!-- Boutons et actions -->
<string name="continue">Continuer</string>
<string name="skip_step">Passer cette étape</string>
<string name="continue_with_apple">Continuer avec Apple</string>
<string name="start_trial">Commencer l\'essai</string>

<!-- Messages informatifs -->
<string name="private_answer_note">Votre partenaire ne verra pas votre réponse</string>
<string name="privacy_promise">Vos informations restent privées et ne seront jamais partagées.</string>
<string name="partner_no_payment">Si votre partenaire paye, vous aussi</string>
```

---

## 📋 Conclusion

Ce rapport fournit toutes les spécifications nécessaires pour recréer fidèlement l'onboarding CoupleApp sur Android :

- **18 écrans** avec designs détaillés
- **Système couleurs/typo cohérent**
- **+80 clés de localisation** multilingues
- **Composants réutilisables** Android
- **Animations et transitions** spécifiées
- **Architecture Compose moderne**

L'adaptation Android peut utiliser ces spécifications exactes pour maintenir la cohérence visuelle tout en respectant les conventions Material Design.
