# Rapport : Système Profil et Paramètres - CoupleApp iOS

## Vue d'ensemble

Ce rapport détaille l'architecture complète du système Profil/Paramètres dans l'application iOS CoupleApp, incluant la gestion du profil utilisateur, l'édition photo/nom, la gestion d'abonnement, la section code partenaire, les CGV, l'intégration Firebase, et les recommandations pour l'adaptation Android.

---

## 🏗️ Architecture Générale du Système

```
┌─────────────────────────────────────────────────────────────────┐
│                    SYSTÈME PROFIL & PARAMÈTRES                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  INTERFACE UTILISATEUR PRINCIPALE                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   MenuView   │  │ EditNameView │  │PartnerMgmtView│          │
│  │- Profil hub  │  │- Édition nom │  │- Code partenaire│        │
│  │- Sections    │  │- Validation  │  │- Connexion   │          │
│  │- Navigation  │  │- Firebase    │  │- Déconnexion │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  ÉDITION PROFIL AVANCÉE                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ SwiftyCrop   │  │UserCacheManager│  │PhotoPermissions│      │
│  │- Crop circulaire│ │- Cache local │  │- Autorisations │      │
│  │- Ajustements │  │- Upload async│  │- Fallbacks    │        │
│  │- Validation  │  │- Performance │  │- Settings     │        │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  GESTION ABONNEMENT & PARTENAIRE                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │SubscriptionView│ │PartnerCodeService│ │FirebaseService│      │
│  │- StoreKit    │  │- Génération  │  │- User update │        │
│  │- Interface   │  │- Validation  │  │- Sync partner│        │
│  │- Restauration│  │- Cloud Func  │  │- Real-time   │        │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  BACKEND FIREBASE                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Firestore  │  │Cloud Functions│  │ Firebase Storage│      │
│  │- users       │  │- generateCode│  │- Profile images │      │
│  │- partnerCodes│  │- connectPartner│ │- Signed URLs   │      │
│  │- subscriptions│ │- syncData    │  │- CDN optimized │      │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘

FLUX UTILISATEUR:
1. Utilisateur → Onglet Profil (MenuView)
2. Photo → Permission → SwiftyCrop → Cache → Firebase upload
3. Nom → EditNameView → Validation → Firebase update
4. Partenaire → PartnerManagementView → Code → Cloud Function
5. Abonnement → SubscriptionView → StoreKit → Validation
6. CGV/Légal → URLs externes Apple/Privacy
```

---

## 🖼️ 1. MenuView - Interface Profil Principale

### 1.1 Structure et Sections

**Localisation :** `Views/Main/MenuView.swift:40-164`

```swift
struct MenuView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    var onLocationTutorialTap: (() -> Void)?
    var onWidgetsTap: (() -> Void)?

    @State private var showingDeleteConfirmation = false
    @State private var isDeleting = false

    @State private var profileImage: UIImage?
    @State private var showingPartnerCode = false
    @State private var showingNameEdit = false
    @State private var showingRelationshipEdit = false
    @State private var showingLocationTutorial = false
    @State private var showingWidgets = false

    var body: some View {
        ScrollView {
            VStack(spacing: 30) {
                // 🔑 HEADER AVEC PHOTO DE PROFIL
                headerSection

                // 🔑 SECTION "À PROPOS DE MOI"
                aboutMeSection

                // 🔑 LIGNE SÉPARATRICE
                separatorLine

                // 🔑 SECTION "APPLICATION"
                applicationSection
            }
            .padding(.top, 20)
        }
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
        .ignoresSafeArea()
    }
}
```

### 1.2 Header avec Photo de Profil Interactive

**Localisation :** `Views/Main/MenuView.swift:169-241`

```swift
@ViewBuilder
private var headerSection: some View {
    VStack(spacing: 16) {
        // 🔑 PHOTO DE PROFIL CLIQUABLE
        Button(action: {
            checkPhotoLibraryPermission() // ✅ Même comportement que l'onboarding
        }) {
            ZStack {
                // Effet de surbrillance (identique à PartnerDistanceView)
                Circle()
                    .fill(Color.white.opacity(0.35))
                    .frame(width: 120 + 12, height: 120 + 12)
                    .blur(radius: 6)

                if let croppedImage = croppedImage {
                    // 🔑 PRIORITÉ À L'IMAGE CROPPÉE RÉCEMMENT
                    Image(uiImage: croppedImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: 120, height: 120)
                        .clipShape(Circle())
                } else if let cachedImage = UserCacheManager.shared.getCachedProfileImage() {
                    // 🔑 PRIORITÉ AU CACHE LOCAL POUR AFFICHAGE INSTANTANÉ
                    Image(uiImage: cachedImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: 120, height: 120)
                        .clipShape(Circle())
                } else if let imageURL = currentUserImageURL {
                    // 🔑 FALLBACK VERS URL FIREBASE
                    AsyncImageView(
                        imageURL: imageURL,
                        width: 120,
                        height: 120,
                        cornerRadius: 60
                    )
                } else {
                    // 🔑 FALLBACK INITIALES
                    Circle()
                        .fill(LinearGradient(
                            gradient: Gradient(colors: [
                                Color(hex: "#FD267A"),
                                Color(hex: "#FF655B")
                            ]),
                            startPoint: .leading,
                            endPoint: .trailing
                        ))
                        .frame(width: 120, height: 120)
                        .overlay(
                            Text(currentUserInitials)
                                .font(.system(size: 40, weight: .bold))
                                .foregroundColor(.white)
                        )
                }
            }
        }
        .buttonStyle(PlainButtonStyle())

        // 🔑 NOM UTILISATEUR
        Text(currentUserName)
            .font(.system(size: 24, weight: .semibold))
            .foregroundColor(.black)
    }
}
```

### 1.3 Section "À propos de moi"

**Localisation :** `Views/Main/MenuView.swift:245-327`

```swift
@ViewBuilder
private var aboutMeSection: some View {
    VStack(spacing: 0) {
        // Titre "À propos de moi"
        HStack {
            Text("about_me".localized)
                .font(.system(size: 22, weight: .semibold))
                .foregroundColor(.black)
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 20)

        // 🔑 NOM (ÉDITABLE)
        ProfileRowView(
            title: "name".localized,
            value: currentUserName,
            showChevron: true,
            action: {
                showingNameEdit = true
            }
        )

        // 🔑 EN COUPLE DEPUIS (ÉDITABLE)
        ProfileRowView(
            title: "in_relationship_since".localized,
            value: currentRelationshipStart,
            showChevron: true,
            action: {
                showingRelationshipEdit = true
            }
        )

        // 🔑 CODE PARTENAIRE
        ProfileRowView(
            title: "partner_code".localized,
            value: "",
            showChevron: true,
            action: {
                showingPartnerCode = true
            }
        )

        // 🔑 TUTORIEL LOCALISATION
        ProfileRowView(
            title: "location_tutorial".localized,
            value: "",
            showChevron: true,
            action: {
                if let onLocationTutorialTap = onLocationTutorialTap {
                    onLocationTutorialTap()
                } else {
                    showingLocationTutorial = true
                }
            }
        )

        // 🔑 WIDGETS
        ProfileRowView(
            title: "widgets".localized,
            value: "",
            showChevron: true,
            action: {
                if let onWidgetsTap = onWidgetsTap {
                    onWidgetsTap()
                } else {
                    showingWidgets = true
                }
            }
        )

        // 🔑 GÉRER SON ABONNEMENT
        ProfileRowView(
            title: "manage_subscription".localized,
            value: "",
            showChevron: true,
            action: {
                openSubscriptionSettings()
            }
        )
    }
    .padding(.bottom, 30)
}
```

### 1.4 Section Application et CGV

**Localisation :** `Views/Main/MenuView.swift:345-399`

```swift
@ViewBuilder
private var applicationSection: some View {
    VStack(spacing: 0) {
        // Titre "Application"
        HStack {
            Text("application".localized)
                .font(.system(size: 22, weight: .semibold))
                .foregroundColor(.black)
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 20)

        // 🔑 CONTACTEZ-NOUS
        ProfileRowView(
            title: "contact_us".localized,
            value: "",
            showChevron: true,
            action: {
                openSupportEmail()
            }
        )

        // 🔑 CGV (CONDITIONS GÉNÉRALES DE VENTE)
        ProfileRowView(
            title: "terms_conditions".localized,
            value: "",
            showChevron: true,
            action: {
                if let url = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/") {
                    UIApplication.shared.open(url)
                }
            }
        )

        // 🔑 POLITIQUE DE CONFIDENTIALITÉ
        ProfileRowView(
            title: "privacy_policy".localized,
            value: "",
            showChevron: true,
            action: {
                let privacyUrl = Locale.preferredLanguages.first?.hasPrefix("fr") == true
                    ? "https://love2lovesite.onrender.com"
                    : "https://love2lovesite.onrender.com/privacy-policy.html"

                if let url = URL(string: privacyUrl) {
                    UIApplication.shared.open(url)
                }
            }
        )

        // 🔑 SUPPRIMER LE COMPTE
        ProfileRowView(
            title: "delete_account".localized,
            value: "",
            showChevron: true,
            textColor: .red,
            action: {
                showingDeleteConfirmation = true
            }
        )
    }
}
```

---

## 📝 2. Édition Profil - Nom et Photo

### 2.1 EditNameView - Modification du Nom

**Localisation :** `Views/Main/MenuView.swift:693-747`

```swift
struct EditNameView: View {
    let currentName: String
    let onSave: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var newName: String

    init(currentName: String, onSave: @escaping (String) -> Void) {
        self.currentName = currentName
        self.onSave = onSave
        self._newName = State(initialValue: currentName)
    }

    var body: some View {
        VStack(spacing: 20) {
            // 🔑 CHAMP DE TEXTE
            TextField("Votre nom", text: $newName)
                .font(.system(size: 16))
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                )

            // 🔑 BOUTON ENREGISTRER
            Button(action: {
                onSave(newName)
                dismiss()
            }) {
                Text("save".localized)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(
                        LinearGradient(
                            gradient: Gradient(colors: [
                                Color(hex: "#FD267A"),
                                Color(hex: "#FF655B")
                            ]),
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(25)
            }
            .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .opacity(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.6 : 1.0)
        }
        .padding(24)
        .background(Color.white)
    }
}
```

### 2.2 SwiftyCrop Integration - Édition Photo

**Localisation :** `Views/Main/MenuView.swift:64-90`

```swift
.fullScreenCover(isPresented: $showImageCropper) {
    if let imageToProcess = selectedImage {
        SwiftyCropView(
            imageToCrop: imageToProcess,
            maskShape: .circle,
            configuration: SwiftyCropConfiguration(
                maxMagnificationScale: 4.0,
                maskRadius: 150,
                cropImageCircular: true,
                rotateImage: false,
                rotateImageWithButtons: false,
                zoomSensitivity: 1.0,
                texts: SwiftyCropConfiguration.Texts(
                    cancelButton: "Annuler",
                    interactionInstructions: "Ajustez votre photo de profil",
                    saveButton: "Valider"
                )
            )
        ) { resultImage in
            guard let finalImage = resultImage else {
                self.showImageCropper = false
                return
            }
            // 🔑 TRAITEMENT RÉSULTAT CROP
            self.croppedImage = finalImage
            self.profileImage = finalImage
            self.showImageCropper = false
        }
    }
}
```

### 2.3 Gestion Upload Photo avec Cache Local

**Localisation :** `Views/Main/MenuView.swift:471-507`

```swift
private func uploadProfileImage(_ image: UIImage) {
    guard appState.currentUser != nil else { return }

    // 🔑 NOUVELLE APPROCHE: Cache local immédiat + upload silencieux

    // 1. Mettre immédiatement l'image en cache pour affichage instantané
    UserCacheManager.shared.cacheProfileImage(image)

    // 2. Nettoyer les états temporaires pour forcer l'utilisation du cache
    self.croppedImage = nil
    self.profileImage = nil

    print("✅ MenuView: Image mise en cache, affichage immédiat")

    // 3. Démarrer l'upload Firebase en arrière-plan (sans callback UI)
    Task {
        await uploadToFirebaseInBackground(image)
    }
}

/// Upload silencieux en arrière-plan sans affecter l'UI
private func uploadToFirebaseInBackground(_ image: UIImage) async {
    print("🔄 MenuView: Début upload Firebase en arrière-plan")

    // 🔑 UPLOAD SANS CALLBACK UI
    FirebaseService.shared.updateProfileImage(image) { success, imageURL in
        if success {
            print("✅ MenuView: Upload Firebase terminé avec succès en arrière-plan")
            // Pas de mise à jour UI - le cache local reste la source de vérité
        } else {
            print("❌ MenuView: Upload Firebase échoué en arrière-plan - retry plus tard")
            // TODO: Optionnel - retry automatique ou notification discrète
        }
    }
}
```

### 2.4 Mise à jour Nom avec Firebase

**Localisation :** `Views/Main/MenuView.swift:509-526`

```swift
private func updateUserName(_ newName: String) {
    guard let currentUser = appState.currentUser else { return }

    // 🔑 METTRE À JOUR LOCALEMENT D'ABORD
    var updatedUser = currentUser
    updatedUser.name = newName
    appState.currentUser = updatedUser

    // 🔑 SAUVEGARDER DANS FIREBASE
    FirebaseService.shared.updateUserName(newName) { success in
        if !success {
            // Rollback en cas d'erreur
            DispatchQueue.main.async {
                self.appState.currentUser = currentUser
            }
        }
    }
}
```

---

## 🤝 3. Gestion Partenaire - PartnerManagementView

### 3.1 Interface Gestion Partenaire

**Localisation :** `Views/Settings/PartnerManagementView.swift:13-35`

```swift
struct PartnerManagementView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var partnerCodeService = PartnerCodeService.shared
    @Environment(\.dismiss) private var dismiss

    @State private var enteredCode = ""
    @State private var showingDisconnectAlert = false
    @FocusState private var isCodeFieldFocused: Bool
    @State private var keyboardHeight: CGFloat = 0

    var body: some View {
        NavigationView {
            ZStack {
                // 🔑 FOND DÉGRADÉ ROSE CLAIR
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FFE5F1"),
                        Color(hex: "#FFF0F8")
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()

                // 🔑 SCROLLVIEW POUR CLAVIER
                ScrollView {
                    VStack {
                        // Padding adaptatif selon clavier
                        Spacer()
                            .frame(height: max(50, (UIScreen.main.bounds.height - keyboardHeight) * 0.15))

                        VStack(spacing: 40) {
                            // Contenu principal
                        }
                    }
                }
            }
        }
    }
}
```

### 3.2 PartnerCodeService - Génération et Validation

**Localisation :** `Services/PartnerCodeService.swift:30-50`

```swift
func generatePartnerCode() async -> String? {
    print("🔗 PartnerCodeService: Début génération code")

    guard let currentUser = Auth.auth().currentUser else {
        print("❌ PartnerCodeService: Utilisateur non connecté")
        await MainActor.run {
            self.errorMessage = NSLocalizedString("user_not_connected", comment: "User not connected error")
        }
        return nil
    }

    await MainActor.run {
        self.isLoading = true
        self.errorMessage = nil
    }

    // 🔑 CONFORMITÉ APPLE : Vérifier si code récent < 24h existe
    do {
        print("🔗 PartnerCodeService: Vérification code récent (< 24h)...")

        let recentCodeQuery = db.collection("partnerCodes")
            .whereField("userId", isEqualTo: currentUser.uid)
            .whereField("expiresAt", isGreaterThan: Date())
            .order(by: "createdAt", descending: true)
            .limit(to: 1)

        let recentSnapshot = try await recentCodeQuery.getDocuments()

        if let recentCode = recentSnapshot.documents.first?.data()["code"] as? String {
            print("🔗 PartnerCodeService: Code récent trouvé, réutilisation")

            await MainActor.run {
                self.generatedCode = recentCode
                self.isLoading = false
            }
            return recentCode
        }

        // Génération nouveau code via Cloud Function
        let result = try await functions.httpsCallable("generatePartnerCode").call()

        if let data = result.data as? [String: Any],
           let success = data["success"] as? Bool,
           success,
           let code = data["code"] as? String {

            await MainActor.run {
                self.generatedCode = code
                self.isLoading = false
            }
            return code
        }

    } catch {
        print("❌ PartnerCodeService: Erreur génération: \(error)")
        await MainActor.run {
            self.isLoading = false
            self.errorMessage = "Erreur lors de la génération du code"
        }
    }

    return nil
}
```

### 3.3 Connexion Partenaire avec Cloud Function

**Localisation :** `firebase/functions/index.js:1196-1372`

```javascript
exports.connectToPartner = functions.https.onCall(async (data, context) => {
  try {
    // 🔑 VÉRIFICATIONS SÉCURITÉ
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Utilisateur non authentifié"
      );
    }

    const { partnerCode } = data;
    const userId = context.auth.uid;

    if (!partnerCode) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Code partenaire requis"
      );
    }

    // 🔑 VÉRIFIER CODE VALIDE
    const codeDoc = await admin
      .firestore()
      .collection("partnerCodes")
      .doc(partnerCode)
      .get();

    if (!codeDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Code partenaire non trouvé ou expiré"
      );
    }

    const codeData = codeDoc.data();

    // 🔑 VÉRIFICATIONS BUSINESS
    if (codeData.userId === userId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Vous ne pouvez pas utiliser votre propre code"
      );
    }

    if (codeData.connectedPartnerId) {
      throw new functions.https.HttpsError(
        "already-exists",
        "Ce code est déjà utilisé par un autre partenaire"
      );
    }

    // 🔑 VÉRIFIER UTILISATEUR PAS DÉJÀ CONNECTÉ
    const userDoc = await admin
      .firestore()
      .collection("users")
      .doc(userId)
      .get();

    const userData = userDoc.data();
    if (userData.connectedPartnerCode) {
      throw new functions.https.HttpsError(
        "already-exists",
        "Vous êtes déjà connecté à un partenaire"
      );
    }

    // 🔑 RÉCUPÉRER DONNÉES PARTENAIRE
    const partnerDoc = await admin
      .firestore()
      .collection("users")
      .doc(codeData.userId)
      .get();

    const partnerData = partnerDoc.data();

    // 🔑 VÉRIFIER ABONNEMENT PARTENAIRE
    const partnerSubscription = partnerData.subscription || {};
    const hasActiveSubscription = partnerSubscription.isSubscribed === true;

    // 🔑 EFFECTUER CONNEXION BATCH
    const batch = admin.firestore().batch();

    // Mettre à jour le code partenaire
    batch.update(
      admin.firestore().collection("partnerCodes").doc(partnerCode),
      {
        connectedPartnerId: userId,
        connectedAt: admin.firestore.FieldValue.serverTimestamp(),
      }
    );

    // Mettre à jour l'utilisateur
    const userUpdate = {
      connectedPartnerCode: partnerCode,
      connectedPartnerId: codeData.userId,
      connectedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    // 🔑 HÉRITAGE ABONNEMENT SI PARTENAIRE PREMIUM
    if (hasActiveSubscription) {
      userUpdate.subscription = {
        ...partnerSubscription,
        inheritedFrom: codeData.userId,
        inheritedAt: admin.firestore.FieldValue.serverTimestamp(),
      };
    }

    batch.update(admin.firestore().collection("users").doc(userId), userUpdate);

    // 🔑 METTRE À JOUR PARTENAIRE AUSSI
    batch.update(admin.firestore().collection("users").doc(codeData.userId), {
      connectedPartnerId: userId,
      connectedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await batch.commit();

    return {
      success: true,
      partnerName: partnerData.name || "Partenaire",
      subscriptionInherited: hasActiveSubscription,
      message: "Connexion réussie avec votre partenaire",
    };
  } catch (error) {
    console.error("❌ connectToPartner error:", error);
    throw error;
  }
});
```

---

## 💳 4. Gestion Abonnement - SubscriptionView

### 4.1 Interface Abonnement

**Localisation :** `Views/Subscription/SubscriptionView.swift:14-40`

```swift
struct SubscriptionView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var receiptService = AppleReceiptService.shared
    @StateObject private var pricingService = StoreKitPricingService.shared
    @Environment(\.dismiss) private var dismiss
    @State private var showingAppleSignIn = false
    @State private var showingSuccessMessage = false
    @State private var purchaseCompleted = false

    var body: some View {
        ZStack {
            // 🔑 FOND COHÉRENT AVEC ONBOARDING
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // 🔑 HEADER AVEC CROIX FERMETURE
                HStack {
                    Button(action: {
                        print("🔥 SubscriptionView: Fermeture via croix")
                        appState.freemiumManager?.dismissSubscription()
                        dismiss()
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.black)
                    }
                    .padding(.leading, 20)

                    Spacer()
                }
                .padding(.top, 10)

                // 🔑 CONTENU PRINCIPAL ABONNEMENT
                // ... (interface StoreKit avec prix, fonctionnalités, etc.)
            }
        }
    }
}
```

### 4.2 Ouverture Paramètres Abonnement iOS

**Localisation :** `Views/Main/MenuView.swift:656-687`

```swift
private func openSubscriptionSettings() {
    // 🔑 OUVRIR PARAMÈTRES ABONNEMENT iOS NATIFS
    if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
        UIApplication.shared.open(url) { success in
            if !success {
                // Fallback vers paramètres généraux
                if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(settingsUrl)
                }
            }
        }
    }

    // 📊 Analytics: Paramètres abonnement ouverts
    Analytics.logEvent("subscription_settings_opened", parameters: [:])
    print("📊 Événement Firebase: subscription_settings_opened")
}
```

---

## 🌍 5. Localisation - Clés XCStrings Profil

### 5.1 Clés Interface Profil

**Localisation :** `UI.xcstrings:12264-12316`

```json
{
  "manage_subscription": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": { "state": "translated", "value": "Gérer son abonnement" }
      },
      "en": {
        "stringUnit": { "state": "translated", "value": "Manage subscription" }
      },
      "de": {
        "stringUnit": { "state": "translated", "value": "Abo verwalten" }
      },
      "es": {
        "stringUnit": {
          "state": "translated",
          "value": "Gestionar suscripción"
        }
      },
      "it": {
        "stringUnit": { "state": "translated", "value": "Gestisci abbonamento" }
      },
      "nl": {
        "stringUnit": { "state": "translated", "value": "Abonnement beheren" }
      },
      "pt-BR": {
        "stringUnit": { "state": "translated", "value": "Gerenciar assinatura" }
      },
      "pt-PT": {
        "stringUnit": { "state": "translated", "value": "Gerir subscrição" }
      }
    }
  },

  "settings": {
    "extractionState": "manual",
    "localizations": {
      "fr": { "stringUnit": { "state": "translated", "value": "Paramètres" } },
      "en": { "stringUnit": { "state": "translated", "value": "Settings" } },
      "de": {
        "stringUnit": { "state": "translated", "value": "Einstellungen" }
      },
      "es": { "stringUnit": { "state": "translated", "value": "Ajustes" } },
      "it": {
        "stringUnit": { "state": "translated", "value": "Impostazioni" }
      },
      "nl": {
        "stringUnit": { "state": "translated", "value": "Instellingen" }
      },
      "pt-BR": {
        "stringUnit": { "state": "translated", "value": "Configurações" }
      },
      "pt-PT": {
        "stringUnit": { "state": "translated", "value": "Definições" }
      }
    }
  },

  "about_me": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": { "state": "translated", "value": "À propos de moi" }
      },
      "en": { "stringUnit": { "state": "translated", "value": "About me" } },
      "de": { "stringUnit": { "state": "translated", "value": "Über mich" } },
      "es": {
        "stringUnit": { "state": "translated", "value": "Acerca de mí" }
      },
      "it": { "stringUnit": { "state": "translated", "value": "Chi sono" } },
      "nl": { "stringUnit": { "state": "translated", "value": "Over mij" } },
      "pt-BR": {
        "stringUnit": { "state": "translated", "value": "Sobre mim" }
      },
      "pt-PT": { "stringUnit": { "state": "translated", "value": "Sobre mim" } }
    }
  },

  "partner_code": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": { "state": "translated", "value": "Code partenaire" }
      },
      "en": {
        "stringUnit": { "state": "translated", "value": "Partner code" }
      },
      "de": {
        "stringUnit": { "state": "translated", "value": "Partner-Code" }
      },
      "es": {
        "stringUnit": { "state": "translated", "value": "Código de pareja" }
      },
      "it": {
        "stringUnit": { "state": "translated", "value": "Codice partner" }
      },
      "nl": {
        "stringUnit": { "state": "translated", "value": "Partner code" }
      },
      "pt-BR": {
        "stringUnit": { "state": "translated", "value": "Código do parceiro" }
      },
      "pt-PT": {
        "stringUnit": { "state": "translated", "value": "Código do parceiro" }
      }
    }
  },

  "terms_conditions": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": { "state": "translated", "value": "Conditions Générales" }
      },
      "en": {
        "stringUnit": { "state": "translated", "value": "Terms & Conditions" }
      },
      "de": { "stringUnit": { "state": "translated", "value": "AGB" } },
      "es": {
        "stringUnit": {
          "state": "translated",
          "value": "Términos y Condiciones"
        }
      },
      "it": {
        "stringUnit": { "state": "translated", "value": "Termini e Condizioni" }
      },
      "nl": { "stringUnit": { "state": "translated", "value": "Voorwaarden" } },
      "pt-BR": {
        "stringUnit": { "state": "translated", "value": "Termos e Condições" }
      },
      "pt-PT": {
        "stringUnit": { "state": "translated", "value": "Termos e Condições" }
      }
    }
  },

  "privacy_policy": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": {
          "state": "translated",
          "value": "Politique de confidentialité"
        }
      },
      "en": {
        "stringUnit": { "state": "translated", "value": "Privacy Policy" }
      },
      "de": { "stringUnit": { "state": "translated", "value": "Datenschutz" } },
      "es": {
        "stringUnit": {
          "state": "translated",
          "value": "Política de Privacidad"
        }
      },
      "it": {
        "stringUnit": { "state": "translated", "value": "Privacy Policy" }
      },
      "nl": {
        "stringUnit": { "state": "translated", "value": "Privacybeleid" }
      },
      "pt-BR": {
        "stringUnit": {
          "state": "translated",
          "value": "Política de Privacidade"
        }
      },
      "pt-PT": {
        "stringUnit": {
          "state": "translated",
          "value": "Política de Privacidade"
        }
      }
    }
  },

  "delete_account": {
    "extractionState": "manual",
    "localizations": {
      "fr": {
        "stringUnit": { "state": "translated", "value": "Supprimer le compte" }
      },
      "en": {
        "stringUnit": { "state": "translated", "value": "Delete account" }
      },
      "de": {
        "stringUnit": { "state": "translated", "value": "Konto löschen" }
      },
      "es": {
        "stringUnit": { "state": "translated", "value": "Eliminar cuenta" }
      },
      "it": {
        "stringUnit": { "state": "translated", "value": "Elimina account" }
      },
      "nl": {
        "stringUnit": { "state": "translated", "value": "Account verwijderen" }
      },
      "pt-BR": {
        "stringUnit": { "state": "translated", "value": "Excluir conta" }
      },
      "pt-PT": {
        "stringUnit": { "state": "translated", "value": "Eliminar conta" }
      }
    }
  }
}
```

---

## 🤖 6. Adaptation Android - Architecture Kotlin/Compose

### 6.1 Modèles de Données Android

```kotlin
// UserProfile.kt
data class UserProfile(
    val id: String,
    val name: String,
    val email: String? = null,
    val imageURL: String? = null,
    val relationshipStartDate: Date? = null,
    val partnerId: String? = null,
    val isSubscribed: Boolean = false,
    val subscription: SubscriptionData? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {

    companion object {
        fun fromFirestore(document: DocumentSnapshot): UserProfile? {
            return try {
                val data = document.data ?: return null

                UserProfile(
                    id = document.id,
                    name = data["name"] as? String ?: "",
                    email = data["email"] as? String,
                    imageURL = data["imageURL"] as? String,
                    relationshipStartDate = (data["relationshipStartDate"] as? com.google.firebase.Timestamp)?.toDate(),
                    partnerId = data["partnerId"] as? String,
                    isSubscribed = data["isSubscribed"] as? Boolean ?: false,
                    subscription = (data["subscription"] as? Map<String, Any>)?.let {
                        SubscriptionData.fromMap(it)
                    },
                    createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                    updatedAt = (data["updatedAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
                )
            } catch (e: Exception) {
                Log.e("UserProfile", "Erreur parsing: ${e.message}")
                null
            }
        }
    }

    val initials: String
        get() = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }
            .take(2).joinToString("").uppercase()

    val hasPartner: Boolean
        get() = !partnerId.isNullOrEmpty()
}

// SubscriptionData.kt
data class SubscriptionData(
    val isSubscribed: Boolean = false,
    val productId: String? = null,
    val purchaseDate: Date? = null,
    val expiryDate: Date? = null,
    val isInheritedFromPartner: Boolean = false,
    val inheritedFrom: String? = null
) {

    companion object {
        fun fromMap(map: Map<String, Any>): SubscriptionData {
            return SubscriptionData(
                isSubscribed = map["isSubscribed"] as? Boolean ?: false,
                productId = map["productId"] as? String,
                purchaseDate = (map["purchaseDate"] as? com.google.firebase.Timestamp)?.toDate(),
                expiryDate = (map["expiryDate"] as? com.google.firebase.Timestamp)?.toDate(),
                isInheritedFromPartner = map["inheritedFrom"] != null,
                inheritedFrom = map["inheritedFrom"] as? String
            )
        }
    }
}
```

### 6.2 ProfileRepository Android

```kotlin
@Singleton
class ProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService
) {

    companion object {
        private const val TAG = "ProfileRepository"
        private const val COLLECTION_USERS = "users"
        private const val STORAGE_PROFILE_IMAGES = "profile_images"
    }

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var userListener: ListenerRegistration? = null

    // MARK: - User Profile Management

    fun initializeForUser(userId: String) {
        Log.d(TAG, "Initialisation profil pour utilisateur: $userId")
        setupUserListener(userId)
    }

    private fun setupUserListener(userId: String) {
        userListener?.remove()

        userListener = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erreur listener profil: ${error.message}")
                    return@addSnapshotListener
                }

                val user = snapshot?.let { UserProfile.fromFirestore(it) }
                _currentUser.value = user

                Log.d(TAG, "Profil utilisateur mis à jour: ${user?.name}")
            }
    }

    // MARK: - Profile Updates

    suspend fun updateUserName(newName: String): Result<Unit> {
        return try {
            _isLoading.value = true

            val currentUser = authRepository.getCurrentUser()
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            // 🔑 METTRE À JOUR FIRESTORE
            firestore.collection(COLLECTION_USERS)
                .document(currentUser.uid)
                .update(
                    mapOf(
                        "name" to newName.trim(),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            // 📊 Analytics
            analyticsService.logEvent("profile_name_updated") {
                param("name_length", newName.length.toLong())
            }

            _isLoading.value = false
            Log.d(TAG, "Nom utilisateur mis à jour avec succès")

            Result.success(Unit)

        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Erreur mise à jour nom: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateProfileImage(imageUri: Uri): Result<String> {
        return try {
            _isLoading.value = true

            val currentUser = authRepository.getCurrentUser()
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            // 🔑 UPLOAD IMAGE VERS FIREBASE STORAGE
            val imageRef = storage.reference
                .child("$STORAGE_PROFILE_IMAGES/${currentUser.uid}/${UUID.randomUUID()}.jpg")

            val uploadTask = imageRef.putFile(imageUri).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()

            // 🔑 METTRE À JOUR FIRESTORE AVEC NOUVELLE URL
            firestore.collection(COLLECTION_USERS)
                .document(currentUser.uid)
                .update(
                    mapOf(
                        "imageURL" to downloadUrl.toString(),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            // 📊 Analytics
            analyticsService.logEvent("profile_image_updated")

            _isLoading.value = false
            Log.d(TAG, "Image de profil mise à jour avec succès")

            Result.success(downloadUrl.toString())

        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Erreur mise à jour image: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateRelationshipStartDate(date: Date): Result<Unit> {
        return try {
            val currentUser = authRepository.getCurrentUser()
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            firestore.collection(COLLECTION_USERS)
                .document(currentUser.uid)
                .update(
                    mapOf(
                        "relationshipStartDate" to com.google.firebase.Timestamp(date),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )
                .await()

            Log.d(TAG, "Date de relation mise à jour avec succès")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur mise à jour date relation: ${e.message}")
            Result.failure(e)
        }
    }

    // MARK: - Account Management

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val currentUser = authRepository.getCurrentUser()
                ?: return Result.failure(Exception("Utilisateur non connecté"))

            // Supprimer les données Firestore
            firestore.collection(COLLECTION_USERS)
                .document(currentUser.uid)
                .delete()
                .await()

            // Supprimer l'authentification Firebase
            currentUser.delete().await()

            analyticsService.logEvent("account_deleted")

            Log.d(TAG, "Compte supprimé avec succès")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur suppression compte: ${e.message}")
            Result.failure(e)
        }
    }

    fun cleanup() {
        userListener?.remove()
    }
}
```

### 6.3 Interface Android - ProfileScreen Compose

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToPartnerManagement: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToWidgets: () -> Unit,
    onNavigateToLocationTutorial: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F8)),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        // 🔑 HEADER AVEC PHOTO DE PROFIL
        item {
            ProfileHeader(
                user = currentUser,
                onPhotoClick = { viewModel.showPhotoSelector() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 🔑 SECTION "À PROPOS DE MOI"
        item {
            ProfileSection(
                title = stringResource(R.string.about_me),
                items = listOf(
                    ProfileItem(
                        title = stringResource(R.string.name),
                        value = currentUser?.name ?: "",
                        onClick = { viewModel.showNameEditor() }
                    ),
                    ProfileItem(
                        title = stringResource(R.string.in_relationship_since),
                        value = currentUser?.relationshipStartDate?.let {
                            SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(it)
                        } ?: "",
                        onClick = { viewModel.showRelationshipDatePicker() }
                    ),
                    ProfileItem(
                        title = stringResource(R.string.partner_code),
                        value = "",
                        onClick = onNavigateToPartnerManagement
                    ),
                    ProfileItem(
                        title = stringResource(R.string.location_tutorial),
                        value = "",
                        onClick = onNavigateToLocationTutorial
                    ),
                    ProfileItem(
                        title = stringResource(R.string.widgets),
                        value = "",
                        onClick = onNavigateToWidgets
                    ),
                    ProfileItem(
                        title = stringResource(R.string.manage_subscription),
                        value = "",
                        onClick = { viewModel.openSubscriptionSettings(context) }
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 🔑 SECTION "APPLICATION"
        item {
            ProfileSection(
                title = stringResource(R.string.application),
                items = listOf(
                    ProfileItem(
                        title = stringResource(R.string.contact_us),
                        value = "",
                        onClick = { viewModel.openSupportEmail(context) }
                    ),
                    ProfileItem(
                        title = stringResource(R.string.terms_conditions),
                        value = "",
                        onClick = { viewModel.openTermsAndConditions(context) }
                    ),
                    ProfileItem(
                        title = stringResource(R.string.privacy_policy),
                        value = "",
                        onClick = { viewModel.openPrivacyPolicy(context) }
                    ),
                    ProfileItem(
                        title = stringResource(R.string.delete_account),
                        value = "",
                        textColor = Color.Red,
                        onClick = { viewModel.showDeleteConfirmation() }
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 🔑 DIALOGS ET SHEETS
    ProfileDialogs(
        uiState = uiState,
        onDismiss = { viewModel.dismissDialog() },
        onConfirmNameChange = { newName -> viewModel.updateName(newName) },
        onConfirmDateChange = { newDate -> viewModel.updateRelationshipDate(newDate) },
        onConfirmDelete = { viewModel.deleteAccount() }
    )
}

@Composable
fun ProfileHeader(
    user: UserProfile?,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 🔑 PHOTO DE PROFIL CLIQUABLE
        Box {
            // Effet de surbrillance
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .background(
                        Color.White.copy(alpha = 0.35f),
                        CircleShape
                    )
                    .blur(6.dp)
            )

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clickable { onPhotoClick() }
            ) {
                if (user?.imageURL != null) {
                    AsyncImage(
                        model = user.imageURL,
                        contentDescription = "Photo de profil",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 🔑 INITIALES AVEC GRADIENT
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFF6B9D),
                                        Color(0xFFE63C6B)
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user?.initials ?: "",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 🔑 NOM UTILISATEUR
        Text(
            text = user?.name ?: "",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
    }
}

@Composable
fun ProfileSection(
    title: String,
    items: List<ProfileItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Titre section
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        )

        // Items
        items.forEach { item ->
            ProfileRowItem(
                item = item,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

data class ProfileItem(
    val title: String,
    val value: String,
    val textColor: Color = Color.Black,
    val onClick: () -> Unit
)

@Composable
fun ProfileRowItem(
    item: ProfileItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .clickable { item.onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = item.textColor
                )

                if (item.value.isNotEmpty()) {
                    Text(
                        text = item.value,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

### 6.4 Édition Photo Android avec UCrop

```kotlin
@Composable
fun ProfileImageEditor(
    onImageSelected: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    var showImagePicker by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // Launcher pour sélection image
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            imageUri = it
            startCropActivity(context, it, onImageSelected)
        }
    }

    // Permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            imagePickerLauncher.launch("image/*")
        } else {
            // Gérer refus permission
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

private fun startCropActivity(
    context: Context,
    sourceUri: Uri,
    onResult: (Uri) -> Unit
) {
    val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))

    val cropIntent = UCrop.of(sourceUri, destinationUri)
        .withAspectRatio(1f, 1f)
        .withMaxResultSize(512, 512)
        .withOptions(UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(false)
            setShowCropGrid(false)
            setStatusBarColor(ContextCompat.getColor(context, R.color.colorPrimary))
            setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))
        })
        .getIntent(context)

    // Lancer crop activity avec result
    (context as Activity).startActivityForResult(cropIntent, UCrop.REQUEST_CROP)
}
```

---

## 📋 Conclusion

Le système Profil/Paramètres de CoupleApp présente une architecture complète et professionnelle :

### 🎯 **Points Forts Système Profil :**

- **Interface unifiée** : MenuView centralise toutes les fonctionnalités profil
- **Édition photo avancée** : SwiftyCrop + cache local + upload asynchrone Firebase
- **Gestion partenaire sophistiquée** : Code temporaire + validation + héritage abonnement
- **Abonnement natif iOS** : Intégration StoreKit + ouverture paramètres système
- **CGV et légal** : Liens externes Apple + politique confidentialité multilingue

### 🔧 **Composants Techniques iOS :**

- `MenuView` - Hub central profil avec sections organisées
- `EditNameView` - Modal édition nom avec validation
- `SwiftyCrop` - Crop circulaire photo profil avec ajustements
- `PartnerCodeService` - Génération codes + validation + connexion
- `UserCacheManager` - Cache local images pour performance

### 🔥 **Firebase Integration Sécurisée :**

- **Cloud Functions** : `generatePartnerCode`, `connectToPartner` avec validations
- **Collection users** : Profils avec chiffrement + sync real-time
- **Storage sécurisé** : Images profil avec URLs signées
- **Batch operations** : Connexions partenaire atomiques

### 🖼️ **Édition Photo Sophistiquée :**

- **Cache local prioritaire** : Affichage instantané UserCacheManager
- **Upload asynchrone** : Firebase en arrière-plan sans bloquer UI
- **SwiftyCrop integration** : Crop circulaire + zoom + ajustements
- **Fallbacks intelligents** : Cache → URL Firebase → Initiales gradient

### 🤝 **Gestion Partenaire Avancée :**

- **Codes temporaires** : 24h validité + réutilisation
- **Validation sécurisée** : Cloud Functions + checks business
- **Héritage abonnement** : Premium partenaire → Utilisateur gratuit
- **Connexion atomique** : Batch Firestore + rollback erreurs

### 🌍 **Localisation Complète - 8 langues**

- **Interface profil** : `about_me`, `manage_subscription`, `partner_code`
- **Légal** : `terms_conditions`, `privacy_policy`, `delete_account`
- **Actions** : `save`, `cancel`, `settings`, `open_settings`
- **Multilingue** : FR, EN, DE, ES, IT, NL, PT-BR, PT-PT

### 🤖 **Architecture Android Robuste :**

- **ProfileRepository** : StateFlow + Firebase listeners + CRUD complet
- **Compose UI moderne** : Material Design 3 + sections organisées
- **UCrop integration** : Crop photo circulaire Android natif
- **Google Play Billing** : Gestion abonnements Android

### ⚡ **Fonctionnalités Avancées :**

- **Profile Header** : Photo cliquable + nom + effet surbrillance
- **Sections organisées** : "À propos", "Application" avec navigation
- **CGV intelligents** : URLs Apple + confidentialité selon langue
- **Suppression compte** : Confirmation + nettoyage complet données

### 📊 **Métriques Business :**

- **Engagement profil** : Taux édition photo/nom
- **Connexions partenaire** : Codes générés vs validés
- **Abonnements** : Taux ouverture paramètres iOS
- **Rétention** : Impact profil complet sur usage app

### ⏱️ **Estimation Android : 8-12 semaines**

- Phase 1 : ProfileRepository + Models (2-3 sem)
- Phase 2 : Interface Compose + Sections (3-4 sem)
- Phase 3 : UCrop + Photo editing (2-3 sem)
- Phase 4 : Play Billing + Partner codes (2-3 sem)
- Phase 5 : Tests + Polish (1-2 sem)

Le système Profil/Paramètres représente le **centre de contrôle utilisateur** avec une **UX professionnelle** et des **fonctionnalités avancées** (crop photo, codes partenaire, abonnements). L'architecture **modulaire et sécurisée** facilite la maintenance et l'évolution, positionnant CoupleApp comme une **application premium de qualité** ! 👤💕🚀

Cette base solide permet d'ajouter facilement futures fonctionnalités profil comme thèmes personnalisés, badges achievements, ou intégrations sociales.
