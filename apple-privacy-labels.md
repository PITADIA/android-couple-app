# Étiquettes de Confidentialité Apple - Love2Love

**Document pour App Store Connect - Privacy Labels**  
**Date : 15 décembre 2024**

---

## Guide pour remplir les Privacy Labels dans App Store Connect

### 1. Data Used to Track You (Données utilisées pour vous suivre)

**Réponse : NON**

❌ Aucune donnée n'est collectée pour le suivi publicitaire
❌ Aucun SDK de tracking tiers
❌ Aucun partage avec des réseaux publicitaires
❌ Firebase Analytics est DÉSACTIVÉ

---

### 2. Data Linked to You (Données liées à vous)

#### ✅ Contact Info (Informations de contact)

- **Email Address** : OUI
  - **Utilisé pour :** Account Management
  - **Collecté via :** Apple Sign In (optionnel)

#### ✅ Identifiers (Identifiants)

- **User ID** : OUI
  - **Utilisé pour :** Account Management
  - **Collecté via :** Apple Sign In (identifiant Apple chiffré)

#### ✅ Purchases (Achats)

- **Purchase History** : OUI
  - **Utilisé pour :** Account Management
  - **Collecté via :** StoreKit (abonnement premium)

#### ✅ User Content (Contenu utilisateur)

- **Other User Content** : OUI
  - **Utilisé pour :** App Functionality
  - **Collecté via :** Questions favorites, préférences relationnelles

#### ✅ Usage Data (Données d'utilisation)

- **Product Interaction** : OUI
  - **Utilisé pour :** App Functionality, Analytics
  - **Collecté via :** Catégories consultées, dernière connexion

#### ✅ Sensitive Info (Informations sensibles)

- **Other Sensitive Info** : OUI
  - **Utilisé pour :** App Functionality
  - **Collecté via :** Objectifs relationnels, durée de relation

---

### 3. Data Not Linked to You (Données non liées à vous)

#### ✅ Diagnostics (Diagnostics)

- **Crash Data** : OUI

  - **Utilisé pour :** App Functionality
  - **Collecté via :** Logs d'erreurs anonymisés

- **Performance Data** : OUI
  - **Utilisé pour :** App Functionality
  - **Collecté via :** Temps de chargement, utilisation mémoire

---

## Détail des réponses par catégorie App Store Connect

### Contact Info

```
☑️ Name: NON (collecté via Apple Sign In mais optionnel)
☑️ Email Address: OUI
   Purpose: Account Management
   Data linked to user: YES
```

### Health & Fitness

```
❌ Aucune donnée de santé collectée
```

### Financial Info

```
❌ Aucune donnée financière directe
❌ Les paiements sont gérés par Apple
```

### Location

```
❌ Aucune donnée de localisation
```

### Sensitive Info

```
☑️ Other Sensitive Info: OUI
   Purpose: App Functionality
   Data linked to user: YES
   Description: Objectifs relationnels, durée de relation
```

### Contacts

```
❌ Aucun accès aux contacts
```

### User Content

```
☑️ Other User Content: OUI
   Purpose: App Functionality
   Data linked to user: YES
   Description: Questions favorites, préférences
```

### Browsing History

```
❌ Aucun historique de navigation web
```

### Search History

```
❌ Aucun historique de recherche
```

### Identifiers

```
☑️ User ID: OUI
   Purpose: Account Management
   Data linked to user: YES
   Description: Identifiant Apple chiffré

❌ Device ID: NON
❌ Advertising Identifier: NON
```

### Purchases

```
☑️ Purchase History: OUI
   Purpose: Account Management
   Data linked to user: YES
   Description: Statut abonnement premium
```

### Usage Data

```
☑️ Product Interaction: OUI
   Purpose: App Functionality, Analytics
   Data linked to user: YES
   Description: Catégories consultées, préférences

❌ Advertising Data: NON
❌ Other Usage Data: NON
```

### Diagnostics

```
☑️ Crash Data: OUI
   Purpose: App Functionality
   Data linked to user: NO
   Description: Logs d'erreurs anonymisés

☑️ Performance Data: OUI
   Purpose: App Functionality
   Data linked to user: NO
   Description: Temps de chargement, mémoire

❌ Other Diagnostic Data: NON
```

---

## Réponses aux questions spécifiques d'Apple

### "Do you or your third-party partners collect data from this app?"

**Réponse : OUI**

### "Is data collected from this app used for tracking?"

**Réponse : NON**

### "Do you collect data for advertising purposes?"

**Réponse : NON**

### "Do you share data with data brokers?"

**Réponse : NON**

### "Do you use data for analytics?"

**Réponse : OUI - Uniquement pour améliorer l'app, pas pour la publicité**

---

## Partenaires tiers à déclarer

### Firebase (Google)

- **Type :** Backend as a Service
- **Données partagées :** Données de profil, authentification
- **Finalité :** Synchronisation et sauvegarde
- **Tracking :** NON (Analytics désactivé)

### Apple

- **Type :** Plateforme
- **Données partagées :** Authentification, achats
- **Finalité :** Connexion et paiements
- **Tracking :** NON

### Realm (MongoDB)

- **Type :** Base de données locale
- **Données partagées :** AUCUNE (stockage local uniquement)
- **Finalité :** Cache local
- **Tracking :** NON

---

## Checklist de vérification avant soumission

### ✅ Données correctement déclarées

- [x] Toutes les données collectées sont listées
- [x] Finalités clairement définies
- [x] Distinction "linked/not linked" correcte
- [x] Aucune donnée de tracking déclarée

### ✅ Cohérence avec le code

- [x] Firebase Analytics désactivé (vérifié dans GoogleService-Info.plist)
- [x] Pas de SDK publicitaire
- [x] Apple Sign In correctement configuré
- [x] StoreKit pour les achats uniquement

### ✅ Documentation

- [x] Politique de confidentialité complète
- [x] URL de la politique dans App Store Connect
- [x] Contact contact@love2loveapp.com fonctionnel

---

## URLs à fournir dans App Store Connect

### Privacy Policy URL

```
https://votre-site.com/privacy-policy
```

### Terms of Use URL (optionnel)

```
https://votre-site.com/terms-of-use
```

### Support URL

```
https://votre-site.com/support
ou
mailto:contact@love2loveapp.com
```

---

## Notes importantes pour la review Apple

1. **Cohérence** : Les Privacy Labels doivent correspondre exactement au code de l'app
2. **Transparence** : Toute collecte de données doit être déclarée, même minime
3. **Finalité** : Chaque donnée doit avoir une finalité claire et légitime
4. **Tracking** : Apple est très strict sur la définition du tracking
5. **Mise à jour** : Les labels doivent être mis à jour à chaque changement

---

**Ce document doit être utilisé pour remplir précisément les Privacy Labels dans App Store Connect lors de la soumission de l'application.**
