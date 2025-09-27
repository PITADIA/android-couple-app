# 🗑️ SUPPRESSION DE COMPTE - Design & Processus

## 🎯 Fonctionnalité

Suppression complète du compte utilisateur via le menu de l'app. Processus en 4 étapes avec gestion partenaire et abonnements.

---

## 📱 Interface Menu

### Bouton Suppression

```swift
ProfileRowView(
    title: isDeleting ? "deleting_account".localized : "delete_account".localized,
    value: "",
    showChevron: false,
    isDestructive: false, // Rouge mais pas destructive style
    action: {
        showingDeleteConfirmation = true
    }
)
```

### Alert de Confirmation

```swift
.alert("delete_account".localized, isPresented: $showingDeleteConfirmation) {
    Button("cancel".localized, role: .cancel) { }
    Button("delete_account".localized, role: .destructive) {
        deleteAccount()
    }
} message: {
    Text("delete_account_confirmation".localized)
}
```

---

## 🔄 Processus de Suppression

### 1. Client iOS (MenuView)

```swift
private func deleteAccount() {
    isDeleting = true // Change le texte du bouton

    AccountDeletionService.shared.deleteAccount { success in
        DispatchQueue.main.async {
            self.isDeleting = false
            if success {
                print("✅ Compte supprimé avec succès")
                self.appState.deleteAccount() // Déconnecte l'utilisateur
            } else {
                print("❌ Erreur lors de la suppression du compte")
            }
        }
    }
}
```

### 2. AccountDeletionService (4 Étapes)

```swift
func deleteAccount(completion: @escaping (Bool) -> Void) {
    // Étape 1: Nettoyage serveur (Cloud Function)
    callServerCleanupFunction(userId: userId) { serverSuccess in

        // Étape 2: Suppression Firestore + codes partenaires
        deleteUserDataFromFirestore(userId: userId) { firestoreSuccess in

            // Étape 3: Vérification suppression
            verifyDataDeletion(userId: userId) { verificationSuccess in

                // Étape 4: Suppression Auth Firebase
                deleteFirebaseAuthAccount(user: currentUser) { authSuccess in
                    let overallSuccess = firestoreSuccess || verificationSuccess
                    completion(overallSuccess)
                }
            }
        }
    }
}
```

---

## 🔥 Firebase Cloud Function

### deleteUserAccount (index.js)

```javascript
exports.deleteUserAccount = functions.https.onCall(async (data, context) => {
  const userId = context.auth.uid;

  // 1. Gestion déconnexion partenaire
  const userDoc = await admin.firestore().collection("users").doc(userId).get();
  const userData = userDoc.data();

  // Si utilisateur a un code ET un partenaire connecté
  if (userData.partnerCode && userData.partnerId) {
    // Déconnecter le partenaire
    await admin.firestore().collection("users").doc(userData.partnerId).update({
      partnerId: admin.firestore.FieldValue.delete(),
      connectedPartnerCode: admin.firestore.FieldValue.delete(),
    });

    // Gérer l'abonnement partagé
    if (
      userData.isSubscribed &&
      connectedPartnerData.subscriptionSharedFrom === userId
    ) {
      // Désactiver l'abonnement hérité du partenaire
      connectedPartnerUpdate.isSubscribed = false;
      connectedPartnerUpdate.subscriptionType =
        admin.firestore.FieldValue.delete();
    }
  }

  // 2. Supprimer codes partenaires
  await admin
    .firestore()
    .collection("partnerCodes")
    .doc(userData.partnerCode)
    .delete();

  // 3. Nettoyer partenaires orphelins (qui étaient connectés à cet utilisateur)
  const orphanedPartners = await admin
    .firestore()
    .collection("users")
    .where("partnerId", "==", userId)
    .get();

  for (const doc of orphanedPartners.docs) {
    await doc.ref.update({
      partnerId: admin.firestore.FieldValue.delete(),
      connectedPartnerCode: admin.firestore.FieldValue.delete(),
      // Désactiver abonnement hérité si applicable
      isSubscribed: false,
      subscriptionType: admin.firestore.FieldValue.delete(),
    });
  }

  // 4. Supprimer document utilisateur
  await admin.firestore().collection("users").doc(userId).delete();

  // 5. Supprimer compte Auth
  await admin.auth().deleteUser(userId);
});
```

---

## 👥 Impact sur les Partenaires

### Déconnexion Automatique

```javascript
// Partenaire connecté direct
await admin.firestore().collection("users").doc(partnerId).update({
  partnerId: admin.firestore.FieldValue.delete(),
  connectedPartnerCode: admin.firestore.FieldValue.delete(),
});
```

### Gestion Abonnement Partagé

```javascript
// Si le partenaire avait un abonnement hérité de l'utilisateur supprimé
if (connectedPartnerData.subscriptionSharedFrom === userId) {
  connectedPartnerUpdate.isSubscribed = false;
  connectedPartnerUpdate.subscriptionType = admin.firestore.FieldValue.delete();
  console.log("🔗 Désactivation abonnement hérité pour partenaire");
}
```

### Nettoyage Codes Partenaires

```swift
// PartnerCodeService.deleteUserPartnerCode()
func deleteUserPartnerCode() async {
    // 1. Supprimer les codes créés par l'utilisateur
    let codeSnapshot = try await db.collection("partnerCodes")
        .whereField("userId", isEqualTo: currentUser.uid)
        .getDocuments()

    for document in codeSnapshot.documents {
        try await document.reference.delete()
    }

    // 2. Libérer les codes où cet utilisateur était connecté
    let connectedCodesSnapshot = try await db.collection("partnerCodes")
        .whereField("connectedPartnerId", isEqualTo: currentUser.uid)
        .getDocuments()

    for document in connectedCodesSnapshot.documents {
        try await document.reference.updateData([
            "connectedPartnerId": NSNull(),
            "connectedAt": FieldValue.delete()
        ])
    }
}
```

---

## 🔑 Clés XCStrings

```xml
<string name="delete_account">Supprimer le compte</string>
<string name="deleting_account">Suppression en cours...</string>
<string name="delete_account_confirmation">Êtes-vous sûr de vouloir supprimer définitivement votre compte ? Cette action est irréversible.</string>
<string name="cancel">Annuler</string>
```

---

## 🤖 Code Android Équivalent

### Interface Menu

```kotlin
@Composable
fun DeleteAccountSection(
    isDeleting: Boolean,
    onDeleteClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    ProfileRowItem(
        title = if (isDeleting) {
            stringResource(R.string.deleting_account)
        } else {
            stringResource(R.string.delete_account)
        },
        onClick = { showDeleteDialog = true },
        isDestructive = true
    )

    if (showDeleteDialog) {
        DeleteAccountDialog(
            onConfirm = {
                showDeleteDialog = false
                onDeleteClick()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
```

### Dialog de Confirmation

```kotlin
@Composable
fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.delete_account))
        },
        text = {
            Text(stringResource(R.string.delete_account_confirmation))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Red
                )
            ) {
                Text(stringResource(R.string.delete_account))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
```

### Service de Suppression Android

```kotlin
class AccountDeletionService(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions
) {

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("No user"))
            val userId = currentUser.uid

            // 1. Appel Cloud Function pour nettoyage serveur
            val serverCleanup = functions
                .getHttpsCallable("deleteUserAccount")
                .call()
                .await()

            // 2. Suppression locale des codes partenaires (backup)
            deletePartnerCodes(userId)

            // 3. Suppression document Firestore (backup)
            firestore.collection("users").document(userId).delete().await()

            // 4. Vérification suppression
            val verification = verifyDataDeletion(userId)

            // 5. Suppression Auth (fait par Cloud Function normalement)
            if (!verification) {
                auth.currentUser?.delete()?.await()
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deletePartnerCodes(userId: String) {
        // Supprimer codes créés par l'utilisateur
        val userCodes = firestore.collection("partnerCodes")
            .whereEqualTo("userId", userId)
            .get()
            .await()

        userCodes.documents.forEach { doc ->
            doc.reference.delete()
        }

        // Libérer codes où utilisateur était connecté
        val connectedCodes = firestore.collection("partnerCodes")
            .whereEqualTo("connectedPartnerId", userId)
            .get()
            .await()

        connectedCodes.documents.forEach { doc ->
            doc.reference.update(
                mapOf(
                    "connectedPartnerId" to FieldValue.delete(),
                    "connectedAt" to FieldValue.delete()
                )
            )
        }
    }

    private suspend fun verifyDataDeletion(userId: String): Boolean {
        return try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            !userDoc.exists() // true si bien supprimé
        } catch (e: Exception) {
            false
        }
    }
}
```

### ViewModel Android

```kotlin
class MenuViewModel(
    private val accountDeletionService: AccountDeletionService,
    private val appState: AppState
) : ViewModel() {

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()

    fun deleteAccount() {
        viewModelScope.launch {
            _isDeleting.value = true

            try {
                val result = accountDeletionService.deleteAccount()

                if (result.isSuccess) {
                    // Déconnexion locale
                    appState.logout()
                    // Navigation vers écran de connexion
                } else {
                    // Afficher erreur
                }
            } catch (e: Exception) {
                // Gestion erreur
            } finally {
                _isDeleting.value = false
            }
        }
    }
}
```

---

## 📋 Résumé Processus

### ✅ Étapes de Suppression

1. **Confirmation utilisateur** → Alert dialog
2. **Cloud Function** → Déconnexion partenaire + nettoyage serveur
3. **Codes partenaires** → Suppression + libération
4. **Abonnements** → Désactivation hérités
5. **Firestore** → Document utilisateur supprimé
6. **Auth** → Compte Firebase supprimé
7. **Déconnexion** → AppState.logout()

### 🔗 Impact Partenaires

- **Déconnexion automatique** du partenaire
- **Perte abonnement partagé** si hérité
- **Codes libérés** pour réutilisation
- **Pas de notification** push

### 🛡️ Sécurité

- **Authentification requise** (context.auth)
- **Suppression irréversible** (confirmation dialog)
- **Nettoyage complet** (orphelins inclus)
- **Vérification backup** côté client
