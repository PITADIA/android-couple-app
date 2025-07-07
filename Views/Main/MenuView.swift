import SwiftUI
import AuthenticationServices
import PhotosUI
import Photos
import SwiftyCrop

struct MenuView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var showingDeleteConfirmation = false
    @State private var isDeleting = false

    @State private var profileImage: UIImage?
    @State private var showingPartnerCode = false
    @State private var showingNameEdit = false
    @State private var showingRelationshipEdit = false

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
                    print("🔥🔥🔥 MENU_SWIFTYCROP: Callback appelé avec image croppée")
                    guard let finalImage = resultImage else {
                        print("❌ MENU_SWIFTYCROP: L'image croppée est nil")
                        self.showImageCropper = false
                        return
                    }
                    print("🔥🔥🔥 MENU_SWIFTYCROP: Cropped image size = \(finalImage.size)")
                    self.croppedImage = finalImage
                    self.profileImage = finalImage
                    print("🔥🔥🔥 MENU_SWIFTYCROP: Fermeture du cropper...")
                    self.showImageCropper = false
                }
                .onAppear {
                    print("🔥🔥🔥 MENU_SWIFTYCROP: SwiftyCropView.onAppear() appelé")
                }
                .onDisappear {
                    print("🔥🔥🔥 MENU_SWIFTYCROP: SwiftyCropView.onDisappear() appelé")
                }
            } else {
                // Afficher une vue d'erreur au lieu d'un écran blanc
                VStack {
                    Text("Erreur: Image non trouvée")
                        .font(.title)
                        .foregroundColor(.red)
                    
                    Button("Fermer") {
                        print("🔥🔥🔥 MENU_SWIFTYCROP: Fermeture forcée du cropper")
                        self.showImageCropper = false
                    }
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.blue)
                    .cornerRadius(10)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)
                .onAppear {
                    print("❌❌❌ MENU_SWIFTYCROP: ERREUR - selectedImage est nil!")
                }
            }
        }
        .alert(isPresented: $showSettingsAlert) {
            Alert(
                title: Text("Autorisation requise"),
                message: Text(alertMessage),
                primaryButton: .default(Text("Ouvrir les paramètres")) {
                    openSettings()
                },
                secondaryButton: .cancel(Text("Annuler"))
            )
        }
        .onChange(of: profileImage) { _, newImage in
            if let image = newImage {
                print("🔥 MenuView: Nouvelle image sélectionnée, démarrage upload")
                uploadProfileImage(image)
            }
        }
        .onChange(of: showImageCropper) { _, newValue in
            print("🔥🔥🔥 MENU_SWIFTYCROP: onChange showImageCropper = \(newValue)")
        }
        .onChange(of: selectedImage) { _, newImage in
            print("🔥🔥🔥 MENU_SWIFTYCROP: onChange selectedImage = \(newImage != nil)")
            if let image = newImage {
                print("🔥🔥🔥 MENU_SWIFTYCROP: Nouvelle image sélectionnée, size = \(image.size)")
            }
        }
        .onChange(of: croppedImage) { _, newImage in
            print("🔥🔥🔥 MENU_SWIFTYCROP: onChange croppedImage = \(newImage != nil)")
            if let image = newImage {
                print("🔥🔥🔥 MENU_SWIFTYCROP: Nouvelle image croppée, size = \(image.size)")
            }
        }
        .alert("Supprimer le compte", isPresented: $showingDeleteConfirmation) {
            Button("Annuler", role: .cancel) { }
            Button("Supprimer définitivement", role: .destructive) {
                deleteAccount()
            }
        } message: {
            Text("Cette action est irréversible. Toutes vos données seront supprimées définitivement.\n\nApple pourrait vous demander de vous ré-authentifier pour confirmer cette action.")
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
                    Circle()
                        .fill(Color.gray.opacity(0.1))
                        .frame(width: 120, height: 120)
                    
                    if let croppedImage = croppedImage {
                        // Priorité à l'image croppée récemment
                        Image(uiImage: croppedImage)
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
                    } else if let profileImage = profileImage {
                        Image(uiImage: profileImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 120, height: 120)
                            .clipShape(Circle())
                    } else {
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
                 Text("À propos de moi")
                     .font(.system(size: 22, weight: .semibold))
                     .foregroundColor(.black)
                 Spacer()
             }
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
            
            // Nom
            ProfileRowView(
                title: "Nom",
                value: currentUserName,
                showChevron: true,
                action: {
                    showingNameEdit = true
                }
            )
            
            // En couple depuis
            ProfileRowView(
                title: "En couple depuis",
                value: currentRelationshipStart,
                showChevron: true,
                action: {
                    showingRelationshipEdit = true
                }
            )
            
            // Code partenaire
            ProfileRowView(
                title: "Code partenaire",
                value: "",
                showChevron: true,
                action: {
                    showingPartnerCode = true
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
                 Text("Application")
                     .font(.system(size: 22, weight: .semibold))
                     .foregroundColor(.black)
                 Spacer()
             }
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
            
            // Contactez-nous
            ProfileRowView(
                title: "Contactez-nous",
                value: "",
                showChevron: true,
                action: {
                    openSupportEmail()
                }
            )
            
            // Aidez-nous à améliorer l'app
            ProfileRowView(
                title: "Une recommandation ?",
                value: "",
                showChevron: true,
                action: {
                    openSupportEmail()
                }
            )
            
            // CGV
            ProfileRowView(
                title: "Conditions générales d'utilisation",
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
                title: "Politique de confidentialité",
                value: "",
                showChevron: true,
                action: {
                    if let url = URL(string: "https://love2lovesite.onrender.com") {
                        UIApplication.shared.open(url)
                    }
                }
            )
            
            // Supprimer le compte (en rouge)
            ProfileRowView(
                title: isDeleting ? "Suppression en cours..." : "Supprimer le compte",
                value: "",
                showChevron: false,
                isDestructive: true,
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
        appState.currentUser?.name ?? "Non défini"
    }
    
    private var currentRelationshipStart: String {
        guard let user = appState.currentUser,
              let startDate = user.relationshipStartDate else {
            return "Non défini"
        }
        
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale(identifier: "fr_FR")
        return formatter.string(from: startDate)
    }
    
    private var hasConnectedPartner: Bool {
        appState.currentUser?.partnerId != nil
    }
    
    // MARK: - Actions
    
    // L'upload vers Firebase se fait maintenant via onChange de profileImage
    
    private func uploadProfileImage(_ image: UIImage) {
        print("🔥 MenuView: uploadProfileImage appelé - Taille image: \(image.size)")
        
        guard let currentUser = appState.currentUser else {
            print("❌ MenuView: Aucun utilisateur connecté pour l'upload")
            return
        }
        
        print("🔥 MenuView: Utilisateur trouvé: \(currentUser.name) (ID: \(currentUser.id))")
        
        // Afficher l'image temporairement
        profileImage = image
        print("🔥 MenuView: Image temporaire mise à jour dans l'interface")
        
        // Utiliser la nouvelle méthode dédiée à l'upload d'image de profil
        print("🔥 MenuView: Appel updateProfileImage pour upload image")
        FirebaseService.shared.updateProfileImage(image) { [weak appState] success, imageURL in
            DispatchQueue.main.async {
                print("🔥 MenuView: Callback updateProfileImage - Success: \(success)")
                print("🔥 MenuView: URL image: \(imageURL ?? "nil")")
                
                if success {
                    print("✅ MenuView: Image de profil mise à jour avec succès")
                    // L'utilisateur sera mis à jour automatiquement dans FirebaseService
                } else {
                    print("❌ MenuView: Erreur lors de la mise à jour de l'image")
                    // Réinitialiser l'image temporaire en cas d'erreur
                    self.profileImage = nil
                }
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
                 print("❌ Erreur lors de la mise à jour du nom")
                 // Rollback en cas d'erreur
                 DispatchQueue.main.async {
                     self.appState.currentUser = currentUser
                 }
             }
         }
    }
    
    private func updateRelationshipStart(_ newDateString: String) {
        // Convertir la string en Date (format reçu du DatePicker)
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale(identifier: "fr_FR")
        
        guard let date = formatter.date(from: newDateString) else {
            print("❌ Erreur conversion date: \(newDateString)")
            return
        }
        
        print("🔥 Mise à jour date relation: \(date)")
        
        // Mettre à jour localement d'abord
        if var currentUser = appState.currentUser {
            currentUser.relationshipStartDate = date
            appState.currentUser = currentUser
        }
        
                 // Sauvegarder dans Firebase
         FirebaseService.shared.updateRelationshipStartDate(date) { success in
             DispatchQueue.main.async {
                 if !success {
                     print("❌ Erreur lors de la mise à jour de la date de relation")
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
                Text("Enregistrer")
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
        formatter.locale = Locale(identifier: "fr_FR")
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
            .environment(\.locale, Locale(identifier: "fr_FR"))
            
            // Bouton Enregistrer
            Button(action: {
                let formatter = DateFormatter()
                formatter.dateStyle = .long
                formatter.locale = Locale(identifier: "fr_FR")
                onSave(formatter.string(from: selectedDate))
                dismiss()
            }) {
                Text("Enregistrer")
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
            alertMessage = "L'accès à votre galerie est nécessaire pour changer votre photo de profil. Veuillez l'activer dans les paramètres de votre appareil."
            showSettingsAlert = true
            
        @unknown default:
            print("❓ MenuView: Statut inconnu")
            alertMessage = "Erreur d'accès à la galerie"
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
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

#Preview {
    MenuView()
        .environmentObject(AppState())
} 