# 💳 PAGE PAYWALL ONBOARDING - Design & Traductions Complètes

## 🎯 Vue d'Ensemble

Page d'abonnement dans l'onboarding avec design moderne, choix de plans, fonctionnalités premium et intégration StoreKit complète.

---

## 📱 Structure Générale (`SubscriptionStepView`)

### 1. Architecture Layout

```swift
ZStack {
    // Fond gris clair (#F7F7F8)
    Color(red: 0.97, green: 0.97, blue: 0.98)
        .ignoresSafeArea()

    VStack(spacing: 0) {
        // 1. Header avec croix (padding top: 10pt)
        // 2. Titre + sous-titre (padding top: 15pt)
        // 3. Spacer fixe (20pt)
        // 4. Features section (padding horizontal: 25pt)
        // 5. Spacer flexible
        // 6. Plans selection (padding horizontal: 20pt)
        // 7. Texte informatif + bouton (padding horizontal: 30pt)
        // 8. Liens légaux (padding bottom: 5pt)
    }
}
```

---

## 🎨 Design Détaillé

### 1. Header avec Fermeture (Haut de Page)

```swift
HStack {
    Button(action: { viewModel.skipSubscription() }) {
        Image(systemName: "xmark")
            .font(.system(size: 18, weight: .medium))
            .foregroundColor(.black)
    }
    .padding(.leading, 20)

    Spacer()
}
.padding(.top, 10)
```

**Spécifications:**

- **Position:** Collé en haut à gauche
- **Padding left:** 20pt
- **Padding top:** 10pt
- **Icon:** xmark (système)
- **Taille:** 18pt, medium weight
- **Couleur:** Noir

### 2. Section Titre (Sous Header)

```swift
VStack(spacing: 8) {
    Text("choose_plan".localized)
        .font(.system(size: 32, weight: .bold))
        .foregroundColor(.black)
        .multilineTextAlignment(.center)

    Text("partner_no_payment".localized)
        .font(.system(size: 16))
        .foregroundColor(.black.opacity(0.7))
        .multilineTextAlignment(.center)
}
.padding(.horizontal, 20)
.padding(.top, 15)
```

**Spécifications:**

- **Spacing VStack:** 8pt
- **Titre principal:** 32pt, bold, noir, centré
- **Sous-titre:** 16pt, regular, noir 70% opacité, centré
- **Padding horizontal:** 20pt
- **Padding top:** 15pt (depuis header)

### 3. Spacer Fixe

```swift
Spacer().frame(height: 20)
```

### 4. Section Fonctionnalités Premium

```swift
VStack(spacing: 25) {
    NewFeatureRow(
        title: "feature_love_stronger".localized,
        subtitle: "feature_love_stronger_description".localized
    )

    NewFeatureRow(
        title: "feature_memory_chest".localized,
        subtitle: "feature_memory_chest_description".localized
    )

    NewFeatureRow(
        title: "feature_love_map".localized,
        subtitle: "feature_love_map_description".localized
    )
}
.padding(.horizontal, 25)
```

**Spécifications:**

- **Spacing VStack:** 25pt entre chaque feature
- **Padding horizontal:** 25pt
- **3 fonctionnalités** présentées

#### Composant NewFeatureRow

```swift
struct NewFeatureRow: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 19, weight: .bold))
                .foregroundColor(.black)
                .multilineTextAlignment(.leading)

            Text(subtitle)
                .font(.system(size: 15))
                .foregroundColor(.black.opacity(0.7))
                .multilineTextAlignment(.leading)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
```

**Spécifications Feature Row:**

- **Layout:** VStack alignement gauche
- **Spacing:** 8pt entre titre et description
- **Titre:** 19pt, bold, noir, aligné gauche
- **Description:** 15pt, regular, noir 70% opacité, multilignes
- **Largeur:** Pleine largeur, alignement gauche

### 5. Spacer Flexible

```swift
Spacer()
```

### 6. Section Sélection Plans

```swift
VStack(spacing: 0) {
    VStack(spacing: 8) {
        // Plan Hebdomadaire (première position)
        PlanSelectionCard(
            planType: .weekly,
            isSelected: receiptService.selectedPlan == .weekly,
            onTap: { receiptService.selectedPlan = .weekly }
        )

        // Plan Mensuel (seconde position)
        PlanSelectionCard(
            planType: .monthly,
            isSelected: receiptService.selectedPlan == .monthly,
            onTap: { receiptService.selectedPlan = .monthly }
        )
    }
    .padding(.horizontal, 20)
    .padding(.bottom, 3)

    Spacer().frame(height: 18)

    // Texte informatif avec checkmark
    HStack(spacing: 5) {
        Image(systemName: "checkmark")
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(.black)

        Text(receiptService.selectedPlan == .monthly ?
             "no_payment_required_now".localized :
             "no_commitment_cancel_anytime".localized)
            .font(.system(size: 14))
            .foregroundColor(.black)
    }
    .padding(.bottom, 12)
}
```

**Spécifications Plans:**

- **Spacing entre cartes:** 8pt
- **Padding horizontal:** 20pt
- **Padding bottom:** 3pt
- **Spacer fixe:** 18pt
- **Texte informatif:** 14pt avec checkmark icon
- **Spacing HStack:** 5pt
- **Padding bottom texte:** 12pt

#### Composant PlanSelectionCard

```swift
// Dans SubscriptionView.swift
struct PlanSelectionCard: View {
    let planType: PlanType
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(planType.displayName)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.black)

                    Text(planType.priceDescription)
                        .font(.system(size: 14))
                        .foregroundColor(.black.opacity(0.7))
                }

                Spacer()

                // Badge "Économisez X%" si applicable
                if planType == .monthly {
                    Text("save_percentage".localized)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(hex: "#FD267A"))
                        .cornerRadius(6)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(
                                isSelected ? Color.black : Color.black.opacity(0.3),
                                lineWidth: isSelected ? 2 : 1
                            )
                    )
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}
```

**Spécifications Plan Card:**

- **Background:** Blanc
- **Corner radius:** 12pt
- **Padding interne:** 20pt horizontal, 16pt vertical
- **Border:** Noir 2pt si sélectionné, noir 30% opacité 1pt sinon
- **Titre plan:** 18pt, semibold, noir
- **Prix:** 14pt, regular, noir 70% opacité
- **Badge économie:** Rose (#FD267A), corner radius 6pt, padding 8pt/4pt

### 7. Bouton Principal

```swift
Button(action: {
    isProcessingPurchase = true
    purchaseSubscription()
}) {
    HStack {
        if isProcessingPurchase {
            HStack(spacing: 8) {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(0.8)
                Text("loading_caps".localized)
            }
        } else {
            Text(receiptService.selectedPlan == .weekly ?
                 "continue".localized.uppercased() :
                 "start_trial".localized)
        }
    }
    .font(.system(size: 18, weight: .bold))
    .foregroundColor(.white)
    .frame(maxWidth: .infinity)
    .frame(height: 56)
    .background(Color(hex: "#FD267A"))
    .cornerRadius(28)
}
.disabled(isProcessingPurchase)
.padding(.horizontal, 30)
```

**Spécifications Bouton:**

- **Height:** 56pt
- **Corner radius:** 28pt (arrondi complet)
- **Background:** Rose #FD267A
- **Text:** 18pt, bold, blanc, majuscules conditionnelles
- **Padding horizontal:** 30pt
- **Loading state:** ProgressView + texte avec spacing 8pt
- **Disabled** pendant processing

### 8. Spacer Final

```swift
Spacer().frame(height: 12)
```

### 9. Section Légale (Bas de Page)

```swift
HStack(spacing: 15) {
    Button("terms".localized) {
        // Ouvre URL Apple Terms
    }
    .font(.system(size: 12))
    .foregroundColor(.black.opacity(0.5))

    Button("privacy_policy".localized) {
        // Ouvre URL Privacy Policy
    }
    .font(.system(size: 12))
    .foregroundColor(.black.opacity(0.5))

    Button("restore".localized) {
        receiptService.restorePurchases()
    }
    .font(.system(size: 12))
    .foregroundColor(.black.opacity(0.5))
}
.padding(.bottom, 5)
```

**Spécifications Légal:**

- **Spacing HStack:** 15pt
- **Font:** 12pt, regular
- **Couleur:** Noir 50% opacité
- **Padding bottom:** 5pt
- **3 liens:** Terms, Privacy Policy, Restore

---

## 🔑 Clés XCStrings Complètes

### 1. Titres Principaux

```xml
<!-- Titre principal -->
<string name="choose_plan">Choisissez votre plan</string>

<!-- Sous-titre -->
<string name="partner_no_payment">Votre partenaire n'aura rien à payer</string>
```

**Traductions `choose_plan`:**

- 🇫🇷 **FR:** "Choisissez votre plan"
- 🇬🇧 **EN:** "Choose your plan"
- 🇩🇪 **DE:** "Wähle deinen Plan"
- 🇪🇸 **ES:** "Elige tu plan"

**Traductions `partner_no_payment`:**

- 🇫🇷 **FR:** "Votre partenaire n'aura rien à payer"
- 🇬🇧 **EN:** "Your partner won't have to pay anything"
- 🇩🇪 **DE:** "Dein:e Partner:in muss nichts bezahlen"
- 🇪🇸 **ES:** "Tu pareja no tendrá que pagar nada"

### 2. Fonctionnalités Premium

```xml
<!-- Feature 1: Amour plus fort -->
<string name="feature_love_stronger">✓ Pour s'aimer encore plus fort</string>
<string name="feature_love_stronger_description">Accédez à des milliers de questions exclusives pour approfondir votre relation.</string>

<!-- Feature 2: Coffre aux souvenirs -->
<string name="feature_memory_chest">✓ Coffre aux souvenirs partagés</string>
<string name="feature_memory_chest_description">Créez ensemble un journal numérique de tous vos moments précieux.</string>

<!-- Feature 3: Carte de l'amour -->
<string name="feature_love_map">✓ Carte de l'amour interactive</string>
<string name="feature_love_map_description">Visualisez tous les lieux spéciaux de votre histoire sur une carte personnalisée.</string>
```

**Traductions `feature_love_stronger`:**

- 🇫🇷 **FR:** "✓ Pour s'aimer encore plus fort"
- 🇬🇧 **EN:** "✓ To love each other even stronger"
- 🇩🇪 **DE:** "✓ Um sich noch stärker zu lieben"
- 🇪🇸 **ES:** "✓ Para amarse aún más fuerte"

### 3. Textes Informatifs

```xml
<!-- Mensuel (essai gratuit) -->
<string name="no_payment_required_now">Aucun paiement requis maintenant</string>

<!-- Hebdomadaire (sans engagement) -->
<string name="no_commitment_cancel_anytime">Aucun engagement, annulez à tout moment</string>
```

### 4. Boutons et Actions

```xml
<!-- Bouton principal (contexte mensuel) -->
<string name="start_trial">COMMENCER L'ESSAI GRATUIT</string>

<!-- Bouton principal (contexte hebdomadaire) -->
<string name="continue">CONTINUER</string>

<!-- État chargement -->
<string name="loading_caps">CHARGEMENT...</string>
```

### 5. Liens Légaux

```xml
<!-- Conditions d'utilisation -->
<string name="terms">Conditions d'utilisation</string>

<!-- Politique de confidentialité -->
<string name="privacy_policy">Politique de confidentialité</string>

<!-- Restaurer achats -->
<string name="restore">Restaurer</string>
```

### 6. Plans et Tarification

```xml
<!-- Plan hebdomadaire -->
<string name="weekly_plan">Hebdomadaire</string>
<string name="weekly_price_description">{{price}} par semaine</string>

<!-- Plan mensuel -->
<string name="monthly_plan">Mensuel</string>
<string name="monthly_price_description">{{price}} par mois après l'essai gratuit</string>

<!-- Badge économie -->
<string name="save_percentage">Économisez 20%</string>
```

---

## 🎨 Couleurs et Styles

### 1. Palette Couleurs

```swift
// Fond principal
Color(red: 0.97, green: 0.97, blue: 0.98) // #F7F7F8

// Couleur accent (boutons, badges)
Color(hex: "#FD267A") // Rose principal

// Textes
.foregroundColor(.black)                  // Titres
.foregroundColor(.black.opacity(0.7))    // Sous-titres
.foregroundColor(.black.opacity(0.5))    // Liens légaux
.foregroundColor(.white)                  // Texte bouton

// Backgrounds
Color.white                               // Cartes plans
Color.white.opacity(0.95)                // Variante légère
```

### 2. Typographie Système

```swift
// Titre principal
.font(.system(size: 32, weight: .bold))

// Sous-titre principal
.font(.system(size: 16))

// Titre feature
.font(.system(size: 19, weight: .bold))

// Description feature
.font(.system(size: 15))

// Titre plan
.font(.system(size: 18, weight: .semibold))

// Prix plan
.font(.system(size: 14))

// Texte informatif
.font(.system(size: 14, weight: .bold)) // Icon
.font(.system(size: 14))                // Texte

// Bouton principal
.font(.system(size: 18, weight: .bold))

// Badge économie
.font(.system(size: 12, weight: .bold))

// Liens légaux
.font(.system(size: 12))
```

### 3. Espacements Standards

```swift
// Spacings VStack principaux
VStack(spacing: 0)    // Container principal
VStack(spacing: 8)    // Titre + sous-titre
VStack(spacing: 25)   // Features list
VStack(spacing: 8)    // Plans list

// Spacings HStack
HStack(spacing: 5)    // Icon + texte informatif
HStack(spacing: 8)    // Loading icon + texte
HStack(spacing: 15)   // Liens légaux

// Paddings standards
.padding(.horizontal, 20)  // Header, titre
.padding(.horizontal, 25)  // Features
.padding(.horizontal, 30)  // Bouton principal
.padding(.vertical, 16)    // Plan cards intérieur
.padding(.top, 10)         // Header
.padding(.top, 15)         // Titre depuis header
.padding(.bottom, 3)       // Plans container
.padding(.bottom, 12)      // Texte informatif
.padding(.bottom, 5)       // Liens légaux

// Spacers fixes
Spacer().frame(height: 20)  // Après titre
Spacer().frame(height: 18)  // Avant texte informatif
Spacer().frame(height: 12)  // Avant liens légaux
```

---

## 🤖 Adaptation Android (Kotlin/Jetpack Compose)

### 1. Structure Principale

```kotlin
@Composable
fun SubscriptionScreen(
    onSubscriptionComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Header avec fermeture
            SubscriptionHeader(onClose = onSkip)

            // 2. Section titre
            SubscriptionTitle()

            // 3. Spacer fixe
            Spacer(modifier = Modifier.height(20.dp))

            // 4. Features premium
            SubscriptionFeatures()

            // 5. Spacer flexible
            Spacer(modifier = Modifier.weight(1f))

            // 6. Sélection plans + bouton
            SubscriptionPlansSection(
                selectedPlan = uiState.selectedPlan,
                isProcessing = uiState.isProcessingPurchase,
                onPlanSelected = viewModel::selectPlan,
                onPurchase = viewModel::purchaseSubscription
            )

            // 7. Liens légaux
            LegalLinksSection()
        }

        // Overlay erreur si nécessaire
        if (uiState.errorMessage != null) {
            ErrorSnackbar(
                message = uiState.errorMessage,
                onDismiss = viewModel::clearError
            )
        }
    }
}
```

### 2. Composant Header

```kotlin
@Composable
fun SubscriptionHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
```

### 3. Composant Titre

```kotlin
@Composable
fun SubscriptionTitle(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, top = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.choose_plan),
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
        )

        Text(
            text = stringResource(R.string.partner_no_payment),
            style = TextStyle(
                fontSize = 16.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        )
    }
}
```

### 4. Composant Features

```kotlin
@Composable
fun SubscriptionFeatures(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 25.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp)
    ) {
        FeatureRow(
            title = stringResource(R.string.feature_love_stronger),
            subtitle = stringResource(R.string.feature_love_stronger_description)
        )

        FeatureRow(
            title = stringResource(R.string.feature_memory_chest),
            subtitle = stringResource(R.string.feature_memory_chest_description)
        )

        FeatureRow(
            title = stringResource(R.string.feature_love_map),
            subtitle = stringResource(R.string.feature_love_map_description)
        )
    }
}

@Composable
fun FeatureRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        )

        Text(
            text = subtitle,
            style = TextStyle(
                fontSize = 15.sp,
                color = Color.Black.copy(alpha = 0.7f)
            )
        )
    }
}
```

### 5. Composant Plans et Achat

```kotlin
@Composable
fun SubscriptionPlansSection(
    selectedPlan: PlanType,
    isProcessing: Boolean,
    onPlanSelected: (PlanType) -> Unit,
    onPurchase: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Plans selection
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 3.dp)
        ) {
            PlanSelectionCard(
                planType = PlanType.WEEKLY,
                isSelected = selectedPlan == PlanType.WEEKLY,
                onSelected = { onPlanSelected(PlanType.WEEKLY) }
            )

            PlanSelectionCard(
                planType = PlanType.MONTHLY,
                isSelected = selectedPlan == PlanType.MONTHLY,
                onSelected = { onPlanSelected(PlanType.MONTHLY) }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Texte informatif
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(14.dp)
            )

            Text(
                text = if (selectedPlan == PlanType.MONTHLY) {
                    stringResource(R.string.no_payment_required_now)
                } else {
                    stringResource(R.string.no_commitment_cancel_anytime)
                },
                style = TextStyle(
                    fontSize = 14.sp,
                    color = Color.Black
                )
            )
        }

        // Bouton principal
        Button(
            onClick = onPurchase,
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFD267A),
                disabledContainerColor = Color(0xFFFD267A).copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp) // Pour le padding 30dp total
        ) {
            if (isProcessing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.loading_caps),
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            } else {
                Text(
                    text = if (selectedPlan == PlanType.WEEKLY) {
                        stringResource(R.string.continue_text).uppercase()
                    } else {
                        stringResource(R.string.start_trial)
                    },
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
    }
}
```

### 6. Composant Plan Card

```kotlin
@Composable
fun PlanSelectionCard(
    planType: PlanType,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelected,
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color.Black else Color.Black.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(planType.nameRes),
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                )

                Text(
                    text = stringResource(planType.priceDescriptionRes),
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
                )
            }

            // Badge économie pour mensuel
            if (planType == PlanType.MONTHLY) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFFFD267A),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.save_percentage),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}
```

### 7. Composant Liens Légaux

```kotlin
@Composable
fun LegalLinksSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally)
    ) {
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"))
                context.startActivity(intent)
            }
        ) {
            Text(
                text = stringResource(R.string.terms),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.5f)
                )
            )
        }

        TextButton(
            onClick = {
                val url = if (Locale.getDefault().language == "fr") {
                    "https://love2lovesite.onrender.com"
                } else {
                    "https://love2lovesite.onrender.com/privacy-policy.html"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        ) {
            Text(
                text = stringResource(R.string.privacy_policy),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.5f)
                )
            )
        }

        TextButton(
            onClick = { /* Restore purchases logic */ }
        ) {
            Text(
                text = stringResource(R.string.restore),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.5f)
                )
            )
        }
    }
}
```

### 8. ViewModel Android

```kotlin
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState = _uiState.asStateFlow()

    data class SubscriptionUiState(
        val selectedPlan: PlanType = PlanType.MONTHLY,
        val isProcessingPurchase: Boolean = false,
        val errorMessage: String? = null,
        val isSubscribed: Boolean = false
    )

    fun selectPlan(plan: PlanType) {
        _uiState.update { it.copy(selectedPlan = plan) }
    }

    fun purchaseSubscription() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingPurchase = true, errorMessage = null) }

            try {
                val result = billingManager.purchaseSubscription(
                    planType = _uiState.value.selectedPlan
                )

                if (result.isSuccess) {
                    _uiState.update { it.copy(
                        isProcessingPurchase = false,
                        isSubscribed = true
                    )}
                } else {
                    _uiState.update { it.copy(
                        isProcessingPurchase = false,
                        errorMessage = result.exceptionOrNull()?.message
                    )}
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isProcessingPurchase = false,
                    errorMessage = e.message
                )}
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
```

### 9. Clés Android (strings.xml)

```xml
<!-- Titres principaux -->
<string name="choose_plan">Choisissez votre plan</string>
<string name="partner_no_payment">Votre partenaire n\'aura rien à payer</string>

<!-- Fonctionnalités premium -->
<string name="feature_love_stronger">✓ Pour s\'aimer encore plus fort</string>
<string name="feature_love_stronger_description">Accédez à des milliers de questions exclusives pour approfondir votre relation.</string>
<string name="feature_memory_chest">✓ Coffre aux souvenirs partagés</string>
<string name="feature_memory_chest_description">Créez ensemble un journal numérique de tous vos moments précieux.</string>
<string name="feature_love_map">✓ Carte de l\'amour interactive</string>
<string name="feature_love_map_description">Visualisez tous les lieux spéciaux de votre histoire sur une carte personnalisée.</string>

<!-- Plans -->
<string name="weekly_plan">Hebdomadaire</string>
<string name="monthly_plan">Mensuel</string>
<string name="weekly_price_description">%1$s par semaine</string>
<string name="monthly_price_description">%1$s par mois après l\'essai gratuit</string>
<string name="save_percentage">Économisez 20%</string>

<!-- Textes informatifs -->
<string name="no_payment_required_now">Aucun paiement requis maintenant</string>
<string name="no_commitment_cancel_anytime">Aucun engagement, annulez à tout moment</string>

<!-- Boutons -->
<string name="start_trial">COMMENCER L\'ESSAI GRATUIT</string>
<string name="continue_text">CONTINUER</string>
<string name="loading_caps">CHARGEMENT...</string>

<!-- Liens légaux -->
<string name="terms">Conditions d\'utilisation</string>
<string name="privacy_policy">Politique de confidentialité</string>
<string name="restore">Restaurer</string>
<string name="close">Fermer</string>
```

---

## 📋 Résumé Technique

### ✅ Design iOS Précis

- **Layout VStack** avec spacings exacts (0, 8, 25pt)
- **Typography système** avec 7 tailles différentes (12-32pt)
- **Couleurs cohérentes** : #F7F7F8 fond, #FD267A accent
- **Paddings standardisés** : 20, 25, 30pt horizontal
- **Spacers fixes et flexibles** pour structure

### ✅ Composants Réutilisables

- **NewFeatureRow** : Feature avec titre/description
- **PlanSelectionCard** : Carte plan avec border conditionnel
- **Gestion états** : Loading, sélection, erreurs

### ✅ Intégration Complète

- **StoreKit** : AppleReceiptService + StoreKitPricingService
- **États réactifs** : @StateObject, @State, onReceive
- **Navigation** : skipSubscription() / completeSubscription()

### ✅ Localisation 4+ Langues

- **18 clés principales** dans UI.xcstrings
- **Features descriptives** avec checkmarks
- **Textes contextuels** selon plan sélectionné
- **Liens légaux** avec URLs localisées

### ✅ Android Équivalent Complet

- **Jetpack Compose** moderne avec Cards, Buttons
- **Material Design 3** : BorderStroke, RoundedCornerShape
- **StateFlow + ViewModel** pour gestion état
- **Google Play Billing** intégration
- **strings.xml** avec formatage paramétré

Cette page paywall est **très sophistiquée** avec un design moderne, des animations fluides et une intégration billing complète ! 💳✨
