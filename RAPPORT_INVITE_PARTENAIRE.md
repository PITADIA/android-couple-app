# 💌 INVITATION PARTENAIRE - Design & Clés

## 🎯 Fonctionnalité

Message card qui s'affiche **sous les photos de profil** quand **aucun partenaire connecté**.
Disparaît automatiquement dès qu'un partenaire est connecté.

---

## 🎨 Design Card

```swift
HStack(spacing: 16) {
    // Icône cœur avec rayons (8 rayons à 45°)
    ZStack {
        ForEach(0..<8) { index in
            Rectangle()
                .fill(Color(hex: "#FD267A").opacity(0.6))
                .frame(width: 2, height: 8)
                .offset(y: -20)
                .rotationEffect(.degrees(Double(index) * 45))
        }

        Image(systemName: "heart.fill")
            .font(.system(size: 24))
            .foregroundColor(Color(hex: "#FD267A"))
    }
    .frame(width: 50, height: 50)

    // Texte invitation
    VStack(alignment: .leading, spacing: 4) {
        Text("invite_partner".localized)
            .font(.system(size: 18, weight: .semibold))
            .foregroundColor(.black)

        Text("invite_partner_description".localized)
            .font(.system(size: 14))
            .foregroundColor(.black.opacity(0.7))
    }

    Spacer()

    // Flèche droite
    Image(systemName: "chevron.right")
        .font(.system(size: 16, weight: .semibold))
        .foregroundColor(Color(hex: "#FD267A"))
}
.padding(.horizontal, 20)
.padding(.vertical, 16)
.background(
    RoundedRectangle(cornerRadius: 16)
        .fill(Color.white.opacity(0.95))
        .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
)
```

**Spécifications** :

- **Background** : Blanc 95% opacité + ombre
- **Corner radius** : 16pt
- **Padding** : 20pt horizontal, 16pt vertical
- **Couleur principale** : `#FD267A` (rose/magenta)
- **Shadow** : Noir 10% opacité, radius 8pt, offset (0,2)

---

## 🎨 Icône Cœur Rayonnant

- **8 rayons** disposés à 45° chacun
- **Rayons** : 2x8pt, couleur `#FD267A` 60% opacité
- **Cœur** : `heart.fill` 24pt, couleur `#FD267A`
- **Frame totale** : 50x50pt

---

## 🔑 Clés XCStrings

```xml
<string name="invite_partner">Inviter son partenaire</string>
<string name="invite_partner_description">Connectez-vous pour partager vos réponses</string>
```

---

## 📱 Logique d'Affichage

```swift
// Condition d'affichage
if !hasConnectedPartner {
    PartnerInviteView {
        activeSheet = .partnerManagement // Ouvre la connexion partenaire
    }
    .padding(.top, -15) // Rapprocher des photos
}
```

**Positionnement** : Padding top -15pt pour se rapprocher de `PartnerDistanceView`

---

## 🤖 Code Android Équivalent

```kotlin
@Composable
fun PartnerInviteCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icône cœur avec rayons
            HeartWithRaysIcon(
                modifier = Modifier.size(50.dp)
            )

            // Texte invitation
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.invite_partner),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stringResource(R.string.invite_partner_description),
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Flèche
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFFD267A),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun HeartWithRaysIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 8 rayons
        repeat(8) { index ->
            Box(
                modifier = Modifier
                    .size(width = 2.dp, height = 8.dp)
                    .offset(y = (-20).dp)
                    .rotate(index * 45f)
                    .background(
                        Color(0xFFFD267A).copy(alpha = 0.6f),
                        RectangleShape
                    )
            )
        }

        // Cœur
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color(0xFFFD267A),
            modifier = Modifier.size(24.dp)
        )
    }
}
```

---

## 📋 États

- **Visible** : `!hasConnectedPartner`
- **Action** : Ouvre `PartnerManagementView`
- **Position** : Sous `PartnerDistanceView` avec offset -15pt
- **Animation** : Apparition/disparition automatique selon état connexion

**Design** : Card blanche avec cœur rayonnant rose + texte invite + flèche droite
