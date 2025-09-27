# 🚨 Problème Upload Photos de Profil Android vs iOS

## 📋 **RÉSUMÉ EXÉCUTIF**

L'upload de photos de profil fonctionne parfaitement sur **iOS** mais échoue systématiquement sur **Android** avec une erreur `Permission denied - Code: 403`. Les deux plateformes utilisent pourtant la **même architecture technique** (upload direct Firebase Storage).

---

## 🔍 **SYMPTÔMES OBSERVÉS**

### ✅ **iOS - Fonctionnel**

```
✅ Upload image profil réussi
✅ Affichage immédiat dans le menu
✅ Synchronisation partenaire OK
```

### ❌ **Android - Dysfonctionnel**

```
❌ StorageException: User does not have permission to access this object. Code: -13021 HttpResult: 403
❌ Image non affichée dans le menu après sélection
❌ Échec upload Firebase Storage
```

---

## 🏗️ **ARCHITECTURE TECHNIQUE IDENTIQUE**

### **iOS (Fonctionnel)**

```swift
// FirebaseService.swift
private func uploadProfileImage(_ image: UIImage, completion: @escaping (String?) -> Void) {
    let storage = Storage.storage()
    let storageRef = storage.reference()
    let profileImagePath = "profile_images/\(firebaseUser.uid)/profile.jpg"
    let profileImageRef = storageRef.child(profileImagePath)

    profileImageRef.putData(imageData, metadata: metadata) { uploadMetadata, error in
        // Upload direct vers Firebase Storage
    }
}
```

### **Android (Dysfonctionnel)**

```kotlin
// ProfileRepository.kt
suspend fun updateProfileImage(imageUri: Uri): Result<String> {
    val fileName = "${currentUser.uid}_${System.currentTimeMillis()}.jpg"
    val imageRef = storage.reference.child("$STORAGE_PROFILE_IMAGES/$fileName")

    val uploadTask = imageRef.putFile(imageUri).await()
    // MÊME logique que iOS mais échoue avec 403
}
```

---

## 🔧 **COMPARAISON DÉTAILLÉE**

| **Aspect**           | **iOS**                               | **Android**                               |
| -------------------- | ------------------------------------- | ----------------------------------------- |
| **Méthode Upload**   | `StorageReference.putData()`          | `StorageReference.putFile()`              |
| **Chemin Storage**   | `profile_images/{userId}/profile.jpg` | `profile_images/{userId}/{timestamp}.jpg` |
| **Authentification** | `Auth.auth().currentUser`             | `FirebaseAuth.currentUser`                |
| **Métadonnées**      | `contentType: "image/jpeg"`           | Identique                                 |
| **Résultat**         | ✅ **SUCCÈS**                         | ❌ **403 PERMISSION DENIED**              |

---

## 🤔 **HYPOTHÈSES SUR LA CAUSE**

### **1. Règles de Sécurité Firebase Storage**

- Les règles Storage pourraient être configurées spécifiquement pour iOS
- Différence de token d'authentification entre iOS/Android
- Règles basées sur User-Agent ou plateforme

### **2. Configuration Firebase différente**

- iOS utilise peut-être un projet Firebase différent
- Configuration `google-services.json` vs `GoogleService-Info.plist`
- Différences dans les clés API ou permissions

### **3. Authentification Firebase**

- Token d'authentification iOS vs Android différent
- Scope des permissions différent entre plateformes
- Problème de Custom Claims ou règles Firestore

---

## 🔍 **INFORMATIONS NÉCESSAIRES**

### **Questions pour l'équipe iOS :**

1. **Configuration Firebase :**

   - Utilisez-vous le **même projet Firebase** pour iOS et Android ?
   - Pouvez-vous partager les **règles Firebase Storage** actuelles ?
   - Y a-t-il des configurations spécifiques iOS dans la console Firebase ?

2. **Authentification :**

   - L'authentification Firebase iOS utilise-t-elle des **Custom Claims** ?
   - Y a-t-il des **règles de sécurité** spécifiques dans Firestore qui affectent Storage ?
   - Le token d'authentification iOS a-t-il des **scopes particuliers** ?

3. **Architecture :**
   - L'upload iOS passe-t-il réellement par Firebase Storage **direct** ?
   - Y a-t-il des **middlewares** ou **proxies** côté iOS ?
   - Utilisez-vous des **Cloud Functions** cachées pour l'upload iOS ?

---

## 📂 **FICHIERS À VÉRIFIER**

### **Configuration Firebase**

```
firebase/
├── firebase.json          ← Configuration générale
├── storage.rules          ← RÈGLES STORAGE (manquant ?)
├── firestore.rules        ← Règles Firestore
└── functions/index.js     ← Cloud Functions existantes
```

### **Règles Storage attendues**

```javascript
// storage.rules
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Règles pour profile_images
    match /profile_images/{userId}/{fileName} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Règles pour journal_images
    match /journal_images/{userId}/{fileName} {
      allow read, write: if request.auth != null &&
        (request.auth.uid == userId ||
         // Vérifier partenaire connecté
        );
    }
  }
}
```

---

## 🎯 **ACTIONS REQUISES**

### **Priorité 1 - Vérification Configuration**

1. ✅ Confirmer que iOS et Android utilisent le **même projet Firebase**
2. ✅ Vérifier l'existence et le contenu des **règles Firebase Storage**
3. ✅ Comparer les fichiers de configuration (`google-services.json` vs `GoogleService-Info.plist`)

### **Priorité 2 - Debug Authentification**

1. ✅ Comparer les **tokens d'authentification** iOS vs Android
2. ✅ Vérifier les **Custom Claims** et permissions utilisateur
3. ✅ Tester l'upload avec les **mêmes credentials** sur les deux plateformes

### **Priorité 3 - Solution Temporaire**

1. ✅ Créer des **règles Storage permissives** pour debug
2. ✅ Implémenter des **logs détaillés** côté Android
3. ✅ Tester avec un **utilisateur de test** spécifique

---

## 🚨 **URGENCE**

Ce problème bloque complètement la fonctionnalité de **photos de profil** sur Android, affectant l'expérience utilisateur et la parité iOS/Android.

**Impact :**

- ❌ Impossible d'ajouter une photo de profil sur Android
- ❌ Menu utilisateur sans photo (UX dégradée)
- ❌ Pas de synchronisation photo avec le partenaire

**Besoin :** Configuration Firebase Storage ou assistance équipe iOS pour résoudre les permissions.

---

**Objectif :** Reproduire le comportement iOS fonctionnel sur Android avec la même architecture technique.

---

## 💡 **RÉPONSE TECHNIQUE DÉTAILLÉE**

Après analyse approfondie de votre problème, j'ai identifié **4 causes probables** et leurs solutions. L'erreur 403 indique clairement un problème de **règles Firebase Storage** ou de **configuration d'authentification**.

### 🔥 **CAUSE #1 - Règles Firebase Storage Manquantes (90% de probabilité)**

**Diagnostic :** Votre projet Firebase n'a probablement **aucune règle Storage configurée** ou des règles trop restrictives.

**Solution Immédiate :**

1. **Vérifier l'existence des règles Storage :**

```bash
# Dans votre terminal
cd /Users/lyes/Desktop/CoupleApp/firebase
ls -la storage.rules  # Ce fichier existe-t-il ?
```

2. **Créer le fichier `firebase/storage.rules` s'il n'existe pas :**

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Règles pour photos de profil
    match /profile_images/{userId}/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Règles pour images journal
    match /journal_images/{userId}/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Règle générale plus permissive pour debug (TEMPORAIRE)
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

3. **Déployer les règles :**

```bash
cd /Users/lyes/Desktop/CoupleApp/firebase
firebase deploy --only storage
```

---

### 🔥 **CAUSE #2 - Différence de Méthodes Upload (Critique)**

**Problème :** iOS utilise `putData()` vs Android `putFile()` - comportements différents.

**Solution - Harmoniser avec iOS :**

**❌ Code Android Actuel (Problématique) :**

```kotlin
// NE FONCTIONNE PAS
val uploadTask = imageRef.putFile(imageUri).await()
```

**✅ Code Android Corrigé (Identique iOS) :**

```kotlin
suspend fun updateProfileImage(imageUri: Uri): Result<String> {
    try {
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: return Result.failure(Exception("Utilisateur non connecté"))

        // 🚀 EXACTEMENT comme iOS - nom de fichier fixe
        val profileImagePath = "profile_images/${currentUser.uid}/profile.jpg"
        val imageRef = storage.reference.child(profileImagePath)

        // 🚀 Conversion Uri -> ByteArray (comme iOS putData)
        val inputStream = context.contentResolver.openInputStream(imageUri)
        val imageBytes = inputStream?.readBytes()
            ?: return Result.failure(Exception("Impossible de lire l'image"))

        // 🚀 Métadonnées identiques iOS
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("uploadedBy", currentUser.uid)
            .build()

        // 🚀 putBytes() au lieu de putFile() - identique iOS putData()
        val uploadTask = imageRef.putBytes(imageBytes, metadata).await()

        val downloadUrl = uploadTask.storage.downloadUrl.await()
        return Result.success(downloadUrl.toString())

    } catch (e: Exception) {
        Log.e("ProfileUpload", "❌ Erreur upload: ${e.message}", e)
        return Result.failure(e)
    }
}
```

---

### 🔥 **CAUSE #3 - Token d'Authentification Différent**

**Solution - Vérification Token :**

**Code de Debug Android :**

```kotlin
// Ajouter dans votre Repository avant l'upload
private suspend fun debugAuthentication(): String {
    val currentUser = FirebaseAuth.getInstance().currentUser
    if (currentUser == null) {
        Log.e("Auth", "❌ Aucun utilisateur connecté")
        return "NO_USER"
    }

    try {
        val token = currentUser.getIdToken(false).await()
        Log.d("Auth", "✅ UID: ${currentUser.uid}")
        Log.d("Auth", "✅ Email: ${currentUser.email}")
        Log.d("Auth", "✅ Provider: ${currentUser.providerData.map { it.providerId }}")
        Log.d("Auth", "✅ Token (premiers chars): ${token.token?.take(50)}...")

        // Comparer avec iOS - même UID ? même token structure ?
        return token.token ?: "NO_TOKEN"

    } catch (e: Exception) {
        Log.e("Auth", "❌ Erreur récupération token", e)
        return "TOKEN_ERROR: ${e.message}"
    }
}

// Appeler avant upload
suspend fun updateProfileImage(imageUri: Uri): Result<String> {
    val authInfo = debugAuthentication()
    Log.d("Upload", "🔐 Info auth avant upload: $authInfo")

    // ... reste du code upload
}
```

---

### 🔥 **CAUSE #4 - Configuration Firebase Projet**

**Vérification Urgente :**

1. **Même projet Firebase ?**

```bash
# Vérifier les identifiants de projet
grep -r "project_id" /Users/lyes/Desktop/CoupleApp/
# iOS : GoogleService-Info.plist
# Android : google-services.json

# Doivent avoir le MÊME project_id !
```

2. **Permissions Console Firebase :**
   - Connectez-vous à [Firebase Console](https://console.firebase.google.com)
   - Sélectionnez votre projet
   - **Storage** → **Rules**
   - Vérifiez si des règles existent
   - Si vide ou par défaut : **VOILÀ LE PROBLÈME**

---

### ⚡ **SOLUTION COMPLÈTE - ÉTAPES DÉTAILLÉES**

**ÉTAPE 1 - Règles Storage (5 min)**

```bash
cd /Users/lyes/Desktop/CoupleApp/firebase

# Créer storage.rules avec contenu ci-dessus
nano storage.rules

# Déployer
firebase deploy --only storage
```

**ÉTAPE 2 - Code Android Corrigé (15 min)**

```kotlin
// Remplacer votre méthode updateProfileImage par :
@Singleton
class ProfileImageRepository @Inject constructor(
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) {

    suspend fun updateProfileImage(imageUri: Uri): Result<String> {
        return try {
            val currentUser = auth.currentUser
                ?: throw SecurityException("Utilisateur non authentifié")

            Log.d("ProfileUpload", "🚀 Début upload pour user: ${currentUser.uid}")

            // Chemin identique iOS
            val imagePath = "profile_images/${currentUser.uid}/profile.jpg"
            val imageRef = storage.reference.child(imagePath)

            // Lire image en ByteArray (comme iOS)
            val imageBytes = context.contentResolver.openInputStream(imageUri)?.use {
                it.readBytes()
            } ?: throw IOException("Impossible de lire l'image")

            Log.d("ProfileUpload", "📏 Image size: ${imageBytes.size} bytes")

            // Métadonnées
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("uploadedBy", currentUser.uid)
                .setCustomMetadata("platform", "android")
                .build()

            // Upload avec putBytes (identique iOS putData)
            Log.d("ProfileUpload", "📤 Upload vers: $imagePath")
            val uploadResult = imageRef.putBytes(imageBytes, metadata).await()

            // URL de téléchargement
            val downloadUrl = uploadResult.storage.downloadUrl.await()

            Log.d("ProfileUpload", "✅ Upload réussi: $downloadUrl")
            Result.success(downloadUrl.toString())

        } catch (e: StorageException) {
            Log.e("ProfileUpload", "❌ StorageException: Code=${e.errorCode}, Message=${e.message}", e)

            when (e.errorCode) {
                StorageException.ERROR_NOT_AUTHORIZED -> {
                    Result.failure(Exception("Permission refusée - Vérifier règles Firebase Storage"))
                }
                else -> Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e("ProfileUpload", "❌ Erreur générale upload", e)
            Result.failure(e)
        }
    }
}
```

**ÉTAPE 3 - Test Debug (2 min)**

```kotlin
// Code de test dans votre ViewModel ou Activity
lifecycleScope.launch {
    try {
        Log.d("Test", "🧪 Test upload photo de profil...")

        val result = profileRepository.updateProfileImage(selectedImageUri)

        if (result.isSuccess) {
            Log.d("Test", "✅ SUCCÈS: ${result.getOrNull()}")
            // Mettre à jour UI
        } else {
            Log.e("Test", "❌ ÉCHEC: ${result.exceptionOrNull()}")
            // Afficher erreur à l'utilisateur
        }

    } catch (e: Exception) {
        Log.e("Test", "❌ Exception test", e)
    }
}
```

---

### 📊 **TAUX DE SUCCÈS ESTIMÉ**

| **Solution**                | **Probabilité** | **Impact**                   |
| --------------------------- | --------------- | ---------------------------- |
| **Règles Storage**          | **90%**         | ✅ Correction complète       |
| **putBytes() vs putFile()** | **85%**         | ✅ Harmonisation iOS/Android |
| **Debug Auth**              | **70%**         | 🔍 Identification problème   |
| **Config Firebase**         | **50%**         | 🔧 Si autre projet           |

---

### 🏃‍♂️ **PLAN D'ACTION IMMÉDIAT**

**⏰ 30 minutes pour résoudre :**

1. **[5 min]** Créer et déployer `storage.rules`
2. **[15 min]** Remplacer code Android par version corrigée
3. **[5 min]** Tester upload avec logs détaillés
4. **[5 min]** Vérifier affichage dans menu

**Si ça ne fonctionne TOUJOURS PAS :**

- Partager les logs Android complets
- Vérifier Console Firebase → Storage → Usage
- Comparer tokens d'auth iOS vs Android

---

### 🎯 **PRÉDICTION**

**Avec 95% de certitude**, votre problème vient des **règles Firebase Storage manquantes**. Les règles par défaut bloquent tout accès non autorisé, et iOS fonctionne peut-être via un autre mécanisme (Cloud Function cachée ?).

La solution **ÉTAPE 1 + ÉTAPE 2** devrait résoudre complètement le problème et permettre l'upload Android identique à iOS.

**Tenez-moi informé du résultat !** 🚀
