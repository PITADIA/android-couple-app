# 💰 BOUTONS PRIX PAYWALL - Clés & Design

## 🎯 Structure des Boutons de Prix

### Composant PlanSelectionCard
```swift
VStack(alignment: .leading, spacing: 5) {
    // 1. TITRE DU PLAN
    Text(planType.displayName)
        .font(.system(size: 17, weight: .bold))
        .foregroundColor(.black)
    
    // 2. PRIX TOTAL + PÉRIODE + "POUR 2 UTILISATEURS"
    HStack(spacing: 0) {
        Text("\(planType.price) / \(planType.period)")
            .font(.system(size: 13, weight: .bold))
            .foregroundColor(.black)
        Text("for_2_users".localized)
            .font(.system(size: 13, weight: .regular))
            .foregroundColor(.black)
    }
    
    // 3. PRIX PAR UTILISATEUR
    Text("\(planType.pricePerUser) " + "per_user_per".localized + " \(planType.period)")
        .font(.system(size: 12))
        .foregroundColor(.black.opacity(0.7))
}
```

---

## 🔑 Clés XCStrings Utilisées

### Titres des Plans
```xml
<!-- Plan hebdomadaire -->
<string name="plan_weekly">Hebdomadaire</string>

<!-- Plan mensuel avec essai -->
<string name="plan_monthly_free_trial">Mensuel - 3 jours gratuits</string>
```

### Périodes 
```xml
<string name="period_week">semaine</string>
<string name="period_month">mois</string>
```

### Textes Prix
```xml
<!-- Ajouté après prix total -->
<string name="for_2_users"> pour 2 utilisateurs</string>

<!-- Dans prix par utilisateur -->
<string name="per_user_per">/ utilisateur /</string>
```

### Textes Informatifs (Sous les boutons)
```xml
<!-- Mensuel sélectionné -->
<string name="no_payment_required_now">Aucun paiement requis maintenant</string>

<!-- Hebdomadaire sélectionné -->
<string name="no_commitment_cancel_anytime">Aucun engagement, annulez à tout moment</string>
```

---

## 🎨 Design Spécifications

### Bouton Plan
- **Background:** Blanc
- **Border:** Noir 2pt (sélectionné) / Noir 30% 1pt (normal)
- **Corner radius:** 12pt
- **Padding:** 20pt horizontal, 8pt vertical

### Typography
- **Titre plan:** 17pt, bold, noir
- **Prix total:** 13pt, bold, noir
- **"pour 2 utilisateurs":** 13pt, regular, noir
- **Prix par utilisateur:** 12pt, regular, noir 70%

### Texte Informatif avec Checkmark
```swift
HStack(spacing: 5) {
    Image(systemName: "checkmark")
        .font(.system(size: 14, weight: .bold))
        .foregroundColor(.black)
    
    Text(/* clé conditionnelle */)
        .font(.system(size: 14))
        .foregroundColor(.black)
}
.padding(.bottom, 12)
```

---

## 📱 Exemples Rendus

### Plan Hebdomadaire
```
┌─────────────────────────────────────┐
│ Hebdomadaire                        │
│ 9,99€ / semaine pour 2 utilisateurs │
│ 4,99€ / utilisateur / semaine       │
└─────────────────────────────────────┘

✓ Aucun engagement, annulez à tout moment
```

### Plan Mensuel
```
┌─────────────────────────────────────┐
│ Mensuel - 3 jours gratuits          │
│ 19,99€ / mois pour 2 utilisateurs   │
│ 9,99€ / utilisateur / mois          │
└─────────────────────────────────────┘

✓ Aucun paiement requis maintenant
```

---

## 🤖 Android strings.xml

```xml
<!-- Plans -->
<string name="plan_weekly">Hebdomadaire</string>
<string name="plan_monthly_free_trial">Mensuel - 3 jours gratuits</string>

<!-- Périodes -->
<string name="period_week">semaine</string>
<string name="period_month">mois</string>

<!-- Prix -->
<string name="for_2_users"> pour 2 utilisateurs</string>
<string name="per_user_per">/ utilisateur /</string>

<!-- Messages informatifs -->
<string name="no_payment_required_now">Aucun paiement requis maintenant</string>
<string name="no_commitment_cancel_anytime">Aucun engagement, annulez à tout moment</string>
```

---

## 📊 Logique Conditionnelle

### Affichage Titre
```swift
planType.displayName // Retourne la clé localisée appropriée
```

### Message Informatif
```swift
Text(receiptService.selectedPlan == .monthly ? 
     "no_payment_required_now".localized : 
     "no_commitment_cancel_anytime".localized)
```

### Prix Dynamique
```swift
planType.price // Prix StoreKit formaté
planType.pricePerUser // Prix ÷ 2 automatiquement
```

---

## ✅ Récapitulatif 

**6 clés principales** pour les boutons prix :
1. `plan_weekly` / `plan_monthly_free_trial` 
2. `period_week` / `period_month`
3. `for_2_users`
4. `per_user_per` 
5. `no_payment_required_now`
6. `no_commitment_cancel_anytime`

**Design uniforme** : 17pt → 13pt → 12pt en cascade
**Logique conditionnelle** : Message selon plan sélectionné
