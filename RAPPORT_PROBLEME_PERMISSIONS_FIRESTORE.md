# 🚨 PROBLÈME PERMISSIONS FIRESTORE - iOS vs Android

## 🎯 Analyse du Problème

Tu as raison ! Il y a effectivement une **différence cruciale** entre iOS et Android dans la gestion de la reconnexion après suppression de compte. L'iOS gère ce cas avec une logique sophistiquée que l'Android n'a pas.

---

## 🔍 Pourquoi ça marche sur iOS

### 1. Détection Temporelle Intelligente (FirebaseService.swift)

```swift
func loadUserData(uid: String) {
    db.collection("users").document(uid).getDocument { [weak self] document, error in

        guard let document = document, document.exists,
              let data = document.data() else {
            print("🔥 Aucune donnée trouvée - onboarding requis")
            self?.isAuthenticated = true
            self?.currentUser = nil
            return
        }

        // 🔧 LOGIQUE CLÉE - Détection reconnexion après suppression ratée
        let lastLoginDate = data["lastLoginDate"] as? Timestamp
        let createdAt = data["createdAt"] as? Timestamp

        if let lastLogin = lastLoginDate?.dateValue(),
           let creation = createdAt?.dateValue() {

            let timeSinceCreation = Date().timeIntervalSince(creation)
            let timeSinceLastLogin = Date().timeIntervalSince(lastLogin)

            // SI compte créé récemment (< 5 min) MAIS dernière connexion ancienne (> 1 min)
            // ET onboarding pas terminé = Suppression ratée détectée
            if timeSinceCreation < 300 && timeSinceLastLogin > 60 &&
               (onboardingInProgress || !hasValidData) {

                print("🔥 DÉTECTION - Possible reconnexion après suppression ratée")
                print("🔥 - Créé il y a: \\(timeSinceCreation) secondes")
                print("🔥 - Dernière connexion il y a: \\(timeSinceLastLogin) secondes")

                // 🗑️ Supprimer les données résiduelles AUTOMATIQUEMENT
                self?.db.collection("users").document(uid).delete { deleteError in
                    if let deleteError = deleteError {
                        print("❌ Erreur suppression forcée: \\(deleteError.localizedDescription)")
                    } else {
                        print("✅ Données résiduelles supprimées avec succès")
                    }
                }

                // ✅ Forcer l'onboarding SANS déconnecter l'utilisateur
                self?.isAuthenticated = true  // 🔑 CLEF - Garder auth
                self?.currentUser = nil       // 🔑 CLEF - Pas de données
                return
            }
        }

        // Sinon charger normalement...
    }
}
```

### 2. Séquence iOS (Qui Marche)

```
1. Utilisateur se connecte avec Apple → Firebase Auth OK ✅
2. loadUserData(uid) appelé
3. Document n'existe pas OU suppression ratée détectée
4. iOS garde isAuthenticated = true MAIS currentUser = nil
5. createEmptyUserProfile appelé avec utilisateur ENCORE connecté
6. Règles Firestore OK car request.auth.uid == resource.id ✅
7. Nouveau profil créé avec succès ✅
```

---

## ❌ Pourquoi ça plante sur Android

### 1. Séquence Android (Qui Plante)

```
1. Utilisateur se connecte avec Google → Firebase Auth OK ✅
2. GoogleAuthService essaie de créer le document
3. EN PARALLÈLE, UserDataIntegrationService détecte document absent
4. handleAccountDeletionDetected se déclenche
5. Utilisateur déconnecté (auth.signOut()) ❌
6. GoogleAuthService essaie ENCORE d'écrire mais auth.uid = null ❌
7. Règles Firestore bloquent : PERMISSION_DENIED ❌
```

### 2. Le Conflit de Timing

```kotlin
// Dans GoogleAuthService - Thread A
private fun createEmptyUserProfile(firebaseUser: FirebaseUser, googleAccount: GoogleSignInAccount) {
    val userData = mapOf(/* ... */)

    // ❌ PROBLÈME: Utilisateur peut être déconnecté PENDANT cet appel
    firebaseFirestore.collection("users").document(firebaseUser.uid)
        .set(userData, SetOptions.merge())  // PERMISSION_DENIED !
}

// Dans UserDataIntegrationService - Thread B (EN PARALLÈLE)
private suspend fun checkUserDataExists(): Boolean {
    val currentUser = auth.currentUser ?: return false
    val document = firestore.collection("users").document(currentUser.uid).get().await()

    if (!document.exists()) {
        // ❌ PROBLÈME: Déclenche déconnexion pendant que Thread A écrit encore
        handleAccountDeletionDetected()  // auth.signOut() ici !
        return false
    }
    return true
}
```

---

## 🔧 Solutions pour Android

### 1. Solution Immédiate - Flag de Protection

```kotlin
class UserDataIntegrationService {

    // 🛡️ Flag pour désactiver temporairement la détection de suppression
    private var suppressAccountDeletionDetection = false
    private val suppressionTimeoutMs = 10000L // 10 secondes

    fun suppressAccountDeletionDetectionTemporarily() {
        suppressAccountDeletionDetection = true

        // Auto-réactivation après timeout
        viewModelScope.launch {
            delay(suppressionTimeoutMs)
            suppressAccountDeletionDetection = false
            Log.d("UserData", "🛡️ Protection suppression désactivée après timeout")
        }
    }

    private suspend fun checkUserDataExists(): Boolean {
        // 🛡️ Si protection active, ne pas détecter la suppression
        if (suppressAccountDeletionDetection) {
            Log.d("UserData", "🛡️ Détection suppression temporairement désactivée")
            return true // Faire comme si tout allait bien
        }

        val currentUser = auth.currentUser ?: return false
        val document = firestore.collection("users").document(currentUser.uid).get().await()

        if (!document.exists()) {
            handleAccountDeletionDetected()
            return false
        }
        return true
    }
}
```

### 2. Modification GoogleAuthService

```kotlin
class GoogleAuthService {

    @Inject
    lateinit var userDataIntegrationService: UserDataIntegrationService

    private fun createEmptyUserProfile(firebaseUser: FirebaseUser, googleAccount: GoogleSignInAccount) {

        // 🛡️ PROTECTION - Désactiver détection suppression temporairement
        userDataIntegrationService.suppressAccountDeletionDetectionTemporarily()

        Log.d("Auth", "🔥 Création profil utilisateur vide avec protection")

        val userData = mapOf(
            "id" to UUID.randomUUID().toString(),
            "googleUserID" to firebaseUser.uid,
            "email" to (googleAccount.email ?: ""),
            "name" to (googleAccount.displayName ?: ""),
            "profileImageURL" to (googleAccount.photoUrl?.toString() ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "lastLoginDate" to FieldValue.serverTimestamp(),
            "onboardingInProgress" to true,
            "isSubscribed" to false,
            "partnerCode" to "",
            "partnerId" to "",
            "relationshipGoals" to emptyList<String>(),
            "relationshipDuration" to "notInRelationship"
        )

        firestore.collection("users").document(firebaseUser.uid)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Auth", "✅ Profil utilisateur vide créé avec succès")
                updateUiState { it.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    user = null // Pas de données complètes encore
                )}
            }
            .addOnFailureListener { error ->
                Log.e("Auth", "❌ Erreur création profil: ${error.message}")
                updateUiState { it.copy(
                    isLoading = false,
                    error = "Erreur création profil: ${error.message}"
                )}
            }
    }
}
```

### 3. Solution Avancée - Logique Temporelle comme iOS

```kotlin
class UserDataIntegrationService {

    private suspend fun checkUserDataExists(): Boolean {
        val currentUser = auth.currentUser ?: return false

        try {
            val document = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()

            if (!document.exists()) {
                Log.d("UserData", "🔥 Document utilisateur n'existe pas")

                // 🔧 PAS de handleAccountDeletionDetected ici
                // Laisser GoogleAuthService créer le profil tranquillement
                return false

            } else {
                // 🔧 LOGIQUE TEMPORELLE comme iOS
                val data = document.data
                val lastLoginTimestamp = data?.get("lastLoginDate") as? Timestamp
                val createdAtTimestamp = data?.get("createdAt") as? Timestamp

                if (lastLoginTimestamp != null && createdAtTimestamp != null) {
                    val now = System.currentTimeMillis()
                    val timeSinceCreation = now - createdAtTimestamp.toDate().time
                    val timeSinceLastLogin = now - lastLoginTimestamp.toDate().time

                    // SI compte récent (< 5 min) MAIS dernière connexion ancienne (> 1 min)
                    if (timeSinceCreation < 300000 && timeSinceLastLogin > 60000) {
                        Log.d("UserData", "🔥 DÉTECTION - Reconnexion après suppression ratée")
                        Log.d("UserData", "- Créé il y a: ${timeSinceCreation / 1000} secondes")
                        Log.d("UserData", "- Dernière connexion il y a: ${timeSinceLastLogin / 1000} secondes")

                        // 🗑️ Supprimer données résiduelles
                        firestore.collection("users")
                            .document(currentUser.uid)
                            .delete()
                            .await()

                        Log.d("UserData", "✅ Données résiduelles supprimées")

                        // ✅ Retourner false SANS déconnecter pour permettre création nouveau profil
                        return false
                    }
                }

                return true
            }

        } catch (e: Exception) {
            Log.e("UserData", "❌ Erreur vérification existence: ${e.message}")

            // En cas d'erreur, NE PAS déclencher handleAccountDeletionDetected
            // Laisser une chance au processus de création
            return false
        }
    }

    // 🔧 Nouvelle méthode séparée pour la vraie détection de suppression
    suspend fun detectAccountDeletionIfNeeded() {
        val currentUser = auth.currentUser ?: return

        try {
            val document = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()

            if (!document.exists()) {
                // Attendre un peu pour laisser le temps à la création
                delay(5000) // 5 secondes

                // Re-vérifier
                val documentRecheck = firestore.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()

                if (!documentRecheck.exists()) {
                    Log.w("UserData", "🚨 Compte vraiment supprimé détecté après délai")
                    handleAccountDeletionDetected()
                }
            }

        } catch (e: Exception) {
            Log.e("UserData", "❌ Erreur détection suppression: ${e.message}")
        }
    }
}
```

### 4. Règles Firestore Optimisées

```javascript
// Dans Firebase Console - Firestore Rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      // Permettre lecture/écriture si utilisateur connecté ET c'est son document
      allow read, write: if request.auth != null && request.auth.uid == userId;

      // ✅ NOUVEAU - Permettre création même si document n'existait pas avant
      allow create: if request.auth != null && request.auth.uid == userId;

      // ✅ NOUVEAU - Permettre suppression pour nettoyage
      allow delete: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

---

## 🚀 Implémentation Recommandée

### Plan d'Action Android

```kotlin
// 1. Modifier UserDataIntegrationService
class UserDataIntegrationService {

    fun suppressAccountDeletionDetectionFor(durationMs: Long = 10000L) {
        // Implémenter logique de protection temporaire
    }

    suspend fun handleReconnectionAfterDeletion(): Boolean {
        // Implémenter logique temporelle iOS
    }
}

// 2. Modifier GoogleAuthService
class GoogleAuthService {

    private fun handleNewUser(firebaseUser: FirebaseUser, account: GoogleSignInAccount) {
        // Activer protection avant création
        userDataIntegrationService.suppressAccountDeletionDetectionFor(15000L)

        // Créer profil vide
        createEmptyUserProfile(firebaseUser, account)
    }
}

// 3. Coordonner avec AppStateRepository
class AppStateRepository {

    init {
        // Observer auth changes AVANT data integration
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                // Donner priorité à la création de profil
                viewModelScope.launch {
                    delay(2000) // Attendre création profil
                    userDataIntegrationService.detectAccountDeletionIfNeeded()
                }
            }
        }
    }
}
```

---

## 📋 Résumé

### ✅ Pourquoi iOS marche

1. **Détection temporelle** sophistiquée des suppressions ratées
2. **Séquence contrôlée** : auth → detection → nettoyage → création
3. **Pas de déconnexion** pendant la création de nouveau profil
4. **createEmptyUserProfile** simple avec utilisateur authentifié

### ❌ Pourquoi Android plante

1. **Pas de détection temporelle** comme iOS
2. **Conflit de timing** entre création et détection suppression
3. **Déconnexion prématurée** pendant écriture Firestore
4. **PERMISSION_DENIED** car `request.auth.uid` devient null

### 🔧 Solution

Implémenter la **même logique temporelle qu'iOS** avec :

- **Protection temporaire** de la détection de suppression
- **Délais coordonnés** entre les services
- **Nettoyage automatique** des données résiduelles
- **Maintien authentification** pendant création nouveau profil

La **clef** est de **NE PAS déconnecter** l'utilisateur pendant qu'un autre service essaie encore de créer son profil !
