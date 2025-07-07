import SwiftUI
import PhotosUI
import Photos
import MapKit

struct CreateJournalEntryView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    
    // NOUVEAU: Utiliser le JournalService depuis AppState
    private var journalService: JournalService {
        return appState.journalService ?? JournalService.shared
    }
    
    @State private var title = ""
    @State private var description = ""
    @State private var eventDate = Date()
    @State private var selectedImage: UIImage?
    @State private var showingImagePicker = false

    @State private var isCreating = false
    @State private var selectedLocation: JournalLocation?
    @State private var showingLocationPicker = false
    @State private var showingDatePicker = false
    @State private var showingTimePicker = false
    @State private var showingFreemiumAlert = false
    @State private var freemiumErrorMessage = ""
    
    // MARK: - Gestion des autorisations photos (comme ProfilePhotoStepView)
    @State private var showingLimitedGalleryView = false
    @State private var limitedPhotoAssets: [PHAsset] = []
    @State private var showSettingsAlert = false
    @State private var alertMessage = ""
    
    private var canSave: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    var body: some View {
        ZStack {
            // Fond gris clair identique aux autres pages
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea(.all)
            
            VStack(spacing: 0) {
                // Header avec bouton retour
                HStack {
                    Button(action: {
                        dismiss()
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .medium))
                            .foregroundColor(.black)
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 60)
                .padding(.bottom, 30)
                
                // Contenu principal
                VStack(spacing: 20) {
                    // Titre
                    VStack(alignment: .leading, spacing: 8) {
                        TextField("Titre de votre souvenir", text: $title)
                            .font(.system(size: 24, weight: .medium))
                            .foregroundColor(.black)
                            .textFieldStyle(PlainTextFieldStyle())
                    }
                    .padding(.horizontal, 20)
                    
                    // Date sélectionnée (affichage discret)
                    HStack {
                        Text(formattedEventDate)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.6))
                        
                        // Affichage de la localisation si sélectionnée
                        if let location = selectedLocation {
                            Text("• \(location.displayName)")
                                .font(.system(size: 16))
                                .foregroundColor(.black.opacity(0.6))
                        }
                        
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    
                    // Description
                    VStack(alignment: .leading, spacing: 8) {
                        TextField("Écrivez quelques mots sur ce souvenir", text: $description, axis: .vertical)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.7))
                            .textFieldStyle(PlainTextFieldStyle())
                            .lineLimit(5...10)
                    }
                    .padding(.horizontal, 20)
                    
                    Spacer()
                }
                
                // Barre d'outils en bas avec icônes
                HStack {
                    // Icônes à gauche
                    HStack(spacing: 20) {
                        // Icône image avec preview si sélectionnée
                        Button(action: {
                            checkPhotoLibraryPermission() // ✅ Vérifier autorisations comme l'onboarding
                        }) {
                            if let image = selectedImage {
                                // Affichage de l'image sélectionnée
                                ZStack(alignment: .topTrailing) {
                                    Image(uiImage: image)
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(width: 60, height: 60)
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                    
                                    // Bouton X pour supprimer
                                    Button(action: {
                                        selectedImage = nil
                                    }) {
                                        Image(systemName: "xmark.circle.fill")
                                            .font(.system(size: 20))
                                            .foregroundColor(.white)
                                            .background(Color.black.opacity(0.6))
                                            .clipShape(Circle())
                                    }
                                    .offset(x: 8, y: -8)
                                }
                            } else {
                                // Icône photo normale
                                Image(systemName: "photo")
                                    .font(.system(size: 24))
                                    .foregroundColor(.black)
                            }
                        }
                        
                        // Icône calendrier (date seulement)
                        Button(action: {
                            showingDatePicker = true
                        }) {
                            Image(systemName: "calendar")
                                .font(.system(size: 24))
                                .foregroundColor(.black)
                        }
                        
                        // Icône horloge (heure seulement)
                        Button(action: {
                            showingTimePicker = true
                        }) {
                            Image(systemName: "clock")
                                .font(.system(size: 24))
                                .foregroundColor(.black)
                        }
                        
                        // Icône localisation
                        Button(action: {
                            showingLocationPicker = true
                        }) {
                            Image(systemName: selectedLocation != nil ? "location.fill" : "location")
                                .font(.system(size: 24))
                                .foregroundColor(.black)
                        }
                    }
                    
                    Spacer()
                    
                    // Bouton Enregistrer à droite (style avec deux roses)
                    Button(action: createEntry) {
                        HStack(spacing: 8) {
                            if isCreating {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.8)
                            }
                            
                            Text(isCreating ? "Enregistrement..." : "Enregistrer")
                                .font(.system(size: 16, weight: .semibold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(canSave && !isCreating ? Color(hex: "#FD267A") : Color(hex: "#FD267A").opacity(0.5))
                        )
                    }
                    .disabled(!canSave || isCreating)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
        }
        .navigationBarHidden(true)
        .sheet(isPresented: $showingImagePicker) {
            StandardGalleryPicker(onImageSelected: { image in
                selectedImage = image
                showingImagePicker = false // ✅ Fermeture explicite comme dans l'onboarding
            })
        }
        .sheet(isPresented: $showingLimitedGalleryView) {
            LimitedGalleryView(assets: limitedPhotoAssets, onImageSelected: handleImageSelection)
        }
        .sheet(isPresented: $showingLocationPicker) {
            LocationPickerView(selectedLocation: $selectedLocation)
        }
        .sheet(isPresented: $showingDatePicker) {
            NavigationView {
                VStack {
                    DatePicker("Date de l'événement", selection: $eventDate, displayedComponents: [.date])
                        .datePickerStyle(GraphicalDatePickerStyle())
                        .environment(\.locale, Locale(identifier: "fr_FR"))
                        .padding()
                    
                    Spacer()
                }
                .navigationTitle("Choisir la date")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Annuler") {
                            showingDatePicker = false
                        }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("OK") {
                            showingDatePicker = false
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .sheet(isPresented: $showingTimePicker) {
            NavigationView {
                VStack {
                    Spacer()
                    
                    HStack {
                        Spacer()
                        DatePicker("", selection: $eventDate, displayedComponents: [.hourAndMinute])
                            .datePickerStyle(WheelDatePickerStyle())
                            .environment(\.locale, Locale(identifier: "fr_FR"))
                        Spacer()
                    }
                    .padding()
                    
                    Spacer()
                }
                .navigationTitle("Choisir l'heure")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Annuler") {
                            showingTimePicker = false
                        }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("OK") {
                            showingTimePicker = false
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .alert("Erreur", isPresented: $showingFreemiumAlert) {
            Button("OK") { }
        } message: {
            Text(freemiumErrorMessage)
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
    }
    
    private var formattedEventDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.timeStyle = .short
        formatter.locale = Locale(identifier: "fr_FR")
        return formatter.string(from: eventDate)
    }
    

    
    private func createEntry() {
        guard canSave else { return }
        
        isCreating = true
        
        Task {
            do {
                try await journalService.createEntry(
                    title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                    description: description.trimmingCharacters(in: .whitespacesAndNewlines),
                    eventDate: eventDate,
                    image: selectedImage,
                    location: selectedLocation
                )
                
                await MainActor.run {
                    dismiss()
                }
                
            } catch {
                await MainActor.run {
                    isCreating = false
                    
                    // NOUVEAU: Gérer spécifiquement les erreurs freemium
                    if let journalError = error as? JournalError {
                        switch journalError {
                        case .freemiumLimitReached:
                            print("📝 CreateJournalEntryView: Limite freemium atteinte")
                            // Fermer la vue et laisser le FreemiumManager gérer le paywall
                            dismiss()
                            
                            // Déclencher le paywall via le FreemiumManager
                            appState.freemiumManager?.handleJournalEntryCreation(
                                currentEntriesCount: journalService.currentUserEntriesCount
                            ) {
                                // Ce callback ne sera pas appelé car la limite est atteinte
                            }
                            
                        default:
                            freemiumErrorMessage = journalError.localizedDescription
                            showingFreemiumAlert = true
                        }
                    } else {
                        freemiumErrorMessage = error.localizedDescription
                        showingFreemiumAlert = true
                    }
                }
            }
        }
    }
    
    // MARK: - Gestion des autorisations photos (copié de ProfilePhotoStepView)
    
    private func checkPhotoLibraryPermission() {
        print("🔐 CreateJournalEntry: Vérification des autorisations de la photothèque")
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        print("📱 CreateJournalEntry: Statut actuel: \(status.toString())")
        
        switch status {
        case .authorized:
            // ✅ ACCÈS COMPLET
            print("✅ CreateJournalEntry: Accès complet déjà autorisé")
            showingImagePicker = true
            
        case .limited:
            // ✅ ACCÈS LIMITÉ - Charger les photos autorisées
            print("🔍 CreateJournalEntry: Accès limité détecté")
            loadLimitedAssets { success in
                DispatchQueue.main.async {
                    if success {
                        self.showingLimitedGalleryView = true
                    } else {
                        // Fallback vers picker standard si échec
                        self.showingImagePicker = true
                    }
                }
            }
            
        case .notDetermined:
            // ⏳ PREMIÈRE DEMANDE
            print("⏳ CreateJournalEntry: Première demande d'autorisation")
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                DispatchQueue.main.async {
                    print("📱 CreateJournalEntry: Nouveau statut: \(newStatus.toString())")
                    // Récursion pour traiter le nouveau statut
                    self.checkPhotoLibraryPermission()
                }
            }
            
        case .denied, .restricted:
            // ❌ ACCÈS REFUSÉ - Proposer d'aller aux paramètres
            print("❌ CreateJournalEntry: Accès refusé")
            alertMessage = "L'accès à votre galerie est nécessaire pour ajouter une photo. Veuillez l'activer dans les paramètres de votre appareil."
            showSettingsAlert = true
            
        @unknown default:
            print("❓ CreateJournalEntry: Statut inconnu")
            alertMessage = "Erreur d'accès à la galerie"
            showSettingsAlert = true
        }
    }
    
    private func loadLimitedAssets(completion: @escaping (Bool) -> Void) {
        print("📸 CreateJournalEntry: Chargement des photos autorisées...")
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        
        // ✅ MAGIE iOS : Cette ligne ne retourne QUE les photos autorisées
        let allPhotos = PHAsset.fetchAssets(with: .image, options: fetchOptions)
        print("📸 CreateJournalEntry: Nombre de photos accessibles: \(allPhotos.count)")
        
        limitedPhotoAssets = []
        
        if allPhotos.count > 0 {
            for i in 0..<allPhotos.count {
                let asset = allPhotos.object(at: i)
                limitedPhotoAssets.append(asset)
            }
            completion(true)
        } else {
            print("❌ CreateJournalEntry: Aucune photo accessible")
            completion(false)
        }
    }
    
    private func handleImageSelection(_ imageData: UIImage) {
        print("✅ CreateJournalEntry: Image sélectionnée")
        selectedImage = imageData
        
        // Fermer la sheet après sélection
        showingImagePicker = false
        showingLimitedGalleryView = false
    }
    
    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

// Extension déjà définie ailleurs, pas besoin de la redéclarer

struct CreateJournalEntryView_Previews: PreviewProvider {
    static var previews: some View {
        CreateJournalEntryView()
            .environmentObject(AppState())
    }
}

 