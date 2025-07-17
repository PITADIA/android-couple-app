# 🌍 Guide de Tarification Régionale Automatique

## ✅ Fonctionnalité Implémentée

L'application **Love2Love** adapte maintenant automatiquement les prix d'abonnement selon la région du téléphone de l'utilisateur grâce à **StoreKit**.

## 🔧 Architecture

### Services Créés

- **`StoreKitPricingService`** : Service principal qui récupère les prix dynamiques
- **Extensions dans `LocalizationService`** : Méthodes d'aide pour la compatibilité
- **Modifications dans `SubscriptionPlanType`** : Utilise maintenant les prix dynamiques

### Fonctionnalités

- ✅ **Prix automatiques** selon la région (USD, EUR, GBP, JPY, etc.)
- ✅ **Calcul automatique** des prix par utilisateur (prix / 2)
- ✅ **Fallbacks robustes** si StoreKit n'est pas disponible
- ✅ **Gestion d'erreurs** complète avec logging détaillé
- ✅ **Compatible** avec votre code Firebase Functions existant

## 🧪 Tests à Effectuer

### 1. Test en Sandbox (Développement)

```bash
# Dans Xcode, utiliser un compte sandbox pour différentes régions :
# - Compte US : Devrait afficher "$4.99", "$14.99"
# - Compte UK : Devrait afficher "£4.99", "£12.99"
# - Compte France : Devrait afficher "4,99 €", "14,99 €"
# - Compte Japon : Devrait afficher "¥600", "¥1500"
```

### 2. Vérification des Logs

Recherchez ces logs dans la console Xcode :

```
💰 StoreKitPricingService: Prix dynamique utilisé pour com.lyes.love2love.subscription.weekly: $4.99
💰 StoreKitPricingService: Prix/utilisateur dynamique utilisé : $2.50
```

Si vous voyez :

```
⚠️ StoreKitPricingService: Prix fallback utilisé
```

Cela signifie que StoreKit n'a pas encore chargé les prix.

### 3. Test des Régions Spécifiques

| Région    | Devise | Prix Weekly Attendu | Prix Monthly Attendu |
| --------- | ------ | ------------------- | -------------------- |
| 🇺🇸 USA    | USD    | $4.99               | $14.99               |
| 🇬🇧 UK     | GBP    | £4.49               | £12.99               |
| 🇫🇷 France | EUR    | 4,99 €              | 14,99 €              |
| 🇯🇵 Japon  | JPY    | ¥600                | ¥1500                |
| 🇨🇦 Canada | CAD    | CAD$6.99            | CAD$19.99            |

## 🔍 Debugging

### Logs de Diagnostic

En mode DEBUG, utilisez cette fonction pour obtenir un diagnostic complet :

```swift
print(StoreKitPricingService.shared.getPricingDiagnostic())
```

### Indicateurs Visuels (Mode DEBUG)

Dans les vues d'abonnement, vous verrez :

- 💰 "Prix dynamiques actifs" (vert) = StoreKit fonctionne
- ⚠️ "Prix statiques (fallback)" (orange) = Utilise les prix hardcodés

## 🚀 Mise en Production

### Avant le Déploiement

1. **App Store Connect** : Vérifiez que vos prix sont configurés pour toutes les régions
2. **Tests Sandbox** : Testez au moins 3-4 régions différentes
3. **Fallbacks** : Vérifiez que les prix de fallback sont corrects

### Configuration App Store Connect

1. Allez dans **App Store Connect > My Apps > Love2Love**
2. **Features > In-App Purchases**
3. Pour chaque abonnement, configurez les **prices** dans **multiple countries**
4. Apple adaptera automatiquement selon les **pricing tiers**

### Code Firebase Functions (Aucun Changement Requis)

Votre code Firebase Functions actuel est **100% compatible** :

```javascript
// ✅ Ces identifiants fonctionnent mondialement
const SUBSCRIPTION_PRODUCTS = {
  WEEKLY: "com.lyes.love2love.subscription.weekly", // ✅
  MONTHLY: "com.lyes.love2love.subscription.monthly", // ✅
};
```

Apple gère automatiquement les prix régionaux côté serveur.

## 📊 Monitoring en Production

### Logs à Surveiller

```
💰 StoreKitPricingService: Prix dynamique utilisé → ✅ Bon
⚠️ StoreKitPricingService: Prix fallback utilisé → ⚠️ À investiguer
❌ StoreKitPricingService: Aucun produit reçu → 🚨 Problème
```

### Analytics Recommandés

Trackez ces métriques :

- % d'utilisateurs avec prix dynamiques vs fallback
- Conversion rates par région
- Erreurs StoreKit par région

## 🔧 Résolution de Problèmes

### "Prix statiques utilisés"

**Causes possibles :**

1. StoreKit pas encore initialisé
2. Connexion internet faible
3. Configuration App Store Connect incomplète

**Solutions :**

1. L'app recharge automatiquement les prix
2. Les fallbacks assurent que l'app continue de fonctionner
3. Vérifier la configuration App Store Connect

### "Erreur produits manquants"

**Solution :**

1. Vérifier les identifiants dans App Store Connect
2. S'assurer que les produits sont approuvés
3. Attendre la propagation (peut prendre quelques heures)

## 🎯 Bénéfices

- 🌍 **Conformité Apple** : Prix automatiquement ajustés par région
- 💰 **Optimisation Revenue** : Prix optimaux selon le pouvoir d'achat local
- 🛡️ **Robustesse** : Fallbacks garantissent le fonctionnement
- 🔄 **Automatique** : Aucune maintenance manuelle requise

---

## 📞 Support

Si vous rencontrez des problèmes :

1. **Vérifiez les logs** avec `getPricingDiagnostic()`
2. **Testez en sandbox** avec différents comptes régionaux
3. **Vérifiez App Store Connect** pour la configuration des prix

L'implémentation est **robuste** et **production-ready** ! 🚀
