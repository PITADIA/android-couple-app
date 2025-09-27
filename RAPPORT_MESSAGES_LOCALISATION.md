# 📍 MESSAGES LOCALISATION PARTENAIRE - Design & Clés

## 🎯 Fonctionnalité

Quand on clique sur la **distance partenaire** ("km ?") en haut de la page principale, 2 cas :

1. **Localisation utilisateur manquante** → `LocationPermissionFlow`
2. **Localisation partenaire manquante** → `LocationPartnerMessageView`

---

## 🎨 Design Distance Button

```swift
Text(cachedDistance) // "km ?" ou "X.X km"
    .font(.system(size: 16, weight: .semibold))
    .foregroundColor(.black)
    .padding(.horizontal, 20)
    .padding(.vertical, 10)
    .background(
        RoundedRectangle(cornerRadius: 20)
            .fill(Color.white.opacity(0.95))
            .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
    )
```

**Spécifications** :

- **Font** : System 16pt Semibold
- **Couleur text** : Noir
- **Background** : Blanc 95% opacité
- **Corner radius** : 20pt
- **Padding** : 20pt horizontal, 10pt vertical
- **Shadow** : Noir 10% opacité, radius 8pt, offset (0,2)

---

## 💬 Message Partenaire (LocationPartnerExplanationView)

### Design

```swift
VStack(spacing: 30) {
    // Titre
    Text("partner_turn".localized)
        .font(.system(size: 28, weight: .bold))
        .foregroundColor(.black)

    // Description
    Text("partner_location_request".localized)
        .font(.system(size: 16))
        .foregroundColor(.black.opacity(0.7))
        .multilineTextAlignment(.center)
        .padding(.horizontal, 30)

    // Icône localisation
    Image(systemName: "location.fill")
        .font(.system(size: 80, weight: .medium))
        .foregroundColor(.black)
        .frame(width: 120, height: 120)

    // Bouton Continuer
    Button("continue_button".localized) {
        // Ferme le sheet
    }
    .font(.system(size: 18, weight: .semibold))
    .foregroundColor(.white)
    .frame(maxWidth: .infinity, height: 56)
    .background(Color(hex: "#FD267A"))
    .clipShape(RoundedRectangle(cornerRadius: 28))
}
```

**Background** : Gris clair `Color(red: 0.97, green: 0.97, blue: 0.98)` + gradient rose doux

---

## 🔑 Clés XCStrings Nécessaires

```xml
<!-- Messages utilisateur localisation manquante -->
<string name="widget_enable_your_location">Activez votre localisation</string>
<string name="widget_enable_your_locations">Activez vos localisations</string>

<!-- Message partenaire localisation manquante -->
<string name="widget_partner_enable_location">Votre partenaire doit activer sa localisation</string>
<string name="partner_turn">À votre partenaire maintenant</string>
<string name="partner_location_request">Demandez-lui d'activer sa localisation pour voir la distance qui vous sépare</string>

<!-- Actions -->
<string name="continue_button">Continuer</string>
```

---

## 🤖 Code Android Équivalent

### Distance Button

```kotlin
@Composable
fun PartnerDistanceButton(
    distance: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = distance,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
    }
}
```

### Message Partenaire Dialog

```kotlin
@Composable
fun PartnerLocationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF7F7F8),
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(30.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.partner_turn),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Text(
                    text = stringResource(R.string.partner_location_request),
                    fontSize = 16.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color.Black
                )

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFD267A)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.continue_button),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    )
}
```

### Logic Android

```kotlin
fun handleDistanceClick(
    hasUserLocation: Boolean,
    hasPartnerLocation: Boolean,
    showLocationDialog: (Boolean) -> Unit
) {
    when {
        !hasUserLocation -> showLocationDialog(false) // Permission flow
        !hasPartnerLocation -> showLocationDialog(true) // Partner message
        // Sinon ne rien faire (distance déjà affichée)
    }
}
```

---

## 📱 États Distance

- **"km ?"** → Cliquable, ouvre dialog selon le contexte
- **"X.X km"** → Non cliquable, distance réelle affichée
- **Messages selon état** : widget*enable*\* ou partner_location_request

**Cache** : Distance sauvegardée dans `UserDefaults` avec update toutes les 2 secondes.
