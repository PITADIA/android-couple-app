import SwiftUI
import CoreLocation
import AuthenticationServices
import PhotosUI
import Photos
import SwiftyCrop
import FirebaseAnalytics

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
    
    @State private var editedName = ""
    @State private var editedRelationshipStart = ""
    
    // MARK: - Gestion des autorisations photos (comme ProfilePhotoStepView)
    @State private var showingGalleryPicker = false        // Picker standard (accès complet)
    @State private var showingLimitedGalleryView = false   // Interface personnalisée (accès limité)
    @State private var showSettingsAlert = false          // Alerte paramètres
    @State private var limitedPhotoAssets: [PHAsset] = [] // Photos autorisées
    @State private var alertMessage = ""
    
    // MARK: - SwiftyCrop states
    @State private var selectedImage: UIImage?            // Image sélectionnée avant crop
    @State private var croppedImage: UIImage?            // Image après crop
    @State private var showImageCropper = false          // Contrôle SwiftyCrop

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // Header avec photo de profil
                headerSection
                
                // Section "À propos de moi"
                aboutMeSection
                
                // Trait de séparation
                separatorLine
                
                // Section Application
                applicationSection
                
                Spacer(minLength: 40)
            }
        }
        .sheet(isPresented: $showingGalleryPicker) {
            StandardGalleryPicker(onImageSelected: handleImageSelection)
        }
        .sheet(isPresented: $showingLimitedGalleryView) {
            LimitedGalleryView(assets: limitedPhotoAssets, onImageSelected: handleImageSelection)
        }
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
                    self.croppedImage = finalImage
                    self.profileImage = finalImage
                    self.showImageCropper = false
                }
            } else {
                // Afficher une vue d'erreur au lieu d'un écran blanc
                VStack {
                    Text("error_image_not_found".localized)
                        .font(.title)
                        .foregroundColor(.red)
                    
                    Button("close".localized) {
                        self.showImageCropper = false
                    }
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.blue)
                    .cornerRadius(10)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)

            }
        }
        .alert(isPresented: $showSettingsAlert) {
            Alert(
                title: Text("authorization_required".localized),
                message: Text(alertMessage),
                primaryButton: .default(Text("open_settings_button".localized)) {
                    openSettings()
                },
                secondaryButton: .cancel(Text("cancel".localized))
            )
        }
        .onChange(of: profileImage) { _, newImage in
            if let image = newImage {
                uploadProfileImage(image)
            }
        }
        .alert("delete_account".localized, isPresented: $showingDeleteConfirmation) {
            Button("cancel".localized, role: .cancel) { }
            Button("delete_account".localized, role: .destructive) {
                deleteAccount()
            }
        } message: {
            Text("delete_account_confirmation".localized)
        }

        .sheet(isPresented: $showingPartnerCode) {
            PartnerManagementView()
                .environmentObject(appState)
        }
        .sheet(isPresented: $showingNameEdit) {
            EditNameView(currentName: currentUserName, onSave: { newName in
                updateUserName(newName)
            })
            .presentationDetents([.height(200)])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showingRelationshipEdit) {
            EditRelationshipView(currentDate: currentRelationshipStart, onSave: { newDate in
                updateRelationshipStart(newDate)
            })
            .presentationDetents([.height(350)])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showingLocationTutorial) {
            LocationPermissionFlow()
        }
        .sheet(isPresented: $showingWidgets) {
            WidgetsView()
                .environmentObject(appState)
        }
        .onAppear {
            editedName = currentUserName
            editedRelationshipStart = currentRelationshipStart
        }
    }
    
    // MARK: - Header Section
    
    @ViewBuilder
    private var headerSection: some View {
        VStack(spacing: 16) {
            // Photo de profil cliquable
                Button(action: {
                    checkPhotoLibraryPermission() // ✅ Même comportement que l'onboarding
                }) {
                ZStack {
                    // Léger effet de surbrillance autour (identique à PartnerDistanceView)
                    Circle()
                        .fill(Color.white.opacity(0.35))
                        .frame(width: 120 + 12, height: 120 + 12)
                        .blur(radius: 6)
                    
                    if let croppedImage = croppedImage {
                        // Priorité à l'image croppée récemment
                        Image(uiImage: croppedImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 120, height: 120)
                            .clipShape(Circle())
                    } else if let cachedImage = UserCacheManager.shared.getCachedProfileImage() {
                        // 🚀 NOUVEAU: Priorité à l'image en cache pour affichage instantané
                        Image(uiImage: cachedImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 120, height: 120)
                            .clipShape(Circle())
                    } else if let imageURL = currentUserImageURL {
                        AsyncImageView(
                            imageURL: imageURL,
                            width: 120,
                            height: 120,
                            cornerRadius: 60
                        )
                        .onAppear {
                            // Télécharger et mettre en cache l'image si elle n'est pas déjà en cache
                            downloadAndCacheProfileImageIfNeeded(from: imageURL)
                        }
                    } else if let profileImage = profileImage {
                        Image(uiImage: profileImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 120, height: 120)
                            .clipShape(Circle())
                    } else {
                        // Afficher les initiales avec fond coloré si pas d'image
                        if !currentUserName.isEmpty && currentUserName != "Non défini" {
                            UserInitialsView(name: currentUserName, size: 120)
                        } else {
                            // Fallback vers l'icône grise si pas de nom
                            Circle()
                                .fill(Color.gray.opacity(0.3))
                                .frame(width: 120, height: 120)
                                .overlay(
                                    Image(systemName: "person.fill")
                                        .font(.system(size: 50))
                                        .foregroundColor(.gray)
                                )
                        }
                    }
                    
                    // Bordure blanche (identique à PartnerDistanceView)
                    Circle()
                        .stroke(Color.white, lineWidth: 3)
                        .frame(width: 120, height: 120)
                }
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.top, 120)
        .padding(.bottom, 50)
    }
    
    // MARK: - About Me Section
    
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
            
            // Nom
            ProfileRowView(
                title: "name".localized,
                value: currentUserName,
                showChevron: true,
                action: {
                    showingNameEdit = true
                }
            )
            
            // En couple depuis
            ProfileRowView(
                title: "in_relationship_since".localized,
                value: currentRelationshipStart,
                showChevron: true,
                action: {
                    showingRelationshipEdit = true
                }
            )
            
            // Code partenaire
            ProfileRowView(
                title: "partner_code".localized,
                value: "",
                showChevron: true,
                action: {
                    showingPartnerCode = true
                }
            )
            
            // Tutoriel de localisation
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
            

            
            // Widgets
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
            
            // Gérer son abonnement
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
    
    // MARK: - Separator
    
         @ViewBuilder
     private var separatorLine: some View {
         Rectangle()
             .fill(Color.gray.opacity(0.3))
             .frame(height: 1)
             .padding(.horizontal, 20)
             .padding(.bottom, 30)
     }
    
    // MARK: - Application Section
    
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
            
            // Contactez-nous
            ProfileRowView(
                title: "contact_us".localized,
                value: "",
                showChevron: true,
                action: {
                    openSupportEmail()
                }
            )
            
            // CGV
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
            

            
            // Politique de confidentialité
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
            
            // Supprimer le compte
            ProfileRowView(
                title: isDeleting ? "deleting_account".localized : "delete_account".localized,
                value: "",
                showChevron: false,
                isDestructive: false,
                action: {
                    showingDeleteConfirmation = true
                }
            )
        }
        .padding(.bottom, 40)
    }
    
    // MARK: - Computed Properties
    
    private var currentUserImageURL: String? {
        guard let imageURL = appState.currentUser?.profileImageURL,
              !imageURL.isEmpty else { return nil }
        return imageURL
    }
    
    private var currentUserName: String {
        let name = appState.currentUser?.name ?? ""
        if name.isEmpty || name == "Non défini" {
            // Utiliser localisation pour fallback
            let locale = Locale.current
            let languageCode: String
            if #available(iOS 16.0, *) {
                languageCode = locale.language.languageCode?.identifier ?? "en"
            } else {
                languageCode = locale.languageCode ?? "en"
            }
            
            return languageCode.hasPrefix("fr") ? "Utilisateur" : "User"
        }
        return name
    }
    
    private var currentRelationshipStart: String {
        guard let user = appState.currentUser,
              let startDate = user.relationshipStartDate else {
            return "Non défini"
        }
        
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale.current
        return formatter.string(from: startDate)
    }
    
    private var hasConnectedPartner: Bool {
        appState.currentUser?.partnerId != nil
    }
    
    private var formattedRelationshipDate: String {
        guard let date = appState.currentUser?.relationshipStartDate else { return "" }
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale.current
        return formatter.string(from: date)
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale.current
        return formatter.string(from: date)
    }
    
    // MARK: - Actions
    
    // L'upload vers Firebase se fait maintenant via onChange de profileImage
    
    private func uploadProfileImage(_ image: UIImage) {
        guard appState.currentUser != nil else {
            return
        }
        
        // 🚀 NOUVELLE APPROCHE: Cache local immédiat + upload silencieux
        
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
        
        // Upload sans callback UI - juste pour synchroniser Firebase/Firestore
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
    
    private func updateUserName(_ newName: String) {
        guard let currentUser = appState.currentUser else { return }
        
        // Mettre à jour localement
        var updatedUser = currentUser
        updatedUser.name = newName
        appState.currentUser = updatedUser
        
        // Sauvegarder dans Firebase
                 FirebaseService.shared.updateUserName(newName) { success in
             if !success {
                 // Rollback en cas d'erreur
                 DispatchQueue.main.async {
                     self.appState.currentUser = currentUser
                 }
             }
         }
    }
    
    /// Télécharge et met en cache l'image de profil si nécessaire
    private func downloadAndCacheProfileImageIfNeeded(from url: String) {
        // Ne pas retélécharger si déjà en cache (UserCacheManager)
        if UserCacheManager.shared.hasCachedProfileImage() {
            print("🖼️ MenuView: Image déjà en cache, pas de téléchargement nécessaire")
            return
        }

        print("🖼️ MenuView: Téléchargement image de profil pour mise en cache (async)")

        guard let imageURL = URL(string: url) else {
            print("❌ MenuView: URL invalide pour image de profil")
            return
        }

        let request = URLRequest(url: imageURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                print("❌ MenuView: Erreur téléchargement image: \(error.localizedDescription)")
                return
            }
            guard let data = data, let image = UIImage(data: data) else {
                print("❌ MenuView: Données image invalides")
                return
            }
            DispatchQueue.main.async {
                UserCacheManager.shared.cacheProfileImage(image)
                print("✅ MenuView: Image de profil mise en cache (URLSession)")
            }
        }.resume()
    }
    
    private func updateRelationshipStart(_ newDateString: String) {
        // Convertir la string en Date (format reçu du DatePicker)
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale.current
        
        guard let date = formatter.date(from: newDateString) else {
            return
        }
        
        // Mettre à jour localement d'abord
        if var currentUser = appState.currentUser {
            currentUser.relationshipStartDate = date
            appState.currentUser = currentUser
        }
        
                 // Sauvegarder dans Firebase
         FirebaseService.shared.updateRelationshipStartDate(date) { success in
             DispatchQueue.main.async {
                 if !success {
                     // Rollback en cas d'erreur
                     if let originalUser = self.appState.currentUser {
                         var rollbackUser = originalUser
                         rollbackUser.relationshipStartDate = nil // ou valeur précédente
                         self.appState.currentUser = rollbackUser
                     }
                 }
             }
         }
    }
    

    
    private func openSupportEmail() {
        print("🔥 MenuView: Ouverture de l'email de support")
        if let url = URL(string: "mailto:contact@love2loveapp.com") {
            UIApplication.shared.open(url)
        }
    }
    

    
    private func openSubscriptionSettings() {
        print("🔥 MenuView: Ouverture des réglages d'abonnement")
        // URL pour ouvrir directement les réglages d'abonnement dans l'app Réglages
        if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
            UIApplication.shared.open(url)
        }
    }
    
         private func deleteAccount() {
         isDeleting = true
         
         AccountDeletionService.shared.deleteAccount { success in
             DispatchQueue.main.async {
                 self.isDeleting = false
                 if success {
                     print("✅ Compte supprimé avec succès")
                     self.appState.deleteAccount()
                 } else { 
                     print("❌ Erreur lors de la suppression du compte")
                 }
             }
         }
     }
}

// MARK: - Profile Row Component

struct ProfileRowView: View {
    let title: String
    let value: String
    let showChevron: Bool
    let isDestructive: Bool
    let icon: String?
    let action: () -> Void
    
    init(
        title: String,
        value: String,
        showChevron: Bool = false,
        isDestructive: Bool = false,
        icon: String? = nil,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.value = value
        self.showChevron = showChevron
        self.isDestructive = isDestructive
        self.icon = icon
        self.action = action
    }
    
    var body: some View {
        Button(action: action) {
            HStack {
                // Icône optionnelle
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: 16))
                        .foregroundColor(Color(hex: "#FD267A"))
                        .frame(width: 20)
                }
                
                Text(title)
                    .font(.system(size: 16))
                    .foregroundColor(isDestructive ? .red : .black)
                
                Spacer()
                
                if !value.isEmpty {
                    Text(value)
                        .font(.system(size: 16))
                        .foregroundColor(.gray)
                }
                
                if showChevron {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Edit Views

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
            // Champ de texte
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
            
            // Bouton Enregistrer
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

struct EditRelationshipView: View {
    let currentDate: String
    let onSave: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selectedDate = Date()
    
    init(currentDate: String, onSave: @escaping (String) -> Void) {
        self.currentDate = currentDate
        self.onSave = onSave
        
        // Initialiser la date sélectionnée avec la date actuelle
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale.current
        if let date = formatter.date(from: currentDate) {
            self._selectedDate = State(initialValue: date)
        } else {
            self._selectedDate = State(initialValue: Date())
        }
    }
    
    var body: some View {
        VStack(spacing: 20) {
            // Sélecteur de date
            DatePicker(
                "",
                selection: $selectedDate,
                displayedComponents: .date
            )
            .datePickerStyle(WheelDatePickerStyle())
            .labelsHidden()
            .environment(\.locale, Locale.current)
            
            // Bouton Enregistrer
            Button(action: {
                let formatter = DateFormatter()
                formatter.dateStyle = .long
                formatter.locale = Locale.current
                onSave(formatter.string(from: selectedDate))
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
        }
        .padding(24)
        .background(Color.white)
    }
}

// MARK: - Gestion des autorisations photos (copié de ProfilePhotoStepView)

extension MenuView {
    private func checkPhotoLibraryPermission() {
        print("🔐 MenuView: Vérification des autorisations de la photothèque")
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        print("📱 MenuView: Statut actuel: \(status.toString())")
        
        switch status {
        case .authorized:
            // ✅ ACCÈS COMPLET
            print("✅ MenuView: Accès complet déjà autorisé")
            showingGalleryPicker = true
            
        case .limited:
            // ✅ ACCÈS LIMITÉ - Charger les photos autorisées
            print("🔍 MenuView: Accès limité détecté")
            loadLimitedAssets { success in
                DispatchQueue.main.async {
                    if success {
                        self.showingLimitedGalleryView = true
                    } else {
                        // Fallback vers picker standard si échec
                        self.showingGalleryPicker = true
                    }
                }
            }
            
        case .notDetermined:
            // ⏳ PREMIÈRE DEMANDE
            print("⏳ MenuView: Première demande d'autorisation")
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                DispatchQueue.main.async {
                    print("📱 MenuView: Nouveau statut: \(newStatus.toString())")
                    // Récursion pour traiter le nouveau statut
                    self.checkPhotoLibraryPermission()
                }
            }
            
        case .denied, .restricted:
            // ❌ ACCÈS REFUSÉ - Proposer d'aller aux paramètres
            print("❌ MenuView: Accès refusé")
            alertMessage = "photo_access_denied_message_menu".localized
            showSettingsAlert = true
            
        @unknown default:
            print("❓ MenuView: Statut inconnu")
            alertMessage = "photo_access_error_generic".localized
            showSettingsAlert = true
        }
    }
    
    private func loadLimitedAssets(completion: @escaping (Bool) -> Void) {
        print("📸 MenuView: Chargement des photos autorisées...")
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        
        // ✅ MAGIE iOS : Cette ligne ne retourne QUE les photos autorisées
        let allPhotos = PHAsset.fetchAssets(with: .image, options: fetchOptions)
        print("📸 MenuView: Nombre de photos accessibles: \(allPhotos.count)")
        
        limitedPhotoAssets = []
        
        if allPhotos.count > 0 {
            for i in 0..<allPhotos.count {
                let asset = allPhotos.object(at: i)
                limitedPhotoAssets.append(asset)
            }
            completion(true)
        } else {
            print("❌ MenuView: Aucune photo accessible")
            completion(false)
        }
    }
    
    private func handleImageSelection(_ imageData: UIImage) {
        print("✅ MenuView: Image sélectionnée, ouverture du cropper")
        print("🔥🔥🔥 MENU_SWIFTYCROP: handleImageSelection appelé")
        print("🔥🔥🔥 MENU_SWIFTYCROP: Image reçue, size = \(imageData.size)")
        
        selectedImage = imageData
        
        // Fermer la sheet de sélection
        showingGalleryPicker = false
        showingLimitedGalleryView = false
        
        print("🔥🔥🔥 MENU_SWIFTYCROP: Avant d'activer showImageCropper")
        
        // Petit délai pour s'assurer que les sheets sont fermées
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            print("🔥🔥🔥 MENU_SWIFTYCROP: Activation de showImageCropper...")
            self.showImageCropper = true
            print("🔥🔥🔥 MENU_SWIFTYCROP: showImageCropper activé = \(self.showImageCropper)")
        }
    }
    
    private func openSettings() {
        // ✅ Stratégie officielle et fiable : ouvrir la page Réglages de l'app
        // et s'assurer que la section Localisation apparaît en amont.

        // 1) Si services de localisation globaux désactivés, informer l'utilisateur
        if !CLLocationManager.locationServicesEnabled() {
            openGeneralSettings()
            return
        }

        // 2) Pré-demande si jamais l'autorisation n'a jamais été demandée
        let auth = CLLocationManager().authorizationStatus
        if auth == .notDetermined {
            let manager = CLLocationManager()
            manager.requestWhenInUseAuthorization()
            // Attendre un court délai pour que l'app soit listée dans Réglages > Confidentialité
            let delay: TimeInterval
            if #available(iOS 18.0, *) { delay = 0.6 } else { delay = 1.2 }
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                self.openGeneralSettings()
            }
            return
        }

        // 3) Ouvrir les réglages de l'app (page officielle et stable)
        openGeneralSettings()
    }
    
    private func openGeneralSettings() {
        // Fallback sûr : Paramètres généraux de l'app
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

#Preview {
    MenuView()
        .environmentObject(AppState())
} 