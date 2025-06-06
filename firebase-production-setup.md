# Configuration Firebase PRODUCTION 🚀

## 1. Créer le projet Firebase

1. Allez sur [Firebase Console](https://console.firebase.google.com)
2. Cliquez "Ajouter un projet"
3. Nommez-le "Love2Love-Production"
4. **DÉSACTIVEZ** Google Analytics (pas nécessaire pour votre app)

## 2. Configurer l'authentification (APPLE UNIQUEMENT)

1. Firebase Console → Authentication → Sign-in method
2. Activez **UNIQUEMENT Apple** :
   - Bundle ID : `com.lyes.love2love`
   - Team ID : `GMS9SB6YB7`
   - Key ID : Créé dans Apple Developer Console
   - Private Key : Téléchargé depuis Apple Developer

## 3. Configurer Firestore (MODE PRODUCTION)

1. Firebase Console → Firestore Database
2. **Créez en MODE PRODUCTION** (pas test !)
3. Région : `europe-west1` (pour l'Europe) ou `us-central1` (pour les US)

## 4. Règles de sécurité Firestore PRODUCTION

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Seuls les utilisateurs authentifiés peuvent accéder à leurs propres données
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Interdire tout autre accès
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

## 5. Configuration Apple Developer

### A. Créer une clé Sign in with Apple

1. [Apple Developer Console](https://developer.apple.com/account/resources/authkeys/list)
2. Créez une nouvelle clé
3. Activez "Sign in with Apple"
4. Téléchargez le fichier `.p8`
5. Notez le Key ID

### B. Configurer votre App ID

1. Apple Developer → Identifiers → App IDs
2. Sélectionnez votre app
3. Activez "Sign in with Apple"
4. Configurez comme "Primary App ID"

## 6. Télécharger GoogleService-Info.plist

1. Firebase Console → Paramètres du projet → Vos applications
2. Ajoutez une app iOS
3. Bundle ID : `com.lyes.love2love`
4. Téléchargez `GoogleService-Info.plist`
5. Glissez dans Xcode (Target Membership coché)

## 7. Configuration App Store Connect

### Produit d'abonnement :

- Product ID : `com.lyes.love2love.subscription.weekly`
- Type : Auto-Renewable Subscription
- Durée : 1 semaine
- Prix : 4,99€
- Essai gratuit : 3 jours

## ✅ Checklist avant soumission App Store

- [ ] GoogleService-Info.plist ajouté à Xcode
- [ ] Bundle ID identique partout (Xcode, Firebase, Apple Developer)
- [ ] Sign in with Apple configuré dans Apple Developer
- [ ] Firestore en mode PRODUCTION avec règles sécurisées
- [ ] Produit d'abonnement créé dans App Store Connect
- [ ] App testée sur TestFlight
- [ ] Politique de confidentialité créée (obligatoire pour Firebase)

## 🔒 Sécurité PRODUCTION

- **Authentification** : Apple ID uniquement (plus sécurisé)
- **Données** : Chiffrées automatiquement par Firestore
- **Accès** : Règles strictes (utilisateur = ses données uniquement)
- **Abonnements** : Validation serveur automatique
