import SwiftUI
import PhotosUI
import Photos

struct ProfilePhotoStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var selectedImage: UIImage?
    @State private var showingGalleryPicker = false        // Picker standard (accès complet)
    @State private var showingLimitedGalleryView = false   // Interface personnalisée (accès limité)
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
                Text("Ajoute ta photo de profil")
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
                        
                        if let selectedImage = selectedImage {
                            Image(uiImage: selectedImage)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 160, height: 160)
                                .clipShape(Circle())
                        } else {
                            VStack(spacing: 8) {
                                Image(systemName: "person.fill")
                                    .font(.system(size: 40))
                                    .foregroundColor(.black.opacity(0.3))
                                
                                Text("Ajouter une photo")
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
                    if let image = selectedImage {
                        viewModel.profileImage = image
                    }
                    viewModel.nextStep()
                }) {
                    Text("Continuer")
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
                    Text("Passer cette étape")
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
        .onChange(of: selectedImage) { _, newImage in
            if let image = newImage {
                viewModel.profileImage = image
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
            alertMessage = "L'accès à votre galerie est nécessaire pour ajouter une photo de profil. Veuillez l'activer dans les paramètres de votre appareil."
            showSettingsAlert = true
            
        @unknown default:
            print("❓ ProfilePhoto: Statut inconnu")
            alertMessage = "Erreur d'accès à la galerie"
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
        print("✅ ProfilePhoto: Image sélectionnée")
        selectedImage = imageData
        viewModel.profileImage = imageData
        
        // Fermer la sheet après sélection
        showingGalleryPicker = false
        showingLimitedGalleryView = false
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

 