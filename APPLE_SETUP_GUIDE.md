# 🍎 Guide de Configuration Apple Store Connect + Firebase

## 📋 Étapes de Configuration

### 1. **App Store Connect - Créer la Clé API**

1. Allez sur [App Store Connect](https://appstoreconnect.apple.com)
2. **Users and Access** → **Keys** → **App Store Connect API**
3. Cliquez sur **Generate API Key**
4. **Name**: `Love2Love API Key`
5. **Access**: `Developer`
6. **Download** le fichier `.p8` (⚠️ Une seule fois possible!)
7. Notez le **Key ID** et **Issuer ID**

### 2. **App Store Connect - Shared Secret**

1. **My Apps** → **Love2Love** → **App Information**
2. **App Store Connect** section
3. **Master Shared Secret** → **Generate**
4. Copiez le secret généré

### 3. **Firebase Functions - Configuration**

```bash
# Installer Firebase CLI
npm install -g firebase-tools

# Se connecter à Firebase
firebase login

# Aller dans le dossier du projet
cd /Users/lyes/Desktop/CoupleApp

# Configurer les variables d'environnement
firebase functions:config:set \
  apple.key_id="VOTRE_KEY_ID" \
  apple.issuer_id="VOTRE_ISSUER_ID" \
  apple.shared_secret="VOTRE_SHARED_SECRET" \
  apple.environment="sandbox" \
  apple.private_key="-----BEGIN PRIVATE KEY-----
VOTRE_CLE_PRIVEE_P8_ICI
-----END PRIVATE KEY-----"

# Déployer les functions
cd firebase
npm install
firebase deploy --only functions
```

### 4. **Configuration des Webhooks Apple**

1. **App Store Connect** → **My Apps** → **Love2Love**
2. **App Information** → **App Store Server Notifications**
3. **Production Server URL**: `https://VOTRE_REGION-VOTRE_PROJECT.cloudfunctions.net/appleWebhook`
4. **Sandbox Server URL**: `https://VOTRE_REGION-VOTRE_PROJECT.cloudfunctions.net/appleWebhook`
5. **Version**: `V2`

### 5. **Tester la Configuration**

```bash
# Tester les functions localement
cd firebase
npm run serve

# Voir les logs
firebase functions:log
```

## 🔐 Sécurité des Clés

### ⚠️ **IMPORTANT - Clé Privée P8**

```bash
# Format de la clé privée pour Firebase:
firebase functions:config:set apple.private_key="-----BEGIN PRIVATE KEY-----
MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg...
VOTRE_CLE_COMPLETE_ICI...
...asdfghjklqwertyuiop
-----END PRIVATE KEY-----"
```

### 📝 **Variables d'Environnement Requises**

- `apple.key_id`: ID de votre clé API (ex: `2X9R4HXF34`)
- `apple.issuer_id`: ID de l'émetteur (ex: `57246542-96fe-1a63-e053-0824d011072a`)
- `apple.shared_secret`: Secret partagé de l'app
- `apple.private_key`: Clé privée P8 complète
- `apple.environment`: `sandbox` ou `production`

## 🧪 Test de Validation

### Test Manuel du Reçu

```javascript
// Dans la console Firebase Functions
const receiptData = "BASE64_RECEIPT_DATA";
const result = await validateAppleReceipt({
  receiptData: receiptData,
  productId: "com.coupleapp.subscription.weekly",
});
console.log(result);
```

### Logs à Surveiller

```
🔥 validateAppleReceipt: Début de la validation
🔥 validateAppleReceipt: Validation du reçu pour le produit: com.coupleapp.subscription.weekly
🔥 validateAppleReceipt: Résultat de la validation: 0
🔥 validateAppleReceipt: Reçu valide, traitement des achats
🔥 validateAppleReceipt: Abonnement mis à jour pour l'utilisateur: USER_ID
```

## 🚀 Déploiement

### Commandes de Déploiement

```bash
# Déployer seulement les functions
firebase deploy --only functions

# Déployer tout
firebase deploy

# Voir les logs en temps réel
firebase functions:log --follow
```

### URLs des Functions

- **Validation**: `https://REGION-PROJECT.cloudfunctions.net/validateAppleReceipt`
- **Webhook**: `https://REGION-PROJECT.cloudfunctions.net/appleWebhook`
- **Status**: `https://REGION-PROJECT.cloudfunctions.net/checkSubscriptionStatus`

## 🔍 Debugging

### Erreurs Communes

1. **"Invalid receipt"** → Vérifiez l'environnement (sandbox/production)
2. **"Product not found"** → Vérifiez l'ID du produit
3. **"Unauthorized"** → Vérifiez les clés API
4. **"Network error"** → Vérifiez la connexion Firebase

### Test avec Postman

```json
POST https://REGION-PROJECT.cloudfunctions.net/validateAppleReceipt
{
  "data": {
    "receiptData": "BASE64_RECEIPT_DATA",
    "productId": "com.coupleapp.subscription.weekly"
  }
}
```

## 📱 Configuration iOS

### Ajout de FirebaseFunctions

```swift
// Dans Podfile
pod 'FirebaseFunctions'

// Dans AppDelegate
import FirebaseFunctions
```

### Test de Connexion

```swift
let functions = Functions.functions()
let testFunction = functions.httpsCallable("checkSubscriptionStatus")

testFunction.call { result, error in
    if let error = error {
        print("Erreur: \(error)")
    } else {
        print("Connexion OK: \(result?.data)")
    }
}
```

---

## 🎯 Résultat Final

Une fois configuré, votre app aura :

✅ **Validation sécurisée** des reçus Apple côté serveur  
✅ **Webhooks automatiques** pour les renouvellements/annulations  
✅ **Synchronisation** en temps réel avec Firebase  
✅ **Logs détaillés** pour le debugging  
✅ **Architecture sécurisée** sans clés exposées côté client

La sheet de paiement Apple s'affichera normalement, et la validation se fera de manière sécurisée côté serveur ! 🔥
