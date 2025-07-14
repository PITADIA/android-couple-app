import SwiftUI
import PhotosUI
import Photos
import SwiftyCrop

struct ProfilePhotoStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var selectedImage: UIImage?
    @State private var croppedImage: UIImage?
    @State private var showingGalleryPicker = false        // Picker standard (accès complet)
    @State private var showingLimitedGalleryView = false   // Interface personnalisée (accès limité)
    @State private var showImageCropper = false           // SwiftyCrop
    @State private var showSettingsAlert = false          // Alerte paramètres
    @State private var limitedPhotoAssets: [PHAsset] = [] // Photos autorisées
    @State private var alertMessage = ""
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("add_profile_photo".localized)
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Premier Spacer pour centrer le contenu
            Spacer()
            
            // Contenu principal centré
            VStack(spacing: 50) {
                // Photo de profil cliquable avec gestion des permissions
                Button(action: {
                    checkPhotoLibraryPermission()
                }) {
                    ZStack {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 160, height: 160)
                            .shadow(color: Color.black.opacity(0.1), radius: 15, x: 0, y: 8)
                        
                        if let croppedImage = croppedImage {
                            Image(uiImage: croppedImage)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 160, height: 160)
                                .clipShape(Circle())
                        } else {
                            VStack(spacing: 8) {
                                Image(systemName: "person.fill")
                                    .font(.system(size: 40))
                                    .foregroundColor(.black.opacity(0.3))
                                
                                Text("add_photo".localized)
                                    .font(.system(size: 14))
                                    .foregroundColor(.black.opacity(0.6))
                            }
                        }
                    }
                }
                .buttonStyle(PlainButtonStyle())
            }
            
            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer()
                
            // Zone blanche collée en bas
            VStack(spacing: 15) {
                Button(action: {
                    if let image = croppedImage {
                        viewModel.profileImage = image
                    }
                    viewModel.nextStep()
                }) {
                                            Text("continue".localized)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color(hex: "#FD267A"))
                        .cornerRadius(28)
                }
                .padding(.horizontal, 30)
                
                // Bouton "Passer cette étape" maintenant en dessous du bouton Continuer
                Button(action: {
                    viewModel.nextStep()
                }) {
                    Text("skip_step".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.6))
                        .underline()
                }
            }
            .padding(.vertical, 30)
            .background(Color.white)
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
                                            cancelButton: "cancel".localized,
                interactionInstructions: "crop_photo_instructions".localized,
                saveButton: "validate".localized
                        )
                    )
                ) { resultImage in
                    print("🔥🔥🔥 SWIFTYCROP: Callback appelé avec image croppée (optionnel ?: \(resultImage != nil))")
                    guard let finalImage = resultImage else {
                        print("❌ SWIFTYCROP: L'image croppée est nil")
                        self.showImageCropper = false
                        return
                    }
                    print("🔥🔥🔥 SWIFTYCROP: Cropped image size = \(finalImage.size)")
                    self.croppedImage = finalImage
                    self.viewModel.profileImage = finalImage
                    print("🔥🔥🔥 SWIFTYCROP: Fermeture du cropper...")
                    self.showImageCropper = false
                }
                .onAppear {
                    print("🔥🔥🔥 SWIFTYCROP: fullScreenCover est en train de s'afficher")
                    print("🔥🔥🔥 SWIFTYCROP: showImageCropper = \(showImageCropper)")
                    print("🔥🔥🔥 SWIFTYCROP: selectedImage existe = \(selectedImage != nil)")
                    if let img = selectedImage {
                        print("🔥🔥🔥 SWIFTYCROP: Image size = \(img.size)")
                    }
                    print("🔥🔥🔥 SWIFTYCROP: SwiftyCropView.onAppear() appelé")
                }
                .onDisappear {
                    print("🔥🔥🔥 SWIFTYCROP: SwiftyCropView.onDisappear() appelé")
                }
            } else {
                // Afficher une vue d'erreur au lieu d'un écran blanc
                VStack {
                    Text("error_image_not_found".localized)
                        .font(.title)
                        .foregroundColor(.red)
                    
                    Button("close".localized) {
                        print("🔥🔥🔥 SWIFTYCROP: Fermeture forcée du cropper")
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
                    print("❌❌❌ SWIFTYCROP: ERREUR - selectedImage est nil!")
                }
            }
        }
        .alert(isPresented: $showSettingsAlert) {
            Alert(
                                    title: Text(NSLocalizedString("authorization_required", comment: "Authorization required title")),
                message: Text(alertMessage),
                primaryButton: .default(Text(NSLocalizedString("open_settings_button", comment: "Open settings button"))) {
                    openSettings()
                },
                                    secondaryButton: .cancel(Text(NSLocalizedString("cancel", comment: "Cancel button")))
            )
        }
        .onChange(of: showImageCropper) { _, newValue in
            print("🔥🔥🔥 SWIFTYCROP: onChange showImageCropper = \(newValue)")
        }
        .onChange(of: selectedImage) { _, newImage in
            print("🔥🔥🔥 SWIFTYCROP: onChange selectedImage = \(newImage != nil)")
            if let image = newImage {
                print("🔥🔥🔥 SWIFTYCROP: Nouvelle image sélectionnée, size = \(image.size)")
            }
        }
    }
    
    // MARK: - Gestion des permissions selon le guide
    
    private func checkPhotoLibraryPermission() {
        print("🔐 ProfilePhoto: Vérification des autorisations de la photothèque")
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        print("📱 ProfilePhoto: Statut actuel: \(status.toString())")
        
        switch status {
        case .authorized:
            // ✅ ACCÈS COMPLET
            print("✅ ProfilePhoto: Accès complet déjà autorisé")
            showingGalleryPicker = true
            
        case .limited:
            // ✅ ACCÈS LIMITÉ - Charger les photos autorisées
            print("🔍 ProfilePhoto: Accès limité détecté")
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
            print("⏳ ProfilePhoto: Première demande d'autorisation")
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                DispatchQueue.main.async {
                    print("📱 ProfilePhoto: Nouveau statut: \(newStatus.toString())")
                    // Récursion pour traiter le nouveau statut
                    self.checkPhotoLibraryPermission()
                }
            }
            
        case .denied, .restricted:
            // ❌ ACCÈS REFUSÉ - Proposer d'aller aux paramètres
            print("❌ ProfilePhoto: Accès refusé")
            alertMessage = "photo_access_denied_message".localized
            showSettingsAlert = true
            
        @unknown default:
            print("❓ ProfilePhoto: Statut inconnu")
            alertMessage = "photo_access_error_generic".localized
            showSettingsAlert = true
        }
    }
    
    private func loadLimitedAssets(completion: @escaping (Bool) -> Void) {
        print("📸 ProfilePhoto: Chargement des photos autorisées...")
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        
        // ✅ MAGIE iOS : Cette ligne ne retourne QUE les photos autorisées
        let allPhotos = PHAsset.fetchAssets(with: .image, options: fetchOptions)
        print("📸 ProfilePhoto: Nombre de photos accessibles: \(allPhotos.count)")
        
        limitedPhotoAssets = []
        
        if allPhotos.count > 0 {
            for i in 0..<allPhotos.count {
                let asset = allPhotos.object(at: i)
                limitedPhotoAssets.append(asset)
            }
            completion(true)
        } else {
            print("❌ ProfilePhoto: Aucune photo accessible")
            completion(false)
        }
    }
    
    private func handleImageSelection(_ imageData: UIImage) {
        print("✅ ProfilePhoto: Image sélectionnée, ouverture du cropper")
        print("🔥🔥🔥 SWIFTYCROP: handleImageSelection appelé")
        print("🔥🔥🔥 SWIFTYCROP: Image reçue, size = \(imageData.size)")
        
        selectedImage = imageData
        
        // Fermer la sheet de sélection
        showingGalleryPicker = false
        showingLimitedGalleryView = false
        
        print("🔥🔥🔥 SWIFTYCROP: Avant d'activer showImageCropper")
        
        // Petit délai pour s'assurer que les sheets sont fermées
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            print("🔥🔥🔥 SWIFTYCROP: Activation de showImageCropper...")
            self.showImageCropper = true
            print("🔥🔥🔥 SWIFTYCROP: showImageCropper activé = \(self.showImageCropper)")
        }
    }
    
    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

#Preview {
    ProfilePhotoStepView(viewModel: OnboardingViewModel())
}

 