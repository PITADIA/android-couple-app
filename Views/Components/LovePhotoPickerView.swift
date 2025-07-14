import SwiftUI
import PhotosUI
import Photos

// MARK: - Extension PHAuthorizationStatus
extension PHAuthorizationStatus {
    func toString() -> String {
        switch self {
        case .notDetermined: return "Non déterminé"
        case .restricted: return "Restreint"
        case .denied: return "Refusé"
        case .authorized: return "Autorisé"
        case .limited: return "Limité"  // ← NOUVEAU dans iOS 14+
        @unknown default: return "Inconnu"
        }
    }
}

// MARK: - Love Photo Picker View
struct LovePhotoPickerView: View {
    @Binding var selectedImage: UIImage?
    @Environment(\.dismiss) private var dismiss
    
    // États d'interface
    @State private var showingGalleryPicker = false        // Picker standard
    @State private var showingLimitedGalleryView = false   // Interface personnalisée
    @State private var limitedPhotoAssets: [PHAsset] = []  // Assets autorisés
    @State private var showSettingsAlert = false          // Alerte paramètres
    @State private var isLoading = false
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                // Header avec icône
                VStack(spacing: 16) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.system(size: 60))
                        .foregroundColor(Color(hex: "#FD267A"))
                    
                    Text("choose_photo".localized)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.black)
                    
                    Text("photo_access_description".localized)
                        .font(.system(size: 13))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 40)
                
                Spacer()
                
                // Bouton principal
                Button(action: {
                    print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Demande d'accès photos initiée par l'utilisateur")
                    checkPhotoLibraryPermission()
                }) {
                    HStack(spacing: 12) {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.8)
                        } else {
                            Image(systemName: "photo.badge.plus")
                                .font(.system(size: 20))
                        }
                        
                        Text(isLoading ? "loading_photos".localized : "access_photos".localized)
                            .font(.system(size: 18, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(hex: "#FD267A"))
                    .clipShape(RoundedRectangle(cornerRadius: 28))
                }
                .disabled(isLoading)
                .padding(.horizontal, 20)
                
                Button(action: {
                    dismiss()
                }) {
                    Text("cancel".localized)
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.6))
                }
                .padding(.bottom, 40)
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showingGalleryPicker) {
            StandardGalleryPicker(onImageSelected: handleImageSelection)
        }
        .sheet(isPresented: $showingLimitedGalleryView) {
            LimitedGalleryView(assets: limitedPhotoAssets, onImageSelected: handleImageSelection)
        }
        .alert("photo_access_required".localized, isPresented: $showSettingsAlert) {
            Button("cancel".localized, role: .cancel) { }
            Button("open_settings_button".localized) {
                openSettings()
            }
        } message: {
            Text("photo_permission_message".localized)
                .font(.system(size: 16))
                .foregroundColor(.black.opacity(0.8))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
        }
    }
    
    // MARK: - Gestion des Permissions
    
    private func checkPhotoLibraryPermission() {
        print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Vérification du statut des permissions photos")
        
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Statut actuel: \(status.toString())")
        
        switch status {
        case .authorized:
            print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Accès complet autorisé - utilisation du picker standard")
            showingGalleryPicker = true
            
        case .limited:
            print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Accès limité détecté - utilisation de l'interface personnalisée")
            loadLimitedAssets { success in
                if success {
                    showingLimitedGalleryView = true
                } else {
                    print("❌ LOVE2LOVE_PRIVACY: [AUDIT] Aucune photo accessible en mode limité")
                    showSettingsAlert = true
                }
            }
            
        case .notDetermined:
            print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Permission non déterminée - demande d'autorisation")
            requestPhotoLibraryPermission()
            
        case .denied, .restricted:
            print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Accès refusé ou restreint - redirection vers les paramètres")
            showSettingsAlert = true
            
        @unknown default:
            print("❌ LOVE2LOVE_PRIVACY: [AUDIT] Statut inconnu")
            showSettingsAlert = true
        }
    }
    
    private func requestPhotoLibraryPermission() {
        isLoading = true
        
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
            DispatchQueue.main.async {
                self.isLoading = false
                print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Nouvelle autorisation: \(newStatus.toString())")
                
                switch newStatus {
                case .authorized:
                    print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Autorisation complète accordée")
                    self.showingGalleryPicker = true
                    
                case .limited:
                    print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Autorisation limitée accordée")
                    self.loadLimitedAssets { success in
                        if success {
                            self.showingLimitedGalleryView = true
                        } else {
                            self.showSettingsAlert = true
                        }
                    }
                    
                case .denied, .restricted:
                    print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Autorisation refusée")
                    self.showSettingsAlert = true
                    
                default:
                    print("❌ LOVE2LOVE_PRIVACY: [AUDIT] Statut inattendu après demande")
                    self.showSettingsAlert = true
                }
            }
        }
    }
    
    private func loadLimitedAssets(completion: @escaping (Bool) -> Void) {
        print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Chargement des assets limités")
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        // Ajouter un filtre pour s'assurer qu'on récupère seulement les images locales disponibles
        fetchOptions.includeHiddenAssets = false
        
        let allPhotos = PHAsset.fetchAssets(with: .image, options: fetchOptions)
        print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] L'app a accès à \(allPhotos.count) photos en mode limité, conforme aux attentes")
        
        limitedPhotoAssets = []
        for i in 0..<allPhotos.count {
            let asset = allPhotos.object(at: i)
            print("🔍 Asset \(i): ID=\(asset.localIdentifier), MediaType=\(asset.mediaType.rawValue), PixelWidth=\(asset.pixelWidth), PixelHeight=\(asset.pixelHeight)")
            limitedPhotoAssets.append(asset)
        }
        
        completion(allPhotos.count > 0)
    }
    
    private func handleImageSelection(_ image: UIImage) {
        print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Image sélectionnée par l'utilisateur - choix explicite conforme")
        selectedImage = image
        dismiss()
    }
    
    private func openSettings() {
        print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Redirection vers les paramètres système")
        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(settingsUrl)
        }
    }
}

// MARK: - Standard Gallery Picker (Accès Complet)
struct StandardGalleryPicker: UIViewControllerRepresentable {
    let onImageSelected: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss
    
    func makeUIViewController(context: Context) -> PHPickerViewController {
        print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Création du picker standard pour accès complet")
        
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 1
        
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = context.coordinator
        return picker
    }
    
    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let parent: StandardGalleryPicker
        
        init(_ parent: StandardGalleryPicker) {
            self.parent = parent
        }
        
        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] L'utilisateur a sélectionné \(results.count) image(s) - choix explicite conforme")
            
            if results.isEmpty {
                print("🔍 LOVE2LOVE_PRIVACY: [AUDIT] Sélection annulée par l'utilisateur")
                parent.dismiss()
                return
            }
            
            guard let result = results.first else {
                parent.dismiss()
                return
            }
            
            if result.itemProvider.canLoadObject(ofClass: UIImage.self) {
                result.itemProvider.loadObject(ofClass: UIImage.self) { image, error in
                    DispatchQueue.main.async {
                        if let uiImage = image as? UIImage {
                            print("✅ LOVE2LOVE_PRIVACY: [AUDIT] Image chargée avec succès via picker standard")
                            self.parent.onImageSelected(uiImage)
                        } else {
                            print("❌ LOVE2LOVE_PRIVACY: [AUDIT] Erreur chargement image picker standard")
                            self.parent.dismiss()
                        }
                    }
                }
            } else {
                parent.dismiss()
            }
        }
    }
}

// MARK: - Limited Gallery View (Accès Limité)
struct LimitedGalleryView: View {
    let assets: [PHAsset]
    let onImageSelected: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss
    
    private let columns = [
        GridItem(.flexible(), spacing: 2),
        GridItem(.flexible(), spacing: 2),
        GridItem(.flexible(), spacing: 2)
    ]
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond similaire à l'interface iOS native
                Color(UIColor.systemBackground)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    if assets.isEmpty {
                        // Message explicatif + bouton paramètres
                        VStack(spacing: 20) {
                            Image(systemName: "photo.badge.exclamationmark")
                                .font(.system(size: 60))
                                .foregroundColor(.gray)
                            
                            Text("no_accessible_photos".localized)
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.black)
                            
                            Text("select_more_photos".localized)
                                .font(.system(size: 14))
                                .foregroundColor(.gray)
                                .multilineTextAlignment(.center)
                            
                            Button("open_settings_button".localized) {
                                openSettings()
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(Color(hex: "#FD267A"))
                            .clipShape(RoundedRectangle(cornerRadius: 20))
                        }
                        .padding()
                    } else {
                        ScrollView {
                            LazyVGrid(columns: columns, spacing: 2) {
                                ForEach(0..<assets.count, id: \.self) { index in
                                    AssetThumbnailView(
                                        asset: assets[index],
                                        onSelected: { image in
                                            onImageSelected(image)
                                            dismiss()
                                        }
                                    )
                                }
                            }
                            .padding(.horizontal, 2)
                            .padding(.top, 2)
                        }
                    }
                }
            }
            .navigationTitle("Photos")
            .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("cancel".localized) {
                        dismiss()
                    }
                    .foregroundColor(Color(hex: "#FD267A"))
                }
            }
        }
    }
    
    private func openSettings() {
        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(settingsUrl)
        }
    }
}

// MARK: - Asset Thumbnail View
struct AssetThumbnailView: View {
    let asset: PHAsset
    let onSelected: (UIImage) -> Void
    
    @State private var thumbnail: UIImage?
    @State private var isLoading = false
    @State private var isPressed = false
    
    var body: some View {
        Button(action: {
            loadFullSizeImage()
        }) {
            ZStack {
                // Fond avec couleur système
                Rectangle()
                    .fill(Color(UIColor.systemGray6))
                    .aspectRatio(1, contentMode: .fit)
                
                if let thumbnail = thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .clipped()
                } else if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: "#FD267A")))
                        .scaleEffect(0.8)
                } else {
                    Image(systemName: "photo")
                        .font(.system(size: 24))
                        .foregroundColor(Color(UIColor.systemGray3))
                }
                
                // Overlay pour effet de pression
                if isPressed {
                    Rectangle()
                        .fill(Color.black.opacity(0.1))
                }
            }
            .clipShape(Rectangle()) // Pas de coins arrondis comme le picker natif
            .scaleEffect(isPressed ? 0.95 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: isPressed)
        }
        .buttonStyle(PlainButtonStyle())
        .onLongPressGesture(minimumDuration: 0, maximumDistance: .infinity, pressing: { pressing in
            isPressed = pressing
        }, perform: {})
        .onAppear {
            loadThumbnail()
        }
    }
    
    private func loadThumbnail() {
        print("🔍 AssetThumbnailView: Chargement miniature pour asset: \(asset.localIdentifier)")
        
        let manager = PHImageManager.default()
        let options = PHImageRequestOptions()
        options.isSynchronous = false
        options.deliveryMode = .opportunistic
        options.resizeMode = .exact
        options.isNetworkAccessAllowed = false
        
        // Calculer la taille optimale pour l'écran
        let scale = UIScreen.main.scale
        let targetSize = CGSize(width: 120 * scale, height: 120 * scale)
        
        manager.requestImage(
            for: asset, 
            targetSize: targetSize, 
            contentMode: .aspectFill, 
            options: options
        ) { image, info in
            DispatchQueue.main.async {
                if let image = image {
                    print("✅ AssetThumbnailView: Miniature chargée avec succès")
                    self.thumbnail = image
                } else {
                    print("❌ AssetThumbnailView: Échec chargement miniature")
                    if let info = info {
                        print("❌ Info: \(info)")
                    }
                    // Essayer avec une taille plus petite en cas d'échec
                    self.loadThumbnailFallback()
                }
            }
        }
    }
    
    private func loadThumbnailFallback() {
        print("🔍 AssetThumbnailView: Tentative fallback avec taille plus petite")
        
        let manager = PHImageManager.default()
        let options = PHImageRequestOptions()
        options.isSynchronous = false
        options.deliveryMode = .fastFormat
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = false
        
        manager.requestImage(
            for: asset, 
            targetSize: CGSize(width: 75, height: 75),
            contentMode: .aspectFill, 
            options: options
        ) { image, info in
            DispatchQueue.main.async {
                if let image = image {
                    print("✅ AssetThumbnailView: Miniature fallback chargée avec succès")
                    self.thumbnail = image
                } else {
                    print("❌ AssetThumbnailView: Échec complet du chargement de miniature")
                    if let info = info {
                        print("❌ Info fallback: \(info)")
                    }
                }
            }
        }
    }
    
    private func loadFullSizeImage() {
        print("🔍 AssetThumbnailView: Chargement image haute résolution pour asset: \(asset.localIdentifier)")
        isLoading = true
        
        let manager = PHImageManager.default()
        let options = PHImageRequestOptions()
        options.isSynchronous = false
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = true
        
        manager.requestImage(
            for: asset, 
            targetSize: PHImageManagerMaximumSize, 
            contentMode: .aspectFit, 
            options: options
        ) { image, info in
            DispatchQueue.main.async {
                self.isLoading = false
                if let image = image {
                    print("✅ LOVE2LOVE_PRIVACY: [AUDIT] Image haute résolution chargée depuis assets limités")
                    self.onSelected(image)
                } else {
                    print("❌ AssetThumbnailView: Échec chargement image haute résolution")
                    if let info = info {
                        print("❌ Info: \(info)")
                    }
                }
            }
        }
    }
}

// MARK: - Preview
#Preview {
    struct PreviewWrapper: View {
        @State private var selectedImage: UIImage?
        @State private var showingPicker = false
        
        var body: some View {
            VStack {
                if let image = selectedImage {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)
                } else {
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 200, height: 200)
                        .overlay(
                                                        Text("no_image".localized)
                                .foregroundColor(.gray)
                        )
                }
                
                Button("select_photo".localized) {
                    showingPicker = true
                }
                .padding()
            }
            .sheet(isPresented: $showingPicker) {
                LovePhotoPickerView(selectedImage: $selectedImage)
            }
        }
    }
    
    return PreviewWrapper()
} 