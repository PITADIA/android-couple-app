# 👥 CONNEXION PARTENAIRE - Design & Clés

## 🎯 Fonctionnalité

Quand on clique sur la **photo partenaire** (quand pas de partenaire connecté) → ouvre `PartnerManagementView`

---

## 🎨 Design General

**Background** : Gradient rose clair

```swift
LinearGradient(colors: [
    Color(hex: "#FFE5F1"), // Rose très clair
    Color(hex: "#FFF0F8")  // Rose ultra clair
])
```

**Typography** :

- **Titre principal** : System 28pt Bold
- **Description** : System 16pt Regular, noir 70%
- **Code** : System 48pt Bold Monospaced, `#FD267A`, tracking 8pt

---

## 📋 États & Sections

### 1. NON CONNECTÉ

```swift
// Titre + description
Text("connect_with_partner".localized)
    .font(.system(size: 28, weight: .bold))
Text("connect_partner_description".localized)
    .font(.system(size: 16))
    .foregroundColor(.black.opacity(0.7))

// 2 actions possibles:
// A. Générer un code → Button("send_partner_code".localized)
// B. Saisir un code → TextField("enter_code".localized)
```

### 2. CODE GÉNÉRÉ

```swift
// Code affiché
Text(generatedCode)
    .font(.system(size: 48, weight: .bold, design: .monospaced))
    .foregroundColor(Color(hex: "#FD267A"))
    .tracking(8)

// Bouton partage
Button("send_partner_code".localized)
```

### 3. CONNECTÉ

```swift
Text("connected_with_partner".localized)
    .font(.system(size: 28, weight: .bold))

// Info partenaire dans card
Text("partner_name".localized + " \(name)")
Text("connected_on".localized + " \(date)")

// Bouton déconnection rouge
Button("disconnect".localized)
    .foregroundColor(.red)
```

---

## 🎨 Components Design

### Card Style (réutilisé partout)

```swift
.background(
    RoundedRectangle(cornerRadius: 16) // ou 12
        .fill(Color.white.opacity(0.95))
        .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
)
```

### Bouton Principal CTA

```swift
.background(
    LinearGradient(colors: [
        Color(hex: "#FD267A"),
        Color(hex: "#FF655B")
    ])
    .shadow(color: Color(hex: "#FD267A").opacity(0.4), radius: 10, x: 0, y: 5)
)
```

---

## 🔑 Clés XCStrings

```xml
<!-- États connexion -->
<string name="connect_with_partner">Se connecter avec son partenaire</string>
<string name="connect_partner_description">Échangez un code à 8 chiffres pour vous connecter</string>
<string name="connected_with_partner">Vous êtes connectés 💞</string>

<!-- Actions -->
<string name="send_partner_code">Envoyer son code</string>
<string name="enter_partner_code">Saisir le code partenaire</string>
<string name="enter_code">Saisir le code</string>
<string name="connect">Se connecter</string>
<string name="disconnect">Se déconnecter</string>

<!-- États -->
<string name="generating">Génération...</string>
<string name="connecting_status">Connexion...</string>

<!-- Infos partenaire -->
<string name="partner_name">Partenaire :</string>
<string name="connected_on">Connecté le :</string>
<string name="shared_premium">Premium partagé</string>

<!-- Messages -->
<string name="send_code_to_partner">Envoyez ce code à votre partenaire</string>
<string name="disconnect_confirmation">Êtes-vous sûr de vouloir vous déconnecter ?</string>
```

---

## 🤖 Code Android Équivalent

### Dialog Principal

```kotlin
@Composable
fun PartnerConnectionDialog(
    isConnected: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                if (isConnected) {
                    ConnectedSection(onDisconnect = { })
                } else {
                    NotConnectedSection(
                        onGenerateCode = { },
                        onConnectWithCode = { code -> }
                    )
                }
            }
        }
    }
}
```

### Section Non Connecté

```kotlin
@Composable
fun NotConnectedSection(
    onGenerateCode: () -> Unit,
    onConnectWithCode: (String) -> Unit
) {
    var enteredCode by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.connect_with_partner),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.connect_partner_description),
            fontSize = 16.sp,
            color = Color.Black.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        // Bouton génération code
        Button(
            onClick = onGenerateCode,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.send_partner_code),
                color = Color.Black
            )
        }

        // Séparateur
        Divider(color = Color.Black.copy(alpha = 0.3f))

        // Saisie code
        OutlinedTextField(
            value = enteredCode,
            onValueChange = {
                if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                    enteredCode = it
                }
            },
            label = { Text(stringResource(R.string.enter_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // Bouton connexion
        Button(
            onClick = { onConnectWithCode(enteredCode) },
            enabled = enteredCode.length == 8,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFD267A)
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                stringResource(R.string.connect),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
```

### Code Généré

```kotlin
@Composable
fun GeneratedCodeSection(
    code: String,
    onShare: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(stringResource(R.string.send_code_to_partner))

        // Code avec style monospace
        Text(
            text = code,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFFD267A),
            letterSpacing = 8.sp,
            modifier = Modifier
                .padding(vertical = 25.dp, horizontal = 20.dp)
                .background(
                    Color.White.copy(alpha = 0.95f),
                    RoundedCornerShape(16.dp)
                )
        )

        Button(
            onClick = onShare,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.send_partner_code),
                color = Color.Black
            )
        }
    }
}
```

---

## 📋 Logique États

```kotlin
sealed class PartnerConnectionState {
    object NotConnected : PartnerConnectionState()
    data class CodeGenerated(val code: String) : PartnerConnectionState()
    data class Connected(val partnerName: String, val connectedDate: Date) : PartnerConnectionState()
    object Loading : PartnerConnectionState()
}
```

**Background** : Gradient rose clair + cards blanches avec ombres (actuellement il est transparent) + CTA gradient rose-orange
